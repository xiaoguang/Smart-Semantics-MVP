import { Card } from 'antd';
import MetricDefTab from './MetricDefTab';
import type { Metric2WorkspaceData } from './mockData';

export default function MetricConfig2Page({ data, onChange, readOnly = false }: {
  data?: Metric2WorkspaceData;
  onChange?(data: Metric2WorkspaceData): void;
  readOnly?: boolean;
}) {
  return (
    <Card title="指标配置" style={{ height: '100%' }}>
      <MetricDefTab data={data} onChange={onChange} readOnly={readOnly} />
    </Card>
  );
}
