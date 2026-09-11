package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.List;

/** Complete, reviewed business-language account of one program-owned local activity. */
public record ReviewedActivity(
    String activityId,
    String materialId,
    List<String> entryIds,
    String name,
    String businessPurpose,
    List<String> participants,
    List<String> businessObjects,
    List<String> triggerOrInput,
    List<String> conditions,
    List<String> activitySteps,
    List<String> codeDefinedResults,
    List<String> businessRules,
    List<String> formulasOrMetrics,
    List<String> terms,
    String certainty,
    List<String> sourceRefs,
    List<String> questions,
    List<String> scopeLimitations) {

  public ReviewedActivity {
    required(activityId, "activity ID");
    required(materialId, "material ID");
    required(name, "activity name");
    required(businessPurpose, "business purpose");
    required(certainty, "certainty");
    entryIds = List.copyOf(entryIds);
    participants = List.copyOf(participants);
    businessObjects = List.copyOf(businessObjects);
    triggerOrInput = List.copyOf(triggerOrInput);
    conditions = List.copyOf(conditions);
    activitySteps = List.copyOf(activitySteps);
    codeDefinedResults = List.copyOf(codeDefinedResults);
    businessRules = List.copyOf(businessRules);
    formulasOrMetrics = List.copyOf(formulasOrMetrics);
    terms = List.copyOf(terms);
    sourceRefs = List.copyOf(sourceRefs);
    questions = List.copyOf(questions);
    scopeLimitations = List.copyOf(scopeLimitations);
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
