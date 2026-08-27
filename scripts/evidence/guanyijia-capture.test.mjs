import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import nodePath from 'node:path';
import test from 'node:test';

const prototypeRoot = '/work/linguan-prototype-v2';
const privateRoot = '/private/guanyijia-evidence';
const manifestPath = `${privateRoot}/manifests/20260820T000000Z-request.json`;
const codeupRemote = 'git@codeup.aliyun.com:guanyijia/private-evidence.git';
const fixedCommit = 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1';
const canonicalPrivateRoot = '/Users/yexiaoguang/Documents/guanyijia-evidence-private';
const canonicalManifestPath = `${canonicalPrivateRoot}/manifests/20260820T000000Z-request.json`;
const canonicalRemote = 'git@codeup.aliyun.com:guanyijia/evidence-private.git';
const canonicalManifest = {
  schemaVersion: 1,
  kind: 'CAPTURE_REQUEST',
  metadataOnly: true,
  privateRepositoryRoot: canonicalPrivateRoot,
  privateRemote: canonicalRemote,
  gitCommit: fixedCommit,
  manifestPath: canonicalManifestPath,
};
const canonicalManifestBytes = '{"schemaVersion":1,"kind":"CAPTURE_REQUEST","metadataOnly":true,"privateRepositoryRoot":"/Users/yexiaoguang/Documents/guanyijia-evidence-private","privateRemote":"git@codeup.aliyun.com:guanyijia/evidence-private.git","gitCommit":"b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1","manifestPath":"/Users/yexiaoguang/Documents/guanyijia-evidence-private/manifests/20260820T000000Z-request.json"}';
const canonicalManifestSha256 = createHash('sha256').update(canonicalManifestBytes, 'utf8').digest('hex');
const fakeSecretToken = 'FAKE_CAPTURE_SECRET_TOKEN_9f4e7d';
const codeupAttestationDigest = `sha256:${'1'.repeat(64)}`;
const lfsAttestationDigest = `sha256:${'2'.repeat(64)}`;
const strictRequestPayloadBytes = `{"codeupAttestationDigest":"${codeupAttestationDigest}","gitCommit":"${fixedCommit}","kind":"CAPTURE_REQUEST","lfsAttestationDigest":"${lfsAttestationDigest}","manifestPath":"${canonicalManifestPath}","metadataOnly":true,"privateRemote":"${canonicalRemote}","privateRepositoryRoot":"${canonicalPrivateRoot}","schemaVersion":1}\n`;
const strictRequestDigest = `sha256:${createHash('sha256').update(strictRequestPayloadBytes, 'utf8').digest('hex')}`;
const strictManifestBytes = `{"codeupAttestationDigest":"${codeupAttestationDigest}","gitCommit":"${fixedCommit}","kind":"CAPTURE_REQUEST","lfsAttestationDigest":"${lfsAttestationDigest}","manifestPath":"${canonicalManifestPath}","metadataOnly":true,"privateRemote":"${canonicalRemote}","privateRepositoryRoot":"${canonicalPrivateRoot}","requestDigest":"${strictRequestDigest}","schemaVersion":1}\n`;
const strictManifestDigest = `sha256:${createHash('sha256').update(strictManifestBytes, 'utf8').digest('hex')}`;
const changedCodeupAttestationDigest = `sha256:${'9'.repeat(64)}`;
const changedStrictRequestPayloadBytes = strictRequestPayloadBytes.replace(codeupAttestationDigest, changedCodeupAttestationDigest);
const changedStrictRequestDigest = `sha256:${createHash('sha256').update(changedStrictRequestPayloadBytes, 'utf8').digest('hex')}`;
const changedStrictManifestBytes = strictManifestBytes
  .replace(codeupAttestationDigest, changedCodeupAttestationDigest)
  .replace(strictRequestDigest, changedStrictRequestDigest);
const changedStrictManifestDigest = `sha256:${createHash('sha256').update(changedStrictManifestBytes, 'utf8').digest('hex')}`;
const approvedFilterConfigValues = {
  'filter.lfs.clean': 'git-lfs clean -- %f',
  'filter.lfs.smudge': 'git-lfs smudge -- %f',
  'filter.lfs.process': 'git-lfs filter-process',
  'filter.lfs.required': 'true',
};
const approvedFilterConfigBytes = `${Object.entries(approvedFilterConfigValues)
  .sort(([left], [right]) => left.localeCompare(right))
  .map(([key, value]) => `${key}=${value}`)
  .join('\n')}\n`;
const approvedGitattributesBytes = 'preflight/lfs-sentinel.bin filter=lfs diff=lfs merge=lfs -text\n';
const approvedFilterConfigSha256 = `sha256:${createHash('sha256').update(approvedFilterConfigBytes, 'utf8').digest('hex')}`;
const approvedGitattributesSha256 = `sha256:${createHash('sha256').update(approvedGitattributesBytes, 'utf8').digest('hex')}`;
const strictManifest = {
  schemaVersion: 1,
  kind: 'CAPTURE_REQUEST',
  metadataOnly: true,
  privateRepositoryRoot: canonicalPrivateRoot,
  privateRemote: canonicalRemote,
  gitCommit: fixedCommit,
  manifestPath: canonicalManifestPath,
  codeupAttestationDigest,
  lfsAttestationDigest,
  requestDigest: strictRequestDigest,
};

let captureModule;
let captureModuleError;
try {
  captureModule = await import('./guanyijia-capture.mjs');
} catch (error) {
  captureModuleError = error;
}

function runner() {
  if (captureModuleError) throw new Error('capture implementation module is unavailable');
  assert.equal(typeof captureModule.runCapturePreflight, 'function');
  return captureModule.runCapturePreflight;
}

function validArgv() {
  return [
    '--authorize-private-capture',
    '--private-repository', privateRoot,
    '--private-remote', codeupRemote,
    '--git-commit', fixedCommit,
    '--manifest', manifestPath,
  ];
}

function makeHarness(overrides = {}) {
  const counters = {
    readerOpens: 0,
    manifestWrites: 0,
    writerPreflights: 0,
    writerWriteCalls: 0,
    writerFinalizations: 0,
    requestAppearances: 0,
    partialFiles: 0,
    repositoryInspections: [],
    writerPreflightArguments: [],
    writerWriteArguments: [],
  };
  const writes = [];
  const repositoryState = {
    status: 'READY',
    isPrivateGitRepository: true,
    headCommit: fixedCommit,
    ...(overrides.repositoryState ?? {}),
  };
  const adapters = {
    process: {
      cwd: () => prototypeRoot,
    },
    path: nodePath,
    gitLfs: {
      isAvailable: async () => {
        if (overrides.gitLfsError) throw overrides.gitLfsError;
        return overrides.gitLfsAvailable ?? true;
      },
    },
    filesystem: {
      realpath: async (target) => overrides.realpaths?.[target] ?? target,
      lstat: async (target) => ({
        isSymbolicLink: () => overrides.symlinkPaths?.includes(target) ?? false,
        isDirectory: () => target === privateRoot || target === canonicalPrivateRoot || target === prototypeRoot || target === '/work' || target.endsWith('/manifests'),
        isFile: () => false,
      }),
    },
    repository: {
      inspect: async (...args) => {
        counters.repositoryInspections.push(args);
        return repositoryState;
      },
    },
    writer: {
      preflight: async (input) => {
        counters.writerPreflightArguments.push(structuredClone(input));
        counters.writerPreflights += 1;
        if (overrides.writerPreflightError) throw overrides.writerPreflightError;
        return overrides.writerPreflightResult ?? { status: 'READY' };
      },
      writeManifest: async (input) => {
        counters.writerWriteCalls += 1;
        counters.writerWriteArguments.push(structuredClone(input));
        if (overrides.writeManifestError) throw overrides.writeManifestError;
        if (overrides.writeManifestSuccessResult) {
          counters.manifestWrites += 1;
          counters.writerFinalizations += 1;
          counters.requestAppearances += 1;
          writes.push({ manifestPath: input.manifestPath, manifest: structuredClone(input.manifest) });
          return overrides.writeManifestSuccessResult;
        }
        if (overrides.writeManifestResult) return overrides.writeManifestResult;
        counters.manifestWrites += 1;
        counters.writerFinalizations += 1;
        counters.requestAppearances += 1;
        writes.push({ manifestPath: input.manifestPath, manifest: structuredClone(input.manifest) });
        return { status: 'WRITTEN' };
      },
    },
    readers: {
      mysql: { open: async () => { counters.readerOpens += 1; } },
      repository: { open: async () => { counters.readerOpens += 1; } },
      officialDocuments: { open: async () => { counters.readerOpens += 1; } },
    },
  };
  return { adapters, counters, writes };
}

function canonicalRepositoryState(overrides = {}) {
  return {
    status: 'READY',
    isPrivateGitRepository: true,
    headCommit: fixedCommit,
    canonicalRoot: canonicalPrivateRoot,
    origin: canonicalRemote,
    privateRepositoryAttestation: {
      status: 'APPROVED',
      visibility: 'PRIVATE',
      repositoryRoot: canonicalPrivateRoot,
      origin: canonicalRemote,
      lfsWriteAllowed: true,
    },
    repoLocalLfs: {
      status: 'READY',
      repositoryRoot: canonicalPrivateRoot,
      filtersConfigured: true,
      sentinelVerified: true,
    },
    ...overrides,
  };
}

function makeSecureHarness(overrides = {}) {
  return makeHarness({
    ...overrides,
    repositoryState: canonicalRepositoryState(overrides.repositoryState),
  });
}

function strictRepositoryState(overrides = {}) {
  return {
    ...canonicalRepositoryState(),
    codeupAttestationDigest,
    lfsAttestationDigest,
    ...overrides,
  };
}

function makeStrictHarness(overrides = {}) {
  return makeHarness({
    ...overrides,
    repositoryState: strictRepositoryState(overrides.repositoryState),
  });
}

function secureArgv({ root = canonicalPrivateRoot, remote = canonicalRemote, commit = fixedCommit } = {}) {
  return [
    '--authorize-private-capture',
    '--private-repository', root,
    '--private-remote', remote,
    '--git-commit', commit,
    '--manifest', `${root}/manifests/20260820T000000Z-request.json`,
  ];
}

function productionCliAdapters(harness) {
  if (captureModuleError) throw new Error('capture implementation module is unavailable');
  assert.equal(typeof captureModule.createCliAdapters, 'function', 'production CLI adapter factory must be exported');
  return captureModule.createCliAdapters({
    prototypeRoot,
    expected: {
      privateRepositoryRoot: canonicalPrivateRoot,
      privateRemote: canonicalRemote,
      gitCommit: fixedCommit,
    },
    adapters: harness.adapters,
  });
}

function productionLocalAdapters(dependencies) {
  if (captureModuleError) throw new Error('capture implementation module is unavailable');
  assert.equal(typeof captureModule.createProductionLocalCaptureAdapters, 'function');
  const adapters = captureModule.createProductionLocalCaptureAdapters(dependencies);
  assert.equal(adapters.filesystem, dependencies.filesystem, 'production adapters must retain the injected filesystem');
  return adapters;
}

const inspectorExpectation = {
  privateRepositoryRoot: canonicalPrivateRoot,
  privateRemote: canonicalRemote,
  gitCommit: fixedCommit,
};

function attestedRecord(record) {
  return {
    ...record,
    attestationSha256: `sha256:${createHash('sha256').update(JSON.stringify(record), 'utf8').digest('hex')}`,
  };
}

function canonicalGitConfigKey(key) {
  const firstDot = key.indexOf('.');
  const lastDot = key.lastIndexOf('.');
  if (firstDot < 0) return key.toLowerCase();
  const section = key.slice(0, firstDot).toLowerCase();
  if (firstDot === lastDot) return `${section}.${key.slice(lastDot + 1).toLowerCase()}`;
  return `${section}.${key.slice(firstDot + 1, lastDot)}.${key.slice(lastDot + 1).toLowerCase()}`;
}

function makeInspectorFixture(overrides = {}) {
  const calls = [];
  const reads = [];
  const sentinelOid = `sha256:${'3'.repeat(64)}`;
  const codeup = attestedRecord({
    schemaVersion: 1,
    origin: canonicalRemote,
    repositoryIdentity: 'codeup:guanyijia/evidence-private',
    visibility: 'PRIVATE',
    lfsWritePermission: 'ALLOWED',
    issuedAt: '2026-08-20T00:00:00.000Z',
    expiresAt: '2099-08-20T00:00:00.000Z',
    issuer: 'CODEUP_OFFICIAL_ATTESTATION',
  });
  const lfs = attestedRecord({
    schemaVersion: 1,
    origin: canonicalRemote,
    repositoryIdentity: 'codeup:guanyijia/evidence-private',
    filterConfigSha256: approvedFilterConfigSha256,
    gitattributesSha256: approvedGitattributesSha256,
    sentinelPath: 'preflight/lfs-sentinel.bin',
    sentinelOid,
  });
  const files = new Map([
    [`${canonicalPrivateRoot}/preflight/codeup-private-attestation.json`, new TextEncoder().encode(JSON.stringify(overrides.codeup ?? codeup))],
    [`${canonicalPrivateRoot}/preflight/lfs-local-attestation.json`, new TextEncoder().encode(JSON.stringify(overrides.lfs ?? lfs))],
    [`${canonicalPrivateRoot}/preflight/lfs-sentinel.bin`, new TextEncoder().encode('version https://git-lfs.github.com/spec/v1\n' + `oid sha256:${'3'.repeat(64)}\nsize 17\n`)],
    [`${canonicalPrivateRoot}/.gitattributes`, new TextEncoder().encode(overrides.gitattributesBytes ?? approvedGitattributesBytes)],
  ]);
  const stat = (kind) => ({
    isSymbolicLink: () => kind === 'symlink',
    isDirectory: () => kind === 'directory',
    isFile: () => kind === 'file',
  });
  const filesystem = {
    lstat: async (target) => {
      if (overrides.gitStat && target === `${canonicalPrivateRoot}/.git`) return stat(overrides.gitStat);
      if (target === canonicalPrivateRoot || target === `${canonicalPrivateRoot}/.git` || target === `${canonicalPrivateRoot}/preflight`) return stat('directory');
      if (target.endsWith('/preflight/codeup-private-attestation.json') || target.endsWith('/preflight/lfs-local-attestation.json') || target.endsWith('/preflight/lfs-sentinel.bin')) return stat('file');
      if (target === `${canonicalPrivateRoot}/.gitattributes`) return stat('file');
      if (target === `${canonicalPrivateRoot}/.lfsconfig` && overrides.lfsConfigContent !== undefined) return stat('file');
      const error = new Error('not found');
      error.code = 'ENOENT';
      throw error;
    },
    realpath: async (target) => overrides.realpaths?.[target] ?? target,
    readFile: async (target) => {
      reads.push(target);
      if (files.has(target)) return files.get(target);
      if (target === `${canonicalPrivateRoot}/.lfsconfig` && overrides.lfsConfigContent !== undefined) {
        return new TextEncoder().encode(overrides.lfsConfigContent);
      }
      const error = new Error('not found');
      error.code = 'ENOENT';
      throw error;
    },
  };
  const configValues = {
    ...approvedFilterConfigValues,
    ...overrides.configValues,
  };
  const configOverrides = { ...overrides.configOverrides };
  const extraConfigValues = { ...overrides.extraConfigValues };
  const configErrors = { ...overrides.configErrors };
  const runner = {
    run: async (input) => {
      calls.push({ executable: input.executable, args: [...input.args], cwd: input.cwd });
      assert.ok(input.executable === 'git' || input.executable === 'git-lfs', 'only fixed local git executables may run');
      assert.equal(input.cwd, canonicalPrivateRoot);
      const args = input.args;
      const key = args.join('\u0000');
      if (overrides.commandResults?.[key]) return overrides.commandResults[key];
      if (input.executable === 'git-lfs' && key === 'version') return { exitCode: 0, stdout: 'git-lfs/3.5.0' };
      if (key === `-C\u0000${canonicalPrivateRoot}\u0000rev-parse\u0000--show-toplevel`) return { exitCode: 0, stdout: canonicalPrivateRoot };
      if (key === `-C\u0000${canonicalPrivateRoot}\u0000remote`) return { exitCode: 0, stdout: 'origin\n' };
      if (key === `-C\u0000${canonicalPrivateRoot}\u0000config\u0000--local\u0000--get-all\u0000remote.origin.url`) return { exitCode: 0, stdout: canonicalRemote };
      if (key === `-C\u0000${canonicalPrivateRoot}\u0000config\u0000--local\u0000--get-all\u0000remote.origin.pushurl`) {
        return overrides.remoteOriginPushurlResult ?? { exitCode: 1, stdout: '' };
      }
      if (key === `-C\u0000${canonicalPrivateRoot}\u0000rev-parse\u0000--verify\u0000HEAD`) return { exitCode: 0, stdout: fixedCommit };
      if (args.includes('config') && args.includes('--get-regexp')) {
        if (configErrors.__regexp !== undefined) return configErrors.__regexp;
        const pattern = args.at(-1);
        const matches = Object.entries(extraConfigValues)
          .map(([configKey, value]) => [canonicalGitConfigKey(configKey), value])
          .filter(([configKey]) => new RegExp(pattern).test(configKey));
        return matches.length
          ? { exitCode: 0, stdout: `${matches.map(([configKey, value]) => `${configKey} ${value}`).join('\n')}\n` }
          : { exitCode: 1, stdout: '' };
      }
      if (args.includes('config') && args.includes('--get')) {
        const configKey = args.at(-1);
        const canonicalConfigKey = canonicalGitConfigKey(configKey);
        if (configErrors[configKey] !== undefined) return configErrors[configKey];
        if (configErrors[canonicalConfigKey] !== undefined) return configErrors[canonicalConfigKey];
        if (configOverrides[configKey] !== undefined) return { exitCode: 0, stdout: configOverrides[configKey] };
        if (configKey in configValues) return { exitCode: 0, stdout: configValues[configKey] };
        const extraConfigEntry = Object.entries(extraConfigValues).find(([rawKey]) => canonicalGitConfigKey(rawKey) === canonicalConfigKey);
        if (extraConfigEntry) return { exitCode: 0, stdout: extraConfigEntry[1] };
        if (configKey === 'lfs.url' && overrides.lfsUrlOverride !== undefined) return { exitCode: 0, stdout: overrides.lfsUrlOverride };
        if (['lfs.url', 'lfs.pushurl', 'remote.origin.lfsurl', 'remote.origin.lfspushurl', 'lfs.standalonetransferagent', 'extensions.worktreeconfig'].includes(canonicalConfigKey)) return { exitCode: 1, stdout: '' };
        if (canonicalConfigKey.startsWith('lfs.customtransfer.') || canonicalConfigKey.startsWith('url.')) return { exitCode: 1, stdout: '' };
      }
      if (args.includes('check-attr')) {
        const attributePath = 'preflight/lfs-sentinel.bin';
        return { exitCode: 0, stdout: overrides.checkAttrOutput ?? `${attributePath}\u0000filter\u0000lfs\u0000${attributePath}\u0000diff\u0000lfs\u0000${attributePath}\u0000merge\u0000lfs\u0000${attributePath}\u0000text\u0000unset\u0000` };
      }
      if (args.includes('pointer')) return { exitCode: overrides.pointerExitCode ?? 0, stdout: '' };
      if (args.includes('ls-files')) return { exitCode: 0, stdout: overrides.lfsFilesOutput ?? `${sentinelOid.slice('sha256:'.length)} * preflight/lfs-sentinel.bin\n` };
      throw new Error(`unexpected command ${key}`);
    },
  };
  const pathGuard = overrides.pathGuard ?? { revalidate: async () => undefined };
  const adapters = productionLocalAdapters({
    filesystem,
    runner,
    path: nodePath,
    pathGuard,
  });
  return { adapters, calls, reads, filesystem, runner, codeup, lfs, sentinelOid, pathGuard };
}

function makeAtomicWriterFixture({ failAtRevalidation } = {}) {
  const events = [];
  const state = {
    finalPresent: false,
    success: false,
    tempDeleted: false,
    finalUnlinks: 0,
    linkCalls: 0,
    revalidations: 0,
  };
  const target = {
    prototypeRoot,
    workspaceRoot: '/work',
    privateRepositoryRoot: canonicalPrivateRoot,
    manifestDirectory: `${canonicalPrivateRoot}/manifests`,
    manifestPath: canonicalManifestPath,
  };
  const temporaryPath = `${canonicalPrivateRoot}/manifests/.request.tmp.fake`;
  const bytes = new TextEncoder().encode(strictManifestBytes);
  const pathGuard = {
    revalidate: async () => {
      state.revalidations += 1;
      events.push('revalidate');
      if (state.revalidations === failAtRevalidation) {
        const error = new Error('unsafe path guard transition');
        error.code = 'MANIFEST_PATH_UNSAFE';
        throw error;
      }
    },
  };
  const filesystem = {
    lstat: async (path) => ({
      isSymbolicLink: () => false,
      isDirectory: () => path.endsWith('/manifests'),
      isFile: () => path === temporaryPath || path === canonicalManifestPath,
    }),
    realpath: async (path) => path,
    openExclusiveNoFollow: async (path) => {
      events.push('stage');
      assert.equal(path, temporaryPath);
      return {
        writeFile: async () => undefined,
        sync: async () => undefined,
        close: async () => undefined,
      };
    },
    link: async (existingPath, finalPath) => {
      events.push('publish');
      state.linkCalls += 1;
      assert.equal(existingPath, temporaryPath);
      assert.equal(finalPath, canonicalManifestPath);
      state.finalPresent = true;
    },
    unlink: async (path) => {
      events.push(path === canonicalManifestPath ? 'unlink-final' : 'unlink-temp');
      if (path === canonicalManifestPath) state.finalUnlinks += 1;
      if (path === temporaryPath) state.tempDeleted = true;
    },
    readFile: async (path) => {
      assert.equal(path, canonicalManifestPath);
      events.push('readback');
      return bytes;
    },
    revalidateDirectory: async () => {
      events.push('revalidate-directory');
    },
  };
  const adapters = productionLocalAdapters({
    filesystem,
    runner: { run: async () => { throw new Error('writer must not run a command'); } },
    path: nodePath,
    pathGuard,
  });
  return { adapters, target, bytes, state, events, pathGuard };
}

async function runCapture(harness, argv = validArgv(), adapters = harness.adapters, root = prototypeRoot) {
  return runner()({
    argv,
    prototypeRoot: root,
    adapters,
  });
}

async function assertBlockedWithoutEffects(harness, operation, message) {
  await assert.rejects(operation, message);
  assert.equal(harness.counters.readerOpens, 0, 'blocked capture must not open any source reader');
  assert.equal(harness.counters.manifestWrites, 0, 'blocked capture must not write a manifest');
  assert.equal(harness.counters.writerFinalizations, 0, 'blocked capture must not finalize a request');
  assert.equal(harness.counters.requestAppearances, 0, 'blocked capture must not expose a request');
  assert.equal(harness.counters.partialFiles, 0, 'blocked capture must not leave a partial file');
}

test('capture requires the exact private-capture authorization flag', async () => {
  const harness = makeHarness();
  const argv = validArgv().filter((argument) => argument !== '--authorize-private-capture');

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, argv), /authorize|private capture|授权/i);
});

test('capture rejects missing required arguments before any adapter can open or write', async () => {
  const harness = makeHarness();
  const argv = validArgv().slice(0, -2);

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, argv), /missing|required|argument|manifest|参数|缺少/i);
});

test('capture rejects relative and unknown arguments before any adapter can open or write', async () => {
  const relativeRootHarness = makeHarness();
  await assertBlockedWithoutEffects(
    relativeRootHarness,
    () => runCapture(relativeRootHarness, validArgv().map((argument) => argument === privateRoot ? 'private-evidence' : argument)),
    /absolute|private repository|绝对|私有.*根/i,
  );

  const unknownFlagHarness = makeHarness();
  await assertBlockedWithoutEffects(
    unknownFlagHarness,
    () => runCapture(unknownFlagHarness, [...validArgv(), '--unexpected']),
    /unknown|unexpected|参数|未知/i,
  );
});

test('capture rejects a private repository root inside the prototype worktree', async () => {
  const harness = makeHarness();
  const argv = validArgv().map((argument) => argument === privateRoot ? `${prototypeRoot}/private-evidence` : argument);

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, argv), /prototype|worktree|outside|外部|工作区/i);
});

test('capture rejects a manifest path that escapes the private repository root', async () => {
  const harness = makeHarness();
  const argv = validArgv().map((argument) => argument === manifestPath ? `${privateRoot}/../outside.json` : argument);

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, argv), /manifest|private root|contained|escape|越界|私有.*根/i);
});

test('capture accepts only the Codeup private SSH remote', async () => {
  const harness = makeHarness();
  const argv = validArgv().map((argument) => argument === codeupRemote ? 'git@github.com:someone/private-evidence.git' : argument);

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, argv), /Codeup|remote|SSH|远程|代码仓/i);
});

test('capture refuses to proceed when Git LFS is unavailable', async () => {
  const harness = makeHarness({ gitLfsAvailable: false });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness), /LFS|git-lfs|Git LFS/i);
});

test('capture refuses a path that is not an initialized private Git repository', async () => {
  const harness = makeHarness({ repositoryState: { isPrivateGitRepository: false } });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness), /private|Git repository|git repo|私有.*仓库/i);
});

test('capture refuses a private repository whose HEAD is not the fixed commit', async () => {
  const harness = makeHarness({ repositoryState: { headCommit: '0'.repeat(40) } });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness), /commit|revision|固定.*提交|版本/i);
});

test('capture treats a repository onblocked result as a hard preflight failure', async () => {
  const harness = makeHarness({ repositoryState: { status: 'BLOCKED', reason: 'private repository is unavailable' } });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness), /blocked|onblocked|阻断|不可用/i);
});

test('capture stops before readers or writes when writer preflight fails', async () => {
  const harness = makeStrictHarness({ writerPreflightError: new Error('writer failure') });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /writer|write|写入/i);
});

test('capture rejects partial writer preflight output without opening readers or writing', async () => {
  const harness = makeStrictHarness({ writerPreflightResult: { status: 'PARTIAL', bytes: 17 } });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /partial|writer|manifest|部分|写入/i);
});

test('capture treats a writer onblocked result as a hard preflight failure', async () => {
  const harness = makeHarness({ writerPreflightResult: { status: 'BLOCKED', reason: 'manifest target is unavailable' } });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness), /blocked|onblocked|writer|manifest|阻断|不可用/i);
});

test('secure preflight rejects a missing Git LFS adapter instead of using a legacy fail-open path', async () => {
  const harness = makeHarness();
  delete harness.adapters.gitLfs;

  await assertBlockedWithoutEffects(harness, () => runCapture(harness), /LFS|attestation|secure|preflight/i);
});

test('secure preflight rejects a repository state without required security proof fields', async () => {
  const harness = makeHarness();

  await assertBlockedWithoutEffects(harness, () => runCapture(harness), /attestation|secure|repository|proof/i);
});

test('secure preflight rejects a WRITTEN result without the required manifest digest', async () => {
  const harness = makeStrictHarness({ writeManifestResult: { status: 'WRITTEN' } });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /digest|manifest|writer/i);
});

test('production CLI adapters bind the expected root, remote, and commit and return the exact canonical result', async () => {
  const harness = makeStrictHarness({
    writeManifestSuccessResult: { status: 'WRITTEN', manifestDigest: strictManifestDigest },
  });
  const adapters = productionCliAdapters(harness);

  const result = await runCapture(harness, secureArgv(), adapters);

  assert.deepEqual(result, {
    status: 'WRITTEN',
    requestDigest: strictRequestDigest,
    manifestDigest: strictManifestDigest,
  });
  assert.equal(harness.counters.readerOpens, 0);
  assert.equal(harness.counters.manifestWrites, 1);
  assert.deepEqual(harness.counters.writerWriteArguments[0], {
    manifestPath: canonicalManifestPath,
    manifest: strictManifest,
    canonicalBytes: strictManifestBytes,
    manifestSha256: strictManifestDigest,
  });
});

test('production CLI adapters reject a root or remote that differs from the expected canonical request', async () => {
  const mismatches = [
    { label: 'root', argv: secureArgv({ root: '/Users/yexiaoguang/Documents/other-private-root' }), message: /root|canonical|repository/i },
    { label: 'remote', argv: secureArgv({ remote: 'git@codeup.aliyun.com:other/private-evidence.git' }), message: /origin|remote|expected|canonical/i },
  ];

  for (const mismatch of mismatches) {
    const harness = makeSecureHarness();
    const adapters = productionCliAdapters(harness);
    await assertBlockedWithoutEffects(
      harness,
      () => runCapture(harness, mismatch.argv, adapters),
      mismatch.message,
    );
  }
});

test('production local inspector accepts only fixed local Git/LFS command arguments and returns attestation digests', async () => {
  const fixture = makeInspectorFixture();
  assert.equal(fixture.lfs.filterConfigSha256, approvedFilterConfigSha256);
  assert.equal(fixture.lfs.gitattributesSha256, approvedGitattributesSha256);
  const state = await fixture.adapters.repository.inspect(inspectorExpectation);

  assert.equal(state.status, 'READY');
  assert.equal(state.canonicalRoot, canonicalPrivateRoot);
  assert.equal(state.origin, canonicalRemote);
  assert.equal(state.headCommit, fixedCommit);
  assert.equal(state.codeupAttestationDigest, fixture.codeup.attestationSha256);
  assert.equal(state.lfsAttestationDigest, fixture.lfs.attestationSha256);
  assert.equal(fixture.reads.includes(`${canonicalPrivateRoot}/preflight/lfs-filter-config.txt`), false, 'published preflight proof must not depend on a hidden filter-config mirror');
  for (const publishedProofPath of [
    `${canonicalPrivateRoot}/preflight/codeup-private-attestation.json`,
    `${canonicalPrivateRoot}/preflight/lfs-local-attestation.json`,
    `${canonicalPrivateRoot}/.gitattributes`,
  ]) {
    assert.equal(fixture.reads.includes(publishedProofPath), true, `published proof must be read: ${publishedProofPath}`);
  }
  assert.ok(fixture.calls.some((call) => call.executable === 'git-lfs' && call.args.join(' ') === 'version'));
  for (const call of fixture.calls) {
    assert.ok(!call.args.some((argument) => ['fetch', 'pull', 'push', 'clone', 'ls-remote', 'curl'].includes(argument)));
  }
});

test('production local inspector rejects incorrect repository-local LFS filter values', async () => {
  const fixture = makeInspectorFixture({ configValues: { 'filter.lfs.clean': 'evil-filter --secret %f' } });

  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|attestation|filter|secure/i);
});

test('production local inspector rejects a changed local filter value even when an attacker recomputes its candidate digest', async () => {
  const changedValues = {
    ...approvedFilterConfigValues,
    'filter.lfs.clean': 'evil-filter --secret %f',
  };
  const changedBytes = `${Object.entries(changedValues)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`)
    .join('\n')}\n`;
  const baseline = makeInspectorFixture();
  const fixture = makeInspectorFixture({
    configValues: changedValues,
    lfs: attestedRecord({
      ...baseline.lfs,
      filterConfigSha256: `sha256:${createHash('sha256').update(changedBytes, 'utf8').digest('hex')}`,
      attestationSha256: undefined,
    }),
  });

  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|attestation|filter|digest|secure/i);
});

test('production local inspector rejects unspecified check-attr output', async () => {
  const path = 'preflight/lfs-sentinel.bin';
  const fixture = makeInspectorFixture({ checkAttrOutput: `${path}\u0000filter\u0000unspecified\u0000${path}\u0000diff\u0000unspecified\u0000${path}\u0000merge\u0000unspecified\u0000${path}\u0000text\u0000unspecified\u0000` });

  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|attribute|attestation|sentinel/i);
});

test('production local inspector accepts real four-triple check-attr output with text unset', async () => {
  const path = 'preflight/lfs-sentinel.bin';
  const fixture = makeInspectorFixture({
    checkAttrOutput: `${path}\u0000filter\u0000lfs\u0000${path}\u0000diff\u0000lfs\u0000${path}\u0000merge\u0000lfs\u0000${path}\u0000text\u0000unset\u0000`,
  });

  const state = await fixture.adapters.repository.inspect(inspectorExpectation);

  assert.equal(state.status, 'READY');
  assert.equal(state.lfsAttestationDigest, fixture.lfs.attestationSha256);
});

test('production local inspector rejects repeated, missing, or malformed check-attr triples', async () => {
  const path = 'preflight/lfs-sentinel.bin';
  const invalidOutputs = [
    `${path}\u0000filter\u0000lfs\u0000${path}\u0000filter\u0000lfs\u0000${path}\u0000merge\u0000lfs\u0000${path}\u0000text\u0000unset\u0000`,
    `${path}\u0000filter\u0000lfs\u0000${path}\u0000diff\u0000lfs\u0000${path}\u0000text\u0000unset\u0000`,
    `${path}\u0000filter\u0000lfs\u0000${path}\u0000diff\u0000lfs\u0000${path}\u0000merge\u0000lfs\u0000${path}\u0000text\u0000unset`,
  ];

  for (const checkAttrOutput of invalidOutputs) {
    const fixture = makeInspectorFixture({ checkAttrOutput });
    await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|attribute|attestation|sentinel/i);
  }
});

test('production local inspector rejects an absent or mismatched LFS sentinel index entry', async () => {
  for (const lfsFilesOutput of ['', `${'9'.repeat(64)} * preflight/lfs-sentinel.bin\n`]) {
    const fixture = makeInspectorFixture({ lfsFilesOutput });
    await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|sentinel|OID|attestation/i);
  }
});

test('production local inspector rejects a repo-local LFS URL override', async () => {
  const fixture = makeInspectorFixture({ lfsUrlOverride: 'https://evil.example.invalid/private-lfs' });

  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|URL|attestation|remote/i);
});

test('production local inspector rejects every repo-local LFS endpoint override and .lfsconfig', async () => {
  const overrides = [
    { configOverrides: { 'lfs.url': 'https://evil.example.invalid/lfs' } },
    { configOverrides: { 'lfs.pushurl': 'https://evil.example.invalid/push' } },
    { configOverrides: { 'remote.origin.lfsurl': 'https://evil.example.invalid/remote-lfs' } },
    { configOverrides: { 'remote.origin.lfspushurl': 'https://evil.example.invalid/remote-push' } },
    { lfsConfigContent: '[lfs]\n\turl = https://evil.example.invalid/config\n' },
  ];

  for (const override of overrides) {
    const fixture = makeInspectorFixture(override);
    await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|URL|config|attestation|remote/i);
  }
});

test('production local inspector fails closed on non-absent local LFS config errors', async () => {
  const fixture = makeInspectorFixture({
    configErrors: { 'lfs.url': { exitCode: 2, stdout: '' } },
  });
  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|config|URL|attestation/i);

  const pushurlFixture = makeInspectorFixture({
    remoteOriginPushurlResult: { exitCode: 2, stdout: '' },
  });
  await assert.rejects(() => pushurlFixture.adapters.repository.inspect(inspectorExpectation), /repository|remote|pushurl|LFS|attestation/i);
});

test('production local inspector rejects a lowercase canonical url insteadOf rewrite', async () => {
  const fixture = makeInspectorFixture({
    extraConfigValues: { 'url.https://evil.example.invalid/.insteadOf': canonicalRemote },
  });
  const result = await fixture.runner.run({
    executable: 'git',
    args: ['-C', canonicalPrivateRoot, 'config', '--local', '--get-regexp', '^url\\..*\\.insteadof$'],
    cwd: canonicalPrivateRoot,
  });
  assert.equal(result.exitCode, 0);
  assert.equal(result.stdout, `url.https://evil.example.invalid/.insteadof ${canonicalRemote}\n`);
  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|URL|rewrite|config|attestation|remote/i);
});

test('production local inspector rejects a lowercase canonical url pushInsteadOf rewrite', async () => {
  const fixture = makeInspectorFixture({
    extraConfigValues: { 'url.https://evil.example.invalid/.pushInsteadOf': canonicalRemote },
  });
  const result = await fixture.runner.run({
    executable: 'git',
    args: ['-C', canonicalPrivateRoot, 'config', '--local', '--get-regexp', '^url\\..*\\.pushinsteadof$'],
    cwd: canonicalPrivateRoot,
  });
  assert.equal(result.exitCode, 0);
  assert.equal(result.stdout, `url.https://evil.example.invalid/.pushinsteadof ${canonicalRemote}\n`);
  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|URL|rewrite|config|attestation|remote/i);
});

test('production local inspector rejects custom LFS transfer-agent configuration', async () => {
  const cases = [
    { 'lfs.standalonetransferagent': 'evil-agent' },
    { 'lfs.customtransfer.evil.path': '/tmp/evil-transfer', 'lfs.customtransfer.evil.args': '--token SECRET' },
  ];
  for (const extraConfigValues of cases) {
    const fixture = makeInspectorFixture({ extraConfigValues });
    await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|transfer|config|attestation/i);
  }
});

test('production local inspector accepts the valid repository only with an exact absent worktree config', async () => {
  const fixture = makeInspectorFixture();
  const state = await fixture.adapters.repository.inspect(inspectorExpectation);

  assert.equal(state.status, 'READY');
  const worktreeProbe = fixture.calls.find((call) => call.args.at(-1) === 'extensions.worktreeconfig');
  assert.ok(worktreeProbe, 'inspector must probe the canonical lowercase worktree config key');
  assert.equal(worktreeProbe.args.at(-2), '--get');
});

test('production local inspector rejects an enabled worktree config override', async () => {
  const fixture = makeInspectorFixture({
    extraConfigValues: { 'extensions.worktreeConfig': 'true' },
  });

  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /worktree|config|LFS|attestation|repository/i);
});

test('production local inspector fails closed on a non-absent worktree config error', async () => {
  const fixture = makeInspectorFixture({
    configErrors: { 'extensions.worktreeconfig': { exitCode: 2, stdout: '' } },
  });

  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /worktree|config|LFS|attestation|repository/i);
});

test('production local inspector fails closed on non-absent rewrite or custom-transfer config command errors', async () => {
  const exactKeyError = makeInspectorFixture({
    configErrors: { 'lfs.standalonetransferagent': { exitCode: 2, stdout: '' } },
  });
  await assert.rejects(() => exactKeyError.adapters.repository.inspect(inspectorExpectation), /LFS|config|transfer|attestation/i);

  const regexpError = makeInspectorFixture({
    configErrors: { __regexp: { exitCode: 2, stdout: '' } },
  });
  await assert.rejects(() => regexpError.adapters.repository.inspect(inspectorExpectation), /LFS|config|rewrite|transfer|attestation/i);
});

test('production local inspector recomputes filter and gitattributes digests from approved bytes', async () => {
  const baseline = makeInspectorFixture();
  const cases = [
    {
      lfs: attestedRecord({
        ...baseline.lfs,
        filterConfigSha256: `sha256:${'a'.repeat(64)}`,
        attestationSha256: undefined,
      }),
    },
    {
      lfs: attestedRecord({
        ...baseline.lfs,
        gitattributesSha256: `sha256:${'b'.repeat(64)}`,
        attestationSha256: undefined,
      }),
    },
    {
      gitattributesBytes: 'preflight/lfs-sentinel.bin filter=lfs diff=lfs merge=lfs -text\n# changed\n',
    },
  ];

  for (const mismatch of cases) {
    const fixture = makeInspectorFixture(mismatch);
    await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /LFS|digest|gitattributes|filter|attestation/i);
  }
});

test('production local inspector rejects malformed or mismatched Codeup/LFS attestations', async () => {
  const baseline = makeInspectorFixture();
  const cases = [
    {
      codeup: attestedRecord({ ...baseline.codeup, schemaVersion: 2, attestationSha256: undefined }),
      message: /Codeup|schema|attestation/i,
    },
    {
      codeup: attestedRecord({ ...baseline.codeup, repositoryIdentity: 'codeup:other/private-evidence', attestationSha256: undefined }),
      message: /Codeup|identity|attestation/i,
    },
    {
      codeup: { ...baseline.codeup, attestationSha256: `sha256:${'f'.repeat(64)}` },
      message: /Codeup|digest|attestation/i,
    },
    {
      lfs: attestedRecord({ ...baseline.lfs, schemaVersion: 2, attestationSha256: undefined }),
      message: /LFS|schema|attestation/i,
    },
    {
      lfs: attestedRecord({ ...baseline.lfs, repositoryIdentity: 'codeup:other/private-evidence', attestationSha256: undefined }),
      message: /LFS|identity|attestation/i,
    },
    {
      lfs: { ...baseline.lfs, attestationSha256: `sha256:${'e'.repeat(64)}` },
      message: /LFS|digest|attestation/i,
    },
    {
      lfs: attestedRecord({ ...baseline.lfs, sentinelOid: `sha256:${'9'.repeat(64)}`, attestationSha256: undefined }),
      message: /LFS|sentinel|OID|attestation/i,
    },
  ];
  for (const malformed of cases) {
    const fixture = makeInspectorFixture(malformed);
    await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), malformed.message);
  }
});

test('production local inspector rejects a .git file or other non-directory repository indirection', async () => {
  const fixture = makeInspectorFixture({ gitStat: 'file' });

  await assert.rejects(() => fixture.adapters.repository.inspect(inspectorExpectation), /repository|git|directory|attestation/i);
});

test('secure request success uses sorted UTF-8 JSON with newline and exact attestation/request/manifest digests', async () => {
  const harness = makeStrictHarness({
    writeManifestSuccessResult: { status: 'WRITTEN', manifestDigest: strictManifestDigest },
  });
  const result = await runCapture(harness, secureArgv());

  assert.deepEqual(result, {
    status: 'WRITTEN',
    requestDigest: strictRequestDigest,
    manifestDigest: strictManifestDigest,
  });
  assert.deepEqual(harness.counters.writerWriteArguments[0], {
    manifestPath: canonicalManifestPath,
    manifest: strictManifest,
    canonicalBytes: strictManifestBytes,
    manifestSha256: strictManifestDigest,
  });
  assert.equal(strictManifestBytes.endsWith('\n'), true);
  assert.equal(Buffer.byteLength(strictManifestBytes, 'utf8'), new TextEncoder().encode(strictManifestBytes).byteLength);
});

test('changing either local attestation changes the canonical request digest and final manifest digest', async () => {
  const first = makeStrictHarness({
    writeManifestSuccessResult: { status: 'WRITTEN', manifestDigest: strictManifestDigest },
  });
  const firstResult = await runCapture(first, secureArgv());
  const second = makeStrictHarness({
    writeManifestSuccessResult: { status: 'WRITTEN', manifestDigest: changedStrictManifestDigest },
    repositoryState: {
      codeupAttestationDigest: changedCodeupAttestationDigest,
    },
  });
  const secondResult = await runCapture(second, secureArgv());

  assert.notEqual(firstResult.requestDigest, secondResult.requestDigest);
  assert.notEqual(firstResult.manifestDigest, secondResult.manifestDigest);
  assert.notEqual(first.counters.writerWriteArguments[0].canonicalBytes, second.counters.writerWriteArguments[0].canonicalBytes);
});

test('atomic writer revalidates before publish and never reports success when the parent becomes unsafe', async () => {
  const fixture = makeAtomicWriterFixture({ failAtRevalidation: 2 });

  await fixture.adapters.writer.preflight(fixture.target);
  fixture.events.length = 0;
  fixture.state.revalidations = 0;
  await assert.rejects(
    () => fixture.adapters.writer.write({
      target: fixture.target,
      canonicalJsonUtf8: fixture.bytes,
      expectedDigest: strictManifestDigest,
    }),
    /unsafe|path|blocked|partial/i,
  );
  assert.equal(fixture.events.includes('stage'), true);
  assert.equal(fixture.state.linkCalls, 0);
  assert.equal(fixture.state.finalPresent, false);
  assert.equal(fixture.state.success, false);
  assert.equal(fixture.state.finalUnlinks, 0);
  assert.equal(fixture.state.tempDeleted, true);
});

test('atomic writer revalidates before readback and never claims success or blindly cleans an unsafe final', async () => {
  const fixture = makeAtomicWriterFixture({ failAtRevalidation: 3 });

  await fixture.adapters.writer.preflight(fixture.target);
  fixture.events.length = 0;
  fixture.state.revalidations = 0;
  await assert.rejects(
    () => fixture.adapters.writer.write({
      target: fixture.target,
      canonicalJsonUtf8: fixture.bytes,
      expectedDigest: strictManifestDigest,
    }),
    /unsafe|path|blocked|digest/i,
  );
  assert.equal(fixture.events.includes('readback'), true);
  assert.equal(fixture.state.linkCalls, 1);
  assert.equal(fixture.state.finalPresent, true);
  assert.equal(fixture.state.success, false);
  assert.equal(fixture.state.finalUnlinks, 0);
});

test('atomic writer guards before staging, before publish, and after final readback', async () => {
  const success = makeAtomicWriterFixture();
  await success.adapters.writer.preflight(success.target);
  success.events.length = 0;
  success.state.revalidations = 0;
  const result = await success.adapters.writer.write({
    target: success.target,
    canonicalJsonUtf8: success.bytes,
    expectedDigest: strictManifestDigest,
  });
  assert.deepEqual(result, { status: 'WRITTEN', manifestDigest: strictManifestDigest });

  const firstStage = success.events.indexOf('stage');
  const publish = success.events.indexOf('publish');
  const readback = success.events.indexOf('readback');
  const guards = success.events.reduce((indexes, event, index) => {
    if (event === 'revalidate') indexes.push(index);
    return indexes;
  }, []);
  assert.ok(firstStage >= 0 && guards.some((index) => index < firstStage), `guard must precede staging: ${success.events.join(',')}`);
  assert.ok(publish >= 0 && guards.some((index) => index > firstStage && index < publish), `guard must precede publish: ${success.events.join(',')}`);
  assert.ok(readback >= 0 && guards.some((index) => index > readback), `final guard must follow readback: ${success.events.join(',')}`);
  assert.equal(success.state.finalUnlinks, 0);
  assert.equal(success.state.tempDeleted, true);

  const beforeStagingFailure = makeAtomicWriterFixture({ failAtRevalidation: 1 });
  await beforeStagingFailure.adapters.writer.preflight(beforeStagingFailure.target);
  beforeStagingFailure.events.length = 0;
  beforeStagingFailure.state.revalidations = 0;
  await assert.rejects(
    () => beforeStagingFailure.adapters.writer.write({
      target: beforeStagingFailure.target,
      canonicalJsonUtf8: beforeStagingFailure.bytes,
      expectedDigest: strictManifestDigest,
    }),
    /unsafe|path|blocked|partial/i,
  );
  assert.equal(beforeStagingFailure.events.includes('stage'), false);
  assert.equal(beforeStagingFailure.events.includes('publish'), false);
  assert.equal(beforeStagingFailure.state.finalUnlinks, 0);
});

test('capture rejects a repository attestation whose canonical root does not match the request', async () => {
  const harness = makeSecureHarness({
    repositoryState: { canonicalRoot: '/Users/yexiaoguang/Documents/not-the-requested-root' },
  });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /canonical|root|repository/i);
});

test('capture rejects a repository attestation whose origin does not match the request', async () => {
  const harness = makeSecureHarness({
    repositoryState: { origin: 'git@github.com:someone/not-the-private-repo.git' },
  });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /origin|remote|repository/i);
});

test('capture rejects missing private visibility or repo-local LFS attestation', async () => {
  const harness = makeSecureHarness({
    repositoryState: {
      privateRepositoryAttestation: {
        status: 'APPROVED',
        visibility: 'PUBLIC',
        repositoryRoot: canonicalPrivateRoot,
        origin: canonicalRemote,
        lfsWriteAllowed: false,
      },
      repoLocalLfs: {
        status: 'READY',
        repositoryRoot: canonicalPrivateRoot,
        filtersConfigured: false,
        sentinelVerified: false,
      },
    },
  });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /private|visibility|LFS|attestation|repository/i);
});

test('capture rejects a symlinked private root resolved into the prototype worktree', async () => {
  const harness = makeSecureHarness({
    realpaths: { [canonicalPrivateRoot]: prototypeRoot },
    symlinkPaths: [canonicalPrivateRoot],
  });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /symlink|realpath|prototype|root|path/i);
});

test('capture rejects a manifest parent symlink that escapes the canonical private root', async () => {
  const manifestParent = `${canonicalPrivateRoot}/manifests`;
  const harness = makeSecureHarness({
    realpaths: {
      [canonicalPrivateRoot]: canonicalPrivateRoot,
      [manifestParent]: `${prototypeRoot}/escaped-manifests`,
    },
    symlinkPaths: [manifestParent],
  });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /symlink|realpath|manifest|contained|escape|path/i);
});

test('capture rejects a final manifest target symlink that escapes the canonical private root', async () => {
  const harness = makeSecureHarness({
    realpaths: {
      [canonicalPrivateRoot]: canonicalPrivateRoot,
      [canonicalManifestPath]: `${prototypeRoot}/escaped-request.json`,
    },
    symlinkPaths: [canonicalManifestPath],
  });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /symlink|realpath|manifest|contained|escape|path/i);
});

test('capture sanitizes adapter errors that contain a secret token', async () => {
  const harness = makeHarness({
    gitLfsError: new Error(`git-lfs attestation failed: Authorization Bearer ${fakeSecretToken}`),
  });

  let error;
  try {
    await runCapture(harness);
  } catch (caught) {
    error = caught;
  }
  assert.ok(error instanceof Error, 'adapter failure must reject the preflight');
  assert.match(error.message, /LFS|preflight|blocked/i);
  assert.doesNotMatch(error.message, new RegExp(fakeSecretToken));
  assert.equal(harness.counters.readerOpens, 0);
  assert.equal(harness.counters.manifestWrites, 0);
  assert.equal(harness.counters.writerFinalizations, 0);
  assert.equal(harness.counters.requestAppearances, 0);
  assert.equal(harness.counters.partialFiles, 0);
});

test('capture and CLI error boundaries sanitize forged domain errors with secret-like codes and newlines', async () => {
  const forgedCode = 'Authorization_Bearer_SECRET\nforged-output';
  const forged = new captureModule.CapturePreflightError(forgedCode);
  const harness = makeStrictHarness({ gitLfsError: forged });

  let error;
  try {
    await runCapture(harness, secureArgv());
  } catch (caught) {
    error = caught;
  }
  assert.ok(error instanceof Error, 'forged adapter failure must reject the preflight');
  assert.match(error.message, /LFS|preflight|blocked/i);
  assert.doesNotMatch(error.message, /Authorization_Bearer_SECRET|forged-output/);
  assert.doesNotMatch(error.message, /\r?\n/);
  assert.doesNotMatch(forged.message, /Authorization_Bearer_SECRET|forged-output/);
  assert.doesNotMatch(forged.message, /\r?\n/);
  assert.equal(harness.counters.readerOpens, 0);
  assert.equal(harness.counters.manifestWrites, 0);
  assert.equal(harness.counters.writerFinalizations, 0);
  assert.equal(harness.counters.requestAppearances, 0);
  assert.equal(harness.counters.partialFiles, 0);
});

test('atomic writer failure never finalizes, exposes a request, or leaves a partial file', async () => {
  const harness = makeStrictHarness({ writeManifestError: new Error('atomic writer failed') });

  await assert.rejects(() => runCapture(harness, secureArgv()), /writer|write|atomic/i);
  assert.equal(harness.counters.readerOpens, 0);
  assert.equal(harness.counters.writerWriteCalls, 1);
  assert.equal(harness.counters.manifestWrites, 0);
  assert.equal(harness.counters.writerFinalizations, 0);
  assert.equal(harness.counters.requestAppearances, 0);
  assert.equal(harness.counters.partialFiles, 0);
  assert.deepEqual(harness.counters.writerWriteArguments[0], {
    manifestPath: canonicalManifestPath,
    manifest: strictManifest,
    canonicalBytes: strictManifestBytes,
    manifestSha256: strictManifestDigest,
  });
});

test('atomic writer partial result never finalizes, exposes a request, or leaves a partial file', async () => {
  const harness = makeStrictHarness({
    writeManifestResult: { status: 'PARTIAL', partialPath: `${canonicalPrivateRoot}/manifests/.request.tmp` },
  });

  await assertBlockedWithoutEffects(harness, () => runCapture(harness, secureArgv()), /partial|writer|manifest|atomic/i);
  assert.equal(harness.counters.writerWriteCalls, 1);
  assert.deepEqual(harness.counters.writerWriteArguments[0], {
    manifestPath: canonicalManifestPath,
    manifest: strictManifest,
    canonicalBytes: strictManifestBytes,
    manifestSha256: strictManifestDigest,
  });
});

test('capture writes one deterministic metadata-only request manifest after all preflight checks', async () => {
  const first = makeStrictHarness({
    writeManifestSuccessResult: { status: 'WRITTEN', manifestDigest: strictManifestDigest },
  });
  const firstResult = await runCapture(first, secureArgv());
  const second = makeStrictHarness({
    writeManifestSuccessResult: { status: 'WRITTEN', manifestDigest: strictManifestDigest },
  });
  const secondResult = await runCapture(second, secureArgv());

  assert.equal(first.counters.readerOpens, 0);
  assert.equal(first.counters.manifestWrites, 1);
  assert.equal(second.counters.readerOpens, 0);
  assert.equal(second.counters.manifestWrites, 1);
  assert.deepEqual(firstResult, secondResult);
  assert.deepEqual(firstResult, {
    status: 'WRITTEN',
    requestDigest: strictRequestDigest,
    manifestDigest: strictManifestDigest,
  });
  assert.deepEqual(first.writes, second.writes);
  assert.equal(first.writes[0].manifestPath, canonicalManifestPath);
  assert.deepEqual(first.writes[0].manifest, strictManifest);
  assert.deepEqual(first.counters.writerWriteArguments[0], {
    manifestPath: canonicalManifestPath,
    manifest: strictManifest,
    canonicalBytes: strictManifestBytes,
    manifestSha256: strictManifestDigest,
  });
  assert.doesNotMatch(JSON.stringify(first.writes[0].manifest), /password|secret|credential|raw|excerpt|tenant.?153|source.?content|bundle/i);
});
