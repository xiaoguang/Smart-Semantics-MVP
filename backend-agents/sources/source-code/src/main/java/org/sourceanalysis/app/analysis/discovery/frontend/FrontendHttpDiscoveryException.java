package org.sourceanalysis.app.analysis.discovery.frontend;

/** A stable, typed failure emitted when a frontend syntax observation cannot be safely admitted. */
public final class FrontendHttpDiscoveryException extends IllegalArgumentException {

  private final String code;

  public FrontendHttpDiscoveryException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
