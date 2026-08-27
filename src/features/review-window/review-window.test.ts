import assert from 'node:assert/strict';
import test from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  REVIEW_WINDOW_MAX_ITEMS,
  REVIEW_WINDOW_OVERSCAN,
  ReviewWindowError,
  createArrayReviewWindowIndex,
  createGeneratedReviewWindowIndex,
  createIndexedDbReviewWindowIndex,
  createStandardizationReviewWindow,
  mergeReviewWindowPage,
  projectReviewWindowError,
  assertReviewMutationAllowed,
  type ReviewContentCache,
  type ReviewWindowTelemetryEvent,
} from './index.ts';
import {
  createReviewPersistenceFaultController,
  withReviewContentCacheFaultpoints,
  withStandardizationMetadataFaultpoints,
  withStandardizationRunReaderFaultpoints,
} from './faultpoint-adapters.ts';
import {
  acknowledgeVerifiedStreamTarget,
  readAndVerifyReviewWindowPage,
  recoverVerifiedStreamTarget,
  type VerifiedStreamRecoveryTarget,
} from './recovery-consumer.ts';

function ref(content: string) {
  return `sha256:${sha256HexSync(content)}` as const;
}

test('stream recovery cannot clear a failure until a summary content reference is verified', async () => {
  const contentRef = ref('recoverable stream body');
  const page = {
    items: [{ stableKey: 'deliverable:1', title: '交付段落', summary: 'summary only', contentRef,
      contentSha256: contentRef.slice('sha256:'.length) }],
    nextCursor: null, previousCursor: null, total: 1, epoch: 'revision:1',
  };
  let verifiedReads = 0;
  const recovered = await readAndVerifyReviewWindowPage({
    readPage: async () => page,
    readContent: async (input) => {
      verifiedReads += 1;
      assert.equal(input.contentRef, contentRef);
      assert.equal(input.expectedSha256, contentRef.slice('sha256:'.length));
      return { content: 'recoverable stream body' };
    },
  }, { contentRef, expectedSha256: contentRef.slice('sha256:'.length) });
  assert.equal(recovered, page);
  assert.equal(verifiedReads, 1);

  await assert.rejects(
    readAndVerifyReviewWindowPage({
      readPage: async () => ({ ...page, items: [] }),
      readContent: async () => { throw new Error('must not read without a reference'); },
    }, { contentRef, expectedSha256: contentRef.slice('sha256:'.length) }),
    (cause: unknown) => cause instanceof ReviewWindowError && cause.code === 'STORE_UNAVAILABLE',
  );
});

test('secondary index failure projects to a readable non-blocking compatibility mode', () => {
  const projection = projectReviewWindowError(new ReviewWindowError({
    code: 'INDEX_UNAVAILABLE',
    message: 'permission denied',
  }));
  assert.equal(projection.title, '兼容阅读模式');
  assert.equal(projection.blocksMutation, false);
  assert.equal(projection.action, undefined);
});

test('summary recovery requires the original failure target instead of trusting the first page item', async () => {
  const originalRef = ref('original failed body');
  const unrelatedRef = ref('unrelated first page body');
  const page = {
    items: [
      { stableKey: 'deliverable:current', title: '当前首项', summary: 'summary',
        contentRef: unrelatedRef, contentSha256: unrelatedRef.slice('sha256:'.length) },
      { stableKey: 'deliverable:failed', title: '原失败项', summary: 'summary',
        contentRef: originalRef, contentSha256: originalRef.slice('sha256:'.length) },
    ],
    nextCursor: null, previousCursor: null, total: 2, epoch: 'revision:1',
  };
  let verifiedRef: string | undefined;
  const recovered = await readAndVerifyReviewWindowPage({
    readPage: async () => page,
    readContent: async ({ contentRef }) => { verifiedRef = contentRef; return { content: 'verified' }; },
  }, { contentRef: originalRef, expectedSha256: originalRef.slice('sha256:'.length) });
  assert.equal(recovered, page);
  assert.equal(verifiedRef, originalRef, 'retry must verify the recorded failure body, not page.items[0]');

  await assert.rejects(
    readAndVerifyReviewWindowPage({
      readPage: async () => page,
      readContent: async () => { throw new Error('must not read without a recorded target'); },
    }, undefined),
    (cause: unknown) => cause instanceof ReviewWindowError && cause.code === 'STORE_UNAVAILABLE',
  );
});

test('recorded stream target recovery keeps the failure when the body is still bad', async () => {
  const contentRef = ref('assistant recovery body');
  const target: VerifiedStreamRecoveryTarget = {
    stream: 'ASSISTANT_HISTORY', stableKey: 'assistant:1', contentRef,
    expectedSha256: contentRef.slice('sha256:'.length),
  };
  let reads = 0;
  let cleared = 0;
  await assert.rejects(
    recoverVerifiedStreamTarget({
      target,
      readContent: async () => {
        reads += 1;
        throw new ReviewWindowError({ code: 'CHECKSUM_MISMATCH', message: 'body is still bad' });
      },
      onVerified: () => { cleared += 1; },
    }),
    /body is still bad/u,
  );
  assert.equal(reads, 1);
  assert.equal(cleared, 0, 'summary success or failed body read must not clear the failure');

  await recoverVerifiedStreamTarget({
    target,
    readContent: async () => { reads += 1; return { content: 'assistant recovery body' }; },
    onVerified: () => { cleared += 1; },
  });
  assert.equal(reads, 2);
  assert.equal(cleared, 1);

  await assert.rejects(
    recoverVerifiedStreamTarget({
      target: undefined,
      readContent: async () => { throw new Error('must not read without a target'); },
      onVerified: () => { cleared += 1; },
    }),
    (cause: unknown) => cause instanceof ReviewWindowError && cause.code === 'STORE_UNAVAILABLE',
  );
  assert.equal(cleared, 1, 'missing target cannot clear an aggregate stream failure');
});

test('timeline, issues, and evidence retries read their recorded target rather than current selection', async () => {
  const streams = ['TIMELINE', 'ISSUES', 'EVIDENCE'] as const;
  for (const stream of streams) {
    const contentRef = ref(`${stream} original body`);
    const target: VerifiedStreamRecoveryTarget = {
      stream, stableKey: `${stream.toLowerCase()}:original`, contentRef,
      expectedSha256: contentRef.slice('sha256:'.length),
    };
    let readInput: { contentRef: string; expectedSha256: string } | undefined;
    let verified = 0;
    await recoverVerifiedStreamTarget({
      target,
      readContent: async (input) => { readInput = input; return { content: 'verified' }; },
      onVerified: () => { verified += 1; },
    });
    assert.deepEqual(readInput, { contentRef, expectedSha256: target.expectedSha256 });
    assert.equal(verified, 1);
  }
});

test('ordinary stream reads cannot clear a recorded failure with a different or missing body target', () => {
  const streams = [
    'TIMELINE', 'ASSISTANT_HISTORY', 'DOCUMENT_BLOCKS', 'ISSUES', 'EVIDENCE', 'DELIVERABLE_SECTIONS',
  ] as const;
  for (const stream of streams) {
    const originalRef = ref(`${stream} failed body`);
    const unrelatedRef = ref(`${stream} unrelated body`);
    const recordedTarget: VerifiedStreamRecoveryTarget = {
      stream, stableKey: `${stream.toLowerCase()}:failed`, contentRef: originalRef,
      expectedSha256: originalRef.slice('sha256:'.length),
    };
    let cleared = 0;

    assert.equal(acknowledgeVerifiedStreamTarget({
      recordedTarget,
      verifiedTarget: { contentRef: unrelatedRef, expectedSha256: unrelatedRef.slice('sha256:'.length) },
      onVerified: () => { cleared += 1; },
    }), false);
    assert.equal(acknowledgeVerifiedStreamTarget({
      recordedTarget: undefined,
      verifiedTarget: { contentRef: originalRef, expectedSha256: originalRef.slice('sha256:'.length) },
      onVerified: () => { cleared += 1; },
    }), false);
    assert.equal(acknowledgeVerifiedStreamTarget({
      recordedTarget,
      verifiedTarget: { contentRef: originalRef, expectedSha256: unrelatedRef.slice('sha256:'.length) },
      onVerified: () => { cleared += 1; },
    }), false);
    assert.equal(cleared, 0, `${stream} must preserve the original failure`);

    assert.equal(acknowledgeVerifiedStreamTarget({
      recordedTarget,
      verifiedTarget: { contentRef: originalRef, expectedSha256: originalRef.slice('sha256:'.length) },
      onVerified: () => { cleared += 1; },
    }), true);
    assert.equal(cleared, 1, `${stream} may clear only after the original body and SHA are verified`);
  }
});

test('production array adapter reads the newest Timeline window through keyset ordering', async () => {
  const index = createArrayReviewWindowIndex(Array.from({ length: 75 }, (_, index) => ({
    stableKey: `event-${String(index + 1).padStart(3, '0')}`,
    title: `事件 ${index + 1}`,
    summary: 'summary only',
  })));
  const runtime = createStandardizationReviewWindow({
    cursorSecret: 'timeline-secret', index,
    bodies: { async read() { throw new Error('Timeline first paint must not read bodies'); } },
  });
  const page = await runtime.read({
    runId: 'run-1', epoch: 'r1', stream: 'TIMELINE', filter: '', direction: 'BACKWARD',
  });
  assert.equal(page.items.length, 40);
  assert.equal(page.items[0].stableKey, 'event-075');
  assert.equal(page.items.at(-1)?.stableKey, 'event-036');
  assert.ok(page.nextCursor);
});

test('BACKWARD keyset uses the traversed page tail so the second Timeline page has no duplicates', async () => {
  const runtime = createStandardizationReviewWindow({
    cursorSecret: 'timeline-secret',
    index: createArrayReviewWindowIndex(Array.from({ length: 75 }, (_, index) => ({
      stableKey: `event-${String(index + 1).padStart(3, '0')}`,
      title: `事件 ${index + 1}`,
      summary: 'summary only',
    }))),
    bodies: { async read() { return null; } },
  });
  const first = await runtime.read({
    runId: 'run-1', epoch: 'r1', stream: 'TIMELINE', filter: '', direction: 'BACKWARD', limit: 40,
  });
  const second = await runtime.read({
    runId: 'run-1', epoch: 'r1', stream: 'TIMELINE', filter: '', direction: 'BACKWARD',
    limit: 40, cursor: first.nextCursor!,
  });
  assert.equal(second.items[0].stableKey, 'event-035');
  assert.equal(second.items.at(-1)?.stableKey, 'event-001');
  assert.equal(new Set([...first.items, ...second.items].map(({ stableKey }) => stableKey)).size, 75);
});

test('10k issue／100k Evidence 首屏只读 summary，Timeline≤40、其余默认20且正文读取为0', async () => {
  let bodyReads = 0;
  const telemetry: ReviewWindowTelemetryEvent[] = [];
  const index = createGeneratedReviewWindowIndex({
    TIMELINE: 10_000,
    ISSUES: 10_000,
    EVIDENCE: 100_000,
  });
  const runtime = createStandardizationReviewWindow({
    cursorSecret: 'cp8-fixed-test-secret', index,
    bodies: { async read() { bodyReads += 1; return null; } },
    telemetry: { record(event) { telemetry.push(event); } },
  });
  const timeline = await runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'TIMELINE', filter: 'all' });
  const issues = await runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'ISSUES', filter: 'open' });
  const evidence = await runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'EVIDENCE', filter: 'conflict:c1' });
  assert.equal(timeline.items.length, 40);
  assert.equal(issues.items.length, 20);
  assert.equal(evidence.items.length, 20);
  assert.equal(bodyReads, 0);
  assert.ok(Buffer.byteLength(JSON.stringify({ timeline, issues }), 'utf8') < 256 * 1024);
  assert.deepEqual(telemetry.map((event) => event.kind), ['QUERY', 'QUERY', 'QUERY']);
  assert.ok(telemetry.every((event) => !('body' in event)));
  assert.equal(index.metrics.materializedItemCount, 0, '生成式索引不得把100k项物化为数组或Map');
});

test('opaque keyset cursor 绑定 schema/epoch/run/stream/filter/direction，翻页无重复且不可跨查询复用', async () => {
  const runtime = createStandardizationReviewWindow({
    cursorSecret: 'cp8-fixed-test-secret',
    index: createGeneratedReviewWindowIndex({ ISSUES: 125 }),
    bodies: { async read() { return null; } },
  });
  const first = await runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'ISSUES', filter: 'open', limit: 50 });
  const second = await runtime.read({
    runId: 'run-1', epoch: 'e1', stream: 'ISSUES', filter: 'open', limit: 50, cursor: first.nextCursor!,
  });
  assert.equal(first.items.length, 50);
  assert.equal(second.items.length, 50);
  assert.equal(new Set([...first.items, ...second.items].map((item) => item.stableKey)).size, 100);
  await assert.rejects(
    runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'ISSUES', filter: 'closed', cursor: first.nextCursor! }),
    (cause) => cause instanceof ReviewWindowError && cause.code === 'CURSOR_EXPIRED',
  );
  await assert.rejects(
    runtime.read({ runId: 'run-1', epoch: 'e2', stream: 'ISSUES', filter: 'open', cursor: first.nextCursor! }),
    (cause) => cause instanceof ReviewWindowError && cause.code === 'CURSOR_EXPIRED',
  );
  const tampered = `${first.nextCursor!.slice(0, -1)}${first.nextCursor!.endsWith('A') ? 'B' : 'A'}`;
  await assert.rejects(
    runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'ISSUES', filter: 'open', cursor: tampered }),
    (cause) => cause instanceof ReviewWindowError && cause.code === 'CURSOR_EXPIRED',
  );
});

test('page硬上限50、前端window硬上限80并以spacer与anchor保留滚动位置', async () => {
  const runtime = createStandardizationReviewWindow({
    cursorSecret: 'cp8-fixed-test-secret', index: createGeneratedReviewWindowIndex({ ISSUES: 200 }),
    bodies: { async read() { return null; } },
  });
  await assert.rejects(
    runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'ISSUES', filter: 'all', limit: 51 }),
    /每页最多50项/,
  );
  const first = await runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'ISSUES', filter: 'all', limit: 50 });
  const second = await runtime.read({
    runId: 'run-1', epoch: 'e1', stream: 'ISSUES', filter: 'all', limit: 50, cursor: first.nextCursor!,
  });
  const merged = mergeReviewWindowPage({
    current: { items: first.items, spacerBefore: 0, spacerAfter: 0 },
    incoming: second.items,
    direction: 'FORWARD',
    rowSize: 48,
    anchorStableKey: first.items[35].stableKey,
  });
  assert.equal(REVIEW_WINDOW_MAX_ITEMS, 80);
  assert.equal(REVIEW_WINDOW_OVERSCAN, 6);
  assert.equal(merged.items.length, 80);
  assert.equal(merged.spacerBefore, 20 * 48);
  assert.equal(merged.anchor.stableKey, first.items[35].stableKey);
  assert.equal(merged.anchor.offsetDelta, 0);
});

test('正文只在展开时读取并复算SHA；offline缓存可读、未缓存唯一恢复为重新读取', async () => {
  const body = '## 库存\n不允许负库存';
  const bodyRef = ref(body);
  let online = true;
  let reads = 0;
  const cached = new Map<string, string>();
  const cache: ReviewContentCache = {
    async read(contentRef) { return cached.get(contentRef) ?? null; },
    async write(contentRef, value) { cached.set(contentRef, value); },
    async estimate() { return { usage: 1024, quota: 4096 }; },
  };
  const runtime = createStandardizationReviewWindow({
    cursorSecret: 'cp8-fixed-test-secret', index: createGeneratedReviewWindowIndex({ DOCUMENT_BLOCKS: 1 }),
    bodies: { async read(contentRef) { reads += 1; return online && contentRef === bodyRef ? body : null; } },
    cache,
    isOnline: () => online,
  });
  await runtime.read({ runId: 'run-1', epoch: 'e1', stream: 'DOCUMENT_BLOCKS', filter: 'document:r1' });
  assert.equal(reads, 0);
  const first = await runtime.readContent({ runId: 'run-1', contentRef: bodyRef, expectedSha256: sha256HexSync(body) });
  assert.equal(first.content, body);
  assert.equal(first.source, 'NETWORK');
  online = false;
  const offlineCached = await runtime.readContent({ runId: 'run-1', contentRef: bodyRef, expectedSha256: sha256HexSync(body) });
  assert.equal(offlineCached.source, 'CACHE');
  await assert.rejects(
    runtime.readContent({ runId: 'run-1', contentRef: ref('never cached'), expectedSha256: sha256HexSync('never cached') }),
    (cause) => cause instanceof ReviewWindowError
      && cause.code === 'OFFLINE_NOT_CACHED'
      && cause.recoveryAction === 'RETRY',
  );
});

test('checksum mismatch 不返回损坏正文并携带expected/actual；quota不推进任何业务metadata', async () => {
  const expected = '可信正文';
  const corrupted = '损坏正文';
  let metadataWrites = 0;
  let quota = false;
  const runtime = createStandardizationReviewWindow({
    cursorSecret: 'cp8-fixed-test-secret', index: createGeneratedReviewWindowIndex({ DELIVERABLE_SECTIONS: 1 }),
    bodies: { async read() { return quota ? expected : corrupted; } },
    cache: {
      async read() { return null; },
      async write() {
        if (quota) throw new DOMException('quota', 'QuotaExceededError');
        metadataWrites += 1;
      },
      async estimate() { return { usage: 900, quota: 1000 }; },
    },
  });
  await assert.rejects(
    runtime.readContent({ runId: 'run-1', contentRef: ref(expected), expectedSha256: sha256HexSync(expected) }),
    (cause) => cause instanceof ReviewWindowError
      && cause.code === 'CHECKSUM_MISMATCH'
      && cause.expectedSha256 === sha256HexSync(expected)
      && cause.actualSha256 === sha256HexSync(corrupted),
  );
  assert.equal(metadataWrites, 0);
  quota = true;
  await assert.rejects(
    runtime.readContent({ runId: 'run-1', contentRef: ref(expected), expectedSha256: sha256HexSync(expected) }),
    (cause) => cause instanceof ReviewWindowError
      && cause.code === 'QUOTA_EXCEEDED'
      && cause.storageEstimate?.usage === 900,
  );
  assert.equal(metadataWrites, 0, 'review-window没有Run/Document metadata写入seam');
});

test('review-window错误投影只暴露契约允许的恢复动作和技术完整性事实', () => {
  const offline = projectReviewWindowError(new ReviewWindowError({
    code: 'OFFLINE_NOT_CACHED', message: '离线未缓存', recoveryAction: 'RETRY',
  }));
  assert.equal(offline.action?.label, '重新读取');
  assert.equal(offline.blocksMutation, false);
  const checksum = projectReviewWindowError(new ReviewWindowError({
    code: 'CHECKSUM_MISMATCH', message: '正文损坏',
    expectedSha256: 'a'.repeat(64), actualSha256: 'b'.repeat(64),
  }));
  assert.equal(checksum.action, undefined);
  assert.equal(checksum.blocksMutation, true);
  assert.match(checksum.technicalDetail!, /expected.*actual/u);
  const quota = projectReviewWindowError(new ReviewWindowError({
    code: 'QUOTA_EXCEEDED', message: '空间不足', storageEstimate: { usage: 90, quota: 100 },
  }));
  assert.equal(quota.action, undefined);
  assert.match(quota.technicalDetail!, /90.*100/u);
});

test('verified-content failures aggregate by stream and fail-close every content-dependent mutation', () => {
  const mutations = [
    'DOCUMENT_PATCH', 'DOCUMENT_REVIEW', 'CONFLICT_RESOLUTION', 'ASSISTANT_PATCH',
    'DELIVERABLE_GENERATE', 'DELIVERABLE_AUTHOR_CONFIRM', 'DELIVERABLE_REVIEW_FREEZE',
    'DELIVERABLE_HANDOFF', 'DELIVERABLE_PENDING_RESUME',
  ] as const;
  const failures = {
    DOCUMENT_BLOCKS: new ReviewWindowError({ code: 'CHECKSUM_MISMATCH', message: 'document checksum' }),
    EVIDENCE: new ReviewWindowError({ code: 'STORE_UNAVAILABLE', message: 'evidence store' }),
    ASSISTANT_HISTORY: new ReviewWindowError({ code: 'STORE_UNAVAILABLE', message: 'assistant history store' }),
    DELIVERABLE_SECTIONS: new ReviewWindowError({ code: 'QUOTA_EXCEEDED', message: 'deliverable quota' }),
  };
  for (const mutation of mutations) {
    assert.throws(() => assertReviewMutationAllowed(failures, mutation), /审阅正文完整性尚未恢复/);
  }

  const documentRecovered = { ...failures };
  delete documentRecovered.DOCUMENT_BLOCKS;
  assert.throws(
    () => assertReviewMutationAllowed(documentRecovered, 'ASSISTANT_PATCH'),
    /审阅正文完整性尚未恢复/,
    'one successful stream must not clear another stream failure',
  );
  assert.doesNotThrow(() => assertReviewMutationAllowed({}, 'DELIVERABLE_HANDOFF'));

  let remaining = { ...failures };
  for (const stream of ['DOCUMENT_BLOCKS', 'EVIDENCE', 'ASSISTANT_HISTORY', 'DELIVERABLE_SECTIONS'] as const) {
    delete remaining[stream];
    if (Object.keys(remaining).length > 0) {
      assert.throws(
        () => assertReviewMutationAllowed(remaining, 'DELIVERABLE_HANDOFF'),
        /审阅正文完整性尚未恢复/u,
        `恢复${stream}后其他失败流仍必须阻止写入`,
      );
    } else {
      assert.doesNotThrow(() => assertReviewMutationAllowed(remaining, 'DELIVERABLE_HANDOFF'));
    }
  }

  const nonBlocking = {
    TIMELINE: new ReviewWindowError({ code: 'CURSOR_EXPIRED', message: 'cursor' }),
    ASSISTANT_HISTORY: new ReviewWindowError({ code: 'OFFLINE_NOT_CACHED', message: 'offline' }),
  };
  for (const mutation of mutations) assert.doesNotThrow(() => assertReviewMutationAllowed(nonBlocking, mutation));
});

test('one-shot persistence adapters exercise real CAS, quota and delivery append seams without changing domain runtimes', async () => {
  const faults = createReviewPersistenceFaultController();
  let metadataVersion = 3;
  const metadata = withStandardizationMetadataFaultpoints({
    async read() { return { version: String(metadataVersion), raw: '{}' }; },
    async compareAndSet(expectedVersion) {
      if (expectedVersion !== String(metadataVersion)) return false;
      metadataVersion += 1;
      return true;
    },
  }, faults);
  faults.arm('STANDARDIZATION_CAS_STALE');
  assert.equal(await metadata.compareAndSet('3', '{"next":true}'), false);
  assert.equal((await metadata.read()).version, '3');
  assert.equal(await metadata.compareAndSet('3', '{"next":true}'), true);
  assert.equal((await metadata.read()).version, '4');

  let cacheWrites = 0;
  const cache = withReviewContentCacheFaultpoints({
    async read() { return null; },
    async write() { cacheWrites += 1; },
    async estimate() { return { usage: 9, quota: 10 }; },
  }, faults);
  faults.arm('REVIEW_CACHE_QUOTA');
  await assert.rejects(cache.write('sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'body'), {
    name: 'QuotaExceededError',
  });
  assert.equal(cacheWrites, 0);
  await cache.write('sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'body');
  assert.equal(cacheWrites, 1);

  let appended = 0;
  const runReader = withStandardizationRunReaderFaultpoints({
    async read() { return null; },
    async readEventPayload() { return '{}'; },
    async appendEvent() { appended += 1; return {} as never; },
  }, faults);
  const appendInput = {
    type: 'DELIVERABLE_FROZEN' as const, commandId: 'freeze:link', runId: 'run-1',
    expectedRunRevision: 7, actorUserId: 'reviewer', deliverableId: 'deliverable-1',
    eventIdentity: 'deliverable-1', payload: '{}',
  };
  faults.arm('DELIVERY_EVENT_APPEND_FAILED');
  await assert.rejects(runReader.appendEvent(appendInput), /injected delivery event append failure/);
  assert.equal(appended, 0);
  await runReader.appendEvent(appendInput);
  assert.equal(appended, 1);
});

test('IndexedDB缺失或open SecurityError投影可恢复错误，且重试会重新open', async () => {
  const missing = createIndexedDbReviewWindowIndex({ factory: undefined });
  const query = { runId: 'run-1', epoch: 'e1', stream: 'TIMELINE' as const, filter: '', direction: 'FORWARD' as const, limit: 20 };
  await assert.rejects(missing.query(query), (cause) => cause instanceof ReviewWindowError
    && cause.code === 'STORE_UNAVAILABLE' && cause.recoveryAction === 'RETRY');

  let opens = 0;
  const factory = {
    open() {
      opens += 1;
      const request: Partial<IDBOpenDBRequest> = {};
      queueMicrotask(() => {
        Object.defineProperty(request, 'error', { value: new DOMException('permission denied', 'SecurityError') });
        request.onerror?.(new Event('error'));
      });
      return request as IDBOpenDBRequest;
    },
  } as IDBFactory;
  const retryable = createIndexedDbReviewWindowIndex({ factory, databaseName: 'denied' });
  await assert.rejects(retryable.query(query), (cause) => cause instanceof ReviewWindowError
    && cause.code === 'STORE_UNAVAILABLE');
  await assert.rejects(retryable.query(query), (cause) => cause instanceof ReviewWindowError
    && cause.code === 'STORE_UNAVAILABLE');
  assert.equal(opens, 2, '失败的databasePromise不能永久缓存');
  assert.equal(projectReviewWindowError(new ReviewWindowError({
    code: 'STORE_UNAVAILABLE', message: 'permission denied', recoveryAction: 'RETRY',
  })).title, '浏览器无法保存本次审阅');
  assert.equal(projectReviewWindowError(new ReviewWindowError({
    code: 'STORE_UNAVAILABLE', message: 'permission denied', recoveryAction: 'RETRY',
  })).action, undefined);
});

test('同一个审阅索引实例可从STORE_UNAVAILABLE重试并委托真实IndexedDB', async () => {
  const previousKeyRange = (globalThis as typeof globalThis & { IDBKeyRange?: unknown }).IDBKeyRange;
  (globalThis as typeof globalThis & { IDBKeyRange: unknown }).IDBKeyRange = {
    bound(...bounds: unknown[]) { return { bounds }; },
  };
  try {
    let realOpenCount = 0;
    const realFactory = {
      open() {
        realOpenCount += 1;
        const request: Partial<IDBOpenDBRequest> = {};
        const objectStoreNames = { contains: () => true, length: 1, item: () => 'summaries' };
        const database = {
          objectStoreNames,
          createObjectStore() {
            return { createIndex() {} };
          },
          transaction() {
            return {
              objectStore() {
                return {
                  index() {
                    return {
                      count() {
                        const countRequest: Partial<IDBRequest<number>> = {};
                        queueMicrotask(() => countRequest.onsuccess?.(new Event('success')));
                        Object.defineProperty(countRequest, 'result', { value: 0 });
                        return countRequest as IDBRequest<number>;
                      },
                      openCursor() {
                        const cursorRequest: Partial<IDBRequest<IDBCursorWithValue | null>> = {};
                        queueMicrotask(() => cursorRequest.onsuccess?.(new Event('success')));
                        Object.defineProperty(cursorRequest, 'result', { value: null });
                        return cursorRequest as IDBRequest<IDBCursorWithValue | null>;
                      },
                    };
                  },
                };
              },
              oncomplete: null,
              onerror: null,
              onabort: null,
            };
          },
        } as unknown as IDBDatabase;
        Object.defineProperty(request, 'result', { value: database });
        queueMicrotask(() => {
          request.onupgradeneeded?.(new Event('upgradeneeded'));
          request.onsuccess?.(new Event('success'));
        });
        return request as IDBOpenDBRequest;
      },
    } as IDBFactory;
    let firstAttempt = true;
    let statefulOpens = 0;
    const statefulFactory = {
      open(name: string, version?: number) {
        statefulOpens += 1;
        if (firstAttempt) {
          firstAttempt = false;
          throw new DOMException('permission denied', 'SecurityError');
        }
        return realFactory.open(name, version);
      },
    } as IDBFactory;
    const index = createIndexedDbReviewWindowIndex({ factory: statefulFactory, databaseName: 'cp8-stateful-retry' });
    const query = {
      runId: 'run-1', epoch: 'e1', stream: 'TIMELINE' as const,
      filter: '', direction: 'FORWARD' as const, limit: 20,
    };
    await assert.rejects(index.query(query), (cause) => cause instanceof ReviewWindowError
      && cause.code === 'STORE_UNAVAILABLE' && cause.recoveryAction === 'RETRY');
    await index.retry();
    const recovered = await index.query(query);
    assert.deepEqual(recovered, { items: [], total: 0, hasMore: false });
    assert.equal(statefulOpens, 2, 'retry必须复用同一个index实例而重新open');
    assert.equal(realOpenCount, 1, '第二次open必须委托真实IndexedDB factory');
  } finally {
    if (previousKeyRange === undefined) delete (globalThis as typeof globalThis & { IDBKeyRange?: unknown }).IDBKeyRange;
    else (globalThis as typeof globalThis & { IDBKeyRange: unknown }).IDBKeyRange = previousKeyRange;
  }
});
