package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;

/** Executes M2 from fresh persisted predecessors through one receipt-last call-graph module. */
final class CallGraphExecution {

  private final ProgramGraphInputReader inputs;
  private final PersistedCodeStructureGraphReader structures;
  private final CallGraphBuilder builder;
  private final CallGraphModulePublisher publisher;

  CallGraphExecution(
      ProgramGraphInputReader inputs,
      PersistedCodeStructureGraphReader structures,
      CallGraphBuilder builder,
      CallGraphModulePublisher publisher) {
    this.inputs = Objects.requireNonNull(inputs, "program graph inputs");
    this.structures = Objects.requireNonNull(structures, "persisted code-structure reader");
    this.builder = Objects.requireNonNull(builder, "call graph builder");
    this.publisher = Objects.requireNonNull(publisher, "call graph publisher");
  }

  /**
   * Reopens one M1 structure, builds calls from its same frozen inputs, then persists exactly M2.
   */
  CallGraphDraftReference execute(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      CodeStructureGraphDraftReference structureReference,
      AnalysisStepModuleAddress destination,
      CallGraphProfile profile) {
    Objects.requireNonNull(profile, "call graph profile");
    ReopenedProgramGraphInputs reopened = inputs.reopen(verifiedSource, applicationDiscovery);
    ReopenedCodeStructureGraph structure =
        structures.reopen(structureReference, reopened, profile.graphProfileRef());
    CallGraphDraft draft = builder.buildCalls(new CallGraphInputs(structure, reopened), profile);
    return publisher.publish(destination, structure, reopened, draft);
  }
}
