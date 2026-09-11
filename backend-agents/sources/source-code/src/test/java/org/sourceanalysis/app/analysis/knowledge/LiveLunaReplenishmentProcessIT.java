package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/**
 * Explicit, opt-in process-quality sample over synthetic activities. It validates the P1/P2
 * business-process boundary without representing customer-source analysis.
 */
class LiveLunaReplenishmentProcessIT {

  private static final String EXECUTABLE = "/Applications/ChatGPT.app/Contents/Resources/codex";

  @Test
  void reconstructsOneSyntheticReplenishmentProcessThroughTheRealLunaHighProvider()
      throws Exception {
    assertThat(System.getProperty("sourceanalysis.liveLunaProcess"))
        .as("live process interpretation must be explicitly opted in")
        .isEqualTo("true");
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    ActivityExplanationResult activities = replenishmentActivities();
    RepositoryBusinessKnowledge knowledge =
        new ProcessExplainer(
                new CodexSubscriptionStructuredProvider(
                    new CodexSubscriptionProfile(
                        Path.of(EXECUTABLE), "gpt-5.6-luna", "high", Duration.ofMinutes(3))))
            .explain(
                new ExplainRepositoryProcessesRequest(
                    activities, new ProcessExplanationProfile(3, 1, 16_000, 12_000, 2, 32, 2_000)));

    writeOutput(
        outputDirectory.resolve("live-luna-replenishment-process-sample.json"),
        activities,
        knowledge);

    assertThat(knowledge.processes()).isNotEmpty();
    Set<String> handled = new HashSet<>(knowledge.unmatchedActivityIds());
    knowledge
        .processes()
        .forEach(
            process -> {
              handled.addAll(process.activityIds());
              assertThat(process.certainty()).isIn("REASONABLE_INFERENCE", "NEEDS_CONFIRMATION");
              assertThat(process.sourceRefs()).allMatch(Set.of("S1", "S2", "S3", "S4")::contains);
            });
    assertThat(handled)
        .containsExactlyInAnyOrder(
            "activity:create-replenishment",
            "activity:record-receipt",
            "activity:create-payable-bill");
  }

  private static ActivityExplanationResult replenishmentActivities() {
    return new ActivityExplanationResult(
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
                List.of("S3", "S4"))),
        List.of(
            coverage("entry:create-replenishment"),
            coverage("entry:record-receipt"),
            coverage("entry:create-payable-bill")));
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

  private static ActivityEntryCoverage coverage(String entryId) {
    return new ActivityEntryCoverage(
        entryId, "ANALYZED", List.of("activity:" + entryId.substring("entry:".length())), null);
  }

  private static Path requiredDirectory(String property) {
    String value = System.getProperty(property);
    assertThat(value).as(property).isNotBlank();
    Path directory = Path.of(value).toAbsolutePath().normalize();
    assertThat(directory).isDirectory();
    return directory;
  }

  private static void writeOutput(
      Path output, ActivityExplanationResult activities, RepositoryBusinessKnowledge knowledge)
      throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "live-luna-process-sample-v1");
    root.put("scenario", "synthetic-replenishment-to-payable");
    root.put("model", "gpt-5.6-luna");
    root.put("reasoningEffort", "high");
    ArrayNode activityValues = root.putArray("inputActivities");
    activities
        .reviewedActivities()
        .forEach(value -> activityJson(activityValues.addObject(), value));
    ArrayNode processValues = root.putArray("reviewedProcesses");
    knowledge.processes().forEach(value -> processJson(processValues.addObject(), value));
    strings(root.putArray("unmatchedActivityIds"), knowledge.unmatchedActivityIds());
    strings(root.putArray("confirmationTopics"), knowledge.confirmationTopics());
    Files.write(output, canonicalJson.encodeCanonical(root).copyToByteArray());
  }

  private static void activityJson(ObjectNode target, ReviewedActivity value) {
    target.put("activityId", value.activityId());
    target.put("name", value.name());
    target.put("businessPurpose", value.businessPurpose());
    strings(target.putArray("businessObjects"), value.businessObjects());
    strings(target.putArray("triggerOrInput"), value.triggerOrInput());
    strings(target.putArray("conditions"), value.conditions());
    strings(target.putArray("activitySteps"), value.activitySteps());
    strings(target.putArray("codeDefinedResults"), value.codeDefinedResults());
    strings(target.putArray("businessRules"), value.businessRules());
    strings(target.putArray("formulasOrMetrics"), value.formulasOrMetrics());
    strings(target.putArray("sourceRefs"), value.sourceRefs());
  }

  private static void processJson(ObjectNode target, BusinessProcess value) {
    target.put("processId", value.processId());
    target.put("name", value.name());
    target.put("businessPurpose", value.businessPurpose());
    strings(target.putArray("activityIds"), value.activityIds());
    ArrayNode stages = target.putArray("stages");
    value
        .stages()
        .forEach(
            stage ->
                stages
                    .addObject()
                    .put("order", stage.order())
                    .put("activityId", stage.activityId())
                    .put("description", stage.description()));
    strings(target.putArray("branches"), value.branches());
    strings(target.putArray("sharedObjects"), value.sharedObjects());
    strings(target.putArray("codeDefinedResults"), value.codeDefinedResults());
    target.put("certainty", value.certainty());
    strings(target.putArray("sourceRefs"), value.sourceRefs());
    strings(target.putArray("confirmationNotes"), value.confirmationNotes());
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }
}
