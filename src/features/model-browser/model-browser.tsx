import { useEffect, useMemo, useState } from 'react';
import { Button, Collapse, Empty, Input, Pagination, Tag, Typography } from 'antd';
import { CloseOutlined, SearchOutlined } from '@ant-design/icons';
import { SemanticObjectTag } from '../../components/semantic-object-visuals.ts';
import BackAction from '../../components/back-action.tsx';
import {
  modelBrowserRefKey,
  type ModelBrowserComparison,
  type ModelBrowserObject,
  type ModelBrowserObjectKind,
  type ModelBrowserObjectRef,
  type ModelBrowserView,
} from './types.ts';
import { modelBrowserPageSize, paginateBrowserItems } from './browser-pagination.ts';
import './model-browser.css';
import './model-browser-packages.css';
import './model-browser-enhancements.css';

type MainCategory = 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC';
type BrowserView = 'SOURCES' | 'RECONCILIATION' | 'MODEL';

const mainCategories: Array<{ kind: MainCategory; label: string; count: keyof ModelBrowserView['counts'] }> = [
  { kind: 'ENTITY', label: '实体', count: 'entities' },
  { kind: 'EVENT', label: '事件', count: 'events' },
  { kind: 'FIELD', label: '字段', count: 'fields' },
  { kind: 'RELATION', label: '关系', count: 'relations' },
  { kind: 'DIMENSION', label: '维度', count: 'dimensions' },
  { kind: 'METRIC', label: '指标', count: 'metrics' },
];

const authorityLabels: Record<string, string> = {
  PRIMARY: '主要依据',
  CORROBORATING: '交叉佐证',
  AUXILIARY: '辅助资料',
  DERIVED: '派生依据',
};

const locatorLabels: Record<string, string> = {
  path: '来源位置', relativePath: '来源位置', file: '来源文件', table: '数据表',
  field: '字段', section: '文档章节', line: '行号', lineStart: '起始行', lineEnd: '结束行',
};

function ObjectLink({ label, ref, onSelect }: { label: string; ref: ModelBrowserObjectRef; onSelect(ref: ModelBrowserObjectRef): void }) {
  return <Button type="link" size="small" onClick={() => onSelect(ref)}>{label}</Button>;
}

function ObjectRow({ object, fields, onSelect }: {
  object: ModelBrowserObject;
  fields?: ModelBrowserObject[];
  onSelect(ref: ModelBrowserObjectRef): void;
}) {
  const content = <button type="button" className="model-browser-object" onClick={() => onSelect(object.ref)}>
    <span><strong>{object.name}</strong><small>{object.code}</small></span>
    {object.semanticKind && <SemanticObjectTag kind={object.semanticKind} />}
  </button>;
  if (!fields?.length) return content;
  return <section className="model-browser-table">
    {content}
    <Collapse ghost size="small" items={[{
      key: 'fields', label: `字段（${fields.length}）`,
      children: fields.map((field) => <ObjectRow key={modelBrowserRefKey(field.ref)} object={field} onSelect={onSelect} />),
    }]} />
  </section>;
}

function ObjectDefinition({ object, objects, comparison, onBack, onSelect }: {
  object: ModelBrowserObject;
  objects: ModelBrowserObject[];
  comparison?: ModelBrowserComparison;
  onBack(): void;
  onSelect(ref: ModelBrowserObjectRef): void;
}) {
  const children = object.childRefs.map((ref) => objects.find((item) => modelBrowserRefKey(item.ref) === modelBrowserRefKey(ref))).filter(Boolean) as ModelBrowserObject[];
  return <div className="model-browser-definition">
    <BackAction destination="列表" onBack={onBack} />
    <Typography.Title level={4}>{object.name}</Typography.Title>
    {object.semanticKind && <SemanticObjectTag kind={object.semanticKind} />}
    {object.summary && <Typography.Paragraph>{object.summary}</Typography.Paragraph>}
    {comparison && <section className={`model-browser-comparison ${comparison.operation.toLowerCase()}`}>
      <header><strong>本次草稿变化</strong><Tag color={comparison.operation === 'REMOVED' ? 'red' : comparison.operation === 'ADDED' ? 'green' : 'blue'}>{comparison.operation === 'REMOVED' ? '删除' : comparison.operation === 'ADDED' ? '新增' : '修改'}</Tag></header>
      <dl>{comparison.changedFields.map((field) => <div key={field.label}><dt>{field.label}</dt><dd><span>{field.before ?? '—'}</span><b>→</b><span>{field.after ?? '—'}</span></dd></div>)}</dl>
      <p>{comparison.evidenceRefs.length} 处依据{comparison.affectedRefs.length ? ` · 影响 ${comparison.affectedRefs.length} 个对象` : ''}</p>
      {comparison.affectedRefs.length > 0 && <div className="model-browser-comparison-links">{comparison.affectedRefs.map((ref) => <ObjectLink key={modelBrowserRefKey(ref)} label={objects.find((item) => modelBrowserRefKey(item.ref) === modelBrowserRefKey(ref))?.name ?? '未命名对象'} ref={ref} onSelect={onSelect} />)}</div>}
    </section>}
    <dl>{object.details.map((detail) => <div key={detail.label}>
      <dt>{detail.label}</dt>
      <dd>{detail.links?.length
        ? <span>{detail.links.map((link) => <ObjectLink key={modelBrowserRefKey(link.ref)} label={link.label} ref={link.ref} onSelect={onSelect} />)}</span>
        : detail.value}</dd>
    </div>)}</dl>
    {children.length > 0 && <Collapse ghost items={[{
      key: 'children', label: `字段（${children.length}）`,
      children: children.map((child) => <ObjectRow key={modelBrowserRefKey(child.ref)} object={child} onSelect={onSelect} />),
    }]} />}
  </div>;
}

export function ModelBrowser({ view, open, onOpen, onClose, focusRef, comparison }: {
  view: ModelBrowserView;
  open: boolean;
  onOpen(): void;
  onClose(): void;
  focusRef?: ModelBrowserObjectRef;
  comparison?: ModelBrowserComparison;
}) {
  const [activeView, setActiveView] = useState<BrowserView>('MODEL');
  const [category, setCategory] = useState<ModelBrowserObjectKind>('ENTITY');
  const [query, setQuery] = useState('');
  const [selectedRef, setSelectedRef] = useState<ModelBrowserObjectRef>();
  const [selectedEvidenceId, setSelectedEvidenceId] = useState<string>();
  const [selectedSourceId, setSelectedSourceId] = useState<string>();
  const [sourceEvidencePageNumber, setSourceEvidencePageNumber] = useState(1);
  const [modelPageNumber, setModelPageNumber] = useState(1);
  const packageSelectionKey = view.sourcePackages.map((item) => `${item.packageId}:${item.active}`).join('|');
  useEffect(() => {
    setSelectedRef(undefined);
    setQuery('');
    setSelectedEvidenceId(undefined);
    setSelectedSourceId(undefined);
    setSourceEvidencePageNumber(1);
    setModelPageNumber(1);
  }, [view.identity.catalogId, packageSelectionKey]);
  useEffect(() => {
    if (!focusRef) return;
    setActiveView('MODEL'); setCategory(focusRef.kind); setSelectedRef(focusRef);
  }, [focusRef ? modelBrowserRefKey(focusRef) : '']);
  const selectedKey = selectedRef ? modelBrowserRefKey(selectedRef) : '';
  const selected = view.objects.find((object) => modelBrowserRefKey(object.ref) === selectedKey);
  const normalizedQuery = query.trim().toLowerCase();
  const visible = useMemo(() => view.objects.filter((object) => object.ref.kind === category
    && (!normalizedQuery || `${object.name} ${object.code} ${object.summary ?? ''}`.toLowerCase().includes(normalizedQuery))), [category, normalizedQuery, view.objects]);
  const groupedFields = view.objects.filter((item) => item.ref.kind === 'FIELD'
    && (!normalizedQuery || `${item.name} ${item.code}`.toLowerCase().includes(normalizedQuery)));
  const modelResults = category === 'FIELD' ? groupedFields : visible;
  const modelPage = paginateBrowserItems(modelResults, modelPageNumber);
  const secondary = [
    { kind: 'HIERARCHY' as const, label: '层级', count: view.counts.hierarchies },
    { kind: 'RULE' as const, label: '规则', count: view.counts.rules },
    { kind: 'ALIAS' as const, label: '同义词', count: view.counts.aliases },
    { kind: 'TIME_RULE' as const, label: '时间语义', count: view.counts.timeRules },
  ];
  const selectedEvidence = selectedEvidenceId
    ? view.evidence.find((item) => item.evidence_id === selectedEvidenceId)
    : undefined;
  const selectedLocator = selectedEvidenceId ? view.evidenceLocators[selectedEvidenceId] : undefined;
  const selectedSource = view.sourceSnapshots.find((item) => item.snapshotId === selectedSourceId);
  const sourceEvidencePage = paginateBrowserItems(selectedSource?.evidenceIds ?? [], sourceEvidencePageNumber);
  useEffect(() => setModelPageNumber(1), [category, normalizedQuery]);
  useEffect(() => setSourceEvidencePageNumber(1), [selectedSourceId]);
  const openEvidence = (evidenceId: string) => {
    setSelectedEvidenceId(evidenceId);
    setActiveView('SOURCES');
    setSelectedRef(undefined);
  };

  if (!open) return <aside className="model-inspector-rail"><Button type="text" onClick={onOpen} aria-label="展开正式模型"><span className="rail-count">{view.counts.entities + view.counts.events}</span><span>模型</span></Button></aside>;
  return <aside className="model-browser" aria-label="正式模型浏览器">
    <header><div><strong>{view.identity.title}</strong><small>{view.identity.subtitle}</small></div><Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="收回正式模型" /></header>
    <nav className="model-browser-views">{([
      ['SOURCES', '资料'], ['RECONCILIATION', '互证'], ['MODEL', '模型'],
    ] as const).map(([key, label]) => <button type="button" key={key} className={activeView === key ? 'active' : ''} onClick={() => { setActiveView(key); setSelectedRef(undefined); }}>{label}</button>)}</nav>
    <div className="model-browser-body">
      {activeView === 'SOURCES' && <section className="model-browser-sources">
        <Typography.Title level={5}>本版本证据批次</Typography.Title>
        {view.sourceBatch ? <article className="model-browser-batch-summary"><strong>证据批次 R{view.sourceBatch.revision}</strong><span>{view.sourceSnapshots.length} 个来源 · {view.sourceBatch.coverage}</span><small>{new Date(view.sourceBatch.frozenAt).toLocaleString('zh-CN')}</small></article>
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该历史版本未保留来源明细" />}
        {selectedEvidenceId && <article className="model-browser-evidence-focus">
          <header><strong>{String(selectedEvidence?.section ?? '证据定位')}</strong><Button type="text" size="small" onClick={() => setSelectedEvidenceId(undefined)}>关闭</Button></header>
          <Typography.Paragraph>{String(selectedEvidence?.quote ?? '该版本未保留原文摘要')}</Typography.Paragraph>
          {selectedLocator && <dl>{Object.entries(selectedLocator).filter(([key]) => !/(sha|ref|id|fingerprint|digest)/iu.test(key)).map(([key, value]) => <div key={key}><dt>{locatorLabels[key] ?? '来源位置'}</dt><dd>{String(value)}</dd></div>)}</dl>}
        </article>}
        {view.sourceSnapshots.length > 0 && <><Typography.Title level={5}>已冻结来源</Typography.Title>{selectedSource ? <div className="model-browser-source-detail-view">
          <BackAction destination="来源列表" onBack={() => { setSelectedSourceId(undefined); setSelectedEvidenceId(undefined); }} />
          <header><div><strong>{selectedSource.displayName}</strong></div><Tag>{authorityLabels[selectedSource.authority] ?? '来源资料'}</Tag></header>
          <p>{selectedSource.summary}</p>
          <dl><div><dt>作用</dt><dd>{selectedSource.role}</dd></div><div><dt>对象</dt><dd>{Object.entries(selectedSource.objectCounts).map(([key, count]) => `${key} ${count}`).join(' · ')}</dd></div>{selectedSource.upstreamSnapshotIds?.length ? <div><dt>上游</dt><dd>已关联上游资料</dd></div> : null}</dl>
          <h5>证据定位 · 显示 {sourceEvidencePage.items.length} / {sourceEvidencePage.total}</h5>
          <div className="model-browser-source-evidence-list">{sourceEvidencePage.items.map((evidenceId) => {
            const evidence = view.evidence.find((item) => item.evidence_id === evidenceId);
            return <button type="button" key={evidenceId} onClick={() => openEvidence(evidenceId)}><strong>{String(evidence?.section ?? '证据定位')}</strong><small>查看来源材料</small></button>;
          })}</div>
          {sourceEvidencePage.total > modelBrowserPageSize && <Pagination size="small" current={sourceEvidencePage.page} pageSize={modelBrowserPageSize} total={sourceEvidencePage.total} showSizeChanger={false} onChange={setSourceEvidencePageNumber} />}
        </div> : <div className="model-browser-source-list">{view.sourceSnapshots.map((source) => <button type="button" key={source.snapshotId} onClick={() => setSelectedSourceId(source.snapshotId)}><span><strong>{source.displayName}</strong><small>{source.summary}</small></span></button>)}</div>}</>}
      </section>}
      {activeView === 'RECONCILIATION' && <section className="model-browser-reconciliation">
        <Typography.Title level={5}>多来源互证</Typography.Title>
        {view.coverageNotices.map((notice) => <Typography.Paragraph key={notice}>{notice}</Typography.Paragraph>)}
        {view.reconciliationFindings.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该历史版本未保留互证明细" /> : <div className="model-browser-findings">{view.reconciliationFindings.map((finding) => <article key={finding.findingId}>
          <header><strong>{finding.title}</strong><Tag color={finding.status === 'CONFLICT' ? 'red' : finding.status === 'CONSISTENT' ? 'green' : 'gold'}>{finding.status === 'CONSISTENT' ? '一致' : finding.status === 'CONFLICT' ? '冲突' : finding.status === 'SINGLE_SOURCE' ? '单一依据' : '派生依据'}</Tag></header>
          <div className="model-browser-claims">{finding.claims.map((claim) => <div key={`${finding.findingId}:${claim.sourceId}`}><b>{claim.sourceName}</b><span>{claim.assertion}</span><small>{authorityLabels[claim.authority] ?? '来源依据'}</small><span className="model-browser-evidence-links">{claim.evidenceRefs.map((evidenceId, index) => <Button type="link" size="small" key={evidenceId} onClick={() => openEvidence(evidenceId)}>查看依据{index + 1}</Button>)}</span></div>)}</div>
          {finding.agreement && <p><b>一致点：</b>{finding.agreement}</p>}{finding.difference && <p><b>差异：</b>{finding.difference}</p>}{finding.decision && <p><b>最终决定：</b>{finding.decision}</p>}
          {finding.resolution && <p className="model-browser-resolution"><b>决定状态：</b>已记录并纳入当前版本。</p>}
          {finding.affectedRefs.length > 0 && <p><b>影响对象：</b>{finding.affectedRefs.map((ref) => <ObjectLink key={modelBrowserRefKey(ref)} label={view.objects.find((item) => modelBrowserRefKey(item.ref) === modelBrowserRefKey(ref))?.name ?? '未命名对象'} ref={ref} onSelect={(next) => { setActiveView('MODEL'); setCategory(next.kind); setSelectedRef(next); }} />)}</p>}
        </article>)}</div>}
      </section>}
      {activeView === 'MODEL' && (selected
        ? <ObjectDefinition object={selected} objects={view.objects} comparison={comparison && focusRef && modelBrowserRefKey(selected.ref) === modelBrowserRefKey(focusRef) ? comparison : undefined} onBack={() => setSelectedRef(undefined)} onSelect={setSelectedRef} />
        : <>
          <Input allowClear prefix={<SearchOutlined />} placeholder="搜索名称或编码" value={query} onChange={(event) => setQuery(event.target.value)} />
          <nav className="model-browser-categories">{mainCategories.map((item) => <button type="button" key={item.kind} className={category === item.kind ? 'active' : ''} onClick={() => setCategory(item.kind)}><SemanticObjectTag kind={item.kind} label={item.label} /><span>{view.counts[item.count]}</span></button>)}</nav>
          <div className="model-browser-list">{category === 'FIELD'
            ? modelPage.items.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /> : view.objects.filter((item) => item.ref.kind === 'ENTITY' || item.ref.kind === 'EVENT').map((table) => {
              const fields = modelPage.items.filter((field) => field.ref.ownerId === table.ref.objectId);
              return fields.length ? <section className="model-browser-field-group" key={modelBrowserRefKey(table.ref)}><header>{table.name} · {fields.length}个字段</header>{fields.map((field) => <ObjectRow key={modelBrowserRefKey(field.ref)} object={field} onSelect={setSelectedRef} />)}</section> : null;
            })
            : modelPage.items.length ? modelPage.items.map((object) => <ObjectRow key={modelBrowserRefKey(object.ref)} object={object} onSelect={setSelectedRef} />)
            : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的对象" />}</div>
          {modelPage.total > modelBrowserPageSize && <Pagination className="model-browser-pagination" size="small" current={modelPage.page} pageSize={modelBrowserPageSize} total={modelPage.total} showSizeChanger={false} onChange={setModelPageNumber} />}
          <Collapse ghost items={[{
            key: 'secondary', label: '其他语义资产',
            children: <div className="model-browser-secondary">{secondary.map((item) => <button type="button" key={item.kind} className={category === item.kind ? 'active' : ''} onClick={() => { setCategory(item.kind); setSelectedRef(undefined); }}><span>{item.label}</span><strong>{item.count}</strong></button>)}</div>,
          }]} />
        </>)}
    </div>
  </aside>;
}
