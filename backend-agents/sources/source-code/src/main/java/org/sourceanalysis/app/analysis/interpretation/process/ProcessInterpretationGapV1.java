package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** Program-created M7 Gap which either blocks a shard or carries one upstream limitation. */
public record ProcessInterpretationGapV1(
    String gapId,
    String gapCode,
    String gapScope,
    String processEvidenceGroupId,
    List<String> candidateRelationIds,
    List<String> affectedFlowSliceIds,
    String limitKind,
    Integer configuredLimit,
    Integer observedValue,
    String messageKey,
    String taskShardId,
    UpstreamFlowGapProjectionV1 upstreamGap) {

  public ProcessInterpretationGapV1 {
    required(gapId, "gap ID");
    required(gapCode, "gap code");
    required(gapScope, "gap scope");
    required(processEvidenceGroupId, "process evidence group ID");
    required(taskShardId, "task shard ID");
    candidateRelationIds = ordered(candidateRelationIds, "candidate relation IDs", false);
    affectedFlowSliceIds = ordered(affectedFlowSliceIds, "affected Flow slice IDs", true);
    if (!"PROCESS_TASK_SHARD".equals(gapScope)) {
      throw new IllegalArgumentException("process task gap scope is invalid");
    }
    if ("PROCESS_TASK_BUDGET_EXCEEDED".equals(gapCode)) {
      required(limitKind, "limit kind");
      if (configuredLimit == null
          || observedValue == null
          || configuredLimit < 1
          || observedValue < 1
          || observedValue <= configuredLimit
          || upstreamGap != null
          || !"process-task-budget-exceeded".equals(messageKey)) {
        throw new IllegalArgumentException("process task budget gap is invalid");
      }
    } else if ("PROCESS_UPSTREAM_MODEL_INELIGIBLE".equals(gapCode)) {
      if (limitKind != null
          || configuredLimit != null
          || observedValue != null
          || upstreamGap == null
          || !"process-upstream-model-ineligible".equals(messageKey)) {
        throw new IllegalArgumentException("process upstream model-ineligible gap is invalid");
      }
    } else if ("PROCESS_UPSTREAM_LIMITATION".equals(gapCode)) {
      if (limitKind != null
          || configuredLimit != null
          || observedValue != null
          || upstreamGap == null
          || !"process-upstream-limitation".equals(messageKey)) {
        throw new IllegalArgumentException("process upstream limitation gap is invalid");
      }
    } else {
      throw new IllegalArgumentException("process task gap code is invalid");
    }
  }

  private static List<String> ordered(List<String> values, String label, boolean nonempty) {
    values = List.copyOf(Objects.requireNonNull(values, label));
    if ((nonempty && values.isEmpty())
        || values.stream().anyMatch(value -> value == null || value.isBlank())
        || values.size() != values.stream().distinct().count()
        || !values.equals(
            values.stream().sorted(BusinessProcessTaskCompiler::compareUtf8).toList())) {
      throw new IllegalArgumentException(label + " are invalid");
    }
    return values;
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
