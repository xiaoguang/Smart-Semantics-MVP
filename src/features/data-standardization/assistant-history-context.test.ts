import assert from 'node:assert/strict';
import test from 'node:test';
import {
  assistantHistoryContextFor,
  projectAssistantHistoryRecoveryState,
} from './assistant-history-context.ts';
import {
  createAssistantHistoryRecoveryController,
} from './assistant-history-recovery.ts';
import type { GuanyijiaWorkbenchSnapshot } from './guanyijia-workbench-runtime.ts';

function snapshot(revision: number, assistantSequence: number): GuanyijiaWorkbenchSnapshot {
  return {
    run: {
      runId: 'run-1', revision,
      timeline: assistantSequence ? [{ type: 'ASSISTANT_TURN_RECORDED', sequence: assistantSequence }] : [],
    },
    current: {
      source: { sourceId: 'guanyijia_mysql' },
      document: { documentId: 'document:mysql', revision: 3 },
    },
  } as unknown as GuanyijiaWorkbenchSnapshot;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => { resolve = resolvePromise; });
  return { promise, resolve };
}

test('command returned snapshot gets a new assistant history identity and wins over its old summary effect', async () => {
  const before = assistantHistoryContextFor(snapshot(6, 0), 'user-author');
  const after = assistantHistoryContextFor(snapshot(7, 12), 'user-author');
  assert.ok(before);
  assert.ok(after);
  assert.notEqual(before.requestEpoch, after.requestEpoch);
  assert.notEqual(before.revision, after.revision);
  assert.notEqual(before.assistantMutationSequence, after.assistantMutationSequence);

  const controller = createAssistantHistoryRecoveryController<{ id: string }>();
  const oldSummary = deferred<{ items: { id: string }[]; total: number }>();
  const newHistory = deferred<{ items: { id: string }[]; total: number }>();
  controller.begin(before);
  const pendingSummary = controller.read(before, () => oldSummary.promise, 'SUMMARY');
  controller.begin(after);
  const pendingHistory = controller.read(after, () => newHistory.promise, 'HISTORY');
  newHistory.resolve({ items: [{ id: 'ask-turn' }], total: 1 });
  await pendingHistory;
  oldSummary.resolve({ items: [], total: 0 });
  await pendingSummary;

  assert.equal(controller.getState()?.contextKey, controller.contextKey(after));
  assert.deepEqual(controller.getState()?.items, [{ id: 'ask-turn' }]);
  assert.equal(controller.getState()?.total, 1);
  assert.equal(controller.getState()?.loadedMode, 'HISTORY');
});

test('loaded HISTORY projects UI items, pending proposal, total, and historyLoaded=true', () => {
  const projected = projectAssistantHistoryRecoveryState({
    items: [{ id: 'turn-1' }], total: 1, pendingProposal: { id: 'pending-1' },
    nextCursor: 'next', loading: false, error: undefined, loadedMode: 'HISTORY',
  });
  assert.deepEqual(projected, {
    items: [{ id: 'turn-1' }], total: 1,
    pendingProposal: { id: 'pending-1' }, historyLoaded: true,
  });
});

test('SUMMARY or an empty recovery state projects historyLoaded=false without retaining history', () => {
  assert.deepEqual(projectAssistantHistoryRecoveryState({
    items: [], total: 0, loading: true, loadedMode: 'SUMMARY',
  }), { items: [], total: 0, pendingProposal: undefined, historyLoaded: false });
  assert.deepEqual(projectAssistantHistoryRecoveryState(undefined), {
    items: [], total: 0, pendingProposal: undefined, historyLoaded: false,
  });
});
