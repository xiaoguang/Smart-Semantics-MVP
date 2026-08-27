import { useState, useMemo } from 'react';
import { Button, Space, Select, Table, Drawer, Form, InputNumber, DatePicker, Tag, Popconfirm, message, Typography, Divider } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useStore } from '../../store/useStore';
import type { MetricTarget, TargetPeriod, TargetValueType, TargetStatus, CaliberCondition, CaliberOperator } from '../../types';

const { Text } = Typography;

const periodLabels: Record<TargetPeriod, string> = { YEAR: '年', QUARTER: '季度', MONTH: '月', WEEK: '周', DAY: '日' };
const valueTypeLabels: Record<TargetValueType, string> = { ABSOLUTE: '绝对值', GROWTH: '增长率', RATIO: '比例' };
const statusLabels: Record<TargetStatus, string> = { DRAFT: '草稿', ACTIVE: '生效', INACTIVE: '停用' };
const statusColors: Record<TargetStatus, string> = { DRAFT: 'default', ACTIVE: 'green', INACTIVE: 'red' };
const caliberOps: CaliberOperator[] = ['=', '!=', '>', '>=', '<', '<=', 'IN', 'NOT_IN'];

export default function MetricTargetTab() {
  const { metrics, metricTargets, addMetricTarget, updateMetricTarget, deleteMetricTarget, validateMetricTarget } = useStore();
  const [filterMetric, setFilterMetric] = useState<number | ''>('');
  const [filterPeriod, setFilterPeriod] = useState<TargetPeriod | ''>('');
  const [filterStatus, setFilterStatus] = useState<TargetStatus | ''>('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MetricTarget | null>(null);
  const [form] = Form.useForm();
  const valueType = Form.useWatch('valueType', form);

  const filtered = useMemo(() => {
    let list = metricTargets;
    if (filterMetric) list = list.filter((t) => t.metricId === filterMetric);
    if (filterPeriod) list = list.filter((t) => t.period === filterPeriod);
    if (filterStatus) list = list.filter((t) => t.status === filterStatus);
    return list;
  }, [metricTargets, filterMetric, filterPeriod, filterStatus]);

  const handleNew = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ valueType: 'ABSOLUTE', period: 'MONTH', status: 'DRAFT', targetValue: 10000, effectiveStart: dayjs(), effectiveEnd: dayjs().add(1, 'month'), dimLogic: 'AND' });
    setOpen(true);
  };
  const handleEdit = (t: MetricTarget) => {
    setEditing(t);
    form.resetFields();
    form.setFieldsValue({
      ...t,
      effectiveStart: dayjs(t.effectiveStart), effectiveEnd: dayjs(t.effectiveEnd),
      dimLogic: t.dimensionFilter?.logic || 'AND',
      dimAttr: t.dimensionFilter?.conditions[0]?.attributeId,
      dimOp: t.dimensionFilter?.conditions[0]?.op || '=',
      dimValues: t.dimensionFilter?.conditions[0]?.values || [],
    });
    setOpen(true);
  };

  const handleSave = () => {
    form.validateFields().then((values) => {
      const metric = metrics.find((m) => m.id === values.metricId);
      let dimensionFilter: { logic: 'AND' | 'OR'; conditions: CaliberCondition[] } | undefined;
      if (values.dimAttr) {
        const attr = resolveAttr(values.metricId, values.dimAttr);
        dimensionFilter = {
          logic: values.dimLogic || 'AND',
          conditions: [{ attributeId: values.dimAttr, attributeCode: attr?.attributeCode || '', attributeName: attr?.attributeName, op: values.dimOp, values: values.dimValues || [] }],
        };
      }
      const payload: Omit<MetricTarget, 'id'> = {
        metricId: values.metricId, metricCode: metric?.metricCode, metricName: metric?.metricName,
        valueType: values.valueType, period: values.period, targetValue: values.targetValue,
        dimensionFilter, dimensionLabel: dimensionFilter ? `${dimensionFilter.conditions[0]?.attributeName || dimensionFilter.conditions[0]?.attributeCode} ${dimensionFilter.conditions[0]?.op} (${(dimensionFilter.conditions[0]?.values || []).join(',')})` : undefined,
        effectiveStart: values.effectiveStart.format('YYYY-MM-DD'), effectiveEnd: values.effectiveEnd.format('YYYY-MM-DD'),
        status: values.status,
      };
      const issues = validateMetricTarget(payload, editing?.id);
      if (issues.some((i) => i.severity === 'BLOCKER')) { message.error(issues.find((i) => i.severity === 'BLOCKER')!.message); return; }
      if (issues.some((i) => i.severity === 'WARNING')) message.warning(issues.find((i) => i.severity === 'WARNING')!.message);
      if (editing) { updateMetricTarget(editing.id, payload); message.success('目标已更新'); }
      else { addMetricTarget(payload); message.success('目标已创建'); }
      setOpen(false);
    });
  };

  const resolveAttr = (metricId: number, attrId: number) => {
    const metric = metrics.find((m) => m.id === metricId);
    const event = useStore.getState().events.find((e) => e.id === metric?.eventId);
    return event?.attributes.find((a) => a.id === attrId);
  };

  const dimAttrOptions = useMemo(() => {
    const mid = form.getFieldValue('metricId');
    const metric = metrics.find((m) => m.id === mid);
    const event = useStore.getState().events.find((e) => e.id === metric?.eventId);
    return event ? event.attributes.filter((a) => a.isFilterable).map((a) => ({ value: a.id, label: `${a.attributeName} (${a.attributeCode})` })) : [];
  }, [metrics, form]);

  const columns = [
    { title: '指标', key: 'metric', render: (_: unknown, t: MetricTarget) => <Space direction="vertical" size={0}><strong>{t.metricName}</strong><Text type="secondary" style={{ fontSize: 12 }}>{t.metricCode}</Text></Space> },
    { title: '类型', dataIndex: 'valueType', key: 'vt', render: (v: TargetValueType) => <Tag>{valueTypeLabels[v]}</Tag> },
    { title: '周期', dataIndex: 'period', key: 'p', render: (v: TargetPeriod) => periodLabels[v] },
    { title: '目标值', dataIndex: 'targetValue', key: 'tv', render: (v: number) => <strong>{v}</strong> },
    { title: '维度限定', key: 'dim', render: (_: unknown, t: MetricTarget) => t.dimensionLabel ? <Tag color="blue">{t.dimensionLabel}</Tag> : <Text type="secondary">全量</Text> },
    { title: '有效期', key: 'eff', render: (_: unknown, t: MetricTarget) => <Text type="secondary" style={{ fontSize: 12 }}>{t.effectiveStart} ~ {t.effectiveEnd}</Text> },
    { title: '状态', dataIndex: 'status', key: 's', render: (v: TargetStatus) => <Tag color={statusColors[v]}>{statusLabels[v]}</Tag> },
    { title: '操作', key: 'a', width: 120, render: (_: unknown, t: MetricTarget) => <Space>
      <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(t)} />
      <Popconfirm title="确认删除？" onConfirm={() => { deleteMetricTarget(t.id); message.success('已删除'); }}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm>
    </Space> },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }} wrap>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleNew}>新建目标</Button>
        <Select style={{ width: 180 }} value={filterMetric} onChange={setFilterMetric} placeholder="按指标筛选" allowClear
          options={[{ value: '', label: '全部指标' }, ...metrics.map((m) => ({ value: m.id, label: `${m.metricName} (${m.metricCode})` }))]} />
        <Select style={{ width: 120 }} value={filterPeriod} onChange={setFilterPeriod} placeholder="按周期"
          options={[{ value: '', label: '全部周期' }, ...Object.entries(periodLabels).map(([v, l]) => ({ value: v, label: l }))]} />
        <Select style={{ width: 120 }} value={filterStatus} onChange={setFilterStatus} placeholder="按状态"
          options={[{ value: '', label: '全部状态' }, ...Object.entries(statusLabels).map(([v, l]) => ({ value: v, label: l }))]} />
      </Space>
      <Table dataSource={filtered} columns={columns} rowKey="id" size="small" pagination={false} />

      <Drawer title={editing ? '编辑指标目标' : '新建指标目标'} width={460} open={open} onClose={() => setOpen(false)}
        extra={<Space><Button onClick={() => setOpen(false)}>取消</Button><Button type="primary" onClick={handleSave}>保存</Button></Space>}>
        <Form form={form} layout="vertical">
          <Form.Item name="metricId" label="指标" rules={[{ required: true }]} extra="只能选择已发布指标">
            <Select showSearch optionFilterProp="label" disabled={!!editing}
              options={metrics.filter((m) => m.status === 'PUBLISHED').map((m) => ({ value: m.id, label: `${m.metricName} (${m.metricCode})` }))} />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item name="valueType" label="目标类型" rules={[{ required: true }]}>
              <Select options={Object.entries(valueTypeLabels).map(([v, l]) => ({ value: v, label: l }))} />
            </Form.Item>
            <Form.Item name="period" label="周期" rules={[{ required: true }]}>
              <Select options={Object.entries(periodLabels).map(([v, l]) => ({ value: v, label: l }))} />
            </Form.Item>
          </div>
          <Form.Item name="targetValue" label="目标值" rules={[{ required: true }]} extra={valueType === 'GROWTH' ? '增长率，如 0.2 表示 20%' : undefined}>
            <InputNumber style={{ width: '100%' }} step={valueType === 'GROWTH' ? 0.01 : 100} />
          </Form.Item>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item name="effectiveStart" label="生效起" rules={[{ required: true }]}><DatePicker style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="effectiveEnd" label="生效止" rules={[{ required: true }]}><DatePicker style={{ width: '100%' }} /></Form.Item>
          </div>
          <Form.Item name="status" label="状态"><Select options={Object.entries(statusLabels).map(([v, l]) => ({ value: v, label: l }))} /></Form.Item>

          <Divider>维度限定（可选）</Divider>
          <div style={{ display: 'flex', gap: 8 }}>
            <Form.Item name="dimLogic" noStyle><Select style={{ width: 90 }} options={[{ value: 'AND', label: 'AND' }, { value: 'OR', label: 'OR' }]} /></Form.Item>
            <Form.Item name="dimAttr" noStyle style={{ flex: 1 }}><Select style={{ width: '100%' }} allowClear placeholder="选择限定属性" options={dimAttrOptions} /></Form.Item>
            <Form.Item name="dimOp" noStyle><Select style={{ width: 90 }} options={caliberOps.map((o) => ({ value: o, label: o }))} /></Form.Item>
          </div>
          <Form.Item name="dimValues" label="限定取值" extra="多个值用回车分隔"><Select mode="tags" placeholder="输入值后回车" /></Form.Item>
        </Form>
      </Drawer>
    </>
  );
}
