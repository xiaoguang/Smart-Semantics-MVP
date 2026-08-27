import assert from 'node:assert/strict';
import test from 'node:test';
import { createCollaborationRuntime } from '../collaboration/runtime.ts';
import { compileModelingDocument } from './compile-modeling-document.ts';
import { materializeModelingDocument } from './document-materializer.ts';
import { createModelingDocumentRuntime } from './runtime.ts';
import { createSourceManagementRuntime } from '../source-management/runtime.ts';
import { createModelingPipelineRuntime } from '../modeling-pipeline/runtime.ts';
import { guanyijiaModelingAdapter } from '../modeling-pipeline/guanyijia-adapter.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

async function approveFreezeImport(runtime: ReturnType<typeof createModelingDocumentRuntime>, input: {
  artifact: Awaited<ReturnType<typeof runtime.execute>>['artifact']; author: string; reviewer: string; freezer: string;
}) {
  let artifact = (await runtime.execute({ type: 'CONFIRM_AUTHOR', artifactId: input.artifact.artifactId,
    expectedRevision: input.artifact.revision, actorUserId: input.author, reason: '作者确认标准文档结论' })).artifact;
  artifact = (await runtime.execute({ type: 'APPROVE_DOCUMENT', artifactId: artifact.artifactId,
    expectedRevision: artifact.revision, actorUserId: input.reviewer, reason: '审核证据、决定与血缘' })).artifact;
  artifact = (await runtime.execute({ type: 'FREEZE_DOCUMENT', artifactId: artifact.artifactId,
    expectedRevision: artifact.revision, actorUserId: input.freezer, reason: '审核通过后冻结' })).artifact;
  const imported = await runtime.execute({ type: 'IMPORT_TO_AI', artifactId: artifact.artifactId, markdownSha256: artifact.markdown.sha256 });
  assert.equal(imported.importRecord?.artifactId, artifact.artifactId);
  return artifact;
}

test('零售B01→B08→r9→M4→草稿→审核→发布V2且版本与用户隔离', async () => {
  const storage = new MemoryStorage();
  const documents = createModelingDocumentRuntime({ storage, now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await documents.execute({ type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'task5_retail',
    title: '零售经营标准建模资料', actorUserId: 'user_kenan_zhang', sourceBatch: { batchId: 'retail-b01-b08', fingerprint: 'retail-b01-b08', snapshotIds: [
      'retail_mysql-snapshot-r2', 'retail_github-snapshot-r2', 'retail_semantica-snapshot-r2', 'retail_sharepoint-snapshot-r2',
      'retail_mongodb-snapshot-r2', 'retail_elasticsearch-snapshot-r2', 'retail_minio-snapshot-r2', 'retail_kafka-snapshot-r2',
    ] } })).artifact;
  assert.equal(artifact.revision, 8);
  artifact = (await documents.execute({ type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'user_kenan_zhang', decisions: [
      { issueId: 'finding_net_sales_refund', resolutionId: 'adopt_completed_refund', reason: '采用已完成退款口径' },
      { issueId: 'finding_bot_filter', resolutionId: 'exclude_bot_and_internal_test', reason: '排除机器人和压测流量' },
      { issueId: 'finding_business_day', resolutionId: 'adopt_store_business_day', reason: '采用门店营业日口径' },
    ] })).artifact;
  assert.equal(artifact.revision, 9);
  artifact = await approveFreezeImport(documents, { artifact, author: 'user_kenan_zhang', reviewer: 'user_zhengqing_xiong', freezer: 'user_hao_xu' });
  const collaboration = createCollaborationRuntime(storage);
  collaboration.resetDemo('user_administer');
  const baseV1 = collaboration.getCatalog('group_retail_ops', 'V1')!;
  const opened = collaboration.openDraft({ actorUserId: 'user_kenan_zhang', modelSpaceId: 'group_retail_ops', materialized: baseV1.data });
  const proposal = materializeModelingDocument(artifact, compileModelingDocument(artifact), { draftId: opened.draftId, base: baseV1.data });
  const draft = collaboration.saveDraft({ actorUserId: 'user_kenan_zhang', draftId: opened.draftId,
    expectedRevision: opened.revision, materialized: proposal.materializedData, changes: proposal.changes });
  assert.ok(draft.changes.length > 0);
  assert.ok(draft.changes.every((change) => change.operation === 'MODIFIED' && change.objectRef?.draftId === draft.draftId));
  assert.equal(collaboration.listDrafts('user_zhengqing_xiong').some((item) => item.draftId === draft.draftId), false);
  const request = collaboration.submitDraft({ actorUserId: 'user_kenan_zhang', draftId: draft.draftId, expectedDraftRevision: draft.revision, summary: '零售标准化V2' });
  const approved = collaboration.reviewRequest({ actorUserId: 'user_zhengqing_xiong', requestId: request.requestId, expectedRevision: request.expectedRevision, decision: 'APPROVE', reason: '证据完整' });
  const published = collaboration.publishRequest({ actorUserId: 'user_hao_xu', requestId: approved.requestId, expectedRevision: approved.expectedRevision, reason: '审核通过，发布零售V2' });
  assert.equal(published.catalog.catalogVersion, 'V2');
  assert.deepEqual({
    entities: (published.catalog.data.workspaceData.entities as unknown[]).length,
    events: (published.catalog.data.workspaceData.events as unknown[]).length,
    relations: (published.catalog.data.workspaceData.semanticRelations as unknown[]).length,
    metrics: (published.catalog.data.metricData.metrics as unknown[]).length,
    dimensions: published.catalog.data.semanticSidecar?.dimensions.length,
  }, { entities: 7, events: 5, relations: 15, metrics: 12, dimensions: 11 });
  assert.equal(published.catalog.data.semanticSidecar?.schemaVersion, 2);
  assert.equal(published.catalog.data.semanticSidecar?.batch.revision, 2);
  assert.equal(collaboration.getCatalog('group_retail_ops', 'V1')?.fingerprint, baseV1.fingerprint);
  const guanyijiaV1 = collaboration.getCatalog('guanyijia_erp', 'V1');
  assert.ok(guanyijiaV1, '零售流程不得移除管伊佳黄金V1');
  assert.deepEqual({
    entities: (guanyijiaV1.data.workspaceData.entities as unknown[]).length,
    events: (guanyijiaV1.data.workspaceData.events as unknown[]).length,
    relations: (guanyijiaV1.data.workspaceData.semanticRelations as unknown[]).length,
    metrics: (guanyijiaV1.data.metricData.metrics as unknown[]).length,
    dimensions: guanyijiaV1.data.semanticSidecar?.dimensions.length,
  }, { entities: 14, events: 9, relations: 30, metrics: 5, dimensions: 4 });
});

test('管伊佳真实MySQL+GitHub形成未来文档且不覆盖黄金V1或生成缺席来源主张', async () => {
  const storage = new MemoryStorage();
  const sources = createSourceManagementRuntime({ storage, now: () => '2026-08-12T10:00:00.000Z' });
  const managed = await sources.read('erp_data_governance');
  const pipeline = createModelingPipelineRuntime({ storage, sourceRuntime: sources, adapter: guanyijiaModelingAdapter, now: () => '2026-08-12T11:00:00.000Z' });
  const scope = { workspaceId: 'erp_data_governance', projectId: 'guanyijia_erp', draftId: 'task5-gyj-pipeline' };
  const actor = { userId: 'user_bo_gao', role: 'EDITOR' as const };
  let pipelineState = await pipeline.read(scope);
  pipelineState = (await pipeline.execute({ type: 'ATTACH_SOURCE_SNAPSHOTS', ...scope, expectedRevision: pipelineState.revision, actor, snapshotIds: managed.defaultBatch.snapshotIds })).snapshot;
  pipelineState = (await pipeline.execute({ type: 'FREEZE_EVIDENCE_BATCH', ...scope, expectedRevision: pipelineState.revision, actor })).snapshot;
  pipelineState = (await pipeline.execute({ type: 'RECONCILE_EVIDENCE', ...scope, expectedRevision: pipelineState.revision, actor })).snapshot;
  pipelineState = (await pipeline.execute({ type: 'GENERATE_CANDIDATE', ...scope, expectedRevision: pipelineState.revision, actor })).snapshot;
  pipelineState = (await pipeline.execute({ type: 'RESOLVE_FINDING', ...scope, expectedRevision: pipelineState.revision, actor,
    findingId: 'finding-debt-columns', resolutionId: 'use_deployed_schema', reason: '以部署结构为准，欠款指标保持阻断' })).snapshot;
  pipelineState = (await pipeline.execute({ type: 'APPLY_CANDIDATE_TO_DRAFT', ...scope, expectedRevision: pipelineState.revision, actor })).snapshot;
  assert.equal(pipelineState.materialized?.semanticSidecar?.schemaVersion, 2);
  const documents = createModelingDocumentRuntime({ storage, now: () => '2026-08-12T12:00:00.000Z' });
  let artifact = (await documents.execute({ type: 'GENERATE_DOCUMENT', projectId: 'guanyijia_erp', documentCode: 'task5_guanyijia',
    title: '管伊佳ERP标准建模资料', actorUserId: 'user_bo_gao', sourceBatch: { batchId: 'gyj-real', fingerprint: 'gyj-real', snapshotIds: [
      ...managed.defaultBatch.snapshotIds, 'guanyijia_sharepoint-unbound', 'guanyijia_semantica-unbound',
    ] } })).artifact;
  assert.deepEqual(
    [...(artifact.sourceBatch?.snapshotIds ?? [])].sort(),
    [...managed.defaultBatch.snapshotIds].sort(),
  );
  assert.ok(artifact.assertions.every((item) => item.evidenceRefs.every((ref) => !/sharepoint|semantica/i.test(ref))));
  artifact = (await documents.execute({ type: 'RECORD_DECISIONS', artifactId: artifact.artifactId, expectedRevision: artifact.revision,
    actorUserId: 'user_bo_gao', decisions: [{ issueId: 'finding_guanyijia_debt_fields', resolutionId: 'keep_debt_metric_blocked', reason: '部署库升级前保持欠款指标阻断' }] })).artifact;
  assert.equal(artifact.validation.gaps.length, 1, '两来源未来文档必须诚实保留未补齐的证据缺口');
  assert.notEqual(artifact.status, 'FROZEN');
  const collaboration = createCollaborationRuntime(storage);
  const goldenV1 = collaboration.getCatalog('guanyijia_erp', 'V1');
  assert.ok(goldenV1);
  assert.equal(goldenV1.requestId, 'SYSTEM_BASELINE');
  assert.deepEqual({
    entities: (goldenV1.data.workspaceData.entities as unknown[]).length,
    events: (goldenV1.data.workspaceData.events as unknown[]).length,
    fields: [
      ...(goldenV1.data.workspaceData.entities as Array<{ attributes: unknown[] }>),
      ...(goldenV1.data.workspaceData.events as Array<{ attributes: unknown[] }>),
    ].reduce((total, item) => total + item.attributes.length, 0),
    relations: (goldenV1.data.workspaceData.semanticRelations as unknown[]).length,
    metrics: (goldenV1.data.metricData.metrics as unknown[]).length,
    dimensions: goldenV1.data.semanticSidecar?.dimensions.length,
  }, { entities: 14, events: 9, fields: 296, relations: 30, metrics: 5, dimensions: 4 });
  assert.equal(collaboration.getCatalog('guanyijia_erp', 'V2'), null);
  assert.ok(artifact.status === 'AWAITING_CONFIRMATION');
  assert.equal(collaboration.getCatalog('group_retail_ops')?.catalogVersion, 'V1');
  assert.equal((await documents.list('group_retail_ops')).length, 0);
});
