package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** One fully verified, never-truncated business or technical payload suitable for CLI display. */
public record ArtifactView(
    AnalysisRunId runId,
    BusinessOutputArtifactKey businessOutputArtifactKey,
    TechnicalArtifactQueryKey technicalArtifactQueryKey,
    OntologyArtifactQueryKey ontologyArtifactQueryKey,
    ArtifactReference immutableReference,
    String schemaVersion,
    String mediaType,
    String contentUtf8) {

  public ArtifactView {
    if (runId == null
        || immutableReference == null
        || schemaVersion == null
        || schemaVersion.isBlank()
        || mediaType == null
        || mediaType.isBlank()
        || contentUtf8 == null
        || selectedBranches(
                businessOutputArtifactKey, technicalArtifactQueryKey, ontologyArtifactQueryKey)
            != 1) {
      throw new IllegalArgumentException("artifact view is invalid");
    }
  }

  /** Preserves callers compiled against the business/technical seven-component wire shape. */
  public ArtifactView(
      AnalysisRunId runId,
      BusinessOutputArtifactKey businessOutputArtifactKey,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      ArtifactReference immutableReference,
      String schemaVersion,
      String mediaType,
      String contentUtf8) {
    this(
        runId,
        businessOutputArtifactKey,
        technicalArtifactQueryKey,
        null,
        immutableReference,
        schemaVersion,
        mediaType,
        contentUtf8);
  }

  /** Preserves the historical business-view construction contract. */
  public ArtifactView(
      AnalysisRunId runId,
      BusinessOutputArtifactKey businessOutputArtifactKey,
      ArtifactReference immutableReference,
      String schemaVersion,
      String mediaType,
      String contentUtf8) {
    this(
        runId,
        businessOutputArtifactKey,
        null,
        null,
        immutableReference,
        schemaVersion,
        mediaType,
        contentUtf8);
  }

  /** Creates the mutually exclusive technical-file view branch. */
  public static ArtifactView technical(
      AnalysisRunId runId,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      ArtifactReference immutableReference,
      String schemaVersion,
      String mediaType,
      String contentUtf8) {
    return new ArtifactView(
        runId,
        null,
        technicalArtifactQueryKey,
        null,
        immutableReference,
        schemaVersion,
        mediaType,
        contentUtf8);
  }

  /** Creates the mutually exclusive ontology-view branch. */
  public static ArtifactView ontology(
      AnalysisRunId runId,
      OntologyArtifactQueryKey ontologyArtifactQueryKey,
      ArtifactReference immutableReference,
      String schemaVersion,
      String mediaType,
      String contentUtf8) {
    return new ArtifactView(
        runId,
        null,
        null,
        ontologyArtifactQueryKey,
        immutableReference,
        schemaVersion,
        mediaType,
        contentUtf8);
  }

  private static int selectedBranches(
      BusinessOutputArtifactKey business,
      TechnicalArtifactQueryKey technical,
      OntologyArtifactQueryKey ontology) {
    return (business == null ? 0 : 1) + (technical == null ? 0 : 1) + (ontology == null ? 0 : 1);
  }
}
