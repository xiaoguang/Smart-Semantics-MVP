package com.linguan.codemd.target.artifacts;

/** Exact artifact type and schema version selector for one registered policy. */
public record ArtifactPolicyKey(String artifactType, String schemaVersion) {
    public ArtifactPolicyKey {
        ArtifactPolicyValidation.artifactType(artifactType);
        ArtifactPolicyValidation.text(schemaVersion, "schemaVersion");
    }
}
