package org.sourceanalysis.app.analysis.interpretation.process;

/** Stable M7 failure with an optional underlying parsing or store cause. */
public final class BusinessProcessTaskCompilationException extends RuntimeException {

  public BusinessProcessTaskCompilationException(String code) {
    super(code);
  }

  public BusinessProcessTaskCompilationException(String code, Throwable cause) {
    super(code, cause);
  }
}
