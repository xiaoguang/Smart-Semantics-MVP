import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { lstat, mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { standardSectionOrder } from '../../src/features/modeling-document-bridge/standard-markdown.ts';

let generator;
let generatorError;
try {
  generator = await import('./guanyijia-demo-content-v6-generate.mjs');
} catch (error) {
  generatorError = error;
}

const moduleDirectory = dirname(fileURLToPath(import.meta.url));
const prototypeRoot = resolve(moduleDirectory, '../..');
const workspaceRoot = resolve(prototypeRoot, '..');
const sourceSnapshotRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v5-20260826',
);
const mysqlSnapshotRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79',
);

const selectedTables = [
  'jsh_account', 'jsh_account_head', 'jsh_account_item', 'jsh_depot',
  'jsh_depot_head', 'jsh_depot_item', 'jsh_function', 'jsh_in_out_item',
  'jsh_material', 'jsh_material_attribute', 'jsh_material_category',
  'jsh_material_current_stock', 'jsh_material_extend', 'jsh_material_initial_stock',
  'jsh_material_property', 'jsh_msg', 'jsh_orga_user_rel', 'jsh_organization',
  'jsh_person', 'jsh_platform_config', 'jsh_role', 'jsh_serial_number',
  'jsh_supplier', 'jsh_sys_dict_data', 'jsh_sys_dict_type', 'jsh_system_config',
  'jsh_tenant', 'jsh_unit', 'jsh_user', 'jsh_user_business',
];
const selectedProcedures = [
  'sp_rebalance_below_low_stock_requisition',
  'sp_rebalance_over_high_stock_sales',
  'sp_run_inventory_stock_rebalance',
  'sp_run_retail_return_rate_and_fact_sync',
  'sp_run_store_retail_return_coverage_and_fact_sync',
  'sp_sync_retail_out_fact_batch',
];

function requireGenerator() {
  if (generatorError) throw new Error(`V6 generation module is unavailable: ${generatorError.message}`);
  return generator;
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function richBody(sourceId, section) {
  return [
    `${section.heading}围绕${sourceId}已经冻结的来源资料展开，先说明可以直接观察或由资料明确表达的对象、动作、字段、关系与管理语境，再区分需要业务确认的候选解释。`,
    '本章只使用分配到这里的资料引用，不把字段存在等同于制度生效，不把查询实现直接写成最终指标，也不把演示编写或派生内容描述为生产事实。',
    '为了支持后续建模，内容同时保留对象粒度、关键标识、时间与状态维度、上下游关联、可计算候选以及适合由业务人员回答的问题；证据不足处继续标为资料缺口。',
  ].join('');
}

function proposalFor(descriptor) {
  const coverage = descriptor.coverageRefs;
  return {
    schemaVersion: 1,
    sourceId: descriptor.sourceId,
    title: descriptor.title,
    sections: standardSectionOrder.map((section, index) => ({
      section: section.key,
      title: `${section.heading.replace(/^\d+\.\s*/u, '')}审阅结论`,
      body: index === standardSectionOrder.length - 1
        ? `${richBody(descriptor.sourceId, section)} GAP：仍需由资料责任人确认未覆盖范围、正式口径和例外处理。`
        : richBody(descriptor.sourceId, section),
      coverageRefs: coverage.filter((_, coverageIndex) => coverageIndex % standardSectionOrder.length === index),
    })),
  };
}

function fakeSessionAdapter() {
  let active = 0;
  let maximum = 0;
  let callCount = 0;
  let prepareCount = 0;
  let release;
  const allStarted = new Promise((resolveStarted) => { release = resolveStarted; });
  return {
    get callCount() { return callCount; },
    get maximum() { return maximum; },
    get prepareCount() { return prepareCount; },
    async prepare() { prepareCount += 1; },
    async generate({ descriptor }) {
      callCount += 1;
      active += 1;
      maximum = Math.max(maximum, active);
      if (callCount === 5) release();
      await allStarted;
      active -= 1;
      return {
        sessionId: `chatgpt-session-${descriptor.sourceId}`,
        rawOutput: `${JSON.stringify(proposalFor(descriptor), null, 2)}\n`,
      };
    },
  };
}

async function missing(path) {
  try {
    await lstat(path);
    return false;
  } catch (error) {
    if (error?.code === 'ENOENT') return true;
    throw error;
  }
}

test('loads five source-pure frozen inputs with the exact admitted V5 and MySQL coverage', async () => {
  const api = requireGenerator();
  const descriptors = await api.loadV6SourceDescriptors({ sourceSnapshotRoot, mysqlSnapshotRoot });
  assert.deepEqual(descriptors.map((descriptor) => descriptor.sourceId), [
    'guanyijia_mysql',
    'guanyijia_github',
    'guanyijia_official_docs',
    'guanyijia_demo_policy',
    'guanyijia_semantica_demo',
  ]);

  const mysql = descriptors[0];
  assert.deepEqual(mysql.coverage.tables, selectedTables);
  assert.deepEqual(mysql.coverage.procedures, selectedProcedures);
  assert.equal(mysql.evidence.length, 36);

  const github = descriptors[1];
  assert.deepEqual(github.coverage, {
    trackedFileCount: 719,
    selectedFileCount: 32,
    uniqueSourceLineCount: 1003,
    evidenceCount: 69,
    claimCount: 43,
  });
  assert.equal(github.evidence.length, 69);
  assert.equal(github.sourceClaims.length, 43);

  assert.equal(descriptors[2].evidence.length, 9, 'three authored documents expose nine H2 passages');
  assert.equal(descriptors[3].evidence.length, 15, 'five policy drafts expose fifteen H2 passages');
  assert.deepEqual(descriptors[4].coverage, { termCount: 56, relationCount: 80, domainCount: 8 });
});

test('an explicit candidate run starts five Luna ChatGPT-session calls in parallel and publishes no snapshot', async (t) => {
  const api = requireGenerator();
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-v6-candidate-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const candidatePath = join(root, 'candidate.json');
  const unintendedSnapshot = join(root, 'guanyijia-demo-content-v6-20260826');
  const codexSession = fakeSessionAdapter();

  const candidate = await api.generateV6Candidate({
    sourceSnapshotRoot,
    mysqlSnapshotRoot,
    candidatePath,
    codexSession,
  });

  assert.equal(codexSession.prepareCount, 1);
  assert.equal(codexSession.callCount, 5);
  assert.equal(codexSession.maximum, 5, 'all five source calls must overlap');
  assert.equal(candidate.reviews.length, 5);
  assert.equal(JSON.parse(await readFile(candidatePath, 'utf8')).snapshotId, 'guanyijia-demo-content-v6-20260826');
  assert.equal(await missing(unintendedSnapshot), true, 'candidate generation cannot freeze or package content');
});

test('accepts non-empty structurally valid prose without using document size as a gate', async (t) => {
  const api = requireGenerator();
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-v6-short-prose-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const candidatePath = join(root, 'candidate.json');
  const candidate = await api.generateV6Candidate({
    sourceSnapshotRoot,
    mysqlSnapshotRoot,
    candidatePath,
    codexSession: fakeSessionAdapter(),
  });
  const descriptors = await api.loadV6SourceDescriptors({ sourceSnapshotRoot, mysqlSnapshotRoot });

  for (const review of candidate.reviews) {
    const proposal = JSON.parse(review.rawOutput);
    proposal.sections = proposal.sections.map((section, index) => ({
      ...section,
      body: index === standardSectionOrder.length - 1
        ? 'GAP：缺少资料；影响待确认；下一步由负责人确认。'
        : '已保存的业务资料可用于审阅。',
    }));
    review.rawOutput = `${JSON.stringify(proposal)}\n`;
    review.generation.outputDigest = sha256(review.rawOutput);
  }

  assert.doesNotThrow(
    () => api.validateV6Candidate(candidate, descriptors),
    'a non-empty valid candidate must not be rejected only because its prose is short',
  );
});

test('the production Codex adapter is Luna/high, read-only, ephemeral, and has no API fallback arguments', () => {
  const api = requireGenerator();
  const args = api.codexInvocationArgs('/private/tmp/guanyijia-v6-output.json');
  assert.equal(args[0], 'exec');
  assert.ok(args.includes('--ephemeral'));
  assert.ok(args.includes('--ignore-user-config'));
  assert.equal(args[args.indexOf('-m') + 1], 'gpt-5.6-luna');
  assert.equal(args[args.indexOf('-c') + 1], 'model_reasoning_effort="high"');
  assert.equal(args[args.indexOf('-s') + 1], 'read-only');
  assert.equal(args[args.indexOf('--output-last-message') + 1], '/private/tmp/guanyijia-v6-output.json');
  assert.equal(args.some((argument) => /api[_-]?key|base[_-]?url/iu.test(argument)), false);
});

test('deterministic validation rejects missing source coverage before a V6 directory is created', async (t) => {
  const api = requireGenerator();
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-v6-invalid-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const candidatePath = join(root, 'candidate.json');
  const targetSnapshotRoot = join(root, 'guanyijia-demo-content-v6-20260826');
  await api.generateV6Candidate({
    sourceSnapshotRoot,
    mysqlSnapshotRoot,
    candidatePath,
    codexSession: fakeSessionAdapter(),
  });
  const candidate = JSON.parse(await readFile(candidatePath, 'utf8'));
  const github = candidate.reviews.find((review) => review.sourceId === 'guanyijia_github');
  const proposal = JSON.parse(github.rawOutput);
  proposal.sections[0].coverageRefs.shift();
  github.rawOutput = `${JSON.stringify(proposal)}\n`;
  github.generation.outputDigest = sha256(github.rawOutput);

  await assert.rejects(
    api.freezeV6Snapshot({ sourceSnapshotRoot, mysqlSnapshotRoot, targetSnapshotRoot, candidate }),
    /coverage.*exact|coverage membership/iu,
  );
  assert.equal(await missing(targetSnapshotRoot), true);
});

test('freezes an append-only V6 snapshot with five canonical rich reviews and leaves V5 byte-identical', async (t) => {
  const api = requireGenerator();
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-v6-freeze-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const candidatePath = join(root, 'candidate.json');
  const targetSnapshotRoot = join(root, 'guanyijia-demo-content-v6-20260826');
  const v5ManifestBefore = await readFile(join(sourceSnapshotRoot, 'manifest.json'));
  await api.generateV6Candidate({
    sourceSnapshotRoot,
    mysqlSnapshotRoot,
    candidatePath,
    codexSession: fakeSessionAdapter(),
  });

  const manifest = await api.freezeV6Snapshot({
    sourceSnapshotRoot,
    mysqlSnapshotRoot,
    targetSnapshotRoot,
    candidatePath,
  });
  const result = await api.validateV6Snapshot(targetSnapshotRoot, { mysqlSnapshotRoot });

  assert.equal(manifest.snapshotId, 'guanyijia-demo-content-v6-20260826');
  assert.equal(manifest.generationRuns.length, 5);
  assert.equal(result.reviews.length, 5);
  for (const review of result.reviews) {
    assert.deepEqual(Object.keys(review.sections), standardSectionOrder.map(({ key }) => key));
    assert.deepEqual(
      review.standardSections.map(({ sectionId, heading }) => ({ sectionId, heading })),
      standardSectionOrder.map(({ key, heading }) => ({ sectionId: key, heading })),
      `${review.sourceId} retains an explicit shared nine-section contract`,
    );
    assert.equal(
      review.standardSections.every((section) => section.purpose && section.markdownAnchor && Array.isArray(section.claimIds)),
      true,
      `${review.sourceId} exposes reader-facing section purpose, anchor and claims`,
    );
    assert.deepEqual(
      [...new Set(review.claims.map((claim) => claim.section))].sort((left, right) => left - right),
      standardSectionOrder.map((_, index) => index + 1),
    );
    assert.equal(review.traceLinks.length, review.claims.length);
    assert.equal(new Set(review.traceLinks.map((trace) => trace.markdownAnchor)).size, review.traceLinks.length);
  }
  const github = result.reviews.find((review) => review.sourceId === 'guanyijia_github');
  assert.deepEqual(github.coverage, {
    trackedFileCount: 719,
    selectedFileCount: 32,
    uniqueSourceLineCount: 1003,
    evidenceCount: 69,
    claimCount: 43,
  });
  assert.equal(github.claims.length, 43);
  assert.equal(github.evidence.length, 69);
  const mysql = result.reviews.find((review) => review.sourceId === 'guanyijia_mysql');
  assert.equal(mysql.coverage.tables.length, 30);
  assert.equal(mysql.coverage.procedures.length, 6);
  const tableClaims = mysql.claims.filter((claim) => claim.claimId.startsWith('v6-mysql-table-'));
  const procedureClaims = mysql.claims.filter((claim) => claim.claimId.startsWith('v6-mysql-procedure-'));
  assert.equal(tableClaims.length, 30, 'each selected table is separately readable and expandable');
  assert.equal(procedureClaims.length, 6, 'each selected extension procedure remains separately reviewable');
  assert.deepEqual(
    tableClaims.map((claim) => claim.evidenceRefs[0]).sort(),
    selectedTables.map((name) => `mysql:table:${name}`).sort(),
  );
  assert.deepEqual(
    procedureClaims.map((claim) => claim.evidenceRefs[0]).sort(),
    selectedProcedures.map((name) => `mysql:procedure:${name}`).sort(),
  );
  assert.equal(tableClaims.every((claim) => /冻结 DDL/u.test(claim.statement)), true);
  assert.equal(procedureClaims.every((claim) => /当前部署扩展过程/u.test(claim.title)), true);
  assert.deepEqual(await readFile(join(sourceSnapshotRoot, 'manifest.json')), v5ManifestBefore);

  await assert.rejects(
    api.freezeV6Snapshot({ sourceSnapshotRoot, mysqlSnapshotRoot, targetSnapshotRoot, candidatePath }),
    /already exists|append-only/iu,
  );
});
