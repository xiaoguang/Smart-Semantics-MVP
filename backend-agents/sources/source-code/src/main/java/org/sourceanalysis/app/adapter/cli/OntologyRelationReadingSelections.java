package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus;
import org.sourceanalysis.app.analysis.ontology.OntologyReadingCoordinator;
import org.sourceanalysis.app.analysis.ontology.OntologySelectionReader;

/** Separates the immutable requested O2 scope from actual bounded reading observations. */
final class OntologyRelationReadingSelections {
  private static final Set<String> FIELDS =
      Set.of("questionId", "taskId", "status", "issueCode", "selectedEntries", "selectedClues");
  private final Map<String, ObjectNode> rows = new LinkedHashMap<>();

  OntologyRelationReadingSelections(List<OntologySelectionReader.Question> questions) {
    for (OntologySelectionReader.Question question : questions) {
      ObjectNode row = JsonNodeFactory.instance.objectNode();
      row.put("questionId", question.questionId());
      row.put("taskId", question.taskId());
      row.put("status", "NOT_STARTED");
      row.put("issueCode", "");
      strings(row.putArray("selectedEntries"), question.entryRefs());
      strings(row.putArray("selectedClues"), question.clueRefs());
      if (rows.putIfAbsent(question.taskId(), row) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
  }

  void observe(
      String taskId,
      OntologyReadingCoordinator.FormalState state,
      String status,
      String issueCode) {
    ObjectNode row = rows.get(taskId);
    if (row == null) throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    row.put("status", status);
    row.put("issueCode", issueCode);
    if (state != null) {
      strings(row.putArray("selectedEntries"), state.selectedEntries());
      strings(row.putArray("selectedClues"), state.selectedClues());
    }
  }

  ArrayNode document() {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    rows.values().forEach(row -> result.add(row.deepCopy()));
    return result;
  }

  static Map<String, JsonNode> read(JsonNode document, OntologyEvidenceCorpus corpus) {
    JsonNode raw = document.path("readingSelections");
    if (!raw.isArray()) throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    Map<String, JsonNode> declared = new LinkedHashMap<>();
    boolean typeComparison =
        "ontology-relations-v5".equals(document.path("schemaVersion").asText())
            && "OBJECT_TYPE_CORRESPONDENCE".equals(document.path("relationProfile").asText());
    JsonNode selected =
        typeComparison ? document.path("effectiveSelection") : document.path("selection");
    if (typeComparison)
      org.sourceanalysis.app.analysis.ontology.OntologySelectionReader.read(selected, corpus);
    for (JsonNode question : selected.path("questions")) {
      if (declared.putIfAbsent(OntologySavedTaskContract.requiredText(question, "taskId"), question)
          != null) throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    Map<String, JsonNode> result = new LinkedHashMap<>();
    for (JsonNode row : raw) {
      OntologySavedTaskContract.requireExactFields(row, FIELDS);
      String taskId = OntologySavedTaskContract.requiredText(row, "taskId");
      JsonNode question = declared.get(taskId);
      if (question == null
          || !OntologySavedTaskContract.requiredText(row, "questionId")
              .equals(OntologySavedTaskContract.requiredText(question, "questionId"))
          || !Set.of("NOT_STARTED", "READY", "INCOMPLETE", "UNRESOLVED", "FAILED")
              .contains(OntologySavedTaskContract.requiredText(row, "status"))
          || !row.path("issueCode").isTextual()
          || result.putIfAbsent(taskId, row.deepCopy()) != null) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
      requireRefs(row.path("selectedEntries"), corpus, true);
      requireRefs(row.path("selectedClues"), corpus, false);
      if (("EXPLICIT".equals(question.path("readingMode").asText())
              || "NOT_STARTED".equals(row.path("status").asText()))
          && (!row.path("selectedEntries").equals(question.path("entryRefs"))
              || !row.path("selectedClues").equals(question.path("clueRefs")))) {
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      }
    }
    if (!result.keySet().equals(declared.keySet()))
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    return Map.copyOf(result);
  }

  static void requireReviewedClues(JsonNode observation, List<String> frozenClues) {
    if (observation == null || !"READY".equals(observation.path("status").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
    ArrayNode expected = JsonNodeFactory.instance.arrayNode();
    strings(expected, frozenClues);
    if (!expected.equals(observation.path("selectedClues"))) {
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    }
  }

  private static void requireRefs(JsonNode values, OntologyEvidenceCorpus corpus, boolean entries) {
    if (!values.isArray() || corpus == null)
      throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
    Set<String> unique = new java.util.HashSet<>();
    for (JsonNode value : values) {
      if (!value.isTextual() || !unique.add(value.asText()))
        throw new IllegalArgumentException("ONTOLOGY_SELECTED_RUN_INVALID");
      if (entries) corpus.aliases().entry(value.asText());
      else corpus.aliases().clue(value.asText());
    }
  }

  private static void strings(ArrayNode array, List<String> values) {
    values.forEach(array::add);
  }
}
