package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Read-only boundary for bounded inspection of concrete technical-output payloads. */
@FunctionalInterface
public interface CompletedTechnicalArtifactReader {

  /** Fresh-reopens and returns exactly one named public technical payload, or rejects it whole. */
  ArtifactView read(
      AnalysisRunId runId,
      AnalysisRunOutput output,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      int maxBytes);
}
