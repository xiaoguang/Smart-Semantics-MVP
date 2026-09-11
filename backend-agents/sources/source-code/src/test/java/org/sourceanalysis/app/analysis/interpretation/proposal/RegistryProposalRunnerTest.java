package org.sourceanalysis.app.analysis.interpretation.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** M2 contract: a scripted R0 provider is called once per persisted, eligible task. */
class RegistryProposalRunnerTest {

  @TempDir Path temporaryDirectory;

  @Test
  void acceptsOneSameFlowBusinessTermFromEachPersistedR0TaskWithoutChangingItsEvidence()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-runner"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, taskSet);

      Object executionSet = run(fixture.moduleArtifacts(), persistedTaskSet, this::validResponse);

      assertThat(list(executionSet, "rounds")).hasSize(taskSet.tasks().size());
      assertThat(list(executionSet, "generationReceipts")).hasSize(taskSet.tasks().size());
      assertThat(list(executionSet, "validatedProposals")).hasSize(taskSet.tasks().size());
      assertThat(list(executionSet, "flowDispositions"))
          .allSatisfy(
              value -> assertThat(property(value, "disposition")).isEqualTo("READY_FOR_FREEZE"));

      ModulePublicationReference persistedExecution =
          publishExecution(fixture, persistedTaskSet, executionSet);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(persistedExecution);
      assertThat(reopened.receipt().address())
          .isEqualTo(
              new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
                  businessFlows.publication().address().runId(),
                  org.sourceanalysis.app.artifact.AnalysisStepKey.FLOW_INTERPRETATION,
                  2,
                  "registry-proposal-runner"));
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName())
                    .isEqualTo("registry-proposal-execution-set.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("FLOW_INTERPRETATION_REGISTRY_PROPOSAL_EXECUTION_SET");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo("flow-interpretation-registry-proposal-execution-set-v3");
              });
    }
  }

  @Test
  void closesEachEligibleFlowAsTypedFailedWithoutInventingARegistryProposal() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-typed-failure"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, taskSet);

      Object executionSet =
          run(fixture.moduleArtifacts(), persistedTaskSet, this::typedFailureResponse);

      assertThat(list(executionSet, "rounds")).hasSize(taskSet.tasks().size());
      assertThat(list(executionSet, "validatedProposals")).isEmpty();
      assertThat(list(executionSet, "flowDispositions"))
          .allSatisfy(value -> assertThat(property(value, "disposition")).isEqualTo("FAILED"));
    }
  }

  @Test
  void closesEachEligibleFlowAsTypedGapUsingOnlyItsPersistedCapsuleGap() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-typed-gap"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, taskSet);

      Object executionSet =
          run(fixture.moduleArtifacts(), persistedTaskSet, this::typedGapResponse);

      assertThat(list(executionSet, "rounds")).hasSize(taskSet.tasks().size());
      assertThat(list(executionSet, "validatedProposals")).isEmpty();
      assertThat(list(executionSet, "flowDispositions"))
          .allSatisfy(
              value -> {
                assertThat(property(value, "disposition")).isEqualTo("GAP");
                assertThat(property(value, "reasonCode")).isEqualTo("INSUFFICIENT_EVIDENCE");
                assertThat((List<?>) property(value, "gapIds")).isNotEmpty();
              });
    }
  }

  @Test
  void failsAfterTheFirstStartedProviderFailureWithoutRetryingAnotherTask() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-provider-failure"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, taskSet);
      AtomicInteger calls = new AtomicInteger();

      assertThatThrownBy(
              () ->
                  new RegistryProposalRunner(fixture.moduleArtifacts())
                      .runRegistryProposals(
                          persistedTaskSet,
                          task -> {
                            calls.incrementAndGet();
                            throw new IllegalStateException("SCRIPTED_TRANSPORT_FAILURE");
                          }))
          .isInstanceOf(RegistryProposalTaskCompilationException.class)
          .hasMessage("PROVIDER_FAILURE_AFTER_START")
          .hasCauseInstanceOf(IllegalStateException.class);
      assertThat(calls).hasValue(1);
    }
  }

  @Test
  void persistsConfiguredAndObservedR0IdentityAsGenerationReceiptV3WithoutReplayingProvider()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-generation-receipt-v3"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, carrierProfile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, taskSet);
      AtomicInteger providerCalls = new AtomicInteger();

      Object executionSet =
          runCarrierScenario(fixture.moduleArtifacts(), persistedTaskSet, providerCalls);
      assertThat(providerCalls).hasValue(taskSet.tasks().size());

      ModulePublicationReference persistedExecution =
          publishExecution(fixture, persistedTaskSet, executionSet);
      int callsAfterExecution = providerCalls.get();
      JsonNode payload =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture
                      .moduleArtifacts()
                      .reopen(persistedExecution)
                      .payloads()
                      .get(0)
                      .canonicalUtf8())
              .path("payload");
      SoftAssertions softly = new SoftAssertions();
      softly.assertThat(payload.path("receipts")).hasSize(taskSet.tasks().size());
      for (JsonNode task : taskNodes(fixture, persistedTaskSet)) {
        softly
            .assertThat(task.path("configuredAdapterId").asText())
            .as("configured adapter for %s", task.path("taskSpecId").asText())
            .isEqualTo("adapter-fixture-alpha");
        softly
            .assertThat(task.path("configuredAuthMode").asText())
            .as("configured auth for %s", task.path("taskSpecId").asText())
            .isEqualTo("auth-fixture-beta");
        softly
            .assertThat(containsText(task.path("inputJson"), "adapter-fixture-alpha"))
            .as("configured adapter must stay outside model-visible input")
            .isFalse();
        softly
            .assertThat(containsText(task.path("inputJson"), "auth-fixture-beta"))
            .as("configured auth must stay outside model-visible input")
            .isFalse();
        softly
            .assertThat(task.path("expectedRuntime").path("upstreamProvider").asText())
            .as("materialized expected upstream provider")
            .isEqualTo("provider-fixture-gamma");
        softly
            .assertThat(task.path("expectedRuntime").path("model").asText())
            .as("materialized expected model")
            .isEqualTo("model-fixture-delta");
      }
      for (JsonNode receipt : payload.path("receipts")) {
        String taskSpecId = receipt.path("taskSpecId").asText();
        softly
            .assertThat(receipt.path("schemaVersion").asText())
            .as("GenerationReceiptV3 schema for %s", taskSpecId)
            .isEqualTo("flow-interpretation-generation-receipt-v3");
        softly
            .assertThat(receipt.path("artifactType").asText())
            .as("GenerationReceiptV3 type for %s", taskSpecId)
            .isEqualTo("FLOW_INTERPRETATION_GENERATION_RECEIPT");
        softly
            .assertThat(receipt.path("generationKind").asText())
            .as("R0 discriminator for %s", taskSpecId)
            .isEqualTo("R0_REGISTRY_PROPOSAL");
        ObjectNode expectedReceipt = expectedR0Receipt(receipt);
        softly
            .assertThat(receipt)
            .as("complete independent GenerationReceiptV3 for %s", taskSpecId)
            .isEqualTo(expectedReceipt);
        softly
            .assertThat(receipt.path("flowSliceId").asText())
            .as("single-flow scope for %s", taskSpecId)
            .isNotBlank();
        softly
            .assertThat(receipt.has("taskShardId"))
            .as("R0 task shard field must be present for %s", taskSpecId)
            .isTrue();
        softly
            .assertThat(receipt.path("taskShardId").isNull())
            .as("R0 task shard must be JSON null for %s", taskSpecId)
            .isTrue();
        softly
            .assertThat(receipt.path("requestSha256").asText())
            .as("request digest for %s", taskSpecId)
            .hasSize(64);
        softly
            .assertThat(receipt.path("responseSha256").asText())
            .as("response digest for %s", taskSpecId)
            .hasSize(64);
        softly
            .assertThat(receipt.path("configuredAdapterId").asText())
            .as("receipt adapter for %s", taskSpecId)
            .isEqualTo("adapter-fixture-alpha");
        softly
            .assertThat(receipt.path("configuredAuthMode").asText())
            .as("receipt auth for %s", taskSpecId)
            .isEqualTo("auth-fixture-beta");
        softly
            .assertThat(receipt.path("expectedRuntime"))
            .as("expected runtime for %s", taskSpecId)
            .isEqualTo(runtimeJson());
        softly
            .assertThat(receipt.path("observedRuntime"))
            .as("observed runtime for %s", taskSpecId)
            .isEqualTo(runtimeJson());
        softly
            .assertThat(receipt.path("started").asBoolean(false))
            .as("started for %s", taskSpecId)
            .isTrue();
        softly
            .assertThat(receipt.path("completed").asBoolean(false))
            .as("completed for %s", taskSpecId)
            .isTrue();
      }
      softly.assertThat(payload.path("callCount").asInt(-1)).isEqualTo(taskSet.tasks().size());
      softly.assertThat(providerCalls.get()).isEqualTo(callsAfterExecution);
      softly.assertAll();
    }
  }

  private Object run(
      org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore moduleArtifacts,
      ModulePublicationReference persistedTaskSet,
      Function<RegistryProposalTask, ImmutableBytes> response)
      throws Exception {
    try {
      Class<?> providerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalProvider");
      Class<?> responseType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalProviderResponse");
      Object provider =
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {providerType},
              (proxy, method, arguments) -> {
                if (!"propose".equals(method.getName()))
                  throw new AssertionError("UNEXPECTED_PROVIDER_METHOD");
                RegistryProposalTask task = (RegistryProposalTask) arguments[0];
                return responseType
                    .getConstructor(ModelRuntimeIdentityV1.class, ImmutableBytes.class)
                    .newInstance(task.expectedRuntime(), response.apply(task));
              });
      Class<?> runnerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalRunner");
      Object runner =
          runnerType
              .getConstructor(org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
              .newInstance(moduleArtifacts);
      return runnerType
          .getMethod("runRegistryProposals", ModulePublicationReference.class, providerType)
          .invoke(runner, persistedTaskSet, provider);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("REGISTRY_PROPOSAL_RUNNER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("REGISTRY_PROPOSAL_RUNNER_FAILED", cause);
    }
  }

  private ModulePublicationReference publishExecution(
      ProgramGraphsPublicFixture fixture, ModulePublicationReference taskSet, Object executionSet)
      throws Exception {
    try {
      Class<?> executionType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalExecutionSet");
      Class<?> publisherType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalExecutionSetModulePublisher");
      Object publisher =
          publisherType
              .getConstructor(org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
              .newInstance(fixture.moduleArtifacts());
      return (ModulePublicationReference)
          publisherType
              .getMethod("publish", ModulePublicationReference.class, executionType)
              .invoke(publisher, taskSet, executionSet);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError(
          "REGISTRY_PROPOSAL_EXECUTION_SET_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("REGISTRY_PROPOSAL_EXECUTION_SET_PUBLISHER_FAILED", cause);
    }
  }

  private ImmutableBytes validResponse(RegistryProposalTask task) {
    JsonNode input = new CanonicalJsonCodec().parseCanonical(task.inputJson());
    String atomId = input.at("/capsuleView/registryProposalBasisAtomIds/0").asText();
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "flow-interpretation-registry-proposal-response-v1");
    response.put("kind", "R0_REGISTRY_PROPOSAL_RESPONSE");
    ArrayNode proposals = response.putArray("proposals");
    ObjectNode proposal = proposals.addObject();
    proposal.put("proposalKind", "BUSINESS_TERM").put("label", "订单审批").put("purpose", "说明订单状态处理");
    proposal.putArray("basisAtomIds").add(atomId);
    proposal.putArray("basisGapIds");
    proposal.putNull("sourceSeedKey");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private ImmutableBytes typedFailureResponse(RegistryProposalTask ignored) {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "flow-interpretation-registry-proposal-response-v1");
    response.put("kind", "R0_REGISTRY_PROPOSAL_FAILED");
    response.put("reasonCode", "MODEL_CANNOT_COMPLETE");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private ImmutableBytes typedGapResponse(RegistryProposalTask task) {
    JsonNode input = new CanonicalJsonCodec().parseCanonical(task.inputJson());
    String gapId = input.at("/capsuleView/registryProposalBasisGapIds/0").asText();
    assertThat(gapId).isNotBlank();
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "flow-interpretation-registry-proposal-response-v1");
    response.put("kind", "R0_REGISTRY_PROPOSAL_GAP");
    response.putArray("gapIds").add(gapId);
    response.put("reasonCode", "INSUFFICIENT_EVIDENCE");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static RegistryProposalTaskProfile profile(ProgramGraphsPublicFixture fixture) {
    return new RegistryProposalTaskProfile(
        "adapter-fixture-alpha",
        "auth-fixture-beta",
        RegistryProposalTaskCompilerTest.reference("registry-prompt", "runner-r0-prompt"),
        RegistryProposalTaskCompilerTest.reference(
            "registry-schema", fixture.artifactControls().schemaBundleSha256()),
        RegistryProposalTaskCompilerTest.reference(
            "registry-runtime", fixture.artifactControls().profileSha256()),
        new ModelRuntimeIdentityV1(
            "provider-fixture-gamma",
            "model-fixture-delta",
            "reasoning-fixture-epsilon",
            "sandbox-fixture-zeta"),
        RegistryProposalTaskCompilerTest.reference("registry-budget", "runner-r0-budget"),
        16,
        16,
        4_096,
        256,
        1_024);
  }

  private static RegistryProposalTaskProfile carrierProfile(ProgramGraphsPublicFixture fixture)
      throws Exception {
    boolean hasCarrierFields = false;
    for (var component : RegistryProposalTaskProfile.class.getRecordComponents()) {
      hasCarrierFields |=
          component.getName().equals("configuredAdapterId")
              || component.getName().equals("configuredAuthMode");
    }
    if (!hasCarrierFields) return profile(fixture);
    Object expectedRuntime = runtimeIdentity();
    var components = RegistryProposalTaskProfile.class.getRecordComponents();
    Object[] values = new Object[components.length];
    Class<?>[] types = new Class<?>[components.length];
    for (int index = 0; index < components.length; index++) {
      var component = components[index];
      types[index] = component.getType();
      values[index] =
          switch (component.getName()) {
            case "configuredAdapterId" -> "adapter-fixture-alpha";
            case "configuredAuthMode" -> "auth-fixture-beta";
            case "promptBundleRef" ->
                RegistryProposalTaskCompilerTest.reference("registry-prompt", "runner-r0-prompt");
            case "outputSchemaRef" ->
                RegistryProposalTaskCompilerTest.reference(
                    "registry-schema", fixture.artifactControls().schemaBundleSha256());
            case "expectedRuntimeRef" ->
                RegistryProposalTaskCompilerTest.reference(
                    "registry-runtime", fixture.artifactControls().profileSha256());
            case "expectedRuntime" -> expectedRuntime;
            case "resourceBudgetRef" ->
                RegistryProposalTaskCompilerTest.reference("registry-budget", "runner-r0-budget");
            case "maxTasks" -> 16;
            case "maxProposalsPerTask" -> 16;
            case "maxResponseUtf8Bytes" -> 4_096;
            case "maxLabelUtf8Bytes" -> 256;
            case "maxPurposeUtf8Bytes" -> 1_024;
            default ->
                throw new AssertionError("UNEXPECTED_R0_PROFILE_COMPONENT_" + component.getName());
          };
    }
    return (RegistryProposalTaskProfile)
        RegistryProposalTaskProfile.class.getDeclaredConstructor(types).newInstance(values);
  }

  private Object runCarrierScenario(
      org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore moduleArtifacts,
      ModulePublicationReference persistedTaskSet,
      AtomicInteger providerCalls)
      throws Exception {
    try {
      Class<?> providerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalProvider");
      Class<?> responseType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalProviderResponse");
      Object provider =
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {providerType},
              (proxy, method, arguments) -> {
                if (!"propose".equals(method.getName())) {
                  throw new AssertionError("UNEXPECTED_PROVIDER_METHOD");
                }
                RegistryProposalTask task = (RegistryProposalTask) arguments[0];
                providerCalls.incrementAndGet();
                Object observedRuntime = task.getClass().getMethod("expectedRuntime").invoke(task);
                ImmutableBytes response = validResponse(task);
                for (Constructor<?> constructor : responseType.getConstructors()) {
                  Class<?>[] types = constructor.getParameterTypes();
                  if (types.length == 2
                      && types[0].isInstance(observedRuntime)
                      && types[1].isInstance(response)) {
                    return constructor.newInstance(observedRuntime, response);
                  }
                }
                throw new AssertionError("R0_PROVIDER_RESPONSE_CONSTRUCTOR_INVALID");
              });
      Class<?> runnerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalRunner");
      Object runner =
          runnerType
              .getConstructor(org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
              .newInstance(moduleArtifacts);
      return runnerType
          .getMethod("runRegistryProposals", ModulePublicationReference.class, providerType)
          .invoke(runner, persistedTaskSet, provider);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("REGISTRY_PROPOSAL_RUNNER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("REGISTRY_PROPOSAL_RUNNER_FAILED", cause);
    }
  }

  private static List<JsonNode> taskNodes(
      ProgramGraphsPublicFixture fixture, ModulePublicationReference persistedTaskSet) {
    JsonNode payload =
        new CanonicalJsonCodec()
            .parseCanonical(
                fixture
                    .moduleArtifacts()
                    .reopen(persistedTaskSet)
                    .payloads()
                    .get(0)
                    .canonicalUtf8())
            .path("payload");
    List<JsonNode> result = new java.util.ArrayList<>();
    payload.path("tasks").forEach(result::add);
    return result;
  }

  private static boolean containsText(JsonNode node, String expected) {
    if (node.isTextual()) return expected.equals(node.textValue());
    if (node.isContainerNode()) {
      var fields = node.fields();
      while (fields.hasNext()) {
        var entry = fields.next();
        if (containsText(entry.getValue(), expected)) return true;
      }
    }
    return false;
  }

  private static Object runtimeIdentity() throws Exception {
    Class<?> type =
        Class.forName("org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1");
    return type.getDeclaredConstructor(String.class, String.class, String.class, String.class)
        .newInstance(
            "provider-fixture-gamma",
            "model-fixture-delta",
            "reasoning-fixture-epsilon",
            "sandbox-fixture-zeta");
  }

  private static ObjectNode runtimeJson() {
    return JsonNodeFactory.instance
        .objectNode()
        .put("upstreamProvider", "provider-fixture-gamma")
        .put("model", "model-fixture-delta")
        .put("reasoningEffort", "reasoning-fixture-epsilon")
        .put("sandbox", "sandbox-fixture-zeta");
  }

  private static ObjectNode expectedR0Receipt(JsonNode actual) {
    ObjectNode expected = JsonNodeFactory.instance.objectNode();
    expected.put("schemaVersion", "flow-interpretation-generation-receipt-v3");
    expected.put("artifactType", "FLOW_INTERPRETATION_GENERATION_RECEIPT");
    expected.put("taskSpecId", actual.path("taskSpecId").asText());
    expected.put("generationKind", "R0_REGISTRY_PROPOSAL");
    expected.put("flowSliceId", actual.path("flowSliceId").asText());
    expected.putNull("taskShardId");
    expected.put("requestSha256", actual.path("requestSha256").asText());
    expected.put("responseSha256", actual.path("responseSha256").asText());
    expected.put("configuredAdapterId", "adapter-fixture-alpha");
    expected.put("configuredAuthMode", "auth-fixture-beta");
    expected.set("expectedRuntime", runtimeJson());
    expected.set("observedRuntime", runtimeJson());
    expected.put("started", true);
    expected.put("completed", true);
    expected.put("generationReceiptId", generationReceiptIdV3(expected));
    return expected;
  }

  private static String generationReceiptIdV3(JsonNode receipt) {
    ObjectNode withoutId = (ObjectNode) receipt.deepCopy();
    withoutId.remove("generationReceiptId");
    return "generation-receipt:"
        + sha256(
            frame("flow-interpretation-generation-receipt-id-v3"),
            frame(new CanonicalJsonCodec().encodeCanonical(withoutId).copyToByteArray()));
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static List<?> list(Object source, String method) {
    Object value = property(source, method);
    if (!(value instanceof List<?> list))
      throw new AssertionError("REGISTRY_PROPOSAL_RUNNER_SHAPE_INVALID");
    return list;
  }

  private static Object property(Object target, String method) {
    try {
      return target.getClass().getMethod(method).invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("REGISTRY_PROPOSAL_RUNNER_SHAPE_INVALID", failure);
    }
  }
}
