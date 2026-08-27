import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  readCandidateEvidenceBundle,
  type CandidateClaim,
  type CandidateEvidenceBundle,
  type CandidateEvidenceFragment,
} from './candidate-evidence.ts';
import { generatedCandidateTargetProposal } from './guanyijia-candidate-target.generated.ts';

export type CandidateTargetStatus = 'PENDING_HUMAN_CONFIRMATION';

export type CandidateTarget = {
  targetId: string;
  topic: 'NEGATIVE_STOCK' | 'DOCUMENT_STATUS';
  title: string;
  statement: string;
  status: CandidateTargetStatus;
  evidenceClass: 'GENERATED_TARGET';
  citationRefs: readonly string[];
  affectedClaimIds: readonly string[];
};

export type CandidateTargetProposal = {
  schemaVersion: 1;
  kind: 'GUANYIJIA_GENERATED_TARGET_CANDIDATE';
  markdown: string;
  targets: readonly CandidateTarget[];
  generation: {
    provider: 'CODEX_CHATGPT_SESSION';
    sessionReference: string;
    model: 'gpt-5.6-luna';
    reasoningEffort: 'xhigh';
    promptVersion: 'guanyijia-candidate-target-v1';
    inputDigest: `sha256:${string}`;
    outputDigest: `sha256:${string}`;
  };
};

type JsonRecord = Record<string, unknown>;
type ProposalTopic = CandidateTarget['topic'];

const proposalTopics = ['NEGATIVE_STOCK', 'DOCUMENT_STATUS'] as const;
const digestPattern = /^sha256:[a-f0-9]{64}$/;
const requiredGeneration = {
  provider: 'CODEX_CHATGPT_SESSION',
  sessionReference: 'codex-task:/root/real_evidence_task2_generation',
  model: 'gpt-5.6-luna',
  reasoningEffort: 'xhigh',
  promptVersion: 'guanyijia-candidate-target-v1',
} as const;

// This full-asset pin is intentionally held in runtime code, apart from the
// generated proposal and its self-digests. A maintenance update must review
// this pin together with the frozen public proposal.
const trustedGeneratedProposalDigest = 'sha256:f676687e46337270e21e55dfefcd0510e551ab4df35885c7d773432dba38c4dc';

function proposalError(message: string): never {
  throw new Error(`候选目标提案不可用：${message}`);
}

function requirePlainRecord(value: unknown, label: string): JsonRecord {
  if (
    typeof value !== 'object'
    || value === null
    || Array.isArray(value)
    || Object.getPrototypeOf(value) !== Object.prototype
  ) {
    proposalError(`${label} 必须是普通对象`);
  }
  return value as JsonRecord;
}

function requireExactKeys(record: JsonRecord, label: string, required: readonly string[]) {
  const keys = Object.keys(record);
  if (keys.length !== required.length) proposalError(`${label} 字段形状不匹配`);
  for (const key of required) {
    if (!Object.hasOwn(record, key)) proposalError(`${label} 缺少字段：${key}`);
  }
  for (const key of keys) {
    if (!required.includes(key)) proposalError(`${label} 包含未知字段：${key}`);
  }
}

function requireText(value: unknown, label: string): string {
  if (typeof value !== 'string' || !value.trim()) proposalError(`${label} 必须是非空文本`);
  return value;
}

function requireDigest(value: unknown, label: string): `sha256:${string}` {
  const digest = requireText(value, label);
  if (!digestPattern.test(digest)) proposalError(`${label} 必须是 SHA-256 摘要`);
  return digest as `sha256:${string}`;
}

function requireArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) proposalError(`${label} 必须是数组`);
  return value;
}

function requireDistinct(values: readonly string[], label: string) {
  if (new Set(values).size !== values.length) proposalError(`${label} 不能重复`);
}

function requireTopic(value: unknown, label: string): ProposalTopic {
  if (typeof value !== 'string' || !proposalTopics.includes(value as ProposalTopic)) {
    proposalError(`${label} 不受支持：${String(value)}`);
  }
  return value as ProposalTopic;
}

function canonicalDigest(domain: string, value: unknown): `sha256:${string}` {
  return `sha256:${sha256HexSync(JSON.stringify({ domain, value }))}`;
}

function publicFragmentProjection(fragment: CandidateEvidenceFragment) {
  return {
    evidenceRef: fragment.evidenceRef,
    sourceId: fragment.sourceId,
    snapshotId: fragment.snapshotId,
    topic: fragment.topic,
    evidenceClass: fragment.evidenceClass,
    title: fragment.title,
    excerpt: fragment.excerpt,
    locationLabel: fragment.locationLabel,
    locationValue: fragment.locationValue,
    sourceName: fragment.sourceName,
    artifactDigest: fragment.artifactDigest,
  };
}

function publicClaimProjection(claim: CandidateClaim) {
  return {
    claimId: claim.claimId,
    topic: claim.topic,
    subjectRef: claim.subjectRef,
    predicate: claim.predicate,
    normalizedValue: claim.normalizedValue,
    scope: claim.scope,
    effectiveTime: claim.effectiveTime ?? null,
    evidenceClass: claim.evidenceClass,
    evidenceRefs: [...claim.evidenceRefs].sort(),
  };
}

function inputDigestFor(bundle: CandidateEvidenceBundle): `sha256:${string}` {
  const fragments = bundle.fragments
    .map(publicFragmentProjection)
    .sort((left, right) => left.evidenceRef.localeCompare(right.evidenceRef));
  const claims = bundle.claims
    .map(publicClaimProjection)
    .sort((left, right) => left.claimId.localeCompare(right.claimId));
  const relations = bundle.relations
    .map((relation) => ({
      leftClaimId: relation.leftClaimId,
      rightClaimId: relation.rightClaimId,
      relation: relation.relation,
      explanation: relation.explanation,
    }))
    .sort((left, right) => (
      `${left.leftClaimId}\u0000${left.rightClaimId}`.localeCompare(`${right.leftClaimId}\u0000${right.rightClaimId}`)
    ));

  return canonicalDigest('guanyijia-candidate-target-proposal/input/v1', {
    schemaVersion: bundle.schemaVersion,
    kind: bundle.kind,
    fragments,
    claims,
    relations,
  });
}

function outputDigestFor(proposal: CandidateTargetProposal): `sha256:${string}` {
  return canonicalDigest('guanyijia-candidate-target-proposal/output/v1', {
    schemaVersion: proposal.schemaVersion,
    kind: proposal.kind,
    markdown: proposal.markdown,
    targets: proposal.targets.map((target) => ({
      targetId: target.targetId,
      topic: target.topic,
      title: target.title,
      statement: target.statement,
      status: target.status,
      evidenceClass: target.evidenceClass,
      citationRefs: [...target.citationRefs],
      affectedClaimIds: [...target.affectedClaimIds],
    })),
    generation: {
      provider: proposal.generation.provider,
      sessionReference: proposal.generation.sessionReference,
      model: proposal.generation.model,
      reasoningEffort: proposal.generation.reasoningEffort,
      promptVersion: proposal.generation.promptVersion,
      inputDigest: proposal.generation.inputDigest,
    },
  });
}

function generatedAssetDigest(): `sha256:${string}` {
  return canonicalDigest('guanyijia-candidate-target-proposal/trusted-asset/v1', generatedCandidateTargetProposal);
}

function validateTarget(
  value: unknown,
  index: number,
  fragmentsByRef: ReadonlyMap<string, CandidateEvidenceFragment>,
  claimsById: ReadonlyMap<string, CandidateClaim>,
): CandidateTarget {
  const record = requirePlainRecord(value, `targets[${index}]`);
  requireExactKeys(record, `targets[${index}]`, [
    'targetId', 'topic', 'title', 'statement', 'status', 'evidenceClass', 'citationRefs', 'affectedClaimIds',
  ]);
  const targetId = requireText(record.targetId, `targets[${index}].targetId`);
  const topic = requireTopic(record.topic, `targets[${index}].topic`);
  const title = requireText(record.title, `targets[${index}].title`);
  const statement = requireText(record.statement, `targets[${index}].statement`);
  if (record.status !== 'PENDING_HUMAN_CONFIRMATION') {
    proposalError(`targets[${index}].status 必须为 PENDING_HUMAN_CONFIRMATION`);
  }
  if (record.evidenceClass !== 'GENERATED_TARGET') {
    proposalError(`targets[${index}].evidenceClass 必须为 GENERATED_TARGET`);
  }

  const citationRefs = requireArray(record.citationRefs, `targets[${index}].citationRefs`).map((citation, citationIndex) =>
    requireText(citation, `targets[${index}].citationRefs[${citationIndex}]`));
  if (citationRefs.length === 0) proposalError(`target ${targetId} 缺少公开证据引用`);
  requireDistinct(citationRefs, `targets[${index}].citationRefs`);
  let hasNonGapCitation = false;
  for (const citationRef of citationRefs) {
    const fragment = fragmentsByRef.get(citationRef);
    if (!fragment) proposalError(`target ${targetId} 引用了未知证据：${citationRef}`);
    if (fragment.topic !== topic) proposalError(`target ${targetId} 的证据主题不一致：${citationRef}`);
    if (fragment.evidenceClass !== 'GAP') hasNonGapCitation = true;
  }
  if (!hasNonGapCitation) proposalError(`target ${targetId} 不能只引用 GAP 证据`);

  const affectedClaimIds = requireArray(record.affectedClaimIds, `targets[${index}].affectedClaimIds`).map((claim, claimIndex) =>
    requireText(claim, `targets[${index}].affectedClaimIds[${claimIndex}]`));
  if (affectedClaimIds.length === 0) proposalError(`target ${targetId} 缺少受影响主张`);
  requireDistinct(affectedClaimIds, `targets[${index}].affectedClaimIds`);
  for (const affectedClaimId of affectedClaimIds) {
    const claim = claimsById.get(affectedClaimId);
    if (!claim) proposalError(`target ${targetId} 引用了未知主张：${affectedClaimId}`);
    if (claim.topic !== topic) proposalError(`target ${targetId} 的主张主题不一致：${affectedClaimId}`);
  }

  return {
    targetId,
    topic,
    title,
    statement,
    status: 'PENDING_HUMAN_CONFIRMATION',
    evidenceClass: 'GENERATED_TARGET',
    citationRefs,
    affectedClaimIds,
  };
}

function validateProposal(): CandidateTargetProposal {
  const bundle = readCandidateEvidenceBundle();
  const fragmentsByRef = new Map(bundle.fragments.map((fragment) => [fragment.evidenceRef, fragment]));
  const claimsById = new Map(bundle.claims.map((claim) => [claim.claimId, claim]));

  const source = requirePlainRecord(generatedCandidateTargetProposal, 'generated proposal');
  requireExactKeys(source, 'generated proposal', ['schemaVersion', 'kind', 'markdown', 'targets', 'generation']);
  if (source.schemaVersion !== 1) proposalError('schemaVersion 不受支持');
  if (source.kind !== 'GUANYIJIA_GENERATED_TARGET_CANDIDATE') proposalError('kind 不受支持');
  const markdown = requireText(source.markdown, 'markdown');
  const targets = requireArray(source.targets, 'targets').map((target, index) =>
    validateTarget(target, index, fragmentsByRef, claimsById));
  if (targets.length !== proposalTopics.length) proposalError('targets 必须恰有两个候选');
  if (targets.map((target) => target.topic).join(',') !== proposalTopics.join(',')) {
    proposalError('targets 必须按 NEGATIVE_STOCK、DOCUMENT_STATUS 的顺序出现');
  }
  requireDistinct(targets.map((target) => target.targetId), 'targets targetId');

  const generation = requirePlainRecord(source.generation, 'generation');
  requireExactKeys(generation, 'generation', [
    'provider', 'sessionReference', 'model', 'reasoningEffort', 'promptVersion', 'inputDigest', 'outputDigest',
  ]);
  for (const [key, expected] of Object.entries(requiredGeneration)) {
    if (generation[key] !== expected) proposalError(`generation.${key} 与冻结 Luna 任务 provenance 不匹配`);
  }
  const inputDigest = requireDigest(generation.inputDigest, 'generation.inputDigest');
  const outputDigest = requireDigest(generation.outputDigest, 'generation.outputDigest');

  const proposal: CandidateTargetProposal = {
    schemaVersion: 1,
    kind: 'GUANYIJIA_GENERATED_TARGET_CANDIDATE',
    markdown,
    targets,
    generation: {
      provider: 'CODEX_CHATGPT_SESSION',
      sessionReference: requiredGeneration.sessionReference,
      model: 'gpt-5.6-luna',
      reasoningEffort: 'xhigh',
      promptVersion: 'guanyijia-candidate-target-v1',
      inputDigest,
      outputDigest,
    },
  };

  for (const target of targets) {
    if (!markdown.includes(target.title)) proposalError(`Markdown 缺少目标声明：${target.targetId}`);
  }
  for (const fragment of bundle.fragments) {
    if (!markdown.includes(fragment.sourceName) || !markdown.includes(fragment.locationValue)) {
      proposalError(`Markdown 缺少公开证据声明：${fragment.evidenceRef}`);
    }
  }
  if (!markdown.includes('候选目标制度') || !markdown.includes('待人工确认')) {
    proposalError('Markdown 必须明确标记候选目标制度和待人工确认');
  }

  const expectedInputDigest = inputDigestFor(bundle);
  if (inputDigest !== expectedInputDigest) {
    proposalError(`generation.inputDigest 不匹配；应为 ${expectedInputDigest}`);
  }
  const expectedOutputDigest = outputDigestFor(proposal);
  if (outputDigest !== expectedOutputDigest) {
    proposalError(`generation.outputDigest 不匹配；应为 ${expectedOutputDigest}`);
  }
  if (generatedAssetDigest() !== trustedGeneratedProposalDigest) {
    proposalError(`生成资产与受信任运行时完整性锚点不一致；应为 ${generatedAssetDigest()}`);
  }

  return proposal;
}

const validatedAtModuleLoad = validateProposal();

export function readCandidateTargetProposal(): CandidateTargetProposal {
  validateProposal();
  return structuredClone(validatedAtModuleLoad);
}
