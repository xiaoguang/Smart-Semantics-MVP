package org.sourceanalysis.app.analysis.interpretation.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M8 contract: one persisted dry two-Flow shard receives exactly P1 then P2. */
class BusinessProcessInterpretationRunnerTest {

  private static final String PACKAGE = "org.sourceanalysis.app.analysis.interpretation.process.";
  private static final String RUNNER = PACKAGE + "BusinessProcessInterpretationRunner";
  private static final String REQUEST = PACKAGE + "BusinessProcessInterpretationRequest";
  private static final String PROVIDER = PACKAGE + "ProcessModelProvider";
  private static final String PROVIDER_RESPONSE = PACKAGE + "ProcessModelProviderResponse";
  private static final String INPUT_READER = PACKAGE + "ProcessInputArtifactReader";

  @TempDir Path temporaryDirectory;

  @Test
  void runsOneFreshReopenedModelSafeShardAsP1ThenP2WithoutLeakingProgramOnlyMaterial()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m8-one-shard-p1-p2"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(
                  CrossFlowCandidateCompilerTest.compileForPublisher(
                      fixture, facts, flows, registry));
      BusinessProcessTaskCompilation shards =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6Publication);
      String shardId = shards.taskShards().get(0).taskShardId();
      ModulePublicationReference m7Publication =
          new BusinessProcessTaskModulePublisher(fixture.moduleArtifacts())
              .publish(m6Publication, shards);

      AtomicInteger calls = new AtomicInteger();
      List<String> requestKinds = new ArrayList<>();
      CrossFlowCandidateCompilerTest.ProcessInputs inputs =
          CrossFlowCandidateCompilerTest.processInputs(fixture);
      Object result =
          run(fixture, m7Publication, shardId, inputs, inputs.runtime(), null, calls, requestKinds);

      assertThat(calls.get()).isEqualTo(2);
      assertThat(requestKinds)
          .containsExactly("PROCESS_P1_HYPOTHESIS_REQUEST", "PROCESS_P2_PRECISION_REVIEW_REQUEST");
      assertThat(property(result, "taskShardId")).isEqualTo(shardId);
      assertThat(property(result, "providerCallCount")).isEqualTo(2);
      assertThat(property(result, "closed")).isEqualTo(true);
      assertThat(property(result, "p1Response")).isNotNull();
      assertThat(property(result, "p2Response")).isNotNull();
    }
  }

  @Test
  void rejectsAMismatchedObservedRuntimeBeforeItCanAcceptP1OrStartP2() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m8-runtime-mismatch"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(
                  CrossFlowCandidateCompilerTest.compileForPublisher(
                      fixture, facts, flows, registry));
      BusinessProcessTaskCompilation shards =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6Publication);
      ModulePublicationReference m7Publication =
          new BusinessProcessTaskModulePublisher(fixture.moduleArtifacts())
              .publish(m6Publication, shards);
      CrossFlowCandidateCompilerTest.ProcessInputs inputs =
          CrossFlowCandidateCompilerTest.processInputs(fixture);
      AtomicInteger calls = new AtomicInteger();
      List<String> requestKinds = new ArrayList<>();

      assertThatThrownBy(
              () ->
                  run(
                      fixture,
                      m7Publication,
                      shards.taskShards().get(0).taskShardId(),
                      inputs,
                      differentRuntime(),
                      null,
                      calls,
                      requestKinds))
          .isInstanceOf(AssertionError.class)
          .hasMessage("PROCESS_INTERPRETATION_RUNNER_FAILED")
          .hasRootCauseMessage("MODEL_RUNTIME_IDENTITY_MISMATCH");
      assertThat(calls.get()).isEqualTo(1);
      assertThat(requestKinds).containsExactly("PROCESS_P1_HYPOTHESIS_REQUEST");
    }
  }

  @Test
  void rejectsAP2ReviewThatRetainsAClaimOutsideTheAcceptedP1Projection() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m8-p2-expanded-claim"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(
                  CrossFlowCandidateCompilerTest.compileForPublisher(
                      fixture, facts, flows, registry));
      BusinessProcessTaskCompilation shards =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6Publication);
      ModulePublicationReference m7Publication =
          new BusinessProcessTaskModulePublisher(fixture.moduleArtifacts())
              .publish(m6Publication, shards);
      CrossFlowCandidateCompilerTest.ProcessInputs inputs =
          CrossFlowCandidateCompilerTest.processInputs(fixture);
      AtomicInteger calls = new AtomicInteger();
      List<String> requestKinds = new ArrayList<>();

      assertThatThrownBy(
              () ->
                  run(
                      fixture,
                      m7Publication,
                      shards.taskShards().get(0).taskShardId(),
                      inputs,
                      inputs.runtime(),
                      p2WithUnexpectedClaim(),
                      calls,
                      requestKinds))
          .isInstanceOf(AssertionError.class)
          .hasMessage("PROCESS_INTERPRETATION_RUNNER_FAILED")
          .hasRootCauseMessage("PROCESS_REVIEW_EXPANDED");
      assertThat(calls.get()).isEqualTo(2);
      assertThat(requestKinds)
          .containsExactly("PROCESS_P1_HYPOTHESIS_REQUEST", "PROCESS_P2_PRECISION_REVIEW_REQUEST");
    }
  }

  private Object run(
      ProgramGraphsPublicFixture fixture,
      ModulePublicationReference m7Publication,
      String shardId,
      CrossFlowCandidateCompilerTest.ProcessInputs inputs,
      ArtifactReference observedRuntime,
      ImmutableBytes p2Override,
      AtomicInteger calls,
      List<String> requestKinds)
      throws Exception {
    final Class<?> runnerType;
    final Class<?> requestType;
    final Class<?> providerType;
    final Class<?> providerResponseType;
    final Class<?> inputReaderType;
    try {
      runnerType = Class.forName(RUNNER);
      requestType = Class.forName(REQUEST);
      providerType = Class.forName(PROVIDER);
      providerResponseType = Class.forName(PROVIDER_RESPONSE);
      inputReaderType = Class.forName(INPUT_READER);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("PROCESS_INTERPRETATION_RUNNER_NOT_IMPLEMENTED", missing);
    }

    Object provider =
        Proxy.newProxyInstance(
            providerType.getClassLoader(),
            new Class<?>[] {providerType},
            (proxy, method, arguments) -> {
              if (!"respond".equals(method.getName())
                  || arguments == null
                  || arguments.length != 1) {
                throw new UnsupportedOperationException(method.getName());
              }
              ImmutableBytes bytes =
                  (ImmutableBytes)
                      arguments[0]
                          .getClass()
                          .getMethod("canonicalApplicationRequestJson")
                          .invoke(arguments[0]);
              JsonNode request = new CanonicalJsonCodec().parseCanonical(bytes);
              assertDryApplicationRequest(request);
              String requestKind = request.path("requestKind").asText();
              requestKinds.add(requestKind);
              int ordinal = calls.getAndIncrement();
              ImmutableBytes response =
                  switch (ordinal) {
                    case 0 -> p1Response(request);
                    case 1 -> p2Override == null ? p2Response(request) : p2Override;
                    default -> throw new AssertionError("PROCESS_PROVIDER_CALLED_MORE_THAN_TWICE");
                  };
              return providerResponseType
                  .getConstructor(ArtifactReference.class, ImmutableBytes.class)
                  .newInstance(observedRuntime, response);
            });

    Object runner =
        runnerType
            .getConstructor(
                org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class, inputReaderType)
            .newInstance(fixture.moduleArtifacts(), inputs.reader());
    Object request =
        requestType
            .getConstructor(ModulePublicationReference.class, String.class, ArtifactReference.class)
            .newInstance(m7Publication, shardId, inputs.runtime());
    try {
      return runnerType
          .getMethod("runInterpretation", requestType, providerType)
          .invoke(runner, request, provider);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "PROCESS_INTERPRETATION_RUNNER_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private static ImmutableBytes p1Response(JsonNode request) {
    assertThat(request.path("requestKind").asText()).isEqualTo("PROCESS_P1_HYPOTHESIS_REQUEST");
    JsonNode packet = request.path("packet");
    assertThat(packet.path("flowCards")).hasSize(2);
    assertThat(packet.path("relationCards")).hasSize(1);
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    ArrayNode hypotheses = response.putArray("hypotheses");
    ObjectNode hypothesis = hypotheses.addObject();
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

  private static ImmutableBytes p2Response(JsonNode request) {
    assertThat(request.path("requestKind").asText())
        .isEqualTo("PROCESS_P2_PRECISION_REVIEW_REQUEST");
    assertThat(request.path("packet").path("flowCards")).hasSize(2);
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("responseKind", "P2_REVIEWS");
    ObjectNode review = response.putArray("reviews").addObject();
    review.put("decision", "KEEP");
    review.put("hypothesisKey", "H01");
    review.putArray("retainedClaimKeys").add("PC01");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static ImmutableBytes p2WithUnexpectedClaim() {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("responseKind", "P2_REVIEWS");
    ObjectNode review = response.putArray("reviews").addObject();
    review.put("decision", "KEEP");
    review.put("hypothesisKey", "H01");
    review.putArray("retainedClaimKeys").add("PC99");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static void assertDryApplicationRequest(JsonNode value) {
    if (value.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String key = field.getKey().toLowerCase(java.util.Locale.ROOT);
        assertThat(key)
            .doesNotContain(
                "artifact",
                "archive",
                "binding",
                "budget",
                "control",
                "digest",
                "evidence",
                "hash",
                "id",
                "proof",
                "receipt",
                "runtime",
                "sha");
        assertDryApplicationRequest(field.getValue());
      }
    } else if (value.isArray()) {
      value.forEach(BusinessProcessInterpretationRunnerTest::assertDryApplicationRequest);
    } else if (value.isTextual()) {
      assertThat(value.textValue()).doesNotContain("/private/", "artifact:", "proof:", "sha256");
    }
  }

  private static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture)
      throws Exception {
    Method method =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod(
            "publishProvenFacts", ProgramGraphsPublicFixture.class);
    method.setAccessible(true);
    return (ProvenCodeFactsReference) method.invoke(null, fixture);
  }

  private static ArtifactReference differentRuntime() {
    String digest = "0".repeat(64);
    return new ArtifactReference(
        ArtifactId.parse("process-model-runtime:" + digest), new Sha256Digest(digest));
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

  private static Object property(Object value, String method) throws Exception {
    return value.getClass().getMethod(method).invoke(value);
  }
}
