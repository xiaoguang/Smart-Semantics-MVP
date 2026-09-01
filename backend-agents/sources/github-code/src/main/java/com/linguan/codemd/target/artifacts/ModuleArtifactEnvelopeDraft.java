package com.linguan.codemd.target.artifacts;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * The caller-owned business payload and verified inputs for one successful module artifact.
 *
 * <p>The envelope module owns its wire shape and content identity. A stage supplies only this
 * typed draft, so it cannot choose an artifact type, prefix, or identity rule.
 */
public record ModuleArtifactEnvelopeDraft(
        ModulePublicationAddress address,
        String moduleVersion,
        List<ArtifactReference> upstreamArtifacts,
        ArtifactControls controls,
        String status,
        List<String> gapRefs,
        JsonNode payload) {
    public ModuleArtifactEnvelopeDraft {
        if (address == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", "address must not be null");
        }
        moduleVersion = ArtifactValues.text(moduleVersion, "moduleVersion");
        if (upstreamArtifacts == null) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_REQUEST_INVALID", "upstreamArtifacts must not be null");
        }
        upstreamArtifacts = List.copyOf(upstreamArtifacts);
        verifyOrderedReferences(upstreamArtifacts);
        if (controls == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", "controls must not be null");
        }
        if (!"SUCCEEDED".equals(status) && !"SUCCEEDED_WITH_GAPS".equals(status)) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_REQUEST_INVALID", "status must be an installed success status");
        }
        if (gapRefs == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", "gapRefs must not be null");
        }
        gapRefs = List.copyOf(gapRefs);
        ArtifactValues.sortedUnique(gapRefs, "gapRefs");
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", "payload must not be null");
        }
        payload = payload.deepCopy();
    }

    private static void verifyOrderedReferences(List<ArtifactReference> references) {
        String previous = null;
        for (ArtifactReference reference : references) {
            if (reference == null) {
                throw new ArtifactStoreException(
                        "MODULE_PUBLICATION_REQUEST_INVALID", "upstreamArtifacts must not contain null");
            }
            if (previous != null && previous.compareTo(reference.artifactId()) >= 0) {
                throw new ArtifactStoreException(
                        "MODULE_PUBLICATION_REQUEST_INVALID", "upstreamArtifacts must be sorted and unique");
            }
            previous = reference.artifactId();
        }
    }
}
