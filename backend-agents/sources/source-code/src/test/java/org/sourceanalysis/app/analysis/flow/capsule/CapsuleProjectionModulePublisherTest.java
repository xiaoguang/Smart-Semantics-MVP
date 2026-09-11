package org.sourceanalysis.app.analysis.flow.capsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
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
              .project(
                  flows,
                  fixture.sourceInventory(),
                  fixture.programGraphs(),
                  facts,
                  capsuleProfile());

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
          .isEqualTo("business-flows-capsule-projection-v7");
      assertThat(reopened.receipt().upstreamArtifacts()).hasSize(14);
    }
  }

  @Test
  void publishesFreshReopenedSignalsAndExactCapsuleBasisClosure() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("capsule-publisher-process-signals"))) {
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

      ModulePublicationReference reference = publish(fixture, facts, flows, projection);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      JsonNode m1Envelope =
          canonicalJson.parseCanonical(
              fixture.moduleArtifacts().reopen(flows).payloads().get(0).canonicalUtf8());
      JsonNode m1Payload = m1Envelope.path("payload");

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
          .isEqualTo("business-flows-capsule-projection-v7");
      assertThat(reopened.receipt().payloadArtifacts())
          .containsExactly(reopened.payloads().get(0).descriptor());
      assertThat(reopened.receipt().upstreamArtifacts()).hasSize(14);

      JsonNode m2Envelope =
          canonicalJson.parseCanonical(reopened.payloads().get(0).canonicalUtf8());
      assertThat(m2Envelope.path("schemaVersion").asText())
          .isEqualTo("business-flows-capsule-projection-v7");
      JsonNode m2Payload = m2Envelope.path("payload");
      Map<String, JsonNode> m1Flows = jsonNodesById(m1Payload.path("flowSlices"), "flowSliceId");
      Map<String, JsonNode> m2Capsules = jsonNodesById(m2Payload.path("capsules"), "flowSliceId");
      assertThat(m2Capsules).hasSize(2);
      for (Map.Entry<String, JsonNode> entry : m2Capsules.entrySet()) {
        JsonNode m1Signals = m1Flows.get(entry.getKey()).path("processJoinSignals");
        JsonNode m2Signals = entry.getValue().path("processJoinSignals");
        assertThat(canonicalJson.encodeCanonical(m2Signals).copyToByteArray())
            .containsExactly(canonicalJson.encodeCanonical(m1Signals).copyToByteArray());
      }

      Map<String, JsonNode> m2Spans = jsonNodesById(m2Payload.path("modelEvidenceSpans"), "spanId");
      for (CapsuleProjection.ModelEvidenceSpan span : projection.modelEvidenceSpans()) {
        JsonNode supportedSignalIds =
            m2Spans.get(span.spanId()).path("supportedProcessJoinSignalIds");
        assertThat(supportedSignalIds.isArray()).isTrue();
        assertThat(jsonStrings(supportedSignalIds))
            .containsExactlyElementsOf(span.supportedProcessJoinSignalIds());
      }

      Map<String, JsonNode> m2Obligations =
          jsonNodesById(m2Payload.path("projectionObligations"), "obligationId");
      Map<String, CapsuleProjection.ProjectionObligation> projectionObligationsById =
          new HashMap<>();
      for (CapsuleProjection.ProjectionObligation obligation : projection.projectionObligations()) {
        projectionObligationsById.put(obligation.obligationId(), obligation);
      }
      for (CapsuleProjection.EvidenceCapsule capsule : projection.capsules()) {
        JsonNode serializedCapsule = m2Capsules.get(capsule.flowSliceId());
        List<String> capsuleSpanIds = capsule.modelEvidenceSpanIds();
        List<String> capsuleObligationIds = capsule.projectionObligationIds();
        List<String> signalIds =
            capsule.processJoinSignals().stream()
                .map(FlowCompilation.ProcessJoinSignalV1::processJoinSignalId)
                .toList();
        assertThat(serializedCapsule.path("flowSliceId").asText()).isEqualTo(capsule.flowSliceId());
        assertThat(jsonStrings(serializedCapsule.path("projectionObligationIds")))
            .containsExactlyElementsOf(capsuleObligationIds);
        for (JsonNode signal : serializedCapsule.path("processJoinSignals")) {
          assertThat(signal.path("flowSliceId").asText()).isEqualTo(capsule.flowSliceId());
        }

        List<String> serializedBasisSignalIds = new ArrayList<>();
        for (String obligationId : capsuleObligationIds) {
          JsonNode obligation = m2Obligations.get(obligationId);
          CapsuleProjection.ProjectionObligation expected =
              projectionObligationsById.get(obligationId);
          assertThat(expected).isNotNull();
          assertThat(obligation.path("kind").asText()).isEqualTo(expected.kind());
          assertThat(obligation.path("semanticItemId").asText())
              .isEqualTo(expected.semanticItemId());
          assertThat(jsonStrings(obligation.path("satisfyingSpanIds")))
              .containsExactlyElementsOf(expected.satisfyingSpanIds());
          assertThat(expected.satisfyingSpanIds()).allMatch(capsuleSpanIds::contains);
          if ("PROCESS_JOIN_SIGNAL_BASIS".equals(obligation.path("kind").asText())) {
            serializedBasisSignalIds.add(obligation.path("semanticItemId").asText());
          }
        }
        assertThat(serializedBasisSignalIds).containsExactlyInAnyOrderElementsOf(signalIds);
      }
      assertThat(jsonStrings(m2Payload.path("capsules"), "evidenceCapsuleId"))
          .containsExactlyElementsOf(
              projection.capsules().stream()
                  .map(CapsuleProjection.EvidenceCapsule::evidenceCapsuleId)
                  .toList());
      assertThat(m2Payload.path("capsules")).hasSize(projection.capsules().size());
      assertThat(m2Obligations.keySet())
          .containsExactlyInAnyOrderElementsOf(
              projection.projectionObligations().stream()
                  .map(CapsuleProjection.ProjectionObligation::obligationId)
                  .toList());
    }
  }

  @Test
  void rejectsCrossFlowSpanOwnershipAndBareEvidenceNodeMutationsBeforeInstall() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("capsule-publisher-shared-source-rejection"))) {
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
                  capsuleProfile());

      assertThat(projection.capsules()).hasSize(2);
      Map<String, CapsuleProjection.EvidenceCapsule> capsulesByFlow =
          projection.capsules().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      CapsuleProjection.EvidenceCapsule::flowSliceId, capsule -> capsule));
      List<String> flowIds = capsulesByFlow.keySet().stream().sorted().toList();
      assertThat(flowIds).hasSize(2).doesNotHaveDuplicates();

      Set<String> sharedEvidenceNodeIds =
          new HashSet<>(evidenceNodeIds(capsulesByFlow.get(flowIds.get(0))));
      sharedEvidenceNodeIds.retainAll(evidenceNodeIds(capsulesByFlow.get(flowIds.get(1))));
      assertThat(sharedEvidenceNodeIds).isNotEmpty();
      Map<String, CapsuleProjection.ModelEvidenceSpan> spansById =
          projection.modelEvidenceSpans().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      CapsuleProjection.ModelEvidenceSpan::spanId, span -> span));
      String sharedEvidenceNodeId =
          sharedEvidenceNodeIds.stream()
              .sorted()
              .filter(
                  evidenceNodeId ->
                      spansById.containsKey(expectedSharedSpanId(flowIds.get(0), evidenceNodeId))
                          && spansById.containsKey(
                              expectedSharedSpanId(flowIds.get(1), evidenceNodeId)))
              .findFirst()
              .orElseThrow();
      String firstFlowSpanId = expectedSharedSpanId(flowIds.get(0), sharedEvidenceNodeId);
      String secondFlowSpanId = expectedSharedSpanId(flowIds.get(1), sharedEvidenceNodeId);
      assertThat(firstFlowSpanId).isNotEqualTo(secondFlowSpanId);
      CapsuleProjection.ModelEvidenceSpan firstFlowSpan = spansById.get(firstFlowSpanId);
      CapsuleProjection.ModelEvidenceSpan secondFlowSpan = spansById.get(secondFlowSpanId);
      assertThat(firstFlowSpan).isNotNull();
      assertThat(secondFlowSpan).isNotNull();
      assertThat(firstFlowSpan.sourceExcerpt()).isEqualTo(secondFlowSpan.sourceExcerpt());
      assertThat(capsulesByFlow.get(flowIds.get(0)).modelEvidenceSpanIds())
          .contains(firstFlowSpanId);
      assertThat(capsulesByFlow.get(flowIds.get(1)).modelEvidenceSpanIds())
          .contains(secondFlowSpanId);

      CapsuleProjection crossFlowMutation =
          moveFlowRootedSpanToOtherCapsule(
              projection, flowIds.get(0), flowIds.get(1), sharedEvidenceNodeId);
      CapsuleProjection bareEvidenceMutation =
          replaceFlowRootedSpanWithBareEvidenceNode(
              projection, flowIds.get(1), sharedEvidenceNodeId);
      CapsuleProjectionModulePublisher publisher =
          new CapsuleProjectionModulePublisher(
              fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader());

      assertThat(crossFlowMutation).isNotEqualTo(projection);
      assertThat(bareEvidenceMutation).isNotEqualTo(projection);
      assertRejected(publisher, fixture, facts, flows, crossFlowMutation);
      assertRejected(publisher, fixture, facts, flows, bareEvidenceMutation);

      ModulePublicationReference reference =
          publisher.publish(
              flows, fixture.sourceInventory(), fixture.programGraphs(), facts, projection);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(reference);
      assertThat(reopened.payloads()).hasSize(1);
      assertThat(reopened.payloads().get(0).descriptor().schemaVersion())
          .isEqualTo("business-flows-capsule-projection-v7");
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

      assertThat(reopened.receipt().status()).isEqualTo(ModuleCompletionStatus.SUCCEEDED_WITH_GAPS);
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
              .newInstance(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader());
      Method publish =
          publisherType.getMethod(
              "publish",
              ModulePublicationReference.class,
              org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
              org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
              ProvenCodeFactsReference.class,
              CapsuleProjection.class);
      return (ModulePublicationReference)
          publish.invoke(
              publisher,
              flows,
              fixture.sourceInventory(),
              fixture.programGraphs(),
              facts,
              projection);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("CAPSULE_PROJECTION_MODULE_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("CAPSULE_PROJECTION_MODULE_PUBLISHER_FAILED", cause);
    }
  }

  private static void assertRejected(
      CapsuleProjectionModulePublisher publisher,
      ProgramGraphsPublicFixture fixture,
      ProvenCodeFactsReference facts,
      ModulePublicationReference flows,
      CapsuleProjection mutation) {
    assertThatThrownBy(
            () ->
                publisher.publish(
                    flows, fixture.sourceInventory(), fixture.programGraphs(), facts, mutation))
        .isInstanceOf(IllegalArgumentException.class)
        .satisfies(
            failure ->
                assertThat(failure.getMessage())
                    .isIn(
                        "PROCESS_JOIN_SIGNAL_FLOW_MISMATCH",
                        "EVIDENCE_PROJECTION_INVARIANT_BROKEN"));
  }

  private static CapsuleProjection moveFlowRootedSpanToOtherCapsule(
      CapsuleProjection projection,
      String sourceFlowId,
      String targetFlowId,
      String sharedEvidenceNodeId) {
    String sourceSpanId = expectedSharedSpanId(sourceFlowId, sharedEvidenceNodeId);
    String targetSpanId = expectedSharedSpanId(targetFlowId, sharedEvidenceNodeId);
    Map<String, CapsuleProjection.EvidenceCapsule> capsulesByFlow = capsulesByFlow(projection);
    CapsuleProjection.EvidenceCapsule source = capsulesByFlow.get(sourceFlowId);
    CapsuleProjection.EvidenceCapsule target = capsulesByFlow.get(targetFlowId);
    CapsuleProjection.ModelEvidenceSpan sourceSpan = spanById(projection, sourceSpanId);
    CapsuleProjection.ModelEvidenceSpan targetSpan = spanById(projection, targetSpanId);

    List<CapsuleProjection.ModelEvidenceSpan> spans =
        projection.modelEvidenceSpans().stream()
            .map(
                span -> {
                  if (span.spanId().equals(sourceSpanId)) {
                    return new CapsuleProjection.ModelEvidenceSpan(
                        targetSpanId,
                        sourceSpan.sourceExcerpt(),
                        sourceSpan.supportedAtomIds(),
                        sourceSpan.supportedOutcomePathIds(),
                        sourceSpan.supportedProcessJoinSignalIds());
                  }
                  if (span.spanId().equals(targetSpanId)) {
                    return new CapsuleProjection.ModelEvidenceSpan(
                        sourceSpanId,
                        targetSpan.sourceExcerpt(),
                        targetSpan.supportedAtomIds(),
                        targetSpan.supportedOutcomePathIds(),
                        targetSpan.supportedProcessJoinSignalIds());
                  }
                  return span;
                })
            .toList();

    List<CapsuleProjection.EvidenceCapsule> capsules =
        projection.capsules().stream()
            .map(
                capsule -> {
                  if (capsule.flowSliceId().equals(sourceFlowId)) {
                    return copyCapsule(
                        capsule,
                        replaceId(capsule.modelEvidenceSpanIds(), sourceSpanId, targetSpanId));
                  }
                  if (capsule.flowSliceId().equals(targetFlowId)) {
                    return copyCapsule(
                        capsule,
                        replaceId(capsule.modelEvidenceSpanIds(), targetSpanId, sourceSpanId));
                  }
                  return capsule;
                })
            .toList();
    return new CapsuleProjection(
        projection.profile(),
        projection.flowCompilationRef(),
        projection.proofPackRef(),
        capsules,
        spans,
        swappedObligations(projection, source, target, sourceSpanId, targetSpanId));
  }

  private static CapsuleProjection replaceFlowRootedSpanWithBareEvidenceNode(
      CapsuleProjection projection, String flowId, String sharedEvidenceNodeId) {
    String rootedSpanId = expectedSharedSpanId(flowId, sharedEvidenceNodeId);
    CapsuleProjection.ModelEvidenceSpan rootedSpan = spanById(projection, rootedSpanId);
    List<CapsuleProjection.ModelEvidenceSpan> spans =
        projection.modelEvidenceSpans().stream()
            .map(
                span ->
                    span.spanId().equals(rootedSpanId)
                        ? new CapsuleProjection.ModelEvidenceSpan(
                            sharedEvidenceNodeId,
                            rootedSpan.sourceExcerpt(),
                            rootedSpan.supportedAtomIds(),
                            rootedSpan.supportedOutcomePathIds(),
                            rootedSpan.supportedProcessJoinSignalIds())
                        : span)
            .toList();
    List<CapsuleProjection.EvidenceCapsule> capsules =
        projection.capsules().stream()
            .map(
                capsule ->
                    capsule.flowSliceId().equals(flowId)
                        ? copyCapsule(
                            capsule,
                            replaceId(
                                capsule.modelEvidenceSpanIds(), rootedSpanId, sharedEvidenceNodeId))
                        : capsule)
            .toList();
    List<CapsuleProjection.ProjectionObligation> obligations =
        projection.projectionObligations().stream()
            .map(
                obligation ->
                    capsulesByFlow(projection)
                            .get(flowId)
                            .projectionObligationIds()
                            .contains(obligation.obligationId())
                        ? copyObligation(obligation, rootedSpanId, sharedEvidenceNodeId)
                        : obligation)
            .toList();
    return new CapsuleProjection(
        projection.profile(),
        projection.flowCompilationRef(),
        projection.proofPackRef(),
        capsules,
        spans,
        obligations);
  }

  private static List<CapsuleProjection.ProjectionObligation> swappedObligations(
      CapsuleProjection projection,
      CapsuleProjection.EvidenceCapsule source,
      CapsuleProjection.EvidenceCapsule target,
      String sourceSpanId,
      String targetSpanId) {
    Set<String> sourceObligationIds = new HashSet<>(source.projectionObligationIds());
    Set<String> targetObligationIds = new HashSet<>(target.projectionObligationIds());
    return projection.projectionObligations().stream()
        .map(
            obligation -> {
              if (sourceObligationIds.contains(obligation.obligationId())) {
                return copyObligation(obligation, sourceSpanId, targetSpanId);
              }
              if (targetObligationIds.contains(obligation.obligationId())) {
                return copyObligation(obligation, targetSpanId, sourceSpanId);
              }
              return obligation;
            })
        .toList();
  }

  private static CapsuleProjection.ProjectionObligation copyObligation(
      CapsuleProjection.ProjectionObligation obligation, String oldSpanId, String newSpanId) {
    return new CapsuleProjection.ProjectionObligation(
        obligation.obligationId(),
        obligation.kind(),
        obligation.semanticItemId(),
        replaceId(obligation.satisfyingSpanIds(), oldSpanId, newSpanId));
  }

  private static CapsuleProjection.EvidenceCapsule copyCapsule(
      CapsuleProjection.EvidenceCapsule capsule, List<String> spanIds) {
    return new CapsuleProjection.EvidenceCapsule(
        capsule.evidenceCapsuleId(),
        capsule.flowSliceId(),
        capsule.proofPackId(),
        capsule.modelEligibility(),
        capsule.modelIneligibilityGapIds(),
        capsule.entryView(),
        capsule.factViews(),
        capsule.gapViews(),
        capsule.outcomePathViews(),
        capsule.processJoinSignals(),
        capsule.registryProposalBasisAtomIds(),
        capsule.registryProposalBasisGapIds(),
        spanIds,
        capsule.projectionObligationIds(),
        capsule.budgetUsage());
  }

  private static List<String> replaceId(List<String> values, String oldId, String newId) {
    return values.stream().map(value -> value.equals(oldId) ? newId : value).toList();
  }

  private static Map<String, CapsuleProjection.EvidenceCapsule> capsulesByFlow(
      CapsuleProjection projection) {
    return projection.capsules().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                CapsuleProjection.EvidenceCapsule::flowSliceId, capsule -> capsule));
  }

  private static CapsuleProjection.ModelEvidenceSpan spanById(
      CapsuleProjection projection, String spanId) {
    return projection.modelEvidenceSpans().stream()
        .filter(span -> span.spanId().equals(spanId))
        .findFirst()
        .orElseThrow();
  }

  private static Set<String> evidenceNodeIds(CapsuleProjection.EvidenceCapsule capsule) {
    Set<String> result = new HashSet<>();
    capsule.processJoinSignals().forEach(signal -> result.addAll(signal.evidenceNodeIds()));
    return result;
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

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
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
        reference("flow-profile", "capsule-publisher-flow"), 16, 8, 64, 96, 32, 64, 256);
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
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }

  private static Map<String, JsonNode> jsonNodesById(JsonNode values, String property) {
    Map<String, JsonNode> result = new HashMap<>();
    values.forEach(value -> result.put(value.path(property).asText(), value));
    return Map.copyOf(result);
  }

  private static List<String> jsonStrings(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static List<String> jsonStrings(JsonNode values, String property) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.path(property).asText()));
    return result;
  }
}
