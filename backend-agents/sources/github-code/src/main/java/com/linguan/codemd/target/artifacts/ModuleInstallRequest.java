package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Full, path-free request to atomically publish one named module's payload set. */
public record ModuleInstallRequest(
        ModulePublicationAddress address,
        String moduleVersion,
        List<ArtifactReference> upstreamArtifacts,
        ArtifactControls controls,
        String status,
        List<String> gapRefs,
        List<CanonicalModulePayload> payloads) {
    public ModuleInstallRequest {
        if (address == null || controls == null) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "address and controls must not be null");
        }
        ArtifactValues.text(moduleVersion, "moduleVersion");
        upstreamArtifacts = List.copyOf(requireList(upstreamArtifacts, "upstreamArtifacts"));
        gapRefs = List.copyOf(requireList(gapRefs, "gapRefs"));
        payloads = List.copyOf(requireList(payloads, "payloads"));
        if (!("SUCCEEDED".equals(status) || "SUCCEEDED_WITH_GAPS".equals(status))) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "status must be SUCCEEDED or SUCCEEDED_WITH_GAPS");
        }
        ArtifactValues.sortedUnique(gapRefs, "gapRefs");
        if (payloads.isEmpty()) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "payloads must not be empty");
        }
    }

    private static <T> List<T> requireList(List<T> values, String field) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", field + " must not be null or contain null");
        }
        return values;
    }
}
