import type { Cp8RecoveryScenarioAdapter } from './recovery-scenario.ts';

export type CasRecoveryPresentation = {
  revision: number;
  preview: 'PREVIEW_READY' | 'PREVIEW_CLEARED';
  draft: string;
  anchor: string;
};

export type CasWinnerMetadata = {
  revision: number;
  status: string;
  timelineLength: number;
};

export async function recoverCasConflict(
  adapter: Pick<Cp8RecoveryScenarioAdapter['cas'], 'injectStaleWrite' | 'reload'>,
  current: Pick<CasRecoveryPresentation, 'preview' | 'draft' | 'anchor'> & {
    staleRevision: number;
    winner: CasWinnerMetadata;
  },
): Promise<CasRecoveryPresentation> {
  if (current.staleRevision >= current.winner.revision) {
    throw new Error('CAS恢复缺少先行生产胜者：旧revision必须早于胜者revision');
  }
  const staleWriteResult = await adapter.injectStaleWrite(current.staleRevision);
  const reloaded = await adapter.reload();
  if (reloaded.revision !== staleWriteResult.revision
    || reloaded.revision !== current.winner.revision
    || reloaded.status !== current.winner.status
    || reloaded.timeline.length !== current.winner.timelineLength) {
    throw new Error('CAS恢复未读取到先行生产胜者，禁止清除预览');
  }
  return {
    revision: reloaded.revision,
    preview: 'PREVIEW_CLEARED',
    draft: current.draft,
    anchor: current.anchor,
  };
}
