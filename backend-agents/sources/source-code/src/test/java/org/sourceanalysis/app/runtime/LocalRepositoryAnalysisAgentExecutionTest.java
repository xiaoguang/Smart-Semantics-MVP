package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Exercises the sole public Agent's guarded run state around an existing internal coordinator. */
class LocalRepositoryAnalysisAgentExecutionTest {

  @TempDir Path temporaryDirectory;

  @Test
  void executesActivityAndProcessIntentsThroughTheConfiguredCoordinator() throws Exception {
    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("explicit-intent-store"))) {
      AnalysisRunReference materialRun = stoppedRun(store);
      AnalysisRunReference activityRun = RunStoreBootstrap.queueAnalysisRun(store, request());
      RepositoryAnalysisRunCoordinator activityCoordinator =
          RepositoryAnalysisRunCoordinator.configured(
              execution -> {
                assertThat(execution.intent())
                    .isEqualTo(AnalysisExecutionIntent.EXPLAIN_ACTIVITIES);
                assertThat(execution.exactMaterialId()).isNull();
                return new AnalysisRunOutput(
                    materialRun.runId(),
                    modulePublication(
                        materialRun.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'),
                    modulePublication(
                        execution.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 11, 'd'),
                    null,
                    null);
              });
      LocalRepositoryAnalysisAgent activityAgent =
          new LocalRepositoryAnalysisAgent(store, activityCoordinator);

      AnalysisRunReference activityFinished =
          activityAgent.executeStep(
              new AnalysisStepExecutionRequest(
                  activityRun.runId(), AnalysisExecutionIntent.EXPLAIN_ACTIVITIES, null, null));
      assertThat(activityFinished.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FINISHED);
      assertThat(
              activityAgent.inspect(activityRun.runId().value()).output().hasCompletedActivities())
          .isTrue();

      AnalysisRunReference processRun = RunStoreBootstrap.queueAnalysisRun(store, request());
      RepositoryAnalysisRunCoordinator processCoordinator =
          RepositoryAnalysisRunCoordinator.configured(
              execution -> {
                assertThat(execution.intent())
                    .isEqualTo(AnalysisExecutionIntent.DISCOVER_PROCESSES);
                assertThat(execution.upstreamRunId()).isEqualTo(activityRun.runId());
                return new AnalysisRunOutput(
                    materialRun.runId(),
                    modulePublication(
                        materialRun.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'),
                    modulePublication(
                        activityRun.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 11, 'd'),
                    processPublication(execution.runId(), '7'),
                    null);
              });
      LocalRepositoryAnalysisAgent processAgent =
          new LocalRepositoryAnalysisAgent(store, processCoordinator);

      AnalysisRunReference processFinished =
          processAgent.executeStep(
              new AnalysisStepExecutionRequest(
                  processRun.runId(),
                  AnalysisExecutionIntent.DISCOVER_PROCESSES,
                  activityRun.runId(),
                  null));
      assertThat(processFinished.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FINISHED);
      assertThat(processAgent.inspect(processRun.runId().value()).output().hasCompletedProcesses())
          .isTrue();
    }
  }

  @Test
  void inspectsAFinishedPreviewWithoutInventingAFormalOutput() throws Exception {
    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("preview-inspection-store"))) {
      AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, request());
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          queued.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store);

      RunInspection inspection = agent.inspect(queued.runId().value());

      assertThat(inspection.analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FINISHED);
      assertThat(inspection.output()).isNull();
    }
  }

  @Test
  void persistsFailedWhenTheConfiguredExecutionCannotProduceTheTechnicalPrefix() throws Exception {
    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("failed-agent-run-store"))) {
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              ignored -> {
                throw new IllegalStateException("fixture execution failure");
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request());

      assertThatThrownBy(
              () ->
                  agent.executeStep(
                      new AnalysisStepExecutionRequest(
                          queued.runId(), AnalysisExecutionIntent.PREPARE_MATERIALS, null, null)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("fixture execution failure");

      assertThat(agent.inspect(queued.runId().value()).analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
    }
  }

  @Test
  void finishesAMaterialsOnlyRunWithoutStartingActivitiesProcessesOrAReport() throws Exception {
    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("materials-only-agent-run"))) {
      RepositoryAnalysisRunCoordinator coordinator =
          RepositoryAnalysisRunCoordinator.configured(
              execution ->
                  new AnalysisRunOutput(
                      execution.runId(),
                      modulePublication(
                          execution.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'),
                      null,
                      null,
                      null));
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request());

      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisExecutionIntent.PREPARE_MATERIALS, null, null));

      assertThat(finished.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FINISHED);
      AnalysisRunOutput output = agent.inspect(queued.runId().value()).output();
      assertThat(output.businessMaterialCheckpoint())
          .isEqualTo(
              modulePublication(queued.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'));
      assertThat(output.activityCheckpoint()).isNull();
      assertThat(output.knowledgeCheckpoint()).isNull();
      assertThat(output.reportCheckpoint()).isNull();
    }
  }

  private static RunStoreHandle openStore(Path path) throws Exception {
    Files.createDirectory(path);
    return RunStoreBootstrap.openForTest(path);
  }

  private static AnalysisRunReference stoppedRun(RunStoreHandle store) {
    AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, request());
    RunStoreBootstrap.transitionAnalysisRun(
        store, queued.runId(), AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);
    return RunStoreBootstrap.transitionAnalysisRun(
        store, queued.runId(), AnalysisRunLifecycleState.RUNNING, AnalysisRunLifecycleState.FAILED);
  }

  private static ModulePublicationReference modulePublication(
      org.sourceanalysis.app.artifact.AnalysisRunId runId,
      AnalysisStepKey step,
      int moduleNumber,
      char fill) {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(runId, step, moduleNumber, moduleKey(step, moduleNumber)),
        ModuleArtifactRoot.parse("module-root:" + String.valueOf(fill).repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static ModulePublicationReference processPublication(
      org.sourceanalysis.app.artifact.AnalysisRunId runId, char fill) {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "business-process-publisher"),
        ModuleArtifactRoot.parse("module-root:" + String.valueOf(fill).repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static String moduleKey(AnalysisStepKey step, int moduleNumber) {
    return switch (step) {
      case FLOW_INTERPRETATION ->
          moduleNumber == 10 ? "business-material-builder" : "activity-explainer";
      case REPOSITORY_KNOWLEDGE -> "process-explainer";
      default -> throw new IllegalArgumentException("unexpected synthetic module step");
    };
  }

  private static AnalysisRunRequest request() {
    return new AnalysisRunRequest(
        artifactId("source-registration", 'a'),
        reference("frozen-repository-request", 'b'),
        reference("profile-bundle", 'c'),
        reference("resource-budget", 'd'),
        reference("toolchain", 'e'),
        reference("schema-bundle", 'f'),
        reference("prompt-bundle", '1'),
        null,
        reference("artifact-policy-registry", '2'),
        reference("candidate-series", '3'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static ArtifactId artifactId(String prefix, char fill) {
    return ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64));
  }

  private static ArtifactReference reference(String prefix, char fill) {
    return new ArtifactReference(
        artifactId(prefix, fill), new Sha256Digest(String.valueOf(fill).repeat(64)));
  }
}
