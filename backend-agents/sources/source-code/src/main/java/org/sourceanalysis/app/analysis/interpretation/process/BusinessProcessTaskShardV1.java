package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** One deterministic process-model ownership shard, with no model lifecycle state. */
public record BusinessProcessTaskShardV1(
    String taskShardId,
    int shardOrdinal,
    String processEvidenceGroupId,
    List<String> ownerCandidateRelationIds,
    List<String> contextFlowSliceIds,
    String shardModelDisposition,
    List<String> modelIneligibilityGapIds,
    ProcessModelPacketV1 processModelPacket,
    List<ReaderKeyBindingV1> readerKeyBindings) {

  public BusinessProcessTaskShardV1 {
    required(taskShardId, "task shard ID");
    required(processEvidenceGroupId, "process evidence group ID");
    ownerCandidateRelationIds = List.copyOf(Objects.requireNonNull(ownerCandidateRelationIds));
    contextFlowSliceIds = List.copyOf(Objects.requireNonNull(contextFlowSliceIds));
    modelIneligibilityGapIds = List.copyOf(Objects.requireNonNull(modelIneligibilityGapIds));
    readerKeyBindings = List.copyOf(Objects.requireNonNull(readerKeyBindings));
    if (shardOrdinal < 0 || contextFlowSliceIds.isEmpty()) {
      throw new IllegalArgumentException("process task shard is invalid");
    }
    if ("MODEL_SAFE".equals(shardModelDisposition)) {
      if (processModelPacket == null || !modelIneligibilityGapIds.isEmpty()) {
        throw new IllegalArgumentException("model-safe process shard is invalid");
      }
    } else if ("NO_MODEL".equals(shardModelDisposition)) {
      if (processModelPacket != null
          || modelIneligibilityGapIds.isEmpty()
          || !readerKeyBindings.isEmpty()) {
        throw new IllegalArgumentException("no-model process shard is invalid");
      }
    } else {
      throw new IllegalArgumentException("process task shard disposition is invalid");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required");
  }
}
