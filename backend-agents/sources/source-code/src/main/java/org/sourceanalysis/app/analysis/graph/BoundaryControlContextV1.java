package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** The M3 basic block and optional shared guard controlling one boundary invocation. */
public record BoundaryControlContextV1(
    ArtifactId basicBlockNodeId, ArtifactId guardNodeId, ControlFlowPolarity polarity) {

  public BoundaryControlContextV1 {
    Objects.requireNonNull(basicBlockNodeId, "boundary basic block node ID");
    if ((guardNodeId == null) != (polarity == null)) {
      throw new IllegalArgumentException(
          "boundary guard and polarity must both be null or present");
    }
  }
}
