package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;

/**
 * Public RED for Fact M1's exact entry-to-boundary join.
 *
 * <p>The fixture is built and persisted by the graph package. This test deliberately receives only
 * typed reopened references; it cannot inspect graph drafts, decode graph canonical values, or
 * hand-build a graph JSON mutation.
 */
class FactCandidateExactPathTest {

  @TempDir Path temporaryDirectory;

  @Test
  void joinsEachDiscoveredEntryToOnlyItsOwnedJavaBoundaryPath() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("two-entry-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());

      FactRegistry registry = FactRegistry.standardJavaBoundary();
      FactCandidateSet result = new FactCandidateEnumerator().enumerate(inputs, registry);

      assertThat(result.candidates()).hasSize(2);
      assertThat(result.candidates())
          .extracting(FactCandidateSet.FactCandidate::entryId)
          .doesNotHaveDuplicates()
          .containsExactlyInAnyOrder("entry:" + digest("approve"), "entry:" + digest("cancel"));
      assertThat(result.candidates())
          .extracting(FactCandidateSet.FactCandidate::subjectNodeIds)
          .allSatisfy(subjects -> assertThat(subjects).hasSize(1));
      assertThat(result.candidates())
          .extracting(FactCandidateSet.FactCandidate::candidateFactKey)
          .containsOnly("JAVA_BOUNDARY_INVOCATION");

      // The implementation must expose a complete denominator, including scoped failures. A
      // second test will remove one required argument/control/evidence relation through a
      // persisted graph mutation fixture; the affected entry must become NOT_APPLICABLE while
      // the other entry remains APPLICABLE. No external SQL/effect claim is allowed here.
      assertThat(result.notApplicableDispositions()).isEmpty();
      assertThat(
              result.candidates().stream()
                  .sorted(java.util.Comparator.comparing(FactCandidateSet.FactCandidate::entryId))
                  .toList())
          .extracting(FactCandidateSet.FactCandidate::boundaryNodeId)
          .doesNotHaveDuplicates();
    }
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
