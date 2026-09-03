package org.sourceanalysis.app.analysis.fact.publish;

import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Opaque reference to the complete four-file proven-code-facts semantic publication. */
public record ProvenCodeFactsReference(AnalysisStepPublicationReference publication) {

  public ProvenCodeFactsReference {
    if (publication == null
        || publication.address().analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS) {
      throw new IllegalArgumentException("proven code facts publication is required");
    }
  }
}
