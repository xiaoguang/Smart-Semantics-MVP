package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;

/** Exact source basis selected before a new execution; it is never inferred from materials. */
public record SelectedSourceBasis(
    Kind kind,
    PreparedSourceReference preparedSource,
    SourceRegistrationReference legacyCapture,
    ArtifactId snapshotId,
    Sha256Digest effectiveScopeDigest) {

  /** The two mutually exclusive source-basis branches. */
  public enum Kind {
    PREPARED_V1,
    LEGACY_CAPTURE_V1
  }

  public SelectedSourceBasis {
    Objects.requireNonNull(kind, "selected source basis kind");
    if (snapshotId == null || !snapshotId.value().startsWith("snapshot:")) {
      throw new IllegalArgumentException("selected source basis snapshot must be canonical");
    }
    Objects.requireNonNull(effectiveScopeDigest, "selected source basis scope digest");
    switch (kind) {
      case PREPARED_V1 -> {
        if (preparedSource == null
            || legacyCapture != null
            || !snapshotId.equals(preparedSource.sourceVersionId())) {
          throw new IllegalArgumentException(
              "prepared source basis requires only its exact prepared source");
        }
      }
      case LEGACY_CAPTURE_V1 -> {
        if (preparedSource != null || legacyCapture == null) {
          throw new IllegalArgumentException("legacy source basis requires only its exact capture");
        }
        ArtifactId legacySnapshot = ArtifactId.parse(legacyCapture.snapshotId());
        if (!snapshotId.equals(legacySnapshot)) {
          throw new IllegalArgumentException("legacy source basis snapshot must match its capture");
        }
      }
    }
  }
}
