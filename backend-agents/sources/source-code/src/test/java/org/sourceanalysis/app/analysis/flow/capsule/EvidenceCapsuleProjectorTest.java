package org.sourceanalysis.app.analysis.flow.capsule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
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
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M2 public seam: one persisted Flow is projected into one closed, same-flow evidence capsule. */
class EvidenceCapsuleProjectorTest {

  private static final String PROJECTOR_CLASS =
      "org.sourceanalysis.app.analysis.flow.capsule.EvidenceCapsuleProjector";
  private static final String PROFILE_CLASS =
      "org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile";

  @TempDir Path temporaryDirectory;

  @Test
  void allowsAReadableFlowCapsuleWhenTheTechnicalContextHasNoAdmittedFact() {
    CapsuleProjection.EvidenceCapsule capsule =
        new CapsuleProjection.EvidenceCapsule(
            "capsule:context-without-fact",
            "flow:context-without-fact",
            "proof-pack:context-without-fact",
            "ELIGIBLE",
            List.of(),
            new CapsuleProjection.FlowEntryView(
                "entry:context-without-fact",
                "HTTP POST /context-without-fact",
                "node:entry",
                List.of("evidence:entry")),
            unavailableContext(
                "entry:context-without-fact",
                "flow:context-without-fact",
                "HTTP POST /context-without-fact"),
            List.of(),
            List.of(),
            List.of(
                new CapsuleProjection.FlowOutcomePathView(
                    "outcome:return",
                    List.of(),
                    "node:return",
                    "ENTRY_RETURN_TERMINAL",
                    List.of(),
                    List.of(),
                    List.of())),
            List.of(),
            List.of("span:return"),
            List.of("obligation:return"),
            new CapsuleProjection.BudgetUsage(1, 42));

    assertThat(capsule.factViews()).isEmpty();
    assertThat(capsule.entryContext().entryId()).isEqualTo("entry:context-without-fact");
  }

  private static FlowCompilation.EntryContext unavailableContext(
      String entryId, String flowId, String trigger) {
    return new FlowCompilation.EntryContext(
        "entry-context:" + entryId,
        entryId,
        flowId,
        trigger,
        "NOT_COLLECTED",
        "JAVA_CODE_CONTEXT_NOT_AVAILABLE_ON_STRICT_GRAPH_PATH",
        null,
        new FlowCompilation.StrictTechnicalContext(
            "example.Controller#entry()", List.of(), List.of(), List.of(), List.of()),
        List.of(),
        List.of(),
        List.of("JAVA_CODE_CONTEXT_NOT_AVAILABLE_ON_STRICT_GRAPH_PATH"));
  }

  @Test
  void projectsOneSameFlowEvidenceCapsuleForEveryPersistedCompiledFlow() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-projector"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      Object projection = project(fixture, facts, compiled, profile(24_576));
      List<?> capsules = listProperty(projection, "capsules");
      List<?> spans = listProperty(projection, "modelEvidenceSpans");
      List<?> obligations = listProperty(projection, "projectionObligations");

      assertThat(capsules).hasSize(compilation.flowSlices().size());
      assertThat(spans).isNotEmpty();
      assertThat(obligations).isNotEmpty();
      assertThat(strings(capsules, "flowSliceId").stream().collect(Collectors.toUnmodifiableSet()))
          .isEqualTo(
              compilation.flowSlices().stream()
                  .map(FlowCompilation.FlowSlice::flowSliceId)
                  .collect(Collectors.toUnmodifiableSet()));
      assertThat(strings(capsules, "evidenceCapsuleId")).doesNotHaveDuplicates();
      assertThat(strings(capsules, "modelEligibility")).containsOnly("ELIGIBLE");

      Map<String, FlowCompilation.FlowSlice> flowsById =
          compilation.flowSlices().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      FlowCompilation.FlowSlice::flowSliceId, flow -> flow));
      for (Object capsule : capsules) {
        FlowCompilation.FlowSlice flow = flowsById.get(property(capsule, "flowSliceId"));
        assertThat(flow).isNotNull();
        assertThat(strings(listProperty(capsule, "factViews"), "factId"))
            .containsExactlyElementsOf(flow.factIds());
        assertThat(strings(listProperty(capsule, "outcomePathViews"), "outcomePathId"))
            .containsExactlyElementsOf(
                flow.outcomePaths().stream()
                    .map(FlowCompilation.OutcomePath::outcomePathId)
                    .toList());
        assertThat(listProperty(capsule, "modelEvidenceSpanIds")).isNotEmpty();
        assertThat(listProperty(capsule, "projectionObligationIds")).isNotEmpty();
        assertThat(listProperty(capsule, "gapViews")).isNotNull();
        assertThat(listProperty(capsule, "processJoinSignals")).isNotEmpty();
      }
    }
  }

  @Test
  void projectsSharedSourceEvidenceWithFlowRootedSpanIdentityAndClosure() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("capsule-projector-shared-source"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      Map<String, FlowCompilation.FlowSlice> flowsById =
          compilation.flowSlices().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      FlowCompilation.FlowSlice::flowSliceId, flow -> flow));
      assertThat(flowsById).hasSize(2);
      List<String> flowIds = flowsById.keySet().stream().sorted().toList();
      assertThat(flowIds).hasSize(2).doesNotHaveDuplicates();

      var reopenedCompilation = fixture.moduleArtifacts().reopen(compiled);
      assertThat(reopenedCompilation.payloads()).hasSize(1);
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      JsonNode compilationEnvelope =
          canonicalJson.parseCanonical(reopenedCompilation.payloads().get(0).canonicalUtf8());
      assertThat(requiredText(compilationEnvelope, "schemaVersion"))
          .isEqualTo("business-flows-flow-compilation-v5");
      JsonNode persistedFlows =
          requiredArray(requiredObject(compilationEnvelope, "payload"), "flowSlices");
      assertThat(persistedFlows.size()).isEqualTo(2);
      Map<String, JsonNode> persistedFlowsById = jsonObjectsById(persistedFlows, "flowSliceId");
      assertThat(persistedFlowsById.keySet()).containsExactlyInAnyOrderElementsOf(flowIds);

      var reopenedFacts = fixture.stepArtifacts().reopen(facts.publication());
      JsonNode provenFacts =
          payload(canonicalJson, reopenedFacts.semanticPayloads(), "proven-facts.json");
      JsonNode proofPack =
          payload(canonicalJson, reopenedFacts.semanticPayloads(), "proof-pack.json");
      assertThat(requiredText(provenFacts, "schemaVersion"))
          .isEqualTo("proven-code-facts-proven-facts-v3");
      assertThat(requiredText(proofPack, "schemaVersion"))
          .isEqualTo("proven-code-facts-proof-pack-v3");
      Map<String, JsonNode> factsById =
          jsonObjectsById(requiredArray(provenFacts, "codeFacts"), "factId");
      Map<String, JsonNode> proofsById =
          jsonObjectsById(requiredArray(proofPack, "atomProofs"), "proofId");

      var reopenedGraphs = fixture.stepArtifacts().reopen(fixture.programGraphs().publication());
      JsonNode evidenceGraph =
          payload(canonicalJson, reopenedGraphs.semanticPayloads(), "evidence-graph.json");
      JsonNode evidenceNodes = requiredArray(evidenceGraph, "nodes");
      Map<String, JsonNode> evidenceById = jsonObjectsById(evidenceNodes, "evidenceNodeId");

      Map<String, Set<String>> evidenceIdsByFlow =
          persistedFlowsById.entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      Map.Entry::getKey,
                      entry -> evidenceIdsForFlow(entry.getValue(), factsById, proofsById)));
      Set<String> firstFlowFacts =
          new HashSet<>(jsonTextArray(persistedFlowsById.get(flowIds.get(0)), "factIds"));
      Set<String> secondFlowFacts =
          new HashSet<>(jsonTextArray(persistedFlowsById.get(flowIds.get(1)), "factIds"));
      assertThat(firstFlowFacts).isNotEmpty();
      assertThat(secondFlowFacts).isNotEmpty();
      assertThat(firstFlowFacts).doesNotContainAnyElementsOf(secondFlowFacts);
      for (String flowId : flowIds) {
        JsonNode persistedFlow = persistedFlowsById.get(flowId);
        FlowCompilation.FlowSlice flow = flowsById.get(flowId);
        assertThat(jsonTextArray(persistedFlow, "factIds"))
            .containsExactlyElementsOf(flow.factIds());
        assertFlowOwnsPersistedFacts(persistedFlow, factsById);
      }

      Set<String> sharedEvidenceIds = new HashSet<>(evidenceIdsByFlow.get(flowIds.get(0)));
      sharedEvidenceIds.retainAll(evidenceIdsByFlow.get(flowIds.get(1)));
      List<String> sharedSourceEvidenceIds =
          sharedEvidenceIds.stream()
              .filter(
                  evidenceId -> {
                    JsonNode evidence = evidenceById.get(evidenceId);
                    assertThat(evidence).isNotNull();
                    return evidence.get("sourceExcerpt") != null
                        && evidence.get("sourceExcerpt").isObject();
                  })
              .sorted()
              .toList();
      assertThat(sharedSourceEvidenceIds).isNotEmpty();
      for (String evidenceId : sharedSourceEvidenceIds) {
        JsonNode source = requiredObject(evidenceById.get(evidenceId), "sourceExcerpt");
        assertThat(requiredObject(source, "locator").isObject()).isTrue();
        assertThat(requiredText(source, "rawUtf8")).isNotNull();
        assertThat(requiredText(source, "rawUtf8Sha256")).hasSize(64);
      }

      // All two-Flow and shared source Evidence premises are independently proven before M2.
      CapsuleProjection projection =
          (CapsuleProjection) project(fixture, facts, compiled, profile(24_576));
      assertThat(projection.capsules()).hasSize(2);
      assertThat(projection.capsules())
          .allSatisfy(capsule -> assertThat(capsule.modelEligibility()).isEqualTo("ELIGIBLE"));
      Map<String, CapsuleProjection.EvidenceCapsule> capsulesByFlow =
          projection.capsules().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      CapsuleProjection.EvidenceCapsule::flowSliceId, capsule -> capsule));
      Map<String, CapsuleProjection.ModelEvidenceSpan> spansById =
          projection.modelEvidenceSpans().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      CapsuleProjection.ModelEvidenceSpan::spanId, span -> span));
      Map<String, CapsuleProjection.ProjectionObligation> obligationsById =
          projection.projectionObligations().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      CapsuleProjection.ProjectionObligation::obligationId,
                      obligation -> obligation));
      assertThat(capsulesByFlow.keySet()).containsExactlyInAnyOrderElementsOf(flowIds);

      Set<String> referencedSpanIds = new HashSet<>();
      Set<String> referencedObligationIds = new HashSet<>();
      for (String flowId : flowIds) {
        FlowCompilation.FlowSlice flow = flowsById.get(flowId);
        CapsuleProjection.EvidenceCapsule capsule = capsulesByFlow.get(flowId);
        assertThat(
                capsule.factViews().stream().map(CapsuleProjection.FlowFactView::factId).toList())
            .containsExactlyElementsOf(flow.factIds());
        assertThat(
                capsule.factViews().stream()
                    .flatMap(fact -> fact.atoms().stream())
                    .map(CapsuleProjection.FlowAtomView::atomId)
                    .toList())
            .containsExactlyInAnyOrderElementsOf(flow.atomIds());
        assertThat(
                capsule.outcomePathViews().stream()
                    .map(CapsuleProjection.FlowOutcomePathView::outcomePathId)
                    .toList())
            .containsExactlyElementsOf(
                flow.outcomePaths().stream()
                    .map(FlowCompilation.OutcomePath::outcomePathId)
                    .toList());
        assertThat(capsule.processJoinSignals())
            .containsExactlyElementsOf(flow.processJoinSignals());

        Set<String> capsuleSpanIds = new HashSet<>(capsule.modelEvidenceSpanIds());
        assertThat(capsuleSpanIds).hasSize(capsule.modelEvidenceSpanIds().size());
        assertThat(referencedSpanIds.addAll(capsuleSpanIds)).isTrue();
        for (String spanId : capsuleSpanIds) {
          CapsuleProjection.ModelEvidenceSpan span = spansById.get(spanId);
          assertThat(span).isNotNull();
          assertThat(span.supportedAtomIds()).allMatch(flow.atomIds()::contains);
          assertThat(span.supportedOutcomePathIds())
              .allMatch(
                  flow.outcomePaths().stream()
                          .map(FlowCompilation.OutcomePath::outcomePathId)
                          .collect(Collectors.toSet())
                      ::contains);
          assertThat(span.supportedProcessJoinSignalIds())
              .allMatch(
                  flow.processJoinSignals().stream()
                          .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
                          .collect(Collectors.toSet())
                      ::contains);
        }

        Set<String> obligatedAtomIds = new HashSet<>();
        Set<String> obligatedOutcomeIds = new HashSet<>();
        Set<String> obligatedSignalIds = new HashSet<>();
        for (String obligationId : capsule.projectionObligationIds()) {
          assertThat(referencedObligationIds.add(obligationId)).isTrue();
          CapsuleProjection.ProjectionObligation obligation = obligationsById.get(obligationId);
          assertThat(obligation).isNotNull();
          assertThat(obligation.satisfyingSpanIds()).isNotEmpty();
          assertThat(obligation.satisfyingSpanIds()).allMatch(capsuleSpanIds::contains);
          switch (obligation.kind()) {
            case "ATOM_DIRECT_SEMANTICS" -> obligatedAtomIds.add(obligation.semanticItemId());
            case "OUTCOME_TERMINAL" -> obligatedOutcomeIds.add(obligation.semanticItemId());
            case "PROCESS_JOIN_SIGNAL_BASIS" -> obligatedSignalIds.add(obligation.semanticItemId());
            default -> throw new AssertionError("unexpected projection obligation kind");
          }
        }
        assertThat(obligatedAtomIds).containsExactlyInAnyOrderElementsOf(flow.atomIds());
        assertThat(obligatedOutcomeIds)
            .containsExactlyInAnyOrderElementsOf(
                flow.outcomePaths().stream()
                    .map(FlowCompilation.OutcomePath::outcomePathId)
                    .toList());
        assertThat(obligatedSignalIds)
            .containsExactlyInAnyOrderElementsOf(
                flow.processJoinSignals().stream()
                    .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
                    .toList());
      }
      assertThat(referencedSpanIds)
          .containsExactlyInAnyOrderElementsOf(
              projection.modelEvidenceSpans().stream()
                  .map(CapsuleProjection.ModelEvidenceSpan::spanId)
                  .toList());
      assertThat(referencedObligationIds)
          .containsExactlyInAnyOrderElementsOf(
              projection.projectionObligations().stream()
                  .map(CapsuleProjection.ProjectionObligation::obligationId)
                  .toList());

      List<String> expectedSharedSpanIds =
          sharedSourceEvidenceIds.stream()
              .flatMap(
                  evidenceId ->
                      flowIds.stream().map(flowId -> expectedSharedSpanId(flowId, evidenceId)))
              .toList();
      assertThat(expectedSharedSpanIds).doesNotHaveDuplicates();
      for (String evidenceId : sharedSourceEvidenceIds) {
        JsonNode source = requiredObject(evidenceById.get(evidenceId), "sourceExcerpt");
        for (String flowId : flowIds) {
          String spanId = expectedSharedSpanId(flowId, evidenceId);
          CapsuleProjection.ModelEvidenceSpan span = spansById.get(spanId);
          assertThat(span).isNotNull();
          assertThat(capsulesByFlow.get(flowId).modelEvidenceSpanIds()).contains(spanId);
          assertThat(
                  projection.capsules().stream()
                      .filter(capsule -> capsule.modelEvidenceSpanIds().contains(spanId))
                      .toList())
              .hasSize(1)
              .extracting(CapsuleProjection.EvidenceCapsule::flowSliceId)
              .containsExactly(flowId);
          assertSourceExcerptEquals(span.sourceExcerpt(), source);
        }
      }
    }
  }

  @Test
  void projectsFullProcessJoinSignalsWithSameFlowBasisAndIdentityBinding() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-projector-process-signals"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      // Prove the projector's persisted M1 input independently before invoking M2.
      var reopened = fixture.moduleArtifacts().reopen(compiled);
      assertThat(reopened.payloads()).hasSize(1);
      JsonNode envelope =
          new CanonicalJsonCodec().parseCanonical(reopened.payloads().get(0).canonicalUtf8());
      assertThat(envelope.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-compilation-v5");
      JsonNode persistedFlows = envelope.path("payload").path("flowSlices");
      assertThat(persistedFlows.isArray()).isTrue();
      assertThat(persistedFlows.size()).isEqualTo(2);
      Map<String, FlowCompilation.FlowSlice> flowsById =
          compilation.flowSlices().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      FlowCompilation.FlowSlice::flowSliceId, flow -> flow));
      for (JsonNode persistedFlow : persistedFlows) {
        String flowSliceId = persistedFlow.path("flowSliceId").asText();
        FlowCompilation.FlowSlice flow = flowsById.get(flowSliceId);
        assertThat(flow).isNotNull();
        assertThat(flow.processJoinSignals()).hasSize(4);
        assertThat(
                flow.processJoinSignals().stream()
                    .map(FlowCompilation.ProcessJoinSignalV1::signalKind)
                    .toList())
            .containsExactlyInAnyOrder(
                "EXPLICIT_CALL", "EXPLICIT_CALL", "JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP");
        JsonNode persistedSignals = persistedFlow.path("processJoinSignals");
        assertThat(persistedSignals.isArray()).isTrue();
        assertThat(persistedSignals.size()).isEqualTo(4);
        assertThat(jsonStrings(persistedSignals, "signalKind"))
            .containsExactlyInAnyOrder(
                "EXPLICIT_CALL", "EXPLICIT_CALL", "JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP");
        assertThat(jsonStrings(persistedSignals, "processJoinSignalId"))
            .containsExactlyElementsOf(
                flow.processJoinSignals().stream()
                    .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
                    .toList());
        for (JsonNode signal : persistedSignals) {
          assertThat(signal.size()).isEqualTo(14);
          assertThat(signal.path("flowSliceId").asText()).isEqualTo(flowSliceId);
        }
      }

      CapsuleProjection projection =
          (CapsuleProjection) project(fixture, facts, compiled, profile(24_576));
      List<?> capsules = listProperty(projection, "capsules");
      List<?> spans = listProperty(projection, "modelEvidenceSpans");
      List<?> obligations = listProperty(projection, "projectionObligations");
      Map<String, Object> spansById =
          spans.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      span -> property(span, "spanId").toString(), span -> span));
      Map<String, Object> obligationsById =
          obligations.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      obligation -> property(obligation, "obligationId").toString(),
                      obligation -> obligation));

      assertThat(capsules).hasSize(2);
      for (Object capsule : capsules) {
        FlowCompilation.FlowSlice flow = flowsById.get(property(capsule, "flowSliceId").toString());
        assertThat(flow).isNotNull();
        List<?> capsuleSignals = listProperty(capsule, "processJoinSignals");
        assertThat(capsuleSignals.toArray()).containsExactly(flow.processJoinSignals().toArray());
        assertThat(capsuleSignals).isNotEmpty();
        List<String> signalIds = strings(capsuleSignals, "processJoinSignalId");
        List<String> capsuleSpanIds = values(listProperty(capsule, "modelEvidenceSpanIds"));
        List<Object> capsuleSpans = capsuleSpanIds.stream().map(spansById::get).toList();
        List<String> capsuleObligationIds =
            values(listProperty(capsule, "projectionObligationIds"));
        List<Object> capsuleObligations =
            capsuleObligationIds.stream().map(obligationsById::get).toList();

        for (FlowCompilation.ProcessJoinSignalV1 signal : flow.processJoinSignals()) {
          List<Object> sameLocatorSpans =
              capsuleSpans.stream()
                  .filter(
                      span ->
                          signal
                              .sourceLocators()
                              .contains(property(property(span, "sourceExcerpt"), "locator")))
                  .toList();
          assertThat(sameLocatorSpans).isNotEmpty();
          assertThat(sameLocatorSpans)
              .allSatisfy(
                  span ->
                      assertThat(values(listProperty(span, "supportedProcessJoinSignalIds")))
                          .contains(signal.processJoinSignalId()));

          List<Object> signalObligations =
              capsuleObligations.stream()
                  .filter(
                      obligation ->
                          "PROCESS_JOIN_SIGNAL_BASIS".equals(property(obligation, "kind"))
                              && signal
                                  .processJoinSignalId()
                                  .equals(property(obligation, "semanticItemId")))
                  .toList();
          assertThat(signalObligations).hasSize(1);
          List<String> satisfyingSpanIds =
              values(listProperty(signalObligations.get(0), "satisfyingSpanIds"));
          assertThat(satisfyingSpanIds)
              .containsExactlyElementsOf(
                  sameLocatorSpans.stream()
                      .map(span -> property(span, "spanId").toString())
                      .toList());
        }

        List<String> obligationSignalIds =
            capsuleObligations.stream()
                .filter(
                    obligation -> "PROCESS_JOIN_SIGNAL_BASIS".equals(property(obligation, "kind")))
                .map(obligation -> property(obligation, "semanticItemId").toString())
                .toList();
        assertThat(obligationSignalIds.stream().sorted().toList())
            .containsExactlyElementsOf(signalIds.stream().sorted().toList());
        for (Object span : capsuleSpans) {
          Object locator = property(property(span, "sourceExcerpt"), "locator");
          List<String> expectedSupportIds =
              flow.processJoinSignals().stream()
                  .filter(signal -> signal.sourceLocators().contains(locator))
                  .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
                  .sorted()
                  .toList();
          assertThat(values(listProperty(span, "supportedProcessJoinSignalIds")))
              .containsExactlyElementsOf(expectedSupportIds);
        }
        List<String> spanSignalIds =
            capsuleSpans.stream()
                .flatMap(
                    span -> values(listProperty(span, "supportedProcessJoinSignalIds")).stream())
                .distinct()
                .toList();
        assertThat(spanSignalIds).containsExactlyInAnyOrderElementsOf(signalIds);

        String expectedCapsuleId =
            contentId(
                "evidence-capsule",
                flow.flowSliceId(),
                projection.proofPackRef().artifactId().value(),
                projection.proofPackRef().sha256().value(),
                String.join("\u0000", signalIds),
                String.join("\u0000", capsuleSpanIds),
                String.join("\u0000", capsuleObligationIds));
        assertThat(property(capsule, "evidenceCapsuleId")).isEqualTo(expectedCapsuleId);
      }
    }
  }

  @Test
  void projectsZeroCapsulesForPersistedZeroFlowCompilationWithCompleteGapAccounting()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(
            temporaryDirectory.resolve("capsule-projector-zero-flow"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(),
                  fixture.programGraphs(),
                  facts,
                  zeroFlowProfile());
      assertThat(compilation.entryDispositions()).hasSize(2);
      assertThat(compilation.flowSlices()).isEmpty();
      assertThat(compilation.flowGaps()).hasSizeGreaterThanOrEqualTo(2);

      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      var reopenedBefore = fixture.moduleArtifacts().reopen(compiled);
      assertThat(reopenedBefore.payloads()).hasSize(1);
      byte[] m1BytesBefore = reopenedBefore.payloads().get(0).canonicalUtf8().copyToByteArray();
      JsonNode envelope =
          new CanonicalJsonCodec().parseCanonical(reopenedBefore.payloads().get(0).canonicalUtf8());
      assertThat(envelope.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-compilation-v5");
      JsonNode payload = envelope.path("payload");
      assertThat(payload.path("flowCompilationProfile").path("maxFlowNodes").asInt()).isEqualTo(1);

      JsonNode persistedDispositions = payload.path("entryDispositions");
      JsonNode persistedFlows = payload.path("flowSlices");
      JsonNode persistedGaps = payload.path("flowGaps");
      assertThat(persistedDispositions.isArray()).isTrue();
      assertThat(persistedDispositions).hasSize(2);
      assertThat(jsonStrings(persistedDispositions, "entryId"))
          .containsExactlyInAnyOrder("entry:" + digest("approve"), "entry:" + digest("cancel"));
      assertThat(jsonStrings(persistedDispositions, "disposition")).containsOnly("GAP");
      assertThat(persistedFlows.isArray()).isTrue();
      assertThat(persistedFlows).isEmpty();
      assertThat(persistedGaps.isArray()).isTrue();
      assertThat(persistedGaps.size()).isGreaterThanOrEqualTo(2);

      Set<String> persistedGapIds = new HashSet<>(jsonStrings(persistedGaps, "gapId"));
      for (JsonNode disposition : persistedDispositions) {
        assertThat(disposition.path("flowSliceId").isNull()).isTrue();
        assertThat(disposition.path("reasonCode").asText()).isNotBlank();
        JsonNode gapIds = disposition.path("gapIds");
        assertThat(gapIds.isArray()).isTrue();
        assertThat(gapIds).isNotEmpty();
        for (JsonNode gapId : gapIds) {
          assertThat(persistedGapIds).contains(gapId.asText());
        }
      }
      for (JsonNode gap : persistedGaps) {
        assertThat(gap.path("gapId").asText()).isNotBlank();
        assertThat(gap.path("reasonCode").asText()).isNotBlank();
        assertThat(gap.path("affectedSemanticIds")).isNotEmpty();
      }
      JsonNode persistedGapLedger =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture.stepArtifacts().reopen(facts.publication()).semanticPayloads().stream()
                      .filter(
                          payloadValue ->
                              "gap-ledger.json".equals(payloadValue.descriptor().fileName()))
                      .findFirst()
                      .orElseThrow()
                      .canonicalUtf8());
      Set<String> externalGapIds =
          java.util.stream.StreamSupport.stream(
                  persistedGapLedger.path("gaps").spliterator(), false)
              .filter(gap -> "DATA_FLOW_BINDING_UNPROVEN".equals(gap.path("code").asText()))
              .map(gap -> gap.path("gapId").asText())
              .collect(Collectors.toSet());
      assertThat(persistedGapIds).containsAll(externalGapIds);
      assertThat(persistedGaps).hasSize(externalGapIds.size() + 2);

      var proofPackPayload =
          fixture.stepArtifacts().reopen(facts.publication()).semanticPayloads().stream()
              .filter(
                  payloadValue -> "proof-pack.json".equals(payloadValue.descriptor().fileName()))
              .findFirst()
              .orElseThrow();
      ArtifactReference flowCompilationRef = artifactReference(reopenedBefore.payloads().get(0));
      ArtifactReference proofPackRef = artifactReference(proofPackPayload);

      CapsuleProjection projection =
          (CapsuleProjection) project(fixture, facts, compiled, profile(24_576));
      assertThat(projection.capsules()).isEmpty();
      assertThat(projection.modelEvidenceSpans()).isEmpty();
      assertThat(projection.projectionObligations()).isEmpty();
      assertThat(projection.flowCompilationRef()).isEqualTo(flowCompilationRef);
      assertThat(projection.proofPackRef()).isEqualTo(proofPackRef);

      var reopenedAfter = fixture.moduleArtifacts().reopen(compiled);
      assertThat(reopenedAfter.payloads()).hasSize(1);
      assertThat(reopenedAfter.payloads().get(0).canonicalUtf8().copyToByteArray())
          .containsExactly(m1BytesBefore);
    }
  }

  @Test
  void retainsAnOverBudgetFlowAsModelIneligibleInsteadOfDiscardingItsEvidence() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-projector-budget"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      Object projection = project(fixture, facts, compiled, profile(1));
      List<?> capsules = listProperty(projection, "capsules");

      assertThat(capsules).hasSize(compilation.flowSlices().size());
      assertThat(strings(capsules, "modelEligibility")).containsOnly("INELIGIBLE");
      assertThat(capsules)
          .allSatisfy(
              capsule -> {
                assertThat(listProperty(capsule, "modelIneligibilityGapIds")).isNotEmpty();
                assertThat(listProperty(capsule, "modelEvidenceSpanIds")).isNotEmpty();
              });
    }
  }

  @Test
  void assignsEveryProjectedEvidenceSpanAndObligationToExactlyOneFlowCapsule() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-projector-flow-local-evidence"))) {
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      FlowCompilation compilation =
          new EntryRootedFlowCompiler(fixture.stepArtifacts())
              .compile(
                  fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
      ModulePublicationReference compiled =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);

      Object projection = project(fixture, facts, compiled, profile(24_576));
      List<?> capsules = listProperty(projection, "capsules");
      Map<String, Object> obligationsById =
          listProperty(projection, "projectionObligations").stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      obligation -> property(obligation, "obligationId").toString(),
                      obligation -> obligation));
      Set<String> referencedSpanIds = new HashSet<>();
      Set<String> referencedObligationIds = new HashSet<>();
      for (Object capsule : capsules) {
        List<String> capsuleSpanIds = values(listProperty(capsule, "modelEvidenceSpanIds"));
        for (String spanId : capsuleSpanIds) {
          assertThat(referencedSpanIds.add(spanId)).as("span id %s", spanId).isTrue();
        }
        for (String obligationId : values(listProperty(capsule, "projectionObligationIds"))) {
          assertThat(referencedObligationIds.add(obligationId))
              .as("obligation id %s", obligationId)
              .isTrue();
          assertThat(values(listProperty(obligationsById.get(obligationId), "satisfyingSpanIds")))
              .allMatch(capsuleSpanIds::contains);
        }
      }

      assertThat(referencedSpanIds)
          .containsExactlyInAnyOrderElementsOf(
              strings(listProperty(projection, "modelEvidenceSpans"), "spanId"));
      assertThat(referencedObligationIds)
          .containsExactlyInAnyOrderElementsOf(
              strings(listProperty(projection, "projectionObligations"), "obligationId"));
    }
  }

  private Object project(
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      ModulePublicationReference compilation,
      CapsuleProjectionProfile profile)
      throws Exception {
    try {
      Class<?> profileType = Class.forName(PROFILE_CLASS);
      Object reflectedProfile =
          profileType
              .getConstructor(ArtifactReference.class, int.class, int.class, int.class, int.class)
              .newInstance(
                  profile.profileRef(),
                  profile.maxCapsules(),
                  profile.maxSpansPerCapsule(),
                  profile.maxSpanBytes(),
                  profile.maxCapsuleUtf8Bytes());
      Class<?> projectorType = Class.forName(PROJECTOR_CLASS);
      Object projector =
          projectorType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class,
                  org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader.class)
              .newInstance(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader());
      return projectorType
          .getMethod(
              "project",
              ModulePublicationReference.class,
              org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              profileType)
          .invoke(
              projector,
              compilation,
              fixture.sourceInventory(),
              fixture.programGraphs(),
              facts,
              reflectedProfile);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("EVIDENCE_CAPSULE_PROJECTOR_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("EVIDENCE_CAPSULE_PROJECTOR_FAILED", cause);
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

  private static JsonNode payload(
      CanonicalJsonCodec canonicalJson,
      List<org.sourceanalysis.app.artifact.VerifiedCanonicalPayload> payloads,
      String fileName) {
    return canonicalJson.parseCanonical(
        payloads.stream()
            .filter(payload -> fileName.equals(payload.descriptor().fileName()))
            .findFirst()
            .orElseThrow()
            .canonicalUtf8());
  }

  private static Map<String, JsonNode> jsonObjectsById(JsonNode values, String idField) {
    assertThat(values.isArray()).isTrue();
    Map<String, JsonNode> result = new java.util.HashMap<>();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          String id = requiredText(value, idField);
          assertThat(result.put(id, value)).isNull();
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

  private static Set<String> evidenceIdsForFlow(
      JsonNode flow, Map<String, JsonNode> factsById, Map<String, JsonNode> proofsById) {
    Set<String> result = new HashSet<>();
    for (String factId : jsonTextArray(flow, "factIds")) {
      JsonNode fact = factsById.get(factId);
      assertThat(fact).isNotNull();
      for (JsonNode atom : requiredArray(fact, "atoms")) {
        addProofEvidence(requiredText(atom, "proofId"), proofsById, result);
      }
    }
    for (JsonNode outcome : requiredArray(flow, "outcomePaths")) {
      for (String proofId : jsonTextArray(outcome, "requiredProofIds")) {
        addProofEvidence(proofId, proofsById, result);
      }
    }
    for (JsonNode signal : requiredArray(flow, "processJoinSignals")) {
      result.addAll(jsonTextArray(signal, "evidenceNodeIds"));
    }
    assertThat(result).isNotEmpty();
    return Set.copyOf(result);
  }

  private static void addProofEvidence(
      String proofId, Map<String, JsonNode> proofsById, Set<String> destination) {
    JsonNode proof = proofsById.get(proofId);
    assertThat(proof).isNotNull();
    assertThat(requiredText(proof, "status")).isEqualTo("CLOSED");
    destination.addAll(jsonTextArray(proof, "requiredEvidenceNodeIds"));
  }

  private static void assertFlowOwnsPersistedFacts(JsonNode flow, Map<String, JsonNode> factsById) {
    String entryId = requiredText(flow, "entryId");
    for (String factId : jsonTextArray(flow, "factIds")) {
      JsonNode fact = factsById.get(factId);
      assertThat(fact).isNotNull();
      String candidateKey = requiredText(fact, "candidateDenominatorKey");
      int separator = candidateKey.indexOf('|');
      assertThat(separator).isGreaterThan(0);
      assertThat(candidateKey.substring(0, separator)).isEqualTo(entryId);
    }
  }

  private static String expectedSharedSpanId(String flowSliceId, String evidenceNodeId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame("business-flows-model-evidence-span-id-v2"));
      digest.update(frame(flowSliceId));
      digest.update(frame(evidenceNodeId));
      return "model-evidence-span:" + java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static void assertSourceExcerptEquals(
      org.sourceanalysis.app.evidence.SourceExcerptV1 actual, JsonNode expected) {
    JsonNode locator = requiredObject(expected, "locator");
    assertThat(actual.locator().fileId().value()).isEqualTo(requiredText(locator, "fileId"));
    assertThat(actual.locator().path()).isEqualTo(requiredText(locator, "path"));
    assertThat(actual.locator().startByte()).isEqualTo(locator.get("startByte").longValue());
    assertThat(actual.locator().endByteExclusive())
        .isEqualTo(locator.get("endByteExclusive").longValue());
    assertThat(actual.locator().startLine()).isEqualTo(locator.get("startLine").intValue());
    assertThat(actual.locator().startColumn()).isEqualTo(locator.get("startColumn").intValue());
    assertThat(actual.locator().endLine()).isEqualTo(locator.get("endLine").intValue());
    assertThat(actual.locator().endColumn()).isEqualTo(locator.get("endColumn").intValue());
    assertThat(actual.rawUtf8().copyToByteArray())
        .containsExactly(requiredText(expected, "rawUtf8").getBytes(StandardCharsets.UTF_8));
    assertThat(actual.rawUtf8Sha256().value()).isEqualTo(requiredText(expected, "rawUtf8Sha256"));
  }

  private static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        new ArtifactReference(
            ArtifactId.parse("flow-profile:" + digest("capsule-flow-profile")),
            new Sha256Digest(digest("capsule-flow-profile-bytes"))),
        16,
        8,
        64,
        96,
        32,
        64,
        256);
  }

  private static FlowCompilationProfile zeroFlowProfile() {
    return new FlowCompilationProfile(
        new ArtifactReference(
            ArtifactId.parse("flow-profile:" + digest("capsule-zero-flow-profile")),
            new Sha256Digest(digest("capsule-zero-flow-profile-bytes"))),
        16,
        8,
        1,
        96,
        32,
        64,
        256);
  }

  private static CapsuleProjectionProfile profile(int maxCapsuleUtf8Bytes) {
    return new CapsuleProjectionProfile(
        new ArtifactReference(
            ArtifactId.parse("capsule-profile:" + digest("closed-same-flow-capsule")),
            new Sha256Digest(digest("closed-same-flow-capsule-bytes"))),
        16,
        32,
        4_096,
        maxCapsuleUtf8Bytes);
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
    Object value = property(target, property);
    if (!(value instanceof List<?> values)) {
      throw new AssertionError("CAPSULE_PROJECTION_SHAPE_INVALID");
    }
    return values;
  }

  private static List<String> strings(List<?> values, String property) {
    return values.stream().map(value -> property(value, property).toString()).toList();
  }

  private static List<String> jsonStrings(JsonNode values, String property) {
    assertThat(values.isArray()).isTrue();
    List<String> result = new java.util.ArrayList<>();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          result.add(requiredText(value, property));
        });
    return result;
  }

  private static List<String> jsonTextArray(JsonNode value, String field) {
    JsonNode values = requiredArray(value, field);
    List<String> result = new java.util.ArrayList<>();
    values.forEach(
        item -> {
          assertThat(item.isTextual()).isTrue();
          assertThat(item.textValue()).isNotBlank();
          result.add(item.textValue());
        });
    return result;
  }

  private static ArtifactReference artifactReference(
      org.sourceanalysis.app.artifact.VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static List<String> values(List<?> values) {
    return values.stream().map(Object::toString).toList();
  }

  private static Object property(Object target, String property) {
    try {
      return target.getClass().getMethod(property).invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("CAPSULE_PROJECTION_SHAPE_INVALID", failure);
    }
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

  private static String contentId(String prefix, String... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame(prefix));
      for (String value : values) digest.update(frame(value));
      return prefix + ":" + java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return java.nio.ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(java.nio.ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }
}
