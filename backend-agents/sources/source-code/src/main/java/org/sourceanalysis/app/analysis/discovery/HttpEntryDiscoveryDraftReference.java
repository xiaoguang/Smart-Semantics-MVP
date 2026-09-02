package org.sourceanalysis.app.analysis.discovery;

import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Typed handoff for the persisted HTTP-entry discovery module draft. */
public record HttpEntryDiscoveryDraftReference(ModulePublicationReference publication) {

  public HttpEntryDiscoveryDraftReference {
    if (publication == null
        || !(publication.address()
            instanceof org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || address.moduleNumber() != 2
        || !"http-entry".equals(address.moduleKey())) {
      throw new IllegalArgumentException(
          "HTTP-entry discovery draft reference has the wrong module address");
    }
  }
}
