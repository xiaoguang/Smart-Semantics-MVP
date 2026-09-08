package org.sourceanalysis.app.analysis.fact.proofs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;

/**
 * Public M2 contract: a guard condition has one source-backed condition Proof and no effect Gap.
 */
class GuardConditionAtomicProofTest {

  @TempDir Path temporaryDirectory;

  @Test
  void admitsTheGuardConditionFromItsOwnEvidenceWithoutAnExternalEffectGap() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("guard-proof-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaFacts());
      FactCandidateSet.FactCandidate guard =
          candidates.candidates().stream()
              .filter(candidate -> "JAVA_GUARD_CONDITION".equals(candidate.kind()))
              .findFirst()
              .orElseThrow();

      ProofDecisionSet decisions =
          new AtomicProofBuilder(fixture.sourceReader())
              .prove(
                  candidates,
                  inputs,
                  fixture.sourceInventory(),
                  ProofRuleRegistry.standardJavaBoundary());

      assertThat(decisions.codeFacts())
          .filteredOn(fact -> fact.candidateDenominatorKey().equals(guard.denominatorKey()))
          .singleElement()
          .satisfies(
              fact -> {
                assertThat(fact.kind()).isEqualTo("JAVA_GUARD_CONDITION");
                assertThat(fact.subjectNodeIds()).containsExactly(guard.guardNodeId());
                assertThat(fact.atoms())
                    .singleElement()
                    .satisfies(
                        atom -> {
                          assertThat(atom.name()).isEqualTo("CONTROL_CONDITION");
                          assertThat(atom.role()).isEqualTo("CONDITION");
                          assertThat(atom.value().type()).isEqualTo("STRING");
                          assertThat(atom.value().canonical()).isEqualTo("status == null");
                        });
              });
      assertThat(decisions.atomProofs())
          .filteredOn(proof -> proof.candidateDenominatorKey().equals(guard.denominatorKey()))
          .singleElement()
          .satisfies(proof -> assertThat(proof.requiredProgramEdgeIds()).isEmpty());
      assertThat(decisions.externalEffectGaps())
          .noneMatch(gap -> gap.candidateDenominatorKey().equals(guard.denominatorKey()));
    }
  }
}
