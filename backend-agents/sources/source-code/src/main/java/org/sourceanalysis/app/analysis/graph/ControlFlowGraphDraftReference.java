package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Typed fresh-reopen reference to the persisted M3 control-flow graph draft. */
public record ControlFlowGraphDraftReference(ModulePublicationReference publication) {

  public ControlFlowGraphDraftReference {
    Objects.requireNonNull(publication, "control-flow publication");
  }
}
