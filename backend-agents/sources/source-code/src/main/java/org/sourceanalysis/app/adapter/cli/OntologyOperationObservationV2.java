package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.analysis.ontology.OntologyJobResultStore;
import org.sourceanalysis.app.runtime.AnalysisRunRequest;

/** Projects verified v2/v3 ontology task outcomes into the existing operation observation wire. */
final class OntologyOperationObservationV2 {
  private OntologyOperationObservationV2() {}

  static void writeObserved(
      ObjectNode observation,
      ArrayNode problems,
      List<JsonNode> taskOutcomes,
      String runId,
      AnalysisRunRequest.OntologyOperation operation,
      OntologyJobResultStore.RuntimeObservation runtime,
      String missingRuntimeProblemCode) {
    observation.put("schemaVersion", "ontology-operation-observation-v2");
    List<JsonNode> declaredOutcomes =
        OntologyOperationObservationV2.uniqueTaskOutcomes(taskOutcomes, runId);
    ArrayNode outcomeNodes = observation.putArray("taskOutcomes");
    declaredOutcomes.forEach(outcomeNodes::add);
    Set<String> problemIdentities = new LinkedHashSet<>();
    for (JsonNode outcome : declaredOutcomes) {
      if ("REVIEWED".equals(outcome.path("status").asText())) {
        continue;
      }
      JsonNode reason = outcome.path("reason");
      JsonNode reasonStage = reason.get("stage");
      if (!reason.isObject()
          || reason.path("code").asText().isBlank()
          || reason.path("category").asText().isBlank()
          || (reasonStage != null
              && !reasonStage.isNull()
              && (!reasonStage.isTextual() || reasonStage.textValue().isBlank()))) {
        throw new IllegalArgumentException("ONTOLOGY_RUNTIME_OBSERVATION_INVALID");
      }
      OntologyOperationObservationV2.appendProblem(
          problems,
          problemIdentities,
          reason.path("code").asText(),
          reason.path("category").asText(),
          outcome.path("runId").isTextual() ? outcome.path("runId").asText() : runId,
          outcome.path("questionId").asText(),
          outcome.path("taskId").asText(),
          reasonStage == null || reasonStage.isNull() ? null : reasonStage.textValue());
    }
    String runtimeProblemCode = runtime == null ? missingRuntimeProblemCode : runtime.problemCode();
    if (runtimeProblemCode != null) {
      String runtimeTaskId = runtime == null ? null : runtime.taskId();
      String runtimeStage = runtime == null ? null : runtime.stage();
      if (!OntologyOperationObservationV2.containsDeclaredOutcomeProblem(
          declaredOutcomes, runId, runtimeTaskId, runtimeProblemCode, runtimeStage)) {
        OntologyOperationObservationV2.appendProblem(
            problems,
            problemIdentities,
            runtimeProblemCode,
            runtime == null || runtime.problemCategory() == null
                ? "ASSEMBLY".equals(runtimeStage) ? "ASSEMBLY" : "UNKNOWN"
                : runtime.problemCategory(),
            runId,
            null,
            runtimeTaskId,
            runtimeStage);
      }
    }
    OntologyOperationObservationV2.writeNextActions(
        observation, declaredOutcomes, runId, operation);
    ObjectNode counts = observation.putObject("modelRequestCounts");
    if (runtime == null || !runtime.isV2()) {
      if (operation == AnalysisRunRequest.OntologyOperation.PUBLISH_ONTOLOGY) {
        counts.put("reservedAttempts", 0);
        counts.put("confirmedStarted", 0);
        counts.put("confirmedEnded", 0);
        counts.put("outcomeUnknown", 0);
      } else {
        counts.putNull("reservedAttempts");
        counts.putNull("confirmedStarted");
        counts.putNull("confirmedEnded");
        counts.putNull("outcomeUnknown");
      }
    } else {
      counts.put("reservedAttempts", runtime.reservedAttempts());
      counts.put("confirmedStarted", runtime.confirmedStarted());
      counts.put("confirmedEnded", runtime.confirmedEnded());
      counts.put("outcomeUnknown", runtime.outcomeUnknown());
    }
  }

  static boolean isTaskOutcomePayload(String artifactType, String schemaVersion) {
    return ("ONTOLOGY_IDENTIFICATION".equals(artifactType)
            && Set.of(
                    "ontology-identification-v2",
                    "ontology-identification-v3",
                    "ontology-identification-v4")
                .contains(schemaVersion))
        || ("ONTOLOGY_RELATIONS".equals(artifactType)
            && Set.of("ontology-relations-v2", "ontology-relations-v3", "ontology-relations-v4")
                .contains(schemaVersion))
        || ("ONTOLOGY_COVERAGE".equals(artifactType)
            && Set.of("ontology-coverage-v2", "ontology-coverage-v3", "ontology-coverage-v4")
                .contains(schemaVersion))
        || ("ONTOLOGY_REVIEW".equals(artifactType)
            && Set.of("ontology-review-v2", "ontology-review-v3", "ontology-review-v4")
                .contains(schemaVersion));
  }

  static boolean isTaskOutcomePayloadArtifact(String artifactType) {
    return "ONTOLOGY_IDENTIFICATION".equals(artifactType)
        || "ONTOLOGY_RELATIONS".equals(artifactType)
        || "ONTOLOGY_COVERAGE".equals(artifactType)
        || "ONTOLOGY_REVIEW".equals(artifactType);
  }

  static List<JsonNode> uniqueTaskOutcomes(List<JsonNode> outcomes, String defaultRunId) {
    Map<String, JsonNode> unique = new LinkedHashMap<>();
    for (JsonNode outcome : outcomes) {
      if (!outcome.isObject()) {
        throw new IllegalArgumentException("ONTOLOGY_RUNTIME_OBSERVATION_INVALID");
      }
      String owner = resolvedOutcomeRunId(outcome, defaultRunId);
      String questionId = outcome.path("questionId").asText();
      String taskId = outcome.path("taskId").asText();
      if (owner.isBlank() || questionId.isBlank() || taskId.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_RUNTIME_OBSERVATION_INVALID");
      }
      String identity = owner + "\u0000" + questionId + "\u0000" + taskId;
      JsonNode prior = unique.putIfAbsent(identity, outcome);
      if (prior != null && !prior.equals(outcome)) {
        throw new IllegalArgumentException("ONTOLOGY_RUNTIME_OBSERVATION_INVALID");
      }
    }
    return List.copyOf(unique.values());
  }

  static void appendProblem(
      ArrayNode problems,
      Set<String> identities,
      String code,
      String category,
      String runId,
      String questionId,
      String taskId,
      String stage) {
    String identity =
        (runId == null ? "" : runId)
            + "\u0000"
            + (questionId == null ? "" : questionId)
            + "\u0000"
            + (code == null ? "" : code)
            + "\u0000"
            + (category == null ? "" : category)
            + "\u0000"
            + (taskId == null ? "" : taskId)
            + "\u0000"
            + (stage == null ? "" : stage);
    if (!identities.add(identity)) {
      return;
    }
    ObjectNode problem = problems.addObject();
    problem.put("code", code);
    problem.put("category", category);
    if (taskId == null) problem.putNull("taskId");
    else problem.put("taskId", taskId);
    if (stage == null) problem.putNull("stage");
    else problem.put("stage", stage);
  }

  static boolean containsDeclaredOutcomeProblem(
      List<JsonNode> outcomes, String ownerRunId, String taskId, String code, String stage) {
    return taskId != null
        && code != null
        && stage != null
        && outcomes.stream()
            .anyMatch(
                outcome ->
                    !"REVIEWED".equals(outcome.path("status").asText())
                        && ownerRunId.equals(resolvedOutcomeRunId(outcome, ownerRunId))
                        && taskId.equals(outcome.path("taskId").asText())
                        && code.equals(outcome.path("reason").path("code").asText())
                        && stage.equals(outcome.path("reason").path("stage").asText()));
  }

  static void writeNextActions(
      ObjectNode observation,
      List<JsonNode> outcomes,
      String defaultRunId,
      AnalysisRunRequest.OntologyOperation operation) {
    ArrayNode actions = observation.putArray("nextActions");
    List<JsonNode> queryable =
        outcomes.stream().filter(outcome -> outcome.path("producingTaskId").isTextual()).toList();
    if (!queryable.isEmpty()) {
      ObjectNode action = actions.addObject();
      action.put("kind", "QUERY_TASK");
      writeOutcomeReferences(action.putArray("taskRefs"), queryable, defaultRunId);
      writeOutcomeOwnerReferences(action.putArray("upstreamRunRefs"), queryable, defaultRunId);
      action.put("requiresNewRun", false);
    }
    boolean hasReviewed =
        outcomes.stream().anyMatch(outcome -> "REVIEWED".equals(outcome.path("status").asText()));
    boolean hasNonReviewed =
        outcomes.stream().anyMatch(outcome -> !"REVIEWED".equals(outcome.path("status").asText()));
    boolean hasPublicationBlockingIntegrityFailure =
        outcomes.stream()
            .map(outcome -> outcome.path("reason"))
            .filter(JsonNode::isObject)
            .map(reason -> reason.path("category").asText())
            .anyMatch(category -> "SOURCE".equals(category) || "STORAGE".equals(category));
    if (hasReviewed
        && hasNonReviewed
        && !hasPublicationBlockingIntegrityFailure
        && (operation == AnalysisRunRequest.OntologyOperation.IDENTIFY_ONTOLOGY
            || operation == AnalysisRunRequest.OntologyOperation.RELATE_ONTOLOGY)) {
      List<JsonNode> reviewed =
          outcomes.stream()
              .filter(outcome -> "REVIEWED".equals(outcome.path("status").asText()))
              .toList();
      ObjectNode action = actions.addObject();
      action.put("kind", "PUBLISH_REVIEWED_PART");
      writeOutcomeReferences(action.putArray("taskRefs"), reviewed, defaultRunId);
      writeOutcomeOwnerReferences(action.putArray("upstreamRunRefs"), reviewed, defaultRunId);
      action.put("requiresNewRun", true);
    }
  }

  private static String resolvedOutcomeRunId(JsonNode outcome, String defaultRunId) {
    return outcome.path("runId").isTextual() ? outcome.path("runId").asText() : defaultRunId;
  }

  private static void writeOutcomeReferences(
      ArrayNode target, List<JsonNode> outcomes, String defaultRunId) {
    Set<String> references = new LinkedHashSet<>();
    for (JsonNode outcome : outcomes) {
      String owner =
          outcome.path("runId").isTextual() ? outcome.path("runId").asText() : defaultRunId;
      String questionId = outcome.path("questionId").asText();
      String taskId = outcome.path("taskId").asText();
      String identity = owner + "\u0000" + questionId + "\u0000" + taskId;
      if (!references.add(identity)) {
        continue;
      }
      ObjectNode reference = target.addObject();
      reference.put("runId", owner);
      reference.put("questionId", questionId);
      reference.put("taskId", taskId);
    }
  }

  private static void writeOutcomeOwnerReferences(
      ArrayNode target, List<JsonNode> outcomes, String defaultRunId) {
    Set<String> owners = new LinkedHashSet<>();
    for (JsonNode outcome : outcomes) {
      String owner =
          outcome.path("runId").isTextual() ? outcome.path("runId").asText() : defaultRunId;
      if (owners.add(owner)) {
        target.add(owner);
      }
    }
  }
}
