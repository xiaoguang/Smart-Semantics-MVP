package org.sourceanalysis.app.analysis.interpretation.proposal;

/** Stable failure for an invalid BusinessFlows-to-R0 task compilation handoff. */
public final class RegistryProposalTaskCompilationException extends RuntimeException {

  public RegistryProposalTaskCompilationException(String code) {
    super(code);
  }

  public RegistryProposalTaskCompilationException(String code, Throwable cause) {
    super(code, cause);
  }
}
