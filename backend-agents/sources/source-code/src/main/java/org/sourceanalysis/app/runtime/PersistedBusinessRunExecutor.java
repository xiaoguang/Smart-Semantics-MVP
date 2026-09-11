package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.document.BusinessReportPublisher;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplainer;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;

/**
 * Executes the business-first portion of one analysis from an already-persisted Flow publication.
 *
 * <p>Referenced publications are reopened by {@link BusinessMaterialBuilder}; this executor
 * therefore neither accepts a source path nor rebuilds technical graphs, Facts, Proofs, or Flows.
 * It composes the existing four deep business Modules with one explicitly selected Provider.
 */
public final class PersistedBusinessRunExecutor {

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final VerifiedSourceTextReader sourceReader;
  private final StructuredModelProvider provider;
  private final PersistedBusinessRunConfiguration configuration;

  /** Creates the application-internal continuation from persisted technical output. */
  public PersistedBusinessRunExecutor(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader,
      StructuredModelProvider provider,
      PersistedBusinessRunConfiguration configuration) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifacts");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis steps");
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.provider = Objects.requireNonNull(provider, "structured model provider");
    this.configuration = Objects.requireNonNull(configuration, "business run configuration");
  }

  /**
   * Produces durable material, activity, knowledge, and report checkpoints from one Flow reference.
   */
  public BusinessAnalysisWorkflowResult execute(BusinessFlowsReference businessFlows) {
    Objects.requireNonNull(businessFlows, "business flows");
    return workflow()
        .run(
            businessFlows,
            configuration.materialProfile(),
            configuration.activityProfile(),
            configuration.maxMaterialsToStart(),
            configuration.processProfile(),
            configuration.reportProfile());
  }

  /** Builds saved model-reading material from the completed Step05 publication only. */
  public BusinessMaterialBuildResult buildMaterials(BusinessFlowsReference businessFlows) {
    Objects.requireNonNull(businessFlows, "business flows");
    return new BusinessMaterialBuilder(moduleArtifacts, analysisSteps, sourceReader)
        .build(new BuildBusinessMaterialsRequest(businessFlows, configuration.materialProfile()));
  }

  private BusinessAnalysisWorkflow workflow() {
    return new BusinessAnalysisWorkflow(
        new BusinessMaterialBuilder(moduleArtifacts, analysisSteps, sourceReader),
        new ActivityExplainer(provider, moduleArtifacts),
        new ProcessExplainer(provider, moduleArtifacts),
        new BusinessReportPublisher(provider, moduleArtifacts));
  }
}
