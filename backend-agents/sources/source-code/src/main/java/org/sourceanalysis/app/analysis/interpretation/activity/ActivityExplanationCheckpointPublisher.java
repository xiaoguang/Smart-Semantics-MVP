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
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Installs the two durable outputs of an already-reviewed local activity explanation pass. */
public final class ActivityExplanationCheckpointPublisher {

  private static final String COVERAGE_TYPE = "FLOW_INTERPRETATION_ACTIVITY_COVERAGE";
  private static final String COVERAGE_SCHEMA = "flow-interpretation-activity-coverage-v2";
  private static final String EXPLANATIONS_TYPE = "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS";
  private static final String EXPLANATIONS_SCHEMA = "flow-interpretation-activity-explanations-v1";
  private static final String STEP05_COVERAGE_SCHEMA = "flow-interpretation-activity-coverage-v4";
  private static final String STEP05_EXPLANATIONS_SCHEMA =
      "flow-interpretation-activity-explanations-v2";
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

  public ActivityExplanationCheckpointPublisher(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  ModulePublicationReference publish(
      BusinessMaterialBuildResult materials,
      List<ReviewedActivity> activities,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries) {
    return publish(
        materials.checkpoint().address().runId(),
        materials,
        activities,
        coverage,
        unexplainedActivityEntries);
  }

  ModulePublicationReference publish(
      AnalysisRunId outputRunId,
      BusinessMaterialBuildResult materials,
      List<ReviewedActivity> activities,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries) {
    Objects.requireNonNull(outputRunId, "activity output run ID");
    ReopenedModulePublication materialCheckpoint = artifacts.reopen(materials.checkpoint());
    List<ArtifactReference> upstream =
        materialCheckpoint.receipt().payloadArtifacts().stream()
            .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    List<String> gaps =
        coverage.stream()
            .filter(
                value ->
                    "NOT_ANALYZED".equals(value.disposition()) || value.requiredScopeIncomplete())
            .map(ActivityEntryCoverage::reasonCode)
            .distinct()
            .sorted(UTF8_ORDER)
            .toList();
    InstalledModulePublication installed =
        artifacts.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    outputRunId, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer"),
                "v2",
                upstream,
                materialCheckpoint.receipt().controls(),
                gaps.isEmpty()
                    ? ModuleCompletionStatus.SUCCEEDED
                    : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS,
                gaps,
                List.of(
                    coveragePayload(coverage, unexplainedActivityEntries),
                    explanationsPayload(activities))));
    return installed.reference();
  }

  /** Publishes Step05-derived activities without altering or re-running the source checkpoint. */
  public ModulePublicationReference publishStep05(
      AnalysisRunId outputRunId,
      AnalysisStepPublicationReference materialCheckpoint,
      CanonicalAnalysisStepArtifactStore steps,
      ArtifactControls outputControls,
      CodeReadingMaterialSet materials,
      List<ReviewedActivity> activities,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries,
      List<ActivityPacketCompletion> packetCompletion) {
    Objects.requireNonNull(outputRunId, "activity output run ID");
    Objects.requireNonNull(materialCheckpoint, "Step05 material checkpoint");
    Objects.requireNonNull(steps, "analysis step artifact store");
    Objects.requireNonNull(outputControls, "activity output controls");
    Objects.requireNonNull(materials, "Step05 materials");
    packetCompletion = List.copyOf(Objects.requireNonNull(packetCompletion, "packet completion"));
    if (materialCheckpoint.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
        || !materialCheckpoint
            .address()
            .runId()
            .equals(materials.header().sourceInventory().publication().address().runId())) {
      throw new IllegalArgumentException("ACTIVITY_STEP05_SOURCE_MISMATCH");
    }
    ReopenedAnalysisStepPublication source = steps.reopen(materialCheckpoint);
    if (!source.reference().equals(materialCheckpoint) || source.semanticPayloads().size() != 1) {
      throw new IllegalArgumentException("ACTIVITY_STEP05_SOURCE_MISMATCH");
    }
    java.util.Map<String, java.util.Set<String>> sourceRefsByPacket =
        materials.packets().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    CodeReadingMaterialSet.Packet::packetId,
                    packet ->
                        packet.sourceReferences().stream()
                            .map(CodeReadingMaterialSet.SourceReference::sourceRef)
                            .collect(java.util.stream.Collectors.toSet())));
    for (ReviewedActivity activity : activities) {
      java.util.Set<String> allowed = sourceRefsByPacket.get(activity.materialId());
      if (!"CODE_READING_MATERIALS".equals(activity.materialSource())
          || allowed == null
          || !allowed.containsAll(activity.originalSourceRefs().values())
          || !activity.originalSourceRefs().keySet().containsAll(activity.sourceRefs())) {
        throw new IllegalArgumentException("ACTIVITY_STEP05_SOURCE_MISMATCH");
      }
    }
    ActivityPacketCompletionVerification.requireExactStep05Packets(materials, packetCompletion);
    ActivityPacketCompletionVerification.requireInternalConsistency(
        packetCompletion, activities, unexplainedActivityEntries);
    List<ArtifactReference> upstream =
        source.receipt().semanticArtifacts().stream()
            .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    List<String> gaps = gaps(coverage, packetCompletion);
    InstalledModulePublication installed =
        artifacts.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    outputRunId, AnalysisStepKey.FLOW_INTERPRETATION, 11, "activity-explainer"),
                "v4",
                upstream,
                outputControls,
                gaps.isEmpty()
                    ? ModuleCompletionStatus.SUCCEEDED
                    : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS,
                gaps,
                List.of(
                    coveragePayload(
                        coverage,
                        unexplainedActivityEntries,
                        STEP05_COVERAGE_SCHEMA,
                        packetCompletion),
                    explanationsPayload(activities, STEP05_EXPLANATIONS_SCHEMA))));
    return installed.reference();
  }

  private CanonicalModulePayload coveragePayload(
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries) {
    return coveragePayload(coverage, unexplainedActivityEntries, COVERAGE_SCHEMA);
  }

  private CanonicalModulePayload coveragePayload(
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries,
      String schema) {
    return coveragePayload(coverage, unexplainedActivityEntries, schema, List.of());
  }

  private CanonicalModulePayload coveragePayload(
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries,
      String schema,
      List<ActivityPacketCompletion> packetCompletion) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", schema);
    value.put("artifactType", COVERAGE_TYPE);
    ArrayNode entries = value.putArray("entryCoverage");
    coverage.forEach(entry -> coverageJson(entries.addObject(), entry));
    ArrayNode unexplained = value.putArray("unexplainedActivityEntries");
    unexplainedActivityEntries.forEach(entry -> unexplainedJson(unexplained.addObject(), entry));
    if (STEP05_COVERAGE_SCHEMA.equals(schema)) {
      ArrayNode completions = value.putArray("packetCompletion");
      packetCompletion.stream()
          .sorted(Comparator.comparing(ActivityPacketCompletion::packetId, UTF8_ORDER))
          .forEach(completion -> completionJson(completions.addObject(), completion));
    }
    value.put(
        "semanticDeliveryStatus",
        coverage.stream()
                    .anyMatch(
                        entry ->
                            "NOT_ANALYZED".equals(entry.disposition())
                                || entry.requiredScopeIncomplete())
                || packetCompletion.stream()
                    .anyMatch(
                        completion ->
                            completion.completion() != ActivityPacketCompletion.Completion.COMPLETE)
            ? "PARTIAL"
            : "READY_FOR_PROCESS_EXPLANATION");
    String id = standaloneId("activity-coverage", schema, COVERAGE_TYPE, value);
    value.put("artifactId", id);
    return new CanonicalModulePayload(
        "activity-coverage.json",
        COVERAGE_TYPE,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(value));
  }

  private CanonicalModulePayload explanationsPayload(List<ReviewedActivity> activities) {
    return explanationsPayload(activities, EXPLANATIONS_SCHEMA);
  }

  private CanonicalModulePayload explanationsPayload(
      List<ReviewedActivity> activities, String schema) {
    StringBuilder values = new StringBuilder();
    activities.stream()
        .sorted(Comparator.comparing(ReviewedActivity::activityId, UTF8_ORDER))
        .forEach(
            activity ->
                values
                    .append(
                        new String(
                            canonicalJson
                                .encodeCanonical(activityJson(activity, schema))
                                .copyToByteArray(),
                            StandardCharsets.UTF_8))
                    .append('\n'));
    byte[] bytes = values.toString().getBytes(StandardCharsets.UTF_8);
    String id = jsonlId("activity-explanations", schema, EXPLANATIONS_TYPE, bytes);
    return new CanonicalModulePayload(
        "activity-explanations.jsonl",
        EXPLANATIONS_TYPE,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private ObjectNode activityJson(ReviewedActivity activity, String schema) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("recordType", "REVIEWED_ACTIVITY");
    value.put("schemaVersion", schema);
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
    if (STEP05_EXPLANATIONS_SCHEMA.equals(schema)) {
      value.put("materialSource", activity.materialSource());
      if (activity.sliceKey() == null) {
        value.putNull("sliceKey");
      } else {
        value.put("sliceKey", activity.sliceKey());
      }
      ObjectNode mapping = value.putObject("originalSourceRefs");
      activity.originalSourceRefs().forEach(mapping::put);
    }
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

  private static void unexplainedJson(ObjectNode value, UnexplainedActivityEntry entry) {
    value.put("entryId", entry.entryId());
    value.put("materialId", entry.materialId());
    value.put("entryKey", entry.entryKey());
    value.put("materialContext", entry.materialContext());
    value.put("reasonCode", entry.reasonCode());
  }

  private static void completionJson(ObjectNode value, ActivityPacketCompletion packetCompletion) {
    value.put("packetId", packetCompletion.packetId());
    strings(value.putArray("entryIds"), packetCompletion.entryIds());
    value.put("completion", packetCompletion.completion().name());
    strings(value.putArray("requiredSliceKeys"), packetCompletion.requiredSliceKeys());
    strings(value.putArray("completedSliceKeys"), packetCompletion.completedSliceKeys());
    ArrayNode incompleteScopes = value.putArray("incompleteScopes");
    packetCompletion
        .incompleteScopes()
        .forEach(
            scope -> {
              ObjectNode scopeJson = incompleteScopes.addObject();
              if (scope.sliceKey() == null) {
                scopeJson.putNull("sliceKey");
              } else {
                scopeJson.put("sliceKey", scope.sliceKey());
              }
              strings(scopeJson.putArray("entryIds"), scope.entryIds());
              scopeJson.put("reasonCode", scope.reasonCode());
            });
  }

  private static List<String> gaps(
      List<ActivityEntryCoverage> coverage, List<ActivityPacketCompletion> packetCompletion) {
    return java.util.stream.Stream.concat(
            coverage.stream()
                .filter(
                    value ->
                        "NOT_ANALYZED".equals(value.disposition())
                            || value.requiredScopeIncomplete())
                .map(ActivityEntryCoverage::reasonCode),
            packetCompletion.stream()
                .flatMap(completion -> completion.incompleteScopes().stream())
                .map(ActivityPacketCompletion.IncompleteScope::reasonCode))
        .distinct()
        .sorted(UTF8_ORDER)
        .toList();
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
