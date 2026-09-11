package org.sourceanalysis.app.analysis.knowledge;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;

/** Input to the one deep Module that turns reviewed local activities into repository knowledge. */
public record ExplainRepositoryProcessesRequest(
    ActivityExplanationResult activities,
    BusinessMaterialSet materials,
    ProcessExplanationProfile profile) {

  public ExplainRepositoryProcessesRequest {
    Objects.requireNonNull(activities, "reviewed activities");
    Objects.requireNonNull(profile, "process explanation profile");
  }

  /**
   * Creates an activity-only request for a narrow in-memory caller. Production workflow callers
   * should provide the already-built material set so recall can use opaque technical cues.
   */
  public ExplainRepositoryProcessesRequest(
      ActivityExplanationResult activities, ProcessExplanationProfile profile) {
    this(activities, null, profile);
  }
}
