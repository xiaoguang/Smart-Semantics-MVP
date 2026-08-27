import type { CatalogEvidenceSidecarV2, FrozenEvidenceClaim, FrozenReconciliationFinding, FrozenSourceSnapshot } from './types.ts';
import { adaptGroupRetailPublishedModel } from '../ai-modeling/group-retail-adapter.ts';
import {
  buildRetailRegistryBlockers,
  projectRetailObjectEvidence,
  retailEvidenceClaims,
  retailEvidenceForRevision,
  retailEvidenceSources,
} from '../evidence-registry/retail-evidence-registry.ts';

type SemanticPart = Pick<CatalogEvidenceSidecarV2,
  'systemCode' | 'sourceFingerprint' | 'dimensions' | 'ruleCandidates' | 'hierarchyDefinitions' | 'generationNotes' | 'compatibilityNotes'>;

export function buildRetailEvidenceSidecar(semantic: SemanticPart, revision: 1 | 2): CatalogEvidenceSidecarV2 {
  const registeredEvidence = retailEvidenceForRevision(revision);
  const sources: FrozenSourceSnapshot[] = retailEvidenceSources.map((source) => ({
    snapshotId: `${source.connectionId}-snapshot-r${revision}`,
    connectionId: source.connectionId,
    displayName: source.displayName,
    connectorType: source.connectorType,
    versionRef: revision === 1 ? source.versionR1 : source.versionR2,
    role: source.role,
    authority: source.authority,
    summary: source.summary,
    objectCounts: structuredClone(source.objectCounts),
    ...(source.upstreamConnectionIds ? { upstreamSnapshotIds: source.upstreamConnectionIds.map((connectionId) => `${connectionId}-snapshot-r${revision}`) } : {}),
    evidenceIds: registeredEvidence.filter((entry) => entry.connectionId === source.connectionId).map((entry) => entry.evidenceId),
  }));
  const evidence = registeredEvidence.map((entry) => ({
    evidence_id: entry.evidenceId,
    source_file: retailEvidenceSources.find((source) => source.connectionId === entry.connectionId)!.displayName,
    section: entry.section, quote: entry.statement, snapshot_id: `${entry.connectionId}-snapshot-r${revision}`,
  }));
  const locators = Object.fromEntries(registeredEvidence.map((entry) => [entry.evidenceId, structuredClone(entry.locator)]));
  const availableEvidence = new Set(registeredEvidence.map((entry) => entry.evidenceId));
  const claims: FrozenEvidenceClaim[] = retailEvidenceClaims
    .filter((claim) => claim.evidenceRefs.every((evidenceId) => availableEvidence.has(evidenceId)))
    .map((claim) => ({
      claimId: claim.claimId, sourceId: claim.sourceId, topic: claim.topic,
      assertion: claim.assertion, evidenceRefs: [...claim.evidenceRefs], authority: claim.authority,
      ...(claim.upstreamClaimIds ? { upstreamClaimIds: [...claim.upstreamClaimIds] } : {}),
    }));
  const resolution = (reason: string): NonNullable<FrozenReconciliationFinding['resolution']> => ({
    decidedByUserId: 'user_kenan_zhang', decidedBy: 'Kenan Zhang',
    decidedAt: '2026-08-09T18:20:00.000Z', reason,
  });
  const registryBlockerById = new Map(buildRetailRegistryBlockers(
    retailEvidenceSources.map((source) => source.connectionId), revision,
  ).map((finding) => [finding.findingId, finding]));
  const registryFinding = (input: {
    findingId: string; fallbackTitle: string; fallbackClaimIds: string[]; agreement: string;
    decision: string; reason: string;
  }): FrozenReconciliationFinding => {
    const blocker = registryBlockerById.get(input.findingId);
    return {
      findingId: input.findingId,
      title: blocker?.title ?? input.fallbackTitle,
      status: 'CONSISTENT',
      claimIds: blocker ? [...blocker.claimIds] : [...input.fallbackClaimIds],
      agreement: input.agreement,
      ...(blocker ? {
        originStatus: 'CONFLICT' as const,
        difference: blocker.difference,
        resolution: resolution(input.reason),
      } : {}),
      decision: input.decision,
      affectedObjectCodes: blocker ? [...blocker.affectedObjectCodes] : [],
    };
  };
  const findings: FrozenReconciliationFinding[] = [
    registryFinding({ findingId: 'finding_net_sales_refund', fallbackTitle: '净销售额退款时点', fallbackClaimIds: ['claim_sharepoint_net_sales', 'claim_github_net_sales', 'claim_minio_net_sales', 'claim_kafka_return_completed'], agreement: '正式口径、SQL和事件均支持按退款完成时点扣减。', decision: '采用已完成退款口径。', reason: '正式制度和已上线 SQL 权威更高，旧日报需要随模型 V2 一并修正。' }),
    { findingId: 'finding_customer_tier_time', title: '客户等级生效时间', status: 'CONSISTENT', claimIds: ['claim_mongodb_customer_tier', 'claim_mongodb_current_tier', 'claim_sharepoint_customer_tier'], agreement: '画像历史与运营制度都要求保存生效时间。', decision: '客户等级增加生效时间语义。', affectedObjectCodes: ['dim_customer_tier', 'customer_tier'] },
    registryFinding({ findingId: 'finding_bot_filter', fallbackTitle: '机器人流量排除', fallbackClaimIds: ['claim_elasticsearch_bot_filter', 'claim_github_bot_filter'], agreement: '当前查询和分析 SQL 都排除机器人及压测流量。', decision: '搜索指标默认排除机器人和压测流量。', reason: '统一采用代码仓库和当前查询中的双重排除条件，旧看板标记为待下线。' }),
    { findingId: 'finding_inventory_snapshot', title: '库存快照时点', status: 'CONSISTENT', claimIds: ['claim_mysql_inventory_grain', 'claim_kafka_inventory_order'], agreement: '数据库粒度和事件顺序共同支持最新有效快照。', decision: '营业日内取最后一份有效快照。', affectedObjectCodes: ['metric_current_inventory', 'inventory_snapshot_time'] },
    registryFinding({ findingId: 'finding_business_day', fallbackTitle: '营业日归属', fallbackClaimIds: ['claim_minio_business_day', 'claim_sharepoint_business_day'], agreement: '两个正式文档均要求按门店营业日归属。', decision: '订单按支付完成时所属门店营业日归属。', reason: 'V2 增加营业日归属规则，历史事件按门店日历重算，不修改原始事件时间。' }),
    { findingId: 'finding_net_sales_alias', title: '净销售额业务称呼', status: 'DERIVED', claimIds: ['claim_semantica_net_sales_alias'], difference: '术语图来自 SharePoint 的派生投影，不计为第二份独立真相。', decision: '作为同义词候选保留。', affectedObjectCodes: ['metric_net_sales'] },
  ];
  const snapshotIds = sources.map((source) => source.snapshotId).sort();
  const model = adaptGroupRetailPublishedModel();
  const binding = (objectKind: Parameters<typeof projectRetailObjectEvidence>[0], objectCode: string, ownerCode?: string) =>
    projectRetailObjectEvidence(objectKind, objectCode, ownerCode, claims);
  const objectEvidence = [
    ...model.entities.map((item) => binding('ENTITY', item.code)),
    ...model.events.map((item) => binding('EVENT', item.code)),
    ...model.fields.map((item) => binding('FIELD', item.code, item.ownerTableCode)),
    ...model.relations.map((item) => binding('RELATION', item.code)),
    ...model.dimensions.map((item) => binding('DIMENSION', item.id)),
    ...model.metrics.map((item) => binding('METRIC', item.code)),
    ...model.hierarchies.map((item) => binding('HIERARCHY', item.id)),
    ...model.ruleCandidates.map((item) => binding('RULE', item.id)),
    ...model.aliases.map((item) => binding('ALIAS', item.id)),
    ...(model.timeSemantics?.rules ?? []).map((item) => binding('TIME_RULE', item.code)),
  ];
  return {
    ...structuredClone(semantic), schemaVersion: 2,
    batch: { batchId: `group_retail_ops-sources-r${revision}`, revision, frozenAt: revision === 1 ? '2026-08-01T08:30:00.000Z' : '2026-08-09T18:30:00.000Z', fingerprint: `retail-evidence-r${revision}-8-sources`, coverage: '订单、库存、退货、客户、搜索、营业日与经营指标', snapshotIds },
    sources, evidence, locators, claims, findings,
    objectEvidence,
  };
}
