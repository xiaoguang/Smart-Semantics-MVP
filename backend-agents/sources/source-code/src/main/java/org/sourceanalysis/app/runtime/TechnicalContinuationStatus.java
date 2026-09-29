package org.sourceanalysis.app.runtime;

/** Whether the result admits the next technical operation; it is not the run lifecycle state. */
public enum TechnicalContinuationStatus {
  READY,
  READY_WITH_LIMITATIONS,
  BLOCKED
}
