package org.sourceanalysis.app.analysis.fact.candidates;

/** Indicates that a persisted Fact M1 predecessor publication cannot be trusted as an input. */
public final class FactCandidateReferenceException extends IllegalArgumentException {

  private static final String FAILURE_CODE = "PROOF_PACK_REFERENCE_BROKEN";

  /** Creates the stable fatal reference failure required for Fact M1. */
  public FactCandidateReferenceException() {
    super(FAILURE_CODE);
  }
}
