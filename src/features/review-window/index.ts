import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type { ContentReference } from '../source-documents/types.ts';

export const REVIEW_WINDOW_MAX_PAGE_SIZE = 50;
export const REVIEW_WINDOW_MAX_ITEMS = 80;
export const REVIEW_WINDOW_OVERSCAN = 6;

export type ReviewWindowStream =
  | 'TIMELINE'
  | 'ISSUES'
  | 'EVIDENCE'
  | 'ASSISTANT_HISTORY'
  | 'DOCUMENT_BLOCKS'
  | 'DELIVERABLE_SECTIONS';

export type ReviewWindowDirection = 'FORWARD' | 'BACKWARD';

export type ReviewWindowSummary = {
  stableKey: string;
  title: string;
  summary: string;
  contentRef?: ContentReference;
  contentSha256?: string;
};

export type ReviewWindowRequest = {
  runId: string;
  epoch: string;
  stream: ReviewWindowStream;
  filter: string;
  direction?: ReviewWindowDirection;
  cursor?: string;
  limit?: number;
};

export type ReviewWindowPage = {
  items: ReviewWindowSummary[];
  nextCursor: string | null;
  previousCursor: string | null;
  total: number;
  epoch: string;
};

export type VerifiedContentRequest = {
  runId: string;
  contentRef: ContentReference;
  expectedSha256: string;
};

export type VerifiedReviewContent = {
  content: string;
  contentRef: ContentReference;
  sha256: string;
  byteLength: number;
  source: 'CACHE' | 'NETWORK';
};

export type ReviewWindowQuery = {
  runId: string;
  epoch: string;
  stream: ReviewWindowStream;
  filter: string;
  direction: ReviewWindowDirection;
  stableKey?: string;
  limit: number;
};

export type ReviewWindowIndex = {
  query(input: ReviewWindowQuery): Promise<{
    items: ReviewWindowSummary[];
    total: number;
    hasMore: boolean;
  }>;
};

export type ReviewContentCache = {
  read(contentRef: ContentReference): Promise<string | null>;
  write(contentRef: ContentReference, content: string): Promise<void>;
  estimate(): Promise<{ usage: number; quota: number }>;
};

export type ReviewWindowTelemetryEvent =
  | {
    kind: 'QUERY'; runId: string; stream: ReviewWindowStream; filterSha256: string;
    itemCount: number; byteLength: number; durationMs: number;
  }
  | {
    kind: 'BODY_READ'; runId: string; contentRef: ContentReference; source: 'CACHE' | 'NETWORK';
    byteLength: number; durationMs: number;
  };

export type StandardizationReviewWindow = {
  read(request: ReviewWindowRequest): Promise<ReviewWindowPage>;
  readContent(request: VerifiedContentRequest): Promise<VerifiedReviewContent>;
};

export function createArrayReviewWindowIndex(items: readonly ReviewWindowSummary[]): ReviewWindowIndex {
  const stableKeys = new Set<string>();
  for (const item of items) {
    if (!item.stableKey.trim() || stableKeys.has(item.stableKey)) {
      throw new Error('review-window数组索引stableKey不能为空或重复');
    }
    stableKeys.add(item.stableKey);
  }
  return {
    async query(input) {
      const cursorIndex = input.stableKey === undefined
        ? undefined
        : items.findIndex(({ stableKey }) => stableKey === input.stableKey);
      if (cursorIndex === -1) throw new ReviewWindowError({
        code: 'CURSOR_EXPIRED', message: '列表锚点已不存在，请重新读取',
      });
      const result: ReviewWindowSummary[] = [];
      let index = input.direction === 'FORWARD'
        ? (cursorIndex === undefined ? 0 : cursorIndex + 1)
        : (cursorIndex === undefined ? items.length - 1 : cursorIndex - 1);
      while (index >= 0 && index < items.length && result.length < input.limit + 1) {
        result.push(items[index]);
        index += input.direction === 'FORWARD' ? 1 : -1;
      }
      return {
        items: result.slice(0, input.limit),
        total: items.length,
        hasMore: result.length > input.limit,
      };
    },
  };
}

export type ReviewWindowErrorCode =
  | 'CURSOR_EXPIRED'
  | 'OFFLINE_NOT_CACHED'
  | 'CHECKSUM_MISMATCH'
  | 'QUOTA_EXCEEDED'
  | 'INDEX_UNAVAILABLE'
  | 'STORE_UNAVAILABLE'
  | 'CONTENT_UNAVAILABLE';

export class ReviewWindowError extends Error {
  readonly code: ReviewWindowErrorCode;
  readonly recoveryAction?: 'RETRY';
  readonly expectedSha256?: string;
  readonly actualSha256?: string;
  readonly storageEstimate?: { usage: number; quota: number };

  constructor(input: {
    code: ReviewWindowErrorCode;
    message: string;
    recoveryAction?: 'RETRY';
    expectedSha256?: string;
    actualSha256?: string;
    storageEstimate?: { usage: number; quota: number };
  }) {
    super(input.message);
    this.name = 'ReviewWindowError';
    this.code = input.code;
    this.recoveryAction = input.recoveryAction;
    this.expectedSha256 = input.expectedSha256;
    this.actualSha256 = input.actualSha256;
    this.storageEstimate = input.storageEstimate;
  }
}

export type ReviewWindowErrorProjection = {
  code: ReviewWindowErrorCode;
  title: string;
  detail: string;
  blocksMutation: boolean;
  technicalDetail?: string;
  action?: { kind: 'RETRY' | 'RELOAD_WINDOW'; label: string };
};

export function projectReviewWindowError(error: ReviewWindowError): ReviewWindowErrorProjection {
  if (error.code === 'INDEX_UNAVAILABLE') return {
    code: error.code,
    title: '兼容阅读模式',
    detail: '当前运行仍可继续审阅；来源正文会在打开时单独校验。',
    blocksMutation: false,
  };
  if (error.code === 'STORE_UNAVAILABLE') return {
    code: error.code,
    title: '浏览器无法保存本次审阅',
    detail: '已打开的内容仍可阅读；恢复浏览器存储权限后再继续修改、处理差异或定版。',
    blocksMutation: true,
  };
  if (error.code === 'CONTENT_UNAVAILABLE') return {
    code: error.code,
    title: '文档正文暂时无法读取',
    detail: error.message,
    blocksMutation: true,
  };
  if (error.code === 'OFFLINE_NOT_CACHED') return {
    code: error.code,
    title: '正文尚未缓存',
    detail: error.message,
    blocksMutation: false,
    action: { kind: 'RETRY', label: '重新读取' },
  };
  if (error.code === 'CURSOR_EXPIRED') return {
    code: error.code,
    title: '列表已更新',
    detail: error.message,
    blocksMutation: false,
    action: { kind: 'RELOAD_WINDOW', label: '重新读取列表' },
  };
  if (error.code === 'CHECKSUM_MISMATCH') return {
    code: error.code,
    title: '正文完整性校验失败',
    detail: '损坏正文未显示；修改、冲突处理和定版已阻止。',
    blocksMutation: true,
    technicalDetail: `expected ${error.expectedSha256 ?? 'unknown'} / actual ${error.actualSha256 ?? 'unknown'}`,
  };
  return {
    code: error.code,
    title: '浏览器存储空间不足',
    detail: '业务 metadata 与 revision 未前移；旧 revision 仍可读取。',
    blocksMutation: true,
    technicalDetail: error.storageEstimate
      ? `usage ${error.storageEstimate.usage} / quota ${error.storageEstimate.quota}`
      : 'storage estimate unavailable',
  };
}

export type ReviewWindowFailures = Partial<Record<ReviewWindowStream, ReviewWindowError>>;

export type ReviewMutationKind =
  | 'PATCH'
  | 'DELIVERABLE_APPROVAL'
  | 'DOCUMENT_PATCH'
  | 'DOCUMENT_REVIEW'
  | 'CONFLICT_RESOLUTION'
  | 'ASSISTANT_PATCH'
  | 'DELIVERABLE_GENERATE'
  | 'DELIVERABLE_AUTHOR_CONFIRM'
  | 'DELIVERABLE_REVIEW_FREEZE'
  | 'DELIVERABLE_HANDOFF'
  | 'DELIVERABLE_PENDING_RESUME';

export function setReviewWindowFailure(
  current: ReviewWindowFailures,
  stream: ReviewWindowStream,
  error: ReviewWindowError,
): ReviewWindowFailures {
  return { ...current, [stream]: error };
}

export function clearReviewWindowFailure(
  current: ReviewWindowFailures,
  stream: ReviewWindowStream,
): ReviewWindowFailures {
  if (!current[stream]) return current;
  const next = { ...current };
  delete next[stream];
  return next;
}

export function blockingReviewWindowFailures(failures: ReviewWindowFailures) {
  return (Object.entries(failures) as Array<[ReviewWindowStream, ReviewWindowError]>)
    .filter(([, error]) => projectReviewWindowError(error).blocksMutation);
}

export function assertReviewMutationAllowed(
  failures: ReviewWindowFailures | ReviewWindowError | undefined,
  mutation: ReviewMutationKind,
) {
  const normalizedFailures = failures instanceof ReviewWindowError
    ? { TIMELINE: failures }
    : failures ?? {};
  if (blockingReviewWindowFailures(normalizedFailures).length) {
    const labels: Record<ReviewMutationKind, string> = {
      PATCH: '结构化修改',
      DELIVERABLE_APPROVAL: '交付审批或定版',
      DOCUMENT_PATCH: '结构化修改',
      DOCUMENT_REVIEW: '来源文档审阅',
      CONFLICT_RESOLUTION: '来源差异决定',
      ASSISTANT_PATCH: '审阅助手修改',
      DELIVERABLE_GENERATE: '交付物生成',
      DELIVERABLE_AUTHOR_CONFIRM: '作者确认',
      DELIVERABLE_REVIEW_FREEZE: '独立审核与定版',
      DELIVERABLE_HANDOFF: '零变化交接',
      DELIVERABLE_PENDING_RESUME: '交付事件恢复',
    };
    throw new Error(`审阅正文完整性尚未恢复，不能执行${labels[mutation]}`);
  }
}

type CursorPayload = {
  schema: 'review-window/v1';
  epoch: string;
  runId: string;
  stream: ReviewWindowStream;
  filterSha256: string;
  direction: ReviewWindowDirection;
  stableKey: string;
};

function base64UrlEncode(value: string) {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/u, '');
}

function base64UrlDecode(value: string) {
  const normalized = value.replaceAll('-', '+').replaceAll('_', '/');
  const padding = '='.repeat((4 - normalized.length % 4) % 4);
  const binary = atob(`${normalized}${padding}`);
  return new TextDecoder().decode(Uint8Array.from(binary, (character) => character.charCodeAt(0)));
}

function cursorToken(secret: string, payload: CursorPayload) {
  const json = JSON.stringify(payload);
  return `${base64UrlEncode(json)}.${sha256HexSync(`${secret}\u0000${json}`)}`;
}

function parseCursor(secret: string, token: string): CursorPayload {
  try {
    const [encoded, signature, extra] = token.split('.');
    if (!encoded || !signature || extra !== undefined) throw new Error('shape');
    const json = base64UrlDecode(encoded);
    if (sha256HexSync(`${secret}\u0000${json}`) !== signature) throw new Error('signature');
    const value = JSON.parse(json) as Partial<CursorPayload>;
    if (value.schema !== 'review-window/v1' || typeof value.epoch !== 'string'
      || typeof value.runId !== 'string' || typeof value.stream !== 'string'
      || typeof value.filterSha256 !== 'string' || typeof value.direction !== 'string'
      || typeof value.stableKey !== 'string') throw new Error('payload');
    return value as CursorPayload;
  } catch {
    throw new ReviewWindowError({ code: 'CURSOR_EXPIRED', message: '列表游标已失效，请重新读取' });
  }
}

function validateRequest(request: ReviewWindowRequest) {
  if (!request.runId.trim() || !request.epoch.trim()) throw new Error('review-window运行身份无效');
  const defaultLimit = request.stream === 'TIMELINE' ? 40 : 20;
  const limit = request.limit ?? defaultLimit;
  if (!Number.isInteger(limit) || limit < 1 || limit > REVIEW_WINDOW_MAX_PAGE_SIZE) {
    throw new Error('review-window每页最多50项');
  }
  return { limit, direction: request.direction ?? 'FORWARD' as ReviewWindowDirection };
}

function byteLength(value: string) {
  return new TextEncoder().encode(value).byteLength;
}

function verifyContent(content: string, expectedSha256: string) {
  const actualSha256 = sha256HexSync(content);
  if (actualSha256 !== expectedSha256) throw new ReviewWindowError({
    code: 'CHECKSUM_MISMATCH',
    message: '正文校验失败，已阻止显示和后续操作',
    expectedSha256,
    actualSha256,
  });
  return actualSha256;
}

export function createStandardizationReviewWindow(input: {
  cursorSecret: string;
  index: ReviewWindowIndex;
  bodies: { read(contentRef: ContentReference): Promise<string | null> };
  cache?: ReviewContentCache;
  isOnline?: () => boolean;
  telemetry?: { record(event: ReviewWindowTelemetryEvent): void };
  now?: () => number;
}): StandardizationReviewWindow {
  if (!input.cursorSecret.trim()) throw new Error('review-window cursor secret不能为空');
  const now = input.now ?? (() => performance.now());
  return {
    async read(request) {
      const { limit, direction } = validateRequest(request);
      const filterSha256 = sha256HexSync(request.filter);
      let stableKey: string | undefined;
      if (request.cursor) {
        const cursor = parseCursor(input.cursorSecret, request.cursor);
        const matches = cursor.epoch === request.epoch && cursor.runId === request.runId
          && cursor.stream === request.stream && cursor.filterSha256 === filterSha256
          && cursor.direction === direction;
        if (!matches) throw new ReviewWindowError({ code: 'CURSOR_EXPIRED', message: '列表条件已变化，请重新读取' });
        stableKey = cursor.stableKey;
      }
      const startedAt = now();
      let result: Awaited<ReturnType<ReviewWindowIndex['query']>>;
      try {
        result = await input.index.query({ ...request, direction, stableKey, limit });
      } catch (cause) {
        if (cause instanceof ReviewWindowError && cause.code === 'CURSOR_EXPIRED') throw cause;
        throw new ReviewWindowError({
          code: 'INDEX_UNAVAILABLE',
          message: cause instanceof Error ? cause.message : '审阅索引暂时不可用',
        });
      }
      if (result.items.length > limit) throw new Error('review-window索引返回超过请求上限');
      const first = result.items[0];
      const last = result.items.at(-1);
      const nextKey = last?.stableKey;
      const previousKey = first?.stableKey;
      const makeCursor = (key: string, cursorDirection: ReviewWindowDirection) => cursorToken(input.cursorSecret, {
        schema: 'review-window/v1', epoch: request.epoch, runId: request.runId,
        stream: request.stream, filterSha256, direction: cursorDirection, stableKey: key,
      });
      const page: ReviewWindowPage = {
        items: result.items,
        nextCursor: result.hasMore && nextKey ? makeCursor(nextKey, direction) : null,
        previousCursor: request.cursor && previousKey
          ? makeCursor(previousKey, direction === 'FORWARD' ? 'BACKWARD' : 'FORWARD')
          : null,
        total: result.total,
        epoch: request.epoch,
      };
      input.telemetry?.record({
        kind: 'QUERY', runId: request.runId, stream: request.stream, filterSha256,
        itemCount: page.items.length, byteLength: byteLength(JSON.stringify(page)), durationMs: now() - startedAt,
      });
      return page;
    },
    async readContent(request) {
      if (!request.runId.trim() || !/^sha256:[a-f0-9]{64}$/u.test(request.contentRef)
        || !/^[a-f0-9]{64}$/u.test(request.expectedSha256)
        || request.contentRef !== `sha256:${request.expectedSha256}`) {
        throw new Error('正文读取身份或SHA无效');
      }
      const startedAt = now();
      const cached = await input.cache?.read(request.contentRef) ?? null;
      if (cached !== null) {
        const sha256 = verifyContent(cached, request.expectedSha256);
        const result = {
          content: cached, contentRef: request.contentRef, sha256,
          byteLength: byteLength(cached), source: 'CACHE' as const,
        };
        input.telemetry?.record({
          kind: 'BODY_READ', runId: request.runId, contentRef: request.contentRef,
          source: result.source, byteLength: result.byteLength, durationMs: now() - startedAt,
        });
        return result;
      }
      if (input.isOnline?.() === false) throw new ReviewWindowError({
        code: 'OFFLINE_NOT_CACHED', message: '当前离线且正文尚未缓存', recoveryAction: 'RETRY',
      });
      const content = await input.bodies.read(request.contentRef);
      if (content === null) throw new ReviewWindowError({
        code: 'OFFLINE_NOT_CACHED', message: '正文暂时不可读取', recoveryAction: 'RETRY',
      });
      const sha256 = verifyContent(content, request.expectedSha256);
      if (input.cache) {
        try {
          await input.cache.write(request.contentRef, content);
        } catch (cause) {
          if (cause instanceof DOMException && cause.name === 'QuotaExceededError') {
            throw new ReviewWindowError({
              code: 'QUOTA_EXCEEDED', message: '浏览器存储空间不足，正文未缓存',
              storageEstimate: await input.cache.estimate(),
            });
          }
          throw cause;
        }
      }
      const result = {
        content, contentRef: request.contentRef, sha256,
        byteLength: byteLength(content), source: 'NETWORK' as const,
      };
      input.telemetry?.record({
        kind: 'BODY_READ', runId: request.runId, contentRef: request.contentRef,
        source: result.source, byteLength: result.byteLength, durationMs: now() - startedAt,
      });
      return result;
    },
  };
}

export function mergeReviewWindowPage(input: {
  current: { items: ReviewWindowSummary[]; spacerBefore: number; spacerAfter: number };
  incoming: ReviewWindowSummary[];
  direction: ReviewWindowDirection;
  rowSize: number;
  anchorStableKey: string;
}) {
  if (!Number.isFinite(input.rowSize) || input.rowSize <= 0) throw new Error('虚拟行高度无效');
  const byKey = new Set<string>();
  const combined = (input.direction === 'FORWARD'
    ? [...input.current.items, ...input.incoming]
    : [...input.incoming, ...input.current.items]).filter((item) => {
    if (byKey.has(item.stableKey)) return false;
    byKey.add(item.stableKey);
    return true;
  });
  const overflow = Math.max(0, combined.length - REVIEW_WINDOW_MAX_ITEMS);
  const items = input.direction === 'FORWARD' ? combined.slice(overflow) : combined.slice(0, REVIEW_WINDOW_MAX_ITEMS);
  return {
    items,
    spacerBefore: input.current.spacerBefore + (input.direction === 'FORWARD' ? overflow * input.rowSize : 0),
    spacerAfter: input.current.spacerAfter + (input.direction === 'BACKWARD' ? overflow * input.rowSize : 0),
    anchor: { stableKey: input.anchorStableKey, offsetDelta: 0 },
  };
}

const generatedPrefix: Record<ReviewWindowStream, string> = {
  TIMELINE: 'timeline', ISSUES: 'issue', EVIDENCE: 'evidence', ASSISTANT_HISTORY: 'turn',
  DOCUMENT_BLOCKS: 'block', DELIVERABLE_SECTIONS: 'section',
};

export function createGeneratedReviewWindowIndex(
  sizes: Partial<Record<ReviewWindowStream, number>>,
): ReviewWindowIndex & { metrics: { materializedItemCount: number } } {
  const metrics = { materializedItemCount: 0 };
  return {
    metrics,
    async query(input) {
      const total = sizes[input.stream] ?? 0;
      const numericKey = input.stableKey ? Number(input.stableKey.split(':').at(-1)) : undefined;
      const start = input.direction === 'FORWARD'
        ? (numericKey === undefined ? 0 : numericKey + 1)
        : (numericKey === undefined ? total - 1 : numericKey - 1);
      const indexes = Array.from({ length: input.limit }, (_, offset) => (
        input.direction === 'FORWARD' ? start + offset : start - offset
      )).filter((index) => index >= 0 && index < total);
      const items = indexes.map((index) => {
        const stableKey = `${generatedPrefix[input.stream]}:${String(index).padStart(9, '0')}`;
        return { stableKey, title: `${input.stream} ${index + 1}`, summary: `摘要 ${index + 1}` };
      });
      const boundary = indexes.at(-1);
      const hasMore = boundary !== undefined && (input.direction === 'FORWARD' ? boundary < total - 1 : boundary > 0);
      return { items, total, hasMore };
    },
  };
}

export { createIndexedDbReviewWindowIndex } from './indexeddb-index.ts';
export type { IndexedDbReviewWindowIndex } from './indexeddb-index.ts';
