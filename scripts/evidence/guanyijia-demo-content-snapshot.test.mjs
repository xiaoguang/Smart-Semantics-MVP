import assert from 'node:assert/strict';
import { execFile as execFileCallback } from 'node:child_process';
import { createHash } from 'node:crypto';
import { access, cp, mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

let snapshotModule;
let snapshotModuleError;
try {
  snapshotModule = await import('./guanyijia-demo-content-snapshot.mjs');
} catch (error) {
  snapshotModuleError = error;
}

const mysqlSnapshotId = '20260813T032528Z-abb0502c7d79';
const githubCommit = 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1';
const v6SnapshotRoot = join(
  dirname(dirname(dirname(fileURLToPath(import.meta.url)))),
  '../modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v6-20260826',
);
const requiredDomains = ['主数据', '采购', '销售', '库存', '财务', '租户权限', '报表指标', '工作流审计'];
const execFile = promisify(execFileCallback);

function sha256(value) {
  return `sha256:${createHash('sha256').update(value, 'utf8').digest('hex')}`;
}

function canonicalJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
}

async function writeJson(root, relativePath, value) {
  const target = join(root, relativePath);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

async function writeText(root, relativePath, value) {
  const target = join(root, relativePath);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, value, 'utf8');
}

async function refreshSnapshotIntegrity(root) {
  const manifest = JSON.parse(await readFile(join(root, 'manifest.json'), 'utf8'));
  const artifacts = manifest.sources.flatMap((source) => source.artifacts);
  for (const artifact of artifacts) {
    artifact.sha256 = sha256(await readFile(join(root, artifact.path), 'utf8'));
  }
  const unsignedManifest = {
    schemaVersion: manifest.schemaVersion,
    snapshotId: manifest.snapshotId,
    storyKey: manifest.storyKey,
    sources: manifest.sources,
    generationRuns: manifest.generationRuns,
    ...(Object.hasOwn(manifest, 'previousSnapshotId')
      ? { previousSnapshotId: manifest.previousSnapshotId }
      : {}),
  };
  manifest.contentSha256 = sha256(`${canonicalJson(unsignedManifest)}\n`);
  await writeJson(root, 'manifest.json', manifest);
  await writeText(root, 'checksums.sha256', `${artifacts.map((artifact) => `${artifact.sha256.slice(7)}  ${artifact.path}`).join('\n')}\n`);
}

function materialMarkdown(title, domain, sectionId) {
  return [
    `# ${title}`,
    '',
    `<!-- section-id: ${sectionId} -->`,
    '',
    `## ${domain}说明`,
    '',
    `${domain}用于演示资料中的业务说明、规则边界、例外处理和操作后果。`.repeat(32),
    '',
  ].join('\n');
}

function termGraph() {
  const terms = Array.from({ length: 48 }, (_, index) => ({
    termId: `term-${String(index + 1).padStart(2, '0')}`,
    name: `${requiredDomains[index % requiredDomains.length]}术语${index + 1}`,
    definition: `用于${requiredDomains[index % requiredDomains.length]}场景的演示术语。`,
    domain: requiredDomains[index % requiredDomains.length],
    aliases: [],
  }));
  const relations = Array.from({ length: 72 }, (_, index) => ({
    relationId: `relation-${String(index + 1).padStart(3, '0')}`,
    subjectId: terms[index % terms.length].termId,
    predicate: '关联',
    objectId: terms[(index + 1) % terms.length].termId,
    description: '演示术语之间的业务关系。',
  }));
  return { schemaVersion: 1, graphId: 'guanyijia-demo-content-v1', terms, relations };
}

async function createValidSnapshot() {
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-demo-content-'));
  const sourceFiles = {
    'sources/github/source/backend/src/main/java/com/jsh/erp/StockService.java': [
      'package com.jsh.erp;',
      'public final class StockService {',
      '  boolean enabled() { return true; }',
      '}',
      '',
    ].join('\n'),
  };
  const sourcePath = Object.keys(sourceFiles)[0];
  await writeText(root, sourcePath, sourceFiles[sourcePath]);
  const githubIndex = [{
    path: sourcePath.replace('sources/github/source/', ''),
    gitMode: '100644',
    gitBlobId: '0123456789012345678901234567890123456789',
    sha256: sha256(sourceFiles[sourcePath]),
    bytes: Buffer.byteLength(sourceFiles[sourcePath], 'utf8'),
    textEncoding: 'UTF-8',
    lineCount: 4,
  }];
  await writeText(root, 'sources/github/file-index.jsonl', `${githubIndex.map((entry) => JSON.stringify(entry)).join('\n')}\n`);
  await writeJson(root, 'sources/github/repository.json', {
    repository: 'https://github.com/jishenghua/jshERP.git',
    commit: githubCommit,
    tree: '0123456789012345678901234567890123456789',
    trackedFileCount: 1,
  });
  await writeJson(root, 'sources/mysql/reference.json', {
    snapshotId: mysqlSnapshotId,
    manifestPath: 'modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79/manifest.json',
    manifestSha256: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    objectCounts: { tables: 95, views: 2, procedures: 35, events: 1 },
    exclusions: ['business rows', 'samples/**', 'profiles/**'],
  });

  const documents = [
    ['sources/official/系统边界、基础资料与角色.md', '系统边界、基础资料与角色', '主数据', 'official.system-boundary'],
    ['sources/official/采购销售与库存业务流程.md', '采购销售与库存业务流程', '采购', 'official.flow'],
    ['sources/official/财务报表、状态与审核口径.md', '财务报表、状态与审核口径', '销售', 'official.finance-status'],
    ['sources/policy/基础资料与数据权限制度.md', '基础资料与数据权限制度', '库存', 'policy.master-data-access'],
    ['sources/policy/采购销售与退货管理制度.md', '采购销售与退货管理制度', '财务', 'policy.trade-return'],
    ['sources/policy/库存批次序列号与负库存制度.md', '库存批次序列号与负库存制度', '租户权限', 'policy.inventory'],
    ['sources/policy/财务结算欠款与账户制度.md', '财务结算欠款与账户制度', '报表指标', 'policy.finance'],
    ['sources/policy/单据审核状态与统计时点制度.md', '单据审核状态与统计时点制度', '工作流审计', 'policy.workflow'],
    ['sources/terminology/术语图说明.md', '术语图说明', '主数据', 'terminology.overview'],
  ];
  for (const [path, title, domain, sectionId] of documents) {
    await writeText(root, path, materialMarkdown(title, domain, sectionId));
  }
  const graph = termGraph();
  await writeJson(root, 'sources/terminology/graph.json', graph);

  const artifactPaths = [
    'sources/mysql/reference.json',
    'sources/github/repository.json',
    'sources/github/file-index.jsonl',
    ...documents.map(([path]) => path),
    'sources/terminology/graph.json',
  ];
  const sourceArtifacts = await Promise.all(artifactPaths.map(async (path) => {
    const value = await readFile(join(root, path), 'utf8');
    return { path, mediaType: path.endsWith('.json') ? 'application/json' : 'text/markdown', sha256: sha256(value) };
  }));
  const sources = [
    { sourceId: 'guanyijia_mysql', snapshotId: mysqlSnapshotId, contentOrigin: 'SNAPSHOT_REFERENCE', artifacts: sourceArtifacts.filter((entry) => entry.path.startsWith('sources/mysql/')) },
    { sourceId: 'guanyijia_github', snapshotId: 'github-b3ab269b05070d40', contentOrigin: 'SOURCE_NATIVE', artifacts: sourceArtifacts.filter((entry) => entry.path.startsWith('sources/github/')) },
    { sourceId: 'guanyijia_official_docs', snapshotId: 'guanyijia-demo-official-v1', contentOrigin: 'DEMO_AUTHORED', artifacts: sourceArtifacts.filter((entry) => entry.path.startsWith('sources/official/')) },
    { sourceId: 'guanyijia_demo_policy', snapshotId: 'guanyijia-demo-policy-content-v1', contentOrigin: 'DEMO_AUTHORED', artifacts: sourceArtifacts.filter((entry) => entry.path.startsWith('sources/policy/')) },
    { sourceId: 'guanyijia_semantica_demo', snapshotId: 'guanyijia-demo-terminology-v1', contentOrigin: 'DERIVED_DEMO', artifacts: sourceArtifacts.filter((entry) => entry.path.startsWith('sources/terminology/')) },
  ];
  const unsignedManifest = {
    schemaVersion: 1,
    snapshotId: 'guanyijia-demo-content-v1-20260821',
    storyKey: 'guanyijia-five-source-v1',
    sources,
    generationRuns: [{
      provider: 'CODEX_CHATGPT_SESSION', model: 'gpt-5.6-luna', reasoningEffort: 'medium',
      sessionId: 'codex-test-session', promptVersion: 'guanyijia-demo-content-v1',
      inputDigest: 'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
      outputDigest: 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
    }],
  };
  const manifest = { ...unsignedManifest, contentSha256: sha256(`${canonicalJson(unsignedManifest)}\n`) };
  await writeJson(root, 'manifest.json', manifest);
  await writeJson(root, 'generation-manifest.json', unsignedManifest.generationRuns[0]);
  await writeText(root, 'checksums.sha256', `${sourceArtifacts.map((entry) => `${entry.sha256.slice(7)}  ${entry.path}`).join('\n')}\n`);
  return { root, manifest };
}

async function createV7RichGenerationSnapshot() {
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-demo-content-v7-'));
  await cp(v6SnapshotRoot, root, { recursive: true });
  const manifest = JSON.parse(await readFile(join(root, 'manifest.json'), 'utf8'));
  manifest.snapshotId = 'guanyijia-demo-content-v7-20260827';
  manifest.previousSnapshotId = 'guanyijia-demo-content-v6-20260826';
  manifest.generationRuns = manifest.generationRuns.map((generation) => ({
    ...generation,
    promptVersion: generation.promptVersion.replace('v6', 'v7'),
  }));
  await writeJson(root, 'manifest.json', manifest);
  const generationManifest = JSON.parse(await readFile(join(root, 'generation-manifest.json'), 'utf8'));
  generationManifest.snapshotId = manifest.snapshotId;
  generationManifest.runs = manifest.generationRuns;
  await writeJson(root, 'generation-manifest.json', generationManifest);
  await refreshSnapshotIntegrity(root);
  return { root, manifest };
}

function validator() {
  if (snapshotModuleError) throw new Error(`snapshot implementation module is unavailable: ${snapshotModuleError.message}`);
  assert.equal(typeof snapshotModule.validateDemoContentSnapshot, 'function');
  return snapshotModule.validateDemoContentSnapshot;
}

function capturer() {
  if (snapshotModuleError) throw new Error(`snapshot implementation module is unavailable: ${snapshotModuleError.message}`);
  assert.equal(typeof snapshotModule.captureGitHubSourceTree, 'function');
  return snapshotModule.captureGitHubSourceTree;
}

function freezer() {
  if (snapshotModuleError) throw new Error(`snapshot implementation module is unavailable: ${snapshotModuleError.message}`);
  assert.equal(typeof snapshotModule.freezeDemoContentSnapshot, 'function');
  return snapshotModule.freezeDemoContentSnapshot;
}

async function createLocalGitRemote(root) {
  const repositoryRoot = join(root, 'origin');
  await mkdir(join(repositoryRoot, 'backend/src/main/java/com/jsh/erp'), { recursive: true });
  await writeText(repositoryRoot, 'backend/src/main/java/com/jsh/erp/StockService.java', [
    'package com.jsh.erp;',
    'public final class StockService {}',
    '',
  ].join('\n'));
  await writeText(repositoryRoot, 'README.md', '# frozen source\n');
  await execFile('git', ['init', '--initial-branch=main'], { cwd: repositoryRoot });
  await execFile('git', ['config', 'user.email', 'test@example.invalid'], { cwd: repositoryRoot });
  await execFile('git', ['config', 'user.name', 'Snapshot Test'], { cwd: repositoryRoot });
  await execFile('git', ['add', '.'], { cwd: repositoryRoot });
  await execFile('git', ['commit', '-m', 'fixture'], { cwd: repositoryRoot });
  const { stdout } = await execFile('git', ['rev-parse', 'HEAD'], { cwd: repositoryRoot });
  return { repositoryRoot, commit: stdout.trim() };
}

function generatedMaterials() {
  return {
    documents: [
      { path: 'sources/official/系统边界、基础资料与角色.md', content: materialMarkdown('系统边界、基础资料与角色', '主数据', 'official.system-boundary') },
      { path: 'sources/official/采购销售与库存业务流程.md', content: materialMarkdown('采购销售与库存业务流程', '采购', 'official.flow') },
      { path: 'sources/official/财务报表、状态与审核口径.md', content: materialMarkdown('财务报表、状态与审核口径', '销售', 'official.finance-status') },
      { path: 'sources/policy/基础资料与数据权限制度.md', content: materialMarkdown('基础资料与数据权限制度', '库存', 'policy.master-data-access') },
      { path: 'sources/policy/采购销售与退货管理制度.md', content: materialMarkdown('采购销售与退货管理制度', '财务', 'policy.trade-return') },
      { path: 'sources/policy/库存批次序列号与负库存制度.md', content: materialMarkdown('库存批次序列号与负库存制度', '租户权限', 'policy.inventory') },
      { path: 'sources/policy/财务结算欠款与账户制度.md', content: materialMarkdown('财务结算欠款与账户制度', '报表指标', 'policy.finance') },
      { path: 'sources/policy/单据审核状态与统计时点制度.md', content: materialMarkdown('单据审核状态与统计时点制度', '工作流审计', 'policy.workflow') },
      { path: 'sources/terminology/术语图说明.md', content: materialMarkdown('术语图说明', '主数据', 'terminology.overview') },
    ],
    graph: termGraph(),
  };
}

test('accepts an immutable five-source content snapshot with native GitHub files', async (t) => {
  const fixture = await createValidSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const result = await validator()(fixture.root);
  assert.equal(result.snapshotId, fixture.manifest.snapshotId);
  assert.equal(result.sources.length, 5);
  assert.equal(result.github.trackedFileCount, 1);
  assert.equal(result.markdown.documentCount, 9);
  assert.equal(result.graph.termCount, 48);
  assert.equal(result.graph.relationCount, 72);
});

test('accepts concise readable Markdown material without using document size as a quality gate', async (t) => {
  const fixture = await createValidSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const documents = [
    ['sources/official/系统边界、基础资料与角色.md', '系统边界、基础资料与角色', '主数据', 'official.system-boundary'],
    ['sources/official/采购销售与库存业务流程.md', '采购销售与库存业务流程', '采购', 'official.flow'],
    ['sources/official/财务报表、状态与审核口径.md', '财务报表、状态与审核口径', '销售', 'official.finance-status'],
    ['sources/policy/基础资料与数据权限制度.md', '基础资料与数据权限制度', '库存', 'policy.master-data-access'],
    ['sources/policy/采购销售与退货管理制度.md', '采购销售与退货管理制度', '财务', 'policy.trade-return'],
    ['sources/policy/库存批次序列号与负库存制度.md', '库存批次序列号与负库存制度', '租户权限', 'policy.inventory'],
    ['sources/policy/财务结算欠款与账户制度.md', '财务结算欠款与账户制度', '报表指标', 'policy.finance'],
    ['sources/policy/单据审核状态与统计时点制度.md', '单据审核状态与统计时点制度', '工作流审计', 'policy.workflow'],
    ['sources/terminology/术语图说明.md', '术语图说明', '主数据', 'terminology.overview'],
  ];
  for (const [path, title, domain, sectionId] of documents) {
    await writeText(fixture.root, path, [
      `# ${title}`,
      '',
      `<!-- section-id: ${sectionId} -->`,
      '',
      `## ${domain}`,
      '',
      `${domain}资料可供审阅。`,
      '',
    ].join('\n'));
  }
  await refreshSnapshotIntegrity(fixture.root);

  const result = await validator()(fixture.root);
  assert.equal(result.markdown.documentCount, 9);
  assert.equal(Object.hasOwn(result.markdown, 'hanCharacterCount'), false);
});

test('accepts a separately frozen v2 content publication generated with Luna high', async (t) => {
  const fixture = await createValidSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const manifest = JSON.parse(await readFile(join(fixture.root, 'manifest.json'), 'utf8'));
  manifest.snapshotId = 'guanyijia-demo-content-v2-20260826';
  manifest.generationRuns[0].reasoningEffort = 'high';
  manifest.generationRuns[0].promptVersion = 'guanyijia-demo-content-v2';
  await writeJson(fixture.root, 'generation-manifest.json', manifest.generationRuns[0]);
  await writeJson(fixture.root, 'manifest.json', manifest);
  await refreshSnapshotIntegrity(fixture.root);
  const result = await validator()(fixture.root);
  assert.equal(result.snapshotId, 'guanyijia-demo-content-v2-20260826');
});

test('accepts a V7 rich publication with the same five source generation records required by V6', async (t) => {
  const fixture = await createV7RichGenerationSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const result = await validator()(fixture.root);
  assert.equal(result.snapshotId, 'guanyijia-demo-content-v7-20260827');
  assert.equal(fixture.manifest.generationRuns.length, 5);
  assert.deepEqual(
    fixture.manifest.generationRuns.map((generation) => generation.sourceId),
    ['guanyijia_mysql', 'guanyijia_github', 'guanyijia_official_docs', 'guanyijia_demo_policy', 'guanyijia_semantica_demo'],
  );
});

test('rejects a V7 publication when its signed append-only lineage drifts', async (t) => {
  const fixture = await createV7RichGenerationSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const manifest = JSON.parse(await readFile(join(fixture.root, 'manifest.json'), 'utf8'));
  manifest.previousSnapshotId = 'guanyijia-demo-content-v5-20260826';
  await writeJson(fixture.root, 'manifest.json', manifest);
  await assert.rejects(() => validator()(fixture.root), /content digest/u);
});

test('rejects a rich publication when two source runs reuse one Codex session', async (t) => {
  const fixture = await createV7RichGenerationSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const manifest = JSON.parse(await readFile(join(fixture.root, 'manifest.json'), 'utf8'));
  manifest.generationRuns[1].sessionId = manifest.generationRuns[0].sessionId;
  await writeJson(fixture.root, 'manifest.json', manifest);
  const generationManifest = JSON.parse(await readFile(join(fixture.root, 'generation-manifest.json'), 'utf8'));
  generationManifest.runs = manifest.generationRuns;
  await writeJson(fixture.root, 'generation-manifest.json', generationManifest);
  await refreshSnapshotIntegrity(fixture.root);
  await assert.rejects(() => validator()(fixture.root), /distinct source sessions/u);
});

test('rejects a GitHub index entry whose hash or line count cannot be verified', async (t) => {
  const fixture = await createValidSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  await writeText(fixture.root, 'sources/github/file-index.jsonl', `${JSON.stringify({
    path: 'backend/src/main/java/com/jsh/erp/StockService.java', gitMode: '100644',
    gitBlobId: '0123456789012345678901234567890123456789', sha256: `sha256:${'0'.repeat(64)}`,
    bytes: 1, textEncoding: 'UTF-8', lineCount: 999,
  })}\n`);
  await refreshSnapshotIntegrity(fixture.root);
  await assert.rejects(() => validator()(fixture.root), /GitHub.*(hash|line)/u);
});

test('rejects a snapshot whose MySQL identity, content digest, or source set drifts', async (t) => {
  const fixture = await createValidSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const changed = structuredClone(fixture.manifest);
  changed.sources[0].snapshotId = 'wrong-snapshot';
  await writeJson(fixture.root, 'manifest.json', changed);
  await refreshSnapshotIntegrity(fixture.root);
  await assert.rejects(() => validator()(fixture.root), /MySQL.*snapshot/u);
});

test('rejects generated materials with duplicate section IDs', async (t) => {
  const fixture = await createValidSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  await writeText(fixture.root, 'sources/policy/单据审核状态与统计时点制度.md', materialMarkdown('单据审核状态与统计时点制度', '工作流审计', 'official.system-boundary'));
  await refreshSnapshotIntegrity(fixture.root);
  await assert.rejects(() => validator()(fixture.root), /section-id/u);
});

test('rejects a terminology graph with a dangling relation or an out-of-range relation count', async (t) => {
  const fixture = await createValidSnapshot();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const graph = termGraph();
  graph.relations[0].objectId = 'missing-term';
  await writeJson(fixture.root, 'sources/terminology/graph.json', graph);
  await refreshSnapshotIntegrity(fixture.root);
  await assert.rejects(() => validator()(fixture.root), /dangling/u);
});

test('captures one fixed Git commit as an exact source tree and index', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-git-capture-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const { repositoryRoot, commit } = await createLocalGitRemote(root);
  const destinationRoot = join(root, 'snapshot/sources/github');
  const result = await capturer()({ repositoryUrl: repositoryRoot, commit, destinationRoot });
  assert.equal(result.commit, commit);
  assert.equal(result.trackedFileCount, 2);
  const index = (await readFile(join(destinationRoot, 'file-index.jsonl'), 'utf8')).trim().split('\n').map(JSON.parse);
  assert.deepEqual(index.map((entry) => entry.path), [
    'backend/src/main/java/com/jsh/erp/StockService.java',
    'README.md',
  ]);
  assert.equal(await readFile(join(destinationRoot, 'source/backend/src/main/java/com/jsh/erp/StockService.java'), 'utf8'), 'package com.jsh.erp;\npublic final class StockService {}\n');
  await assert.rejects(() => access(join(destinationRoot, '.git')));
});

test('refuses an unknown Git commit without leaving a partial source tree', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-git-capture-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const { repositoryRoot } = await createLocalGitRemote(root);
  const destinationRoot = join(root, 'snapshot/sources/github');
  await assert.rejects(
    () => capturer()({ repositoryUrl: repositoryRoot, commit: '0'.repeat(40), destinationRoot }),
    /commit/u,
  );
  await assert.rejects(() => access(destinationRoot));
});

test('freezes generated materials beside a captured source tree without changing formal data', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-content-freeze-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const { repositoryRoot, commit } = await createLocalGitRemote(root);
  await capturer()({ repositoryUrl: repositoryRoot, commit, destinationRoot: join(root, 'sources/github') });
  const repository = JSON.parse(await readFile(join(root, 'sources/github/repository.json'), 'utf8'));
  repository.repository = 'https://github.com/jishenghua/jshERP.git';
  repository.commit = githubCommit;
  await writeJson(root, 'sources/github/repository.json', repository);
  const frozen = await freezer()({
    root,
    mysqlReference: {
      snapshotId: mysqlSnapshotId,
      manifestPath: 'modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79/manifest.json',
      manifestSha256: `sha256:${'a'.repeat(64)}`,
      objectCounts: { tables: 95, views: 2, procedures: 35, events: 1 },
      exclusions: ['business rows', 'samples/**', 'profiles/**'],
    },
    materials: generatedMaterials(),
    generation: {
      provider: 'CODEX_CHATGPT_SESSION', model: 'gpt-5.6-luna', reasoningEffort: 'medium',
      sessionId: 'codex-test-session', promptVersion: 'guanyijia-demo-content-v1',
      inputDigest: `sha256:${'b'.repeat(64)}`, outputDigest: `sha256:${'c'.repeat(64)}`,
    },
  });
  assert.equal(frozen.snapshotId, 'guanyijia-demo-content-v1-20260821');
  const verified = await validator()(root);
  assert.equal(verified.sources.length, 5);
  assert.equal(verified.github.trackedFileCount, 2);
});
