package org.sourceanalysis.app.analysis.discovery.frontend;

/** The one required processing disposition for an admitted frontend source file. */
public record FrontendSourceFileDisposition(String path, String sourceSha256, Status status) {

  public FrontendSourceFileDisposition {
    if (path == null
        || path.isBlank()
        || path.startsWith("/")
        || path.contains("..")
        || sourceSha256 == null
        || !sourceSha256.matches("[0-9a-f]{64}")
        || status == null) {
      throw new IllegalArgumentException("frontend source file disposition is invalid");
    }
  }

  public enum Status {
    PARSED,
    PARTIAL,
    UNSUPPORTED,
    FAILED,
    NOT_INSPECTED
  }
}
