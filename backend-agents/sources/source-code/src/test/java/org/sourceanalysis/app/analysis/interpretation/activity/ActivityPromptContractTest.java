package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/**
 * Guards the Chinese task instructions that make Luna explain business rather than method names.
 */
class ActivityPromptContractTest {

  @TempDir Path temporaryDirectory;

  @Test
  void sendsDistinctChineseBusinessInstructionsForDraftAndReview() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-prompts"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      BusinessMaterialBuildResult allMaterials =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000, 1)));
      BusinessMaterial material = allMaterials.materialSet().materials().get(0);
      BusinessMaterialEntryCoverage entry =
          allMaterials.materialSet().entryCoverage().stream()
              .filter(value -> material.materialId().equals(value.materialId()))
              .findFirst()
              .orElseThrow();
      BusinessMaterialBuildResult oneMaterial =
          new BusinessMaterialBuildResult(
              new BusinessMaterialSet(
                  allMaterials.materialSet().materialSetId(), List.of(material), List.of(entry)),
              allMaterials.checkpoint());
      RecordingProvider provider = new RecordingProvider();

      new ActivityExplainer(provider)
          .explain(
              new ExplainActivitiesRequest(
                  oneMaterial, new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000)));

      assertThat(provider.instructions())
          .hasSize(2)
          .allSatisfy(
              instruction ->
                  assertThat(instruction)
                      .contains("你正在阅读一个冻结源码分析程序准备的业务材料包")
                      .contains("不得创建、修改或猜测 ref"));
      assertThat(provider.instructions().get(0)).contains("请把整个材料包解释为零个或多个完整局部业务活动");
      assertThat(provider.instructions().get(1)).contains("请审阅这些完整活动是否真正回答业务问题");
      assertThat(provider.instructions())
          .allSatisfy(
              instruction ->
                  assertThat(instruction).contains("完整 HTTP 方法与路径").contains("Java 类型、变量名或技术层名"));
      assertThat(provider.instructions().get(0)).isNotEqualTo(provider.instructions().get(1));
    }
  }

  private static final class RecordingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> instructions = new ArrayList<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      instructions.add(request.systemInstructions());
      JsonNode packet = canonicalJson.parseCanonical(request.untrustedInputJson());
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = response.putArray("activities").addObject();
      activity.put("activityLocalId", "activity-1");
      activity.putArray("entryKeys").add("E1");
      activity.put("name", "保存业务对象");
      activity.put("businessPurpose", "把入口提交的数据整理为业务对象并保存。");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("业务对象");
      activity.putArray("triggerOrInput").add("入口提交的数据");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("读取输入").add("保存业务对象");
      activity.putArray("codeDefinedResults").add("系统生成并保存业务对象");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("业务对象");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      var refs = activity.putArray("sourceRefs");
      packet.path("allowlistedRefs").forEach(ref -> refs.add(ref.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次保存成功");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private List<String> instructions() {
      return List.copyOf(instructions);
    }
  }
}
