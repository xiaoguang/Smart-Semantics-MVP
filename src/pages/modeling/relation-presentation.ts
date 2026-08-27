import type { RelationCardinality, RelationDirection } from '../../types';

const cardinalityLabels: Record<RelationCardinality, string> = {
  ONE_TO_ONE: '一对一',
  ONE_TO_MANY: '一对多',
  MANY_TO_ONE: '多对一',
  MANY_TO_MANY: '多对多',
};

const directionLabels: Record<RelationDirection, string> = {
  FORWARD: '正向',
  REVERSE: '反向',
  BIDIRECTIONAL: '双向',
};

const joinLabels: Record<'INNER' | 'LEFT', string> = {
  INNER: '仅保留匹配记录',
  LEFT: '保留左侧记录',
};

export function presentRelationCardinality(value: RelationCardinality) {
  return cardinalityLabels[value];
}

export function presentRelationDirection(value: RelationDirection) {
  return directionLabels[value];
}

export function presentRelationJoin(value: 'INNER' | 'LEFT') {
  return joinLabels[value];
}

export function relationCardinalityOptions(): Array<{ value: RelationCardinality; label: string }> {
  return (Object.keys(cardinalityLabels) as RelationCardinality[]).map((value) => ({
    value,
    label: presentRelationCardinality(value),
  }));
}

export function relationDirectionOptions(): Array<{ value: RelationDirection; label: string }> {
  return (Object.keys(directionLabels) as RelationDirection[]).map((value) => ({
    value,
    label: presentRelationDirection(value),
  }));
}

export function relationJoinOptions(): Array<{ value: 'INNER' | 'LEFT'; label: string }> {
  return (Object.keys(joinLabels) as Array<'INNER' | 'LEFT'>).map((value) => ({
    value,
    label: presentRelationJoin(value),
  }));
}
