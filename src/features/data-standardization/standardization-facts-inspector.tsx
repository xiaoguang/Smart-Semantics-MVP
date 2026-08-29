import { CloseOutlined } from '@ant-design/icons';
import { Button, Empty, Tag } from 'antd';
import type { ReactNode } from 'react';
import type { StandardizationFactsInspectorModel } from './guanyijia-workbench-runtime.ts';
import { displaySourceName, sourceClassLabel } from './human-readable-evidence.ts';
import type { ReviewEvidenceView } from '../guanyijia-evidence-factory/source-review-document.ts';

const evidenceStatusLabel = {
  FACT: '已确认结论',
  INFERENCE: '待核对结论',
  GAP: '资料缺口',
  CONFLICT: '来源差异',
} as const;

function reviewEvidenceSummary(view: ReviewEvidenceView | undefined) {
  if (!view) return null;
  if (view.kind === 'MYSQL_SCHEMA') return <>
    <p>{view.objectComment ?? `${view.objectName}的已保存结构资料支持当前结论。`}</p>
    <small>来源位置：{view.locationValue}</small>
  </>;
  if (view.kind === 'DOCUMENT_SECTION') return <>
    <p>{view.excerpt}</p><small>来源位置：{view.locationValue}</small>
  </>;
  if (view.kind === 'TERM_RELATION') return <>
    <p>本项关联 {view.terms.length} 个术语和 {view.relations.length} 条关系，用于佐证当前结论。</p>
    <small>来源位置：{view.locationValue}</small>
  </>;
  return <>
    <p>已定位到与当前结论相关的{view.language === 'sql' ? '结构摘录' : '源码摘录'}；技术内容请在正文中阅读。</p>
    <small>来源位置：{view.locationValue}</small>
  </>;
}

function sourceStatusLabel(status: string | undefined) {
  if (status === 'ALIGNED' || status === 'REVIEWED') return '已审阅';
  if (status === 'DOCUMENT_READY') return '待审阅';
  if (status === 'CONFLICT_BLOCKED') return '审阅中 · 有待保存差异';
  if (status === 'READING') return '处理中';
  return '未开始';
}

export default function StandardizationFactsInspector({
  model,
  sources = [],
  selectedSourceId,
  onClose,
  closeLabel = '关闭来源资料',
  layerId = 'inspector:inline',
  modal = false,
  reviewEvidenceView,
  comparisonEvidence = [],
  workflow,
  showMaterialDetails = true,
  onOpenTechnicalEvidence,
}: {
  model?: StandardizationFactsInspectorModel;
  sources?: Array<{ sourceId: string; sourceName: string; status: string; sourceClass?: 'REAL' | 'DEMO_POLICY' | 'DERIVED' }>;
  selectedSourceId?: string;
  onClose?(): void;
  closeLabel?: string;
  layerId?: string;
  modal?: boolean;
  reviewEvidenceView?: ReviewEvidenceView;
  comparisonEvidence?: Array<{
    sourceName: string;
    title: string;
    excerpt: string;
    locationLabel: string;
    locationValue: string;
  }>;
  /** Source progress displayed in the upper rail. */
  workflow?: ReactNode;
  /** The lower evidence panel can be suppressed only by an isolated legacy surface. */
  showMaterialDetails?: boolean;
  /** Moves the reader to the already-selected, exact technical material. */
  onOpenTechnicalEvidence?(): void;
}) {
  const selectedSource = sources.find((source) => source.sourceId === (selectedSourceId ?? model?.sourceId));
  const sourceClass = selectedSource?.sourceClass ?? 'REAL';
  const sourceStatus = selectedSource?.status;
  const sourcePosition = selectedSource ? sources.indexOf(selectedSource) + 1 : undefined;
  const reviewedSources = sources.filter((source) => source.status === 'ALIGNED' || source.status === 'REVIEWED').length;
  const currentSourceName = selectedSource
    ? displaySourceName(selectedSource.sourceId, selectedSource.sourceName)
    : model ? displaySourceName(model.sourceId, model.sourceName) : undefined;
  const hasReadableReviewEvidence = Boolean(reviewEvidenceView && model?.block?.readableEvidence);
  return <aside
    id="guanyijia-facts-inspector"
    className="guanyijia-facts-inspector"
    aria-label="来源资料"
    role={modal ? 'dialog' : undefined}
    aria-modal={modal ? true : undefined}
    data-review-layer-id={layerId}
    data-review-inertable
    tabIndex={modal ? -1 : undefined}
  >
    {modal && onClose && <div className="guanyijia-facts-inspector-close">
      <Button id="guanyijia-facts-inspector:close" type="text" aria-label={closeLabel} icon={<CloseOutlined />} onClick={onClose} />
    </div>}
    <section className="guanyijia-source-progress-panel" aria-label="来源进展">
      <header className="guanyijia-rail-panel-heading">
        <div><span>来源处理 {reviewedSources}/{sources.length}</span><h2>来源进展</h2></div>
        {currentSourceName && <small>当前：{currentSourceName}</small>}
      </header>
      {workflow ? <div className="guanyijia-workflow-panel">{workflow}</div>
        : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未配置来源进展" />}
    </section>
    {showMaterialDetails && <section className="guanyijia-evidence-support-panel" aria-label="证据与支持信息">
      <header className="guanyijia-rail-panel-heading"><h2>证据与支持信息</h2></header>
      {!model && selectedSource ? <div className="guanyijia-inspector-body guanyijia-source-summary-preview" id="guanyijia-facts-inspector-content" tabIndex={-1}>
        <h3>当前来源概览</h3>
        <div className="guanyijia-inspector-tags"><Tag color={sourceClass === 'REAL' ? 'green' : sourceClass === 'DERIVED' ? 'purple' : 'orange'}>{sourceClassLabel(sourceClass)}</Tag></div>
        <p className="guanyijia-inspector-summary">{selectedSource.sourceId.startsWith('pre-run:') ? '该来源将在开始资料整理后按顺序载入。' : '该资料已准备好，可在主阅读区查看其支持的结论。'}</p>
        <dl className="guanyijia-fact-list">
          <div><dt>当前状态</dt><dd>{sourceStatusLabel(sourceStatus)}</dd></div>
          {sourcePosition && <div><dt>处理顺序</dt><dd>第 {sourcePosition} 个来源</dd></div>}
          <div><dt>支持范围</dt><dd>仅支持已保存的来源材料与审阅结论。</dd></div>
        </dl>
      </div> : !model ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择审阅事项，查看支持它的材料" /> : <div className="guanyijia-inspector-body" id="guanyijia-facts-inspector-content" tabIndex={-1}>
        <h3>当前事项</h3>
        <div className="guanyijia-inspector-tags">
          <Tag
            color={model.sourceClass === 'REAL' ? 'green' : model.sourceClass === 'DERIVED' ? 'purple' : 'orange'}
            style={model.sourceClass === 'REAL' ? { color: '#237804' } : undefined}
          >
            {sourceClassLabel(model.sourceClass)}
          </Tag>
          {model.block && <Tag>{evidenceStatusLabel[model.block.evidenceStatus]}</Tag>}
        </div>
        {model.block && <section className="guanyijia-inspector-block">
          <header><strong>{model.block.label}</strong></header>
          <p className="guanyijia-inspector-summary">{model.readSummary}</p>
          {model.block.readableEvidence && <div className="guanyijia-readable-evidence">
            {hasReadableReviewEvidence
              ? reviewEvidenceSummary(reviewEvidenceView)
              : <p>该事项的已声明依据暂时无法验证。请刷新当前步骤后重试。</p>}
          </div>}
          {!hasReadableReviewEvidence && <dl className="guanyijia-fact-list">
            <div><dt>证据来源</dt><dd>{displaySourceName(model.sourceId, model.sourceName)}</dd></div>
            <div><dt>支持范围</dt><dd>支持 {model.evidenceCount} 项已保存依据和 {model.objectCount} 个相关对象。</dd></div>
            <div><dt>不能推出</dt><dd>{model.authority === 'PRIMARY' ? '来源外的业务配置或运行结果。' : '未经主来源核验的正式结论。'}</dd></div>
          </dl>
          }
        </section>}
        {comparisonEvidence.length > 1 && <section className="guanyijia-inspector-block guanyijia-comparison-material" aria-label="双方来源材料">
          <header><strong>双方证据索引</strong></header>
          {comparisonEvidence.map((evidence) => <article key={`${evidence.sourceName}:${evidence.title}`}>
            <strong>{evidence.sourceName} · {evidence.title}</strong>
            <small>{evidence.locationLabel}：{evidence.locationValue}</small>
          </article>)}
        </section>}
        {hasReadableReviewEvidence && <Button type="link" className="guanyijia-open-technical-evidence" onClick={onOpenTechnicalEvidence}>在正文查看技术依据</Button>}
      </div>}
    </section>}
  </aside>;
}
