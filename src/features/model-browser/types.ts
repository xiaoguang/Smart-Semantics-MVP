import type { SemanticVisualKind } from '../../components/semantic-object-visuals.ts';

export type ModelBrowserObjectKind =
  | 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC'
  | 'HIERARCHY' | 'RULE' | 'ALIAS' | 'TIME_RULE';

export type ModelBrowserObjectRef =
  | {
      scope: 'RESULT';
      documentVersion: string;
      kind: ModelBrowserObjectKind;
      objectId: string;
      ownerId?: string;
    }
  | {
      scope: 'CATALOG';
      catalogId: string;
      kind: ModelBrowserObjectKind;
      objectId: string;
      ownerId?: string;
    }
  | {
      scope: 'DRAFT';
      draftId: string;
      kind: ModelBrowserObjectKind;
      objectId: string;
      ownerId?: string;
    };

export type ModelBrowserLink = { label: string; ref: ModelBrowserObjectRef };
export type ModelBrowserDetail = { label: string; value: string; links?: ModelBrowserLink[] };

export type ModelBrowserComparison = {
  operation: 'ADDED' | 'MODIFIED' | 'REMOVED';
  name: string;
  changedFields: Array<{ label: string; before?: string; after?: string }>;
  evidenceRefs: string[];
  affectedRefs: ModelBrowserObjectRef[];
};

export type ModelBrowserObject = {
  ref: ModelBrowserObjectRef;
  name: string;
  code: string;
  semanticKind?: SemanticVisualKind;
  summary?: string;
  parentRef?: ModelBrowserObjectRef;
  childRefs: ModelBrowserObjectRef[];
  details: ModelBrowserDetail[];
  technicalDetails?: ModelBrowserDetail[];
  evidenceIds: string[];
  evidenceStatus: 'VERIFIED' | 'NEEDS_CONFIRMATION';
  evidenceSupportClaimIds: string[];
};

export type ModelBrowserCounts = {
  entities: number;
  events: number;
  fields: number;
  relations: number;
  dimensions: number;
  metrics: number;
  hierarchies: number;
  rules: number;
  aliases: number;
  timeRules: number;
};

export type ModelBrowserView = {
  identity: { title: string; subtitle: string; immutable: boolean; catalogId?: string };
  counts: ModelBrowserCounts;
  objects: ModelBrowserObject[];
  evidence: Array<Record<string, unknown>>;
  evidenceLocators: Record<string, Record<string, unknown>>;
  generationNotes: Array<Record<string, unknown>>;
  coverageNotices: string[];
  sourcePackages: Array<{
    packageId: string;
    displayName: string;
    packageType: string;
    packageVersion: string;
    status: string;
    active: boolean;
  }>;
  sourceBatch?: {
    batchId: string;
    revision: number;
    frozenAt: string;
    fingerprint: string;
    coverage: string;
  };
  sourceSnapshots: Array<{
    snapshotId: string;
    connectionId: string;
    displayName: string;
    connectorType: string;
    versionRef: string;
    role: string;
    authority: string;
    summary: string;
    objectCounts: Record<string, number>;
    sourceSnapshotIdentity?: string;
    fingerprint?: string;
    manifestRef?: string;
    upstreamSnapshotIds?: string[];
    evidenceIds: string[];
    status?: 'READY' | 'NEEDS_CONFIRMATION';
  }>;
  reconciliationFindings: Array<{
    findingId: string;
    title: string;
    status: 'CONSISTENT' | 'CONFLICT' | 'SINGLE_SOURCE' | 'DERIVED';
    claims: Array<{ sourceId: string; sourceName: string; assertion: string; authority: string; evidenceRefs: string[] }>;
    agreement?: string;
    difference?: string;
    decision?: string;
    originStatus?: 'CONFLICT';
    resolution?: {
      decidedByUserId: string;
      decidedBy: string;
      decidedAt: string;
      reason: string;
    };
    affectedRefs: ModelBrowserObjectRef[];
  }>;
};

export function modelBrowserRefKey(ref: ModelBrowserObjectRef) {
  const scope = ref.scope === 'CATALOG' ? `CATALOG:${ref.catalogId}` : ref.scope === 'DRAFT' ? `DRAFT:${ref.draftId}` : `RESULT:${ref.documentVersion}`;
  return `${scope}:${ref.kind}:${ref.ownerId ?? ''}:${ref.objectId}`;
}
