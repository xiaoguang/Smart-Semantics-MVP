package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** Closed M6 denominator accounting; M6 always performs zero Provider calls. */
public record CrossFlowCandidateAccountingV1(
    List<String> flowSliceIds,
    List<String> candidateRelationIds,
    List<String> processEvidenceGroupIds,
    List<String> counterScopeRelationIds,
    int flowCount,
    int candidateRelationCount,
    int processEvidenceGroupCount,
    int providerCallCount,
    boolean closed) {

  public CrossFlowCandidateAccountingV1 {
    flowSliceIds = List.copyOf(Objects.requireNonNull(flowSliceIds));
    candidateRelationIds = List.copyOf(Objects.requireNonNull(candidateRelationIds));
    processEvidenceGroupIds = List.copyOf(Objects.requireNonNull(processEvidenceGroupIds));
    counterScopeRelationIds = List.copyOf(Objects.requireNonNull(counterScopeRelationIds));
    if (flowCount != flowSliceIds.size()
        || candidateRelationCount != candidateRelationIds.size()
        || processEvidenceGroupCount != processEvidenceGroupIds.size()
        || providerCallCount != 0
        || !closed) {
      throw new IllegalArgumentException("cross-Flow candidate accounting is not closed");
    }
  }
}
