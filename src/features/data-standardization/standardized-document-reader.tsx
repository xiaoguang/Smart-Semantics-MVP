import { Button } from 'antd';
import type {
  BusinessDocumentSection,
  StandardizedDocumentProjection,
  StandardizedDocumentView,
} from './standardized-document-projection.ts';
import {
  buildStandardizedDocumentReadingEntries,
  isRedundantSectionEntryTitle,
} from './standardized-document-reading.ts';
import { MysqlSchemaEvidenceView } from './mysql-schema-evidence-view.tsx';
import type { MysqlSchemaEvidence } from './mysql-schema-evidence.ts';
import {
  ReviewEvidenceViewDetails,
  reviewEvidenceViewSummary,
} from './source-document-readable.tsx';
import type { ReviewEvidenceView } from '../guanyijia-evidence-factory/source-review-document.ts';

type SchemaDocumentEntry = BusinessDocumentSection['entries'][number] & {
  schemaEvidence: MysqlSchemaEvidence;
  schemaEvidences?: readonly MysqlSchemaEvidence[];
};

function hasSchemaEvidence(entry: BusinessDocumentSection['entries'][number]): entry is SchemaDocumentEntry {
  return entry.schemaEvidence !== undefined;
}

function nonSchemaDetailViews(entry: BusinessDocumentSection['entries'][number]): ReviewEvidenceView[] {
  return (entry.detailViews ?? []).filter((view) => view.kind !== 'MYSQL_SCHEMA');
}

function detailExpanders(entry: BusinessDocumentSection['entries'][number]) {
  return nonSchemaDetailViews(entry).map((view, index) => <details
    className="guanyijia-standardized-document-detail"
    key={`${entry.claimId}:${view.evidenceRef}:${index}`}
  >
    <summary>{reviewEvidenceViewSummary(view)}</summary>
    <ReviewEvidenceViewDetails view={view} />
  </details>);
}

function sectionContents(section: BusinessDocumentSection) {
  const objects = section.entries.filter((entry) => entry.kind === 'OBJECT' || entry.kind === 'TERM' || entry.kind === 'RELATION');
  const notes = section.entries.filter((entry) => entry.kind === 'RULE' || entry.kind === 'GAP');
  const summarizedObjects = objects.filter((entry) => !hasSchemaEvidence(entry));
  const schemaObjects = objects.filter(hasSchemaEvidence);
  const hasFields = summarizedObjects.some((entry) => entry.fields?.length);
  return <>
    {summarizedObjects.length > 0 && <div className="guanyijia-standardized-document-table-wrap">
      <table>
        <thead><tr><th>名称</th><th>业务说明</th>{hasFields && <th>重点字段／术语</th>}</tr></thead>
        <tbody>{summarizedObjects.map((entry) => <tr key={entry.claimId} id={entry.markdownAnchor} tabIndex={-1}>
          <th>{entry.title}</th>
          <td>{entry.statement}{detailExpanders(entry)}</td>
          {hasFields && <td>{entry.fields?.join('、')}</td>}
        </tr>)}</tbody>
      </table>
    </div>}
    {schemaObjects.map((entry) => <article
      className="guanyijia-standardized-document-object"
      id={entry.markdownAnchor}
      tabIndex={-1}
      key={entry.claimId}
    >
      {!isRedundantSectionEntryTitle(section.title, entry.title) && <strong>{entry.title}</strong>}
      <p>{entry.statement}</p>
      <details className="guanyijia-object-details">
        <summary>查看字段结构与原始 DDL</summary>
        <MysqlSchemaEvidenceView evidence={entry.schemaEvidence} />
        {entry.schemaEvidences?.slice(1).map((evidence) => <MysqlSchemaEvidenceView
          evidence={evidence}
          key={`${evidence.objectName}:${evidence.locationValue}`}
        />)}
      </details>
      {detailExpanders(entry)}
    </article>)}
    {notes.map((entry) => <article
      className={`guanyijia-standardized-document-note ${entry.kind === 'GAP' ? 'gap' : 'rule'}`}
      id={entry.markdownAnchor}
      tabIndex={-1}
      key={entry.claimId}
    >
      {!isRedundantSectionEntryTitle(section.title, entry.title) && <strong>{entry.title}</strong>}
      <p>{entry.statement}</p>
      {entry.nextStep && <small>下一步：{entry.nextStep}</small>}
      {detailExpanders(entry)}
    </article>)}
  </>;
}

type NarrativeBlock =
  | { kind: 'PARAGRAPH'; text: string }
  | { kind: 'LIST'; items: string[] }
  | { kind: 'TABLE'; headers: string[]; rows: string[][] };

function tableCells(line: string) {
  return line.trim().replace(/^\|/u, '').replace(/\|$/u, '')
    .split('|').map((cell) => cell.trim());
}

function isMarkdownTableSeparator(line: string) {
  return /^\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$/u.test(line);
}

/**
 * V7 stores source-specific reader context as deterministic Markdown.  This
 * deliberately renders only the small safe subset used by that context:
 * paragraphs, bullet lists and compact tables.  Claims themselves remain
 * below it and are never parsed back out of the context.
 */
function narrativeBlocks(value: string): NarrativeBlock[] {
  return value.trim().split(/\n{2,}/u).map((raw) => {
    const lines = raw.split('\n').map((line) => line.trim()).filter(Boolean);
    if (lines.length >= 2 && lines.every((line) => line.startsWith('|')) && isMarkdownTableSeparator(lines[1]!)) {
      return {
        kind: 'TABLE' as const,
        headers: tableCells(lines[0]!),
        rows: lines.slice(2).map(tableCells),
      };
    }
    if (lines.length > 0 && lines.every((line) => /^[-*]\s+/u.test(line))) {
      return { kind: 'LIST' as const, items: lines.map((line) => line.replace(/^[-*]\s+/u, '')) };
    }
    return { kind: 'PARAGRAPH' as const, text: lines.join(' ') };
  }).filter((block) => block.kind !== 'PARAGRAPH' || block.text.length > 0);
}

function sectionNarrative(value: string | undefined) {
  if (!value) return null;
  return <div className="guanyijia-standardized-document-narrative">
    {narrativeBlocks(value).map((block, index) => {
      if (block.kind === 'LIST') return <ul key={index}>{block.items.map((item, itemIndex) => <li key={itemIndex}>{item}</li>)}</ul>;
      if (block.kind === 'TABLE') return <div className="guanyijia-standardized-document-table-wrap" key={index}>
        <table>
          <thead><tr>{block.headers.map((header, headerIndex) => <th key={headerIndex}>{header}</th>)}</tr></thead>
          <tbody>{block.rows.map((row, rowIndex) => <tr key={rowIndex}>{block.headers.map((_, cellIndex) => <td key={cellIndex}>{row[cellIndex] ?? ''}</td>)}</tr>)}</tbody>
        </table>
      </div>;
      return <p key={index}>{block.text}</p>;
    })}
  </div>;
}

function sourceLines(markdownSource: string) {
  return markdownSource.split('\n');
}

export default function StandardizedDocumentReader({
  document,
  view,
  onViewChange,
  onReturn,
}: {
  document: StandardizedDocumentProjection;
  view: StandardizedDocumentView;
  onViewChange(view: StandardizedDocumentView): void;
  onReturn?(): void;
}) {
  const entries = buildStandardizedDocumentReadingEntries(document);
  const copyMarkdown = async () => {
    if (!navigator.clipboard) return;
    await navigator.clipboard.writeText(document.markdownSource);
  };
  return <section className="guanyijia-standardized-document" aria-label="标准化文档">
    <header className="guanyijia-standardized-document-toolbar">
      <small>{document.revisionLabel} · 只读</small>
      <div role="group" aria-label="标准化文档视图">
        <Button type={view === 'READING' ? 'primary' : 'default'} onClick={() => onViewChange('READING')}>阅读版</Button>
        <Button type={view === 'MARKDOWN_SOURCE' ? 'primary' : 'default'} onClick={() => onViewChange('MARKDOWN_SOURCE')}>Markdown 源文</Button>
        <Button type="link" onClick={() => void copyMarkdown()}>复制</Button>
        {onReturn && <Button type="link" onClick={onReturn}>返回审阅清单</Button>}
      </div>
    </header>
    {view === 'READING' ? <div className="guanyijia-standardized-document-reading">
      <nav aria-label="标准化文档目录">
        {entries.map((section) => <a key={section.sectionId} href={`#standardized-section-${section.sectionId}`}>{section.sectionTitle}</a>)}
      </nav>
      <article>
        {entries.map((section) => <section id={`standardized-section-${section.sectionId}`} key={section.sectionId}>
          <h3>{section.sectionTitle}</h3>
          {section.sectionPurpose && <p className="guanyijia-standardized-document-purpose">{section.sectionPurpose}</p>}
          {sectionNarrative(section.sectionNarrative)}
          {sectionContents({ id: section.sectionId, title: section.sectionTitle, purpose: section.sectionPurpose, entries: section.items })}
        </section>)}
      </article>
    </div> : <div className="guanyijia-standardized-document-source">
      <pre tabIndex={0} aria-label="当前 Markdown 源文"><code>{sourceLines(document.markdownSource).map((line, index) => <span key={index}><i aria-hidden="true">{index + 1}</i><b>{line || ' '}</b>{'\n'}</span>)}</code></pre>
    </div>}
  </section>;
}
