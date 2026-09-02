package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One exact local branch or M2-projected interprocedural relation. */
public record ControlFlowEdge(
    ArtifactId edgeId,
    ControlFlowEdgeKind kind,
    ArtifactId fromNodeId,
    ArtifactId toNodeId,
    String ruleId,
    ProgramResolution resolution,
    ArtifactId guardNodeId,
    ControlFlowPolarity polarity,
    List<ArtifactId> evidenceDraftRefs) {

  public ControlFlowEdge {
    Objects.requireNonNull(edgeId, "control-flow edge ID");
    Objects.requireNonNull(kind, "control-flow edge kind");
    Objects.requireNonNull(fromNodeId, "control-flow edge source");
    Objects.requireNonNull(toNodeId, "control-flow edge target");
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("control-flow edge rule is required");
    }
    Objects.requireNonNull(resolution, "control-flow edge resolution");
    boolean branch = kind == ControlFlowEdgeKind.TRUE || kind == ControlFlowEdgeKind.FALSE;
    if (branch != (guardNodeId != null && polarity != null)) {
      throw new IllegalArgumentException("control-flow guard metadata must match branch kind");
    }
    if (branch
        && ((kind == ControlFlowEdgeKind.TRUE && polarity != ControlFlowPolarity.TRUE)
            || (kind == ControlFlowEdgeKind.FALSE && polarity != ControlFlowPolarity.FALSE))) {
      throw new IllegalArgumentException("control-flow guard polarity must match branch kind");
    }
    evidenceDraftRefs = orderedDistinct(evidenceDraftRefs);
    if (evidenceDraftRefs.isEmpty()) {
      throw new IllegalArgumentException("control-flow edge requires source provenance");
    }
  }

  private static List<ArtifactId> orderedDistinct(List<ArtifactId> values) {
    Objects.requireNonNull(values, "control-flow edge evidence");
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException("control-flow edge evidence must be distinct");
    }
    return List.copyOf(ordered);
  }
}
