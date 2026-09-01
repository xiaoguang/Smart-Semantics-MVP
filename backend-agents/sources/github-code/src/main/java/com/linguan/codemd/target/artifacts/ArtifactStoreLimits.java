package com.linguan.codemd.target.artifacts;

/** Bounded limits shared by the three canonical stores. */
public record ArtifactStoreLimits(
        int maxPayloadFiles, long maxArtifactBytes, long maxPublicationBytes, int maxDirectoryEntries) {
    public ArtifactStoreLimits {
        if (maxPayloadFiles < 1
                || maxArtifactBytes < 1
                || maxPublicationBytes < maxArtifactBytes
                || maxDirectoryEntries < maxPayloadFiles + 1) {
            throw new ArtifactStoreException("MODULE_INSTALL_REQUEST_INVALID", "artifact store limits are invalid");
        }
    }
}
