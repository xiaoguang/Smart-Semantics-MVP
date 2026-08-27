import type { FixtureDocument } from './types.ts';
import type { PublicationCheck } from './runtime-types.ts';

export type PublicationCheckViewStatus = 'PENDING' | 'PASSED' | 'FAILED';

export type PublicationCheckItemView = {
  id: PublicationCheck['id'];
  title: string;
  description: string;
  status: PublicationCheckViewStatus;
  detail: string;
};

export type PublicationChecksView = {
  summary: string;
  completed: boolean;
  passedCount: number;
  failedCount: number;
  autoExpand: boolean;
  items: PublicationCheckItemView[];
};

type CheckDefinition = {
  id: PublicationCheck['id'];
  title: string;
  description: string;
  passedDetail(document: FixtureDocument): string;
  failedDetail(document: FixtureDocument): string;
};

function namesOrFallback(names: string[], prefix: string, suffix: string, fallback: string) {
  return names.length > 0 ? `${prefix}“${names.slice(0, 3).join('、')}”${suffix}` : fallback;
}

function invalidRelations(document: FixtureDocument) {
  const model = document.artifacts.model;
  const tableIds = new Set([...model.entity_tables, ...model.event_tables].map((table) => table.table_id));
  return model.relationships.filter((relation) => !tableIds.has(relation.from_table) || !tableIds.has(relation.to_table));
}

function tablesWithDuplicateFields(document: FixtureDocument) {
  return [...document.artifacts.model.entity_tables, ...document.artifacts.model.event_tables]
    .filter((table) => new Set(table.fields.map((field) => field.field_id)).size !== table.fields.length);
}

function metricsMissingFields(document: FixtureDocument) {
  const model = document.artifacts.model;
  const eventFields = new Map(model.event_tables.map((table) => [
    table.table_id, new Set(table.fields.map((field) => field.field_id)),
  ]));
  return model.metrics.filter((metric) => metric.measure_fields
    .some((field) => !eventFields.get(metric.event_table)?.has(field)));
}

function objectsMissingEvidence(document: FixtureDocument) {
  const model = document.artifacts.model;
  return [...model.entity_tables, ...model.event_tables, ...model.relationships, ...model.metrics]
    .filter((item) => item.evidence_ids.length === 0);
}

const definitions: CheckDefinition[] = [
  {
    id: 'RELATION_ENDPOINTS',
    title: '表之间可以正确关联',
    description: '确认每条关系都能找到对应的数据表。',
    passedDetail: (document) => `${document.artifacts.model.relationships.length} 条关系均已连接到对应数据表`,
    failedDetail: (document) => namesOrFallback(
      invalidRelations(document).map((relation) => relation.name),
      '关系', '无法连接到对应数据表，请返回关系清单处理',
      '存在无法连接到数据表的关系，请返回关系清单处理',
    ),
  },
  {
    id: 'FIELD_UNIQUENESS',
    title: '字段定义没有冲突',
    description: '确认同一张表中没有重复字段定义。',
    passedDetail: (document) => `${document.artifacts.model.entity_tables.length + document.artifacts.model.event_tables.length} 张数据表中没有重复字段定义`,
    failedDetail: (document) => namesOrFallback(
      tablesWithDuplicateFields(document).map((table) => table.name),
      '数据表', '存在重复字段定义，请返回数据表清单处理',
      '存在重复字段定义，请返回数据表清单处理',
    ),
  },
  {
    id: 'METRIC_DEPENDENCIES',
    title: '指标具备计算条件',
    description: '确认每个指标都具备计算所需字段。',
    passedDetail: (document) => `${document.artifacts.model.metrics.length} 个指标所需字段齐全`,
    failedDetail: (document) => namesOrFallback(
      metricsMissingFields(document).map((metric) => metric.name),
      '指标', '缺少计算字段，请返回指标清单处理',
      '部分指标缺少计算字段，请返回指标清单处理',
    ),
  },
  {
    id: 'SOURCE_EVIDENCE',
    title: '结果可以追溯设计资料',
    description: '确认表、关系和指标都有设计资料来源。',
    passedDetail: () => '表、关系和指标都能追溯到设计资料',
    failedDetail: (document) => namesOrFallback(
      objectsMissingEvidence(document).map((item) => item.name),
      '对象', '缺少设计资料来源，请补充资料后重试',
      '部分对象缺少设计资料来源，请补充资料后重试',
    ),
  },
];

export function projectPublicationChecks(
  document: FixtureDocument,
  checks: PublicationCheck[],
): PublicationChecksView {
  const byId = new Map(checks.map((check) => [check.id, check]));
  const completed = definitions.every((definition) => byId.has(definition.id));
  const items = definitions.map((definition): PublicationCheckItemView => {
    const check = byId.get(definition.id);
    const status: PublicationCheckViewStatus = !check ? 'PENDING' : check.passed ? 'PASSED' : 'FAILED';
    return {
      id: definition.id,
      title: definition.title,
      description: definition.description,
      status,
      detail: !check ? definition.description : check.passed ? definition.passedDetail(document) : definition.failedDetail(document),
    };
  });
  const passedCount = items.filter((item) => item.status === 'PASSED').length;
  const failedCount = items.filter((item) => item.status === 'FAILED').length;
  return {
    summary: !completed
      ? '发布时将确认 4 项内容，确保模型可以正常连接、计算和追溯。'
      : failedCount > 0 ? `${failedCount} 项需要处理` : `发布检查 ${passedCount}/4 通过`,
    completed,
    passedCount,
    failedCount,
    autoExpand: failedCount > 0,
    items,
  };
}
