package org.sourceanalysis.app.runtime;

/** Explicit public execution intents; each names one independently persisted result boundary. */
public enum AnalysisExecutionIntent {
  PREPARE_SOURCE,
  PREPARE_MATERIALS,
  EXPLAIN_ACTIVITIES,
  DISCOVER_PROCESSES,
  COLLECT_FRONTEND,
  COLLECT_CODE,
  ANALYZE_PERSISTENCE,
  ASSEMBLE_MATERIALS,
  PREPARE_ONTOLOGY,
  IDENTIFY_ONTOLOGY,
  RELATE_ONTOLOGY,
  PUBLISH_ONTOLOGY
}
