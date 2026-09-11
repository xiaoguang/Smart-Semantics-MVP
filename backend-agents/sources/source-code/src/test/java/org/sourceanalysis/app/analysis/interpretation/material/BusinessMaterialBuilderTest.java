package org.sourceanalysis.app.analysis.interpretation.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Public-seam contract for the model-readable material checkpoint. */
class BusinessMaterialBuilderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void turnsPersistedFlowsIntoReadablePacketsAndKeepsSourceDetailsOutOfModelInput()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("materials"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000)));

      assertThat(result.materialSet().materials())
          .singleElement()
          .satisfies(
              material -> {
                assertThat(material.entryIds()).hasSize(2);
                assertThat(material.modelPacket().context())
                    .contains("HTTP POST /orders/cancel", "HTTP POST /orders/approve");
              });
      assertThat(result.materialSet().entryCoverage()).hasSize(2);
      assertThat(result.materialSet().entryCoverage())
          .allSatisfy(
              coverage -> {
                BusinessMaterial material =
                    result.materialSet().materials().stream()
                        .filter(value -> coverage.materialId().equals(value.materialId()))
                        .findFirst()
                        .orElseThrow();
                assertThat(coverage.disposition())
                    .isEqualTo(
                        material.hasSubstantiveLimitation()
                            ? "MATERIAL_WITH_GAPS"
                            : "ANALYZED_MATERIAL");
              });
      assertThat(result.materialSet().materials())
          .allSatisfy(
              material -> {
                assertThat(material.materialMode()).isEqualTo(BusinessMaterialMode.FLOW_PREFERRED);
                assertThat(material.technicalObservations()).isNotEmpty();
                assertThat(material.sourceRefs()).isNotEmpty();
                assertThat(material.modelPacket().allowlistedRefs()).isNotEmpty();
                assertThat(
                        material.modelPacket().allowlistedRefs().stream()
                            .map(ModelActivityPacket.AllowlistedReference::ref)
                            .toList())
                    .doesNotHaveDuplicates();
                assertThat(material.modelPacket().toString())
                    .doesNotContain(
                        ".java",
                        "sha256",
                        "startLine",
                        "endLine",
                        "proof:",
                        "evidence-node:",
                        "data-flow-node:",
                        "program-graph:",
                        "java-parameter-symbol-v1:",
                        "call-node:",
                        "flow:",
                        "fact-gap:",
                        "material:");
              });

      Set<String> refs = new HashSet<>();
      result
          .materialSet()
          .materials()
          .forEach(material -> material.sourceRefs().forEach(ref -> refs.add(ref.ref())));
      assertThat(refs).hasSizeGreaterThanOrEqualTo(2);

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
      List<JsonNode> lines =
          Arrays.stream(jsonl.stripTrailing().split("\\n"))
              .map(
                  line ->
                      new CanonicalJsonCodec()
                          .parseCanonical(
                              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
              .toList();
      assertThat(lines).hasSize(3);
      List<JsonNode> materialRecords =
          lines.stream()
              .filter(line -> line.path("recordType").asText().equals("BUSINESS_MATERIAL"))
              .toList();
      List<JsonNode> coverageRecords =
          lines.stream()
              .filter(line -> line.path("recordType").asText().equals("ENTRY_COVERAGE"))
              .toList();
      assertThat(materialRecords).hasSize(1);
      assertThat(coverageRecords).hasSize(2);
      assertThat(materialRecords.get(0).path("sourceRefs").get(0).path("file").asText())
          .endsWith(".java");
      assertThat(materialRecords)
          .allSatisfy(
              record -> {
                JsonNode packet = record.path("modelPacket");
                assertThat(packet.isObject()).isTrue();
                assertThat(packet.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder(
                        "context", "technicalObservations", "allowlistedRefs", "limitations");
                assertThat(packet.toString())
                    .doesNotContain("file", "startLine", "endLine", "sha256", "proof:");
              });
      assertThat(coverageRecords)
          .allSatisfy(
              record -> {
                assertThat(record.path("entryId").asText()).isNotBlank();
                assertThat(record.path("disposition").asText()).isNotBlank();
                assertThat(record.path("materialId").asText()).isNotBlank();
              });
    }
  }

  @Test
  void marksAnOverBudgetEntryWithoutCreatingAComparableModelPacket() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("over-budget"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(flows, new BusinessMaterialProfile(8, 24, 1)));

      assertThat(result.materialSet().materials()).isEmpty();
      assertThat(result.materialSet().entryCoverage())
          .allSatisfy(
              entry -> {
                assertThat(entry.disposition()).isEqualTo("NOT_MATERIALIZED");
                assertThat(entry.reasonCode()).isEqualTo("SOURCE_MATERIAL_OVER_BUDGET");
                assertThat(entry.materialId()).isNull();
              });
    }
  }

  @Test
  void exposesACompactCodeOutlineForASelectedLocalActivityWithoutNamingTheBusiness()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("code-outline"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000)));

      BusinessMaterial guardedActivity =
          result.materialSet().materials().stream()
              .filter(
                  material ->
                      material.modelPacket().allowlistedRefs().stream()
                          .anyMatch(reference -> reference.snippet().contains("status == null")))
              .findFirst()
              .orElseThrow();

      assertThat(guardedActivity.modelPacket().technicalObservations())
          .anyMatch(value -> value.startsWith("源码输入：") && value.contains("status"))
          .anyMatch(value -> value.startsWith("源码条件：") && value.contains("status == null"))
          .anyMatch(
              value -> value.startsWith("源码调用：") && value.contains("approvalClient.record(status)"))
          .anyMatch(value -> value.startsWith("源码终止：") && value.contains("return"))
          .noneMatch(value -> value.contains("订单") || value.contains("审批"));
    }
  }

  @Test
  void keepsCallerArgumentsBoundaryAndSourceTogetherInOneModelPacket() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("connected-entry-context"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterial approve =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
                  .build(
                      new BuildBusinessMaterialsRequest(
                          flows, new BusinessMaterialProfile(8, 24, 12_000)))
                  .materialSet()
                  .materials()
                  .stream()
                  .filter(
                      material ->
                          material.sourceRefs().stream()
                              .anyMatch(
                                  reference ->
                                      reference.snippet().contains("orderService.approve")))
                  .findFirst()
                  .orElseThrow();

      assertThat(approve.modelPacket().allowlistedRefs())
          .extracting(ModelActivityPacket.AllowlistedReference::snippet)
          .anySatisfy(snippet -> assertThat(snippet).contains("orderService.approve(status)"))
          .anySatisfy(snippet -> assertThat(snippet).contains("approvalClient.record(status)"));
      assertThat(approve.modelPacket().technicalObservations())
          .anySatisfy(
              observation ->
                  assertThat(observation)
                      .contains(
                          "OrderController#approve", "OrderService#approve", "java.lang.String"))
          .anySatisfy(
              observation ->
                  assertThat(observation)
                      .contains(
                          "OrderService#approve",
                          "ApprovalClient#record",
                          "java.lang.String",
                          "边界"))
          .anySatisfy(observation -> assertThat(observation).contains("status == null"))
          .anySatisfy(observation -> assertThat(observation).contains("返回路径"));
    }
  }
}
