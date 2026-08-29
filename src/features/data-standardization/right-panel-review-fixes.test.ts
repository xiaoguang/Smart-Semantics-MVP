import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  projectBusinessJourneyTimeline,
  type JourneySource,
  type JourneyTimelineItem,
} from './standardization-experience-projection.ts';

const sourceRows: JourneySource[] = [
  { sourceId: 'mysql', displayName: '数据库', status: 'ALIGNED' },
  { sourceId: 'github', displayName: 'GitHub代码仓库', status: 'PENDING' },
];

const workbenchSource = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
const inspectorSource = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
const inspectorStyles = readFileSync(new URL('./data-standardization.css', import.meta.url), 'utf8');
const workflowSource = readFileSync(new URL('./standardization-timeline.tsx', import.meta.url), 'utf8');

function sourceSelectionHandler() {
  const match = workbenchSource.match(/const selectSourceFromInspector = \(sourceId: string\) => \{([\s\S]*?)\n  \};\n\n  const assistantSelection/);
  assert.ok(match, 'expected the source-list selection handler to remain a named workbench seam');
  return match[1];
}

test('keeps the first pending source current after earlier sources have aligned', () => {
  const journey = projectBusinessJourneyTimeline({ sources: sourceRows, timeline: [] });

  assert.equal(
    journey.find((checkpoint) => checkpoint.state === 'CURRENT')?.checkpointId,
    'source:github',
  );
});

test('makes the frozen result current after every source is aligned', () => {
  const timeline: JourneyTimelineItem[] = [{
    itemId: 'frozen',
    eventId: 'frozen',
    kind: 'DELIVERABLE_FROZEN',
    state: 'RECEIPT',
    title: '标准化结果已定版',
    summary: '已定版',
  }];
  const journey = projectBusinessJourneyTimeline({
    sources: sourceRows.map((source) => ({ ...source, status: 'ALIGNED' as const })),
    timeline,
  });

  assert.equal(
    journey.find((checkpoint) => checkpoint.state === 'CURRENT')?.checkpointId,
    'result:standardization',
  );
});

test('selecting a source only locates its workflow and keeps the current inline evidence selection intact', () => {
  const handler = sourceSelectionHandler();

  assert.match(handler, /setSelectedInspectorSourceId\(sourceId\)/);
  assert.doesNotMatch(handler, /setSelectedTimelineInspector\(undefined\)/);
  assert.doesNotMatch(handler, /setSelectedBlockId\(undefined\)/);
  assert.doesNotMatch(handler, /setSelectedCuratedEvidenceRef\(undefined\)/);
});

test('right rail has one source-progress area above an independent evidence-support area', () => {
  assert.doesNotMatch(inspectorSource, /guanyijia-source-list/u);
  assert.match(inspectorSource, /className="guanyijia-source-progress-panel"/u);
  assert.match(inspectorSource, /className="guanyijia-evidence-support-panel"/u);
  assert.match(inspectorStyles, /\.guanyijia-source-progress-panel\s*\{[\s\S]*?max-height:/u);
  assert.match(inspectorStyles, /\.guanyijia-evidence-support-panel\s*\{[\s\S]*?overflow-y:\s*auto/u);
  assert.match(inspectorStyles, /\.guanyijia-document-review-header\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) auto/u);
});

test('a smooth programmatic workflow scroll keeps its guard beyond the first animation frame', () => {
  const programmaticScrollEffect = workflowSource.match(/programmaticScrollRef\.current = true;([\s\S]*?)setFollowState\(\(previous\) => \(\{ \.\.\.previous, scrollToCheckpointItemId: undefined \}\)\);/);
  assert.ok(programmaticScrollEffect, 'expected a programmatic workflow scroll effect');

  assert.doesNotMatch(
    programmaticScrollEffect[1],
    /window\.requestAnimationFrame\(\(\) => \{\s*programmaticScrollRef\.current = false;/,
  );
});

test('来源过程使用单一扁平 disclosure，而不是分离的展开阶段控件或竖向时间线', () => {
  assert.match(
    workflowSource,
    /className=\{`guanyijia-source-process-disclosure[^`]*`\}[\s\S]*?aria-expanded=\{sourceStagesExpanded\}/u,
    'source title row must be the only disclosure control',
  );
  assert.match(
    workflowSource,
    /projectVisibleBusinessJourneyStages\(item\.stages\)/u,
    'the component must not render future stages before they are reached',
  );
  assert.doesNotMatch(workflowSource, /展开阶段|收起阶段|guanyijia-timeline-stage-toggle/u);
  assert.doesNotMatch(inspectorStyles, /\.guanyijia-timeline-item::before/u);
  assert.match(inspectorStyles, /\.guanyijia-source-process-disclosure\s*\{/u);
});
