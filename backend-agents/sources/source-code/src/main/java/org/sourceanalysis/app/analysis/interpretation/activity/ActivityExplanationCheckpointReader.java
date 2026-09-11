package org.sourceanalysis.app.analysis.interpretation.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Fresh-reopens the reviewed local activities saved before process reconstruction. */
final class ActivityExplanationCheckpointReader {

  private static final String COVERAGE_FILE = "activity-coverage.json";
  private static final String COVERAGE_TYPE = "FLOW_INTERPRETATION_ACTIVITY_COVERAGE";
  private static final String COVERAGE_SCHEMA = "flow-interpretation-activity-coverage-v2";
  private static final String EXPLANATIONS_FILE = "activity-explanations.jsonl";
  private static final String EXPLANATIONS_TYPE = "FLOW_INTERPRETATION_ACTIVITY_EXPLANATIONS";
  private static final String EXPLANATIONS_SCHEMA = "flow-interpretation-activity-explanations-v1";
  private static final Set<String> COVERAGE_FIELDS =
      Set.of(
          "artifactId",
          "artifactType",
          "entryCoverage",
          "schemaVersion",
          "semanticDeliveryStatus",
          "unexplainedActivityEntries");
  private static final Set<String> ENTRY_COVERAGE_FIELDS =
      Set.of("activityIds", "disposition", "entryId", "reasonCode");
  private static final Set<String> UNEXPLAINED_ENTRY_FIELDS =
      Set.of("entryId", "entryKey", "materialContext", "materialId", "reasonCode");
  private static final Set<String> ACTIVITY_FIELDS =
      Set.of(
          "activityId",
          "activitySteps",
          "businessObjects",
          "businessPurpose",
          "businessRules",
          "certainty",
          "codeDefinedResults",
          "conditions",
          "entryIds",
          "formulasOrMetrics",
          "materialId",
          "name",
          "participants",
          "questions",
          "recordType",
          "scopeLimitations",
          "schemaVersion",
          "sourceRefs",
          "terms",
          "triggerOrInput");

  private final CanonicalModuleArtifactStore artifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  ActivityExplanationCheckpointReader(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  ActivityExplanationResult reopen(ModulePublicationReference checkpoint) {
    try {
      ReopenedModulePublication reopened = artifacts.reopen(checkpoint);
      verifyCheckpoint(checkpoint, reopened);
      Map<String, VerifiedCanonicalPayload> payloads = payloads(reopened.payloads());
      CoverageCheckpoint coverage = coverage(payloads.get(COVERAGE_FILE));
      List<ReviewedActivity> activities = activities(payloads.get(EXPLANATIONS_FILE));
      verifyCoverage(coverage.entries(), activities, coverage.unexplainedEntries());
      return new ActivityExplanationResult(
          activities, coverage.entries(), coverage.unexplainedEntries(), checkpoint);
    } catch (ActivityCheckpointException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", failure);
    }
  }

  private static void verifyCheckpoint(
      ModulePublicationReference checkpoint, ReopenedModulePublication reopened) {
    if (!checkpoint.equals(reopened.reference())
        || !(checkpoint.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 11
        || !"activity-explainer".equals(address.moduleKey())
        || (reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED
            && reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED_WITH_GAPS)
        || reopened.payloads().size() != 2) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
  }

  private static Map<String, VerifiedCanonicalPayload> payloads(
      List<VerifiedCanonicalPayload> values) {
    Map<String, VerifiedCanonicalPayload> byName = new HashMap<>();
    for (VerifiedCanonicalPayload value : values) {
      if (byName.put(value.descriptor().fileName(), value) != null) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
    }
    if (!byName.keySet().equals(Set.of(COVERAGE_FILE, EXPLANATIONS_FILE))) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    requireDescriptor(
        byName.get(COVERAGE_FILE),
        COVERAGE_TYPE,
        COVERAGE_SCHEMA,
        CanonicalMediaType.APPLICATION_JSON);
    requireDescriptor(
        byName.get(EXPLANATIONS_FILE),
        EXPLANATIONS_TYPE,
        EXPLANATIONS_SCHEMA,
        CanonicalMediaType.APPLICATION_X_NDJSON);
    return Map.copyOf(byName);
  }

  private CoverageCheckpoint coverage(VerifiedCanonicalPayload payload) {
    ObjectNode root = parseObject(payload, COVERAGE_FIELDS);
    requireIdentity(root, payload, COVERAGE_TYPE, COVERAGE_SCHEMA);
    JsonNode entries = root.path("entryCoverage");
    if (!entries.isArray()) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    List<ActivityEntryCoverage> restored = new ArrayList<>();
    Set<String> entryIds = new HashSet<>();
    boolean partial = false;
    for (JsonNode entry : entries) {
      if (!(entry instanceof ObjectNode value) || !fields(value).equals(ENTRY_COVERAGE_FIELDS)) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
      String disposition = requiredText(value, "disposition");
      if ("NOT_ANALYZED".equals(disposition)) {
        partial = true;
      }
      ActivityEntryCoverage restoredEntry =
          new ActivityEntryCoverage(
              requiredText(value, "entryId"),
              disposition,
              strings(value.path("activityIds")),
              nullableText(value.path("reasonCode")));
      if (!entryIds.add(restoredEntry.entryId())) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
      restored.add(restoredEntry);
    }
    String expected = partial ? "PARTIAL" : "READY_FOR_PROCESS_EXPLANATION";
    if (!expected.equals(requiredText(root, "semanticDeliveryStatus"))) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    JsonNode unexplained = root.path("unexplainedActivityEntries");
    if (!unexplained.isArray()) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    List<UnexplainedActivityEntry> restoredUnexplained = new ArrayList<>();
    Set<String> unexplainedEntryIds = new HashSet<>();
    for (JsonNode entry : unexplained) {
      if (!(entry instanceof ObjectNode value) || !fields(value).equals(UNEXPLAINED_ENTRY_FIELDS)) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
      UnexplainedActivityEntry restoredEntry =
          new UnexplainedActivityEntry(
              requiredText(value, "entryId"),
              requiredText(value, "materialId"),
              requiredText(value, "entryKey"),
              requiredText(value, "materialContext"),
              requiredText(value, "reasonCode"));
      if (!unexplainedEntryIds.add(restoredEntry.entryId())) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
      restoredUnexplained.add(restoredEntry);
    }
    return new CoverageCheckpoint(List.copyOf(restored), List.copyOf(restoredUnexplained));
  }

  private List<ReviewedActivity> activities(VerifiedCanonicalPayload payload) {
    String jsonl = strictUtf8(payload.canonicalUtf8().copyToByteArray());
    if (jsonl.isEmpty()) {
      return List.of();
    }
    if (!jsonl.endsWith("\n") || jsonl.indexOf('\r') >= 0) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    List<ReviewedActivity> restored = new ArrayList<>();
    String previous = null;
    for (String line : jsonl.substring(0, jsonl.length() - 1).split("\n", -1)) {
      ObjectNode value = parseObject(line);
      if (!fields(value).equals(ACTIVITY_FIELDS)
          || !"REVIEWED_ACTIVITY".equals(requiredText(value, "recordType"))
          || !EXPLANATIONS_SCHEMA.equals(requiredText(value, "schemaVersion"))) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
      ReviewedActivity activity =
          new ReviewedActivity(
              requiredText(value, "activityId"),
              requiredText(value, "materialId"),
              strings(value.path("entryIds")),
              requiredText(value, "name"),
              requiredText(value, "businessPurpose"),
              strings(value.path("participants")),
              strings(value.path("businessObjects")),
              strings(value.path("triggerOrInput")),
              strings(value.path("conditions")),
              strings(value.path("activitySteps")),
              strings(value.path("codeDefinedResults")),
              strings(value.path("businessRules")),
              strings(value.path("formulasOrMetrics")),
              strings(value.path("terms")),
              requiredText(value, "certainty"),
              strings(value.path("sourceRefs")),
              strings(value.path("questions")),
              strings(value.path("scopeLimitations")));
      if (previous != null && previous.compareTo(activity.activityId()) >= 0) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
      previous = activity.activityId();
      restored.add(activity);
    }
    return List.copyOf(restored);
  }

  private static void verifyCoverage(
      List<ActivityEntryCoverage> coverage,
      List<ReviewedActivity> activities,
      List<UnexplainedActivityEntry> unexplainedActivityEntries) {
    Map<String, ReviewedActivity> activitiesById = new HashMap<>();
    for (ReviewedActivity activity : activities) {
      if (activitiesById.put(activity.activityId(), activity) != null) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
    }
    Set<String> unexplainedEntryIds = new HashSet<>();
    for (UnexplainedActivityEntry unexplained : unexplainedActivityEntries) {
      if (!unexplainedEntryIds.add(unexplained.entryId())) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
    }
    Set<String> seenActivityIds = new HashSet<>();
    for (ActivityEntryCoverage entry : coverage) {
      List<String> expectedActivityIds =
          activities.stream()
              .filter(activity -> activity.entryIds().contains(entry.entryId()))
              .map(ReviewedActivity::activityId)
              .sorted()
              .toList();
      if (!activitiesById.keySet().containsAll(entry.activityIds())
          || !entry.activityIds().equals(expectedActivityIds)) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
      seenActivityIds.addAll(entry.activityIds());
      boolean unexplained = unexplainedEntryIds.contains(entry.entryId());
      if (unexplained
          != ("NOT_ANALYZED".equals(entry.disposition())
              && "MODEL_NOT_EXPLAINED".equals(entry.reasonCode())
              && entry.activityIds().isEmpty())) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
    }
    if (!seenActivityIds.equals(activitiesById.keySet())) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
  }

  private record CoverageCheckpoint(
      List<ActivityEntryCoverage> entries, List<UnexplainedActivityEntry> unexplainedEntries) {}

  private ObjectNode parseObject(VerifiedCanonicalPayload payload, Set<String> expectedFields) {
    JsonNode parsed = canonicalJson.parseCanonical(payload.canonicalUtf8());
    if (!(parsed instanceof ObjectNode value) || !fields(value).equals(expectedFields)) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    return value;
  }

  private ObjectNode parseObject(String json) {
    JsonNode parsed =
        canonicalJson.parseCanonical(ImmutableBytes.copyOf(json.getBytes(StandardCharsets.UTF_8)));
    if (!(parsed instanceof ObjectNode value)) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    return value;
  }

  private static void requireDescriptor(
      VerifiedCanonicalPayload payload, String type, String schema, CanonicalMediaType mediaType) {
    if (!type.equals(payload.descriptor().artifactType())
        || !schema.equals(payload.descriptor().schemaVersion())
        || payload.descriptor().mediaType() != mediaType) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
  }

  private static void requireIdentity(
      ObjectNode value, VerifiedCanonicalPayload payload, String type, String schema) {
    if (!payload.descriptor().artifactId().value().equals(requiredText(value, "artifactId"))
        || !type.equals(requiredText(value, "artifactType"))
        || !schema.equals(requiredText(value, "schemaVersion"))) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
  }

  private static List<String> strings(JsonNode node) {
    if (!node.isArray()) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    List<String> values = new ArrayList<>();
    for (JsonNode value : node) {
      if (!value.isTextual() || value.textValue().isBlank()) {
        throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
      }
      values.add(value.textValue());
    }
    return List.copyOf(values);
  }

  private static String requiredText(ObjectNode value, String field) {
    return requiredText(value.path(field), field);
  }

  private static String requiredText(JsonNode value, String field) {
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", null);
    }
    return value.textValue();
  }

  private static String nullableText(JsonNode value) {
    return value.isNull() ? null : requiredText(value, "reasonCode");
  }

  private static Set<String> fields(ObjectNode value) {
    Set<String> fields = new HashSet<>();
    value.fieldNames().forEachRemaining(fields::add);
    return fields;
  }

  private static String strictUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException invalid) {
      throw failure("ACTIVITY_EXPLANATION_CHECKPOINT_INVALID", invalid);
    }
  }

  private static ActivityCheckpointException failure(String code, Throwable cause) {
    return new ActivityCheckpointException(code, cause);
  }

  private static final class ActivityCheckpointException extends IllegalArgumentException {

    private ActivityCheckpointException(String code, Throwable cause) {
      super(code, cause);
    }
  }
}
