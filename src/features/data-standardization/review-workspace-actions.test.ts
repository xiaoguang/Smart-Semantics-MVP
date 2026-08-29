import assert from 'node:assert/strict';
import test from 'node:test';
import {
  projectReviewPrimaryAction,
  resolveReviewResultSection,
  shouldRetainHistoricalDocumentDuringPreparation,
} from './review-workspace-actions.ts';

test('the header directs a pending scripted review to its exact result claim', () => {
  const action = projectReviewPrimaryAction({
    sourceName: '数据库',
    pendingClaimIds: ['claim:guanyijia_mysql:curated-e005'],
    claimSections: new Map([['claim:guanyijia_mysql:curated-e005', 3]]),
  });

  assert.deepEqual(action, {
    kind: 'OPEN_PENDING_CLAIM',
    label: '核对 1 项建议',
    claimId: 'claim:guanyijia_mysql:curated-e005',
    section: 3,
  });
});

test('the header becomes the single complete-review action after scripted decisions', () => {
  assert.deepEqual(projectReviewPrimaryAction({
    sourceName: '数据库',
    pendingClaimIds: [],
    claimSections: new Map(),
  }), {
    kind: 'COMPLETE_REVIEW',
    label: '完成数据库审阅',
  });
});

test('the header directs the first actionable formal conflict immediately after local suggestions', () => {
  assert.deepEqual(projectReviewPrimaryAction({
    sourceName: 'GitHub代码仓库',
    pendingClaimIds: [],
    claimSections: new Map(),
    currentConflictId: 'conflict:receivable-debt',
  }), {
    kind: 'OPEN_CURRENT_CONFLICT',
    label: '保存当前决定',
    conflictId: 'conflict:receivable-debt',
  });
});

test('an empty metadata section is redirected to the pending result claim', () => {
  assert.equal(resolveReviewResultSection({
    selectedSection: 'GOAL',
    claims: [
      { claimId: 'claim:account', section: 3 },
      { claimId: 'claim:depot-head', section: 3 },
    ],
    pendingClaimIds: ['claim:depot-head'],
  }), 'OBJECT');
});

test('a previously selected section with review results is retained on re-entry', () => {
  assert.equal(resolveReviewResultSection({
    selectedSection: 'ACTIVITY',
    claims: [
      { claimId: 'claim:activity', section: 4 },
      { claimId: 'claim:depot-head', section: 3 },
    ],
    pendingClaimIds: ['claim:depot-head'],
  }), 'ACTIVITY');
});

test('a prepared next source never replaces an intentionally opened historical document', () => {
  assert.equal(shouldRetainHistoricalDocumentDuringPreparation({
    historicalDocumentOpen: true,
    visibleDocumentStableId: 'document:mysql-r1',
    preparedDocumentStableId: 'document:github-r1',
  }), true);

  assert.equal(shouldRetainHistoricalDocumentDuringPreparation({
    historicalDocumentOpen: false,
    visibleDocumentStableId: 'document:mysql-r1',
    preparedDocumentStableId: 'document:github-r1',
  }), false);
});
