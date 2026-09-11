package org.sourceanalysis.app.analysis.interpretation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Exact framed identities and canonical wire projections owned by the local Flow interpretation
 * lane.
 */
final class FlowInterpretationIdentity {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  private FlowInterpretationIdentity() {}

  static String taskId(FlowModelTask task) {
    return identity(
        "flow-model-task:", "flow-interpretation-flow-model-task-id-v1", taskProjection(task));
  }

  static String proposalId(InterpretationProposal proposal) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    projection.put("registryProposalId", proposal.registryProposalId());
    projection.put("flowSliceId", proposal.flowSliceId());
    projection.put("provisionalKey", proposal.provisionalKey());
    projection.put("selectedKey", proposal.selectedKey());
    strings(projection.putArray("basisAtomIds"), proposal.basisAtomIds());
    strings(projection.putArray("basisGapIds"), proposal.basisGapIds());
    projection.put("r2Decision", proposal.r2Decision());
    return identity(
        "interpretation-proposal:",
        "flow-interpretation-interpretation-proposal-id-v1",
        projection);
  }

  static String candidateId(FlowInterpretationCandidate candidate) {
    return candidateId(
        candidate.flowSliceId(),
        candidate.evidenceCapsuleId(),
        candidate.r1RoundId(),
        candidate.r2RoundId(),
        candidate.interpretationProposals());
  }

  static String candidateId(
      String flowSliceId,
      String evidenceCapsuleId,
      String r1RoundId,
      String r2RoundId,
      List<InterpretationProposal> interpretationProposals) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    projection.put("flowSliceId", flowSliceId);
    projection.put("evidenceCapsuleId", evidenceCapsuleId);
    projection.put("r1RoundId", r1RoundId);
    projection.put("r2RoundId", r2RoundId);
    ArrayNode proposals = projection.putArray("interpretationProposals");
    interpretationProposals.stream()
        .sorted(java.util.Comparator.comparing(InterpretationProposal::interpretationProposalId))
        .forEach(value -> proposals.add(proposalJson(value)));
    return identity(
        "flow-interpretation-candidate:",
        "flow-interpretation-flow-interpretation-candidate-id-v1",
        projection);
  }

  static String receiptId(GenerationReceipt receipt) {
    return receiptId(
        receipt.generationKind(),
        receipt.taskSpecId(),
        receipt.flowSliceId(),
        receipt.requestSha256(),
        receipt.responseSha256(),
        receipt.configuredAdapterId(),
        receipt.configuredAuthMode(),
        receipt.expectedRuntime(),
        receipt.observedRuntime());
  }

  static String receiptId(
      String generationKind,
      String taskSpecId,
      String flowSliceId,
      Sha256Digest requestSha256,
      Sha256Digest responseSha256,
      String configuredAdapterId,
      String configuredAuthMode,
      ModelRuntimeIdentityV1 expectedRuntime,
      ModelRuntimeIdentityV1 observedRuntime) {
    return identity(
        "generation-receipt:",
        "flow-interpretation-generation-receipt-id-v3",
        receiptProjection(
            generationKind,
            taskSpecId,
            flowSliceId,
            requestSha256,
            responseSha256,
            configuredAdapterId,
            configuredAuthMode,
            expectedRuntime,
            observedRuntime,
            null));
  }

  static String executionSetId(
      String flowTaskSetId,
      List<ModelRound> rounds,
      List<GenerationReceipt> receipts,
      List<FlowInterpretationCandidate> candidates,
      List<ModelTaskDisposition> taskDispositions,
      List<FlowInterpretationDisposition> dispositions) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    projection.put("flowTaskSetId", flowTaskSetId);
    strings(
        projection.putArray("modelRoundIds"),
        rounds.stream().map(ModelRound::modelRoundId).sorted().toList());
    strings(
        projection.putArray("generationReceiptIds"),
        receipts.stream().map(GenerationReceipt::generationReceiptId).sorted().toList());
    strings(
        projection.putArray("candidateIds"),
        candidates.stream().map(FlowInterpretationCandidate::candidateId).sorted().toList());
    ArrayNode taskValues = projection.putArray("modelTaskDispositions");
    taskDispositions.stream()
        .sorted(java.util.Comparator.comparing(ModelTaskDisposition::taskSpecId))
        .forEach(value -> taskValues.add(taskDispositionJson(value)));
    strings(
        projection.putArray("flowInterpretationDispositionIds"),
        dispositions.stream()
            .map(FlowInterpretationDisposition::flowInterpretationDispositionId)
            .sorted()
            .toList());
    return identity(
        "interpretation-execution-set:", "flow-interpretation-execution-set-id-v1", projection);
  }

  static ObjectNode taskProjection(FlowModelTask task) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    projection.put("taskKind", task.taskKind());
    projection.put("round", task.round());
    projection.put("flowSliceId", task.flowSliceId());
    projection.put("evidenceCapsuleId", task.evidenceCapsuleId());
    projection.put("isolatedSessionKey", task.isolatedSessionKey());
    strings(projection.putArray("allowedKeys"), task.allowedKeys());
    projection.set("inputJson", JSON.parseCanonical(task.inputJson()));
    projection.put("inputJsonSha256", task.inputJsonSha256().value());
    projection.put("outputSchemaSha256", task.outputSchemaSha256().value());
    projection.put("promptBundleSha256", task.promptBundleSha256().value());
    projection.put("configuredAdapterId", task.configuredAdapterId());
    projection.put("configuredAuthMode", task.configuredAuthMode());
    projection.set("expectedRuntimeRef", referenceJson(task.expectedRuntimeRef()));
    projection.set("expectedRuntime", runtimeJson(task.expectedRuntime()));
    return projection;
  }

  static ObjectNode receiptProjection(GenerationReceipt receipt, boolean includeId) {
    return receiptProjection(
        receipt.generationKind(),
        receipt.taskSpecId(),
        receipt.flowSliceId(),
        receipt.requestSha256(),
        receipt.responseSha256(),
        receipt.configuredAdapterId(),
        receipt.configuredAuthMode(),
        receipt.expectedRuntime(),
        receipt.observedRuntime(),
        includeId ? receipt.generationReceiptId() : null);
  }

  private static ObjectNode receiptProjection(
      String generationKind,
      String taskSpecId,
      String flowSliceId,
      Sha256Digest requestSha256,
      Sha256Digest responseSha256,
      String configuredAdapterId,
      String configuredAuthMode,
      ModelRuntimeIdentityV1 expectedRuntime,
      ModelRuntimeIdentityV1 observedRuntime,
      String generationReceiptId) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    projection.put("schemaVersion", GenerationReceipt.SCHEMA_VERSION);
    projection.put("artifactType", GenerationReceipt.ARTIFACT_TYPE);
    if (generationReceiptId != null) projection.put("generationReceiptId", generationReceiptId);
    projection.put("generationKind", generationKind);
    projection.put("taskSpecId", taskSpecId);
    projection.put("flowSliceId", flowSliceId);
    projection.putNull("taskShardId");
    projection.put("requestSha256", requestSha256.value());
    projection.put("responseSha256", responseSha256.value());
    projection.put("configuredAdapterId", configuredAdapterId);
    projection.put("configuredAuthMode", configuredAuthMode);
    projection.set("expectedRuntime", runtimeJson(expectedRuntime));
    projection.set("observedRuntime", runtimeJson(observedRuntime));
    projection.put("started", true);
    projection.put("completed", true);
    return projection;
  }

  static ObjectNode proposalJson(InterpretationProposal proposal) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("interpretationProposalId", proposal.interpretationProposalId());
    node.put("registryProposalId", proposal.registryProposalId());
    node.put("flowSliceId", proposal.flowSliceId());
    node.put("provisionalKey", proposal.provisionalKey());
    node.put("selectedKey", proposal.selectedKey());
    strings(node.putArray("basisAtomIds"), proposal.basisAtomIds());
    strings(node.putArray("basisGapIds"), proposal.basisGapIds());
    node.put("r2Decision", proposal.r2Decision());
    return node;
  }

  static ObjectNode taskDispositionJson(ModelTaskDisposition disposition) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("taskSpecId", disposition.taskSpecId());
    node.put("flowSliceId", disposition.flowSliceId());
    node.put("round", disposition.round());
    node.put("state", disposition.state());
    nullable(node, "modelRoundId", disposition.modelRoundId());
    nullable(node, "generationReceiptId", disposition.generationReceiptId());
    nullable(node, "upstreamTaskSpecId", disposition.upstreamTaskSpecId());
    strings(node.putArray("gapIds"), disposition.gapIds());
    nullable(node, "reasonCode", disposition.reasonCode());
    return node;
  }

  static ObjectNode runtimeJson(ModelRuntimeIdentityV1 runtime) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("upstreamProvider", runtime.upstreamProvider())
        .put("model", runtime.model())
        .put("reasoningEffort", runtime.reasoningEffort())
        .put("sandbox", runtime.sandbox());
  }

  static ObjectNode referenceJson(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  static String identity(String prefix, String domain, JsonNode projection) {
    return prefix
        + sha256(frame(domain), frame(JSON.encodeCanonical(projection).copyToByteArray()));
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.stream().sorted().forEach(node::add);
  }

  private static void nullable(ObjectNode node, String field, String value) {
    if (value == null) node.putNull(field);
    else node.put(field, value);
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
