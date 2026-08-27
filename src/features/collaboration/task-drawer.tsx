import { Button, Drawer, Empty, Tag } from 'antd';
import { CheckCircleOutlined, EditOutlined, RocketOutlined } from '@ant-design/icons';
import { useCollaboration } from './collaboration-context.tsx';
import type { CollaborationTask } from './types.ts';

const kindMeta = {
  EDIT: { label: '需要补充', color: 'orange', icon: <EditOutlined />, action: '继续补充' },
  REVIEW: { label: '等待审核', color: 'blue', icon: <CheckCircleOutlined />, action: '审阅' },
  PUBLISH: { label: '等待发布', color: 'green', icon: <RocketOutlined />, action: '发布' },
} as const;

export default function CollaborationTaskDrawer({ open, onClose, onOpenTask }: {
  open: boolean;
  onClose(): void;
  onOpenTask(task: CollaborationTask): void;
}) {
  const collaboration = useCollaboration();

  return <Drawer title={`待我处理 · ${collaboration.tasks.length}`} width="min(560px, 100vw)" open={open} onClose={onClose}>
      {collaboration.tasks.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有需要你处理的事项" /> : <div className="collaboration-task-list">{collaboration.tasks.map((task) => {
        const meta = kindMeta[task.kind];
        return <article className="collaboration-task-row" key={task.taskId}>
          <span className="collaboration-task-icon">{meta.icon}</span>
          <div><strong>{task.title}</strong><small>{task.modelProjectName} · {task.authorName} · {task.changeCount ?? 0} 项变更</small></div>
          <Tag color={meta.color}>{meta.label}</Tag>
          <Button type="primary" onClick={() => onOpenTask(task)}>{meta.action}</Button>
        </article>;
      })}</div>}
    </Drawer>;
}
