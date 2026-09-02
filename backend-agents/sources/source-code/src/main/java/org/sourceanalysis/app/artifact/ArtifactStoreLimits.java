package org.sourceanalysis.app.artifact;

/** Explicit resource bounds applied by canonical artifact stores. */
public record ArtifactStoreLimits(
    int maxPayloadFiles,
    long maxArtifactBytes,
    long maxPublicationBytes,
    int maxDirectoryEntries) {}
