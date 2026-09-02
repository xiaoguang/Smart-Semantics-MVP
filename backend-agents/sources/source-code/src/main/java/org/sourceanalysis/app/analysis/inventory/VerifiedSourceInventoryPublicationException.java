package org.sourceanalysis.app.analysis.inventory;

/** Stable, path-free failure while projecting verified source inventory artifacts. */
public final class VerifiedSourceInventoryPublicationException extends RuntimeException {

  private final String code;

  public VerifiedSourceInventoryPublicationException(String code) {
    super(code);
    this.code = code;
  }

  /** Returns the stable machine-readable failure code. */
  public String code() {
    return code;
  }
}
