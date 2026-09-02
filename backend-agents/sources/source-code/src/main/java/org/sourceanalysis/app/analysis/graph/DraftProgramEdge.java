package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One source-derived structural relation before the evidence graph resolves provenance tokens. */
public record DraftProgramEdge(
    ArtifactId edgeId,
    ProgramEdgeKind kind,
    ArtifactId fromNodeId,
    ArtifactId toNodeId,
    String ruleId,
    ProgramResolution resolution,
    ArtifactId guardNodeId,
    String polarity,
    List<ArtifactId> evidenceDraftRefs) {

  public DraftProgramEdge {
    Objects.requireNonNull(edgeId, "edge ID");
    Objects.requireNonNull(kind, "edge kind");
    Objects.requireNonNull(fromNodeId, "edge source");
    Objects.requireNonNull(toNodeId, "edge target");
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("edge rule ID is required");
    }
    Objects.requireNonNull(resolution, "edge resolution");
    Objects.requireNonNull(evidenceDraftRefs, "evidence draft references");
    evidenceDraftRefs =
        evidenceDraftRefs.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (evidenceDraftRefs.isEmpty()
        || evidenceDraftRefs.size() != evidenceDraftRefs.stream().distinct().count()) {
      throw new IllegalArgumentException("edge provenance must be nonempty and distinct");
    }
    evidenceDraftRefs = List.copyOf(evidenceDraftRefs);
  }
}
