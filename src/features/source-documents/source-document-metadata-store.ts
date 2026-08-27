import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type { SourceDocumentMetadataStore } from './types.ts';

type MetadataStorage = Pick<Storage, 'getItem' | 'setItem'>;
type IndexedDbStateRecord = { version: number; raw: string };

export const sourceDocumentMetadataStorageKey = 'linguan:source-documents:v1';

const databaseName = 'linguan-source-document-metadata-v1';
const objectStoreName = 'state';
const recordKey = 'source-documents';
const storageQueues = new WeakMap<MetadataStorage, Promise<void>>();

function storageVersion(raw: string | null) {
  return raw === null ? 'storage:empty' : `storage:sha256:${sha256HexSync(raw)}`;
}

function withStorageQueue<T>(storage: MetadataStorage, operation: () => Promise<T>) {
  const previous = storageQueues.get(storage) ?? Promise.resolve();
  const result = previous.catch(() => undefined).then(operation);
  storageQueues.set(storage, result.then(() => undefined, () => undefined));
  return result;
}

export function createStorageSourceDocumentMetadataStore(
  storage: MetadataStorage,
): SourceDocumentMetadataStore {
  return {
    async read() {
      const raw = storage.getItem(sourceDocumentMetadataStorageKey);
      return { version: storageVersion(raw), raw };
    },
    compareAndSet(expectedVersion, nextRaw) {
      return withStorageQueue(storage, async () => {
        const current = storage.getItem(sourceDocumentMetadataStorageKey);
        if (storageVersion(current) !== expectedVersion) return false;
        storage.setItem(sourceDocumentMetadataStorageKey, nextRaw);
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
    request.onerror = () => reject(request.error ?? new Error('无法打开来源文档元数据数据库'));
    request.onblocked = () => reject(new Error('来源文档元数据数据库被阻塞'));
  });
}

function parseRecord(value: unknown): IndexedDbStateRecord | null {
  if (value === undefined) return null;
  if (typeof value !== 'object' || value === null
    || !Number.isSafeInteger((value as Partial<IndexedDbStateRecord>).version)
    || ((value as Partial<IndexedDbStateRecord>).version as number) < 1
    || typeof (value as Partial<IndexedDbStateRecord>).raw !== 'string') {
    throw new Error('来源文档元数据存储记录已损坏');
  }
  return value as IndexedDbStateRecord;
}

export function createBrowserSourceDocumentMetadataStore(options: {
  indexedDB?: IDBFactory | null;
} = {}): SourceDocumentMetadataStore {
  const factory = options.indexedDB === undefined ? globalThis.indexedDB : options.indexedDB;
  let databasePromise: Promise<IDBDatabase> | undefined;
  const database = () => {
    if (!factory) return Promise.reject(new Error('当前浏览器缺少 IndexedDB，无法安全修改来源文档'));
    return (databasePromise ??= openDatabase(factory));
  };
  return {
    async read() {
      const db = await database();
      const value = await new Promise<unknown>((resolve, reject) => {
        const transaction = db.transaction(objectStoreName, 'readonly');
        const request = transaction.objectStore(objectStoreName).get(recordKey);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error ?? new Error('无法读取来源文档元数据'));
      });
      const record = parseRecord(value);
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
            const current = parseRecord(request.result);
            const currentVersion = current?.version ?? 0;
            if (`indexeddb:${currentVersion}` !== expectedVersion) return;
            store.put({ version: currentVersion + 1, raw: nextRaw } satisfies IndexedDbStateRecord, recordKey);
            updated = true;
          } catch (error) {
            failure = error instanceof Error ? error : new Error('来源文档元数据存储记录已损坏');
            transaction.abort();
          }
        };
        request.onerror = () => transaction.abort();
        transaction.oncomplete = () => resolve(updated);
        transaction.onerror = () => reject(failure ?? transaction.error ?? new Error('来源文档元数据CAS失败'));
        transaction.onabort = () => reject(failure ?? transaction.error ?? new Error('来源文档元数据CAS被中止'));
      });
    },
  };
}

export function createDefaultSourceDocumentMetadataStore(input: {
  metadataStorage?: MetadataStorage;
}): SourceDocumentMetadataStore {
  if (input.metadataStorage) return createStorageSourceDocumentMetadataStore(input.metadataStorage);
  if (typeof window !== 'undefined') return createBrowserSourceDocumentMetadataStore();
  throw new Error('Node或测试环境必须显式提供来源文档metadataStorage或metadataStore');
}
