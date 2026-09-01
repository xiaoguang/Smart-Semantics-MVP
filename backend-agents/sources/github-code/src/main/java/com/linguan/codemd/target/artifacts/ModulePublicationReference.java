package com.linguan.codemd.target.artifacts;

/** Content identity for one immutable module directory and its final receipt. */
public record ModulePublicationReference(
        ModulePublicationAddress address,
        String moduleArtifactRoot,
        String moduleReceiptId,
        String moduleReceiptSha256) {
    public ModulePublicationReference {
        if (address == null) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_INVALID", "address must not be null");
        }
        ArtifactValues.contentId(moduleArtifactRoot, "moduleArtifactRoot", "module-root");
        ArtifactValues.contentId(moduleReceiptId, "moduleReceiptId", "module-receipt");
        ArtifactValues.sha256(moduleReceiptSha256, "moduleReceiptSha256", false);
    }
}
