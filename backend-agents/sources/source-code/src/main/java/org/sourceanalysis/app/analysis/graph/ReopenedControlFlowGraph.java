package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.artifact.ArtifactReference;

/** The only verified persisted M3 aggregate that later modules may consume. */
public sealed interface ReopenedControlFlowGraph
    permits PersistedControlFlowGraphReader.VerifiedReopenedControlFlowGraph {

  ControlFlowGraphDraftReference reference();

  ArtifactReference payloadRef();

  ArtifactReference codeStructurePayloadRef();

  ArtifactReference callGraphPayloadRef();

  ControlFlowGraphDraft draft();

  ProgramGraphInputBasis basis();
}
