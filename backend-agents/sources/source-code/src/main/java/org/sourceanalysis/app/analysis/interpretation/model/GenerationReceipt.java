package org.sourceanalysis.app.analysis.interpretation.model;

import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Complete GenerationReceiptV3 carrier for one started and completed local Provider call. */
public record GenerationReceipt(
    String schemaVersion,
    String artifactType,
    String generationReceiptId,
    String generationKind,
    String taskSpecId,
    String flowSliceId,
    String taskShardId,
    Sha256Digest requestSha256,
    Sha256Digest responseSha256,
    String configuredAdapterId,
    String configuredAuthMode,
    ModelRuntimeIdentityV1 expectedRuntime,
    ModelRuntimeIdentityV1 observedRuntime,
    boolean started,
    boolean completed) {

  static final String SCHEMA_VERSION = "flow-interpretation-generation-receipt-v3";
  static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_GENERATION_RECEIPT";

  public GenerationReceipt {
    if (!SCHEMA_VERSION.equals(schemaVersion) || !ARTIFACT_TYPE.equals(artifactType)) {
      throw new IllegalArgumentException("generation receipt discriminator is invalid");
    }
    required(generationReceiptId, "generation receipt ID");
    if (!"R1_FLOW_INTERPRETATION".equals(generationKind)
        && !"R2_FLOW_PRECISION_REVIEW".equals(generationKind)) {
      throw new IllegalArgumentException("generation receipt kind is invalid");
    }
    required(taskSpecId, "task specification ID");
    required(flowSliceId, "Flow slice ID");
    if (taskShardId != null) {
      throw new IllegalArgumentException("local generation receipt task shard must be null");
    }
    Objects.requireNonNull(requestSha256, "request SHA-256");
    Objects.requireNonNull(responseSha256, "response SHA-256");
    required(configuredAdapterId, "configured adapter ID");
    required(configuredAuthMode, "configured auth mode");
    Objects.requireNonNull(expectedRuntime, "expected runtime");
    Objects.requireNonNull(observedRuntime, "observed runtime");
    if (!started || !completed) {
      throw new IllegalArgumentException(
          "completed generation receipt must be started and completed");
    }
    if (!generationReceiptId.equals(
        FlowInterpretationIdentity.receiptId(
            generationKind,
            taskSpecId,
            flowSliceId,
            requestSha256,
            responseSha256,
            configuredAdapterId,
            configuredAuthMode,
            expectedRuntime,
            observedRuntime))) {
      throw new IllegalArgumentException("generation receipt ID does not match receipt content");
    }
  }

  static GenerationReceipt completed(FlowModelTask task, Sha256Digest responseSha256) {
    String kind = "R1".equals(task.round()) ? "R1_FLOW_INTERPRETATION" : "R2_FLOW_PRECISION_REVIEW";
    return new GenerationReceipt(
        SCHEMA_VERSION,
        ARTIFACT_TYPE,
        FlowInterpretationIdentity.receiptId(
            kind,
            task.taskSpecId(),
            task.flowSliceId(),
            task.inputJsonSha256(),
            responseSha256,
            task.configuredAdapterId(),
            task.configuredAuthMode(),
            task.expectedRuntime(),
            task.expectedRuntime()),
        kind,
        task.taskSpecId(),
        task.flowSliceId(),
        null,
        task.inputJsonSha256(),
        responseSha256,
        task.configuredAdapterId(),
        task.configuredAuthMode(),
        task.expectedRuntime(),
        task.expectedRuntime(),
        true,
        true);
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
