package com.linguan.codemd.target.artifacts;

/** Immutable descriptor included in roots and receipts instead of a filesystem location. */
public record ArtifactDescriptor(
        String fileName,
        String artifactType,
        String schemaVersion,
        String artifactId,
        String mediaType,
        long sizeBytes,
        String sha256) {
    public ArtifactDescriptor {
        ArtifactValues.fileName(fileName, "fileName");
        new ArtifactPolicyKey(artifactType, schemaVersion);
        ArtifactValues.artifactId(artifactId, "artifactId");
        ArtifactValues.text(mediaType, "mediaType");
        if (sizeBytes < 0) {
            throw new ArtifactStoreException("MODULE_INSTALL_REQUEST_INVALID", "sizeBytes must be non-negative");
        }
        ArtifactValues.sha256(sha256, "sha256", false);
    }
}
