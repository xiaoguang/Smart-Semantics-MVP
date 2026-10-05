package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict reader for explicit O2/O3 run selections; it never resolves a latest run. */
public final class OntologySelectionReader {
  private static final Set<String> RELATE_FIELDS =
      Set.of("schemaVersion", "operation", "corpusRun", "identificationRuns", "questions");
  private static final Set<String> PUBLISH_FIELDS =
      Set.of("schemaVersion", "operation", "corpusRun", "identificationRuns", "relationRuns");
  private static final Set<String> QUESTION_FIELDS =
      Set.of(
          "questionId",
          "question",
          "taskId",
          "readingMode",
          "entryRefs",
          "clueRefs",
          "unitUses",
          "requiredUnitUses");
  private static final Set<String> QUESTION_FIELDS_V2 =
      Set.of(
          "questionId",
          "question",
          "taskId",
          "readingMode",
          "entryRefs",
          "clueRefs",
          "unitUses",
          "requiredUnitUses",
          "objectSources");

  private OntologySelectionReader() {}

  public static Selection read(JsonNode document, OntologyEvidenceCorpus corpus) {
    Objects.requireNonNull(corpus, "ontology corpus");
    requireObject(document, "ONTOLOGY_SELECTION_INVALID");
    String schemaVersion = requiredText(document, "schemaVersion");
    boolean v2 = "ontology-selection-v2".equals(schemaVersion);
    if (!v2 && !"ontology-selection-v1".equals(schemaVersion)) {
      throw failure("ONTOLOGY_SELECTION_INVALID");
    }
    Operation operation;
    try {
      operation = Operation.valueOf(requiredText(document, "operation"));
    } catch (IllegalArgumentException invalid) {
      throw failure("ONTOLOGY_SELECTION_OPERATION_INVALID");
    }
    requireFields(document, operation == Operation.RELATE ? RELATE_FIELDS : PUBLISH_FIELDS);
    String corpusRun = runId(requiredText(document, "corpusRun"));
    Set<String> selectedRuns = new HashSet<>();
    selectedRuns.add(corpusRun);
    List<String> identificationRuns = runIds(document.get("identificationRuns"), selectedRuns);
    if (identificationRuns.isEmpty()) {
      throw failure("ONTOLOGY_SELECTION_IDENTIFICATION_REQUIRED");
    }
    if (operation == Operation.PUBLISH) {
      List<String> relationRuns = runIds(document.get("relationRuns"), selectedRuns);
      if (relationRuns.isEmpty()) {
        throw failure("ONTOLOGY_SELECTION_RELATION_REQUIRED");
      }
      return new Selection(
          schemaVersion, operation, corpusRun, identificationRuns, relationRuns, List.of());
    }
    List<Question> questions = questions(document.get("questions"), corpus, identificationRuns, v2);
    return new Selection(
        schemaVersion, operation, corpusRun, identificationRuns, List.of(), questions);
  }

  private static List<Question> questions(
      JsonNode rawQuestions,
      OntologyEvidenceCorpus corpus,
      List<String> identificationRuns,
      boolean v2) {
    if (rawQuestions == null || !rawQuestions.isArray()) {
      throw failure("ONTOLOGY_SELECTION_QUESTIONS_INVALID");
    }
    List<Question> questions = new ArrayList<>();
    Set<String> questionIds = new HashSet<>();
    Set<String> taskIds = new HashSet<>();
    for (JsonNode rawQuestion : rawQuestions) {
      requireObject(rawQuestion, "ONTOLOGY_SELECTION_QUESTION_INVALID");
      requireFields(rawQuestion, v2 ? QUESTION_FIELDS_V2 : QUESTION_FIELDS);
      String questionId = requiredText(rawQuestion, "questionId");
      String taskId = requiredText(rawQuestion, "taskId");
      if (!questionIds.add(questionId) || !taskIds.add(taskId)) {
        throw failure("ONTOLOGY_SELECTION_QUESTION_DUPLICATE");
      }
      OntologyScopeReader.ReadingMode readingMode;
      try {
        readingMode =
            OntologyScopeReader.ReadingMode.valueOf(requiredText(rawQuestion, "readingMode"));
      } catch (IllegalArgumentException invalid) {
        throw failure("ONTOLOGY_SELECTION_READING_MODE_INVALID");
      }
      questions.add(
          new Question(
              questionId,
              requiredText(rawQuestion, "question"),
              taskId,
              readingMode,
              entryRefs(rawQuestion.get("entryRefs"), corpus),
              clueRefs(rawQuestion.get("clueRefs"), corpus),
              OntologyScopeReader.unitUses(rawQuestion.get("unitUses"), corpus),
              OntologyScopeReader.unitUses(rawQuestion.get("requiredUnitUses"), corpus),
              v2
                  ? objectSources(rawQuestion.get("objectSources"), identificationRuns)
                  : List.of()));
    }
    return List.copyOf(questions);
  }

  private static List<ObjectSource> objectSources(
      JsonNode rawSources, List<String> identificationRuns) {
    if (rawSources == null || !rawSources.isArray() || rawSources.size() == 0) {
      throw failure("ONTOLOGY_SELECTION_OBJECT_SOURCES_REQUIRED");
    }
    List<ObjectSource> sources = new ArrayList<>();
    Set<String> unique = new HashSet<>();
    for (JsonNode rawSource : rawSources) {
      requireObject(rawSource, "ONTOLOGY_SELECTION_OBJECT_SOURCE_INVALID");
      requireFields(rawSource, Set.of("identificationRun", "questionId"));
      String identificationRun = runId(requiredText(rawSource, "identificationRun"));
      String questionId = requiredText(rawSource, "questionId");
      if (!identificationRuns.contains(identificationRun)
          || !unique.add(identificationRun + "\u0000" + questionId)) {
        throw failure("ONTOLOGY_SELECTION_OBJECT_SOURCE_INVALID");
      }
      sources.add(new ObjectSource(identificationRun, questionId));
    }
    return List.copyOf(sources);
  }

  private static List<String> entryRefs(JsonNode rawRefs, OntologyEvidenceCorpus corpus) {
    return references(rawRefs, corpus, "E[1-9][0-9]*", true);
  }

  private static List<String> clueRefs(JsonNode rawRefs, OntologyEvidenceCorpus corpus) {
    return references(rawRefs, corpus, "K[1-9][0-9]*", false);
  }

  private static List<String> references(
      JsonNode rawRefs, OntologyEvidenceCorpus corpus, String pattern, boolean entry) {
    if (rawRefs == null || !rawRefs.isArray()) {
      throw failure("ONTOLOGY_SELECTION_REFERENCE_INVALID");
    }
    List<String> refs = new ArrayList<>();
    Set<String> unique = new HashSet<>();
    for (JsonNode rawRef : rawRefs) {
      if (!rawRef.isTextual() || !rawRef.asText().matches(pattern)) {
        throw failure("ONTOLOGY_SELECTION_REFERENCE_CATEGORY_INVALID");
      }
      String ref = rawRef.asText();
      try {
        if (entry) {
          corpus.aliases().entry(ref);
        } else {
          corpus.aliases().clue(ref);
        }
      } catch (IllegalArgumentException invalid) {
        throw failure("ONTOLOGY_SELECTION_REFERENCE_INVALID");
      }
      if (!unique.add(ref)) {
        throw failure("ONTOLOGY_SELECTION_REFERENCE_DUPLICATE");
      }
      refs.add(ref);
    }
    return List.copyOf(refs);
  }

  private static List<String> runIds(JsonNode rawRuns, Set<String> selectedRuns) {
    if (rawRuns == null || !rawRuns.isArray()) {
      throw failure("ONTOLOGY_SELECTION_REQUIRED_FIELD");
    }
    if (selectedRuns == null) {
      throw failure("ONTOLOGY_SELECTION_REQUIRED_FIELD");
    }
    List<String> runs = new ArrayList<>();
    for (JsonNode rawRun : rawRuns) {
      String run = runId(rawRun.isTextual() ? rawRun.asText() : null);
      if (!selectedRuns.add(run)) {
        throw failure("ONTOLOGY_SELECTION_RUN_DUPLICATE");
      }
      runs.add(run);
    }
    return List.copyOf(runs);
  }

  private static String runId(String run) {
    if (run == null || !run.matches("analysis-run:[0-9a-f]{64}")) {
      throw failure("ONTOLOGY_SELECTION_RUN_INVALID");
    }
    return run;
  }

  private static void requireObject(JsonNode node, String code) {
    if (node == null || !node.isObject()) {
      throw failure(code);
    }
  }

  private static void requireFields(JsonNode node, Set<String> fields) {
    node.fieldNames()
        .forEachRemaining(
            field -> {
              if (!fields.contains(field)) {
                throw failure("ONTOLOGY_SELECTION_UNKNOWN_FIELD");
              }
            });
    for (String field : fields) {
      if (!node.has(field)) {
        throw failure("ONTOLOGY_SELECTION_REQUIRED_FIELD");
      }
    }
  }

  private static String requiredText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw failure("ONTOLOGY_SELECTION_REQUIRED_FIELD");
    }
    return value.asText();
  }

  private static IllegalArgumentException failure(String code) {
    return new IllegalArgumentException(code);
  }

  public enum Operation {
    RELATE,
    PUBLISH
  }

  public record Selection(
      String schemaVersion,
      Operation operation,
      String corpusRun,
      List<String> identificationRuns,
      List<String> relationRuns,
      List<Question> questions) {
    public Selection {
      if (!"ontology-selection-v1".equals(schemaVersion)
          && !"ontology-selection-v2".equals(schemaVersion)) {
        throw new IllegalArgumentException("ontology selection schema version");
      }
      identificationRuns = List.copyOf(identificationRuns);
      relationRuns = List.copyOf(relationRuns);
      questions = List.copyOf(questions);
    }

    public boolean isV2() {
      return "ontology-selection-v2".equals(schemaVersion);
    }
  }

  public record Question(
      String questionId,
      String question,
      String taskId,
      OntologyScopeReader.ReadingMode readingMode,
      List<String> entryRefs,
      List<String> clueRefs,
      List<OntologyScopeReader.UnitUse> unitUses,
      List<OntologyScopeReader.UnitUse> requiredUnitUses,
      List<ObjectSource> objectSources) {
    public Question {
      entryRefs = List.copyOf(entryRefs);
      clueRefs = List.copyOf(clueRefs);
      unitUses = List.copyOf(unitUses);
      requiredUnitUses = List.copyOf(requiredUnitUses);
      objectSources = List.copyOf(objectSources);
    }
  }

  /** Exact saved O1 question source for one v2 relation task; it is not name-based matching. */
  public record ObjectSource(String identificationRun, String questionId) {
    public ObjectSource {
      if (identificationRun == null || identificationRun.isBlank()) {
        throw new IllegalArgumentException("ontology object source run");
      }
      if (questionId == null || questionId.isBlank()) {
        throw new IllegalArgumentException("ontology object source question");
      }
    }
  }
}
