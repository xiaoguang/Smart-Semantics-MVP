package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;

/** The closed, persisted M1–M3 inputs for one application-discovery publication. */
public record ApplicationDiscoveryPublicationRequest(
    AnalysisStepPublicationAddress destination,
    VerifiedSourceInventoryReference verifiedSourceInventory,
    ApplicationProfileDraftReference applicationProfile,
    HttpEntryDiscoveryDraftReference httpEntryDiscovery,
    MapperCatalogDraftReference mapperCatalog) {

  public ApplicationDiscoveryPublicationRequest {
    destination = Objects.requireNonNull(destination, "application discovery destination");
    verifiedSourceInventory =
        Objects.requireNonNull(verifiedSourceInventory, "verified source inventory");
    applicationProfile = Objects.requireNonNull(applicationProfile, "application profile");
    httpEntryDiscovery = Objects.requireNonNull(httpEntryDiscovery, "HTTP entry discovery");
    mapperCatalog = Objects.requireNonNull(mapperCatalog, "Mapper catalog");
  }
}
