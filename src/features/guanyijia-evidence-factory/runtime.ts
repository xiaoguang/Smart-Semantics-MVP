import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import type {
  EvidenceFactory,
  EvidenceFactoryDependencies,
  EvidenceFragment,
  EvidenceTraceLink,
  FrozenDemoEvidenceBundle,
  GeneratedTargetEvidenceFragment,
  ObservedEvidenceFragment,
  ObservedEvidenceLocator,
  PublicAffectedObjectRef,
  PublicArtifactProvenance,
  PublicationReceipt,
  PublishedArtifactMembership,
  PublishedFragmentApproval,
  PublishedRawManifestAttestation,
  PublicSourceDocumentAssertion,
  PublicSourceDocumentBlock,
  PublicSourceDocumentCompilation,
  PublicSourceReadSummary,
  PublicStructuredBlockValue,
  Sha256,
  SourceSnapshotIdentity,
  TrustedPublicationPin,
  TrustedPublicationRegistry,
} from './types.ts';

const sourceClasses = ['REAL', 'DEMO_POLICY', 'DERIVED'] as const;
const sourceAuthorities = ['PRIMARY', 'CORROBORATING', 'AUXILIARY', 'DERIVED'] as const;
const lineageStatuses = ['ROOT', 'DERIVED_VALID'] as const;
const modelingSections = [
  'OVERVIEW', 'GOAL', 'OBJECT', 'ACTIVITY', 'FIELD', 'RELATION', 'METRIC', 'QUESTION', 'UNRESOLVED',
] as const;
const blockEvidenceStatuses = ['FACT', 'INFERENCE', 'GAP', 'CONFLICT'] as const;
const assertionProvenances = ['OBSERVED', 'INFERRED', 'USER_CONFIRMED'] as const;
const publicSemanticKinds = [
  'ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC', 'HIERARCHY', 'RULE', 'ALIAS', 'TIME_RULE',
  'GAP', 'PENDING_ASSET', 'EXCLUSION',
] as const;
const objectRefKinds = ['ENTITY', 'EVENT', 'FIELD', 'RELATION', 'DIMENSION', 'METRIC', 'HIERARCHY', 'RULE', 'ALIAS', 'TIME_RULE'] as const;
const generationKinds = ['CAPTURE', 'GENERATE', 'FREEZE'] as const;
const generationStatuses = ['SUCCEEDED', 'FAILED'] as const;

type JsonRecord = Record<string, unknown>;

function snapshotError(message: string): never {
  throw new Error(`演示证据快照不可用：${message}`);
}

function clone<T>(value: T): T {
  return structuredClone(value);
}

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requireRecord(value: unknown, label: string): JsonRecord {
  if (!isRecord(value)) snapshotError(`${label} 必须是对象`);
  return value;
}

function requireArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) snapshotError(`${label} 必须是数组`);
  return value;
}

function requireOnlyKeys(record: JsonRecord, label: string, allowed: readonly string[]) {
  for (const key of Object.keys(record)) {
    if (!allowed.includes(key)) snapshotError(`${label} 包含未知公开字段：${key}`);
  }
}

function requireNonBlank(value: unknown, label: string): string {
  if (typeof value !== 'string' || !value.trim()) snapshotError(`${label} 不能为空`);
  return value;
}

function requireNonNegativeInteger(value: unknown, label: string): number {
  if (!Number.isInteger(value) || (value as number) < 0) snapshotError(`${label} 必须是非负整数`);
  return value as number;
}

function requirePositiveInteger(value: unknown, label: string): number {
  if (!Number.isInteger(value) || (value as number) < 1) snapshotError(`${label} 必须是正整数`);
  return value as number;
}

function requireEnum<T extends string>(value: unknown, allowed: readonly T[], label: string): T {
  if (typeof value !== 'string' || !allowed.includes(value as T)) {
    snapshotError(`${label} 不受支持：${String(value)}`);
  }
  return value as T;
}

function requireStringArray(value: unknown, label: string): string[] {
  return requireArray(value, label).map((entry, index) => requireNonBlank(entry, `${label}[${index}]`));
}

function requireDistinct(values: readonly string[], label: string) {
  if (new Set(values).size !== values.length) snapshotError(`${label} 不能重复`);
}

function requireSha256(value: unknown, label: string): Sha256 {
  const digest = requireNonBlank(value, label);
  if (!/^sha256:[a-f0-9]{64}$/.test(digest)) snapshotError(`${label} 必须是 SHA-256 摘要`);
  return digest as Sha256;
}

function sameJson(left: unknown, right: unknown) {
  return canonicalModelingJson(left) === canonicalModelingJson(right);
}

function validateBundleDigest(bundle: JsonRecord) {
  const { bundleSha256: _bundleSha256, ...unsignedBundle } = bundle;
  const expected = `sha256:${sha256HexSync(canonicalModelingJson(unsignedBundle))}`;
  if (bundle.bundleSha256 !== expected) snapshotError('Bundle 校验失败');
  return expected as Sha256;
}

function canonicalSha256(value: unknown): Sha256 {
  return `sha256:${sha256HexSync(canonicalModelingJson(value))}` as Sha256;
}

function utf8Sha256(value: string): Sha256 {
  return `sha256:${sha256HexSync(value)}` as Sha256;
}

function requireStrictlySorted(values: readonly string[], label: string) {
  requireDistinct(values, label);
  for (let index = 1; index < values.length; index += 1) {
    if (values[index - 1] >= values[index]) snapshotError(`${label} 必须按稳定键升序排列`);
  }
}

function requireTimestamp(value: unknown, label: string): string {
  const timestamp = requireNonBlank(value, label);
  if (Number.isNaN(Date.parse(timestamp))) snapshotError(`${label} 必须是有效时间`);
  return timestamp;
}

function validateSourceIdentity(value: unknown): SourceSnapshotIdentity {
  const identity = requireRecord(value, '来源身份');
  requireOnlyKeys(identity, '来源身份', [
    'sourceId', 'sourceName', 'sourceClass', 'snapshotId', 'authority', 'lineageStatus', 'upstreamSourceIds',
  ]);
  const sourceId = requireNonBlank(identity.sourceId, '来源 sourceId');
  const sourceName = requireNonBlank(identity.sourceName, `来源 ${sourceId} 的 sourceName`);
  const snapshotId = requireNonBlank(identity.snapshotId, `来源 ${sourceId} 的 snapshotId`);
  const sourceClass = requireEnum(identity.sourceClass, sourceClasses, `来源 ${sourceId} 的 sourceClass`);
  const authority = requireEnum(identity.authority, sourceAuthorities, `来源 ${sourceId} 的 authority`);
  const lineageStatus = requireEnum(identity.lineageStatus, lineageStatuses, `来源 ${sourceId} 的 lineageStatus`);
  const upstreamSourceIds = requireStringArray(identity.upstreamSourceIds, `来源 ${sourceId} 的上游来源`);
  requireDistinct(upstreamSourceIds, `来源 ${sourceId} 的上游来源`);
  if (lineageStatus === 'ROOT' && upstreamSourceIds.length) snapshotError(`ROOT 来源 ${sourceId} 不能声明上游来源`);
  return { sourceId, sourceName, sourceClass, snapshotId, authority, lineageStatus, upstreamSourceIds };
}

function validateReadSummary(value: unknown, sourceId: string): PublicSourceReadSummary {
  const summary = requireRecord(value, `编译结果 ${sourceId} 的 readSummary`);
  requireOnlyKeys(summary, `编译结果 ${sourceId} 的 readSummary`, [
    'summary', 'objectCount', 'evidenceCount', 'objectCounts', 'versionRef', 'warnings',
  ]);
  const objectCounts = requireRecord(summary.objectCounts, `编译结果 ${sourceId} 的 objectCounts`);
  const publicObjectCounts: Record<string, number> = {};
  for (const [kind, count] of Object.entries(objectCounts)) {
    requireNonBlank(kind, `编译结果 ${sourceId} 的 objectCounts key`);
    publicObjectCounts[kind] = requireNonNegativeInteger(count, `编译结果 ${sourceId} 的 objectCounts.${kind}`);
  }
  return {
    summary: requireNonBlank(summary.summary, `编译结果 ${sourceId} 的 summary`),
    objectCount: requireNonNegativeInteger(summary.objectCount, `编译结果 ${sourceId} 的 objectCount`),
    evidenceCount: requireNonNegativeInteger(summary.evidenceCount, `编译结果 ${sourceId} 的 evidenceCount`),
    objectCounts: publicObjectCounts,
    versionRef: requireNonBlank(summary.versionRef, `编译结果 ${sourceId} 的 versionRef`),
    warnings: requireStringArray(summary.warnings, `编译结果 ${sourceId} 的 warnings`),
  };
}

function validateSections(value: unknown, sourceId: string): PublicSourceDocumentCompilation['sections'] {
  const sections = requireRecord(value, `编译结果 ${sourceId} 的 sections`);
  requireOnlyKeys(sections, `编译结果 ${sourceId} 的 sections`, modelingSections);
  const publicSections = {} as PublicSourceDocumentCompilation['sections'];
  for (const section of modelingSections) {
    if (!(section in sections) || typeof sections[section] !== 'string') {
      snapshotError(`编译结果 ${sourceId} 缺少章节：${section}`);
    }
    publicSections[section] = sections[section] as string;
  }
  return publicSections;
}

function validatePublicValue(value: unknown, blockId: string): PublicStructuredBlockValue {
  const record = requireRecord(value, `block ${blockId} 的 value`);
  requireOnlyKeys(record, `block ${blockId} 的 value`, ['normalized', 'text']);
  return {
    normalized: requireNonBlank(record.normalized, `block ${blockId} 的 value.normalized`),
    text: requireNonBlank(record.text, `block ${blockId} 的 value.text`),
  };
}

function validateAffectedObjectRef(value: unknown, blockId: string, index: number): PublicAffectedObjectRef {
  const ref = requireRecord(value, `block ${blockId} 的 affectedObjectRefs[${index}]`);
  const scope = requireEnum(ref.scope, ['RESULT', 'CATALOG', 'DRAFT'] as const, `block ${blockId} 的对象 scope`);
  const kind = requireEnum(ref.kind, objectRefKinds, `block ${blockId} 的对象 kind`);
  const objectId = requireNonBlank(ref.objectId, `block ${blockId} 的对象 objectId`);
  const ownerId = ref.ownerId === undefined ? undefined : requireNonBlank(ref.ownerId, `block ${blockId} 的对象 ownerId`);
  if (scope === 'RESULT') {
    requireOnlyKeys(ref, `block ${blockId} 的 RESULT 对象`, ['scope', 'documentVersion', 'kind', 'objectId', 'ownerId']);
    return { scope, documentVersion: requireNonBlank(ref.documentVersion, `block ${blockId} 的 documentVersion`), kind, objectId, ...(ownerId ? { ownerId } : {}) };
  }
  if (scope === 'CATALOG') {
    requireOnlyKeys(ref, `block ${blockId} 的 CATALOG 对象`, ['scope', 'catalogId', 'kind', 'objectId', 'ownerId']);
    return { scope, catalogId: requireNonBlank(ref.catalogId, `block ${blockId} 的 catalogId`), kind, objectId, ...(ownerId ? { ownerId } : {}) };
  }
  requireOnlyKeys(ref, `block ${blockId} 的 DRAFT 对象`, ['scope', 'draftId', 'kind', 'objectId', 'ownerId']);
  return { scope, draftId: requireNonBlank(ref.draftId, `block ${blockId} 的 draftId`), kind, objectId, ...(ownerId ? { ownerId } : {}) };
}

function validateBlock(value: unknown, sourceId: string, index: number): PublicSourceDocumentBlock {
  const block = requireRecord(value, `编译结果 ${sourceId} 的 block[${index}]`);
  requireOnlyKeys(block, `编译结果 ${sourceId} 的 block[${index}]`, [
    'blockId', 'section', 'semanticKind', 'stableCode', 'label', 'value', 'evidenceStatus', 'evidenceRefs', 'affectedObjectRefs',
  ]);
  const blockId = requireNonBlank(block.blockId, `编译结果 ${sourceId} 的 blockId`);
  return {
    blockId,
    section: requireEnum(block.section, modelingSections, `block ${blockId} 的 section`),
    semanticKind: requireEnum(block.semanticKind, publicSemanticKinds, `block ${blockId} 的 semanticKind`),
    stableCode: requireNonBlank(block.stableCode, `block ${blockId} 的 stableCode`),
    label: requireNonBlank(block.label, `block ${blockId} 的 label`),
    value: validatePublicValue(block.value, blockId),
    evidenceStatus: requireEnum(block.evidenceStatus, blockEvidenceStatuses, `block ${blockId} 的 evidenceStatus`),
    evidenceRefs: requireStringArray(block.evidenceRefs, `block ${blockId} 的 evidenceRefs`),
    affectedObjectRefs: requireArray(block.affectedObjectRefs, `block ${blockId} 的 affectedObjectRefs`)
      .map((ref, affectedIndex) => validateAffectedObjectRef(ref, blockId, affectedIndex)),
  };
}

function validateAssertion(value: unknown, sourceId: string, index: number): PublicSourceDocumentAssertion {
  const assertion = requireRecord(value, `编译结果 ${sourceId} 的 assertion[${index}]`);
  requireOnlyKeys(assertion, `编译结果 ${sourceId} 的 assertion[${index}]`, [
    'assertionId', 'section', 'statement', 'provenance', 'evidenceRefs',
  ]);
  const assertionId = requireNonBlank(assertion.assertionId, `编译结果 ${sourceId} 的 assertionId`);
  return {
    assertionId,
    section: requireEnum(assertion.section, modelingSections, `assertion ${assertionId} 的 section`),
    statement: requireNonBlank(assertion.statement, `assertion ${assertionId} 的 statement`),
    provenance: requireEnum(assertion.provenance, assertionProvenances, `assertion ${assertionId} 的 provenance`),
    evidenceRefs: requireStringArray(assertion.evidenceRefs, `assertion ${assertionId} 的 evidenceRefs`),
  };
}

function validateObservedLocator(value: unknown, evidenceRef: string): ObservedEvidenceLocator {
  const locator = requireRecord(value, `证据片段 ${evidenceRef} 的 locator`);
  const kind = requireEnum(locator.kind, ['SQL', 'FILE_LINES', 'DOCUMENT_SECTION', 'SOURCE_SYMBOL'] as const,
    `证据片段 ${evidenceRef} 的 locator.kind`);
  if (kind === 'SQL') {
    requireOnlyKeys(locator, `证据片段 ${evidenceRef} 的 SQL locator`, ['kind', 'schema', 'object', 'symbol']);
    return { kind, schema: requireNonBlank(locator.schema, `证据片段 ${evidenceRef} 的 SQL schema`), object: requireNonBlank(locator.object, `证据片段 ${evidenceRef} 的 SQL object`), symbol: requireNonBlank(locator.symbol, `证据片段 ${evidenceRef} 的 SQL symbol`) };
  }
  if (kind === 'FILE_LINES') {
    requireOnlyKeys(locator, `证据片段 ${evidenceRef} 的文件 locator`, ['kind', 'path', 'startLine', 'endLine']);
    const startLine = requirePositiveInteger(locator.startLine, `证据片段 ${evidenceRef} 的起始行`);
    const endLine = requirePositiveInteger(locator.endLine, `证据片段 ${evidenceRef} 的结束行`);
    if (endLine < startLine) snapshotError(`证据片段 ${evidenceRef} 的行范围无效`);
    return { kind, path: requireNonBlank(locator.path, `证据片段 ${evidenceRef} 的文件路径`), startLine, endLine };
  }
  if (kind === 'DOCUMENT_SECTION') {
    requireOnlyKeys(locator, `证据片段 ${evidenceRef} 的文档 locator`, ['kind', 'document', 'section', 'page']);
    const page = locator.page === undefined ? undefined : requirePositiveInteger(locator.page, `证据片段 ${evidenceRef} 的页码`);
    return { kind, document: requireNonBlank(locator.document, `证据片段 ${evidenceRef} 的文档`), section: requireNonBlank(locator.section, `证据片段 ${evidenceRef} 的文档章节`), ...(page ? { page } : {}) };
  }
  requireOnlyKeys(locator, `证据片段 ${evidenceRef} 的源码 locator`, ['kind', 'path', 'symbol', 'startLine', 'endLine']);
  const startLine = requirePositiveInteger(locator.startLine, `证据片段 ${evidenceRef} 的起始行`);
  const endLine = requirePositiveInteger(locator.endLine, `证据片段 ${evidenceRef} 的结束行`);
  if (endLine < startLine) snapshotError(`证据片段 ${evidenceRef} 的行范围无效`);
  return { kind, path: requireNonBlank(locator.path, `证据片段 ${evidenceRef} 的源码路径`), symbol: requireNonBlank(locator.symbol, `证据片段 ${evidenceRef} 的源码符号`), startLine, endLine };
}

function validateExcerpt(value: unknown, evidenceRef: string) {
  const excerpt = requireNonBlank(value, `证据片段 ${evidenceRef} 的摘录`);
  if (new TextEncoder().encode(excerpt).byteLength > 32 * 1024) snapshotError(`证据片段 ${evidenceRef} 的摘录超过公开上限`);
  if (/^\s*[\[{][\s\S]*"(?:rawRow|tenantId)"\s*:/i.test(excerpt)) {
    snapshotError(`证据片段 ${evidenceRef} 包含显式原始敏感载荷`);
  }
  return excerpt;
}

function validateFragment(value: unknown, identities: Map<string, SourceSnapshotIdentity>): EvidenceFragment {
  const fragment = requireRecord(value, '证据片段');
  const evidenceRef = requireNonBlank(fragment.evidenceRef, '证据片段 evidenceRef');
  const evidenceKind = requireEnum(fragment.evidenceKind, ['OBSERVED', 'GENERATED_TARGET'] as const, `证据片段 ${evidenceRef} 的 evidenceKind`);
  if (evidenceKind === 'GENERATED_TARGET') {
    requireOnlyKeys(fragment, `GENERATED_TARGET 证据片段 ${evidenceRef}`, ['evidenceRef', 'evidenceKind', 'title', 'excerpt', 'locator']);
    const locator = requireRecord(fragment.locator, `GENERATED_TARGET ${evidenceRef} 的 locator`);
    requireOnlyKeys(locator, `GENERATED_TARGET ${evidenceRef} 的 locator`, ['kind', 'confirmationStatus']);
    if (locator.kind !== 'GENERATED_TARGET' || locator.confirmationStatus !== 'PENDING_HUMAN_CONFIRMATION') {
      snapshotError(`GENERATED_TARGET 证据片段 ${evidenceRef} 必须标记待人工确认`);
    }
    const target: GeneratedTargetEvidenceFragment = { evidenceRef, evidenceKind, title: requireNonBlank(fragment.title, `证据片段 ${evidenceRef} 的标题`), excerpt: validateExcerpt(fragment.excerpt, evidenceRef), locator: { kind: 'GENERATED_TARGET', confirmationStatus: 'PENDING_HUMAN_CONFIRMATION' } };
    return target;
  }
  requireOnlyKeys(fragment, `观察到的证据片段 ${evidenceRef}`, ['evidenceRef', 'sourceId', 'snapshotId', 'evidenceKind', 'title', 'excerpt', 'artifactRef', 'locator']);
  const sourceId = requireNonBlank(fragment.sourceId, `证据片段 ${evidenceRef} 的 sourceId`);
  const identity = identities.get(sourceId);
  if (!identity) snapshotError(`证据片段 ${evidenceRef} 引用了不存在的来源：${sourceId}`);
  const snapshotId = requireNonBlank(fragment.snapshotId, `证据片段 ${evidenceRef} 的 snapshotId`);
  if (snapshotId !== identity.snapshotId) snapshotError(`证据片段 ${evidenceRef} 的 snapshotId 与来源身份不一致`);
  const observed: ObservedEvidenceFragment = { evidenceRef, evidenceKind, sourceId, snapshotId, title: requireNonBlank(fragment.title, `证据片段 ${evidenceRef} 的标题`), excerpt: validateExcerpt(fragment.excerpt, evidenceRef), artifactRef: requireNonBlank(fragment.artifactRef, `证据片段 ${evidenceRef} 的私有 artifactRef`), locator: validateObservedLocator(fragment.locator, evidenceRef) };
  return observed;
}

function validateEvidenceLocators(value: unknown, sourceId: string): Record<string, ObservedEvidenceLocator> {
  const locators = requireRecord(value, `编译结果 ${sourceId} 的 evidenceLocators`);
  const publicLocators: Record<string, ObservedEvidenceLocator> = {};
  for (const [evidenceRef, locator] of Object.entries(locators)) {
    requireNonBlank(evidenceRef, `编译结果 ${sourceId} 的 evidenceRef`);
    publicLocators[evidenceRef] = validateObservedLocator(locator, evidenceRef);
  }
  return publicLocators;
}

function validateDelta(value: unknown, sourceId: string): PublicSourceDocumentCompilation['delta'] {
  const delta = requireRecord(value, `编译结果 ${sourceId} 的 delta`);
  requireOnlyKeys(delta, `编译结果 ${sourceId} 的 delta`, ['addedBlockIds', 'changedBlockIds', 'addedGapIds']);
  return {
    addedBlockIds: requireStringArray(delta.addedBlockIds, `编译结果 ${sourceId} 的 addedBlockIds`),
    changedBlockIds: requireStringArray(delta.changedBlockIds, `编译结果 ${sourceId} 的 changedBlockIds`),
    addedGapIds: requireStringArray(delta.addedGapIds, `编译结果 ${sourceId} 的 addedGapIds`),
  };
}

function validateCompilation(value: unknown): PublicSourceDocumentCompilation {
  const compilation = requireRecord(value, '来源编译结果');
  requireOnlyKeys(compilation, '来源编译结果', [
    'sourceId', 'sourceName', 'sourceClass', 'snapshotId', 'authority', 'readSummary', 'sections', 'blocks', 'assertions',
    'markdown', 'markdownSha256', 'evidenceLocators', 'delta', 'introducedConflictIds', 'corroboratedConflictIds', 'lineageStatus', 'upstreamSourceIds',
  ]);
  const sourceId = requireNonBlank(compilation.sourceId, '编译结果 sourceId');
  const markdown = requireNonBlank(compilation.markdown, `编译结果 ${sourceId} 的 Markdown`);
  const markdownSha256 = requireNonBlank(compilation.markdownSha256, `编译结果 ${sourceId} 的 markdownSha256`);
  if (markdownSha256 !== sha256HexSync(markdown)) snapshotError(`编译结果 ${sourceId} 的 Markdown SHA 校验失败`);
  const publicCompilation: PublicSourceDocumentCompilation = {
    sourceId,
    sourceName: requireNonBlank(compilation.sourceName, `编译结果 ${sourceId} 的 sourceName`),
    sourceClass: requireEnum(compilation.sourceClass, sourceClasses, `编译结果 ${sourceId} 的 sourceClass`),
    snapshotId: requireNonBlank(compilation.snapshotId, `编译结果 ${sourceId} 的 snapshotId`),
    authority: requireEnum(compilation.authority, sourceAuthorities, `编译结果 ${sourceId} 的 authority`),
    readSummary: validateReadSummary(compilation.readSummary, sourceId),
    sections: validateSections(compilation.sections, sourceId),
    blocks: requireArray(compilation.blocks, `编译结果 ${sourceId} 的 blocks`).map((block, index) => validateBlock(block, sourceId, index)),
    assertions: requireArray(compilation.assertions, `编译结果 ${sourceId} 的 assertions`).map((assertion, index) => validateAssertion(assertion, sourceId, index)),
    markdown,
    markdownSha256,
    evidenceLocators: validateEvidenceLocators(compilation.evidenceLocators, sourceId),
    delta: validateDelta(compilation.delta, sourceId),
    introducedConflictIds: requireStringArray(compilation.introducedConflictIds, `编译结果 ${sourceId} 的 introducedConflictIds`),
    corroboratedConflictIds: requireStringArray(compilation.corroboratedConflictIds, `编译结果 ${sourceId} 的 corroboratedConflictIds`),
    lineageStatus: requireEnum(compilation.lineageStatus, lineageStatuses, `编译结果 ${sourceId} 的 lineageStatus`),
    upstreamSourceIds: requireStringArray(compilation.upstreamSourceIds, `编译结果 ${sourceId} 的 upstreamSourceIds`),
  };
  return publicCompilation;
}

function validateTraceLink(value: unknown, sourceId: string): EvidenceTraceLink {
  const traceLink = requireRecord(value, `来源 ${sourceId} 的 trace link`);
  requireOnlyKeys(traceLink, `来源 ${sourceId} 的 trace link`, ['blockId', 'assertionId', 'section', 'markdownAnchor', 'evidenceRefs']);
  return {
    blockId: requireNonBlank(traceLink.blockId, `来源 ${sourceId} 的 trace blockId`),
    assertionId: requireNonBlank(traceLink.assertionId, `来源 ${sourceId} 的 trace assertionId`),
    section: requireEnum(traceLink.section, modelingSections, `来源 ${sourceId} 的 trace section`),
    markdownAnchor: requireNonBlank(traceLink.markdownAnchor, `来源 ${sourceId} 的 trace Markdown anchor`),
    evidenceRefs: requireStringArray(traceLink.evidenceRefs, `来源 ${sourceId} 的 trace evidenceRefs`),
  };
}

function validateGenerationRuns(value: unknown): FrozenDemoEvidenceBundle['generationRuns'] {
  return requireArray(value, 'Bundle generationRuns').map((entry, index) => {
    const run = requireRecord(entry, `generationRuns[${index}]`);
    requireOnlyKeys(run, `generationRuns[${index}]`, ['schemaVersion', 'runId', 'kind', 'status', 'inputManifestRefs', 'outputSha256']);
    if (run.schemaVersion !== 1) snapshotError(`generationRuns[${index}] 的 schemaVersion 不受支持`);
    return {
      schemaVersion: 1,
      runId: requireNonBlank(run.runId, `generationRuns[${index}] 的 runId`),
      kind: requireEnum(run.kind, generationKinds, `generationRuns[${index}] 的 kind`),
      status: requireEnum(run.status, generationStatuses, `generationRuns[${index}] 的 status`),
      inputManifestRefs: requireStringArray(run.inputManifestRefs, `generationRuns[${index}] 的 inputManifestRefs`),
      ...(run.outputSha256 === undefined ? {} : { outputSha256: requireSha256(run.outputSha256, `generationRuns[${index}] 的 outputSha256`) }),
    };
  });
}

function validateArtifactProvenance(value: unknown, identities: Map<string, SourceSnapshotIdentity>): PublicArtifactProvenance[] {
  const entries = requireArray(value, 'Bundle artifactProvenance').map((entry, index) => {
    const provenance = requireRecord(entry, `artifactProvenance[${index}]`);
    requireOnlyKeys(provenance, `artifactProvenance[${index}]`, ['artifactRef', 'sourceId', 'snapshotId', 'rawArtifactSha256']);
    const artifactRef = requireNonBlank(provenance.artifactRef, `artifactProvenance[${index}] 的 artifactRef`);
    const sourceId = requireNonBlank(provenance.sourceId, `artifactProvenance[${index}] 的 sourceId`);
    const identity = identities.get(sourceId);
    if (!identity) snapshotError(`artifactProvenance 来源不存在：${sourceId}`);
    const snapshotId = requireNonBlank(provenance.snapshotId, `artifactProvenance[${index}] 的 snapshotId`);
    if (snapshotId !== identity.snapshotId) snapshotError(`artifactProvenance snapshotId 与来源不一致：${artifactRef}`);
    return { artifactRef, sourceId, snapshotId, rawArtifactSha256: requireSha256(provenance.rawArtifactSha256, `artifactProvenance[${index}] 的 rawArtifactSha256`) };
  });
  requireDistinct(entries.map((entry) => entry.artifactRef), 'artifactProvenance artifactRef');
  return entries;
}

function validateTrustedRegistry(value: unknown): TrustedPublicationRegistry {
  const registry = requireRecord(value, 'TRUST_PIN_MISSING: trusted registry');
  requireOnlyKeys(registry, 'trusted registry', ['schemaVersion', 'registryVersion', 'publications']);
  if (registry.schemaVersion !== 1) snapshotError('TRUST_PIN_MISSING: 不支持的 registry schemaVersion');
  if (registry.registryVersion !== 'trusted-publications-v1') snapshotError('TRUST_PIN_MISSING: 不支持的 registryVersion');
  const publications = requireArray(registry.publications, 'trusted registry publications').map((entry, index) => {
    const pin = requireRecord(entry, `trusted registry publications[${index}]`);
    requireOnlyKeys(pin, `trusted registry publications[${index}]`, [
      'storyKey', 'receiptId', 'receiptSha256', 'bundleSha256', 'rawManifestDigest', 'redactionPolicySha256',
    ]);
    return {
      storyKey: requireNonBlank(pin.storyKey, `trusted registry publications[${index}] 的 storyKey`),
      receiptId: requireNonBlank(pin.receiptId, `trusted registry publications[${index}] 的 receiptId`),
      receiptSha256: requireSha256(pin.receiptSha256, `trusted registry publications[${index}] 的 receiptSha256`),
      bundleSha256: requireSha256(pin.bundleSha256, `trusted registry publications[${index}] 的 bundleSha256`),
      rawManifestDigest: requireSha256(pin.rawManifestDigest, `trusted registry publications[${index}] 的 rawManifestDigest`),
      redactionPolicySha256: requireSha256(pin.redactionPolicySha256, `trusted registry publications[${index}] 的 redactionPolicySha256`),
    } satisfies TrustedPublicationPin;
  });
  requireDistinct(publications.map((pin) => pin.storyKey), 'trusted registry storyKey');
  requireDistinct(publications.map((pin) => pin.receiptId), 'trusted registry receiptId');
  return { schemaVersion: 1, registryVersion: 'trusted-publications-v1', publications };
}

function validateRawManifestAttestation(value: unknown, index: number): PublishedRawManifestAttestation {
  const attestation = requireRecord(value, `publication receipt rawManifests[${index}]`);
  requireOnlyKeys(attestation, `publication receipt rawManifests[${index}]`, [
    'manifestRef', 'sourceId', 'snapshotId', 'manifestSha256',
  ]);
  return {
    manifestRef: requireNonBlank(attestation.manifestRef, `publication receipt rawManifests[${index}] 的 manifestRef`),
    sourceId: requireNonBlank(attestation.sourceId, `publication receipt rawManifests[${index}] 的 sourceId`),
    snapshotId: requireNonBlank(attestation.snapshotId, `publication receipt rawManifests[${index}] 的 snapshotId`),
    manifestSha256: requireSha256(attestation.manifestSha256, `publication receipt rawManifests[${index}] 的 manifestSha256`),
  };
}

function validateArtifactMembership(value: unknown, index: number): PublishedArtifactMembership {
  const membership = requireRecord(value, `publication receipt artifactMemberships[${index}]`);
  requireOnlyKeys(membership, `publication receipt artifactMemberships[${index}]`, [
    'artifactRef', 'manifestRef', 'rawArtifactSha256',
  ]);
  return {
    artifactRef: requireNonBlank(membership.artifactRef, `publication receipt artifactMemberships[${index}] 的 artifactRef`),
    manifestRef: requireNonBlank(membership.manifestRef, `publication receipt artifactMemberships[${index}] 的 manifestRef`),
    rawArtifactSha256: requireSha256(membership.rawArtifactSha256, `publication receipt artifactMemberships[${index}] 的 rawArtifactSha256`),
  };
}

function validateFragmentApproval(value: unknown, index: number): PublishedFragmentApproval {
  const approval = requireRecord(value, `publication receipt fragmentApprovals[${index}]`);
  requireOnlyKeys(approval, `publication receipt fragmentApprovals[${index}]`, [
    'evidenceRef', 'artifactRef', 'locatorSha256', 'excerptSha256',
  ]);
  return {
    evidenceRef: requireNonBlank(approval.evidenceRef, `publication receipt fragmentApprovals[${index}] 的 evidenceRef`),
    artifactRef: requireNonBlank(approval.artifactRef, `publication receipt fragmentApprovals[${index}] 的 artifactRef`),
    locatorSha256: requireSha256(approval.locatorSha256, `publication receipt fragmentApprovals[${index}] 的 locatorSha256`),
    excerptSha256: requireSha256(approval.excerptSha256, `publication receipt fragmentApprovals[${index}] 的 excerptSha256`),
  };
}

function validatePublicationReceipt(value: unknown): PublicationReceipt {
  const receipt = requireRecord(value, 'PUBLICATION_RECEIPT_INVALID: publication receipt');
  requireOnlyKeys(receipt, 'publication receipt', [
    'schemaVersion', 'receiptId', 'storyKey', 'rawManifests', 'rawManifestDigest', 'artifactMemberships', 'fragmentApprovals',
    'publicBundleSha256', 'redactionApproval', 'receiptSha256',
  ]);
  if (receipt.schemaVersion !== 1) snapshotError('PUBLICATION_RECEIPT_INVALID: 不支持的 receipt schemaVersion');
  const rawManifests = requireArray(receipt.rawManifests, 'publication receipt rawManifests')
    .map(validateRawManifestAttestation);
  requireDistinct(rawManifests.map((entry) => entry.manifestRef), 'publication receipt rawManifests manifestRef');
  requireStrictlySorted(rawManifests.map((entry) => `${entry.sourceId}\u0000${entry.snapshotId}\u0000${entry.manifestRef}`), 'publication receipt rawManifests');
  const artifactMemberships = requireArray(receipt.artifactMemberships, 'publication receipt artifactMemberships')
    .map(validateArtifactMembership);
  requireStrictlySorted(artifactMemberships.map((entry) => entry.artifactRef), 'publication receipt artifactMemberships');
  const fragmentApprovals = requireArray(receipt.fragmentApprovals, 'publication receipt fragmentApprovals')
    .map(validateFragmentApproval);
  requireStrictlySorted(fragmentApprovals.map((entry) => entry.evidenceRef), 'publication receipt fragmentApprovals');
  const approval = requireRecord(receipt.redactionApproval, 'publication receipt redactionApproval');
  requireOnlyKeys(approval, 'publication receipt redactionApproval', [
    'decision', 'scope', 'approvedBundleSha256', 'policyId', 'policyVersion', 'policySha256', 'sanitizerVersion', 'approvalRef', 'approvedAt',
  ]);
  if (approval.decision !== 'APPROVED') snapshotError('PUBLICATION_NOT_APPROVED: redactionApproval decision 必须是 APPROVED');
  if (approval.scope !== 'ENTIRE_PUBLIC_BUNDLE') snapshotError('PUBLICATION_NOT_APPROVED: redactionApproval scope 必须是 ENTIRE_PUBLIC_BUNDLE');
  const redactionApproval = {
    decision: 'APPROVED' as const,
    scope: 'ENTIRE_PUBLIC_BUNDLE' as const,
    approvedBundleSha256: requireSha256(approval.approvedBundleSha256, 'publication receipt redactionApproval approvedBundleSha256'),
    policyId: requireNonBlank(approval.policyId, 'publication receipt redactionApproval policyId'),
    policyVersion: requireNonBlank(approval.policyVersion, 'publication receipt redactionApproval policyVersion'),
    policySha256: requireSha256(approval.policySha256, 'publication receipt redactionApproval policySha256'),
    sanitizerVersion: requireNonBlank(approval.sanitizerVersion, 'publication receipt redactionApproval sanitizerVersion'),
    approvalRef: requireNonBlank(approval.approvalRef, 'publication receipt redactionApproval approvalRef'),
    approvedAt: requireTimestamp(approval.approvedAt, 'publication receipt redactionApproval approvedAt'),
  };
  const publicReceipt = {
    schemaVersion: 1 as const,
    receiptId: requireNonBlank(receipt.receiptId, 'publication receipt receiptId'),
    storyKey: requireNonBlank(receipt.storyKey, 'publication receipt storyKey'),
    rawManifests,
    rawManifestDigest: requireSha256(receipt.rawManifestDigest, 'publication receipt rawManifestDigest'),
    artifactMemberships,
    fragmentApprovals,
    publicBundleSha256: requireSha256(receipt.publicBundleSha256, 'publication receipt publicBundleSha256'),
    redactionApproval,
  };
  const receiptSha256 = requireSha256(receipt.receiptSha256, 'publication receipt receiptSha256');
  if (receiptSha256 !== canonicalSha256(publicReceipt)) snapshotError('PUBLICATION_RECEIPT_INVALID: receiptSha256 校验失败');
  return { ...publicReceipt, receiptSha256 };
}

function validateTrustedPublication(
  bundle: FrozenDemoEvidenceBundle,
  pin: TrustedPublicationPin,
  receipt: PublicationReceipt,
) {
  if (bundle.publicationReceiptId !== pin.receiptId || receipt.receiptId !== pin.receiptId) {
    snapshotError('PUBLICATION_RECEIPT_INVALID: publicationReceiptId 与 trusted registry 不一致');
  }
  if (receipt.storyKey !== bundle.storyKey || receipt.storyKey !== pin.storyKey) {
    snapshotError('PUBLICATION_RECEIPT_INVALID: receipt storyKey 不一致');
  }
  if (bundle.bundleSha256 !== pin.bundleSha256 || receipt.publicBundleSha256 !== pin.bundleSha256 || receipt.redactionApproval.approvedBundleSha256 !== pin.bundleSha256) {
    snapshotError('BUNDLE_DIGEST_MISMATCH: Bundle/receipt/approval/trusted pin 摘要不一致');
  }
  if (receipt.receiptSha256 !== pin.receiptSha256) snapshotError('PUBLICATION_RECEIPT_INVALID: fragment approval 或 receiptSha256 与 trusted pin 不一致');
  if (receipt.redactionApproval.policySha256 !== pin.redactionPolicySha256) {
    snapshotError('REDACTION_POLICY_MISMATCH: redaction policy 摘要不一致');
  }
  const expectedRawManifestDigest = canonicalSha256({
    schemaVersion: 1,
    manifests: receipt.rawManifests.map(({ manifestRef, sourceId, snapshotId, manifestSha256 }) => ({
      manifestRef, sourceId, snapshotId, manifestSha256,
    })),
  });
  if (receipt.rawManifestDigest !== expectedRawManifestDigest || bundle.rawManifestDigest !== expectedRawManifestDigest || pin.rawManifestDigest !== expectedRawManifestDigest) {
    snapshotError('RAW_MANIFEST_ATTESTATION_MISMATCH: rawManifestDigest 不闭合');
  }

  const manifestByRef = new Map(receipt.rawManifests.map((manifest) => [manifest.manifestRef, manifest]));
  const membershipByArtifact = new Map(receipt.artifactMemberships.map((membership) => [membership.artifactRef, membership]));
  for (const membership of receipt.artifactMemberships) {
    if (!manifestByRef.has(membership.manifestRef)) {
      snapshotError(`ARTIFACT_NOT_ATTESTED: artifact membership 缺少 manifest：${membership.artifactRef}`);
    }
  }
  if (bundle.artifactProvenance.length !== receipt.artifactMemberships.length) {
    snapshotError('ARTIFACT_NOT_ATTESTED: artifactProvenance 与 receipt membership 数量不闭合');
  }
  for (const provenance of bundle.artifactProvenance) {
    const membership = membershipByArtifact.get(provenance.artifactRef);
    if (!membership) snapshotError(`ARTIFACT_NOT_ATTESTED: artifactProvenance 缺少 membership：${provenance.artifactRef}`);
    const manifest = manifestByRef.get(membership.manifestRef);
    if (!manifest || provenance.sourceId !== manifest.sourceId || provenance.snapshotId !== manifest.snapshotId || provenance.rawArtifactSha256 !== membership.rawArtifactSha256) {
      snapshotError(`ARTIFACT_NOT_ATTESTED: artifactProvenance 与 receipt 不一致：${provenance.artifactRef}`);
    }
  }

  const observedFragments = bundle.evidenceFragments.filter((fragment): fragment is ObservedEvidenceFragment => fragment.evidenceKind === 'OBSERVED');
  if (receipt.fragmentApprovals.length !== observedFragments.length) {
    snapshotError('FRAGMENT_APPROVAL_MISMATCH: observed fragments 与 approvals 数量不闭合');
  }
  const approvalByEvidenceRef = new Map(receipt.fragmentApprovals.map((approval) => [approval.evidenceRef, approval]));
  const approvedArtifactRefs = new Set(receipt.fragmentApprovals.map((approval) => approval.artifactRef));
  for (const membership of receipt.artifactMemberships) {
    if (!approvedArtifactRefs.has(membership.artifactRef)) snapshotError(`ARTIFACT_NOT_ATTESTED: artifactRef membership 未被 fragment approval 使用：${membership.artifactRef}`);
  }
  for (const fragment of observedFragments) {
    const approval = approvalByEvidenceRef.get(fragment.evidenceRef);
    if (!approval || approval.artifactRef !== fragment.artifactRef) {
      snapshotError(`FRAGMENT_APPROVAL_MISMATCH: observed fragment 未获批准：${fragment.evidenceRef}`);
    }
    const membership = membershipByArtifact.get(fragment.artifactRef);
    const manifest = membership && manifestByRef.get(membership.manifestRef);
    if (!membership || !manifest || fragment.sourceId !== manifest.sourceId || fragment.snapshotId !== manifest.snapshotId) {
      snapshotError(`ARTIFACT_NOT_ATTESTED: observed fragment artifact 不闭合：${fragment.evidenceRef}`);
    }
    if (approval.locatorSha256 !== canonicalSha256(fragment.locator) || approval.excerptSha256 !== utf8Sha256(fragment.excerpt)) {
      snapshotError(`FRAGMENT_APPROVAL_MISMATCH: observed fragment approval 摘要不一致：${fragment.evidenceRef}`);
    }
  }
}

function validateTraceLinks(
  value: unknown,
  compilations: PublicSourceDocumentCompilation[],
  fragments: Map<string, EvidenceFragment>,
) {
  const traceLinksBySource = requireRecord(value, 'Bundle traceLinks');
  const knownSources = new Set(compilations.map((compilation) => compilation.sourceId));
  for (const sourceId of Object.keys(traceLinksBySource)) {
    if (!knownSources.has(sourceId)) snapshotError(`trace link 引用了不存在的来源：${sourceId}`);
  }
  const publicTraceLinks: Record<string, EvidenceTraceLink[]> = {};
  for (const compilation of compilations) {
    if (!Object.hasOwn(traceLinksBySource, compilation.sourceId)) snapshotError(`来源 ${compilation.sourceId} 缺少 trace links`);
    const traces = requireArray(traceLinksBySource[compilation.sourceId], `来源 ${compilation.sourceId} 的 trace links`)
      .map((trace) => validateTraceLink(trace, compilation.sourceId));
    const blocks = new Map(compilation.blocks.map((block) => [block.blockId, block]));
    const assertions = new Map(compilation.assertions.map((assertion) => [assertion.assertionId, assertion]));
    if (blocks.size !== compilation.blocks.length || assertions.size !== compilation.assertions.length) snapshotError(`来源 ${compilation.sourceId} 的公开标识重复`);
    const tracedBlocks = new Set<string>();
    const tracedAssertions = new Set<string>();
    for (const trace of traces) {
      if (!trace.evidenceRefs.length) snapshotError(`trace 必须引用证据片段：${trace.blockId}`);
      if (tracedBlocks.has(trace.blockId) || tracedAssertions.has(trace.assertionId)) snapshotError(`来源 ${compilation.sourceId} 的 trace 覆盖重复`);
      tracedBlocks.add(trace.blockId);
      tracedAssertions.add(trace.assertionId);
      const block = blocks.get(trace.blockId);
      const assertion = assertions.get(trace.assertionId);
      if (!block || !assertion) snapshotError(`trace link 引用了不存在的 block/assertion：${trace.blockId}`);
      if (trace.section !== block.section || trace.section !== assertion.section) snapshotError(`trace link 章节不一致：${trace.blockId}`);
      if (!compilation.markdown.includes(trace.markdownAnchor)) snapshotError(`trace Markdown anchor 不存在：${trace.markdownAnchor}`);
      if (!sameJson(trace.evidenceRefs, block.evidenceRefs) || !sameJson(trace.evidenceRefs, assertion.evidenceRefs)) snapshotError(`trace link 证据引用不一致：${trace.blockId}`);
      for (const evidenceRef of trace.evidenceRefs) {
        const fragment = fragments.get(evidenceRef);
        if (!fragment) snapshotError(`缺少证据片段：${evidenceRef}`);
        if (fragment.evidenceKind === 'GENERATED_TARGET') {
          if (block.evidenceStatus !== 'GAP' || !['GAP', 'PENDING_ASSET'].includes(block.semanticKind) || assertion.provenance !== 'INFERRED') {
            snapshotError(`GENERATED_TARGET 只能支持显式 GAP/PENDING_ASSET 与 INFERRED assertion：${evidenceRef}`);
          }
          continue;
        }
        if (fragment.sourceId !== compilation.sourceId) snapshotError(`证据片段来源与 trace 不一致：${evidenceRef}`);
        if (!sameJson(compilation.evidenceLocators[evidenceRef], fragment.locator)) snapshotError(`证据片段 public locator 与编译结果不一致：${evidenceRef}`);
      }
    }
    for (const id of blocks.keys()) if (!tracedBlocks.has(id)) snapshotError(`trace 未覆盖 block：${id}`);
    for (const id of assertions.keys()) if (!tracedAssertions.has(id)) snapshotError(`trace 未覆盖 assertion：${id}`);
    publicTraceLinks[compilation.sourceId] = traces;
  }
  return publicTraceLinks;
}

function validateBundle(value: unknown): FrozenDemoEvidenceBundle {
  if (!isRecord(value)) snapshotError('Bundle evidenceFragments 必须是数组');
  const bundle = value;
  requireOnlyKeys(bundle, 'Bundle', [
    'schemaVersion', 'storyKey', 'compilerVersion', 'sourceIdentities', 'compilations', 'traceLinks', 'evidenceFragments',
    'publicationReceiptId', 'rawManifestDigest', 'artifactProvenance', 'generationRuns', 'bundleSha256',
  ]);
  if (bundle.schemaVersion !== 1) snapshotError('不支持的 Bundle schemaVersion');
  const storyKey = requireNonBlank(bundle.storyKey, 'Bundle storyKey');
  const publicationReceiptId = requireNonBlank(bundle.publicationReceiptId, 'Bundle publicationReceiptId');
  if (bundle.compilerVersion !== 'evidence-factory-v1') snapshotError(`不支持的 Bundle compilerVersion：${String(bundle.compilerVersion)}`);
  requireArray(bundle.sourceIdentities, 'Bundle sourceIdentities');
  requireArray(bundle.compilations, 'Bundle compilations');
  requireArray(bundle.evidenceFragments, 'Bundle evidenceFragments');
  requireArray(bundle.artifactProvenance, 'Bundle artifactProvenance');
  requireArray(bundle.generationRuns, 'Bundle generationRuns');
  requireRecord(bundle.traceLinks, 'Bundle traceLinks');
  const bundleSha256 = validateBundleDigest(bundle);

  const sourceIdentities = requireArray(bundle.sourceIdentities, 'Bundle sourceIdentities').map(validateSourceIdentity);
  if (!sourceIdentities.length) snapshotError('Bundle sourceIdentities 不能为空');
  requireDistinct(sourceIdentities.map((identity) => identity.sourceId), 'Bundle sourceIdentities sourceId');
  const identities = new Map(sourceIdentities.map((identity) => [identity.sourceId, identity]));
  for (const identity of sourceIdentities) {
    for (const upstreamSourceId of identity.upstreamSourceIds) if (!identities.has(upstreamSourceId)) snapshotError(`来源 ${identity.sourceId} 引用了不存在的上游来源：${upstreamSourceId}`);
  }
  const compilations = requireArray(bundle.compilations, 'Bundle compilations').map(validateCompilation);
  if (compilations.length !== sourceIdentities.length) snapshotError('来源身份与编译结果数量不一致');
  requireDistinct(compilations.map((compilation) => compilation.sourceId), 'Bundle compilations sourceId');
  for (const compilation of compilations) {
    const identity = identities.get(compilation.sourceId);
    if (!identity) snapshotError(`编译结果缺少来源身份：${compilation.sourceId}`);
    for (const field of ['sourceId', 'sourceName', 'sourceClass', 'snapshotId', 'authority', 'lineageStatus', 'upstreamSourceIds'] as const) {
      if (!sameJson(compilation[field], identity[field])) snapshotError(`来源投影不一致：${compilation.sourceId} 的 ${field}`);
    }
  }
  const evidenceFragments = requireArray(bundle.evidenceFragments, 'Bundle evidenceFragments').map((fragment) => validateFragment(fragment, identities));
  requireDistinct(evidenceFragments.map((fragment) => fragment.evidenceRef), 'Bundle evidenceFragments evidenceRef');
  const fragments = new Map(evidenceFragments.map((fragment) => [fragment.evidenceRef, fragment]));
  const artifactProvenance = validateArtifactProvenance(bundle.artifactProvenance, identities);
  const traceLinks = validateTraceLinks(bundle.traceLinks, compilations, fragments);
  const rawManifestDigest = requireSha256(bundle.rawManifestDigest, 'Bundle rawManifestDigest');
  return {
    schemaVersion: 1,
    storyKey,
    compilerVersion: 'evidence-factory-v1',
    sourceIdentities,
    compilations,
    traceLinks,
    evidenceFragments,
    publicationReceiptId,
    rawManifestDigest,
    artifactProvenance,
    generationRuns: validateGenerationRuns(bundle.generationRuns),
    bundleSha256,
  };
}

function findTrustedPin(registry: TrustedPublicationRegistry, storyKey: string): TrustedPublicationPin {
  const matches = registry.publications.filter((pin) => pin.storyKey === storyKey);
  if (!matches.length) snapshotError(`TRUST_PIN_MISSING: 找不到 storyKey 的 trusted publication：${storyKey}`);
  if (matches.length > 1) snapshotError(`TRUST_PIN_AMBIGUOUS: storyKey 对应多个 trusted publication：${storyKey}`);
  return matches[0];
}

function verifiedBundleForStory(
  rawBundles: readonly unknown[],
  rawReceipts: readonly unknown[],
  rawRegistry: unknown,
  storyKey: string,
) {
  // Preflight every Bundle so malformed persisted data never leaks through a
  // registry lookup. This also preserves the readFragment fail-closed seam.
  const bundles = rawBundles.map(validateBundle);
  const registry = validateTrustedRegistry(rawRegistry);
  const pin = findTrustedPin(registry, storyKey);
  const matches = bundles.filter((bundle) => bundle.storyKey === storyKey);
  if (!matches.length) snapshotError(`找不到 storyKey：${storyKey}`);
  if (matches.length > 1) snapshotError(`storyKey 对应多个 Bundle：${storyKey}`);
  const receipts = rawReceipts.map(validatePublicationReceipt);
  const receiptMatches = receipts.filter((receipt) => receipt.receiptId === pin.receiptId);
  if (!receiptMatches.length) snapshotError(`PUBLICATION_RECEIPT_MISSING: 找不到 receipt：${pin.receiptId}`);
  if (receiptMatches.length > 1) snapshotError(`PUBLICATION_RECEIPT_INVALID: receipt 重复：${pin.receiptId}`);
  validateTrustedPublication(matches[0], pin, receiptMatches[0]);
  return matches[0];
}

export function createEvidenceFactory(input: EvidenceFactoryDependencies): EvidenceFactory {
  if (!isRecord(input) || !Array.isArray(input.bundles)) snapshotError('EvidenceFactory bundles 必须是数组');
  if (!Array.isArray(input.publicationReceipts)) snapshotError('EvidenceFactory publicationReceipts 必须是数组');
  const bundles = clone(input.bundles);
  const publicationReceipts = clone(input.publicationReceipts);
  const trustedRegistry = clone(input.trustedRegistry);

  return {
    readBundle({ storyKey }) {
      return clone(verifiedBundleForStory(bundles, publicationReceipts, trustedRegistry, storyKey));
    },
    readFragment({ evidenceRef }) {
      const publicBundles = bundles.map(validateBundle);
      const registry = validateTrustedRegistry(trustedRegistry);
      const receipts = publicationReceipts.map(validatePublicationReceipt);
      for (const bundle of publicBundles) {
        const pin = findTrustedPin(registry, bundle.storyKey);
        const receiptMatches = receipts.filter((receipt) => receipt.receiptId === pin.receiptId);
        if (!receiptMatches.length) snapshotError(`PUBLICATION_RECEIPT_MISSING: 找不到 receipt：${pin.receiptId}`);
        if (receiptMatches.length > 1) snapshotError(`PUBLICATION_RECEIPT_INVALID: receipt 重复：${pin.receiptId}`);
        validateTrustedPublication(bundle, pin, receiptMatches[0]);
      }
      const matches = publicBundles.flatMap((bundle) => bundle.evidenceFragments.filter((fragment) => fragment.evidenceRef === evidenceRef));
      if (!matches.length) snapshotError(`找不到证据片段：${evidenceRef}`);
      if (matches.length > 1) snapshotError(`证据片段在多个 Bundle 中重复：${evidenceRef}`);
      return clone(matches[0]);
    },
  };
}
