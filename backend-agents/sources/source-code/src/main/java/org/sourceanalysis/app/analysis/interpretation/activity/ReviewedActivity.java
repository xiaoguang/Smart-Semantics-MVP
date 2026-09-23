package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.List;
import java.util.Map;

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
    List<String> scopeLimitations,
    String materialSource,
    String sliceKey,
    Map<String, String> originalSourceRefs) {

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
    if (!"BUSINESS_MATERIALS".equals(materialSource)
        && !"CODE_READING_MATERIALS".equals(materialSource)) {
      throw new IllegalArgumentException("activity material source is invalid");
    }
    originalSourceRefs = Map.copyOf(originalSourceRefs);
    if ("CODE_READING_MATERIALS".equals(materialSource)
        && (originalSourceRefs.isEmpty() || !originalSourceRefs.keySet().containsAll(sourceRefs))) {
      throw new IllegalArgumentException("Step05 activity source mapping is incomplete");
    }
  }

  /** Legacy M10 activities have no Step05 source mapping. */
  public ReviewedActivity(
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
    this(
        activityId,
        materialId,
        entryIds,
        name,
        businessPurpose,
        participants,
        businessObjects,
        triggerOrInput,
        conditions,
        activitySteps,
        codeDefinedResults,
        businessRules,
        formulasOrMetrics,
        terms,
        certainty,
        sourceRefs,
        questions,
        scopeLimitations,
        "BUSINESS_MATERIALS",
        null,
        Map.of());
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
