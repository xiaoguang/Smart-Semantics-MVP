import { useState } from 'react';
import { Button, Drawer, Dropdown, Form, Input, Select, InputNumber, Space, Tag, Alert, message, Modal, DatePicker } from 'antd';
import { PlusOutlined, EditOutlined, MoreOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { Metric2WorkspaceData, MetricTarget, TargetType, TimePeriod, DimensionFilter, ValidationIssue } from './mockData';
import { mockTargets, mockMetrics, mockEntities, mockRelations, severityColor } from './mockData';
import { DimensionFilterBuilder } from './components';
import { SemanticObjectTag } from '../../components/semantic-object-visuals';
import ResponsiveDataView, { ResponsiveDataCard } from '../../components/ResponsiveDataView';
import { presentValidationIssue } from '../../features/data-standardization/business-validation-presentation';

let nextId = 7000;
const genId = () => ++nextId;

const PERIODS: TimePeriod[] = ['MONTHLY', 'QUARTERLY', 'YEARLY'];
const periodLabel: Record<TimePeriod, string> = { MONTHLY: '月度', QUARTERLY: '季度', YEARLY: '年度' };
const targetTypeLabel: Record<TargetType, string> = { AMOUNT: '数值目标', RATIO: '比率目标' };
const statusLabel: Record<string, string> = { ACTIVE: '启用', INACTIVE: '停用' };

export default function TargetTab({ data, onChange, readOnly = false }: { data?: Metric2WorkspaceData; onChange?(data: Metric2WorkspaceData): void; readOnly?: boolean }) {
  const metrics = data?.metrics ?? mockMetrics;
  const entities = data?.entities ?? mockEntities;
  const relations = data?.relations ?? mockRelations;
  const [targets, setTargets] = useState<MetricTarget[]>(() => structuredClone(data?.targets ?? mockTargets));
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<MetricTarget | null>(null);
  const [issues, setIssues] = useState<ValidationIssue[]>([]);
  const [filterMetric, setFilterMetric] = useState<number | 'ALL'>('ALL');
  const [filterPeriod, setFilterPeriod] = useState<TimePeriod | 'ALL'>('ALL');
  const commitTargets = (update: (current: MetricTarget[]) => MetricTarget[]) => setTargets((current) => {
    const next = update(current); if (data && onChange) onChange({ ...data, targets: next }); return next;
  });

  const shown = targets.filter((t) => (filterMetric === 'ALL' || t.metricId === filterMetric) && (filterPeriod === 'ALL' || t.timePeriod === filterPeriod));
  const groups = metrics.map((m) => ({ metric: m, targets: shown.filter((t) => t.metricId === m.id) })).filter((g) => g.targets.length > 0);

  const openNew = () => {
    const m = metrics[0];
    if (!m) return;
    setEditing({ id: 0, metricId: m.id, metricName: m.metricName, targetType: 'AMOUNT', targetValue: 0, dimensionFilter: { dimensions: [] }, timePeriod: 'MONTHLY', effectiveDate: '2026-05-01', status: 'ACTIVE' });
    setIssues([]); setDrawerOpen(true);
  };
  const openEdit = (t: MetricTarget) => { setEditing({ ...t }); setIssues([]); setDrawerOpen(true); };
  const remove = (id: number) => { commitTargets((ts) => ts.filter((t) => t.id !== id)); message.success('已删除'); };
  const renderTargetActions = (target: MetricTarget) => readOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : (
    <Space size={0}>
      <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(target)}>编辑</Button>
      <Dropdown
        trigger={['click']}
        menu={{
          items: [{ key: 'delete', label: '删除', danger: true }],
          onClick: () => Modal.confirm({
            title: '删除这个目标值？',
            okText: '删除',
            okButtonProps: { danger: true },
            cancelText: '取消',
            onOk: () => remove(target.id),
          }),
        }}
      >
        <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${target.metricName}`} />
      </Dropdown>
    </Space>
  );

  const setField = <K extends keyof MetricTarget>(k: K, v: MetricTarget[K]) => setEditing((e) => (e ? { ...e, [k]: v } : e));

  const onMetricChange = (id: number) => {
    const m = metrics.find((x) => x.id === id);
    if (!m) return;
    setEditing((e) => e ? { ...e, metricId: id, metricName: m.metricName, dimensionFilter: { dimensions: [] } } : e);
  };

  const validate = (t: MetricTarget): ValidationIssue[] => {
    const iss: ValidationIssue[] = [];
    if (!t.metricId) iss.push({ severity: 'BLOCKER', checkCode: 'MISSING_TARGET_METRIC', message: '必须选择指标' });
    if (t.targetValue === undefined || t.targetValue === null || (t.targetType === 'AMOUNT' && t.targetValue <= 0)) iss.push({ severity: 'BLOCKER', checkCode: 'INVALID_TARGET_VALUE', message: '目标值非法' });
    if (t.targetType === 'RATIO' && (t.targetValue < 0 || t.targetValue > 1)) iss.push({ severity: 'WARNING', checkCode: 'TARGET_RATIO_OUT_OF_RANGE', message: '比例目标建议在 0 到 1 之间' });
    if (!t.effectiveDate) iss.push({ severity: 'BLOCKER', checkCode: 'INVALID_TARGET_VALUE', message: '生效期不能为空' });
    // 维度可达性
    const m = metrics.find((x) => x.id === t.metricId);
    if (m) {
      const rootEventId = m.metricType === 'BASIC' ? m.eventId : undefined;
      const reach = rootEventId ? reachableEntityCodes(rootEventId, entities, relations) : entities.map((e) => e.entityCode);
      t.dimensionFilter.dimensions.forEach((d) => {
        if (!reach.includes(d.entityCode)) iss.push({ severity: 'BLOCKER', checkCode: 'UNREACHABLE_TARGET_DIMENSION', message: `维度实体${d.entityCode}从指标根事件不可达` });
      });
    }
    // 重复
    const dup = targets.some((x) => x.id !== t.id && x.metricId === t.metricId && sameDim(x.dimensionFilter, t.dimensionFilter) && x.timePeriod === t.timePeriod && x.effectiveDate === t.effectiveDate);
    if (dup) iss.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_TARGET', message: '同指标+同维度+同周期+同生效期重复' });
    return iss;
  };

  const save = () => {
    if (!editing) return;
    const iss = validate(editing);
    setIssues(iss);
    if (iss.some((i) => i.severity === 'BLOCKER')) { message.error('存在阻断错误，未保存'); return; }
    if (editing.id === 0) { commitTargets((ts) => [...ts, { ...editing, id: genId() }]); message.success('保存成功'); }
    else { commitTargets((ts) => ts.map((t) => (t.id === editing.id ? editing : t))); message.success('保存成功'); }
    setDrawerOpen(false);
  };

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Select value={filterMetric} onChange={setFilterMetric} style={{ width: 180 }} options={[{ value: 'ALL', label: '全部指标' }, ...metrics.map((m) => ({ value: m.id, label: m.metricName }))]} />
        <Select value={filterPeriod} onChange={setFilterPeriod} style={{ width: 140 }} options={[{ value: 'ALL', label: '全部周期' }, ...PERIODS.map((p) => ({ value: p, label: periodLabel[p] }))]} />
        {!readOnly && <Button type="primary" icon={<PlusOutlined />} onClick={openNew}>新建目标</Button>}
      </Space>

      <Space direction="vertical" style={{ width: '100%' }}>
        {groups.map((g) => (
          <div key={g.metric.id} style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 12 }}>
            <div style={{ fontWeight: 600, marginBottom: 8 }}><Space size={6}><SemanticObjectTag kind="METRIC" />{g.metric.metricName}（{g.metric.metricCode}） · {g.targets.length} 条</Space></div>
            <ResponsiveDataView rowKey="id" dataSource={g.targets} pagination={{ pageSize: 20, showSizeChanger: false }}
              ariaLabel={`${g.metric.metricName}目标值列表`}
              minTableWidth={1030}
              tableProps={{ size: 'small' }}
              columns={[
                { title: '目标类型', dataIndex: 'targetType', width: 120, render: (t: TargetType) => <Tag>{targetTypeLabel[t]}</Tag> },
                { title: '目标值', dataIndex: 'targetValue', width: 130 },
                { title: '维度限定', width: 280, render: (_: unknown, r: MetricTarget) => r.dimensionFilter.dimensions.length ? r.dimensionFilter.dimensions.map((d) => <SemanticObjectTag key={d.entityCode + d.valueCode} kind="DIMENSION" label={`${d.entityCode}=${d.valueCode}`} />) : <Tag>全局</Tag> },
                { title: '时间周期', dataIndex: 'timePeriod', width: 130, render: (period: TimePeriod) => periodLabel[period] },
                { title: '生效期', dataIndex: 'effectiveDate', width: 140 },
                { title: '状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{statusLabel[s] ?? s}</Tag> },
                { title: '操作', width: 126, render: (_: any, r: MetricTarget) => renderTargetActions(r) },
              ]}
              renderCard={(target) => <ResponsiveDataCard
                title={`${target.targetValue}`}
                subtitle={target.metricName}
                status={<Tag color={target.status === 'ACTIVE' ? 'green' : 'default'}>{statusLabel[target.status] ?? target.status}</Tag>}
                fields={[
                  { label: '目标类型', value: targetTypeLabel[target.targetType] },
                  { label: '时间周期', value: periodLabel[target.timePeriod] },
                  { label: '生效期', value: target.effectiveDate },
                  { label: '维度限定', value: target.dimensionFilter.dimensions.length ? target.dimensionFilter.dimensions.map((dimension) => <SemanticObjectTag key={dimension.entityCode + dimension.valueCode} kind="DIMENSION" label={`${dimension.entityCode}=${dimension.valueCode}`} />) : '全局', wide: true },
                ]}
                actions={renderTargetActions(target)}
              />}
            />
          </div>
        ))}
        {groups.length === 0 && <Alert type="info" showIcon message="无符合条件的目标值" />}
      </Space>

      <Drawer title={editing?.id === 0 ? '新建目标值' : '编辑目标值'} open={drawerOpen} onClose={() => setDrawerOpen(false)} width="min(680px, 100vw)" footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setDrawerOpen(false)}>取消</Button><Button type="primary" onClick={save}>保存</Button></Space>}>
        {editing && (
          <Form layout="vertical">
            <Alert type="info" showIcon style={{ marginBottom: 12 }}
              message="先指定目标生效的时间周期；需要细分到门店、商品等范围时，再添加业务维度。" />
            <Form.Item label="关联指标" required>
              <Select value={editing.metricId} onChange={onMetricChange} options={metrics.map((m) => ({ value: m.id, label: `${m.metricName}(${m.metricCode})` }))} />
            </Form.Item>
            <Space style={{ width: '100%' }}>
              <Form.Item label="目标类型" required>
                <Select value={editing.targetType} onChange={(v: TargetType) => setField('targetType', v)} style={{ width: 160 }} options={[{ value: 'AMOUNT', label: targetTypeLabel.AMOUNT }, { value: 'RATIO', label: targetTypeLabel.RATIO }]} />
              </Form.Item>
              <Form.Item label="目标值" required>
                <InputNumber value={editing.targetValue} onChange={(v) => setField('targetValue', v ?? 0)} step={0.1} style={{ width: 200 }} />
              </Form.Item>
            </Space>

            {/* 时间维度轴（固定必填） */}
            <div style={{ border: '1px solid #d9d9d9', borderRadius: 6, padding: 12, marginBottom: 12, background: '#fafafa' }}>
              <div style={{ fontWeight: 600, marginBottom: 8 }}>生效周期（必填）</div>
              <Space>
                <Form.Item label="周期粒度" required style={{ marginBottom: 0 }}>
                  <Select value={editing.timePeriod} onChange={(v: TimePeriod) => setField('timePeriod', v)} style={{ width: 150 }} options={PERIODS.map((p) => ({ value: p, label: periodLabel[p] }))} />
                </Form.Item>
                <Form.Item label="生效日期" required style={{ marginBottom: 0 }}>
                  <DatePicker value={editing.effectiveDate ? dayjs(editing.effectiveDate) : null} onChange={(_, ds) => setField('effectiveDate', ds as string)} style={{ width: 180 }} />
                </Form.Item>
              </Space>
              <div style={{ color: '#8c8c8c', fontSize: 12, marginTop: 8 }}>目标值只对该具体周期生效，不适用于任意/重复范围；多周期配多条。</div>
            </div>

            {/* 业务维度轴（可变多维） */}
            <Form.Item label="业务范围（可选）">
              <DimensionFilterBuilder value={editing.dimensionFilter} onChange={(v: DimensionFilter) => setField('dimensionFilter', v)} metric={metrics.find((m) => m.id === editing.metricId)} entities={entities} relations={relations} />
            </Form.Item>
            <Form.Item label="说明"><Input.TextArea rows={2} value={editing.description} onChange={(e) => setField('description', e.target.value)} /></Form.Item>
          </Form>
        )}

        {issues.length > 0 && (
          <div style={{ marginTop: 16 }}>
            {issues.map((issue, index) => {
              const presentation = presentValidationIssue(issue);
              return <Alert key={index} type={presentation.tone} showIcon style={{ marginBottom: 8 }}
                message={<><Tag color={severityColor(issue.severity)}>{presentation.label}</Tag>{presentation.message}</>} />;
            })}
          </div>
        )}
      </Drawer>
    </div>
  );
}

function reachableEntityCodes(
  eventId: number | undefined,
  entities: Metric2WorkspaceData['entities'],
  relations: Metric2WorkspaceData['relations'],
): string[] {
  if (!eventId) return entities.map((e) => e.entityCode);
  const entityIds = new Set(relations.flatMap((relation) => {
    if (relation.sourceType === 'EVENT' && relation.sourceId === eventId && relation.targetType === 'ENTITY') return [relation.targetId];
    if (relation.targetType === 'EVENT' && relation.targetId === eventId && relation.sourceType === 'ENTITY') return [relation.sourceId];
    return [];
  }));
  return entities.filter((entity) => entityIds.has(entity.id)).map((entity) => entity.entityCode);
}
function sameDim(a: DimensionFilter, b: DimensionFilter): boolean {
  const canonical = (value: DimensionFilter) => [...value.dimensions]
    .map((item) => ({ entityCode: item.entityCode, valueCode: item.valueCode }))
    .sort((left, right) => `${left.entityCode}:${left.valueCode}`.localeCompare(`${right.entityCode}:${right.valueCode}`));
  return JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));
}
