import {
  CheckCircleOutlined,
  PlayCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  projectVisibleBusinessJourneyStages,
  type BusinessJourneyCheckpoint,
  type JourneyStageState,
  type JourneyTimelineItem,
} from './standardization-experience-projection.ts';
import {
  initialWorkflowFollowState,
  projectWorkflowFollow,
  type WorkflowFollowState,
} from './standardization-workflow-follow.ts';

type SourceDisclosureMode = 'AUTO' | 'HISTORY' | 'MANUAL';

function currentCheckpointId(items: readonly JourneyTimelineItem[]) {
  return [...items].reverse().find((item) => item.state !== 'RECEIPT')?.itemId
    ?? items.at(-1)?.itemId
    ?? '';
}

export default function StandardizationTimeline({
  items,
  total = items.length,
  selectedItemId,
  locateRequest,
  onSelect,
}: {
  items: BusinessJourneyCheckpoint[];
  total?: number;
  selectedItemId?: string;
  /** Source-list selection asks the panel to locate its paired business step. */
  locateRequest?: { checkpointId: string; requestId: number };
  /** Kept for the earlier caller contract. Completed records are always visible. */
  completedOpen?: boolean;
  onSelect(item: JourneyTimelineItem): void;
}) {
  const checkpointItemId = currentCheckpointId(items);
  const [followState, setFollowState] = useState<WorkflowFollowState>(() => initialWorkflowFollowState(checkpointItemId));
  const [expandedSourceId, setExpandedSourceId] = useState<string>();
  const [sourceDisclosureMode, setSourceDisclosureMode] = useState<SourceDisclosureMode>('AUTO');
  const previousCurrentSourceIdRef = useRef<string | undefined>(undefined);
  const previousCurrentSourceStartedRef = useRef(false);
  const listRef = useRef<HTMLOListElement>(null);
  const programmaticScrollRef = useRef(false);
  const programmaticScrollIdRef = useRef(0);
  const programmaticScrollTimeoutRef = useRef<number | undefined>(undefined);
  const removeProgrammaticScrollEndListenerRef = useRef<(() => void) | undefined>(undefined);
  const currentSource = items.find((item) => item.businessKind === 'SOURCE' && item.state === 'CURRENT');
  const currentSourceId = currentSource?.sourceId;
  const currentSourceHasVisibleStages = projectVisibleBusinessJourneyStages(currentSource?.stages).length > 0;

  useEffect(() => {
    const previousSourceId = previousCurrentSourceIdRef.current;
    const currentSourceChanged = previousSourceId !== currentSourceId;
    const currentSourceStarted = currentSourceHasVisibleStages
      && (!previousCurrentSourceStartedRef.current || currentSourceChanged);
    const previousSourceComplete = previousSourceId
      ? items.find((item) => item.sourceId === previousSourceId)?.stages?.every((stage) => stage.state === 'COMPLETE')
      : false;

    if (currentSourceChanged && previousSourceComplete && expandedSourceId === previousSourceId) {
      setExpandedSourceId(undefined);
    }
    if ((currentSourceChanged || currentSourceStarted)
      && currentSourceId
      && currentSourceHasVisibleStages
      && sourceDisclosureMode !== 'HISTORY') {
      setExpandedSourceId(currentSourceId);
      setSourceDisclosureMode('AUTO');
    }
    previousCurrentSourceIdRef.current = currentSourceId;
    previousCurrentSourceStartedRef.current = currentSourceHasVisibleStages;
  }, [currentSourceHasVisibleStages, currentSourceId, expandedSourceId, items, sourceDisclosureMode]);

  useEffect(() => {
    if (!checkpointItemId) return;
    setFollowState((previous) => projectWorkflowFollow(previous, { checkpointItemId }));
  }, [checkpointItemId]);

  useEffect(() => {
    if (!locateRequest?.checkpointId) return;
    setFollowState((previous) => ({
      ...previous,
      scrollToCheckpointItemId: locateRequest.checkpointId,
    }));
  }, [locateRequest?.checkpointId, locateRequest?.requestId]);

  useEffect(() => () => {
    programmaticScrollIdRef.current += 1;
    if (programmaticScrollTimeoutRef.current !== undefined) window.clearTimeout(programmaticScrollTimeoutRef.current);
    removeProgrammaticScrollEndListenerRef.current?.();
  }, []);

  useEffect(() => {
    const panel = listRef.current?.closest<HTMLElement>('.guanyijia-workflow-panel');
    if (!panel || !checkpointItemId) return;
    const browseHistory = () => {
      if (programmaticScrollRef.current) return;
      setFollowState((previous) => projectWorkflowFollow(previous, {
        checkpointItemId,
        browsingHistory: true,
      }));
    };
    panel.addEventListener('scroll', browseHistory, { passive: true });
    return () => panel.removeEventListener('scroll', browseHistory);
  }, [checkpointItemId]);

  useEffect(() => {
    const targetItemId = followState.scrollToCheckpointItemId;
    if (!targetItemId) return;
    const target = document.getElementById(`guanyijia-workflow-step:${targetItemId}`);
    const panel = listRef.current?.closest<HTMLElement>('.guanyijia-workflow-panel');
    if (!target || !panel) return;
    programmaticScrollIdRef.current += 1;
    const scrollId = programmaticScrollIdRef.current;
    if (programmaticScrollTimeoutRef.current !== undefined) window.clearTimeout(programmaticScrollTimeoutRef.current);
    removeProgrammaticScrollEndListenerRef.current?.();
    programmaticScrollRef.current = true;
    const settleProgrammaticScroll = () => {
      if (scrollId !== programmaticScrollIdRef.current) return;
      programmaticScrollRef.current = false;
      if (programmaticScrollTimeoutRef.current !== undefined) {
        window.clearTimeout(programmaticScrollTimeoutRef.current);
        programmaticScrollTimeoutRef.current = undefined;
      }
      removeProgrammaticScrollEndListenerRef.current?.();
      removeProgrammaticScrollEndListenerRef.current = undefined;
    };
    const onScrollEnd = () => settleProgrammaticScroll();
    panel.addEventListener('scrollend', onScrollEnd, { once: true });
    removeProgrammaticScrollEndListenerRef.current = () => panel.removeEventListener('scrollend', onScrollEnd);
    programmaticScrollTimeoutRef.current = window.setTimeout(settleProgrammaticScroll, 1_500);
    const targetTop = target.getBoundingClientRect().top
      - panel.getBoundingClientRect().top
      + panel.scrollTop;
    const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    panel.scrollTo({
      top: Math.max(0, targetTop - 10),
      behavior: reducedMotion ? 'auto' : 'smooth',
    });
    if (reducedMotion) settleProgrammaticScroll();
    setFollowState((previous) => ({ ...previous, scrollToCheckpointItemId: undefined }));
  }, [followState.scrollToCheckpointItemId]);

  if (!items.length) return <div className="guanyijia-timeline-empty" aria-live="polite">尚未开始</div>;
  const omitted = Math.max(0, total - items.length);
  const selectItem = (item: JourneyTimelineItem) => {
    if (item.itemId !== checkpointItemId) {
      setFollowState((previous) => projectWorkflowFollow(previous, {
        checkpointItemId,
        browsingHistory: true,
      }));
    }
    onSelect(item);
  };
  const returnToCurrent = () => {
    setFollowState({
      mode: 'FOLLOWING',
      currentCheckpointItemId: checkpointItemId,
      scrollToCheckpointItemId: checkpointItemId,
    });
    if (currentSourceId && currentSourceHasVisibleStages) {
      setExpandedSourceId(currentSourceId);
      setSourceDisclosureMode('AUTO');
    }
  };
  const toggleSourceStages = (sourceId: string) => {
    if (expandedSourceId === sourceId) {
      if (sourceDisclosureMode === 'HISTORY'
        && currentSourceId
        && currentSourceId !== sourceId
        && currentSourceHasVisibleStages) {
        setExpandedSourceId(currentSourceId);
        setSourceDisclosureMode('AUTO');
      } else {
        setExpandedSourceId(undefined);
        setSourceDisclosureMode('MANUAL');
      }
      return;
    }
    setExpandedSourceId(sourceId);
    setSourceDisclosureMode(sourceId === currentSourceId ? 'MANUAL' : 'HISTORY');
  };
  const stageSummary: Record<JourneyStageState, string> = {
    PENDING: '未开始', ACTIVE: '进行中', COMPLETE: '已完成', ERROR: '处理失败',
  };
  const stageIcon = (state: JourneyStageState) => state === 'COMPLETE'
    ? <CheckCircleOutlined />
    : state === 'ERROR' ? <WarningOutlined />
      : state === 'ACTIVE' ? <PlayCircleOutlined />
        : <span className="guanyijia-timeline-stage-pending-dot" />;
  const renderItem = (item: BusinessJourneyCheckpoint, index: number) => {
    const isCurrent = item.itemId === checkpointItemId;
    const sourceComplete = Boolean(item.businessKind === 'SOURCE'
      && item.stages?.every((stage) => stage.state === 'COMPLETE'));
    const visibleStages = projectVisibleBusinessJourneyStages(item.stages);
    const sourceExpandable = item.businessKind === 'SOURCE'
      && Boolean(item.sourceId)
      && visibleStages.length > 0;
    const sourceStagesExpanded = sourceExpandable && expandedSourceId === item.sourceId;
    const sourceHasError = visibleStages.some((stage) => stage.state === 'ERROR');
    const sourceVisualState = sourceHasError
      ? 'error'
      : sourceComplete ? 'complete'
        : isCurrent && sourceExpandable ? 'active'
          : 'pending';
    const sourceTitle = <span className="guanyijia-timeline-copy">
      <strong>{item.title}</strong>
    </span>;
    const sourceSummary = <small className="guanyijia-source-process-summary">{item.summary}</small>;
    const resultContent = <span className="guanyijia-timeline-copy">
      <strong>{item.title}</strong>
      <small>{item.summary}</small>
    </span>;
    const stageList = visibleStages.length ? <ol className="guanyijia-timeline-stages" aria-label={`${item.title}处理阶段`}>
      {visibleStages.map((stage) => <li key={stage.stage} className={`guanyijia-timeline-stage ${stage.state.toLowerCase()}`}>
        <span className="guanyijia-timeline-stage-rail" aria-hidden="true">{stageIcon(stage.state)}</span>
        <span className="guanyijia-timeline-stage-copy"><strong>{stage.label}</strong><small>{stageSummary[stage.state]}</small></span>
        {stage.conflicts?.map((conflict) => {
          const conflictItem: JourneyTimelineItem = {
            ...item,
            itemId: `source:${item.sourceId}:conflict:${conflict.conflictId}`,
            kind: conflict.state === 'COMPLETE' ? 'CONFLICT_RESOLVED' : 'CONFLICT_FOUND',
            state: conflict.state === 'ACTIVE' ? 'CURRENT' : 'RECEIPT',
            title: conflict.title,
            summary: stageSummary[conflict.state],
            action: conflict.action,
            conflicts: [{
              conflictId: conflict.conflictId,
              title: conflict.title,
              affectedObjects: conflict.affectedObjects,
            }],
          };
          const conflictContent = <>
            <span className="guanyijia-timeline-stage-copy"><strong>{conflict.title}</strong><small>{stageSummary[conflict.state]}</small></span>
            {conflict.affectedObjects.length > 0 && <span className="guanyijia-conflict-impact">{conflict.affectedObjects.join(' · ')}</span>}
          </>;
          return <div key={conflict.conflictId} className={`guanyijia-timeline-conflict ${conflict.state.toLowerCase()}`}>
            <span className="guanyijia-timeline-stage-rail" aria-hidden="true">{stageIcon(conflict.state)}</span>
            {conflict.action ? <button type="button" onClick={() => selectItem(conflictItem)}>{conflictContent}</button>
              : <div>{conflictContent}</div>}
          </div>;
        })}
      </li>)}
    </ol> : undefined;
    if (item.businessKind === 'RESULT') {
      const resultControl = item.action ? <button
        id={`guanyijia-timeline:${item.itemId}`}
        type="button"
        className={`guanyijia-source-process-result-action${selectedItemId === item.itemId ? ' selected' : ''}`}
        onClick={() => selectItem(item)}
      >
        <span className="guanyijia-source-process-status-mark" aria-hidden="true">{stageIcon(
          item.state === 'CURRENT' ? 'ACTIVE' : 'PENDING',
        )}</span>
        {resultContent}
      </button> : <div className="guanyijia-source-process-result-static">
        <span className="guanyijia-source-process-status-mark" aria-hidden="true">{stageIcon(
          item.state === 'CURRENT' ? 'ACTIVE' : 'PENDING',
        )}</span>
        {resultContent}
      </div>;
      return <li
        id={`guanyijia-workflow-step:${item.itemId}`}
        key={item.itemId}
        className={`guanyijia-source-process-result ${item.state.toLowerCase()}`}
        data-review-anchor={`timeline:${item.itemId}`}
        aria-label={`${item.title} ${item.summary}`}
        aria-current={isCurrent ? 'step' : undefined}
        aria-setsize={total}
        aria-posinset={omitted + index + 1}
      >{resultControl}</li>;
    }
    const sourceHeader = sourceExpandable && item.sourceId ? <button
      id={`guanyijia-timeline:${item.itemId}`}
      type="button"
      className={`guanyijia-source-process-disclosure ${sourceVisualState}${sourceStagesExpanded ? ' expanded' : ''}`}
      aria-expanded={sourceStagesExpanded}
      aria-controls={`guanyijia-timeline-stages:${item.sourceId}`}
      onClick={() => toggleSourceStages(item.sourceId!)}
    >
      <span className="guanyijia-source-process-status-mark" aria-hidden="true">{stageIcon(
        sourceVisualState === 'complete' ? 'COMPLETE'
          : sourceVisualState === 'error' ? 'ERROR'
            : sourceVisualState === 'active' ? 'ACTIVE' : 'PENDING',
      )}</span>
      {sourceTitle}
      {sourceSummary}
      <span className="guanyijia-source-process-chevron" aria-hidden="true">{sourceStagesExpanded ? '⌃' : '›'}</span>
    </button> : <div className={`guanyijia-source-process-static ${sourceVisualState}`}>
      <span className="guanyijia-source-process-status-mark" aria-hidden="true">{stageIcon(
        sourceVisualState === 'complete' ? 'COMPLETE'
          : sourceVisualState === 'error' ? 'ERROR'
            : sourceVisualState === 'active' ? 'ACTIVE' : 'PENDING',
      )}</span>
      {sourceTitle}
      {sourceSummary}
    </div>;
    return <li
      id={`guanyijia-workflow-step:${item.itemId}`}
      key={item.itemId}
      className={`guanyijia-source-process-item ${sourceVisualState}`}
      data-review-anchor={`timeline:${item.itemId}`}
      aria-label={`${item.title} ${item.summary}`}
      aria-current={isCurrent ? 'step' : undefined}
      aria-setsize={total}
      aria-posinset={omitted + index + 1}
    >
      {sourceHeader}
      {stageList && <div id={`guanyijia-timeline-stages:${item.sourceId}`} className="guanyijia-source-process-details" hidden={!sourceStagesExpanded}>
        {stageList}
        {item.action && <button
          type="button"
          className={`guanyijia-source-process-document-link${selectedItemId === item.itemId ? ' selected' : ''}`}
          onClick={() => selectItem(item)}
        >{isCurrent ? '查看当前审阅文档' : '查看审阅文档'}</button>}
      </div>
      }
    </li>;
  };
  return <div className="guanyijia-standardization-workflow">
    {followState.mode === 'BROWSING_HISTORY' && followState.pendingCheckpointItemId && <Button
      className="guanyijia-workflow-return-current"
      type="link"
      onClick={returnToCurrent}
    >有新进度 · 回到当前步骤</Button>}
    <ol ref={listRef} className="guanyijia-timeline" aria-label="标准化运行时间线" data-review-inertable>
      {omitted > 0 && <li className="guanyijia-timeline-window-note" aria-hidden="true">此前 {omitted} 条记录已移出当前窗口</li>}
      {items.map((item, index) => renderItem(item, index))}
    </ol>
  </div>;
}
