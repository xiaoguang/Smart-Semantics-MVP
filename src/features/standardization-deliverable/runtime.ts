import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  presentReviewSupplementText,
  projectReviewSupplementSegments,
  type ReviewSupplementMatter,
} from '../data-standardization/standardized-document-reading.ts';
import type { ConflictResolutionArtifact } from '../guanyijia-standardization-story/types.ts';
import {
  assertModelingDocumentIntegrity,
  canonicalModelingJson,
  modelingDocumentSemanticPayloadSha256,
  standardSectionOrder,
} from '../modeling-document-bridge/standard-markdown.ts';
import type { ModelingDocumentSection } from '../modeling-document-bridge/types.ts';
import type { StructuredModelingAssertion } from '../modeling-document-bridge/types.ts';
import {
  assertStructuredSourceDocumentProjection,
  renderSourceDocumentMarkdown,
} from '../source-documents/structured-projection.ts';
import type {
  ContentReference,
  SourceDocumentBlock,
  SourceDocumentSections,
  SourceModelingDocument,
} from '../source-documents/types.ts';
import type { StandardizationRun } from '../standardization-run/types.ts';
import type {
  DeliverableCommand,
  GovernanceEvidenceAppendix,
  MergedStandardizationDocument,
  ProtectedBaseline,
  ResolutionDecisionManifest,
  SourceCollectionManifest,
  StandardizationDeliverable,
  StandardizationDeliverablePreview,
  StandardizationDeliverableRuntime,
  StandardizationDeliverableRuntimeInput,
  StandardizationDocumentHandoffReceipt,
  StandardizationModelingHandoffReceipt,
  ZeroDeltaModelingHandoffReceipt,
  ZeroDeltaReport,
} from './types.ts';
import type { StandardizationDeliverableMode } from './types.ts';
import {
  assertModelingEligibilityProjection,
  projectModelingEligibility,
  type StandardizationModelingEligibilityProjection,
} from './modeling-eligibility.ts';

type CommandExecution = {
  fingerprint: ContentReference;
  runId: string;
  deliverableId: string;
  type: DeliverableCommand['type'];
};

type StoredState = {
  schemaVersion: 1;
  deliverables: StandardizationDeliverable[];
  commands: Record<string, CommandExecution>;
};

type LoadedSourceEntry = {
  source: StandardizationRun['sources'][number];
  document: SourceModelingDocument;
  sections: SourceDocumentSections;
  assertions: StructuredModelingAssertion[];
  blocks: SourceDocumentBlock[];
  markdown: string;
};

type PreviewBuild = {
  preview: StandardizationDeliverablePreview;
  baseline: ProtectedBaseline;
  resolutions: ConflictResolutionArtifact[];
  sourceEntries: LoadedSourceEntry[];
  governanceAppendix: GovernanceEvidenceAppendix;
};

const emptyState = (): StoredState => ({
  schemaVersion: 1, deliverables: [], commands: Object.create(null) as StoredState['commands'],
});
const clone = <T,>(value: T): T => structuredClone(value);
const asRef = (content: string) => `sha256:${sha256HexSync(content)}` as ContentReference;
const refPattern = /^sha256:[0-9a-f]{64}$/;

function utf8Chunks(content: string, byteLimit: number) {
  const bytes = new TextEncoder().encode(content);
  const decoder = new TextDecoder('utf-8', { fatal: true });
  const chunks: string[] = [];
  let offset = 0;
  while (offset < bytes.byteLength) {
    let end = Math.min(offset + byteLimit, bytes.byteLength);
    while (end < bytes.byteLength && (bytes[end]! & 0xc0) === 0x80) end -= 1;
    if (end <= offset) throw new Error('交付内容包含无法按UTF-8分页的字符');
    chunks.push(decoder.decode(bytes.subarray(offset, end)));
    offset = end;
  }
  return chunks;
}

function jsonValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(jsonValue);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .filter(([, child]) => child !== undefined)
      .map(([key, child]) => [key, jsonValue(child)]));
  }
  return value;
}

function parseState(raw: string | null): StoredState {
  if (raw === null) return emptyState();
  let parsed: unknown;
  try { parsed = JSON.parse(raw); } catch { throw new Error('交付物 metadata 无法解析'); }
  if (!parsed || typeof parsed !== 'object') throw new Error('交付物 metadata 必须是对象');
  const candidate = parsed as Partial<StoredState>;
  if (candidate.schemaVersion !== 1 || !Array.isArray(candidate.deliverables)
    || !candidate.commands || typeof candidate.commands !== 'object') {
    throw new Error('交付物 metadata 结构无效');
  }
  const ids = new Set<string>();
  const runIds = new Set<string>();
  const statuses = new Set(['GENERATED_AWAITING_AUTHOR', 'AWAITING_INDEPENDENT_REVIEW', 'FROZEN', 'HANDED_OFF']);
  const linkStates = new Set(['LINKED', 'GENERATED_EVENT_PENDING', 'REVIEW_EVENT_PENDING', 'FROZEN_EVENT_PENDING', 'HANDOFF_EVENT_PENDING']);
  const modes = new Set<StandardizationDeliverableMode>(['SOURCE_DOCUMENT_SET', 'MERGED_DOCUMENT']);
  for (const deliverable of candidate.deliverables) {
    if (!deliverable || typeof deliverable !== 'object'
      || deliverable.schemaVersion !== 1 || !deliverable.deliverableId?.trim()
      || !deliverable.runId?.trim() || !deliverable.scenarioKey?.trim()
      || !deliverable.projectId?.trim() || ids.has(deliverable.deliverableId)
      || !Number.isSafeInteger(deliverable.revision)
      || deliverable.revision < 1 || !refPattern.test(deliverable.coreSha256)
      || !statuses.has(deliverable.status) || !linkStates.has(deliverable.linkState)
      || !deliverable.authorUserId?.trim() || !deliverable.createdAt?.trim() || !deliverable.updatedAt?.trim()
      || [deliverable.sourceManifestRef, deliverable.mergedDocumentRef, deliverable.decisionManifestRef,
        deliverable.governanceAppendixRef, deliverable.modelingArtifactRef, deliverable.zeroDeltaReportRef]
        .some((ref) => !refPattern.test(ref))) {
      throw new Error('交付物 metadata identity 无效');
    }
    // Metadata written before the mode contract existed is still readable as
    // the historical merged-document presentation. New writes always persist
    // the explicit mode selected in the production workbench.
    if (!deliverable.mode) deliverable.mode = 'MERGED_DOCUMENT';
    if (!modes.has(deliverable.mode)) throw new Error('交付物 metadata 展示模式无效');
    if (deliverable.modelingEligibilityRef !== undefined && !refPattern.test(deliverable.modelingEligibilityRef)) {
      throw new Error('交付物可建模结论引用无效');
    }
    const allowedLinks = deliverable.status === 'GENERATED_AWAITING_AUTHOR'
      ? ['LINKED', 'GENERATED_EVENT_PENDING']
      : deliverable.status === 'AWAITING_INDEPENDENT_REVIEW'
        ? ['LINKED', 'REVIEW_EVENT_PENDING']
        : deliverable.status === 'FROZEN'
        ? ['LINKED', 'FROZEN_EVENT_PENDING']
          : ['LINKED', 'HANDOFF_EVENT_PENDING'];
    const hasAuthorConfirmation = deliverable.authorConfirmation !== undefined;
    const hasAuthorFreezeConfirmation = deliverable.authorFreezeConfirmation !== undefined;
    const hasReviewerApproval = deliverable.reviewerApproval !== undefined;
    const hasHandoffReceipt = deliverable.handoffReceiptRef !== undefined;
    const exactLifecycleFields = deliverable.status === 'GENERATED_AWAITING_AUTHOR'
      ? !hasAuthorConfirmation && !hasAuthorFreezeConfirmation && !hasReviewerApproval && !hasHandoffReceipt
      : deliverable.status === 'AWAITING_INDEPENDENT_REVIEW'
        ? hasAuthorConfirmation && !hasAuthorFreezeConfirmation && !hasReviewerApproval && !hasHandoffReceipt
        : deliverable.status === 'FROZEN'
          ? !hasHandoffReceipt && ((hasAuthorConfirmation && hasReviewerApproval && !hasAuthorFreezeConfirmation)
            || (!hasReviewerApproval && hasAuthorFreezeConfirmation))
          : hasHandoffReceipt && ((hasAuthorConfirmation && hasReviewerApproval && !hasAuthorFreezeConfirmation)
            || (!hasReviewerApproval && hasAuthorFreezeConfirmation));
    if (!allowedLinks.includes(deliverable.linkState)
      || !exactLifecycleFields
      || (hasHandoffReceipt && !refPattern.test(deliverable.handoffReceiptRef!))) {
      throw new Error('交付物 metadata 状态组合无效');
    }
    if (deliverable.authorConfirmation
      && (!deliverable.authorConfirmation.reviewId?.trim()
        || deliverable.authorConfirmation.actorUserId !== deliverable.authorUserId
        || !deliverable.authorConfirmation.confirmedAt?.trim()
        || deliverable.authorConfirmation.coreSha256 !== deliverable.coreSha256)) {
      throw new Error('交付物作者确认没有绑定完整core');
    }
    if (deliverable.reviewerApproval
      && (!deliverable.authorConfirmation
        || deliverable.reviewerApproval.reviewId !== deliverable.authorConfirmation.reviewId
        || deliverable.reviewerApproval.actorUserId === deliverable.authorUserId
        || !deliverable.reviewerApproval.reviewedAt?.trim()
        || deliverable.reviewerApproval.coreSha256 !== deliverable.coreSha256)) {
      throw new Error('交付物独立审核没有绑定完整core');
    }
    if (deliverable.authorFreezeConfirmation
      && (!deliverable.authorFreezeConfirmation.confirmationId?.trim()
        || !deliverable.authorFreezeConfirmation.actorUserId?.trim()
        || !deliverable.authorFreezeConfirmation.confirmedAt?.trim()
        || deliverable.authorFreezeConfirmation.coreSha256 !== deliverable.coreSha256
        || !Number.isSafeInteger(deliverable.authorFreezeConfirmation.inputDeliverableRevision)
        || deliverable.authorFreezeConfirmation.inputDeliverableRevision < 1
        || deliverable.authorFreezeConfirmation.inputDeliverableRevision >= deliverable.revision)) {
      throw new Error('交付物作者定版确认没有绑定完整core或输入revision');
    }
    ids.add(deliverable.deliverableId);
    runIds.add(deliverable.runId);
  }
  const commands = Object.create(null) as StoredState['commands'];
  for (const [commandId, execution] of Object.entries(candidate.commands)) {
    if (!commandId.trim() || !execution || typeof execution !== 'object'
      || !refPattern.test(execution.fingerprint) || !runIds.has(execution.runId)
      || !ids.has(execution.deliverableId)) throw new Error('交付物 command 记录无效');
    commands[commandId] = execution;
  }
  return { schemaVersion: 1, deliverables: candidate.deliverables, commands };
}

function commandFingerprint(command: DeliverableCommand) {
  return asRef(canonicalModelingJson(command));
}

function valueText(value: SourceDocumentBlock['value']) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    if (typeof record.text === 'string') return record.text;
  }
  return typeof value === 'string' ? value : canonicalModelingJson(value);
}

function decisionSection(resolution: ConflictResolutionArtifact, section: ModelingDocumentSection) {
  const blocks = resolution.result.blocks.filter((block) => block.section === section);
  if (!blocks.length) return '';
  const gap = resolution.strategy === 'DEFER_AS_GAP';
  const decisionLabel = gap ? '资料缺口' : resolution.strategy === 'MERGE' ? '已保留的治理结论' : '已确认结论';
  return [
    `### ${decisionLabel}：${resolution.hunk.title}`,
    gap
      ? `> 无法确定：${resolution.reason}\n> AI 建模处理：本项不进入模型候选。`
      : `> 决定：${resolution.reason}`,
    ...blocks.map((block) => `- ${block.label}：${valueText(block.value)}`),
  ].join('\n');
}

function mergedMarkdown(sections: Record<ModelingDocumentSection, string>) {
  return standardSectionOrder.flatMap(({ key, heading }) => [
    `## ${heading}`, '', sections[key].trim() || '暂无可确认内容。', '',
  ]).join('\n').trimEnd() + '\n';
}

function expectedConflictIds(run: StandardizationRun) {
  return run.sources.flatMap((source) => source.introducedConflictIds);
}

function validateBaseline(baseline: ProtectedBaseline, run: StandardizationRun) {
  if (baseline.scenarioKey !== run.scenarioKey) throw new Error('受保护基线与运行故事不一致');
  if (!baseline.expectedArtifactId.trim()
    || baseline.artifact.artifactId !== baseline.expectedArtifactId) {
    throw new Error('受保护 Artifact ID 漂移');
  }
  if (baseline.artifact.projectId !== run.projectId || baseline.artifact.status !== 'FROZEN') {
    throw new Error('受保护 Artifact identity 漂移');
  }
  assertModelingDocumentIntegrity(baseline.artifact);
  if (baseline.artifact.markdown.sha256 !== baseline.markdownSha256) {
    throw new Error('受保护 Artifact Markdown SHA 漂移');
  }
  const semanticPayload = baseline.artifact.semanticPayload;
  if (!semanticPayload
    || semanticPayload.sha256 !== baseline.semanticPayloadSha256
    || modelingDocumentSemanticPayloadSha256(semanticPayload.data) !== baseline.semanticPayloadSha256) {
    throw new Error('受保护 Artifact semantic SHA 漂移');
  }
  if (!baseline.catalogId.trim() || !baseline.catalogFingerprint.trim()) {
    throw new Error('受保护 Catalog descriptor 无效');
  }
  const actualCounts = {
    entities: semanticPayload.data.entities.length,
    events: semanticPayload.data.events.length,
    fields: semanticPayload.data.fields.length,
    relations: semanticPayload.data.relations.length,
    dimensions: semanticPayload.data.dimensions.length,
    metrics: semanticPayload.data.metrics.length,
    hierarchies: semanticPayload.data.hierarchies.length,
    rules: semanticPayload.data.ruleCandidates.length,
    aliases: semanticPayload.data.aliases.length,
    timeRules: semanticPayload.data.timeRules.length,
    pendingAssets: semanticPayload.data.pendingAssets.length,
    exclusions: semanticPayload.data.exclusions.length,
  };
  if (canonicalModelingJson(actualCounts) !== canonicalModelingJson(baseline.expectedCounts)) {
    throw new Error('受保护 Artifact Counts 漂移');
  }
  if (canonicalModelingJson(run.sources.map((source) => source.sourceId))
    !== canonicalModelingJson(baseline.expectedSourceIds)) {
    throw new Error('运行来源不是受保护故事的精确来源集合或顺序');
  }
  if (canonicalModelingJson(Object.keys(baseline.expectedSourceTypes).sort())
    !== canonicalModelingJson([...baseline.expectedSourceIds].sort())) {
    throw new Error('受保护故事的来源类型描述不完整');
  }
}

function validateResolutions(run: StandardizationRun, resolutions: ConflictResolutionArtifact[]) {
  const expected = expectedConflictIds(run);
  const byConflict = new Map<string, ConflictResolutionArtifact>();
  for (const resolution of resolutions) {
    const conflictId = resolution.hunk.conflictId;
    if (resolution.schemaVersion !== 1 || resolution.runId !== run.runId
      || !expected.includes(conflictId) || byConflict.has(conflictId)
      || resolution.structuredPatch.schemaVersion !== 1
      || resolution.structuredPatch.conflictId !== conflictId
      || resolution.previewSha256 !== `sha256:${sha256HexSync(canonicalModelingJson({
        hunk: resolution.hunk,
        strategy: resolution.strategy,
        result: resolution.result,
        structuredPatch: resolution.structuredPatch,
        markdownDiff: resolution.markdownDiff,
        provenanceSources: resolution.provenanceSources,
      }))}`) {
      throw new Error('冲突决定缺失、重复或内容完整性无效');
    }
    const source = run.sources.find((item) => item.sourceId === resolution.sourceId);
    if (!source?.introducedConflictIds.includes(conflictId)
      || !source.resolvedConflictIds.includes(conflictId)) {
      throw new Error('冲突决定没有回链 Run 的精确来源');
    }
    byConflict.set(conflictId, resolution);
  }
  if (canonicalModelingJson([...byConflict.keys()].sort())
    !== canonicalModelingJson([...expected].sort())) {
    throw new Error('每个已引入冲突必须且仅能有一个完整决定');
  }
  return expected.map((conflictId) => byConflict.get(conflictId)!);
}

function decisionsForAppendix(manifest: ResolutionDecisionManifest) {
  return manifest.decisions.map(({ conflictId, resolutionId, previewSha256 }) => ({
    conflictId, resolutionId, previewSha256,
  }));
}

function governanceAppendixFor(
  runId: string,
  sourceManifest: SourceCollectionManifest,
  decisionManifest: ResolutionDecisionManifest,
): GovernanceEvidenceAppendix {
  return {
    schemaVersion: 1,
    runId,
    formalRootSourceIds: sourceManifest.sources.filter((item) => item.evidenceRole === 'FORMAL_ROOT')
      .map((item) => item.sourceId),
    nonFormalSourceIds: sourceManifest.sources.filter((item) => item.evidenceRole === 'NON_FORMAL_ROOT')
      .map((item) => item.sourceId),
    derivedSourceIds: sourceManifest.sources.filter((item) => item.evidenceRole === 'DERIVED_CORROBORATION')
      .map((item) => item.sourceId),
    decisionRefs: decisionsForAppendix(decisionManifest),
    notes: [
      '演示制度只作为非正式说明，不构成正式根证据。',
      'Semantica 只佐证已保存决定，不构成正式根证据。',
      'current_stock_as_of 继续作为治理排除项。',
    ],
  };
}

function coreFor(deliverable: Pick<StandardizationDeliverable,
  'runId' | 'scenarioKey' | 'sourceManifestRef' | 'mergedDocumentRef' | 'decisionManifestRef'
  | 'governanceAppendixRef' | 'modelingArtifactRef' | 'zeroDeltaReportRef' | 'modelingEligibilityRef'>,
baseline: ProtectedBaseline) {
  return {
    schemaVersion: 1,
    runId: deliverable.runId,
    scenarioKey: deliverable.scenarioKey,
    protectedArtifactId: baseline.artifact.artifactId,
    protectedMarkdownSha256: baseline.markdownSha256,
    protectedSemanticPayloadSha256: baseline.semanticPayloadSha256,
    protectedCatalogId: baseline.catalogId,
    protectedCatalogFingerprint: baseline.catalogFingerprint,
    sourceManifestRef: deliverable.sourceManifestRef,
    mergedDocumentRef: deliverable.mergedDocumentRef,
    decisionManifestRef: deliverable.decisionManifestRef,
    governanceAppendixRef: deliverable.governanceAppendixRef,
    modelingArtifactRef: deliverable.modelingArtifactRef,
    zeroDeltaReportRef: deliverable.zeroDeltaReportRef,
    ...(deliverable.modelingEligibilityRef ? { modelingEligibilityRef: deliverable.modelingEligibilityRef } : {}),
  };
}

export function createStandardizationDeliverableRuntime(
  input: StandardizationDeliverableRuntimeInput,
): StandardizationDeliverableRuntime {
  const now = input.now ?? (() => new Date().toISOString());
  const requireAccess = async (actorUserId: string, projectId: string) => {
    const access = await input.membershipReader.read(actorUserId, projectId);
    if (!access.active) throw new Error('当前用户不是当前项目 ACTIVE member');
    return access;
  };
  const load = async () => {
    const snapshot = await input.metadataStore.read();
    return { snapshot, state: parseState(snapshot.raw) };
  };
  const commit = async (version: string, state: StoredState) => {
    if (!await input.metadataStore.compareAndSet(version, JSON.stringify(state))) {
      throw new Error('交付物已被其他窗口更新');
    }
  };
  const put = async (value: unknown) => {
    const content = canonicalModelingJson(jsonValue(value));
    const expected = asRef(content);
    const actual = await input.contentStore.put(content);
    if (actual !== expected) throw new Error('内容寻址存储返回了错误引用');
    return actual;
  };
  const contentReferenceFor = (value: unknown) => asRef(canonicalModelingJson(jsonValue(value)));
  const findDeliverableById = (state: StoredState, deliverableId: string) => (
    state.deliverables.find((item) => item.deliverableId === deliverableId) ?? null
  );
  /**
   * The Run's current ID is the normal actionable record.  There is one
   * deliberate exception: generation first persists the deliverable metadata,
   * then appends the Run event.  If that append fails, recovery must still
   * find the exact pending record and finish that event.  Superseded results
   * are never returned here; they remain read-only history.
   */
  const findDeliverable = (state: StoredState, run: StandardizationRun) => {
    if (run.deliverableId) return findDeliverableById(state, run.deliverableId);
    const superseded = new Set(run.supersededDeliverableIds ?? []);
    return state.deliverables.find((item) => (
      item.runId === run.runId
      && item.linkState === 'GENERATED_EVENT_PENDING'
      && !superseded.has(item.deliverableId)
    )) ?? null;
  };
  const readJson = async <T,>(ref: ContentReference, label: string): Promise<T> => {
    const content = await input.contentStore.get(ref);
    if (content === null || asRef(content) !== ref) throw new Error(`${label}不存在或校验和不一致`);
    try { return JSON.parse(content) as T; } catch { throw new Error(`${label}不是合法JSON`); }
  };
  const assertExactRunEvent = async (
    run: StandardizationRun,
    type: 'DELIVERABLE_GENERATED' | 'REVIEW_SUBMITTED' | 'DELIVERABLE_FROZEN' | 'MODELING_HANDOFF_COMPLETED',
    actorUserId: string,
    expectedPayload: string,
  ) => {
    const events = run.timeline.filter((event) => event.type === type && event.actor.userId === actorUserId);
    const matches = await Promise.all(events.map(async (event) => (
      await input.runReader.readEventPayload(run.runId, event.eventId) === expectedPayload
    )));
    if (matches.filter(Boolean).length !== 1) {
      throw new Error('Run事件载荷与交付metadata不一致');
    }
  };
  const readSourceEntries = async (
    run: StandardizationRun,
    baseline: ProtectedBaseline,
  ): Promise<LoadedSourceEntry[]> => {
    if (!input.sourceDocuments.readSections || !input.sourceDocuments.readAssertions
      || !input.sourceDocuments.readBlocks || !input.sourceDocuments.readMarkdown) {
      throw new Error('SourceDocumentReader未提供结构化内容读取能力');
    }
    const readSections = input.sourceDocuments.readSections;
    const readAssertions = input.sourceDocuments.readAssertions;
    const readBlocks = input.sourceDocuments.readBlocks;
    const readMarkdown = input.sourceDocuments.readMarkdown;
    return Promise.all(run.sources.map(async (source) => {
      if (!source.documentId || !source.documentRevision || source.status !== 'ALIGNED') {
        throw new Error('来源尚未精确对齐');
      }
      const document = await input.sourceDocuments.read(source.documentId);
      if (!document || document.documentId !== source.documentId
        || document.revision !== source.documentRevision || document.projectId !== run.projectId
        || document.sourceName !== source.sourceName
        || document.sourceType !== baseline.expectedSourceTypes[source.sourceId]
        || !document.blocksRef || document.status !== 'READY_FOR_ALIGNMENT') {
        throw new Error('Run来源文档identity/revision/blocks不一致');
      }
      const documentEvents = run.timeline.filter((event) => (
        (event.type === 'DOCUMENT_GENERATED' || event.type === 'DOCUMENT_REVISED')
        && event.sourceId === source.sourceId
        && event.sourceDocumentId === document.documentId
        && event.sourceDocumentRevision === document.revision
      ));
      if (documentEvents.length !== 1) {
        throw new Error('Run来源文档缺少唯一revision内容身份事件');
      }
      let documentEventPayload: unknown;
      try {
        documentEventPayload = JSON.parse(await input.runReader.readEventPayload(
          run.runId, documentEvents[0]!.eventId,
        ));
      } catch {
        throw new Error('Run来源文档revision事件载荷无效');
      }
      const expectedDocumentContent = {
        sectionsRef: document.sectionsRef,
        assertionsRef: document.assertionsRef,
        blocksRef: document.blocksRef,
        markdownRef: document.markdownRef,
        markdownSha256: document.markdownSha256,
      };
      const actualDocumentContent = documentEventPayload && typeof documentEventPayload === 'object'
        ? (documentEventPayload as Record<string, unknown>).documentContent
        : undefined;
      if (!actualDocumentContent || typeof actualDocumentContent !== 'object'
        || Array.isArray(actualDocumentContent)
        || canonicalModelingJson(actualDocumentContent)
          !== canonicalModelingJson(expectedDocumentContent)) {
        throw new Error('Run来源文档revision事件没有绑定原始结构化内容identity');
      }
      const [sections, assertions, blocks, markdown] = await Promise.all([
        readSections(document.documentId),
        readAssertions(document.documentId),
        readBlocks(document.documentId),
        readMarkdown(document.documentId),
      ]);
      if (sha256HexSync(markdown) !== document.markdownSha256) {
        throw new Error('Run来源Markdown SHA不一致');
      }
      if (!Array.isArray(assertions) || !Array.isArray(blocks)) {
        throw new Error('Run来源结构化内容无效');
      }
      assertStructuredSourceDocumentProjection({ blocks, assertions, sections });
      const expectedMarkdown = renderSourceDocumentMarkdown({
        ...document, sections, assertions,
      });
      if (markdown !== expectedMarkdown) {
        throw new Error('Run来源Markdown不是blocks/assertions/sections的唯一投影');
      }
      return { source, document, sections, assertions, blocks, markdown };
    }));
  };
  const sourceManifestFor = (
    run: StandardizationRun,
    sourceEntries: LoadedSourceEntry[],
  ): SourceCollectionManifest => ({
    schemaVersion: 1,
    runId: run.runId,
    scenarioKey: run.scenarioKey,
    sources: sourceEntries.map(({ source, document }, index) => ({
      order: index + 1,
      sourceId: source.sourceId,
      sourceName: source.sourceName,
      documentId: document.documentId,
      documentRevision: document.revision,
      sourceSnapshotId: document.sourceSnapshotId,
      sourceType: document.sourceType,
      sectionsRef: document.sectionsRef,
      assertionsRef: document.assertionsRef,
      blocksRef: document.blocksRef!,
      markdownRef: document.markdownRef,
      markdownSha256: document.markdownSha256,
      evidenceRole: document.sourceType === 'DERIVED'
        ? 'DERIVED_CORROBORATION'
        : document.sourceType === 'DEMO_POLICY' ? 'NON_FORMAL_ROOT' : 'FORMAL_ROOT',
    })),
  });
  const mergedDocumentFor = (
    run: StandardizationRun,
    sourceEntries: LoadedSourceEntry[],
    resolutions: ConflictResolutionArtifact[],
  ): MergedStandardizationDocument => {
    const sections = Object.fromEntries(standardSectionOrder.map(({ key }) => {
      const sourceParts = sourceEntries.map(({ source, document, sections: sourceSections }) => [
        `### 来源：${source.sourceName}`,
        `> 本节依据本次已读取的 ${document.sourceName} 资料整理。`,
        sourceSections[key],
      ].join('\n'));
      const decisions = resolutions.map((resolution) => decisionSection(resolution, key)).filter(Boolean);
      if (key === 'UNRESOLVED') {
        decisions.push('> 资料缺口：当前库存缺少可信业务生效时间字段。\n> AI 建模处理：不生成当前库存时点指标或 SLA。');
      }
      return [key, [...sourceParts, ...decisions].join('\n\n')];
    })) as Record<ModelingDocumentSection, string>;
    return {
      schemaVersion: 1,
      runId: run.runId,
      sourceSnapshotIds: sourceEntries.map(({ document }) => document.sourceSnapshotId),
      sections,
      markdown: mergedMarkdown(sections),
    };
  };
  const decisionManifestFor = (
    run: StandardizationRun,
    resolutions: ConflictResolutionArtifact[],
  ): ResolutionDecisionManifest => ({
    schemaVersion: 1,
    runId: run.runId,
    decisions: resolutions.map((resolution) => ({
      conflictId: resolution.hunk.conflictId,
      resolutionId: resolution.resolutionId,
      sourceId: resolution.sourceId,
      strategy: resolution.strategy,
      reason: resolution.reason,
      actorUserId: resolution.actorUserId,
      decidedAt: resolution.decidedAt,
      previewSha256: resolution.previewSha256,
      hunkSha256: resolution.hunk.hunkSha256,
      affectedObjectIds: resolution.hunk.affectedObjectRefs.map((ref) => ref.objectId).sort(),
    })),
  });
  const reviewSupplementsFor = (
    sourceEntries: LoadedSourceEntry[],
    resolutions: ConflictResolutionArtifact[],
  ): ReviewSupplementMatter[] => {
    const conflictIdsBySourceBlock = new Map<string, string[]>();
    const addConflict = (input: {
      sourceId: string;
      documentId: string;
      blockId: string;
      conflictId: string;
    }) => {
      const key = `${input.sourceId}:${input.documentId}:${input.blockId}`;
      const values = conflictIdsBySourceBlock.get(key) ?? [];
      if (!values.includes(input.conflictId)) values.push(input.conflictId);
      conflictIdsBySourceBlock.set(key, values);
    };
    for (const resolution of resolutions) {
      for (const side of [resolution.hunk.current, resolution.hunk.incoming, ...resolution.hunk.corroborating]) {
        addConflict({
          sourceId: side.sourceId,
          documentId: side.documentId,
          blockId: side.block.blockId,
          conflictId: resolution.hunk.conflictId,
        });
      }
    }

    return sourceEntries.flatMap(({ source, document, sections, blocks }) => {
      const chapterEntries = blocks.filter((block) => block.section === 'UNRESOLVED');
      const entries = chapterEntries.length > 0
        ? chapterEntries.map((block) => ({
            blockId: block.blockId,
            text: valueText(block.value),
            semanticKind: block.semanticKind,
            label: block.label,
          }))
        : [{ blockId: 'section:UNRESOLVED', text: sections.UNRESOLVED, semanticKind: undefined, label: undefined }];
      return entries.flatMap((entry) => {
        const segments = projectReviewSupplementSegments(entry.text)
          .filter((segment): segment is Extract<ReturnType<typeof projectReviewSupplementSegments>[number], { kind: 'SUPPLEMENT' }> => (
            segment.kind === 'SUPPLEMENT'
          ));
        // Legacy structured source documents have no textual marker in every
        // GAP block. Their semantic kind is an equally exact, persisted
        // identity, so retain the whole block as one supplement rather than
        // hiding it or inferring a smaller sentence from its wording.
        if (segments.length === 0 && entry.semanticKind === 'GAP') {
          segments.push({ kind: 'SUPPLEMENT', ordinal: 1, text: entry.label
            ? `${entry.label}：${entry.text}`
            : entry.text });
        }
        return segments.map((segment, index) => ({
          stableId: `supplement:${source.sourceId}:${document.documentId}:r${document.revision}:${entry.blockId}:${index + 1}`,
          sourceId: source.sourceId,
          sourceName: source.sourceName,
          documentId: document.documentId,
          documentRevision: document.revision,
          section: 'UNRESOLVED' as const,
          ordinal: segment.ordinal,
          text: presentReviewSupplementText(segment.text),
          relatedConflictIds: [...(conflictIdsBySourceBlock.get(
            `${source.sourceId}:${document.documentId}:${entry.blockId}`,
          ) ?? [])],
        }));
      });
    });
  };
  const reviewProjectionFor = (
    sourceEntries: LoadedSourceEntry[],
    resolutions: ConflictResolutionArtifact[],
  ): StandardizationDeliverablePreview['reviewProjection'] => ({
    chapters: standardSectionOrder.map(({ key, heading }) => ({
      section: key,
      heading,
      sources: sourceEntries.map(({ source, sections, assertions, blocks }, index) => ({
        order: index + 1,
        sourceId: source.sourceId,
        sourceName: source.sourceName,
        markdown: sections[key],
        assertions: structuredClone(assertions.filter((assertion) => assertion.section === key)),
        blocks: structuredClone(blocks.filter((block) => block.section === key)),
      })),
    })),
    supplements: reviewSupplementsFor(sourceEntries, resolutions),
    decisions: resolutions.map((resolution) => ({
      conflictId: resolution.hunk.conflictId,
      title: resolution.hunk.title,
      sourceId: resolution.sourceId,
      strategy: resolution.strategy,
      reason: resolution.reason,
    })),
  });
  const buildPreview = async (run: StandardizationRun): Promise<PreviewBuild> => {
    const baseline = await input.baselineProvider.resolve(run.scenarioKey);
    if (!baseline) throw new Error('当前故事没有受保护基线');
    validateBaseline(baseline, run);
    const resolutions = validateResolutions(run, await input.conflictResolutions.list(run));
    const sourceEntries = await readSourceEntries(run, baseline);
    const sourceManifest = sourceManifestFor(run, sourceEntries);
    const mergedDocument = mergedDocumentFor(run, sourceEntries, resolutions);
    const decisionManifest = decisionManifestFor(run, resolutions);
    const governanceAppendix = governanceAppendixFor(run.runId, sourceManifest, decisionManifest);
    const reviewProjection = reviewProjectionFor(sourceEntries, resolutions);
    const previewSha256 = contentReferenceFor({
      sourceManifest,
      decisionManifest,
      mergedDocument,
      reviewProjection,
    });
    return {
      preview: {
        schemaVersion: 1,
        runId: run.runId,
        runRevision: run.revision,
        previewSha256,
        mergedDocumentRef: contentReferenceFor(mergedDocument),
        sourceManifest,
        decisionManifest,
        mergedDocument,
        reviewProjection,
      },
      baseline,
      resolutions,
      sourceEntries,
      governanceAppendix,
    };
  };
  const generatedEventPayloadFor = (deliverable: StandardizationDeliverable) => canonicalModelingJson({
    schemaVersion: 1,
    deliverableId: deliverable.deliverableId,
    mode: deliverable.mode,
    coreSha256: deliverable.coreSha256,
    sourceManifestRef: deliverable.sourceManifestRef,
    mergedDocumentRef: deliverable.mergedDocumentRef,
    decisionManifestRef: deliverable.decisionManifestRef,
    governanceAppendixRef: deliverable.governanceAppendixRef,
    modelingArtifactRef: deliverable.modelingArtifactRef,
    zeroDeltaReportRef: deliverable.zeroDeltaReportRef,
  });
  const reviewEventPayloadFor = (deliverable: StandardizationDeliverable) => {
    const confirmation = deliverable.authorConfirmation;
    if (!confirmation) throw new Error('交付物缺少作者确认');
    return canonicalModelingJson({
      schemaVersion: 1, deliverableId: deliverable.deliverableId,
      reviewId: confirmation.reviewId, authorUserId: confirmation.actorUserId,
      confirmedAt: confirmation.confirmedAt, coreSha256: deliverable.coreSha256,
    });
  };
  const frozenEventPayloadFor = async (deliverable: StandardizationDeliverable) => {
    const authorFreeze = deliverable.authorFreezeConfirmation;
    const approval = deliverable.reviewerApproval;
    const artifact = await readJson<ProtectedBaseline['artifact']>(deliverable.modelingArtifactRef, '建模Artifact');
    if (authorFreeze) {
      return canonicalModelingJson({
        schemaVersion: 1, deliverableId: deliverable.deliverableId,
        confirmationId: authorFreeze.confirmationId,
        authorUserId: authorFreeze.actorUserId,
        confirmedAt: authorFreeze.confirmedAt,
        inputDeliverableRevision: authorFreeze.inputDeliverableRevision,
        coreSha256: deliverable.coreSha256, zeroDeltaReportRef: deliverable.zeroDeltaReportRef,
        semanticPayloadSha256: `sha256:${artifact.semanticPayload!.sha256}`,
      });
    }
    if (!approval) throw new Error('交付物缺少定版确认');
    return canonicalModelingJson({
      schemaVersion: 1, deliverableId: deliverable.deliverableId, reviewId: approval.reviewId,
      reviewerUserId: approval.actorUserId, reviewedAt: approval.reviewedAt,
      coreSha256: deliverable.coreSha256, zeroDeltaReportRef: deliverable.zeroDeltaReportRef,
      semanticPayloadSha256: `sha256:${artifact.semanticPayload!.sha256}`,
    });
  };
  const handoffEventPayloadFor = (
    deliverable: StandardizationDeliverable,
    receipt: StandardizationModelingHandoffReceipt,
  ) => receipt.kind === 'ZERO_DELTA_MODELING_HANDOFF'
    ? canonicalModelingJson({
        schemaVersion: 1, deliverableId: deliverable.deliverableId,
        handoffId: receipt.receiptId, receiptRef: deliverable.handoffReceiptRef,
        zeroDeltaReportRef: deliverable.zeroDeltaReportRef,
        semanticPayloadSha256: `sha256:${receipt.semanticPayloadSha256}`,
        protectedCatalogId: receipt.protectedCatalogId,
        handedOffBy: receipt.handedOffBy,
        handedOffAt: receipt.handedOffAt,
      })
    : canonicalModelingJson({
        schemaVersion: 1, deliverableId: deliverable.deliverableId,
        handoffId: receipt.receiptId, receiptRef: deliverable.handoffReceiptRef,
        mergedDocumentRef: receipt.mergedDocumentRef,
        modelingEligibilityRef: receipt.modelingEligibilityRef,
        eligibleConclusionCount: receipt.eligibleConclusionCount,
        excludedConclusionCount: receipt.excludedConclusionCount,
        handedOffBy: receipt.handedOffBy,
        handedOffAt: receipt.handedOffAt,
      });
  const validateLinkedLifecycle = async (
    deliverable: StandardizationDeliverable,
    run: StandardizationRun,
  ) => {
    const allowedRunStatuses = deliverable.status === 'FROZEN'
      ? deliverable.linkState === 'FROZEN_EVENT_PENDING'
        ? ['READY_FOR_OUTPUT', 'FROZEN']
        : ['FROZEN']
      : deliverable.status === 'HANDED_OFF'
        ? deliverable.linkState === 'HANDOFF_EVENT_PENDING'
          ? ['FROZEN', 'HANDED_OFF']
          : ['HANDED_OFF']
        : ['READY_FOR_OUTPUT'];
    if (!allowedRunStatuses.includes(run.status)) {
      throw new Error('Run阶段与交付metadata状态不一致');
    }
    if (deliverable.linkState !== 'GENERATED_EVENT_PENDING'
      || run.deliverableId === deliverable.deliverableId) {
      await assertExactRunEvent(
        run, 'DELIVERABLE_GENERATED', deliverable.authorUserId,
        generatedEventPayloadFor(deliverable),
      );
    }
    if (deliverable.authorConfirmation && (deliverable.linkState !== 'REVIEW_EVENT_PENDING'
      || run.reviewId === deliverable.authorConfirmation.reviewId)) {
      await assertExactRunEvent(
        run, 'REVIEW_SUBMITTED', deliverable.authorConfirmation.actorUserId,
        reviewEventPayloadFor(deliverable),
      );
    }
    const frozenActorUserId = deliverable.authorFreezeConfirmation?.actorUserId
      ?? deliverable.reviewerApproval?.actorUserId;
    if (frozenActorUserId && (deliverable.linkState !== 'FROZEN_EVENT_PENDING'
      || run.status === 'FROZEN' || run.status === 'HANDED_OFF')) {
      await assertExactRunEvent(
        run, 'DELIVERABLE_FROZEN', frozenActorUserId,
        await frozenEventPayloadFor(deliverable),
      );
    }
  };
  const validateCore = async (
    deliverable: StandardizationDeliverable,
    run: StandardizationRun,
    requireZero: boolean,
  ) => {
    const baseline = await input.baselineProvider.resolve(run.scenarioKey);
    if (!baseline) throw new Error('当前故事没有受保护基线');
    validateBaseline(baseline, run);
    if (deliverable.runId !== run.runId || deliverable.scenarioKey !== run.scenarioKey
      || deliverable.projectId !== run.projectId
      || deliverable.coreSha256 !== asRef(canonicalModelingJson(coreFor(deliverable, baseline)))) {
      throw new Error('交付物核心hash与受保护描述不一致');
    }
    const [manifest, merged, decisionManifest, appendix, artifact, delta, storedEligibility] = await Promise.all([
      readJson<SourceCollectionManifest>(deliverable.sourceManifestRef, '来源清单'),
      readJson<MergedStandardizationDocument>(deliverable.mergedDocumentRef, '九段合并文档'),
      readJson<ResolutionDecisionManifest>(deliverable.decisionManifestRef, '决定清单'),
      readJson<GovernanceEvidenceAppendix>(deliverable.governanceAppendixRef, '治理附录'),
      readJson<ProtectedBaseline['artifact']>(deliverable.modelingArtifactRef, '建模Artifact'),
      readJson<ZeroDeltaReport>(deliverable.zeroDeltaReportRef, '零变化报告'),
      deliverable.modelingEligibilityRef
        ? readJson<StandardizationModelingEligibilityProjection>(deliverable.modelingEligibilityRef, '可建模结论')
        : Promise.resolve(undefined),
    ]);
    const resolutions = validateResolutions(run, await input.conflictResolutions.list(run));
    const sourceEntries = await readSourceEntries(run, baseline);
    const expectedManifest = sourceManifestFor(run, sourceEntries);
    if (canonicalModelingJson(manifest) !== canonicalModelingJson(expectedManifest)) {
      throw new Error('来源清单没有回链当前持久化revision');
    }
    const expectedMerged = mergedDocumentFor(run, sourceEntries, resolutions);
    if (canonicalModelingJson(merged) !== canonicalModelingJson(expectedMerged)) {
      throw new Error('九段合并文档不是持久化来源 revision 与 CP5 决定的唯一投影');
    }
    const expectedDecisions = resolutions.map((resolution) => ({
      conflictId: resolution.hunk.conflictId,
      resolutionId: resolution.resolutionId,
      sourceId: resolution.sourceId,
      strategy: resolution.strategy,
      reason: resolution.reason,
      actorUserId: resolution.actorUserId,
      decidedAt: resolution.decidedAt,
      previewSha256: resolution.previewSha256,
      hunkSha256: resolution.hunk.hunkSha256,
      affectedObjectIds: resolution.hunk.affectedObjectRefs.map((ref) => ref.objectId).sort(),
    }));
    if (decisionManifest.schemaVersion !== 1 || decisionManifest.runId !== run.runId
      || canonicalModelingJson(decisionManifest.decisions) !== canonicalModelingJson(expectedDecisions)) {
      throw new Error('决定清单不是CP5 Artifact的唯一投影');
    }
    const expectedAppendix = governanceAppendixFor(run.runId, expectedManifest, decisionManifest);
    if (canonicalModelingJson(appendix) !== canonicalModelingJson(expectedAppendix)) {
      throw new Error('治理附录不是来源角色与决定的唯一投影');
    }
    assertModelingDocumentIntegrity(artifact);
    if (artifact.artifactId === baseline.artifact.artifactId
      || artifact.derivedFromArtifactId !== baseline.artifact.artifactId
      || artifact.origin !== 'STANDARDIZATION') throw new Error('新Artifact冒充受保护Artifact');
    const projected = await input.modelingProjector.project({ run, baseline, resolutions, mergedDocument: merged });
    if (canonicalModelingJson(jsonValue(projected.artifact)) !== canonicalModelingJson(jsonValue(artifact))
      || canonicalModelingJson(projected.zeroDeltaReport) !== canonicalModelingJson(delta)) {
      throw new Error('Artifact或两层delta不能从持久化来源与决定复算');
    }
    const expectedEligibility = projectModelingEligibility(artifact);
    if (storedEligibility) {
      assertModelingEligibilityProjection(storedEligibility, artifact);
      if (canonicalModelingJson(storedEligibility) !== canonicalModelingJson(expectedEligibility)) {
        throw new Error('可建模结论不能从当前Artifact复算');
      }
    }
    if (requireZero && (delta.semanticDifferences.length !== 0 || delta.catalogChanges.length !== 0
      || artifact.semanticPayload?.sha256 !== baseline.semanticPayloadSha256
      || canonicalModelingJson(delta.counts) !== canonicalModelingJson(baseline.expectedCounts))) {
      throw new Error('两层语义变化非零，禁止定版或交接');
    }
    return { baseline, artifact, delta, eligibility: storedEligibility ?? expectedEligibility };
  };
  const eligibilitySnapshotFor = async (deliverable: StandardizationDeliverable) => (
    deliverable.modelingEligibilityRef
      ? readJson<StandardizationModelingEligibilityProjection>(deliverable.modelingEligibilityRef, '可建模结论')
      : undefined
  );
  const finishGeneratedLink = async (
    command: Extract<DeliverableCommand, { type: 'GENERATE_DELIVERABLE' }>,
    deliverable: StandardizationDeliverable,
    run: StandardizationRun,
    existingEventValidated = false,
  ) => {
    if (deliverable.linkState === 'LINKED') {
      const modelingEligibility = await eligibilitySnapshotFor(deliverable);
      return { deliverable: clone(deliverable), run, ...(modelingEligibility ? { modelingEligibility } : {}) };
    }
    const payload = generatedEventPayloadFor(deliverable);
    let linkedRun = run;
    if (run.deliverableId !== deliverable.deliverableId) {
      linkedRun = await input.runReader.appendEvent({
        type: 'DELIVERABLE_GENERATED', commandId: `${command.commandId}:run-link`,
        runId: command.runId, expectedRunRevision: run.revision,
        actorUserId: command.actorUserId, deliverableId: deliverable.deliverableId,
        eventIdentity: deliverable.deliverableId, payload,
      });
    } else if (!existingEventValidated) {
      await assertExactRunEvent(run, 'DELIVERABLE_GENERATED', deliverable.authorUserId, payload);
    }
    const current = await load();
    // A generated result can become historical when a source decision is
    // reopened before freeze. Finish the link for this exact record, not the
    // run's (possibly newer or temporarily absent) active result.
    const stored = findDeliverableById(current.state, deliverable.deliverableId);
    if (!stored || stored.deliverableId !== deliverable.deliverableId) {
      throw new Error('交付物链接恢复时 metadata 身份变化');
    }
    if (stored.linkState !== 'LINKED') {
      stored.linkState = 'LINKED';
      await commit(current.snapshot.version, current.state);
    }
    const modelingEligibility = await eligibilitySnapshotFor(stored);
    return { deliverable: clone(stored), run: linkedRun, ...(modelingEligibility ? { modelingEligibility } : {}) };
  };
  const receiptFor = async (deliverable: StandardizationDeliverable, run: StandardizationRun) => {
    if (!deliverable.handoffReceiptRef) return undefined;
    const receipt = await readJson<StandardizationModelingHandoffReceipt>(
      deliverable.handoffReceiptRef,
      '标准化文档交接记录',
    );
    const verified = await validateCore(deliverable, run, receipt.kind === 'ZERO_DELTA_MODELING_HANDOFF');
    if (receipt.kind === 'ZERO_DELTA_MODELING_HANDOFF') {
      const identityMismatches = [
        receipt.schemaVersion !== 1 ? 'schemaVersion' : undefined,
        receipt.receiptId !== `zero-delta-receipt-${deliverable.coreSha256.slice(7, 23)}` ? 'receiptId' : undefined,
        receipt.runId !== deliverable.runId ? 'runId' : undefined,
        receipt.deliverableId !== deliverable.deliverableId ? 'deliverableId' : undefined,
        receipt.protectedArtifactId !== verified.baseline.artifact.artifactId ? 'protectedArtifactId' : undefined,
        receipt.protectedCatalogId !== verified.baseline.catalogId ? 'protectedCatalogId' : undefined,
        receipt.artifactId !== verified.artifact.artifactId ? 'artifactId' : undefined,
        receipt.artifactMarkdownSha256 !== verified.artifact.markdown.sha256 ? 'artifactMarkdownSha256' : undefined,
        receipt.semanticPayloadSha256 !== verified.artifact.semanticPayload?.sha256 ? 'semanticPayloadSha256' : undefined,
        receipt.governanceAppendixRef !== deliverable.governanceAppendixRef ? 'governanceAppendixRef' : undefined,
        receipt.zeroDeltaReportRef !== deliverable.zeroDeltaReportRef ? 'zeroDeltaReportRef' : undefined,
        receipt.handedOffAt !== deliverable.updatedAt ? 'handedOffAt' : undefined,
      ].filter((value): value is string => value !== undefined);
      if (identityMismatches.length) throw new Error(`M4零变化Receipt与受保护或新Artifact identity不一致：${identityMismatches.join('、')}`);
    } else {
      const expectedEligible = verified.eligibility.eligibleCandidateIds.length;
      const expectedExcluded = verified.eligibility.excludedCandidateIds.length
        + verified.eligibility.conclusions.filter((item) => !item.candidateId && item.eligibility !== 'ELIGIBLE').length;
      const identityMismatches = [
        receipt.schemaVersion !== 1 ? 'schemaVersion' : undefined,
        receipt.receiptId !== `standardization-handoff-${deliverable.coreSha256.slice(7, 23)}` ? 'receiptId' : undefined,
        receipt.runId !== deliverable.runId ? 'runId' : undefined,
        receipt.deliverableId !== deliverable.deliverableId ? 'deliverableId' : undefined,
        receipt.artifactId !== verified.artifact.artifactId ? 'artifactId' : undefined,
        receipt.mergedDocumentRef !== deliverable.mergedDocumentRef ? 'mergedDocumentRef' : undefined,
        receipt.modelingEligibilityRef !== deliverable.modelingEligibilityRef ? 'modelingEligibilityRef' : undefined,
        receipt.eligibleConclusionCount !== expectedEligible ? 'eligibleConclusionCount' : undefined,
        receipt.excludedConclusionCount !== expectedExcluded ? 'excludedConclusionCount' : undefined,
        receipt.handedOffAt !== deliverable.updatedAt ? 'handedOffAt' : undefined,
      ].filter((value): value is string => value !== undefined);
      if (identityMismatches.length) throw new Error(`标准化文档交接记录与交付物identity不一致：${identityMismatches.join('、')}`);
    }
    if (!receipt.handedOffBy?.trim() || !receipt.handedOffAt?.trim()) {
      throw new Error('标准化文档交接记录缺少操作人或时间');
    }
    if (run.status === 'HANDED_OFF') {
      await assertExactRunEvent(
        run, 'MODELING_HANDOFF_COMPLETED', receipt.handedOffBy,
        handoffEventPayloadFor(deliverable, receipt),
      );
    }
    return receipt;
  };
  const pendingCommandFor = (
    deliverable: StandardizationDeliverable,
    run: StandardizationRun,
    state: StoredState,
    receipt?: StandardizationModelingHandoffReceipt,
  ): DeliverableCommand | undefined => {
    if (deliverable.linkState === 'LINKED') return undefined;
    const persistedHandoffType = Object.values(state.commands).find((execution) => (
      execution.deliverableId === deliverable.deliverableId
      && (execution.type === 'HANDOFF_ZERO_DELTA_TO_M4'
        || execution.type === 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING')
    ))?.type;
    const type = deliverable.linkState === 'GENERATED_EVENT_PENDING'
      ? 'GENERATE_DELIVERABLE'
      : deliverable.linkState === 'REVIEW_EVENT_PENDING'
        ? 'AUTHOR_CONFIRM'
        : deliverable.linkState === 'FROZEN_EVENT_PENDING'
          ? deliverable.authorFreezeConfirmation
            ? 'AUTHOR_CONFIRM_AND_FREEZE'
            : 'REVIEW_AND_FREEZE'
          : (persistedHandoffType ?? (deliverable.modelingEligibilityRef
            ? 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING'
            : 'HANDOFF_ZERO_DELTA_TO_M4'));
    const entries = Object.entries(state.commands).filter(([, execution]) => (
      execution.deliverableId === deliverable.deliverableId && execution.type === type
    ));
    if (entries.length !== 1) throw new Error('待恢复交付命令identity缺失或重复');
    const [commandId, execution] = entries[0]!;
    const actorUserId = type === 'GENERATE_DELIVERABLE'
      ? deliverable.authorUserId
      : type === 'AUTHOR_CONFIRM'
        ? deliverable.authorConfirmation?.actorUserId
        : type === 'AUTHOR_CONFIRM_AND_FREEZE'
          ? deliverable.authorFreezeConfirmation?.actorUserId
        : type === 'REVIEW_AND_FREEZE'
          ? deliverable.reviewerApproval?.actorUserId
          : receipt?.handedOffBy;
    if (!actorUserId) throw new Error('待恢复交付命令缺少操作者identity');
    const command: DeliverableCommand = type === 'GENERATE_DELIVERABLE'
      ? {
          type, commandId, runId: deliverable.runId, actorUserId,
          expectedRunRevision: run.deliverableId === deliverable.deliverableId
            ? run.revision - 1
            : run.revision,
        }
      : {
          type, commandId, runId: deliverable.runId, deliverableId: deliverable.deliverableId,
          actorUserId, expectedDeliverableRevision: deliverable.revision - 1,
        };
    if (execution.runId !== deliverable.runId || execution.fingerprint !== commandFingerprint(command)) {
      throw new Error('待恢复交付命令fingerprint与metadata不一致');
    }
    return command;
  };
  const finishLifecycleLink = async (
    command: Exclude<DeliverableCommand, { type: 'GENERATE_DELIVERABLE' }>,
    deliverable: StandardizationDeliverable,
    run: StandardizationRun,
    existingEventsValidated = false,
  ) => {
    if (deliverable.linkState === 'LINKED') {
      const receipt = await receiptFor(deliverable, run);
      const modelingEligibility = await eligibilitySnapshotFor(deliverable);
      return {
        deliverable: clone(deliverable), run,
        ...(receipt ? { receipt } : {}),
        ...(modelingEligibility ? { modelingEligibility } : {}),
      };
    }
    let linkedRun = run;
    if (command.type === 'AUTHOR_CONFIRM') {
      const confirmation = deliverable.authorConfirmation!;
      const payload = reviewEventPayloadFor(deliverable);
      if (run.reviewId !== confirmation.reviewId) {
        linkedRun = await input.runReader.appendEvent({
          type: 'REVIEW_SUBMITTED', commandId: `${command.commandId}:run-link`, runId: command.runId,
          expectedRunRevision: run.revision, actorUserId: command.actorUserId,
          deliverableId: deliverable.deliverableId, eventIdentity: confirmation.reviewId,
          payload,
        });
      } else if (!existingEventsValidated) {
        await assertExactRunEvent(run, 'REVIEW_SUBMITTED', confirmation.actorUserId, payload);
      }
    } else if (command.type === 'REVIEW_AND_FREEZE' || command.type === 'AUTHOR_CONFIRM_AND_FREEZE') {
      const frozenActorUserId = deliverable.authorFreezeConfirmation?.actorUserId
        ?? deliverable.reviewerApproval?.actorUserId;
      if (!frozenActorUserId) throw new Error('交付物缺少定版确认');
      const payload = await frozenEventPayloadFor(deliverable);
      if (run.status !== 'FROZEN' && run.status !== 'HANDED_OFF') {
        linkedRun = await input.runReader.appendEvent({
          type: 'DELIVERABLE_FROZEN', commandId: `${command.commandId}:run-link`, runId: command.runId,
          expectedRunRevision: run.revision, actorUserId: command.actorUserId,
          deliverableId: deliverable.deliverableId, eventIdentity: deliverable.deliverableId,
          payload,
        });
      } else if (!existingEventsValidated) {
        await assertExactRunEvent(run, 'DELIVERABLE_FROZEN', frozenActorUserId, payload);
      }
    } else if (command.type === 'HANDOFF_ZERO_DELTA_TO_M4'
      || command.type === 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING') {
      const receipt = await receiptFor(deliverable, run);
      if (!receipt) throw new Error('标准化文档交接记录不存在');
      const payload = handoffEventPayloadFor(deliverable, receipt);
      if (run.status !== 'HANDED_OFF') {
        linkedRun = await input.runReader.appendEvent({
          type: 'MODELING_HANDOFF_COMPLETED', commandId: `${command.commandId}:run-link`, runId: command.runId,
          expectedRunRevision: run.revision, actorUserId: command.actorUserId,
          deliverableId: deliverable.deliverableId, eventIdentity: receipt.receiptId,
          payload,
        });
      } else if (!existingEventsValidated) {
        await assertExactRunEvent(run, 'MODELING_HANDOFF_COMPLETED', receipt.handedOffBy, payload);
      }
    }
    const current = await load();
    const stored = findDeliverableById(current.state, deliverable.deliverableId);
    if (!stored || stored.deliverableId !== deliverable.deliverableId) {
      throw new Error('交付物事件链接恢复时 metadata 身份变化');
    }
    if (stored.linkState !== 'LINKED') {
      stored.linkState = 'LINKED';
      await commit(current.snapshot.version, current.state);
    }
    const receipt = await receiptFor(stored, linkedRun);
    const modelingEligibility = await eligibilitySnapshotFor(stored);
    return {
      deliverable: clone(stored), run: linkedRun,
      ...(receipt ? { receipt } : {}),
      ...(modelingEligibility ? { modelingEligibility } : {}),
    };
  };

  return {
    async read({ runId, actorUserId }) {
      const run = await input.runReader.read(runId);
      if (!run) throw new Error('标准化运行不存在');
      await requireAccess(actorUserId, run.projectId);
      const { state } = await load();
      const deliverable = findDeliverable(state, run);
      const receipt = deliverable ? await receiptFor(deliverable, run) : undefined;
      const modelingEligibility = deliverable?.modelingEligibilityRef
        ? await readJson<StandardizationModelingEligibilityProjection>(deliverable.modelingEligibilityRef, '可建模结论')
        : undefined;
      if (deliverable) await validateLinkedLifecycle(deliverable, run);
      const pendingCommand = deliverable
        ? pendingCommandFor(deliverable, run, state, receipt)
        : undefined;
      return {
        deliverable: clone(deliverable), run,
        ...(receipt ? { receipt } : {}),
        ...(modelingEligibility ? { modelingEligibility } : {}),
        ...(pendingCommand ? { pendingCommand } : {}),
      };
    },

    async preview({ runId, actorUserId, expectedRunRevision }) {
      const run = await input.runReader.read(runId);
      if (!run) throw new Error('标准化运行不存在');
      await requireAccess(actorUserId, run.projectId);
      if (run.revision !== expectedRunRevision) throw new Error('标准化运行 revision 已变化');
      const { state } = await load();
      const existingDeliverable = findDeliverable(state, run);
      const canReopenMergedPreview = existingDeliverable?.mode === 'MERGED_DOCUMENT';
      if (run.status !== 'READY_FOR_OUTPUT' && !canReopenMergedPreview) {
        throw new Error('只有 READY_FOR_OUTPUT 运行可以预览完整合并标准化文档');
      }
      const preview = (await buildPreview(run)).preview;
      if (existingDeliverable && existingDeliverable.mergedDocumentRef !== preview.mergedDocumentRef) {
        throw new Error('已登记交付物与完整合并预览 identity 不一致');
      }
      return clone(preview);
    },

    async readContent({ contentRef, actorUserId, cursor, limit = 20 }) {
      if (!refPattern.test(contentRef)) throw new Error('contentRef格式无效');
      if (!Number.isSafeInteger(limit) || limit < 1 || limit > 1000) throw new Error('limit必须在1到1000之间');
      const { state } = await load();
      const deliverable = state.deliverables.find((item) => [
        item.sourceManifestRef, item.mergedDocumentRef, item.decisionManifestRef,
        item.governanceAppendixRef, item.modelingArtifactRef, item.zeroDeltaReportRef,
        item.modelingEligibilityRef, item.handoffReceiptRef,
      ].includes(contentRef as ContentReference));
      if (!deliverable) throw new Error('内容不属于已登记交付物');
      const run = await input.runReader.read(deliverable.runId);
      if (!run) throw new Error('标准化运行不存在');
      await requireAccess(actorUserId, run.projectId);
      const content = await input.contentStore.get(contentRef as ContentReference);
      if (content === null || asRef(content) !== contentRef) throw new Error('交付物内容不存在或校验和不一致');
      const chunks = utf8Chunks(content, 4096);
      const offset = cursor === undefined ? 0 : Number(cursor);
      if (!Number.isSafeInteger(offset) || offset < 0 || offset > chunks.length) throw new Error('cursor无效');
      const items = chunks.slice(offset, offset + limit);
      return {
        contentRef: contentRef as ContentReference,
        items,
        nextCursor: offset + items.length < chunks.length ? String(offset + items.length) : null,
        total: chunks.length,
      };
    },

    async execute(command) {
      const fingerprint = commandFingerprint(command);
      const initial = await load();
      const prior = Object.hasOwn(initial.state.commands, command.commandId)
        ? initial.state.commands[command.commandId]
        : undefined;
      if (prior) {
        if (prior.fingerprint !== fingerprint) throw new Error('commandId已用于不同交付物命令');
        const deliverable = initial.state.deliverables.find((item) => item.deliverableId === prior.deliverableId);
        const run = await input.runReader.read(prior.runId);
        if (!deliverable || !run) throw new Error('幂等命令结果不存在');
        const access = await requireAccess(command.actorUserId, run.projectId);
        if (command.type === 'GENERATE_DELIVERABLE' || command.type === 'AUTHOR_CONFIRM') {
          if (run.createdBy !== command.actorUserId
            || (access.role !== 'EDITOR' && access.role !== 'ADMIN')) {
            throw new Error('只有运行作者且角色为 Editor/Admin 可以恢复交付物命令');
          }
        } else if (command.type === 'AUTHOR_CONFIRM_AND_FREEZE') {
          if (command.actorUserId !== deliverable.authorUserId && access.role !== 'ADMIN') {
            throw new Error('只有作者或 active Admin 可以恢复作者定版');
          }
        } else if (command.type === 'REVIEW_AND_FREEZE') {
          if (command.actorUserId === deliverable.authorUserId
            || (access.role !== 'REVIEWER' && access.role !== 'ADMIN')) {
            throw new Error('必须由异于作者的 active Reviewer/Admin 恢复审核定版');
          }
        } else if (command.actorUserId !== deliverable.authorUserId && access.role !== 'ADMIN') {
          throw new Error('只有作者或 active Admin 可以恢复M4交接');
        }
        await validateLinkedLifecycle(deliverable, run);
        if (command.type === 'GENERATE_DELIVERABLE') {
          return finishGeneratedLink(command, deliverable, run, true);
        }
        return finishLifecycleLink(command, deliverable, run, true);
      }

      const run = await input.runReader.read(command.runId);
      if (!run) throw new Error('标准化运行不存在');
      const access = await requireAccess(command.actorUserId, run.projectId);
      if (command.type !== 'GENERATE_DELIVERABLE') {
        const deliverable = findDeliverable(initial.state, run);
        if (!deliverable || deliverable.deliverableId !== command.deliverableId) {
          throw new Error('当前运行的交付物不存在或identity不一致');
        }
        if (deliverable.revision !== command.expectedDeliverableRevision) {
          throw new Error('交付物 revision 已变化');
        }
        if (deliverable.linkState !== 'LINKED') throw new Error('交付物前序Run事件尚未补登');
        await validateLinkedLifecycle(deliverable, run);
        if (command.type === 'AUTHOR_CONFIRM') {
          if (deliverable.status !== 'GENERATED_AWAITING_AUTHOR') throw new Error('当前交付物不能提交审核');
          if (run.createdBy !== command.actorUserId
            || (access.role !== 'EDITOR' && access.role !== 'ADMIN')) {
            throw new Error('只有运行作者且角色为 Editor/Admin 可以确认交付物');
          }
          await validateCore(deliverable, run, false);
          const confirmedAt = now();
          deliverable.authorConfirmation = {
            reviewId: `review-${deliverable.deliverableId}`,
            actorUserId: command.actorUserId, confirmedAt, coreSha256: deliverable.coreSha256,
          };
          deliverable.status = 'AWAITING_INDEPENDENT_REVIEW';
          deliverable.linkState = 'REVIEW_EVENT_PENDING';
          deliverable.revision += 1;
          deliverable.updatedAt = confirmedAt;
        } else if (command.type === 'AUTHOR_CONFIRM_AND_FREEZE') {
          if (!['GENERATED_AWAITING_AUTHOR', 'AWAITING_INDEPENDENT_REVIEW'].includes(deliverable.status)) {
            throw new Error('当前交付物不能由作者直接定版');
          }
          const authorCanFreeze = run.createdBy === command.actorUserId
            && (access.role === 'EDITOR' || access.role === 'ADMIN');
          const adminCanCompleteLegacyReview = deliverable.status === 'AWAITING_INDEPENDENT_REVIEW'
            && access.role === 'ADMIN';
          if (!authorCanFreeze && !adminCanCompleteLegacyReview) {
            throw new Error('只有运行作者且角色为 Editor/Admin 可以确认结果并定版');
          }
          // A declared gap is a valid governance result. Integrity and all
          // decisions remain mandatory, but semantic delta is not a freeze gate.
          await validateCore(deliverable, run, false);
          const confirmedAt = now();
          deliverable.authorFreezeConfirmation = {
            confirmationId: `author-freeze-${deliverable.deliverableId}`,
            actorUserId: command.actorUserId,
            confirmedAt,
            coreSha256: deliverable.coreSha256,
            inputDeliverableRevision: deliverable.revision,
          };
          deliverable.status = 'FROZEN';
          deliverable.linkState = 'FROZEN_EVENT_PENDING';
          deliverable.revision += 1;
          deliverable.updatedAt = confirmedAt;
        } else if (command.type === 'REVIEW_AND_FREEZE') {
          if (deliverable.status !== 'AWAITING_INDEPENDENT_REVIEW') throw new Error('当前交付物不能定版');
          if (command.actorUserId === deliverable.authorUserId
            || (access.role !== 'REVIEWER' && access.role !== 'ADMIN')) {
            throw new Error('必须由异于作者的 active Reviewer/Admin 审核');
          }
          await validateCore(deliverable, run, false);
          const reviewedAt = now();
          deliverable.reviewerApproval = {
            reviewId: deliverable.authorConfirmation!.reviewId,
            actorUserId: command.actorUserId, reviewedAt, coreSha256: deliverable.coreSha256,
          };
          deliverable.status = 'FROZEN';
          deliverable.linkState = 'FROZEN_EVENT_PENDING';
          deliverable.revision += 1;
          deliverable.updatedAt = reviewedAt;
        } else if (command.type === 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING') {
          if (deliverable.status !== 'FROZEN') throw new Error('请先确认结果并定版');
          if (command.actorUserId !== deliverable.authorUserId && access.role !== 'ADMIN') {
            throw new Error('只有作者或 active Admin 可以交接标准化文档');
          }
          if (!deliverable.modelingEligibilityRef) {
            throw new Error('可建模结论暂时无法读取，请重新生成标准化结果');
          }
          const verified = await validateCore(deliverable, run, false);
          const handedOffAt = now();
          const excludedConclusionCount = verified.eligibility.excludedCandidateIds.length
            + verified.eligibility.conclusions.filter((item) => !item.candidateId && item.eligibility !== 'ELIGIBLE').length;
          const receipt: StandardizationDocumentHandoffReceipt = {
            kind: 'STANDARDIZATION_DOCUMENT_HANDOFF', schemaVersion: 1,
            receiptId: `standardization-handoff-${deliverable.coreSha256.slice(7, 23)}`,
            runId: run.runId, deliverableId: deliverable.deliverableId,
            artifactId: verified.artifact.artifactId,
            mergedDocumentRef: deliverable.mergedDocumentRef,
            modelingEligibilityRef: deliverable.modelingEligibilityRef,
            eligibleConclusionCount: verified.eligibility.eligibleCandidateIds.length,
            excludedConclusionCount,
            handedOffBy: command.actorUserId, handedOffAt,
          };
          deliverable.handoffReceiptRef = await put(receipt);
          deliverable.status = 'HANDED_OFF';
          deliverable.linkState = 'HANDOFF_EVENT_PENDING';
          deliverable.revision += 1;
          deliverable.updatedAt = handedOffAt;
        } else {
          if (deliverable.status !== 'FROZEN') throw new Error('只有已冻结交付物可以零变化交接');
          if (command.actorUserId !== deliverable.authorUserId && access.role !== 'ADMIN') {
            throw new Error('只有作者或 active Admin 可以交接M4');
          }
          const verified = await validateCore(deliverable, run, true);
          const handedOffAt = now();
          const receipt: ZeroDeltaModelingHandoffReceipt = {
            kind: 'ZERO_DELTA_MODELING_HANDOFF', schemaVersion: 1,
            receiptId: `zero-delta-receipt-${deliverable.coreSha256.slice(7, 23)}`,
            runId: run.runId, deliverableId: deliverable.deliverableId,
            protectedArtifactId: verified.baseline.artifact.artifactId,
            protectedCatalogId: verified.baseline.catalogId,
            artifactId: verified.artifact.artifactId,
            artifactMarkdownSha256: verified.artifact.markdown.sha256,
            semanticPayloadSha256: verified.artifact.semanticPayload!.sha256,
            governanceAppendixRef: deliverable.governanceAppendixRef,
            zeroDeltaReportRef: deliverable.zeroDeltaReportRef,
            handedOffBy: command.actorUserId, handedOffAt,
          };
          deliverable.handoffReceiptRef = await put(receipt);
          deliverable.status = 'HANDED_OFF';
          deliverable.linkState = 'HANDOFF_EVENT_PENDING';
          deliverable.revision += 1;
          deliverable.updatedAt = handedOffAt;
        }
        initial.state.commands[command.commandId] = {
          fingerprint, runId: run.runId, deliverableId: deliverable.deliverableId, type: command.type,
        };
        await commit(initial.snapshot.version, initial.state);
        return finishLifecycleLink(command, deliverable, run);
      }
      if (run.revision !== command.expectedRunRevision) throw new Error('标准化运行 revision 已变化');
      if (run.status !== 'READY_FOR_OUTPUT') throw new Error('只有 READY_FOR_OUTPUT 运行可以生成交付物');
      if (run.createdBy !== command.actorUserId
        || (access.role !== 'EDITOR' && access.role !== 'ADMIN')) {
        throw new Error('只有运行作者且角色为 Editor/Admin 可以生成交付物');
      }
      if (findDeliverable(initial.state, run)) throw new Error('当前运行已经生成交付物');

      const mode = command.mode ?? 'MERGED_DOCUMENT';
      if (mode !== 'SOURCE_DOCUMENT_SET' && mode !== 'MERGED_DOCUMENT') {
        throw new Error('交付物展示模式无效');
      }
      if (command.mode === 'MERGED_DOCUMENT' && !command.expectedPreviewSha256) {
        throw new Error('MERGED_DOCUMENT 生成必须绑定完整合并预览 SHA');
      }
      const builtPreview = await buildPreview(run);
      const { preview, baseline, resolutions, governanceAppendix } = builtPreview;
      if (command.expectedPreviewSha256 && command.expectedPreviewSha256 !== preview.previewSha256) {
        throw new Error('完整合并预览 SHA 已变化，请重新核对后再生成标准化结果');
      }
      const { sourceManifest, mergedDocument, decisionManifest } = preview;
      const projected = await input.modelingProjector.project({ run, baseline, resolutions, mergedDocument });
      if (modelingDocumentSemanticPayloadSha256(projected.semanticPayload)
        !== projected.artifact.semanticPayload?.sha256) {
        throw new Error('新 Artifact semantic payload 与声明SHA不一致');
      }
      assertModelingDocumentIntegrity(projected.artifact);
      if (projected.zeroDeltaReport.semanticDifferences.length === 0
        && projected.zeroDeltaReport.catalogChanges.length !== 0) {
        throw new Error('受保护 Catalog 内容与descriptor漂移');
      }
      const modelingEligibility = projectModelingEligibility(projected.artifact);
      assertModelingEligibilityProjection(modelingEligibility, projected.artifact);

      const [sourceManifestRef, mergedDocumentRef, decisionManifestRef, governanceAppendixRef,
        modelingArtifactRef, zeroDeltaReportRef, modelingEligibilityRef] = await Promise.all([
        put(sourceManifest), put(mergedDocument), put(decisionManifest), put(governanceAppendix),
        put(projected.artifact), put(projected.zeroDeltaReport), put(modelingEligibility),
      ]);
      if (mergedDocumentRef !== preview.mergedDocumentRef) {
        throw new Error('实际保存的合并标准化文档与预览 identity 不一致');
      }
      const coreSha256 = asRef(canonicalModelingJson(coreFor({
        runId: run.runId, scenarioKey: run.scenarioKey,
        sourceManifestRef, mergedDocumentRef, decisionManifestRef, governanceAppendixRef,
        modelingArtifactRef, zeroDeltaReportRef, modelingEligibilityRef,
      }, baseline)));
      const deliverableId = `standardization-deliverable-${coreSha256.slice(7, 23)}`;
      const createdAt = now();
      const deliverable: StandardizationDeliverable = {
        schemaVersion: 1,
        deliverableId,
        runId: run.runId,
        scenarioKey: run.scenarioKey,
        projectId: run.projectId,
        revision: 1,
        mode,
        status: 'GENERATED_AWAITING_AUTHOR',
        linkState: 'GENERATED_EVENT_PENDING',
        authorUserId: run.createdBy,
        sourceManifestRef, mergedDocumentRef, decisionManifestRef, governanceAppendixRef,
        modelingArtifactRef, zeroDeltaReportRef, modelingEligibilityRef, coreSha256,
        createdAt, updatedAt: createdAt,
      };
      initial.state.deliverables.push(deliverable);
      initial.state.commands[command.commandId] = {
        fingerprint, runId: run.runId, deliverableId, type: command.type,
      };
      await commit(initial.snapshot.version, initial.state);
      return finishGeneratedLink(command, deliverable, run);
    },
  };
}
