import { useEffect, useState } from 'react';
import { Button, Empty, Tabs, Tag, Typography } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import type { ModelingDocumentArtifact } from '../modeling-document-bridge/types.ts';
import type { CompiledModelingDocument } from '../modeling-document-bridge/compile-modeling-document.ts';
import type { ModelingDocumentCandidateKind } from '../modeling-document-bridge/compile-modeling-document.ts';
import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import { SemanticObjectTag } from '../../components/semantic-object-visuals.ts';

const labels: Record<ModelingDocumentCandidateKind, string> = {
  ENTITY: '实体', EVENT: '事件', FIELD: '字段', RELATION: '关系', DIMENSION: '维度', METRIC: '指标',
  HIERARCHY: '层级', RULE: '规则候选', ALIAS: '同义词', TIME_RULE: '时间语义', PENDING_ASSET: '待归类资产',
};
const primaryKinds = ['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC'] as const;
const isPrimaryKind = (kind: ModelingDocumentCandidateKind): kind is typeof primaryKinds[number] =>
  (primaryKinds as readonly ModelingDocumentCandidateKind[]).includes(kind);

export default function ModelingDocumentInspector({ artifact, candidate, open, onOpen, onClose }: {
  artifact: ModelingDocumentArtifact; candidate?: CompiledModelingDocument; open: boolean; onOpen(): void; onClose(): void;
}) {
  const [tab, setTab] = useState('document');
  useEffect(() => { if (candidate) setTab('model'); }, [candidate?.candidateId]);
  if (!open) return <aside className="model-inspector-rail"><Button type="text" onClick={onOpen} aria-label="展开文档与模型"><span className="rail-count">{candidate?.items.length ?? 9}</span><span>{candidate ? '模型' : '文档'}</span></Button></aside>;
  return <aside className="model-result-inspector modeling-document-inspector" aria-label="标准文档与模型候选">
    <header><div><strong>{artifact.title}</strong><small>第 {artifact.revision} 版 · {artifact.status === 'FROZEN' ? '已冻结' : '待确认'}</small></div><Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="收回文档检查器" /></header>
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'document', label: '文档', children: <div className="modeling-document-sections">{standardSectionOrder.map(({ key, heading }) => <section key={key}><Typography.Title level={5}>{heading}</Typography.Title><Typography.Paragraph>{artifact.sections[key]}</Typography.Paragraph></section>)}</div> },
      { key: 'model', label: '模型', children: candidate ? <div className="modeling-document-candidate">
        <div className="document-candidate-counts">{primaryKinds.map((kind) => {
          const label = labels[kind];
          const count = candidate.items.filter((item) => item.kind === kind).length;
          return <span key={kind} aria-label={`${label} ${count}`}><SemanticObjectTag kind={kind} label={label} /><strong>{count}</strong></span>;
        })}</div>
        {candidate.items.map((item) => <article key={item.candidateId}><div>{isPrimaryKind(item.kind)
          ? <SemanticObjectTag kind={item.kind} label={labels[item.kind]} />
          : <Tag>{labels[item.kind]}</Tag>}<strong>{item.name}</strong><Tag color={item.provenance === 'OBSERVED' ? 'green' : 'orange'}>{item.provenance === 'OBSERVED' ? '证据事实' : '待确认推断'}</Tag></div><p>{item.summary}</p><small>{item.evidenceRefs.length ? `依据：${item.evidenceRefs.length} 处来源材料` : '尚无独立证据定位'}</small></article>)}
        {candidate.unresolved.length > 0 && <section className="document-unresolved"><strong>待确认事项</strong>{candidate.unresolved.map((item) => <p key={item}>{item}</p>)}</section>}
      </div> : <Empty description="确认开始建模后显示候选对象" /> },
    ]} />
  </aside>;
}
