package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Test-only original-shape M11 v3 installer; production deliberately has no v3 writer. */
final class ActivityExplanationHistoricalV3Fixture {

  private static final String COVERAGE_TYPE = "FLOW_INTERPRETATION_ACTIVITY_COVERAGE";
  private static final String COVERAGE_SCHEMA = "flow-interpretation-activity-coverage-v3";
  private static final String EXPLANATIONS_TYPE = "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS";
  private static final String EXPLANATIONS_SCHEMA = "flow-interpretation-activity-explanations-v2";

  private ActivityExplanationHistoricalV3Fixture() {}

  static ModulePublicationReference install(
      CanonicalModuleArtifactStore modules,
      CanonicalJsonCodec canonicalJson,
      ArtifactControls controls,
      AnalysisRunId runId,
      List<ReviewedActivity> activities,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedEntries) {
    ObjectNode coverageValue = JsonNodeFactory.instance.objectNode();
    coverageValue.put("schemaVersion", COVERAGE_SCHEMA);
    coverageValue.put("artifactType", COVERAGE_TYPE);
    ArrayNode entries = coverageValue.putArray("entryCoverage");
    coverage.stream()
        .sorted(Comparator.comparing(ActivityEntryCoverage::entryId))
        .forEach(
            entry -> {
              ObjectNode value = entries.addObject();
              value.put("entryId", entry.entryId());
              value.put("disposition", entry.disposition());
              value.set("activityIds", strings(entry.activityIds()));
              if (entry.reasonCode() == null) {
                value.putNull("reasonCode");
              } else {
                value.put("reasonCode", entry.reasonCode());
              }
            });
    ArrayNode unexplained = coverageValue.putArray("unexplainedActivityEntries");
    unexplainedEntries.stream()
        .sorted(Comparator.comparing(UnexplainedActivityEntry::entryId))
        .forEach(
            entry -> {
              ObjectNode value = unexplained.addObject();
              value.put("entryId", entry.entryId());
              value.put("materialId", entry.materialId());
              value.put("entryKey", entry.entryKey());
              value.put("materialContext", entry.materialContext());
              value.put("reasonCode", entry.reasonCode());
            });
    boolean partial =
        coverage.stream()
                .anyMatch(
                    entry ->
                        "NOT_ANALYZED".equals(entry.disposition())
                            || entry.requiredScopeIncomplete())
            || !unexplainedEntries.isEmpty();
    coverageValue.put(
        "semanticDeliveryStatus", partial ? "PARTIAL" : "READY_FOR_PROCESS_EXPLANATION");
    String coverageId = standaloneId(canonicalJson, coverageValue, COVERAGE_SCHEMA, COVERAGE_TYPE);
    coverageValue.put("artifactId", coverageId);
    ImmutableBytes coverageBytes = canonicalJson.encodeCanonical(coverageValue);

    StringBuilder explanationLines = new StringBuilder();
    activities.stream()
        .sorted(Comparator.comparing(ReviewedActivity::activityId))
        .forEach(
            activity ->
                explanationLines
                    .append(
                        new String(
                            canonicalJson
                                .encodeCanonical(activityValue(activity))
                                .copyToByteArray(),
                            StandardCharsets.UTF_8))
                    .append('\n'));
    byte[] explanationBytes = explanationLines.toString().getBytes(StandardCharsets.UTF_8);
    String explanationsId =
        "activity-explanations:"
            + digest(
                concat(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(EXPLANATIONS_SCHEMA),
                    frame(EXPLANATIONS_TYPE),
                    frame(explanationBytes)));
    ImmutableBytes explanationPayload = ImmutableBytes.copyOf(explanationBytes);
    CanonicalModulePayload coveragePayload =
        new CanonicalModulePayload(
            "activity-coverage.json",
            COVERAGE_TYPE,
            COVERAGE_SCHEMA,
            ArtifactId.parse(coverageId),
            CanonicalMediaType.APPLICATION_JSON,
            coverageBytes);
    CanonicalModulePayload explanationsPayload =
        new CanonicalModulePayload(
            "activity-explanations.jsonl",
            EXPLANATIONS_TYPE,
            EXPLANATIONS_SCHEMA,
            ArtifactId.parse(explanationsId),
            CanonicalMediaType.APPLICATION_X_NDJSON,
            explanationPayload);
    return modules
        .install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    runId, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer"),
                "v3",
                List.of(),
                controls,
                partial
                    ? ModuleCompletionStatus.SUCCEEDED_WITH_GAPS
                    : ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(coveragePayload, explanationsPayload)))
        .reference();
  }

  static ModulePublicationReference installV4WithoutPacketCompletion(
      CanonicalModuleArtifactStore modules,
      CanonicalJsonCodec canonicalJson,
      ArtifactControls controls,
      AnalysisRunId runId) {
    ObjectNode coverageValue = JsonNodeFactory.instance.objectNode();
    coverageValue.put("schemaVersion", "flow-interpretation-activity-coverage-v4");
    coverageValue.put("artifactType", COVERAGE_TYPE);
    coverageValue.putArray("entryCoverage");
    coverageValue.putArray("unexplainedActivityEntries");
    coverageValue.put("semanticDeliveryStatus", "READY_FOR_PROCESS_EXPLANATION");
    String coverageSchema = "flow-interpretation-activity-coverage-v4";
    String coverageId = standaloneId(canonicalJson, coverageValue, coverageSchema, COVERAGE_TYPE);
    coverageValue.put("artifactId", coverageId);
    ImmutableBytes coverageBytes = canonicalJson.encodeCanonical(coverageValue);

    byte[] explanationBytes = new byte[0];
    String explanationsId =
        "activity-explanations:"
            + digest(
                concat(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(EXPLANATIONS_SCHEMA),
                    frame(EXPLANATIONS_TYPE),
                    frame(explanationBytes)));
    return modules
        .install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    runId, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer"),
                "v4",
                List.of(),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(
                    new CanonicalModulePayload(
                        "activity-coverage.json",
                        COVERAGE_TYPE,
                        coverageSchema,
                        ArtifactId.parse(coverageId),
                        CanonicalMediaType.APPLICATION_JSON,
                        coverageBytes),
                    new CanonicalModulePayload(
                        "activity-explanations.jsonl",
                        EXPLANATIONS_TYPE,
                        EXPLANATIONS_SCHEMA,
                        ArtifactId.parse(explanationsId),
                        CanonicalMediaType.APPLICATION_X_NDJSON,
                        ImmutableBytes.copyOf(explanationBytes)))))
        .reference();
  }

  private static ObjectNode activityValue(ReviewedActivity activity) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("recordType", "REVIEWED_ACTIVITY");
    value.put("schemaVersion", EXPLANATIONS_SCHEMA);
    value.put("activityId", activity.activityId());
    value.put("materialId", activity.materialId());
    value.set("entryIds", strings(activity.entryIds()));
    value.put("name", activity.name());
    value.put("businessPurpose", activity.businessPurpose());
    value.set("participants", strings(activity.participants()));
    value.set("businessObjects", strings(activity.businessObjects()));
    value.set("triggerOrInput", strings(activity.triggerOrInput()));
    value.set("conditions", strings(activity.conditions()));
    value.set("activitySteps", strings(activity.activitySteps()));
    value.set("codeDefinedResults", strings(activity.codeDefinedResults()));
    value.set("businessRules", strings(activity.businessRules()));
    value.set("formulasOrMetrics", strings(activity.formulasOrMetrics()));
    value.set("terms", strings(activity.terms()));
    value.put("certainty", activity.certainty());
    value.set("sourceRefs", strings(activity.sourceRefs()));
    value.set("questions", strings(activity.questions()));
    value.set("scopeLimitations", strings(activity.scopeLimitations()));
    value.put("materialSource", activity.materialSource());
    if (activity.sliceKey() == null) {
      value.putNull("sliceKey");
    } else {
      value.put("sliceKey", activity.sliceKey());
    }
    ObjectNode originalSourceRefs = value.putObject("originalSourceRefs");
    activity.originalSourceRefs().forEach(originalSourceRefs::put);
    return value;
  }

  private static ArrayNode strings(List<String> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(result::add);
    return result;
  }

  private static String standaloneId(
      CanonicalJsonCodec canonicalJson, ObjectNode value, String schema, String type) {
    return "activity-coverage:"
        + digest(
            concat(
                frame("canonical-standalone-json-artifact-id-v1"),
                frame(schema),
                frame(type),
                frame(canonicalJson.encodeCanonical(value).copyToByteArray())));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return concat(
        ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value.length).array(),
        value);
  }

  private static byte[] concat(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
