package org.sourceanalysis.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.document.BusinessReportCheckpointRenderer;
import org.sourceanalysis.app.analysis.document.BusinessReportProfile;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.inventory.CaptureReceiptView;
import org.sourceanalysis.app.analysis.inventory.PersistedVerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.ProfileView;
import org.sourceanalysis.app.analysis.inventory.RegisteredCaptureReceiptProjector;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryExecutionRequest;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryExecutor;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.knowledge.ProcessExplanationProfile;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalEnvelopeKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.PublicContentExposure;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.capture.localgit.LocalGitCaptureRequest;
import org.sourceanalysis.app.capture.localgit.LocalGitCommitCaptureAdapter;
import org.sourceanalysis.app.capture.localgit.LocalGitSourceRegistry;
import org.sourceanalysis.app.capture.localgit.RegisteredSourceCapture;

/** Proves the runtime can run persisted technical analysis after verified source inventory. */
class TechnicalAnalysisWorkflowTest {

  @TempDir Path temporaryDirectory;

  @Test
  void composesDiscoveryGraphsFactsAndFlowsFromOnePersistedSourceInventory() throws Exception {
    CapturedSource captured = capturedSpringRepository();
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = allTechnicalPolicies(canonicalJson);
    ArtifactReference resourceBudget = reference("resource-budget", 'a', 'b');
    ArtifactReference profileBundle = reference("profile-bundle", 'c', 'd');
    ArtifactReference frozenRequest =
        frozenRequest(canonicalJson, captured.capture(), resourceBudget);
    org.sourceanalysis.app.runtime.AnalysisRunRequest queuedRequest =
        queuedRequest(captured.capture(), frozenRequest, policies, resourceBudget, profileBundle);
    Path storeRoot = temporaryDirectory.resolve("run-store");
    java.nio.file.Files.createDirectory(storeRoot);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot)) {
      org.sourceanalysis.app.runtime.AnalysisRunReference queued =
          RunStoreBootstrap.queueAnalysisRun(handle, queuedRequest);
      org.sourceanalysis.app.runtime.PersistedAnalysisRunRequest persisted =
          RunStoreBootstrap.reopenPersistedAnalysisRunRequest(handle, queued.runId());
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24));
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24));
      ArtifactReference runRequestReference =
          new ArtifactReference(
              persisted.analysisRun().analysisRunRequestReference().analysisRunRequestId(),
              persisted.analysisRun().analysisRunRequestReference().sha256());
      CaptureReceiptView receipt =
          new RegisteredCaptureReceiptProjector().project(captured.capture(), frozenRequest);
      VerifiedSourceInventoryReference inventory =
          new VerifiedSourceInventoryExecutor(modules, steps, captured.registry())
              .execute(
                  new VerifiedSourceInventoryExecutionRequest(
                      queued.runId(),
                      runRequestReference,
                      persisted.canonicalJson(),
                      frozenRequest,
                      captured.frozenRequestBytes(),
                      captured.capture().sourceRegistrationRef(),
                      reference("verification-policy", 'e', 'f'),
                      reference("capability-profile", '1', '2'),
                      receipt,
                      new ProfileView(profileBundle, resourceBudget, 32, 1_000_000)));
      PersistedVerifiedSourceTextReader sourceReader =
          new PersistedVerifiedSourceTextReader(steps, captured.registry());
      Class<?> workflowType =
          typeOrNull("org.sourceanalysis.app.runtime.TechnicalAnalysisWorkflow");

      assertThat(workflowType)
          .as("the runtime needs one coordinator rather than a CLI or test assembling each step")
          .isNotNull();
      assertThat(workflowType.getDeclaredConstructors())
          .as("runtime source composition remains path-free")
          .allSatisfy(
              constructor ->
                  assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));

      Object workflow =
          workflowType
              .getConstructor(
                  org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader.class,
                  CanonicalModuleArtifactStore.class,
                  CanonicalAnalysisStepArtifactStore.class)
              .newInstance(sourceReader, modules, steps);
      Object result =
          workflowType
              .getMethod(
                  "run",
                  org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
                  DiscoveryProfile.class,
                  ArtifactReference.class,
                  org.sourceanalysis.app.artifact.ArtifactControls.class,
                  FlowCompilationProfile.class,
                  CapsuleProjectionProfile.class)
              .invoke(
                  workflow,
                  inventory,
                  new DiscoveryProfile("application-discovery-v2"),
                  reference("graph-profile", 'a', 'b'),
                  controlsFor(policies, queuedRequest),
                  new FlowCompilationProfile(
                      reference("flow-profile", 'c', 'd'), 16, 8, 64, 96, 32, 64, 256),
                  new CapsuleProjectionProfile(
                      reference("capsule-profile", 'e', 'f'), 16, 32, 4_096, 24_576));

      Object flows = result.getClass().getMethod("businessFlows").invoke(result);
      assertThat(
              steps
                  .reopen(
                      ((org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference) flows)
                          .publication())
                  .semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
    }
  }

  @Test
  void executesDiscoveryFirstAndCanThenContinueTheCompleteTechnicalPrefixFromAPersistedRun()
      throws Exception {
    CapturedSource captured = capturedSpringRepository();
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = allTechnicalPolicies(canonicalJson);
    ArtifactReference resourceBudget = reference("resource-budget", 'a', 'b');
    ArtifactReference profileBundle = reference("profile-bundle", 'c', 'd');
    ArtifactReference frozenRequest =
        frozenRequest(canonicalJson, captured.capture(), resourceBudget);
    org.sourceanalysis.app.runtime.AnalysisRunRequest queuedRequest =
        queuedRequest(captured.capture(), frozenRequest, policies, resourceBudget, profileBundle);
    Path storeRoot = temporaryDirectory.resolve("persisted-run-store");
    java.nio.file.Files.createDirectory(storeRoot);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot)) {
      org.sourceanalysis.app.runtime.AnalysisRunReference queued =
          RunStoreBootstrap.queueAnalysisRun(handle, queuedRequest);
      Class<?> configurationType =
          typeOrNull("org.sourceanalysis.app.runtime.PersistedTechnicalRunConfiguration");
      Class<?> executorType =
          typeOrNull("org.sourceanalysis.app.runtime.PersistedTechnicalRunExecutor");

      assertThat(configurationType)
          .as("runtime configuration binds frozen source inputs without exposing them to callers")
          .isNotNull();
      assertThat(configurationType.getRecordComponents())
          .extracting(component -> component.getType())
          .noneMatch(Path.class::equals);
      assertThat(executorType)
          .as("the persisted run needs one coordinator for Step 01 through Step 05")
          .isNotNull();

      Object configuration =
          configurationType
              .getConstructor(
                  ImmutableBytes.class,
                  ArtifactReference.class,
                  ArtifactReference.class,
                  ProfileView.class,
                  ArtifactStoreLimits.class,
                  DiscoveryProfile.class,
                  ArtifactReference.class,
                  FlowCompilationProfile.class,
                  CapsuleProjectionProfile.class)
              .newInstance(
                  captured.frozenRequestBytes(),
                  reference("verification-policy", 'e', 'f'),
                  reference("capability-profile", '1', '2'),
                  new ProfileView(profileBundle, resourceBudget, 32, 1_000_000),
                  new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24),
                  new DiscoveryProfile("application-discovery-v2"),
                  reference("graph-profile", 'a', 'b'),
                  new FlowCompilationProfile(
                      reference("flow-profile", 'c', 'd'), 16, 8, 64, 96, 32, 64, 256),
                  new CapsuleProjectionProfile(
                      reference("capsule-profile", 'e', 'f'), 16, 32, 4_096, 24_576));
      Object executor =
          executorType
              .getConstructor(
                  RunStoreHandle.class,
                  CanonicalJsonCodec.class,
                  CanonicalArtifactPolicyRegistry.class,
                  LocalGitSourceRegistry.class,
                  configurationType)
              .newInstance(handle, canonicalJson, policies, captured.registry(), configuration);
      Object discovery =
          executorType
              .getMethod(
                  "executeThroughApplicationDiscovery",
                  org.sourceanalysis.app.artifact.AnalysisRunId.class)
              .invoke(executor, queued.runId());
      Object inventory =
          discovery.getClass().getMethod("verifiedSourceInventory").invoke(discovery);
      Object applicationDiscovery =
          discovery.getClass().getMethod("applicationDiscovery").invoke(discovery);
      assertThat(((VerifiedSourceInventoryReference) inventory).publication().address().runId())
          .isEqualTo(queued.runId());
      assertThat(
              ((org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference)
                      applicationDiscovery)
                  .publication()
                  .address()
                  .runId())
          .isEqualTo(queued.runId());

      Object result =
          executorType
              .getMethod("execute", org.sourceanalysis.app.artifact.AnalysisRunId.class)
              .invoke(executor, queued.runId());

      Object flows = result.getClass().getMethod("businessFlows").invoke(result);
      assertThat(
              ((org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference) flows)
                  .publication()
                  .address()
                  .runId())
          .isEqualTo(queued.runId());
    }
  }

  @Test
  void publicFinalDocumentExecutionUsesPersistedFlowsRatherThanTheDiscoveryFallback()
      throws Exception {
    CapturedSource captured = capturedSpringRepository();
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = allTechnicalPolicies(canonicalJson);
    ArtifactReference resourceBudget = reference("resource-budget", 'a', 'b');
    ArtifactReference profileBundle = reference("profile-bundle", 'c', 'd');
    ArtifactReference frozenRequest =
        frozenRequest(canonicalJson, captured.capture(), resourceBudget);
    AnalysisRunRequest request =
        queuedRequest(captured.capture(), frozenRequest, policies, resourceBudget, profileBundle);
    Path storeRoot = temporaryDirectory.resolve("public-agent-flow-runtime-store");
    java.nio.file.Files.createDirectory(storeRoot);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(storeRoot)) {
      ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, canonicalJson, policies, limits);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, canonicalJson, policies, limits);
      PersistedTechnicalRunExecutor technical =
          new PersistedTechnicalRunExecutor(
              handle,
              canonicalJson,
              policies,
              captured.registry(),
              technicalConfiguration(captured, profileBundle, resourceBudget, limits));
      PersistedBusinessRunExecutor business =
          new PersistedBusinessRunExecutor(
              modules,
              steps,
              new PersistedVerifiedSourceTextReader(steps, captured.registry()),
              new PersistedBusinessRunExecutorTest.ScriptedBusinessProvider(),
              new PersistedBusinessRunConfiguration(
                  new BusinessMaterialProfile(8, 24, 12_000),
                  new ActivityExplanationProfile(64_000, 16_000, 2, 32, 2_000),
                  new ProcessExplanationProfile(4, 8, 16_000, 12_000, 2, 16, 2_000, 0),
                  new BusinessReportProfile(64_000, 16_000, 32, 2_000)));
      LocalRepositoryAnalysisAgent agent =
          new LocalRepositoryAnalysisAgent(
              handle,
              new RepositoryAnalysisRunCoordinator(technical, business),
              new BusinessReportCheckpointRenderer(modules),
              new BusinessCheckpointArtifactReader(modules));

      AnalysisRunReference queued = agent.start(request);
      AnalysisRunReference finished =
          agent.executeStep(
              new AnalysisStepExecutionRequest(
                  queued.runId(),
                  org.sourceanalysis.app.artifact.AnalysisStepKey.NINE_SECTION_DOCUMENT));

      assertThat(finished.lifecycleState()).isEqualTo(AnalysisRunLifecycleState.FINISHED);
      ArtifactView materials =
          agent.artifact(
              new ArtifactQuery(
                  queued.runId().value(), BusinessOutputArtifactKey.BUSINESS_MATERIALS, 64_000));
      assertThat(materials.contentUtf8())
          .contains("\"materialMode\":\"FLOW_PREFERRED\"")
          .doesNotContain("\"materialMode\":\"ENTRY_SOURCE_FALLBACK\"");
      assertThat(agent.render(queued.runId().value()).sizeBytes()).isPositive();
    }
  }

  private static PersistedTechnicalRunConfiguration technicalConfiguration(
      CapturedSource captured,
      ArtifactReference profileBundle,
      ArtifactReference resourceBudget,
      ArtifactStoreLimits limits) {
    return new PersistedTechnicalRunConfiguration(
        captured.frozenRequestBytes(),
        reference("verification-policy", 'e', 'f'),
        reference("capability-profile", '1', '2'),
        new ProfileView(profileBundle, resourceBudget, 32, 1_000_000),
        limits,
        new DiscoveryProfile("application-discovery-v2"),
        reference("graph-profile", 'a', 'b'),
        new FlowCompilationProfile(reference("flow-profile", 'c', 'd'), 16, 8, 64, 96, 32, 64, 256),
        new CapsuleProjectionProfile(
            reference("capsule-profile", 'e', 'f'), 16, 32, 4_096, 24_576));
  }

  private CapturedSource capturedSpringRepository() throws Exception {
    Path physicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Path repository = physicalTemporaryDirectory.resolve("repository");
    runGit(physicalTemporaryDirectory, "init", repository.toString());
    runGit(repository, "config", "user.name", "Source Analysis Test");
    runGit(repository, "config", "user.email", "source-analysis@example.invalid");
    java.nio.file.Files.writeString(
        repository.resolve("pom.xml"),
        """
        <project xmlns=\"http://maven.apache.org/POM/4.0.0\">
          <modelVersion>4.0.0</modelVersion>
          <groupId>example</groupId><artifactId>orders</artifactId><version>1.0</version>
          <dependencies>
            <dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId><version>6.0.0</version></dependency>
            <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>3.0.0</version></dependency>
          </dependencies>
        </project>
        """,
        StandardCharsets.UTF_8);
    Path javaDirectory = repository.resolve("src/main/java/example");
    java.nio.file.Files.createDirectories(javaDirectory);
    java.nio.file.Files.writeString(
        javaDirectory.resolve("OrderController.java"),
        """
        package example;
        import org.springframework.web.bind.annotation.PostMapping;
        import org.springframework.web.bind.annotation.RequestMapping;
        import org.springframework.web.bind.annotation.RequestParam;
        import org.springframework.web.bind.annotation.RestController;
        @RestController @RequestMapping(\"/orders\")
        final class OrderController {
          private final OrderService service = new OrderService();
          @PostMapping(\"/approve\") String approve(@RequestParam String status) {
            return service.approve(status);
          }
        }
        final class OrderService {
          private final OrderMapper mapper = null;
          String approve(String status) {
            if (!\"DRAFT\".equals(status)) { throw new IllegalArgumentException(\"status\"); }
            mapper.updateStatus(status); return \"ok\";
          }
        }
        """,
        StandardCharsets.UTF_8);
    java.nio.file.Files.writeString(
        javaDirectory.resolve("OrderMapper.java"),
        """
        package example;
        public interface OrderMapper { void updateStatus(String status); }
        """,
        StandardCharsets.UTF_8);
    Path mapperDirectory = repository.resolve("src/main/resources/mapper");
    java.nio.file.Files.createDirectories(mapperDirectory);
    java.nio.file.Files.writeString(
        mapperDirectory.resolve("OrderMapper.xml"),
        """
        <!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">
        <mapper namespace=\"example.OrderMapper\">
          <update id=\"updateStatus\">UPDATE orders SET status = #{status}</update>
        </mapper>
        """,
        StandardCharsets.UTF_8);
    runGit(repository, "add", ".");
    runGit(repository, "commit", "-m", "source fixture");
    String commit = runGit(repository, "rev-parse", "HEAD").trim();
    ArtifactReference resourceBudget = reference("resource-budget", 'a', 'b');
    Path captureWorkspace = physicalTemporaryDirectory.resolve("capture");
    org.sourceanalysis.app.capture.localgit.SourceRegistrationReference registration =
        new LocalGitCommitCaptureAdapter(captureWorkspace, Path.of("/usr/bin/git"))
            .capture(
                new LocalGitCaptureRequest(
                    "https://example.invalid/customer/orders.git",
                    commit,
                    repository,
                    reference("capture-policy", '3', '4'),
                    resourceBudget));
    LocalGitSourceRegistry registry = new LocalGitSourceRegistry(captureWorkspace);
    RegisteredSourceCapture capture = registry.reopen(registration.sourceRegistrationId());
    return new CapturedSource(
        capture, registry, frozenRequestBytes(canonicalJson(), commit, resourceBudget, capture));
  }

  private static ArtifactReference frozenRequest(
      CanonicalJsonCodec canonicalJson,
      RegisteredSourceCapture capture,
      ArtifactReference resourceBudget) {
    ImmutableBytes bytes =
        frozenRequestBytes(canonicalJson, capture.commitId(), resourceBudget, capture);
    return new ArtifactReference(
        ArtifactId.parse("frozen-request:" + sha256(bytes.copyToByteArray())),
        Sha256Digest.parse(sha256(bytes.copyToByteArray())));
  }

  private static ImmutableBytes frozenRequestBytes(
      CanonicalJsonCodec canonicalJson, String commit, ArtifactReference resourceBudget) {
    return frozenRequestBytes(canonicalJson, commit, resourceBudget, null);
  }

  private static ImmutableBytes frozenRequestBytes(
      CanonicalJsonCodec canonicalJson,
      String commit,
      ArtifactReference resourceBudget,
      RegisteredSourceCapture capture) {
    ObjectNode frozen = JsonNodeFactory.instance.objectNode();
    frozen.put("schemaVersion", "frozen-repository-request-v2");
    ObjectNode origin = frozen.putObject("expectedOrigin");
    origin.put("kind", "GIT_SHA1_COMMIT");
    origin.put("repositoryUrl", "https://example.invalid/customer/orders.git");
    origin.put("revision40", commit);
    if (capture == null) {
      throw new IllegalArgumentException("captured source is required");
    }
    frozen.set("captureReceiptRef", referenceNode(capture.captureReceiptRef()));
    frozen.set("snapshotManifestRef", referenceNode(capture.snapshotManifestRef()));
    ObjectNode scope = frozen.putObject("inventoryScope");
    scope.put("kind", "COMPLETE_CAPTURE");
    scope.putNull("scopeRoot");
    scope.put("declaredPathCount", capture.manifestEntries().size());
    frozen.set("verificationPolicyRef", referenceNode(reference("verification-policy", 'e', 'f')));
    frozen.set("capabilityProfileRef", referenceNode(reference("capability-profile", '1', '2')));
    frozen.set("resourceBudgetRef", referenceNode(resourceBudget));
    return canonicalJson.encodeCanonical(frozen);
  }

  private static org.sourceanalysis.app.runtime.AnalysisRunRequest queuedRequest(
      RegisteredSourceCapture capture,
      ArtifactReference frozenRequest,
      CanonicalArtifactPolicyRegistry policies,
      ArtifactReference resourceBudget,
      ArtifactReference profileBundle) {
    return new org.sourceanalysis.app.runtime.AnalysisRunRequest(
        capture.sourceRegistrationRef().artifactId(),
        frozenRequest,
        profileBundle,
        resourceBudget,
        reference("toolchain", '5', '6'),
        reference("schema-bundle", '7', '8'),
        reference("prompt-bundle", '9', 'a'),
        null,
        new ArtifactReference(policies.reference().artifactId(), policies.reference().sha256()),
        reference("candidate-series", 'b', 'c'),
        ReaderCandidateRound.ROUND_1,
        null,
        List.of());
  }

  private static CanonicalJsonCodec canonicalJson() {
    return new CanonicalJsonCodec();
  }

  /** Adds the five inventory contracts to the existing Step02–05 test policy registry. */
  private static CanonicalArtifactPolicyRegistry allTechnicalPolicies(
      CanonicalJsonCodec canonicalJson) throws Exception {
    CanonicalArtifactPolicyRegistry technical =
        ProgramGraphsPublicFixture.policiesForRuntimeTest(canonicalJson);
    ArtifactPolicyRegistryReference reference =
        new ArtifactPolicyRegistryReference(
            ArtifactId.parse("artifact-policy-registry:" + "f".repeat(64)),
            Sha256Digest.parse("e".repeat(64)));
    java.util.Map<ArtifactPolicyKey, CanonicalArtifactPolicy> inventory =
        java.util.Map.of(
            new ArtifactPolicyKey("VERIFIED_SNAPSHOT", "verified-snapshot-v2"),
            policy(
                "VERIFIED_SNAPSHOT",
                "verified-snapshot-v2",
                "verified-snapshot",
                CanonicalMediaType.APPLICATION_JSON,
                CanonicalEnvelopeKind.STANDALONE_JSON,
                false),
            new ArtifactPolicyKey(
                "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST",
                "verified-source-inventory-admitted-source-request-v2"),
            policy(
                "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST",
                "verified-source-inventory-admitted-source-request-v2",
                "source-request",
                CanonicalMediaType.APPLICATION_JSON,
                CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON,
                false),
            new ArtifactPolicyKey(
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
                "verified-source-inventory-source-input-v2"),
            policy(
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
                "verified-source-inventory-source-input-v2",
                "verified-source-inventory-source-input",
                CanonicalMediaType.APPLICATION_JSON,
                CanonicalEnvelopeKind.STANDALONE_JSON,
                false),
            new ArtifactPolicyKey(
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
                "verified-source-inventory-source-inventory-v2"),
            policy(
                "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
                "verified-source-inventory-source-inventory-v2",
                "verified-source-inventory-source-inventory",
                CanonicalMediaType.APPLICATION_X_NDJSON,
                CanonicalEnvelopeKind.CANONICAL_JSONL,
                false),
            new ArtifactPolicyKey(
                "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX",
                "verified-source-inventory-verified-source-index-v2"),
            policy(
                "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX",
                "verified-source-inventory-verified-source-index-v2",
                "source-index",
                CanonicalMediaType.APPLICATION_JSON,
                CanonicalEnvelopeKind.MODULE_ARTIFACT_JSON,
                false));
    return new CanonicalArtifactPolicyRegistry() {
      @Override
      public ArtifactPolicyRegistryReference reference() {
        return reference;
      }

      @Override
      public CanonicalArtifactPolicy resolve(ArtifactPolicyKey key) {
        CanonicalArtifactPolicy sourcePolicy = inventory.get(key);
        return sourcePolicy != null ? sourcePolicy : technical.resolve(key);
      }
    };
  }

  private static CanonicalArtifactPolicy policy(
      String artifactType,
      String schemaVersion,
      String artifactIdPrefix,
      CanonicalMediaType mediaType,
      CanonicalEnvelopeKind envelopeKind,
      boolean emptyJsonlAllowed) {
    return new CanonicalArtifactPolicy(
        new ArtifactPolicyKey(artifactType, schemaVersion),
        artifactIdPrefix,
        mediaType,
        envelopeKind,
        emptyJsonlAllowed,
        PublicContentExposure.PATH_FREE_COMPLETE_UTF8);
  }

  private static org.sourceanalysis.app.artifact.ArtifactControls controlsFor(
      CanonicalArtifactPolicyRegistry policies,
      org.sourceanalysis.app.runtime.AnalysisRunRequest queuedRequest) {
    return new org.sourceanalysis.app.artifact.ArtifactControls(
        queuedRequest.toolchainRef().sha256(),
        queuedRequest.profileBundleRef().sha256(),
        queuedRequest.schemaBundleRef().sha256(),
        queuedRequest.promptBundleRef().sha256(),
        policies.reference());
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private String runGit(Path directory, String... arguments) throws Exception {
    java.util.ArrayList<String> command = new java.util.ArrayList<>();
    command.add("git");
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
    int exit = process.waitFor();
    String standardOutput =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String standardError =
        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(exit).withFailMessage("git stderr: %s", standardError).isZero();
    return standardOutput;
  }

  private record CapturedSource(
      RegisteredSourceCapture capture,
      LocalGitSourceRegistry registry,
      ImmutableBytes frozenRequestBytes) {}

  private static ArtifactReference reference(String prefix, char identity, char content) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(identity).repeat(64)),
        Sha256Digest.parse(String.valueOf(content).repeat(64)));
  }

  private static Class<?> typeOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }
}
