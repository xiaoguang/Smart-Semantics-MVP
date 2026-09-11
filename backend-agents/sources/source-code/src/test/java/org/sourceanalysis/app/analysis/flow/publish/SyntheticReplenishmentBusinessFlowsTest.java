package org.sourceanalysis.app.analysis.flow.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Public-seam acceptance for the synthetic seven-entry replenishment-to-settlement unit. */
class SyntheticReplenishmentBusinessFlowsTest {

  private static final Set<String> METHODS =
      Set.of(
          "submitReplenishment",
          "approveAtStore",
          "approveRegionAndCreatePurchaseOrder",
          "approvePurchaseOrderAndProcessExpense",
          "executePurchaseAndRegisterLogistics",
          "receiveAndRegisterInventory",
          "generateConfirmAndSettleMonthlyBill");

  @TempDir Path temporaryDirectory;

  @Test
  void publishesSevenIndependentFlowsAndCapsulesFromTheRealSyntheticEntryUnit() {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createSyntheticReplenishmentToSettlement(
            temporaryDirectory.resolve("synthetic-replenishment-to-settlement"))) {
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedAnalysisStepPublication discovery =
          fixture.stepArtifacts().reopen(fixture.applicationDiscovery().publication());
      JsonNode capability =
          canonicalJson.parseCanonical(payload(discovery, "capability-report.json"));
      List<JsonNode> entryPoints = jsonLines(discovery, "entry-points.jsonl");
      Set<String> discoveredEntryIds =
          entryPoints.stream()
              .map(value -> value.path("entryId").asText())
              .collect(Collectors.toSet());
      Set<String> discoveredMethods =
          entryPoints.stream()
              .map(
                  value ->
                      value
                          .path("handlerFqn")
                          .asText()
                          .substring(value.path("handlerFqn").asText().indexOf('#') + 1))
              .collect(Collectors.toSet());
      assertThat(capability.path("repositoryEntryCoverage").path("entryCount").asInt())
          .isEqualTo(7);
      assertThat(discoveredEntryIds).hasSize(7);
      assertThat(discoveredMethods).containsExactlyInAnyOrderElementsOf(METHODS);
      String sourceText =
          new String(
              fixture.sourceReader().reopen(fixture.sourceInventory()).documents().stream()
                  .filter(value -> value.path().endsWith("SyntheticReplenishmentController.java"))
                  .findFirst()
                  .orElseThrow()
                  .rawUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      METHODS.forEach(
          method -> assertThat(sourceText).contains("void " + method + "(String status)"));

      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      assertThat(compilation.entryDispositions()).hasSize(7);
      assertThat(compilation.flowSlices()).hasSize(7);
      assertThat(compilation.flowGaps()).isNotEmpty();
      assertThat(compilation.entryDispositions())
          .allMatch(value -> "COMPILED".equals(value.disposition()));
      Set<String> compiledEntryIds =
          compilation.entryDispositions().stream()
              .map(FlowCompilation.EntryDisposition::entryId)
              .collect(Collectors.toSet());
      Set<String> flowIds =
          compilation.flowSlices().stream()
              .map(FlowCompilation.FlowSlice::flowSliceId)
              .collect(Collectors.toSet());
      Set<String> rootIds =
          compilation.flowSlices().stream()
              .map(FlowCompilation.FlowSlice::rootNodeId)
              .collect(Collectors.toSet());
      assertThat(compiledEntryIds).containsExactlyInAnyOrderElementsOf(discoveredEntryIds);
      assertThat(flowIds).hasSize(7);
      assertThat(rootIds).hasSize(7);

      Map<String, FlowCompilation.FlowSlice> flowsById =
          compilation.flowSlices().stream()
              .collect(Collectors.toMap(FlowCompilation.FlowSlice::flowSliceId, value -> value));
      Set<String> knownGapIds =
          compilation.flowGaps().stream()
              .map(FlowCompilation.FlowGap::gapId)
              .collect(Collectors.toSet());
      compilation
          .flowSlices()
          .forEach(
              flow -> {
                assertThat(flow.factIds()).isNotEmpty();
                assertThat(flow.atomIds()).isNotEmpty();
                assertThat(flow.outcomePaths()).isNotEmpty();
                assertThat(flow.gapIds()).allMatch(knownGapIds::contains);
                assertThat(flow.processJoinSignals()).isNotEmpty();
                flow.processJoinSignals()
                    .forEach(
                        signal -> {
                          assertThat(signal.flowSliceId()).isEqualTo(flow.flowSliceId());
                          assertThat(signal.specificity()).isEqualTo("GENERIC_TECHNICAL");
                          assertThat(signal.factIds()).isNotEmpty();
                          assertThat(signal.atomIds()).isNotEmpty();
                          assertThat(signal.proofIds()).isNotEmpty();
                          assertThat(signal.evidenceNodeIds()).isNotEmpty();
                          assertThat(signal.sourceLocators()).isNotEmpty();
                          if ("EXTERNAL_EFFECT_GAP".equals(signal.signalKind())) {
                            assertThat(signal.gapIds()).isNotEmpty();
                            assertThat(signal.gapIds()).allMatch(knownGapIds::contains);
                          }
                        });
              });

      ModulePublicationReference flowCompilation =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      JsonNode reopenedM1 =
          canonicalJson.parseCanonical(
              fixture.moduleArtifacts().reopen(flowCompilation).payloads().get(0).canonicalUtf8());
      assertThat(reopenedM1.path("payload").path("flowSlices")).hasSize(7);
      assertThat(reopenedM1.path("payload").path("entryDispositions")).hasSize(7);

      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  capsuleProfile());
      assertThat(projection.capsules()).hasSize(7);
      Set<String> capsuleFlowIds =
          projection.capsules().stream()
              .map(CapsuleProjection.EvidenceCapsule::flowSliceId)
              .collect(Collectors.toSet());
      assertThat(capsuleFlowIds).containsExactlyInAnyOrderElementsOf(flowIds);
      Set<String> allSpanIds = new HashSet<>();
      Set<String> allObligationIds = new HashSet<>();
      projection
          .capsules()
          .forEach(
              capsule -> {
                FlowCompilation.FlowSlice flow = flowsById.get(capsule.flowSliceId());
                assertThat(flow).isNotNull();
                assertThat(capsule.entryView().entryId()).isEqualTo(flow.entryId());
                assertThat(capsule.factViews()).isNotEmpty();
                assertThat(capsule.outcomePathViews()).isNotEmpty();
                assertThat(capsule.gapViews())
                    .allSatisfy(gap -> assertThat(knownGapIds).contains(gap.gapId()));
                assertThat(capsule.modelEvidenceSpanIds()).isNotEmpty();
                assertThat(capsule.projectionObligationIds()).isNotEmpty();
                assertThat(capsule.processJoinSignals())
                    .containsExactlyElementsOf(flow.processJoinSignals());
                assertThat(capsule.processJoinSignals())
                    .allMatch(value -> "GENERIC_TECHNICAL".equals(value.specificity()));
                capsule
                    .factViews()
                    .forEach(
                        fact ->
                            fact.atoms().forEach(atom -> assertThat(atom.proofId()).isNotBlank()));
                capsule
                    .outcomePathViews()
                    .forEach(outcome -> assertThat(outcome.requiredProofIds()).isNotEmpty());
                assertThat(capsule.modelEvidenceSpanIds()).allMatch(allSpanIds::add);
                assertThat(capsule.projectionObligationIds()).allMatch(allObligationIds::add);
              });
      assertThat(allSpanIds).hasSize(projection.modelEvidenceSpans().size());
      assertThat(allObligationIds).hasSize(projection.projectionObligations().size());

      ModulePublicationReference capsuleProjection =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  projection);
      JsonNode reopenedM2 =
          canonicalJson.parseCanonical(
              fixture
                  .moduleArtifacts()
                  .reopen(capsuleProjection)
                  .payloads()
                  .get(0)
                  .canonicalUtf8());
      assertThat(reopenedM2.path("payload").path("capsules")).hasSize(7);
      assertThat(jsonStrings(reopenedM2.path("payload").path("capsules"), "flowSliceId"))
          .containsExactlyInAnyOrderElementsOf(flowIds);

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
      ReopenedAnalysisStepPublication reopened =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      assertThat(reopened.semanticPayloads())
          .extracting(value -> value.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
      JsonNode coverage = canonicalJson.parseCanonical(payload(reopened, "flow-coverage.json"));
      assertThat(coverage.path("closed").asBoolean()).isTrue();
      assertThat(jsonStrings(coverage.path("entryIds"))).hasSize(7);
      assertThat(jsonStrings(coverage.path("compiledEntryIds")))
          .containsExactlyInAnyOrderElementsOf(discoveredEntryIds);
      assertThat(jsonStrings(coverage.path("gappedEntryIds"))).isEmpty();
      assertThat(jsonStrings(coverage.path("excludedEntryIds"))).isEmpty();
      assertThat(jsonStrings(coverage.path("flowSliceIds")))
          .containsExactlyInAnyOrderElementsOf(flowIds);
      assertThat(jsonStrings(coverage.path("capsuleIds"))).hasSize(7);
      assertThat(jsonStrings(coverage.path("processJoinSignalIds"))).isNotEmpty();

      JsonNode publicFlows = canonicalJson.parseCanonical(payload(reopened, "flow-slices.json"));
      Map<String, JsonNode> publicFlowsById =
          jsonNodesById(publicFlows.path("flowSlices"), "flowSliceId");
      List<JsonNode> publicCapsules = jsonLines(reopened, "evidence-capsules.jsonl");
      Map<String, JsonNode> publicCapsulesByFlow =
          jsonNodesById(toArray(publicCapsules), "flowSliceId");
      assertThat(publicFlowsById).hasSize(7);
      assertThat(publicCapsulesByFlow).hasSize(7);
      assertThat(publicCapsulesByFlow.keySet())
          .containsExactlyInAnyOrderElementsOf(publicFlowsById.keySet());
      publicFlowsById.forEach(
          (flowId, flow) -> {
            JsonNode capsule = publicCapsulesByFlow.get(flowId);
            assertThat(
                    canonicalJson
                        .encodeCanonical(flow.path("processJoinSignals"))
                        .copyToByteArray())
                .containsExactly(
                    canonicalJson
                        .encodeCanonical(capsule.path("processJoinSignals"))
                        .copyToByteArray());
            assertThat(jsonStrings(flow.path("processJoinSignals"), "specificity"))
                .allMatch("GENERIC_TECHNICAL"::equals);
          });
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
        reference("flow-profile", "synthetic-seven-entry-flow"), 16, 8, 64, 96, 32, 64, 256);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", "synthetic-seven-entry-capsules"), 16, 32, 4_096, 24_576);
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
                codec.parseCanonical(
                    org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                        line.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static List<JsonNode> jsonObjects(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    List<JsonNode> result = new java.util.ArrayList<>();
    values.forEach(result::add);
    return result;
  }

  private static List<String> jsonStrings(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    List<String> result = new java.util.ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static List<String> jsonStrings(JsonNode values, String fieldName) {
    assertThat(values.isArray()).isTrue();
    List<String> result = new java.util.ArrayList<>();
    values.forEach(value -> result.add(value.path(fieldName).asText()));
    return result;
  }

  private static Map<String, JsonNode> jsonNodesById(JsonNode values, String property) {
    Map<String, JsonNode> result = new HashMap<>();
    jsonObjects(values)
        .forEach(value -> assertThat(result.put(value.path(property).asText(), value)).isNull());
    return Map.copyOf(result);
  }

  private static JsonNode toArray(List<JsonNode> values) {
    com.fasterxml.jackson.databind.node.ArrayNode array =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    values.forEach(array::add);
    return array;
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
