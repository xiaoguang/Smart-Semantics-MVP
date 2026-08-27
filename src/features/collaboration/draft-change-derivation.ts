import type { CollaborationCatalog, MaterializedCollaborationData, ModelChange } from './types.ts';
import { projectCatalogBrowser } from './catalog-browser-adapter.ts';
import type {
  ModelBrowserDetail,
  ModelBrowserObject,
  ModelBrowserObjectKind,
  ModelBrowserObjectRef,
} from '../model-browser/types.ts';

const categoryByKind: Record<ModelBrowserObjectKind, string> = {
  ENTITY: '实体', EVENT: '事件', FIELD: '字段', RELATION: '关系', DIMENSION: '维度',
  METRIC: '指标', HIERARCHY: '层级', RULE: '规则', ALIAS: '同义词', TIME_RULE: '时间语义',
};

const objectKey = (ref: Pick<ModelBrowserObjectRef, 'kind' | 'objectId' | 'ownerId'>) => (
  `${ref.kind}:${ref.ownerId ?? ''}:${ref.objectId}`
);

const draftRef = (ref: Pick<ModelBrowserObjectRef, 'kind' | 'objectId' | 'ownerId'>, draftId: string): ModelBrowserObjectRef => ({
  scope: 'DRAFT', draftId, kind: ref.kind, objectId: ref.objectId,
  ...(ref.ownerId ? { ownerId: ref.ownerId } : {}),
});

function syntheticCatalog(input: {
  modelSpaceId: string;
  catalogId: string;
  data: MaterializedCollaborationData;
}): CollaborationCatalog {
  return {
    catalogId: input.catalogId, catalogVersion: 'V0', fingerprint: input.catalogId,
    modelSpaceId: input.modelSpaceId, data: input.data, authorUserId: 'DRAFT',
    reviewerUserIds: [], publisherUserId: 'DRAFT', publishedAt: '1970-01-01T00:00:00.000Z',
    requestId: input.catalogId,
  };
}

function comparableDetails(object: ModelBrowserObject) {
  const values = new Map<string, string>();
  const add = (details: ModelBrowserDetail[] | undefined, prefix = '') => details?.forEach((detail) => {
    values.set(`${prefix}${detail.label}`, detail.value);
  });
  add(object.details);
  add(object.technicalDetails, '技术·');
  return values;
}

function changedFields(before: ModelBrowserObject | undefined, after: ModelBrowserObject | undefined) {
  if (!before && after) return [{ label: '对象定义', after: after.summary || after.name }];
  if (before && !after) return [{ label: '对象定义', before: before.summary || before.name }];
  if (!before || !after) return [];
  const changes: Array<{ label: string; before?: string; after?: string }> = [];
  if (before.name !== after.name) changes.push({ label: '名称', before: before.name, after: after.name });
  if ((before.summary ?? '') !== (after.summary ?? '')) changes.push({
    label: '业务说明', before: before.summary || '未填写', after: after.summary || '未填写',
  });
  const beforeDetails = comparableDetails(before);
  const afterDetails = comparableDetails(after);
  new Set([...beforeDetails.keys(), ...afterDetails.keys()]).forEach((label) => {
    const left = beforeDetails.get(label); const right = afterDetails.get(label);
    if (left === right) return;
    changes.push({ label, ...(left !== undefined ? { before: left } : {}), ...(right !== undefined ? { after: right } : {}) });
  });
  if (changes.length === 0 && before.code !== after.code) changes.push({ label: '技术编码', before: before.code, after: after.code });
  return changes;
}

function relatedRefs(object: ModelBrowserObject | undefined, draftId: string) {
  if (!object) return [];
  const refs = [
    ...(object.parentRef ? [object.parentRef] : []),
    ...object.details.flatMap((detail) => detail.links?.map((link) => link.ref) ?? []),
    ...(object.technicalDetails ?? []).flatMap((detail) => detail.links?.map((link) => link.ref) ?? []),
  ];
  return [...new Map(refs.map((ref) => [objectKey(ref), draftRef(ref, draftId)])).values()];
}

export function deriveDraftObjectChanges(input: {
  draftId: string;
  modelSpaceId: string;
  base?: MaterializedCollaborationData;
  next: MaterializedCollaborationData;
}): ModelChange[] {
  const baseObjects = input.base
    ? projectCatalogBrowser(syntheticCatalog({ modelSpaceId: input.modelSpaceId, catalogId: 'draft-base', data: input.base }), { strictEvidence: false }).objects
    : [];
  const nextObjects = projectCatalogBrowser(syntheticCatalog({ modelSpaceId: input.modelSpaceId, catalogId: 'draft-next', data: input.next }), { strictEvidence: false }).objects;
  const beforeByKey = new Map(baseObjects.map((object) => [objectKey(object.ref), object]));
  const afterByKey = new Map(nextObjects.map((object) => [objectKey(object.ref), object]));
  const keys = new Set([...beforeByKey.keys(), ...afterByKey.keys()]);
  const changes: ModelChange[] = [];

  keys.forEach((key) => {
    const before = beforeByKey.get(key); const after = afterByKey.get(key);
    const fields = changedFields(before, after);
    if (before && after && fields.length === 0) return;
    const object = after ?? before;
    if (!object) return;
    const objectName = object.name || object.code || '未命名对象';
    const operation: NonNullable<ModelChange['operation']> = !before ? 'ADDED' : !after ? 'REMOVED' : 'MODIFIED';
    const evidenceRefs = [...new Set([...(before?.evidenceIds ?? []), ...(after?.evidenceIds ?? [])])];
    const objectRef = draftRef(object.ref, input.draftId);
    changes.push({
      changeId: `change_${operation.toLocaleLowerCase()}_${object.ref.kind.toLocaleLowerCase()}_${object.ref.ownerId ? `${object.ref.ownerId}_` : ''}${object.ref.objectId}`,
      category: categoryByKind[object.ref.kind],
      summary: operation === 'ADDED' ? `新增${categoryByKind[object.ref.kind]}“${objectName}”`
        : operation === 'REMOVED' ? `删除${categoryByKind[object.ref.kind]}“${objectName}”`
          : `${objectName}的${fields.map((field) => field.label).join('、')}已更新`,
      operation, kind: object.ref.kind, objectRef, name: objectName, changedFields: fields,
      evidenceRefs, affectedRefs: relatedRefs(after ?? before, input.draftId),
      attention: operation === 'REMOVED' || evidenceRefs.length === 0 ? 'VERIFY' : 'NORMAL',
    });
  });

  return changes.sort((left, right) => `${left.kind}:${left.objectRef?.ownerId ?? ''}:${left.objectRef?.objectId}`
    .localeCompare(`${right.kind}:${right.objectRef?.ownerId ?? ''}:${right.objectRef?.objectId}`, 'zh-CN'));
}
