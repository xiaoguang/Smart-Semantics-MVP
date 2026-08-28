import type { StandardizationSourceStepStatus } from '../standardization-run/types.ts';
import {
  candidateReviewForConflict,
  candidateReviewForSource,
  type CandidateReviewEvidence,
  type CandidateReviewProjection,
} from './candidate-review-projection.ts';

const sourceSnapshots = {
  guanyijia_mysql: '20260813T032528Z-abb0502c7d79',
  guanyijia_github: '20260813032126Z-5821d0ece9b1',
  guanyijia_official_docs: 'gyjerp-official-docs-20260813T031656Z',
  guanyijia_demo_policy: 'guanyijia-demo-policy-f6c6d209ffe3fd53',
  guanyijia_semantica_demo: 'guanyijia-semantica-demo-ff948845dc5bd778',
} as const;

export function expectedSourceSnapshotId(sourceId: string): string | undefined {
  return sourceSnapshots[sourceId as keyof typeof sourceSnapshots];
}

const admittedStates = new Set<StandardizationSourceStepStatus>([
  'DOCUMENT_READY',
  'REVIEWED',
  'ALIGNED',
  'CONFLICT_BLOCKED',
]);

const topicSources: Readonly<Record<CandidateReviewProjection['topic'], readonly (keyof typeof sourceSnapshots)[]>> = {
  NEGATIVE_STOCK: ['guanyijia_mysql', 'guanyijia_github'],
  DEBT_FIELDS: ['guanyijia_mysql', 'guanyijia_github'],
  DOCUMENT_STATUS: ['guanyijia_mysql', 'guanyijia_github'],
};

const conflictSources: Readonly<Record<string, readonly (keyof typeof sourceSnapshots)[]>> = {
  'gyj-conflict-debt-schema': ['guanyijia_mysql', 'guanyijia_github'],
  'gyj-conflict-negative-stock': ['guanyijia_mysql', 'guanyijia_github', 'guanyijia_demo_policy'],
  'gyj-conflict-status-nine': [
    'guanyijia_mysql',
    'guanyijia_github',
    'guanyijia_official_docs',
    'guanyijia_demo_policy',
  ],
};

const conflictIdByTopic: Readonly<Record<CandidateReviewProjection['topic'], string>> = {
  NEGATIVE_STOCK: 'gyj-conflict-negative-stock',
  DEBT_FIELDS: 'gyj-conflict-debt-schema',
  DOCUMENT_STATUS: 'gyj-conflict-status-nine',
};

export function conflictIdForSourceReviewTopic(topic: CandidateReviewProjection['topic']) {
  return conflictIdByTopic[topic];
}

// This is a display-only excerpt from the immutable V6 content sidecar, not
// a change to the formal candidate bundle. Its complete file digest and
// locator are verified by the source-admission regression test against the
// frozen V6 tree.
const v6NegativeStockCodeEvidence: CandidateReviewEvidence = {
  evidenceRef: 'github:v6:SystemConfigService:getMinusStockFlag',
  sourceId: 'guanyijia_github',
  snapshotId: 'guanyijia-demo-content-v6-20260826',
  evidenceClass: 'FROZEN_RECORD',
  evidenceClassLabel: '固定版本源码节选',
  title: '负库存标记读取',
  excerpt: [
    '    public boolean getMinusStockFlag() throws Exception {',
    '        boolean minusStockFlag = false;',
    '        List<SystemConfig> list = getSystemConfig();',
    '        if(list.size()>0) {',
    '            String flag = list.get(0).getMinusStockFlag();',
    '            if(("1").equals(flag)) {',
    '                minusStockFlag = true;',
    '            }',
    '        }',
    '        return minusStockFlag;',
    '',
  ].join('\n'),
  locationLabel: 'V6 固定版本源码行',
  locationValue: 'jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java:L511-L520',
  sourceName: 'GitHub V6 固定版本源码',
  supportedClaim: '冻结源码读取负库存配置标记。',
  claimId: 'github-negative-stock-control',
  artifactDigest: 'sha256:99d6c27e7705c724c5c2e03aa681ba259ea3e794d55b18627fc90f605baad886',
};

export type AdmittedSource = {
  sourceId: string;
  snapshotId: string;
  status: StandardizationSourceStepStatus;
  /** Formal run facts, distinct from the candidate-evidence adapter. */
  introducedConflictIds?: readonly string[];
  resolvedConflictIds?: readonly string[];
};

export type SourceReviewMatter = {
  stableId: string;
  topic: CandidateReviewProjection['topic'];
  kind: 'FORMAL_CONFLICT' | 'PRELIMINARY_COMPARISON';
  state:
    | 'ACTIONABLE'
    | 'BLOCKED_BY_LOCAL_SUGGESTION'
    | 'BLOCKED_BY_PREVIOUS_CONFLICT'
    | 'WAITING_FOR_SOURCES'
    | 'RESOLVED';
  conflictId?: string;
  finding: CandidateReviewProjection;
};

export type SourceReviewMatterProjection = {
  items: SourceReviewMatter[];
  integrityIssues: Array<{
    conflictId: string;
    reason: 'UNKNOWN_CONFLICT' | 'MISSING_CANDIDATE_PROJECTION';
  }>;
};

export type SourceOnlyReviewProjection = {
  state: 'PENDING' | 'READING' | 'ADMITTED';
  message: string;
};

export type SourceReviewVisibility = {
  sourceDocument: SourceOnlyReviewProjection;
  comparisonFindings: CandidateReviewProjection[];
  matterProjection: SourceReviewMatterProjection;
  actionableConflict?: CandidateReviewProjection;
};

function sourceDocumentProjection(source: AdmittedSource | undefined): SourceOnlyReviewProjection {
  if (!source || source.status === 'PENDING') {
    return { state: 'PENDING', message: '等待按顺序载入' };
  }
  if (source.status === 'READING') {
    return { state: 'READING', message: '正在校验并载入固定快照' };
  }
  return { state: 'ADMITTED', message: '固定快照已载入本次审阅' };
}

function exactAdmittedSources(sources: readonly AdmittedSource[]) {
  const byId = new Map<string, AdmittedSource[]>();
  for (const source of sources) {
    const values = byId.get(source.sourceId) ?? [];
    values.push(source);
    byId.set(source.sourceId, values);
  }

  return new Set(Object.entries(sourceSnapshots).flatMap(([sourceId, snapshotId]) => {
    const matches = byId.get(sourceId) ?? [];
    return matches.length === 1
      && matches[0]!.snapshotId === snapshotId
      && admittedStates.has(matches[0]!.status)
      ? [sourceId]
      : [];
  }));
}

function prerequisitesMet(
  required: readonly (keyof typeof sourceSnapshots)[],
  admitted: ReadonlySet<string>,
) {
  return required.every((sourceId) => admitted.has(sourceId));
}

function withV6NegativeStockCode(projection: CandidateReviewProjection): CandidateReviewProjection {
  if (projection.topic !== 'NEGATIVE_STOCK') return projection;
  return {
    ...projection,
    evidence: [
      ...projection.evidence.filter((evidence) => evidence.sourceId !== 'guanyijia_github'),
      v6NegativeStockCodeEvidence,
    ],
    relationExplanation: 'MySQL 只证明存在 minus_stock_flag 字段；GitHub 固定源码节选证明读取该标记。两者均不足以单独推断负库存政策。',
    reviewGuidance: 'MySQL 只能证明字段存在，GitHub 只能证明读取该标记；是否允许或拦截负库存仍须通过正式决定确认。',
  };
}

function findingsForSource(input: {
  currentSourceId: string;
  admitted: ReadonlySet<string>;
}): CandidateReviewProjection[] {
  if (!input.admitted.has(input.currentSourceId)) return [];
  if (input.currentSourceId === 'guanyijia_semantica_demo') return [];

  const candidateFindings = candidateReviewForSource(input.currentSourceId);
  return candidateFindings.flatMap((finding) => {
    const required = topicSources[finding.topic];
    if (!prerequisitesMet(required, input.admitted)) return [];

    if (finding.target) {
      const targetSources: readonly (keyof typeof sourceSnapshots)[] = finding.topic === 'DOCUMENT_STATUS'
        ? ['guanyijia_mysql', 'guanyijia_github', 'guanyijia_official_docs', 'guanyijia_demo_policy']
        : ['guanyijia_mysql', 'guanyijia_github', 'guanyijia_demo_policy'];
      if (!prerequisitesMet(targetSources, input.admitted)) return [];
    }

    if (input.currentSourceId === 'guanyijia_official_docs') {
      if (!input.admitted.has('guanyijia_official_docs')) return [];
    }

    // A comparison is only useful when the reader can see what each already
    // admitted participant says.  The current-source adapter is still used
    // for eligibility and target-policy visibility; the conflict projection
    // supplies the complete evidence pair and we strictly trim it to sources
    // that this run has already admitted.
    const complete = candidateReviewForConflict(conflictIdByTopic[finding.topic]);
    const evidence = complete?.evidence.filter((item) => input.admitted.has(item.sourceId)) ?? finding.evidence;
    return evidence.length ? [withV6NegativeStockCode({ ...finding, evidence })] : [];
  });
}

/**
 * Projects comparisons for the admitted run rather than for the document the
 * reader happens to have open.  The source-specific projection remains the
 * evidence adapter: later admitted sources replace an earlier, partial view
 * of the same topic with the most complete exact-source view.
 */
export function projectSourceReviewMatters(input: {
  sources: readonly AdmittedSource[];
}): CandidateReviewProjection[] {
  const admitted = exactAdmittedSources(input.sources);
  const byTopic = new Map<CandidateReviewProjection['topic'], CandidateReviewProjection>();
  for (const source of input.sources) {
    for (const finding of findingsForSource({
      currentSourceId: source.sourceId,
      admitted,
    })) {
      byTopic.set(finding.topic, finding);
    }
  }
  return [...byTopic.values()];
}

const formalConflictOrder = [
  'gyj-conflict-debt-schema',
  'gyj-conflict-negative-stock',
  'gyj-conflict-status-nine',
] as const;

function compareFormalConflictIds(left: string, right: string) {
  const leftIndex = formalConflictOrder.indexOf(left as typeof formalConflictOrder[number]);
  const rightIndex = formalConflictOrder.indexOf(right as typeof formalConflictOrder[number]);
  const normalizedLeft = leftIndex < 0 ? Number.MAX_SAFE_INTEGER : leftIndex;
  const normalizedRight = rightIndex < 0 ? Number.MAX_SAFE_INTEGER : rightIndex;
  return normalizedLeft - normalizedRight || left.localeCompare(right);
}

function exactProjectionForConflict(input: {
  conflictId: string;
  admitted: ReadonlySet<string>;
}): CandidateReviewProjection | undefined {
  const candidate = candidateReviewForConflict(input.conflictId);
  if (!candidate) return undefined;
  const evidence = candidate.evidence.filter((item) => input.admitted.has(item.sourceId));
  return evidence.length ? withV6NegativeStockCode({ ...candidate, evidence }) : undefined;
}

/**
 * Formal conflict membership belongs to the run, not to whichever source is
 * currently visible. Candidate records enrich a known conflict with cards and
 * exact evidence; they must never decide whether an introduced conflict is
 * displayed. This keeps the timeline, review items and conflict layer in
 * lock-step even after the reader moves on to another source.
 */
export function projectSourceReviewMatterProjection(input: {
  sources: readonly AdmittedSource[];
  currentConflictId?: string;
  localSuggestionPending?: boolean;
}): SourceReviewMatterProjection {
  const admitted = exactAdmittedSources(input.sources);
  const comparisons = projectSourceReviewMatters({ sources: input.sources });
  const comparisonByConflictId = new Map(comparisons.map((finding) => [
    conflictIdForSourceReviewTopic(finding.topic), finding,
  ]));
  const introduced = new Set<string>();
  const resolved = new Set<string>();
  for (const source of input.sources) {
    for (const conflictId of source.introducedConflictIds ?? []) introduced.add(conflictId);
    for (const conflictId of source.resolvedConflictIds ?? []) resolved.add(conflictId);
  }

  const integrityIssues: SourceReviewMatterProjection['integrityIssues'] = [];
  const formal = [...introduced]
    .sort(compareFormalConflictIds)
    .flatMap((conflictId): SourceReviewMatter[] => {
      const candidate = candidateReviewForConflict(conflictId);
      if (!candidate) {
        integrityIssues.push({ conflictId, reason: 'UNKNOWN_CONFLICT' });
        return [];
      }
      const finding = exactProjectionForConflict({ conflictId, admitted })
        ?? comparisonByConflictId.get(conflictId);
      if (!finding) {
        integrityIssues.push({ conflictId, reason: 'MISSING_CANDIDATE_PROJECTION' });
        return [];
      }
      const state: SourceReviewMatter['state'] = resolved.has(conflictId)
        ? 'RESOLVED'
        : input.localSuggestionPending
          ? 'BLOCKED_BY_LOCAL_SUGGESTION'
          : input.currentConflictId === conflictId
            ? 'ACTIONABLE'
            : 'BLOCKED_BY_PREVIOUS_CONFLICT';
      return [{
        stableId: `conflict:${conflictId}`,
        topic: finding.topic,
        kind: 'FORMAL_CONFLICT',
        state,
        conflictId,
        finding,
      }];
    });

  const preliminary = comparisons
    .filter((finding) => !introduced.has(conflictIdForSourceReviewTopic(finding.topic)))
    .map((finding): SourceReviewMatter => ({
      stableId: `comparison:${finding.topic}`,
      topic: finding.topic,
      kind: 'PRELIMINARY_COMPARISON',
      state: 'WAITING_FOR_SOURCES',
      finding,
    }));

  return { items: [...formal, ...preliminary], integrityIssues };
}

/**
 * The only bridge from immutable candidate evidence to a live five-source
 * run. A snapshot being bundled locally is merely AVAILABLE; it becomes
 * visible in comparisons only once its exact source identity is ADMITTED.
 */
export function projectSourceReviewVisibility(input: {
  currentSourceId: string;
  sources: readonly AdmittedSource[];
  currentConflictId?: string;
  localSuggestionPending?: boolean;
}): SourceReviewVisibility {
  const current = input.sources.find((source) => source.sourceId === input.currentSourceId);
  const admitted = exactAdmittedSources(input.sources);
  const sourceDocument = sourceDocumentProjection(current);
  const comparisonFindings = projectSourceReviewMatters({ sources: input.sources });
  const matterProjection = projectSourceReviewMatterProjection({
    sources: input.sources,
    ...(input.currentConflictId ? { currentConflictId: input.currentConflictId } : {}),
    ...(input.localSuggestionPending ? { localSuggestionPending: true } : {}),
  });

  const conflictPrerequisites = input.currentConflictId
    ? conflictSources[input.currentConflictId]
    : undefined;
  const actionableConflict = current !== undefined && admittedStates.has(current.status)
    && input.currentConflictId
    && conflictPrerequisites
    && prerequisitesMet(conflictPrerequisites, admitted)
    ? (() => {
      const projection = candidateReviewForConflict(input.currentConflictId);
      return projection ? withV6NegativeStockCode(projection) : undefined;
    })()
    : undefined;

  return structuredClone({ sourceDocument, comparisonFindings, matterProjection, actionableConflict });
}
