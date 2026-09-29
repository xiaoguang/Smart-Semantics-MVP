package org.sourceanalysis.app.capture.preparation;

import java.util.Objects;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Immutable, path-free identity of the producer that prepared one source result. */
public record SourcePreparationToolIdentity(
    String producerVersion,
    String buildKind,
    Sha256Digest buildSha256,
    String javaVendor,
    String javaVersion,
    String trustedGitVersion) {

  public SourcePreparationToolIdentity {
    requireText(producerVersion, "producer version");
    requireText(buildKind, "build kind");
    Objects.requireNonNull(buildSha256, "build SHA-256");
    requireText(javaVendor, "Java vendor");
    requireText(javaVersion, "Java version");
    if (trustedGitVersion != null && trustedGitVersion.isBlank()) {
      throw new IllegalArgumentException("trusted Git version must be nonblank when present");
    }
  }

  private static void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
