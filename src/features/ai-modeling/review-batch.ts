import type {
  AnalysisCandidate, DocumentVersion, FixtureDocument, ReviewItem, SemanticFixture,
} from './types.ts';
import { modelingRefForReviewItem, type ModelingObjectRef } from './modeling-object-projection.ts';

export type ReviewChange = 'INHERITED' | 'MODIFIED' | 'ADDED';
export type ReviewBasis = 'EXPLICIT' | 'INFERRED' | 'TECHNICAL' | 'ATTENTION';

export type ReviewItemGuidance = {
  itemId: string;
  name: string;
  type: string;
  change: ReviewChange;
  recommendation: 'ADOPT' | 'VERIFY';
  basis: ReviewBasis;
  reason: string;
  target: string;
  affectedRefs: string[];
  evidenceCount: number;
  confidence?: number;
  targetRef?: ModelingObjectRef;
};

export type ReviewGroupView = {
  id: string;
  name: string;
  purpose: string;
  totalItems: number;
  typeCounts: Record<string, number>;
  changes: Record<ReviewChange, number>;
  items: ReviewItemGuidance[];
};

export type ReviewBatchView = {
  batchKey: string;
  systemName: string;
  documentVersion: DocumentVersion;
  targetModelVersion: string | null;
  sourceFile: string;
  totalItems: number;
  groupCount: number;
  changes: { inherited: number; modified: number; added: number; removedFromPrevious: number };
  groups: ReviewGroupView[];
};

export function isAutoReviewEligible(item: ReviewItemGuidance) {
  return item.recommendation === 'ADOPT'
    && item.basis === 'EXPLICIT'
    && item.evidenceCount > 0
    && item.confidence !== undefined
    && item.confidence >= 0.95;
}

const groupPurposes: Record<string, string> = {
  auto: '这些对象已由多份资料一致支持，且置信度达到系统预审标准。',
  conflicts: '这些口径在不同来源中存在实质差异，需要先明确采用哪一种。',
  weak: '这些结论目前只有单一来源或辅助图片支持，需要业务人员确认。',
  generated: '这些对象由键、关联或公式推导生成，需要确认生成结果符合实际。',
  shared: '商品、仓库、客户等会被多个业务流程共同使用；这里确认它们只有一套一致定义。',
  inventory: '确认库存数量如何形成，以及入库、出库和库存调整如何记录。',
  fulfillment: '确认订单从创建到完成的各个阶段，以及每个订单商品如何履约。',
  shipping: '确认商品如何发出、是否拆分发货，以及是否按时完成发货。',
  repair: '确认维修工单如何处理，以及响应和完成时间如何计算。',
};

function comparable(item: ReviewItem) {
  return JSON.stringify({
    type: item.type,
    objectId: item.objectId,
    name: item.name,
    target: item.target,
    disposition: item.disposition,
    reason: item.reason,
  });
}

function evidenceFor(document: FixtureDocument, item: ReviewItem) {
  if (item.evidenceIds) return item.evidenceIds;
  const model = document.artifacts.model;
  if (item.type === 'ENTITY') return model.entity_tables.find((value) => value.table_id === item.objectId)?.evidence_ids ?? [];
  if (item.type === 'EVENT') return model.event_tables.find((value) => value.table_id === item.objectId)?.evidence_ids ?? [];
  if (item.type === 'FIELD') {
    const ownerId = item.id.split(':')[1];
    const table = [...model.entity_tables, ...model.event_tables].find((value) => value.table_id === ownerId);
    return table?.fields.find((value) => value.field_id === item.objectId)?.evidence_ids ?? [];
  }
  if (item.type === 'RELATION') return model.relationships.find((value) => value.relationship_id === item.objectId)?.evidence_ids ?? [];
  if (item.type === 'METRIC') return model.metrics.find((value) => value.metric_id === item.objectId)?.evidence_ids ?? [];
  return [];
}

function provenanceFor(document: FixtureDocument, item: ReviewItem) {
  const model = document.artifacts.model;
  if (item.type === 'FIELD') {
    const ownerId = item.id.split(':')[1];
    const table = [...model.entity_tables, ...model.event_tables].find((value) => value.table_id === ownerId);
    return table?.fields.find((value) => value.field_id === item.objectId)?.provenance_type;
  }
  if (item.type === 'RELATION') return model.relationships.find((value) => value.relationship_id === item.objectId)?.provenance_type;
  return undefined;
}

function exactCandidateConfidence(document: FixtureDocument, item: ReviewItem) {
  const resolutions = document.artifacts.model.candidate_resolutions as Array<{
    candidate_id?: string; target_table_id?: string; target_field_ids?: string[];
  }>;
  const candidates: AnalysisCandidate[] = [
    ...document.artifacts.analysis.entity_candidates,
    ...document.artifacts.analysis.event_candidates,
    ...document.artifacts.analysis.dimension_candidates,
    ...document.artifacts.analysis.metric_candidates,
  ];
  let candidateId: string | undefined;
  if (item.type === 'ENTITY' || item.type === 'EVENT') {
    candidateId = resolutions.find((value) => value.target_table_id === item.objectId)?.candidate_id;
  } else if (item.type === 'FIELD') {
    const ownerId = item.id.split(':')[1];
    candidateId = resolutions.find((value) => value.target_table_id === ownerId
      && value.target_field_ids?.includes(item.objectId))?.candidate_id;
  }
  return candidateId ? candidates.find((value) => value.candidate_id === candidateId)?.confidence : undefined;
}

function affectedRefs(document: FixtureDocument, item: ReviewItem) {
  const refs = new Set<string>();
  const model = document.artifacts.model;
  for (const relation of model.relationships) {
    if ([relation.from_table, relation.to_table, relation.from_field, relation.to_field].includes(item.objectId)) {
      refs.add(`关系：${relation.name}`);
    }
  }
  for (const metric of model.metrics) {
    if (metric.event_table === item.objectId || metric.measure_fields.includes(item.objectId)
      || metric.numerator_field === item.objectId || metric.denominator_field === item.objectId) {
      refs.add(`指标：${metric.name}`);
    }
  }
  return [...refs];
}

function guidance(document: FixtureDocument, previous: Map<string, ReviewItem>, item: ReviewItem): ReviewItemGuidance {
  const prior = previous.get(item.id);
  const change: ReviewChange = !prior ? 'ADDED' : comparable(prior) === comparable(item) ? 'INHERITED' : 'MODIFIED';
  const evidenceCount = evidenceFor(document, item).length;
  const provenance = provenanceFor(document, item);
  const technical = /技术|代理键|外键/.test(item.reason);
  const inferred = provenance === 'document_inferred' || /推断/.test(item.reason);
  const basis: ReviewBasis = item.reviewClass === 'CONFLICT' || item.reviewClass === 'WEAK_EVIDENCE' ? 'ATTENTION'
    : item.reviewClass === 'SYSTEM_GENERATED' ? 'TECHNICAL'
      : item.reviewClass === 'AUTO' ? 'EXPLICIT'
        : evidenceCount === 0 ? 'ATTENTION' : inferred ? 'INFERRED' : technical ? 'TECHNICAL' : 'EXPLICIT';
  const confidence = item.confidence ?? exactCandidateConfidence(document, item);
  return {
    itemId: item.id,
    name: item.name,
    type: item.type,
    change,
    recommendation: basis === 'INFERRED' || basis === 'ATTENTION' ? 'VERIFY' : 'ADOPT',
    basis,
    reason: item.reason,
    target: item.target,
    affectedRefs: affectedRefs(document, item),
    evidenceCount,
    ...(confidence === undefined ? {} : { confidence }),
    ...(modelingRefForReviewItem(document.documentVersion, item)
      ? { targetRef: modelingRefForReviewItem(document.documentVersion, item) }
      : {}),
  };
}

export function projectReviewBatch(fixture: SemanticFixture, documentVersion: DocumentVersion): ReviewBatchView {
  const index = fixture.documents.findIndex((item) => item.documentVersion === documentVersion);
  if (index < 0) throw new Error(`资料版本不存在：${documentVersion}`);
  const document = fixture.documents[index];
  const previousItems = new Map((fixture.documents[index - 1]?.review.items ?? []).map((item) => [item.id, item]));
  const currentIds = new Set(document.review.items.map((item) => item.id));
  const items = document.review.items.map((item) => guidance(document, previousItems, item));
  const counts = {
    inherited: items.filter((item) => item.change === 'INHERITED').length,
    modified: items.filter((item) => item.change === 'MODIFIED').length,
    added: items.filter((item) => item.change === 'ADDED').length,
    removedFromPrevious: [...previousItems.keys()].filter((id) => !currentIds.has(id)).length,
  };
  return {
    batchKey: `${fixture.system.code}:${documentVersion}`,
    systemName: fixture.system.name,
    documentVersion,
    targetModelVersion: document.modelVersion,
    sourceFile: document.fileName,
    totalItems: items.length,
    groupCount: document.review.groups.length,
    changes: counts,
    groups: document.review.groups.map((group) => {
      const groupItems = items.filter((item) => group.itemIds.includes(item.itemId));
      const typeCounts = Object.fromEntries([...new Set(groupItems.map((item) => item.type))]
        .map((type) => [type, groupItems.filter((item) => item.type === type).length]));
      return {
        id: group.id,
        name: group.name,
        purpose: groupPurposes[group.id] ?? '确认这些业务对象的含义、归属和相互关系是否符合实际。',
        totalItems: groupItems.length,
        typeCounts,
        changes: {
          INHERITED: groupItems.filter((item) => item.change === 'INHERITED').length,
          MODIFIED: groupItems.filter((item) => item.change === 'MODIFIED').length,
          ADDED: groupItems.filter((item) => item.change === 'ADDED').length,
        },
        items: groupItems,
      };
    }),
  };
}
