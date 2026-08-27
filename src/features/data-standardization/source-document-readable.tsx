import { parseNineSectionMarkdown, standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import type { ReactNode } from 'react';
import type { SourceDocumentBlock } from '../source-documents/types.ts';
import type { SourceDocumentTraceLink } from './human-readable-evidence.ts';
import { MysqlSchemaEvidenceView } from './mysql-schema-evidence-view.tsx';
import type {
  ReviewClaim,
  ReviewEvidenceView,
  SourceReviewEvidence,
} from '../guanyijia-evidence-factory/source-review-document.ts';
import { reviewEvidenceViewForClaim } from '../guanyijia-evidence-factory/source-review-document.ts';

type MarkdownAnchor = Pick<SourceDocumentTraceLink, 'blockId' | 'markdownAnchor'> & { label: string };

type EvidenceTrace = {
  blockId?: string;
  linePrefix: string;
  markdownAnchor: string;
  evidenceRefs: string[];
  section?: unknown;
};

type EvidenceAnnotatedMarkdown = {
  traceLinks: EvidenceTrace[];
  onEvidenceSelect(trace: EvidenceTrace, trigger: HTMLElement): void;
  /** Curated documents render review claims first, then show the source material beneath them. */
  claims?: ReviewClaim[];
  sourceEvidence?: SourceReviewEvidence[];
  evidenceViews?: ReviewEvidenceView[];
};

function anchorForLine(line: string, anchors: MarkdownAnchor[]) {
  const text = line.trim().replace(/^[-*]\s+/, '');
  return anchors.find((anchor) => text === anchor.label || text.startsWith(`${anchor.label}：`))?.markdownAnchor;
}

function evidenceTraceForLine(line: string, activeAnchor: string | undefined, evidence?: EvidenceAnnotatedMarkdown) {
  return (activeAnchor
    ? evidence?.traceLinks.find((trace) => trace.markdownAnchor === activeAnchor)
    : undefined)
    ?? evidence?.traceLinks.find((trace) => trace.linePrefix && line.includes(trace.linePrefix));
}

function readableLine(line: string, trace?: EvidenceTrace) {
  return trace ? line.replace(trace.linePrefix, '').replace(/\s{2,}/g, ' ').trim() : line;
}

function evidenceButton(trace: EvidenceTrace, evidence?: EvidenceAnnotatedMarkdown) {
  if (!evidence) return null;
  return <button
    type="button"
    className="guanyijia-curated-evidence-button"
    data-review-focus={`curated-evidence:${trace.markdownAnchor}`}
    onClick={(event) => evidence.onEvidenceSelect(trace, event.currentTarget)}
  >查看来源依据</button>;
}

function claimForTrace(trace: EvidenceTrace | undefined, evidence?: EvidenceAnnotatedMarkdown) {
  if (!trace) return undefined;
  return evidence?.claims?.find((claim) => claim.markdownAnchor === trace.markdownAnchor);
}

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
  // Frozen code snippets may have passed through an HTML-bearing source
  // before packaging. Decode only the five text entities that are safe in a
  // plain-text reader; never render the result as HTML.
  return value
    .replace(/&amp;gt;|&gt;/gu, '>')
    .replace(/&amp;lt;|&lt;/gu, '<')
    .replace(/&amp;amp;|&amp;/gu, '&')
    .replace(/&quot;/gu, '"')
    .replace(/&#39;/gu, "'");
}

function rawCode(source: string, language: 'sql' | 'text', ariaLabel?: string) {
  const visibleSource = decodeVisibleSourceText(source);
  return <div className="guanyijia-source-code-wrap">
    <small>以下行号仅表示本段摘录内的位置。</small>
    <pre className={`guanyijia-source-code language-${language}`} tabIndex={0} aria-label={ariaLabel ?? (language === 'sql' ? 'SQL 摘录' : '来源代码摘录')}>
    <code>{visibleSource.split('\n').map((line, index) => <span className="guanyijia-source-code-line" key={index}>
      <i aria-hidden="true">{index + 1}</i><span>{language === 'sql' ? highlightedSqlLine(line) : line}</span>{'\n'}
    </span>)}</code>
    </pre>
  </div>;
}

/** A concise, user-facing label for the saved material behind one conclusion. */
export function reviewEvidenceViewSummary(view: ReviewEvidenceView): string {
  switch (view.kind) {
    case 'MYSQL_SCHEMA': return '查看字段结构与原始 DDL';
    case 'SOURCE_EXCERPT': return view.language === 'sql' ? '查看 SQL 摘录' : '查看代码摘录';
    case 'DOCUMENT_SECTION': return '查看相关文档章节';
    case 'TERM_RELATION': return '查看术语与关系';
  }
}

/**
 * Presentation-only material renderer shared by the checklist and the
 * standardized-document reader. It works entirely from a validated view, so
 * the reader never needs to reconstruct evidence from raw Markdown.
 */
export function ReviewEvidenceViewDetails({ view }: { view: ReviewEvidenceView }) {
  if (view.kind === 'MYSQL_SCHEMA') return <MysqlSchemaEvidenceView evidence={view} />;
  if (view.kind === 'DOCUMENT_SECTION') return <section className="guanyijia-document-section-evidence">
    <b>{view.title}</b><p>{view.excerpt}</p>
    <small>来源位置：{view.locationValue}</small>
  </section>;
  if (view.kind === 'TERM_RELATION') return <section className="guanyijia-term-relation-evidence">
    <b>{view.title}</b>
    <table aria-label={`${view.title}相关术语`}>
      <thead><tr><th>术语</th><th>说明</th><th>领域</th></tr></thead>
      <tbody>{view.terms.map((term) => <tr key={term.name}><th>{term.name}</th><td>{term.definition}</td><td>{term.domain}</td></tr>)}</tbody>
    </table>
    <table aria-label={`${view.title}相关关系`}>
      <thead><tr><th>起点</th><th>关系</th><th>终点</th><th>说明</th></tr></thead>
      <tbody>{view.relations.map((relation, index) => <tr key={`${relation.subject}:${relation.predicate}:${relation.object}:${index}`}>
        <td>{relation.subject}</td><td>{relation.predicate}</td><td>{relation.object}</td><td>{relation.description}</td>
      </tr>)}</tbody>
    </table>
    <small>来源位置：{view.locationValue}</small>
  </section>;
  return <section className="guanyijia-source-excerpt">
    <b>{view.title}</b>
    {rawCode(view.rawExcerpt, view.language)}
    <small>来源位置：{view.locationValue}</small>
  </section>;
}

function sourceEvidenceContent(source: SourceReviewEvidence, view: ReviewEvidenceView | undefined) {
  if (view) return <ReviewEvidenceViewDetails view={view} />;
  if ((source.excerptKind === 'DOCUMENT_SECTION' || source.excerptKind === 'DERIVED_GRAPH') && !view) {
    return <section className="guanyijia-unavailable-evidence" role="status"><p>这段来源材料暂时无法显示。</p></section>;
  }
  return <section className="guanyijia-source-excerpt">
    <b>{source.title}</b>
    {rawCode(source.excerpt, 'text')}
    <small>来源位置：{source.locationValue}</small>
  </section>;
}

/**
 * One shared, source-aware material reader for a claim in either the
 * checklist or Markdown view.  Keeping this here prevents the workbench from
 * falling back to an anonymous <pre> for one source and a structured table
 * for another.
 */
export function SourceReviewEvidenceDetails({
  source,
  view,
}: {
  source: SourceReviewEvidence;
  view?: ReviewEvidenceView;
}) {
  return <div className="guanyijia-inline-evidence-material">
    {sourceEvidenceContent(source, view)}
  </div>;
}

function claimContent(trace: EvidenceTrace, evidence?: EvidenceAnnotatedMarkdown) {
  const claim = claimForTrace(trace, evidence);
  if (!claim) return null;
  const sources = trace.evidenceRefs.flatMap((reference) => {
    const item = evidence?.sourceEvidence?.find((candidate) => candidate.evidenceRef === reference);
    return item ? [item] : [];
  });
  return <article className="guanyijia-claim-evidence" id={trace.markdownAnchor} tabIndex={-1}>
    <strong>{claim.title}</strong>
    <p>{claim.statement}</p>
    <details>
      <summary>查看来源依据</summary>
      {sources.map((source) => <SourceReviewEvidenceDetails
        key={source.evidenceRef}
        source={source}
        view={evidence?.evidenceViews
          ? reviewEvidenceViewForClaim(evidence.evidenceViews, source.evidenceRef, claim.claimId)
          : undefined}
      />)}
    </details>
  </article>;
}

function renderLines(content: string, sectionKey: string, anchors: MarkdownAnchor[], evidence?: EvidenceAnnotatedMarkdown) {
  const lines = content.split('\n');
  const rows: string[][] = [];
  const body: ReactNode[] = [];
  let list: Array<{ content: string; anchor?: string }> = [];
  let sqlCode: string[] | undefined;
  let activeAnchor: string | undefined;
  const flushList = () => {
    if (!list.length) return;
    body.push(<ul key={`list:${body.length}`}>{list.map((item, index) => {
      const anchor = anchorForLine(item.content, anchors);
      const trace = evidenceTraceForLine(item.content, item.anchor, evidence);
      const elementId = trace?.markdownAnchor ?? anchor;
      return <li id={elementId} tabIndex={elementId ? -1 : undefined} key={`${item.content}:${index}`}>
        {trace && claimForTrace(trace, evidence)
          ? claimContent(trace, evidence)
          : <>{readableLine(item.content, trace)}{trace && evidenceButton(trace, evidence)}</>}
      </li>;
    })}</ul>);
    list = [];
  };
  const flushSqlCode = () => {
    if (!sqlCode) return;
    if (evidence?.claims?.length) {
      sqlCode = undefined;
      return;
    }
    body.push(<pre
      className="guanyijia-curated-sql"
      key={`sql:${body.length}`}
      tabIndex={0}
      aria-label="SQL 证据摘录"
    ><code>{sqlCode.join('\n')}</code></pre>);
    sqlCode = undefined;
  };
  for (const [index, line] of lines.entries()) {
    const trimmed = line.trim();
    if (evidence && sqlCode && /^```\s*$/.test(trimmed)) {
      flushSqlCode();
      continue;
    }
    if (evidence && /^```sql\s*$/i.test(trimmed)) {
      flushList();
      sqlCode = [];
      continue;
    }
    if (sqlCode) {
      sqlCode.push(line);
      continue;
    }
    const anchorMatch = /^<!--\s*([a-z0-9-]+)\s*-->$/i.exec(trimmed);
    if (evidence && anchorMatch) {
      activeAnchor = anchorMatch[1];
      continue;
    }
    if (line.trim().startsWith('|') && line.trim().endsWith('|')) {
      flushList();
      const cells = line.trim().slice(1, -1).split('|').map((cell) => cell.trim());
      if (!/^[-: ]+$/.test(cells.join(''))) rows.push(cells);
      continue;
    }
    if (/^[-*]\s+/.test(line.trim())) {
      list.push({ content: line.trim().replace(/^[-*]\s+/, ''), anchor: activeAnchor });
      continue;
    }
    flushList();
    if (!line.trim()) continue;
    const trace = evidenceTraceForLine(line, activeAnchor, evidence);
    if (trace && claimForTrace(trace, evidence)) {
      body.push(<div key={`claim:${trace.markdownAnchor}`}>{claimContent(trace, evidence)}</div>);
      continue;
    }
    body.push(<p id={trace?.markdownAnchor} tabIndex={trace ? -1 : undefined} key={`p:${index}`}>
      {trace && claimForTrace(trace, evidence)
        ? claimContent(trace, evidence)
        : <>{readableLine(line, trace)}{trace && evidenceButton(trace, evidence)}</>}
    </p>);
  }
  flushList();
  flushSqlCode();
  if (rows.length) {
    body.push(<table key="table"><tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => rowIndex === 0
      ? <th key={cellIndex}>{cell}</th>
      : <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody></table>);
  }
  return <section id={`markdown-${sectionKey}`} data-review-anchor={`markdown:${sectionKey}`}>
    {body}
  </section>;
}

export default function SourceDocumentReadable({
  content,
  blocks = [],
  traceLinks = [],
  evidence,
}: {
  content: string;
  blocks?: SourceDocumentBlock[];
  traceLinks?: SourceDocumentTraceLink[];
  evidence?: EvidenceAnnotatedMarkdown;
}) {
  if (evidence?.claims?.length) {
    const lines = content.split('\n');
    const title = lines.find((line) => /^#\s+/.test(line))?.replace(/^#\s+/, '').trim() ?? 'Markdown 文档';
    const sectionTitles = lines
      .flatMap((line) => /^##\s+(.+)$/u.exec(line)?.[1]?.trim() ?? []);
    const sectionIndexes = [...new Set(evidence.claims.map((claim) => claim.section))]
      .sort((left, right) => left - right);
    return <article className="guanyijia-readable-markdown" aria-label="Markdown 文档">
      <h3>{title}</h3>
      {sectionIndexes.map((sectionIndex, index) => <section key={sectionIndex}>
        <h4>{sectionTitles[index] ?? `审阅内容 ${sectionIndex}`}</h4>
        {evidence.claims!.filter((claim) => claim.section === sectionIndex).map((claim) => {
          const trace = evidence.traceLinks.find((candidate) => candidate.markdownAnchor === claim.markdownAnchor);
          if (!trace) return null;
          return <div key={claim.claimId}>{claimContent(trace, evidence)}</div>;
        })}
      </section>)}
    </article>;
  }
  const parsed = parseNineSectionMarkdown(content);
  const anchors = traceLinks.flatMap((link) => {
    const block = blocks.find((candidate) => candidate.blockId === link.blockId);
    return block ? [{ ...link, label: block.label }] : [];
  });
  return <article className="guanyijia-readable-markdown" aria-label="完整Markdown">
    <h3>{parsed.title}</h3>
    {standardSectionOrder.map(({ key, heading }) => <section key={key}>
      <h4>{heading}</h4>
      {renderLines(
        parsed.sections[key],
        key,
        anchors.filter((anchor) => anchor.markdownAnchor.startsWith(`markdown-${key.toLowerCase()}-`)),
        evidence,
      )}
    </section>)}
  </article>;
}
