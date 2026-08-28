import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  buildStandardizedDocumentReadingEntries,
  isRedundantSectionEntryTitle,
} from './standardized-document-reading.ts';
import { projectStandardizedDocument } from './standardized-document-projection.ts';

const readerSource = readFileSync(new URL('./standardized-document-reader.tsx', import.meta.url), 'utf8');

test('阅读版只保留连续业务内容，不把清单任务、证据按钮或空章节带入文档', () => {
  const document = projectStandardizedDocument({
    revisionLabel: '第 2 版',
    markdownSource: '# 审阅文档\n',
    claims: [
      {
        claimId: 'account', sectionId: 'objects', sectionTitle: '业务对象',
        sectionPurpose: '说明已确认的业务对象。', markdownAnchor: 'account',
        title: '账户主数据（jsh_account）', statement: '保存账户名称与当前余额。',
        fields: ['name', 'current_amount'], kind: 'OBJECT',
      },
      {
        claimId: 'gap', sectionId: 'open-items', sectionTitle: '待确认事项',
        sectionPurpose: '记录不进入建模候选的资料缺口。', markdownAnchor: 'status-nine',
        title: '状态 9 的含义尚未确认', statement: '当前资料不能确认它的正式业务含义。',
        kind: 'GAP', nextStep: '等待业务负责人确认。',
      },
    ],
  });

  const entries = buildStandardizedDocumentReadingEntries(document);

  assert.deepEqual(entries.map((entry) => entry.sectionTitle), ['业务对象', '待确认事项']);
  assert.equal(entries[0]!.items[0]!.fields?.join(','), 'name,current_amount');
  assert.equal(entries[1]!.items[0]!.nextStep, '等待业务负责人确认。');
  assert.equal(JSON.stringify(entries).includes('查看来源依据'), false);
  assert.equal(JSON.stringify(entries).includes('待核对任务'), false);
});

test('标准化文档用一条精简工具栏承载版本、视图和复制，不重复渲染文档标题', () => {
  assert.doesNotMatch(readerSource, /<strong>标准化文档<\/strong>/u);
  assert.doesNotMatch(readerSource, /<strong>当前 Markdown 源文<\/strong>/u);
  assert.match(readerSource, /document\.revisionLabel\} · 只读/u);
  assert.match(readerSource, />阅读版<\/Button>/u);
  assert.match(readerSource, />Markdown 源文<\/Button>/u);
  assert.match(readerSource, />复制<\/Button>/u);
});

test('没有重点字段的阅读表不渲染空白第三列或破折号占位', () => {
  assert.match(readerSource, /const hasFields = summarizedObjects\.some\(\(entry\) => entry\.fields\?\.length\)/u);
  assert.match(readerSource, /\{hasFields && <th>重点字段／术语<\/th>\}/u);
  assert.match(readerSource, /\{hasFields && <td>\{entry\.fields\?\.join\('、'\)\}<\/td>\}/u);
  assert.doesNotMatch(readerSource, /fields\?\.length \? entry\.fields\.join\('、'\) : '—'/u);
});

test('阅读版把九章导航放在正文上方，并复用保存的 MySQL 结构与 DDL 展开组件', () => {
  assert.match(readerSource, /import \{ MysqlSchemaEvidenceView \} from '\.\/mysql-schema-evidence-view\.tsx';/u);
  assert.match(readerSource, /<details className="guanyijia-object-details">/u);
  assert.match(readerSource, /<MysqlSchemaEvidenceView evidence=\{entry\.schemaEvidence\} \/>/u);
});

test('阅读版复用统一依据展开器，为代码、文档和术语结论提供各自的可读展开入口', () => {
  assert.match(readerSource, /ReviewEvidenceViewDetails/u);
  assert.match(readerSource, /reviewEvidenceViewSummary/u);
  assert.match(readerSource, /function nonSchemaDetailViews/u);
  assert.match(readerSource, /className="guanyijia-standardized-document-detail"/u);
});

test('阅读版不在章节标题下重复渲染同名的说明性结论标题', () => {
  assert.equal(isRedundantSectionEntryTitle('1. 文档说明', '文档说明'), true);
  assert.equal(isRedundantSectionEntryTitle('业务对象', '业务对象'), true);
  assert.equal(isRedundantSectionEntryTitle('业务对象', '账户主数据（jsh_account）'), false);
});

test('阅读版在结论之前显示一次保存的章节阅读说明', () => {
  const document = projectStandardizedDocument({
    revisionLabel: '第 1 版',
    markdownSource: '# 标准化文档\n',
    standardSections: [{
      id: 'OBJECT',
      title: '业务对象',
      purpose: '说明已保存的业务对象。',
      narrative: '本章先交代对象范围和建模边界；具体结论随后逐条列出。',
    }],
    claims: [{
      claimId: 'object-1', sectionId: 'OBJECT', sectionTitle: '业务对象',
      sectionPurpose: '说明已保存的业务对象。', markdownAnchor: 'object-1',
      title: '账户主数据', statement: '保存账户名称与当前余额。', kind: 'OBJECT',
    }],
  });

  const [section] = buildStandardizedDocumentReadingEntries(document);
  assert.equal(section?.sectionNarrative, '本章先交代对象范围和建模边界；具体结论随后逐条列出。');
  assert.match(readerSource, /section\.sectionNarrative/u);
});

test('阅读版把连续的 GAP 说明逐项投影为待补充资料，而不改写 Markdown 源文', () => {
  const document = projectStandardizedDocument({
    revisionLabel: '第 1 版',
    markdownSource: '## 待确认事项\nGAP 1：状态 9 的正式业务含义未保存。GAP 2：欠款字段部署 DDL 缺失。\n',
    standardSections: [{
      id: 'UNRESOLVED',
      title: '待确认事项',
      purpose: '记录待补充资料。',
      narrative: 'GAP 1：状态 9 的正式业务含义未保存。GAP 2：欠款字段部署 DDL 缺失。',
    }],
    claims: [{
      claimId: 'gap-1', sectionId: 'UNRESOLVED', sectionTitle: '待确认事项',
      sectionPurpose: '记录待补充资料。', markdownAnchor: 'gap-1',
      title: '状态 9', statement: '仍需补充资料。', kind: 'GAP',
    }],
  });

  const [section] = buildStandardizedDocumentReadingEntries(document);

  assert.doesNotMatch(section?.sectionNarrative ?? '', /\bGAP\b/u,
    '阅读版不得把冻结 Markdown 中的英文 GAP 直接暴露给用户');
  assert.match(section?.sectionNarrative ?? '', /待补充资料 1：状态 9 的正式业务含义未保存。/u);
  assert.match(section?.sectionNarrative ?? '', /待补充资料 2：欠款字段部署 DDL 缺失。/u);
  assert.match(document.markdownSource, /GAP 1：状态 9 的正式业务含义未保存。GAP 2：欠款字段部署 DDL 缺失。/u,
    '转换只能作用于阅读投影，原始 Markdown 必须逐字保留');
});
