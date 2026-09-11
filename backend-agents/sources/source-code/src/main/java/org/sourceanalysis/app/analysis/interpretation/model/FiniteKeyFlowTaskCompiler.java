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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Builds R1/R2 requests only from a receipt-last frozen registry and same-Flow Capsule bytes. */
public final class FiniteKeyFlowTaskCompiler {

  private static final String CAPSULE_FILE = "evidence-capsules.jsonl";
  private static final String CAPSULE_TYPE = "BUSINESS_FLOWS_EVIDENCE_CAPSULE";
  private static final String CAPSULE_SCHEMA = "business-flows-evidence-capsule-v6";
  private static final String REGISTRY_FILE = "repository-interpretation-registry.json";
  private static final String REGISTRY_TYPE =
      "FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY_MODULE";
  private static final String REGISTRY_SCHEMA =
      "flow-interpretation-repository-interpretation-registry-module-v1";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /**
   * Creates an M4 compiler that can fresh-reopen the M3 registry and BusinessFlows public bytes.
   */
  public FiniteKeyFlowTaskCompiler(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
  }

  /** Compiles exactly one R1 and one R2 task for every R0-ready Flow. */
  public FlowModelTaskSet compileFiniteKeyTasks(
      BusinessFlowsReference businessFlows,
      ModulePublicationReference registryPublication,
      FlowModelTaskProfile taskProfile) {
    try {
      Objects.requireNonNull(businessFlows, "business flows");
      Objects.requireNonNull(registryPublication, "registry publication");
      Objects.requireNonNull(taskProfile, "flow model task profile");
      ReopenedAnalysisStepPublication flowPublication =
          analysisSteps.reopen(businessFlows.publication());
      verifyBusinessFlows(businessFlows, flowPublication, taskProfile);
      Registry registry = reopenRegistry(registryPublication, flowPublication);
      Map<String, JsonNode> capsules = reopenCapsules(flowPublication);
      List<String> readyFlows = registry.readyFlowIds();
      if (readyFlows.size() * 2 > taskProfile.maxTasks()) {
        throw failure("FLOW_INTERPRETATION_RESOURCE_LIMIT_EXCEEDED");
      }
      List<FlowModelTask> tasks = new ArrayList<>();
      for (String flowId : readyFlows) {
        JsonNode capsule = capsules.get(flowId);
        if (capsule == null) throw failure("FLOW_CAPSULE_SET_INVALID");
        List<JsonNode> items = registry.itemsByFlow().get(flowId);
        if (items == null || items.isEmpty()) throw failure("MODEL_TASK_INVALID");
        tasks.add(r1(flowId, capsule, registry, items, taskProfile));
        tasks.add(r2(flowId, capsule, registry, items, taskProfile, tasks.get(tasks.size() - 1)));
      }
      tasks.sort(
          Comparator.comparing(FlowModelTask::flowSliceId).thenComparing(FlowModelTask::round));
      List<String> r1Ids =
          tasks.stream()
              .filter(value -> "R1".equals(value.round()))
              .map(FlowModelTask::taskSpecId)
              .toList();
      List<String> r2Ids =
          tasks.stream()
              .filter(value -> "R2".equals(value.round()))
              .map(FlowModelTask::taskSpecId)
              .toList();
      FlowModelTaskShardReceipt r1Shard =
          new FlowModelTaskShardReceipt(
              contentId("flow-model-r1-shard", readyFlows), "R1", readyFlows, r1Ids);
      FlowModelTaskShardReceipt r2Shard =
          new FlowModelTaskShardReceipt(
              contentId("flow-model-r2-shard", readyFlows), "R2", readyFlows, r2Ids);
      return new FlowModelTaskSet(
          taskSetId(registryPublication, registry.registryId(), r1Ids, r2Ids, taskProfile),
          registryPublication,
          registry.registryId(),
          readyFlows,
          tasks,
          List.of(r1Shard),
          List.of(r2Shard),
          taskProfile);
    } catch (FlowModelTaskException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("MODEL_TASK_INVALID");
    }
  }

  private void verifyBusinessFlows(
      BusinessFlowsReference flows,
      ReopenedAnalysisStepPublication publication,
      FlowModelTaskProfile profile) {
    if (!flows.publication().equals(publication.reference())
        || publication.receipt().address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
        || publication.semanticPayloads().size() != 5) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    ArtifactControls controls = publication.receipt().controls();
    if (!profile.r1OutputSchemaRef().sha256().equals(controls.schemaBundleSha256())
        || !profile.r2OutputSchemaRef().sha256().equals(controls.schemaBundleSha256())
        || !profile.r1ExpectedRuntimeRef().sha256().equals(controls.profileSha256())
        || !profile.r2ExpectedRuntimeRef().sha256().equals(controls.profileSha256())) {
      throw failure("MODEL_TASK_INVALID");
    }
  }

  private Registry reopenRegistry(
      ModulePublicationReference reference, ReopenedAnalysisStepPublication flowPublication) {
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    List<ArtifactReference> flowPayloads =
        flowPublication.semanticPayloads().stream()
            .map(FiniteKeyFlowTaskCompiler::reference)
            .toList();
    if (!reference.equals(publication.reference())
        || !(publication.receipt().address()
            instanceof org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 3
        || !"registry-freezer".equals(address.moduleKey())
        || !flowPublication.reference().address().runId().equals(address.runId())
        || !flowPublication.receipt().controls().equals(publication.receipt().controls())
        || publication.receipt().upstreamArtifacts().size() != 7
        || !publication.receipt().upstreamArtifacts().containsAll(flowPayloads)
        || publication.payloads().size() != 1) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!REGISTRY_FILE.equals(payload.descriptor().fileName())
        || !REGISTRY_TYPE.equals(payload.descriptor().artifactType())
        || !REGISTRY_SCHEMA.equals(payload.descriptor().schemaVersion())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    JsonNode body = canonicalJson.parseCanonical(payload.canonicalUtf8()).path("payload");
    String registryId = identifier(body, "repositoryInterpretationRegistryId");
    Set<String> eligible = new HashSet<>(identifierArray(body, "eligibleFlowSliceIds"));
    Map<String, List<JsonNode>> items = new HashMap<>();
    for (JsonNode item : array(body, "items")) {
      String flowId = identifier(item, "flowSliceId");
      String capsuleId = identifier(item, "evidenceCapsuleId");
      String key = text(item, "provisionalKey");
      if (!key.startsWith("TERM_P_")
          && !key.startsWith("CLAIM_P_")
          && !key.startsWith("QUESTION_P_")) {
        throw failure("MODEL_TASK_INVALID");
      }
      if (!eligible.contains(flowId) || capsuleId.isBlank()) throw failure("MODEL_TASK_INVALID");
      items.computeIfAbsent(flowId, ignored -> new ArrayList<>()).add(item.deepCopy());
    }
    List<String> ready = new ArrayList<>();
    for (JsonNode disposition : array(body, "flowDispositions")) {
      String flowId = identifier(disposition, "flowSliceId");
      if (!eligible.remove(flowId)) throw failure("MODEL_TASK_INVALID");
      String value = text(disposition, "disposition");
      if ("READY_FOR_FREEZE".equals(value)) ready.add(flowId);
      else if (!"GAP".equals(value) && !"FAILED".equals(value)) throw failure("MODEL_TASK_INVALID");
    }
    if (!eligible.isEmpty()) throw failure("MODEL_TASK_INVALID");
    ready.sort(String::compareTo);
    items
        .values()
        .forEach(
            values -> values.sort(Comparator.comparing(value -> text(value, "provisionalKey"))));
    return new Registry(registryId, List.copyOf(ready), Map.copyOf(items));
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private Map<String, JsonNode> reopenCapsules(ReopenedAnalysisStepPublication publication) {
    VerifiedCanonicalPayload payload =
        publication.semanticPayloads().stream()
            .filter(value -> CAPSULE_FILE.equals(value.descriptor().fileName()))
            .findFirst()
            .orElseThrow(() -> failure("FLOW_INTERPRETATION_INPUT_INVALID"));
    if (!CAPSULE_TYPE.equals(payload.descriptor().artifactType())
        || !CAPSULE_SCHEMA.equals(payload.descriptor().schemaVersion())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    String raw = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    if (raw.isEmpty()) return Map.of();
    if (!raw.endsWith("\n")) throw failure("FLOW_CAPSULE_SET_INVALID");
    Map<String, JsonNode> result = new HashMap<>();
    for (String line : raw.substring(0, raw.length() - 1).split("\n", -1)) {
      JsonNode capsule =
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
      if (!CAPSULE_TYPE.equals(text(capsule, "artifactType"))
          || !CAPSULE_SCHEMA.equals(text(capsule, "schemaVersion"))) {
        throw failure("FLOW_CAPSULE_SET_INVALID");
      }
      String flowId = identifier(capsule, "flowSliceId");
      if (result.put(flowId, capsule.deepCopy()) != null) throw failure("FLOW_CAPSULE_SET_INVALID");
    }
    return Map.copyOf(result);
  }

  private FlowModelTask r1(
      String flowId,
      JsonNode capsule,
      Registry registry,
      List<JsonNode> items,
      FlowModelTaskProfile profile) {
    ObjectNode input =
        input(
            "R1_INTERPRETATION_INPUT", flowId, capsule, registry, items, profile.maxSelectedKeys());
    ImmutableBytes bytes = canonicalJson.encodeCanonical(input);
    Sha256Digest digest = new Sha256Digest(sha256(bytes.copyToByteArray()));
    return FlowModelTask.create(
        "R1_INTERPRETATION",
        "R1",
        flowId,
        identifier(capsule, "evidenceCapsuleId"),
        contentId(
            "flow-model-session",
            List.of(flowId, identifier(capsule, "evidenceCapsuleId"), registry.registryId())),
        keys(items),
        bytes,
        digest,
        profile.r1OutputSchemaRef().sha256(),
        profile.r1PromptBundleRef().sha256(),
        profile.r1ConfiguredAdapterId(),
        profile.r1ConfiguredAuthMode(),
        profile.r1ExpectedRuntimeRef(),
        profile.r1ExpectedRuntime());
  }

  private FlowModelTask r2(
      String flowId,
      JsonNode capsule,
      Registry registry,
      List<JsonNode> items,
      FlowModelTaskProfile profile,
      FlowModelTask r1) {
    ObjectNode input =
        input("R2_REVIEW_INPUT", flowId, capsule, registry, items, profile.maxSelectedKeys());
    input
        .putObject("reviewTarget")
        .put("protocol", "SAME_SESSION_PRIOR_R1_RESPONSE")
        .put("r1TaskSpecId", r1.taskSpecId());
    ImmutableBytes bytes = canonicalJson.encodeCanonical(input);
    Sha256Digest digest = new Sha256Digest(sha256(bytes.copyToByteArray()));
    return FlowModelTask.create(
        "R2_PRECISION_REVIEW",
        "R2",
        flowId,
        identifier(capsule, "evidenceCapsuleId"),
        r1.isolatedSessionKey(),
        keys(items),
        bytes,
        digest,
        profile.r2OutputSchemaRef().sha256(),
        profile.r2PromptBundleRef().sha256(),
        profile.r2ConfiguredAdapterId(),
        profile.r2ConfiguredAuthMode(),
        profile.r2ExpectedRuntimeRef(),
        profile.r2ExpectedRuntime());
  }

  private static String taskSetId(
      ModulePublicationReference registryPublication,
      String registryId,
      List<String> r1TaskIds,
      List<String> r2TaskIds,
      FlowModelTaskProfile profile) {
    return contentId(
        "flow-model-task-set",
        List.of(
            registryPublication.moduleArtifactRoot().value(),
            registryId,
            String.join("|", r1TaskIds),
            String.join("|", r2TaskIds),
            profile.r1PromptBundleRef().sha256().value(),
            profile.r1ConfiguredAdapterId(),
            profile.r1ConfiguredAuthMode(),
            profile.r1ExpectedRuntime().upstreamProvider(),
            profile.r1ExpectedRuntime().model(),
            profile.r1ExpectedRuntime().reasoningEffort(),
            profile.r1ExpectedRuntime().sandbox(),
            profile.r2PromptBundleRef().sha256().value(),
            profile.r2ConfiguredAdapterId(),
            profile.r2ConfiguredAuthMode(),
            profile.r2ExpectedRuntime().upstreamProvider(),
            profile.r2ExpectedRuntime().model(),
            profile.r2ExpectedRuntime().reasoningEffort(),
            profile.r2ExpectedRuntime().sandbox()));
  }

  private ObjectNode input(
      String kind,
      String flowId,
      JsonNode capsule,
      Registry registry,
      List<JsonNode> items,
      int maxSelectedKeys) {
    if (items.size() > maxSelectedKeys || !flowId.equals(identifier(capsule, "flowSliceId"))) {
      throw failure("MODEL_TASK_INVALID");
    }
    String capsuleId = identifier(capsule, "evidenceCapsuleId");
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.put("schemaVersion", "flow-interpretation-flow-model-input-v1");
    input.put("kind", kind);
    input.put("flowSliceId", flowId);
    input.put("evidenceCapsuleId", capsuleId);
    input.put("repositoryInterpretationRegistryId", registry.registryId());
    input.set("capsuleView", view(capsule));
    ArrayNode allowed = input.putArray("allowedRegistryItems");
    for (JsonNode item : items) {
      if (!flowId.equals(identifier(item, "flowSliceId"))
          || !capsuleId.equals(identifier(item, "evidenceCapsuleId"))) {
        throw failure("MODEL_REFERENCE_INVALID");
      }
      allowed.add(item.deepCopy());
    }
    return input;
  }

  private static ObjectNode view(JsonNode capsule) {
    ObjectNode result = ((ObjectNode) capsule).deepCopy();
    for (JsonNode fact : array(result, "factViews")) {
      artifactReference(fact, "originFactArtifactRef");
      ((ObjectNode) fact).remove("originFactArtifactRef");
    }
    for (JsonNode gap : array(result, "gapViews")) {
      text(gap, "originKind");
      nullableArtifactReference(gap, "originGapLedgerRef");
      orderedArtifactReferences(gap, "evidenceRefs");
      ((ObjectNode) gap).remove("originKind");
      ((ObjectNode) gap).remove("originGapLedgerRef");
    }
    return result;
  }

  private static void artifactReference(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null || value.isNull()) throw failure("MODEL_TASK_INVALID");
    artifactReferenceId(value);
  }

  private static void nullableArtifactReference(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null) throw failure("MODEL_TASK_INVALID");
    if (!value.isNull()) artifactReferenceId(value);
  }

  private static void orderedArtifactReferences(JsonNode source, String name) {
    List<String> values = new ArrayList<>();
    for (JsonNode reference : array(source, name)) values.add(artifactReferenceId(reference));
    List<String> ordered = values.stream().sorted().toList();
    if (!values.equals(ordered) || ordered.size() != ordered.stream().distinct().count()) {
      throw failure("MODEL_TASK_INVALID");
    }
  }

  private static String artifactReferenceId(JsonNode reference) {
    if (!reference.isObject()) throw failure("MODEL_TASK_INVALID");
    Set<String> fields = new HashSet<>();
    reference.fieldNames().forEachRemaining(fields::add);
    if (!fields.equals(Set.of("artifactId", "sha256"))) throw failure("MODEL_TASK_INVALID");
    try {
      String artifactId = ArtifactId.parse(text(reference, "artifactId")).value();
      new Sha256Digest(text(reference, "sha256"));
      return artifactId;
    } catch (RuntimeException invalid) {
      throw failure("MODEL_TASK_INVALID");
    }
  }

  private static List<String> keys(List<JsonNode> items) {
    return items.stream().map(value -> text(value, "provisionalKey")).sorted().toList();
  }

  private static ArrayNode array(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (!(value instanceof ArrayNode array)) throw failure("MODEL_TASK_INVALID");
    return array;
  }

  private static String identifier(JsonNode source, String name) {
    try {
      return ArtifactId.parse(text(source, name)).value();
    } catch (RuntimeException invalid) {
      throw failure("MODEL_TASK_INVALID");
    }
  }

  private static List<String> identifierArray(JsonNode source, String name) {
    List<String> values = new ArrayList<>();
    for (JsonNode item : array(source, name)) values.add(ArtifactId.parse(text(item, "")).value());
    return values;
  }

  private static String text(JsonNode source, String name) {
    JsonNode value = name.isEmpty() ? source : source.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("MODEL_TASK_INVALID");
    }
    return value.textValue();
  }

  private static String contentId(String prefix, List<String> fields) {
    byte[][] frames = new byte[fields.size() + 1][];
    frames[0] = frame(prefix);
    for (int index = 0; index < fields.size(); index++)
      frames[index + 1] = frame(fields.get(index));
    return prefix + ":" + sha256(frames);
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
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

  private static FlowModelTaskException failure(String code) {
    return new FlowModelTaskException(code);
  }

  private record Registry(
      String registryId, List<String> readyFlowIds, Map<String, List<JsonNode>> itemsByFlow) {}
}
