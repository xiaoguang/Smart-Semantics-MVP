package org.sourceanalysis.app.analysis.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.activity.UnexplainedActivityEntry;
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

/**
 * Fresh-reopens complete repository business knowledge without replaying process interpretation.
 */
final class ProcessKnowledgeCheckpointReader {

  private static final String PROCESSES_FILE = "business-processes.jsonl";
  private static final String COVERAGE_FILE = "process-coverage.json";
  private static final String KNOWLEDGE_FILE = "repository-business-knowledge.json";
  private static final String PROCESSES_TYPE = "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES";
  private static final String PROCESSES_SCHEMA = "repository-knowledge-business-processes-v1";
  private static final String COVERAGE_TYPE = "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE";
  private static final String COVERAGE_SCHEMA = "repository-knowledge-process-coverage-v2";
  private static final String KNOWLEDGE_TYPE = "REPOSITORY_KNOWLEDGE_BUSINESS_KNOWLEDGE";
  private static final String KNOWLEDGE_SCHEMA = "repository-knowledge-business-knowledge-v2";
  private static final Set<String> KNOWLEDGE_FIELDS =
      Set.of(
          "activities",
          "activityCoverage",
          "artifactId",
          "artifactType",
          "confirmationTopics",
          "notConsolidatedProcessIds",
          "processes",
          "repositorySummary",
          "schemaVersion",
          "unexplainedActivityEntries",
          "unmatchedActivityIds");
  private static final Set<String> COVERAGE_FIELDS =
      Set.of(
          "activityCoverage",
          "artifactId",
          "artifactType",
          "notConsolidatedProcessIds",
          "processCount",
          "schemaVersion",
          "semanticDeliveryStatus",
          "unexplainedActivityEntries",
          "unmatchedActivityIds");
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
          "sourceRefs",
          "terms",
          "triggerOrInput");
  private static final Set<String> PROCESS_FIELDS =
      Set.of(
          "activityIds",
          "branches",
          "businessPurpose",
          "certainty",
          "codeDefinedResults",
          "confirmationNotes",
          "name",
          "processId",
          "recordType",
          "schemaVersion",
          "sharedObjects",
          "sourceRefs",
          "stages");
  private static final Set<String> ENTRY_COVERAGE_FIELDS =
      Set.of("activityIds", "disposition", "entryId", "reasonCode");
  private static final Set<String> UNEXPLAINED_ENTRY_FIELDS =
      Set.of("entryId", "entryKey", "materialContext", "materialId", "reasonCode");
  private static final Set<String> STAGE_FIELDS = Set.of("activityId", "description", "order");
  private static final Set<String> SUMMARY_FIELDS =
      Set.of("businessGoals", "confirmationTopics", "objectsAndRelations", "sourceRefs", "text");

  private final CanonicalModuleArtifactStore artifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  ProcessKnowledgeCheckpointReader(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  RepositoryBusinessKnowledge reopen(ModulePublicationReference checkpoint) {
    try {
      ReopenedModulePublication reopened = artifacts.reopen(checkpoint);
      if (!(checkpoint.address() instanceof AnalysisStepModuleAddress address)
          || !checkpoint.equals(reopened.reference())
          || address.analysisStepKey() != AnalysisStepKey.REPOSITORY_KNOWLEDGE
          || address.moduleNumber() != 1
          || !"process-explainer".equals(address.moduleKey())
          || (reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED
              && reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED_WITH_GAPS)
          || reopened.payloads().size() != 3) {
        throw failure();
      }
      Map<String, VerifiedCanonicalPayload> payloads = payloads(reopened.payloads());
      ObjectNode root = object(payloads.get(KNOWLEDGE_FILE), KNOWLEDGE_FIELDS);
      identity(root, payloads.get(KNOWLEDGE_FILE), KNOWLEDGE_TYPE, KNOWLEDGE_SCHEMA);
      List<ReviewedActivity> activities = activities(root.path("activities"));
      List<BusinessProcess> processes = processes(root.path("processes"));
      List<ActivityEntryCoverage> coverage = coverage(root.path("activityCoverage"));
      List<UnexplainedActivityEntry> unexplained =
          unexplained(root.path("unexplainedActivityEntries"));
      RepositoryBusinessKnowledge result =
          new RepositoryBusinessKnowledge(
              activities,
              processes,
              coverage,
              unexplained,
              strings(root.path("unmatchedActivityIds")),
              strings(root.path("confirmationTopics")),
              strings(root.path("notConsolidatedProcessIds")),
              summary(root.path("repositorySummary")),
              checkpoint);
      verifyProcessJsonl(payloads.get(PROCESSES_FILE), processes);
      verifyCoverage(payloads.get(COVERAGE_FILE), result);
      return result;
    } catch (ProcessKnowledgeCheckpointException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ProcessKnowledgeCheckpointException(failure);
    }
  }

  private Map<String, VerifiedCanonicalPayload> payloads(List<VerifiedCanonicalPayload> all) {
    Map<String, VerifiedCanonicalPayload> values = new HashMap<>();
    for (VerifiedCanonicalPayload payload : all) {
      if (values.put(payload.descriptor().fileName(), payload) != null) throw failure();
    }
    if (!values.keySet().equals(Set.of(PROCESSES_FILE, COVERAGE_FILE, KNOWLEDGE_FILE)))
      throw failure();
    descriptor(
        values.get(PROCESSES_FILE),
        PROCESSES_TYPE,
        PROCESSES_SCHEMA,
        CanonicalMediaType.APPLICATION_X_NDJSON);
    descriptor(
        values.get(COVERAGE_FILE),
        COVERAGE_TYPE,
        COVERAGE_SCHEMA,
        CanonicalMediaType.APPLICATION_JSON);
    descriptor(
        values.get(KNOWLEDGE_FILE),
        KNOWLEDGE_TYPE,
        KNOWLEDGE_SCHEMA,
        CanonicalMediaType.APPLICATION_JSON);
    return Map.copyOf(values);
  }

  private void verifyProcessJsonl(
      VerifiedCanonicalPayload payload, List<BusinessProcess> expected) {
    String text = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    if ((expected.isEmpty() && !text.isEmpty())
        || (!expected.isEmpty() && (!text.endsWith("\n") || text.indexOf('\r') >= 0)))
      throw failure();
    List<BusinessProcess> actual = new ArrayList<>();
    if (!text.isEmpty())
      for (String line : text.substring(0, text.length() - 1).split("\n", -1))
        actual.add(process(object(line)));
    if (!actual.equals(
        expected.stream()
            .sorted(java.util.Comparator.comparing(BusinessProcess::processId))
            .toList())) throw failure();
  }

  private void verifyCoverage(
      VerifiedCanonicalPayload payload, RepositoryBusinessKnowledge knowledge) {
    ObjectNode root = object(payload, COVERAGE_FIELDS);
    identity(root, payload, COVERAGE_TYPE, COVERAGE_SCHEMA);
    List<ActivityEntryCoverage> coverage = coverage(root.path("activityCoverage"));
    List<UnexplainedActivityEntry> unexplained =
        unexplained(root.path("unexplainedActivityEntries"));
    String expectedStatus = semanticDeliveryStatus(knowledge);
    if (!coverage.equals(knowledge.activityCoverage())
        || !unexplained.equals(knowledge.unexplainedActivityEntries())
        || !strings(root.path("unmatchedActivityIds")).equals(knowledge.unmatchedActivityIds())
        || !strings(root.path("notConsolidatedProcessIds"))
            .equals(knowledge.notConsolidatedProcessIds())
        || !root.path("processCount").canConvertToInt()
        || root.path("processCount").intValue() != knowledge.processes().size()
        || !expectedStatus.equals(text(root, "semanticDeliveryStatus"))) throw failure();
    verifyUnexplainedCoverage(knowledge);
  }

  private List<ReviewedActivity> activities(JsonNode node) {
    if (!node.isArray()) throw failure();
    List<ReviewedActivity> values = new ArrayList<>();
    for (JsonNode nodeValue : node) {
      ObjectNode value = object(nodeValue);
      if (!fields(value).equals(ACTIVITY_FIELDS)
          || !"REVIEWED_ACTIVITY".equals(text(value, "recordType"))) throw failure();
      values.add(
          new ReviewedActivity(
              text(value, "activityId"),
              text(value, "materialId"),
              strings(value.path("entryIds")),
              text(value, "name"),
              text(value, "businessPurpose"),
              strings(value.path("participants")),
              strings(value.path("businessObjects")),
              strings(value.path("triggerOrInput")),
              strings(value.path("conditions")),
              strings(value.path("activitySteps")),
              strings(value.path("codeDefinedResults")),
              strings(value.path("businessRules")),
              strings(value.path("formulasOrMetrics")),
              strings(value.path("terms")),
              text(value, "certainty"),
              strings(value.path("sourceRefs")),
              strings(value.path("questions")),
              strings(value.path("scopeLimitations"))));
    }
    return List.copyOf(values);
  }

  private List<BusinessProcess> processes(JsonNode node) {
    if (!node.isArray()) throw failure();
    List<BusinessProcess> values = new ArrayList<>();
    for (JsonNode nodeValue : node) values.add(process(object(nodeValue)));
    return List.copyOf(values);
  }

  private BusinessProcess process(ObjectNode value) {
    if (!fields(value).equals(PROCESS_FIELDS)
        || !"BUSINESS_PROCESS".equals(text(value, "recordType"))
        || !PROCESSES_SCHEMA.equals(text(value, "schemaVersion"))) throw failure();
    List<BusinessProcessStage> stages = new ArrayList<>();
    if (!value.path("stages").isArray()) throw failure();
    for (JsonNode stageNode : value.path("stages")) {
      ObjectNode stage = object(stageNode);
      if (!fields(stage).equals(STAGE_FIELDS) || !stage.path("order").canConvertToInt())
        throw failure();
      stages.add(
          new BusinessProcessStage(
              stage.path("order").intValue(),
              text(stage, "activityId"),
              text(stage, "description")));
    }
    return new BusinessProcess(
        text(value, "processId"),
        text(value, "name"),
        text(value, "businessPurpose"),
        strings(value.path("activityIds")),
        stages,
        strings(value.path("branches")),
        strings(value.path("sharedObjects")),
        strings(value.path("codeDefinedResults")),
        text(value, "certainty"),
        strings(value.path("sourceRefs")),
        strings(value.path("confirmationNotes")));
  }

  private List<ActivityEntryCoverage> coverage(JsonNode node) {
    if (!node.isArray()) throw failure();
    List<ActivityEntryCoverage> values = new ArrayList<>();
    for (JsonNode nodeValue : node) {
      ObjectNode value = object(nodeValue);
      if (!fields(value).equals(ENTRY_COVERAGE_FIELDS)) throw failure();
      values.add(
          new ActivityEntryCoverage(
              text(value, "entryId"),
              text(value, "disposition"),
              strings(value.path("activityIds")),
              value.path("reasonCode").isNull() ? null : text(value, "reasonCode")));
    }
    return List.copyOf(values);
  }

  private List<UnexplainedActivityEntry> unexplained(JsonNode node) {
    if (!node.isArray()) throw failure();
    List<UnexplainedActivityEntry> values = new ArrayList<>();
    Set<String> entryIds = new HashSet<>();
    for (JsonNode nodeValue : node) {
      ObjectNode value = object(nodeValue);
      if (!fields(value).equals(UNEXPLAINED_ENTRY_FIELDS)) throw failure();
      UnexplainedActivityEntry entry =
          new UnexplainedActivityEntry(
              text(value, "entryId"),
              text(value, "materialId"),
              text(value, "entryKey"),
              text(value, "materialContext"),
              text(value, "reasonCode"));
      if (!entryIds.add(entry.entryId())) throw failure();
      values.add(entry);
    }
    return List.copyOf(values);
  }

  private static String semanticDeliveryStatus(RepositoryBusinessKnowledge knowledge) {
    return knowledge.activityCoverage().stream()
                .anyMatch(value -> "NOT_ANALYZED".equals(value.disposition()))
            || !knowledge.unmatchedActivityIds().isEmpty()
            || !knowledge.notConsolidatedProcessIds().isEmpty()
            || !knowledge.unexplainedActivityEntries().isEmpty()
        ? "PARTIAL"
        : "READY_FOR_REPORT";
  }

  private static void verifyUnexplainedCoverage(RepositoryBusinessKnowledge knowledge) {
    Map<String, ActivityEntryCoverage> coverageByEntry = new HashMap<>();
    for (ActivityEntryCoverage coverage : knowledge.activityCoverage()) {
      if (coverageByEntry.put(coverage.entryId(), coverage) != null) throw failure();
    }
    Set<String> unexplainedEntryIds = new HashSet<>();
    for (UnexplainedActivityEntry unexplained : knowledge.unexplainedActivityEntries()) {
      ActivityEntryCoverage coverage = coverageByEntry.get(unexplained.entryId());
      if (!unexplainedEntryIds.add(unexplained.entryId())
          || coverage == null
          || !"NOT_ANALYZED".equals(coverage.disposition())
          || !"MODEL_NOT_EXPLAINED".equals(coverage.reasonCode())
          || !coverage.activityIds().isEmpty()
          || knowledge.activities().stream()
              .anyMatch(activity -> activity.entryIds().contains(unexplained.entryId()))) {
        throw failure();
      }
    }
    Set<String> modelNotExplained = new HashSet<>();
    for (ActivityEntryCoverage coverage : knowledge.activityCoverage()) {
      if ("NOT_ANALYZED".equals(coverage.disposition())
          && "MODEL_NOT_EXPLAINED".equals(coverage.reasonCode())) {
        modelNotExplained.add(coverage.entryId());
      }
    }
    if (!modelNotExplained.equals(unexplainedEntryIds)) throw failure();
  }

  private RepositoryProcessSummary summary(JsonNode node) {
    if (node.isNull()) return null;
    ObjectNode value = object(node);
    if (!fields(value).equals(SUMMARY_FIELDS)) throw failure();
    return new RepositoryProcessSummary(
        text(value, "text"),
        strings(value.path("businessGoals")),
        strings(value.path("objectsAndRelations")),
        strings(value.path("confirmationTopics")),
        strings(value.path("sourceRefs")));
  }

  private ObjectNode object(VerifiedCanonicalPayload payload, Set<String> expected) {
    ObjectNode value = object(canonicalJson.parseCanonical(payload.canonicalUtf8()));
    if (!fields(value).equals(expected)) throw failure();
    return value;
  }

  private ObjectNode object(String json) {
    return object(
        canonicalJson.parseCanonical(ImmutableBytes.copyOf(json.getBytes(StandardCharsets.UTF_8))));
  }

  private static ObjectNode object(JsonNode node) {
    if (node instanceof ObjectNode value) return value;
    throw failure();
  }

  private static List<String> strings(JsonNode node) {
    if (!node.isArray()) throw failure();
    List<String> values = new ArrayList<>();
    for (JsonNode value : node) {
      if (!value.isTextual() || value.textValue().isBlank()) throw failure();
      values.add(value.textValue());
    }
    return List.copyOf(values);
  }

  private static String text(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) throw failure();
    return node.textValue();
  }

  private static Set<String> fields(ObjectNode value) {
    Set<String> fields = new HashSet<>();
    value.fieldNames().forEachRemaining(fields::add);
    return fields;
  }

  private static void descriptor(
      VerifiedCanonicalPayload p, String type, String schema, CanonicalMediaType media) {
    if (!type.equals(p.descriptor().artifactType())
        || !schema.equals(p.descriptor().schemaVersion())
        || p.descriptor().mediaType() != media) throw failure();
  }

  private static void identity(
      ObjectNode value, VerifiedCanonicalPayload p, String type, String schema) {
    if (!p.descriptor().artifactId().value().equals(text(value, "artifactId"))
        || !type.equals(text(value, "artifactType"))
        || !schema.equals(text(value, "schemaVersion"))) throw failure();
  }

  private static ProcessKnowledgeCheckpointException failure() {
    return new ProcessKnowledgeCheckpointException(null);
  }

  private static final class ProcessKnowledgeCheckpointException extends IllegalArgumentException {
    private ProcessKnowledgeCheckpointException(Throwable cause) {
      super("PROCESS_KNOWLEDGE_CHECKPOINT_INVALID", cause);
    }
  }
}
