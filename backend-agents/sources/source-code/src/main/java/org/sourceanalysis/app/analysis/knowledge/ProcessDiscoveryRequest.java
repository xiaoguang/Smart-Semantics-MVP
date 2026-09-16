package org.sourceanalysis.app.analysis.knowledge;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Immutable reviewed-activity and saved-source input for one process-discovery execution. */
public record ProcessDiscoveryRequest(
    ActivityExplanationResult activities,
    BusinessMaterialBuildResult materials,
    ProcessDiscoveryProfile profile,
    AnalysisRunId outputRunId,
    VerifiedSourceInventoryReference sourceInventoryReference,
    VerifiedSourceTextReader sourceTextReader,
    ImmutableBytes savedCatalogInput,
    String focusQuestion) {

  public ProcessDiscoveryRequest {
    activities = Objects.requireNonNull(activities, "reviewed activities");
    materials = Objects.requireNonNull(materials, "saved business materials");
    profile = Objects.requireNonNull(profile, "process discovery profile");
    outputRunId = Objects.requireNonNull(outputRunId, "process output run ID");
    if ((sourceInventoryReference == null) != (sourceTextReader == null)) {
      throw new IllegalArgumentException(
          "process source inventory reference and text reader must be supplied together");
    }
  }

  /** Convenience form for executions without the optional cross-object reading inputs. */
  public ProcessDiscoveryRequest(
      ActivityExplanationResult activities,
      BusinessMaterialBuildResult materials,
      ProcessDiscoveryProfile profile,
      AnalysisRunId outputRunId) {
    this(activities, materials, profile, outputRunId, null, null, null, null);
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
            .runId(),
        null,
        null,
        null,
        null);
  }
}
