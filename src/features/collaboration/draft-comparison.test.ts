import assert from 'node:assert/strict';
import test from 'node:test';
import { seedCollaborationState, seededRetailV2Draft } from './fixtures.ts';
import {
  paginateDraftChanges,
  projectDraftBrowser,
  projectDraftPublicationChecks,
  projectDraftReview,
  projectDraftWorkflowSummary,
} from './draft-comparison.ts';
import { modelBrowserRefKey } from '../model-browser/types.ts';
import { createCollaborationRuntime, type CollaborationStorage } from './runtime.ts';
import * as draftComparisonModule from './draft-comparison.ts';

class MemoryStorage implements CollaborationStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

test('管理员从正式 V1 新建草稿时，建模过程差异不会伪装成对象变化', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const catalog = runtime.getCatalog('group_retail_ops', 'V1');
  assert.ok(catalog);
  const materialized = structuredClone(catalog.data);
  materialized.workbenchState = { ...materialized.workbenchState as Record<string, unknown>, activeView: 'WORKSPACE' };

  const draft = runtime.openDraft({
    actorUserId: 'user_administer', modelSpaceId: 'group_retail_ops', materialized,
  });

  assert.equal(draft.changes.length, 0);
  assert.equal(projectDraftReview({ draft, baseCatalog: catalog }).changes.length, 0);
});

test('旧式不完整变化成为只读的不可定位记录，不会让草稿审阅白屏', () => {
  const seed = seedCollaborationState();
  const catalog = seed.shared.catalogs.group_retail_ops![0]!;
  const draft = structuredClone(seededRetailV2Draft(catalog.data));
  draft.changes = [{
    changeId: 'legacy-ai-state', category: 'AI建模', summary: '旧建模过程状态已更新',
  }];

  const review = projectDraftReview({ draft, baseCatalog: catalog });

  assert.equal(review.changes.length, 0);
  assert.deepEqual(review.unlinkedChanges, [{
    type: 'UNLINKED_CHANGE', changeId: 'legacy-ai-state', category: 'AI建模',
    summary: '旧建模过程状态已更新', reason: 'PROCESS_ONLY',
  }]);
});

test('手工修改实体、字段和指标后生成可定位的对象级 Before/After', () => {
  const runtime = createCollaborationRuntime(new MemoryStorage());
  const catalog = runtime.getCatalog('group_retail_ops', 'V1');
  assert.ok(catalog);
  const draft = runtime.openDraft({
    actorUserId: 'user_administer', modelSpaceId: 'group_retail_ops', materialized: catalog.data,
  });
  const changed = structuredClone(catalog.data);
  const entity = (changed.workspaceData.entities as Array<Record<string, unknown>>)[0]!;
  entity.description = '商品主数据补充经营用途说明';
  const attribute = (entity.attributes as Array<Record<string, unknown>>)[0]!;
  attribute.attributeName = '商品经营编码';
  const metric = (changed.metricData.metrics as Array<Record<string, unknown>>)[0]!;
  metric.description = '按有效订单计算销售金额';

  const saved = runtime.saveDraft({
    actorUserId: 'user_administer', draftId: draft.draftId,
    expectedRevision: draft.revision, materialized: changed,
  });

  assert.deepEqual(saved.changes.map((item) => item.kind).sort(), ['ENTITY', 'FIELD', 'METRIC']);
  assert.ok(saved.changes.every((item) => item.operation === 'MODIFIED'));
  assert.ok(saved.changes.every((item) => item.objectRef && item.name && item.changedFields?.length));
  assert.ok(saved.changes.find((item) => item.kind === 'FIELD')?.objectRef?.ownerId);
});

test('预置 V2 草稿冻结 24 项完整对象变化并符合业务分类与操作分布', () => {
  const seed = seedCollaborationState();
  const draft = seededRetailV2Draft(seed.shared.catalogs.group_retail_ops![0]!.data);
  const catalog = seed.shared.catalogs.group_retail_ops?.[0];
  assert.ok(draft);
  assert.ok(catalog);
  const review = projectDraftReview({ draft, baseCatalog: catalog });

  assert.equal(review.changes.length, 24);
  assert.deepEqual(review.countsByKind, {
    ENTITY: 1, EVENT: 2, FIELD: 4, RELATION: 3, DIMENSION: 2,
    METRIC: 4, RULE: 3, ALIAS: 3, TIME_RULE: 2,
  });
  assert.deepEqual(review.countsByOperation, { MODIFIED: 20, ADDED: 3, REMOVED: 1 });
  assert.ok(review.changes.every((change) => change.changedFields.length > 0));
  assert.ok(review.changes.every((change) => change.evidenceRefs.length > 0));
  assert.ok(review.changes.every((change) => change.affectedRefs.length > 0));
  assert.ok(review.changes.filter((change) => change.kind === 'FIELD').every((change) => change.objectRef.ownerId));
  for (const phrase of ['退款时点', '退货状态', '客户等级', '机器人流量', '库存快照', '营业日']) {
    assert.match(review.changes.map((change) => `${change.name} ${change.summary}`).join(' '), new RegExp(phrase));
  }
});

test('草稿变化支持搜索和每次 20 项渐进呈现，100 项以上仍全部可达', () => {
  const base = seededRetailV2Draft().changes[0]!;
  const changes = Array.from({ length: 105 }, (_, index) => ({
    ...structuredClone(base), changeId: `large-${index + 1}`, name: `变化 ${index + 1}`,
    summary: index === 104 ? '唯一可搜索的库存快照变化' : `普通变化 ${index + 1}`,
  }));
  assert.equal(paginateDraftChanges(changes, { query: '', limit: 20 }).visible.length, 20);
  assert.equal(paginateDraftChanges(changes, { query: '', limit: 120 }).visible.length, 105);
  assert.deepEqual(
    paginateDraftChanges(changes, { query: '唯一可搜索', limit: 20 }).visible.map((item) => item.changeId),
    ['large-105'],
  );
});

test('草稿变化使用全局优先级排序，不按对象类别分别截断', () => {
  const base = seededRetailV2Draft().changes[0]!;
  const changes = [
    { ...structuredClone(base), changeId: 'normal', name: '普通修改', operation: 'MODIFIED' as const, attention: 'NORMAL' as const },
    { ...structuredClone(base), changeId: 'verify', name: '待确认修改', operation: 'MODIFIED' as const, attention: 'VERIFY' as const },
    { ...structuredClone(base), changeId: 'conflict', name: '冲突修改', operation: 'MODIFIED' as const, attention: 'CONFLICT' as const },
    { ...structuredClone(base), changeId: 'removed', name: '删除对象', operation: 'REMOVED' as const, attention: 'NORMAL' as const },
  ];

  assert.deepEqual(
    paginateDraftChanges(changes, { query: '', limit: 20 }).visible.map((item) => item.changeId),
    ['removed', 'conflict', 'verify', 'normal'],
  );
});

test('固定 24 项不显示搜索，超过 40 项才显示且可以按对象类型查询', () => {
  const shouldShowSearch = (draftComparisonModule as unknown as {
    shouldShowDraftSearch?: (count: number) => boolean;
  }).shouldShowDraftSearch;
  assert.equal(typeof shouldShowSearch, 'function');
  assert.equal(shouldShowSearch?.(24), false);
  assert.equal(shouldShowSearch?.(40), false);
  assert.equal(shouldShowSearch?.(41), true);

  const changes = seededRetailV2Draft().changes;
  const metrics = paginateDraftChanges(changes, { query: '指标', limit: 40 });
  assert.equal(metrics.total, 4);
  assert.ok(metrics.visible.every((item) => item.kind === 'METRIC'));
});

test('草稿全部变化包括删除项都能在右侧比较视图定位', () => {
  const seed = seedCollaborationState();
  const draft = seededRetailV2Draft(seed.shared.catalogs.group_retail_ops![0]!.data);
  const catalog = seed.shared.catalogs.group_retail_ops![0]!;
  const browser = projectDraftBrowser(draft, catalog);
  const objectKeys = new Set(browser.objects.map((item) => modelBrowserRefKey(item.ref)));
  assert.deepEqual(
    draft.changes.filter((item) => !objectKeys.has(modelBrowserRefKey(item.objectRef!))).map((item) => item.changeId),
    [],
  );
});

test('R2 草稿建模过程能还原八个来源、六项互证和三项冲突决定历史', () => {
  const seed = seedCollaborationState();
  const draft = seededRetailV2Draft(seed.shared.catalogs.group_retail_ops![0]!.data);
  const catalog = seed.shared.catalogs.group_retail_ops![0]!;
  const summary = projectDraftWorkflowSummary(draft);
  const browser = projectDraftBrowser(draft, catalog);

  assert.deepEqual(summary, {
    batchRevision: 2,
    sourceCount: 8,
    findingCount: 6,
    resolvedConflictCount: 3,
    changeCount: 24,
  });
  const resolved = browser.reconciliationFindings.filter((item) => item.resolution);
  assert.equal(resolved.length, 3);
  assert.ok(resolved.every((item) => item.resolution?.decidedBy === 'Kenan Zhang'));
  assert.ok(resolved.every((item) => item.resolution?.reason.match(/[\u3400-\u9fff]/)));
  assert.ok(resolved.every((item) => item.claims.length >= 2));
});

test('发布前检查来自冻结草稿和审核状态，不伪装未完成项目为通过', () => {
  const seed = seedCollaborationState();
  const draft = seededRetailV2Draft(seed.shared.catalogs.group_retail_ops![0]!.data);
  const catalog = seed.shared.catalogs.group_retail_ops![0]!;
  const approved = {
    requestId: 'request_v2', draftId: draft.draftId, draftRevision: draft.revision,
    workspaceId: draft.workspaceId, modelSpaceId: draft.modelSpaceId, title: draft.title,
    authorUserId: draft.ownerUserId, baseCatalogVersion: draft.baseCatalogVersion,
    baseFingerprint: draft.baseFingerprint, proposedData: draft.materializedData,
    changes: draft.changes, evidenceRefs: [], status: 'APPROVED' as const,
    approvals: [{ reviewerUserId: 'user_zhengqing_xiong', reviewerName: 'Zhengqing Xiong', decision: 'APPROVED' as const, reason: '', decidedAt: '2026-08-09T20:00:00.000Z' }],
    expectedRevision: 2, submittedAt: '2026-08-09T19:00:00.000Z',
  };

  assert.deepEqual(projectDraftPublicationChecks({ draft, request: approved, baseCatalog: catalog }).map((item) => [item.label, item.passed]), [
    ['证据批次 R2 已冻结', true],
    ['8 个来源快照完整', true],
    ['24 项变化可追溯', true],
    ['审核已通过且基线仍为 V1', true],
  ]);
  const submitted = { ...approved, status: 'SUBMITTED' as const, approvals: [] };
  assert.equal(projectDraftPublicationChecks({ draft, request: submitted, baseCatalog: catalog }).at(-1)?.passed, false);
});
