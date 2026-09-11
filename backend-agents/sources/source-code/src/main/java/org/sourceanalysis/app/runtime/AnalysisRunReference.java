package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Public, path-free handle for one queued or subsequently executed analysis run. */
public record AnalysisRunReference(
    AnalysisRunId runId,
    AnalysisRunRequestReference analysisRunRequestReference,
    AnalysisRunLifecycleState lifecycleState) {

  public AnalysisRunReference {
    if (runId == null || analysisRunRequestReference == null || lifecycleState == null) {
      throw new IllegalArgumentException("analysis run reference fields are required");
    }
  }
}
