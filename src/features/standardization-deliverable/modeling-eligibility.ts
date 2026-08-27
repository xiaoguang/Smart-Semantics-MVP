import { compileModelingDocument, type ModelingDocumentCandidateItem } from '../modeling-document-bridge/compile-modeling-document.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import type { ModelingDocumentArtifact } from '../modeling-document-bridge/types.ts';

/**
 * Eligibility belongs to the standardization handoff, rather than to the
 * Markdown reader. This prevents a later modeling step from inferring safety
 * from prose such as "待确认".
 */
export type ModelingEligibility = 'ELIGIBLE' | 'EXCLUDED_GAP' | 'EXCLUDED_UNCERTAIN';

export type StandardizationConclusion = {
  conclusionId: string;
  title: string;
  statement: string;
  evidenceRefs: string[];
  eligibility: ModelingEligibility;
  exclusionReason?: string;
  markdownAnchor: string;
  candidateId?: string;
};

export type StandardizationModelingEligibilityProjection = {
  schemaVersion: 1;
  artifactId: string;
  artifactMarkdownSha256: string;
  conclusions: StandardizationConclusion[];
  eligibleCandidateIds: string[];
  excludedCandidateIds: string[];
};

function anchorFor(item: ModelingDocumentCandidateItem) {
  const byKind: Record<ModelingDocumentCandidateItem['kind'], string> = {
    ENTITY: 'business-objects', EVENT: 'business-activities', FIELD: 'fields-and-dimensions',
    RELATION: 'object-relations', DIMENSION: 'fields-and-dimensions', METRIC: 'metrics',
    HIERARCHY: 'object-relations', RULE: 'business-activities', ALIAS: 'business-objects',
    TIME_RULE: 'metrics', PENDING_ASSET: 'unresolved-items',
  };
  return `#${byKind[item.kind]}`;
}

function pendingObjectCode(code: string) {
  const match = code.match(/^(?:deferred|candidate):[^:]+:(.+)$/);
  return match?.[1] ?? code;
}

function stableStrings(values: Iterable<string>) {
  return [...new Set(values)].sort((left, right) => left.localeCompare(right, 'en'));
}

export function projectModelingEligibility(
  artifact: ModelingDocumentArtifact,
): StandardizationModelingEligibilityProjection {
  if (!artifact.semanticPayload) throw new Error('标准化文档缺少结构化建模内容');
  const candidate = compileModelingDocument(artifact);
  const payload = artifact.semanticPayload.data;
  const excludedReasons = new Map(payload.exclusions.map((item) => [item.code, item.reason]));
  const uncertainReasons = new Map(payload.pendingAssets.map((item) => [
    pendingObjectCode(item.code), item.reason,
  ]));

  const conclusions: StandardizationConclusion[] = candidate.items.map((item) => {
    const code = item.metadata?.code;
    const gapReason = code ? excludedReasons.get(code) : undefined;
    const uncertainReason = item.kind === 'PENDING_ASSET'
      ? item.summary
      : code ? uncertainReasons.get(code) : undefined;
    const eligibility: ModelingEligibility = gapReason
      ? 'EXCLUDED_GAP'
      : uncertainReason ? 'EXCLUDED_UNCERTAIN'
      : 'ELIGIBLE';
    return {
      conclusionId: `conclusion:${item.candidateId}`,
      title: item.name,
      statement: item.summary,
      evidenceRefs: stableStrings(item.evidenceRefs),
      eligibility,
      ...(eligibility === 'ELIGIBLE' ? {} : { exclusionReason: gapReason ?? uncertainReason }),
      markdownAnchor: anchorFor(item),
      candidateId: item.candidateId,
    } satisfies StandardizationConclusion;
  });
  const representedCodes = new Set(candidate.items.map((item) => item.metadata?.code).filter(Boolean));
  for (const item of payload.exclusions) {
    if (representedCodes.has(item.code)) continue;
    conclusions.push({
      conclusionId: `conclusion:gap:${item.code}`,
      title: item.name,
      statement: item.reason,
      evidenceRefs: stableStrings(item.evidenceRefs),
      eligibility: 'EXCLUDED_GAP',
      exclusionReason: item.reason,
      markdownAnchor: '#unresolved-items',
    });
  }

  const eligibleCandidateIds = stableStrings(conclusions
    .filter((item) => item.eligibility === 'ELIGIBLE' && item.candidateId)
    .map((item) => item.candidateId!));
  const excludedCandidateIds = stableStrings(conclusions
    .filter((item) => item.eligibility !== 'ELIGIBLE' && item.candidateId)
    .map((item) => item.candidateId!));
  return {
    schemaVersion: 1,
    artifactId: artifact.artifactId,
    artifactMarkdownSha256: artifact.markdown.sha256,
    conclusions,
    eligibleCandidateIds,
    excludedCandidateIds,
  };
}

export function assertModelingEligibilityProjection(
  projection: StandardizationModelingEligibilityProjection,
  artifact: ModelingDocumentArtifact,
) {
  const expected = projectModelingEligibility(artifact);
  if (canonicalModelingJson(projection) !== canonicalModelingJson(expected)) {
    throw new Error('可建模结论与排除清单不能从当前标准化文档复算');
  }
}
