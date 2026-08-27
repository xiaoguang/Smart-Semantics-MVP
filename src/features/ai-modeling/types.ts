import type { SemanticEnrichmentArtifact, SourceMemberHierarchy, SourceRuleCandidate, SourceSynonymGroup } from './enrichment-types.ts';
export type { SemanticEnrichmentArtifact, SourceDimension, SourceMemberHierarchy, SourceRuleCandidate, SourceSynonymGroup } from './enrichment-types.ts';

export type DocumentVersion = 'v1' | 'v2' | 'v3' | 'v4';
export type ModelVersion = 'V1' | 'V2' | 'V3';

export type Evidence = {
  evidence_id: string;
  source_file: string;
  section: string;
  line_start?: number;
  line_end?: number;
  quote: string;
};

export type SourceField = {
  field_id: string;
  name: string;
  description: string;
  data_type: string;
  semantic_type: string;
  unit: string | null;
  format_pattern: string | null;
  nullable: boolean;
  field_role: string;
  is_filterable: boolean;
  is_groupable: boolean;
  is_display: boolean;
  is_sortable: boolean;
  provenance_type: string;
  evidence_ids: string[];
  inference_reason: string | null;
  unresolved_item_id: string | null;
  references: { table_id: string; field_id: string } | null;
};

export type SourceEntityTable = {
  table_id: string;
  source_candidate_id: string;
  name: string;
  definition: string;
  entity_kind: string;
  business_key: string[];
  evidence_ids: string[];
  fields: SourceField[];
};

export type SourceEventTable = {
  table_id: string;
  source_candidate_id: string;
  name: string;
  definition: string;
  grain: string;
  factless: boolean;
  measure_ids: string[];
  evidence_ids: string[];
  fields: SourceField[];
};

export type SourceRelation = {
  relationship_id: string;
  name: string;
  relationship_kind: string;
  from_table: string;
  from_field: string;
  to_table: string;
  to_field: string;
  cardinality: string;
  evidence_ids: string[];
  provenance_type: string;
  inference_reason: string | null;
};

export type SourceMetric = {
  metric_id: string;
  name: string;
  definition: string;
  event_table: string;
  measure_fields: string[];
  aggregation: string;
  formula: string;
  numerator_field: string | null;
  denominator_field: string | null;
  semi_additive_over_field: string | null;
  evidence_ids: string[];
};

export type AnalysisCandidate = {
  candidate_id: string;
  name: string;
  definition: string;
  evidence_ids: string[];
  confidence: number;
};

export type GenerationNote = {
  id: string;
  kind: string;
  sourceId: string;
  sourceName: string;
  disposition: string;
  target: string;
  reason: string;
};

export type ReviewGroup = { id: string; name: string; itemIds: string[] };
export type ReviewItem = {
  id: string;
  type: string;
  objectId: string;
  name: string;
  target: string;
  disposition: string;
  reason: string;
  groupId: string;
  reviewClass?: 'AUTO' | 'CONFLICT' | 'WEAK_EVIDENCE' | 'SYSTEM_GENERATED';
  confidence?: number;
  evidenceIds?: string[];
};

export type FixtureDocument = {
  documentVersion: DocumentVersion;
  modelVersion: ModelVersion | null;
  status: 'PUBLISHED' | 'NEEDS_SUPPLEMENT';
  fileName: string;
  title: string;
  summary: string;
  content: string;
  sha256: string;
  qualityGate: {
    publishable: boolean;
    blockers: Array<{ title: string; detail: string; impact: string }>;
    warnings: string[];
  };
  artifacts: {
    analysis: {
      business_summary: {
        system_name: string;
        business_goal: string;
        business_scope: string[];
      };
      dimension_candidates: AnalysisCandidate[];
      entity_candidates: AnalysisCandidate[];
      event_candidates: AnalysisCandidate[];
      metric_candidates: AnalysisCandidate[];
      grain_candidates: AnalysisCandidate[];
      hierarchy_candidates: AnalysisCandidate[];
      evidence_catalog: Evidence[];
      unresolved: Array<{ unresolved_id?: string; title?: string; question?: string; detail?: string }>;
      example_questions: string[];
    };
    model: {
      entity_tables: SourceEntityTable[];
      event_tables: SourceEventTable[];
      relationships: SourceRelation[];
      metrics: SourceMetric[];
      evidence_catalog: Evidence[];
      unresolved: unknown[];
      candidate_resolutions: unknown[];
    };
    enrichment: SemanticEnrichmentArtifact;
    validation: { valid: boolean; errors?: unknown[]; warnings?: unknown[] };
    enrichmentValidation: { valid?: boolean; errors?: unknown[]; warnings?: unknown[] };
    ddl: string;
    report: string;
    callSummary: unknown;
  };
  generationNotes: GenerationNote[];
  review: { groups: ReviewGroup[]; items: ReviewItem[] };
  adjacentDiff: unknown;
  artifactManifest: unknown;
};

export type SemanticFixture = {
  schemaVersion: number;
  system: { code: string; name: string };
  documents: FixtureDocument[];
};

export type WorkspaceSystemCode = string;
export type WorkspaceVersion = 'WORKSPACE' | ModelVersion;

export type LinguanField = {
  id: number;
  code: string;
  name: string;
  description: string;
  ownerTableCode: string;
  ownerTableName: string;
  ownerTableKind: 'ENTITY' | 'EVENT';
  dataType: string;
  semanticType: string;
  role: string;
  unit: string | null;
  filterable: boolean;
  groupable: boolean;
  evidenceIds: string[];
  inferenceReason: string | null;
};

export type LinguanTable = {
  id: number;
  code: string;
  name: string;
  kind: 'ENTITY' | 'EVENT';
  description: string;
  businessKeys: string[];
  grain: string | null;
  fields: LinguanField[];
  evidenceIds: string[];
  physicalMappings?: Array<{ datasourceName: string; schemaName: string; tableName: string }>;
};

export type LinguanRelation = {
  id: number;
  code: string;
  name: string;
  kind: string;
  cardinality: string;
  from: { tableCode: string; tableName: string; fieldCode: string; fieldName: string };
  to: { tableCode: string; tableName: string; fieldCode: string; fieldName: string };
  evidenceIds: string[];
};

export type LinguanMetric = {
  id: number;
  code: string;
  name: string;
  description: string;
  eventTableCode: string;
  eventTableName: string;
  measureFieldCodes: string[];
  aggregation: string;
  formula: string;
  unit: string | null;
  numeratorField: string | null;
  denominatorField: string | null;
  semiAdditiveOverField: string | null;
  evidenceIds: string[];
  editability: 'EDITABLE' | 'READ_ONLY';
  compatibilityReason: string;
};

export type LinguanDimension = {
  id: string;
  name: string;
  description: string;
  disposition: string;
  target: string;
  targetTableCode?: string;
  targetFieldCode?: string;
  evidenceIds: string[];
};

export type RuleCandidate = {
  id: string;
  name: string;
  sourceType: 'SEMANTIC_ENRICHMENT';
  description: string;
  target: string;
  targetType: SourceRuleCandidate['target_type'];
  targetId: string;
  ownerId: string | null;
  evidenceIds: string[];
  confidence: number;
  compatibility: 'NEEDS_STRUCTURE';
};

export type LinguanMemberHierarchy = {
  id: string;
  name: string;
  ownerTableCode: string;
  attributeCode: string;
  levels: SourceMemberHierarchy['levels'];
  members: SourceMemberHierarchy['members'];
  evidenceIds: string[];
  confidence: number;
};

export type LinguanAlias = {
  id: string;
  text: string;
  targetType: SourceSynonymGroup['target_type'];
  targetId: string;
  ownerId: string | null;
  confidence: number;
  evidenceIds: string[];
};

export type LinguanModelSnapshot = {
  systemCode: string;
  systemName: string;
  documentVersion: DocumentVersion;
  modelVersion: ModelVersion;
  projectionRevision: number;
  sourceSha256: string;
  immutableSource: true;
  readOnly: boolean;
  tables: LinguanTable[];
  entities: LinguanTable[];
  events: LinguanTable[];
  fields: LinguanField[];
  relations: LinguanRelation[];
  dimensions: LinguanDimension[];
  metrics: LinguanMetric[];
  executableRules: Array<{ id: string; name: string; description: string; source: 'MANUAL' }>;
  ruleCandidates: RuleCandidate[];
  hierarchies: LinguanMemberHierarchy[];
  aliases: LinguanAlias[];
  evidence: Evidence[];
  generationNotes: GenerationNote[];
  compatibilityNotes: string[];
  timeSemantics?: {
    calendars: Array<{
      code: string; name: string; type: 'NATURAL' | 'FISCAL' | 'RETAIL_445' | 'CUSTOM';
      description: string;
    }>;
    rules: Array<{
      code: string; text: string; calendarCode: string; granularity: 'YEAR' | 'QUARTER' | 'MONTH' | 'WEEK' | 'DAY';
      rangeType?: 'CURRENT' | 'PREVIOUS' | 'ROLLING' | 'SINCE_START' | 'HOLIDAY' | 'CUSTOM' | 'ABSOLUTE';
      offsetValue?: number;
      rollingDays?: number;
      holidayCode?: string;
      offsetStart?: number;
      offsetEnd?: number;
      fixedStartDate?: string;
      fixedEndDate?: string;
      isRecurring?: boolean;
      source?: 'DOCUMENT' | 'SYSTEM_TEMPLATE' | 'MANUAL';
    }>;
  };
};
