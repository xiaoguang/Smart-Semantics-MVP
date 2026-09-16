package org.sourceanalysis.app.runtime;

import java.util.Objects;
import java.util.function.Function;

/** Dispatches one explicit material, Activity, or business-process execution intent. */
public final class RepositoryAnalysisRunCoordinator {

  private final Function<AnalysisStepExecutionRequest, AnalysisRunOutput> execution;

  private RepositoryAnalysisRunCoordinator(
      Function<AnalysisStepExecutionRequest, AnalysisRunOutput> execution) {
    this.execution = Objects.requireNonNull(execution, "configured analysis execution");
  }

  /** Creates the configured intent executor used by the sole production composition root. */
  public static RepositoryAnalysisRunCoordinator configured(
      Function<AnalysisStepExecutionRequest, AnalysisRunOutput> execution) {
    return new RepositoryAnalysisRunCoordinator(execution);
  }

  /** Executes exactly the requested persisted boundary. */
  public AnalysisRunOutput executeIntent(AnalysisStepExecutionRequest request) {
    Objects.requireNonNull(request, "analysis step execution request");
    AnalysisRunOutput output = execution.apply(request);
    if (output == null) {
      throw new IllegalStateException("ANALYSIS_EXECUTION_RESULT_INVALID");
    }
    return output;
  }
}
