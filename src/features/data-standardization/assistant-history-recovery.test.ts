import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createAssistantHistoryRecoveryController,
  type AssistantHistoryContext,
} from './assistant-history-recovery.ts';
import { projectAssistantHistoryRecoveryState } from './assistant-history-context.ts';

type HistoryItem = { id: string };
type VersionedAssistantHistoryContext = AssistantHistoryContext & {
  knowledgeRevision: number;
  assistantMutationSequence: number;
};

function context(
  revision: number,
  sourceId: string,
  assistantMutationSequence = 0,
): VersionedAssistantHistoryContext {
  return {
    actorUserId: 'user-author',
    projectId: 'guanyijia_erp',
    runId: 'standardization-run-1',
    sourceId,
    documentId: `document:${sourceId}`,
    revision,
    documentRevision: revision,
    requestEpoch: `revision:${revision}:${sourceId}`,
    knowledgeRevision: revision,
    assistantMutationSequence,
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

test('旧 assistant history context 的 success 不能覆盖切换后的新 context', async () => {
  const controller = createAssistantHistoryRecoveryController<HistoryItem>();
  const contextA = context(6, 'guanyijia_mysql');
  const contextB = context(7, 'guanyijia_github');
  const readA = deferred<{ items: HistoryItem[]; total: number }>();
  const readB = deferred<{ items: HistoryItem[]; total: number }>();

  controller.begin(contextA);
  const pendingA = controller.read(contextA, () => readA.promise);
  controller.begin(contextB);
  const pendingB = controller.read(contextB, () => readB.promise);
  readB.resolve({ items: [{ id: 'B-turn' }], total: 1 });
  await pendingB;
  readA.resolve({ items: [{ id: 'A-old-turn' }], total: 8 });
  await pendingA;

  assert.deepEqual(controller.getState(), {
    context: contextB,
    contextKey: controller.contextKey(contextB),
    items: [{ id: 'B-turn' }],
    total: 1,
    pendingProposal: undefined,
    nextCursor: undefined,
    loading: false,
    error: undefined,
    loadedMode: 'HISTORY',
  });
});

test('旧 context 的 catch/finally 不能清除新 context 的 loading 或写入错误', async () => {
  const controller = createAssistantHistoryRecoveryController<HistoryItem>();
  const contextA = context(6, 'guanyijia_mysql');
  const contextB = context(7, 'guanyijia_github');
  const readA = deferred<{ items: HistoryItem[]; total: number }>();
  const readB = deferred<{ items: HistoryItem[]; total: number }>();

  controller.begin(contextA);
  const pendingA = controller.read(contextA, () => readA.promise);
  controller.begin(contextB);
  const pendingB = controller.read(contextB, () => readB.promise);
  readA.reject(new Error('old context failed'));
  await pendingA;

  assert.equal(controller.getState()?.contextKey, controller.contextKey(contextB));
  assert.equal(controller.getState()?.loading, true);
  assert.equal(controller.getState()?.error, undefined);

  readB.resolve({ items: [{ id: 'B-turn' }], total: 1 });
  await pendingB;
  assert.deepEqual(controller.getState()?.items, [{ id: 'B-turn' }]);
  assert.equal(controller.getState()?.loading, false);
  assert.equal(controller.getState()?.error, undefined);
});

test('旧 summary effect 即使在 ASK 后的显式 history load 之后才调度，也不能清掉新 generation', async () => {
  const controller = createAssistantHistoryRecoveryController<HistoryItem>();
  const contextA = context(6, 'guanyijia_mysql', 0);
  const contextB = context(7, 'guanyijia_mysql', 1);
  const summaryA = deferred<{ items: HistoryItem[]; total: number }>();
  const historyB = deferred<{ items: HistoryItem[]; total: number }>();

  controller.begin(contextA);
  const pendingA = controller.read(contextA, () => summaryA.promise);
  controller.begin(contextB);
  const pendingB = controller.read(contextB, () => historyB.promise);
  historyB.resolve({ items: [{ id: 'B-turn' }], total: 1 });
  await pendingB;

  // React's old effect can be scheduled after the ASK commit with its old snapshot.
  controller.begin(contextA);
  const lateA = controller.read(contextA, () => summaryA.promise);
  summaryA.resolve({ items: [], total: 0 });
  await Promise.all([pendingA, lateA]);

  assert.equal(controller.getState()?.contextKey, controller.contextKey(contextB));
  assert.deepEqual(controller.getState()?.items, [{ id: 'B-turn' }]);
  assert.equal(controller.getState()?.total, 1);
  assert.equal(controller.getState()?.loading, false);
  assert.equal(controller.getState()?.error, undefined);
});

test('首次合法空 history 的旧 summary reject/finally 不能阻止随后 ASK history 的一条记录', async () => {
  const controller = createAssistantHistoryRecoveryController<HistoryItem>();
  const contextA = context(6, 'guanyijia_mysql', 0);
  const contextB = context(7, 'guanyijia_mysql', 1);
  const summaryA = deferred<{ items: HistoryItem[]; total: number }>();
  const historyB = deferred<{ items: HistoryItem[]; total: number }>();

  controller.begin(contextA);
  const pendingA = controller.read(contextA, () => summaryA.promise);
  controller.begin(contextB);
  const pendingB = controller.read(contextB, () => historyB.promise);
  historyB.resolve({ items: [{ id: 'B-turn' }], total: 1 });
  await pendingB;

  controller.begin(contextA);
  const lateA = controller.read(contextA, () => summaryA.promise);
  summaryA.reject(new Error('stale summary failed'));
  await Promise.all([pendingA, lateA]);

  assert.equal(controller.getState()?.contextKey, controller.contextKey(contextB));
  assert.deepEqual(controller.getState()?.items, [{ id: 'B-turn' }]);
  assert.equal(controller.getState()?.total, 1);
  assert.equal(controller.getState()?.loading, false);
  assert.equal(controller.getState()?.error, undefined);
});

test('same context 中 HISTORY 可以取代旧的 in-flight SUMMARY，且 SUMMARY 完成不能清掉 HISTORY', async () => {
  const controller = createAssistantHistoryRecoveryController<HistoryItem>();
  const currentContext = context(7, 'guanyijia_mysql', 1);
  const summary = deferred<{ items: HistoryItem[]; total: number; nextCursor?: string }>();
  const history = deferred<{ items: HistoryItem[]; total: number; pendingProposal?: HistoryItem; nextCursor?: string }>();

  controller.begin(currentContext);
  const pendingSummary = controller.read(currentContext, () => summary.promise, 'SUMMARY');
  const pendingHistory = controller.read(currentContext, () => history.promise, 'HISTORY');
  history.resolve({ items: [{ id: 'B-turn' }], total: 1, pendingProposal: { id: 'B-pending' }, nextCursor: 'history-next' });
  await pendingHistory;
  summary.resolve({ items: [], total: 0, nextCursor: 'summary-next' });
  await pendingSummary;

  assert.equal(controller.getState()?.contextKey, controller.contextKey(currentContext));
  assert.deepEqual(controller.getState()?.items, [{ id: 'B-turn' }]);
  assert.equal(controller.getState()?.total, 1);
  assert.deepEqual(controller.getState()?.pendingProposal, { id: 'B-pending' });
  assert.equal(controller.getState()?.nextCursor, 'history-next');
  assert.equal(controller.getState()?.loadedMode, 'HISTORY');
});

test('same context 中 in-flight HISTORY 不会被后来发起且先完成的 SUMMARY 抢走提交权', async () => {
  const controller = createAssistantHistoryRecoveryController<HistoryItem>();
  const currentContext = context(7, 'guanyijia_mysql', 1);
  const history = deferred<{ items: HistoryItem[]; total: number; nextCursor?: string }>();
  const summary = deferred<{ items: HistoryItem[]; total: number; nextCursor?: string }>();

  controller.begin(currentContext);
  const pendingHistory = controller.read(currentContext, () => history.promise, 'HISTORY');
  const pendingSummary = controller.read(currentContext, () => summary.promise, 'SUMMARY');
  summary.resolve({ items: [], total: 0, nextCursor: 'summary-next' });
  await pendingSummary;
  history.resolve({ items: [{ id: 'B-turn' }], total: 1, nextCursor: 'history-next' });
  await pendingHistory;

  assert.deepEqual(controller.getState()?.items, [{ id: 'B-turn' }]);
  assert.equal(controller.getState()?.total, 1);
  assert.equal(controller.getState()?.nextCursor, 'history-next');
  assert.equal(controller.getState()?.loadedMode, 'HISTORY');
});

test('same run scope 跨 source/document/revision 保留已验证 conversation，下一次 HISTORY 才替换 items', async () => {
  const controller = createAssistantHistoryRecoveryController<HistoryItem>();
  const mysqlContext = context(6, 'guanyijia_mysql');
  const mysqlItems = Array.from({ length: 6 }, (_, index) => ({ id: `mysql-turn-${index + 1}` }));

  controller.begin(mysqlContext);
  await controller.read(mysqlContext, async () => ({
    items: mysqlItems,
    total: 6,
    pendingProposal: { id: 'mysql-pending' },
    nextCursor: 'mysql-next',
  }));

  const staleMysqlRead = deferred<{ items: HistoryItem[]; total: number }>();
  const pendingStaleMysqlRead = controller.read(mysqlContext, () => staleMysqlRead.promise, 'HISTORY');
  const githubContext = context(7, 'guanyijia_github');
  const migrated = controller.begin(githubContext);

  assert.deepEqual(migrated.items, mysqlItems);
  assert.equal(migrated.total, 6);
  assert.equal(migrated.loadedMode, 'HISTORY');
  assert.equal(projectAssistantHistoryRecoveryState(migrated).historyLoaded, true);

  const githubRead = deferred<{ items: HistoryItem[]; total: number }>();
  const pendingGithubRead = controller.read(githubContext, () => githubRead.promise, 'HISTORY');
  staleMysqlRead.reject(new Error('stale mysql read failed'));
  await pendingStaleMysqlRead;
  assert.equal(controller.getState()?.contextKey, controller.contextKey(githubContext));
  assert.deepEqual(controller.getState()?.items, mysqlItems);
  assert.equal(controller.getState()?.total, 6);
  assert.equal(controller.getState()?.error, undefined);

  const githubItems = [...mysqlItems, { id: 'github-turn-7' }];
  githubRead.resolve({ items: githubItems, total: 7 });
  await pendingGithubRead;
  assert.deepEqual(controller.getState()?.items, githubItems);
  assert.equal(controller.getState()?.total, 7);
  assert.equal(controller.getState()?.loadedMode, 'HISTORY');
});

test('actor/project/run scope 改变时不会携带旧 run-wide history', async () => {
  const scopeChanges: Array<Partial<Pick<AssistantHistoryContext, 'actorUserId' | 'projectId' | 'runId'>>> = [
    { actorUserId: 'user-reviewer' },
    { projectId: 'another-project' },
    { runId: 'standardization-run-2' },
  ];

  for (const scopeChange of scopeChanges) {
    const controller = createAssistantHistoryRecoveryController<HistoryItem>();
    const initialContext = context(6, 'guanyijia_mysql');
    controller.begin(initialContext);
    await controller.read(initialContext, async () => ({
      items: [{ id: 'old-turn' }],
      total: 1,
    }));

    const nextContext: AssistantHistoryContext = {
      ...context(7, 'guanyijia_github'),
      ...scopeChange,
    };
    const nextState = controller.begin(nextContext);
    assert.deepEqual(nextState.items, []);
    assert.equal(nextState.total, 0);
    assert.equal(nextState.loadedMode, undefined);
    assert.equal(projectAssistantHistoryRecoveryState(nextState).historyLoaded, false);
  }
});
