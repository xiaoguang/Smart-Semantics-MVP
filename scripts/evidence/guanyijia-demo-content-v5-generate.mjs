import { createHash } from 'node:crypto';
import { execFile as execFileCallback, spawn } from 'node:child_process';
import { cp, lstat, mkdir, mkdtemp, readFile, rename, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { freezeDemoContentSnapshot } from './guanyijia-demo-content-snapshot.mjs';

const execFile = promisify(execFileCallback);
const moduleDirectory = dirname(fileURLToPath(import.meta.url));
const prototypeRoot = resolve(moduleDirectory, '../..');
const workspaceRoot = resolve(prototypeRoot, '..');
const sourceSnapshotRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v4-20260826',
);
const targetSnapshotRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v5-20260826',
);
const schemaPath = join(moduleDirectory, 'schemas/guanyijia-github-v5-review.schema.json');
const snapshotId = 'guanyijia-demo-content-v5-20260826';
const promptVersion = 'guanyijia-github-v5-native-review';
const forbiddenEnvironmentKeys = Object.freeze([
  'OPENAI_API_KEY', 'CODEX_API_KEY', 'CODEX_ACCESS_TOKEN', 'OPENAI_BASE_URL', 'AZURE_OPENAI_API_KEY', 'AZURE_OPENAI_ENDPOINT',
]);
const sectionNames = Object.freeze([
  '系统结构与部署入口', '租户隔离、权限与组织', '商品、客户、供应商等主数据',
  '采购、入库与采购退货', '销售、出库与销售退货', '库存、负库存、批号与序列号',
  '财务、账户、订金与欠款', '单据状态、审核与迁移记录', '查询、报表口径与资料边界',
]);
const allowedKinds = new Set(['ARCHITECTURE', 'CODE_BEHAVIOR', 'CONFIGURATION', 'SCHEMA_MIGRATION', 'QUERY', 'BUSINESS_RULE', 'GAP']);

/**
 * These are selection rules, not claims. They are intentionally limited to a
 * fixed, existing source tree and make every later LLM sentence cite an
 * already-addressed line range. Two windows from each named business file
 * produce 60–90 candidates without treating the rest of the repository as
 * reviewed.
 */
const selectionRules = Object.freeze([
  [1, 'ARCHITECTURE', 'jshERP-boot/src/main/java/com/jsh/erp/base/BaseController.java', 'JAVA'],
  [1, 'CONFIGURATION', 'jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java', 'JAVA'],
  [1, 'CONFIGURATION', 'jshERP-boot/docs/jsh_erp.sql', 'SYSTEM_CONFIG'],
  [2, 'CONFIGURATION', 'jshERP-boot/src/main/java/com/jsh/erp/config/TenantConfig.java', 'JAVA'],
  [2, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/UserService.java', 'JAVA'],
  [2, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/RoleService.java', 'JAVA'],
  [2, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/UserBusinessService.java', 'JAVA'],
  [3, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/MaterialService.java', 'JAVA'],
  [3, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/SupplierService.java', 'JAVA'],
  [3, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/MaterialExtendService.java', 'JAVA'],
  [3, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/MaterialMapperEx.xml', 'XML'],
  [4, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java', 'JAVA'],
  [4, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemService.java', 'JAVA'],
  [4, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapperEx.xml', 'XML'],
  [4, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml', 'XML'],
  [5, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/InOutItemService.java', 'JAVA'],
  [5, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/DepotService.java', 'JAVA'],
  [5, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/InOutItemMapperEx.xml', 'XML'],
  [5, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/MaterialCurrentStockMapperEx.xml', 'XML'],
  [6, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/SerialNumberService.java', 'JAVA'],
  [6, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/SequenceService.java', 'JAVA'],
  [6, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/SerialNumberMapperEx.xml', 'XML'],
  [6, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/MaterialExtendMapperEx.xml', 'XML'],
  [7, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/AccountService.java', 'JAVA'],
  [7, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java', 'JAVA'],
  [7, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/AccountItemService.java', 'JAVA'],
  [7, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/AccountMapperEx.xml', 'XML'],
  [7, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/AccountHeadMapperEx.xml', 'XML'],
  [7, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/AccountItemMapperEx.xml', 'XML'],
  [8, 'SCHEMA_MIGRATION', 'jshERP-boot/docs/数据库更新记录-首次安装请勿使用.txt', 'MIGRATION'],
  [8, 'CODE_BEHAVIOR', 'jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java', 'STATUS'],
  [8, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapperEx.xml', 'XML_LATE'],
  [9, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml', 'XML_LATE'],
  [9, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/TenantMapperEx.xml', 'XML'],
  [9, 'QUERY', 'jshERP-boot/src/main/resources/mapper_xml/UserMapperEx.xml', 'XML'],
]);

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function lineCount(value) {
  const normalized = value.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  return normalized.length === 0 ? 0 : normalized.endsWith('\n')
    ? normalized.slice(0, -1).split('\n').length : normalized.split('\n').length;
}

function fail(message) {
  throw new Error(`GitHub V5 review generation blocked: ${message}`);
}

function filename(path) {
  return path.slice(path.lastIndexOf('/') + 1).replace(/\.(java|xml|sql|txt)$/u, '');
}

function candidateLineIndexes(lines, mode) {
  const publicMethod = /^\s*(?:public|protected)\s+(?!class\b)[^=;{]+\([^;{]*\)\s*(?:throws\s+[^\{]+)?\s*\{?\s*$/u;
  const query = /^\s*<(?:select|insert|update|delete)\s+id=/u;
  const migration = /^\s*(?:alter\s+table|create\s+table)\b/iu;
  const systemConfig = /`(?:minus_stock_flag|purchase_by_sale_flag|multi_level_approval_flag|force_approval_flag)`/u;
  const status = /(?:case\s+"9"|审核中|status)/u;
  if (mode === 'JAVA') return lines.flatMap((line, index) => publicMethod.test(line) ? [index] : []);
  if (mode === 'XML' || mode === 'XML_LATE') return lines.flatMap((line, index) => query.test(line) ? [index] : []);
  if (mode === 'MIGRATION') return lines.flatMap((line, index) => migration.test(line) ? [index] : []);
  if (mode === 'SYSTEM_CONFIG') return lines.flatMap((line, index) => systemConfig.test(line) ? [index] : []);
  if (mode === 'STATUS') return lines.flatMap((line, index) => status.test(line) ? [index] : []);
  return [];
}

function selectedIndexes(indexes, mode) {
  if (mode === 'XML_LATE') return indexes.slice(-2);
  return indexes.slice(0, 2);
}

function windowFor(mode, match, lines) {
  const after = mode.startsWith('XML') ? 20 : mode === 'MIGRATION' ? 2 : 13;
  const before = mode === 'MIGRATION' || mode === 'SYSTEM_CONFIG' ? 1 : 2;
  return { start: Math.max(0, match - before), end: Math.min(lines.length - 1, match + after) };
}

export async function buildGitHubEvidenceCatalog({ root, selection = selectionRules, index }) {
  const indexByPath = new Map(index.map((entry) => [entry.path, entry]));
  const evidence = [];
  const isDefaultSelection = selection === selectionRules;
  for (const rule of selection) {
    const [section, kind, path, mode] = Array.isArray(rule)
      ? rule
      : [rule.section, rule.kind, rule.path, 'CUSTOM'];
    const entry = indexByPath.get(path);
    if (!entry || entry.textEncoding !== 'UTF-8') fail(`source index is unavailable for ${path}`);
    const content = await readFile(join(root, 'sources/github/source', path), 'utf8');
    if (sha256(content) !== entry.sha256 || lineCount(content) !== entry.lineCount) fail(`source digest mismatch for ${path}`);
    const normalizedContent = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    // The frozen index intentionally counts physical lines, not the synthetic
    // empty entry that String#split creates after a trailing newline. Evidence
    // windows must use the same convention or their final line can exceed the
    // index even though the saved file itself is valid.
    const lines = normalizedContent.length === 0 ? [] : normalizedContent.endsWith('\n')
      ? normalizedContent.slice(0, -1).split('\n')
      : normalizedContent.split('\n');
    const matches = mode === 'CUSTOM'
      ? lines.flatMap((line, index) => line.includes(rule.matcher) ? [index] : [])
      : selectedIndexes(candidateLineIndexes(lines, mode), mode);
    if (!matches.length) fail(`selection rule did not match frozen source: ${path}`);
    for (const [ordinal, match] of matches.entries()) {
      const { start, end } = mode === 'CUSTOM'
        ? {
          start: Math.max(0, match - (rule.before ?? 0)),
          end: Math.min(lines.length - 1, match + (rule.after ?? 0)),
        }
        : windowFor(mode, match, lines);
      const excerpt = lines.slice(start, end + 1).join('\n');
      evidence.push({
        evidenceRef: `github:v5:${String(evidence.length + 1).padStart(3, '0')}`,
        section,
        kind,
        title: rule.title ?? `${filename(path)} · ${ordinal + 1}`,
        path,
        startLine: start + 1,
        endLine: end + 1,
        excerpt,
        excerptSha256: sha256(excerpt),
        artifactDigest: entry.sha256,
        lineCount: end - start + 1,
      });
    }
  }
  const selectedFiles = new Set(evidence.map((item) => item.path));
  const sourceLines = new Map();
  for (const item of evidence) {
    const ranges = sourceLines.get(item.path) ?? new Set();
    for (let line = item.startLine; line <= item.endLine; line += 1) ranges.add(line);
    sourceLines.set(item.path, ranges);
  }
  const uniqueSourceLineCount = [...sourceLines.values()].reduce((sum, lines) => sum + lines.size, 0);
  if (isDefaultSelection && (evidence.length < 60 || evidence.length > 90 || selectedFiles.size < 25 || selectedFiles.size > 40 || uniqueSourceLineCount < 500)) {
    fail(`selection coverage is outside V5 bounds (${evidence.length} evidence, ${selectedFiles.size} files, ${uniqueSourceLineCount} lines)`);
  }
  return {
    schemaVersion: 1,
    selectionVersion: 'guanyijia-github-v5-selection-1',
    selectedFileCount: selectedFiles.size,
    uniqueSourceLineCount,
    evidence,
  };
}

function validClaimId(value) {
  return typeof value === 'string' && /^github-v5-[0-9]{3}$/u.test(value);
}

export function validateGithubReviewProposal(value, catalog) {
  if (!value || value.schemaVersion !== 1 || !Array.isArray(value.claims)) fail('proposal shape is invalid');
  const allowedEvidence = new Set(catalog.evidence.map((item) => item.evidenceRef));
  for (const claim of value.claims) {
    for (const evidenceRef of claim?.evidenceRefs ?? []) {
      if (!allowedEvidence.has(evidenceRef)) fail(`claim cites unknown evidence: ${evidenceRef}`);
    }
  }
  if (value.claims.length < 40 || value.claims.length > 60) fail('proposal claim count must be 40–60');
  const usedEvidence = new Set();
  const claimIds = new Set();
  const claims = value.claims.map((claim) => {
    if (!claim || !validClaimId(claim.claimId) || claimIds.has(claim.claimId)) fail('claim identity is invalid');
    claimIds.add(claim.claimId);
    if (!Number.isInteger(claim.section) || claim.section < 1 || claim.section > sectionNames.length || !allowedKinds.has(claim.kind)) {
      fail('claim section or type is invalid');
    }
    if (typeof claim.title !== 'string' || claim.title.length < 2 || claim.title.length > 28
      || typeof claim.statement !== 'string' || claim.statement.length < 50 || claim.statement.length > 240
      || typeof claim.reviewFocus !== 'string' || claim.reviewFocus.length < 20 || claim.reviewFocus.length > 140
      || typeof claim.boundary !== 'string' || claim.boundary.length < 20 || claim.boundary.length > 140
      || !Array.isArray(claim.evidenceRefs) || claim.evidenceRefs.length < 1 || claim.evidenceRefs.length > 3
      || !Array.isArray(claim.focusIdentifiers)) fail('claim wording or evidence is invalid');
    for (const evidenceRef of claim.evidenceRefs) {
      if (!allowedEvidence.has(evidenceRef)) fail(`claim cites unknown evidence: ${evidenceRef}`);
      usedEvidence.add(evidenceRef);
    }
    return {
      claimId: claim.claimId,
      section: claim.section,
      kind: claim.kind,
      title: claim.title.trim(),
      statement: claim.statement.trim(),
      reviewFocus: claim.reviewFocus.trim(),
      boundary: claim.boundary.trim(),
      evidenceRefs: [...new Set(claim.evidenceRefs)],
      focusIdentifiers: claim.focusIdentifiers.filter((value) => typeof value === 'string' && value.length <= 80),
    };
  });
  if (usedEvidence.size !== allowedEvidence.size) fail('every selected source window must support at least one claim');
  return { schemaVersion: 1, claims };
}

function contentPrompt(catalog) {
  return `你是中文ERP代码审阅文档作者。只输出符合JSON Schema的JSON，不要使用代码围栏或附加说明。\n\n` +
    `以下是已固定的本地GitHub提交中的已准入源码窗口。请写40到60条可审阅结论；每条必须只引用给定evidenceRef，且每个evidenceRef至少被一条结论引用。不要添加文件、行号、SQL、源代码、URL、提交摘要、SHA或任何未展示的事实。\n\n` +
    `每条必须有三个互不重复的中文字段：statement（这段代码直接显示的内容）、reviewFocus（为什么值得业务人员核对）、boundary（片段不能证明什么或需要下一步确认什么）。每一字段都要用完整、自然的中文短段落，不能用模板句或内部技术术语凑字数。\n\n` +
    `文风：面向业务审阅，但保留必要的表名、字段名、方法名。代码片段只证明它直接展示的读取、定义、查询或分支；不要把配置读取写成统一制度，不要把查询实现写成最终指标口径，不要将状态9的显示分支写成正式业务定义。涉及范围不完整时用GAP说明缺什么、限制什么、下一步需要确认什么。\n\n` +
    `九个章节：\n${sectionNames.map((name, index) => `${index + 1}. ${name}`).join('\n')}\n\n` +
    `Evidence catalog:\n${JSON.stringify(catalog.evidence)}`;
}

function sanitizedEnvironment() {
  for (const key of forbiddenEnvironmentKeys) if (process.env[key]) fail(`forbidden API credential is present: ${key}`);
  const environment = { ...process.env };
  for (const key of forbiddenEnvironmentKeys) delete environment[key];
  return environment;
}

async function assertChatGptLogin(environment) {
  const result = await execFile('codex', ['login', 'status'], { cwd: prototypeRoot, env: environment, encoding: 'utf8' });
  if (!`${result.stdout}\n${result.stderr}`.includes('Logged in using ChatGPT')) fail('a ChatGPT Codex login is required');
}

function sessionIdFromJsonLines(value) {
  for (const line of value.split('\n')) {
    try {
      const event = JSON.parse(line);
      if (event.type === 'thread.started' && typeof event.thread_id === 'string' && event.thread_id) return event.thread_id;
    } catch { /* diagnostics do not form a manifest */ }
  }
  fail('Codex session ID was not returned');
}

export function codexInvocationArgs(outputPath) {
  return [
    'exec', '--ephemeral', '--ignore-user-config',
    '-m', 'gpt-5.6-luna', '-c', 'model_reasoning_effort="high"', '-s', 'read-only',
    '--output-schema', schemaPath, '--output-last-message', outputPath, '--json', '-',
  ];
}

function runCodex(input, environment, outputPath) {
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
    child.once('close', (code) => code === 0 ? resolveRun({ stdout, stderr })
      : rejectRun(new Error(`Codex generation exited with ${code}: ${[
        stderr.trim(),
        stdout.trim(),
      ].filter(Boolean).join('\n')}`)));
    child.stdin.end(input, 'utf8');
  });
}

async function assertTargetDoesNotExist(root) {
  try {
    await lstat(root);
    fail(`successor snapshot already exists: ${root}`);
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
}

async function inheritedMaterials(root) {
  const paths = [
    'sources/official/系统边界、基础资料与角色.md', 'sources/official/采购销售与库存业务流程.md',
    'sources/official/财务报表、状态与审核口径.md', 'sources/policy/基础资料与数据权限制度.md',
    'sources/policy/采购销售与退货管理制度.md', 'sources/policy/库存批次序列号与负库存制度.md',
    'sources/policy/财务结算欠款与账户制度.md', 'sources/policy/单据审核状态与统计时点制度.md',
    'sources/terminology/术语图说明.md',
  ];
  return {
    documents: await Promise.all(paths.map(async (path) => ({ path, content: await readFile(join(root, path), 'utf8') }))),
    graph: JSON.parse(await readFile(join(root, 'sources/terminology/graph.json'), 'utf8')),
  };
}

export async function generateGithubV5Content(input = {}) {
  const sourceRoot = input.sourceSnapshotRoot ?? sourceSnapshotRoot;
  const targetRoot = input.targetSnapshotRoot ?? targetSnapshotRoot;
  await assertTargetDoesNotExist(targetRoot);
  const environment = sanitizedEnvironment();
  await assertChatGptLogin(environment);
  const stage = await mkdtemp(join(dirname(targetRoot), `.${basename(targetRoot)}.staging-`));
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-github-v5-'));
  let published = false;
  try {
    await cp(join(sourceRoot, 'sources/github'), join(stage, 'sources/github'), { recursive: true, errorOnExist: true, force: false });
    const index = (await readFile(join(stage, 'sources/github/file-index.jsonl'), 'utf8')).trim().split('\n').filter(Boolean).map(JSON.parse);
    const catalog = await buildGitHubEvidenceCatalog({ root: stage, index });
    const outputPath = join(temporaryRoot, 'proposal.json');
    const { stdout } = await runCodex(contentPrompt(catalog), environment, outputPath);
    const sessionId = sessionIdFromJsonLines(stdout);
    const rawOutput = await readFile(outputPath, 'utf8');
    let proposal;
    try { proposal = JSON.parse(rawOutput); } catch { fail('Codex proposal is not valid JSON'); }
    const review = { ...catalog, ...validateGithubReviewProposal(proposal, catalog) };
    const materials = await inheritedMaterials(sourceRoot);
    const mysqlManifest = await readFile(resolve(workspaceRoot, 'modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79/manifest.json'));
    const manifest = await freezeDemoContentSnapshot({
      root: stage,
      snapshotId,
      mysqlReference: {
        snapshotId: '20260813T032528Z-abb0502c7d79',
        manifestPath: 'modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79/manifest.json',
        manifestSha256: sha256(mysqlManifest), objectCounts: { tables: 95, views: 2, procedures: 35, events: 1 },
        exclusions: ['business rows', 'samples/**', 'profiles/**'],
      },
      materials,
      githubReview: review,
      generation: {
        provider: 'CODEX_CHATGPT_SESSION', model: 'gpt-5.6-luna', reasoningEffort: 'high', sessionId,
        promptVersion, inputDigest: sha256(contentPrompt(catalog)), outputDigest: sha256(rawOutput),
      },
    });
    await rename(stage, targetRoot);
    published = true;
    return manifest;
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
    if (!published) await rm(stage, { recursive: true, force: true });
  }
}

async function main() {
  if (process.argv.slice(2).join(' ') !== '--generate') fail('Usage: node scripts/evidence/guanyijia-demo-content-v5-generate.mjs --generate');
  const manifest = await generateGithubV5Content();
  process.stdout.write(`${JSON.stringify({ snapshotId: manifest.snapshotId, contentSha256: manifest.contentSha256 }, null, 2)}\n`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
}
