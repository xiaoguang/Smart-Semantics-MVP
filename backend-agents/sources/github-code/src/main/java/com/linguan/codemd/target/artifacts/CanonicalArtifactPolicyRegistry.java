package com.linguan.codemd.target.artifacts;

/** Immutable, content-addressed lookup table for artifact identity and exposure policy. */
public interface CanonicalArtifactPolicyRegistry {
    ArtifactPolicyRegistryReference reference();

    CanonicalArtifactPolicy resolve(ArtifactPolicyKey key);
}
