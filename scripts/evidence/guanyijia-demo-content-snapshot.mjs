import { createHash } from 'node:crypto';
import { execFile as execFileCallback } from 'node:child_process';
import { lstat, mkdir, mkdtemp, readdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, relative, sep } from 'node:path';
import { promisify } from 'node:util';

const MYSQL_SNAPSHOT_ID = '20260813T032528Z-abb0502c7d79';
const GITHUB_COMMIT = 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1';
const REQUIRED_SOURCES = Object.freeze([
  ['guanyijia_mysql', 'SNAPSHOT_REFERENCE'],
  ['guanyijia_github', 'SOURCE_NATIVE'],
  ['guanyijia_official_docs', 'DEMO_AUTHORED'],
  ['guanyijia_demo_policy', 'DEMO_AUTHORED'],
  ['guanyijia_semantica_demo', 'DERIVED_DEMO'],
]);
const REQUIRED_DOMAINS = Object.freeze([
  '主数据', '采购', '销售', '库存', '财务', '租户权限', '报表指标', '工作流审计',
]);
const REQUIRED_MARKDOWN_PATHS = Object.freeze([
  'sources/official/系统边界、基础资料与角色.md',
  'sources/official/采购销售与库存业务流程.md',
  'sources/official/财务报表、状态与审核口径.md',
  'sources/policy/基础资料与数据权限制度.md',
  'sources/policy/采购销售与退货管理制度.md',
  'sources/policy/库存批次序列号与负库存制度.md',
  'sources/policy/财务结算欠款与账户制度.md',
  'sources/policy/单据审核状态与统计时点制度.md',
  'sources/terminology/术语图说明.md',
]);
const execFile = promisify(execFileCallback);
const DEMO_CONTENT_SNAPSHOT_ID = /^guanyijia-demo-content-v[1-9][0-9]*-20\d{6}$/u;
const GITHUB_REVIEW_PATH = 'sources/github/review-claims.json';
const GITHUB_REVIEW_KINDS = new Set([
  'ARCHITECTURE', 'CODE_BEHAVIOR', 'CONFIGURATION', 'SCHEMA_MIGRATION', 'QUERY', 'BUSINESS_RULE', 'GAP',
]);

function fail(message) {
  throw new Error(`Demo content snapshot validation failed: ${message}`);
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function canonicalJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
}

function isSha256(value) {
  return typeof value === 'string' && /^sha256:[a-f0-9]{64}$/.test(value);
}

function isDemoContentSnapshotId(value) {
  return typeof value === 'string' && DEMO_CONTENT_SNAPSHOT_ID.test(value);
}

function demoContentSnapshotVersion(value) {
  const match = /^guanyijia-demo-content-v(\d+)-/u.exec(value ?? '');
  return match ? Number(match[1]) : undefined;
}

function requiresGitHubReview(value) {
  const match = /^guanyijia-demo-content-v(\d+)-/u.exec(value ?? '');
  return Boolean(match && Number(match[1]) >= 5);
}

function requiresRichSourceGeneration(snapshotVersion) {
  return Number.isInteger(snapshotVersion) && snapshotVersion >= 6;
}

function signedManifestPayload(manifest) {
  return {
    schemaVersion: manifest.schemaVersion,
    snapshotId: manifest.snapshotId,
    storyKey: manifest.storyKey,
    sources: manifest.sources,
    generationRuns: manifest.generationRuns,
    ...(Object.hasOwn(manifest, 'previousSnapshotId')
      ? { previousSnapshotId: manifest.previousSnapshotId }
      : {}),
  };
}

function validateAppendOnlyLineage(manifest, snapshotVersion) {
  if (!Object.hasOwn(manifest, 'previousSnapshotId')) return;
  const previousSnapshotVersion = demoContentSnapshotVersion(manifest.previousSnapshotId);
  if (!isDemoContentSnapshotId(manifest.previousSnapshotId)
    || !Number.isInteger(previousSnapshotVersion)
    || previousSnapshotVersion >= snapshotVersion) {
    fail('previous snapshot lineage is invalid');
  }
}

function validateRichGenerationRuns(manifest, persistedGeneration) {
  const sourceIds = manifest.generationRuns.map((generation) => generation.sourceId);
  const requiredSourceIds = REQUIRED_SOURCES.map(([sourceId]) => sourceId);
  const sessionIds = manifest.generationRuns.map((generation) => generation.sessionId);
  const expectedReasoningEffort = demoContentSnapshotVersion(manifest.snapshotId) >= 7 ? 'xhigh' : 'high';
  if (manifest.generationRuns.some((generation) => generation.reasoningEffort !== expectedReasoningEffort)
    || canonicalJson(sourceIds) !== canonicalJson(requiredSourceIds)
    || new Set(sessionIds).size !== sessionIds.length
    || persistedGeneration.schemaVersion !== 1
    || persistedGeneration.snapshotId !== manifest.snapshotId
    || canonicalJson(persistedGeneration.runs) !== canonicalJson(manifest.generationRuns)) {
    fail('rich generation manifest does not match five distinct source sessions');
  }
}

function isSafeRelativePath(value) {
  return typeof value === 'string'
    && value.length > 0
    && !value.startsWith('/')
    && !value.startsWith('\\')
    && !value.split(/[\\/]/u).includes('..');
}

function lineCount(value) {
  const normalized = value.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  if (normalized.length === 0) return 0;
  return normalized.endsWith('\n') ? normalized.slice(0, -1).split('\n').length : normalized.split('\n').length;
}

function validGitFileMode(value) {
  return value === '100644' || value === '100755';
}

function asUtf8Text(bytes) {
  try {
    const text = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes);
    return Buffer.from(text, 'utf8').equals(bytes) ? text : undefined;
  } catch {
    return undefined;
  }
}

async function git(cwd, args) {
  try {
    const result = await execFile('git', args, {
      cwd,
      encoding: 'buffer',
      maxBuffer: 128 * 1024 * 1024,
    });
    return {
      stdout: Buffer.isBuffer(result.stdout) ? result.stdout : Buffer.from(result.stdout),
      stderr: Buffer.isBuffer(result.stderr) ? result.stderr : Buffer.from(result.stderr),
    };
  } catch (error) {
    const stderr = Buffer.isBuffer(error.stderr) ? error.stderr.toString('utf8').trim() : '';
    const detail = stderr ? `: ${stderr}` : '';
    throw new Error(`Git capture command failed (${args.join(' ')})${detail}`);
  }
}

async function targetDoesNotExist(target) {
  try {
    await lstat(target);
    fail(`GitHub capture target already exists: ${target}`);
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
}

function parseGitTree(bytes) {
  const records = bytes.toString('utf8').split('\0').filter(Boolean);
  return records.map((record) => {
    const tab = record.indexOf('\t');
    if (tab < 0) fail('GitHub tree entry is malformed');
    const metadata = record.slice(0, tab).split(' ');
    if (metadata.length !== 3) fail('GitHub tree metadata is malformed');
    const [gitMode, objectType, gitBlobId] = metadata;
    const path = record.slice(tab + 1);
    if (objectType !== 'blob' || !validGitFileMode(gitMode) || !/^[a-f0-9]{40}$/u.test(gitBlobId) || !isSafeRelativePath(path)) {
      fail(`GitHub tree entry is unsafe: ${path || '(empty)'}`);
    }
    return { path, gitMode, gitBlobId };
  }).sort((left, right) => left.path.localeCompare(right.path));
}

/**
 * Explicit maintenance operation. It reads exactly one Git commit and writes
 * an immutable expanded source tree; importing this module never invokes it.
 */
export async function captureGitHubSourceTree(input) {
  if (!input || typeof input.repositoryUrl !== 'string' || !input.repositoryUrl
    || typeof input.commit !== 'string' || !/^[a-f0-9]{40}$/u.test(input.commit)
    || typeof input.destinationRoot !== 'string' || !input.destinationRoot) {
    fail('GitHub capture input is invalid');
  }
  const destinationRoot = input.destinationRoot;
  await targetDoesNotExist(destinationRoot);
  const scratchRoot = await mkdtemp(join(tmpdir(), 'guanyijia-github-capture-'));
  const checkoutRoot = join(scratchRoot, 'checkout');
  const stagingRoot = join(dirname(destinationRoot), `.github-capture-${process.pid}-${Date.now()}`);
  try {
    await mkdir(dirname(destinationRoot), { recursive: true });
    await mkdir(stagingRoot, { recursive: false });
    await git(scratchRoot, ['init', '--quiet', checkoutRoot]);
    await git(checkoutRoot, ['remote', 'add', 'origin', input.repositoryUrl]);
    try {
      await git(checkoutRoot, ['fetch', '--quiet', '--no-tags', '--depth=1', 'origin', input.commit]);
    } catch {
      fail(`GitHub commit is unavailable: ${input.commit}`);
    }
    const { stdout: remoteBytes } = await git(checkoutRoot, ['remote', 'get-url', 'origin']);
    if (remoteBytes.toString('utf8').trim() !== input.repositoryUrl) fail('GitHub remote identity mismatch');
    const { stdout: commitBytes } = await git(checkoutRoot, ['rev-parse', 'FETCH_HEAD^{commit}']);
    const resolvedCommit = commitBytes.toString('utf8').trim();
    if (resolvedCommit !== input.commit) fail('GitHub commit identity mismatch');
    const { stdout: treeBytes } = await git(checkoutRoot, ['rev-parse', `${resolvedCommit}^{tree}`]);
    const tree = treeBytes.toString('utf8').trim();
    if (!/^[a-f0-9]{40}$/u.test(tree)) fail('GitHub tree identity mismatch');
    const { stdout: treeEntries } = await git(checkoutRoot, ['ls-tree', '-r', '-z', resolvedCommit]);
    const members = parseGitTree(treeEntries);
    if (members.length === 0) fail('GitHub tree contains no tracked files');
    const sourceRoot = join(stagingRoot, 'source');
    const index = [];
    for (const member of members) {
      const target = join(sourceRoot, member.path);
      await mkdir(dirname(target), { recursive: true });
      const { stdout: bytes } = await git(checkoutRoot, ['cat-file', 'blob', member.gitBlobId]);
      await writeFile(target, bytes);
      const text = asUtf8Text(bytes);
      index.push({
        ...member,
        sha256: sha256(bytes),
        bytes: bytes.byteLength,
        textEncoding: text === undefined ? 'BINARY' : 'UTF-8',
        lineCount: text === undefined ? 0 : lineCount(text),
      });
    }
    await writeFile(join(stagingRoot, 'file-index.jsonl'), `${index.map((entry) => JSON.stringify(entry)).join('\n')}\n`, 'utf8');
    const repository = {
      repository: input.repositoryUrl,
      commit: resolvedCommit,
      tree,
      trackedFileCount: index.length,
    };
    await writeFile(join(stagingRoot, 'repository.json'), `${JSON.stringify(repository, null, 2)}\n`, 'utf8');
    await rename(stagingRoot, destinationRoot);
    return { ...repository, files: index };
  } catch (error) {
    await rm(stagingRoot, { recursive: true, force: true });
    throw error;
  } finally {
    await rm(scratchRoot, { recursive: true, force: true });
  }
}

async function readJson(root, relativePath) {
  try {
    return JSON.parse(await readFile(join(root, relativePath), 'utf8'));
  } catch {
    fail(`invalid JSON artifact: ${relativePath}`);
  }
}

async function listFiles(root, relativeDirectory) {
  const absoluteDirectory = join(root, relativeDirectory);
  const result = [];
  let entries;
  try {
    entries = await readdir(absoluteDirectory, { withFileTypes: true });
  } catch {
    fail(`missing directory: ${relativeDirectory}`);
  }
  for (const entry of entries) {
    const relativePath = `${relativeDirectory}/${entry.name}`;
    const absolutePath = join(root, relativePath);
    if (entry.isSymbolicLink()) fail(`symbolic link is forbidden: ${relativePath}`);
    if (entry.isDirectory()) {
      result.push(...await listFiles(root, relativePath));
    } else if (entry.isFile()) {
      result.push(relativePath);
    } else {
      fail(`unsupported filesystem entry: ${relativePath}`);
    }
    await lstat(absolutePath);
  }
  return result.sort();
}

function requireSource(manifest, sourceId, expectedOrigin) {
  const source = manifest.sources.find((candidate) => candidate?.sourceId === sourceId);
  if (!source || source.contentOrigin !== expectedOrigin || !Array.isArray(source.artifacts)) {
    fail(`invalid source declaration: ${sourceId}`);
  }
  return source;
}

async function validateArtifactHashes(root, manifest) {
  const artifacts = manifest.sources.flatMap((source) => source.artifacts.map((artifact) => ({ ...artifact, sourceId: source.sourceId })));
  const seen = new Set();
  for (const artifact of artifacts) {
    if (!isSafeRelativePath(artifact.path) || !isSha256(artifact.sha256) || typeof artifact.mediaType !== 'string') {
      fail(`invalid artifact declaration: ${artifact.sourceId}`);
    }
    if (seen.has(artifact.path)) fail(`duplicate artifact path: ${artifact.path}`);
    seen.add(artifact.path);
    let bytes;
    try {
      bytes = await readFile(join(root, artifact.path));
    } catch {
      fail(`missing artifact: ${artifact.path}`);
    }
    if (sha256(bytes) !== artifact.sha256) fail(`artifact hash mismatch: ${artifact.path}`);
  }

  const checksumText = await readFile(join(root, 'checksums.sha256'), 'utf8').catch(() => fail('missing checksums.sha256'));
  const checksumEntries = checksumText.trim().split('\n').filter(Boolean).map((line) => {
    const match = /^([a-f0-9]{64})  (.+)$/u.exec(line);
    if (!match) fail('invalid checksum line');
    return { path: match[2], sha256: `sha256:${match[1]}` };
  });
  if (checksumEntries.length !== artifacts.length) fail('checksum membership does not match manifest artifacts');
  for (const artifact of artifacts) {
    const checksum = checksumEntries.find((entry) => entry.path === artifact.path);
    if (!checksum || checksum.sha256 !== artifact.sha256) fail(`checksum mismatch: ${artifact.path}`);
  }
}

async function validateMySqlReference(root, source) {
  if (source.snapshotId !== MYSQL_SNAPSHOT_ID) fail('MySQL snapshot identity mismatch');
  const reference = await readJson(root, 'sources/mysql/reference.json');
  if (reference.snapshotId !== MYSQL_SNAPSHOT_ID || !isSha256(reference.manifestSha256)) {
    fail('MySQL snapshot reference is invalid');
  }
  const expectedCounts = { tables: 95, views: 2, procedures: 35, events: 1 };
  if (canonicalJson(reference.objectCounts) !== canonicalJson(expectedCounts)) fail('MySQL object counts mismatch');
  if (!Array.isArray(reference.exclusions) || !['business rows', 'samples/**', 'profiles/**'].every((entry) => reference.exclusions.includes(entry))) {
    fail('MySQL exclusions are incomplete');
  }
}

async function validateGitHubSource(root, source) {
  const repository = await readJson(root, 'sources/github/repository.json');
  if (repository.repository !== 'https://github.com/jishenghua/jshERP.git' || repository.commit !== GITHUB_COMMIT) {
    fail('GitHub repository or commit mismatch');
  }
  if (!/^[a-f0-9]{40}$/u.test(repository.tree ?? '')) fail('GitHub tree is invalid');

  const indexText = await readFile(join(root, 'sources/github/file-index.jsonl'), 'utf8').catch(() => fail('missing GitHub file index'));
  const entries = indexText.trim().split('\n').filter(Boolean).map((line) => {
    try {
      return JSON.parse(line);
    } catch {
      fail('invalid GitHub file index JSON');
    }
  });
  if (!entries.length || repository.trackedFileCount !== entries.length) fail('GitHub tracked file count mismatch');
  const paths = new Set();
  for (const entry of entries) {
    if (!isSafeRelativePath(entry.path) || paths.has(entry.path)) fail('GitHub index path is invalid');
    paths.add(entry.path);
    if (!/^[a-f0-9]{40}$/u.test(entry.gitBlobId ?? '') || !validGitFileMode(entry.gitMode)) {
      fail('GitHub index Git metadata is invalid');
    }
    if (!isSha256(entry.sha256) || !Number.isSafeInteger(entry.bytes) || entry.bytes < 0
      || !Number.isSafeInteger(entry.lineCount) || entry.lineCount < 0
      || !['UTF-8', 'BINARY'].includes(entry.textEncoding)) fail('GitHub index entry is invalid');
    const relativePath = `sources/github/source/${entry.path}`;
    let bytes;
    try {
      bytes = await readFile(join(root, relativePath));
    } catch {
      fail(`GitHub source file is missing: ${entry.path}`);
    }
    if (sha256(bytes) !== entry.sha256 || bytes.byteLength !== entry.bytes) fail(`GitHub file hash mismatch: ${entry.path}`);
    if (entry.textEncoding === 'UTF-8') {
      const text = bytes.toString('utf8');
      if (Buffer.from(text, 'utf8').compare(bytes) !== 0 || lineCount(text) !== entry.lineCount) {
        fail(`GitHub file line count mismatch: ${entry.path}`);
      }
    } else if (entry.lineCount !== 0) {
      fail(`GitHub binary file has line count: ${entry.path}`);
    }
  }
  const sourceFiles = (await listFiles(root, 'sources/github/source')).map((path) => path.replace(`sources/github/source${sep}`, '').replace('sources/github/source/', ''));
  if (canonicalJson(sourceFiles) !== canonicalJson([...paths].sort())) fail('GitHub index membership mismatch');
  if (source.snapshotId !== 'github-b3ab269b05070d40') fail('GitHub snapshot identity mismatch');
  return { trackedFileCount: entries.length, entries };
}

async function validateGitHubReview(root, indexEntries) {
  const review = await readJson(root, GITHUB_REVIEW_PATH);
  if (review.schemaVersion !== 1 || typeof review.selectionVersion !== 'string'
    || !Number.isInteger(review.selectedFileCount) || !Number.isInteger(review.uniqueSourceLineCount)
    || !Array.isArray(review.evidence) || !Array.isArray(review.claims)
    || review.evidence.length < 60 || review.evidence.length > 90
    || review.claims.length < 40 || review.claims.length > 60
    || review.selectedFileCount < 25 || review.selectedFileCount > 40
    || review.uniqueSourceLineCount < 500) {
    fail('GitHub V5 review shape or coverage is invalid');
  }
  const sourceIndex = new Map(indexEntries.map((entry) => [entry.path, entry]));
  const evidenceIds = new Set();
  const selectedFiles = new Set();
  const observedLineSets = new Map();
  for (const entry of review.evidence) {
    const source = sourceIndex.get(entry?.path);
    if (!entry || typeof entry.evidenceRef !== 'string' || evidenceIds.has(entry.evidenceRef)
      || !Number.isInteger(entry.section) || entry.section < 1 || entry.section > 9
      || !GITHUB_REVIEW_KINDS.has(entry.kind) || typeof entry.title !== 'string' || !entry.title
      || !source || source.textEncoding !== 'UTF-8'
      || !Number.isInteger(entry.startLine) || !Number.isInteger(entry.endLine)
      || entry.startLine < 1 || entry.endLine < entry.startLine || entry.endLine > source.lineCount
      || entry.artifactDigest !== source.sha256 || !isSha256(entry.excerptSha256)) {
      fail('GitHub V5 review evidence is invalid');
    }
    const content = await readFile(join(root, 'sources/github/source', entry.path), 'utf8');
    const lines = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
    const excerpt = lines.slice(entry.startLine - 1, entry.endLine).join('\n');
    if (sha256(excerpt) !== entry.excerptSha256) fail(`GitHub V5 review excerpt mismatch: ${entry.path}`);
    evidenceIds.add(entry.evidenceRef);
    selectedFiles.add(entry.path);
    const numbers = observedLineSets.get(entry.path) ?? new Set();
    for (let line = entry.startLine; line <= entry.endLine; line += 1) numbers.add(line);
    observedLineSets.set(entry.path, numbers);
  }
  const sourceLineCount = [...observedLineSets.values()].reduce((total, lines) => total + lines.size, 0);
  if (selectedFiles.size !== review.selectedFileCount || sourceLineCount !== review.uniqueSourceLineCount) {
    fail('GitHub V5 review coverage digest is invalid');
  }
  const claimIds = new Set();
  const usedEvidence = new Set();
  for (const claim of review.claims) {
    if (!claim || typeof claim.claimId !== 'string' || claimIds.has(claim.claimId)
      || !Number.isInteger(claim.section) || claim.section < 1 || claim.section > 9
      || !GITHUB_REVIEW_KINDS.has(claim.kind) || typeof claim.title !== 'string' || !claim.title
      || typeof claim.statement !== 'string' || !claim.statement
      || typeof claim.reviewFocus !== 'string' || !claim.reviewFocus
      || typeof claim.boundary !== 'string' || !claim.boundary
      || !Array.isArray(claim.evidenceRefs) || claim.evidenceRefs.length < 1 || claim.evidenceRefs.length > 3
      || !Array.isArray(claim.focusIdentifiers)) fail('GitHub V5 review claim is invalid');
    claimIds.add(claim.claimId);
    for (const evidenceRef of claim.evidenceRefs) {
      if (!evidenceIds.has(evidenceRef)) fail('GitHub V5 review claim references unknown evidence');
      usedEvidence.add(evidenceRef);
    }
  }
  if (usedEvidence.size !== evidenceIds.size) fail('GitHub V5 review leaves evidence unused');
  return { selectedFileCount: selectedFiles.size, uniqueSourceLineCount: sourceLineCount, evidenceCount: evidenceIds.size, claimCount: claimIds.size };
}

async function validateMarkdown(root) {
  const markdownFiles = [];
  for (const relativePath of REQUIRED_MARKDOWN_PATHS) {
    const content = await readFile(join(root, relativePath), 'utf8').catch(() => fail(`missing Markdown artifact: ${relativePath}`));
    markdownFiles.push({ path: relativePath, content });
  }
  const actualMarkdownPaths = [
    ...(await listFiles(root, 'sources/official')).filter((path) => path.endsWith('.md')),
    ...(await listFiles(root, 'sources/policy')).filter((path) => path.endsWith('.md')),
    ...(await listFiles(root, 'sources/terminology')).filter((path) => path.endsWith('.md')),
  ].sort();
  if (canonicalJson(actualMarkdownPaths) !== canonicalJson([...REQUIRED_MARKDOWN_PATHS].sort())) fail('Markdown artifact membership mismatch');
  const sectionIds = new Set();
  const combined = markdownFiles.map((document) => document.content).join('\n');
  for (const document of markdownFiles) {
    if (!document.content.startsWith('# ')) fail(`Markdown title is missing: ${document.path}`);
    const ids = [...document.content.matchAll(/<!--\s*section-id:\s*([a-z0-9.-]+)\s*-->/gu)].map((match) => match[1]);
    if (ids.length === 0) fail(`Markdown section-id is missing: ${document.path}`);
    for (const sectionId of ids) {
      if (sectionIds.has(sectionId)) fail(`duplicate section-id: ${sectionId}`);
      sectionIds.add(sectionId);
    }
  }
  for (const domain of REQUIRED_DOMAINS) {
    if (!combined.includes(domain)) fail(`Markdown domain coverage is missing: ${domain}`);
  }
  return { documentCount: markdownFiles.length };
}

async function validateTermGraph(root) {
  const graph = await readJson(root, 'sources/terminology/graph.json');
  if (graph.schemaVersion !== 1 || typeof graph.graphId !== 'string' || !Array.isArray(graph.terms) || !Array.isArray(graph.relations)) {
    fail('terminology graph shape is invalid');
  }
  if (graph.terms.length < 48 || graph.terms.length > 72) fail('terminology graph term count is out of range');
  if (graph.relations.length < 72 || graph.relations.length > 120) fail('terminology graph relation count is out of range');
  const termIds = new Set();
  const graphDomains = new Set();
  for (const term of graph.terms) {
    if (typeof term.termId !== 'string' || !term.termId || termIds.has(term.termId)
      || typeof term.name !== 'string' || typeof term.definition !== 'string'
      || typeof term.domain !== 'string' || !Array.isArray(term.aliases)) fail('terminology term is invalid');
    termIds.add(term.termId);
    graphDomains.add(term.domain);
  }
  for (const domain of REQUIRED_DOMAINS) {
    if (!graphDomains.has(domain)) fail(`terminology graph domain coverage is missing: ${domain}`);
  }
  const relationIds = new Set();
  for (const relation of graph.relations) {
    if (typeof relation.relationId !== 'string' || !relation.relationId || relationIds.has(relation.relationId)
      || typeof relation.predicate !== 'string' || !relation.predicate
      || typeof relation.description !== 'string' || !relation.description) fail('terminology relation is invalid');
    relationIds.add(relation.relationId);
    if (!termIds.has(relation.subjectId) || !termIds.has(relation.objectId)) fail('dangling terminology relation');
  }
  return { termCount: graph.terms.length, relationCount: graph.relations.length };
}

function validateGeneratedMaterials(materials) {
  if (!materials || !Array.isArray(materials.documents) || !materials.graph || typeof materials.graph !== 'object') {
    fail('generated materials are invalid');
  }
  const documentPaths = materials.documents.map((document) => document?.path).sort();
  if (canonicalJson(documentPaths) !== canonicalJson([...REQUIRED_MARKDOWN_PATHS].sort())) {
    fail('generated Markdown paths are invalid');
  }
  for (const document of materials.documents) {
    if (!isSafeRelativePath(document.path) || typeof document.content !== 'string' || !document.content.trim()) {
      fail('generated Markdown content is invalid');
    }
  }
}

function validateGeneration(generation) {
  if (!generation || generation.provider !== 'CODEX_CHATGPT_SESSION'
    || generation.model !== 'gpt-5.6-luna' || !['medium', 'high', 'xhigh'].includes(generation.reasoningEffort)
    || typeof generation.sessionId !== 'string' || !generation.sessionId
    || typeof generation.promptVersion !== 'string' || !generation.promptVersion
    || !isSha256(generation.inputDigest) || !isSha256(generation.outputDigest)) {
    fail('generation metadata is invalid');
  }
}

async function artifact(root, path, mediaType) {
  const bytes = await readFile(join(root, path));
  return { path, mediaType, sha256: sha256(bytes) };
}

/**
 * Explicit maintenance operation. It freezes generated demonstration material
 * alongside an already captured Git source tree. It cannot write formal V1
 * state because it owns only the independent content-sidecar directory.
 */
export async function freezeDemoContentSnapshot(input) {
  if (!input || typeof input.root !== 'string' || !input.root || !input.mysqlReference
    || !input.materials || !input.generation) fail('freeze input is invalid');
  validateGeneratedMaterials(input.materials);
  validateGeneration(input.generation);
  const snapshotId = input.snapshotId ?? 'guanyijia-demo-content-v1-20260821';
  if (!isDemoContentSnapshotId(snapshotId)) fail('freeze snapshot identity is invalid');
  const requiresReview = requiresGitHubReview(snapshotId);
  const mysqlReference = input.mysqlReference;
  if (mysqlReference.snapshotId !== MYSQL_SNAPSHOT_ID || !isSha256(mysqlReference.manifestSha256)
    || canonicalJson(mysqlReference.objectCounts) !== canonicalJson({ tables: 95, views: 2, procedures: 35, events: 1 })
    || !Array.isArray(mysqlReference.exclusions)) fail('freeze MySQL reference is invalid');
  for (const existingPath of ['manifest.json', 'generation-manifest.json', 'checksums.sha256']) {
    try {
      await lstat(join(input.root, existingPath));
      fail(`snapshot already frozen: ${existingPath}`);
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }
  }
  for (const requiredGitPath of ['sources/github/repository.json', 'sources/github/file-index.jsonl', 'sources/github/source']) {
    try {
      await lstat(join(input.root, requiredGitPath));
    } catch {
      fail(`captured GitHub source is missing: ${requiredGitPath}`);
    }
  }

  await mkdir(join(input.root, 'sources/mysql'), { recursive: true });
  await writeFile(join(input.root, 'sources/mysql/reference.json'), `${JSON.stringify(mysqlReference, null, 2)}\n`, 'utf8');
  for (const document of input.materials.documents) {
    await mkdir(dirname(join(input.root, document.path)), { recursive: true });
    await writeFile(join(input.root, document.path), document.content.replace(/\r\n/g, '\n'), 'utf8');
  }
  await mkdir(join(input.root, 'sources/terminology'), { recursive: true });
  await writeFile(join(input.root, 'sources/terminology/graph.json'), `${JSON.stringify(input.materials.graph, null, 2)}\n`, 'utf8');
  if (requiresReview) {
    if (!input.githubReview || !Array.isArray(input.githubReview.evidence) || !Array.isArray(input.githubReview.claims)) {
      fail('GitHub V5 review is required for this snapshot');
    }
    const persistedReview = {
      ...input.githubReview,
      evidence: input.githubReview.evidence.map(({ excerpt, ...entry }) => entry),
    };
    await writeFile(join(input.root, GITHUB_REVIEW_PATH), `${JSON.stringify(persistedReview, null, 2)}\n`, 'utf8');
  }

  const artifactDefinitions = [
    ['sources/mysql/reference.json', 'application/json'],
    ['sources/github/repository.json', 'application/json'],
    ['sources/github/file-index.jsonl', 'application/x-ndjson'],
    ...(requiresReview ? [[GITHUB_REVIEW_PATH, 'application/json']] : []),
    ...REQUIRED_MARKDOWN_PATHS.map((path) => [path, 'text/markdown']),
    ['sources/terminology/graph.json', 'application/json'],
  ];
  const artifacts = await Promise.all(artifactDefinitions.map(([path, mediaType]) => artifact(input.root, path, mediaType)));
  const sources = [
    { sourceId: 'guanyijia_mysql', snapshotId: MYSQL_SNAPSHOT_ID, contentOrigin: 'SNAPSHOT_REFERENCE', artifacts: artifacts.filter((entry) => entry.path.startsWith('sources/mysql/')) },
    { sourceId: 'guanyijia_github', snapshotId: 'github-b3ab269b05070d40', contentOrigin: 'SOURCE_NATIVE', artifacts: artifacts.filter((entry) => entry.path.startsWith('sources/github/')) },
    { sourceId: 'guanyijia_official_docs', snapshotId: 'guanyijia-demo-official-v1', contentOrigin: 'DEMO_AUTHORED', artifacts: artifacts.filter((entry) => entry.path.startsWith('sources/official/')) },
    { sourceId: 'guanyijia_demo_policy', snapshotId: 'guanyijia-demo-policy-content-v1', contentOrigin: 'DEMO_AUTHORED', artifacts: artifacts.filter((entry) => entry.path.startsWith('sources/policy/')) },
    { sourceId: 'guanyijia_semantica_demo', snapshotId: 'guanyijia-demo-terminology-v1', contentOrigin: 'DERIVED_DEMO', artifacts: artifacts.filter((entry) => entry.path.startsWith('sources/terminology/')) },
  ];
  const unsignedManifest = {
    schemaVersion: 1,
    snapshotId,
    storyKey: 'guanyijia-five-source-v1',
    sources,
    generationRuns: [input.generation],
  };
  const manifest = { ...unsignedManifest, contentSha256: sha256(`${canonicalJson(unsignedManifest)}\n`) };
  await writeFile(join(input.root, 'generation-manifest.json'), `${JSON.stringify(input.generation, null, 2)}\n`, 'utf8');
  await writeFile(join(input.root, 'checksums.sha256'), `${artifacts.map((entry) => `${entry.sha256.slice(7)}  ${entry.path}`).join('\n')}\n`, 'utf8');
  await writeFile(join(input.root, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  await validateDemoContentSnapshot(input.root);
  return manifest;
}

export async function validateDemoContentSnapshot(root) {
  const manifest = await readJson(root, 'manifest.json');
  const snapshotVersion = demoContentSnapshotVersion(manifest.snapshotId);
  const richSourceGeneration = requiresRichSourceGeneration(snapshotVersion);
  const expectedGenerationCount = richSourceGeneration ? REQUIRED_SOURCES.length : 1;
  if (manifest.schemaVersion !== 1 || !isDemoContentSnapshotId(manifest.snapshotId)
    || manifest.storyKey !== 'guanyijia-five-source-v1' || !Array.isArray(manifest.sources)
    || !Array.isArray(manifest.generationRuns) || manifest.generationRuns.length !== expectedGenerationCount) {
    fail('manifest shape is invalid');
  }
  if (manifest.sources.length !== REQUIRED_SOURCES.length) fail('manifest source count is invalid');
  for (const [sourceId, origin] of REQUIRED_SOURCES) requireSource(manifest, sourceId, origin);
  const sourceIds = manifest.sources.map((source) => source.sourceId);
  if (new Set(sourceIds).size !== sourceIds.length) fail('manifest source IDs are duplicated');
  validateAppendOnlyLineage(manifest, snapshotVersion);
  const unsignedManifest = signedManifestPayload(manifest);
  if (!isSha256(manifest.contentSha256) || manifest.contentSha256 !== sha256(`${canonicalJson(unsignedManifest)}\n`)) {
    fail('manifest content digest mismatch');
  }
  const allowedReasoningEfforts = demoContentSnapshotVersion(manifest.snapshotId) >= 7
    ? ['medium', 'high', 'xhigh']
    : ['medium', 'high'];
  for (const generation of manifest.generationRuns) {
    if (generation.provider !== 'CODEX_CHATGPT_SESSION' || generation.model !== 'gpt-5.6-luna'
      || !allowedReasoningEfforts.includes(generation.reasoningEffort) || typeof generation.sessionId !== 'string'
      || !generation.sessionId || typeof generation.promptVersion !== 'string'
      || !isSha256(generation.inputDigest) || !isSha256(generation.outputDigest)) fail('generation manifest is invalid');
  }
  const persistedGeneration = await readJson(root, 'generation-manifest.json');
  if (richSourceGeneration) {
    validateRichGenerationRuns(manifest, persistedGeneration);
  } else if (canonicalJson(persistedGeneration) !== canonicalJson(manifest.generationRuns[0])) {
    fail('generation manifest does not match snapshot');
  }

  await validateArtifactHashes(root, manifest);
  const mysql = requireSource(manifest, 'guanyijia_mysql', 'SNAPSHOT_REFERENCE');
  const github = requireSource(manifest, 'guanyijia_github', 'SOURCE_NATIVE');
  await validateMySqlReference(root, mysql);
  const githubResult = await validateGitHubSource(root, github);
  const githubReview = requiresGitHubReview(manifest.snapshotId)
    ? await validateGitHubReview(root, githubResult.entries)
    : undefined;
  const markdown = await validateMarkdown(root);
  const graph = await validateTermGraph(root);
  return {
    snapshotId: manifest.snapshotId,
    sources: manifest.sources,
    github: {
      trackedFileCount: githubResult.trackedFileCount,
      ...(githubReview ? { review: githubReview } : {}),
    },
    markdown,
    graph,
  };
}

async function main() {
  const [command, root] = process.argv.slice(2);
  if (command !== '--check' || !root || process.argv.length !== 4) {
    throw new Error('Usage: node scripts/evidence/guanyijia-demo-content-snapshot.mjs --check <snapshot-root>');
  }
  const result = await validateDemoContentSnapshot(root);
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

if (process.argv[1] && relative(dirname(process.argv[1]), import.meta.filename) === '') {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
