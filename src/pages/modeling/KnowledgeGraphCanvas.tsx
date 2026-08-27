import { useMemo, useState } from 'react';
import { PushpinOutlined } from '@ant-design/icons';
import { Descriptions, Space, Tag, Typography } from 'antd';
import { useStore } from '../../store/useStore';
import type { RelationObjectType, SemanticEntity, SemanticEvent } from '../../types';
import { SemanticObjectTag, semanticObjectVisual } from '../../components/semantic-object-visuals';

const { Text, Title } = Typography;
const entityVisual = semanticObjectVisual('ENTITY');
const eventVisual = semanticObjectVisual('EVENT');

interface SchemaNode {
  key: string;
  objectId: number;
  type: RelationObjectType;
  name: string;
  code: string;
  description?: string;
  attributeCount: number;
  metricCount: number;
  relationCount: number;
  x: number;
  y: number;
}

interface SchemaEdge {
  key: string;
  source: string;
  target: string;
  label: string;
}

const NODE_WIDTH = 176;
const NODE_HEIGHT = 78;

const nodeTheme = {
  EVENT: {
    fill: eventVisual.fill,
    stroke: eventVisual.stroke,
    badgeFill: eventVisual.fill,
    badgeText: eventVisual.stroke,
    label: '事件',
  },
  ENTITY: {
    fill: entityVisual.fill,
    stroke: entityVisual.stroke,
    badgeFill: entityVisual.fill,
    badgeText: entityVisual.stroke,
    label: '实体',
  },
} satisfies Record<RelationObjectType, {
  fill: string;
  stroke: string;
  badgeFill: string;
  badgeText: string;
  label: string;
}>;

function distribute(index: number, count: number, min: number, max: number) {
  if (count <= 1) return (min + max) / 2;
  return min + (index * (max - min)) / (count - 1);
}

function curvePath(source: SchemaNode, target: SchemaNode) {
  const sourceOnLeft = source.x <= target.x;
  const startX = source.x + (sourceOnLeft ? NODE_WIDTH / 2 : -NODE_WIDTH / 2);
  const endX = target.x + (sourceOnLeft ? -NODE_WIDTH / 2 : NODE_WIDTH / 2);
  const controlOffset = Math.max(Math.abs(endX - startX) * 0.48, 90);
  const firstControlX = startX + (sourceOnLeft ? controlOffset : -controlOffset);
  const secondControlX = endX - (sourceOnLeft ? controlOffset : -controlOffset);
  return `M ${startX} ${source.y} C ${firstControlX} ${source.y}, ${secondControlX} ${target.y}, ${endX} ${target.y}`;
}

export default function KnowledgeGraphCanvas() {
  const { entities, events, metrics, semanticRelations } = useStore();
  const [selectedKey, setSelectedKey] = useState<string>();

  const relationCountByObject = useMemo(() => {
    const counts = new Map<string, number>();
    semanticRelations.forEach((relation) => {
      const sourceKey = `${relation.sourceType}-${relation.sourceId}`;
      const targetKey = `${relation.targetType}-${relation.targetId}`;
      counts.set(sourceKey, (counts.get(sourceKey) || 0) + 1);
      counts.set(targetKey, (counts.get(targetKey) || 0) + 1);
    });
    return counts;
  }, [semanticRelations]);

  const nodes = useMemo<SchemaNode[]>(() => {
    const eventNodes = events.map((event, index) => ({
      key: `EVENT-${event.id}`,
      objectId: event.id,
      type: 'EVENT' as const,
      name: event.eventName,
      code: event.eventCode,
      description: event.description,
      attributeCount: event.attributes.length,
      metricCount: metrics.filter((metric) => metric.eventId === event.id).length,
      relationCount: relationCountByObject.get(`EVENT-${event.id}`) || 0,
      x: index % 2 === 0 ? 315 : 365,
      y: distribute(index, events.length, 145, 575),
    }));
    const entityNodes = entities.map((entity, index) => ({
      key: `ENTITY-${entity.id}`,
      objectId: entity.id,
      type: 'ENTITY' as const,
      name: entity.entityName,
      code: entity.entityCode,
      description: entity.description,
      attributeCount: entity.attributes.length,
      metricCount: 0,
      relationCount: relationCountByObject.get(`ENTITY-${entity.id}`) || 0,
      x: index === entities.length - 1 && entities.length > 2 ? 920 : 700,
      y: distribute(index, entities.length, 110, 585),
    }));
    return [...eventNodes, ...entityNodes];
  }, [entities, events, metrics, relationCountByObject]);

  const edges = useMemo<SchemaEdge[]>(() => semanticRelations.map((relation) => ({
    key: `RELATION-${relation.id}`,
    source: `${relation.sourceType}-${relation.sourceId}`,
    target: `${relation.targetType}-${relation.targetId}`,
    label: relation.relationName,
  })), [semanticRelations]);

  const nodeMap = useMemo(() => new Map(nodes.map((node) => [node.key, node])), [nodes]);
  const selectedNode = selectedKey ? nodeMap.get(selectedKey) : undefined;
  const connectedKeys = useMemo(() => {
    if (!selectedKey) return new Set<string>();
    const connected = new Set<string>([selectedKey]);
    edges.forEach((edge) => {
      if (edge.source === selectedKey) connected.add(edge.target);
      if (edge.target === selectedKey) connected.add(edge.source);
    });
    return connected;
  }, [edges, selectedKey]);

  const selectedObject: SemanticEvent | SemanticEntity | undefined = selectedNode
    ? selectedNode.type === 'EVENT'
      ? events.find((event) => event.id === selectedNode.objectId)
      : entities.find((entity) => entity.id === selectedNode.objectId)
    : undefined;
  const selectedMetrics = selectedNode?.type === 'EVENT'
    ? metrics.filter((metric) => metric.eventId === selectedNode.objectId)
    : [];
  const expandedMetricNodes = selectedMetrics.map((metric, index) => ({
    metric,
    x: 105,
    y: distribute(index, selectedMetrics.length, 115, 625),
  }));

  return (
    <div style={{ padding: '0 16px 16px' }}>
      <div
        style={{
          position: 'relative',
          minHeight: 690,
          overflow: 'hidden',
          border: '1px solid #e5e7eb',
          borderRadius: 8,
          backgroundColor: '#fbfcfe',
          backgroundImage: 'radial-gradient(circle, #d9dee8 1px, transparent 1px)',
          backgroundSize: '14px 14px',
        }}
      >
        <div style={{ position: 'absolute', top: 16, left: 18, zIndex: 3 }}>
          <Space size={14}>
            <SemanticObjectTag kind="EVENT" label={`事件 ${events.length}`} />
            <SemanticObjectTag kind="ENTITY" label={`实体 ${entities.length}`} />
            <SemanticObjectTag kind="RELATION" label={`关系 ${edges.length}`} />
            <SemanticObjectTag kind="METRIC" label={`指标 ${metrics.length}`} />
          </Space>
        </div>

        <svg
          viewBox="0 0 1120 690"
          width="100%"
          role="img"
          aria-label={`本体知识图谱，包含 ${events.length} 个事件、${entities.length} 个实体、${metrics.length} 个指标和 ${edges.length} 条关系`}
          style={{ display: 'block', minHeight: 690 }}
          onClick={() => setSelectedKey(undefined)}
        >
          <title>本体知识图谱</title>
          <desc>黄色节点为事件，蓝色节点为实体；箭头和文字表示本体之间的业务关系。</desc>
          <defs>
            <filter id="schema-node-shadow" x="-20%" y="-30%" width="140%" height="160%">
              <feDropShadow dx="0" dy="3" stdDeviation="4" floodColor="#64748b" floodOpacity="0.16" />
            </filter>
            <marker id="graph-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="strokeWidth">
              <path d="M 0 0 L 8 4 L 0 8 z" fill="#8c98a8" />
            </marker>
            <marker id="graph-arrow-active" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="strokeWidth">
              <path d="M 0 0 L 8 4 L 0 8 z" fill="#1677ff" />
            </marker>
            <marker id="metric-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="strokeWidth">
              <path d="M 0 0 L 8 4 L 0 8 z" fill="#27a9a1" />
            </marker>
          </defs>

          {selectedNode?.type === 'EVENT' && expandedMetricNodes.map(({ metric, x, y }) => {
            const startX = selectedNode.x - NODE_WIDTH / 2;
            const endX = x + 75;
            const middleX = (startX + endX) / 2;
            return (
              <path
                key={`METRIC-EDGE-${metric.id}`}
                d={`M ${startX} ${selectedNode.y} C ${middleX} ${selectedNode.y}, ${middleX} ${y}, ${endX} ${y}`}
                fill="none"
                stroke="#27a9a1"
                strokeWidth="2"
                strokeDasharray="5 5"
                markerEnd="url(#metric-arrow)"
              />
            );
          })}

          {edges.map((edge) => {
            const source = nodeMap.get(edge.source);
            const target = nodeMap.get(edge.target);
            if (!source || !target) return null;
            const active = !!selectedKey && (edge.source === selectedKey || edge.target === selectedKey);
            const muted = !!selectedKey && !active;
            return (
              <g key={edge.key} opacity={muted ? 0.12 : 1}>
                <path
                  d={curvePath(source, target)}
                  fill="none"
                  stroke={active ? '#1677ff' : '#8c98a8'}
                  strokeWidth={active ? 3 : 1.6}
                  markerEnd={active ? 'url(#graph-arrow-active)' : 'url(#graph-arrow)'}
                />
                <text
                  x={(source.x + target.x) / 2}
                  y={(source.y + target.y) / 2 - 7}
                  textAnchor="middle"
                  fontSize="11"
                  fontWeight={active ? 500 : 400}
                  fill={active ? '#0958d9' : '#596579'}
                  paintOrder="stroke"
                  stroke="#fbfcfe"
                  strokeWidth="4"
                >
                  {edge.label}
                </text>
              </g>
            );
          })}

          {nodes.map((node) => {
            const theme = nodeTheme[node.type];
            const selected = node.key === selectedKey;
            const muted = !!selectedKey && !connectedKeys.has(node.key);
            return (
              <g
                key={node.key}
                role="button"
                tabIndex={0}
                aria-label={`${theme.label}：${node.name}`}
                transform={`translate(${node.x - NODE_WIDTH / 2} ${node.y - NODE_HEIGHT / 2})`}
                opacity={muted ? 0.16 : 1}
                filter="url(#schema-node-shadow)"
                onClick={(event) => {
                  event.stopPropagation();
                  setSelectedKey(node.key);
                }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') setSelectedKey(node.key);
                }}
                style={{ cursor: 'pointer', outline: 'none' }}
              >
                <rect
                  width={NODE_WIDTH}
                  height={NODE_HEIGHT}
                  rx="13"
                  fill={theme.fill}
                  stroke={selected ? '#1677ff' : theme.stroke}
                  strokeWidth={selected ? 3 : 1.4}
                />
                <text x="14" y="23" fontSize="15" fontWeight="500" fill="#1f2937">{node.name}</text>
                <rect x="129" y="9" width="35" height="20" rx="10" fill={theme.badgeFill} />
                <text x="146.5" y="23" textAnchor="middle" fontSize="10" fontWeight="500" fill={theme.badgeText}>{theme.label}</text>
                <text x="14" y="43" fontSize="10.5" fill="#8a94a6">{node.code}</text>
                <line x1="14" y1="51" x2="162" y2="51" stroke={theme.stroke} strokeOpacity="0.25" />
                <text x="14" y="68" fontSize="10.5" fill="#596579">
                  {node.attributeCount} 个属性　{node.metricCount} 个指标　{node.relationCount} 个关系
                </text>
              </g>
            );
          })}

          {selectedNode?.type === 'EVENT' && expandedMetricNodes.map(({ metric, x, y }) => (
            <g
              key={`METRIC-NODE-${metric.id}`}
              transform={`translate(${x - 75} ${y - 29})`}
              filter="url(#schema-node-shadow)"
              aria-label={`指标：${metric.metricName}`}
            >
              <rect width="150" height="58" rx="11" fill="#ecfffb" stroke="#43bdb4" strokeWidth="1.5" />
              <text x="12" y="22" fontSize="13" fontWeight="500" fill="#1f2937">{metric.metricName}</text>
              <rect x="111" y="8" width="29" height="18" rx="9" fill="#d8f7f1" />
              <text x="125.5" y="20.5" textAnchor="middle" fontSize="9.5" fontWeight="500" fill="#138a82">指标</text>
              <text x="12" y="39" fontSize="9.5" fill="#7b8797">{metric.metricCode}</text>
              <text x="12" y="52" fontSize="9.5" fill="#4d6670">
                {metric.metricType === 'COMPOSITE' ? '复合指标' : '基础指标'} · {metric.status === 'PUBLISHED' ? '已发布' : '草稿'}
              </text>
            </g>
          ))}
        </svg>

        {selectedNode && selectedObject && (
          <div
            style={{
              position: 'absolute',
              top: 58,
              right: 18,
              zIndex: 4,
              width: 360,
              padding: '18px 20px',
              border: `1px solid ${nodeTheme[selectedNode.type].stroke}`,
              borderRadius: 14,
              background: nodeTheme[selectedNode.type].fill,
              boxShadow: '0 14px 32px rgba(68, 84, 106, 0.18)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
              <div>
                <Title level={4} style={{ margin: 0 }}>{selectedNode.name}</Title>
                <Text type="secondary">{selectedNode.code}</Text>
              </div>
              <Space>
                <SemanticObjectTag kind={selectedNode.type} />
                <PushpinOutlined style={{ color: '#ad8b36' }} />
              </Space>
            </div>
            <Space size={8} wrap style={{ marginTop: 14 }}>
              <Tag>{selectedNode.attributeCount} 个属性</Tag>
              <Tag>{selectedNode.metricCount} 个指标</Tag>
              <Tag>{selectedNode.relationCount} 个关系</Tag>
            </Space>
            <div style={{ marginTop: 14 }}>
              <Text strong>描述</Text>
              <div style={{ marginTop: 6, color: '#3f4652', lineHeight: 1.7 }}>
                {selectedObject.description || '暂未维护该本体的业务描述。'}
              </div>
            </div>
            {selectedMetrics.length > 0 && (
              <Descriptions size="small" column={1} style={{ marginTop: 12 }}>
                <Descriptions.Item label="关联指标">
                  <Space size={[4, 4]} wrap>
                    {selectedMetrics.map((metric) => <SemanticObjectTag key={metric.id} kind="METRIC" label={metric.metricName} />)}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
