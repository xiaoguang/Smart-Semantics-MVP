import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Drawer, Empty, Form, Input, Space, Table, Tabs, Tag, Typography, message } from 'antd';
import { EditOutlined, LockOutlined, PlusOutlined } from '@ant-design/icons';
import { useLinguanWorkspace } from './workspace-context';
import type { LinguanMetric, LinguanTable } from './types.ts';
import { SemanticObjectTag } from '../../components/semantic-object-visuals.ts';

const cardinalityLabels: Record<string, string> = {
  many_to_one: '多对一', one_to_many: '一对多', one_to_one: '一对一', many_to_many: '多对多',
};

function NoPublishedModel() {
  return <Card><Empty description="当前系统尚未发布语义模型，请先在 AI 建模中完成审核与发布。" /></Card>;
}

function ReadOnlyNotice({ readOnly }: { readOnly: boolean }) {
  return readOnly ? <Alert type="info" showIcon icon={<LockOutlined />} message="正在查看历史模型；该版本及当时的人工修改已冻结。" style={{ marginBottom: 12 }} /> : null;
}

export function DigitalOntologyPage() {
  const { model } = useLinguanWorkspace();
  if (!model) return <NoPublishedModel />;
  return (
    <Card title="本体建模" style={{ height: '100%' }}>
      <ReadOnlyNotice readOnly={model.readOnly} />
      <Tabs items={[
        { key: 'objects', label: '本体列表', children: <Table
          size="small" rowKey="code" dataSource={model.tables} pagination={false}
          expandable={{ expandedRowRender: (table: LinguanTable) => <Table
            size="small" rowKey="code" pagination={false} dataSource={table.fields}
            columns={[
              { title: '字段', dataIndex: 'name' }, { title: '字段编码', dataIndex: 'code' },
              { title: '角色', dataIndex: 'role' }, { title: '类型', dataIndex: 'semanticType' },
              { title: '业务含义', dataIndex: 'description' },
            ]}
          /> }}
          columns={[
            { title: '本体名称', dataIndex: 'name' }, { title: '编码', dataIndex: 'code' },
            { title: '类型', dataIndex: 'kind', render: (value: 'ENTITY' | 'EVENT') => <SemanticObjectTag kind={value} /> },
            { title: '业务定义', dataIndex: 'description' },
            { title: '字段', dataIndex: 'fields', render: (fields: unknown[]) => `${fields.length} 个` },
          ]}
        /> },
        { key: 'relations', label: '本体关系', children: <Table
          size="small" rowKey="code" dataSource={model.relations} pagination={false}
          columns={[
            { title: '关系', dataIndex: 'name' },
            { title: '起点', render: (_: unknown, row) => `${row.from.tableName}.${row.from.fieldName}` },
            { title: '基数', dataIndex: 'cardinality', render: (value: string) => cardinalityLabels[value] ?? value },
            { title: '终点', render: (_: unknown, row) => `${row.to.tableName}.${row.to.fieldName}` },
            { title: '来源', dataIndex: 'evidenceIds', render: (items: string[]) => `${items.length} 处` },
          ]}
        /> },
      ]} />
    </Card>
  );
}

export function DigitalMetricPage() {
  const { model, copies, refreshModel } = useLinguanWorkspace();
  const [editing, setEditing] = useState<LinguanMetric | null>(null);
  const [form] = Form.useForm();
  if (!model) return <NoPublishedModel />;
  const openEdit = (metric: LinguanMetric) => {
    setEditing(metric);
    form.setFieldsValue(metric);
  };
  const save = async () => {
    if (!editing || !model) return;
    const values = await form.validateFields();
    copies.updateMetric(model.systemCode, model.modelVersion, editing.code, values);
    refreshModel();
    setEditing(null);
    message.success('工作副本已更新');
  };
  return (
    <Card title="指标配置" style={{ height: '100%' }}>
      <ReadOnlyNotice readOnly={model.readOnly} />
      <Alert type="info" showIcon message="SUM / AVG 单字段指标可在当前工作副本中维护；复杂指标保留真实公式，只读展示。" style={{ marginBottom: 12 }} />
      <Table size="small" rowKey="code" dataSource={model.metrics} pagination={false} columns={[
        { title: '指标名称', dataIndex: 'name' }, { title: '编码', dataIndex: 'code' },
        { title: '所属事件', dataIndex: 'eventTableName' },
        { title: '聚合', dataIndex: 'aggregation' }, { title: '公式', dataIndex: 'formula' },
        { title: '兼容状态', render: (_: unknown, metric: LinguanMetric) => metric.editability === 'EDITABLE' ? <Tag color="green">可编辑</Tag> : <Tag>复杂口径·只读</Tag> },
        { title: '操作', render: (_: unknown, metric: LinguanMetric) => <Button size="small" icon={<EditOutlined />} disabled={model.readOnly || metric.editability === 'READ_ONLY'} onClick={() => openEdit(metric)}>编辑</Button> },
      ]} />
      <Drawer title={`编辑指标：${editing?.name ?? ''}`} size={480} open={Boolean(editing)} onClose={() => setEditing(null)} extra={<Button type="primary" onClick={save}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="指标名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="description" label="业务定义" rules={[{ required: true }]}><Input.TextArea rows={4} /></Form.Item>
          <Form.Item name="unit" label="单位"><Input /></Form.Item>
          <Descriptions size="small" column={1} items={[
            { key: 'aggregation', label: '聚合方式', children: editing?.aggregation },
            { key: 'formula', label: '真实公式', children: editing?.formula },
          ]} />
        </Form>
      </Drawer>
    </Card>
  );
}

export function DigitalRulePage() {
  const { model, copies, refreshModel } = useLinguanWorkspace();
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  if (!model) return <NoPublishedModel />;
  const addRule = async () => {
    if (!model) return;
    const values = await form.validateFields();
    copies.addManualRule(model.systemCode, model.modelVersion, values);
    refreshModel();
    setOpen(false);
    form.resetFields();
  };
  return (
    <Card title="业务规则" style={{ height: '100%' }}>
      <ReadOnlyNotice readOnly={model.readOnly} />
      <Tabs items={[
        { key: 'rules', label: `可执行规则 (${model.executableRules.length})`, children: <>
          <Button type="primary" icon={<PlusOutlined />} disabled={model.readOnly} onClick={() => setOpen(true)} style={{ marginBottom: 12 }}>新建人工规则</Button>
          {model.executableRules.length ? <Table rowKey="id" dataSource={model.executableRules} pagination={false} columns={[{ title: '规则名称', dataIndex: 'name' }, { title: '说明', dataIndex: 'description' }, { title: '来源', render: () => <Tag color="cyan">人工维护</Tag> }]} /> : <Empty description="设计资料没有生成可执行规则，可按需人工维护。" />}
        </> },
        { key: 'candidates', label: `待结构化候选 (${model.ruleCandidates.length})`, children: <>
          <Alert type="info" showIcon message="以下内容来自公式、业务键、粒度或基数，只作候选，不冒充可执行规则。" style={{ marginBottom: 12 }} />
          <Table size="small" rowKey="id" dataSource={model.ruleCandidates} pagination={{ pageSize: 12 }} columns={[
            { title: '候选名称', dataIndex: 'name' }, { title: '来源类型', dataIndex: 'sourceType' },
            { title: '内容', dataIndex: 'description' }, { title: '影响对象', dataIndex: 'target' },
            { title: '状态', render: () => <Tag>待结构化</Tag> },
          ]} />
        </> },
      ]} />
      <Drawer title="新建人工规则" size={460} open={open} onClose={() => setOpen(false)} extra={<Button type="primary" onClick={addRule}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="规则名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="description" label="规则说明" rules={[{ required: true }]}><Input.TextArea rows={5} /></Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
}

export function DigitalDeferredPage({ capability }: { capability: 'alias' | 'time' }) {
  const { model, copies, refreshModel } = useLinguanWorkspace();
  const [value, setValue] = useState('');
  const title = capability === 'alias' ? '同义词' : '时间语义';
  const description = capability === 'alias'
    ? '当前资料没有可靠的同义词来源，因此未自动生成。可人工补充，后续再接入 Java 产物。'
    : '当前资料没有日历和时间规则产物，因此未自动生成；默认模型空间原有能力保持不变。';
  if (!model) return <NoPublishedModel />;
  return <Card title={title} style={{ height: '100%' }}>
    <ReadOnlyNotice readOnly={model.readOnly} />
    <Alert type="info" showIcon message={description} style={{ marginBottom: 16 }} />
    {capability === 'alias' && <Space.Compact style={{ marginBottom: 12 }}>
      <Input value={value} placeholder="人工添加同义词" onChange={(event) => setValue(event.target.value)} disabled={model.readOnly} />
      <Button type="primary" disabled={model.readOnly || !value.trim()} onClick={() => { copies.addAlias(model.systemCode, model.modelVersion, value.trim()); refreshModel(); setValue(''); }}>添加</Button>
    </Space.Compact>}
    {model.aliases.length ? model.aliases.map((item) => <Tag key={item.id}>{item.text}</Tag>) : <Typography.Text type="secondary">暂无{title}数据</Typography.Text>}
  </Card>;
}
