package org.sourceanalysis.app.analysis.fact.proofs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateSetReader;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * Reopens M2's sole proof-decision payload and verifies it against the exact persisted M1 candidate
 * publication and source lineage.
 *
 * <p>The public seam deliberately accepts only typed identifiers and records. It never takes a
 * filesystem path, raw JSON, source bytes, or a caller-made decision set.
 */
public final class PersistedProofDecisionSetReader {

  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_PROOF_DECISION_SET";
  private static final String SCHEMA_VERSION = "proven-code-facts-proof-decision-set-v2";
  private static final String FILE_NAME = "proof-decision-set.json";
  private static final String MODULE_VERSION = "v2";
  private static final Set<String> ENVELOPE_FIELDS =
      Set.of(
          "artifactId",
          "artifactType",
          "completion",
          "controls",
          "payload",
          "producer",
          "schemaVersion",
          "upstreamArtifacts");
  private static final Set<String> PRODUCER_FIELDS = Set.of("address", "moduleVersion");
  private static final Set<String> PRODUCER_ADDRESS_FIELDS =
      Set.of("analysisStepKey", "kind", "moduleKey", "moduleNumber", "runId");
  private static final Set<String> REFERENCE_FIELDS = Set.of("artifactId", "sha256");
  private static final Set<String> CONTROLS_FIELDS =
      Set.of(
          "artifactPolicyRegistryRef",
          "profileSha256",
          "promptBundleSha256",
          "schemaBundleSha256",
          "toolchainSha256");
  private static final Set<String> COMPLETION_FIELDS = Set.of("failureRef", "gapRefs", "status");
  private static final Set<String> BODY_FIELDS =
      Set.of(
          "atomDispositions",
          "atomProofs",
          "candidateSetId",
          "codeFacts",
          "externalEffectGaps",
          "factDispositions",
          "rootCauseRejections");
  private static final Set<String> CODE_FACT_FIELDS =
      Set.of("atoms", "candidateDenominatorKey", "factId", "kind", "subjectNodeIds");
  private static final Set<String> FACT_ATOM_FIELDS =
      Set.of("atomId", "name", "proofId", "role", "value");
  private static final Set<String> ATOM_VALUE_FIELDS = Set.of("canonical", "type");
  private static final Set<String> ATOM_PROOF_FIELDS =
      Set.of(
          "atomId",
          "candidateDenominatorKey",
          "factId",
          "proofId",
          "requiredEvidenceNodeIds",
          "requiredProgramEdgeIds",
          "rootEvidenceNodeId",
          "ruleIds",
          "status");
  private static final Set<String> FACT_DISPOSITION_FIELDS =
      Set.of("admittedFactId", "candidateDenominatorKey", "disposition", "reasonCode");
  private static final Set<String> ATOM_DISPOSITION_FIELDS =
      Set.of("atomKey", "candidateDenominatorKey", "disposition", "proofId", "reasonCode");
  private static final Set<String> ROOT_CAUSE_FIELDS =
      Set.of("atomKey", "candidateDenominatorKey", "gapId", "reasonCode");
  private static final Set<String> EXTERNAL_GAP_FIELDS =
      Set.of(
          "basisEvidenceNodeIds",
          "boundaryNodeId",
          "candidateDenominatorKey",
          "code",
          "entryId",
          "gapId",
          "staticTargetMethod",
          "staticTargetSignature",
          "staticTargetType");

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the only typed persisted-reader seam for M2 Proof decisions. */
  public PersistedProofDecisionSetReader(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifacts");
    canonicalJson = new CanonicalJsonCodec();
  }

  /**
   * Returns a complete M2 decision set only when its receipt, payload, M1 candidate predecessor,
   * source lineage, and candidate/atom accounting all close.
   */
  public ProofDecisionSet reopen(
      ModulePublicationReference proofPublication,
      FactCandidateInputs inputs,
      ModulePublicationReference candidatePublication,
      FactCandidateSet candidateSet) {
    try {
      if (proofPublication == null
          || inputs == null
          || candidatePublication == null
          || candidateSet == null) {
        throw broken();
      }
      FactCandidateSet persistedCandidates =
          new PersistedFactCandidateSetReader(moduleArtifacts)
              .reopen(candidatePublication, inputs, FactRegistry.standardJavaBoundary());
      if (!persistedCandidates.equals(candidateSet)) throw broken();
      ArtifactReference candidatePayload = candidatePayload(candidatePublication);
      ReopenedModulePublication publication = moduleArtifacts.reopen(proofPublication);
      requirePublication(publication, proofPublication, inputs, candidatePayload);
      VerifiedCanonicalPayload payload = requiredPayload(publication);
      ProofDecisionSet decisions = parse(payload, publication, inputs, candidatePayload);
      if (!candidateSet.candidateSetId().equals(decisions.candidateSetId())) throw broken();
      requireClosure(decisions, candidateSet);
      return decisions;
    } catch (IllegalArgumentException broken) {
      if ("PROOF_PACK_REFERENCE_BROKEN".equals(broken.getMessage())) throw broken;
      throw broken();
    } catch (RuntimeException broken) {
      throw broken();
    }
  }

  private ArtifactReference candidatePayload(ModulePublicationReference candidatePublication) {
    if (!(candidatePublication.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS
        || address.moduleNumber() != 1
        || !"candidates".equals(address.moduleKey())) {
      throw broken();
    }
    ReopenedModulePublication publication = moduleArtifacts.reopen(candidatePublication);
    if (!candidatePublication.equals(publication.reference())
        || publication.payloads().size() != 1
        || publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || !publication.receipt().gapRefs().isEmpty()) {
      throw broken();
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!"fact-candidate-set.json".equals(payload.descriptor().fileName())
        || !"PROVEN_CODE_FACTS_FACT_CANDIDATE_SET".equals(payload.descriptor().artifactType())
        || !"proven-code-facts-fact-candidate-set-v2"
            .equals(payload.descriptor().schemaVersion())) {
      throw broken();
    }
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private void requirePublication(
      ReopenedModulePublication publication,
      ModulePublicationReference requested,
      FactCandidateInputs inputs,
      ArtifactReference candidatePayload) {
    if (publication == null
        || !requested.equals(publication.reference())
        || !(publication.reference().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS
        || address.moduleNumber() != 2
        || !"proofs".equals(address.moduleKey())
        || !inputs.controls().equals(publication.receipt().controls())) {
      throw broken();
    }
    List<ArtifactReference> expectedUpstream =
        List.of(candidatePayload, inputs.sourceInventoryRef(), inputs.verifiedSnapshotRef())
            .stream()
            .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
            .toList();
    if (!expectedUpstream.equals(publication.receipt().upstreamArtifacts())) throw broken();
  }

  private static VerifiedCanonicalPayload requiredPayload(ReopenedModulePublication publication) {
    if (publication.payloads().size() != 1
        || publication.receipt().payloadArtifacts().size() != 1) {
      throw broken();
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!payload.descriptor().equals(publication.receipt().payloadArtifacts().get(0))
        || !FILE_NAME.equals(payload.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_JSON) {
      throw broken();
    }
    return payload;
  }

  private ProofDecisionSet parse(
      VerifiedCanonicalPayload persisted,
      ReopenedModulePublication publication,
      FactCandidateInputs inputs,
      ArtifactReference candidatePayload) {
    JsonNode raw = canonicalJson.parseCanonical(persisted.canonicalUtf8());
    if (!(raw instanceof ObjectNode envelope)) throw broken();
    requireExactFields(envelope, ENVELOPE_FIELDS);
    requireText(envelope, "schemaVersion", SCHEMA_VERSION);
    requireText(envelope, "artifactType", ARTIFACT_TYPE);
    if (!persisted.descriptor().artifactId().equals(artifactId(envelope, "artifactId")))
      throw broken();
    requireProducer(requiredObject(envelope, "producer"), publication);
    List<ArtifactReference> upstream = references(requiredArray(envelope, "upstreamArtifacts"));
    List<ArtifactReference> expectedUpstream =
        List.of(candidatePayload, inputs.sourceInventoryRef(), inputs.verifiedSnapshotRef())
            .stream()
            .sorted(Comparator.comparing(reference -> reference.artifactId().value()))
            .toList();
    if (!upstream.equals(expectedUpstream)
        || !upstream.equals(publication.receipt().upstreamArtifacts())) {
      throw broken();
    }
    if (!controls(requiredObject(envelope, "controls")).equals(publication.receipt().controls())) {
      throw broken();
    }
    ProofDecisionSet decisions = decisionSet(requiredObject(envelope, "payload"));
    requireCompletion(requiredObject(envelope, "completion"), publication, decisions);
    return decisions;
  }

  private static void requireProducer(ObjectNode producer, ReopenedModulePublication publication) {
    requireExactFields(producer, PRODUCER_FIELDS);
    requireText(producer, "moduleVersion", MODULE_VERSION);
    ObjectNode address = requiredObject(producer, "address");
    requireExactFields(address, PRODUCER_ADDRESS_FIELDS);
    if (!(publication.reference().address() instanceof AnalysisStepModuleAddress expected)
        || !"ANALYSIS_STEP".equals(requiredText(address, "kind"))
        || !expected.runId().value().equals(requiredText(address, "runId"))
        || !expected.analysisStepKey().wireValue().equals(requiredText(address, "analysisStepKey"))
        || expected.moduleNumber() != requiredInt(address, "moduleNumber")
        || !expected.moduleKey().equals(requiredText(address, "moduleKey"))) {
      throw broken();
    }
  }

  private static void requireCompletion(
      ObjectNode completion, ReopenedModulePublication publication, ProofDecisionSet decisions) {
    requireExactFields(completion, COMPLETION_FIELDS);
    List<String> gaps = texts(requiredArray(completion, "gapRefs"));
    List<String> expectedGaps =
        decisions.externalEffectGaps().stream()
            .map(ProofDecisionSet.ExternalEffectGap::gapId)
            .sorted()
            .toList();
    ModuleCompletionStatus expectedStatus =
        expectedGaps.isEmpty()
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
    if (!expectedStatus.name().equals(requiredText(completion, "status"))
        || !gaps.equals(expectedGaps)
        || completion.get("failureRef") == null
        || !completion.get("failureRef").isNull()
        || publication.receipt().status() != expectedStatus
        || !publication.receipt().gapRefs().equals(expectedGaps)) {
      throw broken();
    }
  }

  private static ProofDecisionSet decisionSet(ObjectNode body) {
    requireExactFields(body, BODY_FIELDS);
    ArtifactId candidateSetId = artifactId(body, "candidateSetId");
    List<ProofDecisionSet.CodeFact> codeFacts = codeFacts(requiredArray(body, "codeFacts"));
    List<ProofDecisionSet.AtomProof> proofs = proofs(requiredArray(body, "atomProofs"));
    List<ProofDecisionSet.FactDisposition> facts =
        factDispositions(requiredArray(body, "factDispositions"));
    List<ProofDecisionSet.AtomDisposition> atoms =
        atomDispositions(requiredArray(body, "atomDispositions"));
    List<ProofDecisionSet.RootCauseRejection> causes =
        rootCauses(requiredArray(body, "rootCauseRejections"));
    List<ProofDecisionSet.ExternalEffectGap> gaps =
        externalGaps(requiredArray(body, "externalEffectGaps"));
    ProofDecisionSet result =
        new ProofDecisionSet(candidateSetId, codeFacts, proofs, facts, atoms, causes, gaps);
    if (!codeFacts.equals(result.codeFacts())
        || !proofs.equals(result.atomProofs())
        || !facts.equals(result.factDispositions())
        || !atoms.equals(result.atomDispositions())
        || !causes.equals(result.rootCauseRejections())
        || !gaps.equals(result.externalEffectGaps())) {
      throw broken();
    }
    return result;
  }

  private static List<ProofDecisionSet.CodeFact> codeFacts(ArrayNode values) {
    List<ProofDecisionSet.CodeFact> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode node)) throw broken();
      requireExactFields(node, CODE_FACT_FIELDS);
      result.add(
          new ProofDecisionSet.CodeFact(
              requiredText(node, "factId"),
              requiredText(node, "candidateDenominatorKey"),
              requiredText(node, "kind"),
              texts(requiredArray(node, "subjectNodeIds")),
              factAtoms(requiredArray(node, "atoms"))));
    }
    return List.copyOf(result);
  }

  private static List<ProofDecisionSet.FactAtom> factAtoms(ArrayNode values) {
    List<ProofDecisionSet.FactAtom> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode node)) throw broken();
      requireExactFields(node, FACT_ATOM_FIELDS);
      ObjectNode scalar = requiredObject(node, "value");
      requireExactFields(scalar, ATOM_VALUE_FIELDS);
      result.add(
          new ProofDecisionSet.FactAtom(
              requiredText(node, "atomId"),
              requiredText(node, "role"),
              requiredText(node, "name"),
              new ProofDecisionSet.AtomValue(
                  requiredText(scalar, "type"), requiredText(scalar, "canonical")),
              requiredText(node, "proofId")));
    }
    return List.copyOf(result);
  }

  private static List<ProofDecisionSet.AtomProof> proofs(ArrayNode values) {
    List<ProofDecisionSet.AtomProof> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode node)) throw broken();
      requireExactFields(node, ATOM_PROOF_FIELDS);
      result.add(
          new ProofDecisionSet.AtomProof(
              requiredText(node, "proofId"),
              requiredText(node, "candidateDenominatorKey"),
              requiredText(node, "factId"),
              requiredText(node, "atomId"),
              requiredText(node, "rootEvidenceNodeId"),
              texts(requiredArray(node, "requiredEvidenceNodeIds")),
              texts(requiredArray(node, "requiredProgramEdgeIds")),
              texts(requiredArray(node, "ruleIds")),
              requiredText(node, "status")));
    }
    return List.copyOf(result);
  }

  private static List<ProofDecisionSet.FactDisposition> factDispositions(ArrayNode values) {
    List<ProofDecisionSet.FactDisposition> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode node)) throw broken();
      requireExactFields(node, FACT_DISPOSITION_FIELDS);
      result.add(
          new ProofDecisionSet.FactDisposition(
              requiredText(node, "candidateDenominatorKey"),
              requiredText(node, "disposition"),
              nullableText(node, "admittedFactId"),
              nullableText(node, "reasonCode")));
    }
    return List.copyOf(result);
  }

  private static List<ProofDecisionSet.AtomDisposition> atomDispositions(ArrayNode values) {
    List<ProofDecisionSet.AtomDisposition> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode node)) throw broken();
      requireExactFields(node, ATOM_DISPOSITION_FIELDS);
      result.add(
          new ProofDecisionSet.AtomDisposition(
              requiredText(node, "candidateDenominatorKey"),
              requiredText(node, "atomKey"),
              requiredText(node, "disposition"),
              nullableText(node, "proofId"),
              nullableText(node, "reasonCode")));
    }
    return List.copyOf(result);
  }

  private static List<ProofDecisionSet.RootCauseRejection> rootCauses(ArrayNode values) {
    List<ProofDecisionSet.RootCauseRejection> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode node)) throw broken();
      requireExactFields(node, ROOT_CAUSE_FIELDS);
      result.add(
          new ProofDecisionSet.RootCauseRejection(
              requiredText(node, "candidateDenominatorKey"),
              requiredText(node, "atomKey"),
              requiredText(node, "reasonCode"),
              requiredText(node, "gapId")));
    }
    return List.copyOf(result);
  }

  private static List<ProofDecisionSet.ExternalEffectGap> externalGaps(ArrayNode values) {
    List<ProofDecisionSet.ExternalEffectGap> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode node)) throw broken();
      requireExactFields(node, EXTERNAL_GAP_FIELDS);
      result.add(
          new ProofDecisionSet.ExternalEffectGap(
              requiredText(node, "gapId"),
              requiredText(node, "candidateDenominatorKey"),
              requiredText(node, "entryId"),
              requiredText(node, "boundaryNodeId"),
              requiredText(node, "staticTargetType"),
              requiredText(node, "staticTargetMethod"),
              requiredText(node, "staticTargetSignature"),
              requiredText(node, "code"),
              texts(requiredArray(node, "basisEvidenceNodeIds"))));
    }
    return List.copyOf(result);
  }

  private static void requireClosure(ProofDecisionSet decisions, FactCandidateSet candidates) {
    Map<String, FactCandidateSet.FactCandidate> candidateByKey = new HashMap<>();
    for (FactCandidateSet.FactCandidate candidate : candidates.candidates()) {
      String key = denominatorKey(candidate);
      if (candidateByKey.put(key, candidate) != null) throw broken();
    }
    Set<String> keys = candidateByKey.keySet();
    Map<String, ProofDecisionSet.FactDisposition> facts = uniqueFacts(decisions.factDispositions());
    Map<String, ProofDecisionSet.CodeFact> admitted = uniqueCodeFacts(decisions.codeFacts());
    Map<String, ProofDecisionSet.AtomProof> proofs = uniqueProofs(decisions.atomProofs());
    Map<String, ProofDecisionSet.ExternalEffectGap> gaps =
        uniqueExternalGaps(decisions.externalEffectGaps());
    Set<String> boundaryKeys =
        candidateByKey.entrySet().stream()
            .filter(entry -> "JAVA_BOUNDARY_INVOCATION".equals(entry.getValue().kind()))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (!facts.keySet().equals(keys) || !gaps.keySet().equals(boundaryKeys)) throw broken();
    Map<String, ProofDecisionSet.AtomDisposition> atoms =
        uniqueAtomDispositions(decisions.atomDispositions());
    Map<String, ProofDecisionSet.RootCauseRejection> causes =
        uniqueCauses(decisions.rootCauseRejections());
    for (Map.Entry<String, FactCandidateSet.FactCandidate> entry : candidateByKey.entrySet()) {
      String key = entry.getKey();
      FactCandidateSet.FactCandidate candidate = entry.getValue();
      if ("JAVA_BOUNDARY_INVOCATION".equals(candidate.kind())) {
        requireExternalGap(gaps.get(key), candidate, key);
      }
      List<String> expectedAtomKeys =
          candidate.requiredAtoms().stream().map(FactCandidateSet.RequiredAtom::atomKey).toList();
      Set<String> expectedDispositionKeys =
          expectedAtomKeys.stream()
              .map(atomKey -> key + "\u0000" + atomKey)
              .collect(java.util.stream.Collectors.toUnmodifiableSet());
      Set<String> actualDispositionKeys =
          atoms.keySet().stream()
              .filter(value -> value.startsWith(key + "\u0000"))
              .collect(java.util.stream.Collectors.toUnmodifiableSet());
      if (!actualDispositionKeys.equals(expectedDispositionKeys)) throw broken();
      ProofDecisionSet.FactDisposition fact = facts.get(key);
      if ("ADMITTED".equals(fact.disposition())) {
        requireAdmitted(candidate, key, fact, admitted, atoms, proofs, causes, expectedAtomKeys);
      } else {
        requireRejected(key, fact, admitted, atoms, proofs, causes, expectedAtomKeys);
      }
    }
    if (!atoms.keySet().stream().allMatch(key -> key.contains("\u0000"))
        || !causes.keySet().stream().allMatch(key -> key.contains("\u0000"))) {
      throw broken();
    }
  }

  private static void requireExternalGap(
      ProofDecisionSet.ExternalEffectGap gap,
      FactCandidateSet.FactCandidate candidate,
      String key) {
    if (gap == null
        || !key.equals(gap.candidateDenominatorKey())
        || !candidate.entryId().equals(gap.entryId())
        || !candidate.boundaryNodeId().equals(gap.boundaryNodeId())
        || !candidate.staticTargetType().equals(gap.staticTargetType())
        || !candidate.staticTargetMethod().equals(gap.staticTargetMethod())
        || !candidate.staticTargetSignature().equals(gap.staticTargetSignature())) {
      throw broken();
    }
    Set<String> allowedEvidence =
        candidate.evidenceBySubject().stream()
            .flatMap(
                binding ->
                    java.util.stream.Stream.concat(
                        binding.sourceEvidenceNodeIds().stream(),
                        binding.ruleApplicationEvidenceNodeIds().stream()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (!allowedEvidence.containsAll(gap.basisEvidenceNodeIds())) throw broken();
  }

  private static void requireAdmitted(
      FactCandidateSet.FactCandidate candidate,
      String key,
      ProofDecisionSet.FactDisposition disposition,
      Map<String, ProofDecisionSet.CodeFact> facts,
      Map<String, ProofDecisionSet.AtomDisposition> atoms,
      Map<String, ProofDecisionSet.AtomProof> proofs,
      Map<String, ProofDecisionSet.RootCauseRejection> causes,
      List<String> expectedAtomKeys) {
    ProofDecisionSet.CodeFact fact = facts.get(disposition.admittedFactId());
    if (fact == null
        || !key.equals(fact.candidateDenominatorKey())
        || !candidate.kind().equals(fact.kind())) {
      throw broken();
    }
    Map<String, ProofDecisionSet.FactAtom> factAtoms = new HashMap<>();
    for (ProofDecisionSet.FactAtom atom : fact.atoms()) {
      if (factAtoms.put(atom.name(), atom) != null) throw broken();
    }
    if (!factAtoms.keySet().equals(Set.copyOf(expectedAtomKeys))) throw broken();
    for (String atomKey : expectedAtomKeys) {
      ProofDecisionSet.AtomDisposition atomDisposition = atoms.get(key + "\u0000" + atomKey);
      ProofDecisionSet.FactAtom atom = factAtoms.get(atomKey);
      ProofDecisionSet.AtomProof proof = proofs.get(atom.proofId());
      if (atomDisposition == null
          || !"CLOSED".equals(atomDisposition.disposition())
          || !atom.proofId().equals(atomDisposition.proofId())
          || proof == null
          || !key.equals(proof.candidateDenominatorKey())
          || !fact.factId().equals(proof.factId())
          || !atom.atomId().equals(proof.atomId())
          || causes.containsKey(key + "\u0000" + atomKey)) {
        throw broken();
      }
    }
  }

  private static void requireRejected(
      String key,
      ProofDecisionSet.FactDisposition disposition,
      Map<String, ProofDecisionSet.CodeFact> facts,
      Map<String, ProofDecisionSet.AtomDisposition> atoms,
      Map<String, ProofDecisionSet.AtomProof> proofs,
      Map<String, ProofDecisionSet.RootCauseRejection> causes,
      List<String> expectedAtomKeys) {
    if (facts.values().stream().anyMatch(fact -> key.equals(fact.candidateDenominatorKey()))
        || proofs.values().stream()
            .anyMatch(proof -> key.equals(proof.candidateDenominatorKey()))) {
      throw broken();
    }
    List<ProofDecisionSet.AtomDisposition> directFailures =
        expectedAtomKeys.stream()
            .map(atomKey -> atoms.get(key + "\u0000" + atomKey))
            .filter(atom -> atom != null && disposition.reasonCode().equals(atom.reasonCode()))
            .toList();
    if (directFailures.size() != 1) throw broken();
    ProofDecisionSet.AtomDisposition direct = directFailures.get(0);
    ProofDecisionSet.RootCauseRejection cause = causes.get(key + "\u0000" + direct.atomKey());
    if (cause == null || !direct.reasonCode().equals(cause.reasonCode())) throw broken();
    for (String atomKey : expectedAtomKeys) {
      ProofDecisionSet.AtomDisposition atom = atoms.get(key + "\u0000" + atomKey);
      if (atom == null
          || !"REJECTED_WITH_REASON".equals(atom.disposition())
          || (atom == direct) != causes.containsKey(key + "\u0000" + atomKey)) {
        throw broken();
      }
    }
  }

  private static Map<String, ProofDecisionSet.FactDisposition> uniqueFacts(
      List<ProofDecisionSet.FactDisposition> values) {
    Map<String, ProofDecisionSet.FactDisposition> result = new HashMap<>();
    for (ProofDecisionSet.FactDisposition value : values) {
      if (result.put(value.candidateDenominatorKey(), value) != null) throw broken();
    }
    return result;
  }

  private static Map<String, ProofDecisionSet.CodeFact> uniqueCodeFacts(
      List<ProofDecisionSet.CodeFact> values) {
    Map<String, ProofDecisionSet.CodeFact> result = new HashMap<>();
    for (ProofDecisionSet.CodeFact value : values) {
      if (result.put(value.factId(), value) != null) throw broken();
    }
    return result;
  }

  private static Map<String, ProofDecisionSet.AtomProof> uniqueProofs(
      List<ProofDecisionSet.AtomProof> values) {
    Map<String, ProofDecisionSet.AtomProof> result = new HashMap<>();
    for (ProofDecisionSet.AtomProof value : values) {
      if (result.put(value.proofId(), value) != null) throw broken();
    }
    return result;
  }

  private static Map<String, ProofDecisionSet.AtomDisposition> uniqueAtomDispositions(
      List<ProofDecisionSet.AtomDisposition> values) {
    Map<String, ProofDecisionSet.AtomDisposition> result = new HashMap<>();
    for (ProofDecisionSet.AtomDisposition value : values) {
      if (result.put(value.candidateDenominatorKey() + "\u0000" + value.atomKey(), value) != null) {
        throw broken();
      }
    }
    return result;
  }

  private static Map<String, ProofDecisionSet.RootCauseRejection> uniqueCauses(
      List<ProofDecisionSet.RootCauseRejection> values) {
    Map<String, ProofDecisionSet.RootCauseRejection> result = new HashMap<>();
    for (ProofDecisionSet.RootCauseRejection value : values) {
      if (result.put(value.candidateDenominatorKey() + "\u0000" + value.atomKey(), value) != null) {
        throw broken();
      }
    }
    return result;
  }

  private static Map<String, ProofDecisionSet.ExternalEffectGap> uniqueExternalGaps(
      List<ProofDecisionSet.ExternalEffectGap> values) {
    Map<String, ProofDecisionSet.ExternalEffectGap> result = new HashMap<>();
    for (ProofDecisionSet.ExternalEffectGap value : values) {
      if (result.put(value.candidateDenominatorKey(), value) != null) throw broken();
    }
    return result;
  }

  private static String denominatorKey(FactCandidateSet.FactCandidate candidate) {
    return candidate.denominatorKey();
  }

  private static org.sourceanalysis.app.artifact.ArtifactControls controls(ObjectNode value) {
    requireExactFields(value, CONTROLS_FIELDS);
    JsonNode prompt = value.get("promptBundleSha256");
    if (prompt == null || (!prompt.isNull() && !prompt.isTextual())) throw broken();
    ObjectNode policy = requiredObject(value, "artifactPolicyRegistryRef");
    requireExactFields(policy, REFERENCE_FIELDS);
    return new org.sourceanalysis.app.artifact.ArtifactControls(
        Sha256Digest.parse(requiredText(value, "toolchainSha256")),
        Sha256Digest.parse(requiredText(value, "profileSha256")),
        Sha256Digest.parse(requiredText(value, "schemaBundleSha256")),
        prompt.isNull() ? null : Sha256Digest.parse(prompt.textValue()),
        new org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference(
            artifactId(policy, "artifactId"), Sha256Digest.parse(requiredText(policy, "sha256"))));
  }

  private static List<ArtifactReference> references(ArrayNode values) {
    List<ArtifactReference> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode reference)) throw broken();
      requireExactFields(reference, REFERENCE_FIELDS);
      result.add(
          new ArtifactReference(
              artifactId(reference, "artifactId"),
              Sha256Digest.parse(requiredText(reference, "sha256"))));
    }
    List<ArtifactReference> ordered =
        result.stream().sorted(Comparator.comparing(value -> value.artifactId().value())).toList();
    if (!ordered.equals(result)
        || result.size() != result.stream().map(ArtifactReference::artifactId).distinct().count()) {
      throw broken();
    }
    return List.copyOf(result);
  }

  private static List<String> texts(ArrayNode values) {
    List<String> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!value.isTextual()) throw broken();
      result.add(value.textValue());
    }
    return List.copyOf(result);
  }

  private static ObjectNode requiredObject(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (!(value instanceof ObjectNode object)) throw broken();
    return object;
  }

  private static ArrayNode requiredArray(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (!(value instanceof ArrayNode array)) throw broken();
    return array;
  }

  private static ArtifactId artifactId(ObjectNode parent, String fieldName) {
    return ArtifactId.parse(requiredText(parent, fieldName));
  }

  private static String requiredText(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || !value.isTextual()) throw broken();
    return value.textValue();
  }

  private static String nullableText(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || (!value.isNull() && !value.isTextual())) throw broken();
    return value.isNull() ? null : value.textValue();
  }

  private static int requiredInt(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw broken();
    return value.intValue();
  }

  private static void requireText(ObjectNode parent, String fieldName, String expected) {
    if (!expected.equals(requiredText(parent, fieldName))) throw broken();
  }

  private static void requireExactFields(ObjectNode value, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw broken();
  }

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("PROOF_PACK_REFERENCE_BROKEN");
  }
}
