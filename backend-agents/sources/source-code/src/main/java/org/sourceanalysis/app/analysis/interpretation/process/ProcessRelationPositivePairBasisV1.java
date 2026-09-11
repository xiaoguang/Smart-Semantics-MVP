package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** One exact M6 reason why two local Flows are allowed to be considered together. */
public record ProcessRelationPositivePairBasisV1(
    String pairKind,
    String signalLevel,
    List<String> leftProcessJoinSignalIds,
    List<String> rightProcessJoinSignalIds,
    List<String> processSemanticCueIds,
    String anchorKind,
    String anchorKey,
    String direction) {

  public ProcessRelationPositivePairBasisV1 {
    required(pairKind);
    required(signalLevel);
    leftProcessJoinSignalIds = List.copyOf(Objects.requireNonNull(leftProcessJoinSignalIds));
    rightProcessJoinSignalIds = List.copyOf(Objects.requireNonNull(rightProcessJoinSignalIds));
    processSemanticCueIds = List.copyOf(Objects.requireNonNull(processSemanticCueIds));
    required(anchorKind);
    required(anchorKey);
    required(direction);
  }

  private static void required(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("process relation basis is invalid");
  }
}
