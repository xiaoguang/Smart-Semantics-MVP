export const sourceRoleOrder = [
  'DATABASE',
  'CODE_REPOSITORY',
  'OFFICIAL_DOCUMENTS',
  'ERP_POLICY',
  'TERMINOLOGY',
] as const;

/** Stores only the pre-run role-to-instance selection, never source content. */
export const guanyijiaSourceBoardSelectionStorageKey = 'linguan:guanyijia-source-board:v1';

export type SourceRole = typeof sourceRoleOrder[number];
export type SourceAvailability = 'CONNECTED' | 'NEEDS_CONFIGURATION' | 'CONNECTION_FAILED' | 'DISCONNECTED';
export type SnapshotReadiness = 'READY' | 'MISSING';
export type SourceReadState = 'NONE' | 'PENDING' | 'READING' | 'DOCUMENT_READY' | 'REVIEWED' | 'CONFLICT_BLOCKED' | 'ALIGNED';
export type SourceBoardStatusTag = {
  kind: 'AVAILABILITY' | 'READ_STATE';
  tone: 'SUCCESS' | 'INFO' | 'WARNING' | 'NEUTRAL' | 'DANGER';
  label: string;
};

export type SourceCandidateInstance = {
  instanceId: string;
  sourceId: string;
  sourceRole: SourceRole;
  displayName: string;
  /** Human-readable instance metadata. `displayName` stays for stored bindings. */
  businessName?: string;
  connectionId?: string;
  adapterTypeId?: string;
  adapterLabel?: string;
  environmentLabel?: string;
  locationPreview?: string;
  scopePreview?: string;
  availability: SourceAvailability;
  snapshotReadiness?: SnapshotReadiness;
  compatibleStoryKeys: string[];
  connectorTypeId?: string;
  formalSnapshotId?: string;
  contentSnapshotId?: string;
  selectedRole?: SourceRole;
  incompatibilityReason?: string;
};

export type SourceBoardSlot = {
  role: SourceRole;
  displayName: string;
  instance?: SourceCandidateInstance;
  availability: SourceAvailability;
  readState: SourceReadState;
  statusTags: SourceBoardStatusTag[];
  canRemove: boolean;
  locked: boolean;
};

export type SourceBoardModel = {
  storyKey: string;
  locked: boolean;
  /** Why editing the pre-run board is unavailable. Never carries source content. */
  lockReason?: 'RUN_ACTIVE' | 'SOURCE_MANAGEMENT_UNAVAILABLE' | 'RUN_STATE_UNAVAILABLE';
  slots: SourceBoardSlot[];
  candidates: SourceCandidateInstance[];
};

export type SourceBoardInput = {
  storyKey: string;
  locked: boolean;
  lockReason?: SourceBoardModel['lockReason'];
  candidates: SourceCandidateInstance[];
  bindings?: Partial<Record<SourceRole, string>>;
  sourceStatuses?: Array<{ sourceId: string; status: string }>;
};

export type SourceBoardCommand =
  | { type: 'BIND_INSTANCE'; role: SourceRole; instanceId: string }
  | { type: 'UNBIND_INSTANCE'; role: SourceRole };

export type SourceBoardCommandResult =
  | { ok: true; model: SourceBoardModel }
  | { ok: false; model: SourceBoardModel; reason: string };

export type SourceBoardStartValidation =
  | { ok: true }
  | { ok: false; reason: string };

const roleLabels: Record<SourceRole, string> = {
  DATABASE: '数据库',
  CODE_REPOSITORY: '代码仓库',
  OFFICIAL_DOCUMENTS: '官方业务文档',
  ERP_POLICY: 'ERP 管理制度',
  TERMINOLOGY: '术语图',
};

function clone<T>(value: T): T {
  return structuredClone(value);
}

function readStateFor(sourceId: string, statuses: SourceBoardInput['sourceStatuses']): SourceReadState {
  const status = statuses?.find((item) => item.sourceId === sourceId)?.status;
  if (status === 'READING') return 'READING';
  if (status === 'DOCUMENT_READY') return 'DOCUMENT_READY';
  if (status === 'REVIEWED') return 'REVIEWED';
  if (status === 'CONFLICT_BLOCKED') return 'CONFLICT_BLOCKED';
  if (status === 'ALIGNED') return 'ALIGNED';
  return status === 'PENDING' ? 'PENDING' : 'NONE';
}

function tagsFor(availability: SourceAvailability, readState: SourceReadState): SourceBoardStatusTag[] {
  if (availability === 'NEEDS_CONFIGURATION') return [{ kind: 'AVAILABILITY', tone: 'WARNING', label: '待配置' }];
  if (availability === 'CONNECTION_FAILED') return [{ kind: 'AVAILABILITY', tone: 'DANGER', label: '连接失败' }];
  if (availability === 'DISCONNECTED') return [{ kind: 'AVAILABILITY', tone: 'NEUTRAL', label: '未连接' }];
  const readTag: Partial<Record<SourceReadState, SourceBoardStatusTag>> = {
    PENDING: { kind: 'READ_STATE', tone: 'INFO', label: '待读取' },
    READING: { kind: 'READ_STATE', tone: 'INFO', label: '读取中' },
    DOCUMENT_READY: { kind: 'READ_STATE', tone: 'INFO', label: '待审阅' },
    REVIEWED: { kind: 'READ_STATE', tone: 'SUCCESS', label: '已审阅' },
    ALIGNED: { kind: 'READ_STATE', tone: 'SUCCESS', label: '已审阅' },
    CONFLICT_BLOCKED: { kind: 'READ_STATE', tone: 'DANGER', label: '存在差异' },
  };
  return readTag[readState] ? [readTag[readState]] : [];
}

function normalizeCandidates(input: SourceBoardInput): SourceCandidateInstance[] {
  const selected = new Map<SourceRole, string>(Object.entries(input.bindings ?? {}) as Array<[SourceRole, string]>);
  return input.candidates.map((candidate) => {
    const selectedRole = [...selected.entries()].find(([, instanceId]) => instanceId === candidate.instanceId)?.[0];
    const compatible = candidate.compatibleStoryKeys.includes(input.storyKey);
    return {
      ...clone(candidate),
      ...(selectedRole ? { selectedRole } : {}),
      ...(compatible ? {} : { incompatibilityReason: '不适用于当前标准化流程' }),
    };
  });
}

function project(input: SourceBoardInput): SourceBoardModel {
  const candidates = normalizeCandidates(input);
  const bindings = input.bindings ?? {};
  const slots = sourceRoleOrder.map((role) => {
    const instance = candidates.find((candidate) => candidate.instanceId === bindings[role]);
    const availability: SourceAvailability = instance?.availability ?? 'DISCONNECTED';
    const projectedReadState = instance ? readStateFor(instance.sourceId, input.sourceStatuses) : 'NONE';
    const readState = instance && projectedReadState === 'NONE' ? 'PENDING' : projectedReadState;
    return {
      role,
      displayName: roleLabels[role],
      ...(instance ? { instance } : {}),
      availability,
      readState,
      statusTags: tagsFor(availability, readState),
      canRemove: Boolean(instance) && !input.locked,
      locked: input.locked,
    } satisfies SourceBoardSlot;
  });
  return {
    storyKey: input.storyKey,
    locked: input.locked,
    ...(input.lockReason ? { lockReason: input.lockReason } : {}),
    slots,
    candidates,
  };
}

export function projectSourceBoard(input: SourceBoardInput): SourceBoardModel {
  return clone(project(input));
}

function toInput(model: SourceBoardModel) {
  return {
    storyKey: model.storyKey,
    locked: model.locked,
    ...(model.lockReason ? { lockReason: model.lockReason } : {}),
    candidates: model.candidates.map(({ selectedRole: _selectedRole, incompatibilityReason: _incompatibilityReason, ...candidate }) => candidate),
    bindings: Object.fromEntries(model.slots.flatMap((slot) => slot.instance ? [[slot.role, slot.instance.instanceId]] : [])) as Partial<Record<SourceRole, string>>,
    sourceStatuses: model.slots.flatMap((slot) => slot.instance && slot.readState !== 'NONE'
      ? [{ sourceId: slot.instance.sourceId, status: slot.readState }]
      : []),
  } satisfies SourceBoardInput;
}

export function executeSourceBoardCommand(input: {
  model: SourceBoardModel;
  command: SourceBoardCommand;
}): SourceBoardCommandResult {
  const command = input.command;
  if (input.model.locked) {
    return {
      ok: false,
      model: clone(input.model),
      reason: input.model.lockReason === 'SOURCE_MANAGEMENT_UNAVAILABLE'
        ? '运行来源暂时无法读取，请恢复来源配置后再修改。'
        : input.model.lockReason === 'RUN_STATE_UNAVAILABLE'
          ? '标准化运行暂时无法读取，请刷新后重试。'
          : '标准化运行已经开始，当前来源选择已锁定。',
    };
  }
  const next = toInput(input.model);
  if (command.type === 'UNBIND_INSTANCE') {
    delete next.bindings[command.role];
    return { ok: true, model: projectSourceBoard(next) };
  }
  const candidate = next.candidates.find((item) => item.instanceId === command.instanceId);
  if (!candidate || candidate.sourceRole !== command.role || !candidate.compatibleStoryKeys.includes(next.storyKey)) {
    return { ok: false, model: clone(input.model), reason: '该实例与当前来源角色或标准化流程不兼容。' };
  }
  if (candidate.availability !== 'CONNECTED') {
    return { ok: false, model: clone(input.model), reason: `“${candidate.businessName ?? candidate.displayName}”尚未连接，完成配置并测试连接后再添加。` };
  }
  const duplicateRole = Object.entries(next.bindings).find(([role, instanceId]) => instanceId === candidate.instanceId && role !== command.role)?.[0] as SourceRole | undefined;
  if (duplicateRole) delete next.bindings[duplicateRole];
  next.bindings[command.role] = candidate.instanceId;
  return { ok: true, model: projectSourceBoard(next) };
}

export function validateSourceBoardStart(model: SourceBoardModel): SourceBoardStartValidation {
  if (model.locked && model.lockReason === 'SOURCE_MANAGEMENT_UNAVAILABLE') {
    return { ok: false, reason: '运行来源暂时无法读取，请恢复来源配置后再开始资料整理。' };
  }
  if (model.locked && model.lockReason === 'RUN_STATE_UNAVAILABLE') {
    return { ok: false, reason: '标准化运行暂时无法读取，请刷新后重试。' };
  }
  for (const slot of model.slots) {
    if (!slot.instance) return { ok: false, reason: `请先为“${slot.displayName}”选择实例。` };
    if (slot.availability !== 'CONNECTED') return { ok: false, reason: `“${slot.displayName}”尚未连接，不能开始资料整理。` };
    if (slot.instance.snapshotReadiness === 'MISSING' || !slot.instance.formalSnapshotId || !slot.instance.contentSnapshotId) {
      return { ok: false, reason: `“${slot.displayName}”缺少可用快照，不能开始资料整理。` };
    }
  }
  return { ok: true };
}

export function sourceBoardBindings(model: SourceBoardModel): Partial<Record<SourceRole, string>> {
  return Object.fromEntries(model.slots.flatMap((slot) => (
    slot.instance ? [[slot.role, slot.instance.instanceId]] : []
  ))) as Partial<Record<SourceRole, string>>;
}
