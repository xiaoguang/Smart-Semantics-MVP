import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { connectorTypeById, connectorTypeCatalog } from './connector-catalog.ts';
import { stableSourceJson } from './fixture-adapter.ts';
import { createSeedWorkspace, sourceFixtureAdapter, type StoredSourceWorkspace } from './seed-data.ts';
import type {
  ConnectionYamlDocument, ExportArtifact, ImportPreview, SourceActor,
  SourceManagementCommand, SourceManagementResult, SourceManagementRuntime, SourceManagementSnapshot, YamlConnection,
} from './types.ts';
import { parseConnectionYaml, serializeConnectionYaml } from './yaml-config.ts';

type StorageLike = { getItem(key: string): string | null; setItem(key: string, value: string): void; removeItem?(key: string): void };
type StoredRoot = { schemaVersion: 1; workspaces: Record<string, StoredSourceWorkspace> };
const storageKey = 'linguan:source-management:shared:v1';
const clone = <T>(value: T): T => structuredClone(value);
const forbiddenKey = /(^|_)(password|passwd|token|secret|private[_-]?key)(_|$)/i;

function assertSanitized(input: Record<string, unknown>, path = 'config') {
  for (const [key, value] of Object.entries(input)) {
    if (forbiddenKey.test(key)) throw new Error(`${path}.${key} 不允许保存敏感凭据`);
    if (value && typeof value === 'object' && !Array.isArray(value)) assertSanitized(value as Record<string, unknown>, `${path}.${key}`);
  }
}

function requireAdmin(actor: SourceActor) {
  if (actor.role !== 'ADMIN') throw new Error('只有管理员可以管理共享连接');
}

function requireCapture(actor: SourceActor) {
  if (actor.role !== 'ADMIN' && actor.role !== 'EDITOR') throw new Error('当前角色不能生成来源快照');
}

function revisionFor(state: StoredSourceWorkspace, connectionId: string, revision: number) {
  const value = state.revisions.find((item) => item.connectionId === connectionId && item.revision === revision);
  if (!value) throw new Error('连接配置版本不存在');
  return value;
}

function connectionFor(state: StoredSourceWorkspace, connectionId: string) {
  const value = state.connections.find((item) => item.connectionId === connectionId);
  if (!value) throw new Error('来源连接不存在');
  return value;
}

function latestRevision(state: StoredSourceWorkspace, connectionId: string) {
  return state.revisions.filter((item) => item.connectionId === connectionId).sort((a, b) => b.revision - a.revision)[0];
}

function toSnapshot(state: StoredSourceWorkspace): SourceManagementSnapshot {
  return { ...clone(state), connectorTypes: clone(connectorTypeCatalog) };
}

function normalizeWorkspace(state: StoredSourceWorkspace) {
  state.revisions.forEach((revision) => {
    if (revision.stage) return;
    const connection = state.connections.find((item) => item.connectionId === revision.connectionId);
    revision.stage = connection?.activeRevision === revision.revision && connection.state === 'READY'
      ? 'ACTIVE' : revision.lastTest?.status === 'PASSED' ? 'TESTED' : 'DRAFT';
  });
  return state;
}

function audit(state: StoredSourceWorkspace, actor: SourceActor, action: string, targetId: string, detail: string, now: string) {
  state.audit.unshift({ auditId: `${action.toLowerCase()}-${state.revision + 1}-${state.audit.length + 1}`, actorUserId: actor.userId, action, targetId, createdAt: now, detail });
}

function validateYaml(document: ConnectionYamlDocument, state: StoredSourceWorkspace): ImportPreview {
  const preview: ImportPreview = { created: [], updated: [], unchanged: [], conflicts: [], errors: [] };
  if (document.workspaceId !== state.workspaceId) {
    preview.errors.push({ reason: `配置属于 ${document.workspaceId}，不能导入 ${state.workspaceId}` });
    return preview;
  }
  const seen = new Set<string>();
  for (const connection of document.connections) {
    if (seen.has(connection.connectionId)) { preview.conflicts.push({ connectionId: connection.connectionId, reason: 'YAML 中连接编码重复' }); continue; }
    seen.add(connection.connectionId);
    if (!connectorTypeById(connection.connectorType)) { preview.errors.push({ connectionId: connection.connectionId, reason: '连接器类型不受支持' }); continue; }
    try { assertSanitized(connection.config, `${connection.connectionId}.config`); } catch (error) { preview.errors.push({ connectionId: connection.connectionId, reason: error instanceof Error ? error.message : String(error) }); continue; }
    const existing = state.connections.find((item) => item.connectionId === connection.connectionId);
    if (!existing) { preview.created.push(connection.connectionId); continue; }
    if (existing.connectorTypeId !== connection.connectorType) { preview.conflicts.push({ connectionId: connection.connectionId, reason: '不能修改现有连接的连接器类型' }); continue; }
    const latest = latestRevision(state, connection.connectionId);
    const same = existing.displayName === connection.displayName && existing.environment === connection.environment
      && stableSourceJson(latest.sanitizedConfig) === stableSourceJson(connection.config)
      && stableSourceJson(latest.defaultScope) === stableSourceJson(connection.readScope)
      && (latest.credentialRef ?? '') === (connection.credentialRef ?? '');
    (same ? preview.unchanged : preview.updated).push(connection.connectionId);
  }
  return preview;
}

function yamlConnection(state: StoredSourceWorkspace, connectionId: string): YamlConnection {
  const connection = connectionFor(state, connectionId);
  const revision = latestRevision(state, connectionId);
  return {
    connectionId, connectorType: connection.connectorTypeId, displayName: connection.displayName,
    environment: connection.environment, config: clone(revision.sanitizedConfig), credentialRef: revision.credentialRef,
    readScope: clone(revision.defaultScope),
  };
}

export function createSourceManagementRuntime(options: { storage: StorageLike; now?: () => string }): SourceManagementRuntime {
  const now = options.now ?? (() => new Date().toISOString());
  const loadRoot = (): StoredRoot => {
    try {
      const parsed = JSON.parse(options.storage.getItem(storageKey) ?? '') as StoredRoot;
      if (parsed.schemaVersion === 1 && parsed.workspaces) return parsed;
    } catch { /* create safe v1 state without touching legacy keys */ }
    return { schemaVersion: 1, workspaces: {} };
  };
  const saveRoot = (root: StoredRoot) => options.storage.setItem(storageKey, JSON.stringify(root));
  const loadWorkspace = async (workspaceId: string) => {
    if (!['retail_semantic_modeling', 'erp_data_governance'].includes(workspaceId)) throw new Error('来源管理工作空间不存在');
    const root = loadRoot();
    if (!root.workspaces[workspaceId]) {
      root.workspaces[workspaceId] = await createSeedWorkspace(workspaceId, now());
      saveRoot(root);
    }
    return { root, state: normalizeWorkspace(root.workspaces[workspaceId]) };
  };

  return {
    async read(workspaceId) {
      const { state } = await loadWorkspace(workspaceId);
      return toSnapshot(state);
    },
    async exportYaml(workspaceId): Promise<ExportArtifact> {
      const { state } = await loadWorkspace(workspaceId);
      const content = serializeConnectionYaml({ schemaVersion: 1, workspaceId, connections: state.connections.map((item) => yamlConnection(state, item.connectionId)) });
      return { fileName: `${workspaceId}-sources.yaml`, mediaType: 'application/yaml', content };
    },
    async execute(command: SourceManagementCommand): Promise<SourceManagementResult> {
      const { root, state } = await loadWorkspace(command.workspaceId);
      if (state.revision !== command.expectedRevision) throw new Error('来源配置已更新，请重新加载');
      const timestamp = now();
      if (command.type === 'PREVIEW_YAML_IMPORT') {
        requireAdmin(command.actor);
        const document = parseConnectionYaml(command.content);
        return { snapshot: toSnapshot(state), importPreview: validateYaml(document, state) };
      }
      if (command.type === 'RESET_DEMO') {
        requireAdmin(command.actor);
        const restored = await createSeedWorkspace(command.workspaceId, timestamp);
        root.workspaces[command.workspaceId] = restored;
        saveRoot(root);
        return { snapshot: toSnapshot(restored) };
      }
      if (command.type === 'CREATE_CONNECTION') {
        requireAdmin(command.actor);
        if (state.connections.some((item) => item.connectionId === command.connectionId)) throw new Error('连接编码已存在');
        if (!connectorTypeById(command.connectorTypeId)) throw new Error('连接器类型不受支持');
        assertSanitized(command.sanitizedConfig);
        state.connections.push({ connectionId: command.connectionId, workspaceId: state.workspaceId, connectorTypeId: command.connectorTypeId, displayName: command.displayName.trim(), environment: command.environment.trim(), activeRevision: 0, state: 'DRAFT' });
        state.revisions.push({ connectionId: command.connectionId, revision: 1, stage: 'DRAFT', sanitizedConfig: clone(command.sanitizedConfig), credentialRef: command.credentialRef, defaultScope: clone(command.defaultScope), createdBy: command.actor.userId, createdAt: timestamp });
        audit(state, command.actor, 'CREATE_CONNECTION', command.connectionId, '创建共享连接草稿 revision 1', timestamp);
      } else if (command.type === 'UPDATE_CONNECTION') {
        requireAdmin(command.actor); assertSanitized(command.sanitizedConfig);
        const connection = connectionFor(state, command.connectionId);
        const revision = (latestRevision(state, connection.connectionId)?.revision ?? 0) + 1;
        state.revisions.push({ connectionId: connection.connectionId, revision, stage: 'DRAFT', sanitizedConfig: clone(command.sanitizedConfig), credentialRef: command.credentialRef, defaultScope: clone(command.defaultScope), createdBy: command.actor.userId, createdAt: timestamp });
        audit(state, command.actor, 'UPDATE_CONNECTION', connection.connectionId, `创建配置 revision ${revision}；旧快照保持不变`, timestamp);
      } else if (command.type === 'TEST_CONNECTION') {
        requireAdmin(command.actor);
        const connection = connectionFor(state, command.connectionId);
        const revision = revisionFor(state, connection.connectionId, command.revision);
        revision.lastTest = await sourceFixtureAdapter.test({ connection, revision, now: timestamp });
        revision.stage = revision.lastTest.status === 'PASSED' ? 'TESTED' : 'DRAFT';
        if (revision.lastTest.status === 'FAILED') connection.state = 'ERROR';
        audit(state, command.actor, 'TEST_CONNECTION', connection.connectionId, revision.lastTest.message, timestamp);
      } else if (command.type === 'DISCOVER_SCOPE') {
        requireCapture(command.actor);
        const connection = connectionFor(state, command.connectionId);
        const revision = revisionFor(state, connection.connectionId, command.revision);
        if (revision.lastTest?.status !== 'PASSED' || !['TESTED', 'DISCOVERED'].includes(revision.stage)) {
          throw new Error('请先通过连接测试再发现读取范围');
        }
        const discovery = await sourceFixtureAdapter.discover({ connection, revision, now: timestamp });
        revision.discovery = clone(discovery);
        revision.stage = 'DISCOVERED';
        audit(state, command.actor, 'DISCOVER_SCOPE', connection.connectionId, discovery.summary, timestamp);
        state.revision += 1; root.workspaces[state.workspaceId] = state; saveRoot(root);
        return { snapshot: toSnapshot(state), discovery };
      } else if (command.type === 'ACTIVATE_CONNECTION') {
        requireAdmin(command.actor);
        const connection = connectionFor(state, command.connectionId);
        const revision = revisionFor(state, connection.connectionId, command.revision);
        if (revision.stage !== 'DISCOVERED' || !revision.discovery) throw new Error('请先发现并确认读取范围再激活配置');
        connection.activeRevision = revision.revision; connection.state = 'READY';
        revision.stage = 'ACTIVE';
        audit(state, command.actor, 'ACTIVATE_CONNECTION', connection.connectionId, `激活 revision ${revision.revision}`, timestamp);
      } else if (command.type === 'DISABLE_CONNECTION') {
        requireAdmin(command.actor);
        const connection = connectionFor(state, command.connectionId); connection.state = 'DISABLED';
        audit(state, command.actor, 'DISABLE_CONNECTION', connection.connectionId, '停用共享连接；旧快照和Catalog保持可读', timestamp);
      } else if (command.type === 'CAPTURE_SNAPSHOT') {
        requireCapture(command.actor);
        const connection = connectionFor(state, command.connectionId);
        const revision = revisionFor(state, connection.connectionId, command.revision);
        if (connection.activeRevision !== revision.revision || revision.stage !== 'ACTIVE') throw new Error('请先激活当前配置版本再生成快照');
        const captured = await sourceFixtureAdapter.capture({ connection, revision, now: timestamp }, command.scope);
        const existing = state.snapshots.find((item) => item.fingerprint === captured.fingerprint);
        if (!existing) {
          const snapshotId = `${connection.connectionId}-snapshot-${captured.fingerprint.slice(0, 12)}`;
          state.snapshots.push({ ...captured, snapshotId, connectionId: connection.connectionId, connectionRevision: revision.revision, scope: clone(command.scope), capturedAt: timestamp });
          state.claims.push(...captured.claims.map((claim) => ({ ...claim, snapshotId, connectionRevision: revision.revision })));
          audit(state, command.actor, 'CAPTURE_SNAPSHOT', snapshotId, captured.summary, timestamp);
        } else audit(state, command.actor, 'REUSE_SNAPSHOT', existing.snapshotId, '相同配置版本与读取范围复用已有不可变快照', timestamp);
      } else if (command.type === 'FREEZE_BATCH') {
        requireAdmin(command.actor);
        if (command.projectId !== state.defaultBatch.projectId) throw new Error('证据批次不属于当前模型项目');
        if (command.revision <= state.defaultBatch.revision) throw new Error('新批次修订必须大于当前修订');
        const snapshotIds = [...new Set(command.snapshotIds)].sort();
        if (snapshotIds.length === 0) throw new Error('证据批次至少包含一个快照');
        snapshotIds.forEach((snapshotId) => {
          const snapshot = state.snapshots.find((item) => item.snapshotId === snapshotId);
          if (!snapshot || !['READY', 'PARTIAL'].includes(snapshot.status)) throw new Error(`来源快照不可加入批次：${snapshotId}`);
        });
        state.defaultBatch = {
          batchId: `${command.projectId}-sources-r${command.revision}`,
          projectId: command.projectId,
          revision: command.revision,
          snapshotIds,
          fingerprint: sourceBatchFingerprint(snapshotIds),
          state: 'FROZEN',
        };
        audit(state, command.actor, 'FREEZE_BATCH', state.defaultBatch.batchId, `冻结 ${snapshotIds.length} 个来源快照`, timestamp);
      } else if (command.type === 'APPLY_YAML_IMPORT') {
        requireAdmin(command.actor);
        const document = parseConnectionYaml(command.content);
        const preview = validateYaml(document, state);
        if (preview.errors.length || preview.conflicts.length) return { snapshot: toSnapshot(state), importPreview: preview };
        for (const item of document.connections) {
          const existing = state.connections.find((connection) => connection.connectionId === item.connectionId);
          if (!existing) {
            state.connections.push({ connectionId: item.connectionId, workspaceId: state.workspaceId, connectorTypeId: item.connectorType, displayName: item.displayName, environment: item.environment, activeRevision: 0, state: 'DRAFT' });
            state.revisions.push({ connectionId: item.connectionId, revision: 1, stage: 'DRAFT', sanitizedConfig: clone(item.config), credentialRef: item.credentialRef, defaultScope: clone(item.readScope), createdBy: command.actor.userId, createdAt: timestamp });
          } else if (preview.updated.includes(item.connectionId)) {
            existing.displayName = item.displayName; existing.environment = item.environment;
            state.revisions.push({ connectionId: item.connectionId, revision: latestRevision(state, item.connectionId).revision + 1, stage: 'DRAFT', sanitizedConfig: clone(item.config), credentialRef: item.credentialRef, defaultScope: clone(item.readScope), createdBy: command.actor.userId, createdAt: timestamp });
          }
        }
        audit(state, command.actor, 'APPLY_YAML_IMPORT', state.workspaceId, `新增 ${preview.created.length}，更新 ${preview.updated.length}，跳过 ${preview.unchanged.length}`, timestamp);
      }
      state.revision += 1;
      root.workspaces[state.workspaceId] = state;
      saveRoot(root);
      return { snapshot: toSnapshot(state) };
    },
  };
}

export function sourceBatchFingerprint(snapshotIds: string[]) {
  return sha256HexSync([...new Set(snapshotIds)].sort().join('\n'));
}
