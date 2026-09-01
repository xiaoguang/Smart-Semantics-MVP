package com.linguan.codemd.target.artifacts;

/** One complete reader-visible stage semantic payload, copied without stream aliases. */
public record CanonicalStagePayload(
        String fileName,
        String artifactType,
        String schemaVersion,
        String artifactId,
        String mediaType,
        ImmutableBytes canonicalUtf8) {
    public CanonicalStagePayload {
        ArtifactValues.fileName(fileName, "fileName");
        if ("stage-receipt.json".equals(fileName) || "run-manifest.json".equals(fileName)) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "store-owned filenames cannot be semantic payloads");
        }
        new ArtifactPolicyKey(artifactType, schemaVersion);
        ArtifactValues.artifactId(artifactId, "artifactId");
        ArtifactValues.text(mediaType, "mediaType");
        if (canonicalUtf8 == null) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "canonicalUtf8 must not be null");
        }
    }
}
