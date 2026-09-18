package org.sourceanalysis.app.runtime;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Saved legacy business checkpoints or a Step05 reading-material checkpoint for a finished run. */
public record AnalysisRunOutput(
    AnalysisRunId sourceRunId,
    ModulePublicationReference businessMaterialCheckpoint,
    ModulePublicationReference activityCheckpoint,
    ModulePublicationReference knowledgeCheckpoint,
    ModulePublicationReference reportCheckpoint,
    AnalysisStepPublicationReference readingMaterialCheckpoint) {

  public AnalysisRunOutput {
    Objects.requireNonNull(sourceRunId, "source run ID");
    if (readingMaterialCheckpoint != null) {
      if (businessMaterialCheckpoint != null
          || activityCheckpoint != null
          || knowledgeCheckpoint != null
          || reportCheckpoint != null) {
        throw new IllegalArgumentException("analysis run output checkpoint set is invalid");
      }
      requireReadingMaterials(readingMaterialCheckpoint);
      if (!sourceRunId.equals(readingMaterialCheckpoint.address().runId())) {
        throw new IllegalArgumentException("reading material checkpoint must belong to source run");
      }
    } else {
      requireLegacyCheckpoints(
          sourceRunId,
          businessMaterialCheckpoint,
          activityCheckpoint,
          knowledgeCheckpoint,
          reportCheckpoint);
    }
  }

  private static void requireLegacyCheckpoints(
      AnalysisRunId sourceRunId,
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint) {
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

  /** Preserves the v3/v4 constructor shape for historical business output callers. */
  public AnalysisRunOutput(
      AnalysisRunId sourceRunId,
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint) {
    this(
        sourceRunId,
        businessMaterialCheckpoint,
        activityCheckpoint,
        knowledgeCheckpoint,
        reportCheckpoint,
        null);
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
        reportCheckpoint,
        null);
  }

  /** Creates the v5 form for a completed Step05 reading-material publication. */
  public static AnalysisRunOutput readingMaterials(
      AnalysisRunId sourceRunId, AnalysisStepPublicationReference readingMaterialCheckpoint) {
    return new AnalysisRunOutput(sourceRunId, null, null, null, null, readingMaterialCheckpoint);
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

  /** Returns whether this output is the v5 reading-material-only completion. */
  public boolean hasReadingMaterials() {
    return readingMaterialCheckpoint != null;
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

  private static void requireReadingMaterials(AnalysisStepPublicationReference reference) {
    if (reference.address() == null
        || reference.analysisStepArtifactRoot() == null
        || reference.analysisStepReceiptId() == null
        || reference.analysisStepReceiptSha256() == null
        || reference.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS) {
      throw new IllegalArgumentException(
          "analysis run output reading material checkpoint is invalid");
    }
  }
}
