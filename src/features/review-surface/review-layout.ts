import type { ReviewSurfaceLayerKind, ReviewSurfaceViewport } from './index.ts';

export type ReviewLayoutMode = 'INLINE' | 'OVERLAY' | 'FULLSCREEN';
export type ReviewLayoutDensity = 'WIDE' | 'COMPACT' | 'NARROW';
export type ReviewScrollOwner = 'THREAD' | 'LAYER';

export type ReviewLayoutProjection = {
  mode: ReviewLayoutMode;
  density: ReviewLayoutDensity;
  topLayer: ReviewSurfaceLayerKind | 'TIMELINE';
  scrollOwner: ReviewScrollOwner;
};

const fullScreenWidth = 720;
const inlineInspectorWidth = 1136;

export function projectReviewLayout(input: {
  visualViewportWidth: number;
  containerWidth: number;
  topLayer?: ReviewSurfaceLayerKind | 'TIMELINE';
}): ReviewLayoutProjection {
  const visualWidth = Math.max(0, input.visualViewportWidth);
  const availableWidth = Math.min(visualWidth, Math.max(0, input.containerWidth));
  const topLayer = input.topLayer ?? 'TIMELINE';
  // At 200% zoom a 1440px page has a 720px visual reading frame. Treat the
  // boundary itself as narrow: an overlay at that width leaves too little
  // usable height once the assistant and browser chrome are present.
  const mode: ReviewLayoutMode = visualWidth <= fullScreenWidth || availableWidth < fullScreenWidth
    ? 'FULLSCREEN'
    : availableWidth >= inlineInspectorWidth ? 'INLINE' : 'OVERLAY';
  return {
    mode,
    density: mode === 'INLINE' ? 'WIDE' : mode === 'OVERLAY' ? 'COMPACT' : 'NARROW',
    topLayer,
    scrollOwner: mode === 'FULLSCREEN' && topLayer !== 'TIMELINE' ? 'LAYER' : 'THREAD',
  };
}

export function reviewSurfaceViewportForLayout(layout: ReviewLayoutProjection): ReviewSurfaceViewport {
  return layout.mode === 'INLINE'
    ? 'DESKTOP_INLINE'
    : layout.mode === 'OVERLAY' ? 'DESKTOP_OVERLAY' : 'MOBILE_FULLSCREEN';
}
