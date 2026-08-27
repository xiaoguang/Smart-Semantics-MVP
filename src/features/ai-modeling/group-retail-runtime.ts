import type { ReviewDecision, StoredResult, WorkbenchCommand, WorkbenchRuntime, WorkspaceSnapshot } from './runtime-types.ts';
import type { ModelingBatch } from './source-bundle.ts';
import { computeSourceBundleFingerprint } from './source-bundle.ts';
import {
  createGroupRetailFindings, groupRetailConnectorCatalog, groupRetailEvidenceClaims,
  groupRetailEvidenceLocators, groupRetailScenarioFixture, groupRetailSourceAssets,
} from './group-retail-fixture.ts';
import type { StorageLike } from './runtime.ts';
import { WorkbenchError } from './runtime.ts';

type StoredState = {
  schemaVersion: 1;
  systemCode: 'group_retail_ops';
  lockVersion: number;
  batch: ModelingBatch;
  batchHistory: ModelingBatch[];
  result: StoredResult | null;
  catalog: WorkspaceSnapshot['catalogs'][number] | null;
};

const clone = <T>(value: T): T => structuredClone(value);
const chinese = /[\u3400-\u9fff]/;
const document = groupRetailScenarioFixture.documents[0];

function initial(): StoredState {
  return {
    schemaVersion: 1, systemCode: 'group_retail_ops', lockVersion: 0,
    batch: { batchId: 'group-retail-batch-1-r1', systemCode: 'group_retail_ops', revision: 1, state: 'COLLECTING', sourceIds: [], snapshotIds: [], fingerprint: '', findings: [] },
    batchHistory: [], result: null, catalog: null,
  };
}

function hydrate(state: StoredState): WorkspaceSnapshot {
  const decisions = state.result ? Object.values(state.result.decisions) : [];
  const selectedSourceIds = new Set(state.batch.sourceIds);
  const selectedEvidenceIds = new Set(groupRetailSourceAssets
    .filter((source) => selectedSourceIds.has(source.sourceId))
    .flatMap((source) => source.evidenceIds));
  const results = state.result ? [{
    ...clone(state.result), fixture: document, totalItemCount: 132, decidedItemCount: decisions.length,
    autoApprovedItemCount: decisions.filter((item) => item.source === 'AUTO' && item.decision === 'APPROVED').length,
    userDecidedItemCount: decisions.filter((item) => item.source === 'USER').length,
    pendingItemCount: 132 - decisions.length, allItemsDecided: decisions.length === 132,
  }] : [];
  return {
    schemaVersion: 4, systemCode: state.systemCode, lockVersion: state.lockVersion, uploads: [], unknownUploads: [], results,
    catalogs: state.catalog ? [clone(state.catalog)] : [], currentCatalogVersion: state.catalog?.modelVersion ?? null,
    fixture: groupRetailScenarioFixture, nextExpectedVersion: null, recoveryRequired: false, recoveryMessage: null,
    sourceWorkspace: {
      availableSources: clone(groupRetailSourceAssets),
      selectedSources: groupRetailSourceAssets.filter((item) => state.batch.sourceIds.includes(item.sourceId)).map(clone),
      evidenceLocators: clone(Object.fromEntries(Object.entries(groupRetailEvidenceLocators)
        .filter(([evidenceId]) => selectedEvidenceIds.has(evidenceId)))),
      connectorCatalog: clone(groupRetailConnectorCatalog),
      evidenceClaims: clone(groupRetailEvidenceClaims.filter((claim) => selectedSourceIds.has(claim.sourceId))),
      batch: clone(state.batch), history: clone(state.batchHistory),
    },
  };
}

function requireReason(reviewer: string, reason: string) {
  if (!reviewer.trim()) throw new WorkbenchError('REVIEWER_REQUIRED', '缺少当前用户');
  if (!chinese.test(reason)) throw new WorkbenchError('CHINESE_REASON_REQUIRED', '请填写中文理由');
}

function automaticDecisions(timestamp: string) {
  return Object.fromEntries(document.review.items.filter((item) => item.reviewClass === 'AUTO').map((item) => [item.id, {
    itemId: item.id, decision: 'APPROVED' as const, source: 'AUTO' as const, reviewer: 'AI建模助手',
    reason: `置信度 ${Math.round((item.confidence ?? 0.97) * 100)}%，并由原始来源或可追溯互证支持`,
    confidence: item.confidence ?? 0.97, decidedAt: timestamp,
  } satisfies ReviewDecision]));
}

export function createGroupRetailWorkbenchRuntime(options: { storage: StorageLike; now?: () => string }): WorkbenchRuntime {
  const storageKey = 'linguan:ai-modeling:group_retail_ops:sources:v1';
  const now = options.now ?? (() => new Date().toISOString());
  const load = () => {
    try {
      const raw = options.storage.getItem(storageKey);
      if (!raw) return initial();
      const parsed = JSON.parse(raw) as StoredState;
      if (parsed.schemaVersion !== 1 || parsed.systemCode !== 'group_retail_ops') return initial();
      parsed.batchHistory ??= [];
      return parsed;
    } catch { return initial(); }
  };
  const save = (state: StoredState) => options.storage.setItem(storageKey, JSON.stringify(state));
  const requireSystem = (systemCode: string) => {
    if (systemCode !== 'group_retail_ops') throw new WorkbenchError('SYSTEM_NOT_FOUND', '模型空间不存在');
  };
  return {
    async read(systemCode) { requireSystem(systemCode); const state = load(); if (!options.storage.getItem(storageKey)) save(state); return hydrate(state); },
    async reset(systemCode) { requireSystem(systemCode); const state = initial(); save(state); return hydrate(state); },
    async execute(command) {
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
    const invalid = command.sourceIds.find((id) => !groupRetailSourceAssets.some((item) => item.sourceId === id));
    if (invalid) throw new WorkbenchError('SOURCE_NOT_FOUND', `来源不存在：${invalid}`);
    const additions = [...new Set(command.sourceIds)].filter((id) => !state.batch.sourceIds.includes(id));
    if (!additions.length) return;
    if (state.batch.state !== 'COLLECTING') {
      state.batchHistory.push(clone(state.batch));
      state.batch = { ...state.batch, batchId: `group-retail-batch-1-r${state.batch.revision + 1}`, revision: state.batch.revision + 1, state: 'COLLECTING', sourceIds: [...state.batch.sourceIds], fingerprint: '', findings: [] };
      state.result = null;
    }
    state.batch.sourceIds = [...state.batch.sourceIds, ...additions];
    return;
  }
  if (command.type === 'REMOVE_SOURCE') {
    if (state.batch.state !== 'COLLECTING') throw new WorkbenchError('BATCH_FROZEN', '资料批次已冻结，不能移除来源');
    state.batch.sourceIds = state.batch.sourceIds.filter((id) => id !== command.sourceId);
    return;
  }
  if (command.type === 'ATTACH_SOURCE_SNAPSHOTS') {
    const additions = [...new Set(command.snapshotIds.filter(Boolean))].filter((id) => !state.batch.snapshotIds!.includes(id));
    if (!additions.length) return;
    if (state.batch.state !== 'COLLECTING') {
      state.batchHistory.push(clone(state.batch));
      state.batch = { ...state.batch, batchId: `group-retail-batch-1-r${state.batch.revision + 1}`, revision: state.batch.revision + 1, state: 'COLLECTING', snapshotIds: [...state.batch.snapshotIds!], fingerprint: '', findings: [] };
      state.result = null;
    }
    state.batch.snapshotIds = [...state.batch.snapshotIds!, ...additions];
    return;
  }
  if (command.type === 'DETACH_SOURCE_SNAPSHOT') {
    if (state.batch.state !== 'COLLECTING') throw new WorkbenchError('BATCH_FROZEN', '资料批次已冻结，不能移除快照');
    state.batch.snapshotIds = state.batch.snapshotIds.filter((id) => id !== command.snapshotId);
    return;
  }
  if (command.type === 'START_MODELING') {
    if (!state.batch.sourceIds.length && !state.batch.snapshotIds.length) throw new WorkbenchError('SOURCE_COMBINATION_NO_RESULT', '请先加入至少一个已就绪证据快照');
    const legacyFingerprints = state.batch.sourceIds.map((id) => groupRetailSourceAssets.find((item) => item.sourceId === id)!.fingerprint);
    state.batch.fingerprint = computeSourceBundleFingerprint([...legacyFingerprints, ...state.batch.snapshotIds]);
    const selectedSources = new Set(state.batch.sourceIds);
    state.batch.findings = createGroupRetailFindings()
      .filter((finding) => finding.sourceIds.every((sourceId) => selectedSources.has(sourceId)));
    state.batch.state = 'AWAITING_DECISION';
    return;
  }
  if (command.type === 'RESOLVE_FINDING') {
    requireReason(command.reviewer, command.reason);
    const finding = state.batch.findings.find((item) => item.findingId === command.findingId);
    if (!finding) throw new WorkbenchError('FINDING_NOT_FOUND', '互证结论不存在');
    if (!finding.options.some((item) => item.id === command.resolutionId)) throw new WorkbenchError('RESOLUTION_NOT_FOUND', '处理方案不存在');
    finding.decision = { resolutionId: command.resolutionId, reviewer: command.reviewer.trim(), reason: command.reason.trim(), decidedAt: timestamp };
    state.batch.state = command.resolutionId === 'keep_blocked' ? 'BLOCKED' : 'AWAITING_DECISION';
    return;
  }
  if (command.type === 'GENERATE_MODEL') {
    const unresolved = state.batch.findings.filter((item) => item.severity === 'BLOCKER' && (!item.decision || item.decision.resolutionId === 'keep_blocked'));
    if (unresolved.length) throw new WorkbenchError('FINDINGS_BLOCKED', `请先处理 ${unresolved.length} 项来源冲突`);
    if (state.result) throw new WorkbenchError('MODEL_ALREADY_GENERATED', '模型已经生成');
    state.result = { documentVersion: 'v1', status: 'GENERATED', generatedAt: timestamp, decisions: automaticDecisions(timestamp), confirmedGroups: ['auto'], checks: [], publishedAt: null };
    state.batch.state = 'MODEL_READY';
    return;
  }
  if (command.type === 'DECIDE_REVIEW_ITEM' || command.type === 'REJECT_ITEM') {
    if (!state.result) throw new WorkbenchError('MODEL_NOT_GENERATED', '请先生成模型');
    if (state.result.status === 'PUBLISHED') throw new WorkbenchError('PUBLISHED_IMMUTABLE', '已发布模型不可修改');
    requireReason(command.reviewer, command.reason);
    if (!document.review.items.some((item) => item.id === command.itemId)) throw new WorkbenchError('ITEM_NOT_FOUND', '审核项不存在');
    state.result.decisions[command.itemId] = {
      itemId: command.itemId, decision: command.type === 'REJECT_ITEM' ? 'REJECTED' : command.decision,
      source: 'USER', reviewer: command.reviewer.trim(), reason: command.reason.trim(), decidedAt: timestamp,
    };
    return;
  }
  if (command.type === 'CONFIRM_BUSINESS_GROUP') {
    if (!state.result) throw new WorkbenchError('MODEL_NOT_GENERATED', '请先生成模型');
    requireReason(command.reviewer, command.reason);
    const group = document.review.groups.find((item) => item.id === command.groupId);
    if (!group) throw new WorkbenchError('GROUP_NOT_FOUND', '审核分组不存在');
    group.itemIds.forEach((itemId) => {
      if (!state.result!.decisions[itemId]) state.result!.decisions[itemId] = { itemId, decision: 'APPROVED', source: 'USER', reviewer: command.reviewer.trim(), reason: command.reason.trim(), decidedAt: timestamp };
    });
    if (!state.result.confirmedGroups.includes(group.id)) state.result.confirmedGroups.push(group.id);
    return;
  }
  if (command.type !== 'PUBLISH_MODEL') throw new WorkbenchError('COMMAND_NOT_SUPPORTED', '当前场景不支持该操作');
  if (!state.result) throw new WorkbenchError('MODEL_NOT_GENERATED', '请先生成模型');
  requireReason(command.reviewer, command.reason);
  if (Object.keys(state.result.decisions).length !== 132) throw new WorkbenchError('REVIEW_INCOMPLETE', '请先确认全部人工审核项');
  state.result.status = 'PUBLISHED'; state.result.publishedAt = timestamp;
  state.result.checks = [
    { id: 'RELATION_ENDPOINTS', label: '表之间可以正确关联', passed: true, detail: '15条关系均能定位到两端表和字段' },
    { id: 'FIELD_UNIQUENESS', label: '字段定义没有冲突', passed: true, detail: '12张表的84个字段编码唯一' },
    { id: 'METRIC_DEPENDENCIES', label: '指标具备计算条件', passed: true, detail: '12个指标依赖字段完整' },
    { id: 'SOURCE_EVIDENCE', label: '结果可以追溯设计资料', passed: true, detail: '全部模型对象保留原始来源、派生血缘和互证记录' },
  ];
  state.catalog = { id: 'catalog-group-retail-v1', modelVersion: 'V1', documentVersion: 'v1', publishedAt: timestamp, publishedBy: command.reviewer.trim(), reason: command.reason.trim(), immutable: true };
  state.batch.state = 'PUBLISHED';
}
