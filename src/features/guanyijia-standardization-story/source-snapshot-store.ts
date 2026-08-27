import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';

export type SnapshotStorage = Pick<Storage, 'getItem' | 'setItem'>;

export type SourceSnapshotCacheResult<T> = {
  value: T[];
  loadedFromCache: boolean;
  payloadSha256: string;
};

type SnapshotEnvelope<T> = {
  schemaVersion: 1;
  compilerVersion: 'content-first-v1';
  storyKey: string;
  sourceIds: string[];
  sourceSnapshots: string[];
  payload: T[];
  payloadSha256: string;
};

const memoryCache = new Map<string, { fingerprint: string; value: unknown[]; payloadSha256: string }>();

function clone<T>(value: T): T {
  return structuredClone(value);
}

function browserStorage(): SnapshotStorage | undefined {
  if (typeof window === 'undefined') return undefined;
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}

function metadataFingerprint(input: {
  storyKey: string;
  sourceIds: readonly string[];
  sourceSnapshots: readonly string[];
}) {
  return sha256HexSync(canonicalModelingJson({
    schemaVersion: 1,
    compilerVersion: 'content-first-v1',
    storyKey: input.storyKey,
    sourceIds: input.sourceIds,
    sourceSnapshots: input.sourceSnapshots,
  }));
}

function payloadHash<T>(payload: readonly T[]) {
  return sha256HexSync(canonicalModelingJson(payload));
}

export function loadOrBuildSourceSnapshot<T>(input: {
  key: string;
  storyKey: string;
  sourceIds: readonly string[];
  sourceSnapshots: readonly string[];
  storage?: SnapshotStorage;
  /**
   * A repository-owned, immutable compilation bundle. When supplied it is the
   * authority: browser cache may mirror it, but the dynamic builder must never
   * run during ordinary demo use.
   */
  pinned?: readonly T[];
  build: () => readonly T[];
}): SourceSnapshotCacheResult<T> {
  const fingerprint = metadataFingerprint(input);
  if (input.pinned) {
    const pinned = clone([...input.pinned]);
    const digest = payloadHash(pinned);
    memoryCache.set(input.key, { fingerprint, value: clone(pinned), payloadSha256: digest });
    const storage = input.storage ?? browserStorage();
    try {
      storage?.setItem(input.key, JSON.stringify({
        schemaVersion: 1,
        compilerVersion: 'content-first-v1',
        storyKey: input.storyKey,
        sourceIds: [...input.sourceIds],
        sourceSnapshots: [...input.sourceSnapshots],
        payload: pinned,
        payloadSha256: digest,
      } satisfies SnapshotEnvelope<T>));
    } catch {
      // The repository bundle remains available when browser persistence fails.
    }
    return { value: clone(pinned), loadedFromCache: true, payloadSha256: digest };
  }
  const memory = memoryCache.get(input.key);
  if (memory?.fingerprint === fingerprint) {
    return { value: clone(memory.value) as T[], loadedFromCache: true, payloadSha256: memory.payloadSha256 };
  }

  const storage = input.storage ?? browserStorage();
  const stored = storage?.getItem(input.key);
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as SnapshotEnvelope<T>;
      const validMetadata = parsed.schemaVersion === 1
        && parsed.compilerVersion === 'content-first-v1'
        && parsed.storyKey === input.storyKey
        && canonicalModelingJson(parsed.sourceIds) === canonicalModelingJson(input.sourceIds)
        && canonicalModelingJson(parsed.sourceSnapshots) === canonicalModelingJson(input.sourceSnapshots)
        && Array.isArray(parsed.payload)
        && parsed.payloadSha256 === payloadHash(parsed.payload);
      if (validMetadata) {
        memoryCache.set(input.key, {
          fingerprint,
          value: clone(parsed.payload),
          payloadSha256: parsed.payloadSha256,
        });
        return { value: clone(parsed.payload), loadedFromCache: true, payloadSha256: parsed.payloadSha256 };
      }
    } catch {
      // A damaged local cache is disposable; the fixed source snapshot is rebuilt once.
    }
  }

  const built = clone([...input.build()]);
  const digest = payloadHash(built);
  memoryCache.set(input.key, { fingerprint, value: clone(built), payloadSha256: digest });
  try {
    storage?.setItem(input.key, JSON.stringify({
      schemaVersion: 1,
      compilerVersion: 'content-first-v1',
      storyKey: input.storyKey,
      sourceIds: [...input.sourceIds],
      sourceSnapshots: [...input.sourceSnapshots],
      payload: built,
      payloadSha256: digest,
    } satisfies SnapshotEnvelope<T>));
  } catch {
    // Quota and private-mode failures must not make the deterministic demo unreadable.
  }
  return { value: clone(built), loadedFromCache: false, payloadSha256: digest };
}

export function clearSourceSnapshotMemory() {
  memoryCache.clear();
}
