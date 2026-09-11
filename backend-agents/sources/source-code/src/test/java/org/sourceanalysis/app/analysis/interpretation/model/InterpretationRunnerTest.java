package org.sourceanalysis.app.analysis.interpretation.model;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalExecutionSet;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalExecutionSetModulePublisher;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalProviderResponse;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalRunner;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTask;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompiler;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskProfile;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskSet;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskSetModulePublisher;
import org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistry;
import org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistryFreezer;
import org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistryModulePublisher;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** M5 contract: accepted R1 then finite R2 review becomes one admissible Flow candidate. */
class InterpretationRunnerTest {

  @TempDir Path temporaryDirectory;

  @Test
  void runsR1ThenR2ExactlyOncePerReadyFlowAndClosesCandidates() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("interpretation-runner"))) {
      Prepared prepared = prepare(fixture);
      AtomicInteger calls = new AtomicInteger();
      Object execution = run(fixture, prepared, calls);
      assertThat(calls.get()).isEqualTo(prepared.modelTaskCount());
      assertThat(list(execution, "rounds")).hasSize(prepared.modelTaskCount());
      assertThat(list(execution, "generationReceipts")).hasSize(prepared.modelTaskCount());
      assertThat(list(execution, "candidates")).hasSize(prepared.readyFlowCount());
      assertThat(list(execution, "flowDispositions"))
          .extracting(value -> property(value, "disposition"))
          .containsOnly("READY_FOR_ADMISSION");
      assertThat(list(execution, "modelTaskDispositions"))
          .extracting(value -> property(value, "state"))
          .containsOnly("RESPONSE_ACCEPTED");
      ModulePublicationReference persisted = publish(fixture, prepared, execution);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(persisted);
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName()).isEqualTo("model-execution-set.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("FLOW_INTERPRETATION_MODEL_EXECUTION_SET");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo("flow-interpretation-model-execution-set-v5");
              });
    }
  }

  @Test
  void persistsSemanticTaskSetIdentityCompleteR1R2ReceiptsAndEmbeddedProposalsWithoutReplay()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("interpretation-carrier"))) {
      Prepared prepared = prepare(fixture, distinctProfile(fixture));
      JsonNode taskSet = payload(fixture, prepared.flowTasks());
      String semanticTaskSetId = taskSet.path("flowTaskSetId").asText();
      assertThat(semanticTaskSetId).isNotBlank();
      assertThat(semanticTaskSetId)
          .as("M4 semantic task-set identity must not be a publication root")
          .isNotEqualTo(prepared.flowTasks().moduleArtifactRoot().value());

      List<JsonNode> tasks = nodes(taskSet, "tasks");
      SoftAssertions softly = new SoftAssertions();
      softly.assertThat(tasks).hasSize(prepared.modelTaskCount());
      for (JsonNode task : tasks) {
        String round = task.path("round").asText();
        String adapter =
            "R1".equals(round) ? "adapter-fixture-r1-alpha" : "adapter-fixture-r2-alpha";
        String auth = "R1".equals(round) ? "auth-fixture-r1-beta" : "auth-fixture-r2-beta";
        softly
            .assertThat(task.path("configuredAdapterId").asText())
            .as("configured adapter remains outside task input for %s", round)
            .isEqualTo(adapter);
        softly
            .assertThat(task.path("configuredAuthMode").asText())
            .as("configured auth remains outside task input for %s", round)
            .isEqualTo(auth);
        softly
            .assertThat(containsText(task.path("inputJson"), adapter))
            .as("adapter sentinel must not be model-visible")
            .isFalse();
        softly
            .assertThat(containsText(task.path("inputJson"), auth))
            .as("auth sentinel must not be model-visible")
            .isFalse();
        softly
            .assertThat(task.path("expectedRuntime").path("upstreamProvider").asText())
            .as("materialized expected runtime for %s", round)
            .isEqualTo(
                "R1".equals(round) ? "provider-fixture-r1-gamma" : "provider-fixture-r2-gamma");
        softly
            .assertThat(task.path("taskSpecId").asText())
            .as("independent task identity for %s", round)
            .isEqualTo(expectedTaskId(task));
      }
      AtomicInteger calls = new AtomicInteger();
      Object execution = runWithTaskRuntime(fixture, prepared, calls);
      ModulePublicationReference persisted = publish(fixture, prepared, execution);
      int callsAfterRun = calls.get();
      JsonNode published = payload(fixture, persisted);
      SoftAssertions result = softly;
      result
          .assertThat(published.path("flowTaskSetId").asText())
          .as("M5 must preserve M4 semantic task-set identity")
          .isEqualTo(semanticTaskSetId);
      result.assertThat(callsAfterRun).isEqualTo(tasks.size());
      result.assertThat(published.path("receipts")).hasSize(tasks.size());
      result
          .assertThat(published.path("interpretationProposals").isMissingNode())
          .as("candidate must be the only proposal owner")
          .isTrue();

      for (JsonNode receipt : nodes(published, "receipts")) {
        JsonNode task =
            tasks.stream()
                .filter(
                    candidate ->
                        candidate
                            .path("taskSpecId")
                            .asText()
                            .equals(receipt.path("taskSpecId").asText()))
                .findFirst()
                .orElse(JsonNodeFactory.instance.objectNode());
        ObjectNode expected = expectedGenerationReceipt(receipt, task, published);
        result
            .assertThat(receipt)
            .as("complete independent GenerationReceiptV3")
            .isEqualTo(expected);
        Set<String> actualFields = new HashSet<>();
        receipt.fieldNames().forEachRemaining(actualFields::add);
        Set<String> expectedFields = new HashSet<>();
        expected.fieldNames().forEachRemaining(expectedFields::add);
        result.assertThat(actualFields).isEqualTo(expectedFields);
      }

      List<JsonNode> candidates = nodes(published, "candidates");
      result.assertThat(candidates).hasSize(prepared.readyFlowCount());
      for (JsonNode candidate : candidates) {
        result
            .assertThat(candidate.has("interpretationProposalIds"))
            .as("IDs-only candidate carrier is forbidden")
            .isFalse();
        result
            .assertThat(candidate.path("interpretationProposals").isArray())
            .as("candidate must embed complete proposal values")
            .isTrue();
        if (candidate.path("interpretationProposals").isArray()) {
          List<String> proposalIds = new ArrayList<>();
          for (JsonNode proposal : candidate.path("interpretationProposals")) {
            proposalIds.add(proposal.path("interpretationProposalId").asText());
            result
                .assertThat(proposal.path("flowSliceId").asText())
                .isEqualTo(candidate.path("flowSliceId").asText());
            result
                .assertThat(proposal.path("interpretationProposalId").asText())
                .isEqualTo(expectedProposalId(proposal));
          }
          result.assertThat(proposalIds).isSortedAccordingTo(String::compareTo);
        }
        result
            .assertThat(candidate.path("candidateId").asText())
            .as("independent Candidate identity")
            .isEqualTo(expectedCandidateId(candidate));
      }
      result
          .assertThat(published.path("executionSetId").asText())
          .as("independent execution-set identity")
          .isEqualTo(expectedExecutionSetId(published, semanticTaskSetId));
      result.assertThat(calls.get()).isEqualTo(callsAfterRun);
      fixture.moduleArtifacts().reopen(persisted);
      result.assertThat(calls.get()).isEqualTo(callsAfterRun);
      result.assertAll();
    }
  }

  private ModulePublicationReference publish(
      ProgramGraphsPublicFixture fixture, Prepared prepared, Object execution) throws Exception {
    try {
      Class<?> executionType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.model.InterpretationExecutionSet");
      Class<?> publisherType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.model.InterpretationExecutionSetModulePublisher");
      Object publisher =
          publisherType
              .getConstructor(org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
              .newInstance(fixture.moduleArtifacts());
      return (ModulePublicationReference)
          publisherType.getMethod("publish", executionType).invoke(publisher, execution);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("INTERPRETATION_EXECUTION_SET_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "INTERPRETATION_EXECUTION_SET_PUBLISHER_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private Object run(ProgramGraphsPublicFixture fixture, Prepared prepared, AtomicInteger calls)
      throws Exception {
    try {
      Class<?> taskType =
          Class.forName("org.sourceanalysis.app.analysis.interpretation.model.FlowModelTask");
      Class<?> providerType =
          Class.forName("org.sourceanalysis.app.analysis.interpretation.model.FlowModelProvider");
      Class<?> responseType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.model.FlowModelProviderResponse");
      Object provider =
          Proxy.newProxyInstance(
              providerType.getClassLoader(),
              new Class<?>[] {providerType},
              (proxy, method, args) -> {
                Object task = args[0];
                calls.incrementAndGet();
                JsonNode input =
                    new CanonicalJsonCodec()
                        .parseCanonical(
                            (ImmutableBytes) task.getClass().getMethod("inputJson").invoke(task));
                ImmutableBytes response = response(input);
                return responseType
                    .getConstructor(ModelRuntimeIdentityV1.class, ImmutableBytes.class)
                    .newInstance(
                        task.getClass().getMethod("expectedRuntime").invoke(task), response);
              });
      Class<?> runnerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.model.InterpretationRunner");
      return runnerType
          .getConstructor(org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
          .newInstance(fixture.moduleArtifacts())
          .getClass()
          .getMethod(
              "runInterpretations",
              ModulePublicationReference.class,
              ModulePublicationReference.class,
              ModulePublicationReference.class,
              providerType)
          .invoke(
              runnerType
                  .getConstructor(
                      org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
                  .newInstance(fixture.moduleArtifacts()),
              prepared.r0Execution(),
              prepared.registry(),
              prepared.flowTasks(),
              provider);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("INTERPRETATION_RUNNER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "INTERPRETATION_RUNNER_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private Object runWithTaskRuntime(
      ProgramGraphsPublicFixture fixture, Prepared prepared, AtomicInteger calls) throws Exception {
    Class<?> providerType =
        Class.forName("org.sourceanalysis.app.analysis.interpretation.model.FlowModelProvider");
    Class<?> responseType =
        Class.forName(
            "org.sourceanalysis.app.analysis.interpretation.model.FlowModelProviderResponse");
    Object provider =
        Proxy.newProxyInstance(
            providerType.getClassLoader(),
            new Class<?>[] {providerType},
            (proxy, method, args) -> {
              Object task = args[0];
              calls.incrementAndGet();
              JsonNode input =
                  new CanonicalJsonCodec()
                      .parseCanonical(
                          (ImmutableBytes) task.getClass().getMethod("inputJson").invoke(task));
              ImmutableBytes response = response(input);
              Object observedRuntime = task.getClass().getMethod("expectedRuntime").invoke(task);
              for (Constructor<?> constructor : responseType.getConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2
                    && parameterTypes[0].isInstance(observedRuntime)
                    && parameterTypes[1].isInstance(response)) {
                  return constructor.newInstance(observedRuntime, response);
                }
              }
              throw new AssertionError("M5_PROVIDER_RESPONSE_CONSTRUCTOR_INVALID");
            });
    Class<?> runnerType =
        Class.forName("org.sourceanalysis.app.analysis.interpretation.model.InterpretationRunner");
    Object runner =
        runnerType
            .getConstructor(org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class)
            .newInstance(fixture.moduleArtifacts());
    try {
      return runnerType
          .getMethod(
              "runInterpretations",
              ModulePublicationReference.class,
              ModulePublicationReference.class,
              ModulePublicationReference.class,
              providerType)
          .invoke(
              runner, prepared.r0Execution(), prepared.registry(), prepared.flowTasks(), provider);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "INTERPRETATION_RUNNER_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private static ImmutableBytes response(JsonNode input) {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "flow-interpretation-model-response-v1");
    if ("R1_INTERPRETATION_INPUT".equals(input.path("kind").asText())) {
      response.put("kind", "R1_INTERPRETATION_RESPONSE");
      ArrayNode selections = response.putArray("selections");
      ObjectNode selection = selections.addObject();
      selection.put("selectedKey", input.at("/allowedRegistryItems/0/provisionalKey").asText());
      selection.set("basisAtomIds", input.at("/allowedRegistryItems/0/basisAtomIds").deepCopy());
      selection.set("basisGapIds", input.at("/allowedRegistryItems/0/basisGapIds").deepCopy());
    } else {
      response.put("kind", "R2_PRECISION_REVIEW_RESPONSE");
      ArrayNode reviews = response.putArray("reviews");
      ObjectNode review = reviews.addObject();
      review.put("selectedKey", input.at("/allowedRegistryItems/0/provisionalKey").asText());
      review.put("r2Decision", "KEEP");
      review.set("basisAtomIds", input.at("/allowedRegistryItems/0/basisAtomIds").deepCopy());
      review.set("basisGapIds", input.at("/allowedRegistryItems/0/basisGapIds").deepCopy());
    }
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private Prepared prepare(ProgramGraphsPublicFixture fixture) throws Exception {
    return prepare(fixture, profile(fixture));
  }

  private Prepared prepare(ProgramGraphsPublicFixture fixture, FlowModelTaskProfile profile)
      throws Exception {
    BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
    RegistryProposalTaskSet r0Tasks =
        new RegistryProposalTaskCompiler(fixture.stepArtifacts())
            .compileRegistryProposalTasks(flows, r0Profile(fixture));
    ModulePublicationReference persistedR0Tasks =
        new RegistryProposalTaskSetModulePublisher(
                fixture.moduleArtifacts(), fixture.stepArtifacts())
            .publish(flows, r0Tasks);
    RegistryProposalExecutionSet r0Execution =
        new RegistryProposalRunner(fixture.moduleArtifacts())
            .runRegistryProposals(
                persistedR0Tasks,
                task ->
                    new RegistryProposalProviderResponse(task.expectedRuntime(), r0Response(task)));
    ModulePublicationReference persistedR0Execution =
        new RegistryProposalExecutionSetModulePublisher(fixture.moduleArtifacts())
            .publish(persistedR0Tasks, r0Execution);
    RepositoryInterpretationRegistry registry =
        new RepositoryInterpretationRegistryFreezer().freeze(r0Tasks, r0Execution, flows);
    ModulePublicationReference persistedRegistry =
        new RepositoryInterpretationRegistryModulePublisher(
                fixture.moduleArtifacts(), fixture.stepArtifacts())
            .publish(persistedR0Tasks, persistedR0Execution, flows, registry);
    FlowModelTaskSet taskSet =
        new FiniteKeyFlowTaskCompiler(fixture.moduleArtifacts(), fixture.stepArtifacts())
            .compileFiniteKeyTasks(flows, persistedRegistry, profile);
    ModulePublicationReference persistedFlowTasks =
        new FlowModelTaskSetModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
            .publish(flows, persistedRegistry, taskSet);
    return new Prepared(
        persistedR0Execution,
        persistedRegistry,
        persistedFlowTasks,
        taskSet.tasks().size(),
        taskSet.eligibleR1R2FlowSliceIds().size());
  }

  private static FlowModelTaskProfile distinctProfile(ProgramGraphsPublicFixture fixture)
      throws Exception {
    var components = FlowModelTaskProfile.class.getRecordComponents();
    Object[] values = new Object[components.length];
    Class<?>[] types = new Class<?>[components.length];
    for (int index = 0; index < components.length; index++) {
      var component = components[index];
      types[index] = component.getType();
      values[index] = profileValue(component.getName(), fixture);
    }
    return FlowModelTaskProfile.class.getDeclaredConstructor(types).newInstance(values);
  }

  private static Object profileValue(String name, ProgramGraphsPublicFixture fixture) {
    boolean r1 = name.startsWith("r1");
    String suffix = r1 ? "r1" : "r2";
    return switch (name) {
      case "r1ConfiguredAdapterId", "r2ConfiguredAdapterId" ->
          "adapter-fixture-" + suffix + "-alpha";
      case "r1ConfiguredAuthMode", "r2ConfiguredAuthMode" -> "auth-fixture-" + suffix + "-beta";
      case "r1PromptBundleRef", "r2PromptBundleRef" ->
          RegistryProposalTaskCompilerTest.reference(
              "model-" + suffix + "-prompt", "model-" + suffix + "-prompt");
      case "r1OutputSchemaRef", "r2OutputSchemaRef" ->
          RegistryProposalTaskCompilerTest.reference(
              "model-" + suffix + "-schema", fixture.artifactControls().schemaBundleSha256());
      case "r1ExpectedRuntimeRef", "r2ExpectedRuntimeRef" ->
          RegistryProposalTaskCompilerTest.reference(
              "model-" + suffix + "-runtime", fixture.artifactControls().profileSha256());
      case "r1ExpectedRuntime", "r2ExpectedRuntime" ->
          new ModelRuntimeIdentityV1(
              "provider-fixture-" + suffix + "-gamma",
              "model-fixture-" + suffix + "-delta",
              "reasoning-fixture-" + suffix + "-epsilon",
              "sandbox-fixture-" + suffix + "-zeta");
      case "r1ResourceBudgetRef", "r2ResourceBudgetRef" ->
          RegistryProposalTaskCompilerTest.reference(
              "model-" + suffix + "-budget", "model-" + suffix + "-budget");
      case "maxTasks" -> 16;
      case "maxResponseUtf8Bytes" -> 4096;
      case "maxSelectedKeys", "maxCandidateProposals" -> 16;
      default -> throw new AssertionError("UNEXPECTED_M5_PROFILE_COMPONENT_" + name);
    };
  }

  private static FlowModelTaskProfile profile(ProgramGraphsPublicFixture fixture) {
    ArtifactReference prompt =
        RegistryProposalTaskCompilerTest.reference("model-prompt", "model-prompt");
    ArtifactReference schema =
        RegistryProposalTaskCompilerTest.reference(
            "model-schema", fixture.artifactControls().schemaBundleSha256());
    ArtifactReference runtime =
        RegistryProposalTaskCompilerTest.reference(
            "model-runtime", fixture.artifactControls().profileSha256());
    ArtifactReference budget =
        RegistryProposalTaskCompilerTest.reference("model-budget", "model-budget");
    return new FlowModelTaskProfile(
        "adapter-fixture-r1",
        "auth-fixture-r1",
        prompt,
        schema,
        runtime,
        new ModelRuntimeIdentityV1("provider-fixture-r1", "model-fixture-r1", "none", "read-only"),
        budget,
        "adapter-fixture-r2",
        "auth-fixture-r2",
        prompt,
        schema,
        runtime,
        new ModelRuntimeIdentityV1("provider-fixture-r2", "model-fixture-r2", "none", "read-only"),
        budget,
        16,
        4096,
        16,
        16);
  }

  private static RegistryProposalTaskProfile r0Profile(ProgramGraphsPublicFixture fixture) {
    return new RegistryProposalTaskProfile(
        "adapter-fixture-alpha",
        "auth-fixture-beta",
        RegistryProposalTaskCompilerTest.reference("registry-prompt", "registry-prompt"),
        RegistryProposalTaskCompilerTest.reference(
            "registry-schema", fixture.artifactControls().schemaBundleSha256()),
        RegistryProposalTaskCompilerTest.reference(
            "registry-runtime", fixture.artifactControls().profileSha256()),
        new ModelRuntimeIdentityV1(
            "provider-fixture-gamma",
            "model-fixture-delta",
            "reasoning-fixture-epsilon",
            "sandbox-fixture-zeta"),
        RegistryProposalTaskCompilerTest.reference("registry-budget", "registry-budget"),
        16,
        16,
        4096,
        256,
        1024);
  }

  private static ImmutableBytes r0Response(RegistryProposalTask task) {
    JsonNode input = new CanonicalJsonCodec().parseCanonical(task.inputJson());
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "flow-interpretation-registry-proposal-response-v1");
    response.put("kind", "R0_REGISTRY_PROPOSAL_RESPONSE");
    ObjectNode proposal = response.putArray("proposals").addObject();
    proposal.put("proposalKind", "BUSINESS_TERM");
    proposal.put("label", "订单审批");
    proposal.put("purpose", "说明订单状态处理");
    proposal
        .putArray("basisAtomIds")
        .add(input.at("/capsuleView/registryProposalBasisAtomIds/0").asText());
    proposal.putArray("basisGapIds");
    proposal.putNull("sourceSeedKey");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static JsonNode payload(
      ProgramGraphsPublicFixture fixture, ModulePublicationReference publication) {
    return new CanonicalJsonCodec()
        .parseCanonical(
            fixture.moduleArtifacts().reopen(publication).payloads().get(0).canonicalUtf8())
        .path("payload");
  }

  private static List<JsonNode> nodes(JsonNode source, String field) {
    List<JsonNode> values = new ArrayList<>();
    source.path(field).forEach(values::add);
    return values;
  }

  private static boolean containsText(JsonNode node, String expected) {
    if (node.isTextual()) return expected.equals(node.textValue());
    if (node.isContainerNode()) {
      var fields = node.fields();
      while (fields.hasNext()) {
        if (containsText(fields.next().getValue(), expected)) return true;
      }
    }
    return false;
  }

  private static ObjectNode expectedGenerationReceipt(
      JsonNode actual, JsonNode task, JsonNode published) {
    String round = task.path("round").asText();
    String suffix = "R1".equals(round) ? "r1" : "r2";
    String responseSha = actual.path("responseSha256").asText();
    if (responseSha.isBlank()) {
      for (JsonNode modelRound : published.path("rounds")) {
        if (task.path("taskSpecId").asText().equals(modelRound.path("taskSpecId").asText())) {
          responseSha = modelRound.path("canonicalResponseSha256").asText();
        }
      }
    }
    ObjectNode expected = JsonNodeFactory.instance.objectNode();
    expected.put("schemaVersion", "flow-interpretation-generation-receipt-v3");
    expected.put("artifactType", "FLOW_INTERPRETATION_GENERATION_RECEIPT");
    expected.put("generationReceiptId", actual.path("generationReceiptId").asText());
    expected.put(
        "generationKind",
        "R1".equals(round) ? "R1_FLOW_INTERPRETATION" : "R2_FLOW_PRECISION_REVIEW");
    expected.put("taskSpecId", task.path("taskSpecId").asText());
    expected.put("flowSliceId", task.path("flowSliceId").asText());
    expected.putNull("taskShardId");
    expected.put("requestSha256", task.path("inputJsonSha256").asText());
    expected.put("responseSha256", responseSha);
    expected.put("configuredAdapterId", "adapter-fixture-" + suffix + "-alpha");
    expected.put("configuredAuthMode", "auth-fixture-" + suffix + "-beta");
    expected.set("expectedRuntime", runtimeJson(suffix));
    expected.set("observedRuntime", runtimeJson(suffix));
    expected.put("started", true);
    expected.put("completed", true);
    expected.put("generationReceiptId", generationReceiptIdV3(expected));
    return expected;
  }

  private static ObjectNode runtimeJson(String suffix) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("upstreamProvider", "provider-fixture-" + suffix + "-gamma")
        .put("model", "model-fixture-" + suffix + "-delta")
        .put("reasoningEffort", "reasoning-fixture-" + suffix + "-epsilon")
        .put("sandbox", "sandbox-fixture-" + suffix + "-zeta");
  }

  private static String expectedTaskId(JsonNode task) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    copy(projection, task, "taskKind");
    copy(projection, task, "round");
    copy(projection, task, "flowSliceId");
    copy(projection, task, "evidenceCapsuleId");
    copy(projection, task, "isolatedSessionKey");
    copy(projection, task, "allowedKeys");
    copy(projection, task, "inputJson");
    copy(projection, task, "inputJsonSha256");
    copy(projection, task, "outputSchemaSha256");
    copy(projection, task, "promptBundleSha256");
    String suffix = "R1".equals(task.path("round").asText()) ? "r1" : "r2";
    projection.put(
        "configuredAdapterId",
        task.path("configuredAdapterId").asText("adapter-fixture-" + suffix + "-alpha"));
    projection.put(
        "configuredAuthMode",
        task.path("configuredAuthMode").asText("auth-fixture-" + suffix + "-beta"));
    copy(projection, task, "expectedRuntimeRef");
    if (task.has("expectedRuntime")) copy(projection, task, "expectedRuntime");
    else projection.set("expectedRuntime", runtimeJson(suffix));
    return identity("flow-model-task:", "flow-interpretation-flow-model-task-id-v1", projection);
  }

  private static String expectedProposalId(JsonNode proposal) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    copy(projection, proposal, "registryProposalId");
    copy(projection, proposal, "flowSliceId");
    copy(projection, proposal, "provisionalKey");
    copy(projection, proposal, "selectedKey");
    copy(projection, proposal, "basisAtomIds");
    copy(projection, proposal, "basisGapIds");
    copy(projection, proposal, "r2Decision");
    return identity(
        "interpretation-proposal:",
        "flow-interpretation-interpretation-proposal-id-v1",
        projection);
  }

  private static String expectedCandidateId(JsonNode candidate) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    copy(projection, candidate, "flowSliceId");
    copy(projection, candidate, "evidenceCapsuleId");
    copy(projection, candidate, "r1RoundId");
    copy(projection, candidate, "r2RoundId");
    copy(projection, candidate, "interpretationProposals");
    return identity(
        "flow-interpretation-candidate:",
        "flow-interpretation-flow-interpretation-candidate-id-v1",
        projection);
  }

  private static String expectedExecutionSetId(JsonNode published, String semanticTaskSetId) {
    ObjectNode projection = JsonNodeFactory.instance.objectNode();
    projection.put("flowTaskSetId", semanticTaskSetId);
    projection.set("modelRoundIds", sortedValues(published.path("rounds"), "modelRoundId"));
    projection.set(
        "generationReceiptIds", sortedValues(published.path("receipts"), "generationReceiptId"));
    projection.set("candidateIds", sortedValues(published.path("candidates"), "candidateId"));
    ArrayNode dispositions = JsonNodeFactory.instance.arrayNode();
    nodes(published, "modelTaskDispositions").stream()
        .sorted(Comparator.comparing(value -> value.path("taskSpecId").asText()))
        .forEach(value -> dispositions.add(value.deepCopy()));
    projection.set("modelTaskDispositions", dispositions);
    projection.set(
        "flowInterpretationDispositionIds",
        sortedValues(published.path("flowDispositions"), "flowInterpretationDispositionId"));
    return identity(
        "interpretation-execution-set:", "flow-interpretation-execution-set-id-v1", projection);
  }

  private static ArrayNode sortedValues(JsonNode values, String field) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    List<String> ordered = new ArrayList<>();
    values.forEach(value -> ordered.add(value.path(field).asText()));
    ordered.stream().sorted().forEach(result::add);
    return result;
  }

  private static void copy(ObjectNode target, JsonNode source, String field) {
    JsonNode value = source.get(field);
    target.set(field, value == null ? JsonNodeFactory.instance.nullNode() : value.deepCopy());
  }

  private static String identity(String prefix, String domain, JsonNode projection) {
    return prefix
        + sha256(
            frame(domain),
            frame(new CanonicalJsonCodec().encodeCanonical(projection).copyToByteArray()));
  }

  private static String generationReceiptIdV3(JsonNode receipt) {
    ObjectNode withoutId = (ObjectNode) receipt.deepCopy();
    withoutId.remove("generationReceiptId");
    return identity(
        "generation-receipt:", "flow-interpretation-generation-receipt-id-v3", withoutId);
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return java.util.HexFormat.of().formatHex(digest.digest());
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

  private static List<?> list(Object value, String method) {
    try {
      Object result = value.getClass().getMethod(method).invoke(value);
      if (result instanceof List<?> list) return list;
    } catch (ReflectiveOperationException ignored) {
      // Rewritten below as the assertion failure used by the public seam.
    }
    throw new AssertionError("INTERPRETATION_EXECUTION_SET_SHAPE_INVALID");
  }

  private static Object property(Object value, String method) {
    try {
      return value.getClass().getMethod(method).invoke(value);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("INTERPRETATION_EXECUTION_SET_SHAPE_INVALID", failure);
    }
  }

  private record Prepared(
      ModulePublicationReference r0Execution,
      ModulePublicationReference registry,
      ModulePublicationReference flowTasks,
      int modelTaskCount,
      int readyFlowCount) {}
}
