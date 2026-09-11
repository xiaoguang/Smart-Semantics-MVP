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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionProfile;
import org.sourceanalysis.app.adapter.provider.CodexSubscriptionStructuredProvider;
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
 * Explicit, opt-in process-quality sample over two already completed customer-source activities.
 *
 * <p>It never restarts the activity DRAFT/REVIEW calls. The original material set is supplied only
 * to give {@link ProcessExplainer} program-owned recall cues; neither cue approves a business
 * sequence nor a process identity.
 */
class LiveLunaAutomaticUserLifecycleProcessIT {

  private static final String EXECUTABLE = "/Applications/ChatGPT.app/Contents/Resources/codex";
  private static final String REGISTRATION = "HTTP POST /user/registerUser";
  private static final String LOGIN = "HTTP POST /user/login";

  @TempDir Path temporaryDirectory;

  @Test
  void readsPersistedActivitiesAndTheirOriginalMaterialsWithoutCallingAProvider() throws Exception {
    Path materials = temporaryDirectory.resolve("business-materials.jsonl");
    Files.writeString(
        materials,
        materialLine("material:registration", "entry:registration", REGISTRATION, "S1")
            + System.lineSeparator()
            + materialLine("material:login", "entry:login", LOGIN, "S2")
            + System.lineSeparator(),
        StandardCharsets.UTF_8);
    Path registration = temporaryDirectory.resolve("registration.json");
    Path login = temporaryDirectory.resolve("login.json");
    Files.write(
        registration,
        activityOutput(
            "material:registration", "activity:registration", "entry:registration", "用户注册", "S1"));
    Files.write(
        login, activityOutput("material:login", "activity:login", "entry:login", "用户登录", "S2"));

    ProcessInput input = loadInput(materials, List.of(registration, login));

    assertThat(input.activities().reviewedActivities())
        .extracting(ReviewedActivity::activityId)
        .containsExactly("activity:login", "activity:registration");
    assertThat(input.activities().coverage())
        .extracting(ActivityEntryCoverage::entryId)
        .containsExactly("entry:login", "entry:registration");
    assertThat(input.materials().materials())
        .extracting(BusinessMaterial::materialId)
        .containsExactly("material:login", "material:registration");
    assertThat(input.materials().materials())
        .allSatisfy(material -> assertThat(material.sourceRefs()).hasSize(1));
  }

  @Test
  void preservesTwoCoveredActivitiesFromOneGroupedMaterialWithoutCallingAProvider()
      throws Exception {
    Path materials = temporaryDirectory.resolve("grouped-business-materials.jsonl");
    Files.writeString(
        materials,
        materialLine(
                "material:grouped-user-lifecycle",
                "entry:registration",
                REGISTRATION + "\n" + LOGIN,
                "S1")
            + System.lineSeparator(),
        StandardCharsets.UTF_8);
    Path registration = temporaryDirectory.resolve("grouped-registration.json");
    Path login = temporaryDirectory.resolve("grouped-login.json");
    Files.write(
        registration,
        activityOutput(
            "material:grouped-user-lifecycle",
            "activity:registration",
            "entry:registration",
            "用户注册",
            "S1"));
    Files.write(
        login,
        activityOutput(
            "material:grouped-user-lifecycle", "activity:login", "entry:login", "用户登录", "S1"));

    ProcessInput input = loadInput(materials, List.of(registration, login));

    assertThat(input.activities().reviewedActivities()).hasSize(2);
    assertThat(input.activities().coverage()).hasSize(2);
    assertThat(input.materials().materials())
        .extracting(BusinessMaterial::materialId)
        .containsExactly("material:grouped-user-lifecycle");
  }

  @Test
  @EnabledIfSystemProperty(named = "sourceanalysis.liveLunaUserLifecycleProcess", matches = "true")
  void reconstructsOneUserLifecycleHypothesisFromTwoCompletedAutomaticActivities()
      throws Exception {
    Path materials = requiredFile("sourceanalysis.liveLunaMaterials");
    Path registration = requiredFile("sourceanalysis.liveLunaRegistrationActivity");
    Path login = requiredFile("sourceanalysis.liveLunaLoginActivity");
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    ProcessInput input = loadInput(materials, List.of(registration, login));
    RepositoryBusinessKnowledge knowledge =
        new ProcessExplainer(
                new CodexSubscriptionStructuredProvider(
                    new CodexSubscriptionProfile(
                        Path.of(EXECUTABLE), "gpt-5.6-luna", "high", Duration.ofMinutes(3))))
            .explain(
                new ExplainRepositoryProcessesRequest(
                    input.activities(),
                    input.materials(),
                    new ProcessExplanationProfile(2, 1, 32_000, 16_000, 2, 32, 2_000)));

    writeOutput(
        outputDirectory.resolve("live-luna-automatic-user-lifecycle-process.json"),
        input,
        knowledge);

    assertThat(knowledge.processes()).hasSize(2);
    assertThat(knowledge.processes())
        .flatExtracting(BusinessProcess::activityIds)
        .containsExactlyInAnyOrder(
            "activity:dba8b3c4ec75d40362d77062c476ba7e6590ba694c863faa3a8c061e9e130f55",
            "activity:6dfdb9463b3e53dfdcd01b5d045a596abb9cf1248608bba6f71ee2bf3cfdeeb3");
    assertThat(knowledge.processes())
        .allSatisfy(
            process -> {
              assertThat(process.certainty())
                  .isIn("DIRECT_CODE_BEHAVIOR", "REASONABLE_INFERENCE", "NEEDS_CONFIRMATION");
              assertThat(process.confirmationNotes()).isNotEmpty();
            });
  }

  static ProcessInput loadInput(Path materialsFile, List<Path> activityOutputs) throws IOException {
    return loadInput(materialsFile, activityOutputs, List.of(REGISTRATION, LOGIN));
  }

  static ProcessInput loadInput(
      Path materialsFile, List<Path> activityOutputs, List<String> expectedEntryContexts)
      throws IOException {
    Map<String, BusinessMaterial> materialsById = loadMaterials(materialsFile, expectedEntryContexts);
    List<ReviewedActivity> activities = new ArrayList<>();
    List<ActivityEntryCoverage> coverage = new ArrayList<>();
    for (Path output : activityOutputs) {
      JsonNode root = parseFile(output);
      String materialId = requiredText(root, "materialId");
      if (!materialsById.containsKey(materialId)) {
        throw new IllegalArgumentException("LIVE_LUNA_PROCESS_MATERIAL_NOT_FOUND:" + materialId);
      }
      Map<String, String> entryByActivity = coverageByActivity(root.path("coverage"));
      for (JsonNode activity : root.path("reviewedActivities")) {
        String activityId = requiredText(activity, "activityId");
        String entryId = entryByActivity.get(activityId);
        if (entryId == null) {
          throw new IllegalArgumentException(
              "LIVE_LUNA_PROCESS_ACTIVITY_COVERAGE_MISSING:" + activityId);
        }
        activities.add(readActivity(activity, materialId, entryId));
      }
      for (JsonNode value : root.path("coverage")) {
        coverage.add(
            new ActivityEntryCoverage(
                requiredText(value, "entryId"),
                requiredText(value, "disposition"),
                strings(value, "activityIds"),
                value.path("reasonCode").isNull() ? null : value.path("reasonCode").asText()));
      }
    }
    activities.sort(java.util.Comparator.comparing(ReviewedActivity::activityId));
    coverage.sort(java.util.Comparator.comparing(ActivityEntryCoverage::entryId));
    List<BusinessMaterial> selectedMaterials =
        activities.stream()
            .map(ReviewedActivity::materialId)
            .distinct()
            .map(materialsById::get)
            .sorted(java.util.Comparator.comparing(BusinessMaterial::materialId))
            .toList();
    return new ProcessInput(
        new ActivityExplanationResult(activities, coverage),
        new BusinessMaterialSet(
            "business-material-set:live-user-lifecycle", selectedMaterials, List.of()));
  }

  private static Map<String, BusinessMaterial> loadMaterials(
      Path materialsFile, List<String> expectedEntryContexts)
      throws IOException {
    if (expectedEntryContexts.isEmpty() || expectedEntryContexts.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("LIVE_LUNA_PROCESS_ENTRY_CONTEXT_INVALID");
    }
    Map<String, BusinessMaterial> byId = new HashMap<>();
    try (Stream<String> lines = Files.lines(materialsFile)) {
      lines
          .filter(line -> !line.isBlank())
          .map(LiveLunaAutomaticUserLifecycleProcessIT::parseLine)
          .filter(record -> "BUSINESS_MATERIAL".equals(record.path("recordType").asText()))
          .filter(
              record -> {
                String context = record.path("modelPacket").path("context").asText();
                return expectedEntryContexts.stream().anyMatch(context::contains);
              })
          .map(LiveLunaAutomaticUserLifecycleProcessIT::readMaterial)
          .forEach(material -> byId.put(material.materialId(), material));
    }
    if (expectedEntryContexts.stream()
        .anyMatch(
            context ->
                byId.values().stream()
                    .noneMatch(material -> material.modelPacket().context().contains(context)))) {
      throw new IllegalArgumentException("LIVE_LUNA_PROCESS_REQUIRED_MATERIALS_MISSING");
    }
    return Map.copyOf(byId);
  }

  private static JsonNode parseLine(String line) {
    return new CanonicalJsonCodec()
        .parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
  }

  private static JsonNode parseFile(Path file) throws IOException {
    return new CanonicalJsonCodec().parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(file)));
  }

  private static BusinessMaterial readMaterial(JsonNode selected) {
    JsonNode packet = selected.path("modelPacket");
    List<SourceReference> sourceRefs =
        selected
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
        requiredText(selected, "materialId"),
        strings(selected, "entryIds"),
        BusinessMaterialMode.valueOf(requiredText(selected, "materialMode")),
        requiredText(selected, "context"),
        strings(selected, "technicalObservations"),
        sourceRefs,
        strings(selected, "flowRefs"),
        strings(selected, "technicalProofRefs"),
        strings(selected, "limitations"),
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

  private static ReviewedActivity readActivity(JsonNode value, String materialId, String entryId) {
    return new ReviewedActivity(
        requiredText(value, "activityId"),
        materialId,
        List.of(entryId),
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

  private static Map<String, String> coverageByActivity(JsonNode coverage) {
    Map<String, String> result = new HashMap<>();
    for (JsonNode value : coverage) {
      String entryId = requiredText(value, "entryId");
      for (String activityId : strings(value, "activityIds")) {
        if (result.put(activityId, entryId) != null) {
          throw new IllegalArgumentException("LIVE_LUNA_PROCESS_ACTIVITY_COVERAGE_DUPLICATE");
        }
      }
    }
    return Map.copyOf(result);
  }

  static void writeOutput(
      Path output, ProcessInput input, RepositoryBusinessKnowledge knowledge) throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "live-luna-automatic-process-sample-v1");
    root.put("sourceCommit", requiredText(System.getProperty("sourceanalysis.liveLunaCommit")));
    root.put("model", "gpt-5.6-luna");
    root.put("reasoningEffort", "high");
    ArrayNode activityValues = root.putArray("inputActivities");
    input
        .activities()
        .reviewedActivities()
        .forEach(activity -> activityJson(activityValues.addObject(), activity));
    ArrayNode processValues = root.putArray("reviewedProcesses");
    knowledge.processes().forEach(process -> processJson(processValues.addObject(), process));
    strings(root.putArray("unmatchedActivityIds"), knowledge.unmatchedActivityIds());
    strings(root.putArray("confirmationTopics"), knowledge.confirmationTopics());
    Files.write(output, canonicalJson.encodeCanonical(root).copyToByteArray());
  }

  private static void activityJson(ObjectNode target, ReviewedActivity activity) {
    target.put("activityId", activity.activityId());
    target.put("name", activity.name());
    target.put("businessPurpose", activity.businessPurpose());
    strings(target.putArray("businessObjects"), activity.businessObjects());
    strings(target.putArray("conditions"), activity.conditions());
    strings(target.putArray("activitySteps"), activity.activitySteps());
    strings(target.putArray("codeDefinedResults"), activity.codeDefinedResults());
    strings(target.putArray("sourceRefs"), activity.sourceRefs());
    strings(target.putArray("scopeLimitations"), activity.scopeLimitations());
  }

  private static void processJson(ObjectNode target, BusinessProcess process) {
    target.put("processId", process.processId());
    target.put("name", process.name());
    target.put("businessPurpose", process.businessPurpose());
    strings(target.putArray("activityIds"), process.activityIds());
    ArrayNode stages = target.putArray("stages");
    process
        .stages()
        .forEach(
            stage ->
                stages
                    .addObject()
                    .put("order", stage.order())
                    .put("activityId", stage.activityId())
                    .put("description", stage.description()));
    strings(target.putArray("branches"), process.branches());
    strings(target.putArray("sharedObjects"), process.sharedObjects());
    strings(target.putArray("codeDefinedResults"), process.codeDefinedResults());
    target.put("certainty", process.certainty());
    strings(target.putArray("sourceRefs"), process.sourceRefs());
    strings(target.putArray("confirmationNotes"), process.confirmationNotes());
  }

  private static byte[] activityOutput(
      String materialId, String activityId, String entryId, String name, String ref) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("materialId", materialId);
    ObjectNode activity = root.putArray("reviewedActivities").addObject();
    activity.put("activityId", activityId);
    activity.put("name", name);
    activity.put("businessPurpose", name + "的局部活动。");
    strings(activity.putArray("participants"), List.of());
    strings(activity.putArray("businessObjects"), List.of("用户账户"));
    strings(activity.putArray("triggerOrInput"), List.of(name + "请求"));
    strings(activity.putArray("conditions"), List.of());
    strings(activity.putArray("activitySteps"), List.of("处理" + name));
    strings(activity.putArray("codeDefinedResults"), List.of("返回处理结果"));
    strings(activity.putArray("businessRules"), List.of());
    strings(activity.putArray("formulasOrMetrics"), List.of());
    strings(activity.putArray("terms"), List.of("用户账户"));
    activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
    strings(activity.putArray("sourceRefs"), List.of(ref));
    strings(activity.putArray("questions"), List.of());
    strings(activity.putArray("scopeLimitations"), List.of());
    ObjectNode coverage = root.putArray("coverage").addObject();
    coverage.put("entryId", entryId);
    coverage.put("disposition", "ANALYZED");
    strings(coverage.putArray("activityIds"), List.of(activityId));
    coverage.putNull("reasonCode");
    return new CanonicalJsonCodec().encodeCanonical(root).copyToByteArray();
  }

  private static String materialLine(
      String materialId, String entryId, String context, String ref) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("recordType", "BUSINESS_MATERIAL");
    root.put("materialId", materialId);
    strings(root.putArray("entryIds"), List.of(entryId));
    root.put("materialMode", "FLOW_PREFERRED");
    root.put("context", context);
    strings(root.putArray("technicalObservations"), List.of("一个安全定位的入口"));
    ObjectNode sourceRef = root.putArray("sourceRefs").addObject();
    sourceRef.put("ref", ref);
    sourceRef.put("file", "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java");
    sourceRef.put("startLine", 1);
    sourceRef.put("endLine", 1);
    sourceRef.put("snippet", "@PostMapping");
    root.putArray("flowRefs");
    root.putArray("technicalProofRefs");
    root.putArray("limitations");
    ObjectNode packet = root.putObject("modelPacket");
    packet.put("context", context);
    strings(packet.putArray("technicalObservations"), List.of("一个安全定位的入口"));
    ObjectNode allowed = packet.putArray("allowlistedRefs").addObject();
    allowed.put("ref", ref);
    allowed.put("snippet", "@PostMapping");
    packet.putArray("limitations");
    return new String(
        new CanonicalJsonCodec().encodeCanonical(root).copyToByteArray(), StandardCharsets.UTF_8);
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

  private static String requiredText(JsonNode node, String field) {
    return requiredText(node.path(field).asText());
  }

  private static String requiredText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("required text is missing");
    }
    return value;
  }

  private static List<String> strings(JsonNode value, String field) {
    return value.path(field).valueStream().map(JsonNode::asText).toList();
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  record ProcessInput(ActivityExplanationResult activities, BusinessMaterialSet materials) {}
}
