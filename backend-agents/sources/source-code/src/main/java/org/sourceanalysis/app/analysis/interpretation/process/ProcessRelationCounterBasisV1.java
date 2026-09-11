package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** A counter signal scoped to one exact positive M6 relation basis. */
public record ProcessRelationCounterBasisV1(
    String counterKind,
    ProcessRelationPositivePairBasisV1 scopedPositivePair,
    List<String> leftCounterProcessJoinSignalIds,
    List<String> rightCounterProcessJoinSignalIds) {

  public ProcessRelationCounterBasisV1 {
    if (counterKind == null || counterKind.isBlank()) {
      throw new IllegalArgumentException("process counter kind is required");
    }
    Objects.requireNonNull(scopedPositivePair, "scoped positive pair");
    leftCounterProcessJoinSignalIds =
        List.copyOf(Objects.requireNonNull(leftCounterProcessJoinSignalIds));
    rightCounterProcessJoinSignalIds =
        List.copyOf(Objects.requireNonNull(rightCounterProcessJoinSignalIds));
  }
}
