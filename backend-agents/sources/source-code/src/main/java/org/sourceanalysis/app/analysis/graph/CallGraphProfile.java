package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The versioned call-resolution profile used by one deterministic call graph build. */
public record CallGraphProfile(ArtifactReference graphProfileRef) {

  public CallGraphProfile {
    Objects.requireNonNull(graphProfileRef, "call graph profile reference");
    if (!graphProfileRef.artifactId().value().startsWith("graph-profile:")) {
      throw new IllegalArgumentException("call graph profile must use graph-profile identity");
    }
  }
}
