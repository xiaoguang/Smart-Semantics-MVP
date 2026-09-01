package com.linguan.codemd.target.artifacts;

/** Immutable reference to exact canonical artifact bytes. */
public record ArtifactReference(String artifactId, String sha256) {
    public ArtifactReference {
        ArtifactValues.artifactId(artifactId, "artifactId");
        ArtifactValues.sha256(sha256, "sha256", false);
    }
}
