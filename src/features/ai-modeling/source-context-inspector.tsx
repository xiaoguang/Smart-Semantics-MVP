import { useEffect, useMemo, useState } from 'react';
import { Badge, Button, Collapse, Empty, Input, Segmented, Tag, Typography } from 'antd';
import { CloseOutlined, DatabaseOutlined, FileTextOutlined, GithubOutlined } from '@ant-design/icons';
import ModelResultInspector, { type InspectorNavigation } from './model-result-inspector.tsx';
import CandidateModelInspector from './candidate-model-inspector.tsx';
import type { HydratedResult, WorkspaceSnapshot } from './runtime-types.ts';
import type { ModelingObjectRef } from './modeling-object-projection.ts';
import {
  sourceFamilyLabel, type ConnectorDefinition, type EvidenceClaim, type SourceAsset,
  type SourceEvidenceLocator, type SourceRole,
} from './source-bundle.ts';
import { projectDatabaseEvidenceSections } from './database-evidence.ts';
import { projectRepositoryEvidenceSections } from './repository-evidence.ts';
import { useSourceManagement } from '../source-management/source-management-context.tsx';
import { projectSnapshotAsset } from '../source-management/projection.ts';
import './candidate-model-inspector.css';

type View = 'SOURCES' | 'RECONCILIATION' | 'MODEL';
const sourceRoleLabels: Record<SourceRole, string> = {
  BUSINESS_DEFINITION: '业务定义', PHYSICAL_STRUCTURE: '物理结构',
  ACTUAL_USAGE: '实际使用', AUXILIARY_EVIDENCE: '辅助证据',
};

function SourceIcon({ asset }: { asset: SourceAsset }) {
  if (['DATABASE_CONNECTOR', 'DOCUMENT_DATABASE', 'SEARCH_INDEX'].includes(asset.origin)) return <DatabaseOutlined />;
  if (asset.origin === 'CODE_REPOSITORY') return <GithubOutlined />;
  return <FileTextOutlined />;
}

const authorityLabels = {
  PRIMARY: '原始来源', CORROBORATING: '互证来源', DERIVED: '派生来源', AUXILIARY: '辅助来源',
} as const;

function locatorText(locator?: SourceEvidenceLocator) {
  if (!locator) return '来源位置未保留';
  if (locator.kind === 'FILE') return `${locator.sourceFile}${locator.page ? ` · 第 ${locator.page} 页` : ''}${locator.section ? ` · ${locator.section}` : ''}`;
  if (locator.kind === 'IMAGE') return `${locator.sourceFile} · 图片标注区域`;
  if (locator.kind === 'DATABASE') return `${locator.datasource}.${locator.schema}${locator.table ? `.${locator.table}` : ''}${locator.field ? `.${locator.field}` : ''}${locator.queryId ? ` · 查询 ${locator.queryId}` : ''}`;
  if (locator.kind === 'GITHUB') return `${locator.repository}/${locator.path}${locator.lineStart ? `:${locator.lineStart}` : ''}`;
  if (locator.kind === 'MONGODB') return `${locator.datasource}.${locator.database}.${locator.collection}${locator.jsonPath ? ` · ${locator.jsonPath}` : ''}`;
  if (locator.kind === 'ELASTICSEARCH') return `${locator.cluster}/${locator.index}${locator.mappingPath ? ` · ${locator.mappingPath}` : ''}${locator.queryId ? ` · ${locator.queryId}` : ''}`;
  if (locator.kind === 'RDF') return '术语图中的关联记录';
  if (locator.kind === 'OBJECT') return `${locator.bucket}/${locator.objectKey} · 版本 ${locator.versionId}`;
  if (locator.kind === 'SHAREPOINT') return `${locator.site}${locator.path}${locator.section ? ` · ${locator.section}` : ''}`;
  if (locator.kind === 'KAFKA') return `${locator.cluster}/${locator.topic} · Schema ${locator.schemaId} · ${locator.offsetRange}`;
  return '来源位置未保留';
}

const sourceStatus = {
  READY: { label: '已读取', color: 'green' },
  PROCESSING: { label: '读取中', color: 'blue' },
  FAILED: { label: '读取失败', color: 'red' },
  NO_RESULT: { label: '尚无结果', color: 'default' },
} as const;

const objectCountLabels: Record<string, string> = {
  tables: '数据表', views: '视图', columns: '字段', indexes: '索引', foreignKeys: '外键',
  procedures: '存储过程', functions: '函数', triggers: '触发器', events: '事件',
  documents: '文档', terms: '术语', relations: '关系', files: '文件',
};

function presentObjectCount(key: string, value: number) {
  return `${objectCountLabels[key] ?? '已保存对象'} ${value}`;
}

function SourceCard({ source, search, allSources, locators, claims }: {
  source: SourceAsset; search: string; allSources: SourceAsset[];
  locators: Record<string, SourceEvidenceLocator>; claims: EvidenceClaim[];
}) {
  const detail = source.evidenceDetail;
  const normalizedSearch = search.trim().toLowerCase();
  const sections = detail?.kind === 'DATABASE'
    ? projectDatabaseEvidenceSections(detail.manifest)
    : detail?.kind === 'REPOSITORY' ? projectRepositoryEvidenceSections(detail.manifest)
      : detail?.kind === 'CONNECTOR' ? [{
        id: 'snapshot', label: '快照证据', records: source.evidenceIds.map((evidenceId) => {
          const claim = claims.find((item) => item.evidenceRefs.includes(evidenceId));
          const locator = locators[evidenceId];
          return {
            evidenceId, objectName: claim ? String(claim.value) : '已归档记录',
            objectType: locator?.kind ?? '已归档材料',
            relativePath: locatorText(locator),
          };
        }),
      }] : [];
  const identity = detail?.kind === 'DATABASE'
    ? 'MySQL · 已保存的数据库结构'
    : detail?.kind === 'REPOSITORY' ? '固定版本源码'
      : detail?.kind === 'CONNECTOR' ? '已保存资料' : source.contentKinds.join(' · ');
  const upstream = (source.upstreamSourceIds ?? []).map((id) => allSources.find((item) => item.sourceId === id)?.displayName ?? '未命名来源');
  const status = sourceStatus[source.status];
  const objectCounts = detail?.kind === 'CONNECTOR' ? Object.entries(detail.objectCounts ?? {}) : [];
  return <section className="source-evidence-card">
    <header><SourceIcon asset={source} /><div><strong>{source.displayName}</strong><small>{identity}</small></div><Tag color={status.color}>{status.label}</Tag></header>
    <Typography.Paragraph type="secondary">{source.summary}</Typography.Paragraph>
    {objectCounts.length > 0 && <div className="source-object-counts">{objectCounts.map(([key, value]) => <span key={key}>{presentObjectCount(key, value)}</span>)}</div>}
    <div className="source-role-tags">
      {source.authority && <Tag color={source.authority === 'DERIVED' ? 'gold' : undefined}>{authorityLabels[source.authority]}</Tag>}
      {source.roles.map((role) => <Tag key={role}>{sourceRoleLabels[role]}</Tag>)}
    </div>
    {upstream.length > 0 && <Typography.Paragraph className="source-lineage" type="secondary">来源于：{upstream.join('、')}</Typography.Paragraph>}
    <Collapse className="source-evidence-sections" items={sections.map((section) => {
      const matching = section.records.filter((record) => !normalizedSearch
        || `${record.objectName} ${record.objectType} ${record.relativePath}`.toLowerCase().includes(normalizedSearch));
      return {
        key: section.id,
        label: <span><strong>{section.label}</strong> <Badge count={section.records.length} overflowCount={9999} /></span>,
        children: matching.length ? <><div className="database-evidence-files">{matching.slice(0, 60).map((record) => <article key={record.evidenceId}>
          <div><strong>{record.objectName}</strong><Tag>{record.objectType}</Tag></div>
          <code title={record.relativePath}>{record.relativePath}</code><small>已归档</small>
        </article>)}</div>{matching.length > 60 && <Typography.Text type="secondary">已显示 60 / {matching.length} 条；请搜索对象或材料名称继续定位。</Typography.Text>}</> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配的材料" />,
      };
    })} />
  </section>;
}

function SourceView({ sources, connectorCatalog = [], locators, claims }: {
  sources: SourceAsset[]; connectorCatalog?: ConnectorDefinition[];
  locators: Record<string, SourceEvidenceLocator>; claims: EvidenceClaim[];
}) {
  const [search, setSearch] = useState('');
  const available = connectorCatalog.filter((item) => item.status === 'AVAILABLE');
  return <div className="source-inspector-list source-evidence-list">
    <Input.Search allowClear value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索来源或对象" />
    {sources.map((source) => <SourceCard key={source.sourceId} source={source} search={search} allSources={sources} locators={locators} claims={claims} />)}
    {available.length > 0 && <Collapse className="available-connector-catalog" items={[{
      key: 'available-connectors', label: `更多可接入来源（${available.length}）`,
      children: <div className="available-connector-list">{available.map((item) => <div key={item.connectorId}>
        <span><strong>{item.vendor}</strong><small>{sourceFamilyLabel(item.family)} · {item.label}</small></span><Tag>可接入</Tag>
      </div>)}</div>,
    }]} />}
  </div>;
}

function locatorLabel(snapshot: WorkspaceSnapshot, id: string) {
  const locator = snapshot.sourceWorkspace?.evidenceLocators[id];
  return locatorText(locator);
}

function ReconciliationView({ snapshot, sources, onResolve }: { snapshot: WorkspaceSnapshot; sources: SourceAsset[]; onResolve?(findingId: string, resolutionId: string): void }) {
  const findings = snapshot.sourceWorkspace?.batch.findings ?? [];
  const claims = snapshot.sourceWorkspace?.evidenceClaims ?? [];
  const order = { BLOCKER: 0, WEAK: 1, CONSISTENT: 2 } as const;
  const sorted = [...findings].sort((left, right) => order[left.severity] - order[right.severity]);
  const onlyOneSource = (snapshot.sourceWorkspace?.batch.snapshotIds?.length ?? sources.length) < 2;
  return <div className="reconciliation-list">{sorted.length ? sorted.map((finding) => <article key={finding.findingId} className={`finding-${finding.severity.toLowerCase()}`}>
    <header><strong>{finding.title}</strong><Tag color={finding.severity === 'BLOCKER' ? 'red' : finding.severity === 'WEAK' ? 'orange' : 'green'}>{finding.severity === 'BLOCKER' ? '结构冲突' : finding.severity === 'WEAK' ? '弱依据' : '一致'}</Tag></header>
    <Typography.Paragraph>{finding.conclusion}</Typography.Paragraph>
    {finding.affectedObjects.length > 0 && <small>影响：{finding.affectedObjects.join('、')}</small>}
    {finding.decision ? <div className="finding-decision">已决定：{finding.options.find((option) => option.id === finding.decision?.resolutionId)?.label}</div>
      : finding.severity === 'BLOCKER' && <div className="finding-options">{finding.options.map((option) => <Button key={option.id} type={option.id === finding.recommendationId ? 'primary' : 'default'} danger={option.id === 'keep_blocked'} size="small" onClick={() => onResolve?.(finding.findingId, option.id)}>{option.label}</Button>)}</div>}
    <Collapse ghost size="small" items={[{
      key: 'evidence', label: `来源材料与结论（${finding.sourceIds.length}）`,
      children: <div className="finding-source-claims">{finding.sourceIds.map((sourceId) => {
        const source = sources.find((item) => item.connector?.connectorId === sourceId || item.sourceId === sourceId);
        const sourceEvidence = finding.evidenceIds.filter((id) => source?.evidenceIds.includes(id));
        const sourceClaims = claims.filter((claim) => claim.subject === sourceId
          && claim.evidenceRefs.some((id) => finding.evidenceIds.includes(id)));
        const authority = sourceClaims[0]?.authority ?? source?.authority ?? 'CORROBORATING';
        return <section key={sourceId} className="finding-source-claim">
          <header><strong>{source?.displayName ?? '未命名来源'}</strong><Tag>{authorityLabels[authority]}</Tag></header>
          {sourceClaims.length > 0 ? sourceClaims.map((claim) => <Typography.Paragraph key={claim.claimId}>{String(claim.value)}</Typography.Paragraph>)
            : <Typography.Paragraph type="secondary">该来源支持当前结论，主张摘要未单独保留。</Typography.Paragraph>}
          {sourceEvidence.map((id) => <div className="evidence-locator-line" key={id}><span>来源位置</span><span>{locatorLabel(snapshot, id)}</span></div>)}
        </section>;
      })}</div>,
    }]} />
  </article>) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={onlyOneSource ? '当前只有数据库单一来源，暂不能交叉验证' : '开始建模后在这里查看来源互证'} />}</div>;
}

export default function SourceContextInspector({ snapshot, result, open, selectedRef, navigation, onOpen, onClose, onSelect, onClearSelection, onReviewAction, onResolveFinding }: {
  snapshot: WorkspaceSnapshot | null; result?: HydratedResult; open: boolean; selectedRef?: ModelingObjectRef; navigation?: InspectorNavigation;
  onOpen(): void; onClose(): void; onSelect(ref: ModelingObjectRef): void; onClearSelection(): void;
  onReviewAction?(itemId: string, objectName: string, decision: 'APPROVED' | 'REJECTED'): void;
  onResolveFinding?(findingId: string, resolutionId: string): void;
}) {
  const { snapshot: managedSources } = useSourceManagement();
  const multiSource = Boolean(snapshot?.sourceWorkspace);
  const [view, setView] = useState<View>(multiSource ? 'SOURCES' : 'MODEL');
  useEffect(() => { setView(multiSource ? 'SOURCES' : 'MODEL'); }, [snapshot?.systemCode, multiSource]);
  useEffect(() => { if (navigation || selectedRef) setView('MODEL'); }, [navigation?.requestId, selectedRef]);
  const selectedSources = useMemo(() => {
    const legacy = snapshot?.sourceWorkspace?.selectedSources ?? [];
    const attached = (snapshot?.sourceWorkspace?.batch.snapshotIds ?? []).flatMap((snapshotId) => {
      const asset = managedSources ? projectSnapshotAsset(managedSources, snapshotId) : null;
      return asset ? [asset] : [];
    });
    const byId = new Map([...legacy, ...attached].map((item) => [item.sourceId, item]));
    return [...byId.values()];
  }, [managedSources, snapshot]);
  const counts = useMemo(() => ({
    sources: selectedSources.length,
    findings: snapshot?.sourceWorkspace?.batch.findings.filter((item) => item.severity !== 'CONSISTENT').length ?? 0,
    model: snapshot?.sourceWorkspace?.candidateModel ? 1 : result ? 1 : 0,
  }), [result, selectedSources.length, snapshot]);
  if (!multiSource) return <ModelResultInspector {...{ result, open, selectedRef, navigation, onOpen, onClose, onSelect, onClearSelection, onReviewAction }} />;
  if (!open) return <aside className="model-inspector-rail"><Button type="text" onClick={onOpen}><span className="rail-count">{counts.sources}</span><span>来源资料</span></Button></aside>;
  const candidate = snapshot?.sourceWorkspace?.candidateModel;
  return <aside className="source-context-inspector">
    <header><div><strong>来源资料</strong><small>已载入的资料与比较结果</small></div><Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="收起来源资料" /></header>
    <Segmented block value={view} onChange={(value) => setView(value as View)} options={[
      { value: 'SOURCES', label: <span>来源材料 <Badge count={counts.sources} showZero /></span> },
      { value: 'RECONCILIATION', label: <span>来源比较 <Badge count={counts.findings} showZero /></span> },
      { value: 'MODEL', label: <span>建模建议 <Badge count={counts.model} showZero /></span> },
    ]} />
    <div className="source-context-content">{view === 'SOURCES' ? <SourceView sources={selectedSources} connectorCatalog={snapshot?.sourceWorkspace?.connectorCatalog} locators={snapshot?.sourceWorkspace?.evidenceLocators ?? {}} claims={snapshot?.sourceWorkspace?.evidenceClaims ?? []} />
      : view === 'RECONCILIATION' ? <ReconciliationView snapshot={snapshot!} sources={selectedSources} onResolve={onResolveFinding} />
        : candidate ? <CandidateModelInspector model={candidate} /> : result
          ? <ModelResultInspector {...{ result, open: true, selectedRef, navigation, onOpen, onClose: () => undefined, onSelect, onClearSelection, onReviewAction }} />
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未生成候选语义模型" />}</div>
  </aside>;
}
