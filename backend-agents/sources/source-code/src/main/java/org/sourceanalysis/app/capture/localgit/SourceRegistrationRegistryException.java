package org.sourceanalysis.app.capture.localgit;

/** A stable, path-free failure reopening a private captured-source registration. */
public final class SourceRegistrationRegistryException extends RuntimeException {

  private final String code;

  public SourceRegistrationRegistryException(String code) {
    super(code);
    this.code = code;
  }

  /** Returns the fixed error code without a local path or source-content detail. */
  public String code() {
    return code;
  }
}
