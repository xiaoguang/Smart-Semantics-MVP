package org.sourceanalysis.app.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationCheckpointPublisher;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationCheckpointReader;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityHistoricalReuseTestSupport;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityPacketCompletion;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialPublisher;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialPublisher;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.runtime.AnalysisRunLifecycleState;
import org.sourceanalysis.app.runtime.AnalysisRunOutput;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** End-to-end Task 3 contract for adopting a failed historical Step05 Activity batch offline. */
class SourceAnalysisReuseOnlyReplayTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final ArtifactStoreLimits LIMITS =
      new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);

  @TempDir Path temporaryDirectory;

  @Test
  void literalReuseOnlyReopensFailedBatchAndPublishesCurrentCompletionWithoutModelAuth()
      throws Exception {
    temporaryDirectory = temporaryDirectory.toRealPath();
    ToolFixture tools = toolFixture();
    Path repository = temporaryDirectory.resolve("source");
    String commit = initializeCommittedRepository(repository);
    String missingAuthEnvironment = "TASK3_REUSE_ONLY_MUST_NOT_READ_AUTH_20260923";
    assertThat(System.getenv(missingAuthEnvironment)).isNull();
    Path nonexistentExecutable = temporaryDirectory.resolve("not-installed-codex");
    ObjectNode configurationDocument =
        (ObjectNode)
            YAML.readTree(
                repositoryRunYaml(tools, nonexistentExecutable, missingAuthEnvironment, commit));
    ((ObjectNode) configurationDocument.path("source")).put("commitId", commit);
    Path inputPolicyPath =
        Path.of("tools/repository-run/reading-materials-artifact-policy-set-v1.json")
            .toAbsolutePath();
    configurationDocument.put("inputPolicyRegistry", inputPolicyPath.toString());
    ObjectNode sourceConfigurationDocument = configurationDocument.deepCopy();
    sourceConfigurationDocument.put("policyRegistry", inputPolicyPath.toString());
    Path sourceConfigPath =
        writeConfig(
            "source-repository-run.yaml", YAML.writeValueAsString(sourceConfigurationDocument));
    Path configPath =
        writeConfig("model-repository-run.yaml", YAML.writeValueAsString(configurationDocument));
    RepositoryRunConfiguration configuration = RepositoryRunConfiguration.load(configPath);

    ExecutionResult registrationResult = executeConfigured(sourceConfigPath, "capture-local-git");
    assertThat(registrationResult.exitCode()).as(registrationResult.stderr()).isZero();
    String registrationId = value(registrationResult.stdout(), "sourceRegistrationId");
    ExecutionResult startResult =
        executeConfigured(sourceConfigPath, "start", "--source-registration", registrationId);
    assertThat(startResult.exitCode()).as(startResult.stderr()).isZero();
    AnalysisRunId sourceRunId = AnalysisRunId.parse(value(startResult.stdout(), "runId"));

    CodeReadingMaterialSet materials;
    AnalysisStepPublicationReference materialCheckpoint;
    AnalysisRunId failedBatchId;
    ModulePublicationReference historyCheckpoint;
    ActivityExplanationResult seededActivities;
    Path historicalV1BatchRecordPath;
    ObjectNode historicalV1BatchRecord;
    List<String> oldActivityPayloads;
    Map<String, String> oldPrivatePair;
    try (ProgramGraphsPublicFixture fixture =
            ProgramGraphsPublicFixture.createForJavaCodeIndex(
                temporaryDirectory.resolve("program-graph-fixture"),
                configuration.inputPolicyRegistry());
        RunStoreHandle store = RunStoreBootstrap.open(configuration.runStore())) {
      AnalysisRunRequest sourceRequest =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(store, sourceRunId).request();
      ArtifactControls controls = SourceAnalysisExecution.artifactControls(sourceRequest);
      CanonicalModuleArtifactStore sourceModules =
          SourceAnalysisExecution.inputModuleArtifacts(configuration, store);
      CanonicalAnalysisStepArtifactStore sourceSteps =
          SourceAnalysisExecution.inputStepArtifacts(configuration, store);
      CanonicalModuleArtifactStore outputModules =
          SourceAnalysisExecution.moduleArtifacts(configuration, store);

      VerifiedSourceInventoryReference originalSource = fixture.sourceInventory();
      ApplicationDiscoveryReference originalDiscovery = fixture.applicationDiscovery();
      ProgramGraphsReference originalNavigation =
          new ProgramGraphsExecution(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(
                  originalSource,
                  originalDiscovery,
                  SourceAnalysisMarkdownArtifactExecutionTest.minimalJavaSession(fixture),
                  fixture.artifactControls());

      VerifiedSourceInventoryReference source =
          new VerifiedSourceInventoryReference(
              SourceAnalysisMarkdownArtifactExecutionTest.cloneStep(
                  fixture.stepArtifacts().reopen(originalSource.publication()),
                  sourceRunId,
                  List.of(),
                  fixture.moduleArtifacts(),
                  sourceModules,
                  sourceSteps,
                  controls));
      ApplicationDiscoveryReference discovery =
          new ApplicationDiscoveryReference(
              SourceAnalysisMarkdownArtifactExecutionTest.cloneStep(
                  fixture.stepArtifacts().reopen(originalDiscovery.publication()),
                  sourceRunId,
                  List.of(source.publication()),
                  fixture.moduleArtifacts(),
                  sourceModules,
                  sourceSteps,
                  controls));
      ProgramGraphsReference navigation =
          new ProgramGraphsReference(
              SourceAnalysisMarkdownArtifactExecutionTest.cloneStep(
                  fixture.stepArtifacts().reopen(originalNavigation.publication()),
                  sourceRunId,
                  List.of(source.publication(), discovery.publication()),
                  fixture.moduleArtifacts(),
                  sourceModules,
                  sourceSteps,
                  controls));
      String snapshotId = fixture.sourceReader().reopen(originalSource).snapshotId();
      PersistenceMaterialIndex persistence =
          new PersistenceMaterialIndex(
              new PersistenceMaterialIndex.Header(
                  PersistenceMaterialIndex.Status.DISABLED, snapshotId, navigation, List.of()),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of());
      AnalysisStepPublicationReference persistenceCheckpoint =
          new PersistenceMaterialPublisher(sourceModules, sourceSteps)
              .publish(source, discovery, controls, persistence);
      materials =
          SourceAnalysisMarkdownArtifactExecutionTest.materialSet(
              new org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader(sourceSteps)
                  .reopen(navigation),
              source,
              navigation,
              persistenceCheckpoint);
      materialCheckpoint =
          new CodeReadingMaterialPublisher(sourceModules, sourceSteps)
              .publish(discovery, controls, materials);
      RepositoryRunStateV4.write(
          configuration.stateFile(), materialCheckpoint, materials, configuration.canonicalJson());
      RepositoryRunStateV4.SavedState saved =
          RepositoryRunStateV4.load(configuration.stateFile(), configuration.canonicalJson());

      RunStoreBootstrap.transitionAnalysisRun(
          store, sourceRunId, AnalysisRunLifecycleState.QUEUED, AnalysisRunLifecycleState.RUNNING);
      RunStoreBootstrap.recordAnalysisRunOutput(
          store, sourceRunId, AnalysisRunOutput.readingMaterials(sourceRunId, materialCheckpoint));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          sourceRunId,
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FINISHED);

      AnalysisRunRequest failedBatchRequest =
          SourceAnalysisExecution.modelBatchRequest(
              sourceRequest,
              SourceAnalysisExecution.artifactReference(
                  configuration.policyRegistry().reference()));
      failedBatchId = RunStoreBootstrap.queueAnalysisRun(store, failedBatchRequest).runId();
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          failedBatchId,
          AnalysisRunLifecycleState.QUEUED,
          AnalysisRunLifecycleState.RUNNING);
      writeStep05ExecutionConfiguration(
          configuration, configuration.modelJobs(), saved, failedBatchId);

      ModelJobProviderConfiguration provider = configuration.modelJobs().provider("pro");
      ModelRuntimeIdentityV1 runtimeIdentity =
          new ModelRuntimeIdentityV1(
              "codex_subscription", provider.model(), provider.reasoningEffort(), "read-only");
      seededActivities =
          ActivityHistoricalReuseTestSupport.seedDirectPair(
              configuration.modelJobs().journalDirectory(),
              failedBatchId,
              materials,
              "pro",
              provider.quotaScope(),
              runtimeIdentity,
              configuration.activityProfile());
      assertThat(seededActivities.reviewedActivities()).hasSize(1);
      ArtifactControls batchControls = SourceAnalysisExecution.artifactControls(failedBatchRequest);

      AnalysisRunId normalBatchId = AnalysisRunId.parse("analysis-run:" + "b".repeat(64));
      ActivityExplanationResult normalActivities =
          ActivityHistoricalReuseTestSupport.seedDirectPair(
              configuration.modelJobs().journalDirectory(),
              normalBatchId,
              materials,
              "pro",
              provider.quotaScope(),
              runtimeIdentity,
              configuration.activityProfile());
      List<ActivityPacketCompletion> expectedNormalCompletion =
          List.of(
              new ActivityPacketCompletion(
                  materials.packets().get(0).packetId(),
                  materials.packets().get(0).entries().stream()
                      .map(entry -> entry.entryId())
                      .toList(),
                  ActivityPacketCompletion.Completion.COMPLETE,
                  List.of("whole-packet"),
                  List.of("whole-packet"),
                  List.of()));
      assertThat(normalActivities.packetCompletion()).contains(expectedNormalCompletion);
      ModulePublicationReference normalCheckpoint =
          new ActivityExplanationCheckpointPublisher(outputModules)
              .publishStep05(
                  normalBatchId,
                  materialCheckpoint,
                  sourceSteps,
                  batchControls,
                  materials,
                  normalActivities.reviewedActivities(),
                  normalActivities.coverage(),
                  normalActivities.unexplainedActivityEntries(),
                  normalActivities.packetCompletion().orElseThrow());
      ActivityExplanationResult reopenedNormalActivities =
          new ActivityExplanationCheckpointReader(outputModules).reopen(normalCheckpoint);
      writeNormalStep05BatchResult(
          configuration,
          configuration.modelJobs(),
          saved,
          normalBatchId,
          materials,
          new ActivityExplanationResult(
              normalActivities.reviewedActivities(),
              normalActivities.coverage(),
              normalActivities.unexplainedActivityEntries(),
              normalActivities.packetCompletion().orElseThrow(),
              normalCheckpoint));
      Path normalBatchRecordPath =
          activityBatchResultPath(configuration.modelJobs().journalDirectory(), normalBatchId);
      ObjectNode normalBatchRecord =
          (ObjectNode)
              JSON.readTree(Files.readString(normalBatchRecordPath, StandardCharsets.UTF_8));
      assertThat(normalBatchRecord.path("schemaVersion").asText())
          .isEqualTo("activity-batch-result-v2");
      assertThat(normalBatchRecord.path("activityCheckpoint"))
          .isEqualTo(RepositoryRunStateV3.checkpointJson(normalCheckpoint));
      assertThat(normalBatchRecord.path("readingMaterialCheckpoint"))
          .isEqualTo(RepositoryRunStateV4.checkpointJson(materialCheckpoint));
      assertThat(normalBatchRecord.path("adoptedFromModelBatchId").isNull()).isTrue();
      assertThat(normalBatchRecord.path("adoptedActivityCheckpoint").isNull()).isTrue();
      assertThat(normalBatchRecord.path("packetCompletion"))
          .isEqualTo(JSON.valueToTree(expectedNormalCompletion));
      assertThat(reopenedNormalActivities.packetCompletion()).contains(expectedNormalCompletion);
      assertThat(reopenedNormalActivities.reviewedActivities())
          .containsExactlyElementsOf(normalActivities.reviewedActivities());

      MultiPacketActivityFixture multiPacketFixture =
          multiPacketActivityFixture(materials, normalActivities.reviewedActivities().get(0));
      AnalysisRunId multiPacketBatchId = AnalysisRunId.parse("analysis-run:" + "c".repeat(64));
      ModulePublicationReference multiPacketCheckpoint =
          new ActivityExplanationCheckpointPublisher(outputModules)
              .publishStep05(
                  multiPacketBatchId,
                  materialCheckpoint,
                  sourceSteps,
                  batchControls,
                  multiPacketFixture.materials(),
                  multiPacketFixture.reviewedActivities(),
                  multiPacketFixture.coverage(),
                  List.of(),
                  multiPacketFixture.packetCompletion());
      ActivityExplanationResult reopenedMultiPacketActivities =
          new ActivityExplanationCheckpointReader(outputModules).reopen(multiPacketCheckpoint);
      List<ActivityPacketCompletion> canonicalMultiPacketCompletion =
          multiPacketFixture.packetCompletion().stream()
              .sorted(java.util.Comparator.comparing(ActivityPacketCompletion::packetId))
              .toList();
      assertThat(reopenedMultiPacketActivities.packetCompletion())
          .contains(canonicalMultiPacketCompletion);
      writeStep05ExecutionConfiguration(
          configuration, configuration.modelJobs(), saved, multiPacketBatchId);
      writeNormalStep05BatchResult(
          configuration,
          configuration.modelJobs(),
          saved,
          multiPacketBatchId,
          multiPacketFixture.materials(),
          reopenedMultiPacketActivities);
      Path multiPacketBatchRecordPath =
          activityBatchResultPath(configuration.modelJobs().journalDirectory(), multiPacketBatchId);
      ObjectNode multiPacketBatchRecord =
          (ObjectNode)
              JSON.readTree(Files.readString(multiPacketBatchRecordPath, StandardCharsets.UTF_8));
      ArrayNode reversedCompletion = JSON.createArrayNode();
      reversedCompletion.add(multiPacketBatchRecord.path("packetCompletion").get(1));
      reversedCompletion.add(multiPacketBatchRecord.path("packetCompletion").get(0));
      multiPacketBatchRecord.set("packetCompletion", reversedCompletion);
      writeBatchRecord(configuration, multiPacketBatchRecordPath, multiPacketBatchRecord);
      AnalysisRunOutput multiPacketOutput =
          AnalysisRunOutput.step05Activities(
              sourceRunId, materialCheckpoint, multiPacketCheckpoint, true);
      assertThat(
              readStep05ActivityBatchAdoption(
                  configuration.modelJobs(),
                  saved,
                  multiPacketBatchId,
                  multiPacketOutput,
                  reopenedMultiPacketActivities))
          .as("private v2 and public M11 may list the same packets in different orders")
          .isEmpty();

      ObjectNode mismatchedPacketContent = multiPacketBatchRecord.deepCopy();
      ObjectNode mismatchedPacket =
          (ObjectNode) mismatchedPacketContent.path("packetCompletion").get(0);
      mismatchedPacket.putArray("requiredSliceKeys").add("different-scope");
      mismatchedPacket.putArray("completedSliceKeys").add("different-scope");
      writeBatchRecord(configuration, multiPacketBatchRecordPath, mismatchedPacketContent);
      assertThatThrownBy(
              () ->
                  readStep05ActivityBatchAdoption(
                      configuration.modelJobs(),
                      saved,
                      multiPacketBatchId,
                      multiPacketOutput,
                      reopenedMultiPacketActivities))
          .as("packet identity alone cannot hide different public completion content")
          .hasMessage("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
      writeBatchRecord(configuration, multiPacketBatchRecordPath, multiPacketBatchRecord);
      writeStep05ExecutionConfiguration(
          configuration, configuration.modelJobs(), saved, normalBatchId);
      AnalysisRunOutput normalOutput =
          AnalysisRunOutput.step05Activities(
              sourceRunId, materialCheckpoint, normalCheckpoint, true);
      assertMalformedBatchSummaryRejected(
          configuration,
          saved,
          normalBatchId,
          normalOutput,
          reopenedNormalActivities,
          normalBatchRecordPath,
          normalBatchRecord);

      historyCheckpoint =
          ActivityHistoricalReuseTestSupport.installHistoricalV3(
              outputModules,
              configuration.canonicalJson(),
              batchControls,
              failedBatchId,
              seededActivities);
      historicalV1BatchRecordPath =
          activityBatchResultPath(configuration.modelJobs().journalDirectory(), failedBatchId);
      historicalV1BatchRecord =
          historicalBatchV1Record(
              failedBatchId,
              sourceRunId,
              materialCheckpoint,
              historyCheckpoint,
              materials,
              seededActivities);
      Files.write(
          historicalV1BatchRecordPath,
          configuration.canonicalJson().encodeCanonical(historicalV1BatchRecord).copyToByteArray());
      assertThat(
              (ObjectNode)
                  JSON.readTree(
                      Files.readString(historicalV1BatchRecordPath, StandardCharsets.UTF_8)))
          .isEqualTo(historicalV1BatchRecord);
      ActivityExplanationResult reopenedHistoricalV3 =
          new ActivityExplanationCheckpointReader(outputModules).reopen(historyCheckpoint);
      assertMalformedBatchSummaryRejected(
          configuration,
          saved,
          failedBatchId,
          AnalysisRunOutput.step05Activities(
              sourceRunId, materialCheckpoint, historyCheckpoint, true),
          reopenedHistoricalV3,
          historicalV1BatchRecordPath,
          historicalV1BatchRecord);
      RunStoreBootstrap.recordAnalysisRunOutput(
          store,
          failedBatchId,
          AnalysisRunOutput.step05Activities(
              sourceRunId, materialCheckpoint, historyCheckpoint, true));
      RunStoreBootstrap.transitionAnalysisRun(
          store,
          failedBatchId,
          AnalysisRunLifecycleState.RUNNING,
          AnalysisRunLifecycleState.FAILED);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, sourceRunId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FINISHED);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, failedBatchId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      oldActivityPayloads =
          outputModules.reopen(historyCheckpoint).payloads().stream()
              .map(payload -> hex(payload.canonicalUtf8().copyToByteArray()))
              .toList();
      oldPrivatePair =
          privatePairBytes(configuration.modelJobs().journalDirectory(), failedBatchId);
      assertThat(oldPrivatePair).isNotEmpty();
      assertThat(new CodeReadingMaterialReader(sourceSteps).reopen(materialCheckpoint))
          .isEqualTo(materials);
    }

    ExecutionResult reuse =
        executeConfigured(
            configPath,
            "execute-step",
            "--target",
            "flow-interpretation",
            "--reuse-from-model-batch",
            failedBatchId.value(),
            "--reuse-only");

    assertThat(reuse.exitCode()).as(reuse.stderr()).isZero();
    assertThat(reuse.stderr())
        .doesNotContain(
            "MODEL_AUTH_ENV_MISSING",
            missingAuthEnvironment,
            nonexistentExecutable.toString(),
            "ACTIVITY_REUSE_ONLY_OFFLINE_RESOLVER_REQUIRED");
    AnalysisRunId adoptedBatchId = AnalysisRunId.parse(value(reuse.stdout(), "modelBatchId"));
    assertThat(reuse.stdout())
        .contains("sourceRunId=" + sourceRunId.value())
        .contains("lifecycleState=FINISHED");
    ObjectNode adoptedBatchRecord = activityBatchResult(reuse);
    Path adoptedBatchRecordPath = Path.of(value(reuse.stdout(), "activityBatchResult"));

    RepositoryRunConfiguration reopenedConfiguration = RepositoryRunConfiguration.load(configPath);
    try (RunStoreHandle store = RunStoreBootstrap.open(reopenedConfiguration.runStore())) {
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, sourceRunId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FINISHED);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, failedBatchId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput adoptedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, adoptedBatchId).orElseThrow();
      assertThat(adoptedOutput.sourceRunId()).isEqualTo(sourceRunId);
      assertThat(adoptedOutput.readingMaterialCheckpoint()).isEqualTo(materialCheckpoint);
      assertThat(adoptedOutput.activityBatchComplete()).isTrue();
      CanonicalModuleArtifactStore modules =
          SourceAnalysisExecution.moduleArtifacts(reopenedConfiguration, store);
      ActivityExplanationResult adoptedActivities =
          new ActivityExplanationCheckpointReader(modules)
              .reopen(adoptedOutput.activityCheckpoint());
      assertAdoptionBatchRecord(
          adoptedBatchRecord,
          adoptedBatchId,
          sourceRunId,
          materialCheckpoint,
          adoptedOutput.activityCheckpoint(),
          failedBatchId,
          historyCheckpoint,
          adoptedActivities);
      assertThat(adoptedOutput.activityCheckpoint().address().runId()).isEqualTo(adoptedBatchId);
      assertThat(adoptedActivities.packetCompletion())
          .contains(
              List.of(
                  new ActivityPacketCompletion(
                      materials.packets().get(0).packetId(),
                      materials.packets().get(0).entries().stream()
                          .map(entry -> entry.entryId())
                          .toList(),
                      ActivityPacketCompletion.Completion.COMPLETE,
                      List.of("whole-packet"),
                      List.of("whole-packet"),
                      List.of())));
      assertThat(adoptedActivities.reviewedActivities())
          .extracting(activity -> activity.activityId())
          .containsExactlyElementsOf(
              seededActivities.reviewedActivities().stream()
                  .map(activity -> activity.activityId())
                  .toList());
      assertThat(adoptedActivities.reviewedActivities().get(0).sliceKey()).isNull();
      assertThat(adoptedActivities.reviewedActivities().get(0).originalSourceRefs())
          .isEqualTo(seededActivities.reviewedActivities().get(0).originalSourceRefs());
      assertThat(
              modules.reopen(historyCheckpoint).payloads().stream()
                  .map(payload -> hex(payload.canonicalUtf8().copyToByteArray()))
                  .toList())
          .containsExactlyElementsOf(oldActivityPayloads);
      assertThat(
              privatePairBytes(reopenedConfiguration.modelJobs().journalDirectory(), failedBatchId))
          .isEqualTo(oldPrivatePair);
      assertThat(
              new PrivateModelJobResultStore(
                      reopenedConfiguration.modelJobs().journalDirectory(),
                      adoptedBatchId,
                      "activity")
                  .listReviewedResults())
          .as("reuse-only does not create a second private model execution result")
          .isEmpty();
    }

    ExecutionResult chainedReuse =
        executeConfigured(
            configPath,
            "execute-step",
            "--target",
            "flow-interpretation",
            "--reuse-from-model-batch",
            adoptedBatchId.value(),
            "--reuse-only");
    assertThat(chainedReuse.exitCode()).as(chainedReuse.stderr()).isZero();
    assertThat(chainedReuse.stderr())
        .doesNotContain(
            "MODEL_AUTH_ENV_MISSING",
            missingAuthEnvironment,
            nonexistentExecutable.toString(),
            "ACTIVITY_REUSE_ONLY_OFFLINE_RESOLVER_REQUIRED");
    AnalysisRunId chainedBatchId =
        AnalysisRunId.parse(value(chainedReuse.stdout(), "modelBatchId"));
    assertThat(chainedBatchId).isNotEqualTo(adoptedBatchId);
    assertThat(chainedReuse.stdout())
        .contains("sourceRunId=" + sourceRunId.value())
        .contains("lifecycleState=FINISHED");
    ObjectNode chainedBatchRecord = activityBatchResult(chainedReuse);
    Path chainedBatchRecordPath = Path.of(value(chainedReuse.stdout(), "activityBatchResult"));

    try (RunStoreHandle store = RunStoreBootstrap.open(reopenedConfiguration.runStore())) {
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, failedBatchId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      AnalysisRunOutput adoptedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, adoptedBatchId).orElseThrow();
      AnalysisRunOutput chainedOutput =
          RunStoreBootstrap.reopenAnalysisRunOutput(store, chainedBatchId).orElseThrow();
      assertThat(adoptedOutput.activityCheckpoint().address().runId()).isEqualTo(adoptedBatchId);
      assertThat(chainedOutput.activityCheckpoint().address().runId()).isEqualTo(chainedBatchId);
      CanonicalModuleArtifactStore modules =
          SourceAnalysisExecution.moduleArtifacts(reopenedConfiguration, store);
      ActivityExplanationCheckpointReader reader = new ActivityExplanationCheckpointReader(modules);
      ActivityExplanationResult adoptedActivities =
          reader.reopen(adoptedOutput.activityCheckpoint());
      ActivityExplanationResult chainedActivities =
          reader.reopen(chainedOutput.activityCheckpoint());
      assertAdoptionBatchRecord(
          chainedBatchRecord,
          chainedBatchId,
          sourceRunId,
          materialCheckpoint,
          chainedOutput.activityCheckpoint(),
          adoptedBatchId,
          adoptedOutput.activityCheckpoint(),
          chainedActivities);
      assertThat(chainedActivities.packetCompletion())
          .isEqualTo(adoptedActivities.packetCompletion());
      assertThat(chainedActivities.reviewedActivities())
          .containsExactlyElementsOf(adoptedActivities.reviewedActivities());
      assertThat(chainedActivities.reviewedActivities())
          .containsExactlyElementsOf(seededActivities.reviewedActivities());
      assertThat(
              modules.reopen(historyCheckpoint).payloads().stream()
                  .map(payload -> hex(payload.canonicalUtf8().copyToByteArray()))
                  .toList())
          .containsExactlyElementsOf(oldActivityPayloads);
      assertThat(
              privatePairBytes(reopenedConfiguration.modelJobs().journalDirectory(), failedBatchId))
          .isEqualTo(oldPrivatePair);
      assertThat(
              executionConfiguration(
                      reopenedConfiguration.modelJobs().journalDirectory(), adoptedBatchId)
                  .path("reuseFromModelBatchId")
                  .asText())
          .isEqualTo(failedBatchId.value());
      ObjectNode chainedConfiguration =
          executionConfiguration(
              reopenedConfiguration.modelJobs().journalDirectory(), chainedBatchId);
      assertThat(chainedConfiguration.path("schemaVersion").asText())
          .isEqualTo("model-job-execution-config-v5");
      assertThat(chainedConfiguration.path("reuseFromModelBatchId").asText())
          .isEqualTo(adoptedBatchId.value());
      assertThat(chainedConfiguration.path("executionScope").path("mode").asText())
          .isEqualTo("REUSE_ONLY");
      assertThat(chainedConfiguration.path("executionScope").path("packetIds")).isEmpty();
      assertThat(
              new PrivateModelJobResultStore(
                      reopenedConfiguration.modelJobs().journalDirectory(),
                      chainedBatchId,
                      "activity")
                  .listReviewedResults())
          .as("a chained offline adoption imports history without new model execution")
          .isEmpty();
    }

    ExecutionResult inspection =
        executeConfigured(configPath, "inspect", "--run", adoptedBatchId.value());
    assertThat(inspection.exitCode()).as(inspection.stderr()).isZero();
    assertThat(inspection.stdout())
        .contains("runId=" + adoptedBatchId.value())
        .contains("lifecycleState=FINISHED")
        .contains("packetCompletionCount=1");
    assertThat(inspection.stderr())
        .doesNotContain(
            "MODEL_AUTH_ENV_MISSING", missingAuthEnvironment, nonexistentExecutable.toString());

    ObjectNode adoptedExecutionConfiguration =
        executionConfiguration(
            reopenedConfiguration.modelJobs().journalDirectory(), adoptedBatchId);
    assertThat(adoptedExecutionConfiguration.path("executionScope").path("mode").asText())
        .isEqualTo("REUSE_ONLY");
    assertThat(adoptedBatchRecordPath).isRegularFile();
    // Remove metadata only from this disposable @TempDir store to model a damaged explicit source.
    Files.delete(adoptedBatchRecordPath);

    ExecutionResult missingAdoptionMetadata =
        executeConfigured(
            configPath,
            "execute-step",
            "--target",
            "flow-interpretation",
            "--reuse-from-model-batch",
            adoptedBatchId.value(),
            "--reuse-only");
    assertThat(missingAdoptionMetadata.exitCode()).isNotZero();
    assertThat(missingAdoptionMetadata.stderr())
        .contains("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID")
        .doesNotContain(
            "MODEL_AUTH_ENV_MISSING", missingAuthEnvironment, nonexistentExecutable.toString());
    assertThat(adoptedBatchRecordPath).doesNotExist();
    assertThat(chainedBatchRecordPath).isRegularFile();

    try (RunStoreHandle store = RunStoreBootstrap.open(reopenedConfiguration.runStore())) {
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, failedBatchId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FAILED);
      assertThat(RunStoreBootstrap.reopenAnalysisRun(store, adoptedBatchId).lifecycleState())
          .isEqualTo(AnalysisRunLifecycleState.FINISHED);
      CanonicalModuleArtifactStore modules =
          SourceAnalysisExecution.moduleArtifacts(reopenedConfiguration, store);
      assertThat(
              modules.reopen(historyCheckpoint).payloads().stream()
                  .map(payload -> hex(payload.canonicalUtf8().copyToByteArray()))
                  .toList())
          .containsExactlyElementsOf(oldActivityPayloads);
      assertThat(
              privatePairBytes(reopenedConfiguration.modelJobs().journalDirectory(), failedBatchId))
          .isEqualTo(oldPrivatePair);
      assertThat(
              new ActivityExplanationCheckpointReader(modules)
                  .reopen(
                      RunStoreBootstrap.reopenAnalysisRunOutput(store, adoptedBatchId)
                          .orElseThrow()
                          .activityCheckpoint())
                  .reviewedActivities())
          .containsExactlyElementsOf(seededActivities.reviewedActivities());
    }

    ObjectNode invalidHistoricalV1BatchRecord = historicalV1BatchRecord.deepCopy();
    invalidHistoricalV1BatchRecord.remove("activityCheckpoint");
    Files.write(
        historicalV1BatchRecordPath,
        reopenedConfiguration
            .canonicalJson()
            .encodeCanonical(invalidHistoricalV1BatchRecord)
            .copyToByteArray());
    ExecutionResult invalidHistoricalV1Source =
        executeConfigured(
            configPath,
            "execute-step",
            "--target",
            "flow-interpretation",
            "--reuse-from-model-batch",
            failedBatchId.value(),
            "--reuse-only");
    assertThat(invalidHistoricalV1Source.exitCode()).isNotZero();
    assertThat(invalidHistoricalV1Source.stderr())
        .contains("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID")
        .doesNotContain(
            "MODEL_AUTH_ENV_MISSING", missingAuthEnvironment, nonexistentExecutable.toString());
  }

  private ToolFixture toolFixture() throws IOException {
    Path installation = Files.createDirectories(temporaryDirectory.resolve("tools/jdtls"));
    Path javaHome = Files.createDirectories(temporaryDirectory.resolve("tools/jdk"));
    Path java = Files.createDirectories(javaHome.resolve("bin")).resolve("java");
    Files.createDirectories(installation.resolve("plugins"));
    Files.writeString(
        installation.resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"), "launcher\n");
    Files.writeString(
        installation.resolve("plugins/org.eclipse.jdt.ls.core_1.0.jar"), "jdt-ls-core\n");
    Files.writeString(installation.resolve("plugins/org.eclipse.jdt.core_1.0.jar"), "jdt-core\n");
    Files.createDirectories(installation.resolve(platformConfiguration()));
    Files.writeString(
        java,
        "#!/bin/sh\nif [ \"$1\" = \"-version\" ]; then echo 'openjdk version \"21.0.8\"' >&2; exit 0; fi\nexit 0\n",
        StandardCharsets.UTF_8);
    executable(java);
    Files.createDirectories(temporaryDirectory.resolve("journal"));
    Files.createDirectories(temporaryDirectory.resolve("output"));
    Files.createDirectories(temporaryDirectory.resolve("run-store"));
    Files.createDirectories(temporaryDirectory.resolve("capture-workspace"));
    Path repository = Files.createDirectories(temporaryDirectory.resolve("source"));
    for (String name : List.of("spring-web.jar", "spring-core.jar", "spring-jcl.jar")) {
      Files.writeString(temporaryDirectory.resolve(name), name, StandardCharsets.UTF_8);
    }
    return new ToolFixture(installation, javaHome, repository);
  }

  private String initializeCommittedRepository(Path repository) throws Exception {
    Files.createDirectories(repository.resolve("src/main/java/com/example"));
    Files.writeString(
        repository.resolve("src/main/java/com/example/OrderController.java"),
        "package com.example; public class OrderController { public void save() { } }\n",
        StandardCharsets.UTF_8);
    git(repository, "init");
    git(repository, "config", "user.name", "Task 3 test");
    git(repository, "config", "user.email", "task3@example.invalid");
    git(repository, "add", "src/main/java/com/example/OrderController.java");
    git(repository, "commit", "-m", "seed task 3 local source");
    return git(repository, "rev-parse", "HEAD").strip();
  }

  private static String git(Path repository, String... arguments) throws Exception {
    List<String> command = new java.util.ArrayList<>();
    command.add("/usr/bin/git");
    command.add("-C");
    command.add(repository.toString());
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    byte[] output = process.getInputStream().readAllBytes();
    if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IOException("local test git command timed out");
    }
    if (process.exitValue() != 0) {
      throw new IOException(
          "local test git command failed: " + new String(output, StandardCharsets.UTF_8));
    }
    return new String(output, StandardCharsets.UTF_8);
  }

  private Path writeConfig(String fileName, String yaml) throws IOException {
    Path config = temporaryDirectory.resolve(fileName);
    Files.writeString(config, yaml, StandardCharsets.UTF_8);
    return config;
  }

  private String repositoryRunYaml(
      ToolFixture tools, Path nonexistentExecutable, String missingAuthEnvironment, String commit) {
    Path policy = Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
    return """
        schemaVersion: repository-run-config-v3
        policyRegistry: '%s'
        source:
          repositoryPath: '%s'
          declaredRepositoryIdentity: https://github.com/jishenghua/jshERP.git
          commitId: %s
        paths:
          runStore: '%s'
          captureWorkspace: '%s'
          stateFile: '%s'
          gitExecutable: /usr/bin/git
        sourceAnalysis:
          javaEngine: jdt
          jdt:
            installation: '%s'
            javaHome: '%s'
          activityReading: {maxNavigationPages: 128, maxReadingRounds: 4, maxSlicesPerPacket: 32}
          modelJobs:
            journalDirectory: '%s'
            outputDirectory: '%s'
            providers:
              pro:
                kind: codexSubscription
                quotaScope: task3-fixture-account
                executable: '%s'
                auth:
                  mode: chatgpt
                  codexHomeEnv: %s
                capacity: {contextWindowTokens: 256000, providerOverheadTokens: 8000, reasoningReserveTokens: 24000, tokenAccounting: UTF8_BYTE_ESTIMATE}
            routing:
              activity: [pro]
              processGroup: [pro]
              repositorySummary: [pro]
              report: [pro]
        inputs:
          capturePolicy: {schemaVersion: capture-policy-v1, mode: LOCAL_GIT_COMMIT, commitObjectFormat: SHA1, networkAccess: DISABLED, worktreeRead: FORBIDDEN}
          candidateSeries: {schemaVersion: candidate-series-v1, readerCandidateRound: ROUND_1}
          capabilityProfile: {schemaVersion: capability-profile-v1, languages: [JAVA], frameworks: [MYBATIS, SPRING_MVC]}
          verificationPolicy: {schemaVersion: verification-policy-v1, allowUnverified: false, sourceDisposition: VERIFIED}
          profileBundle: {schemaVersion: profile-bundle-v1, discoveryRuleVersion: application-discovery-v2}
          resourceBudget: {schemaVersion: resource-budget-v1, maxSourceFiles: 200000, maxSourceBytes: 2000000000}
          toolchain: {schemaVersion: toolchain-java-local-git-v1, provider: NONE, networkAccess: DISABLED}
          schemaBundle: {schemaVersion: schema-bundle-step05-v1, analysisStepKeys: [verified-source-inventory, application-discovery, program-graphs, proven-code-facts, business-flows]}
          promptBundle: {schemaVersion: prompt-bundle-v1, provider: NONE, messageCount: 0}
          graphProfile: {schemaVersion: graph-profile-v1, graphProfileVersion: program-graphs-v2}
        technical:
          approvedClasspath: ['%s', '%s', '%s']
          selectedEntryIds: []
          inventory: {maxSourceFiles: 200000, maxSourceBytes: 2000000000}
          store: {maxPayloadFiles: 100000, maxArtifactBytes: 1000000000, maxPublicationBytes: 2000000000, maxDirectoryEntries: 1000000}
          readingMaterials: {maxPacketUtf8Bytes: 64000, maxEntriesPerPacket: 16}
        business:
          activity: {maxModelInputBytes: 128000, maxModelOutputBytes: 32000, maxActivitiesPerMaterial: 16, maxValuesPerField: 64, maxTextCharsPerValue: 8000}
          maxMaterialsToStart: 100000
        """
        .formatted(
            policy,
            tools.repository(),
            commit,
            temporaryDirectory.resolve("run-store"),
            temporaryDirectory.resolve("capture-workspace"),
            temporaryDirectory.resolve("materials-state.json"),
            tools.installation(),
            tools.javaHome(),
            temporaryDirectory.resolve("journal"),
            temporaryDirectory.resolve("output"),
            nonexistentExecutable,
            missingAuthEnvironment,
            temporaryDirectory.resolve("spring-web.jar"),
            temporaryDirectory.resolve("spring-core.jar"),
            temporaryDirectory.resolve("spring-jcl.jar"));
  }

  private ExecutionResult executeConfigured(Path configPath, String... arguments) {
    List<String> command = new java.util.ArrayList<>(List.of("--config", configPath.toString()));
    command.addAll(List.of(arguments));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    int exit =
        SourceAnalysisCli.executeConfigured(
            command.toArray(String[]::new),
            new PrintWriter(output, true, StandardCharsets.UTF_8),
            new PrintWriter(errors, true, StandardCharsets.UTF_8));
    return new ExecutionResult(
        exit, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
  }

  private static String value(String output, String key) {
    return output
        .lines()
        .filter(line -> line.startsWith(key + "="))
        .map(line -> line.substring(key.length() + 1))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing " + key + " output in: " + output));
  }

  private static void writeStep05ExecutionConfiguration(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materials,
      AnalysisRunId modelBatch)
      throws Exception {
    Method writer =
        SourceAnalysisExecution.class.getDeclaredMethod(
            "writeStep05ModelJobExecutionConfiguration",
            RepositoryRunConfiguration.class,
            ModelJobsConfiguration.class,
            RepositoryRunStateV4.SavedState.class,
            AnalysisRunId.class,
            AnalysisRunId.class,
            AnalysisRunId.class,
            Set.class);
    writer.setAccessible(true);
    try {
      writer.invoke(null, configuration, modelJobs, materials, modelBatch, null, null, Set.of());
    } catch (java.lang.reflect.InvocationTargetException failure) {
      throw new AssertionError(
          "writing v5 execution configuration fixture failed", failure.getCause());
    }
  }

  private static MultiPacketActivityFixture multiPacketActivityFixture(
      CodeReadingMaterialSet materials, ReviewedActivity template) {
    CodeReadingMaterialSet.Packet original = materials.packets().get(0);
    EntrySeed firstEntry = original.entries().get(0);
    EntrySeed secondEntry =
        new EntrySeed(
            firstEntry.entryId() + ":packet-order-test",
            firstEntry.methodKey(),
            firstEntry.methodRange(),
            firstEntry.trigger());
    CodeReadingMaterialSet.Packet firstPacket =
        packetForEntry(original, "packet:metadata-order-a", firstEntry);
    CodeReadingMaterialSet.Packet secondPacket =
        packetForEntry(original, "packet:metadata-order-b", secondEntry);
    List<CodeReadingMaterialSet.Packet> packets = List.of(firstPacket, secondPacket);
    CodeReadingMaterialSet multiPacketMaterials =
        new CodeReadingMaterialSet(
            materials.header(),
            packets,
            List.of(
                new CodeReadingMaterialSet.EntryCoverage(
                    firstEntry.entryId(),
                    List.of(firstPacket.packetId()),
                    CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                    List.of()),
                new CodeReadingMaterialSet.EntryCoverage(
                    secondEntry.entryId(),
                    List.of(secondPacket.packetId()),
                    CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                    List.of())));
    List<ReviewedActivity> activities =
        List.of(
            activityForPacket(
                template, firstPacket.packetId(), firstEntry.entryId(), "activity:metadata-a"),
            activityForPacket(
                template, secondPacket.packetId(), secondEntry.entryId(), "activity:metadata-b"));
    List<ActivityEntryCoverage> coverage =
        List.of(
            new ActivityEntryCoverage(
                firstEntry.entryId(), "ANALYZED", List.of(activities.get(0).activityId()), null),
            new ActivityEntryCoverage(
                secondEntry.entryId(), "ANALYZED", List.of(activities.get(1).activityId()), null));
    List<ActivityPacketCompletion> completion =
        packets.stream()
            .map(
                packet ->
                    new ActivityPacketCompletion(
                        packet.packetId(),
                        packet.entries().stream().map(EntrySeed::entryId).toList(),
                        ActivityPacketCompletion.Completion.COMPLETE,
                        List.of("whole-packet"),
                        List.of("whole-packet"),
                        List.of()))
            .toList();
    return new MultiPacketActivityFixture(multiPacketMaterials, activities, coverage, completion);
  }

  private static CodeReadingMaterialSet.Packet packetForEntry(
      CodeReadingMaterialSet.Packet template, String packetId, EntrySeed entry) {
    return new CodeReadingMaterialSet.Packet(
        packetId,
        List.of(entry),
        template.methods(),
        template.calls().stream()
            .map(call -> new CodeReadingMaterialSet.EntryCall(entry.entryId(), call.call()))
            .toList(),
        template.persistence(),
        template.sourceReferences(),
        template.unselectedUnits().stream()
            .map(
                unit ->
                    new CodeReadingMaterialSet.UnselectedUnit(
                        entry.entryId(), unit.unitKind(), unit.unitRef(), unit.reason()))
            .toList(),
        template.limitations(),
        template.selfContainedUtf8Bytes());
  }

  private static ReviewedActivity activityForPacket(
      ReviewedActivity template, String packetId, String entryId, String activityId) {
    return new ReviewedActivity(
        activityId,
        packetId,
        List.of(entryId),
        template.name(),
        template.businessPurpose(),
        template.participants(),
        template.businessObjects(),
        template.triggerOrInput(),
        template.conditions(),
        template.activitySteps(),
        template.codeDefinedResults(),
        template.businessRules(),
        template.formulasOrMetrics(),
        template.terms(),
        template.certainty(),
        template.sourceRefs(),
        template.questions(),
        template.scopeLimitations(),
        template.materialSource(),
        null,
        template.originalSourceRefs());
  }

  private static void assertMalformedBatchSummaryRejected(
      RepositoryRunConfiguration configuration,
      RepositoryRunStateV4.SavedState materialsState,
      AnalysisRunId batchId,
      AnalysisRunOutput output,
      ActivityExplanationResult activities,
      Path recordPath,
      ObjectNode validRecord)
      throws IOException {
    assertThat(
            readStep05ActivityBatchAdoption(
                configuration.modelJobs(), materialsState, batchId, output, activities))
        .isEmpty();
    for (String countField :
        List.of(
            "totalPacketCount",
            "selectedPacketCount",
            "analyzedEntryCount",
            "unprocessedEntryCount")) {
      ObjectNode nonNumericCount = validRecord.deepCopy();
      nonNumericCount.put(countField, "not-a-count");
      writeBatchRecord(configuration, recordPath, nonNumericCount);
      assertThatThrownBy(
              () ->
                  readStep05ActivityBatchAdoption(
                      configuration.modelJobs(), materialsState, batchId, output, activities))
          .as("reject nonnumeric v1/v2 summary field %s", countField)
          .hasMessage("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");

      ObjectNode negativeCount = validRecord.deepCopy();
      negativeCount.put(countField, -1);
      writeBatchRecord(configuration, recordPath, negativeCount);
      assertThatThrownBy(
              () ->
                  readStep05ActivityBatchAdoption(
                      configuration.modelJobs(), materialsState, batchId, output, activities))
          .as("reject negative v1/v2 summary field %s", countField)
          .hasMessage("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    }
    ObjectNode nonArrayEntries = validRecord.deepCopy();
    nonArrayEntries.put("unprocessedEntries", "not-an-array");
    writeBatchRecord(configuration, recordPath, nonArrayEntries);
    assertThatThrownBy(
            () ->
                readStep05ActivityBatchAdoption(
                    configuration.modelJobs(), materialsState, batchId, output, activities))
        .as("unprocessed entries must remain a JSON array in both supported schemas")
        .hasMessage("ACTIVITY_BATCH_RESULT_ADOPTION_INVALID");
    writeBatchRecord(configuration, recordPath, validRecord);
  }

  private static java.util.Optional<?> readStep05ActivityBatchAdoption(
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materialsState,
      AnalysisRunId batchId,
      AnalysisRunOutput output,
      ActivityExplanationResult activities) {
    try {
      Method reader =
          SourceAnalysisExecution.class.getDeclaredMethod(
              "readStep05ActivityBatchAdoption",
              ModelJobsConfiguration.class,
              RepositoryRunStateV4.SavedState.class,
              AnalysisRunId.class,
              AnalysisRunOutput.class,
              ActivityExplanationResult.class);
      reader.setAccessible(true);
      return (java.util.Optional<?>)
          reader.invoke(null, modelJobs, materialsState, batchId, output, activities);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new AssertionError("activity batch reader failed", cause);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("activity batch reader seam is unavailable", failure);
    }
  }

  private static void writeBatchRecord(
      RepositoryRunConfiguration configuration, Path path, ObjectNode record) throws IOException {
    Files.write(path, configuration.canonicalJson().encodeCanonical(record).copyToByteArray());
  }

  private record MultiPacketActivityFixture(
      CodeReadingMaterialSet materials,
      List<ReviewedActivity> reviewedActivities,
      List<ActivityEntryCoverage> coverage,
      List<ActivityPacketCompletion> packetCompletion) {}

  private static void writeNormalStep05BatchResult(
      RepositoryRunConfiguration configuration,
      ModelJobsConfiguration modelJobs,
      RepositoryRunStateV4.SavedState materialsState,
      AnalysisRunId batchId,
      CodeReadingMaterialSet materials,
      ActivityExplanationResult activities)
      throws Exception {
    Method writer =
        SourceAnalysisExecution.class.getDeclaredMethod(
            "writeStep05ActivityBatchResult",
            RepositoryRunConfiguration.class,
            ModelJobsConfiguration.class,
            RepositoryRunStateV4.SavedState.class,
            AnalysisRunId.class,
            CodeReadingMaterialSet.class,
            ActivityExplanationResult.class,
            Set.class);
    writer.setAccessible(true);
    try {
      writer.invoke(
          null, configuration, modelJobs, materialsState, batchId, materials, activities, Set.of());
    } catch (java.lang.reflect.InvocationTargetException failure) {
      throw new AssertionError(
          "writing normal v2 activity batch fixture failed", failure.getCause());
    }
  }

  private static ObjectNode historicalBatchV1Record(
      AnalysisRunId batchId,
      AnalysisRunId sourceRunId,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      CodeReadingMaterialSet materials,
      ActivityExplanationResult activities) {
    ObjectNode record = JSON.createObjectNode();
    record.put("schemaVersion", "activity-batch-result-v1");
    record.put("modelBatchId", batchId.value());
    record.put("sourceRunId", sourceRunId.value());
    record.set(
        "readingMaterialCheckpoint",
        RepositoryRunStateV4.checkpointJson(readingMaterialCheckpoint));
    record.set("activityCheckpoint", RepositoryRunStateV3.checkpointJson(activityCheckpoint));
    record.put("totalPacketCount", materials.packets().size());
    record.put("selectedPacketCount", materials.packets().size());
    record.putArray("unprocessedEntries");
    record.put("analyzedEntryCount", activities.coverage().size());
    record.put("unprocessedEntryCount", 0);
    return record;
  }

  private static Path activityBatchResultPath(Path journal, AnalysisRunId batchId)
      throws java.security.NoSuchAlgorithmException {
    byte[] digest =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(batchId.value().getBytes(StandardCharsets.UTF_8));
    return journal.resolve(
        "activity-batch-" + java.util.HexFormat.of().formatHex(digest) + ".json");
  }

  private static ObjectNode executionConfiguration(Path journal, AnalysisRunId batch)
      throws IOException {
    try (var paths = Files.list(journal)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        if (!path.getFileName().toString().startsWith("model-job-execution-")) {
          continue;
        }
        JsonNode value = YAML.readTree(path.toFile());
        if (value.path("modelBatchId").asText().equals(batch.value())) {
          return (ObjectNode) value;
        }
      }
    }
    throw new AssertionError("missing model execution configuration for " + batch.value());
  }

  private static ObjectNode activityBatchResult(ExecutionResult execution) throws IOException {
    Path resultPath = Path.of(value(execution.stdout(), "activityBatchResult"));
    assertThat(resultPath).isRegularFile();
    return (ObjectNode) JSON.readTree(Files.readString(resultPath, StandardCharsets.UTF_8));
  }

  private static void assertAdoptionBatchRecord(
      ObjectNode record,
      AnalysisRunId batchId,
      AnalysisRunId sourceRunId,
      AnalysisStepPublicationReference readingMaterialCheckpoint,
      ModulePublicationReference activityCheckpoint,
      AnalysisRunId adoptedFromBatchId,
      ModulePublicationReference adoptedActivityCheckpoint,
      ActivityExplanationResult publicActivities) {
    assertThat(record.path("schemaVersion").asText()).isEqualTo("activity-batch-result-v2");
    assertThat(record.path("modelBatchId").asText()).isEqualTo(batchId.value());
    assertThat(record.path("sourceRunId").asText()).isEqualTo(sourceRunId.value());
    assertThat(record.path("readingMaterialCheckpoint"))
        .isEqualTo(RepositoryRunStateV4.checkpointJson(readingMaterialCheckpoint));
    assertThat(record.path("activityCheckpoint"))
        .isEqualTo(RepositoryRunStateV3.checkpointJson(activityCheckpoint));
    assertThat(record.path("adoptedFromModelBatchId").asText())
        .isEqualTo(adoptedFromBatchId.value());
    assertThat(record.path("adoptedActivityCheckpoint"))
        .isEqualTo(RepositoryRunStateV3.checkpointJson(adoptedActivityCheckpoint));
    assertThat(record.path("packetCompletion"))
        .isEqualTo(JSON.valueToTree(publicActivities.packetCompletion().orElseThrow()));
  }

  private static Map<String, String> privatePairBytes(Path journal, AnalysisRunId batch)
      throws IOException {
    List<ObjectNode> results =
        new PrivateModelJobResultStore(journal, batch, "activity").listReviewedResults();
    assertThat(results).hasSize(1);
    String jobKey = results.get(0).path("jobKey").asText();
    Path jobDirectory =
        journal
            .resolve("model-jobs")
            .resolve(batch.value().substring("analysis-run:".length()))
            .resolve("activity")
            .resolve(jobKey);
    Map<String, String> values = new LinkedHashMap<>();
    try (var paths = Files.walk(jobDirectory)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        values.put(jobDirectory.relativize(path).toString(), hex(Files.readAllBytes(path)));
      }
    }
    return Map.copyOf(values);
  }

  private static String platformConfiguration() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) return "config_mac";
    if (os.contains("win")) return "config_win";
    return "config_linux";
  }

  private static String hex(byte[] value) {
    return java.util.HexFormat.of().formatHex(value);
  }

  private static void executable(Path path) throws IOException {
    try {
      Files.setPosixFilePermissions(
          path,
          EnumSet.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
              java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      assertThat(path.toFile().setExecutable(true, false)).isTrue();
    }
  }

  private record ToolFixture(Path installation, Path javaHome, Path repository) {}

  private record ExecutionResult(int exitCode, String stdout, String stderr) {}
}
