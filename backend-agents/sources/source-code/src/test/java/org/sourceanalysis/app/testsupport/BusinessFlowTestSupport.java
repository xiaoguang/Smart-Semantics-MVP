package org.sourceanalysis.app.testsupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjection;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionModulePublisher;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.capsule.EvidenceCapsuleProjector;
import org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * Neutral Step05 fixture support for current business-material, activity, knowledge, report and
 * runtime tests.
 *
 * <p>This class owns generic fixture publication only. It deliberately has no registry proposal,
 * task or model behavior from the retired interpretation route.
 */
public final class BusinessFlowTestSupport {

  private BusinessFlowTestSupport() {}

  public static BusinessFlowsReference publishBusinessFlows(ProgramGraphsPublicFixture fixture) {
    return publishBusinessFlows(fixture, defaultFlowProfile(), defaultCapsuleProfile());
  }

  public static BusinessFlowsReference publishBusinessFlows(
      ProgramGraphsPublicFixture fixture,
      FlowCompilationProfile flowCompilationProfile,
      CapsuleProjectionProfile capsuleProfile) {
    ProvenCodeFactsReference facts = publishProvenFacts(fixture);
    FlowCompilation compilation =
        new EntryRootedFlowCompiler(fixture.stepArtifacts())
            .compile(
                fixture.applicationDiscovery(),
                fixture.programGraphs(),
                facts,
                flowCompilationProfile);
    ModulePublicationReference flowCompilation =
        new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
            .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
    CapsuleProjection projection =
        new EvidenceCapsuleProjector(
                fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
            .project(
                flowCompilation,
                fixture.sourceInventory(),
                fixture.programGraphs(),
                facts,
                capsuleProfile);
    ModulePublicationReference capsuleProjection =
        new CapsuleProjectionModulePublisher(
                fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
            .publish(
                flowCompilation,
                fixture.sourceInventory(),
                fixture.programGraphs(),
                facts,
                projection);
    return new FlowPublicationSpecifier(
            fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
        .specify(
            fixture.sourceInventory(),
            fixture.applicationDiscovery(),
            fixture.programGraphs(),
            facts,
            flowCompilation,
            capsuleProjection);
  }

  public static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest(value)), new Sha256Digest(digest(value + "-bytes")));
  }

  private static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
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
    ModulePublicationReference candidatePublication =
        new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 1, "candidates"), inputs, candidates);
    ModulePublicationReference proofPublication =
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

  private static FlowCompilationProfile defaultFlowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", "business-flow-test-support"), 16, 8, 64, 96, 32, 64, 256);
  }

  private static CapsuleProjectionProfile defaultCapsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", "business-flow-test-support"), 16, 32, 4_096, 24_576);
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
    } catch (java.security.NoSuchAlgorithmException unsupported) {
      throw new AssertionError("SHA_256_UNAVAILABLE", unsupported);
    }
  }
}
