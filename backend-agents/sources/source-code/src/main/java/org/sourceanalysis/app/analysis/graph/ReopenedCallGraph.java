package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.artifact.ArtifactReference;

/** The only verified persisted M2 aggregate that a later graph builder may consume. */
public sealed interface ReopenedCallGraph
    permits PersistedCallGraphReader.VerifiedReopenedCallGraph {

  CallGraphDraftReference reference();

  ArtifactReference payloadRef();

  CallGraphDraft draft();

  ProgramGraphInputBasis basis();
}
