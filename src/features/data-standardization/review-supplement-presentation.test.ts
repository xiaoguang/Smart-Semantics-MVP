import assert from 'node:assert/strict';
import test from 'node:test';
import { projectStandardizedDocument } from './standardized-document-projection.ts';
import {
  projectReviewSupplementSegments,
  projectReviewSupplementText,
} from './standardized-document-reading.ts';
import * as reading from './standardized-document-reading.ts';

test('连续 GAP 标记在阅读投影中逐项成为中文待补充资料', () => {
  const source = '本章记录尚未形成正式结论的事项。GAP 1：状态 9 的正式业务含义未保存。GAP 2：欠款字段部署 DDL 缺失。';

  assert.deepEqual(projectReviewSupplementSegments(source), [
    { kind: 'PROSE', text: '本章记录尚未形成正式结论的事项。' },
    { kind: 'SUPPLEMENT', ordinal: 1, text: '状态 9 的正式业务含义未保存。' },
    { kind: 'SUPPLEMENT', ordinal: 2, text: '欠款字段部署 DDL 缺失。' },
  ]);
  assert.equal(projectReviewSupplementText(source), [
    '本章记录尚未形成正式结论的事项。',
    '待补充资料 1：状态 9 的正式业务含义未保存。',
    '待补充资料 2：欠款字段部署 DDL 缺失。',
  ].join('\n\n'));
});

test('资料缺口和待确认标记也使用同一中文逐项投影，不可靠切分不丢失原文', () => {
  const marked = '资料缺口：缺少业务负责人确认。待确认：补齐正式制度。';
  assert.equal(projectReviewSupplementText(marked), [
    '待补充资料 1：缺少业务负责人确认。',
    '待补充资料 2：补齐正式制度。',
  ].join('\n\n'));

  const unmarked = '没有结构化标记的原始说明应完整保留。';
  assert.deepEqual(projectReviewSupplementSegments(unmarked), [
    { kind: 'PROSE', text: unmarked },
  ]);
  assert.equal(projectReviewSupplementText(unmarked), unmarked);
});

test('第九章的明确补充资料逐项进入审阅事项且保留精确来源身份', () => {
  const document = projectStandardizedDocument({
    revisionLabel: '第 4 版',
    markdownSource: '## 9. 待确认事项\nGAP 1：原始资料。\n',
    standardSections: [{
      id: 'UNRESOLVED', title: '9. 待确认事项', purpose: '记录资料不足。',
    }],
    claims: [{
      claimId: 'claim:mysql:unresolved', sectionId: 'UNRESOLVED', sectionTitle: '9. 待确认事项',
      sectionPurpose: '记录资料不足。', markdownAnchor: 'claim:mysql:unresolved',
      title: '本来源资料不足',
      statement: 'GAP 1：缺少参数值。GAP 2：缺少业务行。待确认：多单位换算方向；未完成前只能作为 GAP，不得作为正式制度。',
      kind: 'GAP',
    }],
  });
  const project = (reading as typeof reading & {
    projectReviewSupplementMatters?: (input: unknown) => Array<{
      stableId: string; sourceId: string; documentId: string; documentRevision: number;
      ordinal: number; text: string; markdownAnchor?: string;
    }>;
  }).projectReviewSupplementMatters;
  const matters = project?.({
    source: {
      sourceId: 'guanyijia_mysql', sourceName: '数据库',
      documentId: 'document:mysql:r4', documentRevision: 4,
    },
    document,
  }) ?? [];

  assert.deepEqual(matters.map((matter) => ({
    sourceId: matter.sourceId,
    documentId: matter.documentId,
    documentRevision: matter.documentRevision,
    ordinal: matter.ordinal,
    text: matter.text,
    markdownAnchor: matter.markdownAnchor,
  })), [
    {
      sourceId: 'guanyijia_mysql', documentId: 'document:mysql:r4', documentRevision: 4,
      ordinal: 1, text: '缺少参数值。', markdownAnchor: 'claim:mysql:unresolved',
    },
    {
      sourceId: 'guanyijia_mysql', documentId: 'document:mysql:r4', documentRevision: 4,
      ordinal: 2, text: '缺少业务行。', markdownAnchor: 'claim:mysql:unresolved',
    },
    {
      sourceId: 'guanyijia_mysql', documentId: 'document:mysql:r4', documentRevision: 4,
      ordinal: 3, text: '多单位换算方向；未完成前只能作为待补充资料，不得作为正式制度。', markdownAnchor: 'claim:mysql:unresolved',
    },
  ], '阅读版中的逐项中文化必须同时形成可操作、可定位的审阅事项');
  assert.equal(document.markdownSource, '## 9. 待确认事项\nGAP 1：原始资料。\n',
    '审阅事项投影不得改写冻结 Markdown 源文');
});
