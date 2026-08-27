import { useMemo, useState } from 'react';
import { Card, Descriptions, Empty, Space, Tag, Typography } from 'antd';
import { useStore } from '../../store/useStore';

const { Text } = Typography;

type GraphNodeType = 'ENTITY' | 'EVENT' | 'METRIC';

interface GraphNode {
  key: string;
  objectId: number;
  type: GraphNodeType;
  name: string;
  code: string;
  x: number;
  y: number;
  attributeCount?: number;
}

interface GraphEdge {
  key: string;
  source: string;
  target: string;
  label: string;
  type: 'RELATION' | 'METRIC_BINDING';
}

const nodeColors: Record<GraphNodeType, { fill: string; stroke: string; tag: string }> = {
  ENTITY: { fill: '#e6f4ff', stroke: '#1677ff', tag: 'blue' },
  EVENT: { fill: '#f9f0ff', stroke: '#722ed1', tag: 'purple' },
  METRIC: { fill: '#fff7e6', stroke: '#d46b08', tag: 'orange' },
};

const nodeTypeLabels: Record<GraphNodeType, string> = {
  ENTITY: '实体',
  EVENT: '事件',
  METRIC: '指标',
};

function distribute(index: number, count: number, min: number, max: number) {
  if (count <= 1) return (min + max) / 2;
  return min + (index * (max - min)) / (count - 1);
}

export default function KnowledgeGraphPanel() {
  const { entities, events, metrics, semanticRelations } = useStore();
  const [selectedKey, setSelectedKey] = useState<string | undefined>(() => events[0] ? `EVENT-${events[0].id}` : undefined);

  const nodes = useMemo<GraphNode[]>(() => [
    ...entities.map((entity, index) => ({
      key: `ENTITY-${entity.id}`,
      objectId: entity.id,
      type: 'ENTITY' as const,
      name: entity.entityName,
      code: entity.entityCode,
      x: 120,
      y: distribute(index, entities.length, 75, 545),
      attributeCount: entity.attributes.length,
    })),
    ...events.map((event, index) => ({
      key: `EVENT-${event.id}`,
      objectId: event.id,
      type: 'EVENT' as const,
      name: event.eventName,
      code: event.eventCode,
      x: 500,
      y: distribute(index, events.length, 145, 475),
      attributeCount: event.attributes.length,
    })),
    ...metrics.map((metric, index) => ({
      key: `METRIC-${metric.id}`,
      objectId: metric.id,
      type: 'METRIC' as const,
      name: metric.metricName,
      code: metric.metricCode,
      x: 830,
      y: distribute(index, metrics.length, 55, 565),
    })),
  ], [entities, events, metrics]);

  const edges = useMemo<GraphEdge[]>(() => [
    ...semanticRelations.map((relation) => ({
      key: `RELATION-${relation.id}`,
      source: `${relation.sourceType}-${relation.sourceId}`,
      target: `${relation.targetType}-${relation.targetId}`,
      label: relation.relationName,
      type: 'RELATION' as const,
    })),
    ...metrics.filter((metric) => metric.eventId).map((metric) => ({
      key: `METRIC-${metric.id}`,
      source: `EVENT-${metric.eventId}`,
      target: `METRIC-${metric.id}`,
      label: metric.metricType === 'COMPOSITE' ? '复合指标' : '基础指标',
      type: 'METRIC_BINDING' as const,
    })),
  ], [metrics, semanticRelations]);

  const nodeMap = useMemo(() => new Map(nodes.map((node) => [node.key, node])), [nodes]);
  const selected = selectedKey ? nodeMap.get(selectedKey) : undefined;
  const selectedEdges = selectedKey ? edges.filter((edge) => edge.source === selectedKey || edge.target === selectedKey) : [];

  return (
    <div style={{ padding: '0 16px 16px' }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 260px', gap: 16 }}>
        <Card
          size="small"
          title="本体知识图谱"
          extra={<Space size={12}>
            <Text type="secondary">{nodes.length} 个节点</Text>
            <Text type="secondary">{edges.length} 条关系</Text>
          </Space>}
        >
          <Space size={16} style={{ marginBottom: 8 }}>
            {(Object.keys(nodeTypeLabels) as GraphNodeType[]).map((type) => (
              <Space key={type} size={5}>
                <span aria-hidden="true" style={{ width: 10, height: 10, borderRadius: type === 'EVENT' ? 2 : 10, background: nodeColors[type].stroke, display: 'inline-block' }} />
                <Text type="secondary">{nodeTypeLabels[type]}</Text>
              </Space>
            ))}
          </Space>
          {nodes.length ? (
            <svg
              viewBox="0 0 1000 620"
              width="100%"
              role="img"
              aria-label={`业务知识图谱，包含 ${entities.length} 个实体、${events.length} 个事件、${metrics.length} 个指标和 ${edges.length} 条关系`}
              style={{ display: 'block', minHeight: 520 }}
            >
              <title>本体知识图谱</title>
              <desc>左侧为实体，中间为事件，右侧为指标；连线表示对象关系和指标归属。</desc>
              {edges.map((edge) => {
                const source = nodeMap.get(edge.source);
                const target = nodeMap.get(edge.target);
                if (!source || !target) return null;
                const highlighted = selectedKey === edge.source || selectedKey === edge.target;
                return (
                  <g key={edge.key}>
                    <line
                      x1={source.x}
                      y1={source.y}
                      x2={target.x}
                      y2={target.y}
                      stroke={edge.type === 'METRIC_BINDING' ? '#d46b08' : '#91caff'}
                      strokeWidth={highlighted ? 3 : 1.5}
                      opacity={selectedKey && !highlighted ? 0.25 : 0.8}
                    />
                    {highlighted && (
                      <text
                        x={(source.x + target.x) / 2}
                        y={(source.y + target.y) / 2 - 6}
                        textAnchor="middle"
                        fontSize="12"
                        fill="#595959"
                      >
                        {edge.label}
                      </text>
                    )}
                  </g>
                );
              })}
              {nodes.map((node) => {
                const color = nodeColors[node.type];
                const selectedNode = selectedKey === node.key;
                return (
                  <g
                    key={node.key}
                    role="button"
                    tabIndex={0}
                    aria-label={`${nodeTypeLabels[node.type]}：${node.name}`}
                    transform={`translate(${node.x - 75} ${node.y - 26})`}
                    onClick={() => setSelectedKey(node.key)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') setSelectedKey(node.key);
                    }}
                    style={{ cursor: 'pointer', outline: 'none' }}
                  >
                    <rect
                      width="150"
                      height="52"
                      rx={node.type === 'EVENT' ? 8 : 26}
                      fill={color.fill}
                      stroke={color.stroke}
                      strokeWidth={selectedNode ? 3 : 1.5}
                    />
                    <text x="75" y="22" textAnchor="middle" fontSize="14" fontWeight="500" fill="#262626">{node.name}</text>
                    <text x="75" y="39" textAnchor="middle" fontSize="11" fill="#8c8c8c">{node.code}</text>
                  </g>
                );
              })}
            </svg>
          ) : <Empty description="暂无可展示的本体图谱" />}
        </Card>

        <Card size="small" title="节点详情">
          {selected ? (
            <>
              <Tag color={nodeColors[selected.type].tag} style={{ marginBottom: 12 }}>{nodeTypeLabels[selected.type]}</Tag>
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="名称">{selected.name}</Descriptions.Item>
                <Descriptions.Item label="ID">{selected.code}</Descriptions.Item>
                {selected.attributeCount !== undefined && <Descriptions.Item label="属性数">{selected.attributeCount}</Descriptions.Item>}
                <Descriptions.Item label="关系数">{selectedEdges.length}</Descriptions.Item>
              </Descriptions>
              {selectedEdges.length > 0 && (
                <div style={{ marginTop: 12 }}>
                  <Text strong>关联关系</Text>
                  <Space direction="vertical" size={6} style={{ width: '100%', marginTop: 8 }}>
                    {selectedEdges.map((edge) => {
                      const peer = nodeMap.get(edge.source === selected.key ? edge.target : edge.source);
                      return <Text key={edge.key} type="secondary">{edge.label} · {peer?.name}</Text>;
                    })}
                  </Space>
                </div>
              )}
            </>
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择节点查看详情" />}
        </Card>
      </div>
    </div>
  );
}
