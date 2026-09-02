package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The versioned graph-profile reference governing a code-structure draft. */
public record CodeStructureGraphProfile(ArtifactReference graphProfileRef) {

  public CodeStructureGraphProfile {
    Objects.requireNonNull(graphProfileRef, "graph profile reference");
    if (!graphProfileRef.artifactId().value().startsWith("graph-profile:")) {
      throw new IllegalArgumentException(
          "code-structure graph profile must use graph-profile identity");
    }
  }
}
