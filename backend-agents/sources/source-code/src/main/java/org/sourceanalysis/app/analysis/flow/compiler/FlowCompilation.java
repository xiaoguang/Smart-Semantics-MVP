package org.sourceanalysis.app.analysis.flow.compiler;

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
import java.util.Set;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Immutable M1 result before its publisher writes the flow-compilation module artifact. */
public record FlowCompilation(
    FlowCompilationProfile profile,
    List<EntryDisposition> entryDispositions,
    List<FlowSlice> flowSlices,
    List<EntryContext> entryContexts,
    List<FlowGap> flowGaps) {

  /**
   * Compatibility constructor for unit fixtures that predate persisted entry context. Production
   * compilation always supplies a real context per discovered entry.
   */
  public FlowCompilation(
      FlowCompilationProfile profile,
      List<EntryDisposition> entryDispositions,
      List<FlowSlice> flowSlices,
      List<FlowGap> flowGaps) {
    this(profile, entryDispositions, flowSlices, legacyEntryContexts(entryDispositions), flowGaps);
  }

  public FlowCompilation {
    profile = Objects.requireNonNull(profile, "Flow compilation profile");
    entryDispositions =
        List.copyOf(ordered(entryDispositions, EntryDisposition::entryId, "entry dispositions"));
    flowSlices = List.copyOf(ordered(flowSlices, FlowSlice::flowSliceId, "Flow slices"));
    entryContexts = List.copyOf(ordered(entryContexts, EntryContext::entryId, "entry contexts"));
    flowGaps = List.copyOf(ordered(flowGaps, FlowGap::gapId, "Flow Gaps"));
    List<EntryDisposition> orderedDispositions = entryDispositions;
    if (entryDispositions.stream().map(EntryDisposition::entryId).distinct().count()
        != entryDispositions.size()) {
      throw broken();
    }
    List<String> compiledFlowIds =
        entryDispositions.stream()
            .filter(value -> "COMPILED".equals(value.disposition()))
            .map(EntryDisposition::flowSliceId)
            .toList();
    List<String> knownGapIds = flowGaps.stream().map(FlowGap::gapId).toList();
    Set<String> knownFlowFactIds =
        flowSlices.stream()
            .flatMap(flow -> flow.factIds().stream())
            .collect(java.util.stream.Collectors.toSet());
    if (compiledFlowIds.contains(null)
        || compiledFlowIds.size() != flowSlices.size()
        || compiledFlowIds.size() != compiledFlowIds.stream().distinct().count()
        || !hasOneMatchingDispositionPerFlow(flowSlices, entryDispositions)
        || entryDispositions.stream()
            .flatMap(disposition -> disposition.gapIds().stream())
            .anyMatch(gapId -> !knownGapIds.contains(gapId))
        || flowSlices.stream()
            .flatMap(flow -> flow.gapIds().stream())
            .anyMatch(gapId -> !knownGapIds.contains(gapId))
        || flowGaps.stream()
            .flatMap(gap -> gap.affectedEntryIds().stream())
            .anyMatch(
                entryId ->
                    orderedDispositions.stream()
                        .noneMatch(disposition -> disposition.entryId().equals(entryId)))
        || entryContexts.size() != entryDispositions.size()
        || entryContexts.stream()
            .anyMatch(
                context ->
                    orderedDispositions.stream()
                        .noneMatch(
                            disposition ->
                                disposition.entryId().equals(context.entryId())
                                    && Objects.equals(
                                        disposition.flowSliceId(), context.flowSliceId())))
        || entryContexts.stream()
            .flatMap(context -> context.factIds().stream())
            .anyMatch(factId -> !knownFlowFactIds.contains(factId))
        || entryContexts.stream()
            .flatMap(context -> context.gapIds().stream())
            .anyMatch(gapId -> !knownGapIds.contains(gapId))) {
      throw broken();
    }
  }

  private static List<EntryContext> legacyEntryContexts(List<EntryDisposition> entryDispositions) {
    Objects.requireNonNull(entryDispositions, "entry dispositions");
    return entryDispositions.stream()
        .map(
            disposition ->
                new EntryContext(
                    "entry-context:legacy:" + disposition.entryId(),
                    disposition.entryId(),
                    disposition.flowSliceId(),
                    "legacy-entry-context",
                    "unknown",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    disposition.gapIds(),
                    List.of("LEGACY_CONTEXT_WITHOUT_GRAPH_RELATIONS")))
        .toList();
  }

  /** One complete, unique terminal disposition for an ApplicationDiscovery entry. */
  public record EntryDisposition(
      String entryId,
      String disposition,
      String flowSliceId,
      List<String> gapIds,
      String reasonCode) {

    public EntryDisposition(
        String entryId, String disposition, String flowSliceId, List<String> gapIds) {
      this(entryId, disposition, flowSliceId, gapIds, null);
    }

    public EntryDisposition {
      required(entryId, "entry ID");
      if (!("COMPILED".equals(disposition)
          || "GAP".equals(disposition)
          || "EXCLUDED".equals(disposition))) {
        throw broken();
      }
      gapIds = List.copyOf(orderedStrings(gapIds, "entry Gap IDs"));
      if (("COMPILED".equals(disposition) != (flowSliceId != null))
          || (!"COMPILED".equals(disposition) && gapIds.isEmpty())
          || ("COMPILED".equals(disposition) != (reasonCode == null))) {
        throw broken();
      }
      if (flowSliceId != null) required(flowSliceId, "Flow slice ID");
      if (reasonCode != null) required(reasonCode, "entry disposition reason");
    }
  }

  /**
   * One entry-owned technical narrative for the model-material projector. It is intentionally a
   * relationship record, not a business classification: Step05 says which code calls which code,
   * which values cross that call, which guard matters, and where the source excerpts belong.
   */
  public record EntryContext(
      String entryContextId,
      String entryId,
      String flowSliceId,
      String trigger,
      String entrySignature,
      List<CallContext> calls,
      List<ControlContext> controls,
      List<ReturnContext> returns,
      List<SourceLocatorV1> sourceLocators,
      List<String> factIds,
      List<String> gapIds,
      List<String> limitations) {

    public EntryContext {
      required(entryContextId, "entry context ID");
      required(entryId, "entry context entry ID");
      required(trigger, "entry context trigger");
      required(entrySignature, "entry context entry signature");
      calls = List.copyOf(ordered(calls, CallContext::sortKey, "entry context calls"));
      controls =
          List.copyOf(ordered(controls, ControlContext::controlNodeId, "entry context controls"));
      returns =
          List.copyOf(ordered(returns, ReturnContext::terminalNodeId, "entry context returns"));
      sourceLocators =
          List.copyOf(
              Objects.requireNonNull(sourceLocators, "entry context source locators").stream()
                  .peek(value -> Objects.requireNonNull(value, "entry context source locator"))
                  .distinct()
                  .sorted(
                      Comparator.comparing(SourceLocatorV1::path)
                          .thenComparingLong(SourceLocatorV1::startByte)
                          .thenComparingLong(SourceLocatorV1::endByteExclusive))
                  .toList());
      factIds = List.copyOf(orderedStrings(factIds, "entry context Fact IDs"));
      gapIds = List.copyOf(orderedStrings(gapIds, "entry context Gap IDs"));
      limitations = List.copyOf(orderedStrings(limitations, "entry context limitations"));
    }
  }

  /** A static call relation, including the exact source expression values that cross it. */
  public record CallContext(
      String callerSignature,
      String targetSignature,
      List<String> argumentExpressions,
      String resolution,
      boolean boundary,
      List<String> factIds,
      List<String> proofIds,
      List<String> evidenceNodeIds) {

    public CallContext {
      required(callerSignature, "call context caller signature");
      required(targetSignature, "call context target signature");
      if (!"EXACT".equals(resolution) && !"UNRESOLVED".equals(resolution)) throw broken();
      argumentExpressions = List.copyOf(Objects.requireNonNull(argumentExpressions, "arguments"));
      if (argumentExpressions.stream().anyMatch(value -> value == null || value.isBlank())) {
        throw broken();
      }
      factIds = List.copyOf(orderedStrings(factIds, "call context Fact IDs"));
      proofIds = List.copyOf(orderedStrings(proofIds, "call context Proof IDs"));
      evidenceNodeIds = List.copyOf(orderedStrings(evidenceNodeIds, "call context evidence IDs"));
      if ("EXACT".equals(resolution) && evidenceNodeIds.isEmpty()) {
        throw broken();
      }
    }

    String sortKey() {
      return callerSignature
          + "\u0000"
          + targetSignature
          + "\u0000"
          + String.join("\u0000", argumentExpressions)
          + "\u0000"
          + String.join("\u0000", evidenceNodeIds);
    }
  }

  /** One source-proven branch condition that can change the local activity path. */
  public record ControlContext(
      String controlNodeId, String ownerSignature, String condition, List<String> evidenceNodeIds) {

    public ControlContext {
      required(controlNodeId, "control context node ID");
      required(ownerSignature, "control context owner signature");
      required(condition, "control context condition");
      evidenceNodeIds =
          List.copyOf(orderedStrings(evidenceNodeIds, "control context evidence IDs"));
      if (evidenceNodeIds.isEmpty()) throw broken();
    }
  }

  /** One terminal return or throw observed on the compiled local flow. */
  public record ReturnContext(
      String terminalNodeId, String terminalKind, List<String> evidenceNodeIds) {

    public ReturnContext {
      required(terminalNodeId, "return context terminal node ID");
      required(terminalKind, "return context terminal kind");
      evidenceNodeIds = List.copyOf(orderedStrings(evidenceNodeIds, "return context evidence IDs"));
      if (evidenceNodeIds.isEmpty()) throw broken();
    }
  }

  /** One explicit reason an entry or a compiled Flow cannot be fully analyzed. */
  public record FlowGap(
      String gapId,
      String scope,
      String reasonCode,
      List<String> affectedEntryIds,
      List<String> evidenceNodeIds) {

    public FlowGap {
      required(gapId, "Flow Gap ID");
      required(scope, "Flow Gap scope");
      required(reasonCode, "Flow Gap reason");
      affectedEntryIds = List.copyOf(orderedStrings(affectedEntryIds, "Flow Gap affected entries"));
      evidenceNodeIds = List.copyOf(orderedStrings(evidenceNodeIds, "Flow Gap evidence IDs"));
      if (affectedEntryIds.isEmpty()) throw broken();
    }
  }

  /** One entry-rooted Flow, which may contain several terminal outcomes but never several roots. */
  public record FlowSlice(
      String flowSliceId,
      String entryId,
      String trigger,
      String rootNodeId,
      List<String> sharedSteps,
      List<String> factIds,
      List<String> atomIds,
      List<OutcomePath> outcomePaths,
      List<String> gapIds,
      List<ProcessJoinSignalV1> processJoinSignals) {

    public FlowSlice {
      required(flowSliceId, "Flow slice ID");
      required(entryId, "entry ID");
      required(trigger, "Flow trigger");
      required(rootNodeId, "Flow root node ID");
      sharedSteps = List.copyOf(Objects.requireNonNull(sharedSteps, "shared steps"));
      factIds = List.copyOf(orderedStrings(factIds, "Fact IDs"));
      atomIds = List.copyOf(orderedStrings(atomIds, "atom IDs"));
      outcomePaths =
          List.copyOf(ordered(outcomePaths, OutcomePath::outcomePathId, "outcome paths"));
      gapIds = List.copyOf(orderedStrings(gapIds, "Flow Gap IDs"));
      processJoinSignals =
          List.copyOf(
              ordered(
                  processJoinSignals,
                  ProcessJoinSignalV1::processJoinSignalId,
                  "process-join signals"));
      if (processJoinSignals.stream()
          .anyMatch(signal -> !flowSliceId.equals(signal.flowSliceId()))) {
        throw broken();
      }
      if (outcomePaths.isEmpty()) throw broken();
    }
  }

  /** One proof-closed, single-Flow comparison material record for later process reconstruction. */
  public record ProcessJoinSignalV1(
      String processJoinSignalId,
      String flowSliceId,
      String signalKind,
      String anchorKind,
      String anchorKey,
      String direction,
      String specificity,
      String claimScope,
      List<String> factIds,
      List<String> atomIds,
      List<String> proofIds,
      List<String> evidenceNodeIds,
      List<SourceLocatorV1> sourceLocators,
      List<String> gapIds) {

    private static final Set<String> SIGNAL_KINDS =
        Set.of(
            "BUSINESS_OBJECT_ANCHOR",
            "JAVA_TYPE_ANCHOR",
            "SQL_TABLE_ANCHOR",
            "FIELD_ANCHOR",
            "BUSINESS_IDENTIFIER_ANCHOR",
            "IDENTIFIER_OUTPUT",
            "IDENTIFIER_INPUT",
            "STATE_PRODUCTION",
            "STATE_CHECK",
            "EXPLICIT_CALL",
            "RETURN_TRANSFER",
            "EVENT_REFERENCE",
            "OBJECT_REFERENCE",
            "COUNTER_CONDITION",
            "CONFLICT_STATE",
            "EXTERNAL_EFFECT_GAP");
    private static final Set<String> ANCHOR_KINDS =
        Set.of(
            "BUSINESS_OBJECT",
            "JAVA_TYPE",
            "SQL_TABLE",
            "FIELD",
            "BUSINESS_IDENTIFIER",
            "CALL_TARGET",
            "RETURN_VALUE",
            "EVENT",
            "STATE",
            "CONDITION",
            "GAP");
    private static final Set<String> DIRECTIONS =
        Set.of(
            "PRODUCES",
            "CONSUMES",
            "CHECKS",
            "REFERENCES",
            "INVOKES",
            "RETURNS",
            "BLOCKS",
            "UNKNOWN");
    private static final Set<String> SPECIFICITIES = Set.of("DOMAIN_SPECIFIC", "GENERIC_TECHNICAL");
    private static final Set<String> CLAIM_SCOPES =
        Set.of("FROZEN_JAVA", "STATIC_STRUCTURE", "GAP_ONLY");
    private static final Set<String> POSITIVE_SIGNAL_KINDS =
        Set.of(
            "BUSINESS_OBJECT_ANCHOR",
            "JAVA_TYPE_ANCHOR",
            "SQL_TABLE_ANCHOR",
            "FIELD_ANCHOR",
            "BUSINESS_IDENTIFIER_ANCHOR",
            "IDENTIFIER_OUTPUT",
            "IDENTIFIER_INPUT",
            "STATE_PRODUCTION",
            "STATE_CHECK",
            "EXPLICIT_CALL",
            "RETURN_TRANSFER",
            "EVENT_REFERENCE",
            "OBJECT_REFERENCE");
    private static final Comparator<SourceLocatorV1> SOURCE_LOCATOR_ORDER =
        Comparator.comparing(SourceLocatorV1::path)
            .thenComparingLong(SourceLocatorV1::startByte)
            .thenComparingLong(SourceLocatorV1::endByteExclusive);

    public ProcessJoinSignalV1 {
      required(flowSliceId, "process-join signal Flow slice ID");
      requireMember(signalKind, SIGNAL_KINDS, "process-join signal kind");
      requireMember(anchorKind, ANCHOR_KINDS, "process-join signal anchor kind");
      required(anchorKey, "process-join signal anchor key");
      requireMember(direction, DIRECTIONS, "process-join signal direction");
      requireMember(specificity, SPECIFICITIES, "process-join signal specificity");
      requireMember(claimScope, CLAIM_SCOPES, "process-join signal claim scope");
      factIds = List.copyOf(orderedStrings(factIds, "process-join signal Fact IDs"));
      atomIds = List.copyOf(orderedStrings(atomIds, "process-join signal atom IDs"));
      proofIds = List.copyOf(orderedStrings(proofIds, "process-join signal Proof IDs"));
      evidenceNodeIds =
          List.copyOf(orderedStrings(evidenceNodeIds, "process-join signal evidence node IDs"));
      sourceLocators = List.copyOf(orderedLocators(sourceLocators));
      gapIds = List.copyOf(orderedStrings(gapIds, "process-join signal Gap IDs"));
      boolean positive = POSITIVE_SIGNAL_KINDS.contains(signalKind);
      boolean counter =
          "COUNTER_CONDITION".equals(signalKind)
              || "CONFLICT_STATE".equals(signalKind)
              || "EXTERNAL_EFFECT_GAP".equals(signalKind);
      if ((positive && "BLOCKS".equals(direction))
          || (counter && !"BLOCKS".equals(direction))
          || (positive
              && (factIds.isEmpty()
                  || atomIds.isEmpty()
                  || proofIds.isEmpty()
                  || evidenceNodeIds.isEmpty()
                  || sourceLocators.isEmpty()))
          || ("EXTERNAL_EFFECT_GAP".equals(signalKind) && gapIds.isEmpty())) {
        throw broken();
      }
      String calculatedId =
          signalId(
              flowSliceId,
              signalKind,
              anchorKind,
              anchorKey,
              direction,
              specificity,
              claimScope,
              factIds,
              atomIds,
              proofIds,
              evidenceNodeIds,
              sourceLocators,
              gapIds);
      if (processJoinSignalId != null && !processJoinSignalId.equals(calculatedId)) throw broken();
      processJoinSignalId = calculatedId;
    }

    static ProcessJoinSignalV1 create(
        String flowSliceId,
        String signalKind,
        String anchorKind,
        String anchorKey,
        String direction,
        String specificity,
        String claimScope,
        List<String> factIds,
        List<String> atomIds,
        List<String> proofIds,
        List<String> evidenceNodeIds,
        List<SourceLocatorV1> sourceLocators,
        List<String> gapIds) {
      return new ProcessJoinSignalV1(
          null,
          flowSliceId,
          signalKind,
          anchorKind,
          anchorKey,
          direction,
          specificity,
          claimScope,
          factIds,
          atomIds,
          proofIds,
          evidenceNodeIds,
          sourceLocators,
          gapIds);
    }

    private static List<SourceLocatorV1> orderedLocators(List<SourceLocatorV1> values) {
      Objects.requireNonNull(values, "process-join signal source locators");
      List<SourceLocatorV1> ordered =
          values.stream()
              .peek(value -> Objects.requireNonNull(value, "process-join source locator"))
              .sorted(SOURCE_LOCATOR_ORDER)
              .toList();
      if (ordered.size() != ordered.stream().distinct().count()) throw broken();
      return ordered;
    }

    private static void requireMember(String value, Set<String> allowed, String label) {
      required(value, label);
      if (!allowed.contains(value)) throw broken();
    }

    private static String signalId(
        String flowSliceId,
        String signalKind,
        String anchorKind,
        String anchorKey,
        String direction,
        String specificity,
        String claimScope,
        List<String> factIds,
        List<String> atomIds,
        List<String> proofIds,
        List<String> evidenceNodeIds,
        List<SourceLocatorV1> sourceLocators,
        List<String> gapIds) {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.put("flowSliceId", flowSliceId);
      value.put("signalKind", signalKind);
      value.put("anchorKind", anchorKind);
      value.put("anchorKey", anchorKey);
      value.put("direction", direction);
      value.put("specificity", specificity);
      value.put("claimScope", claimScope);
      strings(value.putArray("factIds"), factIds);
      strings(value.putArray("atomIds"), atomIds);
      strings(value.putArray("proofIds"), proofIds);
      strings(value.putArray("evidenceNodeIds"), evidenceNodeIds);
      ArrayNode locators = value.putArray("sourceLocators");
      for (SourceLocatorV1 locator : sourceLocators) {
        locators
            .addObject()
            .put("fileId", locator.fileId().value())
            .put("path", locator.path())
            .put("startByte", locator.startByte())
            .put("endByteExclusive", locator.endByteExclusive())
            .put("startLine", locator.startLine())
            .put("startColumn", locator.startColumn())
            .put("endLine", locator.endLine())
            .put("endColumn", locator.endColumn());
      }
      strings(value.putArray("gapIds"), gapIds);
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(frame("business-flows-process-join-signal-id-v1"));
        digest.update(frame(new CanonicalJsonCodec().encodeCanonical(value).copyToByteArray()));
        return "process-join-signal:" + HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException unavailable) {
        throw new IllegalStateException("SHA-256 is unavailable", unavailable);
      }
    }

    private static void strings(ArrayNode destination, List<String> values) {
      values.forEach(destination::add);
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

  /** One terminal path inside the owning Flow. */
  public record OutcomePath(
      String outcomePathId,
      List<BranchDecision> decisions,
      String terminalNodeId,
      String terminalKind,
      List<String> terminalFactIds,
      List<String> requiredAtomIds,
      List<String> requiredProofIds) {

    public OutcomePath {
      required(outcomePathId, "outcome path ID");
      decisions = List.copyOf(Objects.requireNonNull(decisions, "branch decisions"));
      required(terminalNodeId, "terminal node ID");
      required(terminalKind, "terminal kind");
      terminalFactIds = List.copyOf(orderedStrings(terminalFactIds, "terminal Fact IDs"));
      requiredAtomIds = List.copyOf(orderedStrings(requiredAtomIds, "required atom IDs"));
      requiredProofIds = List.copyOf(orderedStrings(requiredProofIds, "required proof IDs"));
    }
  }

  /** One explicit control-flow guard and its true/false branch polarity. */
  public record BranchDecision(
      String guardNodeId, String conditionAtomId, String polarity, String normalizedCondition) {

    public BranchDecision {
      required(guardNodeId, "guard node ID");
      required(conditionAtomId, "condition atom ID");
      if (!"TRUE".equals(polarity) && !"FALSE".equals(polarity)) throw broken();
      required(normalizedCondition, "normalized condition");
    }
  }

  private static <T> List<T> ordered(
      List<T> values, java.util.function.Function<T, String> key, String label) {
    Objects.requireNonNull(values, label);
    List<T> ordered = values.stream().sorted(Comparator.comparing(key)).toList();
    if (ordered.size() != ordered.stream().map(key).distinct().count()) throw broken();
    return ordered;
  }

  private static boolean hasOneMatchingDispositionPerFlow(
      List<FlowSlice> flowSlices, List<EntryDisposition> entryDispositions) {
    return flowSlices.stream()
        .allMatch(
            flow ->
                entryDispositions.stream()
                    .anyMatch(
                        disposition ->
                            flow.entryId().equals(disposition.entryId())
                                && flow.flowSliceId().equals(disposition.flowSliceId())));
  }

  private static List<String> orderedStrings(List<String> values, String label) {
    Objects.requireNonNull(values, label);
    List<String> ordered = values.stream().peek(value -> required(value, label)).sorted().toList();
    if (ordered.size() != ordered.stream().distinct().count()) throw broken();
    return ordered;
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) throw broken();
  }

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("FLOW_ACCOUNTING_INVARIANT_BROKEN");
  }
}
