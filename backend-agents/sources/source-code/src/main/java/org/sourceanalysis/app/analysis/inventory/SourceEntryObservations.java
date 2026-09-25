package org.sourceanalysis.app.analysis.inventory;

/** The available before-and-after observations for one source-entry read. */
public record SourceEntryObservations(SourceObservation before, SourceObservation after) {

  public SourceEntryObservations {
    if (before == null && after == null) {
      throw new IllegalArgumentException("entry observations need a before or after observation");
    }
  }
}
