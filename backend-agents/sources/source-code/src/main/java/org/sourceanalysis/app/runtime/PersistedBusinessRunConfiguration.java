package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;

/** Closed, bootstrap-owned limits for the four business Modules. */
public record PersistedBusinessRunConfiguration(
    BusinessMaterialProfile materialProfile,
    ActivityExplanationProfile activityProfile,
    ProcessExplanationProfile processProfile,
    BusinessReportProfile reportProfile,
    int maxMaterialsToStart) {

  public PersistedBusinessRunConfiguration {
    Objects.requireNonNull(materialProfile, "business material profile");
    Objects.requireNonNull(activityProfile, "activity explanation profile");
    Objects.requireNonNull(processProfile, "process explanation profile");
    Objects.requireNonNull(reportProfile, "business report profile");
    if (maxMaterialsToStart < 1) {
      throw new IllegalArgumentException(
          "maximum materials to start must be positive for a final document run");
    }
  }

  /** Preserves the existing unlimited scripted-fixture configuration. */
  public PersistedBusinessRunConfiguration(
      BusinessMaterialProfile materialProfile,
      ActivityExplanationProfile activityProfile,
      ProcessExplanationProfile processProfile,
      BusinessReportProfile reportProfile) {
    this(materialProfile, activityProfile, processProfile, reportProfile, Integer.MAX_VALUE);
  }
}
