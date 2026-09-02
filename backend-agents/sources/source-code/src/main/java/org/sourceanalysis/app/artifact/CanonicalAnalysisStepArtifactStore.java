package org.sourceanalysis.app.artifact;

/**
 * The path-free storage boundary for semantic analysis-step artifacts and receipt-last completion.
 */
public interface CanonicalAnalysisStepArtifactStore {

  /**
   * Installs one complete semantic analysis-step publication or verifies its byte-identical twin.
   */
  InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request);

  /** Fresh-reopens and verifies one complete semantic analysis-step publication. */
  ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference);
}
