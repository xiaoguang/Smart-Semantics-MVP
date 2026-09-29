package org.sourceanalysis.app.analysis.discovery.frontend;

/** A source-bound frontend diagnostic, optionally associated with an observed request. */
public record FrontendDiagnosticRecord(
    String code, String sourcePath, String sourceSha256, String requestId) {

  public FrontendDiagnosticRecord {
    if (code == null
        || code.isBlank()
        || sourcePath == null
        || sourcePath.isBlank()
        || sourceSha256 == null
        || !sourceSha256.matches("[0-9a-f]{64}")
        || (requestId != null && requestId.isBlank())) {
      throw new IllegalArgumentException("frontend diagnostic identity is invalid");
    }
  }
}
