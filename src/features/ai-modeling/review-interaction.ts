import type { ReviewDecision } from './runtime-types.ts';
import type { ReviewGroupView, ReviewItemGuidance } from './review-batch.ts';

export type { ReviewItemGuidance } from './review-batch.ts';

export type ReviewObjectType = 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'METRIC';
export type ReviewListFilter = 'ALL' | ReviewObjectType | 'PRIORITY';

export type ReviewInteractionState = {
  groupId: string;
  filter: ReviewListFilter;
  mode: 'BROWSE' | 'SELECT_OBJECTION' | 'EDIT_OBJECTION';
  selectedItemId?: string;
};

export type WorkflowFocusTarget =
  | { kind: 'REVIEW_LIST'; groupId: string }
  | { kind: 'REVIEW_ITEM'; itemId: string }
  | { kind: 'NEXT_PROMPT' }
  | { kind: 'OBJECTION_REASON'; itemId: string }
  | { kind: 'PUBLISH_CHECKS' };

export type ReviewInteractionAction =
  | { type: 'TOGGLE_FILTER'; filter: ReviewObjectType }
  | { type: 'SHOW_PRIORITY' }
  | { type: 'START_OBJECTION' }
  | { type: 'SELECT_OBJECTION_ITEM'; itemId: string }
  | { type: 'CANCEL_OBJECTION' };

const objectTypes: ReviewObjectType[] = ['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'METRIC'];

export function projectPendingReviewItems(
  group: ReviewGroupView,
  decisions: Record<string, ReviewDecision>,
) {
  const items = group.items.filter((item) => !decisions[item.itemId]);
  const counts = Object.fromEntries(objectTypes.map((type) => [
    type, items.filter((item) => item.type === type).length,
  ])) as Record<ReviewObjectType, number>;
  return { items, counts };
}

function priority(item: ReviewItemGuidance) {
  if (item.basis === 'ATTENTION') return 0;
  if (item.basis === 'INFERRED') return 1;
  if (item.change === 'MODIFIED') return 2;
  if (item.change === 'ADDED') return 3;
  return 4;
}

export function filterAndSortReviewItems(items: ReviewItemGuidance[], filter: ReviewListFilter) {
  const filtered = filter === 'ALL'
    ? items
    : filter === 'PRIORITY'
      ? items.filter((item) => item.recommendation === 'VERIFY')
      : items.filter((item) => item.type === filter);
  return [...filtered].sort((left, right) => priority(left) - priority(right)
    || left.name.localeCompare(right.name, 'zh-CN')
    || left.itemId.localeCompare(right.itemId));
}

export function transitionReviewInteraction(
  state: ReviewInteractionState,
  action: ReviewInteractionAction,
): { state: ReviewInteractionState; focus: WorkflowFocusTarget } {
  if (action.type === 'TOGGLE_FILTER') {
    return {
      state: { groupId: state.groupId, filter: state.filter === action.filter ? 'ALL' : action.filter, mode: 'BROWSE' },
      focus: { kind: 'REVIEW_LIST', groupId: state.groupId },
    };
  }
  if (action.type === 'SHOW_PRIORITY') {
    return {
      state: { groupId: state.groupId, filter: 'PRIORITY', mode: 'BROWSE' },
      focus: { kind: 'REVIEW_LIST', groupId: state.groupId },
    };
  }
  if (action.type === 'START_OBJECTION') {
    return {
      state: { groupId: state.groupId, filter: 'PRIORITY', mode: 'SELECT_OBJECTION' },
      focus: { kind: 'REVIEW_LIST', groupId: state.groupId },
    };
  }
  if (action.type === 'SELECT_OBJECTION_ITEM') {
    return {
      state: { ...state, mode: 'EDIT_OBJECTION', selectedItemId: action.itemId },
      focus: { kind: 'OBJECTION_REASON', itemId: action.itemId },
    };
  }
  return {
    state: { groupId: state.groupId, filter: 'PRIORITY', mode: 'SELECT_OBJECTION' },
    focus: { kind: 'REVIEW_LIST', groupId: state.groupId },
  };
}

export function validateObjectionReason(reason: string) {
  return reason.trim() && /[\u3400-\u9fff]/.test(reason) ? null : '请输入中文异议原因';
}
