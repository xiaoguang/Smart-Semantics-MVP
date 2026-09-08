package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * One deterministic R0 work shard; M1 currently publishes the complete denominator as one shard.
 */
public record RegistryProposalTaskShardReceipt(
    String shardId, List<String> denominatorFlowSliceIds, List<String> outputTaskSpecIds) {

  public RegistryProposalTaskShardReceipt {
    if (shardId == null || shardId.isBlank()) {
      throw new IllegalArgumentException("registry proposal shard ID is required");
    }
    denominatorFlowSliceIds = ordered(denominatorFlowSliceIds, "denominator Flow IDs");
    outputTaskSpecIds = ordered(outputTaskSpecIds, "output task specification IDs");
    if (denominatorFlowSliceIds.size() != outputTaskSpecIds.size()) {
      throw new IllegalArgumentException("registry proposal shard must preserve its denominator");
    }
  }

  private static List<String> ordered(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered =
        values.stream().sorted(RegistryProposalTaskShardReceipt::compareUtf8).toList();
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
}
