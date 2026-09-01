package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Store-authored receipt written after every module payload has been verified and forced. */
public record ModuleReceipt(
        String schemaVersion,
        String moduleReceiptId,
        ModulePublicationAddress address,
        String moduleVersion,
        List<ArtifactReference> upstreamArtifacts,
        ArtifactControls controls,
        String status,
        List<ArtifactDescriptor> payloadArtifacts,
        String moduleArtifactRoot,
        List<String> gapRefs) {}
