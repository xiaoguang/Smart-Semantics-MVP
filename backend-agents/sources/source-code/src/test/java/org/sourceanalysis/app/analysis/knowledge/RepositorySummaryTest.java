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
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Public-seam contract for the one bounded repository summary after group process reviews. */
class RepositorySummaryTest {

  @Test
  void reviewsOneCleanRepositorySummaryWithoutReplacingTheCompleteActivitiesAndProcesses()
      throws Exception {
    ActivityExplanationResult activities =
        new ActivityExplanationResult(
            List.of(createReplenishment(), recordReceipt()),
            List.of(coverage("entry:create-replenishment"), coverage("entry:record-receipt")));
    RepositorySummaryProvider provider = new RepositorySummaryProvider();
    ProcessExplainer explainer = new ProcessExplainer(provider);

    RepositoryBusinessKnowledge knowledge =
        explainer.explain(
            new ExplainRepositoryProcessesRequest(activities, repositorySummaryProfile()));

    assertThat(provider.taskKinds())
        .containsExactly(
            "PROCESS_GROUP_DRAFT",
            "PROCESS_GROUP_REVIEW",
            "REPOSITORY_SUMMARY_DRAFT",
            "REPOSITORY_SUMMARY_REVIEW");
    assertThat(provider.outputSchemas())
        .hasSize(4)
        .extracting(schema -> schema.path("required"))
        .allSatisfy(
            required -> {
              assertThat(required.isArray()).isTrue();
            });
    provider
        .outputSchemas()
        .subList(2, 4)
        .forEach(RepositorySummaryTest::assertRepositorySummarySchema);
    assertThat(provider.repositoryDraftInput().toString())
        .contains("补货明细集合不能为空", "没有补货明细时不进入补货单生成", "收货记录")
        .doesNotContain(".java", "sha256", "proof:", "flow:", "src/main");
    assertThat(provider.repositoryReviewActualDraft()).isEqualTo(provider.repositoryDraft());
    assertThat(knowledge.activities()).hasSize(2);
    assertThat(knowledge.processes()).hasSize(1);

    Object summary = repositorySummary(knowledge);
    assertThat((String) summary.getClass().getMethod("text").invoke(summary))
        .isEqualTo("仓库支持从补货单形成到收货记录登记的业务活动。");
    @SuppressWarnings("unchecked")
    List<String> goals =
        (List<String>) summary.getClass().getMethod("businessGoals").invoke(summary);
    assertThat(goals).containsExactly("形成并保存补货单", "登记关联收货记录");
  }

  private static ProcessExplanationProfile repositorySummaryProfile() throws Exception {
    try {
      Constructor<ProcessExplanationProfile> constructor =
          ProcessExplanationProfile.class.getConstructor(
              int.class, int.class, int.class, int.class, int.class, int.class, int.class,
              int.class);
      return constructor.newInstance(4, 2, 16_000, 12_000, 2, 16, 2_000, 10);
    } catch (NoSuchMethodException missing) {
      fail("REPOSITORY_SUMMARY_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Object repositorySummary(RepositoryBusinessKnowledge knowledge) throws Exception {
    try {
      return knowledge.getClass().getMethod("repositorySummary").invoke(knowledge);
    } catch (NoSuchMethodException missing) {
      fail("REPOSITORY_SUMMARY_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static void assertRepositorySummarySchema(JsonNode schema) {
    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(textValues(schema.path("required")))
        .containsExactly(
            "text", "businessGoals", "objectsAndRelations", "confirmationTopics", "sourceRefs");
    JsonNode refs = schema.path("properties").path("sourceRefs").path("items");
    assertThat(refs.path("type").asText()).isEqualTo("string");
    assertThat(textValues(refs.path("enum"))).containsExactly("S1", "S2", "S3");
  }

  private static List<String> textValues(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static ActivityEntryCoverage coverage(String entryId) {
    return new ActivityEntryCoverage(
        entryId, "ANALYZED", List.of("activity:" + entryId.substring(6)), null);
  }

  private static ReviewedActivity createReplenishment() {
    return activity(
        "activity:create-replenishment",
        "entry:create-replenishment",
        "创建补货单",
        "把补货明细形成并保存为补货单。",
        List.of("补货单", "补货明细"),
        List.of("补货明细集合"),
        List.of("补货明细集合不能为空"),
        List.of("校验明细", "生成补货单", "保存补货单"),
        List.of("系统生成并保存补货单"),
        List.of("没有补货明细时不进入补货单生成"),
        List.of(),
        List.of("S1", "S2"));
  }

  private static ReviewedActivity recordReceipt() {
    return activity(
        "activity:record-receipt",
        "entry:record-receipt",
        "记录收货",
        "保存与补货单关联的收货记录。",
        List.of("补货单", "收货记录"),
        List.of("补货单标识", "收货数量"),
        List.of(),
        List.of("读取收货数据", "生成收货记录", "保存收货记录"),
        List.of("系统生成并保存收货记录"),
        List.of(),
        List.of(),
        List.of("S2", "S3"));
  }

  private static ReviewedActivity activity(
      String activityId,
      String entryId,
      String name,
      String purpose,
      List<String> objects,
      List<String> inputs,
      List<String> conditions,
      List<String> steps,
      List<String> results,
      List<String> rules,
      List<String> formulas,
      List<String> refs) {
    return new ReviewedActivity(
        activityId,
        "material:" + activityId.substring("activity:".length()),
        List.of(entryId),
        name,
        purpose,
        List.of(),
        objects,
        inputs,
        conditions,
        steps,
        results,
        rules,
        formulas,
        objects,
        "DIRECT_CODE_BEHAVIOR",
        refs,
        List.of(),
        List.of("静态源码不证明某次保存成功"));
  }

  private static final class RepositorySummaryProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> taskKinds = new ArrayList<>();
    private JsonNode repositoryDraftInput;
    private JsonNode repositoryReviewActualDraft;
    private JsonNode repositoryDraft;
    private final List<JsonNode> outputSchemas = new ArrayList<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      outputSchemas.add(canonicalJson.parseCanonical(request.outputJsonSchema()));
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode response =
          switch (request.taskKind()) {
            case "PROCESS_GROUP_DRAFT", "PROCESS_GROUP_REVIEW" -> groupResponse();
            case "REPOSITORY_SUMMARY_DRAFT" -> {
              repositoryDraftInput = input;
              repositoryDraft = repositorySummaryResponse();
              yield repositoryDraft;
            }
            case "REPOSITORY_SUMMARY_REVIEW" -> {
              repositoryReviewActualDraft = input.path("actualDraft");
              yield repositorySummaryResponse();
            }
            default -> throw new AssertionError("unexpected task: " + request.taskKind());
          };
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private JsonNode groupResponse() {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ObjectNode process = root.putArray("processes").addObject();
      process.put("processLocalId", "replenishment-receipt");
      process.put("name", "补货单形成与收货登记");
      process.put("businessPurpose", "形成补货单并登记关联收货记录。");
      process
          .putArray("activityIds")
          .add("activity:create-replenishment")
          .add("activity:record-receipt");
      stage(process.putArray("stages"), 1, "activity:create-replenishment", "生成并保存补货单");
      stage(process.putArray("stages"), 2, "activity:record-receipt", "登记关联收货记录");
      process.putArray("branches").add("补货明细为空时不生成补货单");
      process.putArray("sharedObjects").add("补货单").add("收货记录");
      process.putArray("codeDefinedResults").add("系统生成并保存补货单和收货记录");
      process.put("certainty", "REASONABLE_INFERENCE");
      process.putArray("sourceRefs").add("S1").add("S2").add("S3");
      process.putArray("confirmationNotes").add("组织制度是否强制先补货后收货仍待确认");
      root.putArray("unmatchedActivityIds");
      return root;
    }

    private JsonNode repositorySummaryResponse() {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.put("text", "仓库支持从补货单形成到收货记录登记的业务活动。");
      root.putArray("businessGoals").add("形成并保存补货单").add("登记关联收货记录");
      root.putArray("objectsAndRelations").add("收货记录关联补货单");
      root.putArray("confirmationTopics").add("组织制度是否强制先补货后收货仍待确认");
      root.putArray("sourceRefs").add("S1").add("S2").add("S3");
      return root;
    }

    private static void stage(ArrayNode values, int order, String activityId, String description) {
      values
          .addObject()
          .put("order", order)
          .put("activityId", activityId)
          .put("description", description);
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private JsonNode repositoryDraftInput() {
      return repositoryDraftInput;
    }

    private JsonNode repositoryReviewActualDraft() {
      return repositoryReviewActualDraft;
    }

    private JsonNode repositoryDraft() {
      return repositoryDraft;
    }

    private List<JsonNode> outputSchemas() {
      return List.copyOf(outputSchemas);
    }
  }
}
