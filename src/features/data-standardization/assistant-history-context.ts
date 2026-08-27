import type { GuanyijiaWorkbenchSnapshot } from './guanyijia-workbench-runtime.ts';
import type {
  AssistantHistoryContext,
  AssistantHistoryRecoveryState,
} from './assistant-history-recovery.ts';

export type AssistantHistoryUiProjection<T> = {
  items: T[];
  total: number;
  pendingProposal?: T;
  historyLoaded: boolean;
};

export function assistantHistoryContextFor(
  snapshot: GuanyijiaWorkbenchSnapshot | null | undefined,
  actorUserId: string,
): AssistantHistoryContext | undefined {
  const run = snapshot?.run;
  const current = snapshot?.current;
  if (!run || !current?.document) return undefined;
  const assistantMutationSequence = run.timeline
    .filter((event) => event.type === 'ASSISTANT_TURN_RECORDED')
    .at(-1)?.sequence ?? 0;
  return {
    actorUserId,
    projectId: 'guanyijia_erp',
    runId: run.runId,
    sourceId: current.source.sourceId,
    documentId: current.document.documentId,
    revision: run.revision,
    documentRevision: current.document.revision,
    knowledgeRevision: current.document.revision,
    assistantMutationSequence,
    requestEpoch: `revision:${run.revision}:${current.source.sourceId}:${current.document.documentId}`,
  };
}

export function projectAssistantHistoryRecoveryState<T>(
  state: Pick<AssistantHistoryRecoveryState<T>, 'items' | 'total' | 'pendingProposal' | 'loadedMode'> | undefined,
): AssistantHistoryUiProjection<T> {
  const loaded = state?.loadedMode === 'HISTORY';
  return {
    items: loaded ? state!.items : [],
    total: loaded ? state!.total : state?.total ?? 0,
    pendingProposal: loaded ? state!.pendingProposal : undefined,
    historyLoaded: loaded,
  };
}
