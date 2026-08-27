import assert from 'node:assert/strict';
import test from 'node:test';
import { projectReviewAssistantPlacement } from './review-assistant-placement.ts';

test('places the assistant only inside an active review and expands for context', () => {
  assert.equal(projectReviewAssistantPlacement({
    activeReview: false,
    hasCurrentContext: true,
    hasPendingProposal: false,
    expandedByUser: false,
  }), undefined);

  assert.deepEqual(projectReviewAssistantPlacement({
    activeReview: true,
    hasCurrentContext: false,
    hasPendingProposal: false,
    expandedByUser: false,
  }), { host: 'ACTIVE_REVIEW', presentation: 'COMPACT' });

  assert.deepEqual(projectReviewAssistantPlacement({
    activeReview: true,
    hasCurrentContext: true,
    hasPendingProposal: false,
    expandedByUser: false,
  }), { host: 'ACTIVE_REVIEW', presentation: 'EXPANDED' });
});
