package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.RepositoryAnalysisAgent;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;

/** Filesystem-backed implementation of the public run-creation and observation seam. */
public final class LocalRepositoryAnalysisAgent implements RepositoryAnalysisAgent {

  private final RunStoreHandle store;
  private final RepositoryAnalysisRunCoordinator coordinator;
  private final CompletedReportRenderer reportRenderer;
  private final CompletedBusinessArtifactReader businessArtifactReader;
  private final CompletedTechnicalArtifactReader technicalArtifactReader;

  /** Uses the already-open configured store without accepting or exposing a filesystem path. */
  public LocalRepositoryAnalysisAgent(RunStoreHandle store) {
    this(store, null, null, null, null);
  }

  /** Uses an application-owned internal coordinator to execute the final report target. */
  public LocalRepositoryAnalysisAgent(
      RunStoreHandle store, RepositoryAnalysisRunCoordinator coordinator) {
    this(store, coordinator, null, null, null);
  }

  /** Uses configured internal execution and read-only report-rendering dependencies. */
  public LocalRepositoryAnalysisAgent(
      RunStoreHandle store,
      RepositoryAnalysisRunCoordinator coordinator,
      CompletedReportRenderer reportRenderer) {
    this(store, coordinator, reportRenderer, null, null);
  }

  /** Uses configured internal execution and read-only report/artifact dependencies. */
  public LocalRepositoryAnalysisAgent(
      RunStoreHandle store,
      RepositoryAnalysisRunCoordinator coordinator,
      CompletedReportRenderer reportRenderer,
      CompletedBusinessArtifactReader businessArtifactReader) {
    this(store, coordinator, reportRenderer, businessArtifactReader, null);
  }

  /** Adds a distinct technical artifact reader without changing the business-reader contract. */
  public LocalRepositoryAnalysisAgent(
      RunStoreHandle store,
      RepositoryAnalysisRunCoordinator coordinator,
      CompletedReportRenderer reportRenderer,
      CompletedBusinessArtifactReader businessArtifactReader,
      CompletedTechnicalArtifactReader technicalArtifactReader) {
    this.store = Objects.requireNonNull(store, "run store");
    this.coordinator = coordinator;
    this.reportRenderer = reportRenderer;
    this.businessArtifactReader = businessArtifactReader;
    this.technicalArtifactReader = technicalArtifactReader;
  }

  @Override
  public AnalysisRunReference start(AnalysisRunRequest request) {
    return RunStoreBootstrap.queueAnalysisRun(store, request);
  }

  @Override
  public AnalysisRunReference executeStep(AnalysisStepExecutionRequest request) {
    Objects.requireNonNull(request, "analysis step execution request");
    if (coordinator == null) {
      throw new IllegalStateException("ANALYSIS_RUN_EXECUTION_NOT_CONFIGURED");
    }
    AnalysisRunReference running =
        RunStoreBootstrap.transitionAnalysisRun(
            store,
            request.runId(),
            AnalysisRunLifecycleState.QUEUED,
            AnalysisRunLifecycleState.RUNNING);
    try {
      AnalysisRunOutput output = coordinator.executeIntent(request);
      RunStoreBootstrap.recordAnalysisRunOutput(store, running.runId(), output);
      if (output.technicalOutput() != null) {
        return RunStoreBootstrap.transitionAnalysisRun(
            store,
            running.runId(),
            AnalysisRunLifecycleState.RUNNING,
            output.technicalOutput().continuationStatus() == TechnicalContinuationStatus.BLOCKED
                ? AnalysisRunLifecycleState.FAILED
                : AnalysisRunLifecycleState.FINISHED);
      }
      if (output.sourcePreparationCheckpoint() != null) {
        return RunStoreBootstrap.transitionAnalysisRun(
            store,
            running.runId(),
            AnalysisRunLifecycleState.RUNNING,
            output.hasUsablePreparedSource()
                ? AnalysisRunLifecycleState.FINISHED
                : AnalysisRunLifecycleState.FAILED);
      }
      if (output.readingMaterialCheckpoint() != null
          && output.hasActivityCheckpoint()
          && !output.hasCompletedActivities()) {
        return RunStoreBootstrap.transitionAnalysisRun(
            store,
            running.runId(),
            AnalysisRunLifecycleState.RUNNING,
            AnalysisRunLifecycleState.FAILED);
      }
      return RunStoreBootstrap.transitionAnalysisRun(
          store,
          running.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);
    } catch (RuntimeException failure) {
      try {
        RunStoreBootstrap.transitionAnalysisRun(
            store,
            running.runId(),
            AnalysisRunLifecycleState.RUNNING,
            AnalysisRunLifecycleState.FAILED);
      } catch (RuntimeException transitionFailure) {
        failure.addSuppressed(transitionFailure);
      }
      throw failure;
    }
  }

  @Override
  public RunInspection inspect(String runId) {
    AnalysisRunReference run =
        RunStoreBootstrap.reopenAnalysisRun(store, AnalysisRunId.parse(runId));
    AnalysisRunOutput output =
        (run.lifecycleState() == AnalysisRunLifecycleState.FINISHED
                || run.lifecycleState() == AnalysisRunLifecycleState.FAILED)
            ? RunStoreBootstrap.reopenAnalysisRunOutput(store, run.runId()).orElse(null)
            : null;
    return new RunInspection(run, output);
  }

  @Override
  public ArtifactView artifact(ArtifactQuery query) {
    Objects.requireNonNull(query, "artifact query");
    return query.technicalArtifactQueryKey() == null
        ? businessArtifact(query)
        : technicalArtifact(query);
  }

  private ArtifactView businessArtifact(ArtifactQuery query) {
    if (businessArtifactReader == null) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_READ_NOT_CONFIGURED");
    }
    AnalysisRunId runId = AnalysisRunId.parse(query.runId());
    AnalysisRunReference run = RunStoreBootstrap.reopenAnalysisRun(store, runId);
    if (run.lifecycleState() != AnalysisRunLifecycleState.FINISHED
        && run.lifecycleState() != AnalysisRunLifecycleState.FAILED) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_NOT_READY");
    }
    AnalysisRunOutput output =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, runId)
            .orElseThrow(() -> new IllegalStateException("ANALYSIS_RUN_OUTPUT_MISSING"));
    if (output.technicalOutput() != null) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_NOT_READY");
    }
    if (run.lifecycleState() == AnalysisRunLifecycleState.FAILED
        && !(output.readingMaterialCheckpoint() != null
            && output.hasActivityCheckpoint()
            && !output.hasCompletedActivities()
            && (query.businessOutputArtifactKey() == BusinessOutputArtifactKey.ACTIVITY_EXPLANATIONS
                || query.businessOutputArtifactKey()
                    == BusinessOutputArtifactKey.ACTIVITY_COVERAGE))) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_NOT_READY");
    }
    if (query.businessOutputArtifactKey().readsReadingMaterials()) {
      query.businessOutputArtifactKey().readingMaterialCheckpoint(output);
    } else {
      query.businessOutputArtifactKey().checkpoint(output);
    }
    return businessArtifactReader.read(
        runId, output, query.businessOutputArtifactKey(), query.maxBytes());
  }

  private ArtifactView technicalArtifact(ArtifactQuery query) {
    if (technicalArtifactReader == null) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_READ_NOT_CONFIGURED");
    }
    AnalysisRunId runId = AnalysisRunId.parse(query.runId());
    AnalysisRunReference run = RunStoreBootstrap.reopenAnalysisRun(store, runId);
    if (run.lifecycleState() != AnalysisRunLifecycleState.FINISHED
        && run.lifecycleState() != AnalysisRunLifecycleState.FAILED) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_NOT_READY");
    }
    AnalysisRunOutput output =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, runId)
            .orElseThrow(() -> new IllegalStateException("ANALYSIS_RUN_OUTPUT_MISSING"));
    if (output.technicalOutput() == null) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_NOT_READY");
    }
    return technicalArtifactReader.read(
        runId, output, query.technicalArtifactQueryKey(), query.entryId(), query.maxBytes());
  }

  @Override
  public RenderedDocumentReference render(String runId) {
    if (reportRenderer == null) {
      throw new IllegalStateException("ANALYSIS_RUN_DOCUMENT_READ_NOT_CONFIGURED");
    }
    AnalysisRunReference run =
        RunStoreBootstrap.reopenAnalysisRun(store, AnalysisRunId.parse(runId));
    if (run.lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
      throw new IllegalStateException("ANALYSIS_RUN_DOCUMENT_NOT_READY");
    }
    AnalysisRunOutput output =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, run.runId())
            .orElseThrow(() -> new IllegalStateException("ANALYSIS_RUN_OUTPUT_MISSING"));
    if (!output.hasCompletedReport()) {
      throw new IllegalStateException("ANALYSIS_RUN_DOCUMENT_NOT_READY");
    }
    return reportRenderer.render(run.runId(), output.reportCheckpoint());
  }
}
