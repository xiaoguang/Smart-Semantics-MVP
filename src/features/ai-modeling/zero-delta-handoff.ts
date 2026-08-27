import type { CollaborationCatalog } from '../collaboration/types.ts';
import type { ZeroDeltaModelingHandoffReceipt } from '../standardization-deliverable/types.ts';

export type ZeroDeltaM4View = {
  kind: 'ZERO_DELTA_MODELING_HANDOFF';
  semanticChangeCount: 0;
  catalogMessage: '正式模型本次未改变';
  draftMessage: '本次未创建新的个人草稿';
  governanceMessage: '已保存资料审阅与差异决定记录';
  catalog: CollaborationCatalog;
  draftCount: number;
  receipt: ZeroDeltaModelingHandoffReceipt;
};

export function projectZeroDeltaM4View(input: {
  receipt: ZeroDeltaModelingHandoffReceipt;
  catalog: CollaborationCatalog;
  draftCount: number;
  mutationPorts?: {
    openDraft(): unknown;
    saveDraft(): unknown;
    submitDraft(): unknown;
    publishDraft(): unknown;
  };
}): ZeroDeltaM4View {
  if (input.receipt.kind !== 'ZERO_DELTA_MODELING_HANDOFF'
    || input.catalog.catalogId !== input.receipt.protectedCatalogId
    || input.receipt.semanticPayloadSha256.length !== 64) {
    throw new Error('M4零变化Receipt与正式Catalog不一致');
  }
  return {
    kind: 'ZERO_DELTA_MODELING_HANDOFF',
    semanticChangeCount: 0,
    catalogMessage: '正式模型本次未改变',
    draftMessage: '本次未创建新的个人草稿',
    governanceMessage: '已保存资料审阅与差异决定记录',
    catalog: input.catalog,
    draftCount: input.draftCount,
    receipt: input.receipt,
  };
}
