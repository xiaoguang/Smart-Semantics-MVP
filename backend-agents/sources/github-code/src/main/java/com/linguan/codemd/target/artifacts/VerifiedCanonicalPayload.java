package com.linguan.codemd.target.artifacts;

/** Payload bytes returned only after their receipt, descriptor, policy and hash validate together. */
public record VerifiedCanonicalPayload(ArtifactDescriptor descriptor, ImmutableBytes canonicalUtf8) {}
