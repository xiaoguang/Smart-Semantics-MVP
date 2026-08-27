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
};

export type BusinessJourneyCheckpoint = JourneyTimelineItem & {
  businessKind: 'SOURCE' | 'FINDING' | 'RESULT';
  checkpointId: string;
};

function sourceNeedsAttention(status: SourceCheckpointStatus) {
  return status === 'PENDING'
    || status === 'READING'
    || status === 'DOCUMENT_READY'
    || status === 'CONFLICT_BLOCKED';
}

const sourceStatusSummary: Readonly<Record<SourceCheckpointStatus, string>> = {
  PENDING: '待读取',
  READING: '正在读取固定快照',
  DOCUMENT_READY: '待审阅',
  REVIEWED: '已审阅',
  CONFLICT_BLOCKED: '存在差异',
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

/**
 * Collapse the persisted timeline into the only journey users need to scan:
 * one line per source, one line per source difference, and one result line.
 * Detailed receipts remain in the persisted audit/document history.
 */
export function projectBusinessJourneyTimeline(input: {
  sources: readonly JourneySource[];
  timeline: readonly JourneyTimelineItem[];
}): BusinessJourneyCheckpoint[] {
  // REVIEWED is a completed source checkpoint. The next unread source, a
  // blocked difference, or the result is the user's next business step.
  const currentSourceId = input.sources.find((source) => sourceNeedsAttention(source.status))?.sourceId;
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
    };
  });

  const findingById = new Map<string, JourneyTimelineItem>();
  for (const item of input.timeline) {
    for (const conflict of item.conflicts ?? []) findingById.set(conflict.conflictId, item);
    if (item.action && typeof item.action === 'object' && 'type' in item.action
      && item.action.type === 'OPEN_CONFLICT' && 'conflictId' in item.action) {
      const conflictId = String(item.action.conflictId);
      const prior = findingById.get(conflictId);
      findingById.set(conflictId, {
        ...(prior ?? item),
        ...item,
        conflicts: prior?.conflicts ?? item.conflicts ?? [{ conflictId, title: item.title, affectedObjects: [] }],
      });
    }
  }
  const findings = [...findingById.entries()].map(([conflictId, item]) => {
    const conflict = item.conflicts?.find((candidate) => candidate.conflictId === conflictId)
      ?? { conflictId, title: item.title, affectedObjects: [] };
    return {
      ...item,
      itemId: `finding:${conflictId}`,
      checkpointId: `finding:${conflictId}`,
      businessKind: 'FINDING' as const,
      title: conflict.title,
      summary: item.kind === 'CONFLICT_FOUND' || item.kind === 'CONFLICT_DECISION_REPLACED'
        ? '待决定' : '已决定',
      conflicts: [conflict],
    };
  });

  // A source that is blocked because of a difference is not a second current
  // task. The unresolved difference is the actionable checkpoint. Preserve
  // older finding records as receipts and let the latest active finding own
  // the single journey focus.
  const activeFinding = [...findings].reverse().find((item) => item.state === 'CURRENT');
  const normalizedFindings = findings.map((item) => activeFinding
    ? { ...item, state: item.itemId === activeFinding.itemId ? 'CURRENT' as const : 'RECEIPT' as const }
    : item);
  const normalizedSourceCheckpoints = sourceCheckpoints.map((item) => activeFinding
    ? { ...item, state: 'RECEIPT' as const }
    : item);

  const resultItem = [...input.timeline].reverse().find((item) => item.kind.startsWith('DELIVERABLE_')
    || item.kind === 'MODELING_HANDOFF_COMPLETED');
  const allSourcesAligned = input.sources.length > 0 && input.sources.every((source) => source.status === 'ALIGNED');
  const result: BusinessJourneyCheckpoint = {
    itemId: 'result:standardization',
    checkpointId: 'result:standardization',
    businessKind: 'RESULT',
    kind: resultItem?.kind ?? 'DELIVERABLE_GENERATED',
    state: !normalizedSourceCheckpoints.some((item) => item.state === 'CURRENT')
      && !normalizedFindings.some((item) => item.state === 'CURRENT')
      && (resultItem?.state === 'CURRENT'
        || (allSourcesAligned && resultItem?.kind === 'DELIVERABLE_FROZEN')
        || (!normalizedSourceCheckpoints.some((item) => item.state === 'CURRENT')
          && !normalizedFindings.some((item) => item.state === 'CURRENT')))
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
  return [...normalizedSourceCheckpoints, ...normalizedFindings, result];
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
