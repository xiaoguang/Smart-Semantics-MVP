package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Result of a successful or exact-idempotent stage publication installation. */
public record InstalledStagePublication(
        StagePublicationReference reference,
        String disposition,
        List<ArtifactDescriptor> semanticArtifactDescriptors,
        ArtifactDescriptor archiveManifestDescriptor) {}
