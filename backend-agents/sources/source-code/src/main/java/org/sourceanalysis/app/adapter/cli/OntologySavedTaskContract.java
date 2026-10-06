package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader;
import org.sourceanalysis.app.analysis.ontology.OntologyScopedAssembler;
import org.sourceanalysis.app.analysis.ontology.OntologySelectionReader;
import org.sourceanalysis.app.analysis.ontology.OntologyTaskOutcome;
import org.sourceanalysis.app.analysis.ontology.OntologyTaskRunner;
import org.sourceanalysis.app.analysis.ontology.OntologyTypedTaskRunner;

/** Strict saved-task shape and declared-range checks; no Provider or business interpretation. */
final class OntologySavedTaskContract {
  private OntologySavedTaskContract() {}

  static String taskOutcomeDispositionReason(OntologyTaskOutcome outcome) {
    return outcome.reason() == null
        ? "The exact extract and review pair was saved and reopened."
        : outcome.reason().code();
  }

  static OntologyTaskOutcome reviewedRelationOutcome(
      OntologySelectionReader.Question question,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies) {
    return new OntologyTaskOutcome(
        question.questionId(),
        question.taskId(),
        OntologyTaskRunner.TaskKind.RELATE,
        OntologyTaskOutcome.Status.REVIEWED,
        OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared.jobKey(),
        dependencies,
        null);
  }

  static OntologyTaskOutcome rejectedRelationOutcome(
      OntologySelectionReader.Question question,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies,
      OntologyTaskOutcome.FailureReason reason) {
    return new OntologyTaskOutcome(
        question.questionId(),
        question.taskId(),
        OntologyTaskRunner.TaskKind.RELATE,
        OntologyTaskOutcome.Status.REJECTED,
        OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared.jobKey(),
        dependencies,
        reason);
  }

  static OntologyTaskOutcome unprocessedRelationOutcome(
      OntologySelectionReader.Question question,
      OntologyTypedTaskRunner.PreparedFormalTask prepared,
      List<OntologyTaskOutcome.TaskReference> dependencies,
      OntologyTaskOutcome.FailureReason reason) {
    return new OntologyTaskOutcome(
        question.questionId(),
        question.taskId(),
        OntologyTaskRunner.TaskKind.RELATE,
        OntologyTaskOutcome.Status.UNPROCESSED,
        prepared == null ? null : OntologyTypedTaskRunner.formalProducingTaskId(prepared),
        prepared == null ? null : prepared.jobKey(),
        dependencies,
        reason);
  }

  static List<OntologyScopedAssembler.TaskDisposition> relationTaskDispositions(
      OntologySelectionReader.Selection selection, List<OntologyTaskOutcome> outcomes) {
    Map<String, OntologyTaskOutcome> byTaskId = new LinkedHashMap<>();
    for (OntologyTaskOutcome outcome : outcomes) {
      if (outcome.taskKind() != OntologyTaskRunner.TaskKind.RELATE
          || byTaskId.putIfAbsent(outcome.taskId(), outcome) != null) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
    }
    List<OntologyScopedAssembler.TaskDisposition> dispositions = new ArrayList<>();
    for (OntologySelectionReader.Question question : selection.questions()) {
      OntologyTaskOutcome outcome = byTaskId.get(question.taskId());
      if (outcome == null || !question.questionId().equals(outcome.questionId())) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
      dispositions.add(
          new OntologyScopedAssembler.TaskDisposition(
              question.taskId(),
              outcome.status() == OntologyTaskOutcome.Status.UNPROCESSED
                  ? null
                  : outcome.producingTaskId(),
              OntologyScopedAssembler.TaskDispositionStatus.valueOf(outcome.status().name()),
              taskOutcomeDispositionReason(outcome)));
    }
    return List.copyOf(dispositions);
  }

  static List<OntologyScopedAssembler.TaskDisposition> relationTaskDispositions(
      OntologySelectionReader.Selection selection,
      List<OntologyTypedTaskRunner.PreparedFormalTask> prepared,
      List<OntologyTypedTaskRunner.FormalResult> completed) {
    Map<String, OntologyTypedTaskRunner.PreparedFormalTask> preparedByTask = new LinkedHashMap<>();
    for (OntologyTypedTaskRunner.PreparedFormalTask task : prepared) {
      if (preparedByTask.putIfAbsent(task.task().taskId(), task) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTION_REFERENCE_INVALID");
      }
    }
    Set<String> reviewed = new HashSet<>();
    for (OntologyTypedTaskRunner.FormalResult result : completed) {
      reviewed.add(result.identity().producingTaskId());
    }
    List<OntologyScopedAssembler.TaskDisposition> dispositions = new ArrayList<>();
    for (OntologySelectionReader.Question question : selection.questions()) {
      OntologyTypedTaskRunner.PreparedFormalTask task = preparedByTask.get(question.taskId());
      if (task == null) {
        dispositions.add(
            new OntologyScopedAssembler.TaskDisposition(
                question.taskId(),
                null,
                OntologyScopedAssembler.TaskDispositionStatus.UNPROCESSED,
                "No immutable relation task was prepared after the saved runtime stopped before"
                    + " this selected question."));
        continue;
      }
      String producingTaskId = OntologyTypedTaskRunner.formalProducingTaskId(task);
      boolean wasReviewed = reviewed.contains(producingTaskId);
      dispositions.add(
          new OntologyScopedAssembler.TaskDisposition(
              question.taskId(),
              producingTaskId,
              wasReviewed
                  ? OntologyScopedAssembler.TaskDispositionStatus.REVIEWED
                  : OntologyScopedAssembler.TaskDispositionStatus.REJECTED,
              wasReviewed
                  ? "The exact saved relation extract and review pair was reopened."
                  : "The prepared relation task did not reach a reviewed result in the saved"
                      + " runtime."));
    }
    return List.copyOf(dispositions);
  }

  record Problem(
      String code, String taskId, String stage, OntologyTaskOutcome.FailureReason reason) {
    static Problem none() {
      return new Problem(null, null, null, null);
    }

    Problem first(String taskId, OntologyTaskOutcome.FailureReason reason) {
      return code != null ? this : new Problem(reason.code(), taskId, reason.stage(), reason);
    }
  }

  static OntologyTaskOutcome.FailureReason savedTaskFailureReason(JsonNode reason) {
    if (reason.isNull()) return null;
    requireV2TaskOutcomeReason(reason);
    List<String> expected = new ArrayList<>();
    reason.path("expectedRefs").forEach(ref -> expected.add(ref.asText()));
    List<OntologyTaskOutcome.TaskReference> dependencies = new ArrayList<>();
    reason
        .path("dependencyTaskRefs")
        .forEach(
            ref ->
                dependencies.add(
                    new OntologyTaskOutcome.TaskReference(
                        ref.path("runId").asText(),
                        ref.path("questionId").asText(),
                        ref.path("taskId").asText())));
    return new OntologyTaskOutcome.FailureReason(
        reason.path("code").asText(),
        OntologyTaskOutcome.Category.valueOf(reason.path("category").asText()),
        reason.path("stage").isNull() ? null : reason.path("stage").asText(),
        reason.path("jsonPointer").isNull() ? null : reason.path("jsonPointer").asText(),
        reason.path("offendingRef").isNull() ? null : reason.path("offendingRef").asText(),
        expected,
        dependencies);
  }

  static void requirePreparationFailures(
      JsonNode document, Map<String, JsonNode> outcomes, String schemaVersion) {
    if (!document.has("preparationFailures")) return;
    JsonNode failures = document.path("preparationFailures");
    if (!schemaVersion.endsWith("-v5") || !failures.isArray())
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    Set<String> seen = new HashSet<>();
    for (JsonNode failure : failures) {
      JsonNode outcome = outcomes.get(failure.path("taskId").asText());
      if (failure.size() != 6
          || outcome == null
          || !seen.add(failure.path("taskId").asText())
          || !outcome.path("questionId").equals(failure.path("questionId"))
          || !outcome.path("reason").path("code").equals(failure.path("issueCode"))
          || !outcome.path("reason").path("stage").equals(failure.path("stage"))
          || !outcome.path("reason").path("offendingRef").equals(failure.path("offendingRef")))
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      JsonNode capacity = failure.path("capacity");
      if ("ONTOLOGY_OBJECT_TYPE_SOURCE_UNAVAILABLE".equals(failure.path("issueCode").asText())) {
        if (!capacity.isNull()) throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        continue;
      }
      boolean request = "REQUEST_ENVELOPE".equals(capacity.path("boundary").asText());
      if (!capacity.isObject()
          || capacity.size() != (request ? 7 : 3)
          || (!request && !"COMPLETE_UNIT_BODY".equals(capacity.path("boundary").asText()))
          || !capacity.path("measuredBytes").isIntegralNumber()
          || !capacity.path("limitBytes").isIntegralNumber()
          || capacity.path("limitBytes").asLong() <= 0
          || capacity.path("measuredBytes").asLong() <= capacity.path("limitBytes").asLong())
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      if (request) {
        long measured = 0;
        for (String field :
            List.of("inputBytes", "promptBytes", "schemaBytes", "outputReserveBytes")) {
          if (!capacity.path(field).isIntegralNumber() || capacity.path(field).asLong() < 0)
            throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
          measured = Math.addExact(measured, capacity.path(field).asLong());
        }
        if (measured != capacity.path("measuredBytes").asLong())
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
  }

  static void recordPreparationFailure(
      ArrayNode rows,
      String questionId,
      String taskId,
      OntologyTypedTaskRunner.FormalPreparationFailure failure) {
    ObjectNode row = rows.addObject();
    row.put("questionId", questionId);
    row.put("taskId", taskId);
    row.put("issueCode", failure.reason().code());
    row.put("stage", failure.reason().stage());
    row.put("offendingRef", failure.reason().offendingRef());
    row.set("capacity", failure.capacityObservation());
  }

  static void requireSelectionProductionVersion(
      String selectionVersion, boolean primaryCurrentProducer, boolean companionCurrentProducer) {
    if (Set.of("ontology-selection-v3", "ontology-selection-v4").contains(selectionVersion)
        && !(primaryCurrentProducer && companionCurrentProducer)) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTION_PRODUCTION_VERSION_INVALID");
    }
  }

  static void requireCorpusProductionVersion(boolean currentProducer, boolean currentCorpus) {
    if (currentProducer != currentCorpus) {
      throw new IllegalArgumentException("ONTOLOGY_CORPUS_VERSION_INVALID");
    }
  }

  static void requireV2IdentificationTaskRange(
      JsonNode document, OntologyEvidenceCorpus corpus, Map<String, JsonNode> taskOutcomes) {
    requireV2IdentificationTaskRange(document, corpus, taskOutcomes, false);
  }

  static void requireV2IdentificationTaskRange(
      JsonNode document,
      OntologyEvidenceCorpus corpus,
      Map<String, JsonNode> taskOutcomes,
      boolean jointLinkFamily) {
    if (corpus == null) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    Map<RangeTaskKey, String> declared = new LinkedHashMap<>();
    JsonNode selectedQuestions = document.get("selectedQuestions");
    if (selectedQuestions != null) {
      if (!selectedQuestions.isArray() || selectedQuestions.isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      for (JsonNode question : selectedQuestions) {
        requireExactFields(
            question, Set.of("questionId", "question", "entryRefs", "clueRefs", "tasks"));
        String questionId = requiredText(question, "questionId");
        JsonNode tasks = question.path("tasks");
        if (!tasks.isArray() || tasks.isEmpty()) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
        for (JsonNode task : tasks) {
          requireExactFields(
              task,
              jointLinkFamily
                  ? Set.of(
                      "taskId",
                      "taskKind",
                      "readingMode",
                      "unitUses",
                      "requiredUnitUses",
                      "anchorRefs")
                  : Set.of("taskId", "taskKind", "readingMode", "unitUses", "requiredUnitUses"));
          String taskId = requiredText(task, "taskId");
          String taskKind = requiredText(task, "taskKind");
          if (jointLinkFamily) {
            JsonNode anchors = task.path("anchorRefs");
            if (!anchors.isArray()
                || ("LINK".equals(taskKind)
                    ? anchors.size() != 1
                        || !"TECHNICAL_BUNDLE".equals(requiredText(task, "readingMode"))
                    : !anchors.isEmpty())) {
              throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
            }
            for (JsonNode anchor : anchors) {
              if (!anchor.isTextual()) {
                throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
              }
              boolean declaredAnchor = false;
              for (JsonNode clue : question.path("clueRefs")) declaredAnchor |= anchor.equals(clue);
              if (!declaredAnchor)
                throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
              corpus.aliases().clue(anchor.asText());
            }
          }
          if (!(jointLinkFamily
                      ? Set.of("OBJECT", "ACTION", "ANALYTIC", "LINK")
                      : Set.of("OBJECT", "ACTION", "ANALYTIC"))
                  .contains(taskKind)
              || declared.putIfAbsent(new RangeTaskKey(questionId, taskId), taskKind) != null) {
            throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
          }
        }
      }
    } else {
      OntologyScopeReader.Scope scope;
      try {
        scope = OntologyScopeReader.read(document.path("scope"), corpus);
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID", invalid);
      }
      for (OntologyScopeReader.Question question : scope.questions()) {
        for (OntologyScopeReader.Task task : question.tasks()) {
          if (declared.putIfAbsent(
                  new RangeTaskKey(question.questionId(), task.taskId()), task.taskKind().name())
              != null) {
            throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
          }
        }
      }
    }
    if (declared.isEmpty() || declared.size() != taskOutcomes.size()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    for (JsonNode outcome : taskOutcomes.values()) {
      RangeTaskKey key =
          new RangeTaskKey(outcome.path("questionId").asText(), outcome.path("taskId").asText());
      if (!outcome.path("taskKind").asText().equals(declared.remove(key))) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    if (!declared.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
  }

  static Map<String, JsonNode> requireV2TaskOutcomes(
      JsonNode outcomes, Map<String, TaskRecord> taskRecords) {
    return requireV2TaskOutcomes(outcomes, taskRecords, false);
  }

  static Map<String, JsonNode> requireV2TaskOutcomes(
      JsonNode outcomes, Map<String, TaskRecord> taskRecords, boolean jointLinkFamily) {
    Set<String> declared = new HashSet<>();
    Set<String> actual = new HashSet<>();
    Map<String, JsonNode> saved = new LinkedHashMap<>();
    for (JsonNode outcome : outcomes) {
      requireExactFields(
          outcome,
          Set.of(
              "questionId",
              "taskId",
              "taskKind",
              "status",
              "producingTaskId",
              "jobKey",
              "dependencyTaskRefs",
              "reason"));
      String questionId = requiredText(outcome, "questionId");
      String taskId = requiredText(outcome, "taskId");
      if (questionId.isBlank() || taskId.isBlank() || !declared.add(taskId)) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      String kind = requiredText(outcome, "taskKind");
      String status = requiredText(outcome, "status");
      if (!(jointLinkFamily
                  ? Set.of("OBJECT", "ACTION", "ANALYTIC", "RELATE", "LINK")
                  : Set.of("OBJECT", "ACTION", "ANALYTIC", "RELATE"))
              .contains(kind)
          || !Set.of("REVIEWED", "REJECTED", "UNPROCESSED").contains(status)) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      requireTaskReferences(outcome.path("dependencyTaskRefs"));
      JsonNode producer = outcome.get("producingTaskId");
      JsonNode jobKey = outcome.get("jobKey");
      boolean hasProducer = producer != null && !producer.isNull();
      boolean hasJobKey = jobKey != null && !jobKey.isNull();
      if (hasProducer != hasJobKey
          || (hasProducer && (producer.asText().isBlank() || jobKey.asText().isBlank()))) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      TaskRecord taskRecord = taskRecords.get(taskId);
      if (hasProducer) {
        if (taskRecord == null
            || !producer.asText().equals(taskRecord.producingTaskId())
            || !jobKey.asText().equals(taskRecord.jobKey())
            || !kind.equals(taskRecord.taskKind())
            || !status.equals(taskRecord.status())
            || !actual.add(taskId)) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
      } else if (taskRecord != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      JsonNode reason = outcome.get("reason");
      if ("REVIEWED".equals(status)) {
        if (reason == null || !reason.isNull()) {
          throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
        }
      } else {
        requireV2TaskOutcomeReason(reason);
      }
      saved.put(taskId, outcome.deepCopy());
    }
    if (!actual.equals(taskRecords.keySet())) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(saved));
  }

  static void requireV2TaskOutcomeReason(JsonNode reason) {
    requireExactFields(
        reason,
        Set.of(
            "code",
            "category",
            "stage",
            "jsonPointer",
            "offendingRef",
            "expectedRefs",
            "dependencyTaskRefs"));
    if (requiredText(reason, "code").isBlank()
        || !Set.of(
                "MODEL_OUTPUT",
                "MATERIAL",
                "DEPENDENCY",
                "SOURCE",
                "CONFIGURATION",
                "PROVIDER",
                "STORAGE",
                "DISPATCH_LIMIT",
                "ASSEMBLY",
                "UNKNOWN")
            .contains(requiredText(reason, "category"))
        || !optionalText(reason.get("stage"))
        || !optionalText(reason.get("jsonPointer"))
        || !optionalText(reason.get("offendingRef"))
        || !reason.path("expectedRefs").isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    for (JsonNode reference : reason.path("expectedRefs")) {
      if (!reference.isTextual() || reference.asText().isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    requireTaskReferences(reason.path("dependencyTaskRefs"));
  }

  static void requireTaskReferences(JsonNode references) {
    if (!references.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    Set<String> unique = new HashSet<>();
    for (JsonNode reference : references) {
      requireExactFields(reference, Set.of("runId", "questionId", "taskId"));
      String runId = requiredText(reference, "runId");
      String questionId = requiredText(reference, "questionId");
      String taskId = requiredText(reference, "taskId");
      if (runId.isBlank()
          || questionId.isBlank()
          || taskId.isBlank()
          || !unique.add(runId + "\u0000" + questionId + "\u0000" + taskId)) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
  }

  static void requireExactFields(JsonNode object, Set<String> expected) {
    if (!object.isObject()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    Set<String> actual = new HashSet<>();
    object.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
  }

  static String requiredText(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isTextual()) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    return value.asText();
  }

  static boolean optionalText(JsonNode value) {
    return value == null || value.isNull() || (value.isTextual() && !value.asText().isBlank());
  }

  record TaskRecord(String producingTaskId, String jobKey, String taskKind, String status) {}

  private record RangeTaskKey(String questionId, String taskId) {}
}
