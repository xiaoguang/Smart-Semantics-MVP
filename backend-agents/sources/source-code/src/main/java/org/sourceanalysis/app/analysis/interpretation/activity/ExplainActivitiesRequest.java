package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;

/** M2 input: a typed, persisted material checkpoint plus bounded generation limits. */
public record ExplainActivitiesRequest(
    BusinessMaterialBuildResult materials,
    ActivityExplanationProfile profile,
    int maxMaterialsToStart) {

  public ExplainActivitiesRequest {
    Objects.requireNonNull(materials, "business materials");
    Objects.requireNonNull(profile, "activity explanation profile");
    if (maxMaterialsToStart < 0) {
      throw new IllegalArgumentException("maximum materials to start must not be negative");
    }
  }

  /** Preserves the existing in-memory seam for fully scripted fixtures. */
  public ExplainActivitiesRequest(
      BusinessMaterialBuildResult materials, ActivityExplanationProfile profile) {
    this(materials, profile, Integer.MAX_VALUE);
  }
}
