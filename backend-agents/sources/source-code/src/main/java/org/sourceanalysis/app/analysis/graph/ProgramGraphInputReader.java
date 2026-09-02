package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;

/** Reopens the two persisted predecessor publications needed to build Program Graphs. */
@FunctionalInterface
interface ProgramGraphInputReader {

  /** Returns only source and discovery values that passed predecessor identity checks. */
  ReopenedProgramGraphInputs reopen(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery);
}
