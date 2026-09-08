package org.sourceanalysis.app.analysis.fact.proofs;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
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

/** Installs the complete M2 Proof decision set and fresh-reopens its canonical module artifact. */
public final class ProofDecisionSetModulePublisher {

  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_PROOF_DECISION_SET";
  private static final String SCHEMA_VERSION = "proven-code-facts-proof-decision-set-v2";
  private static final String ARTIFACT_PREFIX = "proven-code-facts-proof-decision-set";
  private static final String FILE_NAME = "proof-decision-set.json";
  private static final String MODULE_VERSION = "v2";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  public ProofDecisionSetModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    canonicalJson = new CanonicalJsonCodec();
  }

  /** Publishes the M2 result only when it closes to the exact persisted M1 candidate artifact. */
  public ModulePublicationReference publish(
      AnalysisStepModuleAddress destination,
      FactCandidateInputs inputs,
      ModulePublicationReference candidatePublication,
      ProofDecisionSet decisions) {
    requireDestination(destination);
    Objects.requireNonNull(inputs, "fact candidate inputs");
    Objects.requireNonNull(candidatePublication, "candidate publication");
    Objects.requireNonNull(decisions, "proof decisions");
    ArtifactReference candidatePayload = candidatePayload(candidatePublication, decisions);
    List<ArtifactReference> upstream =
        List.of(candidatePayload, inputs.sourceInventoryRef(), inputs.verifiedSnapshotRef())
            .stream()
            .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
            .toList();
    if (upstream.size()
        != upstream.stream().map(ArtifactReference::artifactId).distinct().count()) {
      throw broken();
    }
    List<String> gaps =
        decisions.externalEffectGaps().stream()
            .map(ProofDecisionSet.ExternalEffectGap::gapId)
            .sorted()
            .toList();
    ModuleCompletionStatus status =
        gaps.isEmpty()
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
    CanonicalModulePayload payload =
        payload(destination, decisions, upstream, inputs.controls(), status, gaps);
    InstalledModulePublication installed =
        moduleArtifacts.install(
            new ModuleInstallRequest(
                destination,
                MODULE_VERSION,
                upstream,
                inputs.controls(),
                status,
                gaps,
                List.of(payload)));
    ReopenedModulePublication reopened = moduleArtifacts.reopen(installed.reference());
    if (!installed.reference().equals(reopened.reference())) throw broken();
    return installed.reference();
  }

  private ArtifactReference candidatePayload(
      ModulePublicationReference candidatePublication, ProofDecisionSet decisions) {
    if (!(candidatePublication.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS
        || address.moduleNumber() != 1
        || !"candidates".equals(address.moduleKey())) {
      throw broken();
    }
    ReopenedModulePublication reopened = moduleArtifacts.reopen(candidatePublication);
    if (!candidatePublication.equals(reopened.reference())
        || reopened.payloads().size() != 1
        || reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || !reopened.receipt().gapRefs().isEmpty()) {
      throw broken();
    }
    VerifiedCanonicalPayload payload = reopened.payloads().get(0);
    ArtifactDescriptor descriptor = payload.descriptor();
    if (!"fact-candidate-set.json".equals(descriptor.fileName())
        || !"PROVEN_CODE_FACTS_FACT_CANDIDATE_SET".equals(descriptor.artifactType())
        || !"proven-code-facts-fact-candidate-set-v2".equals(descriptor.schemaVersion())) {
      throw broken();
    }
    JsonNode envelope = canonicalJson.parseCanonical(payload.canonicalUtf8());
    if (!decisions
        .candidateSetId()
        .value()
        .equals(envelope.path("payload").path("candidateSetId").asText())) {
      throw broken();
    }
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress destination,
      ProofDecisionSet decisions,
      List<ArtifactReference> upstream,
      ArtifactControls controls,
      ModuleCompletionStatus status,
      List<String> gaps) {
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(destination));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set("completion", completion(status, gaps));
    withoutArtifactId.set("payload", decisionBody(decisions));
    String artifactId =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode decisionBody(ProofDecisionSet decisions) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("candidateSetId", decisions.candidateSetId().value());
    ArrayNode codeFacts = body.putArray("codeFacts");
    decisions.codeFacts().forEach(fact -> codeFacts.add(codeFact(fact)));
    ArrayNode atomProofs = body.putArray("atomProofs");
    decisions.atomProofs().forEach(proof -> atomProofs.add(atomProof(proof)));
    ArrayNode factDispositions = body.putArray("factDispositions");
    decisions.factDispositions().forEach(value -> factDispositions.add(factDisposition(value)));
    ArrayNode atomDispositions = body.putArray("atomDispositions");
    decisions.atomDispositions().forEach(value -> atomDispositions.add(atomDisposition(value)));
    ArrayNode rootCauses = body.putArray("rootCauseRejections");
    decisions.rootCauseRejections().forEach(value -> rootCauses.add(rootCause(value)));
    ArrayNode externalGaps = body.putArray("externalEffectGaps");
    decisions.externalEffectGaps().forEach(value -> externalGaps.add(externalGap(value)));
    return body;
  }

  private static ObjectNode codeFact(ProofDecisionSet.CodeFact fact) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("factId", fact.factId());
    node.put("candidateDenominatorKey", fact.candidateDenominatorKey());
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
                  .put("type", atom.value().type())
                  .put("canonical", atom.value().canonical());
              item.put("proofId", atom.proofId());
            });
    return node;
  }

  private static ObjectNode atomProof(ProofDecisionSet.AtomProof proof) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("proofId", proof.proofId());
    node.put("candidateDenominatorKey", proof.candidateDenominatorKey());
    node.put("factId", proof.factId());
    node.put("atomId", proof.atomId());
    node.put("rootEvidenceNodeId", proof.rootEvidenceNodeId());
    strings(node.putArray("requiredEvidenceNodeIds"), proof.requiredEvidenceNodeIds());
    strings(node.putArray("requiredProgramEdgeIds"), proof.requiredProgramEdgeIds());
    strings(node.putArray("ruleIds"), proof.ruleIds());
    node.put("status", proof.status());
    return node;
  }

  private static ObjectNode factDisposition(ProofDecisionSet.FactDisposition value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("candidateDenominatorKey", value.candidateDenominatorKey());
    node.put("disposition", value.disposition());
    if (value.admittedFactId() == null) node.putNull("admittedFactId");
    else node.put("admittedFactId", value.admittedFactId());
    if (value.reasonCode() == null) node.putNull("reasonCode");
    else node.put("reasonCode", value.reasonCode());
    return node;
  }

  private static ObjectNode atomDisposition(ProofDecisionSet.AtomDisposition value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("candidateDenominatorKey", value.candidateDenominatorKey());
    node.put("atomKey", value.atomKey());
    node.put("disposition", value.disposition());
    if (value.proofId() == null) node.putNull("proofId");
    else node.put("proofId", value.proofId());
    if (value.reasonCode() == null) node.putNull("reasonCode");
    else node.put("reasonCode", value.reasonCode());
    return node;
  }

  private static ObjectNode rootCause(ProofDecisionSet.RootCauseRejection value) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("candidateDenominatorKey", value.candidateDenominatorKey())
        .put("atomKey", value.atomKey())
        .put("reasonCode", value.reasonCode())
        .put("gapId", value.gapId());
  }

  private static ObjectNode externalGap(ProofDecisionSet.ExternalEffectGap value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("gapId", value.gapId());
    node.put("candidateDenominatorKey", value.candidateDenominatorKey());
    node.put("entryId", value.entryId());
    node.put("boundaryNodeId", value.boundaryNodeId());
    node.put("staticTargetType", value.staticTargetType());
    node.put("staticTargetMethod", value.staticTargetMethod());
    node.put("staticTargetSignature", value.staticTargetSignature());
    node.put("code", value.code());
    strings(node.putArray("basisEvidenceNodeIds"), value.basisEvidenceNodeIds());
    return node;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress destination) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ObjectNode address = node.putObject("address");
    address.put("kind", "ANALYSIS_STEP");
    address.put("runId", destination.runId().value());
    address.put("analysisStepKey", destination.analysisStepKey().wireValue());
    address.put("moduleNumber", destination.moduleNumber());
    address.put("moduleKey", destination.moduleKey());
    node.put("moduleVersion", MODULE_VERSION);
    return node;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode node = JsonNodeFactory.instance.arrayNode();
    values.forEach(
        value ->
            node.addObject()
                .put("artifactId", value.artifactId().value())
                .put("sha256", value.sha256().value()));
    return node;
  }

  private static ObjectNode controls(ArtifactControls controls) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("toolchainSha256", controls.toolchainSha256().value());
    node.put("profileSha256", controls.profileSha256().value());
    node.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) node.putNull("promptBundleSha256");
    else node.put("promptBundleSha256", controls.promptBundleSha256().value());
    node.putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return node;
  }

  private static ObjectNode completion(ModuleCompletionStatus status, List<String> gaps) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("status", status.name());
    strings(node.putArray("gapRefs"), gaps);
    node.putNull("failureRef");
    return node;
  }

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
  }

  private static void requireDestination(AnalysisStepModuleAddress destination) {
    if (destination == null
        || destination.analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS
        || destination.moduleNumber() != 2
        || !"proofs".equals(destination.moduleKey())) {
      throw broken();
    }
  }

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
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

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) length = Math.addExact(length, value.length);
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }
}
