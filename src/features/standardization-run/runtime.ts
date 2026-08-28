import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { diffSourceDocumentLines } from '../source-documents/runtime.ts';
import { sourceDocumentBlockHumanText } from '../source-documents/structured-projection.ts';
import type { ContentAddressedStore, ContentReference } from '../source-documents/types.ts';
import type { ConflictResolutionArtifact } from '../guanyijia-standardization-story/types.ts';
import { createDefaultStandardizationMetadataStore } from './standardization-metadata-store.ts';
import type {
  StandardizationMetadataSnapshot,
  StandardizationMetadataStore,
  StandardizationRun,
  StandardizationRunCommand,
  StandardizationRunRuntime,
  StandardizationTimelineEvent,
} from './types.ts';

type MetadataStorage = Pick<Storage, 'getItem' | 'setItem'>;
type CommandExecution = { runId: string; fingerprint: string };
type StoredState = {
  schemaVersion: 1;
  sequence: number;
  runs: StandardizationRun[];
  commandRunIds: Record<string, string | CommandExecution>;
};

const clone = <T,>(value: T): T => structuredClone(value);

function canonicalJson(value: unknown): string {
  if (value === undefined) return 'undefined';
  if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(record[key])}`).join(',')}}`;
  }
  throw new Error('标准化运行命令包含不可序列化值');
}

function commandFingerprint(command: StandardizationRunCommand) {
  return `sha256:${sha256HexSync(canonicalJson(command))}`;
}

function emptyState(): StoredState {
  return { schemaVersion: 1, sequence: 0, runs: [], commandRunIds: Object.create(null) as StoredState['commandRunIds'] };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function metadataError(detail: string): never {
  throw new Error(`标准化运行元数据已损坏：${detail}`);
}

const runStatuses = new Set([
  'READY', 'READING_SOURCE', 'REVIEWING_DOCUMENT', 'CONFLICT_BLOCKED',
  'READY_FOR_OUTPUT', 'FROZEN', 'HANDED_OFF',
]);
const sourceStatuses = new Set([
  'PENDING', 'READING', 'DOCUMENT_READY', 'REVIEWED', 'CONFLICT_BLOCKED', 'ALIGNED',
]);
const eventTypes = new Set([
  'SOURCE_READ_STARTED', 'SOURCE_READ_COMPLETED', 'DOCUMENT_GENERATED', 'DOCUMENT_REVIEWED',
  'DOCUMENT_REVISED', 'CONFLICT_FOUND', 'CONFLICT_RESOLVED', 'DELIVERABLE_GENERATED', 'REVIEW_SUBMITTED',
  'DELIVERABLE_SUPERSEDED',
  'CONFLICT_DECISION_REPLACED',
  'CONFLICT_CORROBORATED', 'ASSISTANT_TURN_RECORDED', 'ASSISTANT_PATCH_CONFIRMED',
  'ASSISTANT_PATCH_CANCELLED',
  'DELIVERABLE_FROZEN', 'MODELING_HANDOFF_COMPLETED',
]);
const sourceEventTypes = new Set([
  'SOURCE_READ_STARTED', 'SOURCE_READ_COMPLETED', 'DOCUMENT_GENERATED',
  'DOCUMENT_REVISED', 'DOCUMENT_REVIEWED', 'CONFLICT_FOUND', 'CONFLICT_RESOLVED',
  'CONFLICT_CORROBORATED',
  'ASSISTANT_PATCH_CONFIRMED',
]);
const deliveryEventTypes = new Set([
  'DELIVERABLE_GENERATED', 'DELIVERABLE_SUPERSEDED', 'REVIEW_SUBMITTED', 'DELIVERABLE_FROZEN', 'MODELING_HANDOFF_COMPLETED',
]);

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && Boolean(value.trim());
}

function assertStringIdArray(value: unknown, label: string, context: string): asserts value is string[] {
  if (!Array.isArray(value)) metadataError(`${label}必须是数组：${context}`);
  if (value.some((item) => !isNonEmptyString(item)) || new Set(value).size !== value.length) {
    metadataError(`${label}包含空值或重复值：${context}`);
  }
}

function validateSources(run: Record<string, unknown>) {
  const runId = run.runId as string;
  const sources = run.sources as unknown[];
  if (!sources.length) metadataError(`运行至少需要一个来源：${runId}`);
  const sourceIds = new Set<string>();
  for (let index = 0; index < sources.length; index += 1) {
    const source = sources[index];
    if (!isRecord(source)) metadataError(`来源步骤必须是对象：${runId}/${index + 1}`);
    if (!isNonEmptyString(source.sourceId)) metadataError(`sourceId不能为空：${runId}/${index + 1}`);
    if (sourceIds.has(source.sourceId)) metadataError(`sourceId重复：${source.sourceId}`);
    sourceIds.add(source.sourceId);
    if (!isNonEmptyString(source.sourceName)) metadataError(`sourceName不能为空：${runId}/${source.sourceId}`);
    if (!Number.isSafeInteger(source.order) || source.order !== index + 1) {
      metadataError(`来源order必须从1连续递增：${runId}/${source.sourceId}`);
    }
    if (typeof source.status !== 'string' || !sourceStatuses.has(source.status)) {
      metadataError(`来源status无效：${runId}/${source.sourceId}`);
    }
    const introducedConflictIds = source.introducedConflictIds;
    const resolvedConflictIds = source.resolvedConflictIds;
    assertStringIdArray(introducedConflictIds, 'introducedConflictIds', `${runId}/${source.sourceId}`);
    assertStringIdArray(resolvedConflictIds, 'resolvedConflictIds', `${runId}/${source.sourceId}`);
    if (resolvedConflictIds.some((conflictId) => !introducedConflictIds.includes(conflictId))) {
      metadataError(`resolvedConflictIds包含未引入冲突：${runId}/${source.sourceId}`);
    }

    const hasReadSummary = source.readSummary !== undefined;
    const hasDocumentId = source.documentId !== undefined;
    const hasDocumentRevision = source.documentRevision !== undefined;
    if (new Set([hasReadSummary, hasDocumentId, hasDocumentRevision]).size !== 1) {
      metadataError(`来源文档字段必须同时存在：${runId}/${source.sourceId}`);
    }
    if (hasReadSummary) {
      if (!isRecord(source.readSummary) || !isNonEmptyString(source.readSummary.summary)) {
        metadataError(`readSummary无效：${runId}/${source.sourceId}`);
      }
      if (!Number.isSafeInteger(source.readSummary.objectCount) || (source.readSummary.objectCount as number) < 0
        || !Number.isSafeInteger(source.readSummary.evidenceCount) || (source.readSummary.evidenceCount as number) < 0) {
        metadataError(`readSummary计数无效：${runId}/${source.sourceId}`);
      }
      if (!isNonEmptyString(source.documentId)
        || !Number.isSafeInteger(source.documentRevision)
        || (source.documentRevision as number) < 1) {
        metadataError(`来源文档身份无效：${runId}/${source.sourceId}`);
      }
    }
    const documentStatuses = new Set(['DOCUMENT_READY', 'REVIEWED', 'CONFLICT_BLOCKED', 'ALIGNED']);
    if (documentStatuses.has(source.status) !== hasReadSummary) {
      metadataError(`来源status与文档字段不一致：${runId}/${source.sourceId}`);
    }
  }
  return sourceIds;
}

function validateStateCombinations(run: Record<string, unknown>) {
  const runId = run.runId as string;
  const sources = run.sources as Array<Record<string, unknown>>;
  const statuses = sources.map((source) => source.status as string);
  const count = (status: string) => statuses.filter((candidate) => candidate === status).length;
  const only = (...allowed: string[]) => statuses.every((status) => allowed.includes(status));
  const hasFrozenDeliveryEvent = Array.isArray(run.timeline)
    && run.timeline.some((event) => isRecord(event) && event.type === 'DELIVERABLE_FROZEN');

  let progress: 'ALIGNED_PREFIX' | 'CURRENT' | 'PENDING_SUFFIX' = 'ALIGNED_PREFIX';
  for (const status of statuses) {
    if (status === 'ALIGNED' || status === 'CONFLICT_BLOCKED') {
      if (progress !== 'ALIGNED_PREFIX') {
        metadataError(`来源状态必须保持ALIGNED前缀和PENDING后缀：${runId}`);
      }
    } else if (status === 'PENDING') {
      progress = 'PENDING_SUFFIX';
    } else {
      if (progress !== 'ALIGNED_PREFIX') {
        metadataError(`来源状态必须保持ALIGNED前缀、至多一个当前来源和PENDING后缀：${runId}`);
      }
      progress = 'CURRENT';
    }
  }

  for (const source of sources) {
    const introduced = source.introducedConflictIds as string[];
    const resolved = source.resolvedConflictIds as string[];
    const hasUnresolved = introduced.some((conflictId) => !resolved.includes(conflictId));
    if (source.status === 'CONFLICT_BLOCKED' && !hasUnresolved) {
      metadataError(`CONFLICT_BLOCKED没有未解决冲突：${runId}/${source.sourceId as string}`);
    }
    if (source.status === 'ALIGNED' && hasUnresolved) {
      metadataError(`ALIGNED仍有未解决冲突：${runId}/${source.sourceId as string}`);
    }
  }

  switch (run.status) {
  case 'READY':
    if (!only('PENDING', 'ALIGNED', 'CONFLICT_BLOCKED')
      || (count('PENDING') < 1 && count('CONFLICT_BLOCKED') < 1)) {
      metadataError(`READY与来源状态不一致：${runId}`);
    }
    break;
  case 'READING_SOURCE':
    if (!only('PENDING', 'READING', 'ALIGNED', 'CONFLICT_BLOCKED') || count('READING') !== 1) {
      metadataError(`READING_SOURCE与来源状态不一致：${runId}`);
    }
    break;
  case 'REVIEWING_DOCUMENT':
    if (!only('PENDING', 'DOCUMENT_READY', 'ALIGNED', 'CONFLICT_BLOCKED') || count('DOCUMENT_READY') !== 1) {
      metadataError(`REVIEWING_DOCUMENT与来源状态不一致：${runId}`);
    }
    break;
  case 'CONFLICT_BLOCKED':
    if (!only('PENDING', 'CONFLICT_BLOCKED', 'ALIGNED') || count('CONFLICT_BLOCKED') < 1) {
      metadataError(`CONFLICT_BLOCKED与来源状态不一致：${runId}`);
    }
    break;
  case 'READY_FOR_OUTPUT':
    if (!only('ALIGNED')) metadataError(`READY_FOR_OUTPUT仍有未对齐来源：${runId}`);
    break;
  case 'FROZEN':
    if (!only('ALIGNED')) metadataError(`FROZEN仍有未对齐来源：${runId}`);
    if (!isNonEmptyString(run.deliverableId)
      || (!isNonEmptyString(run.reviewId) && !hasFrozenDeliveryEvent)) {
      metadataError(`FROZEN缺少交付物或定版确认：${runId}`);
    }
    break;
  case 'HANDED_OFF':
    if (!only('ALIGNED')) metadataError(`HANDED_OFF仍有未对齐来源：${runId}`);
    if (!isNonEmptyString(run.deliverableId)
      || (!isNonEmptyString(run.reviewId) && !hasFrozenDeliveryEvent)
      || !isNonEmptyString(run.modelingHandoffId)) {
      metadataError(`HANDED_OFF缺少交付物、定版确认或交接标识：${runId}`);
    }
    break;
  }

  if (run.reviewId !== undefined && run.deliverableId === undefined) {
    metadataError(`审核标识缺少对应交付物：${runId}`);
  }
  if (run.modelingHandoffId !== undefined && run.status !== 'HANDED_OFF') {
    metadataError(`交接标识与运行status不一致：${runId}`);
  }
}

function validateDeliveryTimeline(run: Record<string, unknown>) {
  const runId = run.runId as string;
  const timeline = run.timeline as Array<Record<string, unknown>>;
  const deliveryStatuses = new Set(['READY_FOR_OUTPUT', 'FROZEN', 'HANDED_OFF']);
  const supersededDeliverableIds = Array.isArray(run.supersededDeliverableIds)
    ? run.supersededDeliverableIds as string[]
    : [];
  const hasAnyDeliveryIdentity = run.deliverableId !== undefined
    || supersededDeliverableIds.length > 0
    || run.reviewId !== undefined
    || run.modelingHandoffId !== undefined;
  if (hasAnyDeliveryIdentity && !deliveryStatuses.has(run.status as string)) {
    metadataError(`交付身份只能存在于全部来源已对齐的交付阶段：${runId}`);
  }

  const requireSingleEvent = (
    type: string,
    expected: boolean,
    identityLabel: string,
  ) => {
    const indexes = timeline.flatMap((event, index) => event.type === type ? [index] : []);
    if (!expected) {
      if (indexes.length) metadataError(`${type}事件与运行交付阶段不一致：${runId}`);
      return -1;
    }
    if (!indexes.length) metadataError(`${identityLabel}缺少对应的${type}事件：${runId}`);
    if (indexes.length !== 1) metadataError(`${type}事件重复：${runId}`);
    return indexes[0]!;
  };

  const deliverableId = isNonEmptyString(run.deliverableId) ? run.deliverableId : undefined;
  const reviewId = isNonEmptyString(run.reviewId) ? run.reviewId : undefined;
  const frozen = run.status === 'FROZEN' || run.status === 'HANDED_OFF';
  const handedOff = run.status === 'HANDED_OFF';
  const generatedIndexes = timeline.flatMap((event, index) => event.type === 'DELIVERABLE_GENERATED' ? [index] : []);
  const supersededIndexes = timeline.flatMap((event, index) => event.type === 'DELIVERABLE_SUPERSEDED' ? [index] : []);
  const expectedGeneratedCount = supersededDeliverableIds.length + (deliverableId ? 1 : 0);
  if (generatedIndexes.length !== expectedGeneratedCount) {
    metadataError(`交付生成事件与当前或历史交付物数量不一致：${runId}`);
  }
  if (supersededIndexes.length !== supersededDeliverableIds.length) {
    metadataError(`交付物重新生成事件与历史交付物数量不一致：${runId}`);
  }
  const finalEventIndexes = [
    requireSingleEvent('REVIEW_SUBMITTED', Boolean(reviewId), 'reviewId'),
    requireSingleEvent('DELIVERABLE_FROZEN', frozen, 'deliverableId'),
    requireSingleEvent('MODELING_HANDOFF_COMPLETED', handedOff, 'modelingHandoffId'),
  ].filter((index) => index >= 0);
  if (finalEventIndexes.some((index, position) => position > 0 && index <= finalEventIndexes[position - 1]!)) {
    metadataError(`交付确认事件顺序无效：${runId}`);
  }
}

function parseDeliveryEventPayload(
  type: Extract<StandardizationTimelineEvent['type'],
    'DELIVERABLE_GENERATED' | 'DELIVERABLE_SUPERSEDED' | 'REVIEW_SUBMITTED' | 'DELIVERABLE_FROZEN' | 'MODELING_HANDOFF_COMPLETED'>,
  payload: string,
  run: Pick<StandardizationRun, 'deliverableId' | 'supersededDeliverableIds' | 'reviewId' | 'modelingHandoffId'>,
) {
  let parsed: unknown;
  try { parsed = JSON.parse(payload); } catch { metadataError(`${type}事件载荷无法解析`); }
  const ref = (value: unknown) => typeof value === 'string' && /^sha256:[0-9a-f]{64}$/.test(value);
  if (!isRecord(parsed) || parsed.schemaVersion !== 1) metadataError(`${type}事件载荷结构无效`);
  if (type === 'DELIVERABLE_GENERATED') {
    const knownDeliverableIds = [run.deliverableId, ...(run.supersededDeliverableIds ?? [])];
    if (!knownDeliverableIds.includes(parsed.deliverableId as string)
      || !ref(parsed.coreSha256) || !ref(parsed.sourceManifestRef)
      || !ref(parsed.mergedDocumentRef) || !ref(parsed.decisionManifestRef)
      || !ref(parsed.governanceAppendixRef) || !ref(parsed.modelingArtifactRef)
      || !ref(parsed.zeroDeltaReportRef)) {
      metadataError(`${type}事件与deliverable内容身份不一致`);
    }
  } else if (type === 'DELIVERABLE_SUPERSEDED') {
    if (!(run.supersededDeliverableIds ?? []).includes(parsed.deliverableId as string)
      || !isNonEmptyString(parsed.reason)) {
      metadataError(`${type}事件与历史交付物身份不一致`);
    }
  } else if (type === 'REVIEW_SUBMITTED') {
    if (parsed.deliverableId !== run.deliverableId || parsed.reviewId !== run.reviewId
      || !isNonEmptyString(parsed.authorUserId) || !isNonEmptyString(parsed.confirmedAt)
      || !ref(parsed.coreSha256)) metadataError(`${type}事件与作者确认身份不一致`);
  } else if (type === 'DELIVERABLE_FROZEN') {
    const legacyIndependentReview = parsed.reviewId === run.reviewId
      && isNonEmptyString(parsed.reviewerUserId) && isNonEmptyString(parsed.reviewedAt);
    const directAuthorFreeze = isNonEmptyString(parsed.confirmationId)
      && isNonEmptyString(parsed.authorUserId)
      && isNonEmptyString(parsed.confirmedAt)
      && Number.isSafeInteger(parsed.inputDeliverableRevision)
      && (parsed.inputDeliverableRevision as number) >= 1;
    if (parsed.deliverableId !== run.deliverableId
      || (!legacyIndependentReview && !directAuthorFreeze)
      || !ref(parsed.coreSha256) || !ref(parsed.zeroDeltaReportRef)
      || !ref(parsed.semanticPayloadSha256)) metadataError(`${type}事件与冻结身份不一致`);
  } else {
    const legacyZeroDelta = ref(parsed.zeroDeltaReportRef)
      && ref(parsed.semanticPayloadSha256) && isNonEmptyString(parsed.protectedCatalogId);
    const standardizationDocument = ref(parsed.mergedDocumentRef)
      && ref(parsed.modelingEligibilityRef)
      && Number.isSafeInteger(parsed.eligibleConclusionCount)
      && Number.isSafeInteger(parsed.excludedConclusionCount)
      && (parsed.eligibleConclusionCount as number) >= 0
      && (parsed.excludedConclusionCount as number) >= 0;
    if (parsed.deliverableId !== run.deliverableId
      || parsed.handoffId !== run.modelingHandoffId || !ref(parsed.receiptRef)
      || (!legacyZeroDelta && !standardizationDocument)
      || !isNonEmptyString(parsed.handedOffBy) || !isNonEmptyString(parsed.handedOffAt)) {
      metadataError(`${type}事件与标准化文档交接身份不一致`);
    }
  }
  return parsed;
}

function validateDeliveryEventAuditChain(
  run: StandardizationRun,
  entries: Array<{ event: StandardizationTimelineEvent; payload: Record<string, unknown> }>,
) {
  const generatedEntries = entries.filter((entry) => entry.event.type === 'DELIVERABLE_GENERATED');
  const supersededEntries = entries.filter((entry) => entry.event.type === 'DELIVERABLE_SUPERSEDED');
  const submitted = entries.find((entry) => entry.event.type === 'REVIEW_SUBMITTED');
  const frozen = entries.find((entry) => entry.event.type === 'DELIVERABLE_FROZEN');
  const handedOff = entries.find((entry) => entry.event.type === 'MODELING_HANDOFF_COMPLETED');
  const generatedById = new Map(generatedEntries.map((entry) => [entry.payload.deliverableId as string, entry]));
  if (generatedById.size !== generatedEntries.length
    || generatedEntries.some((entry) => entry.event.actor.userId !== run.createdBy)) {
    metadataError(`交付事件审计链的生成者或交付物身份无效：${run.runId}`);
  }
  const currentGenerated = run.deliverableId
    ? generatedById.get(run.deliverableId)
    : undefined;
  for (const superseded of supersededEntries) {
    const deliverableId = superseded.payload.deliverableId as string;
    const generated = generatedById.get(deliverableId);
    if (!generated || generated.event.sequence >= superseded.event.sequence
      || superseded.event.actor.userId !== run.createdBy) {
      metadataError(`历史交付物没有先生成后重新生成的审计链：${run.runId}`);
    }
  }
  const supersededIds = new Set(supersededEntries.map((entry) => entry.payload.deliverableId as string));
  if (supersededIds.size !== supersededEntries.length
    || canonicalJson([...supersededIds].sort()) !== canonicalJson([...(run.supersededDeliverableIds ?? [])].sort())) {
    metadataError(`历史交付物与重新生成事件不一致：${run.runId}`);
  }
  if (run.deliverableId && (!currentGenerated || supersededIds.has(run.deliverableId))) {
    metadataError(`当前交付物与生成/重新生成事件不一致：${run.runId}`);
  }
  if (submitted && (!currentGenerated
    || submitted.payload.coreSha256 !== currentGenerated?.payload.coreSha256
    || submitted.payload.authorUserId !== run.createdBy
    || submitted.event.actor.userId !== submitted.payload.authorUserId)) {
    metadataError(`交付事件审计链的作者确认与生成core不一致：${run.runId}`);
  }
  if (frozen) {
    const direct = frozen.payload.confirmationId !== undefined;
    const validIndependentFreeze = submitted
      && frozen.payload.coreSha256 === currentGenerated?.payload.coreSha256
      && frozen.payload.zeroDeltaReportRef === currentGenerated?.payload.zeroDeltaReportRef
      && frozen.event.actor.userId === frozen.payload.reviewerUserId
      && frozen.event.actor.userId !== run.createdBy;
    const validAuthorFreeze = frozen.payload.coreSha256 === currentGenerated?.payload.coreSha256
      && frozen.payload.zeroDeltaReportRef === currentGenerated?.payload.zeroDeltaReportRef
      && frozen.event.actor.userId === frozen.payload.authorUserId;
    if (!currentGenerated || (direct ? !validAuthorFreeze : !validIndependentFreeze)) {
      metadataError(`交付事件审计链的定版确认与生成内容不一致：${run.runId}`);
    }
  }
  if (handedOff) {
    const legacyZeroDelta = handedOff.payload.zeroDeltaReportRef !== undefined;
    const valid = legacyZeroDelta
      ? Boolean(frozen
        && handedOff.payload.zeroDeltaReportRef === frozen.payload.zeroDeltaReportRef
        && handedOff.payload.semanticPayloadSha256 === frozen.payload.semanticPayloadSha256)
      : Boolean(frozen && handedOff.payload.mergedDocumentRef === currentGenerated?.payload.mergedDocumentRef
        && typeof handedOff.payload.eligibleConclusionCount === 'number'
        && typeof handedOff.payload.excludedConclusionCount === 'number');
    if (!valid || handedOff.event.actor.userId !== handedOff.payload.handedOffBy) {
      metadataError(`交付事件审计链的建模交接与冻结内容不一致：${run.runId}`);
    }
  }
}

function validateTimeline(run: Record<string, unknown>, sourceIds: Set<string>) {
  const runId = run.runId as string;
  const timeline = run.timeline as unknown[];
  const eventIds = new Set<string>();
  for (let index = 0; index < timeline.length; index += 1) {
    const event = timeline[index];
    if (!isRecord(event)) metadataError(`时间线事件必须是对象：${runId}/${index + 1}`);
    if (!Number.isSafeInteger(event.sequence) || event.sequence !== index + 1) {
      metadataError(`事件sequence必须从1连续递增：${runId}/${index + 1}`);
    }
    if (!isNonEmptyString(event.eventId)) metadataError(`eventId不能为空：${runId}/${index + 1}`);
    if (eventIds.has(event.eventId)) metadataError(`eventId重复：${event.eventId}`);
    eventIds.add(event.eventId);
    if (event.eventId !== `${runId}:event:${index + 1}`) metadataError(`eventId格式无效：${event.eventId}`);
    if (event.runId !== runId) metadataError(`事件runId与运行不一致：${event.eventId}`);
    if (typeof event.type !== 'string' || !eventTypes.has(event.type)) metadataError(`事件type无效：${event.eventId}`);
    if (!isRecord(event.actor) || !isNonEmptyString(event.actor.userId)) metadataError(`事件actor无效：${event.eventId}`);
    if (typeof event.payloadRef !== 'string' || !/^sha256:[0-9a-f]{64}$/.test(event.payloadRef)) {
      metadataError(`事件payloadRef无效：${event.eventId}`);
    }
    if (!isNonEmptyString(event.createdAt)) metadataError(`事件createdAt无效：${event.eventId}`);
    if (event.type === 'DELIVERABLE_GENERATED') {
      const indexValue = event.deliveryContentIndex;
      const allowedKeys = new Set([
        'sourceManifestRef', 'mergedDocumentRef', 'decisionManifestRef',
        'governanceAppendixRef', 'modelingArtifactRef', 'zeroDeltaReportRef',
      ]);
      if (!isRecord(indexValue) || typeof indexValue.coreSha256 !== 'string'
        || !/^sha256:[0-9a-f]{64}$/u.test(indexValue.coreSha256)
        || !Array.isArray(indexValue.entries) || indexValue.entries.length !== 6
        || new Set(indexValue.entries.map((entry) => isRecord(entry) ? entry.key : undefined)).size !== 6
        || indexValue.entries.some((entry) => !isRecord(entry)
          || typeof entry.key !== 'string' || !allowedKeys.has(entry.key)
          || typeof entry.contentRef !== 'string' || !/^sha256:[0-9a-f]{64}$/u.test(entry.contentRef))) {
        metadataError(`交付生成事件review-window索引无效：${event.eventId}`);
      }
    } else if (event.deliveryContentIndex !== undefined) {
      metadataError(`非交付生成事件不能携带review-window索引：${event.eventId}`);
    }
    if (sourceEventTypes.has(event.type)) {
      if (!isNonEmptyString(event.sourceId) || !sourceIds.has(event.sourceId)) {
        metadataError(`事件sourceId无效：${event.eventId}`);
      }
    } else if (event.sourceId !== undefined) {
      metadataError(`交付事件不能包含sourceId：${event.eventId}`);
    }
    if (event.type === 'CONFLICT_DECISION_REPLACED') {
      if (!isNonEmptyString(event.conflictId)) {
        metadataError(`取代决定事件缺少conflictId：${event.eventId}`);
      }
    } else if (event.conflictId !== undefined) {
      metadataError(`非取代决定事件不能包含conflictId：${event.eventId}`);
    }
    if (event.type === 'ASSISTANT_TURN_RECORDED') {
      if (!isNonEmptyString(event.assistantTurnId)
        || !Array.isArray(event.assistantKnowledgeSourceRevisions)
        || event.assistantKnowledgeSourceRevisions.some((revision) => (
          !isRecord(revision) || !isNonEmptyString(revision.sourceId)
          || !isNonEmptyString(revision.documentId)
          || !Number.isSafeInteger(revision.documentRevision) || (revision.documentRevision as number) < 1
        ))) metadataError(`Assistant Turn事件索引无效：${event.eventId}`);
      const hasProposal = event.assistantProposalId !== undefined
        || event.assistantDocumentId !== undefined || event.assistantDocumentRevision !== undefined;
      if (hasProposal && (!isNonEmptyString(event.assistantProposalId)
        || !isNonEmptyString(event.assistantDocumentId)
        || !Number.isSafeInteger(event.assistantDocumentRevision)
        || (event.assistantDocumentRevision as number) < 1)) {
        metadataError(`Assistant Proposal事件索引无效：${event.eventId}`);
      }
    } else if (event.type === 'ASSISTANT_PATCH_CONFIRMED'
      || event.type === 'ASSISTANT_PATCH_CANCELLED') {
      if (!isNonEmptyString(event.assistantProposalId)
        || !isNonEmptyString(event.assistantDocumentId)
        || !Number.isSafeInteger(event.assistantDocumentRevision)
        || (event.assistantDocumentRevision as number) < 1
        || event.assistantTurnId !== undefined || event.assistantKnowledgeSourceRevisions !== undefined) {
        metadataError(`Assistant Proposal决定事件索引无效：${event.eventId}`);
      }
    } else if (event.assistantTurnId !== undefined || event.assistantProposalId !== undefined
      || event.assistantDocumentId !== undefined || event.assistantDocumentRevision !== undefined
      || event.assistantKnowledgeSourceRevisions !== undefined) {
      metadataError(`非Assistant事件不能包含Assistant索引：${event.eventId}`);
    }
    if (event.type === 'DOCUMENT_GENERATED' || event.type === 'DOCUMENT_REVISED') {
      if (!isNonEmptyString(event.sourceDocumentId)
        || !Number.isSafeInteger(event.sourceDocumentRevision)
        || (event.sourceDocumentRevision as number) < 1) {
        metadataError(`来源文档事件索引无效：${event.eventId}`);
      }
    } else if (event.sourceDocumentId !== undefined || event.sourceDocumentRevision !== undefined) {
      metadataError(`非来源文档事件不能包含文档索引：${event.eventId}`);
    }
  }
}

function validateSourceTimelineSemantics(run: Record<string, unknown>) {
  const runId = run.runId as string;
  const sources = run.sources as Array<Record<string, unknown>>;
  const timeline = run.timeline as Array<Record<string, unknown>>;
  const semanticError = (sourceId: string, type: unknown) => {
    metadataError(`来源事件语义顺序无效：${runId}/${sourceId}/${String(type)}`);
  };

  let nextSourceStart = 0;
  for (const event of timeline) {
    if (event.type !== 'SOURCE_READ_STARTED') continue;
    const sourceIndex = sources.findIndex((source) => source.sourceId === event.sourceId);
    if (sourceIndex !== nextSourceStart) semanticError(String(event.sourceId), event.type);
    nextSourceStart += 1;
  }

  for (const source of sources) {
    const sourceId = source.sourceId as string;
    const events = timeline.filter((event) => event.sourceId === sourceId);
    let started = false;
    let completed = false;
    let generated = false;
    let documentRevised = false;
    let documentReviewed = false;
    let conflictFoundCount = 0;
    let conflictResolvedCount = 0;

    for (const event of events) {
      switch (event.type) {
      case 'SOURCE_READ_STARTED':
        if (started) semanticError(sourceId, event.type);
        started = true;
        break;
      case 'SOURCE_READ_COMPLETED':
        if (!started || completed) semanticError(sourceId, event.type);
        completed = true;
        break;
      case 'DOCUMENT_GENERATED':
        if (!completed || generated) semanticError(sourceId, event.type);
        generated = true;
        break;
      case 'CONFLICT_CORROBORATED':
        if (!generated || documentRevised || documentReviewed) semanticError(sourceId, event.type);
        break;
      case 'DOCUMENT_REVISED':
        if (!generated || documentReviewed || conflictResolvedCount > 0) {
          semanticError(sourceId, event.type);
        }
        documentRevised = true;
        break;
      case 'ASSISTANT_PATCH_CONFIRMED':
        if (!generated || documentReviewed || conflictResolvedCount > 0) {
          semanticError(sourceId, event.type);
        }
        break;
      case 'DOCUMENT_REVIEWED':
        if (!generated || documentReviewed) {
          semanticError(sourceId, event.type);
        }
        documentReviewed = true;
        break;
      case 'CONFLICT_FOUND':
        if (!generated || conflictResolvedCount > 0) semanticError(sourceId, event.type);
        conflictFoundCount += 1;
        break;
      case 'CONFLICT_RESOLVED':
        if (!generated || conflictFoundCount === 0) semanticError(sourceId, event.type);
        conflictResolvedCount += 1;
        break;
      default:
        semanticError(sourceId, event.type);
      }
    }

    const introduced = source.introducedConflictIds as string[];
    const resolved = source.resolvedConflictIds as string[];
    const hasUnresolved = introduced.some((conflictId) => !resolved.includes(conflictId));
    const hasExpectedResolutions = conflictResolvedCount === resolved.length;
    const sourceValid = source.status === 'PENDING'
      ? !started
      : source.status === 'READING'
        ? started && !completed
        : source.status === 'DOCUMENT_READY'
          ? generated && !documentReviewed && hasExpectedResolutions
          : source.status === 'REVIEWED'
            ? generated && documentReviewed && !introduced.length && conflictFoundCount === 0 && !resolved.length
            : source.status === 'CONFLICT_BLOCKED'
              ? generated && documentReviewed && conflictFoundCount > 0 && hasUnresolved && hasExpectedResolutions
              : generated && documentReviewed && !hasUnresolved && hasExpectedResolutions
                && (!introduced.length || conflictFoundCount > 0);
    if (!sourceValid) semanticError(sourceId, `status:${source.status as string}`);
  }

  const firstDeliveryIndex = timeline.findIndex((event) => deliveryEventTypes.has(event.type as string));
  if (firstDeliveryIndex >= 0 && timeline.slice(firstDeliveryIndex + 1).some((event) => (
    sourceEventTypes.has(event.type as string)
  ))) {
    metadataError(`来源事件不能出现在交付事件之后：${runId}`);
  }
}

function load(raw: string | null): StoredState {
  if (!raw) return emptyState();
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    metadataError('无法解析JSON');
  }
  if (!isRecord(parsed)) metadataError('根节点必须是对象');
  if (parsed.schemaVersion !== 1) metadataError('schemaVersion必须为1');
  if (!Number.isSafeInteger(parsed.sequence) || (parsed.sequence as number) < 0) {
    metadataError('sequence必须是非负安全整数');
  }
  if (!Array.isArray(parsed.runs)) metadataError('runs必须是数组');
  if (!isRecord(parsed.commandRunIds)) metadataError('commandRunIds必须是对象');

  const runIds = new Set<string>();
  let maximumRunNumber = 0;
  for (const candidate of parsed.runs) {
    if (!isRecord(candidate)) metadataError('run必须是对象');
    if (candidate.schemaVersion !== 1) metadataError('run.schemaVersion必须为1');
    if (typeof candidate.runId !== 'string' || !candidate.runId.trim()) metadataError('runId不能为空');
    if (runIds.has(candidate.runId)) metadataError('runId重复');
    runIds.add(candidate.runId);
    const identity = /^standardization-run-(\d+)$/.exec(candidate.runId);
    if (!identity || !Number.isSafeInteger(Number(identity[1])) || Number(identity[1]) < 1) {
      metadataError(`runId格式无效：${candidate.runId}`);
    }
    maximumRunNumber = Math.max(maximumRunNumber, Number(identity[1]));
    if (!isNonEmptyString(candidate.projectId) || !isNonEmptyString(candidate.scenarioKey)
      || !isNonEmptyString(candidate.batchId)) {
      metadataError(`运行项目、故事或批次无效：${candidate.runId}`);
    }
    if (!Number.isSafeInteger(candidate.revision) || (candidate.revision as number) < 0) {
      metadataError(`运行revision无效：${candidate.runId}`);
    }
    if (!Array.isArray(candidate.sources) || !Array.isArray(candidate.timeline)) {
      metadataError(`运行来源或时间线无效：${candidate.runId}`);
    }
    if (typeof candidate.status !== 'string' || !runStatuses.has(candidate.status)) {
      metadataError(`运行status无效：${candidate.runId}`);
    }
    if (!isNonEmptyString(candidate.createdBy)
      || !isNonEmptyString(candidate.createdAt)
      || !isNonEmptyString(candidate.updatedAt)) {
      metadataError(`运行审计字段无效：${candidate.runId}`);
    }
    for (const field of ['deliverableId', 'reviewId', 'modelingHandoffId'] as const) {
      if (candidate[field] !== undefined && !isNonEmptyString(candidate[field])) {
        metadataError(`运行${field}无效：${candidate.runId}`);
      }
    }
    if (candidate.supersededDeliverableIds !== undefined) {
      assertStringIdArray(candidate.supersededDeliverableIds, 'supersededDeliverableIds', candidate.runId);
      if (candidate.supersededDeliverableIds.includes(candidate.deliverableId as string)) {
        metadataError(`当前交付物不能同时标记为历史交付物：${candidate.runId}`);
      }
    }
    const sourceIds = validateSources(candidate);
    validateStateCombinations(candidate);
    validateTimeline(candidate, sourceIds);
    validateSourceTimelineSemantics(candidate);
    validateDeliveryTimeline(candidate);
  }
  if ((parsed.sequence as number) < maximumRunNumber) metadataError('sequence小于既有运行编号');

  const commandRunIds = Object.create(null) as StoredState['commandRunIds'];
  for (const [commandId, execution] of Object.entries(parsed.commandRunIds)) {
    if (!commandId.trim()) metadataError('commandId不能为空');
    if (typeof execution === 'string') {
      if (!runIds.has(execution)) metadataError(`旧版commandId引用不存在的运行：${commandId}`);
      commandRunIds[commandId] = execution;
      continue;
    }
    if (!isRecord(execution)
      || typeof execution.runId !== 'string'
      || !runIds.has(execution.runId)
      || typeof execution.fingerprint !== 'string'
      || !/^sha256:[0-9a-f]{64}$/.test(execution.fingerprint)) {
      metadataError(`commandId执行记录无效：${commandId}`);
    }
    commandRunIds[commandId] = execution as CommandExecution;
  }
  return {
    schemaVersion: 1,
    sequence: parsed.sequence as number,
    runs: parsed.runs as StandardizationRun[],
    commandRunIds,
  };
}

function requireRun(state: StoredState, runId: string) {
  const index = state.runs.findIndex((run) => run.runId === runId);
  if (index < 0) throw new Error(`标准化运行不存在：${runId}`);
  return { index, run: state.runs[index]! };
}

function assertRevision(run: StandardizationRun, expectedRevision: number) {
  if (run.revision !== expectedRevision) throw new Error('标准化运行revision已变化，请刷新后重试');
}

function sourceLabel(run: StandardizationRun, sourceId: string) {
  return run.sources.find((source) => source.sourceId === sourceId)?.sourceName ?? sourceId;
}

function nextRunStatus(run: StandardizationRun): StandardizationRun['status'] {
  return run.sources.every((source) => source.status === 'ALIGNED') ? 'READY_FOR_OUTPUT' : 'READY';
}

function firstUnresolvedConflict(run: StandardizationRun) {
  for (const source of run.sources) {
    const conflictId = source.introducedConflictIds.find((candidate) => !source.resolvedConflictIds.includes(candidate));
    if (conflictId) return { source, conflictId };
  }
  return undefined;
}

function sourceHasConflictFound(run: StandardizationRun, sourceId: string) {
  return run.timeline.some((event) => event.type === 'CONFLICT_FOUND' && event.sourceId === sourceId);
}

const resolutionStrategies = new Set([
  'KEEP_CURRENT', 'ACCEPT_INCOMING', 'MERGE', 'DEFER_AS_GAP',
]);

function resolutionError(detail: string): never {
  throw new Error(`冲突决定Artifact已损坏：${detail}`);
}

function parseResolutionArtifact(payload: string) {
  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    resolutionError('无法解析JSON');
  }
  if (!isRecord(parsed) || parsed.schemaVersion !== 1) resolutionError('schemaVersion必须为1');
  for (const field of [
    'resolutionId', 'runId', 'sourceId', 'reason', 'actorUserId', 'decidedAt', 'previewSha256',
  ]) {
    if (!isNonEmptyString(parsed[field])) resolutionError(`${field}不能为空`);
  }
  if (typeof parsed.strategy !== 'string' || !resolutionStrategies.has(parsed.strategy)) {
    resolutionError('strategy无效');
  }
  if (!isRecord(parsed.hunk) || !isNonEmptyString(parsed.hunk.conflictId)
    || !isNonEmptyString(parsed.hunk.hunkSha256)
    || !isRecord(parsed.hunk.current) || !isRecord(parsed.hunk.current.block)
    || !isRecord(parsed.hunk.incoming) || !isRecord(parsed.hunk.incoming.block)
    || !Array.isArray(parsed.hunk.corroborating)) {
    resolutionError('hunk身份无效');
  }
  if (parsed.hunk.current.role !== 'CURRENT' || parsed.hunk.incoming.role !== 'INCOMING'
    || parsed.hunk.corroborating.some((side) => !isRecord(side) || side.role !== 'CORROBORATING')) {
    resolutionError('Hunk side role无效');
  }
  const { hunkSha256, ...hunkContent } = parsed.hunk;
  if (hunkSha256 !== `sha256:${sha256HexSync(canonicalJson(hunkContent))}`) {
    resolutionError('hunkSha256不一致');
  }
  if (!isRecord(parsed.result)
    || !Array.isArray(parsed.result.blocks)
    || !Array.isArray(parsed.result.assertions)
    || !Array.isArray(parsed.result.objectDispositions)) {
    resolutionError('result结构无效');
  }
  if (!parsed.result.assertions.length || parsed.result.assertions.some((assertion) => (
    !isRecord(assertion) || assertion.provenance !== 'USER_CONFIRMED'
  ))) {
    resolutionError('assertion必须全部为USER_CONFIRMED');
  }
  if (!isRecord(parsed.structuredPatch)
    || parsed.structuredPatch.schemaVersion !== 1
    || parsed.structuredPatch.conflictId !== parsed.hunk.conflictId
    || !Array.isArray(parsed.structuredPatch.blockOperations)
    || !Array.isArray(parsed.structuredPatch.assertionOperations)
    || !Array.isArray(parsed.structuredPatch.objectDispositionOperations)) {
    resolutionError('structuredPatch结构无效');
  }
  const expectedPatch = {
    schemaVersion: 1,
    conflictId: parsed.hunk.conflictId,
    blockOperations: parsed.result.blocks.map((block) => ({ op: 'UPSERT', block })),
    assertionOperations: parsed.result.assertions.map((assertion) => ({ op: 'UPSERT', assertion })),
    objectDispositionOperations: parsed.result.objectDispositions.map((value) => ({ op: 'SET', value })),
  };
  if (canonicalJson(parsed.structuredPatch) !== canonicalJson(expectedPatch)) {
    resolutionError('structuredPatch不是result的唯一投影');
  }
  if (!Array.isArray(parsed.markdownDiff)
    || parsed.markdownDiff.some((line) => !isRecord(line)
      || !['UNCHANGED', 'REMOVED', 'ADDED'].includes(String(line.type))
      || typeof line.line !== 'string')
    || !parsed.markdownDiff.some((line) => isRecord(line) && line.type === 'REMOVED')
    || !parsed.markdownDiff.some((line) => isRecord(line) && line.type === 'ADDED')) {
    resolutionError('markdownDiff必须包含真实删除行与新增行');
  }
  if (!Array.isArray(parsed.provenanceSources) || parsed.provenanceSources.length < 2
    || !isRecord(parsed.provenanceSources[0]) || parsed.provenanceSources[0].role !== 'CURRENT'
    || !isRecord(parsed.provenanceSources[1]) || parsed.provenanceSources[1].role !== 'INCOMING') {
    resolutionError('provenanceSources缺少Current或Incoming');
  }
  const artifact = parsed as unknown as ConflictResolutionArtifact;
  const hunkSideLine = (side: typeof artifact.hunk.current) => (
    `- ${side.sourceName}｜${side.block.label}：${sourceDocumentBlockHumanText(side.block.value)}`
  );
  const beforeMarkdown = [
    `# ${artifact.hunk.title}`,
    '## Current',
    hunkSideLine(artifact.hunk.current),
    '## Incoming',
    hunkSideLine(artifact.hunk.incoming),
    ...(artifact.hunk.corroborating.length
      ? ['## Corroborating', ...artifact.hunk.corroborating.map(hunkSideLine)]
      : []),
  ].join('\n');
  const afterMarkdown = [
    `# ${artifact.hunk.title}`,
    '## Result',
    ...artifact.result.blocks.map((block) => `- ${block.label}：${sourceDocumentBlockHumanText(block.value)}`),
    '## Strategy',
    `- ${artifact.strategy}`,
    '## Object dispositions',
    ...artifact.result.objectDispositions.map(({ objectRef, disposition }) => (
      `- ${objectRef.kind}:${objectRef.objectId} → ${disposition}`
    )),
  ].join('\n');
  if (canonicalJson(artifact.markdownDiff)
    !== canonicalJson(diffSourceDocumentLines(beforeMarkdown, afterMarkdown))) {
    resolutionError('markdownDiff不是Hunk与Result的真实行级差异');
  }
  if (artifact.result.assertions.length !== artifact.result.blocks.length
    || artifact.result.assertions.some((assertion, index) => {
      const block = artifact.result.blocks[index];
      return !block || assertion.section !== block.section
        || assertion.statement !== `${block.label}：${sourceDocumentBlockHumanText(block.value)}`
        || canonicalJson(assertion.evidenceRefs) !== canonicalJson(block.evidenceRefs);
    })) {
    resolutionError('assertion不是result blocks的唯一投影');
  }
  const expectedCanonicalBlocks = artifact.strategy === 'KEEP_CURRENT'
    ? [artifact.hunk.current.block]
    : artifact.strategy === 'ACCEPT_INCOMING'
      ? [artifact.hunk.incoming.block]
      : [artifact.hunk.current.block];
  if (canonicalJson(artifact.result.blocks.slice(0, expectedCanonicalBlocks.length))
    !== canonicalJson(expectedCanonicalBlocks)
    || (['MERGE', 'DEFER_AS_GAP'].includes(artifact.strategy)
      && (artifact.result.blocks.length !== 2 || artifact.result.blocks[1]?.semanticKind !== 'GAP'))
    || (!['MERGE', 'DEFER_AS_GAP'].includes(artifact.strategy) && artifact.result.blocks.length !== 1)) {
    resolutionError('result blocks与策略不一致');
  }
  const expectedProvenance = [
    artifact.hunk.current, artifact.hunk.incoming, ...artifact.hunk.corroborating,
  ].map((side) => ({
    role: side.role,
    sourceId: side.sourceId,
    sourceName: side.sourceName,
    sourceClass: side.sourceClass,
    authority: side.authority,
    documentId: side.documentId,
    documentRevision: side.documentRevision,
    evidenceRefs: [...side.block.evidenceRefs],
    usage: side.role === 'CORROBORATING'
      ? 'CORROBORATION'
      : side.role === 'CURRENT'
        ? artifact.strategy === 'ACCEPT_INCOMING' ? 'PROVENANCE_ONLY' : 'CANONICAL'
        : artifact.strategy === 'ACCEPT_INCOMING' ? 'CANONICAL'
          : artifact.strategy === 'MERGE' || artifact.strategy === 'DEFER_AS_GAP'
            ? 'GAP_EVIDENCE'
            : 'PROVENANCE_ONLY',
  }));
  if (canonicalJson(artifact.provenanceSources) !== canonicalJson(expectedProvenance)) {
    resolutionError('provenanceSources不是Hunk与策略的唯一投影');
  }
  const previewContent = {
    hunk: parsed.hunk,
    strategy: parsed.strategy,
    result: parsed.result,
    structuredPatch: parsed.structuredPatch,
    markdownDiff: parsed.markdownDiff,
    provenanceSources: parsed.provenanceSources,
  };
  if (parsed.previewSha256 !== `sha256:${sha256HexSync(canonicalJson(previewContent))}`) {
    resolutionError('previewSha256不一致');
  }
  return artifact;
}

type AssistantTurnEnvelope = {
  schemaVersion: 1;
  turnId: string;
  runId: string;
  actorUserId: string;
  message: string;
  selection: Record<string, unknown>;
  knowledgeSourceRevisions: Array<{
    sourceId: string;
    documentId: string;
    documentRevision: number;
  }>;
  response: Record<string, unknown> & {
    kind: string;
    title: string;
    body: string[];
    proposalId?: string;
    proposalRef?: string;
  };
  createdAt: string;
};

type AssistantProposalEnvelope = {
  schemaVersion: 1;
  proposalId: string;
  runId: string;
  sourceId: string;
  documentId: string;
  documentRevision: number;
  blockId: string;
  previewSha256: string;
  createdBy: string;
  createdAt: string;
};

type AssistantConfirmationEnvelope = {
  schemaVersion: 1;
  runId: string;
  sourceId: string;
  proposalId: string;
  proposalRef: string;
  expectedPreviewSha256: string;
  beforeDocumentId: string;
  beforeDocumentRevision: number;
  documentId: string;
  documentRevision: number;
  blockId: string;
  actorUserId: string;
  confirmedAt: string;
};

type AssistantCancellationEnvelope = {
  schemaVersion: 1;
  cancellationId: string;
  runId: string;
  proposalId: string;
  documentId: string;
  documentRevision: number;
  cancelledBy: string;
  cancelledAt: string;
};

function assistantError(detail: string): never {
  throw new Error(`审阅助手Artifact已损坏：${detail}`);
}

function parseAssistantTurn(payload: string): AssistantTurnEnvelope {
  let parsed: unknown;
  try { parsed = JSON.parse(payload); } catch { assistantError('Turn无法解析JSON'); }
  if (!isRecord(parsed) || parsed.schemaVersion !== 1
    || !isNonEmptyString(parsed.turnId) || !isNonEmptyString(parsed.runId)
    || !isNonEmptyString(parsed.actorUserId) || !isNonEmptyString(parsed.message)
    || !isRecord(parsed.selection) || !Array.isArray(parsed.knowledgeSourceRevisions)
    || parsed.knowledgeSourceRevisions.some((revision) => (
      !isRecord(revision) || !isNonEmptyString(revision.sourceId)
      || !isNonEmptyString(revision.documentId)
      || !Number.isSafeInteger(revision.documentRevision) || (revision.documentRevision as number) < 1
    ))
    || new Set(parsed.knowledgeSourceRevisions.map((revision) => (
      isRecord(revision) ? revision.sourceId : undefined
    ))).size !== parsed.knowledgeSourceRevisions.length
    || !isRecord(parsed.response)
    || !isNonEmptyString(parsed.response.kind) || !isNonEmptyString(parsed.response.title)
    || !Array.isArray(parsed.response.body)
    || parsed.response.body.length > 8
    || parsed.response.body.some((paragraph) => typeof paragraph !== 'string' || [...paragraph].length > 240)
    || !isNonEmptyString(parsed.createdAt)) {
    assistantError('Turn结构或正文限制无效');
  }
  const response = parsed.response as Record<string, unknown>;
  const hasProposal = response.kind === 'PATCH_PREVIEW';
  if (hasProposal !== (isNonEmptyString(response.proposalId) && isNonEmptyString(response.proposalRef))) {
    assistantError('Patch Turn必须只引用完整Proposal');
  }
  if (hasProposal && !/^sha256:[0-9a-f]{64}$/.test(response.proposalRef as string)) {
    assistantError('Proposal内容引用无效');
  }
  return parsed as unknown as AssistantTurnEnvelope;
}

function parseAssistantProposal(payload: string): AssistantProposalEnvelope {
  let parsed: unknown;
  try { parsed = JSON.parse(payload); } catch { assistantError('Proposal无法解析JSON'); }
  if (!isRecord(parsed) || parsed.schemaVersion !== 1
    || !isNonEmptyString(parsed.proposalId) || !isNonEmptyString(parsed.runId)
    || !isNonEmptyString(parsed.sourceId) || !isNonEmptyString(parsed.documentId)
    || !Number.isSafeInteger(parsed.documentRevision) || (parsed.documentRevision as number) < 1
    || !isNonEmptyString(parsed.blockId)
    || !isNonEmptyString(parsed.previewSha256)
    || !/^sha256:[0-9a-f]{64}$/.test(parsed.previewSha256 as string)
    || !isNonEmptyString(parsed.createdBy) || !isNonEmptyString(parsed.createdAt)
    || !isRecord(parsed.change) || !isRecord(parsed.beforeBlock) || !isRecord(parsed.preview)) {
    assistantError('Proposal结构或身份无效');
  }
  return parsed as unknown as AssistantProposalEnvelope;
}

function parseAssistantConfirmation(payload: string): AssistantConfirmationEnvelope {
  let parsed: unknown;
  try { parsed = JSON.parse(payload); } catch { assistantError('确认Artifact无法解析JSON'); }
  if (!isRecord(parsed) || parsed.schemaVersion !== 1
    || !isNonEmptyString(parsed.runId) || !isNonEmptyString(parsed.sourceId)
    || !isNonEmptyString(parsed.proposalId)
    || !isNonEmptyString(parsed.proposalRef) || !/^sha256:[0-9a-f]{64}$/.test(parsed.proposalRef as string)
    || !isNonEmptyString(parsed.expectedPreviewSha256)
    || !/^sha256:[0-9a-f]{64}$/.test(parsed.expectedPreviewSha256 as string)
    || !isNonEmptyString(parsed.beforeDocumentId)
    || !Number.isSafeInteger(parsed.beforeDocumentRevision) || (parsed.beforeDocumentRevision as number) < 1
    || !isNonEmptyString(parsed.documentId)
    || !Number.isSafeInteger(parsed.documentRevision) || (parsed.documentRevision as number) < 2
    || !isNonEmptyString(parsed.blockId) || !isNonEmptyString(parsed.actorUserId)
    || !isNonEmptyString(parsed.confirmedAt)) {
    assistantError('确认Artifact结构或身份无效');
  }
  return parsed as unknown as AssistantConfirmationEnvelope;
}

function parseAssistantCancellation(payload: string): AssistantCancellationEnvelope {
  let parsed: unknown;
  try { parsed = JSON.parse(payload); } catch { assistantError('取消Artifact无法解析JSON'); }
  if (!isRecord(parsed) || parsed.schemaVersion !== 1
    || !isNonEmptyString(parsed.cancellationId) || !isNonEmptyString(parsed.runId)
    || !isNonEmptyString(parsed.proposalId) || !isNonEmptyString(parsed.documentId)
    || !Number.isSafeInteger(parsed.documentRevision) || (parsed.documentRevision as number) < 1
    || !isNonEmptyString(parsed.cancelledBy) || !isNonEmptyString(parsed.cancelledAt)) {
    assistantError('取消Artifact结构或身份无效');
  }
  return parsed as unknown as AssistantCancellationEnvelope;
}

export function createStandardizationRunRuntime(input: {
  metadataStorage?: MetadataStorage;
  metadataStore?: StandardizationMetadataStore;
  contentStore: ContentAddressedStore;
  deliveryCapability?: object;
  resolutionArtifactValidator?: (
    artifact: ConflictResolutionArtifact,
    run: StandardizationRun,
  ) => void | Promise<void>;
  corroborationReceiptValidator?: (
    receipt: { runId: string; sourceId: string; conflictId: string },
    run: StandardizationRun,
  ) => void | Promise<void>;
  corroborationReceiptSetValidator?: (
    projection: { runId: string; sourceId: string; conflictIds: string[] },
    run: StandardizationRun,
  ) => void | Promise<void>;
  assistantArtifactValidator?: (
    turn: unknown,
    proposal: unknown | undefined,
    run: StandardizationRun,
    projection: { eventIndex: number },
  ) => void | Promise<void>;
  assistantPatchConfirmationValidator?: (
    confirmation: unknown,
    run: StandardizationRun,
    projection: {
      introducedConflictIds: string[];
      changedBlockIds: string[];
      changedSections: string[];
      affectedObjectIds: string[];
    },
  ) => void | Promise<void>;
  now?: () => string;
}): StandardizationRunRuntime {
  const now = input.now ?? (() => new Date().toISOString());
  const metadataStore = input.metadataStore ?? createDefaultStandardizationMetadataStore({
    metadataStorage: input.metadataStorage,
  });

  async function readStoredPayload(ref: `sha256:${string}`, label: string) {
    const payload = await input.contentStore.get(ref);
    if (payload === null || `sha256:${sha256HexSync(payload)}` !== ref) {
      assistantError(`${label}内容不存在或校验和不一致`);
    }
    return payload;
  }

  async function registerAssistantKnowledgeDocument(
    event: StandardizationTimelineEvent,
    revisions: Map<string, { sourceId: string; documentId: string; documentRevision: number }>,
  ) {
    if (event.type !== 'DOCUMENT_GENERATED' && event.type !== 'DOCUMENT_REVISED') return;
    if (!event.sourceId || !event.sourceDocumentId || !event.sourceDocumentRevision) {
      assistantError(`Document事件不能登记Assistant事实revision：${event.eventId}`);
    }
    revisions.set(event.sourceId, {
      sourceId: event.sourceId,
      documentId: event.sourceDocumentId,
      documentRevision: event.sourceDocumentRevision,
    });
  }

  function validateAssistantKnowledgeAtEvent(
    turn: AssistantTurnEnvelope,
    run: StandardizationRun,
    revisions: Map<string, { sourceId: string; documentId: string; documentRevision: number }>,
  ) {
    const expected = run.sources.flatMap((source) => {
      const revision = revisions.get(source.sourceId);
      return revision ? [revision] : [];
    });
    if (canonicalJson(turn.knowledgeSourceRevisions) !== canonicalJson(expected)) {
      assistantError('Turn事实投影不属于该事件时点已生成的来源文档');
    }
  }

  async function assistantKnowledgeBeforeEvent(run: StandardizationRun, eventIndex: number) {
    const revisions = new Map<string, { sourceId: string; documentId: string; documentRevision: number }>();
    for (const event of run.timeline.slice(0, eventIndex)) {
      await registerAssistantKnowledgeDocument(event, revisions);
    }
    return revisions;
  }

  async function validateAssistantEventIndexes(run: StandardizationRun) {
    const revisions = new Map<string, { sourceId: string; documentId: string; documentRevision: number }>();
    const turnIds = new Set<string>();
    const proposals = new Map<string, { documentId: string; documentRevision: number; eventIndex: number }>();
    const latestByDocument = new Map<string, string>();
    const confirmed = new Set<string>();
    const cancelled = new Set<string>();
    for (let index = 0; index < run.timeline.length; index += 1) {
      const event = run.timeline[index]!;
      await registerAssistantKnowledgeDocument(event, revisions);
      if (event.type === 'ASSISTANT_TURN_RECORDED') {
        if (turnIds.has(event.assistantTurnId!)) assistantError(`Turn事件索引重复：${event.eventId}`);
        turnIds.add(event.assistantTurnId!);
        const expected = run.sources.flatMap((source) => {
          const revision = revisions.get(source.sourceId);
          return revision ? [revision] : [];
        });
        if (canonicalJson(event.assistantKnowledgeSourceRevisions) !== canonicalJson(expected)) {
          assistantError(`Turn事实投影不属于该事件时点已生成的来源文档：${event.eventId}`);
        }
        if (event.assistantProposalId) {
          if (proposals.has(event.assistantProposalId)
            || !expected.some((revision) => (
              revision.documentId === event.assistantDocumentId
              && revision.documentRevision === event.assistantDocumentRevision
            ))) assistantError(`Proposal事件索引不属于Turn事实revision：${event.eventId}`);
          proposals.set(event.assistantProposalId, {
            documentId: event.assistantDocumentId!,
            documentRevision: event.assistantDocumentRevision!,
            eventIndex: index,
          });
          latestByDocument.set(event.assistantDocumentId!, event.assistantProposalId);
        }
        continue;
      }
      if (event.type === 'ASSISTANT_PATCH_CANCELLED') {
        const proposal = proposals.get(event.assistantProposalId!);
        const cancellation = parseAssistantCancellation(await readStoredPayload(
          event.payloadRef, `取消 ${event.eventId}`,
        ));
        if (!proposal || proposal.eventIndex >= index || confirmed.has(event.assistantProposalId!)
          || cancelled.has(event.assistantProposalId!)
          || latestByDocument.get(proposal.documentId) !== event.assistantProposalId
          || cancellation.runId !== run.runId || cancellation.proposalId !== event.assistantProposalId
          || cancellation.documentId !== proposal.documentId
          || cancellation.documentRevision !== proposal.documentRevision
          || cancellation.cancelledBy !== event.actor.userId
          || event.assistantDocumentId !== proposal.documentId
          || event.assistantDocumentRevision !== proposal.documentRevision) {
          assistantError(`取消事件没有回链最新Proposal：${event.eventId}`);
        }
        cancelled.add(event.assistantProposalId!);
        continue;
      }
      if (event.type !== 'ASSISTANT_PATCH_CONFIRMED') continue;
      const proposal = proposals.get(event.assistantProposalId!);
      if (!proposal || proposal.eventIndex >= index || confirmed.has(event.assistantProposalId!)
        || cancelled.has(event.assistantProposalId!)
        || latestByDocument.get(proposal.documentId) !== event.assistantProposalId
        || proposal.documentId !== event.assistantDocumentId
        || proposal.documentRevision !== event.assistantDocumentRevision
        || run.timeline[index + 1]?.type !== 'DOCUMENT_REVISED'
        || run.timeline[index + 1]?.sourceId !== event.sourceId) {
        assistantError(`确认事件索引没有回链最新Proposal或相邻revision：${event.eventId}`);
      }
      confirmed.add(event.assistantProposalId!);
    }
  }

  async function readAssistantArtifactProjection(
    run: StandardizationRun,
    targetProposalIds?: ReadonlySet<string>,
  ) {
    const proposals = new Map<string, {
      proposal: AssistantProposalEnvelope;
      proposalRef: `sha256:${string}`;
      eventIndex: number;
    }>();
    const latestByDocument = new Map<string, string>();
    const confirmed = new Set<string>();
    const cancelled = new Set<string>();
    const knowledgeRevisions = new Map<
      string,
      { sourceId: string; documentId: string; documentRevision: number }
    >();
    for (let index = 0; index < run.timeline.length; index += 1) {
      const event = run.timeline[index]!;
      await registerAssistantKnowledgeDocument(event, knowledgeRevisions);
      if (event.type === 'ASSISTANT_TURN_RECORDED') {
        if (event.assistantProposalId && event.assistantDocumentId) {
          latestByDocument.set(event.assistantDocumentId, event.assistantProposalId);
        }
        if (targetProposalIds && (!event.assistantProposalId
          || !targetProposalIds.has(event.assistantProposalId))) continue;
        const turn = parseAssistantTurn(await readStoredPayload(event.payloadRef, `Turn ${event.eventId}`));
        if (turn.runId !== run.runId || turn.actorUserId !== event.actor.userId) {
          assistantError(`Turn身份与事件不一致：${event.eventId}`);
        }
        if (turn.turnId !== event.assistantTurnId
          || canonicalJson(turn.knowledgeSourceRevisions)
            !== canonicalJson(event.assistantKnowledgeSourceRevisions)) {
          assistantError(`Turn正文与事件索引不一致：${event.eventId}`);
        }
        validateAssistantKnowledgeAtEvent(turn, run, knowledgeRevisions);
        if (turn.response.kind !== 'PATCH_PREVIEW') {
          if (!input.assistantArtifactValidator) {
            assistantError('Runtime未配置Story Assistant Artifact校验器');
          }
          await input.assistantArtifactValidator(turn, undefined, run, { eventIndex: index });
          continue;
        }
        const proposalRef = turn.response.proposalRef as `sha256:${string}`;
        const proposal = parseAssistantProposal(await readStoredPayload(
          proposalRef, `Proposal ${turn.response.proposalId}`,
        ));
        if (proposal.proposalId !== turn.response.proposalId || proposal.runId !== run.runId
          || proposal.createdBy !== event.actor.userId || proposals.has(proposal.proposalId)) {
          assistantError(`Proposal身份重复或与Turn不一致：${event.eventId}`);
        }
        if (proposal.proposalId !== event.assistantProposalId
          || proposal.documentId !== event.assistantDocumentId
          || proposal.documentRevision !== event.assistantDocumentRevision) {
          assistantError(`Proposal正文与事件索引不一致：${event.eventId}`);
        }
        proposals.set(proposal.proposalId, { proposal, proposalRef, eventIndex: index });
        if (!input.assistantArtifactValidator) {
          assistantError('Runtime未配置Story Assistant Artifact校验器');
        }
        await input.assistantArtifactValidator(turn, proposal, run, { eventIndex: index });
        continue;
      }
      if (event.type === 'ASSISTANT_PATCH_CANCELLED') {
        if (targetProposalIds && (!event.assistantProposalId
          || !targetProposalIds.has(event.assistantProposalId))) continue;
        const cancellation = parseAssistantCancellation(await readStoredPayload(
          event.payloadRef, `取消 ${event.eventId}`,
        ));
        const entry = proposals.get(cancellation.proposalId);
        if (!entry || confirmed.has(cancellation.proposalId) || cancelled.has(cancellation.proposalId)
          || entry.eventIndex >= index
          || latestByDocument.get(entry.proposal.documentId) !== cancellation.proposalId
          || cancellation.runId !== run.runId || cancellation.proposalId !== event.assistantProposalId
          || cancellation.documentId !== event.assistantDocumentId
          || cancellation.documentRevision !== event.assistantDocumentRevision
          || cancellation.documentId !== entry.proposal.documentId
          || cancellation.documentRevision !== entry.proposal.documentRevision
          || cancellation.cancelledBy !== event.actor.userId) {
          assistantError(`取消事件没有回链最新Proposal：${event.eventId}`);
        }
        cancelled.add(cancellation.proposalId);
        continue;
      }
      if (event.type !== 'ASSISTANT_PATCH_CONFIRMED') continue;
      if (targetProposalIds && (!event.assistantProposalId
        || !targetProposalIds.has(event.assistantProposalId))) continue;
      const confirmation = parseAssistantConfirmation(await readStoredPayload(
        event.payloadRef, `确认 ${event.eventId}`,
      ));
      const entry = proposals.get(confirmation.proposalId);
      const revisedEvent = run.timeline[index + 1];
      if (!entry || confirmed.has(confirmation.proposalId) || cancelled.has(confirmation.proposalId)
        || entry.eventIndex >= index
        || latestByDocument.get(entry.proposal.documentId) !== confirmation.proposalId
        || confirmation.runId !== run.runId || confirmation.sourceId !== event.sourceId
        || confirmation.actorUserId !== event.actor.userId
        || confirmation.proposalRef !== entry.proposalRef
        || confirmation.expectedPreviewSha256 !== entry.proposal.previewSha256
        || confirmation.sourceId !== entry.proposal.sourceId
        || confirmation.beforeDocumentId !== entry.proposal.documentId
        || confirmation.beforeDocumentRevision !== entry.proposal.documentRevision
        || confirmation.documentRevision !== confirmation.beforeDocumentRevision + 1
        || confirmation.blockId !== entry.proposal.blockId
        || revisedEvent?.type !== 'DOCUMENT_REVISED'
        || revisedEvent.sourceId !== event.sourceId
        || revisedEvent.actor.userId !== event.actor.userId
        || revisedEvent.createdAt !== event.createdAt) {
        assistantError(`确认事件没有回链最新Proposal或相邻revision：${event.eventId}`);
      }
      const revisedPayload = await readStoredPayload(revisedEvent.payloadRef, `Revision ${revisedEvent.eventId}`);
      let revised: unknown;
      try { revised = JSON.parse(revisedPayload); } catch { assistantError(`Revision无法解析JSON：${revisedEvent.eventId}`); }
      if (!isRecord(revised)
        || revised.beforeDocumentId !== confirmation.beforeDocumentId
        || revised.beforeDocumentRevision !== confirmation.beforeDocumentRevision
        || revised.documentId !== confirmation.documentId
        || revised.documentRevision !== confirmation.documentRevision
        || !Array.isArray(revised.changedBlockIds)
        || !revised.changedBlockIds.includes(confirmation.blockId)
        || !Array.isArray(revised.changedSections)
        || revised.changedSections.some((value) => !isNonEmptyString(value))
        || !Array.isArray(revised.affectedObjectIds)
        || revised.affectedObjectIds.some((value) => !isNonEmptyString(value))
        || !Array.isArray(revised.affectedConflictIds)
        || revised.affectedConflictIds.some((value) => !isNonEmptyString(value))) {
        assistantError(`确认事件与相邻DOCUMENT_REVISED身份不一致：${event.eventId}`);
      }
      if (!input.assistantPatchConfirmationValidator) {
        assistantError('Runtime未配置Story Assistant确认revision校验器');
      }
      await input.assistantPatchConfirmationValidator(confirmation, run, {
        introducedConflictIds: revised.affectedConflictIds as string[],
        changedBlockIds: revised.changedBlockIds as string[],
        changedSections: revised.changedSections as string[],
        affectedObjectIds: revised.affectedObjectIds as string[],
      });
      confirmed.add(confirmation.proposalId);
    }
    return { proposals, latestByDocument, confirmed, cancelled };
  }

  async function validateStoredDeliveryEvents(run: StandardizationRun) {
    const entries: Array<{ event: StandardizationTimelineEvent; payload: Record<string, unknown> }> = [];
    for (const event of run.timeline.filter((candidate) => deliveryEventTypes.has(candidate.type))) {
      const payload = await readStoredPayload(event.payloadRef, `${event.type} ${event.eventId}`);
      const parsed = parseDeliveryEventPayload(
        event.type as Parameters<typeof parseDeliveryEventPayload>[0],
        payload,
        run,
      );
      if (event.type === 'DELIVERABLE_GENERATED') {
        const expectedIndex = {
          coreSha256: parsed.coreSha256,
          entries: ([
            'sourceManifestRef', 'mergedDocumentRef', 'decisionManifestRef',
            'governanceAppendixRef', 'modelingArtifactRef', 'zeroDeltaReportRef',
          ] as const).map((key) => ({ key, contentRef: parsed[key] })),
        };
        if (canonicalJson(event.deliveryContentIndex) !== canonicalJson(expectedIndex)) {
          metadataError(`交付生成事件review-window索引与内容载荷不一致：${event.eventId}`);
        }
      }
      entries.push({ event, payload: parsed });
    }
    validateDeliveryEventAuditChain(run, entries);
  }

  async function readSnapshot() {
    const snapshot = await metadataStore.read();
    const state = load(snapshot.raw);
    for (const run of state.runs) {
      await validateAssistantEventIndexes(run);
      const projected = new Map(run.sources.map((source) => [source.sourceId, [] as string[]]));
      const corroboratedReceipts = new Set<string>();
      const corroboratedBySource = new Map(run.sources.map((source) => [source.sourceId, [] as string[]]));
      for (let eventIndex = 0; eventIndex < run.timeline.length; eventIndex += 1) {
        const event = run.timeline[eventIndex]!;
        if (event.type === 'ASSISTANT_TURN_RECORDED') continue;
        if (event.type === 'CONFLICT_CORROBORATED') {
          const payload = await input.contentStore.get(event.payloadRef);
          if (payload === null || `sha256:${sha256HexSync(payload)}` !== event.payloadRef) {
            resolutionError(`佐证事件载荷不存在或校验和不一致：${event.eventId}`);
          }
          let receipt: unknown;
          try { receipt = JSON.parse(payload); } catch { resolutionError(`佐证事件载荷无法解析：${event.eventId}`); }
          const previouslyResolved = isRecord(receipt) && isNonEmptyString(receipt.conflictId)
            && [...projected.values()].some((conflictIds) => conflictIds.includes(receipt.conflictId as string));
          if (!isRecord(receipt) || receipt.sourceId !== event.sourceId
            || !isNonEmptyString(receipt.conflictId)
            || !previouslyResolved) {
            resolutionError(`佐证事件没有回链此前已验证的冲突：${event.eventId}`);
          }
          const receiptIdentity = `${event.sourceId}:${receipt.conflictId}`;
          if (corroboratedReceipts.has(receiptIdentity)) {
            resolutionError(`佐证事件重复：${event.eventId}`);
          }
          corroboratedReceipts.add(receiptIdentity);
          corroboratedBySource.get(event.sourceId!)!.push(receipt.conflictId);
          if (!input.corroborationReceiptValidator) {
            resolutionError('Runtime未配置Story佐证来源校验器');
          }
          await input.corroborationReceiptValidator({
            runId: run.runId,
            sourceId: event.sourceId!,
            conflictId: receipt.conflictId,
          }, run);
          continue;
        }
        if (event.type !== 'CONFLICT_RESOLVED' && event.type !== 'CONFLICT_DECISION_REPLACED') continue;
        const payload = await input.contentStore.get(event.payloadRef);
        if (payload === null || `sha256:${sha256HexSync(payload)}` !== event.payloadRef) {
          resolutionError(`事件载荷不存在或校验和不一致：${event.eventId}`);
        }
        const artifact = parseResolutionArtifact(payload);
        if (!input.resolutionArtifactValidator) {
          resolutionError('Runtime未配置Story纯投影校验器');
        }
        await input.resolutionArtifactValidator(artifact, run);
        if (artifact.runId !== run.runId || artifact.actorUserId !== event.actor.userId) {
          resolutionError(`事件身份与Artifact不一致：${event.eventId}`);
        }
        const conflictId = artifact.hunk.conflictId as string;
        const source = run.sources.find((candidate) => candidate.sourceId === artifact.sourceId);
        const values = source ? projected.get(source.sourceId) : undefined;
        if (event.type === 'CONFLICT_DECISION_REPLACED') {
          if (event.sourceId !== undefined || event.conflictId !== conflictId
            || !source || !values || !values.includes(conflictId)
            || artifact.resolutionId !== `replacement:${run.runId}:${conflictId}:${event.sequence}`) {
            resolutionError(`取代决定事件身份或前序决定无效：${event.eventId}`);
          }
          continue;
        }
        const foundBefore = run.timeline.slice(0, eventIndex).some((candidate) => (
          candidate.type === 'CONFLICT_FOUND' && candidate.sourceId === source?.sourceId
        ));
        if (artifact.sourceId !== event.sourceId
          || artifact.resolutionId !== `resolution:${run.runId}:${conflictId}`
          || !source || !values || !foundBefore
          || source.introducedConflictIds[values.length] !== conflictId || values.includes(conflictId)) {
          resolutionError(`事件冲突身份未知或重复：${event.eventId}`);
        }
        values.push(conflictId);
      }
      if (corroboratedReceipts.size && !input.corroborationReceiptSetValidator) {
        resolutionError('Runtime未配置Story佐证集合校验器');
      }
      if (input.corroborationReceiptSetValidator) {
        for (const source of run.sources) {
          await input.corroborationReceiptSetValidator({
            runId: run.runId,
            sourceId: source.sourceId,
            conflictIds: corroboratedBySource.get(source.sourceId)!,
          }, run);
        }
      }
      for (const source of run.sources) {
        if (canonicalJson(source.resolvedConflictIds) !== canonicalJson(projected.get(source.sourceId))) {
          resolutionError(`resolvedConflictIds不是已校验事件投影：${run.runId}/${source.sourceId}`);
        }
      }
      await validateStoredDeliveryEvents(run);
    }
    return { snapshot, state };
  }

  async function commit(snapshot: StandardizationMetadataSnapshot, state: StoredState) {
    if (!await metadataStore.compareAndSet(snapshot.version, JSON.stringify(state))) {
      throw new Error('运行已被其他窗口更新');
    }
  }

  async function appendEvents(
    run: StandardizationRun,
    actor: StandardizationRunCommand['actor'],
    values: Array<{
      type: StandardizationTimelineEvent['type'];
      sourceId?: string;
      conflictId?: string;
      sourceDocumentId?: string;
      sourceDocumentRevision?: number;
      assistantTurnId?: string;
      assistantProposalId?: string;
      assistantDocumentId?: string;
      assistantDocumentRevision?: number;
      assistantKnowledgeSourceRevisions?: StandardizationTimelineEvent['assistantKnowledgeSourceRevisions'];
      deliveryContentIndex?: StandardizationTimelineEvent['deliveryContentIndex'];
      payload: string;
    }>,
  ) {
    const payloadRefs = await Promise.all(values.map((value) => input.contentStore.put(value.payload)));
    for (let index = 0; index < values.length; index += 1) {
      const expected = `sha256:${sha256HexSync(values[index]!.payload)}`;
      if (payloadRefs[index] !== expected) throw new Error('内容存储返回了错误的事件引用');
    }
    const createdAt = now();
    const offset = run.timeline.length;
    run.timeline.push(...values.map((value, index): StandardizationTimelineEvent => {
      const sequence = offset + index + 1;
      return {
        eventId: `${run.runId}:event:${sequence}`,
        runId: run.runId,
        sequence,
        type: value.type,
        ...(value.sourceId ? { sourceId: value.sourceId } : {}),
        ...(value.conflictId ? { conflictId: value.conflictId } : {}),
        ...(value.sourceDocumentId ? { sourceDocumentId: value.sourceDocumentId } : {}),
        ...(value.sourceDocumentRevision
          ? { sourceDocumentRevision: value.sourceDocumentRevision } : {}),
        ...(value.assistantTurnId ? { assistantTurnId: value.assistantTurnId } : {}),
        ...(value.assistantProposalId ? { assistantProposalId: value.assistantProposalId } : {}),
        ...(value.assistantDocumentId ? { assistantDocumentId: value.assistantDocumentId } : {}),
        ...(value.assistantDocumentRevision
          ? { assistantDocumentRevision: value.assistantDocumentRevision } : {}),
        ...(value.assistantKnowledgeSourceRevisions ? {
          assistantKnowledgeSourceRevisions: clone(value.assistantKnowledgeSourceRevisions),
        } : {}),
        ...(value.deliveryContentIndex ? {
          deliveryContentIndex: clone(value.deliveryContentIndex),
        } : {}),
        actor: clone(actor),
        payloadRef: payloadRefs[index]!,
        createdAt,
      };
    }));
  }

  async function finishCommand(
    snapshot: StandardizationMetadataSnapshot,
    state: StoredState,
    index: number,
    run: StandardizationRun,
    command: StandardizationRunCommand,
  ) {
    run.revision += 1;
    run.updatedAt = now();
    state.runs[index] = run;
    state.commandRunIds[command.commandId] = {
      runId: run.runId,
      fingerprint: commandFingerprint(command),
    };
    await commit(snapshot, state);
    return clone(run);
  }

  return {
    async read(runId) {
      const { state } = await readSnapshot();
      return clone(state.runs.find((run) => run.runId === runId) ?? null);
    },

    async readMetadata(runId) {
      const metadata = await metadataStore.read();
      const state = load(metadata.raw);
      return clone(state.runs.find((run) => run.runId === runId) ?? null);
    },

    async readEventPayload(runId, eventId) {
      const metadata = await metadataStore.read();
      const state = load(metadata.raw);
      const { run } = requireRun(state, runId);
      const event = run.timeline.find((candidate) => candidate.eventId === eventId);
      if (!event) throw new Error(`标准化运行事件不存在：${eventId}`);
      const payload = await input.contentStore.get(event.payloadRef);
      if (payload === null) throw new Error(`标准化运行事件载荷不存在或已损坏：${eventId}`);
      if (`sha256:${sha256HexSync(payload)}` !== event.payloadRef) {
        throw new Error(`标准化运行事件载荷校验和不一致：${eventId}`);
      }
      return payload;
    },

    async validateAssistantPatchConfirmations(runId, proposalIds) {
      if (!Array.isArray(proposalIds) || proposalIds.some((proposalId) => !proposalId.trim())
        || new Set(proposalIds).size !== proposalIds.length) {
        assistantError('待校验确认Proposal identity为空或重复');
      }
      if (!proposalIds.length) return;
      const { state } = await readSnapshot();
      const { run } = requireRun(state, runId);
      const requested = new Set(proposalIds);
      const projection = await readAssistantArtifactProjection(run, requested);
      for (const proposalId of requested) {
        if (!projection.confirmed.has(proposalId)) {
          assistantError(`当前历史页Proposal没有完整确认投影：${proposalId}`);
        }
      }
    },

    async execute(command) {
      if ((command.type === 'MARK_DELIVERABLE_GENERATED'
        || command.type === 'MARK_REVIEW_SUBMITTED'
        || command.type === 'MARK_DELIVERABLE_FROZEN'
        || command.type === 'MARK_MODELING_HANDOFF_COMPLETED')
        && (!input.deliveryCapability || command.deliveryCapability !== input.deliveryCapability)) {
        throw new Error('交付事件只能由标准化交付深模块写入');
      }
      const { snapshot, state } = await readSnapshot();
        if (typeof command.commandId !== 'string' || !command.commandId.trim()) {
          throw new Error('commandId不能为空');
        }
        if (!command.actor || typeof command.actor.userId !== 'string' || !command.actor.userId.trim()) {
          throw new Error('命令操作者不能为空');
        }
        const priorExecution = Object.hasOwn(state.commandRunIds, command.commandId)
          ? state.commandRunIds[command.commandId]
          : undefined;
        if (priorExecution) {
          if (typeof priorExecution === 'string') {
            throw new Error('旧版commandId记录缺少指纹，无法确认幂等，请使用新的commandId');
          }
          if (priorExecution.fingerprint !== commandFingerprint(command)) {
            throw new Error('commandId已用于不同命令，不能伪装成幂等重试');
          }
          if (command.type !== 'CREATE_RUN' && command.runId !== priorExecution.runId) {
            throw new Error('commandId已用于其他标准化运行');
          }
          return clone(requireRun(state, priorExecution.runId).run);
        }

        if (command.type === 'CREATE_RUN') {
          const existing = state.runs.find((run) => run.projectId === command.projectId && run.batchId === command.batchId);
          if (existing) {
            state.commandRunIds[command.commandId] = {
              runId: existing.runId,
              fingerprint: commandFingerprint(command),
            };
            await commit(snapshot, state);
            return clone(existing);
          }
          if (command.expectedRevision !== 0) throw new Error('创建标准化运行的expectedRevision必须为0');
          if (!command.projectId.trim() || !command.scenarioKey.trim() || !command.batchId.trim()) {
            throw new Error('项目、故事和证据批次不能为空');
          }
          if (!command.sources.length) throw new Error('标准化运行至少需要一个来源');
          const sourceIds = command.sources.map((source) => source.sourceId);
          if (sourceIds.some((sourceId) => !sourceId.trim()) || new Set(sourceIds).size !== sourceIds.length) {
            throw new Error('标准化运行的来源标识不能为空或重复');
          }
          if (command.sources.some((source) => !source.sourceName.trim())) throw new Error('标准化运行的来源名称不能为空');
          if (command.sources.some((source) => source.snapshotId !== undefined && !source.snapshotId.trim())) {
            throw new Error('标准化运行的来源快照标识不能为空');
          }
          state.sequence += 1;
          const createdAt = now();
          const run: StandardizationRun = {
            schemaVersion: 1,
            runId: `standardization-run-${state.sequence}`,
            projectId: command.projectId,
            scenarioKey: command.scenarioKey,
            batchId: command.batchId,
            revision: 0,
            status: 'READY',
            sources: command.sources.map((source, index) => ({
              ...clone(source), order: index + 1, status: 'PENDING',
              introducedConflictIds: [], resolvedConflictIds: [],
            })),
            timeline: [],
            createdBy: command.actor.userId,
            createdAt,
            updatedAt: createdAt,
          };
          state.runs.push(run);
          state.commandRunIds[command.commandId] = {
            runId: run.runId,
            fingerprint: commandFingerprint(command),
          };
          await commit(snapshot, state);
          return clone(run);
        }

        const current = requireRun(state, command.runId);
        assertRevision(current.run, command.expectedRevision);
        const run = clone(current.run);

        switch (command.type) {
        case 'RECORD_ASSISTANT_TURN': {
          if (run.status === 'FROZEN' || run.status === 'HANDED_OFF') {
            throw new Error('已冻结或已交接的运行不能继续记录审阅助手Turn');
          }
          const turn = parseAssistantTurn(command.payload);
          if (turn.runId !== run.runId || turn.actorUserId !== command.actor.userId) {
            throw new Error('审阅助手Turn与命令身份不一致');
          }
          validateAssistantKnowledgeAtEvent(
            turn,
            run,
            await assistantKnowledgeBeforeEvent(run, run.timeline.length),
          );
          let parsedProposal: AssistantProposalEnvelope | undefined;
          if (turn.response.kind === 'PATCH_PREVIEW') {
            if (!command.proposalPayload) assistantError('Patch Turn缺少完整Proposal正文');
            const proposalRef = `sha256:${sha256HexSync(command.proposalPayload)}`;
            if (turn.response.proposalRef !== proposalRef) assistantError('Turn引用的Proposal正文不一致');
            parsedProposal = parseAssistantProposal(command.proposalPayload);
            if (parsedProposal.proposalId !== turn.response.proposalId
              || parsedProposal.runId !== run.runId || parsedProposal.createdBy !== command.actor.userId) {
              assistantError('Proposal与Turn或命令身份不一致');
            }
            const storedRef = await input.contentStore.put(command.proposalPayload);
            if (storedRef !== proposalRef) throw new Error('内容存储返回了错误的Proposal引用');
          } else if (command.proposalPayload !== undefined) {
            assistantError('非Patch Turn不能附带Proposal正文');
          }
          if (!input.assistantArtifactValidator) {
            assistantError('Runtime未配置Story Assistant Artifact校验器');
          }
          await input.assistantArtifactValidator(turn, parsedProposal, run, {
            eventIndex: run.timeline.length,
          });
          await appendEvents(run, command.actor, [{
            type: 'ASSISTANT_TURN_RECORDED', payload: command.payload,
            assistantTurnId: turn.turnId,
            assistantKnowledgeSourceRevisions: turn.knowledgeSourceRevisions,
            ...(parsedProposal ? {
              assistantProposalId: parsedProposal.proposalId,
              assistantDocumentId: parsedProposal.documentId,
              assistantDocumentRevision: parsedProposal.documentRevision,
            } : {}),
          }]);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'CANCEL_ASSISTANT_PATCH': {
          const ready = run.sources.find((source) => source.status === 'DOCUMENT_READY');
          if (run.status !== 'REVIEWING_DOCUMENT' || !ready?.documentId || !ready.documentRevision) {
            throw new Error('当前没有可取消建议的待审阅文档');
          }
          if (ready.documentId !== command.documentId
            || ready.documentRevision !== command.documentRevision) {
            throw new Error('助手Proposal对应的来源文档revision已变化');
          }
          const projection = await readAssistantArtifactProjection(run);
          const entry = projection.proposals.get(command.proposalId);
          if (!entry || projection.confirmed.has(command.proposalId)
            || projection.cancelled.has(command.proposalId)
            || projection.latestByDocument.get(command.documentId) !== command.proposalId
            || entry.proposal.documentId !== command.documentId
            || entry.proposal.documentRevision !== command.documentRevision) {
            throw new Error('只能取消当前文档最新一份未决定Proposal');
          }
          const cancellation = parseAssistantCancellation(command.payload);
          if (cancellation.runId !== run.runId || cancellation.proposalId !== command.proposalId
            || cancellation.documentId !== command.documentId
            || cancellation.documentRevision !== command.documentRevision
            || cancellation.cancelledBy !== command.actor.userId) {
            throw new Error('助手Proposal取消Artifact与命令身份不一致');
          }
          await appendEvents(run, command.actor, [{
            type: 'ASSISTANT_PATCH_CANCELLED', payload: command.payload,
            assistantProposalId: command.proposalId,
            assistantDocumentId: command.documentId,
            assistantDocumentRevision: command.documentRevision,
          }]);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'CONFIRM_ASSISTANT_PATCH': {
          const ready = run.sources.find((source) => source.status === 'DOCUMENT_READY');
          if (run.status !== 'REVIEWING_DOCUMENT' || !ready) {
            throw new Error('当前没有待审阅的来源文档');
          }
          if (ready.resolvedConflictIds.length) {
            throw new Error('已保存来源差异后不能修改来源文档');
          }
          if (ready.sourceId !== command.sourceId || ready.documentId !== command.beforeDocumentId
            || ready.documentRevision !== command.beforeDocumentRevision) {
            throw new Error('助手Proposal对应的来源文档revision已变化');
          }
          const projection = await readAssistantArtifactProjection(run);
          const entry = projection.proposals.get(command.proposalId);
          if (!entry || projection.confirmed.has(command.proposalId)
            || projection.cancelled.has(command.proposalId)
            || projection.latestByDocument.get(command.beforeDocumentId) !== command.proposalId) {
            throw new Error('只能确认当前文档最新一份未确认Proposal');
          }
          if (entry.proposal.sourceId !== command.sourceId
            || entry.proposal.documentId !== command.beforeDocumentId
            || entry.proposal.documentRevision !== command.beforeDocumentRevision
            || entry.proposal.previewSha256 !== command.expectedPreviewSha256) {
            throw new Error('助手Proposal与当前文档或预览SHA不一致');
          }
          if (!command.documentId.trim() || command.documentId === ready.documentId
            || !Number.isSafeInteger(command.documentRevision)
            || command.documentRevision !== ready.documentRevision + 1) {
            throw new Error('助手确认生成的来源文档identity无效');
          }
          const requireUniqueIds = (values: string[], label: string, allowEmpty = true) => {
            if (!Array.isArray(values) || (!allowEmpty && !values.length)
              || values.some((value) => typeof value !== 'string' || !value.trim())
              || new Set(values).size !== values.length) {
              throw new Error(`${label}不能为空、重复或格式无效`);
            }
          };
          requireUniqueIds(command.introducedConflictIds, 'introducedConflictIds');
          requireUniqueIds(command.diffSummary.changedBlockIds, 'changedBlockIds', false);
          requireUniqueIds(command.diffSummary.changedSections, 'changedSections');
          requireUniqueIds(command.diffSummary.affectedObjectIds, 'affectedObjectIds');
          if (!command.diffSummary.changedBlockIds.includes(entry.proposal.blockId)) {
            throw new Error('助手确认修订摘要没有包含Proposal block');
          }
          const confirmation = parseAssistantConfirmation(command.payload);
          if (confirmation.runId !== run.runId || confirmation.sourceId !== command.sourceId
            || confirmation.proposalId !== command.proposalId
            || confirmation.proposalRef !== entry.proposalRef
            || confirmation.expectedPreviewSha256 !== command.expectedPreviewSha256
            || confirmation.beforeDocumentId !== command.beforeDocumentId
            || confirmation.beforeDocumentRevision !== command.beforeDocumentRevision
            || confirmation.documentId !== command.documentId
            || confirmation.documentRevision !== command.documentRevision
            || confirmation.blockId !== entry.proposal.blockId
            || confirmation.actorUserId !== command.actor.userId) {
            throw new Error('助手确认Artifact与命令身份不一致');
          }
          if (!input.assistantPatchConfirmationValidator) {
            assistantError('Runtime未配置Story Assistant确认revision校验器');
          }
          await input.assistantPatchConfirmationValidator(confirmation, run, {
            introducedConflictIds: command.introducedConflictIds,
            changedBlockIds: command.diffSummary.changedBlockIds,
            changedSections: command.diffSummary.changedSections,
            affectedObjectIds: command.diffSummary.affectedObjectIds,
          });
          const beforeDocumentId = ready.documentId;
          const beforeDocumentRevision = ready.documentRevision;
          ready.documentId = command.documentId;
          ready.documentRevision = command.documentRevision;
          ready.introducedConflictIds = [...command.introducedConflictIds];
          ready.resolvedConflictIds = [];
          await appendEvents(run, command.actor, [
            {
              type: 'ASSISTANT_PATCH_CONFIRMED', sourceId: ready.sourceId, payload: command.payload,
              assistantProposalId: confirmation.proposalId,
              assistantDocumentId: confirmation.beforeDocumentId,
              assistantDocumentRevision: confirmation.beforeDocumentRevision,
            },
            {
              type: 'DOCUMENT_REVISED', sourceId: ready.sourceId,
              sourceDocumentId: command.documentId,
              sourceDocumentRevision: command.documentRevision,
              payload: JSON.stringify({
                beforeDocumentId, beforeDocumentRevision,
                documentId: command.documentId, documentRevision: command.documentRevision,
                changedBlockIds: [...command.diffSummary.changedBlockIds],
                changedSections: [...command.diffSummary.changedSections],
                affectedObjectIds: [...command.diffSummary.affectedObjectIds],
                affectedConflictIds: [...command.introducedConflictIds],
                ...(command.documentContent ? { documentContent: command.documentContent } : {}),
              }),
            },
            ...(command.introducedConflictIds.length ? [{
              type: 'CONFLICT_FOUND' as const,
              sourceId: ready.sourceId,
              payload: JSON.stringify({ conflictIds: command.introducedConflictIds }),
            }] : []),
          ]);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'START_NEXT_SOURCE': {
          if (run.status !== 'READY' && run.status !== 'CONFLICT_BLOCKED') {
            throw new Error('当前来源文档尚未完成审阅，不能读取下一来源');
          }
          const source = run.sources.find((candidate) => candidate.status === 'PENDING');
          if (!source) throw new Error('没有待读取的来源');
          source.status = 'READING';
          run.status = 'READING_SOURCE';
          await appendEvents(run, command.actor, [{
            type: 'SOURCE_READ_STARTED', sourceId: source.sourceId,
            payload: JSON.stringify({ sourceId: source.sourceId, sourceName: source.sourceName }),
          }]);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'COMPLETE_SOURCE_DOCUMENT': {
          const reading = run.sources.find((source) => source.status === 'READING');
          if (run.status !== 'READING_SOURCE' || !reading) throw new Error('当前没有正在读取的来源');
          if (reading.sourceId !== command.sourceId) {
            throw new Error(`当前正在读取的来源不是 ${sourceLabel(run, command.sourceId)}`);
          }
          if (!command.documentId.trim()) throw new Error('来源文档标识不能为空');
          if (!Number.isSafeInteger(command.documentRevision) || command.documentRevision < 1) {
            throw new Error('documentRevision必须是正整数');
          }
          if (!command.readSummary.summary.trim()) throw new Error('来源读取摘要不能为空');
          if (!Number.isSafeInteger(command.readSummary.objectCount) || command.readSummary.objectCount < 0) {
            throw new Error('objectCount必须是有限非负整数');
          }
          if (!Number.isSafeInteger(command.readSummary.evidenceCount) || command.readSummary.evidenceCount < 0) {
            throw new Error('evidenceCount必须是有限非负整数');
          }
          const conflictIds = command.introducedConflictIds;
          if (conflictIds.some((conflictId) => !conflictId.trim()) || new Set(conflictIds).size !== conflictIds.length) {
            throw new Error('来源冲突标识不能为空或重复');
          }
          const corroboratedConflictIds = command.corroboratedConflictIds ?? [];
          if (corroboratedConflictIds.some((conflictId) => !conflictId.trim())
            || new Set(corroboratedConflictIds).size !== corroboratedConflictIds.length
            || corroboratedConflictIds.some((conflictId) => !run.sources.some((source) => (
              source.introducedConflictIds.includes(conflictId) && source.resolvedConflictIds.includes(conflictId)
            )))) {
            throw new Error('来源佐证必须精确回链已经解决的冲突');
          }
          if (corroboratedConflictIds.length && !input.corroborationReceiptValidator) {
            resolutionError('Runtime未配置Story佐证来源校验器');
          }
          if (corroboratedConflictIds.length && !input.corroborationReceiptSetValidator) {
            resolutionError('Runtime未配置Story佐证集合校验器');
          }
          for (const conflictId of corroboratedConflictIds) {
            await input.corroborationReceiptValidator!({
              runId: run.runId,
              sourceId: reading.sourceId,
              conflictId,
            }, run);
          }
          reading.readSummary = clone(command.readSummary);
          reading.documentId = command.documentId;
          reading.documentRevision = command.documentRevision;
          reading.introducedConflictIds = [...conflictIds];
          reading.resolvedConflictIds = [];
          reading.status = 'DOCUMENT_READY';
          run.status = 'REVIEWING_DOCUMENT';
          if (input.corroborationReceiptSetValidator) {
            await input.corroborationReceiptSetValidator({
              runId: run.runId,
              sourceId: reading.sourceId,
              conflictIds: corroboratedConflictIds,
            }, run);
          }
          await appendEvents(run, command.actor, [
            {
              type: 'SOURCE_READ_COMPLETED', sourceId: reading.sourceId,
              payload: JSON.stringify(command.readSummary),
            },
            {
              type: 'DOCUMENT_GENERATED', sourceId: reading.sourceId,
              sourceDocumentId: command.documentId,
              sourceDocumentRevision: command.documentRevision,
              payload: command.payload,
            },
            ...(conflictIds.length ? [{
              type: 'CONFLICT_FOUND' as const,
              sourceId: reading.sourceId,
              payload: JSON.stringify({ conflictIds }),
            }] : []),
            ...corroboratedConflictIds.map((conflictId) => ({
              type: 'CONFLICT_CORROBORATED' as const,
              sourceId: reading.sourceId,
              payload: JSON.stringify({ conflictId, sourceId: reading.sourceId }),
            })),
          ]);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'REVISE_SOURCE_DOCUMENT': {
          const ready = run.sources.find((source) => source.status === 'DOCUMENT_READY');
          if (run.status !== 'REVIEWING_DOCUMENT' || !ready) {
            throw new Error('当前没有待审阅的来源文档');
          }
          if (ready.resolvedConflictIds.length) {
            throw new Error('已保存来源差异后不能修改来源文档');
          }
          if (ready.sourceId !== command.sourceId) {
            throw new Error(`当前待审阅的来源不是 ${sourceLabel(run, command.sourceId)}`);
          }
          if (!ready.documentId || !ready.documentRevision) throw new Error('当前来源文档身份缺失');
          if (!command.documentId.trim() || command.documentId === ready.documentId) {
            throw new Error('新来源文档标识必须非空且不同于当前revision');
          }
          if (!Number.isSafeInteger(command.documentRevision)
            || command.documentRevision !== ready.documentRevision + 1) {
            throw new Error('新来源文档revision必须连续递增');
          }
          const requireUniqueIds = (values: string[], label: string, allowEmpty = true) => {
            if (!Array.isArray(values)
              || (!allowEmpty && values.length === 0)
              || values.some((value) => typeof value !== 'string' || !value.trim())
              || new Set(values).size !== values.length) {
              throw new Error(`${label}不能为空、重复或格式无效`);
            }
          };
          requireUniqueIds(command.introducedConflictIds, 'introducedConflictIds');
          if (!command.diffSummary || typeof command.diffSummary !== 'object') {
            throw new Error('来源文档修订摘要不能为空');
          }
          requireUniqueIds(command.diffSummary.changedBlockIds, 'changedBlockIds', false);
          requireUniqueIds(command.diffSummary.changedSections, 'changedSections');
          requireUniqueIds(command.diffSummary.affectedObjectIds, 'affectedObjectIds');
          const allowedSections = new Set([
            'OVERVIEW', 'GOAL', 'OBJECT', 'ACTIVITY', 'FIELD', 'RELATION', 'METRIC', 'QUESTION', 'UNRESOLVED',
          ]);
          if (command.diffSummary.changedSections.some((section) => !allowedSections.has(section))) {
            throw new Error('changedSections包含未知九段章节');
          }
          const beforeDocumentId = ready.documentId;
          const beforeDocumentRevision = ready.documentRevision;
          ready.documentId = command.documentId;
          ready.documentRevision = command.documentRevision;
          ready.introducedConflictIds = [...command.introducedConflictIds];
          ready.resolvedConflictIds = [];
          await appendEvents(run, command.actor, [
            {
              type: 'DOCUMENT_REVISED',
              sourceId: ready.sourceId,
              sourceDocumentId: command.documentId,
              sourceDocumentRevision: command.documentRevision,
              payload: JSON.stringify({
                beforeDocumentId,
                beforeDocumentRevision,
                documentId: command.documentId,
                documentRevision: command.documentRevision,
                changedBlockIds: [...command.diffSummary.changedBlockIds],
                changedSections: [...command.diffSummary.changedSections],
                affectedObjectIds: [...command.diffSummary.affectedObjectIds],
                affectedConflictIds: [...command.introducedConflictIds],
                ...(command.documentContent ? { documentContent: command.documentContent } : {}),
              }),
            },
            ...(command.introducedConflictIds.length ? [{
              type: 'CONFLICT_FOUND' as const,
              sourceId: ready.sourceId,
              payload: JSON.stringify({ conflictIds: command.introducedConflictIds }),
            }] : []),
          ]);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'MARK_DOCUMENT_REVIEWED': {
          const ready = run.sources.find((source) => source.status === 'DOCUMENT_READY');
          if (run.status !== 'REVIEWING_DOCUMENT' || !ready) throw new Error('当前没有待审阅的来源文档');
          if (ready.sourceId !== command.sourceId) {
            throw new Error(`当前待审阅的来源不是 ${sourceLabel(run, command.sourceId)}`);
          }
          const eventValues: Parameters<typeof appendEvents>[2] = [{
            type: 'DOCUMENT_REVIEWED', sourceId: ready.sourceId,
            payload: JSON.stringify({ documentId: ready.documentId, documentRevision: ready.documentRevision }),
          }];
          const hasUnresolvedConflict = ready.introducedConflictIds.some((conflictId) => (
            !ready.resolvedConflictIds.includes(conflictId)
          ));
          if (hasUnresolvedConflict) {
            ready.status = 'CONFLICT_BLOCKED';
            run.status = 'CONFLICT_BLOCKED';
            if (!sourceHasConflictFound(run, ready.sourceId)) {
              eventValues.push({
                type: 'CONFLICT_FOUND', sourceId: ready.sourceId,
                payload: JSON.stringify({ conflictIds: ready.introducedConflictIds }),
              });
            }
          } else {
            ready.status = 'ALIGNED';
            run.status = nextRunStatus(run);
          }
          await appendEvents(run, command.actor, eventValues);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'RESOLVE_SOURCE_CONFLICT': {
          const first = firstUnresolvedConflict(run);
          const canResolveDuringReview = run.status === 'REVIEWING_DOCUMENT'
            && first?.source.status === 'DOCUMENT_READY';
          const canResolveAfterReview = (run.status === 'CONFLICT_BLOCKED' || run.status === 'READY')
            && first?.source.status === 'CONFLICT_BLOCKED';
          if (!first || (!canResolveDuringReview && !canResolveAfterReview)) {
            throw new Error('当前没有待解决的来源冲突');
          }
          const source = first.source;
          if (source.sourceId !== command.sourceId) {
            throw new Error(`当前冲突来源不是 ${sourceLabel(run, command.sourceId)}`);
          }
          if (!source.introducedConflictIds.includes(command.conflictId)) {
            throw new Error('待解决冲突不属于当前来源');
          }
          if (source.resolvedConflictIds.includes(command.conflictId)) {
            throw new Error('当前冲突已经解决');
          }
          if (!command.reason.trim()) throw new Error('冲突决定必须填写中文理由');
          const artifact = parseResolutionArtifact(command.payload);
          if (!input.resolutionArtifactValidator) {
            resolutionError('Runtime未配置Story纯投影校验器');
          }
          await input.resolutionArtifactValidator(artifact, run);
          if (artifact.runId !== run.runId || artifact.sourceId !== source.sourceId
            || artifact.hunk.conflictId !== command.conflictId
            || artifact.strategy !== command.strategy
            || artifact.reason !== command.reason
            || artifact.actorUserId !== command.actor.userId
            || artifact.resolutionId !== `resolution:${run.runId}:${command.conflictId}`) {
            throw new Error('冲突决定Artifact与命令身份不一致');
          }
          if (artifact.hunk.hunkSha256 !== command.expectedHunkSha256) {
            throw new Error('冲突Hunk已变化，请刷新后重试');
          }
          if (first.conflictId !== command.conflictId) {
            throw new Error('必须按顺序解决第一项未决冲突');
          }
          source.resolvedConflictIds.push(command.conflictId);
          const hasRemainingForSource = source.introducedConflictIds.some((conflictId) => (
            !source.resolvedConflictIds.includes(conflictId)
          ));
          if (!hasRemainingForSource && source.status === 'CONFLICT_BLOCKED') {
            source.status = 'ALIGNED';
            run.status = nextRunStatus(run);
          }
          await appendEvents(run, command.actor, [{
            type: 'CONFLICT_RESOLVED', sourceId: source.sourceId, payload: command.payload,
          }]);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'REPLACE_SOURCE_CONFLICT_DECISION': {
          if (run.status !== 'READY_FOR_OUTPUT' || run.reviewId) {
            throw new Error('只能在结果定版前重新处理已保存的来源差异');
          }
          const source = run.sources.find((candidate) => candidate.sourceId === command.sourceId);
          if (!source || !source.introducedConflictIds.includes(command.conflictId)
            || !source.resolvedConflictIds.includes(command.conflictId)) {
            throw new Error('要重新处理的来源差异不存在或尚未决定');
          }
          if (!command.reason.trim()) throw new Error('冲突决定必须填写中文理由');
          const artifact = parseResolutionArtifact(command.payload);
          if (!input.resolutionArtifactValidator) {
            resolutionError('Runtime未配置Story纯投影校验器');
          }
          await input.resolutionArtifactValidator(artifact, run);
          const expectedResolutionId = `replacement:${run.runId}:${command.conflictId}:${run.timeline.length + (run.deliverableId ? 2 : 1)}`;
          if (artifact.runId !== run.runId || artifact.sourceId !== source.sourceId
            || artifact.hunk.conflictId !== command.conflictId
            || artifact.strategy !== command.strategy
            || artifact.reason !== command.reason
            || artifact.actorUserId !== command.actor.userId
            || artifact.resolutionId !== expectedResolutionId) {
            throw new Error('新的来源差异决定与命令身份不一致');
          }
          if (artifact.hunk.hunkSha256 !== command.expectedHunkSha256) {
            throw new Error('冲突Hunk已变化，请刷新后重试');
          }
          const priorDeliverableId = run.deliverableId;
          if (priorDeliverableId) {
            run.supersededDeliverableIds = [...(run.supersededDeliverableIds ?? []), priorDeliverableId];
            run.deliverableId = undefined;
          }
          await appendEvents(run, command.actor, [
            ...(priorDeliverableId ? [{
              type: 'DELIVERABLE_SUPERSEDED' as const,
              payload: JSON.stringify({
                schemaVersion: 1,
                deliverableId: priorDeliverableId,
                reason: '来源差异决定已更新，需要重新生成标准化结果。',
              }),
            }] : []),
            {
              type: 'CONFLICT_DECISION_REPLACED', conflictId: command.conflictId, payload: command.payload,
            },
          ]);
          await validateStoredDeliveryEvents(run);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'MARK_DELIVERABLE_GENERATED': {
          if (run.status !== 'READY_FOR_OUTPUT') {
            const hasUnresolvedDifference = run.sources.some((source) => source.introducedConflictIds
              .some((conflictId) => !source.resolvedConflictIds.includes(conflictId)));
            throw new Error(hasUnresolvedDifference
              ? '仍有来源差异未解决，不能生成标准化交付物'
              : '全部来源对齐后才能生成标准化交付物');
          }
          if (run.deliverableId) throw new Error('标准化交付物已经生成');
          if (!command.deliverableId.trim()) throw new Error('标准化交付物标识不能为空');
          if (command.actor.userId !== run.createdBy) throw new Error('交付物必须由运行作者生成');
          run.deliverableId = command.deliverableId;
          const generated = parseDeliveryEventPayload('DELIVERABLE_GENERATED', command.payload, run);
          await appendEvents(run, command.actor, [{
            type: 'DELIVERABLE_GENERATED', payload: command.payload,
            deliveryContentIndex: {
              coreSha256: generated.coreSha256 as ContentReference,
              entries: ([
                'sourceManifestRef', 'mergedDocumentRef', 'decisionManifestRef',
                'governanceAppendixRef', 'modelingArtifactRef', 'zeroDeltaReportRef',
              ] as const).map((key) => ({
                key,
                contentRef: generated[key] as ContentReference,
              })),
            },
          }]);
          await validateStoredDeliveryEvents(run);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'MARK_REVIEW_SUBMITTED': {
          if (!run.deliverableId) throw new Error('必须先生成标准化交付物，再提交审核');
          if (run.status !== 'READY_FOR_OUTPUT') throw new Error('当前标准化运行不能提交审核');
          if (run.reviewId) throw new Error('标准化交付物已经提交审核');
          if (!command.reviewId.trim()) throw new Error('标准化审核标识不能为空');
          run.reviewId = command.reviewId;
          const submitted = parseDeliveryEventPayload('REVIEW_SUBMITTED', command.payload, run);
          if (submitted.authorUserId !== run.createdBy || submitted.authorUserId !== command.actor.userId) {
            throw new Error('作者确认事件必须由运行作者提交');
          }
          await appendEvents(run, command.actor, [{
            type: 'REVIEW_SUBMITTED', payload: command.payload,
          }]);
          await validateStoredDeliveryEvents(run);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'MARK_DELIVERABLE_FROZEN': {
          if (run.status !== 'READY_FOR_OUTPUT') throw new Error('当前标准化运行不能冻结交付物');
          if (run.deliverableId !== command.deliverableId) throw new Error('冻结的交付物不是当前运行的交付物');
          const frozen = parseDeliveryEventPayload('DELIVERABLE_FROZEN', command.payload, run);
          const isDirectAuthorFreeze = frozen.confirmationId !== undefined;
          if (isDirectAuthorFreeze) {
            if (frozen.authorUserId !== command.actor.userId) {
              throw new Error('作者定版事件身份与运行不一致');
            }
          } else if (!run.reviewId) {
            throw new Error('直接定版事件缺少作者确认');
          } else if (frozen.reviewerUserId !== command.actor.userId || command.actor.userId === run.createdBy) {
            throw new Error('冻结事件必须由异于作者的审核人提交');
          }
          run.status = 'FROZEN';
          await appendEvents(run, command.actor, [{
            type: 'DELIVERABLE_FROZEN', payload: command.payload,
          }]);
          await validateStoredDeliveryEvents(run);
          return finishCommand(snapshot, state, current.index, run, command);
        }

        case 'MARK_MODELING_HANDOFF_COMPLETED': {
          if (run.status !== 'FROZEN') throw new Error('只有已冻结的交付物可以交给AI建模');
          if (!command.handoffId.trim()) throw new Error('AI建模交接标识不能为空');
          run.modelingHandoffId = command.handoffId;
          run.status = 'HANDED_OFF';
          parseDeliveryEventPayload('MODELING_HANDOFF_COMPLETED', command.payload, run);
          await appendEvents(run, command.actor, [{
            type: 'MODELING_HANDOFF_COMPLETED', payload: command.payload,
          }]);
          await validateStoredDeliveryEvents(run);
          return finishCommand(snapshot, state, current.index, run, command);
        }
        }
    },
  };
}
