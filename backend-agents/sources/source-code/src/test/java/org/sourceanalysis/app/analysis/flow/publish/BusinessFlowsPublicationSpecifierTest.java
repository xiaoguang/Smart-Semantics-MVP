package org.sourceanalysis.app.analysis.flow.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
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
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjection;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionModulePublisher;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.capsule.EvidenceCapsuleProjector;
import org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M3 public seam: all compiled flows and capsules become one five-file Business Flows step. */
class BusinessFlowsPublicationSpecifierTest {

  private static final String SPECIFIER_CLASS =
      "org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier";

  @TempDir Path temporaryDirectory;

  @Test
  void installsTheExactFiveSemanticFilesAfterJoiningEveryPersistedFlowAndCapsule()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-flows-publication"))) {
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

      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference =
          specify(fixture, facts, flowCompilation, capsuleProjection);
      ReopenedAnalysisStepPublication reopened = fixture.stepArtifacts().reopen(reference);

      assertThat(reopened.receipt().address().analysisStepKey())
          .isEqualTo(AnalysisStepKey.BUSINESS_FLOWS);
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
      assertThat(reopened.semanticPayloads()).hasSize(5);
      String flowGaps =
          new String(
              reopened.semanticPayloads().stream()
                  .filter(value -> value.descriptor().fileName().equals("flow-gaps.jsonl"))
                  .findFirst()
                  .orElseThrow()
                  .canonicalUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      assertThat(flowGaps).contains("\"scope\":\"FLOW\"").contains("\"affectedSemanticIds\"");
    }
  }

  @Test
  void publishesAnOverBudgetFlowAndItsGapAsModelIneligibleInsteadOfDiscardingTheFlow()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-flows-ineligible-publication"))) {
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
                  new CapsuleProjectionProfile(
                      reference("capsule-profile", "business-flows-ineligible-publication"),
                      16,
                      32,
                      4_096,
                      1));
      assertThat(projection.capsules())
          .allMatch(value -> value.modelEligibility().equals("INELIGIBLE"));
      ModulePublicationReference capsuleProjection =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  projection);

      var reference = specify(fixture, facts, flowCompilation, capsuleProjection);
      ReopenedAnalysisStepPublication reopened = fixture.stepArtifacts().reopen(reference);
      JsonNode coverage =
          new CanonicalJsonCodec()
              .parseCanonical(
                  reopened.semanticPayloads().stream()
                      .filter(value -> value.descriptor().fileName().equals("flow-coverage.json"))
                      .findFirst()
                      .orElseThrow()
                      .canonicalUtf8());

      assertThat(coverage.at("/modelEligibleFlowSliceIds")).isEmpty();
      assertThat(coverage.at("/modelIneligibleFlowSliceIds")).hasSize(projection.capsules().size());
      assertThat(coverage.at("/modelIneligibilityByFlow")).hasSize(projection.capsules().size());
      assertThat(coverage.at("/modelIneligibilityGapIds")).hasSize(projection.capsules().size());
    }
  }

  @Test
  void publishesEachCapsulesCompleteFlowLocalSpansAndObligationsForTheNextAnalysisStep()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-flows-public-evidence-handoff"))) {
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

      var reference = specify(fixture, facts, flowCompilation, capsuleProjection);
      ReopenedAnalysisStepPublication reopened = fixture.stepArtifacts().reopen(reference);
      List<JsonNode> lines = jsonLines(reopened, "evidence-capsules.jsonl");

      assertThat(lines).hasSize(2);
      Set<String> allSpanIds = new java.util.HashSet<>();
      Set<String> allObligationIds = new java.util.HashSet<>();
      lines.forEach(
          capsule -> {
            assertThat(capsule.path("schemaVersion").asText())
                .isEqualTo("business-flows-evidence-capsule-v2");
            List<String> referencedSpanIds = strings(capsule.path("modelEvidenceSpanIds"));
            List<String> embeddedSpanIds = strings(capsule.path("modelEvidenceSpans"), "spanId");
            List<String> referencedObligationIds = strings(capsule.path("projectionObligationIds"));
            List<String> embeddedObligationIds =
                strings(capsule.path("projectionObligations"), "obligationId");

            assertThat(embeddedSpanIds).containsExactlyInAnyOrderElementsOf(referencedSpanIds);
            assertThat(embeddedObligationIds)
                .containsExactlyInAnyOrderElementsOf(referencedObligationIds);
            assertThat(capsule.path("modelEvidenceSpans"))
                .allSatisfy(
                    span ->
                        assertThat(span.path("sourceExcerpt").path("rawUtf8").asText())
                            .isNotBlank());
            assertThat(capsule.path("projectionObligations"))
                .allSatisfy(
                    obligation -> assertThat(obligation.path("satisfyingSpanIds")).isNotEmpty());
            embeddedSpanIds.forEach(
                spanId -> assertThat(allSpanIds.add(spanId)).as("span id %s", spanId).isTrue());
            embeddedObligationIds.forEach(
                obligationId ->
                    assertThat(allObligationIds.add(obligationId))
                        .as("obligation id %s", obligationId)
                        .isTrue());
          });
    }
  }

  private static List<JsonNode> jsonLines(
      ReopenedAnalysisStepPublication publication, String fileName) {
    String jsonl =
        new String(
            publication.semanticPayloads().stream()
                .filter(value -> fileName.equals(value.descriptor().fileName()))
                .findFirst()
                .orElseThrow()
                .canonicalUtf8()
                .copyToByteArray(),
            StandardCharsets.UTF_8);
    return jsonl
        .lines()
        .map(
            value ->
                new CanonicalJsonCodec()
                    .parseCanonical(
                        org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                            value.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static List<String> strings(JsonNode values) {
    return java.util.stream.StreamSupport.stream(values.spliterator(), false)
        .map(JsonNode::asText)
        .toList();
  }

  private static List<String> strings(JsonNode values, String fieldName) {
    return java.util.stream.StreamSupport.stream(values.spliterator(), false)
        .map(value -> value.path(fieldName).asText())
        .toList();
  }

  private static org.sourceanalysis.app.artifact.AnalysisStepPublicationReference specify(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      ModulePublicationReference flowCompilation,
      ModulePublicationReference capsuleProjection)
      throws Exception {
    try {
      Class<?> specifierType = Class.forName(SPECIFIER_CLASS);
      Object specifier =
          specifierType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
              .newInstance(fixture.moduleArtifacts(), fixture.stepArtifacts());
      Method method =
          specifierType.getMethod(
              "specify",
              org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
              org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              ModulePublicationReference.class,
              ModulePublicationReference.class);
      Object result =
          method.invoke(
              specifier,
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              fixture.programGraphs(),
              facts,
              flowCompilation,
              capsuleProjection);
      Method publication = result.getClass().getMethod("publication");
      return (org.sourceanalysis.app.artifact.AnalysisStepPublicationReference)
          publication.invoke(result);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FLOW_PUBLICATION_SPECIFIER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("FLOW_PUBLICATION_SPECIFIER_FAILED", cause);
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
        reference("flow-profile", "business-flows-publication"), 16, 8, 64, 96, 32);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", "business-flows-publication"), 16, 32, 4_096, 24_576);
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
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
