package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Closed, program-only M7 partition of persisted candidate groups; it never invokes a Provider. */
public record BusinessProcessTaskCompilation(
    List<BusinessProcessTaskShardV1> taskShards,
    List<ProcessInterpretationGapV1> processGaps,
    int providerCallCount,
    boolean closed) {

  public BusinessProcessTaskCompilation {
    taskShards = List.copyOf(Objects.requireNonNull(taskShards, "task shards"));
    processGaps = List.copyOf(Objects.requireNonNull(processGaps, "process gaps"));
    if (providerCallCount != 0 || !closed) {
      throw new IllegalArgumentException("process task compilation is not closed");
    }
    if (!processGaps.equals(
        processGaps.stream()
            .sorted(
                java.util.Comparator.comparing(
                    ProcessInterpretationGapV1::gapId, BusinessProcessTaskCompiler::compareUtf8))
            .toList())) {
      throw new IllegalArgumentException("process gaps are not ordered");
    }
    Map<String, ProcessInterpretationGapV1> gapsById =
        processGaps.stream()
            .collect(
                Collectors.toMap(
                    ProcessInterpretationGapV1::gapId,
                    gap -> gap,
                    (left, right) -> {
                      throw new IllegalArgumentException("process gaps are duplicated");
                    }));
    Set<String> noModelOwnedGapIds = new java.util.HashSet<>();
    Set<String> modelSafeLimitationGapIds = new java.util.HashSet<>();
    for (BusinessProcessTaskShardV1 shard : taskShards) {
      if ("NO_MODEL".equals(shard.shardModelDisposition())) {
        for (String gapId : shard.modelIneligibilityGapIds()) {
          ProcessInterpretationGapV1 gap = gapsById.get(gapId);
          if (gap == null
              || !shard.taskShardId().equals(gap.taskShardId())
              || !("PROCESS_TASK_BUDGET_EXCEEDED".equals(gap.gapCode())
                  || "PROCESS_UPSTREAM_MODEL_INELIGIBLE".equals(gap.gapCode()))
              || !noModelOwnedGapIds.add(gapId)) {
            throw new IllegalArgumentException("process Gap ownership is invalid");
          }
        }
        continue;
      }
      if (!"MODEL_SAFE".equals(shard.shardModelDisposition())
          || !shard.modelIneligibilityGapIds().isEmpty()
          || shard.processModelPacket() == null) {
        throw new IllegalArgumentException("process shard disposition is invalid");
      }
      Map<String, ReaderKeyBindingV1> bindingsByKey =
          shard.readerKeyBindings().stream()
              .collect(
                  Collectors.toMap(
                      ReaderKeyBindingV1::readerKey,
                      binding -> binding,
                      (left, right) -> {
                        throw new IllegalArgumentException(
                            "process reader key binding is duplicated");
                      }));
      for (ProcessModelPacketV1.ReaderLimitationV1 limitation :
          shard.processModelPacket().limitations()) {
        ReaderKeyBindingV1 binding = bindingsByKey.get(limitation.limitationKey());
        if (binding == null
            || !"LIMITATION".equals(binding.keyKind())
            || binding.internalReferenceIds().size() != 1) {
          throw new IllegalArgumentException("process limitation binding is invalid");
        }
        ProcessInterpretationGapV1 gap = gapsById.get(binding.internalReferenceIds().get(0));
        if (gap == null
            || !"PROCESS_UPSTREAM_LIMITATION".equals(gap.gapCode())
            || !shard.taskShardId().equals(gap.taskShardId())
            || !shard.processEvidenceGroupId().equals(gap.processEvidenceGroupId())
            || !shard.ownerCandidateRelationIds().equals(gap.candidateRelationIds())
            || !shard.contextFlowSliceIds().equals(gap.affectedFlowSliceIds())
            || gap.upstreamGap() == null
            || !shard.contextFlowSliceIds().containsAll(gap.upstreamGap().sourceFlowSliceIds())
            || !modelSafeLimitationGapIds.add(gap.gapId())) {
          throw new IllegalArgumentException("process limitation carrier is invalid");
        }
      }
    }
    Set<String> allReferencedGapIds = new java.util.HashSet<>(noModelOwnedGapIds);
    allReferencedGapIds.addAll(modelSafeLimitationGapIds);
    Set<String> overlap = new java.util.HashSet<>(noModelOwnedGapIds);
    overlap.retainAll(modelSafeLimitationGapIds);
    if (!overlap.isEmpty() || !allReferencedGapIds.equals(gapsById.keySet())) {
      throw new IllegalArgumentException("process Gap conservation is invalid");
    }
  }
}
