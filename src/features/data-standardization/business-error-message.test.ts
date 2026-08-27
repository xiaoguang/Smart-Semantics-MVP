import assert from 'node:assert/strict';
import test from 'node:test';
import { businessErrorMessage } from './business-error-message.ts';

test('定版的旧零变化错误会映射为可执行的业务提示', () => {
  assert.equal(
    businessErrorMessage(new Error('两层语义变化非零，禁止定版或交接'), '操作暂时无法完成，请重试。'),
    '标准化结果需要重新生成后再定版。',
  );
});

test('未决定的来源差异会指向下一步，而不显示底层错误', () => {
  assert.equal(
    businessErrorMessage(new Error('conflict unresolved for current run'), '操作暂时无法完成，请重试。'),
    '还有来源差异未决定，请返回处理差异。',
  );
});

test('内容引用或校验摘要不会泄漏到业务页面', () => {
  assert.equal(
    businessErrorMessage(new Error('artifact markdownSha256 mismatch'), '文档暂时无法读取，请重新读取文档。'),
    '文档暂时无法读取，请重新读取文档。',
  );
});
