import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import test from 'node:test';

const runner = resolve('scripts/run-cp8-playwright.mjs');
const harnessOutput = resolve('dist/cp8');

function execute(mode, onOutput, extraEnvironment = {}) {
  return new Promise((resolveRun, reject) => {
    const child = spawn(process.execPath, [runner], {
      cwd: process.cwd(),
      env: { ...process.env, CP8_RUNNER_SELF_TEST: mode, ...extraEnvironment },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = '';
    const collect = (chunk) => {
      output += chunk.toString();
      onOutput?.(child, output);
    };
    child.stdout.on('data', collect);
    child.stderr.on('data', collect);
    child.once('error', reject);
    child.once('exit', (code, signal) => resolveRun({ code, signal, output }));
  });
}

async function seedHarnessOutput() {
  await mkdir(harnessOutput, { recursive: true });
  await writeFile(resolve(harnessOutput, 'sentinel.txt'), 'test-only');
}

test('nonzero child 也会在 finally 探测5202并删除故障harness', async () => {
  await seedHarnessOutput();
  const result = await execute('nonzero');
  assert.equal(result.code, 7);
  assert.match(result.output, /\[cp8-port\] finish: 127\.0\.0\.1:5202 closed/u);
  assert.equal(existsSync(harnessOutput), false);
});

test('child signal exit 不跳过 finally port probe', async () => {
  const result = await execute('child-signal');
  assert.equal(result.code, 143);
  assert.match(result.output, /\[cp8-port\] finish: 127\.0\.0\.1:5202 closed/u);
});

test('runner SIGTERM 只走同一 cleanup path 并等待owned child退出', async () => {
  let signalled = false;
  const result = await execute('wait', (child, output) => {
    if (!signalled && output.includes('[cp8-port] start:')) {
      signalled = true;
      child.kill('SIGTERM');
    }
  });
  assert.equal(result.code, 143);
  assert.match(result.output, /\[cp8-port\] finish: 127\.0\.0\.1:5202 closed/u);
  assert.equal((result.output.match(/\[cp8-port\] finish:/gu) ?? []).length, 1);
});

test('启动前发现unknown listener只拒绝运行，不终止其他进程', async () => {
  const result = await execute('nonzero', undefined, { CP8_RUNNER_SELF_TEST_PORT_STATE: 'occupied' });
  assert.equal(result.code, 1);
  assert.match(result.output, /start: .* is occupied; unknown listener was not killed/u);
  assert.match(result.output, /finish: .* still occupied; unknown listener was not killed/u);
  assert.doesNotMatch(result.output, /SIGKILL|terminated unknown/u);
});
