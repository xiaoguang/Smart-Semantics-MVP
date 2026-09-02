package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.artifact.ArtifactReference;

/** A code-structure graph that only the canonical-store reader may make available to M2. */
public sealed interface ReopenedCodeStructureGraph
    permits PersistedCodeStructureGraphReader.VerifiedReopenedCodeStructureGraph {

  CodeStructureGraphDraftReference reference();

  ArtifactReference payloadRef();

  CodeStructureGraphDraft draft();

  ProgramGraphInputBasis basis();
}
