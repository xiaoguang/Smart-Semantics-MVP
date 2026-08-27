import { Fragment, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { EyeOutlined, SettingOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Empty, Input, Spin, Tag, message } from 'antd';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { useCurrentUser } from '../ai-modeling/current-user-context.tsx';
import { createCollaborationRuntime } from '../collaboration/runtime.ts';
import { createIndexedDbContentStore } from '../source-documents/content-store.ts';
import { createSourceDocumentRuntime } from '../source-documents/runtime.ts';
import type { ContentReference } from '../source-documents/types.ts';
import { useSourceManagement } from '../source-management/source-management-context.tsx';
import SourceCenter from '../source-management/source-center.tsx';
import { connectorTypeById } from '../source-management/connector-catalog.ts';
import type { SourceConnection, SourceConnectionRevision } from '../source-management/types.ts';
import BackAction from '../../components/back-action.tsx';
import {
  executeSourceBoardCommand,
  guanyijiaSourceBoardSelectionStorageKey,
  projectSourceBoard,
  sourceBoardBindings,
  type SourceBoardCommand,
  type SourceCandidateInstance,
  type SourceRole,
  validateSourceBoardStart,
} from '../source-management/source-board.ts';
import { createStandardizationRunRuntime } from '../standardization-run/runtime.ts';
import { canonicalModelingJson, standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import type { ModelingDocumentArtifact, ModelingDocumentSection } from '../modeling-document-bridge/types.ts';
import { createBrowserDeliverableMetadataStore } from '../standardization-deliverable/metadata-store.ts';
import { createStandardizationDeliverableRuntime } from '../standardization-deliverable/runtime.ts';
import { createStandardizationModelingProjector } from '../standardization-deliverable/semantic-projection.ts';
import type {
  DeliverableSnapshot,
  MergedStandardizationDocument,
  StandardizationRunReader,
  StandardizationModelingHandoffReceipt,
} from '../standardization-deliverable/types.ts';
import type { StandardizationModelingEligibilityProjection } from '../standardization-deliverable/modeling-eligibility.ts';
import type { StandardizationRunCommand } from '../standardization-run/types.ts';
import { useReviewSurface } from '../review-surface/use-review-surface.ts';
import { projectReviewLayout, reviewSurfaceViewportForLayout } from '../review-surface/review-layout.ts';
import {
  assertReviewMutationAllowed,
  blockingReviewWindowFailures,
  clearReviewWindowFailure as clearReviewWindowFailureForStream,
  ReviewWindowError,
  setReviewWindowFailure,
  type ReviewWindowPage,
  type ReviewWindowStream,
} from '../review-window/index.ts';
import {
  acknowledgeVerifiedStreamTarget,
  recoverVerifiedStreamTarget,
  type VerifiedStreamRecoveryTarget,
} from '../review-window/recovery-consumer.ts';
import type {
  ConflictResolutionArtifact,
  ConflictResolutionPreview,
  ConflictResolutionStrategy,
} from '../guanyijia-standardization-story/types.ts';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';
import {
  candidateReviewTopicForBlock,
} from '../guanyijia-evidence-factory/candidate-review-projection.ts';
import {
  projectSourceReviewVisibility,
} from '../guanyijia-evidence-factory/source-review-visibility.ts';
import {
  projectSourceReviewWorkspace,
  readSourceReviewDocument,
  readSourceStandardDocument,
  projectSourceStandardDocumentRevision,
  reviewEvidenceViewForClaim,
  type ReviewClaim,
  type ReviewCompletedFormalDecision,
  type ReviewEvidenceView,
  type SourceReviewTrace,
} from '../guanyijia-evidence-factory/source-review-document.ts';
import { readDemoContentSourceConfiguration } from '../guanyijia-demo-content/demo-content-review.ts';
import {
  createGuanyijiaWorkbenchRuntime,
  continueCurrentDocumentReviewAfterApply,
  factsInspectorFor,
  isLatestDocumentMarkdownRequest,
  isLatestConflictPreviewRequest,
  openCurrentDocumentReview,
  pageCurrentDocumentBlocks,
  projectCurrentDocumentBlockWindow,
  projectGuanyijiaReviewShellSnapshot,
  reviseEditableStructuredValue,
  structuredValueEditorModel,
  validateGuanyijiaCorroborationReceipt,
  validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions,
  validateGuanyijiaResolutionArtifactAgainstPersistedRevisions,
  validateGuanyijiaAssistantArtifactAgainstPersistedRun,
  validateGuanyijiaAssistantConfirmationAgainstPersistedRevision,
  type DocumentReviewNavigationState,
  type GuanyijiaWorkbenchCommand,
  type GuanyijiaWorkbenchSnapshot,
  type StandardizationFactsInspectorModel,
  type WorkbenchTimelineItem,
} from './guanyijia-workbench-runtime.ts';
import StandardizationFactsInspector from './standardization-facts-inspector.tsx';
import SourceDocumentReadable, { SourceReviewEvidenceDetails } from './source-document-readable.tsx';
import StandardizedDocumentReader from './standardized-document-reader.tsx';
import {
  projectStandardizedDocument,
  type StandardizedDocumentClaim,
  type StandardizedDocumentView,
} from './standardized-document-projection.ts';
import { buildHumanReadableEvidence, buildSourceDocumentTraceLinks, displaySourceName, sourceOriginLabel } from './human-readable-evidence.ts';
import StandardizationTimeline from './standardization-timeline.tsx';
import {
  projectBusinessJourneyTimeline,
  type JourneySource,
  type SourceCheckpointStatus,
} from './standardization-experience-projection.ts';
import {
  auditReasonForBusinessDecision,
  businessConflictDecisionOptions,
  legacyStrategyForBusinessDecision,
  projectBusinessMarkdownDiff,
  resolutionSummaryForBusinessDecision,
  type BusinessConflictDecision,
} from './business-conflict-decision.ts';
import { CrossSourceFindingList } from './cross-source-finding-list.tsx';
import { businessErrorMessage } from './business-error-message.ts';
import { buildWorkbenchActions, sourceSnapshotAction } from './workbench-action-model.ts';
import { projectReviewPrimaryAction, resolveReviewResultSection } from './review-workspace-actions.ts';
import ReviewAssistantPanel from './review-assistant-panel.tsx';
import { classifyReviewAssistantIntent } from './review-assistant.ts';
import type {
  ReviewAssistantContextSelection,
  ReviewAssistantHistoryItem,
  ReviewAssistantTarget,
} from './review-assistant-types.ts';
import {
  createAssistantHistoryRecoveryController,
  type AssistantHistoryContext,
} from './assistant-history-recovery.ts';
import {
  assistantHistoryContextFor,
  projectAssistantHistoryRecoveryState,
} from './assistant-history-context.ts';
import {
  commitAssistantAskDeltaResult,
  type AssistantHistoryCommitPayload,
  type AssistantHistoryOrchestratorResult,
} from './assistant-history-orchestrator.ts';

import {
  assistantHistoryUiScopeKey,
  reduceAssistantHistoryUiState,
  type AssistantHistoryUiState,
} from './assistant-history-ui-state.ts';

function sourceCheckpointStatus(status: string): SourceCheckpointStatus {
  return status === 'READING'
    || status === 'DOCUMENT_READY'
    || status === 'REVIEWED'
    || status === 'CONFLICT_BLOCKED'
    || status === 'ALIGNED'
    ? status
    : 'PENDING';
}

/**
 * The rich V6 reader preserves its source-specific types internally.  The
 * standardized-document reader deliberately needs fewer visual categories:
 * objects expand into structure, gaps state their limitation, and every other
 * verified source statement is a readable rule or behaviour.  Keeping this
 * conversion here prevents the checklist projection from being widened just
 * because the delivery document has richer prose.
 */
function standardizedDocumentKind(kind: string | undefined): StandardizedDocumentClaim['kind'] {
  if (kind === 'OBJECT') return 'OBJECT';
  if (kind === 'GAP') return 'GAP';
  return 'RULE';
}

const demoRoleBySourceId: Record<string, SourceRole> = {
  guanyijia_mysql: 'DATABASE',
  guanyijia_github: 'CODE_REPOSITORY',
  guanyijia_official_docs: 'OFFICIAL_DOCUMENTS',
  guanyijia_demo_policy: 'ERP_POLICY',
  guanyijia_semantica_demo: 'TERMINOLOGY',
};

const demoSourceInstanceMetadata = {
  guanyijia_mysql: {
    businessName: 'ERP交易库', adapterTypeId: 'mysql', adapterLabel: 'MySQL · jsh_erp', environmentLabel: '生产',
    locationPreview: 'jsh_erp', scopePreview: '95张表 · 2个视图 · 35个过程', connectionId: 'guanyijia_mysql',
  },
  guanyijia_github: {
    businessName: 'ERP应用源码库', adapterTypeId: 'github', adapterLabel: 'GitHub · jishenghua/jshERP', environmentLabel: '固定提交',
    locationPreview: 'jishenghua/jshERP', scopePreview: '719个已跟踪文件 · 固定提交', connectionId: 'guanyijia_github',
  },
  guanyijia_official_docs: {
    businessName: 'ERP业务规范库', adapterTypeId: 'markdown', adapterLabel: 'Markdown文档集 · 3篇',
    locationPreview: '已冻结演示资料', scopePreview: '3篇业务规范文档',
  },
  guanyijia_demo_policy: {
    businessName: 'ERP管理制度库', adapterTypeId: 'markdown', adapterLabel: 'Markdown文档集 · 5篇',
    locationPreview: '已冻结演示资料', scopePreview: '5篇管理制度文档',
  },
  guanyijia_semantica_demo: {
    businessName: '企业术语库', adapterTypeId: 'semantica', adapterLabel: 'JSON／RDF图谱 · 54术语／75关系',
    locationPreview: '派生演示图谱', scopePreview: '54个术语 · 75条关系', connectionId: 'guanyijia_semantica',
  },
} as const;

function sourceNameForDemoSource(sourceId: string) {
  return demoSourceInstanceMetadata[sourceId as keyof typeof demoSourceInstanceMetadata]?.businessName ?? sourceId;
}

function demoSourceBoardCandidates(): SourceCandidateInstance[] {
  return readDemoContentSourceConfiguration().map((source) => ({
    instanceId: `demo-content:${source.sourceId}`,
    sourceId: source.sourceId,
    sourceRole: demoRoleBySourceId[source.sourceId]!,
    displayName: sourceNameForDemoSource(source.sourceId),
    ...demoSourceInstanceMetadata[source.sourceId as keyof typeof demoSourceInstanceMetadata],
    availability: 'CONNECTED',
    snapshotReadiness: 'READY',
    compatibleStoryKeys: ['guanyijia-five-source-v1'],
    formalSnapshotId: source.formalSnapshotId,
    contentSnapshotId: source.contentSourceSnapshotId,
  }));
}

function sourceRoleForAdapter(adapterTypeId: string): SourceRole | undefined {
  if (adapterTypeId === 'mysql' || adapterTypeId === 'postgresql') return 'DATABASE';
  if (adapterTypeId === 'github') return 'CODE_REPOSITORY';
  if (adapterTypeId === 'sharepoint') return 'OFFICIAL_DOCUMENTS';
  if (adapterTypeId === 'semantica') return 'TERMINOLOGY';
  return undefined;
}

function managedConnectionCandidate(connection: SourceConnection, revision?: SourceConnectionRevision): SourceCandidateInstance | undefined {
  const sourceRole = sourceRoleForAdapter(connection.connectorTypeId);
  if (!sourceRole) return undefined;
  const adapter = connectorTypeById(connection.connectorTypeId);
  const locationPreview = typeof revision?.sanitizedConfig.repository === 'string'
    ? revision.sanitizedConfig.repository
    : typeof revision?.sanitizedConfig.endpoint === 'string' ? revision.sanitizedConfig.endpoint : undefined;
  const scopePreview = Object.values(revision?.defaultScope ?? {}).flat().filter((value): value is string => typeof value === 'string').join(' · ') || undefined;
  return {
    instanceId: `connection:${connection.connectionId}`, sourceId: connection.connectionId, connectionId: connection.connectionId,
    sourceRole, displayName: connection.displayName, businessName: connection.displayName,
    adapterTypeId: connection.connectorTypeId, adapterLabel: adapter?.displayName ?? connection.connectorTypeId,
    environmentLabel: connection.environment || undefined, locationPreview, scopePreview,
    availability: connection.state === 'READY' ? 'CONNECTED' : connection.state === 'DRAFT' ? 'NEEDS_CONFIGURATION' : connection.state === 'ERROR' ? 'CONNECTION_FAILED' : 'DISCONNECTED',
    snapshotReadiness: 'MISSING', compatibleStoryKeys: ['guanyijia-five-source-v1'],
  };
}

function defaultSourceBoardSelection(candidates: SourceCandidateInstance[]): Partial<Record<SourceRole, string>> {
  const selection: Partial<Record<SourceRole, string>> = {};
  for (const candidate of candidates) {
    if (!selection[candidate.sourceRole]) selection[candidate.sourceRole] = candidate.instanceId;
  }
  return selection;
}

function restoreSourceBoardSelection(candidates: SourceCandidateInstance[]): Partial<Record<SourceRole, string>> {
  const fallback = defaultSourceBoardSelection(candidates);
  try {
    const raw = localStorage.getItem(guanyijiaSourceBoardSelectionStorageKey);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const allowed = new Set(candidates.map((candidate) => candidate.instanceId));
    const roles = new Set<SourceRole>(['DATABASE', 'CODE_REPOSITORY', 'OFFICIAL_DOCUMENTS', 'ERP_POLICY', 'TERMINOLOGY']);
    const selection = Object.fromEntries(Object.entries(parsed).flatMap(([role, instanceId]) => (
      roles.has(role as SourceRole) && typeof instanceId === 'string' && allowed.has(instanceId)
        ? [[role, instanceId]]
        : []
    ))) as Partial<Record<SourceRole, string>>;
    return selection;
  } catch {
    return fallback;
  }
}

const evidenceStatusLabel = {
  FACT: '已确认结论',
  INFERENCE: '待核对结论',
  GAP: '资料缺口',
  CONFLICT: '来源差异',
} as const;

function displayCuratedAffectedObject(reference: string) {
  const [kind, ...rest] = reference.split(':');
  const labels: Record<string, string> = {
    table: '数据表', procedure: '存储过程', relation: '关系', dml: '查询摘要', governance: '治理议题',
  };
  const label = labels[kind] ?? '相关对象';
  return `${label}：${rest.join(':') || '未命名'}`;
}

function blockText(value: unknown) {
  if (typeof value === 'string') return value;
  if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
    const text = (value as Record<string, unknown>).text;
    if (typeof text === 'string') return text;
  }
  return '这条结论包含已整理的信息；可查看来源材料和相关业务对象。';
}

function formatRevisionCreatedAt(createdAt: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    fractionalSecondDigits: 3,
    hour12: false,
  }).format(new Date(createdAt));
}

function commandId(actorUserId: string, snapshot: GuanyijiaWorkbenchSnapshot, type: GuanyijiaWorkbenchCommand['type']) {
  return `${snapshot.storyId}:${actorUserId}:${type}:r${snapshot.run?.revision ?? 0}`;
}

function recoveryTargetForSummary(
  stream: ReviewWindowStream,
  item?: Pick<ReviewWindowPage['items'][number], 'stableKey' | 'contentRef' | 'contentSha256'>
    | Pick<WorkbenchTimelineItem, 'itemId' | 'contentRef' | 'contentSha256'>,
): VerifiedStreamRecoveryTarget | undefined {
  if (!item?.contentRef || !item.contentSha256) return undefined;
  return {
    stream,
    stableKey: 'stableKey' in item ? item.stableKey : item.itemId,
    contentRef: item.contentRef,
    expectedSha256: item.contentSha256,
  };
}

function recoveryTargetFromError(
  stream: ReviewWindowStream,
  cause: unknown,
): VerifiedStreamRecoveryTarget | undefined {
  if (!(cause instanceof ReviewWindowError) || !cause.expectedSha256) return undefined;
  return {
    stream,
    contentRef: `sha256:${cause.expectedSha256}`,
    expectedSha256: cause.expectedSha256,
  };
}

export default function GuanyijiaStandardizationWorkbench({
  onHandoff,
}: {
  onHandoff?(
    artifact: ModelingDocumentArtifact,
    receipt: StandardizationModelingHandoffReceipt,
    eligibility?: StandardizationModelingEligibilityProjection,
  ): void;
}) {
  const { currentUser } = useCurrentUser();
  const sourceManagement = useSourceManagement();
  const contentStore = useMemo(() => createIndexedDbContentStore(), []);
  const collaboration = useMemo(() => createCollaborationRuntime(localStorage), []);
  const sourceDocuments = useMemo(() => createSourceDocumentRuntime({
    contentStore,
  }), [contentStore]);
  const story = useMemo(() => createGuanyijiaStandardizationStory(), []);
  const deliveryCapability = useMemo(() => ({}), []);
  const standardizationRuns = useMemo(() => createStandardizationRunRuntime({
    contentStore, deliveryCapability,
    resolutionArtifactValidator: (artifact, run) => (
      validateGuanyijiaResolutionArtifactAgainstPersistedRevisions({
        artifact, run, sourceDocuments, story,
      })
    ),
    corroborationReceiptValidator: (receipt, run) => (
      validateGuanyijiaCorroborationReceipt({ receipt, run, story })
    ),
    corroborationReceiptSetValidator: (projection, run) => (
      validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions({
        projection, run, sourceDocuments, story,
      })
    ),
    assistantArtifactValidator: (turn, proposal, run, projection) => (
      validateGuanyijiaAssistantArtifactAgainstPersistedRun({
        turn, proposal, run, sourceDocuments, story, eventIndex: projection.eventIndex,
      })
    ),
    assistantPatchConfirmationValidator: (confirmation, run, projection) => (
      validateGuanyijiaAssistantConfirmationAgainstPersistedRevision({
        confirmation, run, sourceDocuments, story, contentStore, projection,
      })
    ),
  }), [contentStore, deliveryCapability, sourceDocuments, story]);
  const runtime = useMemo(() => createGuanyijiaWorkbenchRuntime({
    pointerStorage: localStorage,
    standardizationRuns,
    sourceDocuments,
    story,
    contentStore,
    accessForActor(actorUserId) {
      try {
        return {
          active: true,
          role: collaboration.getRole(actorUserId, 'erp_data_governance'),
        };
      } catch {
        return { active: false, role: 'VIEWER' };
      }
    },
  }), [collaboration, contentStore, sourceDocuments, standardizationRuns, story]);
  const deliverableMetadataStore = useMemo(() => createBrowserDeliverableMetadataStore(), []);
  const deliverables = useMemo(() => {
    const runReader: StandardizationRunReader = {
      read: (runId) => standardizationRuns.read(runId),
      readEventPayload: (runId, eventId) => standardizationRuns.readEventPayload(runId, eventId),
      async appendEvent(value) {
        const common = {
          commandId: value.commandId, runId: value.runId,
          expectedRevision: value.expectedRunRevision, actor: { userId: value.actorUserId },
          payload: value.payload, deliveryCapability,
        };
        const command: StandardizationRunCommand = value.type === 'DELIVERABLE_GENERATED'
          ? { ...common, type: 'MARK_DELIVERABLE_GENERATED', deliverableId: value.deliverableId }
          : value.type === 'REVIEW_SUBMITTED'
            ? { ...common, type: 'MARK_REVIEW_SUBMITTED', reviewId: value.eventIdentity }
            : value.type === 'DELIVERABLE_FROZEN'
              ? { ...common, type: 'MARK_DELIVERABLE_FROZEN', deliverableId: value.deliverableId }
              : { ...common, type: 'MARK_MODELING_HANDOFF_COMPLETED', handoffId: value.eventIdentity };
        return standardizationRuns.execute(command);
      },
    };
    return createStandardizationDeliverableRuntime({
      metadataStore: deliverableMetadataStore,
      contentStore,
      runReader,
      sourceDocuments,
      conflictResolutions: {
        async list(run) {
          const artifacts = await Promise.all(run.timeline.filter((event) => (
            event.type === 'CONFLICT_RESOLVED' || event.type === 'CONFLICT_DECISION_REPLACED'
          )).map(async (event) => (
            JSON.parse(await standardizationRuns.readEventPayload(run.runId, event.eventId)) as ConflictResolutionArtifact
          )));
          const currentByConflict = new Map<string, ConflictResolutionArtifact>();
          for (const artifact of artifacts) currentByConflict.set(artifact.hunk.conflictId, artifact);
          return [...currentByConflict.values()];
        },
      },
      baselineProvider: {
        async resolve(scenarioKey) {
          if (scenarioKey !== 'guanyijia-five-source-v1') return null;
          const catalog = collaboration.getCatalog('guanyijia_erp', 'V1');
          if (!catalog || catalog.catalogId !== 'catalog_guanyijia_v1'
            || catalog.fingerprint !== 'guanyijia:5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210') {
            return null;
          }
          return {
            scenarioKey,
            expectedSourceIds: story.listSources().map((source) => source.sourceId),
            expectedSourceTypes: Object.fromEntries(story.listSources().map((source) => [
              source.sourceId, source.sourceClass,
            ])),
            expectedArtifactId: 'artifact-guanyijia-v1-40c8572864bd',
            artifact: structuredClone(guanyijiaFrozenModelingArtifact),
            catalogId: catalog.catalogId,
            catalogFingerprint: catalog.fingerprint,
            catalogData: catalog.data,
            semanticPayloadSha256: 'c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248',
            markdownSha256: '5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210',
            expectedCounts: {
              entities: 14, events: 9, fields: 296, relations: 30, dimensions: 4, metrics: 5,
              hierarchies: 2, rules: 8, aliases: 10, timeRules: 9, pendingAssets: 63, exclusions: 2,
            },
            projectionContext: {
              systemCode: 'guanyijia_erp', modelSpaceId: 'guanyijia_erp',
              datasourceName: 'guanyijia_mysql', schemaName: 'jsh_erp', ownerName: 'AI 建模',
            },
          };
        },
      },
      modelingProjector: createStandardizationModelingProjector(),
      membershipReader: {
        async read(actorUserId) {
          try { return { active: true, role: collaboration.getRole(actorUserId, 'erp_data_governance') }; }
          catch { return { active: false, role: 'VIEWER' }; }
        },
      },
    });
  }, [collaboration, contentStore, deliverableMetadataStore, deliveryCapability, sourceDocuments, standardizationRuns, story]);
  const shellRef = useRef<HTMLDivElement>(null);
  const threadRef = useRef<HTMLDivElement>(null);
  const conflictPreviewRequestRef = useRef(0);
  const conflictKeyboardFocusRef = useRef<BusinessConflictDecision | undefined>(undefined);
  const assistantRequestRef = useRef(0);
  const assistantHistoryRecoveryRef = useRef<ReturnType<typeof createAssistantHistoryRecoveryController<ReviewAssistantHistoryItem>> | undefined>(undefined);
  if (!assistantHistoryRecoveryRef.current) {
    assistantHistoryRecoveryRef.current = createAssistantHistoryRecoveryController<ReviewAssistantHistoryItem>();
  }
  const assistantPatchSeenRef = useRef<string | undefined>(undefined);
  const autoOpenDocumentRef = useRef(false);
  const reviewScrollRestoreRef = useRef<number | undefined>(undefined);
  const markdownReturnRef = useRef<{
    claimId: string;
    label: string;
    scrollTop: number;
    view: 'MATTERS' | 'CONCLUSIONS';
  } | undefined>(undefined);
  const documentWasOpenRef = useRef(false);
  const workflowLayerSeenRef = useRef<string | undefined>(undefined);
  const [snapshot, setSnapshot] = useState<GuanyijiaWorkbenchSnapshot | null>(null);
  const [runStateUnavailable, setRunStateUnavailable] = useState(false);
  const [deliverableSnapshot, setDeliverableSnapshot] = useState<DeliverableSnapshot>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [interactionAnnouncement, setInteractionAnnouncement] = useState('');
  const [sourceCenterOpen, setSourceCenterOpen] = useState(false);
  const demoSourceCandidates = useMemo(() => demoSourceBoardCandidates(), []);
  const [sourceBoardSelection, setSourceBoardSelection] = useState<Partial<Record<SourceRole, string>>>(() => restoreSourceBoardSelection(demoSourceCandidates));
  const sourceBoardSelectionRestoredRef = useRef(false);
  const allDemoSourceCandidates = useMemo(() => {
    const defaults = demoSourceCandidates;
    const additional = (sourceManagement.snapshot?.connections ?? [])
      .map((connection) => managedConnectionCandidate(
        connection,
        sourceManagement.snapshot?.revisions.filter((item) => item.connectionId === connection.connectionId).sort((a, b) => b.revision - a.revision)[0],
      ))
      .filter((candidate): candidate is SourceCandidateInstance => Boolean(candidate))
      .filter((candidate) => !defaults.some((item) => item.connectionId === candidate.connectionId));
    return [...defaults, ...additional];
  }, [demoSourceCandidates, sourceManagement.snapshot]);
  const sourceBoard = useMemo(() => projectSourceBoard({
    storyKey: 'guanyijia-five-source-v1',
    locked: Boolean(snapshot?.run) || sourceManagement.loading || Boolean(sourceManagement.error) || runStateUnavailable,
    lockReason: sourceManagement.error
      ? 'SOURCE_MANAGEMENT_UNAVAILABLE'
      : runStateUnavailable
        ? 'RUN_STATE_UNAVAILABLE'
        : 'RUN_ACTIVE',
    candidates: allDemoSourceCandidates,
    bindings: sourceBoardSelection,
    sourceStatuses: snapshot?.run?.sources.map((source) => ({ sourceId: source.sourceId, status: source.status })),
  }), [allDemoSourceCandidates, runStateUnavailable, snapshot?.run, sourceBoardSelection, sourceManagement.error, sourceManagement.loading]);

  useEffect(() => {
    if (sourceManagement.loading || sourceBoardSelectionRestoredRef.current) return;
    sourceBoardSelectionRestoredRef.current = true;
    setSourceBoardSelection(restoreSourceBoardSelection(allDemoSourceCandidates));
  }, [allDemoSourceCandidates, sourceManagement.loading]);

  useEffect(() => {
    if (sourceManagement.loading || !sourceBoardSelectionRestoredRef.current) return;
    localStorage.setItem(guanyijiaSourceBoardSelectionStorageKey, JSON.stringify(sourceBoardSelection));
  }, [sourceBoardSelection, sourceManagement.loading]);
  const [selectedTimelineItemId, setSelectedTimelineItemId] = useState<string>();
  const [workflowLocateRequest, setWorkflowLocateRequest] = useState<{ checkpointId: string; requestId: number }>();
  const workflowLocateRequestIdRef = useRef(0);
  const [selectedTimelineInspector, setSelectedTimelineInspector] = useState<StandardizationFactsInspectorModel>();
  const [selectedInspectorSourceId, setSelectedInspectorSourceId] = useState<string>();
  const [historicalResolution, setHistoricalResolution] = useState<ConflictResolutionArtifact>();
  const [historicalDeliverable, setHistoricalDeliverable] = useState<{
    title: string;
    markdown: string;
  }>();
  const [replacementResolution, setReplacementResolution] = useState<ConflictResolutionArtifact>();
  const [selectedBlockId, setSelectedBlockId] = useState<string>();
  const [editingBlockId, setEditingBlockId] = useState<string>();
  const [editLabel, setEditLabel] = useState('');
  const [editValueText, setEditValueText] = useState('');
  const [editNormalized, setEditNormalized] = useState<string>();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [reviewNavigation, setReviewNavigation] = useState<DocumentReviewNavigationState>({ focusToken: 0 });
  const [historicalDocumentOpen, setHistoricalDocumentOpen] = useState(false);
  const [sectionPage, setSectionPage] = useState(1);
  const [reviewLayoutMeasurements, setReviewLayoutMeasurements] = useState({
    visualViewportWidth: 0,
    containerWidth: 0,
  });
  const [inspectorCollapsed, setInspectorCollapsed] = useState(false);
  const [documentView, setDocumentView] = useState<'MATTERS' | 'CONCLUSIONS' | 'MARKDOWN'>('MATTERS');
  const [standardizedDocumentView, setStandardizedDocumentView] = useState<StandardizedDocumentView>('READING');
  const [documentMarkdown, setDocumentMarkdown] = useState<string>();
  const [documentMarkdownLoading, setDocumentMarkdownLoading] = useState(false);
  const [documentMarkdownLoadKey, setDocumentMarkdownLoadKey] = useState<string>();
  const documentMarkdownCurrentKeyRef = useRef<string | undefined>(undefined);
  const [markdownAnchorToFocus, setMarkdownAnchorToFocus] = useState<string>();
  const [selectedCandidateEvidenceRef, setSelectedCandidateEvidenceRef] = useState<string>();
  const [selectedCuratedEvidenceRef, setSelectedCuratedEvidenceRef] = useState<string>();
  const [selectedCuratedTraceAnchor, setSelectedCuratedTraceAnchor] = useState<string>();
  const [curatedValidationRetry, setCuratedValidationRetry] = useState(0);
  const [editingScriptedEditId, setEditingScriptedEditId] = useState<string>();
  const [scriptedEditLabel, setScriptedEditLabel] = useState('');
  const [scriptedEditText, setScriptedEditText] = useState('');
  const [curatedStructuredDiffOpen, setCuratedStructuredDiffOpen] = useState(false);
  const [conflictStrategy, setConflictStrategy] = useState<ConflictResolutionStrategy>();
  const [conflictDecision, setConflictDecision] = useState<BusinessConflictDecision>();
  const [conflictPreview, setConflictPreview] = useState<ConflictResolutionPreview>();
  const [conflictPreviewLoading, setConflictPreviewLoading] = useState(false);
  const [assistantHistoryState, setAssistantHistoryState] = useState<AssistantHistoryUiState>({
    scopeKey: '', items: [], total: 0, pendingProposal: undefined,
    loaded: false, loading: false, error: undefined,
  });
  const assistantHistory = assistantHistoryState.items;
  const assistantHistoryTotal = assistantHistoryState.total;
  const assistantPendingProposal = assistantHistoryState.pendingProposal;
  const assistantHistoryLoaded = assistantHistoryState.loaded;
  const [assistantBusy, setAssistantBusy] = useState(false);
  const [reviewWindowErrors, setReviewWindowErrors] = useState<Partial<Record<ReviewWindowStream, ReviewWindowError>>>({});
  const [reviewWindowFailureTargets, setReviewWindowFailureTargets] = useState<Partial<Record<ReviewWindowStream, VerifiedStreamRecoveryTarget>>>({});
  const [timelineWindowReloadToken, setTimelineWindowReloadToken] = useState(0);
  const [documentWindowPages, setDocumentWindowPages] = useState<ReviewWindowPage[]>([]);
  const [documentWindowPageIndex, setDocumentWindowPageIndex] = useState(0);
  const [issueWindow, setIssueWindow] = useState<ReviewWindowPage>();
  const [evidenceWindow, setEvidenceWindow] = useState<ReviewWindowPage>();
  const conflictForReview = useMemo(() => {
    if (snapshot?.currentConflict) return snapshot.currentConflict;
    if (!replacementResolution || snapshot?.run?.status !== 'READY_FOR_OUTPUT' || snapshot.run.reviewId) {
      return undefined;
    }
    const definition = story.listConflictDefinitions().find((candidate) => (
      candidate.conflictId === replacementResolution.hunk.conflictId
    ));
    if (!definition) return undefined;
    return {
      conflictId: definition.conflictId,
      title: definition.title,
      hunk: replacementResolution.hunk,
      defaultStrategy: definition.defaultStrategy,
      allowedStrategies: definition.allowedStrategies,
    };
  }, [replacementResolution, snapshot?.currentConflict, snapshot?.run?.reviewId, snapshot?.run?.status, story]);
  const isReplacingHistoricalDecision = Boolean(replacementResolution && !snapshot?.currentConflict);

  const baseReviewLayout = useMemo(() => projectReviewLayout({
    ...reviewLayoutMeasurements,
    topLayer: 'TIMELINE',
  }), [reviewLayoutMeasurements]);
  const surfaceViewport = reviewSurfaceViewportForLayout(baseReviewLayout);
  const reviewSurface = useReviewSurface({
    shellRef,
    scrollRef: threadRef,
    runId: snapshot?.run?.runId ?? 'guanyijia:pending',
    runRevision: snapshot?.run?.revision ?? 0,
    cursorEpoch: snapshot?.run?.runId ?? 'guanyijia:pending',
    viewport: surfaceViewport,
  });
  const reviewLayout = useMemo(() => projectReviewLayout({
    ...reviewLayoutMeasurements,
    topLayer: reviewSurface.state.layers.at(-1)?.kind ?? 'TIMELINE',
  }), [reviewLayoutMeasurements, reviewSurface.state.layers]);
  const inspectorLayered = reviewLayout.mode !== 'INLINE';
  const compactInspector = reviewLayout.mode === 'OVERLAY';
  const mobile = reviewLayout.mode === 'FULLSCREEN';
  const documentOpen = reviewSurface.state.layers.some((layer) => layer.kind === 'DOCUMENT');
  const documentLayer = reviewSurface.state.layers.findLast((layer) => layer.kind === 'DOCUMENT');
  const inspectorOpen = reviewSurface.state.layers.some((layer) => layer.kind === 'INSPECTOR');
  const revealReviewTarget = useCallback((selector: string, announcement: string) => {
    setInteractionAnnouncement(announcement);
    window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
      const target = shellRef.current?.querySelector<HTMLElement>(selector);
      target?.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
      target?.focus({ preventScroll: true });
    }));
  }, []);
  useEffect(() => {
    const wasOpen = documentWasOpenRef.current;
    documentWasOpenRef.current = documentOpen;
    if (!wasOpen || documentOpen || reviewScrollRestoreRef.current === undefined) return;
    const restoreScrollTop = reviewScrollRestoreRef.current;
    const restore = () => {
      if (threadRef.current) threadRef.current.scrollTop = restoreScrollTop;
    };
    window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
      restore();
      window.setTimeout(restore, 0);
      reviewScrollRestoreRef.current = undefined;
    }));
  }, [documentOpen]);
  const storeCommandSnapshot = useCallback((next: GuanyijiaWorkbenchSnapshot) => {
    setSnapshot((current) => {
      const projected = projectGuanyijiaReviewShellSnapshot(next);
      const currentDocument = current?.current?.document;
      const nextDocument = projected.current?.document;
      // Command responses intentionally carry shell metadata only. Keep the
      // already verified document page mounted while a preview/answer updates
      // the shell; a changed document identity or revision must rehydrate
      // through readReviewCurrentDocument instead of reusing stale bodies.
      if (current?.current?.reviewCompilation && currentDocument && nextDocument
        && currentDocument.documentId === nextDocument.documentId
        && currentDocument.revision === nextDocument.revision
        && projected.current) {
        return {
          ...projected,
          current: { ...projected.current, reviewCompilation: current.current.reviewCompilation },
        };
      }
      return projected;
    });
  }, []);

  useEffect(() => {
    const decision = conflictKeyboardFocusRef.current;
    if (!decision || conflictPreviewLoading || conflictDecision !== decision || !conflictPreview) return;
    const frame = window.requestAnimationFrame(() => {
      shellRef.current?.querySelector<HTMLElement>(`[data-conflict-decision="${decision}"]`)?.focus({ preventScroll: true });
      conflictKeyboardFocusRef.current = undefined;
    });
    return () => window.cancelAnimationFrame(frame);
  }, [conflictDecision, conflictPreview, conflictPreviewLoading]);
  const assistantDraft = reviewSurface.state.assistantDraft.value;
  const selectedSection = reviewNavigation.selectedSection ?? 'OVERVIEW';
  const currentDocumentBlocks = snapshot?.current?.reviewCompilation?.blocks
    ?? snapshot?.current?.compilation?.blocks;

  const recordReviewWindowFailure = useCallback((
    stream: ReviewWindowStream,
    cause: unknown,
    fallback: string,
    target?: VerifiedStreamRecoveryTarget,
  ) => {
    const error = cause instanceof ReviewWindowError ? cause : new ReviewWindowError({
      code: 'CONTENT_UNAVAILABLE',
      message: businessErrorMessage(cause, fallback),
    });
    if (!(cause instanceof ReviewWindowError)) setError(businessErrorMessage(cause, fallback));
    setReviewWindowFailureTargets((current) => target && !current[stream]
      ? { ...current, [stream]: target }
      : current);
    setReviewWindowErrors((current) => setReviewWindowFailure(current, stream, error));
  }, []);
  const clearReviewWindowFailure = useCallback((stream: ReviewWindowStream) => {
    setReviewWindowErrors((current) => clearReviewWindowFailureForStream(current, stream));
    setReviewWindowFailureTargets((current) => {
      if (!current[stream]) return current;
      const next = { ...current };
      delete next[stream];
      return next;
    });
  }, []);

  const verifyReviewContent = useCallback(async (
    runId: string,
    contentRef: ContentReference,
    expectedSha256 = contentRef.slice('sha256:'.length),
  ) => runtime.readReviewContent({
    actorUserId: currentUser.userId, runId, contentRef, expectedSha256,
  }), [currentUser.userId, runtime]);

  const projectBatch = sourceManagement.snapshot?.defaultBatch.projectId === 'guanyijia_erp'
    ? sourceManagement.snapshot.defaultBatch
    : undefined;
  const timeline = snapshot?.timeline;
  const reviewFocusTargetId = reviewNavigation.focusTargetId;

  useEffect(() => {
    let cancelled = false;
    setBusy(true);
    runtime.readReviewShell(currentUser.userId)
      .then((next) => { if (!cancelled) {
        if (next.nextAction.type === 'REVIEW_DOCUMENT') autoOpenDocumentRef.current = true;
        setSnapshot(next); setRunStateUnavailable(false); setError(undefined);
      } })
      .catch((cause) => { if (!cancelled) {
        setRunStateUnavailable(true);
        setError(businessErrorMessage(cause, '标准化运行暂时无法读取，请刷新后重试。'));
      } })
      .finally(() => { if (!cancelled) setBusy(false); });
    return () => { cancelled = true; };
  }, [currentUser.userId, runtime]);

  useEffect(() => {
    const runId = snapshot?.run?.runId;
    if (!runId) { setDeliverableSnapshot(undefined); return; }
    let cancelled = false;
    deliverables.read({ runId, actorUserId: currentUser.userId })
      .then((next) => { if (!cancelled) setDeliverableSnapshot(next); })
      .catch((cause) => { if (!cancelled) setError(businessErrorMessage(cause, '标准化结果暂时无法读取，请刷新后重试。')); });
    return () => { cancelled = true; };
  }, [currentUser.userId, deliverables, snapshot?.run?.revision, snapshot?.run?.runId]);

  useEffect(() => {
    const element = shellRef.current;
    if (!element) return;
    const update = () => {
      const visualViewport = window.visualViewport;
      const visualViewportWidth = visualViewport?.width ?? window.innerWidth;
      const containerWidth = element.clientWidth;
      element.style.setProperty('--guanyijia-visual-width', `${visualViewportWidth}px`);
      element.style.setProperty('--guanyijia-visual-height', `${visualViewport?.height ?? window.innerHeight}px`);
      element.style.setProperty('--guanyijia-visual-left', `${visualViewport?.offsetLeft ?? 0}px`);
      element.style.setProperty('--guanyijia-visual-top', `${visualViewport?.offsetTop ?? 0}px`);
      document.documentElement.style.setProperty('--guanyijia-visual-width', `${visualViewportWidth}px`);
      document.documentElement.style.setProperty('--guanyijia-visual-height', `${visualViewport?.height ?? window.innerHeight}px`);
      document.documentElement.style.setProperty('--guanyijia-visual-left', `${visualViewport?.offsetLeft ?? 0}px`);
      document.documentElement.style.setProperty('--guanyijia-visual-top', `${visualViewport?.offsetTop ?? 0}px`);
      setReviewLayoutMeasurements((current) => (
        current.visualViewportWidth === visualViewportWidth && current.containerWidth === containerWidth
          ? current
          : { visualViewportWidth, containerWidth }
      ));
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(element);
    window.visualViewport?.addEventListener('resize', update);
    window.visualViewport?.addEventListener('scroll', update);
    // Some embedded Chromium shells update visualViewport during pinch zoom
    // without emitting a resize event. Keep the review layer tied to the
    // actual visible frame so a document that was opened wide becomes a
    // fullscreen layer as soon as the available reading area narrows.
    const visualViewportPoll = window.setInterval(update, 250);
    return () => {
      observer.disconnect();
      window.visualViewport?.removeEventListener('resize', update);
      window.visualViewport?.removeEventListener('scroll', update);
      window.clearInterval(visualViewportPoll);
      for (const property of ['--guanyijia-visual-width', '--guanyijia-visual-height', '--guanyijia-visual-left', '--guanyijia-visual-top']) {
        element.style.removeProperty(property);
        document.documentElement.style.removeProperty(property);
      }
    };
  }, []);

  useEffect(() => {
    if (!timeline?.length) return;
    const current = timeline.findLast((item) => item.state === 'CURRENT') ?? timeline.at(-1)!;
    setSelectedTimelineItemId(current.itemId);
    setSelectedTimelineInspector(current.inspector);
    setSelectedInspectorSourceId(current.sourceId ?? current.inspector?.sourceId);
  }, [timeline]);

  useEffect(() => {
    const conflict = conflictForReview;
    const runId = snapshot?.run?.runId;
    if (!conflict || !runId) {
      setConflictStrategy(undefined);
      setConflictDecision(undefined);
      setConflictPreview(undefined);
      setConflictPreviewLoading(false);
      return;
    }
    const options = businessConflictDecisionOptions({
      conflictId: conflict.conflictId,
      allowedStrategies: conflict.allowedStrategies,
    });
    const decision = options.find((option) => option.default)?.decision ?? options[0]?.decision;
    if (!decision) {
      setConflictStrategy(undefined);
      setConflictDecision(undefined);
      setConflictPreview(undefined);
      setConflictPreviewLoading(false);
      return;
    }
    const strategy = legacyStrategyForBusinessDecision(conflict.conflictId, decision);
    setConflictStrategy(strategy);
    setConflictDecision(decision);
    setConflictPreview(undefined);
    setConflictPreviewLoading(true);
    let cancelled = false;
    const requestId = ++conflictPreviewRequestRef.current;
    runtime.previewCurrentConflict({ runId, conflictId: conflict.conflictId, strategy })
      .then((preview) => {
        if (!cancelled && conflictPreviewRequestRef.current === requestId) setConflictPreview(preview);
      })
      .catch((cause) => { if (!cancelled) setError(businessErrorMessage(cause, '来源差异预览暂时无法生成，请重新选择决定。')); })
      .finally(() => {
        if (!cancelled && conflictPreviewRequestRef.current === requestId) setConflictPreviewLoading(false);
      });
    return () => { cancelled = true; };
  }, [conflictForReview, runtime, snapshot?.run?.runId]);

  useEffect(() => {
    setSelectedBlockId(undefined);
  }, [snapshot?.current?.source.sourceId, snapshot?.current?.document?.documentId]);

  useEffect(() => {
    const context = assistantHistoryContextFor(snapshot, currentUser.userId);
    const controller = assistantHistoryRecoveryRef.current!;
    if (!context) {
      controller.clear();
      setAssistantHistoryState({
        scopeKey: '', items: [], total: 0, pendingProposal: undefined,
        loaded: false, loading: false, error: undefined,
      });
      return;
    }
    const initial = controller.begin(context);
    const projected = projectAssistantHistoryRecoveryState(initial);
    const scopeKey = assistantHistoryUiScopeKey(context);
    setAssistantHistoryState((current) => reduceAssistantHistoryUiState(current, {
      scopeKey,
      items: projected.items,
      total: projected.total,
      pendingProposal: projected.pendingProposal,
      loaded: projected.historyLoaded,
      loading: initial.loading,
      error: initial.error,
    }));
    if (projected.historyLoaded) return;
    setAssistantHistoryState((current) => reduceAssistantHistoryUiState(current, {
      scopeKey,
      items: [],
      total: current.total,
      pendingProposal: current.pendingProposal,
      loaded: current.loaded,
      loading: true,
      error: undefined,
    }));
    let cancelled = false;
    void controller.read(context, async (currentContext) => {
      const summaryPage = await runtime.readReviewWindow({
        actorUserId: currentContext.actorUserId,
        runId: currentContext.runId,
        epoch: currentContext.requestEpoch,
        stream: 'ASSISTANT_HISTORY',
        filter: 'all',
        direction: 'BACKWARD',
      });
      return {
        items: [],
        total: summaryPage.total,
        nextCursor: summaryPage.nextCursor ?? undefined,
      };
    }, 'SUMMARY').then((state) => {
      if (cancelled || state.contextKey !== controller.contextKey(context)) return;
      const nextProjection = projectAssistantHistoryRecoveryState(state);
      setAssistantHistoryState((current) => reduceAssistantHistoryUiState(current, {
        scopeKey,
        items: nextProjection.items,
        total: nextProjection.total,
        pendingProposal: nextProjection.pendingProposal,
        loaded: nextProjection.historyLoaded,
        loading: state.loading,
        error: state.error,
      }));
      if (state.error) recordReviewWindowFailure('ASSISTANT_HISTORY', state.error, '审阅助手历史读取失败');
    });
    return () => { cancelled = true; };
  }, [clearReviewWindowFailure, currentUser.userId, recordReviewWindowFailure, reviewWindowFailureTargets, runtime,
    snapshot?.current?.document?.documentId, snapshot?.current?.document?.revision,
    snapshot?.current?.source.sourceId, snapshot?.run?.revision, snapshot?.run?.runId]);

  useEffect(() => {
    const run = snapshot?.run;
    const document = snapshot?.current?.document;
    if (!documentOpen || !run || !document?.blocksRef) {
      setDocumentWindowPages([]);
      setDocumentWindowPageIndex(0);
      return;
    }
    let cancelled = false;
    runtime.readReviewCurrentDocument({
      actorUserId: currentUser.userId,
      runId: run.runId,
      documentId: document.documentId,
      epoch: `revision:${run.revision}`,
      filter: `section:${selectedSection}`,
    }).then(({ window, current }) => {
      if (cancelled) return;
      setSnapshot((value) => value ? { ...value, current } : value);
      setDocumentWindowPages([window]);
      setDocumentWindowPageIndex(0);
      acknowledgeVerifiedStreamTarget({
        recordedTarget: reviewWindowFailureTargets.DOCUMENT_BLOCKS,
        verifiedTarget: {
          contentRef: document.blocksRef!,
          expectedSha256: document.blocksRef!.slice('sha256:'.length),
        },
        onVerified: () => clearReviewWindowFailure('DOCUMENT_BLOCKS'),
      });
    }).catch((cause) => {
      if (!cancelled) recordReviewWindowFailure('DOCUMENT_BLOCKS', cause, '来源文档窗口读取失败');
    });
    return () => { cancelled = true; };
  }, [
    clearReviewWindowFailure, currentUser.userId, documentOpen, recordReviewWindowFailure,
    reviewWindowFailureTargets.DOCUMENT_BLOCKS, runtime, selectedSection,
    snapshot?.current?.document?.documentId, snapshot?.current?.document?.blocksRef,
    snapshot?.run?.revision, snapshot?.run?.runId, timelineWindowReloadToken,
  ]);

  useLayoutEffect(() => {
    const document = snapshot?.current?.document;
    documentMarkdownCurrentKeyRef.current = document
      ? `document:${document.documentId}:r${document.revision}`
      : undefined;
    setDocumentView('MATTERS');
    setStandardizedDocumentView('READING');
    setDocumentMarkdown(undefined);
    setDocumentMarkdownLoading(false);
    setDocumentMarkdownLoadKey(undefined);
    setMarkdownAnchorToFocus(undefined);
    setSelectedCandidateEvidenceRef(undefined);
    setSelectedCuratedEvidenceRef(undefined);
    setSelectedCuratedTraceAnchor(undefined);
    setEditingScriptedEditId(undefined);
    setScriptedEditLabel('');
    setScriptedEditText('');
    setCuratedStructuredDiffOpen(false);
  }, [snapshot?.current?.document?.documentId, snapshot?.current?.document?.revision]);

  const sourceReviewSource = snapshot?.current?.source;
  const sourceReviewContentBinding = snapshot?.contentBinding;
  const sourceReviewRead = useMemo(() => {
    // A failed integrity check may be retried against the same frozen input.
    // The token deliberately causes a fresh validation without changing the
    // run, document identity, or selected source.
    void curatedValidationRetry;
    const source = sourceReviewSource;
    if (!source) {
      return { document: undefined, validationFailed: false };
    }
    try {
      if (!sourceReviewContentBinding) throw new Error('当前运行未绑定冻结内容快照');
      const document = readSourceReviewDocument({
          sourceId: source.sourceId,
          snapshotId: source.snapshotId,
          contentBinding: sourceReviewContentBinding,
        });
      return { document, validationFailed: false };
    } catch {
      return { document: undefined, validationFailed: true };
    }
  }, [
    curatedValidationRetry,
    sourceReviewContentBinding,
    sourceReviewSource,
  ]);
  const curatedReview = sourceReviewRead.document;
  const curatedValidationFailed = sourceReviewRead.validationFailed;
  const sourceStandardDocumentRead = useMemo(() => {
    // Rich V6 material is an optional, reader-only delivery projection.  It
    // must be exact-bound to this run and never becomes input for the outer
    // review checklist.
    void curatedValidationRetry;
    const source = sourceReviewSource;
    if (!source || !sourceReviewContentBinding) {
      return { document: undefined, validationFailed: false };
    }
    try {
      return {
        document: readSourceStandardDocument({
          sourceId: source.sourceId,
          snapshotId: source.snapshotId,
          contentBinding: sourceReviewContentBinding,
        }),
        validationFailed: false,
      };
    } catch {
      return { document: undefined, validationFailed: true };
    }
  }, [curatedValidationRetry, sourceReviewContentBinding, sourceReviewSource]);
  const sourceStandardDocument = sourceStandardDocumentRead.document;
  const standardizedDocumentValidationFailed = sourceStandardDocumentRead.validationFailed;
  const currentReviewSource = sourceReviewSource;
  const sourceReviewVisibility = useMemo(() => snapshot?.run && currentReviewSource
    ? projectSourceReviewVisibility({
        currentSourceId: currentReviewSource.sourceId,
        currentConflictId: snapshot.currentConflict?.conflictId,
        sources: snapshot.run.sources.map((step) => ({
          sourceId: step.sourceId,
          snapshotId: step.snapshotId ?? '',
          status: step.status,
        })),
      })
    : undefined, [currentReviewSource, snapshot?.currentConflict?.conflictId, snapshot?.run]);
  const usesCuratedReview = Boolean(curatedReview);
  const isMysqlCuratedReview = curatedReview?.sourceId === 'guanyijia_mysql';
  const scriptedReviewEdits = snapshot?.scriptedEdits ?? [];
  const pendingScriptedReviewEdits = scriptedReviewEdits.filter((edit) => edit.status === 'PENDING');
  const sourceReviewSupportsScriptedEdits = scriptedReviewEdits.length > 0;
  const completedFormalReviewDecisions = useMemo<ReviewCompletedFormalDecision[]>(() => (
    (snapshot?.resolutions ?? []).map((resolution) => {
      const decision: BusinessConflictDecision = resolution.strategy === 'KEEP_CURRENT'
        ? 'KEEP_CURRENT'
        : resolution.strategy === 'ACCEPT_INCOMING'
          ? 'ACCEPT_INCOMING'
          : 'REGISTER_GAP';
      const summary = resolutionSummaryForBusinessDecision({
        conflictId: resolution.hunk.conflictId,
        decision,
      });
      return {
        decisionId: resolution.resolutionId,
        sourceId: resolution.sourceId,
        title: resolution.hunk.title,
        statement: `${summary.title}。${summary.gap}`,
      };
    })
  ), [snapshot?.resolutions]);
  const curatedWorkspace = useMemo(() => curatedReview
    ? projectSourceReviewWorkspace({
        sourceDocument: curatedReview,
        currentBlocks: currentDocumentBlocks ?? [],
        scriptedDecisions: scriptedReviewEdits,
        formalDecisions: completedFormalReviewDecisions,
      })
    : undefined, [completedFormalReviewDecisions, curatedReview, currentDocumentBlocks, scriptedReviewEdits]);
  const revisedSourceStandardDocument = useMemo(() => sourceStandardDocument
    ? projectSourceStandardDocumentRevision(sourceStandardDocument, currentDocumentBlocks ?? [])
    : undefined, [currentDocumentBlocks, sourceStandardDocument]);
  const standardizedDocument = useMemo(() => {
    if (!snapshot?.current?.document) return undefined;
    if (revisedSourceStandardDocument) {
      const hasStableReaderContexts = revisedSourceStandardDocument.traceLinks.length > 0
        && revisedSourceStandardDocument.traceLinks.every((trace) => typeof trace.claimId === 'string' && trace.claimId.length > 0);
      const standardSections = revisedSourceStandardDocument.standardSections.map((section) => {
        const savedNarrative = revisedSourceStandardDocument.sections?.[section.sectionId];
        const narrative = hasStableReaderContexts && typeof savedNarrative === 'string'
          ? savedNarrative.trim()
          : undefined;
        return {
          id: section.sectionId,
          title: section.title,
          purpose: section.purpose,
          ...(narrative ? { narrative } : {}),
        };
      });
      const claims: StandardizedDocumentClaim[] = revisedSourceStandardDocument.claims.map((claim) => ({
        claimId: claim.claimId,
        sectionId: claim.sectionId,
        sectionTitle: standardSections.find((section) => section.id === claim.sectionId)?.title ?? claim.sectionId,
        sectionPurpose: claim.sectionPurpose,
        markdownAnchor: claim.markdownAnchor,
        title: claim.title,
        statement: claim.statement,
        ...(claim.focusIdentifiers.length ? { fields: [...claim.focusIdentifiers] } : {}),
        ...(claim.detailViews.length ? { detailViews: claim.detailViews } : {}),
        ...(claim.schemaEvidences.length ? { schemaEvidences: claim.schemaEvidences } : {}),
        kind: standardizedDocumentKind(claim.kind),
        ...(claim.kind === 'GAP' ? { nextStep: '补充资料或由业务负责人确认。' } : {}),
      }));
      return projectStandardizedDocument({
        revisionLabel: `第 ${snapshot.current.document.revision} 版`,
        markdownSource: revisedSourceStandardDocument.markdown,
        standardSections,
        claims,
      });
    }
    if (!curatedWorkspace) return undefined;
    const gaps = new Set(curatedWorkspace.checklist.gaps.map((gap) => gap.claimId));
    const standardSections = curatedReview?.standardSections?.every((section) => (
      Boolean(section.sectionId?.trim()) && Boolean(section.purpose?.trim())
    ))
      ? curatedReview.standardSections.map((section) => ({
          id: section.sectionId!,
          title: section.title,
          purpose: section.purpose!,
        }))
      : undefined;
    const claims: StandardizedDocumentClaim[] = curatedWorkspace.claims.map((claim) => {
      const section = curatedWorkspace.sections.find((candidate) => candidate.index === claim.section);
      const schemaEvidences = claim.evidenceRefs
        .map((reference) => reviewEvidenceViewForClaim(curatedWorkspace.evidenceViews, reference, claim.claimId))
        .flatMap((view) => view?.kind === 'MYSQL_SCHEMA' ? [{
          objectName: view.objectName,
          ...(view.objectComment ? { objectComment: view.objectComment } : {}),
          columns: view.columns.map((column) => ({ ...column })),
          rawDdl: view.rawDdl,
          locationValue: view.locationValue,
        }] : []);
      const detailViews = claim.evidenceRefs
        .map((reference) => reviewEvidenceViewForClaim(curatedWorkspace.evidenceViews, reference, claim.claimId))
        .filter((view): view is ReviewEvidenceView => view !== undefined);
      return {
        claimId: claim.claimId,
        sectionId: claim.sectionId,
        sectionTitle: section?.title ?? `第 ${claim.section} 章`,
        sectionPurpose: claim.sectionPurpose,
        markdownAnchor: claim.markdownAnchor,
        title: claim.title,
        statement: claim.statement,
        ...(claim.focusIdentifiers.length ? { fields: [...claim.focusIdentifiers] } : {}),
        ...(detailViews.length ? { detailViews } : {}),
        ...(schemaEvidences.length ? { schemaEvidences } : {}),
        kind: gaps.has(claim.claimId) ? 'GAP'
          : claim.sectionId === 'object-relations' ? 'RELATION'
            : claim.sectionId === 'fields-dimensions' || claim.sectionId === 'metrics' ? 'RULE'
              : 'OBJECT',
        ...(gaps.has(claim.claimId) ? { nextStep: curatedWorkspace.checklist.gaps.find((gap) => gap.claimId === claim.claimId)?.nextStep } : {}),
      };
    });
    return projectStandardizedDocument({
      revisionLabel: `第 ${snapshot.current.document.revision} 版`,
      markdownSource: curatedWorkspace.markdown.sourceContent,
      ...(standardSections ? { standardSections } : {}),
      claims,
    });
  }, [curatedReview?.standardSections, curatedWorkspace, revisedSourceStandardDocument, snapshot?.current?.document]);
  const editingScriptedReviewEdit = scriptedReviewEdits.find((edit) => edit.definition.editId === editingScriptedEditId);
  const selectedInspectorMatchesCurrentSource = !selectedInspectorSourceId
    || selectedInspectorSourceId === snapshot?.current?.source.sourceId;
  const selectedCuratedEvidence = curatedReview?.evidence.find((evidence) => (
    evidence.evidenceRef === selectedCuratedEvidenceRef
  ));
  const selectedCuratedClaimId = curatedWorkspace?.claims.find((claim) => (
    claim.markdownAnchor === selectedCuratedTraceAnchor
  ))?.claimId;
  const selectedCuratedEvidenceView = curatedWorkspace && selectedCuratedEvidenceRef
    ? reviewEvidenceViewForClaim(curatedWorkspace.evidenceViews, selectedCuratedEvidenceRef, selectedCuratedClaimId)
    : undefined;

  useEffect(() => {
    if (!editingScriptedEditId) return;
    let frame = window.requestAnimationFrame(() => {
      frame = window.requestAnimationFrame(() => {
        const editor = shellRef.current?.querySelector<HTMLElement>(`[data-scripted-edit-id="${editingScriptedEditId}"]`);
        const firstField = editor?.querySelector<HTMLElement>('input, textarea');
        editor?.scrollIntoView({ block: 'center', behavior: 'smooth' });
        firstField?.focus({ preventScroll: true });
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [editingScriptedEditId]);
  const curatedInspectorModel = selectedInspectorMatchesCurrentSource
    && curatedReview && selectedCuratedEvidence && currentReviewSource
    ? {
        sourceId: currentReviewSource.sourceId,
        sourceName: currentReviewSource.sourceName,
        sourceClass: currentReviewSource.sourceClass,
        snapshotId: currentReviewSource.snapshotId,
        versionRef: '冻结来源审阅投影',
        authority: currentReviewSource.authority,
        readSummary: `${curatedWorkspace?.metadata.coverageLabel ?? curatedReview.coverageLabel}；${sourceReviewSupportsScriptedEdits
          ? `${scriptedReviewEdits.length}项建议需要核对，可按页面提示处理。`
          : '当前没有建议核对项。'}`,
        objectCount: selectedCuratedEvidence.affectedObjectRefs.length,
        evidenceCount: curatedReview.evidence.length,
        lineage: { status: currentReviewSource.lineageStatus, upstreamSourceNames: [] },
        block: {
          blockId: `curated:${selectedCuratedEvidence.evidenceRef}`,
          label: selectedCuratedEvidence.title,
          evidenceStatus: selectedCuratedEvidence.evidenceClass === 'GAP' ? 'GAP' as const : 'FACT' as const,
          locator: { [selectedCuratedEvidence.locationLabel]: selectedCuratedEvidence.locationValue },
          affectedObjects: selectedCuratedEvidence.affectedObjectRefs.map(displayCuratedAffectedObject),
          readableEvidence: {
            evidenceRef: selectedCuratedEvidence.evidenceRef,
            title: selectedCuratedEvidence.title,
            sourceType: selectedCuratedEvidence.excerptKind === 'STRUCTURED_RECORD'
              ? 'CODE' as const
              : selectedCuratedEvidence.locationValue.includes('migration')
                ? 'SQL' as const
                : 'DDL' as const,
            excerpt: selectedCuratedEvidence.excerpt,
            locationLabel: selectedCuratedEvidence.locationLabel,
            locationValue: selectedCuratedEvidence.locationValue,
            affectedObjectRefs: selectedCuratedEvidence.affectedObjectRefs.map(displayCuratedAffectedObject),
            technicalDetails: {},
            hiddenTechnicalDetails: {
              evidenceRef: selectedCuratedEvidence.evidenceRef,
              evidenceClass: selectedCuratedEvidence.evidenceClass,
              excerptSha256: selectedCuratedEvidence.excerptSha256,
              authority: currentReviewSource.authority,
            },
          },
        },
      }
    : undefined;

  useEffect(() => {
    if (!snapshot?.current?.document?.documentId || !curatedWorkspace) return;
    const section = resolveReviewResultSection({
      selectedSection: reviewNavigation.selectedSection,
      claims: curatedWorkspace.claims,
      pendingClaimIds: pendingScriptedReviewEdits.map((edit) => edit.definition.reviewClaimId),
    });
    if (!section || section === reviewNavigation.selectedSection) return;
    setReviewNavigation((current) => ({ ...current, selectedSection: section }));
  }, [curatedWorkspace, pendingScriptedReviewEdits, reviewNavigation.selectedSection, snapshot?.current?.document?.documentId]);

  useEffect(() => {
    if (!documentOpen || !reviewFocusTargetId) return;
    window.requestAnimationFrame(() => {
      const target = document.getElementById(reviewFocusTargetId);
      target?.scrollIntoView({ block: 'start', behavior: 'smooth' });
      target?.focus({ preventScroll: true });
    });
  }, [documentOpen, reviewFocusTargetId, reviewNavigation.focusToken, snapshot?.current?.reviewCompilation?.sourceId, snapshot?.current?.compilation?.sourceId]);

  const updateSourceBoard = (command: SourceBoardCommand) => {
    if (sourceManagement.error) {
      const detail = '运行来源暂时无法读取，请恢复来源配置后再修改。';
      setError(detail);
      message.error(detail);
      return;
    }
    const result = executeSourceBoardCommand({ model: sourceBoard, command });
    if (!result.ok) {
      message.warning(result.reason);
      return;
    }
    setSourceBoardSelection(sourceBoardBindings(result.model));
  };

  const execute = async (type: 'START_RUN' | 'READ_NEXT_SOURCE' | 'COMPLETE_CURRENT_DOCUMENT_REVIEW') => {
    if (!snapshot) return;
    if (type === 'COMPLETE_CURRENT_DOCUMENT_REVIEW' && !allowReviewMutation('PATCH')) return;
    if (type === 'COMPLETE_CURRENT_DOCUMENT_REVIEW' && pendingScriptedReviewEdits.length) {
      message.info('请先核对本来源的建议修改，再完成审阅');
      return;
    }
    if (type === 'START_RUN') {
      const validation = validateSourceBoardStart(sourceBoard);
      if (!validation.ok) {
        setSourceCenterOpen(true);
        message.error(validation.reason);
        return;
      }
    }
    setBusy(true); setError(undefined);
    try {
      let next = await runtime.execute({
        type,
        commandId: commandId(currentUser.userId, snapshot, type),
        expectedRevision: snapshot.run?.revision ?? 0,
        actorUserId: currentUser.userId,
        ...(type === 'COMPLETE_CURRENT_DOCUMENT_REVIEW' && snapshot.current?.document ? {
          documentId: snapshot.current.document.documentId,
          documentRevision: snapshot.current.document.revision,
        } : {}),
      });
      if (type === 'START_RUN' && next.run?.status === 'READY') {
        autoOpenDocumentRef.current = true;
        next = await runtime.execute({
          type: 'READ_NEXT_SOURCE',
          commandId: `${commandId(currentUser.userId, snapshot, type)}:first-source`,
          expectedRevision: next.run.revision,
          actorUserId: currentUser.userId,
        });
      }
      if (type === 'READ_NEXT_SOURCE') autoOpenDocumentRef.current = true;
      storeCommandSnapshot(next);
      if (type === 'READ_NEXT_SOURCE' || type === 'START_RUN') message.success(`${next.current?.source ? displaySourceName(next.current.source.sourceId, next.current.source.sourceName) : '来源'}快照已载入，来源文档已生成`);
      if (type === 'COMPLETE_CURRENT_DOCUMENT_REVIEW') {
        if (documentOpen) reviewSurface.close();
        if (next.run?.status === 'READY' && next.nextAction.type === 'READ_NEXT_SOURCE') {
          autoOpenDocumentRef.current = true;
          next = await runtime.execute({
            type: 'READ_NEXT_SOURCE',
            commandId: `${commandId(currentUser.userId, snapshot, type)}:next-source`,
            expectedRevision: next.run.revision,
            actorUserId: currentUser.userId,
          });
          storeCommandSnapshot(next);
          // Open the freshly compiled document from the command result itself.
          // This avoids racing the old document layer's revision migration.
          window.requestAnimationFrame(() => openSourceDocument(next));
          message.success(`${displaySourceName(next.current?.source.sourceId ?? '', next.current?.source.sourceName)}快照已载入，来源文档已生成`);
        } else {
          message.success(next.run?.status === 'CONFLICT_BLOCKED' ? '文档审阅完成，发现需要处理的来源差异' : '本份来源文档已完成审阅');
        }
      }
    } catch (cause) {
      const detail = businessErrorMessage(cause, '当前操作暂时无法完成，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const loadDocumentMarkdown = useCallback(async () => {
    const run = snapshot?.run;
    const document = snapshot?.current?.document;
    if (usesCuratedReview || !run || !document?.markdownRef) return;
    const loadKey = `document:${document.documentId}:r${document.revision}`;
    if (!isLatestDocumentMarkdownRequest(documentMarkdownCurrentKeyRef.current, loadKey)) return;
    setDocumentMarkdownLoadKey(loadKey);
    setDocumentMarkdownLoading(true);
    try {
      const verified = await runtime.readReviewContent({
        actorUserId: currentUser.userId,
        runId: run.runId,
        contentRef: document.markdownRef,
        expectedSha256: document.markdownRef.slice('sha256:'.length),
      });
      if (!isLatestDocumentMarkdownRequest(documentMarkdownCurrentKeyRef.current, loadKey)) return;
      setDocumentMarkdown(verified.content);
      clearReviewWindowFailure('DOCUMENT_BLOCKS');
    } catch (cause) {
      if (!isLatestDocumentMarkdownRequest(documentMarkdownCurrentKeyRef.current, loadKey)) return;
      recordReviewWindowFailure('DOCUMENT_BLOCKS', cause, '来源文档正文读取失败', {
        stream: 'DOCUMENT_BLOCKS',
        contentRef: document.markdownRef,
        expectedSha256: document.markdownRef.slice('sha256:'.length),
      });
    } finally {
      if (!isLatestDocumentMarkdownRequest(documentMarkdownCurrentKeyRef.current, loadKey)) return;
      setDocumentMarkdownLoading(false);
    }
  }, [clearReviewWindowFailure, currentUser.userId, recordReviewWindowFailure, runtime,
    snapshot?.current?.document, snapshot?.run, usesCuratedReview]);

  useEffect(() => {
    const document = snapshot?.current?.document;
    if (usesCuratedReview || !documentOpen || documentView !== 'MARKDOWN' || !document?.markdownRef) return;
    const loadKey = `document:${document.documentId}:r${document.revision}`;
    if (documentMarkdownLoadKey === loadKey) return;
    void loadDocumentMarkdown();
  }, [documentMarkdownLoadKey, documentOpen, documentView,
    loadDocumentMarkdown, snapshot?.current?.document, usesCuratedReview]);

  const openStandardizationHandoff = async (next: DeliverableSnapshot) => {
    const receipt = next.receipt;
    if (!receipt || !next.deliverable) throw new Error('标准化文档暂时无法打开，请稍后重试。');
    const page = await deliverables.readContent({
      contentRef: next.deliverable.modelingArtifactRef,
      actorUserId: currentUser.userId, limit: 1000,
    });
    onHandoff?.(JSON.parse(page.items.join('')) as ModelingDocumentArtifact, receipt, next.modelingEligibility);
  };

  const executeDeliverable = async (
    type: 'GENERATE_DELIVERABLE' | 'AUTHOR_CONFIRM_AND_FREEZE'
      | 'HANDOFF_ZERO_DELTA_TO_M4' | 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING',
  ) => {
    if (!snapshot?.run) return;
    if (!allowReviewMutation('DELIVERABLE_APPROVAL')) return;
    setBusy(true); setError(undefined);
    try {
      const current = deliverableSnapshot?.deliverable;
      const common = {
        type, runId: snapshot.run.runId, actorUserId: currentUser.userId,
        commandId: `${snapshot.storyId}:${currentUser.userId}:${type}:run-r${snapshot.run.revision}:deliverable-r${current?.revision ?? 0}`,
      } as const;
      const next = type === 'GENERATE_DELIVERABLE'
        ? await deliverables.execute({ ...common, type, mode: 'MERGED_DOCUMENT', expectedRunRevision: snapshot.run.revision })
        : await deliverables.execute({
            ...common, type, deliverableId: current!.deliverableId,
            expectedDeliverableRevision: current!.revision,
          });
      setDeliverableSnapshot(next);
      setSnapshot(await runtime.readReviewShell(currentUser.userId));
      if (type === 'GENERATE_DELIVERABLE') message.success('标准化结果已生成，请确认内容。');
      if (type === 'AUTHOR_CONFIRM_AND_FREEZE') message.success('结果已定版。');
      if (type === 'HANDOFF_ZERO_DELTA_TO_M4' || type === 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING') {
        await openStandardizationHandoff(next);
        message.success('标准化文档已交给 AI 建模；缺口已从模型候选中排除。');
      }
    } catch (cause) {
      const detail = businessErrorMessage(cause, '标准化结果暂时无法保存，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const resumePendingDeliverable = async () => {
    const pendingCommand = deliverableSnapshot?.pendingCommand;
    if (!pendingCommand || pendingCommand.actorUserId !== currentUser.userId) return;
    if (!allowReviewMutation('DELIVERABLE_APPROVAL')) return;
    setBusy(true); setError(undefined);
    try {
      const next = await deliverables.execute(pendingCommand);
      setDeliverableSnapshot(next);
      setSnapshot(await runtime.readReviewShell(currentUser.userId));
      if (pendingCommand.type === 'HANDOFF_ZERO_DELTA_TO_M4'
        || pendingCommand.type === 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING') {
        await openStandardizationHandoff(next);
      }
      message.success('已继续完成上一步。');
    } catch (cause) {
      const detail = businessErrorMessage(cause, '上一步暂时无法继续，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const clearPreview = () => setSnapshot((current) => current?.preview || current?.scriptedEditPreviewSha256
    ? { ...current, preview: undefined, scriptedEditPreviewSha256: undefined }
    : current);

  const currentEditChange = () => {
    const block = currentDocumentBlocks?.find((candidate) => candidate.blockId === editingBlockId);
    if (!block) throw new Error('当前结构化块不存在');
    if (curatedReview) {
      throw new Error('当前来源只能处理已列出的建议，不能直接编辑其他结论');
    }
    return {
      blockId: block.blockId,
      label: editLabel,
      value: reviseEditableStructuredValue(block.value, editValueText, editNormalized),
    };
  };

  const displayedReviewDocumentTarget = () => {
    const document = snapshot?.current?.document;
    return document ? {
      documentId: document.documentId,
      documentRevision: document.revision,
    } : {};
  };

  const previewEdit = async () => {
    if (!snapshot?.run) return;
    if (!allowReviewMutation('PATCH')) return;
    setBusy(true); setError(undefined);
    try {
      const change = currentEditChange();
      const next = await runtime.execute({
        type: 'PREVIEW_CURRENT_BLOCK_CHANGE',
        commandId: `${commandId(currentUser.userId, snapshot, 'PREVIEW_CURRENT_BLOCK_CHANGE')}:${change.blockId}`,
        expectedRevision: snapshot.run.revision,
        actorUserId: currentUser.userId,
        ...displayedReviewDocumentTarget(),
        change,
      });
      storeCommandSnapshot(next);
    } catch (cause) {
      const detail = businessErrorMessage(cause, '修改预览暂时无法生成，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const applyEdit = async () => {
    if (!snapshot?.run || !editingBlockId) return;
    if (!allowReviewMutation('PATCH')) return;
    setBusy(true); setError(undefined);
    try {
      const change = currentEditChange();
      const digest = sha256HexSync(canonicalModelingJson(change)).slice(0, 12);
      const next = await runtime.execute({
        type: 'APPLY_CURRENT_BLOCK_CHANGE',
        commandId: `${commandId(currentUser.userId, snapshot, 'APPLY_CURRENT_BLOCK_CHANGE')}:${change.blockId}:${digest}`,
        expectedRevision: snapshot.run.revision,
        actorUserId: currentUser.userId,
        ...displayedReviewDocumentTarget(),
        change,
      });
      const navigation = continueCurrentDocumentReviewAfterApply(next, {
        ...reviewNavigation,
        selectedBlockId: editingBlockId,
      }, editingBlockId);
      storeCommandSnapshot(next);
      setReviewNavigation(navigation);
      setSelectedBlockId(editingBlockId);
      setEditingBlockId(undefined);
      setHistoryOpen(false);
      message.success('修改已应用，已生成新的文档版本');
    } catch (cause) {
      const detail = businessErrorMessage(cause, '修改暂时无法保存，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const openScriptedReviewEditor = (editId: string) => {
    const edit = scriptedReviewEdits.find((candidate) => candidate.definition.editId === editId);
    const task = curatedWorkspace?.tasks.find((candidate) => candidate.taskId === editId);
    if (!edit || edit.status !== 'PENDING') return;
    if (!task) {
      setError('建议核对项缺少可见审阅结论');
      return;
    }
    setEditingScriptedEditId(editId);
    setScriptedEditLabel(task.recommendation.title);
    setScriptedEditText(task.recommendation.statement);
    clearPreview();
    revealReviewTarget(
      `[data-scripted-edit-id="${editId}"]`,
      `已打开“${task.current.title}”的修改项，请核对并调整可修改内容。`,
    );
  };

  const keepScriptedReviewEdit = async (editId: string) => {
    if (!snapshot?.run) return;
    if (!allowReviewMutation('PATCH')) return;
    setBusy(true); setError(undefined);
    try {
      const next = await runtime.execute({
        type: 'DECIDE_SCRIPTED_REVIEW_EDIT',
        commandId: `${commandId(currentUser.userId, snapshot, 'DECIDE_SCRIPTED_REVIEW_EDIT')}:${editId}:keep`,
        expectedRevision: snapshot.run.revision,
        actorUserId: currentUser.userId,
        ...displayedReviewDocumentTarget(),
        editId,
        decision: 'KEEP_CURRENT',
      });
      storeCommandSnapshot(next);
      setEditingScriptedEditId(undefined);
      clearPreview();
      setInteractionAnnouncement('已保留当前结论；可以继续完成本来源审阅。');
      message.success('已核对并保留当前结论');
    } catch (cause) {
      const detail = businessErrorMessage(cause, '暂时无法保存当前结论，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const scriptedPatchForEditor = () => {
    if (!editingScriptedReviewEdit) throw new Error('当前没有建议核对项');
    const { editableFields } = editingScriptedReviewEdit.definition;
    return {
      ...(editableFields.includes('label') ? { label: scriptedEditLabel } : {}),
      ...(editableFields.includes('text') ? { text: scriptedEditText } : {}),
    };
  };

  const previewScriptedReviewEdit = async () => {
    if (!snapshot?.run || !editingScriptedReviewEdit) return;
    if (!allowReviewMutation('PATCH')) return;
    setBusy(true); setError(undefined);
    try {
      const next = await runtime.execute({
        type: 'PREVIEW_SCRIPTED_REVIEW_EDIT',
        commandId: `${commandId(currentUser.userId, snapshot, 'PREVIEW_SCRIPTED_REVIEW_EDIT')}:${editingScriptedReviewEdit.definition.editId}`,
        expectedRevision: snapshot.run.revision,
        actorUserId: currentUser.userId,
        ...displayedReviewDocumentTarget(),
        editId: editingScriptedReviewEdit.definition.editId,
        patch: scriptedPatchForEditor(),
      });
      storeCommandSnapshot(next);
      setInteractionAnnouncement('已生成修改预览；请核对名称和 Markdown 段落。');
    } catch (cause) {
      const detail = businessErrorMessage(cause, '修改预览暂时无法生成，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const applyScriptedReviewEdit = async () => {
    if (!snapshot?.run || !editingScriptedReviewEdit || !snapshot.scriptedEditPreviewSha256) return;
    if (!allowReviewMutation('PATCH')) return;
    setBusy(true); setError(undefined);
    try {
      const editId = editingScriptedReviewEdit.definition.editId;
      const next = await runtime.execute({
        type: 'DECIDE_SCRIPTED_REVIEW_EDIT',
        commandId: `${commandId(currentUser.userId, snapshot, 'DECIDE_SCRIPTED_REVIEW_EDIT')}:${editId}:apply`,
        expectedRevision: snapshot.run.revision,
        actorUserId: currentUser.userId,
        ...displayedReviewDocumentTarget(),
        editId,
        decision: 'APPLY_SUGGESTION',
        patch: scriptedPatchForEditor(),
        expectedPreviewSha256: snapshot.scriptedEditPreviewSha256,
      });
      const navigation = continueCurrentDocumentReviewAfterApply(next, {
        ...reviewNavigation,
        selectedBlockId: editingScriptedReviewEdit.definition.formalBlockId,
      }, editingScriptedReviewEdit.definition.formalBlockId);
      storeCommandSnapshot(next);
      setReviewNavigation(navigation);
      setSelectedBlockId(editingScriptedReviewEdit.definition.formalBlockId);
      setEditingScriptedEditId(undefined);
      setHistoryOpen(false);
      revealReviewTarget(
        `[data-review-claim="${editingScriptedReviewEdit.definition.reviewClaimId}"]`,
        '修改已确认，审阅事项、审阅结论和标准化文档已同步更新。',
      );
      message.success('建议修改已应用，已生成新的文档版本');
    } catch (cause) {
      const detail = businessErrorMessage(cause, '建议修改暂时无法保存，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const selectConflictDecision = async (
    decision: BusinessConflictDecision,
    options?: { restoreKeyboardFocus?: boolean },
  ) => {
    if (!snapshot?.run || !conflictForReview) return;
    const strategy = legacyStrategyForBusinessDecision(conflictForReview.conflictId, decision);
    if (!conflictForReview.allowedStrategies.includes(strategy)) return;
    if (options?.restoreKeyboardFocus) conflictKeyboardFocusRef.current = decision;
    setConflictDecision(decision);
    setConflictStrategy(strategy);
    setConflictPreviewLoading(true);
    setInteractionAnnouncement('已选择决定，正在更新 Markdown 预览。');
    const requestId = ++conflictPreviewRequestRef.current;
    setError(undefined);
    try {
      const preview = await runtime.previewCurrentConflict({
        runId: snapshot.run.runId,
        conflictId: conflictForReview.conflictId,
        strategy,
      });
      if (isLatestConflictPreviewRequest(conflictPreviewRequestRef.current, requestId)) {
        setConflictPreview(preview);
        setInteractionAnnouncement('已更新决定后的 Markdown 预览。');
      }
    } catch (cause) {
      if (isLatestConflictPreviewRequest(conflictPreviewRequestRef.current, requestId)) {
        const detail = businessErrorMessage(cause, '来源差异预览暂时无法生成，请重新选择决定。');
        setError(detail); message.error(detail);
      }
    } finally {
      if (isLatestConflictPreviewRequest(conflictPreviewRequestRef.current, requestId)) {
        setConflictPreviewLoading(false);
      }
    }
  };

  const applyConflictResolution = async () => {
    if (!snapshot?.run || !conflictForReview || !conflictStrategy || !conflictDecision || !conflictPreview) return;
    if (!allowReviewMutation('CONFLICT_RESOLUTION')) return;
    if (conflictPreview.hunk.conflictId !== conflictForReview.conflictId
      || conflictPreview.strategy !== conflictStrategy) {
      setError('冲突策略预览已过期，请重新预览后应用');
      return;
    }
    setBusy(true); setError(undefined);
    try {
      const next = await runtime.execute({
        type: isReplacingHistoricalDecision ? 'REPLACE_RESOLVED_CONFLICT' : 'RESOLVE_CURRENT_CONFLICT',
        commandId: `${commandId(currentUser.userId, snapshot, isReplacingHistoricalDecision ? 'REPLACE_RESOLVED_CONFLICT' : 'RESOLVE_CURRENT_CONFLICT')}:${conflictForReview.conflictId}`,
        expectedRevision: snapshot.run.revision,
        actorUserId: currentUser.userId,
        conflictId: conflictForReview.conflictId,
        strategy: conflictStrategy,
        reason: auditReasonForBusinessDecision({
          conflictId: conflictForReview.conflictId,
          decision: conflictDecision,
        }),
        expectedHunkSha256: conflictPreview.hunk.hunkSha256,
      });
      storeCommandSnapshot(next);
      if (isReplacingHistoricalDecision) {
        setReplacementResolution(undefined);
        setHistoricalResolution(undefined);
        setInteractionAnnouncement('来源差异决定已更新；请重新生成标准化结果后再定版。');
        message.success('当前决定已更新，请重新生成标准化结果');
        return;
      }
      if (next.run?.status === 'READY' && next.nextAction.type === 'READ_NEXT_SOURCE') {
        autoOpenDocumentRef.current = true;
        const readNext = await runtime.execute({
          type: 'READ_NEXT_SOURCE',
          commandId: `${commandId(currentUser.userId, snapshot, 'RESOLVE_CURRENT_CONFLICT')}:next-source`,
          expectedRevision: next.run.revision,
          actorUserId: currentUser.userId,
        });
        storeCommandSnapshot(readNext);
        window.requestAnimationFrame(() => openSourceDocument(readNext));
        setInteractionAnnouncement('当前决定已保存，下一份资料已打开。');
        message.success(`${displaySourceName(readNext.current?.source.sourceId ?? '', readNext.current?.source.sourceName)}快照已载入，来源文档已生成`);
      } else {
        const nextAnnouncement = next.run?.status === 'CONFLICT_BLOCKED'
          ? '当前决定已保存，已打开下一项来源差异。'
          : '当前决定已保存。';
        setInteractionAnnouncement(nextAnnouncement);
        if (next.run?.status === 'CONFLICT_BLOCKED' && next.currentConflict) {
          revealReviewTarget(
            `[data-review-anchor="conflict:${next.currentConflict.conflictId}"]`,
            nextAnnouncement,
          );
        }
        message.success(next.run?.status === 'CONFLICT_BLOCKED'
          ? '本项决定已保存，已定位下一项来源差异'
          : '本项决定已保存');
      }
    } catch (cause) {
      const detail = businessErrorMessage(cause, '当前决定暂时无法保存，请重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  /** The one document-entry path used by task cards, timeline receipts,
   * source navigation and assistant targets. */
  const openSourceDocument = (targetSnapshot = snapshot, trigger?: HTMLElement | null) => {
    if (!targetSnapshot) return;
    try {
      const next = openCurrentDocumentReview(targetSnapshot, reviewNavigation);
      setReviewNavigation(next);
      setSectionPage(1);
      const documentId = targetSnapshot.current?.document?.documentId;
      if (documentId) {
        reviewScrollRestoreRef.current = threadRef.current?.scrollTop;
        reviewSurface.open({
          kind: 'DOCUMENT', stableId: `document:${documentId}`,
          revision: targetSnapshot.run?.revision,
          focusId: `guanyijia-document-review:${documentId}:heading`,
        }, trigger);
      }
    } catch (cause) {
      message.error(businessErrorMessage(cause, '当前审阅文档暂时无法打开，请重试。'));
    }
  };

  const openPersistedSourceDocument = async (documentId: string, trigger?: HTMLElement | null) => {
    const run = snapshot?.run;
    if (!snapshot || !run) return;
    if (documentId === snapshot.current?.document?.documentId) {
      setHistoricalDocumentOpen(false);
      openSourceDocument(snapshot, trigger);
      return;
    }
    try {
      const current = await runtime.readReviewDocumentShell({
        actorUserId: currentUser.userId,
        runId: run.runId,
        documentId,
      });
      const targetSnapshot = { ...snapshot, current };
      setHistoricalDocumentOpen(true);
      setSnapshot(targetSnapshot);
      openSourceDocument(targetSnapshot, trigger);
    } catch (cause) {
      const detail = businessErrorMessage(cause, '来源审阅文档暂时无法打开，请重试。');
      setError(detail);
      message.error(detail);
    }
  };

  useEffect(() => {
    if (!autoOpenDocumentRef.current || !snapshot?.current?.document) return;
    const targetStableId = `document:${snapshot.current.document.documentId}`;
    const currentDocumentLayer = reviewSurface.state.layers.find((layer) => layer.kind === 'DOCUMENT');
    if (currentDocumentLayer?.stableId === targetStableId) {
      autoOpenDocumentRef.current = false;
      return;
    }
    if (currentDocumentLayer) {
      // A completed review closes the previous document asynchronously. If the
      // next source is already in the snapshot, close the stale layer first and
      // let the next effect pass open the new document deterministically.
      reviewSurface.close();
      window.requestAnimationFrame(() => {
        if (autoOpenDocumentRef.current) setReviewNavigation((current) => ({ ...current, focusToken: current.focusToken + 1 }));
      });
      return;
    }
    autoOpenDocumentRef.current = false;
    openSourceDocument(snapshot);
  }, [documentOpen, reviewSurface, snapshot?.current?.document?.documentId, snapshot?.run?.revision]);

  const closeLayer = (layer: 'document' | 'inspector') => {
    const returnAnchor = reviewSurface.state.layers.at(-1)?.returnAnchor ?? reviewSurface.state.anchor;
    const returnFocusId = returnAnchor.focusId
      ?? (reviewSurface.state.assistantDraft.value ? 'guanyijia-assistant-composer' : undefined);
    const returnSelection = returnAnchor.textSelection
      ?? (returnFocusId === 'guanyijia-assistant-composer'
        ? {
            start: reviewSurface.state.assistantDraft.selectionStart,
            end: reviewSurface.state.assistantDraft.selectionEnd,
          }
        : undefined);
    const restoreScrollTop = reviewScrollRestoreRef.current;
    reviewSurface.close();
    if (layer === 'document' && historicalDocumentOpen && snapshot?.run) {
      setHistoricalDocumentOpen(false);
      void runtime.readReviewShell(currentUser.userId)
        .then((next) => setSnapshot(next))
        .catch((cause) => setError(businessErrorMessage(cause, '当前审阅任务暂时无法恢复，请重试。')));
    }
    // The navigator emits the same effect, but an inline document close can
    // replace the assistant textarea during the commit. Re-apply the captured
    // focus/selection on the next frame so the production consumer preserves
    // the user's draft anchor after that DOM replacement.
    window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
      if (restoreScrollTop !== undefined && threadRef.current) threadRef.current.scrollTop = restoreScrollTop;
      if (returnFocusId) {
        const target = document.getElementById(returnFocusId)
          ?? shellRef.current?.querySelector<HTMLElement>(`[data-review-focus="${returnFocusId}"]`);
        target?.focus({ preventScroll: true });
        if (returnSelection && target instanceof HTMLTextAreaElement) {
          target.setSelectionRange(returnSelection.start, returnSelection.end);
        }
      }
    }));
  };

  const openHistoricalConflictDecision = async (conflictId: string) => {
    const run = snapshot?.run;
    if (!run) return;
    const resolutionEvents = [...run.timeline].reverse().filter((event) => (
      event.type === 'CONFLICT_RESOLVED' || event.type === 'CONFLICT_DECISION_REPLACED'
    ));
    try {
      for (const event of resolutionEvents) {
        const artifact = JSON.parse(await standardizationRuns.readEventPayload(run.runId, event.eventId)) as ConflictResolutionArtifact;
        if (artifact.runId !== run.runId || artifact.hunk?.conflictId !== conflictId) continue;
        await validateGuanyijiaResolutionArtifactAgainstPersistedRevisions({
          artifact, run, sourceDocuments, story,
        });
        setHistoricalResolution(artifact);
        setReplacementResolution(undefined);
        window.requestAnimationFrame(() => document.querySelector<HTMLElement>(
          `[data-review-anchor="historical-conflict:${conflictId}"]`,
        )?.scrollIntoView({ block: 'start', behavior: 'smooth' }));
        setInteractionAnnouncement(`已打开“${artifact.hunk.title}”的已保存决定、双方材料和结果。`);
        return;
      }
      setInteractionAnnouncement('这项来源差异尚未保存决定；请在当前步骤处理。');
    } catch (cause) {
      setError(businessErrorMessage(cause, '已保存的来源差异决定暂时无法读取，请稍后重试。'));
    }
  };

  const openTimelineDeliverable = async (item: WorkbenchTimelineItem, mergedDocumentRef: ContentReference) => {
    setBusy(true); setError(undefined);
    try {
      const page = await deliverables.readContent({
        contentRef: mergedDocumentRef,
        actorUserId: currentUser.userId,
        limit: 1000,
      });
      const candidate = JSON.parse(page.items.join('')) as Partial<MergedStandardizationDocument>;
      if (candidate.schemaVersion !== 1 || typeof candidate.markdown !== 'string' || !candidate.markdown.trim()) {
        throw new Error('该时间线记录的标准化文档内容无效');
      }
      setHistoricalDeliverable({ title: item.title, markdown: candidate.markdown });
      setHistoricalResolution(undefined);
      setInteractionAnnouncement(`已打开“${item.title}”时的标准化文档，可只读回看。`);
      window.requestAnimationFrame(() => document.querySelector<HTMLElement>(
        '[data-review-anchor="historical-deliverable"]',
      )?.scrollIntoView({ block: 'start', behavior: 'smooth' }));
    } catch (cause) {
      const detail = businessErrorMessage(cause, '这份标准化结果暂时无法读取，请稍后重试。');
      setError(detail); message.error(detail);
    } finally { setBusy(false); }
  };

  const selectTimeline = (item: WorkbenchTimelineItem) => {
    setSelectedTimelineItemId(item.itemId);
    setSelectedTimelineInspector(item.inspector);
    setSelectedInspectorSourceId(item.sourceId ?? item.inspector?.sourceId);
    setSelectedBlockId(undefined);
    if (snapshot?.run) {
      const target = recoveryTargetForSummary('TIMELINE', item);
      if (!target) {
        recordReviewWindowFailure('TIMELINE', new ReviewWindowError({
          code: 'STORE_UNAVAILABLE', message: '时间线事件缺少可校验正文引用', recoveryAction: 'RETRY',
        }), '时间线事件正文读取失败');
      } else {
        void verifyReviewContent(snapshot.run.runId, target.contentRef, target.expectedSha256)
          .then(() => acknowledgeVerifiedStreamTarget({
            recordedTarget: reviewWindowFailureTargets.TIMELINE,
            verifiedTarget: target,
            onVerified: () => clearReviewWindowFailure('TIMELINE'),
          }))
          .catch((cause) => recordReviewWindowFailure(
            'TIMELINE', cause, '时间线事件正文读取失败', target,
          ));
      }
    }
    if (item.action?.type === 'OPEN_SOURCE_DETAILS') {
      if (inspectorLayered) {
        reviewSurface.open({
          kind: 'INSPECTOR', stableId: `inspector:timeline:${item.itemId}`,
          revision: snapshot?.run?.revision,
          focusId: 'guanyijia-facts-inspector:close',
        }, document.activeElement as HTMLElement | null);
      } else {
        setInspectorCollapsed(false);
      }
      return;
    }
    if (item.action?.type === 'OPEN_DELIVERABLE') {
      if (item.action.mergedDocumentRef) {
        void openTimelineDeliverable(item, item.action.mergedDocumentRef);
        return;
      }
      if (mobile) {
        reviewSurface.open({
          kind: 'DELIVERABLE',
          stableId: `deliverable:${deliverableSnapshot?.deliverable?.deliverableId ?? snapshot?.run?.runId ?? 'pending'}`,
          revision: snapshot?.run?.revision,
          focusId: `deliverable:${deliverableSnapshot?.deliverable?.deliverableId ?? snapshot?.run?.runId ?? 'pending'}:close`,
        }, document.activeElement as HTMLElement | null);
      }
      window.requestAnimationFrame(() => document.querySelector<HTMLElement>(
        `[data-review-anchor="deliverable:${deliverableSnapshot?.deliverable?.deliverableId ?? snapshot?.run?.runId ?? 'pending'}"]`,
      )?.scrollIntoView({ block: 'start', behavior: 'smooth' }));
      setInteractionAnnouncement('已打开标准化结果，可查看定版文档、可建模结论和已排除事项。');
      return;
    }
    if (item.action?.type === 'OPEN_CONFLICT') {
      const activeConflict = snapshot?.currentConflict;
      if (activeConflict?.conflictId === item.action.conflictId) {
        reviewSurface.open({
          kind: 'CONFLICT', stableId: `conflict:${activeConflict.conflictId}`,
          revision: snapshot?.run?.revision,
          focusId: `conflict:${activeConflict.conflictId}:close`,
        }, document.activeElement as HTMLElement | null);
        window.requestAnimationFrame(() => document.querySelector<HTMLElement>(
          `[data-review-anchor="conflict:${activeConflict.conflictId}"]`,
        )?.scrollIntoView({ block: 'start', behavior: 'smooth' }));
        setInteractionAnnouncement(`已打开“${activeConflict.title}”，可查看双方材料并保存当前决定。`);
      } else {
        void openHistoricalConflictDecision(item.action.conflictId);
      }
      return;
    }
    if (inspectorLayered && item.inspector) {
      reviewSurface.open({
        kind: 'INSPECTOR', stableId: `inspector:timeline:${item.itemId}`,
        revision: snapshot?.run?.revision,
        focusId: 'guanyijia-facts-inspector:close',
      }, document.activeElement as HTMLElement | null);
    }
    // A generated or revised document receipt is a real navigation
    // affordance, including completed source documents. Historical revisions
    // open read-only and return to the actual current task on close.
    if (item.document?.documentId) {
      void openPersistedSourceDocument(item.document.documentId, document.activeElement as HTMLElement | null);
    }
  };

  const selectSourceFromInspector = (sourceId: string) => {
    // The source list is navigation for the right-hand journey only.  Opening
    // a source document remains the explicit action on its workflow step.
    // This keeps the reader's current page, scroll position and focus intact.
    setSelectedTimelineItemId(`source:${sourceId}`);
    setSelectedInspectorSourceId(sourceId);
    setWorkflowLocateRequest({
      checkpointId: `source:${sourceId}`,
      requestId: ++workflowLocateRequestIdRef.current,
    });
    setInteractionAnnouncement(`已定位${displaySourceName(sourceId, inspectorSources.find((source) => source.sourceId === sourceId)?.sourceName)}的处理进度。`);
  };

  const assistantSelection = (): ReviewAssistantContextSelection => {
    const selectedTimelineItem = timeline?.find((item) => item.itemId === selectedTimelineItemId);
    const sourceId = documentOpen
      ? snapshot?.current?.source.sourceId
      : selectedTimelineItem?.sourceId ?? snapshot?.current?.source.sourceId;
    const sourceDocument = sourceId && snapshot?.current?.source.sourceId === sourceId
      ? snapshot.current.document
      : undefined;
    const block = sourceId === snapshot?.current?.source.sourceId
      ? currentDocumentBlocks?.find((candidate) => candidate.blockId === selectedBlockId)
      : undefined;
    const conflictId = selectedTimelineItem
      ? selectedTimelineItem.conflicts?.[0]?.conflictId
      : snapshot?.currentConflict?.conflictId;
    return {
      ...(selectedTimelineItem?.eventId ? { timelineItemId: selectedTimelineItem.eventId } : {}),
      ...(sourceId ? { sourceId } : {}),
      ...(sourceDocument ? { documentId: sourceDocument.documentId } : {}),
      ...(documentOpen ? { section: reviewNavigation.selectedSection ?? 'OVERVIEW' } : {}),
      ...(block ? {
        blockId: block.blockId,
        ...(block.evidenceRefs[0] ? { evidenceRef: block.evidenceRefs[0] } : {}),
        ...(block.affectedObjectRefs[0] ? { objectRef: block.affectedObjectRefs[0] } : {}),
      } : {}),
      ...(conflictId ? { conflictId } : {}),
    };
  };

  const focusTimelineItem = (item: WorkbenchTimelineItem) => {
    selectTimeline(item);
    window.requestAnimationFrame(() => {
      const target = document.getElementById(`guanyijia-timeline:${item.itemId}`);
      target?.scrollIntoView({ block: 'center', behavior: 'smooth' });
      target?.focus({ preventScroll: true });
    });
  };

  const openAssistantTarget = (
    target: ReviewAssistantTarget,
    targetSnapshot: GuanyijiaWorkbenchSnapshot | null = snapshot,
    trigger?: HTMLElement | null,
  ) => {
    if (!targetSnapshot) return;
    const assistantComposer = document.getElementById('guanyijia-assistant-composer');
    const returnTrigger = assistantComposer ?? trigger;
    if (target.kind === 'SOURCE_DOCUMENT') {
      if (targetSnapshot.current?.document?.documentId === target.documentId) {
        const navigation = target.blockId
          ? continueCurrentDocumentReviewAfterApply(targetSnapshot, reviewNavigation, target.blockId)
          : {
              ...openCurrentDocumentReview(targetSnapshot, reviewNavigation),
              ...(target.section ? { selectedSection: target.section } : {}),
        };
        setReviewNavigation(navigation);
        if (target.blockId) setSelectedBlockId(target.blockId);
        setSectionPage(1);
        reviewScrollRestoreRef.current = threadRef.current?.scrollTop;
        reviewSurface.open({
          kind: 'DOCUMENT', stableId: `document:${target.documentId}`,
          revision: targetSnapshot.run?.revision,
          focusId: target.blockId
            ? `guanyijia-document-review:${target.documentId}:block:${target.blockId}`
            : `guanyijia-document-review:${target.documentId}:heading`,
        }, returnTrigger ?? document.activeElement as HTMLElement | null);
        return;
      }
      const item = timeline?.findLast((candidate) => candidate.document?.documentId === target.documentId);
      if (item) focusTimelineItem(item);
      return;
    }
    if (target.kind === 'CONFLICT') {
      const item = timeline?.findLast((candidate) => candidate.conflicts?.some((candidateConflict) => (
        candidateConflict.conflictId === target.conflictId
      )) ?? false);
      if (item) focusTimelineItem(item);
      return;
    }
    if (target.kind === 'EVIDENCE') {
      if (targetSnapshot.current?.source.sourceId === target.sourceId
        && (targetSnapshot.current.reviewCompilation?.blocks ?? targetSnapshot.current.compilation?.blocks)
          ?.some((block) => block.blockId === target.blockId)) {
        openAssistantTarget({
          kind: 'SOURCE_DOCUMENT', documentId: targetSnapshot.current.document!.documentId,
          blockId: target.blockId,
        }, targetSnapshot);
        setSelectedBlockId(target.blockId);
        if (inspectorLayered) {
          reviewSurface.open({
            kind: 'INSPECTOR', stableId: `inspector:block:${target.blockId}`,
            revision: targetSnapshot.run?.revision,
            focusId: 'guanyijia-facts-inspector:close',
          }, document.activeElement as HTMLElement | null);
        }
        return;
      }
      const item = timeline?.findLast((candidate) => candidate.sourceId === target.sourceId);
      if (item) focusTimelineItem(item);
      return;
    }
    const objectKey = canonicalModelingJson(target.objectRef);
    const block = (targetSnapshot.current?.reviewCompilation?.blocks ?? targetSnapshot.current?.compilation?.blocks)
      ?.find((candidate) => (
      candidate.affectedObjectRefs.some((reference) => canonicalModelingJson(reference) === objectKey)
    ));
    if (block && targetSnapshot.current?.document) {
      openAssistantTarget({
        kind: 'SOURCE_DOCUMENT', documentId: targetSnapshot.current.document.documentId,
        section: block.section, blockId: block.blockId,
      }, targetSnapshot);
      setSelectedBlockId(block.blockId);
      if (inspectorLayered) {
        reviewSurface.open({
          kind: 'INSPECTOR', stableId: `inspector:block:${block.blockId}`,
          revision: targetSnapshot.run?.revision,
          focusId: 'guanyijia-facts-inspector:close',
        }, document.activeElement as HTMLElement | null);
      }
      return;
    }
    const conflictItem = timeline?.findLast((item) => item.conflicts?.some((candidate) => (
      candidate.affectedObjects.some((value) => value.endsWith(`：${target.objectRef.objectId}`))
    )) ?? false);
    if (conflictItem) focusTimelineItem(conflictItem);
  };

  const readAssistantHistoryProjection = async (
    context: AssistantHistoryContext,
  ): Promise<AssistantHistoryOrchestratorResult<ReviewAssistantHistoryItem>> => {
    const controller = assistantHistoryRecoveryRef.current!;
    const state = await controller.read(context, async (currentContext) => {
      const historyPage = await runtime.readAssistantHistory({
        actorUserId: currentContext.actorUserId,
        runId: currentContext.runId,
      });
      return {
        items: historyPage.items,
        total: historyPage.total,
        pendingProposal: historyPage.pendingProposal,
        nextCursor: historyPage.nextCursor ?? undefined,
      };
    }, 'HISTORY');
    return {
      items: state.items,
      total: state.total,
      pendingProposal: state.pendingProposal,
      nextCursor: state.nextCursor,
      error: state.error,
    };
  };

  const reloadAssistantHistory = async (context: AssistantHistoryContext) => {
    const controller = assistantHistoryRecoveryRef.current!;
    const initial = controller.begin(context);
    const scopeKey = assistantHistoryUiScopeKey(context);
    const initialProjection = projectAssistantHistoryRecoveryState(initial);
    setAssistantHistoryState((current) => reduceAssistantHistoryUiState(current, {
      scopeKey,
      items: initialProjection.items,
      total: initialProjection.total,
      pendingProposal: initialProjection.pendingProposal,
      loaded: initialProjection.historyLoaded,
      loading: true,
      error: undefined,
    }));
    const state = await readAssistantHistoryProjection(context);
    if (state.error) {
      const failedProjection = projectAssistantHistoryRecoveryState(
        controller.getState(),
      );
      setAssistantHistoryState((current) => reduceAssistantHistoryUiState(current, {
        scopeKey,
        items: failedProjection.items,
        total: failedProjection.total,
        pendingProposal: failedProjection.pendingProposal,
        loaded: failedProjection.historyLoaded,
        loading: false,
        error: state.error,
      }));
      recordReviewWindowFailure(
        'ASSISTANT_HISTORY', state.error, '审阅助手历史读取失败',
        recoveryTargetFromError('ASSISTANT_HISTORY', state.error),
      );
      return undefined;
    }
    setAssistantHistoryState((current) => reduceAssistantHistoryUiState(current, {
      scopeKey,
      items: state.items,
      total: state.total,
      pendingProposal: state.pendingProposal,
      loaded: true,
      loading: false,
      error: undefined,
    }));
    return state;
  };

  const commitAssistantAskHistory = (
    payload: AssistantHistoryCommitPayload<GuanyijiaWorkbenchSnapshot, ReviewAssistantHistoryItem>,
  ) => {
    storeCommandSnapshot(payload.snapshot);
    const context = assistantHistoryContextFor(payload.snapshot, currentUser.userId);
    if (context) {
      setAssistantHistoryState((current) => reduceAssistantHistoryUiState(current, {
        scopeKey: assistantHistoryUiScopeKey(context),
        items: payload.items,
        total: payload.total,
        pendingProposal: payload.pendingProposal,
        loaded: payload.historyLoaded,
        loading: false,
        error: payload.error,
      }));
    }
    if (payload.error) {
      recordReviewWindowFailure(
        'ASSISTANT_HISTORY', payload.error, '审阅助手历史读取失败',
        recoveryTargetFromError('ASSISTANT_HISTORY', payload.error),
      );
    }
  };

  const askAssistant = async () => {
    if (!snapshot?.run || !assistantDraft.trim()) return;
    let intent;
    try { intent = classifyReviewAssistantIntent(assistantDraft); }
    catch (cause) {
      const detail = businessErrorMessage(cause, '审阅助手暂时无法理解这条问题，请换一种说法。');
      setError(detail); message.error(detail); return;
    }
    if (editingBlockId && intent.kind === 'PROPOSE_BLOCK_CHANGE') {
      const detail = '请先完成或取消当前手工编辑，再让审阅助手生成修改预览';
      setError(detail); message.error(detail); return;
    }
    if (curatedReview && intent.kind === 'PROPOSE_BLOCK_CHANGE') {
      const detail = '当前来源只开放已列出的建议；请在审阅事项中处理已列出的建议';
      setError(detail); message.error(detail); return;
    }
    if (intent.kind === 'PROPOSE_BLOCK_CHANGE' && !allowReviewMutation('PATCH')) return;
    const requestId = ++assistantRequestRef.current;
    const messageText = assistantDraft;
    setAssistantBusy(true); setError(undefined);
    try {
      let committedHistory: AssistantHistoryCommitPayload<GuanyijiaWorkbenchSnapshot, ReviewAssistantHistoryItem> | undefined;
      const result = await commitAssistantAskDeltaResult({
        requestId,
        isCurrentRequest: (currentRequestId) => assistantRequestRef.current === currentRequestId,
        execute: () => {
          const digest = sha256HexSync(canonicalModelingJson({
            message: messageText, selection: assistantSelection(),
          })).slice(0, 16);
          return runtime.execute({
            type: 'ASK_REVIEW_ASSISTANT',
            commandId: commandId(currentUser.userId, snapshot, 'ASK_REVIEW_ASSISTANT') + ':' + digest,
            expectedRevision: snapshot.run!.revision,
            actorUserId: currentUser.userId,
            message: messageText,
            selection: assistantSelection(),
          });
        },
        contextFor: (next) => {
          const context = assistantHistoryContextFor(next, currentUser.userId);
          if (!context) throw new Error('助手ASK返回的运行上下文无效');
          return context;
        },
        begin: (nextContext) => { assistantHistoryRecoveryRef.current!.begin(nextContext); },
        mergeDelta: (delta) => ({
          items: [delta.item],
          total: delta.total,
          ...(delta.item.proposalStatus === 'PENDING' ? { pendingProposal: delta.item } : {}),
        }),
        commit: (payload) => {
          committedHistory = payload;
          commitAssistantAskHistory(payload);
        },
      });
      if (result !== 'COMMITTED' || !committedHistory || committedHistory.error) return;
      reviewSurface.updateDraft({ value: '', selectionStart: 0, selectionEnd: 0 });
      const latest = committedHistory.items.at(-1)?.response;
      if (latest?.kind === 'OPEN_TARGET') openAssistantTarget(latest.target);
    } catch (cause) {
      if (assistantRequestRef.current === requestId) {
        const detail = businessErrorMessage(cause, '审阅助手暂时无法回答，请重试。');
        setError(detail); message.error(detail);
      }
    } finally {
      if (assistantRequestRef.current === requestId) setAssistantBusy(false);
    }
  };

  const confirmAssistantProposal = async (item: ReviewAssistantHistoryItem) => {
    if (!snapshot?.run || item.response.kind !== 'PATCH_PREVIEW') return;
    if (!allowReviewMutation('PATCH')) return;
    setAssistantBusy(true); setError(undefined);
    try {
      const next = await runtime.execute({
        type: 'CONFIRM_ASSISTANT_PATCH',
        commandId: `${commandId(currentUser.userId, snapshot, 'CONFIRM_ASSISTANT_PATCH')}:${item.response.proposal.proposalId}`,
        expectedRevision: snapshot.run.revision,
        actorUserId: currentUser.userId,
        proposalId: item.response.proposal.proposalId,
        expectedPreviewSha256: item.response.proposal.previewSha256,
      });
      storeCommandSnapshot(next);
      await reloadAssistantHistory(assistantHistoryContextFor(next, currentUser.userId)!);
      if (next.assistantFocusTarget) openAssistantTarget(next.assistantFocusTarget, next);
      message.success('审阅助手修改已应用，已生成新的文档版本');
    } catch (cause) {
      const detail = businessErrorMessage(cause, '建议修改暂时无法保存，请重试。');
      setError(detail); message.error(detail);
    } finally { setAssistantBusy(false); }
  };

  const cancelAssistantProposal = async (item: ReviewAssistantHistoryItem) => {
    if (!snapshot?.run || item.response.kind !== 'PATCH_PREVIEW') return;
    if (!allowReviewMutation('PATCH')) return;
    setAssistantBusy(true); setError(undefined);
    try {
      const next = await runtime.execute({
        type: 'CANCEL_ASSISTANT_PATCH',
        commandId: `${commandId(currentUser.userId, snapshot, 'CANCEL_ASSISTANT_PATCH')}:${item.response.proposal.proposalId}`,
        expectedRevision: snapshot.run.revision,
        actorUserId: currentUser.userId,
        proposalId: item.response.proposal.proposalId,
      });
      storeCommandSnapshot(next);
      await reloadAssistantHistory(assistantHistoryContextFor(next, currentUser.userId)!);
      if (assistantPatchLayer) reviewSurface.close();
      message.success('建议已取消，来源文档没有修订');
    } catch (cause) {
      const detail = businessErrorMessage(cause, '暂时无法取消这条建议，请重试。');
      setError(detail); message.error(detail);
    } finally { setAssistantBusy(false); }
  };

  const activeDocumentWindow = documentWindowPages[documentWindowPageIndex];
  const pagedBlocks = snapshot
    ? currentDocumentBlocks && activeDocumentWindow
      ? projectCurrentDocumentBlockWindow(
          snapshot, selectedSection, activeDocumentWindow, documentWindowPageIndex + 1,
        )
      : snapshot.current?.legacyReadOnly
        ? pageCurrentDocumentBlocks(snapshot, selectedSection, sectionPage)
      : null
    : null;
  const selectedCandidateEvidence = sourceReviewVisibility?.comparisonFindings
    .flatMap((projection) => projection.evidence)
    .find((evidence) => evidence.evidenceRef === selectedCandidateEvidenceRef);
  const candidateInspectorModel = selectedCandidateEvidence
    && selectedInspectorSourceId === selectedCandidateEvidence.sourceId
    ? {
        sourceId: selectedCandidateEvidence.sourceId,
        sourceName: selectedCandidateEvidence.sourceName,
        sourceClass: 'REAL' as const,
        snapshotId: selectedCandidateEvidence.snapshotId,
        versionRef: '本次读取发现',
        authority: selectedCandidateEvidence.evidenceClass === 'OBSERVED' ? 'PRIMARY' as const : 'AUXILIARY' as const,
        readSummary: `${selectedCandidateEvidence.evidenceClassLabel}；仅显示本次已载入来源的可复核内容。`,
        objectCount: selectedCandidateEvidence.evidenceClass === 'GAP' ? 0 : 1,
        evidenceCount: 1,
        lineage: { status: 'ROOT' as const, upstreamSourceNames: [] },
        block: {
          blockId: `candidate:${selectedCandidateEvidence.evidenceRef}`,
          label: selectedCandidateEvidence.title,
          evidenceStatus: selectedCandidateEvidence.evidenceClass === 'GAP' ? 'GAP' as const : 'FACT' as const,
          locator: { [selectedCandidateEvidence.locationLabel]: selectedCandidateEvidence.locationValue },
          affectedObjects: [],
          readableEvidence: {
            evidenceRef: selectedCandidateEvidence.evidenceRef,
            title: selectedCandidateEvidence.title,
            sourceType: selectedCandidateEvidence.sourceId === 'guanyijia_mysql'
              ? 'DDL' as const
              : selectedCandidateEvidence.sourceId === 'guanyijia_github' ? 'CODE' as const : 'DOCUMENT' as const,
            excerpt: selectedCandidateEvidence.excerpt,
            locationLabel: selectedCandidateEvidence.locationLabel,
            locationValue: selectedCandidateEvidence.locationValue,
            affectedObjectRefs: [],
            technicalDetails: {},
            hiddenTechnicalDetails: {
              evidenceClass: selectedCandidateEvidence.evidenceClass,
              artifactDigest: selectedCandidateEvidence.artifactDigest,
              claimId: selectedCandidateEvidence.claimId,
            },
          },
        },
      } satisfies StandardizationFactsInspectorModel
    : undefined;
  // With an open document, the facts panel defaults to the first visible
  // evidence-backed block. Readers see a DDL/SQL/document excerpt first,
  // rather than a technical run summary.
  const inspectorModel = snapshot
    ? candidateInspectorModel ?? (selectedInspectorMatchesCurrentSource
      ? curatedInspectorModel ?? factsInspectorFor(snapshot, selectedBlockId) ?? selectedTimelineInspector ?? snapshot.inspector
      : undefined)
    : selectedTimelineInspector;
  const readCount = snapshot?.run?.sources.filter((source) => Boolean(source.documentId)).length ?? 0;
  const reviewedCount = snapshot?.run?.sources.filter((source) => ['ALIGNED', 'CONFLICT_BLOCKED'].includes(source.status)).length ?? 0;
  const canCompleteReview = snapshot?.current?.sourceStep.status === 'DOCUMENT_READY'
    && !snapshot.current.legacyReadOnly
    && pendingScriptedReviewEdits.length === 0;
  const nextAction = snapshot?.nextAction;
  const visibleWorkflowAction = nextAction
    ? buildWorkbenchActions({
      workflow: nextAction,
      sourceName: snapshot?.current
        ? displaySourceName(snapshot.current.source.sourceId, snapshot.current.source.sourceName)
        : undefined,
      canCompleteCurrentDocument: canCompleteReview,
    })[0]
    : undefined;
  const visibleSnapshotAction = sourceSnapshotAction();
  const pendingSourceName = snapshot?.run?.sources.find((source) => source.status === 'PENDING');
  const workflowTitle = nextAction?.type === 'START_RUN'
    ? '准备资料审阅'
    : nextAction?.type === 'READ_NEXT_SOURCE'
      ? `下一来源：${pendingSourceName ? displaySourceName(pendingSourceName.sourceId, pendingSourceName.sourceName) : '固定快照'}`
      : nextAction?.label;
  const editingBlock = currentDocumentBlocks?.find((block) => block.blockId === editingBlockId);
  const editorModel = editingBlock ? structuredValueEditorModel(editingBlock.value) : undefined;
  const conflict = conflictForReview;
  const conflictWorkspaceOpen = Boolean(conflict) && (
    nextAction?.type === 'RESOLVE_CONFLICT' || isReplacingHistoricalDecision
  );
  const currentConflictPreview = conflictPreview
    && conflict
    && conflictStrategy
    && conflictPreview.hunk.conflictId === conflict.conflictId
    && conflictPreview.strategy === conflictStrategy
    ? conflictPreview
    : undefined;
  const businessConflictMarkdownDiff = currentConflictPreview && conflictDecision
    ? projectBusinessMarkdownDiff({ decision: conflictDecision, preview: currentConflictPreview })
    : undefined;
  const pendingAssistantProposal = assistantPendingProposal ?? assistantHistory.findLast((item) => (
    item.response.kind === 'PATCH_PREVIEW' && item.proposalStatus === 'PENDING'
  ));
  const pendingAssistantProposalId = pendingAssistantProposal?.response.kind === 'PATCH_PREVIEW'
    ? pendingAssistantProposal.response.proposal.proposalId
    : undefined;
  const canConfirmAssistant = !curatedReview
    && (sourceManagement.role === 'EDITOR' || sourceManagement.role === 'ADMIN');
  const deliverable = deliverableSnapshot?.deliverable;
  const pendingDeliverableCommand = deliverableSnapshot?.pendingCommand;
  const modelingEligibility = deliverableSnapshot?.modelingEligibility;
  const eligibleConclusionCount = modelingEligibility?.eligibleCandidateIds.length ?? 0;
  const excludedConclusionCount = modelingEligibility
    ? modelingEligibility.excludedCandidateIds.length
      + modelingEligibility.conclusions.filter((item) => !item.candidateId && item.eligibility !== 'ELIGIBLE').length
    : 0;
  const deliveryPhase = Boolean(deliverable) || ['READY_FOR_OUTPUT', 'FROZEN', 'HANDED_OFF']
    .includes(snapshot?.run?.status ?? '');
  const isDeliverableAuthor = deliverable?.authorUserId === currentUser.userId
    || !deliverable && snapshot?.run?.createdBy === currentUser.userId;
  const canAuthorDeliverable = isDeliverableAuthor
    && (sourceManagement.role === 'EDITOR' || sourceManagement.role === 'ADMIN');
  const canAuthorFreezeDeliverable = canAuthorDeliverable
    || (deliverable?.status === 'AWAITING_INDEPENDENT_REVIEW' && sourceManagement.role === 'ADMIN');
  const canHandoffDeliverable = isDeliverableAuthor || sourceManagement.role === 'ADMIN';
  const inspectorLayer = reviewSurface.state.layers.findLast((layer) => layer.kind === 'INSPECTOR');
  const assistantPatchLayer = reviewSurface.state.layers.findLast((layer) => layer.kind === 'ASSISTANT_PATCH');
  const conflictLayer = reviewSurface.state.layers.findLast((layer) => layer.kind === 'CONFLICT');
  const deliverableLayer = reviewSurface.state.layers.findLast((layer) => layer.kind === 'DELIVERABLE');
  const blockingReviewFailures = blockingReviewWindowFailures(reviewWindowErrors);
  const reviewMutationBlocked = blockingReviewFailures.length > 0 || curatedValidationFailed;
  const documentWindowFailure = reviewWindowErrors.DOCUMENT_BLOCKS;
  const documentRecoveryMessage = curatedValidationFailed
    ? '真实审阅文档校验失败'
    : documentWindowFailure?.code === 'CHECKSUM_MISMATCH'
      ? '正文完整性校验失败'
      : documentWindowFailure?.code === 'QUOTA_EXCEEDED'
        ? '浏览器无法保存本次审阅'
        : documentWindowFailure
          ? '文档正文暂时无法读取'
          : undefined;

  const recoverReviewWindowStream = useCallback(async (stream: ReviewWindowStream) => {
    const run = snapshot?.run;
    if (!run) return;
    try {
      if (['ASSISTANT_HISTORY', 'TIMELINE', 'ISSUES', 'EVIDENCE'].includes(stream)) {
        await recoverVerifiedStreamTarget({
          target: reviewWindowFailureTargets[stream],
          readContent: ({ contentRef, expectedSha256 }) => verifyReviewContent(
            run.runId, contentRef, expectedSha256,
          ),
          onVerified: () => clearReviewWindowFailure(stream),
        });
        return;
      }
      setTimelineWindowReloadToken((token) => token + 1);
    } catch (cause) {
      recordReviewWindowFailure(
        stream, cause, `${stream}窗口恢复失败`, reviewWindowFailureTargets[stream],
      );
    }
  }, [clearReviewWindowFailure,
    recordReviewWindowFailure, reviewWindowFailureTargets, snapshot?.run,
    verifyReviewContent]);

  function allowReviewMutation(
    mutation: Parameters<typeof assertReviewMutationAllowed>[1],
  ) {
    if (historicalDocumentOpen) {
      const detail = '当前正在查看历史文档，请返回当前审阅后再修改或完成审阅。';
      setError(detail);
      message.info(detail);
      return false;
    }
    if (curatedValidationFailed) {
      setError('真实审阅文档校验失败');
      message.error('真实审阅文档校验失败');
      return false;
    }
    try {
      assertReviewMutationAllowed(reviewWindowErrors, mutation);
      return true;
    } catch (cause) {
      const detail = businessErrorMessage(cause, '审阅正文暂时无法恢复，请重试。');
      setError(detail);
      message.error(detail);
      return false;
    }
  }

  useEffect(() => {
    const run = snapshot?.run;
    if (!run || !conflict) { setIssueWindow(undefined); return; }
    let cancelled = false;
    let failureTarget = reviewWindowFailureTargets.ISSUES;
    runtime.readReviewWindow({
      actorUserId: currentUser.userId, runId: run.runId,
      epoch: `revision:${run.revision}`, stream: 'ISSUES', filter: 'all',
    }).then(async (page) => {
      const item = page.items.find(({ stableKey }) => stableKey === `issue:${conflict.conflictId}`);
      const pageTarget = recoveryTargetForSummary('ISSUES', item);
      const recordedTarget = reviewWindowFailureTargets.ISSUES;
      failureTarget = recordedTarget ?? pageTarget;
      if (!pageTarget || recordedTarget && (
        recordedTarget.contentRef !== pageTarget.contentRef
        || recordedTarget.expectedSha256 !== pageTarget.expectedSha256
      )) {
        recordReviewWindowFailure('ISSUES', new ReviewWindowError({
          code: 'STORE_UNAVAILABLE', message: '来源差异窗口缺少原失败正文引用', recoveryAction: 'RETRY',
        }), '来源差异窗口读取失败');
        return;
      }
      await recoverVerifiedStreamTarget({
        target: pageTarget,
        readContent: ({ contentRef, expectedSha256 }) => verifyReviewContent(
          run.runId, contentRef, expectedSha256,
        ),
        onVerified: (verifiedTarget) => {
          if (!cancelled) acknowledgeVerifiedStreamTarget({
            recordedTarget,
            verifiedTarget,
            onVerified: () => clearReviewWindowFailure('ISSUES'),
          });
        },
      });
      if (!cancelled) setIssueWindow(page);
    }).catch((cause) => {
      if (!cancelled) recordReviewWindowFailure(
        'ISSUES', cause, '来源差异窗口读取失败', failureTarget,
      );
    });
    return () => { cancelled = true; };
  }, [
    clearReviewWindowFailure, conflict, currentUser.userId, recordReviewWindowFailure, runtime, snapshot?.run,
    reviewWindowFailureTargets.ISSUES, timelineWindowReloadToken, verifyReviewContent,
  ]);

  useEffect(() => {
    const run = snapshot?.run;
    const sourceId = snapshot?.current?.source.sourceId;
    const currentBlocks = snapshot?.current?.reviewCompilation?.blocks
      ?? snapshot?.current?.compilation?.blocks;
    if (!run || !sourceId || !selectedBlockId
      || !currentBlocks?.some((block) => block.blockId === selectedBlockId)) {
      setEvidenceWindow(undefined);
      return;
    }
    let cancelled = false;
    let failureTarget = reviewWindowFailureTargets.EVIDENCE;
    runtime.readReviewWindow({
      actorUserId: currentUser.userId, runId: run.runId,
      epoch: `revision:${run.revision}`, stream: 'EVIDENCE',
      filter: `source:${sourceId}:block:${selectedBlockId}`,
    }).then(async (page) => {
      const item = page.items[0];
      const pageTarget = recoveryTargetForSummary('EVIDENCE', item);
      const recordedTarget = reviewWindowFailureTargets.EVIDENCE;
      failureTarget = recordedTarget ?? pageTarget;
      if (!pageTarget || recordedTarget && (
        recordedTarget.contentRef !== pageTarget.contentRef
        || recordedTarget.expectedSha256 !== pageTarget.expectedSha256
      )) {
        recordReviewWindowFailure('EVIDENCE', new ReviewWindowError({
          code: 'STORE_UNAVAILABLE', message: 'Evidence窗口缺少原失败正文引用', recoveryAction: 'RETRY',
        }), 'Evidence窗口读取失败');
        return;
      }
      await recoverVerifiedStreamTarget({
        target: pageTarget,
        readContent: ({ contentRef, expectedSha256 }) => verifyReviewContent(
          run.runId, contentRef, expectedSha256,
        ),
        onVerified: (verifiedTarget) => {
          if (!cancelled) acknowledgeVerifiedStreamTarget({
            recordedTarget,
            verifiedTarget,
            onVerified: () => clearReviewWindowFailure('EVIDENCE'),
          });
        },
      });
      if (!cancelled) setEvidenceWindow(page);
    }).catch((cause) => {
      if (!cancelled) recordReviewWindowFailure(
        'EVIDENCE', cause, 'Evidence窗口读取失败', failureTarget,
      );
    });
    return () => { cancelled = true; };
  }, [
    clearReviewWindowFailure, currentUser.userId, recordReviewWindowFailure, runtime, selectedBlockId,
    reviewWindowFailureTargets.EVIDENCE, snapshot?.current?.source.sourceId, snapshot?.run,
    timelineWindowReloadToken, verifyReviewContent,
  ]);

  const loadNextDocumentWindow = async () => {
    const run = snapshot?.run;
    const document = snapshot?.current?.document;
    const currentPage = documentWindowPages[documentWindowPageIndex];
    if (!run || !document?.blocksRef || !currentPage?.nextCursor) return;
    const existing = documentWindowPages[documentWindowPageIndex + 1];
    if (existing) { setDocumentWindowPageIndex((index) => index + 1); return; }
    try {
      // A document page is a verified-content projection, not a summary-only
      // window. Keep the same production facade for the first and subsequent
      // pages so the React document never hydrates an unverified block list.
      const { window: page, current } = await runtime.readReviewCurrentDocument({
        actorUserId: currentUser.userId,
        runId: run.runId,
        documentId: document.documentId,
        epoch: `revision:${run.revision}`,
        filter: `section:${selectedSection}`,
        cursor: currentPage.nextCursor,
      });
      setSnapshot((value) => value ? { ...value, current } : value);
      setDocumentWindowPages((pages) => [...pages.slice(0, documentWindowPageIndex + 1), page]);
      setDocumentWindowPageIndex((index) => index + 1);
    } catch (cause) {
      recordReviewWindowFailure('DOCUMENT_BLOCKS', cause, '来源文档下一页读取失败');
    }
  };

  useEffect(() => {
    if (!pendingAssistantProposalId) {
      assistantPatchSeenRef.current = undefined;
      return;
    }
    if (!mobile || assistantPatchSeenRef.current === pendingAssistantProposalId) return;
    assistantPatchSeenRef.current = pendingAssistantProposalId;
    reviewSurface.open({
      kind: 'ASSISTANT_PATCH', stableId: `assistant-patch:${pendingAssistantProposalId}`,
      revision: snapshot?.run?.revision,
      focusId: 'guanyijia-assistant-composer',
    });
  }, [mobile, pendingAssistantProposalId, reviewSurface, snapshot?.run?.revision]);

  useEffect(() => {
    const identity = conflict && nextAction?.type === 'RESOLVE_CONFLICT'
      ? `conflict:${conflict.conflictId}`
      : deliveryPhase ? `deliverable:${deliverable?.deliverableId ?? snapshot?.run?.runId ?? 'pending'}` : undefined;
    if (!identity) {
      workflowLayerSeenRef.current = undefined;
      return;
    }
    if (!mobile || workflowLayerSeenRef.current === identity) return;
    workflowLayerSeenRef.current = identity;
    reviewSurface.open({
      kind: identity.startsWith('conflict:') ? 'CONFLICT' : 'DELIVERABLE',
      stableId: identity,
      revision: snapshot?.run?.revision,
      focusId: `${identity}:close`,
    });
  }, [conflict, deliverable?.deliverableId, deliveryPhase, mobile, nextAction?.type, reviewSurface, snapshot?.run]);

  useEffect(() => {
    const run = snapshot?.run;
    if (!run || reviewSurface.state.runId !== run.runId || reviewSurface.state.runRevision === run.revision) return;
    const current = timeline?.findLast((item) => item.state === 'CURRENT') ?? timeline?.at(-1);
    const documentLayer = reviewSurface.state.layers.findLast((layer) => layer.kind === 'DOCUMENT');
    const nextDocumentId = snapshot.current?.document?.documentId;
    const previousDocumentId = documentLayer?.stableId.startsWith('document:')
      ? documentLayer.stableId.slice('document:'.length)
      : undefined;
    const migratesCurrentDocument = Boolean(
      documentLayer && nextDocumentId && previousDocumentId && previousDocumentId !== nextDocumentId
      && snapshot.current?.history?.some((document) => document.documentId === previousDocumentId),
    );
    const validStableIds = [
      ...(nextDocumentId ? [`document:${nextDocumentId}`] : []),
      ...(snapshot.currentConflict ? [`conflict:${snapshot.currentConflict.conflictId}`] : []),
      ...(deliveryPhase ? [`deliverable:${deliverable?.deliverableId ?? run.runId}`] : []),
      ...reviewSurface.state.layers
        .filter((layer) => layer.kind === 'INSPECTOR' && Boolean(snapshot.current))
        .map((layer) => layer.stableId),
    ];
    reviewSurface.dispatch({
      type: 'RUN_REVISION_CHANGED',
      runRevision: run.revision,
      validStableIds,
      ...(migratesCurrentDocument ? { layerMigrations: [{
        fromStableId: documentLayer!.stableId,
        toStableId: `document:${nextDocumentId}`,
        focusId: selectedBlockId
          ? `guanyijia-document-review:${nextDocumentId}:block:${selectedBlockId}`
          : `guanyijia-document-review:${nextDocumentId}:heading`,
      }] } : {}),
      fallbackAnchor: {
        itemId: current ? `timeline:${current.itemId}` : 'review-surface:start',
        relativeTop: 0,
        ...(current ? { focusId: `guanyijia-timeline:${current.itemId}` } : {}),
      },
    });
  }, [deliverable?.deliverableId, deliveryPhase, reviewSurface, selectedBlockId, snapshot, timeline]);

  const documentEvidenceCompilation = snapshot?.current?.reviewCompilation ?? snapshot?.current?.compilation;
  const documentTraceLinks = documentEvidenceCompilation ? buildSourceDocumentTraceLinks(documentEvidenceCompilation) : [];
  const formalEvidenceTraces = documentEvidenceCompilation ? documentTraceLinks.flatMap((trace) => {
    const block = documentEvidenceCompilation.blocks.find((candidate) => candidate.blockId === trace.blockId);
    return block ? [{ ...trace, linePrefix: block.label }] : [];
  }) : [];
  const candidateReviewsForSource = sourceReviewVisibility?.comparisonFindings ?? [];
  const crossSourceGapFindings = candidateReviewsForSource.filter((finding) => (
    finding.topic === 'DOCUMENT_STATUS' || finding.relation === 'UNSUPPORTED'
  ));
  const crossSourceConclusionFindings = candidateReviewsForSource.filter((finding) => (
    !crossSourceGapFindings.includes(finding)
  ));
  const pendingChecklistTasks = useMemo(() => {
    const pendingTaskIds = new Set(pendingScriptedReviewEdits.map((edit) => edit.definition.editId));
    return (curatedWorkspace?.tasks ?? []).filter((task) => pendingTaskIds.has(task.taskId));
  }, [curatedWorkspace?.tasks, pendingScriptedReviewEdits]);
  const completedChecklistTasks = curatedWorkspace?.checklist.completedTasks ?? [];
  const completedFormalDecisions = curatedWorkspace?.checklist.completedFormalDecisions ?? [];
  const currentRevisionDiff = snapshot?.current?.revisionDiff
    ?? snapshot?.current?.revisionHistory?.find((diff) => (
      diff.afterDocumentId === snapshot.current?.document?.documentId
    ));
  const selectCuratedEvidence = (evidenceRef: string, trace?: SourceReviewTrace) => {
    const evidence = curatedReview?.evidence.find((candidate) => candidate.evidenceRef === evidenceRef);
    if (!evidence) return;
    if (selectedCuratedEvidenceRef === evidence.evidenceRef
      && (!trace || selectedCuratedTraceAnchor === trace.markdownAnchor)) {
      setSelectedCuratedEvidenceRef(undefined);
      setSelectedCuratedTraceAnchor(undefined);
      setInteractionAnnouncement('已收起来源材料。');
      return;
    }
    setSelectedCuratedEvidenceRef(evidence.evidenceRef);
    setSelectedInspectorSourceId(currentReviewSource?.sourceId);
    if (trace) setSelectedCuratedTraceAnchor(trace.markdownAnchor);
    setInteractionAnnouncement(`已打开“${evidence.title}”的来源材料。`);
    window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
      const inlineEvidence = shellRef.current?.querySelector<HTMLElement>(
        `[data-inline-evidence-ref="${evidence.evidenceRef}"]`,
      );
      inlineEvidence?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
      inlineEvidence?.focus({ preventScroll: true });
    }));
  };
  const selectFormalEvidence = (blockId: string) => {
    setSelectedBlockId(blockId);
    setSelectedCandidateEvidenceRef(candidateReviewTopicForBlock(blockId)?.evidence[0]?.evidenceRef);
    setInteractionAnnouncement('已选中当前审阅结论的来源材料。');
  };
  const reviewViewForClaim = (claimId: string): 'MATTERS' | 'CONCLUSIONS' => {
    const checklist = curatedWorkspace?.checklist;
    if (!checklist) return 'MATTERS';
    return checklist.tasks.some((item) => item.claimId === claimId)
      || checklist.completedTasks.some((item) => item.claimId === claimId)
      || checklist.gaps.some((item) => item.claimId === claimId)
      ? 'MATTERS'
      : 'CONCLUSIONS';
  };
  const focusCuratedTrace = (trace: SourceReviewTrace, claim?: Pick<ReviewClaim, 'claimId' | 'title'>) => {
    if (claim) {
      markdownReturnRef.current = {
        claimId: claim.claimId,
        label: claim.title,
        scrollTop: threadRef.current?.scrollTop ?? 0,
        view: reviewViewForClaim(claim.claimId),
      };
    } else {
      markdownReturnRef.current = undefined;
    }
    setSelectedCuratedTraceAnchor(trace.markdownAnchor);
    setDocumentView('MARKDOWN');
    setStandardizedDocumentView('READING');
    setMarkdownAnchorToFocus(trace.markdownAnchor);
  };
  const returnToReviewClaim = () => {
    const target = markdownReturnRef.current;
    if (!target) return;
    setDocumentView(target.view);
    setStandardizedDocumentView('READING');
    setMarkdownAnchorToFocus(undefined);
    window.requestAnimationFrame(() => {
      const container = threadRef.current;
      if (container) container.scrollTop = target.scrollTop;
      const claim = shellRef.current?.querySelector<HTMLElement>(`[data-review-claim="${target.claimId}"]`);
      claim?.focus({ preventScroll: true });
      claim?.scrollIntoView({ block: 'nearest', behavior: 'auto' });
      setInteractionAnnouncement(`已返回「${target.label}」。`);
    });
  };
  const focusCuratedClaim = (claim: ReviewClaim) => {
    setDocumentView(reviewViewForClaim(claim.claimId));
    setSelectedCuratedTraceAnchor(claim.markdownAnchor);
    const trace = curatedReview?.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor);
    const evidenceRef = claim.evidenceRefs[0];
    if (trace && evidenceRef) selectCuratedEvidence(evidenceRef, trace);
    revealReviewTarget(`[data-review-claim="${claim.claimId}"]`, `已定位到“${claim.title}”审阅结论。`);
  };
  const reviewPrimaryAction = curatedWorkspace && snapshot?.current?.sourceStep.status === 'DOCUMENT_READY'
    ? projectReviewPrimaryAction({
        sourceName: displaySourceName(snapshot.current.source.sourceId, snapshot.current.source.sourceName),
        pendingClaimIds: pendingScriptedReviewEdits.map((edit) => edit.definition.reviewClaimId),
        claimSections: new Map(curatedWorkspace.claims.map((claim) => [claim.claimId, claim.section])),
      })
    : undefined;
  const reviewNextSourceAvailable = Boolean(curatedWorkspace
    && ['REVIEWED', 'ALIGNED'].includes(snapshot?.current?.sourceStep.status ?? '')
    && snapshot?.nextAction.type === 'READ_NEXT_SOURCE');
  const runReviewPrimaryAction = () => {
    if (!reviewPrimaryAction) return;
    if (reviewPrimaryAction.kind === 'OPEN_PENDING_CLAIM') {
      const claim = curatedWorkspace?.claims.find((candidate) => candidate.claimId === reviewPrimaryAction.claimId);
      if (claim) focusCuratedClaim(claim);
      return;
    }
    void execute('COMPLETE_CURRENT_DOCUMENT_REVIEW');
  };
  const focusRevisionBlock = (blockId: string) => {
    const block = currentDocumentBlocks?.find((candidate) => candidate.blockId === blockId)
      ?? documentEvidenceCompilation?.blocks.find((candidate) => candidate.blockId === blockId);
    setDocumentView('MATTERS');
    setSelectedBlockId(blockId);
    setSelectedCandidateEvidenceRef(undefined);
    if (isMysqlCuratedReview) setCuratedStructuredDiffOpen(true);
    if (block) setReviewNavigation((current) => ({ ...current, selectedSection: block.section }));
    setSectionPage(1);
    setDocumentWindowPageIndex(0);
    window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
      const target = document.getElementById(
        `guanyijia-document-review:${snapshot?.current?.document?.documentId}:block:${blockId}`,
      );
      target?.scrollIntoView({ block: 'center', behavior: 'smooth' });
      target?.focus({ preventScroll: true });
    }));
  };
  const beginEditingRevisionBlock = (blockId: string) => {
    const block = currentDocumentBlocks?.find((candidate) => candidate.blockId === blockId)
      ?? documentEvidenceCompilation?.blocks.find((candidate) => candidate.blockId === blockId);
    if (!block) return;
    if (curatedReview) return;
    const valueEditor = structuredValueEditorModel(block.value);
    if (!valueEditor.editable || pendingAssistantProposal) return;
    focusRevisionBlock(blockId);
    setSelectedCandidateEvidenceRef(candidateReviewTopicForBlock(blockId)?.evidence[0]?.evidenceRef);
    setEditingBlockId(blockId);
    setEditLabel(block.label);
    setEditValueText(valueEditor.text);
    setEditNormalized(valueEditor.kind === 'TEXT_OBJECT' ? valueEditor.normalized : undefined);
    clearPreview();
  };
  const navigateToReviewSection = (key: ModelingDocumentSection, index: number) => {
    setReviewNavigation((current) => ({ ...current, selectedSection: key }));
    setSectionPage(1);
    setDocumentWindowPageIndex(0);
    setSelectedCandidateEvidenceRef(undefined);
    const sourceReviewClaim = curatedWorkspace?.claims.find((claim) => claim.section === index + 1);
    const sourceReviewTrace = sourceReviewClaim
      ? curatedReview?.traceLinks.find((trace) => trace.markdownAnchor === sourceReviewClaim.markdownAnchor)
      : undefined;
    const formalTrace = documentTraceLinks.find((trace) => trace.section === key);

    if (documentView === 'MARKDOWN') {
      if (sourceReviewTrace) {
        focusCuratedTrace(sourceReviewTrace);
      } else {
        setMarkdownAnchorToFocus(`markdown-${key}`);
        if (!documentMarkdown) void loadDocumentMarkdown();
      }
      return;
    }
    if (sourceReviewTrace) {
      setSelectedCuratedTraceAnchor(sourceReviewTrace.markdownAnchor);
      setSelectedCuratedEvidenceRef(sourceReviewTrace.evidenceRefs[0]);
    } else if (formalTrace) {
      setSelectedBlockId(formalTrace.blockId);
      return;
    }
  };
  useEffect(() => {
    if (documentView !== 'MARKDOWN' || (!documentMarkdown && !curatedReview) || !markdownAnchorToFocus) return;
    if (curatedReview && standardizedDocumentView !== 'READING') return;
    window.requestAnimationFrame(() => {
      const target = document.getElementById(markdownAnchorToFocus);
      target?.scrollIntoView({ block: 'center', behavior: 'smooth' });
      target?.focus({ preventScroll: true });
    });
  }, [curatedReview, documentMarkdown, documentView, markdownAnchorToFocus, standardizedDocumentView]);
  const inspectorSources = snapshot?.run?.sources.map((source) => ({
    sourceId: source.sourceId,
    sourceName: source.sourceName,
    status: source.status,
    sourceClass: source.sourceId === 'guanyijia_demo_policy' ? 'DEMO_POLICY' as const
      : source.sourceId === 'guanyijia_semantica_demo' ? 'DERIVED' as const : 'REAL' as const,
  })) ?? sourceBoard.slots.map((slot) => ({
    sourceId: slot.instance?.sourceId ?? `pre-run:${slot.role}`,
    sourceName: slot.displayName,
    status: slot.readState === 'NONE' ? 'PENDING' : slot.readState,
    sourceClass: slot.role === 'ERP_POLICY' ? 'DEMO_POLICY' as const
      : slot.role === 'TERMINOLOGY' ? 'DERIVED' as const : 'REAL' as const,
  }));
  const businessWorkflowItems = useMemo(() => projectBusinessJourneyTimeline({
    sources: inspectorSources.map((source): JourneySource => ({
      sourceId: source.sourceId,
      displayName: displaySourceName(source.sourceId, source.sourceName),
      status: sourceCheckpointStatus(source.status),
    })),
    timeline: snapshot?.timeline ?? [],
  }), [inspectorSources, snapshot?.timeline]);
  const reviewAssistant = <ReviewAssistantPanel
    history={assistantHistory}
    historyTotal={assistantHistoryTotal}
    historyLoaded={assistantHistoryLoaded}
    pendingProposal={assistantPendingProposal}
    draft={assistantDraft}
    busy={assistantBusy}
    canSend={Boolean(snapshot?.run)}
    canConfirm={canConfirmAssistant && !reviewMutationBlocked}
    onDraftChange={(value, selectionStart, selectionEnd) => reviewSurface.updateDraft({
      value,
      selectionStart,
      selectionEnd,
    })}
    onSend={() => void askAssistant()}
    onConfirm={(item) => void confirmAssistantProposal(item)}
    onCancel={(item) => void cancelAssistantProposal(item)}
    onOpenTarget={(target, trigger) => openAssistantTarget(target, snapshot, trigger)}
    onLoadHistory={() => {
      const context = assistantHistoryContextFor(snapshot, currentUser.userId);
      if (context) void reloadAssistantHistory(context);
    }}
  />;

  // A corrupt or unavailable body means there are no verified blocks to render.
  // Keep the failure beside the affected document instead of falling back to a
  // page-wide storage/index message or silently collapsing the document layer.
  const documentFailurePanel = documentOpen && snapshot?.current?.document && (documentWindowFailure || curatedValidationFailed)
    ? <section
      className="guanyijia-document-review guanyijia-document-review-unavailable"
      aria-label="当前来源文档"
    >
      <header className="guanyijia-document-review-header">
        <BackAction className="guanyijia-document-back" destination="时间线" onBack={() => closeLayer('document')} />
        <div
          id={`guanyijia-document-review:${snapshot.current.document.documentId}:heading`}
          className="guanyijia-document-focus-target"
          tabIndex={-1}
        >
          <h2>{displaySourceName(snapshot.current.source.sourceId, snapshot.current.source.sourceName)}来源文档</h2>
          <p>当前文档正文无法用于审阅。</p>
        </div>
      </header>
      <Alert
        className="guanyijia-document-recovery"
        type="error"
        showIcon
        data-error-code={curatedValidationFailed ? 'CURATED_VALIDATION' : documentWindowFailure?.code}
        message={documentRecoveryMessage}
        description={curatedValidationFailed
          ? '旧模板正文已隐藏；完成审阅和修改操作已禁用。重新校验仅读取当前冻结审阅对象。'
          : '正文完整性校验失败；修改、冲突处理和定版已阻止。恢复后可重新读取文档。'}
        action={curatedValidationFailed
          ? <Button size="small" onClick={() => setCuratedValidationRetry((retry) => retry + 1)}>重新校验</Button>
          : <Button size="small" onClick={() => void recoverReviewWindowStream('DOCUMENT_BLOCKS')}>重新读取文档</Button>}
      />
    </section>
    : null;

  const documentPanel = documentOpen && snapshot?.current?.document
    && !curatedValidationFailed && (currentDocumentBlocks || snapshot.current.legacyReadOnly) && pagedBlocks
    ? <section
      className="guanyijia-document-review"
      aria-label="当前来源文档"
    >
      <header className="guanyijia-document-review-header">
        <BackAction className="guanyijia-document-back" destination="时间线" onBack={() => closeLayer('document')} />
        <div
          id={`guanyijia-document-review:${snapshot.current.document.documentId}:heading`}
          className="guanyijia-document-focus-target"
          tabIndex={-1}
        >
          <Tag color={snapshot.current.document.status === 'SUPERSEDED' ? 'default' : snapshot.current.sourceStep.status === 'ALIGNED' ? 'green' : 'blue'}>
            {snapshot.current.document.status === 'SUPERSEDED' ? '只读回看' : snapshot.current.sourceStep.status === 'ALIGNED' ? '已审阅' : '待审阅'}
          </Tag>
          <Tag>{sourceOriginLabel(snapshot.current.source.sourceId)}</Tag>
          <h2>{curatedWorkspace?.metadata.title ?? curatedReview?.title ?? `${displaySourceName(snapshot.current.source.sourceId, snapshot.current.source.sourceName)}来源文档`}</h2>
          <p>{curatedWorkspace
            ? `来源范围：${curatedWorkspace.metadata.coverageLabel}`
            : '当前可读内容已准备好；结论与来源材料会在审阅事项和审阅结论中逐项展开。'}</p>
        </div>
        <div className="guanyijia-document-header-actions">
          {(snapshot.current.history?.length ?? 0) > 1 && <Button type="link" onClick={() => setHistoryOpen((open) => !open)}>{historyOpen ? '收起历史版本' : '查看历史版本'}</Button>}
          {reviewPrimaryAction && !editingBlockId && !editingScriptedEditId && !pendingAssistantProposal && <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={runReviewPrimaryAction}>{reviewPrimaryAction.label}</Button>}
          {!reviewPrimaryAction && reviewNextSourceAvailable && !editingBlockId && !editingScriptedEditId && !pendingAssistantProposal && <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={() => void execute('READ_NEXT_SOURCE')}>审阅下一个来源</Button>}
          {!curatedWorkspace && canCompleteReview && !editingBlockId && !editingScriptedEditId && !pendingAssistantProposal && <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={() => void execute('COMPLETE_CURRENT_DOCUMENT_REVIEW')}>完成{displaySourceName(snapshot.current.source.sourceId, snapshot.current.source.sourceName)}审阅</Button>}
        </div>
      </header>
      {documentWindowFailure && <Alert
        className="guanyijia-document-recovery"
        type="error"
        showIcon
        message={documentRecoveryMessage}
        description="当前文档未显示或无法保存；恢复后再继续修改、处理差异或定版。"
        action={<Button size="small" onClick={() => void recoverReviewWindowStream('DOCUMENT_BLOCKS')}>重新读取文档</Button>}
      />}
      <div className="guanyijia-document-tabs" role="tablist" aria-label="来源文档视图">
        {([['MATTERS', '审阅事项'], ['CONCLUSIONS', '审阅结论'], ['MARKDOWN', '标准化文档']] as const).map(([value, label]) => <button
          type="button" role="tab" aria-selected={documentView === value}
          className={documentView === value ? 'active' : ''} key={value}
          onClick={() => {
            setDocumentView(value);
            if (value === 'MARKDOWN' && !curatedReview && !documentMarkdown) void loadDocumentMarkdown();
          }}
        >{label}</button>)}
      </div>
      {historyOpen && <section className="guanyijia-revision-history" aria-label="历史版本">
        {snapshot.current.history?.map((document) => {
          const diff = snapshot.current?.revisionHistory?.find((entry) => entry.afterDocumentId === document.documentId);
          return <article key={document.documentId}>
            <header><strong>第 {document.revision} 版</strong><span>{document.status === 'SUPERSEDED' ? '历史版本' : '当前版本'}</span><small>创建于 {formatRevisionCreatedAt(document.createdAt)}</small></header>
            {diff && <details className="guanyijia-revision-diff">
              <summary>查看本版修改</summary>
              {diff.blockChanges.map((change) => <div key={change.blockId}>
                <span>修改前</span><p><strong>{change.before?.label ?? '不存在'}</strong> · {change.before ? blockText(change.before.value) : '不存在'}</p>
                <span>修改后</span><p><strong>{change.after?.label ?? '不存在'}</strong> · {change.after ? blockText(change.after.value) : '不存在'}</p>
              </div>)}
            </details>}
          </article>;
        })}
      </section>}
      {!curatedWorkspace && <nav className="guanyijia-section-directory" aria-label="来源文档章节">
        {standardSectionOrder.map(({ key, heading }, index) => ({
          key, heading, index,
          count: currentDocumentBlocks
            ? currentDocumentBlocks.filter((block) => block.section === key).length
            : snapshot.current!.legacyReadOnly!.assertions.filter((assertion) => assertion.section === key).length,
          active: selectedSection === key,
        })).map(({ key, heading, index, count, active }) => <button
          type="button"
          key={key}
          className={active ? 'active' : ''}
          aria-current={active ? 'page' : undefined}
          onClick={() => navigateToReviewSection(key as ModelingDocumentSection, index)}
        ><span>{index + 1}</span><strong>{heading}</strong><small>{currentDocumentBlocks ? `${count}项` : `${count}条断言`}</small></button>)}
      </nav>}
      {documentView === 'MARKDOWN' && <div className="guanyijia-readable-markdown-panel">
        {standardizedDocumentValidationFailed ? <Alert
          type="error"
          showIcon
          message="标准化文档校验失败"
          description="本次冻结的标准化文档无法验证，请重新读取当前来源。"
        /> : standardizedDocument ? <StandardizedDocumentReader
          document={standardizedDocument}
          view={standardizedDocumentView}
          onViewChange={setStandardizedDocumentView}
          onReturn={markdownReturnRef.current ? returnToReviewClaim : undefined}
        /> : <>{documentMarkdownLoading && <Spin size="small" />}
          {documentMarkdown ? <SourceDocumentReadable
            content={documentMarkdown}
            blocks={documentEvidenceCompilation?.blocks}
            traceLinks={documentTraceLinks}
            evidence={{
              traceLinks: formalEvidenceTraces,
              onEvidenceSelect: (trace) => {
                if (trace.blockId) selectFormalEvidence(trace.blockId);
              },
            }}
          /> : !documentMarkdownLoading && <Empty description="选择完整 Markdown 读取文档正文" />}</>}
      </div>}
      {documentView !== 'MARKDOWN' && <div className="guanyijia-section-content">
        {documentView === 'CONCLUSIONS' && crossSourceConclusionFindings.length > 0 && <section className="guanyijia-review-findings">
          <CrossSourceFindingList
            findings={crossSourceConclusionFindings}
            expansionKey={`${snapshot.current.document.documentId}:${snapshot.current.document.revision}:${currentReviewSource?.sourceId ?? ''}`}
          />
        </section>}
        {curatedReview ? curatedStructuredDiffOpen ? <section className="guanyijia-curated-structured-diff" aria-label="原有结构化修改">
          <header><div><span>本次修改</span><h3>审阅文档变化</h3></div><BackAction destination="审阅事项" onBack={() => setCuratedStructuredDiffOpen(false)} /></header>
          <p>这里显示本次确认后更新的审阅结论。</p>
          {currentRevisionDiff?.blockChanges.length ? currentRevisionDiff.blockChanges.map((change) => {
            const block = change.after ?? change.before;
            if (!block) return null;
            return <article
              id={`guanyijia-document-review:${snapshot.current!.document!.documentId}:block:${change.blockId}`}
              className={selectedBlockId === change.blockId ? 'selected' : ''}
              key={change.blockId}
              tabIndex={-1}
            >
              <strong>{block.label}</strong>
              <p><b>修改前：</b>{change.before ? blockText(change.before.value) : '不存在'}</p>
              <p><b>修改后：</b>{change.after ? blockText(change.after.value) : '不存在'}</p>
            </article>;
          }) : <Empty description="本次没有修改审阅结论。" />}
        </section> : <section className="guanyijia-review-checklist" aria-label={documentView === 'MATTERS' ? '审阅事项' : '审阅结论'}>
          {documentView === 'MATTERS' && pendingChecklistTasks.length ? <section className="guanyijia-checklist-group" aria-label="待处理事项">
            <header><h4>待处理事项</h4><small>{pendingChecklistTasks.length} 项需要处理</small></header>
            {pendingChecklistTasks.map((task) => {
              const claim = curatedWorkspace!.claims.find((candidate) => candidate.claimId === task.claimId);
              const scriptedEdit = scriptedReviewEdits.find((candidate) => candidate.definition.editId === task.taskId);
              if (!claim || !scriptedEdit) return null;
              const trace = curatedReview.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor);
              const evidenceRef = claim.evidenceRefs[0];
              const evidenceOpen = selectedCuratedTraceAnchor === claim.markdownAnchor
                && selectedCuratedEvidenceRef === evidenceRef;
              const isEditing = editingScriptedEditId === task.taskId;
              const editedTitle = scriptedEdit.definition.editableFields.includes('label') ? scriptedEditLabel : task.current.title;
              const editedStatement = scriptedEdit.definition.editableFields.includes('text') ? scriptedEditText : task.current.statement;
              const editableLabels = task.editableFields.map((field) => field.label).join('、');
              return <article
                data-review-claim={claim.claimId}
                id={`guanyijia-review-claim:${claim.claimId}`}
                key={claim.claimId}
                tabIndex={-1}
              >
                {!isEditing ? <>
                  <div><span>当前结论</span><h5>{task.current.title}</h5><p>{task.current.statement}</p></div>
                  <div><span>推荐修改</span><h5>{task.recommendation.title}</h5>{task.recommendation.statement !== task.current.statement && <p>{task.recommendation.statement}</p>}</div>
                  <div><span>为什么需要核对</span><p>{task.reason}</p></div>
                  {evidenceOpen && <section className="guanyijia-inline-evidence" aria-label="来源依据" data-inline-evidence-ref={evidenceRef} tabIndex={-1}>
                    {claim.evidenceRefs.map((reference) => {
                      const evidence = curatedReview.evidence.find((entry) => entry.evidenceRef === reference);
                      if (!evidence) return null;
                      return <SourceReviewEvidenceDetails
                        key={`${task.taskId}:${reference}`}
                        source={evidence}
                        view={reviewEvidenceViewForClaim(curatedWorkspace!.evidenceViews, reference, claim.claimId)}
                      />;
                    })}
                  </section>}
                  <footer><Button type="link" data-review-focus={`curated-evidence:${claim.claimId}`} onClick={() => selectCuratedEvidence(evidenceRef!, trace)}>{evidenceOpen ? '收起来源依据' : '查看来源依据'}</Button>{scriptedEdit.status === 'PENDING' ? <><Button type="text" disabled={reviewMutationBlocked || busy} onClick={() => void keepScriptedReviewEdit(task.taskId)}>保留当前结论</Button><Button type="primary" disabled={reviewMutationBlocked || busy} onClick={() => openScriptedReviewEditor(task.taskId)}>采用推荐修改</Button></> : <span>已{scriptedEdit.status === 'APPLIED' ? '采用推荐修改' : '核对并保留当前结论'}。</span>}</footer>
                </> : <section
                  className="guanyijia-block-editor guanyijia-scripted-review-editor"
                  aria-label={`修改${task.current.title}`}
                  data-scripted-edit-id={task.taskId}
                  role="region"
                >
                  <header><div><span>建议核对</span><h4>可修改：{editableLabels}</h4></div></header>
                  <p>{curatedReview.sourceId === 'guanyijia_mysql'
                    ? '保存后，审阅事项、审阅结论和标准化文档中的这条名称会更新。'
                    : '保存后，审阅事项、审阅结论和标准化文档中的这条结论会同步更新。'}</p>
                  {scriptedEdit.definition.editableFields.includes('label') && <label>
                    <span>{task.editableFields.find((field) => field.field === 'label')?.label ?? '名称'}</span><Input autoFocus value={scriptedEditLabel} onChange={(event) => { setScriptedEditLabel(event.target.value); clearPreview(); }} />
                  </label>}
                  {scriptedEdit.definition.editableFields.includes('text') && <label>
                    <span>{task.editableFields.find((field) => field.field === 'text')?.label ?? '说明'}</span><Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} value={scriptedEditText} onChange={(event) => { setScriptedEditText(event.target.value); clearPreview(); }} />
                  </label>}
                  <Button type="link" onClick={() => selectCuratedEvidence(evidenceRef!, trace)}>{evidenceOpen ? '收起来源依据' : '查看来源依据'}</Button>
                  {evidenceOpen && <section className="guanyijia-inline-evidence" aria-label="来源依据" data-inline-evidence-ref={evidenceRef} tabIndex={-1}>
                    {claim.evidenceRefs.map((reference) => {
                      const evidence = curatedReview.evidence.find((entry) => entry.evidenceRef === reference);
                      if (!evidence) return null;
                      return <SourceReviewEvidenceDetails
                        key={`${task.taskId}:${reference}`}
                        source={evidence}
                        view={reviewEvidenceViewForClaim(curatedWorkspace!.evidenceViews, reference, claim.claimId)}
                      />;
                    })}
                  </section>}
                  <div className="guanyijia-editor-actions">
                    {!snapshot.scriptedEditPreviewSha256 ? <>
                      <Button type="text" onClick={() => { setEditingScriptedEditId(undefined); clearPreview(); }}>取消</Button>
                      <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked || busy
                        || (scriptedEdit.definition.editableFields.includes('label') && !scriptedEditLabel.trim())
                        || (scriptedEdit.definition.editableFields.includes('text') && !scriptedEditText.trim())} loading={busy} onClick={() => void previewScriptedReviewEdit()}>预览修改</Button>
                    </> : <>
                      <Button type="text" onClick={clearPreview}>继续编辑</Button>
                      <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={() => void applyScriptedReviewEdit()}>确认修改</Button>
                    </>}
                  </div>
                  {snapshot.scriptedEditPreviewSha256 && <section className="guanyijia-change-preview" aria-label="修改预览">
                    <h4>修改预览</h4>
                    <div className="guanyijia-before-after">
                      <div><span>业务名称 · 修改前</span><strong>{task.current.title}</strong><p>{task.current.statement}</p></div>
                      <div><span>业务名称 · 修改后</span><strong>{editedTitle}</strong><p>{editedStatement}</p></div>
                    </div>
                    <section className="guanyijia-scripted-markdown-preview" aria-label={`Markdown · ${task.markdownChange.sectionTitle}`}>
                      <strong>Markdown · {task.markdownChange.sectionTitle}</strong>
                      <p className="removed">− {task.current.title}：{task.current.statement}</p>
                      <p className="added">+ {editedTitle}：{editedStatement}</p>
                    </section>
                  </section>}
                </section>}
              </article>;
            })}
          </section> : null}
          {documentView === 'MATTERS' && <>
          {(curatedWorkspace?.checklist.gaps.length || crossSourceGapFindings.length) ? <section className="guanyijia-checklist-group" aria-label="资料缺口">
            <header><h4>资料缺口</h4></header>
            {curatedWorkspace?.checklist.gaps.length ? <ul className="guanyijia-gap-list">{curatedWorkspace.checklist.gaps.map((gap) => {
              const claim = curatedWorkspace.claims.find((candidate) => candidate.claimId === gap.claimId);
              const trace = claim
                ? curatedReview.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor)
                : undefined;
              const evidenceRef = claim?.evidenceRefs[0];
              const evidenceOpen = Boolean(claim && evidenceRef
                && selectedCuratedTraceAnchor === claim.markdownAnchor
                && selectedCuratedEvidenceRef === evidenceRef);
              return <li key={gap.claimId} data-review-claim={gap.claimId} tabIndex={-1}>
                <strong>{gap.title}</strong><p>{gap.statement}</p><small>下一步：{gap.nextStep}</small>
                {claim && evidenceRef && <div><Button type="link" onClick={() => selectCuratedEvidence(evidenceRef, trace)}>{evidenceOpen ? '收起来源依据' : '查看来源依据'}</Button>{trace && <Button type="link" onClick={() => focusCuratedTrace(trace, claim)}>查看文档段落</Button>}</div>}
                {claim && evidenceOpen && <section className="guanyijia-inline-evidence" aria-label={`${claim.title}的来源依据`} data-inline-evidence-ref={evidenceRef} tabIndex={-1}>{claim.evidenceRefs.map((reference) => {
                  const evidence = curatedReview.evidence.find((entry) => entry.evidenceRef === reference);
                  return evidence ? <SourceReviewEvidenceDetails key={`${claim.claimId}:${reference}`} source={evidence} view={reviewEvidenceViewForClaim(curatedWorkspace.evidenceViews, reference, claim.claimId)} /> : null;
                })}</section>}
              </li>;
            })}</ul> : null}
            {crossSourceGapFindings.length > 0 && <CrossSourceFindingList
              findings={crossSourceGapFindings}
              expansionKey={`${snapshot.current.document.documentId}:${snapshot.current.document.revision}:${currentReviewSource?.sourceId ?? ''}`}
            />}
          </section> : null}
          {(completedChecklistTasks.length || completedFormalDecisions.length) ? <section className="guanyijia-checklist-group" aria-label="已处理">
            <header><h4>已处理</h4><small>{completedChecklistTasks.length + completedFormalDecisions.length} 项已保存</small></header>
            <ul className="guanyijia-key-conclusion-list">{completedChecklistTasks.map((task) => {
              const claim = curatedWorkspace!.claims.find((candidate) => candidate.claimId === task.claimId);
              const decision = scriptedReviewEdits.find((candidate) => candidate.definition.editId === task.scriptedEditId);
              if (!claim || !decision) return null;
              const trace = curatedReview.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor);
              const evidenceRef = claim.evidenceRefs[0];
              const evidenceOpen = selectedCuratedTraceAnchor === claim.markdownAnchor
                && selectedCuratedEvidenceRef === evidenceRef;
              return <li key={task.claimId} data-review-claim={task.claimId} id={`guanyijia-review-claim:${task.claimId}`} tabIndex={-1}>
                <div><strong>{task.title}</strong><p>{task.statement}</p><small>{decision.status === 'APPLIED' ? '已采用推荐修改。' : '已核对并保留当前结论。'}</small></div>
                <div><Button type="link" onClick={() => selectCuratedEvidence(evidenceRef!, trace)}>{evidenceOpen ? '收起来源依据' : '查看来源依据'}</Button>{trace && <Button type="link" onClick={() => focusCuratedTrace(trace, claim)}>查看文档段落</Button>}</div>
                {evidenceOpen && <section className="guanyijia-inline-evidence" aria-label={`${task.title}的来源依据`} data-inline-evidence-ref={evidenceRef} tabIndex={-1}>{claim.evidenceRefs.map((reference) => {
                  const evidence = curatedReview.evidence.find((entry) => entry.evidenceRef === reference);
                  return evidence ? <SourceReviewEvidenceDetails key={`${task.claimId}:${reference}`} source={evidence} view={reviewEvidenceViewForClaim(curatedWorkspace!.evidenceViews, reference, task.claimId)} /> : null;
                })}</section>}
              </li>;
            })}{completedFormalDecisions.map((decision) => <li key={decision.decisionId} data-review-formal-decision={decision.decisionId} tabIndex={-1}>
              <div><strong>{decision.title}</strong><p>{decision.statement}</p><small>已保存的跨来源决定。</small></div>
            </li>)}</ul>
          </section> : null}
          </>}
          {documentView === 'CONCLUSIONS' && <section className="guanyijia-review-conclusion-set">
          {curatedWorkspace?.checklist.keyConclusions.length ? <section className="guanyijia-checklist-group" aria-label="关键业务结论">
            <header><h4>关键业务结论</h4></header>
            <ul className="guanyijia-key-conclusion-list">{curatedWorkspace.checklist.keyConclusions.map((claim) => {
              const trace = curatedReview.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor);
              const evidenceOpen = selectedCuratedTraceAnchor === claim.markdownAnchor
                && selectedCuratedEvidenceRef === claim.evidenceRefs[0];
              return <li key={claim.claimId} data-review-claim={claim.claimId} id={`guanyijia-review-claim:${claim.claimId}`} tabIndex={-1}>
                <div><strong>{claim.title}</strong><p>{claim.statement}</p></div>
                <div><Button type="link" onClick={() => selectCuratedEvidence(claim.evidenceRefs[0]!, trace)}>{evidenceOpen ? '收起来源依据' : '查看来源依据'}</Button>{trace && <Button type="link" onClick={() => focusCuratedTrace(trace, claim)}>查看文档段落</Button>}</div>
                {evidenceOpen && <section className="guanyijia-inline-evidence" aria-label={`${claim.title}的来源依据`} data-inline-evidence-ref={claim.evidenceRefs[0]} tabIndex={-1}>
                  {claim.evidenceRefs.map((reference) => {
                    const evidence = curatedReview.evidence.find((entry) => entry.evidenceRef === reference);
                    if (!evidence) return null;
                    return <SourceReviewEvidenceDetails
                      key={`${claim.claimId}:${reference}`}
                      source={evidence}
                      view={reviewEvidenceViewForClaim(curatedWorkspace.evidenceViews, reference, claim.claimId)}
                    />;
                  })}
                </section>}
              </li>;
            })}</ul>
          </section> : null}
          {curatedWorkspace?.checklist.objectDetails.length ? <details open className="guanyijia-checklist-group guanyijia-object-details" aria-label="对象明细">
            <summary>对象目录 · {curatedWorkspace.checklist.objectDetails.length} 个对象</summary>
            <div className="guanyijia-object-details-scroll"><table><thead><tr><th>类型</th><th>中文名称</th><th>业务用途</th><th>重点字段／规则</th><th>来源</th></tr></thead><tbody>{curatedWorkspace?.checklist.objectDetails.map((claim) => {
              const evidence = curatedReview.evidence.find((entry) => entry.evidenceRef === claim.evidenceRefs[0]);
              const trace = curatedReview.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor);
              if (!evidence) return null;
              const type = evidence.title.includes('过程') ? '存储过程' : evidence.title.includes('表') || evidence.title.includes('DDL') ? '数据表' : evidence.excerptKind === 'DOCUMENT_SECTION' ? '文档主题' : '业务规则';
              const evidenceOpen = selectedCuratedTraceAnchor === claim.markdownAnchor
                && selectedCuratedEvidenceRef === evidence.evidenceRef;
              return <Fragment key={claim.claimId}>
                <tr data-review-claim={claim.claimId} tabIndex={-1}><td>{type}</td><th>{claim.title}</th><td>{claim.statement}</td><td>{claim.focusIdentifiers.length ? claim.focusIdentifiers.join('、') : '相关业务规则'}</td><td><Button type="link" size="small" onClick={() => selectCuratedEvidence(evidence.evidenceRef, trace)}>{evidenceOpen ? '收起依据' : '查看依据'}</Button>{trace && <Button type="link" size="small" onClick={() => focusCuratedTrace(trace, claim)}>查看文档</Button>}</td></tr>
                {evidenceOpen && <tr><td colSpan={5}><section className="guanyijia-inline-evidence" aria-label={`${claim.title}的来源依据`} data-inline-evidence-ref={evidence.evidenceRef} tabIndex={-1}>{claim.evidenceRefs.map((reference) => {
                  const source = curatedReview.evidence.find((entry) => entry.evidenceRef === reference);
                  return source ? <SourceReviewEvidenceDetails key={`${claim.claimId}:${reference}`} source={source} view={reviewEvidenceViewForClaim(curatedWorkspace.evidenceViews, reference, claim.claimId)} /> : null;
                })}</section></td></tr>}
              </Fragment>;
            })}</tbody></table></div>
          </details> : null}
          </section>}
        </section> : <section className="guanyijia-curated-object-list guanyijia-formal-object-list" aria-label="识别结果">
        <header><div><span>识别结果</span><h3>{standardSectionOrder.find(({ key }) => key === selectedSection)?.heading}</h3></div><small>显示 {pagedBlocks.items.length} / {pagedBlocks.total} 项</small></header>
        <p>每项都列出本来源的识别结论、依据位置、影响对象，以及可执行的下一步操作。</p>
        {snapshot.current.legacyReadOnly ? <section className="guanyijia-legacy-document-section">
          <Alert
            type="info"
            showIcon
            message="旧版来源文档仅支持只读"
            description="该文档没有结构化块，不能在新工作台修改或完成审阅。"
          />
          <p>{snapshot.current.legacyReadOnly.sections[selectedSection]}</p>
        </section> : !pagedBlocks.items.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本来源在该章节没有直接识别结果" />
          : <div>{pagedBlocks.items.map((block) => {
              const valueEditor = structuredValueEditorModel(block.value);
              const evidence = buildHumanReadableEvidence(documentEvidenceCompilation!, block.blockId);
              const trace = documentTraceLinks.find((candidate) => candidate.blockId === block.blockId);
              const canEditConclusion = valueEditor.editable && !pendingAssistantProposal && !curatedReview;
              return <article
                className={selectedBlockId === block.blockId ? 'selected' : ''}
                id={`guanyijia-document-review:${snapshot.current!.document!.documentId}:block:${block.blockId}`}
                key={block.blockId}
                data-review-anchor={`document-block:${block.blockId}`}
                data-review-focus={`guanyijia-block-inspect:${block.blockId}`}
                tabIndex={-1}
              >
                <span>{evidenceStatusLabel[block.evidenceStatus]} · {standardSectionOrder.find(({ key }) => key === block.section)?.heading}</span>
                <h4>{block.label}</h4>
                <p>{blockText(block.value)}</p>
                {evidence && <dl><div><dt>结论状态</dt><dd>{evidenceStatusLabel[block.evidenceStatus]}</dd></div><div><dt>{evidence.locationLabel}</dt><dd>{evidence.locationValue}</dd></div></dl>}
                <div className="guanyijia-impact-list"><span>影响对象</span>{block.affectedObjectRefs.length
                  ? block.affectedObjectRefs.map((reference) => <Tag key={`${reference.kind}:${reference.objectId}`}>{reference.kind}：{reference.objectId}</Tag>)
                  : <span>未关联模型对象</span>}</div>
                <footer>{trace && <Button type="link" size="small" onClick={() => {
                  setDocumentView('MARKDOWN');
                  setMarkdownAnchorToFocus(trace.markdownAnchor);
                  if (!documentMarkdown) void loadDocumentMarkdown();
                }}>在文档中定位</Button>}<Button type="link" size="small" onClick={() => selectFormalEvidence(block.blockId)}>查看来源依据</Button>
                {canEditConclusion
                  ? <Button type="link" size="small" disabled={reviewMutationBlocked} onClick={() => beginEditingRevisionBlock(block.blockId)}>修改识别结论</Button>
                  : !curatedReview && <Tag>只读</Tag>}</footer>
              </article>;
            })}</div>}
        {!snapshot.current.legacyReadOnly && editingBlock && editorModel?.editable && <section className="guanyijia-block-editor" aria-label="修改当前识别结论">
          <header><div><span>修改识别结论</span><h4>{editingBlock.label}</h4></div><Tag>{editorModel.kind === 'STRING' ? '文本结果' : '结构化文本结果'}</Tag></header>
          <label><span>名称</span><Input value={editLabel} onChange={(event) => { setEditLabel(event.target.value); clearPreview(); }} /></label>
          <label><span>识别结果</span><Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} value={editValueText} onChange={(event) => { setEditValueText(event.target.value); clearPreview(); }} /></label>
          {editorModel.kind === 'TEXT_OBJECT' && editorModel.normalized !== undefined && <label>
            <span>标准值（用于规则与冲突重算）</span>
            <Input value={editNormalized} onChange={(event) => { setEditNormalized(event.target.value); clearPreview(); }} />
          </label>}
          {editorModel.kind === 'TEXT_OBJECT' && Object.keys(editorModel.readonlyFields).length > 0 && <p className="guanyijia-editor-locked-fields">本次可修改：名称和结论说明。确认后，审阅文档会同步更新对应段落。</p>}
          <div className="guanyijia-editor-actions">
            {!snapshot.preview ? <>
              <Button type="text" onClick={() => { setEditingBlockId(undefined); clearPreview(); }}>取消</Button>
              <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked || !editLabel.trim() || !editValueText.trim()
                || (editorModel.kind === 'TEXT_OBJECT' && editorModel.normalized !== undefined && !editNormalized?.trim())} loading={busy} onClick={() => void previewEdit()}>预览修改</Button>
            </> : <>
              <Button type="text" onClick={clearPreview}>继续编辑</Button>
              <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={() => void applyEdit()}>确认修改</Button>
            </>}
          </div>
          {snapshot.preview && <section className="guanyijia-change-preview" aria-label="修改预览">
            <h4>修改预览</h4>
            <div className="guanyijia-before-after">
              <div><span>修改前</span><strong>{snapshot.preview.block.before.label}</strong><p>{blockText(snapshot.preview.block.before.value)}</p></div>
              <div><span>修改后</span><strong>{snapshot.preview.block.after.label}</strong><p>{blockText(snapshot.preview.block.after.value)}</p></div>
              <div><span>修改前结论</span><p>{snapshot.preview.assertion.before.statement}</p></div>
              <div><span>修改后结论</span><p>{snapshot.preview.assertion.after.statement}</p></div>
            </div>
            <p className="guanyijia-document-change-note">确认后，标准化文档会同步更新这条结论所在的段落。</p>
            <div className="guanyijia-impact-preview">
              <div><strong>受影响冲突</strong>{snapshot.preview.affectedConflicts.length
                ? snapshot.preview.affectedConflicts.map((conflict) => <p key={conflict.conflictId}>{conflict.title}：{conflict.beforeVariant ?? '无'} → {conflict.afterVariant ?? '无'}</p>)
                : <p>没有新增或改变来源冲突</p>}</div>
              <div><strong>受影响对象</strong>{snapshot.preview.affectedObjects.length
                ? snapshot.preview.affectedObjects.map((object) => <p key={object}>{object}</p>)
                : <p>没有直接关联模型对象</p>}</div>
            </div>
          </section>}
        </section>}
        {pagedBlocks.pageCount > 1 && <div className="guanyijia-document-pagination">
        <Button disabled={pagedBlocks.page === 1} onClick={() => currentDocumentBlocks
            ? setDocumentWindowPageIndex((index) => Math.max(0, index - 1))
            : setSectionPage((page) => page - 1)}>上一页</Button>
          <span>{pagedBlocks.page} / {pagedBlocks.pageCount}</span>
          <Button disabled={pagedBlocks.page === pagedBlocks.pageCount} onClick={() => currentDocumentBlocks
            ? void loadNextDocumentWindow()
            : setSectionPage((page) => page + 1)}>下一页</Button>
        </div>}
        </section>}
      </div>}
    </section>
    : null;

  const sourcePanelToggleLabel = inspectorLayered || inspectorCollapsed
    ? '来源资料'
    : '收起来源资料';

  return <div
    ref={shellRef}
    className={`guanyijia-workbench-shell${mobile ? ' mobile-surface' : ''}${compactInspector ? ' inspector-compact' : ''}${documentOpen ? ' document-open' : ''}${inspectorOpen ? ' inspector-open' : ''}${inspectorCollapsed ? ' inspector-collapsed' : ''}`}
    data-review-layout={reviewLayout.mode.toLowerCase()}
    data-review-density={reviewLayout.density.toLowerCase()}
    data-review-scroll-owner={reviewLayout.scrollOwner.toLowerCase()}
    data-review-mutations-blocked={String(reviewMutationBlocked)}
    data-evidence-window-total={evidenceWindow?.total ?? 0}
  >
    <Card className="guanyijia-workbench-card" styles={{ body: { padding: 0 } }}>
      <header className="guanyijia-workbench-bar" data-review-inertable>
        <div className="guanyijia-workbench-progress"><span>已读取 {readCount}/5 · 已审阅 {reviewedCount}/5</span></div>
        <div className="guanyijia-workbench-bar-actions">
          <Button
            id="guanyijia-inspector-trigger"
            data-source-panel-toggle="true"
            type="link"
            icon={<EyeOutlined />}
            aria-label={sourcePanelToggleLabel}
            aria-expanded={inspectorLayered ? inspectorOpen : !inspectorCollapsed}
            onClick={(event) => {
              if (!inspectorLayered) {
                setInspectorCollapsed((collapsed) => !collapsed);
                return;
              }
              if (inspectorOpen) {
                closeLayer('inspector');
                return;
              }
              reviewSurface.open({
                kind: 'INSPECTOR', stableId: `inspector:${selectedBlockId ?? selectedTimelineItemId ?? 'current'}`,
                revision: snapshot?.run?.revision,
                focusId: 'guanyijia-facts-inspector:close',
              }, event.currentTarget);
            }}
          >{sourcePanelToggleLabel}</Button>
          <Button
            type="link"
            icon={<SettingOutlined />}
            aria-label={visibleSnapshotAction.label}
            onClick={() => setSourceCenterOpen(true)}
          >{visibleSnapshotAction.label}</Button>
        </div>
      </header>
      <div className="guanyijia-workbench-layout">
        <main
          className="guanyijia-workbench-thread"
          role={mobile && documentLayer ? 'dialog' : undefined}
          aria-label={mobile && documentLayer ? '当前来源文档' : undefined}
          aria-modal={mobile && documentLayer ? true : undefined}
          data-review-layer-id={mobile ? documentLayer?.stableId : undefined}
          data-review-inertable={mobile && documentLayer ? true : undefined}
          tabIndex={mobile && documentLayer ? -1 : undefined}
        >
          <div ref={threadRef} className="guanyijia-workbench-scroll">
            {error && <Alert role="alert" className="guanyijia-workbench-error" type="error" showIcon title="当前步骤需要处理" description={error} />}
            <Spin spinning={busy && !snapshot}>
            <div className="guanyijia-thread-content">
              {historicalResolution && !documentOpen && <section
                className="guanyijia-next-task guanyijia-historical-decision"
                data-review-anchor={`historical-conflict:${historicalResolution.hunk.conflictId}`}
                aria-label={`已保存的来源差异决定：${historicalResolution.hunk.title}`}
                tabIndex={-1}
              >
                <header>
                  <span>已保存的来源差异决定</span>
                  <Button type="link" onClick={() => setHistoricalResolution(undefined)}>关闭</Button>
                </header>
                <h2>{historicalResolution.hunk.title}</h2>
                <p>这是已保存的只读记录，保留了当时比较的双方材料与决定结果。</p>
                <div className="guanyijia-historical-decision-sides">
                  {[historicalResolution.hunk.current, historicalResolution.hunk.incoming].map((side) => <article key={side.role}>
                    <span>{side.role === 'CURRENT' ? '当时采用的来源' : '参与比较的新来源'}</span>
                    <h3>{displaySourceName(side.sourceId, side.sourceName)}</h3>
                    <strong>{side.block.label}</strong>
                    <p>{blockText(side.block.value)}</p>
                    <Button type="link" onClick={() => selectSourceFromInspector(side.sourceId)}>查看来源材料</Button>
                  </article>)}
                </div>
                <article className="guanyijia-historical-decision-result">
                  <span>决定后的结论</span>
                  {historicalResolution.result.blocks.map((block) => <div key={block.blockId}>
                    <strong>{block.label}</strong>
                    <p>{blockText(block.value)}</p>
                  </div>)}
                </article>
                {snapshot?.run?.status === 'READY_FOR_OUTPUT' && !snapshot.run.reviewId && <Button
                  type="primary"
                  onClick={() => {
                    setReplacementResolution(historicalResolution);
                    setHistoricalResolution(undefined);
                    setInteractionAnnouncement('已打开这项来源差异，可选择新的决定并重新生成标准化结果。');
                    window.requestAnimationFrame(() => document.querySelector<HTMLElement>(
                      `[data-review-anchor="conflict:${historicalResolution.hunk.conflictId}"]`,
                    )?.scrollIntoView({ block: 'start', behavior: 'smooth' }));
                  }}
                >重新处理此差异</Button>}
              </section>}
              {historicalDeliverable && !documentOpen && <section
                className="guanyijia-next-task guanyijia-historical-deliverable"
                data-review-anchor="historical-deliverable"
                aria-label={`${historicalDeliverable.title}：标准化文档`}
                tabIndex={-1}
              >
                <header>
                  <span>时间线记录</span>
                  <Button type="link" onClick={() => setHistoricalDeliverable(undefined)}>返回当前步骤</Button>
                </header>
                <h2>{historicalDeliverable.title}</h2>
                <p>这是该时间点生成的标准化文档，只读保留，便于回看当时确认的结论与已登记缺口。</p>
                <section className="guanyijia-readable-markdown-panel" aria-label="历史标准化文档">
                  <SourceDocumentReadable content={historicalDeliverable.markdown} />
                </section>
              </section>}
              {documentPanel ?? documentFailurePanel}
              {snapshot && !documentOpen && deliveryPhase && !conflictWorkspaceOpen && <section
                className={`guanyijia-next-task guanyijia-deliverable-workspace${mobile
                  ? deliverableLayer ? ' mobile-layer-open' : ' mobile-layer-closed'
                  : ''}`}
                aria-label="标准化结果定版"
                role={mobile && deliverableLayer ? 'dialog' : undefined}
                aria-modal={mobile && deliverableLayer ? true : undefined}
                data-review-anchor={`deliverable:${deliverable?.deliverableId ?? snapshot.run?.runId ?? 'pending'}`}
                data-review-layer-id={deliverableLayer?.stableId}
                data-review-inertable
              >
                {mobile && deliverableLayer && <BackAction
                  id={`${deliverableLayer.stableId}:close`}
                  className="guanyijia-mobile-layer-back"
                  destination="时间线"
                  onBack={reviewSurface.close}
                />}
                {mobile && !deliverableLayer && <Button
                  className="guanyijia-mobile-layer-open"
                  type="primary"
                  data-workflow-primary="true"
                  onClick={(event) => reviewSurface.open({
                    kind: 'DELIVERABLE',
                    stableId: `deliverable:${deliverable?.deliverableId ?? snapshot.run?.runId ?? 'pending'}`,
                    revision: snapshot.run?.revision,
                    focusId: `deliverable:${deliverable?.deliverableId ?? snapshot.run?.runId ?? 'pending'}:close`,
                }, event.currentTarget)}
                >查看标准化结果</Button>}
                <h2>{pendingDeliverableCommand ? '上一步未完成'
                  : !deliverable ? '标准化结果尚未生成'
                  : deliverable.status === 'GENERATED_AWAITING_AUTHOR' ? '标准化结果待定版'
                    : deliverable.status === 'AWAITING_INDEPENDENT_REVIEW' ? '历史结果待定版'
                      : deliverable.status === 'FROZEN' ? '标准化结果已定版'
                      : '标准化文档已交给 AI 建模'}</h2>
                  <p>{pendingDeliverableCommand ? '上一步的内容已经保存；可以继续完成当前流程。'
                  : !deliverable ? '基于已审阅资料和已保存决定生成标准化结果。'
                    : deliverable.status === 'HANDED_OFF'
                    ? '完整标准化文档已交接；资料缺口不会进入模型候选。'
                      : '定版前会确认本次资料审阅和差异决定已完整保存。'}</p>
                {deliverable && <dl className="guanyijia-deliverable-facts">
                  <div><dt>可用于建模</dt><dd>{eligibleConclusionCount} 项已确认结论</dd></div>
                  <div><dt>已排除</dt><dd>{excludedConclusionCount} 项缺口或无法确定内容</dd></div>
                  <div><dt>正式模型</dt><dd>本次未改变</dd></div>
                </dl>}
                {deliverable && <p className="guanyijia-deliverable-summary">
                  完整文档保留所有结论和缺口；AI 建模只读取可用于建模的已确认结论。
                </p>}
                {pendingDeliverableCommand?.actorUserId === currentUser.userId && <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={() => void resumePendingDeliverable()}>继续完成上一步</Button>}
                {!pendingDeliverableCommand && !deliverable && canAuthorDeliverable && <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={() => void executeDeliverable('GENERATE_DELIVERABLE')}>生成标准化结果</Button>}
                {!pendingDeliverableCommand && (deliverable?.status === 'GENERATED_AWAITING_AUTHOR' || deliverable?.status === 'AWAITING_INDEPENDENT_REVIEW') && canAuthorFreezeDeliverable && <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={() => void executeDeliverable('AUTHOR_CONFIRM_AND_FREEZE')}>确认结果并定版</Button>}
                {!pendingDeliverableCommand && deliverable?.status === 'FROZEN' && canHandoffDeliverable && <Button type="primary" data-workflow-primary="true" disabled={reviewMutationBlocked} loading={busy} onClick={() => void executeDeliverable('HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING')}>前往 AI 建模</Button>}
                {!pendingDeliverableCommand && deliverable?.status === 'HANDED_OFF' && <Button type="link" onClick={() => deliverableSnapshot && void openStandardizationHandoff(deliverableSnapshot)}>查看定版文档</Button>}
              </section>}
              {snapshot && !documentOpen && (!deliveryPhase || conflictWorkspaceOpen) && (nextAction?.type !== 'NONE' || conflictWorkspaceOpen) && <section
                className={`guanyijia-next-task ${conflictWorkspaceOpen ? 'blocked' : ''}${
                  mobile && conflictWorkspaceOpen
                    ? conflictLayer ? ' mobile-layer-open' : ' mobile-layer-closed'
                    : ''}`}
                role={mobile && conflictWorkspaceOpen && conflictLayer ? 'dialog' : undefined}
                aria-modal={mobile && conflictWorkspaceOpen && conflictLayer ? true : undefined}
                aria-labelledby={mobile && conflictWorkspaceOpen && conflictLayer ? 'guanyijia-conflict-dialog-title' : undefined}
                data-review-anchor={conflictWorkspaceOpen && conflict
                  ? `conflict:${conflict.conflictId}`
                  : `task:${nextAction?.type ?? 'complete'}`}
                data-review-layer-id={conflictLayer?.stableId}
                data-review-inertable
                tabIndex={-1}
              >
                {mobile && conflictWorkspaceOpen && conflictLayer && <BackAction
                  id={`${conflictLayer.stableId}:close`}
                  className="guanyijia-mobile-layer-back"
                  destination="时间线"
                  onBack={reviewSurface.close}
                />}
                {mobile && !conflictLayer && conflictWorkspaceOpen && conflict && <Button
                  className="guanyijia-mobile-layer-open"
                  type="primary"
                  data-workflow-primary="true"
                  onClick={(event) => reviewSurface.open({
                    kind: 'CONFLICT', stableId: `conflict:${conflict.conflictId}`,
                    revision: snapshot.run?.revision,
                    focusId: `conflict:${conflict.conflictId}:close`,
                  }, event.currentTarget)}
                >审阅来源差异</Button>}
                <h2 id={conflictWorkspaceOpen ? 'guanyijia-conflict-dialog-title' : undefined}>{isReplacingHistoricalDecision ? '重新处理来源差异' : workflowTitle}</h2>
                <p>{nextAction?.type === 'START_RUN' ? '按固定顺序载入数据库、代码、业务文档、演示制度和术语图的固定快照。'
                  : nextAction?.type === 'READ_NEXT_SOURCE' ? '载入固定演示快照，并打开审阅事项。'
                    : nextAction?.type === 'REVIEW_DOCUMENT' ? '文档已就绪；点此继续审阅。'
                      : conflictWorkspaceOpen ? (isReplacingHistoricalDecision
                        ? '上一决定保留在历史中。选择新的处理方式后，请重新生成标准化结果。'
                        : '逐项核对来源差异；保存当前决定后才会定位下一项。')
                        : '当前步骤没有待处理操作。'}</p>
                {nextAction?.type === 'START_RUN' && <Button type="primary" data-workflow-primary="true" loading={busy} onClick={() => void execute('START_RUN')}>{visibleWorkflowAction?.label ?? nextAction.label}</Button>}
                {nextAction?.type === 'READ_NEXT_SOURCE' && <Button type="primary" data-workflow-primary="true" loading={busy} onClick={() => void execute('READ_NEXT_SOURCE')}>{visibleWorkflowAction?.label ?? nextAction.label}</Button>}
                {nextAction?.type === 'REVIEW_DOCUMENT' && <Button
                  type="primary"
                  data-workflow-primary="true"
                  onClick={(event) => openSourceDocument(snapshot, event.currentTarget)}
                >{visibleWorkflowAction?.label ?? nextAction.label}</Button>}
                {conflictWorkspaceOpen && conflict && <section
                  className="guanyijia-conflict-workspace"
                  aria-label="当前来源差异"
                  data-review-window-total={issueWindow?.total ?? 0}
                >
                  <header><Tag color="red">来源差异</Tag><h3>{conflict.title}</h3></header>
                  <div className="guanyijia-conflict-sides">
                    {([conflict.hunk.current, conflict.hunk.incoming] as const).map((side) => <article key={side.role}>
                      <span>{side.role === 'CURRENT' ? '当前工作标准' : '新来源结论'}</span>
                      <h4>{side.block.label}</h4>
                      <p>{blockText(side.block.value)}</p>
                      <small>{side.sourceName}</small>
                    </article>)}
                  </div>
                  <div className="guanyijia-conflict-strategies" role="radiogroup" aria-label="冲突解决策略">
                    {businessConflictDecisionOptions({
                      conflictId: conflict.conflictId,
                      allowedStrategies: conflict.allowedStrategies,
                    }).map((option) => <button
                      type="button"
                      role="radio"
                      data-conflict-decision={option.decision}
                      aria-checked={conflictDecision === option.decision}
                      className={conflictDecision === option.decision ? 'selected' : ''}
                      key={option.decision}
                      onClick={() => void selectConflictDecision(option.decision)}
                      onKeyDown={(event) => {
                        if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return;
                        event.preventDefault();
                        const delta = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1;
                        const decisions = businessConflictDecisionOptions({
                          conflictId: conflict.conflictId,
                          allowedStrategies: conflict.allowedStrategies,
                        });
                        const index = decisions.findIndex((candidate) => candidate.decision === option.decision);
                        const nextIndex = (index + delta + decisions.length) % decisions.length;
                        void selectConflictDecision(decisions[nextIndex]!.decision, {
                          restoreKeyboardFocus: true,
                        });
                      }}
                    >{option.label}</button>)}
                  </div>
                  <section className="guanyijia-conflict-result" aria-label="策略结果">
                    <h4>决定后的结果</h4>
                    {(() => {
                      const summary = resolutionSummaryForBusinessDecision({
                        conflictId: conflict.conflictId,
                        decision: conflictDecision ?? 'REGISTER_GAP',
                      });
                      return <div className="guanyijia-conflict-result-summary">
                        <p><strong>{summary.title}</strong></p>
                        <p>{summary.gap}</p>
                        <p><span>影响：</span>{summary.impact}</p>
                      </div>;
                    })()}
                    <section className="guanyijia-conflict-git-preview" aria-label="Markdown 决定预览" aria-busy={conflictPreviewLoading || undefined}>
                      <h4>Markdown 变化预览</h4>
                      {conflictPreviewLoading && !currentConflictPreview ? <p role="status">正在生成预览</p>
                        : businessConflictMarkdownDiff ? <div className="guanyijia-markdown-diff" aria-live="polite">
                          {businessConflictMarkdownDiff.lines.map((line, index) => <span
                            className={line.kind === 'REMOVED' ? 'removed' : line.kind === 'ADDED' ? 'added' : line.kind === 'RETAINED' ? 'retained' : undefined}
                            key={`${line.kind}:${index}`}
                          ><b aria-hidden="true">{line.kind === 'REMOVED' ? '−' : line.kind === 'ADDED' ? '+' : line.kind === 'RETAINED' ? '✓' : ' '}</b>{line.line}</span>)}
                        </div>
                          : <p>预览暂时无法生成，请重新选择决定。</p>}
                    </section>
                  </section>
                  <Button
                    type="primary"
                    data-workflow-primary="true"
                    loading={busy}
                    disabled={reviewMutationBlocked || !conflictDecision || !currentConflictPreview || conflictPreviewLoading}
                    onClick={() => void applyConflictResolution()}
                  >保存当前决定</Button>
                </section>}
              </section>}
            </div>
            </Spin>
          </div>
          {reviewAssistant}
        </main>
        <StandardizationFactsInspector
          model={inspectorModel}
          sources={inspectorSources}
          selectedSourceId={selectedInspectorSourceId}
          onSelectSource={selectSourceFromInspector}
          onClose={inspectorLayered ? () => closeLayer('inspector') : undefined}
          closeLabel="关闭来源资料"
          layerId={inspectorLayer?.stableId ?? 'inspector:inline'}
          modal={inspectorLayered && inspectorOpen}
          reviewEvidenceView={selectedCuratedEvidenceView}
          workflow={businessWorkflowItems.length ? <StandardizationTimeline
            items={businessWorkflowItems}
            total={businessWorkflowItems.length}
            selectedItemId={selectedTimelineItemId}
            locateRequest={workflowLocateRequest}
            onSelect={(item) => selectTimeline(item as WorkbenchTimelineItem)}
          /> : undefined}
          showMaterialDetails={false}
        />
      </div>
    </Card>
    <div className="guanyijia-review-live-region" role="status" aria-label="操作反馈" aria-live="polite" aria-atomic="true">{interactionAnnouncement || reviewSurface.announcement}</div>
    {sourceCenterOpen && <SourceCenter
      initialView="CONNECTIONS"
      attachedSnapshotIds={projectBatch?.snapshotIds ?? []}
      mode="RUN_CONFIGURATION"
      demoSourceConfiguration={readDemoContentSourceConfiguration()}
      demoSourceStatuses={snapshot?.run?.sources.map((source) => ({
        sourceId: source.sourceId,
        status: source.status,
      })) ?? []}
      demoSourceBoard={sourceBoard}
      onDemoSourceBoardCommand={updateSourceBoard}
      onClose={() => setSourceCenterOpen(false)}
    />}
  </div>;
}
