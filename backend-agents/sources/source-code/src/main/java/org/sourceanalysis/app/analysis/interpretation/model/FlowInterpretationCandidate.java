package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** A Flow-local candidate exists only after both R1 and R2 accepted their closed key set. */
public record FlowInterpretationCandidate(
    String candidateId,
    String flowSliceId,
    String evidenceCapsuleId,
    String r1RoundId,
    String r2RoundId,
    List<InterpretationProposal> interpretationProposals) {

  public FlowInterpretationCandidate {
    required(candidateId, "candidate ID");
    required(flowSliceId, "Flow slice ID");
    required(evidenceCapsuleId, "evidence capsule ID");
    required(r1RoundId, "R1 round ID");
    required(r2RoundId, "R2 round ID");
    Objects.requireNonNull(interpretationProposals, "interpretation proposals");
    interpretationProposals =
        interpretationProposals.stream()
            .sorted(Comparator.comparing(InterpretationProposal::interpretationProposalId))
            .toList();
    if (interpretationProposals.isEmpty()
        || interpretationProposals.stream()
            .anyMatch(value -> !flowSliceId.equals(value.flowSliceId()))
        || interpretationProposals.stream()
                .map(InterpretationProposal::interpretationProposalId)
                .distinct()
                .count()
            != interpretationProposals.size()) {
      throw new IllegalArgumentException("candidate proposals are invalid");
    }
    if (!candidateId.equals(
        FlowInterpretationIdentity.candidateId(
            flowSliceId, evidenceCapsuleId, r1RoundId, r2RoundId, interpretationProposals))) {
      throw new IllegalArgumentException("candidate ID does not match candidate content");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
