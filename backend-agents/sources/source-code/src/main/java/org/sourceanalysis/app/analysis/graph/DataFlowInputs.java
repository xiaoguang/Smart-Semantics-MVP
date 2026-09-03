package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;

/** The sealed persisted M1/M2/M3 aggregate admitted to one data-flow build. */
public record DataFlowInputs(
    ReopenedCodeStructureGraph structure,
    ReopenedCallGraph calls,
    ReopenedControlFlowGraph controlFlow,
    ReopenedProgramGraphInputs reopened) {

  public DataFlowInputs {
    Objects.requireNonNull(structure, "reopened code-structure graph");
    Objects.requireNonNull(calls, "reopened call graph");
    Objects.requireNonNull(controlFlow, "reopened control-flow graph");
    Objects.requireNonNull(reopened, "reopened program graph inputs");
    ProgramGraphInputBasis expected =
        ProgramGraphInputBasis.from(
            reopened.source(),
            reopened.discovery().codeStructureDiscovery(),
            structure.basis().graphProfileRef());
    if (!expected.equals(structure.basis())
        || !expected.equals(calls.basis())
        || !expected.equals(controlFlow.basis())
        || !calls.codeStructurePayloadRef().equals(structure.payloadRef())
        || !controlFlow.codeStructurePayloadRef().equals(structure.payloadRef())
        || !controlFlow.callGraphPayloadRef().equals(calls.payloadRef())) {
      throw new GraphReferenceException();
    }
  }
}
