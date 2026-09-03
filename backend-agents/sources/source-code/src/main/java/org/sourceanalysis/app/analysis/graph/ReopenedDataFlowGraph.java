package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.artifact.ArtifactReference;

/** The only verified persisted M4 aggregate that a later graph builder may consume. */
public sealed interface ReopenedDataFlowGraph
    permits PersistedDataFlowGraphReader.VerifiedReopenedDataFlowGraph {

  DataFlowGraphDraftReference reference();

  ArtifactReference payloadRef();

  ArtifactReference codeStructurePayloadRef();

  ArtifactReference callGraphPayloadRef();

  ArtifactReference controlFlowPayloadRef();

  DataFlowGraphDraft draft();

  ProgramGraphInputBasis basis();
}
