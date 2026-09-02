package org.sourceanalysis.app.analysis.discovery;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Static application capabilities determined from verified Maven and configuration bytes. */
public record ApplicationProfile(
    ArtifactId applicationProfileId,
    String snapshotId,
    String inventoryScopeKind,
    boolean repositoryCompletionEligible,
    ApplicationLanguage language,
    Integer languageVersion,
    List<FrameworkSignal> frameworkSignals,
    List<ConfigSignal> configSignals,
    ArtifactReference capabilityProfileRef,
    ArtifactReference sourceInventoryRef,
    ArtifactReference verifiedSnapshotRef,
    ArtifactControls controls) {

  public ApplicationProfile {
    Objects.requireNonNull(applicationProfileId, "application profile id");
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("snapshot identity must be canonical");
    }
    Objects.requireNonNull(language, "language");
    if (!"COMPLETE_CAPTURE".equals(inventoryScopeKind)
        && !"BOUNDED_PATH_SET".equals(inventoryScopeKind)) {
      throw new IllegalArgumentException("inventory scope kind is invalid");
    }
    if (languageVersion != null && languageVersion < 1) {
      throw new IllegalArgumentException("language version must be positive when known");
    }
    frameworkSignals = List.copyOf(frameworkSignals);
    configSignals = List.copyOf(configSignals);
    Objects.requireNonNull(capabilityProfileRef, "capability profile reference");
    Objects.requireNonNull(sourceInventoryRef, "source inventory reference");
    Objects.requireNonNull(verifiedSnapshotRef, "verified snapshot reference");
    Objects.requireNonNull(controls, "artifact controls");
  }
}
