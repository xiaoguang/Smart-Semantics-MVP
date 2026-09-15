package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.artifact.AnalysisRunId;
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

/** RED contract for the v3 mixed-owner run-output manifest. */
class ModelBatchAnalysisRunOutputTest {

  @TempDir Path temporaryDirectory;

  @Test
  void roundTripsMaterialsFromStoppedSourceRunAndBusinessOutputsFromNewBatchRun() throws Exception {
    Path storeDirectory = temporaryDirectory.resolve("mixed-owner-output-store");
    Files.createDirectory(storeDirectory);

    AnalysisRunReference sourceRun;
    AnalysisRunReference outputRun;
    AnalysisRunOutput output;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeDirectory)) {
      sourceRun = RunStoreBootstrap.queueAnalysisRun(store, request());
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          sourceRun.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      AnalysisRunReference stoppedSource =
          RunStoreBootstrap.transitionAnalysisRun(
              store,
              sourceRun.runId(),
              AnalysisRunLifecycleState.RUNNING,
              AnalysisRunLifecycleState.FAILED);

      outputRun = RunStoreBootstrap.queueAnalysisRun(store, request());
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          outputRun.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      output = mixedOwnerOutput(stoppedSource.runId(), outputRun.runId());

      RunStoreBootstrap.recordAnalysisRunOutput(store, outputRun.runId(), output);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          outputRun.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);

      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, sourceRun.runId()).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
    }

    try (RunStoreHandle reopenedStore = RunStoreBootstrap.open(storeDirectory)) {
      AnalysisRunOutput reopened =
          RunStoreBootstrap.reopenAnalysisRunOutput(reopenedStore, outputRun.runId()).orElseThrow();

      assertThat(sourceRunId(reopened)).isEqualTo(sourceRun.runId());
      assertThat(reopened.businessMaterialCheckpoint().address().runId())
          .isEqualTo(sourceRun.runId());
      assertThat(reopened.activityCheckpoint().address().runId()).isEqualTo(outputRun.runId());
      assertThat(reopened.knowledgeCheckpoint().address().runId()).isEqualTo(outputRun.runId());
      assertThat(reopened.reportCheckpoint().address().runId()).isEqualTo(outputRun.runId());
      assertThat(
              RunStoreBootstrap.reopenAnalysisRun(reopenedStore, sourceRun.runId())
                  .lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      assertThat(
              RunStoreBootstrap.reopenAnalysisRun(reopenedStore, outputRun.runId())
                  .lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FINISHED);
    }
  }

  @Test
  void rejectsBusinessCheckpointsOwnedByAnotherBatchWithoutChangingEitherRun() throws Exception {
    Path storeDirectory = temporaryDirectory.resolve("wrong-owner-output-store");
    Files.createDirectory(storeDirectory);

    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeDirectory)) {
      AnalysisRunReference sourceRun = RunStoreBootstrap.queueAnalysisRun(store, request());
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          sourceRun.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          sourceRun.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FAILED);

      AnalysisRunReference outputRun = RunStoreBootstrap.queueAnalysisRun(store, request());
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          outputRun.runId(),
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      AnalysisRunId foreignRunId = AnalysisRunId.parse("analysis-run:" + "c".repeat(64));
      AnalysisRunOutput wrongOwner = mixedOwnerOutput(sourceRun.runId(), foreignRunId);

      assertThatThrownBy(
              () -> RunStoreBootstrap.recordAnalysisRunOutput(store, outputRun.runId(), wrongOwner))
          .hasMessage("ANALYSIS_RUN_OUTPUT_INVALID");
      assertThat(RunStoreBootstrap.reopenAnalysisRunOutput(store, outputRun.runId())).isEmpty();
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, sourceRun.runId()).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, outputRun.runId()).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.RUNNING);
    }
  }

  @Test
  void roundTripsAProcessCatalogWithoutInventingAReportCheckpoint() throws Exception {
    Path storeDirectory = temporaryDirectory.resolve("process-catalog-output-store");
    Files.createDirectory(storeDirectory);

    AnalysisRunReference sourceRun;
    AnalysisRunReference activityRun;
    AnalysisRunReference processRun;
    AnalysisRunOutput output;
    try (RunStoreHandle store = RunStoreBootstrap.openForTest(storeDirectory)) {
      sourceRun = stoppedRun(store);
      activityRun = stoppedRun(store);
      processRun = runningRun(store);
      output =
          new AnalysisRunOutput(
              sourceRun.runId(),
              modulePublication(sourceRun.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'),
              modulePublication(activityRun.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 11, 'b'),
              processPublication(processRun.runId(), 'c'),
              null);

      RunStoreBootstrap.recordAnalysisRunOutput(store, processRun.runId(), output);
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          processRun.runId(),
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);
    }

    try (RunStoreHandle store = RunStoreBootstrap.open(storeDirectory)) {
      AnalysisRunOutput reopened =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, processRun.runId()).orElseThrow();
      assertThat(reopened).isEqualTo(output);
      assertThat(reopened.hasCompletedProcesses()).isTrue();
      assertThat(reopened.hasCompletedReport()).isFalse();
      assertThat(reopened.activityCheckpoint().address().runId()).isEqualTo(activityRun.runId());
      assertThat(reopened.knowledgeCheckpoint().address().runId()).isEqualTo(processRun.runId());
    }
  }

  private AnalysisRunReference stoppedRun(RunStoreHandle store) {
    AnalysisRunReference running = runningRun(store);
    return RunStoreBootstrap.transitionAnalysisRun(
        store,
        running.runId(),
        AnalysisRunLifecycleState.RUNNING,
        AnalysisRunLifecycleState.FAILED);
  }

  private AnalysisRunReference runningRun(RunStoreHandle store) {
    AnalysisRunReference queued = RunStoreBootstrap.queueAnalysisRun(store, request());
    return RunStoreBootstrap.transitionAnalysisRun(
        store, queued.runId(), AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);
  }

  private static AnalysisRunId sourceRunId(AnalysisRunOutput output) throws Exception {
    try {
      return (AnalysisRunId) output.getClass().getMethod("sourceRunId").invoke(output);
    } catch (NoSuchMethodException missing) {
      fail("analysis-run-output-v3 must expose an explicit sourceRunId", missing);
      throw new AssertionError("unreachable");
    } catch (InvocationTargetException failure) {
      throw new AssertionError(failure.getCause());
    }
  }

  private static AnalysisRunOutput mixedOwnerOutput(
      AnalysisRunId sourceRunId, AnalysisRunId outputRunId) throws Exception {
    Constructor<AnalysisRunOutput> constructor;
    try {
      constructor =
          AnalysisRunOutput.class.getConstructor(
              AnalysisRunId.class,
              ModulePublicationReference.class,
              ModulePublicationReference.class,
              ModulePublicationReference.class,
              ModulePublicationReference.class);
    } catch (NoSuchMethodException missing) {
      fail(
          "analysis-run-output-v3 must carry sourceRunId alongside four checkpoint references",
          missing);
      throw new AssertionError("unreachable");
    }
    return constructor.newInstance(
        sourceRunId,
        modulePublication(sourceRunId, AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'),
        modulePublication(outputRunId, AnalysisStepKey.FLOW_INTERPRETATION, 11, 'b'),
        modulePublication(outputRunId, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, 'c'),
        modulePublication(outputRunId, AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, 'd'));
  }

  private static ModulePublicationReference modulePublication(
      AnalysisRunId runId, AnalysisStepKey step, int number, char fill) {
    String moduleKey =
        switch (step) {
          case FLOW_INTERPRETATION ->
              number == 10 ? "business-material-builder" : "activity-explainer";
          case REPOSITORY_KNOWLEDGE -> "process-explainer";
          case NINE_SECTION_DOCUMENT -> "business-report-publisher";
          default -> throw new IllegalArgumentException("unexpected test step");
        };
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(runId, step, number, moduleKey),
        ModuleArtifactRoot.parse("module-root:" + String.valueOf(fill).repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static ModulePublicationReference processPublication(AnalysisRunId runId, char fill) {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "business-process-publisher"),
        ModuleArtifactRoot.parse("module-root:" + String.valueOf(fill).repeat(64)),
        ModuleReceiptId.parse("module-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
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
