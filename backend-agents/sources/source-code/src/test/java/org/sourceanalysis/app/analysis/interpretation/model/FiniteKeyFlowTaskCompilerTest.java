package org.sourceanalysis.app.analysis.interpretation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
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
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** M4 contract: R1/R2 tasks may select only frozen, same-Flow provisional keys. */
class FiniteKeyFlowTaskCompilerTest {

  @TempDir Path temporaryDirectory;

  @Test
  void compilesTwoFiniteKeyTasksPerReadyFlowAndPersistsTheClosedR1AndR2Shards() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("finite-key-flow-tasks"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      RegistryProposalTaskSet r0Tasks =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, r0Profile(fixture));
      ModulePublicationReference persistedR0Tasks =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, r0Tasks);
      RegistryProposalExecutionSet r0Execution =
          new RegistryProposalRunner(fixture.moduleArtifacts())
              .runRegistryProposals(
                  persistedR0Tasks,
                  task ->
                      new RegistryProposalProviderResponse(task.expectedRuntime(), response(task)));
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
        JsonNode input =
            new CanonicalJsonCodec().parseCanonical((ImmutableBytes) property(task, "inputJson"));
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
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName()).isEqualTo("flow-task-set.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("FLOW_INTERPRETATION_FLOW_TASK_SET");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo("flow-interpretation-flow-task-set-v4");
              });
    }
  }

  @Test
  void retainsTheFullOwningPublicCapsuleInEveryR1AndR2Task() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("finite-key-flow-public-signals"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedAnalysisStepPublication publicStep =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      assertThat(publicStep.semanticPayloads()).hasSize(5);
      var flowPayload =
          publicStep.semanticPayloads().stream()
              .filter(value -> "flow-slices.json".equals(value.descriptor().fileName()))
              .findFirst()
              .orElseThrow();
      assertThat(flowPayload.descriptor().schemaVersion())
          .isEqualTo("business-flows-flow-slices-v4");
      JsonNode flowDocument = canonicalJson.parseCanonical(flowPayload.canonicalUtf8());
      assertThat(flowDocument.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-slices-v4");
      Map<String, JsonNode> flows = jsonNodesById(flowDocument.path("flowSlices"), "flowSliceId");
      assertThat(flows).hasSize(2);

      var capsulePayload =
          publicStep.semanticPayloads().stream()
              .filter(value -> "evidence-capsules.jsonl".equals(value.descriptor().fileName()))
              .findFirst()
              .orElseThrow();
      assertThat(capsulePayload.descriptor().schemaVersion())
          .isEqualTo("business-flows-evidence-capsule-v6");
      Map<String, JsonNode> capsules =
          jsonNodesById(jsonLines(capsulePayload.canonicalUtf8()), "flowSliceId");
      assertThat(capsules).hasSize(2);
      assertThat(capsules.keySet()).containsExactlyInAnyOrderElementsOf(flows.keySet());
      for (Map.Entry<String, JsonNode> entry : capsules.entrySet()) {
        JsonNode capsule = entry.getValue();
        JsonNode flow = flows.get(entry.getKey());
        JsonNode signals = capsule.path("processJoinSignals");
        assertThat(signals.isArray()).isTrue();
        assertThat(signals).hasSize(4);
        assertThat(jsonStrings(signals, "signalKind"))
            .containsExactlyInAnyOrder(
                "EXPLICIT_CALL", "EXPLICIT_CALL", "JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP");
        assertThat(canonicalBytes(canonicalJson, signals))
            .containsExactly(canonicalBytes(canonicalJson, flow.path("processJoinSignals")));
        Set<String> signalIds = new HashSet<>(jsonStrings(signals, "processJoinSignalId"));
        for (JsonNode signal : signals) {
          assertThat(signal.size()).isEqualTo(14);
          assertThat(signal.path("flowSliceId").asText()).isEqualTo(entry.getKey());
        }

        List<String> spanIds = jsonStrings(capsule.path("modelEvidenceSpanIds"));
        List<String> embeddedSpanIds = jsonStrings(capsule.path("modelEvidenceSpans"), "spanId");
        assertThat(spanIds).isNotEmpty();
        assertThat(embeddedSpanIds).containsExactlyElementsOf(spanIds);
        Set<String> supportedSignalIds = new HashSet<>();
        for (JsonNode span : capsule.path("modelEvidenceSpans")) {
          JsonNode supported = span.path("supportedProcessJoinSignalIds");
          assertThat(supported.isArray()).isTrue();
          supportedSignalIds.addAll(jsonStrings(supported));
        }
        assertThat(supportedSignalIds).containsExactlyInAnyOrderElementsOf(signalIds);

        List<String> obligationIds = jsonStrings(capsule.path("projectionObligationIds"));
        List<String> embeddedObligationIds =
            jsonStrings(capsule.path("projectionObligations"), "obligationId");
        assertThat(obligationIds).isNotEmpty();
        assertThat(embeddedObligationIds).containsExactlyElementsOf(obligationIds);
        List<String> basisSignalIds = new java.util.ArrayList<>();
        for (JsonNode obligation : capsule.path("projectionObligations")) {
          List<String> satisfyingSpanIds = jsonStrings(obligation.path("satisfyingSpanIds"));
          assertThat(satisfyingSpanIds).isNotEmpty();
          assertThat(satisfyingSpanIds).allMatch(spanIds::contains);
          if ("PROCESS_JOIN_SIGNAL_BASIS".equals(obligation.path("kind").asText())) {
            basisSignalIds.add(obligation.path("semanticItemId").asText());
          }
        }
        assertThat(basisSignalIds).containsExactlyInAnyOrderElementsOf(signalIds);
      }

      RegistryProposalTaskSet r0Tasks =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, r0Profile(fixture));
      ModulePublicationReference persistedR0Tasks =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, r0Tasks);
      RegistryProposalExecutionSet r0Execution =
          new RegistryProposalRunner(fixture.moduleArtifacts())
              .runRegistryProposals(
                  persistedR0Tasks,
                  task ->
                      new RegistryProposalProviderResponse(task.expectedRuntime(), response(task)));
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
      assertThat(tasks).hasSize(4);
      assertThat(strings(tasks, "round")).containsExactlyInAnyOrder("R1", "R1", "R2", "R2");
      for (Object task : tasks) {
        String flowSliceId = String.valueOf(property(task, "flowSliceId"));
        JsonNode input = canonicalJson.parseCanonical((ImmutableBytes) property(task, "inputJson"));
        JsonNode capsuleView = input.path("capsuleView");
        assertThat(capsuleView.isObject()).isTrue();
        assertThat(canonicalBytes(canonicalJson, capsuleView))
            .containsExactly(
                canonicalBytes(canonicalJson, modelCapsuleView(capsules.get(flowSliceId))));
        assertThat(property(task, "inputJsonSha256"))
            .isEqualTo(
                new Sha256Digest(
                    digest(((ImmutableBytes) property(task, "inputJson")).copyToByteArray())));
        Set<String> signalIds =
            new HashSet<>(
                jsonStrings(capsuleView.path("processJoinSignals"), "processJoinSignalId"));
        Set<String> supportedSignalIds = new HashSet<>();
        for (JsonNode span : capsuleView.path("modelEvidenceSpans")) {
          JsonNode supported = span.path("supportedProcessJoinSignalIds");
          assertThat(supported.isArray()).isTrue();
          supportedSignalIds.addAll(jsonStrings(supported));
        }
        assertThat(supportedSignalIds).containsExactlyInAnyOrderElementsOf(signalIds);
      }
      assertThat(list(taskSet, "r1ShardReceipts")).singleElement();
      assertThat(list(taskSet, "r2ShardReceipts")).singleElement();

      ModulePublicationReference persistedTasks =
          publish(fixture, businessFlows, persistedRegistry, taskSet);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(persistedTasks);
      JsonNode stored = canonicalJson.parseCanonical(reopened.payloads().get(0).canonicalUtf8());
      assertThat(stored.path("schemaVersion").asText())
          .isEqualTo("flow-interpretation-flow-task-set-v4");
      JsonNode storedTasks = stored.path("payload").path("tasks");
      assertThat(storedTasks.isArray()).isTrue();
      assertThat(storedTasks).hasSize(4);
      for (JsonNode storedTask : storedTasks) {
        String flowSliceId = storedTask.path("flowSliceId").asText();
        JsonNode storedInput = storedTask.path("inputJson");
        assertThat(storedInput.isObject()).isTrue();
        assertThat(canonicalBytes(canonicalJson, storedInput.path("capsuleView")))
            .containsExactly(
                canonicalBytes(canonicalJson, modelCapsuleView(capsules.get(flowSliceId))));
        assertThat(storedTask.path("inputJsonSha256").asText())
            .isEqualTo(digest(canonicalBytes(canonicalJson, storedInput)));
      }
    }
  }

  @Test
  void rejectsRegistryDecoysThatCrossRunControlsOrCurrentBusinessFlowsLineage() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("finite-key-registry-lineage"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(fixture);
      ModulePublicationReference registryPublication = publishRegistry(fixture, businessFlows);
      ReopenedModulePublication originalRegistry =
          fixture.moduleArtifacts().reopen(registryPublication);
      assertThat(originalRegistry.payloads()).hasSize(1);
      FlowModelTaskProfile taskProfile = (FlowModelTaskProfile) profile(fixture);

      RegistryDecoy wrongRun = wrongRunDecoy(originalRegistry);
      RegistryDecoy wrongControls = wrongControlsDecoy(originalRegistry);
      RegistryDecoy wrongBusinessFlowsUpstream = wrongBusinessFlowsUpstreamDecoy(originalRegistry);
      Path wrongRunRoot = temporaryDirectory.resolve("registry-lineage-wrong-run");
      Path wrongControlsRoot = temporaryDirectory.resolve("registry-lineage-wrong-controls");
      Path wrongBusinessFlowsRoot =
          temporaryDirectory.resolve("registry-lineage-wrong-business-flows");
      Files.createDirectory(wrongRunRoot);
      Files.createDirectory(wrongControlsRoot);
      Files.createDirectory(wrongBusinessFlowsRoot);

      try (RunStoreHandle wrongRunHandle = RunStoreBootstrap.openForTest(wrongRunRoot);
          RunStoreHandle wrongControlsHandle = RunStoreBootstrap.openForTest(wrongControlsRoot);
          RunStoreHandle wrongBusinessFlowsHandle =
              RunStoreBootstrap.openForTest(wrongBusinessFlowsRoot)) {
        CanonicalArtifactPolicyRegistry policies = fixture.artifactPolicies();
        ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
        CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
        CanonicalModuleArtifactStore wrongRunStore =
            new FileSystemCanonicalModuleArtifactStore(
                wrongRunHandle, canonicalJson, policies, limits);
        CanonicalModuleArtifactStore wrongControlsStore =
            new FileSystemCanonicalModuleArtifactStore(
                wrongControlsHandle, canonicalJson, policies, limits);
        CanonicalModuleArtifactStore wrongBusinessFlowsStore =
            new FileSystemCanonicalModuleArtifactStore(
                wrongBusinessFlowsHandle, canonicalJson, policies, limits);
        ModulePublicationReference wrongRunPublication =
            installDecoy(wrongRunStore, originalRegistry, wrongRun);
        ModulePublicationReference wrongControlsPublication =
            installDecoy(wrongControlsStore, originalRegistry, wrongControls);
        ModulePublicationReference wrongBusinessFlowsPublication =
            installDecoy(wrongBusinessFlowsStore, originalRegistry, wrongBusinessFlowsUpstream);

        assertAll(
            () ->
                assertRegistryLineageRejected(
                    "wrong run",
                    fixture,
                    businessFlows,
                    wrongRunStore,
                    wrongRunPublication,
                    taskProfile),
            () ->
                assertRegistryLineageRejected(
                    "wrong controls",
                    fixture,
                    businessFlows,
                    wrongControlsStore,
                    wrongControlsPublication,
                    taskProfile),
            () ->
                assertRegistryLineageRejected(
                    "wrong current BusinessFlows upstream",
                    fixture,
                    businessFlows,
                    wrongBusinessFlowsStore,
                    wrongBusinessFlowsPublication,
                    taskProfile));
      }
    }
  }

  @Test
  void publishesZeroFlowCompilationAsZeroR0R1AndR2TasksWithExplicitEmptyDenominators()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("finite-key-zero-flow-tasks"))) {
      BusinessFlowsReference businessFlows =
          RegistryProposalTaskCompilerTest.publishBusinessFlows(
              fixture, zeroFlowProfile(), zeroCapsuleProfile());
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      ReopenedAnalysisStepPublication publicStep =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      assertThat(publicStep.semanticPayloads()).hasSize(5);
      Map<String, org.sourceanalysis.app.artifact.ArtifactDescriptor> publicDescriptors =
          new HashMap<>();
      Map<String, ImmutableBytes> publicBytes = new HashMap<>();
      publicStep
          .semanticPayloads()
          .forEach(
              payload -> {
                assertThat(
                        publicDescriptors.put(
                            payload.descriptor().fileName(), payload.descriptor()))
                    .isNull();
                assertThat(
                        publicBytes.put(payload.descriptor().fileName(), payload.canonicalUtf8()))
                    .isNull();
              });

      var flowPayload = semanticPayload(publicStep, "flow-slices.json");
      assertThat(flowPayload.descriptor().schemaVersion())
          .isEqualTo("business-flows-flow-slices-v4");
      JsonNode flowDocument = canonicalJson.parseCanonical(flowPayload.canonicalUtf8());
      assertThat(flowDocument.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-slices-v4");
      assertEmptyArray(flowDocument.path("flowSlices"));

      var capsulePayload = semanticPayload(publicStep, "evidence-capsules.jsonl");
      assertThat(capsulePayload.descriptor().schemaVersion())
          .isEqualTo("business-flows-evidence-capsule-v6");
      assertThat(capsulePayload.canonicalUtf8().size()).isZero();

      List<JsonNode> dispositions =
          jsonLines(semanticPayload(publicStep, "entry-dispositions.jsonl").canonicalUtf8());
      assertThat(dispositions).hasSize(2);
      assertThat(dispositions)
          .allSatisfy(
              disposition -> {
                assertThat(disposition.path("disposition").asText()).isEqualTo("GAP");
                assertThat(disposition.path("flowSliceId").isNull()).isTrue();
                assertThat(disposition.path("reasonCode").asText()).isNotBlank();
                assertThat(disposition.path("gapIds")).isNotEmpty();
              });
      Set<String> gappedEntryIds =
          dispositions.stream()
              .map(value -> value.path("entryId").asText())
              .collect(java.util.stream.Collectors.toSet());
      assertThat(gappedEntryIds).hasSize(2);

      List<JsonNode> flowGaps =
          jsonLines(semanticPayload(publicStep, "flow-gaps.jsonl").canonicalUtf8());
      assertThat(flowGaps).isNotEmpty();
      Map<String, JsonNode> flowGapsById = jsonNodesById(flowGaps, "gapId");
      Set<String> flowGapIds =
          flowGaps.stream()
              .map(value -> value.path("gapId").asText())
              .collect(java.util.stream.Collectors.toSet());
      assertThat(flowGapIds).doesNotContain("");
      for (JsonNode disposition : dispositions) {
        List<String> dispositionGapIds = jsonStrings(disposition.path("gapIds"));
        assertThat(dispositionGapIds).hasSize(1);
        JsonNode gap = flowGapsById.get(dispositionGapIds.get(0));
        assertThat(gap).isNotNull();
        assertThat(gap.path("scope").asText()).isEqualTo("FLOW");
        assertThat(gap.path("originKind").asText()).isEqualTo("FLOW_COMPILATION");
        assertThat(gap.path("originGapLedgerRef").isNull()).isTrue();
        assertThat(jsonStrings(gap.path("affectedSemanticIds")))
            .containsExactly(disposition.path("entryId").asText());
      }

      var coveragePayload = semanticPayload(publicStep, "flow-coverage.json");
      JsonNode coverage = canonicalJson.parseCanonical(coveragePayload.canonicalUtf8());
      assertThat(coverage.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-coverage-v1");
      assertThat(jsonStrings(coverage.path("entryIds"))).hasSize(2);
      assertThat(jsonStrings(coverage.path("gappedEntryIds")))
          .containsExactlyInAnyOrderElementsOf(gappedEntryIds);
      assertThat(jsonStrings(coverage.path("compiledEntryIds"))).isEmpty();
      assertThat(jsonStrings(coverage.path("flowSliceIds"))).isEmpty();
      assertThat(jsonStrings(coverage.path("processJoinSignalIds"))).isEmpty();
      assertThat(jsonStrings(coverage.path("capsuleIds"))).isEmpty();
      assertThat(jsonStrings(coverage.path("modelEligibleFlowSliceIds"))).isEmpty();
      assertThat(jsonStrings(coverage.path("modelIneligibleFlowSliceIds"))).isEmpty();
      assertThat(jsonStrings(coverage.path("modelIneligibilityGapIds"))).isEmpty();
      assertEmptyArray(coverage.path("modelIneligibilityByFlow"));
      assertThat(jsonStrings(coverage.path("gapIds")))
          .containsExactlyElementsOf(flowGapIds.stream().sorted().toList());
      assertThat(coverage.path("closed").asBoolean()).isTrue();

      RegistryProposalTaskSet r0Tasks =
          new RegistryProposalTaskCompiler(fixture.stepArtifacts())
              .compileRegistryProposalTasks(businessFlows, r0Profile(fixture));
      assertThat(r0Tasks.eligibleFlowSliceIds()).isEmpty();
      assertThat(r0Tasks.tasks()).isEmpty();
      assertThat(r0Tasks.taskShardReceipts())
          .singleElement()
          .satisfies(
              shard -> {
                assertThat(shard.denominatorFlowSliceIds()).isEmpty();
                assertThat(shard.outputTaskSpecIds()).isEmpty();
              });
      ModulePublicationReference persistedR0Tasks =
          new RegistryProposalTaskSetModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(businessFlows, r0Tasks);
      AtomicInteger providerCalls = new AtomicInteger();
      RegistryProposalExecutionSet r0Execution =
          new RegistryProposalRunner(fixture.moduleArtifacts())
              .runRegistryProposals(
                  persistedR0Tasks,
                  task -> {
                    providerCalls.incrementAndGet();
                    throw new AssertionError("ZERO_FLOW_PROVIDER_INVOKED");
                  });
      assertThat(providerCalls).hasValue(0);
      assertThat(r0Execution.rounds()).isEmpty();
      assertThat(r0Execution.generationReceipts()).isEmpty();
      assertThat(r0Execution.validatedProposals()).isEmpty();
      assertThat(r0Execution.flowDispositions()).isEmpty();
      ModulePublicationReference persistedR0Execution =
          new RegistryProposalExecutionSetModulePublisher(fixture.moduleArtifacts())
              .publish(persistedR0Tasks, r0Execution);
      RepositoryInterpretationRegistry registry =
          new RepositoryInterpretationRegistryFreezer().freeze(r0Tasks, r0Execution, businessFlows);
      assertThat(registry.eligibleFlowSliceIds()).isEmpty();
      assertThat(registry.items()).isEmpty();
      assertThat(registry.proposalAccounting().readyFlowSliceIds()).isEmpty();
      assertThat(registry.proposalAccounting().gapFlowSliceIds()).isEmpty();
      assertThat(registry.proposalAccounting().failedFlowSliceIds()).isEmpty();
      assertThat(registry.proposalAccounting().acceptedRegistryProposalIds()).isEmpty();
      ModulePublicationReference persistedRegistry =
          new RepositoryInterpretationRegistryModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(persistedR0Tasks, persistedR0Execution, businessFlows, registry);

      Object taskSet = compile(fixture, businessFlows, persistedRegistry, profile(fixture));
      assertThat(list(taskSet, "tasks")).isEmpty();
      assertThat(list(taskSet, "eligibleR1R2FlowSliceIds")).isEmpty();
      assertThat(list(taskSet, "r1ShardReceipts"))
          .singleElement()
          .satisfies(
              shard -> {
                assertThat(list(shard, "denominatorFlowSliceIds")).isEmpty();
                assertThat(list(shard, "outputTaskSpecIds")).isEmpty();
              });
      assertThat(list(taskSet, "r2ShardReceipts"))
          .singleElement()
          .satisfies(
              shard -> {
                assertThat(list(shard, "denominatorFlowSliceIds")).isEmpty();
                assertThat(list(shard, "outputTaskSpecIds")).isEmpty();
              });
      assertThat(property(taskSet, "registryPublicationRef")).isEqualTo(persistedRegistry);

      ModulePublicationReference persistedTasks =
          publish(fixture, businessFlows, persistedRegistry, taskSet);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(persistedTasks);
      assertThat(reopened.receipt().upstreamArtifacts()).hasSize(6);
      JsonNode stored = canonicalJson.parseCanonical(reopened.payloads().get(0).canonicalUtf8());
      assertThat(stored.path("schemaVersion").asText())
          .isEqualTo("flow-interpretation-flow-task-set-v4");
      JsonNode storedPayload = stored.path("payload");
      assertEmptyArray(storedPayload.path("eligibleR1R2FlowSliceIds"));
      assertEmptyArray(storedPayload.path("tasks"));
      assertThat(storedPayload.path("r1ShardReceipts")).hasSize(1);
      assertThat(storedPayload.path("r1ShardReceipts"))
          .singleElement()
          .satisfies(
              shard -> {
                assertEmptyArray(shard.path("denominatorFlowSliceIds"));
                assertEmptyArray(shard.path("outputTaskSpecIds"));
              });
      assertThat(storedPayload.path("r2ShardReceipts")).hasSize(1);
      assertThat(storedPayload.path("r2ShardReceipts"))
          .singleElement()
          .satisfies(
              shard -> {
                assertEmptyArray(shard.path("denominatorFlowSliceIds"));
                assertEmptyArray(shard.path("outputTaskSpecIds"));
              });
      ReopenedAnalysisStepPublication reopenedPublicStep =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      assertThat(reopenedPublicStep.semanticPayloads()).hasSize(publicDescriptors.size());
      for (var payload : reopenedPublicStep.semanticPayloads()) {
        String fileName = payload.descriptor().fileName();
        assertThat(payload.descriptor()).isEqualTo(publicDescriptors.get(fileName));
        assertThat(payload.canonicalUtf8()).isEqualTo(publicBytes.get(fileName));
      }
    }
  }

  private static ModulePublicationReference publishRegistry(
      ProgramGraphsPublicFixture fixture, BusinessFlowsReference businessFlows) {
    RegistryProposalTaskSet r0Tasks =
        new RegistryProposalTaskCompiler(fixture.stepArtifacts())
            .compileRegistryProposalTasks(businessFlows, r0Profile(fixture));
    ModulePublicationReference persistedR0Tasks =
        new RegistryProposalTaskSetModulePublisher(
                fixture.moduleArtifacts(), fixture.stepArtifacts())
            .publish(businessFlows, r0Tasks);
    RegistryProposalExecutionSet r0Execution =
        new RegistryProposalRunner(fixture.moduleArtifacts())
            .runRegistryProposals(
                persistedR0Tasks,
                task ->
                    new RegistryProposalProviderResponse(task.expectedRuntime(), response(task)));
    ModulePublicationReference persistedR0Execution =
        new RegistryProposalExecutionSetModulePublisher(fixture.moduleArtifacts())
            .publish(persistedR0Tasks, r0Execution);
    RepositoryInterpretationRegistry registry =
        new RepositoryInterpretationRegistryFreezer().freeze(r0Tasks, r0Execution, businessFlows);
    return new RepositoryInterpretationRegistryModulePublisher(
            fixture.moduleArtifacts(), fixture.stepArtifacts())
        .publish(persistedR0Tasks, persistedR0Execution, businessFlows, registry);
  }

  private static void assertRegistryLineageRejected(
      String label,
      ProgramGraphsPublicFixture fixture,
      BusinessFlowsReference businessFlows,
      CanonicalModuleArtifactStore decoyStore,
      ModulePublicationReference decoyPublication,
      FlowModelTaskProfile taskProfile) {
    ReopenedModulePublication reopened = decoyStore.reopen(decoyPublication);
    assertThat(reopened.payloads()).hasSize(1);
    Throwable failure =
        catchThrowable(
            () ->
                new FiniteKeyFlowTaskCompiler(decoyStore, fixture.stepArtifacts())
                    .compileFiniteKeyTasks(businessFlows, decoyPublication, taskProfile));
    assertThat(failure)
        .as(label)
        .isInstanceOf(FlowModelTaskException.class)
        .hasMessage("FLOW_INTERPRETATION_INPUT_INVALID");
  }

  private static ModulePublicationReference installDecoy(
      CanonicalModuleArtifactStore store, ReopenedModulePublication original, RegistryDecoy decoy) {
    VerifiedCanonicalPayload originalPayload = original.payloads().get(0);
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalModulePayload payload = rehashRegistryPayload(originalPayload, decoy, canonicalJson);
    var installed =
        store.install(
            new ModuleInstallRequest(
                decoy.address(),
                original.receipt().moduleVersion(),
                decoy.upstreamArtifacts(),
                decoy.controls(),
                original.receipt().status(),
                original.receipt().gapRefs(),
                List.of(payload)));
    ReopenedModulePublication reopened = store.reopen(installed.reference());
    assertThat(reopened.payloads()).hasSize(1);
    JsonNode originalBody =
        canonicalJson.parseCanonical(originalPayload.canonicalUtf8()).path("payload");
    JsonNode decoyBody =
        canonicalJson.parseCanonical(reopened.payloads().get(0).canonicalUtf8()).path("payload");
    assertThat(decoyBody).isEqualTo(originalBody);
    return installed.reference();
  }

  private static RegistryDecoy wrongRunDecoy(ReopenedModulePublication original) {
    AnalysisStepModuleAddress address = registryAddress(original);
    AnalysisRunId wrongRun =
        AnalysisRunId.parse(
            "analysis-run:"
                + digest("registry-lineage-wrong-run".getBytes(StandardCharsets.UTF_8)));
    assertThat(wrongRun).isNotEqualTo(address.runId());
    return new RegistryDecoy(
        new AnalysisStepModuleAddress(
            wrongRun, address.analysisStepKey(), address.moduleNumber(), address.moduleKey()),
        original.receipt().controls(),
        original.receipt().upstreamArtifacts());
  }

  private static RegistryDecoy wrongControlsDecoy(ReopenedModulePublication original) {
    ArtifactControls controls = original.receipt().controls();
    Sha256Digest wrongProfile =
        new Sha256Digest(
            digest("registry-lineage-wrong-controls".getBytes(StandardCharsets.UTF_8)));
    assertThat(wrongProfile).isNotEqualTo(controls.profileSha256());
    return new RegistryDecoy(
        registryAddress(original),
        new ArtifactControls(
            controls.toolchainSha256(),
            wrongProfile,
            controls.schemaBundleSha256(),
            controls.promptBundleSha256(),
            controls.artifactPolicyRegistryRef()),
        original.receipt().upstreamArtifacts());
  }

  private static RegistryDecoy wrongBusinessFlowsUpstreamDecoy(ReopenedModulePublication original) {
    List<ArtifactReference> upstream =
        new java.util.ArrayList<>(original.receipt().upstreamArtifacts());
    int businessFlowsIndex =
        java.util.stream.IntStream.range(0, upstream.size())
            .filter(index -> upstream.get(index).artifactId().value().startsWith("business-flows-"))
            .findFirst()
            .orElseThrow();
    ArtifactReference current = upstream.get(businessFlowsIndex);
    ArtifactReference wrong =
        new ArtifactReference(
            current.artifactId(),
            new Sha256Digest(
                digest(
                    "registry-lineage-wrong-business-flows-upstream"
                        .getBytes(StandardCharsets.UTF_8))));
    assertThat(wrong).isNotEqualTo(current);
    upstream.set(businessFlowsIndex, wrong);
    return new RegistryDecoy(
        registryAddress(original), original.receipt().controls(), List.copyOf(upstream));
  }

  private static AnalysisStepModuleAddress registryAddress(ReopenedModulePublication publication) {
    assertThat(publication.receipt().address()).isInstanceOf(AnalysisStepModuleAddress.class);
    return (AnalysisStepModuleAddress) publication.receipt().address();
  }

  private static CanonicalModulePayload rehashRegistryPayload(
      VerifiedCanonicalPayload original, RegistryDecoy decoy, CanonicalJsonCodec canonicalJson) {
    ObjectNode envelope = (ObjectNode) canonicalJson.parseCanonical(original.canonicalUtf8());
    envelope.set("producer", producerNode(decoy.address(), "v1"));
    envelope.set("upstreamArtifacts", upstreamNode(decoy.upstreamArtifacts()));
    envelope.set("controls", controlsNode(decoy.controls()));
    envelope.remove("artifactId");
    String artifactId =
        "flow-interpretation-repository-registry:"
            + sha256Hex(
                frame("canonical-module-artifact-id-v1"),
                frame(original.descriptor().schemaVersion()),
                frame(original.descriptor().artifactType()),
                frame(canonicalJson.encodeCanonical(envelope).copyToByteArray()));
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        original.descriptor().fileName(),
        original.descriptor().artifactType(),
        original.descriptor().schemaVersion(),
        ArtifactId.parse(artifactId),
        original.descriptor().mediaType(),
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode producerNode(AnalysisStepModuleAddress address, String moduleVersion) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    producer.set("address", addressNode(address));
    producer.put("moduleVersion", moduleVersion);
    return producer;
  }

  private static ObjectNode addressNode(AnalysisStepModuleAddress address) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("kind", "ANALYSIS_STEP")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue())
        .put("moduleNumber", address.moduleNumber())
        .put("moduleKey", address.moduleKey());
  }

  private static ArrayNode upstreamNode(List<ArtifactReference> references) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    references.forEach(
        reference ->
            result
                .addObject()
                .put("artifactId", reference.artifactId().value())
                .put("sha256", reference.sha256().value()));
    return result;
  }

  private static ObjectNode controlsNode(ArtifactControls controls) {
    ObjectNode result =
        JsonNodeFactory.instance
            .objectNode()
            .put("toolchainSha256", controls.toolchainSha256().value())
            .put("profileSha256", controls.profileSha256().value())
            .put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      result.putNull("promptBundleSha256");
    } else {
      result.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    result
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return result;
  }

  private static String sha256Hex(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException unavailable) {
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

  private record RegistryDecoy(
      AnalysisStepModuleAddress address,
      ArtifactControls controls,
      List<ArtifactReference> upstreamArtifacts) {
    private RegistryDecoy {
      upstreamArtifacts = List.copyOf(upstreamArtifacts);
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
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.model.FlowModelTaskProfile");
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
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.model.FlowModelTaskProfile");
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
      return type.getConstructor(
              String.class,
              String.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ModelRuntimeIdentityV1.class,
              ArtifactReference.class,
              String.class,
              String.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ArtifactReference.class,
              ModelRuntimeIdentityV1.class,
              ArtifactReference.class,
              int.class,
              int.class,
              int.class,
              int.class)
          .newInstance(
              "adapter-fixture-r1",
              "auth-fixture-r1",
              prompt,
              schema,
              runtime,
              new ModelRuntimeIdentityV1(
                  "provider-fixture-r1", "model-fixture-r1", "none", "read-only"),
              budget,
              "adapter-fixture-r2",
              "auth-fixture-r2",
              prompt,
              schema,
              runtime,
              new ModelRuntimeIdentityV1(
                  "provider-fixture-r2", "model-fixture-r2", "none", "read-only"),
              budget,
              16,
              4096,
              16,
              16);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("FINITE_KEY_FLOW_TASK_COMPILER_NOT_IMPLEMENTED", missing);
    }
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
    proposal
        .putArray("basisAtomIds")
        .add(input.at("/capsuleView/registryProposalBasisAtomIds/0").asText());
    proposal.putArray("basisGapIds");
    proposal.putNull("sourceSeedKey");
    return new CanonicalJsonCodec().encodeCanonical(response);
  }

  private static List<?> list(Object source, String method) {
    Object value = property(source, method);
    if (!(value instanceof List<?> values))
      throw new AssertionError("FLOW_MODEL_TASK_SET_SHAPE_INVALID");
    return values;
  }

  private static List<String> strings(List<?> source, String method) {
    return source.stream().map(value -> String.valueOf(property(value, method))).toList();
  }

  private static List<JsonNode> jsonLines(ImmutableBytes bytes) {
    String text = new String(bytes.copyToByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(text).isNotBlank().endsWith("\n");
    return text.lines()
        .map(
            line ->
                new CanonicalJsonCodec()
                    .parseCanonical(
                        ImmutableBytes.copyOf(
                            line.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
        .toList();
  }

  private static org.sourceanalysis.app.artifact.VerifiedCanonicalPayload semanticPayload(
      ReopenedAnalysisStepPublication publication, String fileName) {
    return publication.semanticPayloads().stream()
        .filter(value -> fileName.equals(value.descriptor().fileName()))
        .findFirst()
        .orElseThrow();
  }

  private static FlowCompilationProfile zeroFlowProfile() {
    return new FlowCompilationProfile(
        RegistryProposalTaskCompilerTest.reference("flow-profile", "registry-proposal-tasks"),
        16,
        8,
        1,
        96,
        32,
        64,
        256);
  }

  private static CapsuleProjectionProfile zeroCapsuleProfile() {
    return new CapsuleProjectionProfile(
        RegistryProposalTaskCompilerTest.reference("capsule-profile", "registry-proposal-tasks"),
        16,
        32,
        4_096,
        24_576);
  }

  private static List<String> jsonStrings(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    return java.util.stream.StreamSupport.stream(values.spliterator(), false)
        .map(JsonNode::asText)
        .toList();
  }

  private static void assertEmptyArray(JsonNode value) {
    assertThat(value.isArray()).isTrue();
    assertThat(value).hasSize(0);
  }

  private static List<String> jsonStrings(JsonNode values, String fieldName) {
    assertThat(values.isArray()).isTrue();
    return java.util.stream.StreamSupport.stream(values.spliterator(), false)
        .map(value -> value.path(fieldName).asText())
        .toList();
  }

  private static Map<String, JsonNode> jsonNodesById(List<JsonNode> values, String property) {
    Map<String, JsonNode> result = new HashMap<>();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          String id = value.path(property).asText();
          assertThat(id).isNotBlank();
          assertThat(result.put(id, value)).isNull();
        });
    return Map.copyOf(result);
  }

  private static Map<String, JsonNode> jsonNodesById(JsonNode values, String property) {
    assertThat(values.isArray()).isTrue();
    List<JsonNode> items = new java.util.ArrayList<>();
    values.forEach(items::add);
    return jsonNodesById(items, property);
  }

  private static byte[] canonicalBytes(CanonicalJsonCodec codec, JsonNode value) {
    return codec.encodeCanonical(value).copyToByteArray();
  }

  private static JsonNode modelCapsuleView(JsonNode capsule) {
    ObjectNode copy = (ObjectNode) capsule.deepCopy();
    for (JsonNode fact : copy.path("factViews")) {
      assertThat(fact.isObject()).isTrue();
      ((ObjectNode) fact).remove("originFactArtifactRef");
    }
    for (JsonNode gap : copy.path("gapViews")) {
      assertThat(gap.isObject()).isTrue();
      ((ObjectNode) gap).remove("originKind");
      ((ObjectNode) gap).remove("originGapLedgerRef");
    }
    return copy;
  }

  private static Object property(Object target, String method) {
    try {
      Method accessor = target.getClass().getMethod(method);
      return accessor.invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("FLOW_MODEL_TASK_SET_SHAPE_INVALID", failure);
    }
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
