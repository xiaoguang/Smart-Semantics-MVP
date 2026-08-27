import assert from 'node:assert/strict';
import { lstat, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

let generatorModule;
let generatorModuleError;
try {
  generatorModule = await import('./guanyijia-demo-content-generate.mjs');
} catch (error) {
  generatorModuleError = error;
}

const documentPaths = [
  'sources/official/系统边界、基础资料与角色.md',
  'sources/official/采购销售与库存业务流程.md',
  'sources/official/财务报表、状态与审核口径.md',
  'sources/policy/基础资料与数据权限制度.md',
  'sources/policy/采购销售与退货管理制度.md',
  'sources/policy/库存批次序列号与负库存制度.md',
  'sources/policy/财务结算欠款与账户制度.md',
  'sources/policy/单据审核状态与统计时点制度.md',
  'sources/terminology/术语图说明.md',
];

function normalizer() {
  if (generatorModuleError) throw new Error(`generation implementation module is unavailable: ${generatorModuleError.message}`);
  assert.equal(typeof generatorModule.normalizeDemoContentProposal, 'function');
  return generatorModule.normalizeDemoContentProposal;
}

function validProposal() {
  return {
    schemaVersion: 1,
    documents: documentPaths.map((path, index) => ({
      path,
      content: `# 文档${index + 1}\n\n<!-- section-id: document-${index + 1} -->\n\n${'演示资料需要使用完整段落描述业务范围、规则、例外、职责和操作后果。'.repeat(8)}`,
    })),
    graph: { schemaVersion: 1, graphId: 'demo', terms: [], relations: [] },
  };
}

function findConstSchemasWithoutType(value, path = '$') {
  if (!value || typeof value !== 'object') return [];
  const failures = [];
  if (Object.hasOwn(value, 'const') && typeof value.type !== 'string') failures.push(path);
  for (const [key, child] of Object.entries(value)) {
    failures.push(...findConstSchemasWithoutType(child, `${path}.${key}`));
  }
  return failures;
}

test('declares an explicit JSON type for every structured-output const schema', async () => {
  const schemaUrl = new URL('./schemas/guanyijia-demo-content.schema.json', import.meta.url);
  const schema = JSON.parse(await readFile(schemaUrl, 'utf8'));
  assert.deepEqual(findConstSchemasWithoutType(schema), []);
});

test('normalizes exactly the nine generated Markdown artifacts and one terminology graph', () => {
  const result = normalizer()(validProposal());
  assert.deepEqual(result.documents.map((document) => document.path), documentPaths);
  assert.equal(result.graph.graphId, 'demo');
});

test('rejects a proposal that is missing a document, duplicates a path, or adds an unplanned artifact', () => {
  const missing = validProposal();
  missing.documents.pop();
  assert.throws(() => normalizer()(missing), /documents/u);

  const duplicate = validProposal();
  duplicate.documents[8].path = duplicate.documents[0].path;
  assert.throws(() => normalizer()(duplicate), /documents/u);

  const extra = validProposal();
  extra.documents.push({ path: 'sources/official/额外资料.md', content: '# 额外' });
  assert.throws(() => normalizer()(extra), /documents/u);
});

test('enforces generated document length locally instead of relying on a model schema keyword', () => {
  const tooShort = validProposal();
  tooShort.documents[0].content = '# 过短\n\n<!-- section-id: too-short -->';
  assert.throws(() => normalizer()(tooShort), /documents/u);
});

test('recognizes a ChatGPT Codex login message whether the CLI writes it to stdout or stderr', () => {
  if (generatorModuleError) throw new Error(`generation implementation module is unavailable: ${generatorModuleError.message}`);
  assert.equal(typeof generatorModule.hasChatGptLogin, 'function');
  assert.equal(generatorModule.hasChatGptLogin({ stdout: '', stderr: 'Logged in using ChatGPT\n' }), true);
  assert.equal(generatorModule.hasChatGptLogin({ stdout: 'Logged in using ChatGPT\n', stderr: '' }), true);
  assert.equal(generatorModule.hasChatGptLogin({ stdout: '', stderr: 'Not logged in\n' }), false);
});

test('uses one Luna high ChatGPT-session command with the installed read-only CLI contract and no API fallback', () => {
  if (generatorModuleError) throw new Error(`generation implementation module is unavailable: ${generatorModuleError.message}`);
  assert.deepEqual(generatorModule.codexGenerationArguments, [
    'exec', '-m', 'gpt-5.6-luna', '-c', 'model_reasoning_effort="high"', '-s', 'read-only',
    '--output-schema', new URL('./schemas/guanyijia-demo-content.schema.json', import.meta.url).pathname,
  ]);
  assert.equal(generatorModule.codexGenerationArguments.some((value) => value.includes('API')), false);
});

test('reserves a new append-only v4 publication target while retaining v3 as its source material', () => {
  if (generatorModuleError) throw new Error(`generation implementation module is unavailable: ${generatorModuleError.message}`);
  assert.deepEqual(generatorModule.contentGenerationConfiguration, {
    sourceSnapshotId: 'guanyijia-demo-content-v3-20260826',
    targetSnapshotId: 'guanyijia-demo-content-v4-20260826',
    promptVersion: 'guanyijia-demo-content-v4-copy-audit',
  });
});

test('passes a generation prompt through stdin instead of exposing it as a command argument', async () => {
  if (generatorModuleError) throw new Error(`generation implementation module is unavailable: ${generatorModuleError.message}`);
  assert.equal(typeof generatorModule.runCommandWithPrompt, 'function');
  const result = await generatorModule.runCommandWithPrompt('sh', ['-c', 'cat'], 'frozen-input', {
    cwd: process.cwd(),
    env: process.env,
    maxBuffer: 1024,
  });
  assert.equal(result.stdout, 'frozen-input');
  assert.equal(result.stderr, '');
});

test('stages the complete GitHub source tree before generation without creating the successor snapshot', async () => {
  if (generatorModuleError) throw new Error(`generation implementation module is unavailable: ${generatorModuleError.message}`);
  assert.equal(typeof generatorModule.createGenerationStage, 'function');
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-demo-content-stage-test-'));
  const sourceRoot = join(root, 'source');
  const targetRoot = join(root, 'snapshots', 'guanyijia-demo-content-v3-test');
  let stagingRoot;
  try {
    await mkdir(join(sourceRoot, 'sources/github/source/example'), { recursive: true });
    await writeFile(join(sourceRoot, 'sources/github/source/example/Service.java'), 'final class Service {}\n', 'utf8');

    stagingRoot = await generatorModule.createGenerationStage(sourceRoot, targetRoot);
    assert.equal(await readFile(join(stagingRoot, 'sources/github/source/example/Service.java'), 'utf8'), 'final class Service {}\n');
    await assert.rejects(lstat(targetRoot), { code: 'ENOENT' }, 'the immutable successor must not exist before validation and publication');
  } finally {
    if (stagingRoot) await rm(stagingRoot, { recursive: true, force: true });
    await rm(root, { recursive: true, force: true });
  }
});

test('recognizes an npm-relative script path as a direct maintenance invocation', () => {
  if (generatorModuleError) throw new Error(`generation implementation module is unavailable: ${generatorModuleError.message}`);
  assert.equal(generatorModule.isDirectInvocation(['node', 'scripts/evidence/guanyijia-demo-content-generate.mjs', '--generate']), true);
  assert.equal(generatorModule.isDirectInvocation(['node', 'scripts/evidence/not-this-script.mjs', '--generate']), false);
});
