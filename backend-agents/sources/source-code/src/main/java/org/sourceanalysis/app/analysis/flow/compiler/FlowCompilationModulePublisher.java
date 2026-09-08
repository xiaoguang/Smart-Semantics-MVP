package org.sourceanalysis.app.analysis.flow.compiler;

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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
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

/**
 * Installs M1's sole canonical FlowCompilation payload after reopening every public predecessor.
 */
public final class FlowCompilationModulePublisher {

  private static final String FILE_NAME = "flow-compilation.json";
  private static final String ARTIFACT_TYPE = "BUSINESS_FLOWS_FLOW_COMPILATION";
  private static final String SCHEMA_VERSION = "business-flows-flow-compilation-v1";
  private static final String ARTIFACT_PREFIX = "business-flows-flow-compilation";
  private static final String MODULE_VERSION = "v1";
  private static final Comparator<String> UTF8_ORDER =
      (left, right) -> {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < Math.min(leftBytes.length, rightBytes.length); index++) {
          int compared =
              Integer.compare(
                  Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
          if (compared != 0) return compared;
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
      };

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final PersistedFlowCompilationInputReader inputs;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the M1 publisher with receipt-last module storage and its public-input reader. */
  public FlowCompilationModulePublisher(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    inputs = new PersistedFlowCompilationInputReader(analysisSteps);
  }

  /**
   * Reopens the same predecessor wire used by the compiler, checks denominator closure, then
   * installs the only M1 module payload. It never reconstructs a Flow from source or graphs.
   */
  public ModulePublicationReference publish(
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs,
      ProvenCodeFactsReference facts,
      FlowCompilation compilation) {
    Objects.requireNonNull(compilation, "Flow compilation");
    PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened =
        inputs.reopen(discovery, graphs, facts);
    requireCompilationMatchesInputs(compilation, reopened);
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            graphs.publication().address().runId(),
            AnalysisStepKey.BUSINESS_FLOWS,
            1,
            "flow-compiler");
    List<String> gapIds =
        compilation.entryDispositions().stream()
            .flatMap(value -> value.gapIds().stream())
            .sorted(UTF8_ORDER)
            .distinct()
            .toList();
    ModuleCompletionStatus status =
        gapIds.isEmpty()
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
    CanonicalModulePayload payload = payload(address, compilation, reopened, status, gapIds);
    InstalledModulePublication publication =
        moduleArtifacts.install(
            new ModuleInstallRequest(
                address,
                MODULE_VERSION,
                reopened.upstreamArtifacts(),
                reopened.controls(),
                status,
                gapIds,
                List.of(payload)));
    return publication.reference();
  }

  private void requireCompilationMatchesInputs(
      FlowCompilation compilation,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    List<String> expectedEntryIds =
        reopened.entries().stream()
            .map(PersistedFlowCompilationInputReader.FlowEntry::entryId)
            .toList();
    List<String> actualEntryIds =
        compilation.entryDispositions().stream()
            .map(FlowCompilation.EntryDisposition::entryId)
            .toList();
    if (!expectedEntryIds.equals(actualEntryIds)) {
      throw broken();
    }
    java.util.Map<String, FlowCompilation.FlowSlice> flowsById =
        compilation.flowSlices().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    FlowCompilation.FlowSlice::flowSliceId, value -> value));
    Set<String> knownGapIds =
        compilation.flowGaps().stream()
            .map(FlowCompilation.FlowGap::gapId)
            .collect(java.util.stream.Collectors.toSet());
    for (FlowCompilation.EntryDisposition disposition : compilation.entryDispositions()) {
      FlowCompilation.FlowSlice flow =
          disposition.flowSliceId() == null ? null : flowsById.get(disposition.flowSliceId());
      if (("COMPILED".equals(disposition.disposition())
              && (flow == null || !flow.entryId().equals(disposition.entryId())))
          || (!"COMPILED".equals(disposition.disposition()) && flow != null)
          || disposition.gapIds().stream().anyMatch(gapId -> !knownGapIds.contains(gapId))) {
        throw broken();
      }
    }
    for (FlowCompilation.FlowSlice flow : compilation.flowSlices()) {
      List<String> expectedFacts =
          reopened.factsByEntry().getOrDefault(flow.entryId(), List.of()).stream()
              .map(PersistedFlowCompilationInputReader.PersistedFact::factId)
              .sorted(UTF8_ORDER)
              .toList();
      List<String> expectedGaps =
          reopened.gapsByEntry().getOrDefault(flow.entryId(), List.of()).stream()
              .map(PersistedFlowCompilationInputReader.PersistedGap::gapId)
              .sorted(UTF8_ORDER)
              .toList();
      if (!expectedFacts.equals(flow.factIds()) || !expectedGaps.equals(flow.gapIds()))
        throw broken();
    }
    Map<String, PersistedFlowCompilationInputReader.PersistedGap> sourceGaps = new HashMap<>();
    reopened.gapsByEntry().values().stream()
        .flatMap(List::stream)
        .forEach(
            gap -> {
              if (sourceGaps.put(gap.gapId(), gap) != null) throw broken();
            });
    Map<String, FlowCompilation.FlowGap> compilationGaps = new HashMap<>();
    compilation
        .flowGaps()
        .forEach(
            gap -> {
              if (compilationGaps.put(gap.gapId(), gap) != null) throw broken();
            });
    for (PersistedFlowCompilationInputReader.PersistedGap sourceGap : sourceGaps.values()) {
      FlowCompilation.FlowGap actual = compilationGaps.get(sourceGap.gapId());
      if (actual == null
          || !"FLOW".equals(actual.scope())
          || !sourceGap.code().equals(actual.reasonCode())
          || !sourceGap.affectedEntryIds().equals(actual.affectedEntryIds())
          || !sourceGap.evidenceNodeIds().equals(actual.evidenceNodeIds())) {
        throw broken();
      }
    }
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      FlowCompilation compilation,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened,
      ModuleCompletionStatus status,
      List<String> gapIds) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("flowCompilationId", flowCompilationId(compilation));
    profile(body.putObject("flowCompilationProfile"), compilation.profile());
    ArrayNode dispositions = body.putArray("entryDispositions");
    compilation.entryDispositions().forEach(value -> disposition(dispositions.addObject(), value));
    ArrayNode flows = body.putArray("flowSlices");
    compilation.flowSlices().forEach(value -> flow(flows.addObject(), value));
    ArrayNode gaps = body.putArray("flowGaps");
    compilation.flowGaps().forEach(value -> gap(gaps.addObject(), value));
    shard(body.putArray("entryShardReceipts").addObject(), compilation);
    coverage(body.putObject("coverage"), compilation, reopened);
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(reopened.upstreamArtifacts()));
    withoutArtifactId.set("controls", controls(reopened.controls()));
    withoutArtifactId.set("completion", completion(status, gapIds));
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

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", address.runId().value());
    producerAddress.put("analysisStepKey", address.analysisStepKey().wireValue());
    producerAddress.put("moduleNumber", address.moduleNumber());
    producerAddress.put("moduleKey", address.moduleKey());
    producer.put("moduleVersion", MODULE_VERSION);
    return producer;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(
        value ->
            result
                .addObject()
                .put("artifactId", value.artifactId().value())
                .put("sha256", value.sha256().value()));
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

  private static ObjectNode completion(ModuleCompletionStatus status, List<String> gapIds) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", status.name());
    strings(result.putArray("gapRefs"), gapIds);
    result.putNull("failureRef");
    return result;
  }

  private static void profile(ObjectNode node, FlowCompilationProfile profile) {
    node.putObject("profileRef")
        .put("artifactId", profile.profileRef().artifactId().value())
        .put("sha256", profile.profileRef().sha256().value());
    node.put("maxFlows", profile.maxFlows());
    node.put("maxOutcomesPerFlow", profile.maxOutcomesPerFlow());
    node.put("maxFlowNodes", profile.maxFlowNodes());
    node.put("maxFlowEdges", profile.maxFlowEdges());
    node.put("maxTraversalDepth", profile.maxTraversalDepth());
  }

  private static void disposition(ObjectNode node, FlowCompilation.EntryDisposition value) {
    node.put("entryId", value.entryId());
    node.put("disposition", value.disposition());
    if (value.flowSliceId() == null) node.putNull("flowSliceId");
    else node.put("flowSliceId", value.flowSliceId());
    strings(node.putArray("gapIds"), value.gapIds());
    if (value.reasonCode() == null) node.putNull("reasonCode");
    else node.put("reasonCode", value.reasonCode());
    node.putArray("evidenceRefs");
  }

  private static void flow(ObjectNode node, FlowCompilation.FlowSlice value) {
    node.put("flowSliceId", value.flowSliceId());
    node.put("entryId", value.entryId());
    node.put("trigger", value.trigger());
    node.put("rootNodeId", value.rootNodeId());
    strings(node.putArray("sharedSteps"), value.sharedSteps());
    strings(node.putArray("factIds"), value.factIds());
    strings(node.putArray("atomIds"), value.atomIds());
    ArrayNode outcomes = node.putArray("outcomePaths");
    value.outcomePaths().forEach(outcome -> outcome(outcomes.addObject(), outcome));
    strings(node.putArray("gapIds"), value.gapIds());
    node.putNull("parentFlowSliceId");
    node.putArray("childFlowSliceIds");
  }

  private static void gap(ObjectNode node, FlowCompilation.FlowGap value) {
    node.put("gapId", value.gapId());
    node.put("scope", value.scope());
    node.put("reasonCode", value.reasonCode());
    strings(node.putArray("affectedSemanticIds"), value.affectedEntryIds());
    strings(node.putArray("evidenceNodeIds"), value.evidenceNodeIds());
  }

  private static void outcome(ObjectNode node, FlowCompilation.OutcomePath value) {
    node.put("outcomePathId", value.outcomePathId());
    ArrayNode decisions = node.putArray("decisions");
    value.decisions().forEach(decision -> decision(decisions.addObject(), decision));
    node.put("terminalNodeId", value.terminalNodeId());
    node.put("terminalKind", value.terminalKind());
    strings(node.putArray("terminalFactIds"), value.terminalFactIds());
    strings(node.putArray("requiredAtomIds"), value.requiredAtomIds());
    strings(node.putArray("requiredProofIds"), value.requiredProofIds());
  }

  private static void decision(ObjectNode node, FlowCompilation.BranchDecision value) {
    node.put("guardNodeId", value.guardNodeId());
    node.put("conditionAtomId", value.conditionAtomId());
    node.put("polarity", value.polarity());
    node.put("normalizedCondition", value.normalizedCondition());
  }

  private static void shard(ObjectNode node, FlowCompilation compilation) {
    node.put("shardId", contentId("flow-entry-shard", "all-entries"));
    strings(
        node.putArray("denominatorEntryIds"),
        compilation.entryDispositions().stream()
            .map(FlowCompilation.EntryDisposition::entryId)
            .toList());
    strings(
        node.putArray("dispositionEntryIds"),
        compilation.entryDispositions().stream()
            .map(FlowCompilation.EntryDisposition::entryId)
            .toList());
    strings(
        node.putArray("flowSliceIds"),
        compilation.flowSlices().stream().map(FlowCompilation.FlowSlice::flowSliceId).toList());
    node.put("status", "COMPLETE");
    strings(
        node.putArray("gapIds"),
        compilation.entryDispositions().stream()
            .flatMap(value -> value.gapIds().stream())
            .distinct()
            .sorted(UTF8_ORDER)
            .toList());
  }

  private static void coverage(
      ObjectNode node,
      FlowCompilation compilation,
      PersistedFlowCompilationInputReader.PersistedFlowCompilationInputs reopened) {
    List<String> entryIds =
        compilation.entryDispositions().stream()
            .map(FlowCompilation.EntryDisposition::entryId)
            .toList();
    strings(node.putArray("entryIds"), entryIds);
    strings(
        node.putArray("compiledEntryIds"),
        compilation.entryDispositions().stream()
            .filter(value -> "COMPILED".equals(value.disposition()))
            .map(FlowCompilation.EntryDisposition::entryId)
            .toList());
    strings(
        node.putArray("gappedEntryIds"),
        compilation.entryDispositions().stream()
            .filter(value -> "GAP".equals(value.disposition()))
            .map(FlowCompilation.EntryDisposition::entryId)
            .toList());
    strings(
        node.putArray("excludedEntryIds"),
        compilation.entryDispositions().stream()
            .filter(value -> "EXCLUDED".equals(value.disposition()))
            .map(FlowCompilation.EntryDisposition::entryId)
            .toList());
    strings(
        node.putArray("flowSliceIds"),
        compilation.flowSlices().stream().map(FlowCompilation.FlowSlice::flowSliceId).toList());
    strings(
        node.putArray("outcomePathIds"),
        compilation.flowSlices().stream()
            .flatMap(value -> value.outcomePaths().stream())
            .map(FlowCompilation.OutcomePath::outcomePathId)
            .sorted(UTF8_ORDER)
            .toList());
    strings(
        node.putArray("factIds"),
        compilation.flowSlices().stream()
            .flatMap(value -> value.factIds().stream())
            .sorted(UTF8_ORDER)
            .toList());
    strings(
        node.putArray("atomIds"),
        compilation.flowSlices().stream()
            .flatMap(value -> value.atomIds().stream())
            .sorted(UTF8_ORDER)
            .toList());
    strings(
        node.putArray("gapIds"),
        compilation.flowGaps().stream()
            .map(FlowCompilation.FlowGap::gapId)
            .sorted(UTF8_ORDER)
            .toList());
    node.put("closed", true);
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
  }

  private static String flowCompilationId(FlowCompilation compilation) {
    List<String> values = new ArrayList<>();
    values.add(compilation.profile().profileRef().artifactId().value());
    values.add(compilation.profile().profileRef().sha256().value());
    compilation
        .entryDispositions()
        .forEach(
            value -> {
              values.add(value.entryId());
              values.add(value.disposition());
              values.add(value.flowSliceId() == null ? "" : value.flowSliceId());
              values.add(value.reasonCode() == null ? "" : value.reasonCode());
              values.addAll(value.gapIds());
            });
    compilation
        .flowGaps()
        .forEach(
            value -> {
              values.add(value.gapId());
              values.add(value.scope());
              values.add(value.reasonCode());
              values.addAll(value.affectedEntryIds());
              values.addAll(value.evidenceNodeIds());
            });
    return contentId("flow-compilation", values.toArray(String[]::new));
  }

  private static String contentId(String prefix, String... values) {
    byte[][] framed = new byte[values.length + 1][];
    framed[0] = frame(prefix);
    for (int index = 0; index < values.length; index++) {
      framed[index + 1] = frame(values[index]);
    }
    return prefix + ":" + sha256(framed);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
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
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("FLOW_ACCOUNTING_INVARIANT_BROKEN");
  }
}
