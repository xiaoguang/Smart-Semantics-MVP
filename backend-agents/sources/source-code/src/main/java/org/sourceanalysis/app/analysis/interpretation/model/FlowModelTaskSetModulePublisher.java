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
import java.util.ArrayList;
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
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Receipt-last publisher for the closed M4 R1/R2 task plan. */
public final class FlowModelTaskSetModulePublisher {

  private static final String REGISTRY_FILE = "repository-interpretation-registry.json";
  private static final String REGISTRY_TYPE = "FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY";
  private static final String REGISTRY_SCHEMA = "flow-interpretation-repository-interpretation-registry-v2";
  private static final String FILE_NAME = "flow-task-set.json";
  private static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_FLOW_TASK_SET";
  private static final String SCHEMA_VERSION = "flow-interpretation-flow-task-set-v4";
  private static final String PREFIX = "flow-interpretation-flow-task-set";
  private static final String MODULE_VERSION = "v1";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final FiniteKeyFlowTaskCompiler compiler;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates M4's only receipt-last publisher. */
  public FlowModelTaskSetModulePublisher(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
    compiler = new FiniteKeyFlowTaskCompiler(moduleArtifacts, analysisSteps);
  }

  /** Recomputes the plan from fresh public/M3 bytes and atomically installs its canonical snapshot. */
  public ModulePublicationReference publish(
      BusinessFlowsReference businessFlows,
      ModulePublicationReference registryPublication,
      FlowModelTaskSet taskSet) {
    try {
      Objects.requireNonNull(businessFlows, "business flows");
      Objects.requireNonNull(registryPublication, "registry publication");
      Objects.requireNonNull(taskSet, "flow task set");
      if (!registryPublication.equals(taskSet.registryPublicationRef())) throw failure();
      FlowModelTaskSet recomputed =
          compiler.compileFiniteKeyTasks(businessFlows, registryPublication, taskSet.taskProfile());
      if (!recomputed.equals(taskSet)) throw failure();
      ReopenedAnalysisStepPublication flows = analysisSteps.reopen(businessFlows.publication());
      ReopenedModulePublication registry = moduleArtifacts.reopen(registryPublication);
      requireRegistry(registry, registryPublication);
      if (!flows.receipt().controls().equals(registry.receipt().controls())) throw failure();
      List<ArtifactReference> upstream = new ArrayList<>();
      flows.semanticPayloads().forEach(value -> upstream.add(reference(value.descriptor().artifactId(), value.descriptor().sha256())));
      upstream.add(reference(registry.payloads().get(0).descriptor().artifactId(), registry.payloads().get(0).descriptor().sha256()));
      upstream.sort(Comparator.comparing(value -> value.artifactId().value()));
      if (upstream.size() != 6 || upstream.stream().map(ArtifactReference::artifactId).distinct().count() != 6) throw failure();
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              businessFlows.publication().address().runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              4,
              "flow-task-compiler");
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstream,
                  flows.receipt().controls(),
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload(address, upstream, flows.receipt().controls(), taskSet))));
      return installed.reference();
    } catch (FlowModelTaskException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new FlowModelTaskException("MODEL_TASK_INVALID", failure);
    }
  }

  private static void requireRegistry(ReopenedModulePublication value, ModulePublicationReference reference) {
    if (!reference.equals(value.reference())
        || !(value.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 3
        || !"registry-freezer".equals(address.moduleKey())
        || value.payloads().size() != 1) throw failure();
    var descriptor = value.payloads().get(0).descriptor();
    if (!REGISTRY_FILE.equals(descriptor.fileName())
        || !REGISTRY_TYPE.equals(descriptor.artifactType())
        || !REGISTRY_SCHEMA.equals(descriptor.schemaVersion())) throw failure();
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      FlowModelTaskSet taskSet) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("flowTaskSetId", taskSet.flowTaskSetId());
    body.put("repositoryInterpretationRegistryId", taskSet.repositoryInterpretationRegistryId());
    moduleReference(body.putObject("registryPublicationRef"), taskSet.registryPublicationRef());
    strings(body.putArray("eligibleR1R2FlowSliceIds"), taskSet.eligibleR1R2FlowSliceIds());
    ArrayNode tasks = body.putArray("tasks");
    taskSet.tasks().forEach(value -> task(tasks.addObject(), value));
    ArrayNode r1 = body.putArray("r1ShardReceipts");
    taskSet.r1ShardReceipts().forEach(value -> shard(r1.addObject(), value));
    ArrayNode r2 = body.putArray("r2ShardReceipts");
    taskSet.r2ShardReceipts().forEach(value -> shard(r2.addObject(), value));
    runtimePolicy(body.putObject("runtimePolicy"), taskSet.taskProfile());

    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", SCHEMA_VERSION);
    envelope.put("artifactType", ARTIFACT_TYPE);
    envelope.set("producer", producer(address));
    envelope.set("upstreamArtifacts", references(upstream));
    envelope.set("controls", controls(controls));
    envelope.set("completion", completion());
    envelope.set("payload", body);
    ArtifactId artifactId = ArtifactId.parse(PREFIX + ":" + sha256(frame("canonical-module-artifact-id-v1"), frame(SCHEMA_VERSION), frame(ARTIFACT_TYPE), frame(canonicalJson.encodeCanonical(envelope).copyToByteArray())));
    envelope.put("artifactId", artifactId.value());
    return new CanonicalModulePayload(FILE_NAME, ARTIFACT_TYPE, SCHEMA_VERSION, artifactId, CanonicalMediaType.APPLICATION_JSON, canonicalJson.encodeCanonical(envelope));
  }

  private void task(ObjectNode node, FlowModelTask value) {
    node.put("taskSpecId", value.taskSpecId());
    node.put("taskKind", value.taskKind());
    node.put("round", value.round());
    node.put("flowSliceId", value.flowSliceId());
    node.put("evidenceCapsuleId", value.evidenceCapsuleId());
    node.put("isolatedSessionKey", value.isolatedSessionKey());
    strings(node.putArray("allowedKeys"), value.allowedKeys());
    JsonNode input = canonicalJson.parseCanonical(value.inputJson());
    node.set("inputJson", input);
    node.put("inputJsonSha256", value.inputJsonSha256().value());
    node.put("outputSchemaSha256", value.outputSchemaSha256().value());
    node.put("promptBundleSha256", value.promptBundleSha256().value());
    reference(node.putObject("expectedRuntime"), value.expectedRuntime());
  }

  private static void shard(ObjectNode node, FlowModelTaskShardReceipt value) {
    node.put("shardId", value.shardId());
    node.put("round", value.round());
    strings(node.putArray("denominatorFlowSliceIds"), value.denominatorFlowSliceIds());
    strings(node.putArray("outputTaskSpecIds"), value.outputTaskSpecIds());
  }

  private static void runtimePolicy(ObjectNode node, FlowModelTaskProfile value) {
    reference(node.putObject("r1PromptBundleRef"), value.r1PromptBundleRef());
    reference(node.putObject("r1OutputSchemaRef"), value.r1OutputSchemaRef());
    reference(node.putObject("r1ExpectedRuntimeRef"), value.r1ExpectedRuntimeRef());
    reference(node.putObject("r1ResourceBudgetRef"), value.r1ResourceBudgetRef());
    reference(node.putObject("r2PromptBundleRef"), value.r2PromptBundleRef());
    reference(node.putObject("r2OutputSchemaRef"), value.r2OutputSchemaRef());
    reference(node.putObject("r2ExpectedRuntimeRef"), value.r2ExpectedRuntimeRef());
    reference(node.putObject("r2ResourceBudgetRef"), value.r2ResourceBudgetRef());
    node.put("maxTasks", value.maxTasks());
    node.put("maxResponseUtf8Bytes", value.maxResponseUtf8Bytes());
    node.put("maxSelectedKeys", value.maxSelectedKeys());
    node.put("maxCandidateProposals", value.maxCandidateProposals());
  }

  private static void moduleReference(ObjectNode node, ModulePublicationReference value) {
    node.put("moduleArtifactRoot", value.moduleArtifactRoot().value());
    node.put("moduleReceiptId", value.moduleReceiptId().value());
    node.put("moduleReceiptSha256", value.moduleReceiptSha256().value());
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ObjectNode producer = node.putObject("address");
    producer.put("kind", "ANALYSIS_STEP");
    producer.put("runId", address.runId().value());
    producer.put("analysisStepKey", address.analysisStepKey().wireValue());
    producer.put("moduleNumber", address.moduleNumber());
    producer.put("moduleKey", address.moduleKey());
    node.put("moduleVersion", MODULE_VERSION);
    return node;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> reference(result.addObject(), value));
    return result;
  }

  private static void reference(ObjectNode node, ArtifactReference value) {
    node.put("artifactId", value.artifactId().value());
    node.put("sha256", value.sha256().value());
  }

  private static ArtifactReference reference(ArtifactId artifactId, org.sourceanalysis.app.artifact.Sha256Digest sha) {
    return new ArtifactReference(artifactId, sha);
  }

  private static ObjectNode controls(ArtifactControls value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("toolchainSha256", value.toolchainSha256().value());
    node.put("profileSha256", value.profileSha256().value());
    node.put("schemaBundleSha256", value.schemaBundleSha256().value());
    if (value.promptBundleSha256() == null) node.putNull("promptBundleSha256"); else node.put("promptBundleSha256", value.promptBundleSha256().value());
    node.putObject("artifactPolicyRegistryRef")
        .put("artifactId", value.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", value.artifactPolicyRegistryRef().sha256().value());
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
    values.stream().sorted().forEach(node::add);
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
    return ByteBuffer.allocate(Long.BYTES + value.length).order(ByteOrder.BIG_ENDIAN).putLong(value.length).put(value).array();
  }

  private static FlowModelTaskException failure() {
    return new FlowModelTaskException("MODEL_TASK_INVALID");
  }
}
