import { useState } from 'react';
import { Card, Tabs, Button, Drawer, Dropdown, Empty, Form, Input, Select, Space, Tag, Alert, message, Modal, Checkbox, Switch, InputNumber } from 'antd';
import { AimOutlined, PlusOutlined, EditOutlined, DeleteOutlined, LinkOutlined, MoreOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { BusinessRule, Metric2WorkspaceData, ReadOnlyRuleCandidate, RuleMode, TargetObjectType, ComparisonExpression, CaliberFilter, CompositeComponent, ValidationIssue, LogicOp } from './mockData';
import { mockRules, mockMetrics, mockEntities, mockEvents, MODE_OBJECT_MATRIX, targetObjectLabel, severityColor } from './mockData';
import { AttributeConditionBuilder, TriggerWordsInput } from './components';
import TargetTab from './TargetTab';
import { SemanticObjectTag, type SemanticVisualKind } from '../../components/semantic-object-visuals';
import ResponsiveDataView, { ResponsiveDataCard } from '../../components/ResponsiveDataView';
import TechnicalDisclosure from '../../components/TechnicalDisclosure';

let nextId = 5000;
const genId = () => ++nextId;
const OPS = ['>=', '>', '<=', '<', '=', '!='] as const;
const ruleModeLabel: Record<RuleMode, string> = { COMPARISON: '阈值比较', FILTER: '对象筛选', COMPOSITE: '组合规则' };
const ruleStatusLabel: Record<string, string> = { ACTIVE: '启用', INACTIVE: '停用' };

export default function BusinessRulePage({ data, onChange, readOnly = false }: {
  data?: Metric2WorkspaceData;
  onChange?(data: Metric2WorkspaceData): void;
  readOnly?: boolean;
}) {
  const [activeTab, setActiveTab] = useState('rules');

  return (
    <Card title="业务规则" style={{ height: '100%' }}>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: 'rules', label: <span><ThunderboltOutlined /> 业务规则</span>, children: <BusinessRuleTab data={data} onChange={onChange} readOnly={readOnly} /> },
          { key: 'candidates', label: `待结构化候选 (${data?.ruleCandidates?.length ?? 0})`, children: <RuleCandidateTab candidates={data?.ruleCandidates ?? []} /> },
          { key: 'targets', label: <span><AimOutlined /> 目标值维护</span>, children: <TargetTab data={data} onChange={onChange} readOnly={readOnly} /> },
        ]}
      />
    </Card>
  );
}

function RuleCandidateTab({ candidates }: { candidates: ReadOnlyRuleCandidate[] }) {
  return <Space direction="vertical" size={12} style={{ width: '100%' }}>
    <Alert type="info" showIcon message="这些内容来自来源证据和建模推断，只供阅读；完成结构化确认后才能成为可执行规则。" />
    <ResponsiveDataView
      ariaLabel="待结构化规则候选列表"
      rowKey="id"
      dataSource={candidates}
      pagination={{ pageSize: 12 }}
      mobilePageSize={12}
      minTableWidth={980}
      tableProps={{ locale: { emptyText: <Empty description="当前版本没有待结构化规则候选" /> } }}
      columns={[
        { title: '候选规则', dataIndex: 'ruleName', width: 230, render: (name: string, value: ReadOnlyRuleCandidate) => <Space direction="vertical" size={0}><strong>{name}</strong><code>{value.ruleCode}</code></Space> },
        { title: '业务说明', dataIndex: 'description', width: 360 },
        { title: '影响对象', dataIndex: 'targetObjectName', width: 180, render: (name?: string, value?: ReadOnlyRuleCandidate) => name && value?.targetObjectType ? <Space><SemanticObjectTag kind={value.targetObjectType as SemanticVisualKind} />{name}</Space> : <Tag color="orange">目标待确认</Tag> },
        { title: '依据', dataIndex: 'evidenceRefs', width: 100, render: (items: string[]) => `${items.length} 处` },
        { title: '状态', dataIndex: 'status', width: 120, render: (status: ReadOnlyRuleCandidate['status']) => <Tag color={status === 'UNRESOLVED_TARGET' ? 'orange' : undefined}>{status === 'UNRESOLVED_TARGET' ? '目标待确认' : '待结构化'}</Tag> },
      ]}
      renderCard={(candidate) => <ResponsiveDataCard
        title={candidate.ruleName}
        subtitle={<code>{candidate.ruleCode}</code>}
        status={<Tag color={candidate.status === 'UNRESOLVED_TARGET' ? 'orange' : undefined}>{candidate.status === 'UNRESOLVED_TARGET' ? '目标待确认' : '待结构化'}</Tag>}
        fields={[
          { label: '业务说明', value: candidate.description, wide: true },
          { label: '影响对象', value: candidate.targetObjectName && candidate.targetObjectType ? <Space><SemanticObjectTag kind={candidate.targetObjectType as SemanticVisualKind} />{candidate.targetObjectName}</Space> : '目标待确认' },
          { label: '依据', value: `${candidate.evidenceRefs.length} 处` },
        ]}
      />}
    />
  </Space>;
}

function BusinessRuleTab({ data, onChange, readOnly = false }: { data?: Metric2WorkspaceData; onChange?(data: Metric2WorkspaceData): void; readOnly?: boolean }) {
  const metrics = data?.metrics ?? mockMetrics;
  const entities = data?.entities ?? mockEntities;
  const events = data?.events ?? mockEvents;
  const [rules, setRules] = useState<BusinessRule[]>(() => structuredClone(data?.rules ?? mockRules));
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<BusinessRule | null>(null);
  const [issues, setIssues] = useState<ValidationIssue[]>([]);
  const [filterMode, setFilterMode] = useState<RuleMode | 'ALL'>('ALL');
  const [filterObj, setFilterObj] = useState<TargetObjectType | 'ALL'>('ALL');
  const commitRules = (next: BusinessRule[]) => { setRules(next); if (data && onChange) onChange({ ...data, rules: next }); };

  // 对立规则自动生成
  const [genOpposite, setGenOpposite] = useState(false);
  const [oppName, setOppName] = useState('');
  const [oppCode, setOppCode] = useState('');
  const [oppTriggers, setOppTriggers] = useState<string[]>([]);

  const shown = rules.filter((r) => (filterMode === 'ALL' || r.ruleType === filterMode) && (filterObj === 'ALL' || r.targetObjectType === filterObj));

  const openNew = () => {
    setEditing({ id: 0, ruleCode: '', ruleName: '', ruleType: '' as RuleMode, targetObjectType: '' as TargetObjectType, targetObjectId: 0, targetObjectName: '', expression: null, isNegative: false, triggerWords: [], status: 'ACTIVE' });
    setIssues([]); setGenOpposite(false); setOppName(''); setOppCode(''); setOppTriggers([]);
    setDrawerOpen(true);
  };
  const openEdit = (r: BusinessRule) => {
    setEditing(JSON.parse(JSON.stringify(r)));
    setIssues([]); setGenOpposite(false); setOppName(''); setOppCode(''); setOppTriggers([]);
    setDrawerOpen(true);
  };
  const remove = (id: number) => {
    commitRules(rules.filter((r) => r.id !== id).map((r) => (r.oppositeRuleId === id ? { ...r, oppositeRuleId: undefined } : r)));
    message.success('已删除');
  };

  const setField = <K extends keyof BusinessRule>(k: K, v: BusinessRule[K]) => setEditing((e) => (e ? { ...e, [k]: v } : e));

  // 模式 × 对象 联动
  const onModeChange = (mode: RuleMode) => {
    setEditing((e) => {
      if (!e) return e;
      if (mode === 'COMPARISON') return { ...e, ruleType: mode, targetObjectType: 'METRIC', ...resetExprForMode(mode) };
      if (mode === 'FILTER') return { ...e, ruleType: mode, targetObjectType: (e.targetObjectType === 'EVENT' ? 'EVENT' : 'ENTITY'), targetObjectId: 0, targetObjectName: '', ...resetExprForMode(mode) };
      // COMPOSITE：对象由子规则带出，不预设
      return { ...e, ruleType: mode, targetObjectType: '' as TargetObjectType, targetObjectId: 0, targetObjectName: '', ...resetExprForMode(mode) };
    });
  };
  const onObjTypeChange = (t: TargetObjectType) => {
    const allowedModes = (Object.keys(MODE_OBJECT_MATRIX) as RuleMode[]).filter((m) => MODE_OBJECT_MATRIX[m].includes(t));
    const mode = allowedModes.includes(editing!.ruleType) ? editing!.ruleType : allowedModes[0];
    setEditing((e) => e ? { ...e, targetObjectType: t, ruleType: mode, ...resetExprForMode(mode) } : e);
  };
  const onTargetObjectChange = (id: number) => {
    setEditing((e) => e ? { ...e, targetObjectId: id, targetObjectName: objectName(e.targetObjectType, id) } : e);
  };

  function resetExprForMode(mode: RuleMode): Partial<BusinessRule> {
    if (mode === 'COMPARISON') return { expression: { operator: '>=', ratio: 0.95 }, components: undefined };
    if (mode === 'FILTER') return { expression: { logic: 'AND', conditions: [] }, components: undefined };
    return { expression: null, logicOperator: 'AND', components: [] };
  }
  function objectName(t: TargetObjectType, id: number): string {
    if (t === 'METRIC') return metrics.find((m) => m.id === id)?.metricName ?? '';
    if (t === 'ENTITY') return entities.find((e) => e.id === id)?.entityName ?? '';
    return events.find((e) => e.id === id)?.eventName ?? '';
  }
  const targetObjectOptions = (t: TargetObjectType) => {
    if (!t) return [];
    if (t === 'METRIC') return metrics.map((m) => ({ value: m.id, label: `${m.metricName}(${m.metricCode})` }));
    if (t === 'ENTITY') return entities.map((e) => ({ value: e.id, label: `${e.entityName}(${e.entityCode})` }));
    return events.map((e) => ({ value: e.id, label: `${e.eventName}(${e.eventCode})` }));
  };
  const allowedModeOptions = (Object.keys(MODE_OBJECT_MATRIX) as RuleMode[]);
  const allowedObjOptions = editing?.ruleType ? MODE_OBJECT_MATRIX[editing.ruleType] : (['METRIC', 'ENTITY', 'EVENT'] as TargetObjectType[]);
  const showObjectSelectors = editing?.ruleType === 'COMPARISON' || editing?.ruleType === 'FILTER';

  const validate = (r: BusinessRule): ValidationIssue[] => {
    const iss: ValidationIssue[] = [];
    if (!r.ruleCode) iss.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_RULE_CODE', message: '规则编码不能为空' });
    else if (rules.some((x) => x.id !== r.id && x.ruleCode === r.ruleCode)) iss.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_RULE_CODE', message: `规则编码"${r.ruleCode}"已存在` });
    if (!r.ruleName) iss.push({ severity: 'BLOCKER', checkCode: 'MISSING_RULE_NAME', message: '规则名称不能为空' });
    if ((r.ruleType === 'COMPARISON' || r.ruleType === 'FILTER') && !MODE_OBJECT_MATRIX[r.ruleType]?.includes(r.targetObjectType)) iss.push({ severity: 'BLOCKER', checkCode: 'INVALID_RULE_TARGET_COMBINATION', message: '所选判断方式不适用于当前对象，请重新选择。' });
    if (r.ruleType === 'COMPARISON') {
      const e = r.expression as ComparisonExpression;
      if (e.ratio !== undefined && e.ratio <= 0) iss.push({ severity: 'BLOCKER', checkCode: 'INVALID_COMPARISON_EXPRESSION', message: '比率必须为正数' });
    }
    if (r.ruleType === 'FILTER') {
      const e = r.expression as CaliberFilter;
      if (!e.conditions || e.conditions.length === 0) iss.push({ severity: 'BLOCKER', checkCode: 'INVALID_FILTER_ATTRIBUTE', message: '筛选规则至少需要一条条件' });
      e.conditions?.forEach((c) => {
        if (!c.attributeCode) iss.push({ severity: 'BLOCKER', checkCode: 'INVALID_FILTER_ATTRIBUTE', message: '条件未选择属性' });
        if (c.values.length === 0 || !c.values[0]) iss.push({ severity: 'WARNING', checkCode: 'INVALID_CALIBER_VALUE', message: `条件"${c.attributeCode}"未填写取值` });
      });
    }
    if (r.ruleType === 'COMPOSITE') {
      if (!r.components || r.components.length === 0) iss.push({ severity: 'BLOCKER', checkCode: 'COMPOSITE_NO_COMPONENTS', message: '组合规则至少选择一个子规则' });
      r.components?.forEach((c) => {
        if (!c.subRuleId) iss.push({ severity: 'BLOCKER', checkCode: 'COMPOSITE_NO_COMPONENTS', message: '存在未选择的子规则' });
        else {
          const ref = rules.find((b) => b.id === c.subRuleId);
          if (!ref) iss.push({ severity: 'BLOCKER', checkCode: 'COMPOSITE_NO_COMPONENTS', message: '有一项子规则已不存在，请重新选择。' });
          else if (ref.ruleType !== 'FILTER') iss.push({ severity: 'BLOCKER', checkCode: 'COMPOSITE_NO_COMPONENTS', message: `组合规则只能引用筛选规则，请重新选择“${ref.ruleName}”。` });
        }
      });
    }
    return iss;
  };

  const save = () => {
    if (!editing) return;
    const iss = validate(editing);
    setIssues(iss);
    if (iss.some((i) => i.severity === 'BLOCKER')) { message.error('存在阻断错误，未保存'); return; }
    let newRules = rules;
    if (editing.id === 0) {
      const newId = genId();
      const positive: BusinessRule = { ...editing, id: newId };
      const list = [positive];
      if (editing.ruleType === 'COMPARISON' && genOpposite) {
        const oppId = genId();
        const opposite: BusinessRule = {
          id: oppId, ruleCode: oppCode || editing.ruleCode + '_neg', ruleName: oppName || '反向规则',
          ruleType: 'COMPARISON', targetObjectType: editing.targetObjectType, targetObjectId: editing.targetObjectId, targetObjectName: editing.targetObjectName,
          expression: JSON.parse(JSON.stringify(editing.expression)), isNegative: true, oppositeRuleId: newId,
          triggerWords: oppTriggers, status: 'ACTIVE',
        };
        positive.oppositeRuleId = oppId;
        list.push(opposite);
      }
      newRules = [...rules, ...list];
    } else {
      newRules = rules.map((r) => {
        if (r.id === editing.id) return editing;
        if (r.oppositeRuleId === editing.id && editing.ruleType === 'COMPARISON') return { ...r, expression: JSON.parse(JSON.stringify(editing.expression)) };
        return r;
      });
    }
    commitRules(newRules);
    message.success('保存成功');
    setDrawerOpen(false);
  };

  const oppositeRule = editing?.oppositeRuleId ? rules.find((r) => r.id === editing.oppositeRuleId) : undefined;
  // COMPOSITE 候选子规则：仅 FILTER（同对象优先）
  const filterCandidates = rules.filter((r) => r.ruleType === 'FILTER' && r.id !== editing?.id);
  const actions = (rule: BusinessRule) => readOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : <Space size={0}>
    <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(rule)}>编辑</Button>
    <Dropdown trigger={['click']} menu={{ items: [{ key: 'delete', label: '删除', danger: true }], onClick: () => Modal.confirm({ title: `删除规则“${rule.ruleName}”？`, okText: '删除', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => remove(rule.id) }) }}>
      <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${rule.ruleName}`} />
    </Dropdown>
  </Space>;
  const columns = [
    { title: '规则名称', dataIndex: 'ruleName', width: 260, render: (name: string, rule: BusinessRule) => <Space>{name}{rule.oppositeRuleId && <Tag icon={<LinkOutlined />} color="cyan">对立:{rules.find((item) => item.id === rule.oppositeRuleId)?.ruleName ?? '?'}</Tag>}</Space> },
    { title: '编码', dataIndex: 'ruleCode', width: 190, render: (code: string) => <code className="technical-code-wrap">{code}</code> },
    { title: '判定对象', dataIndex: 'targetObjectName', width: 210, render: (name: string, rule: BusinessRule) => {
      const kind = rule.targetObjectType as SemanticVisualKind;
      return name && ['METRIC', 'ENTITY', 'EVENT'].includes(kind) ? <Space size={6}><SemanticObjectTag kind={kind} />{name}</Space> : name;
    } },
    { title: '模式', dataIndex: 'ruleType', width: 130, render: (mode: RuleMode) => <Tag color={mode === 'COMPARISON' ? 'blue' : mode === 'FILTER' ? 'geekblue' : 'purple'}>{ruleModeLabel[mode]}</Tag> },
    { title: '触发词', dataIndex: 'triggerWords', width: 260, render: (words: string[]) => words.map((word) => <Tag key={word}>{word}</Tag>) },
    { title: '状态', dataIndex: 'status', width: 100, render: (status: string) => <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{ruleStatusLabel[status] ?? '待确认'}</Tag> },
    { title: '操作', width: 132, fixed: 'right' as const, render: (_: unknown, rule: BusinessRule) => actions(rule) },
  ];

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        <Select value={filterMode} onChange={setFilterMode} style={{ width: 160 }} options={[{ value: 'ALL', label: '全部模式' }, ...(['COMPARISON', 'FILTER', 'COMPOSITE'] as RuleMode[]).map((m) => ({ value: m, label: ruleModeLabel[m] }))]} />
        <Select value={filterObj} onChange={setFilterObj} style={{ width: 160 }} options={[{ value: 'ALL', label: '全部对象类型' }, ...(['METRIC', 'ENTITY', 'EVENT'] as TargetObjectType[]).map((t) => ({ value: t, label: targetObjectLabel(t) }))]} />
        {!readOnly && <Button type="primary" icon={<PlusOutlined />} onClick={openNew}>新建规则</Button>}
      </Space>

      <ResponsiveDataView
        ariaLabel="业务规则列表"
        rowKey="id"
        dataSource={shown}
        pagination={{ pageSize: 20, showSizeChanger: false }}
        columns={columns}
        minTableWidth={1282}
        renderCard={(rule) => <ResponsiveDataCard
          title={rule.ruleName}
          subtitle={<code>{rule.ruleCode}</code>}
          status={<Tag color={rule.status === 'ACTIVE' ? 'green' : 'default'}>{ruleStatusLabel[rule.status] ?? rule.status}</Tag>}
          fields={[
            { label: '判定对象', value: rule.targetObjectName || '—' },
            { label: '模式', value: <Tag color={rule.ruleType === 'COMPARISON' ? 'blue' : rule.ruleType === 'FILTER' ? 'geekblue' : 'purple'}>{ruleModeLabel[rule.ruleType]}</Tag> },
            { label: '触发词', value: rule.triggerWords.length > 0 ? rule.triggerWords.map((word) => <Tag key={word}>{word}</Tag>) : '—', wide: true },
            ...(rule.oppositeRuleId ? [{ label: '对立规则', value: rules.find((item) => item.id === rule.oppositeRuleId)?.ruleName ?? '?', wide: true }] : []),
          ]}
          actions={actions(rule)}
        />}
      />

      <Drawer title={editing?.id === 0 ? '新建规则' : `编辑规则：${editing?.ruleName}`} open={drawerOpen} onClose={() => setDrawerOpen(false)} width="min(720px, 100vw)" footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setDrawerOpen(false)}>取消</Button><Button type="primary" onClick={save}>保存</Button></Space>}>
        {editing && (
          <Form layout="vertical">
            <Space style={{ width: '100%' }}>
              <Form.Item label="规则名称" required style={{ flex: 1 }}>
                <Input value={editing.ruleName} onChange={(e) => setField('ruleName', e.target.value)} />
              </Form.Item>
              <Form.Item label="规则编码（技术标识）" required style={{ flex: 1 }}>
                <Input value={editing.ruleCode} onChange={(e) => setField('ruleCode', e.target.value)} disabled={editing.id !== 0} />
              </Form.Item>
            </Space>

            <Alert type="info" showIcon style={{ marginBottom: 12 }}
              message="先选择规则想判断什么，再选择它影响的业务对象；页面会自动收敛可用选项。" />
            <Space style={{ width: '100%' }}>
              <Form.Item label="判定模式" required style={{ flex: 1 }}>
                <Select value={editing.ruleType || undefined} placeholder="选择模式" onChange={onModeChange} options={allowedModeOptions.map((m) => ({ value: m, label: ruleModeLabel[m] }))} />
              </Form.Item>
              {showObjectSelectors && (
                <Form.Item label="判定对象类型" required style={{ flex: 1 }}>
                  <Select value={editing.targetObjectType || undefined} placeholder="选择对象类型" onChange={onObjTypeChange} options={allowedObjOptions.map((t) => ({ value: t, label: targetObjectLabel(t) }))} />
                </Form.Item>
              )}
            </Space>
            {showObjectSelectors && (
              <Form.Item label="判定对象" required>
                <Select value={editing.targetObjectId || undefined} placeholder="先选对象类型" onChange={onTargetObjectChange} options={targetObjectOptions(editing.targetObjectType)} />
              </Form.Item>
            )}
            {editing.ruleType === 'COMPOSITE' && (
              <Alert type="info" showIcon message="组合规则会从所选的对象筛选规则中自动带出影响对象。" />
            )}

            {editing.ruleType === 'COMPARISON' && <ComparisonForm editing={editing} setField={setField} />}
            {editing.ruleType === 'FILTER' && (
              <Form.Item label={`过滤条件（${editing.targetObjectName} 的自身属性）`}>
                <AttributeConditionBuilder
                  value={(editing.expression as CaliberFilter) ?? { logic: 'AND', conditions: [] }}
                  attributes={(editing.targetObjectType === 'ENTITY' ? entities : events)
                    .find((object) => object.id === editing.targetObjectId)?.attributes.filter((attribute) => attribute.isFilterable) ?? []}
                  onChange={(v) => setField('expression', v)}
                />
                <TechnicalDisclosure><p>对象筛选仅使用当前对象自身属性。跨对象判断需要先在语义模型中建立可追溯的映射。</p></TechnicalDisclosure>
              </Form.Item>
            )}
            {editing.ruleType === 'COMPOSITE' && <CompositeForm editing={editing} setField={setField} candidates={filterCandidates} />}

            <Form.Item label="触发词">
              <TriggerWordsInput value={editing.triggerWords} onChange={(v) => setField('triggerWords', v)} />
            </Form.Item>
            <Form.Item label="状态">
              <Switch checked={editing.status === 'ACTIVE'} onChange={(v) => setField('status', v ? 'ACTIVE' : 'INACTIVE')} /> {ruleStatusLabel[editing.status] ?? editing.status}
            </Form.Item>

            {editing.ruleType === 'COMPARISON' && (
              <div style={{ borderTop: '1px solid #f0f0f0', marginTop: 16, paddingTop: 12 }}>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>对立规则（自动生成）</div>
                {oppositeRule ? (
                  <Alert type="success" showIcon message={`已绑定对立规则：${oppositeRule.ruleName}。修改正向判断条件时会同步更新。`} />
                ) : (
                  <>
                    <Checkbox checked={genOpposite} onChange={(e) => setGenOpposite(e.target.checked)}>保存时自动生成对立规则</Checkbox>
                    {genOpposite && (
                      <Space style={{ width: '100%', marginTop: 8 }} direction="vertical">
                        <Space><Input placeholder="对立规则名称（如未达标）" value={oppName} onChange={(e) => setOppName(e.target.value)} style={{ width: 200 }} /><Input placeholder="对立规则编码" value={oppCode} onChange={(e) => setOppCode(e.target.value)} style={{ width: 200 }} /></Space>
                        <div><span style={{ marginRight: 8 }}>对立规则触发词：</span><TriggerWordsInput value={oppTriggers} onChange={setOppTriggers} /></div>
                        <TechnicalDisclosure><p>系统复制正向判断结构，并用互相关联的规则标识保存正反关系。</p></TechnicalDisclosure>
                      </Space>
                    )}
                  </>
                )}
              </div>
            )}
          </Form>
        )}

        {issues.length > 0 && (
          <div style={{ marginTop: 16 }}>
            {issues.map((iss, i) => (
              <Alert key={i} type={iss.severity === 'BLOCKER' ? 'error' : 'warning'} showIcon style={{ marginBottom: 8 }}
                message={<><Tag color={severityColor(iss.severity)}>{iss.severity === 'BLOCKER' ? '必须修正' : '请确认'}</Tag>{iss.message}</>} />
            ))}
          </div>
        )}
      </Drawer>
    </>
  );
}

/* ============ COMPARISON 子表单 ============ */
function ComparisonForm({ editing, setField }: { editing: BusinessRule; setField: <K extends keyof BusinessRule>(k: K, v: BusinessRule[K]) => void }) {
  const e = editing.expression as ComparisonExpression;
  const upd = (patch: Partial<ComparisonExpression>) => setField('expression', { ...e, ...patch } as BusinessRule['expression']);
  return (
    <>
      <Space style={{ width: '100%' }}>
        <Form.Item label="比较运算符" required>
          <Select value={e.operator} onChange={(v) => upd({ operator: v })} style={{ width: 120 }} options={OPS.map((o) => ({ value: o, label: o }))} />
        </Form.Item>
        <Form.Item label="达成比例（默认 1）">
          <InputNumber value={e.ratio} onChange={(v) => upd({ ratio: v ?? undefined })} step={0.05} style={{ width: 160 }} placeholder="默认 1" />
        </Form.Item>
      </Space>
      <Alert type="info" showIcon message="阈值来自该指标的目标值配置，并按指标、业务维度和时间周期自动匹配。" />
    </>
  );
}

/* ============ COMPOSITE 子表单（只组合 FILTER） ============ */
function CompositeForm({ editing, setField, candidates }: { editing: BusinessRule; setField: <K extends keyof BusinessRule>(k: K, v: BusinessRule[K]) => void; candidates: BusinessRule[] }) {
  const comps = editing.components ?? [];

  // 候选：所有 FILTER，排除已选（每个子规则互不相同）
  const candFor = (idx: number) => {
    const selectedIds = comps.filter((_, j) => j !== idx).map((c) => c.subRuleId).filter(Boolean);
    return candidates.filter((r) => !selectedIds.includes(r.id));
  };

  // 首个选中规则带出主判定对象（用于规则记录的判定对象展示）
  const derivePrimary = (next: CompositeComponent[]) => {
    const firstWithRule = next.find((c) => c.subRuleId);
    if (firstWithRule) {
      const r = candidates.find((x) => x.id === firstWithRule.subRuleId);
      if (r) {
        setField('targetObjectType', r.targetObjectType);
        setField('targetObjectId', r.targetObjectId);
        setField('targetObjectName', r.targetObjectName);
      }
    } else {
      setField('targetObjectType', '' as TargetObjectType);
      setField('targetObjectId', 0);
      setField('targetObjectName', '');
    }
  };

  const addComp = () => setField('components', [...comps, { subRuleId: 0, isNegated: false, sortOrder: comps.length }]);
  const updComp = (i: number, patch: Partial<CompositeComponent>) => {
    const next = comps.map((c, idx) => (idx === i ? { ...c, ...patch } : c));
    setField('components', next);
    derivePrimary(next);
  };
  const delComp = (i: number) => {
    const next = comps.filter((_, idx) => idx !== i);
    setField('components', next);
    derivePrimary(next);
  };

  return (
    <>
      <Form.Item label="逻辑运算符" required>
        <Select value={editing.logicOperator ?? 'AND'} onChange={(v: LogicOp) => setField('logicOperator', v)} style={{ width: 200 }} options={[{ value: 'AND', label: 'AND（全部满足）' }, { value: 'OR', label: 'OR（任一满足）' }]} />
      </Form.Item>
      <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 12 }}>
        {comps.map((c, i) => {
          const r = c.subRuleId ? candidates.find((x) => x.id === c.subRuleId) : undefined;
          return (
            <Space key={i} style={{ display: 'flex', marginBottom: 8 }} align="center">
              <Select value={c.subRuleId || undefined} placeholder="选择对象筛选规则" onChange={(v) => updComp(i, { subRuleId: v })} style={{ width: 200 }}
                options={candFor(i).map((rr) => ({ value: rr.id, label: rr.ruleName }))} />
              {r && ['METRIC', 'ENTITY', 'EVENT'].includes(r.targetObjectType)
                ? <SemanticObjectTag kind={r.targetObjectType as SemanticVisualKind} />
                : <Tag>对象类型</Tag>}
              <span style={{ color: '#595959', minWidth: 120, display: 'inline-block' }}>{r ? r.targetObjectName : '（未选规则）'}</span>
              <Button type="text" icon={<DeleteOutlined />} onClick={() => delComp(i)} />
            </Space>
          );
        })}
        <Button type="dashed" icon={<PlusOutlined />} size="small" onClick={addComp}>添加对象筛选规则</Button>
      </div>
      <Alert type="info" showIcon style={{ marginTop: 8 }} message="组合规则把多个对象筛选规则合并判断，每个子规则保留自己的业务对象。" />
    </>
  );
}
