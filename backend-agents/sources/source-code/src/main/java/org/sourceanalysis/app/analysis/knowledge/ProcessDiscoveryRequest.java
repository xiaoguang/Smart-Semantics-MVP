package org.sourceanalysis.app.analysis.knowledge;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;

/** Immutable reviewed-activity and saved-source input for one process-discovery execution. */
public record ProcessDiscoveryRequest(
    ActivityExplanationResult activities,
    BusinessMaterialBuildResult materials,
    ProcessDiscoveryProfile profile,
    AnalysisRunId outputRunId) {

  public ProcessDiscoveryRequest {
    activities = Objects.requireNonNull(activities, "reviewed activities");
    materials = Objects.requireNonNull(materials, "saved business materials");
    profile = Objects.requireNonNull(profile, "process discovery profile");
    outputRunId = Objects.requireNonNull(outputRunId, "process output run ID");
  }

  /** Convenience form for direct tests whose output shares the activity checkpoint owner. */
  public ProcessDiscoveryRequest(
      ActivityExplanationResult activities,
      BusinessMaterialBuildResult materials,
      ProcessDiscoveryProfile profile) {
    this(
        activities,
        materials,
        profile,
        ((AnalysisStepModuleAddress)
                Objects.requireNonNull(activities.checkpoint(), "activity checkpoint").address())
            .runId());
  }
}
