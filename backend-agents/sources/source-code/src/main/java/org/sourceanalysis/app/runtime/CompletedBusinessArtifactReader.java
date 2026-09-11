package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Read-only boundary for bounded inspection of canonical business checkpoint payloads. */
@FunctionalInterface
public interface CompletedBusinessArtifactReader {

  /**
   * Fresh-reopens and returns exactly one named public business payload, or rejects it as a whole.
   */
  ArtifactView read(
      AnalysisRunId runId,
      AnalysisRunOutput output,
      BusinessOutputArtifactKey businessOutputArtifactKey,
      int maxBytes);
}
