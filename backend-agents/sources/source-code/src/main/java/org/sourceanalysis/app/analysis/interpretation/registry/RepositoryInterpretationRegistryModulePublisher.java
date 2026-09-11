package org.sourceanalysis.app.analysis.interpretation.registry;

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
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Receipt-last M3 publication of the one frozen repository interpretation registry. */
public final class RepositoryInterpretationRegistryModulePublisher {

  private static final String TASK_SET_FILE = "registry-proposal-task-set.json";
  private static final String TASK_SET_TYPE = "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_TASK_SET";
  private static final String TASK_SET_SCHEMA = "flow-interpretation-registry-proposal-task-set-v2";
  private static final String EXECUTION_FILE = "registry-proposal-execution-set.json";
  private static final String EXECUTION_TYPE =
      "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_EXECUTION_SET";
  private static final String EXECUTION_SCHEMA =
      "flow-interpretation-registry-proposal-execution-set-v3";
  private static final String FILE_NAME = "repository-interpretation-registry.json";
  private static final String ARTIFACT_TYPE =
      "FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY_MODULE";
  private static final String SCHEMA_VERSION =
      "flow-interpretation-repository-interpretation-registry-module-v1";
  private static final String ARTIFACT_PREFIX = "flow-interpretation-repository-registry";
  private static final String MODULE_VERSION = "v1";
  private static final Comparator<String> UTF8_ORDER =
      RepositoryInterpretationRegistryModulePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates M3's only artifact publisher. */
  public RepositoryInterpretationRegistryModulePublisher(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
  }

  /** Validates M1/M2/business-flow inputs from disk before atomically installing M3's registry. */
  public ModulePublicationReference publish(
      ModulePublicationReference taskSetPublication,
      ModulePublicationReference executionSetPublication,
      BusinessFlowsReference businessFlows,
      RepositoryInterpretationRegistry registry) {
    try {
      Objects.requireNonNull(taskSetPublication, "task set publication");
      Objects.requireNonNull(executionSetPublication, "execution set publication");
      Objects.requireNonNull(businessFlows, "business Flows");
      Objects.requireNonNull(registry, "repository interpretation registry");
      if (!businessFlows.publication().equals(registry.businessFlowsPublicationRef()))
        throw failure();
      ReopenedModulePublication taskSet = reopenTaskSet(taskSetPublication);
      ReopenedModulePublication executionSet = reopenExecutionSet(executionSetPublication);
      ReopenedAnalysisStepPublication flowStep = analysisSteps.reopen(businessFlows.publication());
      if (!taskSet.receipt().controls().equals(executionSet.receipt().controls())
          || !taskSet.receipt().controls().equals(flowStep.receipt().controls())) {
        throw failure();
      }
      verifyClosedInputs(taskSet, executionSet, registry);
      List<ArtifactReference> upstream = upstream(taskSet, executionSet, flowStep);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              businessFlows.publication().address().runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              3,
              "registry-freezer");
      CanonicalModulePayload payload =
          payload(address, upstream, taskSet.receipt().controls(), registry);
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstream,
                  taskSet.receipt().controls(),
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      return installed.reference();
    } catch (RegistryFreezeException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new RegistryFreezeException("REGISTRY_FREEZE_INCOMPLETE", exception);
    }
  }

  private ReopenedModulePublication reopenTaskSet(ModulePublicationReference reference) {
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    requireModule(
        publication,
        reference,
        1,
        "registry-task-compiler",
        TASK_SET_FILE,
        TASK_SET_TYPE,
        TASK_SET_SCHEMA);
    return publication;
  }

  private ReopenedModulePublication reopenExecutionSet(ModulePublicationReference reference) {
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    requireModule(
        publication,
        reference,
        2,
        "registry-proposal-runner",
        EXECUTION_FILE,
        EXECUTION_TYPE,
        EXECUTION_SCHEMA);
    return publication;
  }

  private static void requireModule(
      ReopenedModulePublication publication,
      ModulePublicationReference reference,
      int moduleNumber,
      String moduleKey,
      String fileName,
      String artifactType,
      String schemaVersion) {
    if (!reference.equals(publication.reference())
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != moduleNumber
        || !moduleKey.equals(address.moduleKey())
        || publication.payloads().size() != 1) {
      throw failure();
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!fileName.equals(payload.descriptor().fileName())
        || !artifactType.equals(payload.descriptor().artifactType())
        || !schemaVersion.equals(payload.descriptor().schemaVersion())) {
      throw failure();
    }
  }

  private void verifyClosedInputs(
      ReopenedModulePublication taskSetPublication,
      ReopenedModulePublication executionSetPublication,
      RepositoryInterpretationRegistry registry) {
    JsonNode taskSet = payload(taskSetPublication);
    JsonNode executionSet = payload(executionSetPublication);
    List<String> taskFlows = identifierArray(taskSet, "eligibleFlowSliceIds");
    List<String> taskIds = new ArrayList<>();
    Map<String, String> taskFlowById = new HashMap<>();
    for (JsonNode task : array(taskSet, "tasks")) {
      String taskId = identifier(task, "taskSpecId");
      String flowId = identifier(task, "flowSliceId");
      taskIds.add(taskId);
      if (taskFlowById.put(taskId, flowId) != null) throw failure();
    }
    taskIds.sort(UTF8_ORDER);
    if (!taskFlows.equals(taskFlowById.values().stream().sorted(UTF8_ORDER).toList()))
      throw failure();
    if (!identifier(executionSet, "taskSetId").equals(identifier(taskSet, "taskSetId")))
      throw failure();
    Map<String, JsonNode> executionDispositions = new HashMap<>();
    for (JsonNode disposition : array(executionSet, "flowDispositions")) {
      String flowId = identifier(disposition, "flowSliceId");
      if (executionDispositions.put(flowId, disposition) != null) throw failure();
    }
    if (!new HashSet<>(taskFlows).equals(executionDispositions.keySet())
        || !taskFlows.equals(registry.eligibleFlowSliceIds())) throw failure();
    Map<String, JsonNode> proposals = new HashMap<>();
    for (JsonNode proposal : array(executionSet, "validatedProposals")) {
      String proposalId = identifier(proposal, "registryProposalId");
      if (proposals.put(proposalId, proposal) != null) throw failure();
    }
    Map<String, RepositoryInterpretationRegistryFlowDisposition> registryDispositions =
        new HashMap<>();
    for (RepositoryInterpretationRegistryFlowDisposition disposition :
        registry.flowDispositions()) {
      if (registryDispositions.put(disposition.flowSliceId(), disposition) != null) throw failure();
      JsonNode execution = executionDispositions.get(disposition.flowSliceId());
      if (execution == null
          || !text(execution, "disposition").equals(disposition.disposition())
          || !identifier(execution, "taskSpecId").equals(disposition.taskSpecId())
          || !identifier(execution, "registryProposalRoundId")
              .equals(disposition.registryProposalRoundId())
          || !identifier(execution, "generationReceiptId").equals(disposition.generationReceiptId())
          || !identifierArray(execution, "registryProposalIds")
              .equals(disposition.registryProposalIds())
          || !identifierArray(execution, "gapIds").equals(disposition.gapIds())
          || !Objects.equals(nullableText(execution, "reasonCode"), disposition.reasonCode())) {
        throw failure();
      }
    }
    for (RepositoryInterpretationRegistryItem item : registry.items()) {
      JsonNode proposal = proposals.get(item.registryProposalId());
      if (proposal == null
          || !identifier(proposal, "taskSpecId")
              .equals(taskIdForFlow(taskFlowById, item.flowSliceId()))
          || !identifier(proposal, "flowSliceId").equals(item.flowSliceId())
          || !identifier(proposal, "evidenceCapsuleId").equals(item.evidenceCapsuleId())
          || !text(proposal, "proposalKind").equals(item.proposalKind())
          || !text(proposal, "normalizedLabel").equals(item.normalizedLabel())
          || !text(proposal, "normalizedPurpose").equals(item.normalizedPurpose())
          || !identifierArray(proposal, "basisAtomIds").equals(item.basisAtomIds())
          || !identifierArray(proposal, "basisGapIds").equals(item.basisGapIds())
          || !Objects.equals(nullableText(proposal, "sourceSeedKey"), item.sourceSeedKey())) {
        throw failure();
      }
    }
  }

  private static String taskIdForFlow(Map<String, String> taskFlowById, String flowId) {
    return taskFlowById.entrySet().stream()
        .filter(value -> value.getValue().equals(flowId))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(RepositoryInterpretationRegistryModulePublisher::failure);
  }

  private List<ArtifactReference> upstream(
      ReopenedModulePublication taskSet,
      ReopenedModulePublication executionSet,
      ReopenedAnalysisStepPublication flowStep) {
    List<ArtifactReference> result = new ArrayList<>();
    result.add(reference(taskSet.payloads().get(0)));
    result.add(reference(executionSet.payloads().get(0)));
    result.addAll(
        flowStep.semanticPayloads().stream()
            .map(RepositoryInterpretationRegistryModulePublisher::reference)
            .toList());
    result.sort(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER));
    if (result.size() != 7
        || result.size() != result.stream().map(ArtifactReference::artifactId).distinct().count()) {
      throw failure();
    }
    return List.copyOf(result);
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      RepositoryInterpretationRegistry registry) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("repositoryInterpretationRegistryId", registry.repositoryInterpretationRegistryId());
    body.putNull("organizationRegistrySeedRef");
    strings(body.putArray("eligibleFlowSliceIds"), registry.eligibleFlowSliceIds());
    ArrayNode items = body.putArray("items");
    registry.items().forEach(value -> item(items.addObject(), value));
    ArrayNode dispositions = body.putArray("flowDispositions");
    registry.flowDispositions().forEach(value -> disposition(dispositions.addObject(), value));
    accounting(body.putObject("proposalAccounting"), registry.proposalAccounting());
    body.put("closed", registry.closed());

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

  private static JsonNode payload(ReopenedModulePublication publication) {
    return new CanonicalJsonCodec()
        .parseCanonical(publication.payloads().get(0).canonicalUtf8())
        .path("payload");
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static void item(ObjectNode node, RepositoryInterpretationRegistryItem value) {
    node.put("provisionalKey", value.provisionalKey());
    node.put("registryProposalId", value.registryProposalId());
    node.put("flowSliceId", value.flowSliceId());
    node.put("evidenceCapsuleId", value.evidenceCapsuleId());
    node.put("proposalKind", value.proposalKind());
    node.put("normalizedLabel", value.normalizedLabel());
    node.put("normalizedPurpose", value.normalizedPurpose());
    strings(node.putArray("basisAtomIds"), value.basisAtomIds());
    strings(node.putArray("basisGapIds"), value.basisGapIds());
    if (value.sourceSeedKey() == null) node.putNull("sourceSeedKey");
    else node.put("sourceSeedKey", value.sourceSeedKey());
  }

  private static void disposition(
      ObjectNode node, RepositoryInterpretationRegistryFlowDisposition value) {
    node.put("registryFlowDispositionId", value.registryFlowDispositionId());
    node.put("flowSliceId", value.flowSliceId());
    node.put("disposition", value.disposition());
    node.put("taskSpecId", value.taskSpecId());
    node.put("registryProposalRoundId", value.registryProposalRoundId());
    node.put("generationReceiptId", value.generationReceiptId());
    strings(node.putArray("registryProposalIds"), value.registryProposalIds());
    strings(node.putArray("gapIds"), value.gapIds());
    if (value.reasonCode() == null) node.putNull("reasonCode");
    else node.put("reasonCode", value.reasonCode());
  }

  private static void accounting(ObjectNode node, RegistryProposalAccounting value) {
    strings(node.putArray("eligibleFlowSliceIds"), value.eligibleFlowSliceIds());
    strings(node.putArray("readyFlowSliceIds"), value.readyFlowSliceIds());
    strings(node.putArray("gapFlowSliceIds"), value.gapFlowSliceIds());
    strings(node.putArray("failedFlowSliceIds"), value.failedFlowSliceIds());
    strings(node.putArray("acceptedRegistryProposalIds"), value.acceptedRegistryProposalIds());
    strings(
        node.putArray("repositoryInterpretationRegistryItemIds"),
        value.repositoryInterpretationRegistryItemIds());
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

  private static ArrayNode array(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (!(value instanceof ArrayNode array)) throw failure();
    return array;
  }

  private static String identifier(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null || !value.isTextual()) throw failure();
    return ArtifactId.parse(value.textValue()).value();
  }

  private static List<String> identifierArray(JsonNode source, String name) {
    List<String> values = new ArrayList<>();
    for (JsonNode value : array(source, name)) {
      if (!value.isTextual()) throw failure();
      values.add(ArtifactId.parse(value.textValue()).value());
    }
    values.sort(UTF8_ORDER);
    if (values.size() != new HashSet<>(values).size()) throw failure();
    return List.copyOf(values);
  }

  private static String text(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) throw failure();
    return value.textValue();
  }

  private static String nullableText(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual() || value.textValue().isBlank()) throw failure();
    return value.textValue();
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.stream().sorted(UTF8_ORDER).forEach(node::add);
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

  private static RegistryFreezeException failure() {
    return new RegistryFreezeException("REGISTRY_FREEZE_INCOMPLETE");
  }
}
