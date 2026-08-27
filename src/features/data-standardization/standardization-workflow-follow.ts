export type WorkflowFollowMode = 'FOLLOWING' | 'BROWSING_HISTORY';

export type WorkflowFollowState = {
  mode: WorkflowFollowMode;
  currentCheckpointItemId: string;
  pendingCheckpointItemId?: string;
  scrollToCheckpointItemId?: string;
};

export function initialWorkflowFollowState(currentCheckpointItemId: string): WorkflowFollowState {
  return { mode: 'FOLLOWING', currentCheckpointItemId };
}

export function projectWorkflowFollow(
  previous: WorkflowFollowState,
  input: { checkpointItemId: string; browsingHistory?: boolean },
): WorkflowFollowState {
  if (input.browsingHistory) {
    return {
      mode: 'BROWSING_HISTORY',
      currentCheckpointItemId: input.checkpointItemId,
      ...(previous.mode === 'BROWSING_HISTORY' && previous.pendingCheckpointItemId
        ? { pendingCheckpointItemId: previous.pendingCheckpointItemId }
        : {}),
    };
  }
  if (previous.mode === 'BROWSING_HISTORY') {
    return {
      mode: 'BROWSING_HISTORY',
      currentCheckpointItemId: input.checkpointItemId,
      ...(previous.currentCheckpointItemId === input.checkpointItemId
        ? {}
        : { pendingCheckpointItemId: input.checkpointItemId }),
    };
  }
  return {
    mode: 'FOLLOWING',
    currentCheckpointItemId: input.checkpointItemId,
    ...(previous.currentCheckpointItemId === input.checkpointItemId
      ? {}
      : { scrollToCheckpointItemId: input.checkpointItemId }),
  };
}
