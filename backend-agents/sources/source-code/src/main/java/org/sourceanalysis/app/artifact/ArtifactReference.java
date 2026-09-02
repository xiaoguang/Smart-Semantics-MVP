package org.sourceanalysis.app.artifact;

/** A content-addressed artifact identity paired with the digest of its bytes. */
public record ArtifactReference(ArtifactId artifactId, Sha256Digest sha256) {

  public ArtifactReference {
    if (artifactId == null || sha256 == null) {
      throw new IllegalArgumentException("artifact reference components must be non-null");
    }
  }
}
