import type {
  SourceDocumentBlock,
  SourceDocumentCompilation,
  StructuredValue,
} from '../guanyijia-standardization-story/types.ts';

export type SourceDocumentTraceLink = {
  blockId: string;
  assertionId: string;
  section: SourceDocumentBlock['section'];
  markdownAnchor: string;
  evidenceRefs: string[];
};

export type HumanReadableEvidence = {
  evidenceRef: string;
  title: string;
  sourceType: 'DDL' | 'SQL' | 'CODE' | 'DOCUMENT' | 'TRIPLE';
  excerpt: string;
  locationLabel: string;
  locationValue: string;
  affectedObjectRefs: string[];
  technicalDetails: Record<string, string>;
  hiddenTechnicalDetails: Record<string, string>;
};

export type SourceDocumentEvidenceCompilation = Pick<SourceDocumentCompilation,
  'sourceId' | 'blocks' | 'evidenceLocators'>;

type SourceDocumentTraceCompilation = Pick<SourceDocumentCompilation, 'blocks'>
  & Partial<Pick<SourceDocumentCompilation, 'assertions'>>;

const shortNames: Record<string, string> = {
  guanyijia_mysql: '数据库',
  guanyijia_github: 'GitHub代码仓库',
  guanyijia_official_docs: '业务说明',
  guanyijia_demo_policy: 'ERP管理制度',
  guanyijia_semantica_demo: '企业术语图',
};

export function displaySourceName(sourceId: string, fallback?: string) {
  return shortNames[sourceId] ?? fallback?.replaceAll('管伊佳', '').replaceAll('部署', '').trim() ?? sourceId;
}

export function sourceClassLabel(sourceClass: 'REAL' | 'DEMO_POLICY' | 'DERIVED') {
  if (sourceClass === 'REAL') return '已保存来源';
  if (sourceClass === 'DEMO_POLICY') return '演示编写 · 非外部原文';
  return '派生术语图 · 不增加独立依据';
}

/** The visible provenance label belongs at the source header, not in every paragraph. */
export function sourceOriginLabel(sourceId: string) {
  if (sourceId === 'guanyijia_mysql') return '已保存数据库快照';
  if (sourceId === 'guanyijia_github') return '已保存源码片段';
  if (sourceId === 'guanyijia_official_docs') return '演示编写 · 非外部原文';
  if (sourceId === 'guanyijia_demo_policy') return '演示草案 · 非现行制度';
  if (sourceId === 'guanyijia_semantica_demo') return '派生术语图 · 不增加独立依据';
  return '已保存来源材料';
}

function textFromValue(value: StructuredValue): string {
  if (typeof value === 'string') return value;
  if (value === null || typeof value !== 'object') return String(value);
  if (Array.isArray(value)) return value.map(textFromValue).join('；');
  const record = value as Record<string, StructuredValue>;
  if (typeof record.text === 'string') return record.text;
  return Object.entries(record)
    .filter(([key]) => key !== 'normalized')
    .map(([key, entry]: [string, StructuredValue]) => `${key}：${textFromValue(entry)}`)
    .join('；');
}

function sourceTypeFor(sourceId: string, locator: Record<string, unknown>) {
  if (sourceId === 'guanyijia_semantica_demo') return 'TRIPLE' as const;
  if (sourceId === 'guanyijia_mysql') return 'DDL' as const;
  if (sourceId === 'guanyijia_official_docs' || sourceId === 'guanyijia_demo_policy') return 'DOCUMENT' as const;
  const path = String(locator.relativePath ?? '').toLowerCase();
  if (sourceId === 'guanyijia_github') {
    return String(locator.objectType ?? '') === 'DATA_ACCESS' || path.includes('mapper')
      ? 'SQL' as const
      : 'CODE' as const;
  }
  if (path.endsWith('.sql') || path.includes('/ddl/') || path.includes('/constraints/')) return 'DDL' as const;
  if (path.endsWith('.java') || path.endsWith('.xml') || path.endsWith('.vue') || path.includes('/src/')) return 'CODE' as const;
  if (path.endsWith('.md') || path.endsWith('.pdf') || path.endsWith('.docx') || path.endsWith('.wps')) return 'DOCUMENT' as const;
  return 'SQL' as const;
}

function mysqlExcerpt(fallback: string) {
  return `本次资料保存了字段和结构信息；没有保留逐行 DDL 原文，因此不能从这里核对完整建表语句。\n` +
    `\n审阅结论：${fallback}`;
}

function githubExcerpt(locator: Record<string, unknown>, fallback: string) {
  const symbol = String(locator.objectName ?? '固定代码符号');
  return `本次资料已保存与 ${symbol} 相关的代码记录和来源位置；未展示的代码不据此作判断。\n` +
    `\n审阅结论：${fallback}`;
}

function semanticaExcerpt(block: SourceDocumentBlock, fallback: string) {
  const triples: Record<string, string> = {
    'derived.dimension.partner_role': '往来单位 — 具有角色 → 客户、供应商、会员',
    'derived.activity.purchase_receipt': '采购入库 — 派生自 → ERP管理制度（演示）',
    'derived.activity.sales_delivery': '销售出库 — 派生自 → ERP管理制度（演示）',
    'derived.activity.returns': '退货退库 — 派生自 → ERP管理制度（演示）',
    'derived.dimension.document_status.nine': '状态 9 — 术语建议 → 待审核',
    'derived.rule.negative_stock': '负库存控制 — 派生建议 → 制度目标，尚未落地',
  };
  return `固定术语图三元组\n${triples[block.stableCode] ?? `${block.label} — 派生自 → ERP管理制度（演示）`}\n\n${fallback}`;
}

function humanExcerpt(
  compilation: SourceDocumentEvidenceCompilation,
  block: SourceDocumentBlock,
  locator: Record<string, unknown>,
) {
  const fallback = textFromValue(block.value);
  if (compilation.sourceId === 'guanyijia_mysql') return mysqlExcerpt(fallback);
  if (compilation.sourceId === 'guanyijia_github') return githubExcerpt(locator, fallback);
  if (compilation.sourceId === 'guanyijia_semantica_demo') return semanticaExcerpt(block, fallback);
  if (compilation.sourceId === 'guanyijia_demo_policy') return `固定制度段落\n${fallback}`;
  return `固定业务文档段落\n${fallback}`;
}

function locationFor(locator: Record<string, unknown>) {
  const path = typeof locator.relativePath === 'string' ? locator.relativePath : undefined;
  const objectName = typeof locator.objectName === 'string' ? locator.objectName : undefined;
  if (path && objectName) return { label: '来源位置', value: `${path} · ${objectName}` };
  if (path) return { label: '来源文件', value: path };
  if (objectName) return { label: '来源对象', value: objectName };
  return { label: '来源位置', value: '冻结来源中的结构化记录' };
}

export function buildSourceDocumentTraceLinks(compilation: SourceDocumentTraceCompilation): SourceDocumentTraceLink[] {
  return compilation.blocks.map((block) => ({
    blockId: block.blockId,
    assertionId: compilation.assertions?.find((assertion) => assertion.assertionId === block.blockId)?.assertionId ?? block.blockId,
    section: block.section,
    markdownAnchor: `markdown-${block.section.toLowerCase()}-${block.blockId.replaceAll(/[^a-zA-Z0-9_-]/g, '-')}`,
    evidenceRefs: [...block.evidenceRefs],
  }));
}

export function buildHumanReadableEvidence(
  compilation: SourceDocumentEvidenceCompilation,
  blockId: string,
): HumanReadableEvidence | undefined {
  const block = compilation.blocks.find((candidate) => candidate.blockId === blockId);
  if (!block) return undefined;
  const evidenceRef = block.evidenceRefs[0];
  if (!evidenceRef) return undefined;
  const locator = compilation.evidenceLocators[evidenceRef] ?? {};
  const location = locationFor(locator);
  const sourceType = sourceTypeFor(compilation.sourceId, locator);
  const technicalDetails: Record<string, string> = {};
  const hiddenTechnicalDetails: Record<string, string> = {};
  for (const [key, value] of Object.entries(locator)) {
    if (value === undefined || value === null) continue;
    const target = key === 'sha256' || key === 'kind' ? hiddenTechnicalDetails : technicalDetails;
    target[key] = String(value);
  }
  return {
    evidenceRef,
    title: block.label,
    sourceType,
    excerpt: humanExcerpt(compilation, block, locator),
    locationLabel: location.label,
    locationValue: location.value,
    affectedObjectRefs: block.affectedObjectRefs.map((ref) => `${ref.kind}：${ref.objectId}`),
    technicalDetails,
    hiddenTechnicalDetails,
  };
}
