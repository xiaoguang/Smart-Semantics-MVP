package org.sourceanalysis.app.analysis.interpretation.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** M8 contract: a typed P1 Gap persists the planned P2 task without a second Provider call. */
class BusinessProcessInterpretationModulePublisherTest {

  private static final String PUBLISHER =
      "org.sourceanalysis.app.analysis.interpretation.process.BusinessProcessInterpretationModulePublisher";
  @TempDir Path temporaryDirectory;

  @Test
  void persistsP1GapAndPlannedP2NotRunWithoutCallingP2() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m8-p1-gap-checkpoint"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      ModulePublicationReference m6 =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(
                  CrossFlowCandidateCompilerTest.compileForPublisher(
                      fixture, facts, flows, registry));
      BusinessProcessTaskCompilation planned =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6);
      BusinessProcessTaskShardV1 originalShard = planned.taskShards().get(0);
      LimitedShard limited = withOnePacketLocalLimitation(originalShard);
      ModulePublicationReference m7 =
          publishM7Fixture(
              fixture,
              m6,
              new BusinessProcessTaskCompilation(List.of(limited.shard()), List.of(), 0, true));

      CrossFlowCandidateCompilerTest.ProcessInputs inputs =
          CrossFlowCandidateCompilerTest.processInputs(fixture);
      AtomicInteger calls = new AtomicInteger();
      ProcessModelProvider provider =
          request -> {
            int ordinal = calls.getAndIncrement();
            assertThat(ordinal).isZero();
            JsonNode applicationRequest =
                new CanonicalJsonCodec().parseCanonical(request.canonicalApplicationRequestJson());
            assertThat(applicationRequest.path("requestKind").asText())
                .isEqualTo("PROCESS_P1_HYPOTHESIS_REQUEST");
            assertThat(applicationRequest.path("packet").path("limitations"))
                .anySatisfy(
                    value -> assertThat(value.path("limitationKey").asText()).isEqualTo("Q01"));
            ObjectNode response = JsonNodeFactory.instance.objectNode();
            response.putArray("gapKeys").add("Q01");
            response.put("responseKind", "P1_GAP");
            return new ProcessModelProviderResponse(
                inputs.runtime(), new CanonicalJsonCodec().encodeCanonical(response));
          };

      ModulePublicationReference checkpoint =
          runAndPublish(fixture, m7, limited.shard().taskShardId(), inputs, provider);

      assertThat(calls).hasValue(1);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(checkpoint);
      assertThat(reopened.receipt().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  ((AnalysisStepModuleAddress)
                          fixture.moduleArtifacts().reopen(m7).receipt().address())
                      .runId(),
                  AnalysisStepKey.FLOW_INTERPRETATION,
                  8,
                  "business-process-interpretation-runner"));
      assertThat(reopened.payloads()).singleElement().satisfies(this::assertCheckpointDescriptor);
      JsonNode payload =
          new CanonicalJsonCodec()
              .parseCanonical(reopened.payloads().get(0).canonicalUtf8())
              .path("payload");
      assertThat(payload.path("providerCallCount").asInt()).isEqualTo(1);
      assertThat(payload.path("closed").asBoolean()).isTrue();
      assertThat(payload.path("processModelTasks")).hasSize(2);
      assertThat(payload.path("processModelRounds")).hasSize(1);
      assertThat(payload.path("generationReceipts")).hasSize(1);
      assertThat(payload.path("taskDispositions")).hasSize(2);
      assertThat(payload.path("businessProcessHypotheses")).isEmpty();
      assertThat(payload.at("/processModelRounds/0/responseKind").asText()).isEqualTo("P1_GAP");
      assertThat(strings(payload.at("/processModelRounds/0/gapIds")))
          .containsExactly(limited.upstreamGapId());
      assertThat(payload.at("/taskDispositions/0/state").asText()).isEqualTo("RESPONSE_GAP");
      assertThat(payload.at("/taskDispositions/1/state").asText())
          .isEqualTo("NOT_RUN_UPSTREAM_FAILED");
      assertThat(payload.at("/taskDispositions/1/upstreamTaskSpecId").asText()).isNotBlank();
      assertThat(strings(payload.at("/taskDispositions/1/gapIds")))
          .containsExactly(limited.upstreamGapId());
      assertThat(payload.at("/processInterpretationDisposition/disposition").asText())
          .isEqualTo("GAP");
      assertThat(strings(payload.at("/processInterpretationDisposition/gapIds")))
          .containsExactly(limited.upstreamGapId());
    }
  }

  @Test
  void persistsP1HypothesisAndP2KeepWithTwoReceipts() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m8-p1-hypothesis-p2-keep-checkpoint"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      ModulePublicationReference m6 =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(
                  CrossFlowCandidateCompilerTest.compileForPublisher(
                      fixture, facts, flows, registry));
      BusinessProcessTaskCompilation planned =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6);
      ModulePublicationReference m7 = publishM7Fixture(fixture, m6, planned);

      CrossFlowCandidateCompilerTest.ProcessInputs inputs =
          CrossFlowCandidateCompilerTest.processInputs(fixture);
      CanonicalJsonCodec codec = new CanonicalJsonCodec();
      AtomicInteger calls = new AtomicInteger();
      List<JsonNode> requests = new ArrayList<>();
      List<ImmutableBytes> responses = new ArrayList<>();
      ProcessModelProvider provider =
          request -> {
            ImmutableBytes application = request.canonicalApplicationRequestJson();
            JsonNode parsed = codec.parseCanonical(application);
            int ordinal = calls.getAndIncrement();
            requests.add(parsed.deepCopy());
            if (ordinal == 0) {
              assertThat(parsed.path("requestKind").asText())
                  .isEqualTo("PROCESS_P1_HYPOTHESIS_REQUEST");
              assertThat(parsed.path("packet").path("flowCards")).hasSize(2);
              assertThat(parsed.path("packet").path("relationCards")).hasSize(1);
              ImmutableBytes response = p1HypothesisResponse();
              responses.add(response);
              return new ProcessModelProviderResponse(inputs.runtime(), response);
            }
            assertThat(ordinal).isEqualTo(1);
            assertThat(parsed.path("requestKind").asText())
                .isEqualTo("PROCESS_P2_PRECISION_REVIEW_REQUEST");
            assertThat(parsed.path("acceptedHypotheses")).hasSize(1);
            assertThat(parsed.at("/acceptedHypotheses/0/hypothesisKey").asText()).isEqualTo("H01");
            assertThat(strings(parsed.at("/acceptedHypotheses/0/claimKeys")))
                .containsExactly("PC01");
            ImmutableBytes response = p2KeepResponse();
            responses.add(response);
            return new ProcessModelProviderResponse(inputs.runtime(), response);
          };

      AssertionError currentRed = null;
      ModulePublicationReference checkpoint = null;
      try {
        checkpoint =
            runAndPublish(fixture, m7, planned.taskShards().get(0).taskShardId(), inputs, provider);
      } catch (AssertionError failure) {
        currentRed = failure;
      }
      assertThat(currentRed)
          .as(
              "M8 must persist the accepted P1 hypothesis and P2 KEEP checkpoint; current implementation is the expected RED")
          .isNull();
      assertThat(calls).hasValue(2);
      assertThat(requests).hasSize(2);
      assertThat(requests.get(0).path("requestKind").asText())
          .isEqualTo("PROCESS_P1_HYPOTHESIS_REQUEST");
      assertThat(requests.get(1).path("requestKind").asText())
          .isEqualTo("PROCESS_P2_PRECISION_REVIEW_REQUEST");
      assertThat(requests.get(0).path("packet")).isEqualTo(requests.get(1).path("packet"));
      assertThat(responses).hasSize(2);

      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(checkpoint);
      assertThat(reopened.payloads()).singleElement().satisfies(this::assertCheckpointDescriptor);
      JsonNode payload =
          codec.parseCanonical(reopened.payloads().get(0).canonicalUtf8()).path("payload");
      assertThat(payload.path("providerCallCount").asInt()).isEqualTo(2);
      assertThat(payload.path("closed").asBoolean()).isTrue();
      assertThat(payload.path("processModelTasks")).hasSize(2);
      assertThat(payload.path("processModelRounds")).hasSize(2);
      assertThat(payload.path("generationReceipts")).hasSize(2);
      assertThat(payload.path("taskDispositions")).hasSize(2);
      assertThat(payload.path("businessProcessHypotheses")).hasSize(1);
      assertThat(payload.at("/processInterpretationDisposition/disposition").asText())
          .isEqualTo("READY_FOR_ADMISSION");
      assertThat(strings(payload.at("/processInterpretationDisposition/gapIds"))).isEmpty();
      assertThat(payload.at("/processInterpretationDisposition/processGaps")).isEmpty();

      JsonNode p1Task = payload.at("/processModelTasks/0");
      JsonNode p2Task = payload.at("/processModelTasks/1");
      assertThat(p1Task.at("/taskOrdinal").asInt()).isEqualTo(1);
      assertThat(p2Task.at("/taskOrdinal").asInt()).isEqualTo(2);
      assertThat(p1Task.at("/taskKind").asText()).isEqualTo("PROCESS_P1_HYPOTHESIS");
      assertThat(p2Task.at("/taskKind").asText()).isEqualTo("PROCESS_P2_PRECISION_REVIEW");
      assertThat(p1Task.at("/applicationRequest")).isEqualTo(requests.get(0));
      assertThat(p2Task.at("/applicationRequest")).isEqualTo(requests.get(1));
      assertThat(p1Task.at("/inputJsonSha256").asText()).isEqualTo(sha256(requests.get(0), codec));
      assertThat(p2Task.at("/inputJsonSha256").asText()).isEqualTo(sha256(requests.get(1), codec));

      String p1TaskId = p1Task.at("/taskSpecId").asText();
      String p2TaskId = p2Task.at("/taskSpecId").asText();
      String p1RoundId = payload.at("/processModelRounds/0/processModelRoundId").asText();
      String p2RoundId = payload.at("/processModelRounds/1/processModelRoundId").asText();
      String p1ReceiptId = payload.at("/generationReceipts/0/generationReceiptId").asText();
      String p2ReceiptId = payload.at("/generationReceipts/1/generationReceiptId").asText();
      String hypothesisId =
          payload.at("/businessProcessHypotheses/0/businessProcessHypothesisId").asText();
      String claimId = payload.at("/businessProcessHypotheses/0/claims/0/processClaimId").asText();
      String reviewId =
          payload.at("/businessProcessHypotheses/0/review/processHypothesisReviewId").asText();
      assertThat(
              List.of(
                  p1TaskId,
                  p2TaskId,
                  p1RoundId,
                  p2RoundId,
                  p1ReceiptId,
                  p2ReceiptId,
                  hypothesisId,
                  claimId,
                  reviewId))
          .allMatch(value -> !value.isBlank());
      assertThat(payload.at("/processModelRounds/0/taskSpecId").asText()).isEqualTo(p1TaskId);
      assertThat(payload.at("/processModelRounds/1/taskSpecId").asText()).isEqualTo(p2TaskId);
      assertThat(payload.at("/processModelRounds/0/responseKind").asText())
          .isEqualTo("P1_HYPOTHESES");
      assertThat(payload.at("/processModelRounds/1/responseKind").asText()).isEqualTo("P2_REVIEWS");
      assertThat(strings(payload.at("/processModelRounds/0/businessProcessHypothesisIds")))
          .containsExactly(hypothesisId);
      assertThat(strings(payload.at("/processModelRounds/1/businessProcessHypothesisIds")))
          .containsExactly(hypothesisId);
      assertThat(payload.at("/processModelRounds/0/generationReceiptId").asText())
          .isEqualTo(p1ReceiptId);
      assertThat(payload.at("/processModelRounds/1/generationReceiptId").asText())
          .isEqualTo(p2ReceiptId);
      assertThat(payload.at("/processModelRounds/0/processHypothesisReviews")).isEmpty();
      assertThat(payload.at("/processModelRounds/1/processHypothesisReviews")).hasSize(1);
      assertThat(
              payload
                  .at("/processModelRounds/1/processHypothesisReviews/0/processHypothesisReviewId")
                  .asText())
          .isEqualTo(reviewId);
      assertThat(payload.at("/processModelRounds/1/processHypothesisReviews/0/decision").asText())
          .isEqualTo("KEEP");
      assertThat(payload.at("/processModelRounds/0/gapIds")).isEmpty();
      assertThat(payload.at("/processModelRounds/1/gapIds")).isEmpty();
      assertThat(payload.at("/generationReceipts/0/taskSpecId").asText()).isEqualTo(p1TaskId);
      assertThat(payload.at("/generationReceipts/1/taskSpecId").asText()).isEqualTo(p2TaskId);
      assertThat(payload.at("/generationReceipts/0/requestSha256").asText())
          .isEqualTo(p1Task.at("/inputJsonSha256").asText());
      assertThat(payload.at("/generationReceipts/1/requestSha256").asText())
          .isEqualTo(p2Task.at("/inputJsonSha256").asText());
      assertThat(payload.at("/generationReceipts/0/responseSha256").asText())
          .isEqualTo(sha256(responses.get(0)));
      assertThat(payload.at("/generationReceipts/1/responseSha256").asText())
          .isEqualTo(sha256(responses.get(1)));
      assertThat(payload.at("/generationReceipts/0/expectedRuntime"))
          .isEqualTo(payload.at("/generationReceipts/0/observedRuntime"));
      assertThat(payload.at("/generationReceipts/1/expectedRuntime"))
          .isEqualTo(payload.at("/generationReceipts/1/observedRuntime"));
      assertThat(payload.at("/taskDispositions/0/state").asText()).isEqualTo("RESPONSE_ACCEPTED");
      assertThat(payload.at("/taskDispositions/1/state").asText()).isEqualTo("RESPONSE_ACCEPTED");
      assertThat(payload.at("/taskDispositions/0/modelRoundId").asText()).isEqualTo(p1RoundId);
      assertThat(payload.at("/taskDispositions/1/modelRoundId").asText()).isEqualTo(p2RoundId);
      assertThat(payload.at("/taskDispositions/0/generationReceiptId").asText())
          .isEqualTo(p1ReceiptId);
      assertThat(payload.at("/taskDispositions/1/generationReceiptId").asText())
          .isEqualTo(p2ReceiptId);
      assertThat(strings(payload.at("/businessProcessHypotheses/0/flowKeys")))
          .containsExactly("F01", "F02");
      assertThat(strings(payload.at("/businessProcessHypotheses/0/relationKeys")))
          .containsExactly("L01");
      assertThat(strings(payload.at("/businessProcessHypotheses/0/claims/0/basisKeys")))
          .containsExactly("L01");
      assertThat(strings(payload.at("/businessProcessHypotheses/0/claims/0/subjectKeys")))
          .containsExactly("F01");
      assertThat(strings(payload.at("/businessProcessHypotheses/0/claims/0/objectKeys")))
          .containsExactly("F02");
      assertThat(payload.at("/businessProcessHypotheses/0/claims/0/claimKind").asText())
          .isEqualTo("TRANSITION");
      assertThat(payload.at("/businessProcessHypotheses/0/claims/0/predicate").asText())
          .isEqualTo("MAY_HAND_OFF_TO");
      assertThat(payload.at("/businessProcessHypotheses/0/review/hypothesisKey").asText())
          .isEqualTo("H01");
      assertThat(payload.at("/businessProcessHypotheses/0/review/decision").asText())
          .isEqualTo("KEEP");
      assertThat(strings(payload.at("/businessProcessHypotheses/0/review/retainedClaimKeys")))
          .containsExactly("PC01");
      assertThat(
              strings(
                  payload.at(
                      "/processInterpretationDisposition/proposedBusinessProcessHypothesisIds")))
          .containsExactly(hypothesisId);
      assertThat(
              strings(
                  payload.at(
                      "/processInterpretationDisposition/retainedBusinessProcessHypothesisIds")))
          .containsExactly(hypothesisId);

      AtomicInteger idempotentCalls = new AtomicInteger();
      ProcessModelProvider idempotentProvider =
          request -> {
            int ordinal = idempotentCalls.getAndIncrement();
            return new ProcessModelProviderResponse(
                inputs.runtime(), ordinal % 2 == 0 ? p1HypothesisResponse() : p2KeepResponse());
          };
      ModulePublicationReference repeated =
          runAndPublish(
              fixture, m7, planned.taskShards().get(0).taskShardId(), inputs, idempotentProvider);
      assertThat(repeated).isEqualTo(checkpoint);
      assertThat(fixture.moduleArtifacts().reopen(repeated).payloads()).hasSize(1);
    }
  }

  private static ImmutableBytes p1HypothesisResponse() {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    ObjectNode hypothesis = response.putArray("hypotheses").addObject();
    hypothesis.putArray("flowKeys").add("F01").add("F02");
    hypothesis.putArray("relationKeys").add("L01");
    ObjectNode claim = hypothesis.putArray("claims").addObject();
    claim.putArray("basisKeys").add("L01");
    claim.put("claimKind", "TRANSITION");
    claim.putArray("objectKeys").add("F02");
    claim.put("predicate", "MAY_HAND_OFF_TO");
    claim.putArray("subjectKeys").add("F01");
    response.put("responseKind", "P1_HYPOTHESES");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static ImmutableBytes p2KeepResponse() {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("responseKind", "P2_REVIEWS");
    ObjectNode review = response.putArray("reviews").addObject();
    review.put("decision", "KEEP");
    review.put("hypothesisKey", "H01");
    review.putArray("retainedClaimKeys").add("PC01");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static String sha256(JsonNode value, CanonicalJsonCodec codec) {
    return sha256(codec.encodeCanonical(value));
  }

  private static String sha256(ImmutableBytes value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.copyToByteArray()));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new AssertionError("SHA-256 unavailable", unavailable);
    }
  }

  private static LimitedShard withOnePacketLocalLimitation(BusinessProcessTaskShardV1 original) {
    ProcessModelPacketV1 packet = original.processModelPacket();
    if (!packet.limitations().isEmpty()) {
      String limitationKey = packet.limitations().get(0).limitationKey();
      String upstreamGapId =
          original.readerKeyBindings().stream()
              .filter(binding -> limitationKey.equals(binding.readerKey()))
              .filter(binding -> "LIMITATION".equals(binding.keyKind()))
              .findFirst()
              .orElseThrow()
              .internalReferenceIds()
              .get(0);
      return new LimitedShard(original, upstreamGapId);
    }
    List<ReaderKeyBindingV1> bindings = new ArrayList<>(original.readerKeyBindings());
    String upstreamGapId = "gap:known-upstream-limitation";
    bindings.add(new ReaderKeyBindingV1("Q01", "LIMITATION", List.of(upstreamGapId)));
    ProcessModelPacketV1 limited =
        new ProcessModelPacketV1(
            packet.schemaVersion(),
            packet.packetKind(),
            packet.flowCards(),
            packet.relationCards(),
            packet.vocabulary(),
            List.of(
                new ProcessModelPacketV1.ReaderLimitationV1(
                    "Q01", "Static-analysis limitation: unresolved boundary", List.of("F01"))),
            packet.sources());
    return new LimitedShard(
        new BusinessProcessTaskShardV1(
            original.taskShardId(),
            original.shardOrdinal(),
            original.processEvidenceGroupId(),
            original.ownerCandidateRelationIds(),
            original.contextFlowSliceIds(),
            original.shardModelDisposition(),
            original.modelIneligibilityGapIds(),
            limited,
            bindings),
        upstreamGapId);
  }

  private static ModulePublicationReference runAndPublish(
      ProgramGraphsPublicFixture fixture,
      ModulePublicationReference m7,
      String shardId,
      CrossFlowCandidateCompilerTest.ProcessInputs inputs,
      ProcessModelProvider provider)
      throws Exception {
    final Class<?> publisherType;
    try {
      publisherType = Class.forName(PUBLISHER);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("PROCESS_INTERPRETATION_CHECKPOINT_NOT_IMPLEMENTED", missing);
    }
    Object publisher =
        publisherType
            .getConstructor(
                org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                ProcessInputArtifactReader.class)
            .newInstance(fixture.moduleArtifacts(), inputs.reader());
    BusinessProcessInterpretationRequest request =
        new BusinessProcessInterpretationRequest(m7, shardId, inputs.runtime());
    try {
      return (ModulePublicationReference)
          publisherType
              .getMethod(
                  "runAndPublish",
                  BusinessProcessInterpretationRequest.class,
                  ProcessModelProvider.class)
              .invoke(publisher, request, provider);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "PROCESS_INTERPRETATION_CHECKPOINT_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private static ModulePublicationReference publishM7Fixture(
      ProgramGraphsPublicFixture fixture,
      ModulePublicationReference m6,
      BusinessProcessTaskCompilation compilation)
      throws Exception {
    ReopenedModulePublication reopenedM6 = fixture.moduleArtifacts().reopen(m6);
    AnalysisStepModuleAddress m6Address =
        (AnalysisStepModuleAddress) reopenedM6.receipt().address();
    AnalysisStepModuleAddress m7Address =
        new AnalysisStepModuleAddress(
            m6Address.runId(),
            AnalysisStepKey.FLOW_INTERPRETATION,
            7,
            "business-process-task-compiler");
    ArtifactControls controls = reopenedM6.receipt().controls();
    VerifiedCanonicalPayload m6Payload = reopenedM6.payloads().get(0);
    ArtifactReference upstream =
        new ArtifactReference(m6Payload.descriptor().artifactId(), m6Payload.descriptor().sha256());
    BusinessProcessTaskModulePublisher publisher =
        new BusinessProcessTaskModulePublisher(fixture.moduleArtifacts());
    Method payload =
        BusinessProcessTaskModulePublisher.class.getDeclaredMethod(
            "payload",
            AnalysisStepModuleAddress.class,
            List.class,
            ArtifactControls.class,
            ModulePublicationReference.class,
            BusinessProcessTaskCompilation.class);
    payload.setAccessible(true);
    CanonicalModulePayload value =
        (CanonicalModulePayload)
            payload.invoke(publisher, m7Address, List.of(upstream), controls, m6, compilation);
    return fixture
        .moduleArtifacts()
        .install(
            new ModuleInstallRequest(
                m7Address,
                "v1",
                List.of(upstream),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(value)))
        .reference();
  }

  private void assertCheckpointDescriptor(VerifiedCanonicalPayload payload) {
    assertThat(payload.descriptor().fileName()).isEqualTo("process-interpretation-checkpoint.json");
    assertThat(payload.descriptor().schemaVersion())
        .isEqualTo("flow-interpretation-process-interpretation-checkpoint-v1");
    assertThat(payload.descriptor().artifactType())
        .isEqualTo("FLOW_INTERPRETATION_PROCESS_INTERPRETATION_CHECKPOINT");
  }

  private static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture)
      throws Exception {
    Method method =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod(
            "publishProvenFacts", ProgramGraphsPublicFixture.class);
    method.setAccessible(true);
    return (ProvenCodeFactsReference) method.invoke(null, fixture);
  }

  private static ModulePublicationReference publishRegistry(
      ProgramGraphsPublicFixture fixture, BusinessFlowsReference flows) throws Exception {
    Method method =
        Class.forName(
                "org.sourceanalysis.app.analysis.interpretation.model.FiniteKeyFlowTaskCompilerTest")
            .getDeclaredMethod(
                "publishRegistry", ProgramGraphsPublicFixture.class, BusinessFlowsReference.class);
    method.setAccessible(true);
    return (ModulePublicationReference) method.invoke(null, fixture, flows);
  }

  private static List<String> strings(JsonNode value) {
    assertThat(value.isArray()).isTrue();
    List<String> result = new ArrayList<>();
    for (JsonNode item : value) {
      assertThat(item.isTextual()).isTrue();
      result.add(item.textValue());
    }
    return result;
  }

  private record LimitedShard(BusinessProcessTaskShardV1 shard, String upstreamGapId) {}
}
