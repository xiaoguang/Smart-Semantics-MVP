package org.sourceanalysis.app.runtime;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Saved business checkpoints produced by material, process-catalog, or complete-report runs. */
public record AnalysisRunOutput(
    AnalysisRunId sourceRunId,
    ModulePublicationReference businessMaterialCheckpoint,
    ModulePublicationReference activityCheckpoint,
    ModulePublicationReference knowledgeCheckpoint,
    ModulePublicationReference reportCheckpoint) {

  public AnalysisRunOutput {
    Objects.requireNonNull(sourceRunId, "source run ID");
    require(
        businessMaterialCheckpoint,
        AnalysisStepKey.FLOW_INTERPRETATION,
        10,
        "business-material-builder");
    boolean materialsOnly =
        activityCheckpoint == null && knowledgeCheckpoint == null && reportCheckpoint == null;
    boolean activitiesOnly =
        activityCheckpoint != null && knowledgeCheckpoint == null && reportCheckpoint == null;
    boolean processCatalog =
        activityCheckpoint != null && knowledgeCheckpoint != null && reportCheckpoint == null;
    boolean completeReport =
        activityCheckpoint != null && knowledgeCheckpoint != null && reportCheckpoint != null;
    if (!(materialsOnly || activitiesOnly || processCatalog || completeReport)) {
      throw new IllegalArgumentException("analysis run output checkpoint set is invalid");
    }
    if (activitiesOnly || processCatalog || completeReport) {
      require(activityCheckpoint, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer");
    }
    if (processCatalog) {
      require(
          knowledgeCheckpoint,
          AnalysisStepKey.REPOSITORY_KNOWLEDGE,
          1,
          "business-process-publisher");
    }
    if (completeReport) {
      require(knowledgeCheckpoint, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "process-explainer");
      require(
          reportCheckpoint, AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, "business-report-publisher");
    }
    if (!sourceRunId.equals(runId(businessMaterialCheckpoint))) {
      throw new IllegalArgumentException("business material checkpoint must belong to source run");
    }
    AnalysisRunId owner = completeReport ? runId(activityCheckpoint) : null;
    if (completeReport
        && !List.of(activityCheckpoint, knowledgeCheckpoint, reportCheckpoint).stream()
            .map(AnalysisRunOutput::runId)
            .allMatch(owner::equals)) {
      throw new IllegalArgumentException("analysis run output checkpoints must share one run ID");
    }
  }

  /** Creates the ordinary same-run form used when material and business results share one owner. */
  public AnalysisRunOutput(
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint) {
    this(
        runId(Objects.requireNonNull(businessMaterialCheckpoint, "business material checkpoint")),
        businessMaterialCheckpoint,
        activityCheckpoint,
        knowledgeCheckpoint,
        reportCheckpoint);
  }

  /** Projects the four application-internal business results to durable run output pointers. */
  public static AnalysisRunOutput from(RepositoryAnalysisRunResult result) {
    Objects.requireNonNull(result, "repository analysis run result");
    BusinessAnalysisWorkflowResult business = result.business();
    return new AnalysisRunOutput(
        runId(business.materials().checkpoint()),
        business.materials().checkpoint(),
        business.activities().checkpoint(),
        business.knowledge().checkpoint(),
        business.report().checkpoint());
  }

  /** Projects a zero-Provider material-planning result without inventing later checkpoints. */
  public static AnalysisRunOutput from(RepositoryMaterialPlanningResult result) {
    Objects.requireNonNull(result, "repository material planning result");
    return new AnalysisRunOutput(
        runId(result.materials().checkpoint()), result.materials().checkpoint(), null, null, null);
  }

  /** Returns whether this finished run contains a review-approved business report. */
  public boolean hasCompletedReport() {
    return reportCheckpoint != null;
  }

  /** Returns whether this run contains a complete reviewed Activity checkpoint. */
  public boolean hasCompletedActivities() {
    return activityCheckpoint != null;
  }

  /** Returns whether this run contains a closed, reader-visible Step07 process catalog. */
  public boolean hasCompletedProcesses() {
    return knowledgeCheckpoint != null;
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
