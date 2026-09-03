package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Reserved M4 variant for a later, explicitly consumed external-return slice. */
public record UnknownBoundaryReturnV1(
    ArtifactId boundaryInvocationNodeId,
    String declaredReturnType,
    BoundaryReturnState sourceState,
    SourceLocatorV1 sourceLocator,
    String ruleId) {

  public UnknownBoundaryReturnV1 {
    Objects.requireNonNull(boundaryInvocationNodeId, "boundary return invocation node ID");
    if (declaredReturnType == null || declaredReturnType.isBlank()) {
      throw new IllegalArgumentException("boundary return type is required");
    }
    if (sourceState != BoundaryReturnState.UNKNOWN_EXTERNAL_RETURN) {
      throw new IllegalArgumentException("boundary return state is invalid");
    }
    Objects.requireNonNull(sourceLocator, "boundary return source locator");
    if (!"java-boundary-return-source-v1".equals(ruleId)) {
      throw new IllegalArgumentException("boundary return rule is invalid");
    }
  }
}
