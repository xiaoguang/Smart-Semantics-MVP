package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One M4-owned value element; formal parameters remain external M1 endpoints. */
public record DataFlowNode(
    ArtifactId nodeId,
    DataFlowNodeKind kind,
    String canonicalValue,
    List<ArtifactId> owningEntryIds,
    List<ArtifactId> evidenceDraftRefs,
    JavaBoundaryInvocationV1 boundaryInvocation,
    UnknownBoundaryReturnV1 unknownBoundaryReturn) {

  public DataFlowNode {
    Objects.requireNonNull(nodeId, "data-flow node ID");
    Objects.requireNonNull(kind, "data-flow node kind");
    if (canonicalValue == null || canonicalValue.isBlank()) {
      throw new IllegalArgumentException("data-flow canonical value is required");
    }
    owningEntryIds = ordered(owningEntryIds, "data-flow node owning entries");
    evidenceDraftRefs = ordered(evidenceDraftRefs, "data-flow node evidence");
    if (owningEntryIds.isEmpty() || evidenceDraftRefs.isEmpty()) {
      throw new IllegalArgumentException("data-flow node ownership and evidence are required");
    }
    boolean boundary = kind == DataFlowNodeKind.JAVA_BOUNDARY_INVOCATION;
    boolean unknownReturn = kind == DataFlowNodeKind.UNKNOWN_BOUNDARY_RETURN;
    if (boundary != (boundaryInvocation != null)
        || unknownReturn != (unknownBoundaryReturn != null)) {
      throw new IllegalArgumentException("data-flow node variant does not match its kind");
    }
    if (boundary && unknownBoundaryReturn != null || unknownReturn && boundaryInvocation != null) {
      throw new IllegalArgumentException("data-flow node variants are mutually exclusive");
    }
  }

  private static List<ArtifactId> ordered(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }
}
