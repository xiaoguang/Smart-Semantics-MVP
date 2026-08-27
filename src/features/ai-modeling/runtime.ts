import type { FixtureDocument, SemanticFixture } from './types.ts';
import type {
  PublicationCheck, ReviewDecision, StoredResult, WorkbenchCommand, WorkbenchRuntime, WorkspaceSnapshot, WorkspaceState,
} from './runtime-types.ts';
import { sha256Hex } from './sha256.ts';
import { isAutoReviewEligible, projectReviewBatch } from './review-batch.ts';

export type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

export class WorkbenchError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
    this.name = 'WorkbenchError';
  }
}

const versions = ['v1', 'v2', 'v3', 'v4'] as const;
const chineseText = /[\u3400-\u9fff]/;
const clone = <T>(value: T): T => structuredClone(value);

function initialState(fixture: SemanticFixture): WorkspaceState {
  return {
    schemaVersion: 4, systemCode: fixture.system.code, lockVersion: 0,
    uploads: [], unknownUploads: [], results: [], catalogs: [], currentCatalogVersion: null,
  };
}

function requireReview(reviewer: string, reason: string) {
  if (!reviewer.trim()) throw new WorkbenchError('REVIEWER_REQUIRED', '请填写审核人');
  if (!chineseText.test(reason)) throw new WorkbenchError('CHINESE_REASON_REQUIRED', '请填写中文确认理由');
}

function checks(document: FixtureDocument): PublicationCheck[] {
  const model = document.artifacts.model;
  const tables = [...model.entity_tables, ...model.event_tables];
  const tableIds = new Set(tables.map((table) => table.table_id));
  const relationEndpoints = model.relationships.every((relation) => (
    tableIds.has(relation.from_table) && tableIds.has(relation.to_table)
  ));
  const uniqueFields = tables.every((table) => new Set(table.fields.map((field) => field.field_id)).size === table.fields.length);
  const eventFields = new Map(model.event_tables.map((table) => [
    table.table_id, new Set(table.fields.map((field) => field.field_id)),
  ]));
  const metricDependencies = model.metrics.every((metric) => (
    metric.measure_fields.every((field) => eventFields.get(metric.event_table)?.has(field))
  ));
  const evidence = document.artifacts.validation.valid
    && [...tables, ...model.relationships, ...model.metrics].every((item) => item.evidence_ids.length > 0);
  return [
    { id: 'RELATION_ENDPOINTS', label: '关系起点和终点存在', passed: relationEndpoints, detail: relationEndpoints ? `${model.relationships.length} 条关系端点完整` : '存在找不到数据表的关系' },
    { id: 'FIELD_UNIQUENESS', label: '表内字段编码唯一', passed: uniqueFields, detail: uniqueFields ? `${tables.length} 张表字段编码唯一` : '存在重复字段编码' },
    { id: 'METRIC_DEPENDENCIES', label: '指标依赖字段存在', passed: metricDependencies, detail: metricDependencies ? `${model.metrics.length} 个指标依赖完整` : '存在缺失的指标依赖字段' },
    { id: 'SOURCE_EVIDENCE', label: '生成对象具有资料依据', passed: evidence, detail: evidence ? '表、关系和指标均可追溯' : '存在缺少资料依据的生成对象' },
  ];
}

function automaticReviewDecisions(
  fixture: SemanticFixture,
  document: FixtureDocument,
  decidedAt: string,
): Record<string, ReviewDecision> {
  if (!document.qualityGate.publishable) return {};
  const items = projectReviewBatch(fixture, document.documentVersion).groups
    .flatMap((group) => group.items)
    .filter(isAutoReviewEligible);
  return Object.fromEntries(items.map((item) => [item.itemId, {
    itemId: item.itemId,
    decision: 'APPROVED' as const,
    source: 'AUTO' as const,
    reviewer: 'AI建模助手',
    reason: `Java候选置信度${Math.round((item.confidence ?? 0) * 100)}%，具有${item.evidenceCount}处明确资料依据，系统建议采纳`,
    confidence: item.confidence,
    decidedAt,
  }]));
}

function addAutomaticReviews(
  result: StoredResult,
  fixture: SemanticFixture,
  decidedAt: string,
) {
  if (result.status !== 'GENERATED') return;
  const document = fixture.documents.find((item) => item.documentVersion === result.documentVersion);
  if (!document) return;
  const automatic = automaticReviewDecisions(fixture, document, decidedAt);
  for (const [itemId, decision] of Object.entries(automatic)) {
    if (!result.decisions[itemId]) result.decisions[itemId] = decision;
  }
}

function migrateLegacyState(value: unknown, fixture: SemanticFixture): WorkspaceState | null {
  if (!value || typeof value !== 'object') return null;
  const legacy = value as Record<string, unknown>;
  if ((legacy.schemaVersion !== 2 && legacy.schemaVersion !== 3) || legacy.systemCode !== fixture.system.code || !Array.isArray(legacy.results)) return null;
  const state = clone(legacy) as unknown as WorkspaceState;
  state.schemaVersion = 4;
  state.unknownUploads = (state.unknownUploads ?? []).map((item) => ({
    ...item,
    documentCode: item.documentCode ?? `legacy_document_${item.id.replace(/[^a-z0-9_]/gi, '_').toLowerCase()}`,
  }));
  for (const result of state.results) {
    result.decisions = Object.fromEntries(Object.entries(result.decisions).map(([itemId, decision]) => [itemId, {
      ...decision,
      source: decision.source ?? 'USER',
    }]));
    addAutomaticReviews(result, fixture, result.generatedAt);
  }
  return state;
}

function hydrate(state: WorkspaceState, fixture: SemanticFixture, recoveryRequired = false): WorkspaceSnapshot {
  return {
    ...clone(state), fixture,
    results: state.results.map((result) => {
      const document = fixture.documents.find((item) => item.documentVersion === result.documentVersion);
      if (!document) throw new WorkbenchError('INVALID_STATE', '本地结果引用了不存在的资料版本');
      const decisions = Object.values(result.decisions);
      const decidedItemCount = decisions.length;
      const autoApprovedItemCount = decisions.filter((decision) => decision.source === 'AUTO' && decision.decision === 'APPROVED').length;
      const userDecidedItemCount = decisions.filter((decision) => decision.source === 'USER').length;
      const totalItemCount = document.review.items.length;
      return {
        ...clone(result), fixture: document, decidedItemCount, autoApprovedItemCount, userDecidedItemCount,
        pendingItemCount: totalItemCount - decidedItemCount, totalItemCount, allItemsDecided: decidedItemCount === totalItemCount,
      };
    }),
    nextExpectedVersion: fixture.documents[state.uploads.length]?.documentVersion ?? null,
    recoveryRequired,
    recoveryMessage: recoveryRequired ? '流程已更新，请恢复示例资料' : null,
  };
}

export function createSemanticWorkbenchRuntime(options: {
  fixture: SemanticFixture; storage: StorageLike; now?: () => string;
}): WorkbenchRuntime {
  const now = options.now ?? (() => new Date().toISOString());
  const key = (systemCode: string) => `linguan:ai-modeling:${systemCode}:v4`;
  const legacyKeys = (systemCode: string) => [
    `linguan:ai-modeling:${systemCode}:v3`,
    `linguan:ai-modeling:${systemCode}:v2`,
  ];
  const requireSystem = (systemCode: string) => {
    if (systemCode !== options.fixture.system.code) throw new WorkbenchError('SYSTEM_NOT_FOUND', '语义系统不存在');
  };
  const load = (systemCode: string) => {
    requireSystem(systemCode);
    const raw = options.storage.getItem(key(systemCode));
    if (!raw) {
      const legacyRaw = legacyKeys(systemCode).map((legacyKey) => options.storage.getItem(legacyKey)).find(Boolean);
      if (legacyRaw) {
        try {
          const migrated = migrateLegacyState(JSON.parse(legacyRaw), options.fixture);
          if (!migrated) return { state: initialState(options.fixture), recovery: true };
          options.storage.setItem(key(systemCode), JSON.stringify(migrated));
          return { state: migrated, recovery: false };
        } catch {
          return { state: initialState(options.fixture), recovery: true };
        }
      }
      return { state: initialState(options.fixture), recovery: false };
    }
    try {
      const parsed = JSON.parse(raw) as Partial<WorkspaceState>;
      if (parsed.schemaVersion !== 4 || parsed.systemCode !== systemCode) return { state: initialState(options.fixture), recovery: true };
      return { state: parsed as WorkspaceState, recovery: false };
    } catch {
      return { state: initialState(options.fixture), recovery: true };
    }
  };
  const persist = (state: WorkspaceState) => options.storage.setItem(key(state.systemCode), JSON.stringify(state));

  return {
    async read(systemCode) {
      const loaded = load(systemCode);
      if (!loaded.recovery && !options.storage.getItem(key(systemCode))) persist(loaded.state);
      return hydrate(loaded.state, options.fixture, loaded.recovery);
    },
    async execute(command) {
      const loaded = load(command.systemCode);
      if (loaded.recovery) throw new WorkbenchError('RECOVERY_REQUIRED', '流程已更新，请恢复示例资料');
      if (loaded.state.lockVersion !== command.expectedRevision) throw new WorkbenchError('REVISION_CONFLICT', '工作区已被更新，请重新加载后再操作');
      const state = clone(loaded.state);
      await applyCommand(state, command, options.fixture, now());
      state.lockVersion += 1;
      persist(state);
      return { snapshot: hydrate(state, options.fixture) };
    },
    async reset(systemCode) {
      requireSystem(systemCode);
      const state = initialState(options.fixture);
      persist(state);
      return hydrate(state, options.fixture);
    },
  };
}

async function applyCommand(state: WorkspaceState, command: WorkbenchCommand, fixture: SemanticFixture, now: string) {
  const documentFor = (version: string) => {
    const document = fixture.documents.find((item) => item.documentVersion === version);
    if (!document) throw new WorkbenchError('DOCUMENT_NOT_FOUND', '资料版本不存在');
    return document;
  };
  const resultFor = (version: string) => {
    const result = state.results.find((item) => item.documentVersion === version);
    if (!result) throw new WorkbenchError('MODEL_NOT_GENERATED', '请先生成模型');
    return result;
  };

  if (command.type === 'ADD_SOURCES' || command.type === 'REMOVE_SOURCE'
    || command.type === 'ATTACH_SOURCE_SNAPSHOTS' || command.type === 'DETACH_SOURCE_SNAPSHOT'
    || command.type === 'START_MODELING' || command.type === 'RESOLVE_FINDING') {
    throw new WorkbenchError('COMMAND_NOT_SUPPORTED', '当前模型空间不支持多来源命令');
  }

  if (command.type === 'UPLOAD_DOCUMENT') {
    if (!command.fileName.toLowerCase().endsWith('.md')) throw new WorkbenchError('INVALID_EXTENSION', '只支持上传 Markdown（.md）资料');
    if (!command.content.trim()) throw new WorkbenchError('EMPTY_DOCUMENT', '资料内容不能为空');
    const actualSize = new TextEncoder().encode(command.content).byteLength;
    if (actualSize !== command.size || actualSize > 1024 * 1024) throw new WorkbenchError('INVALID_FILE_SIZE', '资料大小必须不超过 1 MiB');
    const actualSha = await sha256Hex(command.content);
    if (actualSha !== command.sha256) throw new WorkbenchError('SHA_MISMATCH', '资料内容与 SHA 不一致');
    const known = fixture.documents.find((item) => item.sha256 === actualSha);
    if (!known) {
      if (!command.documentCode || !/^[a-z][a-z0-9_]{2,63}$/.test(command.documentCode)) {
        throw new WorkbenchError('DOCUMENT_CODE_REQUIRED', '请填写有效的资料编码');
      }
      if (state.unknownUploads.some((item) => item.sha256 === actualSha)) throw new WorkbenchError('DUPLICATE_DOCUMENT', '这份资料已经上传过');
      if (state.unknownUploads.some((item) => item.documentCode === command.documentCode)) throw new WorkbenchError('DUPLICATE_DOCUMENT_CODE', '该资料编码已经存在');
      state.unknownUploads.push({ id: `unknown-${state.unknownUploads.length + 1}`, documentCode: command.documentCode, fileName: command.fileName, content: command.content, size: actualSize, sha256: actualSha, uploadedAt: now, status: 'NO_RESULT' });
      return;
    }
    if (state.uploads.some((item) => item.sha256 === actualSha)) throw new WorkbenchError('DUPLICATE_DOCUMENT', '这份资料已经上传过');
    const expected = versions[state.uploads.length];
    if (known.documentVersion !== expected) throw new WorkbenchError('OUT_OF_ORDER', `请先上传资料 ${expected}`);
    state.uploads.push({ documentVersion: known.documentVersion, documentCode: command.documentCode, fileName: command.fileName, sha256: actualSha, uploadedAt: now });
    return;
  }

  if (command.type === 'GENERATE_MODEL') {
    if (!command.documentVersion) throw new WorkbenchError('DOCUMENT_VERSION_REQUIRED', '请指定资料版本');
    if (!state.uploads.some((item) => item.documentVersion === command.documentVersion)) throw new WorkbenchError('DOCUMENT_NOT_UPLOADED', '请先上传对应资料');
    if (state.results.some((item) => item.documentVersion === command.documentVersion)) throw new WorkbenchError('MODEL_ALREADY_GENERATED', '这个版本已经生成过模型');
    const document = documentFor(command.documentVersion);
    const result: StoredResult = {
      documentVersion: command.documentVersion,
      status: document.qualityGate.publishable ? 'GENERATED' : 'NEEDS_SUPPLEMENT',
      generatedAt: now,
      decisions: {},
      confirmedGroups: [],
      checks: [],
      publishedAt: null,
    };
    addAutomaticReviews(result, fixture, now);
    state.results.push(result);
    return;
  }

  if (command.type === 'CONFIRM_BUSINESS_GROUP' || command.type === 'REJECT_ITEM' || command.type === 'DECIDE_REVIEW_ITEM') {
    requireReview(command.reviewer, command.reason);
    const result = resultFor(command.documentVersion);
    if (result.status === 'PUBLISHED') throw new WorkbenchError('PUBLISHED_IMMUTABLE', '已发布模型的审核决定不可修改');
    const document = documentFor(command.documentVersion);
    if (command.type === 'REJECT_ITEM' || command.type === 'DECIDE_REVIEW_ITEM') {
      if (!document.review.items.some((item) => item.id === command.itemId)) throw new WorkbenchError('ITEM_NOT_FOUND', '审核项不存在');
      result.decisions[command.itemId] = {
        itemId: command.itemId,
        decision: command.type === 'REJECT_ITEM' ? 'REJECTED' : command.decision,
        source: 'USER', reviewer: command.reviewer.trim(), reason: command.reason.trim(), decidedAt: now,
      };
      const completedGroup = document.review.groups.find((group) => group.itemIds.includes(command.itemId)
        && group.itemIds.every((itemId) => result.decisions[itemId]));
      if (completedGroup && !result.confirmedGroups.includes(completedGroup.id)) result.confirmedGroups.push(completedGroup.id);
      return;
    }
    const group = document.review.groups.find((item) => item.id === command.groupId);
    if (!group) throw new WorkbenchError('GROUP_NOT_FOUND', '业务分组不存在');
    group.itemIds.forEach((itemId) => {
      if (result.decisions[itemId]) return;
      result.decisions[itemId] = { itemId, decision: 'APPROVED', source: 'USER', reviewer: command.reviewer.trim(), reason: command.reason.trim(), decidedAt: now };
    });
    if (!result.confirmedGroups.includes(group.id)) result.confirmedGroups.push(group.id);
    return;
  }

  requireReview(command.reviewer, command.reason);
  const result = resultFor(command.documentVersion);
  const document = documentFor(command.documentVersion);
  if (!document.qualityGate.publishable || result.status === 'NEEDS_SUPPLEMENT') throw new WorkbenchError('QUALITY_BLOCKED', '设计资料需要补充，当前模型不能发布');
  if (Object.keys(result.decisions).length !== document.review.items.length) throw new WorkbenchError('REVIEW_INCOMPLETE', '请先确认全部业务分组');
  const publicationChecks = checks(document);
  if (publicationChecks.some((item) => !item.passed)) throw new WorkbenchError('VALIDATION_FAILED', '模型检查未通过，暂不能发布');
  if (!document.modelVersion) throw new WorkbenchError('MODEL_VERSION_MISSING', '模型版本不存在');
  if (state.catalogs.some((item) => item.modelVersion === document.modelVersion)) throw new WorkbenchError('ALREADY_PUBLISHED', '这个模型版本已经发布');
  result.status = 'PUBLISHED';
  result.publishedAt = now;
  result.checks = publicationChecks;
  state.catalogs.push({ id: `catalog-${document.modelVersion.toLowerCase()}`, modelVersion: document.modelVersion, documentVersion: document.documentVersion, publishedAt: now, publishedBy: command.reviewer.trim(), reason: command.reason.trim(), immutable: true });
  state.currentCatalogVersion = document.modelVersion;
}
