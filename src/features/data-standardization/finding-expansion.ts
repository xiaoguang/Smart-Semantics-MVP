export type FindingExpansionState = string | undefined;

/** Keeps cross-source material local to one currently expanded finding. */
export function toggleFindingExpansion(
  expandedFindingId: FindingExpansionState,
  findingId: string,
): FindingExpansionState {
  return expandedFindingId === findingId ? undefined : findingId;
}
