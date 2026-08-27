import { createElement, type ReactElement } from 'react';
import { Tag } from 'antd';

export type SemanticVisualKind = 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC';
export type SemanticFieldRole = 'PLAIN' | 'DIMENSION' | 'MEASURE';

export type SemanticObjectVisual = {
  label: string;
  className: string;
  fill: string;
  stroke: string;
};

export const semanticVisualKinds: SemanticVisualKind[] = ['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC'];

const visuals: Record<SemanticVisualKind, SemanticObjectVisual> = {
  ENTITY: { label: '实体', className: 'semantic-object-tag semantic-object-entity', fill: '#e6f4ff', stroke: '#1677ff' },
  EVENT: { label: '事件', className: 'semantic-object-tag semantic-object-event', fill: '#f9f0ff', stroke: '#722ed1' },
  FIELD: { label: '字段', className: 'semantic-object-tag semantic-object-field', fill: '#f1f5f9', stroke: '#64748b' },
  RELATION: { label: '关系', className: 'semantic-object-tag semantic-object-relation', fill: '#fff0f6', stroke: '#c41d7f' },
  DIMENSION: { label: '维度', className: 'semantic-object-tag semantic-object-dimension', fill: '#e6fffb', stroke: '#08979c' },
  METRIC: { label: '指标', className: 'semantic-object-tag semantic-object-metric', fill: '#fff7e6', stroke: '#d46b08' },
};

export function semanticObjectVisual(kind: SemanticVisualKind): SemanticObjectVisual {
  return visuals[kind];
}

export function SemanticObjectTag({ kind, label, role }: {
  kind: SemanticVisualKind;
  label?: string;
  role?: SemanticFieldRole;
}): ReactElement {
  const visualKind = kind === 'FIELD' && role === 'DIMENSION'
    ? 'DIMENSION'
    : kind === 'FIELD' && role === 'MEASURE'
      ? 'METRIC'
      : kind;
  const visual = semanticObjectVisual(visualKind);
  const roleLabel = kind === 'FIELD' && role
    ? ({ PLAIN: '普通字段', DIMENSION: '维度字段', MEASURE: '度量字段' } as const)[role]
    : undefined;
  return createElement(Tag, {
    className: visual.className,
    style: { color: visual.stroke, background: visual.fill, borderColor: visual.stroke },
  }, label ?? roleLabel ?? visual.label);
}
