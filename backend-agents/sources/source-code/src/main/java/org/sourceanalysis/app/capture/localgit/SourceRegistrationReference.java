package org.sourceanalysis.app.capture.localgit;

import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Path-free identity of a captured source registration and the two immutable capture artifacts. */
public record SourceRegistrationReference(
    ArtifactId sourceRegistrationId,
    String snapshotId,
    ArtifactReference snapshotManifestRef,
    ArtifactReference captureReceiptRef) {

  public SourceRegistrationReference {
    if (sourceRegistrationId == null
        || snapshotId == null
        || snapshotManifestRef == null
        || captureReceiptRef == null) {
      throw new IllegalArgumentException("source registration reference fields are required");
    }
  }
}
