package org.sourceanalysis.app.artifact;

/** The content identity and full-document digest of one immutable artifact policy registry. */
public record ArtifactPolicyRegistryReference(ArtifactId artifactId, Sha256Digest sha256) {

  private static final String PREFIX = "artifact-policy-registry:";

  public ArtifactPolicyRegistryReference {
    if (artifactId == null || sha256 == null) {
      throw new IllegalArgumentException(
          "artifact policy registry reference components must be non-null");
    }
    if (!artifactId.value().startsWith(PREFIX)) {
      throw new IllegalArgumentException(
          "artifact policy registry identity must use the artifact-policy-registry prefix");
    }
  }
}
