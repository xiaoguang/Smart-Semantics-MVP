package org.sourceanalysis.app.analysis.interpretation.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * M7's program-only partition and dry-packet compiler.
 *
 * <p>The compiler fresh-reopens the complete M6 material, assigns one deterministic ownership shard
 * per bounded group in this first vertical slice, and never invokes a Provider. Packet-local reader
 * keys are the only identifiers exposed to the future P1/P2 request builder.
 */
public final class BusinessProcessTaskCompiler {

  private static final String M6_FILE = "cross-flow-candidate-compilation.json";
  private static final String M6_TYPE = "FLOW_INTERPRETATION_CROSS_FLOW_CANDIDATE_COMPILATION";
  private static final String M6_SCHEMA = "flow-interpretation-cross-flow-candidate-compilation-v1";
  private static final String PACKET_SCHEMA = "flow-interpretation-process-model-packet-v1";
  private static final Comparator<String> UTF8_ORDER = BusinessProcessTaskCompiler::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Creates a compiler which reads only receipt-last M6 module publications. */
  public BusinessProcessTaskCompiler(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /** Reopens M6 and deterministically partitions every candidate group without a model call. */
  public BusinessProcessTaskCompilation compileTasks(ModulePublicationReference m6Publication) {
    try {
      JsonNode payload = reopen(m6Publication);
      List<JsonNode> groups =
          sortedObjects(array(payload, "processEvidenceGroups"), "processEvidenceGroupId");
      List<BusinessProcessTaskShardV1> shards = new ArrayList<>();
      List<ProcessInterpretationGapV1> gaps = new ArrayList<>();
      for (JsonNode group : groups) {
        GroupCompilation compilation = compileGroup(group);
        shards.addAll(compilation.taskShards());
        gaps.addAll(compilation.processGaps());
      }
      return new BusinessProcessTaskCompilation(
          shards,
          gaps.stream()
              .sorted(Comparator.comparing(ProcessInterpretationGapV1::gapId, UTF8_ORDER))
              .toList(),
          0,
          true);
    } catch (BusinessProcessTaskCompilationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID", failure);
    }
  }

  private JsonNode reopen(ModulePublicationReference reference) {
    if (reference == null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    ReopenedModulePublication publication = moduleArtifacts.reopen(reference);
    if (!reference.equals(publication.reference())
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 6
        || !"cross-flow-candidate-compiler".equals(address.moduleKey())
        || publication.payloads().size() != 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!M6_FILE.equals(payload.descriptor().fileName())
        || !M6_TYPE.equals(payload.descriptor().artifactType())
        || !M6_SCHEMA.equals(payload.descriptor().schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    JsonNode root = canonicalJson.parseCanonical(payload.canonicalUtf8());
    JsonNode body = root.path("payload");
    if (!body.isObject() || !body.path("closed").asBoolean(false)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return body;
  }

  private GroupCompilation compileGroup(JsonNode group) {
    String groupId = text(group, "processEvidenceGroupId");
    JsonNode material = object(group, "persistedMaterial");
    ProcessMaterialLimitsV1 limits = limits(material.path("limits"));
    List<JsonNode> flows = sortedObjects(array(material, "flowViews"), "flowSliceId");
    List<JsonNode> relations =
        sortedObjects(array(group, "candidateRelations"), "candidateRelationId");
    List<JsonNode> registryItems =
        sortedObjects(array(material, "registryItems"), "provisionalKey");
    Map<String, JsonNode> flowsById = indexed(flows, "flowSliceId");
    List<AtomicUnit> units = atomicUnits(flowsById, relations);
    List<BusinessProcessTaskShardV1> shards = new ArrayList<>();
    List<ProcessInterpretationGapV1> gaps = new ArrayList<>();
    List<AtomicUnit> pending = new ArrayList<>();
    PacketProjection pendingProjection = null;

    for (AtomicUnit unit : units) {
      if (!modelSafe(unit, flowsById)) {
        if (!pending.isEmpty()) {
          SafeShardProjection safe =
              modelSafeShard(groupId, shards.size(), pending, pendingProjection);
          shards.add(safe.shard());
          gaps.addAll(safe.processGaps());
          pending = new ArrayList<>();
          pendingProjection = null;
        }
        NoModelShardProjection projection =
            upstreamNoModelShard(groupId, shards.size(), unit, flowsById);
        shards.add(projection.shard());
        gaps.addAll(projection.processGaps());
        continue;
      }

      List<AtomicUnit> candidate = appended(pending, unit);
      PacketProjection candidateProjection =
          projectionIfFits(material, candidate, flowsById, relations, registryItems, limits);
      if (candidateProjection != null) {
        pending = candidate;
        pendingProjection = candidateProjection;
        continue;
      }

      if (!pending.isEmpty()) {
        SafeShardProjection safe =
            modelSafeShard(groupId, shards.size(), pending, pendingProjection);
        shards.add(safe.shard());
        gaps.addAll(safe.processGaps());
        pending = new ArrayList<>();
        pendingProjection = null;
        candidateProjection =
            projectionIfFits(material, List.of(unit), flowsById, relations, registryItems, limits);
        if (candidateProjection != null) {
          pending = List.of(unit);
          pendingProjection = candidateProjection;
          continue;
        }
      }

      BudgetExcess excess =
          budgetExcess(material, List.of(unit), flowsById, relations, registryItems, limits);
      BusinessProcessTaskShardV1 noModel = budgetNoModelShard(groupId, shards.size(), unit, excess);
      gaps.add(budgetGap(groupId, noModel, excess));
      shards.add(noModel);
    }
    if (!pending.isEmpty()) {
      SafeShardProjection safe = modelSafeShard(groupId, shards.size(), pending, pendingProjection);
      shards.add(safe.shard());
      gaps.addAll(safe.processGaps());
    }
    if (shards.isEmpty()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return new GroupCompilation(shards, gaps);
  }

  private List<AtomicUnit> atomicUnits(Map<String, JsonNode> flowsById, List<JsonNode> relations) {
    List<AtomicUnit> result = new ArrayList<>();
    for (JsonNode relation : relations) {
      String relationId = text(relation, "candidateRelationId");
      String left = text(relation, "leftFlowSliceId");
      String right = text(relation, "rightFlowSliceId");
      if (left.equals(right) || !flowsById.containsKey(left) || !flowsById.containsKey(right)) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      result.add(new AtomicUnit(List.of(relationId), sortedDistinct(List.of(left, right))));
    }
    if (result.isEmpty()) {
      flowsById.keySet().stream()
          .sorted(UTF8_ORDER)
          .forEach(flowId -> result.add(new AtomicUnit(List.of(), List.of(flowId))));
    }
    result.sort(
        Comparator.comparing(
            unit ->
                unit.ownerCandidateRelationIds().isEmpty()
                    ? unit.contextFlowSliceIds().get(0)
                    : unit.ownerCandidateRelationIds().get(0),
            UTF8_ORDER));
    return List.copyOf(result);
  }

  private boolean modelSafe(AtomicUnit unit, Map<String, JsonNode> flowsById) {
    return unit.contextFlowSliceIds().stream()
        .map(flowsById::get)
        .map(value -> object(value, "evidenceCapsule"))
        .allMatch(value -> "ELIGIBLE".equals(text(value, "modelEligibility")));
  }

  private NoModelShardProjection upstreamNoModelShard(
      String groupId, int ordinal, AtomicUnit unit, Map<String, JsonNode> flowsById) {
    String taskShardId =
        shardId(groupId, unit.ownerCandidateRelationIds(), unit.contextFlowSliceIds(), "NO_MODEL");
    Map<String, UpstreamGapAccumulator> upstreamGaps = new HashMap<>();
    for (String flowId : unit.contextFlowSliceIds()) {
      JsonNode capsule = object(required(flowsById.get(flowId)), "evidenceCapsule");
      if (!"ELIGIBLE".equals(text(capsule, "modelEligibility"))) {
        Map<String, JsonNode> gapViews =
            indexed(sortedObjects(array(capsule, "gapViews"), "gapId"), "gapId");
        for (String upstreamGapId : identifiers(capsule, "modelIneligibilityGapIds")) {
          JsonNode gapView = gapViews.get(upstreamGapId);
          if (gapView == null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
          UpstreamGapAccumulator accumulator = upstreamGaps.get(upstreamGapId);
          if (accumulator == null) {
            accumulator = new UpstreamGapAccumulator(gapView.deepCopy(), new ArrayList<>());
            upstreamGaps.put(upstreamGapId, accumulator);
          } else if (!accumulator.gapView().equals(gapView)) {
            throw failure("PROCESS_MODEL_REFERENCE_INVALID");
          }
          accumulator.sourceFlowSliceIds().add(flowId);
        }
      }
    }
    if (upstreamGaps.isEmpty()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    List<ProcessInterpretationGapV1> processGaps =
        upstreamGaps.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(UTF8_ORDER))
            .map(
                entry ->
                    upstreamModelIneligibilityGap(
                        groupId,
                        taskShardId,
                        unit,
                        new UpstreamFlowGapProjectionV1(
                            sortedDistinct(entry.getValue().sourceFlowSliceIds()),
                            entry.getValue().gapView())))
            .sorted(Comparator.comparing(ProcessInterpretationGapV1::gapId, UTF8_ORDER))
            .toList();
    return new NoModelShardProjection(
        new BusinessProcessTaskShardV1(
            taskShardId,
            ordinal,
            groupId,
            unit.ownerCandidateRelationIds(),
            unit.contextFlowSliceIds(),
            "NO_MODEL",
            processGaps.stream().map(ProcessInterpretationGapV1::gapId).toList(),
            null,
            List.of()),
        processGaps);
  }

  private PacketProjection projectionIfFits(
      JsonNode material,
      List<AtomicUnit> units,
      Map<String, JsonNode> flowsById,
      List<JsonNode> allRelations,
      List<JsonNode> allRegistryItems,
      ProcessMaterialLimitsV1 limits) {
    Selection selection = selection(units, flowsById, allRelations, allRegistryItems);
    if (selection.flowViews().size() > limits.maxFlows()
        || selection.relations().size() > limits.maxRelations()
        || selection.signalCount() > limits.maxSignals()
        || selection.registryItems().size() > limits.maxRegistryItems()) {
      return null;
    }
    PacketProjection projection =
        projectPacket(
            material, selection.flowViews(), selection.relations(), selection.registryItems());
    return canonicalJson.encodeCanonical(objectMapper.valueToTree(projection.packet())).size()
            <= limits.maxInputBytes()
        ? projection
        : null;
  }

  private BudgetExcess budgetExcess(
      JsonNode material,
      List<AtomicUnit> units,
      Map<String, JsonNode> flowsById,
      List<JsonNode> allRelations,
      List<JsonNode> allRegistryItems,
      ProcessMaterialLimitsV1 limits) {
    Selection selection = selection(units, flowsById, allRelations, allRegistryItems);
    if (selection.flowViews().size() > limits.maxFlows()) {
      return new BudgetExcess("MAX_FLOWS", limits.maxFlows(), selection.flowViews().size());
    }
    if (selection.relations().size() > limits.maxRelations()) {
      return new BudgetExcess("MAX_RELATIONS", limits.maxRelations(), selection.relations().size());
    }
    if (selection.signalCount() > limits.maxSignals()) {
      return new BudgetExcess("MAX_SIGNALS", limits.maxSignals(), selection.signalCount());
    }
    if (selection.registryItems().size() > limits.maxRegistryItems()) {
      return new BudgetExcess(
          "MAX_REGISTRY_ITEMS", limits.maxRegistryItems(), selection.registryItems().size());
    }
    PacketProjection projection =
        projectPacket(
            material, selection.flowViews(), selection.relations(), selection.registryItems());
    int byteCount =
        canonicalJson.encodeCanonical(objectMapper.valueToTree(projection.packet())).size();
    if (byteCount > limits.maxInputBytes()) {
      return new BudgetExcess("MAX_INPUT_BYTES", limits.maxInputBytes(), byteCount);
    }
    throw failure("PROCESS_MODEL_REFERENCE_INVALID");
  }

  private Selection selection(
      List<AtomicUnit> units,
      Map<String, JsonNode> flowsById,
      List<JsonNode> allRelations,
      List<JsonNode> allRegistryItems) {
    List<String> flowIds =
        sortedUnique(units.stream().flatMap(unit -> unit.contextFlowSliceIds().stream()).toList());
    List<String> relationIds =
        sortedUnique(
            units.stream().flatMap(unit -> unit.ownerCandidateRelationIds().stream()).toList());
    List<JsonNode> flowViews = flowIds.stream().map(flowsById::get).toList();
    Map<String, JsonNode> relationsById = indexed(allRelations, "candidateRelationId");
    List<JsonNode> relations = relationIds.stream().map(relationsById::get).toList();
    List<JsonNode> registryItems =
        allRegistryItems.stream()
            .filter(item -> flowIds.contains(text(item, "flowSliceId")))
            .sorted(Comparator.comparing(item -> text(item, "provisionalKey"), UTF8_ORDER))
            .toList();
    int signalCount =
        flowViews.stream()
            .mapToInt(value -> array(object(value, "evidenceCapsule"), "processJoinSignals").size())
            .sum();
    return new Selection(flowViews, relations, registryItems, signalCount);
  }

  private SafeShardProjection modelSafeShard(
      String groupId, int ordinal, List<AtomicUnit> units, PacketProjection projection) {
    if (projection == null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    List<String> relationIds =
        sortedUnique(
            units.stream().flatMap(unit -> unit.ownerCandidateRelationIds().stream()).toList());
    List<String> flowIds =
        sortedUnique(units.stream().flatMap(unit -> unit.contextFlowSliceIds().stream()).toList());
    String taskShardId = shardId(groupId, relationIds, flowIds, "MODEL_SAFE");
    Map<String, String> carrierIdsByUpstreamGapId = new HashMap<>();
    List<ProcessInterpretationGapV1> processGaps = new ArrayList<>();
    projection.limitationUpstreams().entrySet().stream()
        .sorted(Map.Entry.comparingByKey(UTF8_ORDER))
        .forEach(
            entry -> {
              UpstreamGapAccumulator accumulator = entry.getValue();
              ProcessInterpretationGapV1 carrier =
                  upstreamLimitationGap(
                      groupId,
                      taskShardId,
                      relationIds,
                      flowIds,
                      new UpstreamFlowGapProjectionV1(
                          sortedDistinct(accumulator.sourceFlowSliceIds()), accumulator.gapView()));
              carrierIdsByUpstreamGapId.put(entry.getKey(), carrier.gapId());
              processGaps.add(carrier);
            });
    List<ReaderKeyBindingV1> bindings =
        projection.bindings().stream()
            .map(
                binding -> {
                  if (!"LIMITATION".equals(binding.keyKind())) return binding;
                  if (binding.internalReferenceIds().size() != 1) {
                    throw failure("PROCESS_MODEL_REFERENCE_INVALID");
                  }
                  String carrierId =
                      carrierIdsByUpstreamGapId.get(binding.internalReferenceIds().get(0));
                  if (carrierId == null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
                  return new ReaderKeyBindingV1(
                      binding.readerKey(), binding.keyKind(), List.of(carrierId));
                })
            .sorted(Comparator.comparing(ReaderKeyBindingV1::readerKey, UTF8_ORDER))
            .toList();
    if (projection.packet().limitations().size() != carrierIdsByUpstreamGapId.size()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    Set<String> limitationKeys =
        projection.packet().limitations().stream()
            .map(ProcessModelPacketV1.ReaderLimitationV1::limitationKey)
            .collect(java.util.stream.Collectors.toSet());
    if (limitationKeys.size() != projection.packet().limitations().size()
        || bindings.stream()
                .filter(binding -> "LIMITATION".equals(binding.keyKind()))
                .map(ReaderKeyBindingV1::readerKey)
                .collect(java.util.stream.Collectors.toSet())
                .size()
            != limitationKeys.size()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return new SafeShardProjection(
        new BusinessProcessTaskShardV1(
            taskShardId,
            ordinal,
            groupId,
            relationIds,
            flowIds,
            "MODEL_SAFE",
            List.of(),
            projection.packet(),
            bindings),
        processGaps.stream()
            .sorted(Comparator.comparing(ProcessInterpretationGapV1::gapId, UTF8_ORDER))
            .toList());
  }

  private BusinessProcessTaskShardV1 noModelShard(
      String groupId, int ordinal, AtomicUnit unit, List<String> gapIds) {
    return new BusinessProcessTaskShardV1(
        shardId(groupId, unit.ownerCandidateRelationIds(), unit.contextFlowSliceIds(), "NO_MODEL"),
        ordinal,
        groupId,
        unit.ownerCandidateRelationIds(),
        unit.contextFlowSliceIds(),
        "NO_MODEL",
        gapIds,
        null,
        List.of());
  }

  private BusinessProcessTaskShardV1 budgetNoModelShard(
      String groupId, int ordinal, AtomicUnit unit, BudgetExcess excess) {
    return noModelShard(
        groupId,
        ordinal,
        unit,
        List.of(
            budgetGapId(
                groupId, unit.ownerCandidateRelationIds(), unit.contextFlowSliceIds(), excess)));
  }

  private ProcessInterpretationGapV1 budgetGap(
      String groupId, BusinessProcessTaskShardV1 shard, BudgetExcess excess) {
    return new ProcessInterpretationGapV1(
        shard.modelIneligibilityGapIds().get(0),
        "PROCESS_TASK_BUDGET_EXCEEDED",
        "PROCESS_TASK_SHARD",
        groupId,
        shard.ownerCandidateRelationIds(),
        shard.contextFlowSliceIds(),
        excess.limitKind(),
        excess.configuredLimit(),
        excess.observedValue(),
        "process-task-budget-exceeded",
        shard.taskShardId(),
        null);
  }

  private ProcessInterpretationGapV1 upstreamModelIneligibilityGap(
      String groupId,
      String taskShardId,
      AtomicUnit unit,
      UpstreamFlowGapProjectionV1 upstreamGap) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("gapCode", "PROCESS_UPSTREAM_MODEL_INELIGIBLE");
    identity.put("gapScope", "PROCESS_TASK_SHARD");
    identity.put("processEvidenceGroupId", groupId);
    strings(identity.putArray("candidateRelationIds"), unit.ownerCandidateRelationIds());
    strings(identity.putArray("affectedFlowSliceIds"), unit.contextFlowSliceIds());
    identity.putNull("limitKind");
    identity.putNull("configuredLimit");
    identity.putNull("observedValue");
    identity.put("messageKey", "process-upstream-model-ineligible");
    identity.set("upstreamGap", objectMapper.valueToTree(upstreamGap));
    String gapId =
        "gap:"
            + sha256(
                frame("flow-interpretation-upstream-model-ineligibility-gap-v1"),
                frame(canonicalJson.encodeCanonical(identity).copyToByteArray()));
    return new ProcessInterpretationGapV1(
        gapId,
        "PROCESS_UPSTREAM_MODEL_INELIGIBLE",
        "PROCESS_TASK_SHARD",
        groupId,
        unit.ownerCandidateRelationIds(),
        unit.contextFlowSliceIds(),
        null,
        null,
        null,
        "process-upstream-model-ineligible",
        taskShardId,
        upstreamGap);
  }

  private ProcessInterpretationGapV1 upstreamLimitationGap(
      String groupId,
      String taskShardId,
      List<String> relationIds,
      List<String> flowIds,
      UpstreamFlowGapProjectionV1 upstreamGap) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("gapCode", "PROCESS_UPSTREAM_LIMITATION");
    identity.put("gapScope", "PROCESS_TASK_SHARD");
    identity.put("processEvidenceGroupId", groupId);
    strings(identity.putArray("candidateRelationIds"), relationIds);
    strings(identity.putArray("affectedFlowSliceIds"), flowIds);
    identity.putNull("limitKind");
    identity.putNull("configuredLimit");
    identity.putNull("observedValue");
    identity.put("messageKey", "process-upstream-limitation");
    identity.put("taskShardId", taskShardId);
    identity.set("upstreamGap", objectMapper.valueToTree(upstreamGap));
    String gapId =
        "gap:"
            + sha256(
                frame("flow-interpretation-upstream-limitation-gap-v1"),
                frame(canonicalJson.encodeCanonical(identity).copyToByteArray()));
    return new ProcessInterpretationGapV1(
        gapId,
        "PROCESS_UPSTREAM_LIMITATION",
        "PROCESS_TASK_SHARD",
        groupId,
        relationIds,
        flowIds,
        null,
        null,
        null,
        "process-upstream-limitation",
        taskShardId,
        upstreamGap);
  }

  private static List<AtomicUnit> appended(List<AtomicUnit> values, AtomicUnit next) {
    List<AtomicUnit> result = new ArrayList<>(values);
    result.add(next);
    return List.copyOf(result);
  }

  private PacketProjection projectPacket(
      JsonNode material,
      List<JsonNode> flowViews,
      List<JsonNode> relations,
      List<JsonNode> registryItems) {
    KeySequence keys = new KeySequence();
    Map<String, String> flowKeys = new LinkedHashMap<>();
    for (JsonNode flow : flowViews) flowKeys.put(text(flow, "flowSliceId"), keys.next("F"));

    List<ProcessModelPacketV1.ReaderSourceV1> sources = new ArrayList<>();
    List<ReaderKeyBindingV1> bindings = new ArrayList<>();
    Map<String, String> sourceKeysByLocator = new LinkedHashMap<>();
    Map<String, List<String>> flowSourceKeys = new HashMap<>();
    for (JsonNode view : flowViews) {
      String flowId = text(view, "flowSliceId");
      List<String> sourceKeys = new ArrayList<>();
      for (JsonNode span :
          sortedObjects(array(object(view, "evidenceCapsule"), "modelEvidenceSpans"), "spanId")) {
        JsonNode excerpt = object(span, "sourceExcerpt");
        JsonNode locator = object(excerpt, "locator");
        String locatorKey =
            text(locator, "path")
                + "\u0000"
                + positiveInt(locator, "startLine")
                + "\u0000"
                + positiveInt(locator, "endLine");
        String sourceKey = sourceKeysByLocator.get(locatorKey);
        if (sourceKey == null) {
          sourceKey = keys.next("S");
          sourceKeysByLocator.put(locatorKey, sourceKey);
          sources.add(
              new ProcessModelPacketV1.ReaderSourceV1(
                  sourceKey,
                  text(locator, "path"),
                  positiveInt(locator, "startLine"),
                  positiveInt(locator, "endLine"),
                  null,
                  boundedExcerpt(text(excerpt, "rawUtf8"))));
          bindings.add(new ReaderKeyBindingV1(sourceKey, "SOURCE", List.of(text(span, "spanId"))));
        }
        sourceKeys.add(sourceKey);
      }
      if (sourceKeys.isEmpty()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      flowSourceKeys.put(flowId, sortedUnique(sourceKeys));
    }

    List<ProcessModelPacketV1.ReaderFlowCardV1> flowCards = new ArrayList<>();
    Map<String, JsonNode> facts = facts(material);
    for (JsonNode view : flowViews) {
      String flowId = text(view, "flowSliceId");
      JsonNode flow = object(view, "flowSlice");
      String flowKey = required(flowKeys.get(flowId));
      List<String> sourcesForFlow = required(flowSourceKeys.get(flowId));
      List<ProcessModelPacketV1.ReaderActivityV1> activities = new ArrayList<>();
      List<ProcessModelPacketV1.ReaderValueV1> inputs = new ArrayList<>();
      List<ProcessModelPacketV1.ReaderValueV1> outputs = new ArrayList<>();
      int ordinal = 1;
      for (String factId : identifiers(flow, "factIds")) {
        JsonNode fact = facts.get(factId);
        if (fact == null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        String activityKey = keys.next("A");
        List<String> activityInputs = new ArrayList<>();
        List<String> activityOutputs = new ArrayList<>();
        for (JsonNode atom : array(fact, "atoms")) {
          String valueKey = keys.next("V");
          String role = readerValueRole(text(atom, "role"));
          ProcessModelPacketV1.ReaderValueV1 readerValue =
              new ProcessModelPacketV1.ReaderValueV1(
                  valueKey,
                  role,
                  text(atom, "name"),
                  boundedText(text(object(atom, "value"), "canonical")),
                  sourcesForFlow);
          if ("INPUT".equals(role)) {
            inputs.add(readerValue);
            activityInputs.add(valueKey);
          } else {
            outputs.add(readerValue);
            activityOutputs.add(valueKey);
          }
          bindings.add(new ReaderKeyBindingV1(valueKey, "VALUE", List.of(text(atom, "atomId"))));
        }
        activities.add(
            new ProcessModelPacketV1.ReaderActivityV1(
                activityKey,
                ordinal++,
                activityKind(text(fact, "kind")),
                text(fact, "kind"),
                activityInputs,
                activityOutputs,
                sourcesForFlow));
        bindings.add(new ReaderKeyBindingV1(activityKey, "ACTIVITY", List.of(factId)));
      }
      if (activities.isEmpty()) {
        String activityKey = keys.next("A");
        activities.add(
            new ProcessModelPacketV1.ReaderActivityV1(
                activityKey,
                1,
                "BOUNDARY_CALL",
                text(flow, "trigger"),
                List.of(),
                List.of(),
                sourcesForFlow));
        bindings.add(new ReaderKeyBindingV1(activityKey, "ACTIVITY", List.of(flowId)));
      }
      List<ProcessModelPacketV1.ReaderConditionV1> conditions = new ArrayList<>();
      List<ProcessModelPacketV1.ReaderOutcomeV1> outcomes = new ArrayList<>();
      for (JsonNode outcome : sortedObjects(array(flow, "outcomePaths"), "outcomePathId")) {
        for (JsonNode decision : array(outcome, "decisions")) {
          String conditionKey = keys.next("C");
          conditions.add(
              new ProcessModelPacketV1.ReaderConditionV1(
                  conditionKey,
                  text(decision, "normalizedCondition"),
                  text(decision, "polarity"),
                  sourcesForFlow));
          bindings.add(
              new ReaderKeyBindingV1(
                  conditionKey, "CONDITION", List.of(text(decision, "conditionAtomId"))));
        }
        String outcomeKey = keys.next("OUT");
        outcomes.add(
            new ProcessModelPacketV1.ReaderOutcomeV1(
                outcomeKey,
                text(outcome, "terminalKind"),
                "Technical terminal outcome " + text(outcome, "terminalKind"),
                sourcesForFlow));
        bindings.add(
            new ReaderKeyBindingV1(outcomeKey, "OUTCOME", List.of(text(outcome, "outcomePathId"))));
      }
      flowCards.add(
          new ProcessModelPacketV1.ReaderFlowCardV1(
              flowKey,
              entry(text(flow, "trigger")),
              activities,
              inputs,
              outputs,
              conditions,
              outcomes,
              sourcesForFlow));
      bindings.add(new ReaderKeyBindingV1(flowKey, "FLOW", List.of(flowId)));
    }

    List<ProcessModelPacketV1.ReaderRelationCardV1> relationCards = new ArrayList<>();
    for (JsonNode relation : relations) {
      String relationKey = keys.next("L");
      String left = required(flowKeys.get(text(relation, "leftFlowSliceId")));
      String right = required(flowKeys.get(text(relation, "rightFlowSliceId")));
      String level = text(relation, "strongestSignalLevel");
      relationCards.add(
          new ProcessModelPacketV1.ReaderRelationCardV1(
              relationKey,
              left,
              right,
              level,
              text(relation, "direction"),
              text(relation, "relationUse"),
              "PROVEN_HANDOFF".equals(level) ? "EXPLICIT_CALL" : "BUSINESS_TERM_MATCH",
              relationSummary(level),
              sortedUnique(
                  concat(
                      flowSourceKeys.get(text(relation, "leftFlowSliceId")),
                      flowSourceKeys.get(text(relation, "rightFlowSliceId"))))));
      bindings.add(
          new ReaderKeyBindingV1(
              relationKey, "RELATION", List.of(text(relation, "candidateRelationId"))));
    }

    List<ProcessModelPacketV1.ReaderTermV1> terms = new ArrayList<>();
    for (JsonNode item : registryItems) {
      String termKey = keys.next("T");
      String flowKey = required(flowKeys.get(text(item, "flowSliceId")));
      terms.add(
          new ProcessModelPacketV1.ReaderTermV1(
              termKey,
              text(item, "proposalKind"),
              text(item, "normalizedLabel"),
              nullableText(item, "normalizedPurpose", "No additional purpose was proposed."),
              List.of(flowKey)));
      bindings.add(new ReaderKeyBindingV1(termKey, "TERM", List.of(text(item, "provisionalKey"))));
    }

    Map<String, UpstreamGapAccumulator> limitationUpstreams = new LinkedHashMap<>();
    for (JsonNode view : flowViews) {
      String flowId = text(view, "flowSliceId");
      for (JsonNode gap :
          sortedObjects(array(object(view, "evidenceCapsule"), "gapViews"), "gapId")) {
        String upstreamGapId = text(gap, "gapId");
        UpstreamGapAccumulator accumulator = limitationUpstreams.get(upstreamGapId);
        if (accumulator == null) {
          accumulator = new UpstreamGapAccumulator(gap.deepCopy(), new ArrayList<>());
          limitationUpstreams.put(upstreamGapId, accumulator);
        } else if (!accumulator.gapView().equals(gap)) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
        accumulator.sourceFlowSliceIds().add(flowId);
      }
    }
    List<ProcessModelPacketV1.ReaderLimitationV1> limitations = new ArrayList<>();
    limitationUpstreams.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(UTF8_ORDER))
        .forEach(
            entry -> {
              String limitationKey = keys.next("Q");
              UpstreamGapAccumulator accumulator = entry.getValue();
              List<String> limitationFlowKeys =
                  sortedDistinct(accumulator.sourceFlowSliceIds()).stream()
                      .map(flowKeys::get)
                      .map(BusinessProcessTaskCompiler::required)
                      .toList();
              limitations.add(
                  new ProcessModelPacketV1.ReaderLimitationV1(
                      limitationKey,
                      "Static-analysis limitation: " + text(accumulator.gapView(), "reasonCode"),
                      limitationFlowKeys));
              bindings.add(
                  new ReaderKeyBindingV1(limitationKey, "LIMITATION", List.of(entry.getKey())));
            });
    return new PacketProjection(
        new ProcessModelPacketV1(
            PACKET_SCHEMA,
            "DRY_BUSINESS_PROCESS_READER",
            flowCards,
            relationCards,
            terms,
            limitations,
            sources),
        bindings.stream()
            .sorted(Comparator.comparing(ReaderKeyBindingV1::readerKey, UTF8_ORDER))
            .toList(),
        Map.copyOf(limitationUpstreams));
  }

  private static ProcessModelPacketV1.ReaderEntryV1 entry(String trigger) {
    String normalized = trigger.toUpperCase(Locale.ROOT);
    if (normalized.startsWith("HTTP ")) {
      String[] words = trigger.split("\\s+", 3);
      return new ProcessModelPacketV1.ReaderEntryV1(
          "HTTP", words.length > 1 ? words[1] : null, words.length > 2 ? words[2] : null, trigger);
    }
    return new ProcessModelPacketV1.ReaderEntryV1("OTHER", null, null, trigger);
  }

  private static Map<String, JsonNode> facts(JsonNode material) {
    Map<String, JsonNode> values = new HashMap<>();
    for (JsonNode fact : array(material, "codeFacts")) {
      String factId = text(fact, "factId");
      JsonNode prior = values.putIfAbsent(factId, fact);
      if (prior != null && !prior.equals(fact)) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return Map.copyOf(values);
  }

  private static String readerValueRole(String atomRole) {
    String normalized = atomRole.toUpperCase(Locale.ROOT);
    return normalized.contains("INPUT") || normalized.contains("PARAMETER") ? "INPUT" : "OUTPUT";
  }

  private static String activityKind(String factKind) {
    String normalized = factKind.toUpperCase(Locale.ROOT);
    if (normalized.contains("CALL")) return "CALL";
    if (normalized.contains("CONDITION") || normalized.contains("GUARD")) return "CONDITION";
    if (normalized.contains("STATE")) return "STATE_CHANGE";
    return "ASSIGNMENT";
  }

  private static String relationSummary(String level) {
    return "PROVEN_HANDOFF".equals(level)
        ? "A proven Java call reaches another local Flow entry."
        : "The Flows share a bounded business term; sequence remains unconfirmed.";
  }

  private static ProcessMaterialLimitsV1 limits(JsonNode value) {
    return new ProcessMaterialLimitsV1(
        positiveInt(value, "maxFlows"),
        positiveInt(value, "maxRelations"),
        positiveInt(value, "maxSignals"),
        positiveInt(value, "maxRegistryItems"),
        positiveInt(value, "maxInputBytes"),
        positiveInt(value, "maxHypotheses"),
        positiveInt(value, "maxClaimsPerHypothesis"),
        positiveInt(value, "maxReaderSlots"));
  }

  private static String shardId(
      String groupId, List<String> relationIds, List<String> flowIds, String disposition) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("group", groupId);
    strings(identity.putArray("relations"), relationIds);
    strings(identity.putArray("flows"), flowIds);
    identity.put("disposition", disposition);
    return "process-task-shard:"
        + sha256(new CanonicalJsonCodec().encodeCanonical(identity).copyToByteArray());
  }

  private static String budgetGapId(
      String groupId, List<String> relationIds, List<String> flowIds, BudgetExcess excess) {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("gapCode", "PROCESS_TASK_BUDGET_EXCEEDED");
    identity.put("gapScope", "PROCESS_TASK_SHARD");
    identity.put("processEvidenceGroupId", groupId);
    strings(identity.putArray("candidateRelationIds"), relationIds);
    strings(identity.putArray("affectedFlowSliceIds"), flowIds);
    identity.put("limitKind", excess.limitKind());
    identity.put("configuredLimit", excess.configuredLimit());
    identity.put("observedValue", excess.observedValue());
    identity.put("messageKey", "process-task-budget-exceeded");
    return "gap:" + sha256(new CanonicalJsonCodec().encodeCanonical(identity).copyToByteArray());
  }

  private static List<String> concat(List<String> left, List<String> right) {
    List<String> values = new ArrayList<>();
    values.addAll(required(left));
    values.addAll(required(right));
    return values;
  }

  private static List<JsonNode> sortedObjects(ArrayNode values, String key) {
    List<JsonNode> result = new ArrayList<>();
    values.forEach(result::add);
    result.sort(Comparator.comparing(value -> text(value, key), UTF8_ORDER));
    if (result.size() != result.stream().map(value -> text(value, key)).distinct().count()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return result;
  }

  private static List<String> identifiers(JsonNode value, String field) {
    List<String> result = new ArrayList<>();
    for (JsonNode item : array(value, field)) result.add(textual(item));
    return sortedDistinct(result);
  }

  private static List<String> sortedDistinct(List<String> values) {
    List<String> result = new ArrayList<>(values);
    result.sort(UTF8_ORDER);
    if (result.size() != result.stream().distinct().count()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return List.copyOf(result);
  }

  private static List<String> sortedUnique(List<String> values) {
    return values.stream().distinct().sorted(UTF8_ORDER).toList();
  }

  private static Map<String, JsonNode> indexed(List<JsonNode> values, String field) {
    Map<String, JsonNode> result = new HashMap<>();
    for (JsonNode value : values) {
      String key = text(value, field);
      if (result.put(key, value) != null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return Map.copyOf(result);
  }

  private static ArrayNode array(JsonNode value, String field) {
    JsonNode array = value.get(field);
    if (!(array instanceof ArrayNode result)) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return result;
  }

  private static JsonNode object(JsonNode value, String field) {
    JsonNode object = value.get(field);
    if (object == null || !object.isObject()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return object;
  }

  private static String text(JsonNode value, String field) {
    return textual(value.get(field));
  }

  private static String nullableText(JsonNode value, String field, String fallback) {
    JsonNode candidate = value.get(field);
    return candidate == null || candidate.isNull() ? fallback : textual(candidate);
  }

  private static String textual(JsonNode value) {
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return value.textValue();
  }

  private static int positiveInt(JsonNode value, String field) {
    JsonNode candidate = value.get(field);
    if (candidate == null || !candidate.canConvertToInt() || candidate.intValue() < 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return candidate.intValue();
  }

  private static String boundedExcerpt(String value) {
    return bounded(value, 96);
  }

  private static String boundedText(String value) {
    return bounded(value, 96);
  }

  private static String bounded(String value, int maximumLength) {
    return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
  }

  private static String required(String value) {
    if (value == null || value.isBlank()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return value;
  }

  private static <T> T required(T value) {
    if (value == null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return value;
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException("SHA-256 unavailable", missing);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return java.nio.ByteBuffer.allocate(Long.BYTES + value.length)
        .order(java.nio.ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(first.length, second.length);
  }

  private static BusinessProcessTaskCompilationException failure(String code) {
    return new BusinessProcessTaskCompilationException(code);
  }

  private static BusinessProcessTaskCompilationException failure(String code, Throwable cause) {
    return new BusinessProcessTaskCompilationException(code, cause);
  }

  private record PacketProjection(
      ProcessModelPacketV1 packet,
      List<ReaderKeyBindingV1> bindings,
      Map<String, UpstreamGapAccumulator> limitationUpstreams) {}

  private record AtomicUnit(
      List<String> ownerCandidateRelationIds, List<String> contextFlowSliceIds) {
    private AtomicUnit {
      ownerCandidateRelationIds = sortedDistinct(ownerCandidateRelationIds);
      contextFlowSliceIds = sortedDistinct(contextFlowSliceIds);
      if (contextFlowSliceIds.isEmpty()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
  }

  private record Selection(
      List<JsonNode> flowViews,
      List<JsonNode> relations,
      List<JsonNode> registryItems,
      int signalCount) {}

  private record BudgetExcess(String limitKind, int configuredLimit, int observedValue) {}

  private record GroupCompilation(
      List<BusinessProcessTaskShardV1> taskShards, List<ProcessInterpretationGapV1> processGaps) {}

  private record UpstreamGapAccumulator(JsonNode gapView, List<String> sourceFlowSliceIds) {}

  private record NoModelShardProjection(
      BusinessProcessTaskShardV1 shard, List<ProcessInterpretationGapV1> processGaps) {}

  private record SafeShardProjection(
      BusinessProcessTaskShardV1 shard, List<ProcessInterpretationGapV1> processGaps) {}

  private static final class KeySequence {
    private final Map<String, Integer> counters = new HashMap<>();

    private String next(String prefix) {
      int value = counters.merge(prefix, 1, Integer::sum);
      return prefix + String.format(Locale.ROOT, "%02d", value);
    }
  }
}
