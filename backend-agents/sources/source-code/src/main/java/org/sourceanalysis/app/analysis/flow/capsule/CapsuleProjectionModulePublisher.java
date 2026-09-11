package org.sourceanalysis.app.analysis.flow.capsule;

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
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
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
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Installs M2's complete evidence-capsule projection as one canonical module artifact. */
public final class CapsuleProjectionModulePublisher {

  private static final String FILE_NAME = "capsule-projection.json";
  private static final String ARTIFACT_TYPE = "BUSINESS_FLOWS_CAPSULE_PROJECTION";
  private static final String SCHEMA_VERSION = "business-flows-capsule-projection-v9";
  private static final String ARTIFACT_PREFIX = "business-flows-capsule-projection";
  private static final String PROJECTION_ID_DOMAIN = "business-flows-capsule-projection-id-v2";
  private static final String MODULE_VERSION = "v6";
  private static final Comparator<String> UTF8_ORDER =
      CapsuleProjectionModulePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates M2's receipt-last publisher. Projection is performed once by the owning projector. */
  public CapsuleProjectionModulePublisher(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader sourceReader) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
    Objects.requireNonNull(sourceReader, "verified source reader");
  }

  /**
   * Checks the exact persisted inputs and installs the one already-projected M2 payload. The
   * ordinary path never projects a second time.
   */
  public ModulePublicationReference publish(
      ModulePublicationReference flowCompilation,
      VerifiedSourceInventoryReference source,
      ProgramGraphsReference graphs,
      ProvenCodeFactsReference facts,
      CapsuleProjection projection) {
    try {
      Objects.requireNonNull(projection, "capsule projection");
      requireProjectionIntegrity(projection);
      ReopenedAnalysisStepPublication sourceStep = analysisSteps.reopen(source.publication());
      ReopenedAnalysisStepPublication graphStep = analysisSteps.reopen(graphs.publication());
      ReopenedAnalysisStepPublication factStep = analysisSteps.reopen(facts.publication());
      ArtifactControls controls = sourceStep.receipt().controls();
      if (!controls.equals(graphStep.receipt().controls())
          || !controls.equals(factStep.receipt().controls())) throw failure();
      List<ArtifactReference> upstream =
          upstream(flowCompilation, sourceStep, graphStep, factStep, projection);
      List<String> gapRefs = modelIneligibilityGapRefs(projection);
      ModuleCompletionStatus completionStatus =
          gapRefs.isEmpty()
              ? ModuleCompletionStatus.SUCCEEDED
              : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              source.publication().address().runId(),
              AnalysisStepKey.BUSINESS_FLOWS,
              2,
              "capsule-projector");
      CanonicalModulePayload payload =
          payload(address, upstream, controls, projection, completionStatus, gapRefs);
      InstalledModulePublication publication =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstream,
                  controls,
                  completionStatus,
                  gapRefs,
                  List.of(payload)));
      return publication.reference();
    } catch (CapsuleProjectionException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure();
    }
  }

  /**
   * Checks the submitted projection's own closure without rerunning its owner algorithm. In
   * particular, a source span is flow-rooted: a caller cannot move it to another Capsule merely
   * because both capsules happen to cite the same frozen evidence node.
   */
  private static void requireProjectionIntegrity(CapsuleProjection projection) {
    Map<String, CapsuleProjection.ModelEvidenceSpan> spansById = new HashMap<>();
    for (CapsuleProjection.ModelEvidenceSpan span : projection.modelEvidenceSpans()) {
      if (spansById.put(span.spanId(), span) != null) throw failure();
    }
    for (CapsuleProjection.EvidenceCapsule capsule : projection.capsules()) {
      Set<String> capsuleSpanIds = new HashSet<>(capsule.modelEvidenceSpanIds());
      if (capsuleSpanIds.size() != capsule.modelEvidenceSpanIds().size()
          || !spansById.keySet().containsAll(capsuleSpanIds)) {
        throw failure();
      }
      for (String spanId : capsuleSpanIds) {
        CapsuleProjection.ModelEvidenceSpan span = spansById.get(spanId);
        if (!flowRootedSpanId(capsule.flowSliceId(), span.evidenceNodeId()).equals(spanId)) {
          throw failure();
        }
      }
    }
  }

  private List<ArtifactReference> upstream(
      ModulePublicationReference compilation,
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts,
      CapsuleProjection projection) {
    List<ArtifactReference> values = new ArrayList<>();
    values.add(projection.flowCompilationRef());
    requireModulePayload(compilation, projection.flowCompilationRef(), "flow-compilation.json");
    values.addAll(
        requiredReferences(source, List.of("source-inventory.jsonl", "verified-snapshot.json")));
    values.addAll(
        requiredReferences(
            graphs,
            List.of(
                "call-graph.json",
                "code-structure-graph.json",
                "control-flow-graph.json",
                "data-flow-graph.json",
                "evidence-graph.json",
                "graph-gaps.jsonl",
                "graph-index.json")));
    values.addAll(
        requiredReferences(
            facts,
            List.of(
                "fact-accounting.json",
                "gap-ledger.json",
                "proof-pack.json",
                "proven-facts.json")));
    List<ArtifactReference> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    if (ordered.size() != 14
        || ordered.size() != ordered.stream().map(ArtifactReference::artifactId).distinct().count()
        || !ordered.contains(projection.proofPackRef())) {
      throw failure();
    }
    return ordered;
  }

  private static void requireModulePayload(
      ModulePublicationReference reference, ArtifactReference expected, String expectedFileName) {
    if (reference == null
        || expected == null
        || !expectedFileName.equals("flow-compilation.json")) {
      throw failure();
    }
    // The projector has just freshly reopened the complete M1 envelope. This check prevents a
    // mismatched caller-provided reference from entering the M2 lineage set.
    if (!reference.address().runId().value().matches("analysis-run:[0-9a-f]{64}")) throw failure();
  }

  private static List<ArtifactReference> requiredReferences(
      ReopenedAnalysisStepPublication publication, List<String> fileNames) {
    List<ArtifactReference> values = new ArrayList<>();
    for (String fileName : fileNames) {
      VerifiedCanonicalPayload payload =
          publication.semanticPayloads().stream()
              .filter(value -> fileName.equals(value.descriptor().fileName()))
              .findFirst()
              .orElseThrow(CapsuleProjectionModulePublisher::failure);
      values.add(
          new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()));
    }
    return values;
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      CapsuleProjection projection,
      ModuleCompletionStatus completionStatus,
      List<String> gapRefs) {
    JsonNode bodyValue = canonicalJson.parseCanonical(canonicalProjectionPayloadBody(projection));
    if (!bodyValue.isObject()) throw failure();
    ObjectNode body = (ObjectNode) bodyValue;

    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(address));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set("completion", completion(completionStatus, gapRefs));
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

  private static ObjectNode projectionBody(CapsuleProjection projection) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.set("flowCompilationRef", reference(projection.flowCompilationRef()));
    body.set("proofPackRef", reference(projection.proofPackRef()));
    profile(body.putObject("capsuleProjectionProfile"), projection.profile());
    ArrayNode capsules = body.putArray("capsules");
    projection.capsules().forEach(value -> capsule(capsules.addObject(), value));
    ArrayNode spans = body.putArray("modelEvidenceSpans");
    projection.modelEvidenceSpans().forEach(value -> span(spans.addObject(), value));
    ArrayNode obligations = body.putArray("projectionObligations");
    projection.projectionObligations().forEach(value -> obligation(obligations.addObject(), value));
    ObjectNode budget = body.putObject("budgetUsage");
    budget.put("spanCount", projection.modelEvidenceSpans().size());
    budget.put(
        "sourceUtf8Bytes",
        projection.modelEvidenceSpans().stream()
            .mapToLong(value -> value.sourceExcerpt().rawUtf8().size())
            .sum());
    return body;
  }

  /** Canonical full M2 payload body shared with the M3 owner replay check. */
  public static ImmutableBytes canonicalProjectionPayloadBody(CapsuleProjection projection) {
    Objects.requireNonNull(projection, "capsule projection");
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    ObjectNode body = projectionBody(projection);
    body.put("capsuleProjectionId", projectionId(body, codec));
    return codec.encodeCanonical(body);
  }

  private static void profile(ObjectNode node, CapsuleProjectionProfile profile) {
    node.set("profileRef", reference(profile.profileRef()));
    node.put("maxCapsules", profile.maxCapsules());
    node.put("maxSpansPerCapsule", profile.maxSpansPerCapsule());
    node.put("maxSpanBytes", profile.maxSpanBytes());
    node.put("maxCapsuleUtf8Bytes", profile.maxCapsuleUtf8Bytes());
  }

  private static void capsule(ObjectNode node, CapsuleProjection.EvidenceCapsule capsule) {
    node.put("evidenceCapsuleId", capsule.evidenceCapsuleId());
    node.put("flowSliceId", capsule.flowSliceId());
    node.put("proofPackId", capsule.proofPackId());
    node.put("modelEligibility", capsule.modelEligibility());
    strings(node.putArray("modelIneligibilityGapIds"), capsule.modelIneligibilityGapIds());
    ObjectNode entry = node.putObject("entryView");
    entry.put("entryId", capsule.entryView().entryId());
    entry.put("trigger", capsule.entryView().trigger());
    entry.put("rootNodeId", capsule.entryView().rootNodeId());
    strings(entry.putArray("routeEvidenceNodeIds"), capsule.entryView().routeEvidenceNodeIds());
    entryContext(node.putObject("entryContext"), capsule.entryContext());
    ArrayNode facts = node.putArray("factViews");
    capsule.factViews().forEach(value -> fact(facts.addObject(), value));
    ArrayNode gaps = node.putArray("gapViews");
    capsule.gapViews().forEach(value -> gap(gaps.addObject(), value));
    ArrayNode outcomes = node.putArray("outcomePathViews");
    capsule.outcomePathViews().forEach(value -> outcome(outcomes.addObject(), value));
    ArrayNode signals = node.putArray("processJoinSignals");
    capsule.processJoinSignals().forEach(value -> signal(signals.addObject(), value));
    strings(node.putArray("modelEvidenceSpanIds"), capsule.modelEvidenceSpanIds());
    strings(node.putArray("projectionObligationIds"), capsule.projectionObligationIds());
    node.putObject("budgetUsage")
        .put("spanCount", capsule.budgetUsage().spanCount())
        .put("sourceUtf8Bytes", capsule.budgetUsage().sourceUtf8Bytes());
  }

  private static void entryContext(
      ObjectNode node,
      org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation.EntryContext value) {
    node.put("entryContextId", value.entryContextId());
    node.put("entryId", value.entryId());
    if (value.flowSliceId() == null) node.putNull("flowSliceId");
    else node.put("flowSliceId", value.flowSliceId());
    node.put("trigger", value.trigger());
    node.put("entrySignature", value.entrySignature());
    ArrayNode calls = node.putArray("calls");
    value
        .calls()
        .forEach(
            call -> {
              ObjectNode item = calls.addObject();
              item.put("callerSignature", call.callerSignature());
              item.put("targetSignature", call.targetSignature());
              strings(item.putArray("argumentExpressions"), call.argumentExpressions());
              item.put("resolution", call.resolution());
              item.put("boundary", call.boundary());
              strings(item.putArray("factIds"), call.factIds());
              strings(item.putArray("proofIds"), call.proofIds());
              strings(item.putArray("evidenceNodeIds"), call.evidenceNodeIds());
            });
    ArrayNode controls = node.putArray("controls");
    value
        .controls()
        .forEach(
            control -> {
              ObjectNode item = controls.addObject();
              item.put("controlNodeId", control.controlNodeId());
              item.put("ownerSignature", control.ownerSignature());
              item.put("condition", control.condition());
              strings(item.putArray("evidenceNodeIds"), control.evidenceNodeIds());
            });
    ArrayNode returns = node.putArray("returns");
    value
        .returns()
        .forEach(
            terminal -> {
              ObjectNode item = returns.addObject();
              item.put("terminalNodeId", terminal.terminalNodeId());
              item.put("terminalKind", terminal.terminalKind());
              strings(item.putArray("evidenceNodeIds"), terminal.evidenceNodeIds());
            });
    ArrayNode sourceLocators = node.putArray("sourceLocators");
    value.sourceLocators().forEach(locator -> sourceLocator(sourceLocators.addObject(), locator));
    strings(node.putArray("factIds"), value.factIds());
    strings(node.putArray("gapIds"), value.gapIds());
    strings(node.putArray("limitations"), value.limitations());
  }

  private static void fact(ObjectNode node, CapsuleProjection.FlowFactView fact) {
    node.put("factId", fact.factId());
    node.put("kind", fact.kind());
    strings(node.putArray("subjectNodeIds"), fact.subjectNodeIds());
    ArrayNode atoms = node.putArray("atoms");
    fact.atoms()
        .forEach(
            atom -> {
              ObjectNode item = atoms.addObject();
              item.put("atomId", atom.atomId());
              item.put("role", atom.role());
              item.put("name", atom.name());
              item.putObject("value")
                  .put("type", atom.valueType())
                  .put("canonical", atom.canonicalValue());
              item.put("proofId", atom.proofId());
            });
    node.set("originFactArtifactRef", reference(fact.originFactArtifactRef()));
  }

  private static void gap(ObjectNode node, CapsuleProjection.FlowGapView gap) {
    node.put("gapId", gap.gapId());
    node.put("scope", gap.scope());
    node.put("reasonCode", gap.reasonCode());
    strings(node.putArray("affectedSemanticIds"), gap.affectedSemanticIds());
    node.set("evidenceRefs", references(gap.evidenceRefs()));
    node.put("originKind", gap.originKind());
    if (gap.originGapLedgerRef() == null) node.putNull("originGapLedgerRef");
    else node.set("originGapLedgerRef", reference(gap.originGapLedgerRef()));
  }

  private static void outcome(ObjectNode node, CapsuleProjection.FlowOutcomePathView outcome) {
    node.put("outcomePathId", outcome.outcomePathId());
    ArrayNode decisions = node.putArray("decisions");
    outcome
        .decisions()
        .forEach(
            decision ->
                decisions
                    .addObject()
                    .put("guardNodeId", decision.guardNodeId())
                    .put("conditionAtomId", decision.conditionAtomId())
                    .put("polarity", decision.polarity())
                    .put("normalizedCondition", decision.normalizedCondition()));
    node.put("terminalNodeId", outcome.terminalNodeId());
    node.put("terminalKind", outcome.terminalKind());
    strings(node.putArray("terminalFactIds"), outcome.terminalFactIds());
    strings(node.putArray("requiredAtomIds"), outcome.requiredAtomIds());
    strings(node.putArray("requiredProofIds"), outcome.requiredProofIds());
  }

  private static void signal(ObjectNode node, FlowCompilation.ProcessJoinSignalV1 signal) {
    node.put("processJoinSignalId", signal.processJoinSignalId());
    node.put("flowSliceId", signal.flowSliceId());
    node.put("signalKind", signal.signalKind());
    node.put("anchorKind", signal.anchorKind());
    node.put("anchorKey", signal.anchorKey());
    node.put("direction", signal.direction());
    node.put("specificity", signal.specificity());
    node.put("claimScope", signal.claimScope());
    strings(node.putArray("factIds"), signal.factIds());
    strings(node.putArray("atomIds"), signal.atomIds());
    strings(node.putArray("proofIds"), signal.proofIds());
    strings(node.putArray("evidenceNodeIds"), signal.evidenceNodeIds());
    ArrayNode locators = node.putArray("sourceLocators");
    signal.sourceLocators().forEach(locator -> sourceLocator(locators.addObject(), locator));
    strings(node.putArray("gapIds"), signal.gapIds());
  }

  private static void sourceLocator(ObjectNode node, SourceLocatorV1 locator) {
    node.put("fileId", locator.fileId().value());
    node.put("path", locator.path());
    node.put("startByte", locator.startByte());
    node.put("endByteExclusive", locator.endByteExclusive());
    node.put("startLine", locator.startLine());
    node.put("startColumn", locator.startColumn());
    node.put("endLine", locator.endLine());
    node.put("endColumn", locator.endColumn());
  }

  private static void span(ObjectNode node, CapsuleProjection.ModelEvidenceSpan span) {
    node.put("spanId", span.spanId());
    node.put("evidenceNodeId", span.evidenceNodeId());
    ObjectNode excerpt = node.putObject("sourceExcerpt");
    ObjectNode locator = excerpt.putObject("locator");
    locator.put("fileId", span.sourceExcerpt().locator().fileId().value());
    locator.put("path", span.sourceExcerpt().locator().path());
    locator.put("startByte", span.sourceExcerpt().locator().startByte());
    locator.put("endByteExclusive", span.sourceExcerpt().locator().endByteExclusive());
    locator.put("startLine", span.sourceExcerpt().locator().startLine());
    locator.put("startColumn", span.sourceExcerpt().locator().startColumn());
    locator.put("endLine", span.sourceExcerpt().locator().endLine());
    locator.put("endColumn", span.sourceExcerpt().locator().endColumn());
    excerpt.put(
        "rawUtf8",
        new String(span.sourceExcerpt().rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    excerpt.put("rawUtf8Sha256", span.sourceExcerpt().rawUtf8Sha256().value());
    strings(node.putArray("supportedAtomIds"), span.supportedAtomIds());
    strings(node.putArray("supportedOutcomePathIds"), span.supportedOutcomePathIds());
    strings(node.putArray("supportedProcessJoinSignalIds"), span.supportedProcessJoinSignalIds());
  }

  private static void obligation(
      ObjectNode node, CapsuleProjection.ProjectionObligation obligation) {
    node.put("obligationId", obligation.obligationId());
    node.put("kind", obligation.kind());
    node.put("semanticItemId", obligation.semanticItemId());
    strings(node.putArray("satisfyingSpanIds"), obligation.satisfyingSpanIds());
  }

  private static String flowRootedSpanId(String flowSliceId, String evidenceNodeId) {
    return "model-evidence-span:"
        + sha256(
            frame("business-flows-model-evidence-span-id-v2"),
            frame(flowSliceId),
            frame(evidenceNodeId));
  }

  private static String projectionId(ObjectNode body, CanonicalJsonCodec codec) {
    return ARTIFACT_PREFIX
        + ":"
        + sha256(frame(PROJECTION_ID_DOMAIN), frame(codec.encodeCanonical(body).copyToByteArray()));
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    ObjectNode producerAddress = result.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", address.runId().value());
    producerAddress.put("analysisStepKey", address.analysisStepKey().wireValue());
    producerAddress.put("moduleNumber", address.moduleNumber());
    producerAddress.put("moduleKey", address.moduleKey());
    result.put("moduleVersion", MODULE_VERSION);
    return result;
  }

  private static ObjectNode reference(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> result.add(reference(value)));
    return result;
  }

  private static ObjectNode controls(ArtifactControls values) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("toolchainSha256", values.toolchainSha256().value());
    result.put("profileSha256", values.profileSha256().value());
    result.put("schemaBundleSha256", values.schemaBundleSha256().value());
    if (values.promptBundleSha256() == null) result.putNull("promptBundleSha256");
    else result.put("promptBundleSha256", values.promptBundleSha256().value());
    result
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return result;
  }

  private static List<String> modelIneligibilityGapRefs(CapsuleProjection projection) {
    List<String> values =
        projection.capsules().stream()
            .flatMap(capsule -> capsule.modelIneligibilityGapIds().stream())
            .sorted(UTF8_ORDER)
            .toList();
    if (values.size() != values.stream().distinct().count()) throw failure();
    return values;
  }

  private static ObjectNode completion(ModuleCompletionStatus status, List<String> gapRefs) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("status", status.name());
    strings(result.putArray("gapRefs"), gapRefs);
    result.putNull("failureRef");
    return result;
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
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
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static int compareUtf8(String left, String right) {
    byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
    byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(leftBytes.length, rightBytes.length); index++) {
      int compared =
          Integer.compare(
              Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
      if (compared != 0) return compared;
    }
    return Integer.compare(leftBytes.length, rightBytes.length);
  }

  private static CapsuleProjectionException failure() {
    return new CapsuleProjectionException("EVIDENCE_PROJECTION_INVARIANT_BROKEN");
  }
}
