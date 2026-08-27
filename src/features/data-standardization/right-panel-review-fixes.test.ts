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

test('a selected pending source row has the same selected visual treatment as every other source', () => {
  assert.match(
    inspectorSource,
    /onClick=\{\(\) => onSelectSource\?\.\(source\.sourceId\)\}[\s\S]*?source\.sourceId === \(selectedSourceId \?\? model\?\.sourceId\) \? 'selected' : ''/,
  );
  assert.match(inspectorStyles, /\.guanyijia-source-list-row\.selected\s*\{\s*background:/);
  assert.match(inspectorStyles, /\.guanyijia-source-list-row\.selected \.guanyijia-source-status\s*\{\s*background:/);
});

test('a smooth programmatic workflow scroll keeps its guard beyond the first animation frame', () => {
  const programmaticScrollEffect = workflowSource.match(/programmaticScrollRef\.current = true;([\s\S]*?)setFollowState\(\(previous\) => \(\{ \.\.\.previous, scrollToCheckpointItemId: undefined \}\)\);/);
  assert.ok(programmaticScrollEffect, 'expected a programmatic workflow scroll effect');

  assert.doesNotMatch(
    programmaticScrollEffect[1],
    /window\.requestAnimationFrame\(\(\) => \{\s*programmaticScrollRef\.current = false;/,
  );
});
