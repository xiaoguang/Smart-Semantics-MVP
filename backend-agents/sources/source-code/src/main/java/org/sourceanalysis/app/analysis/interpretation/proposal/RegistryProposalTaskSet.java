package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Closed denominator and task collection emitted by the R0 task compiler. */
public record RegistryProposalTaskSet(
    String taskSetId,
    AnalysisStepPublicationReference businessFlowsPublicationRef,
    List<String> eligibleFlowSliceIds,
    List<RegistryProposalTask> tasks,
    List<RegistryProposalTaskShardReceipt> taskShardReceipts,
    RegistryProposalTaskProfile taskProfile) {

  public RegistryProposalTaskSet {
    required(taskSetId, "task set ID");
    Objects.requireNonNull(businessFlowsPublicationRef, "business flows publication reference");
    eligibleFlowSliceIds = orderedStrings(eligibleFlowSliceIds, "eligible Flow IDs");
    tasks = ordered(tasks, RegistryProposalTask::flowSliceId, "registry proposal tasks");
    taskShardReceipts =
        ordered(
            taskShardReceipts, RegistryProposalTaskShardReceipt::shardId, "task shard receipts");
    Objects.requireNonNull(taskProfile, "task profile");
    if (eligibleFlowSliceIds.size() != tasks.size()
        || !eligibleFlowSliceIds.equals(
            tasks.stream().map(RegistryProposalTask::flowSliceId).toList())
        || tasks.stream().map(RegistryProposalTask::taskSpecId).distinct().count() != tasks.size()
        || tasks.stream().map(RegistryProposalTask::evidenceCapsuleId).distinct().count()
            != tasks.size()
        || tasks.stream().map(RegistryProposalTask::isolatedSessionKey).distinct().count()
            != tasks.size()) {
      throw new IllegalArgumentException("registry proposal task set is not closed");
    }
    List<String> shardFlowIds =
        taskShardReceipts.stream()
            .flatMap(receipt -> receipt.denominatorFlowSliceIds().stream())
            .sorted(RegistryProposalTaskSet::compareUtf8)
            .toList();
    List<String> shardTaskIds =
        taskShardReceipts.stream()
            .flatMap(receipt -> receipt.outputTaskSpecIds().stream())
            .sorted(RegistryProposalTaskSet::compareUtf8)
            .toList();
    List<String> taskIds =
        tasks.stream()
            .map(RegistryProposalTask::taskSpecId)
            .sorted(RegistryProposalTaskSet::compareUtf8)
            .toList();
    if (!eligibleFlowSliceIds.equals(shardFlowIds) || !taskIds.equals(shardTaskIds)) {
      throw new IllegalArgumentException("registry proposal task shards are not closed");
    }
  }

  private static <T> List<T> ordered(
      List<T> values, java.util.function.Function<T, String> key, String label) {
    Objects.requireNonNull(values, label);
    List<T> ordered =
        values.stream()
            .sorted(Comparator.comparing(key, RegistryProposalTaskSet::compareUtf8))
            .toList();
    if (ordered.size() != ordered.stream().map(key).distinct().count()) {
      throw new IllegalArgumentException(label + " must be unique");
    }
    return ordered;
  }

  private static List<String> orderedStrings(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().sorted(RegistryProposalTaskSet::compareUtf8).toList();
    if (ordered.stream().anyMatch(value -> value == null || value.isBlank())
        || ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be unique and nonblank");
    }
    return ordered;
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
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
