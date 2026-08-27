import assert from 'node:assert/strict';
import test from 'node:test';
import {
  beginDemoRecovery,
  createDemoRecoveryDomainAdapters,
  demoRecoveryDatabaseNames,
  demoRecoveryMarkerKey,
  resumeDemoRecovery,
} from './demo-recovery.ts';
import { createSourceManagementRuntime } from '../source-management/runtime.ts';
import { guanyijiaSourceBoardSelectionStorageKey } from '../source-management/source-board.ts';

class MemoryStorage {
  private readonly values = new Map<string, string>();

  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
  keys() { return [...this.values.keys()]; }
}

type DeleteOutcome = 'SUCCESS' | 'BLOCKED' | 'ERROR';

function indexedDb(outcomes: Partial<Record<string, DeleteOutcome>> = {}) {
  const deleted: string[] = [];
  return {
    deleted,
    factory: {
      deleteDatabase(name: string) {
        const request: Partial<IDBOpenDBRequest> = {};
        queueMicrotask(() => {
          const outcome = outcomes[name] ?? 'SUCCESS';
          if (outcome === 'SUCCESS') {
            deleted.push(name);
            request.onsuccess?.call(request as IDBOpenDBRequest, new Event('success'));
            return;
          }
          if (outcome === 'BLOCKED') {
            request.onblocked?.call(request as IDBOpenDBRequest, new Event('blocked'));
            return;
          }
          request.onerror?.call(request as IDBOpenDBRequest, new Event('error'));
        });
        return request as IDBOpenDBRequest;
      },
    } as Pick<IDBFactory, 'deleteDatabase'>,
  };
}

test('完整恢复只删除标准化状态，保留登录、固定快照和无关项目数据', async () => {
  const local = new MemoryStorage();
  const session = new MemoryStorage();
  local.setItem('linguan:guanyijia-workbench:active:v1:batch-a', '{"runId":"stale"}');
  local.setItem('linguan:standardization-runs:v1', '{"legacy":true}');
  local.setItem('linguan:source-documents:v1', '{"legacy":true}');
  local.setItem('linguan:document-alignment:v1', '{"legacy":true}');
  local.setItem(guanyijiaSourceBoardSelectionStorageKey, '{"DATABASE":"demo-content:guanyijia_mysql"}');
  local.setItem('linguan:guanyijia:source-snapshot:fixed', '{"fixed":true}');
  local.setItem('external-project:keep', 'keep');
  session.setItem('linguan:demo-auth:session:v1', '{"userId":"user_administer"}');
  session.setItem('linguan-review-surface-v1:stale-run', '{"anchor":"stale"}');
  session.setItem('external-session:keep', 'keep');
  const database = indexedDb();
  const calls: string[] = [];

  beginDemoRecovery({ localStorage: local, actorUserId: 'user_administer' });
  const result = await resumeDemoRecovery({
    localStorage: local,
    sessionStorage: session,
    indexedDB: database.factory,
    resetCollaboration: (actorUserId) => { calls.push(`collaboration:${actorUserId}`); },
    resetSourceManagement: async (actorUserId) => { calls.push(`sources:${actorUserId}`); },
  });

  assert.deepEqual(result, { status: 'RECOVERED' });
  assert.deepEqual(database.deleted, [...demoRecoveryDatabaseNames]);
  assert.deepEqual(calls, ['collaboration:user_administer', 'sources:user_administer']);
  assert.equal(local.getItem(demoRecoveryMarkerKey), null);
  assert.equal(local.getItem('linguan:guanyijia-workbench:active:v1:batch-a'), null);
  assert.equal(local.getItem('linguan:standardization-runs:v1'), null);
  assert.equal(local.getItem('linguan:source-documents:v1'), null);
  assert.equal(local.getItem('linguan:document-alignment:v1'), null);
  assert.equal(local.getItem(guanyijiaSourceBoardSelectionStorageKey), null);
  assert.equal(session.getItem('linguan-review-surface-v1:stale-run'), null);
  assert.equal(local.getItem('linguan:guanyijia:source-snapshot:fixed'), '{"fixed":true}');
  assert.equal(local.getItem('external-project:keep'), 'keep');
  assert.equal(session.getItem('linguan:demo-auth:session:v1'), '{"userId":"user_administer"}');
  assert.equal(session.getItem('external-session:keep'), 'keep');
});

test('数据库被阻塞时不把恢复伪装为成功，并保留继续恢复标记', async () => {
  const local = new MemoryStorage();
  const session = new MemoryStorage();
  local.setItem('linguan:guanyijia-workbench:active:v1:batch-a', '{"runId":"stale"}');
  const database = indexedDb({ [demoRecoveryDatabaseNames[0]!]: 'BLOCKED' });
  let collaborationReset = false;
  let sourcesReset = false;

  beginDemoRecovery({ localStorage: local, actorUserId: 'user_administer' });
  const result = await resumeDemoRecovery({
    localStorage: local,
    sessionStorage: session,
    indexedDB: database.factory,
    resetCollaboration: () => { collaborationReset = true; },
    resetSourceManagement: async () => { sourcesReset = true; },
  });

  assert.equal(result.status, 'BLOCKED');
  assert.equal(local.getItem(demoRecoveryMarkerKey) !== null, true);
  assert.equal(local.getItem('linguan:guanyijia-workbench:active:v1:batch-a') !== null, true);
  assert.equal(collaborationReset, false);
  assert.equal(sourcesReset, false);
});

test('默认领域适配器恢复协作和两个来源工作区的确定性演示种子', async () => {
  const local = new MemoryStorage();
  const adapters = createDemoRecoveryDomainAdapters(local);
  const sources = createSourceManagementRuntime({ storage: local });
  const before = await sources.read('erp_data_governance');
  await sources.execute({
    type: 'DISABLE_CONNECTION', workspaceId: before.workspaceId, expectedRevision: before.revision,
    connectionId: before.connections[0]!.connectionId,
    actor: { userId: 'user_administer', role: 'ADMIN' },
  });

  await adapters.resetCollaboration('user_administer');
  await adapters.resetSourceManagement('user_administer');

  const restored = await sources.read('erp_data_governance');
  assert.equal(restored.connections[0]!.state, 'READY');
  assert.equal(restored.defaultBatch.state, 'FROZEN');
});
