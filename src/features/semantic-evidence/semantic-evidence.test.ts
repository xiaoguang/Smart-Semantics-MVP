import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { compileSemanticEvidence } from './compiler.ts';
import { evidenceRecordChecksum } from './evidence-record.ts';
import { guanyijiaSemanticExample, retailSemanticExample } from './example-fixtures.ts';
import { generateSemanticReviewBook } from './review-book.ts';

test('RDF证据无损保留命名图、语言、datatype与BNode作用域', () => {
  const compiled = compileSemanticEvidence(retailSemanticExample);
  const label = compiled.records.find((record) => record.evidenceId === 'retail-sem-net-sales-label');
  assert.ok(label && label.statement.kind === 'RDF_QUAD');
  assert.match(label.statementId, /^stmt-[a-f0-9]{20}$/);
  assert.deepEqual(label.statement.quad.graph, { kind: 'IRI', value: 'urn:retail:metrics' });
  assert.deepEqual(label.statement.quad.object, {
    kind: 'LITERAL', value: '净销售额', language: 'zh-CN',
  });
  const bnode = compiled.records.find((record) => record.evidenceId === 'retail-sem-refund-condition-node');
  assert.ok(bnode && bnode.statement.kind === 'RDF_QUAD');
  assert.equal(bnode.statement.quad.object.kind, 'BNODE');
  if (bnode.statement.quad.object.kind === 'BNODE') assert.equal(bnode.statement.quad.object.scope, 'retail-semantic-example-v1');
});

test('派生Semantica主张保留SharePoint血缘且不重复计算独立来源', () => {
  const compiled = compileSemanticEvidence(retailSemanticExample);
  const claim = compiled.claims.find((item) => item.subject === 'urn:retail:NetSales' && item.predicate === 'ALIAS' && item.value === '实收销售额');
  assert.ok(claim);
  assert.deepEqual(claim.rootConnectionIds, ['retail_sharepoint']);
  assert.equal(claim.independentSourceCount, 1);
  assert.equal(claim.support, 'DERIVED_ONLY');
});

test('删除净销售额altLabel只移除对应同义词候选', () => {
  const before = compileSemanticEvidence(retailSemanticExample);
  const after = compileSemanticEvidence({
    ...retailSemanticExample,
    records: retailSemanticExample.records.filter((record) => record.evidenceId !== 'retail-sem-net-sales-alias'),
  });
  assert.ok(before.candidates.some((item) => item.kind === 'ALIAS' && item.name === '实收销售额'));
  assert.ok(!after.candidates.some((item) => item.kind === 'ALIAS' && item.name === '实收销售额'));
  const stableKinds = ['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC', 'RULE'];
  assert.deepEqual(
    after.candidates.filter((item) => stableKinds.includes(item.kind)).map((item) => item.objectRef).sort(),
    before.candidates.filter((item) => stableKinds.includes(item.kind)).map((item) => item.objectRef).sort(),
  );
});

test('退款时点冲突逐来源展示主张并阻断相关指标和规则', () => {
  const compiled = compileSemanticEvidence(retailSemanticExample);
  const finding = compiled.findings.find((item) => item.semanticKey === 'urn:retail:NetSales|REFUND_TIMING');
  assert.ok(finding);
  assert.equal(finding.status, 'CONFLICT');
  assert.equal(finding.severity, 'BLOCKER');
  assert.deepEqual(finding.participants.map((item) => item.connectionId).sort(), ['retail_github', 'retail_sharepoint']);
  assert.ok(finding.affectedObjectRefs.includes('METRIC:net_sales'));
  assert.ok(finding.affectedObjectRefs.includes('RULE:refund_timing'));
});

test('缺少GitHub快照时不会展示GitHub主张或伪造完整互证', () => {
  const compiled = compileSemanticEvidence({
    ...retailSemanticExample,
    records: retailSemanticExample.records.filter((record) => record.connectionId !== 'retail_github'),
  });
  assert.ok(compiled.findings.every((finding) => finding.participants.every((item) => item.connectionId !== 'retail_github')));
  const refund = compiled.findings.find((item) => item.semanticKey === 'urn:retail:NetSales|REFUND_TIMING');
  assert.ok(refund);
  assert.equal(refund.status, 'SINGLE_SOURCE');
  assert.equal(refund.severity, 'WEAK');
});

test('未知谓词保留在待解释队列，不被静默丢弃', () => {
  const compiled = compileSemanticEvidence(retailSemanticExample);
  assert.deepEqual(compiled.unknownPredicates.map((item) => item.predicate), ['urn:retail:needsMerchandisingReview']);
  assert.equal(compiled.unknownPredicates[0]?.evidenceRefs[0], 'retail-sem-unknown-predicate');
});

test('管伊佳示例生成往来单位角色、业务事件和结构冲突', () => {
  const compiled = compileSemanticEvidence(guanyijiaSemanticExample);
  assert.ok(compiled.candidates.some((item) => item.objectRef === 'ENTITY:counterparty'));
  assert.ok(compiled.candidates.some((item) => item.objectRef === 'EVENT:purchase_inbound'));
  assert.ok(compiled.candidates.some((item) => item.objectRef === 'EVENT:return_to_stock'));
  const conflict = compiled.findings.find((item) => item.semanticKey === 'urn:guanyijia:DebtAmount|PHYSICAL_FIELD');
  assert.equal(conflict?.severity, 'BLOCKER');
});

test('审阅说明书只导出可读分类内容，不泄漏内部资料编号或原始JSON', () => {
  const compiled = compileSemanticEvidence(retailSemanticExample);
  const book = generateSemanticReviewBook(compiled);
  assert.equal(book.files['modeling-package.json'], undefined);
  assert.equal(book.files['validation.json'], undefined);
  assert.match(book.files['review/README.md'] ?? '', /已保存材料/);
  assert.ok(book.files['review/04-互证结论.md']?.includes('退款确认时点'));
  assert.ok(book.files['review/model/指标.md']?.includes('净销售额'));
  assert.ok(book.files['review/05-业务规则候选.md']?.includes('仅为候选，不可直接执行'));
  assert.doesNotMatch(Object.values(book.files).join('\n'), /retail-sem-|claim-[a-f0-9]+|objectRef|packageId|fingerprint|\{\s*"/u);
  assert.equal(book.zipBytes[0], 0x50);
  assert.equal(book.zipBytes[1], 0x4b);
});

test('损坏校验和、Locator或上游血缘会阻止证据包生成', () => {
  const brokenChecksum = structuredClone(retailSemanticExample);
  brokenChecksum.records[0]!.checksum = 'broken';
  assert.throws(() => compileSemanticEvidence(brokenChecksum), /校验和不一致/);

  const missingUpstream = structuredClone(retailSemanticExample);
  const missingRecord = missingUpstream.records.find((item) => item.evidenceId === 'retail-sem-net-sales-label')!;
  missingRecord.upstreamEvidenceIds = ['missing-evidence'];
  const { checksum: _missingChecksum, ...missingUnsigned } = missingRecord;
  missingRecord.checksum = evidenceRecordChecksum(missingUnsigned);
  assert.throws(() => compileSemanticEvidence(missingUpstream), /上游证据不存在/);

  const brokenLocator = structuredClone(retailSemanticExample);
  const rdfRecord = brokenLocator.records.find((item) => item.evidenceId === 'retail-sem-product-label')!;
  rdfRecord.locator = { kind: 'RDF', endpoint: '', graphUri: '', subject: '', predicate: '', object: '' };
  const { checksum: _locatorChecksum, ...locatorUnsigned } = rdfRecord;
  rdfRecord.checksum = evidenceRecordChecksum(locatorUnsigned);
  assert.throws(() => compileSemanticEvidence(brokenLocator), /RDF定位不完整/);
});

test('来源中心提供资料整理、示例资料、三阶段结果和审阅说明书入口', () => {
  const panel = readFileSync(new URL('./semantic-collection-panel.tsx', import.meta.url), 'utf8');
  for (const text of ['资料整理与建模建议', '加载示例资料', 'semanticCollectionCopy.tabs.evidence', 'semanticCollectionCopy.tabs.claims', 'semanticCollectionCopy.tabs.findings', 'semanticCollectionCopy.tabs.model', 'semanticCollectionCopy.tabs.review', '查看审阅说明书', '导出审阅说明书 ZIP']) {
    assert.match(panel, new RegExp(text));
  }
  assert.match(panel, /compileSemanticEvidence/);
  assert.match(panel, /generateSemanticReviewBook/);
  assert.match(panel, /retailSemanticExample/);
  assert.match(panel, /guanyijiaSemanticExample/);
  const center = readFileSync(new URL('../source-management/source-center.tsx', import.meta.url), 'utf8');
  assert.match(center, /企业术语与本体图/);
  assert.match(center, /临时示例/);
  assert.match(center, /snapshot\?\.defaultBatch\.projectId === 'guanyijia_erp'/);
});
