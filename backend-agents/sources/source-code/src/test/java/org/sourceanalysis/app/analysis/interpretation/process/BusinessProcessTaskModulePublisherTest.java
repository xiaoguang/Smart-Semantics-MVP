package org.sourceanalysis.app.analysis.interpretation.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/**
 * M7 contract: a dry task-shard plan is receipt-last and must survive a fresh module-store reopen.
 */
class BusinessProcessTaskModulePublisherTest {

  private static final String PUBLISHER =
      "org.sourceanalysis.app.analysis.interpretation.process.BusinessProcessTaskModulePublisher";

  @TempDir Path temporaryDirectory;

  @Test
  void persistsTheDryProcessShardPlanBeforeAnyProcessProviderCall() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m7-task-shard-publication"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(
                  CrossFlowCandidateCompilerTest.compileForPublisher(
                      fixture, facts, flows, registry));
      BusinessProcessTaskCompilation compilation =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6Publication);

      ModulePublicationReference publication = publish(fixture, m6Publication, compilation);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(publication);

      assertThat(reopened.payloads()).hasSize(1);
      assertThat(reopened.payloads().get(0).descriptor().fileName())
          .isEqualTo("process-task-shards.json");
      assertThat(reopened.payloads().get(0).descriptor().artifactType())
          .isEqualTo("FLOW_INTERPRETATION_PROCESS_TASK_SHARDS");
      JsonNode payload =
          new CanonicalJsonCodec()
              .parseCanonical(reopened.payloads().get(0).canonicalUtf8())
              .path("payload");
      assertThat(payload.path("providerCallCount").asInt()).isZero();
      assertThat(payload.path("taskShards")).hasSize(1);
      assertThat(payload.path("taskShards").get(0).path("processModelPacket").path("flowCards"))
          .hasSize(2);
      assertThat(payload.path("taskShards").get(0).path("readerKeyBindings")).isNotEmpty();
    }
  }

  @Test
  void persistsAnAtomicBudgetGapAlongsideTheNoModelShard() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithSharedJavaCall(
            temporaryDirectory.resolve("m7-budget-gap-publication"))) {
      BusinessFlowsReference flows = RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(
                  CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
                      fixture, facts, flows, registry, 1));
      BusinessProcessTaskCompilation compilation =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6Publication);

      ModulePublicationReference publication = publish(fixture, m6Publication, compilation);
      JsonNode payload =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture.moduleArtifacts().reopen(publication).payloads().get(0).canonicalUtf8())
              .path("payload");

      assertThat(payload.path("providerCallCount").asInt()).isZero();
      assertThat(payload.path("taskShards")).hasSize(1);
      assertThat(payload.path("taskShards").get(0).path("shardModelDisposition").asText())
          .isEqualTo("NO_MODEL");
      assertThat(payload.path("processGaps")).hasSize(1);
      assertThat(payload.path("processGaps").get(0).path("gapCode").asText())
          .isEqualTo("PROCESS_TASK_BUDGET_EXCEEDED");
      assertThat(payload.path("processGaps").get(0).path("taskShardId").asText())
          .isEqualTo(payload.path("taskShards").get(0).path("taskShardId").asText());
    }
  }

  @Test
  void persistsFormalShardOwnedUpstreamGapsWithoutModelWork() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithChainedJavaCalls(
            temporaryDirectory.resolve("m7-formal-upstream-gap-publication"))) {
      BusinessFlowsReference flows = publishBusinessFlowsWithCapsuleBudget(fixture, 350);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 2);
      ModulePublicationReference m6Publication =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);
      BusinessProcessTaskCompilation compilation =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6Publication);

      ModulePublicationReference publication = publish(fixture, m6Publication, compilation);
      JsonNode payload =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture.moduleArtifacts().reopen(publication).payloads().get(0).canonicalUtf8())
              .path("payload");

      Map<String, JsonNode> upstreamGapsById = new HashMap<>();
      Map<String, Set<String>> upstreamGapFlowsById = new HashMap<>();
      JsonNode m6Payload =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture.moduleArtifacts().reopen(m6Publication).payloads().get(0).canonicalUtf8())
              .path("payload");
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

      assertThat(payload.path("providerCallCount").asInt()).isZero();
      assertThat(payload.path("taskShards")).hasSize(3);
      Map<String, JsonNode> processGapsById = new HashMap<>();
      List<String> processGapIds = new ArrayList<>();
      payload
          .path("processGaps")
          .forEach(
              gap -> {
                String gapId = gap.path("gapId").asText();
                processGapIds.add(gapId);
                assertThat(processGapsById.put(gapId, gap)).isNull();
              });
      assertThat(processGapIds).containsExactlyElementsOf(processGapIds.stream().sorted().toList());

      Set<String> noModelShardIds = new HashSet<>();
      Set<String> noModelWrapperIds = new HashSet<>();
      payload
          .path("taskShards")
          .forEach(
              shard -> {
                if (!"NO_MODEL".equals(shard.path("shardModelDisposition").asText())) return;
                assertThat(noModelShardIds.add(shard.path("taskShardId").asText())).isTrue();
                assertThat(shard.path("processModelPacket").isNull()).isTrue();
                assertThat(shard.path("readerKeyBindings")).isEmpty();
                assertThat(shard.path("modelIneligibilityGapIds")).isNotEmpty();
                shard
                    .path("modelIneligibilityGapIds")
                    .forEach(
                        gapIdNode -> {
                          String gapId = gapIdNode.asText();
                          JsonNode processGap = processGapsById.get(gapId);
                          assertThat(processGap).as("M7 process Gap " + gapId).isNotNull();
                          assertThat(noModelWrapperIds.add(gapId)).isTrue();
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
                          assertThat(textValues(upstream.path("sourceFlowSliceIds"))).isNotEmpty();
                          assertThat(processGap.path("gapId").asText())
                              .isEqualTo(expectedGapId(processGap));
                        });
              });

      assertThat(noModelShardIds).hasSize(2);
      assertThat(noModelWrapperIds).hasSize(noModelShardIds.size());
    }
  }

  private static ModulePublicationReference publish(
      ProgramGraphsPublicFixture fixture,
      ModulePublicationReference m6Publication,
      BusinessProcessTaskCompilation compilation)
      throws Exception {
    final Class<?> type;
    try {
      type = Class.forName(PUBLISHER);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("BUSINESS_PROCESS_TASK_PUBLISHER_NOT_IMPLEMENTED", missing);
    }
    Object publisher = null;
    for (Constructor<?> constructor : type.getConstructors()) {
      if (constructor.getParameterCount() == 1
          && constructor.getParameterTypes()[0].isInstance(fixture.moduleArtifacts())) {
        publisher = constructor.newInstance(fixture.moduleArtifacts());
        break;
      }
    }
    if (publisher == null)
      throw new AssertionError("BUSINESS_PROCESS_TASK_PUBLISHER_CONSTRUCTOR_INVALID");
    Method method =
        type.getMethod(
            "publish", ModulePublicationReference.class, BusinessProcessTaskCompilation.class);
    try {
      return (ModulePublicationReference) method.invoke(publisher, m6Publication, compilation);
    } catch (InvocationTargetException failure) {
      throw new AssertionError(
          "BUSINESS_PROCESS_TASK_PUBLICATION_FAILED",
          failure.getCause() == null ? failure : failure.getCause());
    }
  }

  private static BusinessFlowsReference publishBusinessFlowsWithCapsuleBudget(
      ProgramGraphsPublicFixture fixture, int maxCapsuleUtf8Bytes) throws Exception {
    Method profile =
        RegistryProposalTaskCompilerTest.class.getDeclaredMethod("capsuleProfile", int.class);
    profile.setAccessible(true);
    Object capsuleProfile = profile.invoke(null, maxCapsuleUtf8Bytes);
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

  private static Set<String> textValues(JsonNode values) {
    Set<String> result = new HashSet<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static String expectedGapId(JsonNode processGap) {
    ObjectNode projection = (ObjectNode) processGap.deepCopy();
    projection.remove("gapId");
    projection.remove("taskShardId");
    return "gap:"
        + sha256(
            frame("flow-interpretation-upstream-model-ineligibility-gap-v1"),
            frame(new CanonicalJsonCodec().encodeCanonical(projection).copyToByteArray()));
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
}
