package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.util.Objects;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Canonical identity of one accepted R0 response. */
public record RegistryProposalRound(
    String registryProposalRoundId, String taskSpecId, Sha256Digest canonicalResponseSha256) {

  public RegistryProposalRound {
    if (registryProposalRoundId == null || registryProposalRoundId.isBlank()) {
      throw new IllegalArgumentException("registry proposal round ID is required");
    }
    if (taskSpecId == null || taskSpecId.isBlank()) {
      throw new IllegalArgumentException("task specification ID is required");
    }
    Objects.requireNonNull(canonicalResponseSha256, "canonical response SHA-256");
  }
}
