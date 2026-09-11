package org.sourceanalysis.app.analysis.interpretation.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

/**
 * Receipt-last M6 persistence boundary for value-bearing cross-Flow material.
 *
 * <p>The payload intentionally retains the exact public Step 03–05 values used for process
 * reconstruction. M7 must reopen this module publication instead of consuming the in-memory {@link
 * CrossFlowCandidateCompilation}.
 */
public final class CrossFlowCandidateModulePublisher {

  private static final String FILE_NAME = "cross-flow-candidate-compilation.json";
  private static final String ARTIFACT_TYPE =
      "FLOW_INTERPRETATION_CROSS_FLOW_CANDIDATE_COMPILATION";
  private static final String SCHEMA_VERSION =
      "flow-interpretation-cross-flow-candidate-compilation-v1";
  private static final String ARTIFACT_PREFIX = "flow-interpretation-cross-flow";
  private static final String MODULE_VERSION = "v1";
  private static final Comparator<String> UTF8_ORDER =
      CrossFlowCandidateModulePublisher::compareUtf8;

  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Creates the M6 persistence boundary over canonical stores only. */
  public CrossFlowCandidateModulePublisher(
      CanonicalAnalysisStepArtifactStore analysisSteps,
      CanonicalModuleArtifactStore moduleArtifacts) {
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis-step artifact store");
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /** Fresh-reopens all predecessors and atomically installs M6's complete program material. */
  public ModulePublicationReference publish(CrossFlowCandidateCompilation compilation) {
    try {
      Objects.requireNonNull(compilation, "cross-Flow candidate compilation");
      ReopenedAnalysisStepPublication graphs =
          reopen(compilation.programGraphsPublicationRef(), AnalysisStepKey.PROGRAM_GRAPHS);
      ReopenedAnalysisStepPublication facts =
          reopen(compilation.provenCodeFactsPublicationRef(), AnalysisStepKey.PROVEN_CODE_FACTS);
      ReopenedAnalysisStepPublication flows =
          reopen(compilation.businessFlowsPublicationRef(), AnalysisStepKey.BUSINESS_FLOWS);
      ReopenedModulePublication registry =
          reopenRegistry(compilation.repositoryInterpretationRegistryPublicationRef());
      requireLineage(compilation, graphs, facts, flows, registry);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              flows.reference().address().runId(),
              AnalysisStepKey.FLOW_INTERPRETATION,
              6,
              "cross-flow-candidate-compiler");
      List<ArtifactReference> upstream =
          upstream(graphs, facts, flows, registry, compilation.analysisRunRequestRef());
      CanonicalModulePayload payload =
          payload(
              address,
              upstream,
              flows.receipt().controls(),
              compilation,
              graphs,
              facts,
              flows,
              registry);
      InstalledModulePublication installed =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstream,
                  flows.receipt().controls(),
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      return installed.reference();
    } catch (CrossFlowCandidateCompilationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new CrossFlowCandidateCompilationException(
          "FLOW_INTERPRETATION_INPUT_INVALID", failure);
    }
  }

  private ReopenedAnalysisStepPublication reopen(
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference,
      AnalysisStepKey expectedStep) {
    if (reference == null || reference.address().analysisStepKey() != expectedStep) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    ReopenedAnalysisStepPublication reopened = analysisSteps.reopen(reference);
    if (!reference.equals(reopened.reference())
        || reopened.receipt().address().analysisStepKey() != expectedStep
        || !reference
            .analysisStepArtifactRoot()
            .equals(reopened.receipt().analysisStepArtifactRoot())
        || !reference.analysisStepReceiptId().equals(reopened.receipt().analysisStepReceiptId())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    return reopened;
  }

  private ReopenedModulePublication reopenRegistry(ModulePublicationReference reference) {
    if (reference == null) throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    ReopenedModulePublication reopened = moduleArtifacts.reopen(reference);
    if (!reference.equals(reopened.reference())
        || !(reopened.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.FLOW_INTERPRETATION
        || address.moduleNumber() != 3
        || !"registry-freezer".equals(address.moduleKey())
        || reopened.payloads().size() != 1) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
    VerifiedCanonicalPayload payload = reopened.payloads().get(0);
    if (!"repository-interpretation-registry.json".equals(payload.descriptor().fileName())
        || !"FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY_MODULE"
            .equals(payload.descriptor().artifactType())
        || !"flow-interpretation-repository-interpretation-registry-module-v1"
            .equals(payload.descriptor().schemaVersion())) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return reopened;
  }

  private void requireLineage(
      CrossFlowCandidateCompilation compilation,
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts,
      ReopenedAnalysisStepPublication flows,
      ReopenedModulePublication registry) {
    if (!compilation.closed()
        || !graphs.reference().address().runId().equals(facts.reference().address().runId())
        || !graphs.reference().address().runId().equals(flows.reference().address().runId())
        || !graphs.receipt().controls().equals(facts.receipt().controls())
        || !graphs.receipt().controls().equals(flows.receipt().controls())
        || !flows.receipt().upstreamAnalysisStepReferences().contains(graphs.reference())
        || !flows.receipt().upstreamAnalysisStepReferences().contains(facts.reference())
        || !registry.receipt().address().runId().equals(flows.reference().address().runId())
        || !registry.receipt().controls().equals(flows.receipt().controls())) {
      throw failure("FLOW_INTERPRETATION_INPUT_INVALID");
    }
  }

  private List<ArtifactReference> upstream(
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts,
      ReopenedAnalysisStepPublication flows,
      ReopenedModulePublication registry,
      ArtifactReference analysisRunRequest) {
    List<ArtifactReference> values = new ArrayList<>();
    graphs.semanticPayloads().forEach(value -> values.add(reference(value)));
    facts.semanticPayloads().forEach(value -> values.add(reference(value)));
    flows.semanticPayloads().forEach(value -> values.add(reference(value)));
    values.add(reference(registry.payloads().get(0)));
    values.add(analysisRunRequest);
    values.sort(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER));
    if (values.size() != values.stream().map(ArtifactReference::artifactId).distinct().count()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return List.copyOf(values);
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      CrossFlowCandidateCompilation compilation,
      ReopenedAnalysisStepPublication graphs,
      ReopenedAnalysisStepPublication facts,
      ReopenedAnalysisStepPublication flows,
      ReopenedModulePublication registry) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("crossFlowCandidateCompilationId", compilation.compilationId());
    references(
        body.putObject("programGraphsPublicationRef"), compilation.programGraphsPublicationRef());
    references(
        body.putObject("provenCodeFactsPublicationRef"),
        compilation.provenCodeFactsPublicationRef());
    references(
        body.putObject("businessFlowsPublicationRef"), compilation.businessFlowsPublicationRef());
    references(
        body.putObject("repositoryInterpretationRegistryPublicationRef"),
        compilation.repositoryInterpretationRegistryPublicationRef());
    references(body.putObject("analysisRunRequestRef"), compilation.analysisRunRequestRef());
    JsonNode flowSlices = payload(flows, "flow-slices.json");
    ArrayNode evidenceCapsules = jsonLines(payloadBytes(flows, "evidence-capsules.jsonl"));
    JsonNode codeFacts = payload(facts, "proven-facts.json");
    JsonNode atomProofs = payload(facts, "proof-pack.json");
    JsonNode evidenceNodes = payload(graphs, "evidence-graph.json");
    copyArray(body.putArray("flowSlices"), flowSlices, "flowSlices");
    copyArray(body.putArray("evidenceCapsules"), evidenceCapsules);
    copyArray(body.putArray("codeFacts"), codeFacts, "codeFacts");
    copyArray(body.putArray("atomProofs"), atomProofs, "atomProofs");
    copyArray(body.putArray("evidenceNodes"), evidenceNodes, "nodes");
    JsonNode registryPayload =
        canonicalJson.parseCanonical(registry.payloads().get(0).canonicalUtf8()).path("payload");
    copyArray(body.putArray("registryItems"), registryPayload, "items");
    body.set("candidateRelations", objectMapper.valueToTree(compilation.candidateRelations()));
    body.set(
        "processEvidenceGroups",
        completeGroups(
            compilation.processEvidenceGroups(),
            flowSlices,
            evidenceCapsules,
            codeFacts,
            atomProofs,
            evidenceNodes,
            registryPayload,
            compilation.processMaterialLimits()));
    body.set("counterScopeIssues", objectMapper.valueToTree(compilation.counterScopeIssues()));
    body.set("accounting", objectMapper.valueToTree(compilation.accounting()));
    body.put("closed", compilation.closed());

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

  private JsonNode payload(ReopenedAnalysisStepPublication publication, String fileName) {
    return canonicalJson.parseCanonical(payloadBytes(publication, fileName));
  }

  private org.sourceanalysis.app.artifact.ImmutableBytes payloadBytes(
      ReopenedAnalysisStepPublication publication, String fileName) {
    List<VerifiedCanonicalPayload> payloads =
        publication.semanticPayloads().stream()
            .filter(value -> fileName.equals(value.descriptor().fileName()))
            .toList();
    if (payloads.size() != 1) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return payloads.get(0).canonicalUtf8();
  }

  private void copyArray(ArrayNode target, JsonNode source, String field) {
    JsonNode values = source.get(field);
    if (!(values instanceof ArrayNode array)) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    array.forEach(value -> target.add(value.deepCopy()));
  }

  private void copyArray(ArrayNode target, ArrayNode source) {
    source.forEach(value -> target.add(value.deepCopy()));
  }

  private ArrayNode jsonLines(org.sourceanalysis.app.artifact.ImmutableBytes canonicalJsonLines) {
    String text = new String(canonicalJsonLines.copyToByteArray(), StandardCharsets.UTF_8);
    if (text.isEmpty() || !text.endsWith("\n")) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
      if (line.isEmpty()) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      values.add(
          canonicalJson.parseCanonical(
              org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                  line.getBytes(StandardCharsets.UTF_8))));
    }
    return values;
  }

  private ArrayNode completeGroups(
      List<ProcessEvidenceGroupV2> groups,
      JsonNode flowSlices,
      ArrayNode evidenceCapsules,
      JsonNode codeFacts,
      JsonNode atomProofs,
      JsonNode evidenceNodes,
      JsonNode registryPayload,
      ProcessMaterialLimitsV1 limits) {
    Map<String, JsonNode> flowsById = indexed(array(flowSlices, "flowSlices"), "flowSliceId");
    Map<String, JsonNode> capsulesByFlowId = indexed(evidenceCapsules, "flowSliceId");
    Map<String, JsonNode> factsById = indexed(array(codeFacts, "codeFacts"), "factId");
    Map<String, JsonNode> proofsById = indexed(array(atomProofs, "atomProofs"), "proofId");
    Map<String, JsonNode> evidenceById = indexed(array(evidenceNodes, "nodes"), "evidenceNodeId");
    Map<String, JsonNode> registryById = indexed(array(registryPayload, "items"), "provisionalKey");
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    for (ProcessEvidenceGroupV2 group : groups) {
      ObjectNode value = objectMapper.valueToTree(group);
      ObjectNode material = value.putObject("persistedMaterial");
      Set<String> factIds = new LinkedHashSet<>();
      Set<String> proofIds = new LinkedHashSet<>();
      Set<String> evidenceIds = new LinkedHashSet<>();
      ArrayNode flowViews = material.putArray("flowViews");
      for (String flowId : group.memberFlowSliceIds()) {
        JsonNode flow = required(flowsById, flowId);
        JsonNode capsule = required(capsulesByFlowId, flowId);
        ObjectNode flowView = flowViews.addObject();
        flowView.put("flowSliceId", flowId);
        flowView.set("flowSlice", flow.deepCopy());
        flowView.set("evidenceCapsule", capsule.deepCopy());
        identifiers(capsule, "factViews", "factId", factIds);
        nestedIdentifiers(capsule, "factViews", "atoms", "proofId", proofIds);
        nestedScalarIdentifiers(capsule, "outcomePathViews", "requiredProofIds", proofIds);
        nestedScalarIdentifiers(capsule, "processJoinSignals", "proofIds", proofIds);
        nestedScalarIdentifiers(capsule, "processJoinSignals", "evidenceNodeIds", evidenceIds);
      }
      for (ProcessCandidateRelationV2 relation : group.candidateRelations()) {
        factIds.addAll(relation.factIds());
        proofIds.addAll(relation.proofIds());
        evidenceIds.addAll(relation.evidenceNodeIds());
      }
      for (String proofId : proofIds) {
        JsonNode proof = required(proofsById, proofId);
        identifiers(proof, "requiredEvidenceNodeIds", evidenceIds);
      }
      material.set("relationViews", objectMapper.valueToTree(group.candidateRelations()));
      material.set(
          "registryItems",
          selected(
              registryById,
              group.repositoryInterpretationRegistryItemIds(),
              "PROCESS_MODEL_REFERENCE_INVALID"));
      material.set("codeFacts", selected(factsById, factIds, "PROCESS_MODEL_REFERENCE_INVALID"));
      material.set("atomProofs", selected(proofsById, proofIds, "PROCESS_MODEL_REFERENCE_INVALID"));
      material.set(
          "evidenceNodes", selected(evidenceById, evidenceIds, "PROCESS_MODEL_REFERENCE_INVALID"));
      material.set("limits", objectMapper.valueToTree(limits));
      result.add(value);
    }
    return result;
  }

  private static ArrayNode selected(
      Map<String, JsonNode> values, Iterable<String> identifiers, String failureCode) {
    List<String> ordered = new ArrayList<>();
    identifiers.forEach(ordered::add);
    ordered.sort(UTF8_ORDER);
    if (ordered.size() != ordered.stream().distinct().count()) throw failure(failureCode);
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    for (String identifier : ordered) result.add(required(values, identifier).deepCopy());
    return result;
  }

  private static Map<String, JsonNode> indexed(ArrayNode values, String identifierField) {
    Map<String, JsonNode> result = new HashMap<>();
    for (JsonNode value : values) {
      String identifier = identifier(value, identifierField);
      if (result.put(identifier, value) != null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return Map.copyOf(result);
  }

  private static JsonNode required(Map<String, JsonNode> values, String identifier) {
    JsonNode value = values.get(identifier);
    if (value == null) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return value;
  }

  private static ArrayNode array(JsonNode value, String field) {
    JsonNode array = value.get(field);
    if (!(array instanceof ArrayNode values)) throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    return values;
  }

  private static String identifier(JsonNode value, String field) {
    JsonNode identifier = value.get(field);
    if (identifier == null || !identifier.isTextual() || identifier.textValue().isBlank()) {
      throw failure("PROCESS_MODEL_REFERENCE_INVALID");
    }
    return identifier.textValue();
  }

  private static void identifiers(
      JsonNode value, String arrayField, String identifierField, Set<String> target) {
    for (JsonNode item : array(value, arrayField)) target.add(identifier(item, identifierField));
  }

  private static void nestedIdentifiers(
      JsonNode value,
      String outerArrayField,
      String innerArrayField,
      String identifierField,
      Set<String> target) {
    for (JsonNode outer : array(value, outerArrayField)) {
      identifiers(outer, innerArrayField, identifierField, target);
    }
  }

  private static void nestedScalarIdentifiers(
      JsonNode value, String outerArrayField, String innerArrayField, Set<String> target) {
    for (JsonNode outer : array(value, outerArrayField)) {
      identifiers(outer, innerArrayField, target);
    }
  }

  private static void identifiers(JsonNode value, String arrayField, Set<String> target) {
    for (JsonNode item : array(value, arrayField)) {
      if (!item.isTextual() || item.textValue().isBlank()) {
        throw failure("PROCESS_MODEL_REFERENCE_INVALID");
      }
      target.add(item.textValue());
    }
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value
        .putObject("address")
        .put("kind", "ANALYSIS_STEP")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue())
        .put("moduleNumber", address.moduleNumber())
        .put("moduleKey", address.moduleKey());
    value.put("moduleVersion", MODULE_VERSION);
    return value;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> references(result.addObject(), value));
    return result;
  }

  private static void references(
      ObjectNode target,
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference) {
    target.put("runId", reference.address().runId().value());
    target.put("analysisStepKey", reference.address().analysisStepKey().wireValue());
    target.put("analysisStepArtifactRoot", reference.analysisStepArtifactRoot().value());
    target.put("analysisStepReceiptId", reference.analysisStepReceiptId().value());
    target.put("analysisStepReceiptSha256", reference.analysisStepReceiptSha256().value());
  }

  private static void references(ObjectNode target, ModulePublicationReference reference) {
    target.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    target.put("moduleReceiptId", reference.moduleReceiptId().value());
    target.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
  }

  private static void references(ObjectNode target, ArtifactReference reference) {
    target.put("artifactId", reference.artifactId().value());
    target.put("sha256", reference.sha256().value());
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

  private static CrossFlowCandidateCompilationException failure(String code) {
    return new CrossFlowCandidateCompilationException(code);
  }
}
