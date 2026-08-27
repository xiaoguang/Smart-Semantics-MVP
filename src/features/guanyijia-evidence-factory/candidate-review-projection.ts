import {
  readCandidateEvidenceBundle,
  type CandidateClaim,
  type CandidateEvidenceBundle,
  type CandidateEvidenceFragment,
} from './candidate-evidence.ts';
import {
  readCandidateTargetProposal,
  type CandidateTargetProposal,
} from './candidate-target-proposal.ts';

/** The three topics admitted to the public candidate packet. */
export type CandidateReviewTopic = 'NEGATIVE_STOCK' | 'DEBT_FIELDS' | 'DOCUMENT_STATUS';

export type CandidateReviewEvidence = {
  evidenceRef: string;
  sourceId: string;
  snapshotId: string;
  evidenceClass: 'OBSERVED' | 'FROZEN_RECORD' | 'GAP';
  evidenceClassLabel: string;
  title: string;
  excerpt: string;
  locationLabel: string;
  locationValue: string;
  sourceName: string;
  supportedClaim: string;
  /** Shown only in the consuming disclosure. */
  claimId: string;
  /** Shown only in the consuming disclosure. */
  artifactDigest: `sha256:${string}`;
};

export type CandidateReviewTarget = {
  title: string;
  statement: string;
  statusLabel: '待人工确认';
  markdown: string;
  /** Generated target is deliberately separate from observed evidence. */
  evidenceClass: 'GENERATED_TARGET';
  /** Shown only in the consuming disclosure. */
  targetId: string;
  /** Shown only in the consuming disclosure. */
  citationRefs: string[];
  /** Shown only in the consuming disclosure. */
  generationOutputDigest: `sha256:${string}`;
};

export type CandidateReviewProjection = {
  topic: CandidateReviewTopic;
  heading: string;
  relation: 'COMPLEMENTS' | 'CORROBORATES' | 'CONFLICTS' | 'TEMPORAL_DRIFT' | 'UNSUPPORTED';
  relationLabel: string;
  relationExplanation: string;
  evidence: CandidateReviewEvidence[];
  target?: CandidateReviewTarget;
  reviewGuidance: string;
};

type TopicDefinition = {
  heading: string;
  reviewGuidance: string;
  claimText: Record<string, string>;
};

const topicDefinitions: Record<CandidateReviewTopic, TopicDefinition> = {
  NEGATIVE_STOCK: {
    heading: '负库存配置',
    reviewGuidance: '两处片段只说明存在配置控制字段；候选目标制度仍需人工确认，不能当作当前生产政策。',
    claimText: {
      'mysql-negative-stock-control': '存在负库存配置控制字段。',
      'github-negative-stock-control': '冻结结构化记录也记载同一负库存配置控制字段。',
    },
  },
  DEBT_FIELDS: {
    heading: '欠款字段',
    reviewGuidance: '保留已部署结构与冻结迁移记录之间的字段差异，必须通过既有冲突决定流程处理。',
    claimText: {
      'mysql-debt-fields-absent': '已部署单据结构未见 debt 与 last_debt 字段。',
      'github-debt-fields-present': '冻结迁移记录声明了 debt 与 last_debt 字段。',
    },
  },
  DOCUMENT_STATUS: {
    heading: '单据状态',
    reviewGuidance: '已部署快照与历史记录来自不同版本；状态 9 的业务含义仍缺少说明，不能自行补写。',
    claimText: {
      'mysql-document-status-current': '已部署快照记录状态代码 0、1、2、3、9。',
      'github-document-status-historical': '冻结历史记录只描述状态代码 0、1、2。',
      'official-document-status-9-gap': '完整官方文本未被保留，状态 9 的官方含义未知。',
    },
  },
};

const evidenceClassLabels: Record<CandidateReviewEvidence['evidenceClass'], string> = {
  OBSERVED: '已保存观察',
  FROZEN_RECORD: '冻结结构化记录',
  GAP: '资料缺口',
};

const relationLabels: Record<CandidateReviewProjection['relation'], string> = {
  COMPLEMENTS: '互补资料',
  CORROBORATES: '互证',
  CONFLICTS: '结构差异',
  TEMPORAL_DRIFT: '不同版本记录不一致',
  UNSUPPORTED: '资料缺口',
};

const sourceTopics: Readonly<Record<string, readonly CandidateReviewTopic[]>> = {
  guanyijia_mysql: ['NEGATIVE_STOCK', 'DEBT_FIELDS', 'DOCUMENT_STATUS'],
  guanyijia_github: ['NEGATIVE_STOCK', 'DEBT_FIELDS', 'DOCUMENT_STATUS'],
  // The current story uses the plural identifier. The singular value remains
  // accepted for persisted/older navigation targets and has the same narrow
  // public projection.
  guanyijia_official_docs: ['DOCUMENT_STATUS'],
  guanyijia_official_document: ['DOCUMENT_STATUS'],
  guanyijia_demo_policy: ['NEGATIVE_STOCK', 'DOCUMENT_STATUS'],
};

const conflictTopics: Readonly<Record<string, CandidateReviewTopic>> = {
  'gyj-conflict-negative-stock': 'NEGATIVE_STOCK',
  'gyj-conflict-debt-schema': 'DEBT_FIELDS',
  'gyj-conflict-status-nine': 'DOCUMENT_STATUS',
};

const blockTopics: Readonly<Record<string, CandidateReviewTopic>> = {
  'schema.jsh_depot_head.debt_fields': 'DEBT_FIELDS',
  'gyj-block:schema.jsh_depot_head.debt_fields': 'DEBT_FIELDS',
  'rule.negative_stock': 'NEGATIVE_STOCK',
  'gyj-block:rule.negative_stock': 'NEGATIVE_STOCK',
  'dimension.document_status.nine': 'DOCUMENT_STATUS',
  'gyj-block:dimension.document_status.nine': 'DOCUMENT_STATUS',
  'derived.rule.negative_stock': 'NEGATIVE_STOCK',
  'gyj-block:derived.rule.negative_stock': 'NEGATIVE_STOCK',
  'resolution.gap.negative_stock_policy_not_implemented': 'NEGATIVE_STOCK',
  'gyj-resolution-block:gyj-conflict-negative-stock:gap': 'NEGATIVE_STOCK',
  'resolution.gap.debt_schema': 'DEBT_FIELDS',
  'gyj-resolution-block:gyj-conflict-debt-schema:gap': 'DEBT_FIELDS',
  'derived.dimension.document_status.nine': 'DOCUMENT_STATUS',
  'gyj-block:derived.dimension.document_status.nine': 'DOCUMENT_STATUS',
  'resolution.gap.status_nine_unconfirmed': 'DOCUMENT_STATUS',
  'gyj-resolution-block:gyj-conflict-status-nine:gap': 'DOCUMENT_STATUS',
};

function projectionError(message: string): never {
  throw new Error(`候选审阅投影不可用：${message}`);
}

function topicRelation(
  bundle: CandidateEvidenceBundle,
  topic: CandidateReviewTopic,
): { relation: CandidateReviewProjection['relation']; explanation: string } {
  const claims = new Map(bundle.claims.map((claim) => [claim.claimId, claim]));
  const matches = bundle.relations.filter((relation) => (
    claims.get(relation.leftClaimId)?.topic === topic
      && claims.get(relation.rightClaimId)?.topic === topic
  ));
  if (matches.length !== 1) projectionError(`${topic} 必须恰有一条已验证的关系`);
  const relation = matches[0]!;
  if (!['COMPLEMENTS', 'CORROBORATES', 'CONFLICTS', 'TEMPORAL_DRIFT', 'UNSUPPORTED'].includes(relation.relation)) {
    projectionError(`${topic} 关系不适用于候选审阅：${relation.relation}`);
  }
  return {
    relation: relation.relation as CandidateReviewProjection['relation'],
    explanation: relation.explanation,
  };
}

function evidenceForTopic(
  bundle: CandidateEvidenceBundle,
  topic: CandidateReviewTopic,
  fragmentFilter?: (fragment: CandidateEvidenceFragment) => boolean,
): CandidateReviewEvidence[] {
  const definition = topicDefinitions[topic];
  const claimsByEvidenceRef = new Map<string, CandidateClaim>();
  for (const claim of bundle.claims) {
    for (const evidenceRef of claim.evidenceRefs) claimsByEvidenceRef.set(evidenceRef, claim);
  }
  const selected = bundle.fragments.filter((fragment) => (
    fragment.topic === topic && (!fragmentFilter || fragmentFilter(fragment))
  ));
  if (!selected.length) projectionError(`${topic} 缺少可显示的公开证据`);
  return selected.map((fragment) => {
    const claim = claimsByEvidenceRef.get(fragment.evidenceRef);
    if (!claim || claim.topic !== topic) projectionError(`${fragment.evidenceRef} 缺少同主题主张`);
    const supportedClaim = definition.claimText[claim.claimId];
    if (!supportedClaim) projectionError(`${claim.claimId} 缺少可读主张映射`);
    if (fragment.evidenceClass === 'GENERATED_TARGET' || fragment.evidenceClass === 'DERIVED') {
      projectionError(`${fragment.evidenceRef} 不能作为公开候选证据`);
    }
    return {
      evidenceRef: fragment.evidenceRef,
      sourceId: fragment.sourceId,
      snapshotId: fragment.snapshotId,
      evidenceClass: fragment.evidenceClass,
      evidenceClassLabel: evidenceClassLabels[fragment.evidenceClass],
      title: fragment.title,
      excerpt: fragment.excerpt,
      locationLabel: fragment.locationLabel,
      locationValue: fragment.locationValue,
      sourceName: fragment.sourceName,
      supportedClaim,
      claimId: claim.claimId,
      artifactDigest: fragment.artifactDigest,
    };
  });
}

function targetForTopic(
  proposal: CandidateTargetProposal,
  topic: CandidateReviewTopic,
): CandidateReviewTarget | undefined {
  const target = proposal.targets.find((candidate) => candidate.topic === topic);
  if (!target) return undefined;
  return {
    title: target.title,
    statement: target.statement,
    statusLabel: '待人工确认',
    markdown: proposal.markdown,
    evidenceClass: 'GENERATED_TARGET',
    targetId: target.targetId,
    citationRefs: [...target.citationRefs],
    generationOutputDigest: proposal.generation.outputDigest,
  };
}

function buildProjection(input: {
  topic: CandidateReviewTopic;
  bundle: CandidateEvidenceBundle;
  proposal: CandidateTargetProposal;
  fragmentFilter?: (fragment: CandidateEvidenceFragment) => boolean;
  includeTarget?: boolean;
}): CandidateReviewProjection {
  const definition = topicDefinitions[input.topic];
  const relation = topicRelation(input.bundle, input.topic);
  return {
    topic: input.topic,
    heading: definition.heading,
    relation: relation.relation,
    relationLabel: relationLabels[relation.relation],
    relationExplanation: relation.explanation,
    evidence: evidenceForTopic(input.bundle, input.topic, input.fragmentFilter),
    ...(input.includeTarget ? (() => {
      const target = targetForTopic(input.proposal, input.topic);
      return target ? { target } : {};
    })() : {}),
    reviewGuidance: definition.reviewGuidance,
  };
}

function candidateInputs() {
  // Each public read validates its frozen asset again. This deliberately keeps
  // a corrupt or mutated candidate from being projected as a partial UI view.
  return {
    bundle: readCandidateEvidenceBundle(),
    proposal: readCandidateTargetProposal(),
  };
}

function sourceFragmentFilter(sourceId: string, fragment: CandidateEvidenceFragment) {
  if (sourceId === 'guanyijia_mysql') return fragment.evidenceClass === 'OBSERVED';
  if (sourceId === 'guanyijia_github') return fragment.evidenceClass === 'FROZEN_RECORD';
  if (sourceId === 'guanyijia_official_docs' || sourceId === 'guanyijia_official_document') {
    return fragment.evidenceClass === 'GAP';
  }
  return true;
}

function targetForSource(topic: CandidateReviewTopic, sourceId: string) {
  return sourceId === 'guanyijia_demo_policy' && (topic === 'NEGATIVE_STOCK' || topic === 'DOCUMENT_STATUS');
}

function fragmentFilterForSource(
  sourceId: string,
  topic: CandidateReviewTopic,
  proposal: CandidateTargetProposal,
) {
  if (sourceId === 'guanyijia_demo_policy') {
    const target = proposal.targets.find((candidate) => candidate.topic === topic);
    if (!target) projectionError(`${sourceId}/${topic} 缺少候选目标引用范围`);
    return (fragment: CandidateEvidenceFragment) => target.citationRefs.includes(fragment.evidenceRef);
  }
  return (fragment: CandidateEvidenceFragment) => sourceFragmentFilter(sourceId, fragment);
}

function sourceRelationContext(sourceId: string, projection: CandidateReviewProjection) {
  if (sourceId !== 'guanyijia_official_docs' && sourceId !== 'guanyijia_official_document') return projection;
  return {
    ...projection,
    relation: 'UNSUPPORTED' as const,
    relationLabel: relationLabels.UNSUPPORTED,
    relationExplanation: '完整官方文本未被保留；状态 9 的官方含义保持资料缺口。',
    reviewGuidance: '完整官方文本未被保留；状态 9 的官方含义保持资料缺口，不能补写。',
  };
}

/**
 * Projects only the public candidate fragments applicable to a source. The
 * formal source document remains untouched; this is a read-only companion.
 */
export function candidateReviewForSource(sourceId: string): CandidateReviewProjection[] {
  const topics = sourceTopics[sourceId];
  if (!topics) return [];
  const { bundle, proposal } = candidateInputs();
  return structuredClone(topics.map((topic) => sourceRelationContext(sourceId, buildProjection({
    topic,
    bundle,
    proposal,
    fragmentFilter: fragmentFilterForSource(sourceId, topic, proposal),
    includeTarget: targetForSource(topic, sourceId),
  }))));
}

/** Projects the complete public evidence set for the existing conflict hunk. */
export function candidateReviewForConflict(conflictId: string): CandidateReviewProjection | undefined {
  const topic = conflictTopics[conflictId];
  if (!topic) return undefined;
  const { bundle, proposal } = candidateInputs();
  return structuredClone(buildProjection({
    topic,
    bundle,
    proposal,
    includeTarget: topic !== 'DEBT_FIELDS',
  }));
}

/**
 * Projects a candidate topic for formal source-document block navigation. It
 * accepts both stable codes and their current `gyj-block:` identities.
 */
export function candidateReviewTopicForBlock(blockId: string): CandidateReviewProjection | undefined {
  const topic = blockTopics[blockId];
  if (!topic) return undefined;
  const { bundle, proposal } = candidateInputs();
  return structuredClone(buildProjection({
    topic,
    bundle,
    proposal,
    includeTarget: topic !== 'DEBT_FIELDS',
  }));
}

/**
 * Source Map follows the latest local navigation action. A candidate fragment
 * selection is therefore authoritative; the selected document block is only a
 * fallback when the user has not selected a candidate fragment.
 */
export function candidateReviewForSourceMapSelection(input: {
  selected?: CandidateReviewProjection;
  mappedBlock?: CandidateReviewProjection;
}): CandidateReviewProjection | undefined {
  return input.selected ?? input.mappedBlock;
}
