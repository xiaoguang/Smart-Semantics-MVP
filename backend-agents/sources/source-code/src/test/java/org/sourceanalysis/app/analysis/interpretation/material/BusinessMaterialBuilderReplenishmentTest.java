package org.sourceanalysis.app.analysis.interpretation.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;

/** Confirms that a multi-activity replenishment scenario produces reviewable local packets. */
class BusinessMaterialBuilderReplenishmentTest {

  private static final Set<String> EXPECTED_METHODS =
      Set.of(
          "submitReplenishment",
          "approveAtStore",
          "approveRegionAndCreatePurchaseOrder",
          "approvePurchaseOrderAndProcessExpense",
          "executePurchaseAndRegisterLogistics",
          "receiveAndRegisterInventory",
          "generateConfirmAndSettleMonthlyBill");

  @TempDir Path temporaryDirectory;

  @Test
  void createsOneBoundedReadablePacketForEachReplenishmentActivity() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createSyntheticReplenishmentToSettlement(
            temporaryDirectory.resolve("replenishment-materials"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(4, 24, 12_000)));

      assertThat(result.materialSet().materials()).hasSize(7);
      assertThat(result.materialSet().entryCoverage())
          .allSatisfy(
              coverage -> {
                assertThat(coverage.disposition()).isIn("ANALYZED_MATERIAL", "MATERIAL_WITH_GAPS");
                assertThat(coverage.materialId()).isNotBlank();
              });
      Set<String> methodsInPackets =
          result.materialSet().materials().stream()
              .flatMap(material -> material.modelPacket().allowlistedRefs().stream())
              .map(ModelActivityPacket.AllowlistedReference::snippet)
              .flatMap(snippet -> EXPECTED_METHODS.stream().filter(snippet::contains))
              .collect(Collectors.toSet());
      assertThat(methodsInPackets).containsExactlyInAnyOrderElementsOf(EXPECTED_METHODS);
      assertThat(result.materialSet().materials())
          .allSatisfy(
              material -> {
                assertThat(material.modelPacket().allowlistedRefs()).hasSizeLessThanOrEqualTo(4);
                assertThat(material.modelPacket().allowlistedRefs())
                    .allSatisfy(reference -> assertThat(reference.snippet()).isNotBlank());
              });
    }
  }
}
