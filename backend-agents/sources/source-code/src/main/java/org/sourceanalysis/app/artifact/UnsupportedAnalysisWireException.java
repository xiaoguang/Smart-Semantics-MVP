package org.sourceanalysis.app.artifact;

/** Raised when an input does not declare the closed current analysis wire header. */
public final class UnsupportedAnalysisWireException extends RuntimeException {

  private static final String CODE = "UNSUPPORTED_ANALYSIS_WIRE";

  public UnsupportedAnalysisWireException() {
    super(CODE);
  }

  /** Returns the stable machine-readable rejection code. */
  public String code() {
    return CODE;
  }
}
