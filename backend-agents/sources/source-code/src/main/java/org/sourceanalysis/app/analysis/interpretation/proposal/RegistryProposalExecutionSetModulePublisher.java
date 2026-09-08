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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Installs M2's already-validated R0 results without replaying any Provider call. */
public final class RegistryProposalExecutionSetModulePublisher {

  private static final String TASK_SET_FILE_NAME = "registry-proposal-task-set.json";
  private static final String TASK_SET_TYPE = "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_TASK_SET";
  private static final String TASK_SET_SCHEMA = "flow-interpretation-registry-proposal-task-set-v2";
  private static final String FILE_NAME = "registry-proposal-execution-set.json";
  private static final String ARTIFACT_TYPE = "FLOW_INTERPRETATION_REGISTRY_PROPOSAL_EXECUTION_SET";
  private static final String SCHEMA_VERSION =
      "flow-interpretation-registry-proposal-execution-set-v3";
  private static final String ARTIFACT_PREFIX = "flow-interpretation-r0-execution-set";
  private static final String MODULE_VERSION = "v1";
  private static final Comparator<String> UTF8_ORDER =
      RegistryProposalExecutionSetModulePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates M2's receipt-last writer. It deliberately has no Provider dependency. */
  public RegistryProposalExecutionSetModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /**
   * Fresh-reopens M1, verifies every R0 result against its denominator, and installs one payload.
   */
  public ModulePublicationReference publish(
      ModulePublicationReference taskSetPublication, RegistryProposalExecutionSet executionSet) {
    try {
      Objects.requireNonNull(taskSetPublication, "registry proposal task set publication");
      Objects.requireNonNull(executionSet, "registry proposal execution set");
      if (!taskSetPublication.equals(executionSet.taskSetPublicationRef())) throw failure();
      TaskSetView taskSet = reopenTaskSet(taskSetPublication);
      verifyExecutionSet(taskSet, executionSet);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              taskSetPublication.address().runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              2,
              "registry-proposal-runner");
      ArtifactReference upstream =
          new ArtifactReference(
              taskSet.payload().descriptor().artifactId(), taskSet.payload().descriptor().sha256());
      CanonicalModulePayload payload = payload(address, upstream, taskSet, executionSet);
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  List.of(upstream),
                  taskSet.controls(),
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      return installed.reference();
    } catch (RegistryProposalTaskCompilationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new RegistryProposalTaskCompilationException(
          "REGISTRY_PROPOSAL_EXECUTION_SET_INVALID", exception);
    }
  }

  private TaskSetView reopenTaskSet(ModulePublicationReference reference) {
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    if (!reference.equals(publication.reference())
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 1
        || !"registry-task-compiler".equals(address.moduleKey())
        || publication.payloads().size() != 1) {
      throw failure();
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!TASK_SET_FILE_NAME.equals(payload.descriptor().fileName())
        || !TASK_SET_TYPE.equals(payload.descriptor().artifactType())
        || !TASK_SET_SCHEMA.equals(payload.descriptor().schemaVersion())) {
      throw failure();
    }
    JsonNode root = canonicalJson.parseCanonical(payload.canonicalUtf8());
    JsonNode body = object(root, "payload");
    String taskSetId = identifier(body, "taskSetId");
    List<TaskDenominatorItem> tasks = new ArrayList<>();
    for (JsonNode task : array(body, "tasks")) {
      tasks.add(
          new TaskDenominatorItem(identifier(task, "taskSpecId"), identifier(task, "flowSliceId")));
    }
    tasks.sort(Comparator.comparing(TaskDenominatorItem::flowSliceId, UTF8_ORDER));
    List<String> eligibleFlowIds = identifierArray(body, "eligibleFlowSliceIds");
    if (tasks.size() != integer(body, "flowCount")
        || tasks.size() != eligibleFlowIds.size()
        || !eligibleFlowIds.equals(tasks.stream().map(TaskDenominatorItem::flowSliceId).toList())
        || tasks.size() != tasks.stream().map(TaskDenominatorItem::taskSpecId).distinct().count()) {
      throw failure();
    }
    ArrayNode shards = array(body, "taskShardReceipts");
    if (!closedShardPartition(shards, tasks)) throw failure();
    return new TaskSetView(
        taskSetId,
        List.copyOf(tasks),
        shards.deepCopy(),
        publication.receipt().controls(),
        payload);
  }

  private void verifyExecutionSet(TaskSetView taskSet, RegistryProposalExecutionSet executionSet) {
    Map<String, TaskDenominatorItem> taskById = new HashMap<>();
    Map<String, String> taskIdByFlowId = new HashMap<>();
    for (TaskDenominatorItem task : taskSet.tasks()) {
      taskById.put(task.taskSpecId(), task);
      taskIdByFlowId.put(task.flowSliceId(), task.taskSpecId());
    }
    if (executionSet.rounds().size() != taskSet.tasks().size()
        || executionSet.generationReceipts().size() != taskSet.tasks().size()
        || executionSet.flowDispositions().size() != taskSet.tasks().size()
        || !exactTaskIds(
            taskById.keySet(),
            executionSet.rounds().stream().map(RegistryProposalRound::taskSpecId).toList())
        || !exactTaskIds(
            taskById.keySet(),
            executionSet.generationReceipts().stream()
                .map(RegistryProposalGenerationReceipt::taskSpecId)
                .toList())) {
      throw failure();
    }
    Map<String, RegistryProposalRound> roundByTask = uniqueByTask(executionSet.rounds());
    Map<String, RegistryProposalGenerationReceipt> receiptByTask =
        uniqueReceiptsByTask(executionSet.generationReceipts());
    Map<String, List<BusinessRegistryProposal>> proposalsByTask = new HashMap<>();
    for (BusinessRegistryProposal proposal : executionSet.validatedProposals()) {
      TaskDenominatorItem task = taskById.get(proposal.taskSpecId());
      if (task == null
          || !task.flowSliceId().equals(proposal.flowSliceId())
          || proposal.registryProposalId().isBlank()) {
        throw failure();
      }
      proposalsByTask
          .computeIfAbsent(proposal.taskSpecId(), ignored -> new ArrayList<>())
          .add(proposal);
    }
    Set<String> dispositionFlows = new HashSet<>();
    Set<String> dispositionIds = new HashSet<>();
    for (RegistryProposalFlowDisposition disposition : executionSet.flowDispositions()) {
      String expectedTaskId = taskIdByFlowId.get(disposition.flowSliceId());
      RegistryProposalRound round = roundByTask.get(disposition.taskSpecId());
      RegistryProposalGenerationReceipt receipt = receiptByTask.get(disposition.taskSpecId());
      if (expectedTaskId == null
          || !expectedTaskId.equals(disposition.taskSpecId())
          || round == null
          || receipt == null
          || !round.registryProposalRoundId().equals(disposition.registryProposalRoundId())
          || !receipt.generationReceiptId().equals(disposition.generationReceiptId())
          || !dispositionFlows.add(disposition.flowSliceId())
          || !dispositionIds.add(disposition.registryProposalDispositionId())) {
        throw failure();
      }
      List<String> actualProposalIds =
          proposalsByTask.getOrDefault(disposition.taskSpecId(), List.of()).stream()
              .map(BusinessRegistryProposal::registryProposalId)
              .sorted(UTF8_ORDER)
              .toList();
      List<String> recordedProposalIds =
          disposition.registryProposalIds().stream().sorted(UTF8_ORDER).toList();
      if (!actualProposalIds.equals(recordedProposalIds)) throw failure();
    }
    if (!dispositionFlows.equals(taskIdByFlowId.keySet())) throw failure();
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      ArtifactReference upstream,
      TaskSetView taskSet,
      RegistryProposalExecutionSet executionSet) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("executionSetId", executionSet.executionSetId());
    body.put("taskSetId", taskSet.taskSetId());
    ArrayNode rounds = body.putArray("rounds");
    executionSet.rounds().stream()
        .sorted(Comparator.comparing(RegistryProposalRound::taskSpecId, UTF8_ORDER))
        .forEach(value -> round(rounds.addObject(), value));
    ArrayNode receipts = body.putArray("receipts");
    executionSet.generationReceipts().stream()
        .sorted(Comparator.comparing(RegistryProposalGenerationReceipt::taskSpecId, UTF8_ORDER))
        .forEach(value -> receipt(receipts.addObject(), value));
    ArrayNode proposals = body.putArray("validatedProposals");
    executionSet.validatedProposals().stream()
        .sorted(Comparator.comparing(BusinessRegistryProposal::registryProposalId, UTF8_ORDER))
        .forEach(value -> proposal(proposals.addObject(), value));
    ArrayNode dispositions = body.putArray("flowDispositions");
    executionSet.flowDispositions().stream()
        .sorted(Comparator.comparing(RegistryProposalFlowDisposition::flowSliceId, UTF8_ORDER))
        .forEach(value -> disposition(dispositions.addObject(), value));
    body.set("shardReceipts", taskSet.shardReceipts());
    body.put("callCount", executionSet.rounds().size());

    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(List.of(upstream)));
    withoutArtifactId.set("controls", controls(taskSet.controls()));
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

  private static void round(ObjectNode node, RegistryProposalRound value) {
    node.put("registryProposalRoundId", value.registryProposalRoundId());
    node.put("taskSpecId", value.taskSpecId());
    node.put("canonicalResponseSha256", value.canonicalResponseSha256().value());
  }

  private static void receipt(ObjectNode node, RegistryProposalGenerationReceipt value) {
    node.put("generationReceiptId", value.generationReceiptId());
    node.put("taskSpecId", value.taskSpecId());
    reference(node.putObject("expectedRuntime"), value.expectedRuntime());
    reference(node.putObject("observedRuntime"), value.observedRuntime());
    node.put("canonicalRequestSha256", value.canonicalRequestSha256().value());
    node.put("canonicalResponseSha256", value.canonicalResponseSha256().value());
  }

  private static void proposal(ObjectNode node, BusinessRegistryProposal value) {
    node.put("registryProposalId", value.registryProposalId());
    node.put("taskSpecId", value.taskSpecId());
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

  private static void disposition(ObjectNode node, RegistryProposalFlowDisposition value) {
    node.put("registryProposalDispositionId", value.registryProposalDispositionId());
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

  private static boolean closedShardPartition(ArrayNode shards, List<TaskDenominatorItem> tasks) {
    List<String> flowIds = new ArrayList<>();
    List<String> taskIds = new ArrayList<>();
    Set<String> shardIds = new HashSet<>();
    for (JsonNode shard : shards) {
      if (!shardIds.add(identifier(shard, "shardId"))) return false;
      flowIds.addAll(identifierArray(shard, "denominatorFlowSliceIds"));
      taskIds.addAll(identifierArray(shard, "outputTaskSpecIds"));
    }
    flowIds.sort(UTF8_ORDER);
    taskIds.sort(UTF8_ORDER);
    return flowIds.equals(tasks.stream().map(TaskDenominatorItem::flowSliceId).toList())
        && taskIds.equals(
            tasks.stream().map(TaskDenominatorItem::taskSpecId).sorted(UTF8_ORDER).toList());
  }

  private static boolean exactTaskIds(Set<String> expected, List<String> actual) {
    return actual.size() == expected.size() && expected.equals(new HashSet<>(actual));
  }

  private static Map<String, RegistryProposalRound> uniqueByTask(
      List<RegistryProposalRound> values) {
    Map<String, RegistryProposalRound> result = new HashMap<>();
    for (RegistryProposalRound value : values) {
      if (result.put(value.taskSpecId(), value) != null) throw failure();
    }
    return result;
  }

  private static Map<String, RegistryProposalGenerationReceipt> uniqueReceiptsByTask(
      List<RegistryProposalGenerationReceipt> values) {
    Map<String, RegistryProposalGenerationReceipt> result = new HashMap<>();
    for (RegistryProposalGenerationReceipt value : values) {
      if (result.put(value.taskSpecId(), value) != null) throw failure();
    }
    return result;
  }

  private static JsonNode object(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null || !value.isObject()) throw failure();
    return value;
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
    List<String> result = new ArrayList<>();
    for (JsonNode item : array(source, name)) {
      if (!item.isTextual()) throw failure();
      result.add(ArtifactId.parse(item.textValue()).value());
    }
    result.sort(UTF8_ORDER);
    if (result.size() != new HashSet<>(result).size()) throw failure();
    return List.copyOf(result);
  }

  private static int integer(JsonNode source, String name) {
    JsonNode value = source.get(name);
    if (value == null || !value.canConvertToInt() || value.intValue() < 0) throw failure();
    return value.intValue();
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

  private static RegistryProposalTaskCompilationException failure() {
    return new RegistryProposalTaskCompilationException("REGISTRY_PROPOSAL_EXECUTION_SET_INVALID");
  }

  private record TaskDenominatorItem(String taskSpecId, String flowSliceId) {}

  private record TaskSetView(
      String taskSetId,
      List<TaskDenominatorItem> tasks,
      ArrayNode shardReceipts,
      ArtifactControls controls,
      VerifiedCanonicalPayload payload) {}
}
