import { useState, useMemo } from 'react';
import { Card, Table, Button, Space, Tag, Popconfirm, message, Empty, Select, Typography } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, LinkOutlined, DisconnectOutlined } from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { BusinessRule, RuleMode, RuleStatus, RelationObjectType } from '../../types';
import BusinessRuleDrawer from '../../components/rule/BusinessRuleDrawer';

const { Text } = Typography;

const modeLabels: Record<RuleMode, string> = { COMPARISON: '比较判定', FILTER: '筛选判定', EXISTENCE: '存在判定', COMPOSITE: '组合判定' };
const modeColors: Record<RuleMode, string> = { COMPARISON: 'blue', FILTER: 'green', EXISTENCE: 'orange', COMPOSITE: 'purple' };
const statusLabels: Record<RuleStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', INACTIVE: '已停用' };
const statusColors: Record<RuleStatus, string> = { DRAFT: 'default', PUBLISHED: 'green', INACTIVE: 'red' };

/** 事件/实体详情内嵌的业务规则页签：主判定为本对象的规则 + 关联的已发布规则 */
export default function BusinessRuleLinkTab({ objectType, objectId }: { objectType: RelationObjectType; objectId: number }) {
  const { businessRules, ruleLinks, deleteBusinessRule, addRuleLink, deleteRuleLink } = useStore();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<BusinessRule | null>(null);
  const [linkRuleId, setLinkRuleId] = useState<number | undefined>(undefined);

  const owned = useMemo(() => businessRules.filter((r) => r.targetObjectType === objectType && r.targetObjectId === objectId), [businessRules, objectType, objectId]);
  const links = useMemo(() => ruleLinks.filter((l) => l.ownerType === objectType && l.ownerId === objectId), [ruleLinks, objectType, objectId]);
  const linkedRules = useMemo(() => links.map((l) => businessRules.find((r) => r.id === l.ruleId)).filter(Boolean) as BusinessRule[], [links, businessRules]);

  const linkableOptions = useMemo(() => {
    return businessRules
      .filter((r) => r.status === 'PUBLISHED'
        && !(r.targetObjectType === objectType && r.targetObjectId === objectId)
        && !links.some((l) => l.ruleId === r.id))
      .map((r) => ({ value: r.id, label: `${r.ruleName} (${r.ruleCode}) · ${r.targetObjectName}` }));
  }, [businessRules, links, objectType, objectId]);

  const columns = [
    { title: '规则', key: 'name', render: (_: unknown, r: BusinessRule) => <Space direction="vertical" size={0}><strong>{r.ruleName}</strong><Text type="secondary" style={{ fontSize: 12 }}>{r.ruleCode}</Text></Space> },
    { title: '判定模式', dataIndex: 'mode', key: 'mode', render: (v: RuleMode) => <Tag color={modeColors[v]}>{modeLabels[v]}</Tag> },
    { title: '触发词', dataIndex: 'triggerWords', key: 'tw', render: (v: string[]) => v.length ? v.map((w) => <Tag key={w}>{w}</Tag>) : <Text type="secondary">-</Text> },
    { title: '状态', dataIndex: 'status', key: 'st', render: (v: RuleStatus) => <Tag color={statusColors[v]}>{statusLabels[v]}</Tag> },
  ];

  const handleLink = () => {
    if (!linkRuleId) { message.warning('请选择要关联的规则'); return; }
    addRuleLink(objectType, objectId, linkRuleId);
    message.success('已关联规则');
    setLinkRuleId(undefined);
  };

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); setOpen(true); }}>新建规则</Button>
        <Text type="secondary">主判定对象锁定为当前{objectType === 'EVENT' ? '事件' : '实体'}</Text>
      </Space>

      <Card size="small" title={<Space><LinkOutlined /> 主判定为本{objectType === 'EVENT' ? '事件' : '实体'}（{owned.length}）</Space>} style={{ marginBottom: 12 }}>
        <Table dataSource={owned} columns={[...columns, {
          title: '操作', key: 'a', width: 120, render: (_: unknown, r: BusinessRule) => <Space>
            <Button size="small" icon={<EditOutlined />} onClick={() => { setEditing(r); setOpen(true); }} />
            <Popconfirm title="确认删除？" onConfirm={() => { deleteBusinessRule(r.id); message.success('已删除'); }}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm>
          </Space>,
        }]} rowKey="id" size="small" pagination={false} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无规则，点击「新建规则」" /> }} />
      </Card>

      <Card size="small" title={`关联的已发布规则（${linkedRules.length}）`} extra={
        <Space>
          <Select style={{ width: 260 }} showSearch optionFilterProp="label" placeholder="选择要关联的已发布规则" value={linkRuleId} onChange={setLinkRuleId} options={linkableOptions} disabled={linkableOptions.length === 0} />
          <Button icon={<LinkOutlined />} onClick={handleLink} disabled={!linkRuleId}>关联</Button>
        </Space>
      }>
        <Table dataSource={linkedRules} columns={[...columns, {
          title: '操作', key: 'a', width: 120, render: (_: unknown, r: BusinessRule) => {
            const link = links.find((l) => l.ruleId === r.id);
            return <Popconfirm title="解除关联？" onConfirm={() => { if (link) { deleteRuleLink(link.id); message.success('已解除关联'); } }}>
              <Button size="small" icon={<DisconnectOutlined />}>解除关联</Button>
            </Popconfirm>;
          },
        }]} rowKey="id" size="small" pagination={false} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无关联规则" /> }} />
      </Card>

      <BusinessRuleDrawer open={open} rule={editing} lockedTargetType={objectType} lockedTargetId={objectId} onClose={() => setOpen(false)} />
    </div>
  );
}
