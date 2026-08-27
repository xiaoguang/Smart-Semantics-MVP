import { useMemo, useState, useEffect } from 'react';
import {
  Card, Tabs, List, Input, Button, Space, Tag, Badge, Empty, Drawer, Form, Select, InputNumber,
  Descriptions, message, Popconfirm, Alert, Typography, Divider, Table,
} from 'antd';
import {
  BarChartOutlined, PlusOutlined, EditOutlined, DeleteOutlined, SaveOutlined, WarningOutlined,
  ThunderboltOutlined, CheckCircleOutlined, CloseCircleOutlined, NodeIndexOutlined,
} from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { SemanticMetric, MetricType, MetricStatus, ObjectAttribute, ValidationIssue, FormulaToken, CaliberCondition, CaliberOperator, BusinessRule, RuleStatus, MetricPreviewResult } from '../../types';
import BusinessRuleDrawer from '../../components/rule/BusinessRuleDrawer';
import { presentValidationIssue } from '../../features/data-standardization/business-validation-presentation';

const { Text } = Typography;

type DetailTab = 'basic' | 'calc' | 'caliber' | 'rules' | 'preview';

const typeLabels: Record<MetricType, string> = { BASIC: '基础指标', COMPOSITE: '复合指标' };
const typeColors: Record<MetricType, string> = { BASIC: 'blue', COMPOSITE: 'purple' };
const statusLabels: Record<MetricStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', INACTIVE: '已停用' };
const statusColors: Record<MetricStatus, string> = { DRAFT: 'default', PUBLISHED: 'green', INACTIVE: 'red' };
const allOps = ['SUM', 'AVG', 'MAX', 'MIN', 'COUNT', 'COUNT_DISTINCT'];
const caliberOps: { value: CaliberOperator; label: string; multi?: boolean; noValue?: boolean }[] = [
  { value: '=', label: '=' }, { value: '!=', label: '≠' }, { value: '>', label: '>' }, { value: '>=', label: '≥' },
  { value: '<', label: '<' }, { value: '<=', label: '≤' }, { value: 'IN', label: 'IN', multi: true },
  { value: 'NOT_IN', label: 'NOT IN', multi: true }, { value: 'IS_NULL', label: '为空', noValue: true }, { value: 'NOT_NULL', label: '不为空', noValue: true },
];

function issueSummary(issues: ValidationIssue[]) {
  return { blockers: issues.filter((x) => x.severity === 'BLOCKER').length, warnings: issues.filter((x) => x.severity === 'WARNING').length };
}
function tabOfIssue(code: string): DetailTab {
  if (code.includes('FORMULA') || code.includes('BASE_ATTRIBUTE') || code.includes('OPERATOR') || code.includes('EMPTY_FORMULA') || code.includes('INVALID_FORMULA')) return 'calc';
  if (code.includes('CALIBER') || code.includes('ATTRIBUTE') || code.includes('FILTER')) return 'caliber';
  if (code.includes('RULE')) return 'rules';
  return 'basic';
}

export default function MetricTab() {
  const { metrics, validateMetric } = useStore();
  const [selectedId, setSelectedId] = useState<number | undefined>(metrics[0]?.id);
  const [detailTab, setDetailTab] = useState<DetailTab>('basic');
  const [search, setSearch] = useState('');
  const [newOpen, setNewOpen] = useState(false);

  const metric = metrics.find((m) => m.id === selectedId);
  const issues = metric ? validateMetric(metric.id) : [];
  const summary = issueSummary(issues);

  const listData = useMemo(() => {
    const q = search.toLowerCase();
    return metrics.filter((m) => !q || m.metricName.toLowerCase().includes(q) || m.metricCode.toLowerCase().includes(q));
  }, [metrics, search]);

  const handleNew = () => setNewOpen(true);

  return (
    <Card title={<Space><BarChartOutlined /> 指标</Space>} extra={<Button type="primary" size="small" icon={<PlusOutlined />} onClick={handleNew}>新建指标</Button>} style={{ height: '100%' }}>
      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16, minHeight: 600 }}>
        <Card size="small" title="指标列表">
          <Input.Search placeholder="搜索名称或编码" allowClear style={{ marginBottom: 12 }} onSearch={setSearch} onChange={(e) => !e.target.value && setSearch('')} />
          <List<SemanticMetric>
            dataSource={listData}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无指标" /> }}
            renderItem={(item) => {
              const selected = item.id === selectedId;
              const rowIssues = validateMetric(item.id);
              const rs = issueSummary(rowIssues);
              return (
                <List.Item onClick={() => { setSelectedId(item.id); setDetailTab('basic'); }} style={{ cursor: 'pointer', padding: '10px 12px', borderRadius: 6, marginBottom: 6, background: selected ? '#e6f4ff' : undefined, border: selected ? '1px solid #91caff' : '1px solid transparent' }}>
                  <List.Item.Meta
                    title={<Space><strong>{item.metricName}</strong><Tag color={typeColors[item.metricType]}>{typeLabels[item.metricType]}</Tag>{rs.blockers > 0 && <Badge count={rs.blockers} size="small" />}</Space>}
                    description={<Space direction="vertical" size={2}>
                      <Text type="secondary" style={{ fontSize: 12 }}>{item.metricCode}</Text>
                      <Space size={4}>
                        <Tag color={statusColors[item.status]}>{statusLabels[item.status]}</Tag>
                        <Text type="secondary" style={{ fontSize: 12 }}>{item.eventName}</Text>
                      </Space>
                    </Space>}
                  />
                </List.Item>
              );
            }}
          />
        </Card>

        {metric ? (
          <Card size="small" title={<Space><BarChartOutlined /> {metric.metricName} <Tag>{metric.metricCode}</Tag></Space>}
          extra={<Space>
            <Button icon={<WarningOutlined />} onClick={() => { if (issues.length === 0) message.success('校验通过'); else message.warning(`${summary.blockers} 阻断 / ${summary.warnings} 警告`); }}>校验</Button>
            <Popconfirm title={`确认${metric.status === 'INACTIVE' ? '启用' : '停用'}该指标？`} onConfirm={() => { useStore.getState().updateMetric(metric.id, { status: metric.status === 'INACTIVE' ? 'DRAFT' : 'INACTIVE' }); message.success('已更新'); }}>
              <Button disabled={metric.status === 'PUBLISHED'}>{metric.status === 'INACTIVE' ? '启用' : '停用'}</Button>
            </Popconfirm>
            <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => {
              const pi = useStore.getState().publishMetric(metric.id);
              if (pi.some((i) => i.severity === 'BLOCKER')) { message.error(pi[0].message); setDetailTab(tabOfIssue(pi[0].checkCode)); }
              else message.success('指标已发布');
            }}>发布</Button>
          </Space>}>
          <Descriptions size="small" column={4} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="类型"><Tag color={typeColors[metric.metricType]}>{typeLabels[metric.metricType]}</Tag></Descriptions.Item>
            <Descriptions.Item label="所属事件">{metric.eventName}</Descriptions.Item>
            <Descriptions.Item label="状态"><Tag color={statusColors[metric.status]}>{statusLabels[metric.status]}</Tag> {metric.version ? <Text type="secondary">v{metric.version}</Text> : null}</Descriptions.Item>
            <Descriptions.Item label="校验">{summary.blockers > 0 ? <Tag color="red">{summary.blockers} 阻断</Tag> : <Tag color="green">通过</Tag>}{summary.warnings > 0 && <Tag color="orange">{summary.warnings} 警告</Tag>}</Descriptions.Item>
          </Descriptions>

          {issues.length > 0 && <IssuePanel issues={issues} onGo={(t) => setDetailTab(t)} />}

          <Tabs activeKey={detailTab} onChange={(k) => setDetailTab(k as DetailTab)} items={[
            { key: 'basic', label: '基本信息', children: <BasicInfoTab key={metric.id} metric={metric} /> },
            { key: 'calc', label: '计算定义', children: <CalcTab key={metric.id} metric={metric} /> },
            { key: 'caliber', label: '口径', children: metric.metricType === 'BASIC' ? <CaliberTab key={metric.id} metric={metric} /> : <Empty description="复合指标无口径配置" /> },
            { key: 'rules', label: '关联规则', children: <RelatedRulesTab metric={metric} /> },
            { key: 'preview', label: '预览与发布', children: <PreviewTab key={metric.id} metric={metric} onJump={setDetailTab} /> },
          ]} />
        </Card>
        ) : <Card><Empty description="请选择或新建指标" /></Card>}
      </div>

      <NewMetricDrawer open={newOpen} onClose={() => setNewOpen(false)} onCreated={(id) => { setSelectedId(id); setDetailTab('basic'); }} />
    </Card>
  );
}

function IssuePanel({ issues, onGo }: { issues: ValidationIssue[]; onGo: (t: DetailTab) => void }) {
  return (
    <Alert type={issues.some((x) => x.severity === 'BLOCKER') ? 'error' : 'warning'} showIcon style={{ marginBottom: 12 }}
      message="当前指标存在校验问题"
      description={<Space direction="vertical" size={4}>{issues.map((issue) => {
        const presentation = presentValidationIssue(issue);
        return <Space key={`${issue.checkCode}-${issue.message}`}><Tag color={presentation.tone === 'error' ? 'red' : 'orange'}>{presentation.label}</Tag><span>{presentation.message}</span><Button type="link" size="small" onClick={() => onGo(tabOfIssue(issue.checkCode))}>去配置</Button></Space>;
      })}</Space>} />
  );
}

function NewMetricDrawer({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (id: number) => void }) {
  const { events, addMetric } = useStore();
  const [form] = Form.useForm();
  const type = Form.useWatch('metricType', form);
  useEffect(() => { if (open) { form.resetFields(); form.setFieldsValue({ metricType: 'BASIC' }); } }, [open, form]);
  const save = () => {
    form.validateFields().then((values) => {
      const event = events.find((e) => e.id === values.eventId);
      const payload: Omit<SemanticMetric, 'id'> = {
        metricCode: values.metricCode, metricName: values.metricName, metricType: values.metricType,
        description: values.description, unit: values.unit, owner: values.owner, version: 1, status: 'DRAFT',
        eventId: values.eventId, eventCode: event?.eventCode || '', eventName: event?.eventName || '',
        ruleIds: [],
      };
      const issues = addMetric(payload);
      if (issues.some((i) => i.severity === 'BLOCKER')) { message.error(issues[0].message); return; }
      const created = useStore.getState().metrics.find((m) => m.metricCode === values.metricCode);
      message.success('指标已创建，请继续配置计算定义');
      if (created) onCreated(created.id);
      onClose();
    });
  };
  return (
    <Drawer title="新建指标" width={520} open={open} onClose={onClose} extra={<Space><Button onClick={onClose}>取消</Button><Button type="primary" onClick={save}>创建</Button></Space>}>
      <Alert type="info" showIcon style={{ marginBottom: 16 }} message="先选择指标类型，类型确认后不可直接切换" />
      <Form form={form} layout="vertical">
        <Form.Item name="metricType" label="指标类型" rules={[{ required: true }]}>
          <Select options={[{ value: 'BASIC', label: '基础指标 - 基于事件度量属性聚合' }, { value: 'COMPOSITE', label: '复合指标 - 基于已发布指标公式计算' }]} />
        </Form.Item>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item name="metricName" label="指标名称" rules={[{ required: true }]}><Input placeholder="如：有效销售额" /></Form.Item>
          <Form.Item name="metricCode" label="指标编码" rules={[{ required: true }]} extra="空间内唯一"><Input placeholder="如：valid_sales" /></Form.Item>
        </div>
        {type === 'BASIC' && (
          <Form.Item name="eventId" label="所属事件" rules={[{ required: true }]} extra="只能选择已发布事件">
            <Select showSearch optionFilterProp="label" options={events.map((e) => ({ value: e.id, label: `${e.eventName} (${e.eventCode})` }))} />
          </Form.Item>
        )}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item name="unit" label="单位"><Input placeholder="如：CNY、笔、人" /></Form.Item>
          <Form.Item name="owner" label="责任人"><Input /></Form.Item>
        </div>
        <Form.Item name="description" label="描述"><Input.TextArea rows={2} /></Form.Item>
      </Form>
    </Drawer>
  );
}

function BasicInfoTab({ metric }: { metric: SemanticMetric }) {
  const { events, updateMetric } = useStore();
  const [form] = Form.useForm();
  useEffect(() => { form.setFieldsValue(metric); }, [metric, form]);
  const save = () => {
    form.validateFields().then((values) => {
      const event = events.find((e) => e.id === values.eventId);
      updateMetric(metric.id, { ...values, eventCode: event?.eventCode || '', eventName: event?.eventName || '' });
      message.success('基本信息已保存');
    });
  };
  return (
    <Form form={form} layout="vertical">
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
        <Form.Item name="metricName" label="指标名称" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="metricCode" label="指标编码" rules={[{ required: true }]}><Input disabled /></Form.Item>
        <Form.Item name="metricType" label="指标类型"><Select disabled options={(Object.entries(typeLabels) as [MetricType, string][]).map(([v, l]) => ({ value: v, label: l }))} /></Form.Item>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
        <Form.Item name="unit" label="单位"><Input /></Form.Item>
        <Form.Item name="owner" label="责任人"><Input /></Form.Item>
        {metric.metricType === 'BASIC' && (
          <Form.Item name="eventId" label="所属事件" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={events.map((e) => ({ value: e.id, label: `${e.eventName} (${e.eventCode})` }))} /></Form.Item>
        )}
      </div>
      <Form.Item name="description" label="描述"><Input.TextArea rows={2} /></Form.Item>
      <Button type="primary" icon={<SaveOutlined />} onClick={save}>保存基本信息</Button>
    </Form>
  );
}

function CalcTab({ metric }: { metric: SemanticMetric }) {
  if (metric.metricType === 'COMPOSITE') return <CompositeCalc metric={metric} />;
  return <BasicCalc metric={metric} />;
}

function BasicCalc({ metric }: { metric: SemanticMetric }) {
  const { events, updateMetric } = useStore();
  const event = events.find((e) => e.id === metric.eventId);
  const metricAttrs = event ? event.attributes.filter((a) => a.isMetric) : [];
  const [form] = Form.useForm();
  const baseAttrId = Form.useWatch('baseAttributeId', form);
  useEffect(() => { form.setFieldsValue({ baseAttributeId: metric.baseAttributeId, defaultOperator: metric.defaultOperator }); }, [metric, form]);
  const selectedAttr = event?.attributes.find((a) => a.id === baseAttrId);
  const allowedOps = selectedAttr && (selectedAttr.dataType === 'STRING' || ['ID', 'FOREIGN_KEY', 'CODE'].includes(selectedAttr.semanticType)) ? ['COUNT', 'COUNT_DISTINCT'] : allOps;
  const save = () => {
    form.validateFields().then((values) => {
      const attr = event?.attributes.find((a) => a.id === values.baseAttributeId);
      updateMetric(metric.id, { baseAttributeId: values.baseAttributeId, baseAttributeCode: attr?.attributeCode, defaultOperator: values.defaultOperator });
      message.success('计算定义已保存');
    });
  };
  return (
    <Form form={form} layout="vertical">
      <Form.Item name="baseAttributeId" label="基础属性" rules={[{ required: true }]} extra="仅显示当前事件标记为「生成指标」的属性">
        <Select showSearch optionFilterProp="label" options={metricAttrs.map((a) => ({ value: a.id, label: `${a.attributeName} (${a.attributeCode}) · ${a.dataType}` }))} />
      </Form.Item>
      <Form.Item name="defaultOperator" label="默认算子" rules={[{ required: true }]} extra="$sum/$count/$avg 受属性类型限制">
        <Select options={allowedOps.map((o) => ({ value: o, label: o }))} />
      </Form.Item>
      <Button type="primary" icon={<SaveOutlined />} onClick={save}>保存计算定义</Button>
    </Form>
  );
}

function CompositeCalc({ metric }: { metric: SemanticMetric }) {
  const { metrics, updateMetric } = useStore();
  const available = metrics.filter((m) => m.status === 'PUBLISHED' && m.id !== metric.id);
  const [tokens, setTokens] = useState<FormulaToken[]>(metric.formulaTokens || []);
  useEffect(() => { setTokens(metric.formulaTokens || []); }, [metric.id, metric.formulaTokens]);

  const pushToken = (t: FormulaToken) => setTokens((x) => [...x, t]);
  const removeAt = (idx: number) => setTokens((x) => x.filter((_, i) => i !== idx));
  const referenced = tokens.filter((t) => t.type === 'METRIC').map((t) => metrics.find((m) => m.metricCode === t.metricCode)).filter(Boolean) as SemanticMetric[];

  const save = () => {
    if (tokens.filter((t) => t.type === 'METRIC').length === 0) { message.error('公式必须至少引用一个已发布指标'); return; }
    const formula = tokens.map((t) => t.value).join(' ');
    updateMetric(metric.id, { formula, formulaTokens: tokens });
    message.success('计算定义已保存');
  };

  return (
    <div>
      <Alert type="info" showIcon style={{ marginBottom: 16 }} message="点击左侧可用指标插入 token，再用运算符编排；只能插入已发布指标" />
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Card size="small" title="可用指标" style={{ maxHeight: 220, overflow: 'auto' }}>
          {available.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /> : available.map((m) => (
            <div key={m.id} style={{ padding: '6px 8px', cursor: 'pointer', borderRadius: 4 }} onClick={() => pushToken({ type: 'METRIC', value: m.metricCode, metricId: m.id, metricCode: m.metricCode })}
              onMouseEnter={(e) => (e.currentTarget.style.background = '#f0f5ff')} onMouseLeave={(e) => (e.currentTarget.style.background = '')}>
              <Tag color="blue">指标</Tag><Text>{m.metricName}</Text><Text type="secondary" style={{ fontSize: 12 }}> ({m.metricCode}){m.unit ? ` · ${m.unit}` : ''}</Text>
            </div>
          ))}
        </Card>
        <Card size="small" title="运算符">
          <Space wrap>
            {['+', '-', '*', '/', '(', ')'].map((op) => (
              <Button key={op} onClick={() => pushToken({ type: op === '(' ? 'LPAREN' : op === ')' ? 'RPAREN' : 'OP', value: op })}>{op}</Button>
            ))}
            <Button danger onClick={() => setTokens([])}>清空</Button>
          </Space>
        </Card>
      </div>
      <Divider>公式</Divider>
      <div style={{ minHeight: 56, padding: 12, border: '1px dashed #d9d9d9', borderRadius: 6, background: '#fafafa', marginBottom: 12 }}>
        {tokens.length === 0 ? <Text type="secondary">点击上方指标与运算符构建公式</Text> : (
          <Space wrap size={6}>
            {tokens.map((t, idx) => (
              <Tag key={idx} color={t.type === 'METRIC' ? 'blue' : t.type === 'NUM' ? 'gold' : 'default'} closable onClose={() => removeAt(idx)} style={{ fontFamily: 'monospace' }}>
                {t.type === 'METRIC' ? `[${t.value}]` : t.value}
              </Tag>
            ))}
          </Space>
        )}
      </div>
      <Card size="small" title="依赖与提示" style={{ marginBottom: 12 }}>
        {referenced.length === 0 ? <Text type="secondary">暂无依赖</Text> : (
          <Space direction="vertical" size={2}>
            {referenced.map((m) => <div key={m.id}><Tag color="blue">{m.metricCode}</Tag><Text>{m.metricName}</Text>{m.unit ? <Text type="secondary"> · 单位 {m.unit}</Text> : null}</div>)}
          </Space>
        )}
        {referenced.length > 1 && new Set(referenced.map((m) => m.unit).filter(Boolean)).size > 1 && (
          <Alert type="warning" showIcon style={{ marginTop: 8 }} message="引用指标单位不一致，请确认计算结果单位" />
        )}
      </Card>
      <Button type="primary" icon={<SaveOutlined />} onClick={save}>保存计算定义</Button>
    </div>
  );
}

function CaliberTab({ metric }: { metric: SemanticMetric }) {
  const { events, updateMetric } = useStore();
  const event = events.find((e) => e.id === metric.eventId);
  const filterableAttrs = event ? event.attributes.filter((a) => a.isFilterable) : [];
  const [logic, setLogic] = useState<'AND' | 'OR'>(metric.caliberFilter?.logic || 'AND');
  const [conditions, setConditions] = useState<CaliberCondition[]>(metric.caliberFilter?.conditions?.map((c) => ({ ...c })) || []);
  useEffect(() => { setLogic(metric.caliberFilter?.logic || 'AND'); setConditions(metric.caliberFilter?.conditions?.map((c) => ({ ...c })) || []); }, [metric.id, metric.caliberFilter]);

  const setCond = (idx: number, patch: Partial<CaliberCondition>) => setConditions((cs) => cs.map((c, i) => i === idx ? { ...c, ...patch } : c));
  const add = () => setConditions((cs) => [...cs, { attributeId: 0, attributeCode: '', op: '=', values: [] }]);
  const remove = (idx: number) => setConditions((cs) => cs.filter((_, i) => i !== idx));

  const save = () => {
    updateMetric(metric.id, { caliberFilter: { logic, conditions } });
    message.success('口径已保存');
  };

  return (
    <div>
      <Alert type="info" showIcon style={{ marginBottom: 16 }} message="口径为结构化条件，不保存物理字段；条件属性只显示当前事件的可筛选属性" />
      <Space style={{ marginBottom: 12 }}>
        <Text>条件组合逻辑：</Text>
        <Select value={logic} onChange={setLogic} style={{ width: 120 }} options={[{ value: 'AND', label: 'AND 全部满足' }, { value: 'OR', label: 'OR 任一满足' }]} />
        <Button type="primary" icon={<PlusOutlined />} onClick={add} disabled={filterableAttrs.length === 0}>添加条件</Button>
      </Space>
      {conditions.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无条件，表示全量口径" />}
      {conditions.map((c, idx) => {
        const attr = filterableAttrs.find((a) => a.id === c.attributeId);
        const op = caliberOps.find((o) => o.value === c.op);
        return (
          <Card key={idx} size="small" style={{ marginBottom: 8 }} extra={<Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={() => remove(idx)} />}>
            <div style={{ display: 'grid', gridTemplateColumns: '1.3fr 0.7fr 1.4fr', gap: 8 }}>
              <Select value={c.attributeId || undefined} placeholder="选择属性" showSearch optionFilterProp="label"
                options={filterableAttrs.map((a) => ({ value: a.id, label: `${a.attributeName} (${a.attributeCode})` }))}
                onChange={(v) => { const a = filterableAttrs.find((x) => x.id === v); if (a) setCond(idx, { attributeId: a.id, attributeCode: a.attributeCode, attributeName: a.attributeName, values: [] }); }} />
              <Select value={c.op} onChange={(v) => setCond(idx, { op: v as CaliberOperator })} options={caliberOps.map((o) => ({ value: o.value, label: o.label }))} />
              <CaliberValue cond={c} attr={attr} op={op} onChange={(patch) => setCond(idx, patch)} />
            </div>
          </Card>
        );
      })}
      <Button type="primary" icon={<SaveOutlined />} onClick={save} style={{ marginTop: 8 }}>保存口径</Button>
    </div>
  );
}

function CaliberValue({ cond, attr, op, onChange }: { cond: CaliberCondition; attr?: ObjectAttribute; op?: { multi?: boolean; noValue?: boolean }; onChange: (patch: Partial<CaliberCondition>) => void }) {
  if (op?.noValue) return <Text type="secondary" style={{ alignSelf: 'center' }}>-</Text>;
  const multi = op?.multi;
  if (attr?.enumValues && attr.enumValues.length > 0) {
    return <Select mode={multi ? 'multiple' : undefined} value={cond.values as string[]} placeholder="选择值" options={attr.enumValues.map((v) => ({ value: v, label: v }))} onChange={(v) => onChange({ values: Array.isArray(v) ? v : [v] })} style={{ width: '100%' }} />;
  }
  if (attr?.dataType === 'NUMBER' || attr?.dataType === 'DECIMAL') {
    return <InputNumber value={cond.values[0] as unknown as number} placeholder="数值" onChange={(v) => onChange({ values: v !== null && v !== undefined ? [String(v)] : [] })} style={{ width: '100%' }} />;
  }
  return <Input value={cond.values[0] || ''} placeholder="值" onChange={(e) => onChange({ values: [e.target.value] })} />;
}

function RelatedRulesTab({ metric }: { metric: SemanticMetric }) {
  const { businessRules, deleteBusinessRule } = useStore();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<BusinessRule | null>(null);

  const owned = businessRules.filter((r) => r.targetObjectType === 'METRIC' && r.targetObjectId === metric.id);
  const referenced = businessRules.filter((r) => r.mode === 'COMPARISON' && (r.expression as { metricId?: number }).metricId === metric.id && !(r.targetObjectType === 'METRIC' && r.targetObjectId === metric.id));

  const columns = [
    { title: '规则', key: 'name', render: (_: unknown, r: BusinessRule) => <Space direction="vertical" size={0}><strong>{r.ruleName}</strong><Text type="secondary" style={{ fontSize: 12 }}>{r.ruleCode}</Text></Space> },
    { title: '模式', dataIndex: 'mode', key: 'mode' },
    { title: '触发词', dataIndex: 'triggerWords', key: 'tw', render: (v: string[]) => v.map((w) => <Tag key={w}>{w}</Tag>) },
    { title: '状态', dataIndex: 'status', key: 'st', render: (v: RuleStatus) => <Tag color={statusColors[v]}>{statusLabels[v]}</Tag> },
    { title: '操作', key: 'a', width: 150, render: (_: unknown, r: BusinessRule) => <Space>
      <Button size="small" icon={<EditOutlined />} onClick={() => { setEditing(r); setOpen(true); }} />
      <Popconfirm title="确认删除？" onConfirm={() => { deleteBusinessRule(r.id); message.success('已删除'); }}><Button size="small" danger icon={<DeleteOutlined />} /></Popconfirm>
    </Space> },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); setOpen(true); }}>新建规则</Button>
        <Text type="secondary">主判定对象锁定为当前指标</Text>
      </Space>
      <Card size="small" title={<Space><NodeIndexOutlined /> 主判定为本指标（{owned.length}）</Space>} style={{ marginBottom: 12 }}>
        <Table dataSource={owned} columns={columns} rowKey="id" size="small" pagination={false} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无规则" /> }} />
      </Card>
      {referenced.length > 0 && (
        <Card size="small" title={`判定中引用本指标（${referenced.length}）`}>
          <Table dataSource={referenced} columns={columns} rowKey="id" size="small" pagination={false} />
        </Card>
      )}
      <BusinessRuleDrawer open={open} rule={editing} lockedTargetType="METRIC" lockedTargetId={metric.id} onClose={() => setOpen(false)} />
    </div>
  );
}

function PreviewTab({ metric, onJump }: { metric: SemanticMetric; onJump: (t: DetailTab) => void }) {
  const { validateMetric, previewMetric, publishMetric, updateMetric } = useStore();
  const [preview, setPreview] = useState<MetricPreviewResult | null>(null);
  const [issues, setIssues] = useState<ValidationIssue[]>([]);

  const handleValidate = () => { const r = validateMetric(metric.id); setIssues(r); if (r.length === 0) message.success('校验通过'); else message.warning(`${r.filter((i) => i.severity === 'BLOCKER').length} 阻断 / ${r.filter((i) => i.severity === 'WARNING').length} 警告`); };
  const handlePreview = () => { setPreview(previewMetric(metric.id)); };
  const handlePublish = () => { const pi = publishMetric(metric.id); if (pi.some((i) => i.severity === 'BLOCKER')) { message.error(pi[0].message); setIssues(pi); onJump(tabOfIssue(pi[0].checkCode)); } else { message.success('指标已发布'); setIssues([]); } };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ThunderboltOutlined />} onClick={handlePreview}>预览</Button>
        <Button icon={<WarningOutlined />} onClick={handleValidate}>校验</Button>
        <Button type="primary" icon={<CheckCircleOutlined />} onClick={handlePublish}>发布</Button>
        {metric.status === 'PUBLISHED' && <Button icon={<CloseCircleOutlined />} onClick={() => { updateMetric(metric.id, { status: 'INACTIVE' }); message.success('已停用'); }}>停用</Button>}
      </Space>
      {issues.length > 0 && (
        <Alert type={issues.some((i) => i.severity === 'BLOCKER') ? 'error' : 'warning'} showIcon style={{ marginBottom: 16 }}
          message="校验问题"
          description={<Space direction="vertical" size={4}>{issues.map((issue, index) => {
            const presentation = presentValidationIssue(issue);
            return <Space key={index}><Tag color={presentation.tone === 'error' ? 'red' : 'orange'}>{presentation.label}</Tag><span>{presentation.message}</span><Button type="link" size="small" onClick={() => onJump(tabOfIssue(issue.checkCode))}>去配置</Button></Space>;
          })}</Space>} />
      )}
      {preview && (
        <Card title="预览结果（示例，非真实执行）" size="small">
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label="指标值">{preview.value !== undefined ? <strong>{preview.value}</strong> : '-'}</Descriptions.Item>
            <Descriptions.Item label="单位">{metric.unit || '-'}</Descriptions.Item>
            <Descriptions.Item label="依赖指标" span={2}>{preview.dependencies.map((d) => <Tag key={d.metricCode}>{d.metricName} ({d.metricCode})</Tag>)}</Descriptions.Item>
            <Descriptions.Item label="SQL 摘要" span={2}><pre style={{ margin: 0, fontSize: 11, whiteSpace: 'pre-wrap' }}>{preview.sql}</pre></Descriptions.Item>
          </Descriptions>
        </Card>
      )}
    </div>
  );
}
