package org.sourceanalysis.app.runtime;

/** Path-free request for exactly one known business or technical payload of one completed run. */
public record ArtifactQuery(
    String runId,
    BusinessOutputArtifactKey businessOutputArtifactKey,
    TechnicalArtifactQueryKey technicalArtifactQueryKey,
    int maxBytes) {

  public ArtifactQuery {
    if (runId == null
        || runId.isBlank()
        || maxBytes <= 0
        || (businessOutputArtifactKey == null) == (technicalArtifactQueryKey == null)) {
      throw new IllegalArgumentException("artifact query is invalid");
    }
  }

  /** Preserves the historical business-query construction contract. */
  public ArtifactQuery(
      String runId, BusinessOutputArtifactKey businessOutputArtifactKey, int maxBytes) {
    this(runId, businessOutputArtifactKey, null, maxBytes);
  }

  /** Creates the mutually exclusive technical-file query branch. */
  public static ArtifactQuery technical(
      String runId, TechnicalArtifactQueryKey technicalArtifactQueryKey, int maxBytes) {
    return new ArtifactQuery(runId, null, technicalArtifactQueryKey, maxBytes);
  }
}
