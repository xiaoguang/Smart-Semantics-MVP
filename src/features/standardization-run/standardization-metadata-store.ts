import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type { StandardizationMetadataStore } from './types.ts';

type MetadataStorage = Pick<Storage, 'getItem' | 'setItem'>;
type IndexedDbStateRecord = { version: number; raw: string };

export const standardizationMetadataStorageKey = 'linguan:standardization-runs:v1';

const databaseName = 'linguan-standardization-metadata-v1';
const objectStoreName = 'state';
const recordKey = 'standardization-runs';
const storageQueues = new WeakMap<MetadataStorage, Promise<void>>();

function versionForStorage(raw: string | null) {
  return raw === null ? 'storage:empty' : `storage:sha256:${sha256HexSync(raw)}`;
}

function withStorageCompareAndSet<T>(storage: MetadataStorage, operation: () => Promise<T>) {
  const previous = storageQueues.get(storage) ?? Promise.resolve();
  const result = previous.catch(() => undefined).then(operation);
  storageQueues.set(storage, result.then(() => undefined, () => undefined));
  return result;
}

export function createStorageStandardizationMetadataStore(
  storage: MetadataStorage,
): StandardizationMetadataStore {
  return {
    async read() {
      const raw = storage.getItem(standardizationMetadataStorageKey);
      return { version: versionForStorage(raw), raw };
    },
    compareAndSet(expectedVersion, nextRaw) {
      return withStorageCompareAndSet(storage, async () => {
        const currentRaw = storage.getItem(standardizationMetadataStorageKey);
        if (versionForStorage(currentRaw) !== expectedVersion) return false;
        storage.setItem(standardizationMetadataStorageKey, nextRaw);
        return true;
      });
    },
  };
}

function openDatabase(factory: IDBFactory) {
  return new Promise<IDBDatabase>((resolve, reject) => {
    const request = factory.open(databaseName, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(objectStoreName)) {
        request.result.createObjectStore(objectStoreName);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('无法打开标准化运行元数据数据库'));
    request.onblocked = () => reject(new Error('标准化运行元数据数据库被阻塞'));
  });
}

function parseIndexedDbRecord(value: unknown): IndexedDbStateRecord | null {
  if (value === undefined) return null;
  if (typeof value !== 'object' || value === null
    || !Number.isSafeInteger((value as Partial<IndexedDbStateRecord>).version)
    || ((value as Partial<IndexedDbStateRecord>).version as number) < 1
    || typeof (value as Partial<IndexedDbStateRecord>).raw !== 'string') {
    throw new Error('标准化运行元数据存储记录已损坏');
  }
  return value as IndexedDbStateRecord;
}

export function createBrowserStandardizationMetadataStore(options: {
  indexedDB?: IDBFactory | null;
} = {}): StandardizationMetadataStore {
  const factory = options.indexedDB === undefined ? globalThis.indexedDB : options.indexedDB;
  let databasePromise: Promise<IDBDatabase> | undefined;
  const database = () => {
    if (!factory) return Promise.reject(new Error('当前浏览器缺少 IndexedDB，无法安全修改标准化运行'));
    return (databasePromise ??= openDatabase(factory));
  };

  return {
    async read() {
      const db = await database();
      const value = await new Promise<unknown>((resolve, reject) => {
        const transaction = db.transaction(objectStoreName, 'readonly');
        const request = transaction.objectStore(objectStoreName).get(recordKey);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error ?? new Error('无法读取标准化运行元数据'));
      });
      const record = parseIndexedDbRecord(value);
      return record
        ? { version: `indexeddb:${record.version}`, raw: record.raw }
        : { version: 'indexeddb:0', raw: null };
    },

    async compareAndSet(expectedVersion, nextRaw) {
      const db = await database();
      return new Promise<boolean>((resolve, reject) => {
        const transaction = db.transaction(objectStoreName, 'readwrite');
        const store = transaction.objectStore(objectStoreName);
        const request = store.get(recordKey);
        let updated = false;
        let failure: Error | undefined;
        request.onsuccess = () => {
          try {
            const current = parseIndexedDbRecord(request.result);
            const currentVersion = current?.version ?? 0;
            if (`indexeddb:${currentVersion}` !== expectedVersion) return;
            store.put({ version: currentVersion + 1, raw: nextRaw } satisfies IndexedDbStateRecord, recordKey);
            updated = true;
          } catch (error) {
            failure = error instanceof Error ? error : new Error('标准化运行元数据存储记录已损坏');
            transaction.abort();
          }
        };
        request.onerror = () => transaction.abort();
        transaction.oncomplete = () => resolve(updated);
        transaction.onerror = () => reject(failure ?? transaction.error ?? new Error('标准化运行元数据CAS失败'));
        transaction.onabort = () => reject(failure ?? transaction.error ?? new Error('标准化运行元数据CAS被中止'));
      });
    },
  };
}

export function createDefaultStandardizationMetadataStore(input: {
  metadataStorage?: MetadataStorage;
}): StandardizationMetadataStore {
  if (typeof window !== 'undefined') return createBrowserStandardizationMetadataStore();
  if (!input.metadataStorage) throw new Error('Node或测试环境必须显式提供metadataStorage');
  return createStorageStandardizationMetadataStore(input.metadataStorage);
}
