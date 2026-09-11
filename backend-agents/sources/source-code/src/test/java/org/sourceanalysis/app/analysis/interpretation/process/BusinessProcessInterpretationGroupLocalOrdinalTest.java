package org.sourceanalysis.app.analysis.interpretation.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** M8 RED: shard ordinals restart at zero for each independent process evidence group. */
class BusinessProcessInterpretationGroupLocalOrdinalTest {

  @TempDir Path temporaryDirectory;

  @Test
  void acceptsGroupLocalZeroOrdinalsAcrossTwoIndependentGroups() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("m8-group-local-ordinal"))) {
      BusinessFlowsReference flows = publishIneligibleBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation base =
          CrossFlowCandidateCompilerTest.compileForPublisher(fixture, facts, flows, registry);
      CrossFlowCandidateCompilation split = splitIntoIndependentGroups(base);
      ModulePublicationReference m6 =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(split);

      BusinessProcessTaskCompilation planned =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6);
      assertThat(planned.taskShards()).hasSize(2);
      assertThat(planned.taskShards())
          .allSatisfy(
              shard -> {
                assertThat(shard.shardModelDisposition()).isEqualTo("NO_MODEL");
                assertThat(shard.shardOrdinal()).isZero();
              });
      assertThat(
              planned.taskShards().stream().map(BusinessProcessTaskShardV1::processEvidenceGroupId))
          .doesNotHaveDuplicates();

      ModulePublicationReference m7 =
          new BusinessProcessTaskModulePublisher(fixture.moduleArtifacts()).publish(m6, planned);
      CrossFlowCandidateCompilerTest.ProcessInputs inputs =
          CrossFlowCandidateCompilerTest.processInputs(fixture);
      AtomicInteger providerCalls = new AtomicInteger();
      ModulePublicationReference checkpoint =
          new BusinessProcessInterpretationExecutionPublisher(
                  fixture.moduleArtifacts(), inputs.reader())
              .runAndPublish(
                  new BusinessProcessInterpretationExecutionRequest(m7, inputs.runtime()),
                  request -> {
                    providerCalls.incrementAndGet();
                    throw new AssertionError("NO_MODEL_SHARD_MUST_NOT_CALL_PROVIDER");
                  });

      assertThat(providerCalls).hasValue(0);
      JsonNode payload =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture.moduleArtifacts().reopen(checkpoint).payloads().get(0).canonicalUtf8())
              .path("payload");
      assertThat(payload.path("shardResults")).hasSize(2);
      assertThat(payload.path("shardResults"))
          .allSatisfy(
              shard -> {
                assertThat(shard.path("shardOrdinal").asInt()).isZero();
                assertThat(shard.path("shardModelDisposition").asText()).isEqualTo("NO_MODEL");
              });
      assertThat(payload.path("accounting").path("providerCallCount").asInt()).isZero();
      assertThat(payload.path("accounting").path("noModelShardCount").asInt()).isEqualTo(2);
    }
  }

  private static CrossFlowCandidateCompilation splitIntoIndependentGroups(
      CrossFlowCandidateCompilation base) {
    ProcessEvidenceGroupV2 original = base.processEvidenceGroups().get(0);
    List<String> flowIds = new ArrayList<>(base.flowSliceIds());
    assertThat(flowIds).hasSize(2);
    List<ProcessEvidenceGroupV2> groups =
        List.of(
                singletonGroup(original, flowIds.get(0), ":left"),
                singletonGroup(original, flowIds.get(1), ":right"))
            .stream()
            .sorted(Comparator.comparing(ProcessEvidenceGroupV2::processEvidenceGroupId))
            .toList();
    return new CrossFlowCandidateCompilation(
        base.compilationId() + ":independent-groups",
        base.programGraphsPublicationRef(),
        base.provenCodeFactsPublicationRef(),
        base.businessFlowsPublicationRef(),
        base.repositoryInterpretationRegistryPublicationRef(),
        base.analysisRunRequestRef(),
        flowIds,
        List.of(),
        groups,
        List.of(),
        base.processMaterialLimits(),
        new CrossFlowCandidateAccountingV1(
            flowIds,
            List.of(),
            groups.stream().map(ProcessEvidenceGroupV2::processEvidenceGroupId).toList(),
            List.of(),
            flowIds.size(),
            0,
            groups.size(),
            0,
            true),
        true);
  }

  private static ProcessEvidenceGroupV2 singletonGroup(
      ProcessEvidenceGroupV2 original, String flowId, String suffix) {
    return new ProcessEvidenceGroupV2(
        original.processEvidenceGroupId() + suffix,
        "SINGLETON",
        List.of(flowId),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "MODEL_INELIGIBLE",
        List.of(),
        original.persistedMaterial());
  }

  private static BusinessFlowsReference publishIneligibleBusinessFlows(
      ProgramGraphsPublicFixture fixture) throws Exception {
    Method profile =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod("capsuleProfile", int.class);
    profile.setAccessible(true);
    Object capsuleProfile = profile.invoke(null, 1);
    Method publish =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod(
            "publishBusinessFlows", ProgramGraphsPublicFixture.class, capsuleProfile.getClass());
    publish.setAccessible(true);
    return (BusinessFlowsReference) publish.invoke(null, fixture, capsuleProfile);
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
}
