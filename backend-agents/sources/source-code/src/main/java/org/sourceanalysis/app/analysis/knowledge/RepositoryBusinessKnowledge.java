package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.analysis.interpretation.activity.UnexplainedActivityEntry;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Complete business knowledge kept independently of the future nine-section report. */
public record RepositoryBusinessKnowledge(
    List<ReviewedActivity> activities,
    List<BusinessProcess> processes,
    List<ActivityEntryCoverage> activityCoverage,
    List<UnexplainedActivityEntry> unexplainedActivityEntries,
    List<String> unmatchedActivityIds,
    List<String> confirmationTopics,
    List<String> notConsolidatedProcessIds,
    RepositoryProcessSummary repositorySummary,
    ModulePublicationReference checkpoint) {

  public RepositoryBusinessKnowledge {
    activities = List.copyOf(activities);
    processes = List.copyOf(processes);
    activityCoverage = List.copyOf(activityCoverage);
    unexplainedActivityEntries = List.copyOf(unexplainedActivityEntries);
    unmatchedActivityIds = List.copyOf(unmatchedActivityIds);
    confirmationTopics = List.copyOf(confirmationTopics);
    notConsolidatedProcessIds = List.copyOf(notConsolidatedProcessIds);
  }

  /** Returns a compatibility-free in-memory convenience value with no unexplained entries. */
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
        List.of(),
        unmatchedActivityIds,
        confirmationTopics,
        List.of(),
        null,
        null);
  }
}
