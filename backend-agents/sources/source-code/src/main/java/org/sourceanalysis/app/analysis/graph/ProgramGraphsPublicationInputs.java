package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** The five independently reopened graph modules required for one M6 publication attempt. */
public record ProgramGraphsPublicationInputs(
    AnalysisStepPublicationReference verifiedSourceInventoryPublication,
    AnalysisStepPublicationReference applicationDiscoveryPublication,
    ReopenedCodeStructureGraph codeStructure,
    ReopenedCallGraph callGraph,
    ReopenedControlFlowGraph controlFlow,
    ReopenedDataFlowGraph dataFlow,
    ReopenedEvidenceGraph evidence) {

  public ProgramGraphsPublicationInputs {
    requireStep(
        verifiedSourceInventoryPublication,
        AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
        "source inventory");
    requireStep(
        applicationDiscoveryPublication,
        AnalysisStepKey.APPLICATION_DISCOVERY,
        "application discovery");
    Objects.requireNonNull(codeStructure, "reopened code structure graph");
    Objects.requireNonNull(callGraph, "reopened call graph");
    Objects.requireNonNull(controlFlow, "reopened control-flow graph");
    Objects.requireNonNull(dataFlow, "reopened data-flow graph");
    Objects.requireNonNull(evidence, "reopened evidence graph");
  }

  private static void requireStep(
      AnalysisStepPublicationReference reference, AnalysisStepKey expected, String label) {
    if (reference == null || reference.address().analysisStepKey() != expected) {
      throw new IllegalArgumentException(label + " publication reference is invalid");
    }
  }
}
