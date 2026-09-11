package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;

/** Persisted technical prefix plus its zero-Provider business-material preflight result. */
public record RepositoryMaterialPlanningResult(
    TechnicalDiscoveryWorkflowResult technical, BusinessMaterialBuildResult materials) {

  public RepositoryMaterialPlanningResult {
    Objects.requireNonNull(technical, "technical discovery result");
    Objects.requireNonNull(materials, "business material result");
    if (!(materials.checkpoint().address() instanceof AnalysisStepModuleAddress materialAddress)
        || !technical
            .verifiedSourceInventory()
            .publication()
            .address()
            .runId()
            .equals(materialAddress.runId())) {
      throw new IllegalArgumentException("REPOSITORY_MATERIAL_PLANNING_LINEAGE_INVALID");
    }
  }
}
