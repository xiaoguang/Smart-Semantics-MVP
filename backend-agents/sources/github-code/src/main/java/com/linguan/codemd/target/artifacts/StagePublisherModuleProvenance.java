package com.linguan.codemd.target.artifacts;

/** Stages 01–07 derive their public bytes from one already-installed publication module. */
public record StagePublisherModuleProvenance(ModulePublicationReference publisherSpecificationModuleReference)
        implements StagePublicationProvenance {
    public StagePublisherModuleProvenance {
        if (publisherSpecificationModuleReference == null) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "publisher specification module reference is required");
        }
    }
}
