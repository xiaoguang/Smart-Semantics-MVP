package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuilder;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Defines the injected Activity execution cap, job task identity, and private result contract. */
class ActivityJobExecutionConfigurationTest {

  private static final int MATERIAL_COUNT = 3;
  private static final String RUN_ID = "analysis-run:" + "a".repeat(64);

  @TempDir Path temporaryDirectory;

  @Test
  void appliesInjectedCapAndPersistsEveryReviewedMaterialUnderItsRunPrivateJobKey()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("activity-job-execution"))) {
      BusinessMaterialBuildResult materials = expandedMaterials(fixture, MATERIAL_COUNT);
      Path journalDirectory = Files.createDirectory(temporaryDirectory.resolve("journal"));
      BlockingProvider provider = new BlockingProvider();
      ActivityExplainer explainer =
          configuredExplainer(provider, journalDirectory, new AnalysisRunId(RUN_ID));
      ExecutorService caller = Executors.newSingleThreadExecutor();
      try {
        Future<ActivityExplanationResult> future =
            caller.submit(
                () ->
                    explainer.explain(
                        new ExplainActivitiesRequest(
                            materials,
                            new ActivityExplanationProfile(64_000, 16_000, 1, 32, 2_000),
                            MATERIAL_COUNT)));

        assertThat(provider.firstTwoDrafts.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(provider.draftStarts)
            .as("the injected cap of two must keep the third material queued")
            .hasValue(2);
        provider.releaseDrafts.countDown();

        ActivityExplanationResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.reviewedActivities()).hasSize(MATERIAL_COUNT);
        assertThat(provider.peakActiveGenerations)
            .as("the injected effective cap limits complete Activity jobs")
            .hasValue(2);
        assertThat(provider.draftTaskIds()).hasSize(MATERIAL_COUNT).doesNotHaveDuplicates();

        List<Path> privateResults;
        try (var paths = Files.walk(journalDirectory)) {
          privateResults =
              paths
                  .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
                  .sorted()
                  .toList();
        }
        assertThat(privateResults).hasSize(MATERIAL_COUNT);
        CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
        for (Path privateResult : privateResults) {
          JsonNode saved =
              canonicalJson.parseCanonical(
                  org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                      Files.readAllBytes(privateResult)));
          assertThat(saved.path("schemaVersion").asText())
              .isEqualTo("model-job-reviewed-result-v2");
          assertThat(saved.path("status").asText()).isEqualTo("COMPLETED");
          assertThat(saved.path("draft").isObject()).isTrue();
          assertThat(saved.path("review").isObject()).isTrue();
          assertThat(saved.path("runId").asText()).isEqualTo(RUN_ID);
          assertThat(saved.path("phase").asText()).isEqualTo("activity");
          assertThat(saved.path("providerBindingKey").asText()).isEqualTo("pro");
          assertThat(saved.path("quotaScope").asText()).isEqualTo("personal-pro-account");
          assertThat(saved.path("materialId").asText()).startsWith("material-");
          assertThat(saved.path("jobKey").asText()).matches("[0-9a-f]{64}");
          assertThat(saved.path("inputFingerprint").asText()).matches("[0-9a-f]{64}");
          assertThat(saved.path("runtimeIdentity").path("upstreamProvider").asText())
              .isEqualTo("scripted");
          assertThat(saved.path("runtimeIdentity").path("model").asText()).isEqualTo("fixture");
          assertThat(saved.path("runtimeIdentity").path("reasoningEffort").asText())
              .isEqualTo("high");
          assertThat(saved.path("runtimeIdentity").path("sandbox").asText()).isEqualTo("read-only");
          assertThat(saved.path("reviewedActivities").isArray()).isTrue();
          assertThat(saved.path("coverage").isArray()).isTrue();
          assertThat(saved.path("unexplainedActivityEntries").isArray()).isTrue();
          assertThat(privateResult.toString()).doesNotContain(RUN_ID);
        }
      } finally {
        provider.releaseDrafts.countDown();
        caller.shutdownNow();
      }
    }
  }

  @Test
  void fatalStopsQueuedActivityJobsButSavesStartedReviewedJobsWithoutPublishingAggregate()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("fatal-activity-job-execution"))) {
      BusinessMaterialBuildResult materials = expandedMaterials(fixture, MATERIAL_COUNT);
      Path journalDirectory = Files.createDirectory(temporaryDirectory.resolve("fatal-journal"));
      BlockingProvider provider = new BlockingProvider("configured-activity-002");
      TrackingModuleStore moduleStore = new TrackingModuleStore(fixture.moduleArtifacts());
      ActivityExplainer explainer =
          configuredExplainer(
              provider,
              moduleStore,
              journalDirectory,
              new AnalysisRunId("analysis-run:" + "b".repeat(64)));
      ExecutorService caller = Executors.newSingleThreadExecutor();
      try {
        Future<ActivityExplanationResult> future =
            caller.submit(
                () ->
                    explainer.explain(
                        new ExplainActivitiesRequest(
                            materials,
                            new ActivityExplanationProfile(64_000, 16_000, 1, 32, 2_000),
                            MATERIAL_COUNT)));

        assertThat(provider.firstTwoDrafts.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(provider.fatalObserved.await(2, TimeUnit.SECONDS)).isTrue();
        provider.releaseDrafts.countDown();

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasRootCauseMessage("ACTIVITY_PROVIDER_FAILED_AFTER_START");
        assertThat(provider.draftStarts).hasValue(2);
        assertThat(provider.reviewCalls("configured-activity-001")).isEqualTo(1);
        assertThat(provider.reviewCalls("configured-activity-002")).isZero();
        assertThat(provider.reviewCalls("configured-activity-003")).isZero();
        assertThat(moduleStore.installCalls)
            .as("a fatal Activity phase must not publish the aggregate checkpoint")
            .hasValue(0);

        List<Path> privateResults = privateResultFiles(journalDirectory);
        assertThat(privateResults).hasSize(1);
        JsonNode saved =
            new CanonicalJsonCodec()
                .parseCanonical(
                    org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                        Files.readAllBytes(privateResults.get(0))));
        assertThat(saved.path("materialId").asText()).isEqualTo("material-001");
      } finally {
        provider.releaseDrafts.countDown();
        caller.shutdownNow();
      }
    }
  }

  private static ActivityExplainer configuredExplainer(
      StructuredModelProvider provider, Path journalDirectory, AnalysisRunId runId)
      throws Exception {
    return configuredExplainer(provider, null, journalDirectory, runId);
  }

  private static ActivityExplainer configuredExplainer(
      StructuredModelProvider provider,
      CanonicalModuleArtifactStore checkpointStore,
      Path journalDirectory,
      AnalysisRunId runId)
      throws Exception {
    try {
      Class<?> configurationType =
          Class.forName(
              "org.sourceanalysis.app.analysis.interpretation.activity.ActivityJobExecutionConfiguration");
      Constructor<?> configurationConstructor =
          configurationType.getConstructor(
              int.class,
              String.class,
              String.class,
              Path.class,
              AnalysisRunId.class,
              ModelRuntimeIdentityV1.class);
      Object configuration =
          configurationConstructor.newInstance(
              2,
              "pro",
              "personal-pro-account",
              journalDirectory,
              runId,
              new ModelRuntimeIdentityV1("scripted", "fixture", "high", "read-only"));
      Method factory;
      Object[] arguments;
      if (checkpointStore == null) {
        factory =
            ActivityExplainer.class.getMethod(
                "forExecution", StructuredModelProvider.class, configurationType);
        arguments = new Object[] {provider, configuration};
      } else {
        factory =
            ActivityExplainer.class.getMethod(
                "forExecution",
                StructuredModelProvider.class,
                CanonicalModuleArtifactStore.class,
                configurationType);
        arguments = new Object[] {provider, checkpointStore, configuration};
      }
      return (ActivityExplainer) factory.invoke(null, arguments);
    } catch (ClassNotFoundException | NoSuchMethodException missing) {
      fail("ACTIVITY_JOB_EXECUTION_CONFIGURATION_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      if (cause instanceof Error errorFailure) {
        throw errorFailure;
      }
      throw failure;
    }
  }

  private static List<Path> privateResultFiles(Path journalDirectory) throws java.io.IOException {
    try (var paths = Files.walk(journalDirectory)) {
      return paths
          .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
          .sorted()
          .toList();
    }
  }

  static BusinessMaterialBuildResult expandedMaterials(
      ProgramGraphsPublicFixture fixture, int count) {
    BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);
    BusinessMaterialBuildResult base =
        new BusinessMaterialBuilder(
                fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
            .build(
                new org.sourceanalysis.app.analysis.interpretation.material
                    .BuildBusinessMaterialsRequest(
                    flows, new BusinessMaterialProfile(8, 24, 12_000, count)));
    BusinessMaterial template = base.materialSet().materials().get(0);
    List<BusinessMaterial> materials = new ArrayList<>();
    List<BusinessMaterialEntryCoverage> coverage = new ArrayList<>();
    for (int index = 1; index <= count; index++) {
      String key = String.format("%03d", index);
      String materialId = "material-" + key;
      String entryId = "entry-" + key;
      String context = "configured-activity-" + key;
      materials.add(
          new BusinessMaterial(
              materialId,
              List.of(entryId),
              template.materialMode(),
              context,
              template.technicalObservations(),
              template.sourceRefs(),
              template.flowRefs(),
              template.technicalProofRefs(),
              template.limitations(),
              new ModelActivityPacket(
                  context,
                  template.modelPacket().technicalObservations(),
                  template.modelPacket().allowlistedRefs(),
                  template.modelPacket().limitations())));
      coverage.add(
          new BusinessMaterialEntryCoverage(entryId, "ANALYZED_MATERIAL", materialId, null));
    }
    return new BusinessMaterialBuildResult(
        new BusinessMaterialSet("configured-parallel-materials", materials, coverage),
        base.checkpoint());
  }

  private static final class BlockingProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final String fatalContext;
    private final CountDownLatch firstTwoDrafts = new CountDownLatch(2);
    private final CountDownLatch fatalObserved = new CountDownLatch(1);
    private final CountDownLatch releaseDrafts = new CountDownLatch(1);
    private final AtomicInteger draftStarts = new AtomicInteger();
    private final AtomicInteger activeGenerations = new AtomicInteger();
    private final AtomicInteger peakActiveGenerations = new AtomicInteger();
    private final List<String> draftTaskIds =
        java.util.Collections.synchronizedList(new ArrayList<>());
    private final Map<String, AtomicInteger> reviewCallsByContext = new ConcurrentHashMap<>();

    private BlockingProvider() {
      this(null);
    }

    private BlockingProvider(String fatalContext) {
      this.fatalContext = fatalContext;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode packet = canonicalJson.parseCanonical(request.untrustedInputJson());
      String context = packet.path("context").asText();
      int active = activeGenerations.incrementAndGet();
      peakActiveGenerations.accumulateAndGet(active, Math::max);
      try {
        if ("ACTIVITY_DRAFT".equals(request.taskKind())) {
          draftTaskIds.add(request.taskId());
          draftStarts.incrementAndGet();
          firstTwoDrafts.countDown();
          if (fatalContext != null && fatalContext.equals(context)) {
            fatalObserved.countDown();
            throw new ActivityExplainerFailureForTest();
          }
          awaitRelease();
        } else {
          reviewCallsByContext
              .computeIfAbsent(context, ignored -> new AtomicInteger())
              .incrementAndGet();
        }
        ObjectNode response =
            activityResponse(packet, "ACTIVITY_REVIEW".equals(request.taskKind()));
        return new StructuredModelResponse(
            canonicalJson.encodeCanonical(response),
            new ModelRuntimeIdentityV1("scripted", "fixture", "high", "read-only"));
      } finally {
        activeGenerations.decrementAndGet();
      }
    }

    private void awaitRelease() {
      try {
        if (!releaseDrafts.await(5, TimeUnit.SECONDS)) {
          throw new AssertionError("configured Activity jobs did not release");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError("configured Activity job interrupted", interrupted);
      }
    }

    private static ObjectNode activityResponse(JsonNode packet, boolean reviewed) {
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = response.putArray("activities").addObject();
      activity.put("activityLocalId", "activity-1");
      activity.putArray("entryKeys").add("E1");
      activity.put("name", "处理业务请求");
      activity.put("businessPurpose", "根据入口提交的数据执行业务处理。");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("业务记录");
      activity.putArray("triggerOrInput").add("入口提交的数据");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("处理业务请求");
      activity.putArray("codeDefinedResults").add("返回业务结果");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("业务记录");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      ArrayNode sourceRefs = activity.putArray("sourceRefs");
      packet.path("allowlistedRefs").forEach(ref -> sourceRefs.add(ref.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次请求成功");
      if (reviewed) {
        response.putArray("unexplainedEntries");
      }
      return response;
    }

    private List<String> draftTaskIds() {
      synchronized (draftTaskIds) {
        return List.copyOf(draftTaskIds);
      }
    }

    private int reviewCalls(String context) {
      AtomicInteger calls = reviewCallsByContext.get(context);
      return calls == null ? 0 : calls.get();
    }
  }

  private static final class TrackingModuleStore implements CanonicalModuleArtifactStore {
    private final CanonicalModuleArtifactStore delegate;
    private final AtomicInteger installCalls = new AtomicInteger();

    private TrackingModuleStore(CanonicalModuleArtifactStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public InstalledModulePublication install(ModuleInstallRequest request) {
      installCalls.incrementAndGet();
      return delegate.install(request);
    }

    @Override
    public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
      return delegate.resolveArtifactPolicy(key);
    }

    @Override
    public ReopenedModulePublication reopen(ModulePublicationReference reference) {
      return delegate.reopen(reference);
    }
  }

  private static final class ActivityExplainerFailureForTest extends RuntimeException {
    private ActivityExplainerFailureForTest() {
      super("ACTIVITY_PROVIDER_FAILED_AFTER_START");
    }
  }
}
