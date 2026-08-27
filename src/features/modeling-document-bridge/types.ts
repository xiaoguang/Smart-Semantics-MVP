export type ModelingDocumentSection =
  | 'OVERVIEW' | 'GOAL' | 'OBJECT' | 'ACTIVITY' | 'FIELD'
  | 'RELATION' | 'METRIC' | 'QUESTION' | 'UNRESOLVED';

export type ValidationIssue = { code: string; message: string; refs?: string[] };

export type ReviewIssueSeverity = 'BLOCKER' | 'ERROR' | 'GAP' | 'WARNING';
export type ReviewIssueStatus = 'OPEN' | 'DECIDED';

export type ReviewResolutionOption = {
  resolutionId: string;
  label: string;
  conclusion: string;
};

export type ReviewIssue = {
  issueId: string;
  topic: string;
  title: string;
  severity: ReviewIssueSeverity;
  status: ReviewIssueStatus;
  difference: string;
  claimIds: string[];
  evidenceRefs: string[];
  affectedObjectCodes: string[];
  recommendedResolutionId: string;
  options: ReviewResolutionOption[];
};

export type Decision = {
  decisionId: string;
  issueId: string;
  resolutionId: string;
  conclusion: string;
  reason: string;
  actorUserId: string;
  decidedAt: string;
  evidenceRefs: string[];
};

export type BatchOverview = {
  artifactId: string;
  revision: number;
  sourceCount: number;
  rootSourceCount: number;
  evidenceCount: number;
  claimCount: number;
  issueCount: number;
  openIssueCount: number;
  decisionCount: number;
  lineageGapCount: number;
};

export type ReviewEvidenceIndexEntry = {
  evidenceId: string;
  sourceId: string;
  rootSourceIds: string[];
  issueIds: string[];
  checksum: string;
};

export type ReviewEvidence = {
  evidenceId: string;
  sourceId: string;
  rootSourceIds: string[];
  statement: string;
  locator: Record<string, unknown>;
  checksum: string;
};

export type ReviewSnapshot = {
  overview: Omit<BatchOverview, 'artifactId' | 'revision'>;
  issues: ReviewIssue[];
  decisions: Decision[];
  evidenceIndex: ReviewEvidenceIndexEntry[];
};

export type CursorPage<T> = { items: T[]; nextCursor: string | null; total: number };

export type ReviewConclusion = {
  issue: ReviewIssue;
  decision: Decision | null;
  evidenceRoots: Array<{ rootSourceId: string; evidenceCount: number }>;
};

export type ReviewQueryService = {
  getOverview(): Promise<BatchOverview>;
  listIssues(query?: { status?: ReviewIssueStatus | 'ALL'; cursor?: string; limit?: number }): Promise<CursorPage<ReviewIssue>>;
  getConclusion(issueId: string): Promise<ReviewConclusion>;
  listEvidence(query: { issueId: string; rootSourceId: string; cursor?: string; limit?: number }): Promise<CursorPage<ReviewEvidence>>;
};

export type ReviewIssueSummary = {
  issueId: string;
  topic: string;
  title: string;
  severity: ReviewIssueSeverity;
  status: ReviewIssueStatus;
  difference: string;
  claimCount: number;
  evidenceCount: number;
  affectedObjectCount: number;
  optionCount: number;
};

export type ReviewEvidenceSummary = {
  evidenceId: string;
  sourceId: string;
  rootSourceIds: string[];
  checksum: string;
};

export type ReviewHeader = {
  overview: BatchOverview;
  gate: { blockingOpenCount: number; canConfirm: boolean };
  facets: {
    statuses: Record<ReviewIssueStatus, number>;
    severities: Record<ReviewIssueSeverity, number>;
    topics: Record<string, number>;
  };
};

export type ScalableReviewQueryService = ReviewQueryService & {
  getHeader(): Promise<ReviewHeader>;
  listIssueSummaries(query?: {
    q?: string;
    statuses?: ReviewIssueStatus[];
    severities?: ReviewIssueSeverity[];
    topics?: string[];
    first?: number;
    after?: string;
  }): Promise<CursorPage<ReviewIssueSummary>>;
  getIssue(issueId: string): Promise<ReviewIssue>;
  listEvidenceSummaries(query: {
    issueId: string;
    rootSourceId: string;
    q?: string;
    first?: number;
    after?: string;
  }): Promise<CursorPage<ReviewEvidenceSummary>>;
  getEvidence(evidenceId: string): Promise<ReviewEvidence>;
};

export type StructuredModelingAssertion = {
  assertionId: string;
  section: ModelingDocumentSection;
  statement: string;
  provenance: 'OBSERVED' | 'INFERRED' | 'USER_CONFIRMED';
  evidenceRefs: string[];
};

export type ModelingDocumentEvidenceStatus = 'VERIFIED' | 'NEEDS_CONFIRMATION';

export type ModelingDocumentSemanticObject = {
  code: string;
  name: string;
  description: string;
  physicalTable?: string;
  physicalTables?: string[];
  grain?: string;
  businessFilter?: string;
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentSemanticField = {
  code: string;
  ownerCode: string;
  name: string;
  description: string;
  dataType: string;
  semanticType: string;
  role: 'PRIMARY_KEY' | 'JOIN_KEY' | 'DIMENSION' | 'MEASURE' | 'TIME' | 'TEXT';
  physicalTable?: string;
  physicalField?: string;
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentSemanticRelation = {
  code: string;
  name: string;
  description: string;
  sourceCode: string;
  sourceFieldCode?: string;
  targetCode: string;
  targetFieldCode?: string;
  cardinality: 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_ONE' | 'MANY_TO_MANY';
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentSemanticDimension = {
  code: string;
  name: string;
  description: string;
  targetCode: string;
  targetFieldCode?: string;
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentSemanticMetric = {
  code: string;
  name: string;
  description: string;
  eventCode: string;
  measureFieldCode: string;
  formula: string;
  filter: string;
  timeFieldCode: string;
  unit?: string;
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentSemanticHierarchy = {
  code: string;
  name: string;
  description: string;
  ownerCode: string;
  attributeCode: string;
  levels: Array<{ code: string; name: string; depth: number; parentCode?: string }>;
  members: Array<{ code: string; name: string; levelCode: string; parentCode?: string }>;
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentRuleCandidate = {
  code: string;
  name: string;
  description: string;
  targetType: 'ENTITY' | 'EVENT' | 'FIELD' | 'METRIC';
  targetCode: string;
  ownerCode?: string;
  structureStatus: 'NEEDS_STRUCTURE' | 'UNRESOLVED_TARGET';
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentSemanticAlias = {
  code: string;
  text: string;
  targetType: 'ENTITY' | 'EVENT' | 'FIELD' | 'DIMENSION' | 'METRIC' | 'RULE';
  targetCode: string;
  ownerCode?: string;
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentSemanticTimeRule = {
  code: string;
  text: string;
  targetCode: string;
  targetFieldCode: string;
  granularity: 'YEAR' | 'QUARTER' | 'MONTH' | 'WEEK' | 'DAY';
  evidenceStatus: ModelingDocumentEvidenceStatus;
  evidenceRefs: string[];
};

export type ModelingDocumentSemanticPayload = {
  schemaVersion: 1;
  projectId: string;
  entities: ModelingDocumentSemanticObject[];
  events: ModelingDocumentSemanticObject[];
  fields: ModelingDocumentSemanticField[];
  relations: ModelingDocumentSemanticRelation[];
  dimensions: ModelingDocumentSemanticDimension[];
  metrics: ModelingDocumentSemanticMetric[];
  hierarchies: ModelingDocumentSemanticHierarchy[];
  ruleCandidates: ModelingDocumentRuleCandidate[];
  aliases: ModelingDocumentSemanticAlias[];
  timeRules: ModelingDocumentSemanticTimeRule[];
  technicalAssets: Array<{ code: string; name: string; reason: string; evidenceRefs: string[] }>;
  pendingAssets: Array<{ code: string; name: string; reason: string; evidenceRefs: string[] }>;
  exclusions: Array<{ code: string; name: string; reason: string; decision: string; evidenceRefs: string[] }>;
  evidenceBindings: Array<{
    objectKind: 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC' | 'HIERARCHY' | 'RULE' | 'ALIAS' | 'TIME_RULE';
    objectCode: string;
    ownerCode?: string;
    status: ModelingDocumentEvidenceStatus;
    evidenceRefs: string[];
    supportClaimIds: string[];
  }>;
  evidenceContext?: {
    sources: Array<Record<string, unknown>>;
    evidence: Array<Record<string, unknown>>;
    locators: Record<string, Record<string, unknown>>;
    claims: Array<Record<string, unknown>>;
    findings: Array<Record<string, unknown>>;
  };
};

export type ModelingDocumentArtifact = {
  artifactId: string;
  revision: number;
  derivedFromArtifactId?: string;
  projectId: string;
  documentCode: string;
  title: string;
  origin: 'MANUAL_UPLOAD' | 'STANDARDIZATION';
  sourceBatch?: { batchId: string; fingerprint: string; snapshotIds: string[] };
  delta?: {
    kind: 'SOURCE_ACCUMULATION' | 'DECISION';
    addedSourceIds: string[];
    addedSnapshotIds: string[];
    addedEvidenceRefs: string[];
    addedClaimIds: string[];
    resolvedIssueIds: string[];
  };
  sections: Record<ModelingDocumentSection, string>;
  assertions: StructuredModelingAssertion[];
  semanticPayload?: { data: ModelingDocumentSemanticPayload; sha256: string };
  review?: ReviewSnapshot;
  markdown: { fileName: string; content: string; sha256: string; byteLength: number };
  generation?: {
    runId: string; agentVersion: string; promptVersion: string; inputFingerprint: string;
  };
  approval?: {
    authorUserId: string;
    authorConfirmation?: { actorUserId: string; at: string; reason: string; markdownSha256: string };
    reviewerApproval?: { actorUserId: string; at: string; reason: string; markdownSha256: string };
  };
  validation: { errors: ValidationIssue[]; warnings: ValidationIssue[]; gaps: ValidationIssue[] };
  status: 'DRAFT' | 'AWAITING_CONFIRMATION' | 'AWAITING_REVIEW' | 'APPROVED' | 'FROZEN' | 'REJECTED';
  audit: Array<{ action: string; actorUserId: string; at: string; detail: string }>;
  createdAt: string;
  updatedAt: string;
};

export type ModelingDocumentImportRecord = {
  importId: string;
  artifactId: string;
  markdownSha256: string;
  importedAt: string;
};

export type ModelingDocumentCommand =
  | {
      type: 'REGISTER_MANUAL_DOCUMENT'; projectId: string; documentCode: string;
      fileName: string; content: string; actorUserId: string;
    }
  | {
      type: 'GENERATE_DOCUMENT'; projectId: string; documentCode: string; title: string;
      sourceBatch: { batchId: string; fingerprint: string; snapshotIds: string[] };
      actorUserId: string;
    }
  | {
      type: 'UPDATE_SECTION'; artifactId: string; expectedRevision: number;
      section: ModelingDocumentSection; content: string; actorUserId: string;
    }
  | {
      type: 'RECORD_DECISIONS'; artifactId: string; expectedRevision: number; actorUserId: string;
      decisions: Array<{ issueId: string; resolutionId: string; reason: string }>;
    }
  | {
      type: 'CONFIRM_AUTHOR'; artifactId: string; expectedRevision: number;
      actorUserId: string; reason: string;
    }
  | {
      type: 'APPROVE_DOCUMENT'; artifactId: string; expectedRevision: number;
      actorUserId: string; reason: string;
    }
  | {
      type: 'FREEZE_DOCUMENT'; artifactId: string; expectedRevision: number;
      actorUserId: string; reason: string;
    }
  | { type: 'REJECT_DOCUMENT'; artifactId: string; expectedRevision: number; actorUserId: string; reason: string }
  | { type: 'IMPORT_TO_AI'; artifactId: string; markdownSha256: string };

export type ModelingDocumentRuntime = {
  list(projectId: string): Promise<ModelingDocumentArtifact[]>;
  read(artifactId: string): Promise<ModelingDocumentArtifact | null>;
  review(artifactId: string): Promise<ScalableReviewQueryService>;
  execute(command: ModelingDocumentCommand): Promise<{ artifact: ModelingDocumentArtifact; importRecord?: ModelingDocumentImportRecord }>;
};
