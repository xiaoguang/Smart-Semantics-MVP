package org.sourceanalysis.app.analysis.inventory;

import java.util.List;

/** Derived counts for a source-preparation result; an unknown total remains explicitly unknown. */
public record SourcePreparationSummary(
    Integer totalRegularFiles,
    int discoveredRegularFiles,
    int verifiedTextFiles,
    int verifiedMediaFiles,
    int excludedKnownFiles,
    int unavailableKnownFiles,
    int uncheckedKnownFiles,
    int directoryEntries,
    int symlinkEntries,
    int submoduleEntries,
    boolean enumerationComplete,
    List<String> unknownSubtrees,
    List<SourcePreparationTarget> unmatchedExclusions) {

  public SourcePreparationSummary {
    if (totalRegularFiles != null && totalRegularFiles < 0) {
      throw new IllegalArgumentException("total regular files cannot be negative");
    }
    if (!enumerationComplete && totalRegularFiles != null) {
      throw new IllegalArgumentException("incomplete enumeration must retain an unknown total");
    }
    if (enumerationComplete && totalRegularFiles == null) {
      throw new IllegalArgumentException("complete enumeration requires a known total");
    }
    if (discoveredRegularFiles < 0
        || verifiedTextFiles < 0
        || verifiedMediaFiles < 0
        || excludedKnownFiles < 0
        || unavailableKnownFiles < 0
        || uncheckedKnownFiles < 0
        || directoryEntries < 0
        || symlinkEntries < 0
        || submoduleEntries < 0) {
      throw new IllegalArgumentException("source-preparation counts cannot be negative");
    }
    if (verifiedTextFiles
            + verifiedMediaFiles
            + excludedKnownFiles
            + unavailableKnownFiles
            + uncheckedKnownFiles
        != discoveredRegularFiles) {
      throw new IllegalArgumentException("regular-file partitions must equal discovered files");
    }
    if (enumerationComplete && !unknownSubtrees.isEmpty()) {
      throw new IllegalArgumentException("complete enumeration cannot retain unknown subtrees");
    }
    unknownSubtrees = List.copyOf(unknownSubtrees);
    unmatchedExclusions = List.copyOf(unmatchedExclusions);
  }
}
