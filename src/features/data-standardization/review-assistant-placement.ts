export type ReviewAssistantPresentation = 'COMPACT' | 'EXPANDED';

export type ReviewAssistantPlacement = {
  host: 'ACTIVE_REVIEW';
  presentation: ReviewAssistantPresentation;
};

export function projectReviewAssistantPlacement(input: {
  activeReview: boolean;
  hasCurrentContext: boolean;
  hasPendingProposal: boolean;
  expandedByUser: boolean;
}): ReviewAssistantPlacement | undefined {
  if (!input.activeReview) return undefined;
  return {
    host: 'ACTIVE_REVIEW',
    presentation: input.hasCurrentContext || input.hasPendingProposal || input.expandedByUser
      ? 'EXPANDED'
      : 'COMPACT',
  };
}
