import { SendOutlined } from '@ant-design/icons';
import { Button, Input, Tag } from 'antd';
import { useState } from 'react';
import { reviewAssistantDiffWindow } from './review-assistant.ts';
import { projectReviewAssistantHistory } from './review-assistant-panel-model.ts';
import { projectReviewAssistantPlacement } from './review-assistant-placement.ts';
import type {
  ReviewAssistantHistoryItem,
  ReviewAssistantTarget,
} from './review-assistant-types.ts';

const diffLineLabel = { UNCHANGED: ' ', REMOVED: '−', ADDED: '+' } as const;

function valueText(value: unknown) {
  if (typeof value === 'string') return value;
  if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
    const text = (value as Record<string, unknown>).text;
    if (typeof text === 'string') return text;
  }
  return JSON.stringify(value);
}

function targetLabel(target: ReviewAssistantTarget) {
  if (target.kind === 'CONFLICT') return '查看来源差异';
  if (target.kind === 'EVIDENCE') return '查看来源依据';
  if (target.kind === 'AFFECTED_OBJECT') return '查看影响对象';
  return target.blockId ? '定位到识别结果' : '打开来源文档';
}

export default function ReviewAssistantPanel({
  history,
  historyTotal,
  historyLoaded,
  pendingProposal,
  draft,
  busy,
  canSend,
  canConfirm,
  onDraftChange,
  onSend,
  onConfirm,
  onCancel,
  onOpenTarget,
  onLoadHistory,
}: {
  history: ReviewAssistantHistoryItem[];
  historyTotal: number;
  historyLoaded: boolean;
  pendingProposal?: ReviewAssistantHistoryItem;
  draft: string;
  busy: boolean;
  canSend: boolean;
  canConfirm: boolean;
  onDraftChange(value: string, selectionStart: number, selectionEnd: number): void;
  onSend(): void;
  onConfirm(item: ReviewAssistantHistoryItem): void;
  onCancel(item: ReviewAssistantHistoryItem): void;
  onOpenTarget(target: ReviewAssistantTarget, trigger: HTMLElement): void;
  onLoadHistory(): void;
}) {
  const [expandedByUser, setExpandedByUser] = useState(false);
  const placement = projectReviewAssistantPlacement({
    activeReview: true,
    hasCurrentContext: false,
    hasPendingProposal: Boolean(pendingProposal),
    expandedByUser,
  });
  const expanded = placement?.presentation === 'EXPANDED';
  const { visibleHistory, loadHistoryLabel } = projectReviewAssistantHistory({
    history, historyTotal, historyLoaded, pendingProposal,
  });
  return <section
    id="guanyijia-review-assistant"
    className={`guanyijia-review-assistant-panel ${expanded ? 'expanded' : 'compact'}`}
    aria-label="审阅助手"
    data-review-anchor="assistant:panel"
  >
    {expanded && (loadHistoryLabel || (historyLoaded && visibleHistory.length > 0)) && <div
      className="guanyijia-review-assistant-expanded-history"
      aria-live="polite"
    >
      {loadHistoryLabel && <div className="guanyijia-review-assistant-history">
        <Button type="link" onClick={onLoadHistory}>{loadHistoryLabel}</Button>
      </div>}
      {historyLoaded && visibleHistory.length > 0 && <div className="guanyijia-review-assistant-history">
      {visibleHistory.map((item) => {
        const targets = item.response.kind === 'OPEN_TARGET'
          ? [item.response.target]
          : 'targets' in item.response ? item.response.targets : [];
        const proposal = item.response.kind === 'PATCH_PREVIEW' ? item.response.proposal : undefined;
        const diffWindow = proposal ? reviewAssistantDiffWindow(proposal.preview.section.lines) : undefined;
        return <article
          key={item.eventId}
          data-review-anchor={`assistant:${item.eventId}`}
          aria-setsize={historyTotal}
          aria-posinset={item.position}
        >
          <p className="guanyijia-review-assistant-question"><strong>你</strong>{item.message}</p>
          <div className="guanyijia-review-assistant-answer">
            <strong>{item.response.title}</strong>
            {item.response.body.map((paragraph, index) => <p key={`${item.eventId}:p:${index}`}>{paragraph}</p>)}
            {targets.map((target, index) => <Button
              type="link"
              size="small"
              key={`${item.eventId}:target:${index}`}
              id={`guanyijia-assistant-target:${item.eventId}:${index}`}
              data-review-focus={`guanyijia-assistant-target:${item.eventId}:${index}`}
              onClick={(event) => {
                onOpenTarget(target, event.currentTarget);
              }}
            >{targetLabel(target)}</Button>)}
            {proposal && <section className="guanyijia-review-assistant-preview" aria-label="助手修改预览">
              <div className="guanyijia-review-assistant-before-after">
                <div><span>修改前</span><strong>{proposal.beforeBlock.label}</strong><p>{valueText(proposal.beforeBlock.value)}</p></div>
                <div><span>修改后</span><strong>{proposal.preview.block.after.label}</strong><p>{valueText(proposal.preview.block.after.value)}</p></div>
                <div><span>修改前结论</span><p>{proposal.preview.assertion.before.statement}</p></div>
                <div><span>修改后结论</span><p>{proposal.preview.assertion.after.statement}</p></div>
              </div>
              <details>
                <summary>查看文档变化预览</summary>
                <pre className="guanyijia-markdown-diff">
                  {diffWindow!.omittedBefore > 0 && <span>… 已省略前 {diffWindow!.omittedBefore} 行{`\n`}</span>}
                  {diffWindow!.lines.map((line, index) => <span
                  className={line.type.toLowerCase()}
                  key={`${item.eventId}:${line.type}:${index}`}
                ><b>{diffLineLabel[line.type]}</b>{line.line}{'\n'}</span>)}
                  {diffWindow!.omittedAfter > 0 && <span>… 已省略后 {diffWindow!.omittedAfter} 行{`\n`}</span>}
                </pre>
              </details>
              <div className="guanyijia-impact-preview">
                <div><strong>后续核对</strong>{proposal.preview.affectedConflicts.length
                  ? proposal.preview.affectedConflicts.slice(0, 20).map((conflict) => <p key={conflict.conflictId}>
                      {conflict.title}
                    </p>)
                  : <p>本次修改不会新增需要处理的来源差异。</p>}</div>
                <div><strong>文档影响</strong><p>确认后会更新当前审阅文档和对应结论，不会改动来源材料。</p></div>
              </div>
              {item.proposalStatus === 'PENDING' && <div className="guanyijia-assistant-proposal-actions">
                <Button loading={busy} disabled={!canConfirm} onClick={() => onCancel(item)}>放弃修改建议</Button>
                <Button
                  type="primary"
                  data-workflow-primary="true"
                  loading={busy}
                  disabled={!canConfirm}
                  onClick={() => onConfirm(item)}
                >应用此修改</Button>
              </div>}
              {item.proposalStatus !== 'PENDING' && <Tag color={item.proposalStatus === 'CONFIRMED' ? 'green' : 'default'}>
                {item.proposalStatus === 'CONFIRMED' ? '已确认应用'
                  : item.proposalStatus === 'CANCELLED' ? '已取消，文档未修订' : '已被更新建议取代'}
              </Tag>}
              {!canConfirm && item.proposalStatus === 'PENDING' && <small>当前角色可查看预览，但无权确认修改。</small>}
            </section>}
          </div>
        </article>;
      })}
      </div>}
    </div>}
    {!pendingProposal && historyTotal > 0 && <Button
      className="guanyijia-review-assistant-toggle"
      type="link"
      aria-expanded={expanded}
      onClick={() => setExpandedByUser((value) => !value)}
    >{expanded ? '收起对话' : '展开对话'}</Button>}
    <div
      className="guanyijia-review-assistant-composer"
      data-review-anchor="assistant-composer"
      data-review-focus="guanyijia-assistant-composer"
    >
      <Input.TextArea
        id="guanyijia-assistant-composer"
        value={draft}
        onChange={(event) => onDraftChange(
          event.target.value,
          event.target.selectionStart ?? event.target.value.length,
          event.target.selectionEnd ?? event.target.value.length,
        )}
        onSelect={(event) => onDraftChange(
          event.currentTarget.value,
          event.currentTarget.selectionStart ?? event.currentTarget.value.length,
          event.currentTarget.selectionEnd ?? event.currentTarget.value.length,
        )}
        onPressEnter={(event) => {
          if (!event.shiftKey) {
            event.preventDefault();
            if (draft.trim() && !busy && canSend) onSend();
          }
        }}
        autoSize={{ minRows: 1, maxRows: 5 }}
        maxLength={4000}
        placeholder="问我当前结论、来源依据、资料差异或业务影响。"
      />
      <Button
        aria-label="发送给审阅助手"
        icon={<SendOutlined />}
        disabled={!draft.trim() || !canSend}
        loading={busy}
        onClick={onSend}
      />
    </div>
  </section>;
}
