package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;

/**
 * Persisted source inventory and application discovery available before optional deeper technical
 * analysis.
 *
 * <p>This prefix is sufficient for the business-material fallback route. Program graphs, Facts, and
 * Flows remain available to callers that explicitly continue the technical workflow.
 */
public record TechnicalDiscoveryWorkflowResult(
    VerifiedSourceInventoryReference verifiedSourceInventory,
    ApplicationDiscoveryReference applicationDiscovery) {

  public TechnicalDiscoveryWorkflowResult {
    Objects.requireNonNull(verifiedSourceInventory, "verified source inventory");
    Objects.requireNonNull(applicationDiscovery, "application discovery");
    if (!verifiedSourceInventory
        .publication()
        .address()
        .runId()
        .equals(applicationDiscovery.publication().address().runId())) {
      throw new IllegalArgumentException("TECHNICAL_DISCOVERY_RUN_MISMATCH");
    }
  }
}
