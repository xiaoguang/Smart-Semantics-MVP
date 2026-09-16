package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;

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

  /** Transitional constructor retained only until old report-generation consumers are removed. */
  public AnalysisStepExecutionRequest(AnalysisRunId runId, AnalysisStepKey targetStep) {
    this(runId, legacyIntent(targetStep), null, null);
  }

  private static AnalysisExecutionIntent legacyIntent(AnalysisStepKey targetStep) {
    Objects.requireNonNull(targetStep, "target analysis step");
    return switch (targetStep) {
      case FLOW_INTERPRETATION -> AnalysisExecutionIntent.PREPARE_MATERIALS;
      case NINE_SECTION_DOCUMENT -> AnalysisExecutionIntent.LEGACY_COMPLETE_REPORT;
      default -> throw new IllegalArgumentException("ANALYSIS_STEP_EXECUTION_NOT_SUPPORTED");
    };
  }
}
