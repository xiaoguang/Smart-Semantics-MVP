package org.sourceanalysis.app.analysis.fact.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSet;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;

/** RED for the M3 semantic Fact ledger and its exact four-file analysis-step publication. */
class ProvenCodeFactsPublicationSpecifierTest {

  private static final String SPECIFIER_CLASS =
      "org.sourceanalysis.app.analysis.fact.publish.FactLedgerPublicationSpecifier";
  private static final String REFERENCE_CLASS =
      "org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference";

  @TempDir Path temporaryDirectory;

  @Test
  void publishesTheExactFourFileFactLedgerAndFiveFileReaderVisibleStep() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("fact-ledger-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      ProofDecisionSet decisions =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(
                  candidates,
                  inputs,
                  fixture.sourceInventory(),
                  ProofRuleRegistry.standardJavaBoundary());
      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 1, "candidates"), inputs, candidates);
      ModulePublicationReference proofPublication =
          new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);

      Object reference =
          specify(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              inputs,
              candidatePublication,
              proofPublication,
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              fixture.programGraphs());
      Method publication = Class.forName(REFERENCE_CLASS).getMethod("publication");
      Object rawPublication = publication.invoke(reference);
      assertThat(rawPublication)
          .isInstanceOf(org.sourceanalysis.app.artifact.AnalysisStepPublicationReference.class);
      ReopenedAnalysisStepPublication reopened =
          fixture
              .stepArtifacts()
              .reopen(
                  (org.sourceanalysis.app.artifact.AnalysisStepPublicationReference)
                      rawPublication);

      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "fact-accounting.json", "gap-ledger.json", "proof-pack.json", "proven-facts.json");
      assertThat(reopened.receipt().status()).isEqualTo(ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
      assertThat(reopened.semanticPayloads()).hasSize(4);
      Set<String> candidateKeys =
          candidates.candidates().stream()
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      Set<String> boundaryKeys = candidateKeys(candidates, "JAVA_BOUNDARY_INVOCATION");
      Set<String> guardKeys = candidateKeys(candidates, "JAVA_GUARD_CONDITION");
      Set<String> exactKeys = candidateKeys(candidates, "JAVA_EXACT_CALL");
      assertThat(boundaryKeys).hasSize(2);
      assertThat(disjointUnion(boundaryKeys, guardKeys, exactKeys))
          .containsExactlyInAnyOrderElementsOf(candidateKeys);
      assertThat(reopened.receipt().gapRefs())
          .containsExactlyElementsOf(
              sortedStrings(
                  decisions.externalEffectGaps().stream()
                      .map(ProofDecisionSet.ExternalEffectGap::gapId)
                      .toList()));
      Map<String, String> schemas =
          Map.of(
              "fact-accounting.json", "proven-code-facts-fact-accounting-v3",
              "gap-ledger.json", "proven-code-facts-gap-ledger-v3",
              "proof-pack.json", "proven-code-facts-proof-pack-v3",
              "proven-facts.json", "proven-code-facts-proven-facts-v3");
      assertThat(reopened.receipt().semanticArtifacts())
          .extracting(descriptor -> descriptor.schemaVersion())
          .containsExactlyInAnyOrderElementsOf(schemas.values());
      for (var payload : reopened.semanticPayloads()) {
        assertThat(payload.descriptor().schemaVersion())
            .isEqualTo(schemas.get(payload.descriptor().fileName()));
      }
      JsonNode accounting = payload(reopened, "fact-accounting.json");
      assertThat(accounting.path("candidateFactCount").asInt()).isEqualTo(candidateKeys.size());
      assertThat(accounting.path("admittedFactCount").asInt())
          .isEqualTo(decisions.codeFacts().size());
      assertStrings(array(accounting, "candidateDenominatorKeys"), sortedStrings(candidateKeys));
      assertStrings(
          array(accounting, "boundaryCandidateDenominatorKeys"), sortedStrings(boundaryKeys));
      assertStrings(array(accounting, "guardCandidateDenominatorKeys"), sortedStrings(guardKeys));
      assertStrings(
          array(accounting, "exactCallCandidateDenominatorKeys"), sortedStrings(exactKeys));
    }
  }

  @Test
  void publishesSharedExactCallFactLedgerWithDisjointV3AccountingAndFreshReopen() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("exact-call-fact-ledger-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      Set<String> candidateKeys =
          candidates.candidates().stream()
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      Set<String> boundaryKeys = candidateKeys(candidates, "JAVA_BOUNDARY_INVOCATION");
      Set<String> guardKeys = candidateKeys(candidates, "JAVA_GUARD_CONDITION");
      Set<String> exactKeys = candidateKeys(candidates, "JAVA_EXACT_CALL");
      assertThat(boundaryKeys).hasSize(2);
      assertThat(exactKeys).hasSize(3);
      assertThat(disjointUnion(boundaryKeys, guardKeys, exactKeys))
          .containsExactlyInAnyOrderElementsOf(candidateKeys);

      ProofDecisionSet decisions =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(
                  candidates,
                  inputs,
                  fixture.sourceInventory(),
                  ProofRuleRegistry.standardJavaBoundary());
      assertThat(decisions.codeFacts())
          .extracting(ProofDecisionSet.CodeFact::candidateDenominatorKey)
          .containsExactlyInAnyOrderElementsOf(candidateKeys);
      assertThat(decisions.atomProofs())
          .filteredOn(proof -> exactKeys.contains(proof.candidateDenominatorKey()))
          .hasSize(exactKeys.size() * 4)
          .allSatisfy(proof -> assertThat(proof.status()).isEqualTo("CLOSED"));
      assertThat(decisions.externalEffectGaps())
          .extracting(ProofDecisionSet.ExternalEffectGap::candidateDenominatorKey)
          .containsExactlyInAnyOrderElementsOf(boundaryKeys);
      assertThat(decisions.externalEffectGaps())
          .filteredOn(gap -> exactKeys.contains(gap.candidateDenominatorKey()))
          .isEmpty();

      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 1, "candidates"), inputs, candidates);
      ModulePublicationReference proofPublication =
          new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);
      ProvenCodeFactsReference reference =
          new FactLedgerPublicationSpecifier(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .specifyCandidatesAndProofs(
                  inputs,
                  candidatePublication,
                  proofPublication,
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());

      ReopenedAnalysisStepPublication reopened =
          fixture.stepArtifacts().reopen(reference.publication());
      ReopenedAnalysisStepPublication freshReopened =
          fixture.stepArtifacts().reopen(reference.publication());
      assertThat(freshReopened.reference()).isEqualTo(reopened.reference());
      assertThat(freshReopened.receipt()).isEqualTo(reopened.receipt());
      assertThat(freshReopened.semanticPayloads()).isEqualTo(reopened.semanticPayloads());
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "fact-accounting.json", "gap-ledger.json", "proof-pack.json", "proven-facts.json");
      assertThat(reopened.semanticPayloads()).hasSize(4);
      assertThat(reopened.receipt().semanticArtifacts()).hasSize(4);
      assertThat(reopened.receipt().status()).isEqualTo(ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
      assertThat(reopened.receipt().gapRefs())
          .containsExactlyElementsOf(
              sortedStrings(
                  decisions.externalEffectGaps().stream()
                      .map(ProofDecisionSet.ExternalEffectGap::gapId)
                      .toList()));

      Map<String, String> schemas =
          Map.of(
              "fact-accounting.json", "proven-code-facts-fact-accounting-v3",
              "gap-ledger.json", "proven-code-facts-gap-ledger-v3",
              "proof-pack.json", "proven-code-facts-proof-pack-v3",
              "proven-facts.json", "proven-code-facts-proven-facts-v3");
      for (var payload : reopened.semanticPayloads()) {
        String fileName = payload.descriptor().fileName();
        assertThat(payload.descriptor().schemaVersion()).isEqualTo(schemas.get(fileName));
        JsonNode body = payload(reopened, fileName);
        assertThat(body.isObject()).isTrue();
        assertThat(body.path("schemaVersion").asText()).isEqualTo(schemas.get(fileName));
        assertThat(body.path("candidateSetId").asText())
            .isEqualTo(candidates.candidateSetId().value());
      }

      JsonNode provenFacts = payload(reopened, "proven-facts.json");
      JsonNode codeFacts = array(provenFacts, "codeFacts");
      JsonNode factDispositions = array(provenFacts, "factDispositions");
      assertThat(codeFacts).hasSize(decisions.codeFacts().size());
      assertThat(factDispositions).hasSize(decisions.factDispositions().size());
      for (ProofDecisionSet.CodeFact fact : decisions.codeFacts()) {
        JsonNode node =
            byText(codeFacts, "candidateDenominatorKey", fact.candidateDenominatorKey());
        assertThat(node.path("factId").asText()).isEqualTo(fact.factId());
        assertThat(node.path("kind").asText()).isEqualTo(fact.kind());
        assertStrings(array(node, "subjectNodeIds"), fact.subjectNodeIds());
        JsonNode atoms = array(node, "atoms");
        assertThat(atoms).hasSize(fact.atoms().size());
        for (ProofDecisionSet.FactAtom atom : fact.atoms()) {
          JsonNode atomNode = byText(atoms, "atomId", atom.atomId());
          assertThat(atomNode.path("role").asText()).isEqualTo(atom.role());
          assertThat(atomNode.path("name").asText()).isEqualTo(atom.name());
          assertThat(atomNode.path("value").path("type").asText()).isEqualTo(atom.value().type());
          assertThat(atomNode.path("value").path("canonical").asText())
              .isEqualTo(atom.value().canonical());
          assertThat(atomNode.path("proofId").asText()).isEqualTo(atom.proofId());
        }
      }
      for (ProofDecisionSet.FactDisposition disposition : decisions.factDispositions()) {
        JsonNode node =
            byText(
                factDispositions, "candidateDenominatorKey", disposition.candidateDenominatorKey());
        assertThat(node.path("disposition").asText()).isEqualTo(disposition.disposition());
        assertNullableText(node, "admittedFactId", disposition.admittedFactId());
        assertNullableText(node, "reasonCode", disposition.reasonCode());
      }

      JsonNode proofPack = payload(reopened, "proof-pack.json");
      JsonNode atomProofs = array(proofPack, "atomProofs");
      JsonNode atomDispositions = array(proofPack, "atomDispositions");
      JsonNode rootCauseRejections = array(proofPack, "rootCauseRejections");
      assertThat(atomProofs).hasSize(decisions.atomProofs().size());
      assertThat(atomDispositions).hasSize(decisions.atomDispositions().size());
      assertThat(rootCauseRejections).hasSize(decisions.rootCauseRejections().size());
      for (ProofDecisionSet.AtomProof proof : decisions.atomProofs()) {
        JsonNode node = byText(atomProofs, "proofId", proof.proofId());
        assertThat(node.path("candidateDenominatorKey").asText())
            .isEqualTo(proof.candidateDenominatorKey());
        assertThat(node.path("factId").asText()).isEqualTo(proof.factId());
        assertThat(node.path("atomId").asText()).isEqualTo(proof.atomId());
        assertThat(node.path("rootEvidenceNodeId").asText()).isEqualTo(proof.rootEvidenceNodeId());
        assertStrings(array(node, "requiredEvidenceNodeIds"), proof.requiredEvidenceNodeIds());
        assertStrings(array(node, "requiredProgramEdgeIds"), proof.requiredProgramEdgeIds());
        assertStrings(array(node, "ruleIds"), proof.ruleIds());
        assertThat(node.path("status").asText()).isEqualTo(proof.status());
      }
      for (ProofDecisionSet.AtomDisposition disposition : decisions.atomDispositions()) {
        JsonNode node =
            byTwoText(
                atomDispositions,
                "candidateDenominatorKey",
                disposition.candidateDenominatorKey(),
                "atomKey",
                disposition.atomKey());
        assertThat(node.path("disposition").asText()).isEqualTo(disposition.disposition());
        assertNullableText(node, "proofId", disposition.proofId());
        assertNullableText(node, "reasonCode", disposition.reasonCode());
      }

      JsonNode gapLedger = payload(reopened, "gap-ledger.json");
      JsonNode gaps = array(gapLedger, "gaps");
      assertThat(gaps).hasSize(decisions.externalEffectGaps().size());
      for (ProofDecisionSet.ExternalEffectGap gap : decisions.externalEffectGaps()) {
        JsonNode node = byText(gaps, "gapId", gap.gapId());
        assertThat(node.path("kind").asText()).isEqualTo("EXTERNAL_EFFECT");
        assertThat(node.path("code").asText()).isEqualTo(gap.code());
        assertStrings(
            array(node, "affectedCandidateDenominatorKeys"),
            List.of(gap.candidateDenominatorKey()));
        assertThat(gap.candidateDenominatorKey()).isIn(boundaryKeys);
      }

      JsonNode accounting = payload(reopened, "fact-accounting.json");
      assertStrings(array(accounting, "candidateDenominatorKeys"), sortedStrings(candidateKeys));
      assertStrings(
          array(accounting, "boundaryCandidateDenominatorKeys"), sortedStrings(boundaryKeys));
      assertStrings(array(accounting, "guardCandidateDenominatorKeys"), sortedStrings(guardKeys));
      assertStrings(
          array(accounting, "exactCallCandidateDenominatorKeys"), sortedStrings(exactKeys));
      assertStrings(
          array(accounting, "admittedFactIds"),
          sortedStrings(
              decisions.codeFacts().stream().map(ProofDecisionSet.CodeFact::factId).toList()));
      assertStrings(
          array(accounting, "rejectedCandidateDenominatorKeys"),
          sortedStrings(
              decisions.factDispositions().stream()
                  .filter(value -> "REJECTED_WITH_REASON".equals(value.disposition()))
                  .map(ProofDecisionSet.FactDisposition::candidateDenominatorKey)
                  .toList()));
      assertStrings(
          array(accounting, "admittedAtomDispositionKeys"),
          sortedStrings(
              decisions.atomDispositions().stream()
                  .filter(value -> "CLOSED".equals(value.disposition()))
                  .map(value -> value.candidateDenominatorKey() + "\u0000" + value.atomKey())
                  .toList()));
      assertStrings(
          array(accounting, "rejectedAtomDispositionKeys"),
          sortedStrings(
              decisions.atomDispositions().stream()
                  .filter(value -> "REJECTED_WITH_REASON".equals(value.disposition()))
                  .map(value -> value.candidateDenominatorKey() + "\u0000" + value.atomKey())
                  .toList()));
      assertStrings(
          array(accounting, "externalEffectGapIds"),
          sortedStrings(
              decisions.externalEffectGaps().stream()
                  .map(ProofDecisionSet.ExternalEffectGap::gapId)
                  .toList()));
      assertThat(accounting.path("candidateFactCount").asInt()).isEqualTo(candidateKeys.size());
      assertThat(accounting.path("admittedFactCount").asInt())
          .isEqualTo(decisions.codeFacts().size());
      assertThat(accounting.path("rejectedFactCount").asInt())
          .isEqualTo(
              decisions.factDispositions().stream()
                  .filter(value -> "REJECTED_WITH_REASON".equals(value.disposition()))
                  .count());
      assertThat(accounting.path("candidateAtomCount").asInt())
          .isEqualTo(decisions.atomDispositions().size());
      assertThat(accounting.path("admittedAtomDispositionCount").asInt())
          .isEqualTo(
              decisions.atomDispositions().stream()
                  .filter(value -> "CLOSED".equals(value.disposition()))
                  .count());
      assertThat(accounting.path("rejectedAtomCount").asInt())
          .isEqualTo(
              decisions.atomDispositions().stream()
                  .filter(value -> "REJECTED_WITH_REASON".equals(value.disposition()))
                  .count());
      assertThat(accounting.path("provenFactAtomCount").asInt())
          .isEqualTo(
              decisions.atomDispositions().stream()
                  .filter(value -> "CLOSED".equals(value.disposition()))
                  .count());
      assertThat(accounting.path("externalEffectGapCount").asInt())
          .isEqualTo(decisions.externalEffectGaps().size());
    }
  }

  private static Set<String> candidateKeys(FactCandidateSet candidates, String kind) {
    return candidates.candidates().stream()
        .filter(candidate -> kind.equals(candidate.kind()))
        .map(FactCandidateSet.FactCandidate::denominatorKey)
        .collect(Collectors.toUnmodifiableSet());
  }

  @SafeVarargs
  private static Set<String> disjointUnion(Set<String>... partitions) {
    Set<String> result = new java.util.HashSet<>();
    for (Set<String> partition : partitions) {
      if (!partition.isEmpty()) assertThat(result).doesNotContainAnyElementsOf(partition);
      result.addAll(partition);
    }
    return Set.copyOf(result);
  }

  private static List<String> sortedStrings(java.util.Collection<String> values) {
    return values.stream().sorted(Comparator.naturalOrder()).toList();
  }

  private static JsonNode array(JsonNode parent, String field) {
    JsonNode value = parent.path(field);
    assertThat(value.isArray()).as("%s must be an array", field).isTrue();
    return value;
  }

  private static JsonNode byText(JsonNode values, String field, String expected) {
    for (JsonNode value : values) {
      if (expected.equals(value.path(field).asText())) return value;
    }
    throw new AssertionError("missing " + field + "=" + expected);
  }

  private static JsonNode byTwoText(
      JsonNode values, String firstField, String first, String secondField, String second) {
    for (JsonNode value : values) {
      if (first.equals(value.path(firstField).asText())
          && second.equals(value.path(secondField).asText())) {
        return value;
      }
    }
    throw new AssertionError(
        "missing " + firstField + "=" + first + ", " + secondField + "=" + second);
  }

  private static void assertStrings(JsonNode values, List<String> expected) {
    assertThat(values.isArray()).isTrue();
    List<String> actual = new ArrayList<>();
    values.forEach(value -> actual.add(value.asText()));
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static void assertNullableText(JsonNode node, String field, String expected) {
    if (expected == null) assertThat(node.path(field).isNull()).isTrue();
    else assertThat(node.path(field).asText()).isEqualTo(expected);
  }

  @Test
  void projectsRejectedCandidatesAsFactRejectionAndExternalEffectGaps() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(
            temporaryDirectory.resolve("fact-ledger-rejection-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
      ProofDecisionSet decisions = rejectedDecisions(candidates);
      ModulePublicationReference candidatePublication =
          new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 1, "candidates"), inputs, candidates);
      ModulePublicationReference proofPublication =
          new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
              .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);

      Object reference =
          specify(
              fixture.moduleArtifacts(),
              fixture.stepArtifacts(),
              inputs,
              candidatePublication,
              proofPublication,
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              fixture.programGraphs());
      ReopenedAnalysisStepPublication reopened =
          fixture
              .stepArtifacts()
              .reopen(
                  (org.sourceanalysis.app.artifact.AnalysisStepPublicationReference)
                      Class.forName(REFERENCE_CLASS).getMethod("publication").invoke(reference));

      assertThat(payload(reopened, "proven-facts.json").path("codeFacts")).isEmpty();
      Set<String> candidateKeys =
          candidates.candidates().stream()
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .collect(Collectors.toUnmodifiableSet());
      Set<String> boundaryKeys = candidateKeys(candidates, "JAVA_BOUNDARY_INVOCATION");
      Set<String> exactKeys = candidateKeys(candidates, "JAVA_EXACT_CALL");
      JsonNode accounting = payload(reopened, "fact-accounting.json");
      assertThat(payload(reopened, "gap-ledger.json").path("gaps"))
          .hasSize(decisions.rootCauseRejections().size() + decisions.externalEffectGaps().size());
      assertThat(accounting.path("candidateFactCount").asInt()).isEqualTo(candidateKeys.size());
      assertThat(accounting.path("admittedFactCount").asInt())
          .isEqualTo(decisions.codeFacts().size());
      assertThat(payload(reopened, "fact-accounting.json").path("rejectedFactCount").asInt())
          .isEqualTo(decisions.factDispositions().size());
      assertThat(accounting.path("boundaryCandidateDenominatorKeys")).hasSize(boundaryKeys.size());
      assertThat(accounting.path("exactCallCandidateDenominatorKeys")).hasSize(exactKeys.size());
      assertThat(payload(reopened, "gap-ledger.json").path("gaps")).isNotEmpty();
    }
  }

  private static ProofDecisionSet rejectedDecisions(FactCandidateSet candidates) {
    java.util.List<ProofDecisionSet.FactDisposition> facts = new java.util.ArrayList<>();
    java.util.List<ProofDecisionSet.AtomDisposition> atoms = new java.util.ArrayList<>();
    java.util.List<ProofDecisionSet.RootCauseRejection> roots = new java.util.ArrayList<>();
    java.util.List<ProofDecisionSet.ExternalEffectGap> gaps = new java.util.ArrayList<>();
    for (FactCandidateSet.FactCandidate candidate : candidates.candidates()) {
      String key = candidate.denominatorKey();
      String directAtom = candidate.requiredAtoms().get(0).atomKey();
      facts.add(
          new ProofDecisionSet.FactDisposition(
              key, "REJECTED_WITH_REASON", null, "PROOF_NOT_CLOSED"));
      for (FactCandidateSet.RequiredAtom atom : candidate.requiredAtoms()) {
        atoms.add(
            new ProofDecisionSet.AtomDisposition(
                key,
                atom.atomKey(),
                "REJECTED_WITH_REASON",
                null,
                atom.atomKey().equals(directAtom)
                    ? "PROOF_NOT_CLOSED"
                    : "COMPOSITE_FACT_REJECTED"));
      }
      roots.add(
          new ProofDecisionSet.RootCauseRejection(
              key, directAtom, "PROOF_NOT_CLOSED", "proof-gap:rejection-" + key));
      if ("JAVA_BOUNDARY_INVOCATION".equals(candidate.kind())) {
        java.util.List<String> evidence =
            candidate.evidenceBySubject().stream()
                .flatMap(
                    binding ->
                        java.util.stream.Stream.concat(
                            binding.sourceEvidenceNodeIds().stream(),
                            binding.ruleApplicationEvidenceNodeIds().stream()))
                .distinct()
                .sorted()
                .toList();
        gaps.add(
            new ProofDecisionSet.ExternalEffectGap(
                "fact-gap:external-" + candidate.entryId() + "-" + candidate.boundaryNodeId(),
                key,
                candidate.entryId(),
                candidate.boundaryNodeId(),
                candidate.staticTargetType(),
                candidate.staticTargetMethod(),
                candidate.staticTargetSignature(),
                "DATA_FLOW_BINDING_UNPROVEN",
                evidence));
      }
    }
    return new ProofDecisionSet(
        candidates.candidateSetId(),
        java.util.List.of(),
        java.util.List.of(),
        facts,
        atoms,
        roots,
        gaps);
  }

  private static JsonNode payload(ReopenedAnalysisStepPublication publication, String fileName) {
    return new org.sourceanalysis.app.artifact.CanonicalJsonCodec()
        .parseCanonical(
            publication.semanticPayloads().stream()
                .filter(payload -> payload.descriptor().fileName().equals(fileName))
                .findFirst()
                .orElseThrow()
                .canonicalUtf8());
  }

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static Object specify(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      FactCandidateInputs inputs,
      ModulePublicationReference candidatePublication,
      ModulePublicationReference proofPublication,
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs)
      throws Exception {
    try {
      Class<?> specifierType = Class.forName(SPECIFIER_CLASS);
      Method method =
          specifierType.getMethod(
              "specifyCandidatesAndProofs",
              FactCandidateInputs.class,
              ModulePublicationReference.class,
              ModulePublicationReference.class,
              VerifiedSourceInventoryReference.class,
              ApplicationDiscoveryReference.class,
              ProgramGraphsReference.class);
      return method.invoke(
          specifierType
              .getConstructor(
                  CanonicalModuleArtifactStore.class, CanonicalAnalysisStepArtifactStore.class)
              .newInstance(modules, steps),
          inputs,
          candidatePublication,
          proofPublication,
          source,
          discovery,
          graphs);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("PROVEN_CODE_FACTS_PUBLICATION_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("PROVEN_CODE_FACTS_PUBLICATION_FAILED", cause);
    }
  }
}
