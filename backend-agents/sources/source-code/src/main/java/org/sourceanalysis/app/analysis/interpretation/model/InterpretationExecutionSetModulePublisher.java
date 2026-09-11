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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Receipt-last publisher for M5's complete model execution result. */
public final class InterpretationExecutionSetModulePublisher {
  private static final String FILE = "model-execution-set.json";
  private static final String TYPE = "FLOW_INTERPRETATION_MODEL_EXECUTION_SET";
  private static final String SCHEMA = "flow-interpretation-model-execution-set-v5";
  private static final String PREFIX = "flow-interpretation-model-execution-set";
  private final CanonicalModuleArtifactStore store;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public InterpretationExecutionSetModulePublisher(CanonicalModuleArtifactStore store) {
    this.store = Objects.requireNonNull(store, "module artifact store");
  }

  /** Installs M5 only when its three persisted direct inputs have matching controls. */
  public ModulePublicationReference publish(InterpretationExecutionSet execution) {
    try {
      Objects.requireNonNull(execution, "interpretation execution");
      ReopenedModulePublication r0 = store.reopen(execution.registryProposalExecutionRef());
      ReopenedModulePublication registry = store.reopen(execution.registryPublicationRef());
      ReopenedModulePublication tasks = store.reopen(execution.flowTaskSetPublicationRef());
      require(
          r0,
          2,
          "registry-proposal-runner",
          "registry-proposal-execution-set.json",
          "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_EXECUTION_SET",
          "flow-interpretation-registry-proposal-execution-set-v3");
      require(
          registry,
          3,
          "registry-freezer",
          "repository-interpretation-registry.json",
          "FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY_MODULE",
          "flow-interpretation-repository-interpretation-registry-module-v1");
      require(
          tasks,
          4,
          "flow-task-compiler",
          "flow-task-set.json",
          "FLOW_INTERPRETATION_FLOW_TASK_SET",
          "flow-interpretation-flow-task-set-v4");
      ArtifactControls controls = r0.receipt().controls();
      if (!controls.equals(registry.receipt().controls())
          || !controls.equals(tasks.receipt().controls())) throw failure();
      validateExecution(execution, tasks);
      List<ArtifactReference> upstream =
          List.of(reference(r0), reference(registry), reference(tasks)).stream()
              .sorted(Comparator.comparing(value -> value.artifactId().value()))
              .toList();
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              r0.receipt().address().runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              5,
              "interpretation-runner");
      InstalledModulePublication installed =
          store.install(
              new ModuleInstallRequest(
                  address,
                  "v1",
                  upstream,
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload(address, upstream, controls, execution))));
      return installed.reference();
    } catch (FlowModelTaskException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new FlowModelTaskException("MODEL_RESPONSE_INVALID", failure);
    }
  }

  private static void require(
      ReopenedModulePublication publication,
      int number,
      String key,
      String file,
      String type,
      String schema) {
    if (!(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != number
        || !key.equals(address.moduleKey())
        || publication.payloads().size() != 1) throw failure();
    var d = publication.payloads().get(0).descriptor();
    if (!file.equals(d.fileName())
        || !type.equals(d.artifactType())
        || !schema.equals(d.schemaVersion())) throw failure();
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      InterpretationExecutionSet execution) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("executionSetId", execution.executionSetId());
    body.put("flowTaskSetId", execution.flowTaskSetId());
    refs(body.putObject("registryProposalExecutionRef"), execution.registryProposalExecutionRef());
    refs(body.putObject("registryPublicationRef"), execution.registryPublicationRef());
    refs(body.putObject("flowTaskSetPublicationRef"), execution.flowTaskSetPublicationRef());
    ArrayNode rounds = body.putArray("rounds");
    execution.rounds().forEach(v -> round(rounds.addObject(), v));
    ArrayNode receipts = body.putArray("receipts");
    execution.generationReceipts().forEach(v -> receipt(receipts.addObject(), v));
    ArrayNode candidates = body.putArray("candidates");
    execution.candidates().forEach(v -> candidate(candidates.addObject(), v));
    ArrayNode tasks = body.putArray("modelTaskDispositions");
    execution.modelTaskDispositions().forEach(v -> taskDisposition(tasks.addObject(), v));
    ArrayNode flows = body.putArray("flowDispositions");
    execution.flowDispositions().forEach(v -> flowDisposition(flows.addObject(), v));
    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", SCHEMA);
    envelope.put("artifactType", TYPE);
    envelope.set("producer", producer(address));
    envelope.set("upstreamArtifacts", references(upstream));
    envelope.set("controls", controls(controls));
    envelope.set("completion", completion());
    envelope.set("payload", body);
    ArtifactId id =
        ArtifactId.parse(
            PREFIX
                + ":"
                + sha256(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA),
                    frame(TYPE),
                    frame(json.encodeCanonical(envelope).copyToByteArray())));
    envelope.put("artifactId", id.value());
    return new CanonicalModulePayload(
        FILE,
        TYPE,
        SCHEMA,
        id,
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(envelope));
  }

  private static void round(ObjectNode n, ModelRound v) {
    n.put("modelRoundId", v.modelRoundId());
    n.put("taskSpecId", v.taskSpecId());
    n.put("round", v.round());
    n.put("canonicalResponseSha256", v.canonicalResponseSha256().value());
  }

  private static void receipt(ObjectNode n, GenerationReceipt v) {
    JsonNode value = FlowInterpretationIdentity.receiptProjection(v, true);
    n.setAll((ObjectNode) value);
  }

  private static void proposal(ObjectNode n, InterpretationProposal v) {
    n.put("interpretationProposalId", v.interpretationProposalId());
    n.put("registryProposalId", v.registryProposalId());
    n.put("flowSliceId", v.flowSliceId());
    n.put("provisionalKey", v.provisionalKey());
    n.put("selectedKey", v.selectedKey());
    strings(n.putArray("basisAtomIds"), v.basisAtomIds());
    strings(n.putArray("basisGapIds"), v.basisGapIds());
    n.put("r2Decision", v.r2Decision());
  }

  private static void candidate(ObjectNode n, FlowInterpretationCandidate v) {
    n.put("candidateId", v.candidateId());
    n.put("flowSliceId", v.flowSliceId());
    n.put("evidenceCapsuleId", v.evidenceCapsuleId());
    n.put("r1RoundId", v.r1RoundId());
    n.put("r2RoundId", v.r2RoundId());
    ArrayNode proposals = n.putArray("interpretationProposals");
    v.interpretationProposals().forEach(value -> proposal(proposals.addObject(), value));
  }

  private static void taskDisposition(ObjectNode n, ModelTaskDisposition v) {
    n.put("taskSpecId", v.taskSpecId());
    n.put("flowSliceId", v.flowSliceId());
    n.put("round", v.round());
    n.put("state", v.state());
    nullable(n, "modelRoundId", v.modelRoundId());
    nullable(n, "generationReceiptId", v.generationReceiptId());
    nullable(n, "upstreamTaskSpecId", v.upstreamTaskSpecId());
    strings(n.putArray("gapIds"), v.gapIds());
    nullable(n, "reasonCode", v.reasonCode());
  }

  private static void flowDisposition(ObjectNode n, FlowInterpretationDisposition v) {
    n.put("flowInterpretationDispositionId", v.flowInterpretationDispositionId());
    n.put("flowSliceId", v.flowSliceId());
    n.put("disposition", v.disposition());
    nullable(n, "candidateId", v.candidateId());
    strings(n.putArray("gapIds"), v.gapIds());
    nullable(n, "reasonCode", v.reasonCode());
  }

  private static void refs(ObjectNode n, ModulePublicationReference v) {
    n.put("moduleArtifactRoot", v.moduleArtifactRoot().value());
    n.put("moduleReceiptId", v.moduleReceiptId().value());
    n.put("moduleReceiptSha256", v.moduleReceiptSha256().value());
  }

  private static void ref(ObjectNode n, ArtifactReference v) {
    n.put("artifactId", v.artifactId().value());
    n.put("sha256", v.sha256().value());
  }

  private static void validateExecution(
      InterpretationExecutionSet execution, ReopenedModulePublication taskPublication) {
    JsonNode taskBody =
        new CanonicalJsonCodec()
            .parseCanonical(taskPublication.payloads().get(0).canonicalUtf8())
            .path("payload");
    String flowTaskSetId = text(taskBody, "flowTaskSetId");
    if (!flowTaskSetId.equals(execution.flowTaskSetId())) throw failure();
    if (!execution.flowTaskSetPublicationRef().equals(taskPublication.reference())
        || !moduleReferenceMatches(
            taskBody.path("registryPublicationRef"), execution.registryPublicationRef())) {
      throw failure();
    }
    java.util.Map<String, JsonNode> tasks = new java.util.HashMap<>();
    for (JsonNode task : taskBody.path("tasks")) {
      String taskId = text(task, "taskSpecId");
      FlowModelTask materialized = materializeTask(task);
      if (!taskId.equals(FlowInterpretationIdentity.taskId(materialized))
          || tasks.put(taskId, task) != null) throw failure();
    }
    validateTaskRuntimePolicy(taskBody.path("runtimePolicy"), tasks.values());
    if (execution.generationReceipts().size() != execution.rounds().size()
        || execution.modelTaskDispositions().size() != tasks.size()) throw failure();
    java.util.Map<String, ModelRound> rounds = new java.util.HashMap<>();
    for (ModelRound round : execution.rounds()) {
      if (rounds.put(round.taskSpecId(), round) != null || !tasks.containsKey(round.taskSpecId())) {
        throw failure();
      }
    }
    java.util.Map<String, GenerationReceipt> receipts = new java.util.HashMap<>();
    for (GenerationReceipt receipt : execution.generationReceipts()) {
      JsonNode task = tasks.get(receipt.taskSpecId());
      ModelRound round = rounds.get(receipt.taskSpecId());
      if (task == null
          || round == null
          || receipts.put(receipt.taskSpecId(), receipt) != null
          || !receipt.generationReceiptId().equals(FlowInterpretationIdentity.receiptId(receipt))
          || !text(task, "flowSliceId").equals(receipt.flowSliceId())
          || !text(task, "inputJsonSha256").equals(receipt.requestSha256().value())
          || !round.canonicalResponseSha256().equals(receipt.responseSha256())
          || !text(task, "configuredAdapterId").equals(receipt.configuredAdapterId())
          || !text(task, "configuredAuthMode").equals(receipt.configuredAuthMode())
          || !runtime(task.path("expectedRuntime")).equals(receipt.expectedRuntime())
          || !receipt.expectedRuntime().equals(receipt.observedRuntime())
          || receipt.taskShardId() != null
          || !receipt.started()
          || !receipt.completed()) throw failure();
    }
    java.util.Map<String, ModelTaskDisposition> dispositions = new java.util.HashMap<>();
    for (ModelTaskDisposition disposition : execution.modelTaskDispositions()) {
      JsonNode task = tasks.get(disposition.taskSpecId());
      if (task == null
          || dispositions.put(disposition.taskSpecId(), disposition) != null
          || !text(task, "flowSliceId").equals(disposition.flowSliceId())
          || !text(task, "round").equals(disposition.round())) throw failure();
      if ("RESPONSE_ACCEPTED".equals(disposition.state())) {
        ModelRound round = rounds.get(disposition.taskSpecId());
        GenerationReceipt receipt = receipts.get(disposition.taskSpecId());
        if (round == null
            || receipt == null
            || !round.modelRoundId().equals(disposition.modelRoundId())
            || !receipt.generationReceiptId().equals(disposition.generationReceiptId()))
          throw failure();
      }
    }
    for (FlowInterpretationCandidate candidate : execution.candidates()) {
      if (!candidate.candidateId().equals(FlowInterpretationIdentity.candidateId(candidate))
          || candidate.interpretationProposals().stream()
              .anyMatch(
                  proposal ->
                      !candidate.flowSliceId().equals(proposal.flowSliceId())
                          || !proposal
                              .interpretationProposalId()
                              .equals(FlowInterpretationIdentity.proposalId(proposal))))
        throw failure();
      ModelTaskDisposition r1 = dispositionForFlow(dispositions, candidate.flowSliceId(), "R1");
      ModelTaskDisposition r2 = dispositionForFlow(dispositions, candidate.flowSliceId(), "R2");
      if (r1 == null
          || r2 == null
          || !"RESPONSE_ACCEPTED".equals(r1.state())
          || !"RESPONSE_ACCEPTED".equals(r2.state())
          || !candidate.r1RoundId().equals(r1.modelRoundId())
          || !candidate.r2RoundId().equals(r2.modelRoundId())) throw failure();
    }
    String expectedId =
        FlowInterpretationIdentity.executionSetId(
            flowTaskSetId,
            execution.rounds(),
            execution.generationReceipts(),
            execution.candidates(),
            execution.modelTaskDispositions(),
            execution.flowDispositions());
    if (!expectedId.equals(execution.executionSetId())) throw failure();
  }

  private static String text(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) throw failure();
    return value.textValue();
  }

  private static FlowModelTask materializeTask(JsonNode task) {
    ImmutableBytes input = new CanonicalJsonCodec().encodeCanonical(task.path("inputJson"));
    Sha256Digest inputSha = new Sha256Digest(text(task, "inputJsonSha256"));
    if (!inputSha.equals(new Sha256Digest(sha256(input.copyToByteArray())))) throw failure();
    return new FlowModelTask(
        text(task, "taskSpecId"),
        text(task, "taskKind"),
        text(task, "round"),
        text(task, "flowSliceId"),
        text(task, "evidenceCapsuleId"),
        text(task, "isolatedSessionKey"),
        strings(task.path("allowedKeys")),
        input,
        inputSha,
        new Sha256Digest(text(task, "outputSchemaSha256")),
        new Sha256Digest(text(task, "promptBundleSha256")),
        text(task, "configuredAdapterId"),
        text(task, "configuredAuthMode"),
        reference(task.path("expectedRuntimeRef")),
        runtime(task.path("expectedRuntime")));
  }

  private static void validateTaskRuntimePolicy(
      JsonNode runtimePolicy, java.util.Collection<JsonNode> taskNodes) {
    if (!runtimePolicy.isObject()) throw failure();
    for (JsonNode task : taskNodes) {
      String prefix = "R1".equals(text(task, "round")) ? "r1" : "r2";
      if (!text(runtimePolicy, prefix + "ConfiguredAdapterId")
              .equals(text(task, "configuredAdapterId"))
          || !text(runtimePolicy, prefix + "ConfiguredAuthMode")
              .equals(text(task, "configuredAuthMode"))
          || !reference(runtimePolicy.path(prefix + "ExpectedRuntimeRef"))
              .equals(reference(task.path("expectedRuntimeRef")))
          || !runtime(runtimePolicy.path(prefix + "ExpectedRuntime"))
              .equals(runtime(task.path("expectedRuntime")))) throw failure();
    }
  }

  private static ModelTaskDisposition dispositionForFlow(
      java.util.Map<String, ModelTaskDisposition> dispositions, String flowSliceId, String round) {
    return dispositions.values().stream()
        .filter(value -> flowSliceId.equals(value.flowSliceId()) && round.equals(value.round()))
        .findFirst()
        .orElse(null);
  }

  private static boolean moduleReferenceMatches(
      JsonNode source, ModulePublicationReference reference) {
    return source.isObject()
        && reference.moduleArtifactRoot().value().equals(text(source, "moduleArtifactRoot"))
        && reference.moduleReceiptId().value().equals(text(source, "moduleReceiptId"))
        && reference.moduleReceiptSha256().value().equals(text(source, "moduleReceiptSha256"));
  }

  private static java.util.List<String> strings(JsonNode source) {
    if (!source.isArray()) throw failure();
    java.util.List<String> values = new java.util.ArrayList<>();
    for (JsonNode value : source) {
      if (!value.isTextual() || value.textValue().isBlank()) throw failure();
      values.add(value.textValue());
    }
    values.sort(String::compareTo);
    if (values.size() != new java.util.HashSet<>(values).size()) throw failure();
    return java.util.List.copyOf(values);
  }

  private static ArtifactReference reference(JsonNode source) {
    if (!source.isObject()) throw failure();
    return new ArtifactReference(
        ArtifactId.parse(text(source, "artifactId")), new Sha256Digest(text(source, "sha256")));
  }

  private static org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1 runtime(
      JsonNode source) {
    return new org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1(
        text(source, "upstreamProvider"),
        text(source, "model"),
        text(source, "reasoningEffort"),
        text(source, "sandbox"));
  }

  private static ArtifactReference reference(ReopenedModulePublication p) {
    var d = p.payloads().get(0).descriptor();
    return new ArtifactReference(d.artifactId(), d.sha256());
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode n = JsonNodeFactory.instance.arrayNode();
    values.forEach(v -> ref(n.addObject(), v));
    return n;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress a) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    ObjectNode p = n.putObject("address");
    p.put("kind", "ANALYSIS_STEP");
    p.put("runId", a.runId().value());
    p.put("analysisStepKey", a.analysisStepKey().wireValue());
    p.put("moduleNumber", a.moduleNumber());
    p.put("moduleKey", a.moduleKey());
    n.put("moduleVersion", "v1");
    return n;
  }

  private static ObjectNode controls(ArtifactControls v) {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("toolchainSha256", v.toolchainSha256().value());
    n.put("profileSha256", v.profileSha256().value());
    n.put("schemaBundleSha256", v.schemaBundleSha256().value());
    nullable(
        n,
        "promptBundleSha256",
        v.promptBundleSha256() == null ? null : v.promptBundleSha256().value());
    n.putObject("artifactPolicyRegistryRef")
        .put("artifactId", v.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", v.artifactPolicyRegistryRef().sha256().value());
    return n;
  }

  private static ObjectNode completion() {
    ObjectNode n = JsonNodeFactory.instance.objectNode();
    n.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    n.putArray("gapRefs");
    n.putNull("failureRef");
    return n;
  }

  private static void strings(ArrayNode n, List<String> v) {
    v.stream().sorted().forEach(n::add);
  }

  private static void nullable(ObjectNode n, String k, String v) {
    if (v == null) n.putNull(k);
    else n.put(k, v);
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      for (byte[] v : values) d.update(v);
      return HexFormat.of().formatHex(d.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] frame(String value) {
    byte[] b = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + b.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(b.length)
        .put(b)
        .array();
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static FlowModelTaskException failure() {
    return new FlowModelTaskException("MODEL_RESPONSE_INVALID");
  }
}
