import type {
  Evidence, FixtureDocument, ReviewItem, SourceEntityTable, SourceEventTable, SourceField,
  SourceMetric, SourceRelation,
} from './types.ts';
import { computeSourceBundleFingerprint } from './source-bundle.ts';
import { omnichannelSourceAssets } from './omnichannel-source-fixture.ts';

const evidenceIds = Array.from({ length: 15 }, (_, index) => `E${String(index + 1).padStart(3, '0')}`);
export const omnichannelEvidence: Evidence[] = evidenceIds.map((id, index) => ({
  evidence_id: id,
  source_file: omnichannelSourceAssets[index < 9 ? Math.min(index, 4) : index < 12 ? 5 : index < 14 ? 6 : 7].displayName,
  section: index < 3 ? '业务对象与流程' : index < 7 ? '指标与术语' : index < 9 ? '辅助图示' : index < 12 ? '物理结构' : '实际使用',
  quote: [
    '商品、门店、客户、行政区域、促销活动和销售渠道是经营分析的共享对象。',
    '销售订单行、库存移动、退货处理和促销核销按最细业务行为记录。',
    '门店归属行政区域，销售行为关联商品、门店、客户、渠道和促销活动。',
    '销售金额按有效销售订单行金额求和，净销售额扣减已完成退款。',
    '当前库存量按商品和门店读取最新库存快照，不跨快照时间累加。',
    '退货率使用退货数量除以销售数量，促销核销率使用核销数量除以发放数量。',
    '业务术语为每个可见语义对象提供可追溯的同义表达。',
    '门店经营看板按门店、渠道、品类、日期展示销售和库存经营结果。',
    '行政区域按照大区、省、市、区县维护成员父子关系。',
    'retail_core 的业务表定义主键、外键、字段类型和非空约束。',
    '库存移动表通过商品、门店和业务日期字段关联主数据。',
    '经营分析查询使用已完成退款口径计算净销售额。',
    '促销核销查询以活动、商品、门店和渠道进行聚合。',
    '订单状态枚举保留 CLOSED，并与 CANCELED 区分。',
    '净销售额规则以已完成退款为扣减条件。',
  ][index],
}));

function sourceField(code: string, name: string, role: string, dataType = 'string', evidence = 'E010'): SourceField {
  const technical = /(_key|_id)$/.test(code) && role !== 'business_key';
  return {
    field_id: code, name, description: `${name}用于记录${name}的业务值。`, data_type: dataType,
    semantic_type: role === 'measure' ? 'NUMBER' : role === 'event_time' ? 'DATETIME' : role === 'business_key' ? 'CODE' : 'TEXT',
    unit: role === 'measure' ? (/amount|profit|cost/.test(code) ? '元' : /rate/.test(code) ? '%' : '件') : null,
    format_pattern: null, nullable: role !== 'business_key', field_role: role,
    is_filterable: role !== 'measure', is_groupable: role === 'attribute' || role === 'business_key',
    is_display: !technical, is_sortable: role === 'event_time' || role === 'measure',
    provenance_type: technical ? 'technical_generated' : 'document_explicit', evidence_ids: [evidence],
    inference_reason: technical ? '根据物理键和关联结构生成。' : null, unresolved_item_id: null, references: null,
  };
}

function entity(code: string, name: string, fields: SourceField[]): SourceEntityTable {
  return { table_id: code, source_candidate_id: `candidate_${code}`, name, definition: `${name}是全渠道零售经营中的共享主数据。`, entity_kind: 'MASTER', business_key: [fields[0].field_id], evidence_ids: ['E001', 'E010'], fields };
}

function event(code: string, name: string, grain: string, fields: SourceField[]): SourceEventTable {
  return { table_id: code, source_candidate_id: `candidate_${code}`, name, definition: `${name}记录${grain}。`, grain, factless: false, measure_ids: fields.filter((item) => item.field_role === 'measure').map((item) => item.field_id), evidence_ids: ['E002', 'E010'], fields };
}

const entityTables: SourceEntityTable[] = [
  entity('entity_product', '商品', [
    sourceField('product_code', '商品编码', 'business_key'), sourceField('product_name', '商品名称', 'attribute'),
    sourceField('category_code', '商品品类', 'attribute'), sourceField('brand_code', '商品品牌', 'attribute'),
    sourceField('standard_cost', '标准成本', 'measure', 'decimal'), sourceField('unit', '计量单位', 'attribute'),
    sourceField('product_status', '商品状态', 'attribute'), sourceField('product_sk', '商品代理键', 'surrogate_key'),
  ]),
  entity('entity_store', '门店', [
    sourceField('store_code', '门店编码', 'business_key'), sourceField('store_name', '门店名称', 'attribute'),
    sourceField('region_code', '行政区域编码', 'foreign_key'), sourceField('store_type', '门店类型', 'attribute'),
    sourceField('open_date', '开业日期', 'attribute', 'date'), sourceField('store_status', '门店状态', 'attribute'),
    sourceField('floor_area', '经营面积', 'measure', 'decimal'), sourceField('store_sk', '门店代理键', 'surrogate_key'),
  ]),
  entity('entity_customer', '客户', [
    sourceField('customer_code', '客户编码', 'business_key'), sourceField('customer_name', '客户名称', 'attribute'),
    sourceField('customer_tier', '客户等级', 'attribute'), sourceField('register_channel_code', '注册渠道', 'foreign_key'),
    sourceField('register_date', '注册日期', 'attribute', 'date'), sourceField('customer_status', '客户状态', 'attribute'),
    sourceField('customer_sk', '客户代理键', 'surrogate_key'),
  ]),
  entity('entity_region', '行政区域', [
    sourceField('region_code', '行政区域编码', 'business_key'), sourceField('region_name', '行政区域名称', 'attribute'),
    sourceField('region_level', '行政区域级别', 'attribute'), sourceField('parent_region_code', '上级行政区域', 'foreign_key'),
    sourceField('region_path', '行政区域路径', 'attribute'), sourceField('region_sk', '区域代理键', 'surrogate_key'),
  ]),
  entity('entity_promotion', '促销活动', [
    sourceField('promotion_code', '促销活动编码', 'business_key'), sourceField('promotion_name', '促销活动名称', 'attribute'),
    sourceField('promotion_type', '促销类型', 'attribute'), sourceField('valid_from', '生效时间', 'attribute', 'timestamp'),
    sourceField('valid_to', '失效时间', 'attribute', 'timestamp'), sourceField('promotion_sk', '促销代理键', 'surrogate_key'),
  ]),
  entity('entity_sales_channel', '销售渠道', [
    sourceField('channel_code', '销售渠道编码', 'business_key'), sourceField('channel_name', '销售渠道名称', 'attribute'),
    sourceField('channel_type', '渠道类型', 'attribute'), sourceField('channel_status', '渠道状态', 'attribute'),
    sourceField('channel_sk', '渠道代理键', 'surrogate_key'),
  ]),
];

const eventTables: SourceEventTable[] = [
  event('event_sales_order_line', '销售订单行', '每个销售订单商品行', [
    sourceField('order_line_code', '订单行编码', 'business_key'), sourceField('order_code', '订单编码', 'attribute'),
    sourceField('product_code', '商品编码', 'foreign_key'), sourceField('store_code', '门店编码', 'foreign_key'),
    sourceField('customer_code', '客户编码', 'foreign_key'), sourceField('channel_code', '销售渠道编码', 'foreign_key'),
    sourceField('promotion_code', '促销活动编码', 'foreign_key'), sourceField('order_status', '订单状态', 'attribute', 'status'),
    sourceField('sales_quantity', '销售数量', 'measure', 'integer'), sourceField('sales_amount', '销售金额', 'measure', 'decimal'),
  ]),
  event('event_inventory_movement', '库存移动', '每次商品库存数量变化', [
    sourceField('movement_code', '库存移动编码', 'business_key'), sourceField('product_code', '商品编码', 'foreign_key'),
    sourceField('store_code', '门店编码', 'foreign_key'), sourceField('business_date', '业务日期', 'event_time', 'date'),
    sourceField('movement_type', '移动类型', 'attribute'), sourceField('movement_quantity', '移动数量', 'measure', 'integer'),
    sourceField('on_hand_quantity', '当前库存量', 'measure', 'integer'), sourceField('movement_reason', '移动原因', 'attribute'),
  ]),
  event('event_return_processing', '退货处理', '每个退货商品处理记录', [
    sourceField('return_line_code', '退货行编码', 'business_key'), sourceField('order_line_code', '原订单行编码', 'foreign_key'),
    sourceField('return_status', '退货状态', 'attribute', 'status'), sourceField('return_time', '退货时间', 'event_time', 'timestamp'),
    sourceField('return_quantity', '退货数量', 'measure', 'integer'), sourceField('refund_amount', '退款金额', 'measure', 'decimal'),
    sourceField('return_reason', '退货原因', 'attribute'),
  ]),
  event('event_promotion_redemption', '促销核销', '每次促销权益核销记录', [
    sourceField('redemption_code', '核销编码', 'business_key'), sourceField('promotion_code', '促销活动编码', 'foreign_key'),
    sourceField('order_line_code', '订单行编码', 'foreign_key'), sourceField('store_code', '门店编码', 'foreign_key'),
    sourceField('redemption_time', '核销时间', 'event_time', 'timestamp'), sourceField('issued_quantity', '发放数量', 'measure', 'integer'),
    sourceField('redeemed_quantity', '核销数量', 'measure', 'integer'),
  ]),
];

const relation = (id: string, name: string, fromTable: string, fromField: string, toTable: string, toField: string): SourceRelation => ({
  relationship_id: id, name, relationship_kind: 'REFERENCE', from_table: fromTable, from_field: fromField,
  to_table: toTable, to_field: toField, cardinality: 'MANY_TO_ONE', evidence_ids: ['E003', 'E010'],
  provenance_type: 'document_inferred', inference_reason: '根据业务流程、外键和实际查询关联共同确认。',
});

const relationships: SourceRelation[] = [
  relation('rel_sales_product', '销售商品', 'event_sales_order_line', 'product_code', 'entity_product', 'product_code'),
  relation('rel_sales_store', '销售门店', 'event_sales_order_line', 'store_code', 'entity_store', 'store_code'),
  relation('rel_sales_customer', '购买客户', 'event_sales_order_line', 'customer_code', 'entity_customer', 'customer_code'),
  relation('rel_sales_channel', '销售渠道', 'event_sales_order_line', 'channel_code', 'entity_sales_channel', 'channel_code'),
  relation('rel_sales_promotion', '使用促销', 'event_sales_order_line', 'promotion_code', 'entity_promotion', 'promotion_code'),
  relation('rel_store_region', '门店所属区域', 'entity_store', 'region_code', 'entity_region', 'region_code'),
  relation('rel_inventory_product', '库存商品', 'event_inventory_movement', 'product_code', 'entity_product', 'product_code'),
  relation('rel_inventory_store', '库存门店', 'event_inventory_movement', 'store_code', 'entity_store', 'store_code'),
  relation('rel_return_sales', '退货原订单', 'event_return_processing', 'order_line_code', 'event_sales_order_line', 'order_line_code'),
  relation('rel_redemption_promotion', '核销促销', 'event_promotion_redemption', 'promotion_code', 'entity_promotion', 'promotion_code'),
  relation('rel_redemption_sales', '核销订单', 'event_promotion_redemption', 'order_line_code', 'event_sales_order_line', 'order_line_code'),
  relation('rel_redemption_store', '核销门店', 'event_promotion_redemption', 'store_code', 'entity_store', 'store_code'),
  relation('rel_region_parent', '行政区域父级', 'entity_region', 'parent_region_code', 'entity_region', 'region_code'),
];

const metric = (id: string, name: string, eventTable: string, fields: string[], aggregation: string, formula: string, evidence: string): SourceMetric => ({
  metric_id: id, name, definition: `${name}用于评价全渠道零售经营表现。`, event_table: eventTable,
  measure_fields: fields, aggregation, formula, numerator_field: null, denominator_field: null,
  semi_additive_over_field: id === 'metric_current_inventory' ? 'business_date' : null, evidence_ids: [evidence],
});

const metrics: SourceMetric[] = [
  metric('metric_sales_amount', '销售金额', 'event_sales_order_line', ['sales_amount'], 'sum', 'SUM(sales_amount)', 'E004'),
  metric('metric_net_sales', '净销售额', 'event_sales_order_line', ['sales_amount'], 'formula', 'SUM(sales_amount)-SUM(completed_refund_amount)', 'E004'),
  metric('metric_sales_quantity', '销售数量', 'event_sales_order_line', ['sales_quantity'], 'sum', 'SUM(sales_quantity)', 'E004'),
  metric('metric_order_count', '订单数', 'event_sales_order_line', ['sales_amount'], 'count_distinct', 'COUNT_DISTINCT(order_code)', 'E004'),
  metric('metric_average_order_value', '客单价', 'event_sales_order_line', ['sales_amount'], 'ratio', '净销售额/订单数', 'E004'),
  metric('metric_current_inventory', '当前库存量', 'event_inventory_movement', ['on_hand_quantity'], 'semi_additive', 'LATEST(on_hand_quantity)', 'E005'),
  metric('metric_stockout_rate', '缺货率', 'event_inventory_movement', ['on_hand_quantity'], 'ratio', '缺货商品数/在售商品数', 'E008'),
  metric('metric_return_rate', '退货率', 'event_return_processing', ['return_quantity'], 'ratio', '退货数量/销售数量', 'E006'),
  metric('metric_promotion_redemption_rate', '促销核销率', 'event_promotion_redemption', ['redeemed_quantity', 'issued_quantity'], 'ratio', '核销数量/发放数量', 'E006'),
  metric('metric_gross_profit', '毛利额', 'event_sales_order_line', ['sales_amount'], 'formula', 'SUM(sales_amount-sales_quantity*standard_cost)', 'E013'),
];

const dimensions = [
  ['dim_product_category', '商品品类', 'entity_product', 'category_code'], ['dim_product_brand', '商品品牌', 'entity_product', 'brand_code'],
  ['dim_store', '门店', 'entity_store', 'store_code'], ['dim_region', '行政区域', 'entity_region', 'region_code'],
  ['dim_customer_tier', '客户等级', 'entity_customer', 'customer_tier'], ['dim_sales_channel', '销售渠道', 'entity_sales_channel', 'channel_code'],
  ['dim_promotion', '促销活动', 'entity_promotion', 'promotion_code'], ['dim_order_status', '订单状态', 'event_sales_order_line', 'order_status'],
  ['dim_business_date', '业务日期', 'event_inventory_movement', 'business_date'],
].map(([dimension_id, name, owner_id, target_id]) => ({
  dimension_id, name, definition: `${name}用于切分和筛选经营结果。`, target_type: 'FIELD' as const,
  target_id, owner_id, evidence_ids: [name === '行政区域' ? 'E009' : 'E001'], confidence: name === '行政区域' ? 0.82 : 0.97,
}));

const rules = [
  ['rule_completed_refund', '已完成退款才扣减净销售额', 'METRIC', 'metric_net_sales'],
  ['rule_closed_status', 'CLOSED 保留为独立订单状态', 'FIELD', 'order_status'],
  ['rule_stockout', '当前库存量小于等于零判定缺货', 'METRIC', 'metric_stockout_rate'],
  ['rule_promotion_validity', '促销核销必须位于活动有效期', 'ENTITY', 'entity_promotion'],
  ['rule_business_day', '销售按零售营业日归属', 'EVENT', 'event_sales_order_line'],
  ['rule_latest_inventory', '库存指标只读取最新快照', 'METRIC', 'metric_current_inventory'],
].map(([rule_id, name, target_type, target_id]) => ({
  rule_id, name, definition: name, target_type: target_type as 'ENTITY' | 'EVENT' | 'FIELD' | 'METRIC', target_id,
  owner_id: target_type === 'FIELD' ? 'event_sales_order_line' : null, evidence_ids: ['E014', 'E015'], confidence: 0.91,
}));

const hierarchy = {
  hierarchy_id: 'hierarchy_region_members', name: '行政区域成员层级', owner_table_id: 'entity_region', attribute_id: 'region_code',
  levels: [
    { level_id: 'region', name: '大区', depth: 1, parent_level_id: null },
    { level_id: 'province', name: '省', depth: 2, parent_level_id: 'region' },
    { level_id: 'city', name: '市', depth: 3, parent_level_id: 'province' },
    { level_id: 'district', name: '区县', depth: 4, parent_level_id: 'city' },
  ],
  members: ([
    ['east', '华东', 'EAST', 'region', null], ['jiangsu', '江苏', 'JS', 'province', 'east'], ['nanjing', '南京', 'NJ', 'city', 'jiangsu'], ['xuanwu', '玄武区', 'XW', 'district', 'nanjing'],
    ['zhejiang', '浙江', 'ZJ', 'province', 'east'], ['hangzhou', '杭州', 'HZ', 'city', 'zhejiang'], ['south', '华南', 'SOUTH', 'region', null],
    ['guangdong', '广东', 'GD', 'province', 'south'], ['shenzhen', '深圳', 'SZ', 'city', 'guangdong'], ['nanshan', '南山区', 'NS', 'district', 'shenzhen'],
  ] satisfies Array<[string, string, string, string, string | null]>).map(([member_id, name, code, level_id, parent_member_id]) => ({ member_id, name, code, level_id, parent_member_id })),
  evidence_ids: ['E009'], confidence: 0.96,
};

const allTables = [...entityTables, ...eventTables];
const synonymTargets = [
  ...entityTables.map((item) => ({ type: 'ENTITY' as const, id: item.table_id, owner: null, name: item.name })),
  ...eventTables.map((item) => ({ type: 'EVENT' as const, id: item.table_id, owner: null, name: item.name })),
  ...allTables.flatMap((table) => table.fields.filter((field) => !['surrogate_key', 'foreign_key'].includes(field.field_role)).map((field) => ({ type: 'FIELD' as const, id: field.field_id, owner: table.table_id, name: field.name }))),
  ...dimensions.map((item) => ({ type: 'DIMENSION' as const, id: item.dimension_id, owner: null, name: item.name })),
  ...metrics.map((item) => ({ type: 'METRIC' as const, id: item.metric_id, owner: null, name: item.name })),
  ...rules.map((item) => ({ type: 'RULE' as const, id: item.rule_id, owner: null, name: item.name })),
];
const synonyms = synonymTargets.map((target, index) => ({
  group_id: `synonym_${index + 1}`, target_type: target.type, target_id: target.id, owner_id: target.owner,
  aliases: [
    { text: `${target.name}业务称呼`, confidence: 0.98, evidence_ids: ['E007'] },
    { text: `${target.name}口径`, confidence: 0.96, evidence_ids: ['E007'] },
  ],
}));

const reviewObjects: ReviewItem[] = [
  ...entityTables.flatMap((table) => [{ id: `ENTITY:${table.table_id}`, type: 'ENTITY', objectId: table.table_id, name: table.name, target: table.table_id, disposition: 'INDEPENDENT', reason: table.definition, groupId: '' }, ...table.fields.map((field) => ({ id: `FIELD:${table.table_id}:${field.field_id}`, type: 'FIELD', objectId: field.field_id, name: field.name, target: `${table.name}.${field.name}`, disposition: 'GENERATED', reason: field.description, groupId: '' }))]),
  ...eventTables.flatMap((table) => [{ id: `EVENT:${table.table_id}`, type: 'EVENT', objectId: table.table_id, name: table.name, target: table.table_id, disposition: 'INDEPENDENT', reason: table.definition, groupId: '' }, ...table.fields.map((field) => ({ id: `FIELD:${table.table_id}:${field.field_id}`, type: 'FIELD', objectId: field.field_id, name: field.name, target: `${table.name}.${field.name}`, disposition: 'GENERATED', reason: field.description, groupId: '' }))]),
  ...relationships.map((item) => ({ id: `RELATION:${item.relationship_id}`, type: 'RELATION', objectId: item.relationship_id, name: item.name, target: `${item.from_table} → ${item.to_table}`, disposition: 'GENERATED', reason: item.inference_reason ?? '', groupId: '' })),
  ...metrics.map((item) => ({ id: `METRIC:${item.metric_id}`, type: 'METRIC', objectId: item.metric_id, name: item.name, target: item.metric_id, disposition: 'GENERATED', reason: item.definition, groupId: '' })),
  ...dimensions.map((item) => ({ id: `DIMENSION:${item.dimension_id}`, type: 'DIMENSION', objectId: item.dimension_id, name: item.name, target: item.target_id, disposition: 'MAPPED_FIELD', reason: item.definition, groupId: '' })),
  ...rules.map((item) => ({ id: `RULE:${item.rule_id}`, type: 'RULE', objectId: item.rule_id, name: item.name, target: item.target_id, disposition: 'NEEDS_STRUCTURE', reason: item.definition, groupId: '' })),
];

const conflictIds = new Set(['METRIC:metric_net_sales', 'FIELD:event_sales_order_line:order_status']);
const weakIds = new Set(['METRIC:metric_stockout_rate', 'DIMENSION:dim_region', 'DIMENSION:dim_customer_tier', 'RULE:rule_promotion_validity', 'METRIC:metric_gross_profit']);
const generatedIds = new Set([
  ...rules.map((item) => `RULE:${item.rule_id}`), ...dimensions.map((item) => `DIMENSION:${item.dimension_id}`),
  'RELATION:rel_return_sales', 'RELATION:rel_region_parent', 'RELATION:rel_sales_product',
  'RELATION:rel_sales_store', 'RELATION:rel_inventory_product',
]);
const conflictItems = reviewObjects.filter((item) => conflictIds.has(item.id));
const weakItems = reviewObjects.filter((item) => weakIds.has(item.id) && !conflictIds.has(item.id));
const generatedItems = reviewObjects.filter((item) => generatedIds.has(item.id) && !conflictIds.has(item.id) && !weakIds.has(item.id));
const manualIds = new Set([...conflictItems, ...weakItems, ...generatedItems].map((item) => item.id));
const autoItems = reviewObjects.filter((item) => !manualIds.has(item.id));
if (reviewObjects.length !== 120 || autoItems.length !== 96 || conflictItems.length !== 2 || weakItems.length !== 5 || generatedItems.length !== 17) {
  throw new Error('全渠道审核分类计数不一致');
}
const classify = (items: ReviewItem[], groupId: string, reviewClass: NonNullable<ReviewItem['reviewClass']>) => items.map((item) => ({
  ...item, groupId, reviewClass, confidence: reviewClass === 'AUTO' ? 0.97 : reviewClass === 'WEAK_EVIDENCE' ? 0.78 : 0.9,
  evidenceIds: reviewClass === 'WEAK_EVIDENCE' ? ['E008'] : ['E001', 'E010'],
}));
const reviewItems = [
  ...classify(autoItems, 'auto', 'AUTO'), ...classify(conflictItems, 'conflicts', 'CONFLICT'),
  ...classify(weakItems, 'weak', 'WEAK_EVIDENCE'), ...classify(generatedItems, 'generated', 'SYSTEM_GENERATED'),
];

export const omnichannelDocument: FixtureDocument = {
  documentVersion: 'v1', modelVersion: 'V1', status: 'PUBLISHED',
  fileName: '全渠道零售经营建模资料包-v1.zip', title: '全渠道零售经营建模资料包',
  summary: '整合业务资料、数据库快照和代码仓库，形成全渠道零售经营语义模型。',
  content: '多来源资料包由 5 个文件、2 个数据源快照和 1 个代码仓库快照组成。',
  sha256: computeSourceBundleFingerprint(omnichannelSourceAssets.map((item) => item.fingerprint)),
  qualityGate: { publishable: true, blockers: [], warnings: ['两项来源冲突必须在生成模型前完成决定。'] },
  artifacts: {
    analysis: {
      business_summary: { system_name: '全渠道零售经营语义模型', business_goal: '统一全渠道零售经营分析口径', business_scope: ['销售', '库存', '退货', '促销'] },
      entity_candidates: entityTables.map((item, index) => ({ candidate_id: `ENT${index + 1}`, name: item.name, definition: item.definition, evidence_ids: item.evidence_ids, confidence: 0.97 })),
      event_candidates: eventTables.map((item, index) => ({ candidate_id: `EVT${index + 1}`, name: item.name, definition: item.definition, evidence_ids: item.evidence_ids, confidence: 0.97 })),
      dimension_candidates: dimensions.map((item, index) => ({ candidate_id: `DIM${index + 1}`, name: item.name, definition: item.definition, evidence_ids: item.evidence_ids, confidence: item.confidence })),
      metric_candidates: metrics.map((item, index) => ({ candidate_id: `MET${index + 1}`, name: item.name, definition: item.definition, evidence_ids: item.evidence_ids, confidence: 0.96 })),
      grain_candidates: eventTables.map((item, index) => ({ candidate_id: `GRN${index + 1}`, name: item.grain, definition: item.grain, evidence_ids: item.evidence_ids, confidence: 0.96 })),
      hierarchy_candidates: [{ candidate_id: 'HIE1', name: hierarchy.name, definition: '大区到区县的成员层级', evidence_ids: ['E009'], confidence: 0.96 }],
      evidence_catalog: omnichannelEvidence, unresolved: [], example_questions: ['各渠道净销售额是多少？', '哪些门店缺货率最高？'],
    },
    model: {
      entity_tables: entityTables, event_tables: eventTables, relationships, metrics, evidence_catalog: omnichannelEvidence,
      unresolved: [], candidate_resolutions: [],
    },
    enrichment: { metadata: { source: 'deterministic_fixture' }, dimensions, rule_candidates: rules, member_hierarchies: [hierarchy], synonym_groups: synonyms },
    validation: { valid: true, errors: [], warnings: [] }, enrichmentValidation: { valid: true, errors: [], warnings: [] },
    ddl: '-- 全渠道零售经营模型 DDL', report: '全渠道模型由多来源互证后生成。', callSummary: { mode: 'fixture' },
  },
  generationNotes: reviewObjects.slice(0, 20).map((item, index) => ({ id: `NOTE${index + 1}`, kind: item.type, sourceId: item.id, sourceName: item.name, disposition: item.disposition, target: item.target, reason: item.reason })),
  review: {
    groups: [
      { id: 'auto', name: '系统预审', itemIds: autoItems.map((item) => item.id) },
      { id: 'conflicts', name: '来源冲突', itemIds: conflictItems.map((item) => item.id) },
      { id: 'weak', name: '弱依据', itemIds: weakItems.map((item) => item.id) },
      { id: 'generated', name: '系统生成', itemIds: generatedItems.map((item) => item.id) },
    ],
    items: reviewItems,
  },
  adjacentDiff: { added: 120, modified: 0, removed: 0, inherited: 0 }, artifactManifest: { sourceCount: 8 },
};
