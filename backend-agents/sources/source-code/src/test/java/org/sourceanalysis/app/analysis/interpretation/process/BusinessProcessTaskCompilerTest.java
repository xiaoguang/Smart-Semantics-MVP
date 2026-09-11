package org.sourceanalysis.app.analysis.interpretation.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/**
 * M7 contract: program-only process sharding projects a small, dry packet before any P1/P2 call.
 */
class BusinessProcessTaskCompilerTest {

  private static final String COMPILER =
      "org.sourceanalysis.app.analysis.interpretation.process.BusinessProcessTaskCompiler";

  @TempDir Path temporaryDirectory;

  @Test
  void projectsOneModelSafeTwoFlowPacketWithoutInternalEvidenceOrArchiveFields() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m7-dry-two-flow-packet"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisher(fixture, facts, flows, registry);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);

      JsonNode result = new ObjectMapper().valueToTree(compile(fixture, m6Publication));

      assertThat(result.path("providerCallCount").asInt()).isZero();
      assertThat(result.path("taskShards")).hasSize(1);
      JsonNode shard = result.path("taskShards").get(0);
      assertThat(shard.path("shardModelDisposition").asText()).isEqualTo("MODEL_SAFE");
      assertThat(shard.path("ownerCandidateRelationIds")).hasSize(1);
      JsonNode packet = shard.path("processModelPacket");
      assertThat(packet.path("schemaVersion").asText())
          .isEqualTo("flow-interpretation-process-model-packet-v1");
      assertThat(packet.path("flowCards")).hasSize(2);
      assertThat(packet.path("relationCards")).hasSize(1);
      assertThat(packet.path("vocabulary")).isNotEmpty();
      assertThat(packet.path("sources")).isNotEmpty();
      assertDryPacket(packet);
    }
  }

  @Test
  void marksAnOversizedAtomicRelationAsNoModelInsteadOfDroppingTheBusinessProcess()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m7-atomic-relation-over-budget"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 1);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);

      JsonNode result = new ObjectMapper().valueToTree(compile(fixture, m6Publication));

      assertThat(result.path("providerCallCount").asInt()).isZero();
      assertThat(result.path("taskShards")).hasSize(1);
      JsonNode shard = result.path("taskShards").get(0);
      assertThat(shard.path("shardModelDisposition").asText()).isEqualTo("NO_MODEL");
      assertThat(shard.path("processModelPacket").isNull()).isTrue();
      assertThat(shard.path("readerKeyBindings")).isEmpty();
      assertThat(shard.path("modelIneligibilityGapIds")).hasSize(1);
      assertThat(result.path("processGaps")).hasSize(1);
      JsonNode gap = result.path("processGaps").get(0);
      assertThat(gap.path("gapCode").asText()).isEqualTo("PROCESS_TASK_BUDGET_EXCEEDED");
      assertThat(gap.path("limitKind").asText()).isEqualTo("MAX_FLOWS");
      assertThat(gap.path("configuredLimit").asInt()).isEqualTo(1);
      assertThat(gap.path("observedValue").asInt()).isEqualTo(2);
      assertThat(gap.path("taskShardId").asText()).isEqualTo(shard.path("taskShardId").asText());
    }
  }

  @Test
  void givesEveryDirectAndSemanticCandidateRelationOneShardOwnerWhenPairsCannotFitTogether()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithChainedJavaCalls(
            temporaryDirectory.resolve("m7-greedy-two-handoff-shards"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 2);
      // The two direct handoffs are accompanied by one deliberately weak shared-term cue between
      // the end Flows. M6 must retain that cue; M7 may not silently discard it merely because
      // the three Flow contexts cannot coexist in a two-Flow packet.
      assertThat(candidates.candidateRelations()).hasSize(3);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);

      JsonNode result = new ObjectMapper().valueToTree(compile(fixture, m6Publication));

      assertThat(result.path("providerCallCount").asInt()).isZero();
      assertProcessGapLaneAccounting(result);
      assertThat(result.path("taskShards")).hasSize(3);
      Set<String> ownedRelations = new HashSet<>();
      Set<String> allContextFlows = new HashSet<>();
      result
          .path("taskShards")
          .forEach(
              shard -> {
                assertThat(shard.path("shardModelDisposition").asText()).isEqualTo("MODEL_SAFE");
                assertThat(shard.path("ownerCandidateRelationIds")).hasSize(1);
                assertThat(shard.path("contextFlowSliceIds")).hasSize(2);
                assertThat(shard.path("processModelPacket").path("relationCards")).hasSize(1);
                shard
                    .path("ownerCandidateRelationIds")
                    .forEach(value -> ownedRelations.add(value.asText()));
                shard
                    .path("contextFlowSliceIds")
                    .forEach(value -> allContextFlows.add(value.asText()));
              });
      assertThat(ownedRelations).hasSize(3);
      assertThat(allContextFlows).hasSize(3);
    }
  }

  @Test
  void greedilyPacksAllReachableHandoffsWhenTheirCompleteContextFitsOneShard() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithChainedJavaCalls(
            temporaryDirectory.resolve("m7-two-direct-handoff-shards"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 3);
      // An entry-rooted Flow includes its repository-local reachable calls. The first Flow thus
      // has direct handoffs to both later entry targets, in addition to the middle-to-final
      // handoff. All three complete relations fit the three-Flow budget and must remain owned.
      assertThat(candidates.candidateRelations()).hasSize(3);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);

      JsonNode result = new ObjectMapper().valueToTree(compile(fixture, m6Publication));

      assertThat(result.path("providerCallCount").asInt()).isZero();
      assertProcessGapLaneAccounting(result);
      assertThat(result.path("taskShards")).hasSize(1);
      Set<String> ownedRelations = new HashSet<>();
      Set<String> allContextFlows = new HashSet<>();
      result
          .path("taskShards")
          .forEach(
              shard -> {
                assertThat(shard.path("shardModelDisposition").asText()).isEqualTo("MODEL_SAFE");
                assertThat(shard.path("ownerCandidateRelationIds")).hasSize(3);
                assertThat(shard.path("contextFlowSliceIds")).hasSize(3);
                assertThat(shard.path("processModelPacket").path("relationCards")).hasSize(3);
                shard
                    .path("ownerCandidateRelationIds")
                    .forEach(value -> ownedRelations.add(value.asText()));
                shard
                    .path("contextFlowSliceIds")
                    .forEach(value -> allContextFlows.add(value.asText()));
              });
      assertThat(ownedRelations).hasSize(3);
      assertThat(allContextFlows).hasSize(3);
    }
  }

  @Test
  void keepsAnIneligibleProcessRelationOutOfTheModelWithoutDroppingItsOwner() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m7-ineligible-relation"))) {
      BusinessFlowsReference flows = publishIneligibleBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisher(fixture, facts, flows, registry);
      assertThat(candidates.candidateRelations()).hasSize(1);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);

      JsonNode result = new ObjectMapper().valueToTree(compile(fixture, m6Publication));

      assertThat(result.path("providerCallCount").asInt()).isZero();
      assertProcessGapLaneAccounting(result);
      assertThat(result.path("taskShards"))
          .singleElement()
          .satisfies(
              shard -> {
                assertThat(shard.path("shardModelDisposition").asText()).isEqualTo("NO_MODEL");
                assertThat(shard.path("ownerCandidateRelationIds")).hasSize(1);
                assertThat(shard.path("contextFlowSliceIds")).hasSize(2);
                assertThat(shard.path("processModelPacket").isNull()).isTrue();
                assertThat(shard.path("readerKeyBindings")).isEmpty();
                assertThat(shard.path("modelIneligibilityGapIds")).isNotEmpty();
              });
    }
  }

  @Test
  void separatesMixedEligibleAndIneligibleRelationUnitsWithoutLosingAnyOwner() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithChainedJavaCalls(
            temporaryDirectory.resolve("m7-mixed-eligibility"))) {
      BusinessFlowsReference flows = publishBusinessFlowsWithCapsuleBudget(fixture, 350);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 2);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);

      JsonNode result = new ObjectMapper().valueToTree(compile(fixture, m6Publication));

      assertThat(result.path("providerCallCount").asInt()).isZero();
      assertThat(result.path("taskShards")).hasSize(3);
      assertThat(result.path("taskShards").findValuesAsText("shardModelDisposition"))
          .containsExactlyInAnyOrder("MODEL_SAFE", "NO_MODEL", "NO_MODEL");

      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(m6Publication);
      JsonNode m6Payload =
          new CanonicalJsonCodec()
              .parseCanonical(reopened.payloads().get(0).canonicalUtf8())
              .path("payload");
      Map<String, JsonNode> upstreamGapsById = new HashMap<>();
      Map<String, Set<String>> upstreamGapFlowsById = new HashMap<>();
      m6Payload
          .path("processEvidenceGroups")
          .forEach(
              group ->
                  group
                      .path("persistedMaterial")
                      .path("flowViews")
                      .forEach(
                          flow -> {
                            String flowId = flow.path("flowSliceId").asText();
                            flow.path("evidenceCapsule")
                                .path("gapViews")
                                .forEach(
                                    gap -> {
                                      String gapId = gap.path("gapId").asText();
                                      upstreamGapsById.put(gapId, gap);
                                      upstreamGapFlowsById
                                          .computeIfAbsent(gapId, ignored -> new HashSet<>())
                                          .add(flowId);
                                    });
                          }));

      Map<String, JsonNode> processGapsById = new HashMap<>();
      List<String> processGapIds = new ArrayList<>();
      result
          .path("processGaps")
          .forEach(
              gap -> {
                String gapId = gap.path("gapId").asText();
                processGapIds.add(gapId);
                assertThat(processGapsById.put(gapId, gap)).isNull();
              });
      assertThat(processGapIds).containsExactlyElementsOf(processGapIds.stream().sorted().toList());

      Set<String> owners = new HashSet<>();
      Set<String> noModelGapIds = new HashSet<>();
      Set<String> noModelWrapperIds = new HashSet<>();
      Set<String> noModelShardIds = new HashSet<>();
      result
          .path("taskShards")
          .forEach(
              shard -> {
                assertThat(shard.path("ownerCandidateRelationIds")).hasSize(1);
                shard
                    .path("ownerCandidateRelationIds")
                    .forEach(value -> owners.add(value.asText()));
                if ("NO_MODEL".equals(shard.path("shardModelDisposition").asText())) {
                  noModelShardIds.add(shard.path("taskShardId").asText());
                  assertThat(shard.path("processModelPacket").isNull()).isTrue();
                  List<String> shardGapIds = new ArrayList<>();
                  shard
                      .path("modelIneligibilityGapIds")
                      .forEach(value -> shardGapIds.add(value.asText()));
                  assertThat(shardGapIds).isNotEmpty();
                  noModelGapIds.addAll(shardGapIds);
                  for (String shardGapId : shardGapIds) {
                    JsonNode processGap = processGapsById.get(shardGapId);
                    assertThat(processGap).isNotNull();
                    noModelWrapperIds.add(processGap.path("gapId").asText());
                    assertThat(processGap.path("gapCode").asText())
                        .isEqualTo("PROCESS_UPSTREAM_MODEL_INELIGIBLE");
                    assertThat(processGap.path("gapScope").asText())
                        .isEqualTo("PROCESS_TASK_SHARD");
                    assertThat(processGap.path("processEvidenceGroupId").asText())
                        .isEqualTo(shard.path("processEvidenceGroupId").asText());
                    assertThat(processGap.path("candidateRelationIds"))
                        .isEqualTo(shard.path("ownerCandidateRelationIds"));
                    assertThat(processGap.path("affectedFlowSliceIds"))
                        .isEqualTo(shard.path("contextFlowSliceIds"));
                    assertThat(processGap.path("limitKind").isNull()).isTrue();
                    assertThat(processGap.path("configuredLimit").isNull()).isTrue();
                    assertThat(processGap.path("observedValue").isNull()).isTrue();
                    assertThat(processGap.path("messageKey").asText())
                        .isEqualTo("process-upstream-model-ineligible");
                    assertThat(processGap.path("taskShardId").asText())
                        .isEqualTo(shard.path("taskShardId").asText());

                    JsonNode upstream = processGap.path("upstreamGap");
                    assertThat(upstream.isObject()).isTrue();
                    JsonNode gapView = upstream.path("gapView");
                    String upstreamGapId = gapView.path("gapId").asText();
                    assertThat(upstreamGapsById).containsKey(upstreamGapId);
                    assertThat(gapView).isEqualTo(upstreamGapsById.get(upstreamGapId));
                    Set<String> expectedSourceFlows =
                        new HashSet<>(upstreamGapFlowsById.get(upstreamGapId));
                    expectedSourceFlows.retainAll(textValues(shard.path("contextFlowSliceIds")));
                    assertThat(textValues(upstream.path("sourceFlowSliceIds")))
                        .containsExactlyInAnyOrderElementsOf(expectedSourceFlows);
                    assertThat(textValues(upstream.path("sourceFlowSliceIds"))).isNotEmpty();
                  }
                }
              });
      assertThat(owners).hasSize(3);
      assertThat(noModelShardIds).hasSize(2);
      assertProcessGapLaneAccounting(result);
      assertThat(noModelWrapperIds).hasSize(noModelShardIds.size());
    }
  }

  @Test
  void conservesNoModelOwnershipAndModelSafeLimitationReferencesSeparately() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithChainedJavaCalls(
            temporaryDirectory.resolve("m7-safe-limitation-gap-carrier"))) {
      BusinessFlowsReference flows = publishBusinessFlowsWithCapsuleBudget(fixture, 350);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 2);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);

      JsonNode result = new ObjectMapper().valueToTree(compile(fixture, m6Publication));
      assertThat(result.path("providerCallCount").asInt()).isZero();
      assertThat(result.path("taskShards")).hasSize(3);

      JsonNode m6Payload =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture.moduleArtifacts().reopen(m6Publication).payloads().get(0).canonicalUtf8())
              .path("payload");
      Map<String, JsonNode> upstreamGapsById = new HashMap<>();
      Map<String, Set<String>> upstreamGapFlowsById = new HashMap<>();
      m6Payload
          .path("processEvidenceGroups")
          .forEach(
              group ->
                  group
                      .path("persistedMaterial")
                      .path("flowViews")
                      .forEach(
                          flow -> {
                            String flowId = flow.path("flowSliceId").asText();
                            flow.path("evidenceCapsule")
                                .path("gapViews")
                                .forEach(
                                    gap -> {
                                      String gapId = gap.path("gapId").asText();
                                      upstreamGapsById.put(gapId, gap);
                                      upstreamGapFlowsById
                                          .computeIfAbsent(gapId, ignored -> new HashSet<>())
                                          .add(flowId);
                                    });
                          }));

      Map<String, JsonNode> processGapsById = new HashMap<>();
      result
          .path("processGaps")
          .forEach(gap -> processGapsById.put(gap.path("gapId").asText(), gap));
      Set<String> ownedByNoModel = new HashSet<>();
      Set<String> referencedBySafeLimitations = new HashSet<>();
      Set<String> allShardIds = new HashSet<>();
      result
          .path("taskShards")
          .forEach(
              shard -> {
                String shardId = shard.path("taskShardId").asText();
                assertThat(allShardIds.add(shardId)).isTrue();
                String disposition = shard.path("shardModelDisposition").asText();
                if ("NO_MODEL".equals(disposition)) {
                  assertThat(shard.path("processModelPacket").isNull()).isTrue();
                  assertThat(shard.path("readerKeyBindings")).isEmpty();
                  List<String> gapIds = new ArrayList<>();
                  shard
                      .path("modelIneligibilityGapIds")
                      .forEach(value -> gapIds.add(value.asText()));
                  assertThat(gapIds).isNotEmpty();
                  gapIds.forEach(
                      gapId -> {
                        assertThat(ownedByNoModel.add(gapId)).isTrue();
                        JsonNode processGap = processGapsById.get(gapId);
                        assertThat(processGap).isNotNull();
                        assertThat(processGap.path("gapCode").asText())
                            .isEqualTo("PROCESS_UPSTREAM_MODEL_INELIGIBLE");
                        assertThat(processGap.path("taskShardId").asText()).isEqualTo(shardId);
                      });
                  return;
                }

                assertThat(disposition).isEqualTo("MODEL_SAFE");
                assertThat(shard.path("modelIneligibilityGapIds")).isEmpty();
                JsonNode packet = shard.path("processModelPacket");
                assertThat(packet.path("limitations")).isNotEmpty();
                Map<String, JsonNode> bindingsByKey = new HashMap<>();
                shard
                    .path("readerKeyBindings")
                    .forEach(
                        binding -> bindingsByKey.put(binding.path("readerKey").asText(), binding));
                packet
                    .path("limitations")
                    .forEach(
                        limitation -> {
                          String limitationKey = limitation.path("limitationKey").asText();
                          JsonNode binding = bindingsByKey.get(limitationKey);
                          assertThat(binding).isNotNull();
                          assertThat(binding.path("keyKind").asText()).isEqualTo("LIMITATION");
                          assertThat(binding.path("internalReferenceIds")).hasSize(1);
                          String carrierId = binding.path("internalReferenceIds").get(0).asText();
                          assertThat(referencedBySafeLimitations.add(carrierId)).isTrue();

                          JsonNode processGap = processGapsById.get(carrierId);
                          assertThat(processGap).isNotNull();
                          assertThat(processGap.path("gapCode").asText())
                              .isEqualTo("PROCESS_UPSTREAM_LIMITATION");
                          assertThat(processGap.path("processEvidenceGroupId").asText())
                              .isEqualTo(shard.path("processEvidenceGroupId").asText());
                          assertThat(processGap.path("taskShardId").asText()).isEqualTo(shardId);
                          assertThat(processGap.path("candidateRelationIds"))
                              .isEqualTo(shard.path("ownerCandidateRelationIds"));
                          assertThat(processGap.path("affectedFlowSliceIds"))
                              .isEqualTo(shard.path("contextFlowSliceIds"));

                          JsonNode upstream = processGap.path("upstreamGap");
                          assertThat(upstream.isObject()).isTrue();
                          String upstreamGapId = upstream.path("gapView").path("gapId").asText();
                          assertThat(upstreamGapsById).containsKey(upstreamGapId);
                          assertThat(upstream.path("gapView"))
                              .isEqualTo(upstreamGapsById.get(upstreamGapId));
                          Set<String> expectedSourceFlows =
                              new HashSet<>(upstreamGapFlowsById.get(upstreamGapId));
                          expectedSourceFlows.retainAll(
                              textValues(shard.path("contextFlowSliceIds")));
                          assertThat(textValues(upstream.path("sourceFlowSliceIds")))
                              .containsExactlyInAnyOrderElementsOf(expectedSourceFlows);
                          assertThat(expectedSourceFlows).isNotEmpty();
                        });
              });

      assertThat(allShardIds).hasSize(3);
      Set<String> allProcessGapIds = processGapsById.keySet();
      Set<String> laneUnion = new HashSet<>(ownedByNoModel);
      laneUnion.addAll(referencedBySafeLimitations);
      assertThat(allProcessGapIds).containsExactlyInAnyOrderElementsOf(laneUnion);
      Set<String> laneIntersection = new HashSet<>(ownedByNoModel);
      laneIntersection.retainAll(referencedBySafeLimitations);
      assertThat(laneIntersection).isEmpty();
    }
  }

  private static Object compile(
      ProgramGraphsPublicFixture fixture, ModulePublicationReference m6Publication)
      throws Exception {
    final Class<?> type;
    try {
      type = Class.forName(COMPILER);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("BUSINESS_PROCESS_TASK_COMPILER_NOT_IMPLEMENTED", missing);
    }
    Object compiler = null;
    for (Constructor<?> constructor : type.getConstructors()) {
      if (constructor.getParameterCount() == 1
          && constructor.getParameterTypes()[0].isInstance(fixture.moduleArtifacts())) {
        compiler = constructor.newInstance(fixture.moduleArtifacts());
        break;
      }
    }
    if (compiler == null)
      throw new AssertionError("BUSINESS_PROCESS_TASK_COMPILER_CONSTRUCTOR_INVALID");
    Method method = type.getMethod("compileTasks", ModulePublicationReference.class);
    try {
      return method.invoke(compiler, m6Publication);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "BUSINESS_PROCESS_TASK_COMPILATION_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private static void assertProcessGapLaneAccounting(JsonNode result) {
    List<String> processGapIds = new ArrayList<>();
    result.path("processGaps").forEach(gap -> processGapIds.add(gap.path("gapId").asText()));
    assertThat(new HashSet<>(processGapIds)).hasSize(processGapIds.size());
    assertThat(processGapIds).containsExactlyElementsOf(processGapIds.stream().sorted().toList());

    Set<String> noModelOwned = new HashSet<>();
    Set<String> safeLimitationReferences = new HashSet<>();
    result
        .path("taskShards")
        .forEach(
            shard -> {
              String disposition = shard.path("shardModelDisposition").asText();
              if ("NO_MODEL".equals(disposition)) {
                shard
                    .path("modelIneligibilityGapIds")
                    .forEach(value -> noModelOwned.add(value.asText()));
              } else if ("MODEL_SAFE".equals(disposition)) {
                shard
                    .path("readerKeyBindings")
                    .forEach(
                        binding -> {
                          if ("LIMITATION".equals(binding.path("keyKind").asText())) {
                            binding
                                .path("internalReferenceIds")
                                .forEach(value -> safeLimitationReferences.add(value.asText()));
                          }
                        });
              }
            });

    Set<String> allProcessGapIds = new HashSet<>(processGapIds);
    Set<String> laneUnion = new HashSet<>(noModelOwned);
    laneUnion.addAll(safeLimitationReferences);
    assertThat(allProcessGapIds).containsExactlyInAnyOrderElementsOf(laneUnion);

    Set<String> laneIntersection = new HashSet<>(noModelOwned);
    laneIntersection.retainAll(safeLimitationReferences);
    assertThat(laneIntersection).isEmpty();
  }

  private static void assertDryPacket(JsonNode value) {
    if (value.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String key = field.getKey().toLowerCase(java.util.Locale.ROOT);
        assertThat(key)
            .doesNotContain(
                "artifact", "archive", "receipt", "sha", "proof", "evidence", "locator");
        assertThat(key).doesNotEndWith("id");
        assertDryPacket(field.getValue());
      }
    } else if (value.isArray()) {
      value.forEach(BusinessProcessTaskCompilerTest::assertDryPacket);
    } else if (value.isTextual()) {
      assertThat(value.textValue())
          .doesNotContain("artifact:", "proof:", "evidence:", "sha256", "/private/");
    }
  }

  private static Set<String> textValues(JsonNode values) {
    Set<String> result = new HashSet<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
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

  private static BusinessFlowsReference publishIneligibleBusinessFlows(
      ProgramGraphsPublicFixture fixture) throws Exception {
    return publishBusinessFlowsWithCapsuleBudget(fixture, 1);
  }

  private static BusinessFlowsReference publishBusinessFlowsWithCapsuleBudget(
      ProgramGraphsPublicFixture fixture, int maxCapsuleUtf8Bytes) throws Exception {
    Method profile =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod("capsuleProfile", int.class);
    profile.setAccessible(true);
    CapsuleProjectionProfile capsuleProfile =
        (CapsuleProjectionProfile) profile.invoke(null, maxCapsuleUtf8Bytes);
    Method publish =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod(
            "publishBusinessFlows",
            ProgramGraphsPublicFixture.class,
            CapsuleProjectionProfile.class);
    publish.setAccessible(true);
    return (BusinessFlowsReference) publish.invoke(null, fixture, capsuleProfile);
  }
}
