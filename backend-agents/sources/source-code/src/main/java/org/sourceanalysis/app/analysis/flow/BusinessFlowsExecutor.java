package org.sourceanalysis.app.analysis.flow;

import java.util.Objects;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjection;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionModulePublisher;
import org.sourceanalysis.app.analysis.flow.capsule.EvidenceCapsuleProjector;
import org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/**
 * Executes the existing Flow compiler, evidence-capsule projector, and public Flow publisher.
 *
 * <p>This is only the Step 05 ordering seam. It accepts already-persisted technical references and
 * resource profiles, publishes M1 and M2 before M3 reopens them, and never reparses source or
 * assigns business semantics.
 */
public final class BusinessFlowsExecutor {

  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;

  /** Creates the only path-free execution seam for the persisted Step 05 modules. */
  public BusinessFlowsExecutor(
      VerifiedSourceTextReader sourceReader,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts) {
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.stepArtifacts = Objects.requireNonNull(stepArtifacts, "analysis step artifact store");
  }

  /** Compiles every discovered entry, projects each compiled Flow, and publishes the five files. */
  public BusinessFlowsReference execute(BusinessFlowsExecutionRequest request) {
    Objects.requireNonNull(request, "business flows execution request");
    FlowCompilation compilation =
        new EntryRootedFlowCompiler(stepArtifacts)
            .compile(
                request.applicationDiscovery(),
                request.programGraphs(),
                request.provenCodeFacts(),
                request.flowProfile());
    ModulePublicationReference flowCompilation =
        new FlowCompilationModulePublisher(moduleArtifacts, stepArtifacts)
            .publish(
                request.applicationDiscovery(),
                request.programGraphs(),
                request.provenCodeFacts(),
                compilation);

    CapsuleProjection projection =
        new EvidenceCapsuleProjector(moduleArtifacts, stepArtifacts, sourceReader)
            .project(
                flowCompilation,
                request.verifiedSource(),
                request.programGraphs(),
                request.provenCodeFacts(),
                request.capsuleProfile());
    ModulePublicationReference capsuleProjection =
        new CapsuleProjectionModulePublisher(moduleArtifacts, stepArtifacts, sourceReader)
            .publish(
                flowCompilation,
                request.verifiedSource(),
                request.programGraphs(),
                request.provenCodeFacts(),
                projection);

    return new FlowPublicationSpecifier(moduleArtifacts, stepArtifacts, sourceReader)
        .specify(
            request.verifiedSource(),
            request.applicationDiscovery(),
            request.programGraphs(),
            request.provenCodeFacts(),
            flowCompilation,
            capsuleProjection);
  }
}
