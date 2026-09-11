package org.sourceanalysis.app.analysis.fact.publish;

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
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateSetReader;
import org.sourceanalysis.app.analysis.fact.proofs.PersistedProofDecisionSetReader;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSet;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * Publishes the closed Fact ledger without re-proving source or inventing an external effect.
 *
 * <p>This is the only M3 seam. It fresh-reopens M1/M2 and all three analysis-step predecessors,
 * projects the verified decision ledger into four standalone semantic files, and then lets the
 * receipt-last stores install the four-file module and reader-visible five-file step.
 */
public final class FactLedgerPublicationSpecifier {

  private static final String PROVEN_FACTS_FILE = "proven-facts.json";
  private static final String PROVEN_FACTS_TYPE = "PROVEN_CODE_FACTS_PROVEN_FACTS";
  private static final String PROVEN_FACTS_SCHEMA = "proven-code-facts-proven-facts-v3";
  private static final String PROOF_PACK_FILE = "proof-pack.json";
  private static final String PROOF_PACK_TYPE = "PROVEN_CODE_FACTS_PROOF_PACK";
  private static final String PROOF_PACK_SCHEMA = "proven-code-facts-proof-pack-v3";
  private static final String GAP_LEDGER_FILE = "gap-ledger.json";
  private static final String GAP_LEDGER_TYPE = "PROVEN_CODE_FACTS_GAP_LEDGER";
  private static final String GAP_LEDGER_SCHEMA = "proven-code-facts-gap-ledger-v3";
  private static final String FACT_ACCOUNTING_FILE = "fact-accounting.json";
  private static final String FACT_ACCOUNTING_TYPE = "PROVEN_CODE_FACTS_FACT_ACCOUNTING";
  private static final String FACT_ACCOUNTING_SCHEMA = "proven-code-facts-fact-accounting-v3";
  private static final String MODULE_VERSION = "v3";
  private static final Comparator<String> UTF8_ORDER = FactLedgerPublicationSpecifier::compareUtf8;

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore analysisStepArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the only M3 publisher with its explicit two receipt-last stores. */
  public FactLedgerPublicationSpecifier(
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore analysisStepArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    this.analysisStepArtifacts =
        Objects.requireNonNull(analysisStepArtifacts, "analysis-step artifact store");
    canonicalJson = new CanonicalJsonCodec();
  }

  /**
   * Fresh-reopens M1 and M2, projects their closed decision set, and installs the only valid M3
   * four-file publication.
   */
  public ProvenCodeFactsReference specifyCandidatesAndProofs(
      FactCandidateInputs inputs,
      ModulePublicationReference candidatePublication,
      ModulePublicationReference proofPublication,
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs) {
    try {
      if (inputs == null
          || candidatePublication == null
          || proofPublication == null
          || source == null
          || discovery == null
          || graphs == null) {
        throw broken();
      }
      ReopenedAnalysisStepPublication sourceStep =
          reopenStepInstance(source.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discoveryStep =
          reopenStepInstance(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      ReopenedAnalysisStepPublication graphStep =
          reopenStepInstance(graphs.publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      requireSharedPredecessors(sourceStep, discoveryStep, graphStep, inputs.controls());
      requireSourcePayloadLineage(sourceStep, inputs);

      FactCandidateSet candidates =
          new PersistedFactCandidateSetReader(moduleArtifacts)
              .reopen(candidatePublication, inputs, FactRegistry.standardJavaFacts());
      ProofDecisionSet decisions =
          new PersistedProofDecisionSetReader(moduleArtifacts)
              .reopen(proofPublication, inputs, candidatePublication, candidates);
      ReopenedModulePublication candidateModule = moduleArtifacts.reopen(candidatePublication);
      ReopenedModulePublication proofModule = moduleArtifacts.reopen(proofPublication);
      ArtifactReference candidatePayload = onlyPayload(candidateModule);
      ArtifactReference proofPayload = onlyPayload(proofModule);
      List<LedgerGap> gaps = gaps(candidates, decisions);
      List<String> gapRefs = gaps.stream().map(LedgerGap::gapId).sorted(UTF8_ORDER).toList();
      ModuleCompletionStatus status =
          gapRefs.isEmpty()
              ? ModuleCompletionStatus.SUCCEEDED
              : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
      List<CanonicalModulePayload> payloads =
          payloads(candidates, decisions, proofPayload, gaps).stream()
              .sorted(Comparator.comparing(CanonicalModulePayload::fileName, UTF8_ORDER))
              .toList();
      AnalysisStepModuleAddress moduleAddress =
          new AnalysisStepModuleAddress(
              source.publication().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              3,
              "publish");
      InstalledModulePublication module =
          moduleArtifacts.install(
              new ModuleInstallRequest(
                  moduleAddress,
                  MODULE_VERSION,
                  sortedReferences(List.of(candidatePayload, proofPayload)),
                  inputs.controls(),
                  status,
                  gapRefs,
                  payloads));
      InstalledAnalysisStepPublication step =
          analysisStepArtifacts.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      moduleAddress.runId(), AnalysisStepKey.PROVEN_CODE_FACTS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(source.publication(), discovery.publication(), graphs.publication()),
                  inputs.controls(),
                  status,
                  gapRefs,
                  payloads.stream().map(FactLedgerPublicationSpecifier::stepPayload).toList(),
                  null));
      ReopenedAnalysisStepPublication reopened = analysisStepArtifacts.reopen(step.reference());
      if (!step.reference().equals(reopened.reference())
          || reopened.semanticPayloads().size() != 4) {
        throw broken();
      }
      return new ProvenCodeFactsReference(step.reference());
    } catch (IllegalArgumentException failure) {
      if ("FACT_ACCOUNTING_INVARIANT_BROKEN".equals(failure.getMessage())) throw failure;
      throw broken();
    } catch (RuntimeException failure) {
      throw broken();
    }
  }

  private ReopenedAnalysisStepPublication reopenStepInstance(
      AnalysisStepPublicationReference reference, AnalysisStepKey expected) {
    if (reference == null || reference.address().analysisStepKey() != expected) throw broken();
    ReopenedAnalysisStepPublication reopened = analysisStepArtifacts.reopen(reference);
    if (!reference.equals(reopened.reference())
        || reopened.receipt().address().analysisStepKey() != expected) {
      throw broken();
    }
    return reopened;
  }

  private static void requireSharedPredecessors(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication graphs,
      ArtifactControls controls) {
    if (!source.reference().address().runId().equals(discovery.reference().address().runId())
        || !source.reference().address().runId().equals(graphs.reference().address().runId())
        || !controls.equals(source.receipt().controls())
        || !controls.equals(discovery.receipt().controls())
        || !controls.equals(graphs.receipt().controls())
        || !discovery.receipt().upstreamAnalysisStepReferences().equals(List.of(source.reference()))
        || !graphs
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference()))) {
      throw broken();
    }
  }

  private static void requireSourcePayloadLineage(
      ReopenedAnalysisStepPublication source, FactCandidateInputs inputs) {
    Map<String, ArtifactReference> references = new HashMap<>();
    for (VerifiedCanonicalPayload payload : source.semanticPayloads()) {
      references.put(
          payload.descriptor().fileName(),
          new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()));
    }
    if (!inputs.sourceInventoryRef().equals(references.get("source-inventory.jsonl"))
        || !inputs.verifiedSnapshotRef().equals(references.get("verified-snapshot.json"))) {
      throw broken();
    }
  }

  private static ArtifactReference onlyPayload(ReopenedModulePublication module) {
    if (module.payloads().size() != 1 || module.receipt().payloadArtifacts().size() != 1)
      throw broken();
    VerifiedCanonicalPayload payload = module.payloads().get(0);
    if (!payload.descriptor().equals(module.receipt().payloadArtifacts().get(0))) throw broken();
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private List<CanonicalModulePayload> payloads(
      FactCandidateSet candidates,
      ProofDecisionSet decisions,
      ArtifactReference proofPayload,
      List<LedgerGap> gaps) {
    return List.of(
        standalone(
            PROVEN_FACTS_FILE,
            PROVEN_FACTS_TYPE,
            PROVEN_FACTS_SCHEMA,
            "proven-code-facts-proven-facts",
            factsBody(candidates, decisions, proofPayload)),
        standalone(
            PROOF_PACK_FILE,
            PROOF_PACK_TYPE,
            PROOF_PACK_SCHEMA,
            "proven-code-facts-proof-pack",
            proofsBody(candidates, decisions, proofPayload)),
        standalone(
            GAP_LEDGER_FILE,
            GAP_LEDGER_TYPE,
            GAP_LEDGER_SCHEMA,
            "proven-code-facts-gap-ledger",
            gapsBody(candidates, decisions, proofPayload, gaps)),
        standalone(
            FACT_ACCOUNTING_FILE,
            FACT_ACCOUNTING_TYPE,
            FACT_ACCOUNTING_SCHEMA,
            "proven-code-facts-fact-accounting",
            accountingBody(candidates, decisions, proofPayload)));
  }

  private static ObjectNode factsBody(
      FactCandidateSet candidates, ProofDecisionSet decisions, ArtifactReference proofPayload) {
    ObjectNode body = commonBody(candidates, decisions, proofPayload);
    ArrayNode facts = body.putArray("codeFacts");
    decisions.codeFacts().forEach(value -> facts.add(codeFact(value)));
    ArrayNode dispositions = body.putArray("factDispositions");
    decisions.factDispositions().forEach(value -> dispositions.add(factDisposition(value)));
    return body;
  }

  private static ObjectNode proofsBody(
      FactCandidateSet candidates, ProofDecisionSet decisions, ArtifactReference proofPayload) {
    ObjectNode body = commonBody(candidates, decisions, proofPayload);
    ArrayNode proofs = body.putArray("atomProofs");
    decisions.atomProofs().forEach(value -> proofs.add(atomProof(value)));
    ArrayNode dispositions = body.putArray("atomDispositions");
    decisions.atomDispositions().forEach(value -> dispositions.add(atomDisposition(value)));
    ArrayNode rootCauses = body.putArray("rootCauseRejections");
    decisions.rootCauseRejections().forEach(value -> rootCauses.add(rootCause(value)));
    return body;
  }

  private static ObjectNode gapsBody(
      FactCandidateSet candidates,
      ProofDecisionSet decisions,
      ArtifactReference proofPayload,
      List<LedgerGap> gaps) {
    ObjectNode body = commonBody(candidates, decisions, proofPayload);
    ArrayNode values = body.putArray("gaps");
    gaps.forEach(value -> values.add(value.node()));
    return body;
  }

  private static ObjectNode accountingBody(
      FactCandidateSet candidates, ProofDecisionSet decisions, ArtifactReference proofPayload) {
    ObjectNode body = commonBody(candidates, decisions, proofPayload);
    List<String> candidateKeys =
        sortedStrings(
            candidates.candidates().stream()
                .map(FactLedgerPublicationSpecifier::candidateKey)
                .toList());
    List<String> boundaryCandidateKeys =
        sortedStrings(
            candidates.candidates().stream()
                .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind()))
                .map(FactLedgerPublicationSpecifier::candidateKey)
                .toList());
    List<String> guardCandidateKeys =
        sortedStrings(
            candidates.candidates().stream()
                .filter(candidate -> "JAVA_GUARD_CONDITION".equals(candidate.kind()))
                .map(FactLedgerPublicationSpecifier::candidateKey)
                .toList());
    List<String> exactCallCandidateKeys =
        sortedStrings(
            candidates.candidates().stream()
                .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
                .map(FactLedgerPublicationSpecifier::candidateKey)
                .toList());
    List<String> admittedFacts =
        sortedStrings(
            decisions.codeFacts().stream().map(ProofDecisionSet.CodeFact::factId).toList());
    List<String> rejectedCandidates =
        sortedStrings(
            decisions.factDispositions().stream()
                .filter(value -> "REJECTED_WITH_REASON".equals(value.disposition()))
                .map(ProofDecisionSet.FactDisposition::candidateDenominatorKey)
                .toList());
    List<String> admittedAtoms =
        sortedStrings(
            decisions.atomDispositions().stream()
                .filter(value -> "CLOSED".equals(value.disposition()))
                .map(FactLedgerPublicationSpecifier::atomDispositionKey)
                .toList());
    List<String> rejectedAtoms =
        sortedStrings(
            decisions.atomDispositions().stream()
                .filter(value -> "REJECTED_WITH_REASON".equals(value.disposition()))
                .map(FactLedgerPublicationSpecifier::atomDispositionKey)
                .toList());
    List<String> externalGapIds =
        sortedStrings(
            decisions.externalEffectGaps().stream()
                .map(ProofDecisionSet.ExternalEffectGap::gapId)
                .toList());
    requireAccountingClosure(
        candidates,
        decisions,
        candidateKeys,
        boundaryCandidateKeys,
        guardCandidateKeys,
        exactCallCandidateKeys,
        admittedFacts,
        rejectedCandidates,
        admittedAtoms,
        rejectedAtoms,
        externalGapIds);
    strings(body.putArray("candidateDenominatorKeys"), candidateKeys);
    strings(body.putArray("admittedFactIds"), admittedFacts);
    strings(body.putArray("rejectedCandidateDenominatorKeys"), rejectedCandidates);
    strings(body.putArray("admittedAtomDispositionKeys"), admittedAtoms);
    strings(body.putArray("rejectedAtomDispositionKeys"), rejectedAtoms);
    strings(body.putArray("externalEffectGapIds"), externalGapIds);
    strings(body.putArray("boundaryCandidateDenominatorKeys"), boundaryCandidateKeys);
    strings(body.putArray("guardCandidateDenominatorKeys"), guardCandidateKeys);
    strings(body.putArray("exactCallCandidateDenominatorKeys"), exactCallCandidateKeys);
    body.put("candidateFactCount", candidateKeys.size());
    body.put("admittedFactCount", admittedFacts.size());
    body.put("rejectedFactCount", rejectedCandidates.size());
    body.put("candidateAtomCount", admittedAtoms.size() + rejectedAtoms.size());
    body.put("admittedAtomDispositionCount", admittedAtoms.size());
    body.put("rejectedAtomCount", rejectedAtoms.size());
    body.put("provenFactAtomCount", admittedAtoms.size());
    body.put("externalEffectGapCount", externalGapIds.size());
    return body;
  }

  private static void requireAccountingClosure(
      FactCandidateSet candidates,
      ProofDecisionSet decisions,
      List<String> candidateKeys,
      List<String> boundaryCandidateKeys,
      List<String> guardCandidateKeys,
      List<String> exactCallCandidateKeys,
      List<String> admittedFacts,
      List<String> rejectedCandidates,
      List<String> admittedAtoms,
      List<String> rejectedAtoms,
      List<String> externalGapIds) {
    List<String> partitionedCandidateKeys = new ArrayList<>(boundaryCandidateKeys);
    partitionedCandidateKeys.addAll(guardCandidateKeys);
    partitionedCandidateKeys.addAll(exactCallCandidateKeys);
    if (!sameKeys(
            candidateKeys,
            decisions.factDispositions().stream()
                .map(ProofDecisionSet.FactDisposition::candidateDenominatorKey)
                .toList())
        || !sameKeys(candidateKeys, partitionedCandidateKeys)
        || !sameKeys(
            boundaryCandidateKeys,
            decisions.externalEffectGaps().stream()
                .map(ProofDecisionSet.ExternalEffectGap::candidateDenominatorKey)
                .toList())
        || externalGapIds.size() != boundaryCandidateKeys.size()) {
      throw broken();
    }
    List<String> requiredAtoms =
        sortedStrings(
            candidates.candidates().stream()
                .flatMap(
                    candidate ->
                        candidate.requiredAtoms().stream()
                            .map(atom -> candidateKey(candidate) + "\u0000" + atom.atomKey()))
                .toList());
    if (!sameKeys(
            requiredAtoms,
            decisions.atomDispositions().stream()
                .map(FactLedgerPublicationSpecifier::atomDispositionKey)
                .toList())
        || admittedAtoms.size() + rejectedAtoms.size() != requiredAtoms.size()
        || admittedFacts.size() + rejectedCandidates.size() != candidateKeys.size()) {
      throw broken();
    }
    Map<String, ProofDecisionSet.FactDisposition> dispositionByKey = new HashMap<>();
    for (ProofDecisionSet.FactDisposition disposition : decisions.factDispositions()) {
      if (dispositionByKey.put(disposition.candidateDenominatorKey(), disposition) != null) {
        throw broken();
      }
    }
    for (ProofDecisionSet.CodeFact fact : decisions.codeFacts()) {
      ProofDecisionSet.FactDisposition disposition =
          dispositionByKey.get(fact.candidateDenominatorKey());
      if (disposition == null || !fact.factId().equals(disposition.admittedFactId()))
        throw broken();
    }
  }

  private static boolean sameKeys(List<String> expected, List<String> actual) {
    return expected.equals(sortedStrings(actual));
  }

  private static ObjectNode commonBody(
      FactCandidateSet candidates, ProofDecisionSet decisions, ArtifactReference proofPayload) {
    if (!candidates.candidateSetId().equals(decisions.candidateSetId())) throw broken();
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("candidateSetId", candidates.candidateSetId().value());
    body.putObject("proofDecisionSetRef")
        .put("artifactId", proofPayload.artifactId().value())
        .put("sha256", proofPayload.sha256().value());
    return body;
  }

  private static List<LedgerGap> gaps(FactCandidateSet candidates, ProofDecisionSet decisions) {
    Map<String, FactCandidateSet.FactCandidate> candidateByKey = new HashMap<>();
    for (FactCandidateSet.FactCandidate candidate : candidates.candidates()) {
      if (candidateByKey.put(candidateKey(candidate), candidate) != null) throw broken();
    }
    List<LedgerGap> result = new ArrayList<>();
    for (ProofDecisionSet.ExternalEffectGap gap : decisions.externalEffectGaps()) {
      FactCandidateSet.FactCandidate candidate = candidateByKey.get(gap.candidateDenominatorKey());
      if (candidate == null || !"JAVA_BOUNDARY_INVOCATION".equals(candidate.kind())) throw broken();
      result.add(LedgerGap.external(gap.gapId(), candidate, gap.basisEvidenceNodeIds()));
    }
    for (ProofDecisionSet.RootCauseRejection rejection : decisions.rootCauseRejections()) {
      FactCandidateSet.FactCandidate candidate =
          candidateByKey.get(rejection.candidateDenominatorKey());
      if (candidate == null) throw broken();
      List<String> evidence =
          candidate.evidenceBySubject().stream()
              .flatMap(
                  binding ->
                      java.util.stream.Stream.concat(
                          binding.sourceEvidenceNodeIds().stream(),
                          binding.ruleApplicationEvidenceNodeIds().stream()))
              .distinct()
              .sorted(UTF8_ORDER)
              .toList();
      result.add(LedgerGap.rejection(rejection, candidate, evidence));
    }
    List<LedgerGap> ordered =
        result.stream().sorted(Comparator.comparing(LedgerGap::gapId, UTF8_ORDER)).toList();
    if (ordered.size() != ordered.stream().map(LedgerGap::gapId).distinct().count()) throw broken();
    return ordered;
  }

  private CanonicalModulePayload standalone(
      String fileName,
      String artifactType,
      String schemaVersion,
      String prefix,
      ObjectNode content) {
    ObjectNode withoutId = content.deepCopy();
    withoutId.put("schemaVersion", schemaVersion);
    withoutId.put("artifactType", artifactType);
    withoutId.remove("artifactId");
    String artifactId =
        prefix
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(withoutId).copyToByteArray())));
    ObjectNode complete = withoutId.deepCopy();
    complete.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(complete));
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  private static ObjectNode codeFact(ProofDecisionSet.CodeFact value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("factId", value.factId());
    node.put("candidateDenominatorKey", value.candidateDenominatorKey());
    node.put("kind", value.kind());
    strings(node.putArray("subjectNodeIds"), value.subjectNodeIds());
    ArrayNode atoms = node.putArray("atoms");
    value
        .atoms()
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

  private static ObjectNode atomProof(ProofDecisionSet.AtomProof value) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("proofId", value.proofId());
    node.put("candidateDenominatorKey", value.candidateDenominatorKey());
    node.put("factId", value.factId());
    node.put("atomId", value.atomId());
    node.put("rootEvidenceNodeId", value.rootEvidenceNodeId());
    strings(node.putArray("requiredEvidenceNodeIds"), value.requiredEvidenceNodeIds());
    strings(node.putArray("requiredProgramEdgeIds"), value.requiredProgramEdgeIds());
    strings(node.putArray("ruleIds"), value.ruleIds());
    node.put("status", value.status());
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

  private static void strings(ArrayNode node, List<String> values) {
    values.forEach(node::add);
  }

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> values) {
    List<ArtifactReference> ordered =
        values.stream()
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    if (ordered.size() != ordered.stream().map(ArtifactReference::artifactId).distinct().count())
      throw broken();
    return ordered;
  }

  private static String candidateKey(FactCandidateSet.FactCandidate candidate) {
    return candidate.denominatorKey();
  }

  private static List<String> sortedStrings(List<String> values) {
    List<String> ordered = values.stream().sorted(UTF8_ORDER).toList();
    if (ordered.size() != ordered.stream().distinct().count()) throw broken();
    return ordered;
  }

  private static String atomDispositionKey(ProofDecisionSet.AtomDisposition disposition) {
    return disposition.candidateDenominatorKey() + "\u0000" + disposition.atomKey();
  }

  private static int compareUtf8(String left, String right) {
    return compareUtf8(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private static int compareUtf8(byte[] left, byte[] right) {
    int length = Math.min(left.length, right.length);
    for (int index = 0; index < length; index++) {
      int compared =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (compared != 0) return compared;
    }
    return Integer.compare(left.length, right.length);
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

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("FACT_ACCOUNTING_INVARIANT_BROKEN");
  }

  private record LedgerGap(String gapId, ObjectNode node) {

    private static LedgerGap external(
        String gapId, FactCandidateSet.FactCandidate candidate, List<String> evidence) {
      return new LedgerGap(
          gapId,
          node(
              gapId,
              "EXTERNAL_EFFECT",
              "DATA_FLOW_BINDING_UNPROVEN",
              candidate,
              evidence,
              "APPROVED_EXTERNAL_SEMANTICS_ADAPTER_OR_PROVEN_RETURN_CHAIN",
              "DO_NOT_DESCRIBE_JAVA_BOUNDARY_AS_EXTERNAL_EFFECT",
              "APPROVED_EXTERNAL_SYSTEM_ANALYSIS_STEP"));
    }

    private static LedgerGap rejection(
        ProofDecisionSet.RootCauseRejection rejection,
        FactCandidateSet.FactCandidate candidate,
        List<String> evidence) {
      return new LedgerGap(
          rejection.gapId(),
          node(
              rejection.gapId(),
              "FACT_REJECTION",
              rejection.reasonCode(),
              candidate,
              evidence,
              rejection.reasonCode(),
              "FACT_NOT_ADMITTED",
              rejection.reasonCode()));
    }

    private static ObjectNode node(
        String gapId,
        String kind,
        String code,
        FactCandidateSet.FactCandidate candidate,
        List<String> evidence,
        String missingRequirement,
        String impact,
        String closureRequirement) {
      ObjectNode node = JsonNodeFactory.instance.objectNode();
      node.put("gapId", gapId);
      node.put("kind", kind);
      node.put("code", code);
      strings(node.putArray("affectedEntryIds"), List.of(candidate.entryId()));
      strings(node.putArray("affectedCandidateDenominatorKeys"), List.of(candidateKey(candidate)));
      strings(node.putArray("evidenceNodeIds"), evidence.stream().sorted(UTF8_ORDER).toList());
      node.put("missingRequirement", missingRequirement);
      node.put("impact", impact);
      node.put("closureRequirement", closureRequirement);
      return node;
    }
  }
}
