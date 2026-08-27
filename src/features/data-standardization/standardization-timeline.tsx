import {
  CheckCircleOutlined,
  DatabaseOutlined,
  EditOutlined,
  FileMarkdownOutlined,
  LinkOutlined,
  MessageOutlined,
  PlayCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Button } from 'antd';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { JourneyTimelineItem } from './standardization-experience-projection.ts';
import {
  initialWorkflowFollowState,
  projectWorkflowFollow,
  type WorkflowFollowState,
} from './standardization-workflow-follow.ts';

const kindIcon: Record<string, ReactNode> = {
  RUN_STARTED: <PlayCircleOutlined />,
  SOURCE_READ_STARTED: <DatabaseOutlined />,
  SOURCE_READ_COMPLETED: <CheckCircleOutlined />,
  DOCUMENT_GENERATED: <FileMarkdownOutlined />,
  DOCUMENT_REVISED: <FileMarkdownOutlined />,
  DOCUMENT_REVIEWED: <CheckCircleOutlined />,
  CONFLICT_FOUND: <WarningOutlined />,
  CONFLICT_RESOLVED: <CheckCircleOutlined />,
  CONFLICT_DECISION_REPLACED: <EditOutlined />,
  CONFLICT_CORROBORATED: <LinkOutlined />,
  DELIVERABLE_GENERATED: <FileMarkdownOutlined />,
  DELIVERABLE_SUPERSEDED: <EditOutlined />,
  DELIVERABLE_FROZEN: <CheckCircleOutlined />,
  MODELING_HANDOFF_COMPLETED: <LinkOutlined />,
  ASSISTANT_TURN_RECORDED: <MessageOutlined />,
  ASSISTANT_PATCH_CONFIRMED: <EditOutlined />,
  ASSISTANT_PATCH_CANCELLED: <EditOutlined />,
};

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
  items: JourneyTimelineItem[];
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
  const listRef = useRef<HTMLOListElement>(null);
  const programmaticScrollRef = useRef(false);
  const programmaticScrollIdRef = useRef(0);
  const programmaticScrollTimeoutRef = useRef<number | undefined>(undefined);
  const removeProgrammaticScrollEndListenerRef = useRef<(() => void) | undefined>(undefined);

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
  const returnToCurrent = () => setFollowState({
    mode: 'FOLLOWING',
    currentCheckpointItemId: checkpointItemId,
    scrollToCheckpointItemId: checkpointItemId,
  });
  const renderItem = (item: JourneyTimelineItem, index: number) => {
    const isCurrent = item.itemId === checkpointItemId;
    const content = <>
      <span className="guanyijia-timeline-copy">
        <strong>{item.title}</strong>
        <small>{item.summary}</small>
      </span>
      {item.conflicts?.map((conflict) => <span className="guanyijia-conflict-impact" key={conflict.conflictId}>
        {conflict.affectedObjects.join(' · ')}
      </span>)}
    </>;
    return <li
      id={`guanyijia-workflow-step:${item.itemId}`}
      key={item.itemId}
      className={`guanyijia-timeline-item ${item.state.toLowerCase()}`}
      data-review-anchor={`timeline:${item.itemId}`}
      aria-label={`${item.title} ${item.summary}`}
      aria-current={isCurrent ? 'step' : undefined}
      aria-setsize={total}
      aria-posinset={omitted + index + 1}
    >
      <span className="guanyijia-timeline-rail" aria-hidden="true">{kindIcon[item.kind] ?? <CheckCircleOutlined />}</span>
      {item.action ? <button
        id={`guanyijia-timeline:${item.itemId}`}
        type="button"
        className={selectedItemId === item.itemId ? 'selected' : ''}
        aria-label={item.action.type === 'OPEN_DOCUMENT' ? `打开${item.title}`
          : item.action.type === 'OPEN_SOURCE_DETAILS' ? `查看${item.title}详情`
            : item.action.type === 'OPEN_DELIVERABLE' ? `查看${item.title}`
              : `审阅${item.title}`}
        onClick={() => selectItem(item)}
      >{content}</button> : <div className="guanyijia-timeline-static">{content}</div>}
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
