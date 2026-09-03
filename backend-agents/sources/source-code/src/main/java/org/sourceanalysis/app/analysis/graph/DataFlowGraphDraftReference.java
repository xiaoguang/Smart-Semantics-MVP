package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Typed fresh-reopen reference to the persisted data-flow graph draft. */
public record DataFlowGraphDraftReference(ModulePublicationReference publication) {

  public DataFlowGraphDraftReference {
    Objects.requireNonNull(publication, "data-flow publication");
  }
}
