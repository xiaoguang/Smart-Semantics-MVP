package org.sourceanalysis.app.analysis.discovery;

import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Typed handoff for the persisted Mapper candidate catalog module draft. */
public record MapperCatalogDraftReference(ModulePublicationReference publication) {

  public MapperCatalogDraftReference {
    if (publication == null
        || !(publication.address()
            instanceof org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || address.moduleNumber() != 3
        || !"mapper-catalog".equals(address.moduleKey())) {
      throw new IllegalArgumentException(
          "Mapper catalog draft reference has the wrong module address");
    }
  }
}
