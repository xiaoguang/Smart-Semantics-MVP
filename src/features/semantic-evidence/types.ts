import type { EvidenceAuthority, SourceEvidenceLocator } from '../ai-modeling/source-bundle.ts';

export type RdfTerm =
  | { kind: 'IRI'; value: string }
  | { kind: 'BNODE'; value: string; scope: string }
  | { kind: 'LITERAL'; value: string; datatype?: string; language?: string };

export type RdfQuad = {
  subject: RdfTerm;
  predicate: RdfTerm;
  object: RdfTerm;
  graph?: RdfTerm;
};

export type StructuredAssertion = {
  subject: string;
  predicate: string;
  value: string | number | boolean;
};

export type EvidenceRecord = {
  evidenceId: string;
  statementId: string;
  snapshotId: string;
  connectionId: string;
  connectionName: string;
  authority: EvidenceAuthority;
  statement:
    | { kind: 'RDF_QUAD'; quad: RdfQuad }
    | { kind: 'ASSERTION'; assertion: StructuredAssertion };
  locator: SourceEvidenceLocator;
  upstreamEvidenceIds?: string[];
  checksum: string;
};

export type PredicateMapping = {
  predicate: string;
  semantic: string;
  label: string;
  conflictSeverity?: 'WEAK' | 'BLOCKER';
  affectedObjectRefs?: string[];
};

export type SemanticCollectionProfile = {
  namedGraphs: string[];
  namespaces: Record<string, string>;
  seedConcepts: string[];
  predicateMappings: PredicateMapping[];
  excludedPredicates: string[];
  upstreamSourceIds: string[];
};

export type SemanticEvidenceCompilationInput = {
  exampleId: string;
  projectId: string;
  title: string;
  profile: SemanticCollectionProfile;
  records: EvidenceRecord[];
};

export type SemanticEvidenceClaim = {
  claimId: string;
  subject: string;
  predicate: string;
  predicateLabel: string;
  value: string;
  authority: EvidenceAuthority;
  evidenceRefs: string[];
  connectionIds: string[];
  rootConnectionIds: string[];
  independentSourceCount: number;
  support: 'MULTI_SOURCE' | 'SINGLE_SOURCE' | 'DERIVED_ONLY';
};

export type ReconciliationParticipant = {
  connectionId: string;
  connectionName: string;
  authority: EvidenceAuthority;
  claimIds: string[];
  assertion: string;
  evidenceRefs: string[];
};

export type SemanticReconciliationFinding = {
  findingId: string;
  semanticKey: string;
  title: string;
  status: 'CONSISTENT' | 'CONFLICT' | 'SINGLE_SOURCE' | 'DERIVED';
  severity: 'CONSISTENT' | 'WEAK' | 'BLOCKER';
  participants: ReconciliationParticipant[];
  agreement?: string;
  difference?: string;
  affectedObjectRefs: string[];
};

export type SemanticCandidateKind =
  | 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC'
  | 'HIERARCHY' | 'RULE' | 'ALIAS' | 'TIME_SEMANTIC';

export type SemanticCandidate = {
  objectRef: string;
  kind: SemanticCandidateKind;
  code: string;
  name: string;
  summary: string;
  ownerRef?: string;
  status: 'RECOMMENDED' | 'VERIFY' | 'BLOCKED';
  claimIds: string[];
  evidenceRefs: string[];
  affectedObjectRefs: string[];
  technicalDefinition: Record<string, string>;
};

export type UnknownPredicate = {
  predicate: string;
  evidenceRefs: string[];
  sampleValues: string[];
};

export type SemanticEvidencePackage = {
  schemaVersion: 1;
  packageId: string;
  exampleId: string;
  projectId: string;
  title: string;
  fingerprint: string;
  records: EvidenceRecord[];
  claims: SemanticEvidenceClaim[];
  findings: SemanticReconciliationFinding[];
  candidates: SemanticCandidate[];
  unknownPredicates: UnknownPredicate[];
  validation: {
    valid: boolean;
    warnings: string[];
    recordCount: number;
    independentRootSourceCount: number;
  };
};

export type SemanticReviewBook = {
  files: Record<string, string>;
  zipBytes: Uint8Array;
};
