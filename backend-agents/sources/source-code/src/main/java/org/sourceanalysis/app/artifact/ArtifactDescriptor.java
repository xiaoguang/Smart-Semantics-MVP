package org.sourceanalysis.app.artifact;

/** A path-free descriptor of one installed canonical artifact. */
public record ArtifactDescriptor(
    String fileName,
    String artifactType,
    String schemaVersion,
    ArtifactId artifactId,
    CanonicalMediaType mediaType,
    long sizeBytes,
    Sha256Digest sha256) {}
