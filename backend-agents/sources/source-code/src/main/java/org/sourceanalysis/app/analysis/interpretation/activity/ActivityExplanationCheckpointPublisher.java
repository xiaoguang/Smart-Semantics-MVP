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
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Installs the two durable outputs of an already-reviewed local activity explanation pass. */
final class ActivityExplanationCheckpointPublisher {

  private static final String COVERAGE_TYPE = "FLOW_INTERPRETATION_ACTIVITY_COVERAGE";
  private static final String COVERAGE_SCHEMA = "flow-interpretation-activity-coverage-v1";
  private static final String EXPLANATIONS_TYPE = "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS";
  private static final String EXPLANATIONS_SCHEMA = "flow-interpretation-activity-explanations-v1";
  private static final Comparator<String> UTF8_ORDER =
      (left, right) -> {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < length; index++) {
          int comparison =
              Integer.compare(
                  Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
          if (comparison != 0) {
            return comparison;
          }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
      };

  private final CanonicalModuleArtifactStore artifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  ActivityExplanationCheckpointPublisher(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  ModulePublicationReference publish(
      BusinessMaterialBuildResult materials,
      List<ReviewedActivity> activities,
      List<ActivityEntryCoverage> coverage) {
    ReopenedModulePublication materialCheckpoint = artifacts.reopen(materials.checkpoint());
    List<ArtifactReference> upstream =
        materialCheckpoint.receipt().payloadArtifacts().stream()
            .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    List<String> gaps =
        coverage.stream()
            .filter(value -> "NOT_ANALYZED".equals(value.disposition()))
            .map(ActivityEntryCoverage::reasonCode)
            .distinct()
            .sorted(UTF8_ORDER)
            .toList();
    InstalledModulePublication installed =
        artifacts.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    materials.checkpoint().address().runId(),
                    AnalysisStepKey.FLOW_INTERPRETATION,
                    11,
                    "activity-explainer"),
                "v1",
                upstream,
                materialCheckpoint.receipt().controls(),
                gaps.isEmpty()
                    ? ModuleCompletionStatus.SUCCEEDED
                    : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS,
                gaps,
                List.of(coveragePayload(coverage), explanationsPayload(activities))));
    return installed.reference();
  }

  private CanonicalModulePayload coveragePayload(List<ActivityEntryCoverage> coverage) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", COVERAGE_SCHEMA);
    value.put("artifactType", COVERAGE_TYPE);
    ArrayNode entries = value.putArray("entryCoverage");
    coverage.forEach(entry -> coverageJson(entries.addObject(), entry));
    value.put(
        "semanticDeliveryStatus",
        coverage.stream().anyMatch(entry -> "NOT_ANALYZED".equals(entry.disposition()))
            ? "PARTIAL"
            : "READY_FOR_PROCESS_EXPLANATION");
    String id = standaloneId("activity-coverage", COVERAGE_SCHEMA, COVERAGE_TYPE, value);
    value.put("artifactId", id);
    return new CanonicalModulePayload(
        "activity-coverage.json",
        COVERAGE_TYPE,
        COVERAGE_SCHEMA,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(value));
  }

  private CanonicalModulePayload explanationsPayload(List<ReviewedActivity> activities) {
    StringBuilder values = new StringBuilder();
    activities.stream()
        .sorted(Comparator.comparing(ReviewedActivity::activityId, UTF8_ORDER))
        .forEach(
            activity ->
                values
                    .append(
                        new String(
                            canonicalJson.encodeCanonical(activityJson(activity)).copyToByteArray(),
                            StandardCharsets.UTF_8))
                    .append('\n'));
    byte[] bytes = values.toString().getBytes(StandardCharsets.UTF_8);
    String id = jsonlId("activity-explanations", EXPLANATIONS_SCHEMA, EXPLANATIONS_TYPE, bytes);
    return new CanonicalModulePayload(
        "activity-explanations.jsonl",
        EXPLANATIONS_TYPE,
        EXPLANATIONS_SCHEMA,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private ObjectNode activityJson(ReviewedActivity activity) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("recordType", "REVIEWED_ACTIVITY");
    value.put("schemaVersion", EXPLANATIONS_SCHEMA);
    value.put("activityId", activity.activityId());
    value.put("materialId", activity.materialId());
    strings(value.putArray("entryIds"), activity.entryIds());
    value.put("name", activity.name());
    value.put("businessPurpose", activity.businessPurpose());
    strings(value.putArray("participants"), activity.participants());
    strings(value.putArray("businessObjects"), activity.businessObjects());
    strings(value.putArray("triggerOrInput"), activity.triggerOrInput());
    strings(value.putArray("conditions"), activity.conditions());
    strings(value.putArray("activitySteps"), activity.activitySteps());
    strings(value.putArray("codeDefinedResults"), activity.codeDefinedResults());
    strings(value.putArray("businessRules"), activity.businessRules());
    strings(value.putArray("formulasOrMetrics"), activity.formulasOrMetrics());
    strings(value.putArray("terms"), activity.terms());
    value.put("certainty", activity.certainty());
    strings(value.putArray("sourceRefs"), activity.sourceRefs());
    strings(value.putArray("questions"), activity.questions());
    strings(value.putArray("scopeLimitations"), activity.scopeLimitations());
    return value;
  }

  private static void coverageJson(ObjectNode value, ActivityEntryCoverage coverage) {
    value.put("entryId", coverage.entryId());
    value.put("disposition", coverage.disposition());
    strings(value.putArray("activityIds"), coverage.activityIds());
    if (coverage.reasonCode() == null) {
      value.putNull("reasonCode");
    } else {
      value.put("reasonCode", coverage.reasonCode());
    }
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
  }

  private String standaloneId(String prefix, String schema, String type, ObjectNode value) {
    ObjectNode withoutId = value.deepCopy();
    withoutId.remove("artifactId");
    return prefix
        + ":"
        + sha256(
            frame("canonical-standalone-json-artifact-id-v1"),
            frame(schema),
            frame(type),
            frame(canonicalJson.encodeCanonical(withoutId)));
  }

  private static String jsonlId(String prefix, String schema, String type, byte[] bytes) {
    return prefix
        + ":"
        + sha256(frame("canonical-jsonl-artifact-id-v1"), frame(schema), frame(type), frame(bytes));
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(ImmutableBytes value) {
    return frame(value.copyToByteArray());
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
