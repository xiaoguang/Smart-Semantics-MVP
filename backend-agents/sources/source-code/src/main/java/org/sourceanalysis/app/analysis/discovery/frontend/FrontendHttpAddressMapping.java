package org.sourceanalysis.app.analysis.discovery.frontend;

/** One explicit, named HTTP address mapping; it never evaluates frontend source expressions. */
public record FrontendHttpAddressMapping(
    String clientRef,
    String requestOrigin,
    String requestPathPrefix,
    String backendApplicationRef,
    String backendContextPath,
    String stripPrefix,
    String addPrefix,
    String basis) {

  public FrontendHttpAddressMapping {
    if (clientRef == null
        || clientRef.isBlank()
        || backendApplicationRef == null
        || backendApplicationRef.isBlank()
        || basis == null
        || basis.isBlank()) {
      throw new IllegalArgumentException(
          "frontend HTTP mapping requires named, evidenced endpoints");
    }
  }
}
