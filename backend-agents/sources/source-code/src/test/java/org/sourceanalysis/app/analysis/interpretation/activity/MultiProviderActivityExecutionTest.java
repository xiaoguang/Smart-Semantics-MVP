package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.LinkedHashMap;
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
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialBuildResult;
import org.sourceanalysis.app.analysis.interpretation.material.LegacyM10CheckpointFixture;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;

/** Defines stable route binding and global/provider concurrency for real Activity jobs. */
class MultiProviderActivityExecutionTest {

  @TempDir Path temporaryDirectory;

  @Test
  void routesAllMaterialsStablyWhileEnforcingGlobalSixProFourAndApiTwo() throws Exception {
    LegacyM10CheckpointFixture.HistoricalCheckpoint fixture = LegacyM10CheckpointFixture.open();
    BusinessMaterialBuildResult materials =
        ActivityJobExecutionConfigurationTest.expandedMaterials(fixture, 12);
    Path journal = Files.createDirectory(temporaryDirectory.resolve("journal"));
    SharedActivityConcurrency shared = new SharedActivityConcurrency(6);
    RoutingProvider pro = new RoutingProvider("pro", 4, shared);
    RoutingProvider api = new RoutingProvider("api", 2, shared);
    ActivityExplainer explainer = configuredExplainer(pro, api, journal);
    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<ActivityExplanationResult> result =
          caller.submit(
              () ->
                  explainer.explain(
                      new ExplainActivitiesRequest(
                          materials,
                          new ActivityExplanationProfile(64_000, 16_000, 1, 32, 2_000),
                          12)));

      assertThat(shared.firstWave.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(shared.active).hasValue(6);
      assertThat(pro.active).hasValue(4);
      assertThat(api.active).hasValue(2);
      shared.release.countDown();

      ActivityExplanationResult completed = result.get(10, TimeUnit.SECONDS);
      assertThat(completed.reviewedActivities()).hasSize(12);
      assertThat(shared.peak).hasValue(6);
      assertThat(pro.peak).hasValue(4);
      assertThat(api.peak).hasValue(2);
      assertThat(pro.contexts())
          .containsExactly(
              "configured-activity-001",
              "configured-activity-003",
              "configured-activity-005",
              "configured-activity-007",
              "configured-activity-009",
              "configured-activity-011");
      assertThat(api.contexts())
          .containsExactly(
              "configured-activity-002",
              "configured-activity-004",
              "configured-activity-006",
              "configured-activity-008",
              "configured-activity-010",
              "configured-activity-012");
      assertThat(pro.totalCalls()).isEqualTo(12);
      assertThat(api.totalCalls()).isEqualTo(12);

      List<JsonNode> saved;
      try (var paths = Files.walk(journal)) {
        CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
        saved =
            paths
                .filter(path -> path.getFileName().toString().equals("reviewed-result.json"))
                .sorted()
                .map(
                    path -> {
                      try {
                        return canonicalJson.parseCanonical(
                            org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                                Files.readAllBytes(path)));
                      } catch (java.io.IOException failure) {
                        throw new AssertionError(failure);
                      }
                    })
                .toList();
      }
      assertThat(saved).hasSize(12);
      assertThat(
              saved.stream().filter(node -> node.path("providerBindingKey").asText().equals("pro")))
          .hasSize(6);
      assertThat(
              saved.stream().filter(node -> node.path("providerBindingKey").asText().equals("api")))
          .hasSize(6);
    } finally {
      shared.release.countDown();
      caller.shutdownNow();
    }
  }

  private static ActivityExplainer configuredExplainer(
      StructuredModelProvider pro, StructuredModelProvider api, Path journal) throws Exception {
    try {
      Class<?> bindingType =
          Class.forName("org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding");
      Constructor<?> bindingConstructor =
          bindingType.getConstructor(
              String.class,
              String.class,
              int.class,
              StructuredModelProvider.class,
              ModelRuntimeIdentityV1.class);
      Object proBinding =
          bindingConstructor.newInstance(
              "pro",
              "personal-pro-account",
              4,
              pro,
              new ModelRuntimeIdentityV1("scripted-pro", "fixture", "high", "read-only"));
      Object apiBinding =
          bindingConstructor.newInstance(
              "api",
              "approved-api-project",
              2,
              api,
              new ModelRuntimeIdentityV1("scripted-api", "fixture", "high", "read-only"));
      Map<String, Object> providers = new LinkedHashMap<>();
      providers.put("pro", proBinding);
      providers.put("api", apiBinding);
      Map<String, List<String>> routes =
          Map.of(
              "activity", List.of("pro", "api"),
              "processGroup", List.of("pro", "api"),
              "repositorySummary", List.of("pro"),
              "report", List.of("pro"));
      Class<?> configurationType =
          Class.forName("org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration");
      Object configuration =
          configurationType
              .getConstructor(int.class, Map.class, Map.class, Path.class, AnalysisRunId.class)
              .newInstance(
                  6,
                  providers,
                  routes,
                  journal,
                  new AnalysisRunId("analysis-run:" + "c".repeat(64)));
      Method factory = ActivityExplainer.class.getMethod("forExecution", configurationType);
      return (ActivityExplainer) factory.invoke(null, configuration);
    } catch (ClassNotFoundException | NoSuchMethodException missing) {
      fail("MULTI_PROVIDER_ACTIVITY_EXECUTION_NOT_IMPLEMENTED", missing);
      throw new AssertionError("unreachable");
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      throw failure;
    }
  }

  private static final class SharedActivityConcurrency {
    private final CountDownLatch firstWave;
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    private SharedActivityConcurrency(int expectedFirstWave) {
      firstWave = new CountDownLatch(expectedFirstWave);
    }
  }

  private static final class RoutingProvider implements StructuredModelProvider {
    private final String providerKey;
    private final int expectedCap;
    private final SharedActivityConcurrency shared;
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> contexts =
        java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private RoutingProvider(String providerKey, int expectedCap, SharedActivityConcurrency shared) {
      this.providerKey = providerKey;
      this.expectedCap = expectedCap;
      this.shared = shared;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode packet = canonicalJson.parseCanonical(request.untrustedInputJson());
      String context = packet.path("context").asText();
      int call = calls.incrementAndGet();
      if ("ACTIVITY_DRAFT".equals(request.taskKind())) {
        contexts.add(context);
        int providerActive = active.incrementAndGet();
        int globalActive = shared.active.incrementAndGet();
        peak.accumulateAndGet(providerActive, Math::max);
        shared.peak.accumulateAndGet(globalActive, Math::max);
        if (providerActive > expectedCap) {
          throw new AssertionError(providerKey + " exceeded provider cap");
        }
        shared.firstWave.countDown();
        try {
          if (!shared.release.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("first Activity wave did not release");
          }
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new AssertionError(interrupted);
        } finally {
          active.decrementAndGet();
          shared.active.decrementAndGet();
        }
      }
      ObjectNode response = response(packet, "ACTIVITY_REVIEW".equals(request.taskKind()));
      return new StructuredModelResponse(
          canonicalJson.encodeCanonical(response),
          new ModelRuntimeIdentityV1("scripted-" + providerKey, "fixture", "high", "read-only"));
    }

    private static ObjectNode response(JsonNode packet, boolean reviewed) {
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

    private List<String> contexts() {
      synchronized (contexts) {
        return contexts.stream().sorted().toList();
      }
    }

    private int totalCalls() {
      return calls.get();
    }
  }
}
