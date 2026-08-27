import { useState, useMemo } from 'react';
import { Button, Space, Input, Select, Table, Tag, Popconfirm, message, Typography } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { BusinessRule, RuleMode, RuleStatus } from '../../types';
import BusinessRuleDrawer from '../../components/rule/BusinessRuleDrawer';

const { Text } = Typography;

const modeLabels: Record<RuleMode, string> = { COMPARISON: '比较判定', FILTER: '筛选判定', EXISTENCE: '存在判定', COMPOSITE: '组合判定' };
const modeColors: Record<RuleMode, string> = { COMPARISON: 'blue', FILTER: 'green', EXISTENCE: 'orange', COMPOSITE: 'purple' };
const statusLabels: Record<RuleStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', INACTIVE: '已停用' };
const statusColors: Record<RuleStatus, string> = { DRAFT: 'default', PUBLISHED: 'green', INACTIVE: 'red' };

export default function BusinessRuleTab() {
  const { businessRules, deleteBusinessRule, publishBusinessRule } = useStore();
  const [search, setSearch] = useState('');
  const [filterMode, setFilterMode] = useState<RuleMode | ''>('');
  const [filterStatus, setFilterStatus] = useState<RuleStatus | ''>('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<BusinessRule | null>(null);

  const filtered = useMemo(() => {
    let list = businessRules;
    if (search) { const q = search.toLowerCase(); list = list.filter((r) => r.ruleName.toLowerCase().includes(q) || r.ruleCode.toLowerCase().includes(q) || r.triggerWords.some((w) => w.toLowerCase().includes(q))); }
    if (filterMode) list = list.filter((r) => r.mode === filterMode);
    if (filterStatus) list = list.filter((r) => r.status === filterStatus);
    return list;
  }, [businessRules, search, filterMode, filterStatus]);

  const handleNew = () => { setEditing(null); setOpen(true); };
  const handleEdit = (r: BusinessRule) => { setEditing(r); setOpen(true); };

  const columns = [
    { title: '规则名称', key: 'name', render: (_: unknown, r: BusinessRule) => <Space direction="vertical" size={0}><strong>{r.ruleName}</strong><Text type="secondary" style={{ fontSize: 12 }}>{r.ruleCode}</Text></Space> },
    { title: '判定模式', dataIndex: 'mode', key: 'mode', render: (v: RuleMode) => <Tag color={modeColors[v]}>{modeLabels[v]}</Tag> },
    { title: '主判定对象', key: 'target', render: (_: unknown, r: BusinessRule) => r.targetObjectName || `${r.targetObjectType}#${r.targetObjectId}` },
    { title: '触发词', dataIndex: 'triggerWords', key: 'tw', render: (v: string[]) => v.length ? v.map((w) => <Tag key={w}>{w}</Tag>) : <Text type="secondary">-</Text> },
    { title: '对立规则', key: 'opp', render: (_: unknown, r: BusinessRule) => {
      const opp = businessRules.find((x) => x.id === r.oppositeRuleId);
      return opp ? <Tag>{opp.ruleName}</Tag> : <Text type="secondary">-</Text>;
    } },
    { title: '状态', dataIndex: 'status', key: 'st', render: (v: RuleStatus) => <Tag color={statusColors[v]}>{statusLabels[v]}</Tag> },
    { title: '校验', key: 'val', render: (_: unknown, r: BusinessRule) => {
      if (!r.lastValidation) return <Text type="secondary">-</Text>;
      const b = r.lastValidation.blockers; const w = r.lastValidation.warnings;
      return <Space size={4}>{b > 0 ? <Tag color="red">{b} 阻断</Tag> : null}{w > 0 ? <Tag color="orange">{w} 警告</Tag> : null}{b === 0 && w === 0 ? <Tag color="green">通过</Tag> : null}</Space>;
    } },
    { title: '操作', key: 'a', width: 200, render: (_: unknown, r: BusinessRule) => <Space>
      {r.status === 'DRAFT' && <Button size="small" type="link" onClick={() => { const i = publishBusinessRule(r.id); if (i.some((x) => x.severity === 'BLOCKER')) message.error(i[0].message); else message.success('已发布'); }}>发布</Button>}
      <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(r)}>编辑</Button>
      <Popconfirm title="确认删除？" onConfirm={() => { deleteBusinessRule(r.id); message.success('已删除'); }}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm>
    </Space> },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }} wrap>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleNew}>新建业务规则</Button>
        <Input.Search placeholder="搜索名称、编码、触发词" allowClear style={{ width: 260 }} onSearch={setSearch} onChange={(e) => !e.target.value && setSearch('')} />
        <Select style={{ width: 130 }} value={filterMode} onChange={setFilterMode} placeholder="按模式"
          options={[{ value: '', label: '全部模式' }, ...Object.entries(modeLabels).map(([v, l]) => ({ value: v, label: l }))]} />
        <Select style={{ width: 130 }} value={filterStatus} onChange={setFilterStatus} placeholder="按状态"
          options={[{ value: '', label: '全部状态' }, ...Object.entries(statusLabels).map(([v, l]) => ({ value: v, label: l }))]} />
      </Space>
      <Table dataSource={filtered} columns={columns} rowKey="id" size="small" pagination={false} />

      <BusinessRuleDrawer open={open} rule={editing} onClose={() => setOpen(false)} />
    </>
  );
}
