package org.sourceanalysis.app.analysis.flow.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
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
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M1 persistence seam: Flow compilation must be a canonical module artifact before M2 can use it. */
class FlowCompilationModulePublisherTest {

  private static final String PUBLISHER_CLASS =
      "org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher";

  @TempDir Path temporaryDirectory;

  @Test
  void installsOneCanonicalFlowCompilationArtifactWithTheExactPublicPredecessorSet()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("flow-module-graphs"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(fixture.applicationDiscovery(), fixture.programGraphs(), facts, profile());

      ModulePublicationReference reference = publish(fixture, facts, compilation);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);

      assertThat(reopened.receipt().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  fixture.programGraphs().publication().address().runId(),
                  AnalysisStepKey.BUSINESS_FLOWS,
                  1,
                  "flow-compiler"));
      assertThat(reopened.payloads()).hasSize(1);
      assertThat(reopened.payloads().get(0).descriptor().fileName())
          .isEqualTo("flow-compilation.json");
      assertThat(reopened.payloads().get(0).descriptor().artifactType())
          .isEqualTo("BUSINESS_FLOWS_FLOW_COMPILATION");
      assertThat(reopened.payloads().get(0).descriptor().schemaVersion())
          .isEqualTo("business-flows-flow-compilation-v1");
      assertThat(reopened.receipt().upstreamArtifacts()).hasSize(13);
    }
  }

  private static ModulePublicationReference publish(
      ProgramGraphsPublicFixture fixture, ProvenCodeFactsReference facts, FlowCompilation compilation)
      throws Exception {
    try {
      Class<?> publisherType = Class.forName(PUBLISHER_CLASS);
      Object publisher =
          publisherType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
              .newInstance(fixture.moduleArtifacts(), fixture.stepArtifacts());
      Method method =
          publisherType.getMethod(
              "publish",
              org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              FlowCompilation.class);
      return (ModulePublicationReference)
          method.invoke(
              publisher, fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FLOW_COMPILATION_MODULE_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FLOW_COMPILATION_MODULE_PUBLISHER_FAILED", cause);
    }
  }

  private static FlowCompilationProfile profile() {
    return new FlowCompilationProfile(
        new ArtifactReference(
            ArtifactId.parse("flow-profile:" + digest("module-publish-profile")),
            new Sha256Digest(digest("module-publish-profile-bytes"))),
        16,
        8,
        64,
        96,
        32);
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

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
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
