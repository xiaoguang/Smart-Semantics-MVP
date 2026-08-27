import type { AssistantHistoryContext } from './assistant-history-recovery.ts';
import type { ReviewAssistantHistoryItem } from './review-assistant-types.ts';

export type AssistantHistoryUiState = {
  scopeKey: string;
  items: ReviewAssistantHistoryItem[];
  total: number;
  pendingProposal?: ReviewAssistantHistoryItem;
  loaded: boolean;
  loading: boolean;
  error?: unknown;
};

export function assistantHistoryUiScopeKey(
  context: Pick<AssistantHistoryContext, 'actorUserId' | 'projectId' | 'runId'>,
) {
  return [context.actorUserId, context.projectId, context.runId].join('\u001f');
}

function maxGlobalPosition(items: readonly ReviewAssistantHistoryItem[]) {
  return items.reduce((maximum, item) => Math.max(maximum, item.position, item.sequence), 0);
}

function latestTwenty(items: Iterable<ReviewAssistantHistoryItem>) {
  return [...items]
    .sort((left, right) => left.sequence - right.sequence || left.eventId.localeCompare(right.eventId))
    .slice(-20);
}

function normalizedIncoming(incoming: AssistantHistoryUiState): AssistantHistoryUiState {
  const items = latestTwenty(new Map(incoming.items.map((item) => [item.eventId, item])).values());
  return {
    ...incoming,
    items,
    total: Math.max(incoming.total, maxGlobalPosition(incoming.items)),
  };
}

export function reduceAssistantHistoryUiState(
  current: AssistantHistoryUiState | undefined,
  incoming: AssistantHistoryUiState,
): AssistantHistoryUiState {
  if (!current || current.scopeKey !== incoming.scopeKey) return normalizedIncoming(incoming);

  const byEventId = new Map(current.items.map((item) => [item.eventId, item]));
  for (const item of incoming.items) byEventId.set(item.eventId, item);
  const items = latestTwenty(byEventId.values());
  return {
    scopeKey: current.scopeKey,
    items,
    total: Math.max(current.total, incoming.total, maxGlobalPosition(items)),
    pendingProposal: incoming.loaded ? incoming.pendingProposal : current.pendingProposal,
    loaded: current.loaded || incoming.loaded,
    loading: incoming.loading,
    error: incoming.error,
  };
}
