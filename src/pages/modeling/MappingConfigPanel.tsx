import { useEffect, useMemo, useState } from 'react';
import {
  Button, Card, Empty, message, Progress, Select, Space, Steps, Table, Tabs, Tag, Typography,
} from 'antd';
import {
  CheckCircleOutlined, DatabaseOutlined, SaveOutlined, ThunderboltOutlined,
} from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { FieldMapping, ObjectAttribute, RelationObjectType } from '../../types';
import { useSourceManagement } from '../../features/source-management/source-management-context.tsx';
import { projectPhysicalSources } from '../../features/source-management/projection.ts';
import type { SnapshotPhysicalTable } from '../../features/source-management/types.ts';

const { Text } = Typography;

type PhysicalField = SnapshotPhysicalTable['fields'][number];
type PhysicalTable = SnapshotPhysicalTable & {
  datasource: string;
  snapshotId: string;
  connectionRevision: number;
};

interface MappingDraft {
  mappingType: 'ATTRIBUTE' | 'IGNORE';
  attributeId?: number;
  sqlTransform?: string;
}

function defaultFieldRole(attr: ObjectAttribute): FieldMapping['fieldRole'] {
  if (attr.semanticType === 'ID') return 'PRIMARY_KEY';
  if (attr.semanticType === 'FOREIGN_KEY') return 'JOIN_KEY';
  if (['DATE', 'DATETIME', 'TIME'].includes(attr.semanticType)) return 'TIME';
  if (attr.isMetric || ['AMOUNT', 'NUMBER', 'RATIO'].includes(attr.semanticType)) return 'MEASURE';
  if (['ENUM', 'CODE', 'HIERARCHY', 'LIFECYCLE', 'BOOLEAN'].includes(attr.semanticType)) return 'DIMENSION';
  return 'TEXT';
}

export default function MappingConfigPanel({ objectType, objectId, readOnly = false }: {
  objectType: RelationObjectType;
  objectId: number;
  readOnly?: boolean;
}) {
  const {
    events, entities, schemaMappings, fieldMappings,
    upsertSchemaMapping, upsertFieldMapping, deleteFieldMapping,
  } = useStore();
  const { snapshot: sourceSnapshot } = useSourceManagement();
  const object = objectType === 'EVENT'
    ? events.find((item) => item.id === objectId)
    : entities.find((item) => item.id === objectId);
  const attrs = object?.attributes || [];
  const savedSchema = schemaMappings.find((item) => item.objectType === objectType && item.objectId === objectId);
  const savedMappings = useMemo(
    () => fieldMappings.filter((item) => item.objectType === objectType && item.objectId === objectId),
    [fieldMappings, objectId, objectType],
  );
  const [datasource, setDatasource] = useState<string | undefined>(savedSchema?.datasourceName);
  const [tableName, setTableName] = useState<string | undefined>(savedSchema?.tableName);
  const [workTab, setWorkTab] = useState('preview');
  const [drafts, setDrafts] = useState<Record<string, MappingDraft>>({});

  const physicalSources = useMemo(() => sourceSnapshot ? projectPhysicalSources(sourceSnapshot) : [], [sourceSnapshot]);
  const datasourceOptions = useMemo(() => {
    const options = physicalSources.map(({ connection, connectorType }) => ({
      value: connection.connectionId,
      label: `${connection.displayName}（${connectorType.vendor}）`,
    }));
    if (savedSchema?.datasourceName && !options.some((item) => item.value === savedSchema.datasourceName)) {
      options.push({ value: savedSchema.datasourceName, label: `${savedSchema.datasourceName}（历史发布映射）` });
    }
    return options;
  }, [physicalSources, savedSchema?.datasourceName]);
  const tables = useMemo<PhysicalTable[]>(() => physicalSources.flatMap(({ connection, snapshot }) =>
    (snapshot.physicalSchema ?? []).map((table) => ({
      ...table,
      datasource: connection.connectionId,
      snapshotId: snapshot.snapshotId,
      connectionRevision: snapshot.connectionRevision,
    }))), [physicalSources]);

  const availableTables = tables.filter((table) => table.datasource === datasource);
  const selectedTable = tables.find((table) => table.datasource === datasource && table.tableName === tableName);

  useEffect(() => {
    const source = physicalSources.find(({ connection }) => connection.connectionId === savedSchema?.datasourceName
      || connection.displayName === savedSchema?.datasourceName);
    setDatasource(source?.connection.connectionId ?? savedSchema?.datasourceName);
    setTableName(savedSchema?.tableName);
    setWorkTab('preview');
  }, [objectId, objectType, physicalSources, savedSchema?.datasourceName, savedSchema?.tableName]);

  useEffect(() => {
    if (!selectedTable) {
      setDrafts({});
      return;
    }
    const next: Record<string, MappingDraft> = {};
    selectedTable.fields.forEach((field) => {
      const mapping = savedMappings.find((item) => item.physicalFieldName === field.name);
      next[field.name] = mapping
        ? { mappingType: 'ATTRIBUTE', attributeId: mapping.attributeId, sqlTransform: mapping.sqlTransform }
        : { mappingType: 'IGNORE' };
    });
    setDrafts(next);
  }, [objectId, objectType, savedMappings, selectedTable]);

  const mappedAttributeIds = new Set(Object.values(drafts).flatMap((draft) => draft.attributeId ? [draft.attributeId] : []));
  const completion = attrs.length ? Math.round((mappedAttributeIds.size / attrs.length) * 100) : 0;

  const chooseTable = (nextTableName: string) => {
    setTableName(nextTableName);
    const table = tables.find((item) => item.datasource === datasource && item.tableName === nextTableName);
    if (!table || !datasource) return;
    upsertSchemaMapping({
      objectType,
      objectId,
      datasourceName: datasource,
      schemaName: table.schemaName,
      tableName: table.tableName,
      tableAlias: table.tableName.split('_').pop()?.slice(0, 1),
    });
    setWorkTab('preview');
    message.success(`已选择快照中的数据表“${table.label}”`);
  };

  const autoMatch = () => {
    if (!selectedTable) return;
    setDrafts((current) => {
      const next = { ...current };
      selectedTable.fields.forEach((field) => {
        const attribute = attrs.find((attr) => attr.attributeCode.toLowerCase() === field.name.toLowerCase());
        if (attribute) next[field.name] = { ...next[field.name], mappingType: 'ATTRIBUTE', attributeId: attribute.id };
      });
      return next;
    });
    message.success('已按字段 ID 自动匹配本体属性');
  };

  const saveMappings = () => {
    if (!selectedTable) return;
    const selectedIds = Object.values(drafts)
      .filter((draft) => draft.mappingType === 'ATTRIBUTE' && draft.attributeId)
      .map((draft) => draft.attributeId as number);
    if (new Set(selectedIds).size !== selectedIds.length) {
      message.error('同一个本体属性不能同时映射多个表字段');
      return;
    }
    selectedTable.fields.forEach((field) => {
      const draft = drafts[field.name];
      const existingByField = savedMappings.find((mapping) => mapping.physicalFieldName === field.name);
      if (!draft || draft.mappingType === 'IGNORE' || !draft.attributeId) {
        if (existingByField) deleteFieldMapping(existingByField.id);
        return;
      }
      if (existingByField && existingByField.attributeId !== draft.attributeId) deleteFieldMapping(existingByField.id);
      const attr = attrs.find((item) => item.id === draft.attributeId);
      if (!attr) return;
      upsertFieldMapping({
        objectType,
        objectId,
        attributeId: attr.id,
        physicalFieldName: field.name,
        physicalDataType: field.dataType,
        fieldRole: defaultFieldRole(attr),
        sqlTransform: draft.sqlTransform?.trim() || undefined,
      });
    });
    message.success('字段映射已保存');
  };

  const mappingColumns = [
    {
      title: '表字段',
      key: 'field',
      width: 180,
      render: (_: unknown, field: PhysicalField) => (
        <Space direction="vertical" size={0}>
          <Text strong>{field.name}</Text>
          <Tag style={{ marginTop: 3 }}>{field.dataType}</Tag>
        </Space>
      ),
    },
    {
      title: '映射类型',
      key: 'mappingType',
      width: 130,
      render: (_: unknown, field: PhysicalField) => (
        <Select
          disabled={readOnly}
          value={drafts[field.name]?.mappingType || 'IGNORE'}
          style={{ width: '100%' }}
          options={[{ value: 'ATTRIBUTE', label: '属性' }, { value: 'IGNORE', label: '忽略' }]}
          onChange={(value) => setDrafts((current) => ({
            ...current,
            [field.name]: {
              ...current[field.name],
              mappingType: value,
              attributeId: value === 'IGNORE' ? undefined : current[field.name]?.attributeId,
            },
          }))}
        />
      ),
    },
    {
      title: '属性目标',
      key: 'attribute',
      width: 250,
      render: (_: unknown, field: PhysicalField) => (
        <Select
          showSearch
          optionFilterProp="label"
          allowClear
          disabled={readOnly || drafts[field.name]?.mappingType === 'IGNORE'}
          value={drafts[field.name]?.attributeId}
          style={{ width: '100%' }}
          placeholder="选择当前本体属性"
          options={attrs.map((attr) => ({ value: attr.id, label: `${attr.attributeName}（${attr.attributeCode}）` }))}
          onChange={(attributeId) => setDrafts((current) => ({
            ...current,
            [field.name]: { ...current[field.name], mappingType: 'ATTRIBUTE', attributeId },
          }))}
        />
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 90,
      render: (_: unknown, field: PhysicalField) => drafts[field.name]?.mappingType === 'ATTRIBUTE' && drafts[field.name]?.attributeId
        ? <Tag color="green" icon={<CheckCircleOutlined />}>已配置</Tag>
        : <Tag>已忽略</Tag>,
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small" title={<Space><DatabaseOutlined /> 选择物理数据</Space>}>
        <Steps
          size="small"
          current={!datasource ? 0 : !selectedTable ? 1 : 2}
          items={[
            { title: '选择数据源' },
            { title: '选择数据表' },
            { title: '确认结构与字段映射' },
          ]}
          style={{ marginBottom: 20 }}
        />
        <div className="mapping-source-grid">
          <div>
            <Text strong>数据源</Text>
            <Select
              value={datasource}
              placeholder="请选择数据源"
              options={datasourceOptions}
              style={{ width: '100%', marginTop: 8 }}
              onChange={(value) => {
                setDatasource(value);
                setTableName(undefined);
                setDrafts({});
              }}
            />
          </div>
          <div>
            <Text strong>数据表</Text>
            <Select
              showSearch
              optionFilterProp="label"
              value={tableName}
              disabled={!datasource}
              placeholder={datasource ? '请选择数据表' : '请先选择数据源'}
              options={availableTables.map((table) => ({
                value: table.tableName,
                label: `${table.label}（${table.schemaName}.${table.tableName}）`,
              }))}
              style={{ width: '100%', marginTop: 8 }}
              onChange={chooseTable}
            />
          </div>
        </div>
      </Card>

      {selectedTable ? (
        <Card size="small">
          <Tabs
            activeKey={workTab}
            onChange={setWorkTab}
            tabBarExtraContent={workTab === 'mapping' && !readOnly ? (
              <Space className="mapping-actions" wrap>
                <div style={{ width: 150 }}>
                  <Text type="secondary">已配置 {mappedAttributeIds.size}/{attrs.length} 个属性</Text>
                  <Progress percent={completion} size="small" showInfo={false} />
                </div>
                <Button icon={<ThunderboltOutlined />} onClick={autoMatch}>自动匹配</Button>
                <Button type="primary" icon={<SaveOutlined />} onClick={saveMappings}>保存映射</Button>
              </Space>
            ) : undefined}
            items={[
              {
                key: 'preview',
                label: '快照结构',
                children: (
                  <Space direction="vertical" size={12} style={{ width: '100%' }}>
                    <Typography.Text type="secondary">
                      来源快照 {selectedTable.snapshotId} · 配置 Revision {selectedTable.connectionRevision}
                    </Typography.Text>
                    <Table
                      dataSource={selectedTable.fields}
                      columns={[
                        { title: '字段', dataIndex: 'label' },
                        { title: '物理字段', dataIndex: 'name', render: (value: string) => <code>{value}</code> },
                        { title: '数据类型', dataIndex: 'dataType' },
                      ]}
                      rowKey="name"
                      size="small"
                      pagination={false}
                    />
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本快照未保存可展示的脱敏行样例；不会生成模拟预览数据。" />
                  </Space>
                ),
              },
              {
                key: 'mapping',
                label: '字段映射',
                children: (
                  <Table
                    dataSource={selectedTable.fields}
                    columns={mappingColumns}
                    rowKey="name"
                    size="small"
                    pagination={false}
                    scroll={{ x: 900 }}
                  />
                ),
              },
            ]}
          />
        </Card>
      ) : (
        <Card size="small"><Empty description={physicalSources.length ? '所选快照没有保存当前对象可用的物理表结构' : '当前项目尚无已激活并读取的物理结构快照'} /></Card>
      )}
    </Space>
  );
}
