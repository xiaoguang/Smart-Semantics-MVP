import assert from 'node:assert/strict';
import test from 'node:test';
import { projectReviewLayout } from './review-layout.ts';

test('review layout uses the actual workbench container before choosing an inline inspector', () => {
  assert.equal(projectReviewLayout({
    visualViewportWidth: 1440,
    containerWidth: 1032,
    topLayer: 'DOCUMENT',
  }).mode, 'OVERLAY');

  assert.equal(projectReviewLayout({
    visualViewportWidth: 1280,
    containerWidth: 1136,
    topLayer: 'DOCUMENT',
  }).mode, 'INLINE');
});

test('review layout switches from overlay to fullscreen at the readable-layer boundary', () => {
  const overlay = projectReviewLayout({
    visualViewportWidth: 900,
    containerWidth: 720,
    topLayer: 'INSPECTOR',
  });
  assert.deepEqual(overlay, {
    mode: 'OVERLAY',
    density: 'COMPACT',
    topLayer: 'INSPECTOR',
    scrollOwner: 'THREAD',
  });

  const fullscreen = projectReviewLayout({
    visualViewportWidth: 720,
    containerWidth: 719,
    topLayer: 'INSPECTOR',
  });
  assert.deepEqual(fullscreen, {
    mode: 'FULLSCREEN',
    density: 'NARROW',
    topLayer: 'INSPECTOR',
    scrollOwner: 'LAYER',
  });
});

test('the visible viewport participates in narrow-mode protection at zoom', () => {
  assert.equal(projectReviewLayout({
    visualViewportWidth: 640,
    containerWidth: 1024,
    topLayer: 'TIMELINE',
  }).mode, 'FULLSCREEN');

  assert.equal(projectReviewLayout({
    visualViewportWidth: 720,
    containerWidth: 1440,
    topLayer: 'DOCUMENT',
  }).mode, 'FULLSCREEN');
});
