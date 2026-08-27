import { useState } from 'react';
import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { copyTechnicalText, presentTechnicalValue } from '../features/source-management/technical-value.ts';
import './readable-technical-value.css';

export { copyTechnicalText } from '../features/source-management/technical-value.ts';

export default function ReadableTechnicalValue({ value, label }: { value: unknown; label?: string }) {
  const presented = presentTechnicalValue(value);
  const text = presented.display;
  const canCollapse = text.length > 44 || text.includes('\n');
  const [expanded, setExpanded] = useState(false);
  const [copyStatus, setCopyStatus] = useState<'IDLE' | 'COPIED' | 'FAILED'>('IDLE');

  const copy = async () => {
    const copied = await copyTechnicalText(presented.copy, navigator.clipboard);
    setCopyStatus(copied ? 'COPIED' : 'FAILED');
    window.setTimeout(() => setCopyStatus('IDLE'), 1800);
  };

  return <div className={`readable-technical-value ${expanded || !canCollapse ? 'expanded' : 'collapsed'}`}>
    {label && <small>{label}</small>}
    <code>{text}</code>
    <span className="technical-value-actions">
      {canCollapse && <Button type="link" size="small" aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}>{expanded ? '收起' : '展开'}</Button>}
      <Button type="link" size="small" danger={copyStatus === 'FAILED'} icon={copyStatus === 'COPIED' ? <CheckOutlined /> : <CopyOutlined />} onClick={() => void copy()}>{copyStatus === 'COPIED' ? '已复制' : copyStatus === 'FAILED' ? '复制失败' : '复制'}</Button>
    </span>
  </div>;
}
