import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import * as ts from 'typescript';

let generationModule;
let generationImportError;
try {
  generationModule = await import('./guanyijia-generate.mjs');
} catch (error) {
  generationImportError = error;
}

const proposalSchemaSource = await readFile(
  new URL('./schemas/guanyijia-proposal.schema.json', import.meta.url),
  'utf8',
);

// This is the reviewed production asset path. The fixture hashes its exact
// bytes; no test-local prompt content is substituted.
const expectedPromptTemplatePath = new URL(
  './prompts/guanyijia-proposal-instructions.txt',
  import.meta.url,
);
const promptTemplateBytes = await readFile(expectedPromptTemplatePath);
const reviewedPromptTemplateSha256 = digest(promptTemplateBytes);
const trustedCodexBinarySha256 = 'sha256:494eb6397b32448a71c9b893178cfa3b3307da6e65860d17ca3c41646df57b0a';
const currentPromptTemplateLabelSha256 = digest('guanyijia-proposal-prompt-template-v1');
const currentProposalSchemaLabelSha256 = 'sha256:41e1eaaf6c8279fc835181629dbebc92550ae6e7a384d12e0cd11a09107a0bfb';

const sourceId = 'source:mysql:jsh_erp';
const snapshotId = 'snapshot:mysql:jsh_erp:20260820T000000Z';
const manifestId = 'raw-manifest:mysql:jsh_erp:20260820T000000Z';
const artifactRef = 'artifact:mysql:jsh_erp:ddl/orders.sql';
const artifactBytes = new TextEncoder().encode([
  'CREATE TABLE orders (',
  '  id BIGINT NOT NULL,',
  '  PRIMARY KEY (id)',
  ');',
  '',
].join('\n'));
const artifactSha256 = `sha256:${createHash('sha256').update(artifactBytes).digest('hex')}`;
const artifactLocator = {
  kind: 'FILE_LINES',
  path: 'ddl/orders.sql',
  startLine: 1,
  endLine: 4,
};
const exactExcerpt = new TextDecoder().decode(artifactBytes).trimEnd();

function stableJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`;
}

function digest(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function validRawManifest() {
  const body = {
    schemaVersion: 1,
    manifestId,
    source: {
      sourceId,
      sourceName: '管伊佳 ERP MySQL',
      sourceClass: 'REAL',
      snapshotId,
      authority: 'PRIMARY',
      lineageStatus: 'ROOT',
      upstreamSourceIds: [],
    },
    capturedAt: '2026-08-20T00:00:00.000Z',
    artifacts: [{ artifactRef, sha256: artifactSha256, locator: artifactLocator }],
  };
  return {
    ...body,
    manifestSha256: digest(`${stableJson(body)}\n`),
  };
}

function validProposal() {
  return {
    schemaVersion: 1,
    kind: 'PROPOSAL',
    sourceManifestId: manifestId,
    statements: [{
      statementId: 'proposal-statement:orders',
      text: 'orders 使用 id 作为主键。',
      citations: [{
        evidenceKind: 'OBSERVED',
        artifactRef,
        locator: artifactLocator,
        excerpt: exactExcerpt,
      }],
    }],
  };
}

function manifestAndProposalForArtifact({ bytes, locator, excerpt }) {
  const body = {
    schemaVersion: 1,
    manifestId,
    source: {
      sourceId,
      sourceName: '管伊佳 ERP MySQL',
      sourceClass: 'REAL',
      snapshotId,
      authority: 'PRIMARY',
      lineageStatus: 'ROOT',
      upstreamSourceIds: [],
    },
    capturedAt: '2026-08-20T00:00:00.000Z',
    artifacts: [{ artifactRef, sha256: digest(bytes), locator }],
  };
  const proposal = validProposal();
  proposal.statements[0].citations[0] = {
    ...proposal.statements[0].citations[0],
    locator,
    excerpt,
  };
  return {
    bytes,
    manifest: { ...body, manifestSha256: digest(`${stableJson(body)}\n`) },
    proposal,
  };
}

function generatedTargetProposal({
  semanticKind = 'GAP',
  evidenceStatus = 'GAP',
  provenance = 'INFERRED',
  confirmationStatus = 'PENDING_HUMAN_CONFIRMATION',
  mixObserved = false,
  citationExtras = {},
} = {}) {
  const base = validProposal();
  const generatedCitation = {
    evidenceKind: 'GENERATED_TARGET',
    evidenceRef: 'generated-target:orders-gap',
    title: '待补充订单业务口径',
    excerpt: '订单业务口径待人工确认。',
    locator: { kind: 'GENERATED_TARGET', confirmationStatus },
    ...citationExtras,
  };
  base.statements[0] = {
    ...base.statements[0],
    statementId: 'proposal-statement:orders-gap',
    text: '订单业务口径待补充。',
    semanticKind,
    evidenceStatus,
    provenance,
    citations: [
      generatedCitation,
      ...(mixObserved ? [validProposal().statements[0].citations[0]] : []),
    ],
  };
  return base;
}

function strictArtifact(artifactRef, text, locator) {
  const bytes = new TextEncoder().encode(text);
  return {
    artifactRef,
    bytes,
    sha256: digest(bytes),
    locator,
    excerpt: text,
  };
}

function strictSourceSpecs() {
  return [
    {
      sourceId: 'guanyijia_mysql',
      snapshotId: 'snapshot:guanyijia_mysql:20260820T000000Z',
      manifestRef: 'raw-manifest:guanyijia_mysql:20260820T000000Z',
      manifestSha256: digest('manifest:guanyijia_mysql'),
      artifactCatalogSha256: digest('catalog:guanyijia_mysql'),
      artifacts: [
        strictArtifact(
          'artifact:guanyijia_mysql:ddl/orders.sql',
          'CREATE TABLE orders (\n  id BIGINT NOT NULL,\n);',
          { kind: 'FILE_LINES', path: 'ddl/orders.sql', startLine: 1, endLine: 3 },
        ),
        strictArtifact(
          'artifact:guanyijia_mysql:index/orders',
          'orders.id BIGINT NOT NULL',
          { kind: 'SQL', schema: 'jsh_erp', object: 'orders', symbol: 'id' },
        ),
      ],
    },
    {
      sourceId: 'guanyijia_github',
      snapshotId: 'snapshot:guanyijia_github:20260820T000000Z',
      manifestRef: 'raw-manifest:guanyijia_github:20260820T000000Z',
      manifestSha256: digest('manifest:guanyijia_github'),
      artifactCatalogSha256: digest('catalog:guanyijia_github'),
      artifacts: [
        strictArtifact(
          'artifact:guanyijia_github:src/orders.ts',
          'export function orders() { return "id"; }',
          { kind: 'SOURCE_SYMBOL', path: 'src/orders.ts', symbol: 'orders', startLine: 1, endLine: 1 },
        ),
      ],
    },
    {
      sourceId: 'guanyijia_official_docs',
      snapshotId: 'snapshot:guanyijia_official_docs:20260820T000000Z',
      manifestRef: 'raw-manifest:guanyijia_official_docs:20260820T000000Z',
      manifestSha256: digest('manifest:guanyijia_official_docs'),
      artifactCatalogSha256: digest('catalog:guanyijia_official_docs'),
      artifacts: [
        strictArtifact(
          'artifact:guanyijia_official_docs:orders.md',
          '订单文档第1节。',
          { kind: 'DOCUMENT_SECTION', document: 'orders.md', section: '字段定义', page: 1 },
        ),
      ],
    },
  ];
}

function strictPromptInputPacket({
  storyKey,
  runId,
  admittedSources,
  resolvedCitations,
  promptTemplateSha256,
  proposalSchemaSha256,
  citationMaterialSha256,
}) {
  return {
    schemaVersion: 1,
    kind: 'GUANYIJIA_PROPOSAL_PROMPT_INPUT',
    storyKey,
    runId,
    promptTemplateSha256,
    proposalSchemaSha256,
    admittedSources,
    citationMaterialSha256,
    citationMaterial: resolvedCitations,
  };
}

function strictPromptInputBytes(input) {
  const promptInputPacket = strictPromptInputPacket(input);
  return new TextEncoder().encode(`${stableJson(promptInputPacket)}\n`);
}

function strictPromptInputDigest(input) {
  const promptInputBytes = strictPromptInputBytes(input);
  return digest(promptInputBytes);
}

function strictInvocationEnvelope({ storyKey, runId, admittedSources, resolvedCitations }) {
  const citationMaterialSha256 = digest(`${stableJson(resolvedCitations)}\n`);
  const proposalSchemaSha256 = digest(proposalSchemaSource);
  const envelopeBody = {
    schemaVersion: 1,
    kind: 'GUANYIJIA_PROPOSAL_INVOCATION',
    storyKey,
    runId,
    executor: {
      client: 'CODEX_CLI',
      cliVersion: 'codex-cli 0.148.0-alpha.15',
      codexBinarySha256: trustedCodexBinarySha256,
      requestedModel: 'gpt-5.6-luna',
      requestedReasoningEffort: 'xhigh',
      requiredProvider: 'openai',
      requiredAuthMode: 'CHATGPT_SESSION',
      sandbox: 'READ_ONLY',
      approvalPolicy: 'NEVER',
      sessionPersistence: 'EPHEMERAL',
      toolPolicy: 'NO_TOOLS',
      attemptLimit: 1,
    },
    inputs: admittedSources,
    rawManifestDigest: digest(`${stableJson(admittedSources)}\n`),
    citationMaterialSha256,
    promptTemplateSha256: reviewedPromptTemplateSha256,
    promptInputSha256: strictPromptInputDigest({
      storyKey,
      runId,
      admittedSources,
      resolvedCitations,
      promptTemplateSha256: reviewedPromptTemplateSha256,
      proposalSchemaSha256,
      citationMaterialSha256,
    }),
    proposalSchemaSha256,
  };
  return {
    ...envelopeBody,
    envelopeSha256: digest(`${stableJson(envelopeBody)}\n`),
  };
}

function strictProposalFixture() {
  const storyKey = 'guanyijia-five-source-v2';
  const runId = 'proposal-run:20260820T000000Z';
  const sources = strictSourceSpecs();
  const admittedSources = sources.map(({ sourceId, snapshotId, manifestRef, manifestSha256, artifactCatalogSha256 }) => ({
    sourceId,
    snapshotId,
    manifestRef,
    manifestSha256,
    artifactCatalogSha256,
  }));
  const plans = [
    { sourceId: 'guanyijia_mysql', artifactRef: 'artifact:guanyijia_mysql:ddl/orders.sql', proposalId: 'proposal:mysql:orders-table', stableCode: 'mysql.orders.table' },
    { sourceId: 'guanyijia_mysql', artifactRef: 'artifact:guanyijia_mysql:index/orders', proposalId: 'proposal:mysql:orders-id', stableCode: 'mysql.orders.id' },
    { sourceId: 'guanyijia_github', artifactRef: 'artifact:guanyijia_github:src/orders.ts', proposalId: 'proposal:github:orders-function', stableCode: 'github.orders.function' },
    { sourceId: 'guanyijia_official_docs', artifactRef: 'artifact:guanyijia_official_docs:orders.md', proposalId: 'proposal:docs:orders-section', stableCode: 'docs.orders.section' },
  ];
  const citationRecords = [];
  const sourceProposals = sources.map((source) => ({
    sourceId: source.sourceId,
    snapshotId: source.snapshotId,
    statements: plans.filter((plan) => plan.sourceId === source.sourceId).map((plan) => {
      const artifact = source.artifacts.find((candidate) => candidate.artifactRef === plan.artifactRef);
      assert.ok(artifact, `strict fixture artifact exists: ${plan.artifactRef}`);
      const evidenceRef = `evidence:${plan.proposalId}`;
      const citation = {
        evidenceKind: 'OBSERVED',
        evidenceRef,
        title: `独立证据 ${plan.proposalId}`,
        excerpt: artifact.excerpt,
        sourceId: source.sourceId,
        snapshotId: source.snapshotId,
        artifactRef: artifact.artifactRef,
        artifactSha256: artifact.sha256,
        locator: structuredClone(artifact.locator),
      };
      citationRecords.push({
        evidenceKind: 'OBSERVED',
        evidenceRef,
        sourceId: source.sourceId,
        snapshotId: source.snapshotId,
        artifactRef: artifact.artifactRef,
        artifactSha256: artifact.sha256,
        locator: structuredClone(artifact.locator),
        excerpt: artifact.excerpt,
      });
      return {
        proposalId: plan.proposalId,
        section: 'OBJECT',
        semanticKind: 'ENTITY',
        stableCode: plan.stableCode,
        label: `订单证据 ${plan.proposalId}`,
        value: { normalized: plan.proposalId, text: artifact.excerpt },
        statement: `独立来源支持 ${plan.proposalId}。`,
        evidenceStatus: 'FACT',
        provenance: 'OBSERVED',
        affectedObjectRefs: [],
        evidence: [citation],
      };
    }),
  }));
  const invocationEnvelope = strictInvocationEnvelope({
    storyKey,
    runId,
    admittedSources,
    resolvedCitations: citationRecords,
  });
  const proposal = {
    schemaVersion: 1,
    kind: 'GUANYIJIA_EVIDENCE_PROPOSAL',
    storyKey,
    runId,
    invocationEnvelopeSha256: invocationEnvelope.envelopeSha256,
    sourceProposals,
  };
  return {
    proposal,
    admittedSources,
    resolvedCitations: citationRecords,
    invocationEnvelope,
  };
}

function rebindStrictInvocationEnvelope(fixture, mutate = () => {}) {
  const envelope = structuredClone(fixture.invocationEnvelope);
  mutate(envelope);
  const { envelopeSha256, ...envelopeBody } = envelope;
  void envelopeSha256;
  envelope.envelopeSha256 = digest(`${stableJson(envelopeBody)}\n`);
  fixture.invocationEnvelope = envelope;
  fixture.proposal.invocationEnvelopeSha256 = envelope.envelopeSha256;
  return fixture;
}

function strictGeneratedStatement(sourceId, index) {
  const evidenceRef = `generated:${sourceId}:${index}`;
  return {
    proposalId: `proposal:${sourceId}:generated:${index}`,
    section: 'UNRESOLVED',
    semanticKind: 'GAP',
    stableCode: `${sourceId}.pending.${index}`,
    label: `待补充 ${sourceId} ${index}`,
    value: { normalized: `pending-${index}`, text: '待人工确认。' },
    statement: '该目标仅待人工确认，未声称为观察事实。',
    evidenceStatus: 'GAP',
    provenance: 'INFERRED',
    affectedObjectRefs: [],
    evidence: [{
      evidenceKind: 'GENERATED_TARGET',
      evidenceRef,
      title: `待确认目标 ${index}`,
      excerpt: '待人工确认。',
      locator: { kind: 'GENERATED_TARGET', confirmationStatus: 'PENDING_HUMAN_CONFIRMATION' },
    }],
  };
}

function strictGeneratedOnlyFixture() {
  const fixture = strictProposalFixture();
  fixture.proposal.sourceProposals = fixture.proposal.sourceProposals.map((sourceProposal, index) => ({
    ...sourceProposal,
    statements: [strictGeneratedStatement(sourceProposal.sourceId, index)],
  }));
  fixture.resolvedCitations = [];
  rebindStrictInvocationEnvelope(fixture, (envelope) => {
    envelope.citationMaterialSha256 = digest(`${stableJson([])}\n`);
    envelope.promptInputSha256 = strictPromptInputDigest({
      storyKey: fixture.proposal.storyKey,
      runId: fixture.proposal.runId,
      admittedSources: fixture.admittedSources,
      resolvedCitations: [],
      promptTemplateSha256: envelope.promptTemplateSha256,
      proposalSchemaSha256: envelope.proposalSchemaSha256,
      citationMaterialSha256: envelope.citationMaterialSha256,
    });
  });
  return fixture;
}

function validateStrictProposal(module, fixture) {
  return module.validateProposalCitations({
    proposal: fixture.proposal,
    admittedSources: fixture.admittedSources,
    resolvedCitations: fixture.resolvedCitations,
    invocationEnvelope: fixture.invocationEnvelope,
  });
}

// Deliberately mirrors the current implementation's weak attestation values
// so each new attack reaches the claimed trust check rather than failing on a
// legacy fixture mismatch. The test must remain rejected after the production
// seam learns the stronger independent domains.
function currentLabelCompatibleStrictFixture() {
  const fixture = strictProposalFixture();
  rebindStrictInvocationEnvelope(fixture, (envelope) => {
    envelope.promptTemplateSha256 = currentPromptTemplateLabelSha256;
    envelope.proposalSchemaSha256 = currentProposalSchemaLabelSha256;
    envelope.promptInputSha256 = envelope.citationMaterialSha256;
  });
  return fixture;
}

const controlledCodexBinaryPath = '/private/maintenance/bin/codex';
const controlledCodexWorkingDirectory = '/private/guanyijia-codex-run';
const controlledCodexSchemaPath = '/private/guanyijia/schema/guanyijia-proposal.schema.json';
const controlledCodexStagingPath = '/private/guanyijia/staging/proposal.json';
const controlledCodexEnvironment = Object.freeze({
  PATH: '/private/maintenance/bin',
  LANG: 'C.UTF-8',
  LC_ALL: 'C.UTF-8',
  TMPDIR: '/private/guanyijia/codex-tmp',
  CODEX_HOME: '/private/guanyijia/codex-home',
});
const controlledCodexToolDisables = [
  'apps',
  'artifact',
  'browser_use',
  'browser_use_external',
  'browser_use_full_cdp_access',
  'code_mode',
  'code_mode_host',
  'computer_use',
  'enable_mcp_apps',
  'goals',
  'hooks',
  'image_generation',
  'in_app_browser',
  'mcp_2026_07_28',
  'multi_agent',
  'multi_agent_v2',
  'plugin_sharing',
  'plugins',
  'recommended_plugins',
  'remote_plugin',
  'request_permissions_tool',
  'shell_tool',
  'skill_mcp_dependency_install',
  'skill_search',
  'standalone_web_search',
  'tool_call_mcp_elicitation',
  'unified_exec',
  'view_image',
  'workspace_dependencies',
];

function controlledExecutorFixture() {
  const fixture = strictProposalFixture();
  const promptInputBytes = strictPromptInputBytes({
    storyKey: fixture.proposal.storyKey,
    runId: fixture.proposal.runId,
    admittedSources: fixture.admittedSources,
    resolvedCitations: fixture.resolvedCitations,
    promptTemplateSha256: fixture.invocationEnvelope.promptTemplateSha256,
    proposalSchemaSha256: fixture.invocationEnvelope.proposalSchemaSha256,
    citationMaterialSha256: fixture.invocationEnvelope.citationMaterialSha256,
  });
  assert.equal(digest(promptInputBytes), fixture.invocationEnvelope.promptInputSha256);

  const promptText = new TextDecoder('utf-8', { fatal: true }).decode(promptTemplateBytes);
  const proposalSchemaBytes = new TextEncoder().encode(proposalSchemaSource);
  const executionAdmissionResult = {
    executablePath: controlledCodexBinaryPath,
    codexBinarySha256: trustedCodexBinarySha256,
    promptTemplateBytes: new Uint8Array(promptTemplateBytes),
    proposalSchemaPath: controlledCodexSchemaPath,
    proposalSchemaBytes,
    workingDirectory: {
      path: controlledCodexWorkingDirectory,
      mode: 0o700,
      empty: true,
    },
    lastMessagePath: controlledCodexStagingPath,
  };
  const expectedAdmissionRequest = {
    runId: fixture.proposal.runId,
    expectedCodexBinarySha256: trustedCodexBinarySha256,
    expectedPromptTemplateSha256: reviewedPromptTemplateSha256,
    expectedProposalSchemaSha256: digest(proposalSchemaSource),
  };
  const admissionCalls = [];

  const calls = [];
  const codexSession = {
    async run(command) {
      calls.push(command);
      if (command.argv.length === 1 && command.argv[0] === '--version') {
        return {
          exitCode: 0,
          signal: null,
          stdout: new TextEncoder().encode('codex-cli 0.148.0-alpha.15\n'),
          stderr: new Uint8Array(),
        };
      }
      if (command.argv.length === 2 && command.argv[0] === 'login' && command.argv[1] === 'status') {
        return {
          exitCode: 0,
          signal: null,
          stdout: new TextEncoder().encode('Logged in using ChatGPT\n'),
          stderr: new Uint8Array(),
        };
      }
      return {
        exitCode: 0,
        signal: null,
        stdout: new Uint8Array(),
        stderr: new Uint8Array(),
        lastMessageBytes: new TextEncoder().encode(JSON.stringify(fixture.proposal)),
        audit: {
          completionEventCount: 1,
          toolCallCount: 0,
        },
      };
    },
  };
  const executionAdmission = {
    async admit(request) {
      admissionCalls.push(request);
      return structuredClone(executionAdmissionResult);
    },
  };
  return {
    fixture,
    executionAdmission,
    executionAdmissionResult,
    expectedAdmissionRequest,
    admissionCalls,
    promptText,
    calls,
    codexSession,
  };
}

function controlledAuthorization(harness, flag = '--authorize-chatgpt-generation') {
  if (flag === undefined) return undefined;
  return {
    kind: 'CHATGPT_GENERATION_AUTHORIZATION',
    flag,
    expectedGenerationInputSha256: harness.fixture.invocationEnvelope.promptInputSha256,
  };
}

function controlledExecutorRequest(harness, ...authorizationArgs) {
  const authorization = authorizationArgs.length === 0
    ? controlledAuthorization(harness)
    : authorizationArgs[0];
  return {
    authorization,
    invocationEnvelope: harness.fixture.invocationEnvelope,
    admittedSources: harness.fixture.admittedSources,
    resolvedCitations: harness.fixture.resolvedCitations,
  };
}

function expectedControlledCodexArgv(admission) {
  return [
    'exec',
    '--strict-config',
    '--ignore-user-config',
    '--ignore-rules',
    '--ephemeral',
    '-m', 'gpt-5.6-luna',
    '-c', 'model_provider="openai"',
    '-c', 'model_reasoning_effort="xhigh"',
    '-c', 'forced_login_method="chatgpt"',
    '-c', 'approval_policy="never"',
    '-s', 'read-only',
    '-C', admission.workingDirectory.path,
    '--skip-git-repo-check',
    '--json',
    '--color', 'never',
    '--output-schema', admission.proposalSchemaPath,
    '--output-last-message', admission.lastMessagePath,
    ...controlledCodexToolDisables.flatMap((tool) => ['--disable', tool]),
    '-c', 'tools.web_search=false',
    '-',
  ];
}

function createControlledExecutor(module, harness, env = controlledCodexEnvironment) {
  return module.createControlledCodexExecutor({
    env,
    executionAdmission: harness.executionAdmission,
    codexSession: harness.codexSession,
  });
}

function assertSafeExecutorError(expectedCode) {
  return (error) => {
    assert.equal(error?.name, 'ProposalGenerationError');
    assert.equal(error?.code, expectedCode);
    assert.doesNotMatch(String(error?.message ?? error), /Authorization_Bearer_SECRET|private-token=SECRET|codex-secret|\r?\n/);
    assert.doesNotMatch(String(error?.code ?? ''), /Authorization_Bearer_SECRET|private-token=SECRET|\r?\n/);
    return true;
  };
}

function overrideCodexRun(harness, override) {
  const originalRun = harness.codexSession.run;
  harness.codexSession.run = async (command) => {
    const result = await originalRun(command);
    return override({ index: harness.calls.length - 1, command, result });
  };
}

const captureRequest = {
  schemaVersion: 1,
  kind: 'CAPTURE_REQUEST',
  metadataOnly: true,
  privateRepositoryRoot: '/private/guanyijia-evidence',
  privateRemote: 'git@codeup.aliyun.com:guanyijia/evidence-private.git',
  gitCommit: 'b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1',
  manifestPath: '/private/guanyijia-evidence/manifests/request.json',
  codeupAttestationDigest: `sha256:${'1'.repeat(64)}`,
  lfsAttestationDigest: `sha256:${'2'.repeat(64)}`,
  requestDigest: `sha256:${'3'.repeat(64)}`,
};

function requireGenerationModule() {
  assert.equal(generationImportError, undefined, 'guanyijia-generate.mjs must exist before the generation seam can be exercised');
  assert.equal(typeof generationModule?.createProposalGeneration, 'function');
  assert.equal(typeof generationModule?.validateProposalCitations, 'function');
  return generationModule;
}

function makeGenerationHarness(overrides = {}) {
  const events = [];
  const rawManifest = overrides.rawManifest ?? validRawManifest();
  const loginStatus = overrides.loginStatus ?? {
    status: 'LOGGED_IN',
    provider: 'CHATGPT',
    accountId: 'codex-session-1',
  };
  const env = Object.hasOwn(overrides, 'env') ? overrides.env : {};
  const module = requireGenerationModule();
  const dependencies = {
    auth: {
      loginStatus: async () => {
        events.push('login');
        if (overrides.authError) throw overrides.authError;
        return loginStatus;
      },
    },
    rawManifestReader: {
      read: async () => {
        events.push('raw-manifest');
        if (overrides.rawManifestError) throw overrides.rawManifestError;
        return rawManifest;
      },
    },
    artifactReader: {
      read: async () => {
        events.push('artifact');
        if (overrides.artifactError) throw overrides.artifactError;
        return artifactBytes;
      },
    },
    modelRunner: {
      generate: async () => {
        events.push('model');
        if (overrides.modelError) throw overrides.modelError;
        return validProposal();
      },
    },
  };
  if (!overrides.omitEnv) dependencies.env = env;
  const generator = module.createProposalGeneration(dependencies);
  assert.equal(typeof generator, 'function', 'createProposalGeneration must return the deep generation operation');
  return { generator, events };
}

function forgedProposalGenerationError(module, label, subclassed) {
  const ErrorClass = subclassed
    ? class ForgedProposalGenerationError extends module.ProposalGenerationError {}
    : module.ProposalGenerationError;
  const error = new ErrorClass('INTERNAL_GENERATION_FAILURE');
  error.code = `Authorization_Bearer_SECRET\nforged-${label}-code`;
  error.message = `forged-${label}-message\nprivate-token=SECRET`;
  return error;
}

function assertSafeGenerationError(expectedCode, injected) {
  return (error) => {
    assert.notEqual(error, injected, 'public failure must be a newly constructed domain error');
    assert.equal(error.name, 'ProposalGenerationError');
    assert.equal(error.code, expectedCode);
    assert.match(error.message, /Proposal generation blocked/);
    assert.doesNotMatch(error.message, /Authorization_Bearer_SECRET|forged-|private-token=SECRET/);
    assert.doesNotMatch(error.message, /\r?\n/);
    assert.doesNotMatch(String(error.code), /Authorization_Bearer_SECRET|forged-|\r?\n/);
    return true;
  };
}

test('proposal generation rejects API-key/provider credential or non-ChatGPT/ambiguous login before raw readers and model', async () => {
  const cases = [
    {
      name: 'API key environment',
      env: { OPENAI_API_KEY: 'sk-test-secret' },
      loginStatus: { status: 'LOGGED_IN', provider: 'CHATGPT', accountId: 'codex-session-1' },
    },
    {
      name: 'non-ChatGPT provider',
      loginStatus: { status: 'LOGGED_IN', provider: 'OPENAI_API', accountId: 'api-key-session' },
    },
    {
      name: 'ambiguous login',
      loginStatus: { status: 'AMBIGUOUS', providers: ['CHATGPT', 'OPENAI_API'] },
    },
  ];

  for (const invalid of cases) {
    const harness = makeGenerationHarness(invalid);
    await assert.rejects(
      () => harness.generator({ manifestRef: manifestId }),
      /auth|credential|provider|ChatGPT|Codex|API|ambiguous|login/i,
      invalid.name,
    );
    assert.equal(harness.events.includes('raw-manifest'), false, `${invalid.name} must fail before raw manifest reader`);
    assert.equal(harness.events.includes('artifact'), false, `${invalid.name} must fail before raw artifact reader`);
    assert.equal(harness.events.includes('model'), false, `${invalid.name} must fail before model runner`);
  }
});

for (const invalidEnvironment of [
  { name: 'missing environment snapshot', overrides: { omitEnv: true } },
  { name: 'null environment', overrides: { env: null } },
  { name: 'array environment', overrides: { env: [] } },
  { name: 'string environment', overrides: { env: 'not-an-environment-object' } },
  { name: 'empty API key environment', overrides: { env: { OPENAI_API_KEY: '' } } },
]) {
  test(`proposal generation rejects ${invalidEnvironment.name} before auth, readers, or model`, async () => {
    const harness = makeGenerationHarness(invalidEnvironment.overrides);

    await assert.rejects(
      () => harness.generator({ manifestRef: manifestId }),
      /environment|credential|API|provider|internal|invalid/i,
    );
    assert.equal(harness.events.includes('login'), false, `${invalidEnvironment.name} must fail before login`);
    assert.equal(harness.events.includes('raw-manifest'), false, `${invalidEnvironment.name} must fail before raw manifest reader`);
    assert.equal(harness.events.includes('artifact'), false, `${invalidEnvironment.name} must fail before raw artifact reader`);
    assert.equal(harness.events.includes('model'), false, `${invalidEnvironment.name} must fail before model runner`);
  });
}

test('proposal generation rejects CAPTURE_REQUEST because it is not a RawSourceSnapshotManifest', async () => {
  const harness = makeGenerationHarness({ rawManifest: captureRequest });

  await assert.rejects(
    () => harness.generator({ manifestRef: 'capture-request:20260820T000000Z' }),
    /RawSourceSnapshotManifest|CAPTURE_REQUEST|capture request|manifest/i,
  );
  assert.equal(harness.events.includes('artifact'), false);
  assert.equal(harness.events.includes('model'), false);
});

test('validateProposalCitations accepts an exact observed excerpt and locator from raw artifact bytes without model invocation', async () => {
  const module = requireGenerationModule();
  let artifactReads = 0;
  const validated = await module.validateProposalCitations({
    rawManifest: validRawManifest(),
    proposal: validProposal(),
    readArtifact: async ({ artifactRef: requestedArtifactRef }) => {
      artifactReads += 1;
      assert.equal(requestedArtifactRef, artifactRef);
      return artifactBytes;
    },
  });

  assert.deepEqual(validated, validProposal());
  assert.equal(artifactReads, 1);
});

test('validateProposalCitations rejects an observed citation whose extracted excerpt or locator does not exactly match', async () => {
  const module = requireGenerationModule();
  const manifest = validRawManifest();
  const cases = [
    {
      label: 'excerpt mismatch',
      proposal: {
        ...validProposal(),
        statements: [{
          ...validProposal().statements[0],
          citations: [{ ...validProposal().statements[0].citations[0], excerpt: 'CREATE TABLE forged (id BIGINT);' }],
        }],
      },
    },
    {
      label: 'locator mismatch',
      proposal: {
        ...validProposal(),
        statements: [{
          ...validProposal().statements[0],
          citations: [{
            ...validProposal().statements[0].citations[0],
            locator: { ...artifactLocator, startLine: 2 },
          }],
        }],
      },
    },
  ];

  for (const invalid of cases) {
    await assert.rejects(
      () => module.validateProposalCitations({
        rawManifest: manifest,
        proposal: invalid.proposal,
        readArtifact: async () => artifactBytes,
      }),
      /citation|excerpt|locator|artifact|raw|exact|match/i,
      invalid.label,
    );
  }
});

test('validateProposalCitations rejects a manifest self-digest mismatch before reading any artifact', async () => {
  const module = requireGenerationModule();
  const manifest = { ...validRawManifest(), manifestId: `${manifestId}:tampered` };
  let artifactReads = 0;

  await assert.rejects(
    () => module.validateProposalCitations({
      rawManifest: manifest,
      proposal: validProposal(),
      readArtifact: async () => {
        artifactReads += 1;
        return artifactBytes;
      },
    }),
    /manifest|digest|invalid/i,
  );
  assert.equal(artifactReads, 0);
});

test('validateProposalCitations rejects artifact bytes whose SHA-256 differs from the manifest', async () => {
  const module = requireGenerationModule();
  const forgedBytes = new TextEncoder().encode([
    'CREATE TABLE forged (',
    '  id BIGINT NOT NULL,',
    '  PRIMARY KEY (id)',
    ');',
    '',
  ].join('\n'));
  let artifactReads = 0;

  await assert.rejects(
    () => module.validateProposalCitations({
      rawManifest: validRawManifest(),
      proposal: validProposal(),
      readArtifact: async () => {
        artifactReads += 1;
        return forgedBytes;
      },
    }),
    /artifact|raw|sha|digest|invalid/i,
  );
  assert.equal(artifactReads, 1);
});

test('validateProposalCitations rejects invalid UTF-8 after matching the artifact digest', async () => {
  const module = requireGenerationModule();
  const invalidUtf8 = Uint8Array.from([0xc3, 0x28, 0x0a]);
  const fixture = manifestAndProposalForArtifact({
    bytes: invalidUtf8,
    locator: { ...artifactLocator, startLine: 1, endLine: 1 },
    excerpt: 'not-used-after-invalid-utf8',
  });
  let artifactReads = 0;

  await assert.rejects(
    () => module.validateProposalCitations({
      rawManifest: fixture.manifest,
      proposal: fixture.proposal,
      readArtifact: async () => {
        artifactReads += 1;
        return fixture.bytes;
      },
    }),
    /artifact|utf|raw|invalid/i,
  );
  assert.equal(artifactReads, 1);
});

test('validateProposalCitations rejects inclusive FILE_LINES ranges that are out of range or off by one', async () => {
  const module = requireGenerationModule();
  const bytes = new TextEncoder().encode([
    'CREATE TABLE orders (',
    '  id BIGINT NOT NULL,',
    '  PRIMARY KEY (id)',
    ');',
  ].join('\n'));
  const cases = [
    {
      label: 'end line beyond the available lines',
      locator: { ...artifactLocator, endLine: 5 },
      excerpt: exactExcerpt,
      expectedReads: 1,
    },
    {
      label: 'zero-based start line',
      locator: { ...artifactLocator, startLine: 0 },
      excerpt: 'CREATE TABLE orders (',
      expectedReads: 0,
    },
    {
      label: 'shifted excerpt for an otherwise in-range line interval',
      locator: { ...artifactLocator, startLine: 2, endLine: 4 },
      excerpt: 'CREATE TABLE orders (\n  id BIGINT NOT NULL,\n  PRIMARY KEY (id)',
      expectedReads: 1,
    },
  ];

  for (const invalid of cases) {
    const fixture = manifestAndProposalForArtifact({
      bytes,
      locator: invalid.locator,
      excerpt: invalid.excerpt,
    });
    let artifactReads = 0;
    await assert.rejects(
      () => module.validateProposalCitations({
        rawManifest: fixture.manifest,
        proposal: fixture.proposal,
        readArtifact: async () => {
          artifactReads += 1;
          return fixture.bytes;
        },
      }),
      /citation|locator|line|range|artifact|invalid/i,
      invalid.label,
    );
    assert.equal(artifactReads, invalid.expectedReads, invalid.label);
  }
});

test('validateProposalCitations preserves exact CR, whitespace, and Unicode bytes without normalization', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'carriage returns',
      bytes: new TextEncoder().encode('first\r\nsecond\r\n'),
      exact: 'first\r\nsecond\r',
      normalized: 'first\nsecond',
    },
    {
      label: 'trailing whitespace',
      bytes: new TextEncoder().encode('first  \nsecond\t\n'),
      exact: 'first  \nsecond\t',
      normalized: 'first\nsecond',
    },
    {
      label: 'Unicode normalization',
      bytes: new TextEncoder().encode('e\u0301\n'),
      exact: 'e\u0301',
      normalized: 'é',
      lineCount: 1,
    },
    {
      label: 'UTF-8 BOM',
      bytes: Uint8Array.from([0xef, 0xbb, 0xbf, ...new TextEncoder().encode('ASCII line\n')]),
      exact: '\uFEFFASCII line',
      normalized: 'ASCII line',
      lineCount: 1,
    },
  ];

  for (const candidate of cases) {
    const locator = { ...artifactLocator, startLine: 1, endLine: candidate.lineCount ?? 2 };
    const exactFixture = manifestAndProposalForArtifact({
      bytes: candidate.bytes,
      locator,
      excerpt: candidate.exact,
    });
    await assert.doesNotReject(
      () => module.validateProposalCitations({
        rawManifest: exactFixture.manifest,
        proposal: exactFixture.proposal,
        readArtifact: async () => exactFixture.bytes,
      }),
      candidate.label,
    );

    const normalizedFixture = manifestAndProposalForArtifact({
      bytes: candidate.bytes,
      locator,
      excerpt: candidate.normalized,
    });
    await assert.rejects(
      () => module.validateProposalCitations({
        rawManifest: normalizedFixture.manifest,
        proposal: normalizedFixture.proposal,
        readArtifact: async () => normalizedFixture.bytes,
      }),
      /citation|excerpt|exact|match|invalid/i,
      `${candidate.label} must not normalize`,
    );
  }
});

test('validateProposalCitations rejects duplicate observed citations instead of accepting the same evidence twice', async () => {
  const module = requireGenerationModule();
  const citation = validProposal().statements[0].citations[0];
  const proposal = validProposal();
  proposal.statements[0].citations = [citation, { ...citation }];
  let artifactReads = 0;

  await assert.rejects(
    () => module.validateProposalCitations({
      rawManifest: validRawManifest(),
      proposal,
      readArtifact: async () => {
        artifactReads += 1;
        return artifactBytes;
      },
    }),
    /duplicate|citation|evidence|invalid/i,
  );
  assert.ok(artifactReads <= 1, `duplicate evidence must not be resolved repeatedly; reads=${artifactReads}`);
});

for (const semanticKind of ['GAP', 'PENDING_ASSET']) {
  test(`validateProposalCitations accepts a pending generated ${semanticKind} target without reading raw artifacts`, async () => {
    const module = requireGenerationModule();
    const proposal = generatedTargetProposal({ semanticKind });
    let artifactReads = 0;
    await assert.doesNotReject(
      async () => {
        const validated = await module.validateProposalCitations({
          rawManifest: validRawManifest(),
          proposal,
          readArtifact: async () => {
            artifactReads += 1;
            return artifactBytes;
          },
        });
        assert.deepEqual(validated, proposal);
      },
      `${semanticKind} generated target should be accepted as pending`,
    );
    assert.equal(artifactReads, 0, `${semanticKind} generated target must not read raw artifacts`);
  });
}

test('validateProposalCitations rejects a repeated GENERATED_TARGET evidenceRef within one statement before any raw artifact read', async () => {
  const module = requireGenerationModule();
  const proposal = generatedTargetProposal();
  const generatedCitation = proposal.statements[0].citations[0];
  proposal.statements[0].citations = [generatedCitation, { ...generatedCitation }];
  let artifactReads = 0;

  await assert.rejects(
    () => module.validateProposalCitations({
      rawManifest: validRawManifest(),
      proposal,
      readArtifact: async () => {
        artifactReads += 1;
        return artifactBytes;
      },
    }),
    /duplicate|evidence|generated|citation|invalid/i,
  );
  assert.equal(artifactReads, 0);
});

test('validateProposalCitations rejects a GENERATED_TARGET evidenceRef repeated across two statements before any raw artifact read', async () => {
  const module = requireGenerationModule();
  const proposal = generatedTargetProposal();
  const firstStatement = proposal.statements[0];
  proposal.statements = [
    firstStatement,
    {
      ...firstStatement,
      statementId: 'proposal-statement:second-gap',
      text: '第二个待补充口径。',
      citations: [{ ...firstStatement.citations[0] }],
    },
  ];
  let artifactReads = 0;

  await assert.rejects(
    () => module.validateProposalCitations({
      rawManifest: validRawManifest(),
      proposal,
      readArtifact: async () => {
        artifactReads += 1;
        return artifactBytes;
      },
    }),
    /duplicate|evidence|generated|citation|invalid/i,
  );
  assert.equal(artifactReads, 0);
});

test('validateProposalCitations rejects generated targets without the exact pending GAP contract or when mixed with observed evidence', async () => {
  const module = requireGenerationModule();
  const cases = [
    { label: 'FACT target', semanticKind: 'GAP', evidenceStatus: 'FACT' },
    { label: 'non-GAP target', semanticKind: 'RULE', evidenceStatus: 'GAP' },
    { label: 'confirmed target', semanticKind: 'GAP', provenance: 'USER_CONFIRMED' },
    { label: 'wrong confirmation label', semanticKind: 'GAP', confirmationStatus: 'CONFIRMED' },
    { label: 'mixed observed and generated evidence', semanticKind: 'GAP', mixObserved: true },
    {
      label: 'generated target carrying raw identity',
      semanticKind: 'GAP',
      citationExtras: { sourceId, snapshotId, artifactRef },
    },
  ];

  for (const invalid of cases) {
    const proposal = generatedTargetProposal(invalid);
    let artifactReads = 0;
    await assert.rejects(
      () => module.validateProposalCitations({
        rawManifest: validRawManifest(),
        proposal,
        readArtifact: async () => {
          artifactReads += 1;
          return artifactBytes;
        },
      }),
      /citation|generated|target|GAP|pending|confirmation|invalid/i,
      invalid.label,
    );
    assert.equal(artifactReads, 0, `${invalid.label} must fail before raw artifact reads`);
  }
});

test('strict pure citation validation accepts the canonical three-source proposal with all four locator kinds', async () => {
  const module = requireGenerationModule();
  const fixture = strictProposalFixture();
  const sourceIds = fixture.proposal.sourceProposals.map((sourceProposal) => sourceProposal.sourceId);
  const locatorKinds = fixture.proposal.sourceProposals
    .flatMap((sourceProposal) => sourceProposal.statements)
    .flatMap((statement) => statement.evidence)
    .map((citation) => citation.locator.kind)
    .sort();

  assert.deepEqual(sourceIds, ['guanyijia_mysql', 'guanyijia_github', 'guanyijia_official_docs']);
  assert.deepEqual(locatorKinds, ['DOCUMENT_SECTION', 'FILE_LINES', 'SOURCE_SYMBOL', 'SQL']);
  assert.equal(fixture.proposal.sourceProposals.every((sourceProposal) => sourceProposal.statements.length >= 1), true);

  const validated = await validateStrictProposal(module, fixture);
  assert.deepEqual(validated, fixture.proposal);
});

test('module import and pure strict validation do not construct a controlled Codex executor', async () => {
  const module = requireGenerationModule();
  const fixture = strictProposalFixture();

  const validated = await validateStrictProposal(module, fixture);
  assert.deepEqual(validated, fixture.proposal);
});

test('controlled Codex executor requires one exact authorization token before touching the fake port', async () => {
  const module = requireGenerationModule();
  for (const invalidAuthorization of [
    undefined,
    false,
    controlledAuthorization(
      { fixture: { invocationEnvelope: { promptInputSha256: digest('test') } } },
      '--authorize-chatgpt-generation --authorize-chatgpt-generation',
    ),
    controlledAuthorization(
      { fixture: { invocationEnvelope: { promptInputSha256: digest('test') } } },
      '--authorize-codex',
    ),
  ]) {
    const harness = controlledExecutorFixture();
    const executor = module.createControlledCodexExecutor({
      env: controlledCodexEnvironment,
      executionAdmission: harness.executionAdmission,
      codexSession: harness.codexSession,
    });
    const authorization = invalidAuthorization === undefined || invalidAuthorization === false
      ? invalidAuthorization
      : { ...invalidAuthorization, expectedGenerationInputSha256: harness.fixture.invocationEnvelope.promptInputSha256 };
    await assert.rejects(
      () => executor.run(controlledExecutorRequest(harness, authorization)),
      (error) => error?.name === 'ProposalGenerationError' && error.code === 'AUTHORIZATION_REQUIRED',
      `authorization ${String(authorization)} must fail closed`,
    );
    assert.equal(harness.admissionCalls.length, 0, 'unauthorized admission must not touch execution admission');
    assert.equal(harness.calls.length, 0, 'unauthorized admission must not touch the Codex session');
  }
});

test('controlled executor rejects authorization digest mismatch, extra fields, and hostile request shapes before ports', async () => {
  const module = requireGenerationModule();
  const harness = controlledExecutorFixture();
  const validAuthorization = controlledAuthorization(harness);
  const cases = [
    {
      label: 'expected digest mismatch',
      request: controlledExecutorRequest(harness, {
        ...validAuthorization,
        expectedGenerationInputSha256: digest('wrong-generation-input'),
      }),
    },
    {
      label: 'authorization extra field',
      request: controlledExecutorRequest(harness, { ...validAuthorization, injected: 'SECRET' }),
    },
    {
      label: 'request extra field',
      request: { ...controlledExecutorRequest(harness), untrusted: 'SECRET' },
    },
    {
      label: 'hostile request own-key trap',
      request: new Proxy(controlledExecutorRequest(harness), {
        ownKeys(target) {
          return [...Reflect.ownKeys(target), 'privateToken'];
        },
        getOwnPropertyDescriptor(target, key) {
          if (key === 'privateToken') throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
          return Reflect.getOwnPropertyDescriptor(target, key);
        },
      }),
    },
  ];

  for (const invalid of cases) {
    const executor = createControlledExecutor(module, harness);
    await assert.rejects(
      () => executor.run(invalid.request),
      assertSafeExecutorError('AUTHORIZATION_REQUIRED'),
      invalid.label,
    );
    assert.equal(harness.admissionCalls.length, 0, invalid.label);
    assert.equal(harness.calls.length, 0, invalid.label);
  }
});

test('authorized controlled Codex executor emits the exact fixed policy through the injected fake port', async () => {
  const module = requireGenerationModule();
  const harness = controlledExecutorFixture();
  const executor = module.createControlledCodexExecutor({
    env: controlledCodexEnvironment,
    executionAdmission: harness.executionAdmission,
    codexSession: harness.codexSession,
  });
  assert.equal(typeof executor.run, 'function');
  assert.equal(harness.admissionCalls.length, 0, 'construction must not call execution admission');
  assert.equal(harness.calls.length, 0, 'construction must not start a child process');

  const result = await executor.run(controlledExecutorRequest(harness));
  assert.deepEqual(harness.admissionCalls, [harness.expectedAdmissionRequest]);
  assert.equal(harness.calls.length, 4);
  assert.deepEqual(harness.calls.map((call) => call.argv), [
    ['--version'],
    ['login', 'status'],
    expectedControlledCodexArgv(harness.executionAdmissionResult),
    ['login', 'status'],
  ]);

  const expectedLimits = {
    maxStdinBytes: 8 * 1024 * 1024,
    maxLastMessageBytes: 8 * 1024 * 1024,
    maxStdoutBytes: 2 * 1024 * 1024,
    maxStderrBytes: 64 * 1024,
    maxJsonlEvents: 10_000,
  };
  for (const [index, call] of harness.calls.entries()) {
    assert.equal(call.executablePath, controlledCodexBinaryPath, `call ${index} must use the admitted absolute binary`);
    assert.equal(call.cwd, controlledCodexWorkingDirectory, `call ${index} must use the admitted empty cwd`);
    assert.deepEqual({ ...call.env }, controlledCodexEnvironment, `call ${index} child env must be positive allowlist`);
    assert.equal(call.shell, false, `call ${index} must disable shell interpretation`);
    assert.deepEqual(call.stdio, ['pipe', 'pipe', 'pipe']);
    assert.deepEqual(call.limits, {
      ...expectedLimits,
      timeoutMs: index === 2 ? 600_000 : 10_000,
    });
    assert.equal(call.argv.includes(harness.promptText), false, `call ${index} must not put prompt text in argv`);
    assert.equal(call.argv.includes(controlledCodexWorkingDirectory), index === 2);
  }
  assert.deepEqual(harness.calls[0].stdin, new Uint8Array());
  assert.deepEqual(harness.calls[1].stdin, new Uint8Array());
  assert.deepEqual(harness.calls[3].stdin, new Uint8Array());
  assert.equal(harness.calls[2].stdin instanceof Uint8Array, true);
  assert.equal(harness.calls[2].stdin.byteLength > 0, true);
  assert.equal(harness.calls[2].argv.at(-1), '-');
  assert.deepEqual(result.proposal, harness.fixture.proposal);
  assert.equal(result.execution.attemptCount, 1);
  assert.equal(result.execution.preAuthMode, 'CHATGPT_SESSION');
  assert.equal(result.execution.postAuthMode, 'CHATGPT_SESSION');
  assert.equal(result.execution.completionEventCount, 1);
  assert.equal(result.execution.toolCallCount, 0);
  assert.equal(result.execution.stdinSha256, digest(harness.calls[2].stdin));
  assert.equal(result.proposalSha256, digest(`${stableJson(harness.fixture.proposal)}\n`));
  const admittedProposal = structuredClone(harness.fixture.proposal);
  result.proposal.sourceProposals[0].statements[0].label = 'mutated-after-return';
  assert.deepEqual(harness.fixture.proposal, admittedProposal, 'returned proposal must be an isolated clone');
});

test('controlled executor sends exactly the recursively canonical GUANYIJIA_CODEX_STDIN bytes', async () => {
  const module = requireGenerationModule();
  const harness = controlledExecutorFixture();
  const executor = createControlledExecutor(module, harness);
  const result = await executor.run(controlledExecutorRequest(harness));
  const promptInputPacket = strictPromptInputPacket({
    storyKey: harness.fixture.proposal.storyKey,
    runId: harness.fixture.proposal.runId,
    admittedSources: harness.fixture.admittedSources,
    resolvedCitations: harness.fixture.resolvedCitations,
    promptTemplateSha256: harness.fixture.invocationEnvelope.promptTemplateSha256,
    proposalSchemaSha256: harness.fixture.invocationEnvelope.proposalSchemaSha256,
    citationMaterialSha256: harness.fixture.invocationEnvelope.citationMaterialSha256,
  });
  const expectedStdin = new TextEncoder().encode(`${stableJson({
    schemaVersion: 1,
    kind: 'GUANYIJIA_CODEX_STDIN',
    instructions: harness.promptText,
    invocationEnvelopeSha256: harness.fixture.invocationEnvelope.envelopeSha256,
    untrustedEvidenceData: promptInputPacket,
  })}\n`);

  assert.deepEqual(harness.calls[2].stdin, expectedStdin);
  assert.equal(new TextDecoder('utf-8', { fatal: true }).decode(harness.calls[2].stdin), `${stableJson({
    schemaVersion: 1,
    kind: 'GUANYIJIA_CODEX_STDIN',
    instructions: harness.promptText,
    invocationEnvelopeSha256: harness.fixture.invocationEnvelope.envelopeSha256,
    untrustedEvidenceData: promptInputPacket,
  })}\n`);
  assert.equal(result.execution.stdinSha256, digest(expectedStdin));
});

function scriptKindForPath(label) {
  if (/\.tsx$/u.test(label)) return ts.ScriptKind.TSX;
  if (/\.jsx$/u.test(label)) return ts.ScriptKind.JSX;
  if (/\.(?:ts)$/u.test(label)) return ts.ScriptKind.TS;
  return ts.ScriptKind.JS;
}

function expressionPath(node) {
  if (ts.isIdentifier(node)) return node.text;
  if (ts.isMetaProperty(node)) return `${node.keywordToken === ts.SyntaxKind.ImportKeyword ? 'import' : 'new'}.${node.name.text}`;
  if (ts.isPropertyAccessExpression(node)) {
    const parent = expressionPath(node.expression);
    return parent ? `${parent}.${node.name.text}` : node.name.text;
  }
  return undefined;
}

function rawNodeText(sourceFile, node) {
  return sourceFile.text.slice(node.getStart(sourceFile), node.getEnd());
}

function analyzeSource(source, label = 'source') {
  const sourceFile = ts.createSourceFile(
    label,
    source,
    ts.ScriptTarget.Latest,
    true,
    scriptKindForPath(label),
  );
  const staticSpecifiers = [];
  const moduleSpecifiersList = [];
  const dynamicModuleCalls = [];
  const calls = [];
  const stringLiterals = [];
  const processCallNames = new Set(['spawn', 'execFile', 'fork']);
  let escapedModuleSpecifier = false;
  let nonLiteralModuleCall = false;
  let directMain = false;
  let hasExecutorConstruction = false;
  let genericRequireReference = false;
  let moduleRequireAccess = false;
  let moduleElementAccess = false;
  let createRequireReference = false;
  let processBuiltinModuleAccess = false;
  let hasExactChildProcessImport = true;
  let childProcessImportCount = 0;
  const addModule = (node, isStatic) => {
    if (!ts.isStringLiteral(node)) {
      nonLiteralModuleCall = true;
      return;
    }
    const raw = rawNodeText(sourceFile, node);
    if (raw.slice(1, -1).includes('\\')) {
      escapedModuleSpecifier = true;
      return;
    }
    moduleSpecifiersList.push(node.text);
    if (isStatic) staticSpecifiers.push(node.text);
  };
  const visit = (node) => {
    if (ts.isIdentifier(node)) {
      if (node.text === 'require') genericRequireReference = true;
      if (node.text === 'createRequire') createRequireReference = true;
    }
    if (ts.isFunctionDeclaration(node) && node.name?.text === 'createControlledCodexExecutor') {
      hasExecutorConstruction = true;
    }
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name)
      && node.name.text === 'createControlledCodexExecutor') {
      hasExecutorConstruction = true;
    }
    if (ts.isStringLiteral(node)) stringLiterals.push(node.text);
    if (ts.isImportDeclaration(node)) {
      addModule(node.moduleSpecifier, true);
      if (node.moduleSpecifier.text === 'node:child_process') {
        childProcessImportCount += 1;
        const clause = node.importClause;
        const named = clause?.namedBindings;
        const exact = Boolean(clause && !clause.name
          && ts.isNamedImports(named)
          && named.elements.length === 1
          && named.elements[0].name.text === 'spawn'
          && !named.elements[0].propertyName);
        if (!exact) hasExactChildProcessImport = false;
        if (ts.isNamedImports(named)) {
          for (const element of named.elements) {
            const importedName = element.propertyName?.text ?? element.name.text;
            if (['spawn', 'execFile', 'fork'].includes(importedName)) {
              processCallNames.add(element.name.text);
            }
          }
        }
      }
    } else if (ts.isExportDeclaration(node) && node.moduleSpecifier) {
      addModule(node.moduleSpecifier, true);
    } else if (ts.isImportEqualsDeclaration(node)) {
      const reference = node.moduleReference;
      if (ts.isExternalModuleReference(reference) && reference.expression) {
        addModule(reference.expression, true);
        if (ts.isStringLiteral(reference.expression) && reference.expression.text === 'node:child_process') {
          hasExactChildProcessImport = false;
        }
      }
    }
    if (ts.isCallExpression(node)) {
      const path = expressionPath(node.expression);
      const dynamicImport = node.expression.kind === ts.SyntaxKind.ImportKeyword;
      const requireCall = path === 'require';
      if (dynamicImport || requireCall) {
        const call = { node, kind: dynamicImport ? 'import' : 'require', specifier: undefined };
        dynamicModuleCalls.push(call);
        if (node.arguments.length !== 1 || !ts.isStringLiteral(node.arguments[0])) {
          nonLiteralModuleCall = true;
        } else {
          const argument = node.arguments[0];
          const raw = rawNodeText(sourceFile, argument);
          if (raw.slice(1, -1).includes('\\')) escapedModuleSpecifier = true;
          else {
            call.specifier = argument.text;
            moduleSpecifiersList.push(argument.text);
          }
        }
        if (requireCall && node.arguments.length === 1 && ts.isStringLiteral(node.arguments[0])
          && node.arguments[0].text === 'node:child_process') {
          hasExactChildProcessImport = false;
        }
      }
      calls.push(node);
      if (path === 'fetch') directMain = directMain;
      if (path === 'process.kill' || path === 'process.exit') directMain = directMain;
    }
    if (ts.isPropertyAccessExpression(node)) {
      const path = expressionPath(node);
      if (path === 'process.argv' || path === 'require.main') directMain = true;
      if (path === 'module.require') moduleRequireAccess = true;
      if (path === 'process.getBuiltinModule') processBuiltinModuleAccess = true;
    }
    if (ts.isElementAccessExpression(node)) {
      if (expressionPath(node.expression) === 'module') moduleElementAccess = true;
    }
    if (ts.isBinaryExpression(node)) {
      const operators = new Set([
        ts.SyntaxKind.EqualsEqualsToken,
        ts.SyntaxKind.EqualsEqualsEqualsToken,
        ts.SyntaxKind.ExclamationEqualsToken,
        ts.SyntaxKind.ExclamationEqualsEqualsToken,
      ]);
      if (operators.has(node.operatorToken.kind)
        && [expressionPath(node.left), expressionPath(node.right)].includes('import.meta.url')) directMain = true;
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return {
    sourceFile,
    parseFailed: !/\.json$/u.test(label) && sourceFile.parseDiagnostics.length > 0,
    staticSpecifiers: [...new Set(staticSpecifiers)],
    moduleSpecifiers: [...new Set(moduleSpecifiersList)],
    dynamicModuleCalls,
    calls,
    stringLiterals,
    escapedModuleSpecifier,
    nonLiteralModuleCall,
    directMain,
    hasExecutorConstruction,
    genericRequireReference,
    moduleRequireAccess,
    moduleElementAccess,
    createRequireReference,
    processBuiltinModuleAccess,
    hasExactChildProcessImport: hasExactChildProcessImport && childProcessImportCount <= 1,
    processCallNames,
  };
}

function staticImportSpecifiers(source) {
  return analyzeSource(source).staticSpecifiers;
}

function moduleSpecifiers(source) {
  return analyzeSource(source).moduleSpecifiers;
}

function hasEscapedModuleSpecifier(source) {
  return analyzeSource(source).escapedModuleSpecifier;
}

function hasNonLiteralDynamicImport(source) {
  return analyzeSource(source).dynamicModuleCalls
    .some((call) => call.kind === 'import' && call.specifier === undefined);
}

function hasNonLiteralDynamicModuleCall(source, names) {
  return analyzeSource(source).dynamicModuleCalls
    .some((call) => names.includes(call.kind) && call.specifier === undefined);
}

function normalizeRepoPath(value) {
  const parts = [];
  for (const part of value.replaceAll('\\', '/').split('/')) {
    if (!part || part === '.') continue;
    if (part === '..') {
      parts.pop();
      continue;
    }
    parts.push(part);
  }
  return parts.join('/');
}

function repoDirname(value) {
  const normalized = normalizeRepoPath(value);
  const slash = normalized.lastIndexOf('/');
  return slash < 0 ? '' : normalized.slice(0, slash);
}

function localModuleCandidates(fromPath, specifier) {
  if (!specifier.startsWith('.')) return [];
  const base = normalizeRepoPath(`${repoDirname(fromPath)}/${specifier}`);
  const hasExtension = /\.(?:mjs|cjs|js|ts|tsx|json)$/.test(base);
  const candidates = hasExtension
    ? [base]
    : [base, `${base}.mjs`, `${base}.cjs`, `${base}.js`, `${base}.ts`, `${base}.tsx`, `${base}.json`];
  if (!hasExtension) candidates.push(
    `${base}/index.mjs`, `${base}/index.cjs`, `${base}/index.js`, `${base}/index.ts`, `${base}/index.tsx`,
  );
  return [...new Set(candidates)];
}

function scriptPathReferences(command) {
  const references = [];
  const pattern = /(?:^|[\s"'`])((?:\.\/)?(?:scripts|src|tests)\/[A-Za-z0-9._/-]+\.(?:mjs|cjs|js|ts|tsx)|(?:\.\/)?(?:vite|playwright)\.config\.[A-Za-z0-9]+)/g;
  for (const match of command.matchAll(pattern)) references.push(normalizeRepoPath(match[1]));
  return [...new Set(references)];
}

function packageScriptClosure(scripts, seeds) {
  const queue = [...seeds];
  const visited = new Set();
  while (queue.length > 0) {
    const name = queue.shift();
    if (visited.has(name) || !Object.hasOwn(scripts, name)) continue;
    visited.add(name);
    for (const hook of [`pre${name}`, `post${name}`]) {
      if (Object.hasOwn(scripts, hook)) queue.push(hook);
    }
    for (const match of scripts[name].matchAll(/\bnpm\s+(?:run|run-script)\s+([A-Za-z0-9:_-]+)/g)) {
      queue.push(match[1]);
    }
  }
  return [...visited];
}

function ordinaryPackageScriptNames(scripts) {
  return Object.keys(scripts)
    .filter((name) => /^(?:dev|build|preview|test(?::|$)|e2e(?::|$)|demo(?::|$))/.test(name));
}

function lifecycleScriptNames(scripts) {
  const queue = Object.keys(scripts).filter((name) => /^(?:pre|post)[A-Za-z]/.test(name));
  const visited = new Set();
  while (queue.length > 0) {
    const name = queue.shift();
    if (visited.has(name) || !Object.hasOwn(scripts, name)) continue;
    visited.add(name);
    for (const match of scripts[name].matchAll(/\bnpm\s+(?:run|run-script)\s+([A-Za-z0-9:_-]+)/g)) {
      const nested = match[1];
      queue.push(nested, `pre${nested}`, `post${nested}`);
    }
  }
  return [...visited];
}

function nestedScriptNamesFromOrdinaryRoots(scripts) {
  const queue = [...ordinaryPackageScriptNames(scripts)];
  const visited = new Set();
  const nested = new Set();
  while (queue.length > 0) {
    const name = queue.shift();
    if (visited.has(name) || !Object.hasOwn(scripts, name)) continue;
    visited.add(name);
    for (const match of scripts[name].matchAll(/\bnpm\s+(?:run|run-script)\s+([A-Za-z0-9:_-]+)/g)) {
      const target = match[1];
      if (target === name) continue;
      nested.add(target);
      queue.push(target, `pre${target}`, `post${target}`);
    }
  }
  return [...nested];
}

function strictGraphEntries(scripts, configEntries) {
  const strictScripts = new Set([
    ...ordinaryPackageScriptNames(scripts),
    ...lifecycleScriptNames(scripts),
    ...nestedScriptNamesFromOrdinaryRoots(scripts),
  ]);
  return [...new Set([
    ...configEntries.map(normalizeRepoPath),
    ...[...strictScripts].flatMap((name) => scriptPathReferences(scripts[name])),
  ])];
}

function strictOperationalGraphEntries(scripts, configEntries) {
  const operationalRoots = new Set(configEntries.map(normalizeRepoPath));
  for (const name of lifecycleScriptNames(scripts)) {
    for (const reference of scriptPathReferences(scripts[name])) operationalRoots.add(reference);
  }
  return [...operationalRoots];
}

const exactOperationalCapabilities = new Map([
  ['scripts/run-cp8-playwright.mjs', {
    imports: new Set(['node:child_process', 'node:fs/promises', 'node:net']),
    calls: new Set(['spawn', 'rm', 'createConnection']),
    allowProcessEntry: true,
    processPolicy: 'cp8-runner',
    importPolicy: 'cp8-child-process',
    sourceSha256: 'sha256:4b7e4ecb6ee390827e19cab3fb9b34433d9ce1abfe60a5b93c189cee79e2aeea',
  }],
  ['scripts/run-cp8-playwright.test.mjs', {
    imports: new Set(['node:child_process', 'node:fs', 'node:fs/promises']),
    calls: new Set(['spawn', 'existsSync', 'mkdir', 'writeFile']),
    allowProcessEntry: true,
    processPolicy: 'cp8-test',
    importPolicy: 'cp8-child-process',
    sourceSha256: 'sha256:185a30acbdeb11090ff90b7441392761fceafbe6888f2858e5b17b08aec93ddf',
  }],
  ['tests/e2e/standardization-capacity-errors.spec.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['mkdirSync', 'writeFileSync']),
  }],
  ['tests/e2e/standardization-responsive.spec.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['mkdirSync']),
  }],
  ['tests/e2e/guanyijia-five-source-story.spec.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['mkdirSync']),
  }],
  ['tests/e2e/standardization-protected.spec.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['mkdirSync']),
  }],
  ['tests/e2e/standardization-zoom.spec.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['mkdirSync']),
  }],
  ['src/features/semantic-evidence/semantic-evidence.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['readFileSync']),
  }],
  ['src/features/source-management/source-management.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['readFileSync']),
  }],
  ['src/features/modeling-document-bridge/modeling-document.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['readFileSync']),
  }],
  ['src/features/standardization-deliverable/standardization-deliverable.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['readFileSync']),
  }],
  ['src/features/data-standardization/data-standardization.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['readFileSync']),
  }],
  ['src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['readFileSync']),
  }],
  ['src/features/collaboration/collaboration.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['readFileSync']),
  }],
  ['src/features/collaboration/catalog-browser.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['readFileSync']),
  }],
  ['src/features/ai-modeling/ai-modeling.test.ts', {
    imports: new Set(['node:fs']),
    calls: new Set(['existsSync', 'readFileSync']),
  }],
]);

function operationalCapabilitiesFor(modulePath, strictOperational) {
  if (strictOperational) return undefined;
  return exactOperationalCapabilities.get(normalizeRepoPath(modulePath));
}

function capabilityAllowsImport(capabilities, specifier) {
  if (!capabilities) return false;
  for (const allowed of capabilities.imports) {
    if (specifier === allowed || specifier.startsWith(`${allowed}/`)) return true;
  }
  return false;
}

function hasExactAllowedChildProcessImport(analysis, capabilities) {
  if (capabilities?.importPolicy !== 'cp8-child-process') return true;
  return analysis.hasExactChildProcessImport;
}

function allowsSafeProcessInvocation(capabilities, call, argumentsList, analysis) {
  if (!capabilities?.calls?.has(call)) return false;
  const firstArg = argumentsList[0] ? rawNodeText(analysis.sourceFile, argumentsList[0]).trim() : undefined;
  const secondArg = argumentsList[1] ? rawNodeText(analysis.sourceFile, argumentsList[1]).trim() : undefined;
  const source = analysis.sourceFile.text;
  if (capabilities.processPolicy === 'cp8-runner') {
    return call === 'spawn'
      && firstArg === 'command'
      && secondArg === 'args'
      && /\bfunction\s+run\s*\(\s*command\s*,\s*args\s*\)/.test(source)
      && /\bconst\s+npmCommand\s*=\s*process\.platform/.test(source)
      && /\bconst\s+npxCommand\s*=\s*process\.platform/.test(source)
      && /\bfunction\s+selfTestCommand\s*\(\s*\)/.test(source);
  }
  if (capabilities.processPolicy === 'cp8-test') {
    return call === 'spawn'
      && firstArg === 'process.execPath'
      && secondArg === '[runner]'
      && /\bconst\s+runner\s*=\s*resolve\(\s*['"]scripts\/run-cp8-playwright\.mjs['"]\s*\)/.test(source);
  }
  return false;
}

function auditGeneratorSource(source, label = 'generator', {
  allowOperationalRuntime = false,
  allowBuiltinImports = false,
  allowSafeLocalFsRead = false,
  capabilities = undefined,
  trustedOperationalSource = false,
  ordinaryReachabilityOnly = false,
  analysis = undefined,
} = {}) {
  const findings = [];
  const parsed = analysis ?? analyzeSource(source, label);
  if (parsed.parseFailed) findings.push(`${label}:parse-failure`);
  if (parsed.escapedModuleSpecifier) findings.push(`${label}:escaped-module-specifier`);
  if (!ordinaryReachabilityOnly && parsed.nonLiteralModuleCall) {
    findings.push(`${label}:nonliteral-module-specifier`);
  }
  if (!ordinaryReachabilityOnly && !trustedOperationalSource && parsed.genericRequireReference) {
    findings.push(`${label}:forbidden-require-reference`);
  }
  if (!ordinaryReachabilityOnly && !trustedOperationalSource && parsed.moduleRequireAccess) {
    findings.push(`${label}:forbidden-module-require`);
  }
  if (!ordinaryReachabilityOnly && !trustedOperationalSource && parsed.moduleElementAccess) {
    findings.push(`${label}:forbidden-module-element-access`);
  }
  if (!ordinaryReachabilityOnly && !trustedOperationalSource && parsed.createRequireReference) {
    findings.push(`${label}:forbidden-createRequire`);
  }
  if (!ordinaryReachabilityOnly && !trustedOperationalSource && parsed.processBuiltinModuleAccess) {
    findings.push(`${label}:forbidden-process-getBuiltinModule`);
  }
  const imports = parsed.moduleSpecifiers;
  const exactChildProcessImport = hasExactAllowedChildProcessImport(parsed, capabilities);
  for (const specifier of imports) {
    const isBuiltin = /^(?:node:)?(?:child_process|fs|http|https|net|dns|tls|worker_threads)(?:\/|$)/.test(specifier);
    if (!ordinaryReachabilityOnly && isBuiltin && !allowBuiltinImports
      && (!capabilityAllowsImport(capabilities, specifier)
        || (specifier === 'node:child_process' && !exactChildProcessImport))) {
      findings.push(`${label}:forbidden-node-import:${specifier}`);
    }
    const externalRuntimePackage = !specifier.startsWith('.')
      && !specifier.startsWith('/')
      && !specifier.startsWith('node:')
      && /^(?:@?openai|@?anthropic|axios|undici|ws|.*(?:sdk|model-runner|raw-reader|writer)(?:\/|$))/i.test(specifier);
    const localRuntimeAdapter = specifier.startsWith('.')
      && /(?:^|\/)(?:model-runner|model-adapter|raw-reader|writer)(?:[-_.\/]|$)/i.test(specifier);
    if (externalRuntimePackage || localRuntimeAdapter) {
      findings.push(`${label}:forbidden-runtime-import:${specifier}`);
    }
  }
  if (!ordinaryReachabilityOnly
    && !allowOperationalRuntime && !capabilities?.allowProcessEntry && parsed.directMain) {
    findings.push(`${label}:direct-main`);
  }
  const dangerousCalls = new Set([
    'spawn', 'execFile', 'fork', 'writeFile', 'appendFile', 'createWriteStream',
    'readFile', 'readFileSync', 'writeFileSync', 'mkdir', 'mkdirSync', 'rm', 'rmSync',
    'createConnection', 'existsSync',
  ]);
  for (const processCallName of parsed.processCallNames) dangerousCalls.add(processCallName);
  for (const callNode of parsed.calls) {
    const fullPath = expressionPath(callNode.expression);
    const call = fullPath?.split('.').at(-1);
    if (['spawn', 'execFile', 'fork'].includes(call)
      && /\b(?:codex|openai|anthropic)\b/i.test(rawNodeText(parsed.sourceFile, callNode))) {
      findings.push(`${label}:forbidden-codex-exec`);
    }
    if (ordinaryReachabilityOnly) continue;
    if (fullPath === 'globalThis.fetch') findings.push(`${label}:globalThis.fetch`);
    if (fullPath === 'fetch' || fullPath === 'globalThis.fetch') findings.push(`${label}:direct-fetch`);
    if (fullPath === 'process.kill' || fullPath === 'process.exit') {
      if (!allowOperationalRuntime && !capabilities?.allowProcessEntry) {
        findings.push(`${label}:direct-live-adapter`);
      }
    }
    if (!dangerousCalls.has(call)) continue;
    const allowed = capabilities?.calls?.has(call) === true;
    if (!allowed && !(allowSafeLocalFsRead && /^readFile(?:Sync)?$/.test(call))) {
      findings.push(`${label}:direct-live-adapter`);
    }
    if (parsed.processCallNames.has(call)
      && !allowsSafeProcessInvocation(capabilities, call, callNode.arguments, parsed)) {
      findings.push(`${label}:direct-live-adapter`);
    }
  }
  if (parsed.stringLiterals.some((value) => /(?:https?|wss?):\/\/[^\s]*(?:openai|anthropic|api\.cohere|generativelanguage\.googleapis)\b/i.test(value))) {
    findings.push(`${label}:model-endpoint`);
  }
  return findings;
}

function auditPackageScriptGraph(
  scripts,
  moduleSources,
  extraEntries = [],
  strictEntries = extraEntries,
  missingRefs = [],
  strictOperationalEntries = undefined,
) {
  const scriptNames = packageScriptClosure(scripts, ordinaryPackageScriptNames(scripts));
  const entries = new Set(extraEntries.map(normalizeRepoPath));
  const strictRoots = new Set(strictEntries.map(normalizeRepoPath));
  const fullStrictRoots = new Set(extraEntries.map(normalizeRepoPath));
  const strictOperationalRoots = new Set(
    (strictOperationalEntries ?? strictOperationalGraphEntries(scripts, extraEntries))
      .map(normalizeRepoPath),
  );
  for (const name of scriptNames) {
    for (const reference of scriptPathReferences(scripts[name])) entries.add(reference);
  }

  const sourceMap = new Map(Object.entries(moduleSources).map(([path, source]) => [normalizeRepoPath(path), source]));
  const violations = [];
  const violationSet = new Set();
  const addViolation = (finding) => {
    if (violationSet.has(finding)) return;
    violationSet.add(finding);
    violations.push(finding);
  };
  for (const missing of missingRefs) {
    addViolation(`missing-local-reference:${missing.from ?? '<package>'}:${missing.specifier}`);
  }

  for (const name of scriptNames) {
    for (const reference of scriptPathReferences(scripts[name])) {
      if (!localModuleCandidates('', `./${reference}`).some((candidate) => sourceMap.has(candidate))) {
        addViolation(`missing-script-source:${name}:${reference}`);
      }
    }
  }
  for (const entry of entries) {
    if (!sourceMap.has(entry)) addViolation(`missing-entry-source:${entry}`);
  }

  const queue = [...entries].map((modulePath) => ({
    modulePath,
    strict: strictRoots.has(modulePath),
    strictOperational: strictOperationalRoots.has(modulePath),
    fullStrict: fullStrictRoots.has(modulePath),
  }));
  const visited = new Map();
  while (queue.length > 0) {
    const queued = queue.shift();
    const modulePath = normalizeRepoPath(queued.modulePath);
    const previous = visited.get(modulePath)
      ?? { strict: false, strictOperational: false, fullStrict: false };
    if ((previous.strict || !queued.strict)
      && (previous.strictOperational || !queued.strictOperational)
      && (previous.fullStrict || !queued.fullStrict)) continue;
    const state = {
      strict: previous.strict || queued.strict,
      strictOperational: previous.strictOperational || queued.strictOperational,
      fullStrict: previous.fullStrict || queued.fullStrict,
    };
    visited.set(modulePath, state);
    const source = sourceMap.get(modulePath);
    if (source === undefined) continue;
    const analysis = analyzeSource(source, modulePath);
    if (/(?:^|\/)guanyijia-generate\.mjs$/.test(modulePath)) {
      addViolation(`generator-reachable:${modulePath}`);
    }
    if (analysis.hasExecutorConstruction
      || analysis.calls.some((call) => expressionPath(call.expression)?.split('.').at(-1) === 'createControlledCodexExecutor')) {
      addViolation(`executor-construction:${modulePath}`);
    }
    if (queued.strict) {
      const capabilities = operationalCapabilitiesFor(modulePath, queued.strictOperational);
      const trustedOperationalSource = Boolean(
        capabilities?.sourceSha256 && digest(source) === capabilities.sourceSha256,
      );
      const isGeneratorSource = /(?:^|\/)guanyijia-generate\.mjs$/.test(modulePath);
      const ordinaryReachabilityOnly = !queued.fullStrict && !queued.strictOperational && !isGeneratorSource
        && !capabilities?.processPolicy;
      if (analysis.parseFailed) {
        addViolation(`parse-failure:${modulePath}`);
      }
      if (analysis.escapedModuleSpecifier) {
        addViolation(`escaped-module-specifier:${modulePath}`);
      }
      if (capabilities?.sourceSha256 && digest(source) !== capabilities.sourceSha256) {
        addViolation(`source-pin-mismatch:${modulePath}`);
      }
      for (const finding of auditGeneratorSource(source, modulePath, {
        allowOperationalRuntime: capabilities?.allowProcessEntry === true,
        allowBuiltinImports: false,
        capabilities,
        trustedOperationalSource,
        ordinaryReachabilityOnly,
        analysis,
      })) addViolation(finding);
      const hasDynamicImport = analysis.dynamicModuleCalls.length > 0;
      const hasNonLiteralImport = analysis.dynamicModuleCalls
        .some((call) => call.kind === 'import' && call.specifier === undefined);
      const hasNonLiteralRequire = analysis.dynamicModuleCalls
        .some((call) => call.kind === 'require' && call.specifier === undefined);
      const hasDynamicRuntimeImport = analysis.dynamicModuleCalls
        .some((call) => call.specifier !== undefined
          && /^(?:@?openai|@?anthropic|axios|undici|ws|.*(?:sdk|model-runner|raw-reader|writer)(?:\/|$))/i.test(call.specifier));
      const hasKnownRuntimeLiteral = /\b(?:openai|anthropic|axios|undici|ws)\b/i.test(source);
      if ((queued.strictOperational && hasDynamicImport)
        || hasDynamicRuntimeImport || hasNonLiteralImport || hasNonLiteralRequire) {
        if (!ordinaryReachabilityOnly || hasKnownRuntimeLiteral) {
          addViolation(`dynamic-or-require:${modulePath}`);
        }
      }
    }
    for (const specifier of analysis.moduleSpecifiers) {
      if (!specifier.startsWith('.')) continue;
      const candidates = localModuleCandidates(modulePath, specifier);
      const matchingCandidate = candidates.find((candidate) => sourceMap.has(candidate));
      if (!matchingCandidate) {
        addViolation(`missing-local-import:${modulePath}:${specifier}`);
        continue;
      }
      queue.push({
        modulePath: matchingCandidate,
        strict: queued.strict,
        strictOperational: queued.strictOperational,
        fullStrict: queued.fullStrict,
      });
    }
  }
  return { scriptNames, modulePaths: [...visited.keys()], violations };
}

async function loadRepositoryModuleGraph({ scripts, extraEntries = [], virtualSources = {} }) {
  const scriptNames = packageScriptClosure(scripts, ordinaryPackageScriptNames(scripts));
  const entries = new Set(extraEntries.map(normalizeRepoPath));
  for (const name of scriptNames) {
    for (const reference of scriptPathReferences(scripts[name])) entries.add(reference);
  }
  const virtualSourceMap = new Map(Object.entries(virtualSources)
    .map(([path, source]) => [normalizeRepoPath(path), source]));
  const queue = [...entries].map((entry) => ({
    from: '<package>',
    specifier: `./${entry}`,
  }));
  const sources = {};
  const missing = [];
  const attempted = new Set();
  while (queue.length > 0) {
    const request = queue.shift();
    const requestKey = `${request.from}\u0000${request.specifier}`;
    if (attempted.has(requestKey)) continue;
    attempted.add(requestKey);
    let foundPath;
    let source;
    const candidates = localModuleCandidates(request.from === '<package>' ? '' : request.from, request.specifier);
    for (const candidate of candidates) {
      if (virtualSourceMap.has(candidate)) {
        source = virtualSourceMap.get(candidate);
        foundPath = candidate;
        break;
      }
      try {
        source = await readFile(new URL(`../../${candidate}`, import.meta.url), 'utf8');
        foundPath = candidate;
        break;
      } catch {
        // Try the next extension; a fully missing local reference is recorded below.
      }
    }
    if (!foundPath || source === undefined) {
      missing.push({ from: request.from, specifier: request.specifier });
      continue;
    }
    if (Object.hasOwn(sources, foundPath)) continue;
    sources[foundPath] = source;
    for (const specifier of moduleSpecifiers(source)) {
      if (specifier.startsWith('.')) queue.push({ from: foundPath, specifier });
    }
  }
  return { sources, missing, scriptNames };
}

test('generator import graph and ordinary package scripts remain no-live and never construct a Codex port', async () => {
  const generatorSource = await readFile(new URL('./guanyijia-generate.mjs', import.meta.url), 'utf8');
  assert.deepEqual(staticImportSpecifiers(generatorSource), ['node:crypto']);
  assert.deepEqual(auditGeneratorSource(generatorSource), []);

  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const ordinaryScriptNames = ordinaryPackageScriptNames(packageManifest.scripts);
  const ordinaryScripts = ordinaryScriptNames.map((name) => [name, packageManifest.scripts[name]]);
  assert.ok(ordinaryScripts.length > 0);
  for (const [name, command] of ordinaryScripts) {
    assert.doesNotMatch(command, /guanyijia-generate|createControlledCodexExecutor|scripts\/evidence\/guanyijia-generate\.mjs/,
      `${name} must not enter generation or construct a controlled port`);
  }

  const graph = await loadRepositoryModuleGraph({
    scripts: packageManifest.scripts,
    extraEntries: [
      'vite.config.ts',
      'playwright.config.ts',
      'tests/e2e/vite.cp8.config.ts',
    ],
  });
  const configEntries = [
    'vite.config.ts',
    'playwright.config.ts',
    'tests/e2e/vite.cp8.config.ts',
  ];
  const audit = auditPackageScriptGraph(
    packageManifest.scripts,
    graph.sources,
    configEntries,
    strictGraphEntries(packageManifest.scripts, configEntries),
    graph.missing,
  );
  assert.deepEqual(audit.violations, []);
});

test('static lifecycle graph mutation catches a real prebuild hook reaching the generator', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    prebuild: 'node ./scripts/evidence/guanyijia-generate.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.scriptNames.includes('prebuild'));
  assert.ok(audit.violations.some((finding) => finding.includes('generator-reachable')));
  assert.ok(audit.violations.some((finding) => finding.includes('executor-construction')));
});

test('static lifecycle roots keep strict operational auditing for fs and non-model fetch', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    prebuild: 'node scripts/prebuild-leak.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/prebuild-leak.mjs': [
        "import { readFile } from 'node:fs/promises';",
        "await readFile('/tmp/not-a-capture-input');",
        "await fetch('https://example.test/non-model');",
      ].join('\n'),
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.scriptNames.includes('prebuild'));
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-node-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('direct-fetch')));
  assert.ok(audit.violations.some((finding) => finding.includes('prebuild-leak.mjs')));
});

test('static ordinary script closure mutation catches a nested npm hidden-model target', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    build: 'npm run hidden-model',
    'hidden-model': 'node scripts/hidden-model.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/hidden-model.mjs': "import OpenAI from 'openai'; new OpenAI({ apiKey: 'SECRET' });\n",
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.scriptNames.includes('hidden-model'));
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-runtime-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('hidden-model.mjs')));
});

test('static ordinary direct script root mutation catches local model entry and module-scope call', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    build: 'node scripts/direct-model.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/direct-model.mjs': "import OpenAI from 'openai'; new OpenAI({ apiKey: 'SECRET' });\n",
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-runtime-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('direct-model.mjs')));
});

test('static ordinary direct root catches a comment-separated provider default import', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    'test:comment-model': 'node scripts/comment-model.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/comment-model.mjs': "import/*provider*/ OpenAI from 'openai'; new OpenAI({ apiKey: 'SECRET' });\n",
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-runtime-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('comment-model.mjs')));
});

test('static and dynamic escaped module specifiers reject provider obfuscation', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    'test:escaped-module': 'node scripts/escaped-module.mjs',
  };
  const auditMutation = async (source) => {
    const graph = await loadRepositoryModuleGraph({
      scripts: mutatedScripts,
      extraEntries: ['vite.config.ts'],
      virtualSources: { 'scripts/escaped-module.mjs': source },
    });
    return auditPackageScriptGraph(
      mutatedScripts,
      graph.sources,
      ['vite.config.ts'],
      strictGraphEntries(mutatedScripts, ['vite.config.ts']),
      graph.missing,
    );
  };
  for (const source of [
    String.raw`import('open\u0061i');`,
    String.raw`require('open\x61i');`,
    String.raw`import OpenAI from 'open\u0061i'; new OpenAI({ apiKey: 'SECRET' });`,
  ]) {
    const audit = await auditMutation(source);
    assert.ok(audit.violations.some((finding) => finding.includes('escaped-module-specifier')));
    assert.ok(audit.violations.some((finding) => finding.includes('escaped-module.mjs')));
  }
});

test('static ordinary multiline local imports traverse helper provider and generator edges', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    'test:multiline-helper': 'node scripts/multiline-entry.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/multiline-entry.mjs': [
        'import {',
        '  helper',
        '} from',
        "  './multiline-helper.mjs';",
        'helper();',
      ].join('\n'),
      'scripts/multiline-helper.mjs': [
        "import OpenAI from 'openai';",
        'import {',
        '  generate',
        '} from',
        "  './evidence/guanyijia-generate.mjs';",
        'export const helper = () => new OpenAI({ apiKey: generate });',
      ].join('\n'),
      'scripts/evidence/guanyijia-generate.mjs': 'export const generate = () => undefined;\n',
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-runtime-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('generator-reachable')));
  assert.ok(audit.violations.some((finding) => finding.includes('multiline-helper.mjs')));
});

test('AST import analysis ignores regex literals while auditing provider and local-helper mutations', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const auditMutation = async (scriptName, command, virtualSources) => {
    const mutatedScripts = { ...packageManifest.scripts, [scriptName]: command };
    const graph = await loadRepositoryModuleGraph({
      scripts: mutatedScripts,
      extraEntries: ['vite.config.ts'],
      virtualSources,
    });
    return auditPackageScriptGraph(
      mutatedScripts,
      graph.sources,
      ['vite.config.ts'],
      strictGraphEntries(mutatedScripts, ['vite.config.ts']),
      graph.missing,
    );
  };

  const directAudit = await auditMutation(
    'test:regex-provider',
    'node scripts/regex-provider.mjs',
    {
      'scripts/regex-provider.mjs': "const marker = /['\"]/; import OpenAI from 'openai'; new OpenAI({ apiKey: marker });\n",
    },
  );
  assert.ok(directAudit.violations.some((finding) => finding.includes('forbidden-runtime-import')));

  const helperAudit = await auditMutation(
    'test:regex-helper',
    'node scripts/regex-entry.mjs',
    {
      'scripts/regex-entry.mjs': [
        "const marker = /['\"]/;",
        'import {',
        '  helper',
        '} from',
        "  './regex-helper.mjs';",
        'helper(marker);',
      ].join('\n'),
      'scripts/regex-helper.mjs': "const marker = /['\"]/; import OpenAI from 'openai'; export const helper = OpenAI;\n",
    },
  );
  assert.ok(helperAudit.violations.some((finding) => finding.includes('forbidden-runtime-import')));
  assert.ok(helperAudit.violations.some((finding) => finding.includes('regex-helper.mjs')));
});

test('static ordinary test script mutation remains strict for dynamic SDK import and model call', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    'test:live': 'node scripts/live.test.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/live.test.mjs': "const sdk = await import('openai'); sdk.responses.create({ model: 'gpt-5' });\n",
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('dynamic-or-require')));
  assert.ok(audit.violations.some((finding) => finding.includes('live.test.mjs')));
});

test('static ordinary test root rejects an unknown local Codex child-process launcher', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    'test:live': 'node scripts/live.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/live.mjs': "import { spawn } from 'node:child_process'; spawn('/usr/local/bin/codex', ['exec', '--read-only']);\n",
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-codex-exec')));
  assert.ok(audit.violations.some((finding) => finding.includes('scripts/live.mjs')));
});

test('static ordinary CommonJS root rejects a provider SDK require', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    'test:require-model': 'node scripts/require-model.cjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/require-model.cjs': "const OpenAI = require('openai'); new OpenAI({ apiKey: 'SECRET' });\n",
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-runtime-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('dynamic-or-require')));
  assert.ok(audit.violations.some((finding) => finding.includes('scripts/require-model.cjs')));
});

test('AST strict source checks re-exports, ImportEquals, parse failures, and escaped local paths', () => {
  const reexportAudit = auditGeneratorSource(
    "export { default } from 'openai';\n",
    'scripts/reexport-provider.mjs',
  );
  assert.ok(reexportAudit.some((finding) => finding.includes('forbidden-runtime-import:openai')));

  const importEqualsAudit = auditGeneratorSource(
    "import OpenAI = require('openai');\n",
    'scripts/import-equals-provider.ts',
  );
  assert.ok(importEqualsAudit.some((finding) => finding.includes('forbidden-runtime-import:openai')));

  const parseFailureAudit = auditGeneratorSource(
    "import { OpenAI from 'openai';\n",
    'scripts/malformed-provider.mjs',
  );
  assert.ok(parseFailureAudit.some((finding) => finding.includes('parse-failure')));

  const escapedLocalAudit = auditGeneratorSource(
    String.raw`import helper from './hel\u0070er.mjs';\n`,
    'scripts/escaped-local-path.mjs',
  );
  assert.ok(escapedLocalAudit.some((finding) => finding.includes('escaped-module-specifier')));
});

test('static ordinary root rejects a non-literal provider import fail-closed', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    'test:live': 'node scripts/live-import.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts: mutatedScripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'scripts/live-import.mjs': "const provider = 'openai'; await import(provider); await import('op' + 'enai');\n",
    },
  });
  const audit = auditPackageScriptGraph(
    mutatedScripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(mutatedScripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('dynamic-or-require')));
  assert.ok(audit.violations.some((finding) => finding.includes('scripts/live-import.mjs')));
});

test('exact CP8 capability paths reject nonliteral, Codex, and environment-driven process targets', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const mutatedScripts = {
    ...packageManifest.scripts,
    'test:e2e:cp8:mutated': 'node scripts/run-cp8-playwright.mjs tests/e2e/core.spec.ts',
  };
  const auditMutation = async (source) => {
    const graph = await loadRepositoryModuleGraph({
      scripts: mutatedScripts,
      extraEntries: ['vite.config.ts'],
      virtualSources: { 'scripts/run-cp8-playwright.mjs': source },
    });
    return auditPackageScriptGraph(
      mutatedScripts,
      graph.sources,
      ['vite.config.ts'],
      strictGraphEntries(mutatedScripts, ['vite.config.ts']),
      graph.missing,
    );
  };

  const aliasAudit = await auditMutation([
    "import { spawn as launch } from 'node:child_process';",
    'const binary = process.env.MODEL_RUNNER;',
    "launch(binary, JSON.parse(process.env.MODEL_ARGS ?? '[]'));",
  ].join('\n'));
  assert.ok(aliasAudit.violations.some((finding) => finding.includes('forbidden-node-import')));
  assert.ok(aliasAudit.violations.some((finding) => finding.includes('direct-live-adapter')));

  const codexEnvAudit = await auditMutation([
    "import { spawn } from 'node:child_process';",
    'const binary = process.env.CODEX_BIN;',
    "spawn(binary, ['exec']);",
  ].join('\n'));
  assert.ok(codexEnvAudit.violations.some((finding) => finding.includes('direct-live-adapter')));

  const curlEnvAudit = await auditMutation([
    "import { spawn } from 'node:child_process';",
    'const url = process.env.MODEL_URL;',
    'spawn(\'curl\', [url]);',
  ].join('\n'));
  assert.ok(curlEnvAudit.violations.some((finding) => finding.includes('direct-live-adapter')));

  const aliasRebindAudit = await auditMutation([
    "import { spawn } from 'node:child_process';",
    'const launch = spawn;',
    "launch(process.env.MODEL_RUNNER, ['exec']);",
  ].join('\n'));
  assert.ok(aliasRebindAudit.violations.some((finding) => finding.includes('source-pin-mismatch')));
});

test('static reachable Vite plugin mutation catches dynamic SDK import through the real loader', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const graph = await loadRepositoryModuleGraph({
    scripts: packageManifest.scripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'vite.config.ts': "import './tests/e2e/mutated-vite-plugin.mjs'; export default {};\n",
      'tests/e2e/mutated-vite-plugin.mjs': "export const sdk = import('openai');\n",
    },
  });
  const audit = auditPackageScriptGraph(
    packageManifest.scripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(packageManifest.scripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('dynamic-or-require')));
  assert.ok(audit.violations.some((finding) => finding.includes('mutated-vite-plugin.mjs')));
});

test('static strict Vite plugin mutation catches bare builtin net import and module-scope connect', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const graph = await loadRepositoryModuleGraph({
    scripts: packageManifest.scripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'vite.config.ts': "import './tests/e2e/bare-net-plugin.mjs'; export default {};\n",
      'tests/e2e/bare-net-plugin.mjs': "import net from 'net'; net.connect({ host: 'secret.invalid', port: 443 });\n",
    },
  });
  const audit = auditPackageScriptGraph(
    packageManifest.scripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(packageManifest.scripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-node-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('bare-net-plugin.mjs')));
});

test('static strict Vite plugin mutation catches bare and node fs/promises imports and calls', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const graph = await loadRepositoryModuleGraph({
    scripts: packageManifest.scripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'vite.config.ts': "import './tests/e2e/fsp-plugin.mjs'; export default {};\n",
      'tests/e2e/fsp-plugin.mjs': "import { readFile } from 'node:fs/promises'; readFile('secret'); const fs = import('fs/promises'); fs.then((module) => module.readFile('secret'));\n",
    },
  });
  const audit = auditPackageScriptGraph(
    packageManifest.scripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(packageManifest.scripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-node-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('dynamic-or-require')));
  assert.ok(audit.violations.some((finding) => finding.includes('fsp-plugin.mjs')));
});

test('static Vite config mutation catches bare fetch and globalThis.fetch at module scope', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const graph = await loadRepositoryModuleGraph({
    scripts: packageManifest.scripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'vite.config.ts': "await fetch('https://api.openai.com/v1/models'); await globalThis.fetch('https://api.openai.com/v1/responses'); export default {};\n",
    },
  });
  const audit = auditPackageScriptGraph(
    packageManifest.scripts,
    graph.sources,
    ['vite.config.ts'],
    strictGraphEntries(packageManifest.scripts, ['vite.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('direct-fetch')));
  assert.ok(audit.violations.some((finding) => finding.includes('globalThis.fetch')));
});

test('static Playwright config mutation catches a local setup that imports and calls an SDK', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const graph = await loadRepositoryModuleGraph({
    scripts: packageManifest.scripts,
    extraEntries: ['playwright.config.ts'],
    virtualSources: {
      'playwright.config.ts': "import './tests/e2e/model-setup.mjs'; export default {};\n",
      'tests/e2e/model-setup.mjs': "import OpenAI from 'openai'; new OpenAI({ apiKey: 'SECRET' });\n",
    },
  });
  const audit = auditPackageScriptGraph(
    packageManifest.scripts,
    graph.sources,
    ['playwright.config.ts'],
    strictGraphEntries(packageManifest.scripts, ['playwright.config.ts']),
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('forbidden-runtime-import')));
  assert.ok(audit.violations.some((finding) => finding.includes('model-setup.mjs')));
});

test('static graph upgrades a shared module from non-strict to strict when reached by a config root', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const scripts = {
    ...packageManifest.scripts,
    'test:shared-canary': 'node scripts/shared-canary.mjs',
  };
  const graph = await loadRepositoryModuleGraph({
    scripts,
    extraEntries: ['scripts/shared-canary.mjs', 'vite.config.ts'],
    virtualSources: {
      'scripts/shared-canary.mjs': "export const sdk = import('openai');\n",
      'vite.config.ts': "import './scripts/shared-canary.mjs'; export default {};\n",
    },
  });
  const audit = auditPackageScriptGraph(
    scripts,
    graph.sources,
    ['scripts/shared-canary.mjs', 'vite.config.ts'],
    ['vite.config.ts'],
    graph.missing,
  );
  assert.ok(audit.violations.some((finding) => finding.includes('dynamic-or-require')));
  assert.ok(audit.violations.some((finding) => finding.includes('shared-canary.mjs')));
});

test('static lifecycle/module graph fails closed when any local script or import source is missing', async () => {
  const packageManifest = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  const missingScriptPackage = {
    ...packageManifest.scripts,
    prebuild: 'node scripts/missing-prebuild-hook.mjs',
  };
  const missingScriptGraph = await loadRepositoryModuleGraph({
    scripts: missingScriptPackage,
    extraEntries: ['vite.config.ts'],
  });
  const missingScriptAudit = auditPackageScriptGraph(
    missingScriptPackage,
    missingScriptGraph.sources,
    ['vite.config.ts'],
    strictGraphEntries(missingScriptPackage, ['vite.config.ts']),
    missingScriptGraph.missing,
  );
  assert.ok(missingScriptAudit.violations.some((finding) => finding.includes('missing-prebuild-hook.mjs')));

  const missingImportGraph = await loadRepositoryModuleGraph({
    scripts: packageManifest.scripts,
    extraEntries: ['vite.config.ts'],
    virtualSources: {
      'vite.config.ts': "import './missing-vite-plugin.mjs'; export default {};\n",
    },
  });
  const missingImportAudit = auditPackageScriptGraph(
    packageManifest.scripts,
    missingImportGraph.sources,
    ['vite.config.ts'],
    strictGraphEntries(packageManifest.scripts, ['vite.config.ts']),
    missingImportGraph.missing,
  );
  assert.ok(missingImportAudit.violations.some((finding) => finding.includes('missing-vite-plugin.mjs')));
});

test('static generator mutation catches direct-main and globalThis.fetch even in unreachable temporary text', () => {
  const findings = auditGeneratorSource(
    "if (import.meta.url === `file://${process.argv[1]}`) globalThis.fetch('https://secret.invalid');",
    'temporary-mutant.mjs',
  );
  assert.ok(findings.some((finding) => finding.endsWith(':direct-main')));
  assert.ok(findings.some((finding) => finding.endsWith(':globalThis.fetch')));
});

test('controlled executor sanitizes dependency and port-property Proxy errors at the public factory boundary', () => {
  const module = requireGenerationModule();
  const failures = [];
  for (const trappedPort of ['executionAdmission', 'codexSession']) {
    const harness = controlledExecutorFixture();
    const hostilePort = trappedPort === 'executionAdmission' ? new Proxy(harness.executionAdmission, {
      get(target, key, receiver) {
        if (key === 'admit') throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
        return Reflect.get(target, key, receiver);
      },
    }) : harness.executionAdmission;
    const hostileSession = trappedPort === 'codexSession' ? new Proxy(harness.codexSession, {
      get(target, key, receiver) {
        if (key === 'run') throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
        return Reflect.get(target, key, receiver);
      },
    }) : harness.codexSession;

    try {
      assert.throws(
        () => module.createControlledCodexExecutor({
          env: controlledCodexEnvironment,
          executionAdmission: hostilePort,
          codexSession: hostileSession,
        }),
        assertSafeExecutorError('INTERNAL_GENERATION_FAILURE'),
      );
    } catch {
      failures.push(trappedPort);
    }
  }
  assert.deepEqual(failures, []);
});

test('controlled executor rejects execution-admission TOCTOU changes before any session call', async () => {
  const module = requireGenerationModule();
  const harness = controlledExecutorFixture();
  const admitted = structuredClone(harness.executionAdmissionResult);
  let executablePathReads = 0;
  const changingAdmission = new Proxy(admitted, {
    get(target, key, receiver) {
      if (key === 'executablePath') {
        executablePathReads += 1;
        return executablePathReads === 1 ? target[key] : 'relative-after-check';
      }
      return Reflect.get(target, key, receiver);
    },
  });
  harness.executionAdmission = { admit: async () => changingAdmission };
  const executor = createControlledExecutor(module, harness);

  await assert.rejects(
    () => executor.run(controlledExecutorRequest(harness)),
    assertSafeExecutorError('CODEX_CONFIGURATION_REJECTED'),
  );
  assert.equal(harness.calls.length, 0);
});

test('controlled executor rejects semantically aliased schema and last-message targets', async () => {
  const module = requireGenerationModule();
  const harness = controlledExecutorFixture();
  const aliased = structuredClone(harness.executionAdmissionResult);
  aliased.lastMessagePath = '/private/guanyijia/schema/./guanyijia-proposal.schema.json';
  assert.notEqual(aliased.lastMessagePath, aliased.proposalSchemaPath);
  harness.executionAdmission = { admit: async () => aliased };
  const executor = createControlledExecutor(module, harness);

  await assert.rejects(
    () => executor.run(controlledExecutorRequest(harness)),
    assertSafeExecutorError('CODEX_CONFIGURATION_REJECTED'),
  );
  assert.equal(harness.calls.length, 0);
});

test('controlled executor rejects forbidden empty credentials and admission pin/path mismatches before both ports', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'empty forbidden credential',
      env: { ...controlledCodexEnvironment, OPENAI_API_KEY: '' },
      expectedCode: 'API_PROVIDER_CREDENTIAL_FORBIDDEN',
      mutateAdmission: (admitted) => admitted,
    },
    {
      label: 'binary pin mismatch',
      env: controlledCodexEnvironment,
      expectedCode: 'CODEX_CONFIGURATION_REJECTED',
      mutateAdmission: (admitted) => ({ ...admitted, codexBinarySha256: digest('wrong-binary') }),
    },
    {
      label: 'binary path mismatch',
      env: controlledCodexEnvironment,
      expectedCode: 'CODEX_CONFIGURATION_REJECTED',
      mutateAdmission: (admitted) => ({ ...admitted, executablePath: 'relative/codex' }),
    },
    {
      label: 'prompt template pin bytes mismatch',
      env: controlledCodexEnvironment,
      expectedCode: 'CODEX_CONFIGURATION_REJECTED',
      mutateAdmission: (admitted) => ({ ...admitted, promptTemplateBytes: new TextEncoder().encode('changed prompt\n') }),
    },
    {
      label: 'proposal schema pin bytes mismatch',
      env: controlledCodexEnvironment,
      expectedCode: 'CODEX_CONFIGURATION_REJECTED',
      mutateAdmission: (admitted) => ({ ...admitted, proposalSchemaBytes: new TextEncoder().encode('{}\n') }),
    },
    {
      label: 'working directory aliases schema target',
      env: controlledCodexEnvironment,
      expectedCode: 'CODEX_CONFIGURATION_REJECTED',
      mutateAdmission: (admitted) => ({
        ...admitted,
        workingDirectory: { ...admitted.workingDirectory, path: admitted.proposalSchemaPath },
      }),
    },
    {
      label: 'last-message path is non-absolute',
      env: controlledCodexEnvironment,
      expectedCode: 'CODEX_CONFIGURATION_REJECTED',
      mutateAdmission: (admitted) => ({ ...admitted, lastMessagePath: 'relative/last-message.json' }),
    },
  ];

  for (const invalid of cases) {
    const harness = controlledExecutorFixture();
    const admitted = invalid.mutateAdmission(structuredClone(harness.executionAdmissionResult));
    harness.executionAdmission = {
      admit: async (request) => {
        harness.admissionCalls.push(request);
        return admitted;
      },
    };
    const executor = createControlledExecutor(module, harness, invalid.env);
    await assert.rejects(
      () => executor.run(controlledExecutorRequest(harness)),
      assertSafeExecutorError(invalid.expectedCode),
      invalid.label,
    );
    assert.equal(harness.admissionCalls.length, invalid.label === 'empty forbidden credential' ? 0 : 1, invalid.label);
    assert.equal(harness.calls.length, 0, invalid.label);
  }
});

test('controlled executor has no retry or secret leakage for probe, post-auth, execution, audit, or JSON failures', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'version whitespace',
      expectedCode: 'CODEX_VERSION_UNSUPPORTED',
      expectedCalls: 1,
      override: ({ index, result }) => index === 0
        ? { ...result, stdout: new TextEncoder().encode('codex-cli 0.148.0-alpha.15 \n') }
        : result,
    },
    {
      label: 'version stderr',
      expectedCode: 'CODEX_VERSION_UNSUPPORTED',
      expectedCalls: 1,
      override: ({ index, result }) => index === 0
        ? { ...result, stderr: new TextEncoder().encode('Authorization_Bearer_SECRET') }
        : result,
    },
    {
      label: 'version ANSI',
      expectedCode: 'CODEX_VERSION_UNSUPPORTED',
      expectedCalls: 1,
      override: ({ index, result }) => index === 0
        ? { ...result, stdout: new TextEncoder().encode('\u001b[32mcodex-cli 0.148.0-alpha.15\n') }
        : result,
    },
    {
      label: 'version signal',
      expectedCode: 'CODEX_VERSION_UNSUPPORTED',
      expectedCalls: 1,
      override: ({ index, result }) => index === 0
        ? { ...result, exitCode: null, signal: 'SIGTERM', stdout: new Uint8Array() }
        : result,
    },
    {
      label: 'version nonzero',
      expectedCode: 'CODEX_VERSION_UNSUPPORTED',
      expectedCalls: 1,
      override: ({ index, result }) => index === 0
        ? { ...result, exitCode: 1, stdout: new Uint8Array() }
        : result,
    },
    {
      label: 'pre-auth whitespace',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 2,
      override: ({ index, result }) => index === 1
        ? { ...result, stdout: new TextEncoder().encode('Logged in using ChatGPT \n') }
        : result,
    },
    {
      label: 'pre-auth stderr',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 2,
      override: ({ index, result }) => index === 1
        ? { ...result, stderr: new TextEncoder().encode('Authorization_Bearer_SECRET') }
        : result,
    },
    {
      label: 'pre-auth ANSI',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 2,
      override: ({ index, result }) => index === 1
        ? { ...result, stdout: new TextEncoder().encode('\u001b[31mLogged in using ChatGPT\n') }
        : result,
    },
    {
      label: 'pre-auth signal',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 2,
      override: ({ index, result }) => index === 1
        ? { ...result, exitCode: null, signal: 'SIGTERM', stdout: new Uint8Array() }
        : result,
    },
    {
      label: 'pre-auth nonzero',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 2,
      override: ({ index, result }) => index === 1
        ? { ...result, exitCode: 1, stdout: new Uint8Array() }
        : result,
    },
    {
      label: 'post-auth drift',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 4,
      override: ({ index, result }) => index === 3
        ? { ...result, stdout: new TextEncoder().encode('Logged in using API key\n') }
        : result,
    },
    {
      label: 'execution throw',
      expectedCode: 'CODEX_EXECUTION_FAILED',
      expectedCalls: 3,
      override: ({ index, result }) => {
        if (index === 2) throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
        return result;
      },
    },
    {
      label: 'execution timeout',
      expectedCode: 'CODEX_EXECUTION_FAILED',
      expectedCalls: 3,
      override: ({ index, result }) => index === 2
        ? { ...result, exitCode: null, signal: 'SIGTERM' }
        : result,
    },
    {
      label: 'execution stdout overflow',
      expectedCode: 'CODEX_EXECUTION_FAILED',
      expectedCalls: 3,
      override: ({ index, result }) => index === 2
        ? { ...result, stdout: new Uint8Array(2 * 1024 * 1024 + 1) }
        : result,
    },
    {
      label: 'execution stderr overflow',
      expectedCode: 'CODEX_EXECUTION_FAILED',
      expectedCalls: 3,
      override: ({ index, result }) => index === 2
        ? { ...result, stderr: new Uint8Array(64 * 1024 + 1) }
        : result,
    },
    {
      label: 'audit mismatch',
      expectedCode: 'CODEX_EXECUTION_FAILED',
      expectedCalls: 3,
      override: ({ index, result }) => index === 2
        ? { ...result, audit: { completionEventCount: 2, toolCallCount: 0 } }
        : result,
    },
    {
      label: 'missing last message',
      expectedCode: 'CODEX_EXECUTION_FAILED',
      expectedCalls: 3,
      override: ({ index, result }) => {
        if (index !== 2) return result;
        const { lastMessageBytes, ...withoutLastMessage } = result;
        void lastMessageBytes;
        return withoutLastMessage;
      },
    },
    {
      label: 'last message oversize',
      expectedCode: 'CODEX_EXECUTION_FAILED',
      expectedCalls: 3,
      override: ({ index, result }) => index === 2
        ? { ...result, lastMessageBytes: new Uint8Array(8 * 1024 * 1024 + 1) }
        : result,
    },
    {
      label: 'bad JSON',
      expectedCode: 'PROPOSAL_SCHEMA_INVALID',
      expectedCalls: 3,
      override: ({ index, result }) => index === 2
        ? { ...result, lastMessageBytes: new TextEncoder().encode('{not-json') }
        : result,
    },
    {
      label: 'invalid strict output',
      expectedCode: 'PROPOSAL_CITATION_INVALID',
      expectedCalls: 3,
      override: ({ index, result }) => {
        if (index !== 2) return result;
        const invalidProposal = JSON.parse(new TextDecoder().decode(result.lastMessageBytes));
        invalidProposal.invocationEnvelopeSha256 = digest('wrong-envelope');
        return { ...result, lastMessageBytes: new TextEncoder().encode(JSON.stringify(invalidProposal)) };
      },
    },
  ];

  for (const invalid of cases) {
    const harness = controlledExecutorFixture();
    overrideCodexRun(harness, invalid.override);
    const executor = createControlledExecutor(module, harness);
    await assert.rejects(
      () => executor.run(controlledExecutorRequest(harness)),
      assertSafeExecutorError(invalid.expectedCode),
      invalid.label,
    );
    assert.equal(harness.admissionCalls.length, 1, invalid.label);
    assert.equal(harness.calls.length, invalid.expectedCalls, `${invalid.label} must not retry or fall back`);
  }
});

test('controlled executor rebuilds native, mutable, and subclassed secret errors at every port stage', async () => {
  const module = requireGenerationModule();
  const failures = [];
  const stages = [
    {
      label: 'execution admission',
      expectedCode: 'CODEX_CONFIGURATION_REJECTED',
      expectedCalls: 0,
      install(harness, error) {
        harness.executionAdmission = {
          admit: async (request) => {
            harness.admissionCalls.push(request);
            throw error;
          },
        };
      },
    },
    { label: 'version probe', expectedCode: 'CODEX_VERSION_UNSUPPORTED', expectedCalls: 1, index: 0 },
    { label: 'pre-auth probe', expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN', expectedCalls: 2, index: 1 },
    { label: 'exec', expectedCode: 'CODEX_EXECUTION_FAILED', expectedCalls: 3, index: 2 },
    { label: 'post-auth probe', expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN', expectedCalls: 4, index: 3 },
  ];
  const variants = ['native', 'mutable', 'subclassed'];

  for (const stage of stages) {
    for (const variant of variants) {
      const harness = controlledExecutorFixture();
      const error = (() => {
        if (variant === 'native') return new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
        const ErrorClass = variant === 'subclassed'
          ? class ForgedExecutorError extends module.ProposalGenerationError {}
          : module.ProposalGenerationError;
        const forged = new ErrorClass('INTERNAL_GENERATION_FAILURE');
        forged.code = `Authorization_Bearer_SECRET\nforged-${stage.label}`;
        forged.message = `forged-${variant}\nprivate-token=SECRET`;
        return forged;
      })();
      if (stage.install) {
        stage.install(harness, error);
      } else {
        const originalRun = harness.codexSession.run;
        harness.codexSession.run = async (command) => {
          const result = await originalRun(command);
          if (harness.calls.length - 1 === stage.index) throw error;
          return result;
        };
      }
      const executor = createControlledExecutor(module, harness);
      try {
        await assert.rejects(
          () => executor.run(controlledExecutorRequest(harness)),
          assertSafeExecutorError(stage.expectedCode),
        );
        assert.equal(harness.calls.length, stage.expectedCalls);
      } catch {
        failures.push(`${stage.label}:${variant}`);
      }
    }
  }
  assert.deepEqual(failures, []);
});

test('controlled executor rebuilds non-Error secret-like throwables at every port stage without retry', async () => {
  const module = requireGenerationModule();
  const failures = [];
  const stages = [
    {
      label: 'execution admission',
      expectedCode: 'CODEX_CONFIGURATION_REJECTED',
      expectedCalls: 0,
      install(harness, throwable) {
        harness.executionAdmission = {
          admit: async (request) => {
            harness.admissionCalls.push(request);
            throw throwable;
          },
        };
      },
    },
    { label: 'version probe', expectedCode: 'CODEX_VERSION_UNSUPPORTED', expectedCalls: 1, index: 0 },
    { label: 'pre-auth probe', expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN', expectedCalls: 2, index: 1 },
    { label: 'exec', expectedCode: 'CODEX_EXECUTION_FAILED', expectedCalls: 3, index: 2 },
    { label: 'post-auth probe', expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN', expectedCalls: 4, index: 3 },
  ];
  const throwables = [
    'Authorization_Bearer_SECRET\nprivate-token=SECRET',
    42,
    null,
    undefined,
    { code: 'Authorization_Bearer_SECRET', message: 'private-token=SECRET\n' },
    Object.create(null),
  ];

  for (const stage of stages) {
    for (const throwable of throwables) {
      const harness = controlledExecutorFixture();
      if (stage.install) {
        stage.install(harness, throwable);
      } else {
        const originalRun = harness.codexSession.run;
        harness.codexSession.run = async (command) => {
          const result = await originalRun(command);
          if (harness.calls.length - 1 === stage.index) throw throwable;
          return result;
        };
      }
      const executor = createControlledExecutor(module, harness);
      try {
        await assert.rejects(
          () => executor.run(controlledExecutorRequest(harness)),
          assertSafeExecutorError(stage.expectedCode),
        );
        assert.equal(harness.calls.length, stage.expectedCalls, `${stage.label} must not retry`);
      } catch {
        failures.push(`${stage.label}:${String(throwable)}`);
      }
    }
  }
  assert.deepEqual(failures, []);
});

test('controlled executor rejects exact post-auth and execution byte failures without retry', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'post-auth whitespace',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 4,
      override: ({ index, result }) => index === 3
        ? { ...result, stdout: new TextEncoder().encode('Logged in using ChatGPT \n') }
        : result,
    },
    {
      label: 'post-auth ANSI',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 4,
      override: ({ index, result }) => index === 3
        ? { ...result, stdout: new TextEncoder().encode('\u001b[32mLogged in using ChatGPT\n') }
        : result,
    },
    {
      label: 'post-auth stderr',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 4,
      override: ({ index, result }) => index === 3
        ? { ...result, stderr: new TextEncoder().encode('Authorization_Bearer_SECRET') }
        : result,
    },
    {
      label: 'post-auth signal',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 4,
      override: ({ index, result }) => index === 3
        ? { ...result, exitCode: null, signal: 'SIGTERM' }
        : result,
    },
    {
      label: 'post-auth nonzero',
      expectedCode: 'CODEX_AUTH_MODE_FORBIDDEN',
      expectedCalls: 4,
      override: ({ index, result }) => index === 3
        ? { ...result, exitCode: 1 }
        : result,
    },
    {
      label: 'execution nonzero',
      expectedCode: 'CODEX_EXECUTION_FAILED',
      expectedCalls: 3,
      override: ({ index, result }) => index === 2
        ? { ...result, exitCode: 1 }
        : result,
    },
    {
      label: 'invalid UTF-8 last message',
      expectedCode: 'PROPOSAL_SCHEMA_INVALID',
      expectedCalls: 3,
      override: ({ index, result }) => index === 2
        ? { ...result, lastMessageBytes: new Uint8Array([0xc3, 0x28]) }
        : result,
    },
  ];

  for (const invalid of cases) {
    const harness = controlledExecutorFixture();
    overrideCodexRun(harness, invalid.override);
    const executor = createControlledExecutor(module, harness);
    await assert.rejects(
      () => executor.run(controlledExecutorRequest(harness)),
      assertSafeExecutorError(invalid.expectedCode),
      invalid.label,
    );
    assert.equal(harness.admissionCalls.length, 1, invalid.label);
    assert.equal(harness.calls.length, invalid.expectedCalls, `${invalid.label} must not retry`);
  }
});

test('controlled executor rejects a Codex port that mutates stdin instead of accepting or hashing mutated bytes', async () => {
  const module = requireGenerationModule();
  const harness = controlledExecutorFixture();
  const originalRun = harness.codexSession.run;
  harness.codexSession.run = async (command) => {
    const result = await originalRun(command);
    if (command.argv[0] === 'exec') command.stdin[0] ^= 0xff;
    return result;
  };
  const executor = createControlledExecutor(module, harness);

  await assert.rejects(
    () => executor.run(controlledExecutorRequest(harness)),
    assertSafeExecutorError('CODEX_EXECUTION_FAILED'),
  );
  assert.equal(harness.admissionCalls.length, 1);
  assert.equal(harness.calls.length, 3, 'stdin mutation must stop before post-auth and never retry');
});

test('controlled executor rejects whitespace-only required child-environment values', async () => {
  const module = requireGenerationModule();
  for (const key of ['CODEX_HOME', 'PATH', 'TMPDIR']) {
    const harness = controlledExecutorFixture();
    const env = { ...controlledCodexEnvironment, [key]: ' \t\n' };
    const executor = createControlledExecutor(module, harness, env);
    await assert.rejects(
      () => executor.run(controlledExecutorRequest(harness)),
      assertSafeExecutorError('CODEX_CONFIGURATION_REJECTED'),
      key,
    );
    assert.equal(harness.admissionCalls.length, 0, key);
    assert.equal(harness.calls.length, 0, key);
  }
});

test('strict invocation binds promptTemplateSha256 to a real non-empty functional UTF-8 instruction asset', async () => {
  // Read the reviewed production asset; do not substitute test-local prompt
  // bytes for the attestation.
  const promptBytes = await readFile(expectedPromptTemplatePath);
  assert.ok(promptBytes.length > 0, 'the fixed prompt template must not be empty');
  const promptText = new TextDecoder('utf-8', { fatal: true }).decode(promptBytes);
  assert.match(promptText, /GUANYIJIA/i);
  assert.match(promptText, /citation/i);

  const fixture = strictProposalFixture();
  rebindStrictInvocationEnvelope(fixture, (envelope) => {
    envelope.promptTemplateSha256 = digest(promptBytes);
  });
  assert.equal(fixture.invocationEnvelope.promptTemplateSha256, digest(promptBytes));
});

test('strict invocation rejects a prompt template label literal even when the attacker recomputes the envelope', async () => {
  const module = requireGenerationModule();
  const fixture = currentLabelCompatibleStrictFixture();
  assert.equal(fixture.invocationEnvelope.promptTemplateSha256, currentPromptTemplateLabelSha256);

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /prompt|template|instruction|digest|policy|invalid/i,
  );
});

test('strict invocation rejects recomputed prompt bytes that differ from the reviewed fixed template', async () => {
  const module = requireGenerationModule();
  const fixture = currentLabelCompatibleStrictFixture();
  const tamperedPromptBytes = new TextEncoder().encode([
    'GUANYIJIA evidence proposal instructions',
    'citation output contract changed by attacker',
  ].join('\n'));
  rebindStrictInvocationEnvelope(fixture, (envelope) => {
    envelope.promptTemplateSha256 = digest(tamperedPromptBytes);
  });

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /prompt|template|instruction|digest|policy|invalid/i,
  );
});

test('strict invocation carries a static reviewed CLI binary pin and rejects a version-label-derived pin', async () => {
  const module = requireGenerationModule();
  const fixture = strictProposalFixture();
  assert.equal(fixture.invocationEnvelope.executor.codexBinarySha256, trustedCodexBinarySha256);
  assert.notEqual(trustedCodexBinarySha256, digest('codex-cli version'));

  rebindStrictInvocationEnvelope(fixture, (envelope) => {
    envelope.executor.codexBinarySha256 = digest('codex-cli version');
  });
  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /executor|binary|pin|version|digest|policy|invalid/i,
  );
});

test('strict prompt input uses a domain-separated canonical packet with template, schema, sources, and citation material', async () => {
  const fixture = strictProposalFixture();
  const expectedPromptInputSha256 = strictPromptInputDigest({
    storyKey: fixture.proposal.storyKey,
    runId: fixture.proposal.runId,
    admittedSources: fixture.admittedSources,
    resolvedCitations: fixture.resolvedCitations,
    promptTemplateSha256: fixture.invocationEnvelope.promptTemplateSha256,
    proposalSchemaSha256: fixture.invocationEnvelope.proposalSchemaSha256,
    citationMaterialSha256: fixture.invocationEnvelope.citationMaterialSha256,
  });
  assert.equal(fixture.invocationEnvelope.promptInputSha256, expectedPromptInputSha256);
  assert.notEqual(
    fixture.invocationEnvelope.promptInputSha256,
    fixture.invocationEnvelope.citationMaterialSha256,
  );
});

test('strict invocation rejects promptInputSha256 copied from citationMaterialSha256', async () => {
  const module = requireGenerationModule();
  const fixture = currentLabelCompatibleStrictFixture();
  assert.equal(
    fixture.invocationEnvelope.promptInputSha256,
    fixture.invocationEnvelope.citationMaterialSha256,
  );

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /prompt|input|citation|packet|digest|domain|invalid/i,
  );
});

test('strict pure citation validation rejects a reduced source-only envelope digest even when the attacker recomputes it', async () => {
  const module = requireGenerationModule();
  const fixture = strictProposalFixture();
  fixture.proposal.invocationEnvelopeSha256 = digest(`${stableJson({
    schemaVersion: 1,
    kind: 'GUANYIJIA_PROPOSAL_INVOCATION',
    storyKey: fixture.proposal.storyKey,
    runId: fixture.proposal.runId,
    inputs: fixture.admittedSources,
  })}\n`);

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /envelope|executor|policy|digest|hash|invalid/i,
  );
});

test('strict pure citation validation rejects executor, prompt, schema, citation, and raw-manifest policy mismatches', async () => {
  const module = requireGenerationModule();
  const cases = [
    { label: 'executor model mismatch', mutate: (envelope) => { envelope.executor.requestedModel = 'gpt-5.6-terra'; } },
    { label: 'prompt policy mismatch', mutate: (envelope) => { envelope.promptTemplateSha256 = digest('attacker-prompt'); } },
    { label: 'schema policy mismatch', mutate: (envelope) => { envelope.proposalSchemaSha256 = digest('attacker-schema'); } },
    { label: 'citation material mismatch', mutate: (envelope) => { envelope.citationMaterialSha256 = digest('attacker-citations'); } },
    { label: 'raw manifest mismatch', mutate: (envelope) => { envelope.rawManifestDigest = digest('attacker-raw-manifests'); } },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    rebindStrictInvocationEnvelope(fixture, invalid.mutate);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      /envelope|executor|policy|prompt|schema|citation|manifest|digest|invalid/i,
      invalid.label,
    );
  }
});

test('strict pure citation validation rejects an invocation envelope whose source inputs do not exactly equal admitted sources', async () => {
  const module = requireGenerationModule();
  const fixture = strictProposalFixture();
  rebindStrictInvocationEnvelope(fixture, (envelope) => {
    envelope.inputs[0].snapshotId = 'snapshot:guanyijia_mysql:attacker';
  });

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /envelope|input|source|snapshot|manifest|invalid/i,
  );
});

test('strict pure citation validation binds proposalSchemaSha256 to the actual pinned schema file rather than a label constant', async () => {
  const module = requireGenerationModule();
  const fixture = strictProposalFixture();
  assert.equal(fixture.invocationEnvelope.proposalSchemaSha256, digest(proposalSchemaSource));
  rebindStrictInvocationEnvelope(fixture, (envelope) => {
    envelope.proposalSchemaSha256 = digest('guanyijia-proposal-schema-v1');
  });

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /schema|envelope|digest|policy|invalid/i,
  );
});

test('strict pure citation validation rejects source proposals that are missing, duplicated, or out of canonical order', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'wrong source order',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals = [
          fixture.proposal.sourceProposals[1],
          fixture.proposal.sourceProposals[0],
          fixture.proposal.sourceProposals[2],
        ];
      },
    },
    {
      label: 'missing source proposal',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals = fixture.proposal.sourceProposals.slice(0, 2);
      },
    },
    {
      label: 'duplicate source proposal',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals[2] = structuredClone(fixture.proposal.sourceProposals[0]);
      },
    },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    invalid.mutate(fixture);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      /source|snapshot|proposal|order|canonical|invalid/i,
      invalid.label,
    );
  }
});

test('strict pure citation validation rejects a source proposal whose snapshot does not match its admitted manifest', async () => {
  const module = requireGenerationModule();
  const fixture = strictProposalFixture();
  fixture.proposal.sourceProposals[1].snapshotId = 'snapshot:guanyijia_github:wrong';

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /snapshot|source|manifest|identity|invalid/i,
  );
});

test('strict pure citation validation rejects an invocation envelope hash that is not bound to admitted source inputs', async () => {
  const module = requireGenerationModule();
  const fixture = strictProposalFixture();
  fixture.proposal.invocationEnvelopeSha256 = digest('tampered-invocation-envelope');

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /envelope|invocation|digest|hash|invalid/i,
  );
});

test('strict pure citation validation rejects duplicate proposal or evidence IDs', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'duplicate proposalId',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals[0].statements[1].proposalId = fixture.proposal.sourceProposals[0].statements[0].proposalId;
      },
    },
    {
      label: 'duplicate evidenceRef',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals[0].statements[1].evidence[0].evidenceRef = fixture.proposal.sourceProposals[0].statements[0].evidence[0].evidenceRef;
      },
    },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    invalid.mutate(fixture);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      /duplicate|proposal|evidence|identity|invalid/i,
      invalid.label,
    );
  }
});

test('strict pure citation validation rejects duplicate or unused independently resolved citations', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'duplicate resolved citation',
      mutate: (fixture) => {
        fixture.resolvedCitations.push(structuredClone(fixture.resolvedCitations[0]));
      },
    },
    {
      label: 'unused resolved citation',
      mutate: (fixture) => {
        fixture.resolvedCitations.push({
          ...structuredClone(fixture.resolvedCitations[0]),
          evidenceRef: 'evidence:unused-resolved',
        });
      },
    },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    invalid.mutate(fixture);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      /duplicate|unused|resolved|evidence|citation|invalid/i,
      invalid.label,
    );
  }
});

test('strict pure citation validation rejects the same resolved evidence tuple under a different evidenceRef', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'same-statement tuple alias',
      mutate: (fixture) => {
        const statement = fixture.proposal.sourceProposals[0].statements[0];
        const original = statement.evidence[0];
        const aliasEvidenceRef = 'evidence:alias:same-statement';
        statement.evidence.push({ ...structuredClone(original), evidenceRef: aliasEvidenceRef });
        fixture.resolvedCitations.push({ ...structuredClone(fixture.resolvedCitations[0]), evidenceRef: aliasEvidenceRef });
      },
    },
    {
      label: 'cross-statement tuple alias',
      mutate: (fixture) => {
        const firstStatement = fixture.proposal.sourceProposals[0].statements[0];
        const secondStatement = fixture.proposal.sourceProposals[0].statements[1];
        const original = firstStatement.evidence[0];
        const aliasEvidenceRef = 'evidence:alias:cross-statement';
        secondStatement.evidence[0] = { ...structuredClone(original), evidenceRef: aliasEvidenceRef, title: '跨语句重复底层证据' };
        fixture.resolvedCitations[1] = { ...structuredClone(fixture.resolvedCitations[0]), evidenceRef: aliasEvidenceRef };
      },
    },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    invalid.mutate(fixture);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      /duplicate|tuple|evidence|citation|identity|invalid/i,
      invalid.label,
    );
  }
});

test('strict pure citation validation accepts a valid three-source generated-target-only proposal with no resolved citations', async () => {
  const module = requireGenerationModule();
  const fixture = strictGeneratedOnlyFixture();

  assert.deepEqual(fixture.resolvedCitations, []);
  const validated = await validateStrictProposal(module, fixture);
  assert.deepEqual(validated, fixture.proposal);
});

test('strict pure citation validation rejects an invalid generated-target-only statement even when no resolved citations are required', async () => {
  const module = requireGenerationModule();
  const fixture = strictGeneratedOnlyFixture();
  fixture.proposal.sourceProposals[0].statements[0].provenance = 'OBSERVED';

  await assert.rejects(
    () => validateStrictProposal(module, fixture),
    /generated|target|provenance|GAP|pending|invalid/i,
  );
});

test('strict pure citation validation enforces pinned schema maxLength and maxItems boundaries', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'citation title maxLength',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals[0].statements[0].evidence[0].title = 'x'.repeat(65537);
      },
    },
    {
      label: 'affectedObjectRefs maxItems',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals[0].statements[0].affectedObjectRefs = Array.from({ length: 201 }, (_, index) => ({
          scope: 'RESULT',
          documentVersion: 'v1',
          kind: 'ENTITY',
          objectId: `orders-${index}`,
        }));
      },
    },
    {
      label: 'source statements maxItems',
      mutate: (fixture) => {
        const sourceProposal = fixture.proposal.sourceProposals[0];
        sourceProposal.statements.push(...Array.from({ length: 199 }, (_, index) => strictGeneratedStatement('guanyijia_mysql', index + 100)));
      },
    },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    invalid.mutate(fixture);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      /length|items|maximum|schema|proposal|invalid/i,
      invalid.label,
    );
  }
});

for (const boundary of [
  { label: 'non-BMP code-point length within schema limit', count: 65536, shouldReject: false },
  { label: 'non-BMP code-point length above schema limit', count: 65537, shouldReject: true },
]) {
  test(`strict pure citation validation enforces Unicode ${boundary.label}`, async () => {
    const module = requireGenerationModule();
    const fixture = strictProposalFixture();
    fixture.proposal.sourceProposals[0].statements[0].evidence[0].title = '😀'.repeat(boundary.count);
    const operation = () => validateStrictProposal(module, fixture);
    if (boundary.shouldReject) {
      await assert.rejects(operation, /length|schema|maximum|proposal|invalid/i);
    } else {
      await assert.doesNotReject(operation, boundary.label);
    }
  });
}

test('strict pure citation validation rejects observed artifact digest or locator mismatches against the independent resolved tuple', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'artifact digest mismatch',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals[0].statements[0].evidence[0].artifactSha256 = digest('wrong-artifact');
      },
    },
    {
      label: 'locator mismatch',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals[0].statements[0].evidence[0].locator = {
          kind: 'FILE_LINES', path: 'ddl/other.sql', startLine: 1, endLine: 3,
        };
      },
    },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    invalid.mutate(fixture);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      /artifact|digest|locator|citation|match|invalid/i,
      invalid.label,
    );
  }
});

test('strict pure citation validation rejects extra proposal fields and USER_CONFIRMED model output', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'extra proposal field',
      mutate: (fixture) => {
        fixture.proposal.unapprovedPrivateNote = 'must-not-cross-pure-seam';
      },
    },
    {
      label: 'USER_CONFIRMED provenance',
      mutate: (fixture) => {
        fixture.proposal.sourceProposals[0].statements[0].provenance = 'USER_CONFIRMED';
      },
    },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    invalid.mutate(fixture);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      /extra|unknown|provenance|confirmed|proposal|invalid/i,
      invalid.label,
    );
  }
});

function assertSafeFixedProposalError(expectedCode) {
  return (error) => {
    assert.equal(error?.name, 'ProposalGenerationError');
    assert.equal(error?.code, expectedCode);
    assert.doesNotMatch(String(error?.message ?? error), /Authorization_Bearer_SECRET|private-token=SECRET|forged-|\r?\n/);
    return true;
  };
}

function assertSafeProposalContractError(error) {
  assertSafeFixedProposalError('PROPOSAL_CITATION_INVALID')(error);
  return true;
}

function descriptorTrapProposal() {
  return new Proxy(validProposal(), {
    ownKeys(target) {
      return [...Reflect.ownKeys(target), 'rawPrivateNote'];
    },
    getOwnPropertyDescriptor(target, key) {
      if (key === 'rawPrivateNote') {
        throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
      }
      return Reflect.getOwnPropertyDescriptor(target, key);
    },
  });
}

function asyncFieldTrap(value, trappedKey) {
  return new Proxy(value, {
    get(target, key, receiver) {
      if (key === trappedKey) {
        throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
      }
      return Reflect.get(target, key, receiver);
    },
  });
}

for (const hostile of [
  {
    label: 'manifest expected field',
    expectedCode: 'INPUT_MANIFEST_INVALID',
    input: () => ({
      rawManifest: asyncFieldTrap(validRawManifest(), 'schemaVersion'),
      proposal: validProposal(),
    }),
  },
  {
    label: 'proposal expected field',
    expectedCode: 'PROPOSAL_CITATION_INVALID',
    input: () => ({
      rawManifest: validRawManifest(),
      proposal: asyncFieldTrap(validProposal(), 'kind'),
    }),
  },
]) {
  test(`legacy public validate sanitizes async Proxy get trap from ${hostile.label}`, async () => {
    const module = requireGenerationModule();
    let artifactReads = 0;
    await assert.rejects(
      () => module.validateProposalCitations({
        ...hostile.input(),
        readArtifact: async () => {
          artifactReads += 1;
          return artifactBytes;
        },
      }),
      assertSafeFixedProposalError(hostile.expectedCode),
      hostile.label,
    );
    assert.equal(artifactReads, 0);
  });
}

function legacyHostileProposalCases() {
  return [
    {
      label: 'non-enumerable extra',
      proposal: (() => {
        const proposal = validProposal();
        Object.defineProperty(proposal, 'rawPrivateNote', { value: 'SECRET', enumerable: false });
        return proposal;
      })(),
    },
    {
      label: 'symbol extra',
      proposal: (() => {
        const proposal = validProposal();
        proposal[Symbol('rawPrivateNote')] = 'SECRET';
        return proposal;
      })(),
    },
    {
      label: 'accessor extra',
      proposal: (() => {
        const proposal = validProposal();
        Object.defineProperty(proposal, 'rawPrivateNote', {
          enumerable: true,
          get() {
            throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
          },
        });
        return proposal;
      })(),
    },
    { label: 'descriptor trap', proposal: descriptorTrapProposal() },
  ];
}

for (const invalid of legacyHostileProposalCases()) {
  test(`legacy proposal validation rejects ${invalid.label} extra fields without getter invocation or secret leakage`, async () => {
    const module = requireGenerationModule();
    let artifactReads = 0;
    await assert.rejects(
      () => module.validateProposalCitations({
        rawManifest: validRawManifest(),
        proposal: invalid.proposal,
        readArtifact: async () => {
          artifactReads += 1;
          return artifactBytes;
        },
      }),
      assertSafeProposalContractError,
      invalid.label,
    );
    assert.equal(artifactReads, 0, `${invalid.label} must fail before artifact reads`);
  });
}

test('strict proposal validation rejects hidden, symbol, accessor, and descriptor-trap extra fields without invoking getters', async () => {
  const module = requireGenerationModule();
  const cases = [
    {
      label: 'non-enumerable extra',
      mutate: (fixture) => {
        Object.defineProperty(fixture.proposal, 'rawPrivateNote', { value: 'SECRET', enumerable: false });
      },
    },
    {
      label: 'symbol extra',
      mutate: (fixture) => {
        fixture.proposal[Symbol('rawPrivateNote')] = 'SECRET';
      },
    },
    {
      label: 'accessor extra',
      mutate: (fixture) => {
        Object.defineProperty(fixture.proposal, 'rawPrivateNote', {
          enumerable: true,
          get() {
            throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
          },
        });
      },
    },
    {
      label: 'descriptor trap',
      mutate: (fixture) => {
        fixture.proposal = new Proxy(fixture.proposal, {
          ownKeys(target) {
            return [...Reflect.ownKeys(target), 'rawPrivateNote'];
          },
          getOwnPropertyDescriptor(target, key) {
            if (key === 'rawPrivateNote') {
              throw new Error('Authorization_Bearer_SECRET\nprivate-token=SECRET');
            }
            return Reflect.getOwnPropertyDescriptor(target, key);
          },
        });
      },
    },
  ];

  for (const invalid of cases) {
    const fixture = strictProposalFixture();
    invalid.mutate(fixture);
    await assert.rejects(
      () => validateStrictProposal(module, fixture),
      assertSafeProposalContractError,
      invalid.label,
    );
  }
});

test('public generation sanitizes a mutable forged auth ProposalGenerationError before any reader', async () => {
  const module = requireGenerationModule();
  const injected = forgedProposalGenerationError(module, 'auth', false);
  const harness = makeGenerationHarness({ authError: injected });

  await assert.rejects(
    () => harness.generator({ manifestRef: manifestId }),
    assertSafeGenerationError('CODEX_AUTH_MODE_FORBIDDEN', injected),
  );
  assert.deepEqual(harness.events, ['login']);
});

test('public generation sanitizes a subclassed forged raw-manifest ProposalGenerationError without opening artifacts', async () => {
  const module = requireGenerationModule();
  const injected = forgedProposalGenerationError(module, 'raw-manifest', true);
  const harness = makeGenerationHarness({ rawManifestError: injected });

  await assert.rejects(
    () => harness.generator({ manifestRef: manifestId }),
    assertSafeGenerationError('INPUT_MANIFEST_INVALID', injected),
  );
  assert.deepEqual(harness.events, ['login', 'raw-manifest']);
});

test('public generation sanitizes a mutable forged model ProposalGenerationError after manifest validation', async () => {
  const module = requireGenerationModule();
  const injected = forgedProposalGenerationError(module, 'model', false);
  const harness = makeGenerationHarness({ modelError: injected });

  await assert.rejects(
    () => harness.generator({ manifestRef: manifestId }),
    assertSafeGenerationError('INTERNAL_GENERATION_FAILURE', injected),
  );
  assert.deepEqual(harness.events, ['login', 'raw-manifest', 'model']);
  assert.equal(harness.events.includes('artifact'), false);
});

test('validateProposalCitations sanitizes a subclassed forged artifact ProposalGenerationError', async () => {
  const module = requireGenerationModule();
  const injected = forgedProposalGenerationError(module, 'artifact', true);
  let artifactReads = 0;

  await assert.rejects(
    () => module.validateProposalCitations({
      rawManifest: validRawManifest(),
      proposal: validProposal(),
      readArtifact: async () => {
        artifactReads += 1;
        throw injected;
      },
    }),
    assertSafeGenerationError('RAW_ARTIFACT_INVALID', injected),
  );
  assert.equal(artifactReads, 1);
});
