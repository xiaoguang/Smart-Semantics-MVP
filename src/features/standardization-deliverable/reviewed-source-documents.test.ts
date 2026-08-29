import assert from 'node:assert/strict';
import test from 'node:test';
import { projectReviewedSourceDocument } from './reviewed-source-documents.ts';

const sectionNames = [
  '文档说明', '业务目标', '业务对象', '业务活动', '字段与维度',
  '对象关系', '指标口径', '示例问题', '待确认事项',
];

function sourceMarkdown(changedTitle = '库存单据主表') {
  return [
    '# 数据库建模审阅',
    ...sectionNames.flatMap((title, index) => [
      '', `## ${index + 1}. ${title}`, '',
      index === 2 ? `### ${changedTitle}\n\n完整来源正文：库存单据的冻结说明。` : `完整来源正文：${title}。`,
    ]),
    '',
  ].join('\n');
}

function document(markdown: string, title = '库存单据主表') {
  return {
    markdown,
    markdownSha256: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' as const,
    standardSections: sectionNames.map((sectionTitle, index) => ({
      index: index + 1,
      sectionId: `section-${index + 1}`,
      title: sectionTitle,
      markdownAnchor: `chapter-${index + 1}`,
    })),
    claims: [{
      claimId: 'claim:depot-head',
      markdownAnchor: 'claim:depot-head',
      title,
      statement: '完整来源正文：库存单据的冻结说明。',
      section: 3,
    }],
  };
}

test('完整审阅来源文档按固定九章拆分，并保留每章的完整原始正文', () => {
  const originalMarkdown = sourceMarkdown();
  const reviewed = projectReviewedSourceDocument({
    sourceId: 'guanyijia_mysql',
    sourceName: '数据库',
    order: 1,
    contentSnapshotId: 'content:mysql:v6',
    documentId: 'document:mysql:r2',
    documentRevision: 2,
    original: document(originalMarkdown),
    reviewed: document(sourceMarkdown('库存单据表头（jsh_depot_head）'), '库存单据表头（jsh_depot_head）'),
  });

  assert.equal(reviewed.sections.length, 9);
  assert.deepEqual(reviewed.sections.map((section) => section.section), [
    'OVERVIEW', 'GOAL', 'OBJECT', 'ACTIVITY', 'FIELD', 'RELATION', 'METRIC', 'QUESTION', 'UNRESOLVED',
  ]);
  assert.equal(reviewed.sections[2]?.markdown, '### 库存单据表头（jsh_depot_head）\n\n完整来源正文：库存单据的冻结说明。');
  assert.equal(reviewed.markdown, sourceMarkdown('库存单据表头（jsh_depot_head）'));
  assert.deepEqual(reviewed.changes, [{
    changeId: 'change:guanyijia_mysql:claim:depot-head:TITLE',
    sourceId: 'guanyijia_mysql',
    claimId: 'claim:depot-head',
    markdownAnchor: 'claim:depot-head',
    field: 'TITLE',
    before: '库存单据主表',
    after: '库存单据表头（jsh_depot_head）',
    section: 'OBJECT',
    decisionId: 'document:mysql:r2',
  }]);
});

test('任何一个固定章节缺失时拒绝构造完整审阅来源文档，而不是生成占位文字', () => {
  const markdown = sourceMarkdown().replace('## 9. 待确认事项', '## 9. 被替换章节');
  assert.throws(() => projectReviewedSourceDocument({
    sourceId: 'guanyijia_mysql',
    sourceName: '数据库',
    order: 1,
    contentSnapshotId: 'content:mysql:v6',
    documentId: 'document:mysql:r1',
    documentRevision: 1,
    original: document(markdown),
    reviewed: document(markdown),
  }), /完整九章|固定章节/u);
});
