import assert from 'node:assert/strict';
import test from 'node:test';
import { createCollaborationRuntime } from '../collaboration/runtime.ts';
import { createSourceManagementRuntime } from '../source-management/runtime.ts';
import { createModelingPipelineRuntime } from './runtime.ts';
import { guanyijiaModelingAdapter } from './guanyijia-adapter.ts';
import {
  guanyijiaEvidenceFixture,
  guanyijiaRepositoryEvidenceFixture,
} from '../ai-modeling/guanyijia-fixture.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

test('管伊佳统一来源管线与证据基线正式 V1 使用同一真实快照', async () => {
  const storage = new MemoryStorage();
  const sources = createSourceManagementRuntime({ storage, now: () => '2026-08-10T12:00:00.000Z' });
  const managed = await sources.read('erp_data_governance');
  assert.deepEqual(managed.defaultBatch.snapshotIds.map((id) => managed.snapshots.find((item) => item.snapshotId === id)?.connectionId).sort(), [
    'guanyijia_github', 'guanyijia_mysql',
  ]);
  assert.deepEqual(managed.connections.map((item) => [item.connectionId, item.state]), [
    ['guanyijia_mysql', 'READY'],
    ['guanyijia_github', 'READY'],
    ['guanyijia_sharepoint', 'DRAFT'],
    ['guanyijia_semantica', 'DRAFT'],
  ]);

  const pipeline = createModelingPipelineRuntime({ storage, sourceRuntime: sources, adapter: guanyijiaModelingAdapter, now: () => '2026-08-10T13:00:00.000Z' });
  const scope = { workspaceId: 'erp_data_governance', projectId: 'guanyijia_erp', draftId: 'draft_guanyijia_story' };
  const bo = { userId: 'user_bo_gao', role: 'EDITOR' as const };
  let state = await pipeline.read(scope);
  assert.deepEqual(state.coverage.expectedConnectionIds, [
    'guanyijia_mysql', 'guanyijia_github', 'guanyijia_sharepoint', 'guanyijia_semantica',
  ]);
  state = (await pipeline.execute({ type: 'ATTACH_SOURCE_SNAPSHOTS', ...scope, expectedRevision: state.revision, actor: bo, snapshotIds: managed.defaultBatch.snapshotIds })).snapshot;
  state = (await pipeline.execute({ type: 'FREEZE_EVIDENCE_BATCH', ...scope, expectedRevision: state.revision, actor: bo })).snapshot;
  state = (await pipeline.execute({ type: 'RECONCILE_EVIDENCE', ...scope, expectedRevision: state.revision, actor: bo })).snapshot;
  assert.equal(state.findings.some((item) => item.findingId === 'finding-debt-columns' && item.severity === 'BLOCKER'), true);
  state = (await pipeline.execute({ type: 'GENERATE_CANDIDATE', ...scope, expectedRevision: state.revision, actor: bo })).snapshot;
  assert.equal(state.candidate?.counts.pendingAssets, 63);
  state = (await pipeline.execute({
    type: 'RESOLVE_FINDING', ...scope, expectedRevision: state.revision, actor: bo,
    findingId: 'finding-debt-columns', resolutionId: 'use_deployed_schema',
    reason: '以当前部署数据库结构为准，欠款指标暂不进入正式模型。',
  })).snapshot;
  state = (await pipeline.execute({ type: 'APPLY_CANDIDATE_TO_DRAFT', ...scope, expectedRevision: state.revision, actor: bo })).snapshot;
  assert.ok(state.materialized);

  const collaboration = createCollaborationRuntime(storage);
  const catalog = collaboration.getCatalog('guanyijia_erp');
  assert.ok(catalog);
  assert.equal(catalog.catalogVersion, 'V1');
  assert.equal(catalog.modelSpaceId, 'guanyijia_erp');
  assert.equal(catalog.authorUserId, 'system_baseline');
  assert.equal(catalog.publicationReason, '证据基线预置');
  assert.equal(catalog.data.semanticSidecar?.schemaVersion, 2);
  const sidecar = catalog.data.semanticSidecar;
  assert.ok(sidecar?.schemaVersion === 2);
  assert.deepEqual(sidecar.sources.map((source) => [source.connectionId, source.status]), [
    ['guanyijia_mysql', 'READY'],
    ['guanyijia_github', 'READY'],
    ['guanyijia_official_docs', 'READY'],
  ]);
  const mysql = sidecar.sources[0]!;
  const github = sidecar.sources[1]!;
  assert.equal(mysql.sourceSnapshotIdentity, guanyijiaEvidenceFixture.manifest.snapshotId);
  assert.equal(mysql.fingerprint, guanyijiaEvidenceFixture.source.fingerprint);
  assert.match(mysql.manifestRef ?? '', /mysql:\/\/jsh_erp\//);
  assert.deepEqual(mysql.objectCounts, guanyijiaEvidenceFixture.manifest.objectCounts);
  assert.equal(github.sourceSnapshotIdentity, guanyijiaRepositoryEvidenceFixture.manifest.snapshotId);
  assert.equal(github.fingerprint, guanyijiaRepositoryEvidenceFixture.source.fingerprint);
  assert.match(github.manifestRef ?? '', /github:\/\/jishenghua\/jshERP\//);
  assert.deepEqual(github.objectCounts, guanyijiaRepositoryEvidenceFixture.manifest.objectCounts);
  assert.ok(sidecar.claims.every((claim) => ['guanyijia_mysql', 'guanyijia_github', 'guanyijia_official_docs'].includes(claim.sourceId)));
  const metrics = catalog.data.metricData.metrics as Array<{ metricCode: string }>;
  const ruleCandidates = catalog.data.metricData.ruleCandidates as unknown[];
  assert.equal(metrics.some((metric) => metric.metricCode === 'receivable_debt'), false);
  assert.equal((catalog.data.metricData.rules as unknown[]).length, 0);
  assert.equal(ruleCandidates.length, 8);
  assert.equal((catalog.data.workspaceData.aliases as unknown[]).length, 10);
  assert.equal((catalog.data.workspaceData.rules as unknown[]).length, 9);
  assert.equal((catalog.data.workspaceData.calendars as unknown[]).length, 0);
  const draft = collaboration.openDraft({ actorUserId: 'user_bo_gao', modelSpaceId: 'guanyijia_erp', materialized: catalog.data });
  assert.equal(draft.baseCatalogVersion, 'V1');
  assert.deepEqual(draft.changes, []);
});
