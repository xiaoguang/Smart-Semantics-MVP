import { useEffect, useMemo, useState, type Key, type ReactNode } from 'react';
import { Empty, Pagination, Table } from 'antd';
import type { ColumnsType, TablePaginationConfig, TableProps } from 'antd/es/table';
import './responsive-data-view.css';

export type ResponsiveDataField = {
  label: ReactNode;
  value: ReactNode;
  wide?: boolean;
};

export function ResponsiveDataCard({
  title,
  subtitle,
  status,
  fields = [],
  actions,
  selected = false,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  status?: ReactNode;
  fields?: ResponsiveDataField[];
  actions?: ReactNode;
  selected?: boolean;
}) {
  return <article className={`responsive-data-card${selected ? ' is-selected' : ''}`}>
    <header className="responsive-data-card-header">
      <div className="responsive-data-card-identity"><strong>{title}</strong>{subtitle && <div>{subtitle}</div>}</div>
      {status && <div className="responsive-data-card-status">{status}</div>}
    </header>
    {fields.length > 0 && <dl className="responsive-data-card-fields">
      {fields.map((field, index) => <div className={field.wide ? 'is-wide' : undefined} key={index}>
        <dt>{field.label}</dt><dd>{field.value}</dd>
      </div>)}
    </dl>}
    {actions && <footer className="responsive-data-card-actions">{actions}</footer>}
  </article>;
}

type ResponsiveDataViewProps<T extends object> = {
  ariaLabel: string;
  dataSource: readonly T[];
  columns: ColumnsType<T>;
  rowKey: TableProps<T>['rowKey'];
  renderCard(record: T): ReactNode;
  minTableWidth: number;
  mobilePageSize?: number;
  pagination?: false | TablePaginationConfig;
  tableProps?: Omit<TableProps<T>, 'columns' | 'dataSource' | 'pagination' | 'rowKey' | 'scroll'>;
};

function recordKey<T extends object>(record: T, index: number, rowKey: TableProps<T>['rowKey']): Key {
  if (typeof rowKey === 'function') return rowKey(record);
  if (typeof rowKey === 'string') return (record as Record<string, Key>)[rowKey] ?? index;
  return index;
}

/**
 * One data-list contract for every reachable page: a horizontally scrollable,
 * explicitly-sized table on desktop and a paginated information-card list on
 * phones. Pages own business rendering; this module owns responsive behavior.
 */
export default function ResponsiveDataView<T extends object>({
  ariaLabel,
  dataSource,
  columns,
  rowKey,
  renderCard,
  minTableWidth,
  mobilePageSize = 12,
  pagination = false,
  tableProps,
}: ResponsiveDataViewProps<T>) {
  const [mobilePage, setMobilePage] = useState(1);
  const membershipKey = dataSource.map((record, index) => String(recordKey(record, index, rowKey))).join('\u001f');
  const maxPage = Math.max(1, Math.ceil(dataSource.length / mobilePageSize));
  useEffect(() => setMobilePage(1), [membershipKey, mobilePageSize]);
  useEffect(() => setMobilePage((current) => Math.min(current, maxPage)), [maxPage]);
  const mobileRecords = useMemo(() => {
    const start = (mobilePage - 1) * mobilePageSize;
    return dataSource.slice(start, start + mobilePageSize);
  }, [dataSource, mobilePage, mobilePageSize]);

  return <section className="responsive-data-view" data-responsive-view={ariaLabel}>
    <div className="responsive-data-view-desktop" data-min-table-width={minTableWidth}>
      <Table<T>
        {...tableProps}
        columns={columns}
        dataSource={[...dataSource]}
        pagination={pagination}
        rowKey={rowKey}
        scroll={{ x: minTableWidth }}
      />
    </div>
    <div className="responsive-data-view-mobile">
      <div className="responsive-data-card-list" role="list" aria-label={ariaLabel} data-page-size={mobilePageSize}>
        {mobileRecords.length > 0
          ? mobileRecords.map((record, index) => <div role="listitem" key={recordKey(record, index, rowKey)}>{renderCard(record)}</div>)
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />}
      </div>
      {dataSource.length > mobilePageSize && <Pagination
        className="responsive-data-pagination"
        current={mobilePage}
        pageSize={mobilePageSize}
        total={dataSource.length}
        showSizeChanger={false}
        onChange={setMobilePage}
        size="small"
      />}
    </div>
  </section>;
}
