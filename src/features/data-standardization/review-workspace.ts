import type {
  BatchOverview, CursorPage, ReviewConclusion, ReviewEvidence, ReviewIssue,
  ModelingDocumentArtifact, ReviewIssueStatus, ReviewQueryService,
} from '../modeling-document-bridge/types.ts';

export const REVIEW_PAGE_SIZE = 20;

export type DecisionDraft = { resolutionId?: string; reason: string };

type ReviewRuntime = Pick<{ review(artifactId: string): Promise<ReviewQueryService> }, 'review'>;

export type ReviewWorkspace = {
  service: ReviewQueryService;
  overview: BatchOverview;
  issues: CursorPage<ReviewIssue>;
};

export async function loadReviewWorkspace(
  runtime: ReviewRuntime,
  artifactId: string,
  status: ReviewIssueStatus | 'ALL' = 'OPEN',
): Promise<ReviewWorkspace> {
  const service = await runtime.review(artifactId);
  const [overview, issues] = await Promise.all([
    service.getOverview(),
    service.listIssues({ status, limit: REVIEW_PAGE_SIZE }),
  ]);
  return { service, overview, issues };
}

export function loadIssuePage(
  service: ReviewQueryService,
  status: ReviewIssueStatus | 'ALL',
  cursor?: string,
): Promise<CursorPage<ReviewIssue>> {
  return service.listIssues({ status, cursor, limit: REVIEW_PAGE_SIZE });
}

export function loadConclusion(service: ReviewQueryService, issueId: string): Promise<ReviewConclusion> {
  return service.getConclusion(issueId);
}

export function loadEvidencePage(
  service: ReviewQueryService,
  query: { issueId: string; rootSourceId: string; cursor?: string },
): Promise<CursorPage<ReviewEvidence>> {
  return service.listEvidence({ ...query, limit: REVIEW_PAGE_SIZE });
}

export function appendCursorPage<T>(current: CursorPage<T>, next: CursorPage<T>): CursorPage<T> {
  return { items: [...current.items, ...next.items], nextCursor: next.nextCursor, total: next.total };
}

export function blockingReviewIssues(issues: ReviewIssue[]) {
  return issues.filter((issue) => issue.status === 'OPEN' && ['BLOCKER', 'ERROR', 'GAP'].includes(issue.severity));
}

export function prepareDecisionInputs(issues: ReviewIssue[], drafts: Record<string, DecisionDraft>) {
  const blocking = blockingReviewIssues(issues);
  const completed = blocking.filter((issue) => {
    const draft = drafts[issue.issueId];
    return Boolean(draft?.resolutionId && draft.reason.trim());
  }).length;
  const ready = blocking.length > 0 && completed === blocking.length;
  return {
    ready,
    completed,
    total: blocking.length,
    inputs: ready ? blocking.map((issue) => ({
      issueId: issue.issueId,
      resolutionId: drafts[issue.issueId].resolutionId!,
      reason: drafts[issue.issueId].reason.trim(),
    })) : [],
  };
}

export function resetIssueQueryState(openIssues: CursorPage<ReviewIssue>) {
  return { issueStatus: 'OPEN' as const, issues: openIssues, openIssues };
}

export function shouldGateDocumentDecision(
  status: ModelingDocumentArtifact['status'],
  openIssues: CursorPage<ReviewIssue> | null,
  drafts: Record<string, DecisionDraft>,
) {
  if (status !== 'AWAITING_CONFIRMATION' || !openIssues) return false;
  if (openIssues.nextCursor) return true;
  const preparation = prepareDecisionInputs(openIssues.items, drafts);
  return preparation.total > 0 && !preparation.ready;
}
