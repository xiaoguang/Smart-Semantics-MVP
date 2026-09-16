package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Requests one explicit public analysis intent for an already queued output run. */
public record AnalysisStepExecutionRequest(
    AnalysisRunId runId,
    AnalysisExecutionIntent intent,
    AnalysisRunId upstreamRunId,
    String exactMaterialId) {

  public AnalysisStepExecutionRequest {
    Objects.requireNonNull(runId, "analysis run ID");
    Objects.requireNonNull(intent, "analysis execution intent");
    if (intent == AnalysisExecutionIntent.DISCOVER_PROCESSES && upstreamRunId == null) {
      throw new IllegalArgumentException("PROCESS_DISCOVERY_ACTIVITY_BATCH_REQUIRED");
    }
    if (intent != AnalysisExecutionIntent.DISCOVER_PROCESSES && upstreamRunId != null) {
      throw new IllegalArgumentException("ANALYSIS_EXECUTION_UPSTREAM_NOT_ALLOWED");
    }
    if (intent != AnalysisExecutionIntent.EXPLAIN_ACTIVITIES && exactMaterialId != null) {
      throw new IllegalArgumentException("ANALYSIS_EXECUTION_MATERIAL_NOT_ALLOWED");
    }
    if (exactMaterialId != null && exactMaterialId.isBlank()) {
      throw new IllegalArgumentException("ANALYSIS_EXECUTION_MATERIAL_INVALID");
    }
  }
}
