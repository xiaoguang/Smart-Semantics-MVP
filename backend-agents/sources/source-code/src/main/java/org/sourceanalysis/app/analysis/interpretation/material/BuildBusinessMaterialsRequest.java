package org.sourceanalysis.app.analysis.interpretation.material;

import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;

/**
 * Path-free request to project one published technical Flow set into readable business material.
 */
public record BuildBusinessMaterialsRequest(
    BusinessFlowsReference businessFlows,
    VerifiedSourceInventoryReference sourceInventory,
    ApplicationDiscoveryReference applicationDiscovery,
    BusinessMaterialProfile profile) {

  public BuildBusinessMaterialsRequest {
    if (businessFlows != null) {
      if (sourceInventory != null || applicationDiscovery != null) {
        throw new IllegalArgumentException("material request must name exactly one input mode");
      }
    } else {
      Objects.requireNonNull(sourceInventory, "verified source inventory");
      Objects.requireNonNull(applicationDiscovery, "application discovery");
    }
    Objects.requireNonNull(profile, "business material profile");
  }

  /** Uses Flow/Capsule material when the technical Flow step has already published. */
  public BuildBusinessMaterialsRequest(
      BusinessFlowsReference businessFlows, BusinessMaterialProfile profile) {
    this(businessFlows, null, null, profile);
  }

  /** Uses safely located discovered-entry source when technical Flow publication is unavailable. */
  public BuildBusinessMaterialsRequest(
      VerifiedSourceInventoryReference sourceInventory,
      ApplicationDiscoveryReference applicationDiscovery,
      BusinessMaterialProfile profile) {
    this(null, sourceInventory, applicationDiscovery, profile);
  }

  boolean usesPublishedFlows() {
    return businessFlows != null;
  }
}
