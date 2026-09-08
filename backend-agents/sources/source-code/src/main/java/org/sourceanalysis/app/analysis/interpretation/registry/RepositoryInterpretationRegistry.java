package org.sourceanalysis.app.analysis.interpretation.registry;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** The one finite-key registry frozen after every eligible Flow has an R0 disposition. */
public record RepositoryInterpretationRegistry(
    String repositoryInterpretationRegistryId,
    AnalysisStepPublicationReference businessFlowsPublicationRef,
    List<String> eligibleFlowSliceIds,
    List<RepositoryInterpretationRegistryItem> items,
    List<RepositoryInterpretationRegistryFlowDisposition> flowDispositions,
    RegistryProposalAccounting proposalAccounting,
    boolean closed) {

  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(
          value -> value.getBytes(StandardCharsets.UTF_8),
          RepositoryInterpretationRegistry::compare);

  public RepositoryInterpretationRegistry {
    required(repositoryInterpretationRegistryId, "repository registry ID");
    Objects.requireNonNull(businessFlowsPublicationRef, "business Flows publication reference");
    eligibleFlowSliceIds = orderedStrings(eligibleFlowSliceIds, "eligible Flow IDs");
    items = ordered(items, RepositoryInterpretationRegistryItem::provisionalKey, "registry items");
    flowDispositions =
        ordered(
            flowDispositions,
            RepositoryInterpretationRegistryFlowDisposition::flowSliceId,
            "registry Flow dispositions");
    Objects.requireNonNull(proposalAccounting, "proposal accounting");
    if (!closed
        || !eligibleFlowSliceIds.equals(
            flowDispositions.stream()
                .map(RepositoryInterpretationRegistryFlowDisposition::flowSliceId)
                .toList())
        || !eligibleFlowSliceIds.equals(proposalAccounting.eligibleFlowSliceIds())
        || !proposalAccounting
            .repositoryInterpretationRegistryItemIds()
            .equals(
                items.stream()
                    .map(RepositoryInterpretationRegistryItem::provisionalKey)
                    .toList())) {
      throw new IllegalArgumentException("repository interpretation registry is not closed");
    }
  }

  private static <T> List<T> ordered(
      List<T> values, java.util.function.Function<T, String> key, String label) {
    Objects.requireNonNull(values, label);
    List<T> ordered = values.stream().sorted(Comparator.comparing(key, UTF8_ORDER)).toList();
    if (ordered.size() != ordered.stream().map(key).distinct().count()) {
      throw new IllegalArgumentException(label + " must be unique");
    }
    return ordered;
  }

  private static List<String> orderedStrings(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (ordered.stream().anyMatch(value -> value == null || value.isBlank())
        || ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be unique and nonblank");
    }
    return ordered;
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required");
  }

  private static int compare(byte[] left, byte[] right) {
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(left.length, right.length);
  }
}
