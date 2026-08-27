import assert from 'node:assert/strict';
import test from 'node:test';
import { toggleFindingExpansion } from './finding-expansion.ts';

test('同一条发现再次触发会收起，切换发现只保留一个原位资料展开区', () => {
  assert.equal(toggleFindingExpansion(undefined, 'debt'), 'debt');
  assert.equal(toggleFindingExpansion('debt', 'debt'), undefined);
  assert.equal(toggleFindingExpansion('debt', 'status'), 'status');
});
