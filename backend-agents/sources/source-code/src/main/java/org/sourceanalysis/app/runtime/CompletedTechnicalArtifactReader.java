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

  /**
   * Receives a complete entry identity at the read boundary; historical readers keep their
   * four-argument functional implementation until an entry-evidence key is requested.
   */
  default ArtifactView read(
      AnalysisRunId runId,
      AnalysisRunOutput output,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      String entryId,
      int maxBytes) {
    if (entryId != null) {
      throw new IllegalStateException("TECHNICAL_ARTIFACT_QUERY_INVALID");
    }
    return read(runId, output, technicalArtifactQueryKey, maxBytes);
  }
}
