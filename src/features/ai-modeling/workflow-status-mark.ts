import { createElement, type ReactElement } from 'react';
import {
  CheckOutlined, CloseOutlined, LoadingOutlined, MinusOutlined,
} from '@ant-design/icons';

export type WorkflowStatusTone = 'SUCCESS' | 'ERROR' | 'ACTIVE' | 'PENDING' | 'PUBLISHED';

const toneColor: Record<WorkflowStatusTone, string> = {
  SUCCESS: 'var(--dh-success)',
  ERROR: 'var(--dh-danger)',
  ACTIVE: 'var(--dh-primary)',
  PENDING: 'var(--dh-text-tertiary)',
  PUBLISHED: 'var(--dh-success)',
};

export function WorkflowStatusMark({
  tone, label, compact = false,
}: {
  tone: WorkflowStatusTone;
  label: string;
  compact?: boolean;
}): ReactElement {
  const Icon = tone === 'SUCCESS' || tone === 'PUBLISHED' ? CheckOutlined
    : tone === 'ERROR' ? CloseOutlined
      : tone === 'ACTIVE' ? LoadingOutlined : MinusOutlined;
  return createElement(
    'span',
    {
      className: `workflow-status-mark workflow-status-mark-${tone.toLowerCase()}${compact ? ' compact' : ''}`,
      role: 'img',
      'aria-label': label,
      style: { color: toneColor[tone] },
    },
    createElement(Icon, tone === 'ACTIVE' ? { spin: true } : undefined),
  );
}
