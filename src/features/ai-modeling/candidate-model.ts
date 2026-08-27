export type CandidateEvidenceStatus = 'VERIFIED' | 'CODE_SUPPORTED' | 'DATABASE_ONLY' | 'NEEDS_CONFIRMATION';

export type CandidateField = {
  id: string;
  code: string;
  name: string;
  description: string;
  dataType: string;
  ownerTableCode: string;
  evidenceIds: string[];
  supportClaimIds?: string[];
  status: CandidateEvidenceStatus;
};

export type CandidateTable = {
  id: string;
  code: string;
  name: string;
  kind: 'ENTITY' | 'EVENT';
  description: string;
  fields: CandidateField[];
  evidenceIds: string[];
  supportClaimIds?: string[];
  status: CandidateEvidenceStatus;
};

export type CandidateModelObject = {
  id: string;
  code: string;
  name: string;
  description: string;
  evidenceIds: string[];
  supportClaimIds?: string[];
  status: CandidateEvidenceStatus;
  target?: string;
  formula?: string;
  risk?: string;
};

export type CandidateModelSnapshot = {
  status: 'DRAFT' | 'READY';
  generatedAt: string | null;
  counts: {
    entities: number;
    events: number;
    fields: number;
    relations: number;
    dimensions: number;
    metrics: number;
    hierarchies: number;
    ruleCandidates: number;
    pendingAssets: number;
  };
  tables: CandidateTable[];
  entities: CandidateTable[];
  events: CandidateTable[];
  fields: CandidateField[];
  relations: CandidateModelObject[];
  dimensions: CandidateModelObject[];
  metrics: CandidateModelObject[];
  hierarchies: CandidateModelObject[];
  ruleCandidates: CandidateModelObject[];
  aliases: CandidateModelObject[];
  pendingAssets: CandidateModelObject[];
};
