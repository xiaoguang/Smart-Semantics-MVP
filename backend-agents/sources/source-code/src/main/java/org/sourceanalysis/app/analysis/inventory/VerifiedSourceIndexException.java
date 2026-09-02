package org.sourceanalysis.app.analysis.inventory;

/** A stable, path-free M2 source-byte verification failure. */
public final class VerifiedSourceIndexException extends RuntimeException {

  private final String code;

  public VerifiedSourceIndexException(String code) {
    super(code);
    this.code = code;
  }

  /** Returns the fixed external failure code without implementation diagnostics. */
  public String code() {
    return code;
  }
}
