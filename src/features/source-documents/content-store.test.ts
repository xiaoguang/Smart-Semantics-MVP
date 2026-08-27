import assert from 'node:assert/strict';
import test from 'node:test';
import { createContentAddressedStore } from './content-store.ts';

test('内容寻址存储去重正文并在读取时校验内容', async () => {
  const values = new Map<string, string>();
  let writes = 0;
  const store = createContentAddressedStore({
    async read(key) { return values.get(key) ?? null; },
    async writeIfAbsent(key, value) {
      if (!values.has(key)) { values.set(key, value); writes += 1; }
    },
  });
  const first = await store.put('同一份证据正文');
  const repeated = await store.put('同一份证据正文');
  assert.equal(first, repeated);
  assert.equal(writes, 1);
  assert.equal(await store.get(first), '同一份证据正文');
  values.set(first, '被篡改的正文');
  await assert.rejects(() => store.get(first), /内容寻址存储校验和不一致/);
});
