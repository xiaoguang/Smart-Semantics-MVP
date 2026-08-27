export type WorkspaceRole = 'VIEWER' | 'EDITOR' | 'REVIEWER' | 'PUBLISHER' | 'ADMIN';
export type MembershipStatus = 'ACTIVE' | 'SUSPENDED' | 'REMOVED';

export type DemoUser = {
  userId: string;
  username: string;
  displayName: string;
  initials: string;
  demoPassword: string;
};

export type WorkspaceMembership = {
  workspaceId: string;
  userId: string;
  role: WorkspaceRole;
  status: MembershipStatus;
};

export type CollaborationWorkspace = {
  workspaceId: string;
  productId: string;
  displayName: string;
  modelProjectIds: string[];
};

export type DemoSession = {
  schemaVersion: 1;
  userId: string;
  authenticatedAt: string;
};

import type { ModelBrowserObjectKind, ModelBrowserObjectRef } from '../model-browser/types.ts';

export type FrozenEvidenceBatch = {
  batchId: string;
  revision: number;
  frozenAt: string;
  fingerprint: string;
  coverage: string;
  snapshotIds: string[];
};

export type FrozenSourceSnapshot = {
  snapshotId: string;
  sourceSnapshotIdentity?: string;
  fingerprint?: string;
  manifestRef?: string;
  connectionId: string;
  displayName: string;
  connectorType: string;
  versionRef: string;
  role: string;
  authority: 'PRIMARY' | 'CORROBORATING' | 'DERIVED' | 'AUXILIARY';
  summary: string;
  objectCounts: Record<string, number>;
  upstreamSnapshotIds?: string[];
  evidenceIds: string[];
  status?: 'READY' | 'NEEDS_CONFIRMATION';
};

export type FrozenEvidenceClaim = {
  claimId: string;
  sourceId: string;
  topic?: string;
  assertion: string;
  authority: FrozenSourceSnapshot['authority'];
  evidenceRefs: string[];
  upstreamClaimIds?: string[];
};

export type FrozenObjectEvidence = {
  objectKind: ModelBrowserObjectKind;
  objectCode: string;
  ownerCode?: string;
  status: 'VERIFIED' | 'NEEDS_CONFIRMATION';
  evidenceRefs: string[];
  supportClaimIds: string[];
};

export type FrozenReconciliationFinding = {
  findingId: string;
  title: string;
  status: 'CONSISTENT' | 'CONFLICT' | 'SINGLE_SOURCE' | 'DERIVED';
  claimIds: string[];
  agreement?: string;
  difference?: string;
  decision?: string;
  affectedObjectCodes: string[];
  originStatus?: 'CONFLICT';
  resolution?: {
    decidedByUserId: string;
    decidedBy: string;
    decidedAt: string;
    reason: string;
  };
};

export type CatalogEvidenceSidecarV2 = {
  schemaVersion: 2;
  systemCode: string;
  sourceFingerprint: string;
  dimensions: Array<Record<string, unknown>>;
  ruleCandidates: Array<Record<string, unknown>>;
  hierarchyDefinitions: Array<Record<string, unknown>>;
  generationNotes: Array<Record<string, unknown>>;
  compatibilityNotes: string[];
  batch: FrozenEvidenceBatch;
  sources: FrozenSourceSnapshot[];
  evidence: Array<Record<string, unknown>>;
  locators: Record<string, Record<string, unknown>>;
  claims: FrozenEvidenceClaim[];
  findings: FrozenReconciliationFinding[];
  objectEvidence: FrozenObjectEvidence[];
};

export type MaterializedCollaborationData = {
  workspaceData: Record<string, unknown>;
  metricData: Record<string, unknown>;
  workbenchState: unknown;
  semanticSidecar?: {
    schemaVersion: 1;
    systemCode: string;
    documentVersion?: string;
    sourceFingerprint: string;
    dimensions: Array<Record<string, unknown>>;
    ruleCandidates: Array<Record<string, unknown>>;
    hierarchyDefinitions: Array<Record<string, unknown>>;
    evidence: Array<Record<string, unknown>>;
    generationNotes: Array<Record<string, unknown>>;
    compatibilityNotes: string[];
  } | CatalogEvidenceSidecarV2;
};

export type ModelChange = {
  changeId: string;
  category: string;
  summary: string;
  operation?: 'ADDED' | 'MODIFIED' | 'REMOVED';
  kind?: ModelBrowserObjectKind;
  objectRef?: ModelBrowserObjectRef;
  name?: string;
  changedFields?: Array<{ label: string; before?: string; after?: string }>;
  evidenceRefs?: string[];
  affectedRefs?: ModelBrowserObjectRef[];
  attention?: 'NORMAL' | 'VERIFY' | 'CONFLICT';
};

export type PersonalDraft = {
  draftId: string;
  workspaceId: string;
  modelSpaceId: string;
  ownerUserId: string;
  title: string;
  baseCatalogVersion: string | 'INITIAL';
  baseFingerprint: string;
  revision: number;
  status: 'ACTIVE' | 'SUBMITTED' | 'ARCHIVED';
  materializedData: MaterializedCollaborationData;
  changes: ModelChange[];
  createdAt: string;
  updatedAt: string;
};

export type DraftIntegrityIssue = {
  draftId?: string;
  userId: string;
  modelSpaceId?: string;
  message: string;
};

export type ChangeApproval = {
  reviewerUserId: string;
  reviewerName: string;
  decision: 'APPROVED' | 'CHANGES_REQUESTED' | 'REJECTED';
  reason: string;
  decidedAt: string;
};

export type ChangeRequestStatus = 'SUBMITTED' | 'IN_REVIEW' | 'CHANGES_REQUESTED'
  | 'REJECTED' | 'APPROVED' | 'CONFLICTED' | 'PUBLISHED';

export type ChangeRequest = {
  requestId: string;
  draftId: string;
  draftRevision: number;
  workspaceId: string;
  modelSpaceId: string;
  title: string;
  authorUserId: string;
  baseCatalogVersion: string | 'INITIAL';
  baseFingerprint: string;
  proposedData: MaterializedCollaborationData;
  changes: ModelChange[];
  evidenceRefs: string[];
  status: ChangeRequestStatus;
  approvals: ChangeApproval[];
  expectedRevision: number;
  submittedAt: string;
  publishedCatalogVersion?: string;
  evidencePackageId?: string;
  migrationNotice?: string;
};

export type CollaborationCatalog = {
  catalogId: string;
  catalogVersion: string;
  fingerprint: string;
  modelSpaceId: string;
  data: MaterializedCollaborationData;
  authorUserId: string;
  reviewerUserIds: string[];
  publisherUserId: string;
  publishedAt: string;
  requestId: string;
  publicationReason?: string;
};

export type CollaborationSharedState = {
  schemaVersion: 4;
  revision: number;
  catalogs: Record<string, CollaborationCatalog[]>;
  packageCatalogHistory: Record<string, CollaborationCatalog[]>;
  requests: ChangeRequest[];
  migrationWarnings: string[];
};

export type CollaborationSeed = {
  shared: CollaborationSharedState;
  drafts: Record<string, PersonalDraft[]>;
};

export type CollaborationTask = {
  taskId: string;
  kind: 'EDIT' | 'REVIEW' | 'PUBLISH';
  title: string;
  modelSpaceId: string;
  requestId?: string;
  workspaceName?: string;
  modelProjectName?: string;
  authorName?: string;
  changeCount?: number;
  updatedAt?: string;
  reason?: string;
};
