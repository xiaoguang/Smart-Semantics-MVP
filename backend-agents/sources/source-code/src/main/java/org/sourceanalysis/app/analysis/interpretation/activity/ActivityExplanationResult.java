package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.List;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** The reviewed activity records completed by this bounded execution. */
public record ActivityExplanationResult(
    List<ReviewedActivity> reviewedActivities,
    List<ActivityEntryCoverage> coverage,
    List<UnexplainedActivityEntry> unexplainedActivityEntries,
    ModulePublicationReference checkpoint) {

  public ActivityExplanationResult {
    reviewedActivities = List.copyOf(reviewedActivities);
    coverage = List.copyOf(coverage);
    unexplainedActivityEntries = List.copyOf(unexplainedActivityEntries);
    if (coverage.stream().map(ActivityEntryCoverage::entryId).distinct().count()
        != coverage.size()) {
      throw new IllegalArgumentException("activity coverage is not closed");
    }
  }

  /** Preserves the existing checkpoint-bearing seam when no entry is explicitly unexplained. */
  public ActivityExplanationResult(
      List<ReviewedActivity> reviewedActivities,
      List<ActivityEntryCoverage> coverage,
      ModulePublicationReference checkpoint) {
    this(reviewedActivities, coverage, List.of(), checkpoint);
  }

  /** Returns an unpersisted preview result for narrow in-memory callers and unit tests. */
  public ActivityExplanationResult(
      List<ReviewedActivity> reviewedActivities, List<ActivityEntryCoverage> coverage) {
    this(reviewedActivities, coverage, List.of(), null);
  }
}
