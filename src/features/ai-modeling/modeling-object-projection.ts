import type { HydratedResult, ReviewDecision } from './runtime-types.ts';
import type { DocumentVersion, FixtureDocument, ReviewItem } from './types.ts';
import type { SemanticFieldRole, SemanticVisualKind } from '../../components/semantic-object-visuals.ts';

export type ModelingObjectKind = 'TABLE' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC';

export type ModelingObjectRef = {
  documentVersion: DocumentVersion;
  kind: ModelingObjectKind;
  objectId: string;
  ownerId?: string;
};

export type ObjectReviewStatus =
  | 'AUTO_APPROVED'
  | 'USER_APPROVED'
  | 'USER_REJECTED'
  | 'PENDING'
  | 'NOT_SEPARATELY_REVIEWED';

export type ProjectedModelObject = {
  ref: ModelingObjectRef;
  category: 'tables' | 'relations' | 'dimensions' | 'metrics';
  visualKind: SemanticVisualKind;
  fieldRole?: SemanticFieldRole;
  name: string;
  code: string;
  definition: string;
  subtitle: string;
  evidenceIds: string[];
  reviewStatus: ObjectReviewStatus;
  reviewMode: 'DIRECT' | 'TARGET' | 'NONE';
  reviewItemId?: string;
  reviewSourceRef?: ModelingObjectRef;
  childRefs: ModelingObjectRef[];
  details: Array<{ label: string; value: string; links?: Array<{ label: string; ref: ModelingObjectRef }> }>;
};

type ReviewObject = Pick<ReviewItem, 'id' | 'type' | 'objectId'>;

export function modelingObjectRefKey(ref: ModelingObjectRef) {
  return [ref.documentVersion, ref.kind, ref.ownerId ?? '', ref.objectId].join(':');
}

export function modelingRefForReviewItem(
  documentVersion: DocumentVersion,
  item: ReviewObject,
): ModelingObjectRef | undefined {
  if (item.type === 'ENTITY' || item.type === 'EVENT') {
    return { documentVersion, kind: 'TABLE', objectId: item.objectId };
  }
  if (item.type === 'FIELD') {
    const ownerId = item.id.split(':')[1];
    return ownerId ? { documentVersion, kind: 'FIELD', objectId: item.objectId, ownerId } : undefined;
  }
  if (item.type === 'RELATION') return { documentVersion, kind: 'RELATION', objectId: item.objectId };
  if (item.type === 'METRIC') return { documentVersion, kind: 'METRIC', objectId: item.objectId };
  return undefined;
}

function statusFromDecision(decision: ReviewDecision | undefined, hasReviewItem: boolean): ObjectReviewStatus {
  if (!decision) return hasReviewItem ? 'PENDING' : 'NOT_SEPARATELY_REVIEWED';
  if (decision.decision === 'REJECTED') return 'USER_REJECTED';
  return decision.source === 'AUTO' ? 'AUTO_APPROVED' : 'USER_APPROVED';
}

export function projectModelObjects(document: FixtureDocument, result: HydratedResult): ProjectedModelObject[] {
  const directReview = new Map<string, { itemId: string; ref: ModelingObjectRef }>();
  for (const item of document.review.items) {
    const ref = modelingRefForReviewItem(document.documentVersion, item);
    if (ref) directReview.set(modelingObjectRefKey(ref), { itemId: item.id, ref });
  }
  const directReviewView = (ref: ModelingObjectRef) => {
    const review = directReview.get(modelingObjectRefKey(ref));
    return {
      reviewStatus: statusFromDecision(review ? result.decisions[review.itemId] : undefined, Boolean(review)),
      ...(review ? { reviewItemId: review.itemId } : {}),
    };
  };
  const tables = [...document.artifacts.model.entity_tables, ...document.artifacts.model.event_tables];
  const tableNames = new Map(tables.map((table) => [table.table_id, table.name]));
  const tableRefs = new Map(tables.map((table) => [table.table_id, {
    documentVersion: document.documentVersion, kind: 'TABLE' as const, objectId: table.table_id,
  }]));
  const fieldRefs = new Map(tables.flatMap((table) => table.fields.map((field) => [`${table.table_id}:${field.field_id}`, {
    documentVersion: document.documentVersion, kind: 'FIELD' as const, objectId: field.field_id, ownerId: table.table_id,
  }] as const)));
  const objects: ProjectedModelObject[] = [];

  for (const table of tables) {
    const isEvent = 'grain' in table;
    const ref: ModelingObjectRef = { documentVersion: document.documentVersion, kind: 'TABLE', objectId: table.table_id };
    const childRefs = table.fields.map((field) => ({
      documentVersion: document.documentVersion, kind: 'FIELD' as const, objectId: field.field_id, ownerId: table.table_id,
    }));
    objects.push({
      ref, category: 'tables', visualKind: isEvent ? 'EVENT' : 'ENTITY', name: table.name, code: table.table_id, definition: table.definition,
      subtitle: isEvent ? '事件表' : '实体表', evidenceIds: table.evidence_ids,
      ...directReviewView(ref), reviewMode: 'DIRECT', childRefs,
      details: isEvent
        ? [{ label: '业务粒度', value: table.grain }, { label: '字段数量', value: `${table.fields.length}` }]
        : [{ label: '业务键', value: table.business_key.join('、') || '—' }, { label: '字段数量', value: `${table.fields.length}` }],
    });
    for (const field of table.fields) {
      const fieldRef = childRefs.find((value) => value.objectId === field.field_id)!;
      objects.push({
        ref: fieldRef, category: 'tables', visualKind: 'FIELD', name: field.name, code: field.field_id, definition: field.description,
        fieldRole: field.field_role === 'measure' ? 'MEASURE' : field.is_groupable ? 'DIMENSION' : 'PLAIN',
        subtitle: table.name, evidenceIds: field.evidence_ids,
        ...directReviewView(fieldRef), reviewMode: 'DIRECT', childRefs: [],
        details: [
          { label: '所属表', value: table.name }, { label: '数据类型', value: field.data_type },
          { label: '字段角色', value: field.field_role }, { label: '单位', value: field.unit ?? '—' },
        ],
      });
    }
  }

  for (const relation of document.artifacts.model.relationships) {
    const ref: ModelingObjectRef = { documentVersion: document.documentVersion, kind: 'RELATION', objectId: relation.relationship_id };
    objects.push({
      ref, category: 'relations', visualKind: 'RELATION', name: relation.name, code: relation.relationship_id,
      definition: relation.inference_reason ?? '', subtitle: relation.cardinality, evidenceIds: relation.evidence_ids,
      ...directReviewView(ref), reviewMode: 'DIRECT', childRefs: [],
      details: [
        { label: '起点', value: `${tableNames.get(relation.from_table) ?? relation.from_table}.${relation.from_field}`, links: [
          ...(tableRefs.get(relation.from_table) ? [{ label: tableNames.get(relation.from_table) ?? relation.from_table, ref: tableRefs.get(relation.from_table)! }] : []),
          ...(fieldRefs.get(`${relation.from_table}:${relation.from_field}`) ? [{ label: relation.from_field, ref: fieldRefs.get(`${relation.from_table}:${relation.from_field}`)! }] : []),
        ] },
        { label: '终点', value: `${tableNames.get(relation.to_table) ?? relation.to_table}.${relation.to_field}`, links: [
          ...(tableRefs.get(relation.to_table) ? [{ label: tableNames.get(relation.to_table) ?? relation.to_table, ref: tableRefs.get(relation.to_table)! }] : []),
          ...(fieldRefs.get(`${relation.to_table}:${relation.to_field}`) ? [{ label: relation.to_field, ref: fieldRefs.get(`${relation.to_table}:${relation.to_field}`)! }] : []),
        ] },
        { label: '基数', value: relation.cardinality },
      ],
    });
  }

  for (const dimension of document.artifacts.enrichment.dimensions) {
    const ref: ModelingObjectRef = { documentVersion: document.documentVersion, kind: 'DIMENSION', objectId: dimension.dimension_id };
    const targetRef: ModelingObjectRef | undefined = dimension.target_type === 'FIELD' && dimension.owner_id
      ? { documentVersion: document.documentVersion, kind: 'FIELD', objectId: dimension.target_id, ownerId: dimension.owner_id }
      : dimension.target_type === 'ENTITY' || dimension.target_type === 'EVENT'
        ? { documentVersion: document.documentVersion, kind: 'TABLE', objectId: dimension.target_id }
        : undefined;
    objects.push({
      ref, category: 'dimensions', visualKind: 'DIMENSION', name: dimension.name, code: dimension.dimension_id,
      definition: dimension.definition, subtitle: targetRef ? '随目标对象审核' : '未单独评审',
      evidenceIds: dimension.evidence_ids,
      reviewStatus: targetRef ? directReviewView(targetRef).reviewStatus : 'NOT_SEPARATELY_REVIEWED',
      reviewMode: targetRef ? 'TARGET' : 'NONE', ...(targetRef ? { reviewSourceRef: targetRef } : {}), childRefs: [],
      details: [{ label: '映射目标', value: targetRef ? `${tableNames.get(targetRef.ownerId ?? targetRef.objectId) ?? targetRef.ownerId ?? ''}.${targetRef.objectId}` : '待补充', links: targetRef ? [{ label: targetRef.objectId, ref: targetRef }] : [] }],
    });
  }

  for (const metric of document.artifacts.model.metrics) {
    const ref: ModelingObjectRef = { documentVersion: document.documentVersion, kind: 'METRIC', objectId: metric.metric_id };
    objects.push({
      ref, category: 'metrics', visualKind: 'METRIC', name: metric.name, code: metric.metric_id, definition: metric.definition,
      subtitle: metric.aggregation, evidenceIds: metric.evidence_ids,
      ...directReviewView(ref), reviewMode: 'DIRECT', childRefs: [],
      details: [
        { label: '所属事件', value: tableNames.get(metric.event_table) ?? metric.event_table, links: tableRefs.get(metric.event_table) ? [{ label: tableNames.get(metric.event_table) ?? metric.event_table, ref: tableRefs.get(metric.event_table)! }] : [] },
        { label: '依赖字段', value: tables.flatMap((table) => table.fields).filter((field) => metric.formula.includes(field.field_id)).map((field) => field.name).join('、') || '—', links: tables.flatMap((table) => table.fields
          .filter((field) => metric.formula.includes(field.field_id))
          .map((field) => ({ label: field.name, ref: fieldRefs.get(`${table.table_id}:${field.field_id}`)! }))) },
        { label: '聚合方式', value: metric.aggregation }, { label: '公式', value: metric.formula },
      ],
    });
  }
  return objects;
}
