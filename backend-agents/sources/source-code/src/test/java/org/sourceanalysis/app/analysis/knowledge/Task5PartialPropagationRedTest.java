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
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.document.BusinessReportPublisher;
import org.sourceanalysis.app.analysis.document.PublishBusinessReportRequest;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.activity.UnexplainedActivityEntry;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Task 5 Luna/xhigh RED for concrete partial coverage propagation. */
class Task5PartialPropagationRedTest {

  private static final String MATERIAL_ID = "material:user-entries";
  private static final String MATERIAL_CONTEXT =
      "入口上下文：POST /user/registerUser；GET /user/logout；完整 HTTP 方法与路径保留。";
  private static final String GLOBAL_E3 = "entry:global-register";
  private static final String GLOBAL_E4 = "entry:global-logout";

  @Test
  void processGroupsUnexplainedKeysOncePerMaterialAndRetainsGlobalRecordsProgramSide() {
    PartialProcessProvider provider = new PartialProcessProvider();
    RepositoryBusinessKnowledge knowledge =
        new ProcessExplainer(provider)
            .explain(
                new ExplainRepositoryProcessesRequest(
                    partialActivities(),
                    partialMaterialSet(),
                    new ProcessExplanationProfile(4, 2, 24_000, 16_000, 2, 32, 2_000, 1)));

    JsonNode input = provider.repositoryDraftInput();
    assertThat(input.path("unexplainedActivityEntries").isArray()).isTrue();
    assertThat(input.path("unexplainedActivityEntries")).hasSize(1);
    JsonNode aggregate = input.path("unexplainedActivityEntries").get(0);
    assertThat(fieldNames(aggregate))
        .containsExactlyInAnyOrder("materialContext", "unexplainedEntryKeys", "reasonCode");
    assertThat(aggregate.path("materialContext").asText()).isEqualTo(MATERIAL_CONTEXT);
    assertThat(textValues(aggregate.path("unexplainedEntryKeys"))).containsExactly("E3", "E4");
    assertThat(aggregate.path("reasonCode").asText()).isEqualTo("MODEL_NOT_EXPLAINED");
    assertThat(aggregate.toString()).doesNotContain(GLOBAL_E3, GLOBAL_E4, MATERIAL_ID);

    List<UnexplainedActivityEntry> records = invokeUnexplainedRecords(knowledge);
    assertThat(records).containsExactly(unexplained(GLOBAL_E3, "E3"), unexplained(GLOBAL_E4, "E4"));
  }

  @Test
  void modelNotExplainedEntriesNeverBecomeActivitiesProcessesOrTechnicalGaps() {
    PartialProcessProvider provider = new PartialProcessProvider();
    RepositoryBusinessKnowledge knowledge =
        new ProcessExplainer(provider)
            .explain(
                new ExplainRepositoryProcessesRequest(
                    partialActivities(),
                    partialMaterialSet(),
                    new ProcessExplanationProfile(4, 2, 24_000, 16_000, 2, 32, 2_000, 1)));

    assertThat(invokeUnexplainedRecords(knowledge))
        .containsExactly(unexplained(GLOBAL_E3, "E3"), unexplained(GLOBAL_E4, "E4"));
    assertThat(knowledge.activities())
        .extracting(ReviewedActivity::entryIds)
        .allSatisfy(
            entryIds ->
                assertThat(entryIds)
                    .doesNotContain("entry:global-register", "entry:global-logout"));
    assertThat(knowledge.processes())
        .flatExtracting(BusinessProcess::activityIds)
        .doesNotContain("entry:global-register", "entry:global-logout", "activity:unexplained");
    assertThat(knowledge.activityCoverage())
        .filteredOn(value -> value.entryId().equals(GLOBAL_E3) || value.entryId().equals(GLOBAL_E4))
        .extracting(ActivityEntryCoverage::disposition, ActivityEntryCoverage::reasonCode)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("NOT_ANALYZED", "MODEL_NOT_EXPLAINED"),
            org.assertj.core.groups.Tuple.tuple("NOT_ANALYZED", "MODEL_NOT_EXPLAINED"));
    assertThat(provider.repositoryDraftInput().path("activities"))
        .extracting(value -> value.path("activityId").asText())
        .doesNotContain("activity:unexplained");
  }

  @Test
  void reportListsConcreteUnexplainedHttpEntriesAndDoesNotInventChapterFourActivity() {
    ReportProvider provider = new ReportProvider();
    RepositoryBusinessKnowledge knowledge = knowledgeWithUnexplainedRecords();
    BusinessReportPublisher publisher = new BusinessReportPublisher(provider);

    String markdown =
        publisher
            .publish(
                new PublishBusinessReportRequest(
                    knowledge,
                    List.of(source("S1"), source("S2")),
                    new BusinessReportProfile(24_000, 16_000, 32, 2_000)))
            .documentMarkdown();

    JsonNode input = provider.draftInput();
    assertThat(input.path("unexplainedActivityEntries")).hasSize(1);
    JsonNode aggregate = input.path("unexplainedActivityEntries").get(0);
    assertThat(aggregate.path("materialContext").asText())
        .contains("POST /user/registerUser", "GET /user/logout");
    assertThat(textValues(aggregate.path("unexplainedEntryKeys"))).containsExactly("E3", "E4");
    assertThat(aggregate.path("reasonCode").asText()).isEqualTo("MODEL_NOT_EXPLAINED");
    assertThat(markdown)
        .contains("POST /user/registerUser", "GET /user/logout", "尚未形成业务解释")
        .doesNotContain(
            "未解释入口数量：2",
            "E3、E4",
            "unexplainedActivityEntries",
            "materialContext",
            "reasonCode",
            "MODEL_NOT_EXPLAINED");
    assertThat(markdown).contains("## 4. 业务活动").doesNotContain("注册活动已完成", "退出活动已完成");
  }

  private static ActivityExplanationResult partialActivities() {
    List<ReviewedActivity> activities =
        List.of(
            activity("activity:login", "entry:global-login", "登录", "用户登录", "E1"),
            activity("activity:session", "entry:global-session", "读取会话", "读取会话用户", "E2"));
    List<ActivityEntryCoverage> coverage =
        new ArrayList<>(
            List.of(
                new ActivityEntryCoverage(
                    "entry:global-login", "ANALYZED", List.of("activity:login"), null),
                new ActivityEntryCoverage(
                    "entry:global-session", "ANALYZED", List.of("activity:session"), null)));
    coverage.add(
        new ActivityEntryCoverage(GLOBAL_E3, "NOT_ANALYZED", List.of(), "MODEL_NOT_EXPLAINED"));
    coverage.add(
        new ActivityEntryCoverage(GLOBAL_E4, "NOT_ANALYZED", List.of(), "MODEL_NOT_EXPLAINED"));
    return new ActivityExplanationResult(
        activities,
        coverage,
        List.of(unexplained(GLOBAL_E3, "E3"), unexplained(GLOBAL_E4, "E4")),
        null);
  }

  private static UnexplainedActivityEntry unexplained(String entryId, String entryKey) {
    return new UnexplainedActivityEntry(
        entryId, MATERIAL_ID, entryKey, MATERIAL_CONTEXT, "MODEL_NOT_EXPLAINED");
  }

  private static ReviewedActivity activity(
      String activityId, String entryId, String name, String purpose, String entryKey) {
    return new ReviewedActivity(
        activityId,
        MATERIAL_ID,
        List.of(entryId),
        name,
        purpose,
        List.of(),
        List.of("用户"),
        List.of("用户请求"),
        List.of(),
        List.of(name),
        List.of("系统返回用户信息"),
        List.of(),
        List.of(),
        List.of("用户"),
        "DIRECT_CODE_BEHAVIOR",
        List.of(entryKey.equals("E1") ? "S1" : "S2"),
        List.of(),
        List.of("静态源码不证明运行成功"));
  }

  private static BusinessMaterialSet partialMaterialSet() {
    SourceReference source = source("S1");
    BusinessMaterial material =
        new BusinessMaterial(
            MATERIAL_ID,
            List.of("entry:global-login", "entry:global-session", GLOBAL_E3, GLOBAL_E4),
            BusinessMaterialMode.FLOW_PREFERRED,
            MATERIAL_CONTEXT,
            List.of("已定位用户入口处理上下文。"),
            List.of(source, source("S2")),
            List.of("flow:user"),
            List.of(),
            List.of("静态源码限制。"),
            new ModelActivityPacket(
                MATERIAL_CONTEXT,
                List.of("已定位用户入口处理上下文。"),
                List.of(
                    new ModelActivityPacket.AllowlistedReference("S1", source.snippet()),
                    new ModelActivityPacket.AllowlistedReference("S2", source("S2").snippet())),
                List.of("静态源码限制。")));
    return new BusinessMaterialSet(
        "business-material-set:partial",
        List.of(material),
        List.of(
            new BusinessMaterialEntryCoverage(
                "entry:global-login", "ANALYZED_MATERIAL", MATERIAL_ID, null),
            new BusinessMaterialEntryCoverage(
                "entry:global-session", "ANALYZED_MATERIAL", MATERIAL_ID, null),
            new BusinessMaterialEntryCoverage(GLOBAL_E3, "ANALYZED_MATERIAL", MATERIAL_ID, null),
            new BusinessMaterialEntryCoverage(GLOBAL_E4, "ANALYZED_MATERIAL", MATERIAL_ID, null)));
  }

  private static RepositoryBusinessKnowledge knowledgeWithUnexplainedRecords() {
    java.lang.reflect.RecordComponent[] components =
        RepositoryBusinessKnowledge.class.getRecordComponents();
    if (components == null) {
      fail("REPORT_PARTIAL_KNOWLEDGE_SEAM_NOT_IMPLEMENTED");
    }
    Object[] values =
        java.util.Arrays.stream(components)
            .map(Task5PartialPropagationRedTest::knowledgeComponent)
            .toArray();
    try {
      Class<?>[] types =
          java.util.Arrays.stream(components)
              .map(java.lang.reflect.RecordComponent::getType)
              .toArray(Class<?>[]::new);
      return (RepositoryBusinessKnowledge)
          RepositoryBusinessKnowledge.class.getDeclaredConstructor(types).newInstance(values);
    } catch (ReflectiveOperationException missing) {
      fail("REPORT_PARTIAL_KNOWLEDGE_SEAM_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Object knowledgeComponent(java.lang.reflect.RecordComponent component) {
    return switch (component.getName()) {
      case "activities" -> partialActivities().reviewedActivities();
      case "processes", "unmatchedActivityIds", "confirmationTopics", "notConsolidatedProcessIds" ->
          List.of();
      case "activityCoverage" -> partialActivities().coverage();
      case "unexplainedActivityEntries" ->
          List.of(unexplained(GLOBAL_E3, "E3"), unexplained(GLOBAL_E4, "E4"));
      case "repositorySummary", "checkpoint" -> null;
      default ->
          throw new IllegalArgumentException("unknown knowledge component: " + component.getName());
    };
  }

  @SuppressWarnings("unchecked")
  private static List<UnexplainedActivityEntry> invokeUnexplainedRecords(
      RepositoryBusinessKnowledge knowledge) {
    try {
      Method accessor = knowledge.getClass().getMethod("unexplainedActivityEntries");
      return (List<UnexplainedActivityEntry>) accessor.invoke(knowledge);
    } catch (NoSuchMethodException missing) {
      fail("PROCESS_UNEXPLAINED_ENTRIES_NOT_EXPOSED", missing);
      throw new AssertionError("unreachable");
    } catch (IllegalAccessException | InvocationTargetException failure) {
      throw new AssertionError(failure);
    }
  }

  private static SourceReference source(String ref) {
    return new SourceReference(
        ref,
        "src/main/java/example/UserController.java",
        ref.equals("S1") ? 10 : 20,
        ref.equals("S1") ? 18 : 28,
        ref.equals("S1")
            ? "@PostMapping(\"/user/registerUser\")"
            : "@GetMapping(\"/user/logout\")");
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> values = new ArrayList<>();
    node.fieldNames().forEachRemaining(values::add);
    return values;
  }

  private static List<String> textValues(JsonNode node) {
    List<String> values = new ArrayList<>();
    node.forEach(value -> values.add(value.asText()));
    return values;
  }

  private static final class PartialProcessProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private JsonNode repositoryDraftInput;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      if (request.taskKind().startsWith("PROCESS_GROUP")) {
        ObjectNode process = response.putArray("processes").addObject();
        process.put("processLocalId", "known-activities-only");
        process.put("name", "用户信息读取");
        process.put("businessPurpose", "读取已解释的用户信息活动。");
        ArrayNode activityIds = process.putArray("activityIds");
        input
            .path("activities")
            .forEach(value -> activityIds.add(value.path("activityId").asText()));
        ArrayNode stages = process.putArray("stages");
        int[] order = {1};
        input
            .path("activities")
            .forEach(
                value ->
                    stages
                        .addObject()
                        .put("order", order[0]++)
                        .put("activityId", value.path("activityId").asText())
                        .put("description", value.path("name").asText()));
        process.putArray("branches");
        process.putArray("sharedObjects").add("用户");
        process.putArray("codeDefinedResults").add("系统返回用户信息");
        process.put("certainty", "DIRECT_CODE_BEHAVIOR");
        process.putArray("sourceRefs").add("S1").add("S2");
        process.putArray("confirmationNotes");
        response.putArray("unmatchedActivityIds");
      } else if (request.taskKind().startsWith("REPOSITORY_SUMMARY")) {
        if (request.taskKind().endsWith("DRAFT")) repositoryDraftInput = input;
        response.put("text", "仓库知识摘要保留未解释入口范围。");
        response.putArray("businessGoals").add("读取用户信息");
        response.putArray("objectsAndRelations").add("用户与会话");
        response.putArray("confirmationTopics");
        response.putArray("sourceRefs").add("S1").add("S2");
      }
      return new StructuredModelResponse(
          json.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "task5", "none", "none"));
    }

    private JsonNode repositoryDraftInput() {
      return repositoryDraftInput;
    }
  }

  private static final class ReportProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec json = new CanonicalJsonCodec();
    private JsonNode draftInput;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = json.parseCanonical(request.untrustedInputJson());
      if (request.taskKind().equals("BUSINESS_REPORT_DRAFT")) draftInput = input;
      ObjectNode report = JsonNodeFactory.instance.objectNode();
      report.put("title", "用户入口 partial 业务说明");
      ObjectNode sections = report.putObject("sections");
      section(sections, "section1", 1, "文档说明", "本报告区分源码行为与未解释入口范围。", List.of());
      section(sections, "section2", 2, "业务目标", "读取已解释的用户信息。", List.of());
      section(sections, "section3", 3, "业务对象", "用户与会话。", List.of());
      section(sections, "section4", 4, "业务活动", "仅记录已解释的登录与会话读取活动。", List.of());
      section(sections, "section5", 5, "字段与维度", "用户请求。", List.of());
      section(sections, "section6", 6, "对象关系", "用户与会话存在代码上下文联系。", List.of());
      section(sections, "section7", 7, "指标口径", "本次未从源码识别到可定义指标。", List.of());
      section(sections, "section8", 8, "示例问题", "已解释活动有哪些返回分支？", List.of());
      section(
          sections,
          "section9",
          9,
          "待确认事项",
          "POST /user/registerUser、GET /user/logout：本次材料尚未形成业务解释，需要补充相应代码材料后再确认。",
          List.of());
      return new StructuredModelResponse(
          json.encodeCanonical(report),
          new ModelRuntimeIdentityV1("scripted", "task5", "none", "none"));
    }

    private static void section(
        ObjectNode sections,
        String slotName,
        int number,
        String title,
        String paragraph,
        List<String> refs) {
      ObjectNode section = sections.putObject(slotName);
      section.put("number", number);
      section.put("title", title);
      ArrayNode paragraphs = section.putArray("paragraphs");
      ObjectNode content = paragraphs.addObject();
      content.put("text", paragraph);
      ArrayNode refValues = content.putArray("refs");
      refs.forEach(refValues::add);
      section.putArray("items");
    }

    private JsonNode draftInput() {
      return draftInput;
    }
  }
}
