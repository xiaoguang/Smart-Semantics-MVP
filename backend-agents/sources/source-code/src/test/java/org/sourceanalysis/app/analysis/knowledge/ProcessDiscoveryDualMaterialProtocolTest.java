package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
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

    ActivityExplanationResult step05Activities =
        withCheckpoint(FrozenAnalysisCorpusDualMaterialSourceTest.step05Activities());
    CodeReadingMaterialSet step05Materials =
        FrozenAnalysisCorpusDualMaterialSourceTest.step05Materials();
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
    ActivityExplanationResult complete =
        withCheckpoint(FrozenAnalysisCorpusDualMaterialSourceTest.step05Activities());
    ActivityExplanationResult partial =
        new ActivityExplanationResult(
            complete.reviewedActivities(),
            List.of(
                complete.coverage().get(0),
                complete.coverage().get(1),
                new ActivityEntryCoverage(
                    "entry:missing", "NOT_ANALYZED", List.of(), "MODEL_NOT_EXPLAINED")),
            complete.unexplainedActivityEntries(),
            complete.checkpoint());
    CodeReadingMaterialSet materials = FrozenAnalysisCorpusDualMaterialSourceTest.step05Materials();

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
  void permitsExplicitUpstreamNavigationGapWithoutCallingItAnActivityFailure() {
    ActivityExplanationResult complete =
        withCheckpoint(FrozenAnalysisCorpusDualMaterialSourceTest.step05Activities());
    CodeReadingMaterialSet original = FrozenAnalysisCorpusDualMaterialSourceTest.step05Materials();
    CodeReadingMaterialSet materials =
        new CodeReadingMaterialSet(
            original.header(),
            original.packets(),
            java.util.stream.Stream.concat(
                    original.coverage().stream(),
                    java.util.stream.Stream.of(
                        new CodeReadingMaterialSet.EntryCoverage(
                            "entry:navigation-gap",
                            List.of(),
                            CodeReadingMaterialSet.CoverageStatus.NOT_COLLECTED,
                            List.of("JDT_NAVIGATION_TIMEOUT"))))
                .toList());
    ActivityExplanationResult activities =
        new ActivityExplanationResult(
            complete.reviewedActivities(),
            java.util.stream.Stream.concat(
                    complete.coverage().stream(),
                    java.util.stream.Stream.of(
                        new ActivityEntryCoverage(
                            "entry:navigation-gap",
                            "NOT_ANALYZED",
                            List.of(),
                            "JDT_NAVIGATION_TIMEOUT")))
                .toList(),
            complete.unexplainedActivityEntries(),
            complete.checkpoint());

    ProcessDiscoveryRequest request =
        new ProcessDiscoveryRequest(
            activities,
            materials,
            step05Checkpoint(),
            profile(),
            RUN,
            materials.header().sourceInventory(),
            ignored -> FrozenAnalysisCorpusDualMaterialSourceTest.sourceTextSet(List.of()),
            null,
            null);
    assertThat(request.usesCodeReadingMaterials()).isTrue();
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
