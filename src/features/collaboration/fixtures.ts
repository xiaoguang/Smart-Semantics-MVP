import { adaptGroupRetailPublishedModel } from '../ai-modeling/group-retail-adapter.ts';
import { projectLinguanWorkspaceData, projectMetric2WorkspaceData } from '../ai-modeling/workspace-store-adapter.ts';
import type {
  CollaborationSeed, CollaborationWorkspace, DemoUser, MaterializedCollaborationData,
  ModelChange, PersonalDraft, WorkspaceMembership,
} from './types.ts';
import { buildRetailEvidenceSidecar } from './retail-evidence-fixture.ts';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import { projectModelingDocument } from '../modeling-document-projector/index.ts';

export const demoUsers: DemoUser[] = [
  { userId: 'user_bo_gao', username: 'bo.gao', displayName: 'Bo Gao', initials: 'BG', demoPassword: 'R7m!Q2v#L9pX' },
  { userId: 'user_zhiyuan_xu', username: 'zhiyuan.xu', displayName: 'Zhiyuan Xu', initials: 'ZX', demoPassword: 'X6p#D2y@H9qM' },
  { userId: 'user_xiaoguang_ye', username: 'xiaoguang.ye', displayName: 'Xiaoguang Ye', initials: 'XY', demoPassword: 'Y3k@F7s!P9dL' },
  { userId: 'user_guorui_yuan', username: 'guorui.yuan', displayName: 'Guorui Yuan', initials: 'GY', demoPassword: 'G5u!N8b#E2wK' },
  { userId: 'user_kenan_zhang', username: 'kenan.zhang', displayName: 'Kenan Zhang', initials: 'KZ', demoPassword: 'K4z@N8c!W3sT' },
  { userId: 'user_zhengqing_xiong', username: 'zhengqing.xiong', displayName: 'Zhengqing Xiong', initials: 'ZX', demoPassword: 'B8r!J5n#V2tC' },
  { userId: 'user_hao_xu', username: 'hao.xu', displayName: 'Hao Xu', initials: 'HX', demoPassword: 'H9v#A4m@Q6xR' },
  { userId: 'user_lexiu_sun', username: 'lexiu.sun', displayName: 'Lexiu Sun', initials: 'LS', demoPassword: 'S2l@C7r!T5yD' },
  { userId: 'user_administer', username: 'administrator', displayName: 'Administrator', initials: 'AD', demoPassword: 'A7m!R9x#K4qV' },
];

export const workspaceDefinitions: CollaborationWorkspace[] = [
  {
    workspaceId: 'erp_data_governance', productId: 'retail_semantic_modeling_product',
    displayName: 'ERP 数据治理', modelProjectIds: ['guanyijia_erp'],
  },
  {
    workspaceId: 'retail_semantic_modeling', productId: 'retail_semantic_modeling_product',
    displayName: '零售经营建模', modelProjectIds: ['group_retail_ops'],
  },
];

export const workspaceMemberships: WorkspaceMembership[] = [
  ['user_bo_gao', 'EDITOR'], ['user_zhiyuan_xu', 'REVIEWER'], ['user_xiaoguang_ye', 'PUBLISHER'], ['user_guorui_yuan', 'VIEWER'],
].map(([userId, role]) => ({ workspaceId: 'erp_data_governance', userId, role, status: 'ACTIVE' })) as WorkspaceMembership[];
workspaceMemberships.push(...[
  ['user_kenan_zhang', 'EDITOR'], ['user_zhengqing_xiong', 'REVIEWER'], ['user_hao_xu', 'PUBLISHER'], ['user_lexiu_sun', 'VIEWER'],
].map(([userId, role]) => ({ workspaceId: 'retail_semantic_modeling', userId, role, status: 'ACTIVE' })) as WorkspaceMembership[]);
workspaceMemberships.push(
  { workspaceId: 'erp_data_governance', userId: 'user_administer', role: 'ADMIN', status: 'ACTIVE' },
  { workspaceId: 'retail_semantic_modeling', userId: 'user_administer', role: 'ADMIN', status: 'ACTIVE' },
);

export function groupRetailV1(): MaterializedCollaborationData {
  const model = adaptGroupRetailPublishedModel();
  const semantic = {
    systemCode: 'group_retail_ops', sourceFingerprint: model.sourceSha256,
    dimensions: structuredClone(model.dimensions) as unknown as Array<Record<string, unknown>>,
    ruleCandidates: structuredClone(model.ruleCandidates) as unknown as Array<Record<string, unknown>>,
    hierarchyDefinitions: structuredClone(model.hierarchies) as unknown as Array<Record<string, unknown>>,
    generationNotes: structuredClone(model.generationNotes) as unknown as Array<Record<string, unknown>>,
    compatibilityNotes: structuredClone(model.compatibilityNotes),
  };
  return {
    workspaceData: projectLinguanWorkspaceData(model) as unknown as Record<string, unknown>,
    metricData: projectMetric2WorkspaceData(model) as unknown as Record<string, unknown>,
    workbenchState: { systemCode: 'group_retail_ops', modelVersion: 'V1', evidencePackageId: 'retail_group_storage' },
    semanticSidecar: buildRetailEvidenceSidecar(semantic, 1),
  };
}

const guanyijiaBaselineData = projectModelingDocument(guanyijiaFrozenModelingArtifact, null, {
  systemCode: 'guanyijia_erp', modelSpaceId: 'guanyijia_erp',
  datasourceName: 'guanyijia_mysql', schemaName: 'jsh_erp', ownerName: 'AI 建模',
}).materializedData;

export function guanyijiaV1(): MaterializedCollaborationData {
  return structuredClone(guanyijiaBaselineData);
}

const draftId = 'draft_kenan_group_retail_v2';
const draftRef = (kind: NonNullable<ModelChange['kind']>, objectId: string, ownerId?: string) => ({
  scope: 'DRAFT' as const, draftId, kind, objectId, ...(ownerId ? { ownerId } : {}),
});
const change = (input: {
  id: string; operation: NonNullable<ModelChange['operation']>; kind: NonNullable<ModelChange['kind']>;
  name: string; objectId: string; ownerId?: string; before?: string; after?: string;
  evidence: string[]; attention?: NonNullable<ModelChange['attention']>; summary: string; category: string;
}): ModelChange => ({
  changeId: input.id, category: input.category, summary: input.summary,
  operation: input.operation, kind: input.kind, name: input.name,
  objectRef: draftRef(input.kind, input.objectId, input.ownerId),
  changedFields: [{ label: '业务口径', ...(input.before ? { before: input.before } : {}), ...(input.after ? { after: input.after } : {}) }],
  evidenceRefs: input.evidence, affectedRefs: [], attention: input.attention ?? 'NORMAL',
});

export function retailV2Changes(aliases: Array<Record<string, unknown>>, refDraftId = draftId): ModelChange[] {
  const modifiedAlias = aliases.find((item) => item.targetCode === 'metric_net_sales')!;
  const removedAlias = aliases.find((item) => item.targetCode === 'entity_customer')!;
  const changes: ModelChange[] = [
    change({ id: 'v2-entity-customer', operation: 'MODIFIED', kind: 'ENTITY', category: '实体', name: '客户', objectId: 'entity_customer', before: '客户当前等级', after: '客户等级带生效时间', evidence: ['GR004', 'GR018'], attention: 'VERIFY', summary: '客户主数据补充等级生效时间语义' }),
    change({ id: 'v2-event-return', operation: 'MODIFIED', kind: 'EVENT', category: '事件', name: '退货处理', objectId: 'event_return_processing', before: '退货发起即扣减', after: '退款完成后扣减', evidence: ['GR002', 'GR014', 'GR017', 'GR020'], attention: 'CONFLICT', summary: '退货事件明确区分发起与退款完成' }),
    change({ id: 'v2-event-search', operation: 'MODIFIED', kind: 'EVENT', category: '事件', name: '搜索转化', objectId: 'event_search_conversion', before: '全部搜索事件', after: '排除机器人和压测流量', evidence: ['GR008', 'GR016', 'GR022'], attention: 'VERIFY', summary: '搜索转化事件排除机器人流量' }),
    change({ id: 'v2-field-customer-tier', operation: 'MODIFIED', kind: 'FIELD', category: '字段', name: '客户等级', objectId: 'customer_tier', ownerId: 'entity_customer', before: '当前等级编码', after: '带生效时间的等级编码', evidence: ['GR004', 'GR018'], attention: 'VERIFY', summary: '客户等级字段补充生效时间约束' }),
    change({ id: 'v2-field-return-status', operation: 'MODIFIED', kind: 'FIELD', category: '字段', name: '退货状态', objectId: 'return_status', ownerId: 'event_return_processing', before: '退货状态', after: '发起、审核、退款完成分阶段状态', evidence: ['GR002', 'GR015', 'GR020'], attention: 'CONFLICT', summary: '退货状态拆分处理阶段' }),
    change({ id: 'v2-field-return-time', operation: 'MODIFIED', kind: 'FIELD', category: '字段', name: '退货时间', objectId: 'return_time', ownerId: 'event_return_processing', before: '单一退货时间', after: '以退款完成时间参与净销售额', evidence: ['GR002', 'GR014', 'GR020'], attention: 'CONFLICT', summary: '退款时点作为净销售额扣减时间' }),
    change({ id: 'v2-field-search-keyword', operation: 'MODIFIED', kind: 'FIELD', category: '字段', name: '搜索词', objectId: 'search_keyword', ownerId: 'event_search_conversion', before: '原始搜索词', after: '仅统计有效用户流量的搜索词', evidence: ['GR007', 'GR008'], attention: 'VERIFY', summary: '搜索词统计排除机器人流量' }),
    change({ id: 'v2-rel-return-sales', operation: 'MODIFIED', kind: 'RELATION', category: '关系', name: '退货原订单', objectId: 'rel_return_sales', before: '退货关联订单行', after: '退款完成事件关联原订单行', evidence: ['GR001', 'GR002', 'GR020'], summary: '退货关系明确退款完成事件路径' }),
    change({ id: 'v2-rel-search-product', operation: 'MODIFIED', kind: 'RELATION', category: '关系', name: '搜索商品', objectId: 'rel_search_product', before: '搜索词关联商品', after: '有效搜索事件关联商品', evidence: ['GR007', 'GR016'], summary: '搜索关系限定为有效用户流量' }),
    change({ id: 'v2-rel-inventory-store', operation: 'MODIFIED', kind: 'RELATION', category: '关系', name: '库存门店', objectId: 'rel_inventory_store', before: '库存关联门店', after: '最新有效快照关联门店', evidence: ['GR003', 'GR013', 'GR021'], summary: '库存关系限定最新有效快照' }),
    change({ id: 'v2-dim-customer-tier', operation: 'MODIFIED', kind: 'DIMENSION', category: '维度', name: '客户等级', objectId: 'dim_customer_tier', before: '当前客户等级', after: '按生效时间取客户等级', evidence: ['GR004', 'GR018'], attention: 'VERIFY', summary: '客户等级维度按生效时间解释' }),
    change({ id: 'v2-dim-business-date', operation: 'MODIFIED', kind: 'DIMENSION', category: '维度', name: '业务日期', objectId: 'dim_business_date', before: '自然日期', after: '门店营业日', evidence: ['GR012', 'GR019'], attention: 'VERIFY', summary: '业务日期改为可跨自然日的营业日' }),
    change({ id: 'v2-metric-net-sales', operation: 'MODIFIED', kind: 'METRIC', category: '指标', name: '净销售额', objectId: 'metric_net_sales', before: '退货发起即扣减', after: '已完成退款才扣减', evidence: ['GR011', 'GR014', 'GR017', 'GR020'], attention: 'CONFLICT', summary: '净销售额采用退款完成时点' }),
    change({ id: 'v2-metric-return-rate', operation: 'MODIFIED', kind: 'METRIC', category: '指标', name: '退货率', objectId: 'metric_return_rate', before: '发起退货数量/销售数量', after: '退款完成数量/销售数量', evidence: ['GR002', 'GR020'], attention: 'VERIFY', summary: '退货率按退款完成状态计算' }),
    change({ id: 'v2-metric-current-inventory', operation: 'MODIFIED', kind: 'METRIC', category: '指标', name: '当前库存量', objectId: 'metric_current_inventory', before: '任意最新记录', after: '营业日最后有效快照', evidence: ['GR003', 'GR013', 'GR021'], attention: 'VERIFY', summary: '库存指标固定最新有效快照时点' }),
    change({ id: 'v2-metric-search-conversion', operation: 'MODIFIED', kind: 'METRIC', category: '指标', name: '搜索转化率', objectId: 'metric_search_conversion_rate', before: '全部流量转化', after: '排除机器人和压测流量', evidence: ['GR008', 'GR016', 'GR022'], attention: 'VERIFY', summary: '搜索转化率排除机器人流量' }),
    change({ id: 'v2-rule-refund', operation: 'MODIFIED', kind: 'RULE', category: '规则', name: '退款完成扣减规则', objectId: 'rule_completed_refund', before: '退款状态不明确', after: 'COMPLETED 才扣减净销售额', evidence: ['GR014', 'GR017', 'GR020'], attention: 'CONFLICT', summary: '退款扣减规则明确完成状态' }),
    change({ id: 'v2-rule-bot', operation: 'MODIFIED', kind: 'RULE', category: '规则', name: '机器人流量排除规则', objectId: 'rule_search_bot_exclusion', before: '仅排除 bot', after: '排除 bot 与 internal_test', evidence: ['GR008', 'GR016'], attention: 'VERIFY', summary: '机器人流量排除规则与查询保持一致' }),
    change({ id: 'v2-rule-tier-time', operation: 'ADDED', kind: 'RULE', category: '规则', name: '客户等级生效规则', objectId: 'rule_customer_tier_effective_time', after: '按等级规则计算完成时间生效', evidence: ['GR004', 'GR018'], attention: 'VERIFY', summary: '新增客户等级生效时间规则' }),
    change({ id: 'v2-alias-modified', operation: 'MODIFIED', kind: 'ALIAS', category: '同义词', name: '净销售额业务称呼', objectId: String(modifiedAlias.id), before: String(modifiedAlias.aliasText), after: '销售净额', evidence: ['GR009', 'GR017'], summary: '净销售额同义词采用业务常用称呼' }),
    change({ id: 'v2-alias-added', operation: 'ADDED', kind: 'ALIAS', category: '同义词', name: '实收销售额', objectId: '990001', after: '净销售额的业务同义词', evidence: ['GR009'], attention: 'VERIFY', summary: '新增净销售额业务称呼“实收销售额”' }),
    change({ id: 'v2-alias-removed', operation: 'REMOVED', kind: 'ALIAS', category: '同义词', name: String(removedAlias.aliasText), objectId: String(removedAlias.id), before: '客户的泛化称呼', evidence: ['GR006'], attention: 'VERIFY', summary: '删除含义过宽的客户旧同义词' }),
    change({ id: 'v2-time-inventory', operation: 'MODIFIED', kind: 'TIME_RULE', category: '时间语义', name: '库存快照时间', objectId: 'inventory_snapshot_time', before: '读取最后一次记录', after: '读取营业日最后一次有效快照', evidence: ['GR013', 'GR021'], attention: 'VERIFY', summary: '库存快照时点限定营业日最后有效记录' }),
    change({ id: 'v2-time-tier', operation: 'ADDED', kind: 'TIME_RULE', category: '时间语义', name: '客户等级生效时间', objectId: 'customer_tier_effective_time', after: '按等级规则计算完成时间生效', evidence: ['GR004', 'GR018'], attention: 'VERIFY', summary: '新增客户等级生效时间语义' }),
  ];
  const impacts: Record<string, Array<[NonNullable<ModelChange['kind']>, string, string?]>> = {
    'v2-entity-customer': [['DIMENSION', 'dim_customer_tier']],
    'v2-event-return': [['METRIC', 'metric_net_sales']],
    'v2-event-search': [['METRIC', 'metric_search_conversion_rate']],
    'v2-field-customer-tier': [['DIMENSION', 'dim_customer_tier']],
    'v2-field-return-status': [['METRIC', 'metric_return_rate']],
    'v2-field-return-time': [['METRIC', 'metric_net_sales']],
    'v2-field-search-keyword': [['METRIC', 'metric_search_conversion_rate']],
    'v2-rel-return-sales': [['METRIC', 'metric_net_sales']],
    'v2-rel-search-product': [['METRIC', 'metric_search_conversion_rate']],
    'v2-rel-inventory-store': [['METRIC', 'metric_current_inventory']],
    'v2-dim-customer-tier': [['ENTITY', 'entity_customer']],
    'v2-dim-business-date': [['TIME_RULE', 'inventory_snapshot_time']],
    'v2-metric-net-sales': [['RULE', 'rule_completed_refund']],
    'v2-metric-return-rate': [['EVENT', 'event_return_processing']],
    'v2-metric-current-inventory': [['TIME_RULE', 'inventory_snapshot_time']],
    'v2-metric-search-conversion': [['RULE', 'rule_search_bot_exclusion']],
    'v2-rule-refund': [['METRIC', 'metric_net_sales']],
    'v2-rule-bot': [['METRIC', 'metric_search_conversion_rate']],
    'v2-rule-tier-time': [['DIMENSION', 'dim_customer_tier']],
    'v2-alias-modified': [['METRIC', 'metric_net_sales']],
    'v2-alias-added': [['METRIC', 'metric_net_sales']],
    'v2-alias-removed': [['ENTITY', 'entity_customer']],
    'v2-time-inventory': [['METRIC', 'metric_current_inventory']],
    'v2-time-tier': [['DIMENSION', 'dim_customer_tier']],
  };
  return changes.map((item) => ({
    ...item,
    objectRef: item.objectRef && item.objectRef.scope === 'DRAFT'
      ? { ...item.objectRef, draftId: refDraftId } : item.objectRef,
    affectedRefs: (impacts[item.changeId] ?? []).map(([kind, objectId, ownerId]) => draftRef(kind, objectId, ownerId)),
  })).map((item) => ({
    ...item,
    affectedRefs: item.affectedRefs?.map((ref) => ref.scope === 'DRAFT' ? { ...ref, draftId: refDraftId } : ref),
  }));
}

export function buildRetailV2Proposal(data: MaterializedCollaborationData, refDraftId = draftId) {
  const next = structuredClone(data);
  const aliases = next.workspaceData.aliases as Array<Record<string, unknown>>;
  const changes = retailV2Changes(aliases, refDraftId);
  const modifiedAliasId = changes.find((item) => item.changeId === 'v2-alias-modified')!.objectRef!.objectId;
  const removedAliasId = changes.find((item) => item.changeId === 'v2-alias-removed')!.objectRef!.objectId;
  const modifiedAlias = aliases.find((item) => String(item.id) === modifiedAliasId);
  if (modifiedAlias) modifiedAlias.aliasText = '销售净额';
  const removedIndex = aliases.findIndex((item) => String(item.id) === removedAliasId);
  if (removedIndex >= 0) aliases.splice(removedIndex, 1);
  aliases.push({
    id: 990001, aliasText: '实收销售额', targetType: 'METRIC', targetCode: 'metric_net_sales',
    targetName: '净销售额', priority: 100, source: 'MANUAL', isConfirmed: false, conflictStatus: 'NONE',
    sourceObjectCode: 'alias_manual_net_sales',
  });
  const calendars = next.workspaceData.calendars as Array<Record<string, unknown>>;
  if (calendars[0]) calendars[0].description = '统一门店营业日、经营周与跨日订单归属口径。';
  const sidecar = next.semanticSidecar!;
  next.semanticSidecar = buildRetailEvidenceSidecar({
    systemCode: sidecar.systemCode, sourceFingerprint: sidecar.sourceFingerprint,
    dimensions: structuredClone(sidecar.dimensions), ruleCandidates: structuredClone(sidecar.ruleCandidates),
    hierarchyDefinitions: structuredClone(sidecar.hierarchyDefinitions), generationNotes: structuredClone(sidecar.generationNotes),
    compatibilityNotes: structuredClone(sidecar.compatibilityNotes),
  }, 2);
  const businessRules = next.metricData.rules as Array<Record<string, unknown>>;
  businessRules.push({ ruleCode: 'rule_customer_tier_effective_time', ruleName: '客户等级生效规则', description: '客户等级从规则计算完成时间起生效。', targetObjectName: '客户等级' });
  const timeRules = next.workspaceData.rules as Array<Record<string, unknown>>;
  const inventoryTime = timeRules.find((item) => item.ruleCode === 'inventory_snapshot_time');
  if (inventoryTime) inventoryTime.ruleText = '库存读取营业日最后一次有效快照';
  timeRules.push({ id: 990002, ruleCode: 'customer_tier_effective_time', ruleText: '客户等级从规则计算完成时间起生效', calendarCode: 'retail_business_calendar', granularity: 'SECOND', status: 'ACTIVE' });
  if (next.semanticSidecar.schemaVersion === 2) {
    const visibleAliasCodes = new Set(aliases.map((alias) => String(alias.sourceObjectCode ?? alias.id)));
    next.semanticSidecar.objectEvidence = next.semanticSidecar.objectEvidence.filter((binding) =>
      binding.objectKind !== 'ALIAS' || visibleAliasCodes.has(binding.objectCode));
    next.semanticSidecar.objectEvidence.push(
      {
        objectKind: 'RULE', objectCode: 'rule_customer_tier_effective_time', status: 'VERIFIED',
        evidenceRefs: ['GR018'], supportClaimIds: ['claim_sharepoint_customer_tier'],
      },
      {
        objectKind: 'ALIAS', objectCode: 'alias_manual_net_sales', status: 'NEEDS_CONFIRMATION',
        evidenceRefs: [], supportClaimIds: [],
      },
      {
        objectKind: 'TIME_RULE', objectCode: 'customer_tier_effective_time', status: 'VERIFIED',
        evidenceRefs: ['GR018'], supportClaimIds: ['claim_sharepoint_customer_tier'],
      },
    );
  }
  return { materializedData: next, changes };
}

export function seededRetailV2Draft(data = groupRetailV1()): PersonalDraft {
  const { materializedData, changes } = buildRetailV2Proposal(data, draftId);
  return {
    draftId, workspaceId: 'retail_semantic_modeling',
    modelSpaceId: 'group_retail_ops', ownerUserId: 'user_kenan_zhang',
    title: '多来源互证与经营口径升级 V2', baseCatalogVersion: 'V1', baseFingerprint: 'catalog_group_retail_v1',
    revision: 1, status: 'ACTIVE', materializedData,
    changes,
    createdAt: '2026-08-08T09:00:00.000Z', updatedAt: '2026-08-08T09:00:00.000Z',
  };
}

export function seedCollaborationState(): CollaborationSeed {
  const data = groupRetailV1();
  const guanyijiaData = guanyijiaV1();
  const seed: CollaborationSeed = {
    shared: {
      schemaVersion: 4, revision: 1, requests: [], packageCatalogHistory: {}, migrationWarnings: [], catalogs: {
        group_retail_ops: [{
          catalogId: 'catalog_group_retail_v1', catalogVersion: 'V1', fingerprint: 'catalog_group_retail_v1',
          modelSpaceId: 'group_retail_ops', data, authorUserId: 'seed', reviewerUserIds: ['seed'],
          publisherUserId: 'seed', publishedAt: '2026-08-01T09:00:00.000Z', requestId: 'seed_v1',
        }],
        guanyijia_erp: [{
          catalogId: 'catalog_guanyijia_v1', catalogVersion: 'V1',
          fingerprint: `guanyijia:${guanyijiaFrozenModelingArtifact.markdown.sha256}`,
          modelSpaceId: 'guanyijia_erp', data: guanyijiaData,
          authorUserId: 'system_baseline', reviewerUserIds: [], publisherUserId: 'system_baseline',
          publishedAt: guanyijiaFrozenModelingArtifact.updatedAt, requestId: 'SYSTEM_BASELINE',
          publicationReason: '证据基线预置',
        }],
      },
    },
    drafts: {},
  };
  return JSON.parse(JSON.stringify(seed)) as CollaborationSeed;
}
