package org.sourceanalysis.app.runtime;

/** Path-free request for one known business checkpoint payload of one finished run. */
public record ArtifactQuery(
    String runId, BusinessOutputArtifactKey businessOutputArtifactKey, int maxBytes) {

  public ArtifactQuery {
    if (runId == null || runId.isBlank() || businessOutputArtifactKey == null || maxBytes <= 0) {
      throw new IllegalArgumentException("business artifact query is invalid");
    }
  }
}
