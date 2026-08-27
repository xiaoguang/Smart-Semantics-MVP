import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import {
  canonicalModelingJson,
  modelingDocumentSemanticPayloadSha256,
  standardSectionOrder,
} from '../modeling-document-bridge/standard-markdown.ts';
import {
  createGuanyijiaStandardizationStory,
  defaultGuanyijiaPolicyDocuments,
} from './index.ts';
import type { SourceDocumentCompilation } from './types.ts';

function assertCompilationContract(compilation: SourceDocumentCompilation) {
  assert.deepEqual(Object.keys(compilation.sections), standardSectionOrder.map(({ key }) => key));
  assert.equal(compilation.blocks.length, compilation.assertions.length);
  assert.equal(new Set(compilation.blocks.map((block) => block.blockId)).size, compilation.blocks.length);
  assert.equal(new Set(compilation.assertions.map((assertion) => assertion.assertionId)).size, compilation.assertions.length);
  assert.equal(new Set(compilation.blocks.map((block) => canonicalModelingJson({
    stableCode: block.stableCode, value: block.value, evidenceRefs: block.evidenceRefs,
  }))).size, compilation.blocks.length);
  for (const block of compilation.blocks) {
    const assertion = compilation.assertions.find((candidate) => candidate.assertionId === block.blockId);
    assert.ok(assertion, `block ${block.blockId} 必须投影同身份 assertion`);
    assert.equal(assertion.section, block.section);
    assert.deepEqual(assertion.evidenceRefs, block.evidenceRefs);
    assert.ok(compilation.sections[block.section].includes(assertion.statement));
    assert.ok(compilation.markdown.includes(assertion.statement));
  }
  for (const { heading } of standardSectionOrder) assert.ok(compilation.markdown.includes(`## ${heading}`));
}

test('五源按冻结顺序暴露真实、演示与派生身份', () => {
  const sources = createGuanyijiaStandardizationStory().listSources();

  assert.deepEqual(sources.map((source) => ({
    sourceId: source.sourceId,
    sourceClass: source.sourceClass,
    authority: source.authority,
    lineageStatus: source.lineageStatus,
    upstreamSourceIds: source.upstreamSourceIds,
  })), [
    { sourceId: 'guanyijia_mysql', sourceClass: 'REAL', authority: 'PRIMARY', lineageStatus: 'ROOT', upstreamSourceIds: [] },
    { sourceId: 'guanyijia_github', sourceClass: 'REAL', authority: 'CORROBORATING', lineageStatus: 'ROOT', upstreamSourceIds: [] },
    { sourceId: 'guanyijia_official_docs', sourceClass: 'REAL', authority: 'AUXILIARY', lineageStatus: 'ROOT', upstreamSourceIds: [] },
    { sourceId: 'guanyijia_demo_policy', sourceClass: 'DEMO_POLICY', authority: 'AUXILIARY', lineageStatus: 'ROOT', upstreamSourceIds: [] },
    { sourceId: 'guanyijia_semantica_demo', sourceClass: 'DERIVED', authority: 'DERIVED', lineageStatus: 'DERIVED_VALID', upstreamSourceIds: ['guanyijia_demo_policy'] },
  ]);
  assert.equal(sources[0]?.snapshotId, '20260813T032528Z-abb0502c7d79');
  assert.equal(sources[1]?.snapshotId, '20260813032126Z-5821d0ece9b1');
  assert.equal(sources[2]?.snapshotId, 'gyjerp-official-docs-20260813T031656Z');
});

test('默认故事优先使用仓库固定的完整来源快照，而不是重新编译来源', () => {
  const canonical = createGuanyijiaStandardizationStory();
  const snapshot: SourceDocumentCompilation[] = [];
  for (const source of canonical.listSources()) {
    snapshot.push(canonical.compileSource({ sourceId: source.sourceId, priorCompilations: snapshot }));
  }
  snapshot[0]!.readSummary.summary = '固定快照已载入';

  const story = createGuanyijiaStandardizationStory({ pinnedSourceSnapshot: snapshot });
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });

  assert.equal(mysql.readSummary.summary, '固定快照已载入');
});

test('默认故事从受校验的仓库固定 Bundle 读取五份完整编译结果', async () => {
  const bundleModule = await import('./pinned-source-snapshot.ts').catch(() => undefined);
  assert.ok(bundleModule, '固定来源 Bundle 必须随仓库交付');

  const bundle = bundleModule.pinnedGuanyijiaFiveSourceSnapshot;
  assert.equal(bundle.storyKey, 'guanyijia-five-source-v1');
  assert.equal(bundle.compilations.length, 5);
  assert.match(bundle.bundleSha256, /^sha256:[a-f0-9]{64}$/);

  const story = createGuanyijiaStandardizationStory();
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  assert.deepEqual(mysql, bundle.compilations[0]);
});

test('五源生产 story 不依赖旧 final Artifact 构建来源身份或官方文档', () => {
  for (const fileName of ['index.ts', 'compiler.ts']) {
    const source = readFileSync(new URL(fileName, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /guanyijia-modeling-baseline|guanyijia-modeling-package/);
  }
});

test('MySQL 从当前 manifest 编译九段结构并独占 63 项待归类资产与库存时点缺口', () => {
  const story = createGuanyijiaStandardizationStory();
  const first = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const repeated = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });

  assertCompilationContract(first);
  assert.deepEqual(repeated, first, '同一冻结来源必须确定性编译');
  assert.equal(first.snapshotId, '20260813T032528Z-abb0502c7d79');
  assert.equal(first.readSummary.objectCount, 95);
  assert.equal(first.readSummary.evidenceCount, 1458);
  assert.deepEqual(first.readSummary.objectCounts, {
    tables: 95, views: 2, columns: 1130, procedures: 35, functions: 0, triggers: 0,
    events: 1, indexes: 270, foreignKeys: 2, tenantTables: 58, tenantViews: 2, dmlDigests: 17,
  });
  assert.equal(first.blocks.filter((block) => block.semanticKind === 'PENDING_ASSET').length, 63);
  assert.equal(first.blocks.filter((block) => block.stableCode === 'gap.current_stock_as_of').length, 1);
  assert.equal(first.blocks.some((block) => block.stableCode === 'schema.jsh_depot_head.debt_fields'
    && JSON.stringify(block.value).includes('DEPLOYED_SCHEMA_NO_DEBT')), true);
  for (const table of ['jsh_supplier', 'jsh_depot_head', 'jsh_depot_item', 'jsh_material', 'jsh_material_extend']) {
    assert.equal(first.blocks.some((block) => JSON.stringify(block.value).includes(table)), true, `${table} 必须作为物理事实进入 block`);
  }
  for (const field of ['oper_time', 'bill_time', 'create_time']) {
    assert.equal(first.blocks.some((block) => JSON.stringify(block.value).includes(field)), true, `${field} 必须作为物理字段进入 block`);
  }
  assert.ok(first.blocks.every((block) => block.evidenceRefs.every((ref) => ref.startsWith('mysql_jsh_erp@v1:'))));
  assert.equal(first.introducedConflictIds.length, 0);
});

test('GitHub 从固定 commit manifest 补充行为并在部署基准之后引入 debt schema 冲突', () => {
  const story = createGuanyijiaStandardizationStory();
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const github = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [mysql] });

  assertCompilationContract(github);
  assert.equal(github.readSummary.objectCount, 436);
  assert.equal(github.readSummary.evidenceCount, 2252);
  assert.equal(github.readSummary.versionRef, 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1');
  assert.deepEqual(github.readSummary.objectCounts, {
    selectedFiles: 436, tables: 32, views: 0, services: 31, controllers: 30,
    entitiesAndVos: 104, mapperJavaFiles: 61, mapperXmlFiles: 61, mapperStatements: 573,
    constants: 2, tenancyFiles: 1, uiVocabularyFiles: 142, migrationFiles: 1,
    workflowMethods: 642, migrationOperations: 570,
  });
  for (const normalized of [
    'PURCHASE_RECEIPT', 'PURCHASE_RETURN', 'SALES_DELIVERY', 'SALES_RETURN', 'STOCK_TRANSFER',
    'STOCKTAKE_REVIEW', 'ASSEMBLY', 'DISASSEMBLY', 'FINANCIAL_TRANSACTION',
  ]) {
    assert.equal(github.blocks.some((block) => JSON.stringify(block.value).includes(normalized)), true, `${normalized} 必须由源码证据支持`);
  }
  for (const normalized of [
    'SOURCE_SCHEMA_HAS_DEBT', 'TENANT_CONFIG_CONTROLS', 'REVIEWING_UNCONFIRMED_ENUM',
  ]) assert.equal(github.blocks.some((block) => JSON.stringify(block.value).includes(normalized)), true);
  assert.deepEqual(github.introducedConflictIds, ['gyj-conflict-debt-schema']);
  assert.equal(github.delta.changedBlockIds.some((id) => id.includes('debt_fields')), true);
  assert.ok(github.blocks.every((block) => block.evidenceRefs.every((ref) => ref.startsWith('github_jshERP@v1:'))));
  assert.equal(github.blocks.some((block) => block.semanticKind === 'PENDING_ASSET'
    || block.stableCode === 'gap.current_stock_as_of'), false);
  assert.match(github.sections.UNRESOLVED, /候选|待补充|不能作为正式/);
});

test('官方核心文档使用冻结 14 条证据补充业务定义且不覆盖部署事实', () => {
  const story = createGuanyijiaStandardizationStory();
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const github = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [mysql] });
  const official = story.compileSource({
    sourceId: 'guanyijia_official_docs', priorCompilations: [mysql, github],
  });

  assertCompilationContract(official);
  assert.equal(official.readSummary.objectCount, 4);
  assert.equal(official.readSummary.evidenceCount, 14);
  assert.deepEqual(official.readSummary.objectCounts, { pages: 4, claims: 20 });
  assert.equal(official.readSummary.versionRef, '2026-08-13T03:16:56Z');
  const evidence = new Set(official.blocks.flatMap((block) => block.evidenceRefs));
  assert.equal(evidence.size, 14);
  assert.ok([...evidence].every((ref) => ref.startsWith('official:')));
  assert.equal(Object.keys(official.evidenceLocators).length, 14);
  for (const normalized of [
    'COUNTERPARTY_ROLES_DEFINED', 'PURCHASE_AND_SALES_FLOWS_DEFINED',
    'PRODUCT_CATEGORY_HIERARCHY_DEFINED', 'PURCHASE_AND_SALES_METRIC_NAMES_DEFINED',
    'OPER_TIME_IS_BUSINESS_TIME', 'BILL_TIME_IS_BUSINESS_TIME',
  ]) assert.equal(official.blocks.some((block) => JSON.stringify(block.value).includes(normalized)), true);
  assert.deepEqual(official.introducedConflictIds, []);
  assert.equal(official.blocks.some((block) => block.stableCode === 'schema.jsh_depot_head.debt_fields'
    || block.stableCode === 'rule.negative_stock'), false);
});

test('演示制度以醒目标记和确定 locator 引入负库存、状态 9 两项故意冲突', () => {
  const story = createGuanyijiaStandardizationStory();
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const github = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [mysql] });
  const official = story.compileSource({ sourceId: 'guanyijia_official_docs', priorCompilations: [mysql, github] });
  const policy = story.compileSource({
    sourceId: 'guanyijia_demo_policy', priorCompilations: [mysql, github, official],
  });

  assertCompilationContract(policy);
  assert.equal(policy.readSummary.objectCount, 3);
  assert.equal(policy.readSummary.evidenceCount, 4);
  assert.deepEqual(policy.introducedConflictIds, [
    'gyj-conflict-negative-stock', 'gyj-conflict-status-nine',
  ]);
  assert.ok(policy.blocks.every((block) => block.evidenceRefs.every((ref) => ref.startsWith('demo-policy://'))));
  assert.ok(Object.keys(policy.evidenceLocators).every((ref) => ref.startsWith('demo-policy://')));
  assert.match(policy.markdown, /演示制度，不是真实生产制度/);
  assert.doesNotMatch(policy.markdown, /demo-policy:\/\/|jsh_|current_stock_as_of/);
  assert.equal(policy.blocks.some((block) => JSON.stringify(block.value).includes('ALWAYS_FORBIDDEN')), true);
  assert.equal(policy.blocks.some((block) => JSON.stringify(block.value).includes('PENDING_REVIEW')), true);
  assert.equal(policy.blocks.some((block) => block.stableCode === 'gap.current_stock_as_of'), false,
    '制度建议不得伪装成已证明或新建库存时点事实');

  const definitions = story.listConflictDefinitions();
  assert.deepEqual(definitions.map((definition) => definition.conflictId), [
    'gyj-conflict-debt-schema', 'gyj-conflict-negative-stock', 'gyj-conflict-status-nine',
  ]);
  assert.deepEqual(definitions.map((definition) => definition.variants.map((variant) => variant.normalizedValue)), [
    ['DEPLOYED_SCHEMA_NO_DEBT', 'SOURCE_SCHEMA_HAS_DEBT'],
    ['TENANT_CONFIG_CONTROLS', 'ALWAYS_FORBIDDEN'],
    ['REVIEWING_UNCONFIRMED_ENUM', 'PENDING_REVIEW'],
  ]);
});

test('冲突Hunk从指定持久化revision按定义精确定位Current Incoming与已读取佐证', () => {
  const story = createGuanyijiaStandardizationStory();
  const compilations: SourceDocumentCompilation[] = [];
  const revisions: Array<{
    documentId: string;
    documentRevision: number;
    compilation: SourceDocumentCompilation;
  }> = [];
  for (const source of story.listSources()) {
    const compilation = story.compileSource({ sourceId: source.sourceId, priorCompilations: compilations });
    compilations.push(compilation);
    revisions.push({
      documentId: `persisted:${source.sourceId}`,
      documentRevision: source.sourceId === 'guanyijia_demo_policy' ? 3 : 1,
      compilation,
    });
  }

  const debt = story.buildConflictHunk({
    conflictId: 'gyj-conflict-debt-schema',
    sourceRevisions: revisions.slice(0, 2),
  });
  assert.equal(debt.current.role, 'CURRENT');
  assert.equal(debt.current.sourceId, 'guanyijia_mysql');
  assert.equal(debt.current.documentId, 'persisted:guanyijia_mysql');
  assert.equal(debt.current.block.stableCode, 'schema.jsh_depot_head.debt_fields');
  assert.equal(debt.incoming.role, 'INCOMING');
  assert.equal(debt.incoming.sourceId, 'guanyijia_github');
  assert.equal(debt.corroborating.length, 0);
  assert.match(debt.hunkSha256, /^sha256:[a-f0-9]{64}$/);

  const policy = story.buildConflictHunk({
    conflictId: 'gyj-conflict-negative-stock',
    sourceRevisions: revisions,
  });
  assert.equal(policy.current.sourceId, 'guanyijia_github');
  assert.equal(policy.incoming.sourceId, 'guanyijia_demo_policy');
  assert.equal(policy.incoming.documentRevision, 3);
  assert.deepEqual(policy.corroborating.map((side) => ({
    role: side.role,
    sourceId: side.sourceId,
    stableCode: side.block.stableCode,
  })), [{
    role: 'CORROBORATING',
    sourceId: 'guanyijia_semantica_demo',
    stableCode: 'derived.rule.negative_stock',
  }]);
});

test('三项固定策略由同一纯投影生成Patch USER_CONFIRMED断言真实行Diff与精确对象处置', () => {
  const story = createGuanyijiaStandardizationStory();
  const compilations: SourceDocumentCompilation[] = [];
  const revisions = story.listSources().slice(0, 4).map((source) => {
    const compilation = story.compileSource({ sourceId: source.sourceId, priorCompilations: compilations });
    compilations.push(compilation);
    return { documentId: `persisted:${source.sourceId}`, documentRevision: 1, compilation };
  });
  const debt = story.previewConflictResolution({
    conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT', sourceRevisions: revisions,
  });
  assert.deepEqual(debt.result.objectDispositions.map(({ objectRef, disposition }) => ({
    objectId: objectRef.objectId, disposition,
  })), [
    { objectId: 'receivable_debt', disposition: 'EXCLUDED' },
    { objectId: 'deposit', disposition: 'CANDIDATE_ONLY' },
  ]);
  assert.equal(debt.result.blocks.length, 1);
  assert.equal(debt.result.blocks[0]?.stableCode, 'schema.jsh_depot_head.debt_fields');

  const negative = story.previewConflictResolution({
    conflictId: 'gyj-conflict-negative-stock', strategy: 'MERGE', sourceRevisions: revisions,
  });
  assert.deepEqual(negative.result.blocks.map((block) => block.stableCode), [
    'rule.negative_stock', 'resolution.gap.negative_stock_policy_not_implemented',
  ]);
  assert.equal(negative.result.blocks[1]?.evidenceStatus, 'GAP');
  assert.match(JSON.stringify(negative.result.blocks[1]?.value), /统一禁止负库存制度尚未落地/);
  assert.deepEqual(negative.result.objectDispositions.map(({ objectRef, disposition }) => ({
    objectId: objectRef.objectId, disposition,
  })), [{ objectId: 'negative_stock', disposition: 'CANDIDATE_ONLY' }]);

  const status = story.previewConflictResolution({
    conflictId: 'gyj-conflict-status-nine', strategy: 'DEFER_AS_GAP', sourceRevisions: revisions,
  });
  assert.deepEqual(status.result.blocks.map((block) => block.stableCode), [
    'dimension.document_status.nine', 'resolution.gap.status_nine_unconfirmed',
  ]);
  assert.deepEqual(status.result.objectDispositions.map(({ objectRef, disposition }) => ({
    objectId: objectRef.objectId, disposition,
  })), [
    { objectId: 'document_status', disposition: 'KEEP_WITHOUT_ENUM_MEMBER' },
    { objectId: 'audit_status', disposition: 'CANDIDATE_ONLY' },
  ]);

  for (const preview of [debt, negative, status]) {
    assert.ok(preview.result.assertions.every((assertion) => assertion.provenance === 'USER_CONFIRMED'));
    assert.deepEqual(
      preview.structuredPatch.blockOperations.map((operation) => operation.block),
      preview.result.blocks,
    );
    assert.deepEqual(
      preview.structuredPatch.assertionOperations.map((operation) => operation.assertion),
      preview.result.assertions,
    );
    assert.equal(preview.markdownDiff.some((line) => line.type === 'REMOVED'), true);
    assert.equal(preview.markdownDiff.some((line) => line.type === 'ADDED'), true);
    assert.deepEqual(preview.provenanceSources.slice(0, 2).map((source) => source.role), [
      'CURRENT', 'INCOMING',
    ]);
    assert.match(preview.previewSha256, /^sha256:[a-f0-9]{64}$/);
  }
});

test('Semantica 只从制度派生并在上游缺席时显式降级为血缘缺口', () => {
  const story = createGuanyijiaStandardizationStory();
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const github = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [mysql] });
  const official = story.compileSource({ sourceId: 'guanyijia_official_docs', priorCompilations: [mysql, github] });
  const policy = story.compileSource({
    sourceId: 'guanyijia_demo_policy', priorCompilations: [mysql, github, official],
  });
  const prior = [mysql, github, official, policy];
  const semantica = story.compileSource({ sourceId: 'guanyijia_semantica_demo', priorCompilations: prior });

  assertCompilationContract(semantica);
  assert.equal(semantica.lineageStatus, 'DERIVED_VALID');
  assert.deepEqual(semantica.upstreamSourceIds, ['guanyijia_demo_policy']);
  assert.deepEqual(semantica.introducedConflictIds, []);
  assert.deepEqual(semantica.corroboratedConflictIds, [
    'gyj-conflict-negative-stock', 'gyj-conflict-status-nine',
  ]);
  assert.equal(story.listSources().filter((source) => source.lineageStatus === 'ROOT').length, 4);
  assert.ok(semantica.blocks.every((block) => (
    block.evidenceRefs.some((ref) => ref.startsWith('semantica://'))
    && block.evidenceRefs.some((ref) => ref.startsWith('demo-policy://'))
  )));
  for (const normalized of [
    'DERIVED_COUNTERPARTY_ROLES', 'DERIVED_PURCHASE_RECEIPT', 'DERIVED_SALES_DELIVERY',
    'DERIVED_RETURN_CONCEPTS', 'DERIVED_STATUS_NINE_TERM', 'DERIVED_NEGATIVE_STOCK_RULE_CANDIDATE',
  ]) assert.equal(semantica.blocks.some((block) => JSON.stringify(block.value).includes(normalized)), true);

  const missing = story.compileSource({
    sourceId: 'guanyijia_semantica_demo', priorCompilations: [mysql, github, official],
  });
  assertCompilationContract(missing);
  assert.equal(missing.lineageStatus, 'UPSTREAM_MISSING');
  assert.equal(missing.blocks.some((block) => block.stableCode === 'gap.semantica.upstream'
    && block.semanticKind === 'GAP'), true);
  assert.equal(missing.blocks.some((block) => block.evidenceStatus === 'FACT'), false);

  const upstreamMutations: SourceDocumentCompilation[] = [];
  const missingBlockRef = structuredClone(policy);
  const policyNegativeBlock = missingBlockRef.blocks.find((block) => block.stableCode === 'rule.negative_stock');
  assert.ok(policyNegativeBlock);
  policyNegativeBlock.evidenceRefs = [];
  upstreamMutations.push(missingBlockRef);
  const missingAssertionRef = structuredClone(policy);
  const policyNegativeAssertion = missingAssertionRef.assertions.find((assertion) => assertion.assertionId === 'gyj-block:rule.negative_stock');
  assert.ok(policyNegativeAssertion);
  policyNegativeAssertion.evidenceRefs = [];
  upstreamMutations.push(missingAssertionRef);
  const missingLocator = structuredClone(policy);
  const policyNegativeRef = policy.blocks.find((block) => block.stableCode === 'rule.negative_stock')?.evidenceRefs[0];
  assert.ok(policyNegativeRef);
  delete missingLocator.evidenceLocators[policyNegativeRef];
  upstreamMutations.push(missingLocator);
  const damagedMarkdown = structuredClone(policy);
  damagedMarkdown.markdown += '\n损坏';
  upstreamMutations.push(damagedMarkdown);
  for (const damagedPolicy of upstreamMutations) {
    const damaged = story.compileSource({
      sourceId: 'guanyijia_semantica_demo', priorCompilations: [mysql, github, official, damagedPolicy],
    });
    assert.equal(damaged.lineageStatus, 'UPSTREAM_MISSING');
    assert.equal(damaged.blocks.some((block) => block.stableCode === 'gap.semantica.upstream'), true);
  }

  const all = [...prior, semantica];
  assert.deepEqual(all.filter((item) => item.blocks.some((block) => block.semanticKind === 'PENDING_ASSET'))
    .map((item) => item.sourceId), ['guanyijia_mysql']);
  assert.deepEqual(all.filter((item) => item.blocks.some((block) => block.stableCode === 'gap.current_stock_as_of'))
    .map((item) => item.sourceId), ['guanyijia_mysql']);
});

test('冲突只在可信 prior variant 到齐时逐项触发', () => {
  const story = createGuanyijiaStandardizationStory();
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const emptyMysql = structuredClone(mysql);
  emptyMysql.blocks = [];
  emptyMysql.assertions = [];
  assert.deepEqual(story.compileSource({
    sourceId: 'guanyijia_github', priorCompilations: [emptyMysql],
  }).introducedConflictIds, []);
  const wrongMysqlSnapshot = structuredClone(mysql);
  wrongMysqlSnapshot.snapshotId = 'fabricated-mysql-snapshot';
  assert.deepEqual(story.compileSource({
    sourceId: 'guanyijia_github', priorCompilations: [wrongMysqlSnapshot],
  }).introducedConflictIds, []);

  const github = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [mysql] });
  const emptyGithub = structuredClone(github);
  emptyGithub.blocks = [];
  emptyGithub.assertions = [];
  assert.deepEqual(story.compileSource({
    sourceId: 'guanyijia_demo_policy', priorCompilations: [mysql, emptyGithub],
  }).introducedConflictIds, []);
  const wrongGithubSnapshot = structuredClone(github);
  wrongGithubSnapshot.snapshotId = 'fabricated-github-snapshot';
  assert.deepEqual(story.compileSource({
    sourceId: 'guanyijia_demo_policy', priorCompilations: [mysql, wrongGithubSnapshot],
  }).introducedConflictIds, []);

  const withoutNegativeLocator = structuredClone(github);
  const githubNegativeRef = github.blocks.find((block) => block.stableCode === 'rule.negative_stock')?.evidenceRefs[0];
  assert.ok(githubNegativeRef);
  delete withoutNegativeLocator.evidenceLocators[githubNegativeRef];
  assert.deepEqual(story.compileSource({
    sourceId: 'guanyijia_demo_policy', priorCompilations: [mysql, withoutNegativeLocator],
  }).introducedConflictIds, ['gyj-conflict-status-nine']);

  const withoutStatusAssertionRef = structuredClone(github);
  const githubStatusAssertion = withoutStatusAssertionRef.assertions.find((assertion) => (
    assertion.assertionId === 'gyj-block:dimension.document_status.nine'
  ));
  assert.ok(githubStatusAssertion);
  githubStatusAssertion.evidenceRefs = [];
  assert.deepEqual(story.compileSource({
    sourceId: 'guanyijia_demo_policy', priorCompilations: [mysql, withoutStatusAssertionRef],
  }).introducedConflictIds, ['gyj-conflict-negative-stock']);
});

test('修改一个制度块只改变制度编译、内容指纹和对应冲突 variant', () => {
  assert.ok(Object.isFrozen(defaultGuanyijiaPolicyDocuments));
  assert.ok(defaultGuanyijiaPolicyDocuments.every((document) => Object.isFrozen(document)));
  const injected = defaultGuanyijiaPolicyDocuments.map((document) => ({ ...document }));
  const changedInventory = injected.find((document) => document.path === '库存管理/负库存与库存时点.md');
  assert.ok(changedInventory);
  changedInventory.content = changedInventory.content.replace('所有租户一律禁止负库存。', '演示制度允许负库存。');
  const changedStory = createGuanyijiaStandardizationStory({ policyDocuments: injected });
  const snapshotBeforeCallerMutation = changedStory.listSources()[3]?.snapshotId;
  changedInventory.content = changedInventory.content.replace('演示制度允许负库存。', '调用方随后又改写，但不得渗入已创建实例。');
  assert.equal(changedStory.listSources()[3]?.snapshotId, snapshotBeforeCallerMutation);

  const baselineStory = createGuanyijiaStandardizationStory();
  const compileReal = (story: ReturnType<typeof createGuanyijiaStandardizationStory>) => {
    const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
    const github = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [mysql] });
    const official = story.compileSource({ sourceId: 'guanyijia_official_docs', priorCompilations: [mysql, github] });
    return [mysql, github, official] as const;
  };
  const baselineReal = compileReal(baselineStory);
  const changedReal = compileReal(changedStory);
  assert.deepEqual(changedReal, baselineReal, '制度输入不得改变三个真实来源编译');
  const baselinePolicy = baselineStory.compileSource({
    sourceId: 'guanyijia_demo_policy', priorCompilations: [...baselineReal],
  });
  const changedPolicy = changedStory.compileSource({
    sourceId: 'guanyijia_demo_policy', priorCompilations: [...changedReal],
  });
  assert.notEqual(changedPolicy.snapshotId, baselinePolicy.snapshotId);
  const changedBlockCodes = changedPolicy.blocks.filter((block) => {
    const baseline = baselinePolicy.blocks.find((candidate) => candidate.stableCode === block.stableCode);
    return JSON.stringify(block.value) !== JSON.stringify(baseline?.value);
  }).map((block) => block.stableCode);
  assert.deepEqual(changedBlockCodes, ['rule.negative_stock']);
  const negativeEvidence = changedPolicy.blocks.find((block) => block.stableCode === 'rule.negative_stock')?.evidenceRefs[0];
  assert.ok(negativeEvidence);
  assert.match(String(changedPolicy.evidenceLocators[negativeEvidence]?.contentSha256), /^[a-f0-9]{64}$/);
  assert.equal(JSON.stringify(changedStory.listConflictDefinitions()).includes('POLICY_ALLOWS_NEGATIVE_STOCK'), true);
  assert.equal(JSON.stringify(baselineStory.listConflictDefinitions()).includes('POLICY_ALLOWS_NEGATIVE_STOCK'), false);
});

test('reviseCompilation只允许修改指定块的label/value并重算断言、章节、SHA、delta和冲突投影', () => {
  const story = createGuanyijiaStandardizationStory();
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const github = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [mysql] });
  const official = story.compileSource({ sourceId: 'guanyijia_official_docs', priorCompilations: [mysql, github] });
  const prior = [mysql, github, official];
  const policy = story.compileSource({ sourceId: 'guanyijia_demo_policy', priorCompilations: prior });
  const beforeReal = structuredClone(prior);
  const negative = policy.blocks.find((block) => block.stableCode === 'rule.negative_stock');
  assert.ok(negative);

  const revised = story.reviseCompilation({
    compilation: policy,
    priorCompilations: prior,
    changes: [{
      blockId: negative.blockId,
      label: '负库存制度规则',
      value: { normalized: 'POLICY_ALLOWS_NEGATIVE_STOCK', text: '演示制度允许负库存。' },
    }],
  });

  assert.deepEqual(prior, beforeReal, '纯修订不能修改三个真实来源编译');
  assert.deepEqual(policy, story.compileSource({ sourceId: 'guanyijia_demo_policy', priorCompilations: prior }),
    '纯修订不能修改原制度编译');
  assert.deepEqual(revised.diff.changedBlockIds, [negative.blockId]);
  const afterNegative = revised.compilation.blocks.find((block) => block.blockId === negative.blockId);
  assert.equal(afterNegative?.label, '负库存制度规则');
  assert.deepEqual(afterNegative?.value, {
    normalized: 'POLICY_ALLOWS_NEGATIVE_STOCK', text: '演示制度允许负库存。',
  });
  assert.deepEqual(afterNegative?.evidenceRefs, negative.evidenceRefs);
  assert.deepEqual(afterNegative?.affectedObjectRefs, negative.affectedObjectRefs);
  assert.equal(afterNegative?.section, negative.section);
  assert.equal(afterNegative?.stableCode, negative.stableCode);
  const unaffectedBlockIds = policy.blocks.filter((block) => block.blockId !== negative.blockId).map((block) => block.blockId);
  assert.deepEqual(
    revised.compilation.blocks.filter((block) => unaffectedBlockIds.includes(block.blockId)),
    policy.blocks.filter((block) => unaffectedBlockIds.includes(block.blockId)),
  );
  assert.notEqual(revised.compilation.markdownSha256, policy.markdownSha256);
  assert.notEqual(revised.compilation.sections.METRIC, policy.sections.METRIC);
  for (const { key } of standardSectionOrder.filter(({ key }) => key !== 'METRIC')) {
    assert.equal(revised.compilation.sections[key], policy.sections[key]);
  }
  assert.equal(revised.compilation.assertions.find((item) => item.assertionId === negative.blockId)?.statement,
    '负库存制度规则：演示制度允许负库存。');
  assert.deepEqual(revised.compilation.delta.changedBlockIds.filter((id) => id === negative.blockId), [negative.blockId]);
  assert.deepEqual(revised.compilation.introducedConflictIds, [
    'gyj-conflict-negative-stock', 'gyj-conflict-status-nine',
  ]);
  assert.deepEqual(revised.diff.affectedConflicts.map((conflict) => ({
    id: conflict.conflictId, before: conflict.beforeVariant, after: conflict.afterVariant,
  })), [{
    id: 'gyj-conflict-negative-stock', before: 'ALWAYS_FORBIDDEN', after: 'POLICY_ALLOWS_NEGATIVE_STOCK',
  }]);
  assert.deepEqual(revised.diff.affectedObjectRefs, negative.affectedObjectRefs);

  assert.throws(() => story.reviseCompilation({
    compilation: policy, priorCompilations: prior,
    changes: [{ blockId: negative.blockId, label: negative.label, value: negative.value }],
  }), /修改没有产生任何变化/);

  const forgedAuthority = structuredClone(policy);
  forgedAuthority.authority = 'PRIMARY';
  assert.throws(() => story.reviseCompilation({
    compilation: forgedAuthority, priorCompilations: prior,
    changes: [{ blockId: negative.blockId, label: '伪造来源身份' }],
  }), /来源身份或不可修改字段与当前故事不一致/);

  const forgedAffectedRefs = structuredClone(policy);
  forgedAffectedRefs.blocks.find((block) => block.blockId === negative.blockId)!.affectedObjectRefs = [];
  assert.throws(() => story.reviseCompilation({
    compilation: forgedAffectedRefs, priorCompilations: prior,
    changes: [{ blockId: negative.blockId, label: '伪造影响对象' }],
  }), /来源身份或不可修改字段与当前故事不一致/);

  const forgedPrior = structuredClone(mysql);
  forgedPrior.sourceClass = 'DEMO_POLICY';
  assert.throws(() => story.reviseCompilation({
    compilation: policy, priorCompilations: [forgedPrior, github, official],
    changes: [{ blockId: negative.blockId, label: '伪造prior身份' }],
  }), /先前来源身份或不可修改字段与当前故事不一致/);
});

test('三份人类可读制度文件与默认冻结输入一致', () => {
  for (const document of defaultGuanyijiaPolicyDocuments) {
    const content = readFileSync(new URL(`./demo-policy/${document.path}`, import.meta.url), 'utf8');
    assert.equal(content, document.content);
  }
});

test('五源编译不改变管伊佳黄金 Artifact 身份、hash、对象计数和正式证据源', () => {
  const story = createGuanyijiaStandardizationStory();
  const compilations: SourceDocumentCompilation[] = [];
  for (const source of story.listSources()) {
    compilations.push(story.compileSource({ sourceId: source.sourceId, priorCompilations: [...compilations] }));
  }
  assert.equal(compilations.length, 5);
  const artifact = guanyijiaFrozenModelingArtifact;
  assert.equal(artifact.artifactId, 'artifact-guanyijia-v1-40c8572864bd');
  assert.equal(artifact.markdown.sha256, '5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210');
  assert.equal(sha256HexSync(artifact.markdown.content), artifact.markdown.sha256);
  assert.ok(artifact.semanticPayload);
  assert.equal(artifact.semanticPayload.sha256, 'c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248');
  assert.equal(modelingDocumentSemanticPayloadSha256(artifact.semanticPayload.data), artifact.semanticPayload.sha256);
  const payload = artifact.semanticPayload.data;
  assert.deepEqual([
    payload.entities.length, payload.events.length, payload.fields.length, payload.relations.length,
    payload.dimensions.length, payload.metrics.length, payload.hierarchies.length, payload.ruleCandidates.length,
    payload.aliases.length, payload.timeRules.length, payload.pendingAssets.length, payload.exclusions.length,
  ], [14, 9, 296, 30, 4, 5, 2, 8, 10, 9, 63, 2]);
  const formalEvidence = canonicalModelingJson(payload.evidenceContext);
  assert.doesNotMatch(formalEvidence, /guanyijia_demo_policy|guanyijia_semantica_demo|demo-policy:\/\/|semantica:\/\//);
  assert.deepEqual(payload.evidenceContext?.sources.map((source) => source.connectionId), [
    'guanyijia_mysql', 'guanyijia_github', 'guanyijia_official_docs',
  ]);

  const mutated = structuredClone(payload);
  mutated.pendingAssets.pop();
  assert.notDeepEqual([
    mutated.entities.length, mutated.events.length, mutated.fields.length, mutated.relations.length,
    mutated.dimensions.length, mutated.metrics.length, mutated.hierarchies.length, mutated.ruleCandidates.length,
    mutated.aliases.length, mutated.timeRules.length, mutated.pendingAssets.length, mutated.exclusions.length,
  ], [14, 9, 296, 30, 4, 5, 2, 8, 10, 9, 63, 2], '保护断言必须能检出黄金计数变异');
});
