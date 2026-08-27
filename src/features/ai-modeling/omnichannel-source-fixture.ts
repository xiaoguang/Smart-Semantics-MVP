import { sha256HexSync } from './sha256.ts';
import { createStoredZip, type ReconciliationFinding, type SourceAsset, type SourceEvidenceLocator } from './source-bundle.ts';

const text = (value: string) => new TextEncoder().encode(value);
const base64 = (value: string) => Uint8Array.from(atob(value), (character) => character.charCodeAt(0));

const fileEntries = [
  {
    name: '01-全渠道零售业务蓝图.pdf',
    bytes: text('%PDF-1.4\n% 全渠道零售业务蓝图\n1 0 obj <</Title (Omnichannel Retail Blueprint)>> endobj\n%%EOF'),
    summary: '说明商品、门店、客户、销售渠道，以及销售、库存、退货和促销核销流程。',
    roles: ['BUSINESS_DEFINITION'] as const,
    evidenceIds: ['E001', 'E002', 'E003'],
  },
  {
    name: '02-零售经营指标口径.wps',
    bytes: text('PK\u0003\u0004WPS-DOCUMENT\n销售金额、净销售额、退货率、毛利额等指标口径。'),
    summary: '定义 10 个经营指标、聚合方式和退款扣减口径。',
    roles: ['BUSINESS_DEFINITION'] as const,
    evidenceIds: ['E004', 'E005', 'E006'],
  },
  {
    name: '03-零售术语与同义词.md',
    bytes: text('# 零售术语与同义词\n\n商品：货品、SKU\n门店：店铺、销售网点\n净销售额：实收销售额、净营收\n'),
    summary: '提供实体、事件、字段、维度、指标和规则候选的业务别名。',
    roles: ['BUSINESS_DEFINITION'] as const,
    evidenceIds: ['E007'],
  },
  {
    name: '04-门店经营看板.png',
    bytes: base64('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='),
    summary: '辅助确认看板常用的门店、销售渠道、库存和毛利分析维度。',
    roles: ['AUXILIARY_EVIDENCE'] as const,
    evidenceIds: ['E008'],
  },
  {
    name: '05-行政区域层级.jpeg',
    bytes: base64('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAEf/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABAf/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPxB//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPxB//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxB//9k='),
    summary: '辅助确认大区、省、市、区县的成员层级示例。',
    roles: ['AUXILIARY_EVIDENCE'] as const,
    evidenceIds: ['E009'],
  },
].map((entry) => ({ ...entry, sha256: sha256HexSync(entry.bytes) }));

export const omnichannelSourcePack = {
  name: '全渠道零售经营建模资料包-v1.zip',
  fileEntries,
  bytes: createStoredZip(fileEntries),
};

const fileAssets: SourceAsset[] = fileEntries.map((entry, index) => ({
  sourceId: `source-file-${index + 1}`,
  displayName: entry.name,
  origin: 'FILE_UPLOAD',
  contentKinds: [entry.name.split('.').pop()?.toUpperCase() ?? 'FILE'],
  roles: [...entry.roles],
  fingerprint: `sha256:${entry.sha256}`,
  fixtureKey: `omnichannel/file/${index + 1}`,
  status: 'READY',
  summary: entry.summary,
  evidenceIds: entry.evidenceIds,
}));

export const omnichannelSourceAssets: SourceAsset[] = [
  ...fileAssets,
  {
    sourceId: 'source-mysql', displayName: 'MySQL · retail_core_prod', origin: 'DATABASE_CONNECTOR',
    contentKinds: ['DDL', 'TABLE', 'FIELD', 'KEY', 'TYPE'], roles: ['PHYSICAL_STRUCTURE'],
    fingerprint: 'snapshot:mysql:retail_core_prod:2026-08-01', fixtureKey: 'omnichannel/mysql', status: 'READY',
    summary: '已读取 10 张业务表、72 个字段及主外键结构快照。', evidenceIds: ['E010', 'E011'],
  },
  {
    sourceId: 'source-snowflake', displayName: 'Snowflake · retail_analytics_prod', origin: 'DATABASE_CONNECTOR',
    contentKinds: ['DML', 'QUERY_SAMPLE', 'JOIN', 'CALCULATION'], roles: ['ACTUAL_USAGE'],
    fingerprint: 'snapshot:snowflake:retail_analytics_prod:2026-08-01', fixtureKey: 'omnichannel/snowflake', status: 'READY',
    summary: '已读取经营分析查询样本、关联方式、过滤条件和计算表达式。', evidenceIds: ['E012', 'E013'],
  },
  {
    sourceId: 'source-github', displayName: 'GitHub · retail-platform/retail-analytics', origin: 'CODE_REPOSITORY',
    contentKinds: ['MIGRATION', 'ENUM', 'RULE', 'ANALYTIC_SQL'], roles: ['ACTUAL_USAGE'],
    fingerprint: 'git:retail-platform/retail-analytics:a91f2c7', fixtureKey: 'omnichannel/github', status: 'READY',
    summary: '已读取 main · a91f2c7 中的迁移、订单状态枚举、规则和分析 SQL。', evidenceIds: ['E014', 'E015'],
  },
];

export const omnichannelEvidenceLocators: Record<string, SourceEvidenceLocator> = {
  E001: { kind: 'FILE', sourceFile: fileEntries[0].name, page: 3, section: '业务对象' },
  E002: { kind: 'FILE', sourceFile: fileEntries[0].name, page: 7, section: '销售与退货流程' },
  E003: { kind: 'FILE', sourceFile: fileEntries[0].name, page: 11, section: '促销核销' },
  E004: { kind: 'FILE', sourceFile: fileEntries[1].name, section: '销售指标', lineStart: 18, lineEnd: 34 },
  E005: { kind: 'FILE', sourceFile: fileEntries[1].name, section: '库存指标', lineStart: 36, lineEnd: 45 },
  E006: { kind: 'FILE', sourceFile: fileEntries[1].name, section: '退货与促销指标', lineStart: 47, lineEnd: 61 },
  E007: { kind: 'FILE', sourceFile: fileEntries[2].name, section: '术语与同义词', lineStart: 1, lineEnd: 28 },
  E008: { kind: 'IMAGE', sourceFile: fileEntries[3].name, region: { x: 80, y: 120, width: 920, height: 480 } },
  E009: { kind: 'IMAGE', sourceFile: fileEntries[4].name, region: { x: 40, y: 36, width: 760, height: 420 } },
  E010: { kind: 'DATABASE', datasource: 'retail_core_prod', schema: 'retail_core', table: 'sales_order_line' },
  E011: { kind: 'DATABASE', datasource: 'retail_core_prod', schema: 'retail_core', table: 'inventory_movement' },
  E012: { kind: 'DATABASE', datasource: 'retail_analytics_prod', schema: 'analytics', queryId: 'query_net_sales_v7' },
  E013: { kind: 'DATABASE', datasource: 'retail_analytics_prod', schema: 'analytics', queryId: 'query_promo_redemption_v3' },
  E014: { kind: 'GITHUB', repository: 'retail-platform/retail-analytics', commit: 'a91f2c7', path: 'models/order_status.sql', lineStart: 18, lineEnd: 39 },
  E015: { kind: 'GITHUB', repository: 'retail-platform/retail-analytics', commit: 'a91f2c7', path: 'rules/net_sales.sql', lineStart: 8, lineEnd: 27 },
};

export function createOmnichannelFindings(): ReconciliationFinding[] {
  return [
    {
      findingId: 'finding-net-sales-refund', title: '净销售额的退款扣减时点不一致',
      conclusion: '指标文档要求扣减已完成退款，分析 SQL 在退货发起时即扣减。', severity: 'BLOCKER',
      sourceIds: ['source-file-2', 'source-snowflake', 'source-github'], evidenceIds: ['E004', 'E012', 'E015'],
      affectedObjects: ['净销售额', '退货率', '退货处理'], recommendationId: 'completed_refund',
      options: [
        { id: 'completed_refund', label: '扣减已完成退款（推荐）', description: '与正式指标口径一致，避免退货撤销导致销售额提前减少。' },
        { id: 'initiated_return', label: '扣减已发起退货', description: '沿用当前分析 SQL，但需同步修改正式指标口径。' },
        { id: 'keep_blocked', label: '保留阻断', description: '暂不生成可发布模型。' },
      ],
    },
    {
      findingId: 'finding-closed-order-status', title: 'CLOSED 订单状态含义不一致',
      conclusion: '领域枚举将 CLOSED 单独保留，看板口径将其并入取消订单。', severity: 'BLOCKER',
      sourceIds: ['source-file-4', 'source-github'], evidenceIds: ['E008', 'E014'],
      affectedObjects: ['订单状态', '订单数', '销售订单行'], recommendationId: 'separate_closed',
      options: [
        { id: 'separate_closed', label: '保留独立 CLOSED 状态（推荐）', description: '保持领域枚举原义，取消订单仍使用 CANCELED。' },
        { id: 'merge_cancelled', label: '并入取消订单', description: '与当前看板展示一致，但会丢失履约关闭原因。' },
        { id: 'keep_blocked', label: '保留阻断', description: '暂不生成可发布模型。' },
      ],
    },
    ...[
      ['finding-dashboard-stockout', '缺货率只有看板展示依据', ['source-file-4'], ['E008']],
      ['finding-region-levels', '区县成员样例只有图片依据', ['source-file-5'], ['E009']],
      ['finding-customer-tier', '客户等级枚举只有数据库结构依据', ['source-mysql'], ['E010']],
      ['finding-promo-validity', '促销有效期只有代码规则依据', ['source-github'], ['E014']],
      ['finding-gross-margin', '毛利额成本取值只有查询样本依据', ['source-snowflake'], ['E013']],
    ].map(([findingId, title, sourceIds, evidenceIds]) => ({
      findingId: findingId as string, title: title as string, conclusion: title as string, severity: 'WEAK' as const,
      sourceIds: sourceIds as string[], evidenceIds: evidenceIds as string[], affectedObjects: [], recommendationId: 'verify',
      options: [{ id: 'verify', label: '人工确认', description: '补充业务确认后纳入模型。' }],
    })),
    {
      findingId: 'finding-core-aligned', title: '核心对象和物理结构一致', conclusion: '业务蓝图、MySQL 结构和分析查询可相互对应。',
      severity: 'CONSISTENT', sourceIds: ['source-file-1', 'source-mysql', 'source-snowflake'], evidenceIds: ['E001', 'E010', 'E012'],
      affectedObjects: ['商品', '门店', '销售订单行'], recommendationId: 'accept', options: [{ id: 'accept', label: '接受一致结论', description: '来源之间没有实质冲突。' }],
    },
  ];
}
