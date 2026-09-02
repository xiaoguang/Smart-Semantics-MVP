package org.sourceanalysis.app.artifact;

/** The content-addressed reference required to fresh-reopen one module publication. */
public record ModulePublicationReference(
    ModulePublicationAddress address,
    ModuleArtifactRoot moduleArtifactRoot,
    ModuleReceiptId moduleReceiptId,
    Sha256Digest moduleReceiptSha256) {}
