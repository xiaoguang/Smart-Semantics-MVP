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

/** Ensures large recall components use bounded overlap instead of severing a possible handoff. */
class ProcessGroupingTest {

  @Test
  void carriesBoundaryActivitiesIntoTheNextBoundedGroupWithoutApprovingAProcessOrder() {
    GroupRecordingProvider provider = new GroupRecordingProvider();
    RepositoryBusinessKnowledge knowledge =
        new ProcessExplainer(provider)
            .explain(
                new ExplainRepositoryProcessesRequest(
                    activities(),
                    new ProcessExplanationProfile(2, 3, 16_000, 12_000, 2, 16, 2_000)));

    assertThat(provider.draftGroups())
        .containsExactly(
            List.of("activity:a", "activity:b"),
            List.of("activity:b", "activity:c"),
            List.of("activity:c", "activity:d"));
    assertThat(provider.reviewCount()).isEqualTo(3);
    assertThat(knowledge.unmatchedActivityIds()).isEmpty();
  }

  private static ActivityExplanationResult activities() {
    List<ReviewedActivity> values =
        List.of(activity("a"), activity("b"), activity("c"), activity("d"));
    return new ActivityExplanationResult(
        values,
        values.stream()
            .map(
                activity ->
                    new ActivityEntryCoverage(
                        activity.entryIds().get(0),
                        "ANALYZED",
                        List.of(activity.activityId()),
                        null))
            .toList());
  }

  private static ReviewedActivity activity(String suffix) {
    return new ReviewedActivity(
        "activity:" + suffix,
        "material:" + suffix,
        List.of("entry:" + suffix),
        "处理订单" + suffix,
        "处理订单。",
        List.of(),
        List.of("订单"),
        List.of("订单标识"),
        List.of(),
        List.of("处理订单"),
        List.of("系统保存订单"),
        List.of(),
        List.of(),
        List.of("订单"),
        "DIRECT_CODE_BEHAVIOR",
        List.of("S" + suffix),
        List.of(),
        List.of("静态源码不证明某次保存成功"));
  }

  private static final class GroupRecordingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<List<String>> draftGroups = new ArrayList<>();
    private int reviewCount;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      if ("PROCESS_GROUP_DRAFT".equals(request.taskKind())) {
        draftGroups.add(activityIds(input));
      } else {
        reviewCount++;
      }
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode process = response.putArray("processes").addObject();
      process.put("processLocalId", "process-" + draftGroups.size());
      process.put("name", "订单处理");
      process.put("businessPurpose", "处理订单。 ");
      ArrayNode activityIds = process.putArray("activityIds");
      activityIds(input).forEach(activityIds::add);
      ArrayNode stages = process.putArray("stages");
      int order = 1;
      for (String activityId : activityIds(input)) {
        stages
            .addObject()
            .put("order", order++)
            .put("activityId", activityId)
            .put("description", "处理订单");
      }
      process.putArray("branches");
      process.putArray("sharedObjects").add("订单");
      process.putArray("codeDefinedResults").add("系统保存订单");
      process.put("certainty", "REASONABLE_INFERENCE");
      ArrayNode refs = process.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(value -> refs.add(value.asText()));
      process.putArray("confirmationNotes").add("制度顺序待确认");
      response.putArray("unmatchedActivityIds");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private static List<String> activityIds(JsonNode input) {
      List<String> values = new ArrayList<>();
      input
          .path("activities")
          .forEach(activity -> values.add(activity.path("activityId").asText()));
      return List.copyOf(values);
    }

    private List<List<String>> draftGroups() {
      return List.copyOf(draftGroups);
    }

    private int reviewCount() {
      return reviewCount;
    }
  }
}
