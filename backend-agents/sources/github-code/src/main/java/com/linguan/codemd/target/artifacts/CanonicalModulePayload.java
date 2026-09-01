package com.linguan.codemd.target.artifacts;

/** One complete canonical JSON or JSONL module payload, never a stream alias. */
public record CanonicalModulePayload(
        String fileName,
        String artifactType,
        String schemaVersion,
        String artifactId,
        String mediaType,
        ImmutableBytes canonicalUtf8) {
    public CanonicalModulePayload {
        ArtifactValues.fileName(fileName, "fileName");
        if ("module-receipt.json".equals(fileName)) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "module-receipt.json is store-owned");
        }
        new ArtifactPolicyKey(artifactType, schemaVersion);
        ArtifactValues.artifactId(artifactId, "artifactId");
        ArtifactValues.text(mediaType, "mediaType");
        if (canonicalUtf8 == null) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "canonicalUtf8 must not be null");
        }
    }
}
