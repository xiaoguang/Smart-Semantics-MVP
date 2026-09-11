package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet.BoundaryArgumentBinding;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet.FactCandidate;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;

/** Regression test for defensive copies of canonical non-boundary candidate argument lists. */
class FactCandidateDefensiveCopyTest {

  @TempDir Path temporaryDirectory;

  @Test
  void nonBoundaryCandidatesDefensivelyCopyCallerOwnedArgumentLists() {
    FactCandidate guardCandidate =
        enumerateGuardCandidate(temporaryDirectory.resolve("guarded-graphs"));
    FactCandidate exactCallCandidate =
        enumerateExactCallCandidate(temporaryDirectory.resolve("exact-call-graphs"));

    assertThat(guardCandidate.kind()).isEqualTo("JAVA_GUARD_CONDITION");
    assertThat(exactCallCandidate.kind()).isEqualTo("JAVA_EXACT_CALL");
    assertAll(
        () -> assertDefensiveCopy(guardCandidate), () -> assertDefensiveCopy(exactCallCandidate));
  }

  private static FactCandidate enumerateGuardCandidate(Path graphDirectory) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(graphDirectory)) {
      FactCandidateInputs inputs = reopen(fixture);
      return new FactCandidateEnumerator()
          .enumerate(inputs, FactRegistry.standardJavaBoundary()).candidates().stream()
              .filter(candidate -> "JAVA_GUARD_CONDITION".equals(candidate.kind()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_JAVA_GUARD_CONDITION_CANDIDATE"));
    }
  }

  private static FactCandidate enumerateExactCallCandidate(Path graphDirectory) {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(graphDirectory)) {
      FactCandidateInputs inputs = reopen(fixture);
      return new FactCandidateEnumerator()
          .enumerate(inputs, FactRegistry.standardJavaBoundary()).candidates().stream()
              .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("MISSING_JAVA_EXACT_CALL_CANDIDATE"));
    }
  }

  private static FactCandidateInputs reopen(ProgramGraphsPublicFixture fixture) {
    return new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
        .reopen(fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
  }

  private static void assertDefensiveCopy(FactCandidate original) {
    assertThat(original.orderedArgumentEdgeIds()).isEmpty();
    assertThat(original.orderedArguments()).isEmpty();

    ArrayList<String> callerOwnedArgumentEdgeIds =
        new ArrayList<>(original.orderedArgumentEdgeIds());
    ArrayList<BoundaryArgumentBinding> callerOwnedArguments =
        new ArrayList<>(original.orderedArguments());
    FactCandidate reconstructed =
        new FactCandidate(
            original.candidateFactKey(),
            original.entryId(),
            original.kind(),
            original.boundaryNodeId(),
            original.invocationCallId(),
            original.callTargetEdgeId(),
            original.staticTargetType(),
            original.staticTargetMethod(),
            original.staticTargetSignature(),
            callerOwnedArgumentEdgeIds,
            callerOwnedArguments,
            original.controlBlockId(),
            original.guardId(),
            original.evidenceBySubject(),
            original.requiredAtoms(),
            original.guardNodeId(),
            original.normalizedCondition(),
            original.branchEdgeIds(),
            original.callSiteNodeId(),
            original.targetMethodNodeId(),
            original.targetCanonicalMethod());

    callerOwnedArgumentEdgeIds.add(null);
    callerOwnedArguments.add(null);

    assertThat(reconstructed.orderedArgumentEdgeIds()).isEmpty();
    assertThat(reconstructed.orderedArguments()).isEmpty();
    assertThatThrownBy(() -> reconstructed.orderedArgumentEdgeIds().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> reconstructed.orderedArguments().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
