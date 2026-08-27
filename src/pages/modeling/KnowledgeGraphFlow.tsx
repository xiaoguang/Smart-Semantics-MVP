import { useEffect, useMemo, useState } from 'react';
import { PushpinOutlined } from '@ant-design/icons';
import { Descriptions, Drawer, Space, Tag, Typography } from 'antd';
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MarkerType,
  MiniMap,
  Panel,
  Position,
  ReactFlow,
  useEdgesState,
  useNodesState,
  type Edge,
  type Node,
  type NodeProps,
  type ReactFlowInstance,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useStore } from '../../store/useStore';
import type { RelationObjectType, SemanticEntity, SemanticEvent, SemanticMetric } from '../../types';
import { SemanticObjectTag, semanticObjectVisual } from '../../components/semantic-object-visuals';

const { Text, Title } = Typography;
const entityVisual = semanticObjectVisual('ENTITY');
const eventVisual = semanticObjectVisual('EVENT');
const metricVisual = semanticObjectVisual('METRIC');
const relationVisual = semanticObjectVisual('RELATION');

type SchemaNodeData = {
  kind: 'schema';
  objectId: number;
  objectType: RelationObjectType;
  name: string;
  code: string;
  attributeCount: number;
  metricCount: number;
  relationCount: number;
  selected: boolean;
  muted: boolean;
};

type MetricNodeData = {
  kind: 'metric';
  parentKey: string;
  metric: SemanticMetric;
};

type SchemaFlowNode = Node<SchemaNodeData, 'schema'>;
type MetricFlowNode = Node<MetricNodeData, 'metric'>;
type GraphNode = SchemaFlowNode | MetricFlowNode;

const schemaTheme = {
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

function SchemaNodeCard({ data }: NodeProps<SchemaFlowNode>) {
  const theme = schemaTheme[data.objectType];
  return (
    <div
      style={{
        width: 176,
        minHeight: 78,
        padding: '10px 13px 8px',
        border: `${data.selected ? 3 : 1.5}px solid ${data.selected ? '#1677ff' : theme.stroke}`,
        borderRadius: 13,
        background: theme.fill,
        boxShadow: '0 4px 12px rgba(68, 84, 106, 0.16)',
        opacity: data.muted ? 0.16 : 1,
        transition: 'opacity 180ms ease, border-color 180ms ease',
        boxSizing: 'border-box',
      }}
    >
      <Handle id="left-target" type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Handle id="left-source" type="source" position={Position.Left} style={{ opacity: 0 }} />
      <Handle id="right-target" type="target" position={Position.Right} style={{ opacity: 0 }} />
      <Handle id="right-source" type="source" position={Position.Right} style={{ opacity: 0 }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' }}>
        <Text strong ellipsis style={{ maxWidth: 112 }}>{data.name}</Text>
        <span style={{ padding: '1px 8px', borderRadius: 10, background: theme.badgeFill, color: theme.badgeText, fontSize: 11 }}>{theme.label}</span>
      </div>
      <div style={{ marginTop: 2, color: '#8a94a6', fontSize: 11 }}>{data.code}</div>
      <div style={{ height: 1, background: theme.stroke, opacity: 0.22, margin: '5px 0' }} />
      <div style={{ color: '#596579', fontSize: 10.5, whiteSpace: 'nowrap' }}>
        {data.attributeCount} 个属性　{data.metricCount} 个指标　{data.relationCount} 个关系
      </div>
    </div>
  );
}

function MetricNodeCard({ data }: NodeProps<MetricFlowNode>) {
  const metric = data.metric;
  return (
    <div
      style={{
        width: 150,
        minHeight: 60,
        padding: '8px 11px 7px',
        border: `1.5px solid ${metricVisual.stroke}`,
        borderRadius: 11,
        background: metricVisual.fill,
        boxShadow: '0 4px 12px rgba(39, 169, 161, 0.15)',
        boxSizing: 'border-box',
      }}
    >
      <Handle id="right-target" type="target" position={Position.Right} style={{ opacity: 0 }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 6 }}>
        <Text strong ellipsis style={{ maxWidth: 100, fontSize: 13 }}>{metric.metricName}</Text>
        <span style={{ color: metricVisual.stroke, fontSize: 10 }}>指标</span>
      </div>
      <div style={{ color: '#7b8797', fontSize: 10 }}>{metric.metricCode}</div>
      <div style={{ color: '#4d6670', fontSize: 10, marginTop: 3 }}>
        {metric.metricType === 'COMPOSITE' ? '复合指标' : '基础指标'} · {metric.status === 'PUBLISHED' ? '已发布' : '草稿'}
      </div>
    </div>
  );
}

const nodeTypes = {
  schema: SchemaNodeCard,
  metric: MetricNodeCard,
};

function distribute(index: number, count: number, min: number, max: number) {
  if (count <= 1) return (min + max) / 2;
  return min + (index * (max - min)) / (count - 1);
}

export default function KnowledgeGraphFlow() {
  const { entities, events, metrics, semanticRelations } = useStore();
  const [selectedKey, setSelectedKey] = useState<string>();
  const [flowInstance, setFlowInstance] = useState<ReactFlowInstance<GraphNode, Edge>>();
  const [nodes, setNodes, onNodesChange] = useNodesState<GraphNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedRelationId, setSelectedRelationId] = useState<number>();

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

  const basePositions = useMemo(() => {
    const positions = new Map<string, { x: number; y: number }>();
    events.forEach((event, index) => positions.set(`EVENT-${event.id}`, {
      x: index % 2 === 0 ? 330 : 390,
      y: distribute(index, events.length, 90, 540),
    }));
    entities.forEach((entity, index) => positions.set(`ENTITY-${entity.id}`, {
      x: index === entities.length - 1 && entities.length > 2 ? 890 : 700,
      y: distribute(index, entities.length, 60, 555),
    }));
    return positions;
  }, [entities, events]);

  const connectedKeys = useMemo(() => {
    if (!selectedKey) return new Set<string>();
    const connected = new Set([selectedKey]);
    semanticRelations.forEach((relation) => {
      const source = `${relation.sourceType}-${relation.sourceId}`;
      const target = `${relation.targetType}-${relation.targetId}`;
      if (source === selectedKey) connected.add(target);
      if (target === selectedKey) connected.add(source);
    });
    return connected;
  }, [selectedKey, semanticRelations]);

  const selectedEventId = selectedKey?.startsWith('EVENT-') ? Number(selectedKey.slice(6)) : undefined;
  const expandedMetrics = useMemo(
    () => selectedEventId ? metrics.filter((metric) => metric.eventId === selectedEventId) : [],
    [metrics, selectedEventId],
  );

  const derivedNodes = useMemo<GraphNode[]>(() => {
    const schemaNodes: SchemaFlowNode[] = [
      ...events.map<SchemaFlowNode>((event) => {
        const key = `EVENT-${event.id}`;
        return {
          id: key,
          type: 'schema',
          position: basePositions.get(key) || { x: 330, y: 100 },
          data: {
            kind: 'schema',
            objectId: event.id,
            objectType: 'EVENT',
            name: event.eventName,
            code: event.eventCode,
            attributeCount: event.attributes.length,
            metricCount: metrics.filter((metric) => metric.eventId === event.id).length,
            relationCount: relationCountByObject.get(key) || 0,
            selected: selectedKey === key,
            muted: !!selectedKey && !connectedKeys.has(key),
          },
        };
      }),
      ...entities.map<SchemaFlowNode>((entity) => {
        const key = `ENTITY-${entity.id}`;
        return {
          id: key,
          type: 'schema',
          position: basePositions.get(key) || { x: 700, y: 100 },
          data: {
            kind: 'schema',
            objectId: entity.id,
            objectType: 'ENTITY',
            name: entity.entityName,
            code: entity.entityCode,
            attributeCount: entity.attributes.length,
            metricCount: 0,
            relationCount: relationCountByObject.get(key) || 0,
            selected: selectedKey === key,
            muted: !!selectedKey && !connectedKeys.has(key),
          },
        };
      }),
    ];
    const selectedPosition = selectedKey ? basePositions.get(selectedKey) : undefined;
    const metricNodes: MetricFlowNode[] = expandedMetrics.map((metric, index) => ({
      id: `METRIC-${metric.id}`,
      type: 'metric',
      position: {
        x: Math.max((selectedPosition?.x || 330) - 270, 20),
        y: distribute(index, expandedMetrics.length, 35, 560),
      },
      data: { kind: 'metric', parentKey: selectedKey || '', metric },
    }));
    return [...schemaNodes, ...metricNodes];
  }, [basePositions, connectedKeys, entities, events, expandedMetrics, metrics, relationCountByObject, selectedKey]);

  const derivedEdges = useMemo<Edge[]>(() => {
    const relationEdges = semanticRelations.map((relation) => {
      const source = `${relation.sourceType}-${relation.sourceId}`;
      const target = `${relation.targetType}-${relation.targetId}`;
      const sourcePosition = basePositions.get(source);
      const targetPosition = basePositions.get(target);
      const sourceOnLeft = (sourcePosition?.x || 0) <= (targetPosition?.x || 0);
      const active = !!selectedKey && (source === selectedKey || target === selectedKey);
      const muted = !!selectedKey && !active;
      return {
        id: `RELATION-${relation.id}`,
        source,
        target,
        sourceHandle: sourceOnLeft ? 'right-source' : 'left-source',
        targetHandle: sourceOnLeft ? 'left-target' : 'right-target',
        type: 'bezier',
        label: relation.relationName,
        style: {
          stroke: relationVisual.stroke,
          strokeWidth: active ? 3 : 1.6,
          opacity: muted ? 0.12 : 1,
        },
        labelStyle: { fill: relationVisual.stroke, fontSize: 11, fontWeight: active ? 600 : 400 },
        labelBgStyle: { fill: '#fbfcfe', fillOpacity: 0.88 },
        labelBgPadding: [4, 2] as [number, number],
        markerEnd: { type: MarkerType.ArrowClosed, color: relationVisual.stroke },
        zIndex: active ? 3 : 1,
      };
    });
    const metricEdges = expandedMetrics.map((metric) => ({
      id: `METRIC-EDGE-${metric.id}`,
      source: selectedKey || '',
      target: `METRIC-${metric.id}`,
      sourceHandle: 'left-source',
      targetHandle: 'right-target',
      type: 'bezier',
      animated: true,
      style: { stroke: metricVisual.stroke, strokeWidth: 2, strokeDasharray: '5 5' },
      markerEnd: { type: MarkerType.ArrowClosed, color: metricVisual.stroke },
      zIndex: 4,
    }));
    return [...relationEdges, ...metricEdges];
  }, [basePositions, expandedMetrics, selectedKey, semanticRelations]);

  useEffect(() => {
    setNodes((current) => {
      const currentPositions = new Map(current.map((node) => [node.id, node.position]));
      return derivedNodes.map((node) => ({
        ...node,
        position: currentPositions.get(node.id) || node.position,
      }));
    });
    setEdges(derivedEdges);
  }, [derivedEdges, derivedNodes, setEdges, setNodes]);

  useEffect(() => {
    if (!flowInstance || !selectedKey) return;
    const timer = window.setTimeout(() => flowInstance.fitView({ padding: 0.18, duration: 350, maxZoom: 1.15 }), 30);
    return () => window.clearTimeout(timer);
  }, [flowInstance, selectedKey]);

  const selectedSchemaNode = nodes.find((node): node is SchemaFlowNode => node.id === selectedKey && node.type === 'schema');
  const selectedObject: SemanticEvent | SemanticEntity | undefined = selectedSchemaNode
    ? selectedSchemaNode.data.objectType === 'EVENT'
      ? events.find((event) => event.id === selectedSchemaNode.data.objectId)
      : entities.find((entity) => entity.id === selectedSchemaNode.data.objectId)
    : undefined;
  const selectedRelation = semanticRelations.find((relation) => relation.id === selectedRelationId);
  const relationObjectName = (type: RelationObjectType, id: number) => type === 'EVENT'
    ? events.find((event) => event.id === id)?.eventName ?? `事件 ${id}`
    : entities.find((entity) => entity.id === id)?.entityName ?? `实体 ${id}`;
  const relationAttributeName = (type: RelationObjectType, objectId: number, attributeId?: number) => {
    const object = type === 'EVENT' ? events.find((event) => event.id === objectId) : entities.find((entity) => entity.id === objectId);
    return object?.attributes.find((attribute) => attribute.id === attributeId)?.attributeName ?? '未指定字段';
  };

  return (
    <div style={{ padding: '0 16px 16px' }}>
      <div style={{ height: 690, border: '1px solid #e5e7eb', borderRadius: 8, overflow: 'hidden', background: '#fbfcfe' }}>
        <ReactFlow<GraphNode, Edge>
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onInit={setFlowInstance}
          onNodeClick={(_, node) => setSelectedKey(node.data.kind === 'metric' ? node.data.parentKey : node.id)}
          onEdgeClick={(_, edge) => {
            if (!edge.id.startsWith('RELATION-')) return;
            setSelectedRelationId(Number(edge.id.slice('RELATION-'.length)));
          }}
          onPaneClick={() => setSelectedKey(undefined)}
          fitView
          fitViewOptions={{ padding: 0.2, maxZoom: 1.1 }}
          minZoom={0.25}
          maxZoom={2.2}
          nodesDraggable
          panOnDrag
          zoomOnScroll
          zoomOnPinch
          zoomOnDoubleClick
          selectionOnDrag={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background variant={BackgroundVariant.Dots} gap={14} size={1} color="#d9dee8" />
          <MiniMap
            zoomable
            pannable
            position="bottom-left"
            nodeColor={(node) => node.type === 'metric' ? metricVisual.stroke : node.data.kind === 'schema' && node.data.objectType === 'EVENT' ? eventVisual.stroke : entityVisual.stroke}
            maskColor="rgba(248, 250, 252, 0.72)"
          />
          <Controls position="bottom-right" showInteractive />
          <Panel position="top-left">
            <Space size={10}>
              <SemanticObjectTag kind="EVENT" label={`事件 ${events.length}`} />
              <SemanticObjectTag kind="ENTITY" label={`实体 ${entities.length}`} />
              <SemanticObjectTag kind="RELATION" label={`关系 ${semanticRelations.length}`} />
              <SemanticObjectTag kind="METRIC" label={`指标 ${metrics.length}`} />
            </Space>
          </Panel>
          {selectedSchemaNode && selectedObject && (
            <Panel position="top-right">
              <div
                style={{
                  width: 360,
                  padding: '18px 20px',
                  border: `1px solid ${schemaTheme[selectedSchemaNode.data.objectType].stroke}`,
                  borderRadius: 14,
                  background: schemaTheme[selectedSchemaNode.data.objectType].fill,
                  boxShadow: '0 14px 32px rgba(68, 84, 106, 0.18)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
                  <div>
                    <Title level={4} style={{ margin: 0 }}>{selectedSchemaNode.data.name}</Title>
                    <Text type="secondary">{selectedSchemaNode.data.code}</Text>
                  </div>
                  <Space>
                    <SemanticObjectTag kind={selectedSchemaNode.data.objectType} />
                    <PushpinOutlined style={{ color: '#ad8b36' }} />
                  </Space>
                </div>
                <Space size={8} wrap style={{ marginTop: 14 }}>
                  <Tag>{selectedSchemaNode.data.attributeCount} 个属性</Tag>
                  <Tag>{selectedSchemaNode.data.metricCount} 个指标</Tag>
                  <Tag>{selectedSchemaNode.data.relationCount} 个关系</Tag>
                </Space>
                <div style={{ marginTop: 14 }}>
                  <Text strong>描述</Text>
                  <div style={{ marginTop: 6, color: '#3f4652', lineHeight: 1.7 }}>
                    {selectedObject.description || '暂未维护该本体的业务描述。'}
                  </div>
                </div>
                {expandedMetrics.length > 0 && (
                  <Descriptions size="small" column={1} style={{ marginTop: 12 }}>
                    <Descriptions.Item label="关联指标">
                      <Space size={[4, 4]} wrap>
                        {expandedMetrics.map((metric) => <SemanticObjectTag key={metric.id} kind="METRIC" label={metric.metricName} />)}
                      </Space>
                    </Descriptions.Item>
                  </Descriptions>
                )}
              </div>
            </Panel>
          )}
        </ReactFlow>
      </div>
      <Drawer title={selectedRelation?.relationName ?? '关系详情'} open={Boolean(selectedRelation)} onClose={() => setSelectedRelationId(undefined)} width="min(520px, 100vw)">
        {selectedRelation && <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="关系类型"><SemanticObjectTag kind="RELATION" label={selectedRelation.relationType === 'EVENT_ENTITY' ? '事件与实体' : selectedRelation.relationType === 'ENTITY_ENTITY' ? '实体与实体' : '事件与事件'} /></Descriptions.Item>
          <Descriptions.Item label="来源对象">{relationObjectName(selectedRelation.sourceType, selectedRelation.sourceId)}</Descriptions.Item>
          <Descriptions.Item label="来源字段">{relationAttributeName(selectedRelation.sourceType, selectedRelation.sourceId, selectedRelation.sourceAttributeId)}</Descriptions.Item>
          <Descriptions.Item label="目标对象">{relationObjectName(selectedRelation.targetType, selectedRelation.targetId)}</Descriptions.Item>
          <Descriptions.Item label="目标字段">{relationAttributeName(selectedRelation.targetType, selectedRelation.targetId, selectedRelation.targetAttributeId)}</Descriptions.Item>
          <Descriptions.Item label="关联数量">{({ ONE_TO_ONE: '一对一', ONE_TO_MANY: '一对多', MANY_TO_ONE: '多对一', MANY_TO_MANY: '多对多' } as const)[selectedRelation.cardinality]}</Descriptions.Item>
        </Descriptions>}
      </Drawer>
    </div>
  );
}
