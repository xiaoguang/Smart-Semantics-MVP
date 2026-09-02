package org.sourceanalysis.app.artifact;

/** Canonical bytes returned only after store-level descriptor and receipt validation. */
public record VerifiedCanonicalPayload(
    ArtifactDescriptor descriptor, ImmutableBytes canonicalUtf8) {}
