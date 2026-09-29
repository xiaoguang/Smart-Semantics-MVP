package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;

/** R1 technical Step02 output plus the exact in-run entry denominator supplied to Step03. */
public record TechnicalApplicationDiscovery(
    ApplicationDiscoveryReference publication, HttpEntryDiscovery entries) {

  public TechnicalApplicationDiscovery {
    publication =
        Objects.requireNonNull(publication, "technical application discovery publication");
    entries = Objects.requireNonNull(entries, "technical HTTP entry discovery");
  }
}
