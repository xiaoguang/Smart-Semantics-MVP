import type { EvidenceAuthority, SourceEvidenceLocator } from '../ai-modeling/source-bundle.ts';
import { createEvidenceRecord } from './evidence-record.ts';
import type { EvidenceRecord, RdfTerm, SemanticEvidenceCompilationInput } from './types.ts';

const RDF_TYPE = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type';
const RDFS_LABEL = 'http://www.w3.org/2000/01/rdf-schema#label';
const RDFS_DOMAIN = 'http://www.w3.org/2000/01/rdf-schema#domain';
const RDFS_RANGE = 'http://www.w3.org/2000/01/rdf-schema#range';
const SKOS_ALT_LABEL = 'http://www.w3.org/2004/02/skos/core#altLabel';
const SKOS_BROADER = 'http://www.w3.org/2004/02/skos/core#broader';
const CANDIDATE_KIND = 'urn:linguan:candidateKind';

const iri = (value: string): RdfTerm => ({ kind: 'IRI', value });
const literal = (value: string, language?: string, datatype?: string): RdfTerm => ({
  kind: 'LITERAL', value, ...(language ? { language } : {}), ...(datatype ? { datatype } : {}),
});

function rdf(input: {
  evidenceId: string; snapshotId: string; connectionId: string; connectionName: string;
  authority: EvidenceAuthority; subject: RdfTerm; predicate: string; object: RdfTerm; graph: string;
  locator: SourceEvidenceLocator; upstreamEvidenceIds?: string[];
}) {
  return createEvidenceRecord({
    evidenceId: input.evidenceId, snapshotId: input.snapshotId, connectionId: input.connectionId,
    connectionName: input.connectionName, authority: input.authority,
    statement: { kind: 'RDF_QUAD', quad: { subject: input.subject, predicate: iri(input.predicate), object: input.object, graph: iri(input.graph) } },
    locator: input.locator, upstreamEvidenceIds: input.upstreamEvidenceIds,
  });
}

function assertion(input: {
  evidenceId: string; snapshotId: string; connectionId: string; connectionName: string;
  authority: EvidenceAuthority; subject: string; predicate: string; value: string | number | boolean;
  locator: SourceEvidenceLocator; upstreamEvidenceIds?: string[];
}) {
  return createEvidenceRecord({
    evidenceId: input.evidenceId, snapshotId: input.snapshotId, connectionId: input.connectionId,
    connectionName: input.connectionName, authority: input.authority,
    statement: { kind: 'ASSERTION', assertion: { subject: input.subject, predicate: input.predicate, value: input.value } },
    locator: input.locator, upstreamEvidenceIds: input.upstreamEvidenceIds,
  });
}

const retailRdf = (evidenceId: string, subject: string, predicate: string, object: RdfTerm, graph: string, upstreamEvidenceIds?: string[]) => rdf({
  evidenceId, snapshotId: 'retail-semantic-example-v1', connectionId: 'retail_semantica', connectionName: '企业术语与本体图', authority: 'DERIVED',
  subject: iri(subject), predicate, object, graph,
  locator: { kind: 'RDF', endpoint: 'https://example.invalid/retail/sparql', graphUri: graph, subject, predicate, object: object.value }, upstreamEvidenceIds,
});
const retailSharePoint = (evidenceId: string, subject: string, predicate: string, value: string, section: string) => assertion({
  evidenceId, snapshotId: 'retail-sharepoint-example-v1', connectionId: 'retail_sharepoint', connectionName: '集团经营知识库', authority: 'PRIMARY',
  subject, predicate, value, locator: { kind: 'SHAREPOINT', site: 'https://example.invalid/sites/retail', driveItemId: 'demo-policy-v1', path: '/指标口径/零售经营口径.docx', section },
});
const retailMysql = (evidenceId: string, subject: string, predicate: string, value: string, table: string, field?: string) => assertion({
  evidenceId, snapshotId: 'retail-mysql-example-v1', connectionId: 'retail_mysql', connectionName: '零售交易库', authority: 'PRIMARY',
  subject, predicate, value, locator: { kind: 'DATABASE', datasource: 'retail-demo', schema: 'retail', table, field },
});
const retailGithub = (evidenceId: string, subject: string, predicate: string, value: string, path: string, lineStart: number) => assertion({
  evidenceId, snapshotId: 'retail-github-example-v1', connectionId: 'retail_github', connectionName: '零售分析代码仓库', authority: 'CORROBORATING',
  subject, predicate, value, locator: { kind: 'GITHUB', repository: 'example/retail-analytics', commit: 'a91f2c7demo0000000000000000000000000000', path, lineStart },
});

const retailRecords: EvidenceRecord[] = [
  retailSharePoint('retail-sp-net-sales-label', 'urn:retail:NetSales', 'LABEL', '净销售额', '净销售额'),
  // SharePoint保存的是原始文档片段；结构化同义词由下方Semantica altLabel产生。
  // 这样既保留血缘，也不会把派生图谱当成第二份独立业务真相。
  retailSharePoint('retail-sp-net-sales-alias', 'urn:retail:NetSales', 'SOURCE_TEXT', '业务称呼：实收销售额', '净销售额·业务称呼'),
  retailSharePoint('retail-sp-refund-timing', 'urn:retail:NetSales', 'REFUND_TIMING', '已完成退款', '净销售额·退款扣减'),
  retailSharePoint('retail-sp-customer-tier-time', 'urn:retail:CustomerTier', 'EFFECTIVE_TIME', '次日生效', '客户等级'),
  retailSharePoint('retail-sp-bot-rule', 'urn:retail:BotTrafficRule', 'EXCLUSION', '排除机器人和内部压测流量', '搜索转化率'),

  retailRdf('retail-sem-product-type', 'urn:retail:Product', RDF_TYPE, iri('http://www.w3.org/2002/07/owl#Class'), 'urn:retail:ontology'),
  retailRdf('retail-sem-product-kind', 'urn:retail:Product', CANDIDATE_KIND, literal('ENTITY'), 'urn:retail:ontology'),
  retailRdf('retail-sem-product-label', 'urn:retail:Product', RDFS_LABEL, literal('商品', 'zh-CN'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-product-alias', 'urn:retail:Product', SKOS_ALT_LABEL, literal('货品', 'zh-CN'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-product-code-kind', 'urn:retail:ProductCode', CANDIDATE_KIND, literal('FIELD'), 'urn:retail:ontology'),
  retailRdf('retail-sem-product-code-label', 'urn:retail:ProductCode', RDFS_LABEL, literal('商品编码', 'zh-CN'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-product-code-domain', 'urn:retail:ProductCode', RDFS_DOMAIN, iri('urn:retail:Product'), 'urn:retail:ontology'),
  retailRdf('retail-sem-product-code-range', 'urn:retail:ProductCode', RDFS_RANGE, iri('http://www.w3.org/2001/XMLSchema#string'), 'urn:retail:ontology'),
  retailRdf('retail-sem-sales-line-kind', 'urn:retail:SalesOrderLine', CANDIDATE_KIND, literal('EVENT'), 'urn:retail:ontology'),
  retailRdf('retail-sem-sales-line-label', 'urn:retail:SalesOrderLine', RDFS_LABEL, literal('销售订单行', 'zh-CN'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-sold-product-kind', 'urn:retail:SoldProduct', CANDIDATE_KIND, literal('RELATION'), 'urn:retail:ontology'),
  retailRdf('retail-sem-sold-product-label', 'urn:retail:SoldProduct', RDFS_LABEL, literal('销售商品', 'zh-CN'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-sold-product-domain', 'urn:retail:SoldProduct', RDFS_DOMAIN, iri('urn:retail:SalesOrderLine'), 'urn:retail:ontology'),
  retailRdf('retail-sem-sold-product-range', 'urn:retail:SoldProduct', RDFS_RANGE, iri('urn:retail:Product'), 'urn:retail:ontology'),
  retailRdf('retail-sem-category-kind', 'urn:retail:ProductCategory', CANDIDATE_KIND, literal('DIMENSION'), 'urn:retail:ontology'),
  retailRdf('retail-sem-category-label', 'urn:retail:ProductCategory', RDFS_LABEL, literal('商品品类', 'zh-CN'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-category-hierarchy-kind', 'urn:retail:ProductCategoryHierarchy', CANDIDATE_KIND, literal('HIERARCHY'), 'urn:retail:ontology'),
  retailRdf('retail-sem-category-hierarchy-label', 'urn:retail:ProductCategoryHierarchy', RDFS_LABEL, literal('商品品类层级', 'zh-CN'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-category-broader', 'urn:retail:GreenTea', SKOS_BROADER, iri('urn:retail:Tea'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-net-sales-kind', 'urn:retail:NetSales', CANDIDATE_KIND, literal('METRIC'), 'urn:retail:metrics'),
  retailRdf('retail-sem-net-sales-label', 'urn:retail:NetSales', RDFS_LABEL, literal('净销售额', 'zh-CN'), 'urn:retail:metrics', ['retail-sp-net-sales-label']),
  retailRdf('retail-sem-net-sales-alias', 'urn:retail:NetSales', SKOS_ALT_LABEL, literal('实收销售额', 'zh-CN'), 'urn:retail:metrics', ['retail-sp-net-sales-alias']),
  retailRdf('retail-sem-net-sales-aggregation', 'urn:retail:NetSales', 'urn:linguan:aggregation', literal('SUM'), 'urn:retail:metrics'),
  retailRdf('retail-sem-refund-rule-kind', 'urn:retail:RefundTiming', CANDIDATE_KIND, literal('RULE'), 'urn:retail:rules'),
  retailRdf('retail-sem-refund-rule-label', 'urn:retail:RefundTiming', RDFS_LABEL, literal('退款确认时点', 'zh-CN'), 'urn:retail:rules'),
  rdf({ evidenceId: 'retail-sem-refund-condition-node', snapshotId: 'retail-semantic-example-v1', connectionId: 'retail_semantica', connectionName: '企业术语与本体图', authority: 'DERIVED', subject: iri('urn:retail:RefundTiming'), predicate: 'urn:linguan:condition', object: { kind: 'BNODE', value: 'refund-condition-1', scope: 'retail-semantic-example-v1' }, graph: 'urn:retail:rules', locator: { kind: 'RDF', endpoint: 'https://example.invalid/retail/sparql', graphUri: 'urn:retail:rules', subject: 'urn:retail:RefundTiming', predicate: 'urn:linguan:condition', object: '_:refund-condition-1' }, upstreamEvidenceIds: ['retail-sp-refund-timing'] }),
  retailRdf('retail-sem-customer-tier-kind', 'urn:retail:CustomerTier', CANDIDATE_KIND, literal('DIMENSION'), 'urn:retail:ontology'),
  retailRdf('retail-sem-customer-tier-label', 'urn:retail:CustomerTier', RDFS_LABEL, literal('客户等级', 'zh-CN'), 'urn:retail:vocabulary'),
  retailRdf('retail-sem-bot-rule-kind', 'urn:retail:BotTrafficRule', CANDIDATE_KIND, literal('RULE'), 'urn:retail:rules'),
  retailRdf('retail-sem-bot-rule-label', 'urn:retail:BotTrafficRule', RDFS_LABEL, literal('机器人流量排除', 'zh-CN'), 'urn:retail:rules'),
  retailRdf('retail-sem-unknown-predicate', 'urn:retail:Product', 'urn:retail:needsMerchandisingReview', literal('true', undefined, 'http://www.w3.org/2001/XMLSchema#boolean'), 'urn:retail:ontology'),

  retailMysql('retail-mysql-product-table', 'urn:retail:Product', 'PHYSICAL_TABLE', 'retail.product', 'product'),
  retailMysql('retail-mysql-product-code', 'urn:retail:ProductCode', 'UNIQUE', 'true', 'product', 'product_code'),
  retailMysql('retail-mysql-sales-grain', 'urn:retail:SalesOrderLine', 'GRAIN', '每个订单商品明细一行', 'sales_order_line'),
  retailMysql('retail-mysql-category-parent', 'urn:retail:ProductCategoryHierarchy', 'PARENT_FIELD', 'parent_category_code', 'product_category', 'parent_category_code'),
  retailMysql('retail-mysql-customer-tier-time', 'urn:retail:CustomerTier', 'EFFECTIVE_TIME', '次日生效', 'customer_tier_history', 'effective_from'),

  retailGithub('retail-github-refund-timing', 'urn:retail:NetSales', 'REFUND_TIMING', '已发起退货', 'sql/net_sales.sql', 37),
  retailGithub('retail-github-sales-join', 'urn:retail:SoldProduct', 'JOIN', 'sales_order_line.product_code = product.product_code', 'mapper/SalesOrderMapper.xml', 82),
  retailGithub('retail-github-bot-rule', 'urn:retail:BotTrafficRule', 'EXCLUSION', '排除机器人和内部压测流量', 'sql/search_conversion.sql', 21),
];

const commonMappings = [
  { predicate: RDF_TYPE, semantic: 'RDF_TYPE', label: 'RDF类型' },
  { predicate: RDFS_LABEL, semantic: 'LABEL', label: '中文名称' },
  { predicate: RDFS_DOMAIN, semantic: 'DOMAIN', label: '所属对象' },
  { predicate: RDFS_RANGE, semantic: 'RANGE', label: '值类型或目标' },
  { predicate: SKOS_ALT_LABEL, semantic: 'ALIAS', label: '同义词' },
  { predicate: SKOS_BROADER, semantic: 'BROADER', label: '上级成员' },
  { predicate: CANDIDATE_KIND, semantic: 'CANDIDATE_KIND', label: '候选类型' },
  { predicate: 'urn:linguan:aggregation', semantic: 'AGGREGATION', label: '聚合方式' },
  { predicate: 'urn:linguan:condition', semantic: 'CONDITION', label: '规则条件' },
  { predicate: 'REFUND_TIMING', semantic: 'REFUND_TIMING', label: '退款确认时点', conflictSeverity: 'BLOCKER' as const, affectedObjectRefs: ['METRIC:net_sales', 'RULE:refund_timing'] },
  { predicate: 'PHYSICAL_FIELD', semantic: 'PHYSICAL_FIELD', label: '物理字段', conflictSeverity: 'BLOCKER' as const, affectedObjectRefs: ['FIELD:debt_amount'] },
  ...['LABEL', 'ALIAS', 'SOURCE_TEXT', 'PHYSICAL_TABLE', 'UNIQUE', 'GRAIN', 'PARENT_FIELD', 'EFFECTIVE_TIME', 'JOIN', 'EXCLUSION', 'ROLE'].map((predicate) => ({ predicate, semantic: predicate, label: predicate })),
];

export const retailSemanticExample: SemanticEvidenceCompilationInput = {
  exampleId: 'retail-semantic-four-source-v1', projectId: 'group_retail_ops', title: '零售经营四源语义证据示例',
  profile: {
    namedGraphs: ['urn:retail:ontology', 'urn:retail:vocabulary', 'urn:retail:metrics', 'urn:retail:rules'],
    namespaces: { retail: 'urn:retail:', linguan: 'urn:linguan:' },
    seedConcepts: ['urn:retail:Product', 'urn:retail:SalesOrderLine', 'urn:retail:NetSales'],
    predicateMappings: commonMappings, excludedPredicates: [], upstreamSourceIds: ['retail_sharepoint'],
  }, records: retailRecords,
};

const erpAssertion = (input: Parameters<typeof assertion>[0]) => assertion(input);
const erpRecords: EvidenceRecord[] = [
  erpAssertion({ evidenceId: 'erp-sp-counterparty', snapshotId: 'erp-sharepoint-example-v1', connectionId: 'guanyijia_sharepoint', connectionName: '管伊佳业务说明', authority: 'PRIMARY', subject: 'urn:guanyijia:Counterparty', predicate: 'LABEL', value: '往来单位', locator: { kind: 'SHAREPOINT', site: 'https://example.invalid/sites/erp', driveItemId: 'erp-terms-v1', path: '/业务术语/往来单位.docx', section: '往来单位角色' } }),
  erpAssertion({ evidenceId: 'erp-sp-purchase-inbound', snapshotId: 'erp-sharepoint-example-v1', connectionId: 'guanyijia_sharepoint', connectionName: '管伊佳业务说明', authority: 'PRIMARY', subject: 'urn:guanyijia:PurchaseInbound', predicate: 'LABEL', value: '采购入库', locator: { kind: 'SHAREPOINT', site: 'https://example.invalid/sites/erp', driveItemId: 'erp-flow-v1', path: '/业务流程/采购与退货.docx', section: '采购入库' } }),
  rdf({ evidenceId: 'erp-sem-counterparty-kind', snapshotId: 'erp-semantic-example-v1', connectionId: 'guanyijia_semantica', connectionName: '管伊佳术语与本体图', authority: 'DERIVED', subject: iri('urn:guanyijia:Counterparty'), predicate: CANDIDATE_KIND, object: literal('ENTITY'), graph: 'urn:guanyijia:ontology', locator: { kind: 'RDF', endpoint: 'https://example.invalid/erp/sparql', graphUri: 'urn:guanyijia:ontology', subject: 'urn:guanyijia:Counterparty', predicate: CANDIDATE_KIND, object: 'ENTITY' }, upstreamEvidenceIds: ['erp-sp-counterparty'] }),
  rdf({ evidenceId: 'erp-sem-counterparty-label', snapshotId: 'erp-semantic-example-v1', connectionId: 'guanyijia_semantica', connectionName: '管伊佳术语与本体图', authority: 'DERIVED', subject: iri('urn:guanyijia:Counterparty'), predicate: RDFS_LABEL, object: literal('往来单位', 'zh-CN'), graph: 'urn:guanyijia:vocabulary', locator: { kind: 'RDF', endpoint: 'https://example.invalid/erp/sparql', graphUri: 'urn:guanyijia:vocabulary', subject: 'urn:guanyijia:Counterparty', predicate: RDFS_LABEL, object: '往来单位' }, upstreamEvidenceIds: ['erp-sp-counterparty'] }),
  ...['供应商', '客户', '会员'].map((role, index) => erpAssertion({ evidenceId: `erp-sem-role-${index + 1}`, snapshotId: 'erp-semantic-example-v1', connectionId: 'guanyijia_semantica', connectionName: '管伊佳术语与本体图', authority: 'DERIVED', subject: 'urn:guanyijia:Counterparty', predicate: 'ROLE', value: role, locator: { kind: 'RDF', endpoint: 'https://example.invalid/erp/sparql', graphUri: 'urn:guanyijia:vocabulary', subject: 'urn:guanyijia:Counterparty', predicate: 'urn:linguan:role', object: role }, upstreamEvidenceIds: ['erp-sp-counterparty'] })),
  ...[
    ['PurchaseInbound', '采购入库', 'purchase_inbound'], ['SalesOutbound', '销售出库', 'sales_outbound'], ['ReturnToStock', '退货退库', 'return_to_stock'],
  ].flatMap(([subject, name, code], index) => [
    erpAssertion({ evidenceId: `erp-sem-event-kind-${index + 1}`, snapshotId: 'erp-semantic-example-v1', connectionId: 'guanyijia_semantica', connectionName: '管伊佳术语与本体图', authority: 'DERIVED', subject: `urn:guanyijia:${subject}`, predicate: 'CANDIDATE_KIND', value: 'EVENT', locator: { kind: 'RDF', endpoint: 'https://example.invalid/erp/sparql', graphUri: 'urn:guanyijia:ontology', subject: `urn:guanyijia:${subject}`, predicate: CANDIDATE_KIND, object: 'EVENT' }, ...(index === 0 ? { upstreamEvidenceIds: ['erp-sp-purchase-inbound'] } : {}) }),
    erpAssertion({ evidenceId: `erp-sem-event-label-${index + 1}`, snapshotId: 'erp-semantic-example-v1', connectionId: 'guanyijia_semantica', connectionName: '管伊佳术语与本体图', authority: 'DERIVED', subject: `urn:guanyijia:${subject}`, predicate: 'LABEL', value: name, locator: { kind: 'RDF', endpoint: 'https://example.invalid/erp/sparql', graphUri: 'urn:guanyijia:vocabulary', subject: `urn:guanyijia:${subject}`, predicate: RDFS_LABEL, object: name }, ...(index === 0 ? { upstreamEvidenceIds: ['erp-sp-purchase-inbound'] } : {}) }),
    erpAssertion({ evidenceId: `erp-mysql-event-${index + 1}`, snapshotId: 'erp-mysql-example-v1', connectionId: 'guanyijia_mysql', connectionName: '管伊佳 MySQL · jsh_erp', authority: 'PRIMARY', subject: `urn:guanyijia:${subject}`, predicate: 'PHYSICAL_TABLE', value: `jsh_depot_head+jsh_depot_item:${code}`, locator: { kind: 'DATABASE', datasource: 'guanyijia-demo', schema: 'jsh_erp', table: 'jsh_depot_head' } }),
  ]),
  erpAssertion({ evidenceId: 'erp-mysql-counterparty', snapshotId: 'erp-mysql-example-v1', connectionId: 'guanyijia_mysql', connectionName: '管伊佳 MySQL · jsh_erp', authority: 'PRIMARY', subject: 'urn:guanyijia:Counterparty', predicate: 'PHYSICAL_TABLE', value: 'jsh_supplier', locator: { kind: 'DATABASE', datasource: 'guanyijia-demo', schema: 'jsh_erp', table: 'jsh_supplier' } }),
  erpAssertion({ evidenceId: 'erp-github-counterparty', snapshotId: 'erp-github-example-v1', connectionId: 'guanyijia_github', connectionName: 'GitHub · jishenghua/jshERP', authority: 'CORROBORATING', subject: 'urn:guanyijia:Counterparty', predicate: 'ROLE', value: '供应商/客户/会员共用表', locator: { kind: 'GITHUB', repository: 'jishenghua/jshERP', commit: '1170e76934f6134e642783ed4401430bd34ffc45', path: 'jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/Supplier.java', lineStart: 1 } }),
  erpAssertion({ evidenceId: 'erp-mysql-debt-field', snapshotId: 'erp-mysql-example-v1', connectionId: 'guanyijia_mysql', connectionName: '管伊佳 MySQL · jsh_erp', authority: 'PRIMARY', subject: 'urn:guanyijia:DebtAmount', predicate: 'PHYSICAL_FIELD', value: '当前数据库缺失', locator: { kind: 'DATABASE', datasource: 'guanyijia-demo', schema: 'jsh_erp', table: 'jsh_depot_head', field: 'debt' } }),
  erpAssertion({ evidenceId: 'erp-github-debt-field', snapshotId: 'erp-github-example-v1', connectionId: 'guanyijia_github', connectionName: 'GitHub · jishenghua/jshERP', authority: 'CORROBORATING', subject: 'urn:guanyijia:DebtAmount', predicate: 'PHYSICAL_FIELD', value: '上游DDL存在', locator: { kind: 'GITHUB', repository: 'jishenghua/jshERP', commit: '1170e76934f6134e642783ed4401430bd34ffc45', path: 'sql/jsh_erp.sql', lineStart: 918 } }),
];

export const guanyijiaSemanticExample: SemanticEvidenceCompilationInput = {
  exampleId: 'guanyijia-semantic-four-source-v1', projectId: 'guanyijia_erp', title: '管伊佳四源语义证据示例',
  profile: {
    namedGraphs: ['urn:guanyijia:ontology', 'urn:guanyijia:vocabulary'], namespaces: { erp: 'urn:guanyijia:', linguan: 'urn:linguan:' },
    seedConcepts: ['urn:guanyijia:Counterparty', 'urn:guanyijia:PurchaseInbound'], predicateMappings: commonMappings,
    excludedPredicates: [], upstreamSourceIds: ['guanyijia_sharepoint'],
  }, records: erpRecords,
};
