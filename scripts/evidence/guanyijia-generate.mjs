import { createHash } from 'node:crypto';

const ERROR_MESSAGES = Object.freeze({
  AUTHORIZATION_REQUIRED: 'An exact ChatGPT generation authorization is required.',
  API_PROVIDER_CREDENTIAL_FORBIDDEN: 'API or provider credentials are forbidden for proposal generation.',
  CODEX_CONFIGURATION_REJECTED: 'The controlled Codex configuration is invalid.',
  CODEX_VERSION_UNSUPPORTED: 'The controlled Codex version is unsupported.',
  CODEX_AUTH_MODE_FORBIDDEN: 'A signed-in ChatGPT Codex session is required.',
  CODEX_EXECUTION_FAILED: 'The controlled Codex execution failed safely.',
  CAPTURE_OUTPUT_REQUIRED: 'A RawSourceSnapshotManifest is required; CAPTURE_REQUEST is metadata only.',
  INPUT_MANIFEST_INVALID: 'The raw source snapshot manifest is invalid.',
  RAW_ARTIFACT_INVALID: 'The cited raw artifact is invalid.',
  PROPOSAL_SCHEMA_INVALID: 'The generated proposal schema is invalid.',
  PROPOSAL_CITATION_INVALID: 'A proposal citation does not exactly match admitted raw evidence.',
  INTERNAL_GENERATION_FAILURE: 'Proposal generation failed safely.',
});

const FORBIDDEN_ENVIRONMENT_KEYS = Object.freeze([
  'OPENAI_API_KEY',
  'CODEX_API_KEY',
  'CODEX_ACCESS_TOKEN',
  'OPENAI_BASE_URL',
  'AZURE_OPENAI_API_KEY',
  'AZURE_OPENAI_ENDPOINT',
]);

export class ProposalGenerationError extends Error {
  constructor(code) {
    const safeCode = Object.hasOwn(ERROR_MESSAGES, code) ? code : 'INTERNAL_GENERATION_FAILURE';
    super(`Proposal generation blocked [${safeCode}]: ${ERROR_MESSAGES[safeCode]}`);
    this.name = 'ProposalGenerationError';
    this.code = safeCode;
  }
}

function fail(code) {
  throw new ProposalGenerationError(code);
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function sameKeys(value, expectedKeys) {
  if (!isRecord(value)) return false;
  try {
    const prototype = Object.getPrototypeOf(value);
    if (prototype !== Object.prototype && prototype !== null) return false;
    if (Object.getOwnPropertySymbols(value).length !== 0) return false;
    const descriptors = Object.getOwnPropertyDescriptors(value);
    const actual = Object.keys(descriptors).sort();
    const expected = [...expectedKeys].sort();
    return actual.length === expected.length
      && actual.every((key, index) => key === expected[index]
        && descriptors[key].enumerable
        && Object.hasOwn(descriptors[key], 'value'));
  } catch {
    return false;
  }
}

function sameStrictKeys(value, expectedKeys) {
  return sameKeys(value, expectedKeys);
}

function nonBlankString(value) {
  return typeof value === 'string' && value.length > 0;
}

const MAX_TEXT_LENGTH = 65536;
const MAX_STATEMENTS_PER_SOURCE = 200;
const MAX_EVIDENCE_PER_STATEMENT = 200;
const MAX_AFFECTED_REFS_PER_STATEMENT = 200;
const MAX_RESOLVED_CITATIONS = 3 * MAX_STATEMENTS_PER_SOURCE * MAX_EVIDENCE_PER_STATEMENT;

function strictNonBlankString(value) {
  if (typeof value !== 'string') return false;
  const length = unicodeCodePointLength(value);
  return length > 0 && length <= MAX_TEXT_LENGTH;
}

function strictText(value) {
  return typeof value === 'string' && unicodeCodePointLength(value) <= MAX_TEXT_LENGTH;
}

function unicodeCodePointLength(value) {
  return typeof value === 'string' ? Array.from(value).length : Number.POSITIVE_INFINITY;
}

function strictArray(value, minimum, maximum) {
  if (!Array.isArray(value)) return false;
  try {
    if (Object.getPrototypeOf(value) !== Array.prototype || Object.getOwnPropertySymbols(value).length !== 0) return false;
    const descriptors = Object.getOwnPropertyDescriptors(value);
    if (!Object.hasOwn(descriptors, 'length') || !Object.hasOwn(descriptors.length, 'value')) return false;
    const length = descriptors.length.value;
    if (!Number.isSafeInteger(length) || length < minimum || length > maximum) return false;
    const keys = Object.keys(descriptors).sort();
    const expectedKeys = [...Array(length).keys()].map(String).concat('length').sort();
    return keys.length === expectedKeys.length
      && keys.every((key, index) => key === expectedKeys[index]
        && (key === 'length'
          ? !descriptors[key].enumerable && Object.hasOwn(descriptors[key], 'value')
          : descriptors[key].enumerable && Object.hasOwn(descriptors[key], 'value')));
  } catch {
    return false;
  }
}

function isSha256(value) {
  return typeof value === 'string' && /^sha256:[a-f0-9]{64}$/.test(value);
}

function canonicalJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function canonicalCitationMaterialDigest(resolvedCitations) {
  return sha256(`${canonicalJson(resolvedCitations)}\n`);
}

function canonicalPromptInputPacket({
  storyKey,
  runId,
  admittedSources,
  resolvedCitations,
  promptTemplateSha256,
  proposalSchemaSha256,
  citationMaterialSha256,
}) {
  // This packet is deliberately a distinct prompt domain, not a second name
  // for citation material. A future composer must retain or version this
  // packet rather than binding its prompt with an untrusted digest equality.
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

function canonicalPromptInputPacketDigest(input) {
  return sha256(`${canonicalJson(canonicalPromptInputPacket(input))}\n`);
}

function equalJson(left, right) {
  return canonicalJson(left) === canonicalJson(right);
}

function cloneValidated(value) {
  try {
    return structuredClone(value);
  } catch {
    fail('PROPOSAL_CITATION_INVALID');
  }
}

function rebuildCitationError(error) {
  try {
    if (error instanceof ProposalGenerationError && Object.hasOwn(ERROR_MESSAGES, error.code)) {
      return new ProposalGenerationError(error.code);
    }
  } catch {
    // Hostile proxy errors are untrusted input, not public diagnostics.
  }
  return new ProposalGenerationError('PROPOSAL_CITATION_INVALID');
}

function rebuildLegacyStageError(error, fallbackCode) {
  const sanitized = rebuildCitationError(error);
  if (sanitized.code === 'CAPTURE_OUTPUT_REQUIRED') return sanitized;
  return new ProposalGenerationError(fallbackCode);
}

function validFileLinesLocator(locator) {
  return sameKeys(locator, ['kind', 'path', 'startLine', 'endLine'])
    && locator.kind === 'FILE_LINES'
    && nonBlankString(locator.path)
    && Number.isSafeInteger(locator.startLine)
    && Number.isSafeInteger(locator.endLine)
    && locator.startLine > 0
    && locator.endLine >= locator.startLine;
}

const STRICT_SOURCE_IDS = Object.freeze([
  'guanyijia_mysql',
  'guanyijia_github',
  'guanyijia_official_docs',
]);

const STRICT_SECTIONS = new Set([
  'OVERVIEW', 'GOAL', 'OBJECT', 'ACTIVITY', 'FIELD',
  'RELATION', 'METRIC', 'QUESTION', 'UNRESOLVED',
]);

const STRICT_SEMANTIC_KINDS = new Set([
  'ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC',
  'HIERARCHY', 'RULE', 'ALIAS', 'TIME_RULE',
  'GAP', 'PENDING_ASSET', 'EXCLUSION',
]);

const STRICT_EVIDENCE_STATUSES = new Set(['FACT', 'INFERENCE', 'GAP', 'CONFLICT']);
const STRICT_PROVENANCES = new Set(['OBSERVED', 'INFERRED']);
// These are reviewed trust pins for committed assets. The pure validator never
// reads the filesystem; a future CLI composition root must verify the actual
// prompt/binary bytes against these pins before constructing its envelope.
const PINNED_CODEX_BINARY_SHA256 = 'sha256:494eb6397b32448a71c9b893178cfa3b3307da6e65860d17ca3c41646df57b0a';
const EXPECTED_PROMPT_TEMPLATE_SHA256 = 'sha256:c32a5cfb1340bf8fd6bfb262e3d396352e1be3da6f156e7ea9e4f65c09ea4691';
const EXPECTED_PROPOSAL_SCHEMA_SHA256 = 'sha256:41e1eaaf6c8279fc835181629dbebc92550ae6e7a384d12e0cd11a09107a0bfb';

function positiveInteger(value) {
  return Number.isSafeInteger(value) && value > 0;
}

function strictDataProperty(value, key) {
  if (!isRecord(value)) return undefined;
  try {
    const descriptor = Object.getOwnPropertyDescriptor(value, key);
    return descriptor && Object.hasOwn(descriptor, 'value') ? descriptor.value : undefined;
  } catch {
    return undefined;
  }
}

function validStrictFileLinesLocator(locator) {
  return sameStrictKeys(locator, ['kind', 'path', 'startLine', 'endLine'])
    && locator.kind === 'FILE_LINES'
    && strictNonBlankString(locator.path)
    && positiveInteger(locator.startLine)
    && positiveInteger(locator.endLine)
    && locator.endLine >= locator.startLine;
}

function validStrictObservedLocator(locator) {
  if (!isRecord(locator)) return false;
  let kind;
  try {
    const kindDescriptor = Object.getOwnPropertyDescriptor(locator, 'kind');
    if (!kindDescriptor || !Object.hasOwn(kindDescriptor, 'value')) return false;
    kind = kindDescriptor.value;
  } catch {
    return false;
  }
  if (kind === 'FILE_LINES') return validStrictFileLinesLocator(locator);
  if (kind === 'SOURCE_SYMBOL') {
    return sameStrictKeys(locator, ['kind', 'path', 'symbol', 'startLine', 'endLine'])
      && strictNonBlankString(locator.path)
      && strictNonBlankString(locator.symbol)
      && positiveInteger(locator.startLine)
      && positiveInteger(locator.endLine)
      && locator.endLine >= locator.startLine;
  }
  if (kind === 'SQL') {
    return sameStrictKeys(locator, ['kind', 'schema', 'object', 'symbol'])
      && strictNonBlankString(locator.schema)
      && strictNonBlankString(locator.object)
      && strictNonBlankString(locator.symbol);
  }
  if (kind === 'DOCUMENT_SECTION') {
    const keys = Object.hasOwn(locator, 'page')
      ? ['kind', 'document', 'section', 'page']
      : ['kind', 'document', 'section'];
    return sameStrictKeys(locator, keys)
      && strictNonBlankString(locator.document)
      && strictNonBlankString(locator.section)
      && (!Object.hasOwn(locator, 'page') || positiveInteger(locator.page));
  }
  return false;
}

function validStrictValue(value) {
  return sameStrictKeys(value, ['normalized', 'text'])
    && strictNonBlankString(value.normalized)
    && strictNonBlankString(value.text);
}

function validStrictAffectedObjectRef(ref) {
  const scope = strictDataProperty(ref, 'scope');
  if (!['RESULT', 'CATALOG', 'DRAFT'].includes(scope)) return false;
  const expectedKeys = scope === 'RESULT'
    ? ['scope', 'documentVersion', 'kind', 'objectId']
    : scope === 'CATALOG'
      ? ['scope', 'catalogId', 'kind', 'objectId']
      : ['scope', 'draftId', 'kind', 'objectId'];
  if (Object.hasOwn(ref, 'ownerId')) expectedKeys.push('ownerId');
  if (!sameStrictKeys(ref, expectedKeys)
    || !STRICT_SEMANTIC_KINDS.has(ref.kind)
    || ['GAP', 'PENDING_ASSET', 'EXCLUSION'].includes(ref.kind)
    || !strictNonBlankString(ref.objectId)
    || (Object.hasOwn(ref, 'ownerId') && !strictNonBlankString(ref.ownerId))) {
    return false;
  }
  if (scope === 'RESULT') {
    return strictNonBlankString(ref.documentVersion);
  }
  if (scope === 'CATALOG') {
    return strictNonBlankString(ref.catalogId);
  }
  return strictNonBlankString(ref.draftId);
}

function validateStrictAdmittedSources(admittedSources) {
  if (!strictArray(admittedSources, STRICT_SOURCE_IDS.length, STRICT_SOURCE_IDS.length)) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  const bySourceId = new Map();
  for (const [index, source] of admittedSources.entries()) {
    if (!sameStrictKeys(source, ['sourceId', 'snapshotId', 'manifestRef', 'manifestSha256', 'artifactCatalogSha256'])
      || source.sourceId !== STRICT_SOURCE_IDS[index]
      || !strictNonBlankString(source.snapshotId)
      || !strictNonBlankString(source.manifestRef)
      || !isSha256(source.manifestSha256)
      || !isSha256(source.artifactCatalogSha256)
      || bySourceId.has(source.sourceId)) {
      fail('PROPOSAL_CITATION_INVALID');
    }
    bySourceId.set(source.sourceId, source);
  }
  return bySourceId;
}

function validateStrictResolvedCitations(resolvedCitations, admittedBySourceId) {
  if (!strictArray(resolvedCitations, 0, MAX_RESOLVED_CITATIONS)) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  const byEvidenceRef = new Map();
  const resolvedTupleIdentities = new Set();
  for (const resolved of resolvedCitations) {
    if (!sameStrictKeys(resolved, [
      'evidenceKind', 'evidenceRef', 'sourceId', 'snapshotId',
      'artifactRef', 'artifactSha256', 'locator', 'excerpt',
    ])
      || resolved.evidenceKind !== 'OBSERVED'
      || !strictNonBlankString(resolved.evidenceRef)
      || !strictNonBlankString(resolved.sourceId)
      || !strictNonBlankString(resolved.snapshotId)
      || !strictNonBlankString(resolved.artifactRef)
      || !isSha256(resolved.artifactSha256)
      || !validStrictObservedLocator(resolved.locator)
      || !strictText(resolved.excerpt)
      || byEvidenceRef.has(resolved.evidenceRef)) {
      fail('PROPOSAL_CITATION_INVALID');
    }
    const admitted = admittedBySourceId.get(resolved.sourceId);
    if (!admitted || admitted.snapshotId !== resolved.snapshotId) fail('PROPOSAL_CITATION_INVALID');
    const tupleIdentity = canonicalJson([
      resolved.sourceId,
      resolved.snapshotId,
      resolved.artifactRef,
      resolved.artifactSha256,
      resolved.locator,
      resolved.excerpt,
    ]);
    if (resolvedTupleIdentities.has(tupleIdentity)) fail('PROPOSAL_CITATION_INVALID');
    resolvedTupleIdentities.add(tupleIdentity);
    byEvidenceRef.set(resolved.evidenceRef, resolved);
  }
  return byEvidenceRef;
}

function strictObservedCitationMatchesResolution(citation, resolved) {
  return citation.evidenceKind === resolved.evidenceKind
    && citation.evidenceRef === resolved.evidenceRef
    && citation.sourceId === resolved.sourceId
    && citation.snapshotId === resolved.snapshotId
    && citation.artifactRef === resolved.artifactRef
    && citation.artifactSha256 === resolved.artifactSha256
    && citation.excerpt === resolved.excerpt
    && equalJson(citation.locator, resolved.locator);
}

function validateStrictObservedCitation(citation, sourceProposal, admitted, evidenceRefs, resolvedByEvidenceRef, usedResolvedRefs) {
  if (!sameStrictKeys(citation, [
    'evidenceKind', 'evidenceRef', 'title', 'excerpt', 'sourceId', 'snapshotId',
    'artifactRef', 'artifactSha256', 'locator',
  ])
    || citation.evidenceKind !== 'OBSERVED'
    || !strictNonBlankString(citation.evidenceRef)
    || !strictNonBlankString(citation.title)
    || !strictText(citation.excerpt)
    || citation.sourceId !== sourceProposal.sourceId
    || citation.snapshotId !== sourceProposal.snapshotId
    || citation.sourceId !== admitted.sourceId
    || citation.snapshotId !== admitted.snapshotId
    || !strictNonBlankString(citation.artifactRef)
    || !isSha256(citation.artifactSha256)
    || !validStrictObservedLocator(citation.locator)
    || evidenceRefs.has(citation.evidenceRef)) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  const resolved = resolvedByEvidenceRef.get(citation.evidenceRef);
  if (!resolved || usedResolvedRefs.has(citation.evidenceRef)
    || !strictObservedCitationMatchesResolution(citation, resolved)) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  evidenceRefs.add(citation.evidenceRef);
  usedResolvedRefs.add(citation.evidenceRef);
}

function validateStrictGeneratedTargetCitation(citation, evidenceRefs) {
  if (!sameStrictKeys(citation, ['evidenceKind', 'evidenceRef', 'title', 'excerpt', 'locator'])
    || citation.evidenceKind !== 'GENERATED_TARGET'
    || !strictNonBlankString(citation.evidenceRef)
    || !strictNonBlankString(citation.title)
    || !strictNonBlankString(citation.excerpt)
    || !sameStrictKeys(citation.locator, ['kind', 'confirmationStatus'])
    || citation.locator.kind !== 'GENERATED_TARGET'
    || citation.locator.confirmationStatus !== 'PENDING_HUMAN_CONFIRMATION'
    || evidenceRefs.has(citation.evidenceRef)) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  evidenceRefs.add(citation.evidenceRef);
}

function validateStrictStatement(statement, sourceProposal, admitted, proposalIds, evidenceRefs, resolvedByEvidenceRef, usedResolvedRefs) {
  if (!sameStrictKeys(statement, [
    'proposalId', 'section', 'semanticKind', 'stableCode', 'label', 'value',
    'statement', 'evidenceStatus', 'provenance', 'affectedObjectRefs', 'evidence',
  ])
    || !strictNonBlankString(statement.proposalId)
    || proposalIds.has(statement.proposalId)
    || !STRICT_SECTIONS.has(statement.section)
    || !STRICT_SEMANTIC_KINDS.has(statement.semanticKind)
    || !strictNonBlankString(statement.stableCode)
    || !strictNonBlankString(statement.label)
    || !validStrictValue(statement.value)
    || !strictNonBlankString(statement.statement)
    || !STRICT_EVIDENCE_STATUSES.has(statement.evidenceStatus)
    || !STRICT_PROVENANCES.has(statement.provenance)
    || !strictArray(statement.affectedObjectRefs, 0, MAX_AFFECTED_REFS_PER_STATEMENT)
    || !statement.affectedObjectRefs.every(validStrictAffectedObjectRef)
    || !strictArray(statement.evidence, 1, MAX_EVIDENCE_PER_STATEMENT)) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  proposalIds.add(statement.proposalId);

  const observed = statement.evidence.every((citation) => strictDataProperty(citation, 'evidenceKind') === 'OBSERVED');
  const targets = statement.evidence.every((citation) => strictDataProperty(citation, 'evidenceKind') === 'GENERATED_TARGET');
  if (!observed && !targets) fail('PROPOSAL_CITATION_INVALID');

  if (targets) {
    if (!['GAP', 'PENDING_ASSET'].includes(statement.semanticKind)
      || statement.evidenceStatus !== 'GAP'
      || statement.provenance !== 'INFERRED') {
      fail('PROPOSAL_CITATION_INVALID');
    }
    statement.evidence.forEach((citation) => validateStrictGeneratedTargetCitation(citation, evidenceRefs));
    return;
  }

  if (statement.evidenceStatus === 'FACT' && statement.provenance !== 'OBSERVED') {
    fail('PROPOSAL_CITATION_INVALID');
  }
  statement.evidence.forEach((citation) => validateStrictObservedCitation(
    citation,
    sourceProposal,
    admitted,
    evidenceRefs,
    resolvedByEvidenceRef,
    usedResolvedRefs,
  ));
}

function deepFreezeValidated(value) {
  if (value === null || typeof value !== 'object' || Object.isFrozen(value)) return value;
  for (const nested of Object.values(value)) deepFreezeValidated(nested);
  return Object.freeze(value);
}

/**
 * Admits the complete, no-I/O material shared by pure proposal validation and
 * the controlled executor. This is deliberately private: callers can only use
 * the public pure validator or the executor's authorization-gated operation.
 */
function admitStrictInvocationMaterial(invocationEnvelope, admittedSources, resolvedCitations) {
  if (!sameStrictKeys(invocationEnvelope, [
    'schemaVersion', 'kind', 'storyKey', 'runId', 'executor', 'inputs',
    'rawManifestDigest', 'citationMaterialSha256', 'promptTemplateSha256',
    'promptInputSha256', 'proposalSchemaSha256', 'envelopeSha256',
  ])
    || invocationEnvelope.schemaVersion !== 1
    || invocationEnvelope.kind !== 'GUANYIJIA_PROPOSAL_INVOCATION'
    || invocationEnvelope.storyKey !== 'guanyijia-five-source-v2'
    || !strictNonBlankString(invocationEnvelope.runId)
    || !sameStrictKeys(invocationEnvelope.executor, [
      'client', 'cliVersion', 'codexBinarySha256', 'requestedModel',
      'requestedReasoningEffort', 'requiredProvider', 'requiredAuthMode',
      'sandbox', 'approvalPolicy', 'sessionPersistence', 'toolPolicy', 'attemptLimit',
    ])
    || invocationEnvelope.executor.client !== 'CODEX_CLI'
    || invocationEnvelope.executor.cliVersion !== 'codex-cli 0.148.0-alpha.15'
    || invocationEnvelope.executor.codexBinarySha256 !== PINNED_CODEX_BINARY_SHA256
    || invocationEnvelope.executor.requestedModel !== 'gpt-5.6-luna'
    || invocationEnvelope.executor.requestedReasoningEffort !== 'xhigh'
    || invocationEnvelope.executor.requiredProvider !== 'openai'
    || invocationEnvelope.executor.requiredAuthMode !== 'CHATGPT_SESSION'
    || invocationEnvelope.executor.sandbox !== 'READ_ONLY'
    || invocationEnvelope.executor.approvalPolicy !== 'NEVER'
    || invocationEnvelope.executor.sessionPersistence !== 'EPHEMERAL'
    || invocationEnvelope.executor.toolPolicy !== 'NO_TOOLS'
    || invocationEnvelope.executor.attemptLimit !== 1
    || !strictArray(invocationEnvelope.inputs, STRICT_SOURCE_IDS.length, STRICT_SOURCE_IDS.length)
    || !isSha256(invocationEnvelope.rawManifestDigest)
    || !isSha256(invocationEnvelope.citationMaterialSha256)
    || !isSha256(invocationEnvelope.promptTemplateSha256)
    || !isSha256(invocationEnvelope.promptInputSha256)
    || !isSha256(invocationEnvelope.proposalSchemaSha256)
    || !isSha256(invocationEnvelope.envelopeSha256)) {
    fail('PROPOSAL_CITATION_INVALID');
  }

  const admittedBySourceId = validateStrictAdmittedSources(admittedSources);
  validateStrictResolvedCitations(resolvedCitations, admittedBySourceId);
  validateStrictAdmittedSources(invocationEnvelope.inputs);
  if (!equalJson(invocationEnvelope.inputs, admittedSources)
    || invocationEnvelope.rawManifestDigest !== sha256(`${canonicalJson(admittedSources)}\n`)
    || invocationEnvelope.citationMaterialSha256 !== canonicalCitationMaterialDigest(resolvedCitations)
    || invocationEnvelope.promptInputSha256 !== canonicalPromptInputPacketDigest({
      storyKey: invocationEnvelope.storyKey,
      runId: invocationEnvelope.runId,
      admittedSources,
      resolvedCitations,
      promptTemplateSha256: invocationEnvelope.promptTemplateSha256,
      proposalSchemaSha256: invocationEnvelope.proposalSchemaSha256,
      citationMaterialSha256: invocationEnvelope.citationMaterialSha256,
    })
    || invocationEnvelope.promptTemplateSha256 !== EXPECTED_PROMPT_TEMPLATE_SHA256
    || invocationEnvelope.proposalSchemaSha256 !== EXPECTED_PROPOSAL_SCHEMA_SHA256) {
    fail('PROPOSAL_CITATION_INVALID');
  }

  const { envelopeSha256, ...envelopeBody } = invocationEnvelope;
  if (sha256(`${canonicalJson(envelopeBody)}\n`) !== envelopeSha256) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  return deepFreezeValidated(cloneValidated({
    invocationEnvelope,
    admittedSources,
    resolvedCitations,
  }));
}

function validateStrictInvocationEnvelope(invocationEnvelope, proposal, admittedSources, resolvedCitations) {
  const material = admitStrictInvocationMaterial(invocationEnvelope, admittedSources, resolvedCitations);
  if (invocationEnvelope.storyKey !== proposal.storyKey
    || invocationEnvelope.runId !== proposal.runId
    || proposal.invocationEnvelopeSha256 !== invocationEnvelope.envelopeSha256) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  return material;
}

function validateStrictPureProposal(input) {
  const { proposal, admittedSources, resolvedCitations, invocationEnvelope } = input;
  const admittedBySourceId = validateStrictAdmittedSources(admittedSources);
  const resolvedByEvidenceRef = validateStrictResolvedCitations(resolvedCitations, admittedBySourceId);

  if (!sameStrictKeys(proposal, [
    'schemaVersion', 'kind', 'storyKey', 'runId', 'invocationEnvelopeSha256', 'sourceProposals',
  ])
    || proposal.schemaVersion !== 1
    || proposal.kind !== 'GUANYIJIA_EVIDENCE_PROPOSAL'
    || proposal.storyKey !== 'guanyijia-five-source-v2'
    || !strictNonBlankString(proposal.runId)
    || !isSha256(proposal.invocationEnvelopeSha256)
    || !strictArray(proposal.sourceProposals, STRICT_SOURCE_IDS.length, STRICT_SOURCE_IDS.length)) {
    fail('PROPOSAL_CITATION_INVALID');
  }
  validateStrictInvocationEnvelope(invocationEnvelope, proposal, admittedSources, resolvedCitations);

  const proposalIds = new Set();
  const evidenceRefs = new Set();
  const usedResolvedRefs = new Set();
  for (const [index, sourceProposal] of proposal.sourceProposals.entries()) {
    const sourceId = STRICT_SOURCE_IDS[index];
    const admitted = admittedBySourceId.get(sourceId);
    if (!sameStrictKeys(sourceProposal, ['sourceId', 'snapshotId', 'statements'])
      || sourceProposal.sourceId !== sourceId
      || sourceProposal.snapshotId !== admitted.snapshotId
      || !strictArray(sourceProposal.statements, 1, MAX_STATEMENTS_PER_SOURCE)) {
      fail('PROPOSAL_CITATION_INVALID');
    }
    sourceProposal.statements.forEach((statement) => validateStrictStatement(
      statement,
      sourceProposal,
      admitted,
      proposalIds,
      evidenceRefs,
      resolvedByEvidenceRef,
      usedResolvedRefs,
    ));
  }
  if (usedResolvedRefs.size !== resolvedByEvidenceRef.size) fail('PROPOSAL_CITATION_INVALID');
  return cloneValidated(proposal);
}

function validateRawManifest(input) {
  if (strictDataProperty(input, 'kind') === 'CAPTURE_REQUEST') fail('CAPTURE_OUTPUT_REQUIRED');
  if (!sameKeys(input, ['schemaVersion', 'manifestId', 'source', 'capturedAt', 'artifacts', 'manifestSha256'])
    || input.schemaVersion !== 1
    || !nonBlankString(input.manifestId)
    || !nonBlankString(input.capturedAt)
    || !isSha256(input.manifestSha256)
    || !sameKeys(input.source, ['sourceId', 'sourceName', 'sourceClass', 'snapshotId', 'authority', 'lineageStatus', 'upstreamSourceIds'])
    || !nonBlankString(input.source.sourceId)
    || !nonBlankString(input.source.sourceName)
    || input.source.sourceClass !== 'REAL'
    || !nonBlankString(input.source.snapshotId)
    || !nonBlankString(input.source.authority)
    || input.source.lineageStatus !== 'ROOT'
    || !strictArray(input.source.upstreamSourceIds, 0, 0)
    || !strictArray(input.artifacts, 1, MAX_RESOLVED_CITATIONS)) {
    fail('INPUT_MANIFEST_INVALID');
  }

  const seenArtifactRefs = new Set();
  for (const artifact of input.artifacts) {
    if (!sameKeys(artifact, ['artifactRef', 'sha256', 'locator'])
      || !nonBlankString(artifact.artifactRef)
      || !isSha256(artifact.sha256)
      || !validFileLinesLocator(artifact.locator)
      || seenArtifactRefs.has(artifact.artifactRef)) {
      fail('INPUT_MANIFEST_INVALID');
    }
    seenArtifactRefs.add(artifact.artifactRef);
  }

  const { manifestSha256, ...body } = input;
  if (sha256(`${canonicalJson(body)}\n`) !== manifestSha256) fail('INPUT_MANIFEST_INVALID');
  return input;
}

function validateProposalShape(proposal, manifest) {
  if (!sameKeys(proposal, ['schemaVersion', 'kind', 'sourceManifestId', 'statements'])
    || proposal.schemaVersion !== 1
    || proposal.kind !== 'PROPOSAL'
    || proposal.sourceManifestId !== manifest.manifestId
    || !strictArray(proposal.statements, 1, MAX_RESOLVED_CITATIONS)) {
    fail('PROPOSAL_CITATION_INVALID');
  }

  const seenStatementIds = new Set();
  const seenObservedCitationIdentities = new Set();
  const seenGeneratedEvidenceRefs = new Set();
  for (const statement of proposal.statements) {
    const observedStatement = sameKeys(statement, ['statementId', 'text', 'citations']);
    const generatedTargetStatement = sameKeys(statement, [
      'statementId', 'text', 'semanticKind', 'evidenceStatus', 'provenance', 'citations',
    ]);
    if ((!observedStatement && !generatedTargetStatement)
      || !nonBlankString(statement.statementId)
      || !nonBlankString(statement.text)
      || !strictArray(statement.citations, 1, MAX_EVIDENCE_PER_STATEMENT)
      || seenStatementIds.has(statement.statementId)) {
      fail('PROPOSAL_CITATION_INVALID');
    }
    seenStatementIds.add(statement.statementId);

    if (observedStatement) {
      for (const citation of statement.citations) {
        if (!sameKeys(citation, ['evidenceKind', 'artifactRef', 'locator', 'excerpt'])
          || citation.evidenceKind !== 'OBSERVED'
          || !nonBlankString(citation.artifactRef)
          || !validFileLinesLocator(citation.locator)
          || typeof citation.excerpt !== 'string') {
          fail('PROPOSAL_CITATION_INVALID');
        }
        const identity = canonicalJson([citation.artifactRef, citation.locator]);
        if (seenObservedCitationIdentities.has(identity)) fail('PROPOSAL_CITATION_INVALID');
        seenObservedCitationIdentities.add(identity);
      }
      continue;
    }

    if (!['GAP', 'PENDING_ASSET'].includes(statement.semanticKind)
      || statement.evidenceStatus !== 'GAP'
      || statement.provenance !== 'INFERRED') {
      fail('PROPOSAL_CITATION_INVALID');
    }
    for (const citation of statement.citations) {
      if (!sameKeys(citation, ['evidenceKind', 'evidenceRef', 'title', 'excerpt', 'locator'])
        || citation.evidenceKind !== 'GENERATED_TARGET'
        || !nonBlankString(citation.evidenceRef)
        || !nonBlankString(citation.title)
        || !nonBlankString(citation.excerpt)
        || !sameKeys(citation.locator, ['kind', 'confirmationStatus'])
        || citation.locator.kind !== 'GENERATED_TARGET'
        || citation.locator.confirmationStatus !== 'PENDING_HUMAN_CONFIRMATION') {
        fail('PROPOSAL_CITATION_INVALID');
      }
      if (seenGeneratedEvidenceRefs.has(citation.evidenceRef)) fail('PROPOSAL_CITATION_INVALID');
      seenGeneratedEvidenceRefs.add(citation.evidenceRef);
    }
  }
}

function extractFileLines(bytes, locator) {
  let text;
  try {
    text = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes);
  } catch {
    fail('RAW_ARTIFACT_INVALID');
  }
  const lines = text.split('\n');
  if (locator.endLine > lines.length) fail('PROPOSAL_CITATION_INVALID');
  return lines.slice(locator.startLine - 1, locator.endLine).join('\n');
}

async function callAdapter(code, operation) {
  try {
    return await operation();
  } catch {
    fail(code);
  }
}

function environmentSnapshot(dependencies) {
  try {
    const dependencyDescriptor = Object.getOwnPropertyDescriptor(dependencies, 'env');
    if (!dependencyDescriptor || !Object.hasOwn(dependencyDescriptor, 'value') || !isRecord(dependencyDescriptor.value)) return undefined;
    const prototype = Object.getPrototypeOf(dependencyDescriptor.value);
    if (prototype !== Object.prototype && prototype !== null) return undefined;
    if (Object.getOwnPropertySymbols(dependencyDescriptor.value).length !== 0) return undefined;
    const descriptors = Object.getOwnPropertyDescriptors(dependencyDescriptor.value);
    const snapshot = Object.create(null);
    for (const [key, descriptor] of Object.entries(descriptors)) {
      if (!descriptor.enumerable || !Object.hasOwn(descriptor, 'value')) return undefined;
      snapshot[key] = descriptor.value;
    }
    return Object.freeze(snapshot);
  } catch {
    return undefined;
  }
}

function rejectCredentialEnvironment(env) {
  if (!isRecord(env)) fail('INTERNAL_GENERATION_FAILURE');
  if (FORBIDDEN_ENVIRONMENT_KEYS.some((key) => Object.hasOwn(env, key))) {
    fail('API_PROVIDER_CREDENTIAL_FORBIDDEN');
  }
}

function validateChatGptLogin(status) {
  if (!isRecord(status) || status.status !== 'LOGGED_IN' || status.provider !== 'CHATGPT') {
    fail('CODEX_AUTH_MODE_FORBIDDEN');
  }
}

const CONTROLLED_CODEX_VERSION = 'codex-cli 0.148.0-alpha.15';
const CONTROLLED_CODEX_DISABLED_FEATURES = Object.freeze([
  'apps', 'artifact', 'browser_use', 'browser_use_external',
  'browser_use_full_cdp_access', 'code_mode', 'code_mode_host', 'computer_use',
  'enable_mcp_apps', 'goals', 'hooks', 'image_generation', 'in_app_browser',
  'mcp_2026_07_28', 'multi_agent', 'multi_agent_v2', 'plugin_sharing', 'plugins',
  'recommended_plugins', 'remote_plugin', 'request_permissions_tool', 'shell_tool',
  'skill_mcp_dependency_install', 'skill_search', 'standalone_web_search',
  'tool_call_mcp_elicitation', 'unified_exec', 'view_image', 'workspace_dependencies',
]);
const CONTROLLED_CODEX_LIMITS = Object.freeze({
  probeTimeoutMs: 10_000,
  execTimeoutMs: 600_000,
  maxStdinBytes: 8 * 1024 * 1024,
  maxLastMessageBytes: 8 * 1024 * 1024,
  maxStdoutBytes: 2 * 1024 * 1024,
  maxStderrBytes: 64 * 1024,
  maxJsonlEvents: 10_000,
});

function isAbsolutePath(value) {
  return strictNonBlankString(value) && value.startsWith('/') && !value.includes('\u0000');
}

function canonicalAbsolutePath(value) {
  if (!isAbsolutePath(value) || value === '/') return undefined;
  const components = value.slice(1).split('/');
  if (components.some((component) => component.length === 0 || component === '.' || component === '..')) {
    return undefined;
  }
  const canonical = `/${components.join('/')}`;
  return canonical === value ? canonical : undefined;
}

function nonWhitespaceString(value) {
  return strictNonBlankString(value) && value.trim().length > 0;
}

function stableDataProperty(value, key) {
  const descriptorValue = strictDataProperty(value, key);
  try {
    return Reflect.get(value, key) === descriptorValue ? descriptorValue : undefined;
  } catch {
    return undefined;
  }
}

function exactByteEqual(left, right) {
  if (!(left instanceof Uint8Array) || !(right instanceof Uint8Array) || left.byteLength !== right.byteLength) {
    return false;
  }
  for (let index = 0; index < left.byteLength; index += 1) {
    if (left[index] !== right[index]) return false;
  }
  return true;
}

function copiedBytes(value) {
  if (!(value instanceof Uint8Array) || Object.getPrototypeOf(value) !== Uint8Array.prototype) return undefined;
  return new Uint8Array(value);
}

function executorError(error, fallbackCode = 'INTERNAL_GENERATION_FAILURE') {
  try {
    if (error instanceof ProposalGenerationError && Object.hasOwn(ERROR_MESSAGES, error.code)) {
      return new ProposalGenerationError(error.code);
    }
  } catch {
    // An adapter throwable is never an error payload for this public seam.
  }
  return new ProposalGenerationError(fallbackCode);
}

async function callControlledPort(code, operation) {
  try {
    return await operation();
  } catch {
    throw new ProposalGenerationError(code);
  }
}

function controlledChildEnvironment(snapshot) {
  const required = ['CODEX_HOME', 'PATH', 'TMPDIR'];
  if (!isRecord(snapshot) || required.some((key) => !nonWhitespaceString(snapshot[key]))) {
    fail('CODEX_CONFIGURATION_REJECTED');
  }
  const child = {};
  for (const key of required) child[key] = snapshot[key];
  for (const key of ['LANG', 'LC_ALL']) {
    if (!Object.hasOwn(snapshot, key)) continue;
    if (!nonWhitespaceString(snapshot[key])) fail('CODEX_CONFIGURATION_REJECTED');
    child[key] = snapshot[key];
  }
  return Object.freeze(child);
}

function validateExecutorDependencies(dependencies) {
  if (!sameStrictKeys(dependencies, ['env', 'executionAdmission', 'codexSession'])) {
    fail('INTERNAL_GENERATION_FAILURE');
  }
  const executionAdmission = stableDataProperty(dependencies, 'executionAdmission');
  const codexSession = stableDataProperty(dependencies, 'codexSession');
  if (!sameStrictKeys(executionAdmission, ['admit'])
    || !sameStrictKeys(codexSession, ['run'])) fail('INTERNAL_GENERATION_FAILURE');
  const admit = stableDataProperty(executionAdmission, 'admit');
  const run = stableDataProperty(codexSession, 'run');
  if (typeof admit !== 'function' || typeof run !== 'function') fail('INTERNAL_GENERATION_FAILURE');
  const env = environmentSnapshot(dependencies);
  if (!env) fail('INTERNAL_GENERATION_FAILURE');
  return Object.freeze({
    env,
    admit,
    run,
  });
}

function snapshotExecutionAdmission(result) {
  if (!sameStrictKeys(result, [
    'executablePath', 'codexBinarySha256', 'promptTemplateBytes',
    'proposalSchemaPath', 'proposalSchemaBytes', 'workingDirectory', 'lastMessagePath',
  ])) fail('CODEX_CONFIGURATION_REJECTED');
  const workingDirectory = strictDataProperty(result, 'workingDirectory');
  if (!sameStrictKeys(workingDirectory, ['path', 'mode', 'empty'])) fail('CODEX_CONFIGURATION_REJECTED');
  try {
    const snapshot = structuredClone(result);
    if (!sameStrictKeys(snapshot, [
      'executablePath', 'codexBinarySha256', 'promptTemplateBytes',
      'proposalSchemaPath', 'proposalSchemaBytes', 'workingDirectory', 'lastMessagePath',
    ]) || !sameStrictKeys(snapshot.workingDirectory, ['path', 'mode', 'empty'])) {
      fail('CODEX_CONFIGURATION_REJECTED');
    }
    return snapshot;
  } catch {
    fail('CODEX_CONFIGURATION_REJECTED');
  }
}

function validateExecutionAdmission(result, material) {
  const snapshot = snapshotExecutionAdmission(result);
  const executablePath = canonicalAbsolutePath(snapshot.executablePath);
  const proposalSchemaPath = canonicalAbsolutePath(snapshot.proposalSchemaPath);
  const lastMessagePath = canonicalAbsolutePath(snapshot.lastMessagePath);
  const workingDirectory = canonicalAbsolutePath(snapshot.workingDirectory.path);
  if (!executablePath
    || snapshot.codexBinarySha256 !== PINNED_CODEX_BINARY_SHA256
    || !proposalSchemaPath
    || !lastMessagePath
    || !workingDirectory
    || snapshot.workingDirectory.mode !== 0o700
    || snapshot.workingDirectory.empty !== true
    || proposalSchemaPath === lastMessagePath
    || workingDirectory === proposalSchemaPath
    || workingDirectory === lastMessagePath) fail('CODEX_CONFIGURATION_REJECTED');

  const promptTemplateBytes = copiedBytes(snapshot.promptTemplateBytes);
  const proposalSchemaBytes = copiedBytes(snapshot.proposalSchemaBytes);
  if (!promptTemplateBytes || !proposalSchemaBytes
    || promptTemplateBytes.byteLength === 0
    || sha256(promptTemplateBytes) !== EXPECTED_PROMPT_TEMPLATE_SHA256
    || sha256(proposalSchemaBytes) !== EXPECTED_PROPOSAL_SCHEMA_SHA256
    || material.invocationEnvelope.promptTemplateSha256 !== EXPECTED_PROMPT_TEMPLATE_SHA256
    || material.invocationEnvelope.proposalSchemaSha256 !== EXPECTED_PROPOSAL_SCHEMA_SHA256) {
    fail('CODEX_CONFIGURATION_REJECTED');
  }

  let instructions;
  try {
    instructions = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(promptTemplateBytes);
  } catch {
    fail('CODEX_CONFIGURATION_REJECTED');
  }
  if (!strictNonBlankString(instructions) || !instructions.endsWith('\n')) {
    fail('CODEX_CONFIGURATION_REJECTED');
  }
  return Object.freeze({
    executablePath,
    proposalSchemaPath,
    lastMessagePath,
    workingDirectory,
    instructions,
  });
}

function controlledStdin(material, instructions) {
  const envelope = material.invocationEnvelope;
  const evidencePacket = canonicalPromptInputPacket({
    storyKey: envelope.storyKey,
    runId: envelope.runId,
    admittedSources: material.admittedSources,
    resolvedCitations: material.resolvedCitations,
    promptTemplateSha256: envelope.promptTemplateSha256,
    proposalSchemaSha256: envelope.proposalSchemaSha256,
    citationMaterialSha256: envelope.citationMaterialSha256,
  });
  const bytes = new TextEncoder().encode(`${canonicalJson({
    schemaVersion: 1,
    kind: 'GUANYIJIA_CODEX_STDIN',
    instructions,
    invocationEnvelopeSha256: envelope.envelopeSha256,
    untrustedEvidenceData: evidencePacket,
  })}\n`);
  if (bytes.byteLength > CONTROLLED_CODEX_LIMITS.maxStdinBytes) fail('CODEX_CONFIGURATION_REJECTED');
  return bytes;
}

function childInvocation(admission, env, argv, stdin, timeoutMs) {
  const portStdin = copiedBytes(stdin);
  if (!portStdin) fail('CODEX_EXECUTION_FAILED');
  return Object.freeze({
    executablePath: admission.executablePath,
    argv: Object.freeze(argv),
    cwd: admission.workingDirectory,
    env,
    shell: false,
    stdio: Object.freeze(['pipe', 'pipe', 'pipe']),
    stdin: portStdin,
    limits: Object.freeze({
      timeoutMs,
      maxStdinBytes: CONTROLLED_CODEX_LIMITS.maxStdinBytes,
      maxLastMessageBytes: CONTROLLED_CODEX_LIMITS.maxLastMessageBytes,
      maxStdoutBytes: CONTROLLED_CODEX_LIMITS.maxStdoutBytes,
      maxStderrBytes: CONTROLLED_CODEX_LIMITS.maxStderrBytes,
      maxJsonlEvents: CONTROLLED_CODEX_LIMITS.maxJsonlEvents,
    }),
  });
}

function snapshotPortResult(result, expectedKeys) {
  if (!sameStrictKeys(result, expectedKeys)) return undefined;
  try {
    const snapshot = structuredClone(result);
    return sameStrictKeys(snapshot, expectedKeys) ? snapshot : undefined;
  } catch {
    return undefined;
  }
}

function validProbeResult(result, expectedStdout) {
  const snapshot = snapshotPortResult(result, ['exitCode', 'signal', 'stdout', 'stderr']);
  return snapshot !== undefined
    && snapshot.exitCode === 0
    && snapshot.signal === null
    && exactByteEqual(snapshot.stdout, expectedStdout)
    && snapshot.stderr instanceof Uint8Array
    && snapshot.stderr.byteLength === 0;
}

function controlledExecArgv(admission) {
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
    '-C', admission.workingDirectory,
    '--skip-git-repo-check',
    '--json',
    '--color', 'never',
    '--output-schema', admission.proposalSchemaPath,
    '--output-last-message', admission.lastMessagePath,
    ...CONTROLLED_CODEX_DISABLED_FEATURES.flatMap((name) => ['--disable', name]),
    '-c', 'tools.web_search=false',
    '-',
  ];
}

function validExecutionResult(result) {
  const snapshot = snapshotPortResult(result, [
    'exitCode', 'signal', 'stdout', 'stderr', 'lastMessageBytes', 'audit',
  ]);
  if (!snapshot
    || snapshot.exitCode !== 0
    || snapshot.signal !== null
    || !(snapshot.stdout instanceof Uint8Array)
    || snapshot.stdout.byteLength > CONTROLLED_CODEX_LIMITS.maxStdoutBytes
    || !(snapshot.stderr instanceof Uint8Array)
    || snapshot.stderr.byteLength > CONTROLLED_CODEX_LIMITS.maxStderrBytes
    || !(snapshot.lastMessageBytes instanceof Uint8Array)
    || snapshot.lastMessageBytes.byteLength === 0
    || snapshot.lastMessageBytes.byteLength > CONTROLLED_CODEX_LIMITS.maxLastMessageBytes
    || !sameStrictKeys(snapshot.audit, ['completionEventCount', 'toolCallCount'])
    || snapshot.audit.completionEventCount !== 1
    || snapshot.audit.toolCallCount !== 0) return undefined;
  return snapshot;
}

function parseLastMessage(bytes) {
  const copied = copiedBytes(bytes);
  if (!copied) fail('PROPOSAL_SCHEMA_INVALID');
  let text;
  try {
    text = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(copied);
  } catch {
    fail('PROPOSAL_SCHEMA_INVALID');
  }
  try {
    return JSON.parse(text);
  } catch {
    fail('PROPOSAL_SCHEMA_INVALID');
  }
}

function authorizeControlledExecution(authorization, material) {
  if (!sameStrictKeys(authorization, ['kind', 'flag', 'expectedGenerationInputSha256'])
    || authorization.kind !== 'CHATGPT_GENERATION_AUTHORIZATION'
    || authorization.flag !== '--authorize-chatgpt-generation'
    || !isSha256(authorization.expectedGenerationInputSha256)
    || authorization.expectedGenerationInputSha256 !== material.invocationEnvelope.promptInputSha256) {
    fail('AUTHORIZATION_REQUIRED');
  }
}

/**
 * Creates a fake-port-only controlled executor. It never imports a process,
 * filesystem, network, model, or raw-reader implementation. The returned
 * callable also exposes `.run` as the same operation for ergonomic injection.
 */
function createControlledCodexExecutorUnsafe(dependencies) {
  const ports = validateExecutorDependencies(dependencies);

  const execute = async (request) => {
    try {
      if (!sameStrictKeys(request, ['authorization', 'invocationEnvelope', 'admittedSources', 'resolvedCitations'])) {
        fail('AUTHORIZATION_REQUIRED');
      }

      // This literal token is checked before environment or port admission.
      const authorization = request.authorization;
      if (!sameStrictKeys(authorization, ['kind', 'flag', 'expectedGenerationInputSha256'])
        || authorization.kind !== 'CHATGPT_GENERATION_AUTHORIZATION'
        || authorization.flag !== '--authorize-chatgpt-generation'
        || !isSha256(authorization.expectedGenerationInputSha256)) {
        fail('AUTHORIZATION_REQUIRED');
      }
      rejectCredentialEnvironment(ports.env);
      const childEnv = controlledChildEnvironment(ports.env);
      const material = admitStrictInvocationMaterial(
        request.invocationEnvelope,
        request.admittedSources,
        request.resolvedCitations,
      );
      authorizeControlledExecution(authorization, material);

      const admitted = await callControlledPort('CODEX_CONFIGURATION_REJECTED', () => ports.admit({
        runId: material.invocationEnvelope.runId,
        expectedCodexBinarySha256: PINNED_CODEX_BINARY_SHA256,
        expectedPromptTemplateSha256: EXPECTED_PROMPT_TEMPLATE_SHA256,
        expectedProposalSchemaSha256: EXPECTED_PROPOSAL_SCHEMA_SHA256,
      }));
      const commandAdmission = validateExecutionAdmission(admitted, material);
      const stdin = controlledStdin(material, commandAdmission.instructions);

      const version = await callControlledPort('CODEX_VERSION_UNSUPPORTED', () => ports.run(childInvocation(
        commandAdmission,
        childEnv,
        ['--version'],
        new Uint8Array(),
        CONTROLLED_CODEX_LIMITS.probeTimeoutMs,
      )));
      if (!validProbeResult(version, new TextEncoder().encode(`${CONTROLLED_CODEX_VERSION}\n`))) {
        fail('CODEX_VERSION_UNSUPPORTED');
      }

      const preAuth = await callControlledPort('CODEX_AUTH_MODE_FORBIDDEN', () => ports.run(childInvocation(
        commandAdmission,
        childEnv,
        ['login', 'status'],
        new Uint8Array(),
        CONTROLLED_CODEX_LIMITS.probeTimeoutMs,
      )));
      if (!validProbeResult(preAuth, new TextEncoder().encode('Logged in using ChatGPT\n'))) {
        fail('CODEX_AUTH_MODE_FORBIDDEN');
      }

      const executionInvocation = childInvocation(
        commandAdmission,
        childEnv,
        controlledExecArgv(commandAdmission),
        stdin,
        CONTROLLED_CODEX_LIMITS.execTimeoutMs,
      );
      const execution = await callControlledPort('CODEX_EXECUTION_FAILED', () => ports.run(executionInvocation));
      if (sha256(executionInvocation.stdin) !== sha256(stdin)) fail('CODEX_EXECUTION_FAILED');
      const trustedExecution = validExecutionResult(execution);
      if (!trustedExecution) fail('CODEX_EXECUTION_FAILED');
      const proposal = parseLastMessage(trustedExecution.lastMessageBytes);
      let validatedProposal;
      try {
        validatedProposal = validateStrictPureProposal({
          proposal,
          admittedSources: material.admittedSources,
          resolvedCitations: material.resolvedCitations,
          invocationEnvelope: material.invocationEnvelope,
        });
      } catch {
        fail('PROPOSAL_CITATION_INVALID');
      }

      const postAuth = await callControlledPort('CODEX_AUTH_MODE_FORBIDDEN', () => ports.run(childInvocation(
        commandAdmission,
        childEnv,
        ['login', 'status'],
        new Uint8Array(),
        CONTROLLED_CODEX_LIMITS.probeTimeoutMs,
      )));
      if (!validProbeResult(postAuth, new TextEncoder().encode('Logged in using ChatGPT\n'))) {
        fail('CODEX_AUTH_MODE_FORBIDDEN');
      }

      const proposalClone = cloneValidated(validatedProposal);
      return Object.freeze({
        proposal: proposalClone,
        proposalSha256: sha256(`${canonicalJson(proposalClone)}\n`),
        execution: Object.freeze({
          attemptCount: 1,
          preAuthMode: 'CHATGPT_SESSION',
          postAuthMode: 'CHATGPT_SESSION',
          completionEventCount: 1,
          toolCallCount: 0,
          stdinSha256: sha256(stdin),
        }),
      });
    } catch (error) {
      throw executorError(error);
    }
  };

  Object.defineProperty(execute, 'run', { value: execute });
  return Object.freeze(execute);
}

export function createControlledCodexExecutor(dependencies) {
  try {
    return createControlledCodexExecutorUnsafe(dependencies);
  } catch (error) {
    throw executorError(error);
  }
}

/**
 * Creates the narrow, adapter-injected Task 3 generation operation. Importing
 * this module neither consults ambient process state nor starts a model.
 */
export function createProposalGeneration(dependencies) {
  if (!isRecord(dependencies)
    || !isRecord(dependencies.auth)
    || typeof dependencies.auth.loginStatus !== 'function'
    || !isRecord(dependencies.rawManifestReader)
    || typeof dependencies.rawManifestReader.read !== 'function'
    || !isRecord(dependencies.artifactReader)
    || typeof dependencies.artifactReader.read !== 'function'
    || !isRecord(dependencies.modelRunner)
    || typeof dependencies.modelRunner.generate !== 'function') {
    fail('INTERNAL_GENERATION_FAILURE');
  }
  const capturedEnvironment = environmentSnapshot(dependencies);

  return async function generate(request) {
    if (!isRecord(request) || !sameKeys(request, ['manifestRef']) || !nonBlankString(request.manifestRef)) {
      fail('INPUT_MANIFEST_INVALID');
    }

    rejectCredentialEnvironment(capturedEnvironment);
    validateChatGptLogin(await callAdapter('CODEX_AUTH_MODE_FORBIDDEN', () => dependencies.auth.loginStatus()));

    const rawManifest = validateRawManifest(await callAdapter(
      'INPUT_MANIFEST_INVALID',
      () => dependencies.rawManifestReader.read({ manifestRef: request.manifestRef }),
    ));
    const proposal = await callAdapter('INTERNAL_GENERATION_FAILURE', () => dependencies.modelRunner.generate({
      manifestRef: request.manifestRef,
      rawManifest,
    }));
    return validateProposalCitations({
      rawManifest,
      proposal,
      readArtifact: ({ artifactRef }) => dependencies.artifactReader.read({ artifactRef, rawManifest }),
    });
  };
}

/**
 * Deterministically compares observed proposal citations with bytes admitted
 * by one raw source snapshot. The caller supplies the only effectful read.
 */
async function validateLegacyProposalCitations(input) {
  const { rawManifest, proposal, readArtifact } = input;
  let manifest;
  try {
    manifest = validateRawManifest(rawManifest);
  } catch (error) {
    throw rebuildLegacyStageError(error, 'INPUT_MANIFEST_INVALID');
  }
  if (typeof readArtifact !== 'function') fail('RAW_ARTIFACT_INVALID');
  let trustedManifest;
  let trustedProposal;
  try {
    validateProposalShape(proposal, manifest);
    trustedManifest = cloneValidated(manifest);
    trustedProposal = cloneValidated(proposal);
  } catch (error) {
    throw rebuildLegacyStageError(error, 'PROPOSAL_CITATION_INVALID');
  }

  const artifactsByRef = new Map(trustedManifest.artifacts.map((artifact) => [artifact.artifactRef, artifact]));
  for (const statement of trustedProposal.statements) {
    for (const citation of statement.citations) {
      if (citation.evidenceKind !== 'OBSERVED') continue;
      const artifact = artifactsByRef.get(citation.artifactRef);
      if (!artifact || !equalJson(citation.locator, artifact.locator)) fail('PROPOSAL_CITATION_INVALID');
      const bytes = await callAdapter('RAW_ARTIFACT_INVALID', () => readArtifact({ artifactRef: artifact.artifactRef }));
      if (!(bytes instanceof Uint8Array) || sha256(bytes) !== artifact.sha256) fail('RAW_ARTIFACT_INVALID');
      if (extractFileLines(bytes, artifact.locator) !== citation.excerpt) fail('PROPOSAL_CITATION_INVALID');
    }
  }
  return trustedProposal;
}

/**
 * Validates either the historical one-manifest seam or the strict, pure
 * proposal/resolution contract. The pure form deliberately has no reader or
 * adapter: its resolved tuples are already independently produced upstream.
 */
export async function validateProposalCitations(input) {
  try {
    if (sameStrictKeys(input, ['proposal', 'admittedSources', 'resolvedCitations', 'invocationEnvelope'])) {
      return validateStrictPureProposal(input);
    }
    if (sameKeys(input, ['rawManifest', 'proposal', 'readArtifact'])) {
      return await validateLegacyProposalCitations(input);
    }
  } catch (error) {
    throw rebuildCitationError(error);
  }
  fail('PROPOSAL_CITATION_INVALID');
}
