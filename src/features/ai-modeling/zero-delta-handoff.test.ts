import assert from 'node:assert/strict';
import test from 'node:test';
import { projectZeroDeltaM4View } from './zero-delta-handoff.ts';

test('zero-change handoff describes the business outcome without Catalog, M4, or receipt terminology', () => {
  const view = projectZeroDeltaM4View({
    receipt: {
      kind: 'ZERO_DELTA_MODELING_HANDOFF',
      protectedCatalogId: 'formal-v1',
      semanticPayloadSha256: 'a'.repeat(64),
    },
    catalog: { catalogId: 'formal-v1' } as never,
    draftCount: 0,
  });

  assert.equal(view.catalogMessage, '正式模型本次未改变');
  assert.equal(view.draftMessage, '本次未创建新的个人草稿');
  assert.equal(view.governanceMessage, '已保存资料审阅与差异决定记录');
});
