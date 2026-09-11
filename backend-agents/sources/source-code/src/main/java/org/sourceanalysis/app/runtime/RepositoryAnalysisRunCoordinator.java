package org.sourceanalysis.app.runtime;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/**
 * Internal one-way composition from a persisted run identifier to one business-language report.
 *
 * <p>The technical executor remains the only owner of source capture and Steps 01–05. Material
 * planning stops after persisted inventory and discovery, while final document execution continues
 * through the persisted Flow/Capsule publication. It does not create a second source-reading or
 * model execution path.
 */
public final class RepositoryAnalysisRunCoordinator {

  private final Function<AnalysisRunId, TechnicalDiscoveryWorkflowResult> discoveryExecution;
  private final Function<AnalysisRunId, TechnicalAnalysisWorkflowResult> completeTechnicalExecution;
  private final BiFunction<
          VerifiedSourceInventoryReference,
          ApplicationDiscoveryReference,
          BusinessMaterialBuildResult>
      materialPlanning;
  private final BiFunction<
          VerifiedSourceInventoryReference,
          ApplicationDiscoveryReference,
          BusinessAnalysisWorkflowResult>
      fallbackBusinessExecution;
  private final Function<BusinessFlowsReference, BusinessAnalysisWorkflowResult> flowBusinessExecution;

  public RepositoryAnalysisRunCoordinator(
      PersistedTechnicalRunExecutor technicalExecutor,
      PersistedBusinessRunExecutor businessExecutor) {
    this(
        technicalExecutor::executeThroughApplicationDiscovery,
        technicalExecutor::execute,
        businessExecutor::buildMaterials,
        businessExecutor::execute,
        businessExecutor::execute);
  }

  /** Package-private test seam; production uses the persisted-executor constructor. */
  RepositoryAnalysisRunCoordinator(
      Function<AnalysisRunId, TechnicalDiscoveryWorkflowResult> technicalExecution,
      BiFunction<
          VerifiedSourceInventoryReference,
          ApplicationDiscoveryReference,
          BusinessAnalysisWorkflowResult>
          businessExecution) {
    this(
        technicalExecution,
        null,
        (inventory, discovery) -> {
          throw new IllegalStateException("REPOSITORY_MATERIAL_PLANNING_NOT_CONFIGURED");
        },
        businessExecution,
        null);
  }

  /** Package-private test seam for independent material planning and final business execution. */
  RepositoryAnalysisRunCoordinator(
      Function<AnalysisRunId, TechnicalDiscoveryWorkflowResult> technicalExecution,
      BiFunction<
              VerifiedSourceInventoryReference,
              ApplicationDiscoveryReference,
              BusinessMaterialBuildResult>
          materialPlanning,
      BiFunction<
              VerifiedSourceInventoryReference,
              ApplicationDiscoveryReference,
              BusinessAnalysisWorkflowResult>
          businessExecution) {
    this(technicalExecution, null, materialPlanning, businessExecution, null);
  }

  private RepositoryAnalysisRunCoordinator(
      Function<AnalysisRunId, TechnicalDiscoveryWorkflowResult> discoveryExecution,
      Function<AnalysisRunId, TechnicalAnalysisWorkflowResult> completeTechnicalExecution,
      BiFunction<
              VerifiedSourceInventoryReference,
              ApplicationDiscoveryReference,
              BusinessMaterialBuildResult>
          materialPlanning,
      BiFunction<
              VerifiedSourceInventoryReference,
              ApplicationDiscoveryReference,
              BusinessAnalysisWorkflowResult>
          fallbackBusinessExecution,
      Function<BusinessFlowsReference, BusinessAnalysisWorkflowResult> flowBusinessExecution) {
    this.discoveryExecution = Objects.requireNonNull(discoveryExecution, "discovery execution");
    this.completeTechnicalExecution = completeTechnicalExecution;
    this.materialPlanning = Objects.requireNonNull(materialPlanning, "material planning");
    this.fallbackBusinessExecution = Objects.requireNonNull(fallbackBusinessExecution, "business execution");
    this.flowBusinessExecution = flowBusinessExecution;
  }

  /** Executes the persisted technical prefix and the zero-Provider business material build only. */
  public RepositoryMaterialPlanningResult planMaterials(AnalysisRunId runId) {
    Objects.requireNonNull(runId, "analysis run ID");
    TechnicalDiscoveryWorkflowResult technical = technical(runId);
    BusinessMaterialBuildResult materials =
        materialPlanning.apply(technical.verifiedSourceInventory(), technical.applicationDiscovery());
    if (materials == null) {
      throw new IllegalStateException("REPOSITORY_ANALYSIS_MATERIAL_RESULT_INVALID");
    }
    return new RepositoryMaterialPlanningResult(technical, materials);
  }

  /** Executes one newly queued run in the only permitted source-to-report order. */
  public RepositoryAnalysisRunResult execute(AnalysisRunId runId) {
    Objects.requireNonNull(runId, "analysis run ID");
    if (completeTechnicalExecution == null || flowBusinessExecution == null) {
      return executeFallback(runId);
    }
    TechnicalAnalysisWorkflowResult completedTechnical = completeTechnicalExecution.apply(runId);
    if (completedTechnical == null) {
      throw new IllegalStateException("REPOSITORY_ANALYSIS_TECHNICAL_RESULT_INVALID");
    }
    TechnicalDiscoveryWorkflowResult technical =
        new TechnicalDiscoveryWorkflowResult(
            completedTechnical.verifiedSourceInventory(), completedTechnical.applicationDiscovery());
    BusinessAnalysisWorkflowResult business =
        flowBusinessExecution.apply(completedTechnical.businessFlows());
    if (business == null) {
      throw new IllegalStateException("REPOSITORY_ANALYSIS_BUSINESS_RESULT_INVALID");
    }
    return new RepositoryAnalysisRunResult(technical, business);
  }

  private RepositoryAnalysisRunResult executeFallback(AnalysisRunId runId) {
    TechnicalDiscoveryWorkflowResult technical = technical(runId);
    BusinessAnalysisWorkflowResult business =
        fallbackBusinessExecution.apply(
            technical.verifiedSourceInventory(), technical.applicationDiscovery());
    if (business == null) {
      throw new IllegalStateException("REPOSITORY_ANALYSIS_BUSINESS_RESULT_INVALID");
    }
    return new RepositoryAnalysisRunResult(technical, business);
  }

  private TechnicalDiscoveryWorkflowResult technical(AnalysisRunId runId) {
    TechnicalDiscoveryWorkflowResult technical = discoveryExecution.apply(runId);
    if (technical == null) {
      throw new IllegalStateException("REPOSITORY_ANALYSIS_TECHNICAL_RESULT_INVALID");
    }
    return technical;
  }
}
