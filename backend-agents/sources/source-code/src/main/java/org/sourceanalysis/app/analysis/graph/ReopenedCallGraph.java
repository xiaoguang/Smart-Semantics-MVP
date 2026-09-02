package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.artifact.ArtifactReference;

/** The only verified persisted M2 aggregate that a later graph builder may consume. */
public sealed interface ReopenedCallGraph
    permits PersistedCallGraphReader.VerifiedReopenedCallGraph {

  CallGraphDraftReference reference();

  ArtifactReference payloadRef();

  /** The exact M1 payload identity present in this M2 module's verified upstream lineage. */
  ArtifactReference codeStructurePayloadRef();

  CallGraphDraft draft();

  ProgramGraphInputBasis basis();
}
