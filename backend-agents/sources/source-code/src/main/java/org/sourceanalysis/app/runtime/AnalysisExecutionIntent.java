package org.sourceanalysis.app.runtime;

/** Explicit public execution intents; each names one independently persisted result boundary. */
public enum AnalysisExecutionIntent {
  PREPARE_MATERIALS,
  EXPLAIN_ACTIVITIES,
  DISCOVER_PROCESSES,
  /** Transitional only while the retired report-generation route is being removed. */
  LEGACY_COMPLETE_REPORT
}
