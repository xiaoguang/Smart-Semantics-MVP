import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  bindDemoContentRun,
  validateDemoContentRunBinding,
  type DemoContentRunBinding,
} from '../guanyijia-demo-content/demo-content-review.ts';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';
import type {
  ConflictHunk,
  ConflictResolutionArtifact,
  ConflictResolutionPreview,
  ConflictResolutionStrategy,
  GuanyijiaStandardizationStory,
  SourceDocumentBlock,
  SourceDocumentCompilation,
  StorySource,
  SourceCompilationDiff,
  StructuredValue,
} from '../guanyijia-standardization-story/types.ts';
import { canonicalModelingJson, standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import type { ModelingDocumentSection, StructuredModelingAssertion } from '../modeling-document-bridge/types.ts';
import { diffSourceDocumentLines } from '../source-documents/runtime.ts';
import { renderSourceDocumentMarkdown } from '../source-documents/structured-projection.ts';
import type {
  ContentAddressedStore,
  ContentReference,
  SourceDocumentLineDiff,
  SourceDocumentRevisionDiff,
  SourceDocumentRuntime,
  SourceDocumentSections,
  SourceModelingDocument,
} from '../source-documents/types.ts';
import type {
  StandardizationRun,
  StandardizationRunRuntime,
  StandardizationSourceStep,
  StandardizationTimelineEvent,
} from '../standardization-run/types.ts';
import {
  createArrayReviewWindowIndex,
  createStandardizationReviewWindow,
  ReviewWindowError,
  type ReviewWindowPage,
  type ReviewWindowRequest,
  type ReviewWindowStream,
  type ReviewWindowSummary,
  type VerifiedReviewContent,
} from '../review-window/index.ts';
import { answerReviewAssistant, classifyReviewAssistantIntent } from './review-assistant.ts';
import {
  buildHumanReadableEvidence,
  displaySourceName,
} from './human-readable-evidence.ts';
import {
  buildScriptedReviewBlockChange,
  findScriptedReviewEdit,
  listScriptedReviewEdits,
  validateScriptedReviewEditBinding,
  type ScriptedReviewDecision,
  type ScriptedReviewEditDefinition,
  type ScriptedReviewPatch,
} from './scripted-review-edits.ts';
import { readSourceReviewDocument } from '../guanyijia-evidence-factory/source-review-document.ts';
import type {
  AssistantStructuredChangeProposal,
  ReviewAssistantContextSelection,
  ReviewAssistantHistoryPage,
  ReviewAssistantHistoryItem,
  ReviewAssistantKnowledge,
  ReviewAssistantAccess,
  ReviewAssistantResponse,
  ReviewAssistantTarget,
  ReviewAssistantTurnArtifact,
} from './review-assistant-types.ts';

type PointerStorage = Pick<Storage, 'getItem' | 'setItem'>;

export const guanyijiaDeliverableContentLabels = {
  sourceManifestRef: '来源清单',
  mergedDocumentRef: '合并文档',
  decisionManifestRef: '审阅决定',
  governanceAppendixRef: '证据附录',
  modelingArtifactRef: '模型交接说明',
  zeroDeltaReportRef: '模型差异',
} as const;

export type GuanyijiaDeliverableContentKey = keyof typeof guanyijiaDeliverableContentLabels;

export function guanyijiaDeliverableContentLabelFor(key: GuanyijiaDeliverableContentKey) {
  return guanyijiaDeliverableContentLabels[key];
}

type GuanyijiaWorkbenchBaseCommand = {
  commandId: string;
  expectedRevision: number;
  actorUserId: string;
  /**
   * The source-document identity rendered by a review surface.  Supplying an
   * identity makes a review write conditional on that exact current document;
   * a history view therefore cannot accidentally write the active revision.
   *
   * Kept optional for legacy, non-UI command replays that pre-date this
   * controller contract.  Every workbench review action supplies both values.
   */
  documentId?: string;
  documentRevision?: number;
};

export type GuanyijiaWorkbenchBlockChange = {
  blockId: string;
  label?: string;
  value?: StructuredValue;
};

/**
 * Ephemeral UI progress emitted only at actual source-processing boundaries.
 * It is deliberately not part of StandardizationRun persistence: reloads use
 * the durable source status, while an in-flight browser can show the work it
 * is genuinely performing without inventing timer-driven progress.
 */
export type SourcePreparationStage = 'READ' | 'ANALYZE' | 'ORGANIZE';
export type SourcePreparationProgress = {
  sourceId: string;
  stage: SourcePreparationStage;
  state: 'ACTIVE' | 'COMPLETE' | 'ERROR';
  sequence: number;
};

export type GuanyijiaWorkbenchCommand = GuanyijiaWorkbenchBaseCommand & (
  | { type: 'START_RUN' | 'READ_NEXT_SOURCE' }
  | { type: 'COMPLETE_CURRENT_DOCUMENT_REVIEW' }
  | { type: 'PREVIEW_CURRENT_BLOCK_CHANGE'; change: GuanyijiaWorkbenchBlockChange }
  | { type: 'APPLY_CURRENT_BLOCK_CHANGE'; change: GuanyijiaWorkbenchBlockChange }
  | { type: 'PREVIEW_SCRIPTED_REVIEW_EDIT'; editId: string; patch: ScriptedReviewPatch }
  | {
      type: 'DECIDE_SCRIPTED_REVIEW_EDIT';
      editId: string;
      decision: ScriptedReviewDecision;
      patch?: ScriptedReviewPatch;
      expectedPreviewSha256?: `sha256:${string}`;
    }
  | {
      type: 'RESOLVE_CURRENT_CONFLICT';
      conflictId: string;
      strategy: ConflictResolutionStrategy;
      reason: string;
      expectedHunkSha256: string;
    }
  | {
      type: 'REPLACE_RESOLVED_CONFLICT';
      conflictId: string;
      strategy: ConflictResolutionStrategy;
      reason: string;
      expectedHunkSha256: string;
    }
  | {
      type: 'ASK_REVIEW_ASSISTANT';
      message: string;
      selection: ReviewAssistantContextSelection;
    }
  | {
      type: 'CONFIRM_ASSISTANT_PATCH';
      proposalId: string;
      expectedPreviewSha256: `sha256:${string}`;
    }
  | {
      type: 'CANCEL_ASSISTANT_PATCH';
      proposalId: string;
    }
);

export type GuanyijiaBlockChangePreview = {
  block: SourceCompilationDiff['blockChanges'][number];
  assertion: SourceCompilationDiff['assertionChanges'][number];
  section: SourceCompilationDiff['sectionChanges'][number] & { lines: SourceDocumentLineDiff[] };
  markdown: SourceCompilationDiff['markdown'] & { lines: SourceDocumentLineDiff[] };
  affectedConflicts: SourceCompilationDiff['affectedConflicts'];
  affectedObjects: string[];
};

export type WorkbenchTimelineItem = {
  itemId: string;
  eventId?: string;
  kind:
    | 'RUN_STARTED'
    | 'SOURCE_READ_STARTED'
    | 'SOURCE_READ_COMPLETED'
    | 'DOCUMENT_GENERATED'
    | 'DOCUMENT_REVISED'
    | 'DOCUMENT_REVIEWED'
    | 'CONFLICT_FOUND'
    | 'CONFLICT_RESOLVED'
    | 'CONFLICT_DECISION_REPLACED'
    | 'CONFLICT_CORROBORATED'
    | 'DELIVERABLE_GENERATED'
    | 'DELIVERABLE_SUPERSEDED'
    | 'DELIVERABLE_FROZEN'
    | 'MODELING_HANDOFF_COMPLETED'
    | 'ASSISTANT_TURN_RECORDED'
    | 'ASSISTANT_PATCH_CONFIRMED'
    | 'ASSISTANT_PATCH_CANCELLED';
  state: 'RECEIPT' | 'CURRENT';
  title: string;
  summary: string;
  action?:
    | { type: 'OPEN_DOCUMENT'; documentId: string; revision: number }
    | { type: 'OPEN_SOURCE_DETAILS'; sourceId: string }
    | { type: 'OPEN_CONFLICT'; conflictId: string }
    | { type: 'OPEN_DELIVERABLE'; mergedDocumentRef?: ContentReference };
  sourceId?: string;
  createdAt?: string;
  contentRef?: ContentReference;
  contentSha256?: string;
  document?: {
    documentId: string;
    revision: number;
    sectionCount: number;
    blockCounts: Record<SourceDocumentBlock['evidenceStatus'], number>;
  };
  conflicts?: Array<{
    conflictId: string;
    title: string;
    affectedObjects: string[];
  }>;
  inspector?: StandardizationFactsInspectorModel;
};

export type StandardizationFactsInspectorModel = {
  sourceId: string;
  sourceName: string;
  sourceClass: StorySource['sourceClass'];
  snapshotId: string;
  versionRef: string;
  authority: StorySource['authority'];
  readSummary: string;
  objectCount: number;
  evidenceCount: number;
  lineage: {
    status: SourceDocumentCompilation['lineageStatus'];
    upstreamSourceNames: string[];
    independentEvidenceNote?: string;
  };
  block?: {
    blockId: string;
    label: string;
    evidenceStatus: SourceDocumentBlock['evidenceStatus'];
    locator?: Record<string, unknown>;
    affectedObjects: string[];
    readableEvidence?: ReturnType<typeof buildHumanReadableEvidence>;
  };
};

export type ScriptedReviewEditState = {
  definition: ScriptedReviewEditDefinition;
  status: 'PENDING' | 'KEPT' | 'APPLIED';
  decision?: ScriptedReviewDecision;
  actorUserId?: string;
  decidedAt?: string;
};

export type GuanyijiaWorkbenchSnapshot = {
  storyId: 'guanyijia-five-source-v1';
  storyName: '管伊佳数据标准化';
  batchId: string;
  run: StandardizationRun | null;
  contentBinding?: DemoContentRunBinding;
  timeline: WorkbenchTimelineItem[];
  timelineTotal: number;
  assistantTurnDelta?: AssistantTurnDelta;
  scriptedEdits?: ScriptedReviewEditState[];
  scriptedEditPreviewSha256?: `sha256:${string}`;
  current?: {
    source: StorySource;
    sourceStep: StandardizationSourceStep;
    document?: SourceModelingDocument;
    compilation?: SourceDocumentCompilation;
    // A selected review page, deliberately narrower than the source compilation.
    // React receives at most the verified blocks for one review-window page.
    reviewCompilation?: Pick<SourceDocumentCompilation,
      'sourceId' | 'sourceName' | 'sourceClass' | 'readSummary' | 'lineageStatus'
      | 'upstreamSourceIds' | 'blocks' | 'evidenceLocators'>;
    compilationSummary?: {
      blockCount: number;
      sectionCount: number;
      firstReviewSection: ModelingDocumentSection;
    };
    history?: SourceModelingDocument[];
    revisionDiff?: SourceDocumentRevisionDiff;
    revisionHistory?: SourceDocumentRevisionDiff[];
    legacyReadOnly?: {
      sections: SourceDocumentSections;
      assertions: StructuredModelingAssertion[];
      markdown: string;
    };
  };
  nextAction:
    | { type: 'START_RUN'; label: '开始资料整理' }
    | { type: 'READ_NEXT_SOURCE'; label: string }
    | { type: 'REVIEW_DOCUMENT'; label: string }
    | { type: 'COMPLETE_REVIEW'; label: '完成本份审阅' }
    | { type: 'RESOLVE_CONFLICT'; label: string }
    | { type: 'NONE'; label: string };
  inspector?: StandardizationFactsInspectorModel;
  preview?: GuanyijiaBlockChangePreview;
  currentConflict?: {
    conflictId: string;
    title: string;
    hunk: ConflictHunk;
    defaultStrategy: ConflictResolutionStrategy;
    allowedStrategies: ConflictResolutionStrategy[];
  };
  resolutions: ConflictResolutionArtifact[];
  assistantFocusTarget?: ReviewAssistantTarget;
};

export type AssistantTurnDelta = {
  item: ReviewAssistantHistoryItem;
  total: number;
  eventId: string;
  position: number;
  contentRef: ContentReference;
  contentSha256: string;
};

export type GuanyijiaTechnicalResolutionSummary = {
  resolutionId: string;
  sourceId: string;
  title: string;
  hunkSha256: string;
  previewSha256: string;
  eventId: string;
  sequence: number;
  createdAt: string;
};

export type DocumentReviewNavigationState = {
  selectedDocumentId?: string;
  selectedSection?: ModelingDocumentSection;
  selectedBlockId?: string;
  focusTargetId?: string;
  focusToken: number;
};

export type GuanyijiaWorkbenchRuntime = {
  read(actorUserId: string): Promise<GuanyijiaWorkbenchSnapshot>;
  readReviewShell(actorUserId: string): Promise<GuanyijiaWorkbenchSnapshot>;
  readTechnicalResolutionSummaries(input: {
    actorUserId: string;
    runId: string;
    revision: number;
  }): Promise<GuanyijiaTechnicalResolutionSummary[]>;
  readReviewCurrentDocument(input: {
    actorUserId: string;
    runId: string;
    documentId: string;
    epoch: string;
    filter: string;
    cursor?: string;
  }): Promise<{
    current: NonNullable<GuanyijiaWorkbenchSnapshot['current']>;
    window: ReviewWindowPage;
  }>;
  readReviewDocumentShell(input: {
    actorUserId: string;
    runId: string;
    documentId: string;
  }): Promise<NonNullable<GuanyijiaWorkbenchSnapshot['current']>>;
  readReviewWindow(input: Omit<ReviewWindowRequest, 'runId'> & {
    actorUserId: string;
    runId: string;
  }): Promise<ReviewWindowPage>;
  readReviewContent(input: {
    actorUserId: string;
    runId: string;
    contentRef: ContentReference;
    expectedSha256: string;
  }): Promise<VerifiedReviewContent>;
  previewCurrentConflict(input: {
    runId: string;
    conflictId: string;
    strategy: ConflictResolutionStrategy;
  }): Promise<ConflictResolutionPreview>;
  readAssistantHistory(input: {
    actorUserId: string;
    runId: string;
    cursor?: string;
  }): Promise<ReviewAssistantHistoryPage>;
  execute(command: GuanyijiaWorkbenchCommand): Promise<GuanyijiaWorkbenchSnapshot>;
};

export function selectDataStandardizationExperience(systemCode: string) {
  return systemCode === projectId ? 'GUANYIJIA_TIMELINE' as const : 'LEGACY' as const;
}

export function isLatestConflictPreviewRequest(currentRequestId: number, requestId: number) {
  return currentRequestId === requestId;
}

/**
 * Keeps a verified Markdown body bound to the document revision that requested
 * it. Callers must check this before every asynchronous state completion.
 */
export function isLatestDocumentMarkdownRequest(
  currentLoadKey: string | undefined,
  completionLoadKey: string,
) {
  return currentLoadKey === completionLoadKey;
}

export function structuredValueEditorModel(value: StructuredValue):
  | { kind: 'STRING'; text: string; editable: true }
  | {
    kind: 'TEXT_OBJECT';
    text: string;
    editable: true;
    normalized?: string;
    readonlyFields: Record<string, StructuredValue>;
  }
  | { kind: 'READ_ONLY'; text: '未知结构，仅可查看'; editable: false } {
  if (typeof value === 'string') return { kind: 'STRING', text: value, editable: true };
  if (typeof value === 'object' && value !== null && !Array.isArray(value)
    && typeof value.text === 'string') {
    const { text, normalized, ...otherFields } = value;
    const editableNormalized = typeof normalized === 'string' ? normalized : undefined;
    const readonlyFields = editableNormalized === undefined
      ? { ...otherFields, ...(normalized !== undefined ? { normalized } : {}) }
      : otherFields;
    return {
      kind: 'TEXT_OBJECT', text, editable: true,
      ...(editableNormalized !== undefined ? { normalized: editableNormalized } : {}),
      readonlyFields,
    };
  }
  return { kind: 'READ_ONLY', text: '未知结构，仅可查看', editable: false };
}

export function reviseEditableStructuredValue(
  value: StructuredValue,
  text: string,
  normalized?: string,
): StructuredValue {
  const model = structuredValueEditorModel(value);
  if (!model.editable) throw new Error('未知结构只读，不能修改识别结果');
  if (model.kind === 'STRING') return text;
  return {
    ...structuredClone(model.readonlyFields),
    ...(model.normalized !== undefined ? { normalized: normalized ?? model.normalized } : {}),
    text,
  };
}

export function openCurrentDocumentReview(
  snapshot: GuanyijiaWorkbenchSnapshot,
  current: DocumentReviewNavigationState,
): DocumentReviewNavigationState {
  const document = snapshot.current?.document;
  const compilation = snapshot.current?.reviewCompilation ?? snapshot.current?.compilation;
  const compilationSummary = snapshot.current?.compilationSummary;
  const legacyReadOnly = snapshot.current?.legacyReadOnly;
  if (!document || (!compilation && !compilationSummary && !legacyReadOnly)) {
    throw new Error('当前没有可审阅的来源文档');
  }
  const firstUnconfirmedSection = compilation
    ? standardSectionOrder.find(({ key }) => (
      compilation.blocks.some((block) => block.section === key && block.evidenceStatus !== 'FACT')
    ))?.key ?? standardSectionOrder[0].key
    : compilationSummary?.firstReviewSection ?? standardSectionOrder[0].key;
  return {
    selectedDocumentId: document.documentId,
    selectedSection: firstUnconfirmedSection,
    focusTargetId: `guanyijia-document-review:${document.documentId}:heading`,
    focusToken: current.focusToken + 1,
  };
}

export function continueCurrentDocumentReviewAfterApply(
  snapshot: GuanyijiaWorkbenchSnapshot,
  current: DocumentReviewNavigationState,
  blockId: string,
): DocumentReviewNavigationState {
  const document = snapshot.current?.document;
  const block = (snapshot.current?.reviewCompilation ?? snapshot.current?.compilation)
    ?.blocks.find((candidate) => candidate.blockId === blockId);
  if (!document || !block) throw new Error('应用修改后无法恢复当前结构化块');
  return {
    selectedDocumentId: document.documentId,
    selectedSection: current.selectedSection ?? block.section,
    selectedBlockId: blockId,
    focusTargetId: `guanyijia-document-review:${document.documentId}:block:${blockId}`,
    focusToken: current.focusToken + 1,
  };
}

export function pageCurrentDocumentBlocks(
  snapshot: GuanyijiaWorkbenchSnapshot,
  section: ModelingDocumentSection,
  requestedPage: number,
  pageSize = 20,
) {
  if (!Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > 20) {
    throw new Error('来源文档每页最多显示20项');
  }
  const matching = snapshot.current?.compilation?.blocks.filter((block) => block.section === section) ?? [];
  const pageCount = Math.max(1, Math.ceil(matching.length / pageSize));
  const page = Math.min(Math.max(1, Math.trunc(requestedPage)), pageCount);
  const items = matching.slice((page - 1) * pageSize, page * pageSize);
  const groups = (['FACT', 'INFERENCE', 'GAP', 'CONFLICT'] as const).flatMap((evidenceStatus) => {
    const groupItems = items.filter((item) => item.evidenceStatus === evidenceStatus);
    return groupItems.length ? [{ evidenceStatus, items: groupItems }] : [];
  });
  return { items, groups, page, pageSize, pageCount, total: matching.length };
}

export function projectCurrentDocumentBlockWindow(
  snapshot: GuanyijiaWorkbenchSnapshot,
  section: ModelingDocumentSection,
  window: ReviewWindowPage,
  page: number,
) {
  const compilation = snapshot.current?.reviewCompilation ?? snapshot.current?.compilation;
  if (!compilation) return pageCurrentDocumentBlocks(snapshot, section, page);
  const stableCodes = new Set(window.items.map((item) => item.title));
  const items = compilation.blocks.filter((block) => (
    block.section === section && stableCodes.has(block.stableCode)
  ));
  const groups = (['FACT', 'INFERENCE', 'GAP', 'CONFLICT'] as const).flatMap((evidenceStatus) => {
    const groupItems = items.filter((item) => item.evidenceStatus === evidenceStatus);
    return groupItems.length ? [{ evidenceStatus, items: groupItems }] : [];
  });
  return {
    items,
    groups,
    page,
    pageSize: window.items.length || 20,
    pageCount: Math.max(1, Math.ceil(window.total / 20)),
    total: window.total,
  };
}

export function projectGuanyijiaReviewShellSnapshot(
  snapshot: GuanyijiaWorkbenchSnapshot,
): GuanyijiaWorkbenchSnapshot {
  const current = snapshot.current;
  const compilation = current?.compilation;
  if (!current || !compilation) return snapshot;
  const { compilation: _body, revisionDiff: _diffBody, revisionHistory: _historyBodies, ...metadata } = current;
  return {
    ...snapshot,
    current: {
      ...metadata,
      compilationSummary: {
        blockCount: compilation.blocks.length,
        sectionCount: standardSectionOrder.length,
        firstReviewSection: standardSectionOrder.find(({ key }) => compilation.blocks.some((block) => (
          block.section === key && block.evidenceStatus !== 'FACT'
        )))?.key ?? standardSectionOrder[0].key,
      },
    },
  };
}

type ActiveRunPointer = {
  schemaVersion: 1;
  projectId: 'guanyijia_erp';
  storyId: 'guanyijia-five-source-v1';
  batchId: string;
  runId: string;
  commandFingerprints: Record<string, string>;
  contentBinding?: DemoContentRunBinding;
  pendingApplies?: Record<string, {
    fingerprint: string;
    beforeDocumentId: string;
    afterDocumentId: string;
  }>;
  pendingResolutions?: Record<string, {
    fingerprint: string;
    decidedAt: string;
  }>;
  pendingAssistantApplies?: Record<string, {
    fingerprint: string;
    proposalId: string;
    beforeDocumentId: string;
    afterDocumentId: string;
    confirmedAt: string;
  }>;
  pendingAssistantTurns?: Record<string, {
    fingerprint: string;
    createdAt: string;
  }>;
  scriptedReviewDecisions?: Record<string, {
    decision: ScriptedReviewDecision;
    actorUserId: string;
    decidedAt: string;
    sourceId: string;
    sourceSnapshotId: string;
    formalBlockId: string;
  }>;
};

const projectId = 'guanyijia_erp' as const;
const storyId = 'guanyijia-five-source-v1' as const;
type PersistedAssistantFacts = Pick<ReviewAssistantKnowledge, 'sources' | 'conflicts'>;
const persistedAssistantFactsCache = new WeakMap<
  StandardizationRun,
  Map<string, Promise<PersistedAssistantFacts>>
>();
export const guanyijiaActiveRunPointerStorageKey = 'linguan:guanyijia-workbench:active:v1';

export function guanyijiaActiveRunPointerStorageKeyFor(batchId: string) {
  return `${guanyijiaActiveRunPointerStorageKey}:${batchId}`;
}

function storyBatchId(sources: readonly Pick<StorySource, 'sourceId' | 'snapshotId'>[]) {
  const fingerprint = sha256HexSync(canonicalModelingJson(sources.map((source) => ({
    sourceId: source.sourceId,
    snapshotId: source.snapshotId,
  }))));
  return `guanyijia-five-source:${fingerprint}`;
}

function assertCommand(command: GuanyijiaWorkbenchCommand) {
  if (!command.commandId.trim()) throw new Error('工作台 commandId 不能为空');
  if (!command.actorUserId.trim()) throw new Error('工作台操作者不能为空');
  if (!Number.isSafeInteger(command.expectedRevision) || command.expectedRevision < 0) {
    throw new Error('工作台 expectedRevision 必须是非负整数');
  }
  const hasDocumentId = command.documentId !== undefined;
  const hasDocumentRevision = command.documentRevision !== undefined;
  if (hasDocumentId !== hasDocumentRevision
    || (hasDocumentId && (!command.documentId?.trim()
      || !Number.isSafeInteger(command.documentRevision)
      || command.documentRevision! < 1))) {
    throw new Error('审阅写入必须携带完整的来源文档身份');
  }
  if (command.type === 'PREVIEW_CURRENT_BLOCK_CHANGE' || command.type === 'APPLY_CURRENT_BLOCK_CHANGE') {
    if (!command.change || typeof command.change.blockId !== 'string' || !command.change.blockId.trim()) {
      throw new Error('结构化块修改必须指定blockId');
    }
    if (!Object.hasOwn(command.change, 'label') && !Object.hasOwn(command.change, 'value')) {
      throw new Error('结构化块修改必须包含名称或识别结果');
    }
    if (command.change.label !== undefined && !command.change.label.trim()) {
      throw new Error('结构化块名称不能为空');
    }
  }
  if (command.type === 'PREVIEW_SCRIPTED_REVIEW_EDIT') {
    if (!command.editId.trim() || !command.patch || typeof command.patch !== 'object') {
      throw new Error('剧本修改预览必须指定编辑项与修改内容');
    }
  }
  if (command.type === 'DECIDE_SCRIPTED_REVIEW_EDIT') {
    if (!command.editId.trim() || !['KEEP_CURRENT', 'APPLY_SUGGESTION'].includes(command.decision)) {
      throw new Error('剧本修改必须选择保留当前结论或采用推荐修改');
    }
    if (command.decision === 'APPLY_SUGGESTION'
      && (!command.expectedPreviewSha256 || !/^sha256:[0-9a-f]{64}$/.test(command.expectedPreviewSha256))) {
      throw new Error('确认剧本修改必须包含已预览的内容摘要');
    }
  }
  if (command.type === 'RESOLVE_CURRENT_CONFLICT') {
    if (!command.conflictId.trim() || !command.reason.trim() || !command.expectedHunkSha256.trim()) {
      throw new Error('冲突决定必须包含冲突标识、中文理由和已预览Hunk SHA');
    }
  }
  if (command.type === 'ASK_REVIEW_ASSISTANT') {
    classifyReviewAssistantIntent(command.message);
    if (!command.selection || typeof command.selection !== 'object') {
      throw new Error('审阅助手上下文选择无效');
    }
  }
  if (command.type === 'CONFIRM_ASSISTANT_PATCH') {
    if (!command.proposalId.trim() || !/^sha256:[0-9a-f]{64}$/.test(command.expectedPreviewSha256)) {
      throw new Error('确认助手修改必须包含Proposal标识和预览SHA');
    }
  }
  if (command.type === 'CANCEL_ASSISTANT_PATCH' && !command.proposalId.trim()) {
    throw new Error('取消助手修改必须指定Proposal');
  }
}

function assertRenderedDocumentIsCurrent(
  command: GuanyijiaWorkbenchCommand,
  currentStep: Pick<StandardizationSourceStep, 'documentId' | 'documentRevision'>,
) {
  // Older stored commands did not carry a rendered-document target.  They are
  // still replayed for compatibility.  Every interactive review write now
  // provides one, and a mismatch is rejected here — before any preview,
  // revision, or decision can be generated.
  if (command.documentId === undefined && command.documentRevision === undefined) return;
  if (command.documentId !== currentStep.documentId
    || command.documentRevision !== currentStep.documentRevision) {
    throw new Error('当前正在查看历史文档，不能修改当前待审阅来源。请返回当前审阅后重试。');
  }
}

function workbenchCommandFingerprint(command: GuanyijiaWorkbenchCommand) {
  return `sha256:${sha256HexSync(canonicalModelingJson(command))}`;
}

function readPointer(storage: Pick<Storage, 'getItem'>, expectedBatchId: string): ActiveRunPointer | null {
  const raw = storage.getItem(guanyijiaActiveRunPointerStorageKeyFor(expectedBatchId));
  if (raw === null) return null;
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new Error('管伊佳标准化活动运行指针已损坏：无法解析 JSON');
  }
  if (typeof value !== 'object' || value === null) {
    throw new Error('管伊佳标准化活动运行指针已损坏：记录必须是对象');
  }
  const pointer = value as Partial<ActiveRunPointer>;
  if (pointer.schemaVersion !== 1
    || pointer.projectId !== projectId
    || pointer.storyId !== storyId
    || pointer.batchId !== expectedBatchId
    || typeof pointer.runId !== 'string'
    || !pointer.runId.trim()
    || typeof pointer.commandFingerprints !== 'object'
    || pointer.commandFingerprints === null
    || Array.isArray(pointer.commandFingerprints)
    || Object.entries(pointer.commandFingerprints).some(([commandId, fingerprint]) => (
      !commandId.trim() || typeof fingerprint !== 'string' || !/^sha256:[0-9a-f]{64}$/.test(fingerprint)
    ))
    || (pointer.pendingApplies !== undefined && (
      typeof pointer.pendingApplies !== 'object'
      || pointer.pendingApplies === null
      || Array.isArray(pointer.pendingApplies)
      || Object.entries(pointer.pendingApplies).some(([commandId, pending]) => (
        !commandId.trim()
        || typeof pending !== 'object' || pending === null
        || typeof pending.fingerprint !== 'string' || !/^sha256:[0-9a-f]{64}$/.test(pending.fingerprint)
        || typeof pending.beforeDocumentId !== 'string' || !pending.beforeDocumentId.trim()
        || typeof pending.afterDocumentId !== 'string' || !pending.afterDocumentId.trim()
      ))
    ))
    || (pointer.pendingResolutions !== undefined && (
      typeof pointer.pendingResolutions !== 'object'
      || pointer.pendingResolutions === null
      || Array.isArray(pointer.pendingResolutions)
      || Object.entries(pointer.pendingResolutions).some(([commandId, pending]) => (
        !commandId.trim()
        || typeof pending !== 'object' || pending === null
        || typeof pending.fingerprint !== 'string' || !/^sha256:[0-9a-f]{64}$/.test(pending.fingerprint)
        || typeof pending.decidedAt !== 'string' || !pending.decidedAt.trim()
      ))
    ))
    || (pointer.pendingAssistantApplies !== undefined && (
      typeof pointer.pendingAssistantApplies !== 'object'
      || pointer.pendingAssistantApplies === null
      || Array.isArray(pointer.pendingAssistantApplies)
      || Object.entries(pointer.pendingAssistantApplies).some(([commandId, pending]) => (
        !commandId.trim() || typeof pending !== 'object' || pending === null
        || typeof pending.fingerprint !== 'string' || !/^sha256:[0-9a-f]{64}$/.test(pending.fingerprint)
        || typeof pending.proposalId !== 'string' || !pending.proposalId.trim()
        || typeof pending.beforeDocumentId !== 'string' || !pending.beforeDocumentId.trim()
        || typeof pending.afterDocumentId !== 'string' || !pending.afterDocumentId.trim()
        || typeof pending.confirmedAt !== 'string' || !pending.confirmedAt.trim()
      ))
    ))
    || (pointer.pendingAssistantTurns !== undefined && (
      typeof pointer.pendingAssistantTurns !== 'object'
      || pointer.pendingAssistantTurns === null
      || Array.isArray(pointer.pendingAssistantTurns)
      || Object.entries(pointer.pendingAssistantTurns).some(([commandId, pending]) => (
        !commandId.trim() || typeof pending !== 'object' || pending === null
        || typeof pending.fingerprint !== 'string' || !/^sha256:[0-9a-f]{64}$/.test(pending.fingerprint)
        || typeof pending.createdAt !== 'string' || !pending.createdAt.trim()
      ))
    ))
    || (pointer.scriptedReviewDecisions !== undefined && (
      typeof pointer.scriptedReviewDecisions !== 'object'
      || pointer.scriptedReviewDecisions === null
      || Array.isArray(pointer.scriptedReviewDecisions)
      || Object.entries(pointer.scriptedReviewDecisions).some(([editId, decision]) => (
        !editId.trim() || typeof decision !== 'object' || decision === null
        || !['KEEP_CURRENT', 'APPLY_SUGGESTION'].includes(decision.decision)
        || typeof decision.actorUserId !== 'string' || !decision.actorUserId.trim()
        || typeof decision.decidedAt !== 'string' || !decision.decidedAt.trim()
        || typeof decision.sourceId !== 'string' || !decision.sourceId.trim()
        || typeof decision.sourceSnapshotId !== 'string' || !decision.sourceSnapshotId.trim()
        || typeof decision.formalBlockId !== 'string' || !decision.formalBlockId.trim()
      ))
    ))) {
    throw new Error('管伊佳标准化活动运行指针已损坏：项目、故事版本或运行标识不匹配');
  }
  return pointer as ActiveRunPointer;
}

function persistPointer(
  storage: PointerStorage,
  batchId: string,
  runId: string,
  command: GuanyijiaWorkbenchCommand,
  contentBinding?: DemoContentRunBinding,
  scriptedDecision?: {
    definition: ScriptedReviewEditDefinition;
    decision: ScriptedReviewDecision;
    actorUserId: string;
    decidedAt: string;
  },
) {
  const current = readPointer(storage, batchId);
  storage.setItem(guanyijiaActiveRunPointerStorageKeyFor(batchId), JSON.stringify({
    schemaVersion: 1,
    projectId,
    storyId,
    batchId,
    runId,
    ...(contentBinding ? { contentBinding } : current?.contentBinding ? { contentBinding: current.contentBinding } : {}),
    commandFingerprints: {
      ...(current?.commandFingerprints ?? {}),
      [command.commandId]: workbenchCommandFingerprint(command),
    },
    ...(current?.pendingApplies && Object.keys(current.pendingApplies).some((id) => id !== command.commandId)
      ? { pendingApplies: Object.fromEntries(Object.entries(current.pendingApplies)
          .filter(([id]) => id !== command.commandId)) }
      : {}),
    ...(current?.pendingResolutions && Object.keys(current.pendingResolutions).some((id) => id !== command.commandId)
      ? { pendingResolutions: Object.fromEntries(Object.entries(current.pendingResolutions)
          .filter(([id]) => id !== command.commandId)) }
      : {}),
    ...(current?.pendingAssistantApplies
      && Object.keys(current.pendingAssistantApplies).some((id) => id !== command.commandId)
      ? { pendingAssistantApplies: Object.fromEntries(Object.entries(current.pendingAssistantApplies)
          .filter(([id]) => id !== command.commandId)) }
      : {}),
    ...(current?.pendingAssistantTurns
      && Object.keys(current.pendingAssistantTurns).some((id) => id !== command.commandId)
      ? { pendingAssistantTurns: Object.fromEntries(Object.entries(current.pendingAssistantTurns)
          .filter(([id]) => id !== command.commandId)) }
      : {}),
    ...((scriptedDecision || current?.scriptedReviewDecisions) ? {
      scriptedReviewDecisions: {
        ...(current?.scriptedReviewDecisions ?? {}),
        ...(scriptedDecision ? {
          [scriptedDecision.definition.editId]: {
            decision: scriptedDecision.decision,
            actorUserId: scriptedDecision.actorUserId,
            decidedAt: scriptedDecision.decidedAt,
            sourceId: scriptedDecision.definition.sourceId,
            sourceSnapshotId: scriptedDecision.definition.sourceSnapshotId,
            formalBlockId: scriptedDecision.definition.formalBlockId,
          },
        } : {}),
      },
    } : {}),
  } satisfies ActiveRunPointer));
}

function formalSourcesForDemoContent(run: StandardizationRun) {
  return run.sources.map((source) => {
    if (!source.snapshotId) throw new Error('运行来源缺少固定快照身份');
    return { sourceId: source.sourceId, snapshotId: source.snapshotId };
  });
}

function contentBindingForRun(run: StandardizationRun, pointer: ActiveRunPointer | null): DemoContentRunBinding | undefined {
  if (!pointer?.contentBinding) return undefined;
  const binding = validateDemoContentRunBinding(pointer.contentBinding);
  const formalSources = formalSourcesForDemoContent(run);
  if (binding.runId !== run.runId
    || binding.sourceBindings.length !== formalSources.length
    || binding.sourceBindings.some((source, index) => (
      source.sourceId !== formalSources[index]?.sourceId
      || source.formalSnapshotId !== formalSources[index]?.snapshotId
    ))) {
    throw new Error('冻结内容快照校验失败');
  }
  return binding;
}

/**
 * Reopens the frozen V6 content binding that was persisted for an existing
 * 管伊佳 run. This is read-only so downstream projections cannot silently
 * substitute the currently active content publication for a reviewed run.
 */
export function readGuanyijiaContentBindingForRun(input: {
  pointerStorage: Pick<Storage, 'getItem'>;
  run: StandardizationRun;
}): DemoContentRunBinding {
  const pointer = readPointer(
    input.pointerStorage,
    storyBatchId(formalSourcesForDemoContent(input.run)),
  );
  const binding = contentBindingForRun(input.run, pointer);
  if (!binding) throw new Error('当前运行未绑定冻结内容快照');
  return binding;
}

function scriptedReviewStatesFor(input: {
  run: StandardizationRun;
  source: StorySource;
  compilation: SourceDocumentCompilation;
  contentBinding?: DemoContentRunBinding;
  pointer: ActiveRunPointer | null;
}): ScriptedReviewEditState[] {
  const definitions = listScriptedReviewEdits({ sourceId: input.source.sourceId });
  if (definitions.length === 0) return [];
  if (!input.contentBinding) throw new Error('当前运行未绑定冻结内容快照');
  const review = readSourceReviewDocument({
    sourceId: input.source.sourceId,
    snapshotId: input.source.snapshotId,
    contentBinding: input.contentBinding,
  });
  if (!review) throw new Error('剧本编辑映射校验失败');
  return definitions.map((definition) => {
    const block = input.compilation.blocks.find((candidate) => candidate.blockId === definition.formalBlockId);
    if (!block) throw new Error('剧本编辑映射校验失败');
    validateScriptedReviewEditBinding({ definition, review, block });
    const decision = input.pointer?.scriptedReviewDecisions?.[definition.editId];
    if (decision && (decision.sourceId !== definition.sourceId
      || decision.sourceSnapshotId !== definition.sourceSnapshotId
      || decision.formalBlockId !== definition.formalBlockId)) {
      throw new Error('剧本编辑映射校验失败');
    }
    return {
      definition,
      status: decision?.decision === 'KEEP_CURRENT'
        ? 'KEPT'
        : decision?.decision === 'APPLY_SUGGESTION'
          ? 'APPLIED'
          : 'PENDING',
      ...(decision ? {
        decision: decision.decision,
        actorUserId: decision.actorUserId,
        decidedAt: decision.decidedAt,
      } : {}),
    } satisfies ScriptedReviewEditState;
  });
}

function scriptedPreviewSha256(input: {
  run: StandardizationRun;
  sourceId: string;
  documentId: string;
  documentRevision: number;
  editId: string;
  beforeBlock: SourceDocumentBlock;
  change: GuanyijiaWorkbenchBlockChange;
  preview: GuanyijiaBlockChangePreview;
}): `sha256:${string}` {
  return `sha256:${sha256HexSync(canonicalModelingJson(input))}`;
}

function persistPendingAssistantTurn(
  storage: PointerStorage,
  batchId: string,
  runId: string,
  command: GuanyijiaWorkbenchCommand & { type: 'ASK_REVIEW_ASSISTANT' },
  createdAt: string,
) {
  const current = readPointer(storage, batchId);
  if (!current) throw new Error('管伊佳标准化活动运行指针不存在');
  storage.setItem(guanyijiaActiveRunPointerStorageKeyFor(batchId), JSON.stringify({
    ...current,
    runId,
    pendingAssistantTurns: {
      ...(current.pendingAssistantTurns ?? {}),
      [command.commandId]: {
        fingerprint: workbenchCommandFingerprint(command), createdAt,
      },
    },
  } satisfies ActiveRunPointer));
}

function persistPendingResolution(
  storage: PointerStorage,
  batchId: string,
  runId: string,
  command: GuanyijiaWorkbenchCommand & { type: 'RESOLVE_CURRENT_CONFLICT' },
  decidedAt: string,
) {
  const current = readPointer(storage, batchId);
  if (!current) throw new Error('管伊佳标准化活动运行指针不存在');
  storage.setItem(guanyijiaActiveRunPointerStorageKeyFor(batchId), JSON.stringify({
    ...current,
    runId,
    pendingResolutions: {
      ...(current.pendingResolutions ?? {}),
      [command.commandId]: { fingerprint: workbenchCommandFingerprint(command), decidedAt },
    },
  } satisfies ActiveRunPointer));
}

function persistPendingApply(
  storage: PointerStorage,
  batchId: string,
  runId: string,
  command: Extract<GuanyijiaWorkbenchCommand, {
    type: 'APPLY_CURRENT_BLOCK_CHANGE' | 'DECIDE_SCRIPTED_REVIEW_EDIT';
  }>,
  beforeDocumentId: string,
  afterDocumentId: string,
) {
  const current = readPointer(storage, batchId);
  if (!current) throw new Error('管伊佳标准化活动运行指针不存在');
  storage.setItem(guanyijiaActiveRunPointerStorageKeyFor(batchId), JSON.stringify({
    ...current,
    runId,
    pendingApplies: {
      ...(current.pendingApplies ?? {}),
      [command.commandId]: {
        fingerprint: workbenchCommandFingerprint(command), beforeDocumentId, afterDocumentId,
      },
    },
  } satisfies ActiveRunPointer));
}

function persistPendingAssistantApply(
  storage: PointerStorage,
  batchId: string,
  runId: string,
  command: GuanyijiaWorkbenchCommand & { type: 'CONFIRM_ASSISTANT_PATCH' },
  input: {
    beforeDocumentId: string;
    afterDocumentId: string;
    confirmedAt: string;
  },
) {
  const current = readPointer(storage, batchId);
  if (!current) throw new Error('管伊佳标准化活动运行指针不存在');
  storage.setItem(guanyijiaActiveRunPointerStorageKeyFor(batchId), JSON.stringify({
    ...current,
    runId,
    pendingAssistantApplies: {
      ...(current.pendingAssistantApplies ?? {}),
      [command.commandId]: {
        fingerprint: workbenchCommandFingerprint(command),
        proposalId: command.proposalId,
        beforeDocumentId: input.beforeDocumentId,
        afterDocumentId: input.afterDocumentId,
        confirmedAt: input.confirmedAt,
      },
    },
  } satisfies ActiveRunPointer));
}

function assertRunMatchesStory(run: StandardizationRun, sources: StorySource[]) {
  const matches = run.sources.length === sources.length && run.sources.every((step, index) => {
    const source = sources[index];
    return source !== undefined
      && step.sourceId === source.sourceId
      && step.sourceName === source.sourceName
      && step.order === index + 1;
  });
  if (!matches) throw new Error('标准化运行来源与当前标准化流程不匹配');
}

function selectCurrentReviewStep(run: StandardizationRun): StandardizationSourceStep | undefined {
  const active = run.sources.find((step) => (
    step.status === 'READING' || step.status === 'DOCUMENT_READY'
  ));
  if (active) return active;
  return [...run.sources]
    .filter((step) => (
      Boolean(step.documentId)
      && ['REVIEWED', 'CONFLICT_BLOCKED', 'ALIGNED'].includes(step.status)
    ))
    .sort((left, right) => right.order - left.order)[0];
}

function selectFirstUnresolvedConflict(run: StandardizationRun): {
  sourceStep: StandardizationSourceStep;
  conflictId: string;
} | undefined {
  for (const sourceStep of [...run.sources].sort((left, right) => left.order - right.order)) {
    const conflictId = sourceStep.introducedConflictIds.find((id) => (
      !sourceStep.resolvedConflictIds.includes(id)
    ));
    if (conflictId) return { sourceStep, conflictId };
  }
  return undefined;
}

function canResolveFirstUnresolvedConflict(
  run: StandardizationRun,
  unresolved: ReturnType<typeof selectFirstUnresolvedConflict>,
) {
  if (!unresolved) return false;
  return run.status === 'CONFLICT_BLOCKED' || run.status === 'READY'
    || (run.status === 'REVIEWING_DOCUMENT' && unresolved.sourceStep.status === 'DOCUMENT_READY');
}

function assertCurrentDocumentRevisionUnlocked(step: StandardizationSourceStep) {
  if (step.resolvedConflictIds.length) {
    throw new Error('已保存来源差异后不能修改来源文档');
  }
}

function unresolvedConflictCount(run: StandardizationRun) {
  return run.sources.reduce((count, sourceStep) => (
    count + sourceStep.introducedConflictIds.filter((id) => !sourceStep.resolvedConflictIds.includes(id)).length
  ), 0);
}

function nextAction(run: StandardizationRun | null): GuanyijiaWorkbenchSnapshot['nextAction'] {
  if (!run) return { type: 'START_RUN', label: '开始资料整理' };
  if (run.status === 'READY') {
    const pending = run.sources.find((source) => source.status === 'PENDING');
    if (pending) return { type: 'READ_NEXT_SOURCE', label: `载入${displaySourceName(pending.sourceId, pending.sourceName)}快照` };
    const unresolved = selectFirstUnresolvedConflict(run);
    return unresolved
      ? { type: 'RESOLVE_CONFLICT', label: `处理${unresolvedConflictCount(run)}项来源差异` }
      : { type: 'NONE', label: '来源资料已整理完成' };
  }
  if (run.status === 'REVIEWING_DOCUMENT') {
    const current = run.sources.find((source) => source.status === 'DOCUMENT_READY');
    return { type: 'REVIEW_DOCUMENT', label: `继续审阅${current ? displaySourceName(current.sourceId, current.sourceName) : '当前来源'}` };
  }
  if (run.status === 'CONFLICT_BLOCKED') {
    const pending = run.sources.find((source) => source.status === 'PENDING');
    if (pending) {
      return { type: 'READ_NEXT_SOURCE', label: `载入${displaySourceName(pending.sourceId, pending.sourceName)}快照` };
    }
    const unresolved = selectFirstUnresolvedConflict(run);
    return unresolved
      ? { type: 'RESOLVE_CONFLICT', label: `处理${unresolvedConflictCount(run)}项来源差异` }
      : { type: 'NONE', label: '来源资料已整理完成' };
  }
  if (run.status === 'READING_SOURCE') return { type: 'NONE', label: '正在载入当前快照' };
  return { type: 'NONE', label: '当前 Checkpoint 暂无可执行操作' };
}

const objectKindLabel = {
  ENTITY: '实体', EVENT: '事件', FIELD: '字段', RELATION: '关系', DIMENSION: '维度',
  METRIC: '指标', HIERARCHY: '层级', RULE: '规则', ALIAS: '同义词', TIME_RULE: '时间语义',
  EXCLUSION: '排除项',
} as const;

export function factsInspectorFor(
  snapshot: GuanyijiaWorkbenchSnapshot,
  blockId?: string,
): StandardizationFactsInspectorModel | undefined {
  const current = snapshot.current;
  const compilation = current?.reviewCompilation ?? current?.compilation;
  if (!current || !compilation) return undefined;
  const effectiveBlockId = blockId ?? snapshot.currentConflict?.hunk.incoming.block.blockId;
  const block = (effectiveBlockId
    ? compilation.blocks.find((candidate) => candidate.blockId === effectiveBlockId)
    : undefined)
    ?? compilation.blocks[0];
  return inspectorForCompilation(snapshot.run, current.source, compilation, block);
}

function inspectorForCompilation(
  run: StandardizationRun | null,
  source: StorySource,
  compilation: Pick<SourceDocumentCompilation,
    'sourceId' | 'blocks' | 'evidenceLocators' | 'readSummary' | 'lineageStatus' | 'upstreamSourceIds'>,
  block?: SourceDocumentBlock,
): StandardizationFactsInspectorModel {
  const evidenceRef = block?.evidenceRefs.find((ref) => compilation.evidenceLocators[ref] !== undefined);
  const upstreamSourceNames = compilation.upstreamSourceIds.map((sourceId) => (
    run?.sources.find((candidate) => candidate.sourceId === sourceId)?.sourceName ?? sourceId
  ));
  return {
    sourceId: source.sourceId,
    sourceName: source.sourceName,
    sourceClass: source.sourceClass,
    snapshotId: source.snapshotId,
    versionRef: compilation.readSummary.versionRef,
    authority: source.authority,
    readSummary: compilation.readSummary.summary,
    objectCount: compilation.readSummary.objectCount,
    evidenceCount: compilation.readSummary.evidenceCount,
    lineage: {
      status: compilation.lineageStatus,
      upstreamSourceNames,
      ...(source.sourceClass === 'DERIVED'
        ? { independentEvidenceNote: '派生来源，不增加独立佐证数' }
        : {}),
    },
    ...(block ? {
      block: {
        blockId: block.blockId,
        label: block.label,
        evidenceStatus: block.evidenceStatus,
        ...(evidenceRef ? { locator: compilation.evidenceLocators[evidenceRef] as Record<string, unknown> } : {}),
        affectedObjects: block.affectedObjectRefs.map((ref) => `${objectKindLabel[ref.kind]}：${ref.objectId}`),
        readableEvidence: buildHumanReadableEvidence(compilation, block.blockId),
      },
    } : {}),
  };
}

function countBlocks(compilation: SourceDocumentCompilation) {
  return Object.fromEntries(['FACT', 'INFERENCE', 'GAP', 'CONFLICT'].map((status) => [
    status,
    compilation.blocks.filter((block) => block.evidenceStatus === status).length,
  ])) as Record<SourceDocumentBlock['evidenceStatus'], number>;
}

function previewFromDiff(diff: SourceCompilationDiff): GuanyijiaBlockChangePreview {
  const block = diff.blockChanges[0];
  const assertion = diff.assertionChanges[0];
  const section = diff.sectionChanges[0];
  if (!block || !assertion || !section || diff.blockChanges.length !== 1) {
    throw new Error('当前结构化块修改没有形成唯一可预览差异');
  }
  return {
    block,
    assertion,
    section: { ...section, lines: diffSourceDocumentLines(section.before, section.after) },
    markdown: {
      ...diff.markdown,
      lines: diffSourceDocumentLines(diff.markdown.before, diff.markdown.after),
    },
    affectedConflicts: diff.affectedConflicts,
    affectedObjects: diff.affectedObjectRefs.map((reference) => `${objectKindLabel[reference.kind]}：${reference.objectId}`),
  };
}

function resolvedConflictForTimelineEvent(input: {
  run: StandardizationRun;
  eventId: string;
  sourceId?: string;
  resolvedConflictIds: readonly string[] | undefined;
}) {
  if (!input.sourceId || !input.resolvedConflictIds) return undefined;
  const resolutionOrdinal = input.run.timeline
    .filter((event) => event.type === 'CONFLICT_RESOLVED' && event.sourceId === input.sourceId)
    .findIndex((event) => event.eventId === input.eventId);
  return resolutionOrdinal < 0 ? undefined : input.resolvedConflictIds[resolutionOrdinal];
}

function timelineFor(input: {
  run: StandardizationRun;
  compilations: SourceDocumentCompilation[];
  documents: Map<string, SourceModelingDocument>;
  legacyDocuments: Map<string, {
    sections: SourceDocumentSections;
    assertions: StructuredModelingAssertion[];
    markdown: string;
  }>;
  generatedEvents: Map<string, SourceModelingDocument>;
  revisionEvents: Map<string, {
    document: SourceModelingDocument;
    diff: SourceDocumentRevisionDiff;
  }>;
  story: GuanyijiaStandardizationStory;
  assistantTurns: Map<string, ReviewAssistantTurnArtifact>;
}): WorkbenchTimelineItem[] {
  const compilationBySource = new Map(input.compilations.map((item) => [item.sourceId, item]));
  const sourceById = new Map(input.story.listSources().map((item) => [item.sourceId, item]));
  const conflicts = new Map(input.story.listConflictDefinitions().map((item) => [item.conflictId, item]));
  const currentSourceId = selectCurrentReviewStep(input.run)?.sourceId;
  const currentEventId = [...input.run.timeline].reverse().find((event) => {
    if (input.run.status === 'READING_SOURCE') return event.type === 'SOURCE_READ_STARTED';
    if (input.run.status === 'REVIEWING_DOCUMENT') {
      return event.sourceId === currentSourceId
        && (event.type === 'DOCUMENT_GENERATED' || event.type === 'DOCUMENT_REVISED');
    }
    if (input.run.status === 'CONFLICT_BLOCKED') return event.type === 'CONFLICT_FOUND';
    return false;
  })?.eventId;
  const deliverableAction = (event: StandardizationTimelineEvent) => {
    const mergedDocumentRef = event.deliveryContentIndex?.entries.find((entry) => (
      entry.key === 'mergedDocumentRef'
    ))?.contentRef;
    return {
      type: 'OPEN_DELIVERABLE' as const,
      ...(mergedDocumentRef ? { mergedDocumentRef } : {}),
    };
  };
  const priorDeliverableAction = (event: StandardizationTimelineEvent) => {
    const prior = input.run.timeline.slice(0, Math.max(0, event.sequence - 1)).reverse().find((candidate) => (
      candidate.type === 'DELIVERABLE_GENERATED'
    ));
    return prior ? deliverableAction(prior) : { type: 'OPEN_DELIVERABLE' as const };
  };
  const items: WorkbenchTimelineItem[] = [{
    itemId: `${input.run.runId}:started`,
    kind: 'RUN_STARTED',
    state: input.run.timeline.length ? 'RECEIPT' : 'CURRENT',
    title: '开始资料整理',
    summary: `按固定顺序载入 ${input.run.sources.length} 份演示快照；每份文档审阅完成后才继续。`,
    createdAt: input.run.createdAt,
  }];
  for (const event of input.run.timeline) {
    const source = eventSource(event, input.run.sources);
    const storySource = event.sourceId ? sourceById.get(event.sourceId) : undefined;
    const compilation = event.sourceId ? compilationBySource.get(event.sourceId) : undefined;
    const document = event.sourceId ? input.documents.get(event.sourceId) : undefined;
    const base = {
      itemId: event.eventId,
      eventId: event.eventId,
      state: event.eventId === currentEventId ? 'CURRENT' as const : 'RECEIPT' as const,
      ...(event.sourceId ? { sourceId: event.sourceId } : {}),
      createdAt: event.createdAt,
      contentRef: event.payloadRef,
      contentSha256: event.payloadRef.slice('sha256:'.length),
      ...(storySource && compilation ? {
        inspector: inspectorForCompilation(input.run, storySource, compilation),
      } : {}),
    };
    if (event.type === 'SOURCE_READ_STARTED') {
      items.push({ ...base, kind: 'SOURCE_READ_STARTED', title: `载入${source ? displaySourceName(source.sourceId, source.sourceName) : '来源'}快照`, summary: '校验固定快照身份并载入冻结范围。' });
    } else if (event.type === 'SOURCE_READ_COMPLETED') {
      items.push({
        ...base,
        kind: 'SOURCE_READ_COMPLETED',
        ...(event.sourceId ? { action: { type: 'OPEN_SOURCE_DETAILS' as const, sourceId: event.sourceId } } : {}),
        title: `${source ? displaySourceName(source.sourceId, source.sourceName) : '来源'}快照已载入`,
        summary: '固定快照已载入；可打开审阅事项查看已保存资料。',
      });
    } else if (event.type === 'DOCUMENT_GENERATED' && compilation && document) {
      const generatedDocument = input.generatedEvents.get(event.eventId);
      if (!generatedDocument) throw new Error(`来源文档生成事件缺少真实revision投影：${event.eventId}`);
      items.push({
        ...base,
        kind: 'DOCUMENT_GENERATED',
        title: `${displaySourceName(compilation.sourceId, compilation.sourceName)}来源文档已生成`,
        summary: '审阅文档已准备好；打开后可查看审阅结论和来源材料。',
        document: {
          documentId: generatedDocument.documentId,
          revision: generatedDocument.revision,
          sectionCount: Object.keys(compilation.sections).length,
          blockCounts: countBlocks(compilation),
        },
        action: { type: 'OPEN_DOCUMENT', documentId: generatedDocument.documentId, revision: generatedDocument.revision },
      });
    } else if (event.type === 'DOCUMENT_GENERATED' && document && input.legacyDocuments.has(event.sourceId!)) {
      const generatedDocument = input.generatedEvents.get(event.eventId);
      if (!generatedDocument) throw new Error(`来源文档生成事件缺少真实revision投影：${event.eventId}`);
      items.push({
        ...base,
        kind: 'DOCUMENT_GENERATED',
        title: `${source ? displaySourceName(source.sourceId, source.sourceName) : '来源'}旧版来源文档已载入`,
        summary: '9 个章节 · 没有结构化识别项，仅支持只读',
      });
    } else if (event.type === 'DOCUMENT_REVISED' && compilation && document) {
      const revisionEvent = input.revisionEvents.get(event.eventId);
      if (!revisionEvent) throw new Error(`来源文档修订事件缺少真实revision投影：${event.eventId}`);
      const labels = revisionEvent.diff.blockChanges.flatMap((change) => (
        change.after?.label ?? change.before?.label ?? []
      ));
      items.push({
        ...base,
        kind: 'DOCUMENT_REVISED',
        title: `${displaySourceName(compilation.sourceId, compilation.sourceName)}来源文档已修订`,
        summary: `${event.actor.userId} 修改了${labels.length ? `“${labels.join('、')}”` : '结构化识别结果'}；已生成新的文档版本。`,
        document: {
          documentId: revisionEvent.document.documentId,
          revision: revisionEvent.document.revision,
          sectionCount: Object.keys(compilation.sections).length,
          blockCounts: countBlocks(compilation),
        },
        action: { type: 'OPEN_DOCUMENT', documentId: revisionEvent.document.documentId, revision: revisionEvent.document.revision },
      });
    } else if (event.type === 'DOCUMENT_REVIEWED') {
      items.push({
        ...base,
        kind: 'DOCUMENT_REVIEWED',
        title: `${source ? displaySourceName(source.sourceId, source.sourceName) : '来源'}文档审阅完成`,
        summary: '本份来源文档已完成审阅，可随时重新查看。',
        ...(document && compilation ? { document: {
          documentId: document.documentId,
          revision: document.revision,
          sectionCount: Object.keys(compilation.sections).length,
          blockCounts: countBlocks(compilation),
        } } : {}),
        ...(document ? { action: { type: 'OPEN_DOCUMENT' as const, documentId: document.documentId, revision: document.revision } } : {}),
      });
    } else if (event.type === 'CONFLICT_FOUND') {
      const definitions = (source?.introducedConflictIds ?? [])
        .flatMap((conflictId) => {
          const definition = conflicts.get(conflictId);
          return definition ? [{
            conflictId,
            title: definition.title,
            affectedObjects: definition.affectedObjectRefs.map((ref) => `${objectKindLabel[ref.kind]}：${ref.objectId}`),
          }] : [];
        });
      if (!definitions.length) {
        items.push({
          ...base,
          kind: 'CONFLICT_FOUND',
          title: '发现来源差异',
          summary: '需要人工处理后才能继续。',
          conflicts: [],
        });
      } else {
        definitions.forEach((definition, index) => {
          items.push({
            ...base,
            itemId: `${event.eventId}:${definition.conflictId}`,
            state: index === 0 ? base.state : 'RECEIPT',
            ...(storySource && compilation
              ? { inspector: inspectorForCompilation(
                  input.run,
                  storySource,
                  compilation,
                  compilation.blocks.find((block) => block.stableCode === conflicts.get(
                    definition.conflictId,
                  )?.incoming.stableCode),
                ) }
              : {}),
            kind: 'CONFLICT_FOUND',
            title: definition.title,
            summary: `影响 ${definition.affectedObjects.join('、')}`,
            conflicts: [definition],
            action: { type: 'OPEN_CONFLICT', conflictId: definition.conflictId },
          });
        });
      }
    } else if (event.type === 'CONFLICT_RESOLVED') {
      const conflictId = resolvedConflictForTimelineEvent({
        run: input.run,
        eventId: event.eventId,
        sourceId: event.sourceId,
        resolvedConflictIds: source?.resolvedConflictIds,
      });
      items.push({
        ...base,
        kind: 'CONFLICT_RESOLVED',
        title: '来源差异已决定',
        summary: '已保存决定、来源材料和结果；可随时重新查看。',
        ...(conflictId ? { action: { type: 'OPEN_CONFLICT' as const, conflictId } } : {}),
      });
    } else if (event.type === 'CONFLICT_DECISION_REPLACED') {
      items.push({
        ...base,
        kind: 'CONFLICT_DECISION_REPLACED',
        title: '来源差异决定已更新',
        summary: '上一决定已保留在历史中；当前决定已更新，重新生成标准化结果后即可定版。',
        ...(event.conflictId ? { action: { type: 'OPEN_CONFLICT' as const, conflictId: event.conflictId } } : {}),
      });
    } else if (event.type === 'DELIVERABLE_GENERATED') {
      items.push({ ...base, kind: 'DELIVERABLE_GENERATED', title: '标准化结果已生成', summary: '可查看完整文档、可建模结论和已排除事项。', action: deliverableAction(event) });
    } else if (event.type === 'DELIVERABLE_SUPERSEDED') {
      items.push({
        ...base,
        kind: 'DELIVERABLE_SUPERSEDED',
        title: '标准化结果需要重新生成',
        summary: '来源差异决定已更新；上一份结果已保留，重新生成后即可定版。',
        action: priorDeliverableAction(event),
      });
    } else if (event.type === 'DELIVERABLE_FROZEN') {
      items.push({ ...base, kind: 'DELIVERABLE_FROZEN', title: '标准化结果已定版', summary: '定版文档可回看；正式模型尚未改变。', action: { type: 'OPEN_DELIVERABLE' } });
    } else if (event.type === 'MODELING_HANDOFF_COMPLETED') {
      items.push({ ...base, kind: 'MODELING_HANDOFF_COMPLETED', title: '标准化文档已交给 AI 建模', summary: '可回看交接文档、建模候选和已排除事项。', action: { type: 'OPEN_DELIVERABLE' } });
    } else if (event.type === 'CONFLICT_CORROBORATED') {
      items.push({
        ...base,
        kind: 'CONFLICT_CORROBORATED',
        title: '派生佐证已回链',
        summary: 'Semantica 仅追加佐证回执，不改变已保存决定。',
      });
    } else if (event.type === 'ASSISTANT_TURN_RECORDED') {
      items.push({
        ...base,
        kind: 'ASSISTANT_TURN_RECORDED',
        title: event.assistantProposalId ? '审阅助手已生成修改建议' : '审阅助手已回答',
        summary: '审阅助手已记录本次说明；可继续查看或处理建议。',
      });
    } else if (event.type === 'ASSISTANT_PATCH_CONFIRMED') {
      items.push({
        ...base,
        kind: 'ASSISTANT_PATCH_CONFIRMED',
        title: '审阅助手修改已确认',
        summary: '建议修改已写入当前审阅文档。',
      });
    } else if (event.type === 'ASSISTANT_PATCH_CANCELLED') {
      items.push({
        ...base,
        kind: 'ASSISTANT_PATCH_CANCELLED',
        title: '审阅助手建议已取消',
        summary: '建议未采用；当前审阅文档保持不变。',
      });
    }
  }
  return items;
}

async function readGuanyijiaPersistedSourceRevisionPrefix(input: {
  run: StandardizationRun;
  sourceDocuments: SourceDocumentRuntime;
  story: GuanyijiaStandardizationStory;
  throughSourceIndex: number;
  skipConflictProjectionAtIndex?: number;
  skipConflictProjection?: boolean;
}) {
  const sources = input.story.listSources();
  const compilations: SourceDocumentCompilation[] = [];
  const sourceRevisions = [] as Array<{
    documentId: string;
    documentRevision: number;
    compilation: SourceDocumentCompilation;
  }>;
  for (let index = 0; index <= input.throughSourceIndex; index += 1) {
    const source = sources[index];
    const step = input.run.sources[index];
    if (!source || !step?.documentId || !step.documentRevision || step.sourceId !== source.sourceId) {
      throw new Error('冲突决定缺少运行登记的持久化来源revision');
    }
    const base = input.story.compileSource({ sourceId: source.sourceId, priorCompilations: compilations });
    const document = await input.sourceDocuments.read(step.documentId);
    if (!document || !document.blocksRef || document.projectId !== projectId
      || document.documentCode !== `guanyijia-five-source--${source.sourceId}`
      || document.sourceSnapshotId !== source.snapshotId || document.sourceType !== source.sourceClass
      || document.sourceName !== source.sourceName || document.revision !== step.documentRevision) {
      throw new Error(`冲突决定引用的来源文档身份不匹配：${step.documentId}`);
    }
    const [blocks, sections, assertions, markdown] = await Promise.all([
      input.sourceDocuments.readBlocks(document.documentId),
      input.sourceDocuments.readSections(document.documentId),
      input.sourceDocuments.readAssertions(document.documentId),
      input.sourceDocuments.readMarkdown(document.documentId),
    ]);
    if (blocks.length !== base.blocks.length) {
      throw new Error(`冲突决定来源文档结构化块数量不一致：${document.documentId}`);
    }
    const changes = blocks.flatMap((block) => {
      const original = base.blocks.find((candidate) => candidate.blockId === block.blockId);
      if (!original) throw new Error(`冲突决定来源文档包含未知结构化块：${block.blockId}`);
      const immutableBefore = { ...original, label: undefined, value: undefined };
      const immutableAfter = { ...block, label: undefined, value: undefined };
      if (canonicalModelingJson(immutableBefore) !== canonicalModelingJson(immutableAfter)) {
        throw new Error(`冲突决定来源文档篡改了不可修改字段：${block.blockId}`);
      }
      return original.label === block.label
        && canonicalModelingJson(original.value) === canonicalModelingJson(block.value)
        ? []
        : [{ blockId: block.blockId, label: block.label, value: block.value }];
    });
    const compilation = changes.length
      ? input.story.reviseCompilation({ compilation: base, priorCompilations: compilations, changes }).compilation
      : base;
    if (canonicalModelingJson(compilation.blocks) !== canonicalModelingJson(blocks)
      || canonicalModelingJson(compilation.sections) !== canonicalModelingJson(sections)
      || canonicalModelingJson(compilation.assertions) !== canonicalModelingJson(assertions)
      || markdown !== renderSourceDocumentMarkdown({ ...document, sections, assertions })
      || document.markdownSha256 !== document.markdownRef.slice('sha256:'.length)
      || (!input.skipConflictProjection && index !== input.skipConflictProjectionAtIndex
        && canonicalModelingJson(compilation.introducedConflictIds)
          !== canonicalModelingJson(step.introducedConflictIds))) {
      throw new Error(`冲突决定来源文档无法通过Story持久化投影校验：${document.documentId}`);
    }
    compilations.push(compilation);
    sourceRevisions.push({
      documentId: document.documentId,
      documentRevision: document.revision,
      compilation,
    });
  }
  return sourceRevisions;
}

export async function validateGuanyijiaResolutionArtifactAgainstPersistedRevisions(input: {
  artifact: ConflictResolutionArtifact;
  run: StandardizationRun;
  sourceDocuments: SourceDocumentRuntime;
  story: GuanyijiaStandardizationStory;
}) {
  input.story.validateConflictResolutionArtifactProjection(input.artifact);
  const sources = input.story.listSources();
  const definition = input.story.listConflictDefinitions().find((candidate) => (
    candidate.conflictId === input.artifact.hunk.conflictId
  ));
  if (!definition || input.artifact.runId !== input.run.runId
    || input.artifact.sourceId !== definition.introducedBySourceId) {
    throw new Error('冲突决定Artifact与当前运行或故事定义不一致');
  }
  const introducedIndex = sources.findIndex((source) => source.sourceId === definition.introducedBySourceId);
  const sourceRevisions = await readGuanyijiaPersistedSourceRevisionPrefix({
    run: input.run,
    sourceDocuments: input.sourceDocuments,
    story: input.story,
    throughSourceIndex: introducedIndex,
  });
  const expected = input.story.previewConflictResolution({
    conflictId: definition.conflictId,
    strategy: input.artifact.strategy,
    sourceRevisions,
  });
  const actual = {
    hunk: input.artifact.hunk,
    strategy: input.artifact.strategy,
    result: input.artifact.result,
    structuredPatch: input.artifact.structuredPatch,
    markdownDiff: input.artifact.markdownDiff,
    provenanceSources: input.artifact.provenanceSources,
    previewSha256: input.artifact.previewSha256,
  };
  if (canonicalModelingJson(actual) !== canonicalModelingJson(expected)) {
    throw new Error('冲突决定Artifact无法由运行登记的持久化revision复算');
  }
}

export function validateGuanyijiaCorroborationReceipt(input: {
  receipt: { runId: string; sourceId: string; conflictId: string };
  run: StandardizationRun;
  story: GuanyijiaStandardizationStory;
}) {
  const source = input.story.listSources().find((candidate) => candidate.sourceId === input.receipt.sourceId);
  const definition = input.story.listConflictDefinitions().find((candidate) => (
    candidate.conflictId === input.receipt.conflictId
  ));
  const isDeclaredCorroborator = definition?.corroborating.some((candidate) => (
    candidate.sourceId === input.receipt.sourceId
    && candidate.upstreamSourceId === definition.introducedBySourceId
  ));
  if (input.receipt.runId !== input.run.runId || !source
    || source.sourceClass !== 'DERIVED' || source.authority !== 'DERIVED'
    || !isDeclaredCorroborator) {
    throw new Error('佐证回执来源不是Story声明的Corroborating来源');
  }
}

export async function validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions(input: {
  projection: { runId: string; sourceId: string; conflictIds: string[] };
  run: StandardizationRun;
  sourceDocuments: SourceDocumentRuntime;
  story: GuanyijiaStandardizationStory;
}) {
  const sources = input.story.listSources();
  const sourceIndex = sources.findIndex((source) => source.sourceId === input.projection.sourceId);
  const step = input.run.sources[sourceIndex];
  if (input.projection.runId !== input.run.runId) {
    throw new Error('佐证回执集合与当前运行或Story来源不一致');
  }
  if (sourceIndex < 0 || !step || step.sourceId !== input.projection.sourceId) {
    if (input.projection.conflictIds.length) {
      throw new Error('佐证回执集合与当前运行或Story来源不一致');
    }
    return;
  }
  const isDeclaredCorroboratingSource = input.story.listConflictDefinitions().some((definition) => (
    definition.corroborating.some((candidate) => candidate.sourceId === input.projection.sourceId)
  ));
  const sourceAdmissionSequence = input.run.timeline.find((event) => (
    event.type === 'SOURCE_READ_COMPLETED' && event.sourceId === input.projection.sourceId
  ))?.sequence;
  const resolvedAtSourceAdmission = new Set(
    sourceAdmissionSequence === undefined
      ? input.run.sources.flatMap((source) => source.resolvedConflictIds)
      : input.run.sources.flatMap((source) => {
        const resolutionEvents = input.run.timeline.filter((event) => (
          event.type === 'CONFLICT_RESOLVED' && event.sourceId === source.sourceId
        ));
        return resolutionEvents.flatMap((event, index) => (
          event.sequence < sourceAdmissionSequence && source.resolvedConflictIds[index]
            ? [source.resolvedConflictIds[index]!]
            : []
        ));
      }),
  );
  let expectedConflictIds: string[] = [];
  if (isDeclaredCorroboratingSource && step.documentId && step.documentRevision) {
    let generatedDocument = await input.sourceDocuments.read(step.documentId);
    if (!generatedDocument) {
      throw new Error(`佐证来源文档不存在：${step.documentId}`);
    }
    while (generatedDocument.derivedFromDocumentId) {
      const parent = await input.sourceDocuments.read(generatedDocument.derivedFromDocumentId);
      if (!parent || parent.documentCode !== generatedDocument.documentCode
        || parent.projectId !== generatedDocument.projectId
        || parent.revision !== generatedDocument.revision - 1) {
        throw new Error(`佐证来源文档revision血缘无效：${generatedDocument.documentId}`);
      }
      generatedDocument = parent;
    }
    const generatedRun = structuredClone(input.run);
    generatedRun.sources[sourceIndex]!.documentId = generatedDocument.documentId;
    generatedRun.sources[sourceIndex]!.documentRevision = generatedDocument.revision;
    const revisions = await readGuanyijiaPersistedSourceRevisionPrefix({
      run: generatedRun,
      sourceDocuments: input.sourceDocuments,
      story: input.story,
      throughSourceIndex: sourceIndex,
    });
    expectedConflictIds = revisions.at(-1)!.compilation.corroboratedConflictIds.filter((conflictId) => (
      resolvedAtSourceAdmission.has(conflictId)
    ));
  }
  if (canonicalModelingJson(input.projection.conflictIds)
    !== canonicalModelingJson(expectedConflictIds)) {
    throw new Error('佐证回执集合不是持久化来源revision的唯一投影');
  }
}

export async function validateGuanyijiaAssistantArtifactAgainstPersistedRun(input: {
  turn: unknown;
  proposal: unknown | undefined;
  run: StandardizationRun;
  sourceDocuments: SourceDocumentRuntime;
  story: GuanyijiaStandardizationStory;
  eventIndex: number;
}) {
  const turn = input.turn as ReviewAssistantTurnArtifact;
  const proposal = input.proposal as AssistantStructuredChangeProposal | undefined;
  if (!turn || turn.schemaVersion !== 1 || turn.runId !== input.run.runId
    || typeof turn.message !== 'string' || typeof turn.response !== 'object' || turn.response === null) {
    throw new Error('审阅助手Turn与当前运行不一致');
  }
  const intent = classifyReviewAssistantIntent(turn.message);
  if (turn.message !== intent.normalizedMessage) {
    throw new Error('审阅助手Turn消息不是确定性规范化投影');
  }
  if ((intent.kind === 'PROPOSE_BLOCK_CHANGE') !== (turn.response.kind === 'PATCH_PREVIEW')) {
    throw new Error('审阅助手回复不是确定性意图的唯一投影');
  }
  const storySources = input.story.listSources();
  const sourceById = new Map(storySources.map((source) => [source.sourceId, source]));
  if (!Number.isSafeInteger(input.eventIndex) || input.eventIndex < 0
    || input.eventIndex > input.run.timeline.length) {
    throw new Error('审阅助手Turn事件时点无效');
  }
  if (turn.knowledgeSourceRevisions.some((revision, index) => (
    revision.sourceId !== storySources[index]?.sourceId
  ))) throw new Error('审阅助手Turn事实revision必须保持Story来源前缀');
  const knowledgeRevisionBySource = new Map(turn.knowledgeSourceRevisions.map((revision) => [
    revision.sourceId, revision,
  ]));
  const eventRun = structuredClone(input.run);
  eventRun.timeline = eventRun.timeline.slice(0, input.eventIndex);
  for (const step of eventRun.sources) {
    const revision = knowledgeRevisionBySource.get(step.sourceId);
    if (revision) {
      step.documentId = revision.documentId;
      step.documentRevision = revision.documentRevision;
    } else {
      delete step.documentId;
      delete step.documentRevision;
      step.introducedConflictIds = [];
      step.resolvedConflictIds = [];
      step.status = 'PENDING';
    }
  }
  const historicalRevisions = turn.knowledgeSourceRevisions.length
    ? await readGuanyijiaPersistedSourceRevisionPrefix({
        run: eventRun,
        sourceDocuments: input.sourceDocuments,
        story: input.story,
        throughSourceIndex: turn.knowledgeSourceRevisions.length - 1,
        skipConflictProjection: true,
      })
    : [];
  for (const [index, revision] of historicalRevisions.entries()) {
    const step = eventRun.sources[index]!;
    step.introducedConflictIds = [...revision.compilation.introducedConflictIds];
    const sourceEvents = eventRun.timeline.filter((event) => event.sourceId === step.sourceId);
    const resolvedCount = sourceEvents.filter((event) => event.type === 'CONFLICT_RESOLVED').length;
    step.resolvedConflictIds = step.introducedConflictIds.slice(0, resolvedCount);
    const lastType = sourceEvents.at(-1)?.type;
    if (lastType === 'SOURCE_READ_STARTED' || lastType === 'SOURCE_READ_COMPLETED') {
      step.status = 'READING';
    } else if (lastType === 'DOCUMENT_GENERATED' || lastType === 'DOCUMENT_REVISED'
      || lastType === 'CONFLICT_CORROBORATED' || lastType === 'ASSISTANT_PATCH_CONFIRMED') {
      step.status = 'DOCUMENT_READY';
    } else if (lastType === 'CONFLICT_FOUND'
      || lastType === 'CONFLICT_RESOLVED' && step.resolvedConflictIds.length < step.introducedConflictIds.length) {
      step.status = 'CONFLICT_BLOCKED';
    } else {
      step.status = 'ALIGNED';
    }
  }
  const eventCurrentStep = selectCurrentReviewStep(eventRun);
  eventRun.status = eventCurrentStep?.status === 'READING' ? 'READING_SOURCE'
    : eventCurrentStep?.status === 'DOCUMENT_READY' ? 'REVIEWING_DOCUMENT'
      : eventCurrentStep?.status === 'CONFLICT_BLOCKED' ? 'CONFLICT_BLOCKED'
        : eventRun.sources.every((step) => step.status === 'ALIGNED') ? 'READY_FOR_OUTPUT' : 'READY';
  const documentCache = new Map<string, SourceModelingDocument>();
  const blocksCache = new Map<string, SourceDocumentBlock[]>();
  const readAllowedDocument = async (documentId: string) => {
    const cached = documentCache.get(documentId);
    if (cached) return cached;
    const document = await input.sourceDocuments.read(documentId);
    const source = document
      ? storySources.find((candidate) => (
          document.documentCode === `guanyijia-five-source--${candidate.sourceId}`
        ))
      : undefined;
    const step = source ? eventRun.sources.find((candidate) => candidate.sourceId === source.sourceId) : undefined;
    if (!document || !source || !step || document.projectId !== projectId
      || document.sourceSnapshotId !== source.snapshotId || document.sourceName !== source.sourceName
      || document.sourceType !== source.sourceClass || document.documentId !== step.documentId
      || document.revision !== step.documentRevision) {
      throw new Error('审阅助手文档目标不属于当前运行');
    }
    documentCache.set(documentId, document);
    return document;
  };
  const readAllowedBlocks = async (documentId: string) => {
    const cached = blocksCache.get(documentId);
    if (cached) return cached;
    const document = await readAllowedDocument(documentId);
    if (!document.blocksRef) throw new Error('旧版来源文档不能生成审阅助手结构化目标');
    const blocks = await input.sourceDocuments.readBlocks(documentId);
    blocksCache.set(documentId, blocks);
    return blocks;
  };
  const selection = turn.selection ?? {};
  if (selection.sourceId && !knowledgeRevisionBySource.has(selection.sourceId)) {
    throw new Error('审阅助手来源目标不属于当前运行');
  }
  let selectedDocument: SourceModelingDocument | undefined;
  if (selection.documentId) selectedDocument = await readAllowedDocument(selection.documentId);
  if (selection.sourceId && selectedDocument
    && selectedDocument.documentCode !== `guanyijia-five-source--${selection.sourceId}`) {
    throw new Error('审阅助手来源与文档目标不一致');
  }
  let selectedBlock: SourceDocumentBlock | undefined;
  if (selection.blockId) {
    if (!selection.documentId) throw new Error('审阅助手Block目标缺少文档身份');
    selectedBlock = (await readAllowedBlocks(selection.documentId)).find((block) => (
      block.blockId === selection.blockId
    ));
    if (!selectedBlock) throw new Error('审阅助手Block目标不属于当前运行');
  }
  if (selection.section && !standardSectionOrder.some(({ key }) => key === selection.section)) {
    throw new Error('审阅助手章节目标不属于标准来源文档');
  }
  if (selection.section && selectedBlock && selection.section !== selectedBlock.section) {
    throw new Error('审阅助手章节目标与当前Block不一致');
  }
  if (selection.evidenceRef && !selectedBlock?.evidenceRefs.includes(selection.evidenceRef)) {
    throw new Error('审阅助手Evidence目标不属于当前运行');
  }
  if (selection.objectRef) {
    const key = canonicalModelingJson(selection.objectRef);
    if (selectedBlock && !selectedBlock.affectedObjectRefs.some((reference) => (
      canonicalModelingJson(reference) === key
    ))) throw new Error('审阅助手影响对象目标与当前Block不一致');
    const selectedConflict = selection.conflictId
      ? input.story.listConflictDefinitions().find((definition) => definition.conflictId === selection.conflictId)
      : undefined;
    if (!selectedBlock && selectedConflict
      && !selectedConflict.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)) {
      throw new Error('审阅助手影响对象目标与当前冲突不一致');
    }
    if (!selectedBlock && !selectedConflict) {
      let belongs = false;
      for (const step of eventRun.sources) {
        if (belongs || !step.documentId) continue;
        belongs = (await readAllowedBlocks(step.documentId)).some((block) => (
          block.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)
        ));
      }
      belongs ||= input.story.listConflictDefinitions().some((definition) => (
        eventRun.sources.some((step) => step.introducedConflictIds.includes(definition.conflictId))
        && definition.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)
      ));
      if (!belongs) throw new Error('审阅助手影响对象目标不属于当前运行');
    }
  }
  if (selection.conflictId && !eventRun.sources.some((source) => (
    source.introducedConflictIds.includes(selection.conflictId!)
  ))) throw new Error('审阅助手冲突目标不属于当前运行');
  if (selection.conflictId && selection.sourceId
    && input.story.listConflictDefinitions().find((definition) => (
      definition.conflictId === selection.conflictId
    ))?.introducedBySourceId !== selection.sourceId) {
    throw new Error('审阅助手冲突目标与当前来源不一致');
  }
  if (selection.timelineItemId) {
    const event = eventRun.timeline.find((candidate) => candidate.eventId === selection.timelineItemId);
    if (!event) throw new Error('审阅助手时间线目标不属于当前运行');
    if (selection.sourceId && event.sourceId !== selection.sourceId) {
      throw new Error('审阅助手时间线目标与当前来源不一致');
    }
  }

  const responseTargets = 'target' in turn.response
    ? [(turn.response as { target: ReviewAssistantTarget }).target]
    : 'targets' in turn.response
      ? (turn.response as { targets: ReviewAssistantTarget[] }).targets
      : [];
  for (const target of responseTargets) {
    if (!target || typeof target !== 'object') throw new Error('审阅助手回复目标无效');
    if (target.kind === 'SOURCE_DOCUMENT') {
      const document = await readAllowedDocument(target.documentId);
      if (target.blockId && !(await readAllowedBlocks(document.documentId)).some((block) => (
        block.blockId === target.blockId && (!target.section || block.section === target.section)
      ))) throw new Error('审阅助手打开目标不属于当前运行');
    } else if (target.kind === 'CONFLICT') {
      if (!eventRun.sources.some((source) => source.introducedConflictIds.includes(target.conflictId))) {
        throw new Error('审阅助手冲突目标不属于当前运行');
      }
    } else if (target.kind === 'EVIDENCE') {
      const source = sourceById.get(target.sourceId);
      const document = source
        ? [...documentCache.values()].find((candidate) => (
            candidate.documentCode === `guanyijia-five-source--${source.sourceId}`
          )) ?? await readAllowedDocument(eventRun.sources.find((step) => step.sourceId === source.sourceId)!.documentId!)
        : undefined;
      const block = document && (await readAllowedBlocks(document.documentId)).find((candidate) => (
        candidate.blockId === target.blockId
      ));
      if (!block?.evidenceRefs.includes(target.evidenceRef)) {
        throw new Error('审阅助手Evidence目标不属于当前运行');
      }
    } else if (target.kind === 'AFFECTED_OBJECT') {
      const key = canonicalModelingJson(target.objectRef);
      const declared = input.story.listConflictDefinitions().some((definition) => (
        eventRun.sources.some((step) => step.introducedConflictIds.includes(definition.conflictId))
        && definition.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)
      )) || [...blocksCache.values()].some((blocks) => blocks.some((block) => (
        block.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)
      )));
      if (!declared) throw new Error('审阅助手影响对象目标不属于当前运行');
    }
  }

  const selectedDocumentSourceId = selectedDocument
    ? storySources.find((source) => selectedDocument.documentCode === `guanyijia-five-source--${source.sourceId}`)?.sourceId
    : undefined;
  const selectedConflictSourceId = selection.conflictId
    ? input.story.listConflictDefinitions().find((definition) => (
        definition.conflictId === selection.conflictId
      ))?.introducedBySourceId
    : undefined;
  const selectedKnowledgeSourceId = selection.sourceId
    ?? selectedDocumentSourceId
    ?? selectedConflictSourceId
    ?? eventCurrentStep?.sourceId;
  const lastReadSourceIndex = selectedKnowledgeSourceId
    ? storySources.findIndex((source) => source.sourceId === selectedKnowledgeSourceId)
    : -1;
  const knowledgeKey = `${lastReadSourceIndex}:${canonicalModelingJson(turn.knowledgeSourceRevisions)}`;
  let runKnowledgeCache = persistedAssistantFactsCache.get(input.run);
  if (!runKnowledgeCache) {
    runKnowledgeCache = new Map();
    persistedAssistantFactsCache.set(input.run, runKnowledgeCache);
  }
  let persistedFacts = runKnowledgeCache.get(knowledgeKey);
  if (!persistedFacts) {
    persistedFacts = (async (): Promise<PersistedAssistantFacts> => {
      const projectionRun = structuredClone(eventRun);
      if (selectedDocument && lastReadSourceIndex >= 0) {
        projectionRun.sources[lastReadSourceIndex]!.documentId = selectedDocument.documentId;
        projectionRun.sources[lastReadSourceIndex]!.documentRevision = selectedDocument.revision;
      }
      const persistedRevisions = lastReadSourceIndex >= 0
        ? await readGuanyijiaPersistedSourceRevisionPrefix({
            run: projectionRun,
            sourceDocuments: input.sourceDocuments,
            story: input.story,
            throughSourceIndex: lastReadSourceIndex,
          })
        : [];
      const revisionBySource = new Map(persistedRevisions.map((revision, index) => [
        storySources[index]!.sourceId, revision,
      ]));
      return {
        sources: persistedRevisions.map((revision, index) => {
          const source = storySources[index]!;
          return {
            sourceId: source.sourceId,
            sourceName: source.sourceName,
            sourceClass: source.sourceClass,
            authority: source.authority,
            snapshotId: source.snapshotId,
            versionRef: revision.compilation.readSummary.versionRef,
            readSummary: revision.compilation.readSummary.summary,
            documentId: revision.documentId,
            documentRevision: revision.documentRevision,
            blocks: revision.compilation.blocks,
            evidenceLocators: revision.compilation.evidenceLocators,
          };
        }),
        conflicts: input.story.listConflictDefinitions().filter((definition) => eventRun.sources.some((step) => (
          step.introducedConflictIds.includes(definition.conflictId)
        ))).map((definition) => {
          const introducedIndex = storySources.findIndex((source) => (
            source.sourceId === definition.introducedBySourceId
          ));
          const revisions = storySources.slice(0, introducedIndex + 1).flatMap((source) => {
            const revision = revisionBySource.get(source.sourceId);
            return revision ? [revision] : [];
          });
          return {
            conflictId: definition.conflictId,
            title: definition.title,
            affectedObjectRefs: definition.affectedObjectRefs,
            ...(revisions.length === introducedIndex + 1
              ? { hunk: input.story.buildConflictHunk({ conflictId: definition.conflictId, sourceRevisions: revisions }) }
              : {}),
          };
        }),
      };
    })();
    runKnowledgeCache.set(knowledgeKey, persistedFacts);
  }
  const currentStep = selectCurrentReviewStep(eventRun);
  const currentConflict = selectFirstUnresolvedConflict(eventRun);
  const facts = await persistedFacts;
  const knowledge: ReviewAssistantKnowledge = {
    ...facts,
    ...(currentStep ? { currentSourceId: currentStep.sourceId, currentDocumentId: currentStep.documentId } : {}),
    ...(selection.blockId ? { currentBlockId: selection.blockId } : {}),
    ...(currentConflict ? { currentConflictId: currentConflict.conflictId } : {}),
  };

  if (turn.response.kind !== 'PATCH_PREVIEW') {
    if (proposal !== undefined) throw new Error('非Patch Turn不能附带Proposal');
    const expectedResponse = answerReviewAssistant({ intent, selection, knowledge });
    if (canonicalModelingJson(turn.response) !== canonicalModelingJson(expectedResponse)) {
      throw new Error('审阅助手回答不是当前Run事实的确定性投影');
    }
    return;
  }
  if (!proposal || proposal.schemaVersion !== 1 || proposal.runId !== input.run.runId
    || proposal.proposalId !== turn.response.proposalId
    || proposal.sourceId !== selection.sourceId || proposal.documentId !== selection.documentId
    || proposal.blockId !== selection.blockId || proposal.createdBy !== turn.actorUserId) {
    throw new Error('审阅助手Proposal与Turn选择不一致');
  }
  const sourceIndex = storySources.findIndex((source) => source.sourceId === proposal.sourceId);
  if (sourceIndex < 0) throw new Error('审阅助手Proposal来源不属于Story');
  const prior = sourceIndex > 0 ? (await readGuanyijiaPersistedSourceRevisionPrefix({
    run: eventRun, sourceDocuments: input.sourceDocuments, story: input.story,
    throughSourceIndex: sourceIndex - 1,
  })).map((value) => value.compilation) : [];
  const source = storySources[sourceIndex]!;
  const document = await readAllowedDocument(proposal.documentId);
  const base = input.story.compileSource({ sourceId: source.sourceId, priorCompilations: prior });
  const blocks = await readAllowedBlocks(document.documentId);
  const changes = blocks.flatMap((block) => {
    const original = base.blocks.find((candidate) => candidate.blockId === block.blockId);
    if (!original) throw new Error('审阅助手Proposal文档包含未知Block');
    const immutableBefore = { ...original, label: undefined, value: undefined };
    const immutableAfter = { ...block, label: undefined, value: undefined };
    if (canonicalModelingJson(immutableBefore) !== canonicalModelingJson(immutableAfter)) {
      throw new Error('审阅助手Proposal文档篡改不可修改Block字段');
    }
    return original.label === block.label && canonicalModelingJson(original.value) === canonicalModelingJson(block.value)
      ? [] : [{ blockId: block.blockId, label: block.label, value: block.value }];
  });
  const compilation = changes.length
    ? input.story.reviseCompilation({ compilation: base, priorCompilations: prior, changes }).compilation
    : base;
  if (canonicalModelingJson(compilation.blocks) !== canonicalModelingJson(blocks)) {
    throw new Error('审阅助手Proposal文档不是Story持久化revision投影');
  }
  const beforeBlock = compilation.blocks.find((block) => block.blockId === proposal.blockId);
  if (!beforeBlock || canonicalModelingJson(beforeBlock) !== canonicalModelingJson(proposal.beforeBlock)) {
    throw new Error('审阅助手Proposal Before Block与持久化revision不一致');
  }
  const revised = input.story.reviseCompilation({
    compilation, priorCompilations: prior, changes: [proposal.change],
  });
  const preview = previewFromDiff(revised.diff);
  const proposalCore = {
    runId: input.run.runId, sourceId: proposal.sourceId,
    documentId: proposal.documentId, documentRevision: document.revision,
    blockId: proposal.blockId, change: proposal.change, beforeBlock, preview,
  };
  if (proposal.documentRevision !== document.revision
    || proposal.previewSha256 !== `sha256:${sha256HexSync(canonicalModelingJson(proposalCore))}`
    || canonicalModelingJson(proposal.preview) !== canonicalModelingJson(preview)) {
    throw new Error('审阅助手Proposal无法由持久化revision复算');
  }
  if (intent.kind !== 'PROPOSE_BLOCK_CHANGE') {
    throw new Error('审阅助手Proposal不是确定性修改意图投影');
  }
  const expectedChange = intent.field === 'label'
    ? { blockId: beforeBlock.blockId, label: intent.value }
    : { blockId: beforeBlock.blockId, value: reviseEditableStructuredValue(beforeBlock.value, intent.value) };
  const expectedResponse = {
    kind: 'PATCH_PREVIEW' as const,
    title: `建议修改“${beforeBlock.label}”`,
    body: ['已从当前持久化 revision 生成真实 Block、Assertion、章节与 Markdown 差异。只有点击确认按钮后才会应用。'],
    proposalId: proposal.proposalId,
    proposalRef: turn.response.proposalRef,
  };
  if (canonicalModelingJson(proposal.change) !== canonicalModelingJson(expectedChange)
    || canonicalModelingJson(turn.response) !== canonicalModelingJson(expectedResponse)) {
    throw new Error('审阅助手Proposal不是消息与持久化revision的确定性投影');
  }
}

export async function validateGuanyijiaAssistantConfirmationAgainstPersistedRevision(input: {
  confirmation: unknown;
  run: StandardizationRun;
  sourceDocuments: SourceDocumentRuntime;
  story: GuanyijiaStandardizationStory;
  contentStore: ContentAddressedStore;
  projection: {
    introducedConflictIds: string[];
    changedBlockIds: string[];
    changedSections: string[];
    affectedObjectIds: string[];
  };
}) {
  const confirmation = input.confirmation as {
    runId?: string;
    sourceId?: string;
    proposalId?: string;
    proposalRef?: `sha256:${string}`;
    expectedPreviewSha256?: string;
    beforeDocumentId?: string;
    beforeDocumentRevision?: number;
    documentId?: string;
    documentRevision?: number;
    blockId?: string;
  };
  const sources = input.story.listSources();
  const sourceIndex = sources.findIndex((source) => source.sourceId === confirmation.sourceId);
  const step = input.run.sources[sourceIndex];
  const stepPointsBefore = step?.documentId === confirmation.beforeDocumentId
    && step.documentRevision === confirmation.beforeDocumentRevision;
  const stepPointsAfter = step?.documentId === confirmation.documentId
    && step.documentRevision === confirmation.documentRevision;
  const stepPointsToLaterRevision = typeof confirmation.documentRevision === 'number'
    && typeof step?.documentRevision === 'number'
    && step.documentRevision > confirmation.documentRevision;
  if (confirmation.runId !== input.run.runId || sourceIndex < 0 || !step
    || step.sourceId !== confirmation.sourceId
    || (!stepPointsBefore && !stepPointsAfter && !stepPointsToLaterRevision)
    || typeof confirmation.proposalRef !== 'string'
    || typeof confirmation.documentId !== 'string'
    || typeof confirmation.documentRevision !== 'number') {
    throw new Error('助手确认revision无法由持久化来源文档复算');
  }
  const proposalPayload = await input.contentStore.get(confirmation.proposalRef);
  if (proposalPayload === null || `sha256:${sha256HexSync(proposalPayload)}` !== confirmation.proposalRef) {
    throw new Error('助手确认revision的Proposal内容不存在或SHA不一致');
  }
  let proposal: AssistantStructuredChangeProposal;
  try { proposal = JSON.parse(proposalPayload) as AssistantStructuredChangeProposal; }
  catch { throw new Error('助手确认revision的Proposal无法解析'); }
  if (proposal.schemaVersion !== 1 || proposal.proposalId !== confirmation.proposalId
    || proposal.runId !== input.run.runId || proposal.sourceId !== confirmation.sourceId
    || proposal.documentId !== confirmation.beforeDocumentId
    || proposal.documentRevision !== confirmation.beforeDocumentRevision
    || proposal.blockId !== confirmation.blockId
    || proposal.previewSha256 !== confirmation.expectedPreviewSha256) {
    throw new Error('助手确认revision的Proposal身份不一致');
  }
  const beforeRun = structuredClone(input.run);
  beforeRun.sources[sourceIndex]!.documentId = confirmation.beforeDocumentId;
  beforeRun.sources[sourceIndex]!.documentRevision = confirmation.beforeDocumentRevision;
  const beforeRevisions = await readGuanyijiaPersistedSourceRevisionPrefix({
    run: beforeRun,
    sourceDocuments: input.sourceDocuments,
    story: input.story,
    throughSourceIndex: sourceIndex,
    skipConflictProjectionAtIndex: sourceIndex,
  });
  const before = beforeRevisions.at(-1);
  if (!before || before.documentId !== confirmation.beforeDocumentId
    || before.documentRevision !== confirmation.beforeDocumentRevision) {
    throw new Error('助手确认revision的Before来源文档不一致');
  }
  const beforeBlock = before.compilation.blocks.find((block) => block.blockId === proposal.blockId);
  if (!beforeBlock || canonicalModelingJson(beforeBlock) !== canonicalModelingJson(proposal.beforeBlock)) {
    throw new Error('助手确认revision的Before Block不一致');
  }
  const priorCompilations = beforeRevisions.slice(0, -1).map((revision) => revision.compilation);
  const revised = input.story.reviseCompilation({
    compilation: before.compilation,
    priorCompilations,
    changes: [proposal.change],
  });
  if (canonicalModelingJson(input.projection.introducedConflictIds)
    !== canonicalModelingJson(revised.compilation.introducedConflictIds)) {
    throw new Error('助手确认revision的冲突投影不一致');
  }
  const expectedDiffSummary = {
    changedBlockIds: revised.diff.changedBlockIds,
    changedSections: revised.diff.sectionChanges.map((change) => change.section),
    affectedObjectIds: revised.diff.affectedObjectRefs.map((reference) => (
      `${reference.kind}:${reference.objectId}`
    )),
  };
  if (canonicalModelingJson({
    changedBlockIds: input.projection.changedBlockIds,
    changedSections: input.projection.changedSections,
    affectedObjectIds: input.projection.affectedObjectIds,
  }) !== canonicalModelingJson(expectedDiffSummary)) {
    throw new Error('助手确认revision的diff摘要不是Proposal唯一投影');
  }
  const preview = previewFromDiff(revised.diff);
  const proposalCore = {
    runId: input.run.runId,
    sourceId: proposal.sourceId,
    documentId: proposal.documentId,
    documentRevision: proposal.documentRevision,
    blockId: proposal.blockId,
    change: proposal.change,
    beforeBlock,
    preview,
  };
  if (proposal.previewSha256 !== `sha256:${sha256HexSync(canonicalModelingJson(proposalCore))}`
    || canonicalModelingJson(proposal.preview) !== canonicalModelingJson(preview)) {
    throw new Error('助手确认revision的Proposal预览无法复算');
  }
  const document = await input.sourceDocuments.read(confirmation.documentId);
  const source = sources[sourceIndex]!;
  if (!document || document.projectId !== projectId
    || document.documentCode !== `guanyijia-five-source--${source.sourceId}`
    || document.sourceSnapshotId !== source.snapshotId
    || document.sourceName !== source.sourceName
    || document.sourceType !== source.sourceClass
    || document.derivedFromDocumentId !== confirmation.beforeDocumentId
    || document.revision !== confirmation.documentRevision) {
    throw new Error('助手确认revision无法由持久化来源文档复算');
  }
  if (stepPointsToLaterRevision) {
    let descendant = await input.sourceDocuments.read(step.documentId!);
    while (descendant && descendant.documentId !== document.documentId && descendant.derivedFromDocumentId) {
      descendant = await input.sourceDocuments.read(descendant.derivedFromDocumentId);
    }
    if (!descendant || descendant.documentId !== document.documentId) {
      throw new Error('助手确认revision不在当前文档的不可变血缘链上');
    }
  }
  const [blocks, assertions, sections, markdown] = await Promise.all([
    input.sourceDocuments.readBlocks(document.documentId),
    input.sourceDocuments.readAssertions(document.documentId),
    input.sourceDocuments.readSections(document.documentId),
    input.sourceDocuments.readMarkdown(document.documentId),
  ]);
  if (canonicalModelingJson(blocks) !== canonicalModelingJson(revised.compilation.blocks)) {
    throw new Error('助手确认revision的blocks不是Proposal的唯一投影');
  }
  if (canonicalModelingJson(assertions) !== canonicalModelingJson(revised.compilation.assertions)) {
    throw new Error('助手确认revision的assertions不是Proposal的唯一投影');
  }
  if (canonicalModelingJson(sections) !== canonicalModelingJson(revised.compilation.sections)) {
    throw new Error('助手确认revision的sections不是Proposal的唯一投影');
  }
  if (markdown !== renderSourceDocumentMarkdown({ ...document, sections, assertions })) {
    throw new Error('助手确认revision的Markdown不是Proposal的唯一投影');
  }
}

export function createGuanyijiaWorkbenchRuntime(input: {
  pointerStorage: PointerStorage;
  standardizationRuns: StandardizationRunRuntime;
  sourceDocuments: SourceDocumentRuntime;
  story?: GuanyijiaStandardizationStory;
  contentStore?: ContentAddressedStore;
  accessForActor?: (actorUserId: string) => ReviewAssistantAccess;
  sourcePreparationObserver?: (progress: SourcePreparationProgress) => void | Promise<void>;
  now?: () => string;
}): GuanyijiaWorkbenchRuntime {
  const story = input.story ?? createGuanyijiaStandardizationStory();
  const now = input.now ?? (() => new Date().toISOString());
  const sources = story.listSources();
  const batchId = storyBatchId(sources);
  let sourcePreparationSequence = 0;
  const reportSourcePreparation = async (
    sourceId: string,
    stage: SourcePreparationStage,
    state: SourcePreparationProgress['state'],
  ) => {
    let observed: void | Promise<void> | undefined;
    try {
      observed = input.sourcePreparationObserver?.({
        sourceId,
        stage,
        state,
        sequence: ++sourcePreparationSequence,
      });
    } catch {
      // A UI adapter can disappear between a real source boundary and its
      // display update. Presentation failure never changes source truth.
      return;
    }
    if (state !== 'ACTIVE') {
      void Promise.resolve(observed).catch(() => undefined);
      return;
    }
    // A presentation adapter may deliberately hold the active state visible
    // in the static Demo. Its failure must never turn a display concern into a
    // persisted source-processing failure.
    try {
      await observed;
    } catch {
      // Presentation is best-effort; the real source boundary remains valid.
    }
  };

  function requireActiveAccess(actorUserId: string) {
    const access = input.accessForActor?.(actorUserId) ?? { active: true, role: 'ADMIN' as const };
    if (!access.active) throw new Error('当前用户不是当前项目ACTIVE成员');
    return access;
  }

  async function documentMetadataBySource(run: StandardizationRun) {
    const entries = await Promise.all(run.sources.flatMap((step) => step.documentId
      ? [input.sourceDocuments.read(step.documentId).then((document) => [step.sourceId, document] as const)]
      : []));
    return new Map(entries.flatMap(([sourceId, document]) => document ? [[sourceId, document] as const] : []));
  }

  function baseCompilationsForReview() {
    const compilations: SourceDocumentCompilation[] = [];
    for (const source of sources) {
      compilations.push(story.compileSource({ sourceId: source.sourceId, priorCompilations: compilations }));
    }
    return compilations;
  }

  const refSummary = (
    stableKey: string,
    title: string,
    summary: string,
    contentRef: ContentReference,
  ): ReviewWindowSummary => ({
    stableKey, title, summary, contentRef,
    contentSha256: contentRef.slice('sha256:'.length),
  });

  async function reviewSummaries(run: StandardizationRun, stream: ReviewWindowStream, filter = 'all') {
    if (stream === 'TIMELINE') return run.timeline.map((event) => refSummary(
      `timeline:${String(event.sequence).padStart(8, '0')}:${event.eventId}`,
      event.type,
      `${event.sourceId ?? run.projectId} · ${event.actor.userId}`,
      event.payloadRef,
    ));
    if (stream === 'ASSISTANT_HISTORY') return run.timeline
      .filter((event) => event.type === 'ASSISTANT_TURN_RECORDED')
      .map((event) => refSummary(
        `assistant:${String(event.sequence).padStart(8, '0')}:${event.eventId}`,
        event.assistantProposalId ? '审阅助手修改建议' : '审阅助手回答',
        `${event.actor.userId} · ${event.createdAt}`,
        event.payloadRef,
      ));
    const documents = await documentMetadataBySource(run);
    const compilations = baseCompilationsForReview();
    if (stream === 'DOCUMENT_BLOCKS') {
      const requestedDocumentId = new URLSearchParams(filter).get('document') ?? undefined;
      const currentStep = run.sources.find((candidate) => !['PENDING', 'ALIGNED'].includes(candidate.status))
        ?? [...run.sources].reverse().find((candidate) => candidate.documentId);
      const step = requestedDocumentId
        ? run.sources.find((candidate) => candidate.documentId === requestedDocumentId)
        : currentStep;
      const document = step ? documents.get(step.sourceId) : undefined;
      const compilation = step ? compilations.find((candidate) => candidate.sourceId === step.sourceId) : undefined;
      if (!document?.blocksRef || !compilation) return [];
      const section = new URLSearchParams(filter).get('section') ?? /^section:(.+)$/u.exec(filter)?.[1];
      return compilation.blocks.filter((block) => !section || block.section === section).map((block) => refSummary(
        `block:${step!.sourceId}:${block.blockId}`,
        block.stableCode,
        `${block.section} · ${block.evidenceStatus}`,
        document.blocksRef!,
      ));
    }
    if (stream === 'EVIDENCE') {
      const match = /^source:([^:]+):block:(.+)$/u.exec(filter);
      return compilations.filter((compilation) => !match || compilation.sourceId === match[1])
        .flatMap((compilation) => {
      const document = documents.get(compilation.sourceId);
      if (!document?.blocksRef) return [];
      return compilation.blocks.filter((block) => !match || block.blockId === match[2])
        .flatMap((block) => block.evidenceRefs.map((evidenceRef) => refSummary(
        `evidence:${compilation.sourceId}:${block.blockId}:${evidenceRef}`,
        evidenceRef,
        `${compilation.sourceName} · ${block.section} · ${block.stableCode}`,
        document.blocksRef!,
      )));
      });
    }
    if (stream === 'ISSUES') return run.sources.flatMap((step) => {
      const foundEvent = run.timeline.find((event) => (
        event.type === 'CONFLICT_FOUND' && event.sourceId === step.sourceId
      ));
      if (!foundEvent) return [];
      return step.introducedConflictIds.flatMap((conflictId) => {
        const definition = story.listConflictDefinitions().find((candidate) => candidate.conflictId === conflictId);
        return definition ? [refSummary(
          `issue:${conflictId}`,
          definition.title,
          `${step.sourceName} · ${step.resolvedConflictIds.includes(conflictId) ? '已决定' : '待决定'}`,
          foundEvent.payloadRef,
        )] : [];
      });
    });
    // A reopened source decision deliberately clears the active deliverable
    // until a replacement is generated. Historical delivery content remains
    // available from its timeline checkpoint, but must not be presented as
    // the current result in the review window.
    if (!run.deliverableId) return [];
    const generated = [...run.timeline].reverse().find((event) => event.type === 'DELIVERABLE_GENERATED');
    if (!generated) return [];
    if (!generated.deliveryContentIndex) {
      throw new Error('交付生成事件缺少review-window summary index');
    }
    return generated.deliveryContentIndex.entries.map(({ key, contentRef }) => refSummary(
      `deliverable:${key}`, guanyijiaDeliverableContentLabelFor(key), generated.deliveryContentIndex!.coreSha256, contentRef,
    ));
  }

  const reviewWindow = createStandardizationReviewWindow({
    cursorSecret: `guanyijia-review-window:${storyBatchId(sources)}`,
    index: {
      async query(query) {
        const run = await readActiveRun();
        if (!run || run.runId !== query.runId) throw new Error('审阅窗口不属于当前运行');
        const summaries = await reviewSummaries(run, query.stream, query.filter);
        return createArrayReviewWindowIndex(summaries).query(query);
      },
    },
    bodies: {
      async read(contentRef) {
        if (!input.contentStore) throw new ReviewWindowError({
          code: 'STORE_UNAVAILABLE', message: '标准化内容存储不可用', recoveryAction: 'RETRY',
        });
        try {
          return await input.contentStore.get(contentRef);
        } catch (cause) {
          if (cause instanceof ReviewWindowError) throw cause;
          const message = cause instanceof Error ? cause.message : '标准化内容存储读取失败';
          if (message.includes('校验和')) throw new ReviewWindowError({
            code: 'CHECKSUM_MISMATCH', message,
            expectedSha256: contentRef.slice('sha256:'.length),
          });
          throw new ReviewWindowError({
            code: 'STORE_UNAVAILABLE', message, recoveryAction: 'RETRY',
          });
        }
      },
    },
  });

  async function allowedReviewContentRefs(run: StandardizationRun) {
    const refs = new Set<ContentReference>(run.timeline.map((event) => event.payloadRef));
    for (const document of (await documentMetadataBySource(run)).values()) {
      refs.add(document.sectionsRef);
      refs.add(document.assertionsRef);
      if (document.blocksRef) refs.add(document.blocksRef);
      refs.add(document.markdownRef);
    }
    for (const item of await reviewSummaries(run, 'DELIVERABLE_SECTIONS')) {
      if (item.contentRef) refs.add(item.contentRef);
    }
    return refs;
  }

  async function readVerifiedReviewContent(reviewInput: {
    runId: string;
    contentRef: ContentReference;
    expectedSha256: string;
  }) {
    const run = await readActiveRun();
    if (!run || run.runId !== reviewInput.runId) throw new Error('审阅正文不属于当前运行');
    if (!(await allowedReviewContentRefs(run)).has(reviewInput.contentRef)) {
      throw new Error('审阅正文引用不属于当前运行');
    }
    return reviewWindow.readContent(reviewInput);
  }

  /**
   * Returns a source document that is already attached to this exact run.
   * The active source is still derived separately by `reviewShellSnapshot`;
   * this helper deliberately permits an ALIGNED document to be opened for
   * read-only evidence review without making it the next workflow action.
   */
  async function reviewDocumentContext(run: StandardizationRun, documentId: string) {
    const allDocuments = await input.sourceDocuments.list(projectId);
    const byDocumentId = new Map(allDocuments.map((document) => [document.documentId, document]));
    const document = byDocumentId.get(documentId);
    if (!document) throw new Error('来源文档不属于当前运行');
    const sourceIndex = run.sources.findIndex((step) => {
      let candidate = step.documentId ? byDocumentId.get(step.documentId) : undefined;
      while (candidate) {
        if (candidate.documentId === documentId) return true;
        candidate = candidate.derivedFromDocumentId
          ? byDocumentId.get(candidate.derivedFromDocumentId)
          : undefined;
      }
      return false;
    });
    if (sourceIndex < 0) throw new Error('来源文档不属于当前运行');
    const sourceStep = run.sources[sourceIndex]!;
    const sourceRevision = sourceStep.documentRevision;
    const source = sources.find((candidate) => candidate.sourceId === sourceStep.sourceId);
    if (!source || !sourceStep.documentId || typeof sourceRevision !== 'number' || !Number.isSafeInteger(sourceRevision)) {
      throw new Error('来源文档缺少来源身份');
    }
    const compilation = baseCompilationsForReview()[sourceIndex];
    if (!document || !compilation || document.documentId !== documentId
      || document.projectId !== projectId
      || document.documentCode !== `guanyijia-five-source--${sourceStep.sourceId}`
      || document.sourceSnapshotId !== compilation.snapshotId
      || document.sourceType !== compilation.sourceClass
      || document.sourceName !== compilation.sourceName
      || document.revision > sourceRevision) {
      throw new Error('来源文档身份或结构化内容不一致');
    }
    const viewSourceStep: StandardizationSourceStep = document.documentId === sourceStep.documentId
      ? sourceStep
      : { ...sourceStep, status: 'ALIGNED' };
    return {
      current: {
        source,
        // A superseded historical revision must never inherit the active
        // document's completion action. It is evidence-only until the user
        // returns to the actual current task.
        sourceStep: viewSourceStep,
        document,
        history: allDocuments.filter((candidate) => candidate.documentCode === document.documentCode)
          .sort((left, right) => right.revision - left.revision),
        compilationSummary: {
          blockCount: compilation.blocks.length,
          sectionCount: standardSectionOrder.length,
          firstReviewSection: standardSectionOrder.find(({ key }) => (
            compilation.blocks.some((block) => block.section === key && block.evidenceStatus !== 'FACT')
          ))?.key ?? standardSectionOrder[0].key,
        },
      },
      compilation,
    };
  }

  async function loadRunContext(
    run: StandardizationRun,
    options: { sourceLimit?: number; blocksOnly?: boolean; skipEventBodies?: boolean } = {},
  ) {
    const compilations: SourceDocumentCompilation[] = [];
    const documents = new Map<string, SourceModelingDocument>();
    const legacyDocuments = new Map<string, {
      sections: SourceDocumentSections;
      assertions: StructuredModelingAssertion[];
      markdown: string;
    }>();
    const generatedEvents = new Map<string, SourceModelingDocument>();
    const revisionDiffs = new Map<string, SourceDocumentRevisionDiff>();
    const revisionEvents = new Map<string, {
      document: SourceModelingDocument;
      diff: SourceDocumentRevisionDiff;
    }>();
    const resolutionsByConflict = new Map<string, ConflictResolutionArtifact>();
    const assistantTurns = new Map<string, ReviewAssistantTurnArtifact>();
    const allDocuments = await input.sourceDocuments.list(projectId);
    for (const [sourceIndex, step] of run.sources.entries()) {
      if (options.sourceLimit !== undefined && sourceIndex >= options.sourceLimit) break;
      if (!step.documentId) continue;
      const base = story.compileSource({ sourceId: step.sourceId, priorCompilations: compilations });
      const document = await input.sourceDocuments.read(step.documentId);
      if (!document) throw new Error(`标准化运行引用的来源文档不存在：${step.documentId}`);
      if (document.projectId !== projectId
        || document.documentCode !== `guanyijia-five-source--${base.sourceId}`
        || document.sourceSnapshotId !== base.snapshotId
        || document.sourceType !== base.sourceClass
        || document.sourceName !== base.sourceName
        || document.revision !== step.documentRevision) {
        throw new Error(`标准化运行引用的来源文档身份不匹配：${step.documentId}`);
      }
      documents.set(step.sourceId, document);
      if (!document.blocksRef) {
        if (options.blocksOnly) {
          throw new Error(`当前冲突引用的来源文档缺少结构化块：${document.documentId}`);
        }
        const [sections, assertions, markdown] = await Promise.all([
          input.sourceDocuments.readSections(document.documentId),
          input.sourceDocuments.readAssertions(document.documentId),
          input.sourceDocuments.readMarkdown(document.documentId),
        ]);
        if (document.markdownSha256 !== document.markdownRef.slice('sha256:'.length)
          || markdown !== renderSourceDocumentMarkdown({ ...document, sections, assertions })) {
          throw new Error(`来源文档Markdown与结构化投影不一致：${document.documentId}`);
        }
        legacyDocuments.set(step.sourceId, { sections, assertions, markdown });
        continue;
      }
      let compilation = base;
      {
        const blocks = await input.sourceDocuments.readBlocks(document.documentId);
        if (blocks.length !== base.blocks.length) {
          throw new Error(`来源文档结构化块数量与故事编译不一致：${document.documentId}`);
        }
        const changes = blocks.flatMap((block) => {
          const original = base.blocks.find((candidate) => candidate.blockId === block.blockId);
          if (!original) throw new Error(`来源文档包含未知结构化块：${block.blockId}`);
          const immutableBefore = { ...original, label: undefined, value: undefined };
          const immutableAfter = { ...block, label: undefined, value: undefined };
          if (canonicalModelingJson(immutableBefore) !== canonicalModelingJson(immutableAfter)) {
            throw new Error(`来源文档篡改了结构化块不可修改字段：${block.blockId}`);
          }
          return original.label === block.label
            && canonicalModelingJson(original.value) === canonicalModelingJson(block.value)
            ? []
            : [{ blockId: block.blockId, label: block.label, value: block.value }];
        });
        compilation = changes.length
          ? story.reviseCompilation({ compilation: base, priorCompilations: compilations, changes }).compilation
          : base;
        if (canonicalModelingJson(compilation.blocks) !== canonicalModelingJson(blocks)) {
          throw new Error(`来源文档结构化内容与故事投影不一致：${document.documentId}`);
        }
        if (!options.blocksOnly) {
          const [sections, assertions, markdown] = await Promise.all([
            input.sourceDocuments.readSections(document.documentId),
            input.sourceDocuments.readAssertions(document.documentId),
            input.sourceDocuments.readMarkdown(document.documentId),
          ]);
          if (canonicalModelingJson(compilation.sections) !== canonicalModelingJson(sections)
            || canonicalModelingJson(compilation.assertions) !== canonicalModelingJson(assertions)) {
            throw new Error(`来源文档结构化内容与故事投影不一致：${document.documentId}`);
          }
          if (document.markdownSha256 !== document.markdownRef.slice('sha256:'.length)
            || markdown !== renderSourceDocumentMarkdown({ ...document, sections, assertions })) {
            throw new Error(`来源文档Markdown与结构化投影不一致：${document.documentId}`);
          }
        }
      }
      if (canonicalModelingJson(compilation.introducedConflictIds)
        !== canonicalModelingJson(step.introducedConflictIds)) {
        throw new Error(`来源文档冲突投影与标准化运行不一致：${document.documentId}`);
      }
      compilations.push(compilation);
      if (document.derivedFromDocumentId) {
        revisionDiffs.set(step.sourceId, await input.sourceDocuments.getRevisionDiff(
          document.derivedFromDocumentId, document.documentId,
        ));
      }
    }
    if (options.skipEventBodies) return {
      compilations, documents, legacyDocuments, generatedEvents, revisionDiffs, revisionEvents,
      resolutions: [], assistantTurns, allDocuments,
    };
    for (const event of run.timeline.filter((candidate) => candidate.type === 'DOCUMENT_GENERATED')) {
      const raw = await input.standardizationRuns.readEventPayload(run.runId, event.eventId);
      let payload: unknown;
      try { payload = JSON.parse(raw); } catch { throw new Error(`来源文档生成事件载荷无法解析：${event.eventId}`); }
      if (typeof payload !== 'object' || payload === null
        || typeof (payload as { documentId?: unknown }).documentId !== 'string'
        || !Number.isSafeInteger((payload as { documentRevision?: unknown }).documentRevision)) {
        throw new Error(`来源文档生成事件载荷格式无效：${event.eventId}`);
      }
      const generatedPayload = payload as { documentId: string; documentRevision: number };
      const generatedDocument = allDocuments.find((candidate) => candidate.documentId === generatedPayload.documentId);
      const currentDocument = event.sourceId ? documents.get(event.sourceId) : undefined;
      if (!generatedDocument || !currentDocument
        || generatedDocument.projectId !== projectId
        || generatedDocument.documentCode !== currentDocument.documentCode
        || generatedDocument.sourceSnapshotId !== currentDocument.sourceSnapshotId
        || generatedDocument.revision !== generatedPayload.documentRevision) {
        throw new Error(`来源文档生成事件与不可变revision不一致：${event.eventId}`);
      }
      generatedEvents.set(event.eventId, generatedDocument);
    }
    for (const event of run.timeline.filter((candidate) => candidate.type === 'DOCUMENT_REVISED')) {
      const raw = await input.standardizationRuns.readEventPayload(run.runId, event.eventId);
      let payload: unknown;
      try { payload = JSON.parse(raw); } catch { throw new Error(`来源文档修订事件载荷无法解析：${event.eventId}`); }
      if (typeof payload !== 'object' || payload === null
        || typeof (payload as { beforeDocumentId?: unknown }).beforeDocumentId !== 'string'
        || typeof (payload as { documentId?: unknown }).documentId !== 'string'
        || !Number.isSafeInteger((payload as { documentRevision?: unknown }).documentRevision)) {
        throw new Error(`来源文档修订事件载荷格式无效：${event.eventId}`);
      }
      const revisionPayload = payload as {
        beforeDocumentId: string;
        documentId: string;
        documentRevision: number;
      };
      const revisedDocument = allDocuments.find((candidate) => candidate.documentId === revisionPayload.documentId);
      const currentDocument = event.sourceId ? documents.get(event.sourceId) : undefined;
      if (!revisedDocument || !currentDocument
        || revisedDocument.projectId !== projectId
        || revisedDocument.documentCode !== currentDocument.documentCode
        || revisedDocument.derivedFromDocumentId !== revisionPayload.beforeDocumentId
        || revisedDocument.revision !== revisionPayload.documentRevision) {
        throw new Error(`来源文档修订事件与不可变revision不一致：${event.eventId}`);
      }
      revisionEvents.set(event.eventId, {
        document: revisedDocument,
        diff: await input.sourceDocuments.getRevisionDiff(
          revisionPayload.beforeDocumentId, revisionPayload.documentId,
        ),
      });
    }
    for (const event of run.timeline.filter((candidate) => (
      candidate.type === 'CONFLICT_RESOLVED' || candidate.type === 'CONFLICT_DECISION_REPLACED'
    ))) {
      const raw = await input.standardizationRuns.readEventPayload(run.runId, event.eventId);
      let artifact: ConflictResolutionArtifact;
      try { artifact = JSON.parse(raw) as ConflictResolutionArtifact; } catch {
        throw new Error(`冲突决定事件载荷无法解析：${event.eventId}`);
      }
      const definition = story.listConflictDefinitions().find((candidate) => (
        candidate.conflictId === artifact.hunk?.conflictId
      ));
      if (!definition || artifact.sourceId !== definition.introducedBySourceId
        || event.type === 'CONFLICT_RESOLVED' && event.sourceId !== artifact.sourceId
        || event.type === 'CONFLICT_DECISION_REPLACED' && event.conflictId !== artifact.hunk.conflictId) {
        throw new Error(`冲突决定事件与故事定义不一致：${event.eventId}`);
      }
      const introducedIndex = sources.findIndex((source) => source.sourceId === definition.introducedBySourceId);
      const sourceRevisions = compilations.slice(0, introducedIndex + 1).map((compilation) => {
        const document = documents.get(compilation.sourceId);
        if (!document) throw new Error(`冲突决定缺少持久化来源revision：${compilation.sourceId}`);
        return { documentId: document.documentId, documentRevision: document.revision, compilation };
      });
      const expected = story.previewConflictResolution({
        conflictId: definition.conflictId,
        strategy: artifact.strategy,
        sourceRevisions,
      });
      const actualPreview = {
        hunk: artifact.hunk,
        strategy: artifact.strategy,
        result: artifact.result,
        structuredPatch: artifact.structuredPatch,
        markdownDiff: artifact.markdownDiff,
        provenanceSources: artifact.provenanceSources,
        previewSha256: artifact.previewSha256,
      };
      if (artifact.schemaVersion !== 1 || artifact.runId !== run.runId
        || artifact.actorUserId !== event.actor.userId
        || canonicalModelingJson(actualPreview) !== canonicalModelingJson(expected)) {
        throw new Error(`冲突决定Artifact无法由持久化来源revision复算：${event.eventId}`);
      }
      if (event.type === 'CONFLICT_DECISION_REPLACED' && !resolutionsByConflict.has(artifact.hunk.conflictId)) {
        throw new Error(`取代决定缺少原始来源差异决定：${event.eventId}`);
      }
      resolutionsByConflict.set(artifact.hunk.conflictId, artifact);
    }
    return {
      compilations, documents, legacyDocuments, generatedEvents, revisionDiffs, revisionEvents,
      resolutions: [...resolutionsByConflict.values()], assistantTurns, allDocuments,
    };
  }

  function reviewShellTimeline(
    run: StandardizationRun,
    compilations: SourceDocumentCompilation[],
    documents: readonly SourceModelingDocument[],
  ) {
    const compilationBySource = new Map(compilations.map((value) => [value.sourceId, value]));
    const sourceById = new Map(sources.map((value) => [value.sourceId, value]));
    const conflictById = new Map(story.listConflictDefinitions().map((value) => [value.conflictId, value]));
    const currentSourceId = selectCurrentReviewStep(run)?.sourceId;
    const currentEventId = [...run.timeline].reverse().find((event) => {
      if (run.status === 'READING_SOURCE') return event.type === 'SOURCE_READ_STARTED';
      if (run.status === 'REVIEWING_DOCUMENT') return event.sourceId === currentSourceId
        && (event.type === 'DOCUMENT_GENERATED' || event.type === 'DOCUMENT_REVISED');
      if (run.status === 'CONFLICT_BLOCKED') return event.type === 'CONFLICT_FOUND';
      return false;
    })?.eventId;
    const deliverableAction = (event: StandardizationTimelineEvent) => {
      const mergedDocumentRef = event.deliveryContentIndex?.entries.find((entry) => (
        entry.key === 'mergedDocumentRef'
      ))?.contentRef;
      return {
        type: 'OPEN_DELIVERABLE' as const,
        ...(mergedDocumentRef ? { mergedDocumentRef } : {}),
      };
    };
    const priorDeliverableAction = (event: StandardizationTimelineEvent) => {
      const prior = run.timeline.slice(0, Math.max(0, event.sequence - 1)).reverse().find((candidate) => (
        candidate.type === 'DELIVERABLE_GENERATED'
      ));
      return prior ? deliverableAction(prior) : { type: 'OPEN_DELIVERABLE' as const };
    };
    const result: WorkbenchTimelineItem[] = [{
      itemId: `${run.runId}:started`, kind: 'RUN_STARTED',
      state: run.timeline.length ? 'RECEIPT' : 'CURRENT',
      title: '开始资料整理',
      summary: `按固定顺序载入 ${run.sources.length} 份演示快照；每份文档审阅完成后才继续。`,
      createdAt: run.createdAt,
    }];
    for (const event of run.timeline) {
      if (event.type === 'REVIEW_SUBMITTED') continue;
      const step = event.sourceId ? run.sources.find((value) => value.sourceId === event.sourceId) : undefined;
      const source = event.sourceId ? sourceById.get(event.sourceId) : undefined;
      const compilation = event.sourceId ? compilationBySource.get(event.sourceId) : undefined;
      const base = {
        itemId: event.eventId, eventId: event.eventId,
        state: event.eventId === currentEventId ? 'CURRENT' as const : 'RECEIPT' as const,
        ...(event.sourceId ? { sourceId: event.sourceId } : {}),
        createdAt: event.createdAt, contentRef: event.payloadRef,
        contentSha256: event.payloadRef.slice('sha256:'.length),
        ...(source && compilation ? { inspector: inspectorForCompilation(run, source, compilation) } : {}),
      };
      if (event.type === 'SOURCE_READ_STARTED') result.push({
        ...base, kind: event.type, title: `载入${step ? displaySourceName(step.sourceId, step.sourceName) : '来源'}快照`, summary: '校验固定快照身份并载入冻结范围。',
      });
      else if (event.type === 'SOURCE_READ_COMPLETED') result.push({
        ...base, kind: event.type, title: `${step ? displaySourceName(step.sourceId, step.sourceName) : '来源'}快照已载入`,
        ...(event.sourceId ? { action: { type: 'OPEN_SOURCE_DETAILS' as const, sourceId: event.sourceId } } : {}),
        summary: '固定快照已载入；可打开审阅事项查看已保存资料。',
      });
      else if (event.type === 'DOCUMENT_GENERATED' || event.type === 'DOCUMENT_REVISED') {
        const document = documents.find((value) => value.documentId === event.sourceDocumentId)
          ?? documents.find((value) => value.documentCode === `guanyijia-five-source--${event.sourceId}`
            && value.revision === event.sourceDocumentRevision);
        result.push({
          ...base, kind: event.type,
          title: `${step ? displaySourceName(step.sourceId, step.sourceName) : '来源'}来源文档${event.type === 'DOCUMENT_REVISED' ? '已修订' : '已生成'}`,
          summary: '审阅文档已准备好；打开后可查看审阅结论和来源材料。',
          ...(document && compilation ? { document: {
            documentId: document.documentId, revision: document.revision,
            sectionCount: 9, blockCounts: countBlocks(compilation),
          } } : {}),
          ...(document ? { action: { type: 'OPEN_DOCUMENT' as const, documentId: document.documentId, revision: document.revision } } : {}),
        });
      } else if (event.type === 'DOCUMENT_REVIEWED') {
        const document = step?.documentId ? documents.find((value) => value.documentId === step.documentId) : undefined;
        result.push({
          ...base, kind: event.type, title: `${step ? displaySourceName(step.sourceId, step.sourceName) : '来源'}文档审阅完成`,
          summary: '本份来源文档已完成审阅，可随时重新查看。',
          ...(document && compilation ? { document: {
            documentId: document.documentId, revision: document.revision,
            sectionCount: 9, blockCounts: countBlocks(compilation),
          } } : {}),
          ...(document ? { action: { type: 'OPEN_DOCUMENT' as const, documentId: document.documentId, revision: document.revision } } : {}),
        });
      }
      else if (event.type === 'CONFLICT_FOUND') {
        const conflicts = (step?.introducedConflictIds ?? []).flatMap((conflictId) => {
          const definition = conflictById.get(conflictId);
          return definition ? [{
            conflictId, title: definition.title,
            affectedObjects: definition.affectedObjectRefs.map((ref) => `${objectKindLabel[ref.kind]}：${ref.objectId}`),
          }] : [];
        });
        if (!conflicts.length) {
          result.push({
            ...base, kind: event.type, title: '发现来源差异',
            summary: '需要人工处理后才能继续。', conflicts: [],
          });
        } else {
          conflicts.forEach((conflict, index) => {
            result.push({
              ...base,
              itemId: `${event.eventId}:${conflict.conflictId}`,
              state: index === 0 ? base.state : 'RECEIPT',
              kind: 'CONFLICT_FOUND',
              title: conflict.title,
              summary: `影响 ${conflict.affectedObjects.join('、')}`,
              conflicts: [conflict],
              action: { type: 'OPEN_CONFLICT' as const, conflictId: conflict.conflictId },
            });
          });
        }
      } else if (event.type === 'CONFLICT_RESOLVED') {
        const conflictId = resolvedConflictForTimelineEvent({
          run,
          eventId: event.eventId,
          sourceId: event.sourceId,
          resolvedConflictIds: step?.resolvedConflictIds,
        });
        result.push({
          ...base, kind: event.type, title: '来源差异已决定',
          summary: '已保存决定、来源材料和结果；可随时重新查看。',
          ...(conflictId ? { action: { type: 'OPEN_CONFLICT' as const, conflictId } } : {}),
        });
      } else if (event.type === 'CONFLICT_DECISION_REPLACED') {
        result.push({
          ...base,
          kind: event.type,
          title: '来源差异决定已更新',
          summary: '上一决定已保留在历史中；当前决定已更新，重新生成标准化结果后即可定版。',
          ...(event.conflictId ? { action: { type: 'OPEN_CONFLICT' as const, conflictId: event.conflictId } } : {}),
        });
      } else if (event.type === 'DELIVERABLE_GENERATED') result.push({
        ...base, kind: event.type, title: '标准化结果已生成',
        summary: '可查看完整文档、可建模结论和已排除事项。', action: deliverableAction(event),
      });
      else if (event.type === 'DELIVERABLE_SUPERSEDED') result.push({
        ...base, kind: event.type, title: '标准化结果需要重新生成',
        summary: '来源差异决定已更新；上一份结果已保留，重新生成后即可定版。',
        action: priorDeliverableAction(event),
      });
      else if (event.type === 'DELIVERABLE_FROZEN') result.push({
        ...base, kind: event.type, title: '标准化结果已定版',
        summary: '定版文档可回看；正式模型尚未改变。', action: { type: 'OPEN_DELIVERABLE' as const },
      });
      else if (event.type === 'MODELING_HANDOFF_COMPLETED') result.push({
        ...base, kind: event.type, title: '标准化文档已交给 AI 建模',
        summary: '可回看交接文档、建模候选和已排除事项。', action: { type: 'OPEN_DELIVERABLE' as const },
      });
      else if (event.type === 'CONFLICT_CORROBORATED') result.push({
        ...base, kind: event.type, title: '派生佐证已回链', summary: 'Semantica 仅追加佐证回执，不改变已保存决定。',
      });
      else if (event.type === 'ASSISTANT_TURN_RECORDED') result.push({
        ...base, kind: event.type,
        title: event.assistantProposalId ? '审阅助手已生成修改建议' : '审阅助手已回答',
        summary: '审阅助手已记录本次说明；可继续查看或处理建议。',
      });
      else if (event.type === 'ASSISTANT_PATCH_CONFIRMED') result.push({
        ...base, kind: event.type, title: '审阅助手修改已确认',
        summary: '建议修改已写入当前审阅文档。',
      });
      else if (event.type === 'ASSISTANT_PATCH_CANCELLED') result.push({
        ...base, kind: event.type, title: '审阅助手建议已取消',
        summary: '建议未采用；当前审阅文档保持不变。',
      });
    }
    return result;
  }

  async function reviewShellSnapshot(run: StandardizationRun | null): Promise<GuanyijiaWorkbenchSnapshot> {
    if (!run) return {
      storyId, storyName: '管伊佳数据标准化', batchId, run: null,
      timeline: [], timelineTotal: 0, nextAction: nextAction(null), resolutions: [],
    };
    const pointer = readPointer(input.pointerStorage, batchId);
    const contentBinding = contentBindingForRun(run, pointer);
    const documents = await input.sourceDocuments.list(projectId);
    let compilations = baseCompilationsForReview();
    let conflictCompilations: SourceDocumentCompilation[] | undefined;
    let conflictDocuments: Map<string, SourceModelingDocument> | undefined;
    let currentConflict: {
      conflictId: string;
      definition: ReturnType<typeof story.listConflictDefinitions>[number];
    } | undefined;
    const currentStep = selectCurrentReviewStep(run);
    const currentSource = currentStep ? sources.find((source) => source.sourceId === currentStep.sourceId) : undefined;
    const currentDocument = currentStep
      ? documents.find((document) => document.documentId === currentStep.documentId)
      : undefined;
    if (currentDocument && !currentDocument.blocksRef) return snapshot(run);
    const unresolvedConflict = selectFirstUnresolvedConflict(run);
    if (unresolvedConflict) {
      const definition = story.listConflictDefinitions().find((candidate) => (
        candidate.conflictId === unresolvedConflict.conflictId
      ));
      if (!definition) throw new Error('标准化运行阻断状态缺少当前来源差异');
      const introducedIndex = sources.findIndex((source) => source.sourceId === definition.introducedBySourceId);
      const context = await loadRunContext(run, {
        sourceLimit: introducedIndex + 1,
        blocksOnly: true,
        skipEventBodies: true,
      });
      conflictCompilations = context.compilations;
      conflictDocuments = context.documents;
      currentConflict = { conflictId: unresolvedConflict.conflictId, definition };
    }
    const currentCompilation = currentStep
      ? compilations.find((compilation) => compilation.sourceId === currentStep.sourceId)
      : undefined;
    const completeTimeline = reviewShellTimeline(run, compilations, documents);
    const timelineWindow = createStandardizationReviewWindow({
      cursorSecret: `guanyijia-review-shell:${run.runId}`,
      index: createArrayReviewWindowIndex(completeTimeline.map((item) => ({
        stableKey: item.itemId, title: item.title, summary: item.summary,
      }))),
      bodies: { async read() { return null; } },
    });
    const timelinePage = await timelineWindow.read({
      runId: run.runId, epoch: `revision:${run.revision}`, stream: 'TIMELINE', filter: 'all', direction: 'BACKWARD',
    });
    const timelineById = new Map(completeTimeline.map((item) => [item.itemId, item]));
    const visibleTimeline = timelinePage.items.flatMap(({ stableKey }) => {
      const item = timelineById.get(stableKey);
      return item ? [item] : [];
    }).reverse();
    const result: GuanyijiaWorkbenchSnapshot = {
      storyId, storyName: '管伊佳数据标准化', batchId, run, contentBinding,
      timeline: visibleTimeline, timelineTotal: timelinePage.total,
      ...(currentStep && currentSource ? { current: {
        source: currentSource, sourceStep: currentStep,
        ...(currentDocument ? {
          document: currentDocument,
          history: documents.filter((document) => document.documentCode === currentDocument.documentCode)
            .sort((left, right) => right.revision - left.revision),
        } : {}),
        ...(currentCompilation ? { compilationSummary: {
          blockCount: currentCompilation.blocks.length,
          sectionCount: standardSectionOrder.length,
          firstReviewSection: standardSectionOrder.find(({ key }) => currentCompilation.blocks.some((block) => (
            block.section === key && block.evidenceStatus !== 'FACT'
          )))?.key ?? standardSectionOrder[0].key,
        } } : {}),
      } } : {}),
      nextAction: nextAction(run), resolutions: [],
    };
    if (currentSource && currentCompilation) {
      result.inspector = inspectorForCompilation(run, currentSource, currentCompilation);
      const scriptedEdits = scriptedReviewStatesFor({
        run, source: currentSource, compilation: currentCompilation, contentBinding, pointer,
      });
      if (scriptedEdits.length) result.scriptedEdits = scriptedEdits;
    }
    if (currentConflict && conflictCompilations && conflictDocuments) {
      const introducedIndex = sources.findIndex((source) => (
        source.sourceId === currentConflict.definition.introducedBySourceId
      ));
      const sourceRevisions = conflictCompilations.slice(0, introducedIndex + 1).map((compilation) => {
        const document = conflictDocuments?.get(compilation.sourceId);
        if (!document) throw new Error(`当前冲突缺少持久化来源revision：${compilation.sourceId}`);
        return { documentId: document.documentId, documentRevision: document.revision, compilation };
      });
      result.currentConflict = {
        conflictId: currentConflict.conflictId,
        title: currentConflict.definition.title,
        hunk: story.buildConflictHunk({ conflictId: currentConflict.conflictId, sourceRevisions }),
        defaultStrategy: currentConflict.definition.defaultStrategy,
        allowedStrategies: [...currentConflict.definition.allowedStrategies],
      };
      result.inspector = factsInspectorFor(result);
    }
    return result;
  }

  async function snapshot(
    run: StandardizationRun | null,
    options: { assistantTurnDelta?: AssistantTurnDelta } = {},
  ): Promise<GuanyijiaWorkbenchSnapshot> {
    const pointer = run ? readPointer(input.pointerStorage, batchId) : null;
    const contentBinding = run
      ? contentBindingForRun(run, pointer)
      : undefined;
    const context = run ? await loadRunContext(run) : {
      compilations: [] as SourceDocumentCompilation[],
      documents: new Map<string, SourceModelingDocument>(),
      legacyDocuments: new Map<string, {
        sections: SourceDocumentSections;
        assertions: StructuredModelingAssertion[];
        markdown: string;
      }>(),
      generatedEvents: new Map<string, SourceModelingDocument>(),
      revisionDiffs: new Map<string, SourceDocumentRevisionDiff>(),
      revisionEvents: new Map<string, { document: SourceModelingDocument; diff: SourceDocumentRevisionDiff }>(),
      resolutions: [] as ConflictResolutionArtifact[],
      assistantTurns: new Map<string, ReviewAssistantTurnArtifact>(),
      allDocuments: [] as SourceModelingDocument[],
    };
    const currentStep = run ? selectCurrentReviewStep(run) : undefined;
    const currentSource = currentStep
      ? sources.find((source) => source.sourceId === currentStep.sourceId)
      : undefined;
    const currentCompilation = currentStep
      ? context.compilations.find((compilation) => compilation.sourceId === currentStep.sourceId)
      : undefined;
    const currentRevisionHistory = currentStep && run
      ? run.timeline.flatMap((event) => event.type === 'DOCUMENT_REVISED'
        && event.sourceId === currentStep.sourceId
        && context.revisionEvents.has(event.eventId)
        ? [context.revisionEvents.get(event.eventId)!.diff]
        : [])
      : [];
    const completeTimeline = run ? timelineFor({
      run,
      compilations: context.compilations,
      documents: context.documents,
      legacyDocuments: context.legacyDocuments,
      generatedEvents: context.generatedEvents,
      revisionEvents: context.revisionEvents,
      assistantTurns: context.assistantTurns,
      story,
    }) : [];
    const timelineWindow = run ? createStandardizationReviewWindow({
      cursorSecret: `guanyijia-timeline:${run.runId}`,
      index: createArrayReviewWindowIndex(completeTimeline.map((item) => ({
        stableKey: item.itemId,
        title: item.title,
        summary: item.summary,
      }))),
      bodies: { async read() { return null; } },
    }) : undefined;
    const timelinePage = run && timelineWindow ? await timelineWindow.read({
      runId: run.runId,
      epoch: `revision:${run.revision}`,
      stream: 'TIMELINE',
      filter: 'all',
      direction: 'BACKWARD',
    }) : undefined;
    const timelineById = new Map(completeTimeline.map((item) => [item.itemId, item]));
    const visibleTimeline = timelinePage
      ? timelinePage.items.flatMap(({ stableKey }) => {
          const item = timelineById.get(stableKey);
          return item ? [item] : [];
        }).reverse()
      : [];
    const result: GuanyijiaWorkbenchSnapshot = {
      storyId,
      storyName: '管伊佳数据标准化',
      batchId,
      run,
      ...(contentBinding ? { contentBinding } : {}),
      timeline: visibleTimeline,
      timelineTotal: timelinePage?.total ?? 0,
      ...(options.assistantTurnDelta ? { assistantTurnDelta: options.assistantTurnDelta } : {}),
      ...(currentStep && currentSource ? {
        current: {
          source: currentSource,
          sourceStep: currentStep,
          ...(context.documents.get(currentStep.sourceId) ? {
            document: context.documents.get(currentStep.sourceId),
            history: context.allDocuments
              .filter((document) => document.documentCode === context.documents.get(currentStep.sourceId)?.documentCode)
              .sort((left, right) => right.revision - left.revision),
          } : {}),
          ...(currentCompilation ? { compilation: currentCompilation } : {}),
          ...(context.legacyDocuments.get(currentStep.sourceId)
            ? { legacyReadOnly: context.legacyDocuments.get(currentStep.sourceId) } : {}),
          ...(context.revisionDiffs.get(currentStep.sourceId)
            ? { revisionDiff: context.revisionDiffs.get(currentStep.sourceId) } : {}),
          ...(currentRevisionHistory.length ? { revisionHistory: currentRevisionHistory } : {}),
        },
      } : {}),
      nextAction: nextAction(run),
      resolutions: context.resolutions,
    };
    if (run && currentSource && currentCompilation) {
      const scriptedEdits = scriptedReviewStatesFor({
        run,
        source: currentSource,
        compilation: currentCompilation,
        contentBinding,
        pointer,
      });
      if (scriptedEdits.length) result.scriptedEdits = scriptedEdits;
    }
    const unresolvedConflict = run ? selectFirstUnresolvedConflict(run) : undefined;
    const definition = unresolvedConflict
      ? story.listConflictDefinitions().find((candidate) => candidate.conflictId === unresolvedConflict.conflictId)
      : undefined;
    if (run && unresolvedConflict && definition) {
      const introducedIndex = sources.findIndex((source) => source.sourceId === definition.introducedBySourceId);
      const sourceRevisions = context.compilations.slice(0, introducedIndex + 1).map((compilation) => {
        const document = context.documents.get(compilation.sourceId);
        if (!document) throw new Error(`当前冲突缺少持久化来源revision：${compilation.sourceId}`);
        return { documentId: document.documentId, documentRevision: document.revision, compilation };
      });
      result.currentConflict = {
        conflictId: unresolvedConflict.conflictId,
        title: definition.title,
        hunk: story.buildConflictHunk({ conflictId: unresolvedConflict.conflictId, sourceRevisions }),
        defaultStrategy: definition.defaultStrategy,
        allowedStrategies: [...definition.allowedStrategies],
      };
    }
    result.inspector = factsInspectorFor(result);
    return result;
  }

  async function readActiveRun(metadataOnly = false) {
    const pointer = readPointer(input.pointerStorage, batchId);
    if (!pointer) return null;
    const run = metadataOnly && input.standardizationRuns.readMetadata
      ? await input.standardizationRuns.readMetadata(pointer.runId)
      : await input.standardizationRuns.read(pointer.runId);
    if (!run) throw new Error(`管伊佳标准化活动运行指针已损坏：运行不存在 ${pointer.runId}`);
    if (run.projectId !== projectId || run.batchId !== batchId) {
      throw new Error('管伊佳标准化活动运行指针已损坏：运行属于其他项目或故事版本');
    }
    assertRunMatchesStory(run, sources);
    return run;
  }

  function assertAssistantSelectionAllowed(
    selection: ReviewAssistantContextSelection,
    run: StandardizationRun,
    context: Awaited<ReturnType<typeof loadRunContext>>,
  ) {
    const selectedSource = selection.sourceId
      ? run.sources.find((source) => source.sourceId === selection.sourceId)
      : undefined;
    if (selection.sourceId && !selectedSource) throw new Error('审阅助手目标不属于当前运行');
    const selectedDocument = selection.documentId
      ? [...context.documents.values()].find((document) => document.documentId === selection.documentId)
      : undefined;
    if (selection.documentId && !selectedDocument) throw new Error('审阅助手文档目标不属于当前运行');
    if (selection.sourceId && selectedDocument
      && context.documents.get(selection.sourceId)?.documentId !== selection.documentId) {
      throw new Error('审阅助手来源与文档目标不一致');
    }
    const sourceId = selection.sourceId ?? (selectedDocument
      ? [...context.documents.entries()].find(([, document]) => document.documentId === selection.documentId)?.[0]
      : undefined);
    const compilation = context.compilations.find((candidate) => candidate.sourceId === sourceId);
    const block = selection.blockId
      ? compilation?.blocks.find((candidate) => candidate.blockId === selection.blockId)
      : undefined;
    if (selection.blockId && !block) throw new Error('审阅助手结构化块目标不属于当前运行');
    if (selection.section && !standardSectionOrder.some(({ key }) => key === selection.section)) {
      throw new Error('审阅助手章节目标不属于标准来源文档');
    }
    if (selection.section && block && selection.section !== block.section) {
      throw new Error('审阅助手章节目标与当前Block不一致');
    }
    if (selection.evidenceRef && (!block?.evidenceRefs.includes(selection.evidenceRef)
      || !compilation?.evidenceLocators[selection.evidenceRef])) {
      throw new Error('审阅助手Evidence目标不属于当前运行');
    }
    if (selection.conflictId && !run.sources.some((source) => (
      source.introducedConflictIds.includes(selection.conflictId!)
    ))) throw new Error('审阅助手冲突目标不属于当前运行');
    if (selection.conflictId && selection.sourceId
      && story.listConflictDefinitions().find((definition) => (
        definition.conflictId === selection.conflictId
      ))?.introducedBySourceId !== selection.sourceId) {
      throw new Error('审阅助手冲突目标与当前来源不一致');
    }
    if (selection.timelineItemId) {
      const event = run.timeline.find((candidate) => candidate.eventId === selection.timelineItemId);
      if (!event) throw new Error('审阅助手时间线目标不属于当前运行');
      if (selection.sourceId && event.sourceId !== selection.sourceId) {
        throw new Error('审阅助手时间线目标与当前来源不一致');
      }
    }
    if (selection.objectRef) {
      const key = canonicalModelingJson(selection.objectRef);
      if (block && !block.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)) {
        throw new Error('审阅助手影响对象目标与当前Block不一致');
      }
      const selectedConflict = selection.conflictId
        ? story.listConflictDefinitions().find((definition) => definition.conflictId === selection.conflictId)
        : undefined;
      if (!block && selectedConflict
        && !selectedConflict.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)) {
        throw new Error('审阅助手影响对象目标与当前冲突不一致');
      }
      if (!block && !selectedConflict) {
        const belongs = context.compilations.some((candidate) => candidate.blocks.some((candidateBlock) => (
          candidateBlock.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)
        ))) || story.listConflictDefinitions().some((definition) => (
          run.sources.some((source) => source.introducedConflictIds.includes(definition.conflictId))
          && definition.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === key)
        ));
        if (!belongs) throw new Error('审阅助手影响对象目标不属于当前运行');
      }
    }
  }

  async function readAssistantTurnDelta(
    run: StandardizationRun,
    event: StandardizationTimelineEvent,
    expected: {
      actorUserId: string;
      message: string;
      selection: ReviewAssistantContextSelection;
    },
  ): Promise<AssistantTurnDelta> {
    if (event.type !== 'ASSISTANT_TURN_RECORDED' || !event.assistantTurnId) {
      throw new Error('审阅助手Turn事件类型或身份无效：' + event.eventId);
    }
    const allTurnEvents = run.timeline.filter((candidate) => candidate.type === 'ASSISTANT_TURN_RECORDED');
    const eventIndex = run.timeline.findIndex((candidate) => candidate.eventId === event.eventId);
    const position = allTurnEvents.findIndex((candidate) => candidate.eventId === event.eventId) + 1;
    if (eventIndex < 0 || position < 1) throw new Error('审阅助手Turn全局位置无效：' + event.eventId);
    const rawTurn = await input.standardizationRuns.readEventPayload(run.runId, event.eventId);
    let turn: ReviewAssistantTurnArtifact;
    try { turn = JSON.parse(rawTurn) as ReviewAssistantTurnArtifact; } catch {
      throw new Error('审阅助手Turn事件载荷无法解析：' + event.eventId);
    }
    const intent = classifyReviewAssistantIntent(expected.message);
    if (turn.schemaVersion !== 1 || turn.runId !== run.runId
      || turn.actorUserId !== expected.actorUserId || turn.actorUserId !== event.actor.userId
      || turn.turnId !== event.assistantTurnId
      || turn.message !== intent.normalizedMessage
      || canonicalModelingJson(turn.selection) !== canonicalModelingJson(expected.selection)
      || canonicalModelingJson(turn.knowledgeSourceRevisions)
        !== canonicalModelingJson(event.assistantKnowledgeSourceRevisions)) {
      throw new Error('审阅助手Turn正文与ASK命令或事件索引不一致：' + event.eventId);
    }

    const latestByDocument = new Map<string, string>();
    const confirmed = new Set<string>();
    const cancelled = new Set<string>();
    for (const candidate of run.timeline) {
      if (candidate.type === 'ASSISTANT_TURN_RECORDED'
        && candidate.assistantProposalId && candidate.assistantDocumentId) {
        latestByDocument.set(candidate.assistantDocumentId, candidate.assistantProposalId);
      } else if (candidate.type === 'ASSISTANT_PATCH_CONFIRMED' && candidate.assistantProposalId) {
        confirmed.add(candidate.assistantProposalId);
      } else if (candidate.type === 'ASSISTANT_PATCH_CANCELLED' && candidate.assistantProposalId) {
        cancelled.add(candidate.assistantProposalId);
      }
    }
    const pendingDocument = run.status === 'REVIEWING_DOCUMENT'
      ? run.sources.find((source) => source.status === 'DOCUMENT_READY')
      : undefined;

    let item: ReviewAssistantHistoryItem;
    if (turn.response.kind === 'PATCH_PREVIEW') {
      if (!input.contentStore) throw new Error('审阅助手Proposal delta缺少内容存储');
      const rawProposal = await input.contentStore.get(turn.response.proposalRef);
      if (rawProposal === null || 'sha256:' + sha256HexSync(rawProposal) !== turn.response.proposalRef) {
        throw new Error('审阅助手Proposal内容不存在或校验和不一致：' + turn.response.proposalId);
      }
      let proposal: AssistantStructuredChangeProposal;
      try { proposal = JSON.parse(rawProposal) as AssistantStructuredChangeProposal; } catch {
        throw new Error('审阅助手Proposal正文无法解析：' + turn.response.proposalId);
      }
      if (proposal.schemaVersion !== 1 || proposal.proposalId !== turn.response.proposalId
        || proposal.runId !== run.runId || proposal.createdBy !== turn.actorUserId
        || proposal.proposalId !== event.assistantProposalId
        || proposal.documentId !== event.assistantDocumentId
        || proposal.documentRevision !== event.assistantDocumentRevision) {
        throw new Error('审阅助手Proposal身份不一致：' + turn.response.proposalId);
      }
      await validateGuanyijiaAssistantArtifactAgainstPersistedRun({
        turn, proposal, run, sourceDocuments: input.sourceDocuments, story, eventIndex,
      });
      item = {
        eventId: event.eventId, sequence: event.sequence, position, actorUserId: turn.actorUserId,
        message: turn.message,
        response: { kind: 'PATCH_PREVIEW', title: turn.response.title, body: turn.response.body, proposal },
        createdAt: turn.createdAt,
        proposalStatus: confirmed.has(proposal.proposalId)
          ? 'CONFIRMED'
          : cancelled.has(proposal.proposalId)
            ? 'CANCELLED'
            : latestByDocument.get(proposal.documentId) === proposal.proposalId
              && pendingDocument?.documentId === proposal.documentId
              && pendingDocument.documentRevision === proposal.documentRevision
              ? 'PENDING' : 'SUPERSEDED',
      };
    } else {
      await validateGuanyijiaAssistantArtifactAgainstPersistedRun({
        turn, proposal: undefined, run, sourceDocuments: input.sourceDocuments, story, eventIndex,
      });
      item = {
        eventId: event.eventId, sequence: event.sequence, position, actorUserId: turn.actorUserId,
        message: turn.message, response: turn.response as ReviewAssistantResponse, createdAt: turn.createdAt,
      };
    }
    return {
      item,
      total: allTurnEvents.length,
      eventId: event.eventId,
      position,
      contentRef: event.payloadRef,
      contentSha256: event.payloadRef.slice('sha256:'.length),
    };
  }

  async function readAssistantTurnDeltaForCommand(
    run: StandardizationRun,
    command: Extract<GuanyijiaWorkbenchCommand, { type: 'ASK_REVIEW_ASSISTANT' }>,
  ) {
    const turnId = 'assistant-turn:' + sha256HexSync(command.commandId).slice(0, 20);
    const event = run.timeline.find((candidate) => (
      candidate.type === 'ASSISTANT_TURN_RECORDED' && candidate.assistantTurnId === turnId
    ));
    if (!event) throw new Error('持久化助手Turn不存在：' + turnId);
    return readAssistantTurnDelta(run, event, {
      actorUserId: command.actorUserId,
      message: command.message,
      selection: command.selection,
    });
  }

  function assistantKnowledge(
    run: StandardizationRun,
    context: Awaited<ReturnType<typeof loadRunContext>>,
    selection: ReviewAssistantContextSelection,
  ): ReviewAssistantKnowledge {
    const currentStep = selectCurrentReviewStep(run);
    const currentConflict = selectFirstUnresolvedConflict(run);
    return {
      sources: context.compilations.map((compilation) => {
        const source = sources.find((candidate) => candidate.sourceId === compilation.sourceId)!;
        const document = context.documents.get(compilation.sourceId);
        return {
          sourceId: source.sourceId, sourceName: source.sourceName, sourceClass: source.sourceClass,
          authority: source.authority, snapshotId: source.snapshotId,
          versionRef: compilation.readSummary.versionRef, readSummary: compilation.readSummary.summary,
          ...(document ? { documentId: document.documentId, documentRevision: document.revision } : {}),
          blocks: compilation.blocks, evidenceLocators: compilation.evidenceLocators,
        };
      }),
      conflicts: story.listConflictDefinitions().filter((definition) => run.sources.some((source) => (
        source.introducedConflictIds.includes(definition.conflictId)
      ))).map((definition) => {
        const introducedIndex = sources.findIndex((source) => (
          source.sourceId === definition.introducedBySourceId
        ));
        const sourceRevisions = context.compilations.slice(0, introducedIndex + 1).flatMap((candidate) => {
          const document = context.documents.get(candidate.sourceId);
          return document ? [{
            documentId: document.documentId,
            documentRevision: document.revision,
            compilation: candidate,
          }] : [];
        });
        return {
          conflictId: definition.conflictId, title: definition.title,
          affectedObjectRefs: definition.affectedObjectRefs,
          ...(sourceRevisions.length === introducedIndex + 1
            ? { hunk: story.buildConflictHunk({ conflictId: definition.conflictId, sourceRevisions }) }
            : {}),
        };
      }),
      ...(currentStep ? { currentSourceId: currentStep.sourceId, currentDocumentId: currentStep.documentId } : {}),
      ...(selection.blockId ? { currentBlockId: selection.blockId } : {}),
      ...(currentConflict ? { currentConflictId: currentConflict.conflictId } : {}),
    };
  }

  async function readAssistantProposalProjection(
    run: StandardizationRun,
  ) {
    if (!input.contentStore) throw new Error('审阅助手Proposal缺少内容存储');
    const proposals = new Map<string, {
      proposal: AssistantStructuredChangeProposal;
      proposalRef: `sha256:${string}`;
    }>();
    const latestByDocument = new Map<string, string>();
    for (let eventIndex = 0; eventIndex < run.timeline.length; eventIndex += 1) {
      const event = run.timeline[eventIndex]!;
      if (event.type !== 'ASSISTANT_TURN_RECORDED' || !event.assistantProposalId) continue;
      const turnRaw = await input.standardizationRuns.readEventPayload(run.runId, event.eventId);
      const turn = JSON.parse(turnRaw) as ReviewAssistantTurnArtifact;
      if (turn.response.kind !== 'PATCH_PREVIEW'
        || turn.turnId !== event.assistantTurnId
        || canonicalModelingJson(turn.knowledgeSourceRevisions)
          !== canonicalModelingJson(event.assistantKnowledgeSourceRevisions)) {
        throw new Error(`审阅助手Turn正文与事件索引不一致：${event.eventId}`);
      }
      const raw = await input.contentStore.get(turn.response.proposalRef);
      if (raw === null || `sha256:${sha256HexSync(raw)}` !== turn.response.proposalRef) {
        throw new Error(`审阅助手Proposal内容不存在或校验和不一致：${turn.response.proposalId}`);
      }
      const proposal = JSON.parse(raw) as AssistantStructuredChangeProposal;
      if (proposal.schemaVersion !== 1 || proposal.proposalId !== turn.response.proposalId
        || proposal.runId !== run.runId || proposal.createdBy !== turn.actorUserId
        || proposals.has(proposal.proposalId)
        || proposal.proposalId !== event.assistantProposalId
        || proposal.documentId !== event.assistantDocumentId
        || proposal.documentRevision !== event.assistantDocumentRevision) {
        throw new Error(`审阅助手Proposal身份重复或不一致：${turn.response.proposalId}`);
      }
      await validateGuanyijiaAssistantArtifactAgainstPersistedRun({
        turn, proposal, run, sourceDocuments: input.sourceDocuments, story, eventIndex,
      });
      proposals.set(proposal.proposalId, { proposal, proposalRef: turn.response.proposalRef });
      latestByDocument.set(proposal.documentId, proposal.proposalId);
    }
    const confirmed = new Set<string>();
    const cancelled = new Set<string>();
    for (const event of run.timeline.filter((candidate) => candidate.type === 'ASSISTANT_PATCH_CONFIRMED')) {
      const proposalId = event.assistantProposalId;
      if (!proposalId || !proposals.has(proposalId) || confirmed.has(proposalId)) {
        throw new Error(`审阅助手确认事件没有回链唯一Proposal：${event.eventId}`);
      }
      confirmed.add(proposalId);
    }
    for (const event of run.timeline.filter((candidate) => candidate.type === 'ASSISTANT_PATCH_CANCELLED')) {
      const proposalId = event.assistantProposalId;
      if (!proposalId || !proposals.has(proposalId) || confirmed.has(proposalId)
        || cancelled.has(proposalId)) {
        throw new Error(`审阅助手取消事件没有回链唯一Proposal：${event.eventId}`);
      }
      cancelled.add(proposalId);
    }
    return { proposals, latestByDocument, confirmed, cancelled };
  }

  return {
    async read(actorUserId) {
      if (!actorUserId.trim()) throw new Error('工作台操作者不能为空');
      requireActiveAccess(actorUserId);
      return snapshot(await readActiveRun());
    },
    async readReviewShell(actorUserId) {
      if (!actorUserId.trim()) throw new Error('工作台操作者不能为空');
      requireActiveAccess(actorUserId);
      return reviewShellSnapshot(await readActiveRun(true));
    },
    async readTechnicalResolutionSummaries({ actorUserId, runId, revision }) {
      if (!actorUserId.trim()) throw new Error('工作台操作者不能为空');
      requireActiveAccess(actorUserId);
      const run = await readActiveRun(true);
      if (!run || run.runId !== runId) throw new Error('resolution artifact不属于当前运行');
      if (run.revision !== revision) throw new Error('resolution artifact不属于当前运行revision，请刷新后重试');
      const events = run.timeline.filter((event) => (
        event.type === 'CONFLICT_RESOLVED' || event.type === 'CONFLICT_DECISION_REPLACED'
      ));
      return Promise.all(events.map(async (event) => {
        const raw = await input.standardizationRuns.readEventPayload(run.runId, event.eventId);
        let parsed: unknown;
        try { parsed = JSON.parse(raw); } catch {
          throw new Error(`resolution artifact无法解析：${event.eventId}`);
        }
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          throw new Error(`resolution artifact结构无效：${event.eventId}`);
        }
        const artifact = parsed as ConflictResolutionArtifact;
        const isReplacement = event.type === 'CONFLICT_DECISION_REPLACED';
        const expectedResolutionId = isReplacement
          ? `replacement:${run.runId}:${event.conflictId}:${event.sequence}`
          : `resolution:${run.runId}:${artifact.hunk?.conflictId}`;
        if (artifact.runId !== run.runId
          || (!isReplacement && artifact.sourceId !== event.sourceId)
          || (isReplacement && event.conflictId !== artifact.hunk?.conflictId)
          || artifact.actorUserId !== event.actor.userId
          || artifact.resolutionId !== expectedResolutionId
          || !artifact.hunk || typeof artifact.hunk.title !== 'string'
          || typeof artifact.hunk.hunkSha256 !== 'string'
          || typeof artifact.previewSha256 !== 'string') {
          throw new Error(`resolution artifact与事件身份不一致：${event.eventId}`);
        }
        try {
          story.validateConflictResolutionArtifactProjection(artifact);
        } catch (cause) {
          const detail = cause instanceof Error ? cause.message : '未知校验错误';
          throw new Error(`resolution artifact无法通过Story纯投影复算：${event.eventId}：${detail}`);
        }
        return {
          resolutionId: artifact.resolutionId,
          sourceId: artifact.sourceId,
          title: artifact.hunk.title,
          hunkSha256: artifact.hunk.hunkSha256,
          previewSha256: artifact.previewSha256,
          eventId: event.eventId,
          sequence: event.sequence,
          createdAt: event.createdAt,
        };
      }));
    },
    async readReviewCurrentDocument(reviewInput) {
      if (!reviewInput.actorUserId.trim()) throw new Error('工作台操作者不能为空');
      requireActiveAccess(reviewInput.actorUserId);
      const run = await readActiveRun();
      if (!run || run.runId !== reviewInput.runId) throw new Error('来源文档不属于当前运行');
      const documentContext = await reviewDocumentContext(run, reviewInput.documentId);
      if (!documentContext.current.document.blocksRef) throw new Error('旧版来源文档没有结构化块，只能只读');
      const window = await reviewWindow.read({
        runId: run.runId,
        epoch: reviewInput.epoch,
        stream: 'DOCUMENT_BLOCKS',
        // Opening a document always obtains the fixed 20-block document window.
        // The selected section is presentation state; it must not shrink the
        // transport page below the review-window contract.
        filter: new URLSearchParams({ document: reviewInput.documentId }).toString(),
        ...(reviewInput.cursor ? { cursor: reviewInput.cursor } : {}),
      });
      const verified = await readVerifiedReviewContent({
        runId: run.runId,
        contentRef: documentContext.current.document.blocksRef,
        expectedSha256: documentContext.current.document.blocksRef.slice('sha256:'.length),
      });
      let allBlocks: SourceDocumentBlock[];
      try { allBlocks = JSON.parse(verified.content) as SourceDocumentBlock[]; } catch {
        throw new Error('来源文档结构化块正文无法解析');
      }
      if (!Array.isArray(allBlocks)) throw new Error('来源文档结构化块正文不是数组');
      const byStableCode = new Map(allBlocks.map((block) => [block.stableCode, block]));
      const blocks = window.items.map((item) => {
        const block = byStableCode.get(item.title);
        if (!block) throw new Error(`来源文档窗口索引与已校验正文不一致：${item.title}`);
        return block;
      });
      const compilation = documentContext.compilation;
      const visibleEvidenceRefs = new Set(blocks.flatMap((block) => block.evidenceRefs));
      const evidenceLocators = Object.fromEntries(Object.entries(compilation.evidenceLocators).filter(([ref]) => (
        visibleEvidenceRefs.has(ref)
      )));
      return {
        window,
        current: {
          ...documentContext.current,
          reviewCompilation: {
            sourceId: compilation.sourceId,
            sourceName: compilation.sourceName,
            sourceClass: compilation.sourceClass,
            readSummary: compilation.readSummary,
            lineageStatus: compilation.lineageStatus,
            upstreamSourceIds: compilation.upstreamSourceIds,
            blocks,
            evidenceLocators,
          },
        },
      };
    },
    async readReviewDocumentShell(reviewInput) {
      if (!reviewInput.actorUserId.trim()) throw new Error('工作台操作者不能为空');
      requireActiveAccess(reviewInput.actorUserId);
      const run = await readActiveRun();
      if (!run || run.runId !== reviewInput.runId) throw new Error('来源文档不属于当前运行');
      return (await reviewDocumentContext(run, reviewInput.documentId)).current;
    },
    async readReviewWindow(reviewInput) {
      if (!reviewInput.actorUserId.trim()) throw new Error('工作台操作者不能为空');
      requireActiveAccess(reviewInput.actorUserId);
      return reviewWindow.read(reviewInput);
    },
    async readReviewContent(reviewInput) {
      if (!reviewInput.actorUserId.trim()) throw new Error('工作台操作者不能为空');
      requireActiveAccess(reviewInput.actorUserId);
      return readVerifiedReviewContent(reviewInput);
    },
    async previewCurrentConflict(previewInput) {
      const run = await readActiveRun();
      if (!run || run.runId !== previewInput.runId) {
        throw new Error('当前没有待解决的来源冲突');
      }
      const context = await loadRunContext(run);
      const unresolvedConflict = selectFirstUnresolvedConflict(run);
      if (unresolvedConflict && canResolveFirstUnresolvedConflict(run, unresolvedConflict)) {
        if (unresolvedConflict.conflictId !== previewInput.conflictId) {
          throw new Error('只能预览当前第一项未解决冲突');
        }
      } else if (run.status !== 'READY_FOR_OUTPUT'
        || !context.resolutions.some((artifact) => artifact.hunk.conflictId === previewInput.conflictId)) {
        throw new Error('只能在结果定版前重新处理已保存的来源差异');
      }
      const definition = story.listConflictDefinitions().find((candidate) => (
        candidate.conflictId === previewInput.conflictId
      ));
      if (!definition) throw new Error(`未知管伊佳来源冲突：${previewInput.conflictId}`);
      const introducedIndex = sources.findIndex((source) => source.sourceId === definition.introducedBySourceId);
      return story.previewConflictResolution({
        conflictId: previewInput.conflictId,
        strategy: previewInput.strategy,
        sourceRevisions: context.compilations.slice(0, introducedIndex + 1).map((compilation) => {
          const document = context.documents.get(compilation.sourceId);
          if (!document) throw new Error(`当前冲突缺少持久化来源revision：${compilation.sourceId}`);
          return { documentId: document.documentId, documentRevision: document.revision, compilation };
        }),
      });
    },
    async readAssistantHistory(historyInput) {
      if (!historyInput.actorUserId.trim()) throw new Error('工作台操作者不能为空');
      requireActiveAccess(historyInput.actorUserId);
      const run = await readActiveRun();
      if (!run || run.runId !== historyInput.runId) throw new Error('审阅助手历史不属于当前运行');
      const allTurnEvents = run.timeline.filter((event) => event.type === 'ASSISTANT_TURN_RECORDED');
      const eventBySummaryKey = new Map(allTurnEvents.map((event) => [
        `assistant:${String(event.sequence).padStart(8, '0')}:${event.eventId}`,
        event,
      ]));
      const historyPage = await reviewWindow.read({
        runId: run.runId,
        epoch: `revision:${run.revision}`,
        stream: 'ASSISTANT_HISTORY',
        filter: 'all',
        direction: 'BACKWARD',
        ...(historyInput.cursor ? { cursor: historyInput.cursor } : {}),
      });
      const pageEvents = historyPage.items.flatMap(({ stableKey }) => {
        const event = eventBySummaryKey.get(stableKey);
        return event ? [event] : [];
      }).reverse();
      const latestByDocument = new Map<string, string>();
      const confirmed = new Set<string>();
      const cancelled = new Set<string>();
      for (const event of run.timeline) {
        if (event.type === 'ASSISTANT_TURN_RECORDED'
          && event.assistantProposalId && event.assistantDocumentId) {
          latestByDocument.set(event.assistantDocumentId, event.assistantProposalId);
        } else if (event.type === 'ASSISTANT_PATCH_CONFIRMED' && event.assistantProposalId) {
          confirmed.add(event.assistantProposalId);
        } else if (event.type === 'ASSISTANT_PATCH_CANCELLED' && event.assistantProposalId) {
          cancelled.add(event.assistantProposalId);
        }
      }
      const pendingDocument = run.status === 'REVIEWING_DOCUMENT'
        ? run.sources.find((source) => source.status === 'DOCUMENT_READY')
        : undefined;
      const visibleConfirmedProposalIds = new Set<string>();
      const readHistoryItem = async (
        event: StandardizationTimelineEvent,
      ): Promise<ReviewAssistantHistoryPage['items'][number]> => {
        const position = allTurnEvents.findIndex((candidate) => candidate.eventId === event.eventId) + 1;
        if (position < 1) throw new Error('审阅助手Turn全局位置无效');
        const rawTurn = (await readVerifiedReviewContent({
          runId: run.runId,
          contentRef: event.payloadRef,
          expectedSha256: event.payloadRef.slice('sha256:'.length),
        })).content;
        let turn: ReviewAssistantTurnArtifact;
        try { turn = JSON.parse(rawTurn) as ReviewAssistantTurnArtifact; } catch {
          throw new Error(`审阅助手Turn事件载荷无法解析：${event.eventId}`);
        }
        if (turn.schemaVersion !== 1 || turn.runId !== run.runId
          || turn.actorUserId !== event.actor.userId || turn.turnId !== event.assistantTurnId
          || canonicalModelingJson(turn.knowledgeSourceRevisions)
            !== canonicalModelingJson(event.assistantKnowledgeSourceRevisions)) {
          throw new Error(`审阅助手Turn正文与事件索引不一致：${event.eventId}`);
        }
        const eventIndex = run.timeline.findIndex((candidate) => candidate.eventId === event.eventId);
        if (turn.response.kind === 'PATCH_PREVIEW') {
          if (!input.contentStore) throw new Error('审阅助手Proposal历史缺少内容存储');
          const raw = await input.contentStore.get(turn.response.proposalRef);
          if (raw === null || `sha256:${sha256HexSync(raw)}` !== turn.response.proposalRef) {
            throw new Error(`审阅助手Proposal内容不存在或校验和不一致：${turn.response.proposalId}`);
          }
          const proposal = JSON.parse(raw) as AssistantStructuredChangeProposal;
          if (proposal.schemaVersion !== 1 || proposal.proposalId !== turn.response.proposalId
            || proposal.runId !== run.runId || proposal.createdBy !== turn.actorUserId
            || proposal.proposalId !== event.assistantProposalId
            || proposal.documentId !== event.assistantDocumentId
            || proposal.documentRevision !== event.assistantDocumentRevision) {
            throw new Error(`审阅助手Proposal身份不一致：${turn.response.proposalId}`);
          }
          await validateGuanyijiaAssistantArtifactAgainstPersistedRun({
            turn, proposal, run, sourceDocuments: input.sourceDocuments, story, eventIndex,
          });
          if (confirmed.has(proposal.proposalId)) {
            visibleConfirmedProposalIds.add(proposal.proposalId);
          }
          return {
            eventId: event.eventId, sequence: event.sequence, position, actorUserId: turn.actorUserId,
            message: turn.message,
            response: {
              kind: 'PATCH_PREVIEW' as const, title: turn.response.title,
              body: turn.response.body, proposal,
            },
            createdAt: turn.createdAt,
            proposalStatus: confirmed.has(proposal.proposalId)
              ? 'CONFIRMED' as const
              : cancelled.has(proposal.proposalId)
                ? 'CANCELLED' as const
              : latestByDocument.get(proposal.documentId) === proposal.proposalId
                && pendingDocument?.documentId === proposal.documentId
                && pendingDocument.documentRevision === proposal.documentRevision
                ? 'PENDING' as const : 'SUPERSEDED' as const,
          };
        }
        await validateGuanyijiaAssistantArtifactAgainstPersistedRun({
          turn, proposal: undefined, run, sourceDocuments: input.sourceDocuments, story, eventIndex,
        });
        return {
          eventId: event.eventId, sequence: event.sequence, position, actorUserId: turn.actorUserId,
          message: turn.message, response: turn.response as ReviewAssistantResponse, createdAt: turn.createdAt,
        };
      };
      const items = await Promise.all(pageEvents.map(readHistoryItem));
      const pendingProposalId = pendingDocument?.documentId
        ? latestByDocument.get(pendingDocument.documentId)
        : undefined;
      const pendingEvent = pendingProposalId && !confirmed.has(pendingProposalId)
        && !cancelled.has(pendingProposalId)
        ? run.timeline.find((event) => (
            event.type === 'ASSISTANT_TURN_RECORDED'
            && event.assistantProposalId === pendingProposalId
            && event.assistantDocumentId === pendingDocument?.documentId
            && event.assistantDocumentRevision === pendingDocument?.documentRevision
          ))
        : undefined;
      const pendingProposal = pendingEvent
        ? items.find((item) => item.eventId === pendingEvent.eventId) ?? await readHistoryItem(pendingEvent)
        : undefined;
      await input.standardizationRuns.validateAssistantPatchConfirmations(
        run.runId, [...visibleConfirmedProposalIds],
      );
      return {
        items,
        total: historyPage.total,
        ...(pendingProposal ? { pendingProposal } : {}),
        nextCursor: historyPage.nextCursor,
      };
    },
    async execute(command) {
      assertCommand(command);
      const access = requireActiveAccess(command.actorUserId);
      if (!['ASK_REVIEW_ASSISTANT', 'PREVIEW_CURRENT_BLOCK_CHANGE', 'PREVIEW_SCRIPTED_REVIEW_EDIT'].includes(command.type)
        && access.role !== 'EDITOR' && access.role !== 'ADMIN') {
        throw new Error('只有编辑者或管理员可以执行当前工作台修改');
      }
      const pointer = readPointer(input.pointerStorage, batchId);
      const priorFingerprint = pointer
        && Object.hasOwn(pointer.commandFingerprints, command.commandId)
        ? pointer.commandFingerprints[command.commandId]
        : undefined;
      if (priorFingerprint) {
        if (priorFingerprint !== workbenchCommandFingerprint(command)) {
          throw new Error('工作台 commandId 已用于不同命令，不能伪装成幂等重试');
        }
        const replayedRun = await readActiveRun();
        if (command.type === 'ASK_REVIEW_ASSISTANT') {
          return snapshot(replayedRun, {
            assistantTurnDelta: await readAssistantTurnDeltaForCommand(replayedRun!, command),
          });
        }
        return snapshot(replayedRun);
      }
      const pendingApply = pointer?.pendingApplies?.[command.commandId];
      if (pendingApply && pendingApply.fingerprint !== workbenchCommandFingerprint(command)) {
        throw new Error('工作台 commandId 已用于不同的未完成修改，不能伪装成幂等重试');
      }
      const pendingResolution = pointer?.pendingResolutions?.[command.commandId];
      if (pendingResolution && pendingResolution.fingerprint !== workbenchCommandFingerprint(command)) {
        throw new Error('工作台 commandId 已用于不同的未完成冲突决定，不能伪装成幂等重试');
      }
      const pendingAssistantApply = pointer?.pendingAssistantApplies?.[command.commandId];
      if (pendingAssistantApply
        && pendingAssistantApply.fingerprint !== workbenchCommandFingerprint(command)) {
        throw new Error('工作台 commandId 已用于不同的未完成助手修改，不能伪装成幂等重试');
      }
      const pendingAssistantTurn = pointer?.pendingAssistantTurns?.[command.commandId];
      if (pendingAssistantTurn
        && pendingAssistantTurn.fingerprint !== workbenchCommandFingerprint(command)) {
        throw new Error('工作台 commandId 已用于不同的未完成助手提问，不能伪装成幂等重试');
      }
      const hasUnfinishedAssistantApply = Object.keys(pointer?.pendingAssistantApplies ?? {}).length > 0;
      if (hasUnfinishedAssistantApply
        && command.type !== 'ASK_REVIEW_ASSISTANT'
        && command.type !== 'PREVIEW_CURRENT_BLOCK_CHANGE'
        && command.type !== 'PREVIEW_SCRIPTED_REVIEW_EDIT'
        && !(command.type === 'CONFIRM_ASSISTANT_PATCH' && pendingAssistantApply)) {
        throw new Error('当前文档有未完成的助手修改，请先恢复原确认');
      }
      if (command.type === 'START_RUN') {
        const run = await input.standardizationRuns.execute({
          type: 'CREATE_RUN',
          commandId: `${storyId}:${command.commandId}:create`,
          expectedRevision: command.expectedRevision,
          actor: { userId: command.actorUserId },
          projectId,
          scenarioKey: storyId,
          batchId,
          sources: sources.map((source) => ({
            sourceId: source.sourceId,
            sourceName: source.sourceName,
            snapshotId: source.snapshotId,
          })),
        });
        assertRunMatchesStory(run, sources);
        const contentBinding = bindDemoContentRun({
          runId: run.runId,
          formalSources: formalSourcesForDemoContent(run),
        });
        persistPointer(input.pointerStorage, batchId, run.runId, command, contentBinding);
        return snapshot(run);
      }

      const activeRun = await readActiveRun();
      if (!activeRun) throw new Error('请先开始数据标准化');
      if (command.type === 'PREVIEW_SCRIPTED_REVIEW_EDIT'
        || command.type === 'DECIDE_SCRIPTED_REVIEW_EDIT') {
        if (activeRun.revision !== command.expectedRevision) {
          throw new Error('标准化运行revision已变化，请刷新后重试');
        }
        const currentStep = activeRun.sources.find((step) => step.status === 'DOCUMENT_READY');
        if (!currentStep?.documentId || !currentStep.documentRevision) {
          throw new Error('当前没有待审阅的来源文档');
        }
        assertRenderedDocumentIsCurrent(command, currentStep);
        const source = sources.find((candidate) => candidate.sourceId === currentStep.sourceId);
        const definition = findScriptedReviewEdit(command.editId);
        const contentBinding = contentBindingForRun(activeRun, pointer);
        const context = await loadRunContext(activeRun);
        const compilation = context.compilations.find((candidate) => candidate.sourceId === currentStep.sourceId);
        const block = compilation?.blocks.find((candidate) => candidate.blockId === definition?.formalBlockId);
        if (!source || !definition || !compilation || !block || source.snapshotId !== definition.sourceSnapshotId) {
          throw new Error('剧本编辑映射校验失败');
        }
        const review = contentBinding ? readSourceReviewDocument({
          sourceId: source.sourceId, snapshotId: source.snapshotId, contentBinding,
        }) : undefined;
        if (!review) throw new Error('剧本编辑映射校验失败');
        validateScriptedReviewEditBinding({ definition, review, block });
        const priorDecision = pointer?.scriptedReviewDecisions?.[definition.editId];
        if (priorDecision) throw new Error('该建议核对项已完成');
        if (command.type === 'DECIDE_SCRIPTED_REVIEW_EDIT' && command.decision === 'KEEP_CURRENT') {
          persistPointer(input.pointerStorage, batchId, activeRun.runId, command, undefined, {
            definition, decision: command.decision, actorUserId: command.actorUserId, decidedAt: now(),
          });
          return snapshot(activeRun);
        }
        const patch = command.type === 'PREVIEW_SCRIPTED_REVIEW_EDIT' ? command.patch : command.patch ?? {};
        const change = buildScriptedReviewBlockChange(definition, block, patch);
        const revised = story.reviseCompilation({
          compilation,
          priorCompilations: context.compilations.filter((candidate) => candidate.sourceId !== currentStep.sourceId),
          changes: [change],
        });
        const preview = previewFromDiff(revised.diff);
        const previewSha256 = scriptedPreviewSha256({
          run: activeRun,
          sourceId: source.sourceId,
          documentId: currentStep.documentId,
          documentRevision: currentStep.documentRevision,
          editId: definition.editId,
          beforeBlock: block,
          change,
          preview,
        });
        if (command.type === 'PREVIEW_SCRIPTED_REVIEW_EDIT') {
          const result = await snapshot(activeRun);
          result.preview = preview;
          result.scriptedEditPreviewSha256 = previewSha256;
          return result;
        }
        if (command.expectedPreviewSha256 !== previewSha256) {
          throw new Error('剧本修改预览已变化，请重新预览');
        }
        assertCurrentDocumentRevisionUnlocked(currentStep);
        let revisedDocument: SourceModelingDocument | null = null;
        if (pendingApply) {
          if (pendingApply.beforeDocumentId !== currentStep.documentId) {
            throw new Error('未完成修改对应的来源文档revision已变化');
          }
          revisedDocument = await input.sourceDocuments.read(pendingApply.afterDocumentId);
          if (!revisedDocument || revisedDocument.derivedFromDocumentId !== currentStep.documentId) {
            throw new Error('未完成修改引用的来源文档不存在或血缘不一致');
          }
          const [blocks, sections, assertions] = await Promise.all([
            input.sourceDocuments.readBlocks(revisedDocument.documentId),
            input.sourceDocuments.readSections(revisedDocument.documentId),
            input.sourceDocuments.readAssertions(revisedDocument.documentId),
          ]);
          if (canonicalModelingJson(blocks) !== canonicalModelingJson(revised.compilation.blocks)
            || canonicalModelingJson(sections) !== canonicalModelingJson(revised.compilation.sections)
            || canonicalModelingJson(assertions) !== canonicalModelingJson(revised.compilation.assertions)) {
            throw new Error('未完成剧本修改的来源文档内容与当前命令不一致');
          }
        } else {
          revisedDocument = await input.sourceDocuments.revise({
            documentId: currentStep.documentId,
            expectedRevision: currentStep.documentRevision,
            sections: revised.compilation.sections,
            assertions: revised.compilation.assertions,
            blocks: revised.compilation.blocks,
            actorUserId: command.actorUserId,
          });
          persistPendingApply(
            input.pointerStorage, batchId, activeRun.runId, command,
            currentStep.documentId, revisedDocument.documentId,
          );
        }
        const run = await input.standardizationRuns.execute({
          type: 'REVISE_SOURCE_DOCUMENT',
          commandId: `${storyId}:${command.commandId}:revise-source-document`,
          expectedRevision: command.expectedRevision,
          actor: { userId: command.actorUserId },
          runId: activeRun.runId,
          sourceId: currentStep.sourceId,
          documentId: revisedDocument.documentId,
          documentRevision: revisedDocument.revision,
          introducedConflictIds: revised.compilation.introducedConflictIds,
          documentContent: {
            sectionsRef: revisedDocument.sectionsRef,
            assertionsRef: revisedDocument.assertionsRef,
            blocksRef: revisedDocument.blocksRef!,
            markdownRef: revisedDocument.markdownRef,
            markdownSha256: revisedDocument.markdownSha256,
          },
          diffSummary: {
            changedBlockIds: revised.diff.changedBlockIds,
            changedSections: revised.diff.sectionChanges.map((change) => change.section),
            affectedObjectIds: revised.diff.affectedObjectRefs.map((reference) => `${reference.kind}:${reference.objectId}`),
          },
        });
        persistPointer(input.pointerStorage, batchId, run.runId, command, undefined, {
          definition, decision: command.decision, actorUserId: command.actorUserId, decidedAt: now(),
        });
        return snapshot(run);
      }
      if (command.type === 'ASK_REVIEW_ASSISTANT') {
        const expectedRunRevision = command.expectedRevision;
        const turnId = `assistant-turn:${sha256HexSync(command.commandId).slice(0, 20)}`;
        if (pendingAssistantTurn && activeRun.revision !== command.expectedRevision) {
          let existingEvent: StandardizationTimelineEvent | undefined;
          for (const event of activeRun.timeline) {
            if (event.type !== 'ASSISTANT_TURN_RECORDED') continue;
            const raw = await input.standardizationRuns.readEventPayload(activeRun.runId, event.eventId);
            const candidate = JSON.parse(raw) as { turnId?: string };
            if (candidate.turnId === turnId) {
              existingEvent = event;
              break;
            }
          }
          if (existingEvent) {
            const raw = await input.standardizationRuns.readEventPayload(activeRun.runId, existingEvent.eventId);
            const existing = JSON.parse(raw) as ReviewAssistantTurnArtifact;
            const intent = classifyReviewAssistantIntent(command.message);
            if (existing.turnId !== turnId || existing.actorUserId !== command.actorUserId
              || existing.message !== intent.normalizedMessage
              || canonicalModelingJson(existing.selection) !== canonicalModelingJson(command.selection)
              || existing.createdAt !== pendingAssistantTurn.createdAt) {
              throw new Error('未完成助手提问与当前持久化Turn不一致');
            }
            persistPointer(input.pointerStorage, batchId, activeRun.runId, command);
            return snapshot(activeRun, {
              assistantTurnDelta: await readAssistantTurnDeltaForCommand(activeRun, command),
            });
          }
        }
        if (activeRun.revision !== expectedRunRevision) {
          throw new Error('标准化运行revision已变化，请刷新后重试');
        }
        const assistantCreatedAt = pendingAssistantTurn?.createdAt ?? now();
        const context = await loadRunContext(activeRun);
        assertAssistantSelectionAllowed(command.selection, activeRun, context);
        const intent = classifyReviewAssistantIntent(command.message);
        let response: ReviewAssistantTurnArtifact['response'];
        let proposalPayload: string | undefined;
        if (intent.kind === 'PROPOSE_BLOCK_CHANGE') {
          if (Object.values(pointer?.pendingAssistantApplies ?? {}).some((pending) => (
            pending.beforeDocumentId === command.selection.documentId
          ))) {
            throw new Error('当前文档有未完成的助手修改，请先恢复原确认');
          }
          if (activeRun.status !== 'REVIEWING_DOCUMENT') {
            throw new Error('只有当前待审阅来源文档可以生成结构化修改建议');
          }
          const currentStep = activeRun.sources.find((step) => step.status === 'DOCUMENT_READY');
          if (!currentStep?.documentId || !currentStep.documentRevision
            || command.selection.documentId !== currentStep.documentId
            || command.selection.sourceId !== currentStep.sourceId
            || !command.selection.blockId) {
            throw new Error('结构化修改建议必须明确选择当前文档中的一项');
          }
          const compilation = context.compilations.find((candidate) => candidate.sourceId === currentStep.sourceId);
          const block = compilation?.blocks.find((candidate) => candidate.blockId === command.selection.blockId);
          if (!compilation || !block) throw new Error('当前结构化块不存在');
          const change = intent.field === 'label'
            ? { blockId: block.blockId, label: intent.value }
            : {
                blockId: block.blockId,
                value: reviseEditableStructuredValue(block.value, intent.value),
              };
          const revised = story.reviseCompilation({
            compilation,
            priorCompilations: context.compilations.filter((candidate) => candidate.sourceId !== currentStep.sourceId),
            changes: [change],
          });
          const preview = previewFromDiff(revised.diff);
          const proposalCore = {
            runId: activeRun.runId,
            sourceId: currentStep.sourceId,
            documentId: currentStep.documentId,
            documentRevision: currentStep.documentRevision,
            blockId: block.blockId,
            change,
            beforeBlock: block,
            preview,
          };
          const proposal: AssistantStructuredChangeProposal = {
            schemaVersion: 1,
            proposalId: `assistant-proposal:${sha256HexSync(command.commandId).slice(0, 20)}`,
            ...proposalCore,
            previewSha256: `sha256:${sha256HexSync(canonicalModelingJson(proposalCore))}`,
            createdBy: command.actorUserId,
            createdAt: assistantCreatedAt,
          };
          proposalPayload = JSON.stringify(proposal);
          const proposalRef = `sha256:${sha256HexSync(proposalPayload)}` as const;
          response = {
            kind: 'PATCH_PREVIEW',
            title: `建议修改“${block.label}”`,
            body: ['已从当前持久化 revision 生成真实 Block、Assertion、章节与 Markdown 差异。只有点击确认按钮后才会应用。'],
            proposalId: proposal.proposalId,
            proposalRef,
          };
        } else {
          const answer = answerReviewAssistant({
            intent, selection: command.selection,
            knowledge: assistantKnowledge(activeRun, context, command.selection),
          });
          if (answer.kind === 'PATCH_PREVIEW') {
            throw new Error('非修改意图不能生成结构化Proposal');
          }
          response = answer;
        }
        const turn: ReviewAssistantTurnArtifact = {
          schemaVersion: 1,
          turnId,
          runId: activeRun.runId,
          actorUserId: command.actorUserId,
          message: intent.normalizedMessage,
          selection: structuredClone(command.selection),
          knowledgeSourceRevisions: sources.flatMap((source) => {
            const document = context.documents.get(source.sourceId);
            return document ? [{
              sourceId: source.sourceId,
              documentId: document.documentId,
              documentRevision: document.revision,
            }] : [];
          }),
          response,
          createdAt: assistantCreatedAt,
        };
        if (!pendingAssistantTurn) {
          persistPendingAssistantTurn(
            input.pointerStorage, batchId, activeRun.runId, command, assistantCreatedAt,
          );
        }
        const run = await input.standardizationRuns.execute({
          type: 'RECORD_ASSISTANT_TURN',
          commandId: `${storyId}:${command.commandId}:assistant-turn`,
          expectedRevision: expectedRunRevision,
          actor: { userId: command.actorUserId },
          runId: activeRun.runId,
          payload: JSON.stringify(turn),
          ...(proposalPayload ? { proposalPayload } : {}),
        });
        const assistantTurnDelta = await readAssistantTurnDeltaForCommand(run, command);
        persistPointer(input.pointerStorage, batchId, run.runId, command);
        return snapshot(run, { assistantTurnDelta });
      }
      if (command.type === 'CANCEL_ASSISTANT_PATCH') {
        const cancellationId = `assistant-cancellation:${sha256HexSync(command.commandId).slice(0, 20)}`;
        if (activeRun.revision !== command.expectedRevision) {
          for (const event of activeRun.timeline) {
            if (event.type !== 'ASSISTANT_PATCH_CANCELLED') continue;
            const raw = await input.standardizationRuns.readEventPayload(activeRun.runId, event.eventId);
            const cancellation = JSON.parse(raw) as {
              cancellationId?: string; proposalId?: string; cancelledBy?: string;
            };
            if (cancellation.cancellationId === cancellationId) {
              if (cancellation.proposalId !== command.proposalId
                || cancellation.cancelledBy !== command.actorUserId) {
                throw new Error('助手Proposal取消恢复与已存事件不一致');
              }
              persistPointer(input.pointerStorage, batchId, activeRun.runId, command);
              return snapshot(activeRun);
            }
          }
          throw new Error('标准化运行revision已变化，请刷新后重试');
        }
        const projection = await readAssistantProposalProjection(activeRun);
        const entry = projection.proposals.get(command.proposalId);
        const currentStep = activeRun.sources.find((step) => step.status === 'DOCUMENT_READY');
        if (!entry || projection.confirmed.has(command.proposalId)
          || projection.cancelled.has(command.proposalId)
          || !currentStep?.documentId || !currentStep.documentRevision
          || projection.latestByDocument.get(currentStep.documentId) !== command.proposalId
          || entry.proposal.documentId !== currentStep.documentId
          || entry.proposal.documentRevision !== currentStep.documentRevision) {
          throw new Error('只能取消当前文档最新一份未决定Proposal');
        }
        const cancellation = {
          schemaVersion: 1 as const,
          cancellationId,
          runId: activeRun.runId,
          proposalId: entry.proposal.proposalId,
          documentId: entry.proposal.documentId,
          documentRevision: entry.proposal.documentRevision,
          cancelledBy: command.actorUserId,
          cancelledAt: now(),
        };
        const run = await input.standardizationRuns.execute({
          type: 'CANCEL_ASSISTANT_PATCH',
          commandId: `${storyId}:${command.commandId}:cancel-assistant-patch`,
          expectedRevision: command.expectedRevision,
          actor: { userId: command.actorUserId },
          runId: activeRun.runId,
          proposalId: entry.proposal.proposalId,
          documentId: entry.proposal.documentId,
          documentRevision: entry.proposal.documentRevision,
          payload: JSON.stringify(cancellation),
        });
        persistPointer(input.pointerStorage, batchId, run.runId, command);
        return snapshot(run);
      }
      if (command.type === 'CONFIRM_ASSISTANT_PATCH') {
        let expectedRunRevision = command.expectedRevision;
        if (pendingAssistantApply && activeRun.revision !== command.expectedRevision) {
          let confirmation: {
            proposalId?: string; beforeDocumentId?: string; documentId?: string; blockId?: string;
          } | undefined;
          for (const event of [...activeRun.timeline].reverse()) {
            if (event.type !== 'ASSISTANT_PATCH_CONFIRMED') continue;
            const raw = await input.standardizationRuns.readEventPayload(activeRun.runId, event.eventId);
            const candidate = JSON.parse(raw) as typeof confirmation;
            if (candidate?.proposalId === command.proposalId) {
              confirmation = candidate;
              break;
            }
          }
          if (confirmation) {
            if (confirmation.proposalId !== command.proposalId
              || confirmation.beforeDocumentId !== pendingAssistantApply.beforeDocumentId
              || confirmation.documentId !== pendingAssistantApply.afterDocumentId
              || activeRun.sources.every((source) => source.documentId !== pendingAssistantApply.afterDocumentId)) {
              throw new Error('未完成助手修改与当前持久化事件不一致');
            }
            persistPointer(input.pointerStorage, batchId, activeRun.runId, command);
            const recovered = await snapshot(activeRun);
            const document = recovered.current?.document;
            if (document && typeof confirmation.blockId === 'string') {
              const block = recovered.current?.compilation?.blocks.find((candidate) => (
                candidate.blockId === confirmation.blockId
              ));
              recovered.assistantFocusTarget = {
                kind: 'SOURCE_DOCUMENT', documentId: document.documentId,
                ...(block ? { section: block.section, blockId: block.blockId } : {}),
              };
            }
            return recovered;
          }
          expectedRunRevision = activeRun.revision;
        }
        if (activeRun.revision !== expectedRunRevision) {
          throw new Error('标准化运行revision已变化，请刷新后重试');
        }
        const context = await loadRunContext(activeRun);
        const projection = await readAssistantProposalProjection(activeRun);
        const entry = projection.proposals.get(command.proposalId);
        if (!entry || projection.confirmed.has(command.proposalId)
          || projection.cancelled.has(command.proposalId)
          || projection.latestByDocument.get(entry.proposal.documentId) !== command.proposalId) {
          throw new Error('只能确认当前文档最新一份未确认Proposal');
        }
        const proposal = entry.proposal;
        const currentStep = activeRun.sources.find((step) => step.status === 'DOCUMENT_READY');
        if (activeRun.status !== 'REVIEWING_DOCUMENT' || !currentStep?.documentId
          || !currentStep.documentRevision || proposal.sourceId !== currentStep.sourceId
          || proposal.documentId !== currentStep.documentId
          || proposal.documentRevision !== currentStep.documentRevision) {
          throw new Error('助手Proposal对应的来源文档revision已变化');
        }
        const compilation = context.compilations.find((candidate) => candidate.sourceId === currentStep.sourceId);
        const beforeBlock = compilation?.blocks.find((candidate) => candidate.blockId === proposal.blockId);
        if (!compilation || !beforeBlock
          || canonicalModelingJson(beforeBlock) !== canonicalModelingJson(proposal.beforeBlock)) {
          throw new Error('助手Proposal对应的结构化块已变化');
        }
        const revised = story.reviseCompilation({
          compilation,
          priorCompilations: context.compilations.filter((candidate) => candidate.sourceId !== currentStep.sourceId),
          changes: [proposal.change],
        });
        const preview = previewFromDiff(revised.diff);
        const proposalCore = {
          runId: activeRun.runId, sourceId: currentStep.sourceId,
          documentId: currentStep.documentId, documentRevision: currentStep.documentRevision,
          blockId: beforeBlock.blockId, change: proposal.change, beforeBlock, preview,
        };
        const recomputedPreviewSha256 = `sha256:${sha256HexSync(canonicalModelingJson(proposalCore))}` as const;
        if (proposal.previewSha256 !== recomputedPreviewSha256
          || command.expectedPreviewSha256 !== recomputedPreviewSha256
          || canonicalModelingJson(proposal.preview) !== canonicalModelingJson(preview)) {
          throw new Error('助手Proposal预览已变化，请重新生成建议');
        }
        assertCurrentDocumentRevisionUnlocked(currentStep);
        let revisedDocument: SourceModelingDocument;
        if (pendingAssistantApply) {
          const existing = await input.sourceDocuments.read(pendingAssistantApply.afterDocumentId);
          if (!existing || existing.derivedFromDocumentId !== currentStep.documentId) {
            throw new Error('未完成助手修改引用的来源文档不存在或血缘不一致');
          }
          const blocks = await input.sourceDocuments.readBlocks(existing.documentId);
          if (canonicalModelingJson(blocks) !== canonicalModelingJson(revised.compilation.blocks)) {
            throw new Error('未完成助手修改的来源文档内容与Proposal不一致');
          }
          revisedDocument = existing;
        } else {
          revisedDocument = await input.sourceDocuments.revise({
            documentId: currentStep.documentId,
            expectedRevision: currentStep.documentRevision,
            sections: revised.compilation.sections,
            assertions: revised.compilation.assertions,
            blocks: revised.compilation.blocks,
            actorUserId: command.actorUserId,
          });
        }
        const confirmedAt = pendingAssistantApply?.confirmedAt ?? now();
        if (!pendingAssistantApply) {
          persistPendingAssistantApply(input.pointerStorage, batchId, activeRun.runId, command, {
            beforeDocumentId: currentStep.documentId,
            afterDocumentId: revisedDocument.documentId,
            confirmedAt,
          });
        }
        const confirmation = {
          schemaVersion: 1 as const,
          runId: activeRun.runId, sourceId: currentStep.sourceId,
          proposalId: proposal.proposalId, proposalRef: entry.proposalRef,
          expectedPreviewSha256: recomputedPreviewSha256,
          beforeDocumentId: currentStep.documentId,
          beforeDocumentRevision: currentStep.documentRevision,
          documentId: revisedDocument.documentId,
          documentRevision: revisedDocument.revision,
          blockId: proposal.blockId,
          actorUserId: command.actorUserId,
          confirmedAt,
        };
        const run = await input.standardizationRuns.execute({
          type: 'CONFIRM_ASSISTANT_PATCH',
          commandId: `${storyId}:${command.commandId}:confirm-assistant-patch`,
          expectedRevision: expectedRunRevision,
          actor: { userId: command.actorUserId },
          runId: activeRun.runId,
          sourceId: currentStep.sourceId,
          proposalId: proposal.proposalId,
          expectedPreviewSha256: recomputedPreviewSha256,
          beforeDocumentId: currentStep.documentId,
          beforeDocumentRevision: currentStep.documentRevision,
          documentId: revisedDocument.documentId,
          documentRevision: revisedDocument.revision,
          introducedConflictIds: revised.compilation.introducedConflictIds,
          documentContent: {
            sectionsRef: revisedDocument.sectionsRef,
            assertionsRef: revisedDocument.assertionsRef,
            blocksRef: revisedDocument.blocksRef!,
            markdownRef: revisedDocument.markdownRef,
            markdownSha256: revisedDocument.markdownSha256,
          },
          diffSummary: {
            changedBlockIds: revised.diff.changedBlockIds,
            changedSections: revised.diff.sectionChanges.map((change) => change.section),
            affectedObjectIds: revised.diff.affectedObjectRefs.map((reference) => (
              `${reference.kind}:${reference.objectId}`
            )),
          },
          payload: JSON.stringify(confirmation),
        });
        persistPointer(input.pointerStorage, batchId, run.runId, command);
        const result = await snapshot(run);
        result.assistantFocusTarget = {
          kind: 'SOURCE_DOCUMENT', documentId: revisedDocument.documentId,
          section: beforeBlock.section, blockId: beforeBlock.blockId,
        };
        return result;
      }
      if (command.type === 'READ_NEXT_SOURCE') {
        // A preparation failure happens after the durable START_NEXT_SOURCE
        // receipt. Resume that exact READING step rather than trying to skip
        // ahead to a later PENDING source; registering the same frozen
        // snapshot is idempotent in the source-document runtime.
        const existingReadingStep = activeRun.sources.find((step) => step.status === 'READING');
        const pendingSource = existingReadingStep ?? activeRun.sources.find((step) => step.status === 'PENDING');
        if (!pendingSource) throw new Error('没有待读取的来源');
        let activeStage: SourcePreparationStage = 'READ';
        await reportSourcePreparation(pendingSource.sourceId, activeStage, 'ACTIVE');
        try {
          const readingRun = existingReadingStep
            ? activeRun
            : await input.standardizationRuns.execute({
                type: 'START_NEXT_SOURCE',
                commandId: `${storyId}:${command.commandId}:start-source`,
                expectedRevision: command.expectedRevision,
                actor: { userId: command.actorUserId },
                runId: activeRun.runId,
              });
          const readingStep = readingRun.sources.find((step) => step.status === 'READING');
          if (!readingStep) throw new Error('标准化运行没有正在读取的来源');
          const priorCompilations = (await loadRunContext(readingRun)).compilations;
          const compilation = story.compileSource({
            sourceId: readingStep.sourceId,
            priorCompilations,
          });
          // `compileSource` is the frozen-material boundary: it selects the
          // immutable source snapshot and validates it against the admitted
          // prior prefix. Do not paint READ green until that exact material is
          // present and its identity has been checked.
          const expectedSource = sources.find((source) => source.sourceId === readingStep.sourceId);
          if (!expectedSource
            || (readingStep.snapshotId !== undefined && readingStep.snapshotId !== expectedSource.snapshotId)
            || compilation.sourceId !== expectedSource.sourceId
            || compilation.snapshotId !== expectedSource.snapshotId
            || compilation.sourceName !== expectedSource.sourceName) {
            throw new Error(`固定来源快照身份不匹配：${readingStep.sourceId}`);
          }
          await reportSourcePreparation(readingStep.sourceId, activeStage, 'COMPLETE');
          activeStage = 'ANALYZE';
          await reportSourcePreparation(readingStep.sourceId, activeStage, 'ACTIVE');
          await reportSourcePreparation(readingStep.sourceId, activeStage, 'COMPLETE');
          activeStage = 'ORGANIZE';
          await reportSourcePreparation(readingStep.sourceId, activeStage, 'ACTIVE');
          const document = await input.sourceDocuments.register({
          projectId,
          documentCode: `guanyijia-five-source--${compilation.sourceId}`,
          sourceSnapshotId: compilation.snapshotId,
          sourceType: compilation.sourceClass,
          sourceName: compilation.sourceName,
          sections: compilation.sections,
          assertions: compilation.assertions,
          blocks: compilation.blocks,
          validation: {
            errors: [],
            warnings: compilation.readSummary.warnings.map((warning, index) => ({
              code: `SOURCE_WARNING_${index + 1}`,
              message: warning,
            })),
            gaps: compilation.blocks.filter((block) => block.evidenceStatus === 'GAP').map((block) => ({
              code: block.stableCode,
              message: block.label,
              refs: [...block.evidenceRefs],
            })),
          },
          actorUserId: command.actorUserId,
          });
          await reportSourcePreparation(readingStep.sourceId, activeStage, 'COMPLETE');
          const payload = JSON.stringify({
          documentId: document.documentId,
          documentRevision: document.revision,
          documentContent: {
            sectionsRef: document.sectionsRef,
            assertionsRef: document.assertionsRef,
            blocksRef: document.blocksRef,
            markdownRef: document.markdownRef,
            markdownSha256: document.markdownSha256,
          },
          title: `${compilation.sourceName}来源文档`,
          sectionCount: Object.keys(compilation.sections).length,
          blockCounts: Object.fromEntries(['FACT', 'INFERENCE', 'GAP', 'CONFLICT'].map((status) => [
            status,
            compilation.blocks.filter((block) => block.evidenceStatus === status).length,
          ])),
          });
          const run = await input.standardizationRuns.execute({
          type: 'COMPLETE_SOURCE_DOCUMENT',
          commandId: `${storyId}:${command.commandId}:complete-source`,
          expectedRevision: readingRun.revision,
          actor: { userId: command.actorUserId },
          runId: readingRun.runId,
          sourceId: compilation.sourceId,
          readSummary: {
            summary: compilation.readSummary.summary,
            objectCount: compilation.readSummary.objectCount,
            evidenceCount: compilation.readSummary.evidenceCount,
          },
          documentId: document.documentId,
          documentRevision: document.revision,
          introducedConflictIds: compilation.introducedConflictIds,
          corroboratedConflictIds: compilation.corroboratedConflictIds.filter((conflictId) => (
            readingRun.sources.some((step) => step.introducedConflictIds.includes(conflictId)
              && step.resolvedConflictIds.includes(conflictId))
          )),
          payload,
          });
          persistPointer(input.pointerStorage, batchId, run.runId, command);
          return snapshot(run);
        } catch (cause) {
          await reportSourcePreparation(pendingSource.sourceId, activeStage, 'ERROR');
          throw cause;
        }
      }
      if (command.type === 'RESOLVE_CURRENT_CONFLICT') {
        if (pendingResolution && activeRun.revision !== command.expectedRevision) {
          const context = await loadRunContext(activeRun);
          const saved = context.resolutions.find((artifact) => artifact.hunk.conflictId === command.conflictId);
          if (!saved || saved.strategy !== command.strategy || saved.reason !== command.reason.trim()
            || saved.actorUserId !== command.actorUserId || saved.decidedAt !== pendingResolution.decidedAt
            || saved.hunk.hunkSha256 !== command.expectedHunkSha256) {
            throw new Error('未完成冲突决定与当前持久化事件不一致');
          }
          const recovered = await input.standardizationRuns.execute({
            type: 'RESOLVE_SOURCE_CONFLICT',
            commandId: `${storyId}:${command.commandId}:resolve-conflict`,
            expectedRevision: command.expectedRevision,
            actor: { userId: command.actorUserId },
            runId: activeRun.runId,
            sourceId: saved.sourceId,
            conflictId: command.conflictId,
            strategy: command.strategy,
            reason: saved.reason,
            expectedHunkSha256: command.expectedHunkSha256,
            payload: JSON.stringify(saved),
          });
          persistPointer(input.pointerStorage, batchId, recovered.runId, command);
          return snapshot(recovered);
        }
        if (activeRun.revision !== command.expectedRevision) {
          throw new Error('标准化运行revision已变化，请刷新后重试');
        }
        const unresolvedConflict = selectFirstUnresolvedConflict(activeRun);
        if (!unresolvedConflict || !canResolveFirstUnresolvedConflict(activeRun, unresolvedConflict)) {
          throw new Error('当前没有待解决的来源冲突');
        }
        if (unresolvedConflict.conflictId !== command.conflictId) {
          throw new Error('只能应用当前第一项未解决冲突');
        }
        const source = sources.find((candidate) => candidate.sourceId === unresolvedConflict.sourceStep.sourceId);
        const context = await loadRunContext(activeRun);
        const compilation = context.compilations.find((candidate) => (
          candidate.sourceId === unresolvedConflict.sourceStep.sourceId
        ));
        const contentBinding = contentBindingForRun(activeRun, pointer);
        if (!source || !compilation) throw new Error('当前冲突来源的文档上下文不存在');
        const hasPendingScriptedReview = scriptedReviewStatesFor({
          run: activeRun,
          source,
          compilation,
          contentBinding,
          pointer,
        }).some((state) => state.status === 'PENDING');
        if (hasPendingScriptedReview) {
          throw new Error('请先核对本来源的建议修改');
        }
        const preview = await this.previewCurrentConflict({
          runId: activeRun.runId,
          conflictId: command.conflictId,
          strategy: command.strategy,
        });
        if (preview.hunk.hunkSha256 !== command.expectedHunkSha256) {
          throw new Error('冲突Hunk已变化，请刷新后重试');
        }
        const decidedAt = pendingResolution?.decidedAt ?? now();
        if (!pendingResolution) {
          persistPendingResolution(
            input.pointerStorage, batchId, activeRun.runId, command, decidedAt,
          );
        }
        const artifact: ConflictResolutionArtifact = {
          schemaVersion: 1,
          resolutionId: `resolution:${activeRun.runId}:${command.conflictId}`,
          runId: activeRun.runId,
          sourceId: unresolvedConflict.sourceStep.sourceId,
          reason: command.reason.trim(),
          actorUserId: command.actorUserId,
          decidedAt,
          ...preview,
        };
        const run = await input.standardizationRuns.execute({
          type: 'RESOLVE_SOURCE_CONFLICT',
          commandId: `${storyId}:${command.commandId}:resolve-conflict`,
          expectedRevision: command.expectedRevision,
          actor: { userId: command.actorUserId },
          runId: activeRun.runId,
          sourceId: unresolvedConflict.sourceStep.sourceId,
          conflictId: command.conflictId,
          strategy: command.strategy,
          reason: artifact.reason,
          expectedHunkSha256: command.expectedHunkSha256,
          payload: JSON.stringify(artifact),
        });
        persistPointer(input.pointerStorage, batchId, run.runId, command);
        return snapshot(run);
      }
      if (command.type === 'REPLACE_RESOLVED_CONFLICT') {
        if (activeRun.revision !== command.expectedRevision) {
          throw new Error('标准化运行revision已变化，请刷新后重试');
        }
        if (activeRun.status !== 'READY_FOR_OUTPUT' || activeRun.reviewId) {
          throw new Error('只能在结果定版前重新处理已保存的来源差异');
        }
        const context = await loadRunContext(activeRun);
        const prior = context.resolutions.find((artifact) => artifact.hunk.conflictId === command.conflictId);
        if (!prior) throw new Error('要重新处理的来源差异不存在或尚未决定');
        const definition = story.listConflictDefinitions().find((candidate) => (
          candidate.conflictId === command.conflictId
        ));
        if (!definition) throw new Error('要重新处理的来源差异定义不存在');
        const introducedIndex = sources.findIndex((source) => source.sourceId === definition.introducedBySourceId);
        const preview = story.previewConflictResolution({
          conflictId: command.conflictId,
          strategy: command.strategy,
          sourceRevisions: context.compilations.slice(0, introducedIndex + 1).map((compilation) => {
            const document = context.documents.get(compilation.sourceId);
            if (!document) throw new Error(`来源差异缺少持久化来源revision：${compilation.sourceId}`);
            return { documentId: document.documentId, documentRevision: document.revision, compilation };
          }),
        });
        if (preview.hunk.hunkSha256 !== command.expectedHunkSha256) {
          throw new Error('冲突Hunk已变化，请刷新后重试');
        }
        const artifact: ConflictResolutionArtifact = {
          schemaVersion: 1,
          resolutionId: `replacement:${activeRun.runId}:${command.conflictId}:${activeRun.timeline.length + (activeRun.deliverableId ? 2 : 1)}`,
          runId: activeRun.runId,
          sourceId: prior.sourceId,
          reason: command.reason.trim(),
          actorUserId: command.actorUserId,
          decidedAt: now(),
          ...preview,
        };
        const run = await input.standardizationRuns.execute({
          type: 'REPLACE_SOURCE_CONFLICT_DECISION',
          commandId: `${storyId}:${command.commandId}:replace-conflict`,
          expectedRevision: command.expectedRevision,
          actor: { userId: command.actorUserId },
          runId: activeRun.runId,
          sourceId: prior.sourceId,
          conflictId: command.conflictId,
          strategy: command.strategy,
          reason: artifact.reason,
          expectedHunkSha256: command.expectedHunkSha256,
          payload: JSON.stringify(artifact),
        });
        persistPointer(input.pointerStorage, batchId, run.runId, command);
        return snapshot(run);
      }
      if (activeRun.revision !== command.expectedRevision) {
        throw new Error('标准化运行revision已变化，请刷新后重试');
      }
      const currentStep = activeRun.sources.find((step) => step.status === 'DOCUMENT_READY');
      if (!currentStep?.documentId || !currentStep.documentRevision) {
        throw new Error('当前没有待审阅的来源文档');
      }
      assertRenderedDocumentIsCurrent(command, currentStep);
      if (command.type === 'PREVIEW_CURRENT_BLOCK_CHANGE'
        || command.type === 'APPLY_CURRENT_BLOCK_CHANGE') {
        const context = await loadRunContext(activeRun);
        if (context.legacyDocuments.has(currentStep.sourceId)) {
          throw new Error('旧版来源文档没有结构化块，只能只读');
        }
        const compilation = context.compilations.find((candidate) => candidate.sourceId === currentStep.sourceId);
        if (!compilation) throw new Error('当前来源文档编译不存在');
        const revised = story.reviseCompilation({
          compilation,
          priorCompilations: context.compilations.filter((candidate) => candidate.sourceId !== currentStep.sourceId),
          changes: [command.change],
        });
        if (command.type === 'PREVIEW_CURRENT_BLOCK_CHANGE') {
          const result = await snapshot(activeRun);
          result.preview = previewFromDiff(revised.diff);
          return result;
        }

        assertCurrentDocumentRevisionUnlocked(currentStep);
        let revisedDocument: SourceModelingDocument | null = null;
        if (pendingApply) {
          if (pendingApply.beforeDocumentId !== currentStep.documentId) {
            throw new Error('未完成修改对应的来源文档revision已变化');
          }
          revisedDocument = await input.sourceDocuments.read(pendingApply.afterDocumentId);
          if (!revisedDocument || revisedDocument.derivedFromDocumentId !== currentStep.documentId) {
            throw new Error('未完成修改引用的来源文档不存在或血缘不一致');
          }
          const [blocks, sections, assertions] = await Promise.all([
            input.sourceDocuments.readBlocks(revisedDocument.documentId),
            input.sourceDocuments.readSections(revisedDocument.documentId),
            input.sourceDocuments.readAssertions(revisedDocument.documentId),
          ]);
          if (canonicalModelingJson(blocks) !== canonicalModelingJson(revised.compilation.blocks)
            || canonicalModelingJson(sections) !== canonicalModelingJson(revised.compilation.sections)
            || canonicalModelingJson(assertions) !== canonicalModelingJson(revised.compilation.assertions)) {
            throw new Error('未完成修改的来源文档内容与当前命令不一致');
          }
        } else {
          revisedDocument = await input.sourceDocuments.revise({
            documentId: currentStep.documentId,
            expectedRevision: currentStep.documentRevision,
            sections: revised.compilation.sections,
            assertions: revised.compilation.assertions,
            blocks: revised.compilation.blocks,
            actorUserId: command.actorUserId,
          });
          persistPendingApply(
            input.pointerStorage, batchId, activeRun.runId, command,
            currentStep.documentId, revisedDocument.documentId,
          );
        }
        const run = await input.standardizationRuns.execute({
          type: 'REVISE_SOURCE_DOCUMENT',
          commandId: `${storyId}:${command.commandId}:revise-source-document`,
          expectedRevision: command.expectedRevision,
          actor: { userId: command.actorUserId },
          runId: activeRun.runId,
          sourceId: currentStep.sourceId,
          documentId: revisedDocument.documentId,
          documentRevision: revisedDocument.revision,
          introducedConflictIds: revised.compilation.introducedConflictIds,
          documentContent: {
            sectionsRef: revisedDocument.sectionsRef,
            assertionsRef: revisedDocument.assertionsRef,
            blocksRef: revisedDocument.blocksRef!,
            markdownRef: revisedDocument.markdownRef,
            markdownSha256: revisedDocument.markdownSha256,
          },
          diffSummary: {
            changedBlockIds: revised.diff.changedBlockIds,
            changedSections: revised.diff.sectionChanges.map((change) => change.section),
            affectedObjectIds: revised.diff.affectedObjectRefs.map((reference) => (
              `${reference.kind}:${reference.objectId}`
            )),
          },
        });
        persistPointer(input.pointerStorage, batchId, run.runId, command);
        return snapshot(run);
      }
      const document = await input.sourceDocuments.read(currentStep.documentId);
      if (!document) throw new Error(`标准化运行引用的来源文档不存在：${currentStep.documentId}`);
      if (!document.blocksRef) throw new Error('旧版来源文档没有结构化块，只能只读');
      if (document.revision !== currentStep.documentRevision) {
        throw new Error('来源文档revision与标准化运行不一致');
      }
      if (document.status === 'NEEDS_REVIEW') {
        await input.sourceDocuments.markReady({
          documentId: document.documentId,
          expectedRevision: document.revision,
          actorUserId: command.actorUserId,
        });
      } else if (document.status !== 'READY_FOR_ALIGNMENT') {
        throw new Error(`当前来源文档状态不能完成审阅：${document.status}`);
      }
      const run = await input.standardizationRuns.execute({
        type: 'MARK_DOCUMENT_REVIEWED',
        commandId: `${storyId}:${command.commandId}:review-document`,
        expectedRevision: command.expectedRevision,
        actor: { userId: command.actorUserId },
        runId: activeRun.runId,
        sourceId: currentStep.sourceId,
      });
      persistPointer(input.pointerStorage, batchId, run.runId, command);
      return snapshot(run);
    },
  };
}

export function eventSource(
  event: StandardizationTimelineEvent,
  sources: StandardizationSourceStep[],
) {
  return event.sourceId ? sources.find((source) => source.sourceId === event.sourceId) : undefined;
}
