package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;

/** The single closed input aggregate for one CallGraphBuilder invocation. */
public record CallGraphInputs(
    CodeStructureGraphDraft structure, ReopenedProgramGraphInputs reopened) {

  public CallGraphInputs {
    Objects.requireNonNull(structure, "code structure draft");
    Objects.requireNonNull(reopened, "reopened program graph inputs");
    if (!structure.snapshotId().equals(reopened.source().snapshotId())
        || !structure
            .applicationProfileId()
            .equals(reopened.discovery().codeStructureDiscovery().applicationProfileId())
        || !structure.entryIds().equals(reopened.discovery().codeStructureDiscovery().entryIds())) {
      throw new IllegalArgumentException("call graph inputs do not share one reopened basis");
    }
  }
}
