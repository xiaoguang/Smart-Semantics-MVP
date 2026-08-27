import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert, Button, Drawer, Dropdown, Empty, Input, Modal, Select, Space, Switch,
  Table, Tag, Typography, message,
} from 'antd';
import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, EditOutlined,
  HolderOutlined, MoreOutlined, PlusOutlined, SaveOutlined,
} from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { EntityHierarchyLevel, ObjectAttribute, RelationObjectType, SemanticEntity, SemanticEvent } from '../../types';
import { SemanticObjectTag } from '../../components/semantic-object-visuals';

const { Text } = Typography;

interface DraftLevel {
  key: string;
  id: number;
  levelName: string;
  levelCode: string;
  status: 'ACTIVE' | 'INACTIVE';
}

interface HierarchyDefinition {
  attribute: ObjectAttribute;
  hierarchyName: string;
  hierarchyCode: string;
  levels: EntityHierarchyLevel[];
  referenceCount: number;
  status: 'ACTIVE' | 'INACTIVE';
}

export default function HierarchyDefinitionTab({ objectType, object, initialAttributeId, onOpenValues, onOpenAttributes }: {
  objectType: RelationObjectType;
  object: SemanticEntity | SemanticEvent;
  initialAttributeId?: number;
  onOpenValues: (attributeId: number) => void;
  onOpenAttributes: () => void;
}) {
  const { entityHierarchies, objectValues, saveObjectHierarchy, deleteObjectHierarchy } = useStore();
  const hierarchyAttrs = useMemo(() => object.attributes.filter((attribute) => attribute.semanticType === 'HIERARCHY'), [object.attributes]);
  const definitions = useMemo<HierarchyDefinition[]>(() => hierarchyAttrs.flatMap((attribute) => {
    const levels = entityHierarchies
      .filter((level) => level.objectType === objectType && level.objectId === object.id && level.attributeId === attribute.id)
      .sort((a, b) => a.levelDepth - b.levelDepth);
    if (!levels.length) return [];
    const levelIds = new Set(levels.map((level) => level.id));
    return [{
      attribute,
      hierarchyName: levels[0].hierarchyName,
      hierarchyCode: levels[0].hierarchyCode,
      levels,
      referenceCount: objectValues.filter((value) => value.hierarchyLevelId && levelIds.has(value.hierarchyLevelId)).length,
      status: levels.some((level) => level.status === 'ACTIVE') ? 'ACTIVE' as const : 'INACTIVE' as const,
    }];
  }), [entityHierarchies, hierarchyAttrs, object.id, objectType, objectValues]);

  const [filterAttributeId, setFilterAttributeId] = useState<number | 'ALL'>('ALL');
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingAttributeId, setEditingAttributeId] = useState<number>();
  const [hierarchyName, setHierarchyName] = useState('');
  const [hierarchyCode, setHierarchyCode] = useState('');
  const [advanced, setAdvanced] = useState(false);
  const [levels, setLevels] = useState<DraftLevel[]>([]);
  const [draggingKey, setDraggingKey] = useState<string>();
  const handledShortcut = useRef('');

  const definitionByAttribute = (attributeId: number) => definitions.find((definition) => definition.attribute.id === attributeId);
  const prepareNew = (attributeId?: number) => {
    const attribute = hierarchyAttrs.find((item) => item.id === attributeId) || hierarchyAttrs.find((item) => !definitionByAttribute(item.id));
    setEditingAttributeId(attribute?.id);
    setHierarchyName(attribute ? `${attribute.attributeName}层级` : '');
    setHierarchyCode(attribute ? `${attribute.attributeCode}_hierarchy` : '');
    setLevels([]);
    setAdvanced(false);
    setDrawerOpen(true);
  };
  const openEdit = (definition: HierarchyDefinition) => {
    setEditingAttributeId(definition.attribute.id);
    setHierarchyName(definition.hierarchyName);
    setHierarchyCode(definition.hierarchyCode);
    setLevels(definition.levels.map((level) => ({ key: `saved-${level.id}`, id: level.id, levelName: level.levelName, levelCode: level.levelCode, status: level.status })));
    setAdvanced(false);
    setDrawerOpen(true);
  };

  useEffect(() => {
    if (!initialAttributeId) return;
    const shortcutKey = `${objectType}-${object.id}-${initialAttributeId}`;
    if (handledShortcut.current === shortcutKey) return;
    handledShortcut.current = shortcutKey;
    const definition = definitions.find((item) => item.attribute.id === initialAttributeId);
    if (definition) {
      setEditingAttributeId(definition.attribute.id);
      setHierarchyName(definition.hierarchyName);
      setHierarchyCode(definition.hierarchyCode);
      setLevels(definition.levels.map((level) => ({ key: `saved-${level.id}`, id: level.id, levelName: level.levelName, levelCode: level.levelCode, status: level.status })));
    } else {
      const attribute = hierarchyAttrs.find((item) => item.id === initialAttributeId);
      setEditingAttributeId(attribute?.id);
      setHierarchyName(attribute ? `${attribute.attributeName}层级` : '');
      setHierarchyCode(attribute ? `${attribute.attributeCode}_hierarchy` : '');
      setLevels([]);
    }
    setAdvanced(false);
    setDrawerOpen(true);
  }, [definitions, hierarchyAttrs, initialAttributeId, object.id, objectType]);

  if (hierarchyAttrs.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<Space direction="vertical"><Text>当前本体尚未配置 HIERARCHY 属性</Text><Button type="primary" onClick={onOpenAttributes}>前往属性列表创建</Button></Space>} />;
  }

  const shown = definitions.filter((definition) => (
    (filterAttributeId === 'ALL' || definition.attribute.id === filterAttributeId)
    && (filterStatus === 'ALL' || definition.status === filterStatus)
  ));
  const isEditing = !!editingAttributeId && !!definitionByAttribute(editingAttributeId);
  const selectedAttribute = hierarchyAttrs.find((attribute) => attribute.id === editingAttributeId);
  const addLevel = () => {
    const depth = levels.length + 1;
    const usedCodes = new Set(levels.map((level) => level.levelCode.trim().toUpperCase()));
    let codeIndex = 1;
    while (usedCodes.has(`L${codeIndex}`)) codeIndex += 1;
    setLevels((current) => [...current, { key: `new-${Date.now()}-${depth}`, id: 0, levelName: `第${depth}级`, levelCode: `L${codeIndex}`, status: 'ACTIVE' }]);
  };
  const move = (index: number, offset: number) => {
    const target = index + offset;
    if (target < 0 || target >= levels.length) return;
    setLevels((current) => {
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };
  const removeLevel = (level: DraftLevel) => {
    const references = objectValues.filter((value) => value.hierarchyLevelId === level.id).length;
    if (references) {
      message.error(`该级别正被 ${references} 个值引用，请先迁移或停用相关值`);
      return;
    }
    setLevels((current) => current.filter((item) => item.key !== level.key));
  };
  const dropAt = (targetKey: string) => {
    if (!draggingKey || draggingKey === targetKey) return;
    setLevels((current) => {
      const from = current.findIndex((level) => level.key === draggingKey);
      const to = current.findIndex((level) => level.key === targetKey);
      if (from < 0 || to < 0) return current;
      const next = [...current];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
    setDraggingKey(undefined);
  };
  const commitSave = () => {
    if (!editingAttributeId) {
      message.error('请选择层级属性');
      return;
    }
    const issues = saveObjectHierarchy(objectType, object.id, editingAttributeId, hierarchyName, hierarchyCode, levels);
    if (issues.length) message.error(issues[0].message);
    else {
      message.success(isEditing ? '层级定义已更新' : '层级定义已创建');
      setDrawerOpen(false);
    }
  };
  const save = () => {
    if (!hierarchyName.trim()) {
      message.error('请填写层级名称');
      return;
    }
    if (levels.length === 0) {
      message.error('请至少配置一个级别');
      return;
    }
    if (levels.some((level) => !level.levelName.trim() || !level.levelCode.trim())) {
      message.error('级别名称和级别编码均为必填项');
      return;
    }
    const normalizedCodes = levels.map((level) => level.levelCode.trim().toUpperCase());
    if (new Set(normalizedCodes).size !== normalizedCodes.length) {
      message.error('同一层级定义内的级别编码不能重复');
      return;
    }
    const existingLevels = editingAttributeId
      ? entityHierarchies.filter((level) => level.objectType === objectType && level.objectId === object.id && level.attributeId === editingAttributeId)
      : [];
    const changedReferencedLevels = levels.filter((level) => {
      if (!level.id) return false;
      const existing = existingLevels.find((item) => item.id === level.id);
      const codeChanged = !!existing && existing.levelCode !== level.levelCode.trim().toUpperCase();
      return codeChanged && objectValues.some((value) => value.hierarchyLevelId === level.id);
    });
    if (changedReferencedLevels.length) {
      Modal.confirm({
        title: '确认修改已引用的级别编码？',
        content: `“${changedReferencedLevels.map((level) => level.levelName).join('、')}”已被实体值引用。级别编码是稳定标识，修改后可能影响物理同步和外部映射。`,
        okText: '确认修改',
        onOk: commitSave,
      });
      return;
    }
    commitSave();
  };
  const removeDefinition = (definition: HierarchyDefinition) => {
    const issues = deleteObjectHierarchy(objectType, object.id, definition.attribute.id);
    if (issues.length) message.error(issues[0].message);
    else message.success('层级定义已删除');
  };
  const confirmRemoveDefinition = (definition: HierarchyDefinition) => Modal.confirm({
    title: '确认删除该层级定义？',
    content: definition.referenceCount
      ? `当前有 ${definition.referenceCount} 个成员引用，无法直接删除。`
      : '删除后不可恢复。',
    okText: '确认删除',
    okButtonProps: { danger: true },
    onOk: () => removeDefinition(definition),
  });

  const levelColumns = [
    { title: '', key: 'drag', width: 42, render: () => <HolderOutlined style={{ color: '#8c8c8c', cursor: 'grab' }} /> },
    { title: '顺序', key: 'depth', width: 68, render: (_: unknown, __: DraftLevel, index: number) => <Tag color="blue">L{index + 1}</Tag> },
    { title: '级别名称', key: 'name', render: (_: unknown, level: DraftLevel) => <Input value={level.levelName} onChange={(event) => setLevels((current) => current.map((item) => item.key === level.key ? { ...item, levelName: event.target.value } : item))} /> },
    { title: <span><Text type="danger">*</Text> 级别编码</span>, key: 'code', width: 180, render: (_: unknown, level: DraftLevel) => <Input value={level.levelCode} placeholder="例如：PROVINCE" status={!level.levelCode.trim() ? 'error' : undefined} onChange={(event) => setLevels((current) => current.map((item) => item.key === level.key ? { ...item, levelCode: event.target.value.toUpperCase() } : item))} /> },
    { title: '父级别', key: 'parent', width: 110, render: (_: unknown, __: DraftLevel, index: number) => index === 0 ? <Text type="secondary">根级别</Text> : levels[index - 1]?.levelName || '-' },
    { title: '状态', key: 'status', width: 75, render: (_: unknown, level: DraftLevel) => <Switch size="small" checked={level.status === 'ACTIVE'} onChange={(checked) => setLevels((current) => current.map((item) => item.key === level.key ? { ...item, status: checked ? 'ACTIVE' : 'INACTIVE' } : item))} /> },
    { title: '操作', key: 'action', width: 120, render: (_: unknown, level: DraftLevel, index: number) => <Space size={0}><Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => move(index, -1)} /><Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={index === levels.length - 1} onClick={() => move(index, 1)} /><Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => removeLevel(level)} /></Space> },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Text type="secondary" className="definition-helper">先定义层级顺序，再到“值定义”维护具体成员和父子关系。</Text>
      <div className="definition-toolbar">
        <Space>
          <Select value={filterAttributeId} onChange={setFilterAttributeId} style={{ width: 220 }} options={[{ value: 'ALL', label: '全部层级属性' }, ...hierarchyAttrs.map((attribute) => ({ value: attribute.id, label: attribute.attributeName }))]} />
          <Select value={filterStatus} onChange={setFilterStatus} style={{ width: 130 }} options={[{ value: 'ALL', label: '全部状态' }, { value: 'ACTIVE', label: '启用' }, { value: 'INACTIVE', label: '停用' }]} />
        </Space>
        <Button type="primary" icon={<PlusOutlined />} disabled={definitions.length >= hierarchyAttrs.length} onClick={() => prepareNew()}>新建层级</Button>
      </div>
      {shown.length ? <div className="hierarchy-card-list">
        {shown.map((definition) => <article className="hierarchy-card" key={`${definition.attribute.id}-${definition.hierarchyCode}`}>
          <header className="hierarchy-card-header">
            <div className="definition-identity">
              <Text strong>{definition.hierarchyName}</Text>
              <span className="definition-technical-code" title={definition.hierarchyCode}>{definition.hierarchyCode}</span>
            </div>
            <Tag color={definition.status === 'ACTIVE' ? 'green' : 'default'}>{definition.status === 'ACTIVE' ? '启用' : '停用'}</Tag>
          </header>
          <div className="hierarchy-card-content">
            <div className="hierarchy-card-attribute">
              <Text type="secondary">关联属性</Text>
              <div className="definition-identity">
                <span>{definition.attribute.attributeName}</span>
                <span className="definition-technical-code" title={definition.attribute.attributeCode}>{definition.attribute.attributeCode}</span>
              </div>
            </div>
            <div className="hierarchy-card-path-block">
              <Text type="secondary">层级路径</Text>
              <div className="hierarchy-level-path">
                {definition.levels.map((level, index) => <span className="hierarchy-level-step" key={level.id}>
                  <SemanticObjectTag kind="DIMENSION" label={level.levelName} />
                  {index < definition.levels.length - 1 && <span className="hierarchy-level-arrow">→</span>}
                </span>)}
              </div>
            </div>
          </div>
          <footer className="hierarchy-card-footer">
            <div className="hierarchy-card-counts">
              <span><strong>{definition.levels.length}</strong> 个级别</span>
              <span><strong>{definition.referenceCount}</strong> 个成员</span>
            </div>
            <Space size={4} className="hierarchy-card-actions">
              <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(definition)}>编辑</Button>
              <Button type="link" size="small" onClick={() => onOpenValues(definition.attribute.id)}>维护值</Button>
              <Dropdown trigger={['click']} menu={{ items: [{ key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除层级' }], onClick: ({ key }) => { if (key === 'delete') confirmRemoveDefinition(definition); } }}>
                <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${definition.hierarchyName}`} />
              </Dropdown>
            </Space>
          </footer>
        </article>)}
      </div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选条件下没有层级定义" />}

      <Drawer title={isEditing ? `编辑层级：${hierarchyName}` : '新建层级'} width="min(820px, 100vw)" open={drawerOpen} onClose={() => setDrawerOpen(false)} footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setDrawerOpen(false)}>取消</Button><Button type="primary" icon={<SaveOutlined />} onClick={save}>保存</Button></Space>}>
        <Space direction="vertical" size={18} style={{ width: '100%' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div><Text strong>关联层级属性</Text><Select value={editingAttributeId} disabled={isEditing} onChange={(attributeId) => prepareNew(attributeId)} style={{ width: '100%', marginTop: 8 }} options={hierarchyAttrs.map((attribute) => ({ value: attribute.id, label: `${attribute.attributeName}（${attribute.attributeCode}）`, disabled: !!definitionByAttribute(attribute.id) }))} /></div>
            <div><Text strong>层级名称</Text><Input value={hierarchyName} onChange={(event) => setHierarchyName(event.target.value)} placeholder="例如：行政区域层级" style={{ marginTop: 8 }} /></div>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Space><Text strong>整体状态</Text><Switch checked={levels.some((level) => level.status === 'ACTIVE')} onChange={(checked) => setLevels((current) => current.map((level) => ({ ...level, status: checked ? 'ACTIVE' : 'INACTIVE' })))} /></Space>
            <Space><Text>高级设置</Text><Switch checked={advanced} onChange={setAdvanced} /></Space>
          </div>
          {advanced && <div><Text strong>整体层级编码</Text><Input value={hierarchyCode} onChange={(event) => setHierarchyCode(event.target.value)} placeholder="用于标识整个层级定义" style={{ marginTop: 8 }} /></div>}
          {levels.length > 0 && <div style={{ padding: '10px 14px', borderRadius: 8, background: '#f6ffed', border: '1px solid #b7eb8f' }}><Text type="secondary">结构预览：</Text>{levels.map((level, index) => <span key={level.key}><Tag color="green">{level.levelName}</Tag>{index < levels.length - 1 && <Text type="secondary">→ </Text>}</span>)}</div>}
          <Table dataSource={levels} columns={levelColumns} rowKey="key" size="small" pagination={false} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请新增层级级别" /> }} onRow={(level) => ({ draggable: true, onDragStart: () => setDraggingKey(level.key), onDragOver: (event) => event.preventDefault(), onDrop: () => dropAt(level.key) })} />
          <div><Button icon={<PlusOutlined />} onClick={addLevel}>新增级别</Button></div>
          {selectedAttribute && <Alert type="info" showIcon message={`当前层级归属属性：${selectedAttribute.attributeName}（${selectedAttribute.attributeCode}）。保存时系统按级别顺序自动生成深度和父级关系。`} />}
        </Space>
      </Drawer>
    </Space>
  );
}
