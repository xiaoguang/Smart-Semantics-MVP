package org.sourceanalysis.app.analysis.interpretation.process;

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
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * Receipt-last M8 checkpoint for one complete, finite M7 shard denominator.
 *
 * <p>Each model-safe shard is still interpreted in isolation. The publisher only owns their
 * canonical sequencing and aggregate accounting; it never combines packets or expands the P1/P2
 * response language.
 */
public final class BusinessProcessInterpretationExecutionPublisher {

  private static final String FILE_NAME = "process-interpretation-checkpoint.json";
  private static final String ARTIFACT_TYPE =
      "FLOW_INTERPRETATION_PROCESS_INTERPRETATION_CHECKPOINT_SET";
  private static final String SCHEMA_VERSION =
      "flow-interpretation-process-interpretation-checkpoint-set-v1";
  private static final String ARTIFACT_PREFIX = "process-interpretation-checkpoint";
  private static final Comparator<String> UTF8_ORDER =
      (left, right) -> {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int commonLength = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < commonLength; index++) {
          int comparison = Integer.compare(leftBytes[index] & 0xff, rightBytes[index] & 0xff);
          if (comparison != 0) {
            return comparison;
          }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
      };

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final BusinessProcessInterpretationRunner runner;
  private final BusinessProcessInterpretationModulePublisher checkpointProjector;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the receipt-last aggregate publisher over fresh-reopened M7 material. */
  public BusinessProcessInterpretationExecutionPublisher(
      CanonicalModuleArtifactStore moduleArtifacts, ProcessInputArtifactReader inputArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    ProcessInputArtifactReader reader =
        Objects.requireNonNull(inputArtifacts, "process input reader");
    this.runner = new BusinessProcessInterpretationRunner(moduleArtifacts, reader);
    this.checkpointProjector =
        new BusinessProcessInterpretationModulePublisher(moduleArtifacts, reader);
  }

  /** Executes every preflight-valid M7 shard and persists one closed aggregate checkpoint. */
  public ModulePublicationReference runAndPublish(
      BusinessProcessInterpretationExecutionRequest request, ProcessModelProvider provider) {
    try {
      Objects.requireNonNull(request, "process interpretation execution request");
      Objects.requireNonNull(provider, "process model provider");
      ReopenedModulePublication m7 = reopenM7(request.businessProcessTaskPublication());
      JsonNode m7Body = payloadBody(m7.payloads().get(0));
      M7Preflight preflight = preflight(m7, m7Body, request);

      AnalysisStepModuleAddress m7Address = analysisAddress(m7);
      AnalysisStepModuleAddress m8Address =
          new AnalysisStepModuleAddress(
              m7Address.runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              8,
              "business-process-interpretation-runner");
      ArtifactControls controls = m7.receipt().controls();
      ArtifactReference m7Payload = reference(m7.payloads().get(0));
      List<ObjectNode> terminals = new ArrayList<>();
      for (M7Shard shard : preflight.shards()) {
        if ("NO_MODEL".equals(shard.shardModelDisposition())) {
          terminals.add(noModelTerminal(shard, preflight.processGapsById()));
          continue;
        }
        BusinessProcessInterpretationRequest safeRequest =
            new BusinessProcessInterpretationRequest(
                request.businessProcessTaskPublication(),
                shard.taskShardId(),
                request.expectedRuntime());
        BusinessProcessInterpretationRunner.CheckpointTerminal terminal =
            runner.runCheckpointTerminal(safeRequest, provider);
        terminals.add(
            modelSafeTerminal(
                m8Address, List.of(m7Payload), controls, safeRequest, terminal, shard));
      }
      Aggregate aggregate = aggregate(preflight, terminals);
      CanonicalModulePayload payload =
          payload(m8Address, List.of(m7Payload), controls, request, terminals, aggregate);
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  m8Address,
                  "v1",
                  List.of(m7Payload),
                  controls,
                  aggregate.completionStatus(),
                  aggregate.gapRefs(),
                  List.of(payload)));
      verifyInstalled(installed.reference(), m8Address, request, preflight, aggregate);
      return installed.reference();
    } catch (BusinessProcessInterpretationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID", failure);
    }
  }

  private ReopenedModulePublication reopenM7(ModulePublicationReference reference) {
    if (reference == null) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    if (!reference.equals(publication.reference())
        || analysisAddress(publication).analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || analysisAddress(publication).moduleNumber() != 7
        || !"business-process-task-compiler".equals(analysisAddress(publication).moduleKey())
        || publication.payloads().size() != 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!"process-task-shards.json".equals(payload.descriptor().fileName())
        || !"FLOW_INTERPRETATION_PROCESS_TASK_SHARDS".equals(payload.descriptor().artifactType())
        || !"flow-interpretation-process-task-shards-v1"
            .equals(payload.descriptor().schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return publication;
  }

  private M7Preflight preflight(
      ReopenedModulePublication m7,
      JsonNode body,
      BusinessProcessInterpretationExecutionRequest request) {
    if (!body.path("closed").asBoolean(false) || body.path("providerCallCount").asInt(-1) != 0) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    ArrayNode rawShards = requiredArray(body, "taskShards");
    Map<String, JsonNode> processGapsById = indexProcessGaps(requiredArray(body, "processGaps"));
    List<M7Shard> shards = new ArrayList<>();
    Set<String> shardIds = new HashSet<>();
    Set<GroupLocalShardOrdinal> groupLocalOrdinals = new HashSet<>();
    Set<String> ownerIds = new HashSet<>();
    for (JsonNode rawShard : rawShards) {
      String shardId = requiredText(rawShard, "taskShardId");
      if (!shardIds.add(shardId) || !rawShard.path("shardOrdinal").canConvertToInt()) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      int ordinal = rawShard.path("shardOrdinal").asInt(-1);
      String groupId = requiredText(rawShard, "processEvidenceGroupId");
      if (ordinal < 0 || !groupLocalOrdinals.add(new GroupLocalShardOrdinal(groupId, ordinal))) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      String disposition = requiredText(rawShard, "shardModelDisposition");
      List<String> owners = requiredIdentifiers(rawShard, "ownerCandidateRelationIds", false);
      for (String owner : owners) {
        if (!ownerIds.add(owner)) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
      }
      List<String> gaps = requiredIdentifiers(rawShard, "modelIneligibilityGapIds", false);
      JsonNode packet = rawShard.get("processModelPacket");
      ArrayNode bindings = requiredArray(rawShard, "readerKeyBindings");
      if ("MODEL_SAFE".equals(disposition)) {
        if (packet == null || packet.isNull() || !gaps.isEmpty() || bindings.isEmpty()) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
      } else if ("NO_MODEL".equals(disposition)) {
        if ((packet != null && !packet.isNull()) || !bindings.isEmpty() || gaps.isEmpty()) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
        if (!gaps.stream().allMatch(processGapsById::containsKey)) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
      } else {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      shards.add(new M7Shard(shardId, groupId, ordinal, disposition, owners, gaps, rawShard));
    }
    shards.sort(
        Comparator.comparing(M7Shard::processEvidenceGroupId, UTF8_ORDER)
            .thenComparingInt(M7Shard::shardOrdinal)
            .thenComparing(M7Shard::taskShardId, UTF8_ORDER));
    List<M7Shard> safe =
        shards.stream()
            .filter(shard -> "MODEL_SAFE".equals(shard.shardModelDisposition()))
            .toList();
    List<M7Shard> noModel =
        shards.stream().filter(shard -> "NO_MODEL".equals(shard.shardModelDisposition())).toList();
    verifyCandidateRelationDenominator(m7, body, ownerIds);
    runner.preflightRuntime(request.businessProcessTaskPublication(), request.expectedRuntime());
    for (M7Shard shard : safe) {
      runner.preflightModelSafeShard(
          new BusinessProcessInterpretationRequest(
              request.businessProcessTaskPublication(),
              shard.taskShardId(),
              request.expectedRuntime()));
    }
    return new M7Preflight(
        List.copyOf(shards), List.copyOf(safe), List.copyOf(noModel), processGapsById);
  }

  private ObjectNode modelSafeTerminal(
      AnalysisStepModuleAddress m8Address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      BusinessProcessInterpretationRequest request,
      BusinessProcessInterpretationRunner.CheckpointTerminal terminal,
      M7Shard shard) {
    CanonicalModulePayload checkpoint;
    if (terminal instanceof BusinessProcessInterpretationRunner.P1SuccessTerminal success) {
      checkpoint =
          checkpointProjector.successPayload(m8Address, upstream, controls, request, success);
    } else if (terminal instanceof BusinessProcessInterpretationRunner.P1GapTerminal gap) {
      checkpoint = checkpointProjector.gapPayload(m8Address, upstream, controls, request, gap);
    } else {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    JsonNode source = canonicalJson.parseCanonical(checkpoint.canonicalUtf8()).path("payload");
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("taskShardId", shard.taskShardId());
    result.put("processEvidenceGroupId", shard.processEvidenceGroupId());
    result.put("shardOrdinal", shard.shardOrdinal());
    result.put("shardModelDisposition", shard.shardModelDisposition());
    strings(result.putArray("ownerCandidateRelationIds"), shard.ownerCandidateRelationIds());
    strings(result.putArray("modelIneligibilityGapIds"), shard.modelIneligibilityGapIds());
    copy(source, result, "processModelTasks");
    copy(source, result, "processModelRounds");
    copy(source, result, "generationReceipts");
    copy(source, result, "taskDispositions");
    copy(source, result, "businessProcessHypotheses");
    copy(source, result, "processInterpretationDisposition");
    result.put("providerCallCount", source.path("providerCallCount").asInt(-1));
    result.put("closed", true);
    return result;
  }

  private ObjectNode noModelTerminal(M7Shard shard, Map<String, JsonNode> processGapsById) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("taskShardId", shard.taskShardId());
    result.put("processEvidenceGroupId", shard.processEvidenceGroupId());
    result.put("shardOrdinal", shard.shardOrdinal());
    result.put("shardModelDisposition", "NO_MODEL");
    strings(result.putArray("ownerCandidateRelationIds"), shard.ownerCandidateRelationIds());
    strings(result.putArray("modelIneligibilityGapIds"), shard.modelIneligibilityGapIds());
    result.putArray("processModelTasks");
    result.putArray("processModelRounds");
    result.putArray("generationReceipts");
    result.putArray("taskDispositions");
    result.putArray("businessProcessHypotheses");
    ObjectNode disposition = result.putObject("processInterpretationDisposition");
    disposition.put("executionKind", "NO_MODEL");
    disposition.putNull("p1TaskId");
    disposition.putNull("p2TaskId");
    disposition.put("disposition", "NO_MODEL_ADMISSION_PENDING");
    disposition.putArray("proposedBusinessProcessHypothesisIds");
    disposition.putArray("retainedBusinessProcessHypothesisIds");
    disposition.putArray("narrowedBusinessProcessHypothesisIds");
    disposition.putArray("droppedBusinessProcessHypothesisIds");
    disposition.putArray("pendingBusinessProcessHypothesisIds");
    disposition.putArray("p2GapBusinessProcessHypothesisIds");
    disposition.putArray("p2FailedBusinessProcessHypothesisIds");
    ArrayNode gaps = disposition.putArray("processGaps");
    for (String gapId : shard.modelIneligibilityGapIds()) {
      JsonNode processGap = processGapsById.get(gapId);
      if (processGap != null) {
        gaps.add(processGap.deepCopy());
      }
    }
    strings(disposition.putArray("gapIds"), shard.modelIneligibilityGapIds());
    disposition.putNull("failureRef");
    disposition.put("reasonCode", "PROCESS_MODEL_INELIGIBLE");
    result.put("providerCallCount", 0);
    result.put("closed", true);
    return result;
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      BusinessProcessInterpretationExecutionRequest request,
      List<ObjectNode> terminals,
      Aggregate aggregate) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("schemaVersion", SCHEMA_VERSION);
    body.put("artifactType", ARTIFACT_TYPE);
    moduleReference(
        body.putObject("businessProcessTaskPublicationRef"),
        request.businessProcessTaskPublication());
    ArrayNode shardResults = body.putArray("shardResults");
    terminals.forEach(terminal -> shardResults.add(terminal));
    body.set("accounting", accounting(aggregate));
    body.put("executionOutcome", aggregate.executionOutcome());
    body.put("providerCallCount", aggregate.providerCallCount());
    body.put("closed", true);

    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set(
        "completion", completion(aggregate.completionStatus(), aggregate.gapRefs()));
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

  private ObjectNode accounting(Aggregate aggregate) {
    ObjectNode accounting = JsonNodeFactory.instance.objectNode();
    strings(accounting.putArray("taskShardIds"), aggregate.taskShardIds());
    strings(
        accounting.putArray("ownerCandidateRelationIds"), aggregate.ownerCandidateRelationIds());
    accounting.put("taskShardCount", aggregate.taskShardCount());
    accounting.put("modelSafeShardCount", aggregate.modelSafeShardCount());
    accounting.put("noModelShardCount", aggregate.noModelShardCount());
    accounting.put("plannedProcessModelTaskCount", aggregate.plannedProcessModelTaskCount());
    accounting.put("actualProcessModelRoundCount", aggregate.actualProcessModelRoundCount());
    accounting.put("generationReceiptCount", aggregate.generationReceiptCount());
    accounting.put("taskDispositionCount", aggregate.taskDispositionCount());
    accounting.put("businessProcessHypothesisCount", aggregate.businessProcessHypothesisCount());
    accounting.put(
        "processInterpretationDispositionCount", aggregate.processInterpretationDispositionCount());
    accounting.put("providerCallCount", aggregate.providerCallCount());
    accounting.put("readyForAdmissionCount", aggregate.readyForAdmissionCount());
    accounting.put("gapDispositionCount", aggregate.gapDispositionCount());
    accounting.put("noModelAdmissionPendingCount", aggregate.noModelAdmissionPendingCount());
    accounting.put("failedDispositionCount", aggregate.failedDispositionCount());
    return accounting;
  }

  private void verifyInstalled(
      ModulePublicationReference reference,
      AnalysisStepModuleAddress expectedAddress,
      BusinessProcessInterpretationExecutionRequest request,
      M7Preflight expected,
      Aggregate aggregate) {
    ReopenedModulePublication reopened = moduleArtifacts.reopen(reference);
    if (!reference.equals(reopened.reference())
        || !expectedAddress.equals(reopened.receipt().address())
        || reopened.receipt().status() != aggregate.completionStatus()
        || !sortedUnique(reopened.receipt().gapRefs()).equals(aggregate.gapRefs())
        || reopened.payloads().size() != 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload payload = reopened.payloads().get(0);
    if (!FILE_NAME.equals(payload.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    JsonNode body = payloadBody(payload);
    if (!sameModuleReference(
            body.path("businessProcessTaskPublicationRef"),
            request.businessProcessTaskPublication())
        || !body.path("closed").asBoolean(false)
        || body.path("providerCallCount").asInt(-1) != aggregate.providerCallCount()
        || !aggregate.executionOutcome().equals(body.path("executionOutcome").asText())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    Map<String, JsonNode> results = new HashMap<>();
    Set<String> ownerIds = new HashSet<>();
    for (JsonNode result : requiredArray(body, "shardResults")) {
      String shardId = requiredText(result, "taskShardId");
      if (results.put(shardId, result) != null) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      for (String owner : requiredIdentifiers(result, "ownerCandidateRelationIds", false)) {
        if (!ownerIds.add(owner)) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
      }
    }
    if (results.size() != expected.shards().size()
        || ownerIds.size()
            != expected.shards().stream()
                .flatMap(shard -> shard.ownerCandidateRelationIds().stream())
                .count()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    for (M7Shard shard : expected.shards()) {
      JsonNode result = results.get(shard.taskShardId());
      if (result == null
          || !shard.processEvidenceGroupId().equals(requiredText(result, "processEvidenceGroupId"))
          || result.path("shardOrdinal").asInt(-1) != shard.shardOrdinal()
          || !shard.shardModelDisposition().equals(result.path("shardModelDisposition").asText())
          || !requiredIdentifiers(result, "ownerCandidateRelationIds", false)
              .equals(sortedUnique(shard.ownerCandidateRelationIds()))
          || !requiredIdentifiers(result, "modelIneligibilityGapIds", false)
              .equals(sortedUnique(shard.modelIneligibilityGapIds()))
          || !result.path("closed").asBoolean(false)) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      if ("MODEL_SAFE".equals(shard.shardModelDisposition())) {
        verifyModelSafeResult(result, expected.processGapsById());
      } else if (result.path("processModelTasks").size() != 0
          || result.path("processModelRounds").size() != 0
          || result.path("generationReceipts").size() != 0
          || result.path("taskDispositions").size() != 0
          || result.path("businessProcessHypotheses").size() != 0
          || result.path("providerCallCount").asInt(-1) != 0
          || !"NO_MODEL"
              .equals(result.at("/processInterpretationDisposition/executionKind").asText())
          || !"NO_MODEL_ADMISSION_PENDING"
              .equals(result.at("/processInterpretationDisposition/disposition").asText())
          || !requiredIdentifiers(result.at("/processInterpretationDisposition"), "gapIds", false)
              .equals(sortedUnique(shard.modelIneligibilityGapIds()))) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
    JsonNode accounting = requiredObject(body, "accounting");
    if (accounting.path("taskShardCount").asInt(-1) != aggregate.taskShardCount()
        || accounting.path("modelSafeShardCount").asInt(-1) != aggregate.modelSafeShardCount()
        || accounting.path("noModelShardCount").asInt(-1) != aggregate.noModelShardCount()
        || accounting.path("plannedProcessModelTaskCount").asInt(-1)
            != aggregate.plannedProcessModelTaskCount()
        || accounting.path("actualProcessModelRoundCount").asInt(-1)
            != aggregate.actualProcessModelRoundCount()
        || accounting.path("generationReceiptCount").asInt(-1) != aggregate.generationReceiptCount()
        || accounting.path("taskDispositionCount").asInt(-1) != aggregate.taskDispositionCount()
        || accounting.path("businessProcessHypothesisCount").asInt(-1)
            != aggregate.businessProcessHypothesisCount()
        || accounting.path("processInterpretationDispositionCount").asInt(-1)
            != aggregate.processInterpretationDispositionCount()
        || accounting.path("providerCallCount").asInt(-1) != aggregate.providerCallCount()
        || accounting.path("readyForAdmissionCount").asInt(-1) != aggregate.readyForAdmissionCount()
        || accounting.path("gapDispositionCount").asInt(-1) != aggregate.gapDispositionCount()
        || accounting.path("noModelAdmissionPendingCount").asInt(-1)
            != aggregate.noModelAdmissionPendingCount()
        || accounting.path("failedDispositionCount").asInt(-1)
            != aggregate.failedDispositionCount()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
  }

  private Aggregate aggregate(M7Preflight preflight, List<ObjectNode> terminals) {
    List<ObjectNode> canonicalTerminals =
        terminals.stream()
            .sorted(
                Comparator.<ObjectNode, String>comparing(
                        value -> requiredText(value, "processEvidenceGroupId"), UTF8_ORDER)
                    .thenComparingInt(value -> value.path("shardOrdinal").asInt())
                    .thenComparing(value -> value.path("taskShardId").asText(), UTF8_ORDER))
            .toList();
    if (!canonicalTerminals.equals(terminals) || terminals.size() != preflight.shards().size()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    int ready = 0;
    int gaps = 0;
    int noModel = 0;
    List<String> gapRefs = new ArrayList<>();
    for (int index = 0; index < terminals.size(); index++) {
      ObjectNode terminal = terminals.get(index);
      M7Shard shard = preflight.shards().get(index);
      if (!shard.taskShardId().equals(requiredText(terminal, "taskShardId"))
          || !shard
              .processEvidenceGroupId()
              .equals(requiredText(terminal, "processEvidenceGroupId"))
          || shard.shardOrdinal() != terminal.path("shardOrdinal").asInt(-1)
          || !shard
              .shardModelDisposition()
              .equals(requiredText(terminal, "shardModelDisposition"))) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      String disposition =
          requiredText(requiredObject(terminal, "processInterpretationDisposition"), "disposition");
      if ("MODEL_SAFE".equals(shard.shardModelDisposition())) {
        if ("READY_FOR_ADMISSION".equals(disposition)) {
          ready++;
        } else if ("GAP".equals(disposition)) {
          gaps++;
          List<String> terminalGaps =
              requiredIdentifiers(
                  requiredObject(terminal, "processInterpretationDisposition"), "gapIds", true);
          if (!terminalGaps.stream().allMatch(preflight.processGapsById()::containsKey)) {
            throw failure("PROCESS_MODEL_REFERENCE_INVALID");
          }
          gapRefs.addAll(terminalGaps);
        } else {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
      } else if ("NO_MODEL".equals(shard.shardModelDisposition())) {
        if (!"NO_MODEL_ADMISSION_PENDING".equals(disposition)) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
        noModel++;
        gapRefs.addAll(shard.modelIneligibilityGapIds());
      } else {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
    int safe = preflight.modelSafeShards().size();
    if (safe != ready + gaps || noModel != preflight.noModelShards().size()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    int providerCalls = safe + ready;
    return new Aggregate(
        preflight.shards().stream().map(M7Shard::taskShardId).toList(),
        preflight.shards().stream()
            .flatMap(shard -> shard.ownerCandidateRelationIds().stream())
            .toList(),
        preflight.shards().size(),
        safe,
        noModel,
        2 * safe,
        safe + ready,
        safe + ready,
        2 * safe,
        ready,
        preflight.shards().size(),
        providerCalls,
        ready,
        gaps,
        noModel,
        0,
        gaps == 0 && noModel == 0 ? "ALL_READY_FOR_ADMISSION" : "CLOSED_WITH_GAPS",
        gaps == 0 && noModel == 0
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS,
        sortedUnique(gapRefs));
  }

  private void verifyModelSafeResult(JsonNode result, Map<String, JsonNode> processGapsById) {
    String disposition =
        requiredText(requiredObject(result, "processInterpretationDisposition"), "disposition");
    if ("READY_FOR_ADMISSION".equals(disposition)) {
      if (result.path("processModelTasks").size() != 2
          || result.path("processModelRounds").size() != 2
          || result.path("generationReceipts").size() != 2
          || result.path("taskDispositions").size() != 2
          || result.path("businessProcessHypotheses").size() != 1
          || result.path("providerCallCount").asInt(-1) != 2
          || !requiredIdentifiers(
                  requiredObject(result, "processInterpretationDisposition"), "gapIds", false)
              .isEmpty()) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      return;
    }
    if ("GAP".equals(disposition)) {
      List<String> gapIds =
          requiredIdentifiers(
              requiredObject(result, "processInterpretationDisposition"), "gapIds", true);
      if (result.path("processModelTasks").size() != 2
          || result.path("processModelRounds").size() != 1
          || result.path("generationReceipts").size() != 1
          || result.path("taskDispositions").size() != 2
          || result.path("businessProcessHypotheses").size() != 0
          || result.path("providerCallCount").asInt(-1) != 1
          || !gapIds.stream().allMatch(processGapsById::containsKey)
          || !"NOT_RUN_UPSTREAM_FAILED".equals(result.at("/taskDispositions/1/state").asText())) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      return;
    }
    throw failure("PROCESS_MODEL_REFERENCE_INVALID");
  }

  private void verifyCandidateRelationDenominator(
      ReopenedModulePublication m7, JsonNode m7Body, Set<String> ownerIds) {
    AnalysisStepModuleAddress m7Address = analysisAddress(m7);
    ModulePublicationReference m6Reference =
        moduleReference(
            requiredObject(m7Body, "crossFlowCandidateCompilationPublicationRef"),
            new AnalysisStepModuleAddress(
                m7Address.runId(),
                AnalysisStepKey.FLOW_INTERPRETATION,
                6,
                "cross-flow-candidate-compiler"));
    ReopenedModulePublication m6 = moduleArtifacts.reopen(m6Reference);
    if (!m6Reference.equals(m6.reference())
        || !analysisAddress(m6).runId().equals(m7Address.runId())
        || analysisAddress(m6).analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || analysisAddress(m6).moduleNumber() != 6
        || !"cross-flow-candidate-compiler".equals(analysisAddress(m6).moduleKey())
        || m6.payloads().size() != 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload payload = m6.payloads().get(0);
    if (!"cross-flow-candidate-compilation.json".equals(payload.descriptor().fileName())
        || !"FLOW_INTERPRETATION_CROSS_FLOW_CANDIDATE_COMPILATION"
            .equals(payload.descriptor().artifactType())
        || !"flow-interpretation-cross-flow-candidate-compilation-v1"
            .equals(payload.descriptor().schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    JsonNode m6Body = payloadBody(payload);
    if (!m6Body.path("closed").asBoolean(false)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    Set<String> denominator = new HashSet<>();
    for (JsonNode group : requiredArray(m6Body, "processEvidenceGroups")) {
      for (JsonNode relation : requiredArray(group, "candidateRelations")) {
        if (!denominator.add(requiredText(relation, "candidateRelationId"))) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
      }
    }
    if (!denominator.equals(ownerIds)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
  }

  private static Map<String, JsonNode> indexProcessGaps(ArrayNode values) {
    Map<String, JsonNode> result = new HashMap<>();
    for (JsonNode value : values) {
      String gapId = requiredText(value, "gapId");
      if (result.put(gapId, value) != null) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
    return Map.copyOf(result);
  }

  private static void copy(JsonNode source, ObjectNode target, String field) {
    JsonNode value = source.get(field);
    if (value == null) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    target.set(field, value.deepCopy());
  }

  private static JsonNode payloadBody(VerifiedCanonicalPayload payload) {
    JsonNode body =
        new CanonicalJsonCodec().parseCanonical(payload.canonicalUtf8()).path("payload");
    if (!body.isObject()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return body;
  }

  private static AnalysisStepModuleAddress analysisAddress(ReopenedModulePublication publication) {
    if (!(publication.receipt().address() instanceof AnalysisStepModuleAddress address)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return address;
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static ArrayNode requiredArray(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (!(value instanceof ArrayNode array)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return array;
  }

  private static ObjectNode requiredObject(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (!(value instanceof ObjectNode object)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return object;
  }

  private static String requiredText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return value.textValue();
  }

  private static List<String> requiredIdentifiers(JsonNode node, String field, boolean nonempty) {
    ArrayNode values = requiredArray(node, field);
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JsonNode value : values) {
      if (!value.isTextual() || value.textValue().isBlank() || !seen.add(value.textValue())) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      result.add(value.textValue());
    }
    if ((nonempty && result.isEmpty()) || !result.equals(sortedUnique(result))) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return List.copyOf(result);
  }

  private static List<String> sortedUnique(List<String> values) {
    return values.stream().distinct().sorted(UTF8_ORDER).toList();
  }

  private static void strings(ArrayNode target, List<String> values) {
    sortedUnique(values).forEach(target::add);
  }

  private static boolean sameModuleReference(JsonNode value, ModulePublicationReference reference) {
    return value.isObject()
        && reference.moduleArtifactRoot().value().equals(value.path("moduleArtifactRoot").asText())
        && reference.moduleReceiptId().value().equals(value.path("moduleReceiptId").asText())
        && reference
            .moduleReceiptSha256()
            .value()
            .equals(value.path("moduleReceiptSha256").asText());
  }

  private static ModulePublicationReference moduleReference(
      JsonNode value, AnalysisStepModuleAddress expectedAddress) {
    if (!value.isObject()
        || value.size() != 3
        || !value.has("moduleArtifactRoot")
        || !value.has("moduleReceiptId")
        || !value.has("moduleReceiptSha256")) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    try {
      return new ModulePublicationReference(
          expectedAddress,
          ModuleArtifactRoot.parse(requiredText(value, "moduleArtifactRoot")),
          ModuleReceiptId.parse(requiredText(value, "moduleReceiptId")),
          Sha256Digest.parse(requiredText(value, "moduleReceiptSha256")));
    } catch (RuntimeException invalid) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID", invalid);
    }
  }

  private static void moduleReference(ObjectNode target, ModulePublicationReference reference) {
    target.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    target.put("moduleReceiptId", reference.moduleReceiptId().value());
    target.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> artifactReference(result.addObject(), value));
    return result;
  }

  private static void artifactReference(ObjectNode target, ArtifactReference reference) {
    target.put("artifactId", reference.artifactId().value());
    target.put("sha256", reference.sha256().value());
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result
        .putObject("address")
        .put("kind", "ANALYSIS_STEP")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue())
        .put("moduleNumber", address.moduleNumber())
        .put("moduleKey", address.moduleKey());
    result.put("moduleVersion", "v1");
    return result;
  }

  private static ObjectNode controls(ArtifactControls values) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("toolchainSha256", values.toolchainSha256().value());
    result.put("profileSha256", values.profileSha256().value());
    result.put("schemaBundleSha256", values.schemaBundleSha256().value());
    if (values.promptBundleSha256() == null) {
      result.putNull("promptBundleSha256");
    } else {
      result.put("promptBundleSha256", values.promptBundleSha256().value());
    }
    result
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return result;
  }

  private static ObjectNode completion(ModuleCompletionStatus status, List<String> gapRefs) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", status.name());
    strings(result.putArray("gapRefs"), gapRefs);
    result.putNull("failureRef");
    return result;
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
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

  private static BusinessProcessInterpretationException failure(String code) {
    return new BusinessProcessInterpretationException(code);
  }

  private static BusinessProcessInterpretationException failure(String code, Throwable cause) {
    return new BusinessProcessInterpretationException(code, cause);
  }

  private record M7Shard(
      String taskShardId,
      String processEvidenceGroupId,
      int shardOrdinal,
      String shardModelDisposition,
      List<String> ownerCandidateRelationIds,
      List<String> modelIneligibilityGapIds,
      JsonNode source) {}

  private record GroupLocalShardOrdinal(String processEvidenceGroupId, int shardOrdinal) {}

  private record M7Preflight(
      List<M7Shard> shards,
      List<M7Shard> modelSafeShards,
      List<M7Shard> noModelShards,
      Map<String, JsonNode> processGapsById) {}

  private record Aggregate(
      List<String> taskShardIds,
      List<String> ownerCandidateRelationIds,
      int taskShardCount,
      int modelSafeShardCount,
      int noModelShardCount,
      int plannedProcessModelTaskCount,
      int actualProcessModelRoundCount,
      int generationReceiptCount,
      int taskDispositionCount,
      int businessProcessHypothesisCount,
      int processInterpretationDispositionCount,
      int providerCallCount,
      int readyForAdmissionCount,
      int gapDispositionCount,
      int noModelAdmissionPendingCount,
      int failedDispositionCount,
      String executionOutcome,
      ModuleCompletionStatus completionStatus,
      List<String> gapRefs) {}
}
