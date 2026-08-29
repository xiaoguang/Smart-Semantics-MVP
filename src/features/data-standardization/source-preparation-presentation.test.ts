import assert from 'node:assert/strict';
import test from 'node:test';
import { sourcePreparationPresentationDelayMs } from './source-preparation-presentation.ts';

test('Demo only holds active preparation stages for their approved visible duration', () => {
  assert.equal(sourcePreparationPresentationDelayMs('DEMO', { stage: 'READ', state: 'ACTIVE' }), 500);
  assert.equal(sourcePreparationPresentationDelayMs('DEMO', { stage: 'ANALYZE', state: 'ACTIVE' }), 700);
  assert.equal(sourcePreparationPresentationDelayMs('DEMO', { stage: 'ORGANIZE', state: 'ACTIVE' }), 700);
  assert.equal(sourcePreparationPresentationDelayMs('DEMO', { stage: 'READ', state: 'COMPLETE' }), 0);
  assert.equal(sourcePreparationPresentationDelayMs('DEMO', { stage: 'ORGANIZE', state: 'ERROR' }), 0);
});

test('LIVE presentation never adds a synthetic source-processing delay', () => {
  assert.equal(sourcePreparationPresentationDelayMs('LIVE', { stage: 'READ', state: 'ACTIVE' }), 0);
  assert.equal(sourcePreparationPresentationDelayMs('LIVE', { stage: 'ANALYZE', state: 'ACTIVE' }), 0);
  assert.equal(sourcePreparationPresentationDelayMs('LIVE', { stage: 'ORGANIZE', state: 'ACTIVE' }), 0);
});
