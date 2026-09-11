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
    FactRegistry standard = FactRegistry.standardJavaFacts();
    FactRegistry.FactTemplate boundary = template(standard, "JAVA_BOUNDARY_INVOCATION");
    FactRegistry.FactTemplate exact = template(standard, "JAVA_EXACT_CALL");
    FactRegistry ordered = registry(List.of(boundary, exact));
    FactRegistry reversed = registry(List.of(exact, boundary));

    try (ProgramGraphsPublicFixture fixture = fixture("template-order")) {
      FactCandidateSet orderedResult = enumerate(fixture, ordered);
      FactCandidateSet reversedResult = enumerate(fixture, reversed);

      assertThat(orderedResult.candidates()).hasSize(6);
      assertThat(
              orderedResult.candidates().stream()
                  .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind())))
          .hasSize(2);
      assertThat(
              orderedResult.candidates().stream()
                  .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind())))
          .hasSize(4);
      assertThat(orderedResult.candidates())
          .extracting(FactCandidateSet.FactCandidate::candidateFactKey)
          .containsOnly("JAVA_BOUNDARY_INVOCATION", "JAVA_EXACT_CALL");
      assertThat(orderedResult.notApplicableDispositions()).isEmpty();
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
  void deletingExactTemplateRemovesOnlyExactCallCombinations() {
    FactRegistry standard = FactRegistry.standardJavaFacts();
    FactRegistry.FactTemplate boundary = template(standard, "JAVA_BOUNDARY_INVOCATION");
    FactRegistry.FactTemplate exact = template(standard, "JAVA_EXACT_CALL");

    try (ProgramGraphsPublicFixture fixture = fixture("template-deletion")) {
      FactCandidateSet complete = enumerate(fixture, registry(List.of(boundary, exact)));
      FactCandidateSet reduced = enumerate(fixture, registry(List.of(boundary)));

      assertThat(complete.candidates()).hasSize(6);
      assertThat(complete.candidates())
          .extracting(FactCandidateSet.FactCandidate::candidateFactKey)
          .containsOnly("JAVA_BOUNDARY_INVOCATION", "JAVA_EXACT_CALL");
      assertThat(
              complete.candidates().stream()
                  .filter(candidate -> "JAVA_EXACT_CALL".equals(candidate.kind())))
          .hasSize(4);
      assertThat(reduced.candidates()).hasSize(2);
      assertThat(reduced.candidates())
          .extracting(FactCandidateSet.FactCandidate::candidateFactKey)
          .containsOnly("JAVA_BOUNDARY_INVOCATION");
      List<FactCandidateSet.FactCandidate> completeBoundaryCandidates =
          complete.candidates().stream()
              .filter(candidate -> "JAVA_BOUNDARY_INVOCATION".equals(candidate.kind()))
              .toList();
      assertThat(completeBoundaryCandidates).hasSize(2);
      assertThat(reduced.candidates())
          .as("deleting exact templates must preserve every boundary candidate row")
          .containsExactlyElementsOf(completeBoundaryCandidates);
      assertThat(complete.notApplicableDispositions()).isEmpty();
      assertThat(reduced.notApplicableDispositions()).isEmpty();
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
    return new FactRegistry("proven-code-facts-fact-registry-v3", templates);
  }

  private static FactRegistry.FactTemplate template(FactRegistry registry, String key) {
    return registry.templates().stream()
        .filter(template -> key.equals(template.candidateFactKey()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing standard template: " + key));
  }
}
