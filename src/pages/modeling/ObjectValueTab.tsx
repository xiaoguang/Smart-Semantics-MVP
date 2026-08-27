import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert, Button, Descriptions, Drawer, Empty, Form, Input, InputNumber, Modal,
  Popconfirm, Select, Space, Switch, Table, Tag, Typography, message,
} from 'antd';
import {
  ApartmentOutlined, DeleteOutlined, EditOutlined,
  PlusOutlined, SyncOutlined,
} from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { ObjectAttribute, ObjectValue, RelationObjectType, SemanticEntity, SemanticEvent } from '../../types';
import { SemanticObjectTag } from '../../components/semantic-object-visuals';
import BackAction from '../../components/back-action.tsx';

const { Text } = Typography;
const valueSemanticTypes = ['ENUM', 'CODE', 'HIERARCHY', 'LIFECYCLE'] as const;

interface TreeValue extends ObjectValue {
  children?: TreeValue[];
}

interface ValueDefinition {
  attribute: ObjectAttribute;
  values: ObjectValue[];
  activeCount: number;
  sourceTypes: ObjectValue['sourceType'][];
  lastSyncedAt?: string;
}

function buildValueTree(values: ObjectValue[], keyword: string): TreeValue[] {
  const nodes = new Map(values.map((value) => [value.id, { ...value, children: [] } as TreeValue]));
  const roots: TreeValue[] = [];
  nodes.forEach((node) => {
    const parent = node.parentValueId ? nodes.get(node.parentValueId) : undefined;
    if (parent) parent.children!.push(node);
    else roots.push(node);
  });
  const sort = (items: TreeValue[]) => items.sort((a, b) => a.sortOrder - b.sortOrder || a.businessName.localeCompare(b.businessName)).forEach((item) => sort(item.children || []));
  sort(roots);
  if (!keyword.trim()) return roots;
  const q = keyword.trim().toLowerCase();
  const filter = (items: TreeValue[]): TreeValue[] => items.flatMap((item) => {
    const children = filter(item.children || []);
    const matched = item.businessName.toLowerCase().includes(q) || item.physicalCode.toLowerCase().includes(q);
    return matched || children.length ? [{ ...item, children }] : [];
  });
  return filter(roots);
}

const semanticTypeLabel: Record<string, string> = {
  ENUM: '枚举', CODE: '编码', HIERARCHY: '层级', LIFECYCLE: '生命周期',
};

const sourceTypeLabel: Record<ObjectValue['sourceType'], string> = {
  MANUAL: '手工', PHYSICAL_SYNC: '物理同步', API_SYNC: 'API同步',
};

export default function ObjectValueTab({ objectType, object, initialAttributeId, onOpenHierarchy, onOpenAttributes }: {
  objectType: RelationObjectType;
  object: SemanticEntity | SemanticEvent;
  initialAttributeId?: number;
  onOpenHierarchy: (attributeId: number) => void;
  onOpenAttributes: () => void;
}) {
  const { entityHierarchies, objectValues, fieldMappings, upsertObjectValue, deleteObjectValue } = useStore();
  const valueAttrs = useMemo(() => object.attributes.filter((attribute) => valueSemanticTypes.includes(attribute.semanticType as typeof valueSemanticTypes[number])), [object.attributes]);
  const scopedValues = useMemo(() => objectValues.filter((value) => value.objectType === objectType && value.objectId === object.id), [object.id, objectType, objectValues]);
  const definitions = useMemo<ValueDefinition[]>(() => valueAttrs.map((attribute) => {
    const values = scopedValues.filter((value) => value.attributeId === attribute.id);
    const sourceTypes = Array.from(new Set(values.map((value) => value.sourceType)));
    const lastSyncedAt = values.map((value) => value.lastSyncedAt).filter((time): time is string => !!time).sort().at(-1);
    return { attribute, values, activeCount: values.filter((value) => value.status === 'ACTIVE').length, sourceTypes, lastSyncedAt };
  }), [scopedValues, valueAttrs]);

  const [managedAttributeId, setManagedAttributeId] = useState<number>();
  const [listSearch, setListSearch] = useState('');
  const [listStatus, setListStatus] = useState<'ALL' | 'MAINTAINED' | 'EMPTY'>('ALL');
  const [search, setSearch] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<ObjectValue>();
  const [form] = Form.useForm();
  const selectedLevelId = Form.useWatch<number>('hierarchyLevelId', form);
  const handledShortcut = useRef('');

  useEffect(() => {
    setManagedAttributeId(undefined);
    setListSearch('');
    setListStatus('ALL');
    setSearch('');
    handledShortcut.current = '';
  }, [object.id, objectType]);

  useEffect(() => {
    if (!initialAttributeId || !valueAttrs.some((attribute) => attribute.id === initialAttributeId)) return;
    const shortcutKey = `${objectType}-${object.id}-${initialAttributeId}`;
    if (handledShortcut.current === shortcutKey) return;
    handledShortcut.current = shortcutKey;
    setManagedAttributeId(initialAttributeId);
    setSearch('');
  }, [initialAttributeId, object.id, objectType, valueAttrs]);

  const attribute = valueAttrs.find((item) => item.id === managedAttributeId);
  const isHierarchy = attribute?.semanticType === 'HIERARCHY';
  const levels = entityHierarchies.filter((level) => level.objectType === objectType && level.objectId === object.id && level.attributeId === managedAttributeId).sort((a, b) => a.levelDepth - b.levelDepth);
  const values = scopedValues.filter((value) => value.attributeId === managedAttributeId);
  const valueById = new Map(values.map((value) => [value.id, value]));
  const levelById = new Map(levels.map((level) => [level.id, level]));
  const hasFieldMapping = !!managedAttributeId && fieldMappings.some((mapping) => mapping.objectType === objectType && mapping.objectId === object.id && mapping.attributeId === managedAttributeId);
  const tableData = isHierarchy
    ? buildValueTree(values, search)
    : values.filter((value) => !search.trim() || value.businessName.toLowerCase().includes(search.trim().toLowerCase()) || value.physicalCode.toLowerCase().includes(search.trim().toLowerCase())).sort((a, b) => a.sortOrder - b.sortOrder);

  if (valueAttrs.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<Space direction="vertical"><Text>当前本体没有可维护值的属性</Text><Button type="primary" onClick={onOpenAttributes}>前往属性列表创建</Button></Space>} />;
  }

  const parentOptions = (() => {
    if (!isHierarchy || !selectedLevelId) return [];
    const level = levelById.get(selectedLevelId);
    if (!level?.parentLevelId) return [];
    return values
      .filter((value) => value.hierarchyLevelId === level.parentLevelId && value.id !== editing?.id)
      .map((value) => ({ value: value.id, label: `${value.businessName}（${value.physicalCode}）` }));
  })();

  const enterMaintenance = (attributeId: number) => {
    setManagedAttributeId(attributeId);
    setSearch('');
  };
  const openNew = (parent?: ObjectValue) => {
    setEditing(undefined);
    form.resetFields();
    const parentLevel = parent?.hierarchyLevelId ? levelById.get(parent.hierarchyLevelId) : undefined;
    const nextLevel = parentLevel ? levels.find((level) => level.parentLevelId === parentLevel.id) : levels[0];
    form.setFieldsValue({
      hierarchyLevelId: isHierarchy ? nextLevel?.id : undefined,
      parentValueId: isHierarchy ? parent?.id : undefined,
      sortOrder: parent ? values.filter((value) => value.parentValueId === parent.id).length * 10 + 10 : values.filter((value) => !value.parentValueId).length * 10 + 10,
      status: 'ACTIVE',
    });
    setDrawerOpen(true);
  };
  const openEdit = (value: ObjectValue) => {
    setEditing(value);
    form.setFieldsValue(value);
    setDrawerOpen(true);
  };
  const descendantCount = (id: number): number => {
    const children = values.filter((value) => value.parentValueId === id);
    return children.length + children.reduce((count, child) => count + descendantCount(child.id), 0);
  };
  const persist = (formValues: Partial<ObjectValue>) => {
    if (!managedAttributeId) return;
    const issues = upsertObjectValue({
      id: editing?.id,
      objectType,
      objectId: object.id,
      attributeId: managedAttributeId,
      hierarchyLevelId: isHierarchy ? formValues.hierarchyLevelId : undefined,
      parentValueId: isHierarchy ? formValues.parentValueId : undefined,
      businessName: formValues.businessName || '',
      physicalCode: formValues.physicalCode || '',
      sortOrder: formValues.sortOrder || 0,
      sourceType: editing?.sourceType || 'MANUAL',
      sourceKey: editing?.sourceKey,
      lastSyncedAt: editing?.lastSyncedAt,
      status: formValues.status || 'ACTIVE',
    });
    const blocker = issues.find((issue) => issue.severity === 'BLOCKER');
    if (blocker) {
      message.error(blocker.message);
      return;
    }
    const warning = issues.find((issue) => issue.severity === 'WARNING');
    if (warning) message.warning(warning.message);
    else message.success(editing ? '实体值已更新' : '实体值已创建');
    setDrawerOpen(false);
  };
  const save = () => {
    form.validateFields().then((formValues) => {
      const parentChanged = editing && editing.parentValueId !== formValues.parentValueId;
      if (isHierarchy && parentChanged) {
        const affected = descendantCount(editing.id);
        Modal.confirm({
          title: '确认调整父节点？',
          content: affected ? `该值下还有 ${affected} 个子孙节点，移动后整棵子树将一起调整。` : '该值的父节点将发生变化。',
          okText: '确认调整',
          onOk: () => persist(formValues),
        });
      } else persist(formValues);
    });
  };
  const remove = (value: ObjectValue) => {
    const issues = deleteObjectValue(value.id);
    if (issues.length) message.error(issues[0].message);
    else message.success('实体值已删除');
  };
  const levelTag = (levelId?: number) => {
    const level = levelById.get(levelId || 0);
    return level ? <Tag color="blue">{level.levelName}</Tag> : '-';
  };

  const valueColumns = [
    { title: '业务名称', dataIndex: 'businessName', key: 'name', render: (name: string, value: TreeValue) => <Space><Text strong>{name}</Text>{value.children && value.children.length > 0 && <Tag>{value.children.length} 个子值</Tag>}</Space> },
    { title: '物理编码', dataIndex: 'physicalCode', key: 'code', render: (code: string) => <Text code>{code}</Text> },
    ...(isHierarchy ? [{ title: '所属级别', dataIndex: 'hierarchyLevelId', key: 'level', width: 110, render: levelTag }] : []),
    { title: '来源', dataIndex: 'sourceType', key: 'source', width: 110, render: (source: ObjectValue['sourceType']) => <Tag color={source === 'MANUAL' ? 'default' : 'cyan'}>{sourceTypeLabel[source]}</Tag> },
    { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (status: ObjectValue['status']) => <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status === 'ACTIVE' ? '启用' : '停用'}</Tag> },
    { title: '操作', key: 'action', width: isHierarchy ? 220 : 150, render: (_: unknown, value: ObjectValue) => {
      const level = levelById.get(value.hierarchyLevelId || 0);
      const hasNextLevel = !!levels.find((item) => item.parentLevelId === level?.id);
      return <Space size={2}>
        {isHierarchy && hasNextLevel && <Button type="link" size="small" icon={<PlusOutlined />} onClick={() => openNew(value)}>添加子值</Button>}
        <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(value)}>编辑</Button>
        <Popconfirm title="确认删除该值？" onConfirm={() => remove(value)}><Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button></Popconfirm>
      </Space>;
    } },
  ];

  const shownDefinitions = definitions.filter((definition) => {
    const q = listSearch.trim().toLowerCase();
    const matchesKeyword = !q || definition.attribute.attributeName.toLowerCase().includes(q) || definition.attribute.attributeCode.toLowerCase().includes(q);
    const matchesStatus = listStatus === 'ALL' || (listStatus === 'MAINTAINED' ? definition.values.length > 0 : definition.values.length === 0);
    return matchesKeyword && matchesStatus;
  });

  if (!attribute) {
    return (
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Text type="secondary" className="definition-helper">值定义跟随属性维护；未维护的可直接开始，已有内容可继续补充。</Text>
        <div className="definition-toolbar">
          <Space>
            <Input.Search allowClear value={listSearch} onChange={(event) => setListSearch(event.target.value)} placeholder="搜索属性名称或编码" style={{ width: 300 }} />
            <Select value={listStatus} onChange={setListStatus} style={{ width: 130 }} options={[{ value: 'ALL', label: '全部状态' }, { value: 'MAINTAINED', label: '已维护' }, { value: 'EMPTY', label: '未维护' }]} />
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={onOpenAttributes}>新增值属性</Button>
        </div>
        {shownDefinitions.length ? <div className="value-definition-list">
          {shownDefinitions.map((definition) => {
            const hierarchyReady = definition.attribute.semanticType !== 'HIERARCHY' || entityHierarchies.some((level) => level.objectType === objectType && level.objectId === object.id && level.attributeId === definition.attribute.id);
            const hasDetails = definition.sourceTypes.length > 0 || !!definition.lastSyncedAt;
            return <article className="value-definition-item" key={definition.attribute.id}>
              <div className="value-definition-main">
                <div className="definition-identity value-definition-identity">
                  <Text strong>{definition.attribute.attributeName}</Text>
                  <span className="definition-technical-code" title={definition.attribute.attributeCode}>{definition.attribute.attributeCode}</span>
                </div>
                <div className="value-definition-tags">
                  <SemanticObjectTag kind="FIELD" role="DIMENSION" />
                  <Tag>{semanticTypeLabel[definition.attribute.semanticType] || definition.attribute.semanticType}</Tag>
                </div>
                <div className="value-definition-structure">
                  <Text type="secondary">值结构</Text>
                  {definition.attribute.semanticType === 'HIERARCHY'
                    ? <Tag icon={<ApartmentOutlined />}>层级值树</Tag>
                    : <Tag>平面值集</Tag>}
                </div>
                <div className="value-definition-count">
                  <span><strong>{definition.values.length}</strong> 个值</span>
                  <Text type="secondary">启用 {definition.activeCount}</Text>
                </div>
                <div className="value-definition-status">
                  {!hierarchyReady
                    ? <Tag color="orange">待配置层级</Tag>
                    : definition.values.length ? <Tag color="green">已维护</Tag> : <Tag>未维护</Tag>}
                </div>
                <Button className="value-definition-action" type="link" size="small" onClick={() => enterMaintenance(definition.attribute.id)}>{definition.values.length ? '维护值' : '开始维护'}</Button>
              </div>
              {hasDetails && <details className="value-definition-details">
                <summary>来源与同步信息</summary>
                <div className="value-definition-details-content">
                  {definition.sourceTypes.length > 0 && <div><Text type="secondary">数据来源</Text><Space size={4} wrap>{definition.sourceTypes.map((source) => <Tag key={source} color={source === 'MANUAL' ? 'default' : 'cyan'}>{sourceTypeLabel[source]}</Tag>)}</Space></div>}
                  {definition.lastSyncedAt && <div><Text type="secondary">最后同步</Text><span>{definition.lastSyncedAt}</span></div>}
                </div>
              </details>}
            </article>;
          })}
        </div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选条件下没有可维护值的属性" />}
      </Space>
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'center' }}>
        <Space>
          <BackAction destination="值定义列表" onBack={() => { setManagedAttributeId(undefined); setSearch(''); }} />
          <span style={{ width: 1, height: 20, background: '#e5e7eb' }} />
          <Space direction="vertical" size={0}>
            <Space><Text strong>{attribute.attributeName}</Text><SemanticObjectTag kind="FIELD" role="DIMENSION" /><Tag>{semanticTypeLabel[attribute.semanticType] || attribute.semanticType}</Tag><Tag>{isHierarchy ? '层级值树' : '平面值集'}</Tag></Space>
            <Text type="secondary" style={{ fontSize: 12 }}>{attribute.attributeCode}</Text>
          </Space>
        </Space>
        <Space>
          {isHierarchy && <Button icon={<SyncOutlined />} disabled title={hasFieldMapping ? '当前快照未保存可用于同步的脱敏值样例' : '请先完成物理字段映射'}>从物理表同步</Button>}
          <Button type="primary" icon={<PlusOutlined />} disabled={isHierarchy && levels.length === 0} onClick={() => openNew()}>{isHierarchy ? '新增根值' : '新增值'}</Button>
        </Space>
      </div>

      <Input.Search allowClear value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索业务名称或物理编码" style={{ width: 300 }} />
      {isHierarchy && levels.length === 0 && <Alert type="warning" showIcon message="该属性的语义类型为 HIERARCHY，但尚未定义层级，暂时不能新增层级值。" action={<Button size="small" onClick={() => onOpenHierarchy(attribute.id)}>立即配置层级</Button>} />}
      {isHierarchy && levels.length > 0 && <Alert type="info" showIcon message={<span>当前层级：{levels.map((level, index) => <span key={level.id}><Tag color="blue">{level.levelName}</Tag>{index < levels.length - 1 && '→ '}</span>)}</span>} />}
      {isHierarchy && !hasFieldMapping && <Alert type="warning" showIcon message="层级属性尚未完成物理字段映射，不能从数据源同步；手工维护实体值不受影响。" />}

      <Table
        key={`${managedAttributeId}-${isHierarchy ? 'tree' : 'flat'}`}
        dataSource={tableData}
        columns={valueColumns}
        rowKey="id"
        size="small"
        pagination={false}
        defaultExpandAllRows={isHierarchy}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={isHierarchy ? '尚未维护层级实体值' : '尚未维护属性值'} /> }}
      />

      <Drawer title={editing ? `编辑实体值：${editing.businessName}` : isHierarchy ? '新增层级实体值' : '新增属性值'} width="min(520px, 100vw)" open={drawerOpen} onClose={() => setDrawerOpen(false)} footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setDrawerOpen(false)}>取消</Button><Button type="primary" onClick={save}>保存</Button></Space>}>
        <Form form={form} layout="vertical">
          <Alert type="info" showIcon message={isHierarchy ? `当前为层级属性，值必须归属“${attribute.attributeName}”的某一级别，并按父值组织。` : `当前为${semanticTypeLabel[attribute.semanticType] || attribute.semanticType}属性，按平面值集维护，无需配置层级和父值。`} style={{ marginBottom: 16 }} />
          <Form.Item name="businessName" label="业务名称" rules={[{ required: true, whitespace: true, message: '请输入业务名称' }]}><Input placeholder="例如：江苏" /></Form.Item>
          <Form.Item name="physicalCode" label="物理编码" rules={[{ required: true, whitespace: true, message: '请输入物理编码' }]}><Input placeholder="例如：JS" /></Form.Item>
          {isHierarchy && <>
            <Form.Item name="hierarchyLevelId" label="所属级别" rules={[{ required: true, message: '请选择所属级别' }]}>
              <Select options={levels.filter((level) => level.status === 'ACTIVE').map((level) => ({ value: level.id, label: `${level.levelName}（${level.levelCode}）` }))} onChange={(levelId) => {
                const level = levelById.get(levelId);
                const currentParent = form.getFieldValue('parentValueId');
                if (!level?.parentLevelId || valueById.get(currentParent)?.hierarchyLevelId !== level.parentLevelId) form.setFieldValue('parentValueId', undefined);
              }} />
            </Form.Item>
            <Form.Item name="parentValueId" label="父值" rules={levelById.get(selectedLevelId || 0)?.levelDepth === 1 ? undefined : [{ required: true, message: '非根级值必须选择父值' }]} extra={levelById.get(selectedLevelId || 0)?.levelDepth === 1 ? '根级别无需选择父值。' : '仅展示合法上一级的值。'}>
              <Select allowClear disabled={!selectedLevelId || levelById.get(selectedLevelId)?.levelDepth === 1} options={parentOptions} showSearch optionFilterProp="label" onChange={(parentId) => {
                const parent = valueById.get(parentId);
                const recommended = levels.find((level) => level.parentLevelId === parent?.hierarchyLevelId);
                if (recommended) form.setFieldValue('hierarchyLevelId', recommended.id);
              }} />
            </Form.Item>
          </>}
          <Form.Item name="sortOrder" label={isHierarchy ? '同级排序号' : '排序号'}><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="status" label="状态" valuePropName="checked" getValueFromEvent={(checked) => checked ? 'ACTIVE' : 'INACTIVE'} getValueProps={(value) => ({ checked: value === 'ACTIVE' })}><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
          {editing && <Descriptions size="small" column={1} bordered items={[{ key: 'source', label: '数据来源', children: sourceTypeLabel[editing.sourceType] }, { key: 'sync', label: '最后同步', children: editing.lastSyncedAt || '-' }]} />}
        </Form>
      </Drawer>

    </Space>
  );
}
