import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import type { ModelingDocumentSection } from '../modeling-document-bridge/types.ts';

export type ReviewPrimaryAction =
  | {
      kind: 'OPEN_PENDING_CLAIM';
      label: string;
      claimId: string;
      section: number;
    }
  | {
      kind: 'COMPLETE_REVIEW';
      label: string;
    };

/** Builds the one visible next step for a source review header. */
export function projectReviewPrimaryAction(input: {
  sourceName: string;
  pendingClaimIds: readonly string[];
  claimSections: ReadonlyMap<string, number>;
}): ReviewPrimaryAction {
  const claimId = input.pendingClaimIds[0];
  if (claimId) {
    const section = input.claimSections.get(claimId);
    if (!section) throw new Error('待核对结论缺少章节定位');
    return {
      kind: 'OPEN_PENDING_CLAIM',
      label: `核对 ${input.pendingClaimIds.length} 项建议`,
      claimId,
      section,
    };
  }
  return { kind: 'COMPLETE_REVIEW', label: `完成${input.sourceName}审阅` };
}

/**
 * Keeps a reader on their last section when it contains visible review results.
 * Metadata-only sections intentionally redirect to the next actionable result.
 */
export function resolveReviewResultSection(input: {
  selectedSection?: ModelingDocumentSection;
  claims: readonly { claimId: string; section: number }[];
  pendingClaimIds: readonly string[];
}): ModelingDocumentSection | undefined {
  const selectedIndex = standardSectionOrder.findIndex((section) => section.key === input.selectedSection);
  if (selectedIndex >= 0 && input.claims.some((claim) => claim.section === selectedIndex + 1)) {
    return input.selectedSection;
  }

  const pending = input.pendingClaimIds
    .map((claimId) => input.claims.find((claim) => claim.claimId === claimId))
    .find((claim): claim is { claimId: string; section: number } => Boolean(claim));
  const next = pending ?? input.claims[0];
  return next ? standardSectionOrder[next.section - 1]?.key : undefined;
}
