import type { CandidateEvidenceStatus } from './candidate-model.ts';

export type CandidateStatusPresentation = {
  label: string;
  color: 'green' | 'blue' | 'default' | 'orange';
};

const statusPresentation: Record<CandidateEvidenceStatus, CandidateStatusPresentation> = {
  VERIFIED: { label: '多项资料确认', color: 'green' },
  CODE_SUPPORTED: { label: '代码资料支持', color: 'blue' },
  DATABASE_ONLY: { label: '待补充说明', color: 'default' },
  NEEDS_CONFIRMATION: { label: '需要确认', color: 'orange' },
};

export function presentCandidateStatus(status: CandidateEvidenceStatus): CandidateStatusPresentation {
  return statusPresentation[status];
}

export function presentCandidateEvidence(evidenceIds: readonly string[]) {
  return evidenceIds.length ? `已关联 ${evidenceIds.length} 处来源材料` : '尚未关联来源材料';
}
