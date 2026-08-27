import assert from 'node:assert/strict';
import test from 'node:test';
import { recoverCasConflict } from './capacity-recovery-consumer.ts';
import { createCp8RecoveryScenario, createCp8RecoveryScenarioAdapter } from './recovery-scenario.ts';

test('capacity recovery scenario uses real Run CAS and Deliverable pending-link recovery', async () => {
  const scenario = await createCp8RecoveryScenario();
  const before = await scenario.cas.revision();
  const after = await scenario.cas.injectStaleWrite();
  assert.equal(after.revision, before, 'rejected stale CAS must not advance the production Run revision');
  assert.equal(after.timeline.length, 0, 'rejected stale CAS must not append a Run event');
  assert.equal(after.status, 'READY');

  const quotaBefore = await scenario.quota.revision();
  const quota = await scenario.quota.injectQuota();
  assert.equal(quota.code, 'QUOTA_EXCEEDED');
  assert.equal(quota.storageEstimate?.usage, 9_000_000);
  assert.equal(await scenario.quota.revision(), quotaBefore, 'quota must not advance production Run metadata');

  const pending = await scenario.freeze.injectPending();
  assert.equal(pending.deliverable?.status, 'FROZEN');
  assert.equal(pending.deliverable?.linkState, 'FROZEN_EVENT_PENDING');
  assert.equal(pending.run?.status, 'READY_FOR_OUTPUT');
  assert.equal(pending.pendingCommand?.type, 'REVIEW_AND_FREEZE');

  const reloaded = await scenario.freeze.reload();
  assert.equal(reloaded.deliverable?.linkState, 'FROZEN_EVENT_PENDING');
  assert.deepEqual(reloaded.pendingCommand, pending.pendingCommand, 'reload must reconstruct the same recovery command');

  const linked = await scenario.freeze.reconcile();
  assert.equal(linked.deliverable?.status, 'FROZEN');
  assert.equal(linked.deliverable?.linkState, 'LINKED');
  assert.equal(linked.run?.status, 'FROZEN');
});

test('CAS loser reloads the production winner without replaying the write and retains the draft anchor', async () => {
  const recovery = createCp8RecoveryScenarioAdapter();
  const staleRevision = await recovery.cas.revision();
  assert.equal(staleRevision, 0);
  const winner = await recovery.cas.advanceWinner();
  assert.equal(winner.revision, 1);
  const presentation = await recoverCasConflict(recovery.cas, {
    preview: 'PREVIEW_READY', draft: '保留助手草稿与选择', anchor: 'issue:000000005', staleRevision,
    winner: { revision: winner.revision, status: winner.status, timelineLength: winner.timeline.length },
  });

  assert.equal(presentation.revision, winner.revision);
  assert.equal(presentation.preview, 'PREVIEW_CLEARED');
  assert.equal(presentation.draft, '保留助手草稿与选择');
  assert.equal(presentation.anchor, 'issue:000000005');
  assert.equal(recovery.cas.reloadCalls(), 1, 'CAS recovery must call the production adapter reload');
});

test('CAS recovery refuses to clear against metadata that is not the recorded production winner', async () => {
  const recovery = createCp8RecoveryScenarioAdapter();
  const staleRevision = await recovery.cas.revision();
  const winner = await recovery.cas.advanceWinner();
  await assert.rejects(
    recoverCasConflict(recovery.cas, {
      preview: 'PREVIEW_READY', draft: '保留助手草稿与选择', anchor: 'issue:000000005', staleRevision,
      winner: { revision: winner.revision + 1, status: winner.status, timelineLength: winner.timeline.length },
    }),
    /先行生产胜者|生产胜者/u,
  );
});
