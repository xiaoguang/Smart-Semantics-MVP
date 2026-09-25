package org.sourceanalysis.app.analysis.inventory;

import java.time.Instant;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Known source-file facts observed at one side of a read or verification operation. */
public record SourceObservation(
    Long sizeBytes,
    Sha256Digest sha256,
    ArtifactId sourceIdentity,
    Instant modifiedAt,
    String fileKey) {

  public SourceObservation {
    if (sizeBytes != null && sizeBytes < 0L) {
      throw new IllegalArgumentException("observed size cannot be negative");
    }
    if (sizeBytes == null
        && sha256 == null
        && sourceIdentity == null
        && modifiedAt == null
        && fileKey == null) {
      throw new IllegalArgumentException("source observation must contain a known fact");
    }
    if (fileKey != null && fileKey.isBlank()) {
      throw new IllegalArgumentException("observed file key cannot be blank");
    }
  }
}
