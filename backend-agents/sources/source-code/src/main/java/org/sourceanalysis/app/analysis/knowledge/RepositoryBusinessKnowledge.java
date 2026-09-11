package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Complete business knowledge kept independently of the future nine-section report. */
public record RepositoryBusinessKnowledge(
    List<ReviewedActivity> activities,
    List<BusinessProcess> processes,
    List<ActivityEntryCoverage> activityCoverage,
    List<String> unmatchedActivityIds,
    List<String> confirmationTopics,
    List<String> notConsolidatedProcessIds,
    RepositoryProcessSummary repositorySummary,
    ModulePublicationReference checkpoint) {

  public RepositoryBusinessKnowledge {
    activities = List.copyOf(activities);
    processes = List.copyOf(processes);
    activityCoverage = List.copyOf(activityCoverage);
    unmatchedActivityIds = List.copyOf(unmatchedActivityIds);
    confirmationTopics = List.copyOf(confirmationTopics);
    notConsolidatedProcessIds = List.copyOf(notConsolidatedProcessIds);
  }

  /** Returns an unpersisted preview result for narrow in-memory callers and unit tests. */
  public RepositoryBusinessKnowledge(
      List<ReviewedActivity> activities,
      List<BusinessProcess> processes,
      List<ActivityEntryCoverage> activityCoverage,
      List<String> unmatchedActivityIds,
      List<String> confirmationTopics) {
    this(
        activities,
        processes,
        activityCoverage,
        unmatchedActivityIds,
        confirmationTopics,
        List.of(),
        null,
        null);
  }
}
