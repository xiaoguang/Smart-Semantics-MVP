import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { modelBrowserPageSize, paginateBrowserItems } from '../model-browser/browser-pagination.ts';
import ts from 'typescript';
import { seedCollaborationState } from './fixtures.ts';
import { modelBrowserRefKey, projectCatalogBrowser } from './catalog-browser-adapter.ts';

test('正式集团零售 V1 投影完整模型对象并保留稳定 Catalog 引用', () => {
  const catalog = seedCollaborationState().shared.catalogs.group_retail_ops[0]!;
  const view = projectCatalogBrowser(catalog);
  assert.deepEqual(view.counts, {
    entities: 7, events: 5, fields: 84, relations: 15, dimensions: 11,
    metrics: 12, hierarchies: 2, rules: 7, aliases: 191, timeRules: 3,
  });
  assert.equal(view.objects.filter((item) => item.ref.kind === 'FIELD').length, 84);
  assert.ok(view.objects.every((item) => item.ref.scope !== 'CATALOG' || item.ref.catalogId === catalog.catalogId));
  assert.ok(view.objects.filter((item) => item.ref.kind === 'FIELD').every((item) => item.ref.ownerId));
  assert.equal(new Set(view.objects.map((item) => modelBrowserRefKey(item.ref))).size, view.objects.length);
  assert.deepEqual(view.sourcePackages.map((item) => item.displayName), [
    '集团多存储证据',
  ]);
  assert.equal(view.sourcePackages.filter((item) => item.active).at(0)?.packageId, 'retail_group_storage');
});

test('正式 Catalog 的每个可见对象持久满足证据或明确待确认 XOR，且支持边语义相关', () => {
  const catalog = seedCollaborationState().shared.catalogs.group_retail_ops[0]!;
  const view = projectCatalogBrowser(catalog);
  const sidecar = catalog.data.semanticSidecar;
  assert.ok(sidecar?.schemaVersion === 2);
  assert.equal(sidecar.objectEvidence.length, view.objects.length);
  const availableEvidence = new Set(sidecar.evidence.map((item) => String(item.evidence_id ?? item.evidenceId)));
  for (const object of view.objects) {
    const hasEvidence = object.evidenceIds.length > 0;
    assert.notEqual(hasEvidence, object.evidenceStatus === 'NEEDS_CONFIRMATION', `${object.ref.kind}:${object.code} 必须满足 XOR`);
    assert.ok(object.evidenceIds.every((id) => availableEvidence.has(id)), `${object.ref.kind}:${object.code} 不得引用批次外证据`);
  }

  const operatingUnitAlias = view.objects.find((item) => item.ref.kind === 'ALIAS' && item.name === '经营组织业务称呼');
  assert.equal(operatingUnitAlias?.evidenceStatus, 'NEEDS_CONFIRMATION');
  assert.deepEqual(operatingUnitAlias?.evidenceIds, [], '经营组织同义词不得由净销售/库存证据伪验证');

  const botMetric = view.objects.find((item) => item.ref.kind === 'METRIC' && item.code === 'metric_search_conversion_rate');
  assert.equal(botMetric?.evidenceStatus, 'NEEDS_CONFIRMATION', '流量过滤Claim不能验证转化率公式');
  assert.deepEqual(botMetric?.evidenceIds, []);
  const botRule = view.objects.find((item) => item.ref.kind === 'RULE' && item.code === 'rule_search_bot_exclusion');
  assert.equal(botRule?.evidenceStatus, 'VERIFIED');
  const botClaims = sidecar.claims.filter((claim) => botRule?.evidenceSupportClaimIds.includes(claim.claimId));
  assert.ok(botClaims.length > 0);
  assert.ok(botClaims.every((claim) => claim.topic === 'BOT_FILTER'));
});

test('正式 V1 冻结批次只展示真实八个来源并包含可追溯互证主张', () => {
  const catalog = seedCollaborationState().shared.catalogs.group_retail_ops[0]!;
  const view = projectCatalogBrowser(catalog);
  assert.equal(view.sourceBatch?.revision, 1);
  assert.equal(view.sourceSnapshots.length, 8);
  assert.equal(new Set(view.sourceSnapshots.map((item) => item.connectionId)).size, 8);
  assert.ok(view.sourceSnapshots.every((item) => item.versionRef && Object.keys(item.objectCounts).length > 0));
  assert.ok(view.reconciliationFindings.length > 0);
  assert.ok(view.reconciliationFindings.every((item) => item.claims.length > 0));
  assert.ok(view.reconciliationFindings.some((item) => item.claims.length >= 2));
  assert.equal(view.reconciliationFindings.filter((item) => item.resolution).length, 0);
});

test('关系端点、指标归属与维度目标使用真实对象链接', () => {
  const catalog = seedCollaborationState().shared.catalogs.group_retail_ops[0]!;
  const view = projectCatalogBrowser(catalog);
  const relation = view.objects.find((item) => item.ref.kind === 'RELATION');
  const metric = view.objects.find((item) => item.ref.kind === 'METRIC');
  const dimension = view.objects.find((item) => item.ref.kind === 'DIMENSION');
  assert.ok(relation?.details.flatMap((item) => item.links ?? []).length >= 2);
  assert.ok(metric?.details.flatMap((item) => item.links ?? []).some((item) => item.ref.kind === 'EVENT'));
  assert.ok(dimension?.details.flatMap((item) => item.links ?? []).length >= 1);
});

test('旧 Catalog 缺少 sidecar 时诚实提示，不从当前 Fixture 猜来源', () => {
  const catalog = structuredClone(seedCollaborationState().shared.catalogs.group_retail_ops[0]!);
  delete catalog.data.semanticSidecar;
  const view = projectCatalogBrowser(catalog);
  assert.equal(view.counts.entities, 7);
  assert.equal(view.counts.dimensions, 0);
  assert.match(view.coverageNotices.join(' '), /历史版本未保留来源明细/);
});

test('正式模型页面只保留一份对象浏览器且新增 TSX 可以解析', () => {
  const catalogView = readFileSync(new URL('./catalog-view.tsx', import.meta.url), 'utf8');
  assert.doesNotMatch(catalogView, /CatalogCountGrid|collaboration-catalog-counts/);
  assert.match(catalogView, /<ModelBrowser/);
  for (const relative of ['../model-browser/model-browser.tsx', './catalog-view.tsx']) {
    const fileName = new URL(relative, import.meta.url);
    const output = ts.transpileModule(readFileSync(fileName, 'utf8'), {
      fileName: fileName.pathname,
      reportDiagnostics: true,
      compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
    });
    assert.deepEqual((output.diagnostics ?? []).filter((item) => item.category === ts.DiagnosticCategory.Error), [], relative);
  }
});

test('正式浏览器对象与来源证据统一每页20条且来源使用列表到详情', () => {
  const items = Array.from({ length: 41 }, (_, index) => index + 1);
  assert.equal(modelBrowserPageSize, 20);
  assert.deepEqual(paginateBrowserItems(items, 2).items, items.slice(20, 40));
  assert.equal(paginateBrowserItems(items, 99).page, 3);
  const browser = readFileSync(new URL('../model-browser/model-browser.tsx', import.meta.url), 'utf8');
  assert.match(browser, /paginateBrowserItems/);
  assert.match(browser, /model-browser-source-list/);
  assert.match(browser, /model-browser-source-detail-view/);
  assert.match(browser, /BackAction/);
  assert.match(browser, /destination="来源列表"/);
  assert.doesNotMatch(browser, /source\.evidenceIds\.map/);
});

test('模型浏览器六类入口在桌面和手机都固定为单行六列', () => {
  const styles = readFileSync(new URL('../model-browser/model-browser.css', import.meta.url), 'utf8');
  const declarations = [...styles.matchAll(/\.model-browser-categories\s*\{(?<body>[\s\S]*?)\}/g)]
    .map((match) => match.groups?.body ?? '');

  assert.ok(declarations.some((body) => /grid-template-columns:\s*repeat\(6,\s*minmax\(0,\s*1fr\)\)/.test(body)));
  assert.ok(declarations.every((body) => !/grid-template-columns:\s*repeat\([23],/.test(body)));
});
