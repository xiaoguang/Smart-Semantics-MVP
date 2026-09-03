package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One ordered Java actual and its source-local value origins at a generic call boundary. */
public record BoundaryArgumentV1(
    int ordinal, ArtifactId argumentNodeId, List<ArtifactId> javaLocalOriginNodeIds) {

  public BoundaryArgumentV1 {
    if (ordinal < 0) {
      throw new IllegalArgumentException("boundary argument ordinal is invalid");
    }
    Objects.requireNonNull(argumentNodeId, "boundary argument node ID");
    Objects.requireNonNull(javaLocalOriginNodeIds, "boundary argument Java-local origins");
    javaLocalOriginNodeIds =
        javaLocalOriginNodeIds.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (javaLocalOriginNodeIds.size() != javaLocalOriginNodeIds.stream().distinct().count()) {
      throw new IllegalArgumentException("boundary argument Java-local origins must be distinct");
    }
    javaLocalOriginNodeIds = List.copyOf(javaLocalOriginNodeIds);
  }
}
