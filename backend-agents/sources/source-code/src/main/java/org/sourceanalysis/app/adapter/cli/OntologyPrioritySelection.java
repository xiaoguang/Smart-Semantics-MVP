package org.sourceanalysis.app.adapter.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.analysis.ontology.OntologyDecisionRunner;
import org.sourceanalysis.app.analysis.ontology.OntologyScopeReader;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Mechanical conversion of reviewed navigation choices; no business interpretation. */
final class OntologyPrioritySelection {
  private OntologyPrioritySelection() {}

  static Result select(
      List<QuestionInput> questions,
      OntologyDecisionRunner.Decision priority,
      OntologyScopeReader.Purpose purpose) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    JsonNode output = json.parseCanonical(priority.output());
    boolean jointLinks =
        "ontology-prioritize-response-v5".equals(output.path("schemaVersion").asText());
    Map<String, QuestionInput> byRef = new LinkedHashMap<>();
    questions.forEach(question -> byRef.put(question.questionRef(), question));
    Set<String> handled = new LinkedHashSet<>();
    List<OntologyScopeReader.Question> selected = new ArrayList<>();
    List<String> refs = new ArrayList<>();
    for (JsonNode item : output.path("selectedQuestions")) {
      QuestionInput question = byRef.get(item.path("questionRef").asText());
      if (question == null || !handled.add(question.questionRef())) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
      }
      if (!item.path("taskKinds").isArray() || item.path("taskKinds").isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_TASK_INVALID");
      }
      List<OntologyScopeReader.Task> tasks = new ArrayList<>();
      Set<String> taskKinds = new LinkedHashSet<>();
      for (JsonNode kind : item.path("taskKinds")) {
        if (!kind.isTextual()
            || !(jointLinks && purpose == OntologyScopeReader.Purpose.SKELETON
                    ? Set.of("OBJECT", "LINK")
                    : Set.of("OBJECT", "ACTION", "ANALYTIC"))
                .contains(kind.asText())
            || !taskKinds.add(kind.asText())) {
          throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_TASK_INVALID");
        }
      }
      if (!taskKinds.contains("OBJECT") && !(jointLinks && taskKinds.contains("LINK"))) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_OBJECT_REQUIRED");
      }
      if (!jointLinks
          && purpose == OntologyScopeReader.Purpose.SKELETON
          && !taskKinds.equals(Set.of("OBJECT"))) {
        throw new IllegalArgumentException("ONTOLOGY_SCOPE_SKELETON_TASK_INVALID");
      }
      for (String kind : List.of("OBJECT", "ACTION", "ANALYTIC")) {
        if (taskKinds.contains(kind)) {
          tasks.add(
              new OntologyScopeReader.Task(
                  "discovery-"
                      + question.questionRef()
                      + "-"
                      + kind.toLowerCase(java.util.Locale.ROOT),
                  OntologyScopeReader.TaskKind.valueOf(kind),
                  OntologyScopeReader.ReadingMode.MODEL,
                  List.of(),
                  List.of()));
        }
      }
      if (jointLinks) {
        JsonNode anchors = item.path("linkAnchorRefs");
        if (!anchors.isArray() || (taskKinds.contains("LINK") == anchors.isEmpty())) {
          throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_TASK_INVALID");
        }
        Set<String> seenAnchors = new LinkedHashSet<>();
        for (JsonNode anchor : anchors) {
          if (!anchor.isTextual()
              || !question.clueRefs().contains(anchor.asText())
              || !seenAnchors.add(anchor.asText())) {
            throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
          }
          tasks.add(
              new OntologyScopeReader.Task(
                  "discovery-" + question.questionRef() + "-link-" + anchor.asText(),
                  OntologyScopeReader.TaskKind.LINK,
                  OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
                  List.of(),
                  List.of(),
                  List.of(anchor.asText())));
        }
      }
      String specificQuestion = item.path("specificQuestion").asText();
      if (specificQuestion.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
      }
      selected.add(
          new OntologyScopeReader.Question(
              question.questionRef(),
              specificQuestion,
              question.entryRefs(),
              question.clueRefs(),
              tasks));
      refs.add(question.questionRef());
    }
    List<DeferredQuestion> deferred = new ArrayList<>();
    for (JsonNode item : output.path("deferredQuestions")) {
      QuestionInput question = byRef.get(item.path("questionRef").asText());
      String reason = item.path("reason").asText();
      if (question == null || reason.isBlank() || !handled.add(question.questionRef())) {
        throw new IllegalArgumentException("ONTOLOGY_PRIORITIZE_REFERENCE_UNKNOWN");
      }
      deferred.add(new DeferredQuestion(question.questionRef(), reason));
    }
    for (QuestionInput question : questions) {
      if (handled.add(question.questionRef())) {
        deferred.add(
            new DeferredQuestion(question.questionRef(), "NOT_SELECTED_WITHIN_DECLARED_LIMIT"));
      }
    }
    return new Result(List.copyOf(selected), List.copyOf(refs), List.copyOf(deferred));
  }

  record QuestionInput(String questionRef, List<String> entryRefs, List<String> clueRefs) {}

  record DeferredQuestion(String questionRef, String reason) {}

  record Result(
      List<OntologyScopeReader.Question> questions,
      List<String> questionRefs,
      List<DeferredQuestion> deferredQuestions) {}
}
