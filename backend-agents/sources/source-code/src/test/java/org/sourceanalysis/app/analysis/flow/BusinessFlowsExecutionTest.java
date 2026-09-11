package org.sourceanalysis.app.analysis.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.ProvenCodeFactsExecutor;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Public-seam RED for executing the persisted Flow/Capsule/publication chain. */
class BusinessFlowsExecutionTest {

  @TempDir Path temporaryDirectory;

  @Test
  void executesFlowCompilationCapsuleProjectionAndFiveFilePublication() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("two-entry-fixture"))) {
      BusinessFlowsReference result = execute(fixture);

      assertThat(fixture.stepArtifacts().reopen(result.publication()).semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
    }
  }

  @Test
  void publishesTheSameFiveFilesWhenTheEntryDenominatorHasNoFlows() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithoutHttpEntries(
            temporaryDirectory.resolve("zero-entry-fixture"))) {
      BusinessFlowsReference result = execute(fixture);

      assertThat(fixture.stepArtifacts().reopen(result.publication()).semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
    }
  }

  private static BusinessFlowsReference execute(ProgramGraphsPublicFixture fixture)
      throws Exception {
    ProvenCodeFactsReference facts =
        new ProvenCodeFactsExecutor(
                fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
            .execute(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    Class<?> requestType =
        typeOrNull("org.sourceanalysis.app.analysis.flow.BusinessFlowsExecutionRequest");
    Class<?> executorType =
        typeOrNull("org.sourceanalysis.app.analysis.flow.BusinessFlowsExecutor");

    assertThat(requestType)
        .as("Step 05 needs one typed execution request rather than six unbound caller arguments")
        .isNotNull();
    assertThat(executorType)
        .as("Step 05 needs one production execution seam instead of test-only M1–M3 composition")
        .isNotNull();
    assertThat(executorType.getDeclaredConstructors())
        .as("the execution seam cannot accept caller-owned filesystem paths")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));

    Object request =
        requestType
            .getConstructor(
                org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
                org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference.class,
                org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
                ProvenCodeFactsReference.class,
                FlowCompilationProfile.class,
                CapsuleProjectionProfile.class)
            .newInstance(
                fixture.sourceInventory(),
                fixture.applicationDiscovery(),
                fixture.programGraphs(),
                facts,
                flowProfile(),
                capsuleProfile());
    Object executor =
        executorType
            .getConstructor(
                org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader.class,
                CanonicalModuleArtifactStore.class,
                CanonicalAnalysisStepArtifactStore.class)
            .newInstance(
                fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts());
    Method execute = executorType.getMethod("execute", requestType);
    return (BusinessFlowsReference) execute.invoke(executor, request);
  }

  private static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", 'a', 'b'), 16, 8, 64, 96, 32, 64, 256);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", 'c', 'd'), 16, 32, 4_096, 24_576);
  }

  private static ArtifactReference reference(String prefix, char identity, char content) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(identity).repeat(64)),
        Sha256Digest.parse(String.valueOf(content).repeat(64)));
  }

  private static Class<?> typeOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }
}
