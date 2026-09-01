package com.linguan.codemd.target.artifacts;

/** Registry-controlled identity and exposure policy for one exact artifact schema. */
public record CanonicalArtifactPolicy(
        ArtifactPolicyKey key,
        String artifactIdPrefix,
        String mediaType,
        String envelopeKind,
        boolean emptyJsonlAllowed,
        String publicContentExposure) {
    public CanonicalArtifactPolicy {
        if (key == null) {
            throw ArtifactPolicyValidation.invalid("key must not be null");
        }
        ArtifactPolicyValidation.artifactIdPrefix(artifactIdPrefix);
        ArtifactPolicyValidation.mediaType(mediaType);
        ArtifactPolicyValidation.envelopeKind(envelopeKind);
        ArtifactPolicyValidation.publicContentExposure(publicContentExposure);
        if (!"CANONICAL_JSONL".equals(envelopeKind) && emptyJsonlAllowed) {
            throw ArtifactPolicyValidation.invalid(
                    "emptyJsonlAllowed is only permitted for CANONICAL_JSONL");
        }
    }
}
