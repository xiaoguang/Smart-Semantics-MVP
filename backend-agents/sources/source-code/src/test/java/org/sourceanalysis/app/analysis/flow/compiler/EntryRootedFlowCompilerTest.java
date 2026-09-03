package org.sourceanalysis.app.analysis.flow.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import org.sourceanalysis.app.analysis.fact.publish.FactLedgerPublicationSpecifier;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M1 public-seam contract for compiling the complete persisted entry denominator into Flows. */
class EntryRootedFlowCompilerTest {

  private static final String COMPILER_CLASS =
      "org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler";
  private static final String PROFILE_CLASS =
      "org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile";

  @TempDir Path temporaryDirectory;

  @Test
  void compilesOneOwnedFlowForEachPersistedEntryAndRetainsItsExternalEffectGap() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("flow-compiler-graphs"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      Set<String> externalEffectGapIds = externalEffectGapIds(fixture, facts);

      Object compilation = compile(fixture, facts);
      List<?> dispositions = listProperty(compilation, "entryDispositions");
      List<?> flows = listProperty(compilation, "flowSlices");

      assertThat(dispositions).hasSize(2);
      assertThat(flows).hasSize(2);
      assertThat(strings(dispositions, "entryId"))
          .containsExactlyInAnyOrder("entry:" + digest("approve"), "entry:" + digest("cancel"));
      assertThat(strings(dispositions, "disposition")).containsOnly("COMPILED");
      assertThat(strings(dispositions, "flowSliceId")).doesNotContainNull().doesNotHaveDuplicates();
      assertThat(strings(flows, "entryId"))
          .containsExactlyInAnyOrder("entry:" + digest("approve"), "entry:" + digest("cancel"));
      assertThat(flows)
          .allSatisfy(
              flow -> {
                assertThat(listProperty(flow, "factIds")).hasSize(1);
                assertThat(listProperty(flow, "atomIds")).isNotEmpty();
                assertThat(listProperty(flow, "gapIds")).hasSize(1);
              });
      assertThat(
              flows.stream()
                  .flatMap(flow -> listProperty(flow, "factIds").stream())
                  .collect(Collectors.toSet()))
          .hasSize(2);
      assertThat(
              flows.stream()
                  .flatMap(flow -> listProperty(flow, "gapIds").stream())
                  .map(Object::toString)
                  .collect(Collectors.toSet()))
          .isEqualTo(externalEffectGapIds);
    }
  }

  @Test
  void compilesTheTwoDistinctTerminalPathsOfAnExactJavaGuard() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("flow-compiler-guard"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);

      Object compilation = compile(fixture, facts);
      Object approveFlow =
          listProperty(compilation, "flowSlices").stream()
              .filter(flow -> ("entry:" + digest("approve")).equals(property(flow, "entryId")))
              .findFirst()
              .orElseThrow();
      List<?> outcomes = listProperty(approveFlow, "outcomePaths");

      assertThat(outcomes).hasSize(2);
      assertThat(outcomes)
          .allSatisfy(
              outcome -> {
                assertThat(listProperty(outcome, "decisions")).hasSize(1);
                Object decision = listProperty(outcome, "decisions").get(0);
                assertThat(property(decision, "polarity"))
                    .isIn("TRUE", "FALSE");
                assertThat(property(decision, "normalizedCondition")).isEqualTo("status == null");
                assertThat(
                        listProperty(outcome, "requiredAtomIds").stream()
                            .map(Object::toString)
                            .toList())
                    .contains(property(decision, "conditionAtomId").toString());
              });
      assertThat(
              outcomes.stream()
                  .map(outcome -> property(listProperty(outcome, "decisions").get(0), "polarity"))
                  .toList())
          .containsExactlyInAnyOrder("TRUE", "FALSE");
    }
  }

  private Object compile(ProgramGraphsPublicFixture fixture, ProvenCodeFactsReference facts)
      throws Exception {
    try {
      Class<?> profileType = Class.forName(PROFILE_CLASS);
      Object profile =
          profileType
              .getConstructor(
                  ArtifactReference.class, int.class, int.class, int.class, int.class, int.class)
              .newInstance(
                  new ArtifactReference(
                      ArtifactId.parse("flow-profile:" + digest("two-entry-flow-profile")),
                      new Sha256Digest(digest("two-entry-flow-profile-bytes"))),
                  16,
                  8,
                  64,
                  96,
                  32);
      Class<?> compilerType = Class.forName(COMPILER_CLASS);
      Object compiler =
          compilerType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
              .newInstance(fixture.stepArtifacts());
      return compilerType
          .getMethod(
              "compile",
              org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              profileType)
          .invoke(
              compiler, fixture.applicationDiscovery(), fixture.programGraphs(), facts, profile);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("ENTRY_ROOTED_FLOW_COMPILER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("ENTRY_ROOTED_FLOW_COMPILER_FAILED", cause);
    }
  }

  private ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
            .reopen(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    FactCandidateSet candidates =
        new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
    ProofDecisionSet decisions =
        new AtomicProofBuilder(fixture.sourceReader())
            .prove(
                candidates,
                inputs,
                fixture.sourceInventory(),
                ProofRuleRegistry.standardJavaBoundary());
    var candidatePublication =
        new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 1, "candidates"), inputs, candidates);
    var proofPublication =
        new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);
    return new FactLedgerPublicationSpecifier(fixture.moduleArtifacts(), fixture.stepArtifacts())
        .specifyCandidatesAndProofs(
            inputs,
            candidatePublication,
            proofPublication,
            fixture.sourceInventory(),
            fixture.applicationDiscovery(),
            fixture.programGraphs());
  }

  private static Set<String> externalEffectGapIds(
      ProgramGraphsPublicFixture fixture, ProvenCodeFactsReference facts) {
    JsonNode ledger =
        new org.sourceanalysis.app.artifact.CanonicalJsonCodec()
            .parseCanonical(
                fixture.stepArtifacts().reopen(facts.publication()).semanticPayloads().stream()
                    .filter(payload -> "gap-ledger.json".equals(payload.descriptor().fileName()))
                    .findFirst()
                    .orElseThrow()
                    .canonicalUtf8());
    return java.util.stream.StreamSupport.stream(ledger.path("gaps").spliterator(), false)
        .filter(gap -> "DATA_FLOW_BINDING_UNPROVEN".equals(gap.path("code").asText()))
        .map(gap -> gap.path("gapId").asText())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static List<?> listProperty(Object target, String property) {
    try {
      Object value = target.getClass().getMethod(property).invoke(target);
      if (!(value instanceof List<?> values))
        throw new AssertionError("FLOW_COMPILATION_SHAPE_INVALID");
      return values;
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("FLOW_COMPILATION_SHAPE_INVALID", failure);
    }
  }

  private static Set<String> strings(List<?> values, String property) {
    return values.stream()
        .flatMap(
            value -> {
              Object propertyValue = property(value, property);
              if (propertyValue instanceof List<?> list) {
                return list.stream().map(Object::toString);
              }
              return java.util.stream.Stream.of((String) propertyValue);
            })
        .collect(Collectors.toSet());
  }

  private static Object property(Object target, String property) {
    try {
      return target.getClass().getMethod(property).invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("FLOW_COMPILATION_SHAPE_INVALID", failure);
    }
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
