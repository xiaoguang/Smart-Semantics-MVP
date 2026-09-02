package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;

/** One exact Java call site and its source-provenance commitment. */
public record CallGraphNode(
    ArtifactId nodeId,
    CallGraphNodeKind kind,
    String canonicalValue,
    List<ArtifactId> owningEntryIds,
    List<ArtifactId> evidenceDraftRefs) {

  public CallGraphNode {
    Objects.requireNonNull(nodeId, "node ID");
    Objects.requireNonNull(kind, "node kind");
    if (canonicalValue == null || canonicalValue.isBlank()) {
      throw new IllegalArgumentException("call graph node canonical value is required");
    }
    owningEntryIds = orderedDistinct(owningEntryIds, "owning entry IDs");
    evidenceDraftRefs = orderedDistinct(evidenceDraftRefs, "evidence draft references");
    if (evidenceDraftRefs.isEmpty()) {
      throw new IllegalArgumentException("call graph node requires source provenance");
    }
  }

  private static List<ArtifactId> orderedDistinct(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != ordered.stream().distinct().count()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }
}
