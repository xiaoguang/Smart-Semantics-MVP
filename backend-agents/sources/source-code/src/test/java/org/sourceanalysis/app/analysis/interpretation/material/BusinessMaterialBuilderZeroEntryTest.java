package org.sourceanalysis.app.analysis.interpretation.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Prevents a zero-entry repository from being turned into invented model-reading material. */
class BusinessMaterialBuilderZeroEntryTest {

  @TempDir Path temporaryDirectory;

  @Test
  void persistsAnEmptyMaterialCheckpointWhenDiscoveryFindsNoHttpEntries() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithoutHttpEntries(
            temporaryDirectory.resolve("zero-entry"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000)));

      assertThat(result.materialSet().materials()).isEmpty();
      assertThat(result.materialSet().entryCoverage()).isEmpty();

      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(result.checkpoint());
      String jsonl =
          new String(
              reopened.payloads().stream()
                  .filter(value -> value.descriptor().fileName().equals("business-materials.jsonl"))
                  .findFirst()
                  .orElseThrow()
                  .canonicalUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      assertThat(jsonl).isEmpty();
    }
  }
}
