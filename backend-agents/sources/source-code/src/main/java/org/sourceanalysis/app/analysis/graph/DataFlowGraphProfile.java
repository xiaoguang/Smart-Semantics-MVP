package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The one verified graph-profile identity authorized to set data-flow rules and budgets. */
public record DataFlowGraphProfile(ArtifactReference graphProfileRef) {

  public DataFlowGraphProfile {
    Objects.requireNonNull(graphProfileRef, "data-flow graph profile reference");
    if (!graphProfileRef.artifactId().value().startsWith("graph-profile:")) {
      throw new IllegalArgumentException("data-flow graph profile reference is invalid");
    }
  }
}
