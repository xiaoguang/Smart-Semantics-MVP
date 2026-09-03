package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One exact value transfer with source provenance and no independently supplied ownership. */
public record DataFlowEdge(
    ArtifactId edgeId,
    DataFlowEdgeKind kind,
    ArtifactId fromNodeId,
    ArtifactId toNodeId,
    String ruleId,
    ProgramResolution resolution,
    ArtifactId guardNodeId,
    ControlFlowPolarity polarity,
    List<ArtifactId> evidenceDraftRefs) {

  public DataFlowEdge {
    Objects.requireNonNull(edgeId, "data-flow edge ID");
    Objects.requireNonNull(kind, "data-flow edge kind");
    Objects.requireNonNull(fromNodeId, "data-flow edge source");
    Objects.requireNonNull(toNodeId, "data-flow edge target");
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("data-flow edge rule is required");
    }
    if (resolution != ProgramResolution.EXACT) {
      throw new IllegalArgumentException("data-flow edges must be exact");
    }
    if ((guardNodeId == null) != (polarity == null)) {
      throw new IllegalArgumentException(
          "data-flow guard and polarity must both be null or present");
    }
    Objects.requireNonNull(evidenceDraftRefs, "data-flow edge evidence");
    evidenceDraftRefs =
        evidenceDraftRefs.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (evidenceDraftRefs.isEmpty()
        || evidenceDraftRefs.size() != evidenceDraftRefs.stream().distinct().count()) {
      throw new IllegalArgumentException("data-flow edge evidence must be nonempty and distinct");
    }
    evidenceDraftRefs = List.copyOf(evidenceDraftRefs);
  }
}
