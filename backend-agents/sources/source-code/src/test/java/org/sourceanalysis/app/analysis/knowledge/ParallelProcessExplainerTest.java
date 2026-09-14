package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityEntryCoverage;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationResult;
import org.sourceanalysis.app.analysis.interpretation.activity.ReviewedActivity;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** Proves independent process groups run concurrently while each group keeps DRAFT then REVIEW. */
class ParallelProcessExplainerTest {

  @TempDir Path temporaryDirectory;

  @Test
  void runsTwoIndependentGroupsConcurrentlyThenReturnsOneStableKnowledgeValue() throws Exception {
    Path journal = Files.createDirectory(temporaryDirectory.resolve("journal"));
    SharedBarrier barrier = new SharedBarrier();
    GroupProvider pro = new GroupProvider("pro", barrier);
    GroupProvider api = new GroupProvider("api", barrier);
    ModelRuntimeIdentityV1 proIdentity =
        new ModelRuntimeIdentityV1("scripted-pro", "fixture", "high", "read-only");
    ModelRuntimeIdentityV1 apiIdentity =
        new ModelRuntimeIdentityV1("scripted-api", "fixture", "high", "read-only");
    ModelJobExecutionConfiguration configuration =
        new ModelJobExecutionConfiguration(
            2,
            Map.of(
                "pro", new ModelJobProviderBinding("pro", "pro-account", 1, pro, proIdentity),
                "api", new ModelJobProviderBinding("api", "api-project", 1, api, apiIdentity)),
            Map.of(
                "activity", List.of("pro", "api"),
                "processGroup", List.of("pro", "api"),
                "repositorySummary", List.of("pro"),
                "report", List.of("pro")),
            journal,
            new AnalysisRunId("analysis-run:" + "d".repeat(64)));
    ProcessExplainer explainer = ProcessExplainer.forExecution(configuration);
    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<RepositoryBusinessKnowledge> result =
          caller.submit(
              () ->
                  explainer.explain(
                      new ExplainRepositoryProcessesRequest(
                          activities(),
                          new ProcessExplanationProfile(4, 4, 32_000, 16_000, 4, 32, 2_000, 10))));

      assertThat(barrier.draftsStarted.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(barrier.active).hasValue(2);
      barrier.release.countDown();

      RepositoryBusinessKnowledge knowledge = result.get(10, TimeUnit.SECONDS);
      assertThat(knowledge.processes()).hasSize(2);
      assertThat(knowledge.repositorySummary()).isNotNull();
      assertThat(pro.taskKinds())
          .containsExactly(
              "PROCESS_GROUP_DRAFT",
              "PROCESS_GROUP_REVIEW",
              "REPOSITORY_SUMMARY_DRAFT",
              "REPOSITORY_SUMMARY_REVIEW");
      assertThat(api.taskKinds()).containsExactly("PROCESS_GROUP_DRAFT", "PROCESS_GROUP_REVIEW");
      assertThat(barrier.groupReviews).hasValue(2);
      assertThat(barrier.peak).hasValue(2);
      try (var paths = Files.walk(journal)) {
        List<Path> results =
            paths
                .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
                .toList();
        assertThat(results).hasSize(3);
        for (Path saved : results) {
          JsonNode record =
              new CanonicalJsonCodec()
                  .parseCanonical(ImmutableBytes.copyOf(Files.readAllBytes(saved)));
          assertThat(record.path("inputFingerprint").asText()).matches("[0-9a-f]{64}");
          assertThat(record.path("jobKey").asText())
              .endsWith(record.path("inputFingerprint").asText());
          assertThat(record.path("schemaVersion").asText())
              .isEqualTo("model-job-reviewed-result-v2");
          assertThat(record.path("draft").isObject()).isTrue();
          assertThat(record.path("review").isObject()).isTrue();
        }
      }

      AnalysisRunId reuseBatch = new AnalysisRunId("analysis-run:" + "e".repeat(64));
      ModelJobExecutionConfiguration reuseConfiguration =
          new ModelJobExecutionConfiguration(
              2,
              Map.of(
                  "pro", new ModelJobProviderBinding("pro", "pro-account", 1, pro, proIdentity),
                  "api", new ModelJobProviderBinding("api", "api-project", 1, api, apiIdentity)),
              Map.of(
                  "activity", List.of("pro", "api"),
                  "processGroup", List.of("pro", "api"),
                  "repositorySummary", List.of("pro"),
                  "report", List.of("pro")),
              journal,
              reuseBatch,
              configuration.runId());
      RepositoryBusinessKnowledge reused =
          ProcessExplainer.forExecution(reuseConfiguration)
              .explain(
                  new ExplainRepositoryProcessesRequest(
                      activities(),
                      new ProcessExplanationProfile(4, 4, 32_000, 16_000, 4, 32, 2_000, 10)));

      assertThat(reused.processes()).isEqualTo(knowledge.processes());
      assertThat(reused.repositorySummary()).isEqualTo(knowledge.repositorySummary());
      assertThat(pro.taskKinds())
          .as("matching process groups and repository summary must be reused without calls")
          .hasSize(4);
      assertThat(api.taskKinds()).hasSize(2);
    } finally {
      barrier.release.countDown();
      caller.shutdownNow();
    }
  }

  private static ActivityExplanationResult activities() {
    List<ReviewedActivity> activities =
        List.of(
            activity("a1", "订单A"),
            activity("a2", "订单A"),
            activity("b1", "库存B"),
            activity("b2", "库存B"));
    return new ActivityExplanationResult(
        activities,
        activities.stream()
            .map(
                activity ->
                    new ActivityEntryCoverage(
                        activity.entryIds().get(0),
                        "ANALYZED",
                        List.of(activity.activityId()),
                        null))
            .toList());
  }

  private static ReviewedActivity activity(String suffix, String object) {
    return new ReviewedActivity(
        "activity:" + suffix,
        "material:" + suffix,
        List.of("entry:" + suffix),
        "处理" + object + suffix,
        "完成" + object + "处理。",
        List.of(),
        List.of(object),
        List.of(object + "标识"),
        List.of(),
        List.of("处理" + object),
        List.of("返回处理结果"),
        List.of(),
        List.of(),
        List.of(object),
        "DIRECT_CODE_BEHAVIOR",
        List.of("S" + suffix),
        List.of(),
        List.of());
  }

  private static final class SharedBarrier {
    private final CountDownLatch draftsStarted = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicInteger groupReviews = new AtomicInteger();
  }

  private static final class GroupProvider implements StructuredModelProvider {
    private final String key;
    private final SharedBarrier barrier;
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<String> taskKinds =
        java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private GroupProvider(String key, SharedBarrier barrier) {
      this.key = key;
      this.barrier = barrier;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      taskKinds.add(request.taskKind());
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      if ("PROCESS_GROUP_DRAFT".equals(request.taskKind())) {
        int active = barrier.active.incrementAndGet();
        barrier.peak.accumulateAndGet(active, Math::max);
        barrier.draftsStarted.countDown();
        try {
          if (!barrier.release.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("process group barrier was not released");
          }
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new AssertionError(interrupted);
        } finally {
          barrier.active.decrementAndGet();
        }
      }
      if ("PROCESS_GROUP_REVIEW".equals(request.taskKind())) {
        barrier.groupReviews.incrementAndGet();
      }
      if (request.taskKind().startsWith("REPOSITORY_SUMMARY")) {
        if (barrier.groupReviews.get() != 2) {
          throw new AssertionError("repository summary started before every process review");
        }
        ObjectNode summary = JsonNodeFactory.instance.objectNode();
        summary.put("text", "仓库包含两个独立的业务处理过程。");
        summary.putArray("businessGoals").add("完成业务处理");
        summary.putArray("objectsAndRelations").add("两个过程处理不同业务对象");
        summary.putArray("confirmationTopics");
        ArrayNode summaryRefs = summary.putArray("sourceRefs");
        input.path("allowlistedRefs").forEach(ref -> summaryRefs.add(ref.asText()));
        return new StructuredModelResponse(
            canonicalJson.encodeCanonical(summary),
            new ModelRuntimeIdentityV1("scripted-" + key, "fixture", "high", "read-only"));
      }
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode process = response.putArray("processes").addObject();
      String first = input.path("activities").get(0).path("activityId").asText();
      process.put("processLocalId", "process-" + first);
      process.put("name", "业务处理过程");
      process.put("businessPurpose", "完成一组有关联的业务活动。");
      ArrayNode ids = process.putArray("activityIds");
      ArrayNode stages = process.putArray("stages");
      int order = 1;
      for (JsonNode activity : input.path("activities")) {
        String id = activity.path("activityId").asText();
        ids.add(id);
        stages.addObject().put("order", order++).put("activityId", id).put("description", "处理业务");
      }
      process.putArray("branches");
      process.putArray("sharedObjects").add("业务对象");
      process.putArray("codeDefinedResults").add("返回处理结果");
      process.put("certainty", "REASONABLE_INFERENCE");
      ArrayNode refs = process.putArray("sourceRefs");
      input.path("allowlistedRefs").forEach(ref -> refs.add(ref.asText()));
      process.putArray("confirmationNotes");
      response.putArray("unmatchedActivityIds");
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted-" + key, "fixture", "high", "read-only"));
    }

    private List<String> taskKinds() {
      synchronized (taskKinds) {
        return List.copyOf(taskKinds);
      }
    }
  }
}
