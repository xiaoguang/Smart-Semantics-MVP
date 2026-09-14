package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.document.BusinessReportPublisher;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplainer;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityJobExecutionConfiguration;
import org.sourceanalysis.app.analysis.interpretation.material.BuildBusinessMaterialsRequest;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplainer;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;

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
  private final ActivityJobExecutionConfiguration activityJobExecutionConfiguration;
  private final ModelJobExecutionConfiguration modelJobExecutionConfiguration;

  /** Creates the application-internal continuation from persisted technical output. */
  public PersistedBusinessRunExecutor(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader,
      StructuredModelProvider provider,
      PersistedBusinessRunConfiguration configuration) {
    this(moduleArtifacts, analysisSteps, sourceReader, provider, configuration, null, null);
  }

  /** Creates the continuation with composition-root-provided Activity job execution values. */
  public PersistedBusinessRunExecutor(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader,
      StructuredModelProvider provider,
      PersistedBusinessRunConfiguration configuration,
      ActivityJobExecutionConfiguration activityJobExecutionConfiguration) {
    this(
        moduleArtifacts,
        analysisSteps,
        sourceReader,
        provider,
        configuration,
        activityJobExecutionConfiguration,
        null);
  }

  /** Creates the continuation with one validated multi-Provider run execution configuration. */
  public PersistedBusinessRunExecutor(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader,
      PersistedBusinessRunConfiguration configuration,
      ModelJobExecutionConfiguration modelJobExecutionConfiguration) {
    this(
        moduleArtifacts,
        analysisSteps,
        sourceReader,
        modelJobExecutionConfiguration.binding("processGroup", 0).provider(),
        configuration,
        null,
        modelJobExecutionConfiguration);
  }

  private PersistedBusinessRunExecutor(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader,
      StructuredModelProvider provider,
      PersistedBusinessRunConfiguration configuration,
      ActivityJobExecutionConfiguration activityJobExecutionConfiguration,
      ModelJobExecutionConfiguration modelJobExecutionConfiguration) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifacts");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis steps");
    this.sourceReader = Objects.requireNonNull(sourceReader, "verified source reader");
    this.provider = Objects.requireNonNull(provider, "structured model provider");
    this.configuration = Objects.requireNonNull(configuration, "business run configuration");
    this.activityJobExecutionConfiguration = activityJobExecutionConfiguration;
    this.modelJobExecutionConfiguration = modelJobExecutionConfiguration;
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

  /** Executes only model work from a freshly reopened immutable material checkpoint. */
  public BusinessAnalysisWorkflowResult execute(BusinessMaterialBuildResult materials) {
    Objects.requireNonNull(materials, "business materials");
    return workflow()
        .run(
            materials,
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
        activityExplainer(),
        processExplainer(),
        reportPublisher());
  }

  private ActivityExplainer activityExplainer() {
    if (modelJobExecutionConfiguration != null) {
      return ActivityExplainer.forExecution(moduleArtifacts, modelJobExecutionConfiguration);
    }
    if (activityJobExecutionConfiguration == null) {
      return new ActivityExplainer(provider, moduleArtifacts);
    }
    return ActivityExplainer.forExecution(
        provider, moduleArtifacts, activityJobExecutionConfiguration);
  }

  private ProcessExplainer processExplainer() {
    if (modelJobExecutionConfiguration != null) {
      return ProcessExplainer.forExecution(moduleArtifacts, modelJobExecutionConfiguration);
    }
    return new ProcessExplainer(provider, moduleArtifacts);
  }

  private BusinessReportPublisher reportPublisher() {
    if (modelJobExecutionConfiguration != null) {
      return BusinessReportPublisher.forExecution(moduleArtifacts, modelJobExecutionConfiguration);
    }
    StructuredModelProvider reportProvider = provider;
    return new BusinessReportPublisher(reportProvider, moduleArtifacts);
  }
}
