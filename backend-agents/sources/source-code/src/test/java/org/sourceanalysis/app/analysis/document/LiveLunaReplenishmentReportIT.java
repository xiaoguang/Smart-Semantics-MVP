package org.sourceanalysis.app.analysis.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.knowledge.BusinessProcess;
import org.sourceanalysis.app.analysis.knowledge.BusinessProcessStage;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/**
 * Explicit, opt-in report-quality sample over synthetic repository knowledge. It does not claim to
 * document a customer repository.
 */
class LiveLunaReplenishmentReportIT {

  private static final String EXECUTABLE = "/Applications/ChatGPT.app/Contents/Resources/codex";
  private static final List<String> CHAPTERS =
      List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");

  @Test
  void writesOneSyntheticNineSectionBusinessReportThroughTheRealLunaHighProvider()
      throws Exception {
    assertThat(System.getProperty("sourceanalysis.liveLunaReport"))
        .as("live report generation must be explicitly opted in")
        .isEqualTo("true");
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    RepositoryBusinessKnowledge knowledge = knowledge();
    List<SourceReference> sources = sources();
    BusinessReportPublication publication =
        new BusinessReportPublisher(
                new CodexSubscriptionStructuredProvider(
                    new CodexSubscriptionProfile(
                        Path.of(EXECUTABLE), "gpt-5.6-luna", "high", Duration.ofMinutes(3))))
            .publish(
                new PublishBusinessReportRequest(
                    knowledge, sources, new BusinessReportProfile(32_000, 18_000, 32, 2_000)));

    writeOutput(
        outputDirectory.resolve("live-luna-replenishment-report-sample.json"),
        knowledge,
        publication);
    assertThat(publication.businessReport().sections())
        .extracting(BusinessReportSection::title)
        .containsExactlyElementsOf(CHAPTERS);
    BusinessReportSection activities = publication.businessReport().sections().get(3);
    assertThat(activities.paragraphs().size() + activities.items().size())
        .as("the business-activity chapter describes the process")
        .isGreaterThan(0);
    assertThat(publication.documentMarkdown())
        .contains("## 1. 文档说明", "## 4. 业务活动", "## 9. 待确认事项")
        .doesNotContain("sha256", "proof:", "artifact/run");
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
                List.of("系统调用补货单保存处理"),
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
                List.of("系统调用收货记录保存处理"),
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
                List.of("系统调用应付账单保存处理"),
                List.of(),
                List.of("应付金额 = 收货数量 × 单价"),
                List.of("S3", "S4")));
    BusinessProcess process =
        new BusinessProcess(
            "process:replenishment-to-payable",
            "补货至应付账单流程",
            "将补货明细形成补货单，在存在关联补货单时记录收货，并基于收货记录形成应付账单。",
            activities.stream().map(ReviewedActivity::activityId).toList(),
            List.of(
                new BusinessProcessStage(1, "activity:create-replenishment", "校验补货明细并生成、保存补货单。"),
                new BusinessProcessStage(2, "activity:record-receipt", "读取收货数据，生成并保存与补货单关联的收货记录。"),
                new BusinessProcessStage(3, "activity:create-payable-bill", "读取收货记录，计算金额并保存应付账单。")),
            List.of("补货明细集合为空时不进入补货单生成"),
            List.of("补货单", "收货记录"),
            List.of("系统调用补货单、收货记录和应付账单保存处理"),
            "NEEDS_CONFIRMATION",
            List.of("S1", "S2", "S3", "S4"),
            List.of("对象承接支持过程叙事，但材料未直接证明三个活动必须依次执行。"));
    return new RepositoryBusinessKnowledge(
        activities,
        List.of(process),
        activities.stream()
            .map(value -> coverage(value.entryIds().get(0), value.activityId()))
            .toList(),
        List.of(),
        List.of("对象承接支持过程叙事，但材料未直接证明三个活动必须依次执行。"));
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
        List.of("这是合成验收材料，不代表客户系统实际运行。"));
  }

  private static ActivityEntryCoverage coverage(String entryId, String activityId) {
    return new ActivityEntryCoverage(entryId, "ANALYZED", List.of(activityId), null);
  }

  private static List<SourceReference> sources() {
    return List.of(
        source("S1", "SyntheticReplenishment.java", 10, "submit(details);"),
        source("S2", "SyntheticReceipt.java", 20, "saveReceipt(replenishmentId, quantity, price);"),
        source("S3", "SyntheticPayable.java", 30, "amount = quantity * price;"),
        source("S4", "SyntheticPayable.java", 31, "savePayable(receiptId, amount);"));
  }

  private static SourceReference source(String ref, String file, int line, String snippet) {
    return new SourceReference(ref, file, line, line, snippet);
  }

  private static Path requiredDirectory(String property) {
    String value = System.getProperty(property);
    assertThat(value).as(property).isNotBlank();
    Path directory = Path.of(value).toAbsolutePath().normalize();
    assertThat(directory).isDirectory();
    return directory;
  }

  private static void writeOutput(
      Path output, RepositoryBusinessKnowledge knowledge, BusinessReportPublication publication)
      throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "live-luna-report-sample-v1");
    root.put("scenario", "synthetic-replenishment-to-payable");
    root.put("model", "gpt-5.6-luna");
    root.put("reasoningEffort", "high");
    root.put("processCount", knowledge.processes().size());
    ArrayNode topics = root.putArray("confirmationTopics");
    knowledge.confirmationTopics().forEach(topics::add);
    root.put("documentMarkdown", publication.documentMarkdown());
    root.set("businessReport", reportJson(publication.businessReport()));
    Files.write(output, canonicalJson.encodeCanonical(root).copyToByteArray());
  }

  private static ObjectNode reportJson(BusinessReport report) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("title", report.title());
    ArrayNode sections = root.putArray("sections");
    report.sections().forEach(section -> sectionJson(sections.addObject(), section));
    return root;
  }

  private static void sectionJson(ObjectNode target, BusinessReportSection section) {
    target.put("number", section.number());
    target.put("title", section.title());
    contents(target.putArray("paragraphs"), section.paragraphs());
    contents(target.putArray("items"), section.items());
  }

  private static void contents(ArrayNode target, List<BusinessReportContent> values) {
    values.forEach(
        value -> {
          ObjectNode item = target.addObject();
          item.put("text", value.text());
          value.refs().forEach(item.putArray("refs")::add);
        });
  }
}
