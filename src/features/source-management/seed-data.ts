import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  createGuanyijiaFindings,
  guanyijiaCandidateModel,
  guanyijiaEvidenceFixture,
  guanyijiaRepositoryEvidenceFixture,
} from '../ai-modeling/guanyijia-fixture.ts';
import { groupRetailDocument } from '../ai-modeling/group-retail-model-fixture.ts';
import { defaultRetailConnectorTypeIds, optionalRetailConnectorTypeIds } from './connector-catalog.ts';
import { createFixtureConnectorAdapter, type FixtureProfile } from './fixture-adapter.ts';
import type { EvidenceBatch, SourceConnection, SourceConnectionRevision, SourceEvidenceClaim, SourceSnapshot } from './types.ts';
import { guanyijiaEvidenceSources, retailEvidenceClaims, retailEvidenceEntries, retailEvidenceSources } from '../evidence-registry/retail-evidence-registry.ts';

export type StoredSourceWorkspace = {
  schemaVersion: 1;
  workspaceId: string;
  revision: number;
  connections: SourceConnection[];
  revisions: SourceConnectionRevision[];
  snapshots: SourceSnapshot[];
  claims: SourceEvidenceClaim[];
  defaultBatch: EvidenceBatch;
  audit: Array<{ auditId: string; actorUserId: string; action: string; targetId: string; createdAt: string; detail: string }>;
};

type Template = {
  connectionId: string; connectorTypeId: string; displayName: string; environment: string;
  config: Record<string, unknown>; credentialRef?: string; scope: Record<string, unknown>;
  profile: Omit<FixtureProfile, 'connectionId' | 'connectorTypeId' | 'config'>;
};

const retailTemplates: Template[] = [
  { connectionId: 'retail_mysql', connectorTypeId: 'mysql', displayName: '零售交易库', environment: 'production', config: { endpoint: 'group-retail-core.internal', port: 3306, database: 'group_retail_core' }, credentialRef: 'secret://retail/mysql-readonly', scope: { schemas: ['retail'], tables: ['sales_*', 'inventory_*', 'return_*', 'promotion_*'] }, profile: { versionRef: 'mysql-snapshot-2026-08-01', revisionVersionRefs: { 2: 'mysql-snapshot-2026-08-09' }, summary: '42张交易表和386个字段，包含订单、库存、退货与促销结构。', objectCounts: { tables: 42, fields: 386, indexes: 73 }, evidenceIds: ['GR001', 'GR002', 'GR003'], revisionEvidenceIds: { 2: ['GR023'] }, legacySourceId: 'group-source-mysql' } },
  { connectionId: 'retail_mongodb', connectorTypeId: 'mongodb', displayName: '商品与客户画像库', environment: 'production', config: { endpoint: 'product-profile.internal', database: 'product_customer_profile' }, credentialRef: 'secret://retail/mongodb-readonly', scope: { includes: ['products', 'customer_profiles'], excludes: ['raw_*'] }, profile: { versionRef: 'mongo-snapshot-2026-08-01', summary: '商品富文本与客户画像文档，保留嵌套属性和聚合管道。', objectCounts: { collections: 6, fields: 94, pipelines: 8 }, evidenceIds: ['GR004', 'GR005', 'GR006'], legacySourceId: 'group-source-mongodb' } },
  { connectionId: 'retail_elasticsearch', connectorTypeId: 'elasticsearch', displayName: '零售搜索索引', environment: 'production', config: { endpoint: 'retail-search.internal' }, credentialRef: 'secret://retail/elasticsearch-readonly', scope: { includes: ['product-*', 'order-*'], excludes: ['.internal-*'] }, profile: { versionRef: 'es-snapshot-2026-08-01', revisionVersionRefs: { 2: 'es-snapshot-2026-08-09' }, summary: '商品与订单索引映射、查询模板和聚合行为；作为派生证据保留上游血缘。', objectCounts: { indexes: 9, mappings: 128, queries: 18 }, evidenceIds: ['GR007', 'GR008'], revisionEvidenceIds: { 2: ['GR024'] }, legacySourceId: 'group-source-elasticsearch', authority: 'DERIVED', upstreamSnapshotIds: ['retail_mysql-r1'] } },
  { connectionId: 'retail_semantica', connectorTypeId: 'semantica', displayName: '企业术语与本体图', environment: 'production', config: { endpoint: 'semantica.internal/sparql', mode: 'RDF_GRAPH' }, credentialRef: 'secret://retail/semantica-readonly', scope: { includes: ['urn:retail:ontology', 'urn:retail:vocabulary'], excludes: [] }, profile: { versionRef: 'rdf-graph-2026-08-01', summary: '企业术语、概念关系和命名图；明确采用RDF三元组模式。', objectCounts: { namedGraphs: 2, classes: 37, triples: 1248 }, evidenceIds: ['GR009', 'GR010'], legacySourceId: 'group-source-semantica', authority: 'DERIVED', upstreamSnapshotIds: ['retail_sharepoint-r1'] } },
  { connectionId: 'retail_minio', connectorTypeId: 'minio', displayName: '集团经营资料库', environment: 'production', config: { endpoint: 'minio.internal', bucket: 'retail-knowledge' }, credentialRef: 'secret://retail/minio-readonly', scope: { includes: ['policies/**', 'metrics/**', 'hierarchy/**'], excludes: ['archive/**'] }, profile: { versionRef: 'object-version-set-2026-08-01', summary: 'PDF、WPS、图片、CSV和Parquet的固定对象版本与内容指纹。', objectCounts: { objects: 28, documents: 16, datasets: 7, images: 5 }, evidenceIds: ['GR011', 'GR012', 'GR013'], legacySourceId: 'group-source-minio' } },
  { connectionId: 'retail_github', connectorTypeId: 'github', displayName: '零售分析代码仓库', environment: 'production', config: { repository: 'group-retail/analytics', ref: 'a91f2c7' }, credentialRef: 'secret://retail/github-readonly', scope: { includes: ['migrations/**', 'sql/**', 'domain/**'], excludes: ['dist/**'] }, profile: { versionRef: 'a91f2c7', summary: '固定Commit中的迁移、关联SQL、枚举和校验规则。', objectCounts: { files: 73, sqlFiles: 29, rules: 17 }, evidenceIds: ['GR014', 'GR015', 'GR016'], legacySourceId: 'group-source-github' } },
  { connectionId: 'retail_sharepoint', connectorTypeId: 'sharepoint', displayName: '集团经营知识库', environment: 'production', config: { endpoint: 'sharepoint.example', site: '集团经营知识库' }, credentialRef: 'secret://retail/sharepoint-readonly', scope: { includes: ['/制度/**', '/指标口径/**', '/术语/**'], excludes: ['/草稿/**'] }, profile: { versionRef: 'drive-delta-2026-08-01', summary: '集团制度、指标口径和术语文档，保留作者、版本和章节。', objectCounts: { documents: 46, sections: 312, terms: 185 }, evidenceIds: ['GR017', 'GR018', 'GR019'], legacySourceId: 'group-source-sharepoint' } },
  { connectionId: 'retail_kafka', connectorTypeId: 'kafka', displayName: '零售业务事件流', environment: 'production', config: { endpoint: 'kafka.internal:9092' }, credentialRef: 'secret://retail/kafka-readonly', scope: { includes: ['retail.sales.*', 'retail.inventory.*', 'retail.return.*'], excludes: ['*.dlq'] }, profile: { versionRef: 'schema-offset-2026-08-01', revisionVersionRefs: { 2: 'schema-offset-2026-08-09' }, summary: '销售、库存、退货与搜索事件Schema、分区键和确定性样例。', objectCounts: { topics: 12, schemas: 18, consumers: 9 }, evidenceIds: ['GR020', 'GR021', 'GR022'], revisionEvidenceIds: { 2: ['GR025'] }, legacySourceId: 'group-source-kafka' } },
  { connectionId: 'retail_snowflake', connectorTypeId: 'snowflake', displayName: '零售分析数仓', environment: 'production', config: { endpoint: 'retail.snowflakecomputing.com', warehouse: 'RETAIL_WH', database: 'RETAIL_ANALYTICS' }, credentialRef: 'secret://retail/snowflake-readonly', scope: { includes: ['DWH.FACT_*', 'DWH.DIM_*'], excludes: ['SANDBOX.*'] }, profile: { versionRef: 'snowflake-snapshot-2026-08-01', summary: '事实表、维表、视图、查询历史和指标计算。', objectCounts: { tables: 31, views: 14, queries: 36 }, evidenceIds: ['OPT001', 'OPT002'] } },
  { connectionId: 'retail_redis', connectorTypeId: 'redis', displayName: '零售实时缓存', environment: 'production', config: { endpoint: 'redis.internal:6379', database: 0 }, credentialRef: 'secret://retail/redis-readonly', scope: { includes: ['product:*', 'inventory:*'], excludes: ['session:*'] }, profile: { versionRef: 'redis-scan-2026-08-01', summary: 'Key模式、数据类型、TTL和访问形态，作为弱证据。', objectCounts: { keyPatterns: 8, dataTypes: 5 }, evidenceIds: ['OPT003'] } },
  { connectionId: 'retail_milvus', connectorTypeId: 'milvus', displayName: '商品语义向量库', environment: 'production', config: { endpoint: 'milvus.internal:19530' }, credentialRef: 'secret://retail/milvus-readonly', scope: { includes: ['product_embedding'], excludes: [] }, profile: { versionRef: 'milvus-snapshot-2026-08-01', summary: '向量Collection、元数据和检索样例，作为派生索引。', objectCounts: { collections: 1, dimensions: 768, indexes: 1 }, evidenceIds: ['OPT004'], authority: 'DERIVED', upstreamSnapshotIds: ['retail_mongodb-r1'] } },
  { connectionId: 'retail_cassandra', connectorTypeId: 'cassandra', displayName: '库存时序明细库', environment: 'production', config: { endpoint: 'cassandra.internal', keyspace: 'retail_timeseries' }, credentialRef: 'secret://retail/cassandra-readonly', scope: { includes: ['inventory_snapshot_by_store'], excludes: [] }, profile: { versionRef: 'cql-schema-2026-08-01', summary: '库存时间序列的分区键、聚簇键和保留策略。', objectCounts: { tables: 4, fields: 38, retentionPolicies: 2 }, evidenceIds: ['OPT005'] } },
];

for (const source of retailEvidenceSources) {
  const template = retailTemplates.find((item) => item.connectionId === source.connectionId);
  if (!template) throw new Error(`零售证据来源缺少来源中心模板：${source.connectionId}`);
  template.profile.versionRef = source.versionR1;
  template.profile.revisionVersionRefs = { 2: source.versionR2 };
  template.profile.evidenceIds = retailEvidenceEntries
    .filter((entry) => entry.connectionId === source.connectionId && entry.introducedRevision === 1)
    .map((entry) => entry.evidenceId);
  template.profile.revisionEvidenceIds = { 2: retailEvidenceEntries
    .filter((entry) => entry.connectionId === source.connectionId && entry.introducedRevision === 2)
    .map((entry) => entry.evidenceId) };
  template.profile.authority = source.authority;
  template.profile.upstreamSnapshotIds = source.upstreamConnectionIds?.map((connectionId) => `${connectionId}-snapshot-r1`);
  template.profile.claims = retailEvidenceClaims.filter((claim) => claim.sourceId === source.connectionId).map((claim) => ({
    claimId: claim.claimId, subject: claim.topic, predicate: '来源结论', value: claim.assertion,
    evidenceRefs: [...claim.evidenceRefs], authority: claim.authority,
    ...(claim.upstreamClaimIds ? { upstreamClaimIds: [...claim.upstreamClaimIds] } : {}),
  }));
}

const erpTemplates: Template[] = [
  { connectionId: 'guanyijia_mysql', connectorTypeId: 'mysql', displayName: '管伊佳 MySQL · jsh_erp', environment: 'production', config: { endpoint: 'mysql.guanyijia.internal', port: 13306, database: 'jsh_erp' }, credentialRef: 'secret://erp/guanyijia-mysql-readonly', scope: { schemas: ['jsh_erp'], tables: ['*'] }, profile: { versionRef: guanyijiaEvidenceFixture.manifest.evidenceVersion, sourceSnapshotIdentity: guanyijiaEvidenceFixture.manifest.snapshotId, sourceFingerprint: guanyijiaEvidenceFixture.source.fingerprint, sourceManifestRef: `fixture://${guanyijiaEvidenceFixture.source.fixtureKey}/manifest.json`, summary: guanyijiaEvidenceFixture.source.summary, objectCounts: structuredClone(guanyijiaEvidenceFixture.manifest.objectCounts), evidenceIds: [], legacySourceId: 'guanyijia-mysql-jsh-erp' } },
  { connectionId: 'guanyijia_github', connectorTypeId: 'github', displayName: 'GitHub · jishenghua/jshERP', environment: 'production', config: { repository: guanyijiaRepositoryEvidenceFixture.manifest.repository, ref: guanyijiaRepositoryEvidenceFixture.manifest.commit }, scope: { includes: ['jshERP-boot/**', 'sql/**'], excludes: ['target/**'] }, profile: { versionRef: guanyijiaRepositoryEvidenceFixture.manifest.commit, sourceSnapshotIdentity: guanyijiaRepositoryEvidenceFixture.manifest.snapshotId, sourceFingerprint: guanyijiaRepositoryEvidenceFixture.source.fingerprint, sourceManifestRef: `fixture://${guanyijiaRepositoryEvidenceFixture.source.fixtureKey}/manifest.json`, summary: guanyijiaRepositoryEvidenceFixture.source.summary, objectCounts: structuredClone(guanyijiaRepositoryEvidenceFixture.manifest.objectCounts), evidenceIds: [], legacySourceId: 'guanyijia-github-jsh-erp' } },
  { connectionId: 'guanyijia_sharepoint', connectorTypeId: 'sharepoint', displayName: 'SharePoint · 管伊佳业务制度', environment: 'production', config: { endpoint: 'pending', site: 'pending' }, scope: { includes: [], excludes: [] }, profile: { available: false, versionRef: 'NEEDS_CONFIRMATION', summary: guanyijiaEvidenceSources[2]!.summary, objectCounts: {}, evidenceIds: [] } },
  { connectionId: 'guanyijia_semantica', connectorTypeId: 'semantica', displayName: 'Semantica · 管伊佳术语图', environment: 'production', config: { endpoint: 'pending', mode: 'RDF_GRAPH' }, scope: { includes: [], excludes: [] }, profile: { available: false, versionRef: 'NEEDS_CONFIRMATION', summary: guanyijiaEvidenceSources[3]!.summary, objectCounts: {}, evidenceIds: [], authority: 'DERIVED' } },
];

const guanyijiaEvidenceIds = [...new Set([
  ...guanyijiaCandidateModel.tables.flatMap((table) => [
    ...table.evidenceIds,
    ...table.fields.flatMap((field) => field.evidenceIds),
  ]),
  ...guanyijiaCandidateModel.relations.flatMap((item) => item.evidenceIds),
  ...guanyijiaCandidateModel.dimensions.flatMap((item) => item.evidenceIds),
  ...guanyijiaCandidateModel.metrics.flatMap((item) => item.evidenceIds),
  ...guanyijiaCandidateModel.hierarchies.flatMap((item) => item.evidenceIds),
  ...guanyijiaCandidateModel.ruleCandidates.flatMap((item) => item.evidenceIds),
  ...guanyijiaCandidateModel.aliases.flatMap((item) => item.evidenceIds),
  ...createGuanyijiaFindings().flatMap((item) => item.evidenceIds),
])].sort();

erpTemplates[0].profile.evidenceIds = guanyijiaEvidenceIds.filter((id) => id.startsWith('mysql_jsh_erp@'));
erpTemplates[1].profile.evidenceIds = guanyijiaEvidenceIds.filter((id) => id.startsWith('github_jshERP@'));

const retailPhysicalTables = [
  ...groupRetailDocument.artifacts.model.entity_tables,
  ...groupRetailDocument.artifacts.model.event_tables,
].map((table) => ({
  schemaName: 'retail', tableName: table.table_id, label: table.name,
  fields: table.fields.map((field) => ({ name: field.field_id, label: field.name, dataType: field.data_type })),
}));
const guanyijiaPhysicalTables = guanyijiaCandidateModel.tables.map((table) => ({
  schemaName: 'jsh_erp', tableName: table.code, label: table.name,
  fields: table.fields.map((field) => ({ name: field.code, label: field.name, dataType: field.dataType })),
}));
retailTemplates.find((item) => item.connectionId === 'retail_mysql')!.profile.physicalSchema = retailPhysicalTables;
erpTemplates.find((item) => item.connectionId === 'guanyijia_mysql')!.profile.physicalSchema = guanyijiaPhysicalTables;

export const sourceFixtureProfiles: FixtureProfile[] = [...retailTemplates, ...erpTemplates].map((item) => ({ connectionId: item.connectionId, connectorTypeId: item.connectorTypeId, config: item.config, ...item.profile }));
export const sourceFixtureAdapter = createFixtureConnectorAdapter(sourceFixtureProfiles);

export async function createSeedWorkspace(workspaceId: string, now: string): Promise<StoredSourceWorkspace> {
  const templates = workspaceId === 'erp_data_governance' ? erpTemplates : retailTemplates;
  const readyIds = workspaceId === 'erp_data_governance' ? ['guanyijia_mysql', 'guanyijia_github'] : [...defaultRetailConnectorTypeIds];
  const connections: SourceConnection[] = [];
  const revisions: SourceConnectionRevision[] = [];
  const snapshots: SourceSnapshot[] = [];
  const claims: SourceEvidenceClaim[] = [];
  for (const template of templates) {
    const ready = workspaceId === 'erp_data_governance'
      ? readyIds.includes(template.connectionId)
      : readyIds.includes(template.connectorTypeId as never);
    const connection: SourceConnection = { connectionId: template.connectionId, workspaceId, connectorTypeId: template.connectorTypeId, displayName: template.displayName, environment: template.environment, activeRevision: ready ? 1 : 0, state: ready ? 'READY' : 'DRAFT' };
    const revision: SourceConnectionRevision = {
      connectionId: connection.connectionId, revision: 1, stage: ready ? 'ACTIVE' : 'DRAFT',
      sanitizedConfig: structuredClone(template.config), credentialRef: template.credentialRef,
      defaultScope: structuredClone(template.scope),
      lastTest: ready ? { status: 'PASSED', message: '已从原固定来源迁移并校验。', testedAt: now } : undefined,
      createdBy: 'system-migration', createdAt: now,
    };
    if (ready) revision.discovery = await sourceFixtureAdapter.discover({ connection, revision, now });
    connections.push(connection); revisions.push(revision);
    if (ready) {
      const captured = await sourceFixtureAdapter.capture({ connection, revision, now }, revision.defaultScope);
      const snapshotId = captured.sourceSnapshotIdentity ?? `${connection.connectionId}-snapshot-r1`;
      snapshots.push({ ...captured, snapshotId, connectionId: connection.connectionId, connectionRevision: 1, scope: structuredClone(revision.defaultScope), capturedAt: now });
      claims.push(...captured.claims.map((claim) => ({ ...claim, snapshotId, connectionRevision: 1 })));
    }
  }
  const snapshotIds = snapshots.map((item) => item.snapshotId).sort();
  const projectId = workspaceId === 'erp_data_governance' ? 'guanyijia_erp' : 'group_retail_ops';
  return {
    schemaVersion: 1, workspaceId, revision: 0, connections, revisions, snapshots, claims,
    defaultBatch: { batchId: `${projectId}-sources-r1`, projectId, revision: 1, snapshotIds, fingerprint: sha256HexSync(snapshotIds.join('\n')), state: 'FROZEN' },
    audit: [{ auditId: `${workspaceId}-migration`, actorUserId: 'system-migration', action: 'MIGRATE_LEGACY_SOURCES', targetId: workspaceId, createdAt: now, detail: `迁移 ${snapshots.length} 个不可变来源快照；旧状态键保持不变。` }],
  };
}

export function seedTemplateIds(workspaceId: string) {
  return (workspaceId === 'erp_data_governance' ? erpTemplates : retailTemplates).map((item) => item.connectionId);
}

export function sourceFixturePreset(workspaceId: string, connectorTypeId: string) {
  const templates = workspaceId === 'erp_data_governance' ? erpTemplates : retailTemplates;
  const template = templates.find((item) => item.connectorTypeId === connectorTypeId);
  return template ? {
    displayName: template.displayName,
    environment: template.environment,
    config: structuredClone(template.config),
    scope: structuredClone(template.scope),
    credentialRef: template.credentialRef,
  } : null;
}

export function isOptionalRetailConnection(connection: SourceConnection) {
  return connection.workspaceId === 'retail_semantic_modeling' && optionalRetailConnectorTypeIds.includes(connection.connectorTypeId as never);
}
