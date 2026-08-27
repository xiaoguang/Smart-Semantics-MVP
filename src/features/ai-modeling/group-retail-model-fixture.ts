import type { Evidence, FixtureDocument, ReviewItem, SourceEntityTable, SourceEventTable, SourceField, SourceMetric, SourceRelation } from './types.ts';
import { omnichannelDocument } from './omnichannel-model-fixture.ts';
import { groupRetailBundleFingerprint } from './group-retail-source-fixture.ts';
import { retailEvidenceEntries, retailEvidenceSources } from '../evidence-registry/retail-evidence-registry.ts';

const evidenceMap: Record<string, string> = {
  E001: 'GR019', E002: 'GR022', E003: 'GR001', E004: 'GR013', E005: 'GR013',
  E006: 'GR013', E007: 'GR021', E008: 'GR009', E009: 'GR014', E010: 'GR001',
  E011: 'GR002', E012: 'GR018', E013: 'GR009', E014: 'GR017', E015: 'GR018',
};

const document = JSON.parse(JSON.stringify(omnichannelDocument, (_key, value) =>
  typeof value === 'string' && evidenceMap[value] ? evidenceMap[value] : value)) as FixtureDocument;

function field(code: string, name: string, role: string, dataType = 'string', evidenceId = 'GR001'): SourceField {
  return {
    field_id: code, name, description: `${name}用于记录${name}的业务值。`, data_type: dataType,
    semantic_type: role === 'measure' ? 'NUMBER' : role === 'event_time' ? 'DATETIME' : role === 'business_key' ? 'CODE' : 'TEXT',
    unit: role === 'measure' ? '次' : null, format_pattern: null, nullable: role !== 'business_key', field_role: role,
    is_filterable: role !== 'measure', is_groupable: role === 'attribute' || role === 'business_key', is_display: true,
    is_sortable: role === 'event_time' || role === 'measure', provenance_type: 'document_explicit', evidence_ids: [evidenceId],
    inference_reason: null, unresolved_item_id: null, references: null,
  };
}

const store = document.artifacts.model.entity_tables.find((item) => item.table_id === 'entity_store')!;
store.fields.push(field('operating_unit_code', '经营组织编码', 'foreign_key', 'string', 'GR001'));

const operatingUnit: SourceEntityTable = {
  table_id: 'entity_operating_unit', source_candidate_id: 'candidate_entity_operating_unit', name: '经营组织',
  definition: '经营组织用于统一集团、事业部和区域公司的经营责任归属。', entity_kind: 'MASTER',
  business_key: ['operating_unit_code'], evidence_ids: ['GR019', 'GR001'],
  fields: [
    field('operating_unit_code', '经营组织编码', 'business_key'),
    field('operating_unit_name', '经营组织名称', 'attribute', 'string', 'GR019'),
    field('operating_unit_level', '经营组织级别', 'attribute', 'string', 'GR019'),
    field('parent_operating_unit_code', '上级经营组织编码', 'foreign_key'),
  ],
};
const searchConversion: SourceEventTable = {
  table_id: 'event_search_conversion', source_candidate_id: 'candidate_event_search_conversion', name: '搜索转化',
  definition: '搜索转化记录一次搜索会话从曝光到购买转化的过程。', grain: '每个搜索会话与商品的转化记录',
  factless: false, measure_ids: ['impression_count', 'conversion_count'], evidence_ids: ['GR009', 'GR024'],
  fields: [
    field('search_event_code', '搜索事件编码', 'business_key', 'string', 'GR024'),
    field('product_code', '商品编码', 'foreign_key', 'string', 'GR007'),
    field('channel_code', '销售渠道编码', 'foreign_key', 'string', 'GR024'),
    field('search_keyword', '搜索词', 'attribute', 'string', 'GR007'),
    field('event_time', '搜索发生时间', 'event_time', 'timestamp', 'GR024'),
    field('impression_count', '曝光次数', 'measure', 'integer', 'GR024'),
    field('conversion_count', '转化次数', 'measure', 'integer', 'GR024'),
  ],
};
document.artifacts.model.entity_tables.push(operatingUnit);
document.artifacts.model.event_tables.push(searchConversion);

const relation = (id: string, name: string, fromTable: string, fromField: string, toTable: string, toField: string, evidenceIds: string[]): SourceRelation => ({
  relationship_id: id, name, relationship_kind: 'REFERENCE', from_table: fromTable, from_field: fromField,
  to_table: toTable, to_field: toField, cardinality: 'MANY_TO_ONE', evidence_ids: evidenceIds,
  provenance_type: 'document_inferred', inference_reason: '根据结构字段、代码关联和事件样例共同确认。',
});
document.artifacts.model.relationships.push(
  relation('rel_store_operating_unit', '门店所属经营组织', 'entity_store', 'operating_unit_code', 'entity_operating_unit', 'operating_unit_code', ['GR001', 'GR019']),
  relation('rel_search_product', '搜索商品', 'event_search_conversion', 'product_code', 'entity_product', 'product_code', ['GR007', 'GR024']),
);

const metric = (id: string, name: string, formula: string, aggregation: string, fields: string[]): SourceMetric => ({
  metric_id: id, name, definition: `${name}用于评价集团零售搜索经营效果。`, event_table: 'event_search_conversion',
  measure_fields: fields, aggregation, formula, numerator_field: null, denominator_field: null,
  semi_additive_over_field: null, evidence_ids: ['GR009', 'GR024'],
});
document.artifacts.model.metrics.push(
  metric('metric_search_conversion_rate', '搜索转化率', 'SUM(conversion_count)/SUM(impression_count)', 'ratio', ['conversion_count', 'impression_count']),
  metric('metric_search_session_count', '搜索会话数', 'COUNT_DISTINCT(search_event_code)', 'count_distinct', ['search_event_code']),
);

document.artifacts.enrichment.dimensions.push(
  {
    dimension_id: 'dim_operating_unit', name: '经营组织', definition: '按集团经营责任组织分析经营结果。',
    target_type: 'FIELD', target_id: 'operating_unit_code', owner_id: 'entity_operating_unit', evidence_ids: ['GR019', 'GR001'], confidence: 0.97,
  },
  {
    dimension_id: 'dim_search_keyword', name: '搜索词', definition: '按用户输入的搜索词分析曝光和转化。',
    target_type: 'FIELD', target_id: 'search_keyword', owner_id: 'event_search_conversion', evidence_ids: ['GR007'], confidence: 0.79,
  },
);
document.artifacts.enrichment.rule_candidates.push({
  rule_id: 'rule_search_bot_exclusion', name: '搜索转化排除机器人流量', definition: '搜索转化指标需要排除已识别的机器人和内部压测流量。',
  target_type: 'EVENT', target_id: 'event_search_conversion', owner_id: null, evidence_ids: ['GR009', 'GR024'], confidence: 0.86,
});
document.artifacts.enrichment.member_hierarchies.push({
  hierarchy_id: 'hierarchy_product_category_members', name: '商品分类成员层级', owner_table_id: 'entity_product', attribute_id: 'category_code',
  levels: [
    { level_id: 'division', name: '事业群', depth: 1, parent_level_id: null },
    { level_id: 'category', name: '大类', depth: 2, parent_level_id: 'division' },
    { level_id: 'subcategory', name: '中类', depth: 3, parent_level_id: 'category' },
    { level_id: 'leaf', name: '小类', depth: 4, parent_level_id: 'subcategory' },
  ],
  members: [
    { member_id: 'food', name: '食品事业群', code: 'FOOD', level_id: 'division', parent_member_id: null },
    { member_id: 'drink', name: '饮料', code: 'DRINK', level_id: 'category', parent_member_id: 'food' },
    { member_id: 'tea', name: '茶饮', code: 'TEA', level_id: 'subcategory', parent_member_id: 'drink' },
    { member_id: 'green-tea', name: '绿茶', code: 'GREEN_TEA', level_id: 'leaf', parent_member_id: 'tea' },
    { member_id: 'home', name: '家居事业群', code: 'HOME', level_id: 'division', parent_member_id: null },
    { member_id: 'cleaning', name: '清洁用品', code: 'CLEANING', level_id: 'category', parent_member_id: 'home' },
  ],
  evidence_ids: ['GR010', 'GR014'], confidence: 0.84,
});

const allTables = [...document.artifacts.model.entity_tables, ...document.artifacts.model.event_tables];
const addedSynonymTargets = [
  { type: 'ENTITY' as const, id: operatingUnit.table_id, owner: null, name: operatingUnit.name },
  { type: 'EVENT' as const, id: searchConversion.table_id, owner: null, name: searchConversion.name },
  ...operatingUnit.fields.map((item) => ({ type: 'FIELD' as const, id: item.field_id, owner: operatingUnit.table_id, name: item.name })),
  ...searchConversion.fields.filter((item) => item.field_role !== 'foreign_key').map((item) => ({ type: 'FIELD' as const, id: item.field_id, owner: searchConversion.table_id, name: item.name })),
  ...document.artifacts.enrichment.dimensions.slice(-2).map((item) => ({ type: 'DIMENSION' as const, id: item.dimension_id, owner: null, name: item.name })),
  ...document.artifacts.model.metrics.slice(-2).map((item) => ({ type: 'METRIC' as const, id: item.metric_id, owner: null, name: item.name })),
  { type: 'RULE' as const, id: 'rule_search_bot_exclusion', owner: null, name: '搜索转化排除机器人流量' },
];
document.artifacts.enrichment.synonym_groups.push(...addedSynonymTargets.map((target, index) => ({
  group_id: `group_retail_synonym_${index + 1}`, target_type: target.type, target_id: target.id, owner_id: target.owner,
  aliases: [{ text: `${target.name}业务称呼`, confidence: 0.96, evidence_ids: ['GR011', 'GR021'] }],
})));
document.artifacts.enrichment.synonym_groups.push({
  group_id: 'group_retail_synonym_business_day', target_type: 'RULE', target_id: 'rule_business_day', owner_id: null,
  aliases: [{ text: '经营日口径', confidence: 0.96, evidence_ids: ['GR019'] }],
});
const netSalesAliases = document.artifacts.enrichment.synonym_groups.find((group) => group.group_id === 'synonym_73');
if (!netSalesAliases || netSalesAliases.aliases.length !== 2) throw new Error('净销售额同义词组缺失');
netSalesAliases.aliases = [
  { text: '实收销售额', confidence: 0.98, evidence_ids: ['GR009'] },
  { text: '销售净额', confidence: 0.96, evidence_ids: ['GR009'] },
];

export const groupRetailEvidence: Evidence[] = retailEvidenceEntries.map((entry) => {
  const source = retailEvidenceSources.find((item) => item.connectionId === entry.connectionId);
  if (!source) throw new Error(`集团零售证据 ${entry.evidenceId} 缺少注册来源`);
  return {
    evidence_id: entry.evidenceId,
    source_file: source.displayName,
    section: entry.section,
    quote: entry.statement,
  };
});

const reviewObject = (input: Omit<ReviewItem, 'groupId'>): ReviewItem => ({ ...input, groupId: '' });
const reviewedDimensions = document.artifacts.enrichment.dimensions.filter((item) => !['dim_store', 'dim_sales_channel'].includes(item.dimension_id));
const reviewObjects: ReviewItem[] = [
  ...document.artifacts.model.entity_tables.flatMap((table) => [
    reviewObject({ id: `ENTITY:${table.table_id}`, type: 'ENTITY', objectId: table.table_id, name: table.name, target: table.table_id, disposition: 'INDEPENDENT', reason: table.definition }),
    ...table.fields.map((item) => reviewObject({ id: `FIELD:${table.table_id}:${item.field_id}`, type: 'FIELD', objectId: item.field_id, name: item.name, target: `${table.name}.${item.name}`, disposition: 'GENERATED', reason: item.description })),
  ]),
  ...document.artifacts.model.event_tables.flatMap((table) => [
    reviewObject({ id: `EVENT:${table.table_id}`, type: 'EVENT', objectId: table.table_id, name: table.name, target: table.table_id, disposition: 'INDEPENDENT', reason: table.definition }),
    ...table.fields.map((item) => reviewObject({ id: `FIELD:${table.table_id}:${item.field_id}`, type: 'FIELD', objectId: item.field_id, name: item.name, target: `${table.name}.${item.name}`, disposition: 'GENERATED', reason: item.description })),
  ]),
  ...document.artifacts.model.relationships.map((item) => reviewObject({ id: `RELATION:${item.relationship_id}`, type: 'RELATION', objectId: item.relationship_id, name: item.name, target: `${item.from_table} → ${item.to_table}`, disposition: 'GENERATED', reason: item.inference_reason ?? '' })),
  ...document.artifacts.model.metrics.map((item) => reviewObject({ id: `METRIC:${item.metric_id}`, type: 'METRIC', objectId: item.metric_id, name: item.name, target: item.metric_id, disposition: 'GENERATED', reason: item.definition })),
  ...reviewedDimensions.map((item) => reviewObject({ id: `DIMENSION:${item.dimension_id}`, type: 'DIMENSION', objectId: item.dimension_id, name: item.name, target: item.target_id, disposition: 'MAPPED_FIELD', reason: item.definition })),
];

const conflictIds = new Set(['METRIC:metric_net_sales', 'DIMENSION:dim_customer_tier', 'FIELD:event_sales_order_line:order_status']);
const weakIds = new Set([
  'METRIC:metric_search_conversion_rate', 'DIMENSION:dim_product_category', 'DIMENSION:dim_region',
  'FIELD:event_search_conversion:search_keyword', 'METRIC:metric_stockout_rate', 'FIELD:entity_customer:customer_tier',
]);
const generatedIds = new Set(document.artifacts.model.relationships.map((item) => `RELATION:${item.relationship_id}`));
const conflicts = reviewObjects.filter((item) => conflictIds.has(item.id));
const weak = reviewObjects.filter((item) => weakIds.has(item.id));
const generated = reviewObjects.filter((item) => generatedIds.has(item.id));
const manualIds = new Set([...conflicts, ...weak, ...generated].map((item) => item.id));
const automatic = reviewObjects.filter((item) => !manualIds.has(item.id));
if (allTables.flatMap((item) => item.fields).length !== 84 || reviewObjects.length !== 132
  || automatic.length !== 108 || conflicts.length !== 3 || weak.length !== 6 || generated.length !== 15) {
  throw new Error('集团零售模型或审核分类计数不一致');
}
const classify = (items: ReviewItem[], groupId: string, reviewClass: NonNullable<ReviewItem['reviewClass']>, evidenceIds: string[]) => items.map((item) => ({
  ...item, groupId, reviewClass, evidenceIds,
  confidence: reviewClass === 'AUTO' ? 0.97 : reviewClass === 'WEAK_EVIDENCE' ? 0.79 : 0.9,
}));
const reviewItems = [
  ...classify(automatic, 'auto', 'AUTO', ['GR001', 'GR019']),
  ...classify(conflicts, 'conflicts', 'CONFLICT', ['GR018', 'GR019', 'GR022']),
  ...classify(weak, 'weak', 'WEAK_EVIDENCE', ['GR009']),
  ...classify(generated, 'generated', 'SYSTEM_GENERATED', ['GR001', 'GR016']),
];

document.documentVersion = 'v1';
document.modelVersion = 'V1';
document.status = 'PUBLISHED';
document.fileName = '集团零售多存储证据批次-v1';
document.title = '集团零售多存储证据批次';
document.summary = '整合八类存储与内容来源，交叉验证后形成集团零售经营语义模型。';
document.content = '本批次由MySQL、MongoDB、Elasticsearch、Semantica RDF、MinIO、GitHub、SharePoint和Kafka八个固定快照组成。';
document.sha256 = groupRetailBundleFingerprint;
document.qualityGate = { publishable: true, blockers: [], warnings: ['三项来源冲突必须在生成模型前完成决定。'] };
document.artifacts.analysis.business_summary = {
  system_name: '集团零售经营语义模型', business_goal: '统一集团零售交易、客户、搜索和经营分析口径',
  business_scope: ['销售', '库存', '退货', '促销', '客户运营', '搜索转化'],
};
document.artifacts.analysis.entity_candidates = document.artifacts.model.entity_tables.map((item, index) => ({ candidate_id: `ENT${index + 1}`, name: item.name, definition: item.definition, evidence_ids: item.evidence_ids, confidence: 0.96 }));
document.artifacts.analysis.event_candidates = document.artifacts.model.event_tables.map((item, index) => ({ candidate_id: `EVT${index + 1}`, name: item.name, definition: item.definition, evidence_ids: item.evidence_ids, confidence: 0.96 }));
document.artifacts.analysis.dimension_candidates = document.artifacts.enrichment.dimensions.map((item, index) => ({ candidate_id: `DIM${index + 1}`, name: item.name, definition: item.definition, evidence_ids: item.evidence_ids, confidence: item.confidence }));
document.artifacts.analysis.metric_candidates = document.artifacts.model.metrics.map((item, index) => ({ candidate_id: `MET${index + 1}`, name: item.name, definition: item.definition, evidence_ids: item.evidence_ids, confidence: 0.95 }));
document.artifacts.analysis.grain_candidates = document.artifacts.model.event_tables.map((item, index) => ({ candidate_id: `GRN${index + 1}`, name: item.grain, definition: item.grain, evidence_ids: item.evidence_ids, confidence: 0.95 }));
document.artifacts.analysis.hierarchy_candidates = document.artifacts.enrichment.member_hierarchies.map((item, index) => ({ candidate_id: `HIE${index + 1}`, name: item.name, definition: item.levels.map((level) => level.name).join(' → '), evidence_ids: item.evidence_ids, confidence: item.confidence }));
document.artifacts.analysis.evidence_catalog = groupRetailEvidence;
document.artifacts.model.evidence_catalog = groupRetailEvidence;
document.artifacts.enrichment.metadata = { source: 'deterministic_multi_storage_fixture', sourceCount: 8 };
document.artifacts.report = '集团零售经营模型由八类来源完成独立读取、血缘识别和交叉验证后生成。';
document.generationNotes = reviewObjects.slice(0, 24).map((item, index) => ({ id: `GROUP_NOTE${index + 1}`, kind: item.type, sourceId: item.id, sourceName: item.name, disposition: item.disposition, target: item.target, reason: item.reason }));
document.review = {
  groups: [
    { id: 'auto', name: '系统预审', itemIds: automatic.map((item) => item.id) },
    { id: 'conflicts', name: '来源冲突', itemIds: conflicts.map((item) => item.id) },
    { id: 'weak', name: '弱依据', itemIds: weak.map((item) => item.id) },
    { id: 'generated', name: '系统生成', itemIds: generated.map((item) => item.id) },
  ],
  items: reviewItems,
};
document.adjacentDiff = { added: 132, modified: 0, removed: 0, inherited: 0 };
document.artifactManifest = { sourceCount: 8, evidenceCount: groupRetailEvidence.length, reviewCount: 132 };

export const groupRetailDocument = document;
