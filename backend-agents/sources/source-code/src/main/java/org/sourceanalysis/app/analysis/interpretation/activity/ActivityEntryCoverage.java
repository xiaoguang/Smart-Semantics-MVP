package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.List;

/** One discovered-entry disposition after local business activity explanation. */
public record ActivityEntryCoverage(
    String entryId, String disposition, List<String> activityIds, String reasonCode) {

  public ActivityEntryCoverage {
    required(entryId, "entry ID");
    if (!("ANALYZED".equals(disposition)
        || "ANALYZED_WITH_GAPS".equals(disposition)
        || "NOT_ANALYZED".equals(disposition))) {
      throw new IllegalArgumentException("activity coverage disposition is invalid");
    }
    activityIds = List.copyOf(activityIds);
    if ("NOT_ANALYZED".equals(disposition)) {
      if (!activityIds.isEmpty()) {
        throw new IllegalArgumentException("unanalysed entry cannot have activity IDs");
      }
      required(reasonCode, "not-analysed reason code");
    } else if (activityIds.isEmpty()
        || (reasonCode != null
            && !("ANALYZED_WITH_GAPS".equals(disposition)
                && "ACTIVITY_READING_INCOMPLETE".equals(reasonCode)))) {
      throw new IllegalArgumentException("analysed entry coverage is invalid");
    }
  }

  /** A reviewed fragment exists, but a model-requested reading scope was not completed. */
  public boolean requiredScopeIncomplete() {
    return "ACTIVITY_READING_INCOMPLETE".equals(reasonCode);
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
