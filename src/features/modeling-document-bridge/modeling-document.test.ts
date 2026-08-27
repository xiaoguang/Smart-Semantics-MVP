import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { createModelingDocumentRuntime } from './runtime.ts';
import { assertStandardModelingMarkdown } from './standard-markdown.ts';
import { compileModelingDocument } from './compile-modeling-document.ts';
import { materializeModelingDocument } from './document-materializer.ts';
import { groupRetailV1 } from '../collaboration/fixtures.ts';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { retailEvidenceEntries } from '../evidence-registry/retail-evidence-registry.ts';
import { guanyijiaEvidenceFixture, guanyijiaRepositoryEvidenceFixture } from '../ai-modeling/guanyijia-fixture.ts';
import { guanyijiaFrozenModelingArtifact } from './guanyijia-modeling-baseline.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

const sourceBatch = {
  batchId: 'guanyijia-r1', fingerprint: 'batch-guanyijia-r1',
  snapshotIds: ['guanyijia-mysql-v1'],
};

test('空浏览器状态仍可审阅并幂等交接管伊佳证据基线文档', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage() });
  const artifacts = await runtime.list('guanyijia_erp');
  assert.deepEqual(artifacts.map((item) => item.artifactId), [guanyijiaFrozenModelingArtifact.artifactId]);
  const overview = await (await runtime.review(guanyijiaFrozenModelingArtifact.artifactId)).getOverview();
  assert.equal(overview.sourceCount, 3);
  assert.equal(overview.evidenceCount, 373);
  assert.equal(overview.claimCount, 469);
  const first = await runtime.execute({
    type: 'IMPORT_TO_AI', artifactId: guanyijiaFrozenModelingArtifact.artifactId,
    markdownSha256: guanyijiaFrozenModelingArtifact.markdown.sha256,
  });
  const second = await runtime.execute({
    type: 'IMPORT_TO_AI', artifactId: guanyijiaFrozenModelingArtifact.artifactId,
    markdownSha256: guanyijiaFrozenModelingArtifact.markdown.sha256,
  });
  assert.equal(first.importRecord?.importId, second.importRecord?.importId);
});

test('审核查询先返回总览、问题和结论，展开根来源后才按游标读取证据', async () => {
  const loadedPages: string[][] = [];
  const runtime = createModelingDocumentRuntime({
    storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z',
    loadReviewEvidence: async (evidenceIds) => {
      loadedPages.push(evidenceIds);
      return evidenceIds.map((evidenceId) => {
        const entry = retailEvidenceEntries.find((item) => item.evidenceId === evidenceId)!;
        return {
          evidenceId, sourceId: entry.connectionId, rootSourceIds: [entry.connectionId],
          statement: entry.statement, locator: structuredClone(entry.locator) as unknown as Record<string, unknown>,
          checksum: sha256HexSync(JSON.stringify({ statement: entry.statement, locator: entry.locator })),
        };
      });
    },
  });
  const artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'retail_review',
    title: '零售审核资料',
    sourceBatch: { batchId: 'retail-review', fingerprint: 'retail-review-sha', snapshotIds: ['retail_mysql-snapshot-r2', 'retail_github-snapshot-r2'] },
    actorUserId: 'author_user',
  })).artifact;

  const review = await runtime.review(artifact.artifactId);
  assert.deepEqual(await review.getOverview(), {
    artifactId: artifact.artifactId, revision: artifact.revision, sourceCount: 2, rootSourceCount: 2,
    evidenceCount: 7, claimCount: 7, issueCount: 1, openIssueCount: 1, decisionCount: 0,
    lineageGapCount: 0,
  });
  const issues = await review.listIssues();
  assert.deepEqual(issues.items.map((item) => item.issueId), ['finding_net_sales_refund']);
  const conclusion = await review.getConclusion('finding_net_sales_refund');
  assert.equal(conclusion.decision, null);
  assert.deepEqual(conclusion.evidenceRoots, [
    { rootSourceId: 'retail_github', evidenceCount: 1 },
    { rootSourceId: 'retail_mysql', evidenceCount: 1 },
  ]);
  assert.equal(loadedPages.length, 0);

  const evidencePage = await review.listEvidence({
    issueId: 'finding_net_sales_refund', rootSourceId: 'retail_mysql', limit: 1,
  });
  assert.deepEqual(evidencePage.items.map((item) => item.evidenceId), ['GR023']);
  assert.equal(evidencePage.nextCursor, null);
  assert.deepEqual(loadedPages, [['GR023']]);
});

test('零售八类真实来源按累积次序形成r1到r8且每版都有可追溯delta', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  const generationCommand = {
    type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'retail_evidence_story',
    title: '零售经营标准建模资料',
    sourceBatch: {
      batchId: 'retail-b01-b08', fingerprint: 'retail-b01-b08-sha',
      snapshotIds: [
        'retail_mysql-snapshot-r2', 'retail_github-snapshot-r2', 'retail_semantica-snapshot-r2',
        'retail_sharepoint-snapshot-r2', 'retail_mongodb-snapshot-r2', 'retail_elasticsearch-snapshot-r2',
        'retail_minio-snapshot-r2', 'retail_kafka-snapshot-r2',
      ],
    },
    actorUserId: 'author_user',
  } as const;
  const artifact = (await runtime.execute(generationCommand)).artifact;
  const revisions = (await runtime.list('group_retail_ops'))
    .filter((item) => item.documentCode === 'retail_evidence_story')
    .sort((left, right) => left.revision - right.revision);

  assert.equal(artifact.revision, 8);
  assert.deepEqual(revisions.map((item) => item.revision), [1, 2, 3, 4, 5, 6, 7, 8]);
  assert.deepEqual(revisions.map((item) => item.sourceBatch?.snapshotIds.length), [1, 2, 3, 4, 5, 6, 7, 8]);
  assert.deepEqual(revisions.map((item) => item.delta?.addedSourceIds), [
    ['retail_mysql'], ['retail_github'], ['retail_semantica'], ['retail_sharepoint'],
    ['retail_mongodb'], ['retail_elasticsearch'], ['retail_minio'], ['retail_kafka'],
  ]);
  assert.equal(new Set(revisions.map((item) => item.markdown.sha256)).size, 8);
  assert.deepEqual(revisions.slice(1).map((item) => item.derivedFromArtifactId), revisions.slice(0, -1).map((item) => item.artifactId));
  assert.doesNotMatch(revisions[0].sections.OVERVIEW, /GitHub|Semantica|SharePoint|MongoDB|Elasticsearch|MinIO|Kafka/);
  assert.doesNotMatch(revisions[0].markdown.content, /当前指标 SQL|客户等级生效时间|门店营业日允许跨自然日/);
  assert.match(revisions[1].markdown.content, /当前指标 SQL 仅扣减已完成退款/);
  assert.doesNotMatch(revisions[1].markdown.content, /客户等级生效时间属于等级关系/);
  assert.match(revisions[7].sections.OVERVIEW, /MySQL.*GitHub.*Semantica.*SharePoint.*MongoDB.*Elasticsearch.*MinIO.*Kafka/s);
  assert.deepEqual(compileModelingDocument(revisions[7]).counts, {
    entities: 7, events: 5, fields: 84, relations: 15, dimensions: 11, metrics: 12,
  });

  const overviews = await Promise.all(revisions.map(async (item) => (await runtime.review(item.artifactId)).getOverview()));
  assert.deepEqual(overviews.map((item) => item.evidenceCount), [4, 7, 9, 12, 15, 18, 21, 25]);
  assert.deepEqual(overviews.map((item) => item.issueCount), [0, 1, 1, 1, 1, 2, 2, 3]);
  assert.deepEqual(overviews.map((item) => item.lineageGapCount), [0, 0, 1, 0, 0, 0, 0, 0]);
  const repeated = (await runtime.execute(generationCommand)).artifact;
  assert.equal(repeated.artifactId, artifact.artifactId);
  assert.equal((await runtime.list('group_retail_ops')).filter((item) => item.documentCode === 'retail_evidence_story').length, 8);
});

test('管伊佳只让已绑定的MySQL和GitHub进入同一修订与审核合同', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  const artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'guanyijia_erp', documentCode: 'guanyijia_ready_sources',
    title: '管伊佳ERP标准建模资料',
    sourceBatch: {
      batchId: 'guanyijia-ready', fingerprint: 'guanyijia-ready-sha',
      snapshotIds: [
        'guanyijia_mysql-snapshot-r1', 'guanyijia_github-snapshot-r1',
        'guanyijia_sharepoint-unbound', 'guanyijia_semantica-unbound',
      ],
    },
    actorUserId: 'author_user',
  })).artifact;
  const revisions = (await runtime.list('guanyijia_erp'))
    .filter((item) => item.documentCode === 'guanyijia_ready_sources')
    .sort((left, right) => left.revision - right.revision);

  assert.equal(artifact.revision, 2);
  assert.deepEqual(revisions.map((item) => item.delta?.addedSourceIds), [['guanyijia_mysql'], ['guanyijia_github']]);
  assert.deepEqual(revisions.map((item) => item.sourceBatch?.snapshotIds), [
    ['guanyijia_mysql-snapshot-r1'],
    ['guanyijia_mysql-snapshot-r1', 'guanyijia_github-snapshot-r1'],
  ]);
  const overview = await (await runtime.review(artifact.artifactId)).getOverview();
  assert.deepEqual({
    sourceCount: overview.sourceCount, rootSourceCount: overview.rootSourceCount,
    evidenceCount: overview.evidenceCount, claimCount: overview.claimCount,
    issueCount: overview.issueCount, openIssueCount: overview.openIssueCount,
  }, { sourceCount: 2, rootSourceCount: 2, evidenceCount: 6, claimCount: 6, issueCount: 3, openIssueCount: 3 });
  const issues = await (await runtime.review(artifact.artifactId)).listIssues();
  assert.deepEqual(issues.items.map((item) => [item.issueId, item.severity]), [
    ['finding_guanyijia_debt_fields', 'BLOCKER'],
    ['finding_guanyijia_extensions_pending', 'WARNING'],
    ['finding_guanyijia_status_nine_basis', 'WARNING'],
  ]);

  const readyOnlyRuntime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  const readyOnlyArtifact = (await readyOnlyRuntime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'guanyijia_erp', documentCode: 'guanyijia_ready_sources',
    title: '管伊佳ERP标准建模资料',
    sourceBatch: {
      batchId: 'guanyijia-ready', fingerprint: 'guanyijia-ready-sha',
      snapshotIds: ['guanyijia_mysql-snapshot-r1', 'guanyijia_github-snapshot-r1'],
    },
    actorUserId: 'author_user',
  })).artifact;
  assert.equal(artifact.sourceBatch?.fingerprint, readyOnlyArtifact.sourceBatch?.fingerprint);
  assert.equal(artifact.markdown.sha256, readyOnlyArtifact.markdown.sha256);
});

test('单个零售R2快照不能被M4扩张成完整八来源Sidecar', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'retail_partial_r2', title: '零售部分R2资料',
    sourceBatch: { batchId: 'partial-r2', fingerprint: 'partial-r2', snapshotIds: ['retail_mysql-snapshot-r2'] },
    actorUserId: 'author_user',
  })).artifact;
  artifact = (await runtime.execute({ type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', reason: '确认部分来源文档' })).artifact;
  artifact = (await runtime.execute({ type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '只确认当前快照' })).artifact;
  artifact = (await runtime.execute({ type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '部分资料合同有效' })).artifact;
  const proposal = materializeModelingDocument(artifact, compileModelingDocument(artifact), {
    draftId: 'partial-r2', base: groupRetailV1(),
  });
  assert.equal(proposal.materializedData.semanticSidecar?.schemaVersion, 2);
  assert.equal(proposal.materializedData.semanticSidecar?.batch.revision, 1);
  assert.equal(proposal.materializedData.semanticSidecar?.sources.length, 8);
  assert.equal(proposal.materializedData.semanticSidecar?.sources.some((source) => source.snapshotId.endsWith('-r2')), false);
});

test('零售三项人工决定形成r9且须作者确认、异人审核和完整性门禁后才能冻结', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'retail_freeze_contract',
    title: '零售经营标准建模资料',
    sourceBatch: {
      batchId: 'retail-freeze', fingerprint: 'retail-freeze-sha',
      snapshotIds: [
        'retail_mysql-snapshot-r2', 'retail_github-snapshot-r2', 'retail_semantica-snapshot-r2',
        'retail_sharepoint-snapshot-r2', 'retail_mongodb-snapshot-r2', 'retail_elasticsearch-snapshot-r2',
        'retail_minio-snapshot-r2', 'retail_kafka-snapshot-r2',
      ],
    },
    actorUserId: 'author_user',
  })).artifact;
  await assert.rejects(() => runtime.execute({
    type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '不能绕过确认和审核',
  }), /作者尚未确认/);
  await assert.rejects(() => runtime.execute({
    type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', reason: '仍有问题时不能确认',
  }), /仍有3项阻断问题未决定/);
  await assert.rejects(() => runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', decisions: [
      { issueId: 'finding_net_sales_refund', resolutionId: 'adopt_completed_refund', reason: '只决定一项不应形成r9' },
    ],
  }), /必须一次决定当前全部3项阻断问题/);

  artifact = (await runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', decisions: [
      { issueId: 'finding_net_sales_refund', resolutionId: 'adopt_completed_refund', reason: '财务制度与当前SQL一致' },
      { issueId: 'finding_bot_filter', resolutionId: 'exclude_bot_and_internal_test', reason: '压测流量不代表真实客户行为' },
      { issueId: 'finding_business_day', resolutionId: 'adopt_store_business_day', reason: '采用正式门店营业日制度' },
    ],
  })).artifact;
  assert.equal(artifact.revision, 9);
  assert.deepEqual(artifact.delta, {
    kind: 'DECISION', addedSourceIds: [], addedSnapshotIds: [], addedEvidenceRefs: [], addedClaimIds: [],
    resolvedIssueIds: ['finding_bot_filter', 'finding_business_day', 'finding_net_sales_refund'],
  });
  assert.match(artifact.sections.UNRESOLVED, /\[已决定\].*净销售额仅在退款完成后冲减/);
  assert.deepEqual(compileModelingDocument(artifact).unresolved, []);
  const review = await runtime.review(artifact.artifactId);
  assert.deepEqual((await review.listIssues()).items, []);
  assert.deepEqual((await review.listIssues({ status: 'ALL' })).items.map((item) => item.status), ['DECIDED', 'DECIDED', 'DECIDED']);
  assert.equal((await review.getOverview()).decisionCount, 3);
  assert.equal((await review.getConclusion('finding_net_sales_refund')).decision?.actorUserId, 'author_user');

  await assert.rejects(() => runtime.execute({
    type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'someone_else', reason: '不是作者',
  }), /只有文档作者/);
  artifact = (await runtime.execute({
    type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', reason: '三项决定与九段内容一致',
  })).artifact;
  assert.equal(artifact.status, 'AWAITING_REVIEW');
  assert.equal(artifact.approval?.authorConfirmation?.markdownSha256, artifact.markdown.sha256);

  await assert.rejects(() => runtime.execute({
    type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', reason: '作者不能自审',
  }), /审核批准人与作者不能相同/);
  artifact = (await runtime.execute({
    type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '证据、结论和来源血缘均已复核',
  })).artifact;
  assert.equal(artifact.status, 'APPROVED');
  assert.equal(artifact.approval?.reviewerApproval?.markdownSha256, artifact.markdown.sha256);

  artifact = (await runtime.execute({
    type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '完整性门禁通过',
  })).artifact;
  assert.equal(artifact.status, 'FROZEN');
});

test('管伊佳也必须记录结构冲突决定并经作者与异人审核后冻结', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'guanyijia_erp', documentCode: 'guanyijia_freeze_contract',
    title: '管伊佳ERP标准建模资料',
    sourceBatch: { batchId: 'guanyijia-freeze', fingerprint: 'guanyijia-freeze-sha', snapshotIds: ['guanyijia_mysql-snapshot-r1', 'guanyijia_github-snapshot-r1'] },
    actorUserId: 'author_user',
  })).artifact;
  artifact = (await runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', decisions: [{
      issueId: 'finding_guanyijia_debt_fields', resolutionId: 'keep_debt_metric_blocked',
      reason: '以当前部署结构为准，迁移和重采集完成前不发布欠款指标',
    }],
  })).artifact;
  assert.equal(artifact.revision, 3);
  assert.match(artifact.sections.UNRESOLVED, /63张/);
  assert.match(artifact.sections.UNRESOLVED, /状态9/);
  assert.equal(compileModelingDocument(artifact).unresolved.length, 2);
  artifact = (await runtime.execute({
    type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', reason: '结构冲突决定已写回标准文档',
  })).artifact;
  artifact = (await runtime.execute({
    type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '核对数据库和固定Commit证据',
  })).artifact;
  artifact = (await runtime.execute({
    type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '管伊佳合同门禁通过',
  })).artifact;
  assert.equal(artifact.status, 'FROZEN');
  const frozenOverview = await (await runtime.review(artifact.artifactId)).getOverview();
  assert.equal(frozenOverview.openIssueCount, 2);
  assert.deepEqual((await (await runtime.review(artifact.artifactId)).listIssues()).items.map((item) => item.severity), ['WARNING', 'WARNING']);
});

test('M4只接受冻结文档的精确SHA且重复导入和物化不重复写入候选', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'retail_m4_import',
    title: '零售经营标准建模资料',
    sourceBatch: {
      batchId: 'retail-m4', fingerprint: 'retail-m4-sha',
      snapshotIds: [
        'retail_mysql-snapshot-r2', 'retail_github-snapshot-r2', 'retail_semantica-snapshot-r2',
        'retail_sharepoint-snapshot-r2', 'retail_mongodb-snapshot-r2', 'retail_elasticsearch-snapshot-r2',
        'retail_minio-snapshot-r2', 'retail_kafka-snapshot-r2',
      ],
    },
    actorUserId: 'author_user',
  })).artifact;
  await assert.rejects(() => runtime.execute({
    type: 'IMPORT_TO_AI', artifactId: artifact.artifactId, markdownSha256: artifact.markdown.sha256,
  }), /只有冻结文档/);
  artifact = (await runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', decisions: [
      { issueId: 'finding_net_sales_refund', resolutionId: 'adopt_completed_refund', reason: '正式制度优先' },
      { issueId: 'finding_bot_filter', resolutionId: 'exclude_bot_and_internal_test', reason: '排除非客户流量' },
      { issueId: 'finding_business_day', resolutionId: 'adopt_store_business_day', reason: '正式营业日制度优先' },
    ],
  })).artifact;
  artifact = (await runtime.execute({ type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'author_user', reason: '确认' })).artifact;
  artifact = (await runtime.execute({ type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'reviewer_user', reason: '批准' })).artifact;
  artifact = (await runtime.execute({ type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'reviewer_user', reason: '冻结' })).artifact;

  await assert.rejects(() => runtime.execute({
    type: 'IMPORT_TO_AI', artifactId: artifact.artifactId, markdownSha256: '0'.repeat(64),
  }), /Markdown校验和不一致/);
  const firstImport = await runtime.execute({ type: 'IMPORT_TO_AI', artifactId: artifact.artifactId, markdownSha256: artifact.markdown.sha256 });
  const secondImport = await runtime.execute({ type: 'IMPORT_TO_AI', artifactId: artifact.artifactId, markdownSha256: artifact.markdown.sha256 });
  assert.equal(firstImport.importRecord?.importId, secondImport.importRecord?.importId);

  const candidate = compileModelingDocument(artifact);
  const mismatchedCandidate = structuredClone(candidate);
  mismatchedCandidate.sourceArtifact.markdownSha256 = '0'.repeat(64);
  const emptyBase = { workspaceData: { entities: [], events: [], semanticRelations: [], aliases: [], calendars: [], rules: [] }, metricData: { metrics: [], rules: [] }, workbenchState: {} };
  assert.throws(() => materializeModelingDocument(artifact, mismatchedCandidate, { draftId: 'draft-m4', base: emptyBase }), /候选来源SHA与冻结文档不一致/);
  const forgedCandidate = structuredClone(candidate);
  forgedCandidate.items.push({
    candidateId: 'forged:entity', kind: 'ENTITY', name: '伪造对象', summary: '不在冻结Markdown中',
    provenance: 'USER_CONFIRMED', evidenceRefs: [],
  });
  forgedCandidate.counts.entities += 1;
  assert.throws(() => materializeModelingDocument(artifact, forgedCandidate, { draftId: 'draft-m4', base: emptyBase }), /候选内容与冻结Markdown不一致/);
  Object.defineProperty(forgedCandidate, 'toJSON', { value: () => candidate });
  assert.throws(() => materializeModelingDocument(artifact, forgedCandidate, { draftId: 'draft-m4', base: emptyBase }), /候选内容与冻结Markdown不一致/);
  const tamperedArtifact = structuredClone(artifact);
  tamperedArtifact.sections.OBJECT += '\n| 伪造对象 | 不在冻结Markdown中 |';
  assert.throws(() => compileModelingDocument(tamperedArtifact), /Artifact章节与冻结Markdown不一致/);
  assert.throws(() => materializeModelingDocument(tamperedArtifact, candidate, { draftId: 'draft-m4', base: emptyBase }), /Artifact章节与冻结Markdown不一致/);
  const tamperedAssertionsArtifact = structuredClone(artifact);
  tamperedAssertionsArtifact.assertions.push({
    assertionId: 'dimension-forged', section: 'FIELD', statement: '维度候选“伪造维度”映射到任意对象',
    provenance: 'USER_CONFIRMED', evidenceRefs: ['EVIL'],
  });
  assert.throws(() => compileModelingDocument(tamperedAssertionsArtifact), /Artifact断言与冻结Markdown不一致/);
  assert.throws(() => materializeModelingDocument(tamperedAssertionsArtifact, candidate, { draftId: 'draft-m4', base: emptyBase }), /Artifact断言与冻结Markdown不一致/);
  const tamperedMarkdownArtifact = structuredClone(artifact);
  tamperedMarkdownArtifact.markdown.content += '\n<!-- tampered -->\n';
  assert.throws(() => materializeModelingDocument(tamperedMarkdownArtifact, candidate, { draftId: 'draft-m4', base: emptyBase }), /Markdown校验和不一致/);
  const tamperedStorage = new MemoryStorage();
  const tamperedRuntime = createModelingDocumentRuntime({ storage: tamperedStorage, now: () => '2026-08-12T12:00:00.000Z' });
  tamperedStorage.setItem('linguan:modeling-documents:v1', JSON.stringify({
    artifacts: [tamperedMarkdownArtifact], imports: [], sequence: 1,
  }));
  await assert.rejects(() => tamperedRuntime.execute({
    type: 'IMPORT_TO_AI', artifactId: tamperedMarkdownArtifact.artifactId,
    markdownSha256: tamperedMarkdownArtifact.markdown.sha256,
  }), /Markdown校验和不一致/);
  const firstProposal = materializeModelingDocument(artifact, candidate, { draftId: 'draft-m4', base: emptyBase });
  assert.equal(firstProposal.changes.length, candidate.items.length);
  assert.equal((firstProposal.materializedData.workspaceData.entities as unknown[]).length, candidate.counts.entities);
  assert.equal((firstProposal.materializedData.workspaceData.events as unknown[]).length, candidate.counts.events);
  const repeatedProposal = materializeModelingDocument(artifact, candidate, { draftId: 'draft-m4', base: firstProposal.materializedData });
  assert.deepEqual(repeatedProposal.materializedData, firstProposal.materializedData);
  assert.deepEqual(repeatedProposal.changes, []);
});

test('冻结拒绝沿用索引SHA但正文与Locator已被篡改的证据', async () => {
  const runtime = createModelingDocumentRuntime({
    storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z',
    loadReviewEvidence: async (evidenceIds) => evidenceIds.map((evidenceId) => {
      const statement = `被替换的证据 ${evidenceId}`;
      const locator = { kind: 'TAMPERED', ref: evidenceId };
      const record = guanyijiaEvidenceFixture.manifest.evidenceFiles.find((item) => item.evidenceId === evidenceId)
        ?? guanyijiaRepositoryEvidenceFixture.manifest.evidenceFiles.find((item) => item.evidenceId === evidenceId);
      return {
        evidenceId,
        sourceId: evidenceId.startsWith('mysql_') ? 'guanyijia_mysql' : 'guanyijia_github',
        rootSourceIds: [evidenceId.startsWith('mysql_') ? 'guanyijia_mysql' : 'guanyijia_github'],
        statement, locator, checksum: record!.sha256,
      };
    }),
  });
  let artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'guanyijia_erp', documentCode: 'guanyijia_checksum_gate',
    title: '管伊佳ERP标准建模资料',
    sourceBatch: { batchId: 'guanyijia-checksum', fingerprint: 'guanyijia-checksum-sha', snapshotIds: ['guanyijia_mysql-snapshot-r1', 'guanyijia_github-snapshot-r1'] },
    actorUserId: 'author_user',
  })).artifact;
  artifact = (await runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', decisions: [{ issueId: 'finding_guanyijia_debt_fields', resolutionId: 'keep_debt_metric_blocked', reason: '保持阻断' }],
  })).artifact;
  artifact = (await runtime.execute({ type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'author_user', reason: '确认' })).artifact;
  artifact = (await runtime.execute({ type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'reviewer_user', reason: '批准' })).artifact;
  await assert.rejects(() => runtime.execute({
    type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '不应通过',
  }), /证据内容Checksum不一致/);
});

test('冻结把派生来源缺少上游识别为独立的Lineage门禁', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'retail_lineage_gate',
    title: '零售经营标准建模资料',
    sourceBatch: {
      batchId: 'retail-lineage', fingerprint: 'retail-lineage-sha',
      snapshotIds: ['retail_mysql-snapshot-r2', 'retail_github-snapshot-r2', 'retail_semantica-snapshot-r2'],
    },
    actorUserId: 'author_user',
  })).artifact;
  artifact = (await runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'author_user', decisions: [{ issueId: 'finding_net_sales_refund', resolutionId: 'adopt_completed_refund', reason: '采用正式SQL' }],
  })).artifact;
  artifact = (await runtime.execute({ type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'author_user', reason: '确认' })).artifact;
  artifact = (await runtime.execute({ type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'reviewer_user', reason: '批准' })).artifact;
  await assert.rejects(() => runtime.execute({
    type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'reviewer_user', reason: '不应冻结',
  }), /来源血缘缺口/);
});

test('DDL批次生成九段标准建模文档且不伪造指标口径', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  const generated = await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'guanyijia_erp', documentCode: 'guanyijia_ddl_r1',
    title: '管伊佳数据库结构建模资料', sourceBatch, actorUserId: 'user_administer',
  });
  const artifact = generated.artifact;
  assert.equal(artifact.status, 'AWAITING_CONFIRMATION');
  assert.equal(artifact.origin, 'STANDARDIZATION');
  assertStandardModelingMarkdown(artifact.markdown.content);
  assert.match(artifact.markdown.content, /DDL只能确认物理结构/);
  assert.match(artifact.markdown.content, /指标口径需要业务资料补充/);
  assert.equal(artifact.assertions.some((item) => item.section === 'METRIC' && item.provenance === 'OBSERVED'), false);
});

test('文档按章节修订和问题决定后冻结为新不可变版本并可幂等交给AI建模', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'retail_r2',
    title: '零售经营标准建模资料',
    sourceBatch: { batchId: 'retail-r2', fingerprint: 'batch-retail-r2', snapshotIds: ['mysql-r2', 'github-r2', 'sharepoint-r2'] },
    actorUserId: 'user_administer',
  })).artifact;
  artifact = (await runtime.execute({
    type: 'UPDATE_SECTION', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    section: 'GOAL', content: '统一零售经营口径并保留退款时点的待确认项。', actorUserId: 'user_administer',
  })).artifact;
  assert.equal(artifact.revision, 4);
  assert.equal(artifact.derivedFromArtifactId?.startsWith('modeling-document-'), true);
  artifact = (await runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'user_administer', decisions: [{
      issueId: 'finding_net_sales_refund', resolutionId: 'adopt_completed_refund', reason: '采用当前正式SQL与制度口径',
    }],
  })).artifact;
  artifact = (await runtime.execute({
    type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'user_administer', reason: '章节和决定已确认',
  })).artifact;
  artifact = (await runtime.execute({
    type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'user_model_admin', reason: '异人审核通过',
  })).artifact;
  const frozen = (await runtime.execute({
    type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'user_model_admin', reason: '来源、冲突与章节已核对，可以交给AI建模',
  })).artifact;
  assert.equal(frozen.status, 'FROZEN');
  const firstImport = await runtime.execute({ type: 'IMPORT_TO_AI', artifactId: frozen.artifactId, markdownSha256: frozen.markdown.sha256 });
  const repeatedImport = await runtime.execute({ type: 'IMPORT_TO_AI', artifactId: frozen.artifactId, markdownSha256: frozen.markdown.sha256 });
  assert.equal(firstImport.importRecord?.importId, repeatedImport.importRecord?.importId);
  await assert.rejects(() => runtime.execute({
    type: 'UPDATE_SECTION', artifactId: frozen.artifactId, expectedRevision: frozen.revision,
    section: 'GOAL', content: '不得覆盖冻结版本', actorUserId: 'user_administer',
  }), /冻结文档不可修改/);
});

test('标准九段文档可以按内容生成候选且不会把缺口编造成指标', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  const erp = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'guanyijia_erp', documentCode: 'guanyijia_ddl_r1',
    title: '管伊佳数据库结构建模资料', sourceBatch, actorUserId: 'user_administer',
  })).artifact;
  const candidate = compileModelingDocument(erp);
  assert.deepEqual(candidate.counts, { entities: 3, events: 3, fields: 0, relations: 0, dimensions: 0, metrics: 0 });
  assert.equal(candidate.items.some((item) => item.kind === 'METRIC'), false);
  assert.ok(candidate.unresolved.some((item) => item.includes('指标')));
  assert.equal(candidate.sourceArtifact.artifactId, erp.artifactId);
  assert.throws(() => materializeModelingDocument(erp, candidate, {
    draftId: 'draft-erp',
    base: { workspaceData: { entities: [], events: [], semanticRelations: [], aliases: [], calendars: [], rules: [] }, metricData: { metrics: [], rules: [] }, workbenchState: {} },
  }), /只有冻结文档可以进入M4/);
});

test('管伊佳MySQL与GitHub批次生成完整19表候选并由冻结文档内容物化', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'guanyijia_erp', documentCode: 'guanyijia_mysql_github_r1',
    title: '管伊佳ERP标准建模资料',
    sourceBatch: { batchId: 'guanyijia-r1', fingerprint: 'batch-guanyijia-r1', snapshotIds: ['guanyijia_mysql-snapshot-r1', 'guanyijia_github-snapshot-r1'] },
    actorUserId: 'user_administer',
  })).artifact;
  const candidate = compileModelingDocument(artifact);
  assert.deepEqual(candidate.counts, { entities: 13, events: 6, fields: 265, relations: 18, dimensions: 14, metrics: 8 });
  assert.match(artifact.sections.UNRESOLVED, /63张/);
  assert.match(artifact.sections.UNRESOLVED, /debt/);
  artifact = (await runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'user_administer', decisions: [{
      issueId: 'finding_guanyijia_debt_fields', resolutionId: 'keep_debt_metric_blocked', reason: '部署库升级前保持欠款指标阻断',
    }],
  })).artifact;
  artifact = (await runtime.execute({ type: 'CONFIRM_AUTHOR', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'user_administer', reason: '确认' })).artifact;
  artifact = (await runtime.execute({ type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'user_model_admin', reason: '批准' })).artifact;
  artifact = (await runtime.execute({ type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId, expectedRevision: artifact.revision, actorUserId: 'user_model_admin', reason: '冻结' })).artifact;
  const frozenCandidate = compileModelingDocument(artifact);
  assert.deepEqual(frozenCandidate.counts, candidate.counts);
  const proposal = materializeModelingDocument(artifact, frozenCandidate, {
    draftId: 'draft-guanyijia-r1',
    base: { workspaceData: { entities: [], events: [], semanticRelations: [], aliases: [], calendars: [], rules: [] }, metricData: { metrics: [], rules: [] }, workbenchState: {} },
  });
  assert.equal((proposal.materializedData.workspaceData.entities as unknown[]).length, 13);
  assert.equal((proposal.materializedData.workspaceData.events as unknown[]).length, 6);
  assert.equal((proposal.materializedData.metricData.metrics as unknown[]).length, 8);
  assert.equal(proposal.changes.length, frozenCandidate.items.length);
  assert.equal(proposal.materializedData.semanticSidecar?.schemaVersion, 1);
  assert.equal(proposal.materializedData.semanticSidecar?.dimensions.length, 14);
});

test('已知人工Markdown通过兼容适配器保留原始确定性模型计数', async () => {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T12:00:00.000Z' });
  const content = readFileSync(new URL('../ai-modeling/fixtures/documents/01-库存与出入库设计-v1.md', import.meta.url), 'utf8');
  const artifact = (await runtime.execute({
    type: 'REGISTER_MANUAL_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'inventory_v1',
    fileName: 'inventory-v1.md', content, actorUserId: 'user_kenan_zhang',
  })).artifact;
  assert.equal(artifact.origin, 'MANUAL_UPLOAD');
  assert.equal(artifact.status, 'FROZEN');
  const candidate = compileModelingDocument(artifact);
  assert.deepEqual(candidate.counts, { entities: 4, events: 2, fields: 47, relations: 9, dimensions: 4, metrics: 6 });
  const proposal = materializeModelingDocument(artifact, candidate, { draftId: 'draft-known-v1', base: groupRetailV1() });
  assert.equal((proposal.materializedData.workspaceData.entities as unknown[]).length, 4);
  assert.equal((proposal.materializedData.workspaceData.events as unknown[]).length, 2);
  assert.equal((proposal.materializedData.workspaceData.semanticRelations as unknown[]).length, 9);
  assert.equal((proposal.materializedData.metricData.metrics as unknown[]).length, 6);
  assert.ok(proposal.changes.length > 0);
});
