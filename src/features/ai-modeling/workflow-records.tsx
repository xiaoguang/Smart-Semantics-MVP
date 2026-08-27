import { Collapse, Space, Tag, Typography } from 'antd';
import { FileMarkdownOutlined } from '@ant-design/icons';
import { projectWorkflowVersions } from './workflow-projection.ts';
import type { HydratedResult, WorkspaceSnapshot } from './runtime-types.ts';
import { WorkflowStatusMark } from './workflow-status-mark.ts';
import { projectModelResultNavigation, type ModelSummaryTarget } from './result-navigation.ts';
import { SemanticObjectTag, type SemanticVisualKind } from '../../components/semantic-object-visuals.ts';

export type { ModelSummaryTarget } from './result-navigation.ts';

export function WorkflowHistory({ snapshot }: { snapshot: WorkspaceSnapshot }) {
  const history = projectWorkflowVersions(snapshot).filter((version) => version.collapsed);
  if (!history.length) return null;
  return <div className="workflow-history">{history.map((version) => <Collapse key={version.documentVersion} ghost items={[{
    key: version.documentVersion,
    label: <span className="workflow-history-label"><WorkflowStatusMark compact tone={version.status === 'NEEDS_SUPPLEMENT' ? 'ERROR' : version.status === 'PUBLISHED' ? 'PUBLISHED' : 'SUCCESS'} label={version.status === 'NEEDS_SUPPLEMENT' ? '需要补充' : version.status === 'PUBLISHED' ? '已发布' : '已完成'} />历史资料记录 · {version.status === 'NEEDS_SUPPLEMENT' ? '需要补充' : version.status === 'PUBLISHED' ? '已发布' : '已完成'}</span>,
    children: <div><Typography.Paragraph>{version.fileName}</Typography.Paragraph><Space wrap><Tag>{version.decided}/{version.total} 项已决定</Tag><Tag>{version.status === 'PUBLISHED' ? '不可变模型已发布' : '版本回执'}</Tag></Space></div>,
  }]} />)}</div>;
}

export function ModelGeneratedSummary({ result, onNavigate }: { result: HydratedResult; onNavigate(target: ModelSummaryTarget): void }) {
  const navigation = projectModelResultNavigation(result.fixture);
  const visualKind: Record<(typeof navigation.items)[number]['label'], SemanticVisualKind> = {
    实体: 'ENTITY', 事件: 'EVENT', 字段: 'FIELD', 关系: 'RELATION', 维度: 'DIMENSION', 指标: 'METRIC',
  };
  return <section id="workflow-document" className="model-generated-summary">
    <FileMarkdownOutlined className="model-summary-icon" />
    <div className="model-summary-title"><strong>当前资料已生成建模建议</strong><small>{result.fixture.fileName}</small></div>
    <div className="model-summary-links">
      {navigation.items.map((item) => <button type="button" aria-label={`查看${item.label}`} key={item.label} onClick={() => onNavigate(item.target)}>
        <strong>{item.count}</strong><SemanticObjectTag kind={visualKind[item.label]} label={item.label} />
      </button>)}
    </div>
  </section>;
}
