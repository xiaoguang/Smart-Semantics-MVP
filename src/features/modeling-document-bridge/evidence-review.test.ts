import assert from 'node:assert/strict';
import test from 'node:test';
import { loadConclusion, loadEvidencePage, loadIssuePage, loadReviewWorkspace } from '../data-standardization/review-workspace.ts';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { createReviewQueryService } from './review-query-service.ts';
import type { ReviewEvidence, ReviewEvidenceIndexEntry, ReviewIssue, ReviewSnapshot } from './types.ts';

test('10k问题/100k证据只读取首屏20项并在展开根来源后游标取证', async () => {
  const issues: ReviewIssue[] = Array.from({ length: 10_000 }, (_, index) => ({
    issueId: `issue-${String(index).padStart(5, '0')}`, topic: 'CAPACITY', title: `容量问题${index}`,
    severity: 'WARNING', status: 'OPEN', difference: '容量验证', claimIds: [], evidenceRefs: [],
    affectedObjectCodes: [], recommendedResolutionId: 'review',
    options: [{ resolutionId: 'review', label: '人工确认', conclusion: '保持待确认' }],
  }));
  const loaded: string[][] = [];
  const evidenceIndex: ReviewEvidenceIndexEntry[] = Array.from({ length: 100_000 }, (_, index) => {
    const evidenceId = `evidence-${String(index).padStart(6, '0')}`;
    const statement = `证据${index}`;
    const locator = { row: index };
    return {
      evidenceId, sourceId: 'source-capacity', rootSourceIds: ['root-capacity'],
      issueIds: [index < 100 ? 'issue-00000' : `issue-${String(index % 10_000).padStart(5, '0')}`],
      checksum: sha256HexSync(JSON.stringify({ statement, locator })),
    };
  });
  const snapshot: ReviewSnapshot = {
    overview: { sourceCount: 1, rootSourceCount: 1, evidenceCount: 100_000, claimCount: 100_000,
      issueCount: 10_000, openIssueCount: 10_000, decisionCount: 0, lineageGapCount: 0 },
    issues, decisions: [], evidenceIndex,
  };
  const service = createReviewQueryService({ artifactId: 'capacity', revision: 1, snapshot, loadEvidence: async (ids) => {
    loaded.push(ids);
    return ids.map((evidenceId): ReviewEvidence => {
      const index = Number(evidenceId.slice('evidence-'.length));
      const statement = `证据${index}`; const locator = { row: index };
      return { evidenceId, sourceId: 'source-capacity', rootSourceIds: ['root-capacity'], statement, locator,
        checksum: sha256HexSync(JSON.stringify({ statement, locator })) };
    });
  } });
  const runtime = { review: async () => service };

  const firstPaint = await loadReviewWorkspace(runtime, 'capacity');
  assert.equal(firstPaint.issues.items.length, 20);
  assert.equal(firstPaint.issues.total, 10_000);
  assert.equal(firstPaint.overview.evidenceCount, 100_000);
  assert.deepEqual(loaded, [], '首屏不能读取任何证据正文');
  const secondIssuePage = await loadIssuePage(service, 'OPEN', firstPaint.issues.nextCursor!);
  assert.equal(secondIssuePage.items[0]?.issueId, 'issue-00020');
  const conclusion = await loadConclusion(service, 'issue-00000');
  assert.equal(conclusion.evidenceRoots[0]?.evidenceCount, 109);
  assert.deepEqual(loaded, [], '结论层仍不能读取证据正文');
  const firstEvidencePage = await loadEvidencePage(service, { issueId: 'issue-00000', rootSourceId: 'root-capacity' });
  assert.equal(firstEvidencePage.items.length, 20);
  assert.equal(loaded.flat().length, 20);
  const secondEvidencePage = await loadEvidencePage(service, { issueId: 'issue-00000', rootSourceId: 'root-capacity', cursor: firstEvidencePage.nextCursor! });
  assert.equal(secondEvidencePage.items.length, 20);
  assert.equal(loaded.flat().length, 40);
});

test('大规模审核使用轻量摘要、全局过滤和绑定查询条件的稳定游标', async () => {
  const issues: ReviewIssue[] = Array.from({ length: 10_000 }, (_, index) => ({
    issueId: `issue-${String(index).padStart(5, '0')}`,
    topic: index % 2 ? 'TIME' : 'METRIC',
    title: index === 9_999 ? '营业日归属终审问题' : `审核问题${index}`,
    severity: index % 100 === 0 ? 'BLOCKER' : 'WARNING',
    status: 'OPEN', difference: '来源结论不同',
    claimIds: Array.from({ length: 30 }, (_, claim) => `claim-${index}-${claim}`),
    evidenceRefs: Array.from({ length: 50 }, (_, evidence) => `evidence-${index}-${evidence}`),
    affectedObjectCodes: [`object-${index}`], recommendedResolutionId: 'review',
    options: [{ resolutionId: 'review', label: '人工确认', conclusion: '保持待确认' }],
  }));
  const evidenceIndex: ReviewEvidenceIndexEntry[] = Array.from({ length: 100_000 }, (_, index) => {
    const evidenceId = `evidence-${String(index).padStart(6, '0')}`;
    const statement = `证据${index}`; const locator = { row: index };
    return { evidenceId, sourceId: `source-${index % 8}`, rootSourceIds: [`root-${index % 4}`],
      issueIds: [`issue-${String(index % 10_000).padStart(5, '0')}`],
      checksum: sha256HexSync(JSON.stringify({ statement, locator })) };
  });
  let bodyLoads = 0;
  const service = createReviewQueryService({
    artifactId: 'capacity-indexed', revision: 7,
    snapshot: { overview: { sourceCount: 8, rootSourceCount: 4, evidenceCount: 100_000, claimCount: 300_000,
      issueCount: 10_000, openIssueCount: 10_000, decisionCount: 0, lineageGapCount: 0 },
      issues, decisions: [], evidenceIndex },
    loadEvidence: async (ids) => { bodyLoads += ids.length; return ids.map((evidenceId) => {
      const index = Number(evidenceId.slice('evidence-'.length)); const statement = `证据${index}`; const locator = { row: index };
      return { evidenceId, sourceId: `source-${index % 8}`, rootSourceIds: [`root-${index % 4}`], statement, locator,
        checksum: sha256HexSync(JSON.stringify({ statement, locator })) };
    }); },
  });
  const header = await service.getHeader();
  assert.equal(header.gate.blockingOpenCount, 100);
  assert.equal(header.facets.severities.BLOCKER, 100);
  const search = await service.listIssueSummaries({ q: '营业日归属', first: 20 });
  assert.equal(search.total, 1, '搜索必须命中首屏之外的问题');
  assert.equal(search.items[0]?.issueId, 'issue-09999');
  assert.equal('claimIds' in search.items[0]!, false);
  assert.equal('evidenceRefs' in search.items[0]!, false);
  assert.equal(search.items[0]?.claimCount, 30);
  assert.equal(bodyLoads, 0);
  const firstPage = await service.listIssueSummaries({ severities: ['WARNING'], first: 20 });
  assert.ok(firstPage.nextCursor);
  await assert.rejects(() => service.listIssueSummaries({ severities: ['BLOCKER'], first: 20,
    after: firstPage.nextCursor! }), /游标无效或不属于当前查询/);
  const issue = await service.getIssue('issue-09999');
  assert.equal(issue.claimIds.length, 30);
  const evidenceSummaries = await service.listEvidenceSummaries({ issueId: 'issue-00000', rootSourceId: 'root-0', first: 20 });
  assert.equal(evidenceSummaries.items.length, 10);
  assert.equal(bodyLoads, 0, '证据摘要不能读取正文');
  await service.getEvidence(evidenceSummaries.items[0]!.evidenceId);
  assert.equal(bodyLoads, 1, '只有打开单条证据详情时才读取正文');
});
