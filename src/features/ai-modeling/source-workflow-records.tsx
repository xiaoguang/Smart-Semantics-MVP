import { Button, Collapse, Tag, Typography } from 'antd';
import { WorkflowStatusMark } from './workflow-status-mark.ts';
import type { WorkspaceSnapshot } from './runtime-types.ts';
import { resolveModelingScenario } from './scenario-registry.ts';
import type { SourceAsset } from './source-bundle.ts';

export default function SourceWorkflowRecords({ snapshot, busy, managedSources = [], onStart, onRemove, onResolve, onGenerate }: {
  snapshot: WorkspaceSnapshot; busy: boolean; onStart(): void;
  managedSources?: Array<{ asset: SourceAsset; snapshotId: string }>;
  onRemove(sourceId: string, snapshotId?: string): void;
  onResolve(findingId: string, resolutionId: string): void; onGenerate(): void;
}) {
  const workspace = snapshot.sourceWorkspace;
  if (!workspace) return null;
  const { batch, candidateModel } = workspace;
  const sourceRows = new Map<string, { asset: SourceAsset; snapshotId?: string }>();
  workspace.selectedSources.forEach((asset) => sourceRows.set(asset.sourceId, { asset }));
  managedSources.forEach((item) => {
    if (!sourceRows.has(item.asset.sourceId)) sourceRows.set(item.asset.sourceId, item);
  });
  const selectedSources = [...sourceRows.values()].map((item) => item.asset);
  const database = selectedSources.find((source) => source.evidenceDetail?.kind === 'DATABASE');
  const repository = selectedSources.find((source) => source.evidenceDetail?.kind === 'REPOSITORY');
  const databaseManifest = database?.evidenceDetail?.kind === 'DATABASE' ? database.evidenceDetail.manifest : null;
  const repositoryManifest = repository?.evidenceDetail?.kind === 'REPOSITORY' ? repository.evidenceDetail.manifest : null;
  const blockers = batch.findings.filter((finding) => finding.severity === 'BLOCKER');
  const unresolved = blockers.filter((finding) => !finding.decision || finding.decision.resolutionId === 'keep_blocked');
  const candidateOnly = resolveModelingScenario(snapshot.systemCode).capabilities.lifecycle === 'CANDIDATE_ONLY';
  const hasConnectorCatalog = Boolean(workspace.connectorCatalog?.length);

  return <div className="source-workflow-records">
    {selectedSources.length > 0 && <div className="assistant-message source-collection-summary">
      <div><WorkflowStatusMark tone="SUCCESS" compact label="已登记" /><strong>已读取 {selectedSources.length} 份来源资料</strong></div>
      <Typography.Paragraph type="secondary">每份资料先独立阅读，再比较业务定义、结构和使用情况。</Typography.Paragraph>
      <Collapse ghost size="small" items={[{
        key: 'sources', label: '查看当前来源', children: [...sourceRows.values()].map(({ asset: source, snapshotId }) => <div className="source-summary-line" key={snapshotId ?? source.sourceId}>
          <span>{source.displayName}</span><span><Tag color="green">已读取</Tag>{batch.state === 'COLLECTING' && <Button type="link" size="small" disabled={busy} onClick={() => onRemove(source.sourceId, snapshotId)}>移除</Button>}</span>
        </div>),
      }]} />
      {batch.state === 'COLLECTING' && <div className="source-start-modeling">
        {selectedSources.length < workspace.availableSources.length && <Typography.Text type="secondary">当前证据覆盖有限，仍可先建模；缺少的来源会进入重点确认。</Typography.Text>}
        <Button type="primary" onClick={onStart} loading={busy}>使用当前来源开始建模</Button>
      </div>}
    </div>}

    {databaseManifest && <div className="assistant-message source-processing-record database-evidence-record">
      <div><WorkflowStatusMark tone="SUCCESS" compact label="已连接" /><strong>{database!.displayName}</strong></div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 校验资料范围和内容完整性</div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 读取表、视图和字段结构</div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 读取存储过程、事件、索引和外键</div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 读取查询语句结构</div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 本次范围不包含实际业务数据</div>
      <Typography.Paragraph type="secondary">{databaseManifest.objectCounts.tables} 张表 · {databaseManifest.objectCounts.views} 个视图 · {databaseManifest.objectCounts.columns} 个字段 · {databaseManifest.objectCounts.procedures} 个存储过程</Typography.Paragraph>
    </div>}

    {repositoryManifest && <div className="assistant-message source-processing-record repository-evidence-record">
      <div><WorkflowStatusMark tone="SUCCESS" compact label="已连接" /><strong>{repository!.displayName}</strong></div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 读取已保存版本的源码片段</div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 整理相关结构和迁移记录</div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 读取数据访问中的关联、过滤和计算</div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 读取服务中的状态、规则和权限处理</div>
      <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 读取页面业务术语</div>
      <Typography.Paragraph type="secondary">已保存版本的源码节选</Typography.Paragraph>
    </div>}

    {batch.state !== 'COLLECTING' && <div className="assistant-message source-processing-record reconciliation-record">
      <strong>来源互证完成</strong>
      {hasConnectorCatalog ? <>
        <div><WorkflowStatusMark compact tone="SUCCESS" label="一致" /> 已对齐 {selectedSources.length} 份来源资料</div>
        <div><WorkflowStatusMark compact tone="SUCCESS" label="可追溯" /> 派生资料保留上游来源关系</div>
        <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 已按定义、结构、使用行为和事件顺序整理结论</div>
      </> : <>
        <div><WorkflowStatusMark compact tone="SUCCESS" label="一致" /> 核心表结构与数据库完成对齐</div>
        <div><WorkflowStatusMark compact tone="PENDING" label="待归类" /> {candidateModel?.pendingAssets.length ?? 63} 张扩展表保留为待归类资产</div>
        <div><WorkflowStatusMark compact tone="SUCCESS" label="完成" /> 代码确认数据库未显式声明的业务关联</div>
      </>}
      <div><WorkflowStatusMark compact tone={unresolved.length ? 'ERROR' : 'SUCCESS'} label={unresolved.length ? '待确认' : '完成'} /> {unresolved.length ? `${unresolved.length} 项结构冲突影响候选对象` : '结构冲突已形成处理决定'}</div>
    </div>}

    {blockers.map((finding) => <div className="assistant-message source-conflict-question" key={finding.findingId}>
      <div className="source-conflict-title"><WorkflowStatusMark tone={finding.decision && finding.decision.resolutionId !== 'keep_blocked' ? 'SUCCESS' : 'ERROR'} compact label="结构冲突" /><strong>{finding.title}</strong></div>
      <Typography.Paragraph>{finding.conclusion}</Typography.Paragraph><small>影响：{finding.affectedObjects.join('、')}</small>
      {finding.decision ? <div className="assistant-receipt">已选择：{finding.options.find((option) => option.id === finding.decision?.resolutionId)?.label}<br /><small>这项处理决定已保存。</small></div>
        : <div className="conversation-options source-conflict-options">{finding.options.map((option) => <button type="button" key={option.id} onClick={() => onResolve(finding.findingId, option.id)}><span className="option-radio" /><span><strong>{option.label}{option.id === finding.recommendationId && <Tag color="blue">推荐</Tag>}</strong><small>{option.description}</small></span></button>)}</div>}
    </div>)}

    {batch.state !== 'COLLECTING' && !candidateModel && snapshot.results.length === 0 && (candidateOnly || unresolved.length === 0) && <div className="assistant-message source-ready-to-generate">
      <WorkflowStatusMark tone={unresolved.length ? 'PENDING' : 'SUCCESS'} label={unresolved.length ? '可生成草案' : '互证完成'} />
      <div><strong>{candidateOnly ? '生成可阅读的候选模型' : '来源互证已完成'}</strong><p>{unresolved.length ? '冲突不会隐藏模型，受影响对象会明确标记“待确认”。' : '可以生成候选模型。'}</p><Button type="primary" onClick={onGenerate} loading={busy}>{candidateOnly ? '生成带风险标记的候选模型' : '生成候选模型'}</Button></div>
    </div>}

    {candidateModel && <div className="assistant-message source-candidate-receipt">
      <WorkflowStatusMark tone={candidateModel.status === 'DRAFT' ? 'PENDING' : 'SUCCESS'} label="候选模型" />
      <div><strong>候选模型已生成</strong><p>{candidateModel.counts.entities} 个实体 · {candidateModel.counts.events} 个事件 · {candidateModel.counts.fields} 个字段 · {candidateModel.counts.relations} 条关系</p><small>{candidateModel.status === 'DRAFT' ? '存在结构冲突；模型可以阅读，但不能审核或发布。' : '候选模型已就绪，本阶段仍不审核或发布。'}</small></div>
    </div>}
  </div>;
}
