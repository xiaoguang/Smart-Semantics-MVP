import type { DocumentVersion, FixtureDocument, ModelVersion, SemanticFixture } from './types.ts';
import type { ConnectorDefinition, EvidenceClaim, ModelingBatch, SourceAsset } from './source-bundle.ts';
import type { CandidateModelSnapshot } from './candidate-model.ts';
import type { SourceEvidenceLocator } from './source-bundle.ts';
import type { MaterializedCollaborationData } from '../collaboration/types.ts';

export type ReviewDecision = {
  itemId: string;
  decision: 'APPROVED' | 'REJECTED';
  source: 'AUTO' | 'USER';
  reviewer: string;
  reason: string;
  confidence?: number;
  decidedAt: string;
};

export type PublicationCheck = {
  id: 'RELATION_ENDPOINTS' | 'FIELD_UNIQUENESS' | 'METRIC_DEPENDENCIES' | 'SOURCE_EVIDENCE';
  label: string;
  passed: boolean;
  detail: string;
};

export type StoredResult = {
  documentVersion: DocumentVersion;
  status: 'GENERATED' | 'NEEDS_SUPPLEMENT' | 'PUBLISHED';
  generatedAt: string;
  decisions: Record<string, ReviewDecision>;
  confirmedGroups: string[];
  checks: PublicationCheck[];
  publishedAt: string | null;
};

export type Catalog = {
  id: string;
  modelVersion: ModelVersion;
  documentVersion: DocumentVersion;
  publishedAt: string;
  publishedBy: string;
  reason: string;
  immutable: true;
};

export type WorkspaceState = {
  schemaVersion: 4;
  systemCode: string;
  lockVersion: number;
  uploads: Array<{ documentVersion: DocumentVersion; documentCode?: string; fileName: string; sha256: string; uploadedAt: string }>;
  unknownUploads: Array<{
    id: string; documentCode: string; fileName: string; content: string; size: number; sha256: string; uploadedAt: string; status: 'NO_RESULT';
  }>;
  results: StoredResult[];
  catalogs: Catalog[];
  currentCatalogVersion: ModelVersion | null;
};

export type HydratedResult = StoredResult & {
  fixture: FixtureDocument;
  decidedItemCount: number;
  autoApprovedItemCount: number;
  userDecidedItemCount: number;
  pendingItemCount: number;
  totalItemCount: number;
  allItemsDecided: boolean;
};

export type WorkspaceSnapshot = Omit<WorkspaceState, 'results'> & {
  fixture: SemanticFixture;
  results: HydratedResult[];
  nextExpectedVersion: DocumentVersion | null;
  recoveryRequired: boolean;
  recoveryMessage: string | null;
  sourceWorkspace?: {
    availableSources: SourceAsset[];
    selectedSources: SourceAsset[];
    evidenceLocators: Record<string, SourceEvidenceLocator>;
    connectorCatalog?: ConnectorDefinition[];
    evidenceClaims?: EvidenceClaim[];
    batch: ModelingBatch;
    candidateModel?: CandidateModelSnapshot;
    materializedDraft?: MaterializedCollaborationData;
    history?: ModelingBatch[];
  };
};

type BaseCommand = { systemCode: string; expectedRevision: number };

export type WorkbenchCommand =
  | (BaseCommand & { type: 'UPLOAD_DOCUMENT'; documentCode?: string; fileName: string; content: string; size: number; sha256: string })
  | (BaseCommand & { type: 'GENERATE_MODEL'; documentVersion?: DocumentVersion })
  | (BaseCommand & { type: 'ADD_SOURCES'; sourceIds: string[] })
  | (BaseCommand & { type: 'ATTACH_SOURCE_SNAPSHOTS'; snapshotIds: string[] })
  | (BaseCommand & { type: 'DETACH_SOURCE_SNAPSHOT'; snapshotId: string })
  | (BaseCommand & { type: 'REMOVE_SOURCE'; sourceId: string })
  | (BaseCommand & { type: 'START_MODELING' })
  | (BaseCommand & {
      type: 'RESOLVE_FINDING'; findingId: string; resolutionId: string; reviewer: string; reason: string;
    })
  | (BaseCommand & {
      type: 'CONFIRM_BUSINESS_GROUP'; documentVersion: DocumentVersion; groupId: string; reviewer: string; reason: string;
    })
  | (BaseCommand & {
      type: 'REJECT_ITEM'; documentVersion: DocumentVersion; itemId: string; reviewer: string; reason: string;
    })
  | (BaseCommand & {
      type: 'DECIDE_REVIEW_ITEM'; documentVersion: DocumentVersion; itemId: string;
      decision: 'APPROVED' | 'REJECTED'; reviewer: string; reason: string;
    })
  | (BaseCommand & { type: 'PUBLISH_MODEL'; documentVersion: DocumentVersion; reviewer: string; reason: string });

export type WorkbenchRuntime = {
  read(systemCode: string): Promise<WorkspaceSnapshot>;
  execute(command: WorkbenchCommand): Promise<{ snapshot: WorkspaceSnapshot }>;
  reset(systemCode: string): Promise<WorkspaceSnapshot>;
};
