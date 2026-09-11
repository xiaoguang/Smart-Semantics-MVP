package org.sourceanalysis.app.analysis.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.knowledge.BusinessProcess;
import org.sourceanalysis.app.analysis.knowledge.BusinessProcessStage;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;
import org.sourceanalysis.app.analysis.knowledge.RepositoryProcessSummary;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Public-seam RED for the first business-language nine-section document. */
class BusinessReportPublisherTest {

  @Test
  void publishesOneReadableNineSectionDocumentAndProgramGeneratedSourceAnchors() throws Exception {
    Class<?> publisherType =
        requireType("org.sourceanalysis.app.analysis.document.BusinessReportPublisher");
    Class<?> profileType =
        requireType("org.sourceanalysis.app.analysis.document.BusinessReportProfile");
    Class<?> requestType =
        requireType("org.sourceanalysis.app.analysis.document.PublishBusinessReportRequest");
    RecordingReportProvider provider = new RecordingReportProvider();
    Object publisher =
        publisherType.getConstructor(StructuredModelProvider.class).newInstance(provider);
    Object profile =
        profileType
            .getConstructor(int.class, int.class, int.class, int.class)
            .newInstance(24_000, 16_000, 32, 2_000);
    Object request =
        requestType
            .getConstructor(RepositoryBusinessKnowledge.class, List.class, profileType)
            .newInstance(knowledge(), sourceReferences(), profile);

    Object publication = invoke(publisherType, publisher, request);
    String markdown =
        (String) publication.getClass().getMethod("documentMarkdown").invoke(publication);

    assertThat(provider.taskKinds())
        .containsExactly("BUSINESS_REPORT_DRAFT", "BUSINESS_REPORT_REVIEW");
    assertThat(provider.outputSchemas())
        .hasSize(2)
        .allSatisfy(BusinessReportPublisherTest::assertReportSchema);
    assertThat(provider.draftInput().toString())
        .contains("补货到应付账单形成", "补货明细集合不能为空", "应付金额 = 收货数量 × 单价")
        .doesNotContain("src/main", "sha256", "proof:", "flow:");
    assertThat(provider.draftInput().path("activities").get(0).path("terms"))
        .extracting(JsonNode::asText)
        .containsExactly("术语：补货单");
    assertThat(provider.draftInput().path("activities").get(0).path("certainty").asText())
        .isEqualTo("DIRECT_CODE_BEHAVIOR");
    assertThat(
            provider
                .draftInput()
                .path("processes")
                .get(0)
                .path("stages")
                .get(0)
                .path("activity")
                .asText())
        .isEqualTo("创建补货单");
    assertThat(provider.draftInput().path("repositorySummary").path("text").asText())
        .isEqualTo("仓库围绕补货、收货和应付账单形成定义了可讨论的业务活动。");
    assertThat(provider.reviewActualDraft()).isEqualTo(provider.draft());
    assertThat(markdown)
        .contains("# 合成补货仓库业务说明", "## 4. 业务活动", "补货到应付账单形成")
        .contains(
            "<summary>技术依据（可选）</summary>",
            "S1 — src/main/java/example/ReplenishmentService.java:21–23");
    assertThat(markdown.lines().filter(line -> line.startsWith("## ")).count()).isEqualTo(9);
  }

  @Test
  void rejectsAReportCitationThatIsOutsideTheProgramOwnedReferenceMap() {
    assertThatThrownBy(
            () ->
                new BusinessReportPublisher(new RecordingReportProvider(true))
                    .publish(
                        new PublishBusinessReportRequest(
                            knowledge(),
                            sourceReferences(),
                            new BusinessReportProfile(24_000, 16_000, 32, 2_000))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("BUSINESS_REPORT_SOURCE_SCOPE_INVALID");
  }

  private static Class<?> requireType(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException missing) {
      fail("BUSINESS_REPORT_PUBLISHER_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    }
  }

  private static Object invoke(Class<?> type, Object publisher, Object request) throws Exception {
    Method publish = type.getMethod("publish", request.getClass());
    try {
      return publish.invoke(publisher, request);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(failure.getCause());
    }
  }

  private static RepositoryBusinessKnowledge knowledge() {
    List<ReviewedActivity> activities =
        List.of(
            activity(
                "activity:create-replenishment",
                "entry:create-replenishment",
                "创建补货单",
                "把补货明细形成并保存为补货单。",
                List.of("补货单", "补货明细"),
                List.of("补货明细集合"),
                List.of("补货明细集合不能为空"),
                List.of("校验明细", "生成补货单", "保存补货单"),
                List.of("没有补货明细时不进入补货单生成"),
                List.of(),
                List.of("S1", "S2")),
            activity(
                "activity:record-receipt",
                "entry:record-receipt",
                "记录收货",
                "保存与补货单关联的收货记录。",
                List.of("补货单", "收货记录"),
                List.of("补货单标识", "收货数量", "单价"),
                List.of(),
                List.of("读取收货数据", "生成收货记录", "保存收货记录"),
                List.of(),
                List.of(),
                List.of("S2", "S3")),
            activity(
                "activity:create-payable-bill",
                "entry:create-payable-bill",
                "创建应付账单",
                "按收货记录形成并保存应付账单。",
                List.of("收货记录", "应付账单"),
                List.of("收货记录标识"),
                List.of(),
                List.of("读取收货记录", "计算金额", "保存应付账单"),
                List.of(),
                List.of("应付金额 = 收货数量 × 单价"),
                List.of("S3", "S4")));
    BusinessProcess process =
        new BusinessProcess(
            "process:replenishment-to-payable",
            "补货到应付账单形成",
            "从补货明细形成补货单，在记录收货后形成应付账单。",
            activities.stream().map(ReviewedActivity::activityId).toList(),
            List.of(
                new BusinessProcessStage(1, "activity:create-replenishment", "生成并保存补货单"),
                new BusinessProcessStage(2, "activity:record-receipt", "记录关联收货"),
                new BusinessProcessStage(3, "activity:create-payable-bill", "形成应付账单")),
            List.of("补货明细为空时，补货单生成前停止"),
            List.of("补货单", "收货记录", "应付账单"),
            List.of("系统可形成补货单、收货记录和应付账单"),
            "REASONABLE_INFERENCE",
            List.of("S1", "S2", "S3", "S4"),
            List.of("组织制度是否强制三个入口依次执行仍待确认"));
    return new RepositoryBusinessKnowledge(
        activities,
        List.of(process),
        activities.stream()
            .map(
                activity ->
                    new ActivityEntryCoverage(
                        activity.entryIds().get(0),
                        "ANALYZED",
                        List.of(activity.activityId()),
                        null))
            .toList(),
        List.of(),
        List.of("组织制度是否强制三个入口依次执行仍待确认"),
        List.of(),
        new RepositoryProcessSummary(
            "仓库围绕补货、收货和应付账单形成定义了可讨论的业务活动。",
            List.of("形成补货单", "登记收货", "形成应付账单"),
            List.of("收货记录承接补货单", "应付账单承接收货记录"),
            List.of("组织制度是否强制三个入口依次执行仍待确认"),
            List.of("S1", "S2", "S3", "S4")),
        null);
  }


  private static ReviewedActivity activity(
      String activityId,
      String entryId,
      String name,
      String purpose,
      List<String> objects,
      List<String> input,
      List<String> conditions,
      List<String> steps,
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
        input,
        conditions,
        steps,
        List.of("系统生成并保存" + objects.get(0)),
        rules,
        formulas,
        List.of("术语：" + objects.get(0)),
        "DIRECT_CODE_BEHAVIOR",
        refs,
        List.of(),
        List.of("静态源码不证明某次保存成功"));
  }

  private static List<SourceReference> sourceReferences() {
    return List.of(
        new SourceReference(
            "S1",
            "src/main/java/example/ReplenishmentService.java",
            21,
            23,
            "if (command.lines().isEmpty()) { throw new IllegalArgumentException(); }"),
        new SourceReference(
            "S2", "src/main/java/example/ReplenishmentService.java", 30, 32, "save(order);"),
        new SourceReference(
            "S3", "src/main/java/example/ReceiptService.java", 41, 44, "save(receipt);"),
        new SourceReference("S4", "src/main/java/example/BillService.java", 51, 55, "save(bill);"));
  }

  private static void assertReportSchema(JsonNode schema) {
    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(textValues(schema.path("required"))).containsExactly("title", "sections");
    JsonNode section = schema.path("properties").path("sections").path("items");
    assertThat(section.path("type").asText()).isEqualTo("object");
    assertThat(section.path("additionalProperties").asBoolean()).isFalse();
    assertThat(textValues(section.path("required")))
        .containsExactly("number", "title", "paragraphs", "items");
    JsonNode content = section.path("properties").path("paragraphs").path("items");
    assertThat(content.path("type").asText()).isEqualTo("object");
    assertThat(textValues(content.path("required"))).containsExactly("text", "refs");
  }

  private static List<String> textValues(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static final class RecordingReportProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> taskKinds = new ArrayList<>();
    private JsonNode draftInput;
    private JsonNode reviewActualDraft;
    private JsonNode draft;
    private final List<JsonNode> outputSchemas = new ArrayList<>();
    private final boolean emitUnknownReference;

    private RecordingReportProvider() {
      this(false);
    }

    private RecordingReportProvider(boolean emitUnknownReference) {
      this.emitUnknownReference = emitUnknownReference;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      outputSchemas.add(canonicalJson.parseCanonical(request.outputJsonSchema()));
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      if ("BUSINESS_REPORT_DRAFT".equals(request.taskKind())) {
        draftInput = input;
      } else {
        reviewActualDraft = input.path("actualDraft");
      }
      JsonNode response = report();
      if ("BUSINESS_REPORT_DRAFT".equals(request.taskKind())) {
        draft = response;
      }
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "none", "none"));
    }

    private JsonNode report() {
      ObjectNode report = JsonNodeFactory.instance.objectNode();
      report.put("title", "合成补货仓库业务说明");
      ArrayNode sections = report.putArray("sections");
      section(sections, 1, "文档说明", "本文描述冻结源码定义的系统行为，不代表某次运行成功。", List.of(), List.of());
      section(
          sections,
          2,
          "业务目标",
          "系统支持形成补货单、记录收货并形成应付账单。",
          emitUnknownReference ? List.of("S99") : List.of("S1", "S2", "S3", "S4"),
          List.of());
      section(
          sections,
          3,
          "业务对象",
          "主要对象是补货单、补货明细、收货记录和应付账单。",
          List.of("S1", "S2", "S3", "S4"),
          List.of());
      section(
          sections,
          4,
          "业务活动",
          "补货到应付账单形成：先形成补货单，再记录收货并形成应付账单；制度顺序仍待确认。",
          List.of("S1", "S2", "S3", "S4"),
          List.of());
      section(
          sections,
          5,
          "字段与维度",
          "业务输入包括补货明细、补货单标识、收货数量、单价和收货记录标识。",
          List.of("S1", "S2", "S3"),
          List.of());
      section(sections, 6, "对象关系", "收货记录承接补货单，应付账单承接收货记录。", List.of("S2", "S3", "S4"), List.of());
      section(sections, 7, "指标口径", "应付金额 = 收货数量 × 单价。", List.of("S4"), List.of());
      section(sections, 8, "示例问题", null, List.of(), List.of("没有补货明细时系统如何处理？"));
      section(sections, 9, "待确认事项", null, List.of(), List.of("需要确认岗位、制度顺序、审核和付款。"));
      return report;
    }

    private static void section(
        ArrayNode sections,
        int number,
        String title,
        String paragraph,
        List<String> paragraphRefs,
        List<String> items) {
      ObjectNode section = sections.addObject();
      section.put("number", number);
      section.put("title", title);
      ArrayNode paragraphs = section.putArray("paragraphs");
      if (paragraph != null) {
        content(paragraphs.addObject(), paragraph, paragraphRefs);
      }
      ArrayNode entries = section.putArray("items");
      for (String item : items) {
        content(entries.addObject(), item, List.of());
      }
    }

    private static void content(ObjectNode content, String text, List<String> refs) {
      content.put("text", text);
      ArrayNode referenceValues = content.putArray("refs");
      refs.forEach(referenceValues::add);
    }

    private List<String> taskKinds() {
      return List.copyOf(taskKinds);
    }

    private JsonNode draftInput() {
      return draftInput;
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
