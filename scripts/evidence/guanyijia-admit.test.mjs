import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import test from 'node:test';

let admissionModule;
let admissionImportError;
try {
  admissionModule = await import('./guanyijia-admit.mjs');
} catch (error) {
  admissionImportError = error;
}

const snapshotRoot = '/private/guanyijia-evidence/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79';

const systemConfigSql = [
  'CREATE TABLE `jsh_system_config` (',
  '  `id` bigint NOT NULL,',
  '  `tenant_id` bigint NOT NULL COMMENT \'Tenant 153\',',
  '  `user_id` bigint DEFAULT NULL COMMENT \'fixed user 10402000\',',
  '  `minus_stock_flag` tinyint DEFAULT NULL COMMENT \'negative stock\',',
  '  PRIMARY KEY (`id`), CONSTRAINT `tenant_user_scope` CHECK (`tenant_id` = 153 AND `user_id` = 10402000)',
  ') ENGINE=InnoDB AUTO_INCREMENT=10402000 COMMENT=\'Tenant 153 deployment\';',
  '-- fixed environment id 10402000',
].join('\n');

const programmabilitySql = [
  'CREATE PROCEDURE `admit_negative_stock_candidate`()',
  'BEGIN',
  '  SELECT `minus_stock_flag` FROM `jsh_system_config`;',
  'END;',
].join('\n');

const dmlDigestSql = [
  '-- Parameterized digest; no business rows are retained.',
  'SELECT `minus_stock_flag` FROM `jsh_system_config`',
  'WHERE `tenant_id` = ? AND `user_id` = ? AND `id` = ?;',
  'EXECUTION_COUNT=17',
  'SUM_TIMER_WAIT=42',
  'FIRST_SEEN=2026-08-13T03:25:28Z',
  'LAST_SEEN=2026-08-13T03:25:28Z',
].join('\n');

const files = new Map([
  ['ddl/tables/jsh_system_config.sql', systemConfigSql],
  ['programmability/procedures/admit_negative_stock_candidate.sql', programmabilitySql],
  ['dml/digests.sql', dmlDigestSql],
  ['samples/rows/jsh_system_config.json', '{"sample-row":{"tenant_id":153}}'],
  ['profiles/queries/jsh_system_config.sql', '-- profile-example with result rows'],
]);

const manifest = {
  schemaVersion: 1,
  kind: 'MYSQL_ZERO_ROW_SNAPSHOT_MANIFEST',
  snapshotId: '20260813T032528Z-abb0502c7d79',
  databaseName: 'jsh_erp',
  tenantId: 153,
  objectCounts: {
    tables: 95,
    views: 2,
    procedures: 35,
    functions: 0,
    triggers: 0,
    events: 1,
    dmlDigests: 17,
  },
  evidenceFiles: [
    {
      evidenceId: 'mysql_jsh_erp@v1:DDL:jsh_system_config',
      artifactRef: 'artifact:mysql:jsh_erp:ddl/tables/jsh_system_config.sql',
      category: 'DDL',
      objectType: 'TABLE',
      objectName: 'jsh_system_config',
      relativePath: 'ddl/tables/jsh_system_config.sql',
      sha256: 'fixture-private-sha-not-used-by-admission',
    },
    {
      evidenceId: 'mysql_jsh_erp@v1:PROGRAMMABILITY:admit_negative_stock_candidate',
      artifactRef: 'artifact:mysql:jsh_erp:programmability/procedures/admit_negative_stock_candidate.sql',
      category: 'PROGRAMMABILITY',
      objectType: 'PROCEDURE',
      objectName: 'admit_negative_stock_candidate',
      relativePath: 'programmability/procedures/admit_negative_stock_candidate.sql',
      sha256: 'fixture-private-sha-not-used-by-admission',
    },
    {
      evidenceId: 'mysql_jsh_erp@v1:DML_DIGEST:minus_stock_flag',
      artifactRef: 'artifact:mysql:jsh_erp:dml/digests.sql',
      category: 'DML_DIGEST',
      objectType: 'PARAMETERIZED_DIGEST',
      objectName: 'jsh_system_config.minus_stock_flag',
      relativePath: 'dml/digests.sql',
      sha256: 'fixture-private-sha-not-used-by-admission',
    },
    {
      evidenceId: 'mysql_jsh_erp@v1:SAMPLE:jsh_system_config',
      artifactRef: 'artifact:mysql:jsh_erp:samples/rows/jsh_system_config.json',
      category: 'SAMPLE',
      objectType: 'RAW_ROW',
      objectName: 'jsh_system_config',
      relativePath: 'samples/rows/jsh_system_config.json',
      sha256: 'fixture-private-sha-not-used-by-admission',
    },
    {
      evidenceId: 'mysql_jsh_erp@v1:PROFILE:jsh_system_config',
      artifactRef: 'artifact:mysql:jsh_erp:profiles/queries/jsh_system_config.sql',
      category: 'PROFILE',
      objectType: 'QUERY_RESULT',
      objectName: 'jsh_system_config',
      relativePath: 'profiles/queries/jsh_system_config.sql',
      sha256: 'fixture-private-sha-not-used-by-admission',
    },
  ],
};

function digestText(text) {
  return `sha256:${createHash('sha256').update(text, 'utf8').digest('hex')}`;
}

function deriveAdmission() {
  if (admissionImportError) {
    throw new Error(
      `admission implementation module is unavailable: ${admissionImportError.message}`,
      { cause: admissionImportError },
    );
  }
  assert.equal(
    typeof admissionModule.deriveZeroRowMySqlAdmission,
    'function',
    'deriveZeroRowMySqlAdmission must be exported',
  );
  return admissionModule.deriveZeroRowMySqlAdmission({
    snapshotRoot,
    manifest,
    readText(relativePath) {
      assert.ok(files.has(relativePath), `fixture file must exist: ${relativePath}`);
      return files.get(relativePath);
    },
  });
}

test('admits only zero-row MySQL evidence with redacted, stable candidate artifacts', () => {
  const result = deriveAdmission();
  const repeated = deriveAdmission();

  assert.deepEqual(result.exclusions, [
    'samples/**', 'profiles/**', 'raw-row', 'query-result', 'bound-parameter', 'example',
  ]);
  assert.deepEqual(result.artifacts.map(({ category }) => category), [
    'DDL', 'DML_DIGEST', 'PROGRAMMABILITY',
  ]);
  assert.doesNotMatch(
    JSON.stringify(result),
    /sample-row|profile-example|Tenant 153|AUTO_INCREMENT=10402000/,
  );
  assert.match(result.artifacts[0].publicExcerpt, /AUTO_INCREMENT=REDACTED/);
  assert.doesNotMatch(JSON.stringify(result), /EXECUTION_COUNT|SUM_TIMER_WAIT|FIRST_SEEN|LAST_SEEN/);
  assert.equal(result.includedObjectCounts.functions, 0);
  assert.equal(result.includedObjectCounts.triggers, 0);
  assert.deepEqual(result.artifacts[0].locator, {
    kind: 'FILE_LINES', path: 'ddl/tables/jsh_system_config.sql', startLine: 1, endLine: 8,
  });

  assert.equal(result.artifacts[0].privateSha256, digestText(systemConfigSql));
  assert.match(result.artifacts[0].publicExcerpt, /`tenant_id` = \[REDACTED_ID\]/);
  assert.match(result.artifacts[0].publicExcerpt, /`user_id` = \[REDACTED_ID\]/);
  assert.doesNotMatch(JSON.stringify(result), /`tenant_id`\s*=\s*153/);
  assert.doesNotMatch(JSON.stringify(result), /`user_id`\s*=\s*10402000/);
  assert.match(result.admissionSha256, /^sha256:[0-9a-f]{64}$/);
  assert.equal(result.admissionSha256, repeated.admissionSha256);
});
