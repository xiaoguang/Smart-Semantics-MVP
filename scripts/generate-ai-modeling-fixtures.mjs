import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureRoot = join(root, 'src/features/ai-modeling/fixtures');
const outputFile = join(fixtureRoot, 'semantic-workbench-seed.json');
const versions = ['v1', 'v2', 'v3', 'v4'];
const files = {
  v1: '01-库存与出入库设计-v1.md',
  v2: '02-订单履约与发货设计-v2.md',
  v3: '03-维修SLA初稿-v3.md',
  v4: '04-维修SLA修正版-v4.md',
};
const modelVersions = { v1: 'V1', v2: 'V2', v3: null, v4: 'V3' };
const summaries = {
  v1: '建立库存、出入库与行政区域成员层级的基础模型。',
  v2: '新增订单履约、发货时效与客户销售区域。',
  v3: '新增维修服务，但 SLA 口径存在四项阻断。',
  v4: '补齐维修 SLA 单位、聚合粒度、统计范围和工作时间口径。',
};
const blockerIds = new Set([
  'unr_repair_sla_duration_unit',
  'unr_repair_compliance_aggregation',
  'unr_repair_sla_work_order_inclusion',
  'unr_repair_response_working_time',
]);

function text(path) { return readFileSync(path, 'utf8'); }
function json(path) { return JSON.parse(text(path)); }
function sha(value) { return createHash('sha256').update(value).digest('hex'); }
function title(markdown) { return markdown.match(/^#\s+(.+)$/m)?.[1] ?? '语义设计资料'; }
function h2Count(markdown) { return [...markdown.matchAll(/^##\s+/gm)].length; }

function groupForCode(code) {
  if (/repair|service_(outlet|site|location)/.test(code)) return 'repair';
  if (/shipment/.test(code)) return 'shipping';
  if (/order_fulfillment/.test(code)) return 'fulfillment';
  if (/inventory/.test(code)) return 'inventory';
  return 'shared';
}

const groupDefinitions = {
  shared: { id: 'shared', name: '共享主数据' },
  inventory: { id: 'inventory', name: '库存与出入库' },
  fulfillment: { id: 'fulfillment', name: '订单履约' },
  shipping: { id: 'shipping', name: '发货时效' },
  repair: { id: 'repair', name: '维修服务' },
};

function reviewProjection(model) {
  const items = [];
  for (const table of model.entity_tables) {
    const groupId = groupForCode(table.table_id);
    items.push({ id: `ENTITY:${table.table_id}`, type: 'ENTITY', objectId: table.table_id, name: table.name, target: table.table_id, disposition: 'INDEPENDENT', reason: table.definition, groupId });
    for (const field of table.fields) items.push({ id: `FIELD:${table.table_id}:${field.field_id}`, type: 'FIELD', objectId: field.field_id, name: field.name, target: `${table.name}.${field.name}`, disposition: 'GENERATED', reason: field.description, groupId });
  }
  for (const table of model.event_tables) {
    const groupId = groupForCode(table.table_id);
    items.push({ id: `EVENT:${table.table_id}`, type: 'EVENT', objectId: table.table_id, name: table.name, target: table.table_id, disposition: 'INDEPENDENT', reason: table.definition, groupId });
    for (const field of table.fields) items.push({ id: `FIELD:${table.table_id}:${field.field_id}`, type: 'FIELD', objectId: field.field_id, name: field.name, target: `${table.name}.${field.name}`, disposition: 'GENERATED', reason: field.description, groupId });
  }
  for (const relation of model.relationships) {
    const groupId = groupForCode(relation.from_table);
    items.push({ id: `RELATION:${relation.relationship_id}`, type: 'RELATION', objectId: relation.relationship_id, name: relation.name, target: `${relation.from_table}.${relation.from_field} → ${relation.to_table}.${relation.to_field}`, disposition: 'GENERATED', reason: relation.inference_reason || '根据资料中的关联字段生成。', groupId });
  }
  for (const metric of model.metrics) {
    const groupId = groupForCode(metric.event_table);
    items.push({ id: `METRIC:${metric.metric_id}`, type: 'METRIC', objectId: metric.metric_id, name: metric.name, target: metric.metric_id, disposition: 'GENERATED', reason: metric.definition, groupId });
  }
  const groups = Object.values(groupDefinitions)
    .map((group) => ({ ...group, itemIds: items.filter((item) => item.groupId === group.id).map((item) => item.id) }))
    .filter((group) => group.itemIds.length > 0);
  return { groups, items };
}

function generationNotes(analysis, model, enrichment) {
  const candidates = [...analysis.entity_candidates, ...analysis.event_candidates];
  const byId = new Map(candidates.map((candidate) => [candidate.candidate_id, candidate]));
  const tableNames = new Map([...model.entity_tables, ...model.event_tables].map((table) => [table.table_id, table.name]));
  const fieldNames = new Map([...model.entity_tables, ...model.event_tables].flatMap((table) => table.fields.map((field) => [`${table.table_id}:${field.field_id}`, `${table.name}.${field.name}`])));
  const notes = model.candidate_resolutions.map((resolution) => {
    const source = byId.get(resolution.candidate_id);
    const targetFields = (resolution.target_field_ids ?? []).map((fieldId) => fieldNames.get(`${resolution.target_table_id}:${fieldId}`) ?? fieldId);
    return {
      id: `NOTE:${resolution.candidate_id}`,
      kind: source?.candidate_id.startsWith('ENT') ? 'ENTITY' : 'EVENT',
      sourceId: resolution.candidate_id,
      sourceName: source?.name ?? resolution.candidate_id,
      disposition: resolution.resolution_kind === 'independent_table' ? 'INDEPENDENT' : 'MERGED',
      target: targetFields.length ? targetFields.join('、') : tableNames.get(resolution.target_table_id) ?? resolution.target_table_id ?? '待补充',
      reason: resolution.reason,
    };
  });
  for (const dimension of enrichment.dimensions) notes.push({
    id: `NOTE:${dimension.dimension_id}`,
    kind: 'DIMENSION',
    sourceId: dimension.dimension_id,
    sourceName: dimension.name,
    disposition: dimension.target_type === 'FIELD' ? 'MAPPED_FIELD' : 'MAPPED_ENTITY',
    target: dimension.owner_id ? `${dimension.owner_id}.${dimension.target_id}` : dimension.target_id,
    reason: dimension.definition,
  });
  return notes;
}

function artifactManifest(version, markdown, artifacts) {
  const rows = [{ path: `documents/${files[version]}`, sha256: sha(markdown) }];
  for (const [name, value] of Object.entries(artifacts)) rows.push({ path: `java/${version}/${name}`, sha256: sha(value) });
  return { generatedFrom: '本地 Java analyze → build → finalize', files: rows };
}

function adjacentDiff(previous, current) {
  if (!previous) return { added: current.items.length, modified: 0, removed: 0, inherited: 0 };
  const prior = new Map(previous.items.map((item) => [item.id, JSON.stringify({ ...item, groupId: undefined })]));
  const next = new Map(current.items.map((item) => [item.id, JSON.stringify({ ...item, groupId: undefined })]));
  let added = 0; let modified = 0; let inherited = 0;
  for (const [id, value] of next) {
    if (!prior.has(id)) added += 1;
    else if (prior.get(id) === value) inherited += 1;
    else modified += 1;
  }
  return { added, modified, inherited, removed: [...prior.keys()].filter((id) => !next.has(id)).length };
}

let previousReview = null;
const documents = versions.map((version) => {
  const markdownPath = join(fixtureRoot, 'documents', files[version]);
  const javaRoot = join(fixtureRoot, 'java', version);
  const markdown = text(markdownPath);
  const rawArtifacts = {
    'model_analysis.json': text(join(javaRoot, 'model_analysis.json')),
    'snowflake_model.json': text(join(javaRoot, 'snowflake_model.json')),
    'semantic_enrichment.json': text(join(javaRoot, 'semantic_enrichment.json')),
    'validation_report.json': text(join(javaRoot, 'validation_report.json')),
    'semantic_enrichment_validation.json': text(join(javaRoot, 'semantic_enrichment_validation.json')),
    'snowflake_schema.sql': text(join(javaRoot, 'snowflake_schema.sql')),
    'model_report.md': text(join(javaRoot, 'model_report.md')),
  };
  const analysis = JSON.parse(rawArtifacts['model_analysis.json']);
  const model = JSON.parse(rawArtifacts['snowflake_model.json']);
  const enrichment = JSON.parse(rawArtifacts['semantic_enrichment.json']);
  const validation = JSON.parse(rawArtifacts['validation_report.json']);
  const enrichmentValidation = JSON.parse(rawArtifacts['semantic_enrichment_validation.json']);
  if (!validation.valid || !enrichmentValidation.valid) throw new Error(`${version} Java 校验产物未通过`);
  const documentSha = sha(markdown);
  if (analysis.metadata.source_sha256 !== `sha256:${documentSha}` || model.metadata.source_sha256 !== `sha256:${documentSha}` || enrichment.metadata.source_sha256 !== `sha256:${documentSha}`) throw new Error(`${version} 文档 SHA 与 Java 产物不一致`);
  const blockers = version === 'v3'
    ? model.unresolved.filter((item) => blockerIds.has(item.item_id)).map((item) => ({ title: item.topic, detail: item.missing_information, impact: item.impact }))
    : [];
  if (version === 'v3' && blockers.length !== 4) throw new Error('v3 必须保留四项维修 SLA 阻断');
  if (version === 'v4' && model.unresolved.some((item) => blockerIds.has(item.item_id))) throw new Error('v4 不得保留维修 SLA 阻断');
  if (enrichment.member_hierarchies.length !== 1 || enrichment.member_hierarchies[0].levels.length !== 4
    || enrichment.member_hierarchies[0].members.length < 10) throw new Error(`${version} 行政区域成员层级不完整`);
  if (!enrichment.synonym_groups.length) throw new Error(`${version} 缺少同义词增强产物`);
  const review = reviewProjection(model);
  const diff = adjacentDiff(previousReview, review);
  previousReview = review;
  return {
    documentVersion: version,
    modelVersion: modelVersions[version],
    status: version === 'v3' ? 'NEEDS_SUPPLEMENT' : 'PUBLISHED',
    fileName: files[version], title: title(markdown), summary: summaries[version], content: markdown, sha256: documentSha,
    sectionCount: h2Count(markdown),
    qualityGate: { publishable: version !== 'v3', blockers, warnings: model.unresolved.filter((item) => !blockerIds.has(item.item_id)).map((item) => item.topic) },
    artifacts: {
      analysis,
      model,
      enrichment,
      validation: { valid: validation.valid ?? validation.status === 'VALID', errors: validation.errors ?? [], warnings: validation.warnings ?? [] },
      enrichmentValidation,
      ddl: rawArtifacts['snowflake_schema.sql'], report: rawArtifacts['model_report.md'],
      callSummary: { analyze: analysis.metadata.llm_execution, build: model.metadata.llm_execution, enrichment: enrichment.metadata.llm_execution },
    },
    generationNotes: generationNotes(analysis, model, enrichment), review, adjacentDiff: diff,
    artifactManifest: artifactManifest(version, markdown, rawArtifacts),
  };
});

const fixture = { schemaVersion: 4, system: { code: 'digital_sales_warehouse', name: '数码产品销售仓储语义模型' }, documents };
const serialized = `${JSON.stringify(fixture, null, 2)}\n`;
if (process.argv.includes('--check')) {
  if (text(outputFile) !== serialized) throw new Error(`${relative(root, outputFile)} 不是最新可复现产物`);
  const { omnichannelScenarioFixture, omnichannelSourceAssets, omnichannelSourcePack } = await import('../src/features/ai-modeling/omnichannel-fixture.ts');
  const omnichannel = omnichannelScenarioFixture.documents[0];
  if (omnichannelSourcePack.fileEntries.length !== 5 || omnichannelSourceAssets.length !== 8) throw new Error('全渠道资料包必须包含 5 个文件和 3 个连接来源');
  for (const entry of omnichannelSourcePack.fileEntries) {
    if (sha(entry.bytes) !== entry.sha256) throw new Error(`${entry.name} 原始字节 SHA 不一致`);
  }
  const omnichannelCounts = {
    entities: omnichannel.artifacts.model.entity_tables.length,
    events: omnichannel.artifacts.model.event_tables.length,
    fields: [...omnichannel.artifacts.model.entity_tables, ...omnichannel.artifacts.model.event_tables].flatMap((table) => table.fields).length,
    relations: omnichannel.artifacts.model.relationships.length,
    dimensions: omnichannel.artifacts.enrichment.dimensions.length,
    metrics: omnichannel.artifacts.model.metrics.length,
    rules: omnichannel.artifacts.enrichment.rule_candidates.length,
    reviews: omnichannel.review.items.length,
  };
  if (JSON.stringify(omnichannelCounts) !== JSON.stringify({ entities: 6, events: 4, fields: 72, relations: 13, dimensions: 9, metrics: 10, rules: 6, reviews: 120 })) throw new Error('全渠道模型计数不一致');
  if (omnichannel.review.items.filter((item) => item.reviewClass === 'AUTO').length !== 96) throw new Error('全渠道系统预审必须为 96 项');
  const guanyijiaFixture = json(join(fixtureRoot, 'guanyijia-database-evidence.json'));
  const evidenceRoot = resolve(root, '../modeling-evidence/guanyijia/database/mysql/jsh_erp');
  const latest = json(join(evidenceRoot, 'latest.json'));
  const snapshotRoot = join(evidenceRoot, 'snapshots', latest.snapshotId);
  const snapshotManifest = json(join(snapshotRoot, 'manifest.json'));
  if (JSON.stringify(guanyijiaFixture.manifest) !== JSON.stringify(snapshotManifest)) throw new Error('管伊佳前端 Fixture 与已推广快照不一致');
  const expectedCounts = { tables: 95, views: 2, columns: 1130, procedures: 35, functions: 0, triggers: 0, events: 1, indexes: 270, foreignKeys: 2, tenantTables: 58, tenantViews: 2 };
  for (const [key, value] of Object.entries(expectedCounts)) {
    if (snapshotManifest.objectCounts[key] !== value) throw new Error(`管伊佳数据库对象计数不一致：${key}`);
  }
  if (!(snapshotManifest.objectCounts.dmlDigests > 0)) throw new Error('管伊佳参数化 DML Digest 不能为空');
  const uniqueFiles = new Map(snapshotManifest.evidenceFiles.map((record) => [record.relativePath, record.sha256]));
  for (const [path, expectedSha] of uniqueFiles) {
    const absolute = join(snapshotRoot, path);
    if (!existsSync(absolute) || sha(readFileSync(absolute)) !== expectedSha) throw new Error(`管伊佳证据文件 SHA 不一致：${path}`);
  }
  const serializedEvidence = JSON.stringify(guanyijiaFixture);
  if (/172\.21\.1\.150|zhang1234/.test(serializedEvidence)) throw new Error('管伊佳 Fixture 不得包含连接信息或密码');
  const repositoryFixture = json(join(fixtureRoot, 'guanyijia-repository-evidence.json'));
  const repositoryRoot = resolve(root, '../modeling-evidence/guanyijia/repositories/github/jishenghua/jshERP');
  const repositoryLatest = json(join(repositoryRoot, 'latest.json'));
  const repositoryManifest = json(join(repositoryRoot, 'snapshots', repositoryLatest.snapshotId, 'manifest.json'));
  if (JSON.stringify(repositoryFixture.manifest) !== JSON.stringify(repositoryManifest)) throw new Error('管伊佳源码 Fixture 与已推广快照不一致');
  if (repositoryManifest.commit !== 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1' || repositoryManifest.objectCounts.tables !== 32) throw new Error('管伊佳源码 Commit 或核心表计数不一致');
  const candidate = json(join(fixtureRoot, 'guanyijia-candidate-model.json'));
  const expectedCandidateCounts = { entities: 13, events: 6, fields: 265, relations: 18, dimensions: 14, metrics: 8, hierarchies: 2, ruleCandidates: 8, pendingAssets: 63 };
  if (JSON.stringify(candidate.counts) !== JSON.stringify(expectedCandidateCounts)) throw new Error('管伊佳候选模型计数不一致');
  if (candidate.databaseSnapshotId !== latest.snapshotId || candidate.repositorySnapshotId !== repositoryLatest.snapshotId || candidate.repositoryCommit !== repositoryManifest.commit) throw new Error('管伊佳候选模型没有引用当前推广证据');
  const validEvidence = new Set([...snapshotManifest.evidenceFiles, ...repositoryManifest.evidenceFiles].map((record) => record.evidenceId));
  const candidateObjects = [...candidate.tables, ...candidate.relations, ...candidate.dimensions, ...candidate.metrics, ...candidate.hierarchies, ...candidate.ruleCandidates, ...candidate.pendingAssets];
  if (candidateObjects.some((item) => !item.evidenceIds.length || item.evidenceIds.some((id) => !validEvidence.has(id)))) throw new Error('管伊佳候选模型存在缺失或失效的证据引用');
  const {
    createGroupRetailFindings, groupRetailConnectorCatalog, groupRetailEvidenceClaims,
    groupRetailScenarioFixture, groupRetailSourceAssets,
  } = await import('../src/features/ai-modeling/group-retail-fixture.ts');
  const groupRetail = groupRetailScenarioFixture.documents[0];
  const groupRetailCounts = {
    entities: groupRetail.artifacts.model.entity_tables.length,
    events: groupRetail.artifacts.model.event_tables.length,
    fields: [...groupRetail.artifacts.model.entity_tables, ...groupRetail.artifacts.model.event_tables].flatMap((table) => table.fields).length,
    relations: groupRetail.artifacts.model.relationships.length,
    dimensions: groupRetail.artifacts.enrichment.dimensions.length,
    metrics: groupRetail.artifacts.model.metrics.length,
    hierarchies: groupRetail.artifacts.enrichment.member_hierarchies.length,
    rules: groupRetail.artifacts.enrichment.rule_candidates.length,
    reviews: groupRetail.review.items.length,
  };
  const expectedGroupRetailCounts = { entities: 7, events: 5, fields: 84, relations: 15, dimensions: 11, metrics: 12, hierarchies: 2, rules: 7, reviews: 132 };
  if (JSON.stringify(groupRetailCounts) !== JSON.stringify(expectedGroupRetailCounts)) throw new Error('集团零售模型计数不一致');
  if (groupRetailConnectorCatalog.length !== 12 || groupRetailSourceAssets.length !== 8) throw new Error('集团零售必须保留 8 个已接来源和 4 个可接入来源');
  if (groupRetail.review.items.filter((item) => item.reviewClass === 'AUTO').length !== 108) throw new Error('集团零售系统预审必须为 108 项');
  if (createGroupRetailFindings().filter((item) => item.severity === 'BLOCKER').length !== 3) throw new Error('集团零售必须保留 3 项来源冲突');
  const sourceIds = new Set(groupRetailSourceAssets.map((item) => item.sourceId));
  if (groupRetailSourceAssets.some((item) => item.upstreamSourceIds?.some((id) => !sourceIds.has(id)))) throw new Error('集团零售派生来源血缘引用不完整');
  if (groupRetailEvidenceClaims.some((claim) => !claim.evidenceRefs.length)) throw new Error('集团零售存在没有证据引用的业务结论');
  const derivedClaims = groupRetailEvidenceClaims.filter((claim) => claim.authority === 'DERIVED');
  const claimsById = new Map(groupRetailEvidenceClaims.map((claim) => [claim.claimId, claim]));
  const sourceById = new Map(groupRetailSourceAssets.map((source) => [source.sourceId, source]));
  if (!derivedClaims.length || derivedClaims.some((claim) => !claim.upstreamClaimIds?.length
    || claim.upstreamClaimIds.some((upstreamId) => {
      const upstream = claimsById.get(upstreamId);
      return !upstream || !sourceById.get(claim.sourceId)?.upstreamSourceIds?.includes(upstream.sourceId);
    }))) throw new Error('集团零售派生结论必须通过上游Claim闭合多来源血缘');
  console.log(`Fixture check passed: ${documents.map((item) => `${item.documentVersion}=${item.review.items.length}`).join(', ')}, omnichannel=120, group-retail=132, guanyijia=${snapshotManifest.evidenceFiles.length}+${repositoryManifest.evidenceFiles.length} evidence records, candidate=19 tables`);
} else {
  writeFileSync(outputFile, serialized);
  console.log(`Generated ${relative(root, outputFile)}`);
}
