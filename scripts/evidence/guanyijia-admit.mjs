import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import * as fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

const MYSQL_SNAPSHOT_ID = '20260813T032528Z-abb0502c7d79';
const GITHUB_SNAPSHOT_ID = '20260813032126Z-5821d0ece9b1';
const EXPECTED_MYSQL_SNAPSHOT_ROOT =
  '/Users/yexiaoguang/Documents/ErpMock/modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79';
const EXPECTED_GITHUB_SNAPSHOT_ROOT =
  '/Users/yexiaoguang/Documents/ErpMock/modeling-evidence/guanyijia/repositories/github/jishenghua/jshERP/snapshots/20260813032126Z-5821d0ece9b1';
const PUBLIC_OUTPUT_DIRECTORY = path.resolve(
  process.cwd(),
  'src/features/guanyijia-evidence-factory',
);
const EXCLUSIONS = Object.freeze([
  'samples/**',
  'profiles/**',
  'raw-row',
  'query-result',
  'bound-parameter',
  'example',
]);
const ADMITTED_CATEGORIES = new Set(['DDL', 'CONSTRAINT', 'PROGRAMMABILITY', 'DML_DIGEST']);
const PUBLIC_ARTIFACT_CATEGORIES = new Set(['DDL', 'PROGRAMMABILITY', 'DML_DIGEST']);
const EXCLUDED_EVIDENCE_MARKER = /(?:samples?|profiles?|raw[-_ ]?row|query[-_ ]?result|bound[-_ ]?parameter|\bexample\b)/i;
const FIXED_ID_IDENTIFIER = "(?:`(?:tenant|user)_id`|\"(?:tenant|user)_id\"|'(?:tenant|user)_id'|\\[(?:tenant|user)_id\\]|\\b(?:tenant|user)_id\\b)";

function admissionError(message) {
  throw new Error(`Guanyijia evidence admission failed: ${message}`);
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value, 'utf8').digest('hex')}`;
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function requireString(value, label) {
  if (typeof value !== 'string' || !value.trim()) admissionError(`${label} must be a non-blank string`);
  return value;
}

function requireSafeRelativePath(value, label) {
  const relativePath = requireString(value, label);
  if (path.isAbsolute(relativePath) || relativePath.split('/').includes('..')) {
    admissionError(`${label} must be a safe relative path`);
  }
  return relativePath;
}

function requireCount(value, label) {
  if (!Number.isInteger(value) || value < 0) admissionError(`${label} must be a non-negative integer`);
  return value;
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (isRecord(value)) {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function redactFixedIdentifierLiterals(text) {
  const listPattern = new RegExp(
    `(${FIXED_ID_IDENTIFIER}\\s+IN\\s*\\(\\s*)((?:[-+]?\\d+\\s*,\\s*)*[-+]?\\d+)`,
    'gi',
  );
  const equalityPattern = new RegExp(`(${FIXED_ID_IDENTIFIER}\\s*=\\s*)[-+]?\\d+\\b`, 'gi');
  return text
    .replace(listPattern, (_match, prefix, values) => `${prefix}${values.replace(/[-+]?\d+/g, '[REDACTED_ID]')}`)
    .replace(equalityPattern, '$1[REDACTED_ID]');
}

function redactPublicText(text) {
  const publicText = text
    .split(/\r?\n/)
    .filter((line) => !/\b(?:EXECUTION_COUNT|SUM_TIMER_WAIT|FIRST_SEEN|LAST_SEEN|executions)\b/i.test(line))
    .join('\n')
    .replace(/\bAUTO_INCREMENT\s*=\s*\d+\b/gi, 'AUTO_INCREMENT=REDACTED');
  return redactFixedIdentifierLiterals(publicText)
    .replace(/\b(?:tenant|user)(?:[_\s-]*id)?\s*(?:=|:)?\s*\d+\b/gi, '[REDACTED_ID]')
    .replace(/\bfixed\s+(?:environment\s+)?id\s*\d+\b/gi, '[REDACTED_ID]')
    .replace(/\bfixed\s+user\s+\d+\b/gi, '[REDACTED_ID]');
}

function lineCount(text) {
  return text === '' ? 0 : text.split(/\r?\n/).length;
}

/**
 * Derives a deterministic, zero-row public admission projection from an already
 * captured MySQL manifest. This function intentionally receives file reads as a
 * dependency so tests can prove that excluded classes are never read.
 */
export function deriveZeroRowMySqlAdmission(input) {
  if (!isRecord(input)) admissionError('input must be an object');
  const snapshotRoot = requireString(input.snapshotRoot, 'snapshotRoot');
  if (!isRecord(input.manifest)) admissionError('manifest must be an object');
  if (typeof input.readText !== 'function') admissionError('readText must be a function');

  const manifest = input.manifest;
  if (manifest.snapshotId !== MYSQL_SNAPSHOT_ID) admissionError('unexpected MySQL snapshot id');
  if (!isRecord(manifest.objectCounts)) admissionError('manifest.objectCounts must be an object');
  if (!Array.isArray(manifest.evidenceFiles)) admissionError('manifest.evidenceFiles must be an array');

  const objectCounts = manifest.objectCounts;
  const includedObjectCounts = {
    tables: requireCount(objectCounts.tables, 'objectCounts.tables'),
    views: requireCount(objectCounts.views, 'objectCounts.views'),
    procedures: requireCount(objectCounts.procedures, 'objectCounts.procedures'),
    functions: requireCount(objectCounts.functions, 'objectCounts.functions'),
    triggers: requireCount(objectCounts.triggers, 'objectCounts.triggers'),
    events: requireCount(objectCounts.events, 'objectCounts.events'),
    dmlDigests: requireCount(objectCounts.dmlDigests, 'objectCounts.dmlDigests'),
  };

  if (includedObjectCounts.functions !== 0 || includedObjectCounts.triggers !== 0) {
    admissionError('the frozen snapshot must retain zero functions and triggers');
  }

  const artifacts = manifest.evidenceFiles
    .filter((entry) => {
      if (!isRecord(entry) || !ADMITTED_CATEGORIES.has(entry.category)) return false;
      const markers = [entry.relativePath, entry.objectType, entry.objectName].filter((value) => typeof value === 'string').join('\n');
      return !EXCLUDED_EVIDENCE_MARKER.test(markers);
    })
    .map((entry) => {
      const category = entry.category === 'CONSTRAINT' ? 'DDL' : entry.category;
      if (!PUBLIC_ARTIFACT_CATEGORIES.has(category)) admissionError(`unsupported admitted category: ${String(category)}`);
      const relativePath = requireSafeRelativePath(entry.relativePath, `relativePath for ${String(entry.evidenceId)}`);
      const privateText = input.readText(relativePath);
      if (typeof privateText !== 'string') admissionError(`readText must return text for ${relativePath}`);
      const artifactRef = requireString(entry.artifactRef ?? entry.evidenceId, `artifactRef for ${relativePath}`);
      return {
        artifactRef,
        evidenceClass: 'OBSERVED',
        category,
        relativePath,
        privateSha256: sha256(privateText),
        publicExcerpt: redactPublicText(privateText),
        locator: {
          kind: 'FILE_LINES',
          path: relativePath,
          startLine: 1,
          endLine: lineCount(privateText),
        },
      };
    })
    .sort((left, right) => left.artifactRef.localeCompare(right.artifactRef));

  const unsigned = {
    schemaVersion: 1,
    kind: 'GUANYIJIA_ZERO_ROW_CANDIDATE',
    basisSnapshotId: MYSQL_SNAPSHOT_ID,
    includedObjectCounts,
    exclusions: [...EXCLUSIONS],
    artifacts,
  };
  return Object.freeze({
    ...unsigned,
    admissionSha256: sha256(`${canonicalJson(unsigned)}\n`),
  });
}

function extractLines(text, startLine, endLine, label) {
  const lines = text.split(/\r?\n/);
  if (!Number.isInteger(startLine) || !Number.isInteger(endLine) || startLine < 1 || endLine < startLine || endLine > lines.length) {
    admissionError(`${label} has an invalid line range`);
  }
  return lines.slice(startLine - 1, endLine).join('\n');
}

function verifyDigest(text, expectedDigest, label) {
  if (!/^sha256:[a-f0-9]{64}$/.test(expectedDigest)) admissionError(`${label} has an invalid expected digest`);
  if (sha256(text) !== expectedDigest) admissionError(`${label} bytes do not match its stored digest`);
}

function findManifestFile(manifest, relativePath, label) {
  const entry = manifest.evidenceFiles.find((item) => isRecord(item) && item.relativePath === relativePath);
  if (!entry || typeof entry.sha256 !== 'string') admissionError(`${label} is not recorded in its manifest`);
  return entry;
}

function readFrozenFile(root, relativePath, label) {
  const absolutePath = path.resolve(root, relativePath);
  if (!absolutePath.startsWith(`${root}${path.sep}`)) admissionError(`${label} escaped the snapshot root`);
  return fs.readFile(absolutePath, 'utf8');
}

function publicFragment({ evidenceRef, topic, evidenceClass, title, excerpt, locationLabel, locationValue, sourceName, artifactDigest }) {
  if (!excerpt || /(?:samples?|profiles?|raw[-_ ]?row|query[-_ ]?result|bound[-_ ]?parameter|\bexample\b|Tenant\s+\d+|FROZEN_FILE|\bREAL\b|\bPRIMARY\b)/i.test(`${title}\n${excerpt}\n${locationLabel}\n${locationValue}\n${sourceName}`)) {
    admissionError(`public fragment ${evidenceRef} contains excluded content`);
  }
  return { evidenceRef, topic, evidenceClass, title, excerpt, locationLabel, locationValue, sourceName, artifactDigest };
}

function selectMigrationOperation(record, operationId) {
  if (!isRecord(record) || !Array.isArray(record.operations)) admissionError('GitHub migration record is malformed');
  const operation = record.operations.find((candidate) => isRecord(candidate) && candidate.operationId === operationId);
  if (!operation || typeof operation.excerpt !== 'string' || !operation.excerpt) {
    admissionError(`GitHub migration operation ${operationId} is unavailable`);
  }
  return operation;
}

function requireMigrationLocator(operation, expectedLine, label) {
  if (operation.lineStart !== expectedLine || operation.lineEnd !== expectedLine) {
    admissionError(`${label} has changed its stored source locator`);
  }
}

function createCandidateData({ mysqlManifest, githubManifest, mysqlFiles, githubFiles }) {
  const mysqlSystemConfig = mysqlFiles.systemConfig;
  const mysqlDepotHead = mysqlFiles.depotHead;
  const githubSystemConfig = githubFiles.systemConfig;
  const migrationRecord = githubFiles.migrationRecord;
  const debt = selectMigrationOperation(migrationRecord, 'migration-0566');
  const lastDebt = selectMigrationOperation(migrationRecord, 'migration-0567');
  const historicalStatus = selectMigrationOperation(migrationRecord, 'migration-0052');
  requireMigrationLocator(debt, 1842, 'debt migration operation');
  requireMigrationLocator(lastDebt, 1843, 'last-debt migration operation');
  requireMigrationLocator(historicalStatus, 312, 'historical status migration operation');

  const mysqlSystemConfigEntry = findManifestFile(mysqlManifest, 'ddl/tables/jsh_system_config.sql', 'MySQL system configuration DDL');
  const mysqlDepotHeadEntry = findManifestFile(mysqlManifest, 'ddl/tables/jsh_depot_head.sql', 'MySQL depot-head DDL');
  const githubSystemConfigEntry = findManifestFile(githubManifest, 'schema/tables/jsh_system_config.sql', 'GitHub system configuration record');
  const githubMigrationEntry = findManifestFile(githubManifest, 'migration-history/79e4bcae4768-数据库更新记录-首次安装请勿使用.txt.json', 'GitHub migration record');

  const mysqlSystemConfigDigest = `sha256:${mysqlSystemConfigEntry.sha256}`;
  const mysqlDepotHeadDigest = `sha256:${mysqlDepotHeadEntry.sha256}`;
  const githubSystemConfigDigest = `sha256:${githubSystemConfigEntry.sha256}`;
  const githubMigrationDigest = `sha256:${githubMigrationEntry.sha256}`;
  verifyDigest(mysqlSystemConfig, mysqlSystemConfigDigest, 'MySQL system configuration DDL');
  verifyDigest(mysqlDepotHead, mysqlDepotHeadDigest, 'MySQL depot-head DDL');
  verifyDigest(githubSystemConfig, githubSystemConfigDigest, 'GitHub system configuration record');
  verifyDigest(githubFiles.migrationText, githubMigrationDigest, 'GitHub migration record');
  if (/`(?:debt|last_debt)`/i.test(mysqlDepotHead)) {
    admissionError('the deployed MySQL depot-head DDL no longer supports the debt-field absence claim');
  }

  const fragments = [
    publicFragment({
      evidenceRef: 'mysql:jsh_system_config:minus_stock_flag',
      topic: 'NEGATIVE_STOCK',
      evidenceClass: 'OBSERVED',
      title: '负库存配置字段',
      excerpt: extractLines(mysqlSystemConfig, 12, 12, 'negative-stock locator'),
      locationLabel: '已保存 DDL 行',
      locationValue: 'ddl/tables/jsh_system_config.sql:L12',
      sourceName: 'MySQL 已保存快照',
      artifactDigest: mysqlSystemConfigDigest,
    }),
    publicFragment({
      evidenceRef: 'github:jsh_system_config:minus_stock_flag',
      topic: 'NEGATIVE_STOCK',
      evidenceClass: 'FROZEN_RECORD',
      title: '负库存配置记录',
      excerpt: extractLines(githubSystemConfig, 12, 12, 'GitHub negative-stock locator'),
      locationLabel: '冻结结构化记录行',
      locationValue: 'schema/tables/jsh_system_config.sql:L12',
      sourceName: 'GitHub 冻结结构化记录（未保留完整原文）',
      artifactDigest: githubSystemConfigDigest,
    }),
    publicFragment({
      evidenceRef: 'mysql:jsh_depot_head:debt_fields_absent',
      topic: 'DEBT_FIELDS',
      evidenceClass: 'OBSERVED',
      title: '已部署单据字段边界',
      excerpt: extractLines(mysqlDepotHead, 24, 27, 'debt-field locator'),
      locationLabel: '已保存 DDL 行',
      locationValue: 'ddl/tables/jsh_depot_head.sql:L24-L27',
      sourceName: 'MySQL 已保存快照',
      artifactDigest: mysqlDepotHeadDigest,
    }),
    publicFragment({
      evidenceRef: 'github:migration:jsh_depot_head:debt_fields',
      topic: 'DEBT_FIELDS',
      evidenceClass: 'FROZEN_RECORD',
      title: '欠款字段迁移记录',
      excerpt: `${debt.excerpt}\n${lastDebt.excerpt}`,
      locationLabel: '冻结迁移记录行',
      locationValue: 'migration-history/数据库更新记录:L1842-L1843',
      sourceName: 'GitHub 冻结结构化记录（未保留完整原文）',
      artifactDigest: githubMigrationDigest,
    }),
    publicFragment({
      evidenceRef: 'mysql:jsh_depot_head:document_status',
      topic: 'DOCUMENT_STATUS',
      evidenceClass: 'OBSERVED',
      title: '已部署单据状态字段',
      excerpt: extractLines(mysqlDepotHead, 27, 27, 'document-status locator'),
      locationLabel: '已保存 DDL 行',
      locationValue: 'ddl/tables/jsh_depot_head.sql:L27',
      sourceName: 'MySQL 已保存快照',
      artifactDigest: mysqlDepotHeadDigest,
    }),
    publicFragment({
      evidenceRef: 'github:migration:jsh_depot_head:historical_status',
      topic: 'DOCUMENT_STATUS',
      evidenceClass: 'FROZEN_RECORD',
      title: '历史单据状态迁移记录',
      excerpt: historicalStatus.excerpt,
      locationLabel: '冻结迁移记录行',
      locationValue: 'migration-history/数据库更新记录:L312',
      sourceName: 'GitHub 冻结结构化记录（未保留完整原文）',
      artifactDigest: githubMigrationDigest,
    }),
    publicFragment({
      evidenceRef: 'gap:official:document_status_9',
      topic: 'DOCUMENT_STATUS',
      evidenceClass: 'GAP',
      title: '状态 9 的官方资料缺口',
      excerpt: '完整官方文本未被保留；不以任何引文补全状态 9 的官方含义。',
      locationLabel: '保留状态',
      locationValue: '完整官方文本未保留',
      sourceName: '官方资料',
      artifactDigest: sha256('official-document-status-9-gap'),
    }),
  ];

  const claims = [
    {
      claimId: 'mysql-negative-stock-control', topic: 'NEGATIVE_STOCK', subjectRef: 'guanyijia:jsh_system_config.minus_stock_flag',
      predicate: 'TENANT_CONFIG_CONTROLS', normalizedValue: 'minus_stock_flag', scope: 'guanyijia:system-config-schema',
      effectiveTime: '2026-08-13', evidenceClass: 'OBSERVED', evidenceRefs: ['mysql:jsh_system_config:minus_stock_flag'],
    },
    {
      claimId: 'github-negative-stock-control', topic: 'NEGATIVE_STOCK', subjectRef: 'guanyijia:jsh_system_config.minus_stock_flag',
      predicate: 'TENANT_CONFIG_CONTROLS', normalizedValue: 'minus_stock_flag', scope: 'guanyijia:system-config-schema',
      effectiveTime: '2026-08-13', evidenceClass: 'FROZEN_RECORD', evidenceRefs: ['github:jsh_system_config:minus_stock_flag'],
    },
    {
      claimId: 'mysql-debt-fields-absent', topic: 'DEBT_FIELDS', subjectRef: 'guanyijia:jsh_depot_head',
      predicate: 'DEBT_FIELDS', normalizedValue: 'absent', scope: 'guanyijia:deployed-schema',
      effectiveTime: '2026-08-13', evidenceClass: 'OBSERVED', evidenceRefs: ['mysql:jsh_depot_head:debt_fields_absent'],
    },
    {
      claimId: 'github-debt-fields-present', topic: 'DEBT_FIELDS', subjectRef: 'guanyijia:jsh_depot_head',
      predicate: 'DEBT_FIELDS', normalizedValue: 'debt,last_debt', scope: 'guanyijia:deployed-schema',
      effectiveTime: '2026-08-13', evidenceClass: 'FROZEN_RECORD', evidenceRefs: ['github:migration:jsh_depot_head:debt_fields'],
    },
    {
      claimId: 'mysql-document-status-current', topic: 'DOCUMENT_STATUS', subjectRef: 'guanyijia:jsh_depot_head.status',
      predicate: 'DOCUMENT_STATUS_CODES', normalizedValue: '0,1,2,3,9', scope: 'guanyijia:deployed-schema',
      effectiveTime: '2026-08-13T03:25:28Z', evidenceClass: 'OBSERVED', evidenceRefs: ['mysql:jsh_depot_head:document_status'],
    },
    {
      claimId: 'github-document-status-historical', topic: 'DOCUMENT_STATUS', subjectRef: 'guanyijia:jsh_depot_head.status',
      predicate: 'DOCUMENT_STATUS_CODES', normalizedValue: '0,1,2', scope: 'guanyijia:deployed-schema',
      effectiveTime: '2026-08-13T03:21:26Z', evidenceClass: 'FROZEN_RECORD', evidenceRefs: ['github:migration:jsh_depot_head:historical_status'],
    },
    {
      claimId: 'official-document-status-9-gap', topic: 'DOCUMENT_STATUS', subjectRef: 'guanyijia:jsh_depot_head.status',
      predicate: 'OFFICIAL_STATUS_9_MEANING', normalizedValue: 'unknown', scope: 'official-documentation',
      evidenceClass: 'GAP', evidenceRefs: ['gap:official:document_status_9'],
    },
  ];

  const relations = [
    {
      leftClaimId: 'mysql-negative-stock-control', rightClaimId: 'github-negative-stock-control', relation: 'CORROBORATES',
      explanation: '两个已保存来源在相同范围和日期都记录了 minus_stock_flag 配置控制字段。',
    },
    {
      leftClaimId: 'mysql-debt-fields-absent', rightClaimId: 'github-debt-fields-present', relation: 'CONFLICTS',
      explanation: '已部署 DDL 的字段边界未见 debt 或 last_debt，而冻结迁移记录声明了这两个字段。',
    },
    {
      leftClaimId: 'mysql-document-status-current', rightClaimId: 'github-document-status-historical', relation: 'TEMPORAL_DRIFT',
      explanation: '已部署快照记录 0/1/2/3/9；冻结历史迁移记录只描述 0/1/2，两个保留快照时刻不同。',
    },
  ];

  const integrity = fragments.map((fragment) => ({
    evidenceRef: fragment.evidenceRef,
    artifactDigest: fragment.artifactDigest,
    excerptDigest: sha256(fragment.excerpt),
    locationValue: fragment.locationValue,
  }));

  return {
    bundle: {
      schemaVersion: 1,
      kind: 'GUANYIJIA_REAL_EVIDENCE_CANDIDATE',
      fragments,
      claims,
      relations,
    },
    integrity: {
      schemaVersion: 1,
      mysqlSnapshotId: MYSQL_SNAPSHOT_ID,
      githubSnapshotId: GITHUB_SNAPSHOT_ID,
      fragments: integrity,
    },
  };
}

function parseArguments(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    if (!['--snapshot-root', '--mysql-snapshot-root', '--github-snapshot-root', '--output'].includes(flag)) {
      admissionError(`unknown argument: ${flag}`);
    }
    const value = argv[index + 1];
    if (!value || value.startsWith('--') || args[flag]) admissionError(`missing or repeated value for ${flag}`);
    args[flag] = value;
    index += 1;
  }
  const mysqlSnapshotRoot = args['--mysql-snapshot-root'] ?? args['--snapshot-root'];
  if (!mysqlSnapshotRoot || !args['--output']) {
    admissionError('usage: --mysql-snapshot-root <absolute-path> [--github-snapshot-root <absolute-path>] --output <path>');
  }
  return {
    mysqlSnapshotRoot,
    githubSnapshotRoot: args['--github-snapshot-root'] ?? EXPECTED_GITHUB_SNAPSHOT_ROOT,
    output: args['--output'],
  };
}

function assertFixedRoot(actual, expected, label) {
  if (!path.isAbsolute(actual) || path.resolve(actual) !== expected) {
    admissionError(`${label} must be the approved frozen root`);
  }
}

function assertPublicOutput(output) {
  const absoluteOutput = path.resolve(process.cwd(), output);
  if (absoluteOutput !== path.join(PUBLIC_OUTPUT_DIRECTORY, 'guanyijia-candidate-evidence.generated.ts')) {
    admissionError('output must be the candidate generated asset under the prototype source directory');
  }
  return absoluteOutput;
}

function renderGeneratedModule(data) {
  return `/**\n * Generated only by \`npm run evidence:guanyijia:admit\`.\n * Inputs: the two approved local frozen Guanyijia snapshots.\n * This public candidate contains redacted excerpts and relative locators only.\n */\n\nexport const generatedCandidateEvidence = ${JSON.stringify(data.bundle, null, 2)} as const;\n\nexport const generatedCandidateEvidenceIntegrity = ${JSON.stringify(data.integrity, null, 2)} as const;\n`;
}

async function main() {
  const args = parseArguments(process.argv.slice(2));
  assertFixedRoot(args.mysqlSnapshotRoot, EXPECTED_MYSQL_SNAPSHOT_ROOT, 'MySQL snapshot root');
  assertFixedRoot(args.githubSnapshotRoot, EXPECTED_GITHUB_SNAPSHOT_ROOT, 'GitHub snapshot root');
  const output = assertPublicOutput(args.output);

  const [mysqlManifestText, githubManifestText, systemConfig, depotHead, githubSystemConfig, migrationText] = await Promise.all([
    readFrozenFile(args.mysqlSnapshotRoot, 'manifest.json', 'MySQL manifest'),
    readFrozenFile(args.githubSnapshotRoot, 'manifest.json', 'GitHub manifest'),
    readFrozenFile(args.mysqlSnapshotRoot, 'ddl/tables/jsh_system_config.sql', 'MySQL system configuration DDL'),
    readFrozenFile(args.mysqlSnapshotRoot, 'ddl/tables/jsh_depot_head.sql', 'MySQL depot-head DDL'),
    readFrozenFile(args.githubSnapshotRoot, 'schema/tables/jsh_system_config.sql', 'GitHub system configuration record'),
    readFrozenFile(args.githubSnapshotRoot, 'migration-history/79e4bcae4768-数据库更新记录-首次安装请勿使用.txt.json', 'GitHub migration record'),
  ]);
  const mysqlManifest = JSON.parse(mysqlManifestText);
  const githubManifest = JSON.parse(githubManifestText);
  if (mysqlManifest.snapshotId !== MYSQL_SNAPSHOT_ID || githubManifest.snapshotId !== GITHUB_SNAPSHOT_ID) {
    admissionError('source manifests do not match the approved frozen snapshots');
  }

  // Admission is exercised before selecting the small public packet. Its full
  // projection remains in memory and is deliberately never written to the demo.
  deriveZeroRowMySqlAdmission({
    snapshotRoot: args.mysqlSnapshotRoot,
    manifest: mysqlManifest,
    readText(relativePath) {
      const safePath = requireSafeRelativePath(relativePath, 'manifest relativePath');
      const filePath = path.resolve(args.mysqlSnapshotRoot, safePath);
      if (!filePath.startsWith(`${args.mysqlSnapshotRoot}${path.sep}`)) admissionError('manifest relativePath escaped the snapshot root');
      return readFileSync(filePath, 'utf8');
    },
  });

  const data = createCandidateData({
    mysqlManifest,
    githubManifest,
    mysqlFiles: { systemConfig, depotHead },
    githubFiles: { systemConfig: githubSystemConfig, migrationText, migrationRecord: JSON.parse(migrationText) },
  });
  await fs.writeFile(output, renderGeneratedModule(data), 'utf8');
  process.stdout.write(`Wrote ${path.relative(process.cwd(), output)} from approved frozen evidence snapshots.\n`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  });
}
