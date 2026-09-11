package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.testsupport.BusinessFlowTestSupport;

/** Proves that a persisted Step 05 publication is enough to reach a durable business report. */
class PersistedBusinessRunExecutorTest {

  @TempDir Path temporaryDirectory;

  @Test
  void turnsOnePersistedFlowPublicationIntoDurableBusinessCheckpointsAndNineSections() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("persisted-business-run"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
      ScriptedBusinessProvider provider = new ScriptedBusinessProvider();
      PersistedBusinessRunExecutor executor =
          new PersistedBusinessRunExecutor(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              fixture.sourceReader(),
              provider,
              new PersistedBusinessRunConfiguration(
                  new BusinessMaterialProfile(8, 24, 12_000),
                  new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
                  new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
                  new BusinessReportProfile(64_000, 16_000, 32, 2_000)));

      BusinessAnalysisWorkflowResult result = executor.execute(flows);

      assertThat(result.materials().checkpoint()).isNotNull();
      assertThat(result.activities().checkpoint()).isNotNull();
      assertThat(result.knowledge().checkpoint()).isNotNull();
      assertThat(result.report().checkpoint()).isNotNull();
      assertThat(result.report().documentMarkdown())
          .contains(
              "## 1. 文档说明",
              "## 4. 业务活动",
              "## 9. 待确认事项",
              "审阅后的业务目的：根据入口提交的数据执行业务处理。",
              "入口数据为空时不进入更新",
              "只有通过入口校验的数据才传给更新边界",
              "输入不合法时返回什么？");
      assertThat(
              result
                  .report()
                  .documentMarkdown()
                  .lines()
                  .filter(line -> line.startsWith("## "))
                  .count())
          .isEqualTo(9);
      assertThat(provider.taskKinds())
          .containsExactly(
              "ACTIVITY_DRAFT",
              "ACTIVITY_REVIEW",
              "PROCESS_GROUP_DRAFT",
              "PROCESS_GROUP_REVIEW",
              "BUSINESS_REPORT_DRAFT",
              "BUSINESS_REPORT_REVIEW");
      assertThat(provider.activityReviewActualDraft()).isEqualTo(provider.activityDraft());
      assertThat(provider.processReviewActualDraft()).isEqualTo(provider.processDraft());
      assertThat(provider.processDraftInput().path("activities").get(0).path("conditions"))
          .extracting(JsonNode::asText)
          .containsExactly("入口数据为空时不进入更新");
      assertThat(provider.processDraftInput().path("activities").get(0).path("businessRules"))
          .extracting(JsonNode::asText)
          .containsExactly("只有通过入口校验的数据才传给更新边界");
      assertThat(provider.processDraftInput().path("activities").get(0).path("scopeLimitations"))
          .extracting(JsonNode::asText)
          .containsExactly("静态源码不证明某次更新成功");
      assertThat(provider.reportReviewActualDraft()).isEqualTo(provider.reportDraft());
      assertThat(provider.reportDraftInput().path("activities").get(0).path("conditions"))
          .extracting(JsonNode::asText)
          .containsExactly("入口数据为空时不进入更新");
      assertThat(provider.reportDraftInput().path("activities").get(0).path("businessRules"))
          .extracting(JsonNode::asText)
          .containsExactly("只有通过入口校验的数据才传给更新边界");
      assertThat(provider.reportDraftInput().path("activities").get(0).path("questions"))
          .extracting(JsonNode::asText)
          .containsExactly("输入不合法时返回什么？");
    }
  }

  @Test
  void rejectsAZeroActivityLimitForTheFinalDocumentWorkflow() {
    assertThatThrownBy(
            () ->
                new PersistedBusinessRunConfiguration(
                    new BusinessMaterialProfile(8, 24, 12_000),
                    new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
                    new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
                    new BusinessReportProfile(64_000, 16_000, 32, 2_000),
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maximum materials to start must be positive for a final document run");
  }

  private static PersistedBusinessRunConfiguration configurationWithActivityLaunchLimit(
      int maxMaterialsToStart) {
    try {
      return PersistedBusinessRunConfiguration.class
          .getConstructor(
              BusinessMaterialProfile.class,
              ActivityExplanationProfile.class,
              ProcessExplanationProfile.class,
              BusinessReportProfile.class,
              int.class)
          .newInstance(
              new BusinessMaterialProfile(8, 24, 12_000),
              new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
              new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
              new BusinessReportProfile(64_000, 16_000, 32, 2_000),
              maxMaterialsToStart);
    } catch (ReflectiveOperationException missing) {
      fail("PERSISTED_ACTIVITY_LAUNCH_LIMIT_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  static final class ScriptedBusinessProvider implements StructuredModelProvider {
    private static final List<String> SECTION_TITLES =
        List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");

    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> taskKinds = new java.util.ArrayList<>();
    private JsonNode activityDraft;
    private JsonNode activityReviewActualDraft;
    private JsonNode processDraft;
    private JsonNode processDraftInput;
    private JsonNode processReviewActualDraft;
    private JsonNode reportDraft;
    private JsonNode reportDraftInput;
    private JsonNode reportReviewActualDraft;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode response =
          switch (request.taskKind()) {
            case "ACTIVITY_DRAFT" -> {
              JsonNode draft = activity(input, false);
              activityDraft = draft;
              yield draft;
            }
            case "ACTIVITY_REVIEW" -> {
              activityReviewActualDraft = input.path("actualDraft");
              yield activity(input, true);
            }
            case "PROCESS_GROUP_DRAFT" -> {
              processDraftInput = input;
              JsonNode draft = process(input);
              processDraft = draft;
              yield draft;
            }
            case "PROCESS_GROUP_REVIEW" -> {
              processReviewActualDraft = input.path("actualDraft");
              yield process(input);
            }
            case "BUSINESS_REPORT_DRAFT" -> {
              reportDraftInput = input;
              JsonNode draft = report(input);
              reportDraft = draft;
              yield draft;
            }
            case "BUSINESS_REPORT_REVIEW" -> {
              reportReviewActualDraft = input.path("actualDraft");
              yield report(input);
            }
            default ->
                throw new IllegalArgumentException(
                    "unexpected scripted task " + request.taskKind());
          };
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "business-runtime", "none", "none"));
    }

    private static JsonNode activity(JsonNode input, boolean reviewed) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      List<String> entryKeys = new java.util.ArrayList<>();
      input.path("entryKeys").forEach(value -> entryKeys.add(value.asText()));
      int activityCount = Math.min(2, entryKeys.size());
      ArrayNode activities = response.putArray("activities");
      for (int index = 0; index < activityCount; index++) {
        int start = index * entryKeys.size() / activityCount;
        int end = (index + 1) * entryKeys.size() / activityCount;
        addActivity(
            activities.addObject(),
            "activity-" + (index + 1),
            entryKeys.subList(start, end),
            input,
            reviewed);
      }
      if (reviewed) {
        response.putArray("unexplainedEntries");
      }
      return response;
    }

    private static void addActivity(
        ObjectNode activity,
        String localId,
        List<String> entryKeys,
        JsonNode input,
        boolean reviewed) {
      activity.put("activityLocalId", localId);
      ArrayNode activityEntryKeys = activity.putArray("entryKeys");
      entryKeys.forEach(activityEntryKeys::add);
      activity.put("name", "处理业务请求");
      activity.put("businessPurpose", reviewed ? "根据入口提交的数据执行业务处理。" : "草稿业务目的。");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("业务记录");
      activity.putArray("triggerOrInput").add("入口提交的数据");
      activity.putArray("conditions").add("入口数据为空时不进入更新");
      activity.putArray("activitySteps").add("校验输入").add("更新业务记录");
      activity.putArray("codeDefinedResults").add("系统调用可见的数据更新边界");
      activity.putArray("businessRules").add("只有通过入口校验的数据才传给更新边界");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("业务记录");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      ArrayNode refs = activity.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.path("ref").asText()));
      activity.putArray("questions").add("输入不合法时返回什么？");
      activity.putArray("scopeLimitations").add("静态源码不证明某次更新成功");
    }

    private static JsonNode process(JsonNode input) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode process = response.putArray("processes").addObject();
      process.put("processLocalId", "process-1");
      process.put("name", "业务请求处理过程");
      process.put("businessPurpose", "处理入口提交的业务请求。");
      ArrayNode activityIds = process.putArray("activityIds");
      ArrayNode stages = process.putArray("stages");
      int order = 1;
      for (JsonNode activity : input.path("activities")) {
        activityIds.add(activity.path("activityId").asText());
        stages
            .addObject()
            .put("order", order++)
            .put("activityId", activity.path("activityId").asText())
            .put("description", activity.path("name").asText());
      }
      process.putArray("branches");
      process.putArray("sharedObjects").add("业务记录");
      process.putArray("codeDefinedResults").add("系统执行可见的数据更新边界");
      process.put("certainty", "REASONABLE_INFERENCE");
      ArrayNode refs = process.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.asText()));
      process.putArray("confirmationNotes").add("跨入口的制度顺序仍待确认");
      response.putArray("unmatchedActivityIds");
      return response;
    }

    private static JsonNode report(JsonNode input) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      response.put("title", "合成业务仓库说明");
      String ref =
          input.path("allowlistedRefs").isEmpty()
              ? null
              : input.path("allowlistedRefs").get(0).asText();
      ArrayNode sections = response.putArray("sections");
      for (int index = 0; index < SECTION_TITLES.size(); index++) {
        ObjectNode section = sections.addObject();
        section.put("number", index + 1);
        section.put("title", SECTION_TITLES.get(index));
        ObjectNode paragraph = section.putArray("paragraphs").addObject();
        paragraph.put(
            "text",
            index == 3
                ? "审阅后的业务目的："
                    + input.path("activities").get(0).path("businessPurpose").asText()
                    + " 条件："
                    + input.path("activities").get(0).path("conditions").get(0).asText()
                    + "。规则："
                    + input.path("activities").get(0).path("businessRules").get(0).asText()
                    + "。问题："
                    + input.path("activities").get(0).path("questions").get(0).asText()
                : "本章根据已审阅的业务活动说明冻结源码定义的系统行为。");
        ArrayNode refs = paragraph.putArray("refs");
        if (ref != null) {
          refs.add(ref);
        }
        section.putArray("items");
      }
      return response;
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private JsonNode activityDraft() {
      return activityDraft;
    }

    private JsonNode activityReviewActualDraft() {
      return activityReviewActualDraft;
    }

    private JsonNode reportDraft() {
      return reportDraft;
    }

    private JsonNode reportDraftInput() {
      return reportDraftInput;
    }

    private JsonNode reportReviewActualDraft() {
      return reportReviewActualDraft;
    }

    private JsonNode processDraft() {
      return processDraft;
    }

    private JsonNode processDraftInput() {
      return processDraftInput;
    }

    private JsonNode processReviewActualDraft() {
      return processReviewActualDraft;
    }
  }
}
