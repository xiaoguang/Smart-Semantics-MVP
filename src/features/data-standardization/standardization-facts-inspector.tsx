import { CloseOutlined } from '@ant-design/icons';
import { Button, Empty, Tag } from 'antd';
import type { ReactNode } from 'react';
import type { StandardizationFactsInspectorModel } from './guanyijia-workbench-runtime.ts';
import { displaySourceName, sourceClassLabel } from './human-readable-evidence.ts';
import { MysqlSchemaEvidenceView } from './mysql-schema-evidence-view.tsx';
import type { ReviewEvidenceView } from '../guanyijia-evidence-factory/source-review-document.ts';

const evidenceStatusLabel = {
  FACT: '已确认结论',
  INFERENCE: '待核对结论',
  GAP: '资料缺口',
  CONFLICT: '来源差异',
} as const;

function highlightedSqlLine(line: string) {
  return line.split(/(\b(?:CREATE|TABLE|PRIMARY|KEY|NOT|NULL|DEFAULT|COMMENT|INDEX|FOREIGN|REFERENCES)\b|`[^`]+`|'[^']*')/giu)
    .filter((part) => part.length > 0)
    .map((part, index) => /^\b(?:CREATE|TABLE|PRIMARY|KEY|NOT|NULL|DEFAULT|COMMENT|INDEX|FOREIGN|REFERENCES)\b$/iu.test(part)
      ? <span className="guanyijia-sql-keyword" key={`${part}:${index}`}>{part}</span>
      : /^`[^`]+`$/u.test(part)
        ? <span className="guanyijia-sql-identifier" key={`${part}:${index}`}>{part}</span>
        : /^'[^']*'$/u.test(part)
          ? <span className="guanyijia-sql-string" key={`${part}:${index}`}>{part}</span>
          : <span key={`${part}:${index}`}>{part}</span>);
}

function decodeVisibleSourceText(value: string) {
  return value
    .replace(/&amp;gt;|&gt;/gu, '>')
    .replace(/&amp;lt;|&lt;/gu, '<')
    .replace(/&amp;amp;|&amp;/gu, '&')
    .replace(/&quot;/gu, '"')
    .replace(/&#39;/gu, "'");
}

function sourceExcerptCode(source: string, language: 'sql' | 'text') {
  const visibleSource = decodeVisibleSourceText(source);
  return <div className="guanyijia-source-code-wrap">
    <small>以下行号仅表示本段摘录内的位置。</small>
    <pre className={`guanyijia-source-code language-${language}`} tabIndex={0} aria-label={language === 'sql' ? 'SQL 摘录' : '来源代码摘录'}>
    <code>{visibleSource.split('\n').map((line, index) => <span className="guanyijia-source-code-line" key={index}>
      <i aria-hidden="true">{index + 1}</i><span>{language === 'sql' ? highlightedSqlLine(line) : line}</span>{'\n'}
    </span>)}</code>
    </pre>
  </div>;
}

function reviewEvidenceViewContent(view: ReviewEvidenceView | undefined) {
  if (!view) return null;
  if (view.kind === 'MYSQL_SCHEMA') {
    return <MysqlSchemaEvidenceView evidence={view} />;
  }
  if (view.kind === 'DOCUMENT_SECTION') return <section className="guanyijia-inspector-document-evidence">
    <strong>{view.title}</strong><p>{view.excerpt}</p>
    <small>来源位置：{view.locationValue}</small>
  </section>;
  if (view.kind === 'TERM_RELATION') return <section className="guanyijia-inspector-term-evidence">
    <strong>{view.title}</strong>
    <table aria-label={`${view.title}相关术语`}><thead><tr><th>术语</th><th>说明</th><th>领域</th></tr></thead><tbody>
      {view.terms.map((term) => <tr key={term.name}><th>{term.name}</th><td>{term.definition}</td><td>{term.domain}</td></tr>)}
    </tbody></table>
    <table aria-label={`${view.title}相关关系`}><thead><tr><th>起点</th><th>关系</th><th>终点</th><th>说明</th></tr></thead><tbody>
      {view.relations.map((relation, index) => <tr key={`${relation.subject}:${relation.predicate}:${relation.object}:${index}`}><td>{relation.subject}</td><td>{relation.predicate}</td><td>{relation.object}</td><td>{relation.description}</td></tr>)}
    </tbody></table>
    <small>来源位置：{view.locationValue}</small>
  </section>;
  if (view.kind === 'SOURCE_EXCERPT') return <section className="guanyijia-inspector-source-excerpt">
    <strong>{view.title}</strong>
    {sourceExcerptCode(view.rawExcerpt, view.language)}
    <small>来源位置：{view.locationValue}</small>
  </section>;
  return null;
}

export default function StandardizationFactsInspector({
  model,
  sources = [],
  selectedSourceId,
  onSelectSource,
  onClose,
  closeLabel = '关闭来源资料',
  layerId = 'inspector:inline',
  modal = false,
  reviewEvidenceView,
  comparisonEvidence = [],
  workflow,
  showMaterialDetails = true,
}: {
  model?: StandardizationFactsInspectorModel;
  sources?: Array<{ sourceId: string; sourceName: string; status: string; sourceClass?: 'REAL' | 'DEMO_POLICY' | 'DERIVED' }>;
  selectedSourceId?: string;
  onSelectSource?(sourceId: string): void;
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
  /** The workflow is separate from source material and owns the lower rail. */
  workflow?: ReactNode;
  /** Detailed material is now presented in the assistant current-context area. */
  showMaterialDetails?: boolean;
}) {
  const selectedSource = sources.find((source) => source.sourceId === (selectedSourceId ?? model?.sourceId));
  const sourceClass = selectedSource?.sourceClass ?? 'REAL';
  const sourceStatus = selectedSource?.status;
  const hasReadableReviewEvidence = Boolean(reviewEvidenceView && model?.block?.readableEvidence);
  const requiresStructuredReviewEvidence = Boolean(model?.block?.readableEvidence && (
    model.sourceId === 'guanyijia_official_docs'
    || model.sourceId === 'guanyijia_demo_policy'
    || model.sourceId === 'guanyijia_semantica_demo'
  ));
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
    <section className="guanyijia-source-list" aria-label="资料来源列表">
      {sources.map((source) => <button type="button" onClick={() => onSelectSource?.(source.sourceId)} className={`guanyijia-source-list-row status-${source.status.toLowerCase()} ${source.sourceId === (selectedSourceId ?? model?.sourceId) ? 'selected' : ''}`} key={source.sourceId}>
        <span className="guanyijia-source-status" aria-hidden="true" />
        <strong>{displaySourceName(source.sourceId, source.sourceName)}</strong>
        <small>{source.status === 'ALIGNED' || source.status === 'REVIEWED' ? '已审阅'
          : source.status === 'DOCUMENT_READY' ? '待审阅'
            : source.status === 'CONFLICT_BLOCKED' ? '审阅中 · 有待保存差异'
              : source.status === 'READING' ? '读取中' : '待读取'}</small>
      </button>)}
    </section>
    {workflow && <section className="guanyijia-workflow-panel" aria-label="标准化流程">
      {workflow}
    </section>}
    {showMaterialDetails && !model && selectedSource ? <div className="guanyijia-inspector-body guanyijia-source-summary-preview" id="guanyijia-facts-inspector-content" tabIndex={-1}>
      <div className="guanyijia-inspector-tags"><Tag color={sourceClass === 'REAL' ? 'green' : sourceClass === 'DERIVED' ? 'purple' : 'orange'}>{sourceClassLabel(sourceClass)}</Tag></div>
      <p className="guanyijia-inspector-summary">{selectedSource.sourceId.startsWith('pre-run:') ? '该来源将在开始资料整理后按顺序载入。' : '该资料已准备好。'}</p>
      <dl className="guanyijia-fact-list">
        <div><dt>当前状态</dt><dd>{sourceStatus === 'PENDING' ? '等待按顺序载入' : '等待选择可读内容'}</dd></div>
      </dl>
    </div> : showMaterialDetails && !model ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择来源或审阅结论，查看支持它的材料" /> : showMaterialDetails && model ? <div className="guanyijia-inspector-body" id="guanyijia-facts-inspector-content" tabIndex={-1}>
      <div className="guanyijia-inspector-tags">
        <Tag
          color={model.sourceClass === 'REAL' ? 'green' : model.sourceClass === 'DERIVED' ? 'purple' : 'orange'}
          style={model.sourceClass === 'REAL' ? { color: '#237804' } : undefined}
        >
          {sourceClassLabel(model.sourceClass)}
        </Tag>
      </div>
      <p className="guanyijia-inspector-summary">以下内容是支持当前审阅结论的已保存来源材料。</p>
      {model.block && <section className="guanyijia-inspector-block">
        <header><Tag>{evidenceStatusLabel[model.block.evidenceStatus]}</Tag><strong>{model.block.label}</strong></header>
        {model.block.readableEvidence && <div className="guanyijia-readable-evidence">
          {reviewEvidenceViewContent(reviewEvidenceView) ?? (requiresStructuredReviewEvidence
            ? <p>这段来源材料暂时无法显示。</p>
            : <p>{model.block.readableEvidence.excerpt}</p>)}
          {!hasReadableReviewEvidence && <dl><div><dt>{model.block.readableEvidence.locationLabel}</dt><dd>{model.block.readableEvidence.locationValue}</dd></div></dl>}
        </div>}
      </section>}
      {comparisonEvidence.length > 1 && <section className="guanyijia-inspector-block guanyijia-comparison-material" aria-label="双方来源材料">
        <header><strong>双方来源材料</strong></header>
        {comparisonEvidence.map((evidence) => <article key={`${evidence.sourceName}:${evidence.title}`}>
          <strong>{evidence.sourceName} · {evidence.title}</strong>
          <p>{evidence.excerpt}</p>
          <small>{evidence.locationLabel}：{evidence.locationValue}</small>
        </article>)}
      </section>}
    </div> : null}
  </aside>;
}
