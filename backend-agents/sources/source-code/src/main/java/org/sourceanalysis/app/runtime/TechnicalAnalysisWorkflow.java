package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryExecutor;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryRequest;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.fact.ProvenCodeFactsExecutor;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.BusinessFlowsExecutionRequest;
import org.sourceanalysis.app.analysis.flow.BusinessFlowsExecutor;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;

/**
 * Coordinates persisted application discovery through technical Flow/Capsule publication.
 *
 * <p>The workflow begins only after verified source inventory has been installed. It reuses the
 * existing Step 02–05 executors, so it neither parses source itself nor accepts caller-created
 * graph, Fact, Flow, Capsule, or filesystem objects.
 */
public final class TechnicalAnalysisWorkflow {

  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;

  /** Creates the path-free runtime coordinator over verified source and canonical publications. */
  public TechnicalAnalysisWorkflow(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
  }

  /** Runs only application discovery from one previously published verified-source inventory. */
  public TechnicalDiscoveryWorkflowResult discover(
      VerifiedSourceInventoryReference verifiedSourceInventory, DiscoveryProfile discoveryProfile) {
    Objects.requireNonNull(verifiedSourceInventory, "verified source inventory");
    Objects.requireNonNull(discoveryProfile, "discovery profile");
    ApplicationDiscoveryReference discovery =
        new ApplicationDiscoveryExecutor(sourceReader, moduleArtifacts, stepArtifacts)
            .execute(
                new ApplicationDiscoveryRequest(
                    new AnalysisStepPublicationAddress(
                        verifiedSourceInventory.publication().address().runId(),
                        AnalysisStepKey.APPLICATION_DISCOVERY),
                    verifiedSourceInventory,
                    discoveryProfile));
    return new TechnicalDiscoveryWorkflowResult(verifiedSourceInventory, discovery);
  }

  /** Runs optional program graphs, technical facts, and flows after a verified discovery prefix. */
  public TechnicalAnalysisWorkflowResult continueAfterDiscovery(
      TechnicalDiscoveryWorkflowResult discoveryResult,
      ArtifactReference graphProfileRef,
      ArtifactControls artifactControls,
      FlowCompilationProfile flowProfile,
      CapsuleProjectionProfile capsuleProfile) {
    Objects.requireNonNull(discoveryResult, "technical discovery result");
    VerifiedSourceInventoryReference verifiedSourceInventory =
        discoveryResult.verifiedSourceInventory();
    ApplicationDiscoveryReference discovery = discoveryResult.applicationDiscovery();
    Objects.requireNonNull(graphProfileRef, "graph profile reference");
    Objects.requireNonNull(artifactControls, "artifact controls");
    Objects.requireNonNull(flowProfile, "Flow compilation profile");
    Objects.requireNonNull(capsuleProfile, "capsule projection profile");
    ProgramGraphsReference graphs =
        new ProgramGraphsExecution(sourceReader, moduleArtifacts, stepArtifacts)
            .execute(verifiedSourceInventory, discovery, graphProfileRef, artifactControls);
    ProvenCodeFactsReference facts =
        new ProvenCodeFactsExecutor(sourceReader, moduleArtifacts, stepArtifacts)
            .execute(verifiedSourceInventory, discovery, graphs);
    BusinessFlowsReference flows =
        new BusinessFlowsExecutor(sourceReader, moduleArtifacts, stepArtifacts)
            .execute(
                new BusinessFlowsExecutionRequest(
                    verifiedSourceInventory,
                    discovery,
                    graphs,
                    facts,
                    flowProfile,
                    capsuleProfile));
    return new TechnicalAnalysisWorkflowResult(
        verifiedSourceInventory, discovery, graphs, facts, flows);
  }

  /** Runs Step 02–05 in fixed order from one previously published verified-source inventory. */
  public TechnicalAnalysisWorkflowResult run(
      VerifiedSourceInventoryReference verifiedSourceInventory,
      DiscoveryProfile discoveryProfile,
      ArtifactReference graphProfileRef,
      ArtifactControls artifactControls,
      FlowCompilationProfile flowProfile,
      CapsuleProjectionProfile capsuleProfile) {
    return continueAfterDiscovery(
        discover(verifiedSourceInventory, discoveryProfile),
        graphProfileRef,
        artifactControls,
        flowProfile,
        capsuleProfile);
  }
}
