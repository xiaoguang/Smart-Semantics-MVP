package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;

/**
 * Executes the M1 code-structure shard from persisted inputs through receipt-last module storage.
 */
final class CodeStructureGraphExecution {

  private final ProgramGraphInputReader inputs;
  private final CodeStructureGraphBuilder builder;
  private final CodeStructureGraphModulePublisher publisher;

  CodeStructureGraphExecution(
      ProgramGraphInputReader inputs,
      CodeStructureGraphBuilder builder,
      CodeStructureGraphModulePublisher publisher) {
    this.inputs = Objects.requireNonNull(inputs, "program graph inputs");
    this.builder = Objects.requireNonNull(builder, "code structure builder");
    this.publisher = Objects.requireNonNull(publisher, "code structure publisher");
  }

  /** Builds and immediately persists the one M1 draft authorized by the supplied predecessors. */
  CodeStructureGraphDraftReference execute(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery,
      AnalysisStepModuleAddress destination,
      CodeStructureGraphProfile profile) {
    ReopenedProgramGraphInputs reopened = inputs.reopen(verifiedSource, applicationDiscovery);
    CodeStructureGraphDraft draft =
        builder.buildStructure(
            reopened.source(), reopened.discovery().codeStructureDiscovery(), profile);
    return publisher.publish(
        destination, reopened.source(), reopened.discovery().codeStructureDiscovery(), draft);
  }
}
