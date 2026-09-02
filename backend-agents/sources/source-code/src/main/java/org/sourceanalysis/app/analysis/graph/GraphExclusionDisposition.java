package org.sourceanalysis.app.analysis.graph;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** The evidence-backed reason a candidate graph element is outside this graph's supported scope. */
public record GraphExclusionDisposition(
    ArtifactId candidateElementId, String reasonCode, List<ArtifactId> evidenceDraftRefs) {

  public GraphExclusionDisposition {
    Objects.requireNonNull(candidateElementId, "candidate element ID");
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new IllegalArgumentException("exclusion reason code is required");
    }
    evidenceDraftRefs = List.copyOf(evidenceDraftRefs);
    if (evidenceDraftRefs.isEmpty()) {
      throw new IllegalArgumentException("exclusion requires source provenance");
    }
  }
}
