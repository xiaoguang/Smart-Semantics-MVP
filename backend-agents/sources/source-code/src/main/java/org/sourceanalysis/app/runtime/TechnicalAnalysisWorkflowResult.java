package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** The JDT navigation, persistence, and reading-material publications for one technical run. */
public record TechnicalAnalysisWorkflowResult(
    VerifiedSourceInventoryReference verifiedSourceInventory,
    ApplicationDiscoveryReference applicationDiscovery,
    ProgramGraphsReference navigation,
    AnalysisStepPublicationReference persistence,
    AnalysisStepPublicationReference readingMaterials) {

  public TechnicalAnalysisWorkflowResult {
    Objects.requireNonNull(verifiedSourceInventory, "verified source inventory");
    Objects.requireNonNull(applicationDiscovery, "application discovery");
    Objects.requireNonNull(navigation, "navigation");
    Objects.requireNonNull(persistence, "persistence");
    Objects.requireNonNull(readingMaterials, "reading materials");
  }
}
