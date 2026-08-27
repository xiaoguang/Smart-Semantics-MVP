import type {
  BatchOverview, CursorPage, ReviewConclusion, ReviewEvidence,
  ReviewEvidenceIndexEntry, ReviewIssueStatus, ReviewSnapshot,
  ReviewEvidenceSummary, ReviewHeader, ReviewIssue, ReviewIssueSummary, ScalableReviewQueryService,
} from './types.ts';
import { sha256HexSync } from '../ai-modeling/sha256.ts';

const clone = <T,>(value: T): T => structuredClone(value);

function readOffset(cursor: string | undefined, scope: string) {
  if (!cursor) return 0;
  const prefix = `${scope}:`;
  const offset = cursor.startsWith(prefix) ? cursor.slice(prefix.length) : '';
  if (!/^\d+$/.test(offset)) throw new Error('游标无效或不属于当前查询');
  return Number(offset);
}

function page<T>(items: T[], offset: number, limit: number, scope: string): CursorPage<T> {
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) throw new Error('分页大小必须在1到100之间');
  if (offset > items.length) throw new Error('游标已超出结果范围');
  const selected = items.slice(offset, offset + limit);
  const nextOffset = offset + selected.length;
  return {
    items: clone(selected),
    nextCursor: nextOffset < items.length ? `${scope}:${nextOffset}` : null,
    total: items.length,
  };
}

function queryScope(prefix: string, revision: number, query: unknown) {
  return `${prefix}-${revision}-${sha256HexSync(JSON.stringify(query)).slice(0, 16)}`;
}

function keysetPage<T>(
  items: T[],
  key: (item: T) => string,
  first: number,
  after: string | undefined,
  scope: string,
): CursorPage<T> {
  if (!Number.isInteger(first) || first < 1 || first > 100) throw new Error('分页大小必须在1到100之间');
  let start = 0;
  if (after) {
    const prefix = `${scope}:`;
    if (!after.startsWith(prefix)) throw new Error('游标无效或不属于当前查询');
    const lastKey = decodeURIComponent(after.slice(prefix.length));
    const index = items.findIndex((item) => key(item) === lastKey);
    if (index < 0) throw new Error('游标已过期');
    start = index + 1;
  }
  const selected = items.slice(start, start + first);
  const last = selected.at(-1);
  return {
    items: clone(selected),
    nextCursor: start + selected.length < items.length && last
      ? `${scope}:${encodeURIComponent(key(last))}` : null,
    total: items.length,
  };
}

function validateEvidence(item: ReviewEvidence, expected: ReviewEvidenceIndexEntry) {
  if (item.sourceId !== expected.sourceId) throw new Error(`证据来源与索引不一致：${item.evidenceId}`);
  if (JSON.stringify([...item.rootSourceIds].sort()) !== JSON.stringify([...expected.rootSourceIds].sort())) {
    throw new Error(`证据根来源与索引不一致：${item.evidenceId}`);
  }
  const contentChecksum = sha256HexSync(JSON.stringify({ statement: item.statement, locator: item.locator }));
  if (item.checksum !== contentChecksum) throw new Error(`证据内容Checksum不一致：${item.evidenceId}`);
  if (item.checksum !== expected.checksum) throw new Error(`证据Checksum与来源索引不一致：${item.evidenceId}`);
  return item;
}

export function createReviewQueryService(input: {
  artifactId: string;
  revision: number;
  snapshot: ReviewSnapshot;
  loadEvidence: (evidenceIds: string[]) => Promise<ReviewEvidence[]>;
}): ScalableReviewQueryService {
  const issueById = new Map(input.snapshot.issues.map((item) => [item.issueId, item]));
  const decisionByIssueId = new Map(input.snapshot.decisions.map((item) => [item.issueId, item]));
  const evidenceById = new Map(input.snapshot.evidenceIndex.map((item) => [item.evidenceId, item]));
  const evidenceByIssueRoot = new Map<string, ReviewEvidenceIndexEntry[]>();
  input.snapshot.evidenceIndex.forEach((entry) => entry.issueIds.forEach((issueId) =>
    entry.rootSourceIds.forEach((rootSourceId) => {
      const key = `${issueId}\u0000${rootSourceId}`;
      evidenceByIssueRoot.set(key, [...(evidenceByIssueRoot.get(key) ?? []), entry]);
    })));
  evidenceByIssueRoot.forEach((items) => items.sort((left, right) => left.evidenceId.localeCompare(right.evidenceId)));

  async function loadAndValidate(evidenceIds: string[]) {
    const evidence = evidenceIds.length ? await input.loadEvidence(evidenceIds) : [];
    const byId = new Map(evidence.map((item) => [item.evidenceId, item]));
    return evidenceIds.map((evidenceId) => {
      const item = byId.get(evidenceId);
      const expected = evidenceById.get(evidenceId);
      if (!item) throw new Error(`证据加载结果缺少 ${evidenceId}`);
      if (!expected) throw new Error(`证据索引缺少 ${evidenceId}`);
      return validateEvidence(item, expected);
    });
  }

  return {
    async getHeader(): Promise<ReviewHeader> {
      const statuses = { OPEN: 0, DECIDED: 0 };
      const severities = { BLOCKER: 0, ERROR: 0, GAP: 0, WARNING: 0 };
      const topics: Record<string, number> = {};
      for (const issue of input.snapshot.issues) {
        statuses[issue.status] += 1;
        severities[issue.severity] += 1;
        topics[issue.topic] = (topics[issue.topic] ?? 0) + 1;
      }
      const blockingOpenCount = input.snapshot.issues.filter((issue) => issue.status === 'OPEN'
        && ['BLOCKER', 'ERROR', 'GAP'].includes(issue.severity)).length;
      return clone({
        overview: { artifactId: input.artifactId, revision: input.revision, ...input.snapshot.overview },
        gate: { blockingOpenCount, canConfirm: blockingOpenCount === 0 },
        facets: { statuses, severities, topics },
      });
    },
    async listIssueSummaries(query = {}) {
      const statuses = query.statuses?.length ? query.statuses : ['OPEN' as const];
      const severities = query.severities?.length ? query.severities : null;
      const topics = query.topics?.length ? query.topics : null;
      const q = query.q?.trim().toLocaleLowerCase() ?? '';
      const normalizedQuery = { q, statuses: [...statuses].sort(), severities: severities ? [...severities].sort() : [], topics: topics ? [...topics].sort() : [] };
      const items: ReviewIssueSummary[] = input.snapshot.issues
        .filter((issue) => statuses.includes(issue.status)
          && (!severities || severities.includes(issue.severity))
          && (!topics || topics.includes(issue.topic))
          && (!q || `${issue.title}\n${issue.topic}\n${issue.difference}`.toLocaleLowerCase().includes(q)))
        .sort((left, right) => left.issueId.localeCompare(right.issueId))
        .map((issue) => ({
          issueId: issue.issueId, topic: issue.topic, title: issue.title, severity: issue.severity,
          status: issue.status, difference: issue.difference, claimCount: issue.claimIds.length,
          evidenceCount: issue.evidenceRefs.length, affectedObjectCount: issue.affectedObjectCodes.length,
          optionCount: issue.options.length,
        }));
      const scope = queryScope('issue-summaries', input.revision, normalizedQuery);
      return keysetPage(items, (item) => item.issueId, query.first ?? 20, query.after, scope);
    },
    async getIssue(issueId): Promise<ReviewIssue> {
      const issue = issueById.get(issueId);
      if (!issue) throw new Error('审核问题不存在');
      return clone(issue);
    },
    async listEvidenceSummaries(query) {
      if (!issueById.has(query.issueId)) throw new Error('审核问题不存在');
      const q = query.q?.trim().toLocaleLowerCase() ?? '';
      const items: ReviewEvidenceSummary[] = (evidenceByIssueRoot.get(`${query.issueId}\u0000${query.rootSourceId}`) ?? [])
        .filter((entry) => !q || `${entry.evidenceId}\n${entry.sourceId}`.toLocaleLowerCase().includes(q))
        .map((entry) => ({ evidenceId: entry.evidenceId, sourceId: entry.sourceId,
          rootSourceIds: entry.rootSourceIds, checksum: entry.checksum }));
      const scope = queryScope('evidence-summaries', input.revision, {
        issueId: query.issueId, rootSourceId: query.rootSourceId, q,
      });
      return keysetPage(items, (item) => item.evidenceId, query.first ?? 20, query.after, scope);
    },
    async getEvidence(evidenceId) {
      if (!evidenceById.has(evidenceId)) throw new Error('证据不存在');
      return clone((await loadAndValidate([evidenceId]))[0]!);
    },
    async getOverview(): Promise<BatchOverview> {
      return clone({ artifactId: input.artifactId, revision: input.revision, ...input.snapshot.overview });
    },
    async listIssues(query = {}) {
      const status: ReviewIssueStatus | 'ALL' = query.status ?? 'OPEN';
      const items = input.snapshot.issues
        .filter((item) => status === 'ALL' || item.status === status)
        .sort((left, right) => left.issueId.localeCompare(right.issueId));
      const scope = `issues-${status}`;
      return page(items, readOffset(query.cursor, scope), query.limit ?? 25, scope);
    },
    async getConclusion(issueId): Promise<ReviewConclusion> {
      const issue = issueById.get(issueId);
      if (!issue) throw new Error('审核问题不存在');
      const rootCounts = new Map<string, number>();
      input.snapshot.evidenceIndex
        .filter((item) => item.issueIds.includes(issueId))
        .forEach((item) => item.rootSourceIds.forEach((rootSourceId) =>
          rootCounts.set(rootSourceId, (rootCounts.get(rootSourceId) ?? 0) + 1)));
      return clone({
        issue,
        decision: decisionByIssueId.get(issueId) ?? null,
        evidenceRoots: [...rootCounts.entries()]
          .map(([rootSourceId, evidenceCount]) => ({ rootSourceId, evidenceCount }))
          .sort((left, right) => left.rootSourceId.localeCompare(right.rootSourceId)),
      });
    },
    async listEvidence(query) {
      if (!issueById.has(query.issueId)) throw new Error('审核问题不存在');
      const index = input.snapshot.evidenceIndex
        .filter((item) => item.issueIds.includes(query.issueId) && item.rootSourceIds.includes(query.rootSourceId))
        .sort((left, right) => left.evidenceId.localeCompare(right.evidenceId));
      const scope = `evidence-${query.issueId}-${query.rootSourceId}`;
      const indexPage = page<ReviewEvidenceIndexEntry>(index, readOffset(query.cursor, scope), query.limit ?? 25, scope);
      const evidenceIds = indexPage.items.map((item) => item.evidenceId);
      const ordered = await loadAndValidate(evidenceIds);
      return { items: clone(ordered), nextCursor: indexPage.nextCursor, total: indexPage.total };
    },
  };
}
