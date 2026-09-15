package org.sourceanalysis.app.analysis.knowledge;

/** Stable failure raised when a Step07 process checkpoint cannot be trusted. */
public final class BusinessProcessCheckpointException extends IllegalArgumentException {

  BusinessProcessCheckpointException(String code, Throwable cause) {
    super(code, cause);
  }
}
