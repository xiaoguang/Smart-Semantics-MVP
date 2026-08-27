import { useEffect, useRef } from 'react';
import { Button, Collapse, Space, Tag, Typography } from 'antd';
import { DownOutlined, RightOutlined } from '@ant-design/icons';
import type { AnalysisTimelineLine } from './analysis-timeline.ts';
import type { ReviewChoice, ReviewPrompt } from './review-conversation.ts';
import type { ReviewBasis, ReviewChange, ReviewGroupView } from './review-batch.ts';
import type { HydratedResult, ReviewDecision } from './runtime-types.ts';
import type { ModelingObjectRef } from './modeling-object-projection.ts';
import type { FixtureDocument } from './types.ts';
import { projectPublicationChecks, type PublicationChecksView } from './publication-checks.ts';
import { WorkflowStatusMark } from './workflow-status-mark.ts';
import type { ReviewItemGuidance } from './review-interaction.ts';
import { projectReviewSections } from './review-sections.ts';
import { SemanticObjectTag, type SemanticVisualKind } from '../../components/semantic-object-visuals.ts';

const basisLabels: Record<ReviewBasis, string> = {
  EXPLICIT: '资料明确', INFERRED: '基于推断', TECHNICAL: '系统生成', ATTENTION: '重点确认',
};
const changeLabels: Record<ReviewChange, string> = {
  ADDED: '新增', MODIFIED: '修改', INHERITED: '继承',
};

export function AnalysisProgress({ lines }: { lines: AnalysisTimelineLine[] }) {
  const completed = lines.every((line) => line.status === 'DONE');
  return <Collapse className="analysis-progress" ghost defaultActiveKey={['analysis']} items={[{
    key: 'analysis',
    label: completed ? '分析完成 · 查看处理过程' : '正在分析资料…',
    children: <div>{lines.map((line) => <div key={line.id} className={`analysis-line ${line.status.toLowerCase()}`}><WorkflowStatusMark compact tone={line.status === 'DONE' ? 'SUCCESS' : line.status === 'ACTIVE' ? 'ACTIVE' : 'PENDING'} label={line.status === 'DONE' ? '已完成' : line.status === 'ACTIVE' ? '处理中' : '等待处理'} />{line.label}</div>)}</div>,
  }]} />;
}

export function PublicationChecksDetails({ view, showSummary = false }: {
  view: PublicationChecksView;
  showSummary?: boolean;
}) {
  return <div className="publication-check-details">
    {showSummary && <p className="publication-check-summary">{view.summary}</p>}
    {view.items.map((item) => <div className="publication-check-row" key={item.id}>
      <WorkflowStatusMark compact tone={item.status === 'PASSED' ? 'SUCCESS' : item.status === 'FAILED' ? 'ERROR' : 'PENDING'} label={item.status === 'PASSED' ? '已通过' : item.status === 'FAILED' ? '需要处理' : '发布时检查'} />
      <span><strong>{item.title}</strong><small>{item.detail}</small></span>
    </div>)}
  </div>;
}

function ReviewItemRow({ item, decision, readOnly, onInspect, onDecide }: {
  item: ReviewItemGuidance;
  decision?: ReviewDecision;
  readOnly?: boolean;
  onInspect?(ref: ModelingObjectRef): void;
  onDecide(item: ReviewItemGuidance, decision: 'APPROVED' | 'REJECTED'): void;
}) {
  return <article id={`review-item-${item.itemId}`} className="review-item">
    <div className="review-item-title"><strong>{item.name}</strong><span><Tag>{changeLabels[item.change]}</Tag><Tag color={item.recommendation === 'ADOPT' ? 'green' : 'orange'}>{item.recommendation === 'ADOPT' ? '建议采纳' : '建议重点确认'}</Tag></span></div>
    <div className="review-item-target"><SemanticObjectTag kind={item.type as SemanticVisualKind} />{item.target}</div>
    <Typography.Paragraph type="secondary">{item.reason}</Typography.Paragraph>
    <Space wrap size={4}>
      {!readOnly && !decision && <><Button type="primary" size="small" onClick={() => onDecide(item, 'APPROVED')}>加入草稿</Button><Button size="small" onClick={() => onDecide(item, 'REJECTED')}>不加入草稿</Button></>}
      {!readOnly && decision?.decision === 'APPROVED' && <Button size="small" onClick={() => onDecide(item, 'REJECTED')}>提出意见</Button>}
      {!readOnly && decision?.decision === 'REJECTED' && <Button size="small" onClick={() => onDecide(item, 'APPROVED')}>改为加入草稿</Button>}
      {item.targetRef && <Button type="link" size="small" onClick={() => onInspect?.(item.targetRef!)}>查看定义</Button>}
      <Collapse ghost size="small" className="review-item-evidence" items={[{
        key: 'evidence', label: '查看依据',
        children: <Space wrap size={4}><Tag>{basisLabels[item.basis]}</Tag><Tag>{item.evidenceCount} 处来源</Tag>{item.confidence !== undefined && <Tag title="来自精确关联的 Java 候选 ID">置信度 {Math.round(item.confidence * 100)}%</Tag>}{item.affectedRefs.map((ref) => <Tag key={ref}>{ref}</Tag>)}</Space>,
      }]} />
    </Space>
    {decision?.decision === 'REJECTED' && <div className="review-item-decision"><strong>意见：</strong>{decision.reason}</div>}
    {decision?.decision === 'APPROVED' && <div className="review-item-decision">{decision.source === 'AUTO' ? '系统加入草稿' : '人工加入草稿'} · {decision.reason}</div>}
  </article>;
}

function ReviewItems({ items, decisions, readOnly, onInspect, onDecide }: {
  items: ReviewItemGuidance[];
  decisions: Record<string, ReviewDecision>;
  readOnly?: boolean;
  onInspect?(ref: ModelingObjectRef): void;
  onDecide(item: ReviewItemGuidance, decision: 'APPROVED' | 'REJECTED'): void;
}) {
  return <div className="review-items">{items.map((item) => <ReviewItemRow
    key={item.itemId}
    item={item}
    decision={decisions[item.itemId]}
    readOnly={readOnly}
    onInspect={onInspect}
    onDecide={onDecide}
  />)}</div>;
}

export function ReviewGroupSummary({
  group, result, onInspect, onDecide,
}: {
  group: ReviewGroupView;
  result: HydratedResult;
  onInspect?(ref: ModelingObjectRef): void;
  onDecide(item: ReviewItemGuidance, decision: 'APPROVED' | 'REJECTED'): void;
}) {
  const sections = projectReviewSections(group, result.decisions);
  const pendingCount = sections.pending.priority.length + sections.pending.other.length;
  const shared = { decisions: result.decisions, readOnly: result.status === 'PUBLISHED', onInspect, onDecide };
  return <section className="review-batch-context">
    <div className="review-group-heading"><div><strong>{group.name}</strong><p>{group.purpose}</p></div><Tag color="blue">待你确认 {pendingCount}</Tag></div>
    <div id={`review-list-${group.id}`} className="review-list-section">
      {sections.pending.priority.length > 0 && <section className="review-section"><Typography.Text strong>建议重点确认（{sections.pending.priority.length}）</Typography.Text><ReviewItems {...shared} items={sections.pending.priority} /></section>}
      {sections.pending.other.length > 0 && <Collapse ghost items={[{
        key: 'pending-other', label: `其余待确认（${sections.pending.other.length}）`,
        children: <ReviewItems {...shared} items={sections.pending.other} />,
      }]} />}
      {sections.adopted.length > 0 && <Collapse ghost items={[{
        key: 'adopted', label: `已加入草稿（${sections.adopted.length}）`,
        children: <ReviewItems {...shared} items={sections.adopted.map((entry) => entry.item)} />,
      }]} />}
      {sections.rejected.length > 0 && <Collapse ghost defaultActiveKey={['rejected']} items={[{
        key: 'rejected', label: `已提出意见（${sections.rejected.length}）`,
        children: <ReviewItems {...shared} items={sections.rejected.map((entry) => entry.item)} />,
      }]} />}
    </div>
  </section>;
}

export function ConversationQuestion({ prompt, document, onSelect, checksVisible, onChecksVisibleChange, focused }: {
  prompt: ReviewPrompt;
  document?: FixtureDocument;
  onSelect(choice: ReviewChoice): void;
  checksVisible?: boolean;
  onChecksVisibleChange?(visible: boolean): void;
  focused?: boolean;
}) {
  const checksRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (checksVisible) checksRef.current?.scrollIntoView({ block: 'nearest' });
  }, [checksVisible]);
  return <section id="active-review-prompt" className={`conversation-question ${focused ? 'workflow-focus-highlight' : ''}`} aria-label={prompt.heading}>
    {prompt.kind === 'PUBLISH' ? <><Typography.Title level={5}>{prompt.heading}</Typography.Title><Typography.Paragraph>{prompt.description}</Typography.Paragraph></>
      : <Typography.Text strong>如何处理本组？</Typography.Text>}
    {prompt.kind === 'PUBLISH' && document && <>
      <button
        type="button"
        className="publish-checks-disclosure"
        aria-expanded={Boolean(checksVisible)}
        aria-controls="publish-checks"
        onClick={() => onChecksVisibleChange?.(!checksVisible)}
      >
        {checksVisible ? <DownOutlined /> : <RightOutlined />}
        <span>{checksVisible ? '收起发布检查' : '发布前将确认 4 项内容'}</span>
      </button>
      {checksVisible && <div ref={checksRef} id="publish-checks" className="publish-checks-inline"><PublicationChecksDetails view={projectPublicationChecks(document, [])} showSummary /></div>}
    </>}
    <div className="conversation-options">{prompt.choices.map((choice) => <button type="button" key={choice.id} onClick={() => onSelect(choice)}>
      <span className="option-radio" aria-hidden="true" />
      <span><strong>{choice.label}{choice.recommended && <Tag color="blue">推荐</Tag>}</strong><small>{choice.description}</small></span>
    </button>)}</div>
    {prompt.kind === 'GROUP' && <Typography.Text className="review-auto-save" type="secondary">审核进度会自动保存</Typography.Text>}
  </section>;
}
