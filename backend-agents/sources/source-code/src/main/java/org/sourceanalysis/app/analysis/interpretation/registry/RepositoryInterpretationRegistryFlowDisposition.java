package org.sourceanalysis.app.analysis.interpretation.registry;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The final R0 registry-freeze outcome for one model-eligible Flow. */
public record RepositoryInterpretationRegistryFlowDisposition(
    String registryFlowDispositionId,
    String flowSliceId,
    String disposition,
    String taskSpecId,
    String registryProposalRoundId,
    String generationReceiptId,
    List<String> registryProposalIds,
    List<String> gapIds,
    String reasonCode) {

  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(
          value -> value.getBytes(StandardCharsets.UTF_8),
          RepositoryInterpretationRegistryFlowDisposition::compare);

  public RepositoryInterpretationRegistryFlowDisposition {
    required(registryFlowDispositionId, "registry Flow disposition ID");
    required(flowSliceId, "Flow slice ID");
    if (!List.of("READY_FOR_FREEZE", "GAP", "FAILED").contains(disposition)) {
      throw new IllegalArgumentException("registry Flow disposition is invalid");
    }
    required(taskSpecId, "task specification ID");
    required(registryProposalRoundId, "registry proposal round ID");
    required(generationReceiptId, "generation receipt ID");
    registryProposalIds = ordered(registryProposalIds, "proposal IDs");
    gapIds = ordered(gapIds, "Gap IDs");
    if (("READY_FOR_FREEZE".equals(disposition)
            && (registryProposalIds.isEmpty() || !gapIds.isEmpty() || reasonCode != null))
        || ("GAP".equals(disposition)
            && (!registryProposalIds.isEmpty()
                || gapIds.isEmpty()
                || reasonCode == null
                || reasonCode.isBlank()))
        || ("FAILED".equals(disposition)
            && (!registryProposalIds.isEmpty()
                || !gapIds.isEmpty()
                || reasonCode == null
                || reasonCode.isBlank()))) {
      throw new IllegalArgumentException("registry Flow disposition is not closed");
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
