import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';

let generator;
let generatorError;
try {
  generator = await import('./guanyijia-demo-content-v5-generate.mjs');
} catch (error) {
  generatorError = error;
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value, 'utf8').digest('hex')}`;
}

async function writeText(root, path, content) {
  const target = join(root, path);
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, content, 'utf8');
}

test('selects native source windows and rejects a claim that cites an unknown window', async (t) => {
  if (generatorError) throw new Error(`V5 generation module is unavailable: ${generatorError.message}`);
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-v5-github-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const path = 'jshERP-boot/src/main/java/com/jsh/erp/service/StockService.java';
  const content = [
    'package com.jsh.erp.service;',
    'public final class StockService {',
    '  public boolean getMinusStockFlag() {',
    '    return true;',
    '  }',
    '}',
    '',
  ].join('\n');
  await writeText(root, `sources/github/source/${path}`, content);
  const catalog = await generator.buildGitHubEvidenceCatalog({
    root,
    selection: [{
      section: 6,
      kind: 'CONFIGURATION',
      path,
      matcher: 'getMinusStockFlag',
      before: 1,
      after: 3,
      title: '负库存配置读取',
    }],
    index: [{ path, sha256: sha256(content), lineCount: 6, textEncoding: 'UTF-8' }],
  });
  assert.equal(catalog.evidence.length, 1);
  assert.match(catalog.evidence[0].excerpt, /getMinusStockFlag/u);

  assert.throws(() => generator.validateGithubReviewProposal({
    schemaVersion: 1,
    claims: [{
      claimId: 'github-v5-001', section: 6, kind: 'CONFIGURATION',
      title: '负库存配置读取', statement: '代码包含读取负库存配置的方法。',
      evidenceRefs: ['github:v5:missing'], focusIdentifiers: [],
    }],
  }, catalog), /unknown evidence/u);
});

test('never includes the terminal empty line of a frozen text file in a source window', async (t) => {
  if (generatorError) throw new Error(`V5 generation module is unavailable: ${generatorError.message}`);
  const root = await mkdtemp(join(tmpdir(), 'guanyijia-v5-terminal-line-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const path = 'jshERP-boot/src/main/resources/mapper_xml/DepotHeadMapperEx.xml';
  const content = [
    '<mapper>',
    '  <select id="lateQuery">',
    '    select * from jsh_depot_head',
    '  </select>',
    '</mapper>',
    '',
  ].join('\n');
  await writeText(root, `sources/github/source/${path}`, content);
  const catalog = await generator.buildGitHubEvidenceCatalog({
    root,
    selection: [{
      section: 8,
      kind: 'QUERY',
      path,
      matcher: '</mapper>',
      before: 1,
      after: 3,
      title: '文件末尾查询',
    }],
    index: [{ path, sha256: sha256(content), lineCount: 5, textEncoding: 'UTF-8' }],
  });

  assert.equal(catalog.evidence[0].endLine, 5);
});

test('runs the one permitted Codex pass without loading local MCP plugins', async () => {
  if (generatorError) throw new Error(`V5 generation module is unavailable: ${generatorError.message}`);
  const invocation = generator.codexInvocationArgs('/private/tmp/guanyijia-v5-output.json');
  assert.equal(invocation[0], 'exec');
  assert.ok(invocation.includes('--ephemeral'));
  assert.ok(invocation.includes('--ignore-user-config'));
  assert.equal(invocation[invocation.indexOf('-m') + 1], 'gpt-5.6-luna');
  assert.equal(invocation[invocation.indexOf('-s') + 1], 'read-only');
  assert.equal(invocation[invocation.indexOf('--output-last-message') + 1], '/private/tmp/guanyijia-v5-output.json');
});

test('declares a concrete JSON type for every constrained response property', async () => {
  const schema = JSON.parse(await readFile(new URL('./schemas/guanyijia-github-v5-review.schema.json', import.meta.url), 'utf8'));
  const visit = (node, path = '$') => {
    if (!node || typeof node !== 'object') return;
    if (node.properties) {
      for (const [key, property] of Object.entries(node.properties)) {
        assert.equal(typeof property.type, 'string', `${path}.properties.${key} must declare type`);
        visit(property, `${path}.properties.${key}`);
      }
    }
    if (node.items) visit(node.items, `${path}.items`);
  };
  visit(schema);
});
