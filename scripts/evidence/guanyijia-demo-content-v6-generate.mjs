import { createHash } from 'node:crypto';
import { execFile as execFileCallback, spawn } from 'node:child_process';
import {
  cp,
  lstat,
  mkdir,
  mkdtemp,
  readFile,
  rename,
  rm,
  writeFile,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { standardSectionOrder } from '../../src/features/modeling-document-bridge/standard-markdown.ts';
import { validateDemoContentSnapshot } from './guanyijia-demo-content-snapshot.mjs';

const execFile = promisify(execFileCallback);
const moduleDirectory = dirname(fileURLToPath(import.meta.url));
const prototypeRoot = resolve(moduleDirectory, '../..');
const workspaceRoot = resolve(prototypeRoot, '..');
const schemaPath = join(moduleDirectory, 'schemas/guanyijia-demo-content-v6-review.schema.json');

export const V5_SNAPSHOT_ID = 'guanyijia-demo-content-v5-20260826';
export const V6_SNAPSHOT_ID = 'guanyijia-demo-content-v6-20260826';
const V5_CONTENT_SHA256 = 'sha256:2663cc64b19b5aa149ae0dfcea2a1917e9295ea490e0a59b422558cbcd009a2c';
const MYSQL_SNAPSHOT_ID = '20260813T032528Z-abb0502c7d79';
const defaultSourceSnapshotRoot = resolve(
  workspaceRoot,
  `modeling-evidence/guanyijia/demo-content/snapshots/${V5_SNAPSHOT_ID}`,
);
const defaultTargetSnapshotRoot = resolve(
  workspaceRoot,
  `modeling-evidence/guanyijia/demo-content/snapshots/${V6_SNAPSHOT_ID}`,
);
const defaultMysqlSnapshotRoot = resolve(
  workspaceRoot,
  `modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/${MYSQL_SNAPSHOT_ID}`,
);

const sourceOrder = Object.freeze([
  'guanyijia_mysql',
  'guanyijia_github',
  'guanyijia_official_docs',
  'guanyijia_demo_policy',
  'guanyijia_semantica_demo',
]);
const reviewPathBySource = Object.freeze({
  guanyijia_mysql: 'sources/mysql/standardized-review.json',
  guanyijia_github: 'sources/github/standardized-review.json',
  guanyijia_official_docs: 'sources/official/standardized-review.json',
  guanyijia_demo_policy: 'sources/policy/standardized-review.json',
  guanyijia_semantica_demo: 'sources/terminology/standardized-review.json',
});
const selectedTables = Object.freeze([
  'jsh_account', 'jsh_account_head', 'jsh_account_item', 'jsh_depot',
  'jsh_depot_head', 'jsh_depot_item', 'jsh_function', 'jsh_in_out_item',
  'jsh_material', 'jsh_material_attribute', 'jsh_material_category',
  'jsh_material_current_stock', 'jsh_material_extend', 'jsh_material_initial_stock',
  'jsh_material_property', 'jsh_msg', 'jsh_orga_user_rel', 'jsh_organization',
  'jsh_person', 'jsh_platform_config', 'jsh_role', 'jsh_serial_number',
  'jsh_supplier', 'jsh_sys_dict_data', 'jsh_sys_dict_type', 'jsh_system_config',
  'jsh_tenant', 'jsh_unit', 'jsh_user', 'jsh_user_business',
]);
const selectedProcedures = Object.freeze([
  'sp_rebalance_below_low_stock_requisition',
  'sp_rebalance_over_high_stock_sales',
  'sp_run_inventory_stock_rebalance',
  'sp_run_retail_return_rate_and_fact_sync',
  'sp_run_store_retail_return_coverage_and_fact_sync',
  'sp_sync_retail_out_fact_batch',
]);
const requiredDomains = Object.freeze([
  '主数据', '采购', '销售', '库存', '财务', '租户权限', '报表指标', '工作流审计',
]);
const forbiddenEnvironmentKeys = Object.freeze([
  'OPENAI_API_KEY',
  'CODEX_API_KEY',
  'CODEX_ACCESS_TOKEN',
  'OPENAI_BASE_URL',
  'AZURE_OPENAI_API_KEY',
  'AZURE_OPENAI_ENDPOINT',
]);
const sectionGuidance = Object.freeze({
  OVERVIEW: '说明资料身份、时间点、选择范围、来源属性与不能推出的结论。',
  GOAL: '说明这份来源能支持哪些业务理解或建模目标，并区分事实与候选。',
  OBJECT: '整理业务对象、对象粒度、标识及其责任范围。',
  ACTIVITY: '整理业务动作、状态变化、前后步骤与参与角色。',
  FIELD: '整理字段、维度、时间、状态、数量、金额及需要确认的语义。',
  RELATION: '整理对象间连接、上下游、基数候选以及无法由单一来源证明的关系。',
  METRIC: '只提出来源能够支持的指标候选、口径要素与阻断条件，不把查询或字段直接当作正式公式。',
  QUESTION: '给出业务人员可直接核对的示例问题，问题应能回到本来源证据。',
  UNRESOLVED: '用 GAP 或“资料缺口/待确认”明确列出未覆盖范围、口径、例外和下一步责任人。',
});
const truthfulCopy = Object.freeze({
  guanyijia_mysql: {
    title: '数据库建模审阅（冻结结构与过程）',
    coverageLabel: '30 / 95 张表 · 6 / 35 个过程',
    preface: 'SNAPSHOT_REFERENCE：只读取固定 MySQL 快照中的零业务行逻辑材料，不读取 samples、profiles、查询结果或绑定参数。',
    overview: '本次从 95 张表中选择 30 张、从 35 个过程里选择 6 个，保存其 DDL 或过程定义；2 个视图、1 个事件和未选对象不在本次标准文档结论范围内。',
    gap: 'GAP：其余 65 张表、29 个过程、2 个视图和 1 个事件未进入选择范围；结构只能证明对象与字段存在，不能证明租户实际配置、业务行分布或最终指标口径。',
  },
  guanyijia_github: {
    title: '代码仓库标准审阅',
    coverageLabel: '43 项结论 · 69 段证据 · 32 / 719 个文件 · 1003 行',
    preface: 'SOURCE_NATIVE：内容只来自固定提交的源码行段，并保留 V5 已准入结论、证据和选择范围。',
    overview: '固定仓库共有 719 个文件；本次选择 32 个文件、69 段证据、1003 个去重源码行并保留 43 项 V5 结论，未选源码不据此作出判断。',
    gap: 'GAP：剩余 687 个文件没有进入本次选择性审阅；“未选入”不表示功能不存在，源码片段也不能替代当前部署配置、业务制度或正式指标确认。',
  },
  guanyijia_official_docs: {
    title: '业务说明标准审阅（演示编写）',
    coverageLabel: '3 份演示编写资料 · 9 个来源章节',
    preface: 'DEMO_AUTHORED：这些业务说明为演示编写材料，不是官方原文，也不证明当前生产实施。',
    overview: '三份演示业务说明的九个来源章节用于补充角色、流程、字段和报表语境；每项表达都应在正式资料到位后重新核对。',
    gap: 'GAP：缺少可核验的官方原始文档、版本状态与当前实施确认；演示说明只能作为审阅语境，不能提升为独立事实。',
  },
  guanyijia_demo_policy: {
    title: 'ERP 管理制度标准审阅（演示草案）',
    coverageLabel: '5 份演示制度草案 · 15 个来源章节',
    preface: 'DEMO_AUTHORED / GENERATED_TARGET：制度内容是待审阅的目标草案，不是现行生产制度。',
    overview: '五份制度草案的十五个来源章节提出权限、采购销售、库存、财务和审核治理候选，所有要求均等待责任人确认、批准和实施。',
    gap: 'GAP：尚无审批记录、生效日期、适用组织、例外清单或实施证明；目标制度与来源事实冲突时必须保留差异并由业务负责人裁决。',
  },
  guanyijia_semantica_demo: {
    title: '企业术语图标准审阅（派生演示）',
    coverageLabel: '56 个术语 · 80 条关系 · 8 个领域',
    preface: 'DERIVED_DEMO：术语图由演示资料派生，用于统一阅读，不增加根证据数量，也不替代来源确认。',
    overview: '术语图包含 56 个术语、80 条关系和 8 个领域分组，用来组织对象、活动、指标候选与治理词汇，并保留其上游来源限制。',
    gap: 'GAP：术语定义、别名、关系方向和领域归属仍需由资料责任人确认；派生关系不能单独证明生产流程、字段实现或正式指标。',
  },
});

function fail(message) {
  throw new Error(`Demo content V6 generation blocked: ${message}`);
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function canonicalJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
}

function lineCount(value) {
  const normalized = value.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  if (!normalized) return 0;
  return normalized.endsWith('\n') ? normalized.slice(0, -1).split('\n').length : normalized.split('\n').length;
}

function exactMembers(actual, expected) {
  return actual.length === expected.length
    && new Set(actual).size === actual.length
    && actual.every((value) => expected.includes(value));
}

async function readJson(path) {
  try {
    return JSON.parse(await readFile(path, 'utf8'));
  } catch (error) {
    fail(`cannot read JSON ${path}: ${error.message}`);
  }
}

async function assertDoesNotExist(path, label) {
  try {
    await lstat(path);
    fail(`${label} already exists: ${path}`);
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
}

function artifactFor(source, path) {
  const artifact = source.artifacts.find((candidate) => candidate.path === path);
  if (!artifact) fail(`source manifest does not declare ${path}`);
  return artifact;
}

function markdownChunks(path, markdown) {
  const lines = markdown.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
  const headings = lines.flatMap((line, index) => /^##\s+(.+)$/u.test(line) ? [index] : []);
  return headings.map((start, index) => {
    let end = headings[index + 1] ?? lines.length;
    while (end > start + 1 && !lines[end - 1].trim()) end -= 1;
    const excerpt = lines.slice(start, end).join('\n');
    return {
      heading: lines[start].replace(/^##\s+/u, '').trim(),
      excerpt,
      startLine: start + 1,
      endLine: end,
    };
  });
}

async function mysqlDescriptor(source, mysqlSnapshotRoot) {
  const referencePath = join(source.root, 'sources/mysql/reference.json');
  const reference = await readJson(referencePath);
  const manifestPath = join(mysqlSnapshotRoot, 'manifest.json');
  const manifestBytes = await readFile(manifestPath);
  const manifest = JSON.parse(manifestBytes.toString('utf8'));
  if (source.snapshotId !== MYSQL_SNAPSHOT_ID || reference.snapshotId !== MYSQL_SNAPSHOT_ID
    || manifest.snapshotId !== MYSQL_SNAPSHOT_ID || sha256(manifestBytes) !== reference.manifestSha256
    || manifest.objectCounts.tables !== 95 || manifest.objectCounts.procedures !== 35) {
    fail('MySQL frozen snapshot identity or scope drifted');
  }
  const digestByPath = new Map();
  for (const file of manifest.evidenceFiles ?? []) {
    const existing = digestByPath.get(file.relativePath);
    if (existing && existing !== file.sha256) fail(`MySQL manifest has conflicting digests for ${file.relativePath}`);
    digestByPath.set(file.relativePath, file.sha256);
  }
  const definitions = [
    ...selectedTables.map((name) => ({ kind: 'table', name, path: `ddl/tables/${name}.sql` })),
    ...selectedProcedures.map((name) => ({ kind: 'procedure', name, path: `programmability/procedures/${name}.sql` })),
  ];
  const evidence = await Promise.all(definitions.map(async (definition) => {
    const excerpt = await readFile(join(mysqlSnapshotRoot, definition.path), 'utf8');
    const digest = sha256(excerpt);
    if (digest !== `sha256:${digestByPath.get(definition.path)}`) fail(`MySQL object digest drifted: ${definition.path}`);
    const isTable = definition.kind === 'table';
    const expectedSql = isTable ? `CREATE TABLE \`${definition.name}\`` : `PROCEDURE \`${definition.name}\``;
    if (!excerpt.includes(expectedSql)) fail(`MySQL object definition is missing: ${definition.name}`);
    return {
      evidenceRef: `mysql:${definition.kind}:${definition.name}`,
      evidenceClass: 'OBSERVED',
      excerptKind: 'EXACT_EXCERPT',
      title: `MySQL ${isTable ? 'DDL' : '过程'} · ${definition.name}`,
      excerpt,
      locationLabel: 'MySQL 冻结快照文件',
      locationValue: definition.path,
      excerptSha256: digest,
      artifactDigest: digest,
      affectedObjectRefs: [`${definition.kind}:${definition.name}`],
    };
  }));
  return {
    sourceId: source.sourceId,
    snapshotId: source.snapshotId,
    contentOrigin: source.contentOrigin,
    ...truthfulCopy[source.sourceId],
    coverage: { tables: [...selectedTables], procedures: [...selectedProcedures] },
    coverageRefs: evidence.map((entry) => entry.evidenceRef),
    evidence,
    sourceClaims: [],
  };
}

async function githubDescriptor(source) {
  const repository = await readJson(join(source.root, 'sources/github/repository.json'));
  const index = (await readFile(join(source.root, 'sources/github/file-index.jsonl'), 'utf8'))
    .trim().split('\n').filter(Boolean).map(JSON.parse);
  const review = await readJson(join(source.root, 'sources/github/review-claims.json'));
  const coverage = {
    trackedFileCount: repository.trackedFileCount,
    selectedFileCount: review.selectedFileCount,
    uniqueSourceLineCount: review.uniqueSourceLineCount,
    evidenceCount: review.evidence.length,
    claimCount: review.claims.length,
  };
  if (canonicalJson(coverage) !== canonicalJson({
    trackedFileCount: 719,
    selectedFileCount: 32,
    uniqueSourceLineCount: 1003,
    evidenceCount: 69,
    claimCount: 43,
  })) fail('GitHub V5 admitted coverage drifted');
  const indexByPath = new Map(index.map((entry) => [entry.path, entry]));
  const evidence = await Promise.all(review.evidence.map(async (entry) => {
    const indexed = indexByPath.get(entry.path);
    if (!indexed || indexed.sha256 !== entry.artifactDigest) fail(`GitHub evidence index drifted: ${entry.evidenceRef}`);
    const content = await readFile(join(source.root, 'sources/github/source', entry.path), 'utf8');
    const lines = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
    const excerpt = lines.slice(entry.startLine - 1, entry.endLine).join('\n');
    if (sha256(content) !== indexed.sha256 || lineCount(content) !== indexed.lineCount
      || sha256(excerpt) !== entry.excerptSha256) fail(`GitHub evidence bytes drifted: ${entry.evidenceRef}`);
    return {
      evidenceRef: entry.evidenceRef,
      evidenceClass: 'SOURCE_NATIVE',
      excerptKind: 'EXACT_EXCERPT',
      title: entry.title,
      excerpt,
      locationLabel: '固定版本源码节选',
      locationValue: `${entry.path}:L${entry.startLine}-L${entry.endLine}`,
      excerptSha256: entry.excerptSha256,
      artifactDigest: entry.artifactDigest,
      affectedObjectRefs: [],
    };
  }));
  return {
    sourceId: source.sourceId,
    snapshotId: source.snapshotId,
    contentOrigin: source.contentOrigin,
    ...truthfulCopy[source.sourceId],
    coverage,
    coverageRefs: review.claims.map((claim) => claim.claimId),
    evidence,
    sourceClaims: review.claims,
  };
}

async function authoredDescriptor(source, expectedEvidenceCount) {
  const markdownPaths = source.artifacts.map((artifact) => artifact.path)
    .filter((path) => path.endsWith('.md')).sort();
  const evidence = [];
  for (const path of markdownPaths) {
    const markdown = await readFile(join(source.root, path), 'utf8');
    const sourceArtifact = artifactFor(source, path);
    if (sha256(markdown) !== sourceArtifact.sha256) fail(`authored source digest drifted: ${path}`);
    for (const chunk of markdownChunks(path, markdown)) {
      const ordinal = evidence.length + 1;
      evidence.push({
        evidenceRef: `${source.sourceId}:document:${ordinal}`,
        evidenceClass: 'DEMO_AUTHORED',
        excerptKind: 'DOCUMENT_SECTION',
        title: chunk.heading,
        excerpt: chunk.excerpt,
        locationLabel: '演示编写资料章节',
        locationValue: `${path}:L${chunk.startLine}-L${chunk.endLine}`,
        excerptSha256: sha256(chunk.excerpt),
        artifactDigest: sourceArtifact.sha256,
        affectedObjectRefs: [],
      });
    }
  }
  if (evidence.length !== expectedEvidenceCount) fail(`${source.sourceId} source section count drifted`);
  return {
    sourceId: source.sourceId,
    snapshotId: source.snapshotId,
    contentOrigin: source.contentOrigin,
    ...truthfulCopy[source.sourceId],
    coverage: { documentCount: markdownPaths.length, sourceSectionCount: evidence.length },
    coverageRefs: evidence.map((entry) => entry.evidenceRef),
    evidence,
    sourceClaims: [],
  };
}

async function terminologyDescriptor(source) {
  const documentPath = 'sources/terminology/术语图说明.md';
  const graphPath = 'sources/terminology/graph.json';
  const document = await readFile(join(source.root, documentPath), 'utf8');
  const graphText = await readFile(join(source.root, graphPath), 'utf8');
  const graph = JSON.parse(graphText);
  const graphDomains = [...new Set(graph.terms.map((term) => term.domain))];
  if (graph.terms.length !== 56 || graph.relations.length !== 80
    || !exactMembers(graphDomains, requiredDomains)) fail('terminology V5 coverage drifted');
  const documentArtifact = artifactFor(source, documentPath);
  const graphArtifact = artifactFor(source, graphPath);
  const guideEvidence = markdownChunks(documentPath, document).map((chunk, index) => ({
    evidenceRef: `${source.sourceId}:guide:${index + 1}`,
    evidenceClass: 'DERIVED_DEMO',
    excerptKind: 'DOCUMENT_SECTION',
    title: chunk.heading,
    excerpt: chunk.excerpt,
    locationLabel: '派生术语说明章节',
    locationValue: `${documentPath}:L${chunk.startLine}-L${chunk.endLine}`,
    excerptSha256: sha256(chunk.excerpt),
    artifactDigest: documentArtifact.sha256,
    affectedObjectRefs: [],
  }));
  const termById = new Map(graph.terms.map((term) => [term.termId, term]));
  const domainEvidence = requiredDomains.map((domain, index) => {
    const terms = graph.terms.filter((term) => term.domain === domain);
    const termIds = new Set(terms.map((term) => term.termId));
    const relations = graph.relations.filter((relation) => (
      termIds.has(relation.subjectId) || termIds.has(relation.objectId)
    ));
    const excerpt = `${JSON.stringify({
      domain,
      terms,
      relations: relations.map((relation) => ({
        ...relation,
        subjectName: termById.get(relation.subjectId)?.name,
        objectName: termById.get(relation.objectId)?.name,
      })),
    }, null, 2)}\n`;
    return {
      evidenceRef: `${source.sourceId}:domain:${index + 1}`,
      evidenceClass: 'DERIVED_DEMO',
      excerptKind: 'DERIVED_GRAPH',
      title: `${domain}术语与关系`,
      excerpt,
      locationLabel: '派生术语图领域投影',
      locationValue: `${graphPath}#domain=${domain}`,
      excerptSha256: sha256(excerpt),
      artifactDigest: graphArtifact.sha256,
      affectedObjectRefs: terms.map((term) => `term:${term.termId}`),
    };
  });
  const evidence = [...guideEvidence, ...domainEvidence];
  return {
    sourceId: source.sourceId,
    snapshotId: source.snapshotId,
    contentOrigin: source.contentOrigin,
    ...truthfulCopy[source.sourceId],
    coverage: { termCount: 56, relationCount: 80, domainCount: 8 },
    coverageRefs: evidence.map((entry) => entry.evidenceRef),
    evidence,
    sourceClaims: [],
  };
}

async function loadSourceDescriptors({ sourceSnapshotRoot, mysqlSnapshotRoot, expectedSnapshotId, expectedContentSha256 }) {
  await validateDemoContentSnapshot(sourceSnapshotRoot);
  const manifest = await readJson(join(sourceSnapshotRoot, 'manifest.json'));
  if (manifest.snapshotId !== expectedSnapshotId
    || (expectedContentSha256 && manifest.contentSha256 !== expectedContentSha256)) {
    fail(`source snapshot must be the exact ${expectedSnapshotId} frozen input`);
  }
  const sourceById = new Map(manifest.sources.map((source) => [source.sourceId, { ...source, root: sourceSnapshotRoot }]));
  if (!exactMembers([...sourceById.keys()], sourceOrder)) fail('five-source manifest membership drifted');
  const [mysql, github, official, policy, terminology] = await Promise.all([
    mysqlDescriptor(sourceById.get('guanyijia_mysql'), mysqlSnapshotRoot),
    githubDescriptor(sourceById.get('guanyijia_github')),
    authoredDescriptor(sourceById.get('guanyijia_official_docs'), 9),
    authoredDescriptor(sourceById.get('guanyijia_demo_policy'), 15),
    terminologyDescriptor(sourceById.get('guanyijia_semantica_demo')),
  ]);
  return [mysql, github, official, policy, terminology];
}

export function loadV6SourceDescriptors(input = {}) {
  return loadSourceDescriptors({
    sourceSnapshotRoot: input.sourceSnapshotRoot ?? defaultSourceSnapshotRoot,
    mysqlSnapshotRoot: input.mysqlSnapshotRoot ?? defaultMysqlSnapshotRoot,
    expectedSnapshotId: V5_SNAPSHOT_ID,
    expectedContentSha256: V5_CONTENT_SHA256,
  });
}

function promptVersionFor(sourceId) {
  return `guanyijia-v6-nine-section-${sourceId}-1`;
}

function contentPrompt(descriptor) {
  const sections = standardSectionOrder.map(({ key, heading }) => ({
    section: key,
    heading,
    purpose: sectionGuidance[key],
  }));
  const sourceMaterial = {
    sourceId: descriptor.sourceId,
    snapshotId: descriptor.snapshotId,
    contentOrigin: descriptor.contentOrigin,
    coverage: descriptor.coverage,
    coverageRefs: descriptor.coverageRefs,
    evidence: descriptor.evidence,
    admittedClaims: descriptor.sourceClaims,
  };
  return [
    '你是中文 ERP 标准建模资料作者。只输出符合 JSON Schema 的 JSON，不要代码围栏、Markdown 标题或附加说明。',
    '这是单一来源、固定快照的审阅任务。不得引用其他来源，不得添加未展示的字段、流程、公式、生产配置、业务行分布或制度事实。',
    '输出必须按给定顺序包含九个 canonical section。每章写可供后续 AI 建模使用的充实中文内容：对象粒度、活动、字段与维度、关系、指标候选、问题和缺口要可区分。',
    '每个 coverageRef 必须且只能分配到一个章节，九章都至少分配一个。coverageRefs 只表示本章的资料覆盖，不允许虚构新的引用。',
    '指标章节只能写候选口径与所需要素；源码查询、DDL 字段、演示制度和派生术语都不能直接成为已确认公式。待确认章节必须明确写 GAP、资料缺口或待确认事项。',
    `来源真实性声明：${descriptor.preface}`,
    `固定标题（必须原样返回）：${descriptor.title}`,
    `九章契约：${JSON.stringify(sections)}`,
    `单一来源材料：${JSON.stringify(sourceMaterial)}`,
  ].join('\n\n');
}

function sanitizedEnvironment() {
  for (const key of forbiddenEnvironmentKeys) {
    if (process.env[key]) fail(`forbidden API credential is present: ${key}`);
  }
  const environment = { ...process.env };
  forbiddenEnvironmentKeys.forEach((key) => delete environment[key]);
  return environment;
}

async function assertChatGptLogin(environment) {
  const result = await execFile('codex', ['login', 'status'], {
    cwd: prototypeRoot,
    env: environment,
    encoding: 'utf8',
  });
  if (!`${result.stdout}\n${result.stderr}`.includes('Logged in using ChatGPT')) {
    fail('a logged-in ChatGPT Codex session is required');
  }
}

function sessionIdFromJsonLines(value) {
  for (const line of value.split('\n')) {
    try {
      const event = JSON.parse(line);
      if (event.type === 'thread.started' && typeof event.thread_id === 'string' && event.thread_id) {
        return event.thread_id;
      }
    } catch { /* Codex diagnostics are not generation metadata. */ }
  }
  fail('Codex did not return a ChatGPT session ID');
}

export function codexInvocationArgs(outputPath) {
  return [
    'exec',
    '--ephemeral',
    '--ignore-user-config',
    '-m', 'gpt-5.6-luna',
    '-c', 'model_reasoning_effort="high"',
    '-s', 'read-only',
    '--output-schema', schemaPath,
    '--output-last-message', outputPath,
    '--json',
    '-',
  ];
}

function runCodex(prompt, environment, outputPath) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn('codex', codexInvocationArgs(outputPath), {
      cwd: prototypeRoot,
      env: environment,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk.toString('utf8'); });
    child.stderr.on('data', (chunk) => { stderr += chunk.toString('utf8'); });
    child.once('error', rejectRun);
    child.once('close', (code) => code === 0
      ? resolveRun({ stdout, stderr })
      : rejectRun(new Error(`Codex V6 generation exited with ${code}: ${[stderr.trim(), stdout.trim()].filter(Boolean).join('\n')}`)));
    child.stdin.end(prompt, 'utf8');
  });
}

function createCodexSessionAdapter() {
  const environment = sanitizedEnvironment();
  return {
    async prepare() {
      await assertChatGptLogin(environment);
    },
    async generate({ prompt, outputPath }) {
      const { stdout } = await runCodex(prompt, environment, outputPath);
      return {
        sessionId: sessionIdFromJsonLines(stdout),
        rawOutput: await readFile(outputPath, 'utf8'),
      };
    },
  };
}

function normalizeProposal(value, descriptor) {
  if (!value || value.schemaVersion !== 1 || value.sourceId !== descriptor.sourceId
    || value.title !== descriptor.title || !Array.isArray(value.sections)
    || value.sections.length !== standardSectionOrder.length) fail(`${descriptor.sourceId} proposal shape is invalid`);
  const titles = new Set();
  const usedCoverage = [];
  const sections = value.sections.map((section, index) => {
    const canonical = standardSectionOrder[index];
    if (!section || section.section !== canonical.key
      || typeof section.title !== 'string' || section.title.trim().length < 4 || section.title.trim().length > 40
      || /[\r\n#]/u.test(section.title) || titles.has(section.title.trim())
      || typeof section.body !== 'string' || !Array.isArray(section.coverageRefs)
      || section.coverageRefs.length === 0) fail(`${descriptor.sourceId} ${canonical.key} section is invalid`);
    const title = section.title.trim();
    const body = section.body.trim();
    if (!body || /^#{1,6}\s/mu.test(body)
      || body.includes('<!--') || body.includes('[TRACE:')) fail(`${descriptor.sourceId} ${canonical.key} content is empty or contains transport markup`);
    if (canonical.key === 'UNRESOLVED' && !/(?:GAP|资料缺口|待确认)/u.test(body)) {
      fail(`${descriptor.sourceId} unresolved section must state a GAP`);
    }
    if (new Set(section.coverageRefs).size !== section.coverageRefs.length
      || section.coverageRefs.some((reference) => typeof reference !== 'string' || !reference)) {
      fail(`${descriptor.sourceId} ${canonical.key} coverage references are invalid`);
    }
    titles.add(title);
    usedCoverage.push(...section.coverageRefs);
    return { section: canonical.key, title, body, coverageRefs: [...section.coverageRefs] };
  });
  if (!exactMembers(usedCoverage, descriptor.coverageRefs)) {
    fail(`${descriptor.sourceId} coverage membership must be exact`);
  }
  return { schemaVersion: 1, sourceId: descriptor.sourceId, title: descriptor.title, sections };
}

function validateGenerationRecord(record, descriptor, rawOutput, sessionIds) {
  const prompt = contentPrompt(descriptor);
  if (!record || record.sourceId !== descriptor.sourceId
    || record.provider !== 'CODEX_CHATGPT_SESSION'
    || record.model !== 'gpt-5.6-luna'
    || record.reasoningEffort !== 'high'
    || typeof record.sessionId !== 'string' || !record.sessionId || sessionIds.has(record.sessionId)
    || record.promptVersion !== promptVersionFor(descriptor.sourceId)
    || record.inputDigest !== sha256(prompt)
    || record.outputDigest !== sha256(rawOutput)) fail(`${descriptor.sourceId} generation metadata is invalid`);
  sessionIds.add(record.sessionId);
  return { ...record };
}

export function validateV6Candidate(candidate, descriptors) {
  if (!candidate || candidate.schemaVersion !== 1 || candidate.snapshotId !== V6_SNAPSHOT_ID
    || candidate.sourceSnapshotId !== V5_SNAPSHOT_ID || candidate.sourceContentSha256 !== V5_CONTENT_SHA256
    || !Array.isArray(candidate.reviews) || candidate.reviews.length !== sourceOrder.length) {
    fail('candidate identity or shape is invalid');
  }
  const sessionIds = new Set();
  const reviews = candidate.reviews.map((candidateReview, index) => {
    const descriptor = descriptors[index];
    if (!candidateReview || candidateReview.sourceId !== descriptor.sourceId
      || typeof candidateReview.rawOutput !== 'string' || !candidateReview.rawOutput.trim()) {
      fail('candidate source order or output is invalid');
    }
    const generation = validateGenerationRecord(
      candidateReview.generation,
      descriptor,
      candidateReview.rawOutput,
      sessionIds,
    );
    let parsed;
    try {
      parsed = JSON.parse(candidateReview.rawOutput);
    } catch {
      fail(`${descriptor.sourceId} candidate output is not JSON`);
    }
    return { descriptor, generation, proposal: normalizeProposal(parsed, descriptor) };
  });
  return { candidate, reviews };
}

async function writeCandidate(path, candidate) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, `${JSON.stringify(candidate, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' })
    .catch((error) => error?.code === 'EEXIST' ? fail(`candidate already exists: ${path}`) : Promise.reject(error));
}

export async function generateV6Candidate(input = {}) {
  if (typeof input.candidatePath !== 'string' || !input.candidatePath) {
    fail('an explicit candidate output path is required');
  }
  const candidatePath = resolve(input.candidatePath);
  await assertDoesNotExist(candidatePath, 'candidate');
  const descriptors = await loadV6SourceDescriptors(input);
  const codexSession = input.codexSession ?? createCodexSessionAdapter();
  if (!codexSession || typeof codexSession.prepare !== 'function' || typeof codexSession.generate !== 'function') {
    fail('ChatGPT session adapter is invalid');
  }
  await codexSession.prepare();
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v6-calls-'));
  try {
    const reviews = await Promise.all(descriptors.map(async (descriptor, index) => {
      const prompt = contentPrompt(descriptor);
      const outputPath = join(temporaryRoot, `${index + 1}-${descriptor.sourceId}.json`);
      const result = await codexSession.generate({ descriptor, prompt, outputPath });
      if (!result || typeof result.sessionId !== 'string' || typeof result.rawOutput !== 'string') {
        fail(`${descriptor.sourceId} ChatGPT session returned an invalid result`);
      }
      return {
        sourceId: descriptor.sourceId,
        generation: {
          sourceId: descriptor.sourceId,
          provider: 'CODEX_CHATGPT_SESSION',
          model: 'gpt-5.6-luna',
          reasoningEffort: 'high',
          sessionId: result.sessionId,
          promptVersion: promptVersionFor(descriptor.sourceId),
          inputDigest: sha256(prompt),
          outputDigest: sha256(result.rawOutput),
        },
        rawOutput: result.rawOutput,
      };
    }));
    const candidate = {
      schemaVersion: 1,
      snapshotId: V6_SNAPSHOT_ID,
      sourceSnapshotId: V5_SNAPSHOT_ID,
      sourceContentSha256: V5_CONTENT_SHA256,
      reviews,
    };
    validateV6Candidate(candidate, descriptors);
    await writeCandidate(candidatePath, candidate);
    return candidate;
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
}

function safeAnchorPart(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/gu, '-').replace(/^-|-$/gu, '');
}

function sectionTitle(canonical) {
  return canonical.heading.replace(/^\d+\.\s*/u, '');
}

/**
 * The frozen review carries an explicit section contract.  It is deliberately
 * not inferred from Markdown headings: the reader can therefore keep the
 * same nine comparison coordinates even when a section has no object row.
 */
function standardSectionsFor(descriptor, claims) {
  return standardSectionOrder.map((canonical, sectionIndex) => {
    const sectionId = canonical.key;
    return {
      sectionId,
      heading: canonical.heading,
      purpose: sectionGuidance[sectionId],
      markdownAnchor: `v6-${safeAnchorPart(descriptor.sourceId)}-section-${sectionId.toLowerCase()}`,
      claimIds: claims
        .filter((claim) => claim.section === sectionIndex + 1)
        .map((claim) => claim.claimId),
    };
  });
}

function tableDefinition(evidence) {
  const name = /^CREATE TABLE `([^`]+)` \(/mu.exec(evidence.excerpt)?.[1];
  if (!name) fail(`MySQL table evidence is not a table definition: ${evidence.locationValue}`);
  const comment = /\) ENGINE=[\s\S]*? COMMENT='([^']*)'/mu.exec(evidence.excerpt)?.[1]?.trim();
  const columns = [];
  for (const line of evidence.excerpt.split('\n')) {
    const match = /^\s*`([^`]+)`\s+(.+?)\s*,?\s*$/u.exec(line);
    if (!match) continue;
    const [, field, definition] = match;
    const dataType = /^([^\s]+(?:\([^)]*\))?)/u.exec(definition)?.[1] ?? '';
    const fieldComment = /\bCOMMENT\s+'([^']*)'/iu.exec(definition)?.[1]?.trim();
    if (!dataType || columns.some((column) => column.field === field)) {
      fail(`MySQL table field parsing drifted: ${evidence.locationValue}`);
    }
    columns.push({ field, dataType, comment: fieldComment });
  }
  if (!columns.length) fail(`MySQL table has no readable fields: ${evidence.locationValue}`);
  return { name, comment, columns };
}

function formatFieldList(columns) {
  const preferred = ['name', 'number', 'type', 'sub_type', 'status', 'tenant_id', 'id', 'create_time', 'oper_time'];
  const ordered = [
    ...preferred.map((field) => columns.find((column) => column.field === field)).filter(Boolean),
    ...columns,
  ].filter((column, index, values) => values.findIndex((candidate) => candidate.field === column.field) === index)
    .slice(0, 6);
  return ordered.map((column) => (
    column.comment ? `${column.field}（${column.comment}）` : column.field
  ));
}

function mysqlTableSection(tableName) {
  if (/^(?:jsh_user|jsh_role|jsh_function|jsh_person|jsh_organization|jsh_orga_user_rel|jsh_tenant|jsh_user_business)$/u.test(tableName)) return 2;
  if (/^(?:jsh_depot_head|jsh_depot_item|jsh_in_out_item)$/u.test(tableName)) return 4;
  if (/^(?:jsh_account|jsh_account_head|jsh_account_item)$/u.test(tableName)) return 7;
  if (/^(?:jsh_system_config|jsh_platform_config|jsh_sys_dict_data|jsh_sys_dict_type)$/u.test(tableName)) return 5;
  if (/^(?:jsh_material_current_stock|jsh_material_initial_stock|jsh_serial_number)$/u.test(tableName)) return 6;
  return 3;
}

function mysqlTableClaim(descriptor, evidence) {
  const table = tableDefinition(evidence);
  const sectionIndex = mysqlTableSection(table.name);
  const fields = formatFieldList(table.columns);
  const titlePrefix = table.comment || '数据表';
  const title = `${titlePrefix}（${table.name}）`;
  const statement = `${table.name} 在冻结 DDL 中定义为${table.comment ? `“${table.comment}”` : '一张业务数据表'}；本次可核对 ${fields.join('、')} 等字段。字段存在能够说明结构承载范围，具体业务口径仍需结合后续来源确认。`;
  const claimId = `v6-mysql-table-${safeAnchorPart(table.name)}`;
  return {
    claimId,
    section: sectionIndex,
    sectionId: standardSectionOrder[sectionIndex - 1].key,
    sectionPurpose: sectionGuidance[standardSectionOrder[sectionIndex - 1].key],
    title,
    statement,
    focusIdentifiers: fields.map((field) => field.replace(/（.*$/u, '')),
    markdownAnchor: claimId,
    evidenceRefs: [evidence.evidenceRef],
    affectedObjectRefs: [...evidence.affectedObjectRefs],
    kind: 'OBJECT',
  };
}

function procedureDefinition(evidence) {
  const name = /PROCEDURE `([^`]+)`\s*\(/iu.exec(evidence.excerpt)?.[1];
  if (!name) fail(`MySQL procedure evidence is not a procedure definition: ${evidence.locationValue}`);
  const parameters = [...evidence.excerpt.matchAll(/^\s*(?:IN|OUT|INOUT)\s+([a-z_][a-z0-9_]*)\s+/gimu)].map((match) => match[1]);
  const tables = [...evidence.excerpt.matchAll(/\b(?:FROM|INTO|UPDATE|JOIN|TABLE)\s+`?([a-z][a-z0-9_]*)`?/gimu)]
    .map((match) => match[1])
    .filter((nameValue, index, values) => values.indexOf(nameValue) === index)
    .slice(0, 8);
  return { name, parameters, tables };
}

function mysqlProcedureClaim(evidence) {
  const procedure = procedureDefinition(evidence);
  const parameterText = procedure.parameters.length ? procedure.parameters.join('、') : '未识别到输入参数';
  const tableText = procedure.tables.length ? procedure.tables.join('、') : '过程体中的对象需打开原始 SQL 核对';
  const title = `当前部署扩展过程（${procedure.name}）`;
  const statement = `${procedure.name} 是冻结快照中保存的当前部署扩展过程；可核对输入参数 ${parameterText}，过程体涉及 ${tableText}。它说明当前部署存在该自动化入口，但不能单独证明这是产品标准流程或已经在所有租户启用。`;
  const claimId = `v6-mysql-procedure-${safeAnchorPart(procedure.name)}`;
  return {
    claimId,
    section: 4,
    sectionId: 'ACTIVITY',
    sectionPurpose: sectionGuidance.ACTIVITY,
    title,
    statement,
    focusIdentifiers: [...procedure.parameters, ...procedure.tables].slice(0, 8),
    markdownAnchor: claimId,
    evidenceRefs: [evidence.evidenceRef],
    affectedObjectRefs: [...evidence.affectedObjectRefs],
    kind: 'CODE_BEHAVIOR',
  };
}

function reviewClaim(descriptor, proposalSection, sectionIndex) {
  const section = standardSectionOrder[sectionIndex];
  const claimId = `v6-${safeAnchorPart(descriptor.sourceId)}-${section.key.toLowerCase()}`;
  return {
    claimId,
    section: sectionIndex + 1,
    sectionId: section.key,
    sectionPurpose: sectionGuidance[section.key],
    title: proposalSection.title,
    statement: proposalSection.body,
    focusIdentifiers: [],
    markdownAnchor: claimId,
    evidenceRefs: [...proposalSection.coverageRefs],
    affectedObjectRefs: [...new Set(descriptor.evidence
      .filter((evidence) => proposalSection.coverageRefs.includes(evidence.evidenceRef))
      .flatMap((evidence) => evidence.affectedObjectRefs))],
    kind: section.key === 'UNRESOLVED' ? 'GAP' : 'BUSINESS_RULE',
  };
}

function githubClaims(descriptor, proposal) {
  const assignment = new Map();
  proposal.sections.forEach((section, sectionIndex) => {
    section.coverageRefs.forEach((claimId) => assignment.set(claimId, sectionIndex));
  });
  return descriptor.sourceClaims.map((claim) => {
    const sectionIndex = assignment.get(claim.claimId);
    const section = standardSectionOrder[sectionIndex];
    if (!section) fail(`GitHub claim was not assigned: ${claim.claimId}`);
    return {
      claimId: claim.claimId,
      section: sectionIndex + 1,
      sectionId: section.key,
      sectionPurpose: sectionGuidance[section.key],
      title: claim.title,
      statement: claim.statement,
      reviewFocus: claim.reviewFocus,
      boundary: claim.boundary,
      focusIdentifiers: [...claim.focusIdentifiers],
      markdownAnchor: `v6-github-${safeAnchorPart(claim.claimId)}`,
      evidenceRefs: [...claim.evidenceRefs],
      affectedObjectRefs: [],
      kind: claim.kind,
    };
  });
}

function mysqlClaims(descriptor, proposal) {
  const narrative = proposal.sections.map((section, index) => reviewClaim(descriptor, section, index));
  const tables = descriptor.evidence
    .filter((evidence) => evidence.evidenceRef.startsWith('mysql:table:'))
    .map((evidence) => mysqlTableClaim(descriptor, evidence));
  const procedures = descriptor.evidence
    .filter((evidence) => evidence.evidenceRef.startsWith('mysql:procedure:'))
    .map((evidence) => mysqlProcedureClaim(evidence));
  if (tables.length !== selectedTables.length || procedures.length !== selectedProcedures.length) {
    fail('MySQL V6 object claim coverage drifted');
  }
  return [...narrative, ...tables, ...procedures];
}

function claimBlock(claim) {
  return [
    `### ${claim.title}`,
    '',
    claim.statement,
    ...(claim.reviewFocus ? ['', `审阅关注：${claim.reviewFocus}`] : []),
    ...(claim.boundary ? ['', `资料边界：${claim.boundary}`] : []),
  ].join('\n');
}

function buildReview(descriptor, proposal, generation) {
  const claims = descriptor.sourceId === 'guanyijia_github'
    ? githubClaims(descriptor, proposal)
    : descriptor.sourceId === 'guanyijia_mysql'
      ? mysqlClaims(descriptor, proposal)
      : proposal.sections.map((section, index) => reviewClaim(descriptor, section, index));
  const sections = {};
  for (const [sectionIndex, canonical] of standardSectionOrder.entries()) {
    const proposalSection = proposal.sections[sectionIndex];
    const sectionClaims = claims.filter((claim) => claim.section === sectionIndex + 1);
    const explanatory = [
      ...(canonical.key === 'OVERVIEW' ? [descriptor.overview] : []),
      ...(descriptor.sourceId === 'guanyijia_github'
        ? [`**章节综述：${proposalSection.title}**`, '', proposalSection.body]
        : []),
      ...(canonical.key === 'UNRESOLVED' ? [descriptor.gap] : []),
    ];
    sections[canonical.key] = [...explanatory, ...sectionClaims.flatMap((claim, index) => [
      ...(index > 0 || explanatory.length ? [''] : []),
      claimBlock(claim),
    ])].join('\n').trim();
  }
  const markdown = [
    `# ${descriptor.title}`,
    '',
    `> ${descriptor.preface}`,
    '',
    ...standardSectionOrder.flatMap(({ key, heading }) => [
      `## ${heading}`,
      '',
      sections[key],
      '',
    ]),
  ].join('\n').trimEnd();
  const traceLinks = claims.map((claim) => ({
    linePrefix: `### ${claim.title}`,
    markdownAnchor: claim.markdownAnchor,
    section: claim.section,
    sectionId: claim.sectionId,
    sectionPurpose: claim.sectionPurpose,
    claim: {
      title: claim.title,
      statement: claim.statement,
      focusIdentifiers: [...claim.focusIdentifiers],
    },
    evidenceRefs: [...claim.evidenceRefs],
  }));
  return {
    schemaVersion: 1,
    documentKind: 'SOURCE',
    sourceId: descriptor.sourceId,
    snapshotId: descriptor.snapshotId,
    contentOrigin: descriptor.contentOrigin,
    title: descriptor.title,
    coverageLabel: descriptor.coverageLabel,
    coverage: descriptor.coverage,
    standardSections: standardSectionsFor(descriptor, claims),
    sections,
    markdown,
    markdownSha256: sha256(markdown),
    evidence: descriptor.evidence,
    claims,
    traceLinks,
    proposal,
    proposalSha256: sha256(canonicalJson(proposal)),
    generationManifest: generation,
  };
}

function countOccurrences(content, value) {
  let count = 0;
  let offset = 0;
  while ((offset = content.indexOf(value, offset)) >= 0) {
    count += 1;
    offset += value.length;
  }
  return count;
}

function validateReviewShape(review, descriptor, generation) {
  const proposal = normalizeProposal(review?.proposal, descriptor);
  const expected = buildReview(descriptor, proposal, generation);
  if (canonicalJson(review) !== canonicalJson(expected)) fail(`${descriptor.sourceId} standardized review is not deterministic`);
  if (review.markdown.includes('<!--') || review.markdown.includes('[TRACE:')) {
    fail(`${descriptor.sourceId} Markdown exposes transport markers`);
  }
  const declaredSections = review.standardSections;
  if (!Array.isArray(declaredSections) || declaredSections.length !== standardSectionOrder.length
    || declaredSections.some((section, index) => (
      !section || section.sectionId !== standardSectionOrder[index].key
      || section.heading !== standardSectionOrder[index].heading
      || section.purpose !== sectionGuidance[section.sectionId]
      || typeof section.markdownAnchor !== 'string' || !section.markdownAnchor
      || !Array.isArray(section.claimIds)
      || !exactMembers(
        section.claimIds,
        review.claims.filter((claim) => claim.section === index + 1).map((claim) => claim.claimId),
      )
    ))) {
    fail(`${descriptor.sourceId} explicit nine-section contract drifted`);
  }
  if (descriptor.sourceId === 'guanyijia_mysql') {
    const tableClaims = review.claims.filter((claim) => claim.claimId.startsWith('v6-mysql-table-'));
    const procedureClaims = review.claims.filter((claim) => claim.claimId.startsWith('v6-mysql-procedure-'));
    if (tableClaims.length !== selectedTables.length || procedureClaims.length !== selectedProcedures.length
      || !exactMembers(tableClaims.map((claim) => claim.evidenceRefs[0]), selectedTables.map((name) => `mysql:table:${name}`))
      || !exactMembers(procedureClaims.map((claim) => claim.evidenceRefs[0]), selectedProcedures.map((name) => `mysql:procedure:${name}`))) {
      fail('MySQL V6 table and procedure claims are incomplete');
    }
  }
  for (const trace of review.traceLinks) {
    const claimBlockText = `${trace.linePrefix}\n\n${trace.claim.statement}`;
    if (countOccurrences(review.markdown, trace.linePrefix) !== 1
      || countOccurrences(review.markdown, claimBlockText) !== 1) {
      fail(`${descriptor.sourceId} claim-to-Markdown mapping drifted`);
    }
  }
  if (new Set(review.traceLinks.map((trace) => trace.markdownAnchor)).size !== review.traceLinks.length) {
    fail(`${descriptor.sourceId} trace anchors are duplicated`);
  }
  return review;
}

async function artifact(root, path, mediaType) {
  return { path, mediaType, sha256: sha256(await readFile(join(root, path))) };
}

async function candidateValue(input) {
  if (input.candidate) return structuredClone(input.candidate);
  if (typeof input.candidatePath !== 'string' || !input.candidatePath) fail('candidate path is required');
  return readJson(resolve(input.candidatePath));
}

export async function freezeV6Snapshot(input = {}) {
  const sourceSnapshotRoot = resolve(input.sourceSnapshotRoot ?? defaultSourceSnapshotRoot);
  const mysqlSnapshotRoot = resolve(input.mysqlSnapshotRoot ?? defaultMysqlSnapshotRoot);
  const targetSnapshotRoot = resolve(input.targetSnapshotRoot ?? defaultTargetSnapshotRoot);
  if (sourceSnapshotRoot === targetSnapshotRoot) fail('append-only target must differ from V5');
  await assertDoesNotExist(targetSnapshotRoot, 'append-only V6 snapshot');
  const descriptors = await loadV6SourceDescriptors({ sourceSnapshotRoot, mysqlSnapshotRoot });
  const validatedCandidate = validateV6Candidate(await candidateValue(input), descriptors);
  const reviews = validatedCandidate.reviews.map(({ descriptor, proposal, generation }) => (
    buildReview(descriptor, proposal, generation)
  ));
  const stage = await mkdtemp(join(dirname(targetSnapshotRoot), `.${basename(targetSnapshotRoot)}.staging-`));
  let published = false;
  try {
    await cp(sourceSnapshotRoot, stage, { recursive: true, errorOnExist: false, force: false });
    const v5Manifest = await readJson(join(stage, 'manifest.json'));
    const sources = [];
    for (const source of v5Manifest.sources) {
      const review = reviews.find((candidate) => candidate.sourceId === source.sourceId);
      const reviewPath = reviewPathBySource[source.sourceId];
      if (!review || !reviewPath || source.artifacts.some((entry) => entry.path === reviewPath)) {
        fail(`cannot append V6 review for ${source.sourceId}`);
      }
      await writeFile(join(stage, reviewPath), `${JSON.stringify(review, null, 2)}\n`, 'utf8');
      sources.push({
        ...source,
        artifacts: [...source.artifacts, await artifact(stage, reviewPath, 'application/json')],
      });
    }
    const generationRuns = reviews.map((review) => review.generationManifest);
    const unsignedManifest = {
      schemaVersion: 1,
      snapshotId: V6_SNAPSHOT_ID,
      storyKey: v5Manifest.storyKey,
      sources,
      generationRuns,
    };
    const manifest = {
      ...unsignedManifest,
      contentSha256: sha256(`${canonicalJson(unsignedManifest)}\n`),
    };
    const artifacts = sources.flatMap((source) => source.artifacts);
    await writeFile(join(stage, 'generation-manifest.json'), `${JSON.stringify({
      schemaVersion: 1,
      snapshotId: V6_SNAPSHOT_ID,
      runs: generationRuns,
    }, null, 2)}\n`, 'utf8');
    await writeFile(
      join(stage, 'checksums.sha256'),
      `${artifacts.map((entry) => `${entry.sha256.slice(7)}  ${entry.path}`).join('\n')}\n`,
      'utf8',
    );
    await writeFile(join(stage, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
    await validateV6Snapshot(stage, { mysqlSnapshotRoot });
    await rename(stage, targetSnapshotRoot);
    published = true;
    return manifest;
  } finally {
    if (!published) await rm(stage, { recursive: true, force: true });
  }
}

export async function validateV6Snapshot(root, input = {}) {
  const snapshotRoot = resolve(root);
  const mysqlSnapshotRoot = resolve(input.mysqlSnapshotRoot ?? defaultMysqlSnapshotRoot);
  const base = await validateDemoContentSnapshot(snapshotRoot);
  const manifest = await readJson(join(snapshotRoot, 'manifest.json'));
  if (manifest.snapshotId !== V6_SNAPSHOT_ID || manifest.generationRuns.length !== 5
    || !exactMembers(manifest.generationRuns.map((run) => run.sourceId), sourceOrder)) {
    fail('V6 snapshot generation membership is invalid');
  }
  const descriptors = await loadSourceDescriptors({
    sourceSnapshotRoot: snapshotRoot,
    mysqlSnapshotRoot,
    expectedSnapshotId: V6_SNAPSHOT_ID,
  });
  const generationBySource = new Map(manifest.generationRuns.map((run) => [run.sourceId, run]));
  const reviews = [];
  for (const descriptor of descriptors) {
    const reviewPath = reviewPathBySource[descriptor.sourceId];
    const source = manifest.sources.find((candidate) => candidate.sourceId === descriptor.sourceId);
    if (!source?.artifacts.some((entry) => entry.path === reviewPath)) {
      fail(`V6 source review artifact is missing: ${descriptor.sourceId}`);
    }
    reviews.push(validateReviewShape(
      await readJson(join(snapshotRoot, reviewPath)),
      descriptor,
      generationBySource.get(descriptor.sourceId),
    ));
  }
  return { ...base, reviews };
}

async function main() {
  const [command, firstPath, secondPath] = process.argv.slice(2);
  if (command === '--generate-candidate' && firstPath && !secondPath) {
    const candidate = await generateV6Candidate({ candidatePath: firstPath });
    process.stdout.write(`${JSON.stringify({ snapshotId: candidate.snapshotId, sourceCalls: candidate.reviews.length }, null, 2)}\n`);
    return;
  }
  if (command === '--check-candidate' && firstPath && !secondPath) {
    const descriptors = await loadV6SourceDescriptors();
    const result = validateV6Candidate(await readJson(resolve(firstPath)), descriptors);
    process.stdout.write(`${JSON.stringify({ snapshotId: result.candidate.snapshotId, sourceReviews: result.reviews.length }, null, 2)}\n`);
    return;
  }
  if (command === '--freeze-candidate' && firstPath) {
    const manifest = await freezeV6Snapshot({ candidatePath: firstPath, targetSnapshotRoot: secondPath });
    process.stdout.write(`${JSON.stringify({ snapshotId: manifest.snapshotId, contentSha256: manifest.contentSha256 }, null, 2)}\n`);
    return;
  }
  if (command === '--check' && firstPath && !secondPath) {
    const result = await validateV6Snapshot(firstPath);
    process.stdout.write(`${JSON.stringify({ snapshotId: result.snapshotId, sourceReviews: result.reviews.length }, null, 2)}\n`);
    return;
  }
  fail('Usage: node scripts/evidence/guanyijia-demo-content-v6-generate.mjs --generate-candidate <candidate.json> | --check-candidate <candidate.json> | --freeze-candidate <candidate.json> [target-root] | --check <snapshot-root>');
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
