package org.sourceanalysis.app.analysis.ontology;

import java.util.List;
import java.util.Objects;

/** Immutable disposition of one declared ontology task, independent from a prepared model job. */
public record OntologyTaskOutcome(
    String questionId,
    String taskId,
    OntologyTaskRunner.TaskKind taskKind,
    Status status,
    String producingTaskId,
    String jobKey,
    List<TaskReference> dependencyTaskRefs,
    FailureReason reason) {

  public OntologyTaskOutcome {
    requireText(questionId, "ontology task outcome question ID");
    requireText(taskId, "ontology task outcome task ID");
    taskKind = Objects.requireNonNull(taskKind, "ontology task outcome kind");
    status = Objects.requireNonNull(status, "ontology task outcome status");
    dependencyTaskRefs =
        List.copyOf(Objects.requireNonNull(dependencyTaskRefs, "task dependencies"));
    if (status == Status.REVIEWED) {
      requireText(producingTaskId, "reviewed producing task ID");
      requireText(jobKey, "reviewed job key");
      if (reason != null) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
    } else if (status == Status.REJECTED) {
      requireText(producingTaskId, "rejected producing task ID");
      requireText(jobKey, "rejected job key");
      reason = Objects.requireNonNull(reason, "rejected task reason");
    } else {
      if ((producingTaskId == null) != (jobKey == null)) {
        throw new IllegalArgumentException("ONTOLOGY_TASK_OUTCOME_INVALID");
      }
      if (producingTaskId != null) {
        requireText(producingTaskId, "unprocessed producing task ID");
        requireText(jobKey, "unprocessed job key");
      }
      reason = Objects.requireNonNull(reason, "unprocessed task reason");
    }
  }

  public enum Status {
    REVIEWED,
    REJECTED,
    UNPROCESSED
  }

  /** A saved run/task range reference; it is not a generic dependency graph edge. */
  public record TaskReference(String runId, String questionId, String taskId) {
    public TaskReference {
      requireText(runId, "ontology dependency run ID");
      requireText(questionId, "ontology dependency question ID");
      requireText(taskId, "ontology dependency task ID");
    }
  }

  /** Producer-owned machine reason; no exception message is classified by this contract. */
  public record FailureReason(
      String code,
      Category category,
      String stage,
      String jsonPointer,
      String offendingRef,
      List<String> expectedRefs,
      List<TaskReference> dependencyTaskRefs) {
    public FailureReason {
      requireText(code, "ontology failure code");
      category = Objects.requireNonNull(category, "ontology failure category");
      stage = optionalText(stage, "ontology failure stage");
      jsonPointer = optionalText(jsonPointer, "ontology failure JSON pointer");
      offendingRef = optionalText(offendingRef, "ontology failure offending reference");
      expectedRefs = List.copyOf(Objects.requireNonNull(expectedRefs, "ontology expected refs"));
      dependencyTaskRefs =
          List.copyOf(Objects.requireNonNull(dependencyTaskRefs, "ontology failure dependencies"));
    }
  }

  public enum Category {
    MODEL_OUTPUT,
    MATERIAL,
    DEPENDENCY,
    SOURCE,
    CONFIGURATION,
    PROVIDER,
    STORAGE,
    DISPATCH_LIMIT,
    ASSEMBLY,
    UNKNOWN
  }

  private static String optionalText(String value, String label) {
    if (value != null && value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }

  private static void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
