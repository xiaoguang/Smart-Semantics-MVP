import { useState, useMemo } from 'react';
import {
  Card, Table, Button, Input, Select, Form, Drawer, Space, Tag, message,
  Switch, Popconfirm, Divider, Descriptions, Empty, Typography,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined,
  LinkOutlined, FileTextOutlined, InfoCircleOutlined,
} from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { SemanticTerm, TermRelatedObject, TermRelatedObjectType } from '../../types';

const { Text } = Typography;

const objectTypeLabels: Record<TermRelatedObjectType, string> = {
  METRIC: '指标', ENTITY: '实体', EVENT: '事件', TERM: '术语', ALIAS: '同义词',
};
const objectTypeColors: Record<TermRelatedObjectType, string> = {
  METRIC: 'blue', ENTITY: 'green', EVENT: 'orange', TERM: 'purple', ALIAS: 'cyan',
};
const relationLabels: Record<string, string> = {
  DEFINES: '定义关联', SYNONYM: '同义词', RELATED: '相关术语',
};

const ALL_TAGS = ['核心指标', '运营', '财务', '用户概念', '交易概念', '口径说明'];

export default function TermPage() {
  const { terms, metrics, entities, events, aliases, addTerm, updateTerm, deleteTerm } = useStore();
  const [filterTag, setFilterTag] = useState<string>('');
  const [search, setSearch] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<SemanticTerm | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewTerm, setPreviewTerm] = useState<SemanticTerm | null>(null);
  const [form] = Form.useForm();

  const filtered = useMemo(() => {
    let list = terms;
    if (filterTag) list = list.filter((t) => t.tags.includes(filterTag));
    if (search) {
      const q = search.toLowerCase();
      list = list.filter((t) => t.termName.toLowerCase().includes(q) || t.termCode.toLowerCase().includes(q) || t.businessDefinition.toLowerCase().includes(q));
    }
    return list;
  }, [terms, filterTag, search]);

  const tagCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    terms.forEach((t) => t.tags.forEach((tag) => { counts[tag] = (counts[tag] || 0) + 1; }));
    return counts;
  }, [terms]);

  const handleNew = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ tags: [], relatedObjects: [], participateInExplanation: true });
    setDrawerOpen(true);
  };

  const handleEdit = (t: SemanticTerm) => {
    setEditing(t);
    form.setFieldsValue({ ...t });
    setDrawerOpen(true);
  };

  const handleSave = () => {
    form.validateFields().then((values) => {
      const term: SemanticTerm = {
        ...values,
        relatedObjects: values.relatedObjects || [],
        status: 'ACTIVE' as const,
      };
      if (editing) {
        updateTerm(editing.id, term);
        message.success('术语已更新');
      } else {
        const issues = addTerm(term);
        if (issues.some((i) => i.severity === 'BLOCKER')) {
          message.error(issues[0].message);
          return;
        }
        message.success('术语已创建');
      }
      setDrawerOpen(false);
    });
  };

  const getRelatedObjectOptions = (objectType: TermRelatedObjectType) => {
    switch (objectType) {
      case 'METRIC': return metrics.map((m) => ({ value: m.id, label: `${m.metricName} (${m.metricCode})`, code: m.metricCode, name: m.metricName }));
      case 'ENTITY': return entities.map((e) => ({ value: e.id, label: `${e.entityName} (${e.entityCode})`, code: e.entityCode, name: e.entityName }));
      case 'EVENT': return events.map((e) => ({ value: e.id, label: `${e.eventName} (${e.eventCode})`, code: e.eventCode, name: e.eventName }));
      case 'TERM': return terms.filter((t) => t.id !== editing?.id).map((t) => ({ value: t.id, label: `${t.termName} (${t.termCode})`, code: t.termCode, name: t.termName }));
      case 'ALIAS': return aliases.map((a) => ({ value: a.id, label: `${a.aliasText} → ${a.targetName}`, code: a.targetCode, name: a.aliasText }));
    }
  };

  const columns = [
    {
      title: '术语名称', dataIndex: 'termName', key: 'name', width: 160,
      render: (v: string, r: SemanticTerm) => (
        <Space>
          <strong>{v}</strong>
          {!r.participateInExplanation && <Tag>不展示</Tag>}
        </Space>
      ),
    },
    { title: '编码', dataIndex: 'termCode', key: 'code', width: 140, render: (v: string) => <code>{v}</code> },
    {
      title: '标签', dataIndex: 'tags', key: 'tags', width: 180,
      render: (tags: string[]) => tags.map((t) => <Tag key={t}>{t}</Tag>),
    },
    { title: '责任人', dataIndex: 'owner', key: 'owner', width: 80 },
    { title: '业务解释', dataIndex: 'businessDefinition', key: 'def', ellipsis: true },
    {
      title: '关联', key: 'related', width: 80, align: 'center' as const,
      render: (_: unknown, r: SemanticTerm) => (
        <Space size={4}>
          {r.relatedObjects.length > 0 ? (
            <Text type="secondary"><LinkOutlined /> {r.relatedObjects.length}</Text>
          ) : <Text type="secondary">—</Text>}
        </Space>
      ),
    },
    {
      title: '操作', key: 'action', width: 160,
      render: (_: unknown, r: SemanticTerm) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => { setPreviewTerm(r); setPreviewOpen(true); }}>预览</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(r)} />
          <Popconfirm title="确认删除该术语？" onConfirm={() => { deleteTerm(r.id); message.success('已删除'); }}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card title="术语知识" extra={<Text type="secondary">业务知识层：定义、串联、注解</Text>} style={{ height: '100%' }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleNew}>新建术语</Button>
        <Input.Search placeholder="搜索术语名称、编码、定义" allowClear style={{ width: 280 }} onSearch={setSearch} onChange={(e) => !e.target.value && setSearch('')} />
        <Select style={{ width: 160 }} value={filterTag} onChange={setFilterTag} placeholder="按标签筛选" allowClear
          options={[
            { value: '', label: '全部标签' },
            ...ALL_TAGS.filter((t) => tagCounts[t]).map((t) => ({ value: t, label: `${t} (${tagCounts[t]})` })),
          ]} />
      </Space>

      <Table dataSource={filtered} columns={columns} rowKey="id" size="small" pagination={false} />

      {/* 新建/编辑 Drawer */}
      <Drawer
        title={editing ? `编辑术语：${editing.termName}` : '新建术语'}
        width={640} open={drawerOpen} onClose={() => setDrawerOpen(false)}
        extra={<Space><Button onClick={() => setDrawerOpen(false)}>取消</Button><Button type="primary" onClick={handleSave}>保存</Button></Space>}
      >
        <Form form={form} layout="vertical">
          {/* ── 基本信息 ── */}
          <Divider><FileTextOutlined /> 基本信息</Divider>

          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item name="termName" label="术语名称" rules={[{ required: true, message: '请输入术语名称' }]} style={{ flex: 1 }}>
              <Input placeholder="如：GMV、高价值用户" />
            </Form.Item>
            <Form.Item name="termCode" label="术语编码" rules={[{ required: true, message: '请输入术语编码' }]} extra="空间内唯一" style={{ flex: 1 }}>
              <Input placeholder="如：gmv_term" disabled={!!editing} />
            </Form.Item>
          </div>

          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item name="tags" label="标签" style={{ flex: 1 }}>
              <Select mode="tags" placeholder="输入或选择标签" options={ALL_TAGS.map((t) => ({ value: t, label: t }))} />
            </Form.Item>
            <Form.Item name="owner" label="责任人" style={{ flex: 1 }}>
              <Input placeholder="如：张三" />
            </Form.Item>
          </div>

          <Form.Item name="businessDefinition" label="业务定义" rules={[{ required: true, message: '请输入业务定义' }]} extra={'面向业务人员的完整解释，回答"这个概念是什么"'}>
            <Input.TextArea rows={3} placeholder="如：GMV（Gross Merchandise Volume）为已支付订单的成交金额总和，不扣除退款和取消订单的金额。" />
          </Form.Item>

          <Form.Item name="participateInExplanation" label="问数解释面板展示" valuePropName="checked" extra="开启后，用户问数命中该术语时会在回答中展示业务定义">
            <Switch />
          </Form.Item>

          {/* ── 关联对象 ── */}
          <Divider><LinkOutlined /> 关联对象 <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>（只读引用，不重复定义技术实现）</Text></Divider>

          <Form.List name="relatedObjects">
            {(fields, { add, remove }) => (
              <>
                {fields.length === 0 && (
                  <Empty description="暂无关联对象" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ margin: '8px 0 16px' }} />
                )}
                {fields.map((field) => (
                  <Card key={field.key} size="small" style={{ marginBottom: 8, background: '#fafafa' }}
                    extra={<Button type="text" danger size="small" onClick={() => remove(field.name)}>移除</Button>}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'start' }}>
                      <Form.Item {...field} name={[field.name, 'objectType']} noStyle>
                        <ObjectTypeSelect onChange={() => {
                          const list = form.getFieldValue('relatedObjects') as TermRelatedObject[];
                          if (list[field.name]) { list[field.name].objectId = undefined as unknown as number; list[field.name].objectCode = ''; list[field.name].objectName = ''; form.setFieldValue('relatedObjects', list); }
                        }} />
                      </Form.Item>
                      <Form.Item {...field} name={[field.name, 'objectId']} noStyle style={{ flex: 1 }}>
                        <ObjectIdSelect
                          objectType={form.getFieldValue(['relatedObjects', field.name, 'objectType'])}
                          getOptions={getRelatedObjectOptions}
                          onChange={(id: number) => {
                            const list = form.getFieldValue('relatedObjects') as TermRelatedObject[];
                            const ot = list[field.name]?.objectType;
                            if (ot) {
                              const opt = getRelatedObjectOptions(ot).find((o) => o.value === id);
                              if (opt) { list[field.name].objectCode = opt.code; list[field.name].objectName = opt.name; form.setFieldValue('relatedObjects', list); }
                            }
                          }}
                        />
                      </Form.Item>
                      <Form.Item {...field} name={[field.name, 'relationType']} noStyle initialValue="DEFINES">
                        <Select style={{ width: 120 }} options={Object.entries(relationLabels).map(([v, l]) => ({ value: v, label: l }))} />
                      </Form.Item>
                    </div>
                  </Card>
                ))}
                <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ objectType: 'METRIC', relationType: 'DEFINES', objectCode: '', objectName: '' })} style={{ marginBottom: 8 }}>
                  添加关联
                </Button>
              </>
            )}
          </Form.List>

          {/* ── 使用上下文 ── */}
          <Divider><InfoCircleOutlined /> 使用上下文</Divider>

          <Form.Item name="usageContext" label="常见使用场景">
            <Input placeholder="如：日报、月度经营分析会、财务报表" />
          </Form.Item>

          <Form.Item name="notes" label="注意事项" extra="与相关概念的区分说明、口径变更历史等">
            <Input.TextArea rows={2} placeholder={'如：与财务"营收"口径不同，营收包含退款冲减后的净额'} />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 问数解释面板预览 */}
      <Drawer title="问数解释面板预览" width={480} open={previewOpen} onClose={() => setPreviewOpen(false)}>
        {previewTerm && (
          <>
            <div style={{ marginBottom: 16, padding: 16, background: '#f6f8fa', borderRadius: 8, border: '1px solid #e8e8e8' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>模拟用户提问</Text>
              <div style={{ marginTop: 4, fontSize: 15 }}>今年{previewTerm.termName}是多少？</div>
            </div>

            <Card size="small" title={<Space><InfoCircleOutlined style={{ color: '#1677ff' }} /> 术语解释</Space>} style={{ marginBottom: 16, borderColor: '#d6e4ff' }}>
              <div style={{ marginBottom: 12 }}>
                <Text strong style={{ fontSize: 15 }}>{previewTerm.termName}</Text>
                <Space style={{ marginLeft: 8 }}>
                  {previewTerm.tags.map((t) => <Tag key={t}>{t}</Tag>)}
                </Space>
              </div>
              <div style={{ color: '#595959', lineHeight: 1.8, marginBottom: 12 }}>{previewTerm.businessDefinition}</div>
              {previewTerm.notes && (
                <div style={{ padding: '8px 12px', background: '#fffbe6', borderRadius: 4, fontSize: 13, color: '#8c6d1f' }}>
                  <strong>注意：</strong>{previewTerm.notes}
                </div>
              )}
            </Card>

            {previewTerm.relatedObjects.length > 0 && (
              <Card size="small" title="关联对象">
                {previewTerm.relatedObjects.map((ro, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0' }}>
                    <Tag color={objectTypeColors[ro.objectType]}>{objectTypeLabels[ro.objectType]}</Tag>
                    <Text>{ro.objectName}</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>({ro.objectCode})</Text>
                    <Text type="secondary" style={{ marginLeft: 'auto', fontSize: 12 }}>{relationLabels[ro.relationType]}</Text>
                  </div>
                ))}
              </Card>
            )}

            <Divider />
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label="术语编码">{previewTerm.termCode}</Descriptions.Item>
              <Descriptions.Item label="责任人">{previewTerm.owner || '—'}</Descriptions.Item>
              <Descriptions.Item label="使用场景">{previewTerm.usageContext || '—'}</Descriptions.Item>
              <Descriptions.Item label="解释面板">{previewTerm.participateInExplanation ? <Tag color="green">展示</Tag> : <Tag>不展示</Tag>}</Descriptions.Item>
            </Descriptions>
          </>
        )}
      </Drawer>
    </Card>
  );
}

/* ── 子组件：对象类型选择器 ── */
function ObjectTypeSelect({ value, onChange }: { value?: TermRelatedObjectType; onChange?: (v: TermRelatedObjectType) => void }) {
  return (
    <Select style={{ width: 110 }} value={value} onChange={onChange}
      options={Object.entries(objectTypeLabels).map(([v, l]) => ({ value: v, label: l }))} />
  );
}

/* ── 子组件：对象 ID 选择器（类型驱动） ── */
function ObjectIdSelect({
  objectType, value, getOptions, onChange,
}: {
  objectType: TermRelatedObjectType;
  value?: number;
  getOptions: (t: TermRelatedObjectType) => { value: number; label: string; code: string; name: string }[];
  onChange?: (v: number) => void;
}) {
  if (!objectType) return <Select style={{ width: 0, flex: 1, minWidth: 0 }} disabled placeholder="先选类型" />;
  return (
    <Select style={{ width: 0, flex: 1, minWidth: 0 }} value={value} onChange={onChange} showSearch optionFilterProp="label"
      placeholder={`选择${objectTypeLabels[objectType]}`} options={getOptions(objectType)} />
  );
}
