import { createCollaborationRuntime } from '../collaboration/runtime.ts';
import { createSourceManagementRuntime } from '../source-management/runtime.ts';
import { guanyijiaSourceBoardSelectionStorageKey } from '../source-management/source-board.ts';

type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
type KeyEnumerableStorage = StorageLike & {
  length?: number;
  key?(index: number): string | null;
  keys?(): string[];
};

export const demoRecoveryMarkerKey = 'linguan:demo-recovery:v1';
export const demoRecoveryDatabaseNames = [
  'linguan-standardization-metadata-v1',
  'linguan-source-document-metadata-v1',
  'linguan-standardization-evidence-v1',
  'linguan-standardization-review-window-v1',
  'linguan-standardization-deliverable-v1',
] as const;

const activeRunPointerKey = 'linguan:guanyijia-workbench:active:v1';
const standardizationLegacyKeys = [
  'linguan:standardization-runs:v1',
  'linguan:source-documents:v1',
  'linguan:document-alignment:v1',
] as const;
const reviewSurfacePrefix = 'linguan-review-surface-v1:';

type RecoveryMarker = {
  schemaVersion: 1;
  actorUserId: string;
};

export type DemoRecoveryResult =
  | { status: 'IDLE' }
  | { status: 'RECOVERED' }
  | { status: 'BLOCKED'; message: string }
  | { status: 'FAILED'; message: string };

export type DemoRecoveryInput = {
  localStorage: StorageLike;
  sessionStorage: StorageLike;
  indexedDB?: Pick<IDBFactory, 'deleteDatabase'>;
  resetCollaboration(actorUserId: string): void | Promise<void>;
  resetSourceManagement(actorUserId: string): void | Promise<void>;
};

/** The only cross-domain reset used by the recovery bootstrap. */
export function createDemoRecoveryDomainAdapters(storage: StorageLike) {
  const collaboration = createCollaborationRuntime(storage);
  const sources = createSourceManagementRuntime({ storage });
  return {
    resetCollaboration(actorUserId: string) {
      collaboration.resetDemo(actorUserId);
    },
    async resetSourceManagement(actorUserId: string) {
      for (const workspace of collaboration.listWorkspacesFor(actorUserId)) {
        if (collaboration.getRole(actorUserId, workspace.workspaceId) !== 'ADMIN') continue;
        const current = await sources.read(workspace.workspaceId);
        await sources.execute({
          type: 'RESET_DEMO',
          workspaceId: workspace.workspaceId,
          expectedRevision: current.revision,
          actor: { userId: actorUserId, role: 'ADMIN' },
        });
      }
    },
  };
}

class RecoveryBlockedError extends Error {}

function storageKeys(storage: KeyEnumerableStorage) {
  if (typeof storage.keys === 'function') return storage.keys();
  if (typeof storage.length !== 'number' || typeof storage.key !== 'function') return [];
  const keys: string[] = [];
  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index);
    if (key !== null) keys.push(key);
  }
  return keys;
}

function parseMarker(storage: StorageLike): RecoveryMarker | null {
  const raw = storage.getItem(demoRecoveryMarkerKey);
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<RecoveryMarker>;
    if (value.schemaVersion !== 1 || typeof value.actorUserId !== 'string' || !value.actorUserId.trim()) {
      throw new Error('恢复标记无效');
    }
    return { schemaVersion: 1, actorUserId: value.actorUserId };
  } catch {
    throw new Error('恢复标记已损坏，请重新发起恢复演示');
  }
}

function deleteDatabase(factory: Pick<IDBFactory, 'deleteDatabase'>, name: string) {
  return new Promise<void>((resolve, reject) => {
    let request: IDBOpenDBRequest;
    try { request = factory.deleteDatabase(name); }
    catch (cause) {
      reject(cause instanceof Error ? cause : new Error(`无法清理 ${name}`));
      return;
    }
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error ?? new Error(`无法清理 ${name}`));
    request.onblocked = () => reject(new RecoveryBlockedError(`资料库 ${name} 正被其他页面使用`));
  });
}

function clearStandardizationKeys(localStorage: StorageLike, sessionStorage: StorageLike) {
  const local = localStorage as KeyEnumerableStorage;
  for (const key of storageKeys(local)) {
    if (standardizationLegacyKeys.includes(key as typeof standardizationLegacyKeys[number])
      || key === guanyijiaSourceBoardSelectionStorageKey
      || key === activeRunPointerKey || key.startsWith(`${activeRunPointerKey}:`)) {
      localStorage.removeItem(key);
    }
  }
  const session = sessionStorage as KeyEnumerableStorage;
  for (const key of storageKeys(session)) {
    if (key.startsWith(reviewSurfacePrefix)) sessionStorage.removeItem(key);
  }
}

/**
 * Phase one runs in the mounted application. It deliberately only writes a
 * durable marker: the page reload releases every open IndexedDB connection
 * before phase two deletes mutable standardization state.
 */
export function beginDemoRecovery(input: { localStorage: StorageLike; actorUserId: string }) {
  if (!input.actorUserId.trim()) throw new Error('恢复演示需要当前管理员身份');
  input.localStorage.setItem(demoRecoveryMarkerKey, JSON.stringify({
    schemaVersion: 1,
    actorUserId: input.actorUserId,
  } satisfies RecoveryMarker));
}

/**
 * Phase two runs before React providers mount. It is idempotent: a failed
 * deletion keeps the marker and no in-memory workbench can consume the
 * partially reset state. Fixed source snapshot caches and login remain out of
 * this key set by design.
 */
export async function resumeDemoRecovery(input: DemoRecoveryInput): Promise<DemoRecoveryResult> {
  let marker: RecoveryMarker | null;
  try { marker = parseMarker(input.localStorage); }
  catch (cause) {
    return { status: 'FAILED', message: cause instanceof Error ? cause.message : '恢复标记无法读取' };
  }
  if (!marker) return { status: 'IDLE' };
  if (!input.indexedDB) return { status: 'FAILED', message: '当前浏览器无法清理本次演示资料' };

  try {
    for (const databaseName of demoRecoveryDatabaseNames) {
      await deleteDatabase(input.indexedDB, databaseName);
    }
    await input.resetCollaboration(marker.actorUserId);
    await input.resetSourceManagement(marker.actorUserId);
    clearStandardizationKeys(input.localStorage, input.sessionStorage);
    input.localStorage.removeItem(demoRecoveryMarkerKey);
    return { status: 'RECOVERED' };
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '恢复演示失败';
    return cause instanceof RecoveryBlockedError
      ? { status: 'BLOCKED', message }
      : { status: 'FAILED', message };
  }
}
