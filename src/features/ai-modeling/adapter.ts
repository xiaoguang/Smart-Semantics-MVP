import { semanticFixture } from './fixture.ts';
import type {
  DocumentVersion, FixtureDocument, LinguanAlias, LinguanDimension, LinguanField, LinguanMemberHierarchy,
  LinguanMetric, LinguanModelSnapshot, LinguanRelation, LinguanTable, ModelVersion, RuleCandidate, SourceField,
} from './types.ts';

function stableId(key: string) {
  let hash = 2166136261;
  for (let index = 0; index < key.length; index += 1) {
    hash ^= key.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return Math.abs(hash) || 1;
}

function fieldView(field: SourceField, tableCode: string, tableName: string, tableKind: 'ENTITY' | 'EVENT'): LinguanField {
  return {
    id: stableId(`FIELD:${tableCode}:${field.field_id}`),
    code: field.field_id,
    name: field.name,
    description: field.description,
    ownerTableCode: tableCode,
    ownerTableName: tableName,
    ownerTableKind: tableKind,
    dataType: field.data_type,
    semanticType: field.semantic_type,
    role: field.field_role,
    unit: field.unit,
    filterable: field.is_filterable,
    groupable: field.is_groupable,
    evidenceIds: field.evidence_ids,
    inferenceReason: field.inference_reason,
  };
}

function tableViews(document: FixtureDocument) {
  const entities = document.artifacts.model.entity_tables.map<LinguanTable>((table) => ({
    id: stableId(`ENTITY:${table.table_id}`), code: table.table_id, name: table.name, kind: 'ENTITY',
    description: table.definition, businessKeys: table.business_key, grain: null,
    fields: table.fields.map((field) => fieldView(field, table.table_id, table.name, 'ENTITY')),
    evidenceIds: table.evidence_ids,
  }));
  const events = document.artifacts.model.event_tables.map<LinguanTable>((table) => ({
    id: stableId(`EVENT:${table.table_id}`), code: table.table_id, name: table.name, kind: 'EVENT',
    description: table.definition, businessKeys: [], grain: table.grain,
    fields: table.fields.map((field) => fieldView(field, table.table_id, table.name, 'EVENT')),
    evidenceIds: table.evidence_ids,
  }));
  return { entities, events, tables: [...entities, ...events] };
}

function relationViews(document: FixtureDocument, tables: LinguanTable[]): LinguanRelation[] {
  const byCode = new Map(tables.map((table) => [table.code, table]));
  return document.artifacts.model.relationships.map((relation) => {
    const fromTable = byCode.get(relation.from_table);
    const toTable = byCode.get(relation.to_table);
    const fromField = fromTable?.fields.find((field) => field.code === relation.from_field);
    const toField = toTable?.fields.find((field) => field.code === relation.to_field);
    if (!fromTable || !toTable || !fromField || !toField) throw new Error(`关系端点无法解析：${relation.relationship_id}`);
    return {
      id: stableId(`RELATION:${relation.relationship_id}`), code: relation.relationship_id,
      name: relation.name, kind: relation.relationship_kind, cardinality: relation.cardinality,
      from: { tableCode: fromTable.code, tableName: fromTable.name, fieldCode: fromField.code, fieldName: fromField.name },
      to: { tableCode: toTable.code, tableName: toTable.name, fieldCode: toField.code, fieldName: toField.name },
      evidenceIds: relation.evidence_ids,
    };
  });
}

function dimensionViews(document: FixtureDocument, tables: LinguanTable[]): LinguanDimension[] {
  return document.artifacts.enrichment.dimensions.map((dimension) => {
    const owner = dimension.owner_id ? tables.find((table) => table.code === dimension.owner_id) : undefined;
    const field = owner?.fields.find((item) => item.code === dimension.target_id);
    return {
      id: dimension.dimension_id, name: dimension.name, description: dimension.definition,
      disposition: dimension.target_type === 'FIELD' ? 'MAPPED_FIELD' : 'MAPPED_ENTITY',
      target: field ? `${owner?.name}.${field.name}` : owner?.name ?? dimension.target_id,
      targetTableCode: owner?.code, targetFieldCode: field?.code, evidenceIds: dimension.evidence_ids,
    };
  });
}

function metricViews(document: FixtureDocument, tables: LinguanTable[]): LinguanMetric[] {
  const eventMap = new Map(tables.filter((table) => table.kind === 'EVENT').map((table) => [table.code, table]));
  return document.artifacts.model.metrics.map((metric) => {
    const event = eventMap.get(metric.event_table);
    if (!event) throw new Error(`指标事件表无法解析：${metric.metric_id}`);
    const units = [...new Set(metric.measure_fields.map((code) => event.fields.find((field) => field.code === code)?.unit).filter(Boolean))];
    const simpleFormula = /^(SUM|AVG)\([^(),+\-*/]+\)$/i.test(metric.formula.replaceAll(' ', ''));
    const editable = ['sum', 'avg'].includes(metric.aggregation) && metric.measure_fields.length === 1 && simpleFormula;
    return {
      id: stableId(`METRIC:${metric.metric_id}`), code: metric.metric_id, name: metric.name,
      description: metric.definition, eventTableCode: event.code, eventTableName: event.name,
      measureFieldCodes: metric.measure_fields, aggregation: metric.aggregation, formula: metric.formula,
      unit: units.length === 1 ? units[0] ?? null : null, numeratorField: metric.numerator_field,
      denominatorField: metric.denominator_field, semiAdditiveOverField: metric.semi_additive_over_field,
      evidenceIds: metric.evidence_ids, editability: editable ? 'EDITABLE' : 'READ_ONLY',
      compatibilityReason: editable ? '可使用现有单字段聚合编辑器' : '保留真实公式只读展示，避免错误降级',
    };
  });
}

function ruleCandidateViews(document: FixtureDocument): RuleCandidate[] {
  return document.artifacts.enrichment.rule_candidates.map((rule) => ({
    id: rule.rule_id, name: rule.name, sourceType: 'SEMANTIC_ENRICHMENT', description: rule.definition,
    target: rule.owner_id ? `${rule.owner_id}.${rule.target_id}` : rule.target_id,
    targetType: rule.target_type, targetId: rule.target_id, ownerId: rule.owner_id,
    evidenceIds: rule.evidence_ids, confidence: rule.confidence, compatibility: 'NEEDS_STRUCTURE',
  }));
}

function hierarchyViews(document: FixtureDocument): LinguanMemberHierarchy[] {
  return document.artifacts.enrichment.member_hierarchies.map((hierarchy) => ({
    id: hierarchy.hierarchy_id, name: hierarchy.name, ownerTableCode: hierarchy.owner_table_id,
    attributeCode: hierarchy.attribute_id, levels: hierarchy.levels, members: hierarchy.members,
    evidenceIds: hierarchy.evidence_ids, confidence: hierarchy.confidence,
  }));
}

function aliasViews(document: FixtureDocument): LinguanAlias[] {
  return document.artifacts.enrichment.synonym_groups.flatMap((group) => group.aliases.map((alias, index) => ({
    id: `${group.group_id}:${index + 1}`, text: alias.text, targetType: group.target_type,
    targetId: group.target_id, ownerId: group.owner_id, confidence: alias.confidence, evidenceIds: alias.evidence_ids,
  })));
}

export function adaptFixtureDocument(
  fixture: { system: { code: string; name: string }; documents: FixtureDocument[] },
  documentVersion: DocumentVersion,
  modelVersion: ModelVersion,
): LinguanModelSnapshot {
  const document = fixture.documents.find((item) => item.documentVersion === documentVersion);
  if (!document || document.modelVersion !== modelVersion || !document.qualityGate.publishable) {
    throw new Error(`资料 ${documentVersion} 与模型 ${modelVersion} 不匹配`);
  }
  const { entities, events, tables } = tableViews(document);
  const relations = relationViews(document, tables);
  const dimensions = dimensionViews(document, tables);
  const metrics = metricViews(document, tables);
  return {
    systemCode: fixture.system.code, systemName: fixture.system.name,
    documentVersion, modelVersion, projectionRevision: 2, sourceSha256: document.sha256, immutableSource: true, readOnly: false,
    tables, entities, events, fields: tables.flatMap((table) => table.fields), relations, dimensions, metrics,
    executableRules: [], ruleCandidates: ruleCandidateViews(document),
    hierarchies: hierarchyViews(document), aliases: aliasViews(document), evidence: document.artifacts.analysis.evidence_catalog,
    generationNotes: document.generationNotes,
    compatibilityNotes: [
      '数据源与 Schema 尚未配置。',
      '成员层级与同义词来自设计资料，并保留来源依据。',
      '规则仍作为待结构化候选，不进入可执行规则列表。',
      '复杂指标保留原始公式，只读展示。',
    ],
  };
}

export function adaptPublishedModel(
  systemCode: string,
  documentVersion: Exclude<DocumentVersion, 'v3'>,
  modelVersion: ModelVersion,
): LinguanModelSnapshot {
  if (systemCode !== semanticFixture.system.code) throw new Error(`系统不存在：${systemCode}`);
  return adaptFixtureDocument(semanticFixture, documentVersion, modelVersion);
}
