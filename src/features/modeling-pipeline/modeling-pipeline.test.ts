import assert from 'node:assert/strict';
import test from 'node:test';
import { createSourceManagementRuntime } from '../source-management/runtime.ts';
import type { SourceManagementRuntime, SourceManagementSnapshot } from '../source-management/types.ts';
import { createModelingPipelineRuntime } from './runtime.ts';
import { retailModelingAdapter } from './retail-adapter.ts';
import { guanyijiaModelingAdapter } from './guanyijia-adapter.ts';
import { buildRetailRegistryBlockers, retailEvidenceSources } from '../evidence-registry/retail-evidence-registry.ts';
import { candidateModelEvidenceBindings } from '../evidence-registry/retail-evidence-registry.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

const admin = { userId: 'user_administer', role: 'ADMIN' as const };
const editor = { userId: 'user_kenan_zhang', role: 'EDITOR' as const };

async function retailR2(sourceRuntime: SourceManagementRuntime): Promise<SourceManagementSnapshot> {
  let state = await sourceRuntime.read('retail_semantic_modeling');
  for (const connection of state.connections.filter((item) => item.state === 'READY')) {
    const revision1 = state.revisions.find((item) => item.connectionId === connection.connectionId && item.revision === 1)!;
    state = (await sourceRuntime.execute({
      type: 'UPDATE_CONNECTION', workspaceId: state.workspaceId, expectedRevision: state.revision, actor: admin,
      connectionId: connection.connectionId, sanitizedConfig: { ...revision1.sanitizedConfig, fixtureRevision: 2 },
      credentialRef: revision1.credentialRef, defaultScope: revision1.defaultScope,
    })).snapshot;
    for (const type of ['TEST_CONNECTION', 'DISCOVER_SCOPE', 'ACTIVATE_CONNECTION'] as const) {
      state = (await sourceRuntime.execute({
        type, workspaceId: state.workspaceId, expectedRevision: state.revision, actor: admin,
        connectionId: connection.connectionId, revision: 2,
      })).snapshot;
    }
    state = (await sourceRuntime.execute({
      type: 'CAPTURE_SNAPSHOT', workspaceId: state.workspaceId, expectedRevision: state.revision, actor: admin,
      connectionId: connection.connectionId, revision: 2, scope: revision1.defaultScope,
    })).snapshot;
  }
  return state;
}

test('缺少来源时只生成当前快照的主张，不引用缺席来源，也不伪造候选模型', async () => {
  const storage = new MemoryStorage();
  const sources = createSourceManagementRuntime({ storage, now: () => '2026-08-10T10:00:00.000Z' });
  const managed = await sources.read('retail_semantic_modeling');
  const mysql = managed.snapshots.find((item) => item.connectionId === 'retail_mysql')!;
  const runtime = createModelingPipelineRuntime({ storage, sourceRuntime: sources, adapter: retailModelingAdapter });
  let state = await runtime.read({ workspaceId: 'retail_semantic_modeling', projectId: 'group_retail_ops', draftId: 'draft-partial' });
  state = (await runtime.execute({ type: 'ATTACH_SOURCE_SNAPSHOTS', ...state.scope, expectedRevision: state.revision, actor: editor, snapshotIds: [mysql.snapshotId] })).snapshot;
  state = (await runtime.execute({ type: 'FREEZE_EVIDENCE_BATCH', ...state.scope, expectedRevision: state.revision, actor: editor })).snapshot;
  state = (await runtime.execute({ type: 'RECONCILE_EVIDENCE', ...state.scope, expectedRevision: state.revision, actor: editor })).snapshot;

  assert.deepEqual(state.sources.map((item) => item.connectionId), ['retail_mysql']);
  assert.ok(state.claims.length > 0);
  assert.ok(state.claims.every((item) => item.sourceId === 'retail_mysql'));
  assert.equal(state.claims.some((item) => item.sourceId === 'retail_github'), false);
  assert.match(state.coverage.message, /缺少 7 个来源/);
  await assert.rejects(() => runtime.execute({
    type: 'GENERATE_CANDIDATE', ...state.scope, expectedRevision: state.revision, actor: editor,
  }), /尚无预生成候选模型/);
});

test('零售八个Revision 2快照形成R2、三项阻断互证和完整候选模型', async () => {
  const storage = new MemoryStorage();
  const sources = createSourceManagementRuntime({ storage, now: () => '2026-08-10T10:00:00.000Z' });
  const managed = await retailR2(sources);
  const r2Ids = managed.snapshots.filter((item) => item.connectionRevision === 2).map((item) => item.snapshotId);
  assert.equal(r2Ids.length, 8);

  const runtime = createModelingPipelineRuntime({ storage, sourceRuntime: sources, adapter: retailModelingAdapter, now: () => '2026-08-10T11:00:00.000Z' });
  let state = await runtime.read({ workspaceId: 'retail_semantic_modeling', projectId: 'group_retail_ops', draftId: 'draft-retail-r2' });
  state = (await runtime.execute({ type: 'ATTACH_SOURCE_SNAPSHOTS', ...state.scope, expectedRevision: state.revision, actor: editor, snapshotIds: [...r2Ids].reverse() })).snapshot;
  state = (await runtime.execute({ type: 'FREEZE_EVIDENCE_BATCH', ...state.scope, expectedRevision: state.revision, actor: editor })).snapshot;
  assert.equal(state.batch?.revision, 2);
  assert.equal(state.batch?.snapshotIds.length, 8);
  state = (await runtime.execute({ type: 'RECONCILE_EVIDENCE', ...state.scope, expectedRevision: state.revision, actor: editor })).snapshot;
  assert.equal(state.findings.filter((item) => item.severity === 'BLOCKER').length, 3);
  const registryBlockers = buildRetailRegistryBlockers(retailEvidenceSources.map((source) => source.connectionId), 2);
  const runtimeBlockers = state.findings.filter((item) => item.severity === 'BLOCKER');
  assert.deepEqual(runtimeBlockers.map((item) => ({ id: item.findingId, claims: item.claimIds, affected: item.affectedObjectCodes })),
    registryBlockers.map((item) => ({ id: item.findingId, claims: item.claimIds, affected: item.affectedObjectCodes })));
  assert.deepEqual(state.claims.find((claim) => claim.claimId === 'claim_semantica_net_sales_alias')?.upstreamClaimIds,
    ['claim_sharepoint_net_sales']);
  const evidenceOwners = new Map(state.sources.flatMap((source) => source.evidenceIds.map((id) => [id, source.connectionId] as const)));
  for (const claim of state.claims) {
    assert.ok(claim.evidenceRefs.every((id) => evidenceOwners.get(id) === claim.sourceId), `${claim.claimId} 的证据必须属于 ${claim.sourceId}`);
  }
  assert.equal(evidenceOwners.get('GR023'), 'retail_mysql');
  assert.equal(evidenceOwners.get('GR024'), 'retail_elasticsearch');
  assert.equal(evidenceOwners.get('GR025'), 'retail_kafka');

  for (const finding of state.findings.filter((item) => item.severity === 'BLOCKER')) {
    state = (await runtime.execute({
      type: 'RESOLVE_FINDING', ...state.scope, expectedRevision: state.revision, actor: editor,
      findingId: finding.findingId, resolutionId: 'accept_recommended', reason: '采用正式制度和当前实现共同支持的业务口径。',
    })).snapshot;
  }
  state = (await runtime.execute({ type: 'GENERATE_CANDIDATE', ...state.scope, expectedRevision: state.revision, actor: editor })).snapshot;
  assert.equal(state.candidate?.status, 'READY');
  assert.deepEqual(state.candidate?.counts, {
    entities: 7, events: 5, fields: 84, relations: 15, dimensions: 11,
    metrics: 12, hierarchies: 2, ruleCandidates: 7, pendingAssets: 0,
  });
  const candidateBindings = candidateModelEvidenceBindings(state.candidate!);
  assert.equal(candidateBindings.filter((binding) => binding.status === 'VERIFIED').length, 17);
  assert.equal(candidateBindings.filter((binding) => binding.status === 'NEEDS_CONFIRMATION').length, 317);
  assert.ok(candidateBindings.every((binding) =>
    (binding.evidenceRefs.length > 0) !== (binding.status === 'NEEDS_CONFIRMATION')), '候选对象必须持久满足证据/待确认 XOR');
  for (const unsupportedCode of [
    'EVENT:event_inventory_movement',
    'FIELD:event_inventory_movement.business_date',
    'FIELD:event_inventory_movement.on_hand_quantity',
    'FIELD:event_return_processing.refund_amount',
    'METRIC:metric_return_rate',
    'EVENT:event_search_conversion',
    'METRIC:metric_search_conversion_rate',
  ]) {
    const binding = candidateBindings.find((item) => item.objectCode === unsupportedCode);
    assert.equal(binding?.status, 'NEEDS_CONFIRMATION', `${unsupportedCode}不得被无关Claim验证`);
    assert.deepEqual(binding?.evidenceRefs, []);
  }
  const operatingUnitAlias = state.candidate!.aliases.find((item) => item.name === '经营组织业务称呼')!;
  assert.equal(operatingUnitAlias.status, 'NEEDS_CONFIRMATION');
  assert.deepEqual(operatingUnitAlias.evidenceIds, []);
  assert.deepEqual((operatingUnitAlias as typeof operatingUnitAlias & { supportClaimIds?: string[] }).supportClaimIds, []);
  const botRule = state.candidate!.ruleCandidates.find((item) => item.code === 'rule_search_bot_exclusion')!;
  assert.equal(botRule.status, 'VERIFIED');
  assert.deepEqual((botRule as typeof botRule & { supportClaimIds?: string[] }).supportClaimIds,
    ['claim_github_bot_filter', 'claim_elasticsearch_bot_filter']);
  state = (await runtime.execute({ type: 'APPLY_CANDIDATE_TO_DRAFT', ...state.scope, expectedRevision: state.revision, actor: editor })).snapshot;
  const process = state.materialized?.workbenchState as { proposedChanges?: unknown[] };
  assert.equal(process.proposedChanges?.length, 24);
  assert.ok(process.proposedChanges?.every((item) => (item as { objectRef?: { draftId?: string } }).objectRef?.draftId === state.scope.draftId));
  const sidecar = state.materialized?.semanticSidecar;
  assert.ok(sidecar?.schemaVersion === 2);
  const sidecarBinding = new Map(sidecar.objectEvidence.map((binding) => [
    `${binding.objectKind}:${binding.ownerCode ? `${binding.ownerCode}.` : ''}${binding.objectCode}`,
    binding,
  ]));
  let comparedBindings = 0;
  for (const binding of candidateBindings) {
    const frozen = sidecarBinding.get(binding.objectCode);
    if (!frozen) continue; // V2变更集会真实删除一个V1同义词，不构成状态分叉。
    comparedBindings += 1;
    assert.equal(binding.status, frozen.status, `${binding.objectCode}候选与Catalog状态不得分叉`);
    assert.deepEqual(binding.evidenceRefs, frozen.evidenceRefs, `${binding.objectCode}候选与Catalog证据不得分叉`);
    const candidateObject = [
      ...state.candidate!.tables, ...state.candidate!.fields, ...state.candidate!.relations,
      ...state.candidate!.dimensions, ...state.candidate!.metrics, ...state.candidate!.hierarchies,
      ...state.candidate!.ruleCandidates, ...state.candidate!.aliases,
    ].find((item) => binding.objectCode.endsWith(`:${'ownerTableCode' in item ? `${item.ownerTableCode}.` : ''}${item.code}`));
    assert.deepEqual((candidateObject as { supportClaimIds?: string[] }).supportClaimIds, frozen.supportClaimIds,
      `${binding.objectCode}候选与Catalog必须共用support Claim`);
  }
  assert.equal(comparedBindings, candidateBindings.length - 1, '除V2明确删除的一个同义词外，所有候选对象必须与Catalog共用证据投影');
});

test('单个来源贡献多个Claim仍判定为SINGLE_SOURCE', async () => {
  const storage = new MemoryStorage();
  const sources = createSourceManagementRuntime({ storage, now: () => '2026-08-10T10:00:00.000Z' });
  const managed = await sources.read('retail_semantic_modeling');
  const mongodb = managed.snapshots.find((item) => item.connectionId === 'retail_mongodb')!;
  const runtime = createModelingPipelineRuntime({ storage, sourceRuntime: sources, adapter: retailModelingAdapter });
  let state = await runtime.read({ workspaceId: 'retail_semantic_modeling', projectId: 'group_retail_ops', draftId: 'draft-single-source' });
  state = (await runtime.execute({ type: 'ATTACH_SOURCE_SNAPSHOTS', ...state.scope, expectedRevision: state.revision, actor: editor, snapshotIds: [mongodb.snapshotId] })).snapshot;
  state = (await runtime.execute({ type: 'FREEZE_EVIDENCE_BATCH', ...state.scope, expectedRevision: state.revision, actor: editor })).snapshot;
  state = (await runtime.execute({ type: 'RECONCILE_EVIDENCE', ...state.scope, expectedRevision: state.revision, actor: editor })).snapshot;
  const finding = state.findings.find((item) => item.findingId === 'finding_customer_tier_time');
  assert.equal(finding?.claimIds.length, 2);
  assert.equal(finding?.status, 'SINGLE_SOURCE');
  assert.equal(finding?.severity, 'WEAK');
});

test('管伊佳与零售共用状态机：双来源可生成候选，阻断未处理时不可应用草稿', async () => {
  const storage = new MemoryStorage();
  const sources = createSourceManagementRuntime({ storage, now: () => '2026-08-10T10:00:00.000Z' });
  const managed = await sources.read('erp_data_governance');
  const runtime = createModelingPipelineRuntime({ storage, sourceRuntime: sources, adapter: guanyijiaModelingAdapter, now: () => '2026-08-10T12:00:00.000Z' });
  let state = await runtime.read({ workspaceId: 'erp_data_governance', projectId: 'guanyijia_erp', draftId: 'draft-guanyijia-r1' });
  state = (await runtime.execute({ type: 'ATTACH_SOURCE_SNAPSHOTS', ...state.scope, expectedRevision: state.revision, actor: { userId: 'user_bo_gao', role: 'EDITOR' }, snapshotIds: managed.defaultBatch.snapshotIds })).snapshot;
  state = (await runtime.execute({ type: 'FREEZE_EVIDENCE_BATCH', ...state.scope, expectedRevision: state.revision, actor: { userId: 'user_bo_gao', role: 'EDITOR' } })).snapshot;
  state = (await runtime.execute({ type: 'RECONCILE_EVIDENCE', ...state.scope, expectedRevision: state.revision, actor: { userId: 'user_bo_gao', role: 'EDITOR' } })).snapshot;
  state = (await runtime.execute({ type: 'GENERATE_CANDIDATE', ...state.scope, expectedRevision: state.revision, actor: { userId: 'user_bo_gao', role: 'EDITOR' } })).snapshot;

  assert.equal(state.candidate?.counts.pendingAssets, 63);
  assert.equal(state.findings.some((item) => item.findingId === 'finding-debt-columns' && item.severity === 'BLOCKER'), true);
  await assert.rejects(() => runtime.execute({
    type: 'APPLY_CANDIDATE_TO_DRAFT', ...state.scope, expectedRevision: state.revision, actor: { userId: 'user_bo_gao', role: 'EDITOR' },
  }), /仍有 1 项阻断/);

  state = (await runtime.execute({
    type: 'RESOLVE_FINDING', ...state.scope, expectedRevision: state.revision, actor: { userId: 'user_bo_gao', role: 'EDITOR' },
    findingId: 'finding-debt-columns', resolutionId: 'use_deployed_schema', reason: '以当前部署数据库结构为准，欠款指标暂不进入正式模型。',
  })).snapshot;
  state = (await runtime.execute({ type: 'APPLY_CANDIDATE_TO_DRAFT', ...state.scope, expectedRevision: state.revision, actor: { userId: 'user_bo_gao', role: 'EDITOR' } })).snapshot;
  assert.equal(state.state, 'APPLIED_TO_DRAFT');
  assert.ok(state.materialized);
});
