import assert from 'node:assert/strict';
import test from 'node:test';
import { createDocumentAlignmentRuntime } from '../document-alignment/runtime.ts';
import { retailEvidenceEntries, retailEvidenceSources } from '../evidence-registry/retail-evidence-registry.ts';
import { createModelingDocumentRuntime } from '../modeling-document-bridge/runtime.ts';
import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import { createMemoryContentStore, createSourceDocumentRuntime } from './runtime.ts';
import { projectArtifactAlignmentClaims, projectArtifactSourceDocuments } from './source-document-projection.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

async function retailArtifact() {
  const runtime = createModelingDocumentRuntime({ storage: new MemoryStorage(), now: () => '2026-08-14T12:00:00.000Z' });
  return (await runtime.execute({
    type: 'GENERATE_DOCUMENT', projectId: 'group_retail_ops', documentCode: 'retail-source-documents',
    title: '零售来源文档', actorUserId: 'author', sourceBatch: {
      batchId: 'retail-b01-b08', fingerprint: 'retail-b01-b08', snapshotIds: [
        'retail_mysql-snapshot-r2', 'retail_github-snapshot-r2', 'retail_semantica-snapshot-r2', 'retail_sharepoint-snapshot-r2',
        'retail_mongodb-snapshot-r2', 'retail_elasticsearch-snapshot-r2', 'retail_minio-snapshot-r2', 'retail_kafka-snapshot-r2',
      ],
    },
  })).artifact;
}

test('零售八来源各自生成九段文档且只引用本来源证据，对齐恰好产生三项主冲突', async () => {
  const artifact = await retailArtifact();
  const drafts = projectArtifactSourceDocuments(artifact, 'author');
  assert.equal(drafts.length, 8);
  for (const draft of drafts) {
    assert.deepEqual(Object.keys(draft.sections), standardSectionOrder.map((item) => item.key));
    const source = retailEvidenceSources.find((item) => draft.documentCode.endsWith(`--${item.connectionId}`))!;
    const allowed = new Set(retailEvidenceEntries
      .filter((item) => item.connectionId === source.connectionId)
      .map((item) => item.evidenceId));
    assert.ok(draft.assertions.length > 0, `${draft.sourceName}必须有结构化断言`);
    assert.ok(draft.assertions.every((item) => item.evidenceRefs.every((evidenceId) => allowed.has(evidenceId))),
      `${draft.sourceName}不能引用其他来源的证据`);
    assert.match(draft.sections.OVERVIEW, new RegExp(draft.sourceName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }

  const metadata = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const documents = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const readyDocuments = [];
  for (const draft of drafts) {
    const created = await documents.register(draft);
    readyDocuments.push(await documents.markReady({ documentId: created.documentId, expectedRevision: 1, actorUserId: 'author' }));
  }
  const claims = projectArtifactAlignmentClaims(artifact, readyDocuments);
  const alignment = createDocumentAlignmentRuntime({ metadataStorage: metadata, contentStore, sourceDocuments: documents });
  const session = await alignment.align({
    projectId: artifact.projectId,
    sourceDocumentIds: readyDocuments.map((item) => item.documentId),
    claims,
    actorUserId: 'author',
  });
  assert.deepEqual(session.issues.map((item) => item.topicRef).sort(), [
    'metric:net_sales:refund_timing',
    'metric:search_conversion:traffic_filter',
    'time:business_day:assignment',
  ]);
  assert.ok(session.agreements.length > 0, '一致结论应自动折叠为计数');
});

test('管伊佳三来源文档来自黄金证据载荷、暴露部署与源码冲突且不会改变正式模型计数', async () => {
  const drafts = projectArtifactSourceDocuments(guanyijiaFrozenModelingArtifact, 'system_baseline');
  assert.deepEqual(drafts.map((item) => item.sourceName), ['管伊佳部署数据库', 'jshERP源码', '管伊佳官方核心文档']);
  const payload = guanyijiaFrozenModelingArtifact.semanticPayload!.data;
  assert.deepEqual({
    entities: payload.entities.length,
    events: payload.events.length,
    fields: payload.fields.length,
    relations: payload.relations.length,
    dimensions: payload.dimensions.length,
    metrics: payload.metrics.length,
    hierarchies: payload.hierarchies.length,
    rules: payload.ruleCandidates.length,
    aliases: payload.aliases.length,
    timeRules: payload.timeRules.length,
    pending: payload.pendingAssets.length,
    exclusions: payload.exclusions.length,
  }, {
    entities: 14, events: 9, fields: 296, relations: 30,
    dimensions: 4, metrics: 5, hierarchies: 2, rules: 8,
    aliases: 10, timeRules: 9, pending: 63, exclusions: 2,
  });
  const metadata = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const documents = createSourceDocumentRuntime({ metadataStorage: metadata, contentStore });
  const ready = [];
  for (const draft of drafts) {
    const created = await documents.register(draft);
    ready.push(await documents.markReady({ documentId: created.documentId, expectedRevision: 1, actorUserId: 'system_baseline' }));
  }
  const alignment = await createDocumentAlignmentRuntime({ metadataStorage: metadata, contentStore, sourceDocuments: documents }).align({
    projectId: 'guanyijia_erp', sourceDocumentIds: ready.map((item) => item.documentId),
    claims: projectArtifactAlignmentClaims(guanyijiaFrozenModelingArtifact, ready), actorUserId: 'system_baseline',
  });
  assert.deepEqual(alignment.issues.map((item) => item.topicRef), ['field:guanyijia:debt_schema']);
});
