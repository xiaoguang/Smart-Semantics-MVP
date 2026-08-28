import assert from 'node:assert/strict';
import test from 'node:test';
import {
  projectReviewSupplementSegments,
  projectReviewSupplementText,
} from './standardized-document-reading.ts';

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
