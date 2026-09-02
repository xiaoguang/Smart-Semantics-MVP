package org.sourceanalysis.app.analysis.inventory;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Path-free reference to a complete, reopened verified-source-inventory analysis step. */
public record VerifiedSourceInventoryReference(AnalysisStepPublicationReference publication) {

  public VerifiedSourceInventoryReference {
    Objects.requireNonNull(publication, "publication");
  }
}
