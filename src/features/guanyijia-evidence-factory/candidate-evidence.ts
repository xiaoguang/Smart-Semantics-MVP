import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  generatedCandidateEvidence,
  generatedCandidateEvidenceIntegrity,
} from './guanyijia-candidate-evidence.generated.ts';

export type CandidateEvidenceClass =
  | 'OBSERVED' | 'FROZEN_RECORD' | 'GENERATED_TARGET' | 'DERIVED' | 'GAP';

export type CandidateEvidenceFragment = {
  evidenceRef: string;
  sourceId: 'guanyijia_mysql' | 'guanyijia_github' | 'guanyijia_official_docs';
  snapshotId: string;
  topic: 'NEGATIVE_STOCK' | 'DEBT_FIELDS' | 'DOCUMENT_STATUS';
  evidenceClass: CandidateEvidenceClass;
  title: string;
  excerpt: string;
  locationLabel: string;
  locationValue: string;
  sourceName: string;
  artifactDigest: `sha256:${string}`;
};

export type CandidateClaim = {
  claimId: string;
  topic: CandidateEvidenceFragment['topic'];
  subjectRef: string;
  predicate: string;
  normalizedValue: string;
  scope: string;
  effectiveTime?: string;
  evidenceClass: CandidateEvidenceClass;
  evidenceRefs: readonly string[];
};

export type CandidateRelationKind =
  | 'CORROBORATES' | 'COMPLEMENTS' | 'CONFLICTS'
  | 'SCOPE_DIFFERENCE' | 'TEMPORAL_DRIFT' | 'UNSUPPORTED';

export type CandidateEvidenceBundle = {
  schemaVersion: 1;
  kind: 'GUANYIJIA_REAL_EVIDENCE_CANDIDATE';
  fragments: readonly CandidateEvidenceFragment[];
  claims: readonly CandidateClaim[];
  relations: readonly {
    leftClaimId: string;
    rightClaimId: string;
    relation: CandidateRelationKind;
    explanation: string;
  }[];
};

type JsonRecord = Record<string, unknown>;

const topics = ['NEGATIVE_STOCK', 'DEBT_FIELDS', 'DOCUMENT_STATUS'] as const;
const evidenceClasses = ['OBSERVED', 'FROZEN_RECORD', 'GENERATED_TARGET', 'DERIVED', 'GAP'] as const;
const publicEvidenceClasses = ['OBSERVED', 'FROZEN_RECORD', 'GAP'] as const;
const publicSourceSnapshots = {
  guanyijia_mysql: '20260813T032528Z-abb0502c7d79',
  guanyijia_github: '20260813032126Z-5821d0ece9b1',
  guanyijia_official_docs: 'gyjerp-official-docs-20260813T031656Z',
} as const;
const publicSourceIds = Object.keys(publicSourceSnapshots) as Array<keyof typeof publicSourceSnapshots>;
const relationKinds = ['CORROBORATES', 'COMPLEMENTS', 'CONFLICTS', 'SCOPE_DIFFERENCE', 'TEMPORAL_DRIFT', 'UNSUPPORTED'] as const;
const forbiddenPublicContent = /(?:samples?|profiles?|raw[-_ ]?row|query[-_ ]?result|bound[-_ ]?parameter|\bexample\b|Tenant\s+\d+|FROZEN_FILE|\bREAL\b|\bPRIMARY\b)/i;
// This anchor lives in trusted runtime code, not beside the generated payload.
// A maintenance admission update must intentionally review and update it.
const trustedCandidatePacketDigest = 'sha256:9139756cbff53b56f5454477a5de7482b6f7d8419e682c7fc4c7f65f5db8bff1';

function candidateError(message: string): never {
  throw new Error(`候选证据快照不可用：${message}`);
}

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requireRecord(value: unknown, label: string): JsonRecord {
  if (!isRecord(value)) candidateError(`${label} 必须是对象`);
  return value;
}

function requireArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) candidateError(`${label} 必须是数组`);
  return value;
}

function requireOnlyKeys(record: JsonRecord, label: string, allowed: readonly string[]) {
  for (const key of Object.keys(record)) {
    if (!allowed.includes(key)) candidateError(`${label} 包含未知字段：${key}`);
  }
}

function requireText(value: unknown, label: string): string {
  if (typeof value !== 'string' || !value.trim()) candidateError(`${label} 必须是非空文本`);
  return value;
}

function requireEnum<T extends string>(value: unknown, allowed: readonly T[], label: string): T {
  if (typeof value !== 'string' || !allowed.includes(value as T)) candidateError(`${label} 不受支持：${String(value)}`);
  return value as T;
}

function requireDigest(value: unknown, label: string): `sha256:${string}` {
  const digest = requireText(value, label);
  if (!/^sha256:[a-f0-9]{64}$/.test(digest)) candidateError(`${label} 必须是 SHA-256 摘要`);
  return digest as `sha256:${string}`;
}

function requireDistinct(values: readonly string[], label: string) {
  if (new Set(values).size !== values.length) candidateError(`${label} 不能重复`);
}

function validatePublicText(text: string, label: string) {
  if (forbiddenPublicContent.test(text)) candidateError(`${label} 包含不允许的公开内容`);
  if (new TextEncoder().encode(text).byteLength > 32 * 1024) candidateError(`${label} 超过公开摘录上限`);
}

function candidatePacketDigest(): `sha256:${string}` {
  return `sha256:${sha256HexSync(JSON.stringify({
    bundle: generatedCandidateEvidence,
    integrity: generatedCandidateEvidenceIntegrity,
  }))}`;
}

function validateTrustedCandidatePacket() {
  if (candidatePacketDigest() !== trustedCandidatePacketDigest) {
    candidateError('候选数据包与受信任完整性锚点不一致');
  }
}

function validateFragment(value: unknown, index: number): CandidateEvidenceFragment {
  const record = requireRecord(value, `fragments[${index}]`);
  requireOnlyKeys(record, `fragments[${index}]`, [
    'evidenceRef', 'sourceId', 'snapshotId', 'topic', 'evidenceClass', 'title', 'excerpt', 'locationLabel', 'locationValue', 'sourceName', 'artifactDigest',
  ]);
  const evidenceRef = requireText(record.evidenceRef, `fragments[${index}].evidenceRef`);
  const sourceId = requireEnum(record.sourceId, publicSourceIds, `fragments[${index}].sourceId`);
  const snapshotId = requireText(record.snapshotId, `fragments[${index}].snapshotId`);
  const topic = requireEnum(record.topic, topics, `fragments[${index}].topic`);
  const evidenceClass = requireEnum(record.evidenceClass, evidenceClasses, `fragments[${index}].evidenceClass`);
  const title = requireText(record.title, `fragments[${index}].title`);
  const excerpt = requireText(record.excerpt, `fragments[${index}].excerpt`);
  const locationLabel = requireText(record.locationLabel, `fragments[${index}].locationLabel`);
  const locationValue = requireText(record.locationValue, `fragments[${index}].locationValue`);
  const sourceName = requireText(record.sourceName, `fragments[${index}].sourceName`);
  const artifactDigest = requireDigest(record.artifactDigest, `fragments[${index}].artifactDigest`);
  if (snapshotId !== publicSourceSnapshots[sourceId as keyof typeof publicSourceSnapshots]) {
    candidateError(`fragments[${index}] 的来源快照身份不匹配`);
  }
  validatePublicText(`${title}\n${excerpt}\n${locationLabel}\n${locationValue}\n${sourceName}`, `fragments[${index}]`);
  if (locationValue.startsWith('/') || locationValue.includes('/Users/')) {
    candidateError(`fragments[${index}] 不能泄露私有绝对路径`);
  }
  if (evidenceClass === 'FROZEN_RECORD' && !sourceName.includes('冻结结构化记录')) {
    candidateError(`FROZEN_RECORD ${evidenceRef} 必须说明它是冻结结构化记录`);
  }
  if (evidenceClass === 'GAP' && !excerpt.includes('未被保留')) {
    candidateError(`GAP ${evidenceRef} 必须明确资料未被保留`);
  }
  const expectedClass = sourceId === 'guanyijia_mysql'
    ? 'OBSERVED'
    : sourceId === 'guanyijia_github' ? 'FROZEN_RECORD' : 'GAP';
  if (evidenceClass !== expectedClass) {
    candidateError(`fragments[${index}] 的证据等级与来源身份不匹配`);
  }
  return { evidenceRef, sourceId, snapshotId, topic, evidenceClass, title, excerpt, locationLabel, locationValue, sourceName, artifactDigest };
}

function validateClaim(value: unknown, index: number, fragments: ReadonlyMap<string, CandidateEvidenceFragment>): CandidateClaim {
  const record = requireRecord(value, `claims[${index}]`);
  requireOnlyKeys(record, `claims[${index}]`, [
    'claimId', 'topic', 'subjectRef', 'predicate', 'normalizedValue', 'scope', 'effectiveTime', 'evidenceClass', 'evidenceRefs',
  ]);
  const claimId = requireText(record.claimId, `claims[${index}].claimId`);
  const topic = requireEnum(record.topic, topics, `claims[${index}].topic`);
  const subjectRef = requireText(record.subjectRef, `claims[${index}].subjectRef`);
  const predicate = requireText(record.predicate, `claims[${index}].predicate`);
  const normalizedValue = requireText(record.normalizedValue, `claims[${index}].normalizedValue`);
  const scope = requireText(record.scope, `claims[${index}].scope`);
  const effectiveTime = record.effectiveTime === undefined ? undefined : requireText(record.effectiveTime, `claims[${index}].effectiveTime`);
  const evidenceClass = requireEnum(record.evidenceClass, evidenceClasses, `claims[${index}].evidenceClass`);
  const evidenceRefs = requireArray(record.evidenceRefs, `claims[${index}].evidenceRefs`).map((ref, refIndex) =>
    requireText(ref, `claims[${index}].evidenceRefs[${refIndex}]`));
  requireDistinct(evidenceRefs, `claims[${index}].evidenceRefs`);
  for (const evidenceRef of evidenceRefs) {
    const fragment = fragments.get(evidenceRef);
    if (!fragment) candidateError(`claim ${claimId} 引用了未知证据：${evidenceRef}`);
    if (fragment.topic !== topic) candidateError(`claim ${claimId} 的证据主题不一致：${evidenceRef}`);
    if (fragment.evidenceClass !== evidenceClass) candidateError(`claim ${claimId} 的证据类别不一致：${evidenceRef}`);
  }
  if (evidenceClass !== 'GAP' && evidenceRefs.length === 0) candidateError(`claim ${claimId} 缺少证据引用`);
  return {
    claimId, topic, subjectRef, predicate, normalizedValue, scope, ...(effectiveTime ? { effectiveTime } : {}), evidenceClass, evidenceRefs,
  };
}

function validateIntegrity(fragments: readonly CandidateEvidenceFragment[]) {
  const integrity = requireRecord(generatedCandidateEvidenceIntegrity, 'candidate integrity');
  requireOnlyKeys(integrity, 'candidate integrity', ['schemaVersion', 'mysqlSnapshotId', 'githubSnapshotId', 'fragments']);
  if (integrity.schemaVersion !== 1) candidateError('candidate integrity schemaVersion 不受支持');
  if (integrity.mysqlSnapshotId !== '20260813T032528Z-abb0502c7d79') candidateError('MySQL 冻结快照身份不匹配');
  if (integrity.githubSnapshotId !== '20260813032126Z-5821d0ece9b1') candidateError('GitHub 冻结快照身份不匹配');
  const records = requireArray(integrity.fragments, 'candidate integrity.fragments');
  if (records.length !== fragments.length) candidateError('candidate integrity 片段数不一致');
  const fragmentByRef = new Map(fragments.map((fragment) => [fragment.evidenceRef, fragment]));
  const integrityRefs: string[] = [];
  for (const [index, value] of records.entries()) {
    const record = requireRecord(value, `candidate integrity.fragments[${index}]`);
    requireOnlyKeys(record, `candidate integrity.fragments[${index}]`, ['evidenceRef', 'artifactDigest', 'excerptDigest', 'locationValue']);
    const evidenceRef = requireText(record.evidenceRef, `candidate integrity.fragments[${index}].evidenceRef`);
    const fragment = fragmentByRef.get(evidenceRef);
    if (!fragment) candidateError(`candidate integrity 引用了未知证据：${evidenceRef}`);
    if (fragment.artifactDigest !== requireDigest(record.artifactDigest, `candidate integrity.fragments[${index}].artifactDigest`)) {
      candidateError(`证据 ${evidenceRef} 的已保存源字节摘要不一致`);
    }
    if (`sha256:${sha256HexSync(fragment.excerpt)}` !== requireDigest(record.excerptDigest, `candidate integrity.fragments[${index}].excerptDigest`)) {
      candidateError(`证据 ${evidenceRef} 的已保存摘录字节不一致`);
    }
    if (fragment.locationValue !== requireText(record.locationValue, `candidate integrity.fragments[${index}].locationValue`)) {
      candidateError(`证据 ${evidenceRef} 的已保存行定位不一致`);
    }
    integrityRefs.push(evidenceRef);
  }
  requireDistinct(integrityRefs, 'candidate integrity.fragments');
}

export function compareCandidateClaims(left: CandidateClaim, right: CandidateClaim): CandidateRelationKind {
  if (
    left.evidenceRefs.length === 0
    || right.evidenceRefs.length === 0
    || left.evidenceClass === 'GAP'
    || right.evidenceClass === 'GAP'
  ) return 'UNSUPPORTED';
  if (left.subjectRef !== right.subjectRef || left.predicate !== right.predicate) return 'COMPLEMENTS';
  if (left.scope !== right.scope) return 'SCOPE_DIFFERENCE';
  if (left.effectiveTime && right.effectiveTime && left.effectiveTime !== right.effectiveTime) return 'TEMPORAL_DRIFT';
  if (left.normalizedValue === right.normalizedValue) {
    // A frozen structural record can add context, but it cannot be counted as
    // a second independent observation of the same production fact.
    return left.evidenceClass === 'FROZEN_RECORD' || right.evidenceClass === 'FROZEN_RECORD'
      ? 'COMPLEMENTS'
      : 'CORROBORATES';
  }
  return 'CONFLICTS';
}

function validateBundle(): CandidateEvidenceBundle {
  validateTrustedCandidatePacket();
  const source = requireRecord(generatedCandidateEvidence, 'candidate bundle');
  requireOnlyKeys(source, 'candidate bundle', ['schemaVersion', 'kind', 'fragments', 'claims', 'relations']);
  if (source.schemaVersion !== 1 || source.kind !== 'GUANYIJIA_REAL_EVIDENCE_CANDIDATE') {
    candidateError('candidate bundle identity 不受支持');
  }

  const fragments = requireArray(source.fragments, 'candidate bundle.fragments').map(validateFragment);
  requireDistinct(fragments.map((fragment) => fragment.evidenceRef), 'candidate bundle.fragments evidenceRef');
  const actualTopics = [...new Set(fragments.map((fragment) => fragment.topic))].sort();
  if (actualTopics.join(',') !== [...topics].sort().join(',')) candidateError('candidate bundle 必须只覆盖三个指定主题');
  const actualClasses = [...new Set(fragments.map((fragment) => fragment.evidenceClass))].sort();
  if (actualClasses.join(',') !== [...publicEvidenceClasses].sort().join(',')) {
    candidateError('candidate bundle 包含未获准的证据类别');
  }
  validateIntegrity(fragments);

  const fragmentByRef = new Map(fragments.map((fragment) => [fragment.evidenceRef, fragment]));
  const claims = requireArray(source.claims, 'candidate bundle.claims').map((claim, index) => validateClaim(claim, index, fragmentByRef));
  requireDistinct(claims.map((claim) => claim.claimId), 'candidate bundle.claims claimId');
  const claimsById = new Map(claims.map((claim) => [claim.claimId, claim]));
  const relations = requireArray(source.relations, 'candidate bundle.relations').map((value, index) => {
    const record = requireRecord(value, `relations[${index}]`);
    requireOnlyKeys(record, `relations[${index}]`, ['leftClaimId', 'rightClaimId', 'relation', 'explanation']);
    const leftClaimId = requireText(record.leftClaimId, `relations[${index}].leftClaimId`);
    const rightClaimId = requireText(record.rightClaimId, `relations[${index}].rightClaimId`);
    const left = claimsById.get(leftClaimId);
    const right = claimsById.get(rightClaimId);
    if (!left || !right) candidateError(`relations[${index}] 引用了未知 claim`);
    const relation = requireEnum(record.relation, relationKinds, `relations[${index}].relation`);
    if (relation !== compareCandidateClaims(left, right)) candidateError(`relations[${index}] 与确定性比较结果不一致`);
    const explanation = requireText(record.explanation, `relations[${index}].explanation`);
    validatePublicText(explanation, `relations[${index}].explanation`);
    return { leftClaimId, rightClaimId, relation, explanation };
  });
  requireDistinct(relations.map((relation) => `${relation.leftClaimId}\u0000${relation.rightClaimId}`), 'candidate bundle.relations');
  if (relations.length !== 3) candidateError('candidate bundle 必须保留三个确定性关系');

  return { schemaVersion: 1, kind: 'GUANYIJIA_REAL_EVIDENCE_CANDIDATE', fragments, claims, relations };
}

// Validate as the module loads and again for every read. Runtime never rereads
// a source snapshot root; it validates the committed generated artifact's
// maintenance-bound source-byte digest, excerpt bytes, and exact relative
// locator before a caller can receive a candidate packet.
const validatedAtModuleLoad = validateBundle();

export function readCandidateEvidenceBundle(): CandidateEvidenceBundle {
  validateBundle();
  return structuredClone(validatedAtModuleLoad);
}
