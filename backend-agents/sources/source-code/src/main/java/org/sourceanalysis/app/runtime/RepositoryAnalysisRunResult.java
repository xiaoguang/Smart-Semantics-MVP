package org.sourceanalysis.app.runtime;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;

/** Complete internal handoff from the technical source prefix to the business-language report. */
public record RepositoryAnalysisRunResult(
    TechnicalDiscoveryWorkflowResult technical, BusinessAnalysisWorkflowResult business) {

  public RepositoryAnalysisRunResult {
    Objects.requireNonNull(technical, "technical analysis result");
    Objects.requireNonNull(business, "business analysis result");
    if (!(business.materials().checkpoint().address()
            instanceof AnalysisStepModuleAddress materialAddress)
        || !technical
            .verifiedSourceInventory()
            .publication()
            .address()
            .runId()
            .equals(materialAddress.runId())) {
      throw new IllegalArgumentException("REPOSITORY_ANALYSIS_RUN_LINEAGE_INVALID");
    }
  }
}
