package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** One fully verified, never-truncated business or technical payload suitable for CLI display. */
public record ArtifactView(
    AnalysisRunId runId,
    BusinessOutputArtifactKey businessOutputArtifactKey,
    TechnicalArtifactQueryKey technicalArtifactQueryKey,
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
        || (businessOutputArtifactKey == null) == (technicalArtifactQueryKey == null)) {
      throw new IllegalArgumentException("artifact view is invalid");
    }
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
        immutableReference,
        schemaVersion,
        mediaType,
        contentUtf8);
  }
}
