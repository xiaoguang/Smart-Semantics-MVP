package org.sourceanalysis.app.analysis.flow.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** M3 public seam: all compiled flows and capsules become one five-file Business Flows step. */
class BusinessFlowsPublicationSpecifierTest {

  private static final String SPECIFIER_CLASS =
      "org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier";

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsACollectedContextThatCarriesACollectionFailureReason() throws Exception {
    ArtifactReference index = reference("java-code-index", "collected-context-reason");
    String entryId = "entry:" + digest("collected-context-reason-entry");
    ObjectNode context = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
    context.put("entryId", entryId);
    context.put("collectionStatus", "COLLECTED");
    context.put("collectionReason", "UNEXPECTED_FAILURE_REASON");
    ObjectNode codeContextRef = context.putObject("codeContextRef");
    codeContextRef.put("entryId", entryId);
    codeContextRef
        .putObject("indexArtifact")
        .put("artifactId", index.artifactId().value())
        .put("sha256", index.sha256().value());
    Method validator =
        Class.forName(SPECIFIER_CLASS)
            .getDeclaredMethod(
                "requireContextReferenceClosure", List.class, ArtifactReference.class);
    validator.setAccessible(true);

    ObjectNode valid = context.deepCopy();
    valid.putNull("collectionReason");
    validator.invoke(null, List.of(valid), index);

    assertThatThrownBy(() -> validator.invoke(null, List.of(context), index))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

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
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      JsonNode publicFlows =
          canonicalJson.parseCanonical(
              reopened.semanticPayloads().stream()
                  .filter(value -> "flow-slices.json".equals(value.descriptor().fileName()))
                  .findFirst()
                  .orElseThrow()
                  .canonicalUtf8());
      assertThat(publicFlows.path("entryContexts")).hasSize(2);
      assertThat(publicFlows.path("entryContexts"))
          .allSatisfy(
              context -> {
                assertThat(context.path("entryId").asText()).isNotBlank();
                assertThat(context.path("collectionStatus").asText()).isEqualTo("NOT_COLLECTED");
                assertThat(context.path("collectionReason").asText())
                    .isEqualTo("JAVA_CODE_CONTEXT_NOT_AVAILABLE_ON_STRICT_GRAPH_PATH");
                assertThat(context.has("codeContext")).isFalse();
                assertThat(context.path("codeContextRef").isNull()).isTrue();
                JsonNode technicalContext = context.path("strictTechnicalContext");
                assertThat(technicalContext.path("entrySignature").asText()).isNotBlank();
                assertThat(technicalContext.path("sourceLocators")).isNotEmpty();
              });
      assertThat(publicFlows.path("entryContexts"))
          .anySatisfy(
              context ->
                  assertThat(context.path("strictTechnicalContext").path("calls"))
                      .anySatisfy(
                          call -> {
                            assertThat(call.path("callerSignature").asText())
                                .contains("OrderController");
                            assertThat(call.path("targetSignature").asText())
                                .contains("OrderService");
                            assertThat(call.path("argumentExpressions"))
                                .extracting(JsonNode::asText)
                                .contains("java.lang.String");
                          }));
      ReopenedAnalysisStepPublication reopenedFacts =
          fixture.stepArtifacts().reopen(facts.publication());
      VerifiedCanonicalPayload gapLedgerPayload =
          reopenedFacts.semanticPayloads().stream()
              .filter(value -> value.descriptor().fileName().equals("gap-ledger.json"))
              .findFirst()
              .orElseThrow();
      JsonNode gapLedger = canonicalJson.parseCanonical(gapLedgerPayload.canonicalUtf8());
      Map<String, JsonNode> sourceGaps = jsonNodesById(gapLedger.path("gaps"), "gapId");
      assertThat(sourceGaps).isNotEmpty();
      List<JsonNode> publicGaps = jsonLines(reopened, "flow-gaps.jsonl");
      Map<String, JsonNode> publicGapsById = jsonNodesById(toArray(publicGaps), "gapId");
      assertThat(publicGapsById.keySet()).containsExactlyInAnyOrderElementsOf(sourceGaps.keySet());
      for (Map.Entry<String, JsonNode> sourceEntry : sourceGaps.entrySet()) {
        JsonNode publicGap = publicGapsById.get(sourceEntry.getKey());
        assertThat(publicGap).isNotNull();
        assertThat(publicGap.path("scope").asText()).isEqualTo("FACT");
        assertThat(publicGap.path("originKind").asText()).isEqualTo("PROVEN_CODE_FACTS_GAP_LEDGER");
        assertThat(publicGap.path("reasonCode").asText())
            .isEqualTo(sourceEntry.getValue().path("code").asText());
        assertThat(publicGap.path("affectedSemanticIds"))
            .isEqualTo(sourceEntry.getValue().path("affectedCandidateDenominatorKeys"));
        assertThat(publicGap.path("originGapLedgerRef").path("artifactId").asText())
            .isEqualTo(gapLedgerPayload.descriptor().artifactId().value());
        assertThat(publicGap.path("originGapLedgerRef").path("sha256").asText())
            .isEqualTo(gapLedgerPayload.descriptor().sha256().value());
      }
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
                .isEqualTo("business-flows-evidence-capsule-v9");
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

  @Test
  void publishesFullProcessJoinSignalsAndClosedCapsuleEvidenceToThePublicStep() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("business-flows-public-signals"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      assertThat(compilation.flowSlices()).hasSize(2);
      assertThat(compilation.flowSlices())
          .allSatisfy(
              flow -> {
                assertThat(flow.processJoinSignals()).hasSize(4);
                assertThat(
                        flow.processJoinSignals().stream()
                            .map(FlowCompilation.ProcessJoinSignalV1::signalKind)
                            .toList())
                    .containsExactlyInAnyOrder(
                        "EXPLICIT_CALL",
                        "EXPLICIT_CALL",
                        "JAVA_TYPE_ANCHOR",
                        "EXTERNAL_EFFECT_GAP");
              });

      ModulePublicationReference flowCompilation =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      JsonNode m1Envelope =
          canonicalJson.parseCanonical(
              fixture.moduleArtifacts().reopen(flowCompilation).payloads().get(0).canonicalUtf8());
      assertThat(m1Envelope.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-compilation-v6");
      JsonNode m1Payload = m1Envelope.path("payload");
      assertThat(m1Payload.isObject()).isTrue();
      Map<String, JsonNode> m1Flows = jsonNodesById(m1Payload.path("flowSlices"), "flowSliceId");
      assertThat(m1Flows).hasSize(2);
      assertThat(m1Flows.values())
          .allSatisfy(
              flow -> {
                JsonNode signals = flow.path("processJoinSignals");
                assertThat(signals.isArray()).isTrue();
                assertThat(signals).hasSize(4);
                assertThat(jsonStrings(signals, "signalKind"))
                    .containsExactlyInAnyOrder(
                        "EXPLICIT_CALL",
                        "EXPLICIT_CALL",
                        "JAVA_TYPE_ANCHOR",
                        "EXTERNAL_EFFECT_GAP");
              });

      CapsuleProjection projection =
          new EvidenceCapsuleProjector(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .project(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  capsuleProfile());
      assertThat(projection.capsules()).hasSize(2);
      assertThat(projection.capsules())
          .allSatisfy(
              capsule -> {
                assertThat(capsule.processJoinSignals()).hasSize(4);
                assertThat(
                        capsule.processJoinSignals().stream()
                            .map(FlowCompilation.ProcessJoinSignalV1::signalKind)
                            .toList())
                    .containsExactlyInAnyOrder(
                        "EXPLICIT_CALL",
                        "EXPLICIT_CALL",
                        "JAVA_TYPE_ANCHOR",
                        "EXTERNAL_EFFECT_GAP");
              });
      ModulePublicationReference capsuleProjection =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  flowCompilation,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  projection);

      var m2Reopened = fixture.moduleArtifacts().reopen(capsuleProjection);
      assertThat(m2Reopened.payloads()).hasSize(1);
      JsonNode m2Envelope =
          canonicalJson.parseCanonical(m2Reopened.payloads().get(0).canonicalUtf8());
      assertThat(m2Envelope.path("schemaVersion").asText())
          .isEqualTo("business-flows-capsule-projection-v11");
      JsonNode m2Payload = m2Envelope.path("payload");
      Map<String, JsonNode> m2Capsules = jsonNodesById(m2Payload.path("capsules"), "flowSliceId");
      assertThat(m2Capsules).hasSize(2);
      assertThat(m2Capsules.keySet()).containsExactlyInAnyOrderElementsOf(m1Flows.keySet());
      Map<String, JsonNode> m2Spans = jsonNodesById(m2Payload.path("modelEvidenceSpans"), "spanId");
      Map<String, JsonNode> m2Obligations =
          jsonNodesById(m2Payload.path("projectionObligations"), "obligationId");
      for (Map.Entry<String, JsonNode> entry : m1Flows.entrySet()) {
        JsonNode m1Signals = entry.getValue().path("processJoinSignals");
        JsonNode m2Signals = m2Capsules.get(entry.getKey()).path("processJoinSignals");
        assertThat(m2Signals.isArray()).isTrue();
        assertThat(m2Signals).hasSize(4);
        assertThat(jsonStrings(m2Signals, "signalKind"))
            .containsExactlyInAnyOrder(
                "EXPLICIT_CALL", "EXPLICIT_CALL", "JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP");
        assertThat(canonicalBytes(canonicalJson, m2Signals))
            .containsExactly(canonicalBytes(canonicalJson, m1Signals));
      }

      var reference = specify(fixture, facts, flowCompilation, capsuleProjection);
      ReopenedAnalysisStepPublication reopened = fixture.stepArtifacts().reopen(reference);
      assertThat(reopened.semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
      assertThat(reopened.semanticPayloads()).hasSize(5);
      assertThat(reopened.semanticPayloads())
          .filteredOn(payload -> payload.descriptor().fileName().equals("flow-slices.json"))
          .extracting(payload -> payload.descriptor().schemaVersion())
          .containsExactly("business-flows-flow-slices-v6");
      assertThat(reopened.semanticPayloads())
          .filteredOn(payload -> payload.descriptor().fileName().equals("evidence-capsules.jsonl"))
          .extracting(payload -> payload.descriptor().schemaVersion())
          .containsExactly("business-flows-evidence-capsule-v9");
      assertThat(reopened.receipt().controls())
          .isEqualTo(
              fixture
                  .stepArtifacts()
                  .reopen(fixture.sourceInventory().publication())
                  .receipt()
                  .controls());
      assertThat(reopened.receipt().upstreamAnalysisStepReferences())
          .containsExactlyInAnyOrder(
              fixture.sourceInventory().publication(),
              fixture.applicationDiscovery().publication(),
              fixture.programGraphs().publication(),
              facts.publication());

      JsonNode publicFlowDocument =
          canonicalJson.parseCanonical(
              reopened.semanticPayloads().stream()
                  .filter(value -> "flow-slices.json".equals(value.descriptor().fileName()))
                  .findFirst()
                  .orElseThrow()
                  .canonicalUtf8());
      assertThat(publicFlowDocument.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-slices-v6");
      Map<String, JsonNode> publicFlows =
          jsonNodesById(publicFlowDocument.path("flowSlices"), "flowSliceId");
      assertThat(publicFlows).hasSize(2);
      assertThat(publicFlows.keySet()).containsExactlyInAnyOrderElementsOf(m1Flows.keySet());
      for (Map.Entry<String, JsonNode> entry : publicFlows.entrySet()) {
        JsonNode publicSignals = entry.getValue().path("processJoinSignals");
        assertThat(publicSignals.isArray()).isTrue();
        assertThat(publicSignals).hasSize(4);
        assertThat(jsonStrings(publicSignals, "signalKind"))
            .containsExactlyInAnyOrder(
                "EXPLICIT_CALL", "EXPLICIT_CALL", "JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP");
        assertThat(canonicalBytes(canonicalJson, publicSignals))
            .containsExactly(
                canonicalBytes(
                    canonicalJson, m1Flows.get(entry.getKey()).path("processJoinSignals")));
        assertThat(canonicalBytes(canonicalJson, publicSignals))
            .containsExactly(
                canonicalBytes(
                    canonicalJson, m2Capsules.get(entry.getKey()).path("processJoinSignals")));
      }

      List<JsonNode> publicCapsules = jsonLines(reopened, "evidence-capsules.jsonl");
      assertThat(publicCapsules).hasSize(2);
      Map<String, JsonNode> publicCapsulesByFlow =
          jsonNodesById(toArray(publicCapsules), "flowSliceId");
      assertThat(publicCapsulesByFlow.keySet())
          .containsExactlyInAnyOrderElementsOf(m2Capsules.keySet());
      Set<String> publicSpanIds = new HashSet<>();
      Set<String> publicObligationIds = new HashSet<>();
      Set<String> expectedEligibleFlowIds = new HashSet<>();
      Set<String> expectedIneligibleFlowIds = new HashSet<>();
      for (JsonNode publicCapsule : publicCapsules) {
        String flowSliceId = publicCapsule.path("flowSliceId").asText();
        JsonNode m2Capsule = m2Capsules.get(flowSliceId);
        assertThat(m2Capsule).isNotNull();
        assertThat(publicCapsule.path("schemaVersion").asText())
            .isEqualTo("business-flows-evidence-capsule-v9");
        assertThat(publicCapsule.path("modelEligibility").asText())
            .isEqualTo(m2Capsule.path("modelEligibility").asText());
        if ("ELIGIBLE".equals(publicCapsule.path("modelEligibility").asText())) {
          expectedEligibleFlowIds.add(flowSliceId);
        } else {
          expectedIneligibleFlowIds.add(flowSliceId);
        }
        assertThat(canonicalBytes(canonicalJson, publicCapsule.path("processJoinSignals")))
            .containsExactly(canonicalBytes(canonicalJson, m2Capsule.path("processJoinSignals")));

        List<String> spanIds = strings(publicCapsule.path("modelEvidenceSpanIds"));
        List<String> embeddedSpanIds = strings(publicCapsule.path("modelEvidenceSpans"), "spanId");
        assertThat(embeddedSpanIds).containsExactlyElementsOf(spanIds);
        for (String spanId : spanIds) {
          assertThat(publicSpanIds.add(spanId)).as("span id %s", spanId).isTrue();
          JsonNode publicSpan =
              publicCapsule.path("modelEvidenceSpans").get(embeddedSpanIds.indexOf(spanId));
          JsonNode expectedSpan = m2Spans.get(spanId);
          assertThat(expectedSpan).isNotNull();
          assertThat(canonicalBytes(canonicalJson, publicSpan))
              .containsExactly(canonicalBytes(canonicalJson, expectedSpan));
          JsonNode supportedSignalIds = publicSpan.path("supportedProcessJoinSignalIds");
          assertThat(supportedSignalIds.isArray()).isTrue();
          assertThat(jsonStrings(supportedSignalIds))
              .containsExactlyElementsOf(
                  jsonStrings(expectedSpan.path("supportedProcessJoinSignalIds")));
        }

        List<String> obligationIds = strings(publicCapsule.path("projectionObligationIds"));
        List<String> embeddedObligationIds =
            strings(publicCapsule.path("projectionObligations"), "obligationId");
        assertThat(embeddedObligationIds).containsExactlyElementsOf(obligationIds);
        List<String> basisSignalIds = new ArrayList<>();
        for (String obligationId : obligationIds) {
          assertThat(publicObligationIds.add(obligationId))
              .as("obligation id %s", obligationId)
              .isTrue();
          JsonNode publicObligation =
              publicCapsule
                  .path("projectionObligations")
                  .get(embeddedObligationIds.indexOf(obligationId));
          JsonNode expectedObligation = m2Obligations.get(obligationId);
          assertThat(expectedObligation).isNotNull();
          assertThat(canonicalBytes(canonicalJson, publicObligation))
              .containsExactly(canonicalBytes(canonicalJson, expectedObligation));
          List<String> satisfyingSpanIds = strings(publicObligation.path("satisfyingSpanIds"));
          assertThat(satisfyingSpanIds).allMatch(spanIds::contains);
          if ("PROCESS_JOIN_SIGNAL_BASIS".equals(publicObligation.path("kind").asText())) {
            basisSignalIds.add(publicObligation.path("semanticItemId").asText());
          }
        }
        assertThat(basisSignalIds)
            .containsExactlyInAnyOrderElementsOf(
                jsonStrings(publicCapsule.path("processJoinSignals"), "processJoinSignalId"));
      }
      assertThat(publicSpanIds).containsExactlyInAnyOrderElementsOf(m2Spans.keySet());
      assertThat(publicObligationIds).containsExactlyInAnyOrderElementsOf(m2Obligations.keySet());

      JsonNode coverage =
          canonicalJson.parseCanonical(
              reopened.semanticPayloads().stream()
                  .filter(value -> "flow-coverage.json".equals(value.descriptor().fileName()))
                  .findFirst()
                  .orElseThrow()
                  .canonicalUtf8());
      assertThat(coverage.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-coverage-v2");
      Set<String> expectedSignalIds = new HashSet<>();
      m1Flows
          .values()
          .forEach(
              flow ->
                  expectedSignalIds.addAll(
                      jsonStrings(flow.path("processJoinSignals"), "processJoinSignalId")));
      m2Capsules
          .values()
          .forEach(
              capsule ->
                  expectedSignalIds.addAll(
                      jsonStrings(capsule.path("processJoinSignals"), "processJoinSignalId")));
      assertThat(jsonStrings(coverage.path("processJoinSignalIds")))
          .containsExactlyElementsOf(expectedSignalIds.stream().sorted().toList());
      assertThat(jsonStrings(coverage.path("flowSliceIds")))
          .containsExactlyElementsOf(m1Flows.keySet().stream().sorted().toList());
      assertThat(jsonStrings(coverage.path("modelEligibleFlowSliceIds")))
          .containsExactlyElementsOf(expectedEligibleFlowIds.stream().sorted().toList());
      assertThat(jsonStrings(coverage.path("modelIneligibleFlowSliceIds")))
          .containsExactlyElementsOf(expectedIneligibleFlowIds.stream().sorted().toList());

      List<JsonNode> publicDispositions = jsonLines(reopened, "entry-dispositions.jsonl");
      assertThat(publicDispositions).hasSize(2);
      Map<String, JsonNode> m1Dispositions =
          jsonNodesById(m1Payload.path("entryDispositions"), "entryId");
      Map<String, JsonNode> publicDispositionsById =
          jsonNodesById(toArray(publicDispositions), "entryId");
      assertThat(publicDispositionsById.keySet())
          .containsExactlyInAnyOrderElementsOf(m1Dispositions.keySet());
      for (Map.Entry<String, JsonNode> entry : publicDispositionsById.entrySet()) {
        assertThat(canonicalBytes(canonicalJson, withoutPublicationMetadata(entry.getValue())))
            .containsExactly(canonicalBytes(canonicalJson, m1Dispositions.get(entry.getKey())));
      }
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
    assertThat(values.isArray()).isTrue();
    return java.util.stream.StreamSupport.stream(values.spliterator(), false)
        .map(JsonNode::asText)
        .toList();
  }

  private static List<String> strings(JsonNode values, String fieldName) {
    assertThat(values.isArray()).isTrue();
    return java.util.stream.StreamSupport.stream(values.spliterator(), false)
        .map(value -> value.path(fieldName).asText())
        .toList();
  }

  private static List<String> jsonStrings(JsonNode values) {
    return strings(values);
  }

  private static List<String> jsonStrings(JsonNode values, String fieldName) {
    return strings(values, fieldName);
  }

  private static Map<String, JsonNode> jsonNodesById(JsonNode values, String property) {
    Map<String, JsonNode> result = new HashMap<>();
    assertThat(values.isArray()).isTrue();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          String id = value.path(property).asText();
          assertThat(id).isNotBlank();
          assertThat(result.put(id, value)).isNull();
        });
    return Map.copyOf(result);
  }

  private static byte[] canonicalBytes(CanonicalJsonCodec codec, JsonNode value) {
    return codec.encodeCanonical(value).copyToByteArray();
  }

  private static JsonNode toArray(List<JsonNode> values) {
    com.fasterxml.jackson.databind.node.ArrayNode array =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    values.forEach(array::add);
    return array;
  }

  private static JsonNode withoutPublicationMetadata(JsonNode value) {
    ObjectNode copy = (ObjectNode) value.deepCopy();
    copy.remove("schemaVersion");
    copy.remove("artifactType");
    return copy;
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
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class,
                  org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader.class)
              .newInstance(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader());
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
        reference("flow-profile", "business-flows-publication"), 16, 8, 64, 96, 32, 64, 256);
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
