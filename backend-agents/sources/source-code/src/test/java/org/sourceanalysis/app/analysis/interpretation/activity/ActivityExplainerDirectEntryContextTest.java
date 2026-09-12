package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Verifies that saved Step05 service context survives the material-to-activity boundary. */
class ActivityExplainerDirectEntryContextTest {

  @TempDir Path temporaryDirectory;

  @Test
  void sendsAHandlerAndSavedDirectTargetThenPreservesReviewedActivityFields() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-direct-context"))) {
      BusinessMaterialBuildResult allMaterials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      BusinessFlowTestSupport.publishBusinessFlows(fixture),
                      new BusinessMaterialProfile(8, 24, 12_000, 1)));
      BusinessMaterial material =
          allMaterials.materialSet().materials().stream()
              .filter(
                  value ->
                      value.sourceRefs().stream()
                          .anyMatch(
                              reference -> reference.snippet().contains("approvalClient.record")))
              .findFirst()
              .orElseThrow();
      BusinessMaterialEntryCoverage materialCoverage =
          allMaterials.materialSet().entryCoverage().stream()
              .filter(value -> material.materialId().equals(value.materialId()))
              .findFirst()
              .orElseThrow();
      BusinessMaterialBuildResult oneMaterial =
          new BusinessMaterialBuildResult(
              new BusinessMaterialSet(
                  allMaterials.materialSet().materialSetId(),
                  List.of(material),
                  List.of(materialCoverage)),
              allMaterials.checkpoint());
      ContextCapturingProvider provider = new ContextCapturingProvider();

      ActivityExplanationResult result =
          new ActivityExplainer(provider)
              .explain(
                  new ExplainActivitiesRequest(
                      oneMaterial, new ActivityExplanationProfile(64_000, 16_000, 1, 24, 2_000)));

      assertThat(provider.taskKinds()).containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW");
      assertThat(provider.packets()).allSatisfy(ContextCapturingProvider::assertCleanDirectContext);
      assertThat(result.coverage())
          .singleElement()
          .satisfies(value -> assertThat(value.disposition()).isEqualTo("ANALYZED_WITH_GAPS"));
      assertThat(result.reviewedActivities())
          .singleElement()
          .satisfies(
              activity -> {
                assertThat(activity.businessPurpose()).isEqualTo("根据状态请求执行订单审批处理。");
                assertThat(activity.businessObjects()).containsExactly("订单", "审批状态");
                assertThat(activity.conditions()).containsExactly("状态不能为空时才记录审批处理。");
                assertThat(activity.activitySteps())
                    .containsExactly("接收状态请求", "调用订单审批处理", "记录审批处理");
                assertThat(activity.codeDefinedResults()).containsExactly("代码调用审批记录处理。");
                assertThat(activity.sourceRefs())
                    .containsExactlyElementsOf(
                        material.modelPacket().allowlistedRefs().stream()
                            .map(value -> value.ref())
                            .toList());
              });
    }
  }

  @Test
  void doesNotTurnTheGenericSnippetBudgetNoticeIntoABusinessCoverageGap() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-budget-notice"))) {
      BusinessMaterialBuildResult allMaterials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      BusinessFlowTestSupport.publishBusinessFlows(fixture),
                      new BusinessMaterialProfile(8, 24, 12_000, 1)));
      BusinessMaterial original = allMaterials.materialSet().materials().get(0);
      BusinessMaterial completeFlow =
          new BusinessMaterial(
              original.materialId(),
              original.entryIds(),
              BusinessMaterialMode.FLOW_PREFERRED,
              original.context(),
              original.technicalObservations(),
              original.sourceRefs(),
              original.flowRefs(),
              original.technicalProofRefs(),
              List.of(BusinessMaterial.SNIPPET_BUDGET_NOTICE),
              original.modelPacket());
      BusinessMaterialBuildResult oneMaterial =
          new BusinessMaterialBuildResult(
              new BusinessMaterialSet(
                  allMaterials.materialSet().materialSetId(),
                  List.of(completeFlow),
                  List.of(
                      new BusinessMaterialEntryCoverage(
                          completeFlow.entryIds().get(0),
                          "ANALYZED_MATERIAL",
                          completeFlow.materialId(),
                          null))),
              allMaterials.checkpoint());

      ActivityExplanationResult result =
          new ActivityExplainer(new ContextCapturingProvider())
              .explain(
                  new ExplainActivitiesRequest(
                      oneMaterial, new ActivityExplanationProfile(64_000, 16_000, 1, 24, 2_000)));

      assertThat(result.coverage())
          .singleElement()
          .satisfies(value -> assertThat(value.disposition()).isEqualTo("ANALYZED"));
    }
  }

  private static final class ContextCapturingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> taskKinds = new ArrayList<>();
    private final List<JsonNode> packets = new ArrayList<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode packet = canonicalJson.parseCanonical(request.untrustedInputJson());
      packets.add(packet);
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(activity(packet, request.taskKind())),
          new ModelRuntimeIdentityV1("scripted", "direct-context", "none", "none"));
    }

    List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    List<JsonNode> packets() {
      return List.copyOf(packets);
    }

    static void assertCleanDirectContext(JsonNode packet) {
      assertThat(packet.path("allowlistedRefs")).hasSizeBetween(2, 8);
      assertThat(packet.toString())
          .doesNotContain("file", "startLine", "endLine", "sha", "proof", "flow");
      assertThat(packet.path("allowlistedRefs"))
          .anySatisfy(
              value -> assertThat(value.path("snippet").asText()).contains("orderService.approve"))
          .anySatisfy(
              value ->
                  assertThat(value.path("snippet").asText()).contains("approvalClient.record"));
    }

    private static ObjectNode activity(JsonNode packet, String taskKind) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = root.putArray("activities").addObject();
      activity.put("activityLocalId", "approve-order");
      activity.putArray("entryKeys").add("E1");
      activity.put("name", "处理订单审批");
      activity.put("businessPurpose", "根据状态请求执行订单审批处理。");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("订单").add("审批状态");
      activity.putArray("triggerOrInput").add("状态请求");
      activity.putArray("conditions").add("状态不能为空时才记录审批处理。");
      activity.putArray("activitySteps").add("接收状态请求").add("调用订单审批处理").add("记录审批处理");
      activity.putArray("codeDefinedResults").add("代码调用审批记录处理。");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("订单").add("审批");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      ArrayNode refs = activity.putArray("sourceRefs");
      packet.path("allowlistedRefs").forEach(value -> refs.add(value.path("ref").asText()));
      activity.putArray("questions").add("审批记录的外部效果是什么？");
      activity.putArray("scopeLimitations").add("静态源码不证明一次审批已经成功完成。");
      if ("ACTIVITY_REVIEW".equals(taskKind)) {
        root.putArray("unexplainedEntries");
      }
      return root;
    }
  }
}
