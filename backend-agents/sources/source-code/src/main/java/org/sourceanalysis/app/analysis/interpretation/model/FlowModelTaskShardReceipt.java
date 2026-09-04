package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.List;
import java.util.Objects;

/** Exact R1 or R2 flow denominator and output-task set. */
public record FlowModelTaskShardReceipt(
    String shardId, String round, List<String> denominatorFlowSliceIds, List<String> outputTaskSpecIds) {

  /** Requires a nonempty unique sorted-per-record shard for one model round. */
  public FlowModelTaskShardReceipt {
    if (shardId == null || shardId.isBlank() || !List.of("R1", "R2").contains(round)) {
      throw new IllegalArgumentException("flow model task shard is invalid");
    }
    denominatorFlowSliceIds = ordered(denominatorFlowSliceIds, "denominator Flow IDs");
    outputTaskSpecIds = ordered(outputTaskSpecIds, "output task IDs");
    if (denominatorFlowSliceIds.size() != outputTaskSpecIds.size()) {
      throw new IllegalArgumentException("flow model task shard is not closed");
    }
  }

  private static List<String> ordered(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().sorted().toList();
    if (ordered.stream().anyMatch(value -> value == null || value.isBlank())
        || ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be unique and nonblank");
    }
    return ordered;
  }
}
