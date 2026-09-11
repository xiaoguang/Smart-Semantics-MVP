package org.sourceanalysis.app.analysis.interpretation.proposal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Complete GenerationReceiptV3 carrier for one started and completed R0 Provider call. */
public record RegistryProposalGenerationReceipt(
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
  static final String GENERATION_KIND = "R0_REGISTRY_PROPOSAL";

  public RegistryProposalGenerationReceipt {
    if (!SCHEMA_VERSION.equals(schemaVersion)
        || !ARTIFACT_TYPE.equals(artifactType)
        || !GENERATION_KIND.equals(generationKind)) {
      throw new IllegalArgumentException(
          "registry proposal generation receipt discriminator is invalid");
    }
    required(generationReceiptId, "generation receipt ID");
    required(taskSpecId, "task specification ID");
    required(flowSliceId, "Flow slice ID");
    if (taskShardId != null) {
      throw new IllegalArgumentException("R0 generation receipt task shard must be null");
    }
    Objects.requireNonNull(requestSha256, "request SHA-256");
    Objects.requireNonNull(responseSha256, "response SHA-256");
    required(configuredAdapterId, "configured adapter ID");
    required(configuredAuthMode, "configured auth mode");
    Objects.requireNonNull(expectedRuntime, "expected runtime");
    Objects.requireNonNull(observedRuntime, "observed runtime");
    if (!started || !completed) {
      throw new IllegalArgumentException("R0 generation receipt must be started and completed");
    }
    if (!generationReceiptId.equals(
        computeId(
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

  static RegistryProposalGenerationReceipt completedR0(
      String taskSpecId,
      String flowSliceId,
      Sha256Digest requestSha256,
      Sha256Digest responseSha256,
      String configuredAdapterId,
      String configuredAuthMode,
      ModelRuntimeIdentityV1 expectedRuntime,
      ModelRuntimeIdentityV1 observedRuntime) {
    String receiptId =
        computeId(
            taskSpecId,
            flowSliceId,
            requestSha256,
            responseSha256,
            configuredAdapterId,
            configuredAuthMode,
            expectedRuntime,
            observedRuntime);
    return new RegistryProposalGenerationReceipt(
        SCHEMA_VERSION,
        ARTIFACT_TYPE,
        receiptId,
        GENERATION_KIND,
        taskSpecId,
        flowSliceId,
        null,
        requestSha256,
        responseSha256,
        configuredAdapterId,
        configuredAuthMode,
        expectedRuntime,
        observedRuntime,
        true,
        true);
  }

  static String recomputeId(RegistryProposalGenerationReceipt receipt) {
    return computeId(
        receipt.taskSpecId(),
        receipt.flowSliceId(),
        receipt.requestSha256(),
        receipt.responseSha256(),
        receipt.configuredAdapterId(),
        receipt.configuredAuthMode(),
        receipt.expectedRuntime(),
        receipt.observedRuntime());
  }

  private static String computeId(
      String taskSpecId,
      String flowSliceId,
      Sha256Digest requestSha256,
      Sha256Digest responseSha256,
      String configuredAdapterId,
      String configuredAuthMode,
      ModelRuntimeIdentityV1 expectedRuntime,
      ModelRuntimeIdentityV1 observedRuntime) {
    ObjectNode fields =
        fields(
            taskSpecId,
            flowSliceId,
            requestSha256,
            responseSha256,
            configuredAdapterId,
            configuredAuthMode,
            expectedRuntime,
            observedRuntime);
    return "generation-receipt:"
        + sha256(
            frame("flow-interpretation-generation-receipt-id-v3"),
            frame(new CanonicalJsonCodec().encodeCanonical(fields).copyToByteArray()));
  }

  static ObjectNode json(RegistryProposalGenerationReceipt receipt) {
    ObjectNode fields =
        fields(
            receipt.taskSpecId(),
            receipt.flowSliceId(),
            receipt.requestSha256(),
            receipt.responseSha256(),
            receipt.configuredAdapterId(),
            receipt.configuredAuthMode(),
            receipt.expectedRuntime(),
            receipt.observedRuntime());
    fields.put("generationReceiptId", receipt.generationReceiptId());
    return fields;
  }

  private static ObjectNode fields(
      String taskSpecId,
      String flowSliceId,
      Sha256Digest requestSha256,
      Sha256Digest responseSha256,
      String configuredAdapterId,
      String configuredAuthMode,
      ModelRuntimeIdentityV1 expectedRuntime,
      ModelRuntimeIdentityV1 observedRuntime) {
    ObjectNode fields = JsonNodeFactory.instance.objectNode();
    fields.put("schemaVersion", SCHEMA_VERSION);
    fields.put("artifactType", ARTIFACT_TYPE);
    fields.put("generationKind", GENERATION_KIND);
    fields.put("taskSpecId", taskSpecId);
    fields.put("flowSliceId", flowSliceId);
    fields.putNull("taskShardId");
    fields.put("requestSha256", requestSha256.value());
    fields.put("responseSha256", responseSha256.value());
    fields.put("configuredAdapterId", configuredAdapterId);
    fields.put("configuredAuthMode", configuredAuthMode);
    runtime(fields.putObject("expectedRuntime"), expectedRuntime);
    runtime(fields.putObject("observedRuntime"), observedRuntime);
    fields.put("started", true);
    fields.put("completed", true);
    return fields;
  }

  private static void runtime(ObjectNode node, ModelRuntimeIdentityV1 runtime) {
    node.put("upstreamProvider", runtime.upstreamProvider());
    node.put("model", runtime.model());
    node.put("reasoningEffort", runtime.reasoningEffort());
    node.put("sandbox", runtime.sandbox());
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }
}
