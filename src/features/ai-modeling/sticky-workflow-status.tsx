import { useMemo, useState } from 'react';
import { Button, Progress, Space, Tag } from 'antd';
import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { projectStickyStatus, type WorkflowAnalysisState } from './workflow-projection.ts';
import type { WorkspaceSnapshot } from './runtime-types.ts';
import { WorkflowStatusMark } from './workflow-status-mark.ts';

const stageLabel = {
  WAITING: '等待资料', ANALYZING: '正在分析', SOURCE_READY: '证据已接入', REVIEW: '审核中', BLOCKED: '需要补充', PUBLISHED: '已发布',
};

export default function StickyWorkflowStatus({
  snapshot, analysis, evidenceOnly = false, onNavigate,
}: {
  snapshot: WorkspaceSnapshot;
  analysis?: WorkflowAnalysisState | null;
  evidenceOnly?: boolean;
  onNavigate(stage: 'document' | 'analysis' | 'review'): void;
}) {
  const [expanded, setExpanded] = useState(false);
  const status = useMemo(() => projectStickyStatus(snapshot, analysis, evidenceOnly), [snapshot, analysis, evidenceOnly]);
  const sourceWorkspace = snapshot.sourceWorkspace;
  const blocked = status.stage === 'BLOCKED';
  const published = status.stage === 'PUBLISHED';
  return <header className={`sticky-workflow-status ${blocked ? 'blocked' : ''}`}>
    <div className="sticky-status-main">
      <button type="button" onClick={() => onNavigate('document')}><span>资料</span><strong>{sourceWorkspace ? '已选来源' : '当前资料'}</strong><small>{sourceWorkspace ? `已登记 ${sourceWorkspace.selectedSources.length} 个来源` : status.fileName ?? '请在下方添加资料'}</small></button>
      <button type="button" onClick={() => onNavigate('analysis')}><span>状态</span><strong>{stageLabel[status.stage]}</strong><small>{blocked ? '当前资料还需要补充' : status.stage === 'SOURCE_READY' ? '资料已接入，等待生成建议' : status.modelVersion ? '建模建议已生成' : '等待生成建模建议'}</small></button>
      <button type="button" onClick={() => onNavigate('review')} className="sticky-review-progress">
        {published || blocked
          ? <WorkflowStatusMark tone={published ? 'PUBLISHED' : 'ERROR'} label={published ? '已发布' : '需要补充'} />
          : <Progress type="circle" size={38} percent={status.percent} format={() => `${status.percent}%`} />}
        <span><strong>{published ? '模型已发布' : blocked ? '需要补充' : '审核进度'}</strong><small>{blocked ? '补充资料后可以继续审核' : `待确认 ${status.pending}`}</small></span>
      </button>
      <Button type="text" icon={expanded ? <UpOutlined /> : <DownOutlined />} onClick={() => setExpanded((value) => !value)} aria-label={expanded ? '收起完整状态' : '展开完整状态'} />
    </div>
    {expanded && <div className="sticky-status-detail"><Space wrap><Tag color="green">系统采纳 {status.autoApproved}</Tag><Tag color="blue">人工采纳 {status.userApproved}</Tag><Tag color="red">异议 {status.rejected}</Tag><Tag color="orange">待确认 {status.pending}</Tag></Space></div>}
  </header>;
}
