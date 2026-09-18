package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Closed public names for historical business outputs and the Step05 reading-material output. */
public enum BusinessOutputArtifactKey {
  BUSINESS_MATERIALS(AnalysisStepKey.FLOW_INTERPRETATION, 10, "business-materials.jsonl"),
  ACTIVITY_EXPLANATIONS(AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explanations.jsonl"),
  ACTIVITY_COVERAGE(AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-coverage.json"),
  BUSINESS_PROCESSES_MARKDOWN(AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "business-processes.md"),
  REPOSITORY_BUSINESS_PROCESS_CATALOG(
      AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "repository-business-process-catalog.json"),
  PROCESS_SOURCE_REFERENCES(AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "source-refs.jsonl"),
  PROCESS_SOURCES_MARKDOWN(AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "sources.md"),
  REPOSITORY_BUSINESS_KNOWLEDGE(
      AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "repository-business-knowledge.json"),
  PROCESS_COVERAGE(AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "process-coverage.json"),
  BUSINESS_REPORT(AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, "business-report.json"),
  DOCUMENT_MARKDOWN(AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, "document.md"),
  SOURCE_REFERENCES(AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, "source-refs.jsonl"),
  REPORT_VALIDATION(AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, "report-validation.json"),
  CODE_READING_MATERIALS(AnalysisStepKey.BUSINESS_FLOWS, 4, "code-reading-materials.jsonl", true);

  private final AnalysisStepKey step;
  private final int moduleNumber;
  private final String fileName;
  private final boolean readingMaterials;

  BusinessOutputArtifactKey(AnalysisStepKey step, int moduleNumber, String fileName) {
    this(step, moduleNumber, fileName, false);
  }

  BusinessOutputArtifactKey(
      AnalysisStepKey step, int moduleNumber, String fileName, boolean readingMaterials) {
    this.step = step;
    this.moduleNumber = moduleNumber;
    this.fileName = fileName;
    this.readingMaterials = readingMaterials;
  }

  String fileName() {
    return fileName;
  }

  ModulePublicationReference checkpoint(AnalysisRunOutput output) {
    if (readingMaterials) {
      throw new IllegalStateException(
          "business output artifact key does not use a module checkpoint");
    }
    ModulePublicationReference checkpoint =
        switch (step) {
          case FLOW_INTERPRETATION ->
              moduleNumber == 10
                  ? output.businessMaterialCheckpoint()
                  : output.activityCheckpoint();
          case REPOSITORY_KNOWLEDGE -> output.knowledgeCheckpoint();
          case NINE_SECTION_DOCUMENT -> output.reportCheckpoint();
          default -> throw new IllegalStateException("business output artifact key is invalid");
        };
    if (checkpoint == null) {
      throw new IllegalStateException("BUSINESS_ARTIFACT_QUERY_NOT_AVAILABLE");
    }
    if (!(checkpoint.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != step
        || address.moduleNumber() != moduleNumber) {
      throw new IllegalStateException("business output artifact checkpoint is invalid");
    }
    return checkpoint;
  }

  boolean readsReadingMaterials() {
    return readingMaterials;
  }

  AnalysisStepPublicationReference readingMaterialCheckpoint(AnalysisRunOutput output) {
    if (!readingMaterials) {
      throw new IllegalStateException("business output artifact key is invalid");
    }
    AnalysisStepPublicationReference checkpoint = output.readingMaterialCheckpoint();
    if (checkpoint == null) {
      throw new IllegalStateException("BUSINESS_ARTIFACT_QUERY_NOT_AVAILABLE");
    }
    if (checkpoint.address() == null || checkpoint.address().analysisStepKey() != step) {
      throw new IllegalStateException("business output artifact checkpoint is invalid");
    }
    return checkpoint;
  }
}
