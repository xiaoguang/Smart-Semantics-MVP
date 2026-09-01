package com.linguan.codemd.target.artifacts;

/** Stage08-only contract for the store-authored archive-manifest payload. */
public record ArchiveManifestSpecification(String fileName, String artifactType, String schemaVersion) {
    public ArchiveManifestSpecification {
        ArtifactValues.fileName(fileName, "fileName");
        new ArtifactPolicyKey(artifactType, schemaVersion);
    }
}
