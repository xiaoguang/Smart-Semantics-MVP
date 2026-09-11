package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Explicit, opt-in product-quality sample. Surefire does not select {@code *IT}; run it only after
 * reviewing the small source packet and setting all {@code sourceanalysis.liveLuna*} properties.
 */
class LiveLunaDepotHeadActivityIT {

  private static final String COMMIT = "8c30ce7861570458920175e200bb2a6442713580";
  private static final String CONTROLLER =
      "jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java";
  private static final String SERVICE =
      "jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java";
  private static final String ACCOUNT_HEAD_CONTROLLER =
      "jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java";
  private static final String ACCOUNT_HEAD_SERVICE =
      "jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java";
  private static final String EXECUTABLE = "/Applications/ChatGPT.app/Contents/Resources/codex";
  private static final String ZERO = "0".repeat(64);

  @Test
  void explainsOneFrozenDepotHeadActivityThroughTheRealLunaHighProvider() throws Exception {
    requireSample("depot-head");
    Path repository = requiredDirectory("sourceanalysis.liveLunaRepository");
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    SourceReference controller =
        new SourceReference(
            "S1", CONTROLLER, 172, 191, frozenLines(repository, CONTROLLER, 172, 191));
    SourceReference service =
        new SourceReference("S2", SERVICE, 742, 814, frozenLines(repository, SERVICE, 742, 814));
    ModelActivityPacket packet = depotHeadPacket(controller, service);
    explainAndWrite(
        outputDirectory,
        "live-luna-depothead-activity-sample.json",
        "entry:live-depot-head-batch-status",
        packet,
        List.of(controller, service));
  }

  @Test
  void explainsOneFrozenAccountHeadActivityThroughTheRealLunaHighProvider() throws Exception {
    requireSample("account-head");
    Path repository = requiredDirectory("sourceanalysis.liveLunaRepository");
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    SourceReference controller =
        new SourceReference(
            "F1",
            ACCOUNT_HEAD_CONTROLLER,
            121,
            135,
            frozenLines(repository, ACCOUNT_HEAD_CONTROLLER, 121, 135));
    SourceReference service =
        new SourceReference(
            "F2",
            ACCOUNT_HEAD_SERVICE,
            299,
            346,
            frozenLines(repository, ACCOUNT_HEAD_SERVICE, 299, 346));
    ModelActivityPacket packet = accountHeadPacket(controller, service);
    explainAndWrite(
        outputDirectory,
        "live-luna-account-head-activity-sample.json",
        "entry:live-account-head-create",
        packet,
        List.of(controller, service));
  }

  private static void explainAndWrite(
      Path outputDirectory,
      String outputName,
      String entryId,
      ModelActivityPacket packet,
      List<SourceReference> sourceReferences)
      throws IOException {
    BusinessMaterial material =
        new BusinessMaterial(
            "material:live-" + entryId.substring("entry:live-".length()),
            List.of(entryId),
            BusinessMaterialMode.ENTRY_SOURCE_FALLBACK,
            packet.context(),
            packet.technicalObservations(),
            sourceReferences,
            List.of(),
            List.of(),
            packet.limitations(),
            packet);
    BusinessMaterialBuildResult materials =
        new BusinessMaterialBuildResult(
            new BusinessMaterialSet(
                "business-material-set:live-" + entryId.substring("entry:live-".length()),
                List.of(material),
                List.of(
                    new BusinessMaterialEntryCoverage(
                        entryId, "MATERIAL_WITH_GAPS", material.materialId(), null))),
            placeholderCheckpoint());

    ActivityExplanationResult explanation =
        new ActivityExplainer(
                new CodexSubscriptionStructuredProvider(
                    new CodexSubscriptionProfile(
                        Path.of(EXECUTABLE), "gpt-5.6-luna", "high", Duration.ofMinutes(3))))
            .explain(
                new ExplainActivitiesRequest(
                    materials, new ActivityExplanationProfile(20_000, 12_000, 2, 24, 1_000)));

    assertThat(explanation.reviewedActivities()).isNotEmpty();
    assertThat(explanation.coverage())
        .allSatisfy(value -> assertThat(value.disposition()).isEqualTo("ANALYZED_WITH_GAPS"));
    writeOutput(outputDirectory.resolve(outputName), packet, explanation, sourceReferences);
  }

  private static ModelActivityPacket depotHeadPacket(
      SourceReference controller, SourceReference service) {
    return new ModelActivityPacket(
        "一个 Spring HTTP 接口接收 status 和 ids，并调用一个 Java 服务方法批量处理这些 ID。",
        List.of(
            "接口从请求体读取 status 和 ids；服务返回值大于零时接口返回成功，否则返回错误。",
            "服务把 ids 解析为单据 ID 列表，逐条读取单据，并按目标 status、当前 status 和 purchaseStatus 决定是否把 ID 加入可更新集合。",
            "审核时，部分系统配置与单据 type/subType 组合会调用库存检查。",
            "存在可更新 ID 时，服务设置 DepotHead.status，以 ID 集合作为条件调用 Mapper 更新方法；随后代码还可能调用库存更新和日志服务。"),
        List.of(
            new ModelActivityPacket.AllowlistedReference("S1", controller.snippet()),
            new ModelActivityPacket.AllowlistedReference("S2", service.snippet())),
        List.of("这是静态源码材料，不表示某次批量操作已经提交成功。", "系统配置的实际取值、库存检查的运行结果和 Mapper 的运行时数据库效果仍需确认。"));
  }

  private static ModelActivityPacket accountHeadPacket(
      SourceReference controller, SourceReference service) {
    return new ModelActivityPacket(
        "一个 Spring HTTP 接口接收财务主表信息和明细行，并调用服务新增财务主表及明细。",
        List.of(
            "接口从请求体读取主表信息与明细行，调用服务后直接返回标准成功结果。",
            "服务先检查单据编号是否重复；转账单据还检查付款账户是否与任一明细账户重复。",
            "服务写入创建人；缺少状态时设为未审核，然后调用财务主表新增方法。",
            "服务按单据编号查询新增主表并将其标识、类型和明细行传入明细保存处理；收预付款类型还调用预付款更新处理，最后进入日志记录调用。"),
        List.of(
            new ModelActivityPacket.AllowlistedReference("F1", controller.snippet()),
            new ModelActivityPacket.AllowlistedReference("F2", service.snippet())),
        List.of(
            "这是静态源码材料，不表示某次财务单据已经实际入库、付款、记账或提交成功。",
            "未提供 Mapper、明细保存、预付款更新和日志服务的实现，运行时效果与事务范围仍需确认。"));
  }

  private static void requireSample(String expected) {
    assertThat(System.getProperty("sourceanalysis.liveLuna"))
        .as("live Luna must be explicitly opted in")
        .isEqualTo("true");
    assertThat(System.getProperty("sourceanalysis.liveLunaSample"))
        .as("the requested live sample must be explicit")
        .isEqualTo(expected);
  }

  private static Path requiredDirectory(String property) {
    String value = System.getProperty(property);
    assertThat(value).as(property).isNotBlank();
    Path directory = Path.of(value).toAbsolutePath().normalize();
    assertThat(directory).isDirectory();
    return directory;
  }

  private static String frozenLines(
      Path repository, String relativePath, int startLine, int endLine)
      throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder(
                "/usr/bin/git", "-C", repository.toString(), "show", COMMIT + ":" + relativePath)
            .redirectErrorStream(true)
            .start();
    String source = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor()).isZero();
    List<String> lines = source.lines().toList();
    assertThat(lines).hasSizeGreaterThanOrEqualTo(endLine);
    return String.join("\n", lines.subList(startLine - 1, endLine));
  }

  private static ModulePublicationReference placeholderCheckpoint() {
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(
            AnalysisRunId.parse("analysis-run:" + ZERO),
            AnalysisStepKey.FLOW_INTERPRETATION,
            10,
            "business-material-builder"),
        ModuleArtifactRoot.parse("module-root:" + ZERO),
        ModuleReceiptId.parse("module-receipt:" + ZERO),
        Sha256Digest.parse(ZERO));
  }

  private static void writeOutput(
      Path output,
      ModelActivityPacket packet,
      ActivityExplanationResult explanation,
      List<SourceReference> sourceReferences)
      throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "live-luna-activity-sample-v1");
    root.put("sourceCommit", COMMIT);
    root.put("model", "gpt-5.6-luna");
    root.put("reasoningEffort", "high");
    root.set("cleanModelPacket", packetJson(packet));
    ArrayNode activities = root.putArray("reviewedActivities");
    explanation.reviewedActivities().forEach(activity -> activities.add(activityJson(activity)));
    ArrayNode coverage = root.putArray("coverage");
    explanation
        .coverage()
        .forEach(
            value -> {
              ObjectNode item = coverage.addObject();
              item.put("entryId", value.entryId());
              item.put("disposition", value.disposition());
              strings(item.putArray("activityIds"), value.activityIds());
              if (value.reasonCode() == null) {
                item.putNull("reasonCode");
              } else {
                item.put("reasonCode", value.reasonCode());
              }
            });
    ArrayNode sources = root.putArray("programSideSourceReferences");
    sourceReferences.forEach(
        value -> {
          ObjectNode item = sources.addObject();
          item.put("ref", value.ref());
          item.put("file", value.file());
          item.put("startLine", value.startLine());
          item.put("endLine", value.endLine());
          item.put("snippet", value.snippet());
        });
    Files.write(output, canonicalJson.encodeCanonical(root).copyToByteArray());
  }

  private static ObjectNode packetJson(ModelActivityPacket packet) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("context", packet.context());
    strings(root.putArray("technicalObservations"), packet.technicalObservations());
    ArrayNode refs = root.putArray("allowlistedRefs");
    packet
        .allowlistedRefs()
        .forEach(
            value -> {
              ObjectNode item = refs.addObject();
              item.put("ref", value.ref());
              item.put("snippet", value.snippet());
            });
    strings(root.putArray("limitations"), packet.limitations());
    return root;
  }

  private static ObjectNode activityJson(ReviewedActivity activity) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("activityId", activity.activityId());
    root.put("name", activity.name());
    root.put("businessPurpose", activity.businessPurpose());
    strings(root.putArray("participants"), activity.participants());
    strings(root.putArray("businessObjects"), activity.businessObjects());
    strings(root.putArray("triggerOrInput"), activity.triggerOrInput());
    strings(root.putArray("conditions"), activity.conditions());
    strings(root.putArray("activitySteps"), activity.activitySteps());
    strings(root.putArray("codeDefinedResults"), activity.codeDefinedResults());
    strings(root.putArray("businessRules"), activity.businessRules());
    strings(root.putArray("formulasOrMetrics"), activity.formulasOrMetrics());
    strings(root.putArray("terms"), activity.terms());
    root.put("certainty", activity.certainty());
    strings(root.putArray("sourceRefs"), activity.sourceRefs());
    strings(root.putArray("questions"), activity.questions());
    strings(root.putArray("scopeLimitations"), activity.scopeLimitations());
    return root;
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }
}
