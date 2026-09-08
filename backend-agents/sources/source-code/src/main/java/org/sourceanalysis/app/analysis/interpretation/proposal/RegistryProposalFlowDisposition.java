package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** The one closed R0 outcome for one eligible Flow. */
public record RegistryProposalFlowDisposition(
    String registryProposalDispositionId,
    String flowSliceId,
    String disposition,
    String taskSpecId,
    String registryProposalRoundId,
    String generationReceiptId,
    List<String> registryProposalIds,
    List<String> gapIds,
    String reasonCode) {

  public RegistryProposalFlowDisposition {
    required(registryProposalDispositionId, "registry proposal disposition ID");
    required(flowSliceId, "Flow slice ID");
    if (!List.of("READY_FOR_FREEZE", "GAP", "FAILED").contains(disposition)) {
      throw new IllegalArgumentException("registry proposal disposition is invalid");
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
      throw new IllegalArgumentException("registry proposal disposition is not closed");
    }
  }

  private static List<String> ordered(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered =
        values.stream().sorted(RegistryProposalFlowDisposition::compareUtf8).toList();
    if (ordered.stream().anyMatch(value -> value == null || value.isBlank())
        || ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be unique and nonblank");
    }
    return ordered;
  }

  private static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(first.length, second.length);
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required");
  }
}
