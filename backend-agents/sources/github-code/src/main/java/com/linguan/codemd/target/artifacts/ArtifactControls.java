package com.linguan.codemd.target.artifacts;

/** Frozen build and policy inputs that participate in every module publication. */
public record ArtifactControls(
        String toolchainSha256,
        String profileSha256,
        String schemaBundleSha256,
        String promptBundleSha256,
        ArtifactPolicyRegistryReference artifactPolicyRegistryRef) {
    public ArtifactControls {
        ArtifactValues.sha256(toolchainSha256, "toolchainSha256", false);
        ArtifactValues.sha256(profileSha256, "profileSha256", false);
        ArtifactValues.sha256(schemaBundleSha256, "schemaBundleSha256", false);
        ArtifactValues.sha256(promptBundleSha256, "promptBundleSha256", true);
        if (artifactPolicyRegistryRef == null) {
            throw new ArtifactStoreException(
                    "MODULE_INSTALL_REQUEST_INVALID", "artifactPolicyRegistryRef must not be null");
        }
    }
}
