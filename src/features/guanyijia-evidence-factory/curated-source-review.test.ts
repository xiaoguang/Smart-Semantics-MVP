import assert from 'node:assert/strict';
import test from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { generatedCuratedMysqlReview } from './guanyijia-curated-mysql-review.generated.ts';
import { readCuratedSourceReview } from './curated-source-review.ts';

const sourceId = 'guanyijia_mysql';
const snapshotId = '20260813T032528Z-abb0502c7d79';
const input = { sourceId, snapshotId };

const selectedTables = [
  'jsh_account', 'jsh_account_head', 'jsh_account_item', 'jsh_depot',
  'jsh_depot_head', 'jsh_depot_item', 'jsh_function', 'jsh_in_out_item',
  'jsh_material', 'jsh_material_attribute', 'jsh_material_category',
  'jsh_material_current_stock', 'jsh_material_extend', 'jsh_material_initial_stock',
  'jsh_material_property', 'jsh_msg', 'jsh_orga_user_rel', 'jsh_organization',
  'jsh_person', 'jsh_platform_config', 'jsh_role', 'jsh_serial_number',
  'jsh_supplier', 'jsh_sys_dict_data', 'jsh_sys_dict_type', 'jsh_system_config',
  'jsh_tenant', 'jsh_unit', 'jsh_user', 'jsh_user_business',
] as const;

const selectedProcedures = [
  'sp_rebalance_below_low_stock_requisition',
  'sp_rebalance_over_high_stock_sales',
  'sp_run_inventory_stock_rebalance',
  'sp_run_retail_return_rate_and_fact_sync',
  'sp_run_store_retail_return_coverage_and_fact_sync',
  'sp_sync_retail_out_fact_batch',
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
] as const;

function readOrThrow() {
  const review = readCuratedSourceReview(input);
  assert.ok(review, '匹配的 MySQL 审阅文档必须可读');
  return review as any;
}

function expectCorrupt(mutator: (asset: any) => void, label: string) {
  const original = structuredClone(generatedCuratedMysqlReview);
  try {
    mutator(generatedCuratedMysqlReview as any);
    assert.throws(() => readCuratedSourceReview(input), /真实审阅文档校验失败/, label);
  } finally {
    for (const key of Object.keys(generatedCuratedMysqlReview as any)) {
      delete (generatedCuratedMysqlReview as any)[key];
    }
    Object.assign(generatedCuratedMysqlReview as any, original);
  }
}

function canonicalOutputDigest(asset: any) {
  const { outputDigest: _ignoredOutputDigest, ...generation } = asset.generationManifest;
  return `sha256:${sha256HexSync(JSON.stringify({
    domain: 'guanyijia-curated-mysql-review/output/v1',
    value: {
      schemaVersion: asset.schemaVersion,
      sourceId: asset.sourceId,
      snapshotId: asset.snapshotId,
      title: asset.title,
      coverageLabel: asset.coverageLabel,
      markdown: asset.markdown,
      markdownSha256: asset.markdownSha256,
      evidence: asset.evidence,
      traceLinks: asset.traceLinks,
      generation,
    },
  }))}`;
}

test('curated MySQL review freezes the exact nine-section 30-table projection', () => {
  const review = readOrThrow();
  assert.equal(review.schemaVersion, 1);
  assert.equal(review.sourceId, sourceId);
  assert.equal(review.snapshotId, snapshotId);
  assert.equal(review.title, '数据库建模审阅（真实证据节选）');
  assert.equal(review.coverageLabel, '30 / 95 张表');
  assert.equal((review.markdown.match(/^## /gm) ?? []).length, 9);
  assert.equal(review.evidence.length, 39);
  assert.equal(review.traceLinks.length, 48);
  assert.match(review.markdown, /负库存/);
  assert.match(review.markdown, /欠款字段/);
  assert.match(review.markdown, /单据状态/);
  assert.match(review.markdown, /后续来源确认/);
  assert.doesNotMatch(review.markdown, /GitHub|统一禁止负库存|官方状态 9|时间漂移/);

  const actualTables = review.evidence
    .filter((item: any) => item.evidenceClass === 'OBSERVED' && item.locationValue.startsWith('ddl/tables/'))
    .map((item: any) => item.locationValue.slice('ddl/tables/'.length, -'.sql'.length))
    .sort();
  assert.deepEqual(actualTables, [...selectedTables].sort(), 'curated projection 必须冻结精确的 30 张表集合');
  for (const table of selectedTables) {
    const tableEvidence = review.evidence.filter((item: any) =>
      item.evidenceClass === 'OBSERVED' && item.locationValue === `ddl/tables/${table}.sql`);
    assert.equal(tableEvidence.length, 1, `${table}必须有一个 MySQL DDL 定位`);
    assert.match(tableEvidence[0].excerpt, new RegExp('CREATE TABLE `' + table + '`'));
  }
  const actualProcedures = review.evidence
    .filter((item: any) => item.evidenceClass === 'OBSERVED' && item.locationValue.startsWith('programmability/procedures/'))
    .map((item: any) => item.locationValue.slice('programmability/procedures/'.length, -'.sql'.length))
    .sort();
  assert.deepEqual(actualProcedures, [...selectedProcedures].sort(), 'curated projection 必须冻结精确的 6 个过程集合');
  for (const procedure of selectedProcedures) {
    const procedureEvidence = review.evidence.filter((item: any) =>
      item.evidenceClass === 'OBSERVED'
      && item.locationValue === `programmability/procedures/${procedure}.sql`);
    assert.equal(procedureEvidence.length, 1, `${procedure}必须有一个过程定位`);
    assert.match(procedureEvidence[0].excerpt, new RegExp('PROCEDURE `' + procedure + '`'));
  }
});

test('curated MySQL evidence remains source-pure and traceable', () => {
  const review = readOrThrow();
  assert.equal(review.markdownSha256, `sha256:${sha256HexSync(review.markdown)}`);
  assert.equal(review.generationManifest.provider, 'CODEX_CHATGPT_SESSION');
  assert.equal(review.generationManifest.model, 'gpt-5.6-luna');
  assert.equal(review.generationManifest.reasoningEffort, 'xhigh');
  assert.match(review.generationManifest.inputDigest, /^sha256:[0-9a-f]{64}$/);
  assert.equal(review.generationManifest.outputDigest, canonicalOutputDigest(review));

  const evidenceRefs = new Set<string>();
  for (const item of review.evidence) {
    assert.equal(evidenceRefs.has(item.evidenceRef), false, `证据 ref 不得重复：${item.evidenceRef}`);
    evidenceRefs.add(item.evidenceRef);
    assert.equal(item.excerptSha256, `sha256:${sha256HexSync(item.excerpt)}`, item.evidenceRef);
    assert.ok(item.title && item.locationLabel && item.locationValue);
    assert.ok(Array.isArray(item.affectedObjectRefs));
    assert.equal(item.evidenceClass, 'OBSERVED');
    assert.match(item.locationValue, /^(ddl\/tables|programmability\/procedures|constraints|dml)\//);
    assert.doesNotMatch(item.locationValue, /github|official|policy/i);
  }
  assert.equal(review.evidence.some((item: any) => item.evidenceClass !== 'OBSERVED'), false);
  assert.doesNotMatch(JSON.stringify(review), /GitHub|官方资料缺口|候选目标制度/);
});

test('every trace prefix is unique, anchored once, and covers every MySQL evidence item', () => {
  const review = readOrThrow();
  const markdown = review.markdown;
  const seenEvidence = new Set<string>();
  const seenPrefixes = new Set<string>();
  const seenAnchors = new Set<string>();
  for (const trace of review.traceLinks) {
    assert.equal(seenPrefixes.has(trace.linePrefix), false, `重复 trace prefix：${trace.linePrefix}`);
    seenPrefixes.add(trace.linePrefix);
    assert.equal((markdown.match(new RegExp(escapeRegExp(trace.linePrefix), 'g')) ?? []).length, 1, trace.linePrefix);
    assert.equal((markdown.match(new RegExp(`${escapeRegExp(trace.markdownAnchor)}(?=\\s*-->)`, 'g')) ?? []).length, 1, trace.markdownAnchor);
    assert.ok(trace.evidenceRefs.length > 0, trace.linePrefix);
    assert.equal(seenAnchors.has(trace.markdownAnchor), false, `重复 Markdown anchor：${trace.markdownAnchor}`);
    seenAnchors.add(trace.markdownAnchor);
    for (const evidenceRef of trace.evidenceRefs) {
      assert.ok(review.evidence.some((item: any) => item.evidenceRef === evidenceRef), `${trace.linePrefix}引用未知证据`);
      seenEvidence.add(evidenceRef);
    }
  }
  for (const item of review.evidence) {
    assert.equal(seenEvidence.has(item.evidenceRef), true, `${item.evidenceRef}未被正文 trace 覆盖`);
  }
  for (const string of forbiddenStrings) {
    assert.equal(markdown.includes(string), false, `正文含禁用模板或越界词：${string}`);
    assert.equal(JSON.stringify(review.evidence).includes(string), false, `证据含禁用数据：${string}`);
  }
});

test('visible SQL fences quote one contiguous OBSERVED excerpt selected by their trace', () => {
  const review = readOrThrow();
  const sqlFences = [...review.markdown.matchAll(/```sql\n([\s\S]*?)```/g)];
  assert.ok(sqlFences.length > 0, '审阅文档必须保留可定位的 SQL 原文节选');

  for (const fence of sqlFences) {
    const fenceText = fence[1];
    const beforeFence = review.markdown.slice(0, fence.index);
    const anchor = [...beforeFence.matchAll(/<!-- ([a-z0-9-]+) -->/g)].at(-1)?.[1];
    const trace = review.traceLinks.find((candidate: any) => candidate.markdownAnchor === anchor);
    assert.ok(trace, '每个 SQL 围栏必须跟随一个 trace anchor');
    const observedExcerpt = trace.evidenceRefs
      .map((evidenceRef: string) => review.evidence.find((item: any) => item.evidenceRef === evidenceRef))
      .find((item: any) => item?.evidenceClass === 'OBSERVED' && item.excerpt.includes(fenceText));
    assert.ok(observedExcerpt, `${trace.linePrefix} 的 SQL 围栏必须是同一 OBSERVED 摘录的连续原文`);
  }
});

test('index, foreign-key, and DML evidence use semantic primary traces and exact frozen ranges', () => {
  const review = readOrThrow();
  const primaryTrace = (evidenceRef: string) => review.traceLinks.find((trace: any) => (
    trace.evidenceRefs.includes(evidenceRef)
  ));
  assert.equal(primaryTrace('mysql:constraints:indexes')?.markdownAnchor, 'curated-g5');
  assert.equal(primaryTrace('mysql:constraints:foreign-keys')?.markdownAnchor, 'curated-g5');
  assert.equal(primaryTrace('mysql:dml:digests')?.markdownAnchor, 'curated-g7');

  const indexes = review.evidence.find((item: any) => item.evidenceRef === 'mysql:constraints:indexes');
  assert.equal(indexes.locationValue, 'constraints/indexes.sql#L112-L113');
  assert.equal(indexes.excerpt,
    'CREATE INDEX `idx_tenant_subtype_status_id` ON `jsh_depot_head` (`tenant_id`, `sub_type`, `status`, `delete_flag`, `id`);\n'
    + 'CREATE INDEX `idx_tenant_type_subtype_id` ON `jsh_depot_head` (`tenant_id`, `type`, `sub_type`, `id`, `number`);');

  const dml = review.evidence.find((item: any) => item.evidenceRef === 'mysql:dml:digests');
  assert.equal(dml.locationValue, 'dml/digests.sql#L3');
  assert.equal(dml.excerpt,
    '-- SELECT `header_id` , `oper_number` FROM `jsh_depot_item` FORCE INDEX ( `FK2A819F474BB6190E` ) WHERE `header_id` IN (...) AND ( `delete_flag` = ? OR `delete_flag` IS NULL );');
});

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

test('matching reader rejects every mutable integrity break and clones the returned review', () => {
  const review = readOrThrow();
  const returned = review as any;
  returned.markdown = `${returned.markdown}\nchanged`;
  assert.equal(readOrThrow().markdown.endsWith('changed'), false, '返回值必须是 clone');

  expectCorrupt((asset) => { asset.markdown += '\\nbyte mutation'; }, 'Markdown 字节变化');
  expectCorrupt((asset) => { asset.evidence[0].excerpt += '\\nexcerpt mutation'; }, 'excerpt 变化');
  expectCorrupt((asset) => { asset.evidence[0].excerptSha256 = 'sha256:' + '0'.repeat(64); }, 'excerpt hash 变化');
  expectCorrupt((asset) => { asset.evidence[0].locationValue = 'samples/tenant-153/rows.json'; }, '越界 locator');
  expectCorrupt((asset) => { asset.evidence[0].evidenceClass = 'FROZEN_RECORD'; }, 'source class 变化');
  expectCorrupt((asset) => { asset.traceLinks.push({ ...asset.traceLinks[0] }); }, '重复 trace prefix');
  expectCorrupt((asset) => { asset.traceLinks[0].evidenceRefs = ['missing:evidence']; }, 'missing reference');
  expectCorrupt((asset) => {
    asset.evidence[0].evidenceClass = 'FROZEN_RECORD';
  }, 'non-MySQL evidence class');
  expectCorrupt((asset) => { asset.traceLinks[0].linePrefix = asset.traceLinks[1].linePrefix; }, 'duplicate line prefix');
  expectCorrupt((asset) => { asset.traceLinks[0].markdownAnchor = asset.traceLinks[1].markdownAnchor; }, 'duplicate anchor');
});

test('reader rejects a rehashed removal of a selected table', () => {
  expectCorrupt((asset) => {
    const removedRef = 'mysql:table:jsh_account';
    asset.evidence = asset.evidence.filter((item: any) => item.evidenceRef !== removedRef);
    asset.traceLinks = asset.traceLinks.map((trace: any) => ({
      ...trace,
      evidenceRefs: trace.evidenceRefs.filter((evidenceRef: string) => evidenceRef !== removedRef),
    })).filter((trace: any) => trace.evidenceRefs.length > 0);
    asset.generationManifest.outputDigest = canonicalOutputDigest(asset);
  }, 'rehashed removal of a scoped table');
});

test('reader rejects a rehashed replacement of a selected table', () => {
  expectCorrupt((asset) => {
    const account = asset.evidence.find((item: any) => item.evidenceRef === 'mysql:table:jsh_account');
    const role = asset.evidence.find((item: any) => item.evidenceRef === 'mysql:table:jsh_role');
    assert.ok(account && role);
    asset.evidence = asset.evidence
      .filter((item: any) => !['mysql:table:jsh_account', 'mysql:table:jsh_role'].includes(item.evidenceRef))
      .concat({ ...account, ...role });
    asset.traceLinks = asset.traceLinks.map((trace: any) => ({
      ...trace,
      evidenceRefs: trace.evidenceRefs.map((evidenceRef: string) => evidenceRef === 'mysql:table:jsh_account' ? 'mysql:table:jsh_role' : evidenceRef),
    }));
    asset.generationManifest.outputDigest = canonicalOutputDigest(asset);
  }, 'rehashed replacement of a scoped table');
});

test('reader rejects a tampered output digest', () => {
  expectCorrupt((asset) => {
    asset.generationManifest.outputDigest = `sha256:${'0'.repeat(64)}`;
  }, 'tampered output digest');
});

test('reader rejects loss of a source-local governance trace', () => {
  expectCorrupt((asset) => {
    asset.traceLinks = asset.traceLinks.map((trace: any) => ({
      ...trace,
      evidenceRefs: trace.evidenceRefs.filter((evidenceRef: string) => evidenceRef !== 'mysql:table:jsh_system_config'),
    }));
    asset.generationManifest.outputDigest = canonicalOutputDigest(asset);
  }, 'status-9 GAP trace coverage');
});

test('nonmatching source or snapshot does not read the curated MySQL document', () => {
  assert.equal(readCuratedSourceReview({ sourceId: 'guanyijia_github', snapshotId }), undefined);
  assert.equal(readCuratedSourceReview({ sourceId, snapshotId: 'other-snapshot' }), undefined);
});
