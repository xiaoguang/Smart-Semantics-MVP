package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;

/** Closed input for executing the complete deterministic application-discovery step. */
public record ApplicationDiscoveryRequest(
    AnalysisStepPublicationAddress destination,
    VerifiedSourceInventoryReference verifiedSourceInventory,
    DiscoveryProfile discoveryProfile) {

  public ApplicationDiscoveryRequest {
    destination = Objects.requireNonNull(destination, "application discovery destination");
    verifiedSourceInventory =
        Objects.requireNonNull(verifiedSourceInventory, "verified source inventory");
    discoveryProfile = Objects.requireNonNull(discoveryProfile, "discovery profile");
  }
}
