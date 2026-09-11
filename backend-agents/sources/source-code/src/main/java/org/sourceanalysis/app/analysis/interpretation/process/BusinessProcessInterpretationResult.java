package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.Objects;

/** Verified transport result of the bounded first P1 then P2 process-interpretation slice. */
public record BusinessProcessInterpretationResult(
    String taskShardId,
    ProcessModelProviderResponse p1Response,
    ProcessModelProviderResponse p2Response,
    int providerCallCount,
    boolean closed) {

  public BusinessProcessInterpretationResult {
    if (taskShardId == null || taskShardId.isBlank()) {
      throw new IllegalArgumentException("task shard ID is required");
    }
    Objects.requireNonNull(p1Response, "P1 response");
    Objects.requireNonNull(p2Response, "P2 response");
    if (providerCallCount != 2 || !closed) {
      throw new IllegalArgumentException("first process interpretation result is not closed");
    }
  }
}
