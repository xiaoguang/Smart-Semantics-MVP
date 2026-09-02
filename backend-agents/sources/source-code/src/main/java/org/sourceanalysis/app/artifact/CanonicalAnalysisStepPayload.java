package org.sourceanalysis.app.artifact;

/** One canonical semantic payload offered to an analysis-step publication. */
public record CanonicalAnalysisStepPayload(
    String fileName,
    String artifactType,
    String schemaVersion,
    ArtifactId artifactId,
    CanonicalMediaType mediaType,
    ImmutableBytes canonicalUtf8) {}
