package org.sourceanalysis.app.analysis.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** Canonical interprocedural DFS order for exactly one discovered entry. */
public record ControlFlowTraversal(
    ArtifactId entryId, List<ArtifactId> nodeIds, List<ArtifactId> edgeIds) {

  public ControlFlowTraversal {
    Objects.requireNonNull(entryId, "control-flow traversal entry ID");
    nodeIds = orderedDistinct(nodeIds, "control-flow traversal node IDs");
    edgeIds = orderedDistinct(edgeIds, "control-flow traversal edge IDs");
    if (nodeIds.isEmpty() || edgeIds.isEmpty()) {
      throw new IllegalArgumentException("control-flow traversal must be nonempty");
    }
  }

  private static List<ArtifactId> orderedDistinct(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> order = List.copyOf(values);
    if (order.size() != new HashSet<>(order).size()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return order;
  }
}
