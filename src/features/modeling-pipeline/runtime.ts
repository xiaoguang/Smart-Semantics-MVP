import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type {
  ModelingPipelineCommand, ModelingPipelineRuntime, ModelingPipelineRuntimeOptions,
  ModelingPipelineScope, ModelingPipelineSnapshot, PipelineSource,
} from './types.ts';

type StoredPipeline = Omit<ModelingPipelineSnapshot, 'sources' | 'coverage'>;
const clone = <T>(value: T): T => structuredClone(value);
const chinese = /[\u3400-\u9fff]/;

function scopeKey(scope: ModelingPipelineScope) {
  return `${scope.workspaceId}:${scope.projectId}:${scope.draftId}`;
}

function initial(scope: ModelingPipelineScope): StoredPipeline {
  return {
    schemaVersion: 1, scope: clone(scope), revision: 0, state: 'COLLECTING', attachedSnapshotIds: [],
    claims: [], findings: [], evidenceLocators: {},
  };
}

function coverage(expected: string[], sources: PipelineSource[]) {
  const presentConnectionIds = [...new Set(sources.map((item) => item.connectionId))].sort();
  const missingConnectionIds = expected.filter((item) => !presentConnectionIds.includes(item));
  return {
    complete: missingConnectionIds.length === 0 && presentConnectionIds.length === expected.length,
    expectedConnectionIds: [...expected], presentConnectionIds, missingConnectionIds,
    message: missingConnectionIds.length
      ? `当前证据覆盖不足：缺少 ${missingConnectionIds.length} 个来源（${missingConnectionIds.join('、')}）。`
      : '当前来源组合覆盖完整，可以生成预置候选模型。',
  };
}

export function createModelingPipelineRuntime(options: ModelingPipelineRuntimeOptions): ModelingPipelineRuntime {
  const now = options.now ?? (() => new Date().toISOString());
  const storageKey = (scope: ModelingPipelineScope) => `linguan:modeling-pipeline:v1:${scopeKey(scope)}`;
  const load = (scope: ModelingPipelineScope): StoredPipeline => {
    const raw = options.storage.getItem(storageKey(scope));
    if (!raw) return initial(scope);
    try {
      const value = JSON.parse(raw) as StoredPipeline;
      return value.schemaVersion === 1 && scopeKey(value.scope) === scopeKey(scope) ? value : initial(scope);
    } catch { return initial(scope); }
  };
  const save = (value: StoredPipeline) => options.storage.setItem(storageKey(value.scope), JSON.stringify(value));

  async function hydrate(value: StoredPipeline): Promise<ModelingPipelineSnapshot> {
    const managed = await options.sourceRuntime.read(value.scope.workspaceId);
    const sources = value.attachedSnapshotIds.map((snapshotId) => {
      const snapshot = managed.snapshots.find((item) => item.snapshotId === snapshotId);
      if (!snapshot) return null;
      const connection = managed.connections.find((item) => item.connectionId === snapshot.connectionId);
      if (!connection) return null;
      const claim = managed.claims.find((item) => item.snapshotId === snapshot.snapshotId);
      return {
        ...clone(snapshot), displayName: connection.displayName, connectorTypeId: connection.connectorTypeId,
        authority: claim?.authority ?? 'CORROBORATING',
      } satisfies PipelineSource;
    }).filter((item): item is PipelineSource => Boolean(item));
    return { ...clone(value), sources, coverage: coverage(options.adapter.expectedConnectionIds, sources) };
  }

  function requireScope(scope: ModelingPipelineScope) {
    if (scope.workspaceId !== options.adapter.workspaceId || scope.projectId !== options.adapter.projectId) {
      throw new Error('建模管线不属于当前项目');
    }
  }
  function requireEditor(command: ModelingPipelineCommand) {
    if (!['EDITOR', 'ADMIN'].includes(command.actor.role)) throw new Error('当前角色不能修改建模草稿');
  }

  return {
    async read(scope) {
      requireScope(scope);
      const state = load(scope);
      if (!options.storage.getItem(storageKey(scope))) save(state);
      return hydrate(state);
    },
    async reset(scope) {
      requireScope(scope);
      const state = initial(scope);
      save(state);
      return hydrate(state);
    },
    async execute(command) {
      requireScope(command);
      requireEditor(command);
      const state = load(command);
      if (state.revision !== command.expectedRevision) throw new Error('建模状态已更新，请重新加载');
      const managed = await options.sourceRuntime.read(command.workspaceId);

      if (command.type === 'ATTACH_SOURCE_SNAPSHOTS') {
        if (state.state !== 'COLLECTING') throw new Error('证据批次已冻结，不能继续添加来源');
        const unique = [...new Set(command.snapshotIds)];
        unique.forEach((snapshotId) => {
          const snapshot = managed.snapshots.find((item) => item.snapshotId === snapshotId);
          if (!snapshot || !['READY', 'PARTIAL'].includes(snapshot.status)) throw new Error(`来源快照不可用：${snapshotId}`);
        });
        state.attachedSnapshotIds = [...new Set([...state.attachedSnapshotIds, ...unique])];
      } else if (command.type === 'DETACH_SOURCE_SNAPSHOT') {
        if (state.state !== 'COLLECTING') throw new Error('证据批次已冻结，不能移除来源');
        state.attachedSnapshotIds = state.attachedSnapshotIds.filter((item) => item !== command.snapshotId);
      } else if (command.type === 'FREEZE_EVIDENCE_BATCH') {
        if (state.state !== 'COLLECTING' || state.attachedSnapshotIds.length === 0) throw new Error('请先加入至少一个证据快照');
        const snapshots = state.attachedSnapshotIds.map((id) => managed.snapshots.find((item) => item.snapshotId === id)!);
        const revision = Math.max(...snapshots.map((item) => item.connectionRevision));
        const snapshotIds = [...state.attachedSnapshotIds].sort();
        state.batch = {
          batchId: `${state.scope.projectId}-evidence-r${revision}-${sha256HexSync(snapshotIds.join('\n')).slice(0, 8)}`,
          revision, snapshotIds, fingerprint: sha256HexSync(snapshots.map((item) => item.fingerprint).sort().join('\n')),
          frozenAt: now(),
        };
        state.state = 'FROZEN';
      } else if (command.type === 'RECONCILE_EVIDENCE') {
        if (state.state !== 'FROZEN' || !state.batch) throw new Error('请先冻结证据批次');
        const hydrated = await hydrate(state);
        const input = { scope: state.scope, sourceWorkspace: managed, sources: hydrated.sources };
        const normalized = options.adapter.normalize(input);
        state.claims = normalized.claims;
        state.evidenceLocators = normalized.locators;
        state.findings = options.adapter.reconcile({ ...input, claims: state.claims });
        state.state = state.findings.some((item) => item.severity === 'BLOCKER') ? 'BLOCKED' : 'RECONCILED';
      } else if (command.type === 'RESOLVE_FINDING') {
        const finding = state.findings.find((item) => item.findingId === command.findingId);
        if (!finding) throw new Error('交叉验证结论不存在');
        if (!finding.options.some((item) => item.id === command.resolutionId)) throw new Error('处理方案不存在');
        if (!chinese.test(command.reason)) throw new Error('请填写中文处理理由');
        finding.decision = {
          resolutionId: command.resolutionId, reviewer: command.actor.userId,
          reason: command.reason.trim(), decidedAt: now(),
        };
        state.state = state.findings.some((item) => item.severity === 'BLOCKER' && !item.decision) ? 'BLOCKED' : 'RECONCILED';
        if (state.candidate) state.candidate.status = state.state === 'BLOCKED' ? 'DRAFT' : 'READY';
      } else if (command.type === 'GENERATE_CANDIDATE') {
        if (!state.batch || !['RECONCILED', 'BLOCKED'].includes(state.state)) throw new Error('请先完成来源交叉验证');
        const hydrated = await hydrate(state);
        const candidate = options.adapter.generate({ scope: state.scope, sourceWorkspace: managed, sources: hydrated.sources, claims: state.claims, findings: state.findings });
        if (!candidate) throw new Error('当前快照组合尚无预生成候选模型');
        state.candidate = clone(candidate);
        state.candidate.status = state.findings.some((item) => item.severity === 'BLOCKER' && !item.decision) ? 'DRAFT' : 'READY';
        state.state = state.candidate.status === 'READY' ? 'CANDIDATE_READY' : 'BLOCKED';
      } else if (command.type === 'APPLY_CANDIDATE_TO_DRAFT') {
        if (!state.batch || !state.candidate) throw new Error('请先生成候选模型');
        const blockers = state.findings.filter((item) => item.severity === 'BLOCKER' && !item.decision);
        if (blockers.length) throw new Error(`仍有 ${blockers.length} 项阻断，不能应用到草稿`);
        const hydrated = await hydrate(state);
        state.materialized = options.adapter.materialize({
          scope: state.scope, sourceWorkspace: managed, sources: hydrated.sources, candidate: state.candidate,
          findings: state.findings, batch: state.batch,
        });
        state.state = 'APPLIED_TO_DRAFT';
      }
      state.revision += 1;
      save(state);
      return { snapshot: await hydrate(state) };
    },
  };
}
