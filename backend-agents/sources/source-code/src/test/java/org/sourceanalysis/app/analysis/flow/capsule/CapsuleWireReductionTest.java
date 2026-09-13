package org.sourceanalysis.app.analysis.flow.capsule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.assertj.core.api.SoftAssertions;
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
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreException;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Public wire contract for removing retired registry proposal basis from Flow capsules. */
class CapsuleWireReductionTest {

  private static final String RETIRED_PROJECTION_TYPE = "BUSINESS_FLOWS_CAPSULE_PROJECTION";
  private static final String RETIRED_PROJECTION_SCHEMA = "business-flows-capsule-projection-v9";
  private static final String RETIRED_CAPSULE_TYPE = "BUSINESS_FLOWS_EVIDENCE_CAPSULE";
  private static final String RETIRED_CAPSULE_SCHEMA = "business-flows-evidence-capsule-v7";

  @TempDir Path temporaryDirectory;

  @Test
  void publishesReducedV10AndV8CapsulesAndRejectsRetiredWireVersions() {
    SoftAssertions softly = new SoftAssertions();
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-wire-reduction"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
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
                  capsuleProfile());
      ModulePublicationReference capsuleProjection =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  projection);

      ReopenedModulePublication reopenedProjection =
          fixture.moduleArtifacts().reopen(capsuleProjection);
      JsonNode projectionEnvelope =
          new CanonicalJsonCodec()
              .parseCanonical(reopenedProjection.payloads().get(0).canonicalUtf8());
      softly
          .assertThat(text(projectionEnvelope, "schemaVersion"))
          .as("capsule projection schema")
          .isEqualTo("business-flows-capsule-projection-v11");
      JsonNode projectionBody = projectionEnvelope.path("payload");
      JsonNode projectionCapsules = projectionBody.path("capsules");
      softly.assertThat(projectionCapsules.isArray()).isTrue();
      softly.assertThat(projectionCapsules).isNotEmpty();
      if (projectionCapsules.isArray() && !projectionCapsules.isEmpty()) {
        assertReducedCapsule(softly, projectionCapsules.get(0), "projected capsule");
      }
      softly.assertThat(projectionBody.path("modelEvidenceSpans").isArray()).isTrue();
      softly.assertThat(projectionBody.path("projectionObligations").isArray()).isTrue();

      BusinessFlowsReference businessFlows =
          new FlowPublicationSpecifier(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .specify(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  fixture.programGraphs(),
                  facts,
                  flowCompilation,
                  capsuleProjection);
      ReopenedAnalysisStepPublication reopenedBusinessFlows =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      JsonNode publicCapsule = firstJsonLine(reopenedBusinessFlows, "evidence-capsules.jsonl");
      softly
          .assertThat(text(publicCapsule, "schemaVersion"))
          .as("public evidence capsule schema")
          .isEqualTo("business-flows-evidence-capsule-v9");
      assertReducedCapsule(softly, publicCapsule, "public capsule");

      softly
          .assertThatThrownBy(
              () ->
                  fixture
                      .moduleArtifacts()
                      .resolveArtifactPolicy(
                          new ArtifactPolicyKey(
                              RETIRED_PROJECTION_TYPE, RETIRED_PROJECTION_SCHEMA)))
          .as("retired capsule projection v9 policy")
          .isInstanceOf(ArtifactStoreException.class)
          .hasMessage("ARTIFACT_POLICY_NOT_FOUND");
      softly
          .assertThatThrownBy(
              () ->
                  fixture
                      .moduleArtifacts()
                      .resolveArtifactPolicy(
                          new ArtifactPolicyKey(RETIRED_CAPSULE_TYPE, RETIRED_CAPSULE_SCHEMA)))
          .as("retired evidence capsule v7 policy")
          .isInstanceOf(ArtifactStoreException.class)
          .hasMessage("ARTIFACT_POLICY_NOT_FOUND");
    } finally {
      softly.assertAll();
    }
  }

  private static void assertReducedCapsule(
      SoftAssertions softly, JsonNode capsule, String description) {
    softly.assertThat(capsule.has("registryProposalBasisAtomIds")).as(description).isFalse();
    softly.assertThat(capsule.has("registryProposalBasisGapIds")).as(description).isFalse();
    softly.assertThat(capsule.path("flowSliceId").isTextual()).as(description).isTrue();
    softly.assertThat(capsule.path("proofPackId").isTextual()).as(description).isTrue();
    softly.assertThat(capsule.path("entryView").isObject()).as(description).isTrue();
    softly.assertThat(capsule.has("entryContext")).as(description).isFalse();
    softly.assertThat(capsule.path("entryContextRef").isObject()).as(description).isTrue();
    softly.assertThat(capsule.path("factViews").isArray()).as(description).isTrue();
    softly.assertThat(capsule.path("gapViews").isArray()).as(description).isTrue();
    softly.assertThat(capsule.path("outcomePathViews").isArray()).as(description).isTrue();
    softly.assertThat(capsule.path("processJoinSignals").isArray()).as(description).isTrue();
    softly.assertThat(capsule.path("factViews")).as(description).isNotEmpty();
    softly.assertThat(capsule.path("outcomePathViews")).as(description).isNotEmpty();
    softly.assertThat(capsule.path("processJoinSignals")).as(description).isNotEmpty();
    softly.assertThat(capsule.path("modelEvidenceSpanIds").isArray()).as(description).isTrue();
    softly.assertThat(capsule.path("modelEvidenceSpanIds")).as(description).isNotEmpty();
    softly.assertThat(capsule.path("projectionObligationIds").isArray()).as(description).isTrue();
    softly.assertThat(capsule.path("projectionObligationIds")).as(description).isNotEmpty();
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

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", "capsule-wire-reduction"), 16, 8, 64, 96, 32, 64, 256);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", "capsule-wire-reduction"), 16, 32, 1_000_000, 1_000_000);
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(
        org.sourceanalysis.app.artifact.ArtifactId.parse(prefix + ":" + digest(value)),
        new Sha256Digest(digest(value + "-bytes")));
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unsupported) {
      throw new AssertionError("SHA_256_UNAVAILABLE", unsupported);
    }
  }

  private static String text(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);
    assertThat(value.isTextual()).as(fieldName).isTrue();
    return value.textValue();
  }

  private static JsonNode firstJsonLine(
      ReopenedAnalysisStepPublication publication, String fileName) {
    var payload =
        publication.semanticPayloads().stream()
            .filter(value -> fileName.equals(value.descriptor().fileName()))
            .findFirst()
            .orElseThrow();
    String firstLine =
        new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8)
            .lines()
            .findFirst()
            .orElseThrow(() -> new AssertionError("empty " + fileName));
    return new CanonicalJsonCodec()
        .parseCanonical(ImmutableBytes.copyOf(firstLine.getBytes(StandardCharsets.UTF_8)));
  }
}
