package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.document.BusinessReport;
import org.sourceanalysis.app.analysis.document.BusinessReportPublication;
import org.sourceanalysis.app.analysis.document.BusinessReportValidation;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
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
  void executesTheFinalDocumentTargetAndPersistsFinishedForAQueuedRun() throws Exception {
    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("agent-run-store"))) {
      RepositoryAnalysisRunCoordinator coordinator =
          new RepositoryAnalysisRunCoordinator(
              LocalRepositoryAnalysisAgentExecutionTest::technicalDiscovery,
              (inventory, discovery) -> business(inventory.publication().address().runId()));
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request());

      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisStepKey.NINE_SECTION_DOCUMENT));

      assertThat(finished.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FINISHED);
      RunInspection inspection = agent.inspect(queued.runId().value());
      assertThat(inspection.analysisRun()).isEqualTo(finished);
      assertThat(inspection.output()).isNotNull();
      assertThat(inspection.output().businessMaterialCheckpoint())
          .isEqualTo(
              modulePublication(queued.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a'));
      assertThat(inspection.output().activityCheckpoint())
          .isEqualTo(
              modulePublication(queued.runId(), AnalysisStepKey.FLOW_INTERPRETATION, 11, 'd'));
      assertThat(inspection.output().knowledgeCheckpoint())
          .isEqualTo(
              modulePublication(queued.runId(), AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, '7'));
      assertThat(inspection.output().reportCheckpoint())
          .isEqualTo(
              modulePublication(queued.runId(), AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, 'a'));
    }
  }

  @Test
  void persistsFailedWhenTheConfiguredExecutionCannotProduceTheTechnicalPrefix() throws Exception {
    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("failed-agent-run-store"))) {
      RepositoryAnalysisRunCoordinator coordinator =
          new RepositoryAnalysisRunCoordinator(
              ignored -> {
                throw new IllegalStateException("fixture technical failure");
              },
              (inventory, discovery) -> {
                throw new AssertionError("business continuation must not start");
              });
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request());

      assertThatThrownBy(
              () ->
                  agent.executeStep(
                      new AnalysisStepExecutionRequest(
                          queued.runId(), AnalysisStepKey.NINE_SECTION_DOCUMENT)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("fixture technical failure");

      assertThat(agent.inspect(queued.runId().value()).analysisRun().lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
    }
  }

  @Test
  void finishesAMaterialsOnlyRunWithoutStartingActivitiesProcessesOrAReport() throws Exception {
    try (RunStoreHandle store = openStore(temporaryDirectory.resolve("materials-only-agent-run"))) {
      RepositoryAnalysisRunCoordinator coordinator = materialPlanningCoordinator();
      LocalRepositoryAnalysisAgent agent = new LocalRepositoryAnalysisAgent(store, coordinator);
      AnalysisRunReference queued = agent.start(request());

      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(), AnalysisStepKey.FLOW_INTERPRETATION));

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

  private static RepositoryAnalysisRunCoordinator materialPlanningCoordinator() throws Exception {
    try {
      @SuppressWarnings("unchecked")
      java.lang.reflect.Constructor<RepositoryAnalysisRunCoordinator> constructor =
          RepositoryAnalysisRunCoordinator.class.getDeclaredConstructor(
              Function.class, BiFunction.class, BiFunction.class);
      Function<org.sourceanalysis.app.artifact.AnalysisRunId, TechnicalDiscoveryWorkflowResult>
          technical = LocalRepositoryAnalysisAgentExecutionTest::technicalDiscovery;
      BiFunction<
              VerifiedSourceInventoryReference,
              ApplicationDiscoveryReference,
              BusinessMaterialBuildResult>
          materials =
              (inventory, discovery) ->
                  new BusinessMaterialBuildResult(
                      new BusinessMaterialSet("materials-only", List.of(), List.of()),
                      modulePublication(
                          inventory.publication().address().runId(),
                          AnalysisStepKey.FLOW_INTERPRETATION,
                          10,
                          'a'));
      BiFunction<
              VerifiedSourceInventoryReference,
              ApplicationDiscoveryReference,
              BusinessAnalysisWorkflowResult>
          business =
              (inventory, discovery) -> {
                throw new AssertionError("materials-only execution must not start business models");
              };
      return constructor.newInstance(new Object[] {technical, materials, business});
    } catch (ReflectiveOperationException missing) {
      fail("MATERIALS_ONLY_RUNTIME_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static TechnicalDiscoveryWorkflowResult technicalDiscovery(
      org.sourceanalysis.app.artifact.AnalysisRunId runId) {
    return new TechnicalDiscoveryWorkflowResult(
        new VerifiedSourceInventoryReference(
            stepPublication(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '4')),
        new ApplicationDiscoveryReference(
            stepPublication(runId, AnalysisStepKey.APPLICATION_DISCOVERY, '5')));
  }

  private static AnalysisStepPublicationReference stepPublication(
      org.sourceanalysis.app.artifact.AnalysisRunId runId, AnalysisStepKey step, char fill) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, step),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + String.valueOf(fill).repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + String.valueOf(fill).repeat(64)),
        new Sha256Digest(String.valueOf(fill).repeat(64)));
  }

  private static BusinessAnalysisWorkflowResult business(
      org.sourceanalysis.app.artifact.AnalysisRunId runId) {
    ModulePublicationReference materials =
        modulePublication(runId, AnalysisStepKey.FLOW_INTERPRETATION, 10, 'a');
    ModulePublicationReference activities =
        modulePublication(runId, AnalysisStepKey.FLOW_INTERPRETATION, 11, 'd');
    ModulePublicationReference knowledge =
        modulePublication(runId, AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, '7');
    ModulePublicationReference report =
        modulePublication(runId, AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, 'a');
    return new BusinessAnalysisWorkflowResult(
        new BusinessMaterialBuildResult(
            new BusinessMaterialSet("synthetic-material-set", List.of(), List.of()), materials),
        new ActivityExplanationResult(List.of(), List.of(), activities),
        new RepositoryBusinessKnowledge(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, knowledge),
        new BusinessReportPublication(
            new BusinessReport("synthetic report", List.of()),
            "# synthetic report\n",
            List.of(),
            new BusinessReportValidation("VALID", 0, true, true),
            report));
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

  private static String moduleKey(AnalysisStepKey step, int moduleNumber) {
    return switch (step) {
      case FLOW_INTERPRETATION ->
          moduleNumber == 10 ? "business-material-builder" : "activity-explainer";
      case REPOSITORY_KNOWLEDGE -> "process-explainer";
      case NINE_SECTION_DOCUMENT -> "business-report-publisher";
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
