package com.linguan.codemd.target.artifacts;

/** Content identity for one reader-visible stage publication and its final receipt. */
public record StagePublicationReference(
        StagePublicationAddress address,
        String stageArtifactRoot,
        String stageReceiptId,
        String stageReceiptSha256) {
    public StagePublicationReference {
        if (address == null) {
            throw new ArtifactStoreException("STAGE_PUBLICATION_INVALID", "address is required");
        }
        ArtifactValues.contentId(stageArtifactRoot, "stageArtifactRoot", "stage-root");
        ArtifactValues.contentId(stageReceiptId, "stageReceiptId", "stage-receipt");
        ArtifactValues.sha256(stageReceiptSha256, "stageReceiptSha256", false);
    }
}
