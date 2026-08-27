import type { ReviewAssistantHistoryItem } from './review-assistant-types.ts';

export function projectReviewAssistantHistory(input: {
  history: ReviewAssistantHistoryItem[];
  historyTotal: number;
  historyLoaded: boolean;
  pendingProposal?: ReviewAssistantHistoryItem;
}) {
  const pendingIsVisible = input.pendingProposal
    && input.history.some((item) => item.eventId === input.pendingProposal!.eventId);
  const visibleHistory = (input.pendingProposal && !pendingIsVisible
    ? [input.pendingProposal, ...input.history.slice(-19)]
    : input.history.slice(-20)).sort((left, right) => left.sequence - right.sequence);
  return {
    visibleHistory,
    loadHistoryLabel: !input.historyLoaded && input.historyTotal > 0
      ? `查看最近 ${Math.min(input.historyTotal, 20)} 条审阅记录`
      : undefined,
  };
}
