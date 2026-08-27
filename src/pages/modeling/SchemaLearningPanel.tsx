import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Progress, Space, Table, Tag, Typography, message } from 'antd';
import { ReadOutlined, ReloadOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import { SemanticObjectTag } from '../../components/semantic-object-visuals';

const { Text } = Typography;

interface SchemaLearningPanelProps {
  onOpenSchema: () => void;
}

interface SchemaRow {
  key: string;
  id: number;
  type: 'ENTITY' | 'EVENT';
  name: string;
  code: string;
  attributeCount: number;
  mappingCount: number;
}

export default function SchemaLearningPanel({ onOpenSchema }: SchemaLearningPanelProps) {
  const { entities, events, fieldMappings } = useStore();
  const [learnedKeys, setLearnedKeys] = useState<Set<string>>(new Set());
  const [learning, setLearning] = useState(false);
  const timers = useRef<number[]>([]);

  const rows = useMemo<SchemaRow[]>(() => [
    ...entities.map((entity) => ({
      key: `ENTITY-${entity.id}`,
      id: entity.id,
      type: 'ENTITY' as const,
      name: entity.entityName,
      code: entity.entityCode,
      attributeCount: entity.attributes.length,
      mappingCount: fieldMappings.filter((mapping) => mapping.objectType === 'ENTITY' && mapping.objectId === entity.id).length,
    })),
    ...events.map((event) => ({
      key: `EVENT-${event.id}`,
      id: event.id,
      type: 'EVENT' as const,
      name: event.eventName,
      code: event.eventCode,
      attributeCount: event.attributes.length,
      mappingCount: fieldMappings.filter((mapping) => mapping.objectType === 'EVENT' && mapping.objectId === event.id).length,
    })),
  ], [entities, events, fieldMappings]);

  useEffect(() => () => {
    timers.current.forEach((timer) => window.clearTimeout(timer));
  }, []);

  const learnOne = (key: string) => {
    setLearnedKeys((current) => new Set(current).add(key));
    message.success('本体学习完成');
  };

  const learnAll = () => {
    timers.current.forEach((timer) => window.clearTimeout(timer));
    timers.current = [];
    setLearnedKeys(new Set());
    setLearning(true);
    rows.forEach((row, index) => {
      const timer = window.setTimeout(() => {
        setLearnedKeys((current) => new Set(current).add(row.key));
        if (index === rows.length - 1) {
          setLearning(false);
          message.success(`已完成 ${rows.length} 个本体的批量学习`);
        }
      }, 220 * (index + 1));
      timers.current.push(timer);
    });
    if (rows.length === 0) setLearning(false);
  };

  const learnedCount = learnedKeys.size;
  const progress = rows.length ? Math.round((learnedCount / rows.length) * 100) : 0;

  const columns = [
    {
      title: '本体',
      key: 'schema',
      render: (_: unknown, row: SchemaRow) => (
        <Space direction="vertical" size={0}>
          <Text strong>{row.name}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{row.code}</Text>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 90,
      render: (type: SchemaRow['type']) => <SemanticObjectTag kind={type} />,
    },
    { title: '属性数', dataIndex: 'attributeCount', key: 'attributeCount', width: 90 },
    { title: '已映射字段', dataIndex: 'mappingCount', key: 'mappingCount', width: 110 },
    {
      title: '学习状态',
      key: 'status',
      width: 110,
      render: (_: unknown, row: SchemaRow) => learnedKeys.has(row.key)
        ? <Tag color="green">已学习</Tag>
        : learning ? <Tag color="processing">等待中</Tag> : <Tag>未学习</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: unknown, row: SchemaRow) => (
        <Button type="link" size="small" disabled={learning} onClick={() => learnOne(row.key)}>
          {learnedKeys.has(row.key) ? '重新学习' : '学习'}
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: '0 16px 16px' }}>
      <Card
        size="small"
        title={<Space><ReadOutlined /> 学习所有本体</Space>}
        extra={<Space>
          <Button icon={<UnorderedListOutlined />} onClick={onOpenSchema}>进入本体列表</Button>
          <Button type="primary" icon={learning ? <ReloadOutlined spin /> : <ReadOutlined />} loading={learning} onClick={learnAll}>
            {learning ? '正在学习' : learnedCount ? '重新学习全部' : '学习所有本体'}
          </Button>
        </Space>}
      >
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 12, marginBottom: 16 }}>
          <Card size="small"><Text type="secondary">本体总数</Text><div style={{ fontSize: 24, fontWeight: 600 }}>{rows.length}</div></Card>
          <Card size="small"><Text type="secondary">已学习</Text><div style={{ fontSize: 24, fontWeight: 600, color: '#389e0d' }}>{learnedCount}</div></Card>
          <Card size="small"><Text type="secondary">待学习</Text><div style={{ fontSize: 24, fontWeight: 600 }}>{Math.max(rows.length - learnedCount, 0)}</div></Card>
        </div>
        <Progress percent={progress} status={learning ? 'active' : 'normal'} style={{ marginBottom: 16 }} />
        <Table dataSource={rows} columns={columns} rowKey="key" size="small" pagination={false} />
      </Card>
    </div>
  );
}
