package org.sourceanalysis.app.runtime;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReadiness;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Saved historical checkpoints, a v7 source-preparation checkpoint, or a v8 technical result. */
public record AnalysisRunOutput(
    AnalysisRunId sourceRunId,
    ModulePublicationReference businessMaterialCheckpoint,
    ModulePublicationReference activityCheckpoint,
    ModulePublicationReference knowledgeCheckpoint,
    ModulePublicationReference reportCheckpoint,
    AnalysisStepPublicationReference readingMaterialCheckpoint,
    boolean activityBatchComplete,
    AnalysisStepPublicationReference sourcePreparationCheckpoint,
    SourcePreparationReadiness sourcePreparationReadiness,
    SelectedSourceBasis selectedSourceBasis,
    TechnicalRunOutput technicalOutput) {

  public AnalysisRunOutput {
    if (technicalOutput != null) {
      requireTechnicalOutput(
          sourceRunId,
          businessMaterialCheckpoint,
          activityCheckpoint,
          knowledgeCheckpoint,
          reportCheckpoint,
          readingMaterialCheckpoint,
          activityBatchComplete,
          sourcePreparationCheckpoint,
          sourcePreparationReadiness,
          selectedSourceBasis,
          technicalOutput);
    } else if (sourcePreparationCheckpoint != null) {
      Objects.requireNonNull(sourceRunId, "source run ID");
      requireSourcePreparation(
          sourceRunId,
          businessMaterialCheckpoint,
          activityCheckpoint,
          knowledgeCheckpoint,
          reportCheckpoint,
          readingMaterialCheckpoint,
          activityBatchComplete,
          sourcePreparationCheckpoint,
          sourcePreparationReadiness,
          selectedSourceBasis);
    } else {
      Objects.requireNonNull(sourceRunId, "source run ID");
      if (sourcePreparationReadiness != null) {
        throw new IllegalArgumentException("source preparation readiness needs its checkpoint");
      }
      requireAnalysisCheckpoints(
          sourceRunId,
          businessMaterialCheckpoint,
          activityCheckpoint,
          knowledgeCheckpoint,
          reportCheckpoint,
          readingMaterialCheckpoint,
          activityBatchComplete);
    }
  }

  /** Preserves the pre-v8 complete constructor shape for historical callers and readers. */
  public AnalysisRunOutput(
      AnalysisRunId sourceRunId,
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      boolean activityBatchComplete,
      AnalysisStepPublicationReference sourcePreparationCheckpoint,
      SourcePreparationReadiness sourcePreparationReadiness,
      SelectedSourceBasis selectedSourceBasis) {
    this(
        sourceRunId,
        businessMaterialCheckpoint,
        activityCheckpoint,
        knowledgeCheckpoint,
        reportCheckpoint,
        readingMaterialCheckpoint,
        activityBatchComplete,
        sourcePreparationCheckpoint,
        sourcePreparationReadiness,
        selectedSourceBasis,
        null);
  }

  /** Preserves the previous seven-field construction contract for historical output readers. */
  public AnalysisRunOutput(
      AnalysisRunId sourceRunId,
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      boolean activityBatchComplete) {
    this(
        sourceRunId,
        businessMaterialCheckpoint,
        activityCheckpoint,
        knowledgeCheckpoint,
        reportCheckpoint,
        readingMaterialCheckpoint,
        activityBatchComplete,
        null,
        null,
        null,
        null);
  }

  private static void requireAnalysisCheckpoints(
      AnalysisRunId sourceRunId,
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      boolean activityBatchComplete) {
    if (readingMaterialCheckpoint != null) {
      if (businessMaterialCheckpoint != null || reportCheckpoint != null) {
        throw new IllegalArgumentException("analysis run output checkpoint set is invalid");
      }
      requireReadingMaterials(readingMaterialCheckpoint);
      if (!sourceRunId.equals(readingMaterialCheckpoint.address().runId())) {
        throw new IllegalArgumentException("reading material checkpoint must belong to source run");
      }
      if (activityCheckpoint != null) {
        require(activityCheckpoint, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer");
      } else if (activityBatchComplete) {
        throw new IllegalArgumentException("complete Activity batch requires a checkpoint");
      }
      if (knowledgeCheckpoint != null) {
        require(
            knowledgeCheckpoint,
            AnalysisStepKey.REPOSITORY_KNOWLEDGE,
            1,
            "business-process-publisher");
        if (activityCheckpoint == null || !activityBatchComplete) {
          throw new IllegalArgumentException(
              "Step05 process output requires complete reviewed Activities");
        }
      }
    } else {
      if (activityBatchComplete != (activityCheckpoint != null)) {
        throw new IllegalArgumentException("legacy Activity output completion is invalid");
      }
      requireLegacyCheckpoints(
          sourceRunId,
          businessMaterialCheckpoint,
          activityCheckpoint,
          knowledgeCheckpoint,
          reportCheckpoint);
    }
  }

  /** Preserves the previous six-field construction contract for historical output readers. */
  public AnalysisRunOutput(
      AnalysisRunId sourceRunId,
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint,
      AnalysisStepPublicationReference readingMaterialCheckpoint) {
    this(
        sourceRunId,
        businessMaterialCheckpoint,
        activityCheckpoint,
        knowledgeCheckpoint,
        reportCheckpoint,
        readingMaterialCheckpoint,
        activityCheckpoint != null);
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
        null,
        activityCheckpoint != null);
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
        null,
        activityCheckpoint != null);
  }

  /** Creates the v5 form for a completed Step05 reading-material publication. */
  public static AnalysisRunOutput readingMaterials(
      AnalysisRunId sourceRunId, AnalysisStepPublicationReference readingMaterialCheckpoint) {
    return new AnalysisRunOutput(
        sourceRunId, null, null, null, null, readingMaterialCheckpoint, false);
  }

  /** Registers a new model batch over an immutable Step05 source, including partial results. */
  public static AnalysisRunOutput step05Activities(
      AnalysisRunId sourceRunId,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      boolean activityBatchComplete) {
    return new AnalysisRunOutput(
        sourceRunId,
        null,
        activityCheckpoint,
        null,
        null,
        readingMaterialCheckpoint,
        activityBatchComplete);
  }

  /** Registers Step07 over a separate, completed Step05 Activity model batch. */
  public static AnalysisRunOutput step05Processes(
      AnalysisRunId sourceRunId,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint) {
    return new AnalysisRunOutput(
        sourceRunId,
        null,
        activityCheckpoint,
        knowledgeCheckpoint,
        null,
        readingMaterialCheckpoint,
        true);
  }

  /** Saves only a verified-source-inventory checkpoint and its source readiness result. */
  public static AnalysisRunOutput sourcePreparation(
      AnalysisRunId runId,
      AnalysisStepPublicationReference sourcePreparationCheckpoint,
      SourcePreparationReadiness readiness,
      SelectedSourceBasis selectedSourceBasis) {
    return new AnalysisRunOutput(
        runId,
        null,
        null,
        null,
        null,
        null,
        false,
        sourcePreparationCheckpoint,
        readiness,
        selectedSourceBasis,
        null);
  }

  /** Attaches the persisted v3 source basis to an already-valid analysis output shape. */
  public static AnalysisRunOutput analysisV7(
      AnalysisRunOutput existingShape, SelectedSourceBasis selectedSourceBasis) {
    Objects.requireNonNull(existingShape, "existing analysis output");
    Objects.requireNonNull(selectedSourceBasis, "selected source basis");
    if (existingShape.sourcePreparationCheckpoint() != null) {
      throw new IllegalArgumentException(
          "source preparation output cannot wrap analysis checkpoints");
    }
    return new AnalysisRunOutput(
        existingShape.sourceRunId(),
        existingShape.businessMaterialCheckpoint(),
        existingShape.activityCheckpoint(),
        existingShape.knowledgeCheckpoint(),
        existingShape.reportCheckpoint(),
        existingShape.readingMaterialCheckpoint(),
        existingShape.activityBatchComplete(),
        null,
        null,
        selectedSourceBasis,
        null);
  }

  /** Wraps one v8 technical output without reusing historical output fields. */
  public static AnalysisRunOutput technical(TechnicalRunOutput technicalOutput) {
    Objects.requireNonNull(technicalOutput, "technical output");
    return new AnalysisRunOutput(
        technicalOutput.selectedSourceBasis().preparedSource().publication().address().runId(),
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        technicalOutput.selectedSourceBasis(),
        technicalOutput);
  }

  /** Returns whether this finished run contains a review-approved business report. */
  public boolean hasCompletedReport() {
    return reportCheckpoint != null;
  }

  /** Returns whether this run contains a complete reviewed Activity checkpoint. */
  public boolean hasCompletedActivities() {
    return activityCheckpoint != null
        && (readingMaterialCheckpoint == null || activityBatchComplete);
  }

  /** True also for a verified, partial Activity publication in a failed model batch. */
  public boolean hasActivityCheckpoint() {
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

  /** True when this output carries a basis that names one exact usable prepared source. */
  public boolean hasUsablePreparedSource() {
    return selectedSourceBasis != null
        && selectedSourceBasis.kind() == SelectedSourceBasis.Kind.PREPARED_V1;
  }

  private static void requireTechnicalOutput(
      AnalysisRunId sourceRunId,
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      boolean activityBatchComplete,
      AnalysisStepPublicationReference sourcePreparationCheckpoint,
      SourcePreparationReadiness sourcePreparationReadiness,
      SelectedSourceBasis selectedSourceBasis,
      TechnicalRunOutput technicalOutput) {
    if (sourceRunId == null
        || businessMaterialCheckpoint != null
        || activityCheckpoint != null
        || knowledgeCheckpoint != null
        || reportCheckpoint != null
        || readingMaterialCheckpoint != null
        || activityBatchComplete
        || sourcePreparationCheckpoint != null
        || sourcePreparationReadiness != null
        || selectedSourceBasis == null
        || !selectedSourceBasis.equals(technicalOutput.selectedSourceBasis())
        || !sourceRunId.equals(
            technicalOutput
                .selectedSourceBasis()
                .preparedSource()
                .publication()
                .address()
                .runId())) {
      throw new IllegalArgumentException("TECHNICAL_RUN_OUTPUT_INVALID");
    }
  }

  private static void requireSourcePreparation(
      AnalysisRunId sourceRunId,
      ModulePublicationReference businessMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      ModulePublicationReference knowledgeCheckpoint,
      ModulePublicationReference reportCheckpoint,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      boolean activityBatchComplete,
      AnalysisStepPublicationReference sourcePreparationCheckpoint,
      SourcePreparationReadiness sourcePreparationReadiness,
      SelectedSourceBasis selectedSourceBasis) {
    if (businessMaterialCheckpoint != null
        || activityCheckpoint != null
        || knowledgeCheckpoint != null
        || reportCheckpoint != null
        || readingMaterialCheckpoint != null
        || activityBatchComplete
        || sourcePreparationReadiness == null
        || sourcePreparationCheckpoint.address().analysisStepKey()
            != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
        || !sourceRunId.equals(sourcePreparationCheckpoint.address().runId())) {
      throw new IllegalArgumentException("source preparation output checkpoint set is invalid");
    }
    boolean ready =
        sourcePreparationReadiness == SourcePreparationReadiness.READY
            || sourcePreparationReadiness == SourcePreparationReadiness.READY_WITH_EXCLUSIONS;
    if (ready
        && (selectedSourceBasis == null
            || selectedSourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
            || !sourcePreparationCheckpoint.equals(
                selectedSourceBasis.preparedSource().publication()))) {
      throw new IllegalArgumentException("usable source preparation output needs its exact basis");
    }
    if (!ready && selectedSourceBasis != null) {
      throw new IllegalArgumentException(
          "non-ready source preparation output cannot expose a source basis");
    }
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
