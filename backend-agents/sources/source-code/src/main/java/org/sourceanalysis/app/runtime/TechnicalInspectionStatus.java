package org.sourceanalysis.app.runtime;

/**
 * How completely the current technical operation performed its checks, independent of readiness.
 */
public enum TechnicalInspectionStatus {
  CHECKS_COMPLETE,
  CHECKS_INCOMPLETE,
  NOT_STARTED
}
