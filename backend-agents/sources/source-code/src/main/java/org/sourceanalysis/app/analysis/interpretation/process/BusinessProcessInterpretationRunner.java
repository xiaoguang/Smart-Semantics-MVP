package org.sourceanalysis.app.analysis.interpretation.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * Executes exactly one fresh-reopened M7 model-safe shard as P1 followed by P2.
 *
 * <p>This first M8 seam intentionally validates only the fixed two-Flow/one-relation specimen. It
 * persists nothing and does not replace the later M8/M9 round, disposition, or publication
 * contracts.
 */
public final class BusinessProcessInterpretationRunner {

  private static final String M7_FILE = "process-task-shards.json";
  private static final String M7_TYPE = "FLOW_INTERPRETATION_PROCESS_TASK_SHARDS";
  private static final String M7_SCHEMA = "flow-interpretation-process-task-shards-v1";
  private static final String M6_FILE = "cross-flow-candidate-compilation.json";
  private static final String M6_TYPE = "FLOW_INTERPRETATION_CROSS_FLOW_CANDIDATE_COMPILATION";
  private static final String M6_SCHEMA = "flow-interpretation-cross-flow-candidate-compilation-v1";
  private static final Set<String> PROHIBITED_READER_KEY_PARTS =
      Set.of(
          "artifact",
          "archive",
          "binding",
          "budget",
          "control",
          "digest",
          "evidence",
          "hash",
          "id",
          "proof",
          "receipt",
          "runtime",
          "sha");

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final ProcessInputArtifactReader inputArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the M8 runner over receipt-verified modules and content-addressed input artifacts. */
  public BusinessProcessInterpretationRunner(
      CanonicalModuleArtifactStore moduleArtifacts, ProcessInputArtifactReader inputArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.inputArtifacts = Objects.requireNonNull(inputArtifacts, "process input artifact reader");
  }

  /** Runs the fixed first P1/P2 protocol without retries, provider fallback, or publication. */
  public BusinessProcessInterpretationResult runInterpretation(
      BusinessProcessInterpretationRequest request, ProcessModelProvider provider) {
    CheckpointTerminal terminal = runCheckpointTerminal(request, provider);
    if (terminal instanceof P1SuccessTerminal success) {
      return new BusinessProcessInterpretationResult(
          success.taskShardId(), success.p1Response(), success.p2Response(), 2, true);
    }
    throw failure("PROCESS_MODEL_RESPONSE_INVALID");
  }

  /**
   * Executes the bounded P1 dispatch once, then either completes the P2 KEEP path or preserves a
   * terminal P1 Gap for the receipt-last publisher. This package-private result never exposes a
   * second model-visible protocol or relaxes the P1/P2 validators.
   */
  CheckpointTerminal runCheckpointTerminal(
      BusinessProcessInterpretationRequest request, ProcessModelProvider provider) {
    try {
      Objects.requireNonNull(request, "process interpretation request");
      Objects.requireNonNull(provider, "process model provider");
      ReopenedModulePublication m7 = reopenM7(request.businessProcessTaskPublication());
      JsonNode m7Body = payloadBody(m7.payloads().get(0));
      JsonNode shard = selectModelSafeShard(m7Body, request.taskShardId());
      JsonNode packet =
          requiredObject(shard, "processModelPacket", "PROCESS_MODEL_REFERENCE_INVALID");
      ReaderKeys readerKeys = verifyDryPacket(packet);
      ArtifactReference frozenRuntime = frozenRuntime(m7, m7Body);
      if (!frozenRuntime.equals(request.expectedRuntime())) {
        throw failure("MODEL_RUNTIME_IDENTITY_MISMATCH");
      }

      ImmutableBytes p1Request = p1Request(packet);
      ProcessModelProviderResponse p1 = call(provider, p1Request, frozenRuntime);
      String responseKind =
          requiredText(
              parseResponse(p1.canonicalResponseJson()),
              "responseKind",
              "PROCESS_MODEL_RESPONSE_INVALID");
      if ("P1_GAP".equals(responseKind)) {
        List<String> gapIds = validateP1Gap(p1.canonicalResponseJson(), packet, shard);
        return new P1GapTerminal(
            request.taskShardId(), p1Request, p1, p2GapRequest(packet), List.copyOf(gapIds));
      }
      if (!"P1_HYPOTHESES".equals(responseKind)) {
        throw failure("PROCESS_MODEL_RESPONSE_INVALID");
      }
      AcceptedP1 accepted = validateP1(p1.canonicalResponseJson(), readerKeys);
      ImmutableBytes p2Request = p2Request(packet, accepted);
      ProcessModelProviderResponse p2 = call(provider, p2Request, frozenRuntime);
      validateP2(p2.canonicalResponseJson(), accepted);
      return new P1SuccessTerminal(request.taskShardId(), p1Request, p1, p2Request, p2);
    } catch (BusinessProcessInterpretationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID", failure);
    }
  }

  /**
   * Executes only the P1 terminal-Gap branch for the next receipt-last checkpoint slice.
   *
   * <p>The planned P2 request is returned as program-only material and must never be sent to the
   * Provider. The public P1/P2 success seam remains unchanged.
   */
  P1GapTerminal runP1GapTerminal(
      BusinessProcessInterpretationRequest request, ProcessModelProvider provider) {
    CheckpointTerminal terminal = runCheckpointTerminal(request, provider);
    if (terminal instanceof P1GapTerminal gap) {
      return gap;
    }
    throw failure("PROCESS_MODEL_RESPONSE_INVALID");
  }

  /**
   * Validates one model-safe task before an aggregate publisher starts any Provider call.
   *
   * <p>This repeats the immutable M7/M6 reopen and dry-packet checks used by dispatch, but does not
   * construct a model request or invoke the Provider. It lets an aggregate preflight fail before a
   * later shard could leave an already-started call behind.
   */
  void preflightModelSafeShard(BusinessProcessInterpretationRequest request) {
    try {
      Objects.requireNonNull(request, "process interpretation request");
      ReopenedModulePublication m7 = reopenM7(request.businessProcessTaskPublication());
      JsonNode m7Body = payloadBody(m7.payloads().get(0));
      JsonNode shard = selectModelSafeShard(m7Body, request.taskShardId());
      verifyDryPacket(
          requiredObject(shard, "processModelPacket", "PROCESS_MODEL_REFERENCE_INVALID"));
      if (!frozenRuntime(m7, m7Body).equals(request.expectedRuntime())) {
        throw failure("MODEL_RUNTIME_IDENTITY_MISMATCH");
      }
    } catch (BusinessProcessInterpretationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID", failure);
    }
  }

  /** Validates the one frozen runtime identity even when an M7 denominator has no safe shard. */
  void preflightRuntime(
      ModulePublicationReference businessProcessTaskPublication,
      ArtifactReference expectedRuntime) {
    try {
      ReopenedModulePublication m7 = reopenM7(businessProcessTaskPublication);
      JsonNode m7Body = payloadBody(m7.payloads().get(0));
      if (!frozenRuntime(m7, m7Body).equals(expectedRuntime)) {
        throw failure("MODEL_RUNTIME_IDENTITY_MISMATCH");
      }
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
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 7
        || !"business-process-task-compiler".equals(address.moduleKey())
        || publication.payloads().size() != 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!M7_FILE.equals(payload.descriptor().fileName())
        || !M7_TYPE.equals(payload.descriptor().artifactType())
        || !M7_SCHEMA.equals(payload.descriptor().schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return publication;
  }

  private JsonNode selectModelSafeShard(JsonNode m7Body, String taskShardId) {
    if (!m7Body.path("closed").asBoolean(false)
        || m7Body.path("providerCallCount").asInt(-1) != 0) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    JsonNode selected = null;
    for (JsonNode shard : requiredArray(m7Body, "taskShards", "PROCESS_MODEL_REFERENCE_INVALID")) {
      if (taskShardId.equals(
          requiredText(shard, "taskShardId", "PROCESS_MODEL_REFERENCE_INVALID"))) {
        if (selected != null) {
          throw failure("PROCESS_MODEL_REFERENCE_INVALID");
        }
        selected = shard;
      }
    }
    if (selected == null
        || !"MODEL_SAFE"
            .equals(
                requiredText(selected, "shardModelDisposition", "PROCESS_MODEL_REFERENCE_INVALID"))
        || !requiredArray(selected, "modelIneligibilityGapIds", "PROCESS_MODEL_REFERENCE_INVALID")
            .isEmpty()
        || !selected.hasNonNull("processModelPacket")
        || requiredArray(selected, "readerKeyBindings", "PROCESS_MODEL_REFERENCE_INVALID")
            .isEmpty()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return selected;
  }

  private ArtifactReference frozenRuntime(ReopenedModulePublication m7, JsonNode m7Body) {
    AnalysisStepModuleAddress m7Address = (AnalysisStepModuleAddress) m7.receipt().address();
    ModulePublicationReference m6Reference =
        moduleReference(
            requiredObject(
                m7Body,
                "crossFlowCandidateCompilationPublicationRef",
                "PROCESS_MODEL_REFERENCE_INVALID"),
            new AnalysisStepModuleAddress(
                m7Address.runId(),
                AnalysisStepKey.FLOW_INTERPRETATION,
                6,
                "cross-flow-candidate-compiler"));
    ReopenedModulePublication m6 = moduleArtifacts.reopen(m6Reference);
    if (!m6Reference.equals(m6.reference())
        || !(m6.receipt().address() instanceof AnalysisStepModuleAddress m6Address)
        || !m6Address.runId().equals(m7Address.runId())
        || m6Address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || m6Address.moduleNumber() != 6
        || !"cross-flow-candidate-compiler".equals(m6Address.moduleKey())
        || m6.payloads().size() != 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    VerifiedCanonicalPayload m6Payload = m6.payloads().get(0);
    if (!M6_FILE.equals(m6Payload.descriptor().fileName())
        || !M6_TYPE.equals(m6Payload.descriptor().artifactType())
        || !M6_SCHEMA.equals(m6Payload.descriptor().schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    JsonNode m6Body = payloadBody(m6Payload);
    ArtifactReference runRequest =
        artifactReference(m6Body, "analysisRunRequestRef", "PROCESS_MODEL_REFERENCE_INVALID");
    JsonNode request = reopenInput(runRequest, "PROCESS_MODEL_REFERENCE_INVALID");
    ArtifactReference profile =
        artifactReference(request, "profileBundleRef", "PROCESS_MODEL_REFERENCE_INVALID");
    if (!profile.sha256().equals(m6.receipt().controls().profileSha256())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    JsonNode profileValue = reopenInput(profile, "PROCESS_MODEL_REFERENCE_INVALID");
    JsonNode interpretation =
        requiredObject(profileValue, "flowInterpretation", "PROCESS_MODEL_REFERENCE_INVALID");
    requireExactFields(
        interpretation,
        Set.of("crossFlowCandidateRuleVersion", "processCueProfile", "processModelRuntimeRef"),
        "PROCESS_MODEL_REFERENCE_INVALID");
    return artifactReference(
        interpretation, "processModelRuntimeRef", "PROCESS_MODEL_REFERENCE_INVALID");
  }

  private ProcessModelProviderResponse call(
      ProcessModelProvider provider,
      ImmutableBytes applicationRequest,
      ArtifactReference expectedRuntime) {
    ProcessModelProviderResponse response;
    try {
      response =
          Objects.requireNonNull(
              provider.respond(new ProcessModelProviderRequest(applicationRequest)),
              "provider response");
    } catch (RuntimeException failure) {
      throw failure("PROVIDER_FAILURE_AFTER_START", failure);
    }
    if (!expectedRuntime.equals(response.observedRuntime())) {
      throw failure("MODEL_RUNTIME_IDENTITY_MISMATCH");
    }
    return response;
  }

  private ImmutableBytes p1Request(JsonNode packet) {
    ObjectNode request = JsonNodeFactory.instance.objectNode();
    request.set("packet", packet.deepCopy());
    request.put("requestKind", "PROCESS_P1_HYPOTHESIS_REQUEST");
    return canonicalJson.encodeCanonical(request);
  }

  private ImmutableBytes p2Request(JsonNode packet, AcceptedP1 accepted) {
    ObjectNode request = JsonNodeFactory.instance.objectNode();
    ObjectNode hypothesis = request.putArray("acceptedHypotheses").addObject();
    hypothesis.putArray("claimKeys").add(accepted.claimKey());
    hypothesis.put("hypothesisKey", accepted.hypothesisKey());
    request.set("packet", packet.deepCopy());
    request.put("requestKind", "PROCESS_P2_PRECISION_REVIEW_REQUEST");
    return canonicalJson.encodeCanonical(request);
  }

  private ImmutableBytes p2GapRequest(JsonNode packet) {
    ObjectNode request = JsonNodeFactory.instance.objectNode();
    request.putArray("acceptedHypotheses");
    request.set("packet", packet.deepCopy());
    request.put("requestKind", "PROCESS_P2_PRECISION_REVIEW_REQUEST");
    return canonicalJson.encodeCanonical(request);
  }

  private AcceptedP1 validateP1(ImmutableBytes responseBytes, ReaderKeys readerKeys) {
    JsonNode response = parseResponse(responseBytes);
    requireExactFields(
        response, Set.of("hypotheses", "responseKind"), "PROCESS_MODEL_RESPONSE_INVALID");
    if (!"P1_HYPOTHESES"
        .equals(requiredText(response, "responseKind", "PROCESS_MODEL_RESPONSE_INVALID"))) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    ArrayNode hypotheses = requiredArray(response, "hypotheses", "PROCESS_MODEL_RESPONSE_INVALID");
    if (hypotheses.size() != 1) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    JsonNode hypothesis = hypotheses.get(0);
    requireExactFields(
        hypothesis, Set.of("claims", "flowKeys", "relationKeys"), "PROCESS_MODEL_RESPONSE_INVALID");
    List<String> flowKeys = strings(hypothesis, "flowKeys", "PROCESS_MODEL_RESPONSE_INVALID");
    List<String> relationKeys =
        strings(hypothesis, "relationKeys", "PROCESS_MODEL_RESPONSE_INVALID");
    if (!flowKeys.equals(readerKeys.flowKeys())
        || !relationKeys.equals(List.of(readerKeys.relationKey()))) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    ArrayNode claims = requiredArray(hypothesis, "claims", "PROCESS_MODEL_RESPONSE_INVALID");
    if (claims.size() != 1) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    JsonNode claim = claims.get(0);
    requireExactFields(
        claim,
        Set.of("basisKeys", "claimKind", "objectKeys", "predicate", "subjectKeys"),
        "PROCESS_MODEL_RESPONSE_INVALID");
    if (!"TRANSITION".equals(requiredText(claim, "claimKind", "PROCESS_MODEL_RESPONSE_INVALID"))
        || !"MAY_HAND_OFF_TO"
            .equals(requiredText(claim, "predicate", "PROCESS_MODEL_RESPONSE_INVALID"))
        || !strings(claim, "basisKeys", "PROCESS_MODEL_RESPONSE_INVALID")
            .equals(List.of(readerKeys.relationKey()))
        || !strings(claim, "subjectKeys", "PROCESS_MODEL_RESPONSE_INVALID")
            .equals(List.of(readerKeys.leftFlowKey()))
        || !strings(claim, "objectKeys", "PROCESS_MODEL_RESPONSE_INVALID")
            .equals(List.of(readerKeys.rightFlowKey()))) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return new AcceptedP1("H01", "PC01");
  }

  private void validateP2(ImmutableBytes responseBytes, AcceptedP1 accepted) {
    JsonNode response = parseResponse(responseBytes);
    requireExactFields(
        response, Set.of("responseKind", "reviews"), "PROCESS_MODEL_RESPONSE_INVALID");
    if (!"P2_REVIEWS"
        .equals(requiredText(response, "responseKind", "PROCESS_MODEL_RESPONSE_INVALID"))) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    ArrayNode reviews = requiredArray(response, "reviews", "PROCESS_MODEL_RESPONSE_INVALID");
    if (reviews.size() != 1) {
      throw failure("PROCESS_REVIEW_EXPANDED");
    }
    JsonNode review = reviews.get(0);
    requireExactFields(
        review,
        Set.of("decision", "hypothesisKey", "retainedClaimKeys"),
        "PROCESS_MODEL_RESPONSE_INVALID");
    if (!"KEEP".equals(requiredText(review, "decision", "PROCESS_MODEL_RESPONSE_INVALID"))
        || !accepted
            .hypothesisKey()
            .equals(requiredText(review, "hypothesisKey", "PROCESS_MODEL_RESPONSE_INVALID"))
        || !strings(review, "retainedClaimKeys", "PROCESS_MODEL_RESPONSE_INVALID")
            .equals(List.of(accepted.claimKey()))) {
      throw failure("PROCESS_REVIEW_EXPANDED");
    }
  }

  private List<String> validateP1Gap(
      ImmutableBytes responseBytes, JsonNode packet, JsonNode shard) {
    JsonNode response = parseResponse(responseBytes);
    requireExactFields(
        response, Set.of("gapKeys", "responseKind"), "PROCESS_MODEL_RESPONSE_INVALID");
    if (!"P1_GAP"
        .equals(requiredText(response, "responseKind", "PROCESS_MODEL_RESPONSE_INVALID"))) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    List<String> gapKeys = strings(response, "gapKeys", "PROCESS_MODEL_RESPONSE_INVALID");
    List<String> sortedKeys = new ArrayList<>(gapKeys);
    sortedKeys.sort(BusinessProcessInterpretationRunner::compareUtf8);
    if (!gapKeys.equals(sortedKeys)) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    Set<String> limitations = new HashSet<>();
    for (JsonNode limitation :
        requiredArray(packet, "limitations", "PROCESS_MODEL_RESPONSE_INVALID")) {
      limitations.add(requiredText(limitation, "limitationKey", "PROCESS_MODEL_RESPONSE_INVALID"));
    }
    List<String> gapIds = new ArrayList<>();
    ArrayNode bindings =
        requiredArray(shard, "readerKeyBindings", "PROCESS_MODEL_RESPONSE_INVALID");
    for (String key : gapKeys) {
      if (!limitations.contains(key)) {
        throw failure("PROCESS_MODEL_RESPONSE_INVALID");
      }
      JsonNode matching = null;
      for (JsonNode binding : bindings) {
        if (key.equals(requiredText(binding, "readerKey", "PROCESS_MODEL_RESPONSE_INVALID"))) {
          if (matching != null
              || !"LIMITATION"
                  .equals(requiredText(binding, "keyKind", "PROCESS_MODEL_RESPONSE_INVALID"))) {
            throw failure("PROCESS_MODEL_RESPONSE_INVALID");
          }
          matching = binding;
        }
      }
      if (matching == null) {
        throw failure("PROCESS_MODEL_RESPONSE_INVALID");
      }
      gapIds.addAll(strings(matching, "internalReferenceIds", "PROCESS_MODEL_RESPONSE_INVALID"));
    }
    gapIds.sort(BusinessProcessInterpretationRunner::compareUtf8);
    if (gapIds.isEmpty() || gapIds.size() != new HashSet<>(gapIds).size()) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID");
    }
    return List.copyOf(gapIds);
  }

  private ReaderKeys verifyDryPacket(JsonNode packet) {
    requireExactFields(
        packet,
        Set.of(
            "flowCards",
            "limitations",
            "packetKind",
            "relationCards",
            "schemaVersion",
            "sources",
            "vocabulary"),
        "PROCESS_MODEL_REFERENCE_INVALID");
    if (!"flow-interpretation-process-model-packet-v1"
            .equals(requiredText(packet, "schemaVersion", "PROCESS_MODEL_REFERENCE_INVALID"))
        || !"DRY_BUSINESS_PROCESS_READER"
            .equals(requiredText(packet, "packetKind", "PROCESS_MODEL_REFERENCE_INVALID"))) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    rejectProgramOnlyMaterial(packet);
    ArrayNode flowCards = requiredArray(packet, "flowCards", "PROCESS_MODEL_REFERENCE_INVALID");
    ArrayNode relationCards =
        requiredArray(packet, "relationCards", "PROCESS_MODEL_REFERENCE_INVALID");
    if (flowCards.size() != 2 || relationCards.size() != 1) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    List<String> flowKeys = new ArrayList<>();
    for (JsonNode card : flowCards) {
      flowKeys.add(requiredText(card, "flowKey", "PROCESS_MODEL_REFERENCE_INVALID"));
    }
    if (!flowKeys.equals(List.of("F01", "F02")) || new HashSet<>(flowKeys).size() != 2) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    JsonNode relation = relationCards.get(0);
    String relationKey = requiredText(relation, "relationKey", "PROCESS_MODEL_REFERENCE_INVALID");
    String left = requiredText(relation, "leftFlowKey", "PROCESS_MODEL_REFERENCE_INVALID");
    String right = requiredText(relation, "rightFlowKey", "PROCESS_MODEL_REFERENCE_INVALID");
    if (!"L01".equals(relationKey) || !"F01".equals(left) || !"F02".equals(right)) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return new ReaderKeys(List.copyOf(flowKeys), relationKey, left, right);
  }

  private void rejectProgramOnlyMaterial(JsonNode value) {
    rejectProgramOnlyMaterial(value, null);
  }

  private void rejectProgramOnlyMaterial(JsonNode value, String parentKey) {
    if (value.isObject()) {
      value
          .fields()
          .forEachRemaining(
              entry -> {
                String lower = entry.getKey().toLowerCase(Locale.ROOT);
                if (PROHIBITED_READER_KEY_PARTS.stream().anyMatch(lower::contains)) {
                  throw failure("PROCESS_MODEL_REFERENCE_INVALID");
                }
                rejectProgramOnlyMaterial(entry.getValue(), entry.getKey());
              });
    } else if (value.isArray()) {
      value.forEach(item -> rejectProgramOnlyMaterial(item, parentKey));
    } else if (value.isTextual()) {
      String text = value.textValue();
      if (("repositoryRelativeFile".equals(parentKey) && text.startsWith("/"))
          || text.contains("/private/")
          || text.contains("artifact:")
          || text.contains("sha256")) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
    }
  }

  private JsonNode parseResponse(ImmutableBytes bytes) {
    try {
      return canonicalJson.parseCanonical(bytes);
    } catch (RuntimeException invalid) {
      throw failure("PROCESS_MODEL_RESPONSE_INVALID", invalid);
    }
  }

  private JsonNode reopenInput(ArtifactReference reference, String code) {
    try {
      ImmutableBytes bytes = inputArtifacts.reopen(reference);
      if (bytes == null) {
        throw failure(code);
      }
      return canonicalJson.parseCanonical(bytes);
    } catch (BusinessProcessInterpretationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure(code, failure);
    }
  }

  private JsonNode payloadBody(VerifiedCanonicalPayload payload) {
    JsonNode body = canonicalJson.parseCanonical(payload.canonicalUtf8()).path("payload");
    if (!body.isObject()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return body;
  }

  private static ModulePublicationReference moduleReference(
      JsonNode source, AnalysisStepModuleAddress address) {
    requireExactFields(
        source,
        Set.of("moduleArtifactRoot", "moduleReceiptId", "moduleReceiptSha256"),
        "PROCESS_MODEL_REFERENCE_INVALID");
    try {
      return new ModulePublicationReference(
          address,
          ModuleArtifactRoot.parse(
              requiredText(source, "moduleArtifactRoot", "PROCESS_MODEL_REFERENCE_INVALID")),
          ModuleReceiptId.parse(
              requiredText(source, "moduleReceiptId", "PROCESS_MODEL_REFERENCE_INVALID")),
          Sha256Digest.parse(
              requiredText(source, "moduleReceiptSha256", "PROCESS_MODEL_REFERENCE_INVALID")));
    } catch (RuntimeException invalid) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID", invalid);
    }
  }

  private static ArtifactReference artifactReference(JsonNode source, String field, String code) {
    JsonNode value = requiredObject(source, field, code);
    requireExactFields(value, Set.of("artifactId", "sha256"), code);
    try {
      return new ArtifactReference(
          ArtifactId.parse(requiredText(value, "artifactId", code)),
          Sha256Digest.parse(requiredText(value, "sha256", code)));
    } catch (RuntimeException invalid) {
      throw failure(code, invalid);
    }
  }

  private static ObjectNode requiredObject(JsonNode source, String field, String code) {
    JsonNode value = source.get(field);
    if (!(value instanceof ObjectNode node)) {
      throw failure(code);
    }
    return node;
  }

  private static ArrayNode requiredArray(JsonNode source, String field, String code) {
    JsonNode value = source.get(field);
    if (!(value instanceof ArrayNode array)) {
      throw failure(code);
    }
    return array;
  }

  private static String requiredText(JsonNode source, String field, String code) {
    JsonNode value = source.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure(code);
    }
    return value.textValue();
  }

  private static List<String> strings(JsonNode source, String field, String code) {
    ArrayNode values = requiredArray(source, field, code);
    List<String> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!value.isTextual() || value.textValue().isBlank()) {
        throw failure(code);
      }
      result.add(value.textValue());
    }
    if (result.isEmpty() || result.size() != new HashSet<>(result).size()) {
      throw failure(code);
    }
    return List.copyOf(result);
  }

  private static void requireExactFields(JsonNode source, Set<String> expected, String code) {
    if (!source.isObject()) {
      throw failure(code);
    }
    Set<String> actual = new HashSet<>();
    source.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure(code);
    }
  }

  private static int compareUtf8(String left, String right) {
    byte[] leftBytes = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] rightBytes = right.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    int length = Math.min(leftBytes.length, rightBytes.length);
    for (int index = 0; index < length; index++) {
      int comparison = Integer.compare(leftBytes[index] & 0xff, rightBytes[index] & 0xff);
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(leftBytes.length, rightBytes.length);
  }

  private static BusinessProcessInterpretationException failure(String code) {
    return new BusinessProcessInterpretationException(code);
  }

  private static BusinessProcessInterpretationException failure(String code, Throwable cause) {
    return new BusinessProcessInterpretationException(code, cause);
  }

  private record ReaderKeys(
      List<String> flowKeys, String relationKey, String leftFlowKey, String rightFlowKey) {}

  private record AcceptedP1(String hypothesisKey, String claimKey) {}

  sealed interface CheckpointTerminal permits P1GapTerminal, P1SuccessTerminal {}

  record P1GapTerminal(
      String taskShardId,
      ImmutableBytes p1ApplicationRequest,
      ProcessModelProviderResponse p1Response,
      ImmutableBytes p2ApplicationRequest,
      List<String> upstreamGapIds)
      implements CheckpointTerminal {}

  record P1SuccessTerminal(
      String taskShardId,
      ImmutableBytes p1ApplicationRequest,
      ProcessModelProviderResponse p1Response,
      ImmutableBytes p2ApplicationRequest,
      ProcessModelProviderResponse p2Response)
      implements CheckpointTerminal {}
}
