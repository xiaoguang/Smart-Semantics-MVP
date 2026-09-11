package org.sourceanalysis.app.analysis.interpretation.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateEnumerator;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateInputs;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSet;
import org.sourceanalysis.app.analysis.fact.candidates.FactCandidateSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.candidates.FactRegistry;
import org.sourceanalysis.app.analysis.fact.candidates.PersistedFactCandidateInputReader;
import org.sourceanalysis.app.analysis.fact.proofs.AtomicProofBuilder;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSet;
import org.sourceanalysis.app.analysis.fact.proofs.ProofDecisionSetModulePublisher;
import org.sourceanalysis.app.analysis.fact.proofs.ProofRuleRegistry;
import org.sourceanalysis.app.analysis.fact.publish.FactLedgerPublicationSpecifier;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjection;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionModulePublisher;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.capsule.EvidenceCapsuleProjector;
import org.sourceanalysis.app.analysis.flow.compiler.EntryRootedFlowCompiler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** M1 contract: each eligible persisted capsule becomes one isolated, complete R0 task. */
public class RegistryProposalTaskCompilerTest {

  private static final String COMPILER_CLASS =
      "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskCompiler";
  private static final String PROFILE_CLASS =
      "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskProfile";

  @TempDir Path temporaryDirectory;

  @Test
  void compilesOneClosedR0TaskForEachEligiblePersistedCapsuleWithoutProvider() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-tasks"))) {
      BusinessFlowsReference businessFlows = publishBusinessFlows(fixture);
      Set<String> eligibleFlowIds = eligibleFlowIds(fixture, businessFlows);

      Object taskSet = compile(fixture, businessFlows);
      List<?> tasks = listProperty(taskSet, "tasks");

      assertThat(tasks).hasSize(eligibleFlowIds.size());
      assertThat(strings(tasks, "flowSliceId"))
          .containsExactlyInAnyOrderElementsOf(eligibleFlowIds);
      assertThat(strings(tasks, "taskKind")).containsOnly("R0_REGISTRY_PROPOSAL");
      assertThat(strings(tasks, "taskSpecId")).doesNotHaveDuplicates();
      assertThat(strings(tasks, "evidenceCapsuleId")).doesNotHaveDuplicates();
      assertThat(strings(tasks, "isolatedSessionKey")).doesNotHaveDuplicates();
      tasks.forEach(this::assertClosedCapsuleInput);
    }
  }

  @Test
  void compilesFullSignalClosedR0TasksFromFreshReopenedPublicCapsules() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-public-signals"))) {
      BusinessFlowsReference businessFlows = publishBusinessFlows(fixture);
      ReopenedAnalysisStepPublication publication =
          fixture.stepArtifacts().reopen(businessFlows.publication());
      assertThat(publication.semanticPayloads()).hasSize(5);
      CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
      var flowPayload =
          publication.semanticPayloads().stream()
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
      assertThat(flows.values())
          .allSatisfy(
              flow -> {
                JsonNode signals = flow.path("processJoinSignals");
                assertThat(signals.isArray()).isTrue();
                assertThat(signals).hasSize(4);
                assertThat(jsonStrings(signals, "signalKind"))
                    .containsExactlyInAnyOrder(
                        "EXPLICIT_CALL",
                        "EXPLICIT_CALL",
                        "JAVA_TYPE_ANCHOR",
                        "EXTERNAL_EFFECT_GAP");
              });

      var capsulePayload =
          publication.semanticPayloads().stream()
              .filter(value -> "evidence-capsules.jsonl".equals(value.descriptor().fileName()))
              .findFirst()
              .orElseThrow();
      assertThat(capsulePayload.descriptor().schemaVersion())
          .isEqualTo("business-flows-evidence-capsule-v6");
      List<JsonNode> capsules = jsonLines(capsulePayload.canonicalUtf8());
      assertThat(capsules).hasSize(2);
      Map<String, JsonNode> capsulesByFlow = jsonNodesById(toArray(capsules), "flowSliceId");
      assertThat(capsulesByFlow.keySet()).containsExactlyInAnyOrderElementsOf(flows.keySet());
      for (JsonNode capsule : capsules) {
        String flowSliceId = capsule.path("flowSliceId").asText();
        JsonNode flow = flows.get(flowSliceId);
        assertThat(flow).isNotNull();
        JsonNode signals = capsule.path("processJoinSignals");
        assertThat(signals.isArray()).isTrue();
        assertThat(signals).hasSize(4);
        assertThat(jsonStrings(signals, "signalKind"))
            .containsExactlyInAnyOrder(
                "EXPLICIT_CALL", "EXPLICIT_CALL", "JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP");
        assertThat(canonicalBytes(canonicalJson, signals))
            .containsExactly(canonicalBytes(canonicalJson, flow.path("processJoinSignals")));
        List<String> signalIds = jsonStrings(signals, "processJoinSignalId");
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
        for (JsonNode obligation : capsule.path("projectionObligations")) {
          List<String> satisfyingSpanIds = jsonStrings(obligation.path("satisfyingSpanIds"));
          assertThat(satisfyingSpanIds).isNotEmpty();
          assertThat(satisfyingSpanIds).allMatch(spanIds::contains);
        }
      }

      Set<String> eligibleFlowIds = eligibleFlowIds(fixture, businessFlows);
      assertThat(eligibleFlowIds).containsExactlyInAnyOrderElementsOf(flows.keySet());
      Object taskSet = compile(fixture, businessFlows);
      List<?> tasks = listProperty(taskSet, "tasks");
      assertThat(tasks).hasSize(2);
      assertThat(strings(tasks, "flowSliceId"))
          .containsExactlyInAnyOrderElementsOf(eligibleFlowIds);
      for (Object task : tasks) {
        String flowSliceId = property(task, "flowSliceId").toString();
        ImmutableBytes inputBytes = (ImmutableBytes) property(task, "inputJson");
        JsonNode input = canonicalJson.parseCanonical(inputBytes);
        JsonNode capsuleView = input.path("capsuleView");
        assertThat(capsuleView.isObject()).isTrue();
        assertThat(canonicalBytes(canonicalJson, capsuleView))
            .containsExactly(
                canonicalBytes(canonicalJson, modelCapsuleView(capsulesByFlow.get(flowSliceId))));
        assertThat(capsuleView.path("flowSliceId").asText()).isEqualTo(flowSliceId);
        assertThat(property(task, "inputJsonSha256"))
            .isEqualTo(new Sha256Digest(digest(inputBytes.copyToByteArray())));
        JsonNode taskSignals = capsuleView.path("processJoinSignals");
        assertThat(taskSignals.isArray()).isTrue();
        assertThat(taskSignals).hasSize(4);
        assertThat(jsonStrings(taskSignals, "signalKind"))
            .containsExactlyInAnyOrder(
                "EXPLICIT_CALL", "EXPLICIT_CALL", "JAVA_TYPE_ANCHOR", "EXTERNAL_EFFECT_GAP");
        Set<String> taskSignalIds = new HashSet<>(jsonStrings(taskSignals, "processJoinSignalId"));
        Set<String> taskSupportedSignalIds = new HashSet<>();
        for (JsonNode span : capsuleView.path("modelEvidenceSpans")) {
          JsonNode supported = span.path("supportedProcessJoinSignalIds");
          assertThat(supported.isArray()).isTrue();
          taskSupportedSignalIds.addAll(jsonStrings(supported));
        }
        assertThat(taskSupportedSignalIds).containsExactlyInAnyOrderElementsOf(taskSignalIds);
      }
    }
  }

  @Test
  void keepsModelIneligibleFlowsOutOfTheR0DenominatorWithoutDiscardingTheirPublication()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-ineligible"))) {
      BusinessFlowsReference businessFlows = publishBusinessFlows(fixture, capsuleProfile(1));

      Object taskSet = compile(fixture, businessFlows);

      assertThat(listProperty(taskSet, "tasks")).isEmpty();
      assertThat(listProperty(taskSet, "eligibleFlowSliceIds")).isEmpty();
      assertThat(listProperty(taskSet, "taskShardReceipts"))
          .singleElement()
          .satisfies(
              shard -> {
                assertThat(listProperty(shard, "denominatorFlowSliceIds")).isEmpty();
                assertThat(listProperty(shard, "outputTaskSpecIds")).isEmpty();
              });
    }
  }

  @Test
  void persistsTheClosedR0TaskSetBeforeTheProviderRunnerCanReadIt() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("registry-proposal-task-set-module"))) {
      BusinessFlowsReference businessFlows = publishBusinessFlows(fixture);
      RegistryProposalTaskSet taskSet = (RegistryProposalTaskSet) compile(fixture, businessFlows);

      ModulePublicationReference publication = publishTaskSet(fixture, businessFlows, taskSet);
      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(publication);

      assertThat(reopened.receipt().address())
          .isEqualTo(
              new AnalysisStepModuleAddress(
                  businessFlows.publication().address().runId(),
                  AnalysisStepKey.FLOW_INTERPRETATION,
                  1,
                  "registry-task-compiler"));
      assertThat(reopened.payloads())
          .singleElement()
          .satisfies(
              payload -> {
                assertThat(payload.descriptor().fileName())
                    .isEqualTo("registry-proposal-task-set.json");
                assertThat(payload.descriptor().artifactType())
                    .isEqualTo("FLOW_INTERPRETATION_REGISTRY_PROPOSAL_TASK_SET");
                assertThat(payload.descriptor().schemaVersion())
                    .isEqualTo("flow-interpretation-registry-proposal-task-set-v2");
              });
      JsonNode stored =
          new CanonicalJsonCodec().parseCanonical(reopened.payloads().get(0).canonicalUtf8());
      assertThat(stored.at("/payload/eligibleFlowSliceIds"))
          .extracting(JsonNode::asText)
          .containsExactlyElementsOf(taskSet.eligibleFlowSliceIds());
      assertThat(stored.at("/payload/tasks")).hasSize(taskSet.tasks().size());
    }
  }

  private Object compile(ProgramGraphsPublicFixture fixture, BusinessFlowsReference businessFlows)
      throws Exception {
    try {
      Class<?> profileType = Class.forName(PROFILE_CLASS);
      Object profile =
          profileType
              .getConstructor(
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
                  int.class,
                  int.class)
              .newInstance(
                  "adapter-fixture-alpha",
                  "auth-fixture-beta",
                  reference("registry-prompt", "r0-task-prompt"),
                  reference("registry-schema", fixture.artifactControls().schemaBundleSha256()),
                  reference("registry-runtime", fixture.artifactControls().profileSha256()),
                  new ModelRuntimeIdentityV1(
                      "provider-fixture-gamma",
                      "model-fixture-delta",
                      "reasoning-fixture-epsilon",
                      "sandbox-fixture-zeta"),
                  reference("registry-budget", "r0-task-budget"),
                  16,
                  16,
                  4_096,
                  256,
                  1_024);
      Class<?> compilerType = Class.forName(COMPILER_CLASS);
      Object compiler =
          compilerType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
              .newInstance(fixture.stepArtifacts());
      return compilerType
          .getMethod("compileRegistryProposalTasks", BusinessFlowsReference.class, profileType)
          .invoke(compiler, businessFlows, profile);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("REGISTRY_PROPOSAL_TASK_COMPILER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("REGISTRY_PROPOSAL_TASK_COMPILER_FAILED", cause);
    }
  }

  private ModulePublicationReference publishTaskSet(
      ProgramGraphsPublicFixture fixture,
      BusinessFlowsReference businessFlows,
      RegistryProposalTaskSet taskSet)
      throws Exception {
    try {
      Class<?> publisherType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskSetModulePublisher");
      Object publisher =
          publisherType
              .getConstructor(
                  org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore.class,
                  org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore.class)
              .newInstance(fixture.moduleArtifacts(), fixture.stepArtifacts());
      return (ModulePublicationReference)
          publisherType
              .getMethod("publish", BusinessFlowsReference.class, RegistryProposalTaskSet.class)
              .invoke(publisher, businessFlows, taskSet);
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("REGISTRY_PROPOSAL_TASK_SET_PUBLISHER_NOT_IMPLEMENTED", missing);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause() == null ? failure : failure.getCause();
      throw new AssertionError("REGISTRY_PROPOSAL_TASK_SET_PUBLISHER_FAILED", cause);
    }
  }

  private void assertClosedCapsuleInput(Object task) {
    ImmutableBytes input = (ImmutableBytes) property(task, "inputJson");
    JsonNode value = new CanonicalJsonCodec().parseCanonical(input);

    assertThat(value.path("flowSliceId").asText()).isEqualTo(property(task, "flowSliceId"));
    assertThat(value.path("evidenceCapsuleId").asText())
        .isEqualTo(property(task, "evidenceCapsuleId"));
    assertThat(value.path("capsuleView").path("factViews")).isNotEmpty();
    assertThat(value.path("capsuleView").path("outcomePathViews")).isNotEmpty();
    assertThat(value.path("capsuleView").path("modelEvidenceSpans")).isNotEmpty();
    assertThat(value.path("capsuleView").path("projectionObligations")).isNotEmpty();
    assertThat(value.path("permittedProposalKinds"))
        .extracting(JsonNode::asText)
        .containsExactly("BUSINESS_TERM", "CLAIM", "QUESTION");
    assertThat(property(task, "inputJsonSha256"))
        .isEqualTo(new Sha256Digest(digest(input.copyToByteArray())));
  }

  public static BusinessFlowsReference publishBusinessFlows(ProgramGraphsPublicFixture fixture) {
    return publishBusinessFlows(fixture, capsuleProfile());
  }

  static BusinessFlowsReference publishBusinessFlows(
      ProgramGraphsPublicFixture fixture, CapsuleProjectionProfile capsuleProfile) {
    return publishBusinessFlows(fixture, flowProfile(), capsuleProfile);
  }

  public static BusinessFlowsReference publishBusinessFlows(
      ProgramGraphsPublicFixture fixture,
      FlowCompilationProfile flowCompilationProfile,
      CapsuleProjectionProfile capsuleProfile) {
    ProvenCodeFactsReference facts = publishProvenFacts(fixture);
    FlowCompilation compilation =
        new EntryRootedFlowCompiler(fixture.stepArtifacts())
            .compile(
                fixture.applicationDiscovery(),
                fixture.programGraphs(),
                facts,
                flowCompilationProfile);
    ModulePublicationReference flowCompilation =
        new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
            .publish(fixture.applicationDiscovery(), fixture.programGraphs(), facts, compilation);
    CapsuleProjection projection =
        new EvidenceCapsuleProjector(
                fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
            .project(
                flowCompilation,
                fixture.sourceInventory(),
                fixture.programGraphs(),
                facts,
                capsuleProfile);
    ModulePublicationReference capsuleProjection =
        new CapsuleProjectionModulePublisher(
                fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
            .publish(
                flowCompilation,
                fixture.sourceInventory(),
                fixture.programGraphs(),
                facts,
                projection);
    return new FlowPublicationSpecifier(
            fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
        .specify(
            fixture.sourceInventory(),
            fixture.applicationDiscovery(),
            fixture.programGraphs(),
            facts,
            flowCompilation,
            capsuleProjection);
  }

  static ProvenCodeFactsReference publishProvenFacts(ProgramGraphsPublicFixture fixture) {
    FactCandidateInputs inputs =
        new PersistedFactCandidateInputReader(fixture.stepArtifacts(), fixture.sourceReader())
            .reopen(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    FactCandidateSet candidates =
        new FactCandidateEnumerator().enumerate(inputs, FactRegistry.standardJavaBoundary());
    ProofDecisionSet decisions =
        new AtomicProofBuilder(fixture.sourceReader())
            .prove(
                candidates,
                inputs,
                fixture.sourceInventory(),
                ProofRuleRegistry.standardJavaBoundary());
    var candidatePublication =
        new FactCandidateSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 1, "candidates"), inputs, candidates);
    var proofPublication =
        new ProofDecisionSetModulePublisher(fixture.moduleArtifacts())
            .publish(address(fixture, 2, "proofs"), inputs, candidatePublication, decisions);
    return new FactLedgerPublicationSpecifier(fixture.moduleArtifacts(), fixture.stepArtifacts())
        .specifyCandidatesAndProofs(
            inputs,
            candidatePublication,
            proofPublication,
            fixture.sourceInventory(),
            fixture.applicationDiscovery(),
            fixture.programGraphs());
  }

  private Set<String> eligibleFlowIds(
      ProgramGraphsPublicFixture fixture, BusinessFlowsReference businessFlows) {
    ReopenedAnalysisStepPublication publication =
        fixture.stepArtifacts().reopen(businessFlows.publication());
    JsonNode coverage =
        new CanonicalJsonCodec()
            .parseCanonical(
                publication.semanticPayloads().stream()
                    .filter(value -> "flow-coverage.json".equals(value.descriptor().fileName()))
                    .findFirst()
                    .orElseThrow()
                    .canonicalUtf8());
    return java.util.stream.StreamSupport.stream(
            coverage.path("modelEligibleFlowSliceIds").spliterator(), false)
        .map(JsonNode::asText)
        .collect(Collectors.toUnmodifiableSet());
  }

  static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", "registry-proposal-tasks"), 16, 8, 64, 96, 32, 64, 256);
  }

  static CapsuleProjectionProfile capsuleProfile() {
    return capsuleProfile(24_576);
  }

  static CapsuleProjectionProfile capsuleProfile(int maxCapsuleUtf8Bytes) {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", "registry-proposal-tasks"),
        16,
        32,
        4_096,
        maxCapsuleUtf8Bytes);
  }

  private static AnalysisStepModuleAddress address(
      ProgramGraphsPublicFixture fixture, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        fixture.programGraphs().publication().address().runId(),
        AnalysisStepKey.PROVEN_CODE_FACTS,
        moduleNumber,
        moduleKey);
  }

  public static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + digest(value)), new Sha256Digest(digest(value + "-bytes")));
  }

  public static ArtifactReference reference(String prefix, Sha256Digest digest) {
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + digest.value()), digest);
  }

  private static List<?> listProperty(Object target, String property) {
    Object value = property(target, property);
    if (!(value instanceof List<?> values))
      throw new AssertionError("REGISTRY_PROPOSAL_TASK_SHAPE_INVALID");
    return values;
  }

  private static List<String> strings(List<?> values, String property) {
    return values.stream().map(value -> property(value, property).toString()).toList();
  }

  private static List<JsonNode> jsonLines(ImmutableBytes bytes) {
    String text = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    assertThat(text).isNotBlank().endsWith("\n");
    return text.lines()
        .map(
            line ->
                new CanonicalJsonCodec()
                    .parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
        .toList();
  }

  private static List<String> jsonStrings(JsonNode values) {
    assertThat(values.isArray()).isTrue();
    return java.util.stream.StreamSupport.stream(values.spliterator(), false)
        .map(JsonNode::asText)
        .toList();
  }

  private static List<String> jsonStrings(JsonNode values, String fieldName) {
    assertThat(values.isArray()).isTrue();
    return java.util.stream.StreamSupport.stream(values.spliterator(), false)
        .map(value -> value.path(fieldName).asText())
        .toList();
  }

  private static Map<String, JsonNode> jsonNodesById(JsonNode values, String property) {
    Map<String, JsonNode> result = new HashMap<>();
    assertThat(values.isArray()).isTrue();
    values.forEach(
        value -> {
          assertThat(value.isObject()).isTrue();
          String id = value.path(property).asText();
          assertThat(id).isNotBlank();
          assertThat(result.put(id, value)).isNull();
        });
    return Map.copyOf(result);
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

  private static JsonNode toArray(List<JsonNode> values) {
    com.fasterxml.jackson.databind.node.ArrayNode array =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    values.forEach(array::add);
    return array;
  }

  private static Object property(Object target, String property) {
    try {
      return target.getClass().getMethod(property).invoke(target);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("REGISTRY_PROPOSAL_TASK_SHAPE_INVALID", failure);
    }
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
