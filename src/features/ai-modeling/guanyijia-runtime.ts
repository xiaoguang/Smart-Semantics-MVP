import {
  createGuanyijiaFindings, guanyijiaCandidateModel, guanyijiaEvidenceLocators,
  guanyijiaScenarioFixture, guanyijiaSourceAssets,
} from './guanyijia-fixture.ts';
import { computeSourceBundleFingerprint, type ModelingBatch } from './source-bundle.ts';
import type { CandidateModelSnapshot } from './candidate-model.ts';
import type { WorkbenchCommand, WorkbenchRuntime, WorkspaceSnapshot } from './runtime-types.ts';
import type { StorageLike } from './runtime.ts';
import { WorkbenchError } from './runtime.ts';

type StoredState = {
  schemaVersion: 2;
  systemCode: 'guanyijia_erp';
  lockVersion: number;
  batch: ModelingBatch;
  candidateModel: CandidateModelSnapshot | null;
};

type LegacyState = {
  schemaVersion: 1;
  systemCode: 'guanyijia_erp';
  lockVersion: number;
  selectedSourceIds: string[];
};

const storageKey = 'linguan:ai-modeling:guanyijia_erp:sources:v2';
const legacyStorageKey = 'linguan:ai-modeling:guanyijia_erp:evidence:v1';
const clone = <T>(value: T): T => structuredClone(value);
const chinese = /[\u3400-\u9fff]/;

function initial(selectedSourceIds: string[] = [], lockVersion = 0): StoredState {
  const known = selectedSourceIds.filter((id) => guanyijiaSourceAssets.some((source) => source.sourceId === id));
  return {
    schemaVersion: 2,
    systemCode: 'guanyijia_erp',
    lockVersion,
    batch: {
      batchId: 'guanyijia-evidence-v1-r1', systemCode: 'guanyijia_erp', revision: 1,
      state: known.length ? 'COLLECTING' : 'COLLECTING', sourceIds: [...new Set(known)], snapshotIds: [], fingerprint: '', findings: [],
    },
    candidateModel: null,
  };
}

function hydrate(state: StoredState): WorkspaceSnapshot {
  return {
    schemaVersion: 4, systemCode: state.systemCode, lockVersion: state.lockVersion,
    uploads: [], unknownUploads: [], results: [], catalogs: [], currentCatalogVersion: null,
    fixture: guanyijiaScenarioFixture, nextExpectedVersion: null, recoveryRequired: false, recoveryMessage: null,
    sourceWorkspace: {
      availableSources: clone(guanyijiaSourceAssets),
      selectedSources: guanyijiaSourceAssets.filter((source) => state.batch.sourceIds.includes(source.sourceId)).map(clone),
      evidenceLocators: clone(guanyijiaEvidenceLocators),
      batch: clone(state.batch), candidateModel: state.candidateModel ? clone(state.candidateModel) : undefined, history: [],
    },
  };
}

function requireReason(reviewer: string, reason: string) {
  if (!reviewer.trim()) throw new WorkbenchError('REVIEWER_REQUIRED', '缺少当前用户');
  if (!chinese.test(reason)) throw new WorkbenchError('CHINESE_REASON_REQUIRED', '请填写中文理由');
}

export function createGuanyijiaEvidenceRuntime(options: { storage: StorageLike; now?: () => string }): WorkbenchRuntime {
  const now = options.now ?? (() => new Date().toISOString());
  const load = (): StoredState => {
    try {
      const raw = options.storage.getItem(storageKey);
      if (raw) {
        const parsed = JSON.parse(raw) as StoredState;
        if (parsed.schemaVersion === 2 && parsed.systemCode === 'guanyijia_erp') return parsed;
      }
      const legacyRaw = options.storage.getItem(legacyStorageKey);
      if (legacyRaw) {
        const legacy = JSON.parse(legacyRaw) as LegacyState;
        if (legacy.schemaVersion === 1 && legacy.systemCode === 'guanyijia_erp') {
          const migrated = initial(legacy.selectedSourceIds, legacy.lockVersion);
          options.storage.setItem(storageKey, JSON.stringify(migrated));
          return migrated;
        }
      }
    } catch { /* preserve legacy value and start a safe new state */ }
    return initial();
  };
  const save = (state: StoredState) => options.storage.setItem(storageKey, JSON.stringify(state));
  const requireSystem = (systemCode: string) => {
    if (systemCode !== 'guanyijia_erp') throw new WorkbenchError('SYSTEM_NOT_FOUND', '模型空间不存在');
  };
  return {
    async read(systemCode) {
      requireSystem(systemCode); const state = load();
      if (!options.storage.getItem(storageKey)) save(state);
      return hydrate(state);
    },
    async reset(systemCode) { requireSystem(systemCode); const state = initial(); save(state); return hydrate(state); },
    async execute(command: WorkbenchCommand) {
      requireSystem(command.systemCode);
      const state = load();
      if (state.lockVersion !== command.expectedRevision) throw new WorkbenchError('REVISION_CONFLICT', '工作区已更新，请重新加载');
      apply(state, command, now());
      state.lockVersion += 1; save(state); return { snapshot: hydrate(state) };
    },
  };
}

function apply(state: StoredState, command: WorkbenchCommand, timestamp: string) {
  state.batch.snapshotIds ??= [];
  if (command.type === 'ADD_SOURCES') {
    const invalid = command.sourceIds.find((id) => !guanyijiaSourceAssets.some((source) => source.sourceId === id));
    if (invalid) throw new WorkbenchError('SOURCE_NOT_FOUND', `来源不存在：${invalid}`);
    if (state.batch.state !== 'COLLECTING') throw new WorkbenchError('BATCH_FROZEN', '证据批次已冻结，不能继续添加来源');
    state.batch.sourceIds = [...new Set([...state.batch.sourceIds, ...command.sourceIds])];
    return;
  }
  if (command.type === 'REMOVE_SOURCE') {
    if (state.batch.state !== 'COLLECTING') throw new WorkbenchError('BATCH_FROZEN', '证据批次已冻结，不能移除来源');
    state.batch.sourceIds = state.batch.sourceIds.filter((id) => id !== command.sourceId);
    return;
  }
  if (command.type === 'ATTACH_SOURCE_SNAPSHOTS') {
    if (state.batch.state !== 'COLLECTING') throw new WorkbenchError('BATCH_FROZEN', '证据批次已冻结，不能继续添加来源');
    state.batch.snapshotIds = [...new Set([...state.batch.snapshotIds, ...command.snapshotIds.filter(Boolean)])];
    return;
  }
  if (command.type === 'DETACH_SOURCE_SNAPSHOT') {
    if (state.batch.state !== 'COLLECTING') throw new WorkbenchError('BATCH_FROZEN', '证据批次已冻结，不能移除来源');
    state.batch.snapshotIds = state.batch.snapshotIds.filter((id) => id !== command.snapshotId);
    return;
  }
  if (command.type === 'START_MODELING') {
    if (state.batch.sourceIds.length !== guanyijiaSourceAssets.length) throw new WorkbenchError('SOURCE_COMBINATION_NO_RESULT', '请先接入数据库和固定 GitHub 源码证据');
    state.batch.fingerprint = computeSourceBundleFingerprint(state.batch.sourceIds.map((id) => guanyijiaSourceAssets.find((source) => source.sourceId === id)!.fingerprint));
    state.batch.findings = createGuanyijiaFindings();
    state.batch.state = 'AWAITING_DECISION';
    return;
  }
  if (command.type === 'RESOLVE_FINDING') {
    requireReason(command.reviewer, command.reason);
    const finding = state.batch.findings.find((item) => item.findingId === command.findingId);
    if (!finding) throw new WorkbenchError('FINDING_NOT_FOUND', '互证结论不存在');
    if (!finding.options.some((option) => option.id === command.resolutionId)) throw new WorkbenchError('RESOLUTION_NOT_FOUND', '处理方案不存在');
    finding.decision = { resolutionId: command.resolutionId, reviewer: command.reviewer.trim(), reason: command.reason.trim(), decidedAt: timestamp };
    if (state.candidateModel) state.candidateModel.status = command.resolutionId === 'keep_blocked' ? 'DRAFT' : 'READY';
    state.batch.state = command.resolutionId === 'keep_blocked' ? 'BLOCKED' : state.candidateModel ? 'MODEL_READY' : 'AWAITING_DECISION';
    return;
  }
  if (command.type === 'GENERATE_MODEL') {
    if (state.batch.state !== 'AWAITING_DECISION' && state.batch.state !== 'BLOCKED') throw new WorkbenchError('SOURCES_NOT_RECONCILED', '请先完成双来源对齐');
    if (state.candidateModel) throw new WorkbenchError('MODEL_ALREADY_GENERATED', '候选模型已经生成');
    const unresolved = state.batch.findings.some((finding) => finding.severity === 'BLOCKER'
      && (!finding.decision || finding.decision.resolutionId === 'keep_blocked'));
    state.candidateModel = clone(guanyijiaCandidateModel);
    state.candidateModel.generatedAt = timestamp;
    state.candidateModel.status = unresolved ? 'DRAFT' : 'READY';
    state.batch.state = unresolved ? 'BLOCKED' : 'MODEL_READY';
    return;
  }
  throw new WorkbenchError('CANDIDATE_ONLY', '候选模型暂不支持审核或发布');
}
