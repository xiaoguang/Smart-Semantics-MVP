import type { FixtureDocument, SourceEntityTable, SourceEventTable } from './types.ts';

export type TableKind = 'ENTITY' | 'EVENT';

export type ModelSummaryTarget =
  | { category: 'tables'; tableKind: TableKind; fieldMode?: false }
  | { category: 'tables'; fieldMode: true }
  | { category: 'relations' | 'dimensions' | 'metrics' };

export type ModelResultNavigationItem = {
  label: '实体' | '事件' | '字段' | '关系' | '维度' | '指标';
  count: number;
  target: ModelSummaryTarget;
};

export function projectTableKindCounts(document: FixtureDocument) {
  const tables = [...document.artifacts.model.entity_tables, ...document.artifacts.model.event_tables];
  return {
    ENTITY: document.artifacts.model.entity_tables.length,
    EVENT: document.artifacts.model.event_tables.length,
    FIELD: tables.reduce((sum, table) => sum + table.fields.length, 0),
  };
}

export function filterTableGroupsByKind(
  document: FixtureDocument,
  kind?: TableKind,
): Array<SourceEntityTable | SourceEventTable> {
  if (kind === 'ENTITY') return document.artifacts.model.entity_tables;
  if (kind === 'EVENT') return document.artifacts.model.event_tables;
  return [...document.artifacts.model.entity_tables, ...document.artifacts.model.event_tables];
}

export function projectModelResultNavigation(document: FixtureDocument): { items: ModelResultNavigationItem[] } {
  const counts = projectTableKindCounts(document);
  return {
    items: [
      { label: '实体', count: counts.ENTITY, target: { category: 'tables', tableKind: 'ENTITY' } },
      { label: '事件', count: counts.EVENT, target: { category: 'tables', tableKind: 'EVENT' } },
      { label: '字段', count: counts.FIELD, target: { category: 'tables', fieldMode: true } },
      { label: '关系', count: document.artifacts.model.relationships.length, target: { category: 'relations' } },
      { label: '维度', count: document.artifacts.enrichment.dimensions.length, target: { category: 'dimensions' } },
      { label: '指标', count: document.artifacts.model.metrics.length, target: { category: 'metrics' } },
    ],
  };
}
