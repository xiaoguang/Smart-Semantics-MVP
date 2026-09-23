package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.cli.SourceAnalysisTestPolicyRegistry;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceipt;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

class ActivityPacketCompletionCheckpointTest {

  @Test
  void freshReaderRejectsV4CoverageThatOmitsPacketCompletion(@TempDir Path temporary)
      throws Exception {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Path productionPolicyFile =
        Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisTestPolicyRegistry.load(productionPolicyFile, canonicalJson);
    ArtifactControls controls = ProgramGraphsPublicFixture.controlsForRuntimeTest(policies);
    ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
    Path outputRoot = Files.createDirectory(temporary.resolve("missing-v4-field-store"));
    AnalysisRunId outputRun = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));
    ModulePublicationReference checkpoint;
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      checkpoint =
          ActivityExplanationHistoricalV3Fixture.installV4WithoutPacketCompletion(
              modules, canonicalJson, controls, outputRun);
    }

    try (RunStoreHandle handle = RunStoreBootstrap.open(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      assertThatThrownBy(() -> new ActivityExplanationCheckpointReader(modules).reopen(checkpoint))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID");
    }
  }

  @Test
  void allPacketCompletionDispositionsPublishAndFreshReopenFromCanonicalStore(
      @TempDir Path temporary) throws Exception {
    Path sourceRoot = temporary.resolve("source-store");
    Files.createDirectory(sourceRoot);
    CodeReadingMaterialSet materials =
        ActivityStageAttemptStoreTest.persistedSinglePacket(sourceRoot);
    CodeReadingMaterialSet.Packet packet = materials.packets().get(0);
    List<String> entryIds = packet.entries().stream().map(value -> value.entryId()).toList();
    String activityId = "activity:" + "a".repeat(64);
    List<String> sourceRefs =
        packet.sourceReferences().stream()
            .map(CodeReadingMaterialSet.SourceReference::sourceRef)
            .toList();
    Map<String, String> originalSourceRefs =
        sourceRefs.stream().collect(Collectors.toMap(value -> value, value -> value));
    ReviewedActivity activity =
        new ReviewedActivity(
            activityId,
            packet.packetId(),
            entryIds,
            "提交订单",
            "读取入口提交的数据并保存订单。",
            List.of(),
            List.of("订单"),
            List.of("入口提交的数据"),
            List.of(),
            List.of("读取请求", "保存订单"),
            List.of("订单被保存"),
            List.of(),
            List.of(),
            List.of("订单"),
            "DIRECT_CODE_BEHAVIOR",
            sourceRefs,
            List.of(),
            List.of("静态源码不证明某次保存成功"),
            "CODE_READING_MATERIALS",
            null,
            originalSourceRefs);
    List<ActivityEntryCoverage> coverage =
        entryIds.stream()
            .map(
                entryId ->
                    new ActivityEntryCoverage(entryId, "ANALYZED", List.of(activityId), null))
            .toList();
    ActivityPacketCompletion completion =
        new ActivityPacketCompletion(
            packet.packetId(),
            entryIds,
            ActivityPacketCompletion.Completion.COMPLETE,
            List.of("whole-packet"),
            List.of("whole-packet"),
            List.of());

    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Path productionPolicyFile =
        Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisTestPolicyRegistry.load(productionPolicyFile, canonicalJson);
    assertThat(
            policies
                .resolve(
                    new ArtifactPolicyKey(
                        "FLOW_INTERPRETATION_ACTIVITY_COVERAGE",
                        "flow-interpretation-activity-coverage-v3"))
                .key()
                .schemaVersion())
        .isEqualTo("flow-interpretation-activity-coverage-v3");
    assertThat(
            policies
                .resolve(
                    new ArtifactPolicyKey(
                        "FLOW_INTERPRETATION_ACTIVITY_COVERAGE",
                        "flow-interpretation-activity-coverage-v4"))
                .key()
                .schemaVersion())
        .isEqualTo("flow-interpretation-activity-coverage-v4");
    ArtifactControls controls = ProgramGraphsPublicFixture.controlsForRuntimeTest(policies);
    ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
    AnalysisStepPublicationReference source = businessFlowsPublication(materials);
    ActivityPacketCompletion incomplete =
        new ActivityPacketCompletion(
            packet.packetId(),
            entryIds,
            ActivityPacketCompletion.Completion.INCOMPLETE,
            List.of("whole-packet"),
            List.of(),
            List.of(
                new ActivityPacketCompletion.IncompleteScope(
                    "whole-packet", entryIds, "ACTIVITY_REVIEW_FAILED")));
    ActivityPacketCompletion undetermined =
        new ActivityPacketCompletion(
            packet.packetId(),
            entryIds,
            ActivityPacketCompletion.Completion.UNDETERMINED,
            List.of(),
            List.of(),
            List.of(
                new ActivityPacketCompletion.IncompleteScope(
                    null, entryIds, "HISTORICAL_REQUIRED_SCOPE_UNKNOWN")));
    List<PublicationCase> publicationCases =
        List.of(
            new PublicationCase("0", completion, List.of(activity), coverage),
            new PublicationCase("1", incomplete, List.of(), List.of()),
            new PublicationCase("2", undetermined, List.of(activity), coverage));
    List<ModulePublicationReference> checkpoints = new java.util.ArrayList<>();
    Path outputRoot = temporary.resolve("activity-output-store");
    Files.createDirectory(outputRoot);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      ActivityExplanationCheckpointPublisher publisher =
          new ActivityExplanationCheckpointPublisher(modules);
      ActivityPacketCompletion invalidScope =
          new ActivityPacketCompletion(
              packet.packetId(),
              entryIds,
              ActivityPacketCompletion.Completion.INCOMPLETE,
              List.of("whole-packet"),
              List.of(),
              List.of(
                  new ActivityPacketCompletion.IncompleteScope(
                      "whole-packet", List.of("entry:outside-packet"), "REVIEW_FAILED")));
      PublicationCase firstCase = publicationCases.get(0);
      AnalysisRunId firstOutputRun =
          AnalysisRunId.parse("analysis-run:" + "f".repeat(63) + firstCase.runSuffix());
      assertThatThrownBy(
              () ->
                  publisher.publishStep05(
                      firstOutputRun,
                      source,
                      sourceStepStore(source),
                      controls,
                      materials,
                      List.of(),
                      List.of(),
                      List.of(),
                      List.of(invalidScope)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ACTIVITY_PACKET_COMPLETION_INVALID");
      for (int index = 0; index < publicationCases.size(); index++) {
        PublicationCase value = publicationCases.get(index);
        AnalysisRunId outputRun =
            AnalysisRunId.parse("analysis-run:" + "f".repeat(63) + value.runSuffix());
        ModulePublicationReference checkpoint =
            publisher.publishStep05(
                outputRun,
                source,
                sourceStepStore(source),
                controls,
                materials,
                value.activities(),
                value.coverage(),
                List.of(),
                List.of(value.completion()));
        checkpoints.add(checkpoint);

        var installed = modules.reopen(checkpoint);
        assertThat(installed.receipt().moduleVersion()).isEqualTo("v4");
        assertThat(installed.payloads())
            .filteredOn(
                valuePayload ->
                    "activity-coverage.json".equals(valuePayload.descriptor().fileName()))
            .singleElement()
            .satisfies(
                payload ->
                    assertThat(
                            canonicalJson
                                .parseCanonical(payload.canonicalUtf8())
                                .path("packetCompletion")
                                .get(0)
                                .path("completion")
                                .asText())
                        .isEqualTo(value.completion().completion().name()));
      }
    }

    try (RunStoreHandle reopenedHandle = RunStoreBootstrap.open(outputRoot)) {
      CanonicalModuleArtifactStore reopenedModules =
          new FileSystemCanonicalModuleArtifactStore(
              reopenedHandle, canonicalJson, policies, limits);
      ActivityExplanationCheckpointReader reader =
          new ActivityExplanationCheckpointReader(reopenedModules);
      for (int index = 0; index < publicationCases.size(); index++) {
        PublicationCase expected = publicationCases.get(index);
        ActivityExplanationResult restored = reader.reopen(checkpoints.get(index));
        assertThat(restored.reviewedActivities()).isEqualTo(expected.activities());
        assertThat(restored.coverage()).isEqualTo(expected.coverage());
        assertThat(restored.packetCompletion()).contains(List.of(expected.completion()));
      }
      assertThat(reader.reopen(checkpoints.get(0)).reviewedActivities().get(0).sliceKey()).isNull();
      assertThat(reader.reopen(checkpoints.get(2)).reviewedActivities())
          .as("valid reviewed content survives an undetermined historical required scope")
          .containsExactly(activity);
    }
  }

  @Test
  void acceptsPacketEntryIdsInDifferentOrderWhenPublishingAndFreshReopening(@TempDir Path temporary)
      throws Exception {
    CodeReadingMaterialSet materials =
        ActivityLargePacketFormalEntryTest.largeStep05MaterialWithTwoEntries();
    CodeReadingMaterialSet.Packet packet = materials.packets().get(0);
    List<String> packetEntryIds = packet.entries().stream().map(value -> value.entryId()).toList();
    assertThat(packetEntryIds).containsExactly("entry:large", "entry:second");
    List<String> claimedEntryIds = List.of("entry:second", "entry:large");
    List<String> sourceRefs =
        packet.sourceReferences().stream()
            .map(CodeReadingMaterialSet.SourceReference::sourceRef)
            .toList();
    Map<String, String> originalSourceRefs =
        sourceRefs.stream().collect(Collectors.toMap(value -> value, value -> value));
    String activityId = "activity:" + "e".repeat(64);
    ReviewedActivity activity =
        new ReviewedActivity(
            activityId,
            packet.packetId(),
            packetEntryIds,
            "提交订单",
            "读取入口提交的数据并保存订单。",
            List.of(),
            List.of("订单"),
            List.of("入口提交的数据"),
            List.of(),
            List.of("读取请求", "保存订单"),
            List.of("订单被保存"),
            List.of(),
            List.of(),
            List.of("订单"),
            "DIRECT_CODE_BEHAVIOR",
            sourceRefs,
            List.of(),
            List.of("静态源码不证明某次保存成功"),
            "CODE_READING_MATERIALS",
            null,
            originalSourceRefs);
    List<ActivityEntryCoverage> coverage =
        packetEntryIds.stream()
            .map(
                entryId ->
                    new ActivityEntryCoverage(entryId, "ANALYZED", List.of(activityId), null))
            .toList();
    ActivityPacketCompletion completion =
        new ActivityPacketCompletion(
            packet.packetId(),
            claimedEntryIds,
            ActivityPacketCompletion.Completion.COMPLETE,
            List.of("whole-packet"),
            List.of("whole-packet"),
            List.of());

    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Path productionPolicyFile =
        Path.of("tools/repository-run/jdt-artifact-policy-set-v1.json").toAbsolutePath();
    CanonicalArtifactPolicyRegistry policies =
        SourceAnalysisTestPolicyRegistry.load(productionPolicyFile, canonicalJson);
    ArtifactControls controls = ProgramGraphsPublicFixture.controlsForRuntimeTest(policies);
    ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
    AnalysisStepPublicationReference source = businessFlowsPublication(materials);
    Path outputRoot = Files.createDirectory(temporary.resolve("reordered-packet-entry-store"));
    AnalysisRunId outputRun = AnalysisRunId.parse("analysis-run:" + "e".repeat(64));
    ModulePublicationReference checkpoint;
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      checkpoint =
          new ActivityExplanationCheckpointPublisher(modules)
              .publishStep05(
                  outputRun,
                  source,
                  sourceStepStore(source),
                  controls,
                  materials,
                  List.of(activity),
                  coverage,
                  List.of(),
                  List.of(completion));
    }

    try (RunStoreHandle handle = RunStoreBootstrap.open(outputRoot)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      ActivityExplanationResult reopened =
          new ActivityExplanationCheckpointReader(modules).reopen(checkpoint);

      assertThat(reopened.packetCompletion().orElseThrow())
          .singleElement()
          .satisfies(
              value -> {
                assertThat(value.packetId()).isEqualTo(packet.packetId());
                assertThat(value.entryIds())
                    .containsExactlyInAnyOrder("entry:large", "entry:second");
                assertThat(value.completion())
                    .isEqualTo(ActivityPacketCompletion.Completion.COMPLETE);
              });
      assertThat(reopened.reviewedActivities()).containsExactly(activity);
    }
  }

  private record PublicationCase(
      String runSuffix,
      ActivityPacketCompletion completion,
      List<ReviewedActivity> activities,
      List<ActivityEntryCoverage> coverage) {}

  private static AnalysisStepPublicationReference businessFlowsPublication(
      CodeReadingMaterialSet materials) {
    AnalysisRunId sourceRun = materials.header().sourceInventory().publication().address().runId();
    String digest = "b".repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(sourceRun, AnalysisStepKey.BUSINESS_FLOWS),
        new AnalysisStepArtifactRoot("analysis-step-root:" + digest),
        new AnalysisStepReceiptId("analysis-step-receipt:" + digest),
        new Sha256Digest(digest));
  }

  private static CanonicalAnalysisStepArtifactStore sourceStepStore(
      AnalysisStepPublicationReference reference) {
    ImmutableBytes bytes = ImmutableBytes.copyOf("{}\n".getBytes(StandardCharsets.UTF_8));
    ArtifactDescriptor descriptor =
        new ArtifactDescriptor(
            "code-reading-materials.jsonl",
            "BUSINESS_FLOWS_CODE_READING_MATERIALS",
            "business-flows-code-reading-materials-v1",
            ArtifactId.parse("code-reading-materials:" + "c".repeat(64)),
            CanonicalMediaType.APPLICATION_X_NDJSON,
            bytes.size(),
            new Sha256Digest("c".repeat(64)));
    AnalysisStepReceipt receipt =
        new AnalysisStepReceipt(
            "analysis-step-receipt-v1",
            reference.analysisStepReceiptId(),
            reference.address(),
            null,
            List.of(),
            null,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(descriptor),
            null,
            reference.analysisStepArtifactRoot(),
            List.of());
    return new CanonicalAnalysisStepArtifactStore() {
      @Override
      public InstalledAnalysisStepPublication install(
          org.sourceanalysis.app.artifact.AnalysisStepInstallRequest request) {
        throw new AssertionError("Step05 must not be re-published by Activity completion output");
      }

      @Override
      public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference requested) {
        assertThat(requested).isEqualTo(reference);
        return new ReopenedAnalysisStepPublication(
            reference, receipt, List.of(new VerifiedCanonicalPayload(descriptor, bytes)), null);
      }
    };
  }
}
