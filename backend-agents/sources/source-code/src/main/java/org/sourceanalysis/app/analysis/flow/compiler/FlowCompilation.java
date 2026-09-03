package org.sourceanalysis.app.analysis.flow.compiler;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable M1 result before its publisher writes the flow-compilation module artifact. */
public record FlowCompilation(
    FlowCompilationProfile profile,
    List<EntryDisposition> entryDispositions,
    List<FlowSlice> flowSlices,
    List<FlowGap> flowGaps) {

  public FlowCompilation {
    profile = Objects.requireNonNull(profile, "Flow compilation profile");
    entryDispositions = ordered(entryDispositions, EntryDisposition::entryId, "entry dispositions");
    flowSlices = ordered(flowSlices, FlowSlice::flowSliceId, "Flow slices");
    flowGaps = ordered(flowGaps, FlowGap::gapId, "Flow Gaps");
    List<EntryDisposition> orderedDispositions = entryDispositions;
    if (entryDispositions.stream().map(EntryDisposition::entryId).distinct().count()
        != entryDispositions.size()) {
      throw broken();
    }
    List<String> compiledFlowIds =
        entryDispositions.stream()
            .filter(value -> "COMPILED".equals(value.disposition()))
            .map(EntryDisposition::flowSliceId)
            .toList();
    List<String> knownGapIds = flowGaps.stream().map(FlowGap::gapId).toList();
    if (compiledFlowIds.contains(null)
        || compiledFlowIds.size() != flowSlices.size()
        || compiledFlowIds.size() != compiledFlowIds.stream().distinct().count()
        || !hasOneMatchingDispositionPerFlow(flowSlices, entryDispositions)
        || entryDispositions.stream()
            .flatMap(disposition -> disposition.gapIds().stream())
            .anyMatch(gapId -> !knownGapIds.contains(gapId))
        || flowSlices.stream()
            .flatMap(flow -> flow.gapIds().stream())
            .anyMatch(gapId -> !knownGapIds.contains(gapId))
        || flowGaps.stream()
            .flatMap(gap -> gap.affectedEntryIds().stream())
            .anyMatch(
                entryId ->
                    orderedDispositions.stream()
                        .noneMatch(disposition -> disposition.entryId().equals(entryId)))) {
      throw broken();
    }
  }

  /** One complete, unique terminal disposition for an ApplicationDiscovery entry. */
  public record EntryDisposition(
      String entryId,
      String disposition,
      String flowSliceId,
      List<String> gapIds,
      String reasonCode) {

    public EntryDisposition(
        String entryId, String disposition, String flowSliceId, List<String> gapIds) {
      this(entryId, disposition, flowSliceId, gapIds, null);
    }

    public EntryDisposition {
      required(entryId, "entry ID");
      if (!("COMPILED".equals(disposition)
          || "GAP".equals(disposition)
          || "EXCLUDED".equals(disposition))) {
        throw broken();
      }
      gapIds = orderedStrings(gapIds, "entry Gap IDs");
      if (("COMPILED".equals(disposition) != (flowSliceId != null))
          || (!"COMPILED".equals(disposition) && gapIds.isEmpty())
          || ("COMPILED".equals(disposition) != (reasonCode == null))) {
        throw broken();
      }
      if (flowSliceId != null) required(flowSliceId, "Flow slice ID");
      if (reasonCode != null) required(reasonCode, "entry disposition reason");
    }
  }

  /** One explicit reason an entry or a compiled Flow cannot be fully analyzed. */
  public record FlowGap(
      String gapId,
      String scope,
      String reasonCode,
      List<String> affectedEntryIds,
      List<String> evidenceNodeIds) {

    public FlowGap {
      required(gapId, "Flow Gap ID");
      required(scope, "Flow Gap scope");
      required(reasonCode, "Flow Gap reason");
      affectedEntryIds = orderedStrings(affectedEntryIds, "Flow Gap affected entries");
      evidenceNodeIds = orderedStrings(evidenceNodeIds, "Flow Gap evidence IDs");
      if (affectedEntryIds.isEmpty()) throw broken();
    }
  }

  /** One entry-rooted Flow, which may contain several terminal outcomes but never several roots. */
  public record FlowSlice(
      String flowSliceId,
      String entryId,
      String trigger,
      String rootNodeId,
      List<String> sharedSteps,
      List<String> factIds,
      List<String> atomIds,
      List<OutcomePath> outcomePaths,
      List<String> gapIds) {

    public FlowSlice {
      required(flowSliceId, "Flow slice ID");
      required(entryId, "entry ID");
      required(trigger, "Flow trigger");
      required(rootNodeId, "Flow root node ID");
      sharedSteps = List.copyOf(Objects.requireNonNull(sharedSteps, "shared steps"));
      factIds = orderedStrings(factIds, "Fact IDs");
      atomIds = orderedStrings(atomIds, "atom IDs");
      outcomePaths = ordered(outcomePaths, OutcomePath::outcomePathId, "outcome paths");
      gapIds = orderedStrings(gapIds, "Flow Gap IDs");
      if (outcomePaths.isEmpty()) throw broken();
    }
  }

  /** One terminal path inside the owning Flow. */
  public record OutcomePath(
      String outcomePathId,
      List<BranchDecision> decisions,
      String terminalNodeId,
      String terminalKind,
      List<String> terminalFactIds,
      List<String> requiredAtomIds,
      List<String> requiredProofIds) {

    public OutcomePath {
      required(outcomePathId, "outcome path ID");
      decisions = List.copyOf(Objects.requireNonNull(decisions, "branch decisions"));
      required(terminalNodeId, "terminal node ID");
      required(terminalKind, "terminal kind");
      terminalFactIds = orderedStrings(terminalFactIds, "terminal Fact IDs");
      requiredAtomIds = orderedStrings(requiredAtomIds, "required atom IDs");
      requiredProofIds = orderedStrings(requiredProofIds, "required proof IDs");
    }
  }

  /** One explicit control-flow guard and its true/false branch polarity. */
  public record BranchDecision(
      String guardNodeId, String conditionAtomId, String polarity, String normalizedCondition) {

    public BranchDecision {
      required(guardNodeId, "guard node ID");
      required(conditionAtomId, "condition atom ID");
      if (!"TRUE".equals(polarity) && !"FALSE".equals(polarity)) throw broken();
      required(normalizedCondition, "normalized condition");
    }
  }

  private static <T> List<T> ordered(
      List<T> values, java.util.function.Function<T, String> key, String label) {
    Objects.requireNonNull(values, label);
    List<T> ordered = values.stream().sorted(Comparator.comparing(key)).toList();
    if (ordered.size() != ordered.stream().map(key).distinct().count()) throw broken();
    return ordered;
  }

  private static boolean hasOneMatchingDispositionPerFlow(
      List<FlowSlice> flowSlices, List<EntryDisposition> entryDispositions) {
    return flowSlices.stream()
        .allMatch(
            flow ->
                entryDispositions.stream()
                    .anyMatch(
                        disposition ->
                            flow.entryId().equals(disposition.entryId())
                                && flow.flowSliceId().equals(disposition.flowSliceId())));
  }

  private static List<String> orderedStrings(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().peek(value -> required(value, label)).sorted().toList();
    if (ordered.size() != ordered.stream().distinct().count()) throw broken();
    return ordered;
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) throw broken();
  }

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("FLOW_ACCOUNTING_INVARIANT_BROKEN");
  }
}
