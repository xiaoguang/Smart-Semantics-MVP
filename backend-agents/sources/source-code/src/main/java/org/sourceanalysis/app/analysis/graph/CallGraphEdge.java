package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** An exact relation from a call site to a proven method target or its paired return. */
public record CallGraphEdge(
    ArtifactId edgeId,
    CallGraphEdgeKind kind,
    ArtifactId fromNodeId,
    ArtifactId toNodeId,
    String ruleId,
    ProgramResolution resolution,
    List<ArtifactId> evidenceDraftRefs) {

  public CallGraphEdge {
    Objects.requireNonNull(edgeId, "edge ID");
    Objects.requireNonNull(kind, "edge kind");
    Objects.requireNonNull(fromNodeId, "edge source");
    Objects.requireNonNull(toNodeId, "edge target");
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("call graph edge rule is required");
    }
    Objects.requireNonNull(resolution, "resolution");
    Objects.requireNonNull(evidenceDraftRefs, "evidence draft references");
    evidenceDraftRefs =
        evidenceDraftRefs.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (evidenceDraftRefs.isEmpty()
        || evidenceDraftRefs.size() != evidenceDraftRefs.stream().distinct().count()) {
      throw new IllegalArgumentException(
          "call graph edge provenance must be nonempty and distinct");
    }
    evidenceDraftRefs = List.copyOf(evidenceDraftRefs);
  }
}
