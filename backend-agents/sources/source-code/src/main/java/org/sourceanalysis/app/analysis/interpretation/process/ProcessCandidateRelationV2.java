package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** A deterministic candidate relationship; it is not an asserted business-process order. */
public record ProcessCandidateRelationV2(
    String candidateRelationId,
    String leftFlowSliceId,
    String rightFlowSliceId,
    String strongestSignalLevel,
    String direction,
    String relationUse,
    List<ProcessRelationPositivePairBasisV1> positivePairBases,
    List<ProcessRelationCounterBasisV1> counterBases,
    List<String> supportingProcessJoinSignalIds,
    List<String> processSemanticCueIds,
    List<String> counterProcessJoinSignalIds,
    List<String> blockingCounterProcessJoinSignalIds,
    List<String> factIds,
    List<String> proofIds,
    List<String> evidenceNodeIds,
    List<SourceLocatorV1> sourceLocators,
    List<String> gapIds) {

  public ProcessCandidateRelationV2 {
    required(candidateRelationId);
    required(leftFlowSliceId);
    required(rightFlowSliceId);
    if (leftFlowSliceId.compareTo(rightFlowSliceId) >= 0) {
      throw new IllegalArgumentException("process relation Flow order is invalid");
    }
    required(strongestSignalLevel);
    required(direction);
    required(relationUse);
    positivePairBases = List.copyOf(Objects.requireNonNull(positivePairBases));
    counterBases = List.copyOf(Objects.requireNonNull(counterBases));
    supportingProcessJoinSignalIds =
        List.copyOf(Objects.requireNonNull(supportingProcessJoinSignalIds));
    processSemanticCueIds = List.copyOf(Objects.requireNonNull(processSemanticCueIds));
    counterProcessJoinSignalIds = List.copyOf(Objects.requireNonNull(counterProcessJoinSignalIds));
    blockingCounterProcessJoinSignalIds =
        List.copyOf(Objects.requireNonNull(blockingCounterProcessJoinSignalIds));
    factIds = List.copyOf(Objects.requireNonNull(factIds));
    proofIds = List.copyOf(Objects.requireNonNull(proofIds));
    evidenceNodeIds = List.copyOf(Objects.requireNonNull(evidenceNodeIds));
    sourceLocators = List.copyOf(Objects.requireNonNull(sourceLocators));
    gapIds = List.copyOf(Objects.requireNonNull(gapIds));
    if (positivePairBases.isEmpty())
      throw new IllegalArgumentException("process relation requires a basis");
  }

  private static void required(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("process relation is invalid");
  }
}
