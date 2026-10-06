package org.sourceanalysis.app.runtime;

/** Path-free request for exactly one known business or technical payload of one completed run. */
public record ArtifactQuery(
    String runId,
    String entryId,
    BusinessOutputArtifactKey businessOutputArtifactKey,
    TechnicalArtifactQueryKey technicalArtifactQueryKey,
    OntologyArtifactQueryKey ontologyArtifactQueryKey,
    String producingTaskId,
    int maxBytes) {

  public ArtifactQuery {
    if (runId == null
        || runId.isBlank()
        || maxBytes <= 0
        || selectedBranches(
                businessOutputArtifactKey, technicalArtifactQueryKey, ontologyArtifactQueryKey)
            != 1) {
      throw new IllegalArgumentException("artifact query is invalid");
    }
    if (requiresEntryId(technicalArtifactQueryKey)) {
      if (entryId == null || !entryId.matches("entry:[0-9a-f]{64}")) {
        throw new IllegalArgumentException("entry-evidence query requires a complete entry ID");
      }
    } else if (entryId != null) {
      throw new IllegalArgumentException("entry ID is only valid for entry-evidence queries");
    }
    if (ontologyArtifactQueryKey == null && producingTaskId != null) {
      throw new IllegalArgumentException("task ID is only valid for ontology task-record queries");
    }
    if (ontologyArtifactQueryKey != null
        && ontologyArtifactQueryKey.taskObservation()
        && (producingTaskId == null || producingTaskId.isBlank())) {
      throw new IllegalArgumentException(
          "ontology task-record query requires a saved producing task ID");
    }
    if (ontologyArtifactQueryKey != null
        && !ontologyArtifactQueryKey.taskObservation()
        && producingTaskId != null) {
      throw new IllegalArgumentException("task ID is only valid for ontology task-record queries");
    }
  }

  /** Preserves callers compiled against the business/technical five-component wire shape. */
  public ArtifactQuery(
      String runId,
      String entryId,
      BusinessOutputArtifactKey businessOutputArtifactKey,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      int maxBytes) {
    this(
        runId, entryId, businessOutputArtifactKey, technicalArtifactQueryKey, null, null, maxBytes);
  }

  /** Preserves the historical business-query construction contract. */
  public ArtifactQuery(
      String runId, BusinessOutputArtifactKey businessOutputArtifactKey, int maxBytes) {
    this(runId, null, businessOutputArtifactKey, null, null, null, maxBytes);
  }

  /** Creates the mutually exclusive technical-file query branch. */
  public static ArtifactQuery technical(
      String runId, TechnicalArtifactQueryKey technicalArtifactQueryKey, int maxBytes) {
    return new ArtifactQuery(runId, null, null, technicalArtifactQueryKey, null, null, maxBytes);
  }

  /** Creates an exact path-free entry-evidence query. */
  public static ArtifactQuery technical(
      String runId,
      TechnicalArtifactQueryKey technicalArtifactQueryKey,
      String entryId,
      int maxBytes) {
    return new ArtifactQuery(runId, entryId, null, technicalArtifactQueryKey, null, null, maxBytes);
  }

  /** Creates a closed public ontology-file query. */
  public static ArtifactQuery ontology(
      String runId, OntologyArtifactQueryKey ontologyArtifactQueryKey, int maxBytes) {
    return new ArtifactQuery(runId, null, null, null, ontologyArtifactQueryKey, null, maxBytes);
  }

  /** Creates a membership-bound private ontology task observation query. */
  public static ArtifactQuery ontologyTaskRecord(
      String runId, String producingTaskId, int maxBytes) {
    return new ArtifactQuery(
        runId,
        null,
        null,
        null,
        OntologyArtifactQueryKey.ONTOLOGY_TASK_RECORD,
        producingTaskId,
        maxBytes);
  }

  private static int selectedBranches(
      BusinessOutputArtifactKey business,
      TechnicalArtifactQueryKey technical,
      OntologyArtifactQueryKey ontology) {
    return (business == null ? 0 : 1) + (technical == null ? 0 : 1) + (ontology == null ? 0 : 1);
  }

  private static boolean requiresEntryId(TechnicalArtifactQueryKey key) {
    return key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE
        || key == TechnicalArtifactQueryKey.ENTRY_EVIDENCE_V2;
  }
}
