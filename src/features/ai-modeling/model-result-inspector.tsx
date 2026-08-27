import { useEffect, useMemo, useState } from 'react';
import { Button, Collapse, Empty, Input, Select, Space, Tag, Typography } from 'antd';
import { CloseOutlined, SearchOutlined } from '@ant-design/icons';
import {
  modelingObjectRefKey,
  projectModelObjects,
  type ModelingObjectRef,
  type ObjectReviewStatus,
  type ProjectedModelObject,
} from './modeling-object-projection.ts';
import type { HydratedResult } from './runtime-types.ts';
import type { ModelSummaryTarget } from './workflow-records.tsx';
import { projectCustomerDescription, projectEvidenceCitations } from './customer-object-presentation.ts';
import { SemanticObjectTag } from '../../components/semantic-object-visuals.ts';
import BackAction from '../../components/back-action.tsx';

type Category = 'entities' | 'events' | 'relations' | 'dimensions' | 'metrics' | 'rules';
type StatusFilter = 'ALL' | 'ADOPTED' | 'ACTION';
export type InspectorNavigation = ModelSummaryTarget & { requestId: number };

const statusView: Record<ObjectReviewStatus, { label: string; color?: string }> = {
  AUTO_APPROVED: { label: '系统采纳', color: 'green' },
  USER_APPROVED: { label: '人工采纳', color: 'blue' },
  USER_REJECTED: { label: '已提出异议', color: 'red' },
  PENDING: { label: '待确认', color: 'orange' },
  NOT_SEPARATELY_REVIEWED: { label: '未单独评审' },
};

function matchesStatus(status: ObjectReviewStatus, filter: StatusFilter) {
  if (filter === 'ALL') return true;
  if (filter === 'ADOPTED') return status === 'AUTO_APPROVED' || status === 'USER_APPROVED';
  return status === 'PENDING' || status === 'USER_REJECTED';
}

function StatusTag({ object }: { object: ProjectedModelObject }) {
  const view = statusView[object.reviewStatus];
  return <Space size={4} wrap><Tag color={view.color}>{view.label}</Tag>{object.reviewMode === 'TARGET' && <Tag>随目标对象审核</Tag>}</Space>;
}

function citationLabel(citation: { sourceFile: string; section: string; lineLabel?: string }) {
  return [citation.sourceFile, citation.section, citation.lineLabel].filter(Boolean).join(' · ');
}

function catalogEvidenceLabel(evidence: { source_file: string; section: string; line_start?: number; line_end?: number }) {
  const lineLabel = evidence.line_start
    ? `第 ${evidence.line_start}${evidence.line_end && evidence.line_end !== evidence.line_start ? `–${evidence.line_end}` : ''} 行`
    : undefined;
  return [evidence.source_file, evidence.section, lineLabel].filter(Boolean).join(' · ');
}

function ReviewActions({ object, readOnly, onReviewAction }: {
  object: ProjectedModelObject;
  readOnly: boolean;
  onReviewAction?(itemId: string, objectName: string, decision: 'APPROVED' | 'REJECTED'): void;
}) {
  if (readOnly || !object.reviewItemId) return null;
  if (object.reviewStatus === 'PENDING') return <Space size={4}><Button type="primary" size="small" onClick={() => onReviewAction?.(object.reviewItemId!, object.name, 'APPROVED')}>采纳</Button><Button size="small" onClick={() => onReviewAction?.(object.reviewItemId!, object.name, 'REJECTED')}>不采纳</Button></Space>;
  if (object.reviewStatus === 'USER_REJECTED') return <Button size="small" onClick={() => onReviewAction?.(object.reviewItemId!, object.name, 'APPROVED')}>改为采纳</Button>;
  if (object.reviewStatus === 'AUTO_APPROVED' || object.reviewStatus === 'USER_APPROVED') return <Button size="small" onClick={() => onReviewAction?.(object.reviewItemId!, object.name, 'REJECTED')}>提出意见</Button>;
  return null;
}

function ObjectDefinition({ object, document, childObjects, readOnly, onBack, onSelect, onReviewAction }: {
  object: ProjectedModelObject;
  document: HydratedResult['fixture'];
  childObjects: ProjectedModelObject[];
  readOnly: boolean;
  onBack(): void;
  onSelect(ref: ModelingObjectRef): void;
  onReviewAction?(itemId: string, objectName: string, decision: 'APPROVED' | 'REJECTED'): void;
}) {
  const description = projectCustomerDescription(object);
  const citations = projectEvidenceCitations(document, object.evidenceIds);
  return <div className="inspector-definition inspector-highlight">
    <BackAction destination="列表" onBack={onBack} />
    <Typography.Title level={4}>{object.name}</Typography.Title>
    <SemanticObjectTag kind={object.visualKind} role={object.fieldRole} label={object.subtitle.endsWith('表') ? object.subtitle : undefined} />
    <div className="inspector-definition-status"><StatusTag object={object} /></div>
    <ReviewActions object={object} readOnly={readOnly} onReviewAction={onReviewAction} />
    <section className="customer-description"><Typography.Text strong>这是什么</Typography.Text><Typography.Paragraph>{description.summary}</Typography.Paragraph>{description.usage && <><Typography.Text strong>如何使用</Typography.Text><Typography.Paragraph>{description.usage}</Typography.Paragraph></>}{description.confirmation && <><Typography.Text strong>需要确认什么</Typography.Text><Typography.Paragraph>{description.confirmation}</Typography.Paragraph></>}</section>
    {object.reviewMode === 'TARGET' && object.reviewSourceRef && <Button type="link" onClick={() => onSelect(object.reviewSourceRef!)}>查看实际审核对象</Button>}
    {childObjects.length > 0 && <Collapse ghost items={[{
      key: 'fields', label: `字段（${childObjects.length}）`,
      children: <div className="inspector-child-fields">{childObjects.map((field) => <ObjectRow key={modelingObjectRefKey(field.ref)} object={field} selected={false} readOnly={readOnly} onSelect={onSelect} onReviewAction={onReviewAction} />)}</div>,
    }]} />}
    <Collapse ghost items={[{
      key: 'technical', label: '技术信息', children: <><Typography.Text code>{object.code}</Typography.Text><dl>{object.details.map((detail) => <div key={detail.label}><dt>{detail.label}</dt><dd>{detail.links?.length ? detail.links.map((link) => <Button type="link" size="small" key={modelingObjectRefKey(link.ref)} onClick={() => onSelect(link.ref)}>{link.label}</Button>) : detail.value}</dd></div>)}</dl>{description.technicalDefinition && <Typography.Paragraph type="secondary">{description.technicalDefinition}</Typography.Paragraph>}</>,
    }]} />
    <Collapse ghost items={[{
      key: 'evidence',
      label: `来源依据（${citations.length} 处）`,
      children: citations.length > 0
        ? <Collapse accordion ghost className="evidence-citations" items={citations.map((citation) => ({
          key: citation.id,
          label: citation.missing ? '来源记录暂时不可用' : citationLabel(citation),
          children: citation.missing ? <Typography.Text type="danger">这条来源记录暂时不可用，请重新打开当前资料后重试。</Typography.Text> : <Typography.Paragraph>{citation.quote}</Typography.Paragraph>,
        }))} />
        : <Typography.Text type="secondary">此对象没有独立来源记录。</Typography.Text>,
    }]} />
  </div>;
}

function ObjectRow({ object, selected, readOnly, onSelect, onReviewAction }: {
  object: ProjectedModelObject;
  selected: boolean;
  readOnly: boolean;
  onSelect(ref: ModelingObjectRef): void;
  onReviewAction?(itemId: string, objectName: string, decision: 'APPROVED' | 'REJECTED'): void;
}) {
  return <div
    id={`model-object-${modelingObjectRefKey(object.ref).replaceAll(':', '-')}`}
    className={`inspector-object-row ${selected ? 'selected inspector-highlight' : ''}`}
  >
    <button type="button" className="inspector-object-main" onClick={() => onSelect(object.ref)}><span><strong>{object.name}</strong><small><SemanticObjectTag kind={object.visualKind} role={object.fieldRole} label={object.subtitle.endsWith('表') ? object.subtitle : undefined} />{object.subtitle.endsWith('表') ? null : <> {object.subtitle}</>}</small></span><StatusTag object={object} /></button>
    <ReviewActions object={object} readOnly={readOnly} onReviewAction={onReviewAction} />
  </div>;
}

function TableObjectGroup({ table, fields, selectedKey, expandFields, readOnly, onSelect, onReviewAction }: {
  table: ProjectedModelObject;
  fields: ProjectedModelObject[];
  selectedKey: string;
  expandFields: boolean;
  readOnly: boolean;
  onSelect(ref: ModelingObjectRef): void;
  onReviewAction?(itemId: string, objectName: string, decision: 'APPROVED' | 'REJECTED'): void;
}) {
  const adopted = fields.filter((field) => field.reviewStatus === 'AUTO_APPROVED' || field.reviewStatus === 'USER_APPROVED').length;
  const action = fields.filter((field) => field.reviewStatus === 'PENDING' || field.reviewStatus === 'USER_REJECTED').length;
  return <section className="inspector-table-group">
    <ObjectRow object={table} selected={selectedKey === modelingObjectRefKey(table.ref)} readOnly={readOnly} onSelect={onSelect} onReviewAction={onReviewAction} />
    {fields.length > 0 && <Collapse ghost size="small" defaultActiveKey={expandFields ? ['fields'] : []} items={[{
      key: 'fields', label: `${fields.length} 个字段 · 已采纳 ${adopted} · 需处理 ${action}`,
      children: fields.map((field) => <ObjectRow key={modelingObjectRefKey(field.ref)} object={field} selected={selectedKey === modelingObjectRefKey(field.ref)} readOnly={readOnly} onSelect={onSelect} onReviewAction={onReviewAction} />),
    }]} />}
  </section>;
}

export default function ModelResultInspector({
  result, open, selectedRef, navigation, onOpen, onClose, onSelect, onClearSelection, onReviewAction,
}: {
  result?: HydratedResult;
  open: boolean;
  selectedRef?: ModelingObjectRef;
  navigation?: InspectorNavigation;
  onOpen(): void;
  onClose(): void;
  onSelect(ref: ModelingObjectRef): void;
  onClearSelection(): void;
  onReviewAction?(itemId: string, objectName: string, decision: 'APPROVED' | 'REJECTED'): void;
}) {
  const [category, setCategory] = useState<Category>('entities');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const [fieldMode, setFieldMode] = useState(false);
  const objects = useMemo(() => result ? projectModelObjects(result.fixture, result) : [], [result]);
  const selectedKey = selectedRef ? modelingObjectRefKey(selectedRef) : '';
  const selected = objects.find((object) => modelingObjectRefKey(object.ref) === selectedKey);

  useEffect(() => {
    if (!selected) return;
    if (selected.category !== 'tables') {
      setCategory(selected.category);
      return;
    }
    const owner = selected.ref.kind === 'FIELD'
      ? objects.find((object) => object.ref.kind === 'TABLE' && object.ref.objectId === selected.ref.ownerId)
      : selected;
    setCategory(owner?.visualKind === 'EVENT' ? 'events' : 'entities');
  }, [objects, selected]);
  useEffect(() => {
    if (!navigation) return;
    if (navigation.category === 'tables') {
      setFieldMode(navigation.fieldMode === true);
      if ('tableKind' in navigation) setCategory(navigation.tableKind === 'EVENT' ? 'events' : 'entities');
    } else {
      setCategory(navigation.category);
      setFieldMode(false);
    }
    setQuery('');
    setFilter('ALL');
  }, [navigation]);
  useEffect(() => {
    if (!open || !selected) return;
    const id = `model-object-${modelingObjectRefKey(selected.ref).replaceAll(':', '-')}`;
    window.requestAnimationFrame(() => document.getElementById(id)?.scrollIntoView({ block: 'nearest' }));
  }, [open, selected]);

  if (!open) {
    return <aside className="model-inspector-rail">
      <Button type="text" onClick={onOpen} aria-label="展开当前生成结果"><span className="rail-count">{objects.length}</span><span>结果</span></Button>
    </aside>;
  }

  const normalizedQuery = query.trim().toLowerCase();
  const sourceCategory = category === 'entities' || category === 'events' ? 'tables' : category === 'rules' ? 'metrics' : category;
  const visible = objects.filter((object) => object.category === sourceCategory
    && matchesStatus(object.reviewStatus, filter)
    && (!normalizedQuery || `${object.name} ${object.code} ${object.subtitle}`.toLowerCase().includes(normalizedQuery)));
  const tableGroups = objects.filter((object) => object.ref.kind === 'TABLE'
    && (fieldMode || (category === 'events' ? object.visualKind === 'EVENT' : object.visualKind === 'ENTITY'))).map((table) => {
    const fields = objects.filter((object) => object.ref.kind === 'FIELD' && object.ref.ownerId === table.ref.objectId
      && matchesStatus(object.reviewStatus, filter)
      && (!normalizedQuery || `${object.name} ${object.code} ${object.subtitle}`.toLowerCase().includes(normalizedQuery)));
    const tableMatches = matchesStatus(table.reviewStatus, filter)
      && (!normalizedQuery || `${table.name} ${table.code} ${table.subtitle}`.toLowerCase().includes(normalizedQuery));
    return { table, fields, visible: tableMatches || fields.length > 0 };
  }).filter((group) => group.visible);
  const counts = {
    entities: objects.filter((item) => item.ref.kind === 'TABLE' && item.visualKind === 'ENTITY').length,
    events: objects.filter((item) => item.ref.kind === 'TABLE' && item.visualKind === 'EVENT').length,
    relations: objects.filter((item) => item.category === 'relations').length,
    dimensions: objects.filter((item) => item.category === 'dimensions').length,
    metrics: objects.filter((item) => item.category === 'metrics').length,
    rules: result?.fixture.artifacts.enrichment.rule_candidates.length ?? 0,
  };
  const fieldCount = objects.filter((item) => item.ref.kind === 'FIELD').length;
  const resultReadOnly = result?.status === 'PUBLISHED';
  const renderGroups = (groups: typeof tableGroups) => groups.map((group) => <TableObjectGroup key={`${modelingObjectRefKey(group.table.ref)}:${navigation?.requestId ?? 0}:${fieldMode ? 'fields' : 'tables'}`} table={group.table} fields={group.fields} selectedKey={selectedKey} expandFields={fieldMode} readOnly={resultReadOnly} onSelect={onSelect} onReviewAction={onReviewAction} />);

  return <aside className="model-result-inspector" aria-label="当前生成结果">
    <header><div><strong>当前生成结果</strong>{result && <small>当前资料 · {result.status === 'NEEDS_SUPPLEMENT' ? '未发布，需要补充' : '建模建议已生成'}</small>}</div><Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="收回当前生成结果" /></header>
    {!result ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="生成模型后在这里检查定义" /> : selected ? <ObjectDefinition object={selected} document={result.fixture} childObjects={objects.filter((item) => selected.childRefs.some((ref) => modelingObjectRefKey(ref) === modelingObjectRefKey(item.ref)))} readOnly={result.status === 'PUBLISHED'} onBack={onClearSelection} onSelect={onSelect} onReviewAction={onReviewAction} /> : <>
      <div className="inspector-toolbar"><Input allowClear prefix={<SearchOutlined />} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索名称或编码" /><Select value={filter} onChange={setFilter} options={[{ value: 'ALL', label: '全部' }, { value: 'ADOPTED', label: '已采纳' }, { value: 'ACTION', label: '需处理' }]} /></div>
      <nav>{(['entities', 'events', 'relations', 'dimensions', 'metrics', 'rules'] as const).map((item) => {
        const view = ({
          entities: { label: '实体', kind: 'ENTITY' }, events: { label: '事件', kind: 'EVENT' },
          relations: { label: '关系', kind: 'RELATION' }, dimensions: { label: '维度', kind: 'DIMENSION' },
          metrics: { label: '指标', kind: 'METRIC' },
          rules: { label: '规则', kind: null },
        } as const)[item];
        return <button type="button" className={!fieldMode && category === item ? 'active' : ''} key={item} onClick={() => { setCategory(item); setFieldMode(false); }}>{view.kind ? <SemanticObjectTag kind={view.kind} label={view.label} /> : <Tag>{view.label}</Tag>} <span>{counts[item]}</span></button>;
      })}</nav>
      <div className="inspector-object-list">{category === 'rules'
        ? result.fixture.artifacts.enrichment.rule_candidates.map((rule) => {
          const decision = result.decisions[`RULE:${rule.rule_id}`];
          return <article className="inspector-rule-candidate" key={rule.rule_id}><div><strong>{rule.name}</strong><Tag>只读候选</Tag></div><p>{rule.definition}</p><small>目标：{rule.owner_id ? `${rule.owner_id}.` : ''}{rule.target_id}</small><Tag color={decision?.decision === 'REJECTED' ? 'red' : decision ? 'green' : 'orange'}>{decision?.decision === 'REJECTED' ? '已提出意见' : decision ? '已采纳' : '待确认'}</Tag></article>;
        })
        : category === 'entities' || category === 'events'
        ? fieldMode
          ? <><div className="inspector-field-mode-heading"><strong>全部字段</strong><span>{fieldCount}</span><BackAction destination="分类" onBack={() => setFieldMode(false)} /></div>
            <div className="inspector-field-kind"><SemanticObjectTag kind="ENTITY" label="实体字段" />{renderGroups(tableGroups.filter((group) => group.table.visualKind === 'ENTITY'))}</div>
            <div className="inspector-field-kind"><SemanticObjectTag kind="EVENT" label="事件字段" />{renderGroups(tableGroups.filter((group) => group.table.visualKind === 'EVENT'))}</div></>
          : tableGroups.length ? renderGroups(tableGroups) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的实体、事件或字段" />
        : visible.length ? visible.map((object) => <ObjectRow key={modelingObjectRefKey(object.ref)} object={object} selected={selectedKey === modelingObjectRefKey(object.ref)} readOnly={result.status === 'PUBLISHED'} onSelect={onSelect} onReviewAction={onReviewAction} />) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的对象" />}</div>
      <Collapse className="inspector-source" ghost items={[
        { key: 'notes', label: `来源与生成说明（${result.fixture.generationNotes.length} 条）`, children: <div className="inspector-note-list">{result.fixture.generationNotes.map((note) => <p key={note.id}><strong>{note.sourceName}</strong> → {note.target}<br /><small>{note.reason}</small></p>)}</div> },
        { key: 'evidence', label: `来源材料目录（${result.fixture.artifacts.analysis.evidence_catalog.length} 处）`, children: <Collapse accordion ghost className="evidence-citations" items={result.fixture.artifacts.analysis.evidence_catalog.map((evidence) => ({ key: evidence.evidence_id, label: catalogEvidenceLabel(evidence), children: <Typography.Paragraph>{evidence.quote}</Typography.Paragraph> }))} /> },
      ]} />
    </>}
  </aside>;
}
