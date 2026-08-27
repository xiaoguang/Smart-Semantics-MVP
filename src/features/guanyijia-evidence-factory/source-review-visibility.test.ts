import assert from 'node:assert/strict';
import test from 'node:test';
import {
  projectSourceReviewVisibility,
  type AdmittedSource,
} from './source-review-visibility.ts';

const snapshots = {
  guanyijia_mysql: '20260813T032528Z-abb0502c7d79',
  guanyijia_github: '20260813032126Z-5821d0ece9b1',
  guanyijia_official_docs: 'gyjerp-official-docs-20260813T031656Z',
  guanyijia_demo_policy: 'guanyijia-demo-policy-f6c6d209ffe3fd53',
  guanyijia_semantica_demo: 'guanyijia-semantica-demo-ff948845dc5bd778',
} as const;

function sources(
  states: Partial<Record<keyof typeof snapshots, AdmittedSource['status']>>,
): AdmittedSource[] {
  return Object.entries(snapshots).map(([sourceId, snapshotId]) => ({
    sourceId,
    snapshotId,
    status: states[sourceId as keyof typeof snapshots] ?? 'PENDING',
  }));
}

test('pending and reading sources expose no document evidence, comparisons, or decisions', () => {
  const pending = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_mysql',
    sources: sources({ guanyijia_mysql: 'PENDING' }),
  });
  assert.equal(pending.sourceDocument.state, 'PENDING');
  assert.deepEqual(pending.comparisonFindings, []);
  assert.equal(pending.actionableConflict, undefined);

  const reading = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_mysql',
    sources: sources({ guanyijia_mysql: 'READING' }),
  });
  assert.equal(reading.sourceDocument.state, 'READING');
  assert.equal(reading.sourceDocument.message, '正在校验并载入固定快照');
  assert.deepEqual(reading.comparisonFindings, []);
});

test('MySQL document is source-only; GitHub comparisons appear only after both exact snapshots are admitted', () => {
  const mysqlOnly = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_mysql',
    sources: sources({ guanyijia_mysql: 'DOCUMENT_READY' }),
  });
  assert.equal(mysqlOnly.sourceDocument.state, 'ADMITTED');
  assert.deepEqual(mysqlOnly.comparisonFindings, []);

  const githubPending = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_github',
    sources: sources({ guanyijia_mysql: 'REVIEWED', guanyijia_github: 'PENDING' }),
  });
  assert.deepEqual(githubPending.comparisonFindings, []);

  const githubReady = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_github',
    sources: sources({ guanyijia_mysql: 'REVIEWED', guanyijia_github: 'DOCUMENT_READY' }),
  });
  assert.deepEqual(githubReady.comparisonFindings.map((item) => [item.topic, item.relation]), [
    ['NEGATIVE_STOCK', 'COMPLEMENTS'],
    ['DEBT_FIELDS', 'CONFLICTS'],
    ['DOCUMENT_STATUS', 'TEMPORAL_DRIFT'],
  ]);
  assert.equal(githubReady.actionableConflict, undefined);
});

test('a comparison includes the already admitted material from both participating sources', () => {
  const githubReady = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_github',
    sources: sources({ guanyijia_mysql: 'REVIEWED', guanyijia_github: 'DOCUMENT_READY' }),
  });

  for (const finding of githubReady.comparisonFindings) {
    assert.deepEqual(new Set(finding.evidence.map((evidence) => evidence.sourceId)), new Set([
      'guanyijia_mysql',
      'guanyijia_github',
    ]));
  }
});

test('an admitted source with a mismatched snapshot can be read but contributes no comparison or conflict', () => {
  const mismatched = sources({ guanyijia_mysql: 'REVIEWED', guanyijia_github: 'CONFLICT_BLOCKED' });
  mismatched.find((source) => source.sourceId === 'guanyijia_github')!.snapshotId = 'wrong-snapshot';
  const result = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_github',
    currentConflictId: 'gyj-conflict-debt-schema',
    sources: mismatched,
  });
  assert.equal(result.sourceDocument.state, 'ADMITTED');
  assert.deepEqual(result.comparisonFindings, []);
  assert.equal(result.actionableConflict, undefined);
});

test('formal decisions appear only in conflict-blocked state after every required source is admitted', () => {
  const ready = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_github',
    currentConflictId: 'gyj-conflict-debt-schema',
    sources: sources({ guanyijia_mysql: 'REVIEWED', guanyijia_github: 'DOCUMENT_READY' }),
  });
  assert.equal(ready.actionableConflict, undefined);

  const blocked = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_github',
    currentConflictId: 'gyj-conflict-debt-schema',
    sources: sources({ guanyijia_mysql: 'REVIEWED', guanyijia_github: 'CONFLICT_BLOCKED' }),
  });
  assert.equal(blocked.actionableConflict?.topic, 'DEBT_FIELDS');

  const policyBeforeOfficial = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_demo_policy',
    sources: sources({
      guanyijia_mysql: 'REVIEWED',
      guanyijia_github: 'REVIEWED',
      guanyijia_demo_policy: 'DOCUMENT_READY',
    }),
  });
  assert.deepEqual(policyBeforeOfficial.comparisonFindings.map((item) => item.topic), [
    'NEGATIVE_STOCK',
  ]);
  assert.ok(policyBeforeOfficial.comparisonFindings[0]?.target);

  const policyReady = projectSourceReviewVisibility({
    currentSourceId: 'guanyijia_demo_policy',
    sources: sources({
      guanyijia_mysql: 'REVIEWED',
      guanyijia_github: 'REVIEWED',
      guanyijia_official_docs: 'REVIEWED',
      guanyijia_demo_policy: 'DOCUMENT_READY',
    }),
  });
  assert.deepEqual(policyReady.comparisonFindings.map((item) => item.topic), [
    'NEGATIVE_STOCK',
    'DOCUMENT_STATUS',
  ]);
  assert.equal(policyReady.comparisonFindings.every((item) => item.target), true);
});
