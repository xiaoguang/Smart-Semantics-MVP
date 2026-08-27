import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type { ContentAddressedStore, ContentReference } from './types.ts';

export type ContentStoreBackend = {
  read(key: string): Promise<string | null>;
  writeIfAbsent(key: string, value: string): Promise<void>;
};

function reference(content: string): ContentReference {
  return `sha256:${sha256HexSync(content)}`;
}

export function createContentAddressedStore(backend: ContentStoreBackend): ContentAddressedStore {
  return {
    async put(content) {
      const ref = reference(content);
      await backend.writeIfAbsent(ref, content);
      return ref;
    },
    async get(ref) {
      const content = await backend.read(ref);
      if (content === null) return null;
      if (reference(content) !== ref) throw new Error('内容寻址存储校验和不一致');
      return content;
    },
  };
}

function requestResult<T>(request: IDBRequest<T>) {
  return new Promise<T>((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('IndexedDB请求失败'));
  });
}

export function createIndexedDbContentStore(options: {
  factory?: IDBFactory;
  databaseName?: string;
  storeName?: string;
} = {}): ContentAddressedStore {
  const factory = options.factory ?? globalThis.indexedDB;
  const databaseName = options.databaseName ?? 'linguan-standardization-evidence-v1';
  const storeName = options.storeName ?? 'content';
  let databasePromise: Promise<IDBDatabase> | null = null;

  const open = () => {
    if (!factory) return Promise.reject(new Error('当前浏览器不支持IndexedDB，标准化资料只能只读显示'));
    if (!databasePromise) {
      databasePromise = new Promise<IDBDatabase>((resolve, reject) => {
        const request = factory.open(databaseName, 1);
        request.onupgradeneeded = () => {
          if (!request.result.objectStoreNames.contains(storeName)) request.result.createObjectStore(storeName);
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error ?? new Error('无法打开标准化内容存储'));
      });
    }
    return databasePromise;
  };

  return createContentAddressedStore({
    async read(key) {
      const database = await open();
      const transaction = database.transaction(storeName, 'readonly');
      const value = await requestResult(transaction.objectStore(storeName).get(key));
      return typeof value === 'string' ? value : null;
    },
    async writeIfAbsent(key, value) {
      const database = await open();
      await new Promise<void>((resolve, reject) => {
        const transaction = database.transaction(storeName, 'readwrite');
        const store = transaction.objectStore(storeName);
        const read = store.get(key);
        read.onsuccess = () => {
          if (read.result === undefined) store.add(value, key);
        };
        read.onerror = () => transaction.abort();
        transaction.oncomplete = () => resolve();
        transaction.onerror = () => reject(transaction.error ?? new Error('无法写入标准化内容存储'));
        transaction.onabort = () => reject(transaction.error ?? new Error('标准化内容存储写入被中止'));
      });
    },
  });
}
