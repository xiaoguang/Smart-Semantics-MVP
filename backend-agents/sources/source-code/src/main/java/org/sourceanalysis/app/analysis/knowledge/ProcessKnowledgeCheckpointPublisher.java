package org.sourceanalysis.app.analysis.knowledge;

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
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
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

/** Installs the reopenable business-process, coverage, and repository-knowledge outputs. */
final class ProcessKnowledgeCheckpointPublisher {

  private static final String PROCESSES_TYPE = "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES";
  private static final String PROCESSES_SCHEMA = "repository-knowledge-business-processes-v1";
  private static final String COVERAGE_TYPE = "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE";
  private static final String COVERAGE_SCHEMA = "repository-knowledge-process-coverage-v1";
  private static final String KNOWLEDGE_TYPE = "REPOSITORY_KNOWLEDGE_BUSINESS_KNOWLEDGE";
  private static final String KNOWLEDGE_SCHEMA = "repository-knowledge-business-knowledge-v1";
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

  ProcessKnowledgeCheckpointPublisher(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  ModulePublicationReference publish(
      ActivityExplanationResult activities, RepositoryBusinessKnowledge knowledge) {
    if (activities.checkpoint() == null) {
      throw new IllegalArgumentException("PROCESS_KNOWLEDGE_INPUT_NOT_PERSISTED");
    }
    ReopenedModulePublication activityCheckpoint = artifacts.reopen(activities.checkpoint());
    List<ArtifactReference> upstream =
        activityCheckpoint.receipt().payloadArtifacts().stream()
            .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    List<String> gaps =
        knowledge.activityCoverage().stream()
            .filter(value -> "NOT_ANALYZED".equals(value.disposition()))
            .map(ActivityEntryCoverage::reasonCode)
            .filter(Objects::nonNull)
            .distinct()
            .sorted(UTF8_ORDER)
            .toList();
    ModuleCompletionStatus status =
        gaps.isEmpty() && knowledge.unmatchedActivityIds().isEmpty()
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
    InstalledModulePublication installed =
        artifacts.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    activities.checkpoint().address().runId(),
                    AnalysisStepKey.REPOSITORY_KNOWLEDGE,
                    1,
                    "process-explainer"),
                "v1",
                upstream,
                activityCheckpoint.receipt().controls(),
                status,
                gaps,
                List.of(
                    processesPayload(knowledge.processes()),
                    coveragePayload(knowledge),
                    knowledgePayload(knowledge))));
    return installed.reference();
  }

  private CanonicalModulePayload processesPayload(List<BusinessProcess> processes) {
    StringBuilder values = new StringBuilder();
    processes.stream()
        .sorted(Comparator.comparing(BusinessProcess::processId, UTF8_ORDER))
        .forEach(
            process ->
                values
                    .append(
                        new String(
                            canonicalJson.encodeCanonical(processJson(process)).copyToByteArray(),
                            StandardCharsets.UTF_8))
                    .append('\n'));
    byte[] bytes = values.toString().getBytes(StandardCharsets.UTF_8);
    return new CanonicalModulePayload(
        "business-processes.jsonl",
        PROCESSES_TYPE,
        PROCESSES_SCHEMA,
        ArtifactId.parse(jsonlId("business-processes", PROCESSES_SCHEMA, PROCESSES_TYPE, bytes)),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private CanonicalModulePayload coveragePayload(RepositoryBusinessKnowledge knowledge) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", COVERAGE_SCHEMA);
    value.put("artifactType", COVERAGE_TYPE);
    ArrayNode coverage = value.putArray("activityCoverage");
    knowledge.activityCoverage().stream()
        .sorted(Comparator.comparing(ActivityEntryCoverage::entryId, UTF8_ORDER))
        .forEach(item -> coverageJson(coverage.addObject(), item));
    strings(value.putArray("unmatchedActivityIds"), knowledge.unmatchedActivityIds());
    strings(value.putArray("notConsolidatedProcessIds"), knowledge.notConsolidatedProcessIds());
    value.put("processCount", knowledge.processes().size());
    value.put(
        "semanticDeliveryStatus",
        knowledge.unmatchedActivityIds().isEmpty()
                && knowledge.notConsolidatedProcessIds().isEmpty()
            ? "READY_FOR_REPORT"
            : "PARTIAL");
    return standalonePayload(
        "process-coverage.json", "process-coverage", COVERAGE_TYPE, COVERAGE_SCHEMA, value);
  }

  private CanonicalModulePayload knowledgePayload(RepositoryBusinessKnowledge knowledge) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", KNOWLEDGE_SCHEMA);
    value.put("artifactType", KNOWLEDGE_TYPE);
    ArrayNode activities = value.putArray("activities");
    knowledge.activities().stream()
        .sorted(Comparator.comparing(ReviewedActivity::activityId, UTF8_ORDER))
        .forEach(activity -> activityJson(activities.addObject(), activity));
    ArrayNode processes = value.putArray("processes");
    knowledge.processes().stream()
        .sorted(Comparator.comparing(BusinessProcess::processId, UTF8_ORDER))
        .forEach(process -> processes.add(processJson(process)));
    ArrayNode coverage = value.putArray("activityCoverage");
    knowledge.activityCoverage().stream()
        .sorted(Comparator.comparing(ActivityEntryCoverage::entryId, UTF8_ORDER))
        .forEach(item -> coverageJson(coverage.addObject(), item));
    strings(value.putArray("unmatchedActivityIds"), knowledge.unmatchedActivityIds());
    strings(value.putArray("confirmationTopics"), knowledge.confirmationTopics());
    strings(value.putArray("notConsolidatedProcessIds"), knowledge.notConsolidatedProcessIds());
    if (knowledge.repositorySummary() == null) {
      value.putNull("repositorySummary");
    } else {
      value.set("repositorySummary", repositorySummaryJson(knowledge.repositorySummary()));
    }
    return standalonePayload(
        "repository-business-knowledge.json",
        "repository-business-knowledge",
        KNOWLEDGE_TYPE,
        KNOWLEDGE_SCHEMA,
        value);
  }

  private CanonicalModulePayload standalonePayload(
      String fileName, String prefix, String type, String schema, ObjectNode value) {
    String id = standaloneId(prefix, schema, type, value);
    value.put("artifactId", id);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(value));
  }

  private ObjectNode activityJson(ObjectNode value, ReviewedActivity activity) {
    value.put("recordType", "REVIEWED_ACTIVITY");
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

  private ObjectNode processJson(BusinessProcess process) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("recordType", "BUSINESS_PROCESS");
    value.put("schemaVersion", PROCESSES_SCHEMA);
    value.put("processId", process.processId());
    value.put("name", process.name());
    value.put("businessPurpose", process.businessPurpose());
    strings(value.putArray("activityIds"), process.activityIds());
    ArrayNode stages = value.putArray("stages");
    process.stages().forEach(stage -> stageJson(stages.addObject(), stage));
    strings(value.putArray("branches"), process.branches());
    strings(value.putArray("sharedObjects"), process.sharedObjects());
    strings(value.putArray("codeDefinedResults"), process.codeDefinedResults());
    value.put("certainty", process.certainty());
    strings(value.putArray("sourceRefs"), process.sourceRefs());
    strings(value.putArray("confirmationNotes"), process.confirmationNotes());
    return value;
  }

  private static ObjectNode repositorySummaryJson(RepositoryProcessSummary summary) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("text", summary.text());
    strings(value.putArray("businessGoals"), summary.businessGoals());
    strings(value.putArray("objectsAndRelations"), summary.objectsAndRelations());
    strings(value.putArray("confirmationTopics"), summary.confirmationTopics());
    strings(value.putArray("sourceRefs"), summary.sourceRefs());
    return value;
  }

  private static void stageJson(ObjectNode value, BusinessProcessStage stage) {
    value.put("order", stage.order());
    value.put("activityId", stage.activityId());
    value.put("description", stage.description());
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

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
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
