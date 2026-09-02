package org.sourceanalysis.app.artifact;

/** The path-free address of one semantic analysis-step publication. */
public record AnalysisStepPublicationAddress(AnalysisRunId runId, AnalysisStepKey analysisStepKey) {

  public AnalysisStepPublicationAddress {
    if (runId == null || analysisStepKey == null) {
      throw new IllegalArgumentException("analysis-step publication address is required");
    }
  }
}
