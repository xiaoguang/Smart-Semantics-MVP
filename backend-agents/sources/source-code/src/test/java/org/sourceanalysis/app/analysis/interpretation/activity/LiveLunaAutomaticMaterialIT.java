package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
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
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Explicit, one-packet live quality check for a material produced by {@code BusinessMaterialBuilder}.
 *
 * <p>Surefire does not select {@code *IT}; callers must explicitly opt in and name the persisted
 * material JSONL file. The model receives only {@link ModelActivityPacket}, never its source
 * locations or any artifact identity.
 */
class LiveLunaAutomaticMaterialIT {

  private static final String EXECUTABLE = "/Applications/ChatGPT.app/Contents/Resources/codex";
  private static final String ZERO = "0".repeat(64);

  @Test
  void explainsOneInspectedAutomaticDepotHeadPacketThroughRealLunaHigh() throws Exception {
    requireExact("sourceanalysis.liveLuna", "true");
    requireExact("sourceanalysis.liveLunaSample", "automatic-depothead");
    Path materialsFile = requiredFile("sourceanalysis.liveLunaMaterials");
    Path outputDirectory = requiredDirectory("sourceanalysis.liveLunaOutput");
    assertThat(outputDirectory.normalize().toString())
        .as("diagnostics remain in the ignored workspace")
        .contains("/.workspace/");

    BusinessMaterial material = automaticDepotHeadMaterial(materialsFile);
    BusinessMaterialBuildResult materials =
        new BusinessMaterialBuildResult(
            new BusinessMaterialSet(
                "business-material-set:live-automatic-depothead",
                List.of(material),
                List.of(
                    new BusinessMaterialEntryCoverage(
                        material.entryIds().get(0),
                        "MATERIAL_WITH_GAPS",
                        material.materialId(),
                        "ENTRY_SOURCE_FALLBACK"))),
            placeholderCheckpoint());

    ActivityExplanationResult explanation =
        new ActivityExplainer(
                new CodexSubscriptionStructuredProvider(
                    new CodexSubscriptionProfile(
                        Path.of(EXECUTABLE), "gpt-5.6-luna", "high", Duration.ofMinutes(3))))
            .explain(
                new ExplainActivitiesRequest(
                    materials, new ActivityExplanationProfile(20_000, 12_000, 2, 24, 1_000)));

    assertThat(explanation.reviewedActivities()).hasSize(1);
    assertThat(explanation.coverage())
        .allSatisfy(value -> assertThat(value.disposition()).isEqualTo("ANALYZED_WITH_GAPS"));
    writeOutput(
        outputDirectory.resolve("live-luna-automatic-depothead-activity.json"),
        material,
        explanation);
  }

  private static BusinessMaterial automaticDepotHeadMaterial(Path materialsFile) throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    JsonNode selected;
    try (Stream<String> lines = Files.lines(materialsFile)) {
      selected =
          lines
              .filter(line -> !line.isBlank())
              .map(
                  line ->
                      canonicalJson.parseCanonical(
                          ImmutableBytes.copyOf(
                              line.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
              .filter(record -> "BUSINESS_MATERIAL".equals(record.path("recordType").asText()))
              .filter(
                  record ->
                      record.path("modelPacket").path("technicalObservations").toString()
                          .contains("DepotHeadController#batchSetStatus"))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("automatic DepotHead material missing"));
    }
    List<SourceReference> sourceRefs =
        selected.path("sourceRefs").valueStream()
            .map(
                value ->
                    new SourceReference(
                        requiredText(value, "ref"),
                        requiredText(value, "file"),
                        value.path("startLine").asInt(),
                        value.path("endLine").asInt(),
                        requiredText(value, "snippet")))
            .toList();
    JsonNode packet = selected.path("modelPacket");
    ModelActivityPacket modelPacket =
        new ModelActivityPacket(
            requiredText(packet, "context"),
            strings(packet, "technicalObservations"),
            packet.path("allowlistedRefs").valueStream()
                .map(
                    value ->
                        new ModelActivityPacket.AllowlistedReference(
                            requiredText(value, "ref"), requiredText(value, "snippet")))
                .toList(),
            strings(packet, "limitations"));
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
        modelPacket);
  }

  private static void writeOutput(
      Path output, BusinessMaterial material, ActivityExplanationResult explanation)
      throws IOException {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", "live-luna-automatic-activity-sample-v1");
    root.put("sourceCommit", requiredText(System.getProperty("sourceanalysis.liveLunaCommit")));
    root.put("model", "gpt-5.6-luna");
    root.put("reasoningEffort", "high");
    root.put("materialId", material.materialId());
    root.set("cleanModelPacket", packetJson(material.modelPacket()));
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
    ArrayNode sourceReferences = root.putArray("programSideSourceReferences");
    material
        .sourceRefs()
        .forEach(
            reference -> {
              ObjectNode item = sourceReferences.addObject();
              item.put("ref", reference.ref());
              item.put("file", reference.file());
              item.put("startLine", reference.startLine());
              item.put("endLine", reference.endLine());
              item.put("snippet", reference.snippet());
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
            reference -> {
              ObjectNode item = refs.addObject();
              item.put("ref", reference.ref());
              item.put("snippet", reference.snippet());
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

  private static List<String> strings(JsonNode node, String field) {
    return node.path(field).valueStream().map(JsonNode::asText).toList();
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
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

  private static void requireExact(String property, String expected) {
    assertThat(System.getProperty(property)).as(property).isEqualTo(expected);
  }

  private static String requiredText(JsonNode node, String field) {
    return requiredText(node.path(field).asText());
  }

  private static String requiredText(String value) {
    assertThat(value).isNotBlank();
    return value;
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
}
