package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Stage08's intentionally acyclic preparation provenance; implemented when Stage08 is reached. */
public record Stage08CoordinatorPreparationProvenance(
        List<ModulePublicationReference> preparationModuleReferences,
        ArtifactReference analysisRunRequestReference,
        List<StagePublicationReference> upstreamStageReferences,
        ArtifactReference repositoryCoverageLedgerRef)
        implements StagePublicationProvenance {
    public Stage08CoordinatorPreparationProvenance {
        if (preparationModuleReferences == null
                || preparationModuleReferences.stream().anyMatch(reference -> reference == null)
                || analysisRunRequestReference == null
                || upstreamStageReferences == null
                || upstreamStageReferences.stream().anyMatch(reference -> reference == null)
                || repositoryCoverageLedgerRef == null) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "Stage08 preparation provenance is incomplete");
        }
        preparationModuleReferences = List.copyOf(preparationModuleReferences);
        upstreamStageReferences = List.copyOf(upstreamStageReferences);
    }
}
