import { useState } from 'react';
import { Card, Tabs, Badge } from 'antd';
import { BarChartOutlined, AimOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import MetricTab from './MetricTab';
import MetricTargetTab from './MetricTargetTab';
import BusinessRuleTab from './BusinessRuleTab';

export default function MetricPage() {
  const [tab, setTab] = useState('metrics');
  const { metrics, metricTargets, businessRules } = useStore();
  const draftMetrics = metrics.filter((m) => m.status === 'DRAFT').length;
  const draftRules = businessRules.filter((r) => r.status === 'DRAFT').length;

  return (
    <Card title="指标配置与业务规则" style={{ height: '100%' }}>
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          { key: 'metrics', label: <span><BarChartOutlined /> 指标 <Badge count={draftMetrics} size="small" offset={[6, -2]} /></span>, children: <MetricTab /> },
          { key: 'targets', label: <span><AimOutlined /> 指标目标 ({metricTargets.length})</span>, children: <MetricTargetTab /> },
          { key: 'rules', label: <span><ThunderboltOutlined /> 业务规则 <Badge count={draftRules} size="small" offset={[6, -2]} /></span>, children: <BusinessRuleTab /> },
        ]}
      />
    </Card>
  );
}
