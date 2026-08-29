import type { SourcePreparationStage } from './guanyijia-workbench-runtime.ts';

export type SourcePreparationPresentationMode = 'DEMO' | 'LIVE';

export const DEMO_STAGE_DWELL_MS: Readonly<Record<SourcePreparationStage, number>> = {
  READ: 500,
  ANALYZE: 700,
  ORGANIZE: 700,
};

/**
 * Demo dwell is presentation-only. LIVE integrations report the same actual
 * stage callbacks but never add an artificial wait.
 */
export function sourcePreparationPresentationDelayMs(
  mode: SourcePreparationPresentationMode,
  progress: Pick<{ stage: SourcePreparationStage; state: 'ACTIVE' | 'COMPLETE' | 'ERROR' }, 'stage' | 'state'>,
) {
  return mode === 'DEMO' && progress.state === 'ACTIVE'
    ? DEMO_STAGE_DWELL_MS[progress.stage]
    : 0;
}
