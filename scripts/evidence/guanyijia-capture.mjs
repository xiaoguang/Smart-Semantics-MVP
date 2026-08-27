import { spawn } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { constants as fsConstants } from 'node:fs';
import * as nodeFs from 'node:fs/promises';
import nodeOs from 'node:os';
import nodePath from 'node:path';
import nodeProcess from 'node:process';
import { pathToFileURL } from 'node:url';

export const FIXED_GIT_COMMIT = 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1';

const requiredOptions = ['--private-repository', '--private-remote', '--git-commit', '--manifest'];
const sha256Pattern = /^sha256:[a-f0-9]{64}$/;
const safeMessages = {
  ARGUMENT_INVALID: 'invalid, missing, non-absolute, or unknown argument',
  AUTHORIZATION_REQUIRED: 'authorize private capture is required',
  PRIVATE_ROOT_UNSAFE: 'private repository root must be outside the prototype workspace',
  MANIFEST_PATH_UNSAFE: 'manifest path is unsafe',
  MANIFEST_PARENT_MISSING: 'manifest parent directory is unavailable',
  MANIFEST_ALREADY_EXISTS: 'manifest target already exists',
  CODEUP_ORIGIN_INVALID: 'Codeup SSH origin is invalid',
  GIT_LFS_UNAVAILABLE: 'Git LFS is unavailable',
  REPOSITORY_ATTESTATION_INVALID: 'private repository attestation is invalid',
  FIXED_HEAD_MISMATCH: 'fixed commit does not match repository HEAD',
  CODEUP_PRIVACY_ATTESTATION_INVALID: 'Codeup privacy attestation is invalid',
  LFS_LOCAL_ATTESTATION_INVALID: 'repository-local LFS attestation is invalid',
  MANIFEST_WRITE_BLOCKED: 'atomic manifest writer is blocked',
  MANIFEST_WRITE_PARTIAL: 'atomic manifest writer returned partial output',
  MANIFEST_DIGEST_MISMATCH: 'manifest digest does not match',
  SAFE_WRITE_UNSUPPORTED: 'safe local manifest writing is unsupported',
  INTERNAL_PREFLIGHT_FAILURE: 'local capture preflight failed',
};
const captureErrorCodes = new Set(Object.keys(safeMessages));

function safeErrorCode(code, fallback = 'INTERNAL_PREFLIGHT_FAILURE') {
  return typeof code === 'string' && captureErrorCodes.has(code) ? code : fallback;
}

export class CapturePreflightError extends Error {
  constructor(code) {
    const safeCode = safeErrorCode(code);
    super(`Capture preflight blocked [${safeCode}]: ${safeMessages[safeCode]}.`);
    this.name = 'CapturePreflightError';
    this.code = safeCode;
  }
}

function fail(code) {
  throw new CapturePreflightError(code);
}

function isRecord(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requireFunction(value) {
  if (typeof value !== 'function') fail('INTERNAL_PREFLIGHT_FAILURE');
  return value;
}

function requireText(value) {
  if (typeof value !== 'string' || !value.trim() || value.includes('\0')) fail('ARGUMENT_INVALID');
  return value;
}

function sha256Hex(value) {
  return createHash('sha256').update(value).digest('hex');
}

function sha256Digest(value) {
  return `sha256:${sha256Hex(value)}`;
}

function requireDigest(value, code) {
  if (typeof value !== 'string' || !sha256Pattern.test(value)) fail(code);
  return value;
}

function stableJson(value) {
  if (value === null || typeof value === 'boolean' || typeof value === 'number' || typeof value === 'string') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (!isRecord(value)) fail('INTERNAL_PREFLIGHT_FAILURE');
  const keys = Object.keys(value).sort();
  return `{${keys.map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`;
}

function canonicalUtf8(value) {
  return `${stableJson(value)}\n`;
}

async function callAdapter(code, operation) {
  try {
    return await operation();
  } catch (error) {
    if (error instanceof CapturePreflightError && captureErrorCodes.has(error.code)) {
      throw new CapturePreflightError(error.code);
    }
    fail(safeErrorCode(code));
  }
}

function parseArguments(argv) {
  if (!Array.isArray(argv)) fail('ARGUMENT_INVALID');
  const parsed = {
    authorized: false,
    '--private-repository': undefined,
    '--private-remote': undefined,
    '--git-commit': undefined,
    '--manifest': undefined,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (typeof argument !== 'string') fail('ARGUMENT_INVALID');
    if (argument === '--authorize-private-capture') {
      if (parsed.authorized) fail('ARGUMENT_INVALID');
      parsed.authorized = true;
      continue;
    }
    if (!requiredOptions.includes(argument) || parsed[argument] !== undefined) fail('ARGUMENT_INVALID');
    const value = argv[index + 1];
    if (typeof value !== 'string' || value.startsWith('--')) fail('ARGUMENT_INVALID');
    parsed[argument] = value;
    index += 1;
  }
  for (const option of requiredOptions) requireText(parsed[option]);
  return parsed;
}

function absolutePath(path, value) {
  const candidate = requireText(value);
  if (!path.isAbsolute(candidate)) fail('ARGUMENT_INVALID');
  return path.resolve(candidate);
}

function strictlyContains(path, root, candidate) {
  const relative = path.relative(root, candidate);
  return relative !== '' && relative !== '..' && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative);
}

function pathsOverlap(path, left, right) {
  return left === right || strictlyContains(path, left, right) || strictlyContains(path, right, left);
}

function canonicalCodeupOrigin(value) {
  const origin = requireText(value);
  const match = /^git@codeup\.aliyun\.com:([^/\\\s?#:]+)\/([^/\\\s?#:]+)\.git$/.exec(origin);
  if (!match || match[1] === '.' || match[1] === '..' || match[2] === '.' || match[2] === '..') fail('CODEUP_ORIGIN_INVALID');
  return origin;
}

function repositoryIdentity(origin) {
  const match = /^git@codeup\.aliyun\.com:([^/]+)\/([^/]+)\.git$/.exec(canonicalCodeupOrigin(origin));
  if (!match) fail('CODEUP_ORIGIN_INVALID');
  return `codeup:${match[1]}/${match[2]}`;
}

function isLink(stat) {
  return Boolean(stat && typeof stat.isSymbolicLink === 'function' && stat.isSymbolicLink());
}

function knownNonDirectory(stat) {
  return Boolean(stat && typeof stat.isDirectory === 'function' && !stat.isDirectory());
}

function knownExistingFileOrDirectory(stat) {
  return Boolean(stat && (
    (typeof stat.isFile === 'function' && stat.isFile())
    || (typeof stat.isDirectory === 'function' && stat.isDirectory())
  ));
}

function knownRegularFile(stat) {
  return Boolean(stat && typeof stat.isFile === 'function' && stat.isFile() && !isLink(stat));
}

async function lstatIfPresent(filesystem, target) {
  try {
    return await filesystem.lstat(target);
  } catch (error) {
    if (isRecord(error) && error.code === 'ENOENT') return undefined;
    throw error;
  }
}

function safeManifestFilename(path, manifestPath) {
  const basename = path.basename(manifestPath);
  return /^[A-Za-z0-9][A-Za-z0-9._-]*\.json$/.test(basename);
}

function createSafePathGuard({ path, filesystem }) {
  const contexts = new WeakMap();

  async function inspect(input) {
    const requestedRoot = absolutePath(path, input.requestedPrivateRoot);
    const requestedManifest = absolutePath(path, input.requestedManifestPath);
    const requestedPrototype = absolutePath(path, input.prototypeRoot);
    const requestedWorkspace = absolutePath(path, input.workspaceRoot);
    const [prototypeRoot, workspaceRoot] = await callAdapter('PRIVATE_ROOT_UNSAFE', () => Promise.all([
      filesystem.realpath(requestedPrototype),
      filesystem.realpath(requestedWorkspace),
    ]));
    const rootStat = await callAdapter('PRIVATE_ROOT_UNSAFE', () => lstatIfPresent(filesystem, requestedRoot));
    if (!rootStat || isLink(rootStat) || knownNonDirectory(rootStat)) fail('PRIVATE_ROOT_UNSAFE');
    const privateRepositoryRoot = await callAdapter('PRIVATE_ROOT_UNSAFE', () => filesystem.realpath(requestedRoot));
    if (pathsOverlap(path, privateRepositoryRoot, prototypeRoot) || pathsOverlap(path, privateRepositoryRoot, workspaceRoot)) {
      fail('PRIVATE_ROOT_UNSAFE');
    }
    if (!strictlyContains(path, requestedRoot, requestedManifest)) fail('MANIFEST_PATH_UNSAFE');

    const manifestPath = path.resolve(privateRepositoryRoot, path.relative(requestedRoot, requestedManifest));
    if (!strictlyContains(path, privateRepositoryRoot, manifestPath) || !safeManifestFilename(path, manifestPath)) fail('MANIFEST_PATH_UNSAFE');
    const manifestDirectory = path.dirname(manifestPath);
    const relativeParent = path.relative(privateRepositoryRoot, manifestDirectory);
    if (!strictlyContains(path, privateRepositoryRoot, manifestDirectory) || (relativeParent !== 'manifests' && !relativeParent.startsWith(`manifests${path.sep}`))) {
      fail('MANIFEST_PATH_UNSAFE');
    }

    let componentPath = privateRepositoryRoot;
    for (const component of relativeParent.split(path.sep)) {
      componentPath = path.join(componentPath, component);
      const componentStat = await callAdapter('MANIFEST_PARENT_MISSING', () => lstatIfPresent(filesystem, componentPath));
      if (!componentStat) fail('MANIFEST_PARENT_MISSING');
      if (isLink(componentStat) || knownNonDirectory(componentStat)) fail('MANIFEST_PATH_UNSAFE');
    }
    const canonicalManifestDirectory = await callAdapter('MANIFEST_PATH_UNSAFE', () => filesystem.realpath(manifestDirectory));
    if (canonicalManifestDirectory !== manifestDirectory || !strictlyContains(path, privateRepositoryRoot, canonicalManifestDirectory)) {
      fail('MANIFEST_PATH_UNSAFE');
    }
    const targetStat = await callAdapter('MANIFEST_PATH_UNSAFE', () => lstatIfPresent(filesystem, manifestPath));
    if (isLink(targetStat)) fail('MANIFEST_PATH_UNSAFE');
    if (knownExistingFileOrDirectory(targetStat)) fail('MANIFEST_ALREADY_EXISTS');

    const target = { prototypeRoot, workspaceRoot, privateRepositoryRoot, manifestDirectory: canonicalManifestDirectory, manifestPath };
    contexts.set(target, input);
    return target;
  }

  return {
    inspect,
    async revalidate(target) {
      const input = contexts.get(target);
      if (!input) fail('MANIFEST_PATH_UNSAFE');
      const rechecked = await inspect(input);
      for (const field of ['prototypeRoot', 'workspaceRoot', 'privateRepositoryRoot', 'manifestDirectory', 'manifestPath']) {
        if (rechecked[field] !== target[field]) fail('MANIFEST_PATH_UNSAFE');
      }
    },
  };
}

function validateWriterResult(result, expectedStatus, expectedDigest) {
  if (!isRecord(result) || result.status === 'BLOCKED') fail('MANIFEST_WRITE_BLOCKED');
  if (result.status === 'PARTIAL') fail('MANIFEST_WRITE_PARTIAL');
  if (result.status !== expectedStatus) fail('MANIFEST_WRITE_BLOCKED');
  if (expectedDigest !== undefined && result.manifestDigest !== expectedDigest) fail('MANIFEST_DIGEST_MISMATCH');
}

function validateRepositoryState(state, expectation) {
  if (!isRecord(state) || state.status !== 'READY' || state.isPrivateGitRepository !== true) fail('REPOSITORY_ATTESTATION_INVALID');
  if (state.headCommit !== FIXED_GIT_COMMIT || expectation.gitCommit !== FIXED_GIT_COMMIT) fail('FIXED_HEAD_MISMATCH');
  if (state.canonicalRoot !== expectation.privateRepositoryRoot || canonicalCodeupOrigin(state.origin) !== expectation.privateRemote) {
    fail('REPOSITORY_ATTESTATION_INVALID');
  }
  const privacy = state.privateRepositoryAttestation;
  if (!isRecord(privacy) || privacy.status !== 'APPROVED' || privacy.visibility !== 'PRIVATE' || privacy.repositoryRoot !== expectation.privateRepositoryRoot || privacy.origin !== expectation.privateRemote || privacy.lfsWriteAllowed !== true) {
    fail('CODEUP_PRIVACY_ATTESTATION_INVALID');
  }
  const lfs = state.repoLocalLfs;
  if (!isRecord(lfs) || lfs.status !== 'READY' || lfs.repositoryRoot !== expectation.privateRepositoryRoot || lfs.filtersConfigured !== true || lfs.sentinelVerified !== true) {
    fail('LFS_LOCAL_ATTESTATION_INVALID');
  }
  requireDigest(state.codeupAttestationDigest, 'CODEUP_PRIVACY_ATTESTATION_INVALID');
  requireDigest(state.lfsAttestationDigest, 'LFS_LOCAL_ATTESTATION_INVALID');
}

function requestManifest({ privateRepositoryRoot, privateRemote, gitCommit, manifestPath, codeupAttestationDigest, lfsAttestationDigest }) {
  const requestPayload = {
    schemaVersion: 1,
    kind: 'CAPTURE_REQUEST',
    metadataOnly: true,
    privateRepositoryRoot,
    privateRemote,
    gitCommit,
    manifestPath,
    codeupAttestationDigest,
    lfsAttestationDigest,
  };
  const requestDigest = sha256Digest(canonicalUtf8(requestPayload));
  return {
    ...requestPayload,
    requestDigest,
  };
}

/** The only high-level seam; it never receives or opens a source reader. */
export async function runCapturePreflight({ argv, prototypeRoot, workspaceRoot, adapters }) {
  const parsed = parseArguments(argv);
  if (!parsed.authorized) fail('AUTHORIZATION_REQUIRED');
  if (!isRecord(adapters) || !isRecord(adapters.path) || !isRecord(adapters.filesystem) || !isRecord(adapters.gitLfs)) fail('INTERNAL_PREFLIGHT_FAILURE');
  const path = adapters.path;
  for (const method of ['isAbsolute', 'resolve', 'relative', 'dirname', 'join', 'basename']) requireFunction(path[method]);
  for (const method of ['realpath', 'lstat']) requireFunction(adapters.filesystem[method]);
  const resolvedPrototypeRoot = absolutePath(path, prototypeRoot);
  const resolvedWorkspaceRoot = workspaceRoot === undefined ? path.dirname(resolvedPrototypeRoot) : absolutePath(path, workspaceRoot);
  const paths = createSafePathGuard({ path, filesystem: adapters.filesystem });
  const target = await paths.inspect({
    requestedPrivateRoot: parsed['--private-repository'],
    requestedManifestPath: parsed['--manifest'],
    prototypeRoot: resolvedPrototypeRoot,
    workspaceRoot: resolvedWorkspaceRoot,
  });
  const privateRemote = canonicalCodeupOrigin(parsed['--private-remote']);
  if (parsed['--git-commit'] !== FIXED_GIT_COMMIT) fail('FIXED_HEAD_MISMATCH');
  const available = await callAdapter('GIT_LFS_UNAVAILABLE', () => requireFunction(adapters.gitLfs.isAvailable)());
  if (available !== true) fail('GIT_LFS_UNAVAILABLE');
  const expectation = { privateRepositoryRoot: target.privateRepositoryRoot, privateRemote, gitCommit: FIXED_GIT_COMMIT };
  const state = await callAdapter('REPOSITORY_ATTESTATION_INVALID', () => requireFunction(adapters.repository?.inspect)(expectation));
  validateRepositoryState(state, expectation);
  await paths.revalidate(target);
  const preflight = await callAdapter('MANIFEST_WRITE_BLOCKED', () => requireFunction(adapters.writer?.preflight)(target));
  validateWriterResult(preflight, 'READY');

  const manifest = requestManifest({
    privateRepositoryRoot: target.privateRepositoryRoot,
    privateRemote,
    gitCommit: FIXED_GIT_COMMIT,
    manifestPath: target.manifestPath,
    codeupAttestationDigest: state.codeupAttestationDigest,
    lfsAttestationDigest: state.lfsAttestationDigest,
  });
  const canonicalBytes = canonicalUtf8(manifest);
  const manifestDigest = sha256Digest(canonicalBytes);
  await paths.revalidate(target);
  const writeResult = await callAdapter('MANIFEST_WRITE_BLOCKED', () => requireFunction(adapters.writer?.writeManifest)({
    manifestPath: target.manifestPath,
    manifest,
    canonicalBytes,
    manifestSha256: manifestDigest,
  }));
  validateWriterResult(writeResult, 'WRITTEN', manifestDigest);
  return { status: 'WRITTEN', requestDigest: manifest.requestDigest, manifestDigest };
}

/** Binds a test/maintenance adapter set to one exact canonical request. */
export function createCliAdapters({ prototypeRoot, expected, adapters }) {
  if (!isRecord(expected) || !isRecord(adapters) || !isRecord(adapters.path)) fail('INTERNAL_PREFLIGHT_FAILURE');
  requireText(prototypeRoot);
  const expectedRoot = absolutePath(adapters.path, expected.privateRepositoryRoot);
  const expectedRemote = canonicalCodeupOrigin(expected.privateRemote);
  if (expected.gitCommit !== FIXED_GIT_COMMIT) fail('FIXED_HEAD_MISMATCH');
  return {
    ...adapters,
    repository: {
      inspect: async (expectation) => {
        if (!isRecord(expectation) || expectation.privateRepositoryRoot !== expectedRoot) fail('PRIVATE_ROOT_UNSAFE');
        if (expectation.privateRemote !== expectedRemote) fail('CODEUP_ORIGIN_INVALID');
        if (expectation.gitCommit !== FIXED_GIT_COMMIT) fail('FIXED_HEAD_MISMATCH');
        return adapters.repository?.inspect(expectation);
      },
    },
  };
}

function createLocalCommandRunner() {
  return {
    async run({ executable, args, cwd }) {
      if ((executable !== 'git' && executable !== 'git-lfs') || !Array.isArray(args) || args.some((argument) => typeof argument !== 'string')) {
        fail('INTERNAL_PREFLIGHT_FAILURE');
      }
      return new Promise((resolve, reject) => {
        const child = spawn(executable, args, {
          cwd,
          shell: false,
          stdio: ['ignore', 'pipe', 'ignore'],
          env: {
            PATH: nodeProcess.env.PATH ?? '',
            GIT_TERMINAL_PROMPT: '0',
            GIT_LFS_SKIP_SMUDGE: '1',
            GIT_CONFIG_NOSYSTEM: '1',
            GIT_CONFIG_GLOBAL: nodeOs.devNull,
          },
        });
        let stdout = '';
        child.stdout.on('data', (chunk) => {
          if (stdout.length < 64 * 1024) stdout += String(chunk).slice(0, 64 * 1024 - stdout.length);
        });
        child.once('error', reject);
        child.once('close', (exitCode) => resolve({ exitCode: exitCode ?? 1, stdout }));
      });
    },
  };
}

function createSecureFilesystem(path) {
  return {
    lstat: (target) => nodeFs.lstat(target),
    realpath: (target) => nodeFs.realpath(target),
    readFile: (target) => nodeFs.readFile(target),
    openExclusiveNoFollow: (target) => {
      if (typeof fsConstants.O_NOFOLLOW !== 'number') fail('SAFE_WRITE_UNSUPPORTED');
      return nodeFs.open(target, fsConstants.O_WRONLY | fsConstants.O_CREAT | fsConstants.O_EXCL | fsConstants.O_NOFOLLOW, 0o600);
    },
    link: (existingPath, finalPath) => nodeFs.link(existingPath, finalPath),
    unlink: (target) => nodeFs.unlink(target),
    createTemporaryPath: (target) => path.join(target.manifestDirectory, `.${path.basename(target.manifestPath)}.tmp.${nodeProcess.pid}.${randomBytes(12).toString('hex')}`),
    async revalidateDirectory(target) {
      const rootStat = await nodeFs.lstat(target.privateRepositoryRoot);
      const directoryStat = await nodeFs.lstat(target.manifestDirectory);
      if (isLink(rootStat) || !rootStat.isDirectory() || isLink(directoryStat) || !directoryStat.isDirectory()) fail('MANIFEST_PATH_UNSAFE');
      const root = await nodeFs.realpath(target.privateRepositoryRoot);
      const directory = await nodeFs.realpath(target.manifestDirectory);
      if (root !== target.privateRepositoryRoot || directory !== target.manifestDirectory || !strictlyContains(path, root, directory)) fail('MANIFEST_PATH_UNSAFE');
    },
  };
}

function outputLines(value) {
  return value ? value.split(/\r?\n/).filter(Boolean) : [];
}

function parseJsonRecord(bytes) {
  try {
    const result = JSON.parse(new TextDecoder().decode(bytes));
    return isRecord(result) ? result : undefined;
  } catch {
    return undefined;
  }
}

function sameKeys(record, allowedKeys) {
  const actual = Object.keys(record).sort();
  const expected = [...allowedKeys].sort();
  return actual.length === expected.length && actual.every((key, index) => key === expected[index]);
}

function validateAttestationDigest(record, code) {
  if (!isRecord(record) || !requireDigest(record.attestationSha256, code)) fail(code);
  const { attestationSha256, ...unsigned } = record;
  if (attestationSha256 !== sha256Digest(JSON.stringify(unsigned))) fail(code);
  return attestationSha256;
}

function validateCodeupAttestation(record, expectation) {
  const code = 'CODEUP_PRIVACY_ATTESTATION_INVALID';
  const fields = ['schemaVersion', 'origin', 'repositoryIdentity', 'visibility', 'lfsWritePermission', 'issuedAt', 'expiresAt', 'issuer', 'attestationSha256'];
  if (!isRecord(record) || !sameKeys(record, fields) || record.schemaVersion !== 1 || record.origin !== expectation.privateRemote || record.repositoryIdentity !== repositoryIdentity(expectation.privateRemote) || record.visibility !== 'PRIVATE' || record.lfsWritePermission !== 'ALLOWED' || record.issuer !== 'CODEUP_OFFICIAL_ATTESTATION') {
    fail(code);
  }
  const issuedAt = Date.parse(record.issuedAt);
  const expiresAt = Date.parse(record.expiresAt);
  if (Number.isNaN(issuedAt) || Number.isNaN(expiresAt) || issuedAt > Date.now() || expiresAt <= Date.now() || expiresAt <= issuedAt) fail(code);
  return validateAttestationDigest(record, code);
}

function validateLfsAttestation(record, expectation) {
  const code = 'LFS_LOCAL_ATTESTATION_INVALID';
  const fields = ['schemaVersion', 'origin', 'repositoryIdentity', 'filterConfigSha256', 'gitattributesSha256', 'sentinelPath', 'sentinelOid', 'attestationSha256'];
  if (!isRecord(record) || !sameKeys(record, fields) || record.schemaVersion !== 1 || record.origin !== expectation.privateRemote || record.repositoryIdentity !== repositoryIdentity(expectation.privateRemote) || record.sentinelPath !== 'preflight/lfs-sentinel.bin') {
    fail(code);
  }
  requireDigest(record.filterConfigSha256, code);
  requireDigest(record.gitattributesSha256, code);
  requireDigest(record.sentinelOid, code);
  return { digest: validateAttestationDigest(record, code), sentinelOid: record.sentinelOid };
}

async function commandOutput(runner, executable, args, cwd, errorCode, allowAbsent = false) {
  const result = await runner.run({ executable, args, cwd });
  if (!isRecord(result) || typeof result.exitCode !== 'number' || typeof result.stdout !== 'string') fail(errorCode);
  if (result.exitCode !== 0) {
    if (allowAbsent && result.exitCode === 1 && result.stdout === '') return undefined;
    fail(errorCode);
  }
  return result.stdout.trim();
}

function parseAttributes(output, sentinelPath) {
  const values = output.split('\0');
  if (values.at(-1) !== '') return undefined;
  values.pop();
  if (values.length !== 12) return undefined;
  const attributes = {};
  const expected = new Set(['filter', 'diff', 'merge', 'text']);
  for (let index = 0; index < values.length; index += 3) {
    const [path, attribute, value] = values.slice(index, index + 3);
    if (path !== sentinelPath || !expected.has(attribute) || Object.hasOwn(attributes, attribute)) return undefined;
    attributes[attribute] = value;
  }
  if (Object.keys(attributes).length !== expected.size) return undefined;
  return attributes;
}

function parseSentinelOid(output, sentinelPath) {
  const entries = outputLines(output).map((line) => /^([a-f0-9]{64})\s+\*?\s*(.+)$/.exec(line)).filter(Boolean);
  if (entries.length !== 1 || entries[0][2] !== sentinelPath) return undefined;
  return `sha256:${entries[0][1]}`;
}

const approvedLfsConfig = {
  'filter.lfs.clean': 'git-lfs clean -- %f',
  'filter.lfs.smudge': 'git-lfs smudge -- %f',
  'filter.lfs.process': 'git-lfs filter-process',
  'filter.lfs.required': 'true',
};
const approvedGitattributesBytes = new TextEncoder().encode('preflight/lfs-sentinel.bin filter=lfs diff=lfs merge=lfs -text\n');

function canonicalFilterConfigBytes(config) {
  return new TextEncoder().encode(`${Object.entries(config)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`)
    .join('\n')}\n`);
}

function equalBytes(left, right) {
  if (!(left instanceof Uint8Array) || !(right instanceof Uint8Array) || left.byteLength !== right.byteLength) return false;
  return left.every((value, index) => value === right[index]);
}

function createLocalRepositoryInspector({ filesystem, runner, path }) {
  return {
    async inspect(expectation) {
      try {
        if (!isRecord(expectation) || expectation.gitCommit !== FIXED_GIT_COMMIT) fail('FIXED_HEAD_MISMATCH');
        const root = expectation.privateRepositoryRoot;
        const origin = canonicalCodeupOrigin(expectation.privateRemote);
        const gitDirectory = await lstatIfPresent(filesystem, path.join(root, '.git'));
        if (!gitDirectory || isLink(gitDirectory) || knownNonDirectory(gitDirectory)) fail('REPOSITORY_ATTESTATION_INVALID');
        const lfsVersion = await commandOutput(runner, 'git-lfs', ['version'], root, 'GIT_LFS_UNAVAILABLE');
        if (!lfsVersion) fail('GIT_LFS_UNAVAILABLE');
        const topLevel = await commandOutput(runner, 'git', ['-C', root, 'rev-parse', '--show-toplevel'], root, 'REPOSITORY_ATTESTATION_INVALID');
        if (await filesystem.realpath(topLevel) !== root) fail('REPOSITORY_ATTESTATION_INVALID');
        const worktreeConfig = await commandOutput(runner, 'git', ['-C', root, 'config', '--local', '--get', 'extensions.worktreeconfig'], root, 'REPOSITORY_ATTESTATION_INVALID', true);
        if (worktreeConfig !== undefined) fail('REPOSITORY_ATTESTATION_INVALID');
        const remotes = outputLines(await commandOutput(runner, 'git', ['-C', root, 'remote'], root, 'REPOSITORY_ATTESTATION_INVALID'));
        const origins = outputLines(await commandOutput(runner, 'git', ['-C', root, 'config', '--local', '--get-all', 'remote.origin.url'], root, 'REPOSITORY_ATTESTATION_INVALID'));
        const pushUrls = outputLines((await commandOutput(runner, 'git', ['-C', root, 'config', '--local', '--get-all', 'remote.origin.pushurl'], root, 'REPOSITORY_ATTESTATION_INVALID', true)) ?? '');
        const headCommit = await commandOutput(runner, 'git', ['-C', root, 'rev-parse', '--verify', 'HEAD'], root, 'FIXED_HEAD_MISMATCH');
        if (remotes.length !== 1 || remotes[0] !== 'origin' || origins.length !== 1 || pushUrls.length || origins[0] !== origin || headCommit !== FIXED_GIT_COMMIT) {
          fail(headCommit !== FIXED_GIT_COMMIT ? 'FIXED_HEAD_MISMATCH' : 'REPOSITORY_ATTESTATION_INVALID');
        }
        const codeup = parseJsonRecord(await filesystem.readFile(path.join(root, 'preflight', 'codeup-private-attestation.json')));
        const codeupAttestationDigest = validateCodeupAttestation(codeup, expectation);
        const lfs = parseJsonRecord(await filesystem.readFile(path.join(root, 'preflight', 'lfs-local-attestation.json')));
        const lfsAttestation = validateLfsAttestation(lfs, expectation);
        const observedLfsConfig = {};
        for (const [key, expectedValue] of Object.entries(approvedLfsConfig)) {
          const value = await commandOutput(runner, 'git', ['-C', root, 'config', '--local', '--get', key], root, 'LFS_LOCAL_ATTESTATION_INVALID');
          if (value !== expectedValue) fail('LFS_LOCAL_ATTESTATION_INVALID');
          observedLfsConfig[key] = value;
        }
        const gitattributesPath = path.join(root, '.gitattributes');
        const filterConfigBytes = canonicalFilterConfigBytes(observedLfsConfig);
        const gitattributesBytes = await filesystem.readFile(gitattributesPath);
        if (sha256Digest(filterConfigBytes) !== lfs.filterConfigSha256) fail('LFS_LOCAL_ATTESTATION_INVALID');
        if (!equalBytes(gitattributesBytes, approvedGitattributesBytes) || sha256Digest(gitattributesBytes) !== lfs.gitattributesSha256) fail('LFS_LOCAL_ATTESTATION_INVALID');
        for (const overrideKey of ['lfs.url', 'lfs.pushurl', 'remote.origin.lfsurl', 'remote.origin.lfspushurl']) {
          const override = await commandOutput(runner, 'git', ['-C', root, 'config', '--local', '--get', overrideKey], root, 'LFS_LOCAL_ATTESTATION_INVALID', true);
          if (override !== undefined) fail('LFS_LOCAL_ATTESTATION_INVALID');
        }
        const standaloneTransferAgent = await commandOutput(runner, 'git', ['-C', root, 'config', '--local', '--get', 'lfs.standalonetransferagent'], root, 'LFS_LOCAL_ATTESTATION_INVALID', true);
        if (standaloneTransferAgent !== undefined) fail('LFS_LOCAL_ATTESTATION_INVALID');
        for (const pattern of ['^url\\..*\\.insteadof$', '^url\\..*\\.pushinsteadof$', '^lfs\\.customtransfer\\..*$']) {
          const transportConfig = await commandOutput(runner, 'git', ['-C', root, 'config', '--local', '--get-regexp', pattern], root, 'LFS_LOCAL_ATTESTATION_INVALID', true);
          if (transportConfig !== undefined) fail('LFS_LOCAL_ATTESTATION_INVALID');
        }
        if (await lstatIfPresent(filesystem, path.join(root, '.lfsconfig'))) fail('LFS_LOCAL_ATTESTATION_INVALID');
        const attributeOutput = await commandOutput(runner, 'git', ['-C', root, 'check-attr', '-z', 'filter', 'diff', 'merge', 'text', '--', 'preflight/lfs-sentinel.bin'], root, 'LFS_LOCAL_ATTESTATION_INVALID');
        const attributes = parseAttributes(attributeOutput, lfs.sentinelPath);
        if (!attributes || attributes.filter !== 'lfs' || attributes.diff !== 'lfs' || attributes.merge !== 'lfs' || attributes.text !== 'unset') fail('LFS_LOCAL_ATTESTATION_INVALID');
        await commandOutput(runner, 'git', ['-C', root, 'lfs', 'pointer', '--check', '--file', 'preflight/lfs-sentinel.bin'], root, 'LFS_LOCAL_ATTESTATION_INVALID');
        const indexedSentinel = parseSentinelOid(await commandOutput(runner, 'git', ['-C', root, 'lfs', 'ls-files', '--long', '--', 'preflight/lfs-sentinel.bin'], root, 'LFS_LOCAL_ATTESTATION_INVALID'), lfs.sentinelPath);
        if (indexedSentinel !== lfsAttestation.sentinelOid) fail('LFS_LOCAL_ATTESTATION_INVALID');
        return {
          status: 'READY',
          isPrivateGitRepository: true,
          canonicalRoot: root,
          origin,
          headCommit,
          codeupAttestationDigest,
          lfsAttestationDigest: lfsAttestation.digest,
          privateRepositoryAttestation: { status: 'APPROVED', visibility: 'PRIVATE', repositoryRoot: root, origin, lfsWriteAllowed: true },
          repoLocalLfs: { status: 'READY', repositoryRoot: root, filtersConfigured: true, sentinelVerified: true },
        };
      } catch (error) {
        if (error instanceof CapturePreflightError) throw error;
        fail('REPOSITORY_ATTESTATION_INVALID');
      }
    },
  };
}

function createWriterPathGuard({ filesystem, path }) {
  return {
    async revalidate(target, { requireFinal = false } = {}) {
      const root = await lstatIfPresent(filesystem, target.privateRepositoryRoot);
      const directory = await lstatIfPresent(filesystem, target.manifestDirectory);
      if (!root || !directory || isLink(root) || isLink(directory) || knownNonDirectory(root) || knownNonDirectory(directory)) fail('MANIFEST_PATH_UNSAFE');
      const canonicalRoot = await filesystem.realpath(target.privateRepositoryRoot);
      const canonicalDirectory = await filesystem.realpath(target.manifestDirectory);
      if (canonicalRoot !== target.privateRepositoryRoot || canonicalDirectory !== target.manifestDirectory || !strictlyContains(path, canonicalRoot, canonicalDirectory)) fail('MANIFEST_PATH_UNSAFE');
      const finalStat = await lstatIfPresent(filesystem, target.manifestPath);
      if (finalStat && (!knownRegularFile(finalStat) || isLink(finalStat))) fail('MANIFEST_PATH_UNSAFE');
      if (requireFinal) {
        if (!finalStat) fail('MANIFEST_PATH_UNSAFE');
        const canonicalFinal = await filesystem.realpath(target.manifestPath);
        if (canonicalFinal !== target.manifestPath || !strictlyContains(path, canonicalRoot, canonicalFinal)) fail('MANIFEST_PATH_UNSAFE');
      }
    },
  };
}

function createAtomicRequestWriter({ filesystem, path, pathGuard }) {
  let preparedTarget;
  const temporaryPathFor = (target) => (typeof filesystem.createTemporaryPath === 'function'
    ? filesystem.createTemporaryPath(target)
    : path.join(target.manifestDirectory, '.request.tmp.fake'));

  async function revalidate(target, options) {
    await callAdapter('MANIFEST_PATH_UNSAFE', () => pathGuard.revalidate(target, options));
    if (typeof filesystem.revalidateDirectory === 'function') {
      await callAdapter('MANIFEST_PATH_UNSAFE', () => filesystem.revalidateDirectory(target, options));
    }
  }

  async function cleanTemporary(target, temporaryPath) {
    try {
      if (typeof filesystem.revalidateDirectory === 'function') await filesystem.revalidateDirectory(target);
      await filesystem.unlink(temporaryPath);
    } catch {
      // A failed cleanup is never reported as success; never disclose the staging path.
    }
  }

  const write = async (input) => {
    const target = input?.target ?? preparedTarget;
    const canonicalBytes = input?.canonicalJsonUtf8 ?? input?.canonicalBytes;
    const expectedDigest = input?.expectedDigest ?? input?.manifestSha256;
    if (!isRecord(target) || (typeof canonicalBytes !== 'string' && !(canonicalBytes instanceof Uint8Array))) fail('MANIFEST_WRITE_BLOCKED');
    requireDigest(expectedDigest, 'MANIFEST_DIGEST_MISMATCH');
    const bytes = typeof canonicalBytes === 'string' ? new TextEncoder().encode(canonicalBytes) : canonicalBytes;
    if (sha256Digest(bytes) !== expectedDigest) fail('MANIFEST_DIGEST_MISMATCH');
    const temporaryPath = temporaryPathFor(target);
    let handle;
    let published = false;
    try {
      await revalidate(target);
      handle = await filesystem.openExclusiveNoFollow(temporaryPath, 0o600);
      await handle.writeFile(bytes);
      await handle.sync();
      await handle.close();
      handle = undefined;
      const temporaryStat = await filesystem.lstat(temporaryPath);
      if (!knownRegularFile(temporaryStat)) fail('MANIFEST_WRITE_PARTIAL');
      await revalidate(target);
      await filesystem.link(temporaryPath, target.manifestPath);
      published = true;
      const finalStat = await filesystem.lstat(target.manifestPath);
      if (!knownRegularFile(finalStat)) fail('MANIFEST_PATH_UNSAFE');
      const finalBytes = await filesystem.readFile(target.manifestPath);
      if (sha256Digest(finalBytes) !== expectedDigest) fail('MANIFEST_DIGEST_MISMATCH');
      await revalidate(target, { requireFinal: true });
      await filesystem.unlink(temporaryPath);
      return { status: 'WRITTEN', manifestDigest: expectedDigest };
    } catch (error) {
      if (handle) await handle.close().catch(() => undefined);
      if (!published) await cleanTemporary(target, temporaryPath);
      if (error instanceof CapturePreflightError) throw error;
      fail(published ? 'MANIFEST_DIGEST_MISMATCH' : 'MANIFEST_WRITE_BLOCKED');
    }
  };

  return {
    async preflight(target) {
      if (!isRecord(target)) fail('MANIFEST_WRITE_BLOCKED');
      preparedTarget = target;
      return { status: 'READY' };
    },
    write,
    writeManifest: write,
  };
}

/** Production adapters are local-only: fs plus fixed git/git-lfs argument arrays. */
export function createProductionLocalCaptureAdapters(dependencies = {}) {
  if (!isRecord(dependencies)) fail('INTERNAL_PREFLIGHT_FAILURE');
  const path = dependencies.path ?? nodePath;
  const filesystem = dependencies.filesystem ?? createSecureFilesystem(path);
  const runner = dependencies.runner ?? createLocalCommandRunner();
  if (!isRecord(path) || !isRecord(filesystem) || !isRecord(runner)) fail('INTERNAL_PREFLIGHT_FAILURE');
  const pathGuard = dependencies.pathGuard ?? createWriterPathGuard({ filesystem, path });
  return {
    path,
    filesystem,
    gitLfs: {
      async isAvailable() {
        const result = await runner.run({ executable: 'git-lfs', args: ['version'], cwd: nodeProcess.cwd() });
        return isRecord(result) && result.exitCode === 0 && typeof result.stdout === 'string' && Boolean(result.stdout.trim());
      },
    },
    repository: createLocalRepositoryInspector({ filesystem, runner, path }),
    writer: createAtomicRequestWriter({ filesystem, path, pathGuard }),
  };
}

async function main() {
  try {
    await runCapturePreflight({
      argv: nodeProcess.argv.slice(2),
      prototypeRoot: nodeProcess.cwd(),
      workspaceRoot: nodePath.dirname(nodeProcess.cwd()),
      adapters: createProductionLocalCaptureAdapters(),
    });
    nodeProcess.stdout.write('Capture request manifest written.\n');
  } catch (error) {
    const safeError = error instanceof CapturePreflightError
      ? new CapturePreflightError(safeErrorCode(error.code))
      : new CapturePreflightError('INTERNAL_PREFLIGHT_FAILURE');
    nodeProcess.stderr.write(`${safeError.message}\n`);
    nodeProcess.exitCode = 1;
  }
}

if (nodeProcess.argv[1] && import.meta.url === pathToFileURL(nodeProcess.argv[1]).href) {
  await main();
}
