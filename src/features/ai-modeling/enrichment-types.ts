export type SourceDimension = {
  dimension_id: string;
  name: string;
  definition: string;
  target_type: 'ENTITY' | 'EVENT' | 'FIELD';
  target_id: string;
  owner_id: string | null;
  evidence_ids: string[];
  confidence: number;
};

export type SourceRuleCandidate = {
  rule_id: string;
  name: string;
  definition: string;
  target_type: 'ENTITY' | 'EVENT' | 'FIELD' | 'METRIC';
  target_id: string;
  owner_id: string | null;
  evidence_ids: string[];
  confidence: number;
};

export type SourceMemberHierarchy = {
  hierarchy_id: string;
  name: string;
  owner_table_id: string;
  attribute_id: string;
  levels: Array<{ level_id: string; name: string; depth: number; parent_level_id: string | null }>;
  members: Array<{
    member_id: string; name: string; code: string; level_id: string; parent_member_id: string | null;
  }>;
  evidence_ids: string[];
  confidence: number;
};

export type SourceSynonymGroup = {
  group_id: string;
  target_type: 'ENTITY' | 'EVENT' | 'FIELD' | 'DIMENSION' | 'METRIC' | 'RULE';
  target_id: string;
  owner_id: string | null;
  aliases: Array<{ text: string; confidence: number; evidence_ids: string[] }>;
};

export type SemanticEnrichmentArtifact = {
  metadata: Record<string, unknown>;
  dimensions: SourceDimension[];
  rule_candidates: SourceRuleCandidate[];
  member_hierarchies: SourceMemberHierarchy[];
  synonym_groups: SourceSynonymGroup[];
};
