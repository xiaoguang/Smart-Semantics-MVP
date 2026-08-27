import { guanyijiaV1 } from '../../../src/features/collaboration/fixtures.ts';
import { sha256HexSync } from '../../../src/features/ai-modeling/sha256.ts';
import {
  createGuanyijiaWorkbenchRuntime,
  validateGuanyijiaCorroborationReceipt,
  validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions,
  validateGuanyijiaResolutionArtifactAgainstPersistedRevisions,
  type GuanyijiaWorkbenchCommand,
} from '../../../src/features/data-standardization/guanyijia-workbench-runtime.ts';
import { createGuanyijiaStandardizationStory } from '../../../src/features/guanyijia-standardization-story/index.ts';
import { guanyijiaFrozenModelingArtifact } from '../../../src/features/modeling-document-bridge/guanyijia-modeling-baseline.ts';
import { createMemoryContentStore, createSourceDocumentRuntime } from '../../../src/features/source-documents/runtime.ts';
import { createStandardizationDeliverableRuntime } from '../../../src/features/standardization-deliverable/runtime.ts';
import { createStandardizationModelingProjector } from '../../../src/features/standardization-deliverable/semantic-projection.ts';
import type {
  DeliverableMetadataStore,
  DeliverableSnapshot,
  ProtectedBaseline,
  StandardizationRunReader,
} from '../../../src/features/standardization-deliverable/types.ts';
import { createStandardizationRunRuntime } from '../../../src/features/standardization-run/runtime.ts';
import type { StandardizationRunCommand, StandardizationMetadataStore } from '../../../src/features/standardization-run/types.ts';
import {
  createReviewPersistenceFaultController,
  withReviewContentCacheFaultpoints,
  withStandardizationMetadataFaultpoints,
  withStandardizationRunReaderFaultpoints,
} from '../../../src/features/review-window/faultpoint-adapters.ts';
import {
  ReviewWindowError,
  createGeneratedReviewWindowIndex,
  createStandardizationReviewWindow,
} from '../../../src/features/review-window/index.ts';

class MemoryStorage implements Pick<Storage, 'getItem' | 'setItem'> {
  readonly values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

class MemoryMetadataStore implements StandardizationMetadataStore, DeliverableMetadataStore {
  private revision = 0;
  private raw: string | null = null;
  async read() { return { version: String(this.revision), raw: this.raw }; }
  async compareAndSet(expectedVersion: string, nextRaw: string) {
    if (expectedVersion !== String(this.revision)) return false;
    this.raw = nextRaw;
    this.revision += 1;
    return true;
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

function workbenchCommand(
  type: GuanyijiaWorkbenchCommand['type'], expectedRevision: number, commandId: string,
): GuanyijiaWorkbenchCommand {
  return { type, expectedRevision, commandId, actorUserId: 'cp8-author' } as GuanyijiaWorkbenchCommand;
}

async function prepareCasScenario() {
  const faults = createReviewPersistenceFaultController();
  const metadataStore = new MemoryMetadataStore();
  const contentStore = createMemoryContentStore();
  const runs = createStandardizationRunRuntime({
    metadataStore: withStandardizationMetadataFaultpoints(metadataStore, faults), contentStore,
  });
  const created = await runs.execute({
    type: 'CREATE_RUN', commandId: 'cp8-cas:create', expectedRevision: 0,
    actor: { userId: 'cp8-author' }, projectId: 'cp8-capacity-project',
    scenarioKey: 'cp8-capacity', batchId: 'cp8-capacity-batch',
    sources: [{ sourceId: 'cp8-source', sourceName: '容量来源' }],
  });
  return {
    runId: created.runId,
    async revision() { return (await runs.read(created.runId))!.revision; },
    async advanceWinner() {
      const before = (await runs.read(created.runId))!;
      return runs.execute({
        type: 'START_NEXT_SOURCE', commandId: 'cp8-cas:winner', runId: created.runId,
        expectedRevision: before.revision, actor: { userId: 'cp8-author' },
      });
    },
    async reload() { return (await runs.read(created.runId))!; },
    async injectStaleWrite(staleRevision?: number) {
      const before = (await runs.read(created.runId))!;
      faults.arm('STANDARDIZATION_CAS_STALE');
      let rejected = false;
      try {
        await runs.execute({
          type: 'START_NEXT_SOURCE', commandId: 'cp8-cas:stale', runId: created.runId,
          expectedRevision: staleRevision ?? before.revision, actor: { userId: 'cp8-author' },
        });
      } catch (cause) {
        rejected = cause instanceof Error
          && (/其他窗口更新/u.test(cause.message) || /revision已变化/u.test(cause.message));
      }
      if (!rejected) throw new Error('真实并发旧revision写入未被拒绝');
      return (await runs.read(created.runId))!;
    },
  };
}

async function prepareQuotaScenario() {
  const faults = createReviewPersistenceFaultController();
  const metadataStore = new MemoryMetadataStore();
  const contentStore = createMemoryContentStore();
  const runs = createStandardizationRunRuntime({ metadataStore, contentStore });
  const run = await runs.execute({
    type: 'CREATE_RUN', commandId: 'cp8-quota:create', expectedRevision: 0,
    actor: { userId: 'cp8-author' }, projectId: 'cp8-capacity-project',
    scenarioKey: 'cp8-capacity', batchId: 'cp8-capacity-quota-batch',
    sources: [{ sourceId: 'cp8-source', sourceName: '容量来源' }],
  });
  const body = '容量 harness 已校验正文';
  const contentRef = `sha256:${sha256HexSync(body)}` as const;
  const cache = withReviewContentCacheFaultpoints({
    async read() { return null; },
    async write() {},
    async estimate() { return { usage: 9_000_000, quota: 10_000_000 }; },
  }, faults);
  const reviewWindow = createStandardizationReviewWindow({
    cursorSecret: 'cp8-recovery-quota', index: createGeneratedReviewWindowIndex({ DOCUMENT_BLOCKS: 1 }),
    bodies: { async read(ref) { return ref === contentRef ? body : null; } }, cache,
  });
  return {
    async revision() { return (await runs.read(run.runId))!.revision; },
    async injectQuota() {
      faults.arm('REVIEW_CACHE_QUOTA');
      try {
        await reviewWindow.readContent({
          runId: run.runId, contentRef, expectedSha256: contentRef.slice('sha256:'.length),
        });
      } catch (cause) {
        if (cause instanceof ReviewWindowError && cause.code === 'QUOTA_EXCEEDED') return cause;
        throw cause;
      }
      throw new Error('真实review-window quota failpoint未被投影');
    },
  };
}

async function prepareFreezeScenario() {
  const metadataStorage = new MemoryStorage();
  const pointerStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const sourceDocuments = createSourceDocumentRuntime({ metadataStorage, contentStore });
  const story = createGuanyijiaStandardizationStory();
  const deliveryCapability = {};
  const runs = createStandardizationRunRuntime({
    metadataStorage, contentStore, deliveryCapability,
    resolutionArtifactValidator: (artifact, run) => validateGuanyijiaResolutionArtifactAgainstPersistedRevisions({
      artifact, run, sourceDocuments, story,
    }),
    corroborationReceiptValidator: (receipt, run) => validateGuanyijiaCorroborationReceipt({ receipt, run, story }),
    corroborationReceiptSetValidator: (projection, run) => (
      validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions({
        projection, run, sourceDocuments, story,
      })
    ),
    now: () => '2026-08-18T12:00:00.000Z',
  });
  const workbench = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: runs, sourceDocuments, story, contentStore,
    now: () => '2026-08-18T12:00:00.000Z',
  });
  let snapshot = await workbench.execute(workbenchCommand('START_RUN', 0, 'cp8-freeze:start'));
  const readAndReview = async (label: string) => {
    snapshot = await workbench.execute(workbenchCommand(
      'READ_NEXT_SOURCE', snapshot.run!.revision, `cp8-freeze:read:${label}`,
    ));
    snapshot = await workbench.execute(workbenchCommand(
      'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, `cp8-freeze:review:${label}`,
    ));
  };
  const resolve = async (
    conflictId: string,
    strategy: 'KEEP_CURRENT' | 'MERGE' | 'DEFER_AS_GAP',
    reason: string,
  ) => {
    const preview = await workbench.previewCurrentConflict({ runId: snapshot.run!.runId, conflictId, strategy });
    snapshot = await workbench.execute({
      type: 'RESOLVE_CURRENT_CONFLICT', commandId: `cp8-freeze:resolve:${conflictId}`,
      expectedRevision: snapshot.run!.revision, actorUserId: 'cp8-author',
      conflictId, strategy, reason, expectedHunkSha256: preview.hunk.hunkSha256,
    });
  };
  await readAndReview('mysql');
  await readAndReview('github');
  await resolve('gyj-conflict-debt-schema', 'KEEP_CURRENT', '部署数据库作为当前工作标准。');
  await readAndReview('official');
  await readAndReview('policy');
  await resolve('gyj-conflict-negative-stock', 'MERGE', '保留实现事实并登记制度缺口。');
  await resolve('gyj-conflict-status-nine', 'DEFER_AS_GAP', '状态九业务含义待确认。');
  await readAndReview('semantica');
  const resolutions = snapshot.resolutions;

  const baseRunReader: StandardizationRunReader = {
    read: (runId) => runs.read(runId),
    readEventPayload: (runId, eventId) => runs.readEventPayload(runId, eventId),
    appendEvent(value) {
      const common = {
        commandId: value.commandId, runId: value.runId,
        expectedRevision: value.expectedRunRevision, actor: { userId: value.actorUserId },
        payload: value.payload, deliveryCapability,
      };
      const command: StandardizationRunCommand = value.type === 'DELIVERABLE_GENERATED'
        ? { ...common, type: 'MARK_DELIVERABLE_GENERATED', deliverableId: value.deliverableId }
        : value.type === 'REVIEW_SUBMITTED'
          ? { ...common, type: 'MARK_REVIEW_SUBMITTED', reviewId: value.eventIdentity }
          : value.type === 'DELIVERABLE_FROZEN'
            ? { ...common, type: 'MARK_DELIVERABLE_FROZEN', deliverableId: value.deliverableId }
            : { ...common, type: 'MARK_MODELING_HANDOFF_COMPLETED', handoffId: value.eventIdentity };
      return runs.execute(command);
    },
  };
  const faults = createReviewPersistenceFaultController();
  const deliverableMetadataStore = new MemoryMetadataStore();
  const createDeliverables = () => createStandardizationDeliverableRuntime({
    metadataStore: deliverableMetadataStore, contentStore,
    runReader: withStandardizationRunReaderFaultpoints(baseRunReader, faults),
    sourceDocuments,
    conflictResolutions: { async list() { return resolutions; } },
    baselineProvider: { async resolve(scenarioKey) { return scenarioKey === baseline.scenarioKey ? baseline : null; } },
    modelingProjector: createStandardizationModelingProjector(),
    membershipReader: { async read(actorUserId) {
      return { active: true, role: actorUserId === 'cp8-reviewer' ? 'REVIEWER' as const : 'EDITOR' as const };
    } },
    now: () => '2026-08-18T12:00:00.000Z',
  });
  const deliverables = createDeliverables();
  const generated = await deliverables.execute({
    type: 'GENERATE_DELIVERABLE', commandId: 'cp8-freeze:generate', runId: snapshot.run!.runId,
    actorUserId: 'cp8-author', expectedRunRevision: snapshot.run!.revision,
  });
  const awaitingReview = await deliverables.execute({
    type: 'AUTHOR_CONFIRM', commandId: 'cp8-freeze:author-confirm', runId: snapshot.run!.runId,
    deliverableId: generated.deliverable!.deliverableId, actorUserId: 'cp8-author',
    expectedDeliverableRevision: generated.deliverable!.revision,
  });
  const freezeCommand = {
    type: 'REVIEW_AND_FREEZE' as const, commandId: 'cp8-freeze:review', runId: snapshot.run!.runId,
    deliverableId: generated.deliverable!.deliverableId, actorUserId: 'cp8-reviewer',
    expectedDeliverableRevision: awaitingReview.deliverable!.revision,
  };
  return {
    faults, deliverables, freezeCommand,
    async injectPending(): Promise<DeliverableSnapshot> {
      faults.arm('DELIVERY_EVENT_APPEND_FAILED');
      try { await deliverables.execute(freezeCommand); } catch (cause) {
        if (!(cause instanceof Error) || !/injected delivery event append failure/u.test(cause.message)) throw cause;
      }
      return deliverables.read({ runId: snapshot.run!.runId, actorUserId: 'cp8-reviewer' });
    },
    async reload() {
      return createDeliverables().read({ runId: snapshot.run!.runId, actorUserId: 'cp8-reviewer' });
    },
    async reconcile() {
      const reloaded = createDeliverables();
      const pending = await reloaded.read({ runId: snapshot.run!.runId, actorUserId: 'cp8-reviewer' });
      if (!pending.pendingCommand) throw new Error('reload后缺少待恢复冻结命令');
      return reloaded.execute(pending.pendingCommand);
    },
  };
}

export async function createCp8RecoveryScenario() {
  const [cas, quota, freeze] = await Promise.all([
    prepareCasScenario(), prepareQuotaScenario(), prepareFreezeScenario(),
  ]);
  return { cas, quota, freeze };
}

/**
 * Browser consumers use the same production adapters, but initialize each
 * recovery seam only when that failure is exercised. The full scenario above
 * remains available to the direct recovery test; this adapter keeps the
 * capacity shell's first paint independent from the expensive freeze setup.
 */
export type Cp8RecoveryScenarioAdapter = {
  cas: Pick<Awaited<ReturnType<typeof prepareCasScenario>>, 'revision' | 'advanceWinner' | 'injectStaleWrite' | 'reload'> & {
    reloadCalls(): number;
  };
  quota: Pick<Awaited<ReturnType<typeof prepareQuotaScenario>>, 'revision' | 'injectQuota'>;
  freeze: Pick<Awaited<ReturnType<typeof prepareFreezeScenario>>, 'injectPending' | 'reload' | 'reconcile'>;
};

export function createCp8RecoveryScenarioAdapter(): Cp8RecoveryScenarioAdapter {
  let cas: ReturnType<typeof prepareCasScenario> | undefined;
  let casReloadCalls = 0;
  let quota: ReturnType<typeof prepareQuotaScenario> | undefined;
  let freeze: ReturnType<typeof prepareFreezeScenario> | undefined;
  const getCas = () => cas ??= prepareCasScenario();
  const getQuota = () => quota ??= prepareQuotaScenario();
  const getFreeze = () => freeze ??= prepareFreezeScenario();
  return {
    cas: {
      revision: () => getCas().then((scenario) => scenario.revision()),
      advanceWinner: () => getCas().then((scenario) => scenario.advanceWinner()),
      injectStaleWrite: (staleRevision?: number) => getCas().then((scenario) => scenario.injectStaleWrite(staleRevision)),
      reload: () => getCas().then((scenario) => {
        casReloadCalls += 1;
        return scenario.reload();
      }),
      reloadCalls: () => casReloadCalls,
    },
    quota: {
      revision: () => getQuota().then((scenario) => scenario.revision()),
      injectQuota: () => getQuota().then((scenario) => scenario.injectQuota()),
    },
    freeze: {
      injectPending: () => getFreeze().then((scenario) => scenario.injectPending()),
      reload: () => getFreeze().then((scenario) => scenario.reload()),
      reconcile: () => getFreeze().then((scenario) => scenario.reconcile()),
    },
  };
}
