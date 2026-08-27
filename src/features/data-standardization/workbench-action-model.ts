export type ReviewTarget =
  | { kind: 'CURRENT_DOCUMENT' }
  | { kind: 'CURRENT_CONFLICT' };

export type PanelId = 'SOURCE_SNAPSHOTS' | 'SOURCE_DETAILS';

export type VisibleAction =
  | { effect: 'COMMAND'; id: 'START_RUN' | 'READ_NEXT_SOURCE' | 'COMPLETE_DOCUMENT'; label: string; primary: boolean }
  | { effect: 'NAVIGATE'; id: 'OPEN_DOCUMENT' | 'OPEN_CONFLICT'; label: string; target: ReviewTarget; primary: boolean }
  | { effect: 'EXPAND'; id: 'OPEN_SOURCE_SNAPSHOTS' | 'OPEN_SOURCE_DETAILS'; label: string; panel: PanelId; primary: boolean };

export type WorkflowAction =
  | { type: 'START_RUN'; label: string }
  | { type: 'READ_NEXT_SOURCE'; label: string }
  | { type: 'REVIEW_DOCUMENT'; label: string }
  | { type: 'COMPLETE_REVIEW'; label: string }
  | { type: 'RESOLVE_CONFLICT'; label: string }
  | { type: 'NONE'; label: string };

/**
 * The only source for a workflow layer's visible business action.  Static
 * status never becomes a button, and a layer can receive at most one primary
 * action.  Consumers own the command/navigation implementation.
 */
export function buildWorkbenchActions(input: {
  workflow: WorkflowAction;
  sourceName?: string;
  canCompleteCurrentDocument?: boolean;
}): VisibleAction[] {
  const sourceName = input.sourceName?.trim();
  switch (input.workflow.type) {
    case 'START_RUN':
      return [{ effect: 'COMMAND', id: 'START_RUN', label: '开始资料整理', primary: true }];
    case 'READ_NEXT_SOURCE':
      return [{ effect: 'COMMAND', id: 'READ_NEXT_SOURCE', label: input.workflow.label, primary: true }];
    case 'REVIEW_DOCUMENT':
      return [{ effect: 'NAVIGATE', id: 'OPEN_DOCUMENT', label: input.workflow.label, target: { kind: 'CURRENT_DOCUMENT' }, primary: true }];
    case 'COMPLETE_REVIEW':
      return [{ effect: 'COMMAND', id: 'COMPLETE_DOCUMENT', label: sourceName ? `完成${sourceName}审阅` : input.workflow.label, primary: true }];
    case 'RESOLVE_CONFLICT':
      return [{ effect: 'NAVIGATE', id: 'OPEN_CONFLICT', label: '审阅来源差异', target: { kind: 'CURRENT_CONFLICT' }, primary: true }];
    case 'NONE':
      return input.canCompleteCurrentDocument && sourceName
        ? [{ effect: 'COMMAND', id: 'COMPLETE_DOCUMENT', label: `完成${sourceName}审阅`, primary: true }]
        : [];
  }
}

export function sourceSnapshotAction(): VisibleAction {
  return { effect: 'EXPAND', id: 'OPEN_SOURCE_SNAPSHOTS', label: '运行来源', panel: 'SOURCE_SNAPSHOTS', primary: false };
}
