package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;

/** Closed accounting of all admitted M1--M4 program elements that received evidence. */
public record EvidenceGraphCoverage(
    List<ArtifactId> candidateProgramElementIds,
    List<ArtifactId> evidencedProgramElementIds,
    boolean closed) {

  public EvidenceGraphCoverage {
    candidateProgramElementIds =
        orderedDistinct(candidateProgramElementIds, "candidate program element IDs");
    evidencedProgramElementIds =
        orderedDistinct(evidencedProgramElementIds, "evidenced program element IDs");
    if (!new HashSet<>(candidateProgramElementIds)
        .equals(new HashSet<>(evidencedProgramElementIds))) {
      throw new IllegalArgumentException(
          "evidence coverage must close all admitted program elements");
    }
  }

  private static List<ArtifactId> orderedDistinct(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    Set<ArtifactId> distinct = new HashSet<>(ordered);
    if (ordered.size() != distinct.size()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }
}
