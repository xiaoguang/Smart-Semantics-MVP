package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;

/** The single closed input aggregate for one CallGraphBuilder invocation. */
public record CallGraphInputs(
    ReopenedCodeStructureGraph structure, ReopenedProgramGraphInputs reopened) {

  public CallGraphInputs {
    Objects.requireNonNull(structure, "reopened code structure graph");
    Objects.requireNonNull(reopened, "reopened program graph inputs");
    ProgramGraphInputBasis expected =
        ProgramGraphInputBasis.from(
            reopened.source(),
            reopened.discovery().codeStructureDiscovery(),
            structure.draft().graphProfileRef());
    if (!structure.basis().equals(expected)) {
      throw new IllegalArgumentException("call graph inputs do not share one reopened basis");
    }
  }
}
