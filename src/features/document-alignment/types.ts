import type { ModelingDocumentSection } from '../modeling-document-bridge/types.ts';
import type { ContentReference } from '../source-documents/types.ts';

export type AlignmentObjectKind =
  | 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION'
  | 'METRIC' | 'RULE' | 'ALIAS' | 'TIME_RULE';

export type AlignmentClaim = {
  claimId: string;
  sourceDocumentId: string;
  topicRef: string;
  objectKind: AlignmentObjectKind;
  statement: string;
  normalizedValue: string;
  authority: 'PRIMARY' | 'CORROBORATING' | 'DERIVED' | 'AUXILIARY';
  evidenceRefs: string[];
  upstreamSourceDocumentIds?: string[];
};

export type AlignmentDecision = {
  decisionId: string;
  issueId: string;
  selectedClaimId: string;
  reason: string;
  actorUserId: string;
  decidedAt: string;
};

export type AlignmentIssue = {
  issueId: string;
  topicRef: string;
  objectKind: AlignmentObjectKind;
  kind: 'CONFLICT' | 'GAP' | 'WEAK_EVIDENCE' | 'DERIVED_LINEAGE';
  severity: 'BLOCKER' | 'WARNING' | 'INFO';
  status: 'OPEN' | 'DECIDED';
  sourceClaimIds: string[];
  decision?: AlignmentDecision;
};

export type AlignmentAgreement = {
  topicRef: string;
  objectKind: AlignmentObjectKind;
  normalizedValue: string;
  sourceClaimIds: string[];
};

export type DocumentAlignmentSession = {
  alignmentId: string;
  projectId: string;
  revision: number;
  sourceDocumentIds: string[];
  claims: AlignmentClaim[];
  agreements: AlignmentAgreement[];
  issues: AlignmentIssue[];
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type DeliverableHashes = {
  sourceDocumentsSha256: string;
  alignmentDecisionsSha256: string;
  semanticPayloadSha256: string;
  markdownSha256: string;
};

export type StandardizationDeliverable = {
  deliverableId: string;
  projectId: string;
  alignmentId: string;
  revision: number;
  mode: 'SOURCE_DOCUMENT_SET' | 'MERGED_DOCUMENT';
  sourceDocumentRefs: string[];
  alignmentDecisionRefs: string[];
  semanticPayloadRef: ContentReference;
  markdownRefs: ContentReference[];
  provenanceManifestRef: ContentReference;
  modelingArtifactRef?: ContentReference;
  hashes: DeliverableHashes;
  status: 'DRAFT' | 'AWAITING_AUTHOR_CONFIRMATION' | 'AWAITING_REVIEW' | 'FREEZING' | 'FROZEN' | 'REJECTED';
  authorUserId: string;
  reviewerUserId?: string;
  authorConfirmation?: { actorUserId: string; reason: string; at: string };
  reviewerApproval?: { actorUserId: string; at: string };
  createdAt: string;
  updatedAt: string;
};

export type DeliverableFreezeProgress = {
  jobId: string;
  deliverableId: string;
  attempt: number;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  totalItems: number;
  completedItems: number;
  totalBatches: number;
  completedBatches: number;
  currentLabel?: string;
  error?: string;
  startedAt: string;
  updatedAt: string;
};

export const alignmentSectionByKind: Record<AlignmentObjectKind, ModelingDocumentSection> = {
  ENTITY: 'OBJECT', EVENT: 'ACTIVITY', FIELD: 'FIELD', RELATION: 'RELATION', DIMENSION: 'FIELD',
  METRIC: 'METRIC', RULE: 'METRIC', ALIAS: 'FIELD', TIME_RULE: 'FIELD',
};
