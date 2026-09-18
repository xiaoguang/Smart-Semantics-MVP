package org.sourceanalysis.app.analysis.fact.candidates;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.sourceanalysis.app.analysis.graph.ProgramGraphKind;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Small preinstalled M1 wire fixture for retained historical reader tests. */
public final class HistoricalFactReaderFixture {

  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET";
  private static final String SCHEMA = "proven-code-facts-fact-candidate-set-v3";
  private static final String FILE_NAME = "fact-candidate-set.json";
  private static final String PROOF_ARTIFACT_TYPE = "PROVEN_CODE_FACTS_PROOF_DECISION_SET";
  private static final String PROOF_SCHEMA = "proven-code-facts-proof-decision-set-v3";
  private static final String PROOF_FILE_NAME = "proof-decision-set.json";
  private static final String SNAPSHOT = "snapshot:" + "1".repeat(64);
  private static final String APPLICATION_PROFILE = "application-profile:" + "2".repeat(64);
  private static final AnalysisRunId RUN = AnalysisRunId.parse("analysis-run:" + "3".repeat(64));

  private HistoricalFactReaderFixture() {}

  public static Fixture open() {
    ArtifactControls controls = controls('4');
    List<ArtifactReference> roots =
        List.of(
            ref("code-structure-graph", '5'),
            ref("call-graph", '6'),
            ref("control-flow", '7'),
            ref("data-flow", '8'),
            ref("evidence-graph", '9'));
    ArtifactReference sourceInventory = ref("source-inventory", 'a');
    ArtifactReference verifiedSnapshot = ref("verified-snapshot", 'b');
    List<String> entryIds = List.of("entry:" + "c".repeat(64));
    Map<ProgramGraphKind, FactCandidateInputs.PublicProgramGraph> graphs =
        new EnumMap<>(ProgramGraphKind.class);
    for (ProgramGraphKind kind :
        List.of(
            ProgramGraphKind.CODE_STRUCTURE,
            ProgramGraphKind.CALL,
            ProgramGraphKind.CONTROL_FLOW,
            ProgramGraphKind.DATA_FLOW)) {
      graphs.put(
          kind,
          new FactCandidateInputs.PublicProgramGraph(
              kind,
              roots.get(kind.ordinal()),
              SNAPSHOT,
              APPLICATION_PROFILE,
              entryIds,
              Map.of(),
              Map.of()));
    }
    FactCandidateInputs.PublicEvidenceGraph evidence =
        new FactCandidateInputs.PublicEvidenceGraph(
            roots.get(4), SNAPSHOT, APPLICATION_PROFILE, entryIds, Map.of(), List.of());
    List<ArtifactReference> upstream =
        List.of(
            roots.get(0),
            roots.get(1),
            roots.get(2),
            roots.get(3),
            roots.get(4),
            sourceInventory,
            verifiedSnapshot);
    FactCandidateInputs inputs =
        new FactCandidateInputs(
            SNAPSHOT,
            controls,
            entryIds,
            sourceInventory,
            verifiedSnapshot,
            roots,
            upstream,
            graphs,
            evidence);
    FactCandidateSet.FactCandidate candidate = exactCallCandidate(entryIds.get(0));
    FactCandidateSet candidateSet = FactCandidateSet.create(roots, List.of(candidate), List.of());
    ModulePublicationReference publication =
        new ModulePublicationReference(
            new AnalysisStepModuleAddress(RUN, AnalysisStepKey.PROVEN_CODE_FACTS, 1, "candidates"),
            ModuleArtifactRoot.parse("module-root:" + "d".repeat(64)),
            ModuleReceiptId.parse("module-receipt:" + "e".repeat(64)),
            new Sha256Digest("f".repeat(64)));
    ReopenedModulePublication saved =
        publication(
            publication,
            controls,
            inputs.candidateModuleUpstreamArtifacts(),
            candidateSet,
            ArtifactId.parse("proven-code-facts-fact-candidate-set:" + "0".repeat(64)),
            null);
    ReopenedModulePublication tampered =
        publication(
            publication,
            controls,
            inputs.candidateModuleUpstreamArtifacts(),
            candidateSet,
            ArtifactId.parse("proven-code-facts-fact-candidate-set:" + "0".repeat(64)),
            ArtifactId.parse("proven-code-facts-fact-candidate-set:" + "1".repeat(64)));
    return new Fixture(inputs, candidateSet, publication, saved, tampered, controls);
  }

  public static ArtifactControls controls(char value) {
    String digits = "0123456789abcdef";
    int base = digits.indexOf(value);
    if (base < 0 || base > digits.length() - 5) {
      throw new IllegalArgumentException("fixture control seed must leave four hex digits");
    }
    return new ArtifactControls(
        new Sha256Digest(String.valueOf(digits.charAt(base)).repeat(64)),
        new Sha256Digest(String.valueOf(digits.charAt(base + 1)).repeat(64)),
        new Sha256Digest(String.valueOf(digits.charAt(base + 2)).repeat(64)),
        null,
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse(
                "artifact-policy-registry:" + String.valueOf(digits.charAt(base + 3)).repeat(64)),
            new Sha256Digest(String.valueOf(digits.charAt(base + 4)).repeat(64))));
  }

  private static ReopenedModulePublication proofPublication(
      Fixture fixture, ArtifactId envelopeArtifactId) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ArtifactReference candidatePayload =
        new ArtifactReference(
            fixture.saved().payloads().get(0).descriptor().artifactId(),
            fixture.saved().payloads().get(0).descriptor().sha256());
    List<ArtifactReference> upstream =
        List.of(
                candidatePayload,
                fixture.inputs().sourceInventoryRef(),
                fixture.inputs().verifiedSnapshotRef())
            .stream()
            .sorted(java.util.Comparator.comparing(value -> value.artifactId().value()))
            .toList();
    ModulePublicationReference reference = fixture.proofPublication();
    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", PROOF_SCHEMA);
    envelope.put("artifactType", PROOF_ARTIFACT_TYPE);
    ArtifactId descriptorArtifactId =
        ArtifactId.parse("proven-code-facts-proof-decision-set:" + "2".repeat(64));
    envelope.put(
        "artifactId",
        envelopeArtifactId == null ? descriptorArtifactId.value() : envelopeArtifactId.value());
    ObjectNode producer = envelope.putObject("producer");
    producer.put("moduleVersion", "v3");
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", RUN.value());
    producerAddress.put("analysisStepKey", AnalysisStepKey.PROVEN_CODE_FACTS.wireValue());
    producerAddress.put("moduleNumber", 2);
    producerAddress.put("moduleKey", "proofs");
    references(envelope.putArray("upstreamArtifacts"), upstream);
    envelope.set("controls", controls(fixture.controls()));
    ObjectNode completion = envelope.putObject("completion");
    completion.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    completion.putArray("gapRefs");
    completion.putNull("failureRef");
    ObjectNode payload = envelope.putObject("payload");
    payload.put("candidateSetId", fixture.candidateSet().candidateSetId().value());
    payload.putArray("atomDispositions");
    payload.putArray("atomProofs");
    payload.putArray("codeFacts");
    payload.putArray("externalEffectGaps");
    FactCandidateSet.FactCandidate candidate = fixture.candidateSet().candidates().get(0);
    String reasonCode = "HISTORICAL_FIXTURE_UNPROVEN";
    ObjectNode factDisposition = payload.putArray("factDispositions").addObject();
    factDisposition.put("candidateDenominatorKey", candidate.denominatorKey());
    factDisposition.put("disposition", "REJECTED_WITH_REASON");
    factDisposition.putNull("admittedFactId");
    factDisposition.put("reasonCode", reasonCode);
    ArrayNode atomDispositions = (ArrayNode) payload.get("atomDispositions");
    List<FactCandidateSet.RequiredAtom> orderedAtoms =
        candidate.requiredAtoms().stream()
            .sorted(java.util.Comparator.comparing(FactCandidateSet.RequiredAtom::atomKey))
            .toList();
    for (int index = 0; index < orderedAtoms.size(); index++) {
      FactCandidateSet.RequiredAtom atom = orderedAtoms.get(index);
      ObjectNode disposition = atomDispositions.addObject();
      disposition.put("candidateDenominatorKey", candidate.denominatorKey());
      disposition.put("atomKey", atom.atomKey());
      disposition.put("disposition", "REJECTED_WITH_REASON");
      disposition.putNull("proofId");
      disposition.put(
          "reasonCode", index == 0 ? reasonCode : "HISTORICAL_FIXTURE_" + atom.atomKey());
    }
    ObjectNode rootCause = payload.putArray("rootCauseRejections").addObject();
    rootCause.put("candidateDenominatorKey", candidate.denominatorKey());
    rootCause.put("atomKey", candidate.requiredAtoms().get(0).atomKey());
    rootCause.put("reasonCode", reasonCode);
    rootCause.put("gapId", "gap:" + "7".repeat(64));
    ImmutableBytes bytes = json.encodeCanonical(envelope);
    ArtifactDescriptor descriptor =
        new ArtifactDescriptor(
            PROOF_FILE_NAME,
            PROOF_ARTIFACT_TYPE,
            PROOF_SCHEMA,
            descriptorArtifactId,
            CanonicalMediaType.APPLICATION_JSON,
            bytes.size(),
            new Sha256Digest(sha256(bytes.copyToByteArray())));
    ModuleReceipt receipt =
        new ModuleReceipt(
            "module-receipt-v1",
            reference.moduleReceiptId(),
            reference.address(),
            "v3",
            upstream,
            fixture.controls(),
            ModuleCompletionStatus.SUCCEEDED,
            List.of(descriptor),
            reference.moduleArtifactRoot(),
            List.of());
    return new ReopenedModulePublication(
        reference, receipt, List.of(new VerifiedCanonicalPayload(descriptor, bytes)));
  }

  private static ReopenedModulePublication publication(
      ModulePublicationReference reference,
      ArtifactControls controls,
      List<ArtifactReference> upstream,
      FactCandidateSet candidateSet,
      ArtifactId descriptorArtifactId,
      ArtifactId envelopeArtifactId) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    ObjectNode envelope = JsonNodeFactory.instance.objectNode();
    envelope.put("schemaVersion", SCHEMA);
    envelope.put("artifactType", ARTIFACT_TYPE);
    envelope.put(
        "artifactId",
        envelopeArtifactId == null ? descriptorArtifactId.value() : envelopeArtifactId.value());
    ObjectNode producer = envelope.putObject("producer");
    producer.put("moduleVersion", "v3");
    ObjectNode producerAddress = producer.putObject("address");
    producerAddress.put("kind", "ANALYSIS_STEP");
    producerAddress.put("runId", RUN.value());
    producerAddress.put("analysisStepKey", AnalysisStepKey.PROVEN_CODE_FACTS.wireValue());
    producerAddress.put("moduleNumber", 1);
    producerAddress.put("moduleKey", "candidates");
    references(envelope.putArray("upstreamArtifacts"), upstream);
    envelope.set("controls", controls(controls));
    ObjectNode completion = envelope.putObject("completion");
    completion.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    completion.putArray("gapRefs");
    completion.putNull("failureRef");
    ObjectNode payload = envelope.putObject("payload");
    payload.put("candidateSetId", candidateSet.candidateSetId().value());
    ArrayNode candidates = payload.putArray("candidates");
    for (FactCandidateSet.FactCandidate candidate : candidateSet.candidates()) {
      candidates.add(candidate(candidate));
    }
    payload.putArray("notApplicableDispositions");
    ObjectNode denominator = payload.putObject("denominator");
    ArrayNode applicableKeys = denominator.putArray("applicableKeys");
    candidateSet.candidates().forEach(candidate -> applicableKeys.add(candidate.denominatorKey()));
    denominator.putArray("notApplicableKeys");
    references(payload.putArray("sourceGraphRoots"), candidateSet.sourceGraphRoots());
    ImmutableBytes bytes = json.encodeCanonical(envelope);
    ArtifactDescriptor descriptor =
        new ArtifactDescriptor(
            FILE_NAME,
            ARTIFACT_TYPE,
            SCHEMA,
            descriptorArtifactId,
            CanonicalMediaType.APPLICATION_JSON,
            bytes.size(),
            new Sha256Digest(sha256(bytes.copyToByteArray())));
    ModuleReceipt receipt =
        new ModuleReceipt(
            "module-receipt-v1",
            reference.moduleReceiptId(),
            reference.address(),
            "v3",
            upstream,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(descriptor),
            reference.moduleArtifactRoot(),
            List.of());
    return new ReopenedModulePublication(
        reference, receipt, List.of(new VerifiedCanonicalPayload(descriptor, bytes)));
  }

  private static ObjectNode controls(ArtifactControls values) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("toolchainSha256", values.toolchainSha256().value());
    result.put("profileSha256", values.profileSha256().value());
    result.put("schemaBundleSha256", values.schemaBundleSha256().value());
    result.putNull("promptBundleSha256");
    result
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", values.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return result;
  }

  private static FactCandidateSet.FactCandidate exactCallCandidate(String entryId) {
    String callSite = id("call-site", '1');
    String callTargetEdge = id("call-target-edge", '2');
    String targetMethod = id("target-method", '3');
    List<FactCandidateSet.SubjectEvidenceBinding> evidence =
        List.of(
            evidence(callSite, '4'), evidence(callTargetEdge, '5'), evidence(targetMethod, '6'));
    List<FactCandidateSet.RequiredAtom> atoms =
        List.of(
            atom("INVOCATION_CALL_ID"),
            atom("STATIC_TARGET_TYPE"),
            atom("STATIC_TARGET_METHOD"),
            atom("STATIC_TARGET_SIGNATURE"));
    return new FactCandidateSet.FactCandidate(
        "JAVA_EXACT_CALL",
        entryId,
        "JAVA_EXACT_CALL",
        callSite,
        callTargetEdge,
        targetMethod,
        "com.example.Target#invoke(java.lang.String)",
        evidence,
        atoms);
  }

  private static FactCandidateSet.SubjectEvidenceBinding evidence(String subjectId, char seed) {
    return new FactCandidateSet.SubjectEvidenceBinding(
        subjectId, List.of(id("source-evidence", seed)), List.of(id("rule-evidence", seed)));
  }

  private static FactCandidateSet.RequiredAtom atom(String atomKey) {
    return new FactCandidateSet.RequiredAtom(
        atomKey, "RELATIONSHIP", "SYMBOL_REF", List.of("SOURCE_EXCERPT", "RULE_APPLICATION"));
  }

  private static ObjectNode candidate(FactCandidateSet.FactCandidate value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("callSiteNodeId", value.callSiteNodeId());
    result.put("callTargetEdgeId", value.callTargetEdgeId());
    result.put("candidateFactKey", value.candidateFactKey());
    result.put("entryId", value.entryId());
    ArrayNode evidence = result.putArray("evidenceNodeIdsBySubject");
    for (FactCandidateSet.SubjectEvidenceBinding binding : value.evidenceBySubject()) {
      ObjectNode item = evidence.addObject();
      item.put("subjectElementId", binding.subjectElementId());
      ArrayNode sourceEvidence = item.putArray("sourceEvidenceNodeIds");
      binding.sourceEvidenceNodeIds().forEach(sourceEvidence::add);
      ArrayNode ruleEvidence = item.putArray("ruleApplicationEvidenceNodeIds");
      binding.ruleApplicationEvidenceNodeIds().forEach(ruleEvidence::add);
    }
    result.put("kind", value.kind());
    ArrayNode atoms = result.putArray("requiredAtoms");
    for (FactCandidateSet.RequiredAtom atom : value.requiredAtoms()) {
      ObjectNode item = atoms.addObject();
      item.put("atomKey", atom.atomKey());
      item.put("role", atom.role());
      item.put("valueType", atom.valueType());
      item.putArray("expectedEvidenceKinds").add("SOURCE_EXCERPT").add("RULE_APPLICATION");
    }
    result.put("targetCanonicalMethod", value.targetCanonicalMethod());
    result.put("targetMethodNodeId", value.targetMethodNodeId());
    return result;
  }

  private static void references(ArrayNode target, List<ArtifactReference> values) {
    values.forEach(
        value ->
            target
                .addObject()
                .put("artifactId", value.artifactId().value())
                .put("sha256", value.sha256().value()));
  }

  private static ArtifactReference ref(String prefix, char value) {
    String hex = String.valueOf(value).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + hex), new Sha256Digest(hex));
  }

  private static String id(String prefix, char value) {
    return prefix + ":" + String.valueOf(value).repeat(64);
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public record Fixture(
      FactCandidateInputs inputs,
      FactCandidateSet candidateSet,
      ModulePublicationReference publication,
      ReopenedModulePublication saved,
      ReopenedModulePublication tampered,
      ArtifactControls controls) {

    public ModulePublicationReference proofPublication() {
      return new ModulePublicationReference(
          new AnalysisStepModuleAddress(RUN, AnalysisStepKey.PROVEN_CODE_FACTS, 2, "proofs"),
          ModuleArtifactRoot.parse("module-root:" + "3".repeat(64)),
          ModuleReceiptId.parse("module-receipt:" + "4".repeat(64)),
          new Sha256Digest("5".repeat(64)));
    }

    public ReopenedModulePublication proof() {
      return HistoricalFactReaderFixture.proofPublication(this, null);
    }

    public ReopenedModulePublication tamperedProof() {
      return HistoricalFactReaderFixture.proofPublication(
          this, ArtifactId.parse("proven-code-facts-proof-decision-set:" + "6".repeat(64)));
    }

    public CanonicalModuleArtifactStore store() {
      return storeFor(saved);
    }

    public CanonicalModuleArtifactStore tamperedStore() {
      return storeFor(tampered);
    }

    public CanonicalModuleArtifactStore proofStore() {
      return storeFor(List.of(saved, proof()));
    }

    public CanonicalModuleArtifactStore tamperedProofStore() {
      return storeFor(List.of(saved, tamperedProof()));
    }

    public CanonicalModuleArtifactStore storeFor(ReopenedModulePublication value) {
      return storeFor(List.of(value));
    }

    private CanonicalModuleArtifactStore storeFor(List<ReopenedModulePublication> values) {
      return new CanonicalModuleArtifactStore() {
        @Override
        public InstalledModulePublication install(ModuleInstallRequest request) {
          throw new AssertionError("historical reader fixture must not install");
        }

        @Override
        public CanonicalArtifactPolicy resolveArtifactPolicy(
            org.sourceanalysis.app.artifact.ArtifactPolicyKey key) {
          throw new AssertionError("historical reader fixture must not resolve policy");
        }

        @Override
        public ReopenedModulePublication reopen(ModulePublicationReference requested) {
          return values.stream()
              .filter(value -> value.reference().equals(requested))
              .findFirst()
              .orElseThrow(
                  () -> new IllegalArgumentException("historical publication ownership mismatch"));
        }
      };
    }

    public FactCandidateInputs inputsWithControls(ArtifactControls changed) {
      return new FactCandidateInputs(
          inputs.snapshotId(),
          changed,
          inputs.entryIds(),
          inputs.sourceInventoryRef(),
          inputs.verifiedSnapshotRef(),
          inputs.sourceGraphRoots(),
          inputs.candidateModuleUpstreamArtifacts(),
          inputs.programGraphs(),
          inputs.evidenceGraph());
    }
  }
}
