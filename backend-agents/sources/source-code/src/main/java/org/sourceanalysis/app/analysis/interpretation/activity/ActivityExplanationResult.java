package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** The reviewed activity records completed by this bounded execution. */
public record ActivityExplanationResult(
    List<ReviewedActivity> reviewedActivities,
    List<ActivityEntryCoverage> coverage,
    List<UnexplainedActivityEntry> unexplainedActivityEntries,
    Optional<List<ActivityPacketCompletion>> packetCompletion,
    ModulePublicationReference checkpoint) {

  public ActivityExplanationResult {
    reviewedActivities = List.copyOf(reviewedActivities);
    coverage = List.copyOf(coverage);
    unexplainedActivityEntries = List.copyOf(unexplainedActivityEntries);
    packetCompletion =
        Objects.requireNonNull(packetCompletion, "packet completion")
            .map(values -> List.copyOf(Objects.requireNonNull(values, "packet completion values")));
    if (coverage.stream().map(ActivityEntryCoverage::entryId).distinct().count()
        != coverage.size()) {
      throw new IllegalArgumentException("activity coverage is not closed");
    }
  }

  /** Preserves the existing checkpoint-bearing seam when no entry is explicitly unexplained. */
  public ActivityExplanationResult(
      List<ReviewedActivity> reviewedActivities,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries,
      ModulePublicationReference checkpoint) {
    this(reviewedActivities, coverage, unexplainedActivityEntries, Optional.empty(), checkpoint);
  }

  /** Creates a current result whose explicit packet-completion set may be empty. */
  public ActivityExplanationResult(
      List<ReviewedActivity> reviewedActivities,
      List<ActivityEntryCoverage> coverage,
      List<UnexplainedActivityEntry> unexplainedActivityEntries,
      List<ActivityPacketCompletion> packetCompletion,
      ModulePublicationReference checkpoint) {
    this(
        reviewedActivities,
        coverage,
        unexplainedActivityEntries,
        Optional.of(List.copyOf(Objects.requireNonNull(packetCompletion, "packet completion"))),
        checkpoint);
  }

  /** Preserves the existing checkpoint-bearing seam when no entry is explicitly unexplained. */
  public ActivityExplanationResult(
      List<ReviewedActivity> reviewedActivities,
      List<ActivityEntryCoverage> coverage,
      ModulePublicationReference checkpoint) {
    this(reviewedActivities, coverage, List.of(), Optional.empty(), checkpoint);
  }

  /** Returns an unpersisted preview result for narrow in-memory callers and unit tests. */
  public ActivityExplanationResult(
      List<ReviewedActivity> reviewedActivities, List<ActivityEntryCoverage> coverage) {
    this(reviewedActivities, coverage, List.of(), Optional.empty(), null);
  }
}
