package org.sourceanalysis.app.analysis.fact.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;

/** Public-seam tests for the Fact M1 registry denominator and ordering contract. */
class FactCandidateRegistryDeterminismTest {

  @TempDir Path temporaryDirectory;

  @Test
  void equivalentTemplateCollectionOrderHasTheSameCandidateIdentityAndDenominator() {
    FactRegistry.FactTemplate first = template("JAVA_BOUNDARY_INVOCATION_FIRST");
    FactRegistry.FactTemplate second = template("JAVA_BOUNDARY_INVOCATION_SECOND");
    FactRegistry ordered = registry(List.of(first, second));
    FactRegistry reversed = registry(List.of(second, first));

    try (ProgramGraphsPublicFixture fixture = fixture("template-order")) {
      FactCandidateSet orderedResult = enumerate(fixture, ordered);
      FactCandidateSet reversedResult = enumerate(fixture, reversed);

      assertThat(reversedResult.candidateSetId())
          .as("equivalent registry collection order must not change candidate identity")
          .isEqualTo(orderedResult.candidateSetId());
      assertThat(reversedResult.candidates())
          .as("equivalent registry collection order must not change candidate semantics")
          .containsExactlyElementsOf(orderedResult.candidates());
      assertThat(reversedResult.notApplicableDispositions())
          .containsExactlyElementsOf(orderedResult.notApplicableDispositions());
      assertThat(reversedResult.denominator()).isEqualTo(orderedResult.denominator());
    }
  }

  @Test
  void deletingOneTemplateRemovesOnlyItsEntryBoundaryCombinations() {
    FactRegistry.FactTemplate first = template("JAVA_BOUNDARY_INVOCATION_FIRST");
    FactRegistry.FactTemplate second = template("JAVA_BOUNDARY_INVOCATION_SECOND");

    try (ProgramGraphsPublicFixture fixture = fixture("template-deletion")) {
      FactCandidateSet complete = enumerate(fixture, registry(List.of(first, second)));
      FactCandidateSet reduced = enumerate(fixture, registry(List.of(first)));

      assertThat(complete.candidates()).hasSize(4);
      assertThat(complete.candidates())
          .extracting(FactCandidateSet.FactCandidate::candidateFactKey)
          .containsExactlyInAnyOrder(
              "JAVA_BOUNDARY_INVOCATION_FIRST",
              "JAVA_BOUNDARY_INVOCATION_FIRST",
              "JAVA_BOUNDARY_INVOCATION_SECOND",
              "JAVA_BOUNDARY_INVOCATION_SECOND");
      assertThat(reduced.candidates()).hasSize(2);
      assertThat(reduced.candidates())
          .extracting(FactCandidateSet.FactCandidate::candidateFactKey)
          .containsOnly("JAVA_BOUNDARY_INVOCATION_FIRST");
      assertThat(reduced.candidateSetId())
          .as("changing the registry denominator must change candidate identity")
          .isNotEqualTo(complete.candidateSetId());
      assertThat(reduced.denominator().applicableKeys()).hasSize(2);
      assertThat(reduced.denominator().notApplicableKeys()).isEmpty();
    }
  }

  @Test
  void requiredAtomsKeepRegistryDeclarationOrderAsCandidateSemantics() {
    FactRegistry.FactTemplate standard = FactRegistry.standardJavaBoundary().templates().get(0);
    List<FactRegistry.RequiredAtomTemplate> declared = standard.requiredAtoms();
    List<FactRegistry.RequiredAtomTemplate> reversed = new ArrayList<>(declared);
    Collections.reverse(reversed);
    FactRegistry.FactTemplate reversedTemplate =
        new FactRegistry.FactTemplate(standard.candidateFactKey(), standard.kind(), reversed);

    try (ProgramGraphsPublicFixture fixture = fixture("atom-order")) {
      FactCandidateSet declaredResult = enumerate(fixture, registry(List.of(standard)));
      FactCandidateSet reversedResult = enumerate(fixture, registry(List.of(reversedTemplate)));

      assertThat(declaredResult.candidates())
          .allSatisfy(
              candidate ->
                  assertThat(candidate.requiredAtoms())
                      .extracting(FactCandidateSet.RequiredAtom::atomKey)
                      .containsExactlyElementsOf(
                          declared.stream()
                              .map(FactRegistry.RequiredAtomTemplate::atomKey)
                              .toList()));
      assertThat(reversedResult.candidates())
          .allSatisfy(
              candidate ->
                  assertThat(candidate.requiredAtoms())
                      .extracting(FactCandidateSet.RequiredAtom::atomKey)
                      .containsExactlyElementsOf(
                          reversed.stream()
                              .map(FactRegistry.RequiredAtomTemplate::atomKey)
                              .toList()));
      assertThat(reversedResult.candidateSetId())
          .as("required atom order is semantic registry input")
          .isNotEqualTo(declaredResult.candidateSetId());
    }
  }

  private ProgramGraphsPublicFixture fixture(String name) {
    return ProgramGraphsPublicFixture.create(temporaryDirectory.resolve(name));
  }

  private static FactCandidateSet enumerate(
      ProgramGraphsPublicFixture fixture, FactRegistry registry) {
    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
            .reopen(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    return new FactCandidateEnumerator().enumerate(inputs, registry);
  }

  private static FactRegistry registry(List<FactRegistry.FactTemplate> templates) {
    return new FactRegistry("proven-code-facts-registry-v1", templates);
  }

  private static FactRegistry.FactTemplate template(String key) {
    FactRegistry.FactTemplate standard = FactRegistry.standardJavaBoundary().templates().get(0);
    return new FactRegistry.FactTemplate(key, standard.kind(), standard.requiredAtoms());
  }
}
