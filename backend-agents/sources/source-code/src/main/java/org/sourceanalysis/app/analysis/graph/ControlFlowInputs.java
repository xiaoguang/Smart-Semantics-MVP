package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;

/** The single sealed predecessor aggregate admitted to one control-flow builder invocation. */
public record ControlFlowInputs(
    ReopenedCodeStructureGraph structure,
    ReopenedCallGraph calls,
    ReopenedProgramGraphInputs reopened) {

  public ControlFlowInputs {
    Objects.requireNonNull(structure, "reopened code-structure graph");
    Objects.requireNonNull(calls, "reopened call graph");
    Objects.requireNonNull(reopened, "reopened program graph inputs");
    ProgramGraphInputBasis expected =
        ProgramGraphInputBasis.from(
            reopened.source(),
            reopened.discovery().codeStructureDiscovery(),
            structure.basis().graphProfileRef());
    if (!expected.equals(structure.basis())
        || !expected.equals(calls.basis())
        || !calls.codeStructurePayloadRef().equals(structure.payloadRef())) {
      throw new GraphReferenceException();
    }
  }
}
