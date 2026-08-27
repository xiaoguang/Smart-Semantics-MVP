import assert from 'node:assert/strict';
import test from 'node:test';
import {
  presentRelationCardinality,
  presentRelationDirection,
  presentRelationJoin,
  relationCardinalityOptions,
  relationDirectionOptions,
  relationJoinOptions,
} from './relation-presentation.ts';

test('关系编辑把内部枚举转换为可理解的业务文案', () => {
  assert.equal(presentRelationCardinality('ONE_TO_ONE'), '一对一');
  assert.equal(presentRelationCardinality('MANY_TO_ONE'), '多对一');
  assert.equal(presentRelationDirection('BIDIRECTIONAL'), '双向');
  assert.equal(presentRelationJoin('INNER'), '仅保留匹配记录');
  assert.equal(presentRelationJoin('LEFT'), '保留左侧记录');
});

test('关系编辑选项保留精确枚举值与中文标签', () => {
  assert.deepEqual(relationCardinalityOptions(), [
    { value: 'ONE_TO_ONE', label: '一对一' },
    { value: 'ONE_TO_MANY', label: '一对多' },
    { value: 'MANY_TO_ONE', label: '多对一' },
    { value: 'MANY_TO_MANY', label: '多对多' },
  ]);
  assert.deepEqual(relationDirectionOptions(), [
    { value: 'FORWARD', label: '正向' },
    { value: 'REVERSE', label: '反向' },
    { value: 'BIDIRECTIONAL', label: '双向' },
  ]);
  assert.deepEqual(relationJoinOptions(), [
    { value: 'INNER', label: '仅保留匹配记录' },
    { value: 'LEFT', label: '保留左侧记录' },
  ]);
});
