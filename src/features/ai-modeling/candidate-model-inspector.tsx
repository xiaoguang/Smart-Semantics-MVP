import { Badge, Collapse, Empty, Input, Segmented, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import type { CandidateModelObject, CandidateModelSnapshot, CandidateTable } from './candidate-model.ts';
import { presentCandidateEvidence, presentCandidateStatus } from './candidate-model-presentation.ts';

type Category = 'ENTITY' | 'EVENT' | 'RELATION' | 'DIMENSION' | 'METRIC' | 'HIERARCHY' | 'RULE' | 'PENDING';

function ObjectCard({ item }: { item: CandidateModelObject }) {
  const status = presentCandidateStatus(item.status);
  return <article className="candidate-object-card">
    <header><div><strong>{item.name}</strong></div><Tag color={status.color}>{status.label}</Tag></header>
    <Typography.Paragraph>{item.description}</Typography.Paragraph>
    {item.risk && <Typography.Paragraph type="warning">{item.risk}</Typography.Paragraph>}
    <Collapse ghost size="small" items={[{ key: 'evidence', label: '来源材料', children: <Typography.Paragraph>{presentCandidateEvidence(item.evidenceIds)}</Typography.Paragraph> }]} />
  </article>;
}

function TableCard({ table }: { table: CandidateTable }) {
  const status = presentCandidateStatus(table.status);
  return <article className="candidate-object-card candidate-table-card">
    <header><div><strong>{table.name}</strong></div><Tag color={status.color}>{status.label}</Tag></header>
    <Typography.Paragraph>{table.description}</Typography.Paragraph>
    <Collapse ghost size="small" items={[{ key: 'fields', label: `字段（${table.fields.length}）`, children: <div className="candidate-field-list">{table.fields.map((field) => {
      const fieldStatus = presentCandidateStatus(field.status);
      return <div key={field.id}><span>{field.name}</span><Tag color={fieldStatus.color}>{fieldStatus.label}</Tag></div>;
    })}</div> }]} />
  </article>;
}

export default function CandidateModelInspector({ model }: { model: CandidateModelSnapshot }) {
  const [category, setCategory] = useState<Category>('ENTITY');
  const [search, setSearch] = useState('');
  const items = useMemo(() => {
    const source: Array<CandidateModelObject | CandidateTable> = category === 'ENTITY' ? model.entities
      : category === 'EVENT' ? model.events : category === 'RELATION' ? model.relations
        : category === 'DIMENSION' ? model.dimensions : category === 'METRIC' ? model.metrics
          : category === 'HIERARCHY' ? model.hierarchies : category === 'RULE' ? model.ruleCandidates : model.pendingAssets;
    const query = search.trim().toLowerCase();
    return query ? source.filter((item) => `${item.name} ${item.code} ${item.description}`.toLowerCase().includes(query)) : source;
  }, [category, model, search]);
  const options = [
    ['ENTITY', '实体', model.counts.entities], ['EVENT', '事件', model.counts.events],
    ['RELATION', '关系', model.counts.relations], ['DIMENSION', '维度', model.counts.dimensions],
    ['METRIC', '指标', model.counts.metrics], ['HIERARCHY', '层级', model.counts.hierarchies],
    ['RULE', '规则', model.counts.ruleCandidates], ['PENDING', '待归类', model.counts.pendingAssets],
  ].map(([value, label, count]) => ({ value, label: <span>{label} <Badge count={count as number} overflowCount={999} /></span> }));
  return <div className="candidate-model-inspector">
    <div className="candidate-model-summary"><strong>候选语义模型</strong><Tag color={model.status === 'DRAFT' ? 'orange' : 'green'}>{model.status === 'DRAFT' ? '存在待确认项' : '候选就绪'}</Tag><small>{model.tables.length} 张核心业务表 · {model.pendingAssets.length} 张扩展表待归类</small></div>
    <Input.Search allowClear value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索名称或编码" />
    <Segmented block value={category} onChange={(value) => setCategory(value as Category)} options={options} />
    <div className="candidate-model-list">{items.length ? items.map((item) => 'fields' in item
      ? <TableCard table={item} key={item.id} /> : <ObjectCard item={item} key={item.id} />)
      : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配对象" />}</div>
  </div>;
}
