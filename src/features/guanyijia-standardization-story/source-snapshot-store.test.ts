import assert from 'node:assert/strict';
import test from 'node:test';
import { clearSourceSnapshotMemory, loadOrBuildSourceSnapshot, type SnapshotStorage } from './source-snapshot-store.ts';

function storage() {
  const values = new Map<string, string>();
  return {
    values,
    getItem(key: string) { return values.get(key) ?? null; },
    setItem(key: string, value: string) { values.set(key, value); },
  } satisfies SnapshotStorage & { values: Map<string, string> };
}

test('fixed source snapshot is persisted and reused without rebuilding', () => {
  clearSourceSnapshotMemory();
  const local = storage();
  let builds = 0;
  const input = {
    key: 'linguan:test:source-snapshot', storyKey: 'guanyijia-five-source-v1',
    sourceIds: ['mysql', 'github'], sourceSnapshots: ['mysql-r1', 'github-r1'], storage: local,
    build: () => { builds += 1; return [{ sourceId: 'mysql', markdown: '# 数据库' }, { sourceId: 'github', markdown: '# 代码' }]; },
  };
  const first = loadOrBuildSourceSnapshot(input);
  assert.equal(first.loadedFromCache, false);
  assert.equal(builds, 1);
  clearSourceSnapshotMemory();
  const second = loadOrBuildSourceSnapshot(input);
  assert.equal(second.loadedFromCache, true);
  assert.equal(builds, 1);
  assert.deepEqual(second.value, first.value);
  assert.equal(local.values.has(input.key), true);
});

test('pinned source snapshot is returned without invoking the dynamic builder', () => {
  clearSourceSnapshotMemory();
  const local = storage();
  const pinned = [{ sourceId: 'mysql', markdown: '# 固定数据库快照' }];

  const result = loadOrBuildSourceSnapshot({
    key: 'linguan:test:pinned-source-snapshot',
    storyKey: 'guanyijia-five-source-v1',
    sourceIds: ['mysql'],
    sourceSnapshots: ['mysql-r1'],
    storage: local,
    pinned,
    build: () => { throw new Error('不应重新编译固定快照'); },
  });

  assert.deepEqual(result.value, pinned);
  assert.equal(local.values.has('linguan:test:pinned-source-snapshot'), true);
});

test('changed source identity invalidates the local snapshot', () => {
  clearSourceSnapshotMemory();
  const local = storage();
  let builds = 0;
  const common = {
    key: 'linguan:test:source-snapshot', storyKey: 'guanyijia-five-source-v1',
    sourceIds: ['mysql'], storage: local,
  };
  loadOrBuildSourceSnapshot({ ...common, sourceSnapshots: ['mysql-r1'], build: () => { builds += 1; return [{ revision: 1 }]; } });
  clearSourceSnapshotMemory();
  const updated = loadOrBuildSourceSnapshot({ ...common, sourceSnapshots: ['mysql-r2'], build: () => { builds += 1; return [{ revision: 2 }]; } });
  assert.equal(updated.loadedFromCache, false);
  assert.equal(builds, 2);
  assert.deepEqual(updated.value, [{ revision: 2 }]);
});
