import { createStoredZip } from '../ai-modeling/source-bundle.ts';
import type { EvidenceRecord, SemanticCandidate, SemanticCandidateKind, SemanticEvidencePackage, SemanticReviewBook } from './types.ts';

const kindLabels: Record<SemanticCandidateKind, string> = {
  ENTITY: '实体', EVENT: '事件', FIELD: '字段', RELATION: '关系', DIMENSION: '维度', METRIC: '指标',
  HIERARCHY: '层级', RULE: '业务规则候选', ALIAS: '同义词', TIME_SEMANTIC: '时间语义',
};

const candidateStatusLabels: Record<SemanticCandidate['status'], string> = {
  RECOMMENDED: '建议纳入后续建模',
  VERIFY: '需要补充确认',
  BLOCKED: '暂不进入建模',
};

function lineRange(lineStart?: number, lineEnd?: number) {
  if (lineStart === undefined) return '';
  return lineEnd && lineEnd !== lineStart ? ` 第 ${lineStart}–${lineEnd} 行` : ` 第 ${lineStart} 行`;
}

function materialLocation(record: EvidenceRecord) {
  const { locator } = record;
  if (locator.kind === 'FILE') return `文件：${locator.sourceFile}${locator.section ? ` · ${locator.section}` : ''}${lineRange(locator.lineStart, locator.lineEnd)}`;
  if (locator.kind === 'GITHUB') return `代码：${locator.path}${lineRange(locator.lineStart, locator.lineEnd)}`;
  if (locator.kind === 'SHAREPOINT') return `文档：${locator.path}${locator.section ? ` · ${locator.section}` : ''}`;
  if (locator.kind === 'DATABASE') return `数据库：${[locator.schema, locator.table, locator.field].filter(Boolean).join(' · ')}`;
  if (locator.kind === 'RDF') return '术语图中的关联记录';
  if (locator.kind === 'IMAGE') return `图片资料：${locator.sourceFile}`;
  if (locator.kind === 'MONGODB') return `文档库：${locator.collection}${locator.jsonPath ? ` · ${locator.jsonPath}` : ''}`;
  if (locator.kind === 'ELASTICSEARCH') return `搜索索引：${locator.index}${locator.mappingPath ? ` · ${locator.mappingPath}` : ''}`;
  if (locator.kind === 'OBJECT') return `已保存文件：${locator.objectKey}`;
  if (locator.kind === 'KAFKA') return `事件主题：${locator.topic}`;
  return '已保存资料';
}

function materialList(pkg: SemanticEvidencePackage, evidenceRefs: readonly string[]) {
  const materialByRef = new Map(pkg.records.map((record) => [record.evidenceId, record]));
  const items = evidenceRefs.flatMap((reference) => {
    const record = materialByRef.get(reference);
    return record ? [`${record.connectionName} · ${materialLocation(record)}`] : [];
  });
  return [...new Set(items)].join('；') || '暂无可定位材料';
}

function candidateSection(pkg: SemanticEvidencePackage, item: SemanticCandidate) {
  return `## ${item.name}\n\n- 类型：${kindLabels[item.kind]}\n- 当前处理：${candidateStatusLabels[item.status]}\n- 说明：${item.summary}\n- 支持材料：${materialList(pkg, item.evidenceRefs)}\n`;
}

function candidateDocument(pkg: SemanticEvidencePackage, kinds: SemanticCandidateKind[], title: string) {
  const candidates = pkg.candidates.filter((item) => kinds.includes(item.kind));
  return `# ${title}\n\n共 ${candidates.length} 项。\n\n${candidates.map((item) => candidateSection(pkg, item)).join('\n\n') || '当前没有此类候选。'}\n`;
}

function findings(pkg: SemanticEvidencePackage) {
  return `# 来源比较\n\n${pkg.findings.map((item) => `## ${item.title}\n\n- 参与来源：${item.participants.map((participant) => participant.connectionName).join('、') || '无'}\n- 一致点：${item.agreement ?? '暂无一致结论'}\n- 差异：${item.difference ?? '暂无明显差异'}\n- 下一步：${item.severity === 'BLOCKER' ? '补充确认后再决定是否进入建模。' : '继续保留为后续审阅材料。'}\n`).join('\n')}`;
}

export function generateSemanticReviewBook(pkg: SemanticEvidencePackage): SemanticReviewBook {
  const files: Record<string, string> = {
    'review/README.md': `# ${pkg.title}\n\n- 已保存材料：${pkg.records.length} 条\n- 审阅结论：${pkg.claims.length} 条\n- 来源比较：${pkg.findings.length} 项\n- 建模建议：${pkg.candidates.length} 项\n\n本说明书只展示便于阅读和复核的内容；原始技术记录仍由系统保留。\n`,
    'review/01-来源清单.md': `# 来源清单\n\n${[...new Map(pkg.records.map((record) => [record.connectionName, 0])).keys()].map((name) => `- ${name}：${pkg.records.filter((record) => record.connectionName === name).length} 条已保存材料`).join('\n')}\n`,
    'review/02-材料位置.md': `# 材料位置\n\n${pkg.records.map((record) => `- ${record.connectionName} · ${materialLocation(record)}`).join('\n')}\n`,
    'review/03-审阅结论.md': `# 审阅结论\n\n${pkg.claims.map((claim) => `- ${claim.predicateLabel}：${claim.value}（${claim.independentSourceCount} 个独立来源）`).join('\n')}\n`,
    'review/04-互证结论.md': findings(pkg),
    'review/model/实体.md': candidateDocument(pkg, ['ENTITY'], '实体候选'),
    'review/model/事件.md': candidateDocument(pkg, ['EVENT'], '事件候选'),
    'review/model/字段.md': candidateDocument(pkg, ['FIELD'], '字段候选'),
    'review/model/关系.md': candidateDocument(pkg, ['RELATION'], '关系候选'),
    'review/model/维度.md': candidateDocument(pkg, ['DIMENSION'], '维度候选'),
    'review/model/指标.md': candidateDocument(pkg, ['METRIC'], '指标候选'),
    'review/model/层级与同义词.md': candidateDocument(pkg, ['HIERARCHY', 'ALIAS'], '层级与同义词候选'),
    'review/05-业务规则候选.md': `# 业务规则候选\n\n> 以下内容仅为候选，不可直接执行；必须进入个人草稿并经过审核。\n\n${pkg.candidates.filter((item) => item.kind === 'RULE').map((item) => candidateSection(pkg, item)).join('\n\n') || '当前没有规则候选。'}\n`,
    'review/06-待确认事项.md': `# 待确认事项\n\n${pkg.candidates.filter((item) => item.status !== 'RECOMMENDED').map((item) => `- ${item.name}：${candidateStatusLabels[item.status]}。${item.summary}`).join('\n') || '无'}\n`,
    'review/07-决定与模型变化.md': '# 决定与模型变化\n\n当前为独立示例批次，尚未写入个人草稿。\n',
    'review/08-验证结果.md': `# 验证结果\n\n- 校验状态：${pkg.validation.valid ? '通过' : '未通过'}\n- 未知谓词：${pkg.unknownPredicates.length}\n- 独立根来源：${pkg.validation.independentRootSourceCount}\n`,
  };
  const encoder = new TextEncoder();
  const zipBytes = createStoredZip(Object.entries(files).map(([name, content]) => ({ name, bytes: encoder.encode(content) })));
  return { files, zipBytes };
}
