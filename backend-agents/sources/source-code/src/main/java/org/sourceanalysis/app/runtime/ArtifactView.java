package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** One fully verified, never-truncated business checkpoint payload suitable for CLI display. */
public record ArtifactView(
    AnalysisRunId runId,
    BusinessOutputArtifactKey businessOutputArtifactKey,
    ArtifactReference immutableReference,
    String schemaVersion,
    String mediaType,
    String contentUtf8) {

  public ArtifactView {
    if (runId == null
        || businessOutputArtifactKey == null
        || immutableReference == null
        || schemaVersion == null
        || schemaVersion.isBlank()
        || mediaType == null
        || mediaType.isBlank()
        || contentUtf8 == null) {
      throw new IllegalArgumentException("business artifact view is invalid");
    }
  }
}
