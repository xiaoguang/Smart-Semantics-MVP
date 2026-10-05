package org.sourceanalysis.app.runtime;

import java.util.Set;

/** Closed public ontology payload keys; callers never supply a filename. */
public enum OntologyArtifactQueryKey {
  ONTOLOGY_CORPUS(
      "ontology-corpus.json", "ONTOLOGY_CORPUS", "ontology-corpus-v1", false, "ontology-corpus-v2"),
  SCHEMA_EVIDENCE("schema-evidence.json", "SCHEMA_EVIDENCE", "schema-evidence-v1", false),
  ONTOLOGY_IDENTIFICATION(
      "ontology-identification.json",
      "ONTOLOGY_IDENTIFICATION",
      "ontology-identification-v1",
      false,
      "ontology-identification-v2",
      "ontology-identification-v3"),
  ONTOLOGY_RELATIONS(
      "ontology-relations.json",
      "ONTOLOGY_RELATIONS",
      "ontology-relations-v1",
      false,
      "ontology-relations-v2",
      "ontology-relations-v3"),
  ONTOLOGY("ontology.json", "ONTOLOGY", "ontology-v1", false, "ontology-v2"),
  ONTOLOGY_COVERAGE(
      "ontology-coverage.json",
      "ONTOLOGY_COVERAGE",
      "ontology-coverage-v1",
      false,
      "ontology-coverage-v2",
      "ontology-coverage-v3"),
  ONTOLOGY_SOURCE_INDEX(
      "ontology-sources.jsonl", "ONTOLOGY_SOURCE_INDEX", "ontology-source-v1", false),
  ONTOLOGY_REVIEW(
      "ontology-review.json",
      "ONTOLOGY_REVIEW",
      "ontology-review-v1",
      false,
      "ontology-review-v2",
      "ontology-review-v3"),
  ONTOLOGY_BUSINESS_OVERVIEW(null, null, "ontology-business-overview-v1", false),
  ONTOLOGY_ASSEMBLY_DIAGNOSTIC(null, null, "ontology-assembly-diagnostic-v1", false),
  ONTOLOGY_TASK_INDEX(null, null, "ontology-task-index-v1", false),
  ONTOLOGY_TASK_RECORD(
      null, null, "ontology-task-observation-v1", true, "ontology-task-observation-v2");

  private final String fileName;
  private final String artifactType;
  private final String schemaVersion;
  private final Set<String> supportedSchemaVersions;
  private final boolean taskObservation;

  OntologyArtifactQueryKey(
      String fileName,
      String artifactType,
      String schemaVersion,
      boolean taskObservation,
      String... additionalSchemaVersions) {
    this.fileName = fileName;
    this.artifactType = artifactType;
    this.schemaVersion = schemaVersion;
    java.util.LinkedHashSet<String> versions = new java.util.LinkedHashSet<>();
    versions.add(schemaVersion);
    java.util.Collections.addAll(versions, additionalSchemaVersions);
    this.supportedSchemaVersions = Set.copyOf(versions);
    this.taskObservation = taskObservation;
  }

  /** Exact installed filename for this closed public query key, never caller-supplied. */
  public String fileName() {
    return fileName;
  }

  /** Exact installed artifact type for this closed public query key. */
  public String artifactType() {
    return artifactType;
  }

  /** Exact installed schema for this closed public query key. */
  public String schemaVersion() {
    return schemaVersion;
  }

  /** Exact persisted payload schema accepted for this closed key. */
  public boolean acceptsSchemaVersion(String candidate) {
    return supportedSchemaVersions.contains(candidate);
  }

  /** Whether this key resolves a membership-bound private observation rather than a file. */
  public boolean taskObservation() {
    return taskObservation;
  }
}
