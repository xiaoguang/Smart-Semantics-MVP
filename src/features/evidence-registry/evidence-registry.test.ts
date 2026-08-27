import assert from 'node:assert/strict';
import test from 'node:test';
import {
  groupRetailEvidenceLocators,
  groupRetailSourceAssets,
} from '../ai-modeling/group-retail-source-fixture.ts';
import { buildRetailEvidenceSidecar } from '../collaboration/retail-evidence-fixture.ts';
import { groupRetailDocument } from '../ai-modeling/group-retail-model-fixture.ts';
import {
  guanyijiaCandidateModel,
  guanyijiaEvidenceFixture,
  guanyijiaRepositoryEvidenceFixture,
} from '../ai-modeling/guanyijia-fixture.ts';
import * as retailRegistry from './retail-evidence-registry.ts';
import * as evidenceRegistry from './retail-evidence-registry.ts';
import { createSourceManagementRuntime } from '../source-management/runtime.ts';
import { adaptGroupRetailPublishedModel } from '../ai-modeling/group-retail-adapter.ts';
import { createGroupRetailWorkbenchRuntime } from '../ai-modeling/group-retail-runtime.ts';
import { groupRetailEvidenceClaims, createGroupRetailFindings } from '../ai-modeling/group-retail-source-fixture.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

const emptySemanticPart = {
  systemCode: 'group_retail_ops',
  sourceFingerprint: 'test-fingerprint',
  dimensions: [],
  ruleCandidates: [],
  hierarchyDefinitions: [],
  generationNotes: [],
  compatibilityNotes: [],
};

test('GR001-GR025在legacy资料与冻结sidecar中具有唯一且相同的来源语义', () => {
  const sidecar = buildRetailEvidenceSidecar(emptySemanticPart, 2);
  const sidecarOwner = new Map(sidecar.sources.flatMap((source) =>
    source.evidenceIds.map((evidenceId) => [evidenceId, source.connectionId] as const)));
  const legacyOwner = new Map(groupRetailSourceAssets.flatMap((source) =>
    source.evidenceIds.map((evidenceId) => [evidenceId, source.sourceId] as const)));
  const connectionToLegacy: Record<string, string> = {
    retail_mysql: 'group-source-mysql',
    retail_github: 'group-source-github',
    retail_semantica: 'group-source-semantica-rdf',
    retail_sharepoint: 'group-source-sharepoint',
    retail_mongodb: 'group-source-mongodb',
    retail_elasticsearch: 'group-source-elasticsearch',
    retail_minio: 'group-source-minio',
    retail_kafka: 'group-source-kafka',
  };
  const expectedEvidenceIds = Array.from({ length: 25 }, (_item, index) =>
    `GR${String(index + 1).padStart(3, '0')}`);

  assert.deepEqual([...sidecarOwner.keys()].sort(), expectedEvidenceIds);
  assert.deepEqual([...legacyOwner.keys()].sort(), expectedEvidenceIds);
  assert.deepEqual(
    groupRetailDocument.artifacts.model.evidence_catalog.map((item) => item.evidence_id).sort(),
    expectedEvidenceIds,
    '模型证据目录也必须由唯一注册表投影完整GR001-GR025',
  );
  for (const evidenceId of expectedEvidenceIds) {
    assert.equal(
      legacyOwner.get(evidenceId),
      connectionToLegacy[sidecarOwner.get(evidenceId)!],
      `${evidenceId}不能在两个投影中代表不同来源`,
    );
    assert.ok(groupRetailEvidenceLocators[evidenceId], `${evidenceId}必须拥有唯一Locator`);
    const registryEntry = retailRegistry.retailEvidenceEntries.find((entry) => entry.evidenceId === evidenceId)!;
    const registrySource = retailRegistry.retailEvidenceSources.find((source) => source.connectionId === registryEntry.connectionId)!;
    const catalogEntry = groupRetailDocument.artifacts.model.evidence_catalog.find((entry) => entry.evidence_id === evidenceId)!;
    assert.equal(catalogEntry.source_file, registrySource.displayName, `${evidenceId}目录来源不得产生第二套语义`);
    assert.equal(catalogEntry.quote, registryEntry.statement, `${evidenceId}目录语义不得产生第二套文案`);
  }
});

test('零售B01-B08按指定来源累积并只在B02、B06、B08产生三项因果Blocker', () => {
  const buildStory = (retailRegistry as typeof retailRegistry & {
    buildRetailEvidenceStory?: () => Array<{
      batchId: string;
      addedConnectionId: string;
      connectionIds: string[];
      evidenceIds: string[];
      claims: Array<{ sourceId: string; evidenceRefs: string[] }>;
      findings: Array<{ severity: string; topic: string }>;
      lineageGaps: Array<{ sourceId: string; missingUpstreamSourceIds: string[] }>;
      independentRootSourceCount: number;
    }>;
  }).buildRetailEvidenceStory;
  assert.ok(buildStory, 'Evidence Registry必须导出零售因果故事编译入口');

  const story = buildStory();
  const expectedSources = [
    'retail_mysql', 'retail_github', 'retail_semantica', 'retail_sharepoint',
    'retail_mongodb', 'retail_elasticsearch', 'retail_minio', 'retail_kafka',
  ];
  assert.deepEqual(story.map((batch) => batch.batchId), ['B01', 'B02', 'B03', 'B04', 'B05', 'B06', 'B07', 'B08']);
  assert.deepEqual(story.map((batch) => batch.addedConnectionId), expectedSources);
  assert.deepEqual(story.map((batch) => batch.evidenceIds.length), [4, 7, 9, 12, 15, 18, 21, 25]);

  assert.deepEqual(
    story.flatMap((batch) => batch.findings.filter((finding) => finding.severity === 'BLOCKER')
      .map((finding) => `${batch.batchId}:${finding.topic}`)),
    ['B02:NET_SALES', 'B06:BOT_FILTER', 'B08:BUSINESS_DAY'],
  );
  assert.deepEqual(story[2]?.lineageGaps, [{ sourceId: 'retail_semantica', missingUpstreamSourceIds: ['retail_sharepoint'] }]);
  assert.equal(story[2]?.independentRootSourceCount, 2, '派生Semantica不得被计为独立根来源');
  assert.equal(story[3]?.lineageGaps.length, 0, 'SharePoint到达后应补齐Semantica上游血缘');
  assert.equal(story[3]?.independentRootSourceCount, 3);

  for (const batch of story) {
    const presentSources = new Set(batch.connectionIds);
    const presentEvidence = new Set(batch.evidenceIds);
    assert.ok(batch.claims.every((claim) => presentSources.has(claim.sourceId)), `${batch.batchId}不得主张缺席来源`);
    assert.ok(batch.claims.every((claim) => claim.evidenceRefs.every((id) => presentEvidence.has(id))), `${batch.batchId}不得引用未到达证据`);
  }
});

test('冻结sidecar逐条复用Evidence Registry的Locator而不生成第二套定位语义', () => {
  const sidecar = buildRetailEvidenceSidecar(emptySemanticPart, 2);
  for (const entry of retailRegistry.retailEvidenceEntries) {
    assert.deepEqual(sidecar.locators[entry.evidenceId], entry.locator, `${entry.evidenceId}的Locator必须来自唯一注册表`);
    assert.deepEqual(groupRetailEvidenceLocators[entry.evidenceId], entry.locator, `${entry.evidenceId}的legacy Locator必须来自唯一注册表`);
  }
});

test('管伊佳按MySQL、GitHub、SharePoint、Semantica累积并绑定真实双来源snapshot identity', () => {
  const buildStory = (evidenceRegistry as typeof evidenceRegistry & { buildGuanyijiaEvidenceStory?: () => any[] }).buildGuanyijiaEvidenceStory;
  assert.ok(buildStory, 'Evidence Registry必须导出管伊佳因果故事编译入口');
  const story = buildStory();
  assert.deepEqual(story.map((batch) => batch.addedConnectionId), [
    'guanyijia_mysql', 'guanyijia_github', 'guanyijia_sharepoint', 'guanyijia_semantica',
  ]);
  assert.deepEqual(story.map((batch) => batch.connectionIds.length), [1, 2, 3, 4]);
  assert.equal(story[0]?.sources[0]?.snapshotIdentity, guanyijiaEvidenceFixture.manifest.snapshotId);
  assert.equal(story[0]?.sources[0]?.fingerprint, guanyijiaEvidenceFixture.source.fingerprint);
  assert.equal(story[1]?.sources[1]?.snapshotIdentity, guanyijiaRepositoryEvidenceFixture.manifest.snapshotId);
  assert.equal(story[1]?.sources[1]?.versionRef, guanyijiaRepositoryEvidenceFixture.manifest.commit);
  assert.ok(story[2]?.sources[2]?.status === 'NEEDS_CONFIRMATION');
  assert.ok(story[3]?.sources[3]?.status === 'NEEDS_CONFIRMATION');
  for (const batch of story) {
    const present = new Set(batch.connectionIds);
    assert.ok(batch.claims.every((claim) => present.has(claim.sourceId)), `${batch.batchId}不得包含缺席来源主张`);
  }
});

test('通用对象绑定校验器保证全部零售与管伊佳可见对象有有效证据或明确待确认', () => {
  const registry = evidenceRegistry as typeof evidenceRegistry & {
    fixtureDocumentEvidenceBindings?: (value: unknown) => any[];
    candidateModelEvidenceBindings?: (value: unknown) => any[];
    linguanModelEvidenceBindings?: (value: unknown) => any[];
    validateVisibleObjectEvidence?: (bindings: any[], available: Set<string>) => any;
  };
  assert.ok(registry.fixtureDocumentEvidenceBindings && registry.candidateModelEvidenceBindings
    && registry.linguanModelEvidenceBindings && registry.validateVisibleObjectEvidence,
    'Evidence Registry必须提供通用可见对象证据绑定校验器');
  const retailBindings = registry.fixtureDocumentEvidenceBindings(groupRetailDocument);
  const retailValidation = registry.validateVisibleObjectEvidence(
    retailBindings,
    new Set(retailRegistry.retailEvidenceEntries.map((entry) => entry.evidenceId)),
  );
  assert.equal(retailValidation.valid, true);
  assert.equal(retailValidation.invalidEvidenceRefs.length, 0);
  assert.equal(retailValidation.unboundObjectCodes.length, 0);

  const retailModel = adaptGroupRetailPublishedModel();
  assert.deepEqual({
    entities: retailModel.entities.length,
    events: retailModel.events.length,
    fields: retailModel.fields.length,
    relations: retailModel.relations.length,
    dimensions: retailModel.dimensions.length,
    metrics: retailModel.metrics.length,
    hierarchies: retailModel.hierarchies.length,
    rules: retailModel.ruleCandidates.length,
    aliases: retailModel.aliases.length,
    timeRules: retailModel.timeSemantics?.rules.length,
  }, {
    entities: 7, events: 5, fields: 84, relations: 15, dimensions: 11,
    metrics: 12, hierarchies: 2, rules: 7, aliases: 191, timeRules: 3,
  });
  const retailModelBindings = registry.linguanModelEvidenceBindings(retailModel);
  const retailModelValidation = registry.validateVisibleObjectEvidence(
    retailModelBindings,
    new Set(retailRegistry.retailEvidenceEntries.map((entry) => entry.evidenceId)),
  );
  assert.equal(retailModelValidation.valid, true);
  assert.equal(retailModelValidation.invalidEvidenceRefs.length, 0);
  assert.equal(retailModelValidation.unboundObjectCodes.length, 0);
  assert.ok(retailModelBindings.filter((binding) => binding.objectCode.startsWith('TIME_RULE:'))
    .every((binding) => binding.status === 'NEEDS_CONFIRMATION'));

  const guanyijiaBindings = registry.candidateModelEvidenceBindings(guanyijiaCandidateModel);
  const guanyijiaAvailable = new Set([
    ...guanyijiaEvidenceFixture.source.evidenceIds,
    ...guanyijiaRepositoryEvidenceFixture.source.evidenceIds,
  ]);
  const guanyijiaValidation = registry.validateVisibleObjectEvidence(guanyijiaBindings, guanyijiaAvailable);
  assert.equal(guanyijiaValidation.valid, true);
  assert.equal(guanyijiaValidation.invalidEvidenceRefs.length, 0);
  assert.equal(guanyijiaValidation.unboundObjectCodes.length, 0);
  assert.deepEqual(guanyijiaCandidateModel.counts, {
    entities: 13, events: 6, fields: 265, relations: 18, dimensions: 14,
    metrics: 8, hierarchies: 2, ruleCandidates: 8, pendingAssets: 63,
  });
});

test('来源中心的管伊佳MySQL和GitHub快照保留真实manifest identity', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T00:00:00.000Z' });
  const state = await runtime.read('erp_data_governance');
  const mysql = state.snapshots.find((snapshot) => snapshot.connectionId === 'guanyijia_mysql');
  const github = state.snapshots.find((snapshot) => snapshot.connectionId === 'guanyijia_github');
  assert.equal(mysql?.sourceSnapshotIdentity, guanyijiaEvidenceFixture.manifest.snapshotId);
  assert.equal(mysql?.fingerprint, guanyijiaEvidenceFixture.source.fingerprint);
  assert.equal(github?.sourceSnapshotIdentity, guanyijiaRepositoryEvidenceFixture.manifest.snapshotId);
  assert.equal(github?.versionRef, guanyijiaRepositoryEvidenceFixture.manifest.commit);
  assert.equal(github?.fingerprint, guanyijiaRepositoryEvidenceFixture.source.fingerprint);
});

test('零售运行时仅暴露当前已加入来源的主张与阻断', async () => {
  const runtime = createGroupRetailWorkbenchRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T00:00:00.000Z' });
  let snapshot = await runtime.read('group_retail_ops');
  assert.deepEqual(snapshot.sourceWorkspace?.evidenceClaims, [], '空批次不得预先声称八来源结论');
  snapshot = (await runtime.execute({
    type: 'ADD_SOURCES', systemCode: 'group_retail_ops', expectedRevision: snapshot.lockVersion,
    sourceIds: ['group-source-mysql'],
  })).snapshot;
  const claims = snapshot.sourceWorkspace?.evidenceClaims as Array<{ sourceId?: string; evidenceRefs: string[] }>;
  assert.equal(claims.length, 4);
  assert.ok(claims.every((claim) => claim.sourceId === 'group-source-mysql'));
  assert.ok(claims.every((claim) => claim.evidenceRefs.every((id) => ['GR001', 'GR002', 'GR003', 'GR023'].includes(id))));
  snapshot = (await runtime.execute({
    type: 'START_MODELING', systemCode: 'group_retail_ops', expectedRevision: snapshot.lockVersion,
  })).snapshot;
  assert.equal(snapshot.sourceWorkspace?.batch.findings.filter((item) => item.severity === 'BLOCKER').length, 0,
    '只有MySQL的B01不得提前出现依赖缺席来源的阻断');
});

test('派生Claim血缘贯穿Registry、legacy和冻结sidecar，B03缺口在B04闭合', () => {
  const registryClaim = retailRegistry.retailEvidenceClaims.find((claim) => claim.claimId === 'claim_semantica_net_sales_alias')!;
  const legacyClaim = groupRetailEvidenceClaims.find((claim) => claim.claimId === registryClaim.claimId)!;
  const sidecar = buildRetailEvidenceSidecar(emptySemanticPart, 2);
  const frozenClaim = sidecar.claims.find((claim) => claim.claimId === registryClaim.claimId)!;
  assert.deepEqual(legacyClaim.upstreamClaimIds, ['claim_sharepoint_net_sales']);
  assert.deepEqual(frozenClaim.upstreamClaimIds, ['claim_sharepoint_net_sales']);
  assert.equal(frozenClaim.topic, 'NET_SALES_ALIAS');
  const story = retailRegistry.buildRetailEvidenceStory();
  assert.ok(story[2]!.claims.some((claim) => claim.claimId === registryClaim.claimId));
  assert.equal(story[2]!.claims.some((claim) => claim.claimId === 'claim_sharepoint_net_sales'), false);
  assert.deepEqual(story[2]!.lineageGaps, [{ sourceId: 'retail_semantica', missingUpstreamSourceIds: ['retail_sharepoint'] }]);
  assert.ok(story[3]!.claims.some((claim) => claim.claimId === 'claim_sharepoint_net_sales'));
  assert.deepEqual(story[3]!.lineageGaps, []);
});

test('来源中心快照Claim保留Registry的上游Claim血缘', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage(), now: () => '2026-08-12T00:00:00.000Z' });
  const state = await runtime.read('retail_semantic_modeling');
  const semantica = state.claims.find((claim) => claim.claimId === 'claim_semantica_net_sales_alias');
  assert.deepEqual(semantica?.upstreamClaimIds, ['claim_sharepoint_net_sales']);
  assert.equal(semantica?.authority, 'DERIVED');
});

test('Registry是runtime与冻结sidecar三项Blocker的唯一事实投影', () => {
  const registryBlockers = retailRegistry.buildRetailRegistryBlockers(
    retailRegistry.retailEvidenceSources.map((source) => source.connectionId), 2,
  );
  const legacyBlockers = createGroupRetailFindings().filter((finding) => finding.severity === 'BLOCKER');
  const frozenBlockers = buildRetailEvidenceSidecar(emptySemanticPart, 2).findings.filter((finding) => finding.originStatus === 'CONFLICT');
  assert.deepEqual(registryBlockers.map((item) => ({
    id: item.findingId, claims: item.claimIds, evidence: item.evidenceIds, affected: item.affectedObjectCodes,
  })), frozenBlockers.map((item) => ({
    id: item.findingId, claims: item.claimIds, evidence: item.claimIds.flatMap((id) =>
      retailRegistry.retailEvidenceClaims.find((claim) => claim.claimId === id)?.evidenceRefs ?? []),
    affected: item.affectedObjectCodes,
  })));
  assert.deepEqual(legacyBlockers.map((item) => ({ id: item.findingId, evidence: item.evidenceIds, affected: item.affectedObjects })),
    registryBlockers.map((item) => ({ id: item.findingId, evidence: item.evidenceIds, affected: item.affectedObjectCodes })));
});

test('证据目录与artifact manifest都固定为25条', () => {
  assert.equal(groupRetailDocument.artifacts.model.evidence_catalog.length, 25);
  assert.equal((groupRetailDocument.artifactManifest as { evidenceCount: number }).evidenceCount, 25);
});

test('所有正式support edge都声明精确支持面与允许topic，无直接Claim的对象不得过度验证', () => {
  type ReviewedEdge = {
    objectKind: string;
    objectCode: string;
    ownerCode?: string;
    supportAspect: string;
    allowedTopics: string[];
    supportClaimIds: string[];
  };
  const reviewedContract: ReviewedEdge[] = [
    { objectKind: 'EVENT', objectCode: 'event_sales_order_line', supportAspect: 'OBJECT_STRUCTURE', allowedTopics: ['SALES_SCHEMA'], supportClaimIds: ['claim_mysql_sales_schema'] },
    { objectKind: 'FIELD', ownerCode: 'event_sales_order_line', objectCode: 'order_status', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['SALES_SCHEMA', 'ORDER_STATUS'], supportClaimIds: ['claim_mysql_sales_schema', 'claim_github_order_status'] },
    { objectKind: 'FIELD', ownerCode: 'event_sales_order_line', objectCode: 'sales_quantity', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['SALES_SCHEMA'], supportClaimIds: ['claim_mysql_sales_schema'] },
    { objectKind: 'FIELD', ownerCode: 'event_sales_order_line', objectCode: 'sales_amount', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['SALES_SCHEMA'], supportClaimIds: ['claim_mysql_sales_schema'] },
    { objectKind: 'EVENT', objectCode: 'event_return_processing', supportAspect: 'OBJECT_STRUCTURE', allowedTopics: ['NET_SALES'], supportClaimIds: ['claim_mysql_refund_fields'] },
    { objectKind: 'FIELD', ownerCode: 'event_return_processing', objectCode: 'return_time', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['NET_SALES'], supportClaimIds: ['claim_mysql_refund_fields', 'claim_kafka_return_completed'] },
    { objectKind: 'METRIC', objectCode: 'metric_net_sales', supportAspect: 'METRIC_FORMULA', allowedTopics: ['NET_SALES'], supportClaimIds: ['claim_github_net_sales', 'claim_sharepoint_net_sales', 'claim_minio_net_sales'] },
    { objectKind: 'RULE', objectCode: 'rule_completed_refund', supportAspect: 'RULE_LOGIC', allowedTopics: ['NET_SALES'], supportClaimIds: ['claim_github_net_sales', 'claim_sharepoint_net_sales', 'claim_minio_net_sales'] },
    { objectKind: 'FIELD', ownerCode: 'entity_customer', objectCode: 'customer_tier', supportAspect: 'FIELD_SEMANTIC', allowedTopics: ['CUSTOMER_TIER'], supportClaimIds: ['claim_mongodb_customer_tier', 'claim_mongodb_current_tier', 'claim_sharepoint_customer_tier'] },
    { objectKind: 'DIMENSION', objectCode: 'dim_customer_tier', supportAspect: 'DIMENSION_SEMANTIC', allowedTopics: ['CUSTOMER_TIER'], supportClaimIds: ['claim_mongodb_customer_tier', 'claim_sharepoint_customer_tier'] },
    { objectKind: 'METRIC', objectCode: 'metric_current_inventory', supportAspect: 'METRIC_FORMULA', allowedTopics: ['INVENTORY'], supportClaimIds: ['claim_minio_inventory_snapshot'] },
    { objectKind: 'RULE', objectCode: 'rule_latest_inventory', supportAspect: 'RULE_LOGIC', allowedTopics: ['INVENTORY'], supportClaimIds: ['claim_minio_inventory_snapshot', 'claim_kafka_inventory_order'] },
    { objectKind: 'TIME_RULE', objectCode: 'inventory_snapshot_time', supportAspect: 'TIME_ASSIGNMENT', allowedTopics: ['INVENTORY'], supportClaimIds: ['claim_minio_inventory_snapshot', 'claim_kafka_inventory_order'] },
    { objectKind: 'RULE', objectCode: 'rule_search_bot_exclusion', supportAspect: 'RULE_LOGIC', allowedTopics: ['BOT_FILTER'], supportClaimIds: ['claim_github_bot_filter', 'claim_elasticsearch_bot_filter'] },
    { objectKind: 'DIMENSION', objectCode: 'dim_business_date', supportAspect: 'DIMENSION_SEMANTIC', allowedTopics: ['BUSINESS_DAY'], supportClaimIds: ['claim_sharepoint_business_day', 'claim_minio_business_day'] },
    { objectKind: 'RULE', objectCode: 'rule_business_day', supportAspect: 'RULE_LOGIC', allowedTopics: ['BUSINESS_DAY'], supportClaimIds: ['claim_sharepoint_business_day', 'claim_minio_business_day'] },
    { objectKind: 'TIME_RULE', objectCode: 'order_business_time', supportAspect: 'TIME_ASSIGNMENT', allowedTopics: ['BUSINESS_DAY'], supportClaimIds: ['claim_sharepoint_business_day', 'claim_minio_business_day'] },
    { objectKind: 'ALIAS', objectCode: 'synonym_73:1', supportAspect: 'ALIAS_TERM', allowedTopics: ['NET_SALES_ALIAS'], supportClaimIds: ['claim_semantica_net_sales_alias'] },
    { objectKind: 'ALIAS', objectCode: 'synonym_73:2', supportAspect: 'ALIAS_TERM', allowedTopics: ['NET_SALES_ALIAS'], supportClaimIds: ['claim_semantica_net_sales_alias'] },
  ];

  assert.deepEqual(retailRegistry.retailObjectSupportEdges, reviewedContract);
  const claimById = new Map(retailRegistry.retailEvidenceClaims.map((claim) => [claim.claimId, claim]));
  for (const edge of retailRegistry.retailObjectSupportEdges as ReviewedEdge[]) {
    assert.ok(edge.supportClaimIds.every((claimId) => edge.allowedTopics.includes(claimById.get(claimId)!.topic)),
      `${edge.objectKind}:${edge.objectCode}不得用未声明topic过度验证`);
  }
  for (const unsupported of [
    'EVENT::event_inventory_movement',
    'FIELD:event_inventory_movement:business_date',
    'FIELD:event_inventory_movement:on_hand_quantity',
    'FIELD:event_return_processing:refund_amount',
    'METRIC::metric_return_rate',
    'EVENT::event_search_conversion',
    'METRIC::metric_search_conversion_rate',
  ]) {
    assert.equal(retailRegistry.retailObjectSupportEdges.some((edge) =>
      `${edge.objectKind}:${edge.ownerCode ?? ''}:${edge.objectCode}` === unsupported), false, `${unsupported}应待确认`);
  }
});

test('Registry自检拒绝编号、Claim归属、派生血缘、support edge和Locator损坏', () => {
  const pristine = () => ({
    sources: structuredClone(retailRegistry.retailEvidenceSources),
    entries: structuredClone(retailRegistry.retailEvidenceEntries),
    claims: structuredClone(retailRegistry.retailEvidenceClaims),
    supportEdges: structuredClone(retailRegistry.retailObjectSupportEdges),
  });
  assert.equal(retailRegistry.assertRetailEvidenceRegistry(pristine()), true);

  const missingId = pristine(); missingId.entries.pop();
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(missingId), /GR001-GR025/);

  const duplicateClaim = pristine(); duplicateClaim.claims[1]!.claimId = duplicateClaim.claims[0]!.claimId;
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(duplicateClaim), /Claim编码重复/);

  const missingEvidence = pristine(); missingEvidence.claims[0]!.evidenceRefs = ['GR999'];
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(missingEvidence), /不存在的证据/);

  const wrongOwner = pristine(); wrongOwner.claims[0]!.evidenceRefs = ['GR014'];
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(wrongOwner), /不属于来源/);

  const missingUpstream = pristine();
  missingUpstream.claims.find((claim) => claim.claimId === 'claim_semantica_net_sales_alias')!.upstreamClaimIds = ['claim_missing'];
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(missingUpstream), /不存在的上游Claim/);

  const derivedWithoutLineage = pristine();
  delete derivedWithoutLineage.claims.find((claim) => claim.claimId === 'claim_semantica_net_sales_alias')!.upstreamClaimIds;
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(derivedWithoutLineage), /派生Claim缺少上游/);

  const brokenSupport = pristine(); brokenSupport.supportEdges[0]!.supportClaimIds = ['claim_missing'];
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(brokenSupport), /support edge引用不存在的Claim/);

  const wrongSupportTopic = pristine(); wrongSupportTopic.supportEdges[0]!.supportClaimIds = ['claim_github_bot_filter'];
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(wrongSupportTopic), /support edge的Claim topic超出允许范围/);

  const blankStatement = pristine(); blankStatement.entries[0]!.statement = '   ';
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(blankStatement), /语义陈述为空/);

  const blankLocator = pristine(); blankLocator.entries[0]!.locator = null as never;
  assert.throws(() => retailRegistry.assertRetailEvidenceRegistry(blankLocator), /Locator为空/);
});
