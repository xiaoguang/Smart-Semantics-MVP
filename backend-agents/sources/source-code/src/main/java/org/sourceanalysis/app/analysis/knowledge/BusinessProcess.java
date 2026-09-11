package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;

/** One complete, reviewed business-process interpretation over one or more local activities. */
public record BusinessProcess(
    String processId,
    String name,
    String businessPurpose,
    List<String> activityIds,
    List<BusinessProcessStage> stages,
    List<String> branches,
    List<String> sharedObjects,
    List<String> codeDefinedResults,
    String certainty,
    List<String> sourceRefs,
    List<String> confirmationNotes) {

  public BusinessProcess {
    required(processId, "process ID");
    required(name, "process name");
    required(businessPurpose, "process purpose");
    required(certainty, "process certainty");
    activityIds = List.copyOf(activityIds);
    stages = List.copyOf(stages);
    branches = List.copyOf(branches);
    sharedObjects = List.copyOf(sharedObjects);
    codeDefinedResults = List.copyOf(codeDefinedResults);
    sourceRefs = List.copyOf(sourceRefs);
    confirmationNotes = List.copyOf(confirmationNotes);
    if (activityIds.isEmpty() || sourceRefs.isEmpty()) {
      throw new IllegalArgumentException(
          "business process requires activities and source references");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
