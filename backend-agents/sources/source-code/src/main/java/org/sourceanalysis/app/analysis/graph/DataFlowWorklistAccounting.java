package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** Exact work-item denominator and completed set for one data-flow draft. */
public record DataFlowWorklistAccounting(
    List<ArtifactId> enqueuedWorkItemIds,
    List<ArtifactId> processedWorkItemIds,
    boolean overLimit) {

  public DataFlowWorklistAccounting {
    enqueuedWorkItemIds = ordered(enqueuedWorkItemIds, "enqueued data-flow work items");
    processedWorkItemIds = ordered(processedWorkItemIds, "processed data-flow work items");
    if (overLimit || !enqueuedWorkItemIds.equals(processedWorkItemIds)) {
      throw new IllegalArgumentException(
          "data-flow worklist must close without an over-limit draft");
    }
  }

  private static List<ArtifactId> ordered(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }
}
