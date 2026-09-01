package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.artifacts.ArtifactReference;

/** Exact identity and registered schema of one non-source capability evidence item. */
public record VersionedArtifactEvidenceV2(
        ArtifactReference artifactRef, String artifactType, String schemaVersion) {
    public VersionedArtifactEvidenceV2 {
        if (artifactRef == null
                || artifactType == null
                || !artifactType.matches("[A-Z][A-Z0-9_]*")
                || schemaVersion == null
                || !schemaVersion.matches("[a-z0-9-]+-v[0-9]+")) {
            throw new ApplicationProfileException(
                    "ENTRY_DISCOVERY_INVARIANT_BROKEN", "artifact evidence fields are invalid");
        }
    }
}
