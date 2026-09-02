package org.sourceanalysis.app.artifact;

/** Immutable control hashes that govern one module publication. */
public record ArtifactControls(
    Sha256Digest toolchainSha256,
    Sha256Digest profileSha256,
    Sha256Digest schemaBundleSha256,
    Sha256Digest promptBundleSha256,
    ArtifactPolicyRegistryReference artifactPolicyRegistryRef) {

  public ArtifactControls {
    if (toolchainSha256 == null
        || profileSha256 == null
        || schemaBundleSha256 == null
        || artifactPolicyRegistryRef == null) {
      throw new IllegalArgumentException("artifact controls have invalid required values");
    }
  }
}
