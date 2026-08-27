import assert from 'node:assert/strict';
import test from 'node:test';
import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import { createMemoryContentStore, createSourceDocumentRuntime } from '../source-documents/runtime.ts';
import type { ContentAddressedStore, ContentReference } from '../source-documents/types.ts';
import type { SourceDocumentSections } from '../source-documents/types.ts';
import { createDocumentAlignmentRuntime } from './runtime.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

function sections(label: string): SourceDocumentSections {
  return Object.fromEntries(standardSectionOrder.map(({ key }) => [key, `${label}未提供本节依据。`])) as SourceDocumentSections;
}

async function createReadyDocument(
  sourceRuntime: ReturnType<typeof createSourceDocumentRuntime>,
  input: { code: string; name: string; snapshot: string; metric: string; evidence: string },
) {
  const value = sections(input.name);
  value.METRIC = input.metric;
  const created = await sourceRuntime.register({
    projectId: 'group_retail_ops', documentCode: input.code, sourceSnapshotId: input.snapshot,
    sourceType: input.code.includes('mysql') ? 'RELATIONAL_DATABASE' : 'CODE_REPOSITORY', sourceName: input.name,
    sections: value,
    assertions: [{ assertionId: `${input.code}:net-sales`, section: 'METRIC', statement: input.metric,
      provenance: 'OBSERVED', evidenceRefs: [input.evidence] }],
    validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  return sourceRuntime.markReady({ documentId: created.documentId, expectedRevision: 1, actorUserId: 'author' });
}

test('对齐把同主题不同来源结论识别为冲突且未决定时不能生成产物', async () => {
  const metadata = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const sourceRuntime = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const mysql = await createReadyDocument(sourceRuntime, {
    code: 'retail-mysql', name: '零售交易库', snapshot: 'mysql-r2',
    metric: '净销售额在退货发起时冲减。', evidence: 'GR023',
  });
  const github = await createReadyDocument(sourceRuntime, {
    code: 'retail-github', name: '零售分析代码仓库', snapshot: 'github-r2',
    metric: '净销售额仅在退款完成后冲减。', evidence: 'GR014',
  });
  const runtime = createDocumentAlignmentRuntime({ metadataStorage: metadata, contentStore, sourceDocuments: sourceRuntime });
  const session = await runtime.align({
    projectId: 'group_retail_ops', sourceDocumentIds: [mysql.documentId, github.documentId], actorUserId: 'author',
    claims: [
      { claimId: 'mysql-net-sales', sourceDocumentId: mysql.documentId, topicRef: 'metric:net_sales:refund_timing',
        objectKind: 'METRIC', statement: '净销售额在退货发起时冲减。', normalizedValue: 'RETURN_STARTED',
        authority: 'PRIMARY', evidenceRefs: ['GR023'] },
      { claimId: 'github-net-sales', sourceDocumentId: github.documentId, topicRef: 'metric:net_sales:refund_timing',
        objectKind: 'METRIC', statement: '净销售额仅在退款完成后冲减。', normalizedValue: 'REFUND_COMPLETED',
        authority: 'CORROBORATING', evidenceRefs: ['GR014'] },
    ],
  });
  assert.equal(session.issues.length, 1);
  assert.equal(session.issues[0]?.kind, 'CONFLICT');
  assert.equal(session.issues[0]?.severity, 'BLOCKER');
  assert.equal(session.agreements.length, 0);
  await assert.rejects(() => runtime.createDeliverable({
    alignmentId: session.alignmentId, expectedRevision: session.revision,
    mode: 'MERGED_DOCUMENT', actorUserId: 'author',
  }), /仍有未解决的阻断问题/);
});

test('冲突决定真实改变合并Markdown，两种产物均由一个统一封装进入终审', async () => {
  const metadata = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const sourceRuntime = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const mysql = await createReadyDocument(sourceRuntime, {
    code: 'retail-mysql', name: '零售交易库', snapshot: 'mysql-r2',
    metric: '净销售额在退货发起时冲减。', evidence: 'GR023',
  });
  const github = await createReadyDocument(sourceRuntime, {
    code: 'retail-github', name: '零售分析代码仓库', snapshot: 'github-r2',
    metric: '净销售额仅在退款完成后冲减。', evidence: 'GR014',
  });
  const runtime = createDocumentAlignmentRuntime({ metadataStorage: metadata, contentStore, sourceDocuments: sourceRuntime,
    now: () => '2026-08-14T12:00:00.000Z' });
  let session = await runtime.align({
    projectId: 'group_retail_ops', sourceDocumentIds: [mysql.documentId, github.documentId], actorUserId: 'author',
    claims: [
      { claimId: 'mysql-net-sales', sourceDocumentId: mysql.documentId, topicRef: 'metric:net_sales:refund_timing',
        objectKind: 'METRIC', statement: '净销售额在退货发起时冲减。', normalizedValue: 'RETURN_STARTED', authority: 'PRIMARY', evidenceRefs: ['GR023'] },
      { claimId: 'github-net-sales', sourceDocumentId: github.documentId, topicRef: 'metric:net_sales:refund_timing',
        objectKind: 'METRIC', statement: '净销售额仅在退款完成后冲减。', normalizedValue: 'REFUND_COMPLETED', authority: 'CORROBORATING', evidenceRefs: ['GR014'] },
    ],
  });
  session = await runtime.decide({
    alignmentId: session.alignmentId, expectedRevision: 1, issueId: session.issues[0]!.issueId,
    selectedClaimId: 'github-net-sales', reason: '正式SQL以退款完成事件为准。', actorUserId: 'author',
  });
  const merged = await runtime.createDeliverable({ alignmentId: session.alignmentId, expectedRevision: 2,
    mode: 'MERGED_DOCUMENT', actorUserId: 'author' });
  const mergedMarkdown = await runtime.readMarkdown(merged.deliverableId);
  assert.match(mergedMarkdown, /净销售额仅在退款完成后冲减/);
  assert.doesNotMatch(mergedMarkdown, /采用结论：净销售额在退货发起时冲减/);
  assert.equal(merged.mode, 'MERGED_DOCUMENT');
  assert.equal(merged.markdownRefs.length, 1);

  const sourceSet = await runtime.createDeliverable({ alignmentId: session.alignmentId, expectedRevision: 2,
    mode: 'SOURCE_DOCUMENT_SET', actorUserId: 'author' });
  assert.equal(sourceSet.markdownRefs.length, 2);
  assert.equal(sourceSet.semanticPayloadRef, merged.semanticPayloadRef, '两种形式必须消费同一对齐结论');

  const confirmed = await runtime.confirmAuthor({ deliverableId: merged.deliverableId, expectedRevision: 1,
    actorUserId: 'author', reason: '来源和冲突均已确认' });
  await assert.rejects(() => runtime.approveAndFreeze({ deliverableId: confirmed.deliverableId,
    expectedRevision: 2, actorUserId: 'author' }), /作者不能审核自己的产物/);
  const frozen = await runtime.approveAndFreeze({ deliverableId: confirmed.deliverableId,
    expectedRevision: 2, actorUserId: 'reviewer' });
  assert.equal(frozen.status, 'FROZEN');
  assert.equal(frozen.revision, 3);
});

test('审核通过会分批校验全部内容并留下可查询的冻结进度', async () => {
  const metadata = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const sourceRuntime = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const mysql = await createReadyDocument(sourceRuntime, {
    code: 'retail-mysql', name: '零售交易库', snapshot: 'mysql-r2',
    metric: '净销售额仅在退款完成后冲减。', evidence: 'GR014',
  });
  const runtime = createDocumentAlignmentRuntime({
    metadataStorage: metadata, contentStore, sourceDocuments: sourceRuntime,
    freezeBatchSize: 2,
    now: () => '2026-08-14T12:00:00.000Z',
  });
  const session = await runtime.align({
    projectId: 'group_retail_ops', sourceDocumentIds: [mysql.documentId], actorUserId: 'author',
    claims: [{
      claimId: 'mysql-net-sales', sourceDocumentId: mysql.documentId,
      topicRef: 'metric:net_sales:refund_timing', objectKind: 'METRIC',
      statement: '净销售额仅在退款完成后冲减。', normalizedValue: 'REFUND_COMPLETED',
      authority: 'PRIMARY', evidenceRefs: ['GR014'],
    }],
  });
  const deliverable = await runtime.createDeliverable({
    alignmentId: session.alignmentId, expectedRevision: session.revision,
    mode: 'SOURCE_DOCUMENT_SET', actorUserId: 'author',
  });
  const confirmed = await runtime.confirmAuthor({
    deliverableId: deliverable.deliverableId, expectedRevision: 1,
    actorUserId: 'author', reason: '已确认来源文档',
  });
  const frozen = await runtime.approveAndFreeze({
    deliverableId: confirmed.deliverableId, expectedRevision: 2, actorUserId: 'reviewer',
  });
  const progress = await runtime.getFreezeProgress(deliverable.deliverableId);
  assert.equal(frozen.status, 'FROZEN');
  assert.equal(progress?.status, 'COMPLETED');
  assert.equal(progress?.completedItems, progress?.totalItems);
  assert.ok((progress?.totalBatches ?? 0) > 1, '小批量校验应记录多个批次');
});

test('内容损坏时冻结失败且保留可重试状态，不能伪装成FROZEN', async () => {
  const metadata = new MemoryStorage();
  const backingStore = createMemoryContentStore();
  let brokenRef: ContentReference | null = null;
  const contentStore: ContentAddressedStore = {
    put: (content) => backingStore.put(content),
    get: async (ref) => ref === brokenRef ? null : backingStore.get(ref),
  };
  const sourceRuntime = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const mysql = await createReadyDocument(sourceRuntime, {
    code: 'retail-mysql', name: '零售交易库', snapshot: 'mysql-r2',
    metric: '净销售额仅在退款完成后冲减。', evidence: 'GR014',
  });
  const runtime = createDocumentAlignmentRuntime({
    metadataStorage: metadata, contentStore, sourceDocuments: sourceRuntime, freezeBatchSize: 2,
  });
  const session = await runtime.align({
    projectId: 'group_retail_ops', sourceDocumentIds: [mysql.documentId], actorUserId: 'author',
    claims: [{
      claimId: 'mysql-net-sales', sourceDocumentId: mysql.documentId,
      topicRef: 'metric:net_sales:refund_timing', objectKind: 'METRIC',
      statement: '净销售额仅在退款完成后冲减。', normalizedValue: 'REFUND_COMPLETED',
      authority: 'PRIMARY', evidenceRefs: ['GR014'],
    }],
  });
  const deliverable = await runtime.createDeliverable({
    alignmentId: session.alignmentId, expectedRevision: 1,
    mode: 'MERGED_DOCUMENT', actorUserId: 'author',
  });
  const confirmed = await runtime.confirmAuthor({
    deliverableId: deliverable.deliverableId, expectedRevision: 1,
    actorUserId: 'author', reason: '已确认',
  });
  brokenRef = confirmed.semanticPayloadRef;
  await assert.rejects(() => runtime.approveAndFreeze({
    deliverableId: confirmed.deliverableId, expectedRevision: 2, actorUserId: 'reviewer',
  }), /冻结校验失败/);
  const progress = await runtime.getFreezeProgress(deliverable.deliverableId);
  assert.equal(progress?.status, 'FAILED');
  assert.match(progress?.error ?? '', /语义载荷/);

  brokenRef = null;
  const frozen = await runtime.approveAndFreeze({
    deliverableId: confirmed.deliverableId, expectedRevision: 2, actorUserId: 'reviewer',
  });
  assert.equal(frozen.status, 'FROZEN');
  assert.equal((await runtime.getFreezeProgress(deliverable.deliverableId))?.status, 'COMPLETED');
});

test('M4只能从完成独立审核的统一产物读取绑定建模文档', async () => {
  const metadata = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const sourceRuntime = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const created = await sourceRuntime.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-mysql', sourceSnapshotId: 'mysql-v1',
    sourceType: 'RELATIONAL_DATABASE', sourceName: '管伊佳部署数据库', sections: sections('管伊佳部署数据库'),
    assertions: [{ assertionId: 'gy:mysql:supplier', section: 'OBJECT', statement: '往来单位来自部署表。', provenance: 'OBSERVED', evidenceRefs: ['GY-MYSQL-001'] }],
    validation: { errors: [], warnings: [], gaps: [] }, actorUserId: 'author',
  });
  const ready = await sourceRuntime.markReady({ documentId: created.documentId, expectedRevision: 1, actorUserId: 'author' });
  const runtime = createDocumentAlignmentRuntime({ metadataStorage: metadata, contentStore, sourceDocuments: sourceRuntime });
  const alignment = await runtime.align({
    projectId: 'guanyijia_erp', sourceDocumentIds: [ready.documentId], actorUserId: 'author',
    claims: [{ claimId: 'gy-mysql-supplier', sourceDocumentId: ready.documentId, topicRef: 'entity:party', objectKind: 'ENTITY', statement: '往来单位来自部署表。', normalizedValue: 'PARTY', authority: 'PRIMARY', evidenceRefs: ['GY-MYSQL-001'] }],
  });
  const deliverable = await runtime.createDeliverable({
    alignmentId: alignment.alignmentId, expectedRevision: alignment.revision,
    mode: 'SOURCE_DOCUMENT_SET', actorUserId: 'author', modelingArtifact: guanyijiaFrozenModelingArtifact,
  });
  await assert.rejects(() => runtime.readModelingArtifact(deliverable.deliverableId), /只有冻结产物/);
  const confirmed = await runtime.confirmAuthor({ deliverableId: deliverable.deliverableId, expectedRevision: 1, actorUserId: 'author', reason: '来源文档已确认' });
  const frozen = await runtime.approveAndFreeze({ deliverableId: confirmed.deliverableId, expectedRevision: 2, actorUserId: 'reviewer' });
  const handoff = await runtime.readModelingArtifact(frozen.deliverableId);
  assert.equal(handoff.artifactId, guanyijiaFrozenModelingArtifact.artifactId);
  assert.equal(handoff.status, 'FROZEN');
  assert.equal(handoff.markdown.sha256, guanyijiaFrozenModelingArtifact.markdown.sha256);
});
