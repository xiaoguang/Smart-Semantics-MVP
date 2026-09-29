package org.sourceanalysis.app.runtime;

/** Path-free request for exactly one known business or technical payload of one completed run. */
public record ArtifactQuery(
    String runId,
    String entryId,
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
    if (technicalArtifactQueryKey == TechnicalArtifactQueryKey.ENTRY_EVIDENCE) {
      if (entryId == null || !entryId.matches("entry:[0-9a-f]{64}")) {
        throw new IllegalArgumentException("entry-evidence query requires a complete entry ID");
      }
    } else if (entryId != null) {
      throw new IllegalArgumentException("entry ID is only valid for entry-evidence queries");
    }
  }

  /** Preserves the historical business-query construction contract. */
  public ArtifactQuery(
      String runId, BusinessOutputArtifactKey businessOutputArtifactKey, int maxBytes) {
    this(runId, null, businessOutputArtifactKey, null, maxBytes);
  }

  /** Creates the mutually exclusive technical-file query branch. */
  public static ArtifactQuery technical(
      String runId, TechnicalArtifactQueryKey technicalArtifactQueryKey, int maxBytes) {
    return new ArtifactQuery(runId, null, null, technicalArtifactQueryKey, maxBytes);
  }

  /** Creates an exact path-free entry-evidence query. */
  public static ArtifactQuery technical(
      String runId,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      String entryId,
      int maxBytes) {
    return new ArtifactQuery(runId, entryId, null, technicalArtifactQueryKey, maxBytes);
  }
}
