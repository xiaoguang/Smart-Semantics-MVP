package org.sourceanalysis.app.analysis.inventory;

/** A stable, path-free request-admission failure. */
public final class FrozenRequestAdmissionException extends RuntimeException {

  private final String code;

  public FrozenRequestAdmissionException(String code) {
    super(code);
    this.code = code;
  }

  /** Returns the fixed external failure code without environment-dependent error detail. */
  public String code() {
    return code;
  }
}
