package org.sourceanalysis.app.analysis.interpretation.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
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

/** M4 contract: R1/R2 tasks may select only frozen, same-Flow provisional keys. */
class FiniteKeyFlowTaskCompilerTest {

  @TempDir Path temporaryDirectory;

  @Test
  void compilesTwoFiniteKeyTasksPerReadyFlowAndPersistsTheClosedR1AndR2Shards() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("finite-key-flow-tasks"))) {
      BusinessFlowsReference businessFlows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet r0Tasks =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, r0Profile(fixture));
      ModulePublicationReference persistedR0Tasks =
          new RegistryProposalTaskSetModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, r0Tasks);
      RegistryProposalExecutionSet r0Execution =
          new RegistryProposalRunner(fixture.moduleArtifacts())
              .runRegistryProposals(
                  persistedR0Tasks,
                  task -> new RegistryProposalProviderResponse(task.expectedRuntime(), response(task)));
      ModulePublicationReference persistedR0Execution =
          new RegistryProposalExecutionSetModulePublisher(fixture.moduleArtifacts())
              .publish(persistedR0Tasks, r0Execution);
      RepositoryInterpretationRegistry registry =
          new RepositoryInterpretationRegistryFreezer().freeze(r0Tasks, r0Execution, businessFlows);
      ModulePublicationReference persistedRegistry =
          new RepositoryInterpretationRegistryModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(persistedR0Tasks, persistedR0Execution, businessFlows, registry);

      Object taskSet = compile(fixture, businessFlows, persistedRegistry, profile(fixture));
      List<?> tasks = list(taskSet, "tasks");
      assertThat(tasks).hasSize(registry.proposalAccounting().readyFlowSliceIds().size() * 2);
      assertThat(strings(tasks, "round")).containsOnly("R1", "R2");
      assertThat(strings(tasks, "flowSliceId"))
          .containsExactlyInAnyOrderElementsOf(
              registry.proposalAccounting().readyFlowSliceIds().stream()
                  .flatMap(flow -> java.util.stream.Stream.of(flow, flow))
                  .toList());
      for (Object task : tasks) {
        JsonNode input = new CanonicalJsonCodec().parseCanonical((ImmutableBytes) property(task, "inputJson"));
        String flowId = String.valueOf(property(task, "flowSliceId"));
        assertThat(input.path("flowSliceId").asText()).isEqualTo(flowId);
        assertThat(input.at("/allowedRegistryItems/0/flowSliceId").asText()).isEqualTo(flowId);
        assertThat(input.path("allowedRegistryItems")).hasSize(1);
        assertThat(input.at("/capsuleView/flowSliceId").asText()).isEqualTo(flowId);
        if ("R2".equals(property(task, "round"))) {
          assertThat(input.at("/reviewTarget/r1TaskSpecId").asText()).isNotBlank();
        }
      }
      assertThat(list(taskSet, "r1ShardReceipts")).singleElement();
      assertThat(list(taskSet, "r2ShardReceipts")).singleElement();
      assertThat(property(taskSet, "registryPublicationRef")).isEqualTo(persistedRegistry);
      ModulePublicationReference persistedTasks =
          publish(fixture, businessFlows, persistedRegistry, taskSet);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(persistedTasks);
      assertThat(reopened.payloads()).singleElement().satisfies(
          payload -> {
            assertThat(payload.descriptor().fileName()).isEqualTo("flow-task-set.json");
            assertThat(payload.descriptor().artifactType())
                .isEqualTo("FLOW_INTERPRETATION_FLOW_TASK_SET");
            assertThat(payload.descriptor().schemaVersion())
                .isEqualTo("flow-interpretation-flow-task-set-v4");
          });
    }
  }

  private ModulePublicationReference publish(
      ProgramGraphsPublicFixture fixture,
      BusinessFlowsReference businessFlows,
      ModulePublicationReference registryPublication,
      Object taskSet)
      throws Exception {
    try {
      Class<?> taskSetType =
          Class.forName("org.sourceanalysis.app.analysis.interpretation.model.FlowModelTaskSet");
      Class<?> publisherType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.model.FlowModelTaskSetModulePublisher");
      Object publisher =
          publisherType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
              .newInstance(fixture.moduleArtifacts(), fixture.stepArtifacts());
      return (ModulePublicationReference)
          publisherType
              .getMethod(
                  "publish",
                  BusinessFlowsReference.class,
                  ModulePublicationReference.class,
                  taskSetType)
              .invoke(publisher, businessFlows, registryPublication, taskSet);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FLOW_MODEL_TASK_SET_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "FLOW_MODEL_TASK_SET_PUBLISHER_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private Object compile(
      ProgramGraphsPublicFixture fixture,
      BusinessFlowsReference businessFlows,
      ModulePublicationReference registryPublication,
      Object profile)
      throws Exception {
    try {
      Class<?> compilerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.model.FiniteKeyFlowTaskCompiler");
      Class<?> profileType =
          Class.forName("org.sourceanalysis.app.analysis.interpretation.model.FlowModelTaskProfile");
      return compilerType
          .getConstructor(
              org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
              org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
          .newInstance(fixture.moduleArtifacts(), fixture.stepArtifacts())
          .getClass()
          .getMethod(
              "compileFiniteKeyTasks",
              BusinessFlowsReference.class,
              ModulePublicationReference.class,
              profileType)
          .invoke(
              compilerType
                  .getConstructor(
                      org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                      org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
                  .newInstance(fixture.moduleArtifacts(), fixture.stepArtifacts()),
              businessFlows,
              registryPublication,
              profile);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FINITE_KEY_FLOW_TASK_COMPILER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "FINITE_KEY_FLOW_TASK_COMPILER_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private Object profile(ProgramGraphsPublicFixture fixture) throws Exception {
    try {
      Class<?> type =
          Class.forName("org.sourceanalysis.app.analysis.interpretation.model.FlowModelTaskProfile");
      ArtifactReference prompt =
          RegistryProposalTaskCompilerTest.reference("flow-model-prompt", "flow-model-prompt");
      ArtifactReference schema =
          RegistryProposalTaskCompilerTest.reference(
              "flow-model-schema", fixture.artifactControls().schemaBundleSha256());
      ArtifactReference runtime =
          RegistryProposalTaskCompilerTest.reference(
              "flow-model-runtime", fixture.artifactControls().profileSha256());
      ArtifactReference budget =
          RegistryProposalTaskCompilerTest.reference("flow-model-budget", "flow-model-budget");
      return type
          .getConstructor(
              ArtifactReference.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ArtifactReference.class,
              int.class,
              int.class,
              int.class,
              int.class)
          .newInstance(prompt, schema, runtime, budget, prompt, schema, runtime, budget, 16, 4096, 16, 16);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FINITE_KEY_FLOW_TASK_COMPILER_NOT_IMPLEMENTED", missing);
    }
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

  private static ImmutableBytes response(RegistryProposalTask task) {
    JsonNode input = new CanonicalJsonCodec().parseCanonical(task.inputJson());
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "flow-interpretation-registry-proposal-response-v1");
    response.put("kind", "R0_REGISTRY_PROPOSAL_RESPONSE");
    ArrayNode proposals = response.putArray("proposals");
    ObjectNode proposal = proposals.addObject();
    proposal.put("proposalKind", "BUSINESS_TERM");
    proposal.put("label", "订单审批");
    proposal.put("purpose", "说明订单状态处理");
    proposal.putArray("basisAtomIds").add(input.at("/capsuleView/registryProposalBasisAtomIds/0").asText());
    proposal.putArray("basisGapIds");
    proposal.putNull("sourceSeedKey");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static List<?> list(Object source, String method) {
    Object value = property(source, method);
    if (!(value instanceof List<?> values)) throw new AssertionError("FLOW_MODEL_TASK_SET_SHAPE_INVALID");
    return values;
  }

  private static List<String> strings(List<?> source, String method) {
    return source.stream().map(value -> String.valueOf(property(value, method))).toList();
  }

  private static Object property(Object target, String method) {
    try {
      Method accessor = target.getClass().getMethod(method);
      return accessor.invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("FLOW_MODEL_TASK_SET_SHAPE_INVALID", failure);
    }
  }
}
