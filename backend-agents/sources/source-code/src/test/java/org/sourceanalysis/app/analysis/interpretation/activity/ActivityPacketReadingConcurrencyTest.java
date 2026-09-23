package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** RED contract that a Step05 packet owns one permit from reading through all selected slices. */
class ActivityPacketReadingConcurrencyTest {

  @Test
  void keepsOversizedPacketReadingAndSlicesLocalWhileHonoringGlobalAndProviderCaps(
      @TempDir Path temporary) throws Exception {
    SharedConcurrency shared = new SharedConcurrency(2);
    ConcurrentPacketProvider first =
        new ConcurrentPacketProvider(
            "first", ActivityPacketFailureIsolationTest.IDENTITY_A, shared);
    ConcurrentPacketProvider second =
        new ConcurrentPacketProvider(
            "second", ActivityPacketFailureIsolationTest.IDENTITY_B, shared);
    ActivityExplainer explainer = configuredExplainer(first, second, temporary.resolve("journal"));
    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      Future<ActivityExplanationResult> future =
          caller.submit(
              () ->
                  explainer.explain(
                      new ExplainCodeReadingMaterialsRequest(
                          ActivityPacketFailureIsolationTest.materialSet(
                              ActivityPacketFailureIsolationTest.packet(
                                  "packet:01-auth", "entry:01-auth", "AUTH"),
                              ActivityPacketFailureIsolationTest.packet(
                                  "packet:02-open", "entry:02-open", "OPEN"),
                              ActivityPacketFailureIsolationTest.packet(
                                  "packet:alpha", "entry:alpha", "ALPHA"),
                              ActivityPacketFailureIsolationTest.packet(
                                  "packet:bravo", "entry:bravo", "BRAVO")),
                          ActivityPacketFailureIsolationTest.PROFILE,
                          4)));

      assertThat(shared.firstPacketReadStarted.await(2, TimeUnit.SECONDS))
          .as("two fixed routes should hold the two global permits while they perform READING_PLAN")
          .isTrue();
      assertThat(shared.active).hasValue(2);
      assertThat(first.active).hasValue(1);
      assertThat(second.active).hasValue(1);
      shared.release.countDown();

      ActivityExplanationResult result = future.get(10, TimeUnit.SECONDS);
      assertThat(result.coverage()).hasSize(4);
      assertThat(shared.peak).hasValue(2);
      assertThat(first.peak).hasValue(1);
      assertThat(second.peak).hasValue(1);
      assertThat(first.completedPackets())
          .containsExactlyInAnyOrder("packet:01-auth", "packet:alpha");
      assertThat(second.completedPackets())
          .containsExactlyInAnyOrder("packet:02-open", "packet:bravo");
      assertOnlyOwnCompleteSelectedSourceInEachDraft(first.draftInputs());
      assertOnlyOwnCompleteSelectedSourceInEachDraft(second.draftInputs());
    } finally {
      shared.release.countDown();
      caller.shutdownNow();
    }
  }

  private static ActivityExplainer configuredExplainer(
      StructuredModelProvider first, StructuredModelProvider second, Path journal)
      throws Exception {
    Files.createDirectory(journal);
    ModelJobProviderBinding firstBinding =
        new ModelJobProviderBinding(
            "first",
            "first-reading-quota",
            1,
            first,
            ActivityPacketFailureIsolationTest.IDENTITY_A);
    ModelJobProviderBinding secondBinding =
        new ModelJobProviderBinding(
            "second",
            "second-reading-quota",
            1,
            second,
            ActivityPacketFailureIsolationTest.IDENTITY_B);
    ModelJobExecutionConfiguration configuration =
        new ModelJobExecutionConfiguration(
            2,
            Map.of("first", firstBinding, "second", secondBinding),
            Map.of(
                "activity", List.of("first", "second"),
                "processGroup", List.of("first"),
                "repositorySummary", List.of("first"),
                "report", List.of("first")),
            journal,
            AnalysisRunId.parse("analysis-run:" + "7".repeat(64)));
    return ActivityExplainer.forExecution(configuration);
  }

  private static void assertOnlyOwnCompleteSelectedSourceInEachDraft(List<DraftInput> drafts) {
    assertThat(drafts).hasSize(2);
    for (DraftInput draft : drafts) {
      List<String> scalar = ActivityPacketFailureIsolationTest.scalarText(draft.input());
      String marker = markerFor(draft.packetId());
      assertThat(scalar)
          .as("the %s slice must retain its own selected complete source", draft.packetId())
          .anyMatch(value -> value.contains(marker + "_SELECTED_COMPLETE_BODY"));
      assertThat(scalar).noneMatch(value -> value.contains(marker + "_UNREAD_COMPLETE_BODY"));
      for (String other : List.of("AUTH", "OPEN", "ALPHA", "BRAVO")) {
        if (!other.equals(marker)) {
          assertThat(scalar)
              .as(
                  "packet-local E1/M1/M2 keys must never join %s source into %s",
                  other, draft.packetId())
              .noneMatch(value -> value.contains(other + "_SELECTED_COMPLETE_BODY"));
        }
      }
    }
  }

  private static String markerFor(String packetId) {
    return switch (packetId) {
      case "packet:01-auth" -> "AUTH";
      case "packet:02-open" -> "OPEN";
      case "packet:alpha" -> "ALPHA";
      case "packet:bravo" -> "BRAVO";
      default -> throw new AssertionError("unknown packet marker " + packetId);
    };
  }

  private record DraftInput(String packetId, JsonNode input) {}

  private static final class SharedConcurrency {
    private final CountDownLatch firstPacketReadStarted;
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    private SharedConcurrency(int initialJobs) {
      firstPacketReadStarted = new CountDownLatch(initialJobs);
    }
  }

  private static final class ConcurrentPacketProvider implements StructuredModelProvider {
    private final String binding;
    private final org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1 identity;
    private final SharedConcurrency shared;
    private final org.sourceanalysis.app.artifact.CanonicalJsonCodec canonicalJson =
        new org.sourceanalysis.app.artifact.CanonicalJsonCodec();
    private final Map<String, Integer> readingRounds =
        Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<String> firstReadingPackets =
        Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<String> completedPackets = Collections.synchronizedList(new ArrayList<>());
    private final List<DraftInput> drafts = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    private ConcurrentPacketProvider(
        String binding,
        org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1 identity,
        SharedConcurrency shared) {
      this.binding = binding;
      this.identity = identity;
      this.shared = shared;
    }

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      int providerActive = active.incrementAndGet();
      int globalActive = shared.active.incrementAndGet();
      peak.accumulateAndGet(providerActive, Math::max);
      shared.peak.accumulateAndGet(globalActive, Math::max);
      if (providerActive > 1 || globalActive > 2) {
        throw new AssertionError("Step05 model permit cap exceeded at " + binding);
      }
      try {
        JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
        return switch (request.taskKind()) {
          case "ACTIVITY_READING_PLAN" -> readingPlan(input);
          case "ACTIVITY_DRAFT" -> draft(input);
          case "ACTIVITY_REVIEW" -> review(input);
          default -> throw new AssertionError("unexpected Step05 task " + request.taskKind());
        };
      } finally {
        active.decrementAndGet();
        shared.active.decrementAndGet();
      }
    }

    private StructuredModelResponse readingPlan(JsonNode input) {
      String packetId = input.path("packetId").asText();
      if (packetId.isBlank()) {
        throw new AssertionError("READING_PLAN must retain packet identity");
      }
      if (firstReadingPackets.add(packetId)) {
        shared.firstPacketReadStarted.countDown();
        awaitRelease();
      }
      int round = readingRounds.merge(packetId, 1, Integer::sum);
      if (round == 1) {
        return ActivityPacketFailureIsolationTest.readingResponse(
            identity,
            "{\"requestedNavigationPages\":[],\"requestedUnitKeys\":[\"M2\"],\"slices\":[],\"unknowns\":[],\"finalSliceKeys\":[],\"supersededSlices\":[],\"finishReading\":false}");
      }
      if (round == 2) {
        return ActivityPacketFailureIsolationTest.readingResponse(
            identity,
            "{\"requestedNavigationPages\":[],\"requestedUnitKeys\":[],\"slices\":[{\"sliceKey\":\"selected-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"packet-local scope\"}],\"unknowns\":[],\"finalSliceKeys\":[\"selected-scope\"],\"supersededSlices\":[],\"finishReading\":true}");
      }
      throw new AssertionError("unexpected READING_PLAN round " + round + " for " + packetId);
    }

    private StructuredModelResponse draft(JsonNode input) {
      String packetId = ActivityPacketFailureIsolationTest.packetIdForActivityInput(input);
      drafts.add(new DraftInput(packetId, input.deepCopy()));
      return ActivityPacketFailureIsolationTest.readingResponse(identity, "{\"activities\":[]}");
    }

    private StructuredModelResponse review(JsonNode input) {
      String packetId = ActivityPacketFailureIsolationTest.packetIdForActivityInput(input);
      completedPackets.add(packetId);
      return ActivityPacketFailureIsolationTest.readingResponse(
          identity, "{\"activities\":[],\"unexplainedEntries\":[\"E1\"]}");
    }

    private void awaitRelease() {
      try {
        if (!shared.release.await(10, TimeUnit.SECONDS)) {
          throw new AssertionError("test never released Step05 packet permits");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Step05 packet read was interrupted", interrupted);
      }
    }

    private List<DraftInput> draftInputs() {
      synchronized (drafts) {
        return List.copyOf(drafts);
      }
    }

    private List<String> completedPackets() {
      synchronized (completedPackets) {
        return List.copyOf(completedPackets);
      }
    }
  }
}
