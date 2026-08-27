import { useState } from 'react';
import { Button, Select, Input, Tag, Space } from 'antd';
import { MinusCircleOutlined, PlusOutlined, CloseOutlined } from '@ant-design/icons';
import type { CaliberFilter, CaliberCondition, DimensionFilter, LogicOp, ObjAttribute, SemanticEntity, SemanticEvent, SemanticMetric, SemanticRelation } from './mockData';
import { OPERATORS, mockEvents, mockEntities, metricRootEventId } from './mockData';
import { SemanticObjectTag } from '../../components/semantic-object-visuals';

const LOGIC_OPTS: LogicOp[] = ['AND', 'OR'];

/* ============ 通用单对象属性条件构建器（caliber_filter / FILTER 共用） ============ */
export function AttributeConditionBuilder({
  value, onChange, attributes,
}: {
  value: CaliberFilter; onChange: (v: CaliberFilter) => void; attributes: ObjAttribute[];
}) {
  const setCond = (i: number, patch: Partial<CaliberCondition>) =>
    onChange({ ...value, conditions: value.conditions.map((c, idx) => (idx === i ? { ...c, ...patch } : c)) });
  const addCond = () =>
    onChange({ ...value, conditions: [...value.conditions, { attributeCode: attributes[0]?.attributeCode ?? '', op: '=', values: [''] }] });
  const delCond = (i: number) => onChange({ ...value, conditions: value.conditions.filter((_, idx) => idx !== i) });

  return (
    <div className="condition-builder">
      <Space className="condition-builder-logic" style={{ marginBottom: 8 }}>
        条件关系：
        <Select value={value.logic} style={{ width: 90 }} onChange={(v) => onChange({ ...value, logic: v })} options={LOGIC_OPTS.map((l) => ({ value: l, label: l }))} />
      </Space>
      {value.conditions.map((c, i) => {
        const attr = attributes.find((a) => a.attributeCode === c.attributeCode);
        return (
          <Space key={i} className="condition-builder-row" style={{ display: 'flex', marginBottom: 8 }} align="center">
            <Select
              value={c.attributeCode}
              style={{ width: 180 }}
              placeholder="选择属性"
              onChange={(v) => setCond(i, { attributeCode: v, values: [''] })}
              options={attributes.map((a) => ({ value: a.attributeCode, label: `${a.attributeName}(${a.attributeCode})` }))}
            />
            <Select value={c.op} style={{ width: 80 }} onChange={(v) => setCond(i, { op: v })} options={OPERATORS.map((o) => ({ value: o, label: o }))} />
            {c.op === 'IN' ? (
              <Input style={{ width: 220 }} placeholder="逗号分隔多值" value={c.values.join(',')}
                onChange={(e) => setCond(i, { values: e.target.value.split(',').map((x) => x.trim()).filter(Boolean) })} />
            ) : attr?.enumValues ? (
              <Select style={{ width: 180 }} value={c.values[0]} onChange={(v) => setCond(i, { values: [v] })}
                options={attr.enumValues.map((ev) => ({ value: ev, label: ev }))} />
            ) : (
              <Input style={{ width: 180 }} value={c.values[0] ?? ''} onChange={(e) => setCond(i, { values: [e.target.value] })} />
            )}
            <Button type="text" icon={<MinusCircleOutlined />} aria-label={`删除第 ${i + 1} 个条件`} onClick={() => delCond(i)} />
          </Space>
        );
      })}
      <Button type="dashed" icon={<PlusOutlined />} size="small" onClick={addCond}>添加条件</Button>
    </div>
  );
}

/* ============ 口径构建器（BASIC 指标：当前事件属性） ============ */
export function CaliberFilterBuilder({
  value, onChange, eventId, events = mockEvents,
}: {
  value: CaliberFilter; onChange: (v: CaliberFilter) => void; eventId?: number; events?: SemanticEvent[];
}) {
  const attrs = events.find((e) => e.id === eventId)?.attributes.filter((a) => a.isFilterable) ?? [];
  return <AttributeConditionBuilder value={value} onChange={onChange} attributes={attrs} />;
}

/* ============ 维度构建器（目标值 dimension_filter：从指标根事件可达实体） ============ */
export function DimensionFilterBuilder({
  value, onChange, metric, entities = mockEntities, relations = [],
}: {
  value: DimensionFilter; onChange: (v: DimensionFilter) => void; metric?: SemanticMetric;
  entities?: SemanticEntity[]; relations?: SemanticRelation[];
}) {
  const rootEventId = metric ? metricRootEventId(metric) : undefined;
  const reachableIds = rootEventId ? new Set(relations.flatMap((relation) => {
    if (relation.sourceType === 'EVENT' && relation.sourceId === rootEventId && relation.targetType === 'ENTITY') return [relation.targetId];
    if (relation.targetType === 'EVENT' && relation.targetId === rootEventId && relation.sourceType === 'ENTITY') return [relation.sourceId];
    return [];
  })) : null;
  const reach = reachableIds ? entities.filter((entity) => reachableIds.has(entity.id)) : entities;
  const setDim = (i: number, patch: Partial<{ entityCode: string; valueCode: string }>) =>
    onChange({ dimensions: value.dimensions.map((d, idx) => (idx === i ? { ...d, ...patch } : d)) });
  const addDim = () => {
    const first = reach[0];
    const ev = first?.attributes.find((a) => a.enumValues?.length)?.enumValues?.[0] ?? '';
    onChange({ dimensions: [...value.dimensions, { entityCode: first?.entityCode ?? '', valueCode: ev }] });
  };
  const delDim = (i: number) => onChange({ dimensions: value.dimensions.filter((_, idx) => idx !== i) });

  return (
    <div className="condition-builder dimension-filter-builder">
      <div style={{ marginBottom: 8, color: '#8c8c8c', fontSize: 12 }}>
        可按与指标事件相关联的实体限定目标范围；留空表示全局目标。
      </div>
      {value.dimensions.map((d, i) => {
        const ent = reach.find((e) => e.entityCode === d.entityCode);
        const enumAttr = ent?.attributes.find((a) => a.enumValues && a.enumValues.length);
        return (
          <Space key={i} className="condition-builder-row" style={{ display: 'flex', marginBottom: 8 }} align="center">
            <SemanticObjectTag kind="DIMENSION" />
            <Select
              value={d.entityCode}
              style={{ width: 150 }}
              onChange={(v) => {
                const ne = reach.find((e) => e.entityCode === v)!;
                const ev = ne.attributes.find((a) => a.enumValues?.length)?.enumValues?.[0] ?? '';
                setDim(i, { entityCode: v, valueCode: ev });
              }}
              options={reach.map((e) => ({ value: e.entityCode, label: `${e.entityName}(${e.entityCode})` }))}
            />
            <Select
              value={d.valueCode}
              style={{ width: 150 }}
              onChange={(v) => setDim(i, { valueCode: v })}
              options={enumAttr?.enumValues?.map((ev) => ({ value: ev, label: ev })) ?? []}
            />
            <Button type="text" icon={<CloseOutlined />} aria-label={`删除第 ${i + 1} 个业务维度`} onClick={() => delDim(i)} />
          </Space>
        );
      })}
      <Button type="dashed" icon={<PlusOutlined />} size="small" onClick={addDim}>添加维度</Button>
      {value.dimensions.length === 0 && <Tag style={{ marginLeft: 8 }}>全局目标</Tag>}
    </div>
  );
}

/* ============ 触发词 Tag 输入 ============ */
export function TriggerWordsInput({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  const [input, setInput] = useState('');
  const remove = (t: string) => onChange(value.filter((x) => x !== t));
  return (
    <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, minHeight: 32 }}>
      {value.map((t) => (
        <Tag key={t} closable onClose={() => remove(t)} style={{ marginBottom: 4 }}>{t}</Tag>
      ))}
      <Input
        size="small"
        style={{ width: 140 }}
        placeholder="输入后回车"
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onPressEnter={() => {
          const v = input.trim();
          if (v && !value.includes(v)) onChange([...value, v]);
          setInput('');
        }}
      />
    </div>
  );
}
