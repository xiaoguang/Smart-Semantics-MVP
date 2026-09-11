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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Reopens one public BusinessFlows publication and makes one isolated R0 task per eligible Flow.
 *
 * <p>The compiler is source-blind: a public, versioned EvidenceCapsule is the sole source for a
 * provider-readable request. It will not reopen source bytes or private upstream module artifacts.
 */
public final class RegistryProposalTaskCompiler {

  private static final String FLOW_SLICES_FILE = "flow-slices.json";
  private static final String FLOW_SLICES_TYPE = "BUSINESS_FLOWS_FLOW_SLICES";
  private static final String FLOW_SLICES_SCHEMA = "business-flows-flow-slices-v4";
  private static final String COVERAGE_FILE = "flow-coverage.json";
  private static final String COVERAGE_TYPE = "BUSINESS_FLOWS_FLOW_COVERAGE";
  private static final String COVERAGE_SCHEMA = "business-flows-flow-coverage-v1";
  private static final String ENTRY_FILE = "entry-dispositions.jsonl";
  private static final String ENTRY_TYPE = "BUSINESS_FLOWS_ENTRY_DISPOSITION";
  private static final String ENTRY_SCHEMA = "business-flows-entry-disposition-v1";
  private static final String CAPSULE_FILE = "evidence-capsules.jsonl";
  private static final String CAPSULE_TYPE = "BUSINESS_FLOWS_EVIDENCE_CAPSULE";
  private static final String CAPSULE_SCHEMA = "business-flows-evidence-capsule-v6";
  private static final String GAP_FILE = "flow-gaps.jsonl";
  private static final String GAP_TYPE = "BUSINESS_FLOWS_FLOW_GAP";
  private static final String GAP_SCHEMA = "business-flows-flow-gap-v2";
  private static final Comparator<String> UTF8_ORDER = RegistryProposalTaskCompiler::compareUtf8;

  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the source-blind compiler over the canonical analysis-step store. */
  public RegistryProposalTaskCompiler(CanonicalAnalysisStepArtifactStore analysisSteps) {
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step artifact store");
  }

  /** Compiles the closed R0 denominator from fresh-reopened public BusinessFlows artifacts. */
  public RegistryProposalTaskSet compileRegistryProposalTasks(
      BusinessFlowsReference businessFlows, RegistryProposalTaskProfile taskProfile) {
    try {
      Objects.requireNonNull(businessFlows, "business flows");
      Objects.requireNonNull(taskProfile, "task profile");
      ReopenedAnalysisStepPublication publication =
          analysisSteps.reopen(businessFlows.publication());
      verifyPublication(businessFlows, publication, taskProfile);
      PublicFlows publicFlows = reopenPublicFlows(publication);
      if (publicFlows.eligibleFlowSliceIds().size() > taskProfile.maxTasks()) {
        throw failure("FLOW_INTERPRETATION_RESOURCE_LIMIT_EXCEEDED");
      }
      List<RegistryProposalTask> tasks =
          publicFlows.eligibleFlowSliceIds().stream()
              .map(flowSliceId -> task(publicFlows.capsuleByFlow().get(flowSliceId), taskProfile))
              .sorted(Comparator.comparing(RegistryProposalTask::flowSliceId, UTF8_ORDER))
              .toList();
      List<String> taskIds = tasks.stream().map(RegistryProposalTask::taskSpecId).toList();
      RegistryProposalTaskShardReceipt shard =
          new RegistryProposalTaskShardReceipt(
              contentId("registry-proposal-task-shard", publicFlows.eligibleFlowSliceIds()),
              publicFlows.eligibleFlowSliceIds(),
              taskIds);
      return new RegistryProposalTaskSet(
          contentId(
              "registry-proposal-task-set",
              List.of(
                  businessFlows.publication().analysisStepArtifactRoot().value(),
                  taskProfile.promptBundleRef().artifactId().value(),
                  taskProfile.promptBundleRef().sha256().value(),
                  taskProfile.outputSchemaRef().sha256().value(),
                  taskProfile.expectedRuntimeRef().sha256().value(),
                  taskProfile.configuredAdapterId(),
                  taskProfile.configuredAuthMode(),
                  taskProfile.expectedRuntime().upstreamProvider(),
                  taskProfile.expectedRuntime().model(),
                  taskProfile.expectedRuntime().reasoningEffort(),
                  taskProfile.expectedRuntime().sandbox(),
                  taskProfile.resourceBudgetRef().sha256().value())),
          businessFlows.publication(),
          publicFlows.eligibleFlowSliceIds(),
          tasks,
          List.of(shard),
          taskProfile);
    } catch (RegistryProposalTaskCompilationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
  }

  private void verifyPublication(
      BusinessFlowsReference businessFlows,
      ReopenedAnalysisStepPublication publication,
      RegistryProposalTaskProfile taskProfile) {
    if (!businessFlows.publication().equals(publication.reference())
        || publication.receipt().address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
        || publication.semanticPayloads().size() != 5
        || publication.archiveManifestPayload() != null) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    ArtifactControls controls = publication.receipt().controls();
    if (!taskProfile.outputSchemaRef().sha256().equals(controls.schemaBundleSha256())
        || !taskProfile.expectedRuntimeRef().sha256().equals(controls.profileSha256())) {
      throw failure("REGISTRY_PROPOSAL_TASK_INVALID");
    }
  }

  private PublicFlows reopenPublicFlows(ReopenedAnalysisStepPublication publication) {
    Map<String, VerifiedCanonicalPayload> payloads = new HashMap<>();
    for (VerifiedCanonicalPayload payload : publication.semanticPayloads()) {
      if (payloads.put(payload.descriptor().fileName(), payload) != null) {
        throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
      }
    }
    VerifiedCanonicalPayload flowPayload =
        requireDescriptor(
            payloads.remove(FLOW_SLICES_FILE),
            FLOW_SLICES_FILE,
            FLOW_SLICES_TYPE,
            FLOW_SLICES_SCHEMA);
    VerifiedCanonicalPayload coveragePayload =
        requireDescriptor(
            payloads.remove(COVERAGE_FILE), COVERAGE_FILE, COVERAGE_TYPE, COVERAGE_SCHEMA);
    requireDescriptor(payloads.remove(ENTRY_FILE), ENTRY_FILE, ENTRY_TYPE, ENTRY_SCHEMA);
    VerifiedCanonicalPayload capsulePayload =
        requireDescriptor(
            payloads.remove(CAPSULE_FILE), CAPSULE_FILE, CAPSULE_TYPE, CAPSULE_SCHEMA);
    requireDescriptor(payloads.remove(GAP_FILE), GAP_FILE, GAP_TYPE, GAP_SCHEMA);
    if (!payloads.isEmpty()) throw failure("FLOW_INTERPRETATION_INPUT_INVALID");

    JsonNode flows = canonicalJson.parseCanonical(flowPayload.canonicalUtf8());
    JsonNode coverage = canonicalJson.parseCanonical(coveragePayload.canonicalUtf8());
    List<String> allFlows = identifierArray(coverage, "flowSliceIds");
    Map<String, JsonNode> flowsById = new HashMap<>();
    for (JsonNode flow : array(flows, "flowSlices")) {
      String flowSliceId = identifier(flow, "flowSliceId");
      if (flowsById.put(flowSliceId, flow) != null) throw failure("FLOW_CAPSULE_SET_INVALID");
    }
    List<String> flowItems = flowsById.keySet().stream().sorted(UTF8_ORDER).toList();
    List<String> eligible = identifierArray(coverage, "modelEligibleFlowSliceIds");
    List<String> ineligible = identifierArray(coverage, "modelIneligibleFlowSliceIds");
    if (!booleanValue(coverage, "closed")
        || !allFlows.equals(flowItems)
        || !partition(allFlows, eligible, ineligible)) {
      throw failure("FLOW_CAPSULE_SET_INVALID");
    }
    Map<String, List<String>> ineligibilityByFlow = readIneligibility(coverage);
    if (!ineligibilityByFlow.keySet().equals(Set.copyOf(ineligible))) {
      throw failure("FLOW_CAPSULE_SET_INVALID");
    }

    Map<String, JsonNode> capsuleByFlow = new HashMap<>();
    Map<String, String> flowByCapsule = new HashMap<>();
    for (JsonNode capsule : jsonLines(capsulePayload.canonicalUtf8())) {
      requireLineEnvelope(capsule, CAPSULE_TYPE, CAPSULE_SCHEMA);
      String flowSliceId = identifier(capsule, "flowSliceId");
      String capsuleId = identifier(capsule, "evidenceCapsuleId");
      if (capsuleByFlow.put(flowSliceId, capsule) != null
          || flowByCapsule.put(capsuleId, flowSliceId) != null) {
        throw failure("FLOW_CAPSULE_SET_INVALID");
      }
      JsonNode flow = flowsById.get(flowSliceId);
      if (flow == null) throw failure("FLOW_CAPSULE_SET_INVALID");
      validateCapsule(
          capsule, flow, flowSliceId, eligible.contains(flowSliceId), ineligibilityByFlow);
    }
    if (!capsuleByFlow.keySet().equals(Set.copyOf(allFlows))
        || !Set.copyOf(identifierArray(coverage, "capsuleIds")).equals(flowByCapsule.keySet())) {
      throw failure("FLOW_CAPSULE_SET_INVALID");
    }
    return new PublicFlows(List.copyOf(eligible), Map.copyOf(capsuleByFlow));
  }

  private void validateCapsule(
      JsonNode capsule,
      JsonNode flow,
      String flowSliceId,
      boolean eligible,
      Map<String, List<String>> ineligibilityByFlow) {
    identifier(capsule, "proofPackId");
    List<String> ineligibilityGaps = identifierArray(capsule, "modelIneligibilityGapIds");
    String modelEligibility = text(capsule, "modelEligibility");
    if ((eligible && (!"ELIGIBLE".equals(modelEligibility) || !ineligibilityGaps.isEmpty()))
        || (!eligible
            && (!"INELIGIBLE".equals(modelEligibility)
                || !ineligibilityGaps.equals(ineligibilityByFlow.get(flowSliceId))))) {
      throw failure("FLOW_CAPSULE_SET_INVALID");
    }
    if (array(capsule, "factViews").isEmpty()
        || array(capsule, "outcomePathViews").isEmpty()
        || array(capsule, "modelEvidenceSpans").isEmpty()
        || array(capsule, "projectionObligations").isEmpty()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    for (JsonNode fact : array(capsule, "factViews")) validateFact(fact);
    for (JsonNode gap : array(capsule, "gapViews")) validateGap(gap);
    for (JsonNode outcome : array(capsule, "outcomePathViews")) validateOutcome(outcome);
    List<String> signalIds = validateProcessJoinSignals(capsule, flowSliceId);
    validateProcessJoinSignals(flow, flowSliceId);
    if (!Arrays.equals(
        canonicalJson.encodeCanonical(field(flow, "processJoinSignals")).copyToByteArray(),
        canonicalJson.encodeCanonical(field(capsule, "processJoinSignals")).copyToByteArray())) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    List<String> spanIds = identifierArray(capsule, "modelEvidenceSpanIds");
    List<String> embeddedSpanIds =
        array(capsule, "modelEvidenceSpans").stream()
            .map(value -> identifier(value, "spanId"))
            .sorted(UTF8_ORDER)
            .toList();
    if (!spanIds.equals(embeddedSpanIds)) throw failure("CAPSULE_CLOSURE_BROKEN");
    Map<String, List<String>> supportingSpanIdsBySignal = new HashMap<>();
    for (JsonNode span : array(capsule, "modelEvidenceSpans")) {
      validateSpan(span);
      String spanId = identifier(span, "spanId");
      for (String signalId : orderedIdentifierArray(span, "supportedProcessJoinSignalIds")) {
        if (!signalIds.contains(signalId)) throw failure("CAPSULE_CLOSURE_BROKEN");
        supportingSpanIdsBySignal
            .computeIfAbsent(signalId, ignored -> new ArrayList<>())
            .add(spanId);
      }
    }
    List<String> obligationIds = identifierArray(capsule, "projectionObligationIds");
    List<String> embeddedObligationIds =
        array(capsule, "projectionObligations").stream()
            .map(value -> identifier(value, "obligationId"))
            .sorted(UTF8_ORDER)
            .toList();
    if (!obligationIds.equals(embeddedObligationIds)) throw failure("CAPSULE_CLOSURE_BROKEN");
    Map<String, List<JsonNode>> obligationsBySignal = new HashMap<>();
    for (JsonNode obligation : array(capsule, "projectionObligations")) {
      List<String> satisfying = identifierArray(obligation, "satisfyingSpanIds");
      if (satisfying.isEmpty() || !spanIds.containsAll(satisfying)) {
        throw failure("CAPSULE_CLOSURE_BROKEN");
      }
      if ("PROCESS_JOIN_SIGNAL_BASIS".equals(text(obligation, "kind"))) {
        String signalId = identifier(obligation, "semanticItemId");
        if (!signalIds.contains(signalId)) throw failure("CAPSULE_CLOSURE_BROKEN");
        obligationsBySignal.computeIfAbsent(signalId, ignored -> new ArrayList<>()).add(obligation);
      }
    }
    for (String signalId : signalIds) {
      List<String> supportingSpanIds = supportingSpanIdsBySignal.getOrDefault(signalId, List.of());
      List<JsonNode> signalObligations = obligationsBySignal.getOrDefault(signalId, List.of());
      if (supportingSpanIds.isEmpty()
          || signalObligations.size() != 1
          || !supportingSpanIds.equals(
              identifierArray(signalObligations.get(0), "satisfyingSpanIds"))) {
        throw failure("CAPSULE_CLOSURE_BROKEN");
      }
    }
    List<String> atomIds = allAtomIds(capsule);
    if (!atomIds.containsAll(identifierArray(capsule, "registryProposalBasisAtomIds"))) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    Set<String> gapIds = new HashSet<>();
    for (JsonNode gap : array(capsule, "gapViews")) gapIds.add(identifier(gap, "gapId"));
    if (!gapIds.containsAll(identifierArray(capsule, "registryProposalBasisGapIds"))) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
  }

  private RegistryProposalTask task(JsonNode capsule, RegistryProposalTaskProfile profile) {
    String flowSliceId = identifier(capsule, "flowSliceId");
    String capsuleId = identifier(capsule, "evidenceCapsuleId");
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    input.put("schemaVersion", "flow-interpretation-registry-proposal-input-v1");
    input.put("kind", "R0_REGISTRY_PROPOSAL_INPUT");
    input.put("flowSliceId", flowSliceId);
    input.put("evidenceCapsuleId", capsuleId);
    input.set("capsuleView", view(capsule));
    ArrayNode kinds = input.putArray("permittedProposalKinds");
    kinds.add("BUSINESS_TERM");
    kinds.add("CLAIM");
    kinds.add("QUESTION");
    ObjectNode limits = input.putObject("proposalLimits");
    limits.put("maxProposals", profile.maxProposalsPerTask());
    limits.put("maxResponseUtf8Bytes", profile.maxResponseUtf8Bytes());
    limits.put("maxLabelUtf8Bytes", profile.maxLabelUtf8Bytes());
    limits.put("maxPurposeUtf8Bytes", profile.maxPurposeUtf8Bytes());
    input.putArray("seedEntries");
    ImmutableBytes bytes = canonicalJson.encodeCanonical(input);
    Sha256Digest digest = new Sha256Digest(sha256(bytes.copyToByteArray()));
    return new RegistryProposalTask(
        contentId(
            "registry-proposal-task",
            List.of(
                flowSliceId,
                capsuleId,
                digest.value(),
                profile.promptBundleRef().sha256().value())),
        "R0_REGISTRY_PROPOSAL",
        flowSliceId,
        capsuleId,
        contentId("registry-proposal-session", List.of(flowSliceId, capsuleId)),
        bytes,
        digest,
        profile.outputSchemaRef().sha256(),
        profile.promptBundleRef().sha256(),
        profile.configuredAdapterId(),
        profile.configuredAuthMode(),
        profile.expectedRuntimeRef(),
        profile.expectedRuntime());
  }

  private static JsonNode view(JsonNode capsule) {
    ObjectNode result = ((ObjectNode) capsule).deepCopy();
    for (JsonNode fact : array(result, "factViews")) {
      ((ObjectNode) fact).remove("originFactArtifactRef");
    }
    for (JsonNode gap : array(result, "gapViews")) {
      ((ObjectNode) gap).remove("originKind");
      ((ObjectNode) gap).remove("originGapLedgerRef");
    }
    return result;
  }

  private void validateFact(JsonNode fact) {
    identifier(fact, "factId");
    artifactReference(fact, "originFactArtifactRef");
    if (array(fact, "atoms").isEmpty()) throw failure("CAPSULE_CLOSURE_BROKEN");
    for (JsonNode atom : array(fact, "atoms")) {
      identifier(atom, "atomId");
      identifier(atom, "proofId");
      text(atom, "role");
      text(atom, "name");
      JsonNode value = field(atom, "value");
      text(value, "type");
      text(value, "canonical");
    }
  }

  private static void validateGap(JsonNode gap) {
    identifier(gap, "gapId");
    text(gap, "originKind");
    nullableArtifactReference(gap, "originGapLedgerRef");
    orderedArtifactReferences(gap, "evidenceRefs");
  }

  private static void artifactReference(JsonNode source, String field) {
    artifactReferenceId(field(source, field));
  }

  private static void nullableArtifactReference(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null) throw failure("CAPSULE_CLOSURE_BROKEN");
    if (!value.isNull()) artifactReferenceId(value);
  }

  private static void orderedArtifactReferences(JsonNode source, String field) {
    List<String> values =
        array(source, field).stream()
            .map(RegistryProposalTaskCompiler::artifactReferenceId)
            .toList();
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (!values.equals(ordered) || ordered.size() != ordered.stream().distinct().count()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
  }

  private static String artifactReferenceId(JsonNode reference) {
    requireExactFields(reference, Set.of("artifactId", "sha256"));
    try {
      String artifactId = ArtifactId.parse(text(reference, "artifactId")).value();
      new Sha256Digest(text(reference, "sha256"));
      return artifactId;
    } catch (RuntimeException invalid) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
  }

  private void validateOutcome(JsonNode outcome) {
    identifier(outcome, "outcomePathId");
    identifier(outcome, "terminalNodeId");
    text(outcome, "terminalKind");
    if (identifierArray(outcome, "requiredAtomIds").isEmpty()
        || identifierArray(outcome, "requiredProofIds").isEmpty()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
  }

  private void validateSpan(JsonNode span) {
    identifier(span, "spanId");
    JsonNode excerpt = field(span, "sourceExcerpt");
    JsonNode locator = field(excerpt, "locator");
    identifier(locator, "fileId");
    text(locator, "path");
    if (!locator.path("startByte").canConvertToLong()
        || !locator.path("endByteExclusive").canConvertToLong()
        || locator.path("endByteExclusive").asLong() < locator.path("startByte").asLong()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    text(excerpt, "rawUtf8");
    new Sha256Digest(text(excerpt, "rawUtf8Sha256"));
    identifierArray(span, "supportedAtomIds");
    identifierArray(span, "supportedOutcomePathIds");
    orderedIdentifierArray(span, "supportedProcessJoinSignalIds");
  }

  private static List<String> validateProcessJoinSignals(JsonNode owner, String flowSliceId) {
    List<String> signalIds = new ArrayList<>();
    for (JsonNode signal : array(owner, "processJoinSignals")) {
      requireProcessJoinSignalFields(signal);
      FlowCompilation.ProcessJoinSignalV1 value =
          new FlowCompilation.ProcessJoinSignalV1(
              identifier(signal, "processJoinSignalId"),
              identifier(signal, "flowSliceId"),
              text(signal, "signalKind"),
              text(signal, "anchorKind"),
              text(signal, "anchorKey"),
              text(signal, "direction"),
              text(signal, "specificity"),
              text(signal, "claimScope"),
              orderedIdentifierArray(signal, "factIds"),
              orderedIdentifierArray(signal, "atomIds"),
              orderedIdentifierArray(signal, "proofIds"),
              orderedIdentifierArray(signal, "evidenceNodeIds"),
              sourceLocators(signal),
              orderedIdentifierArray(signal, "gapIds"));
      if (!flowSliceId.equals(value.flowSliceId())) throw failure("CAPSULE_CLOSURE_BROKEN");
      signalIds.add(value.processJoinSignalId());
    }
    List<String> ordered = signalIds.stream().sorted(UTF8_ORDER).toList();
    if (!signalIds.equals(ordered) || ordered.size() != ordered.stream().distinct().count()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    return ordered;
  }

  private static List<SourceLocatorV1> sourceLocators(JsonNode signal) {
    List<SourceLocatorV1> values =
        array(signal, "sourceLocators").stream()
            .map(RegistryProposalTaskCompiler::sourceLocator)
            .toList();
    List<SourceLocatorV1> ordered =
        values.stream()
            .sorted(
                Comparator.comparing(SourceLocatorV1::path)
                    .thenComparingLong(SourceLocatorV1::startByte)
                    .thenComparingLong(SourceLocatorV1::endByteExclusive))
            .toList();
    if (!values.equals(ordered) || ordered.size() != ordered.stream().distinct().count()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    return ordered;
  }

  private static SourceLocatorV1 sourceLocator(JsonNode locator) {
    requireExactFields(
        locator,
        Set.of(
            "fileId",
            "path",
            "startByte",
            "endByteExclusive",
            "startLine",
            "startColumn",
            "endLine",
            "endColumn"));
    return new SourceLocatorV1(
        ArtifactId.parse(text(locator, "fileId")),
        text(locator, "path"),
        longValue(locator, "startByte"),
        longValue(locator, "endByteExclusive"),
        integer(locator, "startLine"),
        integer(locator, "startColumn"),
        integer(locator, "endLine"),
        integer(locator, "endColumn"));
  }

  private static void requireProcessJoinSignalFields(JsonNode signal) {
    requireExactFields(
        signal,
        Set.of(
            "processJoinSignalId",
            "flowSliceId",
            "signalKind",
            "anchorKind",
            "anchorKey",
            "direction",
            "specificity",
            "claimScope",
            "factIds",
            "atomIds",
            "proofIds",
            "evidenceNodeIds",
            "sourceLocators",
            "gapIds"));
  }

  private static void requireExactFields(JsonNode value, Set<String> expected) {
    if (value == null || !value.isObject()) throw failure("CAPSULE_CLOSURE_BROKEN");
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw failure("CAPSULE_CLOSURE_BROKEN");
  }

  private static List<String> allAtomIds(JsonNode capsule) {
    return array(capsule, "factViews").stream()
        .flatMap(fact -> array(fact, "atoms").stream())
        .map(atom -> identifier(atom, "atomId"))
        .sorted(UTF8_ORDER)
        .toList();
  }

  private Map<String, List<String>> readIneligibility(JsonNode coverage) {
    Map<String, List<String>> result = new HashMap<>();
    for (JsonNode item : array(coverage, "modelIneligibilityByFlow")) {
      String flow = identifier(item, "flowSliceId");
      List<String> gaps = identifierArray(item, "gapIds");
      if (gaps.isEmpty() || result.put(flow, gaps) != null) {
        throw failure("FLOW_CAPSULE_SET_INVALID");
      }
    }
    return Map.copyOf(result);
  }

  private VerifiedCanonicalPayload requireDescriptor(
      VerifiedCanonicalPayload payload, String fileName, String type, String schema) {
    if (payload == null
        || !fileName.equals(payload.descriptor().fileName())
        || !type.equals(payload.descriptor().artifactType())
        || !schema.equals(payload.descriptor().schemaVersion())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    return payload;
  }

  private List<JsonNode> jsonLines(ImmutableBytes bytes) {
    if (bytes.size() == 0) return List.of();
    String text = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    if (!text.endsWith("\n") || text.isBlank()) throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    List<JsonNode> result = new ArrayList<>();
    for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
      if (line.isEmpty()) throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
      result.add(
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))));
    }
    return List.copyOf(result);
  }

  private static void requireLineEnvelope(JsonNode value, String type, String schema) {
    if (!type.equals(text(value, "artifactType")) || !schema.equals(text(value, "schemaVersion"))) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
  }

  private static boolean partition(List<String> whole, List<String> first, List<String> second) {
    Set<String> left = Set.copyOf(first);
    Set<String> right = Set.copyOf(second);
    if (!java.util.Collections.disjoint(left, right)) return false;
    Set<String> union = new HashSet<>(left);
    union.addAll(right);
    return union.equals(Set.copyOf(whole));
  }

  private static boolean booleanValue(JsonNode value, String field) {
    JsonNode result = value.get(field);
    return result != null && result.isBoolean() && result.booleanValue();
  }

  private static JsonNode field(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || value.isNull()) throw failure("CAPSULE_CLOSURE_BROKEN");
    return value;
  }

  private static List<JsonNode> array(JsonNode source, String field) {
    JsonNode value = field(source, field);
    if (!value.isArray()) throw failure("CAPSULE_CLOSURE_BROKEN");
    List<JsonNode> result = new ArrayList<>();
    value.forEach(result::add);
    return List.copyOf(result);
  }

  private static String identifier(JsonNode source, String field) {
    try {
      return ArtifactId.parse(text(source, field)).value();
    } catch (RuntimeException invalid) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
  }

  private static List<String> identifierArray(JsonNode source, String field) {
    List<String> values =
        array(source, field).stream()
            .map(RegistryProposalTaskCompiler::identifierValue)
            .sorted(UTF8_ORDER)
            .toList();
    if (values.size() != values.stream().distinct().count()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    return values;
  }

  private static List<String> orderedIdentifierArray(JsonNode source, String field) {
    List<String> values =
        array(source, field).stream().map(RegistryProposalTaskCompiler::identifierValue).toList();
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (!values.equals(ordered) || ordered.size() != ordered.stream().distinct().count()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    return ordered;
  }

  private static String identifierValue(JsonNode value) {
    if (!value.isTextual()) throw failure("CAPSULE_CLOSURE_BROKEN");
    try {
      return ArtifactId.parse(value.textValue()).value();
    } catch (RuntimeException invalid) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
  }

  private static String text(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    return value.textValue();
  }

  private static long longValue(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.canConvertToLong()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    return value.longValue();
  }

  private static int integer(JsonNode source, String field) {
    JsonNode value = source.get(field);
    if (value == null || !value.canConvertToInt()) {
      throw failure("CAPSULE_CLOSURE_BROKEN");
    }
    return value.intValue();
  }

  private static String contentId(String prefix, List<String> values) {
    byte[][] frames = new byte[values.size() + 1][];
    frames[0] = frame(prefix);
    for (int index = 0; index < values.size(); index++)
      frames[index + 1] = frame(values.get(index));
    return prefix + ":" + sha256(frames);
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
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(first.length, second.length);
  }

  private static RegistryProposalTaskCompilationException failure(String code) {
    return new RegistryProposalTaskCompilationException(code);
  }

  private record PublicFlows(
      List<String> eligibleFlowSliceIds, Map<String, JsonNode> capsuleByFlow) {}
}
