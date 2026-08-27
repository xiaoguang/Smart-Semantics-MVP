import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import {
  createCollaborationRuntime,
  demoUsers,
  seedCollaborationState,
  workspaceDefinitions,
  type CollaborationStorage,
} from './runtime.ts';
import {
  accessibleModelProjects,
  evidencePackageDefinitions,
  modelProjectDefinitions,
  productDefinition,
  workspaceForModelProject,
} from '../model-projects/domain-registry.ts';
import { resolveModelingProjectScenario } from '../model-projects/project-scenario.ts';
import { readStoreData, replaceStoreData, setWorkspaceWriteAccess, useStore } from '../../store/useStore.ts';
import { buildRetailV2Proposal } from './fixtures.ts';
import { presentLoginFailure } from './login-copy.ts';

class MemoryStorage implements CollaborationStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

const materialized = (label: string) => ({
  workspaceData: { aliases: [{ id: label }] },
  metricData: { metrics: [{ id: label }] },
  workbenchState: { label },
});

function createRetailDraft(runtime: ReturnType<typeof createCollaborationRuntime>) {
  const base = runtime.getCatalog('group_retail_ops', 'V1')!.data;
  const draft = runtime.openDraft({ actorUserId: 'user_kenan_zhang', modelSpaceId: 'group_retail_ops', materialized: base });
  const proposal = buildRetailV2Proposal(base, draft.draftId);
  return runtime.saveDraft({
    actorUserId: 'user_kenan_zhang', draftId: draft.draftId, expectedRevision: draft.revision,
    materialized: proposal.materializedData, changes: proposal.changes,
  });
}

test('账号验证区分大小写，Session 不保存密码', () => {
  const storage = new MemoryStorage();
  const runtime = createCollaborationRuntime(storage);
  assert.equal(runtime.authenticate('bo.gao', 'R7m!Q2v#L9pX').displayName, 'Bo Gao');
  assert.throws(() => runtime.authenticate('Bo.Gao', 'R7m!Q2v#L9pX'), /用户名或密码错误/);
  const session = runtime.createSession('bo.gao', 'R7m!Q2v#L9pX', '2026-08-08T12:00:00.000Z');
  assert.deepEqual(session, {
    schemaVersion: 1,
    userId: 'user_bo_gao',
    authenticatedAt: '2026-08-08T12:00:00.000Z',
  });
  assert.equal(JSON.stringify(session).includes('R7m!'), false);
});

test('九个账号密码唯一，administrator 保留稳定用户ID和双空间成员关系', () => {
  assert.equal(demoUsers.length, 9);
  assert.equal(new Set(demoUsers.map((user) => user.demoPassword)).size, 9);
  assert.deepEqual(demoUsers.find((user) => user.username === 'administrator'), {
    userId: 'user_administer', username: 'administrator', displayName: 'Administrator',
    initials: 'AD', demoPassword: 'A7m!R9x#K4qV',
  });
  const runtime = createCollaborationRuntime(new MemoryStorage());
  assert.equal(runtime.authenticate('administrator', 'A7m!R9x#K4qV').userId, 'user_administer');
  assert.throws(() => runtime.authenticate('administer', 'j4ck4l0p3B14!'), /用户名或密码错误/);
  assert.deepEqual(runtime.listWorkspacesFor('user_bo_gao').map((item) => item.workspaceId), ['erp_data_governance']);
  assert.deepEqual(runtime.listWorkspacesFor('user_administer').map((item) => item.workspaceId), [
    'erp_data_governance', 'retail_semantic_modeling',
  ]);
  assert.deepEqual(workspaceDefinitions.find((item) => item.workspaceId === 'retail_semantic_modeling')?.modelProjectIds, [
    'group_retail_ops',
  ]);
});

test('体验账号使用统一目录且不按ERP或零售空间分组', () => {
  const login = readFileSync(new URL('./login-page.tsx', import.meta.url), 'utf8');
  assert.match(login, /demoUsers\.map/);
  assert.doesNotMatch(login, /workspaceDefinitions\.map|workspaceMemberships\.filter/);
  assert.doesNotMatch(login, /ERP 数据治理|零售经营建模/);
});

test('登录页不向用户透传底层错误文本', () => {
  const login = readFileSync(new URL('./login-page.tsx', import.meta.url), 'utf8');
  assert.equal(presentLoginFailure(new Error('用户名或密码错误')), '用户名或密码不正确，请重新输入。');
  assert.equal(presentLoginFailure(new Error('IndexedDB quota exceeded: internal key')), '暂时无法登录，请稍后重试。');
  assert.match(login, /message\.error\(presentLoginFailure\(error\)\)/);
  assert.doesNotMatch(login, /message\.error\(error instanceof Error/);
});

test('旧管理员草稿缺少 changes 时只在内存中兼容为空数组', () => {
  const storage = new MemoryStorage();
  const runtime = createCollaborationRuntime(storage);
  const catalog = runtime.getCatalog('group_retail_ops')!;
  const key = 'linguan:collaboration:drafts:v4:user_administer';
  const raw = JSON.stringify([{
    draftId: 'legacy_admin_draft', workspaceId: 'retail_semantic_modeling', modelSpaceId: 'group_retail_ops',
    ownerUserId: 'user_administer', title: '旧管理员草稿', baseCatalogVersion: 'V1',
    baseFingerprint: catalog.fingerprint, revision: 1, status: 'ACTIVE', materializedData: catalog.data,
    createdAt: '2026-08-01T00:00:00.000Z', updatedAt: '2026-08-01T00:00:00.000Z',
  }]);
  storage.setItem(key, raw);

  const draft = runtime.getActiveDraft('user_administer', 'group_retail_ops');
  assert.deepEqual(draft?.changes, []);
  assert.equal(runtime.getDraftIssue('user_administer', 'group_retail_ops'), null);
  assert.equal(storage.getItem(key), raw);
});

test('缺少完整模型数据的旧草稿返回恢复问题且不改写原始记录', () => {
  const storage = new MemoryStorage();
  const runtime = createCollaborationRuntime(storage);
  const key = 'linguan:collaboration:drafts:v4:user_administer';
  const raw = JSON.stringify([{
    draftId: 'broken_admin_draft', workspaceId: 'retail_semantic_modeling', modelSpaceId: 'group_retail_ops',
    ownerUserId: 'user_administer', title: '损坏管理员草稿', baseCatalogVersion: 'V1',
    baseFingerprint: 'catalog_group_retail_v1', revision: 1, status: 'ACTIVE', changes: [],
    materializedData: { workbenchState: null }, createdAt: '2026-08-01T00:00:00.000Z',
    updatedAt: '2026-08-01T00:00:00.000Z',
  }]);
  storage.setItem(key, raw);

  assert.equal(runtime.getActiveDraft('user_administer', 'group_retail_ops'), null);
  assert.match(runtime.getDraftIssue('user_administer', 'group_retail_ops')?.message ?? '', /草稿内容暂时无法显示/);
  assert.throws(() => runtime.openDraft({
    actorUserId: 'user_administer', modelSpaceId: 'group_retail_ops',
    materialized: runtime.getCatalog('group_retail_ops')!.data,
  }), /草稿内容暂时无法显示/);
  assert.equal(storage.getItem(key), raw);
});

test('A方案注册表只有一个产品、两个协作空间、两个模型项目，旧零售场景只作为证据包', () => {
  assert.equal(productDefinition.productId, 'retail_semantic_modeling_product');
  assert.deepEqual(productDefinition.workspaceIds, ['erp_data_governance', 'retail_semantic_modeling']);
  assert.deepEqual(modelProjectDefinitions.map((item) => item.projectId), ['guanyijia_erp', 'group_retail_ops']);
  assert.equal(modelProjectDefinitions.find((item) => item.projectId === 'group_retail_ops')?.displayName, '零售经营语义模型');
  assert.deepEqual(
    evidencePackageDefinitions.filter((item) => item.projectId === 'group_retail_ops').map((item) => item.legacySystemCode),
    ['default', 'digital_sales_warehouse', 'omnichannel_retail_ops', 'group_retail_ops'],
  );
  assert.equal(new Set(evidencePackageDefinitions.map((item) => item.packageId)).size, evidencePackageDefinitions.length);
});

test('顶部模型项目选择按权限合并协作空间并能反查所属空间', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const adminWorkspaces = runtime.listWorkspacesFor('user_administer');
  const erpWorkspaces = runtime.listWorkspacesFor('user_bo_gao');

  assert.deepEqual(accessibleModelProjects(adminWorkspaces).map((item) => item.projectId), [
    'guanyijia_erp', 'group_retail_ops',
  ]);
  assert.deepEqual(accessibleModelProjects(erpWorkspaces).map((item) => item.projectId), [
    'guanyijia_erp',
  ]);
  assert.equal(workspaceForModelProject(adminWorkspaces, 'group_retail_ops')?.workspaceId, 'retail_semantic_modeling');
  assert.equal(workspaceForModelProject(erpWorkspaces, 'group_retail_ops'), undefined);
});

test('零售项目仅通过证据包适配旧场景，包内版本不进入项目版本序列', () => {
  const scenario = resolveModelingProjectScenario('group_retail_ops');
  assert.equal(scenario.project.displayName, '零售经营语义模型');
  assert.deepEqual(
    scenario.packages.map((item) => item.definition.legacySystemCode),
    ['default', 'digital_sales_warehouse', 'omnichannel_retail_ops', 'group_retail_ops'],
  );
  assert.deepEqual(
    scenario.packages.map((item) => item.definition.packageVersion),
    ['workspace-v1', 'documents-v1-v4', 'source-pack-v1', 'storage-pack-v1'],
  );
  assert.equal(scenario.project.evidencePackageIds.length, 4);
});

test('编辑者草稿按用户和模型项目隔离，正式模型不随草稿改变', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const before = runtime.readShared().catalogs.group_retail_ops.at(-1);
  const boDraft = runtime.openDraft({ actorUserId: 'user_bo_gao', modelSpaceId: 'guanyijia_erp', materialized: materialized('bo') });
  const kenanDraft = runtime.openDraft({ actorUserId: 'user_kenan_zhang', modelSpaceId: 'group_retail_ops', materialized: materialized('kenan') });
  assert.equal(boDraft.ownerUserId, 'user_bo_gao');
  assert.equal(kenanDraft.ownerUserId, 'user_kenan_zhang');
  assert.equal(runtime.listDrafts('user_bo_gao').length, 1);
  assert.equal(runtime.listDrafts('user_kenan_zhang').length, 1);
  assert.deepEqual(runtime.readShared().catalogs.group_retail_ops.at(-1), before);
  assert.throws(() => runtime.openDraft({ actorUserId: 'user_guorui_yuan', modelSpaceId: 'guanyijia_erp', materialized: materialized('viewer') }), /没有创建草稿权限/);
});

test('作者不能审核或发布，审核者批准后发布者生成不可变 V2', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const draft = createRetailDraft(runtime);
  const request = runtime.submitDraft({
    actorUserId: 'user_kenan_zhang',
    draftId: draft.draftId,
    expectedDraftRevision: draft.revision,
    summary: '补充净销售额业务称呼与营业日说明',
  });
  assert.equal(request.status, 'SUBMITTED');
  assert.throws(() => runtime.reviewRequest({ actorUserId: 'user_kenan_zhang', requestId: request.requestId, expectedRevision: request.expectedRevision, decision: 'APPROVE', reason: '我确认自己的修改' }), /作者不能审核/);
  const approved = runtime.reviewRequest({ actorUserId: 'user_zhengqing_xiong', requestId: request.requestId, expectedRevision: request.expectedRevision, decision: 'APPROVE', reason: '术语和营业日说明清楚，可以进入发布。' });
  assert.equal(approved.status, 'APPROVED');
  assert.equal(approved.approvals[0]?.reviewerUserId, 'user_zhengqing_xiong');
  assert.throws(() => runtime.publishRequest({ actorUserId: 'user_kenan_zhang', requestId: request.requestId, expectedRevision: approved.expectedRevision, reason: '作者发布' }), /作者不能发布/);
  const published = runtime.publishRequest({ actorUserId: 'user_hao_xu', requestId: request.requestId, expectedRevision: approved.expectedRevision, reason: '审核已完成，发布零售语义模型 V2。' });
  assert.equal(published.catalog.publicationReason, '审核已完成，发布零售语义模型 V2。');
  assert.equal(published.request.status, 'PUBLISHED');
  assert.equal(published.catalog.catalogVersion, 'V2');
  assert.equal(published.catalog.authorUserId, 'user_kenan_zhang');
  assert.equal(published.catalog.reviewerUserIds[0], 'user_zhengqing_xiong');
  assert.equal(published.catalog.publisherUserId, 'user_hao_xu');
  assert.equal(runtime.getActiveDraft('user_kenan_zhang', 'group_retail_ops'), null);
  assert.equal(runtime.getCatalog('group_retail_ops', 'V1')?.catalogVersion, 'V1');
  assert.equal(runtime.getCatalog('group_retail_ops', 'V2')?.catalogVersion, 'V2');
  assert.notDeepEqual(
    runtime.getCatalog('group_retail_ops', 'V1')?.data,
    runtime.getCatalog('group_retail_ops', 'V2')?.data,
  );
  assert.throws(() => runtime.publishRequest({ actorUserId: 'user_hao_xu', requestId: request.requestId, expectedRevision: published.request.expectedRevision, reason: '重复发布' }), /不能发布/);
});

test('审核通过允许无理由，退回和拒绝仍要求中文，发布仍要求中文理由', () => {
  const createSubmitted = () => {
    const runtime = createCollaborationRuntime(new MemoryStorage());
    const draft = createRetailDraft(runtime);
    const request = runtime.submitDraft({ actorUserId: 'user_kenan_zhang', draftId: draft.draftId, expectedDraftRevision: draft.revision, summary: draft.title });
    return { runtime, request };
  };
  const approvedCase = createSubmitted();
  const approved = approvedCase.runtime.reviewRequest({
    actorUserId: 'user_zhengqing_xiong', requestId: approvedCase.request.requestId,
    expectedRevision: approvedCase.request.expectedRevision, decision: 'APPROVE', reason: '',
  });
  assert.equal(approved.status, 'APPROVED');
  assert.equal(approved.approvals[0]?.reason, '无补充意见');
  assert.throws(() => approvedCase.runtime.publishRequest({
    actorUserId: 'user_hao_xu', requestId: approved.requestId,
    expectedRevision: approved.expectedRevision, reason: 'publish V2',
  }), /中文发布理由/);

  for (const decision of ['REQUEST_CHANGES', 'REJECT'] as const) {
    const current = createSubmitted();
    assert.throws(() => current.runtime.reviewRequest({
      actorUserId: 'user_zhengqing_xiong', requestId: current.request.requestId,
      expectedRevision: current.request.expectedRevision, decision, reason: '',
    }), /中文审核理由/);
  }
});

test('来源候选应用到草稿后按编辑、审核、发布顺序生成待办', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  assert.deepEqual(runtime.listTasks('user_kenan_zhang'), []);
  assert.equal(runtime.listDrafts('user_kenan_zhang').filter((item) => item.status === 'ACTIVE').length, 0);
  assert.deepEqual(runtime.listTasks('user_zhengqing_xiong'), []);
  const ready = createRetailDraft(runtime);
  const request = runtime.submitDraft({
    actorUserId: 'user_kenan_zhang', draftId: ready.draftId,
    expectedDraftRevision: ready.revision, summary: '多来源互证与经营口径升级 V2',
  });
  assert.deepEqual(runtime.listTasks('user_zhengqing_xiong').map((item) => item.kind), ['REVIEW']);
  assert.deepEqual(runtime.listTasks('user_hao_xu'), []);
  const approved = runtime.reviewRequest({
    actorUserId: 'user_zhengqing_xiong', requestId: request.requestId,
    expectedRevision: request.expectedRevision, decision: 'APPROVE', reason: '业务称呼和营业日说明清晰，可以发布。',
  });
  assert.deepEqual(runtime.listTasks('user_zhengqing_xiong'), []);
  assert.deepEqual(runtime.listTasks('user_hao_xu').map((item) => item.kind), ['PUBLISH']);
  runtime.publishRequest({ actorUserId: 'user_hao_xu', requestId: request.requestId, expectedRevision: approved.expectedRevision, reason: '审核记录完整，发布新版本。' });
  assert.deepEqual(runtime.listTasks('user_hao_xu'), []);
  assert.deepEqual(runtime.listTasks('user_lexiu_sun'), []);
  assert.equal(new Set(runtime.listTasks('user_administer').map((item) => item.taskId)).size, runtime.listTasks('user_administer').length);
});

test('草稿保存会从正式基线生成可定位的对象差异，没有修改不能提交', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const base = runtime.getCatalog('group_retail_ops', 'V1')!.data;
  const draft = runtime.openDraft({
    actorUserId: 'user_administer', modelSpaceId: 'group_retail_ops', materialized: structuredClone(base),
  });
  assert.throws(() => runtime.submitDraft({ actorUserId: 'user_administer', draftId: draft.draftId, expectedDraftRevision: draft.revision, summary: '空变更' }), /没有可提交的修改/);
  const changed = structuredClone(base);
  (changed.workspaceData.aliases as Array<Record<string, unknown>>).push({ id: 'new-alias' });
  const saved = runtime.saveDraft({ actorUserId: 'user_administer', draftId: draft.draftId, expectedRevision: draft.revision, materialized: changed });
  assert.deepEqual(saved.changes.map((item) => item.category), ['同义词']);
  assert.equal(saved.changes[0]!.operation, 'ADDED');
  assert.equal(saved.changes[0]!.objectRef?.kind, 'ALIAS');
  assert.equal(saved.changes[0]!.objectRef?.objectId, 'new-alias');
  assert.ok(saved.changes[0]!.changedFields?.length);
});

test('正式基线变化后申请进入冲突状态且不能发布', () => {
  const storage = new MemoryStorage();
  const runtime = createCollaborationRuntime(storage);
  const draft = runtime.openDraft({ actorUserId: 'user_administer', modelSpaceId: 'group_retail_ops', materialized: materialized('admin') });
  const request = runtime.submitDraft({ actorUserId: 'user_administer', draftId: draft.draftId, expectedDraftRevision: draft.revision, summary: '更新指标说明' });
  const approved = runtime.reviewRequest({ actorUserId: 'user_zhengqing_xiong', requestId: request.requestId, expectedRevision: request.expectedRevision, decision: 'APPROVE', reason: '修改内容清楚，可进入发布。' });
  const changed = runtime.readShared();
  changed.catalogs.group_retail_ops = [{
    catalogId: 'external-v9', catalogVersion: 'V9', fingerprint: 'external-new-base', modelSpaceId: 'group_retail_ops',
    data: materialized('external'), authorUserId: 'legacy', reviewerUserIds: ['legacy'], publisherUserId: 'legacy', publishedAt: '2026-08-08T13:00:00.000Z', requestId: 'legacy',
  }];
  storage.setItem('linguan:collaboration:shared:v4', JSON.stringify(changed));
  assert.throws(() => runtime.publishRequest({ actorUserId: 'user_hao_xu', requestId: request.requestId, expectedRevision: approved.expectedRevision, reason: '发布' }), /正式基线已更新/);
  assert.equal(runtime.getRequest(request.requestId)?.status, 'CONFLICTED');
});

test('发布拒绝属于其他模型项目的证据Sidecar', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const base = structuredClone(runtime.getCatalog('group_retail_ops')!.data);
  base.semanticSidecar!.systemCode = 'guanyijia_erp';
  (base.workspaceData.entities as Array<Record<string, unknown>>)[0]!.description = '制造一项可提交变化';
  const draft = runtime.openDraft({ actorUserId: 'user_kenan_zhang', modelSpaceId: 'group_retail_ops', materialized: base });
  const request = runtime.submitDraft({ actorUserId: 'user_kenan_zhang', draftId: draft.draftId, expectedDraftRevision: draft.revision, summary: '错误项目Sidecar' });
  const approved = runtime.reviewRequest({ actorUserId: 'user_zhengqing_xiong', requestId: request.requestId, expectedRevision: request.expectedRevision, decision: 'APPROVE', reason: '检查门禁' });
  assert.throws(() => runtime.publishRequest({ actorUserId: 'user_hao_xu', requestId: approved.requestId, expectedRevision: approved.expectedRevision, reason: '不应发布错项目证据' }), /Sidecar 不属于当前模型项目/);
});

test('恢复协作演示会重建正式 V1、来源 R1，且不预置无因果草稿', () => {
  const storage = new MemoryStorage();
  const runtime = createCollaborationRuntime(storage);
  runtime.resetDemo('user_administer');
  assert.equal(runtime.readShared().catalogs.group_retail_ops.at(-1)?.catalogVersion, 'V1');
  assert.equal(runtime.getActiveDraft('user_kenan_zhang', 'group_retail_ops'), null);
  assert.deepEqual(runtime.readShared(), seedCollaborationState().shared);
  assert.throws(() => runtime.resetDemo('user_hao_xu'), /只有管理员/);
});

test('新状态与恢复演示预置管伊佳证据基线 V1', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const catalog = runtime.getCatalog('guanyijia_erp', 'V1');
  assert.equal(catalog?.requestId, 'SYSTEM_BASELINE');
  assert.equal(catalog?.publicationReason, '证据基线预置');
  assert.equal(catalog?.data.semanticSidecar?.schemaVersion, 2);
  assert.equal((catalog?.data.workspaceData.entities as unknown[]).length, 14);
  assert.equal((catalog?.data.workspaceData.events as unknown[]).length, 9);
  assert.equal((catalog?.data.metricData.metrics as unknown[]).length, 5);
  runtime.resetDemo('user_administer');
  assert.equal(runtime.getCatalog('guanyijia_erp', 'V1')?.fingerprint, catalog?.fingerprint);
});

test('v3状态复制到v4时只补缺失的管伊佳规范V1且保持旧键字节不变', () => {
  const storage = new MemoryStorage();
  const old = structuredClone(seedCollaborationState().shared);
  delete old.catalogs.guanyijia_erp;
  const raw = JSON.stringify({ ...old, schemaVersion: 3 });
  storage.setItem('linguan:collaboration:shared:v3', raw);
  const runtime = createCollaborationRuntime(storage);
  assert.equal(storage.getItem('linguan:collaboration:shared:v3'), raw);
  assert.equal(runtime.readShared().schemaVersion, 4);
  assert.equal(runtime.getCatalog('group_retail_ops', 'V1')?.fingerprint, old.catalogs.group_retail_ops[0]?.fingerprint);
  assert.equal(runtime.getCatalog('guanyijia_erp', 'V1')?.requestId, 'SYSTEM_BASELINE');
  assert.ok(storage.getItem('linguan:migration:collaboration-v4'));
});

test('旧正式版本迁移到共享 Catalog 并保持幂等', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const first = runtime.migrateLegacyCatalog({ modelSpaceId: 'digital_sales_warehouse', catalogVersion: 'V2', data: materialized('legacy-v2') });
  const second = runtime.migrateLegacyCatalog({ modelSpaceId: 'digital_sales_warehouse', catalogVersion: 'V2', data: materialized('ignored') });
  assert.equal(first.catalogId, second.catalogId);
  assert.equal(runtime.readShared().packageCatalogHistory.retail_digital_documents.length, 1);
  assert.equal(runtime.getPackageCatalog('retail_digital_documents', 'V2')?.authorUserId, 'LEGACY');
  assert.equal(runtime.getPackageCatalog('retail_digital_documents', 'V2')?.data.workbenchState.label, 'legacy-v2');
  assert.equal(runtime.getCatalog('group_retail_ops', 'V2'), null);
});

test('v1协作状态复制到v4并保持旧键字节不变', () => {
  const storage = new MemoryStorage();
  const old = seedCollaborationState();
  const oldCatalog = structuredClone(old.shared.catalogs.group_retail_ops[0]!);
  oldCatalog.modelSpaceId = 'omnichannel_retail_ops';
  old.shared.catalogs = { omnichannel_retail_ops: [oldCatalog] };
  const raw = JSON.stringify({ ...old.shared, schemaVersion: 1 });
  storage.setItem('linguan:collaboration:shared:v1', raw);
  const runtime = createCollaborationRuntime(storage);
  assert.equal(storage.getItem('linguan:collaboration:shared:v1'), raw);
  assert.equal(runtime.readShared().schemaVersion, 4);
  assert.equal(runtime.readShared().packageCatalogHistory.retail_omnichannel_sources.length, 1);
  assert.equal(runtime.getCatalog('group_retail_ops', 'V1')?.catalogVersion, 'V1');
  assert.ok(storage.getItem('linguan:migration:collaboration-v4'));
});

test('正式模型通过直接 Store Action 也不能修改，草稿模式才允许写入', () => {
  const before = readStoreData();
  setWorkspaceWriteAccess(false);
  useStore.getState().addEntity({ entityCode: 'forbidden', entityName: '不应写入', status: 'ACTIVE' });
  assert.equal(useStore.getState().entities.some((item) => item.entityCode === 'forbidden'), false);
  setWorkspaceWriteAccess(true);
  useStore.getState().addEntity({ entityCode: 'draft_only', entityName: '草稿对象', status: 'ACTIVE' });
  assert.equal(useStore.getState().entities.some((item) => item.entityCode === 'draft_only'), true);
  replaceStoreData(before);
  setWorkspaceWriteAccess(false);
});

test('待办抽屉先阅读差异再决策，列表只保留一个主动作', () => {
  const source = readFileSync(new URL('./task-drawer.tsx', import.meta.url), 'utf8');
  assert.match(source, /审阅|继续补充|发布/);
  assert.match(source, /onOpenTask\(task\)/);
  assert.doesNotMatch(source, /审核通过|要求修改|拒绝变更|发布新版本/);
});
