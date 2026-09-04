package org.sourceanalysis.app.analysis.interpretation.proposal;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** One complete, isolated R0 provider request for an eligible business Flow. */
public record RegistryProposalTask(
    String taskSpecId,
    String taskKind,
    String flowSliceId,
    String evidenceCapsuleId,
    String isolatedSessionKey,
    ImmutableBytes inputJson,
    Sha256Digest inputJsonSha256,
    Sha256Digest outputSchemaSha256,
    Sha256Digest promptBundleSha256,
    ArtifactReference expectedRuntime) {

  public RegistryProposalTask {
    required(taskSpecId, "task specification ID");
    if (!"R0_REGISTRY_PROPOSAL".equals(taskKind)) {
      throw new IllegalArgumentException("registry proposal task kind is invalid");
    }
    required(flowSliceId, "Flow slice ID");
    required(evidenceCapsuleId, "evidence capsule ID");
    required(isolatedSessionKey, "isolated session key");
    Objects.requireNonNull(inputJson, "canonical input JSON");
    Objects.requireNonNull(inputJsonSha256, "input JSON SHA-256");
    Objects.requireNonNull(outputSchemaSha256, "output schema SHA-256");
    Objects.requireNonNull(promptBundleSha256, "prompt bundle SHA-256");
    Objects.requireNonNull(expectedRuntime, "expected runtime");
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
