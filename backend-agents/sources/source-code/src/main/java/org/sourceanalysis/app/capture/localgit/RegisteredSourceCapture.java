package org.sourceanalysis.app.capture.localgit;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** A path-free, fresh-reopened local capture registration for internal analysis composition. */
public record RegisteredSourceCapture(
    ArtifactReference sourceRegistrationRef,
    String declaredRepositoryIdentity,
    String commitId,
    String snapshotId,
    ArtifactReference captureReceiptRef,
    ArtifactReference snapshotManifestRef,
    List<RegisteredSourceFile> manifestEntries) {

  public RegisteredSourceCapture {
    Objects.requireNonNull(sourceRegistrationRef, "source registration reference");
    if (declaredRepositoryIdentity == null || declaredRepositoryIdentity.isBlank()) {
      throw new IllegalArgumentException("declared repository identity is required");
    }
    if (commitId == null || !commitId.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("capture commit must be a full lowercase SHA-1");
    }
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("snapshot identity must be canonical");
    }
    Objects.requireNonNull(captureReceiptRef, "capture receipt reference");
    Objects.requireNonNull(snapshotManifestRef, "snapshot manifest reference");
    manifestEntries = List.copyOf(manifestEntries);
  }
}
