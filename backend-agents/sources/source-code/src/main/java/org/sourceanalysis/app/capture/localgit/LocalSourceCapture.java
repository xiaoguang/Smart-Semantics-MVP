package org.sourceanalysis.app.capture.localgit;

/** Captures one exact locally available Git commit into a rootless frozen source registration. */
public interface LocalSourceCapture {

  /** Captures only the immutable object tree named by the request's full commit identifier. */
  SourceRegistrationReference capture(LocalGitCaptureRequest request);
}
