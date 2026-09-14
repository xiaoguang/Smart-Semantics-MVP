package org.sourceanalysis.app.analysis.interpretation.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** RED contract for direct, source-run-independent reopening of the M10 material checkpoint. */
class BusinessMaterialCheckpointReaderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void reopensCompleteM10MaterialAndCoverageFromItsFullReferenceAfterSourceRunFailure()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture = fixture("material-reader")) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult built = buildMaterials(fixture, flows);
      assertThat(built.checkpoint().address())
          .satisfies(AnalysisStepModuleAddress.class::isInstance);
      AnalysisStepModuleAddress address = (AnalysisStepModuleAddress) built.checkpoint().address();
      assertThat(address.analysisStepKey()).isEqualTo(AnalysisStepKey.FLOW_INTERPRETATION);
      assertThat(address.moduleNumber()).isEqualTo(10);
      assertThat(address.moduleKey()).isEqualTo("business-material-builder");

      ReopenOnlyModuleStore reopenOnly =
          new ReopenOnlyModuleStore(fixture.moduleArtifacts(), built.checkpoint());
      BusinessMaterialBuildResult reopened = reopenMaterials(reopenOnly, built.checkpoint());

      assertThat(reopened).isEqualTo(built);
      assertThat(reopened.materialSet().materials()).isEqualTo(built.materialSet().materials());
      assertThat(reopened.materialSet().entryCoverage())
          .isEqualTo(built.materialSet().entryCoverage());
      assertThat(reopenOnly.reopenCount).isEqualTo(1);
    }
  }

  @Test
  void rejectsMissingOrNonM10MaterialReferenceWithExplicitCheckpointFailure() throws Exception {
    try (ProgramGraphsPublicFixture fixture = fixture("material-reader-invalid")) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult built = buildMaterials(fixture, flows);

      ModulePublicationReference wrongModule =
          new ModulePublicationReference(
              new AnalysisStepModuleAddress(
                  built.checkpoint().address().runId(),
                  AnalysisStepKey.FLOW_INTERPRETATION,
                  11,
                  "activity-explainer"),
              built.checkpoint().moduleArtifactRoot(),
              built.checkpoint().moduleReceiptId(),
              built.checkpoint().moduleReceiptSha256());
      assertThatThrownBy(() -> reopenMaterials(fixture.moduleArtifacts(), wrongModule))
          .hasMessageContaining("BUSINESS_MATERIAL_CHECKPOINT_INVALID");

      ModulePublicationReference missing =
          new ModulePublicationReference(
              new AnalysisStepModuleAddress(
                  AnalysisRunId.parse("analysis-run:" + "f".repeat(64)),
                  AnalysisStepKey.FLOW_INTERPRETATION,
                  10,
                  "business-material-builder"),
              built.checkpoint().moduleArtifactRoot(),
              built.checkpoint().moduleReceiptId(),
              built.checkpoint().moduleReceiptSha256());
      assertThatThrownBy(() -> reopenMaterials(fixture.moduleArtifacts(), missing))
          .hasMessageContaining("BUSINESS_MATERIAL_CHECKPOINT_INVALID");
    }
  }

  private ProgramGraphsPublicFixture fixture(String name) {
    return ProgramGraphsPublicFixture.createWithGuardedApprove(temporaryDirectory.resolve(name));
  }

  private static BusinessMaterialBuildResult buildMaterials(
      ProgramGraphsPublicFixture fixture, BusinessFlowsReference flows) {
    return new BusinessMaterialBuilder(
            fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
        .build(
            new BuildBusinessMaterialsRequest(flows, new BusinessMaterialProfile(8, 24, 12_000)));
  }

  private static BusinessMaterialBuildResult reopenMaterials(
      CanonicalModuleArtifactStore artifacts, ModulePublicationReference checkpoint)
      throws Exception {
    Class<?> reader;
    try {
      reader =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialCheckpointReader");
    } catch (ClassNotFoundException missing) {
      fail("BUSINESS_MATERIAL_CHECKPOINT_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
    Constructor<?> constructor;
    try {
      constructor = reader.getDeclaredConstructor(CanonicalModuleArtifactStore.class);
    } catch (NoSuchMethodException missing) {
      fail("BUSINESS_MATERIAL_CHECKPOINT_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
    constructor.setAccessible(true);
    Object instance = constructor.newInstance(artifacts);
    Method reopen;
    try {
      reopen = reader.getDeclaredMethod("reopen", ModulePublicationReference.class);
    } catch (NoSuchMethodException missing) {
      fail("BUSINESS_MATERIAL_CHECKPOINT_READER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable", missing);
    }
    reopen.setAccessible(true);
    try {
      return (BusinessMaterialBuildResult) reopen.invoke(instance, checkpoint);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new AssertionError(cause);
    }
  }

  private static final class ReopenOnlyModuleStore implements CanonicalModuleArtifactStore {
    private final CanonicalModuleArtifactStore delegate;
    private final ModulePublicationReference expected;
    private int reopenCount;

    private ReopenOnlyModuleStore(
        CanonicalModuleArtifactStore delegate, ModulePublicationReference expected) {
      this.delegate = delegate;
      this.expected = expected;
    }

    @Override
    public org.sourceanalysis.app.artifact.InstalledModulePublication install(
        ModuleInstallRequest request) {
      throw new AssertionError("material checkpoint reopen must not install a publication");
    }

    @Override
    public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
      throw new AssertionError("material checkpoint reopen must not resolve a producer policy");
    }

    @Override
    public ReopenedModulePublication reopen(ModulePublicationReference reference) {
      reopenCount++;
      if (expected != null && !expected.equals(reference)) {
        throw new AssertionError("material checkpoint reader changed the full reference");
      }
      return delegate.reopen(reference);
    }
  }
}
