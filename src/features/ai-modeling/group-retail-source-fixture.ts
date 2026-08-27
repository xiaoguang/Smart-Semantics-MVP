import { computeSourceBundleFingerprint, type EvidenceClaim, type ReconciliationFinding, type SourceAsset, type SourceEvidenceLocator } from './source-bundle.ts';
import { connectedGroupRetailConnectors } from './group-retail-connectors.ts';
import {
  buildRetailRegistryBlockers,
  retailEvidenceClaims,
  retailEvidenceForRevision,
  retailEvidenceSources,
} from '../evidence-registry/retail-evidence-registry.ts';

const connector = (id: string) => connectedGroupRetailConnectors.find((item) => item.connectorId === id)!;

function source(input: Omit<SourceAsset, 'connector' | 'evidenceDetail'> & { connectorId: string; versionRef: string }) : SourceAsset {
  const definition = connector(input.connectorId);
  return {
    ...input,
    connector: definition,
    evidenceDetail: {
      kind: 'CONNECTOR', connector: definition,
      snapshotId: input.fingerprint, versionRef: input.versionRef,
    },
  };
}

const sourceProjection: Record<string, Pick<SourceAsset, 'origin' | 'roles' | 'contentKinds'>> = {
  retail_mysql: { origin: 'DATABASE_CONNECTOR', roles: ['PHYSICAL_STRUCTURE', 'ACTUAL_USAGE'], contentKinds: ['DDL', 'TABLE', 'FIELD', 'KEY', 'PROFILE'] },
  retail_github: { origin: 'CODE_REPOSITORY', roles: ['PHYSICAL_STRUCTURE', 'ACTUAL_USAGE'], contentKinds: ['MIGRATION', 'MAPPER_SQL', 'ENUM', 'RULE'] },
  retail_semantica: { origin: 'GRAPH_STORE', roles: ['BUSINESS_DEFINITION', 'AUXILIARY_EVIDENCE'], contentKinds: ['RDF_TRIPLE', 'ONTOLOGY', 'NAMED_GRAPH', 'SPARQL'] },
  retail_sharepoint: { origin: 'ENTERPRISE_CONTENT', roles: ['BUSINESS_DEFINITION'], contentKinds: ['POLICY', 'METRIC_SPEC', 'VOCABULARY', 'VERSION_HISTORY'] },
  retail_mongodb: { origin: 'DOCUMENT_DATABASE', roles: ['PHYSICAL_STRUCTURE', 'ACTUAL_USAGE'], contentKinds: ['COLLECTION', 'JSON_SCHEMA', 'AGGREGATION_PIPELINE'] },
  retail_elasticsearch: { origin: 'SEARCH_INDEX', roles: ['ACTUAL_USAGE'], contentKinds: ['MAPPING', 'INDEX_TEMPLATE', 'QUERY_DSL', 'AGGREGATION'] },
  retail_minio: { origin: 'OBJECT_STORAGE', roles: ['BUSINESS_DEFINITION', 'AUXILIARY_EVIDENCE'], contentKinds: ['PDF', 'WPS', 'PNG', 'JPEG', 'CSV', 'PARQUET'] },
  retail_kafka: { origin: 'EVENT_STREAM', roles: ['ACTUAL_USAGE'], contentKinds: ['TOPIC', 'AVRO_SCHEMA', 'PARTITION_KEY', 'EVENT_SAMPLE'] },
};

export const groupRetailSourceAssets: SourceAsset[] = retailEvidenceSources.map((registered) => source({
  sourceId: registered.legacySourceId,
  connectorId: registered.legacyConnectorId,
  versionRef: registered.versionR2,
  displayName: registered.displayName,
  ...sourceProjection[registered.connectionId]!,
  fingerprint: `registry:${registered.connectionId}:${registered.versionR2}`,
  fixtureKey: `group-retail/${registered.connectionId}`,
  status: 'READY', authority: registered.authority,
  summary: registered.summary,
  evidenceIds: retailEvidenceForRevision(2).filter((entry) => entry.connectionId === registered.connectionId).map((entry) => entry.evidenceId),
  ...(registered.upstreamConnectionIds ? {
    upstreamSourceIds: registered.upstreamConnectionIds.map((connectionId) =>
      retailEvidenceSources.find((sourceEntry) => sourceEntry.connectionId === connectionId)!.legacySourceId),
  } : {}),
}));

export const groupRetailEvidenceLocators: Record<string, SourceEvidenceLocator> = Object.fromEntries(
  retailEvidenceForRevision(2).map((entry) => [entry.evidenceId, entry.locator]),
);

export const groupRetailEvidenceClaims: Array<EvidenceClaim & { sourceId: string }> = retailEvidenceClaims.map((claim) => ({
  claimId: claim.claimId,
  sourceId: retailEvidenceSources.find((sourceEntry) => sourceEntry.connectionId === claim.sourceId)!.legacySourceId,
  topic: claim.topic,
  subject: claim.topic,
  predicate: '来源结论',
  value: claim.assertion,
  evidenceRefs: [...claim.evidenceRefs],
  authority: claim.authority,
  ...(claim.upstreamClaimIds ? { upstreamClaimIds: [...claim.upstreamClaimIds] } : {}),
}));

export function createGroupRetailFindings(): ReconciliationFinding[] {
  const blockers: ReconciliationFinding[] = buildRetailRegistryBlockers(
    retailEvidenceSources.map((sourceEntry) => sourceEntry.connectionId), 2,
  ).map((finding) => ({
    findingId: finding.findingId, title: finding.title, conclusion: finding.difference, severity: 'BLOCKER',
    sourceIds: [...new Set(finding.claimIds.map((claimId) => retailEvidenceClaims.find((claim) => claim.claimId === claimId)!.sourceId)
      .map((connectionId) => retailEvidenceSources.find((sourceEntry) => sourceEntry.connectionId === connectionId)!.legacySourceId))],
    evidenceIds: [...finding.evidenceIds], affectedObjects: [...finding.affectedObjectCodes], recommendationId: 'accept_recommended',
    options: [
      { id: 'accept_recommended', label: '采用推荐结论', description: finding.difference },
      { id: 'keep_blocked', label: '保留阻断', description: '等待业务负责人确认。' },
    ],
  }));
  const weak = [
    ['group-weak-search-rate', '搜索转化率缺少正式指标定义', ['group-source-elasticsearch', 'group-source-kafka'], ['GR008', 'GR022']],
    ['group-weak-product-hierarchy', '商品分类成员层级只有本体投影', ['group-source-semantica-rdf'], ['GR010']],
    ['group-weak-inventory-document', '库存快照口径只有对象文件依据', ['group-source-minio'], ['GR013']],
    ['group-weak-promotion-rule', '促销核销时效只有代码校验依据', ['group-source-github'], ['GR015']],
    ['group-weak-profile-freshness', '客户画像刷新时点只有文档库快照依据', ['group-source-mongodb'], ['GR006']],
    ['group-weak-search-term', '搜索词维度只有派生索引依据', ['group-source-elasticsearch'], ['GR007']],
  ].map(([findingId, title, sourceIds, evidenceIds]) => ({
    findingId: findingId as string, title: title as string, conclusion: title as string, severity: 'WEAK' as const,
    sourceIds: sourceIds as string[], evidenceIds: evidenceIds as string[], affectedObjects: [], recommendationId: 'verify',
    options: [{ id: 'verify', label: '人工确认', description: '补充业务确认后纳入模型。' }],
  }));
  return [
    ...blockers,
    ...weak,
    {
      findingId: 'group-consistent-core', title: '核心交易结构与事件相互印证',
      conclusion: 'MySQL表结构、GitHub关联SQL和Kafka事件Schema可以定位到同一组业务对象。', severity: 'CONSISTENT',
      sourceIds: ['group-source-mysql', 'group-source-github', 'group-source-kafka'], evidenceIds: ['GR001', 'GR016', 'GR022'],
      affectedObjects: ['商品', '门店', '销售订单行'], recommendationId: 'accept',
      options: [{ id: 'accept', label: '接受一致结论', description: '来源之间没有实质冲突。' }],
    },
  ];
}

export const groupRetailBundleFingerprint = computeSourceBundleFingerprint(groupRetailSourceAssets.map((item) => item.fingerprint));
