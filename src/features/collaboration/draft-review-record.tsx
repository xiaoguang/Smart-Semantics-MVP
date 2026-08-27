import { useMemo, useState } from 'react';
import { Button, Collapse, Input, Modal, Tag, message } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ChangeRequest, CollaborationCatalog, PersonalDraft } from './types.ts';
import {
  projectDraftPublicationChecks, projectDraftReview, projectDraftWorkflowSummary,
  paginateDraftChanges, shouldShowDraftSearch,
} from './draft-comparison.ts';
import { demoUsers } from './runtime.ts';
import type { ModelBrowserComparison, ModelBrowserObjectRef } from '../model-browser/types.ts';
import { WorkflowStatusMark } from '../ai-modeling/workflow-status-mark.ts';

const kindLabel: Record<string, string> = {
  ENTITY: '实体', EVENT: '事件', FIELD: '字段', RELATION: '关系', DIMENSION: '维度', METRIC: '指标',
  RULE: '规则', ALIAS: '同义词', TIME_RULE: '时间语义',
};
const operationLabel = { ADDED: '新增', MODIFIED: '修改', REMOVED: '删除' } as const;

export function DraftReviewRecord({ draft, baseCatalog, request, canReview, canPublish, onSelect, onApprove, onRequestChanges, onReject, onPublish }: {
  draft: PersonalDraft;
  baseCatalog: CollaborationCatalog;
  request?: ChangeRequest | null;
  canReview: boolean;
  canPublish: boolean;
  onSelect(ref: ModelBrowserObjectRef, comparison: ModelBrowserComparison): void;
  onApprove?(): void;
  onRequestChanges?(reason: string): void;
  onReject?(reason: string): void;
  onPublish?(reason: string): void;
}) {
  const review = useMemo(() => projectDraftReview({ draft, baseCatalog }), [draft, baseCatalog]);
  const [query, setQuery] = useState('');
  const [limit, setLimit] = useState(20);
  const [reasonMode, setReasonMode] = useState<'CHANGES' | 'REJECT' | 'PUBLISH'>();
  const [reason, setReason] = useState('');
  const normalizedQuery = query.trim().toLocaleLowerCase('zh-CN');
  const page = paginateDraftChanges(review.changes, { query: normalizedQuery, limit });
  const remainingCount = page.total - page.visible.length;
  const author = demoUsers.find((item) => item.userId === draft.ownerUserId)?.displayName ?? draft.ownerUserId;
  const workflow = projectDraftWorkflowSummary(draft);
  const publicationChecks = request ? projectDraftPublicationChecks({ draft, request, baseCatalog }) : [];
  const showSearch = shouldShowDraftSearch(review.changes.length);
  const hasActions = (canReview && request && ['SUBMITTED', 'IN_REVIEW'].includes(request.status))
    || (canPublish && request?.status === 'APPROVED');
  const submitReason = () => {
    if (!reasonMode) return;
    if (!/[\u3400-\u9fff]/.test(reason)) { message.warning(reasonMode === 'PUBLISH' ? '请填写中文发布理由' : '请填写中文审核理由'); return; }
    if (reasonMode === 'CHANGES') onRequestChanges?.(reason);
    if (reasonMode === 'REJECT') onReject?.(reason);
    if (reasonMode === 'PUBLISH') onPublish?.(reason);
    setReasonMode(undefined); setReason('');
  };
  return <article className="draft-review-record">
    <header><div><Tag color="blue">草稿审阅</Tag><strong>{review.title}</strong><span className="draft-autosave-status">草稿与审核进度自动保存</span></div><small>{author} · {review.baseVersion} → {review.targetVersion} · 证据批次 R{draft.materializedData.semanticSidecar?.schemaVersion === 2 ? draft.materializedData.semanticSidecar.batch.revision : '?'}</small></header>
    <div className="draft-review-summary"><span>修改 {review.countsByOperation.MODIFIED}</span><span>新增 {review.countsByOperation.ADDED}</span><span>删除 {review.countsByOperation.REMOVED}</span><span>重点 {review.riskCount}</span></div>
    <Collapse className="draft-workflow-process" ghost defaultActiveKey={['workflow']} items={[{
      key: 'workflow', label: '建模过程', children: <div className="draft-workflow-steps">
        <span><WorkflowStatusMark tone="SUCCESS" label="来源已校验" compact />已校验 {workflow.sourceCount} 个来源快照</span>
        <span><WorkflowStatusMark tone="SUCCESS" label="批次已冻结" compact />已冻结证据批次 R{workflow.batchRevision}</span>
        <span><WorkflowStatusMark tone="SUCCESS" label="互证已完成" compact />已完成 {workflow.findingCount} 项多来源互证</span>
        <span><WorkflowStatusMark tone="SUCCESS" label="冲突已处理" compact />{workflow.resolvedConflictCount} 项冲突已有决定历史</span>
        <span><WorkflowStatusMark tone="SUCCESS" label="草稿已生成" compact />已生成 {workflow.changeCount} 项拟发布变化</span>
      </div>,
    }]} />
    {showSearch && <Input className="draft-review-search" allowClear prefix={<SearchOutlined />} value={query} onChange={(event) => { setQuery(event.target.value); setLimit(20); }} placeholder="搜索名称、摘要或对象类型" />}
    {review.changes.length === 0 && review.unlinkedChanges.length === 0
      ? <div className="draft-review-empty"><strong>尚无模型变化</strong><span>可以继续添加资料，或在其他页面修改对象。</span></div>
      : <div className="draft-review-scroll"><div className="draft-change-list" aria-label="草稿变化列表">{page.visible.map((change) => <button type="button" key={change.changeId} className={`draft-change-row ${change.attention.toLowerCase()}`} onClick={() => onSelect(change.objectRef, {
        operation: change.operation, name: change.name, changedFields: change.changedFields,
        evidenceRefs: change.evidenceRefs, affectedRefs: change.affectedRefs,
      })}>
        <span><Tag>{kindLabel[change.kind] ?? change.kind}</Tag><Tag color={change.operation === 'REMOVED' ? 'red' : change.operation === 'ADDED' ? 'green' : 'blue'}>{operationLabel[change.operation]}</Tag><strong>{change.name}</strong></span>
        <small>{change.summary}</small><em>{change.evidenceRefs.length} 处依据{change.affectedRefs.length ? ` · 影响 ${change.affectedRefs.length} 个对象` : ''}</em>
      </button>)}</div>{review.unlinkedChanges.length > 0 && <Collapse ghost items={[{
      key: 'unlinked', label: `历史变化暂时无法定位（${review.unlinkedChanges.length}）`,
      children: <div className="draft-unlinked-list">{review.unlinkedChanges.map((change) => <div key={change.changeId}><strong>{change.category}</strong><span>{change.summary}</span><small>{change.reason === 'PROCESS_ONLY' ? '建模过程记录，不作为模型对象变化' : '旧记录缺少对象引用，仅供查阅'}</small></div>)}</div>,
    }]} />}{remainingCount > 0 && <Button type="link" onClick={() => setLimit((value) => value + 20)}>继续显示（还有 {remainingCount} 项）</Button>}</div>}
    {hasActions && <footer>
      {canReview && request && ['SUBMITTED', 'IN_REVIEW'].includes(request.status) && <div><Button type="primary" onClick={() => Modal.confirm({ title: '确认审核通过？', content: '可以不填写理由，系统将记录“无补充意见”。', okText: '审核通过', onOk: onApprove })}>审核通过</Button><Button onClick={() => setReasonMode('CHANGES')}>要求修改</Button><Button danger type="text" onClick={() => setReasonMode('REJECT')}>拒绝</Button></div>}
      {canPublish && request?.status === 'APPROVED' && <div className="draft-publication-area"><Collapse ghost size="small" items={[{
        key: 'checks', label: `发布前检查 ${publicationChecks.filter((item) => item.passed).length}/${publicationChecks.length}`,
        children: <div className="draft-publication-checks">{publicationChecks.map((item) => <span key={item.key}><WorkflowStatusMark tone={item.passed ? 'SUCCESS' : 'PENDING'} label={item.label} compact />{item.label}</span>)}</div>,
      }]} /><Button type="primary" disabled={publicationChecks.some((item) => !item.passed)} onClick={() => setReasonMode('PUBLISH')}>发布 V2</Button></div>}
    </footer>}
    <Modal title={reasonMode === 'PUBLISH' ? '发布 V2' : reasonMode === 'REJECT' ? '拒绝变更' : '要求修改'} open={Boolean(reasonMode)} onCancel={() => { setReasonMode(undefined); setReason(''); }} onOk={submitReason} okText={reasonMode === 'PUBLISH' ? '确认发布' : '提交意见'}>
      <Input.TextArea rows={4} value={reason} onChange={(event) => setReason(event.target.value)} placeholder={reasonMode === 'PUBLISH' ? '请输入中文发布理由' : '请说明需要修改的内容'} />
    </Modal>
  </article>;
}

export function requestAsDraft(request: ChangeRequest): PersonalDraft {
  return {
    draftId: request.draftId, workspaceId: request.workspaceId, modelSpaceId: request.modelSpaceId,
    ownerUserId: request.authorUserId, title: request.title, baseCatalogVersion: request.baseCatalogVersion,
    baseFingerprint: request.baseFingerprint, revision: request.draftRevision, status: 'SUBMITTED',
    materializedData: request.proposedData, changes: request.changes, createdAt: request.submittedAt, updatedAt: request.approvals.at(-1)?.decidedAt ?? request.submittedAt,
  };
}
