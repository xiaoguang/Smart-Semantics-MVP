package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

/** Public-seam RED for model-reviewed reconstruction of one cross-entry business process. */
class ProcessExplainerTest {

  @Test
  void letsTheModelConnectThreeCompleteActivitiesWithoutJavaApprovingTheirOrder() throws Exception {
    ActivityExplanationResult activities =
        new ActivityExplanationResult(
            List.of(createReplenishment(), recordReceipt(), createPayableBill()),
            List.of(
                coverage("entry:create-replenishment"),
                coverage("entry:record-receipt"),
                coverage("entry:create-payable-bill")));
    Class<?> explainerType =
        requireType("org.sourceanalysis.app.analysis.knowledge.ProcessExplainer");
    Class<?> profileType =
        requireType("org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile");
    Class<?> requestType =
        requireType("org.sourceanalysis.app.analysis.knowledge.ExplainRepositoryProcessesRequest");
    ScriptedProcessProvider provider = new ScriptedProcessProvider();
    Object explainer =
        explainerType.getConstructor(StructuredModelProvider.class).newInstance(provider);
    Object profile =
        profileType
            .getConstructor(
                int.class, int.class, int.class, int.class, int.class, int.class, int.class)
            .newInstance(6, 4, 16_000, 12_000, 4, 32, 2_000);
    Object request =
        requestType
            .getConstructor(ActivityExplanationResult.class, profileType)
            .newInstance(activities, profile);
    Object knowledge = invoke(explainerType, explainer, request);

    assertThat(provider.taskKinds()).containsExactly("PROCESS_GROUP_DRAFT", "PROCESS_GROUP_REVIEW");
    assertThat(provider.outputSchemas())
        .hasSize(2)
        .allSatisfy(ProcessExplainerTest::assertProcessGroupSchema);
    assertThat(provider.firstGroupInput().path("activities")).hasSize(3);
    assertThat(provider.firstGroupInput().toString())
        .contains("补货明细集合不能为空", "应付金额 = 收货数量 × 单价")
        .doesNotContain(".java", "sha256", "proof:", "flow:");
    assertThat(provider.reviewActualDraft()).isEqualTo(provider.draft());

    Object processes = knowledge.getClass().getMethod("processes").invoke(knowledge);
    assertThat((List<?>) processes).hasSize(1);
    Object process = ((List<?>) processes).get(0);
    assertThat(process.getClass().getMethod("name").invoke(process)).isEqualTo("补货到应付账单形成");
    assertThat(strings(process, "activityIds"))
        .containsExactly(
            "activity:create-replenishment",
            "activity:record-receipt",
            "activity:create-payable-bill");
    assertThat(strings(process, "confirmationNotes")).contains("组织制度是否强制三个入口依次执行仍待确认");
  }

  private static Class<?> requireType(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException missing) {
      fail("PROCESS_EXPLAINER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Object invoke(Class<?> type, Object explainer, Object request) throws Exception {
    Method explain = type.getMethod("explain", request.getClass());
    try {
      return explain.invoke(explainer, request);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(failure.getCause());
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> strings(Object value, String accessor) throws Exception {
    return (List<String>) value.getClass().getMethod(accessor).invoke(value);
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
        List.of("补货单标识", "收货数量", "单价"),
        List.of(),
        List.of("读取收货数据", "生成收货记录", "保存收货记录"),
        List.of("系统生成并保存收货记录"),
        List.of(),
        List.of(),
        List.of("S2", "S3"));
  }

  private static ReviewedActivity createPayableBill() {
    return activity(
        "activity:create-payable-bill",
        "entry:create-payable-bill",
        "创建应付账单",
        "按收货记录形成并保存应付账单。",
        List.of("收货记录", "应付账单"),
        List.of("收货记录标识"),
        List.of(),
        List.of("读取收货记录", "计算金额", "保存应付账单"),
        List.of("系统计算金额并生成及保存应付账单"),
        List.of(),
        List.of("应付金额 = 收货数量 × 单价"),
        List.of("S3", "S4"));
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

  private static void assertProcessGroupSchema(JsonNode schema) {
    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(textValues(schema.path("required")))
        .containsExactly("processes", "unmatchedActivityIds");
    JsonNode process = schema.path("properties").path("processes").path("items");
    assertThat(process.path("type").asText()).isEqualTo("object");
    assertThat(process.path("additionalProperties").asBoolean()).isFalse();
    assertThat(textValues(process.path("required")))
        .containsExactly(
            "processLocalId",
            "name",
            "businessPurpose",
            "activityIds",
            "stages",
            "branches",
            "sharedObjects",
            "codeDefinedResults",
            "certainty",
            "sourceRefs",
            "confirmationNotes");
    JsonNode stage = process.path("properties").path("stages").path("items");
    assertThat(stage.path("type").asText()).isEqualTo("object");
    assertThat(textValues(stage.path("required")))
        .containsExactly("order", "activityId", "description");
  }

  private static List<String> textValues(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static final class ScriptedProcessProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> taskKinds = new ArrayList<>();
    private JsonNode firstGroupInput;
    private JsonNode reviewActualDraft;
    private JsonNode draft;
    private final List<JsonNode> outputSchemas = new ArrayList<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      outputSchemas.add(canonicalJson.parseCanonical(request.outputJsonSchema()));
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      if ("PROCESS_GROUP_DRAFT".equals(request.taskKind())) {
        firstGroupInput = input;
      } else if ("PROCESS_GROUP_REVIEW".equals(request.taskKind())) {
        reviewActualDraft = input.path("actualDraft");
      }
      JsonNode response = processResponse();
      if ("PROCESS_GROUP_DRAFT".equals(request.taskKind())) {
        draft = response;
      }
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private JsonNode processResponse() {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ArrayNode processes = root.putArray("processes");
      ObjectNode process = processes.addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "补货到应付账单形成");
      process.put("businessPurpose", "从补货明细形成补货单，在记录收货后形成应付账单。");
      process
          .putArray("activityIds")
          .add("activity:create-replenishment")
          .add("activity:record-receipt")
          .add("activity:create-payable-bill");
      stages(process.putArray("stages"));
      process.putArray("branches");
      process.putArray("sharedObjects").add("补货单").add("收货记录").add("应付账单");
      process.putArray("codeDefinedResults").add("系统可形成补货单、收货记录和应付账单");
      process.put("certainty", "REASONABLE_INFERENCE");
      process.putArray("sourceRefs").add("S1").add("S2").add("S3").add("S4");
      process.putArray("confirmationNotes").add("组织制度是否强制三个入口依次执行仍待确认");
      root.putArray("unmatchedActivityIds");
      return root;
    }

    private static void stages(ArrayNode stages) {
      stage(stages, 1, "activity:create-replenishment", "生成并保存补货单");
      stage(stages, 2, "activity:record-receipt", "记录关联收货");
      stage(stages, 3, "activity:create-payable-bill", "形成应付账单");
    }

    private static void stage(ArrayNode stages, int order, String activityId, String description) {
      stages
          .addObject()
          .put("order", order)
          .put("activityId", activityId)
          .put("description", description);
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private JsonNode firstGroupInput() {
      return firstGroupInput;
    }

    private JsonNode reviewActualDraft() {
      return reviewActualDraft;
    }

    private JsonNode draft() {
      return draft;
    }

    private List<JsonNode> outputSchemas() {
      return List.copyOf(outputSchemas);
    }
  }
}
