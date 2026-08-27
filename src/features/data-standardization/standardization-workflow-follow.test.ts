import assert from 'node:assert/strict';
import test from 'node:test';
import {
  initialWorkflowFollowState,
  projectWorkflowFollow,
} from './standardization-workflow-follow.ts';

test('follows a newly current checkpoint until the reviewer browses history', () => {
  const initial = initialWorkflowFollowState('database-document');
  const following = projectWorkflowFollow(initial, {
    checkpointItemId: 'github-document',
  });
  assert.deepEqual(following, {
    mode: 'FOLLOWING',
    currentCheckpointItemId: 'github-document',
    scrollToCheckpointItemId: 'github-document',
  });

  const browsing = projectWorkflowFollow(following, {
    checkpointItemId: 'github-document',
    browsingHistory: true,
  });
  assert.deepEqual(browsing, {
    mode: 'BROWSING_HISTORY',
    currentCheckpointItemId: 'github-document',
  });

  const progressWhileBrowsing = projectWorkflowFollow(browsing, {
    checkpointItemId: 'official-document',
  });
  assert.deepEqual(progressWhileBrowsing, {
    mode: 'BROWSING_HISTORY',
    currentCheckpointItemId: 'official-document',
    pendingCheckpointItemId: 'official-document',
  });
});
