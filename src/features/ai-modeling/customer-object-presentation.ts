import type { ProjectedModelObject } from './modeling-object-projection.ts';
import type { FixtureDocument } from './types.ts';

export type CustomerObjectDescription = {
  summary: string;
  usage?: string;
  confirmation?: string;
  technicalDefinition?: string;
};

export type EvidenceCitationView = {
  id: string;
  sourceFile: string;
  section: string;
  lineLabel?: string;
  quote?: string;
  missing: boolean;
};

function technicalDefinition(object: ProjectedModelObject) {
  const details = object.details.map((item) => `${item.label}：${item.value}`).join('；');
  return [`编码：${object.code}`, object.definition, details].filter(Boolean).join('；');
}

export function projectCustomerDescription(object: ProjectedModelObject): CustomerObjectDescription {
  if (object.code === 'field_movement_quantity') {
    return {
      summary: '记录一次库存增加或减少的数量。',
      usage: '结合“移动方向”判断这是入库增加还是出库减少。',
      confirmation: object.reviewStatus === 'PENDING' ? '请确认数量单位和移动方向是否符合实际业务。' : undefined,
      technicalDefinition: technicalDefinition(object),
    };
  }
  if (object.code === 'metric_inventory_amount') {
    return {
      summary: '表示当前库存的总价值。',
      usage: '先用在库数量乘以商品标准成本，再对当前库存快照求和；不同快照时间不能直接相加。',
      confirmation: object.reviewStatus === 'PENDING' ? '请确认标准成本的取值时间和有效范围。' : undefined,
      technicalDefinition: technicalDefinition(object),
    };
  }
  const risk = object.reviewStatus === 'PENDING' || object.reviewStatus === 'USER_REJECTED';
  const summary = object.definition || (object.ref.kind === 'RELATION'
    ? `${object.details.find((item) => item.label === '起点')?.value ?? '起点对象'}与${object.details.find((item) => item.label === '终点')?.value ?? '终点对象'}之间的业务关联。`
    : `${object.name}用于描述当前语义模型中的${object.subtitle}。`);
  const usage = object.ref.kind === 'FIELD' ? `作为“${object.details.find((item) => item.label === '所属表')?.value ?? '所属数据表'}”中的业务字段使用。`
    : object.ref.kind === 'METRIC' ? '用于业务分析和指标计算。'
      : object.ref.kind === 'RELATION' ? '用于连接两端数据表，支持跨对象分析。'
        : undefined;
  return {
    summary,
    usage,
    confirmation: risk ? '请结合来源资料确认定义和归属是否准确。' : undefined,
    technicalDefinition: technicalDefinition(object),
  };
}

export function projectEvidenceCitations(document: FixtureDocument, evidenceIds: string[]): EvidenceCitationView[] {
  const catalog = new Map([
    ...document.artifacts.analysis.evidence_catalog,
    ...document.artifacts.model.evidence_catalog,
  ].map((item) => [item.evidence_id, item]));
  return evidenceIds.map((id) => {
    const evidence = catalog.get(id);
    if (!evidence) return { id, sourceFile: '', section: '', missing: true };
    const lineLabel = evidence.line_start
      ? evidence.line_end && evidence.line_end !== evidence.line_start
        ? `第 ${evidence.line_start}–${evidence.line_end} 行`
        : `第 ${evidence.line_start} 行`
      : undefined;
    return {
      id,
      sourceFile: evidence.source_file,
      section: evidence.section,
      ...(lineLabel ? { lineLabel } : {}),
      quote: evidence.quote,
      missing: false,
    };
  });
}

