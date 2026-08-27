import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Card, Drawer, Dropdown, Empty, Form, Input, InputNumber, Modal,
  Popconfirm, Select, Space, Switch, Table, Tabs, Tag, Tooltip, Typography, message,
} from 'antd';
import {
  ApartmentOutlined, BranchesOutlined, DatabaseOutlined, DeleteOutlined, EditOutlined,
  MoreOutlined, NodeIndexOutlined, PlusOutlined, ReadOutlined, SearchOutlined,
} from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type {
  FieldMapping, ObjectAttribute, RelationObjectType, SemanticEntity, SemanticEvent,
  SemanticRelation, SemanticRelationType, SemanticType,
} from '../../types';
import KnowledgeGraphFlow from './KnowledgeGraphFlow';
import SchemaLearningPanel from './SchemaLearningPanel';
import MappingConfigPanel from './MappingConfigPanel';
import HierarchyDefinitionTab from './HierarchyDefinitionTab';
import ObjectValueTab from './ObjectValueTab';
import { SemanticObjectTag, semanticObjectVisual } from '../../components/semantic-object-visuals';
import { projectAttributePresentation } from './attribute-presentation';
import {
  presentRelationCardinality,
  relationCardinalityOptions,
  relationDirectionOptions,
  relationJoinOptions,
} from './relation-presentation';
import { useLinguanWorkspace } from '../../features/ai-modeling/workspace-context';
import ResponsiveDataView, { ResponsiveDataCard } from '../../components/ResponsiveDataView';
import BackAction from '../../components/back-action.tsx';

const { Text } = Typography;

type DetailTab = 'attributes' | 'basic' | 'relations' | 'hierarchy' | 'values' | 'mapping';
type WorkspaceTab = 'graph' | 'schemas' | 'learning';
type OntologyTypeFilter = 'ALL' | RelationObjectType;

const semanticTypeLabels: Record<SemanticType, string> = {
  AMOUNT: '金额',
  NUMBER: '普通数值',
  RATIO: '比例/百分比',
  DATE: '日期',
  DATETIME: '日期时间',
  TIME: '时间（兼容）',
  BOOLEAN: '布尔值',
  TEXT: '文本',
  CODE: '业务编码',
  ENUM: '枚举/分类',
  HIERARCHY: '内部值层级',
  LIFECYCLE: '生命周期',
  ID: '技术标识',
  FOREIGN_KEY: '外键（兼容）',
};

const relationTypeLabels: Record<SemanticRelationType, string> = {
  EVENT_ENTITY: '事件-实体', ENTITY_ENTITY: '实体-实体', EVENT_EVENT: '事件-事件',
};

const objectTypeLabels: Record<RelationObjectType, string> = { EVENT: '事件', ENTITY: '实体' };

const roleOptions: FieldMapping['fieldRole'][] = ['PRIMARY_KEY', 'JOIN_KEY', 'DIMENSION', 'MEASURE', 'TIME', 'TEXT'];
const semanticTypeOptions = Object.keys(semanticTypeLabels) as SemanticType[];

function dataTypeForSemanticType(type: SemanticType) {
  if (type === 'DATE') return 'DATE';
  if (type === 'DATETIME' || type === 'TIME') return 'DATETIME';
  if (type === 'AMOUNT' || type === 'NUMBER' || type === 'RATIO') return 'DECIMAL';
  if (type === 'BOOLEAN') return 'BOOLEAN';
  if (type === 'ENUM' || type === 'LIFECYCLE') return 'ENUM';
  if (type === 'TEXT') return 'TEXT';
  return 'STRING';
}

function normalizeSynonyms(values?: string[]) {
  return [...new Set((values || []).map((value) => value.trim()).filter(Boolean))];
}

function getObjectName(type: RelationObjectType, id: number, events: SemanticEvent[], entities: SemanticEntity[]) {
  if (type === 'EVENT') {
    const event = events.find((x) => x.id === id);
    return event ? `${event.eventName} (${event.eventCode})` : `事件#${id}`;
  }
  const entity = entities.find((x) => x.id === id);
  return entity ? `${entity.entityName} (${entity.entityCode})` : `实体#${id}`;
}

function getAttributes(type: RelationObjectType, id: number, events: SemanticEvent[], entities: SemanticEntity[]) {
  return (type === 'EVENT' ? events.find((x) => x.id === id)?.attributes : entities.find((x) => x.id === id)?.attributes) || [];
}

function getAttributeName(attrs: ObjectAttribute[], id?: number) {
  const attr = attrs.find((x) => x.id === id);
  return attr ? `${attr.attributeName} (${attr.attributeCode})` : '未配置';
}

function defaultFieldRole(attr: ObjectAttribute): FieldMapping['fieldRole'] {
  if (attr.semanticType === 'ID') return 'PRIMARY_KEY';
  if (attr.semanticType === 'FOREIGN_KEY') return 'JOIN_KEY';
  if (['AMOUNT', 'NUMBER', 'RATIO'].includes(attr.semanticType)) return attr.isMetric ? 'MEASURE' : 'DIMENSION';
  if (['DATE', 'DATETIME', 'TIME'].includes(attr.semanticType)) return 'TIME';
  if (['CODE', 'ENUM', 'HIERARCHY', 'LIFECYCLE', 'BOOLEAN'].includes(attr.semanticType)) return 'DIMENSION';
  return 'TEXT';
}

export default function SemanticModelingPage() {
  const { workspaceReadOnly } = useLinguanWorkspace();
  const [workspaceTab, setWorkspaceTab] = useState<WorkspaceTab>('schemas');
  const [ontologyTypeFilter, setOntologyTypeFilter] = useState<OntologyTypeFilter>('ALL');
  const [activeType, setActiveType] = useState<RelationObjectType>('ENTITY');
  const [detailTab, setDetailTab] = useState<DetailTab>('attributes');
  const [selectedEventId, setSelectedEventId] = useState(1);
  const [selectedEntityId, setSelectedEntityId] = useState(1);
  const [search, setSearch] = useState('');
  const [focusedAttributeId, setFocusedAttributeId] = useState<number>();
  const [createOpen, setCreateOpen] = useState(false);
  const [mobileDetail, setMobileDetail] = useState(false);
  const [createForm] = Form.useForm();
  const {
    events, entities, addEvent, addEntity, deleteEvent, deleteEntity,
  } = useStore();

  const activeId = activeType === 'EVENT' ? selectedEventId : selectedEntityId;
  const activeObject = activeType === 'EVENT'
    ? events.find((x) => x.id === selectedEventId)
    : entities.find((x) => x.id === selectedEntityId);

  useEffect(() => {
    if (activeObject) return;
    if (activeType === 'EVENT' && events[0]) setSelectedEventId(events[0].id);
    else if (activeType === 'ENTITY' && entities[0]) setSelectedEntityId(entities[0].id);
    else if (entities[0]) { setActiveType('ENTITY'); setSelectedEntityId(entities[0].id); }
    else if (events[0]) { setActiveType('EVENT'); setSelectedEventId(events[0].id); }
  }, [activeObject, activeType, entities, events]);

  const ontologyListData = useMemo(() => {
    const q = search.trim().toLowerCase();
    return [
      ...entities.map((item) => ({ id: item.id, type: 'ENTITY' as const, name: item.entityName, code: item.entityCode })),
      ...events.map((item) => ({ id: item.id, type: 'EVENT' as const, name: item.eventName, code: item.eventCode })),
    ].filter((item) => {
      const matchesType = ontologyTypeFilter === 'ALL' || item.type === ontologyTypeFilter;
      const matchesSearch = !q || item.name.toLowerCase().includes(q) || item.code.toLowerCase().includes(q);
      return matchesType && matchesSearch;
    });
  }, [entities, events, ontologyTypeFilter, search]);

  const openCreate = () => {
    const objectType = ontologyTypeFilter === 'ALL' ? activeType : ontologyTypeFilter;
    createForm.resetFields();
    createForm.setFieldsValue({ objectType, status: 'ACTIVE' });
    setCreateOpen(true);
  };

  const handleCreate = () => {
    createForm.validateFields().then((values) => {
      const objectType = values.objectType as RelationObjectType;
      const code = String(values.code).trim();
      const name = String(values.name).trim();
      const common = {
        synonyms: normalizeSynonyms(values.synonyms),
        description: values.description?.trim(),
        owner: values.owner?.trim(),
        status: 'ACTIVE' as const,
      };
      const createIssues = objectType === 'EVENT'
        ? addEvent({ ...common, eventName: name, eventCode: code })
        : addEntity({ ...common, entityName: name, entityCode: code });
      if (createIssues.length) {
        message.error(createIssues[0].message);
        return;
      }
      const state = useStore.getState();
      if (objectType === 'EVENT') {
        const created = state.events.find((item) => item.eventCode === code);
        if (created) setSelectedEventId(created.id);
      } else {
        const created = state.entities.find((item) => item.entityCode === code);
        if (created) setSelectedEntityId(created.id);
      }
      setActiveType(objectType);
      setOntologyTypeFilter(objectType);
      setSearch('');
      setDetailTab('basic');
      setMobileDetail(true);
      setCreateOpen(false);
      message.success(`${objectType === 'EVENT' ? '事件' : '实体'}“${name}”已创建`);
    });
  };

  const handleDeleteCurrent = () => {
    if (!activeObject) return;
    const deletedName = activeType === 'EVENT'
      ? (activeObject as SemanticEvent).eventName
      : (activeObject as SemanticEntity).entityName;
    if (activeType === 'EVENT') deleteEvent(activeObject.id);
    else deleteEntity(activeObject.id);
    const state = useStore.getState();
    const sameTypeFallback = activeType === 'EVENT' ? state.events[0] : state.entities[0];
    if (sameTypeFallback) {
      if (activeType === 'EVENT') setSelectedEventId(sameTypeFallback.id);
      else setSelectedEntityId(sameTypeFallback.id);
    } else if (activeType === 'EVENT' && state.entities[0]) {
      setActiveType('ENTITY');
      setSelectedEntityId(state.entities[0].id);
      setOntologyTypeFilter('ALL');
    } else if (activeType === 'ENTITY' && state.events[0]) {
      setActiveType('EVENT');
      setSelectedEventId(state.events[0].id);
      setOntologyTypeFilter('ALL');
    }
    setDetailTab('attributes');
    message.success(`已删除“${deletedName}”及其关联配置`);
  };

  return (
    <Card className="ontology-workspace-card" title="本体建模" style={{ height: '100%' }} styles={{ body: { padding: 0 } }}>
      <Tabs
        activeKey={workspaceTab === 'learning' ? 'schemas' : workspaceTab}
        onChange={(key) => setWorkspaceTab(key as WorkspaceTab)}
        style={{ paddingInline: 16, marginBottom: 0 }}
        items={[
          { key: 'graph', label: '本体知识图谱' },
          { key: 'schemas', label: '本体列表' },
        ]}
      />
      {workspaceTab === 'graph' && <KnowledgeGraphFlow />}
      {workspaceTab === 'learning' && <SchemaLearningPanel onOpenSchema={() => setWorkspaceTab('schemas')} />}
      {workspaceTab === 'schemas' && <div className="ontology-master-detail">
        <Card
          className="ontology-list-pane"
          size="small"
          style={{ borderRadius: 0, borderTop: 0, borderBottom: 0, borderLeft: 0 }}
          styles={{ body: { padding: '12px 10px' } }}
        >
          <div className="ontology-list-toolbar">
            <Select
              aria-label="本体类型"
              value={ontologyTypeFilter}
              style={{ width: 116 }}
              onChange={(nextFilter) => {
                setOntologyTypeFilter(nextFilter);
                if (nextFilter === 'ENTITY' && activeType !== 'ENTITY' && entities[0]) {
                  setActiveType('ENTITY');
                  setSelectedEntityId(entities[0].id);
                  setDetailTab('attributes');
                } else if (nextFilter === 'EVENT' && activeType !== 'EVENT' && events[0]) {
                  setActiveType('EVENT');
                  setSelectedEventId(events[0].id);
                  setDetailTab('attributes');
                }
              }}
              options={[
                { value: 'ALL', label: '全部类型' },
                { value: 'ENTITY', label: '实体' },
                { value: 'EVENT', label: '事件' },
              ]}
            />
            <Input.Search
              prefix={<SearchOutlined />}
              placeholder="搜索名称或编码"
              allowClear
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            {!workspaceReadOnly && <Button type="primary" aria-label="新建本体" icon={<PlusOutlined />} onClick={openCreate}>新建</Button>}
            {!workspaceReadOnly && <Button icon={<ReadOutlined />} onClick={() => setWorkspaceTab('learning')}>学习来源</Button>}
          </div>
          {ontologyListData.map((item) => (
            <OntologyListItem
              key={`${item.type}-${item.id}`}
              {...item}
              selected={activeType === item.type && item.id === (item.type === 'EVENT' ? selectedEventId : selectedEntityId)}
              onClick={() => {
                setActiveType(item.type);
                if (item.type === 'EVENT') setSelectedEventId(item.id);
                else setSelectedEntityId(item.id);
                setFocusedAttributeId(undefined);
                setDetailTab('attributes');
                setMobileDetail(true);
              }}
            />
          ))}
          {ontologyListData.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配本体" />}
        </Card>

        {activeObject ? (
          <Card
            className={`ontology-detail-pane${mobileDetail ? ' mobile-active' : ''}`}
            size="small"
            title={<Space size={8}><BackAction className="ontology-mobile-back" destination="列表" onBack={() => setMobileDetail(false)} /><span>{activeType === 'EVENT' ? (activeObject as SemanticEvent).eventName : (activeObject as SemanticEntity).entityName}</span><SemanticObjectTag kind={activeType} /><Text type="secondary">详情</Text></Space>}
            extra={<Space>
              {!workspaceReadOnly && <Popconfirm title="确认删除当前本体及其关联配置？" onConfirm={handleDeleteCurrent}>
                <Button type="text" danger icon={<DeleteOutlined />}>删除</Button>
              </Popconfirm>}
            </Space>}
            style={{ borderRadius: 0, border: 0 }}
            styles={{ body: { padding: '0 16px 16px' } }}
          >
            <Tabs
              activeKey={detailTab}
              onChange={(key) => setDetailTab(key as DetailTab)}
              items={[
                {
                  key: 'attributes',
                  label: '属性列表',
                  children: <div className="ontology-detail-section">
                    <AttributeListTab
                      objectType={activeType}
                      object={activeObject}
                      readOnly={workspaceReadOnly}
                      onOpenHierarchy={(attributeId) => { setFocusedAttributeId(attributeId); setDetailTab('hierarchy'); }}
                      onOpenValues={(attributeId) => { setFocusedAttributeId(attributeId); setDetailTab('values'); }}
                    />
                  </div>,
                },
                {
                  key: 'basic',
                  label: '基本信息',
                  children: <div className="ontology-detail-section">
                    <BasicInfoTab objectType={activeType} object={activeObject} readOnly={workspaceReadOnly} />
                  </div>,
                },
                {
                  key: 'relations',
                  label: '关系结构',
                  children: <div className="ontology-detail-section">
                    <RelationTab objectType={activeType} objectId={activeId} readOnly={workspaceReadOnly} />
                  </div>,
                },
                {
                  key: 'hierarchy',
                  label: '层级定义',
                  children: <div className="ontology-detail-section">
                    <HierarchyDefinitionTab
                      objectType={activeType}
                      object={activeObject}
                      initialAttributeId={focusedAttributeId}
                      onOpenValues={(attributeId) => { setFocusedAttributeId(attributeId); setDetailTab('values'); }}
                      onOpenAttributes={() => setDetailTab('attributes')}
                    />
                  </div>,
                },
                {
                  key: 'values',
                  label: '值定义',
                  children: <div className="ontology-detail-section">
                    <ObjectValueTab
                      objectType={activeType}
                      object={activeObject}
                      initialAttributeId={focusedAttributeId}
                      onOpenHierarchy={(attributeId) => { setFocusedAttributeId(attributeId); setDetailTab('hierarchy'); }}
                      onOpenAttributes={() => setDetailTab('attributes')}
                    />
                  </div>,
                },
                {
                  key: 'mapping',
                  label: '映射配置',
                  children: <div className="ontology-detail-section">
                    <MappingConfigPanel objectType={activeType} objectId={activeId} readOnly={workspaceReadOnly} />
                  </div>,
                },
              ]}
            />
          </Card>
        ) : (
          <Card><Empty description="请选择对象" /></Card>
        )}
      </div>}
      <Modal
        title="新建本体"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={handleCreate}
        okText="创建"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="objectType" label="类型" rules={[{ required: true }]}>
            <Select options={[{ value: 'ENTITY', label: '实体' }, { value: 'EVENT', label: '事件' }]} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, whitespace: true, message: '请输入名称' }]}>
            <Input placeholder="例如：销售订单" />
          </Form.Item>
          <Form.Item
            name="code"
            label="ID"
            rules={[
              { required: true, whitespace: true, message: '请输入 ID' },
              { pattern: /^[a-z][a-z0-9_]*$/, message: 'ID 需以小写字母开头，仅包含小写字母、数字和下划线' },
            ]}
          >
            <Input placeholder="例如：sales_order" />
          </Form.Item>
          <Form.Item name="owner" label="责任人"><Input placeholder="请输入责任人" /></Form.Item>
          <Form.Item name="synonyms" label="同义词" extra="输入同义词后按回车确认，支持添加多个。">
            <Select mode="tags" tokenSeparators={[',', '，']} placeholder="输入同义词后按回车" />
          </Form.Item>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={3} placeholder="说明该本体的业务含义" /></Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

function OntologyListItem({ type, name, code, selected, onClick }: {
  type: RelationObjectType;
  name: string;
  code: string;
  selected: boolean;
  onClick: () => void;
}) {
  const visual = semanticObjectVisual(type);
  return (
    <button
      type="button"
      aria-label={`查看本体 ${name}`}
      onClick={onClick}
      style={{
        width: '100%', display: 'grid', gridTemplateColumns: '22px minmax(0, 1fr) auto',
        alignItems: 'center', gap: 8, padding: '8px 10px', marginBottom: 2,
        border: selected ? '1px solid #91caff' : '1px solid transparent',
        borderRadius: 6, background: selected ? '#e6f4ff' : 'transparent',
        color: '#1f1f1f', cursor: 'pointer', textAlign: 'left', font: 'inherit',
      }}
    >
      <span style={{ color: visual.stroke }}>
        {type === 'EVENT' ? <ApartmentOutlined /> : <BranchesOutlined />}
      </span>
      <span style={{ minWidth: 0 }}>
        <strong className="ontology-list-name">{name}</strong>
        <Text type="secondary" className="ontology-list-code">{code}</Text>
      </span>
      <Space size={4}>
        <SemanticObjectTag kind={type} />
      </Space>
    </button>
  );
}

function BasicInfoTab({ objectType, object, readOnly = false }: { objectType: RelationObjectType; object: SemanticEvent | SemanticEntity; readOnly?: boolean }) {
  const { updateEvent, updateEntity } = useStore();
  const [baseForm] = Form.useForm();
  const attrs = object.attributes;
  const isEvent = objectType === 'EVENT';
  const codeField = isEvent ? 'eventCode' : 'entityCode';
  const nameField = isEvent ? 'eventName' : 'entityName';

  useEffect(() => {
    baseForm.setFieldsValue(object);
  }, [baseForm, object]);

  const saveBase = () => {
    baseForm.validateFields().then((values) => {
      const nextValues = { ...values, synonyms: normalizeSynonyms(values.synonyms) };
      if (isEvent) updateEvent(object.id, nextValues);
      else updateEntity(object.id, nextValues);
      message.success('基本信息已保存');
    });
  };

  return (
    <Form form={baseForm} layout="vertical" initialValues={object} disabled={readOnly}>
      <div className="ontology-form-grid ontology-form-grid-three">
        <Form.Item name={nameField} label="名称" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name={codeField} label="ID" rules={[{ required: true }]}>
          <Input disabled />
        </Form.Item>
        <Form.Item name="owner" label="责任人"><Input /></Form.Item>
      </div>
      <Form.Item name="synonyms" label="同义词" extra="输入同义词后按回车确认，支持添加多个。">
        <Select mode="tags" tokenSeparators={[',', '，']} placeholder="输入同义词后按回车" />
      </Form.Item>
      <Form.Item name="description" label="业务说明"><Input.TextArea rows={3} /></Form.Item>
      <div className="ontology-form-grid ontology-form-grid-two">
        <Form.Item name="uniqueIdentifierAttributeId" label="唯一标识">
          <Select
            allowClear
            placeholder="选择唯一标识属性"
            options={attrs.filter((x) => x.semanticType === 'ID').map((x) => ({ value: x.id, label: `${x.attributeName} (${x.attributeCode})` }))}
          />
        </Form.Item>
        <Form.Item name="defaultTimeAttributeId" label="默认时间">
          <Select
            allowClear
            placeholder="选择默认时间属性"
            options={attrs.filter((x) => ['DATE', 'DATETIME', 'TIME'].includes(x.semanticType)).map((x) => ({ value: x.id, label: `${x.attributeName} (${x.attributeCode})` }))}
          />
        </Form.Item>
      </div>
      {!readOnly && <Button type="primary" onClick={saveBase}>保存基本信息</Button>}
    </Form>
  );
}

function AttributeListTab({ objectType, object, onOpenHierarchy, onOpenValues, readOnly = false }: {
  objectType: RelationObjectType;
  object: SemanticEvent | SemanticEntity;
  onOpenHierarchy: (attributeId: number) => void;
  onOpenValues: (attributeId: number) => void;
  readOnly?: boolean;
}) {
  const { updateEvent, updateEntity, deleteObjectAttributes, semanticRelations } = useStore();
  const [attrOpen, setAttrOpen] = useState(false);
  const [editingAttr, setEditingAttr] = useState<ObjectAttribute | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [attrSearch, setAttrSearch] = useState('');
  const [attrForm] = Form.useForm();
  const selectedFieldType = Form.useWatch<'DIMENSION' | 'METRIC'>('fieldType', attrForm);
  const selectedSemanticType = Form.useWatch<SemanticType>('semanticType', attrForm);
  const attrs = object.attributes;
  const isEvent = objectType === 'EVENT';

  const openAttr = (attr?: ObjectAttribute) => {
    setEditingAttr(attr || null);
    attrForm.resetFields();
    attrForm.setFieldsValue(attr ? {
      ...attr,
      fieldType: attr.isMetric ? 'METRIC' : 'DIMENSION',
    } : {
      fieldType: 'DIMENSION',
      semanticType: 'TEXT',
      isFilterable: true,
      isGroupable: false,
    });
    setAttrOpen(true);
  };

  const saveAttr = () => {
    attrForm.validateFields().then((values) => {
      const duplicate = attrs.some((attr) => attr.attributeCode === values.attributeCode && attr.id !== editingAttr?.id);
      if (duplicate) {
        message.error(`属性编码“${values.attributeCode}”已存在`);
        return;
      }
      const semanticType = values.semanticType as SemanticType;
      const attributeValues: Partial<ObjectAttribute> = {
        ...(editingAttr || {}),
        attributeCode: values.attributeCode.trim(),
        attributeName: values.attributeName.trim(),
        synonyms: normalizeSynonyms(values.synonyms),
        semanticType,
        dataType: dataTypeForSemanticType(semanticType),
        isFilterable: !!values.isFilterable,
        isGroupable: values.fieldType === 'DIMENSION' ? !!values.isGroupable : false,
        isMetric: values.fieldType === 'METRIC',
        unit: ['AMOUNT', 'NUMBER'].includes(semanticType) ? values.unit?.trim() || undefined : undefined,
        formatPattern: ['AMOUNT', 'NUMBER', 'RATIO', 'DATE', 'DATETIME', 'TIME'].includes(semanticType)
          ? values.formatPattern?.trim() || undefined
          : undefined,
      };
      const nextAttrs = editingAttr
        ? attrs.map((x) => (x.id === editingAttr.id ? { ...x, ...attributeValues } as ObjectAttribute : x))
        : [...attrs, { ...attributeValues, id: Date.now() } as ObjectAttribute];
      if (isEvent) updateEvent(object.id, { attributes: nextAttrs });
      else updateEntity(object.id, { attributes: nextAttrs });
      setAttrOpen(false);
      message.success('属性已保存');
    });
  };

  const deleteAttr = (id: number) => {
    deleteObjectAttributes(objectType, object.id, [id]);
    setSelectedRowKeys((keys) => keys.filter((key) => key !== id));
    message.success('属性及其字段映射已删除');
  };

  const deleteSelected = () => {
    deleteObjectAttributes(objectType, object.id, selectedRowKeys);
    setSelectedRowKeys([]);
    message.success(`已删除 ${selectedRowKeys.length} 个属性及其关联配置`);
  };

  useEffect(() => {
    setSelectedRowKeys([]);
    setAttrSearch('');
  }, [object.id, objectType]);

  const participatesInAttribution = (attr: ObjectAttribute) => (
    !!attr.isGroupable || semanticRelations.some((relation) => (
      (relation.sourceType === objectType && relation.sourceId === object.id && relation.sourceAttributeId === attr.id)
      || (relation.targetType === objectType && relation.targetId === object.id && relation.targetAttributeId === attr.id)
    ))
  );

  const columns = [
    {
      title: '字段',
      key: 'identity',
      width: 300,
      render: (_: unknown, attr: ObjectAttribute) => {
        const view = projectAttributePresentation(attr, participatesInAttribution(attr));
        return <div className="attribute-identity">
          <Text strong>{view.name}</Text>
          <Tooltip title={view.code} placement="bottomLeft"><Text type="secondary" className="attribute-code">{view.code}</Text></Tooltip>
        </div>;
      },
    },
    {
      title: '字段分类',
      key: 'fieldRole',
      width: 126,
      render: (_: unknown, attr: ObjectAttribute) => {
        const view = projectAttributePresentation(attr, participatesInAttribution(attr));
        return <SemanticObjectTag kind="FIELD" role={view.fieldRole} />;
      },
    },
    { title: '语义类型', dataIndex: 'semanticType', key: 'semanticType', width: 150, render: (value: SemanticType) => <span className="attribute-semantic-type">{semanticTypeLabels[value]}</span> },
    {
      title: '使用方式',
      key: 'usage',
      width: 180,
      render: (_: unknown, attr: ObjectAttribute) => {
        const view = projectAttributePresentation(attr, participatesInAttribution(attr));
        return view.usageLabels.length
          ? <Space size={[4, 4]} wrap>{view.usageLabels.map((label) => <Tag key={label}>{label}</Tag>)}</Space>
          : <Text type="secondary">—</Text>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 132,
      fixed: 'right' as const,
      render: (_: unknown, attr: ObjectAttribute) => {
        const menuItems = [
          ...(attr.semanticType === 'HIERARCHY' ? [{ key: 'hierarchy', label: '配置层级' }] : []),
          ...(['ENUM', 'CODE', 'HIERARCHY', 'LIFECYCLE'].includes(attr.semanticType) ? [{ key: 'values', label: '维护值' }] : []),
          { type: 'divider' as const },
          { key: 'delete', label: '删除', danger: true },
        ];
        if (readOnly) return <Text type="secondary">只读</Text>;
        return <Space size={0} className="attribute-actions">
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openAttr(attr)}>编辑</Button>
          <Dropdown
            trigger={['click']}
            menu={{
              items: menuItems,
              onClick: ({ key }) => {
                if (key === 'hierarchy') onOpenHierarchy(attr.id);
                if (key === 'values') onOpenValues(attr.id);
                if (key === 'delete') Modal.confirm({
                  title: `删除字段“${attr.attributeName}”？`,
                  content: '字段及其物理映射将一并删除。',
                  okText: '删除',
                  okButtonProps: { danger: true },
                  cancelText: '取消',
                  onOk: () => deleteAttr(attr.id),
                });
              },
            }}
          >
            <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${attr.attributeName}`} />
          </Dropdown>
        </Space>;
      },
    },
  ];
  const visibleAttrs = attrs.filter((attr) => {
    const keyword = attrSearch.trim().toLowerCase();
    return !keyword
      || attr.attributeName.toLowerCase().includes(keyword)
      || attr.attributeCode.toLowerCase().includes(keyword)
      || semanticTypeLabels[attr.semanticType].toLowerCase().includes(keyword);
  });

  return (
    <>
      <div className="attribute-toolbar">
        <Input.Search allowClear value={attrSearch} onChange={(event) => setAttrSearch(event.target.value)} placeholder="搜索字段名称、编码或语义类型" />
        <Space>
          {!readOnly && selectedRowKeys.length > 0 && <Popconfirm title={`确认删除选中的 ${selectedRowKeys.length} 个字段？`} onConfirm={deleteSelected}>
            <Button danger icon={<DeleteOutlined />}>删除所选（{selectedRowKeys.length}）</Button>
          </Popconfirm>}
          {!readOnly && <Button type="primary" icon={<PlusOutlined />} onClick={() => openAttr()}>添加字段</Button>}
        </Space>
      </div>
      <Table
        className="ontology-attribute-table"
        dataSource={visibleAttrs}
        columns={columns}
        rowKey="id"
        size="small"
        pagination={false}
        tableLayout="fixed"
        scroll={{ x: 920 }}
        rowSelection={readOnly ? undefined : {
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys as number[]),
        }}
      />

      <Drawer title={editingAttr ? '编辑字段' : '新建字段'} width="min(520px, 100vw)" open={attrOpen} onClose={() => setAttrOpen(false)}
        footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setAttrOpen(false)}>取消</Button><Button type="primary" onClick={saveAttr}>保存</Button></Space>}>
        <Form form={attrForm} layout="vertical">
          <Form.Item
            name="attributeCode"
            label="编码"
            rules={[
              { required: true, whitespace: true, message: '请输入属性编码' },
              { pattern: /^[a-z][a-z0-9_]*$/, message: '编码需以小写字母开头，仅包含小写字母、数字和下划线' },
            ]}
          >
            <Input disabled={!!editingAttr} placeholder="例如：order_no" />
          </Form.Item>
          <Form.Item name="attributeName" label="名称" rules={[{ required: true, whitespace: true, message: '请输入属性名称' }]}>
            <Input placeholder="例如：订单编号" />
          </Form.Item>
          <Form.Item name="synonyms" label="同义词" extra="输入同义词后按回车确认，支持添加多个。">
            <Select mode="tags" tokenSeparators={[',', '，']} placeholder="输入同义词后按回车" />
          </Form.Item>
          <Form.Item name="isFilterable" label="参与过滤" valuePropName="checked">
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
          <Form.Item name="fieldType" label="字段类型" rules={[{ required: true }]}>
            <Select
              options={[{ value: 'DIMENSION', label: '维度' }, { value: 'METRIC', label: '指标' }]}
              onChange={(value) => {
                if (value === 'METRIC') {
                  attrForm.setFieldValue('isGroupable', false);
                }
              }}
            />
          </Form.Item>
          <Form.Item name="semanticType" label="语义类型" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={semanticTypeOptions.map((value) => ({ value, label: `${value} · ${semanticTypeLabels[value]}` }))}
            />
          </Form.Item>
          <Form.Item name="isGroupable" label="是否参与归因分析" valuePropName="checked">
            <Switch checkedChildren="是" unCheckedChildren="否" disabled={selectedFieldType === 'METRIC'} />
          </Form.Item>
          {selectedSemanticType === 'HIERARCHY' && <Alert type="info" showIcon style={{ marginBottom: 16 }} message="保存属性后，请通过“层级定义”配置级别骨架，再到“值定义”维护具体值树。" />}
          {selectedSemanticType && ['CODE', 'ENUM', 'LIFECYCLE'].includes(selectedSemanticType) && <Alert type="info" showIcon style={{ marginBottom: 16 }} message="具体业务值与物理编码统一在“值定义”页签维护。" />}
          {selectedSemanticType && ['AMOUNT', 'NUMBER'].includes(selectedSemanticType) && (
            <Form.Item
              name="unit"
              label="基本单位"
              rules={selectedSemanticType === 'AMOUNT' ? [{ required: true, whitespace: true, message: '金额类型必须配置基本单位' }] : undefined}
              extra={selectedSemanticType === 'AMOUNT' ? '金额必须配置，例如：元、美元、EUR。' : '可选，例如：件、天、人。'}
            >
              <Input placeholder={selectedSemanticType === 'AMOUNT' ? '例如：元' : '例如：件'} />
            </Form.Item>
          )}
          {selectedSemanticType && ['AMOUNT', 'NUMBER', 'RATIO', 'DATE', 'DATETIME', 'TIME'].includes(selectedSemanticType) && (
            <Form.Item name="formatPattern" label="格式模板" extra="可选；为空时使用该语义类型的安全默认格式。">
              <Input placeholder="例如：#,##0.00" />
            </Form.Item>
          )}
        </Form>
      </Drawer>
    </>
  );
}

export function RelationTab({ objectType, objectId, readOnly = false }: { objectType: RelationObjectType; objectId: number; readOnly?: boolean }) {
  const { semanticRelations, events, entities, addRelation, updateRelation, deleteRelation } = useStore();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<SemanticRelation | null>(null);
  const [mobileRelationId, setMobileRelationId] = useState<number>();
  const [form] = Form.useForm();
  const sourceType = Form.useWatch('sourceType', form);
  const sourceId = Form.useWatch('sourceId', form);
  const targetType = Form.useWatch('targetType', form);
  const targetId = Form.useWatch('targetId', form);

  const visibleRelations = semanticRelations.filter((r) => (
    (r.sourceType === objectType && r.sourceId === objectId) || (r.targetType === objectType && r.targetId === objectId)
  ));
  const mobileRelation = visibleRelations.find((relation) => relation.id === mobileRelationId);
  useEffect(() => setMobileRelationId(undefined), [objectId, objectType]);

  const objectOptions = (type?: RelationObjectType) => {
    if (type === 'EVENT') return events.map((x) => ({ value: x.id, label: `${x.eventName} (${x.eventCode})` }));
    if (type === 'ENTITY') return entities.map((x) => ({ value: x.id, label: `${x.entityName} (${x.entityCode})` }));
    return [];
  };

  const attrOptions = (type?: RelationObjectType, id?: number) => getAttributes(type || 'ENTITY', id || 0, events, entities).map((x) => ({ value: x.id, label: `${x.attributeName} (${x.attributeCode})` }));

  const openNew = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      sourceType: objectType, sourceId: objectId, targetType: 'ENTITY', relationType: objectType === 'EVENT' ? 'EVENT_ENTITY' : 'ENTITY_ENTITY',
      cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 1, joinType: 'INNER', required: true, status: 'ACTIVE',
    });
    setOpen(true);
  };

  const openEdit = (r: SemanticRelation) => {
    setEditing(r);
    form.setFieldsValue(r);
    setOpen(true);
  };

  const save = () => {
    form.validateFields().then((values) => {
      if (editing) {
        updateRelation(editing.id, values);
        message.success('关系已更新');
      } else {
        const issues = addRelation(values);
        if (issues.length) message.error(issues[0].message);
        else message.success('关系已创建');
      }
      setOpen(false);
    });
  };

  const relationActions = (relation: SemanticRelation) => <Space size={4} wrap>
    <Button className="relation-view-action" size="small" onClick={() => setMobileRelationId(relation.id)} aria-label={`查看关系 ${relation.relationName}`}>查看关系</Button>
    {readOnly ? <Text type="secondary">只读</Text> : <>
      <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(relation)}>编辑</Button>
      <Popconfirm title="确认删除该关系？" onConfirm={() => { deleteRelation(relation.id); message.success('关系已删除'); }}><Button size="small" danger icon={<DeleteOutlined />}>删除</Button></Popconfirm>
    </>}
  </Space>;

  const columns = [
    { title: '关系名称', dataIndex: 'relationName', key: 'name', width: 150 },
    { title: '类型', dataIndex: 'relationType', key: 'type', width: 160, render: (v: SemanticRelationType) => <Space size={4}><SemanticObjectTag kind="RELATION" /><Tag>{relationTypeLabels[v]}</Tag></Space> },
    { title: '来源', key: 'source', render: (_: unknown, r: SemanticRelation) => getObjectName(r.sourceType, r.sourceId, events, entities) },
    { title: '目标', key: 'target', render: (_: unknown, r: SemanticRelation) => getObjectName(r.targetType, r.targetId, events, entities) },
    { title: '连接字段', key: 'join', render: (_: unknown, r: SemanticRelation) => {
      const sAttrs = getAttributes(r.sourceType, r.sourceId, events, entities);
      const tAttrs = getAttributes(r.targetType, r.targetId, events, entities);
      return <Text code>{getAttributeName(sAttrs, r.sourceAttributeId)} = {getAttributeName(tAttrs, r.targetAttributeId)}</Text>;
    } },
    { title: '基数', dataIndex: 'cardinality', key: 'cardinality', width: 120, render: (value: SemanticRelation['cardinality']) => presentRelationCardinality(value) },
    { title: '操作', key: 'action', width: 150, render: (_: unknown, r: SemanticRelation) => relationActions(r) },
  ];

  const relationPreview = (relation: SemanticRelation) => <div className="relation-preview-card">
    <Text strong>{getObjectName(relation.sourceType, relation.sourceId, events, entities)}</Text>
    <div>— {relation.relationName} / {relation.relationRole || relationTypeLabels[relation.relationType]} →</div>
    <Text strong>{getObjectName(relation.targetType, relation.targetId, events, entities)}</Text>
  </div>;
  return (
    <>
      <div className={`relation-master-detail${mobileRelation ? ' has-mobile-detail' : ''}`}>
        <Card className="relation-list-pane" size="small" title={<Space><BranchesOutlined /> 关系列表</Space>} extra={!readOnly && <Button type="primary" icon={<PlusOutlined />} onClick={openNew}>新增关系</Button>}>
          <ResponsiveDataView
            ariaLabel="关系列表"
            dataSource={visibleRelations}
            columns={columns}
            rowKey="id"
            minTableWidth={980}
            mobilePageSize={12}
            pagination={{ pageSize: 20, showSizeChanger: false }}
            tableProps={{ size: 'small' }}
            renderCard={(relation) => <ResponsiveDataCard
              title={relation.relationName}
              subtitle={<code>{relation.relationCode}</code>}
              status={<Tag>{presentRelationCardinality(relation.cardinality)}</Tag>}
              fields={[
                { label: '类型', value: relationTypeLabels[relation.relationType] },
                { label: '业务角色', value: relation.relationRole || '—' },
                { label: '来源', value: getObjectName(relation.sourceType, relation.sourceId, events, entities), wide: true },
                { label: '目标', value: getObjectName(relation.targetType, relation.targetId, events, entities), wide: true },
              ]}
              actions={relationActions(relation)}
            />}
          />
        </Card>
        <Card className={`relation-detail-pane${mobileRelation ? ' mobile-active' : ''}`} size="small" title={<Space><BackAction className="relation-mobile-back" destination="关系列表" onBack={() => setMobileRelationId(undefined)} /><NodeIndexOutlined /> 关系详情</Space>}>
          <Space direction="vertical" style={{ width: '100%' }}>
            {visibleRelations.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无关系边" />}
            {(mobileRelation ? [mobileRelation] : visibleRelations).map((relation) => <div key={relation.id}>{relationPreview(relation)}</div>)}
          </Space>
        </Card>
      </div>

      <Drawer title={editing ? '编辑关系边' : '新增关系边'} width="min(620px, 100vw)" open={open} onClose={() => setOpen(false)}
        footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setOpen(false)}>取消</Button><Button type="primary" onClick={save}>保存</Button></Space>}>
        <Form form={form} layout="vertical">
          <div className="ontology-form-grid ontology-form-grid-two">
            <Form.Item name="relationName" label="关系名称" rules={[{ required: true }]}><Input placeholder="如：订单用户" /></Form.Item>
            <Form.Item name="relationCode" label="关系编码" rules={[{ required: true }]}><Input disabled={!!editing} placeholder="如：order_customer" /></Form.Item>
            <Form.Item name="relationType" label="关系类型" rules={[{ required: true }]}>
              <Select options={Object.entries(relationTypeLabels).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="relationRole" label="业务角色"><Input placeholder="如：buyer、store、belongs_to" /></Form.Item>
            <Form.Item name="sourceType" label="来源类型" rules={[{ required: true }]}><Select options={Object.entries(objectTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
            <Form.Item name="sourceId" label="来源对象" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={objectOptions(sourceType)} /></Form.Item>
            <Form.Item name="targetType" label="目标类型" rules={[{ required: true }]}><Select options={Object.entries(objectTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
            <Form.Item name="targetId" label="目标对象" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={objectOptions(targetType)} /></Form.Item>
            <Form.Item name="sourceAttributeId" label="来源连接属性" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={attrOptions(sourceType, sourceId)} /></Form.Item>
            <Form.Item name="targetAttributeId" label="目标连接属性" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={attrOptions(targetType, targetId)} /></Form.Item>
            <Form.Item name="cardinality" label="基数"><Select options={relationCardinalityOptions()} /></Form.Item>
            <Form.Item name="direction" label="方向"><Select options={relationDirectionOptions()} /></Form.Item>
            <Form.Item name="joinType" label="连接方式"><Select options={relationJoinOptions()} /></Form.Item>
            <Form.Item name="priority" label="路径优先级"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
          </div>
          <Form.Item name="required" label="必选关系" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item name="status" label="状态"><Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'INACTIVE', label: '停用' }]} /></Form.Item>
        </Form>
      </Drawer>
    </>
  );
}

export function MappingTab({ objectType, objectId }: { objectType: RelationObjectType; objectId: number }) {
  const { events, entities, schemaMappings, fieldMappings, upsertSchemaMapping, upsertFieldMapping, deleteFieldMapping } = useStore();
  const object = objectType === 'EVENT' ? events.find((x) => x.id === objectId) : entities.find((x) => x.id === objectId);
  const attrs = object?.attributes || [];
  const schema = schemaMappings.find((x) => x.objectType === objectType && x.objectId === objectId);
  const mappings = fieldMappings.filter((x) => x.objectType === objectType && x.objectId === objectId);
  const [schemaForm] = Form.useForm();

  useEffect(() => {
    schemaForm.setFieldsValue(schema || {
      datasourceName: 'dw_prod',
      schemaName: objectType === 'EVENT' ? 'dwd' : 'dim',
      tableName: '',
      tableAlias: '',
    });
  }, [objectId, objectType, schema, schemaForm]);

  const saveSchema = () => {
    schemaForm.validateFields().then((values) => {
      upsertSchemaMapping({ ...values, objectType, objectId });
      message.success('表映射已保存');
    });
  };

  const saveField = (attr: ObjectAttribute, values: Partial<FieldMapping>) => {
    if (!values.physicalFieldName) return;
    upsertFieldMapping({
      objectType,
      objectId,
      attributeId: attr.id,
      physicalFieldName: values.physicalFieldName,
      physicalDataType: values.physicalDataType || attr.dataType.toLowerCase(),
      fieldRole: values.fieldRole || defaultFieldRole(attr),
    } as Omit<FieldMapping, 'id'>);
    message.success(`属性“${attr.attributeName}”的字段映射已保存`);
  };

  const columns = [
    { title: '语义属性', key: 'attr', width: 180, render: (_: unknown, attr: ObjectAttribute) => <Space direction="vertical" size={0}><strong>{attr.attributeName}</strong><Text type="secondary">{attr.attributeCode}</Text></Space> },
    { title: '语义类型', dataIndex: 'semanticType', key: 'semanticType', width: 100, render: (v: SemanticType) => <Tag>{semanticTypeLabels[v]}</Tag> },
    { title: '物理字段', key: 'field', render: (_: unknown, attr: ObjectAttribute) => {
      const mapping = mappings.find((x) => x.attributeId === attr.id);
      return <InlineFieldMapping
        attr={attr}
        mapping={mapping}
        onSave={(values) => saveField(attr, values)}
        onDelete={() => {
          if (!mapping) return;
          deleteFieldMapping(mapping.id);
          message.success(`属性“${attr.attributeName}”的字段映射已清除`);
        }}
      />;
    } },
  ];

  return (
    <>
      <Card size="small" title={<Space><DatabaseOutlined /> 表映射</Space>} style={{ marginBottom: 16 }}>
        <Form form={schemaForm} layout="vertical" initialValues={schema || { datasourceName: 'dw_prod', schemaName: 'dwd', tableName: '', tableAlias: '' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 16 }}>
            <Form.Item name="datasourceName" label="数据源" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="schemaName" label="本体" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="tableName" label="物理表" rules={[{ required: true }]}><Input placeholder={objectType === 'EVENT' ? 'dwd_order_detail' : 'dim_customer'} /></Form.Item>
            <Form.Item name="tableAlias" label="表别名"><Input placeholder="如：o、c" /></Form.Item>
          </div>
          <Button type="primary" onClick={saveSchema}>保存表映射</Button>
        </Form>
      </Card>

      <Card size="small" title="字段映射">
        <Table dataSource={attrs} columns={columns} rowKey="id" size="small" pagination={false} />
      </Card>
    </>
  );
}

function InlineFieldMapping({ attr, mapping, onSave, onDelete }: {
  attr: ObjectAttribute;
  mapping?: FieldMapping;
  onSave: (values: Partial<FieldMapping>) => void;
  onDelete: () => void;
}) {
  const [form] = Form.useForm();
  useEffect(() => {
    form.setFieldsValue(mapping || {
      physicalFieldName: attr.attributeCode,
      physicalDataType: attr.dataType.toLowerCase(),
      fieldRole: defaultFieldRole(attr),
    });
  }, [attr, form, mapping]);

  return (
    <Form form={form} component={false} initialValues={mapping || { physicalFieldName: attr.attributeCode, physicalDataType: attr.dataType.toLowerCase(), fieldRole: defaultFieldRole(attr) }}>
      <Space.Compact style={{ width: '100%' }}>
        <Form.Item name="physicalFieldName" noStyle><Input style={{ width: '32%' }} placeholder="物理字段" /></Form.Item>
        <Form.Item name="physicalDataType" noStyle><Input style={{ width: '20%' }} placeholder="字段类型" /></Form.Item>
        <Form.Item name="fieldRole" noStyle><Select style={{ width: '24%' }} options={roleOptions.map((x) => ({ value: x, label: x }))} /></Form.Item>
        <Button onClick={() => form.validateFields().then(onSave)}>保存</Button>
        {mapping && <Button danger onClick={onDelete}>清除</Button>}
      </Space.Compact>
    </Form>
  );
}
