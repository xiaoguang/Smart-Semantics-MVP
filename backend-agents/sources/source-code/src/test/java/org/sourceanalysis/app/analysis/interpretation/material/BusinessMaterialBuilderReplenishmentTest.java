package org.sourceanalysis.app.analysis.interpretation.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.testsupport.BusinessFlowTestSupport;

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
  void groupsRelatedReplenishmentEntriesIntoBoundedReadablePackets() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createSyntheticReplenishmentToSettlement(
            temporaryDirectory.resolve("replenishment-materials"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(4, 24, 12_000)));

      assertThat(result.materialSet().materials()).hasSize(2);
      assertThat(result.materialSet().materials())
          .allSatisfy(
              material -> {
                assertThat(material.entryIds()).hasSizeBetween(3, 4);
                assertThat(material.entryIds()).doesNotHaveDuplicates();
                assertThat(material.modelPacket().context())
                    .contains("HTTP 入口")
                    .contains("请仅依据本包片段和观察，解释其局部业务活动");
                IntStream.rangeClosed(1, material.entryIds().size())
                    .forEach(
                        entryIndex ->
                            assertThat(material.modelPacket().context())
                                .contains("入口 E" + entryIndex + "："));
              });
      assertThat(result.materialSet().entryCoverage())
          .allSatisfy(
              coverage -> {
                assertThat(coverage.disposition()).isIn("ANALYZED_MATERIAL", "MATERIAL_WITH_GAPS");
                assertThat(coverage.materialId()).isNotBlank();
                BusinessMaterial material =
                    result.materialSet().materials().stream()
                        .filter(candidate -> candidate.materialId().equals(coverage.materialId()))
                        .findFirst()
                        .orElseThrow();
                assertThat(material.entryIds()).contains(coverage.entryId());
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
