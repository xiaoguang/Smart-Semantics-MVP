package org.sourceanalysis.app.analysis.graph;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** A generic frozen-Java exit; it intentionally carries no external-effect semantics. */
public record JavaBoundaryInvocationV1(
    ArtifactId invocationCallId,
    ArtifactId callTargetEdgeId,
    String staticTargetType,
    String staticTargetMethod,
    String staticTargetSignature,
    List<BoundaryArgumentV1> orderedArguments,
    BoundaryControlContextV1 controlContext,
    SourceLocatorV1 sourceLocator,
    String ruleId) {

  public JavaBoundaryInvocationV1 {
    Objects.requireNonNull(invocationCallId, "boundary invocation call ID");
    Objects.requireNonNull(callTargetEdgeId, "boundary call target edge ID");
    requireText(staticTargetType, "boundary static target type");
    requireText(staticTargetMethod, "boundary static target method");
    requireText(staticTargetSignature, "boundary static target signature");
    Objects.requireNonNull(orderedArguments, "boundary ordered arguments");
    for (int ordinal = 0; ordinal < orderedArguments.size(); ordinal++) {
      BoundaryArgumentV1 argument = orderedArguments.get(ordinal);
      if (argument == null || argument.ordinal() != ordinal) {
        throw new IllegalArgumentException("boundary arguments must have contiguous ordinals");
      }
    }
    if (orderedArguments.stream()
            .map(BoundaryArgumentV1::argumentNodeId)
            .collect(java.util.stream.Collectors.toSet())
            .size()
        != orderedArguments.size()) {
      throw new IllegalArgumentException("boundary argument nodes must be distinct");
    }
    orderedArguments = List.copyOf(orderedArguments);
    Objects.requireNonNull(controlContext, "boundary control context");
    Objects.requireNonNull(sourceLocator, "boundary source locator");
    if (!"java-boundary-invocation-v1".equals(ruleId)) {
      throw new IllegalArgumentException("boundary invocation rule is invalid");
    }
  }

  private static void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
