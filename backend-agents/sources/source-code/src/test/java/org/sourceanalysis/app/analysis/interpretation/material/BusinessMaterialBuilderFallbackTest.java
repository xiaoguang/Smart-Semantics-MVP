package org.sourceanalysis.app.analysis.interpretation.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Ensures a technical Flow Gap cannot make a still-readable HTTP entry silently disappear. */
class BusinessMaterialBuilderFallbackTest {

  @TempDir Path temporaryDirectory;

  @Test
  void fallsBackToLocatedHandlerMaterialWhenEveryTechnicalFlowIsGapped() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("fallback"))) {
      BusinessFlowsReference flows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(
              fixture, zeroFlowProfile(), capsuleProfile());

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000)));

      assertThat(result.materialSet().materials()).hasSize(2);
      assertThat(result.materialSet().materials())
          .allSatisfy(
              material -> {
                assertThat(material.materialMode())
                    .isEqualTo(BusinessMaterialMode.ENTRY_SOURCE_FALLBACK);
                assertThat(material.sourceRefs()).isNotEmpty();
                assertThat(material.flowRefs()).isEmpty();
                assertThat(material.limitations())
                    .anySatisfy(value -> assertThat(value).contains("技术流程"));
              });
      assertThat(result.materialSet().entryCoverage())
          .allSatisfy(
              entry -> {
                assertThat(entry.disposition()).isEqualTo("MATERIAL_WITH_GAPS");
                assertThat(entry.materialId()).isNotBlank();
              });
    }
  }

  @Test
  void buildsLocatedEntryMaterialBeforeAnyBusinessFlowPublicationExists() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("direct-entry-fallback"))) {
      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      new BusinessMaterialProfile(8, 24, 12_000)));

      assertThat(result.materialSet().materials()).hasSize(2);
      assertThat(result.materialSet().materials())
          .allSatisfy(
              material -> {
                assertThat(material.materialMode())
                    .isEqualTo(BusinessMaterialMode.ENTRY_SOURCE_FALLBACK);
                assertThat(material.sourceRefs()).isNotEmpty();
                assertThat(material.flowRefs()).isEmpty();
                assertThat(material.limitations())
                    .anySatisfy(value -> assertThat(value).contains("技术流程尚未完整编译"));
              });
      assertThat(result.materialSet().entryCoverage())
          .allSatisfy(
              entry -> {
                assertThat(entry.disposition()).isEqualTo("MATERIAL_WITH_GAPS");
                assertThat(entry.reasonCode()).isEqualTo("ENTRY_SOURCE_FALLBACK");
              });
    }
  }

  @Test
  void addsOneUniquelyResolvedDirectJavaTargetToHandlerOnlyMaterial() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("direct-entry-context"))) {
      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      new BusinessMaterialProfile(8, 24, 12_000)));

      BusinessMaterial approve =
          result.materialSet().materials().stream()
              .filter(
                  material ->
                      material.sourceRefs().stream()
                          .anyMatch(
                              reference -> reference.snippet().contains("orderService.approve")))
              .findFirst()
              .orElseThrow();

      assertThat(approve.sourceRefs()).hasSize(2);
      assertThat(approve.sourceRefs())
          .extracting(SourceReference::snippet)
          .anySatisfy(snippet -> assertThat(snippet).contains("void approve(String status)"))
          .anySatisfy(snippet -> assertThat(snippet).contains("approvalClient.record(status)"));
      assertThat(approve.modelPacket().allowlistedRefs())
          .extracting(ModelActivityPacket.AllowlistedReference::snippet)
          .containsExactlyElementsOf(
              approve.sourceRefs().stream().map(SourceReference::snippet).toList());
      assertThat(approve.technicalObservations())
          .anySatisfy(value -> assertThat(value).contains("直接调用的同仓库方法"));
      assertThat(approve.modelPacket().technicalObservations())
          .anyMatch(value -> value.startsWith("源码输入：") && value.contains("status"))
          .anyMatch(value -> value.startsWith("源码条件：") && value.contains("status == null"))
          .anyMatch(
              value -> value.startsWith("源码调用：") && value.contains("approvalClient.record(status)"))
          .anyMatch(value -> value.startsWith("源码终止：") && value.contains("return"));
    }
  }

  @Test
  void keepsALateDirectCallFromALongSelectedServiceMethodInTheModelPacket() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithLongGuardedApprove(
            temporaryDirectory.resolve("long-direct-entry-context"))) {
      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      new BusinessMaterialProfile(4, 24, 12_000)));

      BusinessMaterial approve =
          result.materialSet().materials().stream()
              .filter(
                  material ->
                      material.sourceRefs().stream()
                          .anyMatch(
                              reference -> reference.snippet().contains("orderService.approve")))
              .findFirst()
              .orElseThrow();

      assertThat(approve.sourceRefs())
          .anySatisfy(
              reference -> assertThat(reference.snippet()).contains("approvalClient.record(normalized)"));
      assertThat(approve.modelPacket().technicalObservations())
          .anyMatch(
              value ->
                  value.startsWith("源码调用：")
                      && value.contains("approvalClient.record(normalized)"));
    }
  }

  @Test
  void summarizesAMiddleDirectCallWhenOnlyTheOpeningAndFinalWindowsFit() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithLongGuardedApprove(
            temporaryDirectory.resolve("middle-direct-entry-context"))) {
      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      new BusinessMaterialProfile(3, 24, 12_000)));

      BusinessMaterial approve =
          result.materialSet().materials().stream()
              .filter(
                  material ->
                      material.sourceRefs().stream()
                          .anyMatch(
                              reference -> reference.snippet().contains("orderService.approve")))
              .findFirst()
              .orElseThrow();

      assertThat(approve.sourceRefs())
          .noneMatch(reference -> reference.snippet().contains("approvalClient.record(normalized)"));
      assertThat(approve.sourceRefs())
          .anySatisfy(reference -> assertThat(reference.snippet()).contains("auditClient.record(normalized)"));
      assertThat(approve.modelPacket().technicalObservations())
          .anyMatch(
              value ->
                  value.startsWith("源码调用：")
                      && value.contains("approvalClient.record(normalized)"));
    }
  }

  @Test
  void summarizesMiddleStateChangeAndPersistenceCallsFromALongSelectedMethod() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithLongGuardedApprove(
            temporaryDirectory.resolve("middle-state-change-context"))) {
      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      new BusinessMaterialProfile(3, 24, 12_000)));

      BusinessMaterial approve =
          result.materialSet().materials().stream()
              .filter(
                  material ->
                      material.sourceRefs().stream()
                          .anyMatch(
                              reference -> reference.snippet().contains("orderService.approve")))
              .findFirst()
              .orElseThrow();

      assertThat(approve.modelPacket().technicalObservations())
          .anyMatch(
              value ->
                  value.startsWith("源码调用：")
                      && value.contains("record.setStatus(normalized)"))
          .anyMatch(
              value ->
                  value.startsWith("源码调用：") && value.contains("orderMapper.update(record)"));
    }
  }

  private static FlowCompilationProfile zeroFlowProfile() {
    return new FlowCompilationProfile(
        reference("fallback-flow-profile"), 16, 8, 1, 96, 32, 64, 256);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("fallback-capsule-profile"), 16, 32, 4_096, 200_000);
  }

  private static ArtifactReference reference(String value) {
    return new ArtifactReference(
        ArtifactId.parse("material-profile:" + sha256(value)),
        new Sha256Digest(sha256(value + ":bytes")));
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }
}
