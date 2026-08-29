import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  projectBusinessJourneyTimeline,
  type JourneySource,
  type JourneyTimelineItem,
} from './standardization-experience-projection.ts';

const timelineSource = readFileSync(new URL('./standardization-timeline.tsx', import.meta.url), 'utf8');
const workbenchSource = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
const inspectorStyles = readFileSync(new URL('./data-standardization.css', import.meta.url), 'utf8');

test('未来的待读取和待生成步骤不能复用绿色完成状态', () => {
  assert.doesNotMatch(
    timelineSource,
    /item\.state === 'RECEIPT'\s*\?\s*<Tag icon=\{<CheckCircleOutlined \/>\}\s*color="green">完成<\/Tag>/u,
    '业务时间线不能仅凭 RECEIPT 把待读取或待生成步骤显示为完成',
  );
  assert.match(
    timelineSource,
    /item\.summary|summary.*待读取|summary.*待生成/u,
    '时间线状态应来自业务检查点摘要，而不是统一完成标签',
  );
});

test('未决差异挂在当前来源的审阅阶段，来源本身仍是唯一当前步骤', () => {
  const sources: JourneySource[] = [{
    sourceId: 'guanyijia_mysql',
    displayName: '数据库',
    status: 'DOCUMENT_READY',
    introducedConflictIds: ['debt'],
    resolvedConflictIds: [],
  }];
  const timeline: JourneyTimelineItem[] = [{
    itemId: 'finding-event',
    eventId: 'finding-event',
    kind: 'CONFLICT_FOUND',
    state: 'CURRENT',
    sourceId: 'guanyijia_mysql',
    title: '欠款字段',
    summary: '待决定',
    conflicts: [{ conflictId: 'debt', title: '欠款字段', affectedObjects: [] }],
  }];

  const current = projectBusinessJourneyTimeline({ sources, timeline }).filter((item) => item.state === 'CURRENT');

  assert.equal(current.length, 1, '业务时间线只能有一个 aria-current 对应的当前步骤');
  assert.equal(current[0]?.businessKind, 'SOURCE');
  assert.equal(current[0]?.checkpointId, 'source:guanyijia_mysql');
  assert.deepEqual(current[0]?.stages?.find((stage) => stage.stage === 'REVIEW')?.conflicts, [{
    conflictId: 'debt', title: '欠款字段', state: 'ACTIVE', affectedObjects: [],
  }]);
});

test('所有打开的来源资料 Inspector 都保留 flex 布局以承载可滚动流程', () => {
  const openInspectorRules = [...inspectorStyles.matchAll(
    /[^{}]*inspector-open\s+\.guanyijia-facts-inspector\s*\{([^}]*)\}/gu,
  )];

  assert.ok(openInspectorRules.length >= 3, '应覆盖内联、抽屉和全屏 Inspector 打开态');
  for (const rule of openInspectorRules) {
    assert.match(rule[1]!, /display:\s*flex\b/u, `打开态必须保留 display:flex：${rule[0]}`);
    assert.doesNotMatch(rule[1]!, /display:\s*block\b/u, `打开态不能降级为 display:block：${rule[0]}`);
  }
});

test('移动端全屏文档层必须把底部对话输入纳入同一可访问层', () => {
  const mainMatch = workbenchSource.match(
    /<main\b(?=[^>]*\bclassName="guanyijia-workbench-thread")(?=[^>]*\brole=\{mobile && documentVisible \? 'dialog' : undefined\})[^>]*>([\s\S]*?)<\/main>/u,
  );
  assert.ok(mainMatch, '应存在标准化工作区主审阅层');

  const accessibleDialogRoots = [...workbenchSource.matchAll(
    /<main\b(?=[^>]*\brole=\{mobile && documentVisible \? 'dialog' : undefined\})[^>]*>([\s\S]*?)<\/main>/gu,
  )];
  assert.equal(accessibleDialogRoots.length, 1, '移动端文档只能有一个包含审阅助手的可访问对话框根');

  const mainBody = mainMatch[1]!;
  const scrollStart = mainBody.indexOf('className="guanyijia-workbench-scroll"');
  const assistantIndex = mainBody.indexOf('{reviewAssistant}');

  assert.ok(scrollStart >= 0, '主审阅滚动容器必须存在');
  assert.ok(assistantIndex >= 0, '审阅助手必须存在');
  assert.ok(assistantIndex > scrollStart, '审阅助手必须是工作区主层的第二行');
  assert.match(
    inspectorStyles,
    /\.guanyijia-workbench-shell\.mobile-surface\.document-open\s+\.guanyijia-workbench-thread\s*\{[^}]*position:\s*fixed/su,
    '移动端打开文档时必须提升包含滚动正文和底部对话的整个工作区主层',
  );
  assert.match(
    inspectorStyles,
    /\.guanyijia-workbench-shell\.mobile-surface\.document-open\s+\.guanyijia-document-review\s*\{[^}]*position:\s*static/su,
    '文档应在该主层内参与布局，而不是单独 fixed 到对话输入上方',
  );
});
