import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { connectorFamilyCatalog, connectorTypeById, connectorTypeCatalog, defaultRetailConnectorTypeIds, optionalRetailConnectorTypeIds } from './connector-catalog.ts';
import { createSourceManagementRuntime } from './runtime.ts';
import { sourceFixturePreset } from './seed-data.ts';
import { parseConnectionYaml } from './yaml-config.ts';
import { projectEvidenceQuality, projectPhysicalSources, projectSnapshotAsset } from './projection.ts';
import { copyTechnicalText, presentTechnicalValue } from './technical-value.ts';
import { sourceCenterMobileNavigation } from './source-center-navigation.ts';
import { paginateSourceHistory, sourceHistoryPageSize } from './history-pagination.ts';
import { guanyijiaEvidenceFixture, guanyijiaRepositoryEvidenceFixture } from '../ai-modeling/guanyijia-fixture.ts';

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

test('连接器家族与具体技术适配器分层，图来源明确区分两种模式', () => {
  assert.equal(connectorFamilyCatalog.length, 12);
  assert.equal(new Set(connectorFamilyCatalog.map((item) => item.familyId)).size, 12);
  assert.equal(connectorTypeCatalog.length, 13);
  assert.deepEqual(connectorFamilyCatalog.find((item) => item.familyId === 'RELATIONAL')?.adapterTypeIds, ['mysql', 'postgresql']);
  assert.equal(connectorTypeById('postgresql')?.displayName, 'PostgreSQL');
  assert.ok(connectorTypeCatalog.every((item) => item.configSchema.sections.length > 0));
  assert.ok(connectorTypeCatalog.every((item) => item.scopeSchema.sections.length > 0));
  const configurationFields = connectorTypeCatalog.flatMap((item) => item.configSchema.sections.flatMap((section) => section.fields));
  assert.ok(configurationFields.some((field) => field.key === 'credentialRef' && field.label === '凭据设置'));
  assert.ok(configurationFields.filter((field) => field.key === 'credentialRef').every((field) => field.label !== '凭据引用'));
  assert.equal(defaultRetailConnectorTypeIds.length, 8);
  assert.equal(optionalRetailConnectorTypeIds.length, 4);
  assert.equal(new Set([...defaultRetailConnectorTypeIds, ...optionalRetailConnectorTypeIds]).size, 12);
  const graph = connectorTypeCatalog.find((item) => item.family === 'GRAPH')!;
  const mode = graph.configSchema.sections.flatMap((section) => section.fields).find((field) => field.key === 'mode');
  assert.deepEqual(mode?.options?.map((item) => item.value), ['PROPERTY_GRAPH', 'RDF_GRAPH']);
});

test('零售经营迁移八个已就绪来源，四个模板不进入默认批次', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage(), now: () => '2026-08-09T12:00:00.000Z' });
  const snapshot = await runtime.read('retail_semantic_modeling');
  assert.equal(snapshot.connectorTypes.length, 13);
  assert.equal(snapshot.connections.filter((item) => item.state === 'READY').length, 8);
  assert.equal(snapshot.connections.filter((item) => item.state === 'DRAFT').length, 4);
  assert.equal(snapshot.snapshots.filter((item) => item.status === 'READY').length, 8);
  assert.equal(snapshot.defaultBatch.snapshotIds.length, 8);
  assert.ok(snapshot.defaultBatch.snapshotIds.every((id) => snapshot.snapshots.some((item) => item.snapshotId === id)));
});

test('十二类预置连接都能依次测试、发现范围并生成确定性快照', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage(), now: () => '2026-08-09T12:00:00.000Z' });
  let snapshot = await runtime.read('retail_semantic_modeling');
  for (const connection of snapshot.connections) {
    const revision = snapshot.revisions.find((item) => item.connectionId === connection.connectionId && item.revision === 1)!;
    snapshot = (await runtime.execute({
      type: 'TEST_CONNECTION', workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId, revision: 1,
    })).snapshot;
    assert.equal(snapshot.revisions.find((item) => item.connectionId === connection.connectionId && item.revision === 1)?.lastTest?.status, 'PASSED');
    const discovered = await runtime.execute({
      type: 'DISCOVER_SCOPE', workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId, revision: 1,
    });
    assert.ok(Object.keys(discovered.discovery?.objectCounts ?? {}).length > 0);
    snapshot = discovered.snapshot;
    snapshot = (await runtime.execute({
      type: 'ACTIVATE_CONNECTION', workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId, revision: 1,
    })).snapshot;
    snapshot = (await runtime.execute({
      type: 'CAPTURE_SNAPSHOT', workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId, revision: 1,
      scope: revision.defaultScope,
    })).snapshot;
  }
  assert.equal(new Set(snapshot.connections.map((item) => item.connectorTypeId)).size, 12);
  assert.ok(snapshot.connections.every((item) => item.state === 'READY'));
  assert.ok(snapshot.connections.every((connection) => snapshot.snapshots.some((item) => item.connectionId === connection.connectionId)));
});

test('管伊佳登记四来源故事且仅MySQL与GitHub拥有真实快照', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage() });
  const erp = await runtime.read('erp_data_governance');
  const retail = await runtime.read('retail_semantic_modeling');
  assert.deepEqual(erp.connections.map((item) => [item.connectorTypeId, item.state]), [
    ['mysql', 'READY'], ['github', 'READY'], ['sharepoint', 'DRAFT'], ['semantica', 'DRAFT'],
  ]);
  assert.equal(erp.snapshots.length, 2);
  assert.deepEqual(erp.snapshots.map((item) => item.snapshotId), [
    guanyijiaEvidenceFixture.manifest.snapshotId,
    guanyijiaRepositoryEvidenceFixture.manifest.snapshotId,
  ]);
  assert.ok(erp.connections.every((item) => item.workspaceId === 'erp_data_governance'));
  assert.ok(retail.connections.every((item) => item.workspaceId === 'retail_semantic_modeling'));
});

test('配置版本不会改写旧快照且未知配置不会伪造读取结果', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage(), now: () => '2026-08-09T12:00:00.000Z' });
  let snapshot = await runtime.read('retail_semantic_modeling');
  const mysql = snapshot.connections.find((item) => item.connectorTypeId === 'mysql')!;
  const oldSnapshot = snapshot.snapshots.find((item) => item.connectionId === mysql.connectionId)!;
  snapshot = (await runtime.execute({
    type: 'UPDATE_CONNECTION', workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId,
    sanitizedConfig: { host: 'unknown.example.internal', port: 3306, database: 'other' },
    defaultScope: { schemas: ['public'], tables: ['*'] }, credentialRef: 'secret://other/mysql',
  })).snapshot;
  assert.equal(snapshot.connections.find((item) => item.connectionId === mysql.connectionId)?.activeRevision, 1);
  assert.equal(snapshot.snapshots.find((item) => item.snapshotId === oldSnapshot.snapshotId)?.connectionRevision, 1);
  const tested = await runtime.execute({
    type: 'TEST_CONNECTION', workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId, revision: 2,
  });
  assert.equal(tested.snapshot.revisions.find((item) => item.connectionId === mysql.connectionId && item.revision === 2)?.lastTest?.status, 'NO_RESULT');
  await assert.rejects(() => runtime.execute({
    type: 'DISCOVER_SCOPE', workspaceId: snapshot.workspaceId, expectedRevision: tested.snapshot.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId, revision: 2,
  }), /请先通过连接测试/);
  await assert.rejects(() => runtime.execute({
    type: 'CAPTURE_SNAPSHOT', workspaceId: snapshot.workspaceId, expectedRevision: tested.snapshot.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId, revision: 2,
    scope: { schemas: ['public'], tables: ['*'] },
  }), /请先激活当前配置版本/);
  assert.equal(tested.snapshot.snapshots.some((item) => item.connectionRevision === 2), false);
});

test('新配置版本必须按测试、发现、激活、读取顺序推进', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage(), now: () => '2026-08-09T12:00:00.000Z' });
  let state = await runtime.read('retail_semantic_modeling');
  const mysql = state.connections.find((item) => item.connectorTypeId === 'mysql')!;
  const revision1 = state.revisions.find((item) => item.connectionId === mysql.connectionId && item.revision === 1)!;

  state = (await runtime.execute({
    type: 'UPDATE_CONNECTION', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId,
    sanitizedConfig: { ...revision1.sanitizedConfig, fixtureRevision: 2 },
    credentialRef: revision1.credentialRef, defaultScope: revision1.defaultScope,
  })).snapshot;
  const revision2 = state.revisions.find((item) => item.connectionId === mysql.connectionId && item.revision === 2)!;
  assert.equal(revision2.stage, 'DRAFT');
  assert.equal(revision2.lastTest, undefined);
  assert.equal(mysql.activeRevision, 1);

  await assert.rejects(() => runtime.execute({
    type: 'DISCOVER_SCOPE', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId, revision: 2,
  }), /请先通过连接测试/);
  await assert.rejects(() => runtime.execute({
    type: 'ACTIVATE_CONNECTION', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId, revision: 2,
  }), /请先发现并确认读取范围/);
  await assert.rejects(() => runtime.execute({
    type: 'CAPTURE_SNAPSHOT', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId,
    revision: 2, scope: revision1.defaultScope,
  }), /请先激活当前配置版本/);

  state = (await runtime.execute({
    type: 'TEST_CONNECTION', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId, revision: 2,
  })).snapshot;
  assert.equal(state.revisions.find((item) => item.connectionId === mysql.connectionId && item.revision === 2)?.stage, 'TESTED');

  const discovered = await runtime.execute({
    type: 'DISCOVER_SCOPE', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId, revision: 2,
  });
  state = discovered.snapshot;
  const afterDiscovery = state.revisions.find((item) => item.connectionId === mysql.connectionId && item.revision === 2)!;
  assert.equal(afterDiscovery.stage, 'DISCOVERED');
  assert.equal(afterDiscovery.discovery?.summary, discovered.discovery?.summary);

  state = (await runtime.execute({
    type: 'ACTIVATE_CONNECTION', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: mysql.connectionId, revision: 2,
  })).snapshot;
  assert.equal(state.revisions.find((item) => item.connectionId === mysql.connectionId && item.revision === 2)?.stage, 'ACTIVE');
  assert.equal(state.connections.find((item) => item.connectionId === mysql.connectionId)?.activeRevision, 2);
});

test('连接器预置配置按工作空间隔离，不把零售 MySQL 套到管伊佳', () => {
  assert.equal(sourceFixturePreset('erp_data_governance', 'mysql')?.displayName, '管伊佳 MySQL · jsh_erp');
  assert.equal(sourceFixturePreset('retail_semantic_modeling', 'mysql')?.displayName, '零售交易库');
  assert.equal(sourceFixturePreset('erp_data_governance', 'mongodb'), null);
});

test('PostgreSQL 只创建空白实例草稿，不继承任何演示来源或冻结快照', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage() });
  const initial = await runtime.read('erp_data_governance');
  const created = await runtime.execute({
    type: 'CREATE_CONNECTION', workspaceId: initial.workspaceId, expectedRevision: initial.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: 'postgresql_1', connectorTypeId: 'postgresql',
    displayName: 'ERP 备用交易库', environment: '', sanitizedConfig: {}, defaultScope: {},
  });
  const connection = created.snapshot.connections.find((item) => item.connectionId === 'postgresql_1');
  const revision = created.snapshot.revisions.find((item) => item.connectionId === 'postgresql_1' && item.revision === 1);
  assert.deepEqual(connection && [connection.displayName, connection.state, connection.activeRevision], ['ERP 备用交易库', 'DRAFT', 0]);
  assert.deepEqual(revision?.sanitizedConfig, {});
  assert.deepEqual(revision?.defaultScope, {});
  assert.equal(created.snapshot.snapshots.some((item) => item.connectionId === 'postgresql_1'), false);
  const tested = await runtime.execute({
    type: 'TEST_CONNECTION', workspaceId: created.snapshot.workspaceId, expectedRevision: created.snapshot.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: 'postgresql_1', revision: 1,
  });
  assert.equal(tested.snapshot.revisions.find((item) => item.connectionId === 'postgresql_1')?.lastTest?.status, 'NO_RESULT');
});

test('YAML导出不包含凭据，导入先返回新增更新冲突预览', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage() });
  const artifact = await runtime.exportYaml('retail_semantic_modeling');
  assert.match(artifact.content, /^schemaVersion: 1/m);
  assert.match(artifact.content, /credentialRef: secret:\/\//);
  assert.doesNotMatch(artifact.content, /password|token|privateKey/i);
  const parsed = parseConnectionYaml(artifact.content);
  assert.equal(parsed.workspaceId, 'retail_semantic_modeling');
  assert.equal(parsed.connections.length, 12);
  const current = await runtime.read('retail_semantic_modeling');
  const preview = await runtime.execute({
    type: 'PREVIEW_YAML_IMPORT', workspaceId: current.workspaceId, expectedRevision: current.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, content: artifact.content,
  });
  assert.equal(preview.importPreview?.unchanged.length, 12);
  assert.equal(preview.importPreview?.errors.length, 0);
});

test('来源管理权限区分管理员与编辑者', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage() });
  const snapshot = await runtime.read('retail_semantic_modeling');
  await assert.rejects(() => runtime.execute({
    type: 'DISABLE_CONNECTION', workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
    actor: { userId: 'user_editor', role: 'EDITOR' }, connectionId: snapshot.connections[0].connectionId,
  }), /只有管理员/);
  const ready = snapshot.connections[0];
  const captured = await runtime.execute({
    type: 'CAPTURE_SNAPSHOT', workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
    actor: { userId: 'user_editor', role: 'EDITOR' }, connectionId: ready.connectionId,
    revision: ready.activeRevision, scope: snapshot.revisions.find((item) => item.connectionId === ready.connectionId && item.revision === ready.activeRevision)!.defaultScope,
  });
  assert.equal(captured.snapshot.snapshots.at(-1)?.status, 'READY');
});

test('物理映射只读取具备物理结构能力的已就绪快照', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage() });
  const snapshot = await runtime.read('retail_semantic_modeling');
  const physical = projectPhysicalSources(snapshot);
  assert.ok(physical.some((item) => item.connectorTypeId === 'mysql'));
  assert.ok(physical.some((item) => item.connectorTypeId === 'github'));
  assert.ok(!physical.some((item) => item.connectorTypeId === 'sharepoint'));
  assert.ok(physical.every((item) => item.snapshot.status === 'READY' || item.snapshot.status === 'PARTIAL'));
});

test('派生来源不重复计算为独立佐证并可投影为旧建模来源', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage() });
  const snapshot = await runtime.read('retail_semantic_modeling');
  const quality = projectEvidenceQuality(snapshot);
  assert.ok(quality.coveredClaims > 0);
  assert.ok(quality.derivedClaims > 0);
  assert.ok(quality.independentSources < snapshot.snapshots.length);
  const mysqlSnapshot = snapshot.snapshots.find((item) => item.connectionId === 'retail_mysql')!;
  const asset = projectSnapshotAsset(snapshot, mysqlSnapshot.snapshotId)!;
  assert.equal(asset.status, 'READY');
  assert.equal(asset.fixtureKey, mysqlSnapshot.snapshotId);
  assert.equal(asset.sourceId, mysqlSnapshot.legacySourceId);
});

test('来源设置提供添加来源、配置文档、版本、资料历史与管理员YAML入口', () => {
  const center = readFileSync(new URL('./source-center.tsx', import.meta.url), 'utf8');
  for (const text of ['添加来源', '已配置来源', '配置文档', '读取范围', '配置版本', '资料历史', '审计记录', '导入 YAML', '导出 YAML']) {
    assert.match(center, new RegExp(text));
  }
  assert.match(center, /connectorType\.configSchema/);
  assert.match(center, /connectorType\.scopeSchema/);
  assert.match(center, /TEST_CONNECTION/);
  assert.match(center, /DISCOVER_SCOPE/);
  assert.match(center, /CAPTURE_SNAPSHOT/);
  assert.match(center, /正在读取/);
  assert.match(center, /加入当前建模批次/);
  assert.match(center, /attachedSnapshotIds/);
  assert.match(center, /当前个人草稿正在引用/);
  assert.match(center, /当前证据批次/);
  assert.match(center, /FREEZE_BATCH/);
  assert.match(center, /onBatchReady/);
  assert.doesNotMatch(center, /填入 R2 批量配置/);
});

test('标准化运行前的来源编排使用业务实例与运行绑定，而不是通用连接 Seed', () => {
  const center = readFileSync(new URL('./source-center.tsx', import.meta.url), 'utf8');
  assert.match(center, /demoSourceConfiguration/);
  assert.match(center, /RUN_CONFIGURATION/);
  assert.match(center, /source-board-slot/);
  assert.match(center, /connectorFamilyCatalog/);
  assert.match(center, /adapterPickerFamilyId/);
  assert.match(center, /label: '实例'/);
  assert.match(center, /source-board-slot/);
  assert.match(center, /运行来源/);
  assert.doesNotMatch(center, /sourceFixturePreset/);
  assert.doesNotMatch(center, /搜索连接实例/);
  assert.doesNotMatch(center, /查看详情/);
  assert.match(center, /businessName/);
  assert.match(center, /snapshotReadiness/);
});

test('运行来源只在页首说明一次锁定规则，快照概览不重复固定或只读提示', () => {
  const center = readFileSync(new URL('./source-center.tsx', import.meta.url), 'utf8');
  const sourceBoard = center.slice(
    center.indexOf("if (mode === 'RUN_CONFIGURATION' && demoSourceBoard)"),
    center.indexOf("if (mode === 'SNAPSHOT_OVERVIEW' && demoSourceConfiguration)"),
  );

  assert.equal((sourceBoard.match(/开始后[^。]*锁定/gu) ?? []).length, 1);
  assert.doesNotMatch(sourceBoard, /source-board-section-heading"><div><strong>已选来源<\/strong><small>/u);
  assert.doesNotMatch(center, /固定资料范围/u);
  assert.doesNotMatch(center, /<Tag color="blue">只读<\/Tag>/u);
  assert.doesNotMatch(center, /<h3>资料状态<\/h3>/u);
});

test('标准化来源编排使用业务化来源名称和可执行的错误提示', () => {
  const center = readFileSync(new URL('./source-center.tsx', import.meta.url), 'utf8');

  assert.match(center, /业务说明（演示资料）/);
  assert.match(center, /ERP 管理制度（演示草案）/);
  assert.match(center, /企业术语图（由已有资料整理）/);
  assert.match(center, /function presentSourceCenterError/);
  assert.match(center, /浏览器暂时无法保存本次设置/);
  assert.match(center, /连接暂时不可用/);
  assert.match(center, /配置格式无法识别/);
  assert.match(center, /已整理可查看的来源位置/);
  assert.match(center, /固定资料已准备好/);
  assert.match(center, /资料版本已固定/);
  assert.doesNotMatch(center, /已生成证据定位与来源血缘/);
  assert.doesNotMatch(center, /message\.error\(error instanceof Error/);
  assert.match(center, /仅保存脱敏连接设置；密码和令牌不会保存在浏览器中。/);
});

test('来源中心以可读资料位置代替内容指纹等技术标识', () => {
  const center = readFileSync(new URL('./source-center.tsx', import.meta.url), 'utf8');
  const readable = readFileSync(new URL('../../components/ReadableTechnicalValue.tsx', import.meta.url), 'utf8');
  assert.match(center, /ReadableTechnicalValue/);
  assert.match(center, /资料位置/);
  assert.doesNotMatch(center, /内容指纹/);
  assert.doesNotMatch(center, /SHA-?256/iu);
  assert.doesNotMatch(center, /<dt>凭据引用<\/dt>/);
  assert.match(center, /<dt>凭据<\/dt><dd>已配置<\/dd>/);
  assert.doesNotMatch(center, /<dd title=/);
  assert.match(readable, /aria-expanded/);
  assert.match(readable, /copyTechnicalText\(presented\.copy, navigator\.clipboard\)/);
  assert.match(readable, /复制失败/);
  assert.match(readable, /展开/);
  assert.match(readable, /复制/);
});

test('来源设置不会把对象配置或数组直接显示成原始 JSON', () => {
  assert.deepEqual(presentTechnicalValue({ host: 'example.internal', token: 'masked' }), {
    display: '已配置',
    copy: '{\n  "host": "example.internal",\n  "token": "masked"\n}',
  });
  assert.deepEqual(presentTechnicalValue([{ table: 'jsh_depot_head' }, { table: 'jsh_depot_item' }]), {
    display: '已配置（2 项）',
    copy: '[\n  {\n    "table": "jsh_depot_head"\n  },\n  {\n    "table": "jsh_depot_item"\n  }\n]',
  });
  assert.deepEqual(presentTechnicalValue(['表结构', '存储过程']), {
    display: '表结构、存储过程',
    copy: '表结构、存储过程',
  });
});

test('390来源中心是列表到详情的单滚动，1440仍为左右双栏', () => {
  const center = readFileSync(new URL('./source-center.tsx', import.meta.url), 'utf8');
  const navigation = readFileSync(new URL('./source-center-navigation.ts', import.meta.url), 'utf8');
  const css = readFileSync(new URL('./source-management.css', import.meta.url), 'utf8');
  assert.match(center, /source-connections-view/);
  assert.match(center, /sourceCenterMobileNavigation/);
  assert.match(navigation, /mobile-detail/);
  assert.match(navigation, /mobile-list/);
  assert.match(center, /source-mobile-back/);
  assert.match(css, /grid-template-columns:\s*minmax\(230px,\s*300px\)\s+minmax\(0,\s*1fr\)/);
  assert.match(css, /@media\s*\(max-width:\s*600px\)/);
  assert.match(css, /mobile-list[^}]*source-connection-detail[^}]*display:\s*none/s);
  assert.match(css, /mobile-detail[^}]*source-connection-list[^}]*display:\s*none/s);
  assert.doesNotMatch(css, /source-connection-list\s*\{\s*max-height:\s*220px;\s*overflow:\s*auto;/);
});

test('来源配置版本、快照和审计记录统一每页20条', () => {
  const records = Array.from({ length: 41 }, (_, index) => `record-${index + 1}`);
  assert.equal(sourceHistoryPageSize, 20);
  assert.deepEqual(paginateSourceHistory(records, 1), {
    items: records.slice(0, 20), page: 1, pageCount: 3, total: 41,
  });
  assert.deepEqual(paginateSourceHistory(records, 3), {
    items: records.slice(40), page: 3, pageCount: 3, total: 41,
  });
  assert.equal(paginateSourceHistory(records, 99).page, 3);
  const center = readFileSync(new URL('./source-center.tsx', import.meta.url), 'utf8');
  assert.match(center, /paginateSourceHistory/);
  assert.match(center, /source-history-pagination/);
});

test('来源中心移动导航可从列表进入详情并返回，复制失败成为显式状态', async () => {
  const detail = sourceCenterMobileNavigation({ type: 'SELECT', connectionId: 'retail_mysql' });
  assert.deepEqual(detail, { selectedConnectionId: 'retail_mysql', className: 'mobile-detail' });
  assert.deepEqual(sourceCenterMobileNavigation({ type: 'BACK' }), {
    selectedConnectionId: undefined, className: 'mobile-list',
  });
  const copied: string[] = [];
  assert.equal(await copyTechnicalText('manifest://retail/mysql', { writeText: async (value) => { copied.push(value); } }), true);
  assert.deepEqual(copied, ['manifest://retail/mysql']);
  assert.equal(await copyTechnicalText('denied', { writeText: async () => { throw new Error('denied'); } }), false);
  assert.equal(await copyTechnicalText('unavailable', undefined), false);
});

test('管理员可将八个来源升级到 revision 2 并冻结不可变 R2 批次', async () => {
  const runtime = createSourceManagementRuntime({ storage: new MemoryStorage(), now: () => '2026-08-09T18:00:00.000Z' });
  let state = await runtime.read('retail_semantic_modeling');
  const ready = state.connections.filter((item) => item.state === 'READY');
  assert.equal(ready.length, 8);

  for (const connection of ready) {
    const revision1 = state.revisions.find((item) => item.connectionId === connection.connectionId && item.revision === 1)!;
    state = (await runtime.execute({
      type: 'UPDATE_CONNECTION', workspaceId: state.workspaceId, expectedRevision: state.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId,
      sanitizedConfig: { ...revision1.sanitizedConfig, fixtureRevision: 2 },
      credentialRef: revision1.credentialRef, defaultScope: revision1.defaultScope,
    })).snapshot;
    state = (await runtime.execute({
      type: 'TEST_CONNECTION', workspaceId: state.workspaceId, expectedRevision: state.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId, revision: 2,
    })).snapshot;
    assert.equal(state.revisions.find((item) => item.connectionId === connection.connectionId && item.revision === 2)?.lastTest?.status, 'PASSED');
    state = (await runtime.execute({
      type: 'DISCOVER_SCOPE', workspaceId: state.workspaceId, expectedRevision: state.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId, revision: 2,
    })).snapshot;
    state = (await runtime.execute({
      type: 'ACTIVATE_CONNECTION', workspaceId: state.workspaceId, expectedRevision: state.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId, revision: 2,
    })).snapshot;
    state = (await runtime.execute({
      type: 'CAPTURE_SNAPSHOT', workspaceId: state.workspaceId, expectedRevision: state.revision,
      actor: { userId: 'user_administer', role: 'ADMIN' }, connectionId: connection.connectionId,
      revision: 2, scope: revision1.defaultScope,
    })).snapshot;
  }

  const r2SnapshotIds = state.connections.filter((item) => item.state === 'READY').map((connection) =>
    state.snapshots.find((snapshot) => snapshot.connectionId === connection.connectionId && snapshot.connectionRevision === 2)!.snapshotId);
  state = (await runtime.execute({
    type: 'FREEZE_BATCH', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' }, projectId: 'group_retail_ops', revision: 2,
    snapshotIds: [...r2SnapshotIds].reverse(),
  })).snapshot;
  assert.equal(state.defaultBatch.batchId, 'group_retail_ops-sources-r2');
  assert.equal(state.defaultBatch.revision, 2);
  assert.equal(state.defaultBatch.state, 'FROZEN');
  assert.equal(state.defaultBatch.snapshotIds.length, 8);
  assert.ok(state.defaultBatch.snapshotIds.every((snapshotId) => state.snapshots.find((item) => item.snapshotId === snapshotId)?.connectionRevision === 2));

  state = (await runtime.execute({
    type: 'RESET_DEMO', workspaceId: state.workspaceId, expectedRevision: state.revision,
    actor: { userId: 'user_administer', role: 'ADMIN' },
  })).snapshot;
  assert.equal(state.defaultBatch.revision, 1);
  assert.equal(state.connections.filter((item) => item.state === 'READY').length, 8);
  assert.ok(state.connections.every((connection) => connection.activeRevision === (connection.state === 'READY' ? 1 : 0)));
  assert.equal(state.revisions.some((item) => item.revision === 2), false);
});
