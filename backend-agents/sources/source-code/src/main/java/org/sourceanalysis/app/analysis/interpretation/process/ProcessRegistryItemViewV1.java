package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** The frozen registry fields needed by later bounded process-task compilation. */
public record ProcessRegistryItemViewV1(
    String registryItemId,
    String flowSliceId,
    String provisionalKey,
    String proposalKind,
    String normalizedLabel,
    List<String> basisAtomIds,
    List<String> basisGapIds) {

  public ProcessRegistryItemViewV1 {
    required(registryItemId);
    required(flowSliceId);
    required(provisionalKey);
    required(proposalKind);
    required(normalizedLabel);
    basisAtomIds = List.copyOf(Objects.requireNonNull(basisAtomIds));
    basisGapIds = List.copyOf(Objects.requireNonNull(basisGapIds));
  }

  private static void required(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("registry item view is invalid");
  }
}
