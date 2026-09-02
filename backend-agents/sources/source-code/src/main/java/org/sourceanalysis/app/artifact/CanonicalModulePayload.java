package org.sourceanalysis.app.artifact;

/** One caller-supplied canonical JSON/JSONL payload offered for module installation. */
public record CanonicalModulePayload(
    String fileName,
    String artifactType,
    String schemaVersion,
    ArtifactId artifactId,
    CanonicalMediaType mediaType,
    ImmutableBytes canonicalUtf8) {}
