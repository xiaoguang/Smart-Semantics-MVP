package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** An M6 value passed to M7 when a counter cannot be assigned to an exact positive pair. */
public record ProcessCounterScopeIssueV1(
    String candidateRelationId,
    List<String> unscopedCounterProcessJoinSignalIds,
    String reasonCode) {

  public ProcessCounterScopeIssueV1 {
    if (candidateRelationId == null || candidateRelationId.isBlank()) {
      throw new IllegalArgumentException("candidate relation ID is required");
    }
    unscopedCounterProcessJoinSignalIds =
        List.copyOf(Objects.requireNonNull(unscopedCounterProcessJoinSignalIds));
    if (unscopedCounterProcessJoinSignalIds.isEmpty()
        || !"PROCESS_COUNTER_SCOPE_UNRESOLVED".equals(reasonCode)) {
      throw new IllegalArgumentException("counter scope issue is invalid");
    }
  }
}
