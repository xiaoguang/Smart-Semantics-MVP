package org.sourceanalysis.app.analysis.interpretation.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.ArtifactReference;
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
      BusinessFlowsReference businessFlows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
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
      assertThat(reopened.payloads()).singleElement().satisfies(
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
      BusinessFlowsReference businessFlows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, taskSet);

      Object executionSet = run(fixture.moduleArtifacts(), persistedTaskSet, this::typedFailureResponse);

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
      BusinessFlowsReference businessFlows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, taskSet);

      Object executionSet = run(fixture.moduleArtifacts(), persistedTaskSet, this::typedGapResponse);

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
      BusinessFlowsReference businessFlows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
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

  private Object run(
      org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore moduleArtifacts,
      ModulePublicationReference persistedTaskSet,
      Function<RegistryProposalTask, ImmutableBytes> response)
      throws Exception {
    try {
      Class<?> providerType =
          Class.forName("org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalProvider");
      Class<?> responseType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalProviderResponse");
      Object provider =
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {providerType},
              (proxy, method, arguments) -> {
                if (!"propose".equals(method.getName())) throw new AssertionError("UNEXPECTED_PROVIDER_METHOD");
                RegistryProposalTask task = (RegistryProposalTask) arguments[0];
                return responseType
                    .getConstructor(ArtifactReference.class, ImmutableBytes.class)
                    .newInstance(task.expectedRuntime(), response.apply(task));
              });
      Class<?> runnerType =
          Class.forName("org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalRunner");
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
      ProgramGraphsPublicFixture fixture,
      ModulePublicationReference taskSet,
      Object executionSet)
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
      throw new AssertionError("REGISTRY_PROPOSAL_EXECUTION_SET_PUBLISHER_NOT_IMPLEMENTED", missing);
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
    proposal
        .put("proposalKind", "BUSINESS_TERM")
        .put("label", "订单审批")
        .put("purpose", "说明订单状态处理");
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
        RegistryProposalTaskCompilerTest.reference("registry-prompt", "runner-r0-prompt"),
        RegistryProposalTaskCompilerTest.reference(
            "registry-schema", fixture.artifactControls().schemaBundleSha256()),
        RegistryProposalTaskCompilerTest.reference(
            "registry-runtime", fixture.artifactControls().profileSha256()),
        RegistryProposalTaskCompilerTest.reference("registry-budget", "runner-r0-budget"),
        16,
        16,
        4_096,
        256,
        1_024);
  }

  private static List<?> list(Object source, String method) {
    Object value = property(source, method);
    if (!(value instanceof List<?> list)) throw new AssertionError("REGISTRY_PROPOSAL_RUNNER_SHAPE_INVALID");
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
