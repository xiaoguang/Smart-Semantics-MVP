package org.sourceanalysis.app.analysis.discovery;

/** A stable failure code emitted while discovering one frozen application's static capabilities. */
public final class ApplicationDiscoveryException extends IllegalArgumentException {

  private final String code;

  public ApplicationDiscoveryException(String code) {
    super(code);
    this.code = code;
  }

  /**
   * Creates a stable public failure code while retaining the internal causal detail for
   * diagnostics.
   */
  public ApplicationDiscoveryException(String code, Throwable cause) {
    super(code, cause);
    this.code = code;
  }

  /** Returns the documented, path-free failure code. */
  public String code() {
    return code;
  }
}
