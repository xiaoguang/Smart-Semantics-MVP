import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { generatedCuratedMysqlReview } from './guanyijia-curated-mysql-review.generated.ts';

const curatedSourceId = 'guanyijia_mysql';
const curatedSnapshotId = '20260813T032528Z-abb0502c7d79';
const evidenceSnapshotError = '真实审阅文档校验失败';
const sha256Pattern = /^sha256:[0-9a-f]{64}$/;
const canonicalOutputDomain = 'guanyijia-curated-mysql-review/output/v1';
const frozenOutputDigest = 'sha256:1d81ba71292fa6c6fa68581818509707cef0eec2407aa29d85f229c1b9df3589';
const selectedTableRefs = [
  'mysql:table:jsh_account',
  'mysql:table:jsh_account_head',
  'mysql:table:jsh_account_item',
  'mysql:table:jsh_depot',
  'mysql:table:jsh_depot_head',
  'mysql:table:jsh_depot_item',
  'mysql:table:jsh_function',
  'mysql:table:jsh_in_out_item',
  'mysql:table:jsh_material',
  'mysql:table:jsh_material_attribute',
  'mysql:table:jsh_material_category',
  'mysql:table:jsh_material_current_stock',
  'mysql:table:jsh_material_extend',
  'mysql:table:jsh_material_initial_stock',
  'mysql:table:jsh_material_property',
  'mysql:table:jsh_msg',
  'mysql:table:jsh_orga_user_rel',
  'mysql:table:jsh_organization',
  'mysql:table:jsh_person',
  'mysql:table:jsh_platform_config',
  'mysql:table:jsh_role',
  'mysql:table:jsh_serial_number',
  'mysql:table:jsh_supplier',
  'mysql:table:jsh_sys_dict_data',
  'mysql:table:jsh_sys_dict_type',
  'mysql:table:jsh_system_config',
  'mysql:table:jsh_tenant',
  'mysql:table:jsh_unit',
  'mysql:table:jsh_user',
  'mysql:table:jsh_user_business',
] as const;
const selectedProcedureRefs = [
  'mysql:procedure:sp_rebalance_below_low_stock_requisition',
  'mysql:procedure:sp_rebalance_over_high_stock_sales',
  'mysql:procedure:sp_run_inventory_stock_rebalance',
  'mysql:procedure:sp_run_retail_return_rate_and_fact_sync',
  'mysql:procedure:sp_run_store_retail_return_coverage_and_fact_sync',
  'mysql:procedure:sp_sync_retail_out_fact_batch',
] as const;
const forbiddenStrings = [
  '该业务载体存在于当前部署结构',
  '部署扩展资产（第',
  '冻结扫描已识别',
  '已冻结的关联或汇总语句',
  'samples/',
  'profiles/',
  'query-results',
  'raw-row',
  'Tenant 153',
];

export type CuratedReviewEvidence = {
  evidenceRef: string;
  evidenceClass: 'OBSERVED';
  title: string;
  excerpt: string;
  locationLabel: string;
  locationValue: string;
  excerptSha256: `sha256:${string}`;
  affectedObjectRefs: string[];
};

export type CuratedReviewTrace = {
  linePrefix: string;
  markdownAnchor: string;
  evidenceRefs: string[];
  relatedBlockIds: string[];
};

export type CuratedSourceReviewDocument = {
  schemaVersion: 1;
  sourceId: 'guanyijia_mysql';
  snapshotId: '20260813T032528Z-abb0502c7d79';
  title: string;
  coverageLabel: '30 / 95 张表';
  markdown: string;
  markdownSha256: `sha256:${string}`;
  evidence: CuratedReviewEvidence[];
  traceLinks: CuratedReviewTrace[];
  generationManifest: {
    provider: 'CODEX_CHATGPT_SESSION';
    model: 'gpt-5.6-luna';
    reasoningEffort: 'xhigh';
    inputDigest: `sha256:${string}`;
    outputDigest: `sha256:${string}`;
  };
};

function failValidation(): never {
  throw new Error(evidenceSnapshotError);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function nonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function stringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.length > 0 && value.every(nonEmptyString);
}

function exactSha256(value: unknown, content: string): value is `sha256:${string}` {
  return typeof value === 'string' && sha256Pattern.test(value)
    && value === `sha256:${sha256HexSync(content)}`;
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function countOccurrences(content: string, value: string) {
  return (content.match(new RegExp(escapeRegExp(value), 'g')) ?? []).length;
}

function hasNoDuplicates(values: string[]) {
  return new Set(values).size === values.length;
}

function hasExactSet(actual: string[], expected: readonly string[]) {
  const expectedValues = new Set(expected);
  return actual.length === expected.length
    && actual.every((value) => expectedValues.has(value));
}

function canonicalOutputDigest(
  value: Record<string, unknown>,
  generationManifest: Record<string, unknown>,
): `sha256:${string}` {
  const { outputDigest: _ignoredOutputDigest, ...generation } = generationManifest;
  try {
    return `sha256:${sha256HexSync(JSON.stringify({
      domain: canonicalOutputDomain,
      value: {
        schemaVersion: value.schemaVersion,
        sourceId: value.sourceId,
        snapshotId: value.snapshotId,
        title: value.title,
        coverageLabel: value.coverageLabel,
        markdown: value.markdown,
        markdownSha256: value.markdownSha256,
        evidence: value.evidence,
        traceLinks: value.traceLinks,
        generation,
      },
    }))}`;
  } catch {
    return failValidation();
  }
}

function validateObservedEvidence(evidence: CuratedReviewEvidence) {
  if (evidence.locationLabel !== 'MySQL 冻结快照文件'
    || !/^(ddl\/tables|programmability\/procedures|constraints|dml)\/.+\.sql(?:#L\d+(?:-L\d+)?)?$/.test(evidence.locationValue)
    || !evidence.evidenceRef.startsWith('mysql:')) failValidation();

  const table = /^mysql:table:([a-z0-9_]+)$/.exec(evidence.evidenceRef)?.[1];
  if (table && (evidence.locationValue !== `ddl/tables/${table}.sql`
    || !evidence.excerpt.includes(`CREATE TABLE \`${table}\``))) failValidation();

  const procedure = /^mysql:procedure:([a-z0-9_]+)$/.exec(evidence.evidenceRef)?.[1];
  if (procedure && (evidence.locationValue !== `programmability/procedures/${procedure}.sql`
    || !evidence.excerpt.includes(`PROCEDURE \`${procedure}\``))) failValidation();

  if (!table && !procedure && !['mysql:constraints:indexes', 'mysql:constraints:foreign-keys', 'mysql:dml:digests']
    .includes(evidence.evidenceRef)) failValidation();
}

function validateEvidence(value: unknown): CuratedReviewEvidence[] {
  if (!Array.isArray(value) || value.length === 0) failValidation();
  const seenRefs = new Set<string>();
  const evidence: CuratedReviewEvidence[] = [];

  for (const candidate of value) {
    if (!isRecord(candidate)
      || !nonEmptyString(candidate.evidenceRef)
      || candidate.evidenceClass !== 'OBSERVED'
      || !nonEmptyString(candidate.title)
      || !nonEmptyString(candidate.excerpt)
      || !nonEmptyString(candidate.locationLabel)
      || !nonEmptyString(candidate.locationValue)
      || !stringArray(candidate.affectedObjectRefs)
      || !hasNoDuplicates(candidate.affectedObjectRefs)
      || !exactSha256(candidate.excerptSha256, candidate.excerpt)
      || seenRefs.has(candidate.evidenceRef)) failValidation();

    const item = candidate as CuratedReviewEvidence;
    seenRefs.add(item.evidenceRef);
    validateObservedEvidence(item);
    evidence.push(item);
  }
  return evidence;
}

function validateSelectedScope(evidence: CuratedReviewEvidence[]) {
  const tableRefs = evidence
    .filter((item) => item.evidenceRef.startsWith('mysql:table:'))
    .map((item) => item.evidenceRef);
  const procedureRefs = evidence
    .filter((item) => item.evidenceRef.startsWith('mysql:procedure:'))
    .map((item) => item.evidenceRef);
  if (!hasExactSet(tableRefs, selectedTableRefs) || !hasExactSet(procedureRefs, selectedProcedureRefs)) {
    failValidation();
  }
}

function validateTraceLinks(value: unknown, markdown: string, evidence: CuratedReviewEvidence[]) {
  if (!Array.isArray(value) || value.length === 0) failValidation();
  const evidenceRefs = new Set(evidence.map((item) => item.evidenceRef));
  const referencedEvidence = new Set<string>();
  const seenPrefixes = new Set<string>();
  const seenAnchors = new Set<string>();
  const traces: CuratedReviewTrace[] = [];

  for (const candidate of value) {
    if (!isRecord(candidate)
      || !nonEmptyString(candidate.linePrefix)
      || !/^\[TRACE:[A-Z][A-Z0-9_-]*\]$/.test(candidate.linePrefix)
      || !nonEmptyString(candidate.markdownAnchor)
      || !/^curated-[a-z0-9-]+$/.test(candidate.markdownAnchor)
      || !stringArray(candidate.evidenceRefs)
      || !stringArray(candidate.relatedBlockIds)
      || !hasNoDuplicates(candidate.evidenceRefs)
      || !hasNoDuplicates(candidate.relatedBlockIds)
      || seenPrefixes.has(candidate.linePrefix)
      || seenAnchors.has(candidate.markdownAnchor)
      || countOccurrences(markdown, candidate.linePrefix) !== 1
      || countOccurrences(markdown, `<!-- ${candidate.markdownAnchor} -->`) !== 1
      || candidate.evidenceRefs.some((evidenceRef) => !evidenceRefs.has(evidenceRef))) failValidation();

    seenPrefixes.add(candidate.linePrefix);
    seenAnchors.add(candidate.markdownAnchor);
    candidate.evidenceRefs.forEach((evidenceRef) => referencedEvidence.add(evidenceRef));
    traces.push(candidate as CuratedReviewTrace);
  }

  if (evidence.some((item) => !referencedEvidence.has(item.evidenceRef))) {
    failValidation();
  }
  return traces;
}

function validateGeneratedDocument(value: unknown): CuratedSourceReviewDocument {
  if (!isRecord(value)) failValidation();
  if (!nonEmptyString(value.markdown)) failValidation();
  const markdown = value.markdown;

  if (value.schemaVersion !== 1
    || value.sourceId !== curatedSourceId
    || value.snapshotId !== curatedSnapshotId
    || value.title !== '数据库建模审阅（真实证据节选）'
    || value.coverageLabel !== '30 / 95 张表'
    || !exactSha256(value.markdownSha256, markdown)
    || !isRecord(value.generationManifest)
    || value.generationManifest.provider !== 'CODEX_CHATGPT_SESSION'
    || value.generationManifest.model !== 'gpt-5.6-luna'
    || value.generationManifest.reasoningEffort !== 'xhigh'
    || typeof value.generationManifest.inputDigest !== 'string'
    || !sha256Pattern.test(value.generationManifest.inputDigest)
    || typeof value.generationManifest.outputDigest !== 'string'
    || !sha256Pattern.test(value.generationManifest.outputDigest)
    || value.generationManifest.outputDigest !== canonicalOutputDigest(value, value.generationManifest)
    || value.generationManifest.outputDigest !== frozenOutputDigest) failValidation();

  if (forbiddenStrings.some((string) => markdown.includes(string))) failValidation();
  if ((markdown.match(/^## /gm) ?? []).length !== 9
    || !markdown.startsWith(`# ${value.title}\n`)) failValidation();
  if (forbiddenStrings.some((string) => JSON.stringify(value.evidence).includes(string))) failValidation();

  const evidence = validateEvidence(value.evidence);
  validateSelectedScope(evidence);
  const traceLinks = validateTraceLinks(value.traceLinks, markdown, evidence);
  return {
    schemaVersion: 1,
    sourceId: curatedSourceId,
    snapshotId: curatedSnapshotId,
    title: value.title,
    coverageLabel: '30 / 95 张表',
    markdown,
    markdownSha256: value.markdownSha256 as `sha256:${string}`,
    evidence,
    traceLinks,
    generationManifest: {
      provider: 'CODEX_CHATGPT_SESSION',
      model: 'gpt-5.6-luna',
      reasoningEffort: 'xhigh',
      inputDigest: value.generationManifest.inputDigest as `sha256:${string}`,
      outputDigest: value.generationManifest.outputDigest as `sha256:${string}`,
    },
  };
}

/**
 * Reads the committed, public review projection. The generated object is
 * validated for every matching request before a clone leaves this module, so
 * callers cannot observe corrupted bytes or mutate the frozen source value.
 */
export function readCuratedSourceReview(input: {
  sourceId: string;
  snapshotId: string;
}): CuratedSourceReviewDocument | undefined {
  if (input.sourceId !== curatedSourceId || input.snapshotId !== curatedSnapshotId) return undefined;
  return structuredClone(validateGeneratedDocument(generatedCuratedMysqlReview));
}
