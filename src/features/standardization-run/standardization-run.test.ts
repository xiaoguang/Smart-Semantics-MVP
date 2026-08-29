import assert from 'node:assert/strict';
import test from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import { createMemoryContentStore } from '../source-documents/runtime.ts';
import type { ContentAddressedStore } from '../source-documents/types.ts';
import type {
  StandardizationMetadataStore,
  StandardizationRunCommand,
} from './types.ts';
import { createStandardizationRunRuntime } from './runtime.ts';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';
import { projectConflictResolution } from '../guanyijia-standardization-story/conflict-resolution.ts';
import type {
  ConflictResolutionArtifact,
  ConflictResolutionStrategy,
} from '../guanyijia-standardization-story/types.ts';

const actor = { userId: 'standardization-author' };
const resolutionStory = createGuanyijiaStandardizationStory();
const deliveryGeneratedPayload = (deliverableId: string) => JSON.stringify({
  schemaVersion: 1, deliverableId,
  coreSha256: `sha256:${'a'.repeat(64)}`,
  sourceManifestRef: `sha256:${'b'.repeat(64)}`,
  mergedDocumentRef: `sha256:${'c'.repeat(64)}`,
  decisionManifestRef: `sha256:${'d'.repeat(64)}`,
  governanceAppendixRef: `sha256:${'e'.repeat(64)}`,
  modelingArtifactRef: `sha256:${'f'.repeat(64)}`,
  zeroDeltaReportRef: `sha256:${'1'.repeat(64)}`,
});
const reviewSubmittedPayload = (deliverableId: string, reviewId: string) => JSON.stringify({
  schemaVersion: 1, deliverableId, reviewId,
  authorUserId: actor.userId, confirmedAt: '2026-08-17T12:00:00.000Z',
  coreSha256: `sha256:${'a'.repeat(64)}`,
});

class MemoryStorage {
  private values = new Map<string, string>();

  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  raw(key: string) { return this.values.get(key) ?? null; }
}

class FakeAtomicMetadataBackend {
  private version = 0;
  private value: string | null = null;
  private barrier: { remaining: number; promise: Promise<void>; release: () => void } | null = null;
  failedCompareAndSetCount = 0;

  createStore(): StandardizationMetadataStore {
    return {
      read: async () => {
        const snapshot = { version: String(this.version), raw: this.value };
        const barrier = this.barrier;
        if (barrier) {
          barrier.remaining -= 1;
          if (barrier.remaining === 0) {
            this.barrier = null;
            barrier.release();
          }
          await barrier.promise;
        }
        return snapshot;
      },
      compareAndSet: async (expectedVersion, nextRaw) => {
        if (expectedVersion !== String(this.version)) {
          this.failedCompareAndSetCount += 1;
          return false;
        }
        this.version += 1;
        this.value = nextRaw;
        return true;
      },
    };
  }

  synchronizeNextReads(participants: number) {
    let release!: () => void;
    const promise = new Promise<void>((resolve) => { release = resolve; });
    this.barrier = { remaining: participants, promise, release };
  }

  raw() { return this.value; }
}

const fiveSources = [
  { sourceId: 'mysql', sourceName: '管伊佳部署数据库' },
  { sourceId: 'github', sourceName: '管伊佳 GitHub' },
  { sourceId: 'core-docs', sourceName: '官方核心文档' },
  { sourceId: 'policy', sourceName: '演示制度 Markdown' },
  { sourceId: 'semantica', sourceName: '派生 Semantica' },
];
const deliveryCapability = {};

function setup() {
  const metadataStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const runtime = createStandardizationRunRuntime({
    metadataStorage,
    contentStore,
    deliveryCapability,
    resolutionArtifactValidator: validateTrustedResolutionArtifact,
    now: () => '2026-08-17T12:00:00.000Z',
  });
  return { metadataStorage, contentStore, runtime };
}

async function createRun(
  runtime: ReturnType<typeof createStandardizationRunRuntime>,
  input: {
    commandId?: string; projectId?: string; batchId?: string;
    scenarioKey?: string;
    sources?: Array<{ sourceId: string; sourceName: string; snapshotId?: string }>;
  } = {},
) {
  return runtime.execute({
    type: 'CREATE_RUN',
    commandId: input.commandId ?? 'create-run',
    expectedRevision: 0,
    actor,
    projectId: input.projectId ?? 'guanyijia-erp',
    batchId: input.batchId ?? 'batch-five-source-v1',
    scenarioKey: input.scenarioKey ?? 'guanyijia-five-source-v1',
    sources: input.sources ?? fiveSources,
  });
}

async function completeCurrentSource(
  runtime: ReturnType<typeof createStandardizationRunRuntime>,
  input: {
    runId: string;
    sourceId: string;
    expectedRevision: number;
    commandId: string;
    conflicts?: string[];
    payload?: string;
  },
) {
  return runtime.execute({
    type: 'COMPLETE_SOURCE_DOCUMENT',
    commandId: input.commandId,
    runId: input.runId,
    expectedRevision: input.expectedRevision,
    actor,
    sourceId: input.sourceId,
    readSummary: { summary: `${input.sourceId} 已读取`, objectCount: 12, evidenceCount: 20 },
    documentId: `document-${input.sourceId}`,
    documentRevision: 1,
    introducedConflictIds: input.conflicts ?? [],
    payload: input.payload ?? `# ${input.sourceId} 九段式文档`,
  });
}

async function prepareSingleSource(
  runtime: ReturnType<typeof createStandardizationRunRuntime>,
  conflicts: string[] = [],
  source = fiveSources[0]!,
) {
  let run = await createRun(runtime, { sources: [source] });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'prepare-start', runId: run.runId,
    expectedRevision: 0, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: source.sourceId, expectedRevision: 1,
    commandId: 'prepare-complete', conflicts,
  });
  return run;
}

async function reviewSingleSource(
  runtime: ReturnType<typeof createStandardizationRunRuntime>,
  conflicts: string[] = [],
  source = fiveSources[0]!,
) {
  const run = await prepareSingleSource(runtime, conflicts, source);
  return runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'prepare-review', runId: run.runId,
    expectedRevision: 2, actor, sourceId: source.sourceId,
  });
}

function resolutionArtifactPayload(input: {
  runId: string;
  sourceId: string;
  conflictId: 'gyj-conflict-debt-schema' | 'gyj-conflict-negative-stock' | 'gyj-conflict-status-nine';
  strategy: ConflictResolutionStrategy;
  reason: string;
}) {
  const story = createGuanyijiaStandardizationStory();
  const compilations = story.listSources().slice(0, 4).reduce<ReturnType<typeof story.compileSource>[]>(
    (values, source) => [...values, story.compileSource({
      sourceId: source.sourceId,
      priorCompilations: values,
    })],
    [],
  );
  const preview = story.previewConflictResolution({
    conflictId: input.conflictId,
    strategy: input.strategy,
    sourceRevisions: compilations.map((compilation, index) => ({
      documentId: `persisted-document-${index + 1}`,
      documentRevision: 1,
      compilation,
    })),
  });
  const artifact: ConflictResolutionArtifact = {
    schemaVersion: 1,
    resolutionId: `resolution:${input.runId}:${input.conflictId}`,
    runId: input.runId,
    sourceId: input.sourceId,
    reason: input.reason,
    actorUserId: actor.userId,
    decidedAt: '2026-08-17T12:00:00.000Z',
    ...preview,
  };
  return { artifact, payload: JSON.stringify(artifact) };
}

function rehashArtifactPreview(artifact: ConflictResolutionArtifact) {
  const previewContent = {
    hunk: artifact.hunk,
    strategy: artifact.strategy,
    result: artifact.result,
    structuredPatch: artifact.structuredPatch,
    markdownDiff: artifact.markdownDiff,
    provenanceSources: artifact.provenanceSources,
  };
  artifact.previewSha256 = `sha256:${sha256HexSync(canonicalModelingJson(previewContent))}`;
  return JSON.stringify(artifact);
}

function validateTrustedResolutionArtifact(artifact: ConflictResolutionArtifact) {
  resolutionStory.validateConflictResolutionArtifactProjection(artifact);
  const expected = resolutionArtifactPayload({
    runId: artifact.runId,
    sourceId: artifact.sourceId,
    conflictId: artifact.hunk.conflictId as Parameters<typeof resolutionArtifactPayload>[0]['conflictId'],
    strategy: artifact.strategy,
    reason: artifact.reason,
  }).artifact;
  const previewProjection = (value: ConflictResolutionArtifact) => ({
    hunk: value.hunk,
    strategy: value.strategy,
    result: value.result,
    structuredPatch: value.structuredPatch,
    markdownDiff: value.markdownDiff,
    provenanceSources: value.provenanceSources,
    previewSha256: value.previewSha256,
  });
  if (canonicalModelingJson(previewProjection(artifact))
    !== canonicalModelingJson(previewProjection(expected))) {
    throw new Error('冲突决定Artifact无法由可信revision fixture复算');
  }
}

test('五个来源必须严格依次读取，并在上一份文档审阅前阻止下一来源', async () => {
  const { runtime } = setup();
  let run = await createRun(runtime);
  assert.equal(run.revision, 0);
  assert.deepEqual(run.sources.map((source) => source.sourceId), ['mysql', 'github', 'core-docs', 'policy', 'semantica']);
  assert.deepEqual(run.sources.map((source) => source.status), ['PENDING', 'PENDING', 'PENDING', 'PENDING', 'PENDING']);

  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-mysql', runId: run.runId,
    expectedRevision: 0, actor,
  });
  assert.equal(run.status, 'READING_SOURCE');
  assert.equal(run.sources[0]?.status, 'READING');
  await assert.rejects(() => runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-too-early', runId: run.runId,
    expectedRevision: 1, actor,
  }), /当前来源文档尚未完成审阅/);
  await assert.rejects(() => completeCurrentSource(runtime, {
    runId: run.runId, sourceId: 'github', expectedRevision: 1, commandId: 'complete-wrong-source',
  }), /当前正在读取的来源不是 .*GitHub/);
  await assert.rejects(() => completeCurrentSource(runtime, {
    runId: run.runId, sourceId: 'mysql', expectedRevision: 0, commandId: 'complete-stale',
  }), /revision已变化/);
  assert.equal((await runtime.read(run.runId))?.revision, 1, '失败命令不能写入半状态');

  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: 'mysql', expectedRevision: 1, commandId: 'complete-mysql',
  });
  assert.equal(run.status, 'REVIEWING_DOCUMENT');
  assert.equal(run.sources[0]?.status, 'DOCUMENT_READY');
  await assert.rejects(() => runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-before-review', runId: run.runId,
    expectedRevision: 2, actor,
  }), /当前来源文档尚未完成审阅/);

  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-mysql', runId: run.runId,
    expectedRevision: 2, actor, sourceId: 'mysql',
  });
  assert.equal(run.status, 'READY');
  assert.equal(run.sources[0]?.status, 'ALIGNED');
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-github', runId: run.runId,
    expectedRevision: 3, actor,
  });
  assert.equal(run.sources[1]?.status, 'READING');

  for (let sourceIndex = 1; sourceIndex < fiveSources.length; sourceIndex += 1) {
    const source = fiveSources[sourceIndex]!;
    run = await completeCurrentSource(runtime, {
      runId: run.runId,
      sourceId: source.sourceId,
      expectedRevision: run.revision,
      commandId: `complete-${source.sourceId}`,
    });
    run = await runtime.execute({
      type: 'MARK_DOCUMENT_REVIEWED', commandId: `review-${source.sourceId}`, runId: run.runId,
      expectedRevision: run.revision, actor, sourceId: source.sourceId,
    });
    if (sourceIndex < fiveSources.length - 1) {
      assert.equal(run.status, 'READY');
      run = await runtime.execute({
        type: 'START_NEXT_SOURCE', commandId: `start-${fiveSources[sourceIndex + 1]!.sourceId}`,
        runId: run.runId, expectedRevision: run.revision, actor,
      });
    }
  }
  assert.equal(run.status, 'READY_FOR_OUTPUT');
  assert.equal(run.revision, 15);
  assert.deepEqual(run.sources.map((source) => source.status), ['ALIGNED', 'ALIGNED', 'ALIGNED', 'ALIGNED', 'ALIGNED']);
  assert.deepEqual(run.timeline.filter((event) => event.type === 'SOURCE_READ_STARTED').map((event) => event.sourceId),
    ['mysql', 'github', 'core-docs', 'policy', 'semantica']);
});

test('CREATE_RUN 保留本次运行所准入的来源快照身份', async () => {
  const { runtime } = setup();
  const run = await createRun(runtime, {
    sources: [
      { sourceId: 'mysql', sourceName: '管伊佳部署数据库', snapshotId: 'mysql-snapshot-a' },
      { sourceId: 'github', sourceName: '管伊佳 GitHub', snapshotId: 'github-snapshot-b' },
    ],
  });

  assert.deepEqual(run.sources.map((source) => source.snapshotId), [
    'mysql-snapshot-a', 'github-snapshot-b',
  ]);
});

test('来源读完后的事件严格为 STARTED→COMPLETED→DOCUMENT_GENERATED，正文仅按引用读取', async () => {
  const { runtime, metadataStorage } = setup();
  let run = await createRun(runtime, { sources: [fiveSources[0]!] });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-mysql', runId: run.runId,
    expectedRevision: 0, actor,
  });
  const largePayload = `# 九段式正文\n\n${'只应进入内容存储。'.repeat(2_000)}`;
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: 'mysql', expectedRevision: 1,
    commandId: 'complete-mysql', payload: largePayload,
  });

  assert.deepEqual(run.timeline.map((event) => event.type), [
    'SOURCE_READ_STARTED', 'SOURCE_READ_COMPLETED', 'DOCUMENT_GENERATED',
  ]);
  assert.ok(run.timeline.every((event) => event.payloadRef.startsWith('sha256:')));
  const generated = run.timeline[2]!;
  assert.equal(await runtime.readEventPayload(run.runId, generated.eventId), largePayload);
  const persistedMetadata = metadataStorage.raw('linguan:standardization-runs:v1') ?? '';
  assert.doesNotMatch(persistedMetadata, /只应进入内容存储/);
  assert.match(persistedMetadata, /"payloadRef":"sha256:/);
});

test('DOCUMENT_REVISED只在文档审阅态替换当前revision和冲突并保存轻量diff摘要', async () => {
  const { runtime } = setup();
  let run = await createRun(runtime, { sources: [fiveSources[0]!] });
  await assert.rejects(() => runtime.execute({
    type: 'REVISE_SOURCE_DOCUMENT', commandId: 'revise:too-early', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: 'mysql', documentId: 'document-mysql-r2',
    documentRevision: 2, introducedConflictIds: ['conflict-new'],
    diffSummary: { changedBlockIds: ['block-1'], changedSections: ['METRIC'], affectedObjectIds: ['RULE:negative_stock'] },
  }), /当前没有待审阅的来源文档/);
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'revise:prepare:start', runId: run.runId,
    expectedRevision: run.revision, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: 'mysql', expectedRevision: run.revision,
    commandId: 'revise:prepare:complete', conflicts: ['conflict-old'],
  });
  const command = {
    type: 'REVISE_SOURCE_DOCUMENT' as const,
    commandId: 'revise:mysql:block-1', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: 'mysql',
    documentId: 'document-mysql-r2', documentRevision: 2,
    introducedConflictIds: ['conflict-new'],
    diffSummary: {
      changedBlockIds: ['block-1'],
      changedSections: ['METRIC'],
      affectedObjectIds: ['RULE:negative_stock'],
    },
  };

  const revised = await runtime.execute(command);

  assert.equal(revised.status, 'REVIEWING_DOCUMENT');
  assert.equal(revised.sources[0]?.status, 'DOCUMENT_READY');
  assert.equal(revised.sources[0]?.documentId, 'document-mysql-r2');
  assert.equal(revised.sources[0]?.documentRevision, 2);
  assert.deepEqual(revised.sources[0]?.introducedConflictIds, ['conflict-new']);
  assert.deepEqual(revised.sources[0]?.resolvedConflictIds, []);
  assert.deepEqual(revised.timeline.slice(-2).map((event) => event.type), [
    'DOCUMENT_REVISED', 'CONFLICT_FOUND',
  ]);
  const payload = await runtime.readEventPayload(revised.runId, revised.timeline.at(-2)!.eventId);
  assert.deepEqual(JSON.parse(payload), {
    beforeDocumentId: 'document-mysql', beforeDocumentRevision: 1,
    documentId: 'document-mysql-r2', documentRevision: 2,
    changedBlockIds: ['block-1'], changedSections: ['METRIC'],
    affectedObjectIds: ['RULE:negative_stock'], affectedConflictIds: ['conflict-new'],
  });
  assert.equal(payload.length < 1024, true);
  assert.doesNotMatch(payload, /## |markdown|所有租户/iu);

  const repeated = await runtime.execute(command);
  assert.equal(repeated.revision, revised.revision);
  assert.equal(repeated.timeline.length, revised.timeline.length);
  await assert.rejects(() => runtime.execute({
    ...command, commandId: 'revise:mysql:stale', expectedRevision: command.expectedRevision,
    documentId: 'document-mysql-r3', documentRevision: 3,
  }), /revision已变化/);
  assert.equal((await runtime.read(revised.runId))?.sources[0]?.documentId, 'document-mysql-r2');
});

test('normalized-only修订允许changedSections为空且仍登记新revision', async () => {
  const { runtime } = setup();
  let run = await createRun(runtime, { sources: [fiveSources[0]!] });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'normalized-only:start', runId: run.runId,
    expectedRevision: run.revision, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: 'mysql', expectedRevision: run.revision,
    commandId: 'normalized-only:complete',
  });

  const revised = await runtime.execute({
    type: 'REVISE_SOURCE_DOCUMENT', commandId: 'normalized-only:revise', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: 'mysql',
    documentId: 'document-mysql-r2', documentRevision: 2, introducedConflictIds: [],
    diffSummary: {
      changedBlockIds: ['block-normalized-only'],
      changedSections: [],
      affectedObjectIds: ['RULE:negative_stock'],
    },
  });

  assert.equal(revised.sources[0]?.documentRevision, 2);
  assert.equal(revised.timeline.at(-1)?.type, 'DOCUMENT_REVISED');
  assert.deepEqual(JSON.parse(await runtime.readEventPayload(
    revised.runId, revised.timeline.at(-1)!.eventId,
  )).changedSections, []);
});

test('读取事件载荷时独立复算 sha256 并拒绝与 payloadRef 不一致的正文', async () => {
  const metadataStorage = new MemoryStorage();
  const backingStore = createMemoryContentStore();
  let tamperedRef: string | null = null;
  const uncheckedStore: ContentAddressedStore = {
    put: (content) => backingStore.put(content),
    get: async (ref) => ref === tamperedRef ? '被篡改的事件正文' : backingStore.get(ref),
  };
  const runtime = createStandardizationRunRuntime({ metadataStorage, contentStore: uncheckedStore });
  const created = await createRun(runtime, { sources: [fiveSources[0]!] });
  const started = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-for-tamper', runId: created.runId,
    expectedRevision: 0, actor,
  });
  tamperedRef = started.timeline[0]!.payloadRef;

  await assert.rejects(
    () => runtime.readEventPayload(started.runId, started.timeline[0]!.eventId),
    /标准化运行事件载荷校验和不一致/,
  );
});

test('已引入的正式差异必须在所属来源完成前逐项保存，随后才可读取下一来源', async () => {
  const { runtime } = setup();
  const policySource = { sourceId: 'guanyijia_demo_policy', sourceName: '管伊佳演示制度 Markdown' };
  const nextSource = { sourceId: 'guanyijia_semantica_demo', sourceName: '管伊佳演示制度术语图' };
  let run = await createRun(runtime, { sources: [policySource, nextSource] });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-mysql', runId: run.runId,
    expectedRevision: 0, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: policySource.sourceId, expectedRevision: 1, commandId: 'complete-policy',
    conflicts: ['gyj-conflict-negative-stock', 'gyj-conflict-status-nine'],
  });
  await assert.rejects(() => runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-before-saving-conflicts', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: policySource.sourceId,
  }), /请先保存当前来源的正式差异/u);
  await assert.rejects(() => runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-before-saving-conflicts', runId: run.runId,
    expectedRevision: run.revision, actor,
  }), /请先保存当前来源的正式差异/u);
  const negative = resolutionArtifactPayload({
    runId: run.runId, sourceId: policySource.sourceId, conflictId: 'gyj-conflict-negative-stock',
    strategy: 'MERGE', reason: '保留当前实现并登记制度落地缺口',
  });
  const statusFirst = resolutionArtifactPayload({
    runId: run.runId, sourceId: policySource.sourceId, conflictId: 'gyj-conflict-status-nine',
    strategy: 'DEFER_AS_GAP', reason: '不应越过第一项。',
  });
  await assert.rejects(() => runtime.execute({
    type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'resolve-status-out-of-order', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: policySource.sourceId, conflictId: 'gyj-conflict-status-nine',
    strategy: 'DEFER_AS_GAP', reason: statusFirst.artifact.reason,
    expectedHunkSha256: statusFirst.artifact.hunk.hunkSha256, payload: statusFirst.payload,
  }), /必须按顺序解决第一项未决冲突/);
  run = await runtime.execute({
    type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'resolve-negative', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: policySource.sourceId, conflictId: 'gyj-conflict-negative-stock',
    strategy: 'MERGE', reason: negative.artifact.reason,
    expectedHunkSha256: negative.artifact.hunk.hunkSha256, payload: negative.payload,
  });
  assert.equal(run.status, 'REVIEWING_DOCUMENT');
  assert.equal(run.sources[0]?.status, 'DOCUMENT_READY');
  assert.deepEqual(run.sources[0]?.resolvedConflictIds, ['gyj-conflict-negative-stock']);
  assert.equal(run.timeline.at(-1)?.type, 'CONFLICT_RESOLVED');
  const stored = JSON.parse(await runtime.readEventPayload(run.runId, run.timeline.at(-1)!.eventId));
  assert.equal(stored.structuredPatch.assertionOperations.length > 0, true);
  assert.ok(stored.result.assertions.every((assertion: { provenance: string }) => assertion.provenance === 'USER_CONFIRMED'));
  assert.ok(stored.markdownDiff.some((line: { type: string }) => line.type === 'REMOVED'));
  assert.ok(stored.markdownDiff.some((line: { type: string }) => line.type === 'ADDED'));
  assert.deepEqual(stored.provenanceSources.map((source: { role: string }) => source.role).slice(0, 2), ['CURRENT', 'INCOMING']);

  const status = resolutionArtifactPayload({
    runId: run.runId, sourceId: policySource.sourceId, conflictId: 'gyj-conflict-status-nine',
    strategy: 'DEFER_AS_GAP', reason: '状态九尚缺正式业务确认',
  });
  run = await runtime.execute({
    type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'resolve-status', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: policySource.sourceId, conflictId: 'gyj-conflict-status-nine',
    strategy: 'DEFER_AS_GAP', reason: status.artifact.reason,
    expectedHunkSha256: status.artifact.hunk.hunkSha256, payload: status.payload,
  });
  assert.equal(run.status, 'REVIEWING_DOCUMENT');
  assert.equal(run.sources[0]?.status, 'DOCUMENT_READY');

  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-after-saving-conflicts', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: policySource.sourceId,
  });
  assert.equal(run.status, 'READY');
  assert.equal(run.sources[0]?.status, 'ALIGNED');

  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-after-saving-conflicts', runId: run.runId,
    expectedRevision: run.revision, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: nextSource.sourceId, expectedRevision: run.revision,
    commandId: 'complete-semantica-after-saving-conflicts',
  });
  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-semantica-after-saving-conflicts',
    runId: run.runId, expectedRevision: run.revision, actor, sourceId: nextSource.sourceId,
  });
  assert.equal(run.status, 'READY_FOR_OUTPUT');
  assert.equal(run.sources[1]?.status, 'ALIGNED');
});

test('来源文档就绪时即可保存第一项差异，且保存后仍待审阅并锁定当前 revision', async () => {
  const { runtime } = setup();
  const mysqlSource = { sourceId: 'guanyijia_mysql', sourceName: '管伊佳部署数据库' };
  const githubSource = { sourceId: 'guanyijia_github', sourceName: '管伊佳 GitHub' };
  let run = await createRun(runtime, { sources: [mysqlSource, githubSource] });

  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'early-decision:start:mysql', runId: run.runId,
    expectedRevision: run.revision, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: mysqlSource.sourceId, expectedRevision: run.revision,
    commandId: 'early-decision:complete:mysql',
  });
  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'early-decision:review:mysql', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: mysqlSource.sourceId,
  });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'early-decision:start:github', runId: run.runId,
    expectedRevision: run.revision, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: githubSource.sourceId, expectedRevision: run.revision,
    commandId: 'early-decision:complete:github', conflicts: ['gyj-conflict-debt-schema'],
  });

  assert.equal(run.status, 'REVIEWING_DOCUMENT');
  assert.equal(run.sources[1]?.status, 'DOCUMENT_READY');
  assert.deepEqual(run.timeline.slice(-2).map((event) => event.type), [
    'DOCUMENT_GENERATED', 'CONFLICT_FOUND',
  ], '文档就绪即登记已引入差异，不能等到完成审阅后才发现');

  const debt = resolutionArtifactPayload({
    runId: run.runId,
    sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT',
    reason: '部署结构仍是本次审阅的当前工作标准。',
  });
  run = await runtime.execute({
    type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'early-decision:resolve:debt', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT', reason: debt.artifact.reason,
    expectedHunkSha256: debt.artifact.hunk.hunkSha256, payload: debt.payload,
  });

  assert.equal(run.status, 'REVIEWING_DOCUMENT');
  assert.equal(run.sources[1]?.status, 'DOCUMENT_READY');
  assert.deepEqual(run.sources[1]?.resolvedConflictIds, ['gyj-conflict-debt-schema']);
  await assert.rejects(() => runtime.execute({
    type: 'REVISE_SOURCE_DOCUMENT', commandId: 'early-decision:rewrite:github', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: githubSource.sourceId,
    documentId: 'document-guanyijia-github-r2', documentRevision: 2,
    introducedConflictIds: ['gyj-conflict-debt-schema'],
    diffSummary: { changedBlockIds: ['block-1'], changedSections: ['METRIC'], affectedObjectIds: ['RULE:debt'] },
    payload: '# 不允许在正式决定后改写的文档',
  }), /已保存来源差异后不能修改来源文档/u);

  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'early-decision:review:github', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: githubSource.sourceId,
  });
  assert.equal(run.status, 'READY_FOR_OUTPUT');
  assert.equal(run.sources[1]?.status, 'ALIGNED');
});

test('定版前可以以新的不可变决定取代已保存差异，且原决定保持在时间线中', async () => {
  const { runtime } = setup();
  const githubSource = { sourceId: 'guanyijia_github', sourceName: 'jshERP 源码' };
  const blocked = await prepareSingleSource(runtime, ['gyj-conflict-debt-schema'], githubSource);
  const first = resolutionArtifactPayload({
    runId: blocked.runId,
    sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT',
    reason: '先以当前部署结构为准。',
  });
  const aligned = await runtime.execute({
    type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'resolve-debt-first', runId: blocked.runId,
    expectedRevision: blocked.revision, actor, sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT', reason: first.artifact.reason,
    expectedHunkSha256: first.artifact.hunk.hunkSha256, payload: first.payload,
  });
  assert.equal(aligned.status, 'REVIEWING_DOCUMENT');
  const reviewed = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-after-first-debt-decision', runId: aligned.runId,
    expectedRevision: aligned.revision, actor, sourceId: githubSource.sourceId,
  });
  assert.equal(reviewed.status, 'READY_FOR_OUTPUT');

  const replacement = resolutionArtifactPayload({
    runId: reviewed.runId,
    sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'DEFER_AS_GAP',
    reason: '改为登记缺口，等待部署结构确认。',
  });
  replacement.artifact.resolutionId = `replacement:${reviewed.runId}:gyj-conflict-debt-schema:${reviewed.timeline.length + 1}`;
  const replaced = await runtime.execute({
    type: 'REPLACE_SOURCE_CONFLICT_DECISION', commandId: 'replace-debt-decision', runId: reviewed.runId,
    expectedRevision: reviewed.revision, actor, sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema', strategy: 'DEFER_AS_GAP', reason: replacement.artifact.reason,
    expectedHunkSha256: replacement.artifact.hunk.hunkSha256, payload: JSON.stringify(replacement.artifact),
  });

  assert.equal(replaced.status, 'READY_FOR_OUTPUT');
  assert.deepEqual(replaced.sources[0]?.resolvedConflictIds, ['gyj-conflict-debt-schema']);
  assert.deepEqual(replaced.timeline.slice(-2).map((event) => event.type), [
    'DOCUMENT_REVIEWED',
    'CONFLICT_DECISION_REPLACED',
  ]);
  const saved = JSON.parse(await runtime.readEventPayload(replaced.runId, replaced.timeline.at(-1)!.eventId));
  assert.equal(saved.strategy, 'DEFER_AS_GAP');
  assert.equal(saved.resolutionId, replacement.artifact.resolutionId);
});

test('已生成但尚未定版的结果会在重新处理差异后保留为历史，并要求重新生成', async () => {
  const { runtime } = setup();
  const githubSource = { sourceId: 'guanyijia_github', sourceName: 'jshERP 源码' };
  const blocked = await prepareSingleSource(runtime, ['gyj-conflict-debt-schema'], githubSource);
  const first = resolutionArtifactPayload({
    runId: blocked.runId,
    sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT',
    reason: '先以当前部署结构为准。',
  });
  const aligned = await runtime.execute({
    type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'resolve-debt-before-generated-result', runId: blocked.runId,
    expectedRevision: blocked.revision, actor, sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT', reason: first.artifact.reason,
    expectedHunkSha256: first.artifact.hunk.hunkSha256, payload: first.payload,
  });
  const reviewed = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-before-generated-result', runId: aligned.runId,
    expectedRevision: aligned.revision, actor, sourceId: githubSource.sourceId,
  });
  const generated = await runtime.execute({
    type: 'MARK_DELIVERABLE_GENERATED', commandId: 'generate-before-reprocess', runId: reviewed.runId,
    expectedRevision: reviewed.revision, actor, deliverableId: 'deliverable-before-reprocess',
    deliveryCapability, payload: deliveryGeneratedPayload('deliverable-before-reprocess'),
  });
  const replacement = resolutionArtifactPayload({
    runId: generated.runId,
    sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'DEFER_AS_GAP',
    reason: '改为登记缺口，等待部署结构确认。',
  });
  replacement.artifact.resolutionId = `replacement:${generated.runId}:gyj-conflict-debt-schema:${generated.timeline.length + 2}`;
  const reprocessed = await runtime.execute({
    type: 'REPLACE_SOURCE_CONFLICT_DECISION', commandId: 'replace-after-generated-result', runId: generated.runId,
    expectedRevision: generated.revision, actor, sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema', strategy: 'DEFER_AS_GAP', reason: replacement.artifact.reason,
    expectedHunkSha256: replacement.artifact.hunk.hunkSha256, payload: JSON.stringify(replacement.artifact),
  });

  assert.equal(reprocessed.deliverableId, undefined);
  assert.deepEqual(reprocessed.supersededDeliverableIds, ['deliverable-before-reprocess']);
  assert.deepEqual(reprocessed.timeline.slice(-2).map((event) => event.type), [
    'DELIVERABLE_SUPERSEDED',
    'CONFLICT_DECISION_REPLACED',
  ]);
  const regenerated = await runtime.execute({
    type: 'MARK_DELIVERABLE_GENERATED', commandId: 'regenerate-after-reprocess', runId: reprocessed.runId,
    expectedRevision: reprocessed.revision, actor, deliverableId: 'deliverable-after-reprocess',
    deliveryCapability, payload: deliveryGeneratedPayload('deliverable-after-reprocess'),
  });
  assert.equal(regenerated.deliverableId, 'deliverable-after-reprocess');
  assert.deepEqual(regenerated.supersededDeliverableIds, ['deliverable-before-reprocess']);
  assert.equal((await runtime.read(regenerated.runId))?.timeline.filter((event) => event.type === 'DELIVERABLE_GENERATED').length, 2);
});

test('单项决定拒绝未知错源重复空理由陈旧Hunk与重算SHA后的Assertion Diff provenance篡改', async () => {
  const { runtime } = setup();
  const githubSource = { sourceId: 'guanyijia_github', sourceName: 'jshERP 源码' };
  const blocked = await prepareSingleSource(runtime, ['gyj-conflict-debt-schema'], githubSource);
  const valid = resolutionArtifactPayload({
    runId: blocked.runId, sourceId: githubSource.sourceId, conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT', reason: '部署事实优先，源码差异仅保留溯源。',
  });
  const baseCommand = {
    type: 'RESOLVE_SOURCE_CONFLICT' as const,
    runId: blocked.runId,
    expectedRevision: blocked.revision,
    actor,
    sourceId: githubSource.sourceId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT' as const,
    reason: valid.artifact.reason,
    expectedHunkSha256: valid.artifact.hunk.hunkSha256,
    payload: valid.payload,
  };
  await assert.rejects(() => runtime.execute({
    ...baseCommand, commandId: 'reject:unknown', conflictId: 'unknown-conflict',
  }), /不属于当前来源/);
  await assert.rejects(() => runtime.execute({
    ...baseCommand, commandId: 'reject:wrong-source', sourceId: 'github',
  }), /当前冲突来源不是/);
  await assert.rejects(() => runtime.execute({
    ...baseCommand, commandId: 'reject:empty-reason', reason: '  ',
  }), /必须填写中文理由/);
  await assert.rejects(() => runtime.execute({
    ...baseCommand, commandId: 'reject:stale-hunk', expectedHunkSha256: 'sha256:stale',
  }), /Hunk已变化/);

  const mutate = async (
    commandId: string,
    change: (artifact: ConflictResolutionArtifact) => void,
    error: RegExp,
  ) => {
    const artifact = structuredClone(valid.artifact);
    change(artifact);
    await assert.rejects(() => runtime.execute({
      ...baseCommand, commandId, payload: rehashArtifactPreview(artifact),
    }), error);
  };
  await mutate('reject:assertion', (artifact) => {
    artifact.result.assertions[0]!.statement = '伪造断言';
    artifact.structuredPatch.assertionOperations[0]!.assertion.statement = '伪造断言';
  }, /assertion不是result blocks的唯一投影/);
  await mutate('reject:assertion-id', (artifact) => {
    artifact.result.assertions[0]!.assertionId = 'attacker-controlled-id';
    artifact.structuredPatch.assertionOperations[0]!.assertion.assertionId = 'attacker-controlled-id';
  }, /Story纯投影/);
  const forgedHunkArtifact = structuredClone(valid.artifact);
  forgedHunkArtifact.hunk.current.block.label = '攻击者伪造的部署事实';
  const forgedHunkContent = {
    conflictId: forgedHunkArtifact.hunk.conflictId,
    title: forgedHunkArtifact.hunk.title,
    sections: forgedHunkArtifact.hunk.sections,
    current: forgedHunkArtifact.hunk.current,
    incoming: forgedHunkArtifact.hunk.incoming,
    corroborating: forgedHunkArtifact.hunk.corroborating,
    affectedObjectRefs: forgedHunkArtifact.hunk.affectedObjectRefs,
  };
  forgedHunkArtifact.hunk.hunkSha256 = `sha256:${sha256HexSync(canonicalModelingJson(forgedHunkContent))}`;
  Object.assign(forgedHunkArtifact, projectConflictResolution({
    hunk: forgedHunkArtifact.hunk,
    strategy: forgedHunkArtifact.strategy,
  }));
  await assert.rejects(() => runtime.execute({
    ...baseCommand,
    commandId: 'reject:forged-hunk-full-chain',
    expectedHunkSha256: forgedHunkArtifact.hunk.hunkSha256,
    payload: JSON.stringify(forgedHunkArtifact),
  }), /可信revision fixture复算/);
  await mutate('reject:diff', (artifact) => {
    artifact.markdownDiff.find((line) => line.type === 'ADDED')!.line = '+ 伪造Diff';
  }, /markdownDiff不是Hunk与Result的真实行级差异/);
  await mutate('reject:provenance', (artifact) => {
    artifact.provenanceSources[0]!.usage = 'PROVENANCE_ONLY';
  }, /provenanceSources不是Hunk与策略的唯一投影/);
  await assert.rejects(() => runtime.execute({
    ...baseCommand, commandId: 'reject:preview-sha',
    payload: JSON.stringify({ ...valid.artifact, previewSha256: 'sha256:tampered' }),
  }), /previewSha256不一致/);
  assert.equal((await runtime.read(blocked.runId))?.revision, blocked.revision);

  const resolved = await runtime.execute({ ...baseCommand, commandId: 'resolve:valid' });
  assert.equal(resolved.status, 'REVIEWING_DOCUMENT');
  await assert.rejects(() => runtime.execute({
    ...baseCommand, commandId: 'resolve:again', expectedRevision: resolved.revision,
  }), /当前没有待解决的来源冲突|已经解决/);
  await assert.rejects(() => runtime.execute({
    ...baseCommand, commandId: 'resolve:valid', reason: '改变理由', expectedRevision: blocked.revision,
  }), /commandId已用于不同命令/);
  const reviewed = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-after-valid-resolution', runId: resolved.runId,
    expectedRevision: resolved.revision, actor, sourceId: githubSource.sourceId,
  });
  assert.equal(reviewed.status, 'READY_FOR_OUTPUT');
  assert.equal((await runtime.read(blocked.runId))?.revision, reviewed.revision);
});

test('两个Runtime同revision并发决定同一项时只有一个CAS成功且事件投影不被覆盖', async () => {
  const backend = new FakeAtomicMetadataBackend();
  const contentStore = createMemoryContentStore();
  const firstRuntime = createStandardizationRunRuntime({
    metadataStore: backend.createStore(), contentStore,
    resolutionArtifactValidator: validateTrustedResolutionArtifact,
    now: () => '2026-08-17T12:00:00.000Z',
  });
  const secondRuntime = createStandardizationRunRuntime({
    metadataStore: backend.createStore(), contentStore,
    resolutionArtifactValidator: validateTrustedResolutionArtifact,
    now: () => '2026-08-17T12:00:00.000Z',
  });
  const githubSource = { sourceId: 'guanyijia_github', sourceName: 'jshERP 源码' };
  const blocked = await prepareSingleSource(firstRuntime, ['gyj-conflict-debt-schema'], githubSource);
  const keep = resolutionArtifactPayload({
    runId: blocked.runId, sourceId: githubSource.sourceId, conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT', reason: '部署事实优先。',
  });
  const accept = resolutionArtifactPayload({
    runId: blocked.runId, sourceId: githubSource.sourceId, conflictId: 'gyj-conflict-debt-schema',
    strategy: 'ACCEPT_INCOMING', reason: '源码结构优先。',
  });
  backend.synchronizeNextReads(2);
  const outcomes = await Promise.allSettled([
    firstRuntime.execute({
      type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'concurrent:keep', runId: blocked.runId,
      expectedRevision: blocked.revision, actor, sourceId: githubSource.sourceId,
      conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT', reason: keep.artifact.reason,
      expectedHunkSha256: keep.artifact.hunk.hunkSha256, payload: keep.payload,
    }),
    secondRuntime.execute({
      type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'concurrent:accept', runId: blocked.runId,
      expectedRevision: blocked.revision, actor, sourceId: githubSource.sourceId,
      conflictId: 'gyj-conflict-debt-schema', strategy: 'ACCEPT_INCOMING', reason: accept.artifact.reason,
      expectedHunkSha256: accept.artifact.hunk.hunkSha256, payload: accept.payload,
    }),
  ]);

  assert.equal(outcomes.filter((result) => result.status === 'fulfilled').length, 1);
  assert.equal(outcomes.filter((result) => result.status === 'rejected').length, 1);
  assert.match(String((outcomes.find((result) => result.status === 'rejected') as PromiseRejectedResult).reason),
    /运行已被其他窗口更新/);
  const saved = await firstRuntime.read(blocked.runId);
  assert.equal(saved?.revision, blocked.revision + 1);
  assert.equal(saved?.timeline.filter((event) => event.type === 'CONFLICT_RESOLVED').length, 1);
  assert.deepEqual(saved?.sources[0]?.resolvedConflictIds, ['gyj-conflict-debt-schema']);
});

test('含佐证的完成命令在缺少集合validator时必须于CAS前拒绝', async () => {
  const metadataStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const runtime = createStandardizationRunRuntime({
    metadataStorage,
    contentStore,
    resolutionArtifactValidator: validateTrustedResolutionArtifact,
    corroborationReceiptValidator: () => undefined,
    now: () => '2026-08-17T12:00:00.000Z',
  });
  const sources = [
    { sourceId: 'guanyijia_github', sourceName: 'jshERP 源码' },
    { sourceId: 'guanyijia_semantica_demo', sourceName: '派生 Semantica' },
  ];
  let run = await createRun(runtime, { sources });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'validator:start:github', runId: run.runId,
    expectedRevision: run.revision, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId,
    sourceId: 'guanyijia_github',
    expectedRevision: run.revision,
    commandId: 'validator:complete:github',
    conflicts: ['gyj-conflict-debt-schema'],
  });
  const resolution = resolutionArtifactPayload({
    runId: run.runId,
    sourceId: 'guanyijia_github',
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT',
    reason: '部署事实优先。',
  });
  run = await runtime.execute({
    type: 'RESOLVE_SOURCE_CONFLICT', commandId: 'validator:resolve:debt', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: 'guanyijia_github',
    conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT', reason: resolution.artifact.reason,
    expectedHunkSha256: resolution.artifact.hunk.hunkSha256, payload: resolution.payload,
  });
  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'validator:review:github', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: 'guanyijia_github',
  });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'validator:start:semantica', runId: run.runId,
    expectedRevision: run.revision, actor,
  });

  await assert.rejects(() => runtime.execute({
    type: 'COMPLETE_SOURCE_DOCUMENT', commandId: 'validator:complete:semantica', runId: run.runId,
    expectedRevision: run.revision, actor, sourceId: 'guanyijia_semantica_demo',
    readSummary: { summary: '派生佐证已读取', objectCount: 2, evidenceCount: 2 },
    documentId: 'document-semantica', documentRevision: 1,
    introducedConflictIds: [], corroboratedConflictIds: ['gyj-conflict-debt-schema'],
    payload: '# Semantica',
  }), /Runtime未配置Story佐证集合校验器/);
  assert.equal((await runtime.read(run.runId))?.revision, run.revision);
});

test('重复 commandId 与重复 projectId+batchId 都不会新增运行、revision或事件', async () => {
  const { runtime } = setup();
  const created = await createRun(runtime);
  const repeatedCommand = await createRun(runtime);
  const repeatedBatch = await createRun(runtime, { commandId: 'create-same-batch-again' });
  assert.equal(repeatedCommand.runId, created.runId);
  assert.equal(repeatedBatch.runId, created.runId);
  assert.equal(repeatedBatch.revision, 0);
  assert.equal(repeatedBatch.timeline.length, 0);

  const started = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-mysql', runId: created.runId,
    expectedRevision: 0, actor,
  });
  const repeatedStart = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-mysql', runId: created.runId,
    expectedRevision: 0, actor,
  });
  assert.equal(repeatedStart.revision, started.revision);
  assert.equal(repeatedStart.timeline.length, 1);
});

test('同一运行复用 commandId 但改变命令内容时拒绝伪幂等', async () => {
  const { runtime } = setup();
  const created = await createRun(runtime, { sources: [fiveSources[0]!] });
  const started = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'reused-command', runId: created.runId,
    expectedRevision: 0, actor,
  });
  await assert.rejects(() => completeCurrentSource(runtime, {
    runId: created.runId, sourceId: 'mysql', expectedRevision: started.revision,
    commandId: 'reused-command', payload: '# 不同命令内容',
  }), /commandId已用于不同命令/);
  assert.equal((await runtime.read(created.runId))?.revision, 1);
});

test('CREATE_RUN 跨项目复用 commandId 时拒绝返回错误运行', async () => {
  const { runtime } = setup();
  await createRun(runtime, {
    commandId: 'shared-create-id', projectId: 'project-a', batchId: 'batch-a', sources: [fiveSources[0]!],
  });
  await assert.rejects(() => createRun(runtime, {
    commandId: 'shared-create-id', projectId: 'project-b', batchId: 'batch-b', sources: [fiveSources[1]!],
  }), /commandId已用于不同命令/);
  assert.equal(await runtime.read('standardization-run-2'), null);
});

for (const prototypeCommandId of ['toString', 'constructor', '__proto__']) {
  test(`commandId=${prototypeCommandId} 作为自有键执行并保持精确幂等`, async () => {
    const { runtime } = setup();
    const created = await createRun(runtime, {
      commandId: prototypeCommandId,
      projectId: `project-${prototypeCommandId}`,
      batchId: `batch-${prototypeCommandId}`,
      sources: [fiveSources[0]!],
    });
    const repeated = await createRun(runtime, {
      commandId: prototypeCommandId,
      projectId: `project-${prototypeCommandId}`,
      batchId: `batch-${prototypeCommandId}`,
      sources: [fiveSources[0]!],
    });
    assert.equal(created.runId, 'standardization-run-1');
    assert.equal(repeated.runId, created.runId);
    assert.equal(repeated.revision, 0);
  });
}

test('旧版 string commandId 记录可读取但不能在缺少指纹时伪装幂等', async () => {
  const { runtime, metadataStorage, contentStore } = setup();
  const created = await createRun(runtime, { sources: [fiveSources[0]!] });
  const raw = metadataStorage.raw('linguan:standardization-runs:v1');
  assert.ok(raw);
  const legacy = JSON.parse(raw) as { commandRunIds: Record<string, unknown> };
  legacy.commandRunIds['legacy-start'] = created.runId;
  metadataStorage.setItem('linguan:standardization-runs:v1', JSON.stringify(legacy));
  const migratedRuntime = createStandardizationRunRuntime({ metadataStorage, contentStore });

  await assert.rejects(() => migratedRuntime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'legacy-start', runId: created.runId,
    expectedRevision: 0, actor,
  }), /旧版commandId记录缺少指纹/);
  assert.equal((await migratedRuntime.read(created.runId))?.revision, 0);
});

test('不可解析的运行元数据必须报错并保留原字节', async () => {
  const { runtime, metadataStorage } = setup();
  const brokenBytes = '{"schemaVersion":1,"runs":[';
  metadataStorage.setItem('linguan:standardization-runs:v1', brokenBytes);

  await assert.rejects(() => runtime.read('missing-run'), /标准化运行元数据已损坏：无法解析JSON/);
  await assert.rejects(() => createRun(runtime), /标准化运行元数据已损坏：无法解析JSON/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), brokenBytes);
});

test('不支持的元数据 schemaVersion 必须拒绝而不是降级为空状态', async () => {
  const { runtime, metadataStorage } = setup();
  const unsupported = JSON.stringify({ schemaVersion: 2, sequence: 0, runs: [], commandRunIds: {} });
  metadataStorage.setItem('linguan:standardization-runs:v1', unsupported);

  await assert.rejects(() => runtime.read('missing-run'), /标准化运行元数据已损坏：schemaVersion必须为1/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), unsupported);
});

test('合法 JSON 中的重复 runId 必须作为损坏状态拒绝', async () => {
  const { runtime, metadataStorage } = setup();
  await createRun(runtime, {
    commandId: 'create-a', projectId: 'project-a', batchId: 'batch-a', sources: [fiveSources[0]!],
  });
  await createRun(runtime, {
    commandId: 'create-b', projectId: 'project-b', batchId: 'batch-b', sources: [fiveSources[1]!],
  });
  const raw = metadataStorage.raw('linguan:standardization-runs:v1');
  assert.ok(raw);
  const duplicate = JSON.parse(raw) as { runs: Array<{ runId: string }> };
  duplicate.runs[1]!.runId = duplicate.runs[0]!.runId;
  const duplicateBytes = JSON.stringify(duplicate);
  metadataStorage.setItem('linguan:standardization-runs:v1', duplicateBytes);

  await assert.rejects(() => runtime.read('standardization-run-1'), /标准化运行元数据已损坏：runId重复/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), duplicateBytes);
});

test('sequence 小于既有运行编号时必须拒绝创建以免生成重复 runId', async () => {
  const { runtime, metadataStorage } = setup();
  await createRun(runtime, { sources: [fiveSources[0]!] });
  const raw = metadataStorage.raw('linguan:standardization-runs:v1');
  assert.ok(raw);
  const staleSequence = JSON.parse(raw) as { sequence: number };
  staleSequence.sequence = 0;
  const staleBytes = JSON.stringify(staleSequence);
  metadataStorage.setItem('linguan:standardization-runs:v1', staleBytes);

  await assert.rejects(() => createRun(runtime, {
    commandId: 'create-second', projectId: 'project-b', batchId: 'batch-b', sources: [fiveSources[1]!],
  }), /标准化运行元数据已损坏：sequence小于既有运行编号/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), staleBytes);
});

const brokenSourceScenarios: Array<{
  name: string;
  mutate(state: { runs: Array<{ status: string; sources: Array<Record<string, unknown> | null> }> }): void;
  error: RegExp;
}> = [
  {
    name: 'null 来源步骤',
    mutate: (state) => { state.runs[0]!.sources = [null]; },
    error: /来源步骤必须是对象/,
  },
  {
    name: '非法运行状态',
    mutate: (state) => { state.runs[0]!.status = 'BROKEN'; },
    error: /运行status无效/,
  },
  {
    name: '重复来源身份',
    mutate: (state) => {
      state.runs[0]!.sources[1]!.sourceId = state.runs[0]!.sources[0]!.sourceId;
    },
    error: /sourceId重复/,
  },
  {
    name: '不连续来源顺序',
    mutate: (state) => { state.runs[0]!.sources[0]!.order = 2; },
    error: /order必须从1连续递增/,
  },
  {
    name: '非法来源状态',
    mutate: (state) => { state.runs[0]!.sources[0]!.status = 'BROKEN'; },
    error: /来源status无效/,
  },
  {
    name: '破损冲突数组',
    mutate: (state) => { state.runs[0]!.sources[0]!.introducedConflictIds = null; },
    error: /introducedConflictIds必须是数组/,
  },
  {
    name: '不完整文档字段',
    mutate: (state) => { state.runs[0]!.sources[0]!.documentRevision = 1; },
    error: /文档字段必须同时存在/,
  },
];

for (const scenario of brokenSourceScenarios) {
  test(`嵌套元数据拒绝${scenario.name}并保留原字节`, async () => {
    const { runtime, metadataStorage } = setup();
    await createRun(runtime, { sources: fiveSources.slice(0, 2) });
    const raw = metadataStorage.raw('linguan:standardization-runs:v1');
    assert.ok(raw);
    const state = JSON.parse(raw) as Parameters<typeof scenario.mutate>[0];
    scenario.mutate(state);
    const brokenBytes = JSON.stringify(state);
    metadataStorage.setItem('linguan:standardization-runs:v1', brokenBytes);

    await assert.rejects(() => runtime.read('standardization-run-1'), scenario.error);
    assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), brokenBytes);
  });
}

const brokenTimelineScenarios: Array<{
  name: string;
  mutate(state: { runs: Array<{ timeline: Array<Record<string, unknown> | null> }> }): void;
  error: RegExp;
}> = [
  {
    name: 'null 时间线事件',
    mutate: (state) => { state.runs[0]!.timeline = [null]; },
    error: /时间线事件必须是对象/,
  },
  {
    name: '不连续事件序号',
    mutate: (state) => { state.runs[0]!.timeline[0]!.sequence = 2; },
    error: /事件sequence必须从1连续递增/,
  },
  {
    name: '错误事件运行身份',
    mutate: (state) => { state.runs[0]!.timeline[0]!.runId = 'standardization-run-2'; },
    error: /事件runId与运行不一致/,
  },
  {
    name: '非法事件类型',
    mutate: (state) => { state.runs[0]!.timeline[0]!.type = 'BROKEN'; },
    error: /事件type无效/,
  },
  {
    name: 'null 事件操作者',
    mutate: (state) => { state.runs[0]!.timeline[0]!.actor = null; },
    error: /事件actor无效/,
  },
  {
    name: '非法 payloadRef',
    mutate: (state) => { state.runs[0]!.timeline[0]!.payloadRef = 'not-a-content-ref'; },
    error: /事件payloadRef无效/,
  },
  {
    name: '重复 eventId',
    mutate: (state) => {
      const duplicate = structuredClone(state.runs[0]!.timeline[0]!);
      duplicate.sequence = 2;
      state.runs[0]!.timeline.push(duplicate);
    },
    error: /eventId重复/,
  },
];

for (const scenario of brokenTimelineScenarios) {
  test(`嵌套元数据拒绝${scenario.name}并保留原字节`, async () => {
    const { runtime, metadataStorage } = setup();
    const created = await createRun(runtime, { sources: [fiveSources[0]!] });
    await runtime.execute({
      type: 'START_NEXT_SOURCE', commandId: 'start-for-timeline-corruption', runId: created.runId,
      expectedRevision: 0, actor,
    });
    const raw = metadataStorage.raw('linguan:standardization-runs:v1');
    assert.ok(raw);
    const state = JSON.parse(raw) as Parameters<typeof scenario.mutate>[0];
    scenario.mutate(state);
    const brokenBytes = JSON.stringify(state);
    metadataStorage.setItem('linguan:standardization-runs:v1', brokenBytes);

    await assert.rejects(() => runtime.read(created.runId), scenario.error);
    assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), brokenBytes);
  });
}

type MutableRunState = {
  runs: Array<{
    status: string;
    deliverableId?: string;
    reviewId?: string;
    sources: Array<{
      status: string;
      introducedConflictIds: string[];
      resolvedConflictIds: string[];
    }>;
  }>;
};

const brokenStateCombinationScenarios: Array<{
  name: string;
  arrange(runtime: ReturnType<typeof createStandardizationRunRuntime>): Promise<{ runId: string }>;
  mutate(state: MutableRunState): void;
  error: RegExp;
}> = [
  {
    name: 'READY 运行包含 READING 来源',
    arrange: (runtime) => createRun(runtime, { sources: [fiveSources[0]!] }),
    mutate: (state) => { state.runs[0]!.sources[0]!.status = 'READING'; },
    error: /READY与来源状态不一致/,
  },
  {
    name: 'READY_FOR_OUTPUT 仍包含 PENDING 来源',
    arrange: (runtime) => createRun(runtime, { sources: [fiveSources[0]!] }),
    mutate: (state) => { state.runs[0]!.status = 'READY_FOR_OUTPUT'; },
    error: /READY_FOR_OUTPUT仍有未对齐来源/,
  },
  {
    name: 'FROZEN 缺少交付物和定版确认',
    arrange: (runtime) => reviewSingleSource(runtime),
    mutate: (state) => { state.runs[0]!.status = 'FROZEN'; },
    error: /FROZEN缺少交付物或定版确认/,
  },
  {
    name: 'CONFLICT_BLOCKED 没有未解决冲突',
    arrange: (runtime) => prepareSingleSource(runtime, ['conflict-1']),
    mutate: (state) => {
      state.runs[0]!.status = 'CONFLICT_BLOCKED';
      state.runs[0]!.sources[0]!.status = 'CONFLICT_BLOCKED';
      state.runs[0]!.sources[0]!.resolvedConflictIds = ['conflict-1'];
    },
    error: /CONFLICT_BLOCKED没有未解决冲突/,
  },
  {
    name: 'ALIGNED 来源仍有未解决冲突',
    arrange: (runtime) => prepareSingleSource(runtime, ['conflict-1']),
    mutate: (state) => {
      state.runs[0]!.status = 'READY_FOR_OUTPUT';
      state.runs[0]!.sources[0]!.status = 'ALIGNED';
    },
    error: /ALIGNED仍有未解决冲突/,
  },
];

for (const scenario of brokenStateCombinationScenarios) {
  test(`状态组合校验拒绝${scenario.name}，read和execute均保留原字节`, async () => {
    const { runtime, metadataStorage } = setup();
    const prepared = await scenario.arrange(runtime);
    const raw = metadataStorage.raw('linguan:standardization-runs:v1');
    assert.ok(raw);
    const state = JSON.parse(raw) as MutableRunState;
    scenario.mutate(state);
    const brokenBytes = JSON.stringify(state);
    metadataStorage.setItem('linguan:standardization-runs:v1', brokenBytes);

    await assert.rejects(() => runtime.read(prepared.runId), scenario.error);
    await assert.rejects(() => createRun(runtime, {
      commandId: `execute-against-broken-${scenario.name}`,
      projectId: 'another-project', batchId: 'another-batch', sources: [fiveSources[1]!],
    }), scenario.error);
    assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), brokenBytes);
  });
}

test('来源状态拒绝 PENDING,ALIGNED,PENDING 破坏已对齐前缀', async () => {
  const { runtime, metadataStorage } = setup();
  const created = await createRun(runtime, { sources: fiveSources.slice(0, 3) });
  const raw = metadataStorage.raw('linguan:standardization-runs:v1');
  assert.ok(raw);
  const state = JSON.parse(raw) as {
    runs: Array<{ sources: Array<Record<string, unknown>> }>;
  };
  Object.assign(state.runs[0]!.sources[1]!, {
    status: 'ALIGNED',
    readSummary: { summary: '伪造的已对齐中间来源', objectCount: 1, evidenceCount: 1 },
    documentId: 'document-middle',
    documentRevision: 1,
  });
  const brokenBytes = JSON.stringify(state);
  metadataStorage.setItem('linguan:standardization-runs:v1', brokenBytes);

  await assert.rejects(() => runtime.read(created.runId), /来源状态必须保持ALIGNED前缀和PENDING后缀/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), brokenBytes);
});

test('READY 运行不能偷偷携带 deliverableId', async () => {
  const { runtime, metadataStorage } = setup();
  const created = await createRun(runtime, { sources: [fiveSources[0]!] });
  const raw = metadataStorage.raw('linguan:standardization-runs:v1');
  assert.ok(raw);
  const state = JSON.parse(raw) as { runs: Array<{ deliverableId?: string }> };
  state.runs[0]!.deliverableId = 'secret-deliverable';
  const brokenBytes = JSON.stringify(state);
  metadataStorage.setItem('linguan:standardization-runs:v1', brokenBytes);

  await assert.rejects(() => runtime.read(created.runId), /交付身份只能存在于全部来源已对齐的交付阶段/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), brokenBytes);
});

test('reviewId 缺少对应 REVIEW_SUBMITTED 事件时拒绝读取', async () => {
  const { runtime, metadataStorage } = setup();
  let run = await reviewSingleSource(runtime);
  run = await runtime.execute({
    type: 'MARK_DELIVERABLE_GENERATED', commandId: 'prepare-deliverable', runId: run.runId,
    expectedRevision: 3, actor, deliverableId: 'deliverable-1',
    deliveryCapability,
    payload: deliveryGeneratedPayload('deliverable-1'),
  });
  run = await runtime.execute({
    type: 'MARK_REVIEW_SUBMITTED', commandId: 'prepare-review-submission', runId: run.runId,
    expectedRevision: 4, actor, reviewId: 'review-1',
    deliveryCapability,
    payload: reviewSubmittedPayload('deliverable-1', 'review-1'),
  });
  const raw = metadataStorage.raw('linguan:standardization-runs:v1');
  assert.ok(raw);
  const state = JSON.parse(raw) as { runs: Array<{ timeline: unknown[] }> };
  state.runs[0]!.timeline.pop();
  const brokenBytes = JSON.stringify(state);
  metadataStorage.setItem('linguan:standardization-runs:v1', brokenBytes);

  await assert.rejects(() => runtime.read(run.runId), /reviewId缺少对应的REVIEW_SUBMITTED事件/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), brokenBytes);
});

test('交付事件 payloadRef 必须与当前 deliverableId 对应', async () => {
  const { runtime, metadataStorage } = setup();
  let run = await reviewSingleSource(runtime);
  run = await runtime.execute({
    type: 'MARK_DELIVERABLE_GENERATED', commandId: 'prepare-deliverable', runId: run.runId,
    expectedRevision: 3, actor, deliverableId: 'deliverable-1',
    deliveryCapability,
    payload: deliveryGeneratedPayload('deliverable-1'),
  });
  const raw = metadataStorage.raw('linguan:standardization-runs:v1');
  assert.ok(raw);
  const state = JSON.parse(raw) as { runs: Array<{ deliverableId: string }> };
  state.runs[0]!.deliverableId = 'tampered-deliverable';
  const brokenBytes = JSON.stringify(state);
  metadataStorage.setItem('linguan:standardization-runs:v1', brokenBytes);

  await assert.rejects(() => runtime.read(run.runId), /DELIVERABLE_GENERATED事件与deliverable内容身份不一致/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), brokenBytes);
});

test('公开Run Runtime不能绕过标准化交付深模块直接伪造交付事件', async () => {
  const metadataStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const runtime = createStandardizationRunRuntime({ metadataStorage, contentStore });
  const run = await reviewSingleSource(runtime);

  await assert.rejects(() => runtime.execute({
    type: 'MARK_DELIVERABLE_GENERATED', commandId: 'forged-direct-delivery', runId: run.runId,
    expectedRevision: run.revision, actor, deliverableId: 'forged-deliverable',
    payload: deliveryGeneratedPayload('forged-deliverable'),
  }), /标准化交付深模块/);
  assert.equal((await runtime.read(run.runId))?.revision, run.revision);
  assert.equal((await runtime.read(run.runId))?.deliverableId, undefined);
});

test('所有命令必须拒绝空 commandId 和空 actor', async () => {
  const { runtime, metadataStorage } = setup();
  await assert.rejects(() => runtime.execute({
    type: 'CREATE_RUN', commandId: '   ', expectedRevision: 0, actor,
    projectId: 'project-a', batchId: 'batch-a', sources: [fiveSources[0]!],
  }), /commandId不能为空/);
  assert.equal(metadataStorage.raw('linguan:standardization-runs:v1'), null);

  const created = await createRun(runtime, { sources: [fiveSources[0]!] });
  await assert.rejects(() => runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'blank-actor', runId: created.runId,
    expectedRevision: 0, actor: { userId: '   ' },
  }), /命令操作者不能为空/);
  assert.equal((await runtime.read(created.runId))?.revision, 0);
});

test('来源完成命令拒绝非整数 revision 和非有限非负读取计数', async () => {
  const { runtime } = setup();
  const created = await createRun(runtime, { sources: [fiveSources[0]!] });
  const started = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-numeric-validation', runId: created.runId,
    expectedRevision: 0, actor,
  });
  const base = {
    type: 'COMPLETE_SOURCE_DOCUMENT' as const,
    runId: started.runId,
    expectedRevision: 1,
    actor,
    sourceId: 'mysql',
    readSummary: { summary: '读取完成', objectCount: 1, evidenceCount: 1 },
    documentId: 'document-mysql',
    documentRevision: 1,
    introducedConflictIds: [],
    payload: '# MySQL 文档',
  };
  await assert.rejects(() => runtime.execute({
    ...base, commandId: 'invalid-document-revision', documentRevision: Number.NaN,
  }), /documentRevision必须是正整数/);
  await assert.rejects(() => runtime.execute({
    ...base, commandId: 'negative-object-count',
    readSummary: { ...base.readSummary, objectCount: -1 },
  }), /objectCount必须是有限非负整数/);
  await assert.rejects(() => runtime.execute({
    ...base, commandId: 'fractional-evidence-count',
    readSummary: { ...base.readSummary, evidenceCount: 1.5 },
  }), /evidenceCount必须是有限非负整数/);
  assert.equal((await runtime.read(started.runId))?.revision, 1);
});

test('同一 Runtime 的并发命令按 revision 串行提交，不能互相覆盖', async () => {
  const metadataStorage = new MemoryStorage();
  const backingStore = createMemoryContentStore();
  let releaseFirstPut!: () => void;
  let markFirstPutStarted!: () => void;
  const firstPutStarted = new Promise<void>((resolve) => { markFirstPutStarted = resolve; });
  const firstPutReleased = new Promise<void>((resolve) => { releaseFirstPut = resolve; });
  let putCount = 0;
  const delayedStore: ContentAddressedStore = {
    async put(content) {
      putCount += 1;
      if (putCount === 1) {
        markFirstPutStarted();
        await firstPutReleased;
      }
      return backingStore.put(content);
    },
    get: (ref) => backingStore.get(ref),
  };
  const runtime = createStandardizationRunRuntime({ metadataStorage, contentStore: delayedStore });
  const created = await createRun(runtime, { sources: [fiveSources[0]!] });
  const first = runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'concurrent-start-1', runId: created.runId,
    expectedRevision: 0, actor,
  });
  await firstPutStarted;
  const second = runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'concurrent-start-2', runId: created.runId,
    expectedRevision: 0, actor,
  });
  releaseFirstPut();

  const results = await Promise.allSettled([first, second]);
  assert.deepEqual(results.map((result) => result.status).sort(), ['fulfilled', 'rejected']);
  const rejection = results.find((result) => result.status === 'rejected');
  assert.match(rejection?.reason instanceof Error ? rejection.reason.message : '', /运行已被其他窗口更新/);
  const persisted = await runtime.read(created.runId);
  assert.equal(persisted?.revision, 1);
  assert.equal(persisted?.timeline.length, 1);
});

test('两个独立 Runtime 通过共享原子 metadata backend 并发 START 时只有一个 CAS 成功', async () => {
  const backend = new FakeAtomicMetadataBackend();
  const contentStore = createMemoryContentStore();
  const legacyStorage = new MemoryStorage();
  const firstRuntime = createStandardizationRunRuntime({
    metadataStore: backend.createStore(), metadataStorage: legacyStorage, contentStore,
  });
  const secondRuntime = createStandardizationRunRuntime({
    metadataStore: backend.createStore(), metadataStorage: legacyStorage, contentStore,
  });
  const created = await createRun(firstRuntime, { sources: [fiveSources[0]!] });
  backend.synchronizeNextReads(2);

  const results = await Promise.allSettled([
    firstRuntime.execute({
      type: 'START_NEXT_SOURCE', commandId: 'atomic-start-a', runId: created.runId,
      expectedRevision: 0, actor,
    }),
    secondRuntime.execute({
      type: 'START_NEXT_SOURCE', commandId: 'atomic-start-b', runId: created.runId,
      expectedRevision: 0, actor,
    }),
  ]);

  assert.deepEqual(results.map((result) => result.status).sort(), ['fulfilled', 'rejected']);
  const rejection = results.find((result) => result.status === 'rejected');
  assert.match(rejection?.reason instanceof Error ? rejection.reason.message : '', /运行已被其他窗口更新/);
  assert.equal(backend.failedCompareAndSetCount, 1);
  const persisted = JSON.parse(backend.raw() ?? '') as { runs: Array<{ revision: number; timeline: unknown[] }> };
  assert.equal(persisted.runs[0]?.revision, 1);
  assert.equal(persisted.runs[0]?.timeline.length, 1, '失败 CAS 不能覆盖成功窗口提交的状态');
});

test('两个独立 Runtime 通过共享原子 metadata backend 并发 CREATE 不生成重复 runId', async () => {
  const backend = new FakeAtomicMetadataBackend();
  const contentStore = createMemoryContentStore();
  const legacyStorage = new MemoryStorage();
  const firstRuntime = createStandardizationRunRuntime({
    metadataStore: backend.createStore(), metadataStorage: legacyStorage, contentStore,
  });
  const secondRuntime = createStandardizationRunRuntime({
    metadataStore: backend.createStore(), metadataStorage: legacyStorage, contentStore,
  });
  backend.synchronizeNextReads(2);

  const results = await Promise.allSettled([
    createRun(firstRuntime, {
      commandId: 'atomic-create-a', projectId: 'project-a', batchId: 'batch-a', sources: [fiveSources[0]!],
    }),
    createRun(secondRuntime, {
      commandId: 'atomic-create-b', projectId: 'project-b', batchId: 'batch-b', sources: [fiveSources[1]!],
    }),
  ]);

  assert.deepEqual(results.map((result) => result.status).sort(), ['fulfilled', 'rejected']);
  const rejection = results.find((result) => result.status === 'rejected');
  assert.match(rejection?.reason instanceof Error ? rejection.reason.message : '', /运行已被其他窗口更新/);
  const persisted = JSON.parse(backend.raw() ?? '') as { sequence: number; runs: Array<{ runId: string }> };
  assert.equal(persisted.sequence, 1);
  assert.deepEqual(persisted.runs.map((run) => run.runId), ['standardization-run-1']);
});

test('浏览器 metadata store 使用 IndexedDB 原子状态记录，不依赖 Web Locks 或租约', async () => {
  const storeModule = await import('./standardization-metadata-store.ts').catch(() => null);
  assert.ok(storeModule, '应提供浏览器原子 metadata store');
  let openCount = 0;
  const indexedDB = {
    open() {
      openCount += 1;
      throw new Error('IDB_METADATA_STORE_PROBE');
    },
  } as unknown as IDBFactory;
  const store = storeModule.createBrowserStandardizationMetadataStore({ indexedDB });

  await assert.rejects(() => store.read(), /IDB_METADATA_STORE_PROBE/);
  assert.equal(openCount, 1);
});

test('两个 Runtime 共享 Storage 时并发 START 仍只能提交一个 revision', async () => {
  const metadataStorage = new MemoryStorage();
  const backingStore = createMemoryContentStore();
  let releaseFirstPut!: () => void;
  let markFirstPutStarted!: () => void;
  const firstPutStarted = new Promise<void>((resolve) => { markFirstPutStarted = resolve; });
  const firstPutReleased = new Promise<void>((resolve) => { releaseFirstPut = resolve; });
  let putCount = 0;
  const delayedStore: ContentAddressedStore = {
    async put(content) {
      putCount += 1;
      if (putCount === 1) {
        markFirstPutStarted();
        await firstPutReleased;
      }
      return backingStore.put(content);
    },
    get: (ref) => backingStore.get(ref),
  };
  const firstRuntime = createStandardizationRunRuntime({ metadataStorage, contentStore: delayedStore });
  const secondRuntime = createStandardizationRunRuntime({ metadataStorage, contentStore: delayedStore });
  const created = await createRun(firstRuntime, { sources: [fiveSources[0]!] });
  const first = firstRuntime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'cross-runtime-start-1', runId: created.runId,
    expectedRevision: 0, actor,
  });
  await firstPutStarted;
  const second = secondRuntime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'cross-runtime-start-2', runId: created.runId,
    expectedRevision: 0, actor,
  });
  releaseFirstPut();

  const results = await Promise.allSettled([first, second]);
  assert.deepEqual(results.map((result) => result.status).sort(), ['fulfilled', 'rejected']);
  const rejection = results.find((result) => result.status === 'rejected');
  assert.match(rejection?.reason instanceof Error ? rejection.reason.message : '', /运行已被其他窗口更新/);
  const persisted = await secondRuntime.read(created.runId);
  assert.equal(persisted?.revision, 1);
  assert.equal(persisted?.timeline.length, 1);
});

test('两个 Runtime 共享 Storage adapter 时并发 CREATE 由 CAS 阻止重复 runId', async () => {
  const metadataStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const firstRuntime = createStandardizationRunRuntime({ metadataStorage, contentStore });
  const secondRuntime = createStandardizationRunRuntime({ metadataStorage, contentStore });

  const results = await Promise.allSettled([
    createRun(firstRuntime, {
      commandId: 'cross-runtime-create-a', projectId: 'project-a', batchId: 'batch-a', sources: [fiveSources[0]!],
    }),
    createRun(secondRuntime, {
      commandId: 'cross-runtime-create-b', projectId: 'project-b', batchId: 'batch-b', sources: [fiveSources[1]!],
    }),
  ]);
  assert.deepEqual(results.map((result) => result.status).sort(), ['fulfilled', 'rejected']);
  const rejection = results.find((result) => result.status === 'rejected');
  assert.match(rejection?.reason instanceof Error ? rejection.reason.message : '', /运行已被其他窗口更新/);
  const persisted = await firstRuntime.read('standardization-run-1');
  assert.ok(persisted?.projectId === 'project-a' || persisted?.projectId === 'project-b');
  assert.equal(await firstRuntime.read('standardization-run-2'), null);
});

test('不同 sourceId 可以使用相同 sourceName，创建后仍可读取', async () => {
  const { runtime } = setup();
  const created = await createRun(runtime, {
    sources: [
      { sourceId: 'mysql-primary', sourceName: '同名数据库来源' },
      { sourceId: 'mysql-replica', sourceName: '同名数据库来源' },
    ],
  });

  assert.deepEqual((await runtime.read(created.runId))?.sources.map(({ sourceId, sourceName }) => ({ sourceId, sourceName })), [
    { sourceId: 'mysql-primary', sourceName: '同名数据库来源' },
    { sourceId: 'mysql-replica', sourceName: '同名数据库来源' },
  ]);
});

test('并发命令内容写入失败不会阻止另一个 Runtime 完成 CAS', async () => {
  const metadataStorage = new MemoryStorage();
  const backingStore = createMemoryContentStore();
  let putCount = 0;
  const failOnceStore: ContentAddressedStore = {
    async put(content) {
      putCount += 1;
      if (putCount === 1) {
        await Promise.resolve();
        throw new Error('模拟内容存储失败');
      }
      return backingStore.put(content);
    },
    get: (ref) => backingStore.get(ref),
  };
  const firstRuntime = createStandardizationRunRuntime({ metadataStorage, contentStore: failOnceStore });
  const secondRuntime = createStandardizationRunRuntime({ metadataStorage, contentStore: failOnceStore });
  const created = await createRun(firstRuntime, { sources: [fiveSources[0]!] });
  const results = await Promise.allSettled([
    firstRuntime.execute({
      type: 'START_NEXT_SOURCE', commandId: 'failing-cross-runtime-start', runId: created.runId,
      expectedRevision: 0, actor,
    }),
    secondRuntime.execute({
      type: 'START_NEXT_SOURCE', commandId: 'recovering-cross-runtime-start', runId: created.runId,
      expectedRevision: 0, actor,
    }),
  ]);
  assert.deepEqual(results.map((result) => result.status).sort(), ['fulfilled', 'rejected']);
  const rejection = results.find((result) => result.status === 'rejected');
  assert.match(rejection?.reason instanceof Error ? rejection.reason.message : '', /模拟内容存储失败/);
  assert.equal((await secondRuntime.read(created.runId))?.revision, 1);
});

test('不同项目与 runId 完全隔离，不存在的 runId 不会回落到项目最新运行', async () => {
  const { runtime } = setup();
  let first = await createRun(runtime, {
    commandId: 'create-first', projectId: 'project-a', batchId: 'batch-a', sources: [fiveSources[0]!],
  });
  let second = await createRun(runtime, {
    commandId: 'create-second', projectId: 'project-b', batchId: 'batch-b', sources: [fiveSources[1]!],
  });
  first = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-first', runId: first.runId,
    expectedRevision: 0, actor,
  });
  second = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-second', runId: second.runId,
    expectedRevision: 0, actor,
  });
  assert.equal(first.sources[0]?.sourceId, 'mysql');
  assert.equal(second.sources[0]?.sourceId, 'github');
  assert.equal((await runtime.read(first.runId))?.timeline.length, 1);
  assert.equal((await runtime.read(second.runId))?.timeline.length, 1);
  assert.equal(await runtime.read('missing-run'), null);
  await assert.rejects(() => runtime.readEventPayload('missing-run', first.timeline[0]!.eventId), /标准化运行不存在/);
});

test('最后来源对齐后进入 READY_FOR_OUTPUT，四个交付命令只能按顺序推进到 HANDED_OFF', async () => {
  const { runtime, metadataStorage, contentStore } = setup();
  let run = await createRun(runtime, { sources: [fiveSources[0]!] });
  run = await runtime.execute({
    type: 'START_NEXT_SOURCE', commandId: 'start-mysql', runId: run.runId,
    expectedRevision: 0, actor,
  });
  run = await completeCurrentSource(runtime, {
    runId: run.runId, sourceId: 'mysql', expectedRevision: 1, commandId: 'complete-mysql',
  });
  run = await runtime.execute({
    type: 'MARK_DOCUMENT_REVIEWED', commandId: 'review-mysql', runId: run.runId,
    expectedRevision: 2, actor, sourceId: 'mysql',
  });
  assert.equal(run.status, 'READY_FOR_OUTPUT');
  assert.equal(run.scenarioKey, 'guanyijia-five-source-v1');
  await assert.rejects(() => runtime.execute({
    type: 'MARK_REVIEW_SUBMITTED', commandId: 'submit-too-early', runId: run.runId,
    expectedRevision: 3, actor, reviewId: 'review-1', deliveryCapability,
    payload: JSON.stringify({ reviewId: 'review-1' }),
  }), /必须先生成标准化交付物/);
  assert.equal((await runtime.read(run.runId))?.revision, 3);

  const refs = {
    core: `sha256:${'a'.repeat(64)}`,
    source: `sha256:${'b'.repeat(64)}`,
    merged: `sha256:${'c'.repeat(64)}`,
    decision: `sha256:${'d'.repeat(64)}`,
    governance: `sha256:${'e'.repeat(64)}`,
    artifact: `sha256:${'f'.repeat(64)}`,
    delta: `sha256:${'1'.repeat(64)}`,
    semantic: `sha256:${'2'.repeat(64)}`,
    receipt: `sha256:${'3'.repeat(64)}`,
  };
  const delivery: StandardizationRunCommand[] = [
    { type: 'MARK_DELIVERABLE_GENERATED', commandId: 'deliverable-generated', runId: run.runId,
      expectedRevision: 3, actor, deliverableId: 'deliverable-1', deliveryCapability, payload: JSON.stringify({
        schemaVersion: 1, deliverableId: 'deliverable-1', coreSha256: refs.core,
        sourceManifestRef: refs.source, mergedDocumentRef: refs.merged,
        decisionManifestRef: refs.decision, governanceAppendixRef: refs.governance,
        modelingArtifactRef: refs.artifact, zeroDeltaReportRef: refs.delta,
      }) },
    { type: 'MARK_REVIEW_SUBMITTED', commandId: 'review-submitted', runId: run.runId,
      expectedRevision: 4, actor, reviewId: 'review-1', deliveryCapability, payload: JSON.stringify({
        schemaVersion: 1, deliverableId: 'deliverable-1', reviewId: 'review-1',
        authorUserId: actor.userId, confirmedAt: '2026-08-17T12:00:00.000Z', coreSha256: refs.core,
      }) },
    { type: 'MARK_DELIVERABLE_FROZEN', commandId: 'deliverable-frozen', runId: run.runId,
      expectedRevision: 5, actor: { userId: 'reviewer' }, deliverableId: 'deliverable-1',
      deliveryCapability, payload: JSON.stringify({
        schemaVersion: 1, deliverableId: 'deliverable-1', reviewId: 'review-1',
        reviewerUserId: 'reviewer', reviewedAt: '2026-08-17T12:00:00.000Z',
        coreSha256: refs.core, zeroDeltaReportRef: refs.delta,
        semanticPayloadSha256: refs.semantic,
      }) },
    { type: 'MARK_MODELING_HANDOFF_COMPLETED', commandId: 'handoff-completed', runId: run.runId,
      expectedRevision: 6, actor, handoffId: 'handoff-1', deliveryCapability, payload: JSON.stringify({
        schemaVersion: 1, deliverableId: 'deliverable-1', handoffId: 'handoff-1',
        receiptRef: refs.receipt, zeroDeltaReportRef: refs.delta,
        semanticPayloadSha256: refs.semantic, protectedCatalogId: 'catalog-v1',
        handedOffBy: actor.userId, handedOffAt: '2026-08-17T12:00:00.000Z',
      }) },
  ];
  for (const command of delivery) run = await runtime.execute(command);

  assert.equal(run.status, 'HANDED_OFF');
  assert.equal(run.revision, 7);
  assert.deepEqual(run.timeline.slice(-4).map((event) => event.type), [
    'DELIVERABLE_GENERATED', 'REVIEW_SUBMITTED', 'DELIVERABLE_FROZEN', 'MODELING_HANDOFF_COMPLETED',
  ]);
  assert.equal(run.deliverableId, 'deliverable-1');
  assert.equal(run.reviewId, 'review-1');
  assert.equal(run.modelingHandoffId, 'handoff-1');
  assert.deepEqual(JSON.parse(await runtime.readEventPayload(
    run.runId, run.timeline.at(-4)!.eventId,
  )), JSON.parse((delivery[0] as Extract<StandardizationRunCommand, { type: 'MARK_DELIVERABLE_GENERATED' }>).payload));

  const raw = metadataStorage.raw('linguan:standardization-runs:v1');
  assert.ok(raw);
  const state = JSON.parse(raw) as {
    runs: Array<{ timeline: Array<{ type: string; payloadRef: string }> }>;
  };
  const reviewEvent = state.runs[0]!.timeline.find((event) => event.type === 'REVIEW_SUBMITTED')!;
  reviewEvent.payloadRef = await contentStore.put(JSON.stringify({
    ...JSON.parse((delivery[1] as Extract<StandardizationRunCommand, { type: 'MARK_REVIEW_SUBMITTED' }>).payload),
    coreSha256: `sha256:${'9'.repeat(64)}`,
  }));
  metadataStorage.setItem('linguan:standardization-runs:v1', JSON.stringify(state));
  await assert.rejects(() => runtime.read(run.runId), /交付事件审计链/u);
});
