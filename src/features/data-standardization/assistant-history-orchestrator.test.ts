import assert from 'node:assert/strict';
import test from 'node:test';
import {
  commitAssistantAskDeltaResult,
  commitAssistantAskResult,
} from './assistant-history-orchestrator.ts';

type Snapshot = { revision: number; runId: string };
type Context = { runId: string; revision: number };

test('authoritative ASK snapshot commits only after HISTORY and includes the verified history projection', async () => {
  const events: string[] = [];
  let committed: unknown;
  await commitAssistantAskResult({
    requestId: 4,
    isCurrentRequest: () => true,
    nextSnapshot: { revision: 8, runId: 'run-1' } satisfies Snapshot,
    context: { runId: 'run-1', revision: 8 } satisfies Context,
    begin: () => { events.push('begin'); },
    readHistory: async () => {
      events.push('history');
      return { items: [{ id: 'turn-1' }], total: 1, pendingProposal: { id: 'turn-1' }, nextCursor: 'next' };
    },
    commit: (payload) => { events.push('commit'); committed = payload; },
  });

  assert.deepEqual(events, ['begin', 'history', 'commit']);
  assert.deepEqual(committed, {
    snapshot: { revision: 8, runId: 'run-1' },
    items: [{ id: 'turn-1' }], total: 1,
    pendingProposal: { id: 'turn-1' }, nextCursor: 'next',
    historyLoaded: true, error: undefined,
  });
});

test('production consumer commits all eight verified turns and keeps the final GitHub conflict answer', async () => {
  let committed: any;
  const items = Array.from({ length: 8 }, (_, index) => ({
    id: `turn-${index + 1}`,
    response: index === 7 ? { kind: 'CONFLICT_EXPLANATION', title: '欠款字段结构冲突' } : undefined,
  }));
  const result = await commitAssistantAskResult({
    requestId: 12,
    isCurrentRequest: () => true,
    nextSnapshot: { revision: 24, runId: 'run-exact-history' } satisfies Snapshot,
    context: { runId: 'run-exact-history', revision: 24 } satisfies Context,
    begin: () => undefined,
    readHistory: async () => ({ items, total: 8, nextCursor: undefined }),
    commit: (payload) => { committed = payload; },
  });

  assert.equal(result, 'COMMITTED');
  assert.equal(committed.historyLoaded, true);
  assert.equal(committed.total, 8);
  assert.equal(committed.items.length, 8);
  assert.deepEqual(committed.items, items);
  assert.deepEqual(committed.items.at(-1), {
    id: 'turn-8', response: { kind: 'CONFLICT_EXPLANATION', title: '欠款字段结构冲突' },
  });
});

test('history failure commits the authoritative ASK snapshot with loaded=false and failure, never a rollback', async () => {
  let committed: any;
  const cause = new Error('history unavailable');
  await commitAssistantAskResult({
    requestId: 5,
    isCurrentRequest: () => true,
    nextSnapshot: { revision: 9, runId: 'run-1' } satisfies Snapshot,
    context: { runId: 'run-1', revision: 9 } satisfies Context,
    begin: () => undefined,
    readHistory: async () => { throw cause; },
    commit: (payload) => { committed = payload; },
  });

  assert.equal(committed.snapshot.revision, 9);
  assert.equal(committed.historyLoaded, false);
  assert.equal(committed.error, cause);
  assert.deepEqual(committed.items, []);
});

test('stale assistant request does not begin history or commit its authoritative snapshot', async () => {
  let began = false;
  let committed = false;
  const result = await commitAssistantAskResult({
    requestId: 6,
    isCurrentRequest: () => false,
    nextSnapshot: { revision: 10, runId: 'run-1' } satisfies Snapshot,
    context: { runId: 'run-1', revision: 10 } satisfies Context,
    begin: () => { began = true; },
    readHistory: async () => ({ items: [], total: 0 }),
    commit: () => { committed = true; },
  });

  assert.equal(result, 'STALE');
  assert.equal(began, false);
  assert.equal(committed, false);
});

test('ASK delta orchestrator executes, merges one verified delta, then commits without reading full history', async () => {
  const events: string[] = [];
  let committed: any;
  const item = { id: 'turn-8' };
  const result = await commitAssistantAskDeltaResult({
    requestId: 20,
    isCurrentRequest: () => true,
    execute: async () => {
      events.push('execute');
      return {
        revision: 25, runId: 'run-delta',
        assistantTurnDelta: { item, total: 8, position: 8, eventId: 'event-8' },
      } satisfies Snapshot & { assistantTurnDelta: { item: { id: string }; total: number; position: number; eventId: string } };
    },
    contextFor: (snapshot) => ({ runId: snapshot.runId, revision: snapshot.revision } satisfies Context),
    begin: () => { events.push('begin'); },
    mergeDelta: (delta) => {
      events.push('merge');
      return { items: [delta.item], total: delta.total };
    },
    commit: (payload) => {
      events.push('commit');
      committed = payload;
    },
  });

  assert.equal(result, 'COMMITTED');
  assert.deepEqual(events, ['execute', 'begin', 'merge', 'commit']);
  assert.deepEqual(committed.items, [item]);
  assert.equal(committed.total, 8);
  assert.equal(committed.historyLoaded, true);
});

test('invalid ASK delta commits authoritative snapshot as failed history without fabricating an item', async () => {
  let committed: any;
  let merged = false;
  const result = await commitAssistantAskDeltaResult({
    requestId: 21,
    isCurrentRequest: () => true,
    execute: async () => ({
      revision: 26, runId: 'run-invalid-delta',
      assistantTurnDelta: undefined,
    } satisfies Snapshot & { assistantTurnDelta?: undefined }),
    contextFor: (snapshot) => ({ runId: snapshot.runId, revision: snapshot.revision } satisfies Context),
    begin: () => undefined,
    mergeDelta: () => {
      merged = true;
      return { items: [{ id: 'never-committed' }], total: 1 };
    },
    commit: (payload) => { committed = payload; },
  });

  assert.equal(result, 'INVALID_DELTA');
  assert.equal(merged, false);
  assert.equal(committed.snapshot.revision, 26);
  assert.deepEqual(committed.items, []);
  assert.equal(committed.historyLoaded, false);
  assert.ok(committed.error instanceof Error);
});

test('ASK command failure never commits a fabricated delta', async () => {
  let committed = false;
  await assert.rejects(() => commitAssistantAskDeltaResult({
    requestId: 22,
    isCurrentRequest: () => true,
    execute: async () => { throw new Error('command failed'); },
    contextFor: () => ({ runId: 'run-failed', revision: 1 }),
    begin: () => undefined,
    mergeDelta: () => ({ items: [{ id: 'never-committed' }], total: 1 }),
    commit: () => { committed = true; },
  }), /command failed/);
  assert.equal(committed, false);
});
