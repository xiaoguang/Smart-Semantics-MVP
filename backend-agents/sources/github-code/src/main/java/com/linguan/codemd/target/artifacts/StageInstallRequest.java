package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Full, path-free request to publish one stage's exact reader-visible semantic set. */
public record StageInstallRequest(
        StagePublicationAddress address,
        StagePublicationProvenance publicationProvenance,
        List<StagePublicationReference> upstreamStageReferences,
        ArtifactControls controls,
        String status,
        List<String> gapRefs,
        List<CanonicalStagePayload> semanticPayloads,
        ArchiveManifestSpecification archiveManifestSpecification) {
    public StageInstallRequest {
        if (address == null || publicationProvenance == null || controls == null) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "address, provenance, and controls are required");
        }
        upstreamStageReferences = copy(upstreamStageReferences, "upstreamStageReferences");
        gapRefs = copy(gapRefs, "gapRefs");
        semanticPayloads = copy(semanticPayloads, "semanticPayloads");
        if (semanticPayloads.isEmpty()) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "semanticPayloads must not be empty");
        }
        if (!"SUCCEEDED".equals(status) && !"SUCCEEDED_WITH_GAPS".equals(status)) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "status must be SUCCEEDED or SUCCEEDED_WITH_GAPS");
        }
        ArtifactValues.sortedUnique(gapRefs, "gapRefs");
        if (address.stageNumber() <= 7) {
            if (!(publicationProvenance instanceof StagePublisherModuleProvenance)
                    || archiveManifestSpecification != null) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_REQUEST_INVALID", "Stages01-07 require only publisher-module provenance");
            }
        } else if (!(publicationProvenance instanceof Stage08CoordinatorPreparationProvenance)
                || archiveManifestSpecification == null) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", "Stage08 requires coordinator provenance and archive manifest");
        }
    }

    private static <T> List<T> copy(List<T> values, String field) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_REQUEST_INVALID", field + " must not be null or contain null");
        }
        return List.copyOf(values);
    }
}
