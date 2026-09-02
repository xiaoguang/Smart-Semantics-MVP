package org.sourceanalysis.app.capture.localgit;

/** Stable, path-free failure for local Git capture. */
public final class LocalGitCaptureException extends RuntimeException {

  private final String code;

  public LocalGitCaptureException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
