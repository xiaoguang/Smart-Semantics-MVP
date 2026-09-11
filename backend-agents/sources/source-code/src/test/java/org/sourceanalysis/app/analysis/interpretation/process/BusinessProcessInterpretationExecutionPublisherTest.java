package org.sourceanalysis.app.analysis.interpretation.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompilerTest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** M8 aggregate contracts for mixed and multi-safe shard sets. */
class BusinessProcessInterpretationExecutionPublisherTest {

  private static final String PUBLISHER =
      "org.sourceanalysis.app.analysis.interpretation.process.BusinessProcessInterpretationExecutionPublisher";

  @TempDir Path temporaryDirectory;

  @Test
  void publishesOneModelSafeAndTwoNoModelShardsAsOneClosedM8Result() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithChainedJavaCalls(
            temporaryDirectory.resolve("m8-mixed-shard-execution"))) {
      BusinessFlowsReference flows = publishBusinessFlowsWithCapsuleBudget(fixture, 350);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 2);
      ModulePublicationReference m6 =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);
      BusinessProcessTaskCompilation planned =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6);

      assertThat(planned.taskShards()).hasSize(3);
      assertThat(
              planned.taskShards().stream()
                  .filter(BusinessProcessInterpretationExecutionPublisherTest::isModelSafe)
                  .count())
          .isEqualTo(1);
      assertThat(
              planned.taskShards().stream()
                  .filter(BusinessProcessInterpretationExecutionPublisherTest::isNoModel)
                  .count())
          .isEqualTo(2);
      Set<String> plannedOwners = new HashSet<>();
      planned
          .taskShards()
          .forEach(shard -> plannedOwners.addAll(shard.ownerCandidateRelationIds()));
      assertThat(plannedOwners).hasSize(3);

      CrossFlowCandidateCompilerTest.ProcessInputs inputs =
          CrossFlowCandidateCompilerTest.processInputs(fixture);
      ModulePublicationReference m7 = publishM7Fixture(fixture, m6, planned);
      AtomicInteger calls = new AtomicInteger();
      CanonicalJsonCodec codec = new CanonicalJsonCodec();
      ProcessModelProvider provider =
          request -> {
            int ordinal = calls.getAndIncrement();
            JsonNode application = codec.parseCanonical(request.canonicalApplicationRequestJson());
            if (ordinal == 0) {
              assertThat(application.path("requestKind").asText())
                  .isEqualTo("PROCESS_P1_HYPOTHESIS_REQUEST");
              assertThat(application.path("packet").path("flowCards")).hasSize(2);
              assertThat(application.path("packet").path("relationCards")).hasSize(1);
              return new ProcessModelProviderResponse(inputs.runtime(), p1HypothesisResponse());
            }
            assertThat(ordinal).isEqualTo(1);
            assertThat(application.path("requestKind").asText())
                .isEqualTo("PROCESS_P2_PRECISION_REVIEW_REQUEST");
            assertThat(application.path("acceptedHypotheses")).hasSize(1);
            assertThat(application.at("/acceptedHypotheses/0/hypothesisKey").asText())
                .isEqualTo("H01");
            assertThat(strings(application.at("/acceptedHypotheses/0/claimKeys")))
                .containsExactly("PC01");
            return new ProcessModelProviderResponse(inputs.runtime(), p2KeepResponse());
          };

      ModulePublicationReference checkpoint =
          invokeExecutionPublisher(fixture, m7, inputs, provider);

      assertThat(calls).hasValue(2);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(checkpoint);
      assertThat(reopened.receipt().address())
          .satisfies(
              address -> {
                assertThat(address)
                    .isInstanceOf(org.sourceanalysis.app.artifact.AnalysisStepModuleAddress.class);
                org.sourceanalysis.app.artifact.AnalysisStepModuleAddress stepAddress =
                    (org.sourceanalysis.app.artifact.AnalysisStepModuleAddress) address;
                assertThat(stepAddress.analysisStepKey())
                    .isEqualTo(AnalysisStepKey.FLOW_INTERPRETATION);
                assertThat(stepAddress.moduleNumber()).isEqualTo(8);
                assertThat(stepAddress.moduleKey())
                    .isEqualTo("business-process-interpretation-runner");
              });
      assertThat(reopened.receipt().status().name()).isEqualTo("SUCCEEDED_WITH_GAPS");
      assertThat(reopened.receipt().gapRefs())
          .containsExactlyElementsOf(
              planned.taskShards().stream()
                  .filter(BusinessProcessInterpretationExecutionPublisherTest::isNoModel)
                  .flatMap(shard -> shard.modelIneligibilityGapIds().stream())
                  .distinct()
                  .sorted()
                  .toList());

      JsonNode payload =
          codec.parseCanonical(reopened.payloads().get(0).canonicalUtf8()).path("payload");
      assertThat(payload.path("schemaVersion").asText())
          .isEqualTo("flow-interpretation-process-interpretation-checkpoint-set-v1");
      assertThat(payload.path("artifactType").asText())
          .isEqualTo("FLOW_INTERPRETATION_PROCESS_INTERPRETATION_CHECKPOINT_SET");
      JsonNode taskPublicationRef = payload.path("businessProcessTaskPublicationRef");
      assertThat(taskPublicationRef.isObject()).isTrue();
      assertThat(taskPublicationRef.size()).isEqualTo(3);
      assertThat(taskPublicationRef.path("moduleArtifactRoot").asText())
          .isEqualTo(m7.moduleArtifactRoot().value());
      assertThat(taskPublicationRef.path("moduleReceiptId").asText())
          .isEqualTo(m7.moduleReceiptId().value());
      assertThat(taskPublicationRef.path("moduleReceiptSha256").asText())
          .isEqualTo(m7.moduleReceiptSha256().value());
      assertThat(taskPublicationRef.has("artifactId")).isFalse();
      assertThat(payload.path("shardResults")).hasSize(3);
      assertThat(payload.path("closed").asBoolean()).isTrue();
      assertThat(payload.path("executionOutcome").asText()).isEqualTo("CLOSED_WITH_GAPS");
      assertThat(payload.path("providerCallCount").asInt()).isEqualTo(2);

      JsonNode accounting = payload.path("accounting");
      assertThat(accounting.path("taskShardCount").asInt()).isEqualTo(3);
      assertThat(accounting.path("modelSafeShardCount").asInt()).isEqualTo(1);
      assertThat(accounting.path("noModelShardCount").asInt()).isEqualTo(2);
      assertThat(accounting.path("plannedProcessModelTaskCount").asInt()).isEqualTo(2);
      assertThat(accounting.path("actualProcessModelRoundCount").asInt()).isEqualTo(2);
      assertThat(accounting.path("generationReceiptCount").asInt()).isEqualTo(2);
      assertThat(accounting.path("taskDispositionCount").asInt()).isEqualTo(2);
      assertThat(accounting.path("businessProcessHypothesisCount").asInt()).isEqualTo(1);
      assertThat(accounting.path("processInterpretationDispositionCount").asInt()).isEqualTo(3);
      assertThat(accounting.path("providerCallCount").asInt()).isEqualTo(2);
      assertThat(accounting.path("readyForAdmissionCount").asInt()).isEqualTo(1);
      assertThat(accounting.path("gapDispositionCount").asInt()).isZero();
      assertThat(accounting.path("noModelAdmissionPendingCount").asInt()).isEqualTo(2);
      assertThat(accounting.path("failedDispositionCount").asInt()).isZero();

      Map<String, JsonNode> terminals = new HashMap<>();
      Set<String> owners = new HashSet<>();
      payload
          .path("shardResults")
          .forEach(
              terminal -> {
                String shardId = terminal.path("taskShardId").asText();
                assertThat(terminals.put(shardId, terminal)).isNull();
                terminal
                    .path("ownerCandidateRelationIds")
                    .forEach(owner -> assertThat(owners.add(owner.asText())).isTrue());
              });
      assertThat(terminals).hasSize(3);
      assertThat(owners).hasSize(3);

      JsonNode safe =
          terminals.values().stream()
              .filter(value -> "MODEL_SAFE".equals(value.path("shardModelDisposition").asText()))
              .findFirst()
              .orElseThrow();
      assertThat(safe.path("processModelTasks")).hasSize(2);
      assertThat(safe.path("processModelRounds")).hasSize(2);
      assertThat(safe.path("generationReceipts")).hasSize(2);
      assertThat(safe.path("taskDispositions")).hasSize(2);
      assertThat(safe.path("businessProcessHypotheses")).hasSize(1);
      assertThat(safe.path("providerCallCount").asInt()).isEqualTo(2);
      assertThat(safe.path("closed").asBoolean()).isTrue();
      assertThat(safe.at("/processInterpretationDisposition/disposition").asText())
          .isEqualTo("READY_FOR_ADMISSION");
      assertThat(safe.at("/processInterpretationDisposition/gapIds")).isEmpty();

      List<JsonNode> noModel =
          terminals.values().stream()
              .filter(value -> "NO_MODEL".equals(value.path("shardModelDisposition").asText()))
              .toList();
      assertThat(noModel).hasSize(2);
      Map<String, List<String>> plannedGaps = new HashMap<>();
      planned.taskShards().stream()
          .filter(BusinessProcessInterpretationExecutionPublisherTest::isNoModel)
          .forEach(shard -> plannedGaps.put(shard.taskShardId(), shard.modelIneligibilityGapIds()));
      noModel.forEach(
          terminal -> {
            assertThat(terminal.path("processModelTasks")).isEmpty();
            assertThat(terminal.path("processModelRounds")).isEmpty();
            assertThat(terminal.path("generationReceipts")).isEmpty();
            assertThat(terminal.path("taskDispositions")).isEmpty();
            assertThat(terminal.path("businessProcessHypotheses")).isEmpty();
            assertThat(terminal.path("providerCallCount").asInt()).isZero();
            assertThat(terminal.path("closed").asBoolean()).isTrue();
            assertThat(terminal.at("/processInterpretationDisposition/executionKind").asText())
                .isEqualTo("NO_MODEL");
            assertThat(terminal.at("/processInterpretationDisposition/disposition").asText())
                .isEqualTo("NO_MODEL_ADMISSION_PENDING");
            assertThat(strings(terminal.at("/processInterpretationDisposition/gapIds")))
                .containsExactlyElementsOf(plannedGaps.get(terminal.path("taskShardId").asText()));
          });
    }
  }

  @Test
  void closesTwoSafeAndOneNoModelShardOneDryPacketAtATime() throws Exception {
    RelationSelection relationSelection = originalRelationSelection();
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithChainedJavaCalls(
            temporaryDirectory.resolve("m8-all-safe-shards"))) {
      BusinessFlowsReference flows = publishBusinessFlowsWithCapsuleBudget(fixture, 350);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 2);
      CrossFlowCandidateCompilation allSafeCandidateInput =
          withCompilerCreatedSecondSafeRelation(candidates, relationSelection);
      ModulePublicationReference m6 =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(allSafeCandidateInput);
      BusinessProcessTaskCompilation planned =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6);
      assertThat(planned.taskShards()).hasSize(3);
      assertThat(
              planned.taskShards().stream()
                  .filter(BusinessProcessInterpretationExecutionPublisherTest::isModelSafe)
                  .count())
          .isEqualTo(2);
      assertThat(
              planned.taskShards().stream()
                  .filter(BusinessProcessInterpretationExecutionPublisherTest::isNoModel)
                  .count())
          .isEqualTo(1);
      ModulePublicationReference m7 = publishM7Fixture(fixture, m6, planned);
      BusinessProcessTaskShardV1 secondSafe =
          planned.taskShards().stream()
              .filter(BusinessProcessInterpretationExecutionPublisherTest::isModelSafe)
              .filter(
                  shard ->
                      shard.processModelPacket().relationCards().stream()
                          .anyMatch(
                              relation ->
                                  "SECOND_SAFE_SHARD_MARKER".equals(relation.relationUse())))
              .findFirst()
              .orElseThrow();
      BusinessProcessTaskShardV1 remainingNoModel =
          planned.taskShards().stream()
              .filter(BusinessProcessInterpretationExecutionPublisherTest::isNoModel)
              .findFirst()
              .orElseThrow();

      CrossFlowCandidateCompilerTest.ProcessInputs inputs =
          CrossFlowCandidateCompilerTest.processInputs(fixture);
      CanonicalJsonCodec codec = new CanonicalJsonCodec();
      AtomicInteger calls = new AtomicInteger();
      List<JsonNode> requests = new ArrayList<>();
      ProcessModelProvider provider =
          request -> {
            int ordinal = calls.getAndIncrement();
            JsonNode application = codec.parseCanonical(request.canonicalApplicationRequestJson());
            requests.add(application.deepCopy());
            String serialized = application.toString();
            assertThat(serialized)
                .doesNotContain("artifactId", "sha256", "receipt", "proof", "/private/");
            if (ordinal == 0) {
              assertThat(application.path("requestKind").asText())
                  .isEqualTo("PROCESS_P1_HYPOTHESIS_REQUEST");
              assertThat(application.path("packet").path("flowCards")).hasSize(2);
              assertThat(application.path("packet").path("relationCards")).hasSize(1);
              assertThat(serialized).doesNotContain("SECOND_SAFE_SHARD_MARKER", "H01", "PC01");
              return new ProcessModelProviderResponse(inputs.runtime(), p1HypothesisResponse());
            }
            if (ordinal == 1) {
              assertThat(application.path("requestKind").asText())
                  .isEqualTo("PROCESS_P2_PRECISION_REVIEW_REQUEST");
              assertThat(application.path("acceptedHypotheses")).hasSize(1);
              assertThat(application.at("/acceptedHypotheses/0/hypothesisKey").asText())
                  .isEqualTo("H01");
              assertThat(strings(application.at("/acceptedHypotheses/0/claimKeys")))
                  .containsExactly("PC01");
              assertThat(application.path("packet")).isEqualTo(requests.get(0).path("packet"));
              return new ProcessModelProviderResponse(inputs.runtime(), p2KeepResponse());
            }
            assertThat(ordinal).isEqualTo(2);
            assertThat(application.path("requestKind").asText())
                .isEqualTo("PROCESS_P1_HYPOTHESIS_REQUEST");
            assertThat(application.has("acceptedHypotheses")).isFalse();
            assertThat(serialized).contains("SECOND_SAFE_SHARD_MARKER");
            assertThat(serialized).doesNotContain("H01", "PC01");
            assertThat(application.path("packet").path("limitations"))
                .anySatisfy(
                    value -> assertThat(value.path("limitationKey").asText()).isEqualTo("Q01"));
            ObjectNode gap = JsonNodeFactory.instance.objectNode();
            gap.putArray("gapKeys").add("Q01");
            gap.put("responseKind", "P1_GAP");
            return new ProcessModelProviderResponse(
                inputs.runtime(), new CanonicalJsonCodec().encodeCanonical(gap));
          };

      AssertionError currentRed = null;
      ModulePublicationReference checkpoint = null;
      try {
        checkpoint = invokeExecutionPublisher(fixture, m7, inputs, provider);
      } catch (AssertionError failure) {
        currentRed = failure;
      }
      assertThat(currentRed)
          .as(
              "M8 must execute every finite model-safe shard and aggregate two safe plus one no-model terminal")
          .isNull();
      assertThat(calls).hasValue(3);
      assertThat(requests).hasSize(3);
      assertThat(requests.get(0).path("packet")).isEqualTo(requests.get(1).path("packet"));
      assertThat(requests.get(0).path("packet")).isNotEqualTo(requests.get(2).path("packet"));

      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(checkpoint);
      JsonNode payload =
          codec.parseCanonical(reopened.payloads().get(0).canonicalUtf8()).path("payload");
      assertThat(reopened.receipt().status().name()).isEqualTo("SUCCEEDED_WITH_GAPS");
      assertThat(payload.path("executionOutcome").asText()).isEqualTo("CLOSED_WITH_GAPS");
      assertThat(payload.path("providerCallCount").asInt()).isEqualTo(3);
      assertThat(payload.path("shardResults")).hasSize(3);
      JsonNode accounting = payload.path("accounting");
      assertThat(accounting.path("taskShardCount").asInt()).isEqualTo(3);
      assertThat(accounting.path("modelSafeShardCount").asInt()).isEqualTo(2);
      assertThat(accounting.path("noModelShardCount").asInt()).isEqualTo(1);
      assertThat(accounting.path("plannedProcessModelTaskCount").asInt()).isEqualTo(4);
      assertThat(accounting.path("actualProcessModelRoundCount").asInt()).isEqualTo(3);
      assertThat(accounting.path("generationReceiptCount").asInt()).isEqualTo(3);
      assertThat(accounting.path("taskDispositionCount").asInt()).isEqualTo(4);
      assertThat(accounting.path("businessProcessHypothesisCount").asInt()).isEqualTo(1);
      assertThat(accounting.path("processInterpretationDispositionCount").asInt()).isEqualTo(3);
      assertThat(accounting.path("providerCallCount").asInt()).isEqualTo(3);
      assertThat(accounting.path("readyForAdmissionCount").asInt()).isEqualTo(1);
      assertThat(accounting.path("gapDispositionCount").asInt()).isEqualTo(1);
      assertThat(accounting.path("noModelAdmissionPendingCount").asInt()).isEqualTo(1);
      assertThat(accounting.path("failedDispositionCount").asInt()).isZero();
      assertThat(reopened.receipt().gapRefs())
          .containsExactlyElementsOf(
              List.of(
                      secondSafe.readerKeyBindings().stream()
                          .filter(binding -> "Q01".equals(binding.readerKey()))
                          .findFirst()
                          .orElseThrow()
                          .internalReferenceIds()
                          .get(0),
                      remainingNoModel.modelIneligibilityGapIds().get(0))
                  .stream()
                  .distinct()
                  .sorted()
                  .toList());
    }
  }

  private RelationSelection originalRelationSelection() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithChainedJavaCalls(
            temporaryDirectory.resolve("m8-all-safe-shards-baseline"))) {
      BusinessFlowsReference flows = publishBusinessFlowsWithCapsuleBudget(fixture, 350);
      ProvenCodeFactsReference facts = publishProvenFacts(fixture);
      ModulePublicationReference registry = publishRegistry(fixture, flows);
      CrossFlowCandidateCompilation candidates =
          CrossFlowCandidateCompilerTest.compileForPublisherWithMaxFlowsPerShard(
              fixture, facts, flows, registry, 2);
      ModulePublicationReference m6 =
          new CrossFlowCandidateModulePublisher(fixture.stepArtifacts(), fixture.moduleArtifacts())
              .publish(candidates);
      BusinessProcessTaskCompilation planned =
          new BusinessProcessTaskCompiler(fixture.moduleArtifacts()).compileTasks(m6);
      String safeRelationId =
          planned.taskShards().stream()
              .filter(BusinessProcessInterpretationExecutionPublisherTest::isModelSafe)
              .findFirst()
              .orElseThrow()
              .ownerCandidateRelationIds()
              .get(0);
      String noModelRelationId =
          planned.taskShards().stream()
              .filter(BusinessProcessInterpretationExecutionPublisherTest::isNoModel)
              .findFirst()
              .orElseThrow()
              .ownerCandidateRelationIds()
              .get(0);
      return new RelationSelection(safeRelationId, noModelRelationId);
    }
  }

  private static CrossFlowCandidateCompilation withCompilerCreatedSecondSafeRelation(
      CrossFlowCandidateCompilation base, RelationSelection selection) {
    ProcessEvidenceGroupV2 group =
        base.processEvidenceGroups().stream()
            .filter(
                value ->
                    value.candidateRelations().stream()
                        .anyMatch(
                            relation ->
                                selection.safeRelationId().equals(relation.candidateRelationId())))
            .findFirst()
            .orElseThrow();
    ProcessCandidateRelationV2 safeRelation =
        group.candidateRelations().stream()
            .filter(value -> selection.safeRelationId().equals(value.candidateRelationId()))
            .findFirst()
            .orElseThrow();
    String duplicateId = selection.safeRelationId() + ":second-safe";
    ProcessCandidateRelationV2 duplicateSafeRelation =
        new ProcessCandidateRelationV2(
            duplicateId,
            safeRelation.leftFlowSliceId(),
            safeRelation.rightFlowSliceId(),
            safeRelation.strongestSignalLevel(),
            safeRelation.direction(),
            "SECOND_SAFE_SHARD_MARKER",
            safeRelation.positivePairBases(),
            safeRelation.counterBases(),
            safeRelation.supportingProcessJoinSignalIds(),
            safeRelation.processSemanticCueIds(),
            safeRelation.counterProcessJoinSignalIds(),
            safeRelation.blockingCounterProcessJoinSignalIds(),
            safeRelation.factIds(),
            safeRelation.proofIds(),
            safeRelation.evidenceNodeIds(),
            safeRelation.sourceLocators(),
            safeRelation.gapIds());
    List<ProcessCandidateRelationV2> selectedRelations =
        group.candidateRelations().stream()
            .filter(value -> !selection.noModelRelationId().equals(value.candidateRelationId()))
            .toList();
    List<ProcessCandidateRelationV2> groupRelations = new ArrayList<>(selectedRelations);
    groupRelations.add(duplicateSafeRelation);
    ProcessEvidenceGroupV2 adjustedGroup =
        new ProcessEvidenceGroupV2(
            group.processEvidenceGroupId(),
            group.groupKind(),
            group.memberFlowSliceIds(),
            groupRelations,
            group.processSemanticCues(),
            group.supportingProcessJoinSignalIds(),
            group.counterProcessJoinSignalIds(),
            group.repositoryInterpretationRegistryItemIds(),
            group.modelEligibility(),
            group.modelIneligibilityGapIds(),
            group.persistedMaterial());
    List<ProcessEvidenceGroupV2> groups =
        base.processEvidenceGroups().stream()
            .map(value -> value == group ? adjustedGroup : value)
            .toList();
    List<ProcessCandidateRelationV2> relations =
        groups.stream().flatMap(value -> value.candidateRelations().stream()).toList();
    ProcessMaterialLimitsV1 baseLimits = base.processMaterialLimits();
    ProcessMaterialLimitsV1 splitRelationLimits =
        new ProcessMaterialLimitsV1(
            baseLimits.maxFlows(),
            1,
            baseLimits.maxSignals(),
            baseLimits.maxRegistryItems(),
            baseLimits.maxInputBytes(),
            baseLimits.maxHypotheses(),
            baseLimits.maxClaimsPerHypothesis(),
            baseLimits.maxReaderSlots());
    CrossFlowCandidateAccountingV1 accounting =
        new CrossFlowCandidateAccountingV1(
            base.flowSliceIds(),
            relations.stream().map(ProcessCandidateRelationV2::candidateRelationId).toList(),
            groups.stream().map(ProcessEvidenceGroupV2::processEvidenceGroupId).toList(),
            base.accounting().counterScopeRelationIds().stream()
                .filter(
                    value ->
                        relations.stream()
                            .anyMatch(relation -> value.equals(relation.candidateRelationId())))
                .toList(),
            base.flowSliceIds().size(),
            relations.size(),
            groups.size(),
            0,
            true);
    return new CrossFlowCandidateCompilation(
        base.compilationId() + ":all-safe-shards",
        base.programGraphsPublicationRef(),
        base.provenCodeFactsPublicationRef(),
        base.businessFlowsPublicationRef(),
        base.repositoryInterpretationRegistryPublicationRef(),
        base.analysisRunRequestRef(),
        base.flowSliceIds(),
        relations,
        groups,
        base.counterScopeIssues(),
        splitRelationLimits,
        accounting,
        true);
  }

  private static boolean isModelSafe(BusinessProcessTaskShardV1 shard) {
    return "MODEL_SAFE".equals(shard.shardModelDisposition());
  }

  private static boolean isNoModel(BusinessProcessTaskShardV1 shard) {
    return "NO_MODEL".equals(shard.shardModelDisposition());
  }

  private static ModulePublicationReference invokeExecutionPublisher(
      ProgramGraphsPublicFixture fixture,
      ModulePublicationReference m7,
      CrossFlowCandidateCompilerTest.ProcessInputs inputs,
      ProcessModelProvider provider)
      throws Exception {
    final Class<?> type;
    try {
      type = Class.forName(PUBLISHER);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError(
          "PROCESS_INTERPRETATION_EXECUTION_PUBLISHER_NOT_IMPLEMENTED", missing);
    }
    Object publisher = null;
    for (Constructor<?> constructor : type.getConstructors()) {
      if (constructor.getParameterCount() != 2) continue;
      Object[] values = {fixture.moduleArtifacts(), inputs.reader()};
      if (constructor.getParameterTypes()[0].isInstance(values[0])
          && constructor.getParameterTypes()[1].isInstance(values[1])) {
        publisher = constructor.newInstance(values);
        break;
      }
    }
    if (publisher == null) {
      throw new AssertionError("PROCESS_INTERPRETATION_EXECUTION_PUBLISHER_CONSTRUCTOR_INVALID");
    }
    for (Method method : type.getMethods()) {
      if (!"runAndPublish".equals(method.getName()) || method.getParameterCount() != 2) continue;
      Class<?> requestType = method.getParameterTypes()[0];
      Object request = null;
      for (Constructor<?> constructor : requestType.getDeclaredConstructors()) {
        if (constructor.getParameterCount() != 2) continue;
        if (constructor.getParameterTypes()[0].isInstance(m7)
            && constructor.getParameterTypes()[1].isInstance(inputs.runtime())) {
          constructor.setAccessible(true);
          request = constructor.newInstance(m7, inputs.runtime());
          break;
        }
      }
      if (request == null || !method.getParameterTypes()[1].isInstance(provider)) continue;
      try {
        return (ModulePublicationReference) method.invoke(publisher, request, provider);
      } catch (InvocationTargetException failure) {
        throw new AssertionError(
            "PROCESS_INTERPRETATION_EXECUTION_PUBLISHER_FAILED",
            failure.getCause() == null ? failure : failure.getCause());
      }
    }
    throw new AssertionError("PROCESS_INTERPRETATION_EXECUTION_PUBLISHER_METHOD_INVALID");
  }

  private static ImmutableBytes p1HypothesisResponse() {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    ObjectNode hypothesis = response.putArray("hypotheses").addObject();
    hypothesis.putArray("flowKeys").add("F01").add("F02");
    hypothesis.putArray("relationKeys").add("L01");
    ObjectNode claim = hypothesis.putArray("claims").addObject();
    claim.putArray("basisKeys").add("L01");
    claim.put("claimKind", "TRANSITION");
    claim.putArray("objectKeys").add("F02");
    claim.put("predicate", "MAY_HAND_OFF_TO");
    claim.putArray("subjectKeys").add("F01");
    response.put("responseKind", "P1_HYPOTHESES");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static ImmutableBytes p2KeepResponse() {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("responseKind", "P2_REVIEWS");
    ObjectNode review = response.putArray("reviews").addObject();
    review.put("decision", "KEEP");
    review.put("hypothesisKey", "H01");
    review.putArray("retainedClaimKeys").add("PC01");
    return new CanonicalJsonCodec().encodeCanonical(response);
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

  private static ModulePublicationReference publishM7Fixture(
      ProgramGraphsPublicFixture fixture,
      ModulePublicationReference m6,
      BusinessProcessTaskCompilation compilation)
      throws Exception {
    Method method =
        BusinessProcessInterpretationModulePublisherTest.class.getDeclaredMethod(
            "publishM7Fixture",
            ProgramGraphsPublicFixture.class,
            ModulePublicationReference.class,
            BusinessProcessTaskCompilation.class);
    method.setAccessible(true);
    return (ModulePublicationReference) method.invoke(null, fixture, m6, compilation);
  }

  private static List<String> strings(JsonNode value) {
    assertThat(value.isArray()).isTrue();
    List<String> result = new ArrayList<>();
    value.forEach(item -> result.add(item.asText()));
    return result;
  }

  private record RelationSelection(String safeRelationId, String noModelRelationId) {}
}
