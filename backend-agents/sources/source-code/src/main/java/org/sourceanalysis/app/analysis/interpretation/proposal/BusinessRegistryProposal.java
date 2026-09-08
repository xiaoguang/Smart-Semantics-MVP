package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.util.List;
import java.util.Objects;

/** One validated, same-Flow R0 business-label proposal; it is not a source fact. */
public record BusinessRegistryProposal(
    String registryProposalId,
    String taskSpecId,
    String flowSliceId,
    String evidenceCapsuleId,
    String proposalKind,
    String normalizedLabel,
    String normalizedPurpose,
    List<String> basisAtomIds,
    List<String> basisGapIds,
    String sourceSeedKey) {

  public BusinessRegistryProposal {
    required(registryProposalId, "registry proposal ID");
    required(taskSpecId, "task specification ID");
    required(flowSliceId, "Flow slice ID");
    required(evidenceCapsuleId, "evidence capsule ID");
    if (!List.of("BUSINESS_TERM", "CLAIM", "QUESTION").contains(proposalKind)) {
      throw new IllegalArgumentException("registry proposal kind is invalid");
    }
    required(normalizedLabel, "normalized label");
    required(normalizedPurpose, "normalized purpose");
    basisAtomIds = List.copyOf(Objects.requireNonNull(basisAtomIds, "basis atom IDs"));
    basisGapIds = List.copyOf(Objects.requireNonNull(basisGapIds, "basis Gap IDs"));
    if (basisAtomIds.isEmpty() && basisGapIds.isEmpty()) {
      throw new IllegalArgumentException("registry proposal basis is required");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required");
  }
}
