package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;

/** Public M1 contract: a reachable Java guard is its own fact candidate, not call context. */
class GuardConditionFactCandidateTest {

  @TempDir Path temporaryDirectory;

  @Test
  void enumeratesOneIndependentCandidateForTheApprovedEntryGuard() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("guarded-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());

      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());

      assertThat(candidates.candidates())
          .filteredOn(candidate -> candidate.kind().equals("JAVA_GUARD_CONDITION"))
          .singleElement()
          .satisfies(
              candidate ->
                  assertThat(candidate.normalizedCondition())
                      .as("the fact condition must use the public control-flow v2 field, never its technical key")
                      .isEqualTo("status == null"));
    }
  }
}
