package org.sourceanalysis.app.analysis.fact.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder;
import org.sourceanalysis.app.analysis.fact.proofs.PersistedProofDecisionSetReader;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSet;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;

/** Locks the v3 fact ledger rule: guards are proved facts, never external-effect gaps. */
class GuardConditionFactLedgerTest {

  @TempDir Path temporaryDirectory;

  @Test
  void persistsSeparateGuardAndBoundaryAccountingWithoutAnExternalEffectGapForTheGuard() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("guard-ledger-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaFacts());
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
      assertThat(
              new PersistedProofDecisionSetReader(fixture.moduleArtifacts())
                  .reopen(proofPublication, inputs, candidatePublication, candidates))
          .isEqualTo(decisions);

      ProvenCodeFactsReference reference =
          new FactLedgerPublicationSpecifier(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .specifyCandidatesAndProofs(
                  inputs,
                  candidatePublication,
                  proofPublication,
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      ReopenedAnalysisStepPublication publication =
          fixture.stepArtifacts().reopen(reference.publication());
      JsonNode accounting = payload(publication, "fact-accounting.json");
      JsonNode gaps = payload(publication, "gap-ledger.json");
      List<String> exactKeys =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .map(FactCandidateSet.FactCandidate::denominatorKey)
              .sorted()
              .toList();
      assertThat(exactKeys).hasSize(4);

      assertThat(accounting.path("schemaVersion").asText())
          .isEqualTo("proven-code-facts-fact-accounting-v3");
      assertThat(accounting.path("boundaryCandidateDenominatorKeys")).hasSize(2);
      assertThat(accounting.path("guardCandidateDenominatorKeys")).hasSize(1);
      assertThat(accounting.path("exactCallCandidateDenominatorKeys").isArray()).isTrue();
      List<String> publishedExactKeys = new ArrayList<>();
      accounting
          .path("exactCallCandidateDenominatorKeys")
          .forEach(value -> publishedExactKeys.add(value.asText()));
      assertThat(publishedExactKeys).containsExactlyElementsOf(exactKeys);
      assertThat(accounting.path("externalEffectGapCount").asInt()).isEqualTo(2);
      assertThat(gaps.path("schemaVersion").asText()).isEqualTo("proven-code-facts-gap-ledger-v3");
      assertThat(payload(publication, "proven-facts.json").path("schemaVersion").asText())
          .isEqualTo("proven-code-facts-proven-facts-v3");
      assertThat(payload(publication, "proof-pack.json").path("schemaVersion").asText())
          .isEqualTo("proven-code-facts-proof-pack-v3");
      java.util.List<String> gapCandidateKeys = new java.util.ArrayList<>();
      gaps.path("gaps")
          .forEach(
              gap ->
                  gap.path("affectedCandidateDenominatorKeys")
                      .forEach(value -> gapCandidateKeys.add(value.asText())));
      assertThat(gapCandidateKeys).noneMatch(key -> key.contains("|JAVA_GUARD_CONDITION"));
      assertThat(gapCandidateKeys).doesNotContainAnyElementsOf(exactKeys);
    }
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
}
