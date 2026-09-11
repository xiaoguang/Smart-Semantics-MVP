package org.sourceanalysis.app.analysis.interpretation.process;

/** Stable, fail-closed M6 error surface. */
public final class CrossFlowCandidateCompilationException extends RuntimeException {

  public CrossFlowCandidateCompilationException(String code) {
    super(code);
  }

  public CrossFlowCandidateCompilationException(String code, Throwable cause) {
    super(code, cause);
  }
}
