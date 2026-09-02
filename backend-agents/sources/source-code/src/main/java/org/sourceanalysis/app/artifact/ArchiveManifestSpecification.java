package org.sourceanalysis.app.artifact;

/** Registered archive-manifest shape; absent for the first seven analysis steps. */
public record ArchiveManifestSpecification(
    String fileName, String artifactType, String schemaVersion) {}
