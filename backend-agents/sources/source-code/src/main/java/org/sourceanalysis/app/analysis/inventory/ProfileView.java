package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Read-only profile and resource-budget boundary used only for deterministic M1 admission. */
public record ProfileView(
    ArtifactReference profileBundleRef,
    ArtifactReference resourceBudgetRef,
    int maxSourceFiles,
    long maxSourceBytes) {

  public ProfileView {
    Objects.requireNonNull(profileBundleRef, "profile bundle reference");
    Objects.requireNonNull(resourceBudgetRef, "resource budget reference");
    if (maxSourceFiles < 1 || maxSourceBytes < 0L) {
      throw new IllegalArgumentException("source admission limits must be positive and finite");
    }
  }
}
