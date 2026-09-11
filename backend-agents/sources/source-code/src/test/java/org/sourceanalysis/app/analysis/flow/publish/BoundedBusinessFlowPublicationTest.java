package org.sourceanalysis.app.analysis.flow.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Public-seam regression for bounded repository closure through BusinessFlows M3. */
class BoundedBusinessFlowPublicationTest {

  @TempDir Path temporaryDirectory;

  @Test
  void publishesBoundedFlowWithFalseRepositoryClosureAndClosedLocalPredecessors() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithBoundedPathSet(
            temporaryDirectory.resolve("bounded-business-flows"))) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedAnalysisStepPublication discovery =
          fixture.stepArtifacts().reopen(fixture.applicationDiscovery().publication());
      JsonNode capability =
          canonicalJson.parseCanonical(payload(discovery, "capability-report.json"));
      JsonNode repositoryCoverage = requiredObject(capability, "repositoryEntryCoverage");
      assertThat(repositoryCoverage.path("closed").isBoolean()).isTrue();
      assertThat(repositoryCoverage.path("closed").booleanValue()).isFalse();
      List<JsonNode> discoveredEntries = jsonLines(discovery, "entry-points.jsonl");
      Map<String, JsonNode> discoveredEntriesById = objectsById(discoveredEntries, "entryId");
      assertThat(discoveredEntriesById).isNotEmpty();
      assertThat(repositoryCoverage.path("entryCount").intValue())
          .isEqualTo(discoveredEntriesById.size());
      assertThat(textValues(requiredArray(repositoryCoverage, "entryIds")))
          .containsExactlyInAnyOrderElementsOf(discoveredEntriesById.keySet());

      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference flowCompilation =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      ReopenedModulePublication reopenedM1 = fixture.moduleArtifacts().reopen(flowCompilation);
      JsonNode m1Envelope =
          canonicalJson.parseCanonical(reopenedM1.payloads().get(0).canonicalUtf8());
      JsonNode m1Payload = requiredObject(m1Envelope, "payload");
      JsonNode m1Coverage = requiredObject(m1Payload, "coverage");
      assertThat(m1Coverage.path("closed").isBoolean()).isTrue();
      assertThat(m1Coverage.path("closed").booleanValue()).isTrue();
      Map<String, JsonNode> m1EntriesById =
          objectsById(requiredArray(m1Payload, "entryDispositions"), "entryId");
      Map<String, JsonNode> m1FlowsById =
          objectsById(requiredArray(m1Payload, "flowSlices"), "flowSliceId");
      assertThat(m1EntriesById).isNotEmpty();
      assertThat(m1FlowsById).isNotEmpty();
      assertThat(m1EntriesById.keySet())
          .containsExactlyInAnyOrderElementsOf(discoveredEntriesById.keySet());
      assertThat(textValues(requiredArray(m1Coverage, "flowSliceIds")))
          .containsExactlyInAnyOrderElementsOf(m1FlowsById.keySet());

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
      ReopenedModulePublication reopenedM2 = fixture.moduleArtifacts().reopen(capsuleProjection);
      JsonNode m2Envelope =
          canonicalJson.parseCanonical(reopenedM2.payloads().get(0).canonicalUtf8());
      JsonNode m2Payload = requiredObject(m2Envelope, "payload");
      Map<String, JsonNode> m2CapsulesByFlow =
          objectsById(requiredArray(m2Payload, "capsules"), "flowSliceId");
      assertThat(m2CapsulesByFlow).isNotEmpty();
      assertThat(m2CapsulesByFlow.keySet())
          .containsExactlyInAnyOrderElementsOf(m1FlowsById.keySet());
      List<String> m2CapsuleIds =
          m2CapsulesByFlow.values().stream()
              .map(value -> requiredText(value, "evidenceCapsuleId"))
              .toList();
      assertThat(m2CapsuleIds).doesNotHaveDuplicates();

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
      ReopenedAnalysisStepPublication reopenedPublic =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      JsonNode publicFlowSlices =
          canonicalJson.parseCanonical(payload(reopenedPublic, "flow-slices.json"));
      Map<String, JsonNode> publicFlowsById =
          objectsById(requiredArray(publicFlowSlices, "flowSlices"), "flowSliceId");
      List<JsonNode> publicEntries = jsonLines(reopenedPublic, "entry-dispositions.jsonl");
      Map<String, JsonNode> publicEntriesById = objectsById(publicEntries, "entryId");
      List<JsonNode> publicCapsules = jsonLines(reopenedPublic, "evidence-capsules.jsonl");
      Map<String, JsonNode> publicCapsulesByFlow = objectsById(publicCapsules, "flowSliceId");
      List<String> publicCapsuleIds =
          publicCapsules.stream().map(value -> requiredText(value, "evidenceCapsuleId")).toList();
      assertThat(publicEntriesById).isNotEmpty();
      assertThat(publicFlowsById).isNotEmpty();
      assertThat(publicCapsulesByFlow).isNotEmpty();
      assertThat(publicEntriesById.keySet())
          .containsExactlyInAnyOrderElementsOf(m1EntriesById.keySet());
      assertThat(publicFlowsById.keySet())
          .containsExactlyInAnyOrderElementsOf(m1FlowsById.keySet());
      assertThat(publicCapsulesByFlow.keySet())
          .containsExactlyInAnyOrderElementsOf(m2CapsulesByFlow.keySet());
      assertThat(publicCapsuleIds).containsExactlyInAnyOrderElementsOf(m2CapsuleIds);

      JsonNode publicCoverage =
          canonicalJson.parseCanonical(payload(reopenedPublic, "flow-coverage.json"));
      assertThat(publicCoverage.path("closed").isBoolean()).isTrue();
      assertThat(publicCoverage.path("closed").booleanValue()).isFalse();
      assertThat(textValues(requiredArray(publicCoverage, "entryIds")))
          .containsExactlyInAnyOrderElementsOf(m1EntriesById.keySet());
      assertThat(textValues(requiredArray(publicCoverage, "flowSliceIds")))
          .containsExactlyInAnyOrderElementsOf(m1FlowsById.keySet());
      assertThat(textValues(requiredArray(publicCoverage, "capsuleIds")))
          .containsExactlyInAnyOrderElementsOf(m2CapsuleIds);
    }
  }

  private static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
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

  private static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", "bounded-business-flow"), 16, 8, 64, 96, 32, 64, 256);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", "bounded-business-capsule"), 16, 32, 4_096, 24_576);
  }

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest(value)), new Sha256Digest(digest(value + "-bytes")));
  }

  private static ImmutableBytes payload(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(value -> fileName.equals(value.descriptor().fileName()))
        .findFirst()
        .orElseThrow()
        .canonicalUtf8();
  }

  private static List<JsonNode> jsonLines(
      ReopenedAnalysisStepPublication publication, String fileName) {
    String value =
        new String(payload(publication, fileName).copyToByteArray(), StandardCharsets.UTF_8);
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    return value
        .lines()
        .map(
            line ->
                codec.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static Map<String, JsonNode> objectsById(List<JsonNode> values, String idField) {
    Map<String, JsonNode> result = new HashMap<>();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          assertThat(result.put(requiredText(value, idField), value)).isNull();
        });
    return Map.copyOf(result);
  }

  private static Map<String, JsonNode> objectsById(JsonNode values, String idField) {
    assertThat(values.isArray()).isTrue();
    Map<String, JsonNode> result = new HashMap<>();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          assertThat(result.put(requiredText(value, idField), value)).isNull();
        });
    return Map.copyOf(result);
  }

  private static JsonNode requiredObject(JsonNode value, String field) {
    assertThat(value.isObject()).isTrue();
    JsonNode child = value.get(field);
    assertThat(child).isNotNull();
    assertThat(child.isObject()).isTrue();
    return child;
  }

  private static JsonNode requiredArray(JsonNode value, String field) {
    assertThat(value.isObject()).isTrue();
    JsonNode child = value.get(field);
    assertThat(child).isNotNull();
    assertThat(child.isArray()).isTrue();
    return child;
  }

  private static String requiredText(JsonNode value, String field) {
    assertThat(value.isObject()).isTrue();
    JsonNode child = value.get(field);
    assertThat(child).isNotNull();
    assertThat(child.isTextual()).isTrue();
    assertThat(child.textValue()).isNotBlank();
    return child.textValue();
  }

  private static List<String> textValues(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    java.util.ArrayList<String> result = new java.util.ArrayList<>();
    values.forEach(
        value -> {
          assertThat(value.isTextual()).isTrue();
          result.add(value.textValue());
        });
    return List.copyOf(result);
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
