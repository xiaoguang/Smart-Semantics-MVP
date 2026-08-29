/**
 * The business journey deliberately has a lower cardinality than the audit
 * event stream.  A source may emit several technical events while it is read,
 * compiled, revised, and reviewed; people navigate one stable source
 * checkpoint instead of that implementation history.
 */

export type StandardizationSource = {
  sourceId: string;
  displayName: string;
};

export type SourceLifecycleEventType =
  | 'SOURCE_READ_STARTED'
  | 'SOURCE_READ_COMPLETED'
  | 'DOCUMENT_GENERATED'
  | 'DOCUMENT_REVISED'
  | 'DOCUMENT_REVIEWED'
  | 'SOURCE_ALIGNED'
  | 'CONFLICT_FOUND'
  | 'CONFLICT_RESOLVED'
  | 'CONFLICT_DECISION_REPLACED'
  | 'CONFLICT_CORROBORATED';

/** A source-scoped subset of the persisted audit timeline. */
export type SourceLifecycleEvent = {
  eventId: string;
  sourceId: string;
  type: SourceLifecycleEventType;
};

export type SourceCheckpointStatus =
  | 'PENDING'
  | 'READING'
  | 'DOCUMENT_READY'
  | 'REVIEWED'
  | 'CONFLICT_BLOCKED'
  | 'ALIGNED';

export type SourceJourneyCheckpoint = {
  kind: 'SOURCE';
  checkpointId: `source:${string}`;
  sourceId: string;
  status: SourceCheckpointStatus;
  /** Audit-event references remain internal, but preserve replayability. */
  eventIds: string[];
};

export type StandardizationExperienceProjection = {
  checkpoints: SourceJourneyCheckpoint[];
};

/**
 * The UI timeline has richer navigation data than the persisted lifecycle
 * event.  Keep it structural here: this module projects the small, business
 * journey without teaching the workbench how to collapse an audit stream.
 */
export type JourneyTimelineAction = {
  type: string;
  [key: string]: unknown;
};

export type JourneyTimelineItem = {
  itemId: string;
  eventId?: string;
  kind: string;
  state: 'RECEIPT' | 'CURRENT';
  title: string;
  summary: string;
  sourceId?: string;
  action?: JourneyTimelineAction;
  document?: unknown;
  conflicts?: Array<{ conflictId: string; title: string; affectedObjects: string[] }>;
  inspector?: unknown;
  createdAt?: string;
  contentRef?: unknown;
  contentSha256?: string;
};

export type JourneySource = StandardizationSource & {
  status: SourceCheckpointStatus;
  /** Formal conflict ownership remains on the source step, never in a UI-only queue. */
  introducedConflictIds?: readonly string[];
  resolvedConflictIds?: readonly string[];
  /** In-flight UI receipt from actual READ/ANALYZE/ORGANIZE boundaries. */
  preparationStates?: Partial<Record<'READ' | 'ANALYZE' | 'ORGANIZE', JourneyStageState>>;
};

export type JourneyStageState = 'PENDING' | 'ACTIVE' | 'COMPLETE' | 'ERROR';

export type BusinessJourneyStage = {
  stage: 'READ' | 'ANALYZE' | 'ORGANIZE' | 'REVIEW';
  label: '读取' | '分析' | '组织' | '审阅';
  state: JourneyStageState;
  conflicts?: Array<{
    conflictId: string;
    title: string;
    state: JourneyStageState;
    affectedObjects: string[];
    action?: JourneyTimelineAction;
  }>;
};

export type BusinessJourneyCheckpoint = JourneyTimelineItem & {
  businessKind: 'SOURCE' | 'RESULT';
  checkpointId: string;
  stages?: BusinessJourneyStage[];
};

/**
 * The source-process disclosure never foreshadows work that has not started.
 * A completed source exposes its full receipt when reopened; an active source
 * exposes only the stable prefix through the actual active or failed phase.
 */
export function projectVisibleBusinessJourneyStages(
  stages: readonly BusinessJourneyStage[] | undefined,
): BusinessJourneyStage[] {
  if (!stages?.length) return [];
  const lastVisibleIndex = stages.findLastIndex((stage) => stage.state !== 'PENDING');
  return lastVisibleIndex < 0 ? [] : stages.slice(0, lastVisibleIndex + 1);
}

function sourceNeedsAttention(status: SourceCheckpointStatus) {
  return status === 'PENDING'
    || status === 'READING'
    || status === 'DOCUMENT_READY'
    || status === 'CONFLICT_BLOCKED';
}

const sourceStatusSummary: Readonly<Record<SourceCheckpointStatus, string>> = {
  PENDING: '待读取',
  READING: '正在读取固定快照',
  DOCUMENT_READY: '审阅中',
  REVIEWED: '已审阅',
  CONFLICT_BLOCKED: '审阅中 · 有待保存差异',
  ALIGNED: '已审阅',
};

function sourceKindForStatus(status: SourceCheckpointStatus) {
  if (status === 'PENDING' || status === 'READING') return 'SOURCE_READ_STARTED';
  if (status === 'DOCUMENT_READY') return 'DOCUMENT_GENERATED';
  if (status === 'CONFLICT_BLOCKED') return 'CONFLICT_FOUND';
  return 'DOCUMENT_REVIEWED';
}

function latestDocumentItem(items: readonly JourneyTimelineItem[]) {
  return [...items].reverse().find((item) => item.action && typeof item.action === 'object'
    && 'type' in item.action && item.action.type === 'OPEN_DOCUMENT');
}

const journeyStageDefinitions: ReadonlyArray<Pick<BusinessJourneyStage, 'stage' | 'label'>> = [
  { stage: 'READ', label: '读取' },
  { stage: 'ANALYZE', label: '分析' },
  { stage: 'ORGANIZE', label: '组织' },
  { stage: 'REVIEW', label: '审阅' },
];

function stageStatesForSource(source: JourneySource): JourneyStageState[] {
  const defaultStates = source.status === 'PENDING'
    ? ['PENDING', 'PENDING', 'PENDING', 'PENDING'] as JourneyStageState[]
    : source.status === 'READING'
      ? ['ACTIVE', 'PENDING', 'PENDING', 'PENDING'] as JourneyStageState[]
      : source.status === 'DOCUMENT_READY' || source.status === 'CONFLICT_BLOCKED'
        ? ['COMPLETE', 'COMPLETE', 'COMPLETE', 'ACTIVE'] as JourneyStageState[]
        : ['COMPLETE', 'COMPLETE', 'COMPLETE', 'COMPLETE'] as JourneyStageState[];
  return defaultStates.map((state, index) => {
    const stage = journeyStageDefinitions[index]?.stage;
    return stage && stage !== 'REVIEW' ? source.preparationStates?.[stage] ?? state : state;
  });
}

function conflictItemsById(items: readonly JourneyTimelineItem[]) {
  const findings = new Map<string, JourneyTimelineItem>();
  for (const item of items) {
    for (const conflict of item.conflicts ?? []) findings.set(conflict.conflictId, item);
    if (item.action && typeof item.action === 'object' && 'type' in item.action
      && item.action.type === 'OPEN_CONFLICT' && 'conflictId' in item.action) {
      const conflictId = String(item.action.conflictId);
      const prior = findings.get(conflictId);
      findings.set(conflictId, {
        ...(prior ?? item),
        ...item,
        conflicts: prior?.conflicts ?? item.conflicts ?? [{
          conflictId,
          title: item.title,
          affectedObjects: [],
        }],
      });
    }
  }
  return findings;
}

function journeyStagesForSource(
  source: JourneySource,
  conflictItems: ReadonlyMap<string, JourneyTimelineItem>,
): BusinessJourneyStage[] {
  const states = stageStatesForSource(source);
  const resolved = new Set(source.resolvedConflictIds ?? []);
  const conflicts = (source.introducedConflictIds ?? []).map((conflictId) => {
    const item = conflictItems.get(conflictId);
    const conflict = item?.conflicts?.find((candidate) => candidate.conflictId === conflictId);
    if (!item || !conflict) {
      return {
        conflictId,
        title: `差异资料异常：${conflictId}`,
        state: 'ERROR' as const,
        affectedObjects: [],
      };
    }
    return {
      conflictId,
      title: conflict.title,
      state: resolved.has(conflictId) ? 'COMPLETE' as const : 'ACTIVE' as const,
      affectedObjects: conflict.affectedObjects,
      ...(item.action?.type === 'OPEN_CONFLICT' ? { action: item.action } : {}),
    };
  });
  return journeyStageDefinitions.map((definition, index) => ({
    ...definition,
    state: states[index]!,
    ...(definition.stage === 'REVIEW' && conflicts.length ? { conflicts } : {}),
  }));
}

/**
 * Collapse the persisted timeline into the only journey users need to scan:
 * one expandable source group and one result line. A formal difference lives
 * under the exact source whose review introduced it, never as a late queue.
 * Detailed receipts remain in the persisted audit/document history.
 */
export function projectBusinessJourneyTimeline(input: {
  sources: readonly JourneySource[];
  timeline: readonly JourneyTimelineItem[];
}): BusinessJourneyCheckpoint[] {
  // REVIEWED is a completed source checkpoint. The next unread source, a
  // blocked difference, or the result is the user's next business step.
  const currentSourceId = input.sources.find((source) => sourceNeedsAttention(source.status))?.sourceId;
  const conflictItems = conflictItemsById(input.timeline);
  const sourceCheckpoints: BusinessJourneyCheckpoint[] = input.sources.map((source) => {
    const sourceItems = input.timeline.filter((item) => item.sourceId === source.sourceId);
    const latest = sourceItems.at(-1);
    const documentItem = latestDocumentItem(sourceItems);
    return {
      itemId: `source:${source.sourceId}`,
      checkpointId: `source:${source.sourceId}`,
      businessKind: 'SOURCE',
      kind: sourceKindForStatus(source.status),
      state: source.sourceId === currentSourceId ? 'CURRENT' : 'RECEIPT',
      sourceId: source.sourceId,
      title: source.displayName,
      summary: sourceStatusSummary[source.status],
      ...(documentItem?.action ? { action: documentItem.action } : {}),
      ...(documentItem?.document ? { document: documentItem.document } : {}),
      ...(latest?.inspector ? { inspector: latest.inspector } : {}),
      ...(latest?.eventId ? { eventId: latest.eventId } : {}),
      ...(latest?.createdAt ? { createdAt: latest.createdAt } : {}),
      ...(latest?.contentRef ? { contentRef: latest.contentRef } : {}),
      ...(latest?.contentSha256 ? { contentSha256: latest.contentSha256 } : {}),
      stages: journeyStagesForSource(source, conflictItems),
    };
  });

  const resultItem = [...input.timeline].reverse().find((item) => item.kind.startsWith('DELIVERABLE_')
    || item.kind === 'MODELING_HANDOFF_COMPLETED');
  const allSourcesReviewed = input.sources.length > 0 && input.sources.every((source) => (
    source.status === 'ALIGNED' || source.status === 'REVIEWED'
  ));
  const result: BusinessJourneyCheckpoint = {
    itemId: 'result:standardization',
    checkpointId: 'result:standardization',
    businessKind: 'RESULT',
    kind: resultItem?.kind ?? 'DELIVERABLE_GENERATED',
    state: !sourceCheckpoints.some((item) => item.state === 'CURRENT') && allSourcesReviewed
      ? 'CURRENT' : 'RECEIPT',
    title: '标准化结果',
    summary: resultItem?.kind === 'MODELING_HANDOFF_COMPLETED' ? '已交接'
      : resultItem?.kind === 'DELIVERABLE_FROZEN' ? '已定版'
        : resultItem?.kind === 'DELIVERABLE_GENERATED' ? '待定版'
          : resultItem?.kind === 'DELIVERABLE_SUPERSEDED' ? '需要重新生成'
            : '待生成',
    ...(resultItem?.action ? { action: resultItem.action } : {}),
    ...(resultItem?.eventId ? { eventId: resultItem.eventId } : {}),
    ...(resultItem?.createdAt ? { createdAt: resultItem.createdAt } : {}),
    ...(resultItem?.contentRef ? { contentRef: resultItem.contentRef } : {}),
    ...(resultItem?.contentSha256 ? { contentSha256: resultItem.contentSha256 } : {}),
  };
  return [...sourceCheckpoints, result];
}

export type SourceCheckpointLocation = {
  effect: 'LOCATE_WORKFLOW_CHECKPOINT';
  checkpointId: `source:${string}`;
  sourceId: string;
  /** Source-list navigation only locates the workflow. It never opens a document. */
  opensDocument: false;
};

const statusForEvent: Readonly<Record<SourceLifecycleEventType, SourceCheckpointStatus>> = {
  SOURCE_READ_STARTED: 'READING',
  SOURCE_READ_COMPLETED: 'READING',
  DOCUMENT_GENERATED: 'DOCUMENT_READY',
  DOCUMENT_REVISED: 'DOCUMENT_READY',
  DOCUMENT_REVIEWED: 'REVIEWED',
  SOURCE_ALIGNED: 'ALIGNED',
  CONFLICT_FOUND: 'CONFLICT_BLOCKED',
  CONFLICT_RESOLVED: 'ALIGNED',
  CONFLICT_DECISION_REPLACED: 'CONFLICT_BLOCKED',
  CONFLICT_CORROBORATED: 'ALIGNED',
};

/**
 * Convert source-scoped lifecycle events into one deterministic checkpoint per
 * configured source. The last source event wins because a retried read is a
 * real return to the reading state, not an historic status to be ranked away.
 */
export function projectStandardizationExperience(input: {
  sources: readonly StandardizationSource[];
  timeline: readonly SourceLifecycleEvent[];
}): StandardizationExperienceProjection {
  const sourceIds = new Set(input.sources.map((source) => source.sourceId));
  const eventsBySource = new Map<string, SourceLifecycleEvent[]>();

  for (const event of input.timeline) {
    if (!sourceIds.has(event.sourceId)) continue;
    const events = eventsBySource.get(event.sourceId) ?? [];
    events.push(event);
    eventsBySource.set(event.sourceId, events);
  }

  return {
    checkpoints: input.sources.map((source) => {
      const events = eventsBySource.get(source.sourceId) ?? [];
      const latest = events.at(-1);
      return {
        kind: 'SOURCE' as const,
        checkpointId: `source:${source.sourceId}`,
        sourceId: source.sourceId,
        status: latest ? statusForEvent[latest.type] : 'PENDING',
        eventIds: events.map((event) => event.eventId),
      };
    }),
  };
}

/**
 * Source-list activation intentionally has no document navigation side effect.
 * The UI can pass this request to the right workflow panel and leave the
 * current review scroll position and focus untouched.
 */
export function locateSourceCheckpoint(input: {
  sourceId: string;
  checkpoints: readonly SourceJourneyCheckpoint[];
}): SourceCheckpointLocation | undefined {
  const checkpoint = input.checkpoints.find((item) => item.sourceId === input.sourceId);
  if (!checkpoint) return undefined;
  return {
    effect: 'LOCATE_WORKFLOW_CHECKPOINT',
    checkpointId: checkpoint.checkpointId,
    sourceId: checkpoint.sourceId,
    opensDocument: false,
  };
}
