import type { EvidenceAuthority, SourceEvidenceLocator } from '../ai-modeling/source-bundle.ts';
import {
  guanyijiaEvidenceFixture,
  guanyijiaRepositoryEvidenceFixture,
} from '../ai-modeling/guanyijia-fixture.ts';
import type { CandidateModelSnapshot } from '../ai-modeling/candidate-model.ts';
import type { FixtureDocument, LinguanModelSnapshot } from '../ai-modeling/types.ts';

export type RetailEvidenceSource = {
  connectionId: string;
  legacySourceId: string;
  legacyConnectorId: string;
  displayName: string;
  connectorType: string;
  role: string;
  authority: EvidenceAuthority;
  versionR1: string;
  versionR2: string;
  summary: string;
  objectCounts: Record<string, number>;
  upstreamConnectionIds?: string[];
};

export type RetailEvidenceEntry = {
  evidenceId: string;
  connectionId: string;
  introducedRevision: 1 | 2;
  section: string;
  statement: string;
  locator: SourceEvidenceLocator;
};

export type RetailEvidenceClaim = {
  claimId: string;
  sourceId: string;
  topic: string;
  assertion: string;
  evidenceRefs: string[];
  authority: EvidenceAuthority;
  upstreamClaimIds?: string[];
};

export type RetailObjectSupportEdge = {
  objectKind: 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC' | 'HIERARCHY' | 'RULE' | 'ALIAS' | 'TIME_RULE';
  objectCode: string;
  ownerCode?: string;
  supportAspect: 'OBJECT_STRUCTURE' | 'FIELD_SEMANTIC' | 'DIMENSION_SEMANTIC' | 'METRIC_FORMULA' | 'RULE_LOGIC' | 'ALIAS_TERM' | 'TIME_ASSIGNMENT';
  allowedTopics: string[];
  supportClaimIds: string[];
};

export type RetailEvidenceStoryBatch = {
  batchId: `B0${1 | 2 | 3 | 4 | 5 | 6 | 7 | 8}`;
  addedConnectionId: string;
  connectionIds: string[];
  evidenceIds: string[];
  claims: RetailEvidenceClaim[];
  findings: Array<{
    findingId: string;
    topic: 'NET_SALES' | 'BOT_FILTER' | 'BUSINESS_DAY';
    title: string;
    severity: 'BLOCKER';
    claimIds: string[];
    affectedObjectCodes: string[];
  }>;
  lineageGaps: Array<{ sourceId: string; missingUpstreamSourceIds: string[] }>;
  independentRootSourceCount: number;
};

export const retailEvidenceSources: RetailEvidenceSource[] = [
  {
    connectionId: 'retail_mysql', legacySourceId: 'group-source-mysql', legacyConnectorId: 'mysql-group-retail',
    displayName: 'MySQL · 零售交易库', connectorType: 'MySQL', role: '物理结构与数据形态', authority: 'PRIMARY',
    versionR1: 'mysql-snapshot-2026-08-01', versionR2: 'mysql-snapshot-2026-08-09',
    summary: '订单、库存、退货和促销的表、字段、键与约束。', objectCounts: { tables: 42, fields: 386, indexes: 73 },
  },
  {
    connectionId: 'retail_github', legacySourceId: 'group-source-github', legacyConnectorId: 'github-group-retail',
    displayName: 'GitHub · 零售分析仓库', connectorType: 'GitHub', role: '计算逻辑与业务规则', authority: 'CORROBORATING',
    versionR1: 'a91f2c7', versionR2: 'b72e5d1',
    summary: '固定 Commit 中的迁移、SQL、枚举和规则实现。', objectCounts: { files: 73, sqlFiles: 29, rules: 17 },
  },
  {
    connectionId: 'retail_semantica', legacySourceId: 'group-source-semantica-rdf', legacyConnectorId: 'semantica-rdf',
    displayName: 'Semantica · 企业术语图', connectorType: 'RDF/SPARQL', role: '术语、本体与概念关系', authority: 'DERIVED',
    versionR1: 'rdf-graph-2026-08-01', versionR2: 'rdf-graph-2026-08-09',
    summary: 'RDF 命名图中的业务术语、概念层级和三元组。', objectCounts: { namedGraphs: 2, classes: 37, triples: 1248 },
    upstreamConnectionIds: ['retail_sharepoint'],
  },
  {
    connectionId: 'retail_sharepoint', legacySourceId: 'group-source-sharepoint', legacyConnectorId: 'sharepoint-operations',
    displayName: 'SharePoint · 集团经营知识库', connectorType: 'SharePoint', role: '制度、口径与术语文档', authority: 'PRIMARY',
    versionR1: 'drive-delta-2026-08-01', versionR2: 'drive-delta-2026-08-09',
    summary: '带作者、版本和章节的集团制度与指标口径。', objectCounts: { documents: 46, sections: 312, terms: 185 },
  },
  {
    connectionId: 'retail_mongodb', legacySourceId: 'group-source-mongodb', legacyConnectorId: 'mongodb-product-profile',
    displayName: 'MongoDB · 商品与客户画像', connectorType: 'MongoDB', role: '嵌套数据形态', authority: 'CORROBORATING',
    versionR1: 'mongo-snapshot-2026-08-01', versionR2: 'mongo-snapshot-2026-08-09',
    summary: '商品富文本和客户等级历史的嵌套文档。', objectCounts: { collections: 6, fields: 94, pipelines: 8 },
  },
  {
    connectionId: 'retail_elasticsearch', legacySourceId: 'group-source-elasticsearch', legacyConnectorId: 'elasticsearch-retail-search',
    displayName: 'Elasticsearch · 零售搜索索引', connectorType: 'Elasticsearch', role: '实际搜索与过滤行为', authority: 'DERIVED',
    versionR1: 'es-snapshot-2026-08-01', versionR2: 'es-snapshot-2026-08-09',
    summary: '索引 Mapping、过滤条件和查询聚合；由交易和商品数据派生。', objectCounts: { indexes: 9, mappings: 128, queries: 18 },
    upstreamConnectionIds: ['retail_mysql', 'retail_mongodb', 'retail_github'],
  },
  {
    connectionId: 'retail_minio', legacySourceId: 'group-source-minio', legacyConnectorId: 'minio-retail-knowledge',
    displayName: 'MinIO · 集团经营资料库', connectorType: 'S3/MinIO', role: '正式制度与数据文件', authority: 'PRIMARY',
    versionR1: 'object-version-set-2026-08-01', versionR2: 'object-version-set-2026-08-09',
    summary: '固定版本的 PDF、WPS、图片、CSV 和 Parquet。', objectCounts: { objects: 28, documents: 16, datasets: 7, images: 5 },
  },
  {
    connectionId: 'retail_kafka', legacySourceId: 'group-source-kafka', legacyConnectorId: 'kafka-retail-events',
    displayName: 'Kafka · 零售业务事件流', connectorType: 'Kafka', role: '事件顺序与生产消费关系', authority: 'CORROBORATING',
    versionR1: 'schema-offset-2026-08-01', versionR2: 'schema-offset-2026-08-09',
    summary: '事件 Schema、分区键、顺序和确定性样例。', objectCounts: { topics: 12, schemas: 18, consumers: 9 },
  },
];

export const retailEvidenceEntries: RetailEvidenceEntry[] = [
  { evidenceId: 'GR001', connectionId: 'retail_mysql', introducedRevision: 1, section: 'sales_order_line', statement: '销售订单行保存订单状态、销售数量和销售金额。', locator: { kind: 'DATABASE', datasource: 'group_retail_core', schema: 'retail', table: 'sales_order_line' } },
  { evidenceId: 'GR002', connectionId: 'retail_mysql', introducedRevision: 1, section: 'return_processing.refund_time', statement: '退款完成时间与退货发起时间分别存储。', locator: { kind: 'DATABASE', datasource: 'group_retail_core', schema: 'retail', table: 'return_processing', field: 'refund_time' } },
  { evidenceId: 'GR003', connectionId: 'retail_mysql', introducedRevision: 1, section: 'inventory_snapshot', statement: '库存快照以门店、商品和业务日期组成稳定粒度。', locator: { kind: 'DATABASE', datasource: 'group_retail_core', schema: 'retail', table: 'inventory_snapshot' } },
  { evidenceId: 'GR023', connectionId: 'retail_mysql', introducedRevision: 2, section: 'query_digest/legacy_daily_sales', statement: '旧日报查询在退货发起时就冲减销售额。', locator: { kind: 'DATABASE', datasource: 'group_retail_core', schema: 'retail', queryId: 'legacy-daily-sales-v1' } },
  { evidenceId: 'GR014', connectionId: 'retail_github', introducedRevision: 1, section: 'sql/net_sales.sql', statement: '净销售额只扣减状态为 COMPLETED 的退款。', locator: { kind: 'GITHUB', repository: 'group-retail/analytics', commit: 'b72e5d1', path: 'sql/net_sales.sql', lineStart: 18, lineEnd: 31 } },
  { evidenceId: 'GR015', connectionId: 'retail_github', introducedRevision: 1, section: 'domain/OrderStatus.ts', statement: 'CLOSED 是独立订单状态，不并入 CANCELLED。', locator: { kind: 'GITHUB', repository: 'group-retail/analytics', commit: 'b72e5d1', path: 'domain/OrderStatus.ts', lineStart: 6, lineEnd: 18 } },
  { evidenceId: 'GR016', connectionId: 'retail_github', introducedRevision: 1, section: 'sql/search_conversion.sql', statement: '搜索转化计算过滤机器人和内部压测流量。', locator: { kind: 'GITHUB', repository: 'group-retail/analytics', commit: 'b72e5d1', path: 'sql/search_conversion.sql', lineStart: 22, lineEnd: 40 } },
  { evidenceId: 'GR009', connectionId: 'retail_semantica', introducedRevision: 1, section: 'urn:retail:vocabulary', statement: '净销售额的业务称呼包括实收销售额和销售净额。', locator: { kind: 'RDF', endpoint: 'semantica.internal/sparql', graphUri: 'urn:retail:vocabulary', subject: 'retail:NetSales', predicate: 'skos:altLabel', object: '实收销售额' } },
  { evidenceId: 'GR010', connectionId: 'retail_semantica', introducedRevision: 1, section: 'urn:retail:ontology', statement: '客户等级的生效时间属于客户等级关系，而非客户主数据本身。', locator: { kind: 'RDF', endpoint: 'semantica.internal/sparql', graphUri: 'urn:retail:ontology', subject: 'retail:CustomerTier', predicate: 'retail:effectiveAt', object: 'retail:TierAssignment' } },
  { evidenceId: 'GR017', connectionId: 'retail_sharepoint', introducedRevision: 1, section: '指标口径/净销售额', statement: '退款完成后才冲减净销售额。', locator: { kind: 'SHAREPOINT', site: '集团经营知识库', driveItemId: 'sp-net-sales', path: '/指标口径/净销售额.docx', section: '退款扣减' } },
  { evidenceId: 'GR018', connectionId: 'retail_sharepoint', introducedRevision: 1, section: '客户运营/等级管理', statement: '客户等级从规则计算完成时间起生效。', locator: { kind: 'SHAREPOINT', site: '集团经营知识库', driveItemId: 'sp-customer-tier', path: '/客户运营/等级管理.docx', section: '生效时间' } },
  { evidenceId: 'GR019', connectionId: 'retail_sharepoint', introducedRevision: 1, section: '门店运营/营业日', statement: '门店营业日允许跨自然日。', locator: { kind: 'SHAREPOINT', site: '集团经营知识库', driveItemId: 'sp-business-day', path: '/门店运营/营业日.docx', section: '跨日规则' } },
  { evidenceId: 'GR004', connectionId: 'retail_mongodb', introducedRevision: 1, section: 'customer_profiles.tier_history', statement: '客户等级历史包含等级编码、生效时间和失效时间。', locator: { kind: 'MONGODB', datasource: 'product_customer_profile', database: 'retail_profile', collection: 'customer_profiles', jsonPath: '$.tier_history[*].effective_at' } },
  { evidenceId: 'GR005', connectionId: 'retail_mongodb', introducedRevision: 1, section: 'products.attributes', statement: '商品扩展属性以嵌套键值保存。', locator: { kind: 'MONGODB', datasource: 'product_customer_profile', database: 'retail_profile', collection: 'products', jsonPath: '$.attributes' } },
  { evidenceId: 'GR006', connectionId: 'retail_mongodb', introducedRevision: 1, section: 'customer_profiles.channel', statement: '客户画像记录注册渠道和当前等级。', locator: { kind: 'MONGODB', datasource: 'product_customer_profile', database: 'retail_profile', collection: 'customer_profiles', jsonPath: '$.current_tier' } },
  { evidenceId: 'GR007', connectionId: 'retail_elasticsearch', introducedRevision: 1, section: 'search-events-v2/_mapping', statement: '搜索事件包含 user_agent_class 与 traffic_type。', locator: { kind: 'ELASTICSEARCH', cluster: 'retail_search', index: 'search-events-v2', mappingPath: 'properties.traffic_type' } },
  { evidenceId: 'GR008', connectionId: 'retail_elasticsearch', introducedRevision: 1, section: 'conversion-query', statement: '转化查询排除 bot 和 internal_test 两类流量。', locator: { kind: 'ELASTICSEARCH', cluster: 'retail_search', index: 'search-events-v2', queryId: 'conversion-query-v2' } },
  { evidenceId: 'GR024', connectionId: 'retail_elasticsearch', introducedRevision: 2, section: 'legacy-conversion-dashboard', statement: '旧看板只排除 bot，仍包含 internal_test 压测流量。', locator: { kind: 'ELASTICSEARCH', cluster: 'retail_search', index: 'search-events-v2', queryId: 'legacy-conversion-dashboard-v1' } },
  { evidenceId: 'GR011', connectionId: 'retail_minio', introducedRevision: 1, section: '经营指标口径.wps#净销售额', statement: '净销售额按已完成退款扣减，发起退货不立即冲减。', locator: { kind: 'OBJECT', bucket: 'retail-knowledge', objectKey: 'metrics/经营指标口径.wps', versionId: '2026-08-09', mediaType: 'application/vnd.ms-works' } },
  { evidenceId: 'GR012', connectionId: 'retail_minio', introducedRevision: 1, section: '营业日制度.pdf#跨日订单', statement: '订单在门店营业日结束后支付时归属下一营业日。', locator: { kind: 'OBJECT', bucket: 'retail-knowledge', objectKey: 'policies/营业日制度.pdf', versionId: '2026-08-09', mediaType: 'application/pdf' } },
  { evidenceId: 'GR013', connectionId: 'retail_minio', introducedRevision: 1, section: '库存快照说明.pdf', statement: '当前库存量读取营业日最后一份有效快照。', locator: { kind: 'OBJECT', bucket: 'retail-knowledge', objectKey: 'metrics/库存快照说明.pdf', versionId: '2026-08-09', mediaType: 'application/pdf' } },
  { evidenceId: 'GR020', connectionId: 'retail_kafka', introducedRevision: 1, section: 'retail.return.completed', statement: '退款完成事件包含 return_line_code 和 completed_at。', locator: { kind: 'KAFKA', cluster: 'retail-business-events', topic: 'retail.return.completed', schemaId: '203', offsetRange: 'fixture:1200-1210' } },
  { evidenceId: 'GR021', connectionId: 'retail_kafka', introducedRevision: 1, section: 'retail.inventory.snapshot', statement: '库存快照事件按门店和商品分区，offset 保证分区内顺序。', locator: { kind: 'KAFKA', cluster: 'retail-business-events', topic: 'retail.inventory.snapshot', schemaId: 'inventory-snapshot-v4', offsetRange: 'fixture:780-786' } },
  { evidenceId: 'GR022', connectionId: 'retail_kafka', introducedRevision: 1, section: 'retail.search.events', statement: '搜索事件标记机器人流量并保留事件发生时间。', locator: { kind: 'KAFKA', cluster: 'retail-business-events', topic: 'retail.search.events', schemaId: '311', offsetRange: 'fixture:420-438' } },
  { evidenceId: 'GR025', connectionId: 'retail_kafka', introducedRevision: 2, section: 'retail.order.paid', statement: '旧支付事件没有营业日字段，消费端曾按自然日归属。', locator: { kind: 'KAFKA', cluster: 'retail-business-events', topic: 'retail.order.paid', schemaId: '317', offsetRange: 'fixture:900-910' } },
];

export const retailEvidenceClaims: RetailEvidenceClaim[] = [
  { claimId: 'claim_mysql_sales_schema', sourceId: 'retail_mysql', topic: 'SALES_SCHEMA', assertion: '销售订单行保存销售数量、销售金额和订单状态。', evidenceRefs: ['GR001'], authority: 'PRIMARY' },
  { claimId: 'claim_mysql_refund_fields', sourceId: 'retail_mysql', topic: 'NET_SALES', assertion: '退货发起时间与退款完成时间是两个独立字段。', evidenceRefs: ['GR002'], authority: 'PRIMARY' },
  { claimId: 'claim_mysql_inventory_grain', sourceId: 'retail_mysql', topic: 'INVENTORY', assertion: '库存快照粒度为门店、商品和业务日期。', evidenceRefs: ['GR003'], authority: 'PRIMARY' },
  { claimId: 'claim_mysql_net_sales_legacy', sourceId: 'retail_mysql', topic: 'NET_SALES', assertion: '旧日报在退货发起时就冲减净销售额。', evidenceRefs: ['GR023'], authority: 'PRIMARY' },
  { claimId: 'claim_github_net_sales', sourceId: 'retail_github', topic: 'NET_SALES', assertion: '当前指标 SQL 仅扣减已完成退款。', evidenceRefs: ['GR014'], authority: 'CORROBORATING' },
  { claimId: 'claim_github_order_status', sourceId: 'retail_github', topic: 'ORDER_STATUS', assertion: 'CLOSED 是独立订单状态。', evidenceRefs: ['GR015'], authority: 'CORROBORATING' },
  { claimId: 'claim_github_bot_filter', sourceId: 'retail_github', topic: 'BOT_FILTER', assertion: '当前指标 SQL 排除 bot 和 internal_test。', evidenceRefs: ['GR016'], authority: 'CORROBORATING' },
  { claimId: 'claim_semantica_net_sales_alias', sourceId: 'retail_semantica', topic: 'NET_SALES_ALIAS', assertion: '净销售额可称实收销售额和销售净额。', evidenceRefs: ['GR009'], authority: 'DERIVED', upstreamClaimIds: ['claim_sharepoint_net_sales'] },
  { claimId: 'claim_semantica_customer_tier_time', sourceId: 'retail_semantica', topic: 'CUSTOMER_TIER', assertion: '客户等级生效时间属于等级关系。', evidenceRefs: ['GR010'], authority: 'DERIVED', upstreamClaimIds: ['claim_sharepoint_customer_tier'] },
  { claimId: 'claim_sharepoint_net_sales', sourceId: 'retail_sharepoint', topic: 'NET_SALES', assertion: '退款完成后才冲减净销售额。', evidenceRefs: ['GR017'], authority: 'PRIMARY' },
  { claimId: 'claim_sharepoint_customer_tier', sourceId: 'retail_sharepoint', topic: 'CUSTOMER_TIER', assertion: '客户等级从规则计算完成时间起生效。', evidenceRefs: ['GR018'], authority: 'PRIMARY' },
  { claimId: 'claim_sharepoint_business_day', sourceId: 'retail_sharepoint', topic: 'BUSINESS_DAY', assertion: '门店营业日允许跨自然日。', evidenceRefs: ['GR019'], authority: 'PRIMARY' },
  { claimId: 'claim_mongodb_customer_tier', sourceId: 'retail_mongodb', topic: 'CUSTOMER_TIER', assertion: '客户等级历史保存生效和失效时间。', evidenceRefs: ['GR004'], authority: 'CORROBORATING' },
  { claimId: 'claim_mongodb_product_attributes', sourceId: 'retail_mongodb', topic: 'PRODUCT_ATTRIBUTE', assertion: '商品扩展属性以嵌套键值保存。', evidenceRefs: ['GR005'], authority: 'CORROBORATING' },
  { claimId: 'claim_mongodb_current_tier', sourceId: 'retail_mongodb', topic: 'CUSTOMER_TIER', assertion: '客户画像记录注册渠道和当前等级。', evidenceRefs: ['GR006'], authority: 'CORROBORATING' },
  { claimId: 'claim_elasticsearch_traffic_mapping', sourceId: 'retail_elasticsearch', topic: 'BOT_FILTER', assertion: '搜索事件包含流量类型字段。', evidenceRefs: ['GR007'], authority: 'DERIVED', upstreamClaimIds: ['claim_github_bot_filter'] },
  { claimId: 'claim_elasticsearch_bot_filter', sourceId: 'retail_elasticsearch', topic: 'BOT_FILTER', assertion: '当前转化查询排除 bot 和 internal_test。', evidenceRefs: ['GR008'], authority: 'DERIVED', upstreamClaimIds: ['claim_github_bot_filter'] },
  { claimId: 'claim_elasticsearch_bot_filter_legacy', sourceId: 'retail_elasticsearch', topic: 'BOT_FILTER', assertion: '旧看板只排除 bot，仍包含 internal_test。', evidenceRefs: ['GR024'], authority: 'DERIVED', upstreamClaimIds: ['claim_github_bot_filter'] },
  { claimId: 'claim_minio_net_sales', sourceId: 'retail_minio', topic: 'NET_SALES', assertion: '正式指标文档要求退款完成后扣减净销售额。', evidenceRefs: ['GR011'], authority: 'PRIMARY' },
  { claimId: 'claim_minio_business_day', sourceId: 'retail_minio', topic: 'BUSINESS_DAY', assertion: '跨日订单按门店营业日归属。', evidenceRefs: ['GR012'], authority: 'PRIMARY' },
  { claimId: 'claim_minio_inventory_snapshot', sourceId: 'retail_minio', topic: 'INVENTORY', assertion: '当前库存读取营业日最后一份有效快照。', evidenceRefs: ['GR013'], authority: 'PRIMARY' },
  { claimId: 'claim_kafka_return_completed', sourceId: 'retail_kafka', topic: 'NET_SALES', assertion: '退款完成是独立有序事件。', evidenceRefs: ['GR020'], authority: 'CORROBORATING' },
  { claimId: 'claim_kafka_inventory_order', sourceId: 'retail_kafka', topic: 'INVENTORY', assertion: '库存快照事件在门店商品分区内有序。', evidenceRefs: ['GR021'], authority: 'CORROBORATING' },
  { claimId: 'claim_kafka_bot_marker', sourceId: 'retail_kafka', topic: 'BOT_FILTER', assertion: '搜索事件标记机器人流量并保留事件发生时间。', evidenceRefs: ['GR022'], authority: 'CORROBORATING' },
  { claimId: 'claim_kafka_business_day_legacy', sourceId: 'retail_kafka', topic: 'BUSINESS_DAY', assertion: '旧支付事件缺少营业日字段并曾按自然日归属。', evidenceRefs: ['GR025'], authority: 'CORROBORATING' },
];

// These are semantic support edges, not a fallback from arbitrary evidence IDs.
// Objects absent from this list remain explicitly NEEDS_CONFIRMATION in frozen catalogs.
export const retailObjectSupportEdges: RetailObjectSupportEdge[] = [
  { objectKind: 'EVENT', objectCode: 'event_sales_order_line', supportAspect: 'OBJECT_STRUCTURE', allowedTopics: ['SALES_SCHEMA'], supportClaimIds: ['claim_mysql_sales_schema'] },
  { objectKind: 'FIELD', ownerCode: 'event_sales_order_line', objectCode: 'order_status', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['SALES_SCHEMA', 'ORDER_STATUS'], supportClaimIds: ['claim_mysql_sales_schema', 'claim_github_order_status'] },
  { objectKind: 'FIELD', ownerCode: 'event_sales_order_line', objectCode: 'sales_quantity', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['SALES_SCHEMA'], supportClaimIds: ['claim_mysql_sales_schema'] },
  { objectKind: 'FIELD', ownerCode: 'event_sales_order_line', objectCode: 'sales_amount', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['SALES_SCHEMA'], supportClaimIds: ['claim_mysql_sales_schema'] },
  { objectKind: 'EVENT', objectCode: 'event_return_processing', supportAspect: 'OBJECT_STRUCTURE', allowedTopics: ['NET_SALES'], supportClaimIds: ['claim_mysql_refund_fields'] },
  { objectKind: 'FIELD', ownerCode: 'event_return_processing', objectCode: 'return_time', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['NET_SALES'], supportClaimIds: ['claim_mysql_refund_fields', 'claim_kafka_return_completed'] },
  { objectKind: 'METRIC', objectCode: 'metric_net_sales', supportAspect: 'METRIC_FORMULA', allowedTopics: ['NET_SALES'], supportClaimIds: ['claim_github_net_sales', 'claim_sharepoint_net_sales', 'claim_minio_net_sales'] },
  { objectKind: 'RULE', objectCode: 'rule_completed_refund', supportAspect: 'RULE_LOGIC', allowedTopics: ['NET_SALES'], supportClaimIds: ['claim_github_net_sales', 'claim_sharepoint_net_sales', 'claim_minio_net_sales'] },
  { objectKind: 'FIELD', ownerCode: 'entity_customer', objectCode: 'customer_tier', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['CUSTOMER_TIER'], supportClaimIds: ['claim_mongodb_customer_tier', 'claim_mongodb_current_tier', 'claim_sharepoint_customer_tier'] },
  { objectKind: 'DIMENSION', objectCode: 'dim_customer_tier', supportAspect: 'DIMENSION_SEMANTIC', allowedTopics: ['CUSTOMER_TIER'], supportClaimIds: ['claim_mongodb_customer_tier', 'claim_sharepoint_customer_tier'] },
  { objectKind: 'METRIC', objectCode: 'metric_current_inventory', supportAspect: 'METRIC_FORMULA', allowedTopics: ['INVENTORY'], supportClaimIds: ['claim_minio_inventory_snapshot'] },
  { objectKind: 'RULE', objectCode: 'rule_latest_inventory', supportAspect: 'RULE_LOGIC', allowedTopics: ['INVENTORY'], supportClaimIds: ['claim_minio_inventory_snapshot', 'claim_kafka_inventory_order'] },
  { objectKind: 'TIME_RULE', objectCode: 'inventory_snapshot_time', supportAspect: 'TIME_ASSIGNMENT', allowedTopics: ['INVENTORY'], supportClaimIds: ['claim_minio_inventory_snapshot', 'claim_kafka_inventory_order'] },
  { objectKind: 'RULE', objectCode: 'rule_search_bot_exclusion', supportAspect: 'RULE_LOGIC', allowedTopics: ['BOT_FILTER'], supportClaimIds: ['claim_github_bot_filter', 'claim_elasticsearch_bot_filter'] },
  { objectKind: 'DIMENSION', objectCode: 'dim_business_date', supportAspect: 'DIMENSION_SEMANTIC', allowedTopics: ['BUSINESS_DAY'], supportClaimIds: ['claim_sharepoint_business_day', 'claim_minio_business_day'] },
  { objectKind: 'RULE', objectCode: 'rule_business_day', supportAspect: 'RULE_LOGIC', allowedTopics: ['BUSINESS_DAY'], supportClaimIds: ['claim_sharepoint_business_day', 'claim_minio_business_day'] },
  { objectKind: 'TIME_RULE', objectCode: 'order_business_time', supportAspect: 'TIME_ASSIGNMENT', allowedTopics: ['BUSINESS_DAY'], supportClaimIds: ['claim_sharepoint_business_day', 'claim_minio_business_day'] },
  { objectKind: 'ALIAS', objectCode: 'synonym_73:1', supportAspect: 'ALIAS_TERM', allowedTopics: ['NET_SALES_ALIAS'], supportClaimIds: ['claim_semantica_net_sales_alias'] },
  { objectKind: 'ALIAS', objectCode: 'synonym_73:2', supportAspect: 'ALIAS_TERM', allowedTopics: ['NET_SALES_ALIAS'], supportClaimIds: ['claim_semantica_net_sales_alias'] },
];

export type RetailObjectEvidenceProjection = {
  objectKind: RetailObjectSupportEdge['objectKind'];
  objectCode: string;
  ownerCode?: string;
  status: 'VERIFIED' | 'NEEDS_CONFIRMATION';
  evidenceRefs: string[];
  supportClaimIds: string[];
};

export function projectRetailObjectEvidence(
  objectKind: RetailObjectSupportEdge['objectKind'],
  objectCode: string,
  ownerCode: string | undefined,
  availableClaims: ReadonlyArray<Pick<RetailEvidenceClaim, 'claimId' | 'evidenceRefs'>> = retailEvidenceClaims,
): RetailObjectEvidenceProjection {
  const edge = retailObjectSupportEdges.find((item) => item.objectKind === objectKind
    && item.objectCode === objectCode && item.ownerCode === ownerCode);
  const claimById = new Map(availableClaims.map((claim) => [claim.claimId, claim]));
  const supportClaims = (edge?.supportClaimIds ?? []).flatMap((claimId) => claimById.get(claimId) ?? []);
  const evidenceRefs = [...new Set(supportClaims.flatMap((claim) => claim.evidenceRefs))];
  return {
    objectKind, objectCode, ...(ownerCode ? { ownerCode } : {}),
    status: evidenceRefs.length ? 'VERIFIED' : 'NEEDS_CONFIRMATION',
    evidenceRefs,
    supportClaimIds: supportClaims.map((claim) => claim.claimId),
  };
}

export type RetailRegistryBlocker = {
  findingId: string;
  topic: 'NET_SALES' | 'BOT_FILTER' | 'BUSINESS_DAY';
  title: string;
  triggerConnectionId: string;
  claimIds: string[];
  evidenceIds: string[];
  affectedObjectCodes: string[];
  difference: string;
};

const retailBlockerDefinitions: Array<Omit<RetailRegistryBlocker, 'evidenceIds'>> = [
  {
    findingId: 'finding_net_sales_refund', topic: 'NET_SALES', title: '净销售额退款时点冲突',
    triggerConnectionId: 'retail_github', claimIds: ['claim_mysql_net_sales_legacy', 'claim_github_net_sales'],
    affectedObjectCodes: ['metric_net_sales', 'rule_completed_refund'],
    difference: '旧日报在退货发起时提前冲减；当前指标 SQL 仅扣减已完成退款。',
  },
  {
    findingId: 'finding_bot_filter', topic: 'BOT_FILTER', title: '机器人与压测流量过滤冲突',
    triggerConnectionId: 'retail_elasticsearch', claimIds: ['claim_github_bot_filter', 'claim_elasticsearch_bot_filter_legacy'],
    affectedObjectCodes: ['metric_search_conversion_rate', 'rule_search_bot_exclusion'],
    difference: '当前 SQL 排除 bot 和 internal_test；旧搜索看板只排除 bot。',
  },
  {
    findingId: 'finding_business_day', topic: 'BUSINESS_DAY', title: '营业日归属冲突',
    triggerConnectionId: 'retail_kafka', claimIds: ['claim_sharepoint_business_day', 'claim_minio_business_day', 'claim_kafka_business_day_legacy'],
    affectedObjectCodes: ['order_business_time', 'dim_business_date'],
    difference: '正式制度按跨自然日的门店营业日归属；旧事件消费端曾按自然日归属。',
  },
];

export function buildRetailRegistryBlockers(connectionIds: string[], revision: 1 | 2): RetailRegistryBlocker[] {
  const present = new Set(connectionIds);
  const availableEvidence = new Set(retailEvidenceForRevision(revision)
    .filter((entry) => present.has(entry.connectionId)).map((entry) => entry.evidenceId));
  const availableClaims = new Map(retailEvidenceClaims.filter((claim) => present.has(claim.sourceId)
    && claim.evidenceRefs.every((evidenceId) => availableEvidence.has(evidenceId))).map((claim) => [claim.claimId, claim]));
  return retailBlockerDefinitions.flatMap((definition) => {
    const claims = definition.claimIds.flatMap((claimId) => availableClaims.get(claimId) ?? []);
    if (claims.length !== definition.claimIds.length) return [];
    return [{
      ...structuredClone(definition),
      evidenceIds: [...new Set(claims.flatMap((claim) => claim.evidenceRefs))],
    }];
  });
}

export function retailEvidenceForRevision(revision: 1 | 2) {
  return retailEvidenceEntries.filter((entry) => entry.introducedRevision <= revision);
}

export function retailEvidenceForSource(connectionId: string, revision: 1 | 2) {
  return retailEvidenceForRevision(revision).filter((entry) => entry.connectionId === connectionId);
}

export type RetailEvidenceRegistryData = {
  sources: RetailEvidenceSource[];
  entries: RetailEvidenceEntry[];
  claims: RetailEvidenceClaim[];
  supportEdges: RetailObjectSupportEdge[];
};

export function assertRetailEvidenceRegistry(input: RetailEvidenceRegistryData = {
  sources: retailEvidenceSources,
  entries: retailEvidenceEntries,
  claims: retailEvidenceClaims,
  supportEdges: retailObjectSupportEdges,
}) {
  const sourceIds = new Set<string>();
  for (const source of input.sources) {
    if (sourceIds.has(source.connectionId)) throw new Error(`来源编码重复：${source.connectionId}`);
    sourceIds.add(source.connectionId);
  }
  for (const source of input.sources) {
    for (const upstreamId of source.upstreamConnectionIds ?? []) {
      if (!sourceIds.has(upstreamId)) throw new Error(`来源 ${source.connectionId} 引用了不存在的上游来源 ${upstreamId}`);
    }
    if (source.authority === 'DERIVED' && !source.upstreamConnectionIds?.length) {
      throw new Error(`派生来源缺少上游：${source.connectionId}`);
    }
  }
  const evidenceIds = new Set<string>();
  const evidenceById = new Map<string, RetailEvidenceEntry>();
  for (const entry of input.entries) {
    if (!sourceIds.has(entry.connectionId)) throw new Error(`证据 ${entry.evidenceId} 引用了不存在的来源 ${entry.connectionId}`);
    if (evidenceIds.has(entry.evidenceId)) throw new Error(`证据编码重复：${entry.evidenceId}`);
    if (!entry.statement.trim()) throw new Error(`证据 ${entry.evidenceId} 的语义陈述为空`);
    if (!entry.section.trim()) throw new Error(`证据 ${entry.evidenceId} 的定位章节为空`);
    if (!entry.locator || typeof entry.locator !== 'object' || Object.keys(entry.locator).length === 0) {
      throw new Error(`证据 ${entry.evidenceId} 的Locator为空`);
    }
    evidenceIds.add(entry.evidenceId);
    evidenceById.set(entry.evidenceId, entry);
  }
  const expectedEvidenceIds = Array.from({ length: 25 }, (_item, index) => `GR${String(index + 1).padStart(3, '0')}`);
  if (JSON.stringify([...evidenceIds].sort()) !== JSON.stringify(expectedEvidenceIds)) {
    throw new Error('Evidence Registry必须恰好包含GR001-GR025');
  }
  const claimsById = new Map<string, RetailEvidenceClaim>();
  for (const claim of input.claims) {
    if (claimsById.has(claim.claimId)) throw new Error(`Claim编码重复：${claim.claimId}`);
    const source = input.sources.find((item) => item.connectionId === claim.sourceId);
    if (!source) throw new Error(`Claim ${claim.claimId} 引用了不存在的来源 ${claim.sourceId}`);
    if (!claim.evidenceRefs.length) throw new Error(`Claim ${claim.claimId} 缺少证据`);
    for (const evidenceId of claim.evidenceRefs) {
      const evidence = evidenceById.get(evidenceId);
      if (!evidence) throw new Error(`Claim ${claim.claimId} 引用了不存在的证据 ${evidenceId}`);
      if (evidence.connectionId !== claim.sourceId) throw new Error(`Claim ${claim.claimId} 的证据 ${evidenceId} 不属于来源 ${claim.sourceId}`);
    }
    if (claim.authority === 'DERIVED' && !claim.upstreamClaimIds?.length) throw new Error(`派生Claim缺少上游：${claim.claimId}`);
    if (source.authority === 'DERIVED' && claim.authority !== 'DERIVED') throw new Error(`派生来源的Claim权威等级错误：${claim.claimId}`);
    claimsById.set(claim.claimId, claim);
  }
  for (const claim of input.claims) {
    const source = input.sources.find((item) => item.connectionId === claim.sourceId)!;
    for (const upstreamClaimId of claim.upstreamClaimIds ?? []) {
      const upstream = claimsById.get(upstreamClaimId);
      if (!upstream) throw new Error(`Claim ${claim.claimId} 引用了不存在的上游Claim ${upstreamClaimId}`);
      if (!source.upstreamConnectionIds?.includes(upstream.sourceId)) {
        throw new Error(`Claim ${claim.claimId} 的上游Claim不属于声明的上游来源 ${upstream.sourceId}`);
      }
    }
  }
  const supportKeys = new Set<string>();
  for (const edge of input.supportEdges) {
    const key = `${edge.objectKind}:${edge.ownerCode ?? ''}:${edge.objectCode}`;
    if (supportKeys.has(key)) throw new Error(`support edge重复：${key}`);
    supportKeys.add(key);
    if (!edge.supportClaimIds.length) throw new Error(`support edge缺少Claim：${key}`);
    if (!edge.supportAspect || !edge.allowedTopics.length) throw new Error(`support edge缺少支持面或允许topic：${key}`);
    for (const claimId of edge.supportClaimIds) {
      const claim = claimsById.get(claimId);
      if (!claim) throw new Error(`support edge引用不存在的Claim：${key}:${claimId}`);
      if (!edge.allowedTopics.includes(claim.topic)) {
        throw new Error(`support edge的Claim topic超出允许范围：${key}:${claimId}:${claim.topic}`);
      }
    }
  }
  return true;
}

function rootConnectionIds(connectionId: string, present: Set<string>, path = new Set<string>()): string[] {
  if (path.has(connectionId)) throw new Error(`来源血缘存在循环：${connectionId}`);
  const source = retailEvidenceSources.find((item) => item.connectionId === connectionId);
  if (!source) throw new Error(`来源不存在：${connectionId}`);
  if (!source.upstreamConnectionIds?.length) return [connectionId];
  const next = new Set(path); next.add(connectionId);
  return source.upstreamConnectionIds.flatMap((upstreamId) =>
    present.has(upstreamId) ? rootConnectionIds(upstreamId, present, next) : []);
}

export function buildRetailEvidenceStory(): RetailEvidenceStoryBatch[] {
  const present = new Set<string>();
  return retailEvidenceSources.map((source, index) => {
    present.add(source.connectionId);
    const evidence = retailEvidenceEntries.filter((entry) => present.has(entry.connectionId));
    const evidenceIds = new Set(evidence.map((entry) => entry.evidenceId));
    const claims = retailEvidenceClaims.filter((claim) =>
      present.has(claim.sourceId) && claim.evidenceRefs.every((evidenceId) => evidenceIds.has(evidenceId)));
    const findings: RetailEvidenceStoryBatch['findings'] = buildRetailRegistryBlockers([...present], 2)
      .filter((finding) => finding.triggerConnectionId === source.connectionId)
      .map((finding) => ({
        findingId: finding.findingId, topic: finding.topic, title: finding.title, severity: 'BLOCKER',
        claimIds: [...finding.claimIds], affectedObjectCodes: [...finding.affectedObjectCodes],
      }));
    const lineageGaps = retailEvidenceSources.filter((item) => present.has(item.connectionId) && item.upstreamConnectionIds?.some((upstreamId) => !present.has(upstreamId)))
      .map((item) => ({ sourceId: item.connectionId, missingUpstreamSourceIds: item.upstreamConnectionIds!.filter((upstreamId) => !present.has(upstreamId)) }));
    const independentRoots = new Set([...present].flatMap((connectionId) => rootConnectionIds(connectionId, present)));
    return {
      batchId: `B0${index + 1}` as RetailEvidenceStoryBatch['batchId'], addedConnectionId: source.connectionId,
      connectionIds: [...present], evidenceIds: evidence.map((entry) => entry.evidenceId).sort(), claims: structuredClone(claims),
      findings, lineageGaps, independentRootSourceCount: independentRoots.size,
    };
  });
}

export type GuanyijiaEvidenceSource = {
  connectionId: string;
  displayName: string;
  connectorType: string;
  role: string;
  summary: string;
  objectCounts: Record<string, number>;
  snapshotIdentity: string | null;
  versionRef: string | null;
  fingerprint: string | null;
  manifestRef: string | null;
  status: 'READY' | 'NEEDS_CONFIRMATION';
  upstreamConnectionIds?: string[];
};

export const guanyijiaEvidenceSources: GuanyijiaEvidenceSource[] = [
  {
    connectionId: 'guanyijia_mysql',
    displayName: '管伊佳 MySQL · jsh_erp', connectorType: 'MySQL', role: '物理结构与数据形态',
    summary: guanyijiaEvidenceFixture.source.summary,
    objectCounts: structuredClone(guanyijiaEvidenceFixture.manifest.objectCounts),
    snapshotIdentity: guanyijiaEvidenceFixture.manifest.snapshotId,
    versionRef: guanyijiaEvidenceFixture.manifest.evidenceVersion,
    fingerprint: guanyijiaEvidenceFixture.source.fingerprint,
    manifestRef: `fixture://${guanyijiaEvidenceFixture.source.fixtureKey}/manifest.json`,
    status: 'READY',
  },
  {
    connectionId: 'guanyijia_github',
    displayName: 'GitHub · jishenghua/jshERP', connectorType: 'GitHub', role: '业务逻辑与实际使用',
    summary: guanyijiaRepositoryEvidenceFixture.source.summary,
    objectCounts: structuredClone(guanyijiaRepositoryEvidenceFixture.manifest.objectCounts),
    snapshotIdentity: guanyijiaRepositoryEvidenceFixture.manifest.snapshotId,
    versionRef: guanyijiaRepositoryEvidenceFixture.manifest.commit,
    fingerprint: guanyijiaRepositoryEvidenceFixture.source.fingerprint,
    manifestRef: `fixture://${guanyijiaRepositoryEvidenceFixture.source.fixtureKey}/manifest.json`,
    status: 'READY',
  },
  {
    connectionId: 'guanyijia_sharepoint', displayName: 'SharePoint · 管伊佳业务制度', connectorType: 'SharePoint',
    role: '业务制度与术语', summary: '尚未绑定真实 SharePoint manifest。', objectCounts: {},
    snapshotIdentity: null, versionRef: null, fingerprint: null, manifestRef: null, status: 'NEEDS_CONFIRMATION',
  },
  {
    connectionId: 'guanyijia_semantica', displayName: 'Semantica · 管伊佳术语图', connectorType: 'RDF/SPARQL',
    role: 'SharePoint 派生术语图', summary: '等待真实 SharePoint 上游后再采集派生图。', objectCounts: {},
    snapshotIdentity: null, versionRef: null, fingerprint: null, manifestRef: null,
    status: 'NEEDS_CONFIRMATION', upstreamConnectionIds: ['guanyijia_sharepoint'],
  },
];

const guanyijiaStoryClaims = [
  { claimId: 'claim_guanyijia_mysql_material', sourceId: 'guanyijia_mysql', assertion: '部署数据库包含 jsh_material 表。', evidenceRefs: ['mysql_jsh_erp@v1:TABLE:jsh_material'] },
  { claimId: 'claim_guanyijia_mysql_depot_item', sourceId: 'guanyijia_mysql', assertion: '部署数据库包含 jsh_depot_item 表。', evidenceRefs: ['mysql_jsh_erp@v1:TABLE:jsh_depot_item'] },
  { claimId: 'claim_guanyijia_mysql_extensions', sourceId: 'guanyijia_mysql', assertion: '部署数据库包含源码外扩展表。', evidenceRefs: ['mysql_jsh_erp@v1:TABLE:biz_bill_item_fact'] },
  { claimId: 'claim_guanyijia_github_material', sourceId: 'guanyijia_github', assertion: '固定 Commit 的 DDL 定义 jsh_material 表。', evidenceRefs: ['github_jshERP@v1:TABLE:jsh_material'] },
  { claimId: 'claim_guanyijia_github_depot_item', sourceId: 'guanyijia_github', assertion: '固定 Commit 的 DDL 定义 jsh_depot_item 表。', evidenceRefs: ['github_jshERP@v1:TABLE:jsh_depot_item'] },
  { claimId: 'claim_guanyijia_github_depot_head', sourceId: 'guanyijia_github', assertion: '固定 Commit 的 DDL 定义当前欠款字段口径。', evidenceRefs: ['github_jshERP@v1:TABLE:jsh_depot_head'] },
];

export function buildGuanyijiaEvidenceStory() {
  const present = new Set<string>();
  return guanyijiaEvidenceSources.map((source, index) => {
    present.add(source.connectionId);
    return {
      batchId: `B0${index + 1}`,
      addedConnectionId: source.connectionId,
      connectionIds: [...present],
      sources: guanyijiaEvidenceSources.filter((item) => present.has(item.connectionId)).map((item) => structuredClone(item)),
      claims: guanyijiaStoryClaims.filter((claim) => present.has(claim.sourceId)).map((claim) => structuredClone(claim)),
    };
  });
}

export type VisibleObjectEvidenceBinding = {
  objectCode: string;
  evidenceRefs: string[];
  status: string;
};

export function validateVisibleObjectEvidence(bindings: VisibleObjectEvidenceBinding[], availableEvidenceIds: Set<string>) {
  const invalidEvidenceRefs = bindings.flatMap((binding) => binding.evidenceRefs
    .filter((evidenceId) => !availableEvidenceIds.has(evidenceId))
    .map((evidenceId) => ({ objectCode: binding.objectCode, evidenceId })));
  const unboundObjectCodes = bindings
    .filter((binding) => !binding.evidenceRefs.length && binding.status !== 'NEEDS_CONFIRMATION')
    .map((binding) => binding.objectCode);
  return { valid: invalidEvidenceRefs.length === 0 && unboundObjectCodes.length === 0, invalidEvidenceRefs, unboundObjectCodes };
}

export function candidateModelEvidenceBindings(candidate: CandidateModelSnapshot): VisibleObjectEvidenceBinding[] {
  const bind = (prefix: string, code: string, evidenceRefs: string[], status: string): VisibleObjectEvidenceBinding => ({
    objectCode: `${prefix}:${code}`, evidenceRefs: [...new Set(evidenceRefs)], status,
  });
  return [
    ...candidate.tables.map((item) => bind(item.kind, item.code, item.evidenceIds, item.status)),
    ...candidate.fields.map((item) => bind('FIELD', `${item.ownerTableCode}.${item.code}`, item.evidenceIds, item.status)),
    ...candidate.relations.map((item) => bind('RELATION', item.code, item.evidenceIds, item.status)),
    ...candidate.dimensions.map((item) => bind('DIMENSION', item.code, item.evidenceIds, item.status)),
    ...candidate.metrics.map((item) => bind('METRIC', item.code, item.evidenceIds, item.status)),
    ...candidate.hierarchies.map((item) => bind('HIERARCHY', item.code, item.evidenceIds, item.status)),
    ...candidate.ruleCandidates.map((item) => bind('RULE', item.code, item.evidenceIds, item.status)),
    ...candidate.aliases.map((item) => bind('ALIAS', item.code, item.evidenceIds, item.status)),
    ...candidate.pendingAssets.map((item) => bind('PENDING', item.code, item.evidenceIds, item.status)),
  ];
}

export function linguanModelEvidenceBindings(model: LinguanModelSnapshot): VisibleObjectEvidenceBinding[] {
  const bind = (prefix: string, code: string, evidenceRefs: string[]): VisibleObjectEvidenceBinding => ({
    objectCode: `${prefix}:${code}`,
    evidenceRefs: [...new Set(evidenceRefs)],
    status: evidenceRefs.length ? 'VERIFIED' : 'NEEDS_CONFIRMATION',
  });
  return [
    ...model.entities.map((item) => bind('ENTITY', item.code, item.evidenceIds)),
    ...model.events.map((item) => bind('EVENT', item.code, item.evidenceIds)),
    ...model.fields.map((item) => bind('FIELD', `${item.ownerTableCode}.${item.code}`, item.evidenceIds)),
    ...model.relations.map((item) => bind('RELATION', item.code, item.evidenceIds)),
    ...model.dimensions.map((item) => bind('DIMENSION', item.id, item.evidenceIds)),
    ...model.metrics.map((item) => bind('METRIC', item.code, item.evidenceIds)),
    ...model.hierarchies.map((item) => bind('HIERARCHY', item.id, item.evidenceIds)),
    ...model.ruleCandidates.map((item) => bind('RULE', item.id, item.evidenceIds)),
    ...model.aliases.map((item) => bind('ALIAS', item.id, item.evidenceIds)),
    ...model.executableRules.map((item) => bind('EXECUTABLE_RULE', item.id, [])),
    ...(model.timeSemantics?.calendars ?? []).map((item) => bind('CALENDAR', item.code, [])),
    ...(model.timeSemantics?.rules ?? []).map((item) => bind('TIME_RULE', item.code, [])),
  ];
}

export function fixtureDocumentEvidenceBindings(document: FixtureDocument): VisibleObjectEvidenceBinding[] {
  const bind = (prefix: string, code: string, evidenceRefs: string[]): VisibleObjectEvidenceBinding => ({
    objectCode: `${prefix}:${code}`, evidenceRefs: [...new Set(evidenceRefs)], status: evidenceRefs.length ? 'VERIFIED' : 'NEEDS_CONFIRMATION',
  });
  return [
    ...document.artifacts.model.entity_tables.flatMap((table) => [
      bind('ENTITY', table.table_id, table.evidence_ids),
      ...table.fields.map((field) => bind('FIELD', `${table.table_id}.${field.field_id}`, field.evidence_ids)),
    ]),
    ...document.artifacts.model.event_tables.flatMap((table) => [
      bind('EVENT', table.table_id, table.evidence_ids),
      ...table.fields.map((field) => bind('FIELD', `${table.table_id}.${field.field_id}`, field.evidence_ids)),
    ]),
    ...document.artifacts.model.relationships.map((item) => bind('RELATION', item.relationship_id, item.evidence_ids)),
    ...document.artifacts.enrichment.dimensions.map((item) => bind('DIMENSION', item.dimension_id, item.evidence_ids)),
    ...document.artifacts.model.metrics.map((item) => bind('METRIC', item.metric_id, item.evidence_ids)),
    ...document.artifacts.enrichment.member_hierarchies.map((item) => bind('HIERARCHY', item.hierarchy_id, item.evidence_ids)),
    ...document.artifacts.enrichment.rule_candidates.map((item) => bind('RULE', item.rule_id, item.evidence_ids)),
    ...document.artifacts.enrichment.synonym_groups.flatMap((group) => group.aliases.map((alias, index) =>
      bind('ALIAS', `${group.group_id}.${index + 1}`, alias.evidence_ids))),
  ];
}

assertRetailEvidenceRegistry();
