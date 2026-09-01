package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Store-authored receipt written only after a stage semantic set is complete. */
public record StageReceipt(
        String schemaVersion,
        String stageReceiptId,
        String stageArtifactRoot,
        String runId,
        int stageNumber,
        String stageKey,
        StagePublicationProvenance publicationProvenance,
        List<StagePublicationReference> upstreamStageReferences,
        String status,
        ArtifactControls controls,
        List<ArtifactDescriptor> semanticArtifacts,
        ArtifactDescriptor archiveManifest,
        int gapCount,
        List<String> gapRefs) {}
