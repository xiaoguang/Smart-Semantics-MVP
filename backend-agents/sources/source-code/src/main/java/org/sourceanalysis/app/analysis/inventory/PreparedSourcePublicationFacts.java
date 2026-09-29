package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Exact v3 publication facts needed to project a prepared source into the typed text view. */
public record PreparedSourcePublicationFacts(
    ArtifactReference capabilityProfileRef,
    ArtifactReference sourceInventoryRef,
    ArtifactReference verifiedSnapshotRef,
    ArtifactControls controls) {

  public PreparedSourcePublicationFacts {
    Objects.requireNonNull(capabilityProfileRef, "prepared source capability profile reference");
    Objects.requireNonNull(sourceInventoryRef, "prepared source inventory reference");
    Objects.requireNonNull(verifiedSnapshotRef, "prepared source result reference");
    Objects.requireNonNull(controls, "prepared source controls");
  }
}
