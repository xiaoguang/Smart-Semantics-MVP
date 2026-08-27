/**
 * The only counts shown to a reviewer.  Formal compilation blocks can include
 * compatibility records and broad source inventory gaps, so they are not a
 * reliable measure of what a person can actually read or decide in this run.
 */
export type ReviewSummaryProjection = {
  conclusions: { count: number; claimIds: string[] };
  materials: { count: number; evidenceRefs: string[] };
  pendingTasks: { count: number; taskIds: string[] };
  findings: { count: number; findingIds: string[] };
};

/** The four exact reading sets exposed by the review summary toolbar. */
export type ReviewSummaryFilter =
  | 'CONCLUSIONS'
  | 'MATERIALS'
  | 'TASKS'
  | 'FINDINGS';

type SummaryClaim = { claimId: string; evidenceRefs: readonly string[] };
type SummaryFinding = { findingId: string; sourceIds: readonly string[] };

export function projectReviewSummary(input: {
  sourceId: string;
  admittedSourceIds: readonly string[];
  /** All material may include pending review tasks; conclusion count must not. */
  materialClaims?: readonly SummaryClaim[];
  claims: readonly SummaryClaim[];
  taskIds: readonly string[];
  findings: readonly SummaryFinding[];
}): ReviewSummaryProjection {
  const admitted = new Set(input.admittedSourceIds);
  const claimIds = [...new Set(input.claims.map((claim) => claim.claimId))];
  const evidenceRefs = [...new Set((input.materialClaims ?? input.claims)
    .flatMap((claim) => claim.evidenceRefs))];
  const taskIds = [...new Set(input.taskIds)];
  const findingIds = input.findings
    .filter((finding) => finding.sourceIds.length > 0
      && finding.sourceIds.every((sourceId) => admitted.has(sourceId)))
    .map((finding) => finding.findingId)
    .filter((findingId, index, all) => all.indexOf(findingId) === index);

  return {
    conclusions: { count: claimIds.length, claimIds },
    materials: { count: evidenceRefs.length, evidenceRefs },
    pendingTasks: { count: taskIds.length, taskIds },
    findings: { count: findingIds.length, findingIds },
  };
}
