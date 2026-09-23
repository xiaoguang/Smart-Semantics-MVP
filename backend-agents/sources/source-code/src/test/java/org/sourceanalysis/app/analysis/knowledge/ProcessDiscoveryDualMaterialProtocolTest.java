package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityPacketCompletion;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialPublisher;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.AnalysisStepReceipt;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallDisposition;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.runtime.BusinessProcessWorkflowResult;
import org.sourceanalysis.app.runtime.PersistedBusinessProcessRunExecutor;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;

class ProcessDiscoveryDualMaterialProtocolTest {

  @Test
  void keepsLegacyAndStep05MaterialSourcesExplicitAndMutuallyExclusive() {
    ActivityExplanationResult legacyActivities =
        withCheckpoint(FrozenAnalysisCorpusDualMaterialSourceTest.legacyActivities());
    ProcessDiscoveryRequest legacy =
        new ProcessDiscoveryRequest(
            legacyActivities,
            FrozenAnalysisCorpusDualMaterialSourceTest.legacyMaterials(),
            profile(),
            RUN);

    assertThat(legacy.usesCodeReadingMaterials()).isFalse();
    assertThat(legacy.codeReadingMaterials()).isNull();
    assertThat(legacy.codeReadingMaterialCheckpoint()).isNull();

    CodeReadingMaterialSet step05Materials = completionGateMaterials(false);
    ActivityExplanationResult step05Activities =
        currentStep05Activities(step05Materials, completePacketCompletions(step05Materials));
    AnalysisStepPublicationReference step05Checkpoint = step05Checkpoint();
    VerifiedSourceTextReader sourceText =
        ignored -> FrozenAnalysisCorpusDualMaterialSourceTest.sourceTextSet(List.of());
    ProcessDiscoveryRequest step05 =
        new ProcessDiscoveryRequest(
            step05Activities,
            step05Materials,
            step05Checkpoint,
            profile(),
            RUN,
            step05Materials.header().sourceInventory(),
            sourceText,
            null,
            null);

    assertThat(step05.usesCodeReadingMaterials()).isTrue();
    assertThat(step05.materials()).isNull();
    assertThat(step05.codeReadingMaterials()).isSameAs(step05Materials);
    assertThat(step05.codeReadingMaterialCheckpoint()).isEqualTo(step05Checkpoint);

    assertThatThrownBy(
            () ->
                new ProcessDiscoveryRequest(
                    step05Activities,
                    FrozenAnalysisCorpusDualMaterialSourceTest.legacyMaterials(),
                    step05Materials,
                    step05Checkpoint,
                    profile(),
                    RUN,
                    step05Materials.header().sourceInventory(),
                    sourceText,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROCESS_DISCOVERY_MATERIAL_SOURCE_INVALID");
    assertThatThrownBy(
            () ->
                new ProcessDiscoveryRequest(
                    step05Activities, null, null, null, profile(), RUN, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROCESS_DISCOVERY_MATERIAL_SOURCE_INVALID");
  }

  @Test
  void rejectsPartialStep05ActivityBeforeProcessDiscovery() {
    CodeReadingMaterialSet materials = completionGateMaterials(false);
    ActivityExplanationResult complete =
        currentStep05Activities(materials, completePacketCompletions(materials));
    ActivityExplanationResult partial =
        new ActivityExplanationResult(
            complete.reviewedActivities(),
            List.of(
                complete.coverage().get(0),
                complete.coverage().get(1),
                new ActivityEntryCoverage(
                    "entry:missing", "NOT_ANALYZED", List.of(), "MODEL_NOT_EXPLAINED")),
            complete.unexplainedActivityEntries(),
            complete.packetCompletion().orElseThrow(),
            complete.checkpoint());
    assertThatThrownBy(
            () ->
                new ProcessDiscoveryRequest(
                    partial,
                    materials,
                    step05Checkpoint(),
                    profile(),
                    RUN,
                    materials.header().sourceInventory(),
                    ignored -> FrozenAnalysisCorpusDualMaterialSourceTest.sourceTextSet(List.of()),
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT");
  }

  @Test
  void rejectsReviewedActivitiesWhenAnyRequiredReadingSliceRemainsUnfulfilled() {
    CodeReadingMaterialSet materials = completionGateMaterials(false);
    ActivityExplanationResult complete =
        currentStep05Activities(materials, completePacketCompletions(materials));
    ActivityEntryCoverage first = complete.coverage().get(0);
    ActivityExplanationResult incompleteRequiredSlice =
        new ActivityExplanationResult(
            complete.reviewedActivities(),
            java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(
                        new ActivityEntryCoverage(
                            first.entryId(),
                            "ANALYZED_WITH_GAPS",
                            first.activityIds(),
                            "ACTIVITY_READING_INCOMPLETE")),
                    complete.coverage().stream().skip(1))
                .toList(),
            complete.unexplainedActivityEntries(),
            complete.packetCompletion().orElseThrow(),
            complete.checkpoint());
    assertThatThrownBy(
            () ->
                new ProcessDiscoveryRequest(
                    incompleteRequiredSlice,
                    materials,
                    step05Checkpoint(),
                    profile(),
                    RUN,
                    materials.header().sourceInventory(),
                    ignored -> FrozenAnalysisCorpusDualMaterialSourceTest.sourceTextSet(List.of()),
                    null,
                    null))
        .as(
            "one reviewed slice cannot make a packet with an unfulfilled required slice Step07-ready")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT");
  }

  @Test
  void permitsExplicitUpstreamNavigationGapWithoutCallingItAnActivityFailure() {
    CodeReadingMaterialSet materials = completionGateMaterials(true);
    ActivityExplanationResult activities =
        currentStep05Activities(materials, completePacketCompletions(materials));

    ProcessDiscoveryRequest request = step05Request(activities, materials);
    assertThat(request.usesCodeReadingMaterials()).isTrue();
  }

  @Test
  void requiresExplicitCompletePacketSetBeforeStep07AcceptsCurrentStep05Activities() {
    CodeReadingMaterialSet materials = completionGateMaterials(false);
    ActivityExplanationResult historicalWithoutCompletion =
        withCheckpoint(FrozenAnalysisCorpusDualMaterialSourceTest.step05Activities());
    assertStep05Rejected(
        historicalWithoutCompletion, materials, "PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT");

    List<ActivityPacketCompletion> incompletePacketSet =
        new ArrayList<>(completePacketCompletions(materials));
    ActivityPacketCompletion alpha = incompletePacketSet.get(0);
    incompletePacketSet.set(
        0,
        new ActivityPacketCompletion(
            alpha.packetId(),
            alpha.entryIds(),
            ActivityPacketCompletion.Completion.INCOMPLETE,
            List.of("whole-packet"),
            List.of(),
            List.of(
                new ActivityPacketCompletion.IncompleteScope(
                    "whole-packet", alpha.entryIds(), "ACTIVITY_REVIEW_FAILED"))));
    assertStep05Rejected(
        currentStep05Activities(materials, incompletePacketSet),
        materials,
        "PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT");

    List<ActivityPacketCompletion> unknownPacketSet =
        new ArrayList<>(completePacketCompletions(materials));
    ActivityPacketCompletion beta = unknownPacketSet.get(1);
    unknownPacketSet.set(
        1,
        new ActivityPacketCompletion(
            beta.packetId(),
            beta.entryIds(),
            ActivityPacketCompletion.Completion.UNDETERMINED,
            List.of(),
            List.of(),
            List.of(
                new ActivityPacketCompletion.IncompleteScope(
                    null, beta.entryIds(), "HISTORICAL_REQUIRED_SCOPE_UNKNOWN"))));
    assertStep05Rejected(
        currentStep05Activities(materials, unknownPacketSet),
        materials,
        "PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT");
  }

  @Test
  void rejectsPacketOrEntryMembershipThatDisagreesWithStep05Materials() {
    CodeReadingMaterialSet materials = completionGateMaterials(false);

    assertStep05Rejected(
        currentStep05Activities(materials, List.of()),
        materials,
        "PROCESS_DISCOVERY_STEP05_INPUT_INVALID");

    List<ActivityPacketCompletion> wrongPacketSet =
        new ArrayList<>(completePacketCompletions(materials));
    ActivityPacketCompletion alpha = wrongPacketSet.get(0);
    wrongPacketSet.set(
        0,
        new ActivityPacketCompletion(
            "packet:not-in-step05",
            alpha.entryIds(),
            ActivityPacketCompletion.Completion.COMPLETE,
            List.of("whole-packet"),
            List.of("whole-packet"),
            List.of()));
    assertStep05Rejected(
        currentStep05Activities(materials, wrongPacketSet),
        materials,
        "PROCESS_DISCOVERY_STEP05_INPUT_INVALID");

    List<ActivityPacketCompletion> wrongEntrySet =
        new ArrayList<>(completePacketCompletions(materials));
    ActivityPacketCompletion beta = wrongEntrySet.get(1);
    wrongEntrySet.set(
        1,
        new ActivityPacketCompletion(
            beta.packetId(),
            List.of("entry:not-in-packet"),
            ActivityPacketCompletion.Completion.COMPLETE,
            List.of("whole-packet"),
            List.of("whole-packet"),
            List.of()));
    assertStep05Rejected(
        currentStep05Activities(materials, wrongEntrySet),
        materials,
        "PROCESS_DISCOVERY_STEP05_INPUT_INVALID");
  }

  @Test
  void allowsCompletePacketSetAlongsideAnExplicitNotCollectedStep05Entry() {
    CodeReadingMaterialSet materials = completionGateMaterials(true);
    ProcessDiscoveryRequest request =
        step05Request(
            currentStep05Activities(materials, completePacketCompletions(materials)), materials);

    assertThat(request.usesCodeReadingMaterials()).isTrue();
    assertThat(request.codeReadingMaterials().coverage())
        .anySatisfy(
            coverage -> {
              assertThat(coverage.entryId()).isEqualTo("entry:navigation-gap");
              assertThat(coverage.status())
                  .isEqualTo(CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED);
            });
  }

  @Test
  void carriesStep05ProvenanceThroughResultWorkflowAndPublisherUpstream() {
    ActivityExplanationResult activities =
        withCheckpoint(FrozenAnalysisCorpusDualMaterialSourceTest.step05Activities());
    CodeReadingMaterialSet materials = FrozenAnalysisCorpusDualMaterialSourceTest.step05Materials();
    AnalysisStepPublicationReference step05Checkpoint = step05Checkpoint();
    ProcessDiscoveryResult result =
        new ProcessDiscoveryResult(
            emptyCatalog(),
            completeCoverage(),
            List.of(),
            RUN,
            activities.checkpoint(),
            null,
            step05Checkpoint);
    CapturingModuleStore modules = new CapturingModuleStore(activities.checkpoint(), controls());
    CapturingStepStore steps = new CapturingStepStore(step05Checkpoint, controls());

    BusinessProcessPublication publication =
        new CanonicalBusinessProcessPublisher(modules, steps).publish(result);
    BusinessProcessWorkflowResult workflow =
        new BusinessProcessWorkflowResult(
            materials, step05Checkpoint, activities, result, publication);

    assertThat(result.materialCheckpoint()).isNull();
    assertThat(result.codeReadingMaterialCheckpoint()).isEqualTo(step05Checkpoint);
    assertThat(workflow.materials()).isNull();
    assertThat(workflow.codeReadingMaterials()).isEqualTo(materials);
    assertThat(workflow.codeReadingMaterialCheckpoint()).isEqualTo(step05Checkpoint);
    assertThat(modules.reopenCount).isEqualTo(1);
    assertThat(steps.reopenCount).isEqualTo(1);
    assertThat(modules.installedUpstream)
        .extracting(value -> value.artifactId().value())
        .containsExactly(
            "activity-input:" + "1".repeat(64), "code-reading-input:" + "2".repeat(64));
  }

  @Test
  void exposesPersistedExecutorEntryPointsWithoutAdaptingTheStep05Reference() throws Exception {
    assertThat(
            PersistedBusinessProcessRunExecutor.class.getConstructor(
                CanonicalModuleArtifactStore.class,
                CanonicalAnalysisStepArtifactStore.class,
                ModelJobExecutionConfiguration.class,
                ProcessDiscoveryProfile.class))
        .isNotNull();
    assertThat(
            PersistedBusinessProcessRunExecutor.class.getMethod(
                "execute",
                AnalysisRunId.class,
                ActivityExplanationResult.class,
                AnalysisStepPublicationReference.class,
                VerifiedSourceInventoryReference.class,
                VerifiedSourceTextReader.class))
        .isNotNull();
  }

  private static ActivityExplanationResult withCheckpoint(ActivityExplanationResult activities) {
    return new ActivityExplanationResult(
        activities.reviewedActivities(),
        activities.coverage(),
        activities.unexplainedActivityEntries(),
        activityCheckpoint());
  }

  private static void assertStep05Rejected(
      ActivityExplanationResult activities, CodeReadingMaterialSet materials, String errorCode) {
    assertThatThrownBy(() -> step05Request(activities, materials))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(errorCode);
  }

  private static ProcessDiscoveryRequest step05Request(
      ActivityExplanationResult activities, CodeReadingMaterialSet materials) {
    return new ProcessDiscoveryRequest(
        activities,
        materials,
        step05Checkpoint(),
        profile(),
        RUN,
        materials.header().sourceInventory(),
        ignored -> FrozenAnalysisCorpusDualMaterialSourceTest.sourceTextSet(List.of()),
        null,
        null);
  }

  private static CodeReadingMaterialSet completionGateMaterials(boolean includeNotCollected) {
    CodeReadingMaterialSet original = FrozenAnalysisCorpusDualMaterialSourceTest.step05Materials();
    List<CodeReadingMaterialSet.Packet> packets =
        original.packets().stream()
            .map(
                packet -> {
                  String entryId = "entry:" + packet.packetId().substring("packet:".length());
                  return new CodeReadingMaterialSet.Packet(
                      packet.packetId(),
                      List.of(
                          new EntrySeed(
                              entryId,
                              "method:" + packet.packetId().substring("packet:".length()),
                              new SourceRange(0, 1, 1, 1),
                              "HTTP controller entry")),
                      packet.methods(),
                      packet.calls(),
                      packet.persistence(),
                      packet.sourceReferences(),
                      packet.unselectedUnits(),
                      packet.limitations(),
                      packet.selfContainedUtf8Bytes());
                })
            .toList();
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>(original.coverage());
    if (includeNotCollected) {
      coverage.add(
          new CodeReadingMaterialSet.EntryCoverage(
              "entry:navigation-gap",
              List.of(),
              CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED,
              List.of("JDT_NAVIGATION_TIMEOUT")));
    }
    return new CodeReadingMaterialSet(original.header(), packets, coverage);
  }

  private static ActivityExplanationResult currentStep05Activities(
      CodeReadingMaterialSet materials, List<ActivityPacketCompletion> packetCompletion) {
    ActivityExplanationResult original =
        FrozenAnalysisCorpusDualMaterialSourceTest.step05Activities();
    List<ActivityEntryCoverage> coverage = new ArrayList<>(original.coverage());
    if (materials.coverage().stream()
        .anyMatch(value -> "entry:navigation-gap".equals(value.entryId()))) {
      coverage.add(
          new ActivityEntryCoverage(
              "entry:navigation-gap", "NOT_ANALYZED", List.of(), "JDT_NAVIGATION_TIMEOUT"));
    }
    return new ActivityExplanationResult(
        original.reviewedActivities(),
        coverage,
        original.unexplainedActivityEntries(),
        packetCompletion,
        activityCheckpoint());
  }

  private static List<ActivityPacketCompletion> completePacketCompletions(
      CodeReadingMaterialSet materials) {
    List<ReviewedActivity> activities =
        FrozenAnalysisCorpusDualMaterialSourceTest.step05Activities().reviewedActivities();
    return materials.packets().stream()
        .map(
            packet -> {
              List<String> entryIds = packet.entries().stream().map(EntrySeed::entryId).toList();
              List<String> sliceKeys =
                  activities.stream()
                      .filter(activity -> packet.packetId().equals(activity.materialId()))
                      .map(ReviewedActivity::sliceKey)
                      .filter(java.util.Objects::nonNull)
                      .distinct()
                      .toList();
              List<String> requiredSliceKeys =
                  sliceKeys.isEmpty() ? List.of("whole-packet") : sliceKeys;
              return new ActivityPacketCompletion(
                  packet.packetId(),
                  entryIds,
                  ActivityPacketCompletion.Completion.COMPLETE,
                  requiredSliceKeys,
                  requiredSliceKeys,
                  List.of());
            })
        .toList();
  }

  private static RepositoryBusinessProcessCatalog emptyCatalog() {
    return new RepositoryBusinessProcessCatalog(
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static ProcessCoverage completeCoverage() {
    return new ProcessCoverage(List.of(), List.of(), List.of(), "CLOSED", "COMPLETE");
  }

  private static ProcessDiscoveryProfile profile() {
    return new ProcessDiscoveryProfile(4, 4, 4, 4_096, 32_768, 8_192, 2, 8, 1_024);
  }

  private static ModulePublicationReference activityCheckpoint() {
    return moduleReference(AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer", 'a');
  }

  private static AnalysisStepPublicationReference step05Checkpoint() {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(RUN, AnalysisStepKey.BUSINESS_FLOWS),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + "b".repeat(64)),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + "b".repeat(64)),
        Sha256Digest.parse("b".repeat(64)));
  }

  private static ModulePublicationReference moduleReference(
      AnalysisStepKey step, int number, String key, char fill) {
    String digest = String.valueOf(fill).repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(RUN, step, number, key),
        ModuleArtifactRoot.parse("module-root:" + digest),
        ModuleReceiptId.parse("module-receipt:" + digest),
        Sha256Digest.parse(digest));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        Sha256Digest.parse("3".repeat(64)),
        Sha256Digest.parse("4".repeat(64)),
        Sha256Digest.parse("5".repeat(64)),
        Sha256Digest.parse("6".repeat(64)),
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + "7".repeat(64)),
            Sha256Digest.parse("7".repeat(64))));
  }

  private static ArtifactDescriptor descriptor(
      String fileName,
      String artifactType,
      String schemaVersion,
      String artifactPrefix,
      char fill,
      CanonicalMediaType mediaType) {
    String digest = String.valueOf(fill).repeat(64);
    return new ArtifactDescriptor(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactPrefix + ":" + digest),
        mediaType,
        0,
        Sha256Digest.parse(digest));
  }

  private static final AnalysisRunId RUN = AnalysisRunId.parse("analysis-run:" + "c".repeat(64));

  private static final class CapturingModuleStore implements CanonicalModuleArtifactStore {
    private final ModulePublicationReference activity;
    private final ArtifactControls controls;
    private List<ArtifactReference> installedUpstream = List.of();
    private int reopenCount;

    private CapturingModuleStore(ModulePublicationReference activity, ArtifactControls controls) {
      this.activity = activity;
      this.controls = controls;
    }

    @Override
    public InstalledModulePublication install(ModuleInstallRequest request) {
      installedUpstream = request.upstreamArtifacts();
      return new InstalledModulePublication(
          moduleReference(
              AnalysisStepKey.REPOSITORY_KNOWLEDGE, 1, "business-process-publisher", 'd'),
          ModuleInstallDisposition.INSTALLED,
          List.of());
    }

    @Override
    public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ReopenedModulePublication reopen(ModulePublicationReference reference) {
      reopenCount++;
      if (!activity.equals(reference)) {
        throw new IllegalArgumentException("unexpected module reopen");
      }
      ArtifactDescriptor input =
          descriptor(
              "activity-input.json",
              "TEST_ACTIVITY_INPUT",
              "test-v1",
              "activity-input",
              '1',
              CanonicalMediaType.APPLICATION_JSON);
      return new ReopenedModulePublication(
          reference,
          new ModuleReceipt(
              "module-receipt-v2",
              reference.moduleReceiptId(),
              reference.address(),
              "v3",
              List.of(),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(input),
              reference.moduleArtifactRoot(),
              List.of()),
          List.of());
    }
  }

  private static final class CapturingStepStore implements CanonicalAnalysisStepArtifactStore {
    private final AnalysisStepPublicationReference material;
    private final ArtifactControls controls;
    private int reopenCount;

    private CapturingStepStore(
        AnalysisStepPublicationReference material, ArtifactControls controls) {
      this.material = material;
      this.controls = controls;
    }

    @Override
    public InstalledAnalysisStepPublication install(
        org.sourceanalysis.app.artifact.AnalysisStepInstallRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference) {
      reopenCount++;
      if (!material.equals(reference)) {
        throw new IllegalArgumentException("unexpected analysis-step reopen");
      }
      ArtifactDescriptor input =
          descriptor(
              CodeReadingMaterialPublisher.FILE_NAME,
              CodeReadingMaterialPublisher.ARTIFACT_TYPE,
              CodeReadingMaterialPublisher.SCHEMA_VERSION,
              "code-reading-input",
              '2',
              CanonicalMediaType.APPLICATION_X_NDJSON);
      ArtifactDescriptor manifest =
          descriptor(
              "archive-manifest.json",
              "ANALYSIS_STEP_ARCHIVE_MANIFEST",
              "analysis-step-archive-manifest-v1",
              "archive-manifest",
              '8',
              CanonicalMediaType.APPLICATION_JSON);
      AnalysisStepReceipt receipt =
          new AnalysisStepReceipt(
              "analysis-step-receipt-v1",
              reference.analysisStepReceiptId(),
              reference.address(),
              new AnalysisStepPublisherModuleProvenance(activityCheckpoint()),
              List.of(),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(input),
              manifest,
              reference.analysisStepArtifactRoot(),
              List.of());
      return new ReopenedAnalysisStepPublication(
          reference,
          receipt,
          List.of(new VerifiedCanonicalPayload(input, ImmutableBytes.copyOf(new byte[0]))),
          new VerifiedCanonicalPayload(manifest, ImmutableBytes.copyOf(new byte[0])));
    }
  }
}
