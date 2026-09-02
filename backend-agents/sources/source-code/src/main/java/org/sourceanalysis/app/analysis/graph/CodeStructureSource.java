package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Fresh-reopened verified source bytes and the exact upstream references that authorize them. */
public record CodeStructureSource(
    String snapshotId,
    String inventoryScopeKind,
    boolean repositoryCompletionEligible,
    ArtifactReference sourceInventoryRef,
    ArtifactReference verifiedSnapshotRef,
    ArtifactControls controls,
    List<CodeStructureSourceDocument> documents) {

  public CodeStructureSource {
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("source snapshot identity must be canonical");
    }
    if (!"COMPLETE_CAPTURE".equals(inventoryScopeKind)
        && !"BOUNDED_PATH_SET".equals(inventoryScopeKind)) {
      throw new IllegalArgumentException("source inventory scope is invalid");
    }
    if (repositoryCompletionEligible && !"COMPLETE_CAPTURE".equals(inventoryScopeKind)) {
      throw new IllegalArgumentException("only a complete capture may be completion eligible");
    }
    Objects.requireNonNull(sourceInventoryRef, "source inventory reference");
    Objects.requireNonNull(verifiedSnapshotRef, "verified snapshot reference");
    Objects.requireNonNull(controls, "artifact controls");
    documents =
        documents.stream().sorted(Comparator.comparing(CodeStructureSourceDocument::path)).toList();
    if (documents.isEmpty()
        || documents.size()
            != documents.stream().map(CodeStructureSourceDocument::path).distinct().count()) {
      throw new IllegalArgumentException("source documents must be nonempty and have unique paths");
    }
    if (documents.size()
        != documents.stream().map(CodeStructureSourceDocument::fileId).distinct().count()) {
      throw new IllegalArgumentException("source documents must have unique verified file IDs");
    }
    documents = List.copyOf(documents);
  }
}
