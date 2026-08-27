import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test, { before } from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { guanyijiaV1 } from '../collaboration/fixtures.ts';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import {
  canonicalModelingJson,
  modelingDocumentSemanticPayloadSha256,
} from '../modeling-document-bridge/standard-markdown.ts';
import type { ContentReference } from '../source-documents/types.ts';
import { createSourceDocumentRuntime } from '../source-documents/runtime.ts';
import { createStandardizationRunRuntime } from '../standardization-run/runtime.ts';
import type { StandardizationRun, StandardizationRunCommand } from '../standardization-run/types.ts';
import {
  createGuanyijiaWorkbenchRuntime,
  guanyijiaDeliverableContentLabelFor,
  guanyijiaActiveRunPointerStorageKeyFor,
  validateGuanyijiaCorroborationReceipt,
  validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions,
  validateGuanyijiaResolutionArtifactAgainstPersistedRevisions,
  type GuanyijiaWorkbenchCommand,
} from '../data-standardization/guanyijia-workbench-runtime.ts';
import { createStandardizationDeliverableRuntime } from './runtime.ts';
import { createBrowserDeliverableMetadataStore } from './metadata-store.ts';
import { projectZeroDeltaM4View } from '../ai-modeling/zero-delta-handoff.ts';
import {
  createStandardizationModelingProjector,
  semanticPathDifferences,
} from './semantic-projection.ts';
import type {
  ConflictResolutionReader,
  ProtectedBaseline,
  StandardizationRunReader,
} from './types.ts';
import type { WorkspaceRole } from '../collaboration/types.ts';

class MemoryMetadataStore {
  version = 0;
  raw: string | null = null;
  compareAndSetCalls = 0;
  private readonly failCompareAndSetAt?: number;
  constructor(failCompareAndSetAt?: number) { this.failCompareAndSetAt = failCompareAndSetAt; }
  async read() { return { version: String(this.version), raw: this.raw }; }
  async compareAndSet(expectedVersion: string, nextRaw: string) {
    this.compareAndSetCalls += 1;
    if (this.compareAndSetCalls === this.failCompareAndSetAt) return false;
    if (expectedVersion !== String(this.version)) return false;
    this.raw = nextRaw;
    this.version += 1;
    return true;
  }
}

class MemoryStorage implements Pick<Storage, 'getItem' | 'setItem'> {
  readonly values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  clone() {
    const result = new MemoryStorage();
    for (const [key, value] of this.values) result.values.set(key, value);
    return result;
  }
}

class MemoryContentStore {
  readonly values = new Map<ContentReference, string>();
  async put(content: string) {
    const ref = `sha256:${sha256HexSync(content)}` as ContentReference;
    if (!this.values.has(ref)) this.values.set(ref, content);
    return ref;
  }
  async get(ref: ContentReference) { return this.values.get(ref) ?? null; }
  clone() {
    const result = new MemoryContentStore();
    for (const [key, value] of this.values) result.values.set(key, value);
    return result;
  }
}

const baseline: ProtectedBaseline = {
  scenarioKey: 'guanyijia-five-source-v1',
  expectedSourceIds: [
    'guanyijia_mysql', 'guanyijia_github', 'guanyijia_official_docs',
    'guanyijia_demo_policy', 'guanyijia_semantica_demo',
  ],
  expectedSourceTypes: {
    guanyijia_mysql: 'REAL', guanyijia_github: 'REAL', guanyijia_official_docs: 'REAL',
    guanyijia_demo_policy: 'DEMO_POLICY', guanyijia_semantica_demo: 'DERIVED',
  },
  expectedArtifactId: guanyijiaFrozenModelingArtifact.artifactId,
  artifact: structuredClone(guanyijiaFrozenModelingArtifact),
  catalogId: 'catalog_guanyijia_v1',
  catalogFingerprint: `guanyijia:${guanyijiaFrozenModelingArtifact.markdown.sha256}`,
  catalogData: guanyijiaV1(),
  semanticPayloadSha256: 'c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248',
  markdownSha256: '5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210',
  expectedCounts: {
    entities: 14, events: 9, fields: 296, relations: 30, dimensions: 4, metrics: 5,
    hierarchies: 2, rules: 8, aliases: 10, timeRules: 9, pendingAssets: 63, exclusions: 2,
  },
  projectionContext: {
    systemCode: 'guanyijia_erp', modelSpaceId: 'guanyijia_erp',
    datasourceName: 'guanyijia_mysql', schemaName: 'jsh_erp', ownerName: 'AI 建模',
  },
};
const deliveryCapability = {};

type Prepared = {
  metadataStorage: MemoryStorage; contentStore: MemoryContentStore;
  runId: string; runRevision: number; run: StandardizationRun;
  resolutions: Awaited<ReturnType<ConflictResolutionReader['list']>>;
};
let prepared: Prepared;

function workbenchCommand(
  type: GuanyijiaWorkbenchCommand['type'], expectedRevision: number, commandId: string,
): GuanyijiaWorkbenchCommand {
  return { type, expectedRevision, commandId, actorUserId: 'user_bo_gao' } as GuanyijiaWorkbenchCommand;
}

before(async () => {
  const metadataStorage = new MemoryStorage();
  const pointerStorage = new MemoryStorage();
  const contentStore = new MemoryContentStore();
  const sourceDocuments = createSourceDocumentRuntime({ metadataStorage, contentStore });
  const story = createGuanyijiaStandardizationStory();
  const runs = createStandardizationRunRuntime({
    metadataStorage, contentStore, deliveryCapability,
    resolutionArtifactValidator: (artifact, run) => (
      validateGuanyijiaResolutionArtifactAgainstPersistedRevisions({
        artifact, run, sourceDocuments, story,
      })
    ),
    corroborationReceiptValidator: (receipt, run) => (
      validateGuanyijiaCorroborationReceipt({ receipt, run, story })
    ),
    corroborationReceiptSetValidator: (projection, run) => (
      validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions({
        projection, run, sourceDocuments, story,
      })
    ),
    now: () => '2026-08-18T10:00:00.000Z',
  });
  const workbench = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: runs, sourceDocuments, story, contentStore,
    now: () => '2026-08-18T10:00:00.000Z',
  });
  let snapshot = await workbench.execute(workbenchCommand('START_RUN', 0, 'prepare:start'));
  const readAndReview = async (label: string) => {
    snapshot = await workbench.execute(workbenchCommand(
      'READ_NEXT_SOURCE', snapshot.run!.revision, `prepare:read:${label}`,
    ));
    snapshot = await workbench.execute(workbenchCommand(
      'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, `prepare:review:${label}`,
    ));
  };
  const resolve = async (
    conflictId: string,
    strategy: 'KEEP_CURRENT' | 'MERGE' | 'DEFER_AS_GAP',
    reason: string,
  ) => {
    const preview = await workbench.previewCurrentConflict({
      runId: snapshot.run!.runId, conflictId, strategy,
    });
    snapshot = await workbench.execute({
      type: 'RESOLVE_CURRENT_CONFLICT', commandId: `prepare:resolve:${conflictId}`,
      expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
      conflictId, strategy, reason, expectedHunkSha256: preview.hunk.hunkSha256,
    });
  };
  await readAndReview('mysql');
  await readAndReview('github');
  await resolve('gyj-conflict-debt-schema', 'KEEP_CURRENT', '部署数据库作为当前工作标准。');
  await readAndReview('official');
  await readAndReview('policy');
  await resolve('gyj-conflict-negative-stock', 'MERGE', '保留租户配置实现并登记制度缺口。');
  await resolve('gyj-conflict-status-nine', 'DEFER_AS_GAP', '状态九正式业务含义待确认。');
  await readAndReview('semantica');
  assert.equal(snapshot.run?.status, 'READY_FOR_OUTPUT');
  const resolutions = await Promise.all(snapshot.run!.timeline
    .filter((event) => event.type === 'CONFLICT_RESOLVED')
    .map(async (event) => JSON.parse(await runs.readEventPayload(snapshot.run!.runId, event.eventId))));
  prepared = {
    metadataStorage, contentStore,
    runId: snapshot.run!.runId, runRevision: snapshot.run!.revision,
    run: structuredClone(snapshot.run!),
    resolutions,
  };
});

function readyFixture(
  now: string | (() => string) = '2026-08-18T11:00:00.000Z',
  options: {
    baseline?: ProtectedBaseline;
    projectRun?: (run: StandardizationRun) => StandardizationRun;
    projectResolutions?: (values: Awaited<ReturnType<ConflictResolutionReader['list']>>) => Awaited<ReturnType<ConflictResolutionReader['list']>>;
    failAppendOnce?: StandardizationRunReader['appendEvent'] extends (input: infer T) => unknown ? T['type'] : never;
    accessForActor?: (actorUserId: string) => { active: boolean; role: WorkspaceRole };
    bypassRunValidation?: boolean;
    failDeliverableCasAt?: number;
  } = {},
) {
  const clock = typeof now === 'string' ? () => now : now;
  const metadataStorage = prepared.metadataStorage.clone();
  const contentStore = prepared.contentStore.clone();
  const sourceDocuments = createSourceDocumentRuntime({ metadataStorage, contentStore });
  const story = createGuanyijiaStandardizationStory();
  const runs = createStandardizationRunRuntime({
    metadataStorage, contentStore, deliveryCapability,
    resolutionArtifactValidator: (artifact, run) => (
      validateGuanyijiaResolutionArtifactAgainstPersistedRevisions({
        artifact, run, sourceDocuments, story,
      })
    ),
    corroborationReceiptValidator: (receipt, run) => (
      validateGuanyijiaCorroborationReceipt({ receipt, run, story })
    ),
    corroborationReceiptSetValidator: (projection, run) => (
      validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions({
        projection, run, sourceDocuments, story,
      })
    ),
    now: clock,
  });
  let pendingFailure = options.failAppendOnce;
  const runReader: StandardizationRunReader = {
    async read(runId) {
      if (options.bypassRunValidation && runId === prepared.runId) return structuredClone(prepared.run);
      const run = await runs.read(runId);
      return run && options.projectRun ? options.projectRun(run) : run;
    },
    async readEventPayload(runId, eventId) {
      if (!options.bypassRunValidation) return runs.readEventPayload(runId, eventId);
      const raw = metadataStorage.getItem('linguan:standardization-runs:v1');
      if (!raw) throw new Error('标准化运行不存在');
      const run = JSON.parse(raw).runs.find((candidate: { runId: string }) => candidate.runId === runId);
      const event = run?.timeline.find((candidate: { eventId: string }) => candidate.eventId === eventId);
      if (!event) throw new Error('标准化运行事件不存在');
      const payload = await contentStore.get(event.payloadRef);
      if (payload === null) throw new Error('标准化运行事件载荷不存在');
      return payload;
    },
    async appendEvent(input) {
      if (pendingFailure === input.type) {
        pendingFailure = undefined;
        throw new Error(`injected ${input.type} append failure`);
      }
      const common = {
        commandId: input.commandId, runId: input.runId, expectedRevision: input.expectedRunRevision,
        actor: { userId: input.actorUserId }, payload: input.payload, deliveryCapability,
      };
      const command: StandardizationRunCommand = input.type === 'DELIVERABLE_GENERATED'
        ? { ...common, type: 'MARK_DELIVERABLE_GENERATED', deliverableId: input.deliverableId }
        : input.type === 'REVIEW_SUBMITTED'
          ? { ...common, type: 'MARK_REVIEW_SUBMITTED', reviewId: input.eventIdentity }
          : input.type === 'DELIVERABLE_FROZEN'
            ? { ...common, type: 'MARK_DELIVERABLE_FROZEN', deliverableId: input.deliverableId }
            : { ...common, type: 'MARK_MODELING_HANDOFF_COMPLETED', handoffId: input.eventIdentity };
      return runs.execute(command);
    },
  };
  const conflictResolutions: ConflictResolutionReader = {
    async list(run: StandardizationRun) {
      if (options.bypassRunValidation) return structuredClone(prepared.resolutions);
      const values = await Promise.all(run.timeline.filter((event) => (
        event.type === 'CONFLICT_RESOLVED' || event.type === 'CONFLICT_DECISION_REPLACED'
      )).map(async (event) => (
        JSON.parse(await runs.readEventPayload(run.runId, event.eventId))
      )));
      const currentByConflict = new Map<string, Awaited<ReturnType<ConflictResolutionReader['list']>>[number]>();
      for (const value of values) currentByConflict.set(value.hunk.conflictId, value);
      const current = [...currentByConflict.values()];
      return options.projectResolutions ? options.projectResolutions(current) : current;
    },
  };
  const metadataStore = new MemoryMetadataStore(options.failDeliverableCasAt);
  const modelingProjector = createStandardizationModelingProjector();
  const runtime = createStandardizationDeliverableRuntime({
    metadataStore, contentStore, runReader, sourceDocuments,
    conflictResolutions,
    baselineProvider: { async resolve(scenarioKey) {
      const selected = options.baseline ?? baseline;
      return scenarioKey === selected.scenarioKey ? structuredClone(selected) : null;
    } },
    modelingProjector,
    membershipReader: { async read(actorUserId) {
      return options.accessForActor?.(actorUserId) ?? (actorUserId === 'user_bo_gao'
        ? { active: true, role: 'EDITOR' as const }
        : { active: true, role: 'REVIEWER' as const });
    } },
    now: clock,
  });
  return {
    runtime, runs, contentStore, metadataStore, metadataStorage,
    conflictResolutions, modelingProjector, sourceDocuments, story,
  };
}

async function rewriteGeneratedRunEvent(
  fixture: ReturnType<typeof readyFixture>,
  deliverable: {
    deliverableId: string; mode?: 'SOURCE_DOCUMENT_SET' | 'MERGED_DOCUMENT'; coreSha256: string;
    sourceManifestRef: ContentReference; mergedDocumentRef: ContentReference;
    decisionManifestRef: ContentReference; governanceAppendixRef: ContentReference;
    modelingArtifactRef: ContentReference; zeroDeltaReportRef: ContentReference;
  },
) {
  const raw = fixture.metadataStorage.getItem('linguan:standardization-runs:v1');
  assert.ok(raw);
  const state = JSON.parse(raw);
  const event = state.runs[0].timeline.find((candidate: { type: string }) => (
    candidate.type === 'DELIVERABLE_GENERATED'
  ));
  assert.ok(event);
  const payload = {
    schemaVersion: 1,
    deliverableId: deliverable.deliverableId,
    mode: deliverable.mode ?? 'MERGED_DOCUMENT',
    coreSha256: deliverable.coreSha256,
    sourceManifestRef: deliverable.sourceManifestRef,
    mergedDocumentRef: deliverable.mergedDocumentRef,
    decisionManifestRef: deliverable.decisionManifestRef,
    governanceAppendixRef: deliverable.governanceAppendixRef,
    modelingArtifactRef: deliverable.modelingArtifactRef,
    zeroDeltaReportRef: deliverable.zeroDeltaReportRef,
  };
  event.payloadRef = await fixture.contentStore.put(canonicalModelingJson(payload));
  event.deliveryContentIndex = {
    coreSha256: payload.coreSha256,
    entries: [
      'sourceManifestRef', 'mergedDocumentRef', 'decisionManifestRef',
      'governanceAppendixRef', 'modelingArtifactRef', 'zeroDeltaReportRef',
    ].map((key) => ({ key, contentRef: payload[key] })),
  };
  fixture.metadataStorage.setItem('linguan:standardization-runs:v1', JSON.stringify(state));
}

test('非 READY_FOR_OUTPUT 的 Run 不能生成交付物', async () => {
  const runtime = createStandardizationDeliverableRuntime({
    metadataStore: new MemoryMetadataStore(),
    contentStore: { async put() { throw new Error('不应写内容'); }, async get() { return null; } },
    runReader: {
      async read() {
        return {
          schemaVersion: 1, runId: 'run-not-ready', scenarioKey: 'guanyijia-five-source-v1',
          projectId: 'guanyijia_erp', batchId: 'batch-1', revision: 9, status: 'READY',
          sources: [], timeline: [], createdBy: 'author',
          createdAt: '2026-08-18T10:00:00.000Z', updatedAt: '2026-08-18T10:00:00.000Z',
        };
      },
      async appendEvent() { throw new Error('不应补登事件'); },
      async readEventPayload() { throw new Error('不应读取事件'); },
    },
    sourceDocuments: { async read() { return null; } },
    conflictResolutions: { async list() { return []; } },
    baselineProvider: { async resolve() { throw new Error('不应读取基线'); } },
    modelingProjector: { async project() { throw new Error('不应投影'); } },
    membershipReader: { async read() { return { active: true, role: 'EDITOR' as const }; } },
    now: () => '2026-08-18T10:00:00.000Z',
  });

  await assert.rejects(runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'generate:not-ready', runId: 'run-not-ready',
    actorUserId: 'author', expectedRunRevision: 9,
  }), /READY_FOR_OUTPUT/);
});

test('交付metadata按状态精确禁止审批或Receipt未来字段提前出现', async () => {
  const fixture = readyFixture();
  const generated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'future-field:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const metadata = JSON.parse(fixture.metadataStore.raw!);
  metadata.deliverables[0].authorConfirmation = {
    reviewId: 'future-review', actorUserId: 'user_bo_gao',
    confirmedAt: '2026-08-18T11:00:00.000Z', coreSha256: generated.deliverable!.coreSha256,
  };
  fixture.metadataStore.raw = JSON.stringify(metadata);

  await assert.rejects(
    fixture.runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' }),
    /metadata 状态组合无效/,
  );
});

test('来源revision必须保持Run事件绑定且blocks assertions sections Markdown投影一致', async () => {
  const fixture = readyFixture('2026-08-18T11:00:00.000Z', { bypassRunValidation: true });
  const run = (await fixture.runs.read(prepared.runId))!;
  const sourceRaw = fixture.metadataStorage.getItem('linguan:source-documents:v1');
  assert.ok(sourceRaw);
  const sourceState = JSON.parse(sourceRaw);
  const firstDocument = sourceState.documents.find((document: { documentId: string }) => (
    document.documentId === run.sources[0]!.documentId
  ));
  assert.ok(firstDocument);
  firstDocument.blocksRef = await fixture.contentStore.put('[]');
  fixture.metadataStorage.setItem('linguan:source-documents:v1', JSON.stringify(sourceState));
  const runRaw = fixture.metadataStorage.getItem('linguan:standardization-runs:v1');
  assert.ok(runRaw);
  const runState = JSON.parse(runRaw);
  const generatedEvent = runState.runs[0].timeline.find((event: {
    type: string; sourceDocumentId?: string;
  }) => event.type === 'DOCUMENT_GENERATED' && event.sourceDocumentId === firstDocument.documentId);
  assert.ok(generatedEvent);
  const eventPayload = JSON.parse((await fixture.contentStore.get(generatedEvent.payloadRef))!);
  eventPayload.documentContent.blocksRef = firstDocument.blocksRef;
  generatedEvent.payloadRef = await fixture.contentStore.put(JSON.stringify(eventPayload));
  fixture.metadataStorage.setItem('linguan:standardization-runs:v1', JSON.stringify(runState));

  await assert.rejects(fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'source-projection:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  }), /每个结构化块必须有且仅有一个对应断言/);
});

test('真实五源Run确定性生成四类内容、独立Artifact与零变化报告', async () => {
  const first = readyFixture('2026-08-18T11:00:00.000Z');
  const generated = await first.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'generate:ready', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  assert.equal(generated.deliverable?.status, 'GENERATED_AWAITING_AUTHOR');
  assert.equal(generated.deliverable?.linkState, 'LINKED');
  assert.notEqual(generated.deliverable?.deliverableId, guanyijiaFrozenModelingArtifact.artifactId);
  assert.equal(generated.run?.timeline.at(-1)?.type, 'DELIVERABLE_GENERATED');

  const readJson = async <T,>(contentRef: ContentReference) => JSON.parse((await first.runtime.readContent({
    contentRef, actorUserId: 'user_bo_gao', limit: 1000,
  })).items.join('')) as T;
  const manifest = await readJson<{ sources: Array<{ sourceId: string; sourceSnapshotId: string; evidenceRole: string }> }>(
    generated.deliverable!.sourceManifestRef,
  );
  assert.deepEqual(manifest.sources.map(({ sourceId, evidenceRole }) => ({ sourceId, evidenceRole })), [
    { sourceId: 'guanyijia_mysql', evidenceRole: 'FORMAL_ROOT' },
    { sourceId: 'guanyijia_github', evidenceRole: 'FORMAL_ROOT' },
    { sourceId: 'guanyijia_official_docs', evidenceRole: 'FORMAL_ROOT' },
    { sourceId: 'guanyijia_demo_policy', evidenceRole: 'NON_FORMAL_ROOT' },
    { sourceId: 'guanyijia_semantica_demo', evidenceRole: 'DERIVED_CORROBORATION' },
  ]);
  const decisions = await readJson<{ decisions: Array<{ conflictId: string; strategy: string }> }>(
    generated.deliverable!.decisionManifestRef,
  );
  assert.deepEqual(decisions.decisions.map(({ conflictId, strategy }) => ({ conflictId, strategy })), [
    { conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT' },
    { conflictId: 'gyj-conflict-negative-stock', strategy: 'MERGE' },
    { conflictId: 'gyj-conflict-status-nine', strategy: 'DEFER_AS_GAP' },
  ]);
  const artifact = await readJson<typeof guanyijiaFrozenModelingArtifact>(generated.deliverable!.modelingArtifactRef);
  assert.notEqual(artifact.artifactId, guanyijiaFrozenModelingArtifact.artifactId);
  assert.equal(artifact.origin, 'STANDARDIZATION');
  assert.deepEqual(artifact.delta?.addedSnapshotIds, manifest.sources.map((source) => source.sourceSnapshotId));
  assert.deepEqual(artifact.sourceBatch?.snapshotIds, manifest.sources.map((source) => source.sourceSnapshotId));
  assert.equal(artifact.semanticPayload?.sha256, baseline.semanticPayloadSha256);
  assert.equal(modelingDocumentSemanticPayloadSha256(artifact.semanticPayload!.data), baseline.semanticPayloadSha256);
  const firstArtifactChunk = await first.runtime.readContent({
    contentRef: generated.deliverable!.modelingArtifactRef, actorUserId: 'user_bo_gao', limit: 1,
  });
  assert.ok(new TextEncoder().encode(firstArtifactChunk.items[0]!).byteLength <= 4096);
  assert.ok(firstArtifactChunk.nextCursor);
  assert.ok(firstArtifactChunk.total > 1);
  const remainingArtifactChunks = await first.runtime.readContent({
    contentRef: generated.deliverable!.modelingArtifactRef,
    actorUserId: 'user_bo_gao', cursor: firstArtifactChunk.nextCursor!, limit: 1000,
  });
  assert.deepEqual(
    JSON.parse([...firstArtifactChunk.items, ...remainingArtifactChunks.items].join('')),
    artifact,
  );
  const delta = await readJson<{ semanticDifferences: unknown[]; catalogChanges: unknown[] }>(
    generated.deliverable!.zeroDeltaReportRef,
  );
  assert.deepEqual(delta.semanticDifferences, []);
  assert.deepEqual(delta.catalogChanges, []);

  const second = readyFixture('2026-08-19T12:34:56.000Z');
  const repeated = await second.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'generate:other-clock', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  assert.equal(repeated.deliverable?.deliverableId, generated.deliverable?.deliverableId);
  assert.equal(repeated.deliverable?.coreSha256, generated.deliverable?.coreSha256);
  assert.equal(repeated.deliverable?.modelingArtifactRef, generated.deliverable?.modelingArtifactRef);
});

test('production deliverable review-window lists six refs without opening their bodies and verifies only the selected content', async () => {
  const fixture = readyFixture('2026-08-18T11:00:00.000Z');
  const generated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'review-window:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const deliverable = generated.deliverable!;
  const expectedRefs = new Set([
    deliverable.sourceManifestRef, deliverable.mergedDocumentRef, deliverable.decisionManifestRef,
    deliverable.governanceAppendixRef, deliverable.modelingArtifactRef, deliverable.zeroDeltaReportRef,
  ]);
  const openedRefs: ContentReference[] = [];
  const originalGet = fixture.contentStore.get.bind(fixture.contentStore);
  fixture.contentStore.get = async (ref) => { openedRefs.push(ref); return originalGet(ref); };
  const pointerStorage = new MemoryStorage();
  pointerStorage.setItem(guanyijiaActiveRunPointerStorageKeyFor(prepared.run.batchId), JSON.stringify({
    schemaVersion: 1, projectId: 'guanyijia_erp', storyId: 'guanyijia-five-source-v1',
    batchId: prepared.run.batchId, runId: prepared.runId, commandFingerprints: {},
  }));
  const workbench = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: fixture.runs, sourceDocuments: fixture.sourceDocuments,
    story: fixture.story, contentStore: fixture.contentStore,
  });

  const page = await workbench.readReviewWindow({
    actorUserId: 'user_bo_gao', runId: prepared.runId,
    epoch: `revision:${generated.run!.revision}`, stream: 'DELIVERABLE_SECTIONS', filter: 'all',
  });
  assert.equal(page.items.length, 6);
  assert.deepEqual(new Set(page.items.map(({ contentRef }) => contentRef!)), expectedRefs);
  assert.equal(openedRefs.some((ref) => expectedRefs.has(ref)), false, 'deliverable summary index must not open content bodies');

  const selected = page.items.find(({ stableKey }) => stableKey === 'deliverable:mergedDocumentRef')!;
  const verified = await workbench.readReviewContent({
    actorUserId: 'user_bo_gao', runId: prepared.runId,
    contentRef: selected.contentRef!, expectedSha256: selected.contentSha256!,
  });
  assert.equal(verified.contentRef, deliverable.mergedDocumentRef);
  assert.equal(openedRefs.filter((ref) => expectedRefs.has(ref)).length, 1);
});

test('SOURCE_DOCUMENT_SET 交付物通过真实生命周期持久化模式并可在reload后保持FROZEN', async () => {
  const fixture = readyFixture('2026-08-18T11:30:00.000Z');
  const generated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', mode: 'SOURCE_DOCUMENT_SET',
    commandId: 'source-document-set:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  assert.equal(generated.deliverable?.mode, 'SOURCE_DOCUMENT_SET');
  const submitted = await fixture.runtime.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'source-document-set:author', runId: prepared.runId,
    deliverableId: generated.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: generated.deliverable!.revision,
  });
  const frozen = await fixture.runtime.execute({
    type: 'REVIEW_AND_FREEZE', commandId: 'source-document-set:freeze', runId: prepared.runId,
    deliverableId: submitted.deliverable!.deliverableId, actorUserId: 'reviewer',
    expectedDeliverableRevision: submitted.deliverable!.revision,
  });
  assert.equal(frozen.deliverable?.mode, 'SOURCE_DOCUMENT_SET');
  const reloaded = createStandardizationDeliverableRuntime({
    metadataStore: fixture.metadataStore, contentStore: fixture.contentStore,
    runReader: fixture.runs as unknown as StandardizationRunReader,
    sourceDocuments: fixture.sourceDocuments,
    conflictResolutions: { async list() { return fixture.conflictResolutions; } },
    baselineProvider: { async resolve(scenarioKey) { return scenarioKey === baseline.scenarioKey ? baseline : null; } },
    modelingProjector: fixture.modelingProjector,
    membershipReader: { async read(actorUserId) {
      return { active: true, role: actorUserId === 'reviewer' ? 'REVIEWER' as const : 'EDITOR' as const };
    } },
    now: () => '2026-08-18T11:30:00.000Z',
  });
  const persisted = await reloaded.read({ runId: prepared.runId, actorUserId: 'reviewer' });
  assert.equal(persisted.deliverable?.mode, 'SOURCE_DOCUMENT_SET');
  assert.equal(persisted.deliverable?.status, 'FROZEN');
});

test('作者确认后只能由异人审核自动定版，并显式零变化交给M4', async () => {
  let clockTick = 0;
  const { runtime, contentStore, metadataStore } = readyFixture(() => (
    new Date(Date.parse('2026-08-18T11:00:00.000Z') + clockTick++).toISOString()
  ));
  const generated = await runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'lifecycle:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const submitted = await runtime.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'lifecycle:author', runId: prepared.runId,
    deliverableId: generated.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: generated.deliverable!.revision,
  });
  assert.equal(submitted.deliverable?.status, 'AWAITING_INDEPENDENT_REVIEW');
  assert.equal(submitted.deliverable?.linkState, 'LINKED');
  assert.equal(submitted.run?.timeline.at(-1)?.type, 'REVIEW_SUBMITTED');

  await assert.rejects(runtime.execute({
    type: 'REVIEW_AND_FREEZE', commandId: 'lifecycle:self-review', runId: prepared.runId,
    deliverableId: submitted.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: submitted.deliverable!.revision,
  }), /异于作者/);

  const frozen = await runtime.execute({
    type: 'REVIEW_AND_FREEZE', commandId: 'lifecycle:review', runId: prepared.runId,
    deliverableId: submitted.deliverable!.deliverableId, actorUserId: 'user_zhiyuan_xu',
    expectedDeliverableRevision: submitted.deliverable!.revision,
  });
  assert.equal(frozen.deliverable?.status, 'FROZEN');
  assert.equal(frozen.deliverable?.linkState, 'LINKED');
  assert.equal(frozen.run?.timeline.at(-1)?.type, 'DELIVERABLE_FROZEN');

  const handedOff = await runtime.execute({
    type: 'HANDOFF_ZERO_DELTA_TO_M4', commandId: 'lifecycle:handoff', runId: prepared.runId,
    deliverableId: frozen.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: frozen.deliverable!.revision,
  });
  assert.equal(handedOff.deliverable?.status, 'HANDED_OFF');
  assert.equal(handedOff.deliverable?.linkState, 'LINKED');
  assert.equal(handedOff.run?.timeline.at(-1)?.type, 'MODELING_HANDOFF_COMPLETED');
  assert.equal(handedOff.receipt?.kind, 'ZERO_DELTA_MODELING_HANDOFF');
  assert.equal(handedOff.receipt?.protectedArtifactId, guanyijiaFrozenModelingArtifact.artifactId);
  assert.equal(handedOff.receipt?.protectedCatalogId, baseline.catalogId);
  assert.equal(handedOff.receipt?.semanticPayloadSha256, baseline.semanticPayloadSha256);
  assert.equal(handedOff.receipt?.zeroDeltaReportRef, frozen.deliverable!.zeroDeltaReportRef);

  const validMetadata = metadataStore.raw!;
  const forgedApprovalMetadata = JSON.parse(validMetadata);
  forgedApprovalMetadata.deliverables[0].reviewerApproval.actorUserId = 'user_eve';
  forgedApprovalMetadata.deliverables[0].reviewerApproval.reviewedAt = '2026-08-18T12:00:00.000Z';
  metadataStore.raw = JSON.stringify(forgedApprovalMetadata);
  await assert.rejects(
    runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' }),
    /Run事件.*交付metadata/u,
  );

  const forgedActorReceiptRef = await contentStore.put(canonicalModelingJson({
    ...handedOff.receipt,
    handedOffBy: 'user_eve',
  }));
  const forgedActorMetadata = JSON.parse(validMetadata);
  forgedActorMetadata.deliverables[0].handoffReceiptRef = forgedActorReceiptRef;
  metadataStore.raw = JSON.stringify(forgedActorMetadata);
  await assert.rejects(
    runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' }),
    /Run事件.*交付metadata/u,
  );

  const swappedReceiptRef = await contentStore.put(canonicalModelingJson({
    ...handedOff.receipt,
    runId: 'run-from-another-project',
    deliverableId: 'deliverable-from-another-project',
  }));
  const tamperedMetadata = JSON.parse(validMetadata);
  tamperedMetadata.deliverables[0].handoffReceiptRef = swappedReceiptRef;
  metadataStore.raw = JSON.stringify(tamperedMetadata);
  await assert.rejects(
    runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' }),
    /Receipt.*identity/u,
  );

  const forgedReceiptRef = await contentStore.put(canonicalModelingJson({
    ...handedOff.receipt,
    protectedArtifactId: 'artifact-forged-protected',
    artifactId: 'artifact-forged-standardization',
    artifactMarkdownSha256: 'a'.repeat(64),
    semanticPayloadSha256: 'b'.repeat(64),
  }));
  const forgedMetadata = JSON.parse(validMetadata);
  forgedMetadata.deliverables[0].handoffReceiptRef = forgedReceiptRef;
  metadataStore.raw = JSON.stringify(forgedMetadata);
  await assert.rejects(
    runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' }),
    /Receipt.*Artifact/u,
  );
});

test('新交付物由作者确认结果后直接定版，不生成独立审批记录', async () => {
  const { runtime } = readyFixture('2026-08-18T11:45:00.000Z');
  const generated = await runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'author-freeze:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });

  const frozen = await runtime.execute({
    type: 'AUTHOR_CONFIRM_AND_FREEZE', commandId: 'author-freeze:confirm', runId: prepared.runId,
    deliverableId: generated.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: generated.deliverable!.revision,
  });

  assert.equal(frozen.deliverable?.status, 'FROZEN');
  assert.equal(frozen.deliverable?.linkState, 'LINKED');
  assert.equal(frozen.deliverable?.authorFreezeConfirmation?.actorUserId, 'user_bo_gao');
  assert.equal(frozen.deliverable?.authorFreezeConfirmation?.inputDeliverableRevision, generated.deliverable!.revision);
  assert.equal(frozen.deliverable?.reviewerApproval, undefined);
  assert.equal(frozen.run?.timeline.at(-1)?.type, 'DELIVERABLE_FROZEN');
});

test('已登记的缺口可定版，并通过标准化文档交给AI建模且结构化排除', async () => {
  const { runtime } = readyFixture('2026-08-18T11:50:00.000Z');
  const generated = await runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'gap-handoff:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const frozen = await runtime.execute({
    type: 'AUTHOR_CONFIRM_AND_FREEZE', commandId: 'gap-handoff:freeze', runId: prepared.runId,
    deliverableId: generated.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: generated.deliverable!.revision,
  });
  assert.equal(frozen.deliverable?.status, 'FROZEN');
  assert.ok(frozen.deliverable?.modelingEligibilityRef);

  const handedOff = await runtime.execute({
    type: 'HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING', commandId: 'gap-handoff:handoff',
    runId: prepared.runId, deliverableId: frozen.deliverable!.deliverableId,
    actorUserId: 'user_bo_gao', expectedDeliverableRevision: frozen.deliverable!.revision,
  });
  assert.equal(handedOff.deliverable?.status, 'HANDED_OFF');
  assert.equal(handedOff.receipt?.kind, 'STANDARDIZATION_DOCUMENT_HANDOFF');
  assert.ok(handedOff.modelingEligibility, '交接命令结果必须带回当前可建模结论，供前端立即打开 AI 建模。');
  assert.ok(handedOff.modelingEligibility!.excludedCandidateIds.length > 0);
  assert.ok(handedOff.receipt?.excludedConclusionCount && handedOff.receipt.excludedConclusionCount > 0);
  assert.ok(handedOff.receipt?.eligibleConclusionCount && handedOff.receipt.eligibleConclusionCount > 0);
});

test('三项来源差异均登记为缺口时仍可定版，且不会进入建模候选', async () => {
  const fixture = readyFixture('2026-08-18T11:53:00.000Z');
  const initialRun = (await fixture.runs.read(prepared.runId))!;
  const pointerStorage = new MemoryStorage();
  pointerStorage.setItem(guanyijiaActiveRunPointerStorageKeyFor(initialRun.batchId), JSON.stringify({
    schemaVersion: 1,
    projectId: 'guanyijia_erp',
    storyId: 'guanyijia-five-source-v1',
    batchId: initialRun.batchId,
    runId: initialRun.runId,
    commandFingerprints: {},
  }));
  const workbench = createGuanyijiaWorkbenchRuntime({
    pointerStorage,
    standardizationRuns: fixture.runs,
    sourceDocuments: fixture.sourceDocuments,
    story: fixture.story,
    contentStore: fixture.contentStore,
    now: () => '2026-08-18T11:53:00.000Z',
  });
  const replaceWithGap = async (conflictId: string) => {
    const run = (await fixture.runs.read(prepared.runId))!;
    const preview = await workbench.previewCurrentConflict({
      runId: run.runId, conflictId, strategy: 'DEFER_AS_GAP',
    });
    return workbench.execute({
      type: 'REPLACE_RESOLVED_CONFLICT',
      commandId: `all-gaps:${conflictId}`,
      expectedRevision: run.revision,
      actorUserId: 'user_bo_gao',
      conflictId,
      strategy: 'DEFER_AS_GAP',
      reason: '现有资料不足以形成正式结论，登记为资料缺口。',
      expectedHunkSha256: preview.hunk.hunkSha256,
    });
  };
  await replaceWithGap('gyj-conflict-debt-schema');
  const afterNegative = await replaceWithGap('gyj-conflict-negative-stock');
  const generated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'all-gaps:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: afterNegative.run!.revision,
  });
  const frozen = await fixture.runtime.execute({
    type: 'AUTHOR_CONFIRM_AND_FREEZE', commandId: 'all-gaps:freeze', runId: prepared.runId,
    deliverableId: generated.deliverable!.deliverableId,
    actorUserId: 'user_bo_gao', expectedDeliverableRevision: generated.deliverable!.revision,
  });

  assert.equal(frozen.deliverable?.status, 'FROZEN');
  assert.ok(frozen.modelingEligibility?.excludedCandidateIds.length);
  assert.ok(frozen.modelingEligibility?.conclusions.some((item) => item.eligibility === 'EXCLUDED_GAP'));
});

test('结果生成后重新处理来源差异会保留旧文档，并要求生成新的结果才能定版', async () => {
  const fixture = readyFixture('2026-08-18T11:55:00.000Z');
  const first = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'replace-after-output:first', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  assert.ok(first.deliverable);

  const pointerStorage = new MemoryStorage();
  pointerStorage.setItem(guanyijiaActiveRunPointerStorageKeyFor(first.run!.batchId), JSON.stringify({
    schemaVersion: 1,
    projectId: 'guanyijia_erp',
    storyId: 'guanyijia-five-source-v1',
    batchId: first.run!.batchId,
    runId: first.run!.runId,
    commandFingerprints: {},
  }));
  const workbench = createGuanyijiaWorkbenchRuntime({
    pointerStorage,
    standardizationRuns: fixture.runs,
    sourceDocuments: fixture.sourceDocuments,
    story: fixture.story,
    contentStore: fixture.contentStore,
    now: () => '2026-08-18T11:56:00.000Z',
  });
  const preview = await workbench.previewCurrentConflict({
    runId: prepared.runId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'DEFER_AS_GAP',
  });
  const replaced = await workbench.execute({
    type: 'REPLACE_RESOLVED_CONFLICT',
    commandId: 'replace-after-output:debt',
    expectedRevision: first.run!.revision,
    actorUserId: 'user_bo_gao',
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'DEFER_AS_GAP',
    reason: '部署结构和源码记录尚不能确认同一版本，登记为资料缺口。',
    expectedHunkSha256: preview.hunk.hunkSha256,
  });

  assert.equal(replaced.run?.status, 'READY_FOR_OUTPUT');
  assert.equal(replaced.run?.deliverableId, undefined);
  assert.ok(replaced.run?.supersededDeliverableIds?.includes(first.deliverable!.deliverableId));
  assert.equal(replaced.run?.timeline.at(-2)?.type, 'DELIVERABLE_SUPERSEDED');
  assert.equal(replaced.run?.timeline.at(-1)?.type, 'CONFLICT_DECISION_REPLACED');
  assert.equal((await fixture.runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' })).deliverable, null);

  const historicalDocument = await fixture.runtime.readContent({
    contentRef: first.deliverable!.mergedDocumentRef,
    actorUserId: 'user_bo_gao',
    limit: 1000,
  });
  assert.ok(historicalDocument.items.join('').includes('管伊佳'), '被取代的结果必须仍可从时间线只读回看');

  const regenerated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'replace-after-output:regenerate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: replaced.run!.revision,
  });
  assert.ok(regenerated.deliverable);
  assert.notEqual(regenerated.deliverable!.deliverableId, first.deliverable!.deliverableId);
  assert.equal(regenerated.run?.deliverableId, regenerated.deliverable!.deliverableId);
});

test('缺来源、缺决定和受保护基线漂移均在生成前 fail closed', async () => {
  const missingSource = readyFixture('2026-08-18T11:00:00.000Z', {
    projectRun: (run) => ({ ...run, sources: run.sources.slice(0, -1) }),
  });
  await assert.rejects(missingSource.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'negative:source', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  }), /精确来源集合/);

  const missingDecision = readyFixture('2026-08-18T11:00:00.000Z', {
    projectResolutions: (values) => values.slice(1),
  });
  await assert.rejects(missingDecision.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'negative:decision', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  }), /每个已引入冲突/);

  const drifted = structuredClone(baseline);
  drifted.expectedCounts.fields += 1;
  const baselineDrift = readyFixture('2026-08-18T11:00:00.000Z', { baseline: drifted });
  await assert.rejects(baselineDrift.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'negative:baseline', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  }), /Counts 漂移/);

  const artifactIdentityDrift = structuredClone(baseline);
  artifactIdentityDrift.artifact.artifactId = 'artifact-guanyijia-v1-evil';
  const identityFixture = readyFixture('2026-08-18T11:00:00.000Z', {
    baseline: artifactIdentityDrift,
  });
  await assert.rejects(identityFixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'negative:artifact-id', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  }), /Artifact ID 漂移/);
});

test('语义Patch改变debt会产生具体path，不能伪装为零变化', async () => {
  const fixture = readyFixture();
  const run = (await fixture.runs.read(prepared.runId))!;
  const resolutions = await fixture.conflictResolutions.list(run);
  const mutant = structuredClone(resolutions);
  const debt = mutant.find((item) => item.hunk.conflictId === 'gyj-conflict-debt-schema')!;
  const debtDisposition = debt.structuredPatch.objectDispositionOperations.find(
    (item) => item.value.objectRef.objectId === 'receivable_debt',
  )!;
  debtDisposition.value.disposition = 'KEEP';
  const sections = Object.fromEntries([
    'OVERVIEW', 'GOAL', 'OBJECT', 'ACTIVITY', 'FIELD', 'RELATION', 'METRIC', 'QUESTION', 'UNRESOLVED',
  ].map((key) => [key, `mutant ${key}`])) as never;
  const projected = await fixture.modelingProjector.project({
    run, baseline: structuredClone(baseline), resolutions: mutant,
    mergedDocument: {
      schemaVersion: 1, runId: run.runId,
      sourceSnapshotIds: baseline.expectedSourceIds, sections, markdown: 'mutant',
    },
  });
  assert.ok(projected.zeroDeltaReport.semanticDifferences.some((item) => (
    item.path.includes('exclusions') && item.before !== item.after
  )));
  assert.notEqual(projected.artifact.semanticPayload?.sha256, baseline.semanticPayloadSha256);
  assert.ok(semanticPathDifferences(
    baseline.artifact.semanticPayload!.data,
    projected.semanticPayload,
  ).some((item) => item.path.includes('exclusions')));
});

test('定版前重新读取全部refs；内容篡改会拒绝且不冻结', async () => {
  const fixture = readyFixture();
  const generated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'tamper:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const submitted = await fixture.runtime.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'tamper:author', runId: prepared.runId,
    deliverableId: generated.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: generated.deliverable!.revision,
  });
  fixture.contentStore.values.set(submitted.deliverable!.modelingArtifactRef, '{}');
  await assert.rejects(fixture.runtime.execute({
    type: 'REVIEW_AND_FREEZE', commandId: 'tamper:review', runId: prepared.runId,
    deliverableId: submitted.deliverable!.deliverableId, actorUserId: 'user_zhiyuan_xu',
    expectedDeliverableRevision: submitted.deliverable!.revision,
  }), /校验和不一致/);
  const snapshot = await fixture.runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' });
  assert.equal(snapshot.deliverable?.status, 'AWAITING_INDEPENDENT_REVIEW');
});

test('作者确认前从来源 revision 重建九段，整链重算的伪造正文仍被拒绝', async () => {
  const fixture = readyFixture();
  const generated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'full-chain:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const deliverable = generated.deliverable!;
  const originalMerged = JSON.parse((await fixture.contentStore.get(deliverable.mergedDocumentRef))!);
  const forgedMerged = structuredClone(originalMerged);
  forgedMerged.sections.OVERVIEW = `${forgedMerged.sections.OVERVIEW}\n\n攻击者伪造的标准化正文。`;
  forgedMerged.markdown = forgedMerged.markdown.replace(
    originalMerged.sections.OVERVIEW,
    forgedMerged.sections.OVERVIEW,
  );
  const run = (await fixture.runs.read(prepared.runId))!;
  const resolutions = await fixture.conflictResolutions.list(run);
  const projected = await fixture.modelingProjector.project({
    run, baseline: structuredClone(baseline), resolutions, mergedDocument: forgedMerged,
  });
  const mergedDocumentRef = await fixture.contentStore.put(canonicalModelingJson(forgedMerged));
  const modelingArtifactRef = await fixture.contentStore.put(JSON.stringify(projected.artifact));
  const zeroDeltaReportRef = await fixture.contentStore.put(JSON.stringify(projected.zeroDeltaReport));
  const coreSha256 = `sha256:${sha256HexSync(canonicalModelingJson({
    schemaVersion: 1, runId: deliverable.runId, scenarioKey: deliverable.scenarioKey,
    protectedArtifactId: baseline.artifact.artifactId,
    protectedMarkdownSha256: baseline.markdownSha256,
    protectedSemanticPayloadSha256: baseline.semanticPayloadSha256,
    protectedCatalogId: baseline.catalogId,
    protectedCatalogFingerprint: baseline.catalogFingerprint,
    sourceManifestRef: deliverable.sourceManifestRef, mergedDocumentRef,
    decisionManifestRef: deliverable.decisionManifestRef,
    governanceAppendixRef: deliverable.governanceAppendixRef,
    modelingArtifactRef, zeroDeltaReportRef,
    modelingEligibilityRef: deliverable.modelingEligibilityRef,
  }))}`;
  const metadata = JSON.parse(fixture.metadataStore.raw!);
  Object.assign(metadata.deliverables[0], {
    mergedDocumentRef, modelingArtifactRef, zeroDeltaReportRef, coreSha256,
  });
  fixture.metadataStore.raw = JSON.stringify(metadata);
  await rewriteGeneratedRunEvent(fixture, metadata.deliverables[0]);

  await assert.rejects(fixture.runtime.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'full-chain:author', runId: prepared.runId,
    deliverableId: deliverable.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: deliverable.revision,
  }), /九段合并文档不是持久化来源 revision/);
});

test('治理附录notes必须是来源角色与决定的确定性唯一投影', async () => {
  const fixture = readyFixture();
  const generated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'appendix:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const deliverable = generated.deliverable!;
  const appendix = JSON.parse((await fixture.contentStore.get(deliverable.governanceAppendixRef))!);
  appendix.notes = ['Semantica 被攻击者伪造为正式根证据。'];
  const governanceAppendixRef = await fixture.contentStore.put(canonicalModelingJson(appendix));
  const coreSha256 = `sha256:${sha256HexSync(canonicalModelingJson({
    schemaVersion: 1, runId: deliverable.runId, scenarioKey: deliverable.scenarioKey,
    protectedArtifactId: baseline.artifact.artifactId,
    protectedMarkdownSha256: baseline.markdownSha256,
    protectedSemanticPayloadSha256: baseline.semanticPayloadSha256,
    protectedCatalogId: baseline.catalogId,
    protectedCatalogFingerprint: baseline.catalogFingerprint,
    sourceManifestRef: deliverable.sourceManifestRef,
    mergedDocumentRef: deliverable.mergedDocumentRef,
    decisionManifestRef: deliverable.decisionManifestRef,
    governanceAppendixRef,
    modelingArtifactRef: deliverable.modelingArtifactRef,
    zeroDeltaReportRef: deliverable.zeroDeltaReportRef,
    modelingEligibilityRef: deliverable.modelingEligibilityRef,
  }))}`;
  const metadata = JSON.parse(fixture.metadataStore.raw!);
  Object.assign(metadata.deliverables[0], { governanceAppendixRef, coreSha256 });
  fixture.metadataStore.raw = JSON.stringify(metadata);
  await rewriteGeneratedRunEvent(fixture, metadata.deliverables[0]);

  await assert.rejects(fixture.runtime.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'appendix:author', runId: prepared.runId,
    deliverableId: deliverable.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: deliverable.revision,
  }), /治理附录不是来源角色与决定的唯一投影/);
});

test('commandId幂等、CAS并发和生成事件失败均不复制产物', async () => {
  const saga = readyFixture('2026-08-18T11:00:00.000Z', { failAppendOnce: 'DELIVERABLE_GENERATED' });
  const command = {
    type: 'GENERATE_DELIVERABLE' as const, commandId: 'saga:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  };
  await assert.rejects(saga.runtime.execute(command), /injected/);
  const pending = await saga.runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' });
  assert.equal(pending.deliverable?.linkState, 'GENERATED_EVENT_PENDING');
  const recovered = await saga.runtime.execute(command);
  assert.equal(recovered.deliverable?.linkState, 'LINKED');
  const replayed = await saga.runtime.execute(command);
  assert.equal(replayed.deliverable?.deliverableId, recovered.deliverable?.deliverableId);
  assert.equal(saga.metadataStore.version, 2);
  await assert.rejects(saga.runtime.execute({ ...command, actorUserId: 'user_zhiyuan_xu' }), /commandId已用于不同/);

  const concurrent = readyFixture();
  const results = await Promise.allSettled([
    concurrent.runtime.execute({ ...command, commandId: 'cas:one' }),
    concurrent.runtime.execute({ ...command, commandId: 'cas:two' }),
  ]);
  assert.equal(results.filter((result) => result.status === 'fulfilled').length, 1);
  assert.equal(results.filter((result) => result.status === 'rejected').length, 1);
  assert.match(String(results.find((result) => result.status === 'rejected')!.reason), /其他窗口更新/);
});

test('pending saga不能被同identity但内容伪造的Run事件劫持', async () => {
  const fixture = readyFixture('2026-08-18T11:00:00.000Z', {
    failAppendOnce: 'DELIVERABLE_GENERATED',
  });
  const command = {
    type: 'GENERATE_DELIVERABLE' as const, commandId: 'saga:hijack', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  };
  await assert.rejects(fixture.runtime.execute(command), /injected/);
  const pending = await fixture.runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' });
  await fixture.runs.execute({
    type: 'MARK_DELIVERABLE_GENERATED', commandId: 'attacker:same-identity', runId: prepared.runId,
    expectedRevision: prepared.runRevision, actor: { userId: 'user_bo_gao' }, deliveryCapability,
    deliverableId: pending.deliverable!.deliverableId,
    payload: canonicalModelingJson({
      schemaVersion: 1, deliverableId: pending.deliverable!.deliverableId,
      coreSha256: `sha256:${'a'.repeat(64)}`,
      sourceManifestRef: `sha256:${'b'.repeat(64)}`,
      mergedDocumentRef: `sha256:${'c'.repeat(64)}`,
      decisionManifestRef: `sha256:${'d'.repeat(64)}`,
      governanceAppendixRef: `sha256:${'e'.repeat(64)}`,
      modelingArtifactRef: `sha256:${'f'.repeat(64)}`,
      zeroDeltaReportRef: `sha256:${'1'.repeat(64)}`,
    }),
  });

  await assert.rejects(fixture.runtime.execute(command), /Run事件载荷与交付metadata不一致/);
  await assert.rejects(
    fixture.runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' }),
    /Run事件载荷与交付metadata不一致/,
  );
});

test('定版事件失败保持FROZEN pending，恢复前禁止handoff且同命令可补登', async () => {
  let reviewerRole: WorkspaceRole = 'REVIEWER';
  const fixture = readyFixture('2026-08-18T11:00:00.000Z', {
    failAppendOnce: 'DELIVERABLE_FROZEN',
    accessForActor: (actorUserId) => ({
      active: true,
      role: actorUserId === 'user_bo_gao' ? 'EDITOR' : reviewerRole,
    }),
  });
  const generated = await fixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'freeze-saga:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const submitted = await fixture.runtime.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'freeze-saga:author', runId: prepared.runId,
    deliverableId: generated.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: generated.deliverable!.revision,
  });
  const review = {
    type: 'REVIEW_AND_FREEZE' as const, commandId: 'freeze-saga:review', runId: prepared.runId,
    deliverableId: submitted.deliverable!.deliverableId, actorUserId: 'user_zhiyuan_xu',
    expectedDeliverableRevision: submitted.deliverable!.revision,
  };
  await assert.rejects(fixture.runtime.execute(review), /injected/);
  const pending = await fixture.runtime.read({ runId: prepared.runId, actorUserId: 'user_bo_gao' });
  assert.equal(pending.deliverable?.status, 'FROZEN');
  assert.equal(pending.deliverable?.linkState, 'FROZEN_EVENT_PENDING');
  assert.deepEqual(pending.pendingCommand, review);
  await assert.rejects(fixture.runtime.execute({
    type: 'HANDOFF_ZERO_DELTA_TO_M4', commandId: 'freeze-saga:early-handoff', runId: prepared.runId,
    deliverableId: pending.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: pending.deliverable!.revision,
  }), /前序Run事件尚未补登/);
  reviewerRole = 'VIEWER';
  await assert.rejects(fixture.runtime.execute(review), /Reviewer\/Admin/);
  reviewerRole = 'REVIEWER';
  const recovered = await fixture.runtime.execute(review);
  assert.equal(recovered.deliverable?.status, 'FROZEN');
  assert.equal(recovered.deliverable?.linkState, 'LINKED');
  assert.equal(recovered.run?.timeline.at(-1)?.type, 'DELIVERABLE_FROZEN');
});

test('Run事件已追加但最终LINKED CAS失败时freeze与handoff均按exact事件恢复', async () => {
  const freezeFixture = readyFixture('2026-08-18T11:00:00.000Z', { failDeliverableCasAt: 6 });
  const freezeGenerated = await freezeFixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'post-link-freeze:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const freezeSubmitted = await freezeFixture.runtime.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'post-link-freeze:author', runId: prepared.runId,
    deliverableId: freezeGenerated.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: freezeGenerated.deliverable!.revision,
  });
  const freezeCommand = {
    type: 'REVIEW_AND_FREEZE' as const, commandId: 'post-link-freeze:review', runId: prepared.runId,
    deliverableId: freezeSubmitted.deliverable!.deliverableId, actorUserId: 'user_zhiyuan_xu',
    expectedDeliverableRevision: freezeSubmitted.deliverable!.revision,
  };
  await assert.rejects(freezeFixture.runtime.execute(freezeCommand), /其他窗口更新/);
  const freezePending = await freezeFixture.runtime.read({
    runId: prepared.runId, actorUserId: 'user_zhiyuan_xu',
  });
  assert.equal(freezePending.deliverable?.linkState, 'FROZEN_EVENT_PENDING');
  assert.equal(freezePending.run?.status, 'FROZEN');
  const freezeRecovered = await freezeFixture.runtime.execute(freezeCommand);
  assert.equal(freezeRecovered.deliverable?.linkState, 'LINKED');
  assert.equal(freezeRecovered.run?.status, 'FROZEN');

  const handoffFixture = readyFixture('2026-08-18T11:00:00.000Z', { failDeliverableCasAt: 8 });
  const handoffGenerated = await handoffFixture.runtime.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'post-link-handoff:generate', runId: prepared.runId,
    actorUserId: 'user_bo_gao', expectedRunRevision: prepared.runRevision,
  });
  const handoffSubmitted = await handoffFixture.runtime.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'post-link-handoff:author', runId: prepared.runId,
    deliverableId: handoffGenerated.deliverable!.deliverableId, actorUserId: 'user_bo_gao',
    expectedDeliverableRevision: handoffGenerated.deliverable!.revision,
  });
  const handoffFrozen = await handoffFixture.runtime.execute({
    type: 'REVIEW_AND_FREEZE', commandId: 'post-link-handoff:review', runId: prepared.runId,
    deliverableId: handoffSubmitted.deliverable!.deliverableId, actorUserId: 'user_zhiyuan_xu',
    expectedDeliverableRevision: handoffSubmitted.deliverable!.revision,
  });
  const handoffCommand = {
    type: 'HANDOFF_ZERO_DELTA_TO_M4' as const, commandId: 'post-link-handoff:handoff',
    runId: prepared.runId, deliverableId: handoffFrozen.deliverable!.deliverableId,
    actorUserId: 'user_bo_gao', expectedDeliverableRevision: handoffFrozen.deliverable!.revision,
  };
  await assert.rejects(handoffFixture.runtime.execute(handoffCommand), /其他窗口更新/);
  const handoffPending = await handoffFixture.runtime.read({
    runId: prepared.runId, actorUserId: 'user_bo_gao',
  });
  assert.equal(handoffPending.deliverable?.linkState, 'HANDOFF_EVENT_PENDING');
  assert.equal(handoffPending.run?.status, 'HANDED_OFF');
  const handoffRecovered = await handoffFixture.runtime.execute(handoffCommand);
  assert.equal(handoffRecovered.deliverable?.linkState, 'LINKED');
  assert.equal(handoffRecovered.run?.status, 'HANDED_OFF');
});

test('M4 discriminated zero-delta路由不调用任何草稿或发布端口且保持Catalog identity', () => {
  const calls: string[] = [];
  const catalog = {
    catalogId: baseline.catalogId, catalogVersion: 'V1', fingerprint: baseline.catalogFingerprint,
    modelSpaceId: 'guanyijia_erp', data: baseline.catalogData,
    authorUserId: 'system', reviewerUserIds: [], publisherUserId: 'system',
    publishedAt: '2026-08-18T00:00:00.000Z', requestId: 'protected-v1',
  };
  const receipt = {
    kind: 'ZERO_DELTA_MODELING_HANDOFF' as const, schemaVersion: 1 as const,
    receiptId: 'zero-delta-receipt', runId: prepared.runId, deliverableId: 'deliverable',
    protectedArtifactId: baseline.artifact.artifactId, protectedCatalogId: baseline.catalogId,
    artifactId: 'new-artifact', artifactMarkdownSha256: 'a'.repeat(64),
    semanticPayloadSha256: baseline.semanticPayloadSha256,
    governanceAppendixRef: `sha256:${'b'.repeat(64)}` as ContentReference,
    zeroDeltaReportRef: `sha256:${'c'.repeat(64)}` as ContentReference,
    handedOffBy: 'author', handedOffAt: '2026-08-18T00:00:00.000Z',
  };
  const view = projectZeroDeltaM4View({
    receipt, catalog, draftCount: 3,
    mutationPorts: {
      openDraft() { calls.push('openDraft'); }, saveDraft() { calls.push('saveDraft'); },
      submitDraft() { calls.push('submitDraft'); }, publishDraft() { calls.push('publishDraft'); },
    },
  });
  assert.deepEqual(calls, []);
  assert.equal(view.catalog, catalog);
  assert.equal(view.catalog.fingerprint, baseline.catalogFingerprint);
  assert.equal(view.draftCount, 3);
  assert.equal(view.semanticChangeCount, 0);
});

test('Workbench与M4只暴露当前状态唯一主动作，右侧检查器保持只读', () => {
  const workbench = readFileSync(new URL('../data-standardization/guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
  const assistantPanel = readFileSync(new URL('../data-standardization/review-assistant-panel.tsx', import.meta.url), 'utf8');
  const inspector = readFileSync(new URL('../data-standardization/standardization-facts-inspector.tsx', import.meta.url), 'utf8');
  const timeline = readFileSync(new URL('../data-standardization/standardization-timeline.tsx', import.meta.url), 'utf8');
  const modeling = readFileSync(new URL('../ai-modeling/ai-modeling-page.tsx', import.meta.url), 'utf8');
  const layout = readFileSync(new URL('../../layout/AppLayout.tsx', import.meta.url), 'utf8');
  assert.match(workbench, /生成标准化结果/u);
  assert.match(workbench, /确认结果并定版/u);
  assert.match(workbench, /AUTHOR_CONFIRM_AND_FREEZE/u);
  assert.doesNotMatch(workbench, /提交独立审核|通过审核并定版/u);
  assert.match(workbench, /HANDOFF_STANDARDIZATION_DOCUMENT_TO_MODELING/u);
  assert.match(workbench, /前往 AI 建模/u);
  assert.match(workbench, /HANDOFF_ZERO_DELTA_TO_M4/u, '历史零变化交接仍可读取');
  assert.match(workbench, /pendingCommand/u);
  assert.match(workbench, /继续完成上一步/u);
  assert.equal(guanyijiaDeliverableContentLabelFor('mergedDocumentRef'), '合并文档');
  assert.match(timeline, /OPEN_DELIVERABLE/u);
  assert.match(timeline, /查看\$\{item\.title\}/u);
  assert.match(workbench, /const reviewAssistant = <ReviewAssistantPanel/u);
  assert.equal([...workbench.matchAll(/<ReviewAssistantPanel/g)].length, 1);
  assert.match(workbench, /<main[\s\S]*?className="guanyijia-workbench-thread"[\s\S]*?>[\s\S]*?\{reviewAssistant\}\s*<\/main>/u);
  assert.doesNotMatch(workbench, /deliveryPhase\s*&&\s*<ReviewAssistantPanel/u);
  assert.doesNotMatch(assistantPanel, /guanyijia-assistant-current-context/u);
  assert.doesNotMatch(inspector, /executeDeliverable|onClick=.*AUTHOR_CONFIRM|type="primary"/u);
  assert.match(modeling, /zeroDeltaReceipt \? <section/u);
  assert.match(modeling, /modelingDocument && standardizationReceipt \? <section/u);
  assert.match(modeling, /生成建模候选/u);
  assert.match(modeling, /语义变化/u);
  const zeroDeltaStart = modeling.indexOf('zeroDeltaReceipt ? <section');
  const zeroDeltaBranch = modeling.slice(
    zeroDeltaStart,
    modeling.indexOf('</section> : modelingDocument && standardizationReceipt', zeroDeltaStart),
  );
  assert.doesNotMatch(zeroDeltaBranch, /开始建模|加入个人草稿|type="primary"/u);
  assert.match(modeling, /zeroDeltaReceipt && formalCatalog\s*\? <CollaborationCatalogInspector/u);
  assert.match(layout, /handoff\.kind === 'ZERO_DELTA_MODELING_HANDOFF'[\s\S]*setViewMode\('FORMAL'\)[\s\S]*selectVersion\('V1'\)/u);
});

test('交付深模块不导入legacy runtime、Fixture或按projectId选择结果', () => {
  const moduleFiles = ['runtime.ts', 'types.ts', 'semantic-projection.ts'];
  const source = moduleFiles.map((file) => readFileSync(new URL(file, import.meta.url), 'utf8')).join('\n');
  assert.doesNotMatch(source, /document-alignment|modeling-document-bridge\/runtime|fixtures?\.ts/u);
  assert.doesNotMatch(source, /projectId\s*===\s*['"]|switch\s*\([^)]*projectId/u);
  assert.doesNotMatch(source, /artifact-guanyijia-v1-40c8572864bd|catalog_guanyijia_v1/u);
});

test('浏览器交付物metadata使用独立IndexedDB CAS，不回落localStorage', async () => {
  let openCount = 0;
  const indexedDB = {
    open() { openCount += 1; throw new Error('DELIVERABLE_IDB_PROBE'); },
  } as unknown as IDBFactory;
  const store = createBrowserDeliverableMetadataStore(indexedDB);
  await assert.rejects(store.read(), /DELIVERABLE_IDB_PROBE/);
  assert.equal(openCount, 1);
});
