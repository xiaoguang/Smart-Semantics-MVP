import type {
  ReviewWindowIndex,
  ReviewWindowQuery,
  ReviewWindowStream,
  ReviewWindowSummary,
} from './index.ts';
import { ReviewWindowError } from './index.ts';

type StoredReviewWindowSummary = ReviewWindowSummary & {
  runId: string;
  epoch: string;
  stream: ReviewWindowStream;
  filter: string;
};

export type IndexedDbReviewWindowIndex = ReviewWindowIndex & {
  /** Re-open the backing store on the same adapter after a typed availability failure. */
  retry(): Promise<void>;
  writeBatch(input: {
    runId: string;
    epoch: string;
    stream: ReviewWindowStream;
    filter: string;
    items: ReviewWindowSummary[];
  }): Promise<void>;
  clearQuery(input: {
    runId: string;
    epoch: string;
    stream: ReviewWindowStream;
    filter: string;
  }): Promise<void>;
};

function requestResult<T>(request: IDBRequest<T>) {
  return new Promise<T>((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('审阅索引IndexedDB请求失败'));
  });
}

function transactionDone(transaction: IDBTransaction) {
  return new Promise<void>((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error('审阅索引事务失败'));
    transaction.onabort = () => reject(transaction.error ?? new Error('审阅索引事务被中止'));
  });
}

function prefixRange(input: Pick<ReviewWindowQuery, 'runId' | 'epoch' | 'stream' | 'filter'>) {
  const prefix = [input.runId, input.epoch, input.stream, input.filter];
  return IDBKeyRange.bound([...prefix, ''], [...prefix, '\uffff']);
}

function queryRange(input: ReviewWindowQuery) {
  const prefix = [input.runId, input.epoch, input.stream, input.filter];
  if (!input.stableKey) return prefixRange(input);
  return input.direction === 'FORWARD'
    ? IDBKeyRange.bound([...prefix, input.stableKey], [...prefix, '\uffff'], true, false)
    : IDBKeyRange.bound([...prefix, ''], [...prefix, input.stableKey], false, true);
}

export function createIndexedDbReviewWindowIndex(options: {
  factory?: IDBFactory;
  databaseName?: string;
} = {}): IndexedDbReviewWindowIndex {
  const factory = options.factory ?? globalThis.indexedDB;
  const databaseName = options.databaseName ?? 'linguan-standardization-review-window-v1';
  const storeName = 'summaries';
  let databasePromise: Promise<IDBDatabase> | null = null;
  const open = () => {
    if (!factory) return Promise.reject(new ReviewWindowError({
      code: 'STORE_UNAVAILABLE', message: '当前浏览器不支持IndexedDB，审阅索引不可用', recoveryAction: 'RETRY',
    }));
    if (!databasePromise) {
      databasePromise = new Promise<IDBDatabase>((resolve, reject) => {
        let request: IDBOpenDBRequest;
        const fail = (cause: unknown) => {
          databasePromise = null;
          reject(new ReviewWindowError({
            code: 'STORE_UNAVAILABLE',
            message: cause instanceof Error ? `无法打开审阅索引：${cause.message}` : '无法打开审阅索引',
            recoveryAction: 'RETRY',
          }));
        };
        try { request = factory.open(databaseName, 1); }
        catch (cause) { fail(cause); return; }
        request.onupgradeneeded = () => {
          const database = request.result;
          if (!database.objectStoreNames.contains(storeName)) {
            const store = database.createObjectStore(storeName, {
              keyPath: ['runId', 'epoch', 'stream', 'filter', 'stableKey'],
            });
            store.createIndex('query', ['runId', 'epoch', 'stream', 'filter', 'stableKey'], { unique: true });
          }
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => fail(request.error ?? new Error('无法打开审阅索引IndexedDB'));
        request.onblocked = () => fail(new Error('审阅索引升级被其他页面阻止'));
      });
    }
    return databasePromise;
  };
  return {
    async retry() {
      // A failed open is cleared by fail(), but reset explicitly so the UI's
      // typed STORE_UNAVAILABLE action is deterministic and never depends on
      // which request phase failed. The adapter identity remains unchanged.
      databasePromise = null;
      await open();
    },
    async query(input) {
      const database = await open();
      const transaction = database.transaction(storeName, 'readonly');
      const index = transaction.objectStore(storeName).index('query');
      const totalPromise = requestResult(index.count(prefixRange(input)));
      const items = await new Promise<ReviewWindowSummary[]>((resolve, reject) => {
        const values: ReviewWindowSummary[] = [];
        const request = index.openCursor(queryRange(input), input.direction === 'FORWARD' ? 'next' : 'prev');
        request.onerror = () => reject(request.error ?? new Error('审阅索引cursor读取失败'));
        request.onsuccess = () => {
          const cursor = request.result;
          if (!cursor || values.length >= input.limit + 1) {
            resolve(values);
            return;
          }
          const record = cursor.value as StoredReviewWindowSummary;
          values.push({
            stableKey: record.stableKey,
            title: record.title,
            summary: record.summary,
            ...(record.contentRef ? { contentRef: record.contentRef } : {}),
            ...(record.contentSha256 ? { contentSha256: record.contentSha256 } : {}),
          });
          cursor.continue();
        };
      });
      const total = await totalPromise;
      return { items: items.slice(0, input.limit), total, hasMore: items.length > input.limit };
    },
    async writeBatch(input) {
      if (input.items.length > 1_000) throw new Error('审阅索引单批最多1000项');
      const database = await open();
      const transaction = database.transaction(storeName, 'readwrite');
      const store = transaction.objectStore(storeName);
      for (const item of input.items) store.put({ ...input, ...item, items: undefined });
      await transactionDone(transaction);
    },
    async clearQuery(input) {
      const database = await open();
      const transaction = database.transaction(storeName, 'readwrite');
      transaction.objectStore(storeName).delete(prefixRange(input));
      await transactionDone(transaction);
    },
  };
}
