package org.sourceanalysis.app.analysis.inventory;

import java.util.List;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The analyzable-text partition of one fresh-reopened verified source inventory. */
public record VerifiedSourceTextSet(
    String snapshotId,
    String inventoryScopeKind,
    boolean repositoryCompletionEligible,
    ArtifactReference capabilityProfileRef,
    ArtifactReference sourceInventoryRef,
    ArtifactReference verifiedSnapshotRef,
    ArtifactControls controls,
    List<VerifiedSourceTextDocument> documents) {

  public VerifiedSourceTextSet {
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("snapshot identity must be canonical");
    }
    if (!"COMPLETE_CAPTURE".equals(inventoryScopeKind)
        && !"BOUNDED_PATH_SET".equals(inventoryScopeKind)) {
      throw new IllegalArgumentException("verified source inventory scope is invalid");
    }
    if (repositoryCompletionEligible && !"COMPLETE_CAPTURE".equals(inventoryScopeKind)) {
      throw new IllegalArgumentException("only complete capture may be completion eligible");
    }
    if (capabilityProfileRef == null
        || sourceInventoryRef == null
        || verifiedSnapshotRef == null
        || controls == null) {
      throw new IllegalArgumentException(
          "verified source text set must carry exact published controls");
    }
    documents = List.copyOf(documents);
    if (documents.isEmpty()) {
      throw new IllegalArgumentException("verified source text set must not be empty");
    }
  }
}
