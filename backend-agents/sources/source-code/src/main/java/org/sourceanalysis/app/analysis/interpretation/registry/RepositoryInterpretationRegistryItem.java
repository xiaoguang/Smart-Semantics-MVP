package org.sourceanalysis.app.analysis.interpretation.registry;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** One R0-approved, Flow-scoped key that later R1/R2 work may select verbatim. */
public record RepositoryInterpretationRegistryItem(
    String provisionalKey,
    String registryProposalId,
    String flowSliceId,
    String evidenceCapsuleId,
    String proposalKind,
    String normalizedLabel,
    String normalizedPurpose,
    List<String> basisAtomIds,
    List<String> basisGapIds,
    String sourceSeedKey) {

  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(value -> value.getBytes(StandardCharsets.UTF_8), RepositoryInterpretationRegistryItem::compare);

  public RepositoryInterpretationRegistryItem {
    required(provisionalKey, "provisional key");
    required(registryProposalId, "registry proposal ID");
    required(flowSliceId, "Flow slice ID");
    required(evidenceCapsuleId, "evidence capsule ID");
    if (!List.of("BUSINESS_TERM", "CLAIM", "QUESTION").contains(proposalKind)) {
      throw new IllegalArgumentException("repository registry proposal kind is invalid");
    }
    required(normalizedLabel, "normalized label");
    required(normalizedPurpose, "normalized purpose");
    basisAtomIds = ordered(basisAtomIds, "atom basis");
    basisGapIds = ordered(basisGapIds, "Gap basis");
    if (basisAtomIds.isEmpty() && basisGapIds.isEmpty()) {
      throw new IllegalArgumentException("repository registry basis is required");
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

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
  }

  private static int compare(byte[] left, byte[] right) {
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison = Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(left.length, right.length);
  }
}
