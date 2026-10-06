package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader;

/** Strict saved-task shape and declared-range checks; no Provider or business interpretation. */
final class OntologySavedTaskContract {
  private OntologySavedTaskContract() {}

  static void requireSelectionProductionVersion(
      String selectionVersion, boolean primaryCurrentProducer, boolean companionCurrentProducer) {
    if ("ontology-selection-v3".equals(selectionVersion)
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
