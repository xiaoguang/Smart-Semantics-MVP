package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Audit receipt for one started and accepted scripted R0 provider call. */
public record RegistryProposalGenerationReceipt(
    String generationReceiptId,
    String taskSpecId,
    ArtifactReference expectedRuntime,
    ArtifactReference observedRuntime,
    Sha256Digest canonicalRequestSha256,
    Sha256Digest canonicalResponseSha256) {

  public RegistryProposalGenerationReceipt {
    if (generationReceiptId == null || generationReceiptId.isBlank()) {
      throw new IllegalArgumentException("generation receipt ID is required");
    }
    if (taskSpecId == null || taskSpecId.isBlank()) {
      throw new IllegalArgumentException("task specification ID is required");
    }
    Objects.requireNonNull(expectedRuntime, "expected runtime");
    Objects.requireNonNull(observedRuntime, "observed runtime");
    Objects.requireNonNull(canonicalRequestSha256, "canonical request SHA-256");
    Objects.requireNonNull(canonicalResponseSha256, "canonical response SHA-256");
  }
}
