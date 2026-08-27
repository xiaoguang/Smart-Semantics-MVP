import { useEffect, useRef, useState } from 'react';
import {
  projectMysqlSchemaEvidence,
  type MysqlSchemaEvidence,
  type MysqlSchemaEvidenceProjection,
} from './mysql-schema-evidence.ts';

type StructuredRow = Record<string, unknown>;

function useContainerWidth() {
  const containerRef = useRef<HTMLElement>(null);
  const [containerWidth, setContainerWidth] = useState(0);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;
    const updateWidth = (width: number) => {
      const nextWidth = Math.round(width);
      setContainerWidth((currentWidth) => currentWidth === nextWidth ? currentWidth : nextWidth);
    };
    updateWidth(container.getBoundingClientRect().width);
    if (typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(([entry]) => updateWidth(entry.contentRect.width));
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  return { containerRef, containerWidth };
}

function sqlLine(line: string) {
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

function cellValue(row: StructuredRow, key: string) {
  const value = row[key];
  if (Array.isArray(value)) {
    return <span className="guanyijia-mysql-schema-constraints">{value.map((item) => <span key={String(item)}>{String(item)}</span>)}</span>;
  }
  return typeof value === 'string' ? value : undefined;
}

function StructuredTable({ projection }: { projection: MysqlSchemaEvidenceProjection }) {
  const columns = projection.structured.columns;
  if (!columns) return null;
  return <table className="guanyijia-mysql-schema-table">
    <colgroup>
      {columns.map((column) => <col className={`guanyijia-mysql-schema-column-${column.key}`} key={column.key} />)}
    </colgroup>
    <thead><tr>{columns.map((column) => <th key={column.key} scope="col">{column.label}</th>)}</tr></thead>
    <tbody>{projection.structured.rows.map((row, index) => <tr key={`${String(row.field)}:${index}`}>
      {columns.map((column) => <td key={column.key}>{column.key === 'field' && typeof row.field === 'string'
        ? <code>{row.field}</code>
        : cellValue(row, column.key)}</td>)}
    </tr>)}</tbody>
  </table>;
}

function DefinitionRows({ projection }: { projection: MysqlSchemaEvidenceProjection }) {
  return <dl className="guanyijia-mysql-schema-definitions">
    {projection.structured.rows.map((row, index) => <div key={`${String(row.field)}:${index}`}>
      <dt><code>{String(row.field)}</code><span>{String(row.type)}</span></dt>
      <dd>
        {typeof row.description === 'string' && <p>{row.description}</p>}
        {Array.isArray(row.constraints) && <span className="guanyijia-mysql-schema-constraints">
          {row.constraints.map((constraint) => <span key={String(constraint)}>{String(constraint)}</span>)}
        </span>}
      </dd>
    </div>)}
  </dl>;
}

function StructuredSchema({ evidence, containerWidth }: {
  evidence: MysqlSchemaEvidence;
  containerWidth: number;
}) {
  const projection = projectMysqlSchemaEvidence(evidence, { containerWidth });
  return <div className="guanyijia-mysql-schema-structured" data-layout={projection.layout}>
    {projection.layout === 'NARROW'
      ? <DefinitionRows projection={projection} />
      : <StructuredTable projection={projection} />}
  </div>;
}

function RawDdl({ rawDdl }: Pick<MysqlSchemaEvidence, 'rawDdl'>) {
  return <div className="guanyijia-mysql-schema-ddl">
    <small>以下行号仅表示本段摘录内的位置。</small>
    <pre tabIndex={0} aria-label="原始 DDL"><code>{rawDdl.split('\n').map((line, index) => <span className="guanyijia-mysql-schema-ddl-line" key={index}>
      <i aria-hidden="true">{index + 1}</i><span>{sqlLine(line)}</span>{'\n'}
    </span>)}</code></pre>
  </div>;
}

/**
 * A single responsive reader for frozen MySQL table evidence. Both the
 * document reader and inspector supply the same saved evidence to this view.
 */
export function MysqlSchemaEvidenceView({ evidence }: { evidence: MysqlSchemaEvidence }) {
  const { containerRef, containerWidth } = useContainerWidth();
  const highlightedColumns = evidence.columns.filter((column) => column.highlighted);
  const focusedColumns = highlightedColumns.length > 0 ? highlightedColumns : evidence.columns;
  const hasAdditionalColumns = focusedColumns.length < evidence.columns.length;

  return <section
    ref={containerRef}
    className="guanyijia-mysql-schema-evidence"
    aria-label={`${evidence.objectName} 数据表结构`}
  >
    <header>
      <strong>{evidence.objectName}</strong>
      <span>重点字段 {highlightedColumns.length}/{evidence.columns.length}</span>
    </header>
    {evidence.objectComment && <p className="guanyijia-mysql-schema-object-comment">{evidence.objectComment}</p>}
    <StructuredSchema evidence={{ ...evidence, columns: focusedColumns }} containerWidth={containerWidth} />
    {hasAdditionalColumns && <details>
      <summary>查看完整结构</summary>
      <StructuredSchema evidence={evidence} containerWidth={containerWidth} />
    </details>}
    <details>
      <summary>查看原始 DDL</summary>
      <RawDdl rawDdl={evidence.rawDdl} />
    </details>
    <small className="guanyijia-mysql-schema-location">来源位置：{evidence.locationValue}</small>
  </section>;
}
