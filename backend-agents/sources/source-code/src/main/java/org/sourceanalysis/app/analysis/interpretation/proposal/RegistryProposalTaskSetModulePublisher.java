package org.sourceanalysis.app.analysis.interpretation.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;

/** Persists the verified R0 task set before a Provider-facing module can read it. */
public final class RegistryProposalTaskSetModulePublisher {

  private static final String FILE_NAME = "registry-proposal-task-set.json";
  private static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_TASK_SET";
  private static final String SCHEMA_VERSION = "flow-interpretation-registry-proposal-task-set-v2";
  private static final String ARTIFACT_PREFIX = "flow-interpretation-registry-proposal-task-set";
  private static final String MODULE_VERSION = "v1";
  private static final Comparator<String> UTF8_ORDER =
      RegistryProposalTaskSetModulePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final RegistryProposalTaskCompiler compiler;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the only receipt-last writer for M1's R0 task-set artifact. */
  public RegistryProposalTaskSetModulePublisher(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
    compiler = new RegistryProposalTaskCompiler(analysisSteps);
  }

  /**
   * Recomputes the task set from public BusinessFlows bytes, then installs its canonical snapshot.
   */
  public ModulePublicationReference publish(
      BusinessFlowsReference businessFlows, RegistryProposalTaskSet taskSet) {
    try {
      Objects.requireNonNull(businessFlows, "business flows");
      Objects.requireNonNull(taskSet, "registry proposal task set");
      if (!businessFlows.publication().equals(taskSet.businessFlowsPublicationRef())) {
        throw failure();
      }
      RegistryProposalTaskSet recomputed =
          compiler.compileRegistryProposalTasks(businessFlows, taskSet.taskProfile());
      if (!recomputed.equals(taskSet)) throw failure();
      ReopenedAnalysisStepPublication source = analysisSteps.reopen(businessFlows.publication());
      List<ArtifactReference> upstream =
          source.semanticPayloads().stream()
              .map(
                  value ->
                      new ArtifactReference(
                          value.descriptor().artifactId(), value.descriptor().sha256()))
              .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
              .toList();
      if (upstream.size() != 5) throw failure();
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              businessFlows.publication().address().runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              1,
              "registry-task-compiler");
      CanonicalModulePayload payload =
          payload(address, taskSet, upstream, source.receipt().controls());
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstream,
                  source.receipt().controls(),
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      return installed.reference();
    } catch (RegistryProposalTaskCompilationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new RegistryProposalTaskCompilationException("REGISTRY_PROPOSAL_TASK_INVALID", failure);
    }
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      RegistryProposalTaskSet taskSet,
      List<ArtifactReference> upstream,
      ArtifactControls controls) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("taskSetId", taskSet.taskSetId());
    analysisStepReference(body.putObject("businessFlowsPublicationRef"), taskSet);
    body.put("flowCount", taskSet.eligibleFlowSliceIds().size());
    strings(body.putArray("eligibleFlowSliceIds"), taskSet.eligibleFlowSliceIds());
    ArrayNode tasks = body.putArray("tasks");
    taskSet.tasks().forEach(task -> task(tasks.addObject(), task));
    ArrayNode shardReceipts = body.putArray("taskShardReceipts");
    taskSet.taskShardReceipts().forEach(receipt -> shard(shardReceipts.addObject(), receipt));
    runtimePolicy(body.putObject("runtimePolicy"), taskSet.taskProfile());
    body.putNull("organizationRegistrySeedRef");

    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set("completion", completion());
    withoutArtifactId.set("payload", body);
    ArtifactId artifactId =
        ArtifactId.parse(
            ARTIFACT_PREFIX
                + ":"
                + sha256(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId.value());
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        artifactId,
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static void analysisStepReference(ObjectNode node, RegistryProposalTaskSet taskSet) {
    var reference = taskSet.businessFlowsPublicationRef();
    node.put("runId", reference.address().runId().value());
    node.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    node.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    node.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    node.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
  }

  private void task(ObjectNode node, RegistryProposalTask task) {
    node.put("taskSpecId", task.taskSpecId());
    node.put("taskKind", task.taskKind());
    node.put("flowSliceId", task.flowSliceId());
    node.put("evidenceCapsuleId", task.evidenceCapsuleId());
    node.put("isolatedSessionKey", task.isolatedSessionKey());
    JsonNode input = canonicalJson.parseCanonical(task.inputJson());
    node.set("inputJson", input);
    node.put("inputJsonSha256", task.inputJsonSha256().value());
    node.put("outputSchemaSha256", task.outputSchemaSha256().value());
    node.put("promptBundleSha256", task.promptBundleSha256().value());
    reference(node.putObject("expectedRuntime"), task.expectedRuntime());
  }

  private static void shard(ObjectNode node, RegistryProposalTaskShardReceipt receipt) {
    node.put("shardId", receipt.shardId());
    strings(node.putArray("denominatorFlowSliceIds"), receipt.denominatorFlowSliceIds());
    strings(node.putArray("outputTaskSpecIds"), receipt.outputTaskSpecIds());
  }

  private static void runtimePolicy(ObjectNode node, RegistryProposalTaskProfile profile) {
    reference(node.putObject("promptBundleRef"), profile.promptBundleRef());
    reference(node.putObject("outputSchemaRef"), profile.outputSchemaRef());
    reference(node.putObject("expectedRuntimeRef"), profile.expectedRuntimeRef());
    reference(node.putObject("resourceBudgetRef"), profile.resourceBudgetRef());
    node.put("maxTasks", profile.maxTasks());
    node.put("maxProposalsPerTask", profile.maxProposalsPerTask());
    node.put("maxResponseUtf8Bytes", profile.maxResponseUtf8Bytes());
    node.put("maxLabelUtf8Bytes", profile.maxLabelUtf8Bytes());
    node.put("maxPurposeUtf8Bytes", profile.maxPurposeUtf8Bytes());
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ObjectNode producerAddress = node.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", address.runId().value());
    producerAddress.put("analysisStepKey", address.analysisStepKey().wireValue());
    producerAddress.put("moduleNumber", address.moduleNumber());
    producerAddress.put("moduleKey", address.moduleKey());
    node.put("moduleVersion", MODULE_VERSION);
    return node;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode node = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> reference(node.addObject(), value));
    return node;
  }

  private static void reference(ObjectNode node, ArtifactReference value) {
    node.put("artifactId", value.artifactId().value());
    node.put("sha256", value.sha256().value());
  }

  private static ObjectNode controls(ArtifactControls values) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("toolchainSha256", values.toolchainSha256().value());
    node.put("profileSha256", values.profileSha256().value());
    node.put("schemaBundleSha256", values.schemaBundleSha256().value());
    if (values.promptBundleSha256() == null) node.putNull("promptBundleSha256");
    else node.put("promptBundleSha256", values.promptBundleSha256().value());
    node.putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return node;
  }

  private static ObjectNode completion() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    node.putArray("gapRefs");
    node.putNull("failureRef");
    return node;
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
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

  private static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(first.length, second.length);
  }

  private static RegistryProposalTaskCompilationException failure() {
    return new RegistryProposalTaskCompilationException("REGISTRY_PROPOSAL_TASK_INVALID");
  }
}
