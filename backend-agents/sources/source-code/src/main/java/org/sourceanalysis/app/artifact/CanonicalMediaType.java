package org.sourceanalysis.app.artifact;

/** The closed set of media types available to canonical artifact policies. */
public enum CanonicalMediaType {
  APPLICATION_JSON("application/json"),
  APPLICATION_X_NDJSON("application/x-ndjson"),
  TEXT_MARKDOWN("text/markdown");

  private final String wireValue;

  CanonicalMediaType(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Parses one exact policy-registry media type. */
  public static CanonicalMediaType parse(String wireValue) {
    for (CanonicalMediaType candidate : values()) {
      if (candidate.wireValue.equals(wireValue)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("unknown canonical artifact media type");
  }

  /** Returns the exact media-type wire value. */
  public String wireValue() {
    return wireValue;
  }
}
