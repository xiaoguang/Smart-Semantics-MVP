package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict reader for one bounded ontology identification scope, without ontology definitions. */
public final class OntologyScopeReader {
  private static final Set<String> ROOT_FIELDS =
      Set.of("schemaVersion", "mode", "selectionMode", "questions");
  private static final Set<String> QUESTION_FIELDS =
      Set.of("questionId", "question", "entryRefs", "clueRefs", "tasks");
  private static final Set<String> TASK_FIELDS =
      Set.of("taskId", "taskKind", "readingMode", "unitUses", "requiredUnitUses");
  private static final Set<String> UNIT_USE_FIELDS = Set.of("unitRef", "entryRef");

  private OntologyScopeReader() {}

  public static Scope read(JsonNode document, OntologyEvidenceCorpus corpus) {
    Objects.requireNonNull(corpus, "ontology corpus");
    requireObject(document, "ONTOLOGY_SCOPE_INVALID");
    requireFields(
        document, ROOT_FIELDS, "ONTOLOGY_SCOPE_UNKNOWN_FIELD", "ONTOLOGY_SCOPE_REQUIRED_FIELD");
    if (!"ontology-scope-v1"
        .equals(requiredText(document, "schemaVersion", "ONTOLOGY_SCOPE_INVALID"))) {
      throw failure("ONTOLOGY_SCOPE_INVALID");
    }
    Mode mode = enumValue(Mode.class, requiredText(document, "mode", "ONTOLOGY_SCOPE_INVALID"));
    SelectionMode selectionMode =
        enumValue(
            SelectionMode.class, requiredText(document, "selectionMode", "ONTOLOGY_SCOPE_INVALID"));
    JsonNode rawQuestions = document.get("questions");
    if (!rawQuestions.isArray()) {
      throw failure("ONTOLOGY_SCOPE_INVALID");
    }
    if (mode == Mode.QUESTION && rawQuestions.isEmpty()) {
      throw failure("ONTOLOGY_SCOPE_QUESTION_REQUIRED");
    }
    if (mode == Mode.DISCOVERY && !rawQuestions.isEmpty()) {
      throw failure("ONTOLOGY_SCOPE_DISCOVERY_QUESTIONS_INVALID");
    }
    List<Question> questions = new ArrayList<>();
    Set<String> questionIds = new HashSet<>();
    Set<String> taskIds = new HashSet<>();
    for (JsonNode rawQuestion : rawQuestions) {
      Question question = question(rawQuestion, corpus, taskIds);
      if (!questionIds.add(question.questionId())) {
        throw failure("ONTOLOGY_SCOPE_QUESTION_DUPLICATE");
      }
      questions.add(question);
    }
    return new Scope(mode, selectionMode, questions);
  }

  private static Question question(
      JsonNode rawQuestion, OntologyEvidenceCorpus corpus, Set<String> taskIds) {
    requireObject(rawQuestion, "ONTOLOGY_SCOPE_QUESTION_INVALID");
    requireFields(
        rawQuestion,
        QUESTION_FIELDS,
        "ONTOLOGY_SCOPE_QUESTION_UNKNOWN_FIELD",
        "ONTOLOGY_SCOPE_QUESTION_REQUIRED_FIELD");
    String questionId = requiredText(rawQuestion, "questionId", "ONTOLOGY_SCOPE_QUESTION_INVALID");
    String question = requiredText(rawQuestion, "question", "ONTOLOGY_SCOPE_QUESTION_INVALID");
    List<String> entryRefs = entryRefs(rawQuestion.get("entryRefs"), corpus);
    List<String> clueRefs = clueRefs(rawQuestion.get("clueRefs"), corpus);
    JsonNode rawTasks = rawQuestion.get("tasks");
    if (!rawTasks.isArray() || rawTasks.isEmpty()) {
      throw failure("ONTOLOGY_SCOPE_TASK_REQUIRED");
    }
    List<Task> tasks = new ArrayList<>();
    int firstObject = -1;
    int firstDependent = -1;
    for (int index = 0; index < rawTasks.size(); index++) {
      Task task = task(rawTasks.get(index), corpus);
      if (!taskIds.add(task.taskId())) {
        throw failure("ONTOLOGY_SCOPE_TASK_DUPLICATE");
      }
      if (task.taskKind() == TaskKind.OBJECT && firstObject < 0) {
        firstObject = index;
      }
      if ((task.taskKind() == TaskKind.ACTION || task.taskKind() == TaskKind.ANALYTIC)
          && firstDependent < 0) {
        firstDependent = index;
      }
      tasks.add(task);
    }
    if (firstDependent >= 0 && (firstObject < 0 || firstObject > firstDependent)) {
      throw failure("ONTOLOGY_SCOPE_OBJECT_DEPENDENCY_MISSING");
    }
    return new Question(questionId, question, entryRefs, clueRefs, tasks);
  }

  private static Task task(JsonNode rawTask, OntologyEvidenceCorpus corpus) {
    requireObject(rawTask, "ONTOLOGY_SCOPE_TASK_INVALID");
    requireFields(
        rawTask,
        TASK_FIELDS,
        "ONTOLOGY_SCOPE_TASK_UNKNOWN_FIELD",
        "ONTOLOGY_SCOPE_TASK_REQUIRED_FIELD");
    String taskId = requiredText(rawTask, "taskId", "ONTOLOGY_SCOPE_TASK_INVALID");
    TaskKind taskKind;
    try {
      taskKind = TaskKind.valueOf(requiredText(rawTask, "taskKind", "ONTOLOGY_SCOPE_TASK_INVALID"));
    } catch (IllegalArgumentException invalid) {
      throw failure("ONTOLOGY_SCOPE_TASK_KIND_INVALID");
    }
    ReadingMode readingMode =
        enumValue(
            ReadingMode.class, requiredText(rawTask, "readingMode", "ONTOLOGY_SCOPE_TASK_INVALID"));
    return new Task(
        taskId,
        taskKind,
        readingMode,
        unitUses(rawTask.get("unitUses"), corpus),
        unitUses(rawTask.get("requiredUnitUses"), corpus));
  }

  static List<UnitUse> unitUses(JsonNode rawUses, OntologyEvidenceCorpus corpus) {
    if (rawUses == null || !rawUses.isArray()) {
      throw failure("ONTOLOGY_SCOPE_UNIT_USE_INVALID");
    }
    List<UnitUse> uses = new ArrayList<>();
    Set<UnitUse> unique = new HashSet<>();
    for (JsonNode rawUse : rawUses) {
      requireObject(rawUse, "ONTOLOGY_SCOPE_UNIT_USE_INVALID");
      requireFields(
          rawUse,
          UNIT_USE_FIELDS,
          "ONTOLOGY_SCOPE_UNIT_USE_UNKNOWN_FIELD",
          "ONTOLOGY_SCOPE_UNIT_USE_REQUIRED_FIELD");
      String unitRef = requiredText(rawUse, "unitRef", "ONTOLOGY_SCOPE_UNIT_USE_INVALID");
      String entryRef = requiredText(rawUse, "entryRef", "ONTOLOGY_SCOPE_UNIT_USE_INVALID");
      if (!unitRef.matches("U[1-9][0-9]*")) {
        throw failure("ONTOLOGY_SCOPE_UNIT_USE_CATEGORY_INVALID");
      }
      if (!entryRef.matches("E[1-9][0-9]*")) {
        throw failure("ONTOLOGY_SCOPE_UNIT_USE_CATEGORY_INVALID");
      }
      try {
        corpus.aliases().read(unitRef, entryRef);
      } catch (IllegalArgumentException invalid) {
        throw failure("ONTOLOGY_SCOPE_UNIT_USE_INVALID");
      }
      UnitUse use = new UnitUse(unitRef, entryRef);
      if (!unique.add(use)) {
        throw failure("ONTOLOGY_SCOPE_UNIT_USE_DUPLICATE");
      }
      uses.add(use);
    }
    return List.copyOf(uses);
  }

  private static List<String> entryRefs(JsonNode rawRefs, OntologyEvidenceCorpus corpus) {
    return references(
        rawRefs,
        corpus,
        "E[1-9][0-9]*",
        true,
        "ONTOLOGY_SCOPE_ENTRY_REF_CATEGORY_INVALID",
        "ONTOLOGY_SCOPE_ENTRY_REF_INVALID");
  }

  private static List<String> clueRefs(JsonNode rawRefs, OntologyEvidenceCorpus corpus) {
    return references(
        rawRefs,
        corpus,
        "K[1-9][0-9]*",
        false,
        "ONTOLOGY_SCOPE_CLUE_REF_CATEGORY_INVALID",
        "ONTOLOGY_SCOPE_CLUE_REF_INVALID");
  }

  private static List<String> references(
      JsonNode rawRefs,
      OntologyEvidenceCorpus corpus,
      String pattern,
      boolean entry,
      String categoryFailure,
      String invalidFailure) {
    if (rawRefs == null || !rawRefs.isArray()) {
      throw failure("ONTOLOGY_SCOPE_REFERENCE_INVALID");
    }
    List<String> refs = new ArrayList<>();
    Set<String> unique = new HashSet<>();
    for (JsonNode rawRef : rawRefs) {
      if (!rawRef.isTextual() || !rawRef.asText().matches(pattern)) {
        throw failure(categoryFailure);
      }
      String ref = rawRef.asText();
      try {
        if (entry) {
          corpus.aliases().entry(ref);
        } else {
          corpus.aliases().clue(ref);
        }
      } catch (IllegalArgumentException invalid) {
        throw failure(invalidFailure);
      }
      if (!unique.add(ref)) {
        throw failure("ONTOLOGY_SCOPE_REFERENCE_DUPLICATE");
      }
      refs.add(ref);
    }
    return List.copyOf(refs);
  }

  private static void requireObject(JsonNode node, String code) {
    if (node == null || !node.isObject()) {
      throw failure(code);
    }
  }

  private static void requireFields(
      JsonNode node, Set<String> fields, String unknownCode, String requiredCode) {
    node.fieldNames()
        .forEachRemaining(
            field -> {
              if (!fields.contains(field)) {
                throw failure(unknownCode);
              }
            });
    for (String field : fields) {
      if (!node.has(field)) {
        throw failure(requiredCode);
      }
    }
  }

  private static String requiredText(JsonNode node, String field, String code) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw failure(code);
    }
    return value.asText();
  }

  private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException invalid) {
      throw failure("ONTOLOGY_SCOPE_ENUM_INVALID");
    }
  }

  private static IllegalArgumentException failure(String code) {
    return new IllegalArgumentException(code);
  }

  public enum Mode {
    QUESTION,
    DISCOVERY
  }

  public enum SelectionMode {
    EXPLICIT,
    MODEL
  }

  public enum TaskKind {
    OBJECT,
    ACTION,
    ANALYTIC
  }

  public enum ReadingMode {
    EXPLICIT,
    MODEL
  }

  public record Scope(Mode mode, SelectionMode selectionMode, List<Question> questions) {
    public Scope {
      questions = List.copyOf(questions);
    }
  }

  public record Question(
      String questionId,
      String question,
      List<String> entryRefs,
      List<String> clueRefs,
      List<Task> tasks) {
    public Question {
      entryRefs = List.copyOf(entryRefs);
      clueRefs = List.copyOf(clueRefs);
      tasks = List.copyOf(tasks);
    }
  }

  public record Task(
      String taskId,
      TaskKind taskKind,
      ReadingMode readingMode,
      List<UnitUse> unitUses,
      List<UnitUse> requiredUnitUses) {
    public Task {
      unitUses = List.copyOf(unitUses);
      requiredUnitUses = List.copyOf(requiredUnitUses);
    }
  }

  public record UnitUse(String unitRef, String entryRef) {}
}
