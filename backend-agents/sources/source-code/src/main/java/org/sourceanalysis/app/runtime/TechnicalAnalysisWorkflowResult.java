package org.sourceanalysis.app.runtime;

import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;

/** The five persisted technical analysis publications in their fixed downstream order. */
public record TechnicalAnalysisWorkflowResult(
    VerifiedSourceInventoryReference verifiedSourceInventory,
    ApplicationDiscoveryReference applicationDiscovery,
    ProgramGraphsReference programGraphs,
    ProvenCodeFactsReference provenCodeFacts,
    BusinessFlowsReference businessFlows) {}
