package org.sourceanalysis.app.runtime;

/** Closed names for concrete public files of a completed technical output. */
public enum TechnicalArtifactQueryKey {
  JAVA_COMPILATION_ENVIRONMENT(
      PublicationSource.READINESS_REPORT,
      "java-compilation-environment.json",
      "APPLICATION_DISCOVERY_JAVA_COMPILATION_ENVIRONMENT",
      "java-compilation-environment-v1"),
  JAVA_ANALYSIS_READINESS(
      PublicationSource.READINESS_REPORT,
      "java-analysis-readiness.json",
      "APPLICATION_DISCOVERY_JAVA_ANALYSIS_READINESS",
      "java-analysis-readiness-v1"),
  FRONTEND_HTTP_INDEX(
      PublicationSource.FRONTEND_INDEX,
      "frontend-http-index.jsonl",
      "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
      "frontend-http-index-v1"),
  FRONTEND_HTTP_INDEX_V2(
      PublicationSource.FRONTEND_INDEX,
      "frontend-http-index.jsonl",
      "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX",
      "frontend-http-index-v2"),
  APPLICATION_PROFILE(
      PublicationSource.APPLICATION_DISCOVERY,
      "application-profile.json",
      "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
      "application-discovery-application-profile-v2"),
  ENTRY_POINTS(
      PublicationSource.APPLICATION_DISCOVERY,
      "entry-points.jsonl",
      "APPLICATION_DISCOVERY_ENTRY_POINTS",
      "application-discovery-entry-points-v3"),
  MAPPER_CATALOG(
      PublicationSource.APPLICATION_DISCOVERY,
      "mapper-catalog.jsonl",
      "APPLICATION_DISCOVERY_MAPPER_CATALOG",
      "application-discovery-mapper-catalog-v2"),
  CAPABILITY_REPORT(
      PublicationSource.APPLICATION_DISCOVERY,
      "capability-report.json",
      "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
      "application-discovery-capability-report-v2"),
  JAVA_CODE_INDEX(
      PublicationSource.NAVIGATION,
      "java-code-index.jsonl",
      "PROGRAM_GRAPHS_JAVA_CODE_INDEX",
      "java-code-index-v2"),
  JAVA_CODE_INDEX_V3(
      PublicationSource.NAVIGATION,
      "java-code-index.jsonl",
      "PROGRAM_GRAPHS_JAVA_CODE_INDEX",
      "java-code-index-v3"),
  PERSISTENCE_MATERIAL_INDEX(
      PublicationSource.PERSISTENCE,
      "persistence-material-index.jsonl",
      "PERSISTENCE_MATERIAL_INDEX",
      "persistence-material-index-v1"),
  PERSISTENCE_MATERIAL_INDEX_V2(
      PublicationSource.PERSISTENCE,
      "persistence-material-index.jsonl",
      "PERSISTENCE_MATERIAL_INDEX",
      "persistence-material-index-v2"),
  CODE_READING_MATERIALS(
      PublicationSource.READING_MATERIALS,
      "code-reading-materials.jsonl",
      "CODE_READING_MATERIAL_SET",
      "code-reading-material-set-v1"),
  CODE_READING_MATERIALS_V2(
      PublicationSource.READING_MATERIALS,
      "code-reading-materials.jsonl",
      "CODE_READING_MATERIAL_SET",
      "code-reading-material-set-v2"),
  ENTRY_EVIDENCE_INDEX(
      PublicationSource.READING_MATERIALS,
      "entry-evidence-index.json",
      "ENTRY_EVIDENCE_INDEX",
      "entry-evidence-index-v1"),
  ENTRY_EVIDENCE(PublicationSource.READING_MATERIALS, null, "ENTRY_EVIDENCE", "entry-evidence-v1"),
  FRONTEND_EVIDENCE_COVERAGE(
      PublicationSource.READING_MATERIALS,
      "frontend-coverage.jsonl",
      "FRONTEND_EVIDENCE_COVERAGE",
      "frontend-evidence-coverage-v1");

  private final PublicationSource publicationSource;
  private final String fileName;
  private final String artifactType;
  private final String schemaVersion;

  TechnicalArtifactQueryKey(
      PublicationSource publicationSource,
      String fileName,
      String artifactType,
      String schemaVersion) {
    this.publicationSource = publicationSource;
    this.fileName = fileName;
    this.artifactType = artifactType;
    this.schemaVersion = schemaVersion;
  }

  PublicationSource publicationSource() {
    return publicationSource;
  }

  String fileName() {
    return fileName;
  }

  String artifactType() {
    return artifactType;
  }

  String schemaVersion() {
    return schemaVersion;
  }

  enum PublicationSource {
    READINESS_REPORT,
    FRONTEND_INDEX,
    APPLICATION_DISCOVERY,
    NAVIGATION,
    PERSISTENCE,
    READING_MATERIALS
  }
}
