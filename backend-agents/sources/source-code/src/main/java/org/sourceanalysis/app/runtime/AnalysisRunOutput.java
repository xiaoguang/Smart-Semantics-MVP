package org.sourceanalysis.app.runtime;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Saved business checkpoints produced by either a material preflight or a complete report run. */
public record AnalysisRunOutput(
    ModulePublicationReference businessMaterialCheckpoint,
    ModulePublicationReference activityCheckpoint,
    ModulePublicationReference knowledgeCheckpoint,
    ModulePublicationReference reportCheckpoint) {

  public AnalysisRunOutput {
    require(businessMaterialCheckpoint, AnalysisStepKey.FLOW_INTERPRETATION, 10, "business-material-builder");
    boolean materialsOnly =
        activityCheckpoint == null && knowledgeCheckpoint == null && reportCheckpoint == null;
    if (!materialsOnly) {
      require(activityCheckpoint, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer");
      require(knowledgeCheckpoint, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "process-explainer");
      require(reportCheckpoint, AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, "business-report-publisher");
    }
    AnalysisRunId owner = runId(businessMaterialCheckpoint);
    if (!materialsOnly
        && !List.of(activityCheckpoint, knowledgeCheckpoint, reportCheckpoint).stream()
            .map(AnalysisRunOutput::runId)
            .allMatch(owner::equals)) {
      throw new IllegalArgumentException("analysis run output checkpoints must share one run ID");
    }
  }

  /** Projects the four application-internal business results to durable run output pointers. */
  public static AnalysisRunOutput from(RepositoryAnalysisRunResult result) {
    Objects.requireNonNull(result, "repository analysis run result");
    BusinessAnalysisWorkflowResult business = result.business();
    return new AnalysisRunOutput(
        business.materials().checkpoint(),
        business.activities().checkpoint(),
        business.knowledge().checkpoint(),
        business.report().checkpoint());
  }

  /** Projects a zero-Provider material-planning result without inventing later checkpoints. */
  public static AnalysisRunOutput from(RepositoryMaterialPlanningResult result) {
    Objects.requireNonNull(result, "repository material planning result");
    return new AnalysisRunOutput(result.materials().checkpoint(), null, null, null);
  }

  /** Returns whether this finished run contains a review-approved business report. */
  public boolean hasCompletedReport() {
    return reportCheckpoint != null;
  }

  private static void require(
      ModulePublicationReference reference,
      AnalysisStepKey expectedStep,
      int expectedNumber,
      String expectedKey) {
    if (reference == null
        || !(reference.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != expectedStep
        || address.moduleNumber() != expectedNumber
        || !expectedKey.equals(address.moduleKey())) {
      throw new IllegalArgumentException("analysis run output checkpoint is invalid");
    }
  }

  private static AnalysisRunId runId(ModulePublicationReference reference) {
    return ((AnalysisStepModuleAddress) reference.address()).runId();
  }
}
