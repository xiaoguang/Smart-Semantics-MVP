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
const mysqlRoot = resolve(workspaceRoot, 'modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79');
const officialClaimsPath = resolve(workspaceRoot, 'modeling-evidence/guanyijia/documents/official/modeling-claims.json');
const schemaPath = join(moduleDirectory, 'schemas/guanyijia-demo-content.schema.json');
const defaultSourceSnapshotRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v3-20260826',
);
const defaultTargetSnapshotRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v4-20260826',
);
const successorSnapshotId = 'guanyijia-demo-content-v4-20260826';
const promptVersion = 'guanyijia-demo-content-v4-copy-audit';

export const contentGenerationConfiguration = Object.freeze({
  sourceSnapshotId: 'guanyijia-demo-content-v3-20260826',
  targetSnapshotId: successorSnapshotId,
  promptVersion,
});
const expectedDocumentPaths = Object.freeze([
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
const mysqlTables = Object.freeze([
  'jsh_supplier', 'jsh_material', 'jsh_material_category', 'jsh_material_extend', 'jsh_material_attribute', 'jsh_unit',
  'jsh_depot', 'jsh_material_current_stock', 'jsh_material_initial_stock', 'jsh_serial_number', 'jsh_inventory_rebalance_log',
  'jsh_depot_head', 'jsh_depot_item', 'jsh_sequence', 'jsh_in_out_item',
  'jsh_account', 'jsh_account_head', 'jsh_account_item',
  'jsh_tenant', 'jsh_user', 'jsh_person', 'jsh_organization', 'jsh_orga_user_rel', 'jsh_role', 'jsh_user_business', 'jsh_function',
  'jsh_system_config', 'biz_bill_item_fact', 'biz_order_link_map_1', 'biz_store_product_supplier_map_1',
]);
const mysqlProcedures = Object.freeze([
  'proc_insert_purchase_requisition', 'proc_generate_purchase_order_from_requisition',
  'proc_generate_purchase_inbound_from_order', 'proc_generate_purchase_return',
  'proc_generate_retail_return', 'sp_run_inventory_stock_rebalance',
]);
const codeWindows = Object.freeze([
  ['jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java', 505, 524],
  ['jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemService.java', 642, 660],
  ['jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java', 175, 198],
  ['jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java', 740, 790],
  ['jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java', 1438, 1462],
  ['jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java', 1582, 1648],
  ['jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java', 1828, 1845],
  ['jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapperEx.xml', 145, 170],
  ['jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapperEx.xml', 1170, 1245],
]);
const forbiddenEnvironmentKeys = Object.freeze([
  'OPENAI_API_KEY', 'CODEX_API_KEY', 'CODEX_ACCESS_TOKEN', 'OPENAI_BASE_URL', 'AZURE_OPENAI_API_KEY', 'AZURE_OPENAI_ENDPOINT',
]);
export const codexGenerationArguments = Object.freeze([
  'exec', '-m', 'gpt-5.6-luna', '-c', 'model_reasoning_effort="high"', '-s', 'read-only',
  '--output-schema', schemaPath,
]);

function sha256(value) {
  return `sha256:${createHash('sha256').update(value, 'utf8').digest('hex')}`;
}

function fail(message) {
  throw new Error(`Demo content generation blocked: ${message}`);
}

function diagnose(message) {
  if (process.env.GUANYIJIA_DEMO_CONTENT_DIAGNOSTICS === '1') process.stderr.write(`[demo-content] ${message}\n`);
}

function isNonBlankString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

export function normalizeDemoContentProposal(value) {
  if (!value || value.schemaVersion !== 1 || !Array.isArray(value.documents)
    || value.documents.length !== expectedDocumentPaths.length || !value.graph || typeof value.graph !== 'object') {
    fail('documents and graph must match the approved shape');
  }
  const byPath = new Map();
  for (const document of value.documents) {
    if (!document || !expectedDocumentPaths.includes(document.path) || !isNonBlankString(document.content)
      || document.content.trim().length < 200 || byPath.has(document.path)) {
      fail('documents must contain each approved path exactly once');
    }
    byPath.set(document.path, document.content.replace(/\r\n/g, '\n'));
  }
  if (byPath.size !== expectedDocumentPaths.length) fail('documents must contain each approved path exactly once');
  return {
    documents: expectedDocumentPaths.map((path) => ({ path, content: byPath.get(path) })),
    graph: structuredClone(value.graph),
  };
}

function lineRange(content, startLine, endLine) {
  const lines = content.replace(/\r\n/g, '\n').split('\n');
  if (startLine < 1 || endLine < startLine || endLine > lines.length) fail('source code window is outside captured source');
  return lines.slice(startLine - 1, endLine).join('\n');
}

async function buildGenerationPacket(snapshotRoot) {
  const mysqlManifest = JSON.parse(await readFile(join(mysqlRoot, 'manifest.json'), 'utf8'));
  const mysqlArtifacts = [];
  for (const table of mysqlTables) {
    const relativePath = `ddl/tables/${table}.sql`;
    mysqlArtifacts.push({ path: relativePath, content: await readFile(join(mysqlRoot, relativePath), 'utf8') });
  }
  for (const procedure of mysqlProcedures) {
    const relativePath = `programmability/procedures/${procedure}.sql`;
    mysqlArtifacts.push({ path: relativePath, content: await readFile(join(mysqlRoot, relativePath), 'utf8') });
  }
  const githubRoot = join(snapshotRoot, 'sources/github/source');
  const codeArtifacts = [];
  for (const [path, startLine, endLine] of codeWindows) {
    const content = await readFile(join(githubRoot, path), 'utf8');
    codeArtifacts.push({ path, startLine, endLine, content: lineRange(content, startLine, endLine) });
  }
  const officialClaims = JSON.parse(await readFile(officialClaimsPath, 'utf8'));
  return {
    schemaVersion: 1,
    purpose: '生成独立的、丰富但明确属于演示资料的五源内容快照；不生成证据链或正式模型结论。',
    database: {
      snapshotId: mysqlManifest.snapshotId,
      objectCounts: mysqlManifest.objectCounts,
      selectedArtifacts: mysqlArtifacts,
    },
    github: {
      repository: 'https://github.com/jishenghua/jshERP.git',
      commit: 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1',
      selectedCodeWindows: codeArtifacts,
    },
    historicalOfficialRecords: officialClaims.claims,
  };
}

function buildPrompt(packet) {
  return `你是中文ERP产品与数据治理文档作者。请只输出符合给定JSON Schema的JSON，不使用Markdown代码围栏，不附加说明。\n\n任务：基于下面冻结的数据库结构、固定源码片段和历史官方记录，生成“管伊佳五源Demo资料”的第四个可读性净化版本。资料是演示内容，不是外部官方原文、现行生产制度、证据链或正式模型结论。不要写任何引用、URL、SHA、文件路径、行号、证据编号、内部ID、TRACE、HTML标签或“依据如下”。\n\n必须生成恰好9份指定路径的Markdown和一个术语图。每份Markdown必须以一级标题开始，包含2至5个有业务意义的二级章节，且为内部校验另含一个唯一的注释格式章节ID，例如 <!-- section-id: official.system-boundary -->。章节正文不得出现该注释或任何技术标识。九份Markdown合计中文汉字必须介于5800和7800之间。\n\n八个领域必须自然覆盖：主数据、采购、销售、库存、财务、租户权限、报表指标、工作流审计。文风应像成熟企业的产品说明和制度文件：使用完整段落、具体业务场景、规则、例外、操作后果与职责分工，避免“该业务载体存在”“冻结扫描已识别”等模板句。每段最多三句；适合枚举的内容使用简洁列表或表格。不要重复标题、免责声明或同一句话。\n\n三项治理议题必须保留、补足其各自语境，但不要替审阅者裁决：\n1. 负库存：源码显示配置读取和库存校验；制度可以提出统一禁止负库存的目标，但必须保留“当前配置能力”与“目标制度”之间的待审阅差异。\n2. 欠款字段：源码有debt/last_debt计算和查询；当前部署结构可能没有对应字段。解释这可以是部署版本、迁移节奏或数据口径差异，不能断言谁自动正确。\n3. 单据状态：审核、反审核、状态9或“审核中”可能处于不同版本/流程语境。解释版本差异和待确认含义，不能直接消除差异。\n\n术语图必须有48至72个术语、72至120条关系，覆盖八领域；关系只能引用本图已有termId，definition和description使用中文人类可读句子。\n\n固定文档路径：\n${expectedDocumentPaths.map((path) => `- ${path}`).join('\n')}\n\n冻结输入包：\n${JSON.stringify(packet)}\n`;
}

function sanitizedEnvironment() {
  for (const key of forbiddenEnvironmentKeys) {
    if (process.env[key]) fail(`forbidden API credential is present: ${key}`);
  }
  const environment = { ...process.env };
  for (const key of forbiddenEnvironmentKeys) delete environment[key];
  return environment;
}

export function hasChatGptLogin(result) {
  return `${result?.stdout ?? ''}\n${result?.stderr ?? ''}`.includes('Logged in using ChatGPT');
}

async function assertChatGptLogin(environment) {
  const result = await execFile('codex', ['login', 'status'], {
    cwd: prototypeRoot,
    env: environment,
    encoding: 'utf8',
  });
  if (!hasChatGptLogin(result)) fail('a ChatGPT Codex login is required');
}

function sessionIdFromJsonLines(value) {
  for (const line of value.split('\n')) {
    try {
      const event = JSON.parse(line);
      if (event.type === 'thread.started' && isNonBlankString(event.thread_id)) return event.thread_id;
    } catch {
      // Non-event diagnostics cannot authenticate a generation manifest.
    }
  }
  fail('Codex session ID was not returned');
}

export function runCommandWithPrompt(command, args, input, options) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    let settled = false;
    const failRun = (error) => {
      if (settled) return;
      settled = true;
      rejectRun(error);
    };
    const append = (stream, chunk) => {
      if (settled) return;
      const next = stream === 'stdout' ? stdout + chunk.toString('utf8') : stderr + chunk.toString('utf8');
      if (Buffer.byteLength(next, 'utf8') > options.maxBuffer) {
        child.kill('SIGTERM');
        failRun(new Error('Codex generation output exceeded the allowed buffer'));
        return;
      }
      if (stream === 'stdout') stdout = next;
      else stderr = next;
    };
    child.once('error', failRun);
    child.stdout.on('data', (chunk) => append('stdout', chunk));
    child.stderr.on('data', (chunk) => append('stderr', chunk));
    child.once('close', (code, signal) => {
      if (settled) return;
      settled = true;
      if (code === 0) {
        resolveRun({ stdout, stderr });
        return;
      }
      const diagnostics = [stderr.trim(), stdout.trim()].filter(Boolean).join('\n');
      rejectRun(new Error(`Codex generation exited with ${code ?? signal ?? 'an unknown error'}${diagnostics ? `: ${diagnostics}` : ''}`));
    });
    child.stdin.once('error', failRun);
    child.stdin.end(input, 'utf8');
  });
}

async function assertTargetDoesNotExist(targetRoot) {
  try {
    await lstat(targetRoot);
    fail(`successor snapshot already exists: ${targetRoot}`);
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
}

/**
 * Creates a same-filesystem staging root before the one allowed Codex run.
 * The immutable target name is never written until all generated material has
 * passed validation and freezing.  Staging is deliberately beside the target
 * so the final rename is atomic.
 */
export async function createGenerationStage(sourceRoot, targetRoot) {
  await assertTargetDoesNotExist(targetRoot);
  await mkdir(dirname(targetRoot), { recursive: true });
  const stagingRoot = await mkdtemp(join(dirname(targetRoot), `.${basename(targetRoot)}.staging-`));
  try {
    await cp(join(sourceRoot, 'sources/github'), join(stagingRoot, 'sources/github'), {
      recursive: true,
      errorOnExist: true,
      force: false,
    });
    return stagingRoot;
  } catch (error) {
    await rm(stagingRoot, { recursive: true, force: true });
    throw error;
  }
}

async function publishGenerationStage(stagingRoot, targetRoot) {
  await assertTargetDoesNotExist(targetRoot);
  await rename(stagingRoot, targetRoot);
}

export async function generateDemoContentSnapshot(input = {}) {
  const sourceSnapshotRoot = input.sourceSnapshotRoot ?? defaultSourceSnapshotRoot;
  const targetSnapshotRoot = input.targetSnapshotRoot ?? defaultTargetSnapshotRoot;
  const snapshotId = input.snapshotId ?? successorSnapshotId;
  diagnose(`target=${targetSnapshotRoot}`);
  await assertTargetDoesNotExist(targetSnapshotRoot);
  const environment = sanitizedEnvironment();
  await assertChatGptLogin(environment);
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-demo-content-generation-'));
  const responsePath = join(temporaryRoot, 'proposal.json');
  let stagingRoot;
  let published = false;
  try {
    // Capture the complete source input before the session starts.  A failed
    // session leaves the old immutable snapshots intact and only this staging
    // directory is removed.
    stagingRoot = await createGenerationStage(sourceSnapshotRoot, targetSnapshotRoot);
    const packet = await buildGenerationPacket(stagingRoot);
    const prompt = buildPrompt(packet);
    diagnose('starting Codex session');
    const { stdout } = await runCommandWithPrompt('codex', [
      ...codexGenerationArguments, '--output-last-message', responsePath, '--json', '-',
    ], prompt, {
      cwd: prototypeRoot,
      env: environment,
      maxBuffer: 4 * 1024 * 1024,
    });
    const sessionId = sessionIdFromJsonLines(stdout);
    diagnose(`Codex session=${sessionId}`);
    const rawOutput = await readFile(responsePath, 'utf8');
    let proposal;
    try {
      proposal = JSON.parse(rawOutput);
    } catch {
      fail('Codex proposal is not valid JSON');
    }
    const materials = normalizeDemoContentProposal(proposal);
    const manifest = await freezeDemoContentSnapshot({
      root: stagingRoot,
      snapshotId,
      mysqlReference: {
        snapshotId: '20260813T032528Z-abb0502c7d79',
        manifestPath: 'modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79/manifest.json',
        manifestSha256: `sha256:${createHash('sha256').update(await readFile(join(mysqlRoot, 'manifest.json'))).digest('hex')}`,
        objectCounts: { tables: 95, views: 2, procedures: 35, events: 1 },
        exclusions: ['business rows', 'samples/**', 'profiles/**'],
      },
      materials,
      generation: {
        provider: 'CODEX_CHATGPT_SESSION',
        model: 'gpt-5.6-luna',
        reasoningEffort: 'high',
        sessionId,
        promptVersion,
        inputDigest: sha256(prompt),
        outputDigest: sha256(rawOutput),
      },
    });
    await publishGenerationStage(stagingRoot, targetSnapshotRoot);
    published = true;
    return manifest;
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
    if (stagingRoot && !published) await rm(stagingRoot, { recursive: true, force: true });
  }
}

async function main() {
  const [command, targetSnapshotRoot] = process.argv.slice(2);
  if (command !== '--generate' || process.argv.length > 4) {
    throw new Error('Usage: node scripts/evidence/guanyijia-demo-content-generate.mjs --generate [target-snapshot-root]');
  }
  diagnose(`main target=${targetSnapshotRoot ?? defaultTargetSnapshotRoot}`);
  const manifest = await generateDemoContentSnapshot(targetSnapshotRoot ? { targetSnapshotRoot: resolve(targetSnapshotRoot) } : undefined);
  process.stdout.write(`${JSON.stringify({ snapshotId: manifest.snapshotId, contentSha256: manifest.contentSha256 }, null, 2)}\n`);
}

export function isDirectInvocation(argv = process.argv) {
  return Boolean(argv[1]) && resolve(argv[1]) === fileURLToPath(import.meta.url);
}

if (isDirectInvocation()) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
