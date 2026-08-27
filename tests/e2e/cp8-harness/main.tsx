/* oxlint-disable react/only-export-components -- isolated Playwright harness entry */
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { sha256HexSync } from '../../../src/features/ai-modeling/sha256.ts';
import {
  ReviewWindowError,
  createGeneratedReviewWindowIndex,
  createIndexedDbReviewWindowIndex,
  createStandardizationReviewWindow,
  mergeReviewWindowPage,
  type ReviewWindowPage,
  type ReviewWindowSummary,
  type ReviewWindowTelemetryEvent,
} from '../../../src/features/review-window/index.ts';
import { ReviewWindowRecovery } from '../../../src/features/review-window/review-window-recovery.tsx';
import type { DeliverableSnapshot } from '../../../src/features/standardization-deliverable/types.ts';
import {
  createCp8RecoveryScenarioAdapter,
  type Cp8RecoveryScenarioAdapter,
} from './recovery-scenario.ts';
import { recoverCasConflict } from './capacity-recovery-consumer.ts';
import './style.css';

const runId = 'cp8-capacity-run';
const body = '缓存中的已校验审阅正文';
const bodySha256 = sha256HexSync(body);
const bodyRef = `sha256:${bodySha256}` as const;

function App() {
  const index = useMemo(() => createGeneratedReviewWindowIndex({
    TIMELINE: 10_000,
    ISSUES: 10_000,
    EVIDENCE: 100_000,
  }), []);
  const telemetry = useMemo<ReviewWindowTelemetryEvent[]>(() => [], []);
  const runtime = useMemo(() => createStandardizationReviewWindow({
    cursorSecret: 'cp8-browser-capacity-secret', index,
    bodies: { async read() { return null; } },
    telemetry: { record(event) { telemetry.push(event); } },
  }), [index, telemetry]);
  const reviewIndex = useMemo(() => {
    let firstAttempt = true;
    const realFactory = globalThis.indexedDB;
    const statefulFactory = {
      open(name: string, version?: number) {
        if (firstAttempt) {
          firstAttempt = false;
          throw new DOMException('审阅索引存储权限暂不可用', 'SecurityError');
        }
        if (!realFactory) throw new DOMException('当前浏览器不支持IndexedDB', 'NotSupportedError');
        return realFactory.open(name, version);
      },
    } as IDBFactory;
    return createIndexedDbReviewWindowIndex({
      factory: statefulFactory,
      databaseName: 'cp8-review-window-retry-v1',
    });
  }, []);
  const recovery = useMemo<Cp8RecoveryScenarioAdapter>(() => createCp8RecoveryScenarioAdapter(), []);
  const [recoveryBusy, setRecoveryBusy] = useState(false);
  const [timeline, setTimeline] = useState<ReviewWindowPage>();
  const [issues, setIssues] = useState<ReviewWindowPage>();
  const [issueWindow, setIssueWindow] = useState<{ items: ReviewWindowSummary[]; spacerBefore: number; spacerAfter: number }>({
    items: [], spacerBefore: 0, spacerAfter: 0,
  });
  const [error, setError] = useState<ReviewWindowError>();
  const [reviewIndexMode, setReviewIndexMode] = useState('索引加载中');
  const [content, setContent] = useState('');
  const [announcement, setAnnouncement] = useState('容量索引初始化中');
  const [anchor] = useState('issue:000000005');
  const [draft, setDraft] = useState('保留助手草稿与选择');
  const [preview, setPreview] = useState(true);
  const [metadataRevision, setMetadataRevision] = useState<number>();
  const [freezeSnapshot, setFreezeSnapshot] = useState<DeliverableSnapshot>();
  const [lastWindowCommitMs, setLastWindowCommitMs] = useState(0);
  const windowUpdateStartedAt = useRef<number | undefined>(undefined);

  useLayoutEffect(() => {
    if (windowUpdateStartedAt.current === undefined) return;
    setLastWindowCommitMs(performance.now() - windowUpdateStartedAt.current);
    windowUpdateStartedAt.current = undefined;
  }, [issueWindow]);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      runtime.read({ runId, epoch: 'e1', stream: 'TIMELINE', filter: 'all' }),
      runtime.read({ runId, epoch: 'e1', stream: 'ISSUES', filter: 'open' }),
    ]).then(([timelinePage, issuePage]) => {
      if (cancelled) return;
      setTimeline(timelinePage);
      setIssues(issuePage);
      setIssueWindow({ items: issuePage.items, spacerBefore: 0, spacerAfter: 0 });
      setAnnouncement('容量索引已就绪');
    });
    return () => { cancelled = true; };
  }, [runtime]);

  useEffect(() => {
    let cancelled = false;
    void reviewIndex.query({
      runId, epoch: 'e1', stream: 'ISSUES', filter: 'open', direction: 'FORWARD', limit: 20,
    }).then((page) => {
      if (!cancelled) setReviewIndexMode(`索引已就绪 ${page.items.length}/${page.total}`);
    }).catch((cause) => {
      if (cancelled) return;
      // The index is only an accelerator. Keep the canonical window readable and
      // do not turn a background storage failure into a user-facing retry task.
      if (cause instanceof ReviewWindowError && cause.code === 'STORE_UNAVAILABLE') {
        setReviewIndexMode('受限列表可用');
        return;
      }
      setReviewIndexMode('受限列表可用');
    });
    return () => { cancelled = true; };
  }, [reviewIndex]);

  const readNextIssues = async () => {
    if (!issues?.nextCursor) return;
    const page = await runtime.read({
      runId, epoch: 'e1', stream: 'ISSUES', filter: 'open', cursor: issues.nextCursor,
    });
    setIssues(page);
    windowUpdateStartedAt.current = performance.now();
    setIssueWindow((current) => mergeReviewWindowPage({
      current, incoming: page.items, direction: 'FORWARD', rowSize: 36, anchorStableKey: anchor,
    }));
  };

  const readBody = async (mode: 'CACHE' | 'OFFLINE' | 'CHECKSUM') => {
    setError(undefined);
    setContent('');
    const cache = {
      async read() { return mode === 'CACHE' ? body : null; },
      async write() {},
      async estimate() { return { usage: 9_000_000, quota: 10_000_000 }; },
    };
    const contentRuntime = createStandardizationReviewWindow({
      cursorSecret: 'cp8-body-secret', index,
      bodies: { async read() { return mode === 'CHECKSUM' ? '被篡改正文' : body; } },
      cache,
      isOnline: () => mode !== 'OFFLINE' && mode !== 'CACHE',
      telemetry: { record(event) { telemetry.push(event); } },
    });
    try {
      const result = await contentRuntime.readContent({ runId, contentRef: bodyRef, expectedSha256: bodySha256 });
      setContent(`${result.source}:${result.content}`);
    } catch (cause) {
      if (cause instanceof ReviewWindowError) setError(cause);
      else throw cause;
    }
  };

  const injectCasConflict = async () => {
    setRecoveryBusy(true);
    setError(undefined);
    try {
      const staleRevision = await recovery.cas.revision();
      const winner = await recovery.cas.advanceWinner();
      const next = await recoverCasConflict(recovery.cas, {
        preview: preview ? 'PREVIEW_READY' : 'PREVIEW_CLEARED', draft, anchor,
        staleRevision,
        winner: {
          revision: winner.revision,
          status: winner.status,
          timelineLength: winner.timeline.length,
        },
      });
      setMetadataRevision(next.revision);
      setPreview(next.preview === 'PREVIEW_READY');
      setAnnouncement('运行已刷新，写命令未自动重放');
    } finally {
      setRecoveryBusy(false);
    }
  };

  const injectQuota = async () => {
    setRecoveryBusy(true);
    setError(undefined);
    try {
      const quota = await recovery.quota.injectQuota();
      setMetadataRevision(await recovery.quota.revision());
      setError(quota);
    } catch (cause) {
      if (cause instanceof ReviewWindowError) setError(cause);
      else throw cause;
    } finally {
      setRecoveryBusy(false);
    }
  };

  const injectFreezePending = async () => {
    setRecoveryBusy(true);
    setError(undefined);
    try {
      setFreezeSnapshot(await recovery.freeze.injectPending());
    } finally {
      setRecoveryBusy(false);
    }
  };

  const reconcileFreeze = async () => {
    setRecoveryBusy(true);
    setError(undefined);
    try {
      setFreezeSnapshot(await recovery.freeze.reconcile());
    } finally {
      setRecoveryBusy(false);
    }
  };

  const expireCursor = async () => {
    const page = await runtime.read({ runId, epoch: 'old', stream: 'ISSUES', filter: 'open' });
    try {
      await runtime.read({ runId, epoch: 'new', stream: 'ISSUES', filter: 'open', cursor: page.nextCursor! });
    } catch (cause) {
      if (cause instanceof ReviewWindowError) setError(cause);
      else throw cause;
    }
  };

  const recover = async () => {
    if (error?.code === 'CURSOR_EXPIRED') {
      const page = await runtime.read({ runId, epoch: 'new', stream: 'ISSUES', filter: 'open' });
      setIssues(page);
      setIssueWindow({ items: page.items, spacerBefore: 0, spacerAfter: 0 });
      setAnnouncement('列表已更新');
      setError(undefined);
      return;
    }
    await readBody('CACHE');
  };

  const queryEvents = telemetry.filter(({ kind }) => kind === 'QUERY');
  const bodyEvents = telemetry.filter(({ kind }) => kind === 'BODY_READ');
  const payloadBytes = new TextEncoder().encode(JSON.stringify({ timeline, issues })).byteLength;
  const visibleIssues = issueWindow.items.slice(-50);

  return <main className="capacity-shell">
    <header><h1>标准化审阅容量与恢复</h1><p aria-live="polite">{announcement}</p></header>
    <dl className="capacity-metrics">
      <div><dt>Timeline summaries</dt><dd data-testid="timeline-count">{timeline?.items.length ?? 0}</dd></div>
      <div><dt>Issue window</dt><dd data-testid="issue-window-count">{issueWindow.items.length}</dd></div>
      <div><dt>Evidence index</dt><dd data-testid="evidence-index-count">100000</dd></div>
      <div><dt>Materialized</dt><dd data-testid="materialized-count">{index.metrics.materializedItemCount}</dd></div>
      <div><dt>Payload bytes</dt><dd data-testid="payload-bytes">{payloadBytes}</dd></div>
      <div><dt>Body reads</dt><dd data-testid="body-read-count">{bodyEvents.length}</dd></div>
      <div><dt>Queries</dt><dd data-testid="query-count">{queryEvents.length}</dd></div>
      <div><dt>Metadata revision</dt><dd data-testid="metadata-revision">{metadataRevision ?? '—'}</dd></div>
      <div><dt>Window commit ms</dt><dd data-testid="window-commit-ms">{lastWindowCommitMs}</dd></div>
    </dl>
    <section><h2>Timeline</h2><ol aria-label="容量时间线">{timeline?.items.map((item, index) => <li key={item.stableKey} aria-setsize={timeline.total} aria-posinset={index + 1}>{item.title}</li>)}</ol></section>
    <section><h2>Issues</h2><div style={{ height: issueWindow.spacerBefore }} aria-hidden="true" /><ol aria-label="问题窗口">{visibleIssues.map((item) => <li
      key={item.stableKey}
      data-anchor={item.stableKey}
      aria-setsize={issues?.total ?? 0}
      aria-posinset={Number(item.stableKey.split(':').at(-1)) + 1}
    >{item.title}</li>)}</ol><button type="button" onClick={() => void readNextIssues()}>读取下一页问题</button></section>
    <output hidden data-testid="review-index-state">{reviewIndexMode}</output>
    <section className="actions" aria-label="故障注入">
      <button type="button" onClick={() => void readBody('CACHE')}>读取离线缓存</button>
      <button type="button" onClick={() => void readBody('OFFLINE')}>注入离线未缓存</button>
      <button type="button" onClick={() => void readBody('CHECKSUM')}>注入正文损坏</button>
      <button type="button" disabled={recoveryBusy} onClick={() => void injectQuota()}>注入存储空间不足</button>
      <button type="button" onClick={() => void expireCursor()}>注入游标过期</button>
      <button type="button" disabled={recoveryBusy} onClick={() => void injectCasConflict()}>注入并发版本冲突</button>
      <button type="button" disabled={recoveryBusy} onClick={() => void injectFreezePending()}>注入定版登记中</button>
    </section>
    <label>助手草稿<textarea value={draft} onChange={(event) => setDraft(event.target.value)} /></label>
    <p data-testid="preview-state">{preview ? 'PREVIEW_READY' : 'PREVIEW_CLEARED'}</p>
    {content && <article aria-label="已校验正文">{content}</article>}
    {error && <ReviewWindowRecovery error={error} onRecover={() => void recover()} />}
    {freezeSnapshot?.pendingCommand && <section role="status"><strong>定版登记中</strong><button type="button" disabled={recoveryBusy} onClick={() => void reconcileFreeze()}>继续登记定版</button></section>}
    {freezeSnapshot?.deliverable?.linkState === 'LINKED' && <section role="status"><strong>定版登记完成</strong><button type="button">交给 AI 建模</button></section>}
  </main>;
}

createRoot(document.getElementById('root')!).render(<App />);
