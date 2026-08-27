import assert from 'node:assert/strict';
import test from 'node:test';

// The workbench must use one pure key guard before any asynchronous Markdown
// completion is allowed to update the visible document state. This export is
// intentionally the contract under test; production wiring is still absent in
// the RED phase.
const workbenchRuntime = await import('./guanyijia-workbench-runtime.ts');
const isLatestDocumentMarkdownRequest = workbenchRuntime.isLatestDocumentMarkdownRequest as (
  currentLoadKey: string,
  completionLoadKey: string,
) => boolean;

test('rejects a stale Markdown completion after documentId or revision changes', () => {
  assert.equal(typeof isLatestDocumentMarkdownRequest, 'function');

  const current = 'document:github:r2';
  assert.equal(isLatestDocumentMarkdownRequest(current, current), true);

  // The old document may finish after the user advanced to another source.
  assert.equal(
    isLatestDocumentMarkdownRequest(current, 'document:mysql:r1'),
    false,
  );
  // A same-document revision change is stale too; documentId alone is not
  // sufficient to authorize the completion.
  assert.equal(
    isLatestDocumentMarkdownRequest('document:mysql:r2', 'document:mysql:r1'),
    false,
  );
});
