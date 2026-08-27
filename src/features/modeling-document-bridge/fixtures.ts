import type { ModelingDocumentSection, StructuredModelingAssertion, ValidationIssue } from './types.ts';
import { guanyijiaCandidateModel } from '../ai-modeling/guanyijia-fixture.ts';

type DocumentFixture = {
  sections: Record<ModelingDocumentSection, string>;
  assertions: StructuredModelingAssertion[];
  warnings: ValidationIssue[];
  gaps: ValidationIssue[];
};

const guanyijiaSections: Record<ModelingDocumentSection, string> = {
  OVERVIEW: '- 来源：管伊佳 MySQL 不可变DDL快照\n- 范围：95张表、字段、索引、主键与外键\n- 说明：DDL只能确认物理结构；中文业务含义需要代码或业务资料互证。',
  GOAL: '当前只确认“梳理现有ERP结构并形成语义建模输入”。具体分析目标需要业务资料补充。',
  OBJECT: '| 候选对象 | 物理依据 | 可信度 |\n| --- | --- | --- |\n| 往来单位 | `jsh_supplier` | 名称推断，待确认 |\n| 商品 | `jsh_material` | 表、字段与代码共同支持 |\n| 商品SKU | `jsh_material_extend` | 结构推断，待确认 |',
  ACTIVITY: '| 候选活动 | 物理依据 | 说明 |\n| --- | --- | --- |\n| 采购入库 | `jsh_depot_head + jsh_depot_item` | 事件分类属于推断 |\n| 销售出库 | `jsh_depot_head + jsh_depot_item` | 需GitHub状态与类型枚举互证 |\n| 退货退库 | `jsh_depot_head + jsh_depot_item` | 需确认方向和完成时点 |',
  FIELD: '已记录物理字段、MySQL类型、主键和索引。字段中文业务名称优先采用注释；缺少注释时保留技术名称并标记待确认。',
  RELATION: '显式外键可作为观察事实；仅通过命名推断的关联不会自动确认为业务关系。',
  METRIC: '指标口径需要业务资料补充。DDL中的金额或数量字段不能单独证明聚合方式、过滤条件和时间归属。',
  QUESTION: '当前没有足够业务依据生成示例问题。',
  UNRESOLVED: '1. 确认 `jsh_supplier` 的供应商、客户、会员角色。\n2. 补充单据类型、状态迁移和完成时点。\n3. 补充指标口径、排除规则和业务目标。',
};

function markdownTable(headers: string[], rows: string[][]) {
  return [`| ${headers.join(' | ')} |`, `| ${headers.map(() => '---').join(' | ')} |`,
    ...rows.map((row) => `| ${row.map((cell) => cell.replace(/\|/g, '\\|').replace(/\n/g, ' ')).join(' | ')} |`)].join('\n');
}

function guanyijiaCombinedSections(): Record<ModelingDocumentSection, string> {
  const tableName = new Map(guanyijiaCandidateModel.tables.map((table) => [table.code, table.name]));
  const relations = guanyijiaCandidateModel.relations.map((relation) => {
    const match = relation.description.match(/([a-zA-Z0-9_]+)\.[a-zA-Z0-9_]+\s*[→-]+\s*([a-zA-Z0-9_]+)\.[a-zA-Z0-9_]+/);
    return [relation.name, tableName.get(match?.[1] ?? '') ?? match?.[1] ?? '待确认',
      tableName.get(match?.[2] ?? '') ?? match?.[2] ?? relation.target ?? '待确认', relation.description];
  });
  return {
    OVERVIEW: `- 来源：管伊佳 MySQL DDL与GitHub固定Commit\n- 覆盖：95张数据库表、32张源码核心表；19张兼容候选表，${guanyijiaCandidateModel.pendingAssets.length}张扩展表保留待归类\n- 说明：部署结构决定字段事实，Mapper与Service补充关联、状态和流程。`,
    GOAL: '建立管伊佳ERP的业务对象、单据事件、关系和候选指标；结构冲突解决前可以阅读候选，但不得提交发布。',
    OBJECT: markdownTable(['候选对象', '说明', '依据状态'], guanyijiaCandidateModel.entities.map((table) => [table.name, table.description, table.status])),
    ACTIVITY: markdownTable(['候选活动', '粒度或说明', '依据状态'], guanyijiaCandidateModel.events.map((table) => [table.name, table.description, table.status])),
    FIELD: markdownTable(['所属对象', '字段', '数据类型', '说明', '字段角色'], guanyijiaCandidateModel.fields.map((field) => [
      tableName.get(field.ownerTableCode) ?? field.ownerTableCode, field.name, field.dataType, field.description, '普通字段',
    ])),
    RELATION: markdownTable(['关系', '起点', '终点', '连接依据'], relations),
    METRIC: markdownTable(['指标', '业务说明', '公式', '依据状态'], guanyijiaCandidateModel.metrics.map((metric) => [metric.name, metric.description, metric.formula ?? '待补充', metric.status])),
    QUESTION: '1. 各仓库当前库存量和库存金额是多少？\n2. 采购入库、销售出库和退货退库的处理时效如何？',
    UNRESOLVED: `1. 源码DDL包含debt、last_debt、last_deposit，当前部署库缺少这些字段，欠款指标保持阻断。\n2. ${guanyijiaCandidateModel.pendingAssets.length}张仅数据库存在的扩展表保持待归类，不根据名称猜测业务含义。\n3. 审核中状态9已由DDL和Service确认，但常量依据仍需完善。`,
  };
}

const retailSections: Record<ModelingDocumentSection, string> = {
  OVERVIEW: '- 来源：零售经营多来源证据批次R2\n- 覆盖：交易库、代码仓库、术语图、文档库、搜索索引、对象存储和事件流\n- 派生来源不重复计算为独立佐证。',
  GOAL: '统一全渠道销售、库存、退货和促销口径，并保留每项结论的来源与冲突记录。',
  OBJECT: '| 对象 | 说明 |\n| --- | --- |\n| 商品 | 全渠道共享主数据 |\n| 门店 | 线下经营主体 |\n| 客户 | 统一客户主数据 |\n| 行政区域 | 标准地域成员层级 |',
  ACTIVITY: '| 业务活动 | 粒度 |\n| --- | --- |\n| 销售订单行 | 每个订单商品明细一行 |\n| 库存移动 | 每次商品库存增加或减少一行 |\n| 退货处理 | 每次退货状态变化一行 |',
  FIELD: '字段来自MySQL结构、MongoDB文档和Kafka Schema；角色、单位与时间语义由代码和制度文档互证。',
  RELATION: '关系端点优先由键和Join确认；图谱关系作为术语与业务关联证据，不能覆盖物理事实。',
  METRIC: '| 指标 | 口径 |\n| --- | --- |\n| 净销售额 | 销售金额扣除已完成退款；退款时点仍需人工确认 |\n| 当前库存量 | 按业务日读取最后一次有效快照 |',
  QUESTION: '1. 各门店净销售额和退货率是多少？\n2. 哪些商品低于库存下限？',
  UNRESOLVED: '1. 确认净销售额使用“已完成退款”还是“已发起退货”。\n2. 确认CLOSED订单状态是否独立保留。\n3. 确认机器人和内部压测流量排除范围。',
};

function assertions(projectId: string, combinedErp = false): StructuredModelingAssertion[] {
  if (projectId === 'guanyijia_erp' && combinedErp) return [
    ...guanyijiaCandidateModel.tables.map((table) => ({ assertionId: `gyj-table-${table.code}`, section: table.kind === 'ENTITY' ? 'OBJECT' as const : 'ACTIVITY' as const,
      statement: `${table.name}由数据库结构与源码共同识别`, provenance: 'OBSERVED' as const, evidenceRefs: table.evidenceIds })),
    ...guanyijiaCandidateModel.metrics.map((metric) => ({ assertionId: `gyj-metric-${metric.code}`, section: 'METRIC' as const,
      statement: `${metric.name}由字段与代码公式生成候选`, provenance: 'INFERRED' as const, evidenceRefs: metric.evidenceIds })),
    ...guanyijiaCandidateModel.dimensions.map((dimension) => ({ assertionId: `gyj-dimension-${dimension.code}`, section: 'FIELD' as const,
      statement: `维度候选“${dimension.name}”映射到${dimension.target}`, provenance: 'INFERRED' as const, evidenceRefs: dimension.evidenceIds })),
  ];
  if (projectId === 'guanyijia_erp') return [
    { assertionId: 'gyj-table-material', section: 'OBJECT', statement: 'jsh_material是商品候选物理表', provenance: 'OBSERVED', evidenceRefs: ['mysql_jsh_erp@v1:TABLE:jsh_material'] },
    { assertionId: 'gyj-event-inbound', section: 'ACTIVITY', statement: '通用单据可投影为采购入库事件', provenance: 'INFERRED', evidenceRefs: ['mysql_jsh_erp@v1:TABLE:jsh_depot_head', 'mysql_jsh_erp@v1:TABLE:jsh_depot_item'] },
  ];
  return [
    { assertionId: 'retail-product', section: 'OBJECT', statement: '商品是全渠道共享主数据', provenance: 'OBSERVED', evidenceRefs: ['GR001', 'GR009'] },
    { assertionId: 'retail-net-sales', section: 'METRIC', statement: '净销售额扣减已完成退款', provenance: 'INFERRED', evidenceRefs: ['GR004', 'GR017'] },
  ];
}

export function modelingDocumentFixture(projectId: string, snapshotIds: string[] = []): DocumentFixture {
  const isErp = projectId === 'guanyijia_erp';
  const combinedErp = isErp && snapshotIds.some((snapshotId) => /github/i.test(snapshotId));
  return {
    sections: structuredClone(isErp ? combinedErp ? guanyijiaCombinedSections() : guanyijiaSections : retailSections),
    assertions: assertions(projectId, combinedErp),
    warnings: isErp ? combinedErp
      ? [{ code: 'STRUCTURE_CONFLICT', message: '源码与部署库的欠款字段存在真实结构冲突' }]
      : [{ code: 'DDL_SEMANTIC_INFERENCE', message: '仅凭DDL推断的业务名称和事件分类必须人工确认' }]
      : [{ code: 'SOURCE_CONFLICTS', message: '3项跨来源冲突需要人工确认' }],
    gaps: isErp && !combinedErp ? [{ code: 'MISSING_BUSINESS_DEFINITION', message: '缺少指标、规则和业务目标依据' }] : [],
  };
}
