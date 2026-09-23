package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.cli.SourceAnalysisTestPolicyRegistry;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

class ActivityHistoricalReuseResolverTest {

  @Test
  void recoversCompleteDirectPairAgainstHistoricalV3WithoutChangingReviewedRows(
      @TempDir Path temporary) throws Exception {
    CodeReadingMaterialSet materials =
        ActivityStageAttemptStoreTest.persistedSinglePacket(
            Files.createDirectory(temporary.resolve("step05")));
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "d".repeat(64));
    CompleteDirectProvider seedingProvider = new CompleteDirectProvider();
    ActivityExplanationResult seeded =
        ActivityStageAttemptStoreTest.explain(
            ActivityStageAttemptStoreTest.configuredExplainer(seedingProvider, journal), materials);
    assertThat(seedingProvider.taskKinds()).containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW");
    assertThat(seeded.reviewedActivities()).hasSize(1);
    assertThat(seeded.unexplainedActivityEntries()).isEmpty();

    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Path policyFile =
        Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisTestPolicyRegistry.load(policyFile, canonicalJson);
    ArtifactControls controls = ProgramGraphsPublicFixture.controlsForRuntimeTest(policies);
    ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
    Path outputRoot = Files.createDirectory(temporary.resolve("historical-m11-store"));
    ModulePublicationReference historicalCheckpoint;
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      historicalCheckpoint =
          ActivityExplanationHistoricalV3Fixture.install(
              modules,
              canonicalJson,
              controls,
              sourceBatch,
              seeded.reviewedActivities(),
              seeded.coverage(),
              seeded.unexplainedActivityEntries());
    }

    ActivityExplanationResult historical;
    try (RunStoreHandle handle = RunStoreBootstrap.open(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      historical = new ActivityExplanationCheckpointReader(modules).reopen(historicalCheckpoint);
    }
    assertThat(historical.packetCompletion()).as("M11 v3 carries no completion field").isEmpty();
    assertThat(historical.checkpoint()).isEqualTo(historicalCheckpoint);
    assertThat(historicalCheckpoint.address().runId()).isEqualTo(sourceBatch);

    ActivityExplanationResult reused =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, sourceBatch);

    assertThat(reused.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(reused.coverage()).isEqualTo(historical.coverage());
    assertThat(reused.unexplainedActivityEntries())
        .isEqualTo(historical.unexplainedActivityEntries());
    assertThat(reused.checkpoint()).isEqualTo(historicalCheckpoint);
    assertThat(reused.packetCompletion())
        .contains(
            List.of(
                new ActivityPacketCompletion(
                    materials.packets().get(0).packetId(),
                    materials.packets().get(0).entries().stream()
                        .map(value -> value.entryId())
                        .toList(),
                    ActivityPacketCompletion.Completion.COMPLETE,
                    List.of("whole-packet"),
                    List.of("whole-packet"),
                    List.of())));
    assertThat(reused.reviewedActivities().get(0).sliceKey()).isNull();
    assertThat(reused.reviewedActivities().get(0).originalSourceRefs())
        .isEqualTo(historical.reviewedActivities().get(0).originalSourceRefs());
    assertThat(seedingProvider.taskKinds())
        .as("the resolver consumes the existing pair and does not perform new generation")
        .containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW");
  }

  @Test
  void followsExplicitReusedFromChainWhenSelectedBatchHasNoLocalStageCopies(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials =
        ActivityStageAttemptStoreTest.persistedSinglePacket(
            Files.createDirectory(temporary.resolve("step05")));
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId originalBatch = AnalysisRunId.parse("analysis-run:" + "d".repeat(64));
    AnalysisRunId selectedBatch = AnalysisRunId.parse("analysis-run:" + "e".repeat(64));
    CompleteDirectProvider originalProvider = new CompleteDirectProvider();
    ActivityExplanationResult original =
        ActivityStageAttemptStoreTest.explain(
            ActivityStageAttemptStoreTest.configuredExplainer(originalProvider, journal),
            materials);
    assertThat(originalProvider.taskKinds()).containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW");

    CompleteDirectProvider reuseProvider = new CompleteDirectProvider();
    ActivityExplanationResult reused =
        ActivityStageAttemptStoreTest.explain(
            ActivityExplainer.forExecution(
                execution(journal, selectedBatch, originalBatch, reuseProvider)),
            materials);
    assertThat(reuseProvider.taskKinds())
        .as("the selected batch imports the existing pair without new model calls")
        .isEmpty();

    PrivateModelJobResultStore selectedResults =
        new PrivateModelJobResultStore(journal, selectedBatch, "activity");
    var childResult = selectedResults.listReviewedResults().get(0);
    assertThat(childResult.path("reusedFromModelBatchId").asText())
        .isEqualTo(originalBatch.value());
    String jobKey = childResult.path("jobKey").asText();
    Path childJob = modelJobDirectory(journal, selectedBatch, jobKey);
    assertThat(childJob.resolve("DRAFT/success.json")).doesNotExist();
    assertThat(childJob.resolve("REVIEW/success.json")).doesNotExist();
    assertThat(childJob.resolve("reviewed-result.json")).isRegularFile();
    assertThat(
            new PrivateModelJobResultStore(journal, originalBatch, "activity")
                .readStageSuccess(jobKey, "DRAFT"))
        .isPresent();
    assertThat(
            new PrivateModelJobResultStore(journal, originalBatch, "activity")
                .readStageSuccess(jobKey, "REVIEW"))
        .isPresent();

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("selected-history")), selectedBatch, reused);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, selectedBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion().orElseThrow())
        .singleElement()
        .extracting(ActivityPacketCompletion::completion)
        .isEqualTo(ActivityPacketCompletion.Completion.COMPLETE);
    assertThat(resolved.checkpoint()).isEqualTo(historical.checkpoint());
    assertThat(resolved.reviewedActivities().get(0).sliceKey()).isNull();
  }

  @Test
  void rejectsDamagedStageSuccessClaimInsteadOfDowngradingItToUnknown(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials =
        ActivityStageAttemptStoreTest.persistedSinglePacket(
            Files.createDirectory(temporary.resolve("step05")));
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "d".repeat(64));
    CompleteDirectProvider provider = new CompleteDirectProvider();
    ActivityExplanationResult seeded =
        ActivityStageAttemptStoreTest.explain(
            ActivityStageAttemptStoreTest.configuredExplainer(provider, journal), materials);
    assertThat(provider.taskKinds()).containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW");
    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    String jobKey =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity")
            .listReviewedResults()
            .get(0)
            .path("jobKey")
            .asText();
    Path draftSuccess =
        modelJobDirectory(journal, sourceBatch, jobKey).resolve("DRAFT/success.json");
    assertThat(draftSuccess).isRegularFile();
    Files.writeString(
        draftSuccess, "{}", StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

    assertThatThrownBy(
            () ->
                new ActivityHistoricalReuseResolver(journal)
                    .resolve(materials, historical, sourceBatch))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ACTIVITY_HISTORICAL_REUSE_PROVENANCE_INVALID");
  }

  @Test
  void resolvesScopedAggregateAndSliceRowsForOnePacketFromSavedRequiredScopes(
      @TempDir Path temporary) throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    CodeReadingMaterialSet.Packet packet = materials.packets().get(0);
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
    ActivityLargePacketFormalEntryTest.FormalLargePacketProvider provider =
        new ActivityLargePacketFormalEntryTest.FormalLargePacketProvider();
    ActivityExplanationResult seeded =
        ActivityLargePacketFormalEntryTest.configuredFormalExplainer(journal, sourceBatch, provider)
            .explain(
                new ExplainCodeReadingMaterialsRequest(
                    materials, new ActivityExplanationProfile(20_000, 8_000, 4, 32, 2_000), 1));

    assertThat(seeded.reviewedActivities()).hasSize(2);
    List<ObjectNode> savedResults =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity").listReviewedResults();
    assertThat(savedResults)
        .filteredOn(
            result -> "activity-packet-result-v1".equals(result.path("schemaVersion").asText()))
        .singleElement()
        .satisfies(
            aggregate -> {
              assertThat(aggregate.path("materialId").asText()).isEqualTo(packet.packetId());
              assertThat(aggregate.path("pipeline").asText())
                  .isEqualTo("activity-reading-plan-slices-v1");
            });
    assertThat(savedResults)
        .filteredOn(
            result -> "model-job-reviewed-result-v4".equals(result.path("schemaVersion").asText()))
        .hasSize(2)
        .allSatisfy(
            slice -> {
              assertThat(slice.path("materialId").asText()).isEqualTo(packet.packetId());
              assertThat(slice.path("pipeline").asText()).isEqualTo("activity-draft-review-v3");
            });
    List<String> providerCallsBeforeResolution = provider.taskKinds();
    assertThat(providerCallsBeforeResolution).filteredOn("ACTIVITY_DRAFT"::equals).hasSize(2);
    assertThat(providerCallsBeforeResolution).filteredOn("ACTIVITY_REVIEW"::equals).hasSize(2);

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, sourceBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion())
        .contains(
            List.of(
                new ActivityPacketCompletion(
                    packet.packetId(),
                    packet.entries().stream().map(value -> value.entryId()).toList(),
                    ActivityPacketCompletion.Completion.COMPLETE,
                    List.of("slice-m2", "slice-m3"),
                    List.of("slice-m2", "slice-m3"),
                    List.of())));
    assertThat(provider.taskKinds()).isEqualTo(providerCallsBeforeResolution);
  }

  @Test
  void resolvesCompleteV1PlanFromItsSavedScopedPacketsAndStages(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));
    ActivityExplanationResult seeded = seedCompleteScopedPacket(journal, sourceBatch, materials);
    PrivateModelJobResultStore results =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity");
    ObjectNode aggregate = scopedAggregate(results);
    String jobKey = aggregate.path("jobKey").asText();
    ObjectNode originalV2Plan = results.readActivityReadingPlan(jobKey).orElseThrow();
    assertThat(originalV2Plan.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v2");

    ObjectNode historicalV1Plan = historicalV1PlanFromV2(originalV2Plan);
    assertThat(fieldNames(historicalV1Plan))
        .containsExactlyInAnyOrder(
            "decisions",
            "mode",
            "navigationPages",
            "remainingNavigationPages",
            "packetId",
            "schemaVersion",
            "selectedUnitKeys",
            "slices",
            "totalNavigationPages",
            "unitDispositions",
            "unknowns",
            "unreadUnitKeys");
    assertThat(historicalV1Plan.path("schemaVersion").asText())
        .isEqualTo("activity-reading-plan-v1");
    assertThat(historicalV1Plan.has("finalSliceKeys")).isFalse();
    assertThat(historicalV1Plan.path("slices").size()).isEqualTo(2);
    for (JsonNode decision : historicalV1Plan.path("decisions")) {
      assertThat(fieldNames(decision))
          .containsExactlyInAnyOrder(
              "requestedNavigationPages", "requestedUnitKeys", "slices", "unknowns");
    }

    Path savedPlan =
        modelJobDirectory(journal, sourceBatch, jobKey).resolve("decision-result.json");
    assertThat(savedPlan).isRegularFile();
    Files.delete(savedPlan);
    results.writeDecision(jobKey, historicalV1Plan);
    ObjectNode persistedV1 = results.readActivityReadingPlan(jobKey).orElseThrow();
    ActivityMaterialView view =
        new ActivityMaterialProjector()
            .project(materials.packets().get(0), new ActivityExplanationProfile(1, 1, 1, 1, 1));
    ActivityReadingPlan reopenedPlan = ActivityReadingCoordinator.reopenSaved(view, persistedV1);
    assertThat(reopenedPlan.sliceKeys()).containsExactly("slice-m2", "slice-m3");
    assertThat(
            new CanonicalJsonCodec()
                .parseCanonical(reopenedPlan.readingPackets().get(1).modelInputJson()))
        .isEqualTo(persistedV1.path("slices").get(1).path("readingPacket"));

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, sourceBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion())
        .contains(
            List.of(
                new ActivityPacketCompletion(
                    materials.packets().get(0).packetId(),
                    materials.packets().get(0).entries().stream()
                        .map(value -> value.entryId())
                        .toList(),
                    ActivityPacketCompletion.Completion.COMPLETE,
                    List.of("slice-m2", "slice-m3"),
                    List.of("slice-m2", "slice-m3"),
                    List.of())));
  }

  @Test
  void resolvesLatestSameKeyV1DefinitionDespiteRetainedHistoricalCapacityUnknown(
      @TempDir Path temporary) throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "c".repeat(64));
    ActivityExplanationResult seeded = seedCompleteScopedPacket(journal, sourceBatch, materials);
    PrivateModelJobResultStore results =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity");
    ObjectNode aggregate = scopedAggregate(results);
    String jobKey = aggregate.path("jobKey").asText();
    ObjectNode historicalV1Plan =
        historicalV1PlanFromV2(results.readActivityReadingPlan(jobKey).orElseThrow());
    ObjectNode savedSlice = sliceDefinition(historicalV1Plan, "slice-m3");
    ObjectNode supersededDefinition = savedSlice.deepCopy();
    supersededDefinition.put("scope", "earlier same-key scope before capacity reduction");
    String historicalCapacityUnknown =
        "INPUT_CAPACITY_EXCEEDED:slice-m3:historical-revision-already-reviewed";
    historicalV1Plan.putArray("unknowns").add(historicalCapacityUnknown);
    appendV1Decision(historicalV1Plan, supersededDefinition, historicalCapacityUnknown);
    appendV1Decision(historicalV1Plan, savedSlice, null);
    ObjectNode persistedV1 = persistV1Plan(results, journal, sourceBatch, jobKey, historicalV1Plan);

    ActivityMaterialView view =
        new ActivityMaterialProjector()
            .project(materials.packets().get(0), new ActivityExplanationProfile(1, 1, 1, 1, 1));
    ActivityReadingPlan reopenedPlan = ActivityReadingCoordinator.reopenSaved(view, persistedV1);
    assertThat(reopenedPlan.sliceKeys()).containsExactly("slice-m2", "slice-m3");
    assertThat(persistedV1.path("unknowns").isArray()).isTrue();
    assertThat(persistedV1.path("unknowns").size()).isEqualTo(1);
    assertThat(persistedV1.path("unknowns").get(0).asText()).isEqualTo(historicalCapacityUnknown);
    assertThat(reopenedPlan.slices().get(1).scope()).isEqualTo(savedSlice.path("scope").asText());

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, sourceBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion())
        .contains(
            List.of(
                new ActivityPacketCompletion(
                    materials.packets().get(0).packetId(),
                    materials.packets().get(0).entries().stream()
                        .map(value -> value.entryId())
                        .toList(),
                    ActivityPacketCompletion.Completion.COMPLETE,
                    List.of("slice-m2", "slice-m3"),
                    List.of("slice-m2", "slice-m3"),
                    List.of())));
  }

  @Test
  void laterSameKeyV1DefinitionDoesNotCompleteAnEarlierMatchingSavedPacket(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "f".repeat(64));
    ActivityExplanationResult seeded = seedCompleteScopedPacket(journal, sourceBatch, materials);
    PrivateModelJobResultStore results =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity");
    ObjectNode aggregate = scopedAggregate(results);
    String jobKey = aggregate.path("jobKey").asText();
    ObjectNode historicalV1Plan =
        historicalV1PlanFromV2(results.readActivityReadingPlan(jobKey).orElseThrow());
    ObjectNode oldSavedDefinition = sliceDefinition(historicalV1Plan, "slice-m3");
    ObjectNode laterDefinition = sliceDefinition(historicalV1Plan, "slice-m2");
    assertThat(laterDefinition.path("requiredUnitKeys"))
        .isNotEqualTo(oldSavedDefinition.path("requiredUnitKeys"));
    assertThat(laterDefinition.path("scope").asText())
        .isNotEqualTo(oldSavedDefinition.path("scope").asText());
    laterDefinition.put("sliceKey", "slice-m3");
    appendV1Decision(historicalV1Plan, laterDefinition, null);
    ObjectNode persistedV1 = persistV1Plan(results, journal, sourceBatch, jobKey, historicalV1Plan);

    ActivityMaterialView view =
        new ActivityMaterialProjector()
            .project(materials.packets().get(0), new ActivityExplanationProfile(1, 1, 1, 1, 1));
    ActivityReadingPlan reopenedPlan = ActivityReadingCoordinator.reopenSaved(view, persistedV1);
    assertThat(reopenedPlan.sliceKeys()).containsExactly("slice-m2", "slice-m3");
    assertThat(oldSavedDefinition.path("scope").asText())
        .isEqualTo(reopenedPlan.slices().get(1).scope());

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, sourceBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion().orElseThrow())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.completion())
                  .isEqualTo(ActivityPacketCompletion.Completion.INCOMPLETE);
              assertThat(packet.requiredSliceKeys()).containsExactly("slice-m2", "slice-m3");
              assertThat(packet.completedSliceKeys()).containsExactly("slice-m2");
              assertThat(packet.incompleteScopes())
                  .anySatisfy(scope -> assertThat(scope.sliceKey()).isEqualTo("slice-m3"));
            });
  }

  @Test
  void partialNavigationInHistoricalV1CannotEstablishCompleteScope(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "9".repeat(64));
    ActivityExplanationResult seeded = seedCompleteScopedPacket(journal, sourceBatch, materials);
    PrivateModelJobResultStore results =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity");
    ObjectNode aggregate = scopedAggregate(results);
    String jobKey = aggregate.path("jobKey").asText();
    ObjectNode historicalV1Plan =
        historicalV1PlanFromV2(results.readActivityReadingPlan(jobKey).orElseThrow());
    historicalV1Plan.put(
        "totalNavigationPages", historicalV1Plan.path("totalNavigationPages").asInt() + 1);
    historicalV1Plan.put(
        "remainingNavigationPages", historicalV1Plan.path("remainingNavigationPages").asInt() + 1);
    ObjectNode persistedV1 = persistV1Plan(results, journal, sourceBatch, jobKey, historicalV1Plan);
    assertThat(persistedV1.path("remainingNavigationPages").asInt()).isEqualTo(1);

    ActivityMaterialView view =
        new ActivityMaterialProjector()
            .project(materials.packets().get(0), new ActivityExplanationProfile(1, 1, 1, 1, 1));
    ActivityReadingPlan reopenedPlan = ActivityReadingCoordinator.reopenSaved(view, persistedV1);
    assertThat(reopenedPlan.sliceKeys()).containsExactly("slice-m2", "slice-m3");

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, sourceBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion().orElseThrow())
        .singleElement()
        .satisfies(
            packet ->
                assertThat(packet.completion())
                    .isIn(
                        ActivityPacketCompletion.Completion.INCOMPLETE,
                        ActivityPacketCompletion.Completion.UNDETERMINED));
  }

  @Test
  void retainsSuccessfulSliceWhenFailedPacketHasNoAggregate(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    ActivityExplanationProfile profile =
        new ActivityExplanationProfile(20_000, 8_000, 4, 32, 2_000);
    ActivityRetryProfile oneAttempt =
        new ActivityRetryProfile(1, 0, 0, 1.0, 0.0, Set.of("TRANSIENT_TRANSPORT"), Map.of());
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "8".repeat(64));
    ActivityLargePacketFormalEntryTest.FormalLargePacketProvider provider =
        new ActivityLargePacketFormalEntryTest.FormalLargePacketProvider("slice-m3");
    ActivityExplanationResult partial =
        ActivityExplainer.forExecution(
                ActivityLargePacketFormalEntryTest.execution(
                    journal, sourceBatch, null, provider, oneAttempt))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, profile, 1));

    assertThat(partial.reviewedActivities())
        .extracting(ReviewedActivity::sliceKey)
        .containsExactly("slice-m2");
    assertThat(partial.packetCompletion().orElseThrow())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.completion())
                  .isEqualTo(ActivityPacketCompletion.Completion.INCOMPLETE);
              assertThat(packet.requiredSliceKeys()).containsExactly("slice-m2", "slice-m3");
              assertThat(packet.completedSliceKeys()).containsExactly("slice-m2");
            });

    PrivateModelJobResultStore results =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity");
    List<ObjectNode> savedResults = results.listReviewedResults();
    assertThat(savedResults)
        .noneMatch(
            result -> "activity-packet-result-v1".equals(result.path("schemaVersion").asText()));
    assertThat(savedResults)
        .filteredOn(
            result -> "model-job-reviewed-result-v4".equals(result.path("schemaVersion").asText()))
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.path("materialId").asText())
                  .isEqualTo(materials.packets().get(0).packetId());
              assertThat(result.path("reviewedActivities").get(0).path("sliceKey").asText())
                  .isEqualTo("slice-m2");
            });
    ObjectNode terminalFailure = results.listTerminalFailures().stream().findFirst().orElseThrow();
    assertThat(results.listTerminalFailures()).hasSize(1);
    assertThat(terminalFailure.path("schemaVersion").asText()).isEqualTo("activity-job-failure-v1");
    assertThat(terminalFailure.path("materialId").asText())
        .isEqualTo(materials.packets().get(0).packetId());
    assertThat(terminalFailure.path("sliceKey").asText()).isEqualTo("slice-m3");
    assertThat(terminalFailure.path("stageKey").asText()).isEqualTo("REVIEW");
    String aggregateJobKey = terminalFailure.path("jobKey").asText();
    assertThat(results.readActivityReadingPlan(aggregateJobKey)).isPresent();

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, partial);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, sourceBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion().orElseThrow())
        .singleElement()
        .satisfies(
            packet -> {
              assertThat(packet.completion())
                  .isEqualTo(ActivityPacketCompletion.Completion.INCOMPLETE);
              assertThat(packet.requiredSliceKeys()).containsExactly("slice-m2", "slice-m3");
              assertThat(packet.completedSliceKeys()).containsExactly("slice-m2");
              assertThat(packet.incompleteScopes())
                  .anySatisfy(
                      scope -> {
                        assertThat(scope.sliceKey()).isEqualTo("slice-m3");
                        assertThat(scope.entryIds())
                            .containsExactlyElementsOf(
                                materials.packets().get(0).entries().stream()
                                    .map(value -> value.entryId())
                                    .toList());
                      });
            });
  }

  @Test
  void rejectsTamperedReviewedActivityInScopedSliceAgainstPublicPacket(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "1".repeat(64));
    ActivityExplanationResult seeded = seedCompleteScopedPacket(journal, sourceBatch, materials);
    PrivateModelJobResultStore results =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity");
    List<ObjectNode> scopedRows =
        results.listReviewedResults().stream()
            .filter(
                result ->
                    "model-job-reviewed-result-v4".equals(result.path("schemaVersion").asText()))
            .toList();
    ObjectNode m2Result = resultForSlice(scopedRows, "slice-m2");
    String jobKey = m2Result.path("jobKey").asText();
    Path resultFile =
        modelJobDirectory(journal, sourceBatch, jobKey).resolve("reviewed-result.json");
    assertThat(resultFile).isRegularFile();

    ObjectNode tamperedResult = m2Result.deepCopy();
    String publicBusinessPurpose =
        tamperedResult.path("reviewedActivities").get(0).path("businessPurpose").asText();
    ((ObjectNode) tamperedResult.path("reviewedActivities").get(0))
        .put("businessPurpose", "tampered private Activity body");
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Files.write(
        resultFile,
        canonicalJson.encodeCanonical(tamperedResult).copyToByteArray(),
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
    assertThat(results.readReviewedResult(jobKey))
        .hasValueSatisfying(
            result ->
                assertThat(
                        result.path("reviewedActivities").get(0).path("businessPurpose").asText())
                    .isEqualTo("tampered private Activity body"));

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    ReviewedActivity publicM2 =
        historical.reviewedActivities().stream()
            .filter(activity -> "slice-m2".equals(activity.sliceKey()))
            .findFirst()
            .orElseThrow();
    assertThat(publicM2.businessPurpose()).isEqualTo(publicBusinessPurpose);
    assertThatThrownBy(
            () ->
                new ActivityHistoricalReuseResolver(journal)
                    .resolve(materials, historical, sourceBatch))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ACTIVITY_HISTORICAL_REUSE_PROVENANCE_INVALID");
  }

  @Test
  void rejectsClaimedCompleteScopedResultWhenItsSavedReadingPlanIsAbsent(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "b".repeat(64));
    ActivityExplanationResult seeded = seedCompleteScopedPacket(journal, sourceBatch, materials);
    PrivateModelJobResultStore results =
        new PrivateModelJobResultStore(journal, sourceBatch, "activity");
    ObjectNode aggregate = scopedAggregate(results);
    String jobKey = aggregate.path("jobKey").asText();
    assertThat(aggregate.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(aggregate.path("readingPlan").asText()).isEqualTo("decision-result.json");
    assertThat(seeded.reviewedActivities()).hasSize(2);
    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    assertThat(historical.packetCompletion()).as("M11 v3 has no packet completion").isEmpty();

    Path claimedPlan =
        modelJobDirectory(journal, sourceBatch, jobKey).resolve("decision-result.json");
    assertThat(claimedPlan).isRegularFile();
    Files.delete(claimedPlan);

    assertThatThrownBy(
            () ->
                new ActivityHistoricalReuseResolver(journal)
                    .resolve(materials, historical, sourceBatch))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ACTIVITY_HISTORICAL_REUSE_PROVENANCE_INVALID");
  }

  @Test
  void resolvesAReusedScopedPacketFromItsOriginalSliceStagePair(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    ActivityExplanationProfile profile =
        new ActivityExplanationProfile(20_000, 8_000, 4, 32, 2_000);
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId originalBatch = AnalysisRunId.parse("analysis-run:" + "4".repeat(64));
    AnalysisRunId selectedBatch = AnalysisRunId.parse("analysis-run:" + "5".repeat(64));
    ActivityLargePacketFormalEntryTest.FormalLargePacketProvider originalProvider =
        new ActivityLargePacketFormalEntryTest.FormalLargePacketProvider();
    ActivityExplanationResult original =
        ActivityLargePacketFormalEntryTest.configuredFormalExplainer(
                journal, originalBatch, originalProvider)
            .explain(new ExplainCodeReadingMaterialsRequest(materials, profile, 1));
    assertThat(original.reviewedActivities()).hasSize(2);
    List<ObjectNode> originalRows =
        new PrivateModelJobResultStore(journal, originalBatch, "activity").listReviewedResults();
    assertThat(originalRows)
        .filteredOn(
            result -> "activity-packet-result-v1".equals(result.path("schemaVersion").asText()))
        .hasSize(1);
    assertThat(originalRows)
        .filteredOn(
            result -> "model-job-reviewed-result-v4".equals(result.path("schemaVersion").asText()))
        .hasSize(2)
        .allSatisfy(
            slice -> {
              assertThat(slice.path("stageSuccesses").path("DRAFT").asText())
                  .isEqualTo("DRAFT/success.json");
              assertThat(slice.path("stageSuccesses").path("REVIEW").asText())
                  .isEqualTo("REVIEW/success.json");
            });

    ActivityLargePacketFormalEntryTest.FormalLargePacketProvider selectedProvider =
        new ActivityLargePacketFormalEntryTest.FormalLargePacketProvider();
    ActivityExplanationResult selected =
        ActivityLargePacketFormalEntryTest.configuredFormalExplainer(
                journal, selectedBatch, originalBatch, selectedProvider)
            .explain(new ExplainCodeReadingMaterialsRequest(materials, profile, 1));

    assertThat(selectedProvider.taskKinds())
        .as("the selected batch imports the aggregate and scoped rows without generation")
        .isEmpty();
    assertThat(
            new PrivateModelJobResultStore(journal, selectedBatch, "activity")
                .listReviewedResults())
        .filteredOn(
            result -> "activity-packet-result-v1".equals(result.path("schemaVersion").asText()))
        .singleElement()
        .satisfies(
            aggregate ->
                assertThat(aggregate.path("reusedFromModelBatchId").isNull())
                    .as("the selected batch writes a new aggregate after reopening saved stages")
                    .isTrue());

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("selected-history")), selectedBatch, selected);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, selectedBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion())
        .contains(
            List.of(
                new ActivityPacketCompletion(
                    materials.packets().get(0).packetId(),
                    materials.packets().get(0).entries().stream()
                        .map(value -> value.entryId())
                        .toList(),
                    ActivityPacketCompletion.Completion.COMPLETE,
                    List.of("slice-m2", "slice-m3"),
                    List.of("slice-m2", "slice-m3"),
                    List.of())));
  }

  @Test
  void resolvesAReusedScopedSliceWhenThePacketAggregateCompletesLocally(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials = ActivityLargePacketFormalEntryTest.largeStep05Material();
    ActivityExplanationProfile profile =
        new ActivityExplanationProfile(20_000, 8_000, 4, 32, 2_000);
    ActivityRetryProfile oneAttempt =
        new ActivityRetryProfile(1, 0, 0, 1.0, 0.0, Set.of("TRANSIENT_TRANSPORT"), Map.of());
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId failedBatch = AnalysisRunId.parse("analysis-run:" + "e".repeat(64));
    AnalysisRunId selectedBatch = AnalysisRunId.parse("analysis-run:" + "d".repeat(64));
    ActivityLargePacketFormalEntryTest.FormalLargePacketProvider firstProvider =
        new ActivityLargePacketFormalEntryTest.FormalLargePacketProvider("slice-m3");
    ActivityExplanationResult partial =
        ActivityExplainer.forExecution(
                ActivityLargePacketFormalEntryTest.execution(
                    journal, failedBatch, null, firstProvider, oneAttempt))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, profile, 1));
    assertThat(partial.reviewedActivities())
        .extracting(ReviewedActivity::sliceKey)
        .containsExactly("slice-m2");

    ActivityLargePacketFormalEntryTest.FormalLargePacketProvider resumedProvider =
        new ActivityLargePacketFormalEntryTest.FormalLargePacketProvider();
    ActivityExplanationResult resumed =
        ActivityExplainer.forExecution(
                ActivityLargePacketFormalEntryTest.execution(
                    journal, selectedBatch, failedBatch, resumedProvider, oneAttempt))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, profile, 1));
    assertThat(resumedProvider.taskKinds()).containsExactly("ACTIVITY_REVIEW");
    assertThat(resumed.reviewedActivities())
        .extracting(ReviewedActivity::sliceKey)
        .containsExactlyInAnyOrder("slice-m2", "slice-m3");

    List<ObjectNode> selectedRows =
        new PrivateModelJobResultStore(journal, selectedBatch, "activity").listReviewedResults();
    assertThat(selectedRows)
        .filteredOn(
            result -> "activity-packet-result-v1".equals(result.path("schemaVersion").asText()))
        .singleElement()
        .satisfies(
            aggregate ->
                assertThat(aggregate.path("reusedFromModelBatchId").isNull())
                    .as("the partially resumed packet aggregate is completed in the child batch")
                    .isTrue());
    List<ObjectNode> scopedResults =
        selectedRows.stream()
            .filter(
                result ->
                    "model-job-reviewed-result-v4".equals(result.path("schemaVersion").asText()))
            .toList();
    assertThat(scopedResults).hasSize(2);
    ObjectNode reusedM2 = resultForSlice(scopedResults, "slice-m2");
    ObjectNode resumedM3 = resultForSlice(scopedResults, "slice-m3");
    assertThat(reusedM2.path("reusedFromModelBatchId").isNull())
        .as("the packet-level result is local; successful stage reuse is recorded per stage")
        .isTrue();
    assertThat(resumedM3.path("reusedFromModelBatchId").isNull()).isTrue();

    PrivateModelJobResultStore selectedStore =
        new PrivateModelJobResultStore(journal, selectedBatch, "activity");
    for (String stage : List.of("DRAFT", "REVIEW")) {
      assertThat(selectedStore.readStageSuccess(reusedM2.path("jobKey").asText(), stage))
          .hasValueSatisfying(
              success ->
                  assertThat(success.path("reusedFromModelBatchId").asText())
                      .isEqualTo(failedBatch.value()));
      for (String event : List.of("request", "started", "response", "validation", "outcome")) {
        assertThat(
                selectedStore.readStageAttemptRecord(
                    reusedM2.path("jobKey").asText(), stage, 1, event))
            .as("reused %s must retain its original %s attempt record", stage, event)
            .isPresent();
      }
    }
    assertThat(selectedStore.readStageSuccess(resumedM3.path("jobKey").asText(), "DRAFT"))
        .hasValueSatisfying(
            success ->
                assertThat(success.path("reusedFromModelBatchId").asText())
                    .isEqualTo(failedBatch.value()));
    for (String event : List.of("request", "started", "response", "validation", "outcome")) {
      assertThat(
              selectedStore.readStageAttemptRecord(
                  resumedM3.path("jobKey").asText(), "DRAFT", 1, event))
          .as("the resumed M3 draft must retain its original %s attempt record", event)
          .isPresent();
    }
    assertThat(selectedStore.readStageSuccess(resumedM3.path("jobKey").asText(), "REVIEW"))
        .hasValueSatisfying(success -> assertThat(success.has("reusedFromModelBatchId")).isFalse());

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("selected-history")), selectedBatch, resumed);
    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, selectedBatch);

    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());
    assertThat(resolved.packetCompletion())
        .contains(
            List.of(
                new ActivityPacketCompletion(
                    materials.packets().get(0).packetId(),
                    materials.packets().get(0).entries().stream()
                        .map(value -> value.entryId())
                        .toList(),
                    ActivityPacketCompletion.Completion.COMPLETE,
                    List.of("slice-m2", "slice-m3"),
                    List.of("slice-m2", "slice-m3"),
                    List.of())));
  }

  @Test
  void matchesTopLevelHistoryByActivityIdButKeepsNestedListsContentStrict(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials =
        ActivityStageAttemptStoreTest.persistedSinglePacket(
            Files.createDirectory(temporary.resolve("step05")));
    Path journal = Files.createDirectory(temporary.resolve("private-journal"));
    AnalysisRunId sourceBatch = AnalysisRunId.parse("analysis-run:" + "6".repeat(64));
    CompleteDirectProvider provider =
        new CompleteDirectProvider(true, materials.packets().get(0).packetId(), null);
    ActivityExplanationResult seeded =
        ActivityStageAttemptStoreTest.explain(
            ActivityExplainer.forExecution(execution(journal, sourceBatch, null, provider)),
            materials);
    List<String> privateOrder =
        seeded.reviewedActivities().stream().map(ReviewedActivity::activityId).toList();
    assertThat(privateOrder).hasSize(2);
    assertThat(privateOrder.get(0)).isGreaterThan(privateOrder.get(1));

    ActivityExplanationResult historical =
        publishAndReopenHistoricalV3(
            Files.createDirectory(temporary.resolve("historical-m11")), sourceBatch, seeded);
    assertThat(historical.reviewedActivities())
        .extracting(ReviewedActivity::activityId)
        .containsExactly(privateOrder.get(1), privateOrder.get(0));

    ActivityExplanationResult resolved =
        new ActivityHistoricalReuseResolver(journal).resolve(materials, historical, sourceBatch);
    assertThat(resolved.reviewedActivities())
        .containsExactlyElementsOf(historical.reviewedActivities());

    for (String changedField : List.of("activitySteps", "businessRules")) {
      List<ReviewedActivity> changedActivities = new ArrayList<>(historical.reviewedActivities());
      ReviewedActivity original = changedActivities.get(0);
      if ("activitySteps".equals(changedField)) {
        changedActivities.set(0, withActivitySteps(original, reversed(original.activitySteps())));
      } else {
        changedActivities.set(0, withBusinessRules(original, reversed(original.businessRules())));
      }
      ActivityExplanationResult changedHistory =
          new ActivityExplanationResult(
              changedActivities,
              historical.coverage(),
              historical.unexplainedActivityEntries(),
              historical.checkpoint());
      ActivityExplanationResult reopenedChanged =
          publishAndReopenHistoricalV3(
              Files.createDirectory(temporary.resolve("changed-" + changedField)),
              sourceBatch,
              changedHistory);

      assertThatThrownBy(
              () ->
                  new ActivityHistoricalReuseResolver(journal)
                      .resolve(materials, reopenedChanged, sourceBatch))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ACTIVITY_HISTORICAL_REUSE_PROVENANCE_INVALID");
    }
  }

  private static ModelJobExecutionConfiguration execution(
      Path journal, AnalysisRunId run, AnalysisRunId reuseFrom, StructuredModelProvider provider) {
    return new ModelJobExecutionConfiguration(
        1,
        Map.of(
            "pro",
            new ModelJobProviderBinding(
                "pro", "retry-fixture-scope", 1, provider, ActivityStageAttemptStoreTest.IDENTITY)),
        Map.of(
            "activity", List.of("pro"),
            "processGroup", List.of("pro"),
            "repositorySummary", List.of("pro"),
            "report", List.of("pro")),
        journal,
        run,
        reuseFrom);
  }

  private static ObjectNode resultForSlice(List<ObjectNode> results, String sliceKey) {
    return results.stream()
        .filter(
            result ->
                result.path("reviewedActivities").isArray()
                    && sliceKey.equals(
                        result.path("reviewedActivities").get(0).path("sliceKey").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no saved Activity result for " + sliceKey));
  }

  private static ActivityExplanationResult seedCompleteScopedPacket(
      Path journal, AnalysisRunId batch, CodeReadingMaterialSet materials) {
    ActivityLargePacketFormalEntryTest.FormalLargePacketProvider provider =
        new ActivityLargePacketFormalEntryTest.FormalLargePacketProvider();
    return ActivityLargePacketFormalEntryTest.configuredFormalExplainer(journal, batch, provider)
        .explain(
            new ExplainCodeReadingMaterialsRequest(
                materials, new ActivityExplanationProfile(20_000, 8_000, 4, 32, 2_000), 1));
  }

  private static ObjectNode scopedAggregate(PrivateModelJobResultStore results) {
    return results.listReviewedResults().stream()
        .filter(result -> "activity-packet-result-v1".equals(result.path("schemaVersion").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no saved scoped Activity aggregate"));
  }

  private static ObjectNode historicalV1PlanFromV2(ObjectNode currentPlan) {
    ObjectNode historical = JsonNodeFactory.instance.objectNode();
    for (String field :
        List.of(
            "packetId",
            "mode",
            "totalNavigationPages",
            "remainingNavigationPages",
            "navigationPages",
            "selectedUnitKeys",
            "unreadUnitKeys",
            "unitDispositions",
            "slices",
            "unknowns")) {
      historical.set(field, currentPlan.path(field).deepCopy());
    }
    historical.put("schemaVersion", "activity-reading-plan-v1");
    var decisions = historical.putArray("decisions");
    for (JsonNode currentDecision : currentPlan.path("decisions")) {
      ObjectNode decision = decisions.addObject();
      for (String field :
          List.of("requestedNavigationPages", "requestedUnitKeys", "slices", "unknowns")) {
        decision.set(field, currentDecision.path(field).deepCopy());
      }
    }
    return historical;
  }

  private static ObjectNode sliceDefinition(ObjectNode plan, String sliceKey) {
    for (JsonNode savedSlice : plan.path("slices")) {
      if (sliceKey.equals(savedSlice.path("sliceKey").asText())) {
        ObjectNode definition = JsonNodeFactory.instance.objectNode();
        for (String field :
            List.of(
                "sliceKey", "entryKeys", "requiredUnitKeys", "sharedContextUnitKeys", "scope")) {
          definition.set(field, savedSlice.path(field).deepCopy());
        }
        return definition;
      }
    }
    throw new AssertionError("no saved v1 slice definition for " + sliceKey);
  }

  private static void appendV1Decision(
      ObjectNode plan, ObjectNode sliceDefinition, String historicalUnknown) {
    ObjectNode decision = JsonNodeFactory.instance.objectNode();
    decision.putArray("requestedNavigationPages");
    decision.putArray("requestedUnitKeys");
    decision.putArray("slices").add(sliceDefinition.deepCopy());
    var unknowns = decision.putArray("unknowns");
    if (historicalUnknown != null) {
      unknowns.add(historicalUnknown);
    }
    ((ArrayNode) plan.path("decisions")).add(decision);
  }

  private static ObjectNode persistV1Plan(
      PrivateModelJobResultStore results,
      Path journal,
      AnalysisRunId sourceBatch,
      String jobKey,
      ObjectNode historicalV1Plan)
      throws Exception {
    Path savedPlan =
        modelJobDirectory(journal, sourceBatch, jobKey).resolve("decision-result.json");
    assertThat(savedPlan).isRegularFile();
    Files.delete(savedPlan);
    results.writeDecision(jobKey, historicalV1Plan);
    return results.readActivityReadingPlan(jobKey).orElseThrow();
  }

  private static List<String> fieldNames(JsonNode value) {
    List<String> result = new ArrayList<>();
    value.fieldNames().forEachRemaining(result::add);
    return result;
  }

  private static ActivityExplanationResult publishAndReopenHistoricalV3(
      Path outputRoot, AnalysisRunId owner, ActivityExplanationResult activities) throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Path policyFile =
        Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisTestPolicyRegistry.load(policyFile, canonicalJson);
    ArtifactControls controls = ProgramGraphsPublicFixture.controlsForRuntimeTest(policies);
    ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
    ModulePublicationReference historicalCheckpoint;
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      historicalCheckpoint =
          ActivityExplanationHistoricalV3Fixture.install(
              modules,
              canonicalJson,
              controls,
              owner,
              activities.reviewedActivities(),
              activities.coverage(),
              activities.unexplainedActivityEntries());
    }
    try (RunStoreHandle handle = RunStoreBootstrap.open(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      return new ActivityExplanationCheckpointReader(modules).reopen(historicalCheckpoint);
    }
  }

  private static Path modelJobDirectory(Path journal, AnalysisRunId run, String jobKey) {
    return journal
        .resolve("model-jobs")
        .resolve(run.value().substring("analysis-run:".length()))
        .resolve("activity")
        .resolve(jobKey);
  }

  private static ReviewedActivity withActivitySteps(
      ReviewedActivity source, List<String> activitySteps) {
    return withNestedLists(source, activitySteps, source.businessRules());
  }

  private static ReviewedActivity withBusinessRules(
      ReviewedActivity source, List<String> businessRules) {
    return withNestedLists(source, source.activitySteps(), businessRules);
  }

  private static ReviewedActivity withNestedLists(
      ReviewedActivity source, List<String> activitySteps, List<String> businessRules) {
    return new ReviewedActivity(
        source.activityId(),
        source.materialId(),
        source.entryIds(),
        source.name(),
        source.businessPurpose(),
        source.participants(),
        source.businessObjects(),
        source.triggerOrInput(),
        source.conditions(),
        activitySteps,
        source.codeDefinedResults(),
        businessRules,
        source.formulasOrMetrics(),
        source.terms(),
        source.certainty(),
        source.sourceRefs(),
        source.questions(),
        source.scopeLimitations(),
        source.materialSource(),
        source.sliceKey(),
        source.originalSourceRefs());
  }

  private static List<String> reversed(List<String> values) {
    List<String> result = new ArrayList<>(values);
    java.util.Collections.reverse(result);
    return List.copyOf(result);
  }

  private static final class CompleteDirectProvider implements StructuredModelProvider {

    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final java.util.ArrayList<String> taskKinds = new java.util.ArrayList<>();
    private final boolean reverseStableActivityIdOrder;
    private final String materialId;
    private final String sliceKey;

    private CompleteDirectProvider() {
      this(false, null, null);
    }

    private CompleteDirectProvider(boolean reverseStableActivityIdOrder) {
      this(reverseStableActivityIdOrder, null, null);
    }

    private CompleteDirectProvider(
        boolean reverseStableActivityIdOrder, String materialId, String sliceKey) {
      this.reverseStableActivityIdOrder = reverseStableActivityIdOrder;
      this.materialId = materialId;
      this.sliceKey = sliceKey;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode packet = input.path("readingPacket");
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      List<ObjectNode> activityRows = new ArrayList<>();
      List<String> localIds =
          reverseStableActivityIdOrder
              ? List.of("activity-one", "activity-two")
              : List.of("activity-1");
      for (String localId : localIds) {
        ObjectNode activity = JsonNodeFactory.instance.objectNode();
        activity.put("activityLocalId", localId);
        activity.putArray("entryKeys").add(packet.path("entryKeys").get(0).asText());
        activity.put("name", "保存订单");
        activity.put("businessPurpose", "根据入口提交的数据保存订单。");
        activity.putArray("participants").add("订单提交者");
        activity.putArray("businessObjects").add("订单");
        activity.putArray("triggerOrInput").add("入口提交的订单数据");
        activity.putArray("conditions");
        activity.putArray("activitySteps").add("读取订单数据").add("保存订单");
        activity.putArray("codeDefinedResults").add("订单被保存");
        activity.putArray("businessRules").add("先检查请求").add("再保存订单");
        activity.putArray("formulasOrMetrics");
        activity.putArray("terms").add("订单");
        activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
        var sourceRefs = activity.putArray("sourceRefs");
        packet
            .path("allowlistedRefs")
            .forEach(reference -> sourceRefs.add(reference.path("ref").asText()));
        activity.putArray("questions");
        activity.putArray("scopeLimitations").add("静态源码不能证明线上保存结果。");
        activityRows.add(activity);
      }
      if (reverseStableActivityIdOrder) {
        activityRows.sort(
            Comparator.comparing(
                    (ObjectNode activity) -> stableActivityId(materialId, sliceKey, activity))
                .reversed());
      }
      var activities = response.putArray("activities");
      activityRows.forEach(activities::add);
      if ("ACTIVITY_REVIEW".equals(request.taskKind())) {
        response.putArray("unexplainedEntries");
      }
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response), ActivityStageAttemptStoreTest.IDENTITY);
    }

    private String stableActivityId(String materialId, String sliceKey, ObjectNode activity) {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(materialId.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
        if (sliceKey != null) {
          digest.update(sliceKey.getBytes(StandardCharsets.UTF_8));
          digest.update((byte) '\n');
        }
        digest.update(activity.path("activityLocalId").asText().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
        digest.update(canonicalJson.encodeCanonical(activity).copyToByteArray());
        return "activity:" + java.util.HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException unavailable) {
        throw new IllegalStateException("SHA-256 is unavailable", unavailable);
      }
    }

    List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }
  }
}
