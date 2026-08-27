import { useEffect, useMemo, useState } from 'react';
import {
  Drawer, Form, Input, Select, Button, Space, Tag, Tabs, Switch, InputNumber,
  message, Modal, Alert, Descriptions, Typography, Divider, Empty, DatePicker, Card,
} from 'antd';
import { PlusOutlined, DeleteOutlined, ThunderboltOutlined, CheckCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useStore } from '../../store/useStore';
import type {
  BusinessRule, RuleMode, RuleTargetObjectType, RuleStatus, CaliberCondition, CaliberOperator,
  ComparisonExpression, FilterExpression, ExistenceExpression, CompositeExpression,
  MetricTarget, ObjectAttribute, ValidationIssue, RulePreviewResult,
} from '../../types';
import { presentValidationIssue } from '../../features/data-standardization/business-validation-presentation';

const { Text, Paragraph } = Typography;

const modeLabels: Record<RuleMode, string> = {
  COMPARISON: '比较判定', FILTER: '筛选判定', EXISTENCE: '存在判定', COMPOSITE: '组合判定',
};
const modeDesc: Record<RuleMode, string> = {
  COMPARISON: '指标与阈值/目标比较，如「未达标」「超额」',
  FILTER: '按属性条件筛选对象集合，如「核心店」「高价值用户」',
  EXISTENCE: '事件存在性或计数，如「高频退款」「曾下单」',
  COMPOSITE: '组合已发布子规则，AND/OR/NOT 编排',
};
const targetTypeLabels: Record<RuleTargetObjectType, string> = { METRIC: '指标', EVENT: '事件', ENTITY: '实体' };
const targetColors: Record<RuleTargetObjectType, string> = { METRIC: 'blue', EVENT: 'orange', ENTITY: 'green' };
const statusLabels: Record<RuleStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', INACTIVE: '已停用' };
const statusColors: Record<RuleStatus, string> = { DRAFT: 'default', PUBLISHED: 'green', INACTIVE: 'red' };

const caliberOps: { value: CaliberOperator; label: string; multi?: boolean; noValue?: boolean }[] = [
  { value: '=', label: '= 等于' },
  { value: '!=', label: '≠ 不等于' },
  { value: '>', label: '> 大于' },
  { value: '>=', label: '≥ 大于等于' },
  { value: '<', label: '< 小于' },
  { value: '<=', label: '≤ 小于等于' },
  { value: 'IN', label: 'IN 属于', multi: true },
  { value: 'NOT_IN', label: 'NOT IN 不属于', multi: true },
  { value: 'BETWEEN', label: 'BETWEEN 区间', multi: true },
  { value: 'LIKE', label: 'LIKE 模糊' },
  { value: 'IS_NULL', label: '为空', noValue: true },
  { value: 'NOT_NULL', label: '不为空', noValue: true },
];

interface Draft {
  ruleCode: string;
  ruleName: string;
  description: string;
  mode: RuleMode | '';
  targetObjectType: RuleTargetObjectType | '';
  targetObjectId: number | undefined;
  metricId?: number;
  operator?: string;
  thresholdSource?: 'CONSTANT' | 'METRIC_TARGET' | 'METRIC';
  thresholdValue?: number;
  ratio?: number;
  oppositeMetricId?: number;
  entityId?: number;
  logic?: 'AND' | 'OR';
  conditions?: CaliberCondition[];
  eventId?: number;
  aggregate?: 'EXISTS' | 'COUNT';
  value?: number;
  timeRuleCode?: string;
  compLogic?: 'AND' | 'OR' | 'NOT';
  ruleIds?: number[];
  triggerWords: string[];
  isNegative: boolean;
  oppositeRuleId?: number;
  status: RuleStatus;
}

function emptyDraft(): Draft {
  return {
    ruleCode: '', ruleName: '', description: '', mode: '', targetObjectType: '', targetObjectId: undefined,
    triggerWords: [], isNegative: false, status: 'DRAFT',
    logic: 'AND', compLogic: 'AND', conditions: [], ruleIds: [], thresholdSource: 'CONSTANT', aggregate: 'COUNT',
  };
}

function ruleToDraft(r: BusinessRule): Draft {
  const d = emptyDraft();
  d.ruleCode = r.ruleCode; d.ruleName = r.ruleName; d.description = r.description || '';
  d.mode = r.mode; d.targetObjectType = r.targetObjectType; d.targetObjectId = r.targetObjectId;
  d.triggerWords = [...r.triggerWords]; d.isNegative = r.isNegative;
  d.oppositeRuleId = r.oppositeRuleId; d.status = r.status;
  const e = r.expression;
  if (r.mode === 'COMPARISON') {
    const c = e as ComparisonExpression;
    d.metricId = c.metricId; d.operator = c.operator; d.thresholdSource = c.thresholdSource;
    d.thresholdValue = c.thresholdValue; d.ratio = c.ratio; d.oppositeMetricId = c.targetId;
  } else if (r.mode === 'FILTER') {
    const c = e as FilterExpression;
    d.entityId = c.entityId; d.logic = c.logic; d.conditions = c.conditions?.map((x) => ({ ...x })) || [];
  } else if (r.mode === 'EXISTENCE') {
    const c = e as ExistenceExpression;
    d.eventId = c.eventId; d.aggregate = c.aggregate; d.operator = c.operator; d.value = c.value; d.timeRuleCode = c.timeRuleCode;
  } else if (r.mode === 'COMPOSITE') {
    const c = e as CompositeExpression;
    d.compLogic = c.logic; d.ruleIds = [...(c.ruleIds || [])];
  }
  return d;
}

function buildExpression(d: Draft): BusinessRule['expression'] {
  if (d.mode === 'COMPARISON') {
    return {
      metricId: d.metricId, operator: (d.operator || '<') as ComparisonExpression['operator'],
      thresholdSource: d.thresholdSource || 'CONSTANT', thresholdValue: d.thresholdValue, ratio: d.ratio, targetId: d.oppositeMetricId,
    } as ComparisonExpression;
  }
  if (d.mode === 'FILTER') {
    return { entityId: d.entityId, logic: d.logic || 'AND', conditions: d.conditions || [] } as FilterExpression;
  }
  if (d.mode === 'EXISTENCE') {
    return {
      eventId: d.eventId, aggregate: d.aggregate || 'COUNT',
      operator: d.operator as ExistenceExpression['operator'], value: d.value, timeRuleCode: d.timeRuleCode,
    } as ExistenceExpression;
  }
  return { logic: d.compLogic || 'AND', ruleIds: d.ruleIds || [] } as CompositeExpression;
}

function buildRule(d: Draft, existing?: BusinessRule): Omit<BusinessRule, 'id'> {
  const targetType = (d.targetObjectType || 'EVENT') as RuleTargetObjectType;
  const targetId = d.targetObjectId || 0;
  return {
    ruleCode: d.ruleCode, ruleName: d.ruleName, description: d.description, mode: d.mode as RuleMode,
    targetObjectType: targetType, targetObjectId: targetId,
    targetObjectName: getObjectName(targetType, targetId),
    expression: buildExpression(d), triggerWords: d.triggerWords, isNegative: d.isNegative,
    oppositeRuleId: d.oppositeRuleId, status: d.status,
    version: existing?.version || 1, owner: existing?.owner, publishedAt: existing?.publishedAt,
    lastValidation: existing?.lastValidation,
  };
}

function getObjectName(type: RuleTargetObjectType, id: number) {
  const s = useStore.getState();
  if (type === 'METRIC') { const m = s.metrics.find((x) => x.id === id); return m ? `${m.metricName} (${m.metricCode})` : `指标#${id}`; }
  if (type === 'EVENT') { const e = s.events.find((x) => x.id === id); return e ? `${e.eventName} (${e.eventCode})` : `事件#${id}`; }
  const en = s.entities.find((x) => x.id === id); return en ? `${en.entityName} (${en.entityCode})` : `实体#${id}`;
}

interface Props {
  open: boolean;
  rule?: BusinessRule | null;
  lockedTargetType?: RuleTargetObjectType;
  lockedTargetId?: number;
  onClose: () => void;
  onSaved?: (rule: BusinessRule) => void;
}

export default function BusinessRuleDrawer({ open, rule, lockedTargetType, lockedTargetId, onClose, onSaved }: Props) {
  const store = useStore();
  const { metrics, events, entities, businessRules } = store;
  const [draft, setDraft] = useState<Draft>(emptyDraft());
  const [tab, setTab] = useState('basic');
  const [issues, setIssues] = useState<ValidationIssue[]>([]);
  const [preview, setPreview] = useState<RulePreviewResult | null>(null);
  const [targetDrawerOpen, setTargetDrawerOpen] = useState(false);
  const [previewScope, setPreviewScope] = useState('华东');
  const [previewMonth, setPreviewMonth] = useState<dayjs.Dayjs | null>(dayjs('2026-06-01'));

  useEffect(() => {
    if (!open) return;
    if (rule) {
      setDraft(ruleToDraft(rule));
    } else {
      const d = emptyDraft();
      if (lockedTargetType) { d.targetObjectType = lockedTargetType; d.targetObjectId = lockedTargetId; }
      setDraft(d);
    }
    setTab('basic'); setIssues([]); setPreview(null);
  }, [open, rule, lockedTargetType, lockedTargetId]);

  const editing = !!rule;
  const update = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }));

  const locked = !!lockedTargetType;
  const targetType = draft.targetObjectType;
  const targetId = draft.targetObjectId;

  const targetObjectOptions = useMemo<{ value: number; label: string }[]>(() => {
    if (targetType === 'METRIC') return metrics.map((m) => ({ value: m.id, label: `${m.metricName} (${m.metricCode})` }));
    if (targetType === 'EVENT') return events.map((e) => ({ value: e.id, label: `${e.eventName} (${e.eventCode})` }));
    if (targetType === 'ENTITY') return entities.map((e) => ({ value: e.id, label: `${e.entityName} (${e.entityCode})` }));
    return [];
  }, [targetType, metrics, events, entities]);

  const metricOptions = useMemo(() => metrics.filter((m) => m.status === 'PUBLISHED').map((m) => ({ value: m.id, label: `${m.metricName} (${m.metricCode})` })), [metrics]);

  const filterEntityId = draft.mode === 'FILTER'
    ? (draft.entityId ?? (draft.targetObjectType === 'ENTITY' ? draft.targetObjectId : undefined))
    : undefined;
  const filterableAttrs = useMemo<ObjectAttribute[]>(() => {
    if (!filterEntityId) return [];
    const ent = entities.find((e) => e.id === filterEntityId);
    return ent ? ent.attributes.filter((a) => a.isFilterable) : [];
  }, [filterEntityId, entities]);

  const oppositeOptions = useMemo(() => {
    return businessRules.filter((r) => r.id !== rule?.id && r.status !== 'INACTIVE'
      && r.targetObjectType === targetType && r.targetObjectId === targetId && r.mode === 'COMPARISON')
      .map((r) => ({ value: r.id, label: `${r.ruleName} (${r.ruleCode})` }));
  }, [businessRules, rule, targetType, targetId]);

  const compositeOptions = useMemo(() => {
    return businessRules.filter((r) => r.id !== rule?.id && r.status === 'PUBLISHED')
      .map((r) => ({ value: r.id, label: `${r.ruleName} (${r.ruleCode}) · ${modeLabels[r.mode]}` }));
  }, [businessRules, rule]);

  const triggerConflicts = useMemo(() => {
    return draft.triggerWords.filter((w) => businessRules.some((r) => r.id !== rule?.id && r.status !== 'INACTIVE' && r.triggerWords.includes(w)));
  }, [draft.triggerWords, businessRules, rule]);

  const targetMetric = draft.metricId ? metrics.find((m) => m.id === draft.metricId) : undefined;

  const applyMode = (mode: RuleMode): Partial<Draft> => {
    const patch: Partial<Draft> = { mode, conditions: [], ruleIds: [], thresholdValue: undefined, ratio: undefined, value: undefined, eventId: undefined, metricId: undefined, entityId: undefined, oppositeMetricId: undefined, operator: undefined };
    if (mode === 'FILTER' && draft.targetObjectType === 'ENTITY' && draft.targetObjectId) patch.entityId = draft.targetObjectId;
    if (mode === 'EXISTENCE' && draft.targetObjectType === 'EVENT' && draft.targetObjectId) patch.eventId = draft.targetObjectId;
    if (mode === 'COMPARISON' && draft.targetObjectType === 'METRIC' && draft.targetObjectId) patch.metricId = draft.targetObjectId;
    if (mode === 'COMPARISON') patch.thresholdSource = 'CONSTANT';
    if (mode === 'FILTER') patch.logic = 'AND';
    if (mode === 'COMPOSITE') patch.compLogic = 'AND';
    if (mode === 'EXISTENCE') patch.aggregate = 'COUNT';
    return patch;
  };

  const handleModeChange = (mode: RuleMode) => {
    const hasExpr = draft.mode && (
      draft.metricId || (draft.conditions && draft.conditions.length > 0) || draft.eventId || (draft.ruleIds && draft.ruleIds.length > 0)
    );
    if (hasExpr && draft.mode !== mode) {
      Modal.confirm({
        title: '切换判定模式',
        content: '切换后将清空当前模式不兼容的判定配置，是否继续？',
        okText: '切换并清空', cancelText: '取消',
        onOk: () => { setDraft((d) => ({ ...d, ...applyMode(mode) })); setIssues([]); setPreview(null); },
      });
    } else {
      update(applyMode(mode));
    }
  };

  const runValidate = (): ValidationIssue[] => {
    const pre: ValidationIssue[] = [];
    if (!draft.ruleCode) pre.push({ severity: 'BLOCKER', checkCode: 'MISSING_RULE_CODE', message: '规则编码不能为空' });
    if (!draft.ruleName) pre.push({ severity: 'BLOCKER', checkCode: 'MISSING_RULE_NAME', message: '规则名称不能为空' });
    if (!draft.mode) pre.push({ severity: 'BLOCKER', checkCode: 'MISSING_RULE_MODE', message: '必须选择判定模式' });
    if (!draft.targetObjectType || !draft.targetObjectId) pre.push({ severity: 'BLOCKER', checkCode: 'MISSING_RULE_TARGET', message: '必须选择主判定对象' });
    if (pre.length) { setIssues(pre); return pre; }
    const r = buildRule(draft, rule || undefined);
    const result = store.validateBusinessRule(
      { ...r } as Partial<BusinessRule> & { mode: RuleMode; targetObjectType: RuleTargetObjectType; targetObjectId: number; expression: BusinessRule['expression'] },
      rule?.id,
    );
    setIssues(result);
    return result;
  };

  const handleSave = (publish = false) => {
    const result = runValidate();
    if (result.some((i) => i.severity === 'BLOCKER')) {
      message.error('存在阻断问题，请先修复'); setTab('config'); return;
    }
    const payload = buildRule(draft, rule || undefined);
    if (rule) {
      store.updateBusinessRule(rule.id, payload);
      if (draft.oppositeRuleId !== rule.oppositeRuleId) store.setBusinessRuleOpposite(rule.id, draft.oppositeRuleId);
      if (publish) {
        const pi = store.publishBusinessRule(rule.id);
        if (pi.some((i) => i.severity === 'BLOCKER')) { message.error('发布失败：' + pi[0].message); return; }
        message.success('规则已发布');
      } else message.success('规则已保存');
      onSaved?.({ ...rule, ...payload, id: rule.id });
    } else {
      const ai = store.addBusinessRule(payload);
      if (ai.some((i) => i.severity === 'BLOCKER')) { message.error(ai[0].message); return; }
      const created = useStore.getState().businessRules.find((x) => x.ruleCode === payload.ruleCode);
      if (created && draft.oppositeRuleId) store.setBusinessRuleOpposite(created.id, draft.oppositeRuleId);
      if (publish && created) {
        const pi = store.publishBusinessRule(created.id);
        if (pi.some((i) => i.severity === 'BLOCKER')) { message.error('发布失败：' + pi[0].message); return; }
        message.success('规则已创建并发布');
      } else message.success('规则已创建');
      if (created) onSaved?.(created);
    }
    onClose();
  };

  const handlePreview = () => {
    if (!draft.mode || !draft.targetObjectType || !draft.targetObjectId) { message.warning('请先完成基本信息与判定配置'); return; }
    const r = { ...buildRule(draft, rule || undefined), id: rule?.id || 0 } as BusinessRule;
    setPreview(store.previewRule(r));
  };

  const conditionAttr = (id?: number) => filterableAttrs.find((a) => a.id === id);

  return (
    <Drawer
      title={editing ? `编辑业务规则：${rule?.ruleName}` : '新建业务规则'}
      width={780} open={open} onClose={onClose}
      extra={<Space>
        <Button onClick={onClose}>取消</Button>
        <Button icon={<ThunderboltOutlined />} onClick={runValidate}>校验</Button>
        <Button type="primary" onClick={() => handleSave(false)}>保存草稿</Button>
      </Space>}
    >
      <Tabs activeKey={tab} onChange={setTab} items={[
        { key: 'basic', label: '基本信息', children: (
          <Form layout="vertical">
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <Form.Item label="规则编码" required>
                <Input value={draft.ruleCode} onChange={(e) => update({ ruleCode: e.target.value })} disabled={editing} placeholder="如：underperform" />
              </Form.Item>
              <Form.Item label="规则名称" required>
                <Input value={draft.ruleName} onChange={(e) => update({ ruleName: e.target.value })} placeholder="如：未达标" />
              </Form.Item>
            </div>
            <Form.Item label="判定模式" required extra={draft.mode ? modeDesc[draft.mode] : '选择后不可直接切换，切换会清空不兼容配置'}>
              <Select value={draft.mode || undefined} placeholder="选择判定模式" disabled={editing && !!rule?.mode} onChange={handleModeChange}
                options={(Object.entries(modeLabels) as [RuleMode, string][]).map(([v, l]) => ({ value: v, label: `${l} · ${modeDesc[v]}` }))} />
            </Form.Item>
            <Form.Item label="主判定对象" required extra={locked ? '从详情页进入时锁定为当前对象' : '规则的主判定对象，跨实体规则只挂主判定对象'}>
              {locked ? (
                <Space>
                  <Tag color={targetColors[(targetType || 'EVENT') as RuleTargetObjectType]}>{targetTypeLabels[(targetType || 'EVENT') as RuleTargetObjectType]}</Tag>
                  <Text strong>{targetId ? getObjectName((targetType || 'EVENT') as RuleTargetObjectType, targetId) : '未选择'}</Text>
                </Space>
              ) : (
                <Space.Compact style={{ width: '100%' }}>
                  <Select style={{ width: '30%' }} value={draft.targetObjectType || undefined} placeholder="类型"
                    onChange={(v) => update({ targetObjectType: v as RuleTargetObjectType, targetObjectId: undefined })}
                    options={(Object.entries(targetTypeLabels) as [RuleTargetObjectType, string][]).map(([v, l]) => ({ value: v, label: l }))} />
                  <Select style={{ width: '70%' }} value={draft.targetObjectId || undefined} placeholder="选择对象" showSearch optionFilterProp="label"
                    options={targetObjectOptions} disabled={!draft.targetObjectType}
                    onChange={(v) => update({ targetObjectId: v })} />
                </Space.Compact>
              )}
            </Form.Item>
            <Form.Item label="描述"><Input.TextArea rows={2} value={draft.description} onChange={(e) => update({ description: e.target.value })} /></Form.Item>
            <Form.Item label="状态"><Space><Tag color={statusColors[draft.status]}>{statusLabels[draft.status]}</Tag>{rule?.version ? <Text type="secondary">v{rule.version}</Text> : null}</Space></Form.Item>
          </Form>
        )},
        { key: 'config', label: '判定配置', children: draft.mode ? (
          <ModeConfig draft={draft} update={update} metricOptions={metricOptions} filterableAttrs={filterableAttrs}
            compositeOptions={compositeOptions} conditionAttr={conditionAttr} onManageTarget={() => setTargetDrawerOpen(true)} />
        ) : <Empty description="请先在「基本信息」选择判定模式" /> },
        { key: 'trigger', label: '触发词与对立规则', children: (
          <Form layout="vertical">
            <Form.Item label="触发词" extra="用户问数命中触发词后由规则反推指标与判定；不写入同义词表">
              <Select mode="tags" value={draft.triggerWords} placeholder="输入后回车，如：未达标、没达标"
                onChange={(v) => update({ triggerWords: v as string[] })} tokenSeparators={[',', ' ']} />
            </Form.Item>
            {triggerConflicts.length > 0 && (
              <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={`以下触发词已被其他规则占用：${triggerConflicts.join('、')}`} />
            )}
            <Form.Item label="是否反向判定" extra="如「未达标」是「达标」的反向">
              <Switch checked={draft.isNegative} onChange={(v) => update({ isNegative: v })} />
            </Form.Item>
            <Form.Item label="对立规则" extra="选择后保存时双向维护 oppositeRuleId；仅展示同主判定对象、可比较的已发布规则">
              <Select allowClear value={draft.oppositeRuleId} placeholder="选择对立规则（达标/未达标）"
                onChange={(v) => update({ oppositeRuleId: v })} options={oppositeOptions} disabled={oppositeOptions.length === 0 && !draft.oppositeRuleId} />
            </Form.Item>
            {draft.mode === 'COMPARISON' && draft.thresholdSource === 'METRIC_TARGET' && (
              <Alert type="info" showIcon
                message={<Space><span>阈值来源为目标值，比例 {draft.ratio ?? '-'}</span><Button type="link" size="small" onClick={() => setTargetDrawerOpen(true)}>管理目标值</Button></Space>}
                description={targetMetric ? `当前指标：${targetMetric.metricName}` : '请先在判定配置选择指标'} />
            )}
          </Form>
        )},
        { key: 'preview', label: '预览与发布', children: (
          <Form layout="vertical">
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <Form.Item label="范围"><Input value={previewScope} onChange={(e) => setPreviewScope(e.target.value)} placeholder="如：华东" /></Form.Item>
              <Form.Item label="参考时间"><DatePicker picker="month" value={previewMonth} onChange={setPreviewMonth} style={{ width: '100%' }} /></Form.Item>
            </div>
            <Space style={{ marginBottom: 16 }}>
              <Button icon={<ThunderboltOutlined />} onClick={handlePreview}>预览</Button>
              <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => handleSave(true)} disabled={!draft.mode}>保存并发布</Button>
            </Space>
            {issues.length > 0 && (
              <Alert type={issues.some((i) => i.severity === 'BLOCKER') ? 'error' : 'warning'} showIcon style={{ marginBottom: 16 }}
                message={`校验发现 ${issues.filter((i) => i.severity === 'BLOCKER').length} 阻断 / ${issues.filter((i) => i.severity === 'WARNING').length} 警告`}
                description={<Space direction="vertical" size={4}>{issues.map((issue, index) => {
                  const presentation = presentValidationIssue(issue);
                  return <Space key={index}><Tag color={presentation.tone === 'error' ? 'red' : 'orange'}>{presentation.label}</Tag><span>{presentation.message}</span></Space>;
                })}</Space>} />
            )}
            {preview && (
              <Card title="预览结果" size="small">
                <Descriptions size="small" column={2} bordered>
                  <Descriptions.Item label="判定结果"><Tag color={preview.verdict === 'PASS' ? 'green' : preview.verdict === 'FAIL' ? 'red' : 'default'}>{preview.verdict === 'PASS' ? '满足条件' : preview.verdict === 'FAIL' ? '不满足条件' : '暂无法判断'}</Tag></Descriptions.Item>
                  <Descriptions.Item label="命中对象数">{preview.matchedObjectCount}</Descriptions.Item>
                  {preview.actual !== undefined && <Descriptions.Item label="实际值">{preview.actual}</Descriptions.Item>}
                  {preview.threshold !== undefined && <Descriptions.Item label="阈值">{preview.threshold}</Descriptions.Item>}
                  <Descriptions.Item label="判定条件" span={2}><code style={{ fontSize: 12 }}>{preview.logicForm}</code></Descriptions.Item>
                  <Descriptions.Item label="SQL 摘要" span={2}><Paragraph style={{ margin: 0 }}><pre style={{ margin: 0, fontSize: 11, whiteSpace: 'pre-wrap' }}>{preview.sql}</pre></Paragraph></Descriptions.Item>
                </Descriptions>
              </Card>
            )}
          </Form>
        )},
      ]} />

      <MetricTargetSubDrawer open={targetDrawerOpen} metricId={draft.metricId} onClose={() => setTargetDrawerOpen(false)} />
    </Drawer>
  );
}

/* ---------- 按模式动态表单 ---------- */
function ModeConfig({ draft, update, metricOptions, filterableAttrs, compositeOptions, conditionAttr, onManageTarget }: {
  draft: Draft;
  update: (patch: Partial<Draft>) => void;
  metricOptions: { value: number; label: string }[];
  filterableAttrs: ObjectAttribute[];
  compositeOptions: { value: number; label: string }[];
  conditionAttr: (id?: number) => ObjectAttribute | undefined;
  onManageTarget: () => void;
}) {
  const store = useStore();
  if (draft.mode === 'COMPARISON') {
    return (
      <Form layout="vertical">
        <Form.Item label="判定指标" required extra={draft.targetObjectType === 'METRIC' ? '主判定对象为指标时锁定' : '选择当前或可达事件的已发布指标'}>
          <Select value={draft.metricId} placeholder="选择指标" showSearch optionFilterProp="label"
            options={metricOptions} disabled={draft.targetObjectType === 'METRIC'}
            onChange={(v) => update({ metricId: v, oppositeMetricId: undefined })} />
        </Form.Item>
        <Form.Item label="比较符" required>
          <Select value={draft.operator} placeholder="选择比较符" onChange={(v) => update({ operator: v })}
            options={[['<', '< 小于'], ['<=', '≤ 小于等于'], ['>', '> 大于'], ['>=', '≥ 大于等于'], ['=', '= 等于'], ['!=', '≠ 不等于']].map(([v, l]) => ({ value: v, label: l }))} />
        </Form.Item>
        <Form.Item label="阈值来源" required>
          <Select value={draft.thresholdSource} onChange={(v) => update({ thresholdSource: v })}
            options={[{ value: 'CONSTANT', label: '常量 - 直接填写阈值' }, { value: 'METRIC_TARGET', label: '目标值 - 引用指标目标并按比例换算' }, { value: 'METRIC', label: '另一指标 - 与另一指标比较' }]} />
        </Form.Item>
        {draft.thresholdSource === 'CONSTANT' && (
          <Form.Item label="阈值" required><InputNumber style={{ width: '100%' }} value={draft.thresholdValue} onChange={(v) => update({ thresholdValue: v ?? undefined })} /></Form.Item>
        )}
        {draft.thresholdSource === 'METRIC_TARGET' && (
          <Form.Item label="比例" required extra="目标值 × 比例 = 判定阈值，如 0.95 表示低于目标 95% 即未达标">
            <InputNumber style={{ width: '100%' }} min={0} max={10} step={0.05} value={draft.ratio} onChange={(v) => update({ ratio: v ?? undefined })} />
            <Button type="link" size="small" onClick={onManageTarget}>管理目标值</Button>
          </Form.Item>
        )}
        {draft.thresholdSource === 'METRIC' && (
          <Form.Item label="对比指标" required><Select value={draft.oppositeMetricId} showSearch optionFilterProp="label" options={metricOptions} onChange={(v) => update({ oppositeMetricId: v })} /></Form.Item>
        )}
      </Form>
    );
  }

  if (draft.mode === 'FILTER') {
    const conditions = draft.conditions || [];
    const effEntityId = draft.entityId ?? (draft.targetObjectType === 'ENTITY' ? draft.targetObjectId : undefined);
    const addCondition = () => update({ conditions: [...conditions, { attributeId: 0, attributeCode: '', op: '=', values: [] }] });
    const setCond = (idx: number, patch: Partial<CaliberCondition>) => update({ conditions: conditions.map((c, i) => i === idx ? { ...c, ...patch } : c) });
    return (
      <Form layout="vertical">
        <Form.Item label="主判定实体" required extra={draft.targetObjectType === 'ENTITY' ? '主判定对象为实体时锁定' : '筛选条件所属实体'}>
          <Select value={effEntityId} placeholder="选择实体" showSearch optionFilterProp="label"
            options={store.entities.map((e) => ({ value: e.id, label: `${e.entityName} (${e.entityCode})` }))}
            disabled={draft.targetObjectType === 'ENTITY'}
            onChange={(v) => update({ entityId: v, conditions: [] })} />
        </Form.Item>
        <Form.Item label="条件组合逻辑">
          <Select value={draft.logic} onChange={(v) => update({ logic: v })} options={[{ value: 'AND', label: 'AND 全部满足' }, { value: 'OR', label: 'OR 任一满足' }]} />
        </Form.Item>
        <Divider>条件列表</Divider>
        {conditions.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无条件，点击下方添加" />}
        {conditions.map((c, idx) => {
          const attr = conditionAttr(c.attributeId);
          return <ConditionRow key={idx} cond={c} attrs={filterableAttrs} attr={attr}
            onChange={(patch) => setCond(idx, patch)} onRemove={() => update({ conditions: conditions.filter((_, i) => i !== idx) })} />;
        })}
        <Button type="dashed" block icon={<PlusOutlined />} onClick={addCondition} disabled={filterableAttrs.length === 0}>添加条件</Button>
      </Form>
    );
  }

  if (draft.mode === 'EXISTENCE') {
    return (
      <Form layout="vertical">
        <Form.Item label="判定事件" required extra={draft.targetObjectType === 'EVENT' ? '主判定对象为事件时锁定' : '选择参与或可达事件'}>
          <Select value={draft.eventId} placeholder="选择事件" showSearch optionFilterProp="label"
            options={store.events.map((e) => ({ value: e.id, label: `${e.eventName} (${e.eventCode})` }))}
            disabled={draft.targetObjectType === 'EVENT'}
            onChange={(v) => update({ eventId: v })} />
        </Form.Item>
        <Form.Item label="聚合方式" required>
          <Select value={draft.aggregate} onChange={(v) => update({ aggregate: v })} options={[{ value: 'EXISTS', label: 'EXISTS 是否存在' }, { value: 'COUNT', label: 'COUNT 计数' }]} />
        </Form.Item>
        {draft.aggregate === 'COUNT' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item label="比较符"><Select value={draft.operator} onChange={(v) => update({ operator: v })} options={['>', '>=', '<', '<=', '='].map((x) => ({ value: x, label: x }))} /></Form.Item>
            <Form.Item label="阈值次数"><InputNumber style={{ width: '100%' }} value={draft.value} onChange={(v) => update({ value: v ?? undefined })} /></Form.Item>
          </div>
        )}
        <Form.Item label="时间窗口" extra="复用时间语义规则">
          <Select allowClear value={draft.timeRuleCode} placeholder="选择时间规则" showSearch optionFilterProp="label"
            onChange={(v) => update({ timeRuleCode: v })}
            options={store.rules.filter((r) => r.status === 'ACTIVE').map((r) => ({ value: r.ruleCode, label: `${r.ruleText} (${r.ruleCode})` }))} />
        </Form.Item>
      </Form>
    );
  }

  return (
    <Form layout="vertical">
      <Alert type="info" showIcon style={{ marginBottom: 16 }} message="用可视化 AND/OR/NOT 编排已发布子规则；不可选择自身或形成循环的规则" />
      <Form.Item label="编排逻辑" required>
        <Select value={draft.compLogic} onChange={(v) => update({ compLogic: v })} options={[{ value: 'AND', label: 'AND 全部满足' }, { value: 'OR', label: 'OR 任一满足' }, { value: 'NOT', label: 'NOT 取反' }]} />
      </Form.Item>
      <Form.Item label="子规则" required>
        <Select mode="multiple" value={draft.ruleIds} placeholder="选择已发布子规则" optionFilterProp="label"
          onChange={(v) => update({ ruleIds: v as number[] })} options={compositeOptions} />
      </Form.Item>
      {draft.ruleIds && draft.ruleIds.length > 0 && (
        <Card size="small" title="依赖子规则">
          {draft.ruleIds.map((rid) => {
            const r = store.businessRules.find((x) => x.id === rid);
            return r ? <div key={rid} style={{ padding: '4px 0' }}><Tag>{modeLabels[r.mode]}</Tag><Text>{r.ruleName}</Text><Text type="secondary"> ({r.ruleCode})</Text></div> : null;
          })}
        </Card>
      )}
    </Form>
  );
}

/* ---------- 条件行 ---------- */
function ConditionRow({ cond, attrs, attr, onChange, onRemove }: {
  cond: CaliberCondition;
  attrs: ObjectAttribute[];
  attr?: ObjectAttribute;
  onChange: (patch: Partial<CaliberCondition>) => void;
  onRemove: () => void;
}) {
  const op = caliberOps.find((o) => o.value === cond.op);
  const setAttr = (a: ObjectAttribute) => onChange({ attributeId: a.id, attributeCode: a.attributeCode, attributeName: a.attributeName, values: [] });
  return (
    <Card size="small" style={{ marginBottom: 8 }} extra={<Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={onRemove} />}>
      <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 0.8fr 1.4fr', gap: 8 }}>
        <Select size="small" value={cond.attributeId || undefined} placeholder="选择属性" showSearch optionFilterProp="label"
          options={attrs.map((a) => ({ value: a.id, label: `${a.attributeName} (${a.attributeCode})` }))} onChange={(v) => { const a = attrs.find((x) => x.id === v); if (a) setAttr(a); }} />
        <Select size="small" value={cond.op} onChange={(v) => onChange({ op: v as CaliberOperator })} options={caliberOps.map((o) => ({ value: o.value, label: o.label }))} />
        <ValueControl cond={cond} attr={attr} op={op} onChange={onChange} />
      </div>
    </Card>
  );
}

function ValueControl({ cond, attr, op, onChange }: { cond: CaliberCondition; attr?: ObjectAttribute; op?: { multi?: boolean; noValue?: boolean }; onChange: (patch: Partial<CaliberCondition>) => void }) {
  if (op?.noValue) return <Text type="secondary" style={{ fontSize: 12, alignSelf: 'center' }}>-</Text>;
  const multi = op?.multi;
  if (attr?.enumValues && attr.enumValues.length > 0) {
    return <Select size="small" mode={multi ? 'multiple' : undefined} value={cond.values as string[]} placeholder="选择值"
      options={attr.enumValues.map((v) => ({ value: v, label: v }))} onChange={(v) => onChange({ values: Array.isArray(v) ? v : [v] })} style={{ width: '100%' }} />;
  }
  if (attr?.dataType === 'DATETIME' || (attr && ['DATE', 'DATETIME', 'TIME'].includes(attr.semanticType))) {
    if (multi) return <DatePicker.RangePicker size="small" value={cond.values[0] && cond.values[1] ? [dayjs(cond.values[0]), dayjs(cond.values[1])] : undefined} onChange={(_, ds) => onChange({ values: ds as string[] })} style={{ width: '100%' }} />;
    return <DatePicker size="small" value={cond.values[0] ? dayjs(cond.values[0]) : null} onChange={(d) => onChange({ values: d ? [d.format('YYYY-MM-DD')] : [] })} style={{ width: '100%' }} />;
  }
  if (attr?.dataType === 'NUMBER' || attr?.dataType === 'DECIMAL') {
    return <InputNumber size="small" value={cond.values[0] as unknown as number} placeholder="数值" onChange={(v) => onChange({ values: v !== null && v !== undefined ? [String(v)] : [] })} style={{ width: '100%' }} />;
  }
  return <Input size="small" value={cond.values[0] || ''} placeholder="值" onChange={(e) => onChange({ values: [e.target.value] })} />;
}

/* ---------- 目标值子抽屉 ---------- */
function MetricTargetSubDrawer({ open, metricId, onClose }: { open: boolean; metricId?: number; onClose: () => void }) {
  const store = useStore();
  const [form] = Form.useForm();
  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ valueType: 'ABSOLUTE', period: 'MONTH', targetValue: 30000, effectiveStart: dayjs('2026-01-01'), effectiveEnd: dayjs('2026-12-31'), status: 'ACTIVE' });
  }, [open, form]);

  const metric = metricId ? store.metrics.find((m) => m.id === metricId) : undefined;
  const existingTargets = store.metricTargets.filter((t) => t.metricId === metricId);

  const save = () => {
    form.validateFields().then((values) => {
      const payload: Omit<MetricTarget, 'id'> = {
        metricId: metricId!, metricCode: metric?.metricCode, metricName: metric?.metricName,
        valueType: values.valueType, period: values.period, targetValue: values.targetValue,
        effectiveStart: values.effectiveStart.format('YYYY-MM-DD'), effectiveEnd: values.effectiveEnd.format('YYYY-MM-DD'),
        status: values.status,
      };
      const issues = store.validateMetricTarget(payload);
      if (issues.some((i) => i.severity === 'BLOCKER')) { message.error(issues.find((i) => i.severity === 'BLOCKER')!.message); return; }
      store.addMetricTarget(payload);
      message.success('目标值已创建并回填');
      onClose();
    });
  };

  return (
    <Drawer title="管理目标值" width={460} open={open} onClose={onClose}
      extra={<Space><Button onClick={onClose}>取消</Button><Button type="primary" onClick={save} disabled={!metricId}>保存目标</Button></Space>}>
      {metric ? <Alert type="info" showIcon style={{ marginBottom: 16 }} message={`指标：${metric.metricName} (${metric.metricCode})`} /> : <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="请先在判定配置选择指标" />}
      <Form form={form} layout="vertical">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item name="valueType" label="目标类型" rules={[{ required: true }]}>
            <Select options={[{ value: 'ABSOLUTE', label: '绝对值' }, { value: 'GROWTH', label: '增长率' }, { value: 'RATIO', label: '比例' }]} />
          </Form.Item>
          <Form.Item name="period" label="周期" rules={[{ required: true }]}>
            <Select options={[{ value: 'YEAR', label: '年' }, { value: 'QUARTER', label: '季度' }, { value: 'MONTH', label: '月' }, { value: 'WEEK', label: '周' }, { value: 'DAY', label: '日' }]} />
          </Form.Item>
        </div>
        <Form.Item name="targetValue" label="目标值" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} /></Form.Item>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item name="effectiveStart" label="生效起" rules={[{ required: true }]}><DatePicker style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="effectiveEnd" label="生效止" rules={[{ required: true }]}><DatePicker style={{ width: '100%' }} /></Form.Item>
        </div>
        <Form.Item name="status" label="状态"><Select options={[{ value: 'ACTIVE', label: '生效' }, { value: 'DRAFT', label: '草稿' }, { value: 'INACTIVE', label: '停用' }]} /></Form.Item>
      </Form>
      {existingTargets.length > 0 && (
        <>
          <Divider>已存在目标</Divider>
          {existingTargets.map((t) => (
            <div key={t.id} style={{ padding: '6px 0', borderBottom: '1px solid #f0f0f0' }}>
              <Space>
                <Tag>{t.period}</Tag><Text>{t.targetValue}</Text>
                {t.dimensionLabel && <Text type="secondary">· {t.dimensionLabel}</Text>}
                <Text type="secondary" style={{ fontSize: 12 }}>{t.effectiveStart} ~ {t.effectiveEnd}</Text>
                <Tag color={t.status === 'ACTIVE' ? 'green' : 'default'}>{t.status}</Tag>
              </Space>
            </div>
          ))}
        </>
      )}
    </Drawer>
  );
}
