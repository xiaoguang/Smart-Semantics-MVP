package org.sourceanalysis.app.analysis.graph;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.ArtifactId;

/** Complete candidate accounting for one independently built program graph. */
public record GraphCoverage(
    List<ArtifactId> candidateElementIds,
    List<ArtifactId> exactElementIds,
    List<GraphGapDisposition> gapDispositions,
    List<GraphExclusionDisposition> exclusionDispositions,
    List<ArtifactId> scopeGapIds,
    boolean closed) {

  public GraphCoverage {
    candidateElementIds = orderedDistinct(candidateElementIds, "candidate element IDs");
    exactElementIds = orderedDistinct(exactElementIds, "exact element IDs");
    gapDispositions = orderedGaps(gapDispositions);
    exclusionDispositions = orderedExclusions(exclusionDispositions);
    scopeGapIds = orderedDistinct(scopeGapIds, "scope gap IDs");

    Set<ArtifactId> denominator = new HashSet<>(candidateElementIds);
    Set<ArtifactId> disposition = new HashSet<>(exactElementIds);
    gapDispositions.forEach(gap -> disposition.add(gap.candidateElementId()));
    exclusionDispositions.forEach(exclusion -> disposition.add(exclusion.candidateElementId()));
    if (!denominator.equals(disposition)
        || exactElementIds.size() + gapDispositions.size() + exclusionDispositions.size()
            != denominator.size()) {
      throw new IllegalArgumentException(
          "graph coverage must close a disjoint candidate denominator");
    }
  }

  private static List<ArtifactId> orderedDistinct(List<ArtifactId> values, String label) {
    Objects.requireNonNull(values, label);
    List<ArtifactId> ordered =
        values.stream().sorted(Comparator.comparing(ArtifactId::value)).toList();
    if (ordered.size() != new HashSet<>(ordered).size()) {
      throw new IllegalArgumentException(label + " must be distinct");
    }
    return List.copyOf(ordered);
  }

  private static List<GraphGapDisposition> orderedGaps(List<GraphGapDisposition> values) {
    Objects.requireNonNull(values, "gap dispositions");
    List<GraphGapDisposition> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.candidateElementId().value()))
            .toList();
    if (ordered.size()
        != ordered.stream().map(GraphGapDisposition::candidateElementId).distinct().count()) {
      throw new IllegalArgumentException("gap dispositions must be distinct by candidate");
    }
    return List.copyOf(ordered);
  }

  private static List<GraphExclusionDisposition> orderedExclusions(
      List<GraphExclusionDisposition> values) {
    Objects.requireNonNull(values, "exclusion dispositions");
    List<GraphExclusionDisposition> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.candidateElementId().value()))
            .toList();
    if (ordered.size()
        != ordered.stream().map(GraphExclusionDisposition::candidateElementId).distinct().count()) {
      throw new IllegalArgumentException("exclusion dispositions must be distinct by candidate");
    }
    return List.copyOf(ordered);
  }

  @Override
  public List<ArtifactId> candidateElementIds() {
    return List.copyOf(candidateElementIds);
  }

  @Override
  public List<ArtifactId> exactElementIds() {
    return List.copyOf(exactElementIds);
  }

  @Override
  public List<GraphGapDisposition> gapDispositions() {
    return List.copyOf(gapDispositions);
  }

  @Override
  public List<GraphExclusionDisposition> exclusionDispositions() {
    return List.copyOf(exclusionDispositions);
  }

  @Override
  public List<ArtifactId> scopeGapIds() {
    return List.copyOf(scopeGapIds);
  }
}
