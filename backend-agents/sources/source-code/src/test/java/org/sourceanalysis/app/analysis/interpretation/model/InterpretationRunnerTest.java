package org.sourceanalysis.app.analysis.interpretation.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
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
                    .getConstructor(ArtifactReference.class, ImmutableBytes.class)
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
    FlowModelTaskProfile profile = profile(fixture);
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
        prompt, schema, runtime, budget, prompt, schema, runtime, budget, 16, 4096, 16, 16);
  }

  private static RegistryProposalTaskProfile r0Profile(ProgramGraphsPublicFixture fixture) {
    return new RegistryProposalTaskProfile(
        RegistryProposalTaskCompilerTest.reference("registry-prompt", "registry-prompt"),
        RegistryProposalTaskCompilerTest.reference(
            "registry-schema", fixture.artifactControls().schemaBundleSha256()),
        RegistryProposalTaskCompilerTest.reference(
            "registry-runtime", fixture.artifactControls().profileSha256()),
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
