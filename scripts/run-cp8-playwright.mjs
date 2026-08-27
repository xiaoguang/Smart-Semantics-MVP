import { spawn } from 'node:child_process';
import { rm } from 'node:fs/promises';
import net from 'node:net';
import { resolve } from 'node:path';
import process from 'node:process';

const host = '127.0.0.1';
const port = 5202;
const specs = process.argv.slice(2);
const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const npxCommand = process.platform === 'win32' ? 'npx.cmd' : 'npx';
const harnessOutput = resolve(process.cwd(), 'dist/cp8');
let activeChild;
let activeChildSettled = Promise.resolve();
let requestedSignal;

function portListening() {
  if (process.env.CP8_RUNNER_SELF_TEST_PORT_STATE === 'occupied') return Promise.resolve(true);
  return new Promise((resolveListening) => {
    const socket = net.createConnection({ host, port });
    const finish = (listening) => {
      socket.removeAllListeners();
      socket.destroy();
      resolveListening(listening);
    };
    socket.setTimeout(400);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
  });
}

function run(command, args) {
  if (requestedSignal) return Promise.resolve({
    code: requestedSignal === 'SIGINT' ? 130 : 143,
    signal: requestedSignal,
  });
  activeChildSettled = new Promise((resolveRun, reject) => {
    const child = spawn(command, args, {
      cwd: process.cwd(),
      stdio: 'inherit',
      detached: process.platform !== 'win32',
    });
    activeChild = child;
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (activeChild === child) activeChild = undefined;
      resolveRun({ code: code ?? (signal === 'SIGINT' ? 130 : signal === 'SIGTERM' ? 143 : 1), signal });
    });
  });
  return activeChildSettled;
}

async function requirePortClosed(label) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (!await portListening()) {
      process.stdout.write(`[cp8-port] ${label}: ${host}:${port} closed\n`);
      return true;
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 100));
  }
  process.stderr.write(`[cp8-port] ${label}: ${host}:${port} still occupied; unknown listener was not killed\n`);
  return false;
}

function terminateOwnedChild(signal = 'SIGTERM') {
  const child = activeChild;
  if (!child?.pid) return;
  try {
    if (process.platform === 'win32') child.kill(signal);
    else process.kill(-child.pid, signal);
  } catch (cause) {
    if (cause?.code !== 'ESRCH') throw cause;
  }
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    if (requestedSignal) return;
    requestedSignal = signal;
    terminateOwnedChild(signal);
  });
}

function selfTestCommand() {
  const mode = process.env.CP8_RUNNER_SELF_TEST;
  if (mode === 'nonzero') return [process.execPath, ['-e', 'process.exit(7)']];
  if (mode === 'child-signal') return [process.execPath, ['-e', "process.kill(process.pid, 'SIGTERM')"]];
  if (mode === 'wait') return [process.execPath, ['-e', 'setInterval(() => {}, 1000)']];
  return null;
}

async function main() {
  let exitCode = 1;
  let finishClosed = false;
  try {
    if (!specs.length && !process.env.CP8_RUNNER_SELF_TEST) {
      process.stderr.write('CP8 Playwright runner requires at least one explicit spec\n');
      return 2;
    }
    if (await portListening()) {
      process.stderr.write(`[cp8-port] start: ${host}:${port} is occupied; unknown listener was not killed\n`);
      return 1;
    }
    process.stdout.write(`[cp8-port] start: ${host}:${port} closed\n`);
    await rm(harnessOutput, { recursive: true, force: true });

    const selfTest = selfTestCommand();
    if (selfTest) {
      ({ code: exitCode } = await run(selfTest[0], selfTest[1]));
    } else {
      ({ code: exitCode } = await run(npmCommand, ['run', 'build']));
      if (exitCode === 0 && !requestedSignal) {
        ({ code: exitCode } = await run(npxCommand, ['vite', 'build', '--config', 'tests/e2e/vite.cp8.config.ts']));
      }
      if (exitCode === 0 && !requestedSignal) {
        ({ code: exitCode } = await run(npxCommand, ['playwright', 'test', ...specs]));
      }
    }
    return requestedSignal === 'SIGINT' ? 130 : requestedSignal === 'SIGTERM' ? 143 : exitCode;
  } catch (cause) {
    process.stderr.write(`${cause instanceof Error ? cause.stack ?? cause.message : String(cause)}\n`);
    return requestedSignal === 'SIGINT' ? 130 : requestedSignal === 'SIGTERM' ? 143 : 1;
  } finally {
    terminateOwnedChild(requestedSignal ?? 'SIGTERM');
    await Promise.race([
      activeChildSettled.catch(() => undefined),
      new Promise((resolveWait) => setTimeout(resolveWait, 2_000)),
    ]);
    await rm(harnessOutput, { recursive: true, force: true });
    finishClosed = await requirePortClosed('finish');
    if (!finishClosed) process.exitCode = 1;
  }
}

const exitCode = await main();
if (!process.exitCode) process.exitCode = exitCode;
