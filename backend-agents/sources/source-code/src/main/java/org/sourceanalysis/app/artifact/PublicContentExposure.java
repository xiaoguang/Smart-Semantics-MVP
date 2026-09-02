package org.sourceanalysis.app.artifact;

/** The closed public-content exposure policy for a canonical artifact. */
public enum PublicContentExposure {
  METADATA_ONLY,
  PATH_FREE_COMPLETE_UTF8;

  /** Parses one exact public-content exposure wire value. */
  public static PublicContentExposure parse(String wireValue) {
    try {
      return valueOf(wireValue);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("unknown public content exposure");
    }
  }
}
