import assert from 'node:assert/strict';
import test from 'node:test';
import { projectReviewSummary } from './review-summary.ts';

test('projects only admitted human-review material into the visible summary', () => {
  const summary = projectReviewSummary({
    sourceId: 'guanyijia_mysql',
    admittedSourceIds: ['guanyijia_mysql'],
    claims: [
      { claimId: 'claim:account', evidenceRefs: ['mysql:account'] },
      { claimId: 'claim:depot', evidenceRefs: ['mysql:depot', 'mysql:index'] },
    ],
    taskIds: ['scripted:mysql:rename-depot-head'],
    findings: [{ findingId: 'finding:debt', sourceIds: ['guanyijia_mysql', 'guanyijia_github'] }],
  });

  assert.deepEqual(summary.conclusions, { count: 2, claimIds: ['claim:account', 'claim:depot'] });
  assert.deepEqual(summary.materials, { count: 3, evidenceRefs: ['mysql:account', 'mysql:depot', 'mysql:index'] });
  assert.deepEqual(summary.pendingTasks, { count: 1, taskIds: ['scripted:mysql:rename-depot-head'] });
  assert.deepEqual(summary.findings, { count: 0, findingIds: [] });
});

test('includes a cross-source finding only after every source is admitted', () => {
  const summary = projectReviewSummary({
    sourceId: 'guanyijia_github',
    admittedSourceIds: ['guanyijia_mysql', 'guanyijia_github'],
    claims: [{ claimId: 'claim:minus-stock', evidenceRefs: ['github:config'] }],
    taskIds: [],
    findings: [
      { findingId: 'finding:minus-stock', sourceIds: ['guanyijia_mysql', 'guanyijia_github'] },
      { findingId: 'finding:policy', sourceIds: ['guanyijia_mysql', 'guanyijia_demo_policy'] },
    ],
  });

  assert.deepEqual(summary.findings, { count: 1, findingIds: ['finding:minus-stock'] });
});

test('deduplicates visible evidence references without importing legacy block counts', () => {
  const summary = projectReviewSummary({
    sourceId: 'guanyijia_mysql',
    admittedSourceIds: ['guanyijia_mysql'],
    claims: [
      { claimId: 'claim:one', evidenceRefs: ['mysql:shared'] },
      { claimId: 'claim:two', evidenceRefs: ['mysql:shared'] },
    ],
    taskIds: [],
    findings: [],
  });

  assert.deepEqual(summary.materials, { count: 1, evidenceRefs: ['mysql:shared'] });
  assert.equal('gaps' in summary, false);
  assert.equal('facts' in summary, false);
});

test('counts actionable tasks separately from non-task review conclusions while retaining all material', () => {
  const summary = projectReviewSummary({
    sourceId: 'guanyijia_mysql',
    admittedSourceIds: ['guanyijia_mysql'],
    claims: [{ claimId: 'claim:confirmed', evidenceRefs: ['mysql:confirmed'] }],
    materialClaims: [
      { claimId: 'claim:confirmed', evidenceRefs: ['mysql:confirmed'] },
      { claimId: 'claim:task', evidenceRefs: ['mysql:task'] },
    ],
    taskIds: ['task:rename-depot-head'],
    findings: [],
  });

  assert.deepEqual(summary.conclusions, { count: 1, claimIds: ['claim:confirmed'] });
  assert.deepEqual(summary.materials, { count: 2, evidenceRefs: ['mysql:confirmed', 'mysql:task'] });
  assert.deepEqual(summary.pendingTasks, { count: 1, taskIds: ['task:rename-depot-head'] });
});
