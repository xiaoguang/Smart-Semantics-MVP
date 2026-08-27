import type { WorkspaceSnapshot } from './runtime-types.ts';
import type { DocumentVersion, ModelVersion } from './types.ts';

export type WorkflowVersionView = {
  documentVersion: DocumentVersion;
  modelVersion: ModelVersion | null;
  fileName: string;
  status: 'GENERATED' | 'NEEDS_SUPPLEMENT' | 'PUBLISHED';
  collapsed: boolean;
  decided: number;
  total: number;
};

export type StickyWorkflowStatus = {
  stage: 'WAITING' | 'ANALYZING' | 'SOURCE_READY' | 'REVIEW' | 'BLOCKED' | 'PUBLISHED';
  documentVersion: DocumentVersion | null;
  fileName: string | null;
  modelVersion: ModelVersion | null;
  currentCatalogVersion: ModelVersion | null;
  percent: number;
  autoApproved: number;
  userApproved: number;
  rejected: number;
  pending: number;
};

export type WorkflowAnalysisState = {
  documentVersion: DocumentVersion;
  activeIndex: number;
  completed: boolean;
};

export function projectWorkflowVersions(snapshot: WorkspaceSnapshot): WorkflowVersionView[] {
  const currentIndex = snapshot.results.length - 1;
  return snapshot.results.map((result, index) => ({
    documentVersion: result.documentVersion,
    modelVersion: result.fixture.modelVersion,
    fileName: result.fixture.fileName,
    status: result.status,
    collapsed: index !== currentIndex,
    decided: result.decidedItemCount,
    total: result.totalItemCount,
  }));
}

export function projectStickyStatus(
  snapshot: WorkspaceSnapshot,
  analysis?: WorkflowAnalysisState | null,
  evidenceOnly = false,
): StickyWorkflowStatus {
  if (analysis && !analysis.completed) {
    const document = snapshot.fixture.documents.find((item) => item.documentVersion === analysis.documentVersion);
    return {
      stage: 'ANALYZING', documentVersion: analysis.documentVersion,
      fileName: document?.fileName ?? null, modelVersion: document?.modelVersion ?? null,
      currentCatalogVersion: snapshot.currentCatalogVersion,
      percent: Math.round((analysis.activeIndex + 1) / 7 * 100),
      autoApproved: 0, userApproved: 0, rejected: 0, pending: 0,
    };
  }
  const result = snapshot.results.at(-1);
  if (!result) {
    const sourceBatch = snapshot.sourceWorkspace?.batch;
    const candidateModel = snapshot.sourceWorkspace?.candidateModel;
    return {
      stage: candidateModel
        ? 'SOURCE_READY'
        : sourceBatch?.state === 'BLOCKED'
        ? 'BLOCKED'
        : evidenceOnly && sourceBatch?.sourceIds.length
          ? 'SOURCE_READY'
          : sourceBatch && sourceBatch.state !== 'COLLECTING' ? 'ANALYZING' : 'WAITING',
      documentVersion: null,
      fileName: candidateModel ? `${candidateModel.counts.entities + candidateModel.counts.events} 个候选业务对象` : sourceBatch ? `${sourceBatch.sourceIds.length} 个来源` : null,
      modelVersion: sourceBatch ? 'V1' : null,
      currentCatalogVersion: snapshot.currentCatalogVersion, percent: 0,
      autoApproved: 0, userApproved: 0, rejected: 0, pending: 0,
    };
  }
  const decisions = Object.values(result.decisions);
  return {
    stage: result.status === 'PUBLISHED' ? 'PUBLISHED' : result.status === 'NEEDS_SUPPLEMENT' ? 'BLOCKED' : 'REVIEW',
    documentVersion: result.documentVersion,
    fileName: result.fixture.fileName,
    modelVersion: result.fixture.modelVersion,
    currentCatalogVersion: snapshot.currentCatalogVersion,
    percent: result.totalItemCount ? Math.round(result.decidedItemCount / result.totalItemCount * 100) : 0,
    autoApproved: result.autoApprovedItemCount,
    userApproved: decisions.filter((item) => item.source === 'USER' && item.decision === 'APPROVED').length,
    rejected: decisions.filter((item) => item.source === 'USER' && item.decision === 'REJECTED').length,
    pending: result.pendingItemCount,
  };
}
