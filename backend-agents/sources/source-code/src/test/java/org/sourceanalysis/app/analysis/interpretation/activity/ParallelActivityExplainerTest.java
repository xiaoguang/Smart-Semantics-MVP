package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterial;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialCheckpointReader;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialSet;
import org.sourceanalysis.app.analysis.interpretation.material.LegacyM10CheckpointFixture;
import org.sourceanalysis.app.analysis.interpretation.material.ModelActivityPacket;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** RED integration tests for the bounded parallel Activity job execution contract. */
class ParallelActivityExplainerTest {

  private static final int MATERIAL_COUNT = 13;
  private static final int DEFAULT_JOB_CAP = 4;
  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

  @Test
  void runsMoreThanTwelveMaterialJobsInParallelAndAggregatesInStableMaterialOrder()
      throws Exception {
    LegacyM10CheckpointFixture.HistoricalCheckpoint fixture = LegacyM10CheckpointFixture.open();
    BusinessMaterialBuildResult materials = expandedMaterials(fixture, MATERIAL_COUNT);
    BlockingActivityProvider provider = new BlockingActivityProvider(MATERIAL_COUNT, null);
    Future<ActivityExplanationResult> future = runAsync(materials, provider);

    boolean barrierReached = provider.draftStartedBarrier.await(2, TimeUnit.SECONDS);
    int peakBeforeRelease = provider.peakActiveGenerations.get();
    provider.releaseDrafts.countDown();

    ActivityExplanationResult completed = provider.awaitResult(future);
    assertThat(barrierReached)
        .as("the Activity phase must dispatch multiple jobs before waiting")
        .isTrue();
    assertThat(peakBeforeRelease)
        .as("default global/provider cap must be enforced")
        .isBetween(2, DEFAULT_JOB_CAP);
    assertThat(completed.coverage()).hasSize(MATERIAL_COUNT);
    assertThat(completed.reviewedActivities()).hasSize(MATERIAL_COUNT);
    assertThat(provider.totalCalls()).isEqualTo(MATERIAL_COUNT * 2);
    assertThat(provider.maximumCallsPerMaterial()).isEqualTo(2);
    assertThat(completed.reviewedActivities().stream().map(ReviewedActivity::materialId).toList())
        .isSortedAccordingTo(Comparator.naturalOrder());
    assertThat(completed.coverage().stream().map(ActivityEntryCoverage::entryId).toList())
        .isSortedAccordingTo(Comparator.naturalOrder());
    assertThat(provider.crossedDraftReviewBoundary.get())
        .as("each job must give its own actual DRAFT to its own REVIEW")
        .isFalse();
  }

  @Test
  void fatalStopsNewDispatchButStartedValidPairsFinishWithoutRetry() throws Exception {
    LegacyM10CheckpointFixture.HistoricalCheckpoint fixture = LegacyM10CheckpointFixture.open();
    BusinessMaterialBuildResult materials = expandedMaterials(fixture, MATERIAL_COUNT);
    BlockingActivityProvider provider =
        new BlockingActivityProvider(MATERIAL_COUNT, "activity-job-004");
    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<ActivityExplanationResult> future =
          caller.submit(
              () ->
                  new ActivityExplainer(provider)
                      .explain(
                          new ExplainActivitiesRequest(
                              materials,
                              new ActivityExplanationProfile(64_000, 16_000, 1, 32, 2_000),
                              MATERIAL_COUNT)));
      assertThat(provider.firstDraftStarted.await(2, TimeUnit.SECONDS)).isTrue();
      boolean fourJobsStarted = provider.draftStartedBarrier.await(2, TimeUnit.SECONDS);
      boolean fatalBeforeRelease = provider.fatalObserved.await(1, TimeUnit.SECONDS);
      provider.releaseDrafts.countDown();

      assertThatThrownBy(() -> future.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasRootCauseMessage("ACTIVITY_PROVIDER_FAILED_AFTER_START");
      assertThat(fourJobsStarted)
          .as("the fatal job must be observed after the bounded initial dispatch")
          .isTrue();
      assertThat(fatalBeforeRelease)
          .as("fatal observation must happen before releasing already-started jobs")
          .isTrue();
      assertThat(provider.reviewCalls("activity-job-001"))
          .as("a valid DRAFT that was already started must finish its REVIEW")
          .isEqualTo(1);
      assertThat(provider.reviewCalls("activity-job-004"))
          .as("the failed DRAFT must not be retried or reviewed")
          .isZero();
      assertThat(provider.startedMaterialKeys())
          .as("fatal observation must stop new dispatch after the initial bounded set")
          .hasSizeLessThanOrEqualTo(DEFAULT_JOB_CAP);
      assertThat(provider.maximumCallsPerMaterial()).isLessThanOrEqualTo(2);
      assertThat(provider.reviewCalls("activity-job-001")).isEqualTo(1);
    } finally {
      provider.releaseDrafts.countDown();
      caller.shutdownNow();
    }
  }

  private Future<ActivityExplanationResult> runAsync(
      BusinessMaterialBuildResult materials, BlockingActivityProvider provider) throws Exception {
    ExecutorService caller = Executors.newSingleThreadExecutor();
    Future<ActivityExplanationResult> future =
        caller.submit(
            () ->
                new ActivityExplainer(provider)
                    .explain(
                        new ExplainActivitiesRequest(
                            materials,
                            new ActivityExplanationProfile(64_000, 16_000, 1, 32, 2_000),
                            MATERIAL_COUNT)));
    provider.caller = caller;
    return future;
  }

  private static BusinessMaterialBuildResult expandedMaterials(
      LegacyM10CheckpointFixture.HistoricalCheckpoint fixture, int count) {
    BusinessMaterialBuildResult base =
        new BusinessMaterialCheckpointReader(fixture.artifacts()).reopen(fixture.checkpoint());
    BusinessMaterial template = base.materialSet().materials().get(0);
    List<BusinessMaterial> materials = new ArrayList<>();
    List<BusinessMaterialEntryCoverage> coverage = new ArrayList<>();
    for (int index = 1; index <= count; index++) {
      String key = String.format("%03d", index);
      String materialId = "material-" + key;
      String entryId = "entry-" + key;
      String context = "activity-job-" + key;
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
        new BusinessMaterialSet("parallel-materials", materials, coverage), base.checkpoint());
  }

  private static final class BlockingActivityProvider implements StructuredModelProvider {
    private final String fatalContext;
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final Map<String, AtomicInteger> callsByContext = new ConcurrentHashMap<>();
    private final Map<String, JsonNode> draftsByContext = new ConcurrentHashMap<>();
    private final AtomicInteger activeGenerations = new AtomicInteger();
    private final AtomicInteger peakActiveGenerations = new AtomicInteger();
    private final AtomicBoolean crossedDraftReviewBoundary = new AtomicBoolean();
    private final AtomicBoolean firstDraftSeen = new AtomicBoolean();
    private final CountDownLatch draftStartedBarrier;
    private final CountDownLatch firstDraftStarted = new CountDownLatch(1);
    private final CountDownLatch fatalObserved = new CountDownLatch(1);
    private final CountDownLatch releaseDrafts = new CountDownLatch(1);
    private volatile ExecutorService caller;

    private BlockingActivityProvider(int expectedMaterials, String fatalContext) {
      this.fatalContext = fatalContext;
      this.draftStartedBarrier = new CountDownLatch(Math.min(DEFAULT_JOB_CAP, expectedMaterials));
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode packet = canonicalJson.parseCanonical(request.untrustedInputJson());
      String context = packet.path("context").asText();
      if (context.isBlank()) {
        throw new AssertionError("parallel activity packet lost its material context");
      }
      AtomicInteger calls = callsByContext.computeIfAbsent(context, ignored -> new AtomicInteger());
      int call = calls.incrementAndGet();
      if (call > 2) {
        throw new AssertionError("duplicate DRAFT/REVIEW submission for " + context);
      }
      int active = activeGenerations.incrementAndGet();
      peakActiveGenerations.accumulateAndGet(active, Math::max);
      try {
        if ("ACTIVITY_DRAFT".equals(request.taskKind())) {
          draftStartedBarrier.countDown();
          if (fatalContext != null && fatalContext.equals(context)) {
            fatalObserved.countDown();
            throw new ActivityExplainerFailureForTest();
          }
          if (firstDraftSeen.compareAndSet(false, true)) {
            firstDraftStarted.countDown();
          }
          JsonNode draft = response(packet, context, "draft");
          draftsByContext.put(context, draft);
          awaitRelease();
          return responseObject(draft);
        }
        JsonNode actualDraft = packet.path("actualDraft");
        if (!actualDraft.equals(draftsByContext.get(context))) {
          crossedDraftReviewBoundary.set(true);
        }
        return responseObject(response(packet, context, "review"), true);
      } finally {
        activeGenerations.decrementAndGet();
      }
    }

    private void awaitRelease() {
      try {
        if (!releaseDrafts.await(10, TimeUnit.SECONDS)) {
          throw new AssertionError("parallel activity DRAFT barrier was not released");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError("parallel activity provider interrupted", interrupted);
      }
    }

    private StructuredModelResponse responseObject(JsonNode value) {
      return responseObject(value, false);
    }

    private StructuredModelResponse responseObject(JsonNode value, boolean review) {
      ObjectNode response = ((ObjectNode) value).deepCopy();
      if (review) {
        response.putArray("unexplainedEntries");
      }
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted", "fixture", "high", "read-only"));
    }

    private JsonNode response(JsonNode packet, String context, String phase) {
      String materialKey = context.substring(context.lastIndexOf('-') + 1);
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = root.putArray("activities").addObject();
      activity.put("activityLocalId", "activity-" + materialKey);
      activity.putArray("entryKeys").add("E1");
      activity.put("name", "处理 " + materialKey);
      activity.put("businessPurpose", phase + " purpose " + materialKey);
      emptyListFields(activity, "participants", "businessObjects");
      activity.putArray("triggerOrInput").add("入口输入 " + materialKey);
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("处理 " + materialKey);
      activity.putArray("codeDefinedResults").add("结果 " + materialKey);
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms").add("业务活动");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      ArrayNode sourceRefs = activity.putArray("sourceRefs");
      packet.path("allowlistedRefs").forEach(ref -> sourceRefs.add(ref.path("ref").asText()));
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("静态源码不证明某次请求成功");
      return root;
    }

    private static void emptyListFields(ObjectNode activity, String... fields) {
      for (String field : fields) {
        activity.putArray(field);
      }
    }

    private ActivityExplanationResult awaitResult(Future<ActivityExplanationResult> future)
        throws InterruptedException, ExecutionException, TimeoutException {
      try {
        return future.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      } finally {
        if (caller != null) {
          caller.shutdownNow();
        }
      }
    }

    private int totalCalls() {
      return callsByContext.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    private int maximumCallsPerMaterial() {
      return callsByContext.values().stream().mapToInt(AtomicInteger::get).max().orElse(0);
    }

    private int reviewCalls(String context) {
      AtomicInteger count = callsByContext.get(context);
      return count == null ? 0 : Math.max(0, count.get() - 1);
    }

    private List<String> startedMaterialKeys() {
      return callsByContext.keySet().stream().sorted().toList();
    }
  }

  /** Distinguishes an intentional Provider fatal from a test assertion failure. */
  private static final class ActivityExplainerFailureForTest extends RuntimeException {
    private ActivityExplainerFailureForTest() {
      super("ACTIVITY_PROVIDER_FAILED_AFTER_START");
    }
  }
}
