package org.sourceanalysis.app.analysis.fact;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;

/** Public-seam RED for executing the already-persisted M1–M3 Fact chain. */
class ProvenCodeFactsExecutionTest {

  @TempDir Path temporaryDirectory;

  @Test
  void executesFactCandidatesProofsAndTheFourFileLedgerFromPersistedTechnicalInputs()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("fixture"))) {
      Class<?> executorType =
          typeOrNull("org.sourceanalysis.app.analysis.fact.ProvenCodeFactsExecutor");

      assertThat(executorType)
          .as("Step 04 needs one production execution seam instead of acceptance-test composition")
          .isNotNull();
      assertThat(executorType.getDeclaredConstructors())
          .as("the execution seam cannot accept caller-owned filesystem paths")
          .allSatisfy(
              constructor ->
                  assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));

      Object executor =
          executorType
              .getConstructor(
                  org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader.class,
                  CanonicalModuleArtifactStore.class,
                  CanonicalAnalysisStepArtifactStore.class)
              .newInstance(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts());
      Method execute =
          executorType.getMethod(
              "execute",
              org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
              org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class);

      ProvenCodeFactsReference result =
          (ProvenCodeFactsReference)
              execute.invoke(
                  executor,
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());

      assertThat(fixture.stepArtifacts().reopen(result.publication()).semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "fact-accounting.json", "gap-ledger.json", "proof-pack.json", "proven-facts.json");
    }
  }

  private static Class<?> typeOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }
}
