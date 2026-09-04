package org.sourceanalysis.app.analysis.interpretation.registry;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Exact denominator accounting for the R0 results that were frozen into one registry. */
public record RegistryProposalAccounting(
    List<String> eligibleFlowSliceIds,
    List<String> readyFlowSliceIds,
    List<String> gapFlowSliceIds,
    List<String> failedFlowSliceIds,
    List<String> acceptedRegistryProposalIds,
    List<String> repositoryInterpretationRegistryItemIds) {

  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(value -> value.getBytes(StandardCharsets.UTF_8), RegistryProposalAccounting::compare);

  public RegistryProposalAccounting {
    eligibleFlowSliceIds = ordered(eligibleFlowSliceIds, "eligible Flow IDs");
    readyFlowSliceIds = ordered(readyFlowSliceIds, "ready Flow IDs");
    gapFlowSliceIds = ordered(gapFlowSliceIds, "Gap Flow IDs");
    failedFlowSliceIds = ordered(failedFlowSliceIds, "failed Flow IDs");
    acceptedRegistryProposalIds = ordered(acceptedRegistryProposalIds, "accepted proposal IDs");
    repositoryInterpretationRegistryItemIds =
        ordered(repositoryInterpretationRegistryItemIds, "registry item IDs");
    List<String> partition =
        java.util.stream.Stream.of(readyFlowSliceIds, gapFlowSliceIds, failedFlowSliceIds)
            .flatMap(List::stream)
            .sorted(UTF8_ORDER)
            .toList();
    if (!eligibleFlowSliceIds.equals(partition)
        || acceptedRegistryProposalIds.size() != repositoryInterpretationRegistryItemIds.size()) {
      throw new IllegalArgumentException("registry proposal accounting is not closed");
    }
  }

  private static List<String> ordered(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (ordered.stream().anyMatch(value -> value == null || value.isBlank())
        || ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be unique and nonblank");
    }
    return ordered;
  }

  private static int compare(byte[] left, byte[] right) {
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison = Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(left.length, right.length);
  }
}
