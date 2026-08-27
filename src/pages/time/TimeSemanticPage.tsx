import { useState } from 'react';
import { Tabs, Card } from 'antd';
import { CalendarOutlined, FieldTimeOutlined, GiftOutlined } from '@ant-design/icons';
import CalendarTab from './CalendarTab';
import RuleTab from './RuleTab';
import HolidayTab from './HolidayTab';
import { useLinguanWorkspace } from '../../features/ai-modeling/workspace-context';

export default function TimeSemanticPage() {
  const { workspaceReadOnly } = useLinguanWorkspace();
  const [activeTab, setActiveTab] = useState('rules');

  return (
    <Card title="时间语义配置" style={{ height: '100%' }}>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: 'rules', label: <span><FieldTimeOutlined /> 时间规则</span>, children: <RuleTab readOnly={workspaceReadOnly} /> },
          { key: 'calendars', label: <span><CalendarOutlined /> 时间日历</span>, children: <CalendarTab readOnly={workspaceReadOnly} /> },
          { key: 'holidays', label: <span><GiftOutlined /> 节假日</span>, children: <HolidayTab readOnly={workspaceReadOnly} /> },
        ]}
      />
    </Card>
  );
}
