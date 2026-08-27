import assert from 'node:assert/strict';
import test from 'node:test';
import {
  applyReviewSurfaceEffects,
  createReviewSurfaceNavigator,
  initialReviewSurfaceState,
  projectWorkflowPrimary,
  restoreReviewSurfaceState,
  type ReviewSurfaceState,
} from './index.ts';

const navigator = createReviewSurfaceNavigator();

function transition(
  state: ReviewSurfaceState,
  event: Parameters<typeof navigator.transition>[0]['event'],
  viewport: Parameters<typeof navigator.transition>[0]['viewport'] = 'MOBILE_FULLSCREEN',
) {
  return navigator.transition({ state, event, viewport });
}

test('Document→Inspector→Patch 嵌套关闭按稳定 anchor、相对偏移和 focus 逆序恢复', () => {
  let state = initialReviewSurfaceState({
    runId: 'run-1', runRevision: 7, cursorEpoch: 'epoch-1',
    anchor: { itemId: 'timeline:17', relativeTop: 1.5, focusId: 'timeline-button:17' },
    assistantDraft: { value: '保留草稿', selectionStart: 2, selectionEnd: 4 },
  });
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'DOCUMENT', stableId: 'document:r2',
    returnAnchor: state.anchor,
    focusId: 'document:block:stock',
  } }));
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'INSPECTOR', stableId: 'inspector:block:stock',
    returnAnchor: { itemId: 'document:block:stock', relativeTop: 0, focusId: 'document:block:stock' },
    focusId: 'inspector:close',
  } }, 'DESKTOP_OVERLAY'));
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'ASSISTANT_PATCH', stableId: 'proposal:p1',
    returnAnchor: { itemId: 'inspector:block:stock', relativeTop: 0, focusId: 'inspector:close' },
    focusId: 'assistant:confirm:p1',
  } }));

  const patchClose = transition(state, { type: 'CLOSE' });
  assert.equal(patchClose.state.layers.at(-1)?.kind, 'INSPECTOR');
  assert.deepEqual(patchClose.effects, [
    { type: 'SET_MODAL_STATE', bodyLocked: true, backgroundInert: true, trapFocus: true },
    { type: 'RESTORE_SCROLL', anchor: { itemId: 'inspector:block:stock', relativeTop: 0, focusId: 'inspector:close' } },
    { type: 'RESTORE_FOCUS', focusId: 'inspector:close' },
    { type: 'ANNOUNCE', message: '已返回来源资料' },
  ]);
  const inspectorClose = transition(patchClose.state, { type: 'ESCAPE' }, 'DESKTOP_OVERLAY');
  assert.equal(inspectorClose.state.layers.at(-1)?.kind, 'DOCUMENT');
  assert.deepEqual(inspectorClose.effects.map(({ type }) => type), [
    'SET_MODAL_STATE', 'RESTORE_SCROLL', 'RESTORE_FOCUS', 'ANNOUNCE',
  ], 'background must become interactive before focus is restored into it');
  assert.deepEqual(inspectorClose.state.assistantDraft, { value: '保留草稿', selectionStart: 2, selectionEnd: 4 });
});

test('viewport change 只投影声明式 dialog/body-lock/focus effects，desktop inline Inspector 不锁焦点', () => {
  let state = initialReviewSurfaceState({ runId: 'run-1', runRevision: 1, cursorEpoch: 'e1' });
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'INSPECTOR', stableId: 'facts:1', returnAnchor: { itemId: 'timeline:1', relativeTop: 0 }, focusId: 'facts:close',
  } }, 'DESKTOP_INLINE'));
  const inline = transition(state, { type: 'VIEWPORT_CHANGED' }, 'DESKTOP_INLINE');
  assert.deepEqual(inline.effects, [{ type: 'SET_MODAL_STATE', bodyLocked: false, backgroundInert: false, trapFocus: false }]);
  const overlay = transition(state, { type: 'VIEWPORT_CHANGED' }, 'DESKTOP_OVERLAY');
  assert.deepEqual(overlay.effects, [
    { type: 'SET_MODAL_STATE', bodyLocked: true, backgroundInert: true, trapFocus: true },
    { type: 'FOCUS', focusId: 'facts:close' },
  ]);
});

test('run revision 与 cursor 过期通过同一 seam 清除陈旧 preview/window 并保留草稿', () => {
  let state = initialReviewSurfaceState({
    runId: 'run-1', runRevision: 3, cursorEpoch: 'e1',
    anchor: { itemId: 'timeline:30', cursor: 'opaque-old', relativeTop: 2 },
    assistantDraft: { value: '尚未发送', selectionStart: 4, selectionEnd: 4 },
  });
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'CONFLICT', stableId: 'conflict:c1', revision: 3,
    returnAnchor: state.anchor, focusId: 'conflict:strategy:keep',
  } }));
  const revised = transition(state, {
    type: 'RUN_REVISION_CHANGED', runRevision: 4, validStableIds: ['timeline:31'],
    fallbackAnchor: { itemId: 'timeline:31', relativeTop: 0, focusId: 'timeline-button:31' },
  });
  assert.equal(revised.state.layers.length, 0);
  assert.equal(revised.state.runRevision, 4);
  assert.deepEqual(revised.state.assistantDraft, state.assistantDraft);
  assert.ok(revised.effects.some((effect) => effect.type === 'ANNOUNCE' && effect.message === '审阅内容已更新'));

  const expired = transition(revised.state, {
    type: 'CURSOR_EXPIRED', cursorEpoch: 'e2',
    fallbackAnchor: { itemId: 'timeline:31', relativeTop: 1, focusId: 'timeline-button:31' },
  });
  assert.equal(expired.state.anchor.cursor, undefined);
  assert.equal(expired.state.cursorEpoch, 'e2');
  assert.ok(expired.effects.some((effect) => effect.type === 'ANNOUNCE' && effect.message === '列表已更新'));
});

test('结构化文档产生新 revision 时只迁移受信 Document layer 并保持返回锚点', () => {
  let state = initialReviewSurfaceState({
    runId: 'run-1', runRevision: 3, cursorEpoch: 'e1',
    anchor: { itemId: 'timeline:document-r1', relativeTop: 1, focusId: 'review-r1' },
  });
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'DOCUMENT', stableId: 'document:source-document-r1', revision: 3,
    returnAnchor: state.anchor, focusId: 'document-r1:heading',
  } }, 'DESKTOP_INLINE'));

  const revised = transition(state, {
    type: 'RUN_REVISION_CHANGED', runRevision: 4,
    validStableIds: ['document:source-document-r2', 'timeline:document-r2'],
    layerMigrations: [{
      fromStableId: 'document:source-document-r1',
      toStableId: 'document:source-document-r2',
      focusId: 'document-r2:heading',
    }],
    fallbackAnchor: { itemId: 'timeline:document-r2', relativeTop: 0, focusId: 'review-r2' },
  });

  assert.deepEqual(revised.state.layers, [{
    kind: 'DOCUMENT', stableId: 'document:source-document-r2', revision: 4,
    returnAnchor: { itemId: 'timeline:document-r1', relativeTop: 1, focusId: 'review-r1' },
    focusId: 'document-r2:heading',
  }]);
  assert.ok(revised.effects.some((effect) => effect.type === 'FOCUS'
    && effect.focusId === 'document-r2:heading'));
});

test('Document 迁移时会清除其上方的陈旧 Patch，并将焦点交给仍存活的 Document', () => {
  let state = initialReviewSurfaceState({
    runId: 'run-1', runRevision: 3, cursorEpoch: 'e1',
    anchor: { itemId: 'timeline:document-r1', relativeTop: 0, focusId: 'review-r1' },
  });
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'DOCUMENT', stableId: 'document:r1', revision: 3,
    returnAnchor: state.anchor, focusId: 'document-r1:heading',
  } }));
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'ASSISTANT_PATCH', stableId: 'proposal:p1', revision: 3,
    returnAnchor: { itemId: 'document:r1', relativeTop: 0, focusId: 'document-r1:heading' },
    focusId: 'proposal:p1:confirm',
  } }));

  const revised = transition(state, {
    type: 'RUN_REVISION_CHANGED', runRevision: 4,
    validStableIds: ['document:r2'],
    layerMigrations: [{ fromStableId: 'document:r1', toStableId: 'document:r2', focusId: 'document-r2:heading' }],
    fallbackAnchor: { itemId: 'timeline:document-r2', relativeTop: 0, focusId: 'review-r2' },
  });

  assert.deepEqual(revised.state.layers.map(({ kind, stableId }) => ({ kind, stableId })), [
    { kind: 'DOCUMENT', stableId: 'document:r2' },
  ]);
  assert.ok(revised.effects.some((effect) => effect.type === 'FOCUS'
    && effect.focusId === 'document-r2:heading'));
});

test('workflow primary 投影拒绝同一可见主区出现多个业务主动作', () => {
  assert.equal(projectWorkflowPrimary(['resolve-current-conflict']), 'resolve-current-conflict');
  assert.equal(projectWorkflowPrimary([]), undefined);
  assert.throws(
    () => projectWorkflowPrimary(['resolve-current-conflict', 'confirm-assistant-patch']),
    /主区只能有一个工作流主操作/,
  );
});

test('React adapter 以稳定ID恢复≤2px相对位置，并集中执行inert/body-lock/live-region', () => {
  const calls: string[] = [];
  const positions = new Map([['timeline:17', 132]]);
  let scrollTop = 200;
  applyReviewSurfaceEffects([
    { type: 'RESTORE_SCROLL', anchor: { itemId: 'timeline:17', relativeTop: 2, focusId: 'timeline-button:17' } },
    { type: 'RESTORE_FOCUS', focusId: 'timeline-button:17' },
    { type: 'RESTORE_TEXT_SELECTION', focusId: 'guanyijia-assistant-composer', start: 2, end: 4 },
    { type: 'ANNOUNCE', message: '已返回运行时间线' },
    { type: 'SET_MODAL_STATE', bodyLocked: true, backgroundInert: true, trapFocus: true },
  ], {
    itemTop: (itemId) => positions.get(itemId) ?? null,
    viewportTop: () => 100,
    readScrollTop: () => scrollTop,
    writeScrollTop: (value) => { scrollTop = value; calls.push(`scroll:${value}`); },
    focus: (focusId) => calls.push(`focus:${focusId}`),
    restoreTextSelection: (focusId, start, end) => calls.push(`selection:${focusId}:${start}:${end}`),
    announce: (message) => calls.push(`announce:${message}`),
    setBodyLocked: (value) => calls.push(`body:${value}`),
    setBackgroundInert: (value) => calls.push(`inert:${value}`),
    setFocusTrap: (value) => calls.push(`trap:${value}`),
  });
  assert.equal(scrollTop, 230);
  assert.deepEqual(calls, [
    'scroll:230', 'focus:timeline-button:17', 'selection:guanyijia-assistant-composer:2:4',
    'announce:已返回运行时间线',
    'body:true', 'inert:true', 'trap:true',
  ]);
});

test('关闭目标层后恢复助手 composer 焦点和选区', () => {
  let state = initialReviewSurfaceState({
    runId: 'run-1', runRevision: 7, cursorEpoch: 'e1',
    anchor: {
      itemId: 'assistant-composer', relativeTop: 0,
      focusId: 'guanyijia-assistant-composer',
      textSelection: { start: 2, end: 6 },
    },
    assistantDraft: { value: '请打开当前来源文档', selectionStart: 2, selectionEnd: 6 },
  });
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'DOCUMENT', stableId: 'document:r1', revision: 7,
    returnAnchor: state.anchor, focusId: 'document-r1:heading',
  } }));
  const closed = transition(state, { type: 'CLOSE' });
  assert.deepEqual(closed.effects.filter((effect) => (
    effect.type === 'RESTORE_FOCUS' || effect.type === 'RESTORE_TEXT_SELECTION'
  )), [
    { type: 'RESTORE_FOCUS', focusId: 'guanyijia-assistant-composer' },
    { type: 'RESTORE_TEXT_SELECTION', focusId: 'guanyijia-assistant-composer', start: 2, end: 6 },
  ]);
});

test('同一来源文档已打开时，重新定位目标会更新其返回助手锚点而不嵌套重复 layer', () => {
  let state = initialReviewSurfaceState({
    runId: 'run-1', runRevision: 7, cursorEpoch: 'e1',
    anchor: { itemId: 'task:REVIEW_DOCUMENT', relativeTop: 1, focusId: 'document:open' },
  });
  ({ state } = transition(state, { type: 'OPEN', layer: {
    kind: 'DOCUMENT', stableId: 'document:r1', revision: 7,
    returnAnchor: state.anchor, focusId: 'document:r1:heading',
  } }));
  const relocated = transition(state, { type: 'UPDATE_TOP_LAYER', layer: {
    kind: 'DOCUMENT', stableId: 'document:r1', revision: 7,
    returnAnchor: {
      itemId: 'assistant:panel', relativeTop: 12,
      focusId: 'guanyijia-assistant-composer', textSelection: { start: 2, end: 4 },
    },
    focusId: 'document:r1:block:stock',
  } });
  assert.equal(relocated.state.layers.length, 1);
  assert.deepEqual(relocated.state.layers[0], {
    kind: 'DOCUMENT', stableId: 'document:r1', revision: 7,
    returnAnchor: {
      itemId: 'assistant:panel', relativeTop: 12,
      focusId: 'guanyijia-assistant-composer', textSelection: { start: 2, end: 4 },
    },
    focusId: 'document:r1:block:stock',
  });
  assert.deepEqual(relocated.effects, [
    { type: 'SET_MODAL_STATE', bodyLocked: true, backgroundInert: true, trapFocus: true },
    { type: 'FOCUS', focusId: 'document:r1:block:stock' },
  ]);
});

test('刷新只恢复稳定surface状态，拒绝正文/DOM形态与revision倒退', () => {
  const valid = JSON.stringify({
    runId: 'run-1', runRevision: 4, cursorEpoch: 'e1',
    anchor: { itemId: 'timeline:4', relativeTop: 1, focusId: 'timeline-button:4' },
    assistantDraft: { value: '保留问题', selectionStart: 2, selectionEnd: 4 },
    layers: [{
      kind: 'DOCUMENT', stableId: 'document:r2', revision: 4,
      returnAnchor: { itemId: 'timeline:4', relativeTop: 1, focusId: 'timeline-button:4' },
      focusId: 'document:r2:heading',
    }],
  });
  const restored = restoreReviewSurfaceState(valid, {
    runId: 'run-1', runRevision: 4, cursorEpoch: 'e1',
  });
  assert.equal(restored.layers.at(-1)?.stableId, 'document:r2');
  assert.equal(restored.assistantDraft.value, '保留问题');
  assert.doesNotMatch(JSON.stringify(restored), /HTMLElement|innerHTML|正文内容/u);

  const injected = valid.replace('"layers":[', '"businessBody":"伪造正文","layers":[');
  assert.equal(restoreReviewSurfaceState(injected, {
    runId: 'run-1', runRevision: 4, cursorEpoch: 'e1',
  }).layers.length, 0);
  assert.equal(restoreReviewSurfaceState(valid, {
    runId: 'run-1', runRevision: 3, cursorEpoch: 'e1',
  }).layers.length, 0);
});
