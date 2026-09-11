package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.List;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** The reviewed activity records completed by this bounded execution. */
public record ActivityExplanationResult(
    List<ReviewedActivity> reviewedActivities,
    List<ActivityEntryCoverage> coverage,
    ModulePublicationReference checkpoint) {

  public ActivityExplanationResult {
    reviewedActivities = List.copyOf(reviewedActivities);
    coverage = List.copyOf(coverage);
    if (coverage.stream().map(ActivityEntryCoverage::entryId).distinct().count()
        != coverage.size()) {
      throw new IllegalArgumentException("activity coverage is not closed");
    }
  }

  /** Returns an unpersisted preview result for narrow in-memory callers and unit tests. */
  public ActivityExplanationResult(
      List<ReviewedActivity> reviewedActivities, List<ActivityEntryCoverage> coverage) {
    this(reviewedActivities, coverage, null);
  }
}
