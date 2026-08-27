import { useState, useMemo } from 'react';
import { Card, Button, Checkbox, Dropdown, Input, Select, Form, Drawer, Space, Tag, message, Tabs, Alert, Switch, InputNumber, Modal, Badge } from 'antd';
import { PlusOutlined, EditOutlined, CheckOutlined, MoreOutlined, WarningOutlined } from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { TermAlias, TargetType, AliasSource, ConflictStatus } from '../../types';
import { useLinguanWorkspace } from '../../features/ai-modeling/workspace-context';
import { SemanticObjectTag, type SemanticVisualKind } from '../../components/semantic-object-visuals';
import ResponsiveDataView, { ResponsiveDataCard } from '../../components/ResponsiveDataView';

type AliasTargetType = Exclude<TargetType, 'VALUE' | 'TERM'>;

const targetTypeLabels: Record<AliasTargetType, string> = { METRIC: '指标', ENTITY: '实体', EVENT: '事件', ATTRIBUTE: '属性', DIMENSION: '维度', RULE: '规则候选', TIME_RULE: '时间规则' };
const availableTargetTypes = Object.keys(targetTypeLabels) as AliasTargetType[];
const sourceLabels: Record<AliasSource, string> = { MANUAL: '人工创建', DISCOVERY: '自动发现', UNKNOWN_TERM: '未知词', IMPORT: '批量导入' };

export default function AliasPage() {
  const { model, semanticSidecar, workspaceReadOnly } = useLinguanWorkspace();
  const { aliases, metrics, entities, events, rules, addAlias, updateAlias, deleteAlias, batchConfirmAliases, unknownTerms, mapUnknownTerm, ignoreUnknownTerm } = useStore();
  const [tab, setTab] = useState('aliases');
  const [filterType, setFilterType] = useState<AliasTargetType | ''>('');
  const [filterConfirmed, setFilterConfirmed] = useState<'all' | 'confirmed' | 'unconfirmed'>('all');
  const [search, setSearch] = useState('');
  const [selectedKeys, setSelectedKeys] = useState<number[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<TermAlias | null>(null);
  const [form] = Form.useForm();
  const [mapOpen, setMapOpen] = useState(false);
  const [mapTerm, setMapTerm] = useState<number | null>(null);
  const [mapForm] = Form.useForm();

  const targetType = Form.useWatch('targetType', form);
  const mapTargetType = Form.useWatch('targetType', mapForm);
  const aliasText = Form.useWatch('aliasText', form);

  const filtered = useMemo(() => {
    let list = aliases.filter((a) => availableTargetTypes.includes(a.targetType as AliasTargetType));
    if (filterType) list = list.filter((a) => a.targetType === filterType);
    if (filterConfirmed === 'confirmed') list = list.filter((a) => a.isConfirmed);
    if (filterConfirmed === 'unconfirmed') list = list.filter((a) => !a.isConfirmed);
    if (search) list = list.filter((a) => a.aliasText.includes(search) || a.targetName.includes(search));
    return list;
  }, [aliases, filterType, filterConfirmed, search]);

  const conflictCheck = useMemo(() => {
    if (!aliasText || !targetType) return null;
    const conflicts = aliases.filter((a) => a.aliasText === aliasText && a.targetType === targetType && a.targetId !== editing?.targetId);
    return conflicts.length > 0 ? conflicts : null;
  }, [aliasText, targetType, aliases, editing]);

  const buildTargetOptions = (selectedType: AliasTargetType | undefined) => {
    if (!selectedType) return [];
    switch (selectedType) {
      case 'METRIC': return metrics.map((m) => ({ value: m.id, label: `${m.metricName} (${m.metricCode})`, code: m.metricCode, name: m.metricName }));
      case 'ENTITY': return entities.map((e) => ({ value: e.id, label: `${e.entityName} (${e.entityCode})`, code: e.entityCode, name: e.entityName }));
      case 'EVENT': return events.map((e) => ({ value: e.id, label: `${e.eventName} (${e.eventCode})`, code: e.eventCode, name: e.eventName }));
      case 'ATTRIBUTE': return [...entities, ...events].flatMap((object) => object.attributes.map((attribute) => ({ value: attribute.id, label: `${'entityName' in object ? object.entityName : object.eventName}.${attribute.attributeName} (${attribute.attributeCode})`, code: attribute.attributeCode, name: attribute.attributeName })));
      case 'DIMENSION': return (model?.dimensions ?? semanticSidecar?.dimensions ?? []).flatMap((dimension) => {
        const record = dimension as unknown as Record<string, unknown>;
        const code = String(record.id ?? record.code ?? ''); const name = String(record.name ?? '');
        return code && name ? [{ value: Math.abs([...code].reduce((hash, char) => Math.imul(hash ^ char.charCodeAt(0), 16777619), 2166136261)) || 1, label: `${name} (${code})`, code, name }] : [];
      });
      case 'RULE': return (model?.ruleCandidates ?? semanticSidecar?.ruleCandidates ?? []).flatMap((rule) => {
        const record = rule as unknown as Record<string, unknown>;
        const code = String(record.id ?? record.code ?? ''); const name = String(record.name ?? '');
        return code && name ? [{ value: Math.abs([...code].reduce((hash, char) => Math.imul(hash ^ char.charCodeAt(0), 16777619), 2166136261)) || 1, label: `${name} (${code})`, code, name }] : [];
      });
      case 'TIME_RULE': return rules.map((r) => ({ value: r.id, label: `${r.ruleText} (${r.ruleCode})`, code: r.ruleCode, name: r.ruleText }));
      default: return [];
    }
  };
  const targetOptions = useMemo(() => buildTargetOptions(targetType), [targetType, metrics, entities, events, rules, model, semanticSidecar]);
  const mapTargetOptions = useMemo(() => buildTargetOptions(mapTargetType), [mapTargetType, metrics, entities, events, rules, model, semanticSidecar]);

  const handleNew = () => { setEditing(null); form.resetFields(); form.setFieldsValue({ priority: 0, isConfirmed: true }); setOpen(true); };
  const handleEdit = (r: TermAlias) => { setEditing(r); form.setFieldsValue(r); setOpen(true); };
  const handleSave = () => {
    form.validateFields().then((values) => {
      const opt = targetOptions.find((o) => o.value === values.targetId);
      const alias = { ...values, targetCode: opt?.code || '', targetName: opt?.name || '', source: editing?.source || 'MANUAL', conflictStatus: 'NONE' as ConflictStatus };
      if (editing) { updateAlias(editing.id, alias); message.success('更新成功'); }
      else {
        const issues = addAlias(alias as TermAlias);
        if (issues.some((i) => i.severity === 'BLOCKER')) { message.error(issues[0].message); return; }
        if (issues.length > 0) message.warning(issues.map((i) => i.message).join('; '));
        else message.success('创建成功');
      }
      setOpen(false);
    });
  };

  const handleBatchConfirm = () => { batchConfirmAliases(selectedKeys); message.success(`已确认 ${selectedKeys.length} 条`); setSelectedKeys([]); };

  const handleMap = (id: number) => { setMapTerm(id); mapForm.resetFields(); mapForm.setFieldsValue({ targetType: 'METRIC' }); setMapOpen(true); };
  const handleMapSave = () => {
    mapForm.validateFields().then((values) => {
      const ut = unknownTerms.find((u) => u.id === mapTerm);
      if (!ut) return;
      const opt = mapTargetOptions.find((o) => o.value === values.targetId);
      mapUnknownTerm(ut.id, {
        id: 0, aliasText: ut.termText, targetType: values.targetType, targetId: values.targetId,
        targetCode: opt?.code || '', targetName: opt?.name || '', priority: 0,
        source: 'UNKNOWN_TERM', isConfirmed: true, conflictStatus: 'NONE',
      });
      message.success(`已将"${ut.termText}"映射为同义词`);
      setMapOpen(false);
    });
  };

  const columns = [
    { title: '同义词', dataIndex: 'aliasText', key: 'text', width: 240, render: (v: string, r: TermAlias) => (
      <span>{v} {!r.isConfirmed && <Tag color="orange" style={{ marginLeft: 4 }}>未确认</Tag>} {r.conflictStatus === 'CONFLICTED' && <WarningOutlined style={{ color: '#faad14' }} />}</span>
    )},
    { title: '目标类型', dataIndex: 'targetType', key: 'type', width: 150, render: (v: TargetType) => {
      const semanticKind = ({ METRIC: 'METRIC', ENTITY: 'ENTITY', EVENT: 'EVENT', ATTRIBUTE: 'FIELD', DIMENSION: 'DIMENSION' } as Partial<Record<TargetType, SemanticVisualKind>>)[v];
      return semanticKind ? <SemanticObjectTag kind={semanticKind} label={targetTypeLabels[v as AliasTargetType]} /> : <Tag>{targetTypeLabels[v as AliasTargetType]}</Tag>;
    } },
    { title: '目标对象', dataIndex: 'targetName', key: 'target', width: 260 },
    { title: '来源', dataIndex: 'source', key: 'source', width: 140, render: (v: AliasSource) => <span style={{ fontSize: 12, color: '#888' }}>{sourceLabels[v]}</span> },
    { title: '操作', key: 'action', width: 126, render: (_: unknown, r: TermAlias) => workspaceReadOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : (
      <Space size={0}>
        {!r.isConfirmed
          ? <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => { updateAlias(r.id, { isConfirmed: true }); message.success('已确认'); }}>确认</Button>
          : <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(r)}>编辑</Button>}
        <Dropdown trigger={['click']} menu={{ items: [
          ...(!r.isConfirmed ? [{ key: 'edit', label: '编辑' }] : []),
          { type: 'divider' as const }, { key: 'delete', label: '删除', danger: true },
        ], onClick: ({ key }) => {
          if (key === 'edit') handleEdit(r);
          if (key === 'delete') Modal.confirm({ title: `删除同义词“${r.aliasText}”？`, okText: '删除', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => { deleteAlias(r.id); message.success('已删除'); } });
        } }}><Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${r.aliasText}`} /></Dropdown>
      </Space>
    )},
  ];

  const unknownColumns = [
    { title: '词面文本', dataIndex: 'termText', key: 'text', width: 260 },
    { title: '出现次数', dataIndex: 'occurrenceCount', key: 'count', width: 120, sorter: (a: { occurrenceCount: number }, b: { occurrenceCount: number }) => a.occurrenceCount - b.occurrenceCount },
    { title: '最后出现', dataIndex: 'lastOccurredAt', key: 'last', width: 180 },
    { title: '状态', dataIndex: 'resolutionStatus', key: 'status', width: 120, render: (v: string) => ({ OPEN: <Badge status="warning" text="待处理" />, RESOLVED: <Badge status="success" text="已映射" />, IGNORED: <Badge status="default" text="已忽略" /> }[v]) },
    { title: '操作', key: 'action', width: 126, render: (_: unknown, r: { id: number; resolutionStatus: string; termText?: string }) => workspaceReadOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : r.resolutionStatus === 'OPEN' ? (
      <Space size={0}>
        <Button size="small" type="primary" onClick={() => handleMap(r.id)}>映射</Button>
        <Dropdown trigger={['click']} menu={{ items: [{ key: 'ignore', label: '忽略' }], onClick: () => Modal.confirm({ title: `忽略“${r.termText || '该词'}”？`, okText: '忽略', cancelText: '取消', onOk: () => { ignoreUnknownTerm(r.id); message.success('已忽略'); } }) }}>
          <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${r.termText || '未知词'}`} />
        </Dropdown>
      </Space>
    ) : <span style={{ color: '#999' }}>—</span> },
  ];

  return (
    <Card title="同义词管理" style={{ height: '100%' }}>
      <Tabs activeKey={tab} onChange={setTab} items={[
        { key: 'aliases', label: `同义词 (${aliases.length})`, children: (
          <>
            <Space style={{ marginBottom: 16 }} wrap>
              {!workspaceReadOnly && <Button type="primary" icon={<PlusOutlined />} onClick={handleNew}>新建同义词</Button>}
              <Input.Search placeholder="搜索同义词或目标对象" allowClear style={{ width: 240 }} onSearch={setSearch} onChange={(e) => !e.target.value && setSearch('')} />
              <Select style={{ width: 120 }} value={filterType} onChange={setFilterType} options={[{ value: '', label: '全部类型' }, ...Object.entries(targetTypeLabels).map(([v, l]) => ({ value: v, label: l }))]} />
              <Select style={{ width: 120 }} value={filterConfirmed} onChange={setFilterConfirmed} options={[{ value: 'all', label: '全部' }, { value: 'confirmed', label: '已确认' }, { value: 'unconfirmed', label: '未确认' }]} />
              {!workspaceReadOnly && selectedKeys.length > 0 && <Button onClick={handleBatchConfirm}>批量确认 ({selectedKeys.length})</Button>}
            </Space>
            <ResponsiveDataView
              ariaLabel="同义词列表"
              dataSource={filtered}
              columns={columns}
              rowKey="id"
              minTableWidth={1050}
              mobilePageSize={20}
              pagination={{ pageSize: 20, showSizeChanger: false }}
              tableProps={{ size: 'small', rowSelection: workspaceReadOnly ? undefined : { selectedRowKeys: selectedKeys, onChange: (keys) => setSelectedKeys(keys as number[]) } }}
              renderCard={(alias) => <ResponsiveDataCard
                title={alias.aliasText}
                subtitle={alias.targetCode ? <code>{alias.targetCode}</code> : undefined}
                status={<Space size={4}>{alias.isConfirmed ? <Tag color="green">已确认</Tag> : <Tag color="orange">未确认</Tag>}{alias.conflictStatus === 'CONFLICTED' && <Tag color="warning" icon={<WarningOutlined />}>冲突</Tag>}</Space>}
                fields={[
                  { label: '目标类型', value: targetTypeLabels[alias.targetType as AliasTargetType] },
                  { label: '来源', value: sourceLabels[alias.source] },
                  { label: '目标对象', value: alias.targetName, wide: true },
                ]}
                actions={<Space size={4} wrap>
                  {!workspaceReadOnly && <Checkbox checked={selectedKeys.includes(alias.id)} onChange={(event) => setSelectedKeys((keys) => event.target.checked ? [...keys, alias.id] : keys.filter((key) => key !== alias.id))}>选择</Checkbox>}
                  {workspaceReadOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : !alias.isConfirmed
                    ? <Button size="small" type="primary" onClick={() => { updateAlias(alias.id, { isConfirmed: true }); message.success('已确认'); }}>确认</Button>
                    : <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(alias)}>编辑</Button>}
                  {!workspaceReadOnly && <Dropdown trigger={['click']} menu={{ items: [
                    ...(!alias.isConfirmed ? [{ key: 'edit', label: '编辑' }] : []),
                    { type: 'divider' as const }, { key: 'delete', label: '删除', danger: true },
                  ], onClick: ({ key }) => {
                    if (key === 'edit') handleEdit(alias);
                    if (key === 'delete') Modal.confirm({ title: `删除同义词“${alias.aliasText}”？`, okText: '删除', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => { deleteAlias(alias.id); message.success('已删除'); } });
                  } }}><Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${alias.aliasText}`} /></Dropdown>}
                </Space>}
              />}
            />
          </>
        )},
        { key: 'unknown', label: <span>未识别词 <Badge count={unknownTerms.filter((u) => u.resolutionStatus === 'OPEN').length} size="small" style={{ marginLeft: 4 }} /></span>, children: (
          <ResponsiveDataView
            ariaLabel="未识别词列表"
            dataSource={unknownTerms}
            columns={unknownColumns}
            rowKey="id"
            minTableWidth={810}
            mobilePageSize={20}
            pagination={{ pageSize: 20, showSizeChanger: false }}
            tableProps={{ size: 'small' }}
            renderCard={(term) => <ResponsiveDataCard
              title={term.termText}
              status={({ OPEN: <Badge status="warning" text="待处理" />, RESOLVED: <Badge status="success" text="已映射" />, IGNORED: <Badge status="default" text="已忽略" /> } as Record<string, React.ReactNode>)[term.resolutionStatus]}
              fields={[{ label: '出现次数', value: term.occurrenceCount }, { label: '最后出现', value: term.lastOccurredAt }]}
              actions={workspaceReadOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : term.resolutionStatus === 'OPEN' ? <Space size={4}>
                <Button size="small" type="primary" onClick={() => handleMap(term.id)}>映射</Button>
                <Dropdown trigger={['click']} menu={{ items: [{ key: 'ignore', label: '忽略' }], onClick: () => Modal.confirm({ title: `忽略“${term.termText}”？`, okText: '忽略', cancelText: '取消', onOk: () => { ignoreUnknownTerm(term.id); message.success('已忽略'); } }) }}>
                  <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${term.termText}`} />
                </Dropdown>
              </Space> : undefined}
            />}
          />
        )},
      ]} />

      <Drawer title={editing ? '编辑同义词' : '新建同义词'} width="min(480px, 100vw)" open={open} onClose={() => setOpen(false)}
        footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setOpen(false)}>取消</Button><Button type="primary" onClick={handleSave}>保存</Button></Space>}>
        <Form form={form} layout="vertical">
          <Form.Item name="aliasText" label="同义词文本" rules={[{ required: true }]} extra="用户可能输入的词">
            <Input placeholder="如：成交额、大区、今年" />
          </Form.Item>
          <Form.Item name="targetType" label="目标类型" rules={[{ required: true }]}>
            <Select placeholder="选择目标类型" options={Object.entries(targetTypeLabels).map(([v, l]) => ({ value: v, label: l }))} onChange={() => form.setFieldValue('targetId', undefined)} />
          </Form.Item>
          <Form.Item name="targetId" label="目标对象" rules={[{ required: true }]}>
            <Select placeholder="选择目标对象" showSearch optionFilterProp="label" options={targetOptions} disabled={!targetType} />
          </Form.Item>

          {conflictCheck && (
            <Alert type="warning" showIcon icon={<WarningOutlined />} style={{ marginBottom: 16 }}
              message={`"${aliasText}" 已指向以下 ${targetTypeLabels[targetType as AliasTargetType]}：`}
              description={conflictCheck.map((c) => `${c.targetName}（优先级 ${c.priority}）`).join('、')} />
          )}

          <Form.Item name="priority" label="优先级"><InputNumber style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="isConfirmed" label="是否确认" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Drawer>

      <Drawer title={`映射"${unknownTerms.find((u) => u.id === mapTerm)?.termText}"为同义词`} width="min(400px, 100vw)" open={mapOpen} onClose={() => setMapOpen(false)}
        footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setMapOpen(false)}>取消</Button><Button type="primary" onClick={handleMapSave}>确认映射</Button></Space>}>
        <Form form={mapForm} layout="vertical">
          <Form.Item name="targetType" label="目标类型" rules={[{ required: true }]}>
            <Select options={Object.entries(targetTypeLabels).map(([v, l]) => ({ value: v, label: l }))} onChange={() => mapForm.setFieldValue('targetId', undefined)} />
          </Form.Item>
          <Form.Item name="targetId" label="目标对象" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={mapTargetOptions} disabled={!mapTargetType} />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
}
