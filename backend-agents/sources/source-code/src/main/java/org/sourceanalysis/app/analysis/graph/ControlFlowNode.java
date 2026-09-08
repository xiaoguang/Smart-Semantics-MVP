package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One M3-owned control node with closed entry ownership and source provenance. */
public record ControlFlowNode(
    ArtifactId nodeId,
    ControlFlowNodeKind kind,
    String canonicalValue,
    String normalizedCondition,
    List<ArtifactId> owningEntryIds,
    List<ArtifactId> evidenceDraftRefs) {

  public ControlFlowNode {
    Objects.requireNonNull(nodeId, "control-flow node ID");
    Objects.requireNonNull(kind, "control-flow node kind");
    if (canonicalValue == null || canonicalValue.isBlank()) {
      throw new IllegalArgumentException("control-flow node canonical value is required");
    }
    if (kind == ControlFlowNodeKind.GUARD) {
      if (normalizedCondition == null || normalizedCondition.isBlank()) {
        throw new IllegalArgumentException(
            "guard control-flow node normalized condition is required");
      }
    } else if (normalizedCondition != null) {
      throw new IllegalArgumentException(
          "only guard control-flow nodes may carry a normalized condition");
    }
    owningEntryIds = orderedDistinct(owningEntryIds, "control-flow node owners");
    evidenceDraftRefs = orderedDistinct(evidenceDraftRefs, "control-flow node evidence");
    if (owningEntryIds.isEmpty() || evidenceDraftRefs.isEmpty()) {
      throw new IllegalArgumentException("control-flow node requires entry ownership and evidence");
    }
  }

  private static List<ArtifactId> orderedDistinct(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }
}
