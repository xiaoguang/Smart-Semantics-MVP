import type { MaterializedCollaborationData, WorkspaceRole } from '../collaboration/types.ts';
import type { ConflictResolutionArtifact } from '../guanyijia-standardization-story/types.ts';
import type {
  ModelingDocumentArtifact,
  ModelingDocumentSemanticPayload,
  ModelingDocumentSection,
  StructuredModelingAssertion,
} from '../modeling-document-bridge/types.ts';
import type {
  ContentAddressedStore,
  ContentReference,
  SourceDocumentBlock,
  SourceModelingDocument,
} from '../source-documents/types.ts';
import type { StandardizationRun } from '../standardization-run/types.ts';
import type { ProjectionContext } from '../modeling-document-projector/index.ts';
import type { StandardizationModelingEligibilityProjection } from './modeling-eligibility.ts';
import type { ReviewSupplementMatter } from '../data-standardization/standardized-document-reading.ts';

export type DeliverableStatus =
  | 'GENERATED_AWAITING_AUTHOR'
  | 'AWAITING_INDEPENDENT_REVIEW'
  | 'FROZEN'
  | 'HANDED_OFF';

export type DeliverableLinkState =
  | 'LINKED'
  | 'GENERATED_EVENT_PENDING'
  | 'REVIEW_EVENT_PENDING'
  | 'FROZEN_EVENT_PENDING'
  | 'HANDOFF_EVENT_PENDING';

export type DeliverableCommand =
  | {
      type: 'GENERATE_DELIVERABLE'; commandId: string; runId: string;
      actorUserId: string; expectedRunRevision: number;
      mode?: StandardizationDeliverableMode;
      /**
       * Bound by the workbench to the exact read-only merged preview. Legacy
       * commands without an explicit MERGED_DOCUMENT mode remain readable for
       * idempotent recovery.
       */
      expectedPreviewSha256?: ContentReference;
    }
  | {
      type: 'AUTHOR_CONFIRM'; commandId: string; runId: string; deliverableId: string;
      actorUserId: string; expectedDeliverableRevision: number;
    }
  | {
      type: 'AUTHOR_CONFIRM_AND_FREEZE'; commandId: string; runId: string; deliverableId: string;
      actorUserId: string; expectedDeliverableRevision: number;
    }
  | {
      type: 'REVIEW_AND_FREEZE'; commandId: string; runId: string; deliverableId: string;
      actorUserId: string; expectedDeliverableRevision: number;
    }
  | {
      type: 'HANDOFF_ZERO_DELTA_TO_M4'; commandId: string; runId: string; deliverableId: string;
      actorUserId: string; expectedDeliverableRevision: number;
    }
  | {
      type: 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING'; commandId: string; runId: string; deliverableId: string;
      actorUserId: string; expectedDeliverableRevision: number;
    };

export type SourceCollectionManifest = {
  schemaVersion: 1;
  runId: string;
  scenarioKey: string;
  sources: Array<{
    order: number;
    sourceId: string;
    sourceName: string;
    documentId: string;
    documentRevision: number;
    sourceSnapshotId: string;
    sourceType: string;
    sectionsRef: ContentReference;
    assertionsRef: ContentReference;
    blocksRef: ContentReference;
    markdownRef: ContentReference;
    markdownSha256: string;
    evidenceRole: 'FORMAL_ROOT' | 'NON_FORMAL_ROOT' | 'DERIVED_CORROBORATION';
  }>;
};

export type MergedStandardizationDocument = {
  schemaVersion: 1;
  runId: string;
  sourceSnapshotIds: string[];
  sections: Record<ModelingDocumentSection, string>;
  markdown: string;
};

export type ResolutionDecisionManifest = {
  schemaVersion: 1;
  runId: string;
  decisions: Array<{
    conflictId: string;
    resolutionId: string;
    sourceId: string;
    strategy: ConflictResolutionArtifact['strategy'];
    reason: string;
    actorUserId: string;
    decidedAt: string;
    previewSha256: string;
    hunkSha256: string;
    affectedObjectIds: string[];
  }>;
};

export type GovernanceEvidenceAppendix = {
  schemaVersion: 1;
  runId: string;
  formalRootSourceIds: string[];
  nonFormalSourceIds: string[];
  derivedSourceIds: string[];
  decisionRefs: Array<{ conflictId: string; resolutionId: string; previewSha256: string }>;
  notes: string[];
};

export type SemanticPathDifference = { path: string; before?: unknown; after?: unknown };

export type ZeroDeltaReport = {
  schemaVersion: 1;
  semanticDifferences: SemanticPathDifference[];
  catalogChanges: unknown[];
  counts: Record<string, number>;
};

export type ZeroDeltaModelingHandoffReceipt = {
  kind: 'ZERO_DELTA_MODELING_HANDOFF';
  schemaVersion: 1;
  receiptId: string;
  runId: string;
  deliverableId: string;
  protectedArtifactId: string;
  protectedCatalogId: string;
  artifactId: string;
  artifactMarkdownSha256: string;
  semanticPayloadSha256: string;
  governanceAppendixRef: ContentReference;
  zeroDeltaReportRef: ContentReference;
  handedOffBy: string;
  handedOffAt: string;
};

/** New runs transfer a complete governance document, not a zero-delta assertion. */
export type StandardizationDocumentHandoffReceipt = {
  kind: 'STANDARDIZATION_DOCUMENT_HANDOFF';
  schemaVersion: 1;
  receiptId: string;
  runId: string;
  deliverableId: string;
  artifactId: string;
  mergedDocumentRef: ContentReference;
  modelingEligibilityRef: ContentReference;
  eligibleConclusionCount: number;
  excludedConclusionCount: number;
  handedOffBy: string;
  handedOffAt: string;
};

export type StandardizationModelingHandoffReceipt =
  | ZeroDeltaModelingHandoffReceipt
  | StandardizationDocumentHandoffReceipt;

export type StandardizationDeliverable = {
  schemaVersion: 1;
  deliverableId: string;
  runId: string;
  scenarioKey: string;
  projectId: string;
  revision: number;
  mode: StandardizationDeliverableMode;
  status: DeliverableStatus;
  linkState: DeliverableLinkState;
  authorUserId: string;
  sourceManifestRef: ContentReference;
  mergedDocumentRef: ContentReference;
  decisionManifestRef: ContentReference;
  governanceAppendixRef: ContentReference;
  modelingArtifactRef: ContentReference;
  zeroDeltaReportRef: ContentReference;
  /** Present on all new gap-tolerant deliverables; absent only on legacy records. */
  modelingEligibilityRef?: ContentReference;
  coreSha256: string;
  authorConfirmation?: {
    reviewId: string;
    actorUserId: string;
    confirmedAt: string;
    coreSha256: string;
  };
  /**
   * New five-source runs are frozen by the author after all pre-freeze
   * validations. It intentionally records a finalization, not a second
   * review/approval, so old independent-review records remain readable while
   * new runs do not invent a reviewer.
   */
  authorFreezeConfirmation?: {
    confirmationId: string;
    actorUserId: string;
    confirmedAt: string;
    coreSha256: string;
    inputDeliverableRevision: number;
  };
  reviewerApproval?: {
    reviewId: string;
    actorUserId: string;
    reviewedAt: string;
    coreSha256: string;
  };
  handoffReceiptRef?: ContentReference;
  createdAt: string;
  updatedAt: string;
};

export type StandardizationDeliverableMode = 'SOURCE_DOCUMENT_SET' | 'MERGED_DOCUMENT';

export type DeliverableSnapshot = {
  deliverable: StandardizationDeliverable | null;
  run: StandardizationRun | null;
  receipt?: StandardizationModelingHandoffReceipt;
  modelingEligibility?: StandardizationModelingEligibilityProjection;
  pendingCommand?: DeliverableCommand;
};

/**
 * A read-only, deterministic pre-generation identity. The three final review
 * tabs consume this one value rather than assembling independent summaries.
 */
export type StandardizationDeliverablePreview = {
  schemaVersion: 1;
  runId: string;
  runRevision: number;
  previewSha256: ContentReference;
  mergedDocumentRef: ContentReference;
  sourceManifest: SourceCollectionManifest;
  decisionManifest: ResolutionDecisionManifest;
  mergedDocument: MergedStandardizationDocument;
  reviewProjection: {
    chapters: Array<{
      section: ModelingDocumentSection;
      heading: string;
      sources: Array<{
        order: number;
        sourceId: string;
        sourceName: string;
        markdown: string;
        assertions: StructuredModelingAssertion[];
        blocks: SourceDocumentBlock[];
      }>;
    }>;
    supplements: ReviewSupplementMatter[];
    decisions: Array<{
      conflictId: string;
      title: string;
      sourceId: string;
      strategy: ConflictResolutionArtifact['strategy'];
      reason: string;
    }>;
  };
};

export type ContentPage = {
  contentRef: ContentReference;
  items: string[];
  nextCursor: string | null;
  total: number;
};

export type DeliverableMetadataSnapshot = { version: string; raw: string | null };
export type DeliverableMetadataStore = {
  read(): Promise<DeliverableMetadataSnapshot>;
  compareAndSet(expectedVersion: string, nextRaw: string): Promise<boolean>;
};

export type StandardizationRunReader = {
  read(runId: string): Promise<StandardizationRun | null>;
  readEventPayload(runId: string, eventId: string): Promise<string>;
  appendEvent(input: {
    type: 'DELIVERABLE_GENERATED' | 'REVIEW_SUBMITTED' | 'DELIVERABLE_FROZEN' | 'MODELING_HANDOFF_COMPLETED';
    commandId: string;
    runId: string;
    expectedRunRevision: number;
    actorUserId: string;
    deliverableId: string;
    eventIdentity: string;
    payload: string;
  }): Promise<StandardizationRun>;
};

export type SourceDocumentReader = {
  read(documentId: string): Promise<SourceModelingDocument | null>;
  readMarkdown?(documentId: string): Promise<string>;
  readSections?(documentId: string): Promise<Record<ModelingDocumentSection, string>>;
  readAssertions?(documentId: string): Promise<StructuredModelingAssertion[]>;
  readBlocks?(documentId: string): Promise<SourceDocumentBlock[]>;
};

export type ConflictResolutionReader = {
  list(run: StandardizationRun): Promise<ConflictResolutionArtifact[]>;
};

export type ProtectedBaseline = {
  scenarioKey: string;
  expectedSourceIds: string[];
  expectedSourceTypes: Record<string, string>;
  expectedArtifactId: string;
  artifact: ModelingDocumentArtifact;
  catalogId: string;
  catalogFingerprint: string;
  catalogData: MaterializedCollaborationData;
  semanticPayloadSha256: string;
  markdownSha256: string;
  expectedCounts: Record<string, number>;
  projectionContext: ProjectionContext;
};

export type ProtectedBaselineProvider = {
  resolve(scenarioKey: string): Promise<ProtectedBaseline | null>;
};

export type ModelingProjector = {
  project(input: {
    run: StandardizationRun;
    baseline: ProtectedBaseline;
    resolutions: ConflictResolutionArtifact[];
    mergedDocument: MergedStandardizationDocument;
  }): Promise<{
    artifact: ModelingDocumentArtifact;
    semanticPayload: ModelingDocumentSemanticPayload;
    zeroDeltaReport: ZeroDeltaReport;
  }>;
};

export type MembershipReader = {
  read(actorUserId: string, projectId: string): Promise<{
    active: boolean;
    role: WorkspaceRole;
  }>;
};

export type StandardizationDeliverableRuntime = {
  read(input: { runId: string; actorUserId: string }): Promise<DeliverableSnapshot>;
  preview(input: {
    runId: string;
    actorUserId: string;
    expectedRunRevision: number;
  }): Promise<StandardizationDeliverablePreview>;
  readContent(input: {
    contentRef: string; actorUserId: string; cursor?: string; limit?: number;
  }): Promise<ContentPage>;
  execute(command: DeliverableCommand): Promise<DeliverableSnapshot>;
};

export type StandardizationDeliverableRuntimeInput = {
  metadataStore: DeliverableMetadataStore;
  contentStore: ContentAddressedStore;
  runReader: StandardizationRunReader;
  sourceDocuments: SourceDocumentReader;
  conflictResolutions: ConflictResolutionReader;
  baselineProvider: ProtectedBaselineProvider;
  modelingProjector: ModelingProjector;
  membershipReader: MembershipReader;
  now?: () => string;
};
