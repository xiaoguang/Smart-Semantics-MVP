package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Typed reference to a freshly reopened, completed application-discovery publication. */
public record ApplicationDiscoveryReference(AnalysisStepPublicationReference publication) {

  public ApplicationDiscoveryReference {
    publication = Objects.requireNonNull(publication, "application discovery publication");
  }
}
