import assert from 'node:assert/strict';
import test from 'node:test';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';
import {
  buildHumanReadableEvidence,
  buildSourceDocumentTraceLinks,
  displaySourceName,
  sourceClassLabel,
  sourceOriginLabel,
} from './human-readable-evidence.ts';

test('五源使用短的人类可读名称，不改变内部 sourceId', () => {
  assert.equal(displaySourceName('guanyijia_mysql'), '数据库');
  assert.equal(displaySourceName('guanyijia_github'), 'GitHub代码仓库');
  assert.equal(displaySourceName('guanyijia_official_docs'), '业务说明');
  assert.equal(displaySourceName('guanyijia_demo_policy'), 'ERP管理制度');
  assert.equal(displaySourceName('guanyijia_semantica_demo'), '企业术语图');
  assert.equal(sourceClassLabel('REAL'), '已保存来源');
  assert.equal(sourceClassLabel('DEMO_POLICY'), '演示编写 · 非外部原文');
  assert.equal(sourceClassLabel('DERIVED'), '派生术语图 · 不增加独立依据');
  assert.equal(sourceOriginLabel('guanyijia_official_docs'), '演示编写 · 非外部原文');
  assert.equal(sourceOriginLabel('guanyijia_demo_policy'), '演示草案 · 非现行制度');
  assert.equal(sourceOriginLabel('guanyijia_semantica_demo'), '派生术语图 · 不增加独立依据');
});

test('每个识别项都能定位到同一份九段Markdown和来源依据', () => {
  const story = createGuanyijiaStandardizationStory();
  const compilation = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const links = buildSourceDocumentTraceLinks(compilation);
  assert.equal(links.length, compilation.blocks.length);
  assert.equal(new Set(links.map((link) => link.blockId)).size, links.length);
  assert.ok(links.every((link) => link.assertionId && link.markdownAnchor.startsWith('markdown-')));
  assert.ok(links.every((link) => link.evidenceRefs.length > 0));
  const evidence = buildHumanReadableEvidence(compilation, compilation.blocks[0]!.blockId);
  assert.ok(evidence);
  assert.ok(evidence.excerpt.length > 10);
  assert.ok(evidence.locationValue.length > 0);
  assert.equal(evidence.technicalDetails.sha256, undefined);
  assert.equal(typeof evidence.hiddenTechnicalDetails.sha256, 'string');
});

test('依据追踪可从已验证的分页识别项建立锚点，而不要求完整文档正文进入React', () => {
  const story = createGuanyijiaStandardizationStory();
  const compilation = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const visiblePage = { blocks: [compilation.blocks[0]!] };

  const links = buildSourceDocumentTraceLinks(visiblePage);

  assert.deepEqual(links, [{
    blockId: compilation.blocks[0]!.blockId,
    assertionId: compilation.blocks[0]!.blockId,
    section: compilation.blocks[0]!.section,
    markdownAnchor: `markdown-${compilation.blocks[0]!.section.toLowerCase()}-${compilation.blocks[0]!.blockId.replaceAll(/[^a-zA-Z0-9_-]/g, '-')}`,
    evidenceRefs: compilation.blocks[0]!.evidenceRefs,
  }]);
});

test('未保存逐行原文的正式识别项说明材料边界，不重建伪 SQL 或伪代码', () => {
  const story = createGuanyijiaStandardizationStory();
  const mysql = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const github = story.compileSource({ sourceId: 'guanyijia_github', priorCompilations: [mysql] });
  const policy = story.compileSource({ sourceId: 'guanyijia_demo_policy', priorCompilations: [mysql, github] });
  const semantica = story.compileSource({ sourceId: 'guanyijia_semantica_demo', priorCompilations: [mysql, github, policy] });

  const mysqlEvidence = buildHumanReadableEvidence(mysql, mysql.blocks.find((block) => (
    block.stableCode === 'field.jsh_depot_head.oper_time'
  ))!.blockId);
  assert.match(mysqlEvidence!.excerpt, /保存了字段和结构信息/);
  assert.match(mysqlEvidence!.excerpt, /不能从这里核对完整建表语句/);
  assert.doesNotMatch(mysqlEvidence!.excerpt, /CREATE TABLE/);
  assert.equal(mysqlEvidence!.sourceType, 'DDL');

  const githubEvidence = buildHumanReadableEvidence(github, github.blocks.find((block) => (
    block.stableCode === 'rule.negative_stock'
  ))!.blockId);
  assert.match(githubEvidence!.excerpt, /已保存.*代码记录/);
  assert.match(githubEvidence!.excerpt, /未展示的代码不据此作判断/);
  assert.doesNotMatch(githubEvidence!.excerpt, /SELECT\s*\/\*/);
  assert.equal(githubEvidence!.sourceType, 'CODE');

  const policyEvidence = buildHumanReadableEvidence(policy, policy.blocks.find((block) => (
    block.stableCode === 'rule.negative_stock'
  ))!.blockId);
  assert.match(policyEvidence!.excerpt, /禁止负库存/);
  assert.equal(policyEvidence!.sourceType, 'DOCUMENT');

  const semanticaEvidence = buildHumanReadableEvidence(semantica, semantica.blocks[0]!.blockId);
  assert.match(semanticaEvidence!.excerpt, /→/);
  assert.equal(semanticaEvidence!.sourceType, 'TRIPLE');
});
