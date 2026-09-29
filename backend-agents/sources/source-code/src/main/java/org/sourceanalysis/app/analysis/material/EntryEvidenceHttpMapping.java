package org.sourceanalysis.app.analysis.material;

/**
 * One explicit, already-configured frontend-to-backend address mapping used only while assembling
 * saved HTTP observations. It is not a proxy convention or a URL guesser.
 */
public record EntryEvidenceHttpMapping(
    String clientRef,
    String requestOrigin,
    String requestPathPrefix,
    String backendApplicationRef,
    String backendContextPath,
    String stripPrefix,
    String addPrefix,
    String basis) {

  public EntryEvidenceHttpMapping {
    required(clientRef, "entry-evidence mapping client reference");
    required(backendApplicationRef, "entry-evidence mapping backend application reference");
    required(basis, "entry-evidence mapping basis");
    requestOrigin = nullable(requestOrigin, "entry-evidence mapping request origin");
    requestPathPrefix = pathOrNull(requestPathPrefix, "entry-evidence mapping request prefix");
    backendContextPath = pathOrNull(backendContextPath, "entry-evidence mapping backend context");
    stripPrefix = pathOrNull(stripPrefix, "entry-evidence mapping strip prefix");
    addPrefix = pathOrNull(addPrefix, "entry-evidence mapping add prefix");
  }

  private static String nullable(String value, String label) {
    if (value != null && value.isBlank()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
    return value;
  }

  private static String pathOrNull(String value, String label) {
    nullable(value, label);
    if (value != null && !value.startsWith("/")) {
      throw new IllegalArgumentException(label + " must start with '/'");
    }
    return value;
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
