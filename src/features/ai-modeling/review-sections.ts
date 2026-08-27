import type { ReviewDecision } from './runtime-types.ts';
import type { ReviewGroupView, ReviewItemGuidance } from './review-batch.ts';
import { filterAndSortReviewItems } from './review-interaction.ts';

export type ReviewSectionItem = { item: ReviewItemGuidance; decision: ReviewDecision };

export function projectReviewSections(
  group: ReviewGroupView,
  decisions: Record<string, ReviewDecision>,
) {
  const pending = filterAndSortReviewItems(
    group.items.filter((item) => !decisions[item.itemId]),
    'ALL',
  );
  return {
    pending: {
      priority: pending.filter((item) => item.recommendation === 'VERIFY'),
      other: pending.filter((item) => item.recommendation !== 'VERIFY'),
    },
    adopted: group.items.flatMap((item) => {
      const decision = decisions[item.itemId];
      return decision?.decision === 'APPROVED' ? [{ item, decision }] : [];
    }),
    rejected: group.items.flatMap((item) => {
      const decision = decisions[item.itemId];
      return decision?.decision === 'REJECTED' ? [{ item, decision }] : [];
    }),
  };
}

