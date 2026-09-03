package org.sourceanalysis.app.analysis.fact.proofs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;

/** RED for the first complete, frozen-Java M2 proof decision. */
class AtomicProofBuilderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void closesEveryBoundaryAtomButRetainsOneExternalEffectGapPerCandidate() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("two-entry-graphs"))) {
      FactCandidateInputs inputs =
          new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
              .reopen(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs());
      FactCandidateSet candidates =
          new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());

      assertThat(candidates.candidates()).hasSize(2);
      Object decisions = prove(fixture.sourceReader(), candidates, inputs, fixture.sourceInventory());
      Set<String> candidateKeys =
          candidates.candidates().stream()
              .map(AtomicProofBuilderTest::candidateDenominatorKey)
              .collect(Collectors.toUnmodifiableSet());

      assertThat(list(decisions, "codeFacts")).hasSize(2);
      assertThat(list(decisions, "atomProofs")).hasSize(16);
      assertThat(list(decisions, "rootCauseRejections")).isEmpty();

      List<?> factDispositions = list(decisions, "factDispositions");
      assertThat(factDispositions).hasSize(2);
      assertThat(strings(factDispositions, "candidateDenominatorKey"))
          .containsExactlyInAnyOrderElementsOf(candidateKeys);
      assertThat(strings(factDispositions, "disposition"))
          .containsOnly("ADMITTED");

      List<?> atomDispositions = list(decisions, "atomDispositions");
      assertThat(atomDispositions).hasSize(16);
      assertThat(strings(atomDispositions, "disposition")).containsOnly("CLOSED");

      List<?> externalEffectGaps = list(decisions, "externalEffectGaps");
      assertThat(externalEffectGaps).hasSize(2);
      assertThat(strings(externalEffectGaps, "candidateDenominatorKey"))
          .containsExactlyInAnyOrderElementsOf(candidateKeys);
      assertThat(strings(externalEffectGaps, "code"))
          .containsOnly("DATA_FLOW_BINDING_UNPROVEN");
    }
  }

  private static Object prove(
      VerifiedSourceTextReader sourceReader,
      FactCandidateSet candidates,
      FactCandidateInputs inputs,
      VerifiedSourceInventoryReference source) {
    try {
      Class<?> registryType =
          Class.forName("org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry");
      Object rules = registryType.getMethod("standardJavaBoundary").invoke(null);
      Class<?> builderType =
          Class.forName("org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder");
      Object builder = builderType.getConstructor(VerifiedSourceTextReader.class).newInstance(sourceReader);
      return builderType
          .getMethod(
              "prove",
              FactCandidateSet.class,
              FactCandidateInputs.class,
              VerifiedSourceInventoryReference.class,
              registryType)
          .invoke(builder, candidates, inputs, source, rules);
    } catch (ClassNotFoundException absent) {
      fail("M2 AtomicProofBuilder and ProofRuleRegistry must be public production types", absent);
      return null;
    } catch (ReflectiveOperationException failure) {
      fail("M2 public AtomicProofBuilder seam does not match the published contract", failure);
      return null;
    }
  }

  private static String candidateDenominatorKey(FactCandidateSet.FactCandidate candidate) {
    return candidate.entryId()
        + "|"
        + candidate.boundaryNodeId()
        + "|"
        + candidate.candidateFactKey();
  }

  private static List<?> list(Object receiver, String accessor) {
    Object value = accessor(receiver, accessor);
    assertThat(value).as("%s() must return a list", accessor).isInstanceOf(List.class);
    return (List<?>) value;
  }

  private static Set<String> strings(List<?> values, String accessor) {
    return values.stream()
        .map(value -> String.valueOf(accessor(value, accessor)))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Object accessor(Object receiver, String accessor) {
    try {
      Method method = receiver.getClass().getMethod(accessor);
      return method.invoke(receiver);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
      fail("M2 public result is missing accessor " + accessor + "()", failure);
      return null;
    }
  }
}
