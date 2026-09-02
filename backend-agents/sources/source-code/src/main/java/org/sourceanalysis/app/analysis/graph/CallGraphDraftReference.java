package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Typed fresh-reopen reference to the persisted M2 call-graph draft. */
public record CallGraphDraftReference(ModulePublicationReference publication) {

  public CallGraphDraftReference {
    Objects.requireNonNull(publication, "call-graph publication");
  }
}
