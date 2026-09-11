package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
import org.sourceanalysis.app.analysis.document.BusinessReport;
import org.sourceanalysis.app.analysis.document.BusinessReportContent;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.document.BusinessReportPublication;
import org.sourceanalysis.app.analysis.document.BusinessReportPublisher;
import org.sourceanalysis.app.analysis.document.BusinessReportSection;
import org.sourceanalysis.app.analysis.document.PublishBusinessReportRequest;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Explicit opt-in report sample built from completed customer-source activity and process reviews.
 *
 * <p>The input check is a separate zero-Provider action. The report invocation never repeats the
 * registration/login activity or process requests already preserved in the ignored workspace.
 */
class LiveLunaAutomaticUserLifecycleReportIT {

  private static final String EXECUTABLE = "/Applications/ChatGPT.app/Contents/Resources/codex";
  private static final List<String> CHAPTERS =
      List.of("文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项");

  @Test
  @EnabledIfSystemProperty(
      named = "sourceanalysis.liveLunaUserLifecycleReportInput",
      matches = "true")
  void loadsCompletedActivitiesProcessesAndSourceReferencesWithoutCallingAProvider()
      throws Exception {
    ReportInput input = loadInput();

    assertThat(input.knowledge().activities()).hasSize(2);
    assertThat(input.knowledge().processes()).hasSize(2);
    assertThat(input.knowledge().activityCoverage()).hasSize(2);
    assertThat(input.sourceReferences()).isNotEmpty();
    assertThat(input.sourceReferences())
        .extracting(SourceReference::ref)
        .containsExactlyElementsOf(
            input.sourceReferences().stream().map(SourceReference::ref).sorted().toList());
    assertThat(input.knowledge().processes())
        .allSatisfy(
            process ->
                assertThat(input.knowledge().activities())
                    .extracting(activity -> activity.activityId())
                    .containsAll(process.activityIds()));
  }

  @Test
  @EnabledIfSystemProperty(named = "sourceanalysis.liveLunaUserLifecycleReport", matches = "true")
  void writesOneNineSectionReportFromCompletedAutomaticActivitiesAndProcesses() throws Exception {
    ReportInput input = loadInput();
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    BusinessReportPublication publication =
        new BusinessReportPublisher(
                new CodexSubscriptionStructuredProvider(
                    new CodexSubscriptionProfile(
                        Path.of(EXECUTABLE), "gpt-5.6-luna", "high", Duration.ofMinutes(3))))
            .publish(
                new PublishBusinessReportRequest(
                    input.knowledge(),
                    input.sourceReferences(),
                    new BusinessReportProfile(32_000, 18_000, 32, 2_000)));

    writeOutput(
        outputDirectory.resolve("live-luna-automatic-user-lifecycle-report.json"),
        input,
        publication);
    assertThat(publication.businessReport().sections())
        .extracting(BusinessReportSection::title)
        .containsExactlyElementsOf(CHAPTERS);
    assertThat(
            publication.businessReport().sections().get(3).paragraphs().size()
                + publication.businessReport().sections().get(3).items().size())
        .as("chapter 4 describes the reviewed business activities or processes")
        .isGreaterThan(0);
    assertThat(publication.documentMarkdown())
        .contains("## 1. 文档说明", "## 4. 业务活动", "## 9. 待确认事项")
        .doesNotContain("sha256", "proof:", "artifact/run");
  }

  private static ReportInput loadInput() throws Exception {
    Path materials = requiredFile("sourceanalysis.liveLunaMaterials");
    Path registration = requiredFile("sourceanalysis.liveLunaRegistrationActivity");
    Path login = requiredFile("sourceanalysis.liveLunaLoginActivity");
    Path processes = requiredFile("sourceanalysis.liveLunaUserLifecycleProcess");
    LiveLunaAutomaticUserLifecycleProcessIT.ProcessInput activitiesAndMaterials =
        LiveLunaAutomaticUserLifecycleProcessIT.loadInput(materials, List.of(registration, login));
    List<BusinessProcess> reviewedProcesses = readProcesses(processes);
    List<SourceReference> sourceReferences =
        activitiesAndMaterials.materials().materials().stream()
            .flatMap(material -> material.sourceRefs().stream())
            .collect(
                java.util.stream.Collectors.toMap(
                    SourceReference::ref,
                    value -> value,
                    (left, right) -> {
                      if (!left.equals(right)) {
                        throw new IllegalArgumentException("LIVE_LUNA_REPORT_SOURCE_REF_COLLISION");
                      }
                      return left;
                    }))
            .values()
            .stream()
            .sorted(Comparator.comparing(SourceReference::ref))
            .toList();
    JsonNode processRoot = parseFile(processes);
    RepositoryBusinessKnowledge knowledge =
        new RepositoryBusinessKnowledge(
            activitiesAndMaterials.activities().reviewedActivities(),
            reviewedProcesses,
            activitiesAndMaterials.activities().coverage(),
            strings(processRoot, "unmatchedActivityIds"),
            strings(processRoot, "confirmationTopics"));
    return new ReportInput(knowledge, sourceReferences);
  }

  private static List<BusinessProcess> readProcesses(Path output) throws IOException {
    JsonNode root = parseFile(output);
    if (!root.path("reviewedProcesses").isArray()) {
      throw new IllegalArgumentException("LIVE_LUNA_REPORT_PROCESSES_MISSING");
    }
    List<BusinessProcess> processes =
        root.path("reviewedProcesses")
            .valueStream()
            .map(
                value ->
                    new BusinessProcess(
                        text(value, "processId"),
                        text(value, "name"),
                        text(value, "businessPurpose"),
                        strings(value, "activityIds"),
                        value
                            .path("stages")
                            .valueStream()
                            .map(
                                stage ->
                                    new BusinessProcessStage(
                                        stage.path("order").asInt(),
                                        text(stage, "activityId"),
                                        text(stage, "description")))
                            .toList(),
                        strings(value, "branches"),
                        strings(value, "sharedObjects"),
                        strings(value, "codeDefinedResults"),
                        text(value, "certainty"),
                        strings(value, "sourceRefs"),
                        strings(value, "confirmationNotes")))
            .sorted(Comparator.comparing(BusinessProcess::processId))
            .toList();
    if (processes.isEmpty()) {
      throw new IllegalArgumentException("LIVE_LUNA_REPORT_PROCESSES_EMPTY");
    }
    return processes;
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

  private static JsonNode parseFile(Path file) throws IOException {
    return new CanonicalJsonCodec().parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(file)));
  }

  private static String text(JsonNode object, String field) {
    return requiredText(object.path(field).asText());
  }

  private static List<String> strings(JsonNode object, String field) {
    return object.path(field).valueStream().map(JsonNode::asText).toList();
  }

  private static String requiredText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("required text is missing");
    }
    return value;
  }

  private static void writeOutput(
      Path output, ReportInput input, BusinessReportPublication publication) throws IOException {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "live-luna-automatic-user-lifecycle-report-v1");
    root.put("sourceCommit", requiredText(System.getProperty("sourceanalysis.liveLunaCommit")));
    root.put("model", "gpt-5.6-luna");
    root.put("reasoningEffort", "high");
    root.put("activityCount", input.knowledge().activities().size());
    root.put("processCount", input.knowledge().processes().size());
    root.put("documentMarkdown", publication.documentMarkdown());
    root.set("businessReport", reportJson(publication.businessReport()));
    Files.write(output, new CanonicalJsonCodec().encodeCanonical(root).copyToByteArray());
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

  private record ReportInput(
      RepositoryBusinessKnowledge knowledge, List<SourceReference> sourceReferences) {}
}
