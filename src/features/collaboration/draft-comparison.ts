import type { ChangeRequest, CollaborationCatalog, ModelChange, PersonalDraft } from './types.ts';
import { projectCatalogBrowser } from './catalog-browser-adapter.ts';
import type { ModelBrowserDetail, ModelBrowserObjectKind, ModelBrowserObjectRef, ModelBrowserView } from '../model-browser/types.ts';

const kinds: ModelBrowserObjectKind[] = [
  'ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC', 'RULE', 'ALIAS', 'TIME_RULE',
];

export type DraftReviewProjection = {
  title: string;
  authorUserId: string;
  baseVersion: string;
  targetVersion: string;
  evidenceBatch: string;
  changes: Required<Pick<ModelChange,
    'changeId' | 'operation' | 'kind' | 'objectRef' | 'name' | 'changedFields' | 'evidenceRefs' | 'affectedRefs' | 'attention' | 'summary' | 'category'>>[];
  unlinkedChanges: DraftReviewUnlinkedChange[];
  countsByKind: Record<ModelBrowserObjectKind, number>;
  countsByOperation: Record<'MODIFIED' | 'ADDED' | 'REMOVED', number>;
  riskCount: number;
};

export type DraftReviewEntry =
  | { type: 'OBJECT_CHANGE'; change: DraftReviewProjection['changes'][number] }
  | DraftReviewUnlinkedChange;

export type DraftReviewUnlinkedChange = {
  type: 'UNLINKED_CHANGE';
  changeId: string;
  category: string;
  summary: string;
  reason: 'LEGACY' | 'PROCESS_ONLY';
};

function complete(change: ModelChange): DraftReviewProjection['changes'][number] | null {
  if (!change.operation || !change.kind || !change.objectRef || !change.name || !change.changedFields || !change.evidenceRefs || !change.affectedRefs || !change.attention) {
    return null;
  }
  return change as DraftReviewProjection['changes'][number];
}

export function projectDraftReview(input: { draft: PersonalDraft; baseCatalog: CollaborationCatalog }): DraftReviewProjection {
  const entries: DraftReviewEntry[] = input.draft.changes.map((change) => {
    const objectChange = complete(change);
    return objectChange ? { type: 'OBJECT_CHANGE', change: objectChange } : {
      type: 'UNLINKED_CHANGE', changeId: change.changeId, category: change.category,
      summary: change.summary, reason: change.category === 'AI建模' ? 'PROCESS_ONLY' : 'LEGACY',
    };
  });
  const changes = entries.flatMap((entry) => entry.type === 'OBJECT_CHANGE' ? [entry.change] : []);
  const unlinkedChanges = entries.flatMap((entry) => entry.type === 'UNLINKED_CHANGE' ? [entry] : []);
  const countsByKind = Object.fromEntries(kinds.map((kind) => [kind, 0])) as Record<ModelBrowserObjectKind, number>;
  const countsByOperation = { MODIFIED: 0, ADDED: 0, REMOVED: 0 };
  changes.forEach((change) => {
    countsByKind[change.kind] += 1;
    countsByOperation[change.operation] += 1;
  });
  return {
    title: input.draft.title,
    authorUserId: input.draft.ownerUserId,
    baseVersion: input.baseCatalog.catalogVersion,
    targetVersion: `V${Number(input.baseCatalog.catalogVersion.replace(/^V/, '')) + 1}`,
    evidenceBatch: input.draft.materializedData.semanticSidecar?.schemaVersion === 2
      ? input.draft.materializedData.semanticSidecar.batch.batchId : '未保留证据批次',
    changes, unlinkedChanges,
    countsByKind,
    countsByOperation,
    riskCount: changes.filter((change) => change.attention !== 'NORMAL' || change.operation === 'REMOVED').length,
  };
}

export function projectDraftWorkflowSummary(draft: PersonalDraft) {
  const sidecar = draft.materializedData.semanticSidecar?.schemaVersion === 2
    ? draft.materializedData.semanticSidecar : undefined;
  return {
    batchRevision: sidecar?.batch.revision ?? 0,
    sourceCount: sidecar?.sources.length ?? 0,
    findingCount: sidecar?.findings.length ?? 0,
    resolvedConflictCount: sidecar?.findings.filter((finding) => finding.originStatus === 'CONFLICT' && finding.resolution).length ?? 0,
    changeCount: draft.changes.length,
  };
}

export function projectDraftPublicationChecks(input: {
  draft: PersonalDraft;
  request: ChangeRequest;
  baseCatalog: CollaborationCatalog;
}) {
  const sidecar = input.draft.materializedData.semanticSidecar?.schemaVersion === 2
    ? input.draft.materializedData.semanticSidecar : undefined;
  const completeChanges = input.draft.changes.filter((change) => change.objectRef && change.evidenceRefs?.length).length;
  return [
    { key: 'batch', label: `证据批次 R${sidecar?.batch.revision ?? '?'} 已冻结`, passed: sidecar?.batch.revision === 2 },
    { key: 'sources', label: `${sidecar?.sources.length ?? 0} 个来源快照完整`, passed: sidecar?.sources.length === 8 && sidecar.batch.snapshotIds.length === 8 },
    { key: 'changes', label: `${input.draft.changes.length} 项变化可追溯`, passed: input.draft.changes.length === 24 && completeChanges === 24 },
    { key: 'review', label: `审核已通过且基线仍为 ${input.draft.baseCatalogVersion}`, passed: input.request.status === 'APPROVED' && input.baseCatalog.catalogVersion === input.draft.baseCatalogVersion && input.baseCatalog.fingerprint === input.draft.baseFingerprint },
  ];
}

const searchKindLabel: Partial<Record<ModelBrowserObjectKind, string>> = {
  ENTITY: '实体', EVENT: '事件', FIELD: '字段', RELATION: '关系', DIMENSION: '维度',
  METRIC: '指标', HIERARCHY: '层级', RULE: '规则', ALIAS: '同义词', TIME_RULE: '时间语义',
};

export function shouldShowDraftSearch(changeCount: number) {
  return changeCount > 40;
}

export function paginateDraftChanges<T extends Pick<ModelChange, 'changeId' | 'name' | 'summary'> & Pick<ModelChange, 'category' | 'kind' | 'operation' | 'attention'>>(changes: T[], input: { query: string; limit: number }) {
  const query = input.query.trim().toLocaleLowerCase('zh-CN');
  const filtered = query ? changes.filter((change) => (
    `${change.name ?? ''} ${change.summary} ${change.category ?? ''} ${change.kind ? searchKindLabel[change.kind] ?? change.kind : ''}`
      .toLocaleLowerCase('zh-CN').includes(query)
  )) : changes;
  const priority = (change: T) => {
    if (change.operation === 'REMOVED') return 0;
    if (change.attention === 'CONFLICT') return 1;
    if (change.attention === 'VERIFY') return 2;
    if (change.operation === 'ADDED') return 3;
    return 4;
  };
  const sorted = filtered.map((change, index) => ({ change, index }))
    .sort((left, right) => priority(left.change) - priority(right.change) || left.index - right.index)
    .map(({ change }) => change);
  return { visible: sorted.slice(0, Math.max(0, input.limit)), total: sorted.length, hasMore: sorted.length > input.limit };
}

function draftRef(ref: ModelBrowserObjectRef, draftId: string): ModelBrowserObjectRef {
  return { scope: 'DRAFT', draftId, kind: ref.kind, objectId: ref.objectId, ...(ref.ownerId ? { ownerId: ref.ownerId } : {}) };
}

function draftDetails(details: ModelBrowserDetail[], draftId: string): ModelBrowserDetail[] {
  return details.map((detail) => ({ ...detail, links: detail.links?.map((link) => ({ ...link, ref: draftRef(link.ref, draftId) })) }));
}

function draftObject(object: ModelBrowserView['objects'][number], draftId: string): ModelBrowserView['objects'][number] {
  return {
    ...object,
    ref: draftRef(object.ref, draftId),
    parentRef: object.parentRef ? draftRef(object.parentRef, draftId) : undefined,
    childRefs: object.childRefs.map((ref) => draftRef(ref, draftId)),
    details: draftDetails(object.details, draftId),
    technicalDetails: object.technicalDetails ? draftDetails(object.technicalDetails, draftId) : undefined,
  };
}

export function projectDraftBrowser(draft: PersonalDraft, baseCatalog: CollaborationCatalog): ModelBrowserView {
  const projected = projectCatalogBrowser({
    ...baseCatalog, catalogId: draft.draftId, catalogVersion: `V${Number(baseCatalog.catalogVersion.replace(/^V/, '')) + 1}`,
    data: draft.materializedData,
  });
  const objects = projected.objects.map((object) => draftObject(object, draft.draftId));
  const objectKeys = new Set(objects.map((object) => `${object.ref.kind}:${object.ref.ownerId ?? ''}:${object.ref.objectId}`));
  const base = projectCatalogBrowser(baseCatalog);
  draft.changes.filter((change) => change.operation === 'REMOVED' && change.objectRef).forEach((change) => {
    const ref = change.objectRef!;
    const key = `${ref.kind}:${ref.ownerId ?? ''}:${ref.objectId}`;
    if (objectKeys.has(key)) return;
    const removed = base.objects.find((object) => object.ref.kind === ref.kind
      && object.ref.objectId === ref.objectId && (object.ref.ownerId ?? '') === (ref.ownerId ?? ''));
    if (!removed) return;
    objects.push(draftObject({ ...removed, summary: `${removed.summary ?? ''}${removed.summary ? ' ' : ''}该对象将在 V2 中删除。` }, draft.draftId));
    objectKeys.add(key);
  });
  return {
    ...projected,
    identity: { title: '拟发布 V2', subtitle: '个人草稿 · 尚未发布', immutable: false },
    objects,
    reconciliationFindings: projected.reconciliationFindings.map((finding) => ({
      ...finding, affectedRefs: finding.affectedRefs.map((ref) => draftRef(ref, draft.draftId)),
    })),
  };
}
