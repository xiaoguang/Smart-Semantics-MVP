package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The one verified graph-profile identity authorized to set control-flow rules and budgets. */
public record ControlFlowGraphProfile(ArtifactReference graphProfileRef) {

  public ControlFlowGraphProfile {
    Objects.requireNonNull(graphProfileRef, "control-flow graph profile reference");
    if (!graphProfileRef.artifactId().value().startsWith("graph-profile:")) {
      throw new IllegalArgumentException("control-flow graph profile reference is invalid");
    }
  }
}
