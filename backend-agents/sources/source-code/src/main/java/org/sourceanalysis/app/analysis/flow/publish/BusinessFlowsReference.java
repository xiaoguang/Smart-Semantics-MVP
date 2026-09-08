package org.sourceanalysis.app.analysis.flow.publish;

import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Opaque reference to the complete five-file business-flow publication. */
public record BusinessFlowsReference(AnalysisStepPublicationReference publication) {

  public BusinessFlowsReference {
    if (publication == null
        || publication.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS) {
      throw new IllegalArgumentException("business flows publication is required");
    }
  }
}
