package org.sourceanalysis.app.analysis.inventory;

/** Whether one prepared source scope is safe to hand to a new downstream execution. */
public enum SourcePreparationReadiness {
  READY,
  READY_WITH_EXCLUSIONS,
  NEEDS_DECISION,
  NO_ANALYZABLE_TEXT,
  BLOCKED
}
