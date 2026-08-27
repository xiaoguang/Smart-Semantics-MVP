import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

test('规则和目标值页面不把内部模式枚举写入业务校验提示', () => {
  const rulePage = readFileSync(new URL('./BusinessRulePage.tsx', import.meta.url), 'utf8');
  const targetTab = readFileSync(new URL('./TargetTab.tsx', import.meta.url), 'utf8');
  const metricTab = readFileSync(new URL('./MetricDefTab.tsx', import.meta.url), 'utf8');

  assert.match(rulePage, /筛选规则至少需要一条条件/);
  assert.match(rulePage, /组合规则至少选择一个子规则/);
  assert.match(targetTab, /比例目标建议在 0 到 1 之间/);
  assert.doesNotMatch(rulePage, /FILTER 至少需要一条条件|COMPOSITE 至少选择一个子规则|COMPOSITE 只能组合 FILTER/);
  assert.doesNotMatch(targetTab, /RATIO 建议在 \[0,1\] 范围/);
  assert.doesNotMatch(metricTab, /STATUS_LABEL\[status\] \?\? status/);
  assert.doesNotMatch(rulePage, /ruleStatusLabel\[status\] \?\? status/);
});
