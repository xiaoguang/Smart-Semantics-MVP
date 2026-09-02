package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Typed fresh-reopen reference to the persisted M1 code-structure draft. */
public record CodeStructureGraphDraftReference(ModulePublicationReference publication) {

  public CodeStructureGraphDraftReference {
    Objects.requireNonNull(publication, "code-structure publication");
  }
}
