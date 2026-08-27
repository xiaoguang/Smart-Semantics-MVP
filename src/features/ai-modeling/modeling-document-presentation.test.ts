import assert from 'node:assert/strict';
import test from 'node:test';
import {
  presentModelingDocumentSourceSummary,
  presentModelingDocumentVersion,
} from './modeling-document-presentation.ts';

test('建模文档交接以人类可读版本和来源数量替代内部编号', () => {
  assert.equal(presentModelingDocumentVersion(2), '第 2 版');
  assert.equal(presentModelingDocumentSourceSummary(['mysql-2026', 'github-fixed']), '已载入 2 份固定来源资料。');
  assert.equal(presentModelingDocumentSourceSummary([]), '由人工上传并完成结构校验。');
});
