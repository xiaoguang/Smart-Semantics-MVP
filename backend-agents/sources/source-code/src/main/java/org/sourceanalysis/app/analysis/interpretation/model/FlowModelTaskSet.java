package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Closed R1/R2 plan generated only after a persisted all-Flow registry freeze. */
public record FlowModelTaskSet(
    String flowTaskSetId,
    ModulePublicationReference registryPublicationRef,
    String repositoryInterpretationRegistryId,
    List<String> eligibleR1R2FlowSliceIds,
    List<FlowModelTask> tasks,
    List<FlowModelTaskShardReceipt> r1ShardReceipts,
    List<FlowModelTaskShardReceipt> r2ShardReceipts,
    FlowModelTaskProfile taskProfile) {

  /** Checks the two separate R-sized denominators and exact two-task-per-Flow plan. */
  public FlowModelTaskSet {
    required(flowTaskSetId, "flow task set ID");
    Objects.requireNonNull(registryPublicationRef, "registry publication reference");
    required(repositoryInterpretationRegistryId, "repository registry ID");
    eligibleR1R2FlowSliceIds = orderedStrings(eligibleR1R2FlowSliceIds, "R1/R2 Flow IDs");
    tasks =
        tasks.stream()
            .sorted(
                Comparator.comparing(FlowModelTask::flowSliceId)
                    .thenComparing(FlowModelTask::round))
            .toList();
    if (tasks.size() != eligibleR1R2FlowSliceIds.size() * 2
        || tasks.stream().map(FlowModelTask::taskSpecId).distinct().count() != tasks.size()
        || !eligibleR1R2FlowSliceIds.equals(
            tasks.stream()
                .filter(value -> "R1".equals(value.round()))
                .map(FlowModelTask::flowSliceId)
                .toList())
        || !eligibleR1R2FlowSliceIds.equals(
            tasks.stream()
                .filter(value -> "R2".equals(value.round()))
                .map(FlowModelTask::flowSliceId)
                .toList())) {
      throw new IllegalArgumentException("flow model tasks are not closed");
    }
    r1ShardReceipts = shards(r1ShardReceipts, "R1", eligibleR1R2FlowSliceIds, tasks);
    r2ShardReceipts = shards(r2ShardReceipts, "R2", eligibleR1R2FlowSliceIds, tasks);
    Objects.requireNonNull(taskProfile, "flow model task profile");
  }

  private static List<FlowModelTaskShardReceipt> shards(
      List<FlowModelTaskShardReceipt> receipts,
      String round,
      List<String> flows,
      List<FlowModelTask> tasks) {
    Objects.requireNonNull(receipts, round + " shard receipts");
    List<FlowModelTaskShardReceipt> ordered =
        receipts.stream().sorted(Comparator.comparing(FlowModelTaskShardReceipt::shardId)).toList();
    List<String> actualFlows =
        ordered.stream()
            .flatMap(value -> value.denominatorFlowSliceIds().stream())
            .sorted()
            .toList();
    List<String> actualTasks =
        ordered.stream().flatMap(value -> value.outputTaskSpecIds().stream()).sorted().toList();
    List<String> expectedTasks =
        tasks.stream()
            .filter(value -> round.equals(value.round()))
            .map(FlowModelTask::taskSpecId)
            .sorted()
            .toList();
    if (ordered.stream().anyMatch(value -> !round.equals(value.round()))
        || !flows.equals(actualFlows)
        || !expectedTasks.equals(actualTasks)) {
      throw new IllegalArgumentException(round + " task shards are not closed");
    }
    return ordered;
  }

  private static List<String> orderedStrings(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().sorted().toList();
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
}
