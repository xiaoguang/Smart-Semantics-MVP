package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/**
 * Guards the boundary between deterministic candidate recall and model-owned business-process
 * judgment.
 */
class ProcessMaterialRecallTest {

  @Test
  void offersActivitiesWithOnlyASharedTechnicalMaterialLocationToTheModelWithoutExposingThePath()
      throws Exception {
    RecordingProvider provider = new RecordingProvider();

    new ProcessExplainer(provider)
        .explain(
            request(
                disjointActivities(),
                materials(),
                new ProcessExplanationProfile(4, 2, 16_000, 12_000, 2, 16, 2_000)));

    assertThat(provider.taskKinds()).containsExactly("PROCESS_GROUP_DRAFT", "PROCESS_GROUP_REVIEW");
    assertThat(provider.groupInput().path("activities"))
        .extracting(value -> value.path("activityId").asText())
        .containsExactly("activity:record-receipt", "activity:submit-request");
    assertThat(provider.groupInput().path("recallReasons"))
        .extracting(JsonNode::asText)
        .contains("技术材料来自同一冻结源码文件");
    assertThat(provider.groupInput().path("activities").get(0).properties())
        .extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder(
            "activityId",
            "name",
            "businessPurpose",
            "participants",
            "businessObjects",
            "triggerOrInput",
            "conditions",
            "activitySteps",
            "codeDefinedResults",
            "businessRules",
            "formulasOrMetrics",
            "terms",
            "certainty",
            "questions",
            "scopeLimitations",
            "sourceRefs");
    assertThat(provider.groupInput().path("activities").get(0).path("participants"))
        .extracting(JsonNode::asText)
        .containsExactly("经办人员");
    assertThat(provider.groupInput().path("activities").get(0).path("terms"))
        .extracting(JsonNode::asText)
        .containsExactly("术语：收货记录");
    assertThat(provider.groupInput().path("activities").get(0).path("certainty").asText())
        .isEqualTo("REASONABLE_INFERENCE");
    assertThat(provider.groupInput().path("activities").get(0).path("scopeLimitations"))
        .extracting(JsonNode::asText)
        .containsExactly("静态源码不证明某次保存成功");
    assertThat(provider.groupInput().toString())
        .doesNotContain("ReplenishmentWorkflow.java", "src/main/java", "proof:", "flow:");
  }

  private static ExplainRepositoryProcessesRequest request(
      ActivityExplanationResult activities,
      BusinessMaterialSet materials,
      ProcessExplanationProfile profile)
      throws Exception {
    try {
      Constructor<ExplainRepositoryProcessesRequest> constructor =
          ExplainRepositoryProcessesRequest.class.getConstructor(
              ActivityExplanationResult.class,
              BusinessMaterialSet.class,
              ProcessExplanationProfile.class);
      return constructor.newInstance(activities, materials, profile);
    } catch (NoSuchMethodException missing) {
      fail("PROCESS_MATERIAL_RECALL_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static ActivityExplanationResult disjointActivities() {
    List<ReviewedActivity> activities =
        List.of(
            activity(
                "activity:submit-request",
                "material:submit-request",
                "entry:submit-request",
                "提交采购申请",
                "登记采购申请。",
                "采购申请",
                "申请明细"),
            activity(
                "activity:record-receipt",
                "material:record-receipt",
                "entry:record-receipt",
                "记录收货",
                "保存收货信息。",
                "收货记录",
                "收货数量"));
    return new ActivityExplanationResult(
        activities,
        activities.stream()
            .map(
                activity ->
                    new ActivityEntryCoverage(
                        activity.entryIds().get(0),
                        "ANALYZED",
                        List.of(activity.activityId()),
                        null))
            .toList());
  }

  private static ReviewedActivity activity(
      String activityId,
      String materialId,
      String entryId,
      String name,
      String purpose,
      String object,
      String input) {
    return new ReviewedActivity(
        activityId,
        materialId,
        List.of(entryId),
        name,
        purpose,
        List.of("经办人员"),
        List.of(object),
        List.of(input),
        List.of(),
        List.of(name),
        List.of("系统保存" + object),
        List.of(),
        List.of(),
        List.of("术语：" + object),
        "REASONABLE_INFERENCE",
        List.of("S" + activityId.substring("activity:".length(), "activity:".length() + 1)),
        List.of(),
        List.of("静态源码不证明某次保存成功"));
  }

  private static BusinessMaterialSet materials() {
    BusinessMaterial submit =
        material("material:submit-request", "entry:submit-request", "Ssubmit");
    BusinessMaterial receipt =
        material("material:record-receipt", "entry:record-receipt", "Sreceipt");
    return new BusinessMaterialSet(
        "business-material-set:technical-recall",
        List.of(submit, receipt),
        List.of(
            new BusinessMaterialEntryCoverage(
                "entry:submit-request", "ANALYZED_MATERIAL", submit.materialId(), null),
            new BusinessMaterialEntryCoverage(
                "entry:record-receipt", "ANALYZED_MATERIAL", receipt.materialId(), null)));
  }

  private static BusinessMaterial material(String materialId, String entryId, String ref) {
    SourceReference source =
        new SourceReference(
            ref,
            "src/main/java/example/ReplenishmentWorkflow.java",
            "Ssubmit".equals(ref) ? 10 : 40,
            "Ssubmit".equals(ref) ? 18 : 48,
            "void handler() {}");
    return new BusinessMaterial(
        materialId,
        List.of(entryId),
        BusinessMaterialMode.ENTRY_SOURCE_FALLBACK,
        "已发现入口。",
        List.of("已定位入口处理方法。"),
        List.of(source),
        List.of(),
        List.of(),
        List.of("静态源码限制。"),
        new ModelActivityPacket(
            "已发现入口。",
            List.of("已定位入口处理方法。"),
            List.of(new ModelActivityPacket.AllowlistedReference(ref, source.snippet())),
            List.of("静态源码限制。")));
  }

  private static final class RecordingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> taskKinds = new ArrayList<>();
    private JsonNode groupInput;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      if ("PROCESS_GROUP_DRAFT".equals(request.taskKind())) {
        groupInput = input;
      }
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode process = response.putArray("processes").addObject();
      process.put("processLocalId", "candidate-only");
      process.put("name", "待确认业务过程");
      process.put("businessPurpose", "需要结合业务语义确认活动关系。 ");
      ArrayNode activityIds = process.putArray("activityIds");
      input.path("activities").forEach(value -> activityIds.add(value.path("activityId").asText()));
      ArrayNode stages = process.putArray("stages");
      int order = 1;
      for (JsonNode activity : input.path("activities")) {
        stages
            .addObject()
            .put("order", order++)
            .put("activityId", activity.path("activityId").asText())
            .put("description", activity.path("name").asText());
      }
      process.putArray("branches");
      process.putArray("sharedObjects");
      process.putArray("codeDefinedResults");
      process.put("certainty", "NEEDS_CONFIRMATION");
      ArrayNode refs = process.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(value -> refs.add(value.asText()));
      process.putArray("confirmationNotes").add("共同技术位置不证明业务关系。");
      response.putArray("unmatchedActivityIds");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private JsonNode groupInput() {
      return groupInput;
    }
  }
}
