package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;

/** Requests one explicit public analysis target for an already queued run. */
public record AnalysisStepExecutionRequest(AnalysisRunId runId, AnalysisStepKey targetStep) {

  public AnalysisStepExecutionRequest {
    Objects.requireNonNull(runId, "analysis run ID");
    Objects.requireNonNull(targetStep, "target analysis step");
  }
}
