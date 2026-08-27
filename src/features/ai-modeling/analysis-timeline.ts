import type { FixtureDocument } from './types.ts';

export type AnalysisTimelineLine = {
  id: string;
  label: string;
  status: 'WAITING' | 'ACTIVE' | 'DONE';
};

type TimelineOptions = { activeIndex?: number; completed?: boolean; sourceCount?: number };

function countFields(document: FixtureDocument) {
  return [...document.artifacts.model.entity_tables, ...document.artifacts.model.event_tables]
    .reduce((total, table) => total + table.fields.length, 0);
}

export function buildAnalysisTimeline(
  document: FixtureDocument,
  options: TimelineOptions = {},
): AnalysisTimelineLine[] {
  const analysis = document.artifacts.analysis;
  const model = document.artifacts.model;
  const sectionCount = document.content.match(/^##\s+/gm)?.length ?? 0;
  const sourceMode = options.sourceCount !== undefined;
  const lines = [
    sourceMode ? '正在读取来源结构……' : '正在读取文档结构……',
    `已校验 ${document.fileName}`,
    sourceMode ? `已读取 ${options.sourceCount} 个证据来源` : `已读取 ${sectionCount} 个业务章节`,
    `识别出 ${analysis.entity_candidates.length} 个实体候选、${analysis.event_candidates.length} 个事件候选、${analysis.dimension_candidates.length} 个维度候选、${analysis.metric_candidates.length} 个指标候选`,
    `已归一化为 ${model.entity_tables.length + model.event_tables.length} 张数据表、${countFields(document)} 个字段、${model.relationships.length} 条关系`,
    `已生成 ${model.metrics.length} 个最终指标和 ${document.review.items.length} 个评审对象`,
    `已划分 ${document.review.groups.length} 个业务审核组`,
  ];
  const activeIndex = Math.max(0, Math.min(options.activeIndex ?? 0, lines.length - 1));
  return lines.map((label, index) => ({
    id: `analysis-${index}`,
    label,
    status: options.completed ? 'DONE' : index < activeIndex ? 'DONE' : index === activeIndex ? 'ACTIVE' : 'WAITING',
  }));
}
