package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.RepositoryAnalysisAgent;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;

/** Filesystem-backed implementation of the public run-creation and observation seam. */
public final class LocalRepositoryAnalysisAgent implements RepositoryAnalysisAgent {

  private final RunStoreHandle store;
  private final RepositoryAnalysisRunCoordinator coordinator;
  private final CompletedReportRenderer reportRenderer;
  private final CompletedBusinessArtifactReader artifactReader;

  /** Uses the already-open configured store without accepting or exposing a filesystem path. */
  public LocalRepositoryAnalysisAgent(RunStoreHandle store) {
    this(store, null, null, null);
  }

  /** Uses an application-owned internal coordinator to execute the final report target. */
  public LocalRepositoryAnalysisAgent(
      RunStoreHandle store, RepositoryAnalysisRunCoordinator coordinator) {
    this(store, coordinator, null, null);
  }

  /** Uses configured internal execution and read-only report-rendering dependencies. */
  public LocalRepositoryAnalysisAgent(
      RunStoreHandle store,
      RepositoryAnalysisRunCoordinator coordinator,
      CompletedReportRenderer reportRenderer) {
    this(store, coordinator, reportRenderer, null);
  }

  /** Uses configured internal execution and read-only report/artifact dependencies. */
  public LocalRepositoryAnalysisAgent(
      RunStoreHandle store,
      RepositoryAnalysisRunCoordinator coordinator,
      CompletedReportRenderer reportRenderer,
      CompletedBusinessArtifactReader artifactReader) {
    this.store = Objects.requireNonNull(store, "run store");
    this.coordinator = coordinator;
    this.reportRenderer = reportRenderer;
    this.artifactReader = artifactReader;
  }

  @Override
  public AnalysisRunReference start(AnalysisRunRequest request) {
    return RunStoreBootstrap.queueAnalysisRun(store, request);
  }

  @Override
  public AnalysisRunReference executeStep(AnalysisStepExecutionRequest request) {
    Objects.requireNonNull(request, "analysis step execution request");
    if (request.targetStep() != AnalysisStepKey.NINE_SECTION_DOCUMENT
        && request.targetStep() != AnalysisStepKey.FLOW_INTERPRETATION) {
      throw new IllegalArgumentException("ANALYSIS_STEP_EXECUTION_NOT_SUPPORTED");
    }
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
      AnalysisRunOutput output =
          request.targetStep() == AnalysisStepKey.FLOW_INTERPRETATION
              ? AnalysisRunOutput.from(coordinator.planMaterials(running.runId()))
              : AnalysisRunOutput.from(coordinator.execute(running.runId()));
      RunStoreBootstrap.recordAnalysisRunOutput(store, running.runId(), output);
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
        run.lifecycleState() == AnalysisRunLifecycleState.FINISHED
            ? RunStoreBootstrap.reopenAnalysisRunOutput(store, run.runId())
                .orElseThrow(() -> new IllegalStateException("ANALYSIS_RUN_OUTPUT_MISSING"))
            : null;
    return new RunInspection(run, output);
  }

  @Override
  public ArtifactView artifact(ArtifactQuery query) {
    Objects.requireNonNull(query, "business artifact query");
    if (artifactReader == null) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_READ_NOT_CONFIGURED");
    }
    AnalysisRunId runId = AnalysisRunId.parse(query.runId());
    AnalysisRunReference run = RunStoreBootstrap.reopenAnalysisRun(store, runId);
    if (run.lifecycleState() != AnalysisRunLifecycleState.FINISHED) {
      throw new IllegalStateException("ANALYSIS_RUN_ARTIFACT_NOT_READY");
    }
    AnalysisRunOutput output =
        RunStoreBootstrap.reopenAnalysisRunOutput(store, runId)
            .orElseThrow(() -> new IllegalStateException("ANALYSIS_RUN_OUTPUT_MISSING"));
    query.businessOutputArtifactKey().checkpoint(output);
    return artifactReader.read(runId, output, query.businessOutputArtifactKey(), query.maxBytes());
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
