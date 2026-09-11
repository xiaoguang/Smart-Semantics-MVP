package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** One complete R1 or R2 request whose selectable keys are frozen to one Flow. */
public record FlowModelTask(
    String taskSpecId,
    String taskKind,
    String round,
    String flowSliceId,
    String evidenceCapsuleId,
    String isolatedSessionKey,
    List<String> allowedKeys,
    ImmutableBytes inputJson,
    Sha256Digest inputJsonSha256,
    Sha256Digest outputSchemaSha256,
    Sha256Digest promptBundleSha256,
    String configuredAdapterId,
    String configuredAuthMode,
    ArtifactReference expectedRuntimeRef,
    ModelRuntimeIdentityV1 expectedRuntime) {

  /** Validates the closed task identity and its finite select-key allowlist. */
  public FlowModelTask {
    required(taskSpecId, "task specification ID");
    if (!("R1_INTERPRETATION".equals(taskKind) && "R1".equals(round))
        && !("R2_PRECISION_REVIEW".equals(taskKind) && "R2".equals(round))) {
      throw new IllegalArgumentException("flow model task kind is invalid");
    }
    required(flowSliceId, "Flow slice ID");
    required(evidenceCapsuleId, "evidence capsule ID");
    required(isolatedSessionKey, "isolated session key");
    Objects.requireNonNull(allowedKeys, "allowed keys");
    allowedKeys = allowedKeys.stream().sorted().toList();
    if (allowedKeys.isEmpty()
        || allowedKeys.stream().anyMatch(value -> value == null || value.isBlank())
        || allowedKeys.size() != allowedKeys.stream().distinct().count()) {
      throw new IllegalArgumentException("flow model task keys are invalid");
    }
    Objects.requireNonNull(inputJson, "canonical input JSON");
    Objects.requireNonNull(inputJsonSha256, "input JSON SHA-256");
    Objects.requireNonNull(outputSchemaSha256, "output schema SHA-256");
    Objects.requireNonNull(promptBundleSha256, "prompt bundle SHA-256");
    required(configuredAdapterId, "configured adapter ID");
    required(configuredAuthMode, "configured auth mode");
    Objects.requireNonNull(expectedRuntimeRef, "expected runtime reference");
    Objects.requireNonNull(expectedRuntime, "expected runtime");
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required");
  }

  static FlowModelTask create(
      String taskKind,
      String round,
      String flowSliceId,
      String evidenceCapsuleId,
      String isolatedSessionKey,
      List<String> allowedKeys,
      ImmutableBytes inputJson,
      Sha256Digest inputJsonSha256,
      Sha256Digest outputSchemaSha256,
      Sha256Digest promptBundleSha256,
      String configuredAdapterId,
      String configuredAuthMode,
      ArtifactReference expectedRuntimeRef,
      ModelRuntimeIdentityV1 expectedRuntime) {
    FlowModelTask provisional =
        new FlowModelTask(
            "flow-model-task:placeholder",
            taskKind,
            round,
            flowSliceId,
            evidenceCapsuleId,
            isolatedSessionKey,
            allowedKeys,
            inputJson,
            inputJsonSha256,
            outputSchemaSha256,
            promptBundleSha256,
            configuredAdapterId,
            configuredAuthMode,
            expectedRuntimeRef,
            expectedRuntime);
    return new FlowModelTask(
        FlowInterpretationIdentity.taskId(provisional),
        taskKind,
        round,
        flowSliceId,
        evidenceCapsuleId,
        isolatedSessionKey,
        allowedKeys,
        inputJson,
        inputJsonSha256,
        outputSchemaSha256,
        promptBundleSha256,
        configuredAdapterId,
        configuredAuthMode,
        expectedRuntimeRef,
        expectedRuntime);
  }
}
