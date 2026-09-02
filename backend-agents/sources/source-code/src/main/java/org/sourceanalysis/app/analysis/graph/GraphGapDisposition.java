package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** The explicit non-exact disposition of one candidate graph element. */
public record GraphGapDisposition(ArtifactId candidateElementId, ArtifactId gapId) {

  public GraphGapDisposition {
    Objects.requireNonNull(candidateElementId, "candidate element ID");
    Objects.requireNonNull(gapId, "gap ID");
  }
}
