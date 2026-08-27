export type AssistantHistoryContext = {
  actorUserId: string;
  projectId: string;
  runId: string;
  sourceId: string;
  documentId: string;
  revision: number;
  documentRevision: number;
  knowledgeRevision: number;
  assistantMutationSequence: number;
  requestEpoch: string;
};

export type AssistantHistoryReadMode = 'SUMMARY' | 'HISTORY';

export type AssistantHistoryReadResult<T> = {
  items: T[];
  total: number;
  pendingProposal?: T;
  nextCursor?: string;
};

export type AssistantHistoryRecoveryState<T> = {
  context: AssistantHistoryContext;
  contextKey: string;
  items: T[];
  total: number;
  pendingProposal?: T;
  nextCursor?: string;
  loading: boolean;
  error?: unknown;
  loadedMode?: AssistantHistoryReadMode;
};

function emptyState<T>(context: AssistantHistoryContext, contextKey: string): AssistantHistoryRecoveryState<T> {
  return {
    context,
    contextKey,
    items: [],
    total: 0,
    pendingProposal: undefined,
    nextCursor: undefined,
    loading: false,
    error: undefined,
    loadedMode: undefined,
  };
}

export function assistantHistoryContextKey(context: AssistantHistoryContext) {
  return [
    context.actorUserId,
    context.projectId,
    context.runId,
    context.sourceId,
    context.documentId,
    String(context.revision),
    String(context.documentRevision),
    String(context.knowledgeRevision),
    String(context.assistantMutationSequence),
    context.requestEpoch,
  ].join('\u001f');
}

function assistantHistoryScopeKey(context: AssistantHistoryContext) {
  return [context.actorUserId, context.projectId, context.runId].join('\u001f');
}

function isSameContext(a: AssistantHistoryContext, b: AssistantHistoryContext) {
  return assistantHistoryContextKey(a) === assistantHistoryContextKey(b);
}

function isOlderContext(candidate: AssistantHistoryContext, current: AssistantHistoryContext) {
  if (candidate.runId !== current.runId) return false;
  return candidate.revision < current.revision
    || candidate.documentRevision < current.documentRevision
    || candidate.knowledgeRevision < current.knowledgeRevision
    || candidate.assistantMutationSequence < current.assistantMutationSequence;
}

export function createAssistantHistoryRecoveryController<T>() {
  let requestToken = 0;
  let state: AssistantHistoryRecoveryState<T> | undefined;
  let activeRead: {
    contextKey: string;
    token: number;
    mode: AssistantHistoryReadMode;
  } | undefined;
  let contextGeneration = 0;
  const generationByContextKey = new Map<string, number>();

  const contextKey = assistantHistoryContextKey;
  const isCurrent = (key: string, token: number) => Boolean(
    state && state.contextKey === key && requestToken === token,
  );

  return {
    contextKey,
    begin(context: AssistantHistoryContext) {
      const key = contextKey(context);
      if (state && isSameContext(context, state.context)) return state;
      if (state) {
        const knownGeneration = generationByContextKey.get(key);
        if (knownGeneration !== undefined && knownGeneration < contextGeneration) return state;
        if (isOlderContext(context, state.context)) return state;
      }
      contextGeneration += 1;
      generationByContextKey.set(key, contextGeneration);
      requestToken += 1;
      const sameRunScope = state !== undefined
        && assistantHistoryScopeKey(context) === assistantHistoryScopeKey(state.context);
      state = sameRunScope && state?.loadedMode === 'HISTORY'
        ? {
          ...state,
          context,
          contextKey: key,
          pendingProposal: undefined,
          nextCursor: undefined,
          loading: false,
          error: undefined,
          loadedMode: 'HISTORY',
        }
        : emptyState(context, key);
      return state;
    },
    clear() {
      requestToken += 1;
      activeRead = undefined;
      state = undefined;
    },
    async read(
      context: AssistantHistoryContext,
      reader: (context: AssistantHistoryContext) => Promise<AssistantHistoryReadResult<T>>,
      mode: AssistantHistoryReadMode = 'HISTORY',
    ) {
      const key = contextKey(context);
      if (state && !isSameContext(context, state.context)) return state;
      if (mode === 'SUMMARY' && activeRead?.contextKey === key && activeRead.mode === 'HISTORY') return state!;
      if (state?.loadedMode === 'HISTORY' && mode === 'SUMMARY') return state;
      if (state?.error !== undefined && mode === 'SUMMARY') return state;
      const token = ++requestToken;
      if (!state) {
        contextGeneration += 1;
        generationByContextKey.set(key, contextGeneration);
        state = emptyState(context, key);
      }
      activeRead = { contextKey: key, token, mode };
      state = { ...state, context, contextKey: key, loading: true, error: undefined };
      try {
        const result = await reader(context);
        if (isCurrent(key, token)) {
          state = {
            ...state!,
            ...(mode === 'SUMMARY' ? {} : { items: result.items }),
            total: result.total,
            ...(mode === 'SUMMARY' ? {} : { pendingProposal: result.pendingProposal }),
            nextCursor: result.nextCursor,
            error: undefined,
            loadedMode: mode,
          };
        }
      } catch (cause) {
        if (isCurrent(key, token)) state = { ...state!, error: cause };
      } finally {
        if (isCurrent(key, token)) state = { ...state!, loading: false };
        if (activeRead?.token === token) activeRead = undefined;
      }
      return state!;
    },
    getState() {
      return state;
    },
  };
}
