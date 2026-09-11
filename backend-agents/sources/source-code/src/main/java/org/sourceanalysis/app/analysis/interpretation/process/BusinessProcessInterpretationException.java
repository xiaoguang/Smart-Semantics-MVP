package org.sourceanalysis.app.analysis.interpretation.process;

/** Stable failure for a bounded M8 process-model interpretation operation. */
public final class BusinessProcessInterpretationException extends IllegalStateException {

  public BusinessProcessInterpretationException(String code) {
    super(code);
  }

  public BusinessProcessInterpretationException(String code, Throwable cause) {
    super(code, cause);
  }
}
