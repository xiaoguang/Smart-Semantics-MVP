import assert from 'node:assert/strict';
import test from 'node:test';
import { collectBusinessCopyIssues, assertBusinessCopy } from './business-copy-quality.ts';

test('business copy quality rejects leaked technical tokens, malformed text, and unreadable paragraphs', () => {
  const issues = collectBusinessCopyIssues([
    { value: 'sha256:abcdef', role: 'paragraph' },
    { value: '字段&amp;gt;0', role: 'paragraph' },
    { value: '[TRACE:G1] 这是一条结论', role: 'paragraph' },
    { value: 'x'.repeat(141), role: 'paragraph' },
    { value: '相同标题', role: 'heading', pairedValue: '相同标题' },
  ]);

  assert.deepEqual(issues.map((issue) => issue.kind), [
    'TECHNICAL_TOKEN', 'HTML_ENTITY', 'TECHNICAL_TOKEN', 'LONG_PARAGRAPH', 'DUPLICATE_HEADING',
  ]);
  assert.throws(() => assertBusinessCopy([{ value: 'artifactId: demo', role: 'paragraph' }]), /业务文案校验失败/u);
});

test('business copy quality allows compact Chinese review language and database identifiers', () => {
  assert.deepEqual(collectBusinessCopyIssues([
    { value: '账户主数据（jsh_account）', role: 'heading', pairedValue: '保存账户名称、当前余额和租户归属。' },
    { value: '保存账户名称、当前余额和租户归属。', role: 'paragraph' },
    { value: '来源位置：ddl/tables/jsh_account.sql 第 1–11 行', role: 'paragraph' },
  ]), []);
});

test('business copy quality rejects raw audit payloads and repeated human-facing sentences', () => {
  const issues = collectBusinessCopyIssues([
    { value: '本次已保存审阅结果。', role: 'paragraph' },
    { value: '本次已保存审阅结果。', role: 'paragraph' },
    { value: 'Block 与 Assertion 已更新。', role: 'paragraph' },
    { value: 'M4 Receipt 已生成。', role: 'paragraph' },
    { value: '{"sourceId":"internal-source","checkCode":"abc"}', role: 'paragraph' },
  ]);

  assert.deepEqual(issues.map((issue) => issue.kind), [
    'DUPLICATE_COPY',
    'TECHNICAL_TOKEN',
    'TECHNICAL_TOKEN',
    'RAW_JSON',
    'TECHNICAL_TOKEN',
  ]);
});

test('business copy quality rejects a dense multi-sentence paragraph even when it is short enough by character count', () => {
  const issues = collectBusinessCopyIssues([
    { value: '先说明范围。再说明依据。接着说明影响。最后说明下一步。', role: 'paragraph' },
  ]);

  assert.deepEqual(issues.map((issue) => issue.kind), ['LONG_PARAGRAPH']);
});
