package org.sourceanalysis.app.analysis.interpretation.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistry;
import org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistryFreezer;
import org.sourceanalysis.app.analysis.interpretation.registry.RepositoryInterpretationRegistryModulePublisher;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** RED contracts for the M5 R2 exact-R1-basis gate. */
class M5R2BasisGateTest {

  @TempDir Path temporaryDirectory;

  @Test
  void rejectsR2BasisExpansionAgainstTheExactR1Selection() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("r2-basis-expansion"))) {
      Object prepared = prepareWithTwoAllowedBasisAtoms(fixture);

      Throwable failure = invokeRunner(fixture, prepared, Mutation.EXPANDED_BASIS);

      assertThat(failure).isNotNull();
      assertThat(failure).hasMessage("MODEL_REVIEW_EXPANDED");
    }
  }

  @Test
  void rejectsR2ForeignAtomAndGapReferencesBeforeCandidateCreation() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("r2-foreign-reference"))) {
      Object prepared = prepareUsingPersistedM4(fixture);

      Throwable failure = invokeRunner(fixture, prepared, Mutation.FOREIGN_REFERENCE);

      assertThat(failure).isNotNull();
      assertThat(failure).hasMessage("MODEL_REFERENCE_INVALID");
    }
  }

  private Object prepareUsingPersistedM4(ProgramGraphsPublicFixture fixture) throws Exception {
    Method prepare =
        InterpretationRunnerTest.class.getDeclaredMethod("prepare", fixture.getClass());
    prepare.setAccessible(true);
    return prepare.invoke(new InterpretationRunnerTest(), fixture);
  }

  private Object prepareWithTwoAllowedBasisAtoms(ProgramGraphsPublicFixture fixture) {
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
                    new RegistryProposalProviderResponse(
                        task.expectedRuntime(), responseWithTwoAllowedAtoms(task)));
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
            .compileFiniteKeyTasks(flows, persistedRegistry, flowModelProfile(fixture));
    ModulePublicationReference persistedFlowTasks =
        new FlowModelTaskSetModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
            .publish(flows, persistedRegistry, taskSet);
    return new PreparedReferences(persistedR0Execution, persistedRegistry, persistedFlowTasks);
  }

  private static ImmutableBytes responseWithTwoAllowedAtoms(RegistryProposalTask task) {
    JsonNode input = new CanonicalJsonCodec().parseCanonical(task.inputJson());
    List<String> atoms = new ArrayList<>();
    for (JsonNode value : input.at("/capsuleView/registryProposalBasisAtomIds")) {
      if (value.isTextual()) atoms.add(value.textValue());
    }
    if (atoms.size() < 2) throw new AssertionError("M5_TEST_FIXTURE_NEEDS_TWO_CAPSULE_ATOMS");
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "flow-interpretation-registry-proposal-response-v1");
    response.put("kind", "R0_REGISTRY_PROPOSAL_RESPONSE");
    ObjectNode proposal = response.putArray("proposals").addObject();
    proposal.put("proposalKind", "BUSINESS_TERM");
    proposal.put("label", "订单审批");
    proposal.put("purpose", "说明订单状态处理");
    proposal.putArray("basisAtomIds").add(atoms.get(0)).add(atoms.get(1));
    proposal.putArray("basisGapIds");
    proposal.putNull("sourceSeedKey");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static FlowModelTaskProfile flowModelProfile(ProgramGraphsPublicFixture fixture) {
    var prompt = RegistryProposalTaskCompilerTest.reference("model-prompt", "model-prompt");
    var schema =
        RegistryProposalTaskCompilerTest.reference(
            "model-schema", fixture.artifactControls().schemaBundleSha256());
    var runtime =
        RegistryProposalTaskCompilerTest.reference(
            "model-runtime", fixture.artifactControls().profileSha256());
    var budget = RegistryProposalTaskCompilerTest.reference("model-budget", "model-budget");
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

  private Throwable invokeRunner(
      ProgramGraphsPublicFixture fixture, Object prepared, Mutation mutation) throws Exception {
    ModulePublicationReference r0Execution =
        (ModulePublicationReference) field(prepared, "r0Execution");
    ModulePublicationReference registry = (ModulePublicationReference) field(prepared, "registry");
    ModulePublicationReference flowTasks =
        (ModulePublicationReference) field(prepared, "flowTasks");
    Class<?> providerType =
        Class.forName("org.sourceanalysis.app.analysis.interpretation.model.FlowModelProvider");
    Class<?> responseType =
        Class.forName(
            "org.sourceanalysis.app.analysis.interpretation.model.FlowModelProviderResponse");
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    Object provider =
        Proxy.newProxyInstance(
            providerType.getClassLoader(),
            new Class<?>[] {providerType},
            (proxy, method, args) -> {
              Object task = args[0];
              JsonNode input =
                  codec.parseCanonical(
                      (ImmutableBytes) task.getClass().getMethod("inputJson").invoke(task));
              ImmutableBytes response = response(input, mutation);
              return responseType
                  .getConstructor(
                      org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1.class,
                      ImmutableBytes.class)
                  .newInstance(task.getClass().getMethod("expectedRuntime").invoke(task), response);
            });
    Class<?> runnerType =
        Class.forName("org.sourceanalysis.app.analysis.interpretation.model.InterpretationRunner");
    Object runner =
        runnerType
            .getConstructor(CanonicalModuleArtifactStore.class)
            .newInstance(fixture.moduleArtifacts());
    Method run =
        runnerType.getMethod(
            "runInterpretations",
            ModulePublicationReference.class,
            ModulePublicationReference.class,
            ModulePublicationReference.class,
            providerType);
    try {
      run.invoke(runner, r0Execution, registry, flowTasks, provider);
      return null;
    } catch (InvocationTargetException failure) {
      return failure.getCause() == null ? failure : failure.getCause();
    }
  }

  private static Object field(Object target, String name) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static ImmutableBytes response(JsonNode input, Mutation mutation) {
    String key = input.at("/allowedRegistryItems/0/provisionalKey").asText();
    String atom = input.at("/allowedRegistryItems/0/basisAtomIds/0").asText();
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("schemaVersion", "flow-interpretation-model-response-v1");
    if ("R1_INTERPRETATION_INPUT".equals(input.path("kind").asText())) {
      response.put("kind", "R1_INTERPRETATION_RESPONSE");
      ArrayNode selections = response.putArray("selections");
      ObjectNode selection = selections.addObject();
      selection.put("selectedKey", key);
      selection.putArray("basisAtomIds").add(atom);
      selection.putArray("basisGapIds");
    } else {
      response.put("kind", "R2_PRECISION_REVIEW_RESPONSE");
      ObjectNode review = response.putArray("reviews").addObject();
      review.put("selectedKey", key);
      review.put("r2Decision", "KEEP");
      ArrayNode atoms = review.putArray("basisAtomIds");
      atoms.add(atom);
      if (mutation == Mutation.EXPANDED_BASIS) {
        String secondAllowed = input.at("/allowedRegistryItems/0/basisAtomIds/1").asText();
        if (secondAllowed.isBlank() || secondAllowed.equals(atom)) {
          throw new AssertionError("M5_TEST_FIXTURE_BASIS_NOT_DISTINCT");
        }
        atoms.add(secondAllowed);
      } else {
        atoms.add("atom:" + "f".repeat(64));
      }
      ArrayNode gaps = review.putArray("basisGapIds");
      if (mutation == Mutation.FOREIGN_REFERENCE) {
        gaps.add("gap:" + "e".repeat(64));
      }
    }
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private enum Mutation {
    EXPANDED_BASIS,
    FOREIGN_REFERENCE
  }

  private record PreparedReferences(
      ModulePublicationReference r0Execution,
      ModulePublicationReference registry,
      ModulePublicationReference flowTasks) {}
}
