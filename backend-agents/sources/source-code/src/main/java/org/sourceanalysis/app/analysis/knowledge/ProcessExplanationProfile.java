package org.sourceanalysis.app.analysis.knowledge;

/** Deterministic limits for bounded, model-reviewed cross-activity process reconstruction. */
public record ProcessExplanationProfile(
    int maxActivitiesPerGroup,
    int maxProcessGroups,
    int maxModelInputBytes,
    int maxModelOutputBytes,
    int maxProcessesPerGroup,
    int maxValuesPerField,
    int maxTextCharsPerValue,
    int maxRepositorySummaryItems) {

  public ProcessExplanationProfile {
    if (maxActivitiesPerGroup < 1
        || maxProcessGroups < 1
        || maxModelInputBytes < 1
        || maxModelOutputBytes < 1
        || maxProcessesPerGroup < 1
        || maxValuesPerField < 1
        || maxTextCharsPerValue < 1
        || maxRepositorySummaryItems < 0) {
      throw new IllegalArgumentException("process explanation profile values must be positive");
    }
  }

  /**
   * Retains the pre-summary profile for callers that intentionally leave consolidation disabled.
   */
  public ProcessExplanationProfile(
      int maxActivitiesPerGroup,
      int maxProcessGroups,
      int maxModelInputBytes,
      int maxModelOutputBytes,
      int maxProcessesPerGroup,
      int maxValuesPerField,
      int maxTextCharsPerValue) {
    this(
        maxActivitiesPerGroup,
        maxProcessGroups,
        maxModelInputBytes,
        maxModelOutputBytes,
        maxProcessesPerGroup,
        maxValuesPerField,
        maxTextCharsPerValue,
        0);
  }
}
