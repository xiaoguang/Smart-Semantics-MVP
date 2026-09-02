package org.sourceanalysis.app.analysis.discovery;

import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Typed handoff for the persisted application-profile module draft. */
public record ApplicationProfileDraftReference(ModulePublicationReference publication) {

  public ApplicationProfileDraftReference {
    if (publication == null
        || !(publication.address()
            instanceof org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || address.moduleNumber() != 1
        || !"application-profile".equals(address.moduleKey())) {
      throw new IllegalArgumentException(
          "application profile draft reference has the wrong module address");
    }
  }
}
