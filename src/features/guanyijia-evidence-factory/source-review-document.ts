import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  readCuratedSourceReview,
  type CuratedReviewEvidence,
  type CuratedReviewTrace,
} from './curated-source-review.ts';
import {
  readBoundDemoContentSourceReview,
  readLegacyDemoContentSourceReview,
  type DemoContentReviewClaim,
  type DemoContentRunBinding,
  type DemoContentSourceReview,
} from '../guanyijia-demo-content/demo-content-review.ts';
import {
  listScriptedReviewEdits,
  type ScriptedReviewEditableField,
  type ScriptedReviewEditDefinition,
} from '../data-standardization/scripted-review-edits.ts';
import { assertBusinessCopy, type BusinessCopyEntry } from '../data-standardization/business-copy-quality.ts';
import type { ReviewSummaryProjection } from '../data-standardization/review-summary.ts';

export type SourceReviewEvidenceClass =
  | 'OBSERVED'
  | 'SOURCE_NATIVE'
  | 'DEMO_AUTHORED'
  | 'DERIVED_DEMO'
  | 'FROZEN_RECORD'
  | 'GAP';
export type SourceReviewExcerptKind =
  | 'EXACT_EXCERPT'
  | 'STRUCTURED_RECORD'
  | 'DOCUMENT_SECTION'
  | 'DERIVED_GRAPH'
  | 'GAP';

export type SourceReviewEvidence = {
  evidenceRef: string;
  evidenceClass: SourceReviewEvidenceClass;
  excerptKind: SourceReviewExcerptKind;
  title: string;
  excerpt: string;
  locationLabel: string;
  locationValue: string;
  excerptSha256: `sha256:${string}`;
  affectedObjectRefs: string[];
  artifactDigest?: `sha256:${string}`;
};

export type SourceReviewTrace = {
  /** Internal transport token retained for legacy package compatibility; never rendered to reviewers. */
  linePrefix: string;
  /**
   * Stable V7 claim identity.  Legacy packages omit it and continue to use
   * their frozen line prefix; rich documents use it with `markdownAnchor` so
   * an approved wording revision never depends on an old display title.
   */
  claimId?: string;
  markdownAnchor: string;
  evidenceRefs: string[];
  /** Explicit business grouping for packaged human content. */
  section?: number;
  sectionId?: string;
  sectionPurpose?: string;
  /** Explicit readable claim metadata. Legacy packages use the verified fallback map instead. */
  claim?: {
    title: string;
    statement: string;
    focusIdentifiers: string[];
  };
};

export type SourceReviewItem = {
  id: string;
  section: number;
  title: string;
  /** The Chinese conclusion shown to a reviewer. It is never raw source text. */
  statement: string;
  summary: string;
  resultType: '事实' | '源码摘录' | '演示资料' | '派生资料' | '冻结记录' | '资料缺口';
  evidenceRefs: string[];
  markdownAnchors: string[];
  affectedObjectRefs: string[];
  formalBlockId?: string;
  scriptedEditId?: string;
};

export type ReviewClaim = {
  claimId: string;
  section: number;
  sectionId: string;
  sectionPurpose: string;
  title: string;
  statement: string;
  focusIdentifiers: string[];
  markdownAnchor: string;
  evidenceRefs: string[];
  affectedObjectRefs: string[];
  formalBlockId?: string;
  scriptedEditId?: string;
};

/** A review claim is publishable only with an explicit reading section. */
export type ReadableReviewClaim = ReviewClaim & {
  sectionId: string;
  sectionPurpose: string;
};

export type InlineEvidencePresentation = {
  evidenceRef: string;
  claimId: string;
  title: string;
  location: string;
  supports: string;
};

export type ReadableDemoDocument = {
  sourceId: string;
  title: string;
  markdown: string;
};

export type ReadableContentPublication = {
  sourceId: string;
  snapshotId: string;
  contentOrigin: NonNullable<SourceReviewDocument['contentOrigin']>;
};

/**
 * The immutable human-reading projection shared by the checklist, Markdown
 * reader and inline evidence. Formal blocks and transport markers stay out of
 * this seam.
 */
export type ValidatedReviewContentBundle = {
  documents: ReadableDemoDocument[];
  claims: ReadableReviewClaim[];
  evidence: InlineEvidencePresentation[];
  summaries: ReviewSummaryProjection[];
  publication: ReadableContentPublication;
};

type ClaimPresentation = Pick<ReviewClaim, 'title' | 'statement' | 'focusIdentifiers'> & {
  /** A human conclusion may deliberately use a narrower subset than a legacy trace. */
  evidenceRefs?: readonly string[];
  /**
   * The legacy source package uses a nine-section transport format.  Human
   * review must use an explicit business grouping instead of inheriting that
   * transport position (or a modulo-based authored-document position).
   */
  section?: number;
  /** A short, verified passage for long-form authored material. */
  sourceExcerpt?: string;
};

export type ReviewDocumentMetadata = {
  title: string;
  coverageLabel: string;
  sourceDescription: string;
  limitations: string[];
};

export type MysqlSchemaColumn = {
  name: string;
  dataType: string;
  nullable: boolean;
  defaultValue?: string;
  comment?: string;
  keyRole?: 'PRIMARY' | 'INDEX' | 'FOREIGN';
  highlighted: boolean;
};

export type ReviewTermRow = {
  name: string;
  definition: string;
  domain: string;
};

export type ReviewTermRelationRow = {
  subject: string;
  predicate: string;
  object: string;
  description: string;
};

export type ReviewEvidenceView =
  | {
      kind: 'MYSQL_SCHEMA';
      evidenceRef: string;
      claimId?: string;
      objectName: string;
      objectComment?: string;
      columns: MysqlSchemaColumn[];
      rawDdl: string;
      locationValue: string;
    }
  | {
      kind: 'SOURCE_EXCERPT';
      evidenceRef: string;
      claimId?: string;
      title: string;
      language: 'sql' | 'text';
      rawExcerpt: string;
      locationValue: string;
    }
  | {
      kind: 'DOCUMENT_SECTION';
      evidenceRef: string;
      /** The short document passage is specific to this review conclusion. */
      claimId: string;
      title: string;
      excerpt: string;
      locationValue: string;
    }
  | {
      kind: 'TERM_RELATION';
      evidenceRef: string;
      /** The displayed terms and relations are specific to this review conclusion. */
      claimId: string;
      title: string;
      terms: ReviewTermRow[];
      relations: ReviewTermRelationRow[];
      locationValue: string;
    };

export type ReviewTraceRow = {
  claimId: string;
  evidenceRef: string;
  markdownAnchor: string;
};

/**
 * A concise, business-facing entry used by the review checklist.  The raw
 * trace remains available through `evidenceRefs`, but is deliberately not a
 * separate review item: reviewers decide a conclusion, not an anchor or an
 * internal object id.
 */
export type ReviewChecklistItem = ReviewClaim & {
  sourceSummary: string;
  sourceLocation: string;
};

export type ReviewChecklistGap = ReviewChecklistItem & {
  nextStep: string;
};

export type ReviewChecklistProjection = {
  tasks: ReviewChecklistItem[];
  keyConclusions: ReviewChecklistItem[];
  gaps: ReviewChecklistGap[];
  objectDetails: ReviewChecklistItem[];
};

/**
 * The one reviewer-facing representation of a scripted change.  It contains
 * only the decision a person can make, the material that supports it and the
 * exact Markdown paragraph that will change.  Formal blocks remain an
 * execution detail of the revision chain.
 */
export type ReviewTaskPresentation = {
  taskId: string;
  claimId: string;
  current: { title: string; statement: string };
  recommendation: { title: string; statement: string };
  editableFields: Array<{
    field: ScriptedReviewEditableField;
    label: string;
    value: string;
  }>;
  reason: string;
  evidence: Array<{
    title: string;
    excerpt: string;
    location: string;
    supports: string;
  }>;
  markdownChange: {
    sectionTitle: string;
    before: string[];
    after: string[];
  };
};

export type ReviewWorkspaceProjection = {
  /** Evidence stays validated in traceRows, but is shown inline beneath its claim. */
  tabs: readonly ['审阅清单', 'Markdown 文档'];
  metadata: ReviewDocumentMetadata;
  sections: SourceReviewSection[];
  claims: ReviewClaim[];
  tasks: ReviewTaskPresentation[];
  checklist: ReviewChecklistProjection;
  markdown: {
    content: string;
    sourceContent: string;
  };
  evidenceViews: ReviewEvidenceView[];
  traceRows: ReviewTraceRow[];
  contentBundle: ValidatedReviewContentBundle;
};

/**
 * Runtime decision state is intentionally structural here: this human-reading
 * seam only needs to know whether an otherwise scripted conclusion remains a
 * task. It does not import or expose the command runtime's implementation
 * types.
 */
type ScriptedDecisionState = {
  definition?: { editId?: string };
  status?: 'PENDING' | 'KEPT' | 'APPLIED';
};

/**
 * Preferred name for the one business-facing review projection.  Keep the
 * older export as an alias while callers migrate; it deliberately exposes no
 * formal Block, assertion, hash or source-reference implementation detail.
 */
export type ReviewExperienceProjection = ReviewWorkspaceProjection;

export type SourceReviewSection = {
  index: number;
  /** Stable rich-document coordinate, for example `OBJECT` or `METRIC`. */
  sectionId?: string;
  title: string;
  /** A short reader-facing reason this fixed section exists. */
  purpose?: string;
  markdownAnchor?: string;
};

export type SourceReviewDocument = {
  schemaVersion: 1;
  sourceId: string;
  snapshotId: string;
  title: string;
  coverageLabel: string;
  markdown: string;
  markdownSha256: `sha256:${string}`;
  evidence: SourceReviewEvidence[];
  traceLinks: SourceReviewTrace[];
  items: SourceReviewItem[];
  /**
   * Declares that this is a rich standard document rather than a legacy
   * source projection.  V6 supplies this explicitly so a document never
   * becomes a nine-chapter deliverable merely because its Markdown happens
   * to contain nine headings.
   */
  standardSections?: readonly SourceReviewSection[];
  contentOrigin?: 'SNAPSHOT_REFERENCE' | 'SOURCE_NATIVE' | 'DEMO_AUTHORED' | 'DERIVED_DEMO';
};

export type SourceStandardDocumentClaim = DemoContentReviewClaim & {
  kind?: 'OBJECT' | 'GAP' | 'BUSINESS_RULE' | 'CODE_BEHAVIOR'
    | 'ARCHITECTURE' | 'CONFIGURATION' | 'QUERY' | 'SCHEMA_MIGRATION';
  /**
   * Claim-bound, reader-ready material.  The rich document keeps this beside
   * the immutable evidence references so every source can expand its own
   * saved material without asking the workbench to infer a presentation.
   */
  detailViews: ReviewEvidenceView[];
  /** Kept for the existing MySQL structure reader during the transition. */
  schemaEvidences: Array<Extract<ReviewEvidenceView, { kind: 'MYSQL_SCHEMA' }>>;
};

export type SourceStandardDocument = {
  schemaVersion: 1;
  documentKind?: 'SOURCE' | 'MERGED';
  sourceId: string;
  snapshotId: string;
  title: string;
  coverageLabel: string;
  markdown: string;
  markdownSha256: `sha256:${string}`;
  evidence: SourceReviewEvidence[];
  traceLinks: SourceReviewTrace[];
  contentOrigin: 'SNAPSHOT_REFERENCE' | 'SOURCE_NATIVE' | 'DEMO_AUTHORED' | 'DERIVED_DEMO';
  sections?: Record<string, unknown>;
  standardSections: NonNullable<DemoContentSourceReview['standardSections']>;
  claims: SourceStandardDocumentClaim[];
  [key: string]: unknown;
};

export type SourceReviewDocumentProjection = {
  tabs: readonly ['审阅清单', 'Markdown 文档'];
  sections: SourceReviewSection[];
  items: SourceReviewItem[];
  traceLinks: SourceReviewTrace[];
};

type CurrentReviewBlock = {
  blockId: string;
  label: string;
  value: unknown;
};

const mysqlInput = {
  sourceId: 'guanyijia_mysql',
  snapshotId: '20260813T032528Z-abb0502c7d79',
} as const;
const sourceReviewError = '来源审阅文档校验失败';
const canonicalStandardSectionTitles = [
  '文档说明',
  '业务目标',
  '业务对象',
  '业务活动',
  '字段与维度',
  '对象关系',
  '指标口径',
  '示例问题',
  '待确认事项',
] as const;

const canonicalStandardSectionPurposes: readonly string[] = [
  '说明本份资料的来源范围、可确认内容和阅读边界。',
  '概述资料能够支持的业务目标与限制。',
  '归纳涉及的业务对象、职责与结构。',
  '说明采购、销售、库存或结算等业务活动。',
  '说明字段、状态、时间和其他业务维度。',
  '说明对象之间的关系及其确认范围。',
  '说明指标、查询口径及其适用边界。',
  '列出资料可帮助回答的业务问题。',
  '列出尚待确认的事项、限制和下一步。',
];
const formalBlockByEvidenceRef: Readonly<Record<string, string>> = {
  'github:service:minus-stock-flag': 'gyj-block:rule.negative_stock',
  'github:v5:005': 'gyj-block:rule.negative_stock',
  'github:v5:006': 'gyj-block:rule.negative_stock',
  'github:migration:jsh-depot-head-debt-fields': 'gyj-block:schema.jsh_depot_head.debt_fields',
  'github:migration:jsh-depot-head-historical-status': 'gyj-block:dimension.document_status.nine',
};

const formalBlockByReviewClaim: Readonly<Record<string, {
  formalBlockId: string;
  scriptedEditId?: string;
}>> = {
  'guanyijia_mysql:curated-e005': {
    formalBlockId: 'gyj-block:physical.table.jsh_depot_head',
    scriptedEditId: 'scripted:mysql:rename-depot-head',
  },
  'guanyijia_github:github-story-1': {
    formalBlockId: 'gyj-block:rule.negative_stock',
    scriptedEditId: 'scripted:github:clarify-negative-stock',
  },
  'guanyijia_github:github-v5-github-v5-003': {
    formalBlockId: 'gyj-block:rule.negative_stock',
    scriptedEditId: 'scripted:github:clarify-negative-stock',
  },
};

const sourceStandardRevisionMappings = [{
  sourceId: 'guanyijia_mysql',
  claimId: 'v6-mysql-table-jsh-depot-head',
  formalBlockId: 'gyj-block:physical.table.jsh_depot_head',
  editableStatement: false,
}, {
  sourceId: 'guanyijia_github',
  claimId: 'github-v5-003',
  formalBlockId: 'gyj-block:rule.negative_stock',
  editableStatement: true,
}] as const;

const presentationOnlyMysqlTracePrefixes = new Set(['[TRACE:G1]', '[TRACE:G2]', '[TRACE:G3]']);

function failValidation(): never {
  throw new Error(sourceReviewError);
}

function toSha256(value: string): `sha256:${string}` {
  return `sha256:${sha256HexSync(value)}`;
}

function hasExactDigest(value: string, digest: string) {
  return toSha256(value) === digest;
}

function clone<T>(value: T): T {
  return structuredClone(value);
}

function parseSections(markdown: string): SourceReviewSection[] {
  const sections = [...markdown.matchAll(/^## (\d+)\.\s*(.+)$/gm)].map((match) => ({
    index: Number(match[1]),
    title: match[2].trim(),
  }));
  if (sections.length === 0 || sections.some((section, index) => (
    !Number.isInteger(section.index) || section.index < 1
    || (index > 0 && section.index <= sections[index - 1]!.index)
  ))) {
    failValidation();
  }
  return sections;
}

function sectionForAnchor(markdown: string, anchor: string) {
  const anchorOffset = markdown.indexOf(`<!-- ${anchor} -->`);
  if (anchorOffset < 0) failValidation();
  const preceding = [...markdown.slice(0, anchorOffset).matchAll(/^## (\d+)\./gm)].at(-1);
  if (!preceding) failValidation();
  return Number(preceding[1]);
}

function sectionForTrace(markdown: string, trace: SourceReviewTrace) {
  if (trace.section !== undefined) return trace.section;
  return sectionForAnchor(markdown, trace.markdownAnchor);
}

function traceItemSummary(evidence: SourceReviewEvidence) {
  if (evidence.evidenceClass === 'OBSERVED') return '已保存数据库结构，可核对表、字段和约束。';
  if (evidence.evidenceClass === 'SOURCE_NATIVE') return '已保存对应源码节选，可核对具体实现。';
  if (evidence.evidenceClass === 'DEMO_AUTHORED') return '这段演示资料说明了相关业务语境。';
  if (evidence.evidenceClass === 'DERIVED_DEMO') return '这条术语关系由演示资料整理而来。';
  if (evidence.evidenceClass === 'GAP') return '当前资料无法确认这项内容。';
  return '已保存该记录的位置和结构化摘要，完整原文尚未保留。';
}

function resultTypeFor(evidence: SourceReviewEvidence): SourceReviewItem['resultType'] {
  if (evidence.evidenceClass === 'OBSERVED') return '事实';
  if (evidence.evidenceClass === 'SOURCE_NATIVE') return '源码摘录';
  if (evidence.evidenceClass === 'DEMO_AUTHORED') return '演示资料';
  if (evidence.evidenceClass === 'DERIVED_DEMO') return '派生资料';
  if (evidence.evidenceClass === 'GAP') return '资料缺口';
  return '冻结记录';
}

function internalItemPresentation(sourceId: string, evidence: SourceReviewEvidence) {
  if (sourceId === mysqlInput.sourceId) {
    const table = /^CREATE TABLE `([^`]+)` \(/mu.exec(evidence.excerpt);
    if (table) {
      const tableComment = /\) ENGINE=[^\n]+COMMENT='([^']+)'/mu.exec(evidence.excerpt)?.[1];
      const fields = [...evidence.excerpt.matchAll(/^\s*`([^`]+)`/gmu)]
        .map((match) => match[1]!)
        .filter((name) => name !== 'id')
        .slice(0, 5);
      return {
        title: `${table[1]}${tableComment ? ` ${tableComment}` : ''}`,
        statement: fields.length
          ? `已保存字段：${fields.join('、')}。`
          : '已保存该表的字段结构。',
      };
    }
    const procedure = /PROCEDURE `([^`]+)`/mu.exec(evidence.excerpt)?.[1];
    if (procedure) {
      return { title: procedure, statement: '已保存该过程的签名和参数。' };
    }
  }
  return { title: evidence.title, statement: traceItemSummary(evidence) };
}

function blockMappingForTrace(
  sourceId: string,
  trace: SourceReviewTrace,
) {
  const explicit = formalBlockByReviewClaim[`${sourceId}:${trace.markdownAnchor}`];
  if (explicit) return explicit;
  const mapped = [...new Set(trace.evidenceRefs.flatMap((reference) => (
    formalBlockByEvidenceRef[reference] ? [formalBlockByEvidenceRef[reference]!] : []
  )))];
  return mapped.length === 1 ? { formalBlockId: mapped[0]! } : undefined;
}

function buildItems(
  sourceId: string,
  markdown: string,
  evidence: SourceReviewEvidence[],
  traceLinks: SourceReviewTrace[],
) {
  const evidenceByRef = new Map(evidence.map((entry) => [entry.evidenceRef, entry]));
  return traceLinks.map((trace) => {
    const referencedEvidence = trace.evidenceRefs.map((reference) => evidenceByRef.get(reference));
    if (referencedEvidence.some((entry) => !entry)) failValidation();
    const primaryEvidence = referencedEvidence[0]!;
    const presentation = trace.claim ?? internalItemPresentation(sourceId, primaryEvidence);
    const mapping = blockMappingForTrace(sourceId, trace);
    return {
      id: `claim:${sourceId}:${trace.markdownAnchor}`,
      section: sectionForTrace(markdown, trace),
      title: presentation.title,
      statement: presentation.statement,
      summary: traceItemSummary(primaryEvidence),
      resultType: resultTypeFor(primaryEvidence),
      evidenceRefs: [...trace.evidenceRefs],
      markdownAnchors: [trace.markdownAnchor],
      affectedObjectRefs: [...new Set(referencedEvidence.flatMap((entry) => entry!.affectedObjectRefs))],
      ...(mapping ? { formalBlockId: mapping.formalBlockId } : {}),
      ...(mapping?.scriptedEditId ? { scriptedEditId: mapping.scriptedEditId } : {}),
    } satisfies SourceReviewItem;
  });
}

function validateEvidenceAndTraces(
  markdown: string,
  evidence: SourceReviewEvidence[],
  traceLinks: SourceReviewTrace[],
) {
  const evidenceRefs = new Set<string>();
  for (const entry of evidence) {
    if (!entry.evidenceRef || evidenceRefs.has(entry.evidenceRef)
      || !entry.title || !entry.excerpt || !entry.locationLabel || !entry.locationValue
      || !hasExactDigest(entry.excerpt, entry.excerptSha256)) failValidation();
    evidenceRefs.add(entry.evidenceRef);
  }
  const referenced = new Set<string>();
  const anchors = new Set<string>();
  const prefixes = new Set<string>();
  for (const trace of traceLinks) {
    const explicitClaim = trace.claim;
    const hasExplicitLocation = trace.section !== undefined
      && Boolean(trace.sectionId)
      && Boolean(trace.sectionPurpose)
      && Boolean(explicitClaim?.title)
      && Boolean(explicitClaim?.statement);
    if (!trace.linePrefix || !trace.markdownAnchor || trace.evidenceRefs.length === 0
      || anchors.has(trace.markdownAnchor) || prefixes.has(trace.linePrefix)
      || (!hasExplicitLocation && (
        markdown.split(trace.linePrefix).length !== 2
        || markdown.split(`<!-- ${trace.markdownAnchor} -->`).length !== 2
      ))
      || (hasExplicitLocation && (!Number.isInteger(trace.section) || trace.section! < 1))
      || trace.evidenceRefs.some((reference) => !evidenceRefs.has(reference))) failValidation();
    anchors.add(trace.markdownAnchor);
    prefixes.add(trace.linePrefix);
    trace.evidenceRefs.forEach((reference) => referenced.add(reference));
  }
  if (evidence.some((entry) => !referenced.has(entry.evidenceRef))) failValidation();
}

function asMysqlEvidence(value: CuratedReviewEvidence): SourceReviewEvidence {
  return {
    evidenceRef: value.evidenceRef,
    evidenceClass: 'OBSERVED',
    excerptKind: 'EXACT_EXCERPT',
    title: value.title.replace(/^MySQL DDL · /, ''),
    excerpt: value.excerpt,
    locationLabel: value.locationLabel,
    locationValue: value.locationValue,
    excerptSha256: value.excerptSha256,
    affectedObjectRefs: [...value.affectedObjectRefs],
  };
}

function asMysqlTrace(value: CuratedReviewTrace): SourceReviewTrace {
  return {
    linePrefix: value.linePrefix,
    markdownAnchor: value.markdownAnchor,
    evidenceRefs: [...value.evidenceRefs],
  };
}

function readMysqlDocument(): SourceReviewDocument {
  const review = readCuratedSourceReview(mysqlInput);
  if (!review) failValidation();
  const evidence = review.evidence.map(asMysqlEvidence);
  const markdown = review.markdown
    .replace(/\[TRACE:G[1-3]\]\s*/g, '')
    .replace(
      '这是针对冻结 MySQL 快照的只读审阅投影；正文只呈现可定位结构片段。',
      '这是针对冻结 MySQL 快照的审阅文档；正文只呈现可定位结构片段。',
    )
    .replace(
      '本投影不改写正式 V1 编译物、版本、冲突决定或交付链。',
      '用户可核对本次建议修改；来源字节、正式 V1 与既有冲突决定不会被直接编辑。',
    );
  const traceLinks = review.traceLinks
    .map(asMysqlTrace)
    .filter((trace) => !presentationOnlyMysqlTracePrefixes.has(trace.linePrefix));
  validateEvidenceAndTraces(markdown, evidence, traceLinks);
  parseSections(markdown);
  return {
    schemaVersion: 1,
    sourceId: review.sourceId,
    snapshotId: review.snapshotId,
    title: review.title,
    coverageLabel: review.coverageLabel,
    markdown,
    markdownSha256: toSha256(markdown),
    evidence,
    traceLinks,
    items: buildItems(review.sourceId, markdown, evidence, traceLinks),
    contentOrigin: 'SNAPSHOT_REFERENCE',
  };
}

function readPackagedDemoDocument(input: {
  sourceId: string;
  snapshotId: string;
  contentBinding?: DemoContentRunBinding;
}): SourceReviewDocument | undefined {
  if (input.contentBinding) {
    readBoundDemoContentSourceReview({
      sourceId: input.sourceId,
      snapshotId: input.snapshotId,
      contentBinding: input.contentBinding,
    });
  }
  const review = readLegacyDemoContentSourceReview(input);
  if (!review) return undefined;
  validateEvidenceAndTraces(review.markdown, review.evidence, review.traceLinks);
  parseSections(review.markdown);
  return {
    schemaVersion: 1,
    sourceId: review.sourceId,
    snapshotId: review.snapshotId,
    title: review.title,
    coverageLabel: review.coverageLabel,
    markdown: review.markdown,
    markdownSha256: review.markdownSha256,
    evidence: review.evidence,
    traceLinks: review.traceLinks,
    items: buildItems(review.sourceId, review.markdown, review.evidence, review.traceLinks),
    contentOrigin: review.contentOrigin,
  };
}

/**
 * Reads the rich document bound to this exact run. Legacy publications do not
 * expose this seam, so existing V5 readers continue through their old path.
 */
export function readSourceStandardDocument(input: {
  sourceId: string;
  snapshotId: string;
  contentBinding: DemoContentRunBinding;
}): SourceStandardDocument | undefined {
  const review = readBoundDemoContentSourceReview(input);
  if (!review?.standardSections || !review.claims) return undefined;
  const claims = review.claims as ReviewClaim[];
  const detailViewsByClaimId = new Map<string, ReviewEvidenceView[]>();
  for (const view of evidenceViewsForClaims(review, claims)) {
    if (!view.claimId) continue;
    detailViewsByClaimId.set(view.claimId, [...(detailViewsByClaimId.get(view.claimId) ?? []), view]);
  }
  return clone({
    ...review,
    standardSections: review.standardSections,
    claims: review.claims.map((claim) => ({
      ...claim,
      detailViews: detailViewsByClaimId.get(claim.claimId) ?? [],
      schemaEvidences: (detailViewsByClaimId.get(claim.claimId) ?? [])
        .filter((view): view is Extract<ReviewEvidenceView, { kind: 'MYSQL_SCHEMA' }> => view.kind === 'MYSQL_SCHEMA'),
    })),
  });
}

/**
 * Read-only, source-pure document projection used by the three human review
 * tabs. It never consults the external source or recompiles the formal V1.
 */
export function readSourceReviewDocument(input: {
  sourceId: string;
  snapshotId: string;
  contentBinding?: DemoContentRunBinding;
}): SourceReviewDocument | undefined {
  if (input.contentBinding && input.sourceId === mysqlInput.sourceId && input.snapshotId === mysqlInput.snapshotId) {
    // `readBoundDemoContentSourceReview` verifies all five exact source
    // identities; the MySQL body itself remains owned by its curated DDL adapter.
    readBoundDemoContentSourceReview({
      sourceId: input.sourceId,
      snapshotId: input.snapshotId,
      contentBinding: input.contentBinding,
    });
  }
  if (input.sourceId === mysqlInput.sourceId && input.snapshotId === mysqlInput.snapshotId) {
    return clone(readMysqlDocument());
  }
  return readPackagedDemoDocument(input);
}

const mysqlMetadataAnchors = new Set([
  // These lines describe the review package itself or the boundary of the
  // saved query shapes. They are useful source-level context, but they are
  // not a business conclusion a reviewer should have to read as a card.
  'curated-g1', 'curated-g2', 'curated-g3', 'curated-g7', 'curated-g8', 'curated-g12',
]);
const mysqlCrossSourceAnchors = new Set([
  'curated-g6', 'curated-g9', 'curated-g10', 'curated-g11',
]);

const mysqlClaimPresentation: Readonly<Record<string, ClaimPresentation>> = {
  'curated-e001': {
    title: '账户主数据（jsh_account）',
    statement: '保存账户名称、期初金额、当前余额、启用状态与租户归属。',
    focusIdentifiers: ['name', 'initial_amount', 'current_amount', 'enabled', 'tenant_id'],
    section: 1,
  },
  'curated-e005': {
    title: '库存单据主表（jsh_depot_head）',
    statement: '保存出入库单据类型、分类、票据号、业务时间、状态、金额和租户归属。',
    focusIdentifiers: ['type', 'sub_type', 'number', 'oper_time', 'status', 'tenant_id'],
    section: 1,
  },
  'curated-e002': {
    title: '财务单据表头（jsh_account_head）',
    statement: '保存收付款类型、业务单据号、金额、状态和租户归属，可查看完整字段结构。',
    focusIdentifiers: ['type', 'bill_no', 'change_amount', 'status', 'tenant_id'],
    section: 1,
  },
  'curated-g4': {
    title: '单据表头的时间、状态与租户字段',
    statement: '单据表头保存创建时间、出入库时间、状态和租户归属；这些字段的业务口径仍需结合后续资料确认。',
    focusIdentifiers: ['create_time', 'oper_time', 'status', 'tenant_id'],
    evidenceRefs: ['mysql:table:jsh_depot_head'],
    section: 2,
  },
  'curated-g5': {
    title: '单据明细关联表头、商品和仓库',
    statement: '单据明细保存表头、商品和仓库标识（header_id、material_id 和 depot_id），可将一行明细对应到具体单据、商品和仓库。',
    focusIdentifiers: ['header_id', 'material_id', 'depot_id'],
    evidenceRefs: ['mysql:table:jsh_depot_item'],
    section: 3,
  },
};

/**
 * The packaged business documents contain long-form Markdown.  Their trace
 * markers intentionally point at document headings, which are useful for a
 * parser but make terrible review conclusions.  This small, authored map
 * promotes only the few reviewer-facing conclusions that are actually stated
 * in that frozen Markdown.  The rest remains readable in the Markdown tab;
 * it is not inflated into a wall of pseudo objects.
 */
const claimPresentation: Readonly<Record<string, ClaimPresentation>> = {
  'guanyijia_github:github-story-1': {
    title: '负库存开关读取',
    statement: '源码读取租户配置中的负库存开关；该片段只证明读取动作，不能判断库存是否允许或拦截。',
    focusIdentifiers: ['minus_stock_flag'],
    section: 1,
  },
  'guanyijia_github:github-story-2': {
    title: '单据库存校验读取负库存配置',
    statement: '单据处理代码读取负库存配置；该片段不足以单独证明最终校验规则。',
    focusIdentifiers: ['minus_stock_flag'],
    section: 1,
  },
  'guanyijia_github:github-story-3': {
    title: '早期状态字段迁移',
    statement: '迁移记录仅列出早期状态 0、1、2；它只反映该条记录，不代表当前版本的完整状态定义。',
    focusIdentifiers: ['status'],
    section: 2,
  },
  'guanyijia_github:github-story-4': {
    title: '欠款字段迁移记录',
    statement: '迁移记录向单据主表增加本次欠款和最终欠款字段，可与当前部署 DDL 逐项核对。',
    focusIdentifiers: ['debt', 'last_debt', 'last_deposit'],
    section: 2,
  },
  'guanyijia_github:github-story-5': {
    title: '租户范围过滤',
    statement: '代码包含按表名判断租户过滤的入口；完整的访问范围仍需结合其他代码确认。',
    focusIdentifiers: [],
    section: 1,
  },
  'guanyijia_github:github-story-6': {
    title: '按编号读取序列号记录',
    statement: '源码按编号读取序列号记录；不据此推断序列号全流程追踪。',
    focusIdentifiers: [],
    section: 3,
  },
  'guanyijia_github:github-story-7': {
    title: '按批号读取库存明细',
    statement: '源码按商品扩展和批号读取一条库存明细；不据此推断完整库存历史。',
    focusIdentifiers: [],
    section: 3,
  },
  'guanyijia_github:github-story-8': {
    title: '采购与销售数量汇总',
    statement: '代码对单据明细执行采购或销售数量汇总，并带有单据类型、时间、商品扩展等筛选条件；不是完整指标口径。',
    focusIdentifiers: ['basic_number'],
    section: 4,
  },
  'guanyijia_github:github-story-9': {
    title: '采购与销售金额汇总',
    statement: '代码用于查询采购或销售金额；最终统计范围和计算方式仍需与业务资料核对。',
    focusIdentifiers: [],
    section: 4,
  },
  'guanyijia_github:github-story-10': {
    title: '系统业务开关定义',
    statement: '固定建库脚本定义负库存、以销定购和多级审核等字段；字段定义不等于当前配置或运行效果。',
    focusIdentifiers: ['minus_stock_flag'],
    section: 1,
  },
  'guanyijia_github:github-story-11': {
    title: '状态 9 的源码显示含义',
    statement: '代码分支把状态值 9 显示为“审核中”；其正式业务含义和适用流程仍待确认。',
    focusIdentifiers: ['status'],
    section: 2,
  },
  'guanyijia_github:github-story-12': {
    title: '以销定购字段定义',
    statement: '建库脚本定义以销定购字段；是否被采购流程读取仍需更多代码确认。',
    focusIdentifiers: [],
    section: 1,
  },
  'guanyijia_official_docs:guanyijia_official_docs-1-2': {
    title: '库存变动必须方向明确',
    statement: '采购、销售、退货和调拨都应形成可解释的库存变动；调拨需要两端确认。',
    focusIdentifiers: [],
    section: 2,
    sourceExcerpt: '采购入库、销售出库、采购退货、销售退货和仓间调拨都应形成方向明确的库存变动。调拨同时涉及调出仓库和调入仓库，只有两端确认后才能解释为一次完整的仓间转移。',
  },
  'guanyijia_official_docs:guanyijia_official_docs-2-1': {
    title: '业务范围由基础资料和角色共同界定',
    statement: '商品、往来单位、仓库、账户和租户权限共同构成可执行的业务边界。',
    focusIdentifiers: [],
    section: 1,
    sourceExcerpt: '系统边界覆盖基础资料维护、采购销售协同、库存变动、财务收付款、报表统计、用户与租户权限以及单据审核。商品是业务共同对象，商品分类提供层级归类，商品扩展承载条码、单位和价格，库存按商品与仓库形成数量和单价状态。',
  },
  'guanyijia_official_docs:guanyijia_official_docs-3-1': {
    title: '基础资料需要明确维护责任',
    statement: '商品、往来单位和仓库的启用、停用和关键属性需要由对应业务角色维护。',
    focusIdentifiers: [],
    section: 1,
    sourceExcerpt: '商品管理员负责建立商品名称、品牌、型号、规格、颜色、基础单位、保质期和启用状态。对于多单位商品，应明确基础单位与副单位的换算比例，采购、销售和库存人员不得在单据中自行改变换算关系。',
  },
  'guanyijia_official_docs:guanyijia_official_docs-4-2': {
    title: '收付款应关联业务单据',
    statement: '结算应先确认单据金额口径，再判断对账户余额和未结金额的影响。',
    focusIdentifiers: [],
    section: 3,
    sourceExcerpt: '采购入库通常形成对供应商的应付关系，销售出库通常形成对客户的应收关系。付款或收款应关联业务单据，先确认金额口径，再判断是否影响余额和未结金额。',
  },
  'guanyijia_official_docs:guanyijia_official_docs-6-2': {
    title: '单据状态不能一概而论',
    statement: '状态可能描述审批阶段或下游执行进度，不能把所有状态都理解成同一种流程状态。最终含义仍需结合单据类型和版本确认。',
    focusIdentifiers: [],
    section: 3,
    sourceExcerpt: '单据状态常见表达包括未审核、已审核、审核中、完成采购、部分采购、完成销售和部分销售。状态既可能表示审批阶段，也可能表示下游执行进度，不能把所有状态都理解为同一状态机。',
  },
  'guanyijia_demo_policy:guanyijia_demo_policy-2-1': {
    title: '基础资料应完整、唯一且可追溯',
    statement: '关键资料维护需要明确责任人，停用不应抹去历史业务含义。',
    focusIdentifiers: [],
    section: 1,
    sourceExcerpt: '基础资料必须完整、唯一、可追溯、可停用。租户内的商品、单位、分类、供应商、客户、会员、仓库、账户、组织和用户都应有明确责任人。新增资料前先搜索同名、同条码、同助记码和同税务识别信息；确需重复使用时，应说明业务边界。',
  },
  'guanyijia_demo_policy:guanyijia_demo_policy-3-1': {
    title: '权限按业务范围控制',
    statement: '用户只能在获授权的租户、组织和仓库范围内工作；制单、审核、收付款和权限配置原则上分离。',
    focusIdentifiers: [],
    section: 1,
    sourceExcerpt: '权限按租户、组织、岗位、仓库和功能按钮组合控制。用户只能在所属租户内工作，只能查看或操作被授予的仓库和组织范围；角色的价格屏蔽规则不能被报表导出绕过。',
  },
  'guanyijia_demo_policy:guanyijia_demo_policy-3-2': {
    title: '制度草案：统一禁止负库存',
    statement: '制度草案要求出库审核前检查可用库存，库存不足时进入授权例外处理。',
    focusIdentifiers: [],
    section: 3,
    sourceExcerpt: '企业目标是统一禁止负库存。任何销售出库、采购退货或其他出库，在审核前都应检查商品、仓库、基础数量和可用库存；批次商品按实际填写数量校验，普通商品按基础单位数量校验。库存不足时，单据应停留在待处理状态，由业务人员补货、调整数量或申请经授权的例外处理。',
  },
  'guanyijia_demo_policy:guanyijia_demo_policy-3-3': {
    title: '状态需要区分审核与完成阶段',
    statement: '未审核、审核中、已审核和完成代表不同业务阶段，不能混为同一结果。',
    focusIdentifiers: [],
    section: 5,
    sourceExcerpt: '单据至少区分草稿或未审核、审核中、已审核和完成或关闭等业务阶段。未审核单据允许补充和修改，但不能作为正式库存、财务和经营统计依据；审核中单据表示流程尚未结束，是否允许下游转换由流程配置决定；已审核单据可进入库存、结算或下游执行；完成状态表示相关业务已经达到本流程定义的终点。',
  },
  'guanyijia_demo_policy:guanyijia_demo_policy-5-2': {
    title: '库存异常需要先核对再调整',
    statement: '盘点发现负库存时，应先核对单据、时间和退货回补，未经授权不得直接改数。',
    focusIdentifiers: [],
    section: 3,
    sourceExcerpt: '盘点发现负库存时，先冻结相关商品仓库组合的自动业务，再核对单据顺序、审核时间、退货回补和历史迁移。未经授权不得直接把数量改成零。若需要调整，必须说明原因、责任人、批准人和对报表、财务及批次追踪的影响。',
  },
  'guanyijia_demo_policy:guanyijia_demo_policy-8-2': {
    title: '欠款口径需要按结算要素计算',
    statement: '未结金额需要结合单据金额、订金、收付款和退货；部署字段差异仍待核对。',
    focusIdentifiers: [],
    section: 4,
    sourceExcerpt: '当前部署结构可能没有对应字段，存在部署版本、迁移节奏或数据口径差异的可能。Demo只把它列为待核对问题，不能断言计算值或字段值哪一方自动正确。',
  },
  'guanyijia_semantica_demo:semantica-1': {
    title: '术语图用于统一业务阅读',
    statement: '术语图连接对象、流程、指标和职责，帮助理解资料，但不能替代来源资料或业务确认。',
    focusIdentifiers: [],
    section: 1,
  },
  'guanyijia_semantica_demo:semantica-3': {
    title: '账户与欠款术语',
    statement: '账户承载资金结算；欠款和剩余欠款描述业务金额扣除已结算金额后的关系。',
    focusIdentifiers: [],
    section: 1,
  },
  'guanyijia_semantica_demo:semantica-6': {
    title: '库存方向术语',
    statement: '入库增加库存、出库减少库存，当前库存和安全库存用于解释库存状态。',
    focusIdentifiers: [],
    section: 1,
  },
  'guanyijia_semantica_demo:semantica-8': {
    title: '单据状态与工作流审计术语',
    statement: '单据状态、反审核和关联单据用于描述审批、执行和上下游关系。',
    focusIdentifiers: [],
    section: 1,
  },
  'guanyijia_semantica_demo:semantica-9': {
    title: '租户与仓库权限术语',
    statement: '租户、角色、组织和仓库权限共同定义用户可查看和操作的数据范围。',
    focusIdentifiers: [],
    section: 1,
  },
};

function mysqlPresentationFromEvidence(evidence: SourceReviewEvidence): ClaimPresentation | undefined {
  const table = /^CREATE TABLE `([^`]+)` \(/mu.exec(evidence.excerpt);
  if (table) {
    const objectName = table[1]!;
    const tableComment = /\) ENGINE=[^\n]+COMMENT='([^']+)'/mu.exec(evidence.excerpt)?.[1];
    const titleStemByComment: Readonly<Record<string, string>> = {
      '财务主表': '财务单据表头',
      '财务子表': '财务单据明细',
      '单据子表': '库存单据明细',
    };
    const titleStem = tableComment ? (titleStemByComment[tableComment] ?? tableComment) : '数据表';
    const candidateFields = [...evidence.excerpt.matchAll(/^\s*`([^`]+)`[^\n]*?COMMENT '([^']+)'/gmu)]
      .filter((match) => match[1] !== 'id');
    // The source snapshot contains operational columns such as passwords,
    // delete flags and audit timestamps. They are useful in the full schema
    // view but make a poor first sentence for a business reviewer. Prefer
    // domain fields here; the complete, verified definition remains one click
    // away in the evidence view.
    const businessFields = candidateFields.filter((match) => !/密码|口令|密钥|令牌|删除标记|创建人|更新人|创建时间|更新时间|排序|主键/u.test(match[2]!));
    const fields = (businessFields.length > 0 ? businessFields : candidateFields).slice(0, 5);
    const focusIdentifiers = fields.map((match) => match[1]!);
    const readableFields = fields
      .map((match) => humanizeMysqlFieldComment(match[2]!))
      .filter(Boolean);
    // The physical table name stays available in the schema evidence view.
    // Keeping a long identifier in every checklist heading makes the reading
    // surface harder to scan, especially for the inventory tables.
    const titledWithPhysicalName = `${titleStem}（${objectName}）`;
    return {
      title: titledWithPhysicalName.length <= 28 ? titledWithPhysicalName : titleStem,
      statement: readableFields.length
        ? `保存${joinBusinessFields(readableFields)}等字段，可查看完整字段结构。`
        : '已保存这张表的字段结构，可查看完整字段定义。',
      focusIdentifiers,
      section: 1,
    };
  }

  const procedure = /PROCEDURE `([^`]+)`/mu.exec(evidence.excerpt)?.[1];
  if (procedure) {
    const procedureTitle: Readonly<Record<string, string>> = {
      sp_rebalance_below_low_stock_requisition: '低库存补货处理过程',
      sp_rebalance_over_high_stock_sales: '高库存销售平衡过程',
      sp_run_inventory_stock_rebalance: '库存平衡调度过程',
      sp_run_retail_return_rate_and_fact_sync: '零售退货率同步过程',
      sp_run_store_retail_return_coverage_and_fact_sync: '门店退货覆盖同步过程',
      sp_sync_retail_out_fact_batch: '零售出库事实同步过程',
    };
    const title = procedureTitle[procedure] ?? '数据库处理过程';
    return {
      title,
      statement: `已保存${title}的签名和参数，可核对其处理范围；不据此推断实际执行结果。`,
      focusIdentifiers: [],
      section: 4,
    };
  }

  if (/index|constraint/i.test(evidence.locationValue)) {
    return {
      title: '索引与约束定义',
      statement: '已保存索引或约束字段组合，可用于核对结构关系和查询条件。',
      focusIdentifiers: [],
      section: 3,
    };
  }
  return undefined;
}

/**
 * Column comments are the authoritative schema wording, but some of them
 * carry implementation-era shorthand such as `headerId` or enum samples such
 * as `0-禁用`.  The review summary names the business field in plain Chinese;
 * the exact comment, type and enum sample remain available in the schema
 * table and raw DDL view.
 */
function humanizeMysqlFieldComment(comment: string) {
  const field = comment
    .replace(/（.*?）|\(.*?\)/gu, '')
    .split(/[，,]/u, 1)[0]!
    .replace(/\s+\d+\s*[-—]\s*.*$/u, '')
    .replace(/--例如.*$/u, '')
    .trim()
    .replace(/(?:Id|id)$/u, '标识');
  const replacements: Readonly<Record<string, string>> = {
    '用户数量限制': '用户数上限',
    '是否卖出': '是否售出',
    '启用': '启用状态',
    '价格屏蔽': '价格权限范围',
  };
  return replacements[field] ?? field;
}

function joinBusinessFields(fields: readonly string[]) {
  if (fields.length <= 1) return fields[0] ?? '';
  if (fields.length === 2) return `${fields[0]}和${fields[1]}`;
  return `${fields.slice(0, -1).join('、')}和${fields[fields.length - 1]}`;
}

function focusIdentifiersFor(item: SourceReviewItem, evidence: readonly SourceReviewEvidence[]) {
  const available = new Set(evidence.flatMap((entry) => (
    [...entry.excerpt.matchAll(/^\s*`([^`]+)`\s+/gmu)].map((match) => match[1]!)
  )));
  return [...new Set([...item.statement.matchAll(/`([^`]+)`/g)].map((match) => match[1]!))]
    .filter((value) => available.has(value));
}

function reviewMetadata(document: SourceReviewDocument): ReviewDocumentMetadata {
  if (document.sourceId === mysqlInput.sourceId) {
    return {
      title: document.title,
      coverageLabel: document.coverageLabel,
      sourceDescription: '本次保存了数据库结构、相关处理过程和查询条件。可以核对涉及的表、字段和筛选条件；没有实际查询结果，因此不能判断使用频率、数据量或业务效果。',
      limitations: [
        '字段存在，不等于已经确认相关制度、指标或业务时间范围。',
      ],
    };
  }
  if (document.contentOrigin === 'SOURCE_NATIVE') {
    return {
      title: document.title,
      // The frozen package needs the full repository count for integrity
      // checks, but a reviewer is deciding from the saved excerpts below.
      // Showing both quantities made the visible label look like a promise
      // that all 719 files had been reviewed.
      coverageLabel: `${document.evidence.length} 个已保存代码片段`,
      sourceDescription: '本次审阅基于已保存的指定版本源码节选；未保存的源码不会在页面中补全。',
      limitations: ['代码内容仅用于说明实现，不自动视为制度结论。'],
    };
  }
  if (document.sourceId === 'guanyijia_official_docs') {
    return {
      title: '业务说明',
      coverageLabel: document.coverageLabel,
      sourceDescription: '用于说明系统边界、流程和业务口径。',
      limitations: ['需要外部原文支持的结论会明确标为资料缺口。'],
    };
  }
  if (document.sourceId === 'guanyijia_demo_policy') {
    return {
      title: 'ERP管理制度',
      coverageLabel: document.coverageLabel,
      sourceDescription: '用于核对待讨论的目标规则。',
      limitations: ['需要业务负责人确认后，制度目标才能形成正式结论。'],
    };
  }
  if (document.sourceId === 'guanyijia_semantica_demo') {
    return {
      title: '企业术语图',
      coverageLabel: document.coverageLabel,
      sourceDescription: '用于统一业务术语与关系的阅读方式。',
      limitations: ['术语关系用于统一阅读，不能替代来源材料或业务确认。'],
    };
  }
  return {
    title: document.title,
    coverageLabel: document.coverageLabel,
    sourceDescription: document.contentOrigin === 'DERIVED_DEMO'
      ? '以下内容由已确认的演示资料派生，不新增独立依据。'
      : '本次审阅展示冻结的演示编写资料；它不冒充外部原文。',
    limitations: ['需要外部原文支持的结论会明确标为资料缺口。'],
  };
}

function normalizedHumanText(value: string) {
  return value
    .replace(/[`*_#]/g, '')
    .replace(/\s+/g, '')
    .trim();
}

function isMeaningfulClaim(item: SourceReviewItem, presentation: Pick<ReviewClaim, 'title' | 'statement'> | undefined) {
  const title = presentation?.title ?? item.title;
  const statement = presentation?.statement ?? item.statement;
  if (/^#{1,6}\s*\d*\.?/u.test(title) || /^#{1,6}\s*\d*\.?/u.test(statement)) return false;
  if (/\bguanyijia_[a-z_]+:\d+\b/u.test(`${title} ${statement}`)) return false;
  return normalizedHumanText(title) !== normalizedHumanText(statement);
}

const initialScriptedSourceTitles: Readonly<Record<string, readonly string[]>> = {
  // These are formal-chain baseline labels, not human-review copy. They can
  // vary between the old compilation and the frozen review projection, so all
  // known baselines map back to the single readable claim presentation.
  'guanyijia_mysql:curated-e005': [
    'jsh_depot_head 单据主表',
    '库存单据主表',
  ],
  'guanyijia_github:github-story-1': [
    '服务方法读取系统配置中的负库存开关，并将其作为后续业务校验的输入',
    '负库存开关的读取实现',
    '负库存控制候选',
  ],
  'guanyijia_github:github-v5-github-v5-003': [
    '企业级业务开关',
    '负库存控制候选',
  ],
};

function presentationForAnchor(document: EvidenceViewDocument, anchor: string): ClaimPresentation | undefined {
  const trace = document.traceLinks.find((candidate) => candidate.markdownAnchor === anchor);
  if (trace?.claim) return trace.claim as ClaimPresentation;
  if (document.sourceId !== mysqlInput.sourceId) {
    return claimPresentation[`${document.sourceId}:${anchor}`];
  }
  const explicit = mysqlClaimPresentation[anchor];
  if (explicit) return explicit;
  if (!trace || trace.evidenceRefs.length !== 1) return undefined;
  const evidence = document.evidence.find((candidate) => candidate.evidenceRef === trace.evidenceRefs[0]);
  return evidence ? mysqlPresentationFromEvidence(evidence) : undefined;
}

function usesFrozenPresentation(input: {
  document: SourceReviewDocument;
  item: SourceReviewItem;
  anchor: string;
  trace?: SourceReviewTrace;
}) {
  const frozenClaim = input.trace?.claim;
  if (frozenClaim) {
    return input.item.title === frozenClaim.title && input.item.statement === frozenClaim.statement;
  }
  const originalScriptedTitles = initialScriptedSourceTitles[`${input.document.sourceId}:${input.anchor}`];
  return !originalScriptedTitles || originalScriptedTitles.includes(input.item.title);
}

function claimsForReviewDocument(document: SourceReviewDocument): ReviewClaim[] {
  const evidenceByRef = new Map(document.evidence.map((evidence) => [evidence.evidenceRef, evidence]));
  const claims = document.items
    .filter((item) => document.sourceId !== mysqlInput.sourceId
      || (!mysqlMetadataAnchors.has(item.markdownAnchors[0]!) && !mysqlCrossSourceAnchors.has(item.markdownAnchors[0]!)))
    .flatMap((item) => {
      const anchor = item.markdownAnchors[0];
      if (!anchor || item.markdownAnchors.length !== 1 || item.evidenceRefs.length === 0
        || item.evidenceRefs.some((reference) => !evidenceByRef.has(reference))) failValidation();
      const presentation: ClaimPresentation | undefined = presentationForAnchor(document, anchor);
      const trace = document.traceLinks.find((candidate) => candidate.markdownAnchor === anchor);
      // Packaged source Markdown is a transport artefact.  Only explicitly
      // authored human claims may become checklist entries for non-MySQL
      // sources; headings and neighbouring prose must never be promoted by
      // the old line parser.
      if (document.sourceId !== mysqlInput.sourceId && !presentation) return [];
      if (!isMeaningfulClaim(item, presentation)) return [];
      // The curated source calls this table `jsh_depot_head 单据主表`; the
      // review surface gives it a clearer initial Chinese label. Once the
      // scripted review writes a new formal block label, that approved wording
      // takes precedence over the initial presentation label everywhere.
      const usePresentation = usesFrozenPresentation({ document, item, anchor, trace });
      const evidenceRefs = presentation?.evidenceRefs ?? item.evidenceRefs;
      if (evidenceRefs.length === 0 || evidenceRefs.some((reference) => !evidenceByRef.has(reference))) {
        failValidation();
      }
      const claimEvidence = evidenceRefs.map((reference) => evidenceByRef.get(reference)!);
      const section = trace?.section ?? presentation?.section ?? item.section;
      const sectionId = trace?.sectionId ?? `${document.sourceId}:section:${section}`;
      const sectionPurpose = trace?.sectionPurpose ?? readableSectionPurposeFor(document.sourceId, section);
      return [{
        claimId: item.id,
        section,
        sectionId,
        sectionPurpose,
        title: usePresentation ? presentation?.title ?? item.title : item.title,
        statement: usePresentation ? presentation?.statement ?? item.statement : item.statement,
        focusIdentifiers: usePresentation
          ? presentation?.focusIdentifiers ?? focusIdentifiersFor(item, claimEvidence)
          : focusIdentifiersFor(item, claimEvidence),
        evidenceRefs: [...evidenceRefs],
        markdownAnchor: anchor,
        affectedObjectRefs: [...new Set(claimEvidence.flatMap((evidence) => evidence.affectedObjectRefs))],
        ...(item.formalBlockId ? { formalBlockId: item.formalBlockId } : {}),
        ...(item.scriptedEditId ? { scriptedEditId: item.scriptedEditId } : {}),
      } satisfies ReviewClaim];
    });
  if (new Set(claims.map((claim) => claim.claimId)).size !== claims.length
    || new Set(claims.map((claim) => claim.markdownAnchor)).size !== claims.length) failValidation();
  return claims;
}

const readableSectionTitles: Readonly<Record<string, Readonly<Record<number, string>>>> = {
  guanyijia_mysql: {
    1: '业务对象', 2: '字段与业务口径', 3: '对象关系', 4: '业务活动', 5: '待确认事项',
  },
  guanyijia_github: {
    1: '系统结构与部署入口',
    2: '租户隔离、权限与组织',
    3: '商品、客户与供应商主数据',
    4: '采购、入库与采购退货',
    5: '销售、出库与销售退货',
    6: '库存、负库存、批号与序列号',
    7: '财务、账户、订金与欠款',
    8: '单据状态、审核与迁移记录',
    9: '查询、报表口径与资料边界',
  },
  guanyijia_official_docs: {
    1: '系统边界与基础资料', 2: '采购、销售与库存流程', 3: '财务、状态与时间口径',
  },
  guanyijia_demo_policy: {
    1: '基础资料与权限', 2: '采购、销售与退货', 3: '库存与负库存', 4: '财务结算与欠款', 5: '状态与统计时间',
  },
  guanyijia_semantica_demo: {
    1: '术语与业务关系',
  },
};

function readableSectionPurposeFor(sourceId: string, section: number) {
  const title = readableSectionTitles[sourceId]?.[section];
  return title ? `说明${title}相关的可审阅结论。` : '说明本组可审阅的业务结论。';
}

function canonicalStandardSectionsFor(document: SourceReviewDocument): SourceReviewSection[] | undefined {
  const declared = document.standardSections;
  if (!declared || declared.length !== canonicalStandardSectionTitles.length) return undefined;
  const parsed = parseSections(document.markdown);
  if (parsed.length !== canonicalStandardSectionTitles.length
    || parsed.some((section, index) => (
      section.index !== index + 1
      || section.title !== canonicalStandardSectionTitles[index]
    ))
    || declared.some((section, index) => (
      section.index !== index + 1
      || !section.sectionId?.trim()
      || section.title !== canonicalStandardSectionTitles[index]
      || !section.purpose?.trim()
      || !section.markdownAnchor?.trim()
    ))
    || new Set(declared.map((section) => section.markdownAnchor)).size !== declared.length
    // A V6 document must declare the standard coordinate system and every
    // trace must still describe a real readable block in its frozen Markdown.
    // The explicit contract keeps accidental legacy nine-heading documents on
    // the legacy projection path.
    || document.traceLinks.length === 0
    || document.traceLinks.some((trace) => {
      const claim = trace.claim;
      if (!claim?.title || !claim.statement) return true;
      const block = `### ${claim.title}\n\n${claim.statement}`;
      return document.markdown.split(block).length !== 2;
    })
  ) {
    return undefined;
  }
  return declared.map((section, index) => ({
    index: section.index,
    sectionId: section.sectionId,
    title: section.title,
    purpose: section.purpose ?? canonicalStandardSectionPurposes[index]!,
    markdownAnchor: section.markdownAnchor,
  }));
}

function humanSectionsFor(document: SourceReviewDocument, claims: readonly ReviewClaim[]) {
  const canonicalSections = canonicalStandardSectionsFor(document);
  if (canonicalSections) return canonicalSections;
  const titles = readableSectionTitles[document.sourceId] ?? {};
  return [...new Set(claims.map((claim) => claim.section))]
    .sort((left, right) => left - right)
    .map((index) => ({
      index,
      title: titles[index] ?? `审阅内容 ${index}`,
      purpose: readableSectionPurposeFor(document.sourceId, index),
    }));
}

function renderedMarkdownBlock(claim: Pick<ReviewClaim, 'title' | 'statement'>) {
  return `### ${claim.title}\n\n${claim.statement}`;
}

type StructuredStandardMarkdown = {
  contexts: Record<string, string>;
  tracesByClaimId: Map<string, SourceReviewTrace>;
};

/**
 * V7 separates chapter reader context from the claim blocks.  Validate that
 * separation here, at the one seam where an approved formal wording revision
 * is re-projected.  A missing or malformed structured context is not silently
 * guessed from nearby Markdown text.
 */
function structuredStandardMarkdownFor(document: SourceStandardDocument): StructuredStandardMarkdown | undefined {
  // V6 already has a `sections` transport field, but its traces are still
  // title-shaped.  Only V7 explicitly opts in to the stable identity seam.
  const hasStableClaimTrace = document.traceLinks.some((trace) => trace.claimId !== undefined);
  if (!hasStableClaimTrace) return undefined;
  if (document.sections === undefined) failValidation();
  if (!document.sections || typeof document.sections !== 'object' || Array.isArray(document.sections)) {
    failValidation();
  }
  const contexts: Record<string, string> = {};
  for (const section of document.standardSections) {
    const value = document.sections[section.sectionId];
    if (typeof value !== 'string' || !value.trim()) failValidation();
    contexts[section.sectionId] = value.trim();
  }
  const tracesByClaimId = new Map<string, SourceReviewTrace>();
  for (const trace of document.traceLinks) {
    if (!trace.claimId?.trim()) failValidation();
    if (tracesByClaimId.has(trace.claimId)) failValidation();
    tracesByClaimId.set(trace.claimId, trace);
  }
  const claimsById = new Map(document.claims.map((claim) => [claim.claimId, claim]));
  const declaredClaimIds = document.standardSections.flatMap((section) => section.claimIds);
  if (declaredClaimIds.length !== document.claims.length
    || new Set(declaredClaimIds).size !== document.claims.length
    || declaredClaimIds.some((claimId) => !claimsById.has(claimId))) {
    failValidation();
  }
  for (const claim of document.claims) {
    const trace = tracesByClaimId.get(claim.claimId);
    if (!trace || trace.markdownAnchor !== claim.markdownAnchor) failValidation();
  }
  return { contexts, tracesByClaimId };
}

function markdownPreambleForStructuredDocument(markdown: string, title: string) {
  const firstSection = markdown.search(/^##\s+\d+\.\s+/mu);
  if (firstSection < 0) failValidation();
  const savedPreamble = markdown.slice(0, firstSection).trim();
  if (!savedPreamble || /^###\s/mu.test(savedPreamble)) failValidation();
  const lines = savedPreamble.split('\n');
  const savedIntro = lines.slice(1).join('\n').trim();
  return [`# ${title}`, ...(savedIntro ? ['', savedIntro] : [])];
}

/**
 * Rebuilds the reader Markdown from immutable V7 chapter context plus the
 * current structured claims.  The stable trace relation is validated before
 * rendering; neither an old title nor its old prose is used as a search key.
 */
function renderStructuredStandardMarkdown(
  document: SourceStandardDocument,
  claims: readonly SourceStandardDocumentClaim[],
  structured: StructuredStandardMarkdown,
) {
  const claimsById = new Map(claims.map((claim) => [claim.claimId, claim]));
  const lines = markdownPreambleForStructuredDocument(document.markdown, document.title);
  for (const section of document.standardSections) {
    lines.push('', `## ${section.index}. ${section.title}`, '', structured.contexts[section.sectionId]!);
    for (const claimId of section.claimIds) {
      const claim = claimsById.get(claimId);
      const trace = structured.tracesByClaimId.get(claimId);
      if (!claim || claim.sectionId !== section.sectionId
        || !trace || trace.markdownAnchor !== claim.markdownAnchor) {
        failValidation();
      }
      lines.push('', `### ${claim.title}`, '', claim.statement);
    }
  }
  return lines.join('\n').trimEnd().concat('\n');
}

function renderCanonicalRichMarkdown(document: SourceReviewDocument, claims: readonly ReviewClaim[]) {
  let markdown = document.markdown;
  for (const claim of claims) {
    const trace = document.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor);
    const original = trace?.claim;
    if (!original) failValidation();
    const frozenBlock = renderedMarkdownBlock(original);
    const revisedBlock = renderedMarkdownBlock(claim);
    if (markdown.split(frozenBlock).length !== 2) failValidation();
    if (frozenBlock !== revisedBlock) markdown = markdown.replace(frozenBlock, revisedBlock);
  }
  return markdown.trimEnd().concat('\n');
}

function renderCurrentReviewMarkdown(
  document: SourceReviewDocument,
  claims: readonly ReviewClaim[],
  sections: readonly SourceReviewSection[],
  visibleTitle = document.title,
) {
  if (canonicalStandardSectionsFor(document)) return renderCanonicalRichMarkdown(document, claims);
  const lines = [`# ${visibleTitle}`, ''];
  for (const section of sections) {
    lines.push(`## ${section.title}`, '');
    for (const claim of claims.filter((candidate) => candidate.section === section.index)) {
      lines.push(`- ${claim.title}：${claim.statement}`, '');
    }
  }
  return lines.join('\n').trimEnd().concat('\n');
}

function readableContentBundle(input: {
  document: SourceReviewDocument;
  claims: readonly ReviewClaim[];
  markdown: string;
  pendingTasks: readonly ReviewTaskPresentation[];
}): ValidatedReviewContentBundle {
  const evidenceByRef = new Map(input.document.evidence.map((evidence) => [evidence.evidenceRef, evidence]));
  const evidence = input.claims.flatMap((claim) => claim.evidenceRefs.map((evidenceRef) => {
    const item = evidenceByRef.get(evidenceRef);
    if (!item) failValidation();
    return {
      evidenceRef,
      claimId: claim.claimId,
      title: item.title,
      location: `${item.locationLabel}：${item.locationValue}`,
      supports: `支持“${claim.title}”这条审阅结论。`,
    } satisfies InlineEvidencePresentation;
  }));
  // A pending scripted task is not yet an accepted reviewer conclusion. Keep
  // its material in the bundle so the task can be checked, but exclude it
  // from the conclusion statistic everywhere this projection is consumed.
  const pendingClaimIds = new Set(input.pendingTasks.map((task) => task.claimId));
  const claimIds = input.claims
    .filter((claim) => !pendingClaimIds.has(claim.claimId))
    .map((claim) => claim.claimId);
  const evidenceRefs = [...new Set(evidence.map((item) => item.evidenceRef))];
  const taskIds = input.pendingTasks.map((task) => task.taskId);
  return {
    documents: [{ sourceId: input.document.sourceId, title: input.document.title, markdown: input.markdown }],
    claims: input.claims.map((claim) => ({ ...claim })),
    evidence,
    summaries: [{
      conclusions: { count: claimIds.length, claimIds },
      materials: { count: evidenceRefs.length, evidenceRefs },
      pendingTasks: { count: taskIds.length, taskIds },
      findings: { count: 0, findingIds: [] },
    }],
    publication: {
      sourceId: input.document.sourceId,
      snapshotId: input.document.snapshotId,
      contentOrigin: input.document.contentOrigin ?? 'SNAPSHOT_REFERENCE',
    },
  };
}

function sourceSummaryForClaim(document: SourceReviewDocument, claim: ReviewClaim) {
  const evidence = claim.evidenceRefs.map((reference) => document.evidence.find((entry) => entry.evidenceRef === reference));
  if (evidence.some((entry) => !entry)) failValidation();
  return evidence.map((entry) => entry!.title).join('、');
}

function sourceLocationForClaim(document: SourceReviewDocument, claim: ReviewClaim) {
  const evidence = claim.evidenceRefs.map((reference) => document.evidence.find((entry) => entry.evidenceRef === reference));
  if (evidence.some((entry) => !entry)) failValidation();
  return evidence.map((entry) => entry!.locationValue).join('；');
}

const keyConclusionAnchors: Readonly<Record<string, readonly string[]>> = {
  guanyijia_mysql: ['curated-e001', 'curated-e005', 'curated-e012', 'curated-e026', 'curated-g5', 'curated-g7'],
  guanyijia_github: [
    'github-v5-github-v5-003', 'github-v5-github-v5-004',
    'github-v5-github-v5-016', 'github-v5-github-v5-023',
  ],
  guanyijia_official_docs: [
    'guanyijia_official_docs-1-2', 'guanyijia_official_docs-3-1',
    'guanyijia_official_docs-4-2', 'guanyijia_official_docs-6-2',
  ],
  guanyijia_demo_policy: [
    'guanyijia_demo_policy-2-1', 'guanyijia_demo_policy-3-1',
    'guanyijia_demo_policy-3-2', 'guanyijia_demo_policy-8-2',
  ],
  guanyijia_semantica_demo: ['semantica-1', 'semantica-3', 'semantica-6', 'semantica-9'],
};

function projectChecklist(
  document: SourceReviewDocument,
  claims: ReviewClaim[],
  scriptedDecisions: readonly ScriptedDecisionState[],
): ReviewChecklistProjection {
  const entries = claims.map((claim) => ({
    ...claim,
    sourceSummary: sourceSummaryForClaim(document, claim),
    sourceLocation: sourceLocationForClaim(document, claim),
  }));
  const resolvedScriptedEditIds = new Set(scriptedDecisions.flatMap((decision) => (
    decision.status === 'KEPT' || decision.status === 'APPLIED'
      ? decision.definition?.editId ? [decision.definition.editId] : []
      : []
  )));
  const tasks = entries.filter((entry) => {
    const editId = entry.scriptedEditId;
    if (!editId) return false;
    return !resolvedScriptedEditIds.has(editId);
  });
  const pendingTaskIds = new Set(tasks.map((entry) => entry.claimId));
  const gaps = entries
    .filter((entry) => !pendingTaskIds.has(entry.claimId) && document.evidence
      .filter((evidence) => entry.evidenceRefs.includes(evidence.evidenceRef))
      .some((evidence) => evidence.evidenceClass === 'GAP'))
    .map((entry) => ({
      ...entry,
      nextStep: '补充对应来源材料或由业务负责人确认后，再形成正式结论。',
    }));
  const gapIds = new Set(gaps.map((entry) => entry.claimId));
  const keyAnchors = new Set(keyConclusionAnchors[document.sourceId] ?? []);
  const keyConclusions = entries.filter((entry) => !pendingTaskIds.has(entry.claimId) && !gapIds.has(entry.claimId) && (
    keyAnchors.has(entry.markdownAnchor)
    || Boolean(document.sourceId !== mysqlInput.sourceId && claimPresentation[`${document.sourceId}:${entry.markdownAnchor}`])
    || Boolean(document.sourceId === mysqlInput.sourceId && mysqlClaimPresentation[entry.markdownAnchor])
  ));
  // Object details are a compact inventory of business objects, fields,
  // relationships and measurable rules. Narrative examples and pending-topic
  // headings are useful in the Markdown document, but are not objects a
  // reviewer can inspect in a table.
  const keyConclusionIds = new Set(keyConclusions.map((entry) => entry.claimId));
  const objectDetails = entries.filter((entry) => !pendingTaskIds.has(entry.claimId)
    && !gapIds.has(entry.claimId) && !keyConclusionIds.has(entry.claimId));
  return { tasks, keyConclusions, gaps, objectDetails };
}

function parseMysqlSchemaEvidence(evidence: SourceReviewEvidence, highlightedIdentifiers: readonly string[]): ReviewEvidenceView | undefined {
  const tableMatch = /^CREATE TABLE `([^`]+)` \(/m.exec(evidence.excerpt);
  if (!tableMatch) return undefined;
  const columns: MysqlSchemaColumn[] = [];
  for (const line of evidence.excerpt.split('\n')) {
    const match = /^\s*`([^`]+)`\s+(.+?)(?:,)?\s*$/u.exec(line);
    if (!match) continue;
    const [, name, definition] = match;
    if (columns.some((column) => column.name === name)) failValidation();
    const dataType = /^([^\s]+(?:\([^)]*\))?)/u.exec(definition)?.[1] ?? '';
    if (!dataType) failValidation();
    const defaultValue = /\bDEFAULT\s+((?:'[^']*')|[^\s,]+)/iu.exec(definition)?.[1];
    const comment = /\bCOMMENT\s+'([^']*)'/iu.exec(definition)?.[1];
    columns.push({
      name,
      dataType,
      nullable: !/\bNOT\s+NULL\b/iu.test(definition),
      ...(defaultValue !== undefined ? { defaultValue } : {}),
      ...(comment !== undefined ? { comment } : {}),
      ...(new RegExp(`PRIMARY KEY \\(` + '`' + name + '`', 'u').test(evidence.excerpt) ? { keyRole: 'PRIMARY' as const } : {}),
      highlighted: highlightedIdentifiers.includes(name),
    });
  }
  if (!columns.length || highlightedIdentifiers.some((identifier) => !columns.some((column) => column.name === identifier))) {
    failValidation();
  }
  const tableComment = /COMMENT='([^']*)'/u.exec(evidence.excerpt)?.[1];
  return {
    kind: 'MYSQL_SCHEMA',
    evidenceRef: evidence.evidenceRef,
    objectName: tableMatch[1]!,
    ...(tableComment ? { objectComment: tableComment } : {}),
    columns,
    rawDdl: evidence.excerpt,
    locationValue: evidence.locationValue,
  };
}

type EvidenceViewDocument = Pick<SourceReviewDocument, 'sourceId' | 'markdown' | 'evidence' | 'traceLinks'>;

function sourcePassageForClaim(document: EvidenceViewDocument, claim: ReviewClaim) {
  const trace = document.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor);
  if (trace?.claim) {
    const sourceEvidence = claim.evidenceRefs
      .map((reference) => document.evidence.find((evidence) => evidence.evidenceRef === reference))
      .filter((evidence): evidence is SourceReviewEvidence => evidence !== undefined);
    if (sourceEvidence.length !== claim.evidenceRefs.length) failValidation();
    return sourceEvidence.map((evidence) => evidence.excerpt).join('\n');
  }
  const marker = `<!-- ${claim.markdownAnchor} -->`;
  const firstMarker = document.markdown.indexOf(marker);
  if (firstMarker < 0 || document.markdown.indexOf(marker, firstMarker + marker.length) >= 0) failValidation();
  const afterMarker = document.markdown.slice(firstMarker + marker.length);
  const boundaries = [
    afterMarker.indexOf('[TRACE:'),
    // `section-id` is a transport comment within an authored section, not the
    // next claim. Only an actual next anchor ends the reviewer excerpt.
    afterMarker.search(/\n<!--\s+(?!section-id:)[a-z0-9_-]+\s+-->/iu),
    afterMarker.search(/\n##\s/u),
  ].filter((index) => index >= 0);
  const sourcePassage = afterMarker.slice(0, boundaries.length ? Math.min(...boundaries) : undefined);
  return sourcePassage
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !/^#{1,6}\s/u.test(line) && !/^<!--/u.test(line))
    .join(' ')
    .replace(/\s{2,}/gu, ' ')
    .trim();
}

function shortExcerptForClaim(document: EvidenceViewDocument, claim: ReviewClaim) {
  const presentation = presentationForAnchor(document, claim.markdownAnchor);
  if (presentation?.sourceExcerpt) {
    const expectedExcerpt = presentation.sourceExcerpt;
    const sourceEvidence = claim.evidenceRefs
      .map((reference) => document.evidence.find((evidence) => evidence.evidenceRef === reference))
      .filter((evidence): evidence is SourceReviewEvidence => evidence !== undefined);
    if (expectedExcerpt.length > 140
      || !sourceEvidence.some((evidence) => normalizedHumanText(evidence.excerpt)
        .includes(normalizedHumanText(expectedExcerpt)))) failValidation();
    return compactReviewPassage(expectedExcerpt);
  }
  const sourcePassage = sourcePassageForClaim(document, claim);
  if (!sourcePassage) failValidation();
  return compactReviewPassage(sourcePassage);
}

/** Keep a claim-specific document citation readable without changing its source bytes. */
function compactReviewPassage(value: string) {
  const sentences = value
    .split(/(?<=[。！？!?])/u)
    .map((sentence) => sentence.trim())
    .filter(Boolean);
  const sentenceLimited = sentences.length > 3
    ? sentences.slice(0, 3).join('')
    : value;
  if (sentenceLimited.length <= 140) return sentenceLimited;
  const limit = sentenceLimited.slice(0, 140);
  const stop = Math.max(limit.lastIndexOf('。'), limit.lastIndexOf('；'), limit.lastIndexOf('！'), limit.lastIndexOf('？'));
  return `${limit.slice(0, stop > 40 ? stop + 1 : 140).trimEnd()}…`;
}

type FrozenTermGraph = {
  terms: Array<{
    termId: string;
    name: string;
    definition: string;
    domain: string;
  }>;
  relations: Array<{
    relationId: string;
    subjectId: string;
    predicate: string;
    objectId: string;
    description: string;
    subjectName?: string;
    objectName?: string;
  }>;
};

function parseFrozenTermGraph(evidence: SourceReviewEvidence): FrozenTermGraph {
  let parsed: unknown;
  try {
    parsed = JSON.parse(evidence.excerpt);
  } catch {
    failValidation();
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) failValidation();
  const record = parsed as Record<string, unknown>;
  if (!Array.isArray(record.terms) || !Array.isArray(record.relations)) failValidation();
  const terms = record.terms.map((candidate) => {
    if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) failValidation();
    const value = candidate as Record<string, unknown>;
    if (typeof value.termId !== 'string' || typeof value.name !== 'string'
      || typeof value.definition !== 'string' || typeof value.domain !== 'string') failValidation();
    return {
      termId: value.termId,
      name: value.name,
      definition: value.definition,
      domain: value.domain,
    };
  });
  const relations = record.relations.map((candidate) => {
    if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) failValidation();
    const value = candidate as Record<string, unknown>;
    if (typeof value.relationId !== 'string' || typeof value.subjectId !== 'string'
      || typeof value.predicate !== 'string' || typeof value.objectId !== 'string'
      || typeof value.description !== 'string') failValidation();
    return {
      relationId: value.relationId,
      subjectId: value.subjectId,
      predicate: value.predicate,
      objectId: value.objectId,
      description: value.description,
      ...(typeof value.subjectName === 'string' ? { subjectName: value.subjectName } : {}),
      ...(typeof value.objectName === 'string' ? { objectName: value.objectName } : {}),
    };
  });
  const termIds = new Set(terms.map((term) => term.termId));
  if (termIds.size !== terms.length || new Set(relations.map((relation) => relation.relationId)).size !== relations.length) failValidation();
  return { terms, relations };
}

function termRelationViewForClaim(evidence: SourceReviewEvidence, document: EvidenceViewDocument, claim: ReviewClaim) {
  const graph = parseFrozenTermGraph(evidence);
  const namesInPassage = new Set([
    ...claim.focusIdentifiers,
    ...[...shortExcerptForClaim(document, claim).matchAll(/\*\*([^*]+)\*\*/gu)]
      .map((match) => match[1]!.trim()),
  ]);
  // Rich term-graph passages are already scoped by domain. Some of the
  // high-level chapter claims intentionally have no individual focus term;
  // in that case show the saved domain terms rather than failing the whole
  // standardized document.
  const terms = (graph.terms.filter((term) => namesInPassage.has(term.name)).length
    ? graph.terms.filter((term) => namesInPassage.has(term.name))
    : graph.terms).slice(0, 8);
  if (!terms.length) failValidation();
  const selectedIds = new Set(terms.map((term) => term.termId));
  const namesById = new Map(graph.terms.map((term) => [term.termId, term.name]));
  const relations = graph.relations
    .filter((relation) => selectedIds.has(relation.subjectId) || selectedIds.has(relation.objectId))
    .flatMap((relation) => {
      const subject = namesById.get(relation.subjectId) ?? relation.subjectName;
      const object = namesById.get(relation.objectId) ?? relation.objectName;
      return subject && object ? [{
        subject,
        predicate: relation.predicate,
        object,
        description: relation.description,
      }] : [];
    }).slice(0, 8);
  return {
    kind: 'TERM_RELATION' as const,
    evidenceRef: evidence.evidenceRef,
    claimId: claim.claimId,
    title: claim.title,
    terms: terms.map(({ name, definition, domain }) => ({ name, definition, domain })),
    relations,
    locationValue: evidence.locationValue,
  };
}

/**
 * Evidence can support several conclusions. The reader must choose the
 * conclusion-specific view instead of accidentally showing the first whole
 * source document that happens to share the same evidence reference.
 */
export function reviewEvidenceViewForClaim(
  views: readonly ReviewEvidenceView[],
  evidenceRef: string,
  claimId?: string,
) {
  return (claimId
    ? views.find((view) => view.evidenceRef === evidenceRef && view.claimId === claimId)
    : undefined)
    ?? views.find((view) => view.evidenceRef === evidenceRef && !view.claimId)
    ?? views.find((view) => view.evidenceRef === evidenceRef);
}

function evidenceViewsForClaims(document: EvidenceViewDocument, claims: readonly ReviewClaim[]): ReviewEvidenceView[] {
  const sourceEvidenceByRef = new Map(document.evidence.map((evidence) => [evidence.evidenceRef, evidence]));
  return claims.flatMap<ReviewEvidenceView>((claim) => claim.evidenceRefs.flatMap<ReviewEvidenceView>((evidenceRef): ReviewEvidenceView | readonly ReviewEvidenceView[] => {
    const evidence = sourceEvidenceByRef.get(evidenceRef);
    if (!evidence) failValidation();
    const mysql = document.sourceId === mysqlInput.sourceId
      ? parseMysqlSchemaEvidence(evidence, claim.focusIdentifiers)
      : undefined;
    if (mysql) return [{ ...mysql, claimId: claim.claimId }];
    if (evidence.excerptKind === 'DERIVED_GRAPH') {
      return [termRelationViewForClaim(evidence, document, claim)];
    }
    if (evidence.excerptKind === 'DOCUMENT_SECTION') {
      return [{
        kind: 'DOCUMENT_SECTION' as const,
        evidenceRef,
        claimId: claim.claimId,
        title: claim.title,
        excerpt: shortExcerptForClaim(document, claim),
        locationValue: evidence.locationValue,
      }];
    }
    return [{
      kind: 'SOURCE_EXCERPT' as const,
      evidenceRef,
      claimId: claim.claimId,
      title: evidence.title,
      language: /sql|ddl|procedure|dml/i.test(evidence.locationValue) ? 'sql' as const : 'text' as const,
      rawExcerpt: evidence.excerpt,
      locationValue: evidence.locationValue,
    }];
  }));
}

/**
 * Raw SQL, DDL and source-code excerpts stay outside the copy rules because
 * they are intentionally exact evidence. Every prose-only evidence view is
 * checked before it can enter the business review surface.
 */
function businessCopyForEvidenceViews(views: readonly ReviewEvidenceView[]): BusinessCopyEntry[] {
  return views.flatMap((view): BusinessCopyEntry[] => {
    if (view.kind === 'DOCUMENT_SECTION') {
      return [
        { role: 'heading', value: view.title, pairedValue: view.excerpt },
        { role: 'paragraph', value: view.excerpt },
      ];
    }
    if (view.kind === 'TERM_RELATION') {
      return [
        { role: 'heading', value: view.title },
        ...view.terms.flatMap((term) => [
          { role: 'heading' as const, value: term.name, pairedValue: term.definition },
          { role: 'paragraph' as const, value: term.definition },
        ]),
        ...view.relations.map((relation) => ({
          role: 'paragraph' as const,
          value: relation.description,
        })),
      ];
    }
    return [];
  });
}

function editableFieldLabel(input: {
  sourceId: string;
  field: ScriptedReviewEditableField;
}) {
  if (input.sourceId === 'guanyijia_mysql' && input.field === 'label') return '业务名称';
  if (input.field === 'label') return '结论名称';
  return '业务说明';
}

function taskTitleForDisplay(input: {
  definition: ScriptedReviewEditDefinition;
  claim: ReviewClaim;
}) {
  const { initialDisplay } = input.definition;
  return initialDisplay?.claimTitle === input.claim.title
    ? initialDisplay.title
    : input.claim.title;
}

function taskReason(sourceId: string) {
  if (sourceId === 'guanyijia_mysql') {
    return '统一术语，让“表头”与这张表保存的单据信息更一致。';
  }
  if (sourceId === 'guanyijia_github') {
    return '补充“按租户配置控制”的范围，避免把实现误读为统一制度。';
  }
  failValidation();
}

function projectReviewTasks(input: {
  document: SourceReviewDocument;
  claims: readonly ReviewClaim[];
  sections: readonly SourceReviewSection[];
}): ReviewTaskPresentation[] {
  const evidenceByRef = new Map(input.document.evidence.map((entry) => [entry.evidenceRef, entry]));
  const claimsById = new Map(input.claims.map((claim) => [claim.claimId, claim]));
  return listScriptedReviewEdits({ sourceId: input.document.sourceId }).map((definition) => {
    const claim = claimsById.get(definition.reviewClaimId);
    const section = claim && input.sections.find((candidate) => candidate.index === claim.section);
    if (!claim || !section || claim.scriptedEditId !== definition.editId
      || claim.formalBlockId !== definition.formalBlockId
      || claim.evidenceRefs.length !== definition.reviewEvidenceRefs.length
      || definition.reviewEvidenceRefs.some((reference) => !claim.evidenceRefs.includes(reference))) {
      failValidation();
    }
    const current = {
      title: taskTitleForDisplay({ definition, claim }),
      statement: claim.statement,
    };
    const recommendation = {
      title: definition.suggestedPatch.label ?? current.title,
      statement: definition.suggestedPatch.text ?? current.statement,
    };
    const evidence = definition.reviewEvidenceRefs.map((reference) => {
      const item = evidenceByRef.get(reference);
      if (!item) failValidation();
      return {
        title: item.title,
        excerpt: item.excerpt,
        location: `${item.locationLabel}：${item.locationValue}`,
        supports: `支持“${current.title}”这条审阅结论。`,
      };
    });
    const editableFields = definition.editableFields.map((field) => ({
      field,
      label: editableFieldLabel({ sourceId: definition.sourceId, field }),
      value: field === 'label'
        ? recommendation.title
        : recommendation.statement,
    }));
    return {
      taskId: definition.editId,
      claimId: claim.claimId,
      current,
      recommendation,
      editableFields,
      reason: taskReason(definition.sourceId),
      evidence,
      markdownChange: {
        sectionTitle: section.title,
        before: [`${current.title}：${current.statement}`],
        after: [`${recommendation.title}：${recommendation.statement}`],
      },
    } satisfies ReviewTaskPresentation;
  });
}

/**
 * The sole human-review seam. It turns immutable source evidence and the
 * current verified revision into the three coherent user-facing views.
 */
export function projectSourceReviewWorkspace(input: {
  sourceDocument: SourceReviewDocument;
  currentBlocks: readonly CurrentReviewBlock[];
  scriptedDecisions: readonly ScriptedDecisionState[];
}): ReviewWorkspaceProjection {
  const revised = projectSourceReviewRevision(input.sourceDocument, input.currentBlocks);
  const metadata = reviewMetadata(revised);
  const claims = claimsForReviewDocument(revised);
  const sections = humanSectionsFor(revised, claims);
  const markdown = renderCurrentReviewMarkdown(revised, claims, sections, metadata.title);
  const evidenceViews = evidenceViewsForClaims(revised, claims);
  const traceRows = claims.flatMap((claim) => claim.evidenceRefs.map((evidenceRef) => ({
    claimId: claim.claimId,
    evidenceRef,
    markdownAnchor: claim.markdownAnchor,
  })));
  const tasks = projectReviewTasks({ document: revised, claims, sections });
  const contentBundle = readableContentBundle({
    document: revised,
    claims,
    markdown,
    pendingTasks: tasks.filter((task) => {
      const decision = input.scriptedDecisions.find((candidate) => candidate.definition?.editId === task.taskId);
      return decision?.status !== 'KEPT' && decision?.status !== 'APPLIED';
    }),
  });
  const reviewCopy: BusinessCopyEntry[] = [
    { role: 'heading', value: metadata.title },
    { role: 'paragraph', value: metadata.coverageLabel },
    { role: 'paragraph', value: metadata.sourceDescription },
    ...metadata.limitations.map((value) => ({ role: 'paragraph' as const, value })),
    ...sections.flatMap((section) => [
      { role: 'heading' as const, value: section.title },
      { role: 'paragraph' as const, value: section.purpose ?? readableSectionPurposeFor(revised.sourceId, section.index) },
    ]),
    ...claims.flatMap((claim) => [
      { role: 'heading' as const, value: claim.title, pairedValue: claim.statement },
      { role: 'paragraph' as const, value: claim.statement },
    ]),
  ];
  assertBusinessCopy([...reviewCopy, ...businessCopyForEvidenceViews(evidenceViews)]);
  return clone({
    tabs: ['审阅清单', 'Markdown 文档'] as const,
    metadata,
    sections,
    claims,
    tasks,
    checklist: projectChecklist(revised, claims, input.scriptedDecisions),
    markdown: { content: markdown, sourceContent: markdown },
    evidenceViews,
    traceRows,
    contentBundle,
  });
}

export function projectSourceReviewDocument(document: SourceReviewDocument): SourceReviewDocumentProjection {
  const sections = parseSections(document.markdown);
  validateEvidenceAndTraces(document.markdown, document.evidence, document.traceLinks);
  const expectedItems = buildItems(
    document.sourceId,
    document.markdown,
    document.evidence,
    document.traceLinks,
  );
  if (JSON.stringify(expectedItems) !== JSON.stringify(document.items)) failValidation();
  return clone({
    tabs: ['审阅清单', 'Markdown 文档'] as const,
    sections,
    items: document.items,
    traceLinks: document.traceLinks,
  });
}

/**
 * Combines the immutable source-material projection with an already-verified
 * formal revision. Only a claim with an exact scripted block mapping can
 * receive the approved visible wording. Source bytes, evidence references,
 * locations and Markdown anchors remain the frozen values.
 */
export function projectSourceReviewRevision(
  document: SourceReviewDocument,
  blocks: readonly CurrentReviewBlock[],
): SourceReviewDocument {
  const currentByBlockId = new Map(blocks.map((block) => [block.blockId, block]));
  const next = clone(document);
  next.items = next.items.map((item) => {
    if (!item.formalBlockId || !item.scriptedEditId) return item;
    const current = currentByBlockId.get(item.formalBlockId);
    if (!current || !current.label.trim()) return item;
    const updated = { ...item, title: current.label };
    if (item.scriptedEditId === 'scripted:github:clarify-negative-stock'
      && typeof current.value === 'object' && current.value !== null
      && 'text' in current.value && typeof current.value.text === 'string'
      && current.value.text.trim()) {
      updated.statement = current.value.text;
    }
    return updated;
  });
  return next;
}

/**
 * Applies the verified formal revision to the two explicitly scripted V6
 * reader claims. Frozen evidence, schema views and Markdown anchors are never
 * rewritten by this projection.
 */
export function projectSourceStandardDocumentRevision(
  document: SourceStandardDocument,
  blocks: readonly CurrentReviewBlock[],
): SourceStandardDocument {
  const next = clone(document);
  const mapping = sourceStandardRevisionMappings.find((candidate) => candidate.sourceId === document.sourceId);
  if (!mapping) return next;
  const current = blocks.find((block) => block.blockId === mapping.formalBlockId);
  if (!current?.label.trim()) return next;
  const index = next.claims.findIndex((claim) => claim.claimId === mapping.claimId);
  if (index < 0) failValidation();
  const claim = next.claims[index]!;
  const statement = mapping.editableStatement
    && typeof current.value === 'object' && current.value !== null
    && 'text' in current.value && typeof current.value.text === 'string'
    && current.value.text.trim()
    ? current.value.text
    : claim.statement;
  const structured = structuredStandardMarkdownFor(next);
  next.claims[index] = { ...claim, title: current.label, statement };
  if (structured) {
    next.markdown = renderStructuredStandardMarkdown(next, next.claims, structured);
    next.markdownSha256 = toSha256(next.markdown);
    return next;
  }
  const before = `### ${claim.title}\n\n${claim.statement}`;
  const after = `### ${current.label}\n\n${statement}`;
  if (next.markdown.split(before).length !== 2) failValidation();
  next.markdown = next.markdown.replace(before, after);
  next.markdownSha256 = toSha256(next.markdown);
  return next;
}
