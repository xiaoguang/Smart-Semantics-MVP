package org.sourceanalysis.app.analysis.interpretation.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
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
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** M3 contract: valid R0 results become one finite, Flow-scoped repository registry. */
class RepositoryInterpretationRegistryFreezerTest {

  @TempDir Path temporaryDirectory;

  @Test
  void freezesSameLabelProposalsAsSeparateFlowScopedKeysAndPersistsOneRegistry() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("repository-interpretation-registry"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, profile(fixture));
      ModulePublicationReference persistedTaskSet =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, taskSet);
      RegistryProposalExecutionSet executionSet =
          new RegistryProposalRunner(fixture.moduleArtifacts())
              .runRegistryProposals(
                  persistedTaskSet,
                  task ->
                      new RegistryProposalProviderResponse(
                          task.expectedRuntime(), sameLabelTermResponse(task)));
      ModulePublicationReference persistedExecutionSet =
          new RegistryProposalExecutionSetModulePublisher(fixture.moduleArtifacts())
              .publish(persistedTaskSet, executionSet);

      Object registry = freeze(taskSet, executionSet, businessFlows);

      List<?> items = list(registry, "items");
      assertThat(items).hasSize(taskSet.tasks().size());
      assertThat(strings(items, "normalizedLabel")).containsOnly("订单审批");
      assertThat(strings(items, "flowSliceId"))
          .containsExactlyInAnyOrderElementsOf(taskSet.eligibleFlowSliceIds());
      assertThat(strings(items, "provisionalKey")).doesNotHaveDuplicates();
      assertThat(list(registry, "flowDispositions"))
          .extracting(value -> property(value, "disposition"))
          .containsOnly("READY_FOR_FREEZE");

      ModulePublicationReference persistedRegistry =
          publishRegistry(
              fixture, persistedTaskSet, persistedExecutionSet, businessFlows, registry);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(persistedRegistry);
      assertThat(reopened.receipt().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  businessFlows.publication().address().runId(),
                  AnalysisStepKey.FLOW_INTERPRETATION,
                  3,
                  "registry-freezer"));
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName())
                    .isEqualTo("repository-interpretation-registry.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("FLOW_INTERPRETATION_REPOSITORY_INTERPRETATION_REGISTRY_MODULE");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo("flow-interpretation-repository-interpretation-registry-module-v1");
              });
    }
  }

  private Object freeze(
      RegistryProposalTaskSet taskSet,
      RegistryProposalExecutionSet executionSet,
      BusinessFlowsReference businessFlows)
      throws Exception {
    try {
      Class<?> freezerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistryFreezer");
      return freezerType
          .getMethod(
              "freeze",
              RegistryProposalTaskSet.class,
              RegistryProposalExecutionSet.class,
              BusinessFlowsReference.class)
          .invoke(freezerType.getConstructor().newInstance(), taskSet, executionSet, businessFlows);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError(
          "REPOSITORY_INTERPRETATION_REGISTRY_FREEZER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("REPOSITORY_INTERPRETATION_REGISTRY_FREEZER_FAILED", cause);
    }
  }

  private ModulePublicationReference publishRegistry(
      ProgramGraphsPublicFixture fixture,
      ModulePublicationReference taskSet,
      ModulePublicationReference executionSet,
      BusinessFlowsReference businessFlows,
      Object registry)
      throws Exception {
    try {
      Class<?> registryType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistry");
      Class<?> publisherType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistryModulePublisher");
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
                  ModulePublicationReference.class,
                  ModulePublicationReference.class,
                  BusinessFlowsReference.class,
                  registryType)
              .invoke(publisher, taskSet, executionSet, businessFlows, registry);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError(
          "REPOSITORY_INTERPRETATION_REGISTRY_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("REPOSITORY_INTERPRETATION_REGISTRY_PUBLISHER_FAILED", cause);
    }
  }

  private ImmutableBytes sameLabelTermResponse(RegistryProposalTask task) {
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

  private static RegistryProposalTaskProfile profile(ProgramGraphsPublicFixture fixture) {
    return new RegistryProposalTaskProfile(
        "adapter-fixture-alpha",
        "auth-fixture-beta",
        RegistryProposalTaskCompilerTest.reference("registry-prompt", "registry-freezer-r0-prompt"),
        RegistryProposalTaskCompilerTest.reference(
            "registry-schema", fixture.artifactControls().schemaBundleSha256()),
        RegistryProposalTaskCompilerTest.reference(
            "registry-runtime", fixture.artifactControls().profileSha256()),
        new ModelRuntimeIdentityV1(
            "provider-fixture-gamma",
            "model-fixture-delta",
            "reasoning-fixture-epsilon",
            "sandbox-fixture-zeta"),
        RegistryProposalTaskCompilerTest.reference("registry-budget", "registry-freezer-r0-budget"),
        16,
        16,
        4_096,
        256,
        1_024);
  }

  private static List<?> list(Object source, String method) {
    Object value = property(source, method);
    if (!(value instanceof List<?> values))
      throw new AssertionError("REGISTRY_FREEZER_SHAPE_INVALID");
    return values;
  }

  private static List<String> strings(List<?> source, String method) {
    return source.stream().map(value -> String.valueOf(property(value, method))).toList();
  }

  private static Object property(Object target, String method) {
    try {
      return target.getClass().getMethod(method).invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("REGISTRY_FREEZER_SHAPE_INVALID", failure);
    }
  }
}
