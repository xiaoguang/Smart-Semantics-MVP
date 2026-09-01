package com.linguan.codemd.target.artifacts;

/** Content-addressed reference to the immutable artifact-policy registry. */
public record ArtifactPolicyRegistryReference(String artifactId, String sha256) {
    public ArtifactPolicyRegistryReference {
        ArtifactPolicyValidation.registryArtifactId(artifactId);
        ArtifactPolicyValidation.sha256(sha256, "sha256");
    }
}
