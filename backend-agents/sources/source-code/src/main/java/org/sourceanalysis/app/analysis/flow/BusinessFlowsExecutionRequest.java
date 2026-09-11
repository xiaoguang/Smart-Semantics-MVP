package org.sourceanalysis.app.analysis.flow;

import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;

/** Closed typed input for compiling and publishing one persisted business-flow step. */
public record BusinessFlowsExecutionRequest(
    VerifiedSourceInventoryReference verifiedSource,
    ApplicationDiscoveryReference applicationDiscovery,
    ProgramGraphsReference programGraphs,
    ProvenCodeFactsReference provenCodeFacts,
    FlowCompilationProfile flowProfile,
    CapsuleProjectionProfile capsuleProfile) {

  public BusinessFlowsExecutionRequest {
    verifiedSource = Objects.requireNonNull(verifiedSource, "verified source inventory");
    applicationDiscovery = Objects.requireNonNull(applicationDiscovery, "application discovery");
    programGraphs = Objects.requireNonNull(programGraphs, "program graphs");
    provenCodeFacts = Objects.requireNonNull(provenCodeFacts, "proven code facts");
    flowProfile = Objects.requireNonNull(flowProfile, "Flow compilation profile");
    capsuleProfile = Objects.requireNonNull(capsuleProfile, "capsule projection profile");
  }
}
