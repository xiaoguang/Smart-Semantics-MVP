import type { AlignmentIssue } from '../document-alignment/types.ts';

export type AlignmentIssueFilters = {
  q: string;
  status: 'ALL' | AlignmentIssue['status'];
  severity: 'ALL' | AlignmentIssue['severity'];
};

export const defaultAlignmentIssueFilters: AlignmentIssueFilters = {
  q: '',
  status: 'OPEN',
  severity: 'ALL',
};

export function buildAlignmentIssueQuery(
  alignmentId: string,
  filters: AlignmentIssueFilters,
  after?: string | null,
) {
  return {
    alignmentId,
    q: filters.q.trim() || undefined,
    statuses: filters.status === 'ALL' ? undefined : [filters.status],
    severities: filters.severity === 'ALL' ? undefined : [filters.severity],
    first: 20,
    after: after || undefined,
  };
}
