package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Ensures the process DRAFT and REVIEW instructions carry their distinct Chinese contracts. */
class ProcessPromptContractTest {

  @Test
  void sendsDistinctChineseInstructionsForProcessDraftAndWholeProcessReview() {
    RecordingProvider provider = new RecordingProvider();

    new ProcessExplainer(provider)
        .explain(
            new ExplainRepositoryProcessesRequest(
                activities(), new ProcessExplanationProfile(4, 2, 16_000, 12_000, 2, 16, 2_000)));

    assertThat(provider.instructions())
        .hasSize(2)
        .allSatisfy(
            instruction -> assertThat(instruction).contains("不是顺序或因果证明").contains("不得输出源码路径"));
    assertThat(provider.instructions().get(0)).contains("提出零个或多个完整过程").doesNotContain("返回完整修订");
    assertThat(provider.instructions().get(1)).contains("审阅完整过程").contains("返回完整修订 JSON");
    assertThat(provider.instructions().get(0)).isNotEqualTo(provider.instructions().get(1));
  }

  private static ActivityExplanationResult activities() {
    List<ReviewedActivity> activities =
        List.of(
            activity("activity:create-order", "entry:create-order", "创建补货单", "补货单", "S1"),
            activity("activity:record-receipt", "entry:record-receipt", "记录收货", "补货单", "S2"));
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
      String activityId, String entryId, String name, String object, String ref) {
    return new ReviewedActivity(
        activityId,
        "material:" + activityId,
        List.of(entryId),
        name,
        "处理" + object + "。",
        List.of(),
        List.of(object),
        List.of(object + "标识"),
        List.of(),
        List.of("处理" + object),
        List.of("系统保存" + object),
        List.of(),
        List.of(),
        List.of(object),
        "DIRECT_CODE_BEHAVIOR",
        List.of(ref),
        List.of(),
        List.of("静态源码不证明某次保存成功"));
  }

  private static final class RecordingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> instructions = new ArrayList<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      instructions.add(request.systemInstructions());
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode process = response.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "补货处理");
      process.put("businessPurpose", "处理补货单并记录结果。");
      ArrayNode activityIds = process.putArray("activityIds");
      input
          .path("activities")
          .forEach(activity -> activityIds.add(activity.path("activityId").asText()));
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
      process.putArray("sharedObjects").add("补货单");
      process.putArray("codeDefinedResults").add("系统保存补货单");
      process.put("certainty", "REASONABLE_INFERENCE");
      ArrayNode refs = process.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.asText()));
      process.putArray("confirmationNotes").add("组织制度是否要求按该顺序执行仍待确认");
      response.putArray("unmatchedActivityIds");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private List<String> instructions() {
      return List.copyOf(instructions);
    }
  }
}
