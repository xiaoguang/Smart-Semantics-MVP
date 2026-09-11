package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** Program-only material retained by M6 for M7's later path-free packet projection. */
public record ProcessPersistedFlowViewV1(
    String flowSliceId,
    String evidenceCapsuleId,
    String entryId,
    List<String> factIds,
    List<String> gapIds,
    List<String> outcomePathIds,
    List<String> processJoinSignalIds,
    List<String> modelEvidenceSpanIds,
    List<String> projectionObligationIds) {

  public ProcessPersistedFlowViewV1 {
    required(flowSliceId);
    required(evidenceCapsuleId);
    required(entryId);
    factIds = List.copyOf(Objects.requireNonNull(factIds));
    gapIds = List.copyOf(Objects.requireNonNull(gapIds));
    outcomePathIds = List.copyOf(Objects.requireNonNull(outcomePathIds));
    processJoinSignalIds = List.copyOf(Objects.requireNonNull(processJoinSignalIds));
    modelEvidenceSpanIds = List.copyOf(Objects.requireNonNull(modelEvidenceSpanIds));
    projectionObligationIds = List.copyOf(Objects.requireNonNull(projectionObligationIds));
  }

  private static void required(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("persisted Flow view is invalid");
  }
}
