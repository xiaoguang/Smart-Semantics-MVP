package org.sourceanalysis.app.analysis.flow.capsule;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
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
import org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M2 persistence seam: a capsule projection must be canonical before M3 can join it to Flows. */
class CapsuleProjectionModulePublisherTest {

  private static final String PUBLISHER_CLASS =
      "org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionModulePublisher";

  @TempDir Path temporaryDirectory;

  @Test
  void installsOneCapsuleProjectionArtifactWithEveryDeclaredPublicPredecessor() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-publisher"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference flows =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(flows, fixture.sourceInventory(), fixture.programGraphs(), facts, capsuleProfile());

      ModulePublicationReference reference = publish(fixture, facts, flows, projection);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);

      assertThat(reopened.receipt().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  fixture.programGraphs().publication().address().runId(),
                  AnalysisStepKey.BUSINESS_FLOWS,
                  2,
                  "capsule-projector"));
      assertThat(reopened.payloads()).hasSize(1);
      assertThat(reopened.payloads().get(0).descriptor().fileName())
          .isEqualTo("capsule-projection.json");
      assertThat(reopened.payloads().get(0).descriptor().artifactType())
          .isEqualTo("BUSINESS_FLOWS_CAPSULE_PROJECTION");
      assertThat(reopened.payloads().get(0).descriptor().schemaVersion())
          .isEqualTo("business-flows-capsule-projection-v4");
      assertThat(reopened.receipt().upstreamArtifacts()).hasSize(14);
    }
  }

  @Test
  void recordsTheBudgetGapWhenItKeepsAnOverBudgetCapsuleForFlowAccounting() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-publisher-ineligible"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference flows =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(
                  flows,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  new CapsuleProjectionProfile(
                      reference("capsule-profile", "capsule-publisher-ineligible"),
                      16,
                      32,
                      4_096,
                      1));

      ModulePublicationReference reference = publish(fixture, facts, flows, projection);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);

      assertThat(reopened.receipt().status())
          .isEqualTo(ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
      assertThat(reopened.receipt().gapRefs())
          .containsExactlyElementsOf(
              projection.capsules().stream()
                  .flatMap(value -> value.modelIneligibilityGapIds().stream())
                  .sorted()
                  .toList());
    }
  }

  private static ModulePublicationReference publish(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      ModulePublicationReference flows,
      CapsuleProjection projection)
      throws Exception {
    try {
      Class<?> publisherType = Class.forName(PUBLISHER_CLASS);
      Object publisher =
          publisherType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class,
                  org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader.class)
              .newInstance(fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader());
      Method publish =
          publisherType.getMethod(
              "publish",
              ModulePublicationReference.class,
              org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              CapsuleProjection.class);
      return (ModulePublicationReference)
          publish.invoke(publisher, flows, fixture.sourceInventory(), fixture.programGraphs(), facts, projection);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("CAPSULE_PROJECTION_MODULE_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("CAPSULE_PROJECTION_MODULE_PUBLISHER_FAILED", cause);
    }
  }

  private ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
            .reopen(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    FactCandidateSet candidates =
        new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaFacts());
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

  private static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", "capsule-publisher-flow"), 16, 8, 64, 96, 32);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", "capsule-publisher"), 16, 32, 4_096, 24_576);
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest(value)), new Sha256Digest(digest(value + "-bytes")));
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
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
