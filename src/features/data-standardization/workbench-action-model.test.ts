import assert from 'node:assert/strict';
import test from 'node:test';
import { buildWorkbenchActions, sourceSnapshotAction } from './workbench-action-model.ts';

test('each workflow state exposes at most one meaningful primary action', () => {
  const cases = [
    { workflow: { type: 'START_RUN', label: 'ignored' } as const, label: '开始资料整理' },
    { workflow: { type: 'READ_NEXT_SOURCE', label: '载入数据库快照' } as const, label: '载入数据库快照' },
    { workflow: { type: 'REVIEW_DOCUMENT', label: '继续审阅数据库文档' } as const, label: '继续审阅数据库文档' },
    { workflow: { type: 'RESOLVE_CONFLICT', label: '处理 1 项来源差异' } as const, label: '审阅来源差异' },
    { workflow: { type: 'NONE', label: '无操作' } as const, label: undefined },
  ];
  for (const current of cases) {
    const actions = buildWorkbenchActions({ workflow: current.workflow });
    assert.ok(actions.filter((action) => action.primary).length <= 1);
    assert.equal(actions[0]?.label, current.label);
  }
});

test('static workflow state has no button and document completion names the source', () => {
  assert.deepEqual(buildWorkbenchActions({ workflow: { type: 'NONE', label: '当前 Checkpoint 暂无可执行操作' } }), []);
  assert.deepEqual(buildWorkbenchActions({
    workflow: { type: 'NONE', label: '当前 Checkpoint 暂无可执行操作' },
    canCompleteCurrentDocument: true,
    sourceName: '数据库',
  }), [{ effect: 'COMMAND', id: 'COMPLETE_DOCUMENT', label: '完成数据库审阅', primary: true }]);
});

test('运行来源是一个明确的只读展开操作', () => {
  assert.deepEqual(sourceSnapshotAction(), {
    effect: 'EXPAND', id: 'OPEN_SOURCE_SNAPSHOTS', label: '运行来源', panel: 'SOURCE_SNAPSHOTS', primary: false,
  });
});
