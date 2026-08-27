import type { ContentReference } from '../source-documents/types.ts';
import type { ModelingDocumentSection } from '../modeling-document-bridge/types.ts';
import type { ConflictResolutionStrategy } from '../guanyijia-standardization-story/types.ts';

export type StandardizationRunStatus =
  | 'READY'
  | 'READING_SOURCE'
  | 'REVIEWING_DOCUMENT'
  | 'CONFLICT_BLOCKED'
  | 'READY_FOR_OUTPUT'
  | 'FROZEN'
  | 'HANDED_OFF';

export type StandardizationSourceStepStatus =
  | 'PENDING'
  | 'READING'
  | 'DOCUMENT_READY'
  | 'REVIEWED'
  | 'CONFLICT_BLOCKED'
  | 'ALIGNED';

export type StandardizationActor = {
  userId: string;
};

export type SourceReadSummary = {
  summary: string;
  objectCount: number;
  evidenceCount: number;
};

export type StandardizationSourceStep = {
  sourceId: string;
  sourceName: string;
  /**
   * The immutable snapshot admitted into this particular run.  Older runs
   * predating this field remain readable, but cannot be used to project a
   * cross-source comparison because their source identity is incomplete.
   */
  snapshotId?: string;
  order: number;
  status: StandardizationSourceStepStatus;
  readSummary?: SourceReadSummary;
  documentId?: string;
  documentRevision?: number;
  introducedConflictIds: string[];
  resolvedConflictIds: string[];
};

export type StandardizationTimelineEvent = {
  eventId: string;
  runId: string;
  sequence: number;
  type:
    | 'SOURCE_READ_STARTED'
    | 'SOURCE_READ_COMPLETED'
    | 'DOCUMENT_GENERATED'
    | 'DOCUMENT_REVISED'
    | 'DOCUMENT_REVIEWED'
    | 'CONFLICT_FOUND'
    | 'CONFLICT_RESOLVED'
    | 'CONFLICT_DECISION_REPLACED'
    | 'CONFLICT_CORROBORATED'
    | 'ASSISTANT_TURN_RECORDED'
    | 'ASSISTANT_PATCH_CONFIRMED'
    | 'ASSISTANT_PATCH_CANCELLED'
    | 'DELIVERABLE_GENERATED'
    | 'DELIVERABLE_SUPERSEDED'
    | 'REVIEW_SUBMITTED'
    | 'DELIVERABLE_FROZEN'
    | 'MODELING_HANDOFF_COMPLETED';
  sourceId?: string;
  /** Present only on a non-source replacement event so the timeline can reopen it. */
  conflictId?: string;
  sourceDocumentId?: string;
  sourceDocumentRevision?: number;
  assistantTurnId?: string;
  assistantProposalId?: string;
  assistantDocumentId?: string;
  assistantDocumentRevision?: number;
  assistantKnowledgeSourceRevisions?: Array<{
    sourceId: string;
    documentId: string;
    documentRevision: number;
  }>;
  deliveryContentIndex?: {
    coreSha256: ContentReference;
    entries: Array<{
      key: 'sourceManifestRef' | 'mergedDocumentRef' | 'decisionManifestRef'
        | 'governanceAppendixRef' | 'modelingArtifactRef' | 'zeroDeltaReportRef';
      contentRef: ContentReference;
    }>;
  };
  actor: StandardizationActor;
  payloadRef: ContentReference;
  createdAt: string;
};

export type StandardizationRun = {
  schemaVersion: 1;
  runId: string;
  projectId: string;
  scenarioKey: string;
  batchId: string;
  revision: number;
  status: StandardizationRunStatus;
  sources: StandardizationSourceStep[];
  timeline: StandardizationTimelineEvent[];
  deliverableId?: string;
  /**
   * Generated deliverables are immutable historical records.  If a decision
   * changes before freeze, the former result is retained here and a new
   * deliverable must be generated instead of overwriting that record.
   */
  supersededDeliverableIds?: string[];
  reviewId?: string;
  modelingHandoffId?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

type BaseCommand = {
  commandId: string;
  expectedRevision: number;
  actor: StandardizationActor;
};

type RunCommand = BaseCommand & { runId: string };

export type StandardizationRunCommand =
  | (BaseCommand & {
      type: 'CREATE_RUN';
      projectId: string;
      scenarioKey: string;
      batchId: string;
      sources: Array<{ sourceId: string; sourceName: string; snapshotId?: string }>;
    })
  | (RunCommand & { type: 'START_NEXT_SOURCE' })
  | (RunCommand & {
      type: 'COMPLETE_SOURCE_DOCUMENT';
      sourceId: string;
      readSummary: SourceReadSummary;
      documentId: string;
      documentRevision: number;
      introducedConflictIds: string[];
      corroboratedConflictIds?: string[];
      payload: string;
    })
  | (RunCommand & {
      type: 'REVISE_SOURCE_DOCUMENT';
      sourceId: string;
      documentId: string;
      documentRevision: number;
      introducedConflictIds: string[];
      documentContent?: {
        sectionsRef: ContentReference;
        assertionsRef: ContentReference;
        blocksRef: ContentReference;
        markdownRef: ContentReference;
        markdownSha256: string;
      };
      diffSummary: {
        changedBlockIds: string[];
        changedSections: ModelingDocumentSection[];
        affectedObjectIds: string[];
      };
    })
  | (RunCommand & { type: 'MARK_DOCUMENT_REVIEWED'; sourceId: string })
  | (RunCommand & {
      type: 'RESOLVE_SOURCE_CONFLICT';
      sourceId: string;
      conflictId: string;
      strategy: ConflictResolutionStrategy;
      reason: string;
      expectedHunkSha256: string;
      payload: string;
    })
  | (RunCommand & {
      /** Replaces a saved decision and invalidates an unfrozen generated result. */
      type: 'REPLACE_SOURCE_CONFLICT_DECISION';
      sourceId: string;
      conflictId: string;
      strategy: ConflictResolutionStrategy;
      reason: string;
      expectedHunkSha256: string;
      payload: string;
    })
  | (RunCommand & {
      type: 'RECORD_ASSISTANT_TURN';
      payload: string;
      proposalPayload?: string;
    })
  | (RunCommand & {
      type: 'CONFIRM_ASSISTANT_PATCH';
      sourceId: string;
      proposalId: string;
      expectedPreviewSha256: string;
      beforeDocumentId: string;
      beforeDocumentRevision: number;
      documentId: string;
      documentRevision: number;
      introducedConflictIds: string[];
      documentContent?: {
        sectionsRef: ContentReference;
        assertionsRef: ContentReference;
        blocksRef: ContentReference;
        markdownRef: ContentReference;
        markdownSha256: string;
      };
      diffSummary: {
        changedBlockIds: string[];
        changedSections: ModelingDocumentSection[];
        affectedObjectIds: string[];
      };
      payload: string;
    })
  | (RunCommand & {
      type: 'CANCEL_ASSISTANT_PATCH';
      proposalId: string;
      documentId: string;
      documentRevision: number;
      payload: string;
    })
  | (RunCommand & {
      type: 'MARK_DELIVERABLE_GENERATED'; deliverableId: string; payload: string;
      deliveryCapability?: object;
    })
  | (RunCommand & {
      type: 'MARK_REVIEW_SUBMITTED'; reviewId: string; payload: string;
      deliveryCapability?: object;
    })
  | (RunCommand & {
      type: 'MARK_DELIVERABLE_FROZEN'; deliverableId: string; payload: string;
      deliveryCapability?: object;
    })
  | (RunCommand & {
      type: 'MARK_MODELING_HANDOFF_COMPLETED'; handoffId: string; payload: string;
      deliveryCapability?: object;
    });

export type StandardizationRunRuntime = {
  read(runId: string): Promise<StandardizationRun | null>;
  /**
   * Reads only the validated run metadata. Consumers that render a shell must
   * not force event payload validation/body hydration before an explicit read.
   */
  readMetadata?(runId: string): Promise<StandardizationRun | null>;
  readEventPayload(runId: string, eventId: string): Promise<string>;
  validateAssistantPatchConfirmations(runId: string, proposalIds: readonly string[]): Promise<void>;
  execute(command: StandardizationRunCommand): Promise<StandardizationRun>;
};

export type StandardizationMetadataSnapshot = {
  version: string;
  raw: string | null;
};

export type StandardizationMetadataStore = {
  read(): Promise<StandardizationMetadataSnapshot>;
  compareAndSet(expectedVersion: string, nextRaw: string): Promise<boolean>;
};
