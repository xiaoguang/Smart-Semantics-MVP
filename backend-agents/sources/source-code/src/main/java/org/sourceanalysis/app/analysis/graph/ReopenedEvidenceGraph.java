package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.artifact.ArtifactReference;

/** The only verified persisted M5 aggregate that program-graph publication may consume. */
public sealed interface ReopenedEvidenceGraph
    permits PersistedEvidenceGraphReader.VerifiedReopenedEvidenceGraph {

  EvidenceGraphDraftReference reference();

  ArtifactReference payloadRef();

  ArtifactReference codeStructurePayloadRef();

  ArtifactReference callGraphPayloadRef();

  ArtifactReference controlFlowPayloadRef();

  ArtifactReference dataFlowPayloadRef();

  EvidenceGraphDraft draft();

  ProgramGraphInputBasis basis();
}
