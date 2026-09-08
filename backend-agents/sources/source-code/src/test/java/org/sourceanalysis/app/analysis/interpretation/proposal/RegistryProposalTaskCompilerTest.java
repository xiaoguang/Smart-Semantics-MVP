package org.sourceanalysis.app.analysis.interpretation.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
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
                  ArtifactReference.class,
                  ArtifactReference.class,
                  ArtifactReference.class,
                  ArtifactReference.class,
                  int.class,
                  int.class,
                  int.class,
                  int.class,
                  int.class)
              .newInstance(
                  reference("registry-prompt", "r0-task-prompt"),
                  reference("registry-schema", fixture.artifactControls().schemaBundleSha256()),
                  reference("registry-runtime", fixture.artifactControls().profileSha256()),
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
    ProvenCodeFactsReference facts = publishProvenFacts(fixture);
    FlowCompilation compilation =
        new EntryRootedFlowCompiler(fixture.stepArtifacts())
            .compile(fixture.applicationDiscovery(), fixture.programGraphs(), facts, flowProfile());
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
    return new FlowPublicationSpecifier(fixture.moduleArtifacts(), fixture.stepArtifacts())
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
        reference("flow-profile", "registry-proposal-tasks"), 16, 8, 64, 96, 32);
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
