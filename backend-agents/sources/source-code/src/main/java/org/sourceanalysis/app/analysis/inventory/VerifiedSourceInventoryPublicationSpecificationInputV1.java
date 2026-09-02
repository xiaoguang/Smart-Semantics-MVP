package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** The closed, path-free preimage set M3 needs to project verified source inventory artifacts. */
public record VerifiedSourceInventoryPublicationSpecificationInputV1(
    AnalysisStepPublicationAddress destination,
    ModulePublicationReference admittedSourceRequestPublication,
    ModulePublicationReference verifiedSourceIndexPublication,
    ArtifactReference analysisRunRequestRef,
    ArtifactReference frozenRepositoryRequestRef) {

  public VerifiedSourceInventoryPublicationSpecificationInputV1 {
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(admittedSourceRequestPublication, "admitted source request publication");
    Objects.requireNonNull(verifiedSourceIndexPublication, "verified source index publication");
    Objects.requireNonNull(analysisRunRequestRef, "analysis run request reference");
    Objects.requireNonNull(frozenRepositoryRequestRef, "frozen repository request reference");
  }
}
