package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.document.BusinessReport;
import org.sourceanalysis.app.analysis.document.BusinessReportContent;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.document.BusinessReportPublication;
import org.sourceanalysis.app.analysis.document.BusinessReportPublisher;
import org.sourceanalysis.app.analysis.document.BusinessReportSection;
import org.sourceanalysis.app.analysis.document.PublishBusinessReportRequest;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialMode;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Explicit live continuation of one completed four-entry activity candidate.
 *
 * <p>This harness never calls {@code ActivityExplainer}. It proves that a frozen completed activity
 * result can be fed into the active Process and Report Modules without rebuilding the source
 * material or replaying an activity request.
 */
class LiveLunaAutomaticUserAccountGroupChainIT {

  private static final String EXECUTABLE = "/Applications/ChatGPT.app/Contents/Resources/codex";
  private static final List<String> CHAPTERS =
      List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");
  private static final String USER_ACCOUNT_GROUP_MATERIAL_ID =
      "material:8be00d5562743218931b721c547d915076a08b7200bc06e415d1248c5ea663eb";

  @Test
  @EnabledIfSystemProperty(
      named = "sourceanalysis.liveLunaUserAccountGroupChainInput",
      matches = "true")
  void reopensTheCompletedFourEntryActivityCandidateWithoutCallingAProvider() throws Exception {
    ChainInput input = loadInput();

    assertThat(input.activities().reviewedActivities()).hasSize(4);
    assertThat(input.activities().coverage()).hasSize(4);
    assertThat(input.activities().coverage())
        .allSatisfy(
            coverage -> {
              assertThat(coverage.disposition()).isEqualTo("ANALYZED");
              assertThat(coverage.activityIds()).hasSize(1);
            });
    assertThat(input.materials().materials())
        .extracting(BusinessMaterial::materialId)
        .containsExactly(USER_ACCOUNT_GROUP_MATERIAL_ID);
    assertThat(input.sourceReferences())
        .extracting(SourceReference::ref)
        .containsExactly("S487", "S722", "S731", "S898");
  }

  @Test
  @EnabledIfSystemProperty(named = "sourceanalysis.liveLunaUserAccountGroupChain", matches = "true")
  void reconstructsProcessesAndWritesOneNineSectionReportFromTheCompletedActivities()
      throws Exception {
    ChainInput input = loadInput();
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    AtomicInteger calls = new AtomicInteger();
    StructuredModelProvider provider =
        recordingProvider(
            outputDirectory,
            calls,
            new CodexSubscriptionStructuredProvider(
                new CodexSubscriptionProfile(
                    Path.of(EXECUTABLE), "gpt-5.6-luna", "high", Duration.ofMinutes(3))));
    RepositoryBusinessKnowledge knowledge =
        new ProcessExplainer(provider)
            .explain(
                new ExplainRepositoryProcessesRequest(
                    input.activities(),
                    input.materials(),
                    new ProcessExplanationProfile(4, 2, 32_000, 16_000, 4, 32, 2_000, 4)));
    assertThat(knowledge.processes())
        .as(
            "the model must produce at least one cautious cross-activity process or the candidate is not useful")
        .isNotEmpty();

    BusinessReportPublication report =
        new BusinessReportPublisher(provider)
            .publish(
                new PublishBusinessReportRequest(
                    knowledge,
                    input.sourceReferences(),
                    new BusinessReportProfile(32_000, 18_000, 32, 2_000)));
    assertThat(report.businessReport().sections())
        .extracting(BusinessReportSection::title)
        .containsExactlyElementsOf(CHAPTERS);
    assertThat(report.documentMarkdown())
        .contains("## 1. 文档说明", "## 4. 业务活动", "## 9. 待确认事项")
        .doesNotContain("sha256", "proof:", "artifact/run");
    assertThat(calls.get())
        .as("at most two calls for each bounded process/repository/report unit")
        .isBetween(4, 8);

    writeOutput(outputDirectory, input, knowledge, report, calls.get());
  }

  private static ChainInput loadInput() throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    JsonNode root = parseFile(requiredFile("sourceanalysis.liveLunaActivityResult"));
    String materialId = requiredText(root, "materialId");
    if (!USER_ACCOUNT_GROUP_MATERIAL_ID.equals(materialId)) {
      throw new IllegalArgumentException("LIVE_LUNA_CHAIN_MATERIAL_UNEXPECTED:" + materialId);
    }
    BusinessMaterial material =
        readMatchingMaterial(requiredFile("sourceanalysis.liveLunaMaterials"), materialId);
    Map<String, List<String>> entryIdsByActivity = activityCoverage(root.path("coverage"));
    List<ReviewedActivity> activities =
        root.path("reviewedActivities")
            .valueStream()
            .map(activity -> readActivity(activity, materialId, entryIdsByActivity))
            .sorted(Comparator.comparing(ReviewedActivity::activityId))
            .toList();
    List<ActivityEntryCoverage> coverage =
        root.path("coverage")
            .valueStream()
            .map(LiveLunaAutomaticUserAccountGroupChainIT::readCoverage)
            .sorted(Comparator.comparing(ActivityEntryCoverage::entryId))
            .toList();
    if (activities.size() != 4 || coverage.size() != 4) {
      throw new IllegalArgumentException("LIVE_LUNA_CHAIN_EXPECTED_FOUR_ENTRIES");
    }
    if (!entryIdsByActivity
        .keySet()
        .equals(
            activities.stream()
                .map(ReviewedActivity::activityId)
                .collect(java.util.stream.Collectors.toSet()))) {
      throw new IllegalArgumentException("LIVE_LUNA_CHAIN_ACTIVITY_COVERAGE_INVALID");
    }
    return new ChainInput(
        new ActivityExplanationResult(activities, coverage),
        new BusinessMaterialSet(
            "business-material-set:live-user-account-group", List.of(material), List.of()),
        material.sourceRefs());
  }

  private static StructuredModelProvider recordingProvider(
      Path outputDirectory, AtomicInteger calls, StructuredModelProvider delegate) {
    return request -> {
      int call = calls.incrementAndGet();
      writeDiagnostic(
          outputDirectory, call, request.taskKind(), "input", request.untrustedInputJson());
      StructuredModelResponse response = delegate.generate(request);
      writeDiagnostic(
          outputDirectory, call, request.taskKind(), "response", response.responseJson());
      return response;
    };
  }

  private static void writeDiagnostic(
      Path outputDirectory, int call, String taskKind, String suffix, ImmutableBytes bytes) {
    try {
      Files.write(
          outputDirectory.resolve(String.format("%02d-%s-%s.json", call, taskKind, suffix)),
          bytes.copyToByteArray());
    } catch (IOException failure) {
      throw new IllegalStateException("LIVE_LUNA_CHAIN_DIAGNOSTIC_WRITE_FAILED", failure);
    }
  }

  private static BusinessMaterial readMatchingMaterial(Path materialsFile, String materialId)
      throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    try (Stream<String> lines = Files.lines(materialsFile)) {
      JsonNode record =
          lines
              .filter(line -> !line.isBlank())
              .map(
                  line ->
                      canonicalJson.parseCanonical(
                          ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
              .filter(value -> "BUSINESS_MATERIAL".equals(value.path("recordType").asText()))
              .filter(value -> materialId.equals(value.path("materialId").asText()))
              .findFirst()
              .orElseThrow(
                  () -> new IllegalArgumentException("LIVE_LUNA_CHAIN_MATERIAL_NOT_FOUND"));
      JsonNode packet = record.path("modelPacket");
      List<SourceReference> refs =
          record
              .path("sourceRefs")
              .valueStream()
              .map(
                  value ->
                      new SourceReference(
                          requiredText(value, "ref"),
                          requiredText(value, "file"),
                          value.path("startLine").asInt(),
                          value.path("endLine").asInt(),
                          requiredText(value, "snippet")))
              .toList();
      return new BusinessMaterial(
          materialId,
          strings(record, "entryIds"),
          BusinessMaterialMode.valueOf(requiredText(record, "materialMode")),
          requiredText(record, "context"),
          strings(record, "technicalObservations"),
          refs,
          strings(record, "flowRefs"),
          strings(record, "technicalProofRefs"),
          strings(record, "limitations"),
          new ModelActivityPacket(
              requiredText(packet, "context"),
              strings(packet, "technicalObservations"),
              packet
                  .path("allowlistedRefs")
                  .valueStream()
                  .map(
                      value ->
                          new ModelActivityPacket.AllowlistedReference(
                              requiredText(value, "ref"), requiredText(value, "snippet")))
                  .toList(),
              strings(packet, "limitations")));
    }
  }

  private static Map<String, List<String>> activityCoverage(JsonNode coverage) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (JsonNode value : coverage) {
      String entryId = requiredText(value, "entryId");
      for (String activityId : strings(value, "activityIds")) {
        result.computeIfAbsent(activityId, ignored -> new ArrayList<>()).add(entryId);
      }
    }
    return result;
  }

  private static ReviewedActivity readActivity(
      JsonNode value, String materialId, Map<String, List<String>> entryIdsByActivity) {
    String activityId = requiredText(value, "activityId");
    List<String> entryIds = entryIdsByActivity.get(activityId);
    if (entryIds == null || entryIds.isEmpty()) {
      throw new IllegalArgumentException("LIVE_LUNA_CHAIN_ACTIVITY_COVERAGE_MISSING:" + activityId);
    }
    return new ReviewedActivity(
        activityId,
        materialId,
        List.copyOf(entryIds),
        requiredText(value, "name"),
        requiredText(value, "businessPurpose"),
        strings(value, "participants"),
        strings(value, "businessObjects"),
        strings(value, "triggerOrInput"),
        strings(value, "conditions"),
        strings(value, "activitySteps"),
        strings(value, "codeDefinedResults"),
        strings(value, "businessRules"),
        strings(value, "formulasOrMetrics"),
        strings(value, "terms"),
        requiredText(value, "certainty"),
        strings(value, "sourceRefs"),
        strings(value, "questions"),
        strings(value, "scopeLimitations"));
  }

  private static ActivityEntryCoverage readCoverage(JsonNode value) {
    return new ActivityEntryCoverage(
        requiredText(value, "entryId"),
        requiredText(value, "disposition"),
        strings(value, "activityIds"),
        value.path("reasonCode").isNull() ? null : value.path("reasonCode").asText());
  }

  private static void writeOutput(
      Path outputDirectory,
      ChainInput input,
      RepositoryBusinessKnowledge knowledge,
      BusinessReportPublication publication,
      int providerCalls)
      throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "live-luna-user-account-group-chain-v1");
    root.put("sourceCommit", requiredText(System.getProperty("sourceanalysis.liveLunaCommit")));
    root.put("model", "gpt-5.6-luna");
    root.put("reasoningEffort", "high");
    root.put("activityCount", input.activities().reviewedActivities().size());
    root.put("processCount", knowledge.processes().size());
    root.put("providerCallCount", providerCalls);
    ArrayNode processes = root.putArray("processes");
    knowledge.processes().forEach(process -> processJson(processes.addObject(), process));
    root.set("businessReport", reportJson(publication.businessReport()));
    root.put("documentMarkdown", publication.documentMarkdown());
    Files.write(
        outputDirectory.resolve("live-luna-automatic-user-account-group-chain.json"),
        canonicalJson.encodeCanonical(root).copyToByteArray());
    Files.writeString(
        outputDirectory.resolve("document.md"),
        publication.documentMarkdown(),
        StandardCharsets.UTF_8);
  }

  private static void processJson(ObjectNode value, BusinessProcess process) {
    value.put("processId", process.processId());
    value.put("name", process.name());
    value.put("businessPurpose", process.businessPurpose());
    strings(value.putArray("activityIds"), process.activityIds());
    ArrayNode stages = value.putArray("stages");
    process
        .stages()
        .forEach(
            stage ->
                stages
                    .addObject()
                    .put("order", stage.order())
                    .put("activityId", stage.activityId())
                    .put("description", stage.description()));
    strings(value.putArray("branches"), process.branches());
    strings(value.putArray("sharedObjects"), process.sharedObjects());
    strings(value.putArray("codeDefinedResults"), process.codeDefinedResults());
    value.put("certainty", process.certainty());
    strings(value.putArray("sourceRefs"), process.sourceRefs());
    strings(value.putArray("confirmationNotes"), process.confirmationNotes());
  }

  private static ObjectNode reportJson(BusinessReport report) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("title", report.title());
    ArrayNode sections = root.putArray("sections");
    report.sections().forEach(section -> sectionJson(sections.addObject(), section));
    return root;
  }

  private static void sectionJson(ObjectNode value, BusinessReportSection section) {
    value.put("number", section.number());
    value.put("title", section.title());
    contents(value.putArray("paragraphs"), section.paragraphs());
    contents(value.putArray("items"), section.items());
  }

  private static void contents(ArrayNode target, List<BusinessReportContent> contents) {
    contents.forEach(
        content -> {
          ObjectNode value = target.addObject();
          value.put("text", content.text());
          strings(value.putArray("refs"), content.refs());
        });
  }

  private static JsonNode parseFile(Path file) throws IOException {
    return new CanonicalJsonCodec().parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(file)));
  }

  private static Path requiredFile(String property) {
    Path file = Path.of(requiredText(System.getProperty(property))).toAbsolutePath().normalize();
    assertThat(file).as(property).isRegularFile();
    return file;
  }

  private static Path requiredDirectory(String property) {
    Path directory =
        Path.of(requiredText(System.getProperty(property))).toAbsolutePath().normalize();
    assertThat(directory).as(property).isDirectory();
    return directory;
  }

  private static String requiredText(JsonNode value, String field) {
    return requiredText(value.path(field).asText());
  }

  private static String requiredText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LIVE_LUNA_CHAIN_REQUIRED_TEXT_MISSING");
    }
    return value;
  }

  private static List<String> strings(JsonNode value, String field) {
    return value.path(field).valueStream().map(JsonNode::asText).toList();
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private record ChainInput(
      ActivityExplanationResult activities,
      BusinessMaterialSet materials,
      List<SourceReference> sourceReferences) {}
}
