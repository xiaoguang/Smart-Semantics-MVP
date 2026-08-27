export type AssistantHistoryOrchestratorResult<T> = {
  items: T[];
  total: number;
  pendingProposal?: T;
  nextCursor?: string;
  error?: unknown;
};

export type AssistantHistoryCommitPayload<TSnapshot, TItem> = AssistantHistoryOrchestratorResult<TItem> & {
  snapshot: TSnapshot;
  historyLoaded: boolean;
};

export type AssistantTurnDelta<TItem> = {
  item: TItem;
  total: number;
  eventId?: string;
  position?: number;
  contentRef?: string;
  contentSha256?: string;
};

export type CommitAssistantAskDeltaResultInput<TSnapshot, TContext, TItem> = {
  requestId: number;
  isCurrentRequest(requestId: number): boolean;
  execute(): Promise<TSnapshot & { assistantTurnDelta?: AssistantTurnDelta<TItem> }>;
  contextFor(snapshot: TSnapshot): TContext;
  begin(context: TContext): void;
  mergeDelta(delta: AssistantTurnDelta<TItem>): AssistantHistoryOrchestratorResult<TItem>;
  commit(payload: AssistantHistoryCommitPayload<TSnapshot, TItem>): void;
};

export async function commitAssistantAskDeltaResult<TSnapshot, TContext, TItem>(
  input: CommitAssistantAskDeltaResultInput<TSnapshot, TContext, TItem>,
): Promise<'COMMITTED' | 'STALE' | 'INVALID_DELTA'> {
  if (!input.isCurrentRequest(input.requestId)) return 'STALE';
  const result = await input.execute();
  if (!input.isCurrentRequest(input.requestId)) return 'STALE';
  const delta = result.assistantTurnDelta;
  input.begin(input.contextFor(result));
  if (!delta || !delta.item || !Number.isSafeInteger(delta.total) || delta.total < 1) {
    const error = new Error('审阅助手ASK没有返回经持久化校验的Turn delta');
    input.commit({
      snapshot: result, items: [], total: 0,
      nextCursor: undefined, historyLoaded: false, error,
    });
    return 'INVALID_DELTA';
  }
  let projection: AssistantHistoryOrchestratorResult<TItem>;
  try {
    projection = input.mergeDelta(delta);
  } catch (error) {
    input.commit({
      snapshot: result, items: [], total: 0,
      nextCursor: undefined, historyLoaded: false, error,
    });
    return 'INVALID_DELTA';
  }
  if (!input.isCurrentRequest(input.requestId)) return 'STALE';
  input.commit({
    snapshot: result,
    items: projection.items,
    total: projection.total,
    pendingProposal: projection.pendingProposal,
    nextCursor: projection.nextCursor,
    historyLoaded: true,
    error: undefined,
  });
  return 'COMMITTED';
}

export type CommitAssistantAskResultInput<TSnapshot, TContext, TItem> = {
  requestId: number;
  isCurrentRequest(requestId: number): boolean;
  nextSnapshot: TSnapshot;
  context: TContext;
  begin(context: TContext): void;
  readHistory(context: TContext): Promise<AssistantHistoryOrchestratorResult<TItem>>;
  commit(payload: AssistantHistoryCommitPayload<TSnapshot, TItem>): void;
};

export async function commitAssistantAskResult<TSnapshot, TContext, TItem>(
  input: CommitAssistantAskResultInput<TSnapshot, TContext, TItem>,
): Promise<'COMMITTED' | 'STALE'> {
  if (!input.isCurrentRequest(input.requestId)) return 'STALE';
  input.begin(input.context);
  let history: AssistantHistoryOrchestratorResult<TItem>;
  try {
    history = await input.readHistory(input.context);
  } catch (error) {
    history = { items: [], total: 0, error };
  }
  if (!input.isCurrentRequest(input.requestId)) return 'STALE';
  input.commit({
    snapshot: input.nextSnapshot,
    items: history.items,
    total: history.total,
    pendingProposal: history.pendingProposal,
    nextCursor: history.nextCursor,
    historyLoaded: history.error === undefined,
    error: history.error,
  });
  return 'COMMITTED';
}
