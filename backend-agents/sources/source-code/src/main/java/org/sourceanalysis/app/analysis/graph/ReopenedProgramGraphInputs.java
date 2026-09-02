package org.sourceanalysis.app.analysis.graph;

import java.util.Objects;

/** Fresh, identity-checked source and discovery inputs for the program-graph builders. */
record ReopenedProgramGraphInputs(
    CodeStructureSource source, ProgramGraphDiscoveryInputs discovery) {

  ReopenedProgramGraphInputs {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(discovery, "discovery");
  }
}
