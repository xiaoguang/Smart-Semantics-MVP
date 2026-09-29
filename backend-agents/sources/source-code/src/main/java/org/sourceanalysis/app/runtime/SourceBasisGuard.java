package org.sourceanalysis.app.runtime;

/** Exact pre-execution check for a saved selected source basis and its reopened upstream basis. */
public final class SourceBasisGuard {

  private SourceBasisGuard() {}

  public static void requireMatch(
      SelectedSourceBasis expectedFromSavedRequest,
      SelectedSourceBasis actualFromReopenedUpstream) {
    if (expectedFromSavedRequest == null) {
      throw new IllegalArgumentException("SOURCE_BASIS_NOT_BOUND");
    }
    if (!expectedFromSavedRequest.equals(actualFromReopenedUpstream)) {
      throw new IllegalArgumentException("SOURCE_BASIS_MISMATCH");
    }
  }
}
