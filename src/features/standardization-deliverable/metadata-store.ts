import type { DeliverableMetadataStore } from './types.ts';

type IndexedDbRecord = { version: number; raw: string };
const databaseName = 'linguan-standardization-deliverable-v1';
const objectStoreName = 'state';
const recordKey = 'deliverables';

function openDatabase(factory: IDBFactory) {
  return new Promise<IDBDatabase>((resolve, reject) => {
    const request = factory.open(databaseName, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(objectStoreName)) request.result.createObjectStore(objectStoreName);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('无法打开标准化交付物数据库'));
    request.onblocked = () => reject(new Error('标准化交付物数据库被阻塞'));
  });
}

function parseRecord(value: unknown): IndexedDbRecord | null {
  if (value === undefined) return null;
  if (!value || typeof value !== 'object'
    || !Number.isSafeInteger((value as Partial<IndexedDbRecord>).version)
    || (value as IndexedDbRecord).version < 1
    || typeof (value as Partial<IndexedDbRecord>).raw !== 'string') {
    throw new Error('标准化交付物元数据记录已损坏');
  }
  return value as IndexedDbRecord;
}

export function createBrowserDeliverableMetadataStore(
  factory: IDBFactory | null = globalThis.indexedDB,
): DeliverableMetadataStore {
  let databasePromise: Promise<IDBDatabase> | undefined;
  const database = () => {
    if (!factory) return Promise.reject(new Error('当前浏览器缺少 IndexedDB，无法安全修改标准化交付物'));
    return (databasePromise ??= openDatabase(factory));
  };
  return {
    async read() {
      const db = await database();
      const value = await new Promise<unknown>((resolve, reject) => {
        const request = db.transaction(objectStoreName, 'readonly').objectStore(objectStoreName).get(recordKey);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error ?? new Error('无法读取标准化交付物元数据'));
      });
      const record = parseRecord(value);
      return record ? { version: `indexeddb:${record.version}`, raw: record.raw } : { version: 'indexeddb:0', raw: null };
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
            const version = current?.version ?? 0;
            if (`indexeddb:${version}` !== expectedVersion) return;
            store.put({ version: version + 1, raw: nextRaw } satisfies IndexedDbRecord, recordKey);
            updated = true;
          } catch (cause) {
            failure = cause instanceof Error ? cause : new Error('标准化交付物元数据记录已损坏');
            transaction.abort();
          }
        };
        request.onerror = () => transaction.abort();
        transaction.oncomplete = () => resolve(updated);
        transaction.onerror = () => reject(failure ?? transaction.error ?? new Error('标准化交付物CAS失败'));
        transaction.onabort = () => reject(failure ?? transaction.error ?? new Error('标准化交付物CAS被中止'));
      });
    },
  };
}
