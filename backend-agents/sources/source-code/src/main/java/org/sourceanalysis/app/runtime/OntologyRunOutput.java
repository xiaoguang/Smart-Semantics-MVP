package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** The independently persisted output of one formal ontology operation. */
public record OntologyRunOutput(
    AnalysisRunRequest.OntologyOperation operation,
    AnalysisRunId outputRunId,
    Status status,
    SelectedSourceBasis selectedSourceBasis,
    AnalysisStepPublicationReference evidencePublication,
    ModulePublicationReference ontologyPublication) {

  /** Distinguishes an installed final result from an installed partial or blocked stage report. */
  public enum Status {
    COMPLETED,
    PARTIAL,
    BLOCKED
  }

  public OntologyRunOutput {
    Objects.requireNonNull(operation, "ontology output operation");
    Objects.requireNonNull(outputRunId, "ontology output run ID");
    Objects.requireNonNull(status, "ontology output status");
    if (selectedSourceBasis == null
        || selectedSourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
        || outputRunId.equals(selectedSourceBasis.preparedSource().publication().address().runId())
        || !isEvidencePublication(evidencePublication)
        || !isOwnedOntologyPublication(ontologyPublication, outputRunId, operation)) {
      throw new IllegalArgumentException("ONTOLOGY_RUN_OUTPUT_INVALID");
    }
  }

  /**
   * Preserves the original Task 1 construction seam while defaulting an installed result to done.
   */
  public OntologyRunOutput(
      AnalysisRunRequest.OntologyOperation operation,
      AnalysisRunId outputRunId,
      SelectedSourceBasis selectedSourceBasis,
      AnalysisStepPublicationReference evidencePublication,
      ModulePublicationReference ontologyPublication) {
    this(
        operation,
        outputRunId,
        Status.COMPLETED,
        selectedSourceBasis,
        evidencePublication,
        ontologyPublication);
  }

  private static boolean isEvidencePublication(AnalysisStepPublicationReference publication) {
    return publication != null
        && publication.address() != null
        && publication.analysisStepArtifactRoot() != null
        && publication.analysisStepReceiptId() != null
        && publication.analysisStepReceiptSha256() != null
        && publication.address().analysisStepKey() == AnalysisStepKey.BUSINESS_FLOWS;
  }

  private static boolean isOwnedOntologyPublication(
      ModulePublicationReference publication,
      AnalysisRunId outputRunId,
      AnalysisRunRequest.OntologyOperation operation) {
    if (publication == null
        || !(publication.address() instanceof AnalysisStepModuleAddress address)
        || publication.moduleArtifactRoot() == null
        || publication.moduleReceiptId() == null
        || publication.moduleReceiptSha256() == null
        || !outputRunId.equals(address.runId())
        || address.analysisStepKey() != AnalysisStepKey.REPOSITORY_KNOWLEDGE) {
      return false;
    }
    return switch (operation) {
      case PREPARE_ONTOLOGY ->
          address.moduleNumber() == 2 && "ontology-corpus".equals(address.moduleKey());
      case IDENTIFY_ONTOLOGY ->
          address.moduleNumber() == 3 && "ontology-identification".equals(address.moduleKey());
      case RELATE_ONTOLOGY ->
          address.moduleNumber() == 4 && "ontology-relations".equals(address.moduleKey());
      case PUBLISH_ONTOLOGY ->
          address.moduleNumber() == 5 && "ontology-publisher".equals(address.moduleKey());
    };
  }
}
