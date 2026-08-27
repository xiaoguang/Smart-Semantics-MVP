import assert from 'node:assert/strict';
import test from 'node:test';
import {
  assistantHistoryUiScopeKey,
  reduceAssistantHistoryUiState,
  type AssistantHistoryUiState,
} from './assistant-history-ui-state.ts';
import type { ReviewAssistantHistoryItem } from './review-assistant-types.ts';

function item(sequence: number, proposalStatus?: ReviewAssistantHistoryItem['proposalStatus']): ReviewAssistantHistoryItem {
  return {
    eventId: `event-${sequence}`,
    sequence,
    position: sequence,
    actorUserId: 'user-author',
    message: `turn-${sequence}`,
    response: { kind: 'SUPPORTED_SCOPE', title: `turn-${sequence}`, body: [] },
    createdAt: `2026-08-18T16:00:${String(sequence).padStart(2, '0')}.000Z`,
    ...(proposalStatus ? { proposalStatus } : {}),
  };
}

function state(scopeKey: string, count: number, loaded = true): AssistantHistoryUiState {
  return {
    scopeKey,
    items: Array.from({ length: count }, (_, index) => item(index + 1)),
    total: count,
    pendingProposal: undefined,
    loaded,
    loading: false,
    error: undefined,
  };
}

test('same-scope stale history/summary cannot erase eight committed items', () => {
  const scopeKey = assistantHistoryUiScopeKey({ actorUserId: 'user-author', projectId: 'guanyijia_erp', runId: 'run-1' });
  const committed = state(scopeKey, 8);
  const staleProjection = {
    scopeKey,
    items: Array.from({ length: 7 }, (_, index) => item(index + 1, index === 1 ? 'CANCELLED' : undefined)),
    total: 7,
    pendingProposal: undefined,
    loaded: true,
    loading: false,
    error: undefined,
  } satisfies AssistantHistoryUiState;

  const afterHistory = reduceAssistantHistoryUiState(committed, staleProjection);
  const afterSummary = reduceAssistantHistoryUiState(afterHistory, {
    scopeKey,
    items: [], total: 0, pendingProposal: undefined,
    loaded: false, loading: false, error: undefined,
  });

  assert.equal(afterHistory.items.length, 8);
  assert.equal(afterHistory.items.find((candidate) => candidate.eventId === 'event-2')?.proposalStatus, 'CANCELLED');
  assert.equal(afterHistory.loaded, true);
  assert.equal(afterHistory.total, 8);
  assert.deepEqual(afterSummary.items, afterHistory.items);
  assert.equal(afterSummary.total, 8);
  assert.equal(afterSummary.loaded, true);
});

test('same-scope incoming status replaces the event and keeps latest twenty by sequence', () => {
  const scopeKey = 'user-author\u001fguanyijia_erp\u001frun-1';
  const current = state(scopeKey, 8);
  const incoming = {
    scopeKey,
    items: [
      item(4, 'CONFIRMED'),
      ...Array.from({ length: 13 }, (_, index) => item(index + 9)),
    ],
    total: 21,
    pendingProposal: undefined,
    loaded: true,
    loading: false,
    error: undefined,
  } satisfies AssistantHistoryUiState;

  const reduced = reduceAssistantHistoryUiState(current, incoming);
  assert.equal(reduced.items.length, 20);
  assert.deepEqual(reduced.items.map((candidate) => candidate.sequence), Array.from({ length: 20 }, (_, index) => index + 2));
  assert.equal(reduced.items.find((candidate) => candidate.eventId === 'event-4')?.proposalStatus, 'CONFIRMED');
  assert.equal(reduced.total, 21);
});

test('new actor/project/run scope clears the prior conversation', () => {
  const current = state('user-author\u001fguanyijia_erp\u001frun-1', 8);
  const incoming = {
    scopeKey: 'user-reviewer\u001fguanyijia_erp\u001frun-2',
    items: [item(1)], total: 1, pendingProposal: undefined,
    loaded: false, loading: true, error: undefined,
  } satisfies AssistantHistoryUiState;

  const reduced = reduceAssistantHistoryUiState(current, incoming);
  assert.equal(reduced.scopeKey, incoming.scopeKey);
  assert.deepEqual(reduced.items, incoming.items);
  assert.equal(reduced.total, 1);
  assert.equal(reduced.loaded, false);
  assert.equal(reduced.loading, true);
});

test('same-scope persisted ASK delta appends the eighth item without a history reread', () => {
  const scopeKey = 'user-author\u001fguanyijia_erp\u001frun-1';
  const current = state(scopeKey, 7);
  const delta = item(8);
  const reduced = reduceAssistantHistoryUiState(current, {
    scopeKey,
    items: [delta],
    total: 8,
    pendingProposal: undefined,
    loaded: true,
    loading: false,
    error: undefined,
  });

  assert.equal(reduced.items.length, 8);
  assert.equal(reduced.items.at(-1)?.eventId, 'event-8');
  assert.equal(reduced.total, 8);
  assert.equal(reduced.loaded, true);
});
